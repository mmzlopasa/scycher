package dev.scyde.scycher.data

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import java.security.MessageDigest

/** PIN + biometric protection for apps, hidden apps and settings. */
object Security {
    @Volatile private var unlockedAt = 0L

    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("scycher:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun verify(cfg: SecurityConfig, pin: String) = cfg.pinHash != null && cfg.pinHash == hash(pin)

    fun markUnlocked() { unlockedAt = SystemClock.elapsedRealtime() }

    /** True when the user unlocked recently enough that we shouldn't ask again. */
    fun recentlyUnlocked(cfg: SecurityConfig) =
        unlockedAt != 0L && SystemClock.elapsedRealtime() - unlockedAt < cfg.relockSeconds * 1000L

    fun lockNow() { unlockedAt = 0L }

    fun biometricAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        if (Build.VERSION.SDK_INT >= 30) {
            val bm = context.getSystemService(BiometricManager::class.java) ?: return false
            return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        }
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) || pm.hasSystemFeature(PackageManager.FEATURE_FACE)
    }

    /** Shows the system biometric sheet; [onFallback] is called for "Use PIN", cancel or errors. */
    fun authenticate(activity: Activity, title: String, onSuccess: () -> Unit, onFallback: () -> Unit) {
        if (Build.VERSION.SDK_INT < 28) { onFallback(); return }
        try {
            val exec = activity.mainExecutor
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle("Scycher")
                .setNegativeButton("Use PIN", exec) { _, _ -> onFallback() }
                .build()
            prompt.authenticate(CancellationSignal(), exec, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    markUnlocked(); onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onFallback()
                }
            })
        } catch (_: Throwable) { onFallback() }
    }
}
