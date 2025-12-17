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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ttcoach.analysis.PoseAnalysisCoordinator
import com.ttcoach.analysis.TechniqueAnalysis
import com.ttcoach.analysis.HitDetectionResult
import com.ttcoach.camera.CameraManager
import com.ttcoach.cv.MediaPipePoseProcessor
import com.ttcoach.cv.KeyPoint
import com.ttcoach.utils.ImageUtils

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
    var keyPoints by remember { mutableStateOf<List<KeyPoint>?>(null) }
    
    // Track if composable is still active
    var isActive by remember { mutableStateOf(true) }
    
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
                if (activity != null && previewViewRef != null && isActive) {
                    cameraManager.initialize(previewViewRef!!, activity) { error ->
                        // Only update state if composable is still active
                        if (isActive) {
                            cameraError = error
                        }
                    }
                    
                    // Observe FPS - collect directly in LaunchedEffect so it's cancelled automatically
                    cameraManager.fps.collect {
                        if (isActive) {
                            fps = it
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    cameraError = e.message
                }
            }
        } else if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // Observe pose analysis results
    LaunchedEffect(poseCoordinator) {
        poseCoordinator.techniqueAnalysis.collect {
            if (isActive) {
                techniqueAnalysis = it
            }
        }
    }
    
    LaunchedEffect(poseCoordinator) {
        poseCoordinator.hitDetection.collect {
            if (isActive) {
                hitDetection = it
            }
        }
    }
    
    // Process camera frames for pose analysis
    LaunchedEffect(cameraManager, poseProcessor) {
        cameraManager.frameFlow.collect { imageProxy ->
            if (!isActive) {
                imageProxy?.close()
                return@collect
            }
            
            imageProxy?.let {
                try {
                    val timestamp = System.currentTimeMillis()
                    
                    // Convert ImageProxy to Bitmap
                    val bitmap = ImageUtils.imageProxyToBitmap(it)
                    
                    if (bitmap != null && poseProcessor.isInitialized.value) {
                        // Process frame with MediaPipe
                        poseProcessor.processFrame(bitmap, timestamp)
                        
                        // Get key points for visualization
                        val points = poseProcessor.getKeyPoints()
                        if (isActive) {
                            keyPoints = points
                        }
                        
                        // Process with pose coordinator for analysis
                        if (points != null) {
                            poseCoordinator.processFrame(ballPosition = null, timestamp = timestamp)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CameraPreview", "Error processing frame", e)
                } finally {
                    imageProxy.close()
                }
            }
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        isActive = true
        onDispose {
            isActive = false
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
                // Camera Preview with Pose Overlay
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                ) {
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
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Pose Key Points Overlay
                    keyPoints?.let { points ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw skeleton connections first (so points appear on top)
                            drawPoseConnections(points, size.width, size.height)
                            
                            // Draw key points
                            points.forEach { point ->
                                if (point.visibility > 0.5f) {
                                    val x = point.x * size.width
                                    val y = point.y * size.height
                                    
                                    // Draw key point
                                    drawCircle(
                                        color = Color.Red,
                                        radius = 6.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                    
                                    // Draw visibility indicator
                                    drawCircle(
                                        color = Color.Green.copy(alpha = point.visibility),
                                        radius = 4.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                }
                            }
                        }
                    }
                }
                
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

/**
 * Draw pose skeleton connections
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPoseConnections(
    points: List<KeyPoint>,
    width: Float,
    height: Float
) {
    if (points.size < 33) return
    
    val strokeWidth = 3.dp.toPx()
    val connectionColor = Color.Cyan.copy(alpha = 0.7f)
    
    // Helper function to get point if visible
    fun getPoint(index: Int): Offset? {
        if (index >= points.size) return null
        val point = points[index]
        return if (point.visibility > 0.5f) {
            Offset(point.x * width, point.y * height)
        } else null
    }
    
    // Draw connections
    val connections = listOf(
        // Face
        Pair(LEFT_EYE, RIGHT_EYE),
        Pair(LEFT_EYE, NOSE),
        Pair(RIGHT_EYE, NOSE),
        Pair(LEFT_EAR, LEFT_EYE),
        Pair(RIGHT_EAR, RIGHT_EYE),
        // Upper body
        Pair(LEFT_SHOULDER, RIGHT_SHOULDER),
        Pair(LEFT_SHOULDER, LEFT_ELBOW),
        Pair(LEFT_ELBOW, LEFT_WRIST),
        Pair(RIGHT_SHOULDER, RIGHT_ELBOW),
        Pair(RIGHT_ELBOW, RIGHT_WRIST),
        Pair(LEFT_SHOULDER, LEFT_HIP),
        Pair(RIGHT_SHOULDER, RIGHT_HIP),
        // Torso
        Pair(LEFT_HIP, RIGHT_HIP),
        // Lower body
        Pair(LEFT_HIP, LEFT_KNEE),
        Pair(LEFT_KNEE, LEFT_ANKLE),
        Pair(RIGHT_HIP, RIGHT_KNEE),
        Pair(RIGHT_KNEE, RIGHT_ANKLE),
    )
    
    connections.forEach { (startIdx, endIdx) ->
        val start = getPoint(startIdx)
        val end = getPoint(endIdx)
        if (start != null && end != null) {
            drawLine(
                color = connectionColor,
                start = start,
                end = end,
                strokeWidth = strokeWidth
            )
        }
    }
}

