/*
 * SPDX-FileCopyrightText: 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.models

import android.hardware.camera2.CameraMetadata
import androidx.camera.video.Quality

/**
 * Intermediate and standard photo resolution presets.
 */
enum class PhotoResolutionPreset(val maxLongEdge: Int) {
    FULL(0),
    QHD_PLUS_2_5K(2880),
    QHD_2K(2560),
    FHD_1080P(1920),
}

/**
 * Intermediate (2K / 2.5K) and standard video resolution presets.
 */
enum class VideoResolutionPreset(
    val underlyingQuality: Quality,
    val width: Int,
    val height: Int,
    val targetBitRate: Int,
) {
    SD_480P(Quality.SD, 854, 480, 0),
    HD_720P(Quality.HD, 1280, 720, 0),
    FHD_1080P(Quality.FHD, 1920, 1080, 0),
    QHD_2K(Quality.UHD, 2560, 1440, 24_000_000),
    QHD_PLUS_2_5K(Quality.UHD, 2880, 1620, 32_000_000),
    UHD_4K(Quality.UHD, 3840, 2160, 0);

    companion object {
        fun fromQuality(quality: Quality): VideoResolutionPreset = when (quality) {
            Quality.SD -> SD_480P
            Quality.HD -> HD_720P
            Quality.FHD -> FHD_1080P
            Quality.UHD -> UHD_4K
            else -> FHD_1080P
        }
    }
}

/**
 * Hardware-backed Camera2 White Balance modes for Pro Photo and Pro Video.
 */
enum class ProWhiteBalanceMode(val camera2Value: Int, val label: String) {
    AUTO(CameraMetadata.CONTROL_AWB_MODE_AUTO, "WB OTO"),
    INCANDESCENT(CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, "2800K"),
    FLUORESCENT(CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT, "4000K"),
    DAYLIGHT(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, "5500K"),
    CLOUDY(CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "6500K"),
    SHADE(CameraMetadata.CONTROL_AWB_MODE_SHADE, "7500K"),
}
