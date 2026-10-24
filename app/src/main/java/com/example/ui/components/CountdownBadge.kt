package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.SuccessGreen

@Composable
fun CountdownBadge(
    daysLeft: Long,
    isDeadline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, text) = when {
        daysLeft < 0 -> Triple(
            AlertCoral.copy(alpha = 0.15f),
            AlertCoral,
            "Expired"
        )
        daysLeft == 0L -> Triple(
            AlertCoral,
            Color.White,
            "Due Today!"
        )
        daysLeft == 1L -> Triple(
            AlertCoral.copy(alpha = 0.2f),
            AlertCoral,
            if (isDeadline) "Ends Tomorrow" else "Renews Tomorrow"
        )
        daysLeft in 2..3 -> Triple(
            AccentOrange.copy(alpha = 0.2f),
            AccentOrange,
            if (isDeadline) "Ends in $daysLeft days" else "Renews in $daysLeft days"
        )
        daysLeft in 4..7 -> Triple(
            PrimaryPurple.copy(alpha = 0.15f),
            PrimaryPurple,
            "In $daysLeft days"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "In $daysLeft days"
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
