/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.viewmodels

import android.animation.ValueAnimator
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.OrientationEventListener
import androidx.camera.core.AspectRatio
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.extensions.ExtensionMode
import androidx.camera.video.Quality
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.location.LocationRequestCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.lineageos.aperture.ext.applicationContext
import org.lineageos.aperture.ext.broadcastReceiverFlow
import org.lineageos.aperture.ext.flashMode
import org.lineageos.aperture.ext.locationFlow
import org.lineageos.aperture.ext.mapToRange
import org.lineageos.aperture.ext.next
import org.lineageos.aperture.ext.nextPowerOfTwo
import org.lineageos.aperture.ext.previous
import org.lineageos.aperture.ext.previousPowerOfTwo
import org.lineageos.aperture.ext.thermalStatusFlow
import org.lineageos.aperture.models.Camera
import org.lineageos.aperture.models.CameraConfiguration
import org.lineageos.aperture.models.CameraFacing
import org.lineageos.aperture.models.CameraMode
import org.lineageos.aperture.models.CameraState
import org.lineageos.aperture.models.ColorCorrectionAberrationMode
import org.lineageos.aperture.models.DistortionCorrectionMode
import org.lineageos.aperture.models.EdgeMode
import org.lineageos.aperture.models.Event
import org.lineageos.aperture.models.FlashMode
import org.lineageos.aperture.models.GridMode
import org.lineageos.aperture.models.HardwareKey
import org.lineageos.aperture.models.HotPixelMode
import org.lineageos.aperture.models.IslandItem
import org.lineageos.aperture.models.NoiseReductionMode
import org.lineageos.aperture.models.PhotoOutputFormat
import org.lineageos.aperture.models.PhotoResolutionPreset
import org.lineageos.aperture.models.ProWhiteBalanceMode
import org.lineageos.aperture.models.Rotation
import org.lineageos.aperture.models.ShadingMode
import org.lineageos.aperture.models.ThermalStatus
import org.lineageos.aperture.models.TimerMode
import org.lineageos.aperture.models.VideoResolutionPreset
import org.lineageos.aperture.models.VideoStabilizationMode
import org.lineageos.aperture.qr.QrImageAnalyzer
import org.lineageos.aperture.repositories.CameraRepository
import org.lineageos.aperture.utils.CameraSoundsUtils
import org.lineageos.aperture.utils.StorageUtils
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.safeCast

/**
 * [ViewModel] representing a camera session. This data is used to receive
 * live data regarding the setting currently enabled.
 */
class CameraViewModel(application: Application) : ApertureViewModel(application) {
    // System services
    private val locationManager = applicationContext.getSystemService(LocationManager::class.java)
    private val powerManager = applicationContext.getSystemService(PowerManager::class.java)

    // Camera sounds
    val cameraSoundsUtils = CameraSoundsUtils(preferencesRepository)

    // Orientation
    private val orientation = callbackFlow {
        val orientationEventListener = object : OrientationEventListener(applicationContext) {
            override fun onOrientationChanged(orientation: Int) {
                trySend(orientation)
            }
        }

        orientationEventListener.enable()

        awaitClose {
            orientationEventListener.disable()
        }
    }
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    /**
     * [ExecutorService] for camera related operations.
     */
    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * CameraX's [LifecycleCameraController].
     */
    val cameraController = LifecycleCameraController(applicationContext)

    /**
     * Mutex used to rebind the camera.
     */
    val rebindMutex = Mutex()

    /**
     * Mutex used to zoom the camera.
     */
    private var zoomGestureMutex = Mutex()

    /**
     * QR image analyzer.
     */
    val qrImageAnalyzer = QrImageAnalyzer(applicationContext, viewModelScope)

    /**
     * Do not emit here directly, use [emitEvent].
     */
    private val _event = MutableSharedFlow<Event>()

    /**
     * Events.
     */
    val event = _event.asSharedFlow()

    /**
     * The initial camera mode.
     */
    var initialCameraMode = preferencesRepository.lastCameraMode.value

    /**
     * The initial camera facing.
     */
    var initialCameraFacing = preferencesRepository.lastCameraFacing.value

    /**
     * Whether the current session is in single capture mode.
     */
    val inSingleCaptureMode = MutableStateFlow(false)

