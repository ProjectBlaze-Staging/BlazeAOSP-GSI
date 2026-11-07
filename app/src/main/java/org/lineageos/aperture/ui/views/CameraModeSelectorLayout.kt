/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import org.lineageos.aperture.R
import org.lineageos.aperture.models.CameraMode
import org.lineageos.aperture.models.CameraState
import org.lineageos.aperture.utils.TimeUtils
import kotlin.reflect.cast

class CameraModeSelectorLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    // Views
    private val cameraModeButtonsScrollView by lazy {
        findViewById<HorizontalScrollView>(R.id.cameraModeButtonsScrollView)
    }
    private val cameraModeButtonsLinearLayout by lazy {
        findViewById<LinearLayout>(R.id.cameraModeButtonsLinearLayout)
    }
    private val cameraModeHighlightButton by lazy {
        findViewById<MaterialButton>(R.id.cameraModeHighlightButton)
    }
    private val videoDurationButton by lazy {
        findViewById<MaterialButton>(R.id.videoDurationButton)
    }

    // System services
    private val layoutInflater by lazy { context.getSystemService(LayoutInflater::class.java) }

    private val cameraToButton = mutableMapOf<CameraMode, MaterialButton>()

    private var inSingleCaptureMode = false
    private var cameraState = CameraState.IDLE
    private var activeCameraMode = CameraMode.PHOTO

    private var positionAnimator: ValueAnimator? = null
    private var widthAnimator: ValueAnimator? = null

    var onModeSelectedCallback: (cameraMode: CameraMode) -> Unit = {}

    init {
        inflate(context, R.layout.camera_mode_selector_layout, this)

        // Add a 1px visible leading guard View so framework bottom-bar heuristics never
        // mistake the camera mode strip for an application BottomNavigationView.
        cameraModeButtonsLinearLayout.addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(1, 1)
            }
        )

        for (cameraMode in CameraMode.entries) {
            cameraToButton[cameraMode] = MaterialButton::class.cast(
                layoutInflater.inflate(
                    R.layout.camera_mode_button, this, false
                )
            ).apply {
                setText(
                    when (cameraMode) {
                        CameraMode.NIGHT -> R.string.camera_mode_night
                        CameraMode.PHOTO -> R.string.camera_mode_photo
                        CameraMode.VIDEO -> R.string.camera_mode_video
                        CameraMode.PRO_PHOTO -> R.string.camera_mode_pro_photo
                        CameraMode.PRO_VIDEO -> R.string.camera_mode_pro_video
                        CameraMode.QR -> R.string.camera_mode_qr
                    }
                )
                setOnClickListener {
                    if (cameraState == CameraState.IDLE) {
                        onModeSelectedCallback(cameraMode)
                    }
                }
            }.also {
                cameraModeButtonsLinearLayout.addView(it)
            }
        }
    }

    fun setCurrentCameraMode(cameraMode: CameraMode) {
        activeCameraMode = cameraMode
        val currentCameraModeButton =
            cameraToButton[cameraMode] ?: throw Exception("No button for $cameraMode")

        cameraToButton.forEach {
            it.value.isEnabled = cameraMode != it.key
        }

        // Animate camera mode change
        doOnLayout {
            positionAnimator?.cancel()
            positionAnimator = ValueAnimator.ofFloat(
                cameraModeHighlightButton.x,
                cameraModeButtonsLinearLayout.x + currentCameraModeButton.x
            ).apply {
                addUpdateListener { valueAnimator ->
                    cameraModeHighlightButton.x = valueAnimator.animatedValue as Float
                }
            }.also { it.start() }

            widthAnimator?.cancel()
            widthAnimator = ValueAnimator.ofInt(
                cameraModeHighlightButton.width, currentCameraModeButton.width
            ).apply {
                addUpdateListener { valueAnimator ->
                    cameraModeHighlightButton.updateLayoutParams {
                        width = valueAnimator.animatedValue as Int
                    }
                }
            }.also { it.start() }

            val targetScrollX = (
                cameraModeButtonsLinearLayout.left +
                    currentCameraModeButton.left +
                    (currentCameraModeButton.width / 2) -
                    (cameraModeButtonsScrollView.width / 2)
                ).coerceAtLeast(0)
            cameraModeButtonsScrollView.smoothScrollTo(targetScrollX, 0)
        }
    }

    fun setInSingleCaptureMode(inSingleCaptureMode: Boolean) {
        this.inSingleCaptureMode = inSingleCaptureMode

        updateButtons()
    }

    fun setCameraState(cameraState: CameraState) {
        this.cameraState = cameraState

        // Update video duration button
        videoDurationButton.isVisible = cameraState.isRecordingVideo

        updateButtons()
    }

    fun setVideoRecordingDuration(duration: Long) {
        videoDurationButton.text = TimeUtils.convertNanosToString(duration)
    }

    private fun updateButtons() {
        val hideSelector = cameraState.isRecordingVideo || inSingleCaptureMode
        cameraModeButtonsScrollView.isInvisible = hideSelector
        cameraModeHighlightButton.isInvisible = hideSelector
        cameraToButton.forEach {
            it.value.isInvisible = hideSelector
            it.value.isEnabled = activeCameraMode != it.key
        }
    }
}
