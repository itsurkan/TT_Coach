package com.ttcoach.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ttcoach.analysis.PoseAnalysisCoordinator
import com.ttcoach.analysis.TechniqueAnalysis
import com.ttcoach.analysis.HitDetectionResult
import com.ttcoach.camera.CameraManager
import com.ttcoach.cv.MediaPipePoseProcessor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var hasPermission by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var fps by remember { mutableStateOf(0f) }
    
    val cameraManager = remember { CameraManager(context) }
    val poseProcessor = remember { MediaPipePoseProcessor(context) }
    val poseCoordinator = remember { PoseAnalysisCoordinator(poseProcessor, useRightArm = true) }
    
    var techniqueAnalysis by remember { mutableStateOf<TechniqueAnalysis?>(null) }
    var hitDetection by remember { mutableStateOf<HitDetectionResult?>(null) }
    
    // Check camera permission
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // Request permission if needed
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (!isGranted) {
            cameraError = "Camera permission is required"
        }
    }
    
    // Initialize camera when permission is granted
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    
    LaunchedEffect(hasPermission, previewViewRef) {
        if (hasPermission && previewViewRef != null) {
            try {
                val activity = context as? androidx.activity.ComponentActivity
                if (activity != null && previewViewRef != null) {
                    cameraManager.initialize(previewViewRef!!, activity) { error ->
                        cameraError = error
                    }
                    
                    // Observe FPS - collect directly in LaunchedEffect so it's cancelled automatically
                    cameraManager.fps.collect {
                        fps = it
                    }
                }
            } catch (e: Exception) {
                cameraError = e.message
            }
        } else if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // Observe pose analysis results
    LaunchedEffect(poseCoordinator) {
        poseCoordinator.techniqueAnalysis.collect {
            techniqueAnalysis = it
        }
    }
    
    LaunchedEffect(poseCoordinator) {
        poseCoordinator.hitDetection.collect {
            hitDetection = it
        }
    }
    
    // Process camera frames for pose analysis
    LaunchedEffect(cameraManager) {
        cameraManager.frameFlow.collect { imageProxy ->
            imageProxy?.let {
                val timestamp = System.currentTimeMillis()
                // TODO: Convert ImageProxy to Bitmap and process with MediaPipe
                // For now, process with pose coordinator when MediaPipe is ready
                // poseCoordinator.processFrame(ballPosition = null, timestamp = timestamp)
                imageProxy.close()
            }
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
            poseProcessor.release()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TT Coach - Camera Preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (cameraError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = cameraError ?: "Unknown error",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            if (hasPermission) {
                // Camera Preview
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewViewRef = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                
                // FPS Display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FPS: ${fps.toInt()}")
                        Text("Pose: ${if (poseProcessor.isInitialized.value) "Ready" else "Initializing..."}")
                    }
                }
                
                // Technique Analysis Display
                techniqueAnalysis?.let { analysis ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Technique Analysis",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            analysis.elbowAngle?.let {
                                Text("Elbow: ${it.toInt()}° (Score: ${(analysis.elbowScore * 100).toInt()}%)")
                            }
                            analysis.shoulderAngle?.let {
                                Text("Shoulder: ${it.toInt()}° (Score: ${(analysis.shoulderScore * 100).toInt()}%)")
                            }
                            analysis.bodyRotation?.let {
                                Text("Body Rotation: ${it.toInt()}° (Score: ${(analysis.bodyRotationScore * 100).toInt()}%)")
                            }
                            Text(
                                text = "Overall Score: ${(analysis.overallScore * 100).toInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                // Hit Detection Display
                hitDetection?.let { hit ->
                    if (hit.hitDetected || hit.hitStart || hit.hitEnd) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hit.hitDetected) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = when {
                                        hit.hitStart -> "HIT START"
                                        hit.hitEnd -> "HIT END"
                                        hit.hitDetected -> "HIT!"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Velocity: ${(hit.wristVelocity * 1000).toInt()}",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            } else {
                // Permission request UI
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Camera permission required",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }
}

