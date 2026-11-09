package dev.scyde.scycher.gestures

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.NavGestureConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Scycher's own navigation gestures. Third-party launchers can't use HyperOS full-screen
 * gestures out of the box, so this service draws thin invisible edge strips that turn swipes
 * into Home / Back / Recents. It also lets the launcher lock the screen or open the shade.
 */
class ScycherGestureService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var cfg = NavGestureConfig()
    private val views = mutableListOf<View>()
    private var zones = mutableListOf<Pair<View, WindowManager.LayoutParams>>()
    private var backIndicator: BackIndicatorView? = null
    private var backIndicatorParams: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        running.value = true
        wm = getSystemService(WindowManager::class.java)
        val store = (application as ScycherApp).store
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            store.config.map { it.nav }.distinctUntilChanged().onEach { cfg = it; rebuild() }.launchIn(s)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        main.post { rebuild() }
    }

    /** Package of the window in front, from window-state events (no screen content is read). */
    @Volatile private var frontPkg: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { if (it != "android") frontPkg = it }
        }
    }
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        removeAll()
        scope?.cancel(); scope = null
        if (instance === this) instance = null
        running.value = false
    }

    private fun removeAll() {
        for (v in views) runCatching { wm.removeView(v) }
        views.clear(); zones.clear()
        backIndicator = null
    }

    private fun dp(v: Number) = v.toFloat() * resources.displayMetrics.density

    private fun baseParams(w: Int, h: Int, gravity: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    ).apply {
        this.gravity = gravity
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun add(v: View, p: WindowManager.LayoutParams, zone: Boolean) {
        try {
            wm.addView(v, p); views += v
            if (zone) zones += v to p
        } catch (_: Throwable) {}
    }

    private fun rebuild() {
        removeAll()
        val dm = resources.displayMetrics
        val sw = dm.widthPixels; val sh = dm.heightPixels
        if (cfg.homeRescue && SystemActions.hyperGesturesEnabled(this)) {
            // HyperOS draws its own gestures; we only guard the bottom edge so Home always works.
            val w = (sw * cfg.bottomWidthPercent / 100f).toInt()
            add(BottomZone(this, rescue = true), baseParams(w, dp(cfg.bottomHeightDp.coerceAtMost(20)).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, true), true)
            return
        }
        if (!cfg.enabled) return

        if (cfg.showHandle && cfg.bottomHome) {
            val handle = HandleView(this, cfg)
            add(handle, baseParams(dp(cfg.handleWidthDp).toInt(), dp(14).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, false), false)
        }
        if (cfg.bottomHome) {
            val w = (sw * cfg.bottomWidthPercent / 100f).toInt()
            add(BottomZone(this), baseParams(w, dp(cfg.bottomHeightDp).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, true), true)
        }
        if (cfg.sideBack) {
            val h = (sh * cfg.edgeHeightPercent / 100f).toInt()
            val w = dp(cfg.edgeWidthDp).toInt()
            if (cfg.backLeft) add(SideZone(this, true), baseParams(w, h, Gravity.START or Gravity.CENTER_VERTICAL, true), true)
            if (cfg.backRight) add(SideZone(this, false), baseParams(w, h, Gravity.END or Gravity.CENTER_VERTICAL, true), true)
            if (cfg.backIndicator) {
                val bi = BackIndicatorView(this)
                val p = baseParams(dp(56).toInt(), dp(180).toInt(), Gravity.TOP or Gravity.START, false)
                add(bi, p, false)
                backIndicator = bi; backIndicatorParams = p
            }
        }
    }

    /* --------------------------- feedback --------------------------- */

    private fun tick() {
        if (!cfg.vibrate) return
        try {
            val v = getSystemService(Vibrator::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            else v.vibrate(VibrationEffect.createOneShot(12, 120))
        } catch (_: Throwable) {}
    }

    private fun showBack(left: Boolean, y: Float, progress: Float) {
        val bi = backIndicator ?: return
        val p = backIndicatorParams ?: return
        bi.left = left; bi.progress = progress
        val sw = resources.displayMetrics.widthPixels
        p.x = if (left) 0 else sw - p.width
        p.y = (y - p.height / 2f).toInt()
        runCatching { wm.updateViewLayout(bi, p) }
        bi.invalidate()
    }

    private fun forwardTap(x: Float, y: Float) {
        if (!cfg.forwardTaps) return
        setZonesTouchable(false)
        main.postDelayed({
            try {
                val path = Path().apply { moveTo(x, y) }
                val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 30)).build()
                val ok = dispatchGesture(g, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { main.postDelayed({ setZonesTouchable(true) }, 30) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { main.postDelayed({ setZonesTouchable(true) }, 30) }
                }, main)
                if (!ok) setZonesTouchable(true)
            } catch (_: Throwable) { setZonesTouchable(true) }
        }, 24)
    }

    private fun setZonesTouchable(t: Boolean) {
        for ((v, p) in zones) {
            p.flags = if (t) p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { wm.updateViewLayout(v, p) }
        }
    }

    /* ----------------------------- zones ----------------------------- */

    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private inner class BottomZone(ctx: Context, private val rescue: Boolean = false) : View(ctx) {
        private var sx = 0f; private var sy = 0f; private var rawX = 0f; private var rawY = 0f
        private var pkgAtDown: String? = null
        /**
         * The system gesture handler stole the swipe (we got CANCEL). Give it a moment; if the same
         * app is still in front, the system failed to go home (HyperOS + 3rd party launcher), so we do.
         */
        private val watchdog = Runnable {
            val before = pkgAtDown
            if (before != null && before == frontPkg && before != packageName) performGlobalAction(GLOBAL_ACTION_HOME)
        }
        private var armed = false; private var recentsFired = false; private var downTime = 0L
        private var lastX = 0f; private var lastY = 0f
        private val hold = Runnable {
            if (armed && cfg.holdForRecents) {
                recentsFired = true; tick(); performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val threshold = dp(26) / cfg.sensitivity
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.x; sy = e.y; rawX = e.rawX; rawY = e.rawY; lastX = e.rawX; lastY = e.rawY
                    armed = false; recentsFired = false; downTime = e.eventTime
                    pkgAtDown = frontPkg; main.removeCallbacks(watchdog)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - rawY
                    if (!armed && -dy > threshold) { armed = true; tick(); main.postDelayed(hold, 320) }
                    if (armed && hypot(e.rawX - lastX, e.rawY - lastY) > dp(10)) {
                        lastX = e.rawX; lastY = e.rawY
                        main.removeCallbacks(hold); main.postDelayed(hold, 320)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(hold)
                    val moved = hypot(e.rawX - rawX, e.rawY - rawY)
                    if (armed && !recentsFired) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    } else if (!armed && moved < dp(10) && e.eventTime - downTime < 400) {
                        forwardTap(e.rawX, e.rawY)
                    }
                    armed = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(hold)
                    if (rescue && (armed || rawY - e.rawY > dp(6))) main.postDelayed(watchdog, 700)
                    armed = false
                }
            }
            return true
        }
    }

    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private inner class SideZone(ctx: Context, private val left: Boolean) : View(ctx) {
        private var rawX = 0f; private var rawY = 0f; private var armed = false; private var downTime = 0L

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val threshold = dp(36) / cfg.sensitivity
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { rawX = e.rawX; rawY = e.rawY; armed = false; downTime = e.eventTime }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - rawX) * if (left) 1 else -1
                    val progress = (dx / (threshold * 2.2f)).coerceIn(0f, 1f)
                    if (dx > dp(4)) showBack(left, e.rawY, progress)
                    if (!armed && dx > threshold && abs(e.rawY - rawY) < dx * 1.6f) { armed = true; tick() }
                    else if (armed && dx < threshold * 0.6f) armed = false
                }
                MotionEvent.ACTION_UP -> {
                    showBack(left, e.rawY, 0f)
                    val dx = abs(e.rawX - rawX); val dy = abs(e.rawY - rawY)
                    if (armed) performGlobalAction(GLOBAL_ACTION_BACK)
                    else if (dx < dp(8) && dy < dp(8) && e.eventTime - downTime < 400) forwardTap(e.rawX, e.rawY)
                    armed = false
                }
                MotionEvent.ACTION_CANCEL -> { showBack(left, e.rawY, 0f); armed = false }
            }
            return true
        }
    }

    @SuppressLint("ViewConstructor")
    private class HandleView(ctx: Context, private val c: NavGestureConfig) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.handleColor.toInt()
            alpha = (c.handleOpacity * 255).toInt()
        }
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
        override fun onDraw(canvas: Canvas) {
            val h = 5f * resources.displayMetrics.density
            val top = (height - h) / 2f
            canvas.drawRoundRect(RectF(0f, top + 1, width.toFloat(), top + h + 1), h, h, shadow)
            canvas.drawRoundRect(RectF(0f, top, width.toFloat(), top + h), h, h, paint)
        }
    }

    private class BackIndicatorView(ctx: Context) : View(ctx) {
        var left = true
        var progress = 0f
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC1C1C1E.toInt() }
        private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            if (progress <= 0.01f) return
            val d = resources.displayMetrics.density
            val w = width.toFloat(); val h = height.toFloat()
            val bulge = min(w, 6 * d + progress * (w - 10 * d))
            canvas.save()
            if (!left) canvas.scale(-1f, 1f, w / 2, h / 2)
            path.reset()
            path.moveTo(0f, 0f)
            path.cubicTo(0f, h * 0.25f, bulge, h * 0.3f, bulge, h / 2)
            path.cubicTo(bulge, h * 0.7f, 0f, h * 0.75f, 0f, h)
            path.close()
            bg.alpha = (220 * min(1f, progress * 1.6f)).toInt()
            canvas.drawPath(path, bg)
            val ax = bulge * 0.55f; val ay = h / 2; val s = 6 * d * min(1f, progress * 1.5f)
            arrow.strokeWidth = 2.5f * d
            arrow.alpha = (255 * min(1f, progress * 2f)).toInt()
            canvas.drawLine(ax + s * 0.5f, ay - s, ax - s * 0.5f, ay, arrow)
            canvas.drawLine(ax - s * 0.5f, ay, ax + s * 0.5f, ay + s, arrow)
            canvas.restore()
        }
    }

    companion object {
        @Volatile var instance: ScycherGestureService? = null
            private set
        val running = MutableStateFlow(false)

        fun global(action: Int): Boolean = instance?.performGlobalAction(action) ?: false

        /** Re-create the edge strips (e.g. after HyperOS gestures were switched on/off). */
        fun refresh() { instance?.let { s -> s.main.post { s.rebuild() } } }
    }
}
