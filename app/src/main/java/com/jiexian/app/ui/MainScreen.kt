package com.jiexian.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.AppRule
import com.jiexian.app.model.AppTarget
import com.jiexian.app.model.RuleType
import com.jiexian.app.ui.components.AppIcon
import com.jiexian.app.ui.components.BrandMark
import com.jiexian.app.ui.theme.Moonlight
import com.jiexian.app.ui.theme.NightMuted
import com.jiexian.app.ui.theme.NightText
import com.jiexian.app.update.ReleaseInfo
import com.jiexian.app.util.PermissionUtils
import com.jiexian.app.util.formatMinutes
import com.jiexian.app.util.formatUsage
import com.jiexian.app.util.unlockTimeLabel
import java.time.LocalTime

private val GlassBorder = Color(0x18DAE7CC)
private val GlassSurface = Color(0x0FDCE7CC)

data class HomeCopy(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
)

fun homeCopyFor(
    hour: Int,
    hasRules: Boolean,
    activeRuleCount: Int,
): HomeCopy {
    if (!hasRules) {
        return HomeCopy(
            eyebrow = "从一个安静的时刻开始",
            title = "给时间，\n留一道界限。",
            subtitle = "选择几个容易分心的应用，\n为自己留一段不被打扰的时间。",
        )
    }
    if (activeRuleCount > 0) {
        return HomeCopy(
            eyebrow = "此刻，安静正好",
            title = "让世界，\n暂时安静。",
            subtitle = "$activeRuleCount 条界限正在守护，\n其他事情可以等到结束以后。",
        )
    }
    return when (hour) {
        in 5..10 -> {
            HomeCopy(
                eyebrow = "清晨，不必急于回应",
                title = "天亮了，\n慢一点开始。",
                subtitle = "先留一点安静给自己，\n再走向今天的喧嚣。",
            )
        }

        in 11..17 -> {
            HomeCopy(
                eyebrow = "留一点空白给此刻",
                title = "此刻，\n让世界慢下来。",
                subtitle = "不必时刻回应世界，\n专注本身就是答案。",
            )
        }

        in 18..22 -> {
            HomeCopy(
                eyebrow = "把一天的喧嚣放下",
                title = "夜幕渐深，\n把喧嚣留在身后。",
                subtitle = "今天已经足够忙碌，\n现在，把时间还给自己。",
            )
        }

        else -> {
            HomeCopy(
                eyebrow = "此刻，安静正好",
                title = "夜深了，\n让世界暂时安静。",
                subtitle = "留下此刻的宁静，\n其他事情可以等到明天。",
            )
        }
    }
}

