package gr.dkaratzas.tanrenkiroku.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import gr.dkaratzas.tanrenkiroku.ui.viewmodel.SyncState
import gr.dkaratzas.tanrenkiroku.ui.viewmodel.SyncViewModel
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
    onBack: () -> Unit
) {
    val state = viewModel.state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync to Desktop") },
                navigationIcon = { BackButton(onClick = { viewModel.reset(); onBack() }) }
            )
        }
    ) { padding ->
        when (state) {
            is SyncState.Scanning -> QrScannerSection(
                modifier = Modifier.padding(padding),
                onQrFound = viewModel::onQrScanned
            )

            is SyncState.InProgress -> SyncProgressSection(
                modifier = Modifier.padding(padding),
                step = state.step
            )

            is SyncState.Done -> SyncDoneSection(
                modifier = Modifier.padding(padding),
                uploaded = state.uploaded,
                deleted = state.deleted,
                onDone = { viewModel.reset(); onBack() },
                onSyncAgain = viewModel::reset
            )

            is SyncState.Failed -> SyncErrorSection(
                modifier = Modifier.padding(padding),
                message = state.message,
                onRetry = viewModel::reset,
                onBack = { viewModel.reset(); onBack() }
            )
        }
    }
}

@Composable
private fun QrScannerSection(modifier: Modifier, onQrFound: (String) -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
            if (!granted) permissionDenied = true
        }

    LaunchedEffect(Unit) {
        if (!permissionGranted) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            permissionGranted -> CameraQrView(onQrFound = onQrFound)
            permissionDenied -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Camera permission is required to scan the QR code.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }

            else -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun CameraQrView(onQrFound: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handled = remember { AtomicBoolean(false) }
    val providerRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    runCatching {
                        val provider = future.get()
                        providerRef.value = provider

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val scanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                        )
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().apply {
                                setAnalyzer(ContextCompat.getMainExecutor(ctx)) @androidx.camera.core.ExperimentalGetImage { proxy ->
                                    proxy.image?.let { img ->
                                        val input = InputImage.fromMediaImage(
                                            img, proxy.imageInfo.rotationDegrees
                                        )
                                        scanner.process(input)
                                            .addOnSuccessListener { barcodes ->
                                                barcodes.firstOrNull()?.rawValue?.let { raw ->
                                                    if (handled.compareAndSet(false, true)) {
                                                        onQrFound(raw)
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener { proxy.close() }
                                    } ?: proxy.close()
                                }
                            }

                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            onRelease = { providerRef.value?.unbindAll() },
            modifier = Modifier.fillMaxSize()
        )

        Text(
            text = "Point at the QR code shown on your desktop",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SyncProgressSection(modifier: Modifier, step: String) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CircularProgressIndicator()
            Text(step, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun syncSummary(uploaded: Int, deleted: Int): String = when {
    uploaded == 0 && deleted == 0 -> "Already up to date"
    uploaded == 0 -> "Removed ${deleted.files} from desktop"
    deleted == 0 -> "Synced ${uploaded.files}"
    else -> "Synced ${uploaded.files}, removed ${deleted.files} from desktop"
}

private val Int.files get() = if (this == 1) "1 file" else "$this files"

@Composable
private fun SyncDoneSection(
    modifier: Modifier,
    uploaded: Int,
    deleted: Int,
    onDone: () -> Unit,
    onSyncAgain: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                syncSummary(uploaded, deleted),
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSyncAgain) { Text("Sync Again") }
                Button(onClick = onDone) { Text("Done") }
            }
        }
    }
}

@Composable
private fun SyncErrorSection(
    modifier: Modifier,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text("Sync Failed", style = MaterialTheme.typography.titleMedium)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("Close") }
                Button(onClick = onRetry) { Text("Try Again") }
            }
        }
    }
}
