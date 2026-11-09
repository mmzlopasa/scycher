package dev.scyde.scycher.notifications

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val controller: MediaController,
)

/** Provides notification badges and the Now Playing widget. */
class ScycherNotificationListener : NotificationListenerService() {

    private var sessions: MediaSessionManager? = null
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> bindControllers(list) }
    private val controllerCallbacks = HashMap<MediaController, MediaController.Callback>()

    override fun onListenerConnected() {
        connected.value = true
        refreshBadges()
        try {
            val sm = getSystemService(MediaSessionManager::class.java)
            sessions = sm
            val cn = ComponentName(this, ScycherNotificationListener::class.java)
            sm.addOnActiveSessionsChangedListener(sessionListener, cn, Handler(Looper.getMainLooper()))
            bindControllers(sm.getActiveSessions(cn))
        } catch (_: Throwable) {}
    }

    override fun onListenerDisconnected() {
        connected.value = false
        try { sessions?.removeOnActiveSessionsChangedListener(sessionListener) } catch (_: Throwable) {}
        unbindAll()
        badges.value = emptyMap(); nowPlaying.value = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { refreshBadges() }

    // progress notifications update every second – coalesce instead of re-reading all notifications each time
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.isOngoing == true && badges.value.containsKey(sbn.packageName).not()) return
        handler.removeCallbacks(refresh); handler.postDelayed(refresh, 300)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        handler.removeCallbacks(refresh); handler.postDelayed(refresh, 300)
    }

    private fun refreshBadges() {
        val list = try { activeNotifications ?: emptyArray() } catch (_: Throwable) { emptyArray() }
        val counts = HashMap<String, Int>()
        for (n in list) {
            if (n.isOngoing) continue
            val flags = n.notification.flags
            if (flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) continue
            val num = n.notification.number.takeIf { it > 0 } ?: 1
            counts[n.packageName] = (counts[n.packageName] ?: 0) + num
        }
        badges.value = counts
    }

    private fun unbindAll() {
        controllerCallbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        controllerCallbacks.clear()
    }

    private fun bindControllers(list: List<MediaController>?) {
        unbindAll()
        val controllers = list ?: emptyList()
        for (c in controllers) {
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish(controllers)
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish(controllers)
                override fun onSessionDestroyed() = publish(controllers)
            }
            c.registerCallback(cb, Handler(Looper.getMainLooper()))
            controllerCallbacks[c] = cb
        }
        publish(controllers)
    }

    private fun publish(controllers: List<MediaController>) {
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null }
        if (c == null) { nowPlaying.value = null; return }
        val md = c.metadata
        nowPlaying.value = NowPlaying(
            pkg = c.packageName,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            controller = c,
        )
    }

    companion object {
        val badges = MutableStateFlow<Map<String, Int>>(emptyMap())
        val nowPlaying = MutableStateFlow<NowPlaying?>(null)
        val connected = MutableStateFlow(false)
        val badgeFlow: StateFlow<Map<String, Int>> get() = badges

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.contains(context.packageName + "/")
        }
    }
}
