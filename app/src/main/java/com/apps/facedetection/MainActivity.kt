package com.apps.facedetection

import android.Manifest.permission.CAMERA
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.apps.facedetection.ui.theme.FaceDetectionTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceDetectionTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    var showFaceDetectionScreen by remember { mutableStateOf(false) }
    var permissionLaunched by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val cameraPermissionState = rememberPermissionState(permission = CAMERA) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Permission denied", Toast.LENGTH_LONG).show()
        }
    }

    if (permissionLaunched) {
        LaunchedEffect(cameraPermissionState.status) {
            showFaceDetectionScreen = cameraPermissionState.status.isGranted
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (!permissionLaunched) {
            PermissionRequestButton(
                onRequestPermission = {
                    cameraPermissionState.launchPermissionRequest()
                    permissionLaunched = true
                }
            )
        } else if (showFaceDetectionScreen) {
            FaceDetectionScreen()
        }
    }
}

@Composable
fun PermissionRequestButton(onRequestPermission: () -> Unit) {
    Button(onClick = { onRequestPermission() }) {
        Text(text = "Open Camera")
    }
}
