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
import androidx.compose.ui.draw.shadow // Import for shadow

import androidx.core.content.ContextCompat
import com.example.swiftcard.R
import com.google.gson.Gson // Make sure you've added the Gson dependency to build.gradle (app)
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

            // Dynamic Card Detection Overlay (modified)
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
 * Dynamic Card Detection Overlay with Visual Feedback (MODIFIED for ID Card appearance)
 */
@Composable
private fun CardDetectionOverlay(
    isCardDetected: Boolean, detectedCardBounds: android.graphics.Rect?
) {
    val placeholderColor = if (isCardDetected) Color.Green else MaterialTheme.colorScheme.primary
    val placeholderText = if (isCardDetected) {
        "Card detected! Tap to capture"
    } else {
        "Align your ECI card within the frame" // Changed instruction text for clarity
    }

    // Define a typical ID card width and calculate height based on a common aspect ratio (e.g., 1.58:1)
    // Adjust these values (cardWidth) if the visual guide isn't matching well on your device.
    val cardWidth = 320.dp
    val cardHeight = cardWidth / 1.58f // Standard ID card aspect ratio (approx 85.60mm / 53.98mm)
    val cornerRadius = 12.dp // More pronounced rounded corners for a card look

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        Box(
            modifier = Modifier
                .size(width = cardWidth, height = cardHeight) // Fixed card dimensions
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(cornerRadius)) // Stronger shadow
                .border(
                    width = if (isCardDetected) 3.dp else 1.5.dp, // Border thickness
                    color = placeholderColor,
                    shape = RoundedCornerShape(cornerRadius) // Match rounded corners
                )
                .background(
                    color = placeholderColor.copy(
                        alpha = if (isCardDetected) 0.15f else 0.08f // Subtle translucent background
                    ),
                    shape = RoundedCornerShape(cornerRadius) // Match rounded corners
                )
        )

        // Instructional text
        Text(
            text = placeholderText,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp) // Adjusted offset to be higher
                .background(
                    color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isCardDetected) Color.Green else Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * calculatePlaceholderSize (MODIFIED - Simplified for fixed aspect ratio)
 *
 * This function is now simplified as the overlay has a fixed size.
 * We'll use the detectedCardBounds to influence the 'isCardDetected' state,
 * but the visual frame will remain a consistent card size.
 */
private fun calculatePlaceholderSize(detectedCardBounds: android.graphics.Rect?): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
    // We are no longer dynamically sizing the overlay based on detected bounds.
    // The overlay is now a fixed visual guide for the user.
    // The 'detectedCardBounds' will still be used by the analyzer to set 'isCardDetected'.
    return Pair(0.dp, 0.dp) // Return dummy values, as they are no longer used by CardDetectionOverlay for size
}


/**
 * Processes an image for OCR using ML Kit Text Recognition.
 * Handles errors, empty results, and provides user feedback.
 */
