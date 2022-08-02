package com.example.machinelearningkit.ui.view.camera

import android.content.Context
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.machinelearningkit.ui.view.camera.model.PreviewScaleType
import com.example.machinelearningkit.ui.view.camera.model.SourceInfo
import com.example.machinelearningkit.ui.view.camera.useCase.bindAnalysisUseCase
import com.example.machinelearningkit.ui.screens.faceDetectionScreen.view.DetectedFaces
import com.example.machinelearningkit.ui.screens.poseDetectionScreen.view.DetectedPose
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.pose.Pose

@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    cameraLens:Int,
    scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER,
    poseDetection:Boolean = false,
    faceDetection:Boolean = false
) {

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    var detectedFaces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var detectedPose by remember { mutableStateOf<Pose?>(null) }
    var sourceInfo by remember { mutableStateOf(SourceInfo(10, 10, false)) }
    
    val previewView = remember { PreviewView(context) }
    val cameraProvider = remember(sourceInfo, cameraLens) {
        ProcessCameraProvider.getInstance(context)
            .configureCamera(
                previewView = previewView,
                lifecycleOwner = lifecycleOwner,
                cameraLens = cameraLens,
                context = context,
                poseDetection = poseDetection,
                faceDetection = faceDetection,
                setSourceInfo = { sourceInfo = it },
                onFacesDetected = { detectedFaces = it },
                onPoseDetected = { detectedPose = it }
            )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ){
        with(LocalDensity.current){
            Box(
                modifier = Modifier
                    .size(
                        height = sourceInfo.height.toDp(),
                        width = sourceInfo.width.toDp()
                    )
                    .scale(
                        calculateScale(
                            constraints,
                            sourceInfo,
                            PreviewScaleType.CENTER_CROP
                        )
                    )
            ){
                CameraPreview(modifier,previewView, scaleType)
                if (faceDetection){
                    DetectedFaces(faces = detectedFaces, sourceInfo = sourceInfo)
                }
                if (poseDetection){
                    DetectedPose(pose = detectedPose, sourceInfo = sourceInfo)
                }
            }
        }

    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    previewView: PreviewView,
    scaleType: PreviewView.ScaleType
) {
    AndroidView(
        modifier = modifier,
        factory = {
            previewView.apply {
                this.scaleType = scaleType

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                implementationMode = PreviewView.ImplementationMode.COMPATIBLE


            }

            previewView
        }
    )
}

private fun ListenableFuture<ProcessCameraProvider>.configureCamera(
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    cameraLens: Int,
    context: Context,
    poseDetection:Boolean,
    faceDetection:Boolean,
    setSourceInfo: (SourceInfo) -> Unit,
    onFacesDetected: (List<Face>) -> Unit,
    onPoseDetected: (Pose) -> Unit
): ListenableFuture<ProcessCameraProvider> {
    addListener({
        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraLens).build()

        val preview = androidx.camera.core.Preview.Builder()
            .build()
            .apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

        val analysis = bindAnalysisUseCase(
            lens = cameraLens,
            poseDetection = poseDetection,
            faceDetection = faceDetection,
            setSourceInfo = setSourceInfo,
            onFacesDetected = onFacesDetected,
            onPoseDetected = onPoseDetected
        )

        val imageCapture = ImageCapture.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()

        try {
            get().apply {
                unbindAll()
                bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                bindToLifecycle(lifecycleOwner, cameraSelector, analysis, imageCapture)
            }
        } catch (exc: Exception) {
            TODO("process errors")
        }
    }, ContextCompat.getMainExecutor(context))
    return this
}

fun calculateScale(
    constraints: Constraints,
    sourceInfo: SourceInfo,
    scaleType: PreviewScaleType
): Float {
    val heightRatio = constraints.maxHeight.toFloat() / sourceInfo.height
    val widthRatio = constraints.maxWidth.toFloat() / sourceInfo.width
    return when (scaleType) {
        PreviewScaleType.FIT_CENTER -> kotlin.math.min(heightRatio, widthRatio)
        PreviewScaleType.CENTER_CROP -> kotlin.math.max(heightRatio, widthRatio)
    }
}