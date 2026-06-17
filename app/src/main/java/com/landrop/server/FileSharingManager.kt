package com.landrop.server

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.landrop.data.AppDatabase
import com.landrop.data.TransferRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID
import java.util.Locale

data class ConnectedDevice(
    val ip: String,
    val lastSeen: Long,
    val userAgent: String
)

object FileSharingManager {
    private var fsServer: LocalFileSystemServer? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val activeTransfersLock = Any()
    private val connectedDevicesLock = Any()
    private val sharedFilesLock = Any()
    private val customFolderLock = Any()

    private val _customFolderUri = MutableStateFlow("")
    val customFolderUri = _customFolderUri.asStateFlow()

    private val _sharedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val sharedFiles = _sharedFiles.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning = _isServerRunning.asStateFlow()

    private val _serverPort = MutableStateFlow(8080)
    val serverPort = _serverPort.asStateFlow()

    private val _lastKnownWorkingPort = MutableStateFlow(8080)
    val lastKnownWorkingPort = _lastKnownWorkingPort.asStateFlow()

    private val _serverMessage = MutableStateFlow<String?>(null)
    val serverMessage = _serverMessage.asStateFlow()

    private val _serverPassword = MutableStateFlow("")
    val serverPassword = _serverPassword.asStateFlow()

    private val _isPasswordEnabled = MutableStateFlow(false)
    val isPasswordEnabled = _isPasswordEnabled.asStateFlow()

    private val _isSavedPinInvalid = MutableStateFlow(false)
    val isSavedPinInvalid = _isSavedPinInvalid.asStateFlow()

    fun postServerMessage(msg: String) {
        _serverMessage.value = msg
    }

    fun clearServerMessage() {
        _serverMessage.value = null
    }

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices = _connectedDevices.asStateFlow()

    // Tracks currently active transfers: transferId -> progress float (0.0f to 1.0f)
    private val _activeTransfers = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val activeTransfers = _activeTransfers.asStateFlow()

    private val _localIpAddress = MutableStateFlow("127.0.0.1")
    val localIpAddress = _localIpAddress.asStateFlow()

