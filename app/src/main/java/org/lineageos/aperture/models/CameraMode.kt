/*
 * SPDX-FileCopyrightText: 2022-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.models

enum class CameraMode(
    vararg supportedFlashModes: FlashMode,
) {
    NIGHT(
        FlashMode.OFF,
    ),
    PHOTO(
        FlashMode.OFF,
        FlashMode.AUTO,
        FlashMode.ON,
        FlashMode.TORCH,
        FlashMode.SCREEN,
    ),
    VIDEO(
        FlashMode.OFF,
        FlashMode.TORCH,
    ),
    PRO_PHOTO(
        FlashMode.OFF,
        FlashMode.ON,
        FlashMode.TORCH,
    ),
    PRO_VIDEO(
        FlashMode.OFF,
        FlashMode.TORCH,
    ),
    QR(
        FlashMode.OFF,
        FlashMode.TORCH,
    );

    val supportedFlashModes = supportedFlashModes.toSet()

    val isPhotoMode: Boolean
        get() = this == PHOTO || this == NIGHT || this == PRO_PHOTO

    val isVideoMode: Boolean
        get() = this == VIDEO || this == PRO_VIDEO

    val isProMode: Boolean
        get() = this == PRO_PHOTO || this == PRO_VIDEO
}
