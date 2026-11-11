package com.jiexian.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jiexian.app.data.InstalledApp
import com.jiexian.app.model.AppRule
import com.jiexian.app.model.AppTarget
import com.jiexian.app.model.MAX_TOTAL_LIMIT_MINUTES
import com.jiexian.app.model.RuleType
import com.jiexian.app.model.minuteLabel
import com.jiexian.app.ui.components.AppIcon
import com.jiexian.app.ui.theme.Forest
import com.jiexian.app.ui.theme.Moonlight
import com.jiexian.app.ui.theme.NightMuted
import com.jiexian.app.ui.theme.NightSurface
import com.jiexian.app.ui.theme.NightText
import com.jiexian.app.util.formatMinutes
import com.jiexian.app.util.unlockTimeLabel
import kotlin.math.roundToInt

private val EditorGlass = Color(0x0FDCE7CC)
private val EditorBorder = Color(0x18DAE7CC)

@Composable
fun RuleEditorScreen(
    existingRule: AppRule?,
    state: MainUiState,
    onLoadApps: () -> Unit,
    onBack: () -> Unit,
    onSave: (AppRule) -> Unit,
    onDelete: (AppRule) -> Unit,
) {
    var ruleName by remember { mutableStateOf(existingRule?.name.orEmpty()) }
    var selectedTargets by remember { mutableStateOf(existingRule?.targets.orEmpty()) }
    var ruleType by remember { mutableStateOf(existingRule?.type ?: RuleType.DAILY_LIMIT) }
    var limitMinutes by remember { mutableStateOf(existingRule?.limitMinutes ?: 30) }
    var totalLimitMinutes by remember { mutableStateOf(existingRule?.totalLimitMinutes) }
    var startMinute by remember { mutableStateOf(existingRule?.startMinute ?: 23 * 60) }
    var endMinute by remember { mutableStateOf(existingRule?.endMinute ?: 9 * 60) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val currentLocks = state.activeLocks.filter { it.sourceRuleId == existingRule?.id && it.unlockAt > state.nowMillis }
    val latestUnlock = currentLocks.maxOfOrNull { it.unlockAt }
    val canSave = ruleName.isNotBlank() && selectedTargets.isNotEmpty() && (ruleType == RuleType.DAILY_LIMIT || startMinute != endMinute)

    LaunchedEffect(Unit) { onLoadApps() }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1B4337), Color(0xFF0A2420), Color(0xFF071D1A)),
                    center = Offset(0.82f, 0.08f),
                    radius = 1.3f,
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = NightText) }
                    Text(
                        if (existingRule ==
                            null
                        ) {
                            "新建界限"
                        } else {
                            "调整界限"
                        },
                        color = NightText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    if (existingRule != null) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除规则", tint = NightMuted)
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }
            },
            bottomBar = {
                Column(
                    Modifier
                        .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp),
                ) {
                    Button(
                        onClick = {
                            val base =
                                existingRule ?: AppRule(
                                    name = ruleName.trim(),
                                    targets = selectedTargets,
                                    type = ruleType,
                                )
                            onSave(
                                base.copy(
                                    name = ruleName.trim(),
                                    targets = selectedTargets.distinctBy(AppTarget::packageName),
                                    type = ruleType,
                                    limitMinutes = limitMinutes,
                                    totalLimitMinutes = if (ruleType == RuleType.DAILY_LIMIT) totalLimitMinutes else null,
                                    startMinute = startMinute,
                                    endMinute = endMinute,
                                ),
                            )
                        },
                        enabled = canSave,
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Moonlight,
                                contentColor = Forest,
                                disabledContainerColor = Moonlight.copy(alpha = 0.18f),
                                disabledContentColor = NightMuted,
                            ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (existingRule == null) "创建界限" else "保存修改", fontWeight = FontWeight.SemiBold) }
                    Text(
                        if (ruleType == RuleType.ALLOW_ONLY) {
                            "时段内只保留这些应用，其余应用会回到桌面"
                        } else {
                            "开始之后，本次限制没有临时加时"
                        },
                        color = NightMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 15.dp),
                    )
                }
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 23.dp),
            ) {
                if (latestUnlock != null) {
                    Surface(shape = RoundedCornerShape(16.dp), color = EditorGlass) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, tint = Moonlight, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${currentLocks.distinctBy { it.packageName }.size} 个应用限制中 · ${unlockTimeLabel(
                                    latestUnlock,
                                )} 恢复\n修改或删除不会影响本次锁定",
                                color = Moonlight,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }

                Text(
                    "为这段安静起个名字",
                    color = NightMuted,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 17.dp),
                )
                TextField(
                    value = ruleName,
                    onValueChange = { if (it.length <= 24) ruleName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "睡前免打扰",
                            color = NightMuted.copy(alpha = 0.45f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    textStyle =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            color = NightText,
                        ),
                    singleLine = true,
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Moonlight,
                        ),
                )

                Spacer(Modifier.height(18.dp))
                EditorHeading(
                    if (ruleType == RuleType.ALLOW_ONLY) "仍然可以使用的应用" else "一起安静的应用",
                    "${selectedTargets.size} 个",
                )
                Spacer(Modifier.height(13.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(selectedTargets, key = { it.packageName }) { target ->
                        AppIcon(target.packageName, size = 48.dp, modifier = Modifier.clickable { showAppPicker = true })
                    }
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .size(
                                        48.dp,
                                    ).clip(
                                        RoundedCornerShape(15.dp),
                                    ).border(1.dp, NightMuted.copy(alpha = 0.45f), RoundedCornerShape(15.dp))
                                    .clickable {
                                        showAppPicker =
                                            true
                                    },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Add, contentDescription = "选择应用", tint = Moonlight, modifier = Modifier.size(21.dp)) }
                    }
                }
                if (selectedTargets.isEmpty()) {
                    Text(
                        if (ruleType == RuleType.ALLOW_ONLY) "选择此刻仍然可以使用的应用" else "选择几个容易分心的应用",
                        color = NightMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                Spacer(Modifier.height(23.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(15.dp),
                            ).background(EditorGlass)
                            .border(1.dp, EditorBorder.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
                            .padding(4.dp),
                ) {
                    TypeOption("每日限额", ruleType == RuleType.DAILY_LIMIT, Modifier.weight(1f)) { ruleType = RuleType.DAILY_LIMIT }
                    TypeOption("禁用时段", ruleType == RuleType.TIME_WINDOW, Modifier.weight(1f)) { ruleType = RuleType.TIME_WINDOW }
                    TypeOption("允许清单", ruleType == RuleType.ALLOW_ONLY, Modifier.weight(1f)) { ruleType = RuleType.ALLOW_ONLY }
                }

                Spacer(Modifier.height(18.dp))
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = EditorGlass,
                    border = androidx.compose.foundation.BorderStroke(1.dp, EditorBorder),
                ) {
                    if (ruleType == RuleType.DAILY_LIMIT) {
                        DailyLimitEditor(
                            limitMinutes = limitMinutes,
                            totalLimitMinutes = totalLimitMinutes,
                            onChange = { next ->
                                limitMinutes = next
                                val total = totalLimitMinutes
                                if (total != null && total <= next) {
                                    totalLimitMinutes = (next + 5).coerceAtMost(MAX_TOTAL_LIMIT_MINUTES)
                                }
                            },
                            onTotalChange = { totalLimitMinutes = it },
                        )
                    } else {
                        TimeWindowEditor(
                            startMinute = startMinute,
                            endMinute = endMinute,
                            allowOnly = ruleType == RuleType.ALLOW_ONLY,
                            onStartChange = { startMinute = it },
                            onEndChange = { endMinute = it },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAppPicker) {
        AppPickerSheet(
            apps = state.installedApps,
            loading = state.appsLoading,
            selectedPackages = selectedTargets.mapTo(mutableSetOf(), AppTarget::packageName),
            onDismiss = { showAppPicker = false },
            onToggle = { app ->
                selectedTargets =
                    if (selectedTargets.any { it.packageName == app.packageName }) {
                        selectedTargets.filterNot { it.packageName == app.packageName }
                    } else {
                        selectedTargets + AppTarget(app.packageName, app.name)
                    }
            },
            onDone = { showAppPicker = false },
        )
    }

    if (showDeleteConfirmation && existingRule != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除「${existingRule.name}」？") },
            text = {
                Text(
                    if (currentLocks.isEmpty()) {
                        "删除后，这条规则将不再限制后续使用。"
                    } else {
                        "后续规则会被删除，但这 ${currentLocks.distinctBy {
                            it.packageName
                        }.size} 个应用的本次限制仍会持续到原定结束时间。"
                    },
                )
            },
            confirmButton = { TextButton(onClick = { onDelete(existingRule) }) { Text("删除规则", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun EditorHeading(
    title: String,
    trailing: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = NightText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(trailing, color = NightMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TypeOption(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(
                    RoundedCornerShape(11.dp),
                ).background(if (selected) Color(0x22DBE9CA) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Moonlight else NightMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyLimitEditor(
    limitMinutes: Int,
    totalLimitMinutes: Int?,
    onChange: (Int) -> Unit,
    onTotalChange: (Int?) -> Unit,
) {
    Column(Modifier.padding(horizontal = 21.dp, vertical = 23.dp)) {
        Text("每个应用，每天可以使用", color = NightMuted, style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.fillMaxWidth().padding(top = 17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$limitMinutes", color = NightText, fontSize = 58.sp, lineHeight = 62.sp, fontWeight = FontWeight.ExtraLight)
            Text("分钟", color = NightMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
        Slider(
            value = limitMinutes.toFloat(),
            onValueChange = { onChange((it / 5f).roundToInt().coerceIn(1, 144) * 5) },
            valueRange = 5f..720f,
            thumb = { Box(Modifier.size(14.dp).background(Moonlight, CircleShape)) },
            track = { sliderState ->
                val progress = ((sliderState.value - 5f) / 715f).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Moonlight.copy(alpha = 0.13f)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Moonlight),
                    )
                }
            },
            colors =
                SliderDefaults.colors(
                    activeTrackColor = Moonlight,
                    inactiveTrackColor = Moonlight.copy(alpha = 0.13f),
                ),
            modifier = Modifier.padding(top = 15.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text("5 分钟", color = NightMuted.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text("12 小时", color = NightMuted.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().padding(top = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(15, 30, 60, 120).forEach { minutes ->
                Text(
                    if (minutes < 60) "$minutes 分钟" else "${minutes / 60} 小时",
                    color = if (limitMinutes == minutes) Moonlight else NightMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (limitMinutes == minutes) FontWeight.SemiBold else FontWeight.Normal,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (limitMinutes ==
                                    minutes
                                ) {
                                    Color(0x1FD2E4BE)
                                } else {
                                    Color.Transparent
                                },
                            ).clickable { onChange(minutes) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, EditorBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("这组应用合计上限", color = NightText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "合计用完后，组内所有应用一起停止",
                        color = NightMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Switch(
                    checked = totalLimitMinutes != null,
                    onCheckedChange = { enabled ->
                        onTotalChange(
                            if (enabled) {
                                (limitMinutes + 30).coerceAtMost(MAX_TOTAL_LIMIT_MINUTES)
                            } else {
                                null
                            },
                        )
                    },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Forest,
                            checkedTrackColor = Moonlight,
                            uncheckedThumbColor = NightMuted,
                            uncheckedTrackColor = Color(0x22DCE7CC),
                        ),
                )
            }
            if (totalLimitMinutes != null) {
                val minTotal = (limitMinutes + 5).coerceAtMost(MAX_TOTAL_LIMIT_MINUTES)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        "$totalLimitMinutes",
                        color = NightText,
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        "分钟（合计）",
                        color = NightMuted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                    )
                }
                Slider(
                    value = totalLimitMinutes.toFloat(),
                    onValueChange = { onTotalChange((it / 5f).roundToInt().times(5).coerceIn(minTotal, MAX_TOTAL_LIMIT_MINUTES)) },
                    valueRange = minTotal.toFloat()..MAX_TOTAL_LIMIT_MINUTES.toFloat(),
                    thumb = { Box(Modifier.size(14.dp).background(Moonlight, CircleShape)) },
                    track = { sliderState ->
                        val span = (MAX_TOTAL_LIMIT_MINUTES - minTotal).toFloat().coerceAtLeast(1f)
                        val progress = ((sliderState.value - minTotal) / span).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(Moonlight.copy(alpha = 0.13f)),
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(Moonlight),
                            )
                        }
                    },
                    colors =
                        SliderDefaults.colors(
                            activeTrackColor = Moonlight,
                            inactiveTrackColor = Moonlight.copy(alpha = 0.13f),
                        ),
                    modifier = Modifier.padding(top = 9.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(60, 90, 120, 180, 240, 360)
                        .filter { it > limitMinutes }
                        .take(4)
                        .forEach { minutes ->
                            Text(
                                formatMinutes(minutes),
                                color = if (totalLimitMinutes == minutes) Moonlight else NightMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (totalLimitMinutes == minutes) FontWeight.SemiBold else FontWeight.Normal,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (totalLimitMinutes == minutes) Color(0x1FD2E4BE) else Color.Transparent)
                                        .clickable { onTotalChange(minutes.coerceAtLeast(minTotal)) }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                        }
                }
                Text(
                    "组内合计超过 ${formatMinutes(totalLimitMinutes)}后，全部一起停止",
                    color = NightMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TimeWindowEditor(
    startMinute: Int,
    endMinute: Int,
    allowOnly: Boolean,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    val context = LocalContext.current

    fun pickTime(
        initial: Int,
        onSelected: (Int) -> Unit,
    ) {
        TimePickerDialog(context, { _, hour, minute -> onSelected(hour * 60 + minute) }, initial / 60, initial % 60, true).show()
    }
    Column(Modifier.padding(horizontal = 22.dp, vertical = 23.dp)) {
        Text(
            if (allowOnly) "每天只留下这些应用的时刻" else "每天的安静时刻",
            color = NightMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeField("开始", startMinute, Modifier.weight(1f)) { pickTime(startMinute, onStartChange) }
            TimeField("结束", endMinute, Modifier.weight(1f)) { pickTime(endMinute, onEndChange) }
        }
        Canvas(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 35.dp, vertical = 8.dp)) {
            drawArc(
                color = Moonlight.copy(alpha = 0.28f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(0f, 0f),
                size = size.copy(height = size.height * 2f),
            )
            drawCircle(Moonlight, radius = 6f, center = Offset(size.width / 2f, 0f))
        }
        Text(
            when {
                startMinute == endMinute -> "开始和结束时间不能相同"
                startMinute > endMinute -> "跨越午夜 · 次日 ${minuteLabel(endMinute)} 自动恢复"
                allowOnly -> "每天重复 · ${minuteLabel(endMinute)} 起恢复全部应用"
                else -> "每天重复 · ${minuteLabel(endMinute)} 自动恢复"
            },
            color = if (startMinute == endMinute) MaterialTheme.colorScheme.error else NightMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    minute: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(label, color = NightMuted, style = MaterialTheme.typography.labelMedium)
        Text(
            minuteLabel(minute),
            color = NightText,
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    apps: List<InstalledApp>,
    loading: Boolean,
    selectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (InstalledApp) -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = apps.filter { it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("选择应用", color = NightText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal)
                    Text(
                        "点击勾选，可以多次添加或移除",
                        color = NightMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedPackages.size} 个已选", color = Moonlight, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭应用选择", tint = NightText)
                    }
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                placeholder = { Text("搜索应用", color = NightMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = NightMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                        ) { Icon(Icons.Outlined.Close, contentDescription = "清空搜索", tint = NightMuted) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
                colors =
                    TextFieldDefaults.colors(
                        focusedTextColor = NightText,
                        unfocusedTextColor = NightText,
                        focusedContainerColor = NightSurface,
                        unfocusedContainerColor = NightSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Moonlight,
                    ),
            )
            when {
                loading -> {
                    Box(
                        Modifier.fillMaxWidth().height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = Moonlight) }
                }

                filtered.isEmpty() -> {
                    Box(
                        Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("没有找到匹配的应用", color = NightMuted) }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(filtered, key = { it.packageName }) { app ->
                            val selected = app.packageName in selectedPackages
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(
                                            RoundedCornerShape(14.dp),
                                        ).clickable { onToggle(app) }
                                        .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName, size = 44.dp)
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.name, color = NightText, fontWeight = FontWeight.Medium)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NightMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                SelectionIndicator(selected)
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onDone,
                enabled = selectedPackages.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Moonlight,
                        contentColor = Forest,
                        disabledContainerColor = EditorGlass,
                        disabledContentColor = NightMuted,
                    ),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
            ) { Text(if (selectedPackages.isEmpty()) "请选择应用" else "完成 · ${selectedPackages.size} 个应用", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    Box(
        modifier =
            Modifier
                .size(
                    25.dp,
                ).clip(
                    CircleShape,
                ).background(
                    if (selected) Moonlight else Color.Transparent,
                ).border(if (selected) 0.dp else 1.5.dp, if (selected) Color.Transparent else NightMuted.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Outlined.Check, contentDescription = "已选择", tint = Forest, modifier = Modifier.size(16.dp))
    }
}
