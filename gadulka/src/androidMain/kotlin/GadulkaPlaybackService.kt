/*
 * Copyright 2025 Konstantin <hi@iamkonstantin.eu>.
 *  Use of this source code is governed by the BSD 3-Clause License that can be found in LICENSE file.
 */

package eu.iamkonstantin.kotlin.gadulka

import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false
    private var hasAudioFocus = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained — resume if we were playing before
                hasAudioFocus = true
                if (wasPlayingBeforeFocusLoss) {
                    mediaSession?.player?.play()
                    wasPlayingBeforeFocusLoss = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Focus lost — pause and remember we were playing
                hasAudioFocus = false
                val player = mediaSession?.player
                if (player?.isPlaying == true) {
                    wasPlayingBeforeFocusLoss = true
                    player.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // We could duck, but for music it's better to pause
                hasAudioFocus = false
                val player = mediaSession?.player
                if (player?.isPlaying == true) {
                    wasPlayingBeforeFocusLoss = true
                    player.pause()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Request audio focus when playback starts, abandon when it stops
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    requestAudioFocus()
                }
            }
        })

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

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(object : MediaSession.Callback {})
            .build()
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
        abandonAudioFocus()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener, Handler(Looper.getMainLooper()))
                .build()
            audioFocusRequest = request
            val result = am.requestAudioFocus(request)
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        } else {
            @Suppress("DEPRECATION")
            val result = am.requestAudioFocus(
                audioFocusListener,
                android.media.AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusListener)
        }
        hasAudioFocus = false
    }
}