@Composable
fun MainScreen(
    state: MainUiState,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (AppRule) -> Unit,
) {
    var showAbout by remember { mutableStateOf(false) }
    val activeLocks = state.activeLocks.filter { it.unlockAt > state.nowMillis }
    val activeRuleCount = activeLocks.map(ActiveLock::sourceRuleId).distinct().size
    val copy =
        homeCopyFor(
            hour = LocalTime.now().hour,
            hasRules = state.rules.isNotEmpty(),
            activeRuleCount = activeRuleCount,
        )

    Box(Modifier.fillMaxSize().nightAmbient()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(size = 34.dp, color = Moonlight)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "界限",
                        color = NightText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp,
                    )
                    Text("JIEXIAN · 安静有度", color = NightMuted, fontSize = 8.sp, letterSpacing = 1.1.sp)
                }
                IconButton(onClick = onCheckUpdates, enabled = !state.checkingUpdate) {
                    if (state.checkingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Moonlight, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = "检查更新", tint = NightMuted)
                    }
                }
                IconButton(onClick = { showAbout = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = "关于与隐私", tint = NightMuted)
                }
            }

            NightHero(
                copy = copy,
                hasAccess = state.hasUsageAccess && state.hasAccessibilityAccess && state.guardianAlive,
                activeLocks = activeLocks,
            )

            if (state.hasAccessibilityAccess && !state.guardianAlive) {
                GuardianStoppedCard()
            }

            state.availableUpdate?.let { update ->
                UpdateCard(update = update, installing = state.installingUpdate, onInstall = onInstallUpdate)
            }

            val guardianNeedsAttention = state.hasAccessibilityAccess && !state.guardianAlive
            if (!state.hasUsageAccess ||
                !state.hasAccessibilityAccess ||
                !state.hasNotificationAccess ||
                !state.hasBackgroundAccess ||
                guardianNeedsAttention
            ) {
                PermissionCard(state)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("我的界限", color = NightText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (state.rules.isEmpty()) "为容易分心的时刻设一道边界" else "${state.rules.size} 条规则 · 未来随时可以调整",
                        color = NightMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconButton(onClick = onAddRule) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加规则", tint = Moonlight)
                }
            }

            if (state.rules.isEmpty()) {
                EmptyRules(onAddRule)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    state.rules.forEach { rule ->
                        RuleCard(
                            rule = rule,
                            usageToday = state.usageToday,
                            activeLocks = activeLocks.filter { it.sourceRuleId == rule.id },
                            onClick = { onEditRule(rule) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (showAbout) AboutDialog { showAbout = false }
}

private fun Modifier.nightAmbient(): Modifier =
    background(
        Brush.linearGradient(
            colors = listOf(Color(0xFF173D33), Color(0xFF0A2420), Color(0xFF071D1A)),
            start = Offset.Zero,
            end = Offset.Infinite,
        ),
    )

@Composable
private fun NightHero(
    copy: HomeCopy,
    hasAccess: Boolean,
    activeLocks: List<ActiveLock>,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(276.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val moonCenter = Offset(size.width * 0.91f, size.height * 0.16f)
            drawCircle(Moonlight.copy(alpha = 0.025f), size.minDimension * 0.34f, moonCenter)
            drawCircle(Moonlight.copy(alpha = 0.035f), size.minDimension * 0.25f, moonCenter)
            drawCircle(
                brush =
                    Brush.radialGradient(
                        listOf(Moonlight.copy(alpha = 0.18f), Moonlight.copy(alpha = 0.015f)),
                        center = moonCenter,
                        radius = size.minDimension * 0.19f,
                    ),
                radius = size.minDimension * 0.19f,
                center = moonCenter,
            )
            drawCircle(Moonlight.copy(alpha = 0.22f), 1.8f, Offset(size.width * 0.14f, size.height * 0.27f))
            drawCircle(Moonlight.copy(alpha = 0.14f), 1.5f, Offset(size.width * 0.76f, size.height * 0.64f))
        }
        Column(Modifier.padding(top = 18.dp, bottom = 12.dp)) {
            Surface(shape = CircleShape, color = GlassSurface) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Box(Modifier.size(5.dp).background(if (hasAccess) Moonlight else NightMuted, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(if (hasAccess) copy.eyebrow else "完成授权后，安静才会开始", color = Moonlight, style = MaterialTheme.typography.labelMedium)
                }
            }
            AnimatedContent(
                targetState = copy,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "homeCopy",
            ) { currentCopy ->
                Column {
                    Text(
                        currentCopy.title,
                        color = NightText,
                        fontFamily = FontFamily.Serif,
                        fontSize = 30.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Text(
                        currentCopy.subtitle,
                        color = NightMuted,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val activeRuleCount = activeLocks.map(ActiveLock::sourceRuleId).distinct().size
            Text(
                if (activeLocks.isEmpty()) {
                    "当前没有生效中的限制"
                } else {
                    "$activeRuleCount 条界限 · ${activeLocks.map(ActiveLock::packageName).distinct().size} 个应用安静中"
                },
                color = if (activeLocks.isEmpty()) NightMuted else Moonlight,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun GuardianStoppedCard() {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0x1ADCE7CC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40DAE7CC)),
    ) {
        Row(Modifier.padding(horizontal = 17.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = Moonlight, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("守护已停止", color = NightText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "系统清理或强制停止后，无障碍服务需要重新开启",
                    color = NightMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            TextButton(onClick = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) }) {
                Text("重新开启", color = Moonlight)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun UpdateCard(
    update: ReleaseInfo,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0x14DCE7CC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x30DAE7CC)),
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = Moonlight, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "新版本 ${update.versionName}",
                    color = NightText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(update.notes, color = NightMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = onInstall,
                enabled = !installing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Moonlight, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(44.dp),
            ) {
                if (installing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在下载并校验")
                } else {
                    Text("下载并安装")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(state: MainUiState) {
    val context = LocalContext.current
    var showBackgroundGuide by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = GlassSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
        Column(Modifier.padding(17.dp)) {
            Text("让守护真正开始", color = NightText, fontWeight = FontWeight.Medium)
            Text(
                "完成授权 · 数据始终留在本机",
                color = NightMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 13.dp),
            )
            PermissionRow(Icons.Outlined.Timelapse, "使用情况访问", if (state.hasUsageAccess) "已授权" else "计算每个应用的每日额度", state.hasUsageAccess) {
                context.startActivity(PermissionUtils.usageAccessSettingsIntent())
            }
            PermissionRow(
                Icons.Outlined.NotificationsNone,
                "系统通知",
                if (state.hasNotificationAccess) "额度、时段和亮屏提醒已开启" else "允许状态栏使用提醒",
                state.hasNotificationAccess,
            ) {
                context.startActivity(PermissionUtils.notificationSettingsIntent(context))
            }
            PermissionRow(
                Icons.Outlined.Security,
                "应用守护服务",
                when {
                    state.hasAccessibilityAccess && state.guardianAlive -> "服务运行中"
                    state.hasAccessibilityAccess -> "被系统停止了，点此重新开启"
                    else -> "识别并退出受限应用"
                },
                state.hasAccessibilityAccess && state.guardianAlive,
            ) {
                context.startActivity(PermissionUtils.accessibilitySettingsIntent())
            }
            BackgroundRow(granted = state.hasBackgroundAccess) { showBackgroundGuide = true }
        }
    }
    if (showBackgroundGuide) {
        BackgroundGuideDialog(
            context = context,
            batteryExempt = state.hasBackgroundAccess,
            onDismiss = { showBackgroundGuide = false },
        )
    }
}

@Composable
private fun BackgroundRow(
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.BatterySaver, null, tint = Moonlight, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("后台守护", color = NightText, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (granted) "已允许后台运行，退出多任务不会被清理" else "允许后台运行，避免被系统清理",
                color = NightMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(
            if (granted) Icons.Outlined.Check else Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Moonlight,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun BackgroundGuideDialog(
    context: android.content.Context,
    batteryExempt: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("让守护留在后台") },
        text = {
            Column {
                Text(
                    "清除多任务后，界限仍会继续拦截。国产系统（小米、华为、OPPO、vivo 等）还需要允许自启动，并允许后台运行，才能避免被系统清理。",
                )
                Spacer(Modifier.height(10.dp))
                Text("被清理后，打开界限会显示「守护已停止」，重新开启无障碍服务即可恢复。", color = NightMuted)
                Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { openAutoStart(context) }) { Text("自启动设置") }
                    TextButton(onClick = { openAppDetails(context) }) { Text("应用信息") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openBatteryOptimizations(context)
                onDismiss()
            }) { Text(if (batteryExempt) "后台运行已开启" else "忽略电池优化") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun openBatteryOptimizations(context: android.content.Context) {
    if (PermissionUtils.isIgnoringBatteryOptimizations(context)) {
        openAppDetails(context)
        return
    }
    PermissionUtils.batteryOptimizationIntents(context).forEach { intent ->
        if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return
    }
    openAppDetails(context)
}

private fun openAutoStart(context: android.content.Context) {
    if (!PermissionUtils.openAutoStartSettings(context)) openAppDetails(context)
}

private fun openAppDetails(context: android.content.Context) {
    runCatching { context.startActivity(PermissionUtils.appDetailsIntent(context)) }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(14.dp),
                ).clickable(enabled = !granted, onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Moonlight, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = NightText, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = NightMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            if (granted) Icons.Outlined.Check else Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Moonlight,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RuleCard(
    rule: AppRule,
    usageToday: Map<String, Long>,
    activeLocks: List<ActiveLock>,
    onClick: () -> Unit,
) {
    val unlockAt = activeLocks.maxOfOrNull(ActiveLock::unlockAt)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0x14DCE7CC)) {
                    Text(
                        if (unlockAt == null) {
                            when (rule.type) {
                                RuleType.DAILY_LIMIT -> "每日"
                                RuleType.TIME_WINDOW -> "每天"
                                RuleType.ALLOW_ONLY -> "仅允许"
                            }
                        } else {
                            "${unlockTimeLabel(unlockAt)} 恢复"
                        },
                        color = Moonlight,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (activeLocks.isNotEmpty()) {
                    Box(Modifier.size(6.dp).background(Moonlight, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("限制中", color = Moonlight, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                rule.name,
                color = NightText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 15.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rule.usesWindow) {
                Text(
                    rule.windowLabel(),
                    color = Moonlight,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(top = 7.dp),
                )
                Text(
                    when {
                        rule.type == RuleType.ALLOW_ONLY && rule.startMinute > rule.endMinute ->
                            "跨越午夜 · 只允许 ${rule.targets.size} 个应用"
                        rule.type == RuleType.ALLOW_ONLY ->
                            "每天重复 · 只允许 ${rule.targets.size} 个应用"
                        rule.startMinute > rule.endMinute -> "跨越午夜 · ${rule.targets.size} 个应用"
                        else -> "每天重复 · ${rule.targets.size} 个应用"
                    },
                    color = NightMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 7.dp),
                )
            } else {
                val highestUsage = rule.targets.maxOfOrNull { usageToday[it.packageName] ?: 0L } ?: 0L
                val limitMillis = rule.limitMinutes * 60_000L
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        if (activeLocks.isEmpty()) "最少剩余 ${formatUsage((limitMillis - highestUsage).coerceAtLeast(0L))}" else "今日额度已用完",
                        color = Moonlight,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text("每个应用 ${formatMinutes(rule.limitMinutes)}", color = NightMuted, style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(
                    progress = { (highestUsage.toFloat() / limitMillis).coerceIn(0f, 1f) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 13.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                    color = Moonlight,
                    trackColor = Color(0x18DCE7CC),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TargetIcons(rule.targets)
                Spacer(Modifier.width(10.dp))
                Text(
                    rule.targets.take(2).joinToString("、") { it.appName } + if (rule.targets.size > 2) " 等" else "",
                    color = NightMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, contentDescription = "编辑规则", tint = NightMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TargetIcons(targets: List<AppTarget>) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        targets.take(4).forEach { AppIcon(it.packageName, size = 32.dp) }
        if (targets.size > 4) {
            Box(Modifier.size(32.dp).background(GlassSurface, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("+${targets.size - 4}", color = Moonlight, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyRules(onAddRule: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = GlassSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 25.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(58.dp).background(Color(0x0ADCE7CC), CircleShape))
                Icon(Icons.Outlined.Timelapse, null, tint = Moonlight, modifier = Modifier.size(24.dp))
            }
            Text("给专注一个名字", color = NightText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 15.dp))
            Text("一条界限，可以同时照看多个应用", color = NightMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 7.dp))
            Button(
                onClick = onAddRule,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Moonlight, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.padding(top = 18.dp).height(45.dp),
            ) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建一条界限")
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { BrandMark(size = 38.dp, color = Moonlight) },
        title = { Text("关于界限") },
        text = {
            Text(
                "每条规则可以命名并包含多个应用。每日额度按应用分别计算；禁用时段限制勾选的应用；允许清单则相反，时段内只保留勾选的应用，其余会回到桌面。规则可以修改或删除，但已开始的限制会保留原结束时间。\n\n所有规则和使用统计只保存在本机，不会读取屏幕内容。更新功能仅连接 GitHub 公开 Release，用于检查、下载和校验安装包，不上传个人数据。\n\n界限会用常驻通知保持后台运行，退出多任务后仍会拦截。受 Android 系统限制，强制停止、关闭无障碍服务、清除应用数据或卸载应用仍会停止守护；重新打开应用并在首页重新开启无障碍服务即可恢复。设备级不可绕过管控需要 Device Owner 管理模式。",
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}
