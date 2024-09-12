package com.apps.facedetection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

private const val OVAL_WIDTH_DP = 250
private const val OVAL_HEIGHT_DP = 300
private const val OVAL_LEFT_OFFSET_RATIO = 2
private const val OVAL_TOP_OFFSET_RATIO = 3

@SuppressLint("RememberReturnType")
@Composable
fun FaceDetectionScreen() {
    val context: Context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current

    var isCameraShown by remember { mutableStateOf(true) }
    var isFaceDetected by remember { mutableStateOf(false) }

    var capturedPhoto by remember { mutableStateOf<ImageBitmap?>(null) }
    var ovalCenter by remember { mutableStateOf<Offset?>(null) }

    val cameraController: LifecycleCameraController =
        remember { LifecycleCameraController(context) }
    cameraController.cameraSelector =
        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

    val cameraPreviewView = remember {
        mutableStateOf(PreviewView(context))
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .statusBarsPadding(),
    ) { paddingValues: PaddingValues ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isCameraShown) {
                CameraView(paddingValues, cameraPreviewView)
            } else {
                capturedPhoto?.let { photo ->
                    CapturedPhotoView(photo)
                }
            }

            OvalOverlay(
                modifier = Modifier.fillMaxSize(),
                isFaceDetected = isFaceDetected,
                onCenterCalculated = { ovalCenter = it }
            )
            ovalCenter?.let {
                startFaceDetection(
                    context = context,
                    cameraController = cameraController,
                    lifecycleOwner = lifecycleOwner,
                    previewView = cameraPreviewView.value,
                    onFaceDetected = { detected ->
                        isFaceDetected = detected
                    },
                    it,
                )
            }

            if (isCameraShown) {
                CapturePhotoButton(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 50.dp),
                    isFaceDetected = isFaceDetected,
                    context = context,
                    cameraController = cameraController,
                    onCapturePhoto = { capturedPhoto = it },
                    onCloseCamera = {
                        if (isFaceDetected)
                            isCameraShown = false
                    },
                )
            }
        }

    }
}

@Composable
private fun CapturedPhotoView(photo: ImageBitmap) {
    Image(
        modifier = Modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .fillMaxSize(),
        bitmap = photo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun CameraView(
    paddingValues: PaddingValues,
    cameraPreviewView: MutableState<PreviewView>,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        factory = { context ->
            PreviewView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also {
                cameraPreviewView.value = it
            }
        },
    )
}

@Composable
private fun CapturePhotoButton(
    modifier: Modifier,
    isFaceDetected: Boolean,
    context: Context,
    cameraController: LifecycleCameraController,
    onCapturePhoto: (ImageBitmap?) -> Unit,
    onCloseCamera: () -> Unit,
) {

    Image(
        modifier = modifier
            .padding(top = 20.dp)
            .size(92.dp)
            .clickable {
                capturePhotoAndReplaceBackground(
                    context,
                    cameraController,
                    isFaceDetected
                ) { capturedBitmap ->
                    onCapturePhoto(capturedBitmap.asImageBitmap())
                    onCloseCamera()
                }

            },
        painter = painterResource(
            id =
            if (isFaceDetected)
                R.drawable.camera_button_enabled
            else
                R.drawable.camera_button_disabled,
        ),
        contentDescription = null,
    )

}

private fun capturePhotoAndReplaceBackground(
    context: Context,
    cameraController: LifecycleCameraController,
    isFaceDetected: Boolean,
    onBackgroundReplaced: (Bitmap) -> Unit,
) {
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    if (isFaceDetected) {
        cameraController.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val capturedBitmap: Bitmap =
                        image.toBitmap().rotateBitmap(image.imageInfo.rotationDegrees)

                    processCapturedPhotoAndReplaceBackground(capturedBitmap) { processedBitmap ->
                        onBackgroundReplaced(processedBitmap)
                    }

                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                }
            },
        )
    }
}

private fun processCapturedPhotoAndReplaceBackground(
    capturedBitmap: Bitmap,
    onBackgroundReplaced: (Bitmap) -> Unit,
) {
    onBackgroundReplaced(capturedBitmap)
}

private fun startFaceDetection(
    context: Context,
    cameraController: LifecycleCameraController,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onFaceDetected: (Boolean) -> Unit,
    ovalRect: Offset,
) {

    cameraController.imageAnalysisTargetSize = CameraController.OutputSize(AspectRatio.RATIO_16_9)
    cameraController.setImageAnalysisAnalyzer(
        ContextCompat.getMainExecutor(context),
        FaceDetector(
            onFaceDetected = onFaceDetected,
            ovalCenter = ovalRect,
            ovalRadiusX = OVAL_WIDTH_DP / 2f,
            ovalRadiusY = OVAL_HEIGHT_DP / 2f,
        ),
    )

    cameraController.bindToLifecycle(lifecycleOwner)
    previewView.controller = cameraController
}


@Composable
private fun OvalOverlay(
    modifier: Modifier = Modifier,
    isFaceDetected: Boolean,
    onCenterCalculated: (Offset) -> Unit = {},
) {
    val ovalColor =
        if (isFaceDetected) Color.Green else Color.Red
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        val ovalCenterOffset = remember {
            mutableStateOf<Offset?>(null)
        }
        LaunchedEffect(ovalCenterOffset) {
            ovalCenterOffset.value?.let { onCenterCalculated(it) }
        }
        Canvas(modifier = modifier) {
            val ovalSize = Size(OVAL_WIDTH_DP.dp.toPx(), OVAL_HEIGHT_DP.dp.toPx())
            val ovalLeftOffset = (size.width - ovalSize.width) / OVAL_LEFT_OFFSET_RATIO
            val ovalTopOffset = (size.height - ovalSize.height) / OVAL_TOP_OFFSET_RATIO

            ovalCenterOffset.value =
                Offset(
                    (ovalLeftOffset + OVAL_WIDTH_DP / OVAL_LEFT_OFFSET_RATIO),
                    (ovalTopOffset - OVAL_HEIGHT_DP / OVAL_TOP_OFFSET_RATIO)
                )

            val ovalRect =
                Rect(
                    ovalLeftOffset,
                    ovalTopOffset,
                    ovalLeftOffset + ovalSize.width,
                    ovalTopOffset + ovalSize.height
                )
            val ovalPath = Path().apply {
                addOval(ovalRect)
            }
            clipPath(ovalPath, clipOp = ClipOp.Difference) {
                drawRect(SolidColor(Color.Black.copy(alpha = 0.8f)))
            }
        }


        Canvas(
            modifier = modifier,
        ) {
            val ovalSize = Size(OVAL_WIDTH_DP.dp.toPx(), OVAL_HEIGHT_DP.dp.toPx())
            val ovalLeft = (size.width - ovalSize.width) / OVAL_LEFT_OFFSET_RATIO
            val ovalTop =
                (size.height - ovalSize.height) / OVAL_TOP_OFFSET_RATIO - ovalSize.height
            drawOval(
                color = ovalColor,
                style = Stroke(width = OVAL_TOP_OFFSET_RATIO.dp.toPx()),
                topLeft = Offset(ovalLeft, ovalTop + ovalSize.height),
                size = ovalSize,
            )
        }

    }
}


