package com.kiz.transitapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kiz.transitapp.ui.viewmodel.SettingsViewModel
import com.kiz.transitapp.ui.components.LocationInputDialog

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel()
) {
    val updateState by viewModel.updateState.collectAsState()
    val homeLocation by viewModel.homeLocation.collectAsState()
    val schoolLocation by viewModel.schoolLocation.collectAsState()

    // Dialog states
    var showHomeDialog by remember { mutableStateOf(false) }
    var showSchoolDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // GTFS Data Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Transit Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto-update status
                    SettingsItem(
                        icon = Icons.Default.Schedule,
                        title = "Automatic Updates",
                        subtitle = if (updateState.isScheduled) "Scheduled daily at 2:00 AM" else "Not scheduled"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Last update time
                    SettingsItem(
                        icon = Icons.Default.Refresh,
                        title = "Last Updated",
                        subtitle = updateState.lastUpdateTime ?: "Never updated"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Manual refresh button with progress
                    if (updateState.isUpdating) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = updateState.status,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${updateState.progress}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { updateState.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.triggerManualUpdate() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Transit Data")
                        }
                    }

                    if (updateState.status.isNotEmpty() && !updateState.isUpdating) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateState.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateState.status.contains("success", ignoreCase = true))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // TODO: Quick Locations Section - Commented out for now, can be re-enabled in the future
        /*
        // Saved Locations Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Quick Locations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsItem(
                        icon = Icons.Default.Home,
                        title = "Home Location",
                        subtitle = homeLocation?.address ?: "Set your home address for quick route planning",
                        onClick = { showHomeDialog = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsItem(
                        icon = Icons.Default.School,
                        title = "School Location",
                        subtitle = schoolLocation?.address ?: "Set your college address for easy commute planning",
                        onClick = { showSchoolDialog = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "These locations will appear as quick options when planning routes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 40.dp)
                    )
                }
            }
        }
        */

        // App Information (minimal)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "About Transit App",
                        subtitle = "Version 1.0 • Built for me :)",
                        onClick = null // No action needed, just info display
                    )
                }
            }
        }

        // Add some bottom padding for better scrolling experience
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Location input dialogs
    if (showHomeDialog) {
        LocationInputDialog(
            title = "Set Home Location",
            currentAddress = homeLocation?.address,
            onSave = { address -> viewModel.setHomeLocation(address) },
            onClear = { viewModel.clearHomeLocation() },
            onDismiss = { showHomeDialog = false }
        )
    }

    if (showSchoolDialog) {
        LocationInputDialog(
            title = "Set School Location",
            currentAddress = schoolLocation?.address,
            onSave = { address -> viewModel.setSchoolLocation(address) },
            onClear = { viewModel.clearSchoolLocation() },
            onDismiss = { showSchoolDialog = false }
        )
    }
}

// The original SettingsItem composable is kept for when we add it back.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        onClick = onClick ?: {},
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trailing != null) {
                Spacer(modifier = Modifier.width(16.dp))
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
