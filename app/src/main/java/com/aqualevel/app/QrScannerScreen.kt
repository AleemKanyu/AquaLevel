package com.aqualevel.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Full-screen camera preview with an animated scan frame overlay.
 * Uses LifecycleCameraController (no ProcessCameraProvider / ListenableFuture needed).
 * Detects QR codes matching "aqualevel://pair?id=..." via ML Kit.
 *
 * @param onCodeScanned  Called once with the raw QR string on a valid AquaLevel QR.
 */
@Composable
fun QrScannerScreen(onCodeScanned: (String) -> Unit) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        // ── Permission denied UI ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A1628)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector        = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint               = Color(0xFF1CB0F6),
                    modifier           = Modifier.size(72.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Camera permission required\nto scan QR codes",
                    color     = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize  = 16.sp
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF1CB0F6)),
                    shape   = RoundedCornerShape(12.dp)
                ) {
                    Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // ── Camera setup using LifecycleCameraController ──────────────────────────
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    var alreadyScanned by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                val controller = LifecycleCameraController(ctx).apply {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                    setImageAnalysisAnalyzer(
                        ContextCompat.getMainExecutor(ctx)
                    ) { imageProxy ->
                        if (!alreadyScanned) {
                            analyzeImageProxy(barcodeScanner, imageProxy) { rawValue ->
                                if (rawValue.startsWith("aqualevel://pair?id=")) {
                                    alreadyScanned = true
                                    onCodeScanned(rawValue)
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                    bindToLifecycle(lifecycleOwner)
                }

                previewView.controller = controller
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Animated scan frame overlay ───────────────────────────────────────
        ScanFrameOverlay()

        // ── Hint text ─────────────────────────────────────────────────────────
        Text(
            text      = "Point camera at the QR code\non your AquaLevel device",
            color     = Color.White,
            fontSize  = 14.sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .padding(horizontal = 32.dp)
        )
    }
}

// ── Overlay ───────────────────────────────────────────────────────────────────

@Composable
private fun ScanFrameOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val frameSize    = size.minDimension * 0.65f
        val left         = (size.width  - frameSize) / 2f
        val top          = (size.height - frameSize) / 2f
        val frameRect    = Rect(left, top, left + frameSize, top + frameSize)
        val cornerLen    = frameSize * 0.12f
        val cornerRadius = 8.dp.toPx()
        val strokeW      = 4.dp.toPx()

        // Dark scrim outside the frame
        val cutoutPath = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(frameRect, CornerRadius(cornerRadius)))
        }
        clipPath(cutoutPath, ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }

        // Animated scan line
        val scanY = top + (frameSize * scanLineProgress)
        drawLine(
            color       = Color(0xFF1CB0F6).copy(alpha = 0.85f),
            start       = Offset(left + 8.dp.toPx(), scanY),
            end         = Offset(left + frameSize - 8.dp.toPx(), scanY),
            strokeWidth = 2.dp.toPx()
        )

        // Corner brackets
        data class Corner(val x: Float, val y: Float, val dx: Float, val dy: Float)
        listOf(
            Corner(left,             top,             1f,  1f),
            Corner(left + frameSize, top,            -1f,  1f),
            Corner(left,             top + frameSize, 1f, -1f),
            Corner(left + frameSize, top + frameSize,-1f, -1f)
        ).forEach { (cx, cy, dx, dy) ->
            drawLine(Color(0xFF1CB0F6), Offset(cx, cy), Offset(cx + dx * cornerLen, cy), strokeW, StrokeCap.Round)
            drawLine(Color(0xFF1CB0F6), Offset(cx, cy), Offset(cx, cy + dy * cornerLen), strokeW, StrokeCap.Round)
        }
    }
}

// ── ML Kit analysis ───────────────────────────────────────────────────────────

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeImageProxy(
    scanner:     com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy:  ImageProxy,
    onDetected:  (String) -> Unit
) {
    val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes
                .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                ?.rawValue
                ?.let(onDetected)
        }
        .addOnCompleteListener { imageProxy.close() }
}
