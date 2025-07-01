package com.example.swiftcard.ui.ScanBusinessCard


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView


import androidx.core.content.ContextCompat
import com.example.swiftcard.R
import com.google.gson.Gson
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

/**
 * Business Card Scanner Screen with Dynamic Placeholder
 *
 * Features:
 * - Real-time card detection using ML Kit
 * - Dynamic placeholder that adjusts to card size
 * - Smart data extraction (name, email, phone, company, etc.)
 * - Visual feedback for card detection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBusinessCardScreen(
    onScanComplete: (extractedDataJson: String) -> Unit,
    onBack: () -> Unit,
) {
    // Context and lifecycle
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Camera states
    val cameraExecutor: MutableState<ExecutorService?> = remember { mutableStateOf(null) }
    val imageCapture: MutableState<ImageCapture?> = remember { mutableStateOf(null) }

    // Card detection states
    var detectedCardBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var isCardDetected by remember { mutableStateOf(false) }

    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    // Gallery image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(), onResult = { uri: Uri? ->
            uri?.let {
                processImageForOcr(
                    context = context,
                    imageUri = it,
                    onScanComplete = onScanComplete,
                    coroutineScope = scope
                )
            }
        })

    // Request camera permission on first composition
    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }

            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Manage camera executor lifecycle
    DisposableEffect(Unit) {
        cameraExecutor.value = Executors.newSingleThreadExecutor()
        onDispose {
            cameraExecutor.value?.shutdown()
        }
    }

    // Main UI
    Scaffold(
        topBar = {
        TopAppBar(title = { Text("Scan Business Card") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack, contentDescription = "Back"
                )
            }
        })
    }, floatingActionButton = {
        FloatingActionButtons(onCapturePhoto = {
            capturePhoto(
                imageCapture = imageCapture.value,
                context = context,
                cameraExecutor = cameraExecutor.value,
                onScanComplete = onScanComplete,
                scope = scope
            )
        }, onPickFromGallery = { imagePickerLauncher.launch("image/*") })
    }, floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize()) {
            // Camera Preview with Real-time Detection
            CameraPreview(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                context = context,
                lifecycleOwner = lifecycleOwner,
                cameraExecutor = cameraExecutor,
                imageCapture = imageCapture,
                onCardDetected = { bounds, detected ->
                    detectedCardBounds = bounds
                    isCardDetected = detected
                })

            // Dynamic Card Detection Overlay
            CardDetectionOverlay(
                isCardDetected = isCardDetected, detectedCardBounds = detectedCardBounds
            )
        }
    }
}

/**
 * Floating Action Buttons for photo capture and gallery selection
 */
@Composable
private fun FloatingActionButtons(
    onCapturePhoto: () -> Unit, onPickFromGallery: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Capture Photo Button
        FloatingActionButton(onClick = onCapturePhoto) {
            Icon(
                painter = painterResource(R.drawable.img),
                contentDescription = "Take Photo",
                modifier = Modifier.size(24.dp)
            )
        }

        // Pick from Gallery Button
        FloatingActionButton(onClick = onPickFromGallery) {
            Icon(
                painter = painterResource(R.drawable.img_1),
                contentDescription = "Pick from Gallery",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Camera Preview with Real-time Card Detection
 */
@Composable
private fun CameraPreview(
    modifier: Modifier,
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraExecutor: MutableState<ExecutorService?>,
    imageCapture: MutableState<ImageCapture?>,
    onCardDetected: (bounds: android.graphics.Rect?, isDetected: Boolean) -> Unit
) {
    AndroidView(factory = { ctx ->
        PreviewView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }, modifier = modifier, update = { previewView ->
        setupCamera(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            cameraExecutor = cameraExecutor,
            imageCapture = imageCapture,
            onCardDetected = onCardDetected
        )
    })
}

/**
 * Dynamic Card Detection Overlay with Visual Feedback
 */
@Composable
private fun CardDetectionOverlay(
    isCardDetected: Boolean, detectedCardBounds: android.graphics.Rect?
) {
    // Dynamic UI properties based on detection state
    val placeholderColor = if (isCardDetected) Color.Green else MaterialTheme.colorScheme.primary
    val placeholderText = if (isCardDetected) {
        "Card detected! Tap to capture"
    } else {
        "Place business card within the frame"
    }

    // Calculate dynamic size based on detected bounds
    val (placeholderWidth, placeholderHeight) = calculatePlaceholderSize(detectedCardBounds)

    // Main placeholder rectangle
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        Box(
            modifier = Modifier
                .size(width = placeholderWidth, height = placeholderHeight)
                .border(
                    width = if (isCardDetected) 3.dp else 2.dp,
                    color = placeholderColor,
                    shape = MaterialTheme.shapes.medium
                )
                .background(
                    color = placeholderColor.copy(
                        alpha = if (isCardDetected) 0.15f else 0.08f
                    )
                )
        )

        // Instructional text
        Text(
            text = placeholderText,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-50).dp)
                .background(
                    color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isCardDetected) Color.Green else Color.White,
            style = MaterialTheme.typography.bodyMedium
        )


    }
}


