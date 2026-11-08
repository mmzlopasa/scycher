package dev.scyde.scycher.gestures

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** Actions that need system help (status bar, lock, recents) + HyperOS gesture unlock. */
object SystemActions {

    @SuppressLint("WrongConstant")
    private fun statusBar(context: Context, method: String): Boolean = try {
        val sb = context.getSystemService("statusbar")
        sb.javaClass.getMethod(method).invoke(sb)
        true
    } catch (_: Throwable) { false }

    fun openNotifications(context: Context) {
        if (!statusBar(context, "expandNotificationsPanel"))
            if (!ScycherGestureService.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) needService(context)
    }

    fun openQuickSettings(context: Context) {
        if (!statusBar(context, "expandSettingsPanel"))
            if (!ScycherGestureService.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) needService(context)
    }

    fun lockScreen(context: Context) {
        if (Build.VERSION.SDK_INT < 28 || !ScycherGestureService.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) needService(context)
    }

    fun recents(context: Context) {
        if (!ScycherGestureService.global(AccessibilityService.GLOBAL_ACTION_RECENTS)) needService(context)
    }

    fun screenshot(context: Context) {
        if (Build.VERSION.SDK_INT < 28 || !ScycherGestureService.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) needService(context)
    }

    private fun needService(context: Context) {
        Toast.makeText(context, "Enable “Scycher Gestures” in Accessibility to use this action", Toast.LENGTH_LONG).show()
        openAccessibilitySettings(context)
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, ScycherGestureService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun openAccessibilitySettings(context: Context) {
        val me = ComponentName(context, ScycherGestureService::class.java).flattenToString()
        val i = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra("android.intent.extra.COMPONENT_NAME", me)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(i) } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openNotificationAccess(context: Context) {
        val i = if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, dev.scyde.scycher.notifications.ScycherNotificationListener::class.java).flattenToString()
            )
        else Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {
            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /* ----------------------- default launcher ----------------------- */

    fun isDefaultLauncher(context: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = context.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == context.packageName
    }

    fun defaultLauncherIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME))
                return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    /* ------------------ HyperOS / MIUI full-screen gestures ------------------ */

    const val ADB_GRANT = "adb shell pm grant dev.scyde.scycher android.permission.WRITE_SECURE_SETTINGS"
    const val ADB_DIRECT = "adb shell settings put global force_fsg_nav_bar 1"

    fun canWriteSecure(context: Context) =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun hyperGesturesEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "force_fsg_nav_bar", 0) == 1

    /** Turns on HyperOS full-screen gestures even though a 3rd party launcher is default. */
    fun setHyperGestures(context: Context, on: Boolean, hideLine: Boolean? = null): Boolean = try {
        Settings.Global.putInt(context.contentResolver, "force_fsg_nav_bar", if (on) 1 else 0)
        hideLine?.let { Settings.Global.putInt(context.contentResolver, "hide_gesture_line", if (it) 1 else 0) }
        true
    } catch (_: Throwable) { false }

    fun gestureLineHidden(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "hide_gesture_line", 0) == 1

    fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("Xiaomi", true) || Build.BRAND.equals("Redmi", true) || Build.BRAND.equals("POCO", true)
}
