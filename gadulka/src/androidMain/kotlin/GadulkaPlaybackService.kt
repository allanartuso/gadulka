/*
 * Copyright 2025 Konstantin <hi@iamkonstantin.eu>.
 *  Use of this source code is governed by the BSD 3-Clause License that can be found in LICENSE file.
 */

package eu.iamkonstantin.kotlin.gadulka

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * A foreground [MediaSessionService] that hosts the [ExoPlayer] and [MediaSession].
 *
 * This service ensures audio playback continues when the app is in the background and provides
 * media controls via the system notification. When the user removes the app from recents
 * (task removed), the service stops itself and releases all resources, so that the next time the
 * app is opened, no previous playback state is resumed.
 */
class GadulkaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        // When the app task is removed, stop playback and clean up everything
        mediaSession?.player?.let { player ->
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                // If the player is not actively playing, stop the service immediately
                stopSelf()
            }
        }
        // Always stop and clean up when task is removed per user requirement
        mediaSession?.player?.stop()
        mediaSession?.player?.clearMediaItems()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