private fun processImageForOcr(
    context: Context,
    imageUri: Uri,
    onScanComplete: (extractedDataJson: String) -> Unit, // Parameter type changed to String
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
                val extractedText = visionText.textBlocks.joinToString("\n") { it.text }

                // --- ADD THIS LOG STATEMENT ---
                Log.d("OCR_DEBUG", "Raw Extracted Text:\n$extractedText")
                // --- END LOG STATEMENT ---

                if (extractedText.isBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context, "No text found on the card.", Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                val parsedData = parseECICardText(extractedText)
                val gson = Gson()
                val extractedDataJson = gson.toJson(parsedData)

                // --- ADD THIS LOG STATEMENT ---
                Log.d("OCR_DEBUG", "Parsed Data JSON:\n$extractedDataJson")
                // --- END LOG STATEMENT ---

                onScanComplete(extractedDataJson)
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
 * Parses text specifically for ECI Voter ID card fields: Name, DOB, EPIC No., Gender, Relation Name.
 * This uses heuristic rules and may require refinement based on actual card layouts.
 */
private fun parseECICardText(extractedText: String): Map<String, String> {
    val parsedData = mutableMapOf<String, String>()
    val lines = extractedText.lines().map { it.trim() }.filter { it.isNotBlank() }

    val remainingLines = lines.toMutableList()

    // --- 1. Extract EPIC No. ---
    val epicNoPattern = Regex("([A-Z]{3}\\d{7}|[A-Z]{2,3}/\\d{2}/\\d{3}/\\d{6}|[A-Z0-9]{10,})")
    val epicNoKeywordPattern = Regex("(?i)(EPIC\\s*No\\.?|ID\\s*No\\.?|No\\.?)\\s*[:]?\\s*([A-Z0-9]{7,15})")

    for (line in lines) {
        val keywordMatch = epicNoKeywordPattern.find(line)
        if (keywordMatch != null) {
            parsedData["epicNumber"] = keywordMatch.groupValues[2].trim()
            remainingLines.remove(line)
            break
        }
        val directMatch = epicNoPattern.find(line)
        if (directMatch != null && parsedData["epicNumber"].isNullOrBlank() && !line.contains(Regex("\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}"))) {
            parsedData["epicNumber"] = directMatch.value.trim()
            remainingLines.remove(line)
            break
        }
    }

    // --- 2. Extract Date of Birth (DOB) ---
    val dobKeywordPattern = Regex("(?i)(Date\\s*of\\s*Birth\\s*/\\s*Age|जन्मतिथि\\s*/\\s*आयु|DOB)\\s*[:]?\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})")
    val directDatePattern = Regex("\\b\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}\\b")

    for (line in lines) {
        val keywordMatch = dobKeywordPattern.find(line)
        if (keywordMatch != null) {
            parsedData["dob"] = keywordMatch.groupValues[2].trim()
            remainingLines.remove(line)
            break
        }
        val directMatch = directDatePattern.find(line)
        if (directMatch != null && parsedData["dob"].isNullOrBlank()) {
            val matchedValue = directMatch.value.trim()
            if (matchedValue.contains("/") || matchedValue.contains("-") || matchedValue.length == 4) {
                parsedData["dob"] = matchedValue
                remainingLines.remove(line)
                break
            }
        }
    }

    // --- 3. Extract Gender ---
    val genderKeywordPattern = Regex("(?i)(लिंग\\s*/\\s*Gender)\\s*[:]?\\s*(पुरुष|महिला|Male|Female|M|F)")
    val directGenderPattern = Regex("(?i)\\b(पुरुष|महिला|Male|Female|M|F)\\b")

    for (line in lines) {
        val keywordMatch = genderKeywordPattern.find(line)
        if (keywordMatch != null) {
            parsedData["gender"] = keywordMatch.groupValues[2].trim()
            remainingLines.remove(line)
            break
        }
        val directMatch = directGenderPattern.find(line)
        if (directMatch != null && parsedData["gender"].isNullOrBlank()) {
            parsedData["gender"] = directMatch.value.trim()
            remainingLines.remove(line)
            break
        }
    }

    // --- 4. Extract Name and Relation Name ---
    val nameKeyword = Regex("(?i)(नाम|Name)\\s*[:]?\\s*(.*)") // Capture everything after the keyword and colon/space
    val relationKeyword = Regex("(?i)(पिता\\s*का\\s*नाम|Father's\\s*Name|पति\\s*का\\s*नाम|Husband's\\s*Name|माता\\s*का\\s*नाम|Mother's\\s*Name|S/o|D/o|W/o)\\s*[:]?\\s*(.*)") // Capture everything after

    for (line in lines) {
        val trimmedLine = line.trim()

        if (parsedData["name"].isNullOrBlank()) {
            val nameMatch = nameKeyword.find(trimmedLine)
            if (nameMatch != null) {
                val nameValue = nameMatch.groupValues[2].trim() // Capture the content from the second capturing group
                if (nameValue.isNotBlank() && nameValue.length > 2 && !nameValue.contains(Regex("\\d"))) {
                    parsedData["name"] = nameValue
                    continue // Move to next line
                }
            }
        }

        if (parsedData["relationName"].isNullOrBlank()) {
            val relationMatch = relationKeyword.find(trimmedLine)
            if (relationMatch != null) {
                val relationValue = relationMatch.groupValues[2].trim() // Capture the content from the second capturing group
                if (relationValue.isNotBlank() && relationValue.length > 2 && !relationValue.contains(Regex("\\d"))) {
                    parsedData["relationName"] = relationValue
                    continue // Move to next line
                }
            }
        }
    }

    // Fallback: If names are still missing, try to pick from remaining lines based on common order
    // (This part might need further refinement based on diverse OCR outputs)
    val tempRemainingLines = remainingLines.filter {
        // Only consider lines that haven't been assigned yet to any specific field
        !parsedData.containsValue(it)
    }.toMutableList()

    if (parsedData["name"].isNullOrBlank()) {
        val potentialName = tempRemainingLines.firstOrNull {
            // Heuristic for name: multiple words, reasonable length, no numbers
            it.split(" ").size > 1 && it.length > 5 && !it.contains(Regex("\\d"))
        }
        if (potentialName != null) {
            parsedData["name"] = potentialName
            tempRemainingLines.remove(potentialName)
        }
    }

    if (parsedData["relationName"].isNullOrBlank()) {
        val potentialRelationName = tempRemainingLines.firstOrNull {
            // Heuristic for relation name: multiple words, reasonable length, no numbers, and not the same as parsed name
            it.split(" ").size > 1 && it.length > 5 && !it.contains(Regex("\\d")) && parsedData["name"] != it
        }
        if (potentialRelationName != null) {
            parsedData["relationName"] = potentialRelationName
        }
    }

    parsedData["extractedRawText"] = extractedText

    return parsedData.filterValues { it.isNotEmpty() }
}


// These helper functions might not be as relevant for ECI cards, and can be removed or modified
// if not used elsewhere for other parsing logic. For ECI, the main parsing is in parseECICardText itself.
private fun isLikelyPersonName(line: String): Boolean {
    val words = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    return words.size in 2..4 && words.all { word ->
        word.first().isUpperCase() && word.all { it.isLetter() || it in "'-." }
    }
}

private fun isLikelyCompanyName(line: String, companyIndicators: List<String>): Boolean {
    // This is from business card context, unlikely for ECI card.
    return false // Or implement ECI specific checks for organization names if any
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

                            // Check for business card keywords (adapted for ID card potential text)
                            val text = block.text.lowercase()
                            if (text.contains("name") || text.contains("date of birth") || text.contains("epic no") || text.contains(
                                    "gender"
                                ) || text.contains("father's name") || text.matches(
                                    Regex("\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}") // Date pattern
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
        Handler(Looper.getMainLooper()).post { // Ensure Toast is on Main thread
            Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val executor = cameraExecutor ?: run {
        Handler(Looper.getMainLooper()).post { // Ensure Toast is on Main thread
            Toast.makeText(context, "Camera executor not available", Toast.LENGTH_SHORT).show()
        }
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