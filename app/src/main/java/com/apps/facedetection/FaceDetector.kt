package com.apps.facedetection

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

class FaceDetector(
    private val ovalCenter: Offset,
    private val ovalRadiusX: Float,
    private val ovalRadiusY: Float,
    private val onFaceDetected: (Boolean) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        private const val THROTTLE_TIMEOUT_MS = 4_00L
        private const val MIN_FACE_SIZE = 50f
        private const val FACE_SIZE_MULTIPLIER = 3.5f
        private const val FACE_POSITION_ACCURACY = 0.3f
    }

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val faceDetector: FaceDetector = FaceDetection.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        scope.launch {
            val mediaImage = imageProxy.image ?: run {
                imageProxy.close()
                return@launch
            }

            val inputImage = InputImage.fromMediaImage(
                mediaImage, imageProxy.imageInfo.rotationDegrees,
            )

            suspendCoroutine<Unit> { continuation ->
                faceDetector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        val isFaceDetected = faces.any {
                            isFaceInsideOval(
                                Offset(it.boundingBox.centerX().toFloat(), it.boundingBox.centerY().toFloat()),
                                it.boundingBox.width().toFloat(),
                                it.boundingBox.height().toFloat()
                            )
                        }
                        onFaceDetected(isFaceDetected)
                    }
                    .addOnFailureListener { exception ->
                        exception.printStackTrace()
                    }
                    .addOnCompleteListener {
                        continuation.resume(Unit)
                    }
            }

            delay(THROTTLE_TIMEOUT_MS)
        }.invokeOnCompletion { exception ->
            exception?.printStackTrace()
            imageProxy.close()
        }
    }

private fun isFaceInsideOval(
    faceCenter: Offset,
    faceWidth: Float,
    faceHeight: Float
): Boolean {

    val verticalFaceCenter = Offset(
        faceCenter.x,
        faceCenter.y - ovalRadiusY
    )

    val xInsideOval = abs(verticalFaceCenter.x - ovalCenter.x) <= (ovalRadiusX * FACE_POSITION_ACCURACY)
    val yInsideOval = abs(verticalFaceCenter.y - ovalCenter.y) <= (ovalRadiusY * FACE_POSITION_ACCURACY)

    val isCenterInsideOval = xInsideOval && yInsideOval

    val faceFitsInOval = faceWidth in (MIN_FACE_SIZE)..(ovalRadiusX * FACE_SIZE_MULTIPLIER) &&
            faceHeight in MIN_FACE_SIZE..(ovalRadiusY * FACE_SIZE_MULTIPLIER)

    return isCenterInsideOval && faceFitsInOval
}

}