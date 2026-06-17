package com.landrop.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.landrop.data.AppDatabase
import com.landrop.data.TransferEntity
import com.landrop.data.TransferRepository
import com.landrop.server.ConnectedDevice
import com.landrop.server.FileSharingManager
import com.landrop.server.SharedFile
import com.landrop.service.FileServerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val db = AppDatabase.getDatabase(application)
    private val repository = TransferRepository(db.transferDao())

    private val _downloadedRegistry = MutableStateFlow<Set<Long>>(emptySet())
    val downloadedRegistry: StateFlow<Set<Long>> = _downloadedRegistry.asStateFlow()

    init {
        FileSharingManager.loadSettings(application)
    }

    val sharedFiles: StateFlow<List<SharedFile>> = FileSharingManager.sharedFiles
    val isServerRunning: StateFlow<Boolean> = FileSharingManager.isServerRunning
    val serverPort: StateFlow<Int> = FileSharingManager.serverPort
    val serverPassword: StateFlow<String> = FileSharingManager.serverPassword
    val isPasswordEnabled: StateFlow<Boolean> = FileSharingManager.isPasswordEnabled
    val isSavedPinInvalid: StateFlow<Boolean> = FileSharingManager.isSavedPinInvalid
    val serverMessage: StateFlow<String?> = FileSharingManager.serverMessage
    val customFolderUri: StateFlow<String> = FileSharingManager.customFolderUri
    val connectedDevices: StateFlow<List<ConnectedDevice>> = FileSharingManager.connectedDevices
    val activeTransfers: StateFlow<Map<Long, Float>> = FileSharingManager.activeTransfers
    val localIpAddress: StateFlow<String> = FileSharingManager.localIpAddress

    val transferHistory: StateFlow<List<TransferEntity>> = repository.allTransfers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            FileSharingManager.addSharedFiles(getApplication(), uris)
        }
    }

    fun addFolders(treeUris: List<Uri>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            FileSharingManager.addSharedFolders(getApplication(), treeUris)
        }
    }

    fun removeSharedFile(id: String) {
        FileSharingManager.removeSharedFile(id)
    }

    fun clearSharedFiles() {
        FileSharingManager.clearSharedFiles()
    }

    fun clearServerMessage() {
        FileSharingManager.clearServerMessage()
    }

    fun toggleServer() {
        val currentlyRunning = isServerRunning.value
        val context = getApplication<Application>()
        if (currentlyRunning) {
            FileSharingManager.stopServer()
            FileServerService.stopService(context)
        } else {
            FileSharingManager.startServer(context)
            FileServerService.startService(context)
        }
    }

    fun updateSettings(port: Int, usePassword: Boolean, passwordCode: String) {
        val context = getApplication<Application>()
        val oldPort = FileSharingManager.serverPort.value
        val oldUsePassword = FileSharingManager.isPasswordEnabled.value
        val oldPassword = FileSharingManager.serverPassword.value
        
        // Validate the port before updating or saving
        if (port <= 0 || port > 65535) {
            FileSharingManager.postServerMessage("Invalid port number.")
            return
        }
        if (port in 1..1023) {
            FileSharingManager.postServerMessage("This port requires elevated privileges or is restricted by Android.")
            return
        }
        
        FileSharingManager.setPort(context, port)
        FileSharingManager.setPasswordEnabled(context, usePassword)
        FileSharingManager.setPassword(context, passwordCode)
        
        val portChanged = (oldPort != port)
        
        if (isServerRunning.value) {
            // Restart server only if the port changed, as changing the PIN does not require a socket reboot
            if (portChanged) {
                FileSharingManager.stopServer()
                FileSharingManager.startServer(context)
                // Update service notification if details changed
                FileServerService.startService(context)
            }
        } else {
            // Boot the server if it was stopped, matching user expectation
            FileSharingManager.startServer(context)
            FileServerService.startService(context)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun getLocalIpAddress(): String {
        return FileSharingManager.getLocalIpAddress()
    }

    fun refreshIpAddress() {
        FileSharingManager.refreshIpAddress(getApplication())
    }

    fun hasReceivedFile(logId: Long): Boolean {
        return FileSharingManager.hasReceivedFile(getApplication(), logId)
    }

    fun isFileDownloaded(logId: Long): Boolean {
        return _downloadedRegistry.value.contains(logId) || FileSharingManager.isDownloaded(getApplication(), logId)
    }

    fun saveCustomFolderUri(uri: Uri) {
        FileSharingManager.setCustomFolderUri(getApplication(), uri.toString())
    }

    fun clearCustomFolderUri() {
        FileSharingManager.setCustomFolderUri(getApplication(), "")
    }

    fun downloadFile(logId: Long, fileName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val customFolder = customFolderUri.value
            var uri: Uri? = null
            if (customFolder.isNotEmpty()) {
                uri = FileSharingManager.downloadReceivedFileToCustomFolder(getApplication(), logId, fileName, customFolder)
                if (uri == null) {
                    android.util.Log.w("MainViewModel", "Custom folder save failed, falling back to public downloads directory.")
                    uri = FileSharingManager.downloadReceivedFileToPublic(getApplication(), logId, fileName)
                }
            } else {
                uri = FileSharingManager.downloadReceivedFileToPublic(getApplication(), logId, fileName)
            }
            if (uri != null) {
                val current = _downloadedRegistry.value.toMutableSet()
                current.add(logId)
                _downloadedRegistry.value = current
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete(uri != null)
            }
        }
    }
}
