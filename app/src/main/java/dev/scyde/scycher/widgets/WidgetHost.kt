package dev.scyde.scycher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

/** AppWidgetHostView that lets the launcher detect long-press over a widget. */
class ScycherWidgetView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private var downX = 0f; private var downY = 0f
    private var fired = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable {
        if (parent != null && isPressedState) {
            fired = true
            onLongPress?.invoke()
            // cancel the child's gesture
            val now = android.os.SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            super.dispatchTouchEvent(cancel); cancel.recycle()
        }
    }
    private var isPressedState = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; fired = false; isPressedState = true
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (hypot(ev.x - downX, ev.y - downY) > slop) { isPressedState = false; removeCallbacks(longPress) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { isPressedState = false; removeCallbacks(longPress) }
        }
        return fired
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (fired) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) fired = false
            return true
        }
        return super.onTouchEvent(event)
    }

    fun applySize(wDp: Float, hDp: Float) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp)))
            } else {
                @Suppress("DEPRECATION")
                updateAppWidgetSize(null, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
            }
        } catch (_: Throwable) {}
    }
}

class ScycherWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        ScycherWidgetView(context)

    companion object { const val HOST_ID = 0x5C7D }
}

class WidgetManager(private val context: Context) {
    val host = ScycherWidgetHost(context.applicationContext)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val views = HashMap<Int, ScycherWidgetView>()

    fun providers(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders.filter { it.provider != null }
    } catch (_: Throwable) { emptyList() }

    fun info(id: Int): AppWidgetProviderInfo? = try { manager.getAppWidgetInfo(id) } catch (_: Throwable) { null }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, provider: ComponentName): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, provider)
    } catch (_: Throwable) { false }

    fun delete(id: Int) {
        views.remove(id)
        try { host.deleteAppWidgetId(id) } catch (_: Throwable) {}
    }

    /** Views are cached so pages can recompose without re-inflating RemoteViews. */
    fun view(context: Context, id: Int): ScycherWidgetView? {
        views[id]?.let { v ->
            (v.parent as? android.view.ViewGroup)?.removeView(v)
            return v
        }
        val info = info(id) ?: return null
        val v = host.createView(context.applicationContext, id, info) as ScycherWidgetView
        views[id] = v
        return v
    }

    fun start() = try { host.startListening() } catch (_: Throwable) {}
    fun stop() = try { host.stopListening() } catch (_: Throwable) {}

    /** Removes widget ids that are allocated but no longer on the home screen. */
    fun cleanup(used: Set<Int>) {
        try {
            for (id in host.appWidgetIds) if (id !in used) delete(id)
        } catch (_: Throwable) {}
    }
}
