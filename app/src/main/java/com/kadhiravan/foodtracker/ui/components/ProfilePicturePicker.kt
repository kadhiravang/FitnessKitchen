package com.kadhiravan.foodtracker.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

/** A square region of the *original* profile picture file to display — normalized (0..1)
 * so it survives independent of whatever resolution the image happens to be decoded at.
 * Never bakes a new file: the original is untouched, this is applied at display time. */
data class ProfilePicCrop(val left: Float, val top: Float, val size: Float)

/** Centered circular avatar, backed by a single file in app-private storage — shared by
 * onboarding and the You tab so both stay in sync. Tapping it opens a full-screen viewer
 * with Edit/Remove actions instead of exposing camera/gallery/remove as buttons up front.
 * Cropping never replaces [picturePath] — it only changes [cropRect], the region of the
 * original file to show, so re-cropping later always has the full original to work from. */
@Composable
fun ProfilePicturePicker(
    picturePath: String,
    cropRect: ProfilePicCrop?,
    onPictureChanged: (String) -> Unit,
    onCropChanged: (ProfilePicCrop?) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    // Standalone usage (onboarding) wants to self-center in whatever width it's given;
    // placed beside other content (e.g. the You tab's name field) it should just take its
    // own size instead of claiming the full row width.
    centerInParent: Boolean = true
) {
    val context = LocalContext.current
    var showViewer by remember { mutableStateOf(false) }
    var showSourceChooser by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }

    fun newProfilePicFile(): File {
        val dir = File(context.filesDir, "profile_pics").apply { mkdirs() }
        return File(dir, "profile_${System.currentTimeMillis()}.jpg")
    }

    /** Only for an actually new photo (camera/gallery) — replaces the file on disk and
     * clears any crop, since a crop region from the old photo means nothing on a new one. */
    fun replacePicture(newFile: File) {
        val old = picturePath.takeIf { it.isNotBlank() }?.let { File(it) }
        onPictureChanged(newFile.absolutePath)
        onCropChanged(null)
        old?.delete()
    }

    var pendingFile by remember { mutableStateOf<File?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingFile?.let { replacePicture(it) }
        pendingFile = null
    }
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = newProfilePicFile()
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            replacePicture(file)
        }
    }

    fun launchCamera() {
        val file = newProfilePicFile()
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    fun launchGallery() {
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Box(
        modifier = if (centerInParent) modifier.fillMaxWidth() else modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    if (picturePath.isNotBlank()) showViewer = true else showSourceChooser = true
                },
            contentAlignment = Alignment.Center
        ) {
            if (picturePath.isNotBlank()) {
                CroppedProfileImage(
                    picturePath = picturePath,
                    cropRect = cropRect,
                    contentDescription = "Profile picture",
                    modifier = Modifier.size(size).clip(CircleShape)
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Add a profile picture",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }

    if (showViewer && picturePath.isNotBlank()) {
        ProfilePictureViewerDialog(
            picturePath = picturePath,
            cropRect = cropRect,
            onDismiss = { showViewer = false },
            onEdit = {
                showViewer = false
                showSourceChooser = true
            },
            onRemove = {
                File(picturePath).delete()
                onPictureChanged("")
                onCropChanged(null)
                showViewer = false
            }
        )
    }

    if (showSourceChooser) {
        PictureSourceChooserDialog(
            hasExistingPhoto = picturePath.isNotBlank(),
            onDismiss = { showSourceChooser = false },
            onCrop = {
                showSourceChooser = false
                showCropDialog = true
            },
            onCamera = {
                showSourceChooser = false
                launchCamera()
            },
            onGallery = {
                showSourceChooser = false
                launchGallery()
            }
        )
    }

    if (showCropDialog && picturePath.isNotBlank()) {
        ProfilePictureCropDialog(
            picturePath = picturePath,
            initialCrop = cropRect,
            onDismiss = { showCropDialog = false },
            onCropped = { crop ->
                onCropChanged(crop)
                showCropDialog = false
            }
        )
    }
}

/** Decodes [picturePath] and, if [cropRect] is set, cuts out just that square region —
 * otherwise renders the whole file with the given [contentScale] (default center-crop).
 * Nothing is ever written back to disk here; this runs fresh (cached per path+crop) every
 * time the picture is displayed. */
@Composable
private fun CroppedProfileImage(
    picturePath: String,
    cropRect: ProfilePicCrop?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (cropRect == null) {
        AsyncImage(
            model = File(picturePath),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
        return
    }
    val cropped: ImageBitmap? = remember(picturePath, cropRect) {
        runCatching {
            val decoded = decodeSampledBitmap(picturePath, 800) ?: return@runCatching null
            val bw = decoded.width
            val bh = decoded.height
            val shortSide = minOf(bw, bh)
            val cropSize = (cropRect.size * shortSide).roundToInt().coerceIn(1, shortSide)
            val left = (cropRect.left * bw).roundToInt().coerceIn(0, bw - cropSize)
            val top = (cropRect.top * bh).roundToInt().coerceIn(0, bh - cropSize)
            Bitmap.createBitmap(decoded, left, top, cropSize, cropSize).asImageBitmap()
        }.getOrNull()
    }
    if (cropped != null) {
        Image(
            bitmap = cropped,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        AsyncImage(
            model = File(picturePath),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

/** Full-screen preview of the current photo with Edit/Remove actions — reached by tapping
 * the avatar, rather than exposing those actions as buttons next to it all the time. */
@Composable
private fun ProfilePictureViewerDialog(
    picturePath: String,
    cropRect: ProfilePicCrop?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            CroppedProfileImage(
                picturePath = picturePath,
                cropRect = cropRect,
                contentDescription = "Profile picture",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(32.dp)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DialogActionButton(
                    icon = Icons.Default.Edit,
                    label = "Edit",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                )
                DialogActionButton(
                    icon = Icons.Default.Delete,
                    label = "Remove",
                    onClick = onRemove,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** Crop/camera/gallery choice, reached from either the empty avatar or the viewer's Edit
 * action. Crop only applies to a photo that's already set, so it's hidden until one exists. */
@Composable
private fun PictureSourceChooserDialog(
    hasExistingPhoto: Boolean,
    onDismiss: () -> Unit,
    onCrop: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    "Update profile picture",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                if (hasExistingPhoto) {
                    SourceOptionRow(icon = Icons.Default.Crop, label = "Crop to fit", onClick = onCrop)
                }
                SourceOptionRow(icon = Icons.Default.CameraAlt, label = "Take photo", onClick = onCamera)
                SourceOptionRow(icon = Icons.Default.PhotoLibrary, label = "Choose from gallery", onClick = onGallery)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun SourceOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp))
    }
}

/** Downsamples before decoding so a full-resolution camera capture doesn't risk an OOM —
 * an avatar never needs more than this many pixels on a side even at high DPI. */
private fun decodeSampledBitmap(path: String, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)
}

/** A fixed circular frame stays put in the center — exactly the same circle the avatar itself
 * is clipped to — while the photo is what moves: drag to slide it around underneath, pinch to
 * zoom it in. This mirrors the classic profile-photo picker pattern (Instagram, WhatsApp) where
 * the frame never moves and the photo does, which reads as more natural than a resizable crop
 * rectangle for a shape that's always going to be this one circle.
 *
 * Critically, "Done" never writes a new file — [onCropped] only reports which normalized
 * region of the *original* [picturePath] to remember, so re-cropping later (or just changing
 * your mind) always starts from the untouched original, never a lossy re-crop of a re-crop. */
@Composable
private fun ProfilePictureCropDialog(
    picturePath: String,
    initialCrop: ProfilePicCrop?,
    onDismiss: () -> Unit,
    onCropped: (ProfilePicCrop) -> Unit
) {
    val density = LocalDensity.current
    val viewportDp = 280.dp
    val viewportPx = with(density) { viewportDp.toPx() }
    // Bigger than the crop circle so there's real context visible around the frame.
    val stageDp = 340.dp

    val sourceBitmap = remember(picturePath) { decodeSampledBitmap(picturePath, 1600) }
    if (sourceBitmap == null) {
        onDismiss()
        return
    }

    val bw = sourceBitmap.width.toFloat()
    val bh = sourceBitmap.height.toFloat()
    // A pure "cover" fit leaves the already-fitting axis with zero pan room — for anything
    // but a perfectly square source, one drag direction would do nothing at the default
    // zoom, reading as broken. A generous overscan margin guarantees both axes always have
    // real slack to drag with — including sliding a tall photo all the way up to its top
    // edge or down to its bottom edge — without needing to pinch first. zoom=1f (the default)
    // sits at this margin, not at true cover fit — minZoom below compensates so pinching out
    // can still reach all the way back down to true cover, instead of stopping 1.6x short of it.
    val overscan = 1.6f
    val baseScale = maxOf(viewportPx / bw, viewportPx / bh) * overscan
    val baseWidthDp = with(density) { (bw * baseScale).toDp() }
    val baseHeightDp = with(density) { (bh * baseScale).toDp() }
    val minZoom = 1f / overscan

    fun clampedZoom(z: Float) = z.coerceIn(minZoom, 8f)
    fun clampedPan(z: Float, px: Float, py: Float): Pair<Float, Float> {
        val totalScale = baseScale * z
        val maxPanX = (bw * totalScale / 2f - viewportPx / 2f).coerceAtLeast(0f)
        val maxPanY = (bh * totalScale / 2f - viewportPx / 2f).coerceAtLeast(0f)
        return px.coerceIn(-maxPanX, maxPanX) to py.coerceIn(-maxPanY, maxPanY)
    }

    // Resume exactly where the last crop left off, rather than resetting to the default
    // framing every time — inverse of the "Done" math below.
    val initialState = remember(picturePath, initialCrop) {
        if (initialCrop == null) {
            Triple(1f, 0f, 0f)
        } else {
            val shortSide = minOf(bw, bh)
            val cropSizePx = (initialCrop.size * shortSide).coerceAtLeast(1f)
            val totalScale = viewportPx / cropSizePx
            val z = clampedZoom(totalScale / baseScale)
            val actualScale = baseScale * z
            val leftPx = initialCrop.left * bw
            val topPx = initialCrop.top * bh
            val rawPanX = (bw / 2f - leftPx) * actualScale - viewportPx / 2f
            val rawPanY = (bh / 2f - topPx) * actualScale - viewportPx / 2f
            val (px, py) = clampedPan(z, rawPanX, rawPanY)
            Triple(z, px, py)
        }
    }
    var zoom by remember { mutableStateOf(initialState.first) }
    var panX by remember { mutableStateOf(initialState.second) }
    var panY by remember { mutableStateOf(initialState.third) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Drag to reposition, pinch to zoom",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                // A stage bigger than the crop circle itself, so the rest of the photo stays
                // visible (dimmed) around the frame instead of being hard-clipped away — the
                // frame is only a guide overlay here, not a mask on the image underneath.
                Box(
                    modifier = Modifier
                        .size(stageDp)
                        .clipToBounds()
                        .background(Color.DarkGray)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                val newZoom = clampedZoom(zoom * zoomChange)
                                val (px, py) = clampedPan(newZoom, panX + pan.x, panY + pan.y)
                                zoom = newZoom
                                panX = px
                                panY = py
                            }
                        }
                ) {
                    // Sized to its own natural "cover" dimensions and centered, rather than
                    // relying on ContentScale.Crop's own internal fit-and-clip — layering a
                    // second scale system (Crop, then our own graphicsLayer) on top of each
                    // other made the two disagree at the edges, leaving a gap the pan clamp
                    // below didn't account for. This way there's exactly one transform.
                    // requiredSize (not size) because the parent Box's fixed stageDp size
                    // otherwise coerces this down to the stage's own bounds, silently
                    // squashing the overflowing dimension ContentScale.FillBounds needs intact.
                    Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = "Crop preview",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .requiredSize(baseWidthDp, baseHeightDp)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = panX
                                translationY = panY
                            }
                    )

                    // The actual crop boundary — everything outside it is dimmed but still
                    // visible, everything inside stays at full brightness with a thin outline.
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val circle = Path().apply {
                            addOval(Rect(center = center, radius = viewportPx / 2f))
                        }
                        clipPath(circle, clipOp = ClipOp.Difference) {
                            drawRect(color = Color.Black.copy(alpha = 0.6f), size = Size(size.width, size.height))
                        }
                        drawCircle(
                            color = Color.White,
                            radius = viewportPx / 2f,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DialogActionButton(
                    icon = Icons.Default.Close,
                    label = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                DialogActionButton(
                    icon = Icons.Default.Check,
                    label = "Done",
                    onClick = {
                        val totalScale = baseScale * zoom
                        val shortSide = minOf(bw, bh)
                        val cropSize = (viewportPx / totalScale).coerceAtMost(shortSide)
                        val left = (bw / 2f - (viewportPx / 2f + panX) / totalScale)
                            .coerceIn(0f, bw - cropSize)
                        val top = (bh / 2f - (viewportPx / 2f + panY) / totalScale)
                            .coerceIn(0f, bh - cropSize)
                        onCropped(
                            ProfilePicCrop(
                                left = left / bw,
                                top = top / bh,
                                size = cropSize / shortSide
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
