package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.RecordEntity
import com.example.model.RecordStatus
import com.example.ui.components.CountdownBadge
import com.example.ui.components.DetectionAlertCard
import com.example.ui.components.MarkCancelledDialog
import com.example.ui.components.RecordCard
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleDark
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.viewmodel.DashboardFilter
import com.example.viewmodel.DashboardSort
import com.example.viewmodel.SubscriptionViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: SubscriptionViewModel,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToPrefillDetection: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredRecords by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val pendingDetections by viewModel.pendingDetections.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val currentSort by viewModel.currentSort.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var recordToCancel by remember { mutableStateOf<RecordEntity?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CancelBefore",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Remember everything before it's too late",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.testTag("sort_menu_button")
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Sort records")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            DashboardSort.values().forEach { sortOption ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sortOption.label,
                                            fontWeight = if (currentSort == sortOption) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.currentSort.value = sortOption
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = PrimaryPurple,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_record_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Record")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Next Important Deadline Banner
            if (metrics.nextImportantDeadline != null) {
                item {
                    NextDeadlineHeroCard(
                        record = metrics.nextImportantDeadline!!,
                        onClick = { onNavigateToDetail(metrics.nextImportantDeadline!!.id) }
                    )
                }
            }

            // Overview Metric Chips Row
            item {
                DashboardMetricsGrid(metrics = metrics)
            }

            // Pending Automatic Detections Section
            if (pendingDetections.isNotEmpty()) {
                item {
                    Text(
                        text = "Detected Payments Awaiting Confirmation (${pendingDetections.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                }
                items(pendingDetections, key = { "det_${it.id}" }) { detection ->
                    val matchedRecord = viewModel.findMatchingRecord(detection)
                    DetectionAlertCard(
                        detection = detection,
                        matchedRecord = matchedRecord,
                        onConfirm = { customDueDate, customFreq ->
                            viewModel.confirmDetection(
                                detection = detection,
                                updateExistingRecordId = matchedRecord?.id,
                                customDueDateEpochDay = customDueDate,
                                customFrequency = customFreq
                            )
                        },
                        onEdit = { onNavigateToPrefillDetection(detection.id) },
                        onDismiss = { viewModel.dismissDetection(detection.id) },
                        onIgnoreSource = { viewModel.ignoreSource(detection.packageName, detection.appName) },
                        onReviewLater = { viewModel.markReviewLater(detection.id) }
                    )
                }
            }

            // Search and Category Filter Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_field"),
                        placeholder = { Text("Search merchants, categories, notes...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Filter chips row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DashboardFilter.values().forEach { filter ->
                            FilterChip(
                                selected = currentFilter == filter,
                                onClick = { viewModel.currentFilter.value = filter },
                                label = { Text(filter.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryPurple,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Records List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentFilter.label} (${filteredRecords.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sorted by ${currentSort.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Empty State
            if (filteredRecords.isEmpty()) {
                item {
                    EmptyRecordsState(
                        isFiltered = searchQuery.isNotEmpty() || currentFilter != DashboardFilter.ALL,
                        onAddRecord = onNavigateToAdd,
                        onResetFilter = {
                            viewModel.searchQuery.value = ""
                            viewModel.currentFilter.value = DashboardFilter.ALL
                        }
                    )
                }
            } else {
                items(filteredRecords, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        onClick = { onNavigateToDetail(record.id) },
                        onEdit = { onNavigateToEdit(record.id) },
                        onPauseResume = {
                            if (record.status == RecordStatus.ACTIVE.name) {
                                viewModel.pauseRecord(record)
                            } else {
                                viewModel.resumeRecord(record)
                            }
                        },
                        onMarkCancelled = { recordToCancel = record },
                        onDelete = { viewModel.deleteRecord(record.id, record.name) }
                    )
                }
            }
        }
    }

    // Cancellation confirmation dialog
    if (recordToCancel != null) {
        MarkCancelledDialog(
            record = recordToCancel!!,
            onDismiss = { recordToCancel = null },
            onConfirm = { effectiveDay ->
                viewModel.markCancelled(recordToCancel!!, effectiveDay)
                recordToCancel = null
            }
        )
    }
}

@Composable
fun NextDeadlineHeroCard(
    record: RecordEntity,
    onClick: () -> Unit
) {
    val today = LocalDate.now().toEpochDay()
    val daysLeft = record.daysUntilDue(today)
    val nextDate = LocalDate.ofEpochDay(record.nextDueDateEpochDay).toString()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("next_deadline_hero_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(PrimaryPurpleDark, DarkNavy)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = AlertCoral,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "NEXT IMPORTANT DEADLINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.85f),
                            letterSpacing = 1.sp
                        )
                    }
                    CountdownBadge(daysLeft = daysLeft, isDeadline = record.getRecordTypeEnum().isDeadlineType())
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = record.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Due date: $nextDate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${record.getRecordTypeEnum().displayName} • ${record.getFrequencyEnum().displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    if (record.amount > 0.0) {
                        Text(
                            text = "${record.currency} %.2f".format(record.amount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardMetricsGrid(metrics: com.example.viewmodel.DashboardMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            title = "Active Subscriptions",
            value = "${metrics.activeCount}",
            icon = Icons.Default.CreditCard,
            tint = PrimaryPurple
        )

        MetricCard(
            title = "Due Next 7 Days",
            value = "${metrics.upcoming7DaysCount}",
            icon = Icons.Default.Alarm,
            tint = if (metrics.upcoming7DaysCount > 0) AlertCoral else Slate400
        )

        MetricCard(
            title = "Trials Nearing End",
            value = "${metrics.freeTrialsNearDeadlineCount}",
            icon = Icons.Default.HourglassTop,
            tint = if (metrics.freeTrialsNearDeadlineCount > 0) AlertCoral else Slate400
        )

        MetricCard(
            title = "Returns / Warranties",
            value = "${metrics.returnsAndWarrantiesCount}",
            icon = Icons.Default.Security,
            tint = AccentOrange
        )

        // Estimated Monthly Spend (separated by currency)
        if (metrics.monthlySpendByCurrency.isNotEmpty()) {
            metrics.monthlySpendByCurrency.forEach { (currency, total) ->
                MetricCard(
                    title = "Monthly $currency Est.",
                    value = "$currency %.2f".format(total),
                    icon = Icons.Default.TrendingUp,
                    tint = PrimaryPurple
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color
) {
    Card(
        modifier = Modifier.width(135.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.12f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EmptyRecordsState(
    isFiltered: Boolean,
    onAddRecord: () -> Unit,
    onResetFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = PrimaryPurple.copy(alpha = 0.1f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = PrimaryPurple,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isFiltered) "No matching records found" else "No deadlines or payments tracked yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isFiltered) "Try a different search term or category filter." else "Add your subscriptions, free trials, and return deadlines to receive timely reminders before you are charged.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (isFiltered) {
            TextButton(onClick = onResetFilter) {
                Text("Reset Filters")
            }
        } else {
            androidx.compose.material3.Button(
                onClick = onAddRecord,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add First Record")
            }
        }
    }
}
