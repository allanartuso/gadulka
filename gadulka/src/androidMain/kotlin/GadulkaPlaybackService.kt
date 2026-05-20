/*
 * Copyright 2025 Konstantin <hi@iamkonstantin.eu>.
 *  Use of this source code is governed by the BSD 3-Clause License that can be found in LICENSE file.
 */

package eu.iamkonstantin.kotlin.gadulka

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * A foreground [MediaSessionService] that hosts the [ExoPlayer] and [MediaSession].
 *
 * This service ensures audio playback continues when the app is in the background and provides
 * media controls via the system notification (previous, play/pause, next).
 *
 * When the user removes the app from recents (task removed), the service stops itself and releases
 * all resources, so that the next time the app is opened, no previous playback state is resumed.
 */
class GadulkaPlaybackService : MediaSessionService() {

    companion object {
        /**
         * Static listener that [GadulkaPlayer] sets so the service can forward next/previous events.
         */
        internal var mediaControlListener: MediaControlListener? = null
    }

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build()

        // Wrap the player to intercept next/previous commands and forward them
        // to the app-level MediaControlListener instead of default ExoPlayer behavior.
        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() {
                Handler(Looper.getMainLooper()).post {
                    mediaControlListener?.onNext()
                }
            }

            override fun seekToNextMediaItem() {
                Handler(Looper.getMainLooper()).post {
                    mediaControlListener?.onNext()
                }
            }

            override fun seekToPrevious() {
                Handler(Looper.getMainLooper()).post {
                    mediaControlListener?.onPrevious()
                }
            }

            override fun seekToPreviousMediaItem() {
                Handler(Looper.getMainLooper()).post {
                    mediaControlListener?.onPrevious()
                }
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        // When the app task is removed, stop playback and clean up everything
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
