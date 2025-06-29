package com.example.swiftcard.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.swiftcard.data.model.BusinessCard

@Composable
fun BusinessCardItem(
    businessCard: BusinessCard,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Image Preview (if imageUrl is available)
            businessCard.imageURL?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Business Card Image Preview",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)), // Rounded corners for image
                    contentScale = ContentScale.Crop,
                )
            } ?: run {
                // Placeholder if no image URL
                // You can add an Icon or a simple Box here
                Spacer(modifier = Modifier.size(60.dp)) // Maintain layout
            }


            Spacer(modifier = Modifier.width(16.dp))

            Column (
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = businessCard.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = businessCard.company,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = businessCard.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Card")
            }
        }
    }
}