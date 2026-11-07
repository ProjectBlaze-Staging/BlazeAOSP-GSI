/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.models

enum class VideoStabilizationMode {
    OFF,
    ON,
    ON_PREVIEW,
    OIS,
    EIS_OIS;

    val usesEis: Boolean
        get() = this == ON || this == ON_PREVIEW || this == EIS_OIS

    val usesOis: Boolean
        get() = this == OIS || this == EIS_OIS

    companion object {
        fun getMode(camera: Camera) = when {
            camera.supportedVideoStabilizationModes.contains(EIS_OIS) -> EIS_OIS
            camera.supportedVideoStabilizationModes.contains(ON_PREVIEW) -> ON_PREVIEW
            camera.supportedVideoStabilizationModes.contains(ON) -> ON
            camera.supportedVideoStabilizationModes.contains(OIS) -> OIS
            else -> OFF
        }

        fun getSelectableModes(camera: Camera): List<VideoStabilizationMode> = buildList {
            add(OFF)
            when {
                camera.supportedVideoStabilizationModes.contains(ON_PREVIEW) -> add(ON_PREVIEW)
                camera.supportedVideoStabilizationModes.contains(ON) -> add(ON)
            }
            if (camera.supportedVideoStabilizationModes.contains(OIS)) {
                add(OIS)
            }
            if (camera.supportedVideoStabilizationModes.contains(EIS_OIS)) {
                add(EIS_OIS)
            }
        }
    }
}
