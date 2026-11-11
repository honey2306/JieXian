package com.jiexian.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.jiexian.app.model.ActiveLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full screen overlay for locked apps when the accessibility service is unavailable.
 *
 * The guard cannot send the user home without accessibility, but it can cover the locked app
 * and swallow every touch. Pressing Home leaves the screen; reopening the app brings the
 * overlay back, so no interaction with it is possible without the accessibility service.
 */
class AppBlockerOverlay(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var shownPackage: String? = null

    val isShowing: Boolean get() = overlay != null

    /** Adds or updates the overlay for [lock]; repeated calls keep one view. */
    fun show(
        lock: ActiveLock,
        packageName: String,
    ) = onMain { showLocked(lock, packageName) }

    fun hide() = onMain { hideLocked() }

    /** Window changes must happen on the main thread, the guard polls from IO. */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun showLocked(
        lock: ActiveLock,
        packageName: String,
    ) {
        if (overlay != null && shownPackage == packageName) return
        hideLocked()
        val view = buildView(lock)
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // Touchable on purpose: the overlay must swallow taps meant for the app below.
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Fill the whole display, including the areas behind the system bars.
                        fitInsetsTypes = 0
                    }
                }
        runCatching { windowManager?.addView(view, params) }
            .onSuccess {
                overlay = view
                shownPackage = packageName
            }
            .onFailure { android.util.Log.w("JieXianOverlay", "addView failed", it) }
    }

    private fun hideLocked() {
        val view = overlay ?: return
        runCatching { windowManager?.removeView(view) }
        overlay = null
        shownPackage = null
    }

    private fun buildView(lock: ActiveLock): View {
        val density = context.resources.displayMetrics.density

        fun dp(value: Int): Int = (value * density).toInt()

        val root =
            FrameLayout(context).apply {
                setBackgroundColor(Color.parseColor("#0B2622"))
                isClickable = true
                isFocusable = true
            }
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(32), dp(32), dp(32), dp(32))
            }

        fun text(
            value: String,
            size: Float,
            color: Int,
            top: Int = 0,
            bold: Boolean = false,
        ): TextView =
            TextView(context).apply {
                this.text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(top), 0, 0)
            }

        column.addView(text("界限 · 已到时间", 13f, MUTED))
        column.addView(text(lock.ruleName.ifBlank { "限制中" }, 30f, TEXT, top = 18, bold = true))
        column.addView(text(lock.detail, 14f, MUTED, top = 14))
        column.addView(
            text(
                "${unlockTime(lock.unlockAt)} 后自动恢复",
                22f,
                TEXT,
                top = 22,
            ),
        )
        column.addView(
            text(
                "这里是界限的覆盖层，按 Home 键可回到桌面；再次打开这个应用仍会被拦截。",
                12f,
                MUTED,
                top = 26,
            ),
        )
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return root
    }

    private fun unlockTime(unlockAt: Long): String =
        DateTimeFormatter
            .ofPattern("HH:mm", Locale.getDefault())
            .format(Instant.ofEpochMilli(unlockAt).atZone(ZoneId.systemDefault()))

    companion object {
        private const val TEXT = 0xFFDAE7CC.toInt()
        private const val MUTED = 0x99DAE7CC.toInt()

    }
}
