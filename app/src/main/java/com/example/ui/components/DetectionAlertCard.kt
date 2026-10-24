package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.DetectedNotificationEntity
import com.example.data.entity.RecordEntity
import com.example.model.BillingFrequency
import com.example.model.DetectedEventType
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.SuccessGreen
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetectionAlertCard(
    detection: DetectedNotificationEntity,
    matchedRecord: RecordEntity?,
    onConfirm: (customDueDate: Long?, customFrequency: BillingFrequency?) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onIgnoreSource: () -> Unit,
    onReviewLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEvidenceDetails by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    var selectedDueDate by remember {
        mutableStateOf(
            if (detection.suggestedDueDateEpochDay != null) {
                LocalDate.ofEpochDay(detection.suggestedDueDateEpochDay)
            } else {
                today.plusMonths(1)
            }
        )
    }

    var selectedFrequency by remember { mutableStateOf(detection.getFrequencyEnum()) }
    var frequencyDropdownExpanded by remember { mutableStateOf(false) }

    val percent = (detection.confidence * 100).toInt()
    val hasDate = detection.suggestedDueDateEpochDay != null
    val eventType = detection.getEventCategoryEnum()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("detection_alert_card_${detection.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFBEB) // Warm alert amber tint
        ),
        border = BorderStroke(1.5.dp, AccentOrange.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Possible recurring payment detected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentOrange.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "$percent% confidence",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Event & Source badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source: ${detection.appName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB45309)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = getEventColor(eventType).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = eventType.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = getEventColor(eventType),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Extracted Details: Merchant, Amount, Next Due Date
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = detection.normalizedMerchant.ifBlank { detection.extractedMerchant },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = DarkNavy
                            )
                            Text(
                                text = "Raw: \"${detection.detectedSnippet}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F),
                                maxLines = 2
                            )
                        }

                        if (detection.extractedAmount > 0.0) {
                            Text(
                                text = "${detection.extractedCurrency} %.2f".format(detection.extractedAmount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = DarkNavy
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Date & Frequency selection if date was missing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date picker button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (hasDate) Color(0xFFF1F5F9) else AlertCoral.copy(alpha = 0.1f),
                            modifier = Modifier.clickable {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        selectedDueDate = LocalDate.of(year, month + 1, dayOfMonth)
                                    },
                                    selectedDueDate.year,
                                    selectedDueDate.monthValue - 1,
                                    selectedDueDate.dayOfMonth
                                ).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = if (hasDate) PrimaryPurple else AlertCoral,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (hasDate) "Date: $selectedDueDate" else "Set Date: $selectedDueDate",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasDate) DarkNavy else AlertCoral
                                )
                            }
                        }

                        // Frequency selector
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "Freq: ${selectedFrequency.displayName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkNavy,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Existing Subscription Match Notice (Step 4 & Step 6)
            if (matchedRecord != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Matches active subscription: '${matchedRecord.name}'. Confirming will update the renewal schedule without creating a duplicate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF065F46),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Expandable Evidence Explanation
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEvidenceDetails = !showEvidenceDetails }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Why was this detected?",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB45309)
                )
                Icon(
                    imageVector = if (showEvidenceDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showEvidenceDetails) {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = detection.confidenceReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF78350F),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Action Buttons (Confirm, Edit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onConfirm(selectedDueDate.toEpochDay(), selectedFrequency)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("confirm_detection_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (matchedRecord != null) "Update Record" else "Confirm & Save", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_detection_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Details", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Secondary Action Row: Review Later, Ignore Source, Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onReviewLater,
                    modifier = Modifier.testTag("review_later_button")
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Review Later", fontSize = 12.sp, color = Color.Gray)
                }

                TextButton(
                    onClick = onIgnoreSource,
                    modifier = Modifier.testTag("ignore_source_button")
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ignore Source", fontSize = 12.sp, color = Color.Gray)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_detection_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = AlertCoral)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dismiss", fontSize = 12.sp, color = AlertCoral)
                }
            }
        }
    }
}

private fun getEventColor(type: DetectedEventType): Color {
    return when (type) {
        DetectedEventType.NEW_MANDATE_CREATED -> PrimaryPurple
        DetectedEventType.FREE_TRIAL_ACTIVATED -> AlertCoral
        DetectedEventType.UPCOMING_RECURRING_DEBIT -> AccentOrange
        DetectedEventType.SUCCESSFUL_RECURRING_PAYMENT -> SuccessGreen
        DetectedEventType.FAILED_PAYMENT -> AlertCoral
        DetectedEventType.SUBSCRIPTION_RENEWED -> PrimaryPurple
        DetectedEventType.SUBSCRIPTION_CANCELLED, DetectedEventType.MANDATE_REVOKED -> Color.Gray
        else -> Color.Gray
    }
}