// Function to process image for OCR using ML Kit Text Recognition
/**
 * Processes an image for OCR using ML Kit Text Recognition.
 * Handles errors, empty results, and provides user feedback.
 */
private fun processImageForOcr(
    context: Context,
    imageUri: Uri,
    onScanComplete: (extractedData: String) -> Unit,
    coroutineScope: CoroutineScope
) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    coroutineScope.launch {
        // Try to decode the image from the provided URI
        val bitmap = try {
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Use ImageDecoder for newer Android versions
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(
                            context.contentResolver, imageUri
                        )
                    )
                } else {
                    // Use MediaStore for older Android versions
                    MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                }
            }
        } catch (e: Exception) {
            Log.e("OCR", "Failed to load bitmap: ${e.message}", e)
            withContext(Dispatchers.Main) {
                // Show error to user if image loading fails
                Toast.makeText(context, "Failed to load image for OCR", Toast.LENGTH_SHORT).show()
            }
            null
        }

        // If bitmap is null, exit early
        if (bitmap == null) return@launch

        // Create ML Kit InputImage from bitmap
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image).addOnSuccessListener { visionText ->
            coroutineScope.launch {
                // Join all recognized text blocks into a single string
                val extractedText = visionText.textBlocks.joinToString("\n") { it.text }
                if (extractedText.isBlank()) {
                    // Notify user if no text was found
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context, "No text found on the card.", Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                // Parse the extracted text for business card fields
                val parsedData = parseBusinessCardText(extractedText)
                // Convert parsed data to JSON
                val gson = Gson()
                val extractedDataJson = gson.toJson(parsedData)
                // Pass the result to the callback
                onScanComplete(extractedDataJson)
                // Notify user of completion
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "OCR Complete!", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener { e ->
            coroutineScope.launch {
                // Log and show error if OCR fails
                Log.e("OCR", "Text recognition failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/**
 * Simplified business card text parsing focused on essential fields
 * Extracts only: Name, Company, and Job Title (matching AddEditScreen fields)
 */
private fun parseBusinessCardText(extractedText: String): Map<String, String> {
    val parsedData = mutableMapOf<String, String>()
    val lines = extractedText.lines().filter { it.isNotBlank() && it.trim().length > 1 }

    // Clean lines by removing lines with emails, phones, websites, and addresses
    val cleanedLines = lines.filter { line ->
        val trimmedLine = line.trim()

        // Skip lines with email patterns
        !trimmedLine.contains("@") &&

                // Skip lines with phone number patterns
                !trimmedLine.matches(Regex(".*\\+?\\d[\\d\\s\\-\\(\\)\\.]{7,}.*")) &&

                // Skip lines with website patterns
                !trimmedLine.contains(
                    Regex(
                        "www\\.|http|\\.[a-z]{2,4}(/|\\s|\$)", RegexOption.IGNORE_CASE
                    )
                ) &&

                // Skip lines with address indicators
                !trimmedLine.contains(
                    Regex(
                        "street|avenue|road|drive|lane|blvd|st\\b|ave\\b|rd\\b",
                        RegexOption.IGNORE_CASE
                    )
                ) &&

                // Skip lines that are mostly numbers or special characters
                !trimmedLine.matches(Regex("^[\\d\\s\\-\\+\\(\\)\\.#]+\$")) &&

                // Keep lines with reasonable length (2-50 characters)
                trimmedLine.length in 2..50
    }

    // Job title indicators for better detection
    val jobTitleIndicators = listOf(
        "CEO",
        "CTO",
        "CFO",
        "COO",
        "CIO",
        "CMO",
        "President",
        "Vice President",
        "VP",
        "Director",
        "Manager",
        "Assistant Manager",
        "Senior",
        "Junior",
        "Lead",
        "Head",
        "Supervisor",
        "Coordinator",
        "Specialist",
        "Analyst",
        "Consultant",
        "Engineer",
        "Developer",
        "Designer",
        "Architect",
        "Executive",
        "Officer",
        "Administrator"
    )

    // Company name indicators
    val companyIndicators = listOf(
        "Inc",
        "LLC",
        "Corp",
        "Corporation",
        "Ltd",
        "Limited",
        "Company",
        "Co",
        "Group",
        "Solutions",
        "Technologies",
        "Services",
        "Consulting",
        "Systems",
        "Enterprise",
        "Global",
        "International",
        "Associates",
        "Partners"
    )

    var nameFound = false
    var companyFound = false
    var titleFound = false

    // First pass: Look for job titles (they're usually easier to identify)
    for (line in cleanedLines) {
        val trimmedLine = line.trim()

        if (!titleFound && jobTitleIndicators.any { indicator ->
                trimmedLine.contains(indicator, ignoreCase = true)
            }) {
            // Use "title" to match AddEditScreen field name
            parsedData["title"] = trimmedLine
            titleFound = true
        }
    }

    // Second pass: Look for company names
    for (line in cleanedLines) {
        val trimmedLine = line.trim()

        // Skip if this line is already identified as job title
        if (trimmedLine == parsedData["title"]) continue

        if (!companyFound && isLikelyCompanyName(trimmedLine, companyIndicators)) {
            // Use "company" to match AddEditScreen field name
            parsedData["company"] = trimmedLine
            companyFound = true
        }
    }

    // Third pass: Look for person names
    for (line in cleanedLines) {
        val trimmedLine = line.trim()

        // Skip if this line is already identified as job title or company
        if (trimmedLine == parsedData["title"] || trimmedLine == parsedData["company"]) continue

        if (!nameFound && isLikelyPersonName(trimmedLine)) {
            // Use "name" to match AddEditScreen field name
            parsedData["name"] = trimmedLine
            nameFound = true
        }
    }

    // Fallback logic: Use position-based detection if some fields are still missing
    val remainingLines = cleanedLines.filter { line ->
        val trimmedLine = line.trim()
        trimmedLine != parsedData["title"] && trimmedLine != parsedData["company"] && trimmedLine != parsedData["name"]
    }

    // If no name found, use the first remaining line that looks like a name
    if (!nameFound && remainingLines.isNotEmpty()) {
        for (line in remainingLines) {
            if (isLikelyPersonName(line.trim())) {
                parsedData["name"] = line.trim()
                nameFound = true
                break
            }
        }

        // If still no name found, use first line as fallback
        if (!nameFound && cleanedLines.isNotEmpty()) {
            parsedData["name"] = cleanedLines[0].trim()
        }
    }

    // If no company found, look for any line that could be a company
    if (!companyFound && remainingLines.isNotEmpty()) {
        for (line in remainingLines) {
            val trimmedLine = line.trim()
            if (trimmedLine != parsedData["name"] && (companyIndicators.any {
                    trimmedLine.contains(
                        it, ignoreCase = true
                    )
                } || trimmedLine.split("\\s+".toRegex()).size <= 4)) {
                parsedData["company"] = trimmedLine
                break
            }
        }
    }

    // Return only non-empty values with correct field names for AddEditScreen
    return parsedData.filterValues { it.isNotEmpty() }
}

/**
 * Enhanced helper function to identify person names
 */
private fun isLikelyPersonName(line: String): Boolean {
    val words = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }

    return when {
        // Names typically have 2-4 words
        words.size !in 2..4 -> false

        // Each word should start with uppercase and contain mostly letters
        words.all { word ->
            word.length >= 2 && word.first()
                .isUpperCase() && word.count { it.isLetter() } >= word.length * 0.7 && // At least 70% letters
                    word.all { it.isLetter() || it in "'-." }
        } -> true

        else -> false
    }
}

/**
 * Enhanced helper function to identify company names
 */
private fun isLikelyCompanyName(line: String, companyIndicators: List<String>): Boolean {
    return when {
        // Too long for typical company name
        line.length > 40 -> false

        // Contains company indicators
        companyIndicators.any { line.contains(it, ignoreCase = true) } -> true

        // Reasonable word count for company names
        line.split("\\s+".toRegex()).size in 1..6 -> {
            // Has mixed case or all caps (typical for company names)
            line.any { it.isUpperCase() } && line.any { it.isLetter() } &&
                    // Not all lowercase (less likely to be a company name)
                    !line.all { it.isLowerCase() || !it.isLetter() }
        }

        else -> false
    }
}

/**
 * CardDetectionAnalyzer: Real-time card detection using ML Kit Text Recognition
 * Detects business card boundaries and calculates aspect ratio dynamically
 */
@OptIn(ExperimentalGetImage::class)
private class CardDetectionAnalyzer(
    private val onCardDetected: (bounds: android.graphics.Rect?, isDetected: Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            textRecognizer.process(image).addOnSuccessListener { visionText ->
                // Check if we have text blocks that could represent a business card
                if (visionText.textBlocks.isNotEmpty()) {
                    // Calculate bounding box that encompasses all text blocks
                    var minX = Int.MAX_VALUE
                    var minY = Int.MAX_VALUE
                    var maxX = Int.MIN_VALUE
                    var maxY = Int.MIN_VALUE

                    var hasBusinessCardKeywords = false

                    for (block in visionText.textBlocks) {
                        val boundingBox = block.boundingBox
                        if (boundingBox != null) {
                            minX = minOf(minX, boundingBox.left)
                            minY = minOf(minY, boundingBox.top)
                            maxX = maxOf(maxX, boundingBox.right)
                            maxY = maxOf(maxY, boundingBox.bottom)

                            // Check for business card keywords
                            val text = block.text.lowercase()
                            if (text.contains("@") || text.contains("phone") || text.contains("email") || text.contains(
                                    "www"
                                ) || text.contains(".com") || text.contains("tel") || text.matches(
                                    Regex(".*\\d{3}[-.]?\\d{3}[-.]?\\d{4}.*")
                                )
                            ) {
                                hasBusinessCardKeywords = true
                            }
                        }
                    }

                    // If we found text with business card characteristics
                    if (hasBusinessCardKeywords && minX != Int.MAX_VALUE) {
                        val detectedBounds = android.graphics.Rect(minX, minY, maxX, maxY)
                        onCardDetected(detectedBounds, true)
                    } else {
                        onCardDetected(null, false)
                    }
                } else {
                    onCardDetected(null, false)
                }
            }.addOnFailureListener {
                onCardDetected(null, false)
            }.addOnCompleteListener {
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }
}

/**
 * Calculate placeholder size based on detected card bounds
 */
private fun calculatePlaceholderSize(detectedCardBounds: android.graphics.Rect?): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
    return if (detectedCardBounds != null) {
        val bounds = detectedCardBounds
        val detectedWidth = (bounds.width() * 0.8f).coerceAtLeast(200f).coerceAtMost(350f)
        val detectedHeight = (bounds.height() * 0.8f).coerceAtLeast(120f).coerceAtMost(200f)
        Pair(detectedWidth.dp, detectedHeight.dp)
    } else {
        Pair(280.dp, 160.dp) // Default business card aspect ratio
    }
}

/**
 * Setup camera with preview, image capture, and real-time analysis
 */
private fun setupCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    cameraExecutor: MutableState<ExecutorService?>,
    imageCapture: MutableState<ImageCapture?>,
    onCardDetected: (bounds: android.graphics.Rect?, isDetected: Boolean) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview configuration
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // Image capture configuration
            imageCapture.value =
                ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

            // Real-time image analysis for card detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                .also { analyzer ->
                    cameraExecutor.value?.let { executor ->
                        analyzer.setAnalyzer(
                            executor, CardDetectionAnalyzer(onCardDetected)
                        )
                    }
                }

            // Camera selector (back camera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Bind use cases to lifecycle
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageCapture.value, imageAnalyzer
            )

        } catch (exc: Exception) {
            Log.e("CameraSetup", "Camera binding failed", exc)
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Capture photo and process it for OCR
 */
private fun capturePhoto(
    imageCapture: ImageCapture?,
    context: Context,
    cameraExecutor: ExecutorService?,
    onScanComplete: (extractedDataJson: String) -> Unit,
    scope: CoroutineScope
) {
    val capture = imageCapture ?: run {
        Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
        return
    }

    val executor = cameraExecutor ?: run {
        Toast.makeText(context, "Camera executor not available", Toast.LENGTH_SHORT).show()
        return
    }

    // Create temporary file for captured image
    val photoFile = File(
        context.externalCacheDir, "business_card_${System.currentTimeMillis()}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    // Capture image
    capture.takePicture(
        outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e("PhotoCapture", "Photo capture failed: ${exception.message}", exception)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context, "Photo capture failed: ${exception.message}", Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Photo captured!", Toast.LENGTH_SHORT).show()
                }

                // Process captured image for OCR
                processImageForOcr(
                    context = context,
                    imageUri = savedUri,
                    onScanComplete = onScanComplete,
                    coroutineScope = scope
                )
            }
        })
}
