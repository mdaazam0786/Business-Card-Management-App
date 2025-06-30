package com.example.swiftcard.ui.ScanBusinessCard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.swiftcard.R

// ScanBusinessCardScreen: Handles camera preview, image capture, and OCR.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBusinessCardScreen(
    onScanComplete: (extractedData: Map<String, String>) -> Unit, // Placeholder for extracted data
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor: MutableState<ExecutorService?> = remember { mutableStateOf(null) }
    val imageCapture: MutableState<ImageCapture?> = remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    // Request camera permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
            onBack() // Go back if permission is denied
        }
    }

    // Launch permission request when the screen first appears
    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // DisposableEffect to manage camera executor lifecycle
    DisposableEffect(Unit) {
        cameraExecutor.value = Executors.newSingleThreadExecutor()
        onDispose {
            cameraExecutor.value?.shutdown()
        }
    }

    // For picking an image from the gallery
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                processImageForOcr(context, it, onScanComplete, coroutineScope = scope)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Business Card") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = {
                        imageCapture.value?.let { capture ->
                            val photoFile = File(context.externalCacheDir, "${System.currentTimeMillis()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            capture.takePicture(
                                outputOptions,
                                cameraExecutor.value!!,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onError(exc: ImageCaptureException) {
                                        Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
                                        scope.launch { // Launch a coroutine
                                            withContext(Dispatchers.Main) { // Switch to Main thread
                                                Toast.makeText(context, "Photo capture failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }

                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                                        scope.launch { // Launch a coroutine
                                            withContext(Dispatchers.Main) { // Switch to Main thread
                                                Toast.makeText(context, "Photo captured!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        processImageForOcr(context, savedUri, onScanComplete, scope)
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(painterResource(R.drawable.img), contentDescription = "Take Photo")
                }
                FloatingActionButton(
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Icon(painterResource(R.drawable.img_1), contentDescription = "Pick from Gallery")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        // AndroidView to embed CameraX PreviewView
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    // ScaleType.FILL_CENTER for better preview
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    imageCapture.value = ImageCapture.Builder().build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture.value
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraX", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
    }
}

// Function to process image for OCR using ML Kit Text Recognition
// Function to process image for OCR using ML Kit Text Recognition
private fun processImageForOcr(
    context: Context,
    imageUri: Uri,
    onScanComplete: (extractedData: Map<String, String>) -> Unit,
    coroutineScope: CoroutineScope // Pass CoroutineScope here
) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    coroutineScope.launch { // This `launch` block creates a coroutine
        val bitmap: Bitmap? = try {
            withContext(Dispatchers.IO) { // <--- This withContext is fine here
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri))
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                }
            }
        } catch (e: Exception) {
            Log.e("OCR", "Failed to load bitmap: ${e.message}", e)
            withContext(Dispatchers.Main) { // <--- This withContext is also fine
                Toast.makeText(context, "Failed to load image for OCR", Toast.LENGTH_SHORT).show()
            }
            null
        }

        bitmap?.let { img ->
            val image = InputImage.fromBitmap(img, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Since this block is a callback, it's not a coroutine.
                    // If you need to use suspend functions (like withContext) here,
                    // you must launch a new coroutine within this block.
                    coroutineScope.launch { // Launch a new coroutine for suspend calls
                        val extractedText = visionText.textBlocks.joinToString("\n") { it.text }
                        val data = mapOf(
                            "name" to "",
                            "company" to "",
                            "title" to "",
                            "extractedRawText" to extractedText
                        )

                        onScanComplete(data)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "OCR Complete!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    // Same here: Launch a new coroutine if you need suspend functions
                    coroutineScope.launch { // Launch a new coroutine for suspend calls
                        Log.e("OCR", "Text recognition failed: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }
    }
}