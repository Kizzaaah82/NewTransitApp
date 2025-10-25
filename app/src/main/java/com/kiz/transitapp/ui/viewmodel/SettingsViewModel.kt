package com.kiz.transitapp.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.kiz.transitapp.data.gtfs.GtfsDownloadWorker
import com.kiz.transitapp.data.gtfs.GtfsUpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class SavedLocation(
    val name: String,
    val address: String
)

data class GtfsUpdateState(
    val isUpdating: Boolean = false,
    val progress: Int = 0,
    val status: String = "",
    val lastUpdateTime: String? = null,
    val isScheduled: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val gtfsUpdateManager = GtfsUpdateManager(application)

    private val _updateState = MutableStateFlow(GtfsUpdateState())
    val updateState: StateFlow<GtfsUpdateState> = _updateState.asStateFlow()

    // Location state flows
    private val _homeLocation = MutableStateFlow<SavedLocation?>(null)
    val homeLocation: StateFlow<SavedLocation?> = _homeLocation.asStateFlow()

    private val _schoolLocation = MutableStateFlow<SavedLocation?>(null)
    val schoolLocation: StateFlow<SavedLocation?> = _schoolLocation.asStateFlow()

    init {
        // Initialize daily updates when ViewModel is created
        gtfsUpdateManager.scheduleDaily2AMUpdates()

        // Monitor update status
        viewModelScope.launch {
            gtfsUpdateManager.getManualUpdateStatus().collect { workInfo ->
                updateStateFromWorkInfo(workInfo)
            }
        }

        // Monitor if updates are scheduled
        viewModelScope.launch {
            gtfsUpdateManager.isUpdateScheduled().collect { isScheduled ->
                _updateState.value = _updateState.value.copy(isScheduled = isScheduled)
            }
        }

        // Load last update time from preferences
        loadLastUpdateTime()
        loadSavedLocations()
    }

    fun triggerManualUpdate() {
        viewModelScope.launch {
            gtfsUpdateManager.triggerManualUpdate()
        }
    }

    private fun updateStateFromWorkInfo(workInfo: WorkInfo?) {
        when (workInfo?.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getInt(GtfsDownloadWorker.PROGRESS_KEY, 0)
                val status = workInfo.progress.getString(GtfsDownloadWorker.STATUS_KEY) ?: "Downloading..."

                _updateState.value = _updateState.value.copy(
                    isUpdating = true,
                    progress = progress,
                    status = status
                )
            }
            WorkInfo.State.SUCCEEDED -> {
                val timestamp = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                    .format(Date())

                saveLastUpdateTime(timestamp)

                _updateState.value = _updateState.value.copy(
                    isUpdating = false,
                    progress = 100,
                    status = "Update completed successfully!",
                    lastUpdateTime = timestamp
                )
            }
            WorkInfo.State.FAILED -> {
                val errorMessage = workInfo.outputData.getString(GtfsDownloadWorker.STATUS_KEY)
                    ?: "Update failed"

                _updateState.value = _updateState.value.copy(
                    isUpdating = false,
                    progress = 0,
                    status = errorMessage
                )
            }
            WorkInfo.State.CANCELLED -> {
                _updateState.value = _updateState.value.copy(
                    isUpdating = false,
                    progress = 0,
                    status = "Update cancelled"
                )
            }
            else -> {
                // ENQUEUED or BLOCKED
                if (workInfo?.state == WorkInfo.State.ENQUEUED) {
                    _updateState.value = _updateState.value.copy(
                        isUpdating = true,
                        progress = 0,
                        status = "Preparing to download..."
                    )
                }
            }
        }
    }

    private fun loadLastUpdateTime() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("gtfs_prefs", 0)
        val lastUpdate = sharedPrefs.getString("last_update_time", null)
        _updateState.value = _updateState.value.copy(lastUpdateTime = lastUpdate)
    }

    private fun saveLastUpdateTime(timestamp: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("gtfs_prefs", 0)
        sharedPrefs.edit().putString("last_update_time", timestamp).apply()
    }

    // Location management methods
    fun setHomeLocation(address: String) {
        val location = SavedLocation("Home", address)
        _homeLocation.value = location
        saveLocationToPrefs("home_location", location)
    }

    fun setSchoolLocation(address: String) {
        val location = SavedLocation("School", address)
        _schoolLocation.value = location
        saveLocationToPrefs("school_location", location)
    }

    fun clearHomeLocation() {
        _homeLocation.value = null
        clearLocationFromPrefs("home_location")
    }

    fun clearSchoolLocation() {
        _schoolLocation.value = null
        clearLocationFromPrefs("school_location")
    }

    private fun loadSavedLocations() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("locations_prefs", Context.MODE_PRIVATE)

        val homeAddress = sharedPrefs.getString("home_location", null)
        if (homeAddress != null) {
            _homeLocation.value = SavedLocation("Home", homeAddress)
        }

        val schoolAddress = sharedPrefs.getString("school_location", null)
        if (schoolAddress != null) {
            _schoolLocation.value = SavedLocation("School", schoolAddress)
        }
    }

    private fun saveLocationToPrefs(key: String, location: SavedLocation) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("locations_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString(key, location.address).apply()
    }

    private fun clearLocationFromPrefs(key: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("locations_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove(key).apply()
    }
}
