package org.hogel.tidytalk.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Which inline preview, if any, to render for a file. */
enum class PreviewKind { Image, Pdf, None }

fun previewKindFor(file: File): PreviewKind = when (file.extension.lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> PreviewKind.Image
    "pdf" -> PreviewKind.Pdf
    else -> PreviewKind.None
}

/** Hand the file off to the system's default viewer via FileProvider + ACTION_VIEW. */
fun openFileExternally(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val mime = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase())
        ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "開けるアプリがありません", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PreviewThumbnail(
    file: File,
    kind: PreviewKind,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (kind == PreviewKind.None) return
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            PreviewKind.Image -> AsyncImage(
                model = file,
                contentDescription = "画像プレビュー（タップで開く）",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            PreviewKind.Pdf -> PdfThumbnail(file)
            PreviewKind.None -> Unit
        }
    }
}

@Composable
private fun PdfThumbnail(file: File) {
    var bmp by remember(file.path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.path) {
        bmp = runCatching { renderPdfFirstPage(file, widthPx = 240) }.getOrNull()
    }
    val current = bmp
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = "PDF プレビュー（タップで開く）",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Text(
            "PDF",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private suspend fun renderPdfFirstPage(file: File, widthPx: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return@withContext null
                renderer.openPage(0).use { page ->
                    val aspect = page.height.toFloat() / page.width
                    val h = (widthPx * aspect).toInt().coerceAtLeast(1)
                    val out = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    out.eraseColor(Color.WHITE)
                    page.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out
                }
            }
        }
    }
