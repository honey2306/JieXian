package com.jiexian.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiexian.app.data.InstalledApp
import com.jiexian.app.data.InstalledAppsRepository
import com.jiexian.app.data.RuleRepository
import com.jiexian.app.data.UsageRepository
import com.jiexian.app.enforce.ShizukuEnforcement
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.AppRule
import com.jiexian.app.service.GuardService
import com.jiexian.app.service.GuardianHeartbeat
import com.jiexian.app.service.GuardianLog
import com.jiexian.app.service.LockSnapshotter
import com.jiexian.app.service.ReminderScheduler
import com.jiexian.app.update.ReleaseInfo
import com.jiexian.app.update.UpdateChecker
import com.jiexian.app.update.UpdateInstaller
import com.jiexian.app.util.PermissionUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val rules: List<AppRule> = emptyList(),
    val activeLocks: List<ActiveLock> = emptyList(),
    val usageToday: Map<String, Long> = emptyMap(),
    val installedApps: List<InstalledApp> = emptyList(),
    val appsLoading: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasAccessibilityAccess: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val guardianAlive: Boolean = true,
    val hasBackgroundAccess: Boolean = true,
    val hasOverlayAccess: Boolean = false,
    /** Whether system-level suspension through Shizuku is available right now. */
    val systemLock: ShizukuEnforcement.State = ShizukuEnforcement.State.UNAVAILABLE,
    val nowMillis: Long = System.currentTimeMillis(),
    val message: String? = null,
    val availableUpdate: ReleaseInfo? = null,
    val checkingUpdate: Boolean = false,
    val installingUpdate: Boolean = false,
)

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = RuleRepository.get(application)
    private val usageRepository = UsageRepository(application)
    private val appsRepository = InstalledAppsRepository(application)
    private val snapshotter = LockSnapshotter(repository, usageRepository)
    private val reminderScheduler = ReminderScheduler(application, repository)
    private val updateChecker = UpdateChecker(application)
    private val updateInstaller = UpdateInstaller(application)
    private val _state = MutableStateFlow(MainUiState(availableUpdate = updateChecker.cachedUpdate()))
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var loggedMissingAccessibility = false

    init {
        recordLaunch()
        viewModelScope.launch {
            repository.rules.collect { rules ->
                _state.update { it.copy(rules = rules) }
                refreshUsage()
            }
        }
        viewModelScope.launch {
            repository.activeLocks().collect { locks ->
                _state.update { it.copy(activeLocks = locks) }
            }
        }
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(30_000)
            }
        }
    }

    /** Records how the guardian looked at launch, so a vendor kill can be diagnosed later. */
    private fun recordLaunch() {
        val context = getApplication<Application>()
        val listed = PermissionUtils.isAccessibilityEnabledInSettings(context)
        // A recent heartbeat only proves the service ran, not that the grant still exists.
        val alive = listed && GuardianHeartbeat.isAlive(context)
        val lastSignal = GuardianHeartbeat.lastSignalAt(context)
        val age =
            if (lastSignal <= 0L) {
                "从未"
            } else {
                val minutes = (System.currentTimeMillis() - lastSignal) / 60_000L
                if (minutes < 1) "刚刚" else "$minutes 分钟前"
            }
        GuardianLog.record(
            context,
            "打开 app · 无障碍${if (listed) "已授权" else "未授权"} · 守护${if (alive) "存活" else "已停止"} · 上次心跳 $age",
        )
        loggedMissingAccessibility = !listed
    }

    fun refresh() {
        val context = getApplication<Application>()
        if (!loggedMissingAccessibility && !PermissionUtils.isAccessibilityEnabledInSettings(context)) {
            loggedMissingAccessibility = true
            GuardianLog.record(context, "检测到无障碍未授权（系统未列出守护服务）")
        }
        _state.update {
            it.copy(
                hasUsageAccess = usageRepository.hasUsageAccess(),
                hasAccessibilityAccess = PermissionUtils.isAccessibilityEnabled(context),
                hasNotificationAccess = PermissionUtils.hasNotificationAccess(context),
                guardianAlive = GuardianHeartbeat.isAlive(context),
                hasBackgroundAccess = PermissionUtils.isIgnoringBatteryOptimizations(context),
                hasOverlayAccess = PermissionUtils.canDrawOverlays(context),
                systemLock = ShizukuEnforcement.state(),
                nowMillis = System.currentTimeMillis(),
                activeLocks = it.activeLocks.filter { lock -> lock.unlockAt > System.currentTimeMillis() },
            )
        }
        refreshUsage()
        GuardService.sync(context)
        viewModelScope.launch { runCatching { reminderScheduler.check() } }
        viewModelScope.launch {
            runCatching { updateChecker.check() }
            _state.update { it.copy(availableUpdate = updateChecker.cachedUpdate()) }
        }
    }

    fun loadApps() {
        if (_state.value.installedApps.isNotEmpty() || _state.value.appsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(appsLoading = true) }
            val apps = appsRepository.launchableApps()
            _state.update { it.copy(installedApps = apps, appsLoading = false) }
        }
    }

    fun saveRule(
        rule: AppRule,
        isEditing: Boolean,
    ) {
        viewModelScope.launch {
            if (isEditing) repository.updateRule(rule) else repository.addRule(rule)
            // A rule saved during its disabled window starts that cycle immediately.
            snapshotter.snapshotAll()
            runCatching { ShizukuEnforcement.sync(getApplication(), repository) }
            val lockActive =
                repository
                    .activeLocksForRule(rule.id, System.currentTimeMillis())
                    .isNotEmpty()
            _state.update {
                it.copy(
                    message =
                        if (lockActive) {
                            "规则已更新；本次限制仍按原结束时间执行"
                        } else {
                            if (isEditing) "规则已更新" else "规则已添加，守护已开启"
                        },
                )
            }
        }
    }

    fun deleteRule(rule: AppRule) {
        viewModelScope.launch {
            val lockActive =
                repository
                    .activeLocksForRule(rule.id, System.currentTimeMillis())
                    .isNotEmpty()
            repository.deleteRule(rule)
            runCatching { ShizukuEnforcement.sync(getApplication(), repository) }
            _state.update {
                it.copy(
                    message =
                        if (lockActive) {
                            "规则已删除；本次限制不会被解除"
                        } else {
                            "规则已删除"
                        },
                )
            }
        }
    }

    /** Called after the user grants the Shizuku permission: enforce immediately. */
    fun onSystemLockGranted() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            runCatching { ShizukuEnforcement.sync(context, repository) }
            _state.update { it.copy(systemLock = ShizukuEnforcement.state()) }
        }
    }

    fun checkForUpdates() {
        if (_state.value.checkingUpdate) return
        viewModelScope.launch {
            _state.update { it.copy(checkingUpdate = true) }
            try {
                val update = updateChecker.check(force = true)
                _state.update {
                    it.copy(
                        availableUpdate = update,
                        message = if (update == null) "当前已经是最新版本" else "发现新版本 ${update.versionName}",
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(message = "暂时无法连接 GitHub，请稍后重试") }
            } finally {
                _state.update { it.copy(checkingUpdate = false) }
            }
        }
    }

    fun installUpdate() {
        val update = _state.value.availableUpdate ?: return
        if (_state.value.installingUpdate) return
        viewModelScope.launch {
            _state.update { it.copy(installingUpdate = true) }
            try {
                updateInstaller.install(update)
            } catch (error: Exception) {
                _state.update { it.copy(message = error.message ?: "下载或安装更新失败") }
            } finally {
                _state.update { it.copy(installingUpdate = false) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun refreshUsage() {
        viewModelScope.launch {
            val packages =
                _state.value.rules
                    .flatMap { rule -> rule.targets.map { target -> target.packageName } }
                    .distinct()
            val usage = usageRepository.usageToday(packages)
            _state.update { it.copy(usageToday = usage) }
        }
    }
}
