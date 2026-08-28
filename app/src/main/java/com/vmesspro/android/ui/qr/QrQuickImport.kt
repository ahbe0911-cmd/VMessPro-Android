package com.vmesspro.android.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vmesspro.android.domain.config.BulkImportParser
import com.vmesspro.android.ui.AppViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global QR importer. A QR payload is kept intact, including newlines, and then passed to
 * BulkImportParser so one QR can contain multiple VMess/VLESS/Trojan/subscription entries.
 */
@Composable
fun QrQuickImport(viewModel: AppViewModel) {
    var scannerOpen by rememberSaveable { mutableStateOf(false) }
    var scannedPayload by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        SmallFloatingActionButton(
            onClick = { scannerOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 82.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "ورود کانفیگ با QR")
        }
    }

    if (scannerOpen) {
        QrScannerDialog(
            onPayload = { raw ->
                // Normalize only line-ending representation. Do not split or drop any QR line here.
                scannedPayload = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
                scannerOpen = false
            },
            onDismiss = { scannerOpen = false },
        )
    }

    scannedPayload?.let { payload ->
        val preview = remember(payload) { BulkImportParser.parse(payload) }
        val canImport = preview.validServerCount > 0 || preview.subscriptionUrls.isNotEmpty()
        val nonBlankLines = remember(payload) { payload.lineSequence().count { it.isNotBlank() } }

        AlertDialog(
            onDismissRequest = { scannedPayload = null },
            icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
            title = { Text(if (canImport) "QR چندخطی شناسایی شد" else "QR قابل وارد کردن نیست") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (canImport) {
                            "$nonBlankLines خط خوانده شد • ${preview.validServerCount} سرور معتبر • " +
                                "${preview.subscriptionUrls.size} اشتراک • ${preview.duplicateCount} تکراری"
                        } else {
                            "داخل این QR کانفیگ VMess / VLESS / Trojan یا لینک Subscription معتبری پیدا نشد."
                        },
                    )
                    if (preview.invalidCount > 0) {
                        Text(
                            "${preview.invalidCount} مورد نامعتبر نادیده گرفته می‌شود.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                if (canImport) {
                    Button(onClick = {
                        viewModel.importText(payload)
                        scannedPayload = null
                    }) {
                        Text("ذخیره همه")
                    }
                } else {
                    Button(onClick = {
                        scannedPayload = null
                        scannerOpen = true
                    }) {
                        Text("اسکن دوباره")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { scannedPayload = null }) { Text("انصراف") }
            },
        )
    }
}

@Composable
private fun QrScannerDialog(
    onPayload: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var cameraError by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF020610),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF061322), Color(0xFF020610), Color(0xFF050A14))
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "اسکن QR کانفیگ",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                            )
                            Text(
                                "یک QR می‌تواند چند خط کانفیگ داشته باشد",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9FB2C9),
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = Color.White)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    when {
                        hasPermission -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .border(
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                        RoundedCornerShape(28.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                QrCameraPreview(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(3.dp),
                                    onPayload = onPayload,
                                    onError = { cameraError = it },
                                )
                                Box(
                                    modifier = Modifier
                                        .size(238.dp)
                                        .border(
                                            BorderStroke(2.dp, Color.White.copy(alpha = 0.72f)),
                                            RoundedCornerShape(28.dp),
                                        )
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                cameraError ?: "QR را داخل کادر قرار دهید؛ تمام خطوط آن یکجا خوانده می‌شود.",
                                color = if (cameraError == null) Color(0xFFB6C5D8) else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        permissionRequested -> {
                            PermissionPanel(
                                onRetry = {
                                    cameraError = null
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                onDismiss = onDismiss,
                            )
                        }

                        else -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPanel(onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("برای اسکن QR فقط در همین بخش به مجوز دوربین نیاز است.", color = Color.White)
        Button(onClick = onRetry) { Text("صدور مجوز دوربین") }
        TextButton(onClick = onDismiss) { Text("بستن") }
    }
}

@Composable
private fun QrCameraPreview(
    modifier: Modifier = Modifier,
    onPayload: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPayload by rememberUpdatedState(onPayload)
    val latestOnError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        val scannerOptions = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(scannerOptions)
        val delivered = AtomicBoolean(false)
        val active = AtomicBoolean(true)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            analyzeQrFrame(
                imageProxy = imageProxy,
                scanner = scanner,
                active = active,
                delivered = delivered,
                onPayload = { payload -> previewView.post { latestOnPayload(payload) } },
            )
        }

        providerFuture.addListener(
            {
                if (!active.get()) return@addListener
                runCatching {
                    provider = providerFuture.get()
                    provider?.unbindAll()
                    provider?.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure { throwable ->
                    previewView.post {
                        latestOnError(throwable.message ?: "دوربین برای اسکن QR آماده نشد")
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            active.set(false)
            analysis.clearAnalyzer()
            provider?.unbindAll()
            scanner.close()
            cameraExecutor.shutdownNow()
        }
    }
}

@ExperimentalGetImage
private fun analyzeQrFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    active: AtomicBoolean,
    delivered: AtomicBoolean,
    onPayload: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            if (!active.get() || delivered.get()) return@addOnSuccessListener
            val payload = barcodes
                .asSequence()
                .mapNotNull { it.rawValue }
                .firstOrNull { it.isNotBlank() }
            if (payload != null && delivered.compareAndSet(false, true)) {
                onPayload(payload)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
