/*
 * Copyright 2025 Konstantin <hi@iamkonstantin.eu>.
 *  Use of this source code is governed by the BSD 3-Clause License that can be found in LICENSE file.
 */

package eu.iamkonstantin.kotlin.gadulka

import android.content.ComponentName
import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.kdroid.androidcontextprovider.ContextProvider

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class GadulkaPlayer actual constructor() {

    private var controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null
    private var errorListener: ErrorListener? = null
    private var mediaControlListener: MediaControlListener? = null

    init {
        val context = ContextProvider.getContext()
        val sessionToken = SessionToken(context, ComponentName(context, GadulkaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                setupListener()
            },
            MoreExecutors.directExecutor()
        )
    }

    actual fun play(url: String) {
        val controller = mediaController ?: return

        if (controller.isPlaying) {
            stop()
        }

        if (controller.isCommandAvailable(Player.COMMAND_PREPARE)) controller.prepare()

        val mediaItem = MediaItem.fromUri(url)

        if (controller.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM)) controller.setMediaItem(mediaItem)

        if (controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) controller.play()
    }

    @OptIn(UnstableApi::class)
    actual fun play() {
        val controller = mediaController ?: return
        if (controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            if (currentPlayerState() == GadulkaPlayerState.IDLE)
                seekTo(0)
            controller.play()
        }
    }

    /**
     * Android-specific implementation of the [play] method which uses a ContentResolver to calculate the Uri of a raw file resource bundled with the app.
     */
    fun play(rawResourceId: Int) {
        val uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .appendPath("$rawResourceId")
            .build().toString()
        play(uri)
    }

    actual fun currentPosition(): Long? {
        val controller = mediaController ?: return null
        if (controller.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
            return controller.currentPosition
        }
        return null
    }

    actual fun currentDuration(): Long? {
        val controller = mediaController ?: return null
        if (controller.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
            if (controller.duration >= 0) return controller.duration
        }
        return null
    }

    @UnstableApi
    actual fun currentPlayerState(): GadulkaPlayerState? {
        val controller = mediaController ?: return null
        val state = controller.playbackState
        val playWhenReady = controller.playWhenReady
        return when {
            state == Player.STATE_READY && playWhenReady -> GadulkaPlayerState.PLAYING
            state == Player.STATE_READY && !playWhenReady -> GadulkaPlayerState.PAUSED
            state == Player.STATE_BUFFERING -> GadulkaPlayerState.BUFFERING
            state == Player.STATE_IDLE -> GadulkaPlayerState.IDLE
            state == Player.STATE_ENDED -> GadulkaPlayerState.IDLE
            else -> GadulkaPlayerState.IDLE
        }
    }

    actual fun release() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        MediaController.releaseFuture(controllerFuture)
        mediaController = null
    }

    actual fun stop() {
        val controller = mediaController ?: return
        controller.stop()
        controller.clearMediaItems()
    }

    actual fun pause() {
        mediaController?.pause()
    }

    actual fun currentVolume(): Float? {
        val controller = mediaController ?: return null
        if (controller.isCommandAvailable(Player.COMMAND_GET_VOLUME)) {
            return controller.volume
        }
        return null
    }

    actual fun setVolume(volume: Float) {
        val controller = mediaController ?: return
        if (!controller.isCommandAvailable(Player.COMMAND_GET_VOLUME)) return
        controller.volume = volume
    }

    actual fun setRate(rate: Float) {
        val controller = mediaController ?: return
        if (!controller.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return
        controller.playbackParameters = controller.playbackParameters.withSpeed(rate)
    }

    actual fun seekTo(time: Long) {
        val controller = mediaController ?: return
        if (!controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
        controller.seekTo(time)
    }

    actual fun setOnErrorListener(listener: ErrorListener) {
        errorListener = listener
    }

    actual fun setOnMediaControlListener(listener: MediaControlListener) {
        mediaControlListener = listener
        GadulkaPlaybackService.mediaControlListener = listener
    }

    private fun setupListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorListener?.onError(error.errorCodeName)
                super.onPlayerError(error)
            }
        })

    }
}
