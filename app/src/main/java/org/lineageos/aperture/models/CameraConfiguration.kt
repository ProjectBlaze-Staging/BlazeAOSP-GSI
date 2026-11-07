/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.models

import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import androidx.camera.video.Quality

/**
 * Current camera runtime configuration. Note that skipping validations here won't help since paths
 * after this point assume everything has already been validated and may cause disruptions if not.
 */
sealed interface CameraConfiguration {
    /**
     * The active [Camera].
     */
    val camera: Camera

    /**
     * The current [CameraMode].
     */
    val cameraMode: CameraMode

    /**
     * Current CameraX [ExtensionMode]. Defaults to [ExtensionMode.NONE].
     */
    @ExtensionMode.Mode
    val extensionMode: Int
        get() = ExtensionMode.NONE

    /**
     * Optional Camera2's CaptureRequest options. Not respected when [extensionMode] isn't
     * [ExtensionMode.NONE].
     */
    data class Camera2Options(
        val edgeMode: EdgeMode? = null,
        val noiseReductionMode: NoiseReductionMode? = null,
        val shadingMode: ShadingMode? = null,
        val colorCorrectionAberrationMode: ColorCorrectionAberrationMode? = null,
        val distortionCorrectionMode: DistortionCorrectionMode? = null,
        val hotPixelMode: HotPixelMode? = null,
        val manualIso: Int? = null,
        val manualExposureTimeNs: Long? = null,
        val manualFocusDistance: Float? = null,
        val whiteBalanceMode: ProWhiteBalanceMode = ProWhiteBalanceMode.AUTO,
    )

    val camera2Options: Camera2Options
        get() = Camera2Options()

    /**
     * Photo capture session configuration (used for PHOTO, NIGHT, and PRO_PHOTO).
     */
    data class Photo(
        override val camera: Camera,
        override val cameraMode: CameraMode = CameraMode.PHOTO,
        override val extensionMode: Int,
        override val camera2Options: Camera2Options,
        @ImageCapture.CaptureMode val photoCaptureMode: Int,
        @AspectRatio.Ratio val photoAspectRatio: Int,
        val enableHighResolution: Boolean,
        val photoOutputFormat: PhotoOutputFormat,
        val photoResolutionPreset: PhotoResolutionPreset = PhotoResolutionPreset.FULL,
    ) : CameraConfiguration

    /**
     * Video capture session configuration (used for VIDEO and PRO_VIDEO).
     */
    data class Video(
        override val camera: Camera,
        override val cameraMode: CameraMode = CameraMode.VIDEO,
        override val camera2Options: Camera2Options,
        val videoQuality: Quality,
        val videoResolutionPreset: VideoResolutionPreset = VideoResolutionPreset.fromQuality(videoQuality),
        val videoFrameRate: FrameRate?,
        val videoDynamicRange: VideoDynamicRange,
        val videoMirrorMode: VideoMirrorMode,
        val enableVideoStabilization: Boolean,
        val videoStabilizationMode: VideoStabilizationMode = if (enableVideoStabilization) {
            VideoStabilizationMode.getMode(camera)
        } else {
            VideoStabilizationMode.OFF
        },
    ) : CameraConfiguration

    /**
     * QR scan session configuration.
     */
    data class Qr(
        override val camera: Camera,
    ) : CameraConfiguration {
        override val cameraMode = CameraMode.QR
    }
}
