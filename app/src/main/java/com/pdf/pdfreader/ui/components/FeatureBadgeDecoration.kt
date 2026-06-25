package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdf.pdfreader.core.model.FeatureBadge

/**
 * Draws the BETA / SOON / PREMIUM(lock) decoration for a feature control.
 *
 * Place inside a [Box] that already contains the control; the badge is aligned to
 * the top-end corner. Renders nothing for [FeatureBadge.NONE].
 */
@Composable
fun BoxScope.FeatureBadgeDecoration(badge: FeatureBadge) {
    when (badge) {
        FeatureBadge.NONE -> Unit
        FeatureBadge.PREMIUM -> {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Premium",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
            )
        }
        FeatureBadge.BETA -> BadgeChip("BETA", MaterialTheme.colorScheme.tertiary)
        FeatureBadge.COMING_SOON -> BadgeChip("SOON", MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun BoxScope.BadgeChip(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 7.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 3.dp, vertical = 1.dp)
    )
}