    fun refreshIpAddress(context: Context) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            _localIpAddress.value = calculateLocalIpAddress(context)
        }
    }

    fun setServerRunning(running: Boolean) {
        _isServerRunning.value = running
        if (!running) {
            _connectedDevices.value = emptyList()
            _activeTransfers.value = emptyMap()
        }
    }

    fun startServer(context: Context) {
        val port = _serverPort.value
        refreshIpAddress(context)
        
        // 1. Validate the port
        if (port <= 0 || port > 65535) {
            _serverMessage.value = "Invalid port number."
            revertToLastWorkingPort(context)
            setServerRunning(false)
            return
        }
        
        if (port in 1..1023) {
            _serverMessage.value = "This port requires elevated privileges or is restricted by Android."
            revertToLastWorkingPort(context)
            setServerRunning(false)
            return
        }

        if (fsServer != null) {
            try {
                fsServer?.stop()
            } catch (e: Exception) {}
            fsServer = null
        }

        val db = AppDatabase.getDatabase(context.applicationContext)
        val repo = TransferRepository(db.transferDao())
        
        val server = LocalFileSystemServer(context.applicationContext, port, repo)
        fsServer = server
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                server.bindAndRun(
                    onSuccess = {
                        setServerRunning(true)
                        _lastKnownWorkingPort.value = port
                        context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putInt("last_known_working_port", port)
                            .apply()
                        _serverMessage.value = "Port $port started successfully."
                        registerMdns(context.applicationContext, port)
                    },
                    onFailure = { throwable ->
                        android.util.Log.e("FileSharingManager", "Server start failed", throwable)
                        val errMsg = when {
                            throwable is java.net.BindException -> "Port $port is already in use. Choose another port."
                            throwable is java.io.IOException && throwable.message?.contains("Permission denied", ignoreCase = true) == true -> 
                                "This port requires elevated privileges or is restricted by Android."
                            else -> "Failed to bind to port $port."
                        }
                        _serverMessage.value = errMsg
                        
                        server.stop()
                        if (fsServer == server) {
                            fsServer = null
                        }
                        setServerRunning(false)
                        unregisterMdns()
                        revertToLastWorkingPort(context)
                    }
                )
            } catch (e: Exception) {
                _serverMessage.value = "Failed to bind to port $port."
                server.stop()
                if (fsServer == server) {
                    fsServer = null
                }
                setServerRunning(false)
                unregisterMdns()
                revertToLastWorkingPort(context)
            }
        }
    }

    private fun revertToLastWorkingPort(context: Context) {
        val lastPort = _lastKnownWorkingPort.value
        _serverPort.value = lastPort
        context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("server_port", lastPort)
            .apply()
    }

    fun stopServer() {
        fsServer?.stop()
        fsServer = null
        unregisterMdns()
        setServerRunning(false)
    }

    private fun registerMdns(context: Context, port: Int) {
        try {
            if (nsdManager != null) {
                unregisterMdns()
            }
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "LANDrop"
                serviceType = "_http._tcp"
                setPort(port)
            }
            
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    android.util.Log.d("FileSharingManager", "mDNS Service registered: ${info.serviceName}")
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    android.util.Log.e("FileSharingManager", "mDNS Registration failed: $errorCode")
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) {
                    android.util.Log.d("FileSharingManager", "mDNS Service unregistered")
                }

                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    android.util.Log.e("FileSharingManager", "mDNS Unregistration failed: $errorCode")
                }
            }
            
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            android.util.Log.e("FileSharingManager", "Failed starting mDNS service", e)
        }
    }

    private fun unregisterMdns() {
        try {
            if (nsdManager != null && registrationListener != null) {
                nsdManager?.unregisterService(registrationListener)
            }
        } catch (e: Exception) {
            android.util.Log.e("FileSharingManager", "Failed stopping mDNS service", e)
        } finally {
            nsdManager = null
            registrationListener = null
        }
    }

    fun setPort(context: Context, port: Int) {
        _serverPort.value = port
        context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("server_port", port)
            .apply()
    }

    fun isValidPin(pin: String): Boolean {
        return pin.length in 4..8 && pin.all { it.isDigit() }
    }

    fun setPassword(context: Context, password: String) {
        _serverPassword.value = password
        context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("server_password", password)
            .apply()
        
        if (_isPasswordEnabled.value) {
            _isSavedPinInvalid.value = !isValidPin(password)
        } else {
            _isSavedPinInvalid.value = false
        }
    }

    fun setPasswordEnabled(context: Context, enabled: Boolean) {
        _isPasswordEnabled.value = enabled
        context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_password_enabled", enabled)
            .apply()

        if (enabled) {
            _isSavedPinInvalid.value = !isValidPin(_serverPassword.value)
        } else {
            _isSavedPinInvalid.value = false
        }
    }

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
        _serverPort.value = prefs.getInt("server_port", 8080)
        _lastKnownWorkingPort.value = prefs.getInt("last_known_working_port", 8080)
        val savedPassword = prefs.getString("server_password", "") ?: ""
        _serverPassword.value = savedPassword
        val enabledState = prefs.getBoolean("is_password_enabled", false)
        _isPasswordEnabled.value = enabledState
        _customFolderUri.value = prefs.getString("custom_download_folder_uri", "") ?: ""
        refreshIpAddress(context)

        if (enabledState) {
            _isSavedPinInvalid.value = !isValidPin(savedPassword)
        } else {
            _isSavedPinInvalid.value = false
        }
    }

    fun setCustomFolderUri(context: Context, uriString: String) {
        if (uriString.isNotEmpty()) {
            try {
                val uri = Uri.parse(uriString)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                android.util.Log.e("FileSharingManager", "Failed persisting SAF permissions for $uriString", e)
            }
        }
        synchronized(customFolderLock) {
            _customFolderUri.value = uriString
        }
        context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_download_folder_uri", uriString)
            .apply()
    }

    private fun getSafeFilename(name: String): String {
        return java.io.File(name).name ?: "file"
    }

    fun addSharedFiles(context: Context, uris: List<Uri>) {
        synchronized(sharedFilesLock) {
            val newList = _sharedFiles.value.toMutableList()
            for (uri in uris) {
                getSharedFileFromUri(context, uri)?.let { shared ->
                    if (newList.none { it.uri == uri }) {
                        newList.add(shared)
                    }
                }
            }
            _sharedFiles.value = newList
        }
    }

    fun addSharedFolders(context: Context, treeUris: List<Uri>) {
        try {
            synchronized(sharedFilesLock) {
                val newList = _sharedFiles.value.toMutableList()
                for (treeUri in treeUris) {
                    val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (rootDoc != null && rootDoc.isDirectory) {
                        val foundFiles = mutableListOf<DocumentFile>()
                        traverseDirectory(rootDoc, foundFiles)
                        for (docFile in foundFiles) {
                            val shared = SharedFile(
                                id = UUID.randomUUID().toString(),
                                uri = docFile.uri,
                                name = docFile.name ?: "unnamed",
                                size = docFile.length(),
                                mimeType = docFile.type
                            )
                            if (newList.none { it.uri == docFile.uri }) {
                                newList.add(shared)
                            }
                        }
                    }
                }
                _sharedFiles.value = newList
            }
        } catch (e: Exception) {
            android.util.Log.e("FileSharingManager", "Error in addSharedFolders operation", e)
        }
    }

    private fun traverseDirectory(dir: DocumentFile, list: MutableList<DocumentFile>) {
        try {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        traverseDirectory(file, list)
                    } else if (file.isFile) {
                        list.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FileSharingManager", "Failed traversing directory contents safely", e)
        }
    }

    fun removeSharedFile(fileId: String) {
        synchronized(sharedFilesLock) {
            _sharedFiles.value = _sharedFiles.value.filterNot { it.id == fileId }
        }
    }

    fun clearSharedFiles() {
        synchronized(sharedFilesLock) {
            _sharedFiles.value = emptyList()
        }
    }

    fun updateActiveTransfer(id: Long, progress: Float) {
        val safeProgress = if (progress.isNaN() || progress.isInfinite()) 0f else progress.coerceIn(0f, 1f)
        synchronized(activeTransfersLock) {
            val current = _activeTransfers.value.toMutableMap()
            if (safeProgress >= 1.0f) {
                current.remove(id)
            } else {
                current[id] = safeProgress
            }
            _activeTransfers.value = current
        }
    }

    fun removeActiveTransfer(id: Long) {
        synchronized(activeTransfersLock) {
            val current = _activeTransfers.value.toMutableMap()
            current.remove(id)
            _activeTransfers.value = current
        }
    }

    fun registerDeviceActivity(ip: String, userAgent: String) {
        synchronized(connectedDevicesLock) {
            val current = _connectedDevices.value.toMutableList()
            val index = current.indexOfFirst { it.ip == ip }
            val now = System.currentTimeMillis()
            if (index != -1) {
                current[index] = ConnectedDevice(ip, now, userAgent)
            } else {
                current.add(ConnectedDevice(ip, now, userAgent))
            }
            // Clean out inactive devices after 2 minutes
            _connectedDevices.value = current.filter { now - it.lastSeen < 120_000 }
        }
    }

    fun getLocalIpAddress(): String {
        return _localIpAddress.value
    }

    private fun calculateLocalIpAddress(context: Context): String {
        var wifiIpAddress: String? = null
        var fallbackIpAddress: String? = null
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (netInterface in Collections.list(interfaces)) {
                    if (netInterface == null) continue
                    if (!netInterface.isUp || netInterface.isLoopback) continue
                    
                    val name = netInterface.name?.lowercase() ?: ""
                    val addresses = netInterface.inetAddresses ?: continue
                    
                    for (inetAddress in Collections.list(addresses)) {
                        if (inetAddress == null || inetAddress.isLoopbackAddress || inetAddress.isLinkLocalAddress) continue
                        val hostAddress = inetAddress.hostAddress ?: ""
                        val isIPv4 = hostAddress.indexOf(':') < 0
                        if (isIPv4 && hostAddress.isNotEmpty() && hostAddress != "0.0.0.0" && hostAddress != "127.0.0.1") {
                            if (name.contains("wlan") || name.contains("ap") || name.contains("p2p") || name.contains("wifi")) {
                                wifiIpAddress = hostAddress
                            } else {
                                fallbackIpAddress = hostAddress
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            android.util.Log.e("FileSharingManager", "Failed enumerating network interfaces", ex)
        }

        if (wifiIpAddress != null) {
            return wifiIpAddress
        }
        if (fallbackIpAddress != null) {
            return fallbackIpAddress
        }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipAddress = wifiInfo?.ipAddress ?: 0
            if (ipAddress != 0) {
                val ipString = String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    ipAddress and 0xFF,
                    ipAddress shr 8 and 0xFF,
                    ipAddress shr 16 and 0xFF,
                    ipAddress shr 24 and 0xFF
                )
                if (ipString != "0.0.0.0" && ipString != "127.0.0.1") {
                    return ipString
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FileSharingManager", "Failed getting IP from WifiManager in fallback", e)
        }

        return ""
    }

    fun hasReceivedFile(context: Context, logId: Long): Boolean {
        val file = java.io.File(context.filesDir, "received/$logId")
        return file.exists() && file.isFile
    }

    fun isDownloaded(context: Context, logId: Long): Boolean {
        val prefs = context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("downloaded_$logId", false)
    }

    fun setDownloaded(context: Context, logId: Long, downloaded: Boolean) {
        val prefs = context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("downloaded_$logId", downloaded).apply()
    }

    fun downloadReceivedFileToCustomFolder(context: Context, logId: Long, fileName: String, customUriString: String): Uri? {
        val privateFile = java.io.File(context.filesDir, "received/$logId")
        if (!privateFile.exists()) {
            android.util.Log.e("FileSharingManager", "Private received file not found for log: $logId")
            return null
        }

        return try {
            val treeUri = Uri.parse(customUriString)
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (pEx: Exception) {
                // Ignore if permission already persisted or not grantable
            }

            val parentDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (parentDoc == null || !parentDoc.isDirectory || !parentDoc.canWrite()) {
                android.util.Log.e("FileSharingManager", "Custom folder is unavailable or not writable")
                return null
            }

            val safeName = getSafeFilename(fileName)
            val baseName = safeName.substringBeforeLast(".")
            val extension = safeName.substringAfterLast(".", "")
            val suffix = if (extension.isNotEmpty()) ".$extension" else ""
            var uniqueName = safeName

            var targetDoc = parentDoc.findFile(uniqueName)
            var counter = 1
            while (targetDoc != null) {
                uniqueName = "$baseName ($counter)$suffix"
                targetDoc = parentDoc.findFile(uniqueName)
                counter++
            }

            val mime = context.contentResolver.getType(Uri.fromFile(privateFile)) ?: "application/octet-stream"
            val newFileDoc = parentDoc.createFile(mime, uniqueName) ?: return null

            privateFile.inputStream().use { inputStream ->
                context.contentResolver.openOutputStream(newFileDoc.uri)?.use { outputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
            setDownloaded(context, logId, true)
            newFileDoc.uri
        } catch (e: Exception) {
            android.util.Log.e("FileSharingManager", "Failed saving file to custom tree folder", e)
            null
        }
    }

    fun downloadReceivedFileToPublic(context: Context, logId: Long, fileName: String): Uri? {
        val privateFile = java.io.File(context.filesDir, "received/$logId")
        if (!privateFile.exists()) {
            android.util.Log.e("FileSharingManager", "Private received file not found for log: $logId")
            return null
        }
        
        return try {
            privateFile.inputStream().use { inputStream ->
                val safeName = getSafeFilename(fileName)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/LANDrop")
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { os ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                os.write(buffer, 0, bytesRead)
                            }
                        }
                        setDownloaded(context, logId, true)
                        uri
                    } else null
                } else {
                    val downloadDir = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                        "LANDrop"
                    )
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs()
                    }
                    var destFile = java.io.File(downloadDir, safeName)
                    if (destFile.exists()) {
                        val baseName = safeName.substringBeforeLast(".")
                        val extension = safeName.substringAfterLast(".", "")
                        val suffix = if (extension.isNotEmpty()) ".$extension" else ""
                        var counter = 1
                        while (destFile.exists()) {
                            destFile = java.io.File(downloadDir, "$baseName ($counter)$suffix")
                            counter++
                        }
                    }
                    java.io.FileOutputStream(destFile).use { os ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            os.write(buffer, 0, bytesRead)
                        }
                    }
                    setDownloaded(context, logId, true)
                    Uri.fromFile(destFile)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FileSharingManager", "Failed exporting private file $logId to public downloads", e)
            null
        }
    }

    private fun getSharedFileFromUri(context: Context, uri: Uri): SharedFile? {
        return try {
            var name = "unknown"
            var size = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex) ?: "unknown"
                        }
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FileSharingManager", "Failed querying metadata for uri: $uri", e)
            }
            val type = try {
                context.contentResolver.getType(uri)
            } catch (e: Exception) {
                android.util.Log.e("FileSharingManager", "Failed getting mimeType for uri: $uri", e)
                null
            }
            SharedFile(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = name,
                size = size,
                mimeType = type
            )
        } catch (e: Exception) {
            android.util.Log.e("FileSharingManager", "Failed resolving SharedFile from uri: $uri", e)
            null
        }
    }
}
