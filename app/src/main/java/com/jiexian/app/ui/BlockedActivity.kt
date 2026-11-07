package com.jiexian.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiexian.app.MainActivity
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.RuleType
import com.jiexian.app.ui.components.AppIcon
import com.jiexian.app.ui.components.BrandMark
import com.jiexian.app.ui.theme.Forest
import com.jiexian.app.ui.theme.JieXianTheme
import com.jiexian.app.ui.theme.Moonlight
import com.jiexian.app.ui.theme.NightMuted
import com.jiexian.app.ui.theme.NightText
import com.jiexian.app.util.unlockTimeLabel

class BlockedActivity : ComponentActivity() {
    private val lock = mutableStateOf<ActiveLock?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lock.value = intent.toLock()
        if (lock.value == null) {
            finish()
            return
        }
        setContent {
            JieXianTheme {
                lock.value?.let { currentLock ->
                    BackHandler { goHome() }
                    BlockedScreen(
                        lock = currentLock,
                        onHome = { goHome() },
                        onViewRules = {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lock.value = intent.toLock()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun Intent.toLock(): ActiveLock? {
        val id = getStringExtra(EXTRA_ID) ?: return null
        val type = getStringExtra(EXTRA_TYPE)?.let { runCatching { RuleType.valueOf(it) }.getOrNull() } ?: return null
        return ActiveLock(
            id = id,
            sourceRuleId = getStringExtra(EXTRA_RULE_ID).orEmpty(),
            ruleName = getStringExtra(EXTRA_RULE_NAME).orEmpty().ifBlank { getStringExtra(EXTRA_APP_NAME).orEmpty() },
            packageName = getStringExtra(EXTRA_PACKAGE).orEmpty(),
            appName = getStringExtra(EXTRA_APP_NAME).orEmpty(),
            type = type,
            unlockAt = getLongExtra(EXTRA_UNLOCK_AT, 0L),
            detail = getStringExtra(EXTRA_DETAIL).orEmpty(),
            startedAt = getLongExtra(EXTRA_STARTED_AT, 0L),
        )
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_RULE_ID = "rule_id"
        private const val EXTRA_RULE_NAME = "rule_name"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_APP_NAME = "app_name"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_UNLOCK_AT = "unlock_at"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_STARTED_AT = "started_at"

        fun createIntent(
            context: Context,
            lock: ActiveLock,
        ): Intent =
            Intent(context, BlockedActivity::class.java).apply {
                putExtra(EXTRA_ID, lock.id)
                putExtra(EXTRA_RULE_ID, lock.sourceRuleId)
                putExtra(EXTRA_RULE_NAME, lock.ruleName)
                putExtra(EXTRA_PACKAGE, lock.packageName)
                putExtra(EXTRA_APP_NAME, lock.appName)
                putExtra(EXTRA_TYPE, lock.type.name)
                putExtra(EXTRA_UNLOCK_AT, lock.unlockAt)
                putExtra(EXTRA_DETAIL, lock.detail)
                putExtra(EXTRA_STARTED_AT, lock.startedAt)
            }
    }
}

@Composable
private fun BlockedScreen(
    lock: ActiveLock,
    onHome: () -> Unit,
    onViewRules: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .blockedAmbient()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(size = 30.dp, color = Moonlight)
            Spacer(Modifier.width(10.dp))
            Text("界限", color = NightText, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (lock.type == RuleType.DAILY_LIMIT) "今日额度" else "禁用时段",
                color = NightMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(148.dp)) {
                drawCircle(Moonlight.copy(alpha = 0.035f), radius = size.minDimension / 2f)
                drawCircle(Moonlight.copy(alpha = 0.08f), radius = size.minDimension * 0.38f)
            }
            AppIcon(lock.packageName, size = 76.dp)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 14.dp)
                        .size(34.dp)
                        .background(Moonlight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Forest, modifier = Modifier.size(17.dp))
            }
        }

        Text(
            if (lock.type == RuleType.DAILY_LIMIT) "今天，先到这里" else "此刻，留一点安静",
            color = NightText,
            fontFamily = FontFamily.Serif,
            fontSize = 31.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 22.dp),
        )
        Text(
            "${lock.appName} 已进入本次限制",
            color = NightMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 25.dp),
        ) {
            Text(
                unlockTimeLabel(lock.unlockAt),
                color = Moonlight,
                fontSize = 54.sp,
                lineHeight = 58.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-1).sp,
            )
            Text(
                "恢复使用",
                color = NightMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, bottom = 9.dp),
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0x0FDCE7CC),
            modifier = Modifier.fillMaxWidth().padding(top = 25.dp),
        ) {
            Column(Modifier.padding(horizontal = 19.dp, vertical = 17.dp)) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("来源规则", color = NightMuted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(lock.ruleName, color = NightText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Text(lock.detail, color = NightMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Text(
            "本次限制没有临时加时",
            color = NightMuted.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 15.dp),
        )

        Spacer(Modifier.weight(1.15f))
        Button(
            onClick = onHome,
            shape = RoundedCornerShape(17.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Outlined.Home, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("回到桌面", fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onViewRules, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = NightMuted, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("调整后续规则", color = NightMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun Modifier.blockedAmbient(): Modifier =
    background(
        Brush.linearGradient(
            colors = listOf(Color(0xFF173D33), Color(0xFF0A2420), Color(0xFF071D1A)),
            start = Offset.Zero,
            end = Offset.Infinite,
        ),
    )
