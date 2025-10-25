package com.kiz.transitapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyScheduleScreen() {
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var selectedDay by remember { mutableStateOf("Monday") }
    var showAddClassDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Weekly Schedule",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Day selector tabs
        ScrollableTabRow(
            selectedTabIndex = daysOfWeek.indexOf(selectedDay),
            modifier = Modifier.fillMaxWidth()
        ) {
            daysOfWeek.forEach { day ->
                Tab(
                    selected = selectedDay == day,
                    onClick = { selectedDay = day },
                    text = { Text(day.take(3)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Classes for selected day
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Classes for $selectedDay",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            FloatingActionButton(
                onClick = { showAddClassDialog = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add class")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ClassesList(selectedDay = selectedDay)
    }

    if (showAddClassDialog) {
        AddClassDialog(
            onDismiss = { showAddClassDialog = false },
            onClassAdded = { className, time, busStop ->
                // TODO: Save class to database
                showAddClassDialog = false
            }
        )
    }
}

@Composable
fun ClassesList(selectedDay: String) {
    // Mock data - will be replaced with actual data from database
    val mockClasses = when (selectedDay) {
        "Monday" -> listOf(
            ClassItem("BIOL 101", "10:00 AM", "University at Sunset"),
            ClassItem("MATH 200", "2:00 PM", "University at Sunset")
        )
        "Wednesday" -> listOf(
            ClassItem("BIOL 101", "10:00 AM", "University at Sunset"),
            ClassItem("CHEM 150", "1:00 PM", "Ouellette at Chatham")
        )
        "Friday" -> listOf(
            ClassItem("MATH 200", "11:00 AM", "University at Sunset")
        )
        else -> emptyList()
    }

    if (mockClasses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No classes scheduled",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap + to add a class",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mockClasses) { classItem ->
                ClassCard(classItem = classItem)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassCard(classItem: ClassItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = classItem.className,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Time: ${classItem.time}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Stop: ${classItem.busStop}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { /* TODO: Delete class */ }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete class",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddClassDialog(
    onDismiss: () -> Unit,
    onClassAdded: (String, String, String) -> Unit
) {
    var className by remember { mutableStateOf("") }
    var classTime by remember { mutableStateOf("") }
    var selectedBusStop by remember { mutableStateOf("") }

    // Mock favorite stops - will be replaced with actual data
    val favoriteStops = listOf(
        "University at Sunset",
        "Ouellette at Chatham",
        "Tecumseh Mall"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Class") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Class Name") },
                    placeholder = { Text("e.g., BIOL 101") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = classTime,
                    onValueChange = { classTime = it },
                    label = { Text("Class Time") },
                    placeholder = { Text("e.g., 10:00 AM") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Bus stop dropdown would go here
                // For now, using a simple text field
                OutlinedTextField(
                    value = selectedBusStop,
                    onValueChange = { selectedBusStop = it },
                    label = { Text("Bus Stop") },
                    placeholder = { Text("Select your commute stop") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (className.isNotBlank() && classTime.isNotBlank() && selectedBusStop.isNotBlank()) {
                        onClassAdded(className, classTime, selectedBusStop)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class ClassItem(
    val className: String,
    val time: String,
    val busStop: String
)