    private val _cameraConfiguration = MutableStateFlow<CameraConfiguration?>(null)
    val cameraConfiguration = _cameraConfiguration
        .filterNotNull()
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    /**
     * The current camera.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val camera = cameraConfiguration
        .mapLatest { cameraConfiguration -> cameraConfiguration.camera }
        .flowOn(Dispatchers.IO)
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val cameraFacing = camera
        .mapLatest { camera -> camera.cameraFacing }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesRepository.lastCameraFacing.value,
        )

    /**
     * The current camera mode.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cameraMode = cameraConfiguration
        .mapLatest { cameraConfiguration -> cameraConfiguration.cameraMode }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.lastCameraMode.value,
        )

    /**
     * The list of [Camera]s to use for cycling.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val camerasForCycling = combine(
        cameraRepository.cameras,
        cameraMode,
    ) { cameras, cameraMode ->
        cameras.filter { it.supportsCameraMode(cameraMode) }.groupBy(Camera::cameraFacing)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = mapOf(),
        )

    /**
     * Flow used for camera mode animations.
     */
    val cameraModeTransition = cameraMode
        .runningFold(null as CameraMode? to cameraMode.value) { acc, value ->
            acc.second to value
        }
        .flowOn(Dispatchers.IO)
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
        )

    /**
     * Pair of active camera to the available cameras.
     */
    val lenses = combine(
        cameraRepository.cameras,
        camera,
        cameraMode,
    ) { cameras, camera, cameraMode ->
        camera to cameras.filter {
            it.cameraFacing == camera.cameraFacing && it.supportsCameraMode(cameraMode)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null to listOf(),
        )

    /**
     * CameraX's state of the current camera.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cameraXCameraState = camera
        .flatMapLatest { camera ->
            camera.cameraXCameraState
        }
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    /**
     * Current camera state.
     */
    val cameraState = MutableStateFlow(CameraState.IDLE)

    /**
     * Current screen rotation.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val screenRotation = orientation
        .filter { it != OrientationEventListener.ORIENTATION_UNKNOWN }
        .mapLatest { orientation ->
            Rotation.fromDegreesInAperture(orientation)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = Rotation.ROTATION_0
        )

    /**
     * Captured media [Uri]s
     */
    val capturedMedia = mediaRepository.capturedMedia()
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = listOf(),
        )

    /**
     * The current list of supported [FlashMode]s.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val supportedFlashModes = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            cameraConfiguration.cameraMode.supportedFlashModes.intersect(
                cameraConfiguration.camera.supportedFlashModes
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = setOf()
        )

    /**
     * Whether force torch mode should be enabled.
     */
    private val forceTorch = MutableStateFlow(false)

    /**
     * Whether QR torch mode should be enabled.
     */
    private val qrFlashMode = MutableStateFlow(FlashMode.OFF)

    /**
     * The user selected flash mode.
     */
    private val wantedFlashMode = combine(
        cameraConfiguration,
        preferencesRepository.photoFlashMode,
        preferencesRepository.videoFlashMode,
        qrFlashMode,
    ) { cameraConfiguration, photoFlashMode, videoFlashMode, qrFlashMode ->
        when {
            cameraConfiguration.cameraMode.isPhotoMode -> photoFlashMode
            cameraConfiguration.cameraMode.isVideoMode -> videoFlashMode
            cameraConfiguration.cameraMode == CameraMode.QR -> qrFlashMode
            else -> FlashMode.OFF
        }
    }
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    /**
     * The flash mode that will actually be used.
     */
    val flashMode = combine(
        supportedFlashModes,
        wantedFlashMode,
        forceTorch,
        cameraConfiguration,
    ) { supportedFlashModes, wantedFlashMode, forceTorch, cameraConfiguration ->
        val flashMode = wantedFlashMode.takeIf { it in supportedFlashModes } ?: FlashMode.OFF

        val shouldForceTorch = forceTorch
                && cameraConfiguration.cameraMode.isPhotoMode
                && FlashMode.TORCH in cameraConfiguration.camera.supportedFlashModes

        when (shouldForceTorch) {
            true -> FlashMode.TORCH
            false -> flashMode
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = FlashMode.OFF
        )

    /**
     * Whether the user should be able to cycle between different flash modes.
     */
    val isFlashButtonEnabled = combine(
        cameraState,
        cameraMode,
    ) { cameraState, cameraMode ->
        cameraState == CameraState.IDLE || cameraMode.isVideoMode
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    /**
     * Grid mode.
     */
    val gridMode = combine(
        preferencesRepository.lastGridMode,
        cameraConfiguration,
    ) { gridMode, cameraConfiguration ->
        gridMode.takeIf { cameraConfiguration.cameraMode != CameraMode.QR } ?: GridMode.OFF
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = GridMode.OFF
        )

    /**
     * Timer mode.
     */
    val timerMode = preferencesRepository.timerMode
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = TimerMode.OFF
        )

    /**
     * Whether the leveler is enabled.
     */
    val levelerEnabled = preferencesRepository.leveler
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    /**
     * Whether screen brightness should be forced to full.
     */
    val fullScreenBrightness = preferencesRepository.brightScreen
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        powerManager.thermalStatusFlow().mapLatest { thermalStatus ->
            when (thermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
                else -> error("Unknown thermal status: $thermalStatus")
            }
        }
    } else {
        flowOf(ThermalStatus.NONE)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = ThermalStatus.NONE,
        )

    /**
     * The current zoom state.
     */
    val zoomState = cameraController.zoomState.asFlow()
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val tapToFocusInfoState = cameraController.tapToFocusInfoState.asFlow()
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    @Suppress("MissingPermission")
    val location = locationManager.locationFlow(
        LocationRequestCompat.Builder(1000)
            .setMinUpdateDistanceMeters(1f)
            .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            .build()
    )
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val saveLocation = combine(
        preferencesRepository.saveLocation,
        inSingleCaptureMode,
    ) { saveLocation, inSingleCaptureMode ->
        when (inSingleCaptureMode) {
            true -> false
            false -> saveLocation
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesRepository.saveLocation,
        )

    private val batteryIntent = applicationContext.broadcastReceiverFlow(
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )
        .onStart { emit(null) }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    /**
     * The current, exposure compensation level, from 0 to 1
     */
    private val exposureCompensationLevel = MutableStateFlow(0.5f)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exposureCompensationRange = cameraConfiguration
        .mapLatest { it.camera.exposureCompensationRange }
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    val exposureCompensationRangeToLevel = combine(
        exposureCompensationLevel,
        exposureCompensationRange,
    ) { exposureCompensationLevel, exposureCompensationRange ->
        exposureCompensationRange to exposureCompensationLevel
    }
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exposureCompensationIndex = exposureCompensationRangeToLevel
        .mapLatest { (exposureCompensationLevel, exposureCompensationRange) ->
            Int.mapToRange(exposureCompensationLevel, exposureCompensationRange)
        }
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    val isShutterButtonEnabled = combine(
        cameraMode,
        cameraState,
    ) { cameraMode, cameraState ->
        when (cameraState) {
            CameraState.IDLE -> true
            CameraState.COUNTDOWN -> cameraMode.isVideoMode
            CameraState.TAKING_PHOTO -> false
            CameraState.PRE_RECORDING_VIDEO -> false
            CameraState.RECORDING_VIDEO -> true
            CameraState.RECORDING_VIDEO_PAUSED -> true
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    // Photo

    /**
     * Photo resolution preset (FULL, 2.5K, 2K, FHD).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photoResolutionPreset = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> cameraConfiguration.photoResolutionPreset
                else -> preferencesRepository.photoResolutionPreset.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.photoResolutionPreset.value,
        )

    /**
     * Photo capture mode.
     * @see ImageCapture.CaptureMode
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photoCaptureMode = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> cameraConfiguration.photoCaptureMode
                else -> preferencesRepository.photoCaptureMode.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.photoCaptureMode.value,
        )

    /**
     * Photo aspect ratio.
     * @see AspectRatio.Ratio
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photoAspectRatio = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> cameraConfiguration.photoAspectRatio
                else -> preferencesRepository.photoAspectRatio.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.photoAspectRatio.value,
        )

    /**
     * Photo effect.
     * @see ExtensionMode.Mode
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photoEffect = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            cameraConfiguration.extensionMode
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.photoEffect.value,
        )

    /**
     * Whether the user should be able to select a photo effect.
     */
    @androidx.annotation.OptIn(ExperimentalZeroShutterLag::class)
    val isPhotoEffectButtonVisible = combine(
        cameraConfiguration,
        cameraMode,
        photoCaptureMode,
    ) { cameraConfiguration, cameraMode, photoCaptureMode ->
        cameraMode == CameraMode.PHOTO &&
                photoCaptureMode != ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG &&
                cameraConfiguration.camera.supportedExtensionModes.size > 1
    }

    // Video

    /**
     * Video resolution preset (including 2K and 2.5K).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val videoResolutionPreset = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Video -> cameraConfiguration.videoResolutionPreset
                else -> preferencesRepository.videoResolutionPreset.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.videoResolutionPreset.value,
        )

    /**
     * Video stabilization mode (OFF, EIS, OIS, EIS_OIS).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val videoStabilizationMode = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Video -> cameraConfiguration.videoStabilizationMode
                else -> preferencesRepository.videoStabilizationMode.value
                    ?: VideoStabilizationMode.OFF
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.videoStabilizationMode.value
                ?: VideoStabilizationMode.OFF,
        )

    val isVideoStabilizationButtonVisible = combine(
        camera,
        cameraMode,
    ) { camera, cameraMode ->
        cameraMode.isVideoMode && VideoStabilizationMode.getSelectableModes(camera).size > 1
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    // Pro manual controls state
    private val manualIsoState = MutableStateFlow<Int?>(null)
    private val manualShutterSpeedNsState = MutableStateFlow<Long?>(null)
    private val manualWhiteBalanceState = MutableStateFlow(ProWhiteBalanceMode.AUTO)
    private val manualFocusDistanceState = MutableStateFlow<Float?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val proIso = cameraConfiguration
        .mapLatest { it.camera2Options.manualIso }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val proShutterSpeedNs = cameraConfiguration
        .mapLatest { it.camera2Options.manualExposureTimeNs }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val proWhiteBalance = cameraConfiguration
        .mapLatest { it.camera2Options.whiteBalanceMode }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = ProWhiteBalanceMode.AUTO,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val proFocusDistance = cameraConfiguration
        .mapLatest { it.camera2Options.manualFocusDistance }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

    val isProFocusVisible = combine(
        camera,
        cameraMode,
    ) { camera, cameraMode ->
        cameraMode.isProMode && camera.minFocusDistance > 0f
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    /**
     * Video quality.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val videoQuality = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Video -> cameraConfiguration.videoQuality
                else -> preferencesRepository.videoQuality.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.videoQuality.value,
        )

    val isVideoQualityButtonEnabled = combine(
        camera,
        cameraState,
    ) { camera, cameraState ->
        cameraState == CameraState.IDLE && camera.supportedVideoResolutionPresets.size > 1
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    /**
     * Video frame rate.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val videoFrameRate = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Video -> cameraConfiguration.videoFrameRate
                else -> preferencesRepository.videoFrameRate.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.videoFrameRate.value,
        )

    val isVideoFrameRateButtonEnabled = combine(
        cameraConfiguration,
        cameraState,
    ) { cameraConfiguration, cameraState ->
        cameraState == CameraState.IDLE && when (cameraConfiguration) {
            is CameraConfiguration.Video -> {
                val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                    cameraConfiguration.videoQuality
                ] ?: error("Video quality not supported")

                videoQualityInfo.supportedFrameRates.size > 1
            }

            else -> false
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    /**
     * Video dynamic range.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val videoDynamicRange = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Video -> cameraConfiguration.videoDynamicRange
                else -> preferencesRepository.videoDynamicRange.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.videoDynamicRange.value,
        )

    val isVideoDynamicRangeButtonEnabled = combine(
        cameraConfiguration,
        cameraState,
    ) { cameraConfiguration, cameraState ->
        cameraState == CameraState.IDLE && when (cameraConfiguration) {
            is CameraConfiguration.Video -> {
                val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                    cameraConfiguration.videoQuality
                ] ?: error("Video quality not supported")

                videoQualityInfo.supportedDynamicRanges.size > 1
            }

            else -> false
        }
    }

    /**
     * Video mic mode.
     */
    val videoMicMode = MutableStateFlow(preferencesRepository.videoMicMode.value)

    /**
     * Video [AudioConfig].
     */
    var videoAudioConfig: AudioConfig = AudioConfig.AUDIO_DISABLED

    /**
     * Video [Recording].
     */
    val videoRecording = MutableStateFlow<Recording?>(null)

    val isVideoMicButtonEnabled = combine(
        cameraState,
        videoRecording,
    ) { cameraState, videoRecording ->
        cameraState == CameraState.IDLE || videoRecording?.isAudioSourceConfigured == true
    }

    /**
     * Video recording duration.
     */
    val videoRecordingDuration = MutableStateFlow(0L)

    /**
     * Video record event.
     */
    val videoRecordEvent = MutableSharedFlow<VideoRecordEvent>()

    // QR

    /**
     * QR result.
     */
    val qrResult = qrImageAnalyzer.qrResult
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
        )

    /**
     * Whether the camera can be flipped.
     */
    val canFlipCamera = combine(
        camerasForCycling,
        cameraState,
    ) { camerasForCycling, cameraState ->
        camerasForCycling.keys.size > 1 && !cameraState.isRecordingVideo
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    /**
     * Island items.
     */
    val islandItems = combine(
        cameraConfiguration,
        videoMicMode,
        batteryIntent,
        thermalStatus,
    ) { cameraConfiguration, videoMicMode, batteryIntent, thermalStatus ->
        buildList {
            when (thermalStatus) {
                ThermalStatus.NONE,
                ThermalStatus.LIGHT -> Unit

                ThermalStatus.MODERATE,
                ThermalStatus.SEVERE -> add(
                    IslandItem.ThermalThrottling(false)
                )

                ThermalStatus.CRITICAL,
                ThermalStatus.EMERGENCY,
                ThermalStatus.SHUTDOWN -> add(
                    IslandItem.ThermalThrottling(true)
                )
            }

            batteryIntent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

                val percentage = level.times(100).div(scale)

                if (percentage <= 15) {
                    add(
                        IslandItem.LowBattery(
                            isCharging = it.getIntExtra(
                                BatteryManager.EXTRA_PLUGGED,
                                0,
                            ) != 0,
                        )
                    )
                }

                when (cameraConfiguration) {
                    is CameraConfiguration.Photo -> {
                        when (cameraConfiguration.photoOutputFormat) {
                            PhotoOutputFormat.JPEG -> {}

                            PhotoOutputFormat.JPEG_ULTRA_HDR -> add(
                                IslandItem.PhotoJpegUltraHdr
                            )

                            PhotoOutputFormat.RAW -> add(
                                IslandItem.PhotoRawEnabled(false)
                            )

                            PhotoOutputFormat.RAW_JPEG -> add(
                                IslandItem.PhotoRawEnabled(true)
                            )
                        }
                    }

                    is CameraConfiguration.Video -> {
                        if (!videoMicMode) {
                            add(IslandItem.VideoMicMuted)
                        }
                    }

                    is CameraConfiguration.Qr -> {}
                }
            }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = listOf(),
        )

    init {
        viewModelScope.launch {
            launch {
                flashMode.collectLatest { flashMode ->
                    cameraController.flashMode = flashMode
                }
            }

            launch {
                exposureCompensationIndex.collectLatest { exposureCompensationIndex ->
                    cameraController.cameraControl?.setExposureCompensationIndex(
                        exposureCompensationIndex
                    )
                }
            }

            launch {
                // Track all user preferences that requires a rebind
                combine<Any?, _>(
                    preferencesRepository.photoCaptureMode,
                    preferencesRepository.enableZsl,
                    preferencesRepository.usePhotoJpegUltraHdr,
                    preferencesRepository.enableRawImageCapture,
                    preferencesRepository.disableJpegWithRaw,
                    preferencesRepository.videoStabilization,
                    preferencesRepository.videoMirrorMode,
                    preferencesRepository.edgeMode,
                    preferencesRepository.noiseReductionMode,
                    preferencesRepository.shadingMode,
                    preferencesRepository.colorCorrectionAberrationMode,
                    preferencesRepository.distortionCorrectionMode,
                    preferencesRepository.hotPixelMode,
                ) { }.drop(1).collectLatest {
                    updateConfiguration<CameraConfiguration> { cameraConfiguration ->
                        createInitialCameraConfiguration(
                            camera = cameraConfiguration.camera,
                            cameraMode = cameraConfiguration.cameraMode,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        cameraController.unbind()

        cameraExecutor.shutdown()

        cameraSoundsUtils.release()
    }

    /**
     * Initialize [cameraConfiguration].
     * [initialCameraMode] and [initialCameraFacing] must be initialized.
     */
    suspend fun initializeCameraConfiguration(): Boolean {
        val cameraMode = initialCameraMode
        val cameraFacing = initialCameraFacing

        val camera = getSuitableCamera(
            cameraMode = cameraMode,
            cameraFacing = cameraFacing,
        ) ?: return false

        val cameraConfiguration = createInitialCameraConfiguration(
            camera = camera,
            cameraMode = cameraMode,
        )

        _cameraConfiguration.value = cameraConfiguration

        return true
    }

    /**
     * @see CameraRepository.getExtensionEnabledCameraSelector
     */
    fun getExtensionEnabledCameraSelector(
        camera: Camera,
        mode: Int,
    ) = cameraRepository.getExtensionEnabledCameraSelector(camera, mode)

    /**
     * Get whether we can restart the camera.
     */
    fun canRestartCamera() = cameraState.value == CameraState.IDLE

    /**
     * Select another camera.
     *
     * @param camera The new camera
     */
    fun selectCamera(
        camera: Camera,
    ) = updateConfiguration<CameraConfiguration> { cameraConfiguration ->
        createInitialCameraConfiguration(
            camera = camera,
            cameraMode = cameraConfiguration.cameraMode,
        )
    }

    fun takePhoto() {
        // Bail out if a photo is already being taken
        if (cameraState.value == CameraState.TAKING_PHOTO) {
            return
        }

        cameraState.value = CameraState.TAKING_PHOTO

        val photoOutputStream = if (inSingleCaptureMode.value) {
            ByteArrayOutputStream(SINGLE_CAPTURE_PHOTO_BUFFER_INITIAL_SIZE_BYTES)
        } else {
            null
        }

        val imageOutputFormat = cameraController.imageOutputFormat

        val mimeType = when (imageOutputFormat) {
            ImageCapture.OUTPUT_FORMAT_JPEG,
            ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR,
            ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> "image/jpeg"

            ImageCapture.OUTPUT_FORMAT_RAW -> "image/x-adobe-dng"

            else -> error("Unknown image output format")
        }

        val imageCount = AtomicInteger(
            when (imageOutputFormat) {
                ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> 2
                else -> 1
            }
        )

        // Create output options object which contains file + metadata
        val timestamp = System.currentTimeMillis()
        val outputOptions = StorageUtils.getPhotoMediaStoreOutputOptions(
            applicationContext.contentResolver,
            ImageCapture.Metadata().apply {
                if (!inSingleCaptureMode.value) {
                    location = this@CameraViewModel.location.value
                }
                if (cameraFacing.value == CameraFacing.FRONT) {
                    isReversedHorizontal = preferencesRepository.photoFfcMirror.value
                }
            },
            mimeType,
            timestamp,
            photoOutputStream,
        )

        // Set up image capture listener, which is triggered after photo has
        // been taken
        val onImageSavedCallback = object : ImageCapture.OnImageSavedCallback {
            override fun onCaptureStarted() {
                emitEvent(Event.PhotoCaptureStatus.CaptureStarted)

                cameraSoundsUtils.playShutterClick()
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d(
                    LOG_TAG,
                    "Photo capture succeeded: ${output.savedUri} format = ${output.imageFormat}"
                )

                val targetMaxEdge = photoResolutionPreset.value.maxLongEdge
                if (
                    targetMaxEdge > 0 &&
                    (imageOutputFormat == ImageCapture.OUTPUT_FORMAT_JPEG ||
                        imageOutputFormat == ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR ||
                        (imageOutputFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG &&
                            output.imageFormat !in RAW_FORMATS))
                ) {
                    output.savedUri?.let { savedUri ->
                        downscaleSavedJpegUriIfNeeded(savedUri, targetMaxEdge)
                    }
                }

                if (!inSingleCaptureMode.value) {
                    output.savedUri?.let {
                        mediaRepository.broadcastNewPicture(it)
                    }
                }

                if (imageCount.decrementAndGet() == 0) {
                    cameraState.value = CameraState.IDLE
                }

                // We only care about JPEG-like formats
                if (imageOutputFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG
                    && output.imageFormat in RAW_FORMATS
                ) {
                    return
                }

                emitEvent(
                    Event.PhotoCaptureStatus.ImageSaved(
                        output,
                        photoOutputStream,
                    )
                )
            }

            override fun onError(exc: ImageCaptureException) {
                emitEvent(Event.PhotoCaptureStatus.Error(exc))

                Log.e(LOG_TAG, "Photo capture failed", exc)
                cameraState.value = CameraState.IDLE
            }
        }

        when (imageOutputFormat) {
            ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> {
                // Create output options object which contains raw file + metadata
                val rawOutputOptions = StorageUtils.getPhotoMediaStoreOutputOptions(
                    applicationContext.contentResolver,
                    ImageCapture.Metadata().apply {
                        if (!inSingleCaptureMode.value) {
                            location = this@CameraViewModel.location.value
                        }
                        if (cameraFacing.value == CameraFacing.FRONT) {
                            isReversedHorizontal = preferencesRepository.photoFfcMirror.value
                        }
                    },
                    "image/x-adobe-dng",
                    timestamp,
                )

                cameraController.takePicture(
                    rawOutputOptions,
                    outputOptions,
                    ContextCompat.getMainExecutor(applicationContext),
                    onImageSavedCallback,
                )
            }

            else -> cameraController.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(applicationContext),
                onImageSavedCallback,
            )
        }
    }

    fun captureVideo() {
        if (cameraState.value != CameraState.IDLE) {
            if (cameraController.isRecording) {
                // Stop the current recording session.
                videoRecording.value?.stop()
            }
            return
        }

        // Disallow state changes while we are about to prepare for recording video
        cameraState.value = CameraState.PRE_RECORDING_VIDEO

        // Update duration text
        videoRecordingDuration.value = 0L

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getVideoMediaStoreOutputOptions(
            applicationContext.contentResolver,
            System.currentTimeMillis(),
            location.value.takeUnless { inSingleCaptureMode.value }
        )

        // Play shutter sound
        val delayTime = if (cameraSoundsUtils.playStartVideoRecording()) 500L else 0L

        viewModelScope.launch {
            delay(delayTime)

            // Start recording
            videoRecording.value = cameraController.startRecording(
                outputOptions,
                videoAudioConfig,
                cameraExecutor,
            ) {
                viewModelScope.launch {
                    videoRecordEvent.emit(it)
                }

                when (it) {
                    is VideoRecordEvent.Start -> {
                        cameraState.value = CameraState.RECORDING_VIDEO
                    }

                    is VideoRecordEvent.Pause -> {
                        cameraState.value = CameraState.RECORDING_VIDEO_PAUSED
                    }

                    is VideoRecordEvent.Resume -> {
                        cameraState.value = CameraState.RECORDING_VIDEO
                    }

                    is VideoRecordEvent.Status -> {
                        videoRecordingDuration.value = it.recordingStats.recordedDurationNanos
                    }

                    is VideoRecordEvent.Finalize -> {
                        cameraSoundsUtils.playStopVideoRecording()
                        if (it.error != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) {
                            Log.d(
                                LOG_TAG,
                                "Video capture succeeded: ${it.outputResults.outputUri}",
                            )

                            if (!inSingleCaptureMode.value) {
                                mediaRepository.broadcastNewVideo(it.outputResults.outputUri)
                            }
                        }
                        cameraState.value = CameraState.IDLE
                        videoRecording.value = null
                    }
                }
            }
        }
    }

    fun onVideoRecordingStateButtonPress() {
        when (cameraState.value) {
            CameraState.RECORDING_VIDEO -> videoRecording.value?.pause()
            CameraState.RECORDING_VIDEO_PAUSED -> videoRecording.value?.resume()
            else -> error(
                "videoRecordingStateButton clicked while in invalid state: ${cameraState.value}"
            )
        }
    }

    /**
     * Flip the camera.
     */
    fun flipCamera() = updateConfiguration<CameraConfiguration> { cameraConfiguration ->
        val cameraForCycling = camerasForCycling.value

        val cameraFacing = cameraForCycling.keys.sortedWith(
            cameraFacingComparator
        ).next(cameraConfiguration.camera.cameraFacing) ?: error("No camera available")

        val camera = when (cameraFacing) {
            CameraFacing.UNKNOWN -> null
            CameraFacing.FRONT -> cameraRepository.mainFrontCamera
            CameraFacing.BACK -> cameraRepository.mainBackCamera
            CameraFacing.EXTERNAL -> null
        } ?: cameraForCycling[cameraFacing]?.firstOrNull() ?: error(
            "Camera with facing $cameraFacing not available"
        )

        preferencesRepository.lastCameraFacing.value = camera.cameraFacing

        emitEvent(Event.FlipCameraAnimation)

        createInitialCameraConfiguration(
            camera = camera,
            cameraMode = cameraConfiguration.cameraMode,
        )
    }

    fun setCameraMode(
        cameraMode: CameraMode,
    ) = viewModelScope.launch {
        updateConfiguration<CameraConfiguration> { cameraConfiguration ->
            if (cameraConfiguration.cameraMode == cameraMode) {
                return@updateConfiguration cameraConfiguration
            }

            val camera = cameraConfiguration.camera.takeIf {
                it.supportsCameraMode(cameraMode)
            } ?: getSuitableCamera(
                cameraMode,
                cameraConfiguration.camera.cameraFacing,
            ) ?: run {
                Log.e(LOG_TAG, "No camera supports the requested camera mode $cameraMode")
                return@updateConfiguration cameraConfiguration
            }

            preferencesRepository.lastCameraMode.value = cameraMode

            createInitialCameraConfiguration(
                camera = camera,
                cameraMode = cameraMode,
            )
        }
    }

    /**
     * Switch to the previous camera mode.
     */
    fun previousCameraMode() = cameraMode.value.previous()?.let { setCameraMode(it) }

    /**
     * Switch to the next camera mode.
     */
    fun nextCameraMode() = cameraMode.value.next()?.let { setCameraMode(it) }

    /**
     * Cycle flash mode
     *
     * @param forceTorch Whether force torch mode should be toggled
     * @return true if the flash mode was changed, false otherwise
     */
    fun cycleFlashMode(forceTorch: Boolean): Boolean {
        val cameraConfiguration = _cameraConfiguration.value ?: return false

        // Long-press is supported only on photo mode and if torch mode is available
        val forceTorchAvailable = cameraConfiguration.cameraMode.isPhotoMode
                && cameraConfiguration.camera.supportedFlashModes.contains(FlashMode.TORCH)
        if (forceTorch && !forceTorchAvailable) {
            this.forceTorch.value = false

            return false
        }

        when (forceTorch) {
            true -> {
                this.forceTorch.value = this.forceTorch.value.not()
            }

            else -> when (this.forceTorch.value) {
                true -> {
                    // Just disable torch mode
                    this.forceTorch.value = false
                }

                false -> supportedFlashModes.value.toList().next(flashMode.value)?.let {
                    when {
                        cameraConfiguration.cameraMode.isPhotoMode ->
                            preferencesRepository.photoFlashMode.value = it
                        cameraConfiguration.cameraMode.isVideoMode ->
                            preferencesRepository.videoFlashMode.value = it
                        cameraConfiguration.cameraMode == CameraMode.QR ->
                            qrFlashMode.value = it
                    }
                }
            }
        }

        // Check if we should show the force torch suggestion
        if (!preferencesRepository.forceTorchHelpShown.value) {
            if (forceTorch) {
                // The user figured it out by themself
                preferencesRepository.forceTorchHelpShown.value = true
            } else {
                emitEvent(Event.ShowForceTorchHelp)
            }
        }

        return true
    }

    /**
     * Set whether the force torch help has been shown.
     */
    fun setForceModeHelpShown(forceTorchHelpShown: Boolean) {
        preferencesRepository.forceTorchHelpShown.value = forceTorchHelpShown
    }

    /**
     * Cycle to the next grid mode.
     */
    fun cycleGridMode() {
        gridMode.value.next()?.let {
            preferencesRepository.lastGridMode.value = it
        }
    }

    /**
     * Toggle the timer mode.
     */
    fun toggleTimerMode() {
        timerMode.value.next()?.let {
            preferencesRepository.timerMode.value = it
        }
    }

    /**
     * Cycle the extension mode.
     */
    fun cycleExtensionMode() =
        updateConfiguration<CameraConfiguration.Photo> { cameraConfiguration ->
            val supportedExtensionModes = cameraConfiguration.camera.supportedExtensionModes
            val extensionMode = supportedExtensionModes.toList().sorted().next(
                cameraConfiguration.extensionMode
            ) ?: error("No extension mode supported by the camera")

            preferencesRepository.photoEffect.value = extensionMode

            cameraConfiguration.copy(
                extensionMode = extensionMode,
            )
        }

    /**
     * Cycle the photo aspect ratio.
     */
    fun cyclePhotoAspectRatio() =
        updateConfiguration<CameraConfiguration.Photo> { cameraConfiguration ->
            val newAspectRatio = when (cameraConfiguration.photoAspectRatio) {
                AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
                AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
                else -> AspectRatio.RATIO_4_3
            }

            preferencesRepository.photoAspectRatio.value = newAspectRatio

            cameraConfiguration.copy(
                photoAspectRatio = newAspectRatio,
            )
        }

    /**
     * Cycle the intermediate photo resolution preset (FULL -> 2.5K -> 2K -> FHD).
     */
    fun cyclePhotoResolutionPreset() =
        updateConfiguration<CameraConfiguration.Photo> { cameraConfiguration ->
            val newPreset = cameraConfiguration.photoResolutionPreset.next()
                ?: PhotoResolutionPreset.FULL
            preferencesRepository.photoResolutionPreset.value = newPreset
            cameraConfiguration.copy(
                photoResolutionPreset = newPreset,
            )
        }

    /**
     * Cycle the video quality (including intermediate 2K and 2.5K presets).
     */
    fun cycleVideoQuality() =
        updateConfiguration<CameraConfiguration.Video> { cameraConfiguration ->
            val presets = cameraConfiguration.camera.supportedVideoResolutionPresets
            val newPreset = presets.next(cameraConfiguration.videoResolutionPreset)
                ?: presets.firstOrNull()
                ?: error("No video quality supported by the camera")

            val videoQuality = newPreset.underlyingQuality

            val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                videoQuality
            ] ?: error("Video quality not supported")

            val videoFrameRate = cameraConfiguration.videoFrameRate.takeIf {
                videoQualityInfo.supportedFrameRates.contains(it)
            } ?: videoQualityInfo.supportedFrameRates.firstOrNull()

            val videoDynamicRange = cameraConfiguration.videoDynamicRange.takeIf {
                videoQualityInfo.supportedDynamicRanges.contains(it)
            } ?: videoQualityInfo.supportedDynamicRanges.firstOrNull() ?: error(
                "No video dynamic range supported by the camera"
            )

            preferencesRepository.videoQuality.value = videoQuality
            preferencesRepository.videoResolutionPreset.value = newPreset

            cameraConfiguration.copy(
                videoQuality = videoQuality,
                videoResolutionPreset = newPreset,
                videoFrameRate = videoFrameRate,
                videoDynamicRange = videoDynamicRange,
            )
        }

    /**
     * Cycle the video stabilization mode (OFF -> EIS -> OIS -> EIS+OIS).
     */
    fun cycleVideoStabilizationMode() =
        updateConfiguration<CameraConfiguration.Video> { cameraConfiguration ->
            val modes = VideoStabilizationMode.getSelectableModes(cameraConfiguration.camera)
            val newMode = modes.next(cameraConfiguration.videoStabilizationMode)
                ?: VideoStabilizationMode.OFF

            preferencesRepository.videoStabilizationMode.value = newMode
            preferencesRepository.videoStabilization.value = newMode != VideoStabilizationMode.OFF

            cameraConfiguration.copy(
                enableVideoStabilization = newMode != VideoStabilizationMode.OFF,
                videoStabilizationMode = newMode,
            )
        }

    /**
     * Cycle Pro manual ISO value.
     */
    fun cycleProIso() =
        updateConfiguration<CameraConfiguration> { cameraConfiguration ->
            val range = cameraConfiguration.camera.isoRange
            val candidates = buildList<Int?> {
                add(null)
                listOf(50, 100, 200, 400, 800, 1600, 3200).forEach { iso ->
                    if (range == null || iso in range) add(iso)
                }
            }
            val nextIso = candidates.next(manualIsoState.value)
            manualIsoState.value = nextIso
            val updatedOptions = cameraConfiguration.camera2Options.copy(manualIso = nextIso)
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> cameraConfiguration.copy(camera2Options = updatedOptions)
                is CameraConfiguration.Video -> cameraConfiguration.copy(camera2Options = updatedOptions)
                else -> cameraConfiguration
            }
        }

    /**
     * Cycle Pro manual shutter speed (exposure time in nanoseconds).
     */
    fun cycleProShutterSpeed() =
        updateConfiguration<CameraConfiguration> { cameraConfiguration ->
            val range = cameraConfiguration.camera.exposureTimeRange
            val maxVideoNs = if (cameraConfiguration is CameraConfiguration.Video) {
                val fps = cameraConfiguration.videoFrameRate?.value ?: 30
                1_000_000_000L / fps
            } else {
                Long.MAX_VALUE
            }
            val candidates = buildList<Long?> {
                add(null)
                listOf(
                    250_000L,       // 1/4000s
                    1_000_000L,     // 1/1000s
                    2_000_000L,     // 1/500s
                    4_000_000L,     // 1/250s
                    8_000_000L,     // 1/125s
                    16_666_666L,    // 1/60s
                    33_333_333L,    // 1/30s
                    66_666_666L,    // 1/15s
                    250_000_000L,   // 1/4s
                    500_000_000L,   // 1/2s
                    1_000_000_000L, // 1s
                ).forEach { ns ->
                    if (ns <= maxVideoNs && (range == null || ns in range)) {
                        add(ns)
                    }
                }
            }
            val nextShutter = candidates.next(manualShutterSpeedNsState.value)
            manualShutterSpeedNsState.value = nextShutter
            val updatedOptions = cameraConfiguration.camera2Options.copy(
                manualExposureTimeNs = nextShutter
            )
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> cameraConfiguration.copy(camera2Options = updatedOptions)
                is CameraConfiguration.Video -> cameraConfiguration.copy(camera2Options = updatedOptions)
                else -> cameraConfiguration
            }
        }

    /**
     * Cycle Pro manual White Balance preset.
     */
    fun cycleProWhiteBalance() =
        updateConfiguration<CameraConfiguration> { cameraConfiguration ->
            val modes = cameraConfiguration.camera.supportedAwbModes
            val nextWb = modes.next(manualWhiteBalanceState.value) ?: ProWhiteBalanceMode.AUTO
            manualWhiteBalanceState.value = nextWb
            val updatedOptions = cameraConfiguration.camera2Options.copy(
                whiteBalanceMode = nextWb
            )
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> cameraConfiguration.copy(camera2Options = updatedOptions)
                is CameraConfiguration.Video -> cameraConfiguration.copy(camera2Options = updatedOptions)
                else -> cameraConfiguration
            }
        }

    /**
     * Cycle Pro manual Focus distance (in diopters, 0.0f = infinity .. minFocusDistance = macro).
     */
    fun cycleProFocusDistance() =
        updateConfiguration<CameraConfiguration> { cameraConfiguration ->
            val maxDiopter = cameraConfiguration.camera.minFocusDistance
            val candidates = buildList<Float?> {
                add(null)
                if (maxDiopter > 0f) {
                    add(0f)
                    listOf(0.5f, 2.0f, 4.0f).forEach { d ->
                        if (d < maxDiopter) add(d)
                    }
                    add(maxDiopter)
                }
            }
            val nextFocus = candidates.next(manualFocusDistanceState.value)
            manualFocusDistanceState.value = nextFocus
            val updatedOptions = cameraConfiguration.camera2Options.copy(
                manualFocusDistance = nextFocus
            )
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> cameraConfiguration.copy(camera2Options = updatedOptions)
                is CameraConfiguration.Video -> cameraConfiguration.copy(camera2Options = updatedOptions)
                else -> cameraConfiguration
            }
        }

    /**
     * Cycle the video frame rate.
     */
    fun cycleVideoFrameRate() =
        updateConfiguration<CameraConfiguration.Video> { cameraConfiguration ->
            val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                cameraConfiguration.videoQuality
            ] ?: error(
                "Camera ${cameraConfiguration.camera.cameraId} does not support video quality ${cameraConfiguration.videoQuality}"
            )

            val videoFrameRate = videoQualityInfo.supportedFrameRates.toList().sorted().next(
                cameraConfiguration.videoFrameRate
            )

            preferencesRepository.videoFrameRate.value = videoFrameRate

            cameraConfiguration.copy(
                videoFrameRate = videoFrameRate,
            )
        }

    /**
     * Cycle the video dynamic range.
     */
    fun cycleVideoDynamicRange() =
        updateConfiguration<CameraConfiguration.Video> { cameraConfiguration ->
            val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                cameraConfiguration.videoQuality
            ] ?: error(
                "Camera ${cameraConfiguration.camera.cameraId} does not support video quality ${cameraConfiguration.videoQuality}"
            )

            val videoDynamicRange = videoQualityInfo.supportedDynamicRanges.toList().next(
                cameraConfiguration.videoDynamicRange
            ) ?: error("No video dynamic range supported by the camera")

            preferencesRepository.videoDynamicRange.value = videoDynamicRange

            cameraConfiguration.copy(
                videoDynamicRange = videoDynamicRange,
            )
        }

    /**
     * Toggle the video microphone status.
     */
    fun toggleVideoMicrophoneEnabled() = setVideoMicrophoneEnabled(!videoMicMode.value)

    /**
     * Set whether the video microphone is enabled.
     *
     * @param videoMicrophoneEnabled Whether the video microphone should be enabled
     */
    @Suppress("MissingPermission")
    fun setVideoMicrophoneEnabled(videoMicrophoneEnabled: Boolean) {
        videoAudioConfig = when (videoMicrophoneEnabled) {
            true -> AudioConfig.create(true)
            false -> AudioConfig.AUDIO_DISABLED
        }

        videoRecording.value?.mute(!videoMicrophoneEnabled)

        this.videoMicMode.value = videoMicrophoneEnabled

        preferencesRepository.videoMicMode.value = videoMicrophoneEnabled
    }

    /**
     * Set the desired exposure compensation range.
     *
     * @param exposureCompensationLevel A value between 0 and 1, with 0.5 being 0 EV
     */
    fun setExposureCompensationLevel(exposureCompensationLevel: Float) {
        require(exposureCompensationLevel in 0f..1f) {
            "Exposure compensation level must be between 0 and 1, got $exposureCompensationLevel"
        }

        this.exposureCompensationLevel.value = exposureCompensationLevel
    }

    /**
     * Apply the specified zoom smoothly. The value will be automatically clamped
     * between min and max.
     * @param zoomRatio The zoom ratio to apply
     */
    fun smoothZoom(zoomRatio: Float) {
        val acquired = zoomGestureMutex.tryLock()
        if (!acquired) {
            return
        }

        val zoomState = zoomState.value ?: return

        ValueAnimator.ofFloat(
            zoomState.zoomRatio,
            zoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        ).apply {
            addUpdateListener {
                cameraController.setZoomRatio(it.animatedValue as Float)
            }
            addListener(
                onEnd = {
                    zoomGestureMutex.unlock()
                }
            )
        }.start()
    }

    /**
     * Reset the zoom to 1.0x (value relative to the lens, not of the FOV).
     */
    fun resetZoom() = smoothZoom(1f)

    /**
     * Zoom in by a power of 2.
     */
    fun zoomIn() = zoomState.value?.zoomRatio?.let {
        smoothZoom(it.nextPowerOfTwo())
    }

    /**
     * Zoom out by a power of 2.
     */
    fun zoomOut() = zoomState.value?.zoomRatio?.let {
        smoothZoom(it.previousPowerOfTwo())
    }

    fun setSaveLocation(saveLocation: Boolean?) {
        preferencesRepository.saveLocation.value = saveLocation
    }

    fun onQrDialogDismissed() {
        qrImageAnalyzer.dismissResult()
    }

    fun getHardwareKeyAction(
        hardwareKey: HardwareKey,
    ) = preferencesRepository.hardwareKeyActionPreferences[
        hardwareKey
    ]?.value ?: hardwareKey.defaultAction

    fun getHardwareKeyInvert(
        hardwareKey: HardwareKey,
    ) = hardwareKey.isTwoWayKey && preferencesRepository.hardwareKeyInvertPreferences[
        hardwareKey
    ]?.value ?: false

    fun fileExists(uri: Uri) = mediaRepository.fileExists(uri)

    /**
     * Get whether or not any camera currently supports video mode.
     */
    suspend fun isVideoRecordingAvailable() = cameraRepository.cameras.first().any {
        it.supportsCameraMode(CameraMode.VIDEO)
    }

    /**
     * Emit a new event to let the activity handle it.
     */
    private fun emitEvent(event: Event) = viewModelScope.launch {
        _event.emit(event)
    }

    /**
     * Get a suitable [Camera] for the provided [CameraFacing] and the current [CameraMode].
     *
     * @param cameraMode The requested [CameraMode]
     * @param cameraFacing The requested [CameraFacing]
     * @return A [Camera] that is compatible with the provided configuration or null
     */
    private suspend fun getSuitableCamera(
        cameraMode: CameraMode,
        cameraFacing: CameraFacing,
    ): Camera? {
        val compatibleCameras = cameraRepository.cameras.first()
            .filter { camera ->
                camera.supportsCameraMode(cameraMode)
            }

        return compatibleCameras.firstOrNull { camera ->
            camera.cameraFacing == cameraFacing
        } ?: compatibleCameras.firstOrNull()
    }

    @androidx.annotation.OptIn(ExperimentalZeroShutterLag::class)
    private fun createInitialCameraConfiguration(
        camera: Camera,
        cameraMode: CameraMode,
    ) = when (cameraMode) {
        CameraMode.PHOTO,
        CameraMode.NIGHT,
        CameraMode.PRO_PHOTO -> {
            // Enable ZSL when requested by the user and supported by the camera
            val photoCaptureMode = when (cameraMode) {
                CameraMode.NIGHT -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                else -> when (
                    val prefCaptureMode = preferencesRepository.photoCaptureMode.value
                ) {
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY -> when (
                        cameraMode == CameraMode.PHOTO &&
                            preferencesRepository.enableZsl.value &&
                            camera.supportsZsl
                    ) {
                        true -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
                        false -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    }

                    else -> prefCaptureMode
                }
            }

            val usePhotoJpegUltraHdr = preferencesRepository.usePhotoJpegUltraHdr.value
            val enableRawImageCapture = preferencesRepository.enableRawImageCapture.value
            val disableJpegWithRaw = preferencesRepository.disableJpegWithRaw.value
            val photoOutputFormat = when {
                inSingleCaptureMode.value -> PhotoOutputFormat.JPEG

                enableRawImageCapture
                        && disableJpegWithRaw
                        && camera.supportedPhotoOutputFormats.contains(
                    PhotoOutputFormat.RAW
                ) -> PhotoOutputFormat.RAW

                enableRawImageCapture && camera.supportedPhotoOutputFormats.contains(
                    PhotoOutputFormat.RAW_JPEG
                ) -> PhotoOutputFormat.RAW_JPEG

                usePhotoJpegUltraHdr && camera.supportedPhotoOutputFormats.contains(
                    PhotoOutputFormat.JPEG_ULTRA_HDR
                ) -> PhotoOutputFormat.JPEG_ULTRA_HDR

                else -> PhotoOutputFormat.JPEG
            }

            val extensionMode = when (cameraMode) {
                CameraMode.NIGHT -> if (camera.supportsExtensionMode(ExtensionMode.NIGHT)) {
                    ExtensionMode.NIGHT
                } else {
                    ExtensionMode.NONE
                }
                CameraMode.PRO_PHOTO -> ExtensionMode.NONE
                else -> preferencesRepository.photoEffect.value.takeIf {
                    camera.supportsExtensionMode(it)
                } ?: ExtensionMode.NONE
            }

            val baseCamera2Options = buildPhotoCamera2Options(camera, photoCaptureMode)
            val camera2Options = when (cameraMode) {
                CameraMode.NIGHT -> baseCamera2Options.copy(
                    edgeMode = EdgeMode.HIGH_QUALITY.takeIf {
                        camera.supportedEdgeModes.contains(it)
                    } ?: baseCamera2Options.edgeMode,
                    noiseReductionMode = NoiseReductionMode.HIGH_QUALITY.takeIf {
                        camera.supportedNoiseReductionModes.contains(it)
                    } ?: baseCamera2Options.noiseReductionMode,
                    shadingMode = ShadingMode.HIGH_QUALITY.takeIf {
                        camera.supportedShadingModes.contains(it)
                    } ?: baseCamera2Options.shadingMode,
                    colorCorrectionAberrationMode = ColorCorrectionAberrationMode.HIGH_QUALITY.takeIf {
                        camera.supportedColorCorrectionAberrationModes.contains(it)
                    } ?: baseCamera2Options.colorCorrectionAberrationMode,
                    hotPixelMode = HotPixelMode.HIGH_QUALITY.takeIf {
                        camera.supportedHotPixelModes.contains(it)
                    } ?: baseCamera2Options.hotPixelMode,
                )
                CameraMode.PRO_PHOTO -> baseCamera2Options.copy(
                    manualIso = manualIsoState.value,
                    manualExposureTimeNs = manualShutterSpeedNsState.value,
                    manualFocusDistance = manualFocusDistanceState.value,
                    whiteBalanceMode = manualWhiteBalanceState.value,
                )
                else -> baseCamera2Options
            }

            CameraConfiguration.Photo(
                camera = camera,
                cameraMode = cameraMode,
                extensionMode = extensionMode,
                camera2Options = camera2Options,
                photoCaptureMode = photoCaptureMode,
                photoAspectRatio = preferencesRepository.photoAspectRatio.value,
                enableHighResolution = overlaysRepository.enableHighResolution,
                photoOutputFormat = photoOutputFormat,
                photoResolutionPreset = preferencesRepository.photoResolutionPreset.value,
            )
        }

        CameraMode.VIDEO,
        CameraMode.PRO_VIDEO -> {
            val savedPreset = preferencesRepository.videoResolutionPreset.value
            val videoResolutionPreset = savedPreset.takeIf {
                camera.supportedVideoResolutionPresets.contains(it)
            } ?: camera.supportedVideoResolutionPresets.firstOrNull {
                it.underlyingQuality == preferencesRepository.videoQuality.value
            } ?: camera.supportedVideoResolutionPresets.firstOrNull()
                ?: VideoResolutionPreset.FHD_1080P

            val videoQuality = videoResolutionPreset.underlyingQuality.takeIf {
                camera.supportedVideoQualities.contains(it)
            } ?: camera.supportedVideoQualities.keys.firstOrNull() ?: error(
                "Camera ${camera.cameraId} does not support any video quality"
            )

            val videoQualityInfo = camera.supportedVideoQualities[videoQuality] ?: error(
                "Camera ${camera.cameraId} does not support video quality $videoQuality"
            )

            val videoFrameRate = preferencesRepository.videoFrameRate.value.takeIf {
                videoQualityInfo.supportedFrameRates.contains(it)
            } ?: videoQualityInfo.supportedFrameRates.firstOrNull()

            val videoDynamicRange = preferencesRepository.videoDynamicRange.value.takeIf {
                videoQualityInfo.supportedDynamicRanges.contains(it)
            } ?: videoQualityInfo.supportedDynamicRanges.firstOrNull() ?: error(
                "No video dynamic range supported by the camera"
            )

            val enableVideoStabilization = preferencesRepository.videoStabilization.value
            val selectableStabModes = VideoStabilizationMode.getSelectableModes(camera)
            val videoStabilizationMode = if (!enableVideoStabilization) {
                VideoStabilizationMode.OFF
            } else {
                preferencesRepository.videoStabilizationMode.value?.takeIf {
                    selectableStabModes.contains(it)
                } ?: VideoStabilizationMode.getMode(camera)
            }

            val baseVideoOptions = buildVideoCamera2Options(camera)
            val camera2Options = if (cameraMode == CameraMode.PRO_VIDEO) {
                baseVideoOptions.copy(
                    manualIso = manualIsoState.value,
                    manualExposureTimeNs = manualShutterSpeedNsState.value,
                    manualFocusDistance = manualFocusDistanceState.value,
                    whiteBalanceMode = manualWhiteBalanceState.value,
                )
            } else {
                baseVideoOptions
            }

            CameraConfiguration.Video(
                camera = camera,
                cameraMode = cameraMode,
                camera2Options = camera2Options,
                videoQuality = videoQuality,
                videoResolutionPreset = videoResolutionPreset,
                videoFrameRate = videoFrameRate,
                videoDynamicRange = videoDynamicRange,
                videoMirrorMode = preferencesRepository.videoMirrorMode.value,
                enableVideoStabilization = enableVideoStabilization,
                videoStabilizationMode = videoStabilizationMode,
            )
        }

        CameraMode.QR -> CameraConfiguration.Qr(
            camera = camera,
        )
    }

    private fun downscaleSavedJpegUriIfNeeded(uri: Uri, maxLongEdge: Int) {
        runCatching {
            val cr = applicationContext.contentResolver
            val exifTags = mutableMapOf<String, String>()
            val trackedTags = listOf(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_SUBSEC_TIME,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_FOCAL_LENGTH,
            )
            cr.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                for (tag in trackedTags) {
                    exif.getAttribute(tag)?.let { exifTags[tag] = it }
                }
            }
            val original = cr.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@runCatching

            val width = original.width
            val height = original.height
            val longEdge = maxOf(width, height)
            if (longEdge <= maxLongEdge) {
                original.recycle()
                return@runCatching
            }

            val scale = maxLongEdge.toFloat() / longEdge.toFloat()
            val targetWidth = (width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
            if (scaled !== original) {
                original.recycle()
            }

            cr.openOutputStream(uri, "wt")?.use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            scaled.recycle()

            cr.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exifOut = ExifInterface(pfd.fileDescriptor)
                for ((tag, value) in exifTags) {
                    exifOut.setAttribute(tag, value)
                }
                exifOut.saveAttributes()
            }
        }.onFailure {
            Log.w(LOG_TAG, "Failed to downscale saved JPEG to $maxLongEdge", it)
        }
    }

    @androidx.annotation.OptIn(ExperimentalZeroShutterLag::class)
    private fun buildPhotoCamera2Options(
        camera: Camera,
        photoCaptureMode: Int,
    ) = CameraConfiguration.Camera2Options(
        edgeMode = preferencesRepository.edgeMode.value?.takeIf {
            camera.supportedEdgeModes.contains(it) && when (
                photoCaptureMode
            ) {
                ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG ->
                    EdgeMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        noiseReductionMode = preferencesRepository.noiseReductionMode.value?.takeIf {
            camera.supportedNoiseReductionModes.contains(it) && when (
                photoCaptureMode
            ) {
                ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG ->
                    NoiseReductionMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        shadingMode = preferencesRepository.shadingMode.value?.takeIf {
            camera.supportedShadingModes.contains(it) && when (
                photoCaptureMode
            ) {
                ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG ->
                    ShadingMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        colorCorrectionAberrationMode = preferencesRepository.colorCorrectionAberrationMode.value?.takeIf {
            camera.supportedColorCorrectionAberrationModes.contains(it) && when (
                photoCaptureMode
            ) {
                ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG ->
                    ColorCorrectionAberrationMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        distortionCorrectionMode = preferencesRepository.distortionCorrectionMode.value?.takeIf {
            camera.supportedDistortionCorrectionModes.contains(it) && when (
                photoCaptureMode
            ) {
                ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG ->
                    DistortionCorrectionMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        hotPixelMode = preferencesRepository.hotPixelMode.value?.takeIf {
            camera.supportedHotPixelModes.contains(it) && when (
                photoCaptureMode
            ) {
                ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG ->
                    HotPixelMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
    )

    @androidx.annotation.OptIn(ExperimentalZeroShutterLag::class)
    private fun buildVideoCamera2Options(
        camera: Camera,
    ) = CameraConfiguration.Camera2Options(
        edgeMode = preferencesRepository.edgeMode.value?.takeIf {
            camera.supportedEdgeModes.contains(it)
                    && EdgeMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        noiseReductionMode = preferencesRepository.noiseReductionMode.value?.takeIf {
            camera.supportedNoiseReductionModes.contains(it)
                    && NoiseReductionMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        shadingMode = preferencesRepository.shadingMode.value?.takeIf {
            camera.supportedShadingModes.contains(it)
                    && ShadingMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        colorCorrectionAberrationMode = preferencesRepository.colorCorrectionAberrationMode.value?.takeIf {
            camera.supportedColorCorrectionAberrationModes.contains(it)
                    && ColorCorrectionAberrationMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        distortionCorrectionMode = preferencesRepository.distortionCorrectionMode.value?.takeIf {
            camera.supportedDistortionCorrectionModes.contains(it)
                    && DistortionCorrectionMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        hotPixelMode = preferencesRepository.hotPixelMode.value?.takeIf {
            camera.supportedHotPixelModes.contains(it)
                    && HotPixelMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
    )

    /**
     * Reconfigure the camera session. This helper method will also handle concurrency.
     *
     * @param block A block that will be executed with the current [CameraConfiguration] and should
     *   return a new one.
     */
    private inline fun <reified T : CameraConfiguration> updateConfiguration(
        block: (T) -> CameraConfiguration,
    ): Boolean {
        if (!canRestartCamera()) {
            return false
        }

        if (!rebindMutex.tryLock()) {
            return false
        }

        return try {
            val currentCameraConfiguration = _cameraConfiguration.value ?: error(
                "Camera configuration is null"
            )

            T::class.safeCast(currentCameraConfiguration)?.let {
                val newCameraConfiguration = block(it)

                _cameraConfiguration.value = newCameraConfiguration

                true
            } ?: false
        } finally {
            rebindMutex.unlock()
        }
    }

    companion object {
        private val LOG_TAG = CameraViewModel::class.simpleName!!

        private const val SINGLE_CAPTURE_PHOTO_BUFFER_INITIAL_SIZE_BYTES = 8 * 1024 * 1024 // 8 MiB

        private val cameraFacingComparator = Comparator.comparingInt<CameraFacing> {
            when (it) {
                CameraFacing.UNKNOWN -> 3
                CameraFacing.FRONT -> 1
                CameraFacing.BACK -> 0
                CameraFacing.EXTERNAL -> 2
            }
        }

        private val RAW_FORMATS = listOf(
            ImageFormat.RAW10,
            ImageFormat.RAW12,
            ImageFormat.RAW_SENSOR,
            ImageFormat.RAW_PRIVATE,
        )
    }
}
