package ir.dinal.storehub.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScannerScreen(nav: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            }
            val executor = remember { Executors.newSingleThreadExecutor() }
            val handled = remember { AtomicBoolean(false) }
            var camera by remember { mutableStateOf<Camera?>(null) }
            var torchOn by remember { mutableStateOf(false) }
            val scanner = remember {
                BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .build()
                )
            }

            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            DisposableEffect(previewView, lifecycleOwner) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val mainExecutor = ContextCompat.getMainExecutor(context)
                cameraProviderFuture.addListener({
                    runCatching {
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { proxy ->
                            val mediaImage = proxy.image
                            if (mediaImage == null || handled.get()) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { list ->
                                    val raw = list.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                    if (!raw.isNullOrBlank() && handled.compareAndSet(false, true)) {
                                        nav.previousBackStackEntry?.savedStateHandle?.set("scan_result", raw.trim())
                                        nav.popBackStack()
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, mainExecutor)

                onDispose {
                    runCatching { if (cameraProviderFuture.isDone) cameraProviderFuture.get().unbindAll() }
                    scanner.close()
                    executor.shutdown()
                    camera = null
                }
            }

            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(270.dp)
                    .border(3.dp, Color.White.copy(alpha = .92f), RoundedCornerShape(30.dp))
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(.82f)
                        .height(2.dp)
                        .background(Color(0xFFD6AA57))
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                color = Color.Black.copy(alpha = .72f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("بارکد یا QR را داخل کادر نگه دار", color = Color.White)
                }
            }

            IconButton(
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                enabled = camera?.cameraInfo?.hasFlashUnit() == true,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
            ) {
                Icon(if (torchOn) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn, contentDescription = "چراغ قوه", tint = Color.White)
            }
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(64.dp))
                Text("برای اسکن بارکد و QR، دسترسی دوربین لازم است.", color = Color.White)
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("دادن دسترسی دوربین") }
            }
        }

        IconButton(
            onClick = { nav.popBackStack() },
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = Color.White)
        }
    }
}
