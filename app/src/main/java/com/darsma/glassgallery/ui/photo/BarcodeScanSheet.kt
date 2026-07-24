package com.darsma.glassgallery.ui.photo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.glassSheen
import com.darsma.glassgallery.ui.components.liquidGlass
import com.darsma.glassgallery.ui.components.liquidGlassBorder
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAX_SCAN_DIMENSION = 2048
private val ScanSheetShape = RoundedCornerShape(30.dp)
private val ScanResultShape = RoundedCornerShape(20.dp)
private val DirectExecutor = Executor { command -> command.run() }

internal suspend fun scanPhotoBarcodes(context: Context, photoUri: Uri): List<Barcode> =
    withContext(Dispatchers.IO) {
        val decodedPhoto = loadScanBitmap(context, photoUri)
            ?: error("Unable to decode photo")
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        try {
            val image = InputImage.fromBitmap(
                decodedPhoto.bitmap,
                decodedPhoto.rotationDegrees,
            )
            scanner.process(image).awaitResult()
        } finally {
            scanner.close()
        }
    }

private data class ScanBitmap(
    val bitmap: Bitmap,
    val rotationDegrees: Int,
)

private fun loadScanBitmap(context: Context, uri: Uri): ScanBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
    while (largestDimension / sampleSize > MAX_SCAN_DIMENSION) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    val rotationDegrees = readPhotoRotation(context, uri)
    return ScanBitmap(bitmap, rotationDegrees)
}

private fun readPhotoRotation(context: Context, uri: Uri): Int =
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.ImageColumns.ORIENTATION),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } ?: 0
    }.getOrDefault(0)
        .takeIf { it == 0 || it == 90 || it == 180 || it == 270 }
        ?: 0

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener(DirectExecutor) { completed ->
            if (!continuation.isActive) return@addOnCompleteListener
            when {
                completed.isSuccessful -> continuation.resume(completed.result)
                completed.isCanceled -> continuation.cancel()
                else -> continuation.resumeWithException(
                    completed.exception ?: IllegalStateException("ML Kit task failed"),
                )
            }
        }
    }

@Composable
internal fun BarcodeResultsSheet(
    visible: Boolean,
    barcodes: List<Barcode>,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = Motion.expressive(),
            ) + fadeIn(Motion.standard()),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = Motion.standard(),
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .clip(ScanSheetShape)
                    .liquidGlass(alpha = 0.90f)
                    .glassSheen()
                    .liquidGlassBorder(ScanSheetShape)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Scanned codes",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "${barcodes.size} found",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                    BouncyIconButton(onClick = onDismiss, size = 40.dp) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close scan results",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(barcodes) { index, barcode ->
                        BarcodeResultCard(
                            index = index,
                            barcode = barcode,
                            onCopy = { copyText(context, it) },
                            onOpen = { openUrl(context, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarcodeResultCard(
    index: Int,
    barcode: Barcode,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val presentation = barcodePresentation(barcode)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ScanResultShape)
            .liquidGlass(alpha = 0.46f)
            .liquidGlassBorder(ScanResultShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = "${index + 1}. ${presentation.typeLabel}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(6.dp))
        presentation.fields.forEach { (label, value) ->
            ResultField(label, value)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            presentation.openUrl?.let { url ->
                ResultAction(
                    label = "Open",
                    icon = Icons.Rounded.OpenInNew,
                    onClick = { onOpen(url) },
                )
                Spacer(Modifier.size(8.dp))
            }
            ResultAction(
                label = "Copy",
                icon = Icons.Rounded.ContentCopy,
                onClick = { onCopy(presentation.copyValue) },
            )
        }
    }
}

@Composable
private fun ResultField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.50f),
            modifier = Modifier.fillMaxWidth(0.27f),
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}

@Composable
private fun ResultAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

private data class BarcodePresentation(
    val typeLabel: String,
    val fields: List<Pair<String, String>>,
    val copyValue: String,
    val openUrl: String? = null,
)

private fun barcodePresentation(barcode: Barcode): BarcodePresentation {
    val rawValue = barcode.rawValue
        ?: barcode.displayValue
        ?: "Unknown code"

    return when (barcode.valueType) {
        Barcode.TYPE_URL -> {
            val url = barcode.url
            val parsedUrl = url?.url?.takeIf { it.isNotBlank() }
            BarcodePresentation(
                typeLabel = "Link",
                fields = buildList {
                    url?.title?.takeIf { it.isNotBlank() }?.let { add("Title" to it) }
                    add("URL" to (parsedUrl ?: rawValue))
                },
                copyValue = parsedUrl ?: rawValue,
                openUrl = parsedUrl,
            )
        }

        Barcode.TYPE_WIFI -> {
            val wifi = barcode.wifi
            BarcodePresentation(
                typeLabel = "Wi-Fi",
                fields = buildList {
                    add("Network" to (wifi?.ssid?.takeIf { it.isNotBlank() } ?: "Hidden"))
                    add("Security" to wifiSecurityLabel(wifi?.encryptionType))
                    wifi?.password?.takeIf { it.isNotBlank() }?.let { add("Password" to it) }
                },
                copyValue = rawValue,
            )
        }

        Barcode.TYPE_CONTACT_INFO -> {
            val contact = barcode.contactInfo
            BarcodePresentation(
                typeLabel = "Contact",
                fields = buildList {
                    contact?.name?.formattedName?.takeIf { it.isNotBlank() }
                        ?.let { add("Name" to it) }
                    contact?.organization?.takeIf { it.isNotBlank() }
                        ?.let { add("Company" to it) }
                    contact?.title?.takeIf { it.isNotBlank() }
                        ?.let { add("Title" to it) }
                    contact?.phones
                        ?.mapNotNull { it.number?.takeIf(String::isNotBlank) }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { add("Phone" to it.joinToString("\n")) }
                    contact?.emails
                        ?.mapNotNull { it.address?.takeIf(String::isNotBlank) }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { add("Email" to it.joinToString("\n")) }
                    contact?.addresses
                        ?.flatMap { it.addressLines.toList() }
                        ?.filter(String::isNotBlank)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { add("Address" to it.joinToString("\n")) }
                    contact?.urls
                        ?.filter(String::isNotBlank)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { add("Website" to it.joinToString("\n")) }
                    if (isEmpty()) add("Contact" to rawValue)
                },
                copyValue = rawValue,
            )
        }

        Barcode.TYPE_GEO -> {
            val point = barcode.geoPoint
            BarcodePresentation(
                typeLabel = "Location",
                fields = if (point != null) {
                    listOf(
                        "Latitude" to point.lat.toString(),
                        "Longitude" to point.lng.toString(),
                    )
                } else {
                    listOf("Location" to rawValue)
                },
                copyValue = rawValue,
            )
        }

        else -> BarcodePresentation(
            typeLabel = if (barcode.valueType == Barcode.TYPE_TEXT) "Text" else "Code",
            fields = listOf("Value" to rawValue),
            copyValue = rawValue,
        )
    }
}

private fun wifiSecurityLabel(encryptionType: Int?): String =
    when (encryptionType) {
        Barcode.WiFi.TYPE_OPEN -> "Open"
        Barcode.WiFi.TYPE_WEP -> "WEP"
        Barcode.WiFi.TYPE_WPA -> "WPA/WPA2"
        else -> "Unknown"
    }

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Scanned code", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
}
