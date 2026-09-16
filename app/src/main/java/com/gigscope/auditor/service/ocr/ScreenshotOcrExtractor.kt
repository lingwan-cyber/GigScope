package com.gigscope.auditor.service.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import com.gigscope.auditor.domain.model.PhotoOfferRecord
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ScreenshotOcrExtractor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val filenameDateRegex = Regex("""Screenshot_(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})""", RegexOption.IGNORE_CASE)
    private val totalRegex = Regex("""(?i)(?:Estimated\s+total|Total)[:\s]*\$?([0-9]+\.[0-9]{2})""")
    private val baseRegex = Regex("""(?i)\bBase[:\s]*\$?([0-9]+\.[0-9]{2})""")
    private val tipBoostRegex = Regex("""(?i)\b(?:Tip|Boost)[:\s]*\$?([0-9]+\.[0-9]{2})""")
    private val stopsMilesRegex = Regex("""(?i)(\d+)\s+stops\s*[•·\-\s]*\s*([\d\.]+)\s+miles""")
    private val storeRegex = Regex("""(?i)(Walmart\s+[A-Za-z0-9\s#\-]+)""")
    private val timeLineRegex = Regex("""^\d{1,2}:\d{2}\s*(?:AM|PM)$""", RegexOption.IGNORE_CASE)

    /**
     * Scans local screenshot directory for screenshots taken within [startDate] and [endDate].
     */
    suspend fun findAndExtractScreenshots(
        context: Context,
        startDate: LocalDate,
        endDate: LocalDate,
        onProgress: ((current: Int, total: Int, currentOffer: PhotoOfferRecord?) -> Unit)? = null
    ): List<PhotoOfferRecord> = withContext(Dispatchers.IO) {
        val extractedRecords = mutableListOf<PhotoOfferRecord>()

        // 1. Locate screenshot directories
        val candidateDirs = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots"),
            File("/sdcard/Pictures/Screenshots"),
            File("/sdcard/DCIM/Screenshots")
        ).filter { it.exists() && it.isDirectory }

        val allFiles = candidateDirs.flatMap { dir ->
            dir.listFiles { file ->
                file.isFile && (file.extension.equals("png", ignoreCase = true) || file.extension.equals("jpg", ignoreCase = true))
            }?.toList() ?: emptyList()
        }.distinctBy { it.absolutePath }

        // 2. Filter files by date range from filename or file modification time
        val matchingFiles = allFiles.filter { file ->
            val fileDate = extractDateFromFile(file) ?: return@filter false
            !fileDate.isBefore(startDate) && !fileDate.isAfter(endDate)
        }.sortedByDescending { it.lastModified() }

        val total = matchingFiles.size
        for ((index, file) in matchingFiles.withIndex()) {
            try {
                val record = extractFromImageFile(file)
                if (record != null) {
                    extractedRecords.add(record)
                    onProgress?.invoke(index + 1, total, record)
                } else {
                    onProgress?.invoke(index + 1, total, null)
                }
            } catch (e: Exception) {
                onProgress?.invoke(index + 1, total, null)
            }
        }

        extractedRecords
    }

    /**
     * Extracts date from screenshot filename pattern "Screenshot_YYYYMMDD-HHMMSS...".
     */
    fun extractDateFromFile(file: File): LocalDate? {
        val match = filenameDateRegex.find(file.name)
        if (match != null) {
            val (year, month, day) = match.destructured
            return try {
                LocalDate.of(year.toInt(), month.toInt(), day.toInt())
            } catch (_: Exception) {
                null
            }
        }
        // Fallback to file modification date
        return try {
            val instant = java.time.Instant.ofEpochMilli(file.lastModified())
            instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Runs ML Kit OCR on a screenshot file and parses customer & trip details.
     */
    suspend fun extractFromImageFile(file: File): PhotoOfferRecord? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(inputImage).awaitTask()
        val text = visionText.text
        if (text.isBlank()) return null

        return parseOfferText(
            text = text,
            fileDate = extractDateFromFile(file) ?: LocalDate.now(),
            imageUri = file.toURI().toString(),
            fileName = file.name
        )
    }

    /**
     * Parses the raw OCR text of a Spark Driver offer screen.
     */
    fun parseOfferText(
        text: String,
        fileDate: LocalDate,
        imageUri: String? = null,
        fileName: String = ""
    ): PhotoOfferRecord {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Parse Estimated Total, Base Pay, Tip/Boost
        val total = totalRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val base = baseRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val tip = tipBoostRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        // Parse Stops & Miles
        val stopsMatch = stopsMilesRegex.find(text)
        val stopCount = stopsMatch?.groupValues?.get(1)?.toIntOrNull()
        val distanceMiles = stopsMatch?.groupValues?.get(2)?.toDoubleOrNull()

        // Parse Store Name
        val storeName = storeRegex.find(text)?.groupValues?.get(1)?.trim()

        // Parse Drop-off Customer & Address
        var customerName: String? = null
        var dropoffAddress: String? = null
        var deliveryTime: String? = null

        val dropoffIdx = lines.indexOfFirst {
            it.equals("Drop-off", ignoreCase = true) || it.startsWith("Drop-off", ignoreCase = true)
        }

        if (dropoffIdx != -1) {
            var curr = dropoffIdx + 1

            // Check if next line is delivery time (e.g. "7:31 AM" or "6:19 PM")
            if (curr < lines.size && timeLineRegex.matches(lines[curr])) {
                deliveryTime = lines[curr]
                curr++
            }

            // Next non-empty line is the Customer Name
            if (curr < lines.size) {
                val candidateName = lines[curr]
                // Make sure candidate line is not a button or system label
                if (!candidateName.equals("Apartment", ignoreCase = true) &&
                    !candidateName.equals("House", ignoreCase = true) &&
                    !candidateName.contains("verification", ignoreCase = true)
                ) {
                    customerName = candidateName
                    curr++
                }
            }

            // Next line is the Street Address
            if (curr < lines.size) {
                val street = lines[curr]
                curr++
                // If the line after that is City, State Zip, append it
                val cityZip = if (curr < lines.size && (lines[curr].contains(Regex("""[A-Z]{2}\s+\d{5}""")) || lines[curr].contains(","))) {
                    lines[curr]
                } else null

                dropoffAddress = if (cityZip != null) "$street, $cityZip" else street
            }
        }

        // Generate or parse Trip ID
        val tripIdMatch = Regex("""(?:Trip|Offer)\s*#?\s*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE).find(text)
        val tripId = tripIdMatch?.groupValues?.get(1) ?: run {
            // Fallback to timestamp extracted from filename
            val match = filenameDateRegex.find(fileName)
            if (match != null) {
                "SPARK-${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}-${match.groupValues[4]}${match.groupValues[5]}${match.groupValues[6]}"
            } else {
                "SPARK-OFFER-${System.currentTimeMillis().toString().takeLast(6)}"
            }
        }

        return PhotoOfferRecord(
            tripId = tripId,
            offeredTip = tip,
            basePay = base,
            captureDate = fileDate,
            timestamp = deliveryTime,
            imageUri = imageUri,
            isAccepted = text.contains("ACCEPT", ignoreCase = true),
            extractedText = text.take(1500),
            estimatedTotal = total ?: (base + tip),
            customerName = customerName,
            dropoffAddress = dropoffAddress,
            storeName = storeName,
            stopCount = stopCount,
            distanceMiles = distanceMiles
        )
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
