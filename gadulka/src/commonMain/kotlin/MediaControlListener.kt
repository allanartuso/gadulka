/*
 * Copyright 2025 Konstantin <hi@iamkonstantin.eu>.
 *  Use of this source code is governed by the BSD 3-Clause License that can be found in LICENSE file.
 */

package eu.iamkonstantin.kotlin.gadulka

/**
 * Listener for media control events triggered by system media controls
 * (e.g., notification buttons, lock screen controls, headset buttons).
 *
 * Implement this interface to handle "next" and "previous" actions from the media notification.
 */
interface MediaControlListener {
    /**
     * Called when the user taps the "next" button in media controls.
     */
    fun onNext()

    /**
     * Called when the user taps the "previous" button in media controls.
     */
    fun onPrevious()
}

