package com.example.swiftcard.ui.ScanBusinessCard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.gson.Gson
import android.os.Handler
import android.os.Looper
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
    onScanComplete: (extractedDataJson: String) -> Unit,// Placeholder for extracted data
    onBack: () -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                        Handler(Looper.getMainLooper()).post {
                                            Toast.makeText(context, "Photo capture failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                                        Handler(Looper.getMainLooper()).post {
                                            Toast.makeText(context, "Photo captured!", Toast.LENGTH_SHORT).show()
                                        }
                                        processImageForOcr(context, savedUri, onScanComplete, scope)
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.img),
                        contentDescription = "Take Photo",
                        modifier = Modifier.size(24.dp)
                    )
                }
                FloatingActionButton(
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.img_1),
                        contentDescription = "Pick from Gallery",
                        modifier = Modifier.size(24.dp)
                    )
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
// In your ScanBusinessCardScreen.kt file, modify processImageForOcr:

private fun processImageForOcr(
    context: Context,
    imageUri: Uri,
    onScanComplete: (extractedData: String) -> Unit,
    coroutineScope: CoroutineScope // Pass CoroutineScope here
) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    coroutineScope.launch {
        val bitmap: Bitmap? = try {
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri))
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                }
            }
        } catch (e: Exception) {
            Log.e("OCR", "Failed to load bitmap: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to load image for OCR", Toast.LENGTH_SHORT).show()
            }
            null
        }

        bitmap?.let { img ->
            val image = InputImage.fromBitmap(img, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    coroutineScope.launch {
                        val extractedText = visionText.textBlocks.joinToString("\n") { it.text }
                        val parsedData = parseBusinessCardText(extractedText)

                        // Convert map to JSON string before passing via callback
                        val gson = Gson() // Use Gson
                        val extractedDataJson = gson.toJson(parsedData)

                        onScanComplete(extractedDataJson) // Pass JSON string

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "OCR Complete!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    coroutineScope.launch {
                        Log.e("OCR", "Text recognition failed: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }
    }
}

// Add this new private function outside your ScanBusinessCardScreen composable
// (e.g., below it, or in a separate utility file if you prefer)
private fun parseBusinessCardText(extractedText: String): Map<String, String> {
    val parsedData = mutableMapOf<String, String>()


    var remainingText = extractedText.lines().toMutableList()



    // Heuristic for Name, Company, Title (very basic)
    // This part is highly dependent on card layout and will need refinement
    val cleanedLines = remainingText.filter { it.isNotBlank() && it.length > 2 }

    if (cleanedLines.isNotEmpty()) {
        // Simple heuristic: First non-empty line could be name/company
        parsedData["name"] = cleanedLines.getOrElse(0) { "" }

        if (cleanedLines.size > 1) {
            parsedData["company"] = cleanedLines.getOrElse(1) { "" }
        }
        if (cleanedLines.size > 2) {
            parsedData["title"] = cleanedLines.getOrElse(2) { "" }
        }
    }

    // Always include the raw text
    parsedData["extractedRawText"] = extractedText

    return parsedData
}