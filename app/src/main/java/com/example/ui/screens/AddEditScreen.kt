package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.RecordEntity
import com.example.model.BillingFrequency
import com.example.model.DetectionSource
import com.example.model.RecordStatus
import com.example.model.RecordType
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.PrimaryPurple
import com.example.viewmodel.SubscriptionViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    recordId: Long,
    prefillDetectionId: Long,
    viewModel: SubscriptionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val pendingDetections by viewModel.pendingDetections.collectAsStateWithLifecycle()
    val preferredCurrency by viewModel.currencyFlow.collectAsStateWithLifecycle()
    val defaultReminders by viewModel.defaultRemindersFlow.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(preferredCurrency) }
    var selectedType by remember { mutableStateOf(RecordType.SUBSCRIPTION) }
    var selectedFrequency by remember { mutableStateOf(BillingFrequency.MONTHLY) }
    var customFrequencyDaysText by remember { mutableStateOf("30") }

    val today = LocalDate.now()
    var startDate by remember { mutableStateOf(today) }
    var nextDueDate by remember { mutableStateOf(today.plusMonths(1)) }
    var hasTrialEnd by remember { mutableStateOf(false) }
    var trialEndDate by remember { mutableStateOf(today.plusDays(14)) }

    var selectedOffsets by remember { mutableStateOf(setOf(7, 3, 1, 0)) }
    var category by remember { mutableStateOf("Entertainment") }
    var notes by remember { mutableStateOf("") }
    var managementUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(RecordStatus.ACTIVE) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }

    var currencyDropdownExpanded by remember { mutableStateOf(false) }
    val currencies = listOf("USD", "EUR", "GBP", "INR", "CAD", "AUD", "JPY")

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    val categories = listOf("Entertainment", "Software", "Utilities", "Finance", "Shopping", "Health", "Education", "Personal", "General")

    val isEditing = recordId > 0

    // Load existing record or prefill from detection
    LaunchedEffect(recordId, prefillDetectionId, allRecords, pendingDetections) {
        if (isEditing) {
            val record = allRecords.find { it.id == recordId }
            if (record != null) {
                name = record.name
                amountText = if (record.amount > 0.0) record.amount.toString() else ""
                currency = record.currency
                selectedType = record.getRecordTypeEnum()
                selectedFrequency = record.getFrequencyEnum()
                customFrequencyDaysText = record.customFrequencyDays.toString()
                startDate = LocalDate.ofEpochDay(record.startDateEpochDay)
                nextDueDate = LocalDate.ofEpochDay(record.nextDueDateEpochDay)
                if (record.trialEndEpochDay != null) {
                    hasTrialEnd = true
                    trialEndDate = LocalDate.ofEpochDay(record.trialEndEpochDay)
                }
                selectedOffsets = record.getReminderOffsets().toSet()
                category = record.category
                notes = record.notes
                managementUrl = record.managementUrl
                status = record.getStatusEnum()
            }
        } else if (prefillDetectionId > 0) {
            val detection = pendingDetections.find { it.id == prefillDetectionId }
            if (detection != null) {
                name = detection.extractedMerchant.ifBlank { detection.appName }
                amountText = if (detection.extractedAmount > 0.0) detection.extractedAmount.toString() else ""
                currency = detection.extractedCurrency
                selectedType = detection.getRecordTypeEnum()
                selectedFrequency = detection.getFrequencyEnum()
                category = detection.getRecordTypeEnum().defaultCategory
                notes = "Auto-detected from ${detection.appName}: \"${detection.detectedSnippet}\""
                if (detection.suggestedDueDateEpochDay != null) {
                    nextDueDate = LocalDate.ofEpochDay(detection.suggestedDueDateEpochDay)
                }
            }
        } else {
            selectedOffsets = defaultReminders.toSet()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Record" else "Add New Record",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(
                            onClick = {
                                viewModel.deleteRecord(recordId, name)
                                onNavigateBack()
                            },
                            modifier = Modifier.testTag("delete_record_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertCoral)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Record Type Selector
            Text(
                text = "Record Type",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecordType.values().forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            category = type.defaultCategory
                            if (type == RecordType.FREE_TRIAL) {
                                hasTrialEnd = true
                            }
                        },
                        label = { Text(type.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryPurple,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Name / Merchant field
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = if (it.isBlank()) "Merchant or record name is required" else null
                },
                label = { Text("Merchant / Service / Item Name *") },
                placeholder = { Text("e.g. Netflix, Amazon Prime, Google One, Gym") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("record_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Amount and Currency Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        val parsed = it.toDoubleOrNull()
                        amountError = if (it.isNotBlank() && (parsed == null || parsed < 0)) "Enter valid amount" else null
                    },
                    label = { Text("Amount (0 if free)") },
                    placeholder = { Text("9.99") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("record_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = currencyDropdownExpanded,
                    onExpandedChange = { currencyDropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Currency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .testTag("record_currency_selector"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = currencyDropdownExpanded,
                        onDismissRequest = { currencyDropdownExpanded = false }
                    ) {
                        currencies.forEach { curr ->
                            DropdownMenuItem(
                                text = { Text(curr) },
                                onClick = {
                                    currency = curr
                                    currencyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Billing Frequency
            Text(
                text = "Billing Frequency",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BillingFrequency.values().forEach { freq ->
                    FilterChip(
                        selected = selectedFrequency == freq,
                        onClick = { selectedFrequency = freq },
                        label = { Text(freq.displayName) }
                    )
                }
            }

            if (selectedFrequency == BillingFrequency.CUSTOM) {
                OutlinedTextField(
                    value = customFrequencyDaysText,
                    onValueChange = { customFrequencyDaysText = it },
                    label = { Text("Recur every (days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Next Due / Renewal / Expiry Date Picker (MANDATORY & VALIDATED)
            Text(
                text = if (selectedType.isDeadlineType()) "Deadline Date *" else "Next Renewal / Payment Date *",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                nextDueDate = LocalDate.of(year, month + 1, dayOfMonth)
                            },
                            nextDueDate.year,
                            nextDueDate.monthValue - 1,
                            nextDueDate.dayOfMonth
                        ).show()
                    }
                    .testTag("next_due_date_picker")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = nextDueDate.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap to change deadline date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryPurple)
                }
            }

            // Optional Trial End Date for Free Trials
            if (selectedType == RecordType.FREE_TRIAL || hasTrialEnd) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Free Trial End Date",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AlertCoral.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    trialEndDate = LocalDate.of(year, month + 1, dayOfMonth)
                                },
                                trialEndDate.year,
                                trialEndDate.monthValue - 1,
                                trialEndDate.dayOfMonth
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = trialEndDate.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AlertCoral
                            )
                            Text(
                                text = "Cancel before this date to prevent paid conversion",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = AlertCoral)
                    }
                }
            }

            // Reminder Schedule Presets
            Text(
                text = "Reminder Schedule (When to notify)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    14 to "14 days before",
                    7 to "7 days before",
                    3 to "3 days before",
                    1 to "1 day before",
                    0 to "On the day"
                ).forEach { (offset, label) ->
                    val isSelected = selectedOffsets.contains(offset)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedOffsets = if (isSelected) {
                                selectedOffsets - offset
                            } else {
                                selectedOffsets + offset
                            }
                        },
                        label = { Text(label) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryPurple,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Cancellation / Management Link
            OutlinedTextField(
                value = managementUrl,
                onValueChange = { managementUrl = it },
                label = { Text("Cancellation or Management URL (Optional)") },
                placeholder = { Text("https://www.service.com/account/cancel") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("record_url_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Notes field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Cancellation Instructions (Optional)") },
                placeholder = { Text("e.g. Billed to Chase credit card, must call to cancel or click Manage Membership.") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = "Merchant or record name is required"
                        return@Button
                    }
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    val offsetsCsv = if (selectedOffsets.isEmpty()) "1,0" else selectedOffsets.sortedDescending().joinToString(",")
                    val customDays = customFrequencyDaysText.toIntOrNull() ?: 30

                    val recordToSave = RecordEntity(
                        id = if (isEditing) recordId else 0L,
                        name = name.trim(),
                        amount = amount,
                        currency = currency,
                        recordType = selectedType.name,
                        frequency = selectedFrequency.name,
                        customFrequencyDays = customDays,
                        startDateEpochDay = startDate.toEpochDay(),
                        nextDueDateEpochDay = nextDueDate.toEpochDay(),
                        trialEndEpochDay = if (selectedType == RecordType.FREE_TRIAL || hasTrialEnd) trialEndDate.toEpochDay() else null,
                        reminderOffsetsCsv = offsetsCsv,
                        category = category,
                        notes = notes.trim(),
                        managementUrl = managementUrl.trim(),
                        detectionSource = if (prefillDetectionId > 0) DetectionSource.NOTIFICATION.name else (if (isEditing) allRecords.find { it.id == recordId }?.detectionSource ?: DetectionSource.MANUAL.name else DetectionSource.MANUAL.name),
                        detectionConfidence = 1.0f,
                        status = status.name,
                        updatedAt = System.currentTimeMillis()
                    )

                    if (isEditing) {
                        viewModel.updateRecord(recordToSave)
                    } else {
                        viewModel.saveRecord(recordToSave)
                        if (prefillDetectionId > 0) {
                            viewModel.dismissDetection(prefillDetectionId)
                        }
                    }
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_record_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditing) "Save Changes" else "Track Subscription & Remind Me",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
