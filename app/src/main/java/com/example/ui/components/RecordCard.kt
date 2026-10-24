package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.RecordEntity
import com.example.model.DetectionSource
import com.example.model.RecordStatus
import com.example.model.RecordType
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.SuccessGreen
import java.time.LocalDate

@Composable
fun RecordCard(
    record: RecordEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onPauseResume: () -> Unit,
    onMarkCancelled: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    val daysLeft = record.daysUntilDue(today)
    val typeEnum = record.getRecordTypeEnum()
    val isDeadline = typeEnum.isDeadlineType()
    val nextDate = LocalDate.ofEpochDay(record.nextDueDateEpochDay).toString()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("record_card_${record.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category / Type Avatar Icon
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = getIconBgColor(typeEnum)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getRecordIcon(typeEnum),
                            contentDescription = null,
                            tint = getIconTintColor(typeEnum),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name and Type
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${typeEnum.displayName} • ${record.getFrequencyEnum().displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Amount
                if (record.amount > 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${record.currency} %.2f".format(record.amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Action Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("record_menu_button_${record.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (record.status == RecordStatus.ACTIVE.name) "Pause Tracking" else "Resume Tracking")
                            },
                            leadingIcon = {
                                Icon(
                                    if (record.status == RecordStatus.ACTIVE.name) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onPauseResume()
                            }
                        )
                        if (record.status != RecordStatus.CANCELLED.name) {
                            DropdownMenuItem(
                                text = { Text("Mark Cancelled") },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AlertCoral) },
                                onClick = {
                                    menuExpanded = false
                                    onMarkCancelled()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = AlertCoral) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AlertCoral) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Badges: Countdown, Date, and Detection Source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (record.status == RecordStatus.ACTIVE.name) {
                        CountdownBadge(daysLeft = daysLeft, isDeadline = isDeadline)
                    } else {
                        StatusChip(status = record.getStatusEnum())
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDeadline) "Deadline: $nextDate" else "Renews: $nextDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (record.getDetectionSourceEnum() == DetectionSource.NOTIFICATION) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryPurple.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoMode,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Auto-detected",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryPurple
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: RecordStatus) {
    val (color, text) = when (status) {
        RecordStatus.ACTIVE -> SuccessGreen to "Active"
        RecordStatus.PAUSED -> AccentOrange to "Paused"
        RecordStatus.CANCELLED -> AlertCoral to "Cancelled"
        RecordStatus.ARCHIVED -> Color.Gray to "Archived"
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun getRecordIcon(type: RecordType): ImageVector {
    return when (type) {
        RecordType.SUBSCRIPTION -> Icons.Default.CardMembership
        RecordType.FREE_TRIAL -> Icons.Default.HourglassTop
        RecordType.UPI_AUTOPAY -> Icons.Default.AutoMode
        RecordType.CARD_RECURRING -> Icons.Default.CreditCard
        RecordType.BANK_DEBIT -> Icons.Default.Receipt
        RecordType.BILL_REMINDER -> Icons.Default.Receipt
        RecordType.PRODUCT_RETURN -> Icons.Default.ShoppingBag
        RecordType.WARRANTY_EXPIRATION -> Icons.Default.Security
        RecordType.DOCUMENT_EXPIRATION -> Icons.Default.EventNote
        RecordType.CUSTOM_REMINDER -> Icons.Default.HelpOutline
    }
}

private fun getIconBgColor(type: RecordType): Color {
    return when (type) {
        RecordType.FREE_TRIAL -> AlertCoral.copy(alpha = 0.15f)
        RecordType.UPI_AUTOPAY -> AccentOrange.copy(alpha = 0.15f)
        RecordType.PRODUCT_RETURN, RecordType.WARRANTY_EXPIRATION -> PrimaryPurple.copy(alpha = 0.15f)
        else -> PrimaryPurple.copy(alpha = 0.12f)
    }
}

private fun getIconTintColor(type: RecordType): Color {
    return when (type) {
        RecordType.FREE_TRIAL -> AlertCoral
        RecordType.UPI_AUTOPAY -> AccentOrange
        RecordType.PRODUCT_RETURN, RecordType.WARRANTY_EXPIRATION -> PrimaryPurple
        else -> PrimaryPurple
    }
}
