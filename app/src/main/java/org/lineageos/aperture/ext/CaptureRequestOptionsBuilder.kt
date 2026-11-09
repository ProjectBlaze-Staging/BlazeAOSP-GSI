/*
 * SPDX-FileCopyrightText: 2022-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.ext

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ExperimentalZeroShutterLag
import org.lineageos.aperture.models.Camera
import org.lineageos.aperture.models.CameraConfiguration
import org.lineageos.aperture.models.ColorCorrectionAberrationMode
import org.lineageos.aperture.models.DistortionCorrectionMode
import org.lineageos.aperture.models.EdgeMode
import org.lineageos.aperture.models.FrameRate
import org.lineageos.aperture.models.HotPixelMode
import org.lineageos.aperture.models.NoiseReductionMode
import org.lineageos.aperture.models.ProWhiteBalanceMode
import org.lineageos.aperture.models.ShadingMode
import org.lineageos.aperture.models.VideoStabilizationMode

@OptIn(ExperimentalCamera2Interop::class)
fun CaptureRequestOptions.Builder.setFrameRate(
    frameRate: FrameRate?
) = frameRate?.let {
    setCaptureRequestOption(
        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(it.value, it.value)
    )
} ?: clearCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)

@OptIn(ExperimentalCamera2Interop::class)
private fun <T : Any> CaptureRequestOptions.Builder.trySetVendorKey(
    name: String,
    type: Class<T>,
    value: T,
) {
    runCatching {
        val ctor = CaptureRequest.Key::class.java.getDeclaredConstructor(
            String::class.java,
            Class::class.java,
        )
        ctor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val key = ctor.newInstance(name, type) as CaptureRequest.Key<T>
        setCaptureRequestOption(key, value)
    }
}

@OptIn(ExperimentalCamera2Interop::class)
fun CaptureRequestOptions.Builder.setVideoStabilizationMode(
    videoStabilizationMode: VideoStabilizationMode?,
    camera: Camera? = null,
) = apply {
    // Universal vendor client & MFNR hints (ignored safely if HAL does not expose them)
    trySetVendorKey("samsung.android.control.cameraClientIndex", Int::class.javaObjectType, 1)
    trySetVendorKey(
        "org.quic.camera.CustomNoiseReduction.CustomNoiseReduction",
        Byte::class.javaObjectType,
        1.toByte()
    )

    videoStabilizationMode?.let { mode ->
        val eisMode = when (mode) {
            VideoStabilizationMode.OFF,
            VideoStabilizationMode.OIS -> CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF

            VideoStabilizationMode.ON -> CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
            VideoStabilizationMode.ON_PREVIEW ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                } else {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                }

            VideoStabilizationMode.EIS_OIS ->
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    camera?.supportedVideoStabilizationModes?.contains(
                        VideoStabilizationMode.ON_PREVIEW
                    ) == true
                ) {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                } else {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                }
        }
        setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, eisMode)

        val eisEnabled = if (eisMode != CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF) {
            1.toByte()
        } else {
            0.toByte()
        }
        trySetVendorKey("org.quic.camera.eis3enable.EISV3Enable", Byte::class.javaObjectType, eisEnabled)
        trySetVendorKey("org.codeaurora.qcamera3.eis3.enable", Byte::class.javaObjectType, eisEnabled)

        if (camera?.supportsOis == true) {
            setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                if (mode.usesOis) {
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                } else {
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                }
            )
        } else {
            clearCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE)
        }
    } ?: run {
        clearCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE)
        clearCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE)
    }
}

@OptIn(ExperimentalCamera2Interop::class)
fun CaptureRequestOptions.Builder.setProManualOptions(
    options: CameraConfiguration.Camera2Options,
    camera: Camera,
) = apply {
    val iso = options.manualIso?.let { raw ->
        camera.isoRange?.let { raw.coerceIn(it.start, it.endInclusive) } ?: raw
    }
    val exposureNs = options.manualExposureTimeNs?.let { raw ->
        camera.exposureTimeRange?.let { raw.coerceIn(it.start, it.endInclusive) } ?: raw
    }

    if (iso != null || exposureNs != null) {
        setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CameraMetadata.CONTROL_AE_MODE_OFF
        )
        val effectiveIso = iso ?: (camera.isoRange?.let { 200.coerceIn(it.start, it.endInclusive) } ?: 200)
        val effectiveExposureNs = exposureNs ?: (camera.exposureTimeRange?.let {
            16_666_666L.coerceIn(it.start, it.endInclusive)
        } ?: 16_666_666L)
        setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, effectiveIso)
        setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, effectiveExposureNs)
    } else {
        clearCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE)
        clearCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY)
        clearCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME)
    }

    val focusDistance = options.manualFocusDistance
    if (focusDistance != null && camera.minFocusDistance > 0f) {
        setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            CameraMetadata.CONTROL_AF_MODE_OFF
        )
        setCaptureRequestOption(
            CaptureRequest.LENS_FOCUS_DISTANCE,
            focusDistance.coerceIn(0f, camera.minFocusDistance)
        )
    } else {
        clearCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)
        clearCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE)
    }

    if (options.whiteBalanceMode != ProWhiteBalanceMode.AUTO) {
        setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            options.whiteBalanceMode.camera2Value
        )
    } else {
        clearCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE)
    }
}

@OptIn(ExperimentalCamera2Interop::class, ExperimentalZeroShutterLag::class)
fun CaptureRequestOptions.Builder.setEdgeMode(
    edgeMode: EdgeMode?,
) = edgeMode?.let {
    setCaptureRequestOption(
        CaptureRequest.EDGE_MODE,
        when (it) {
            EdgeMode.OFF -> CameraMetadata.EDGE_MODE_OFF
            EdgeMode.FAST -> CameraMetadata.EDGE_MODE_FAST
            EdgeMode.HIGH_QUALITY -> CameraMetadata.EDGE_MODE_HIGH_QUALITY
            EdgeMode.ZERO_SHUTTER_LAG -> CameraMetadata.EDGE_MODE_ZERO_SHUTTER_LAG
        }
    )
} ?: clearCaptureRequestOption(CaptureRequest.EDGE_MODE)

@OptIn(ExperimentalCamera2Interop::class, ExperimentalZeroShutterLag::class)
fun CaptureRequestOptions.Builder.setNoiseReductionMode(
    noiseReductionMode: NoiseReductionMode?
) = noiseReductionMode?.let {
    setCaptureRequestOption(
        CaptureRequest.NOISE_REDUCTION_MODE,
        when (it) {
            NoiseReductionMode.OFF -> CameraMetadata.NOISE_REDUCTION_MODE_OFF
            NoiseReductionMode.FAST -> CameraMetadata.NOISE_REDUCTION_MODE_FAST
            NoiseReductionMode.HIGH_QUALITY -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
            NoiseReductionMode.MINIMAL -> CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL
            NoiseReductionMode.ZERO_SHUTTER_LAG ->
                CameraMetadata.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG
        }
    )
} ?: clearCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE)

@OptIn(ExperimentalCamera2Interop::class)
fun CaptureRequestOptions.Builder.setShadingMode(
    shadingMode: ShadingMode?
) = shadingMode?.let {
    setCaptureRequestOption(
        CaptureRequest.SHADING_MODE,
        when (it) {
            ShadingMode.OFF -> CameraMetadata.SHADING_MODE_OFF
            ShadingMode.FAST -> CameraMetadata.SHADING_MODE_FAST
            ShadingMode.HIGH_QUALITY -> CameraMetadata.SHADING_MODE_HIGH_QUALITY
        }
    )
} ?: clearCaptureRequestOption(CaptureRequest.SHADING_MODE)

@OptIn(ExperimentalCamera2Interop::class)
fun CaptureRequestOptions.Builder.setColorCorrectionAberrationMode(
    colorCorrectionAberrationMode: ColorCorrectionAberrationMode?
) = colorCorrectionAberrationMode?.let {
    setCaptureRequestOption(
        CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
        when (it) {
            ColorCorrectionAberrationMode.OFF ->
                CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF

            ColorCorrectionAberrationMode.FAST ->
                CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_FAST

            ColorCorrectionAberrationMode.HIGH_QUALITY ->
                CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
        }
    )
} ?: clearCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE)

@OptIn(ExperimentalCamera2Interop::class)
fun CaptureRequestOptions.Builder.setDistortionCorrectionMode(
    distortionCorrectionMode: DistortionCorrectionMode?
) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    distortionCorrectionMode?.let {
        setCaptureRequestOption(
            CaptureRequest.DISTORTION_CORRECTION_MODE,
            when (it) {
                DistortionCorrectionMode.OFF -> CameraMetadata.DISTORTION_CORRECTION_MODE_OFF
                DistortionCorrectionMode.FAST -> CameraMetadata.DISTORTION_CORRECTION_MODE_FAST
                DistortionCorrectionMode.HIGH_QUALITY ->
                    CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY
            }
        )
    } ?: clearCaptureRequestOption(CaptureRequest.DISTORTION_CORRECTION_MODE)
} else {
    this
}

@OptIn(ExperimentalCamera2Interop::class)
fun CaptureRequestOptions.Builder.setHotPixelMode(
    hotPixelMode: HotPixelMode?
) = hotPixelMode?.let {
    setCaptureRequestOption(
        CaptureRequest.HOT_PIXEL_MODE,
        when (it) {
            HotPixelMode.OFF -> CameraMetadata.HOT_PIXEL_MODE_OFF
            HotPixelMode.FAST -> CameraMetadata.HOT_PIXEL_MODE_FAST
            HotPixelMode.HIGH_QUALITY -> CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY
        }
    )
} ?: clearCaptureRequestOption(CaptureRequest.HOT_PIXEL_MODE)
