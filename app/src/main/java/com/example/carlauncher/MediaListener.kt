package com.example.carlauncher
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

/** Needs user to grant Notification Access once. Shows now-playing from ANY app (Spotify, YouTube Music...). */
class MediaListener : NotificationListenerService() {
    companion object {
        var controller: MediaController? = null
        fun attach(ctx: Context, onChange: () -> Unit) {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(ctx, MediaListener::class.java)
            val pick = { l: List<MediaController>? ->
                controller = l?.firstOrNull()
                controller?.registerCallback(object : MediaController.Callback() {
                    override fun onMetadataChanged(m: MediaMetadata?) = onChange()
                    override fun onPlaybackStateChanged(s: android.media.session.PlaybackState?) = onChange()
                }); onChange()
            }
            msm.addOnActiveSessionsChangedListener({ pick(it) }, cn)
            pick(msm.getActiveSessions(cn))
        }
    }
}
