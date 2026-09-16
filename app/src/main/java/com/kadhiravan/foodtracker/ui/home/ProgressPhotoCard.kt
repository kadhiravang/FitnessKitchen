package com.kadhiravan.foodtracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kadhiravan.foodtracker.data.local.ProgressPhoto
import com.kadhiravan.foodtracker.util.DateUtils
import java.io.File

/** A weekly-ish visual check-in, separate from the scale, one photo per day, shown for
 * whichever date the Diary tab currently has selected. Tapping the photo opens a
 * scrollable gallery of every day's photo. */
@Composable
fun ProgressPhotoCard(
    photo: ProgressPhoto?,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onDelete: (ProgressPhoto) -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Progress photo", style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = onTakePhoto) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Take photo")
                    }
                    IconButton(onClick = onPickFromGallery) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Choose from gallery")
                    }
                }
            }

            if (photo == null) {
                Text(
                    "No photo for this day yet, a weekly one alongside your weigh-ins helps you see changes numbers miss.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                TextButton(onClick = onOpenGallery, modifier = Modifier.padding(top = 4.dp)) {
                    Text("View all photos")
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    AsyncImage(
                        model = File(photo.filePath),
                        contentDescription = "Progress photo from ${DateUtils.isoToDisplay(photo.date)}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenGallery)
                    )
                    IconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete photo", tint = Color.White)
                    }
                }
                Text(
                    DateUtils.isoToDisplay(photo.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (confirmDelete && photo != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this photo?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(photo); confirmDelete = false }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
