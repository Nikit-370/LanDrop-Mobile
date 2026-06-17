package com.landrop.server

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.landrop.data.TransferEntity
import com.landrop.data.TransferRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class LocalFileSystemServer(
    private val context: Context,
    private val port: Int,
    private val repository: TransferRepository
) {
    private var serverSocket: java.net.ServerSocket? = null
    private val serverScope = CoroutineScope(Dispatchers.IO)
    private val executor = java.util.concurrent.Executors.newCachedThreadPool()
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun bindAndRun(onSuccess: () -> Unit, onFailure: (Throwable) -> Unit) {
        try {
            try {
                serverSocket?.close()
            } catch (e: Exception) {}
            serverSocket = null

            val socket = java.net.ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(port))
            }
            serverSocket = socket
            
            onSuccess()
            
            serverScope.launch {
                try {
                    while (true) {
                        val clientSocket = serverSocket?.accept() ?: break
                        executor.execute {
                            handleClient(clientSocket)
                        }
                    }
                } catch (e: Exception) {
                    if (serverSocket != null) {
                        Log.d("LocalFileSystemServer", "Accept loop finished or terminated: ${e.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            try {
                serverSocket?.close()
            } catch (ex: Exception) {}
            serverSocket = null
            onFailure(e)
        }
    }

    fun start() {
        bindAndRun(
            onSuccess = { FileSharingManager.setServerRunning(true) },
            onFailure = { FileSharingManager.setServerRunning(false) }
        )
    }

    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
            executor.shutdownNow()
            FileSharingManager.setServerRunning(false)
            Log.d("LocalFileSystemServer", "Custom Server successfully stopped")
        } catch (e: Exception) {
            Log.e("LocalFileSystemServer", "Error stopping customized server", e)
        }
    }

    private class HttpRequest(
        val method: String,
        val path: String,
        val query: String?,
        val headers: Map<String, String>,
        val bodyStartBuffer: ByteArray,
        val bodyStartOffset: Int,
        val bodyStartLength: Int
    )

    private fun readRequest(inputStream: InputStream): HttpRequest? {
        val buffer = java.io.ByteArrayOutputStream()
        val tempBuffer = ByteArray(4096)
        var bytesRead: Int
        var endOfHeaders = -1
        
        while (true) {
            bytesRead = inputStream.read(tempBuffer)
            if (bytesRead == -1) break
            buffer.write(tempBuffer, 0, bytesRead)
            
            val currentBytes = buffer.toByteArray()
            endOfHeaders = findHeaderEnd(currentBytes)
            if (endOfHeaders != -1) {
                break
            }
            if (currentBytes.size > 16384) {
                // Protect against overlong invalid headers
                return null
            }
        }
        
        if (endOfHeaders == -1) return null
        
        val fullBytes = buffer.toByteArray()
        val headerString = String(fullBytes, 0, endOfHeaders, StandardCharsets.UTF_8)
        
        val bodyStartOffset = endOfHeaders
        val bodyStartLength = fullBytes.size - endOfHeaders
        
        val lines = headerString.split("\n")
        if (lines.isEmpty()) return null
        val requestLine = lines[0].trim()
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null
        val method = parts[0]
        val fullUrl = parts[1]
        
        val qIdx = fullUrl.indexOf('?')
        val path = if (qIdx != -1) fullUrl.substring(0, qIdx) else fullUrl
        val query = if (qIdx != -1) fullUrl.substring(qIdx + 1) else null
        
        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val colonIdx = line.indexOf(':')
            if (colonIdx != -1) {
                val name = line.substring(0, colonIdx).trim().lowercase(Locale.ROOT)
                val value = line.substring(colonIdx + 1).trim()
                headers[name] = value
            }
        }
        
        return HttpRequest(
            method = method,
            path = path,
            query = query,
            headers = headers,
            bodyStartBuffer = fullBytes,
            bodyStartOffset = bodyStartOffset,
            bodyStartLength = bodyStartLength
        )
    }

    private fun findHeaderEnd(bytes: ByteArray): Int {
        for (i in 0 until bytes.size - 3) {
            if (bytes[i] == '\r'.code.toByte() && bytes[i+1] == '\n'.code.toByte() &&
                bytes[i+2] == '\r'.code.toByte() && bytes[i+3] == '\n'.code.toByte()) {
                return i + 4
            }
        }
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == '\n'.code.toByte() && bytes[i+1] == '\n'.code.toByte()) {
                return i + 2
            }
        }
        return -1
    }

    private fun handleClient(socket: java.net.Socket) {
        try {
            socket.use { s ->
                try {
                    s.tcpNoDelay = true
                    s.receiveBufferSize = 256 * 1024
                    s.sendBufferSize = 256 * 1024
                } catch (e: Exception) {
                    Log.w("LocalFileSystemServer", "Could not set custom socket options", e)
                }
                val inputStream = java.io.BufferedInputStream(s.getInputStream(), 128 * 1024)
                val request = readRequest(inputStream) ?: return
                
                val userAgent = request.headers["user-agent"] ?: "Browser"
                val clientIp = s.inetAddress.hostAddress ?: "Unknown"
                FileSharingManager.registerDeviceActivity(clientIp, userAgent)
                
                val outputStream = java.io.BufferedOutputStream(s.getOutputStream(), 128 * 1024)
                
                when (request.path) {
                    "/" -> handlePortal(outputStream, request)
                    "/api/status" -> handleStatus(outputStream, request, clientIp)
                    "/api/delete" -> handleDelete(outputStream, request, clientIp)
                    "/download" -> handleDownload(outputStream, request, clientIp, userAgent)
                    "/api/upload" -> handleUpload(outputStream, request, inputStream, clientIp, userAgent)
                    else -> sendErrorResponse(outputStream, 404, "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e("LocalFileSystemServer", "Error handling client connection", e)
        }
    }

    private fun handlePortal(outputStream: OutputStream, request: HttpRequest) {
        if (!request.method.equals("GET", ignoreCase = true)) {
            sendErrorResponse(outputStream, 405, "Method Not Allowed")
            return
        }
        val responseBytes = WebPortalHtml.HTML.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                "Content-Length: ${responseBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        outputStream.write(header.toByteArray(StandardCharsets.UTF_8))
        outputStream.write(responseBytes)
        outputStream.flush()
    }

    private fun handleStatus(outputStream: OutputStream, request: HttpRequest, clientIp: String) {
        if (!authenticatePin(clientIp, request)) {
            val jsonStr = getAuthJsonResponse(clientIp, request)
            val errorBytes = jsonStr.toByteArray(StandardCharsets.UTF_8)
            sendJsonResponse(outputStream, 401, errorBytes)
            return
        }

        if (!request.method.equals("GET", ignoreCase = true)) {
            sendErrorResponse(outputStream, 405, "Method Not Allowed")
            return
        }

        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"device_name\":\"").append(escapeJson(deviceName)).append("\",")
        
        // files
        sb.append("\"files\":[")
        val files = FileSharingManager.sharedFiles.value
        files.forEachIndexed { index, file ->
            sb.append("{")
            sb.append("\"id\":\"").append(escapeJson(file.id)).append("\",")
            sb.append("\"name\":\"").append(escapeJson(file.name)).append("\",")
            sb.append("\"size\":").append(file.size)
            sb.append("}")
            if (index < files.size - 1) sb.append(",")
        }
        sb.append("],")

        // received
        sb.append("\"received\":[")
        val receivedList = getReceivedFiles()
        receivedList.forEachIndexed { index, file ->
            sb.append("{")
            sb.append("\"id\":\"").append(file.id).append("\",")
            sb.append("\"name\":\"").append(escapeJson(file.fileName)).append("\",")
            sb.append("\"size\":").append(file.fileSize).append(",")
            sb.append("\"timestamp\":").append(file.timestamp)
            sb.append("}")
            if (index < receivedList.size - 1) sb.append(",")
        }
        sb.append("],")

        // history
        sb.append("\"history\":[")
        val historyList = kotlinx.coroutines.runBlocking { repository.getAllTransfersList() }
        historyList.forEachIndexed { index, entry ->
            sb.append("{")
            sb.append("\"id\":\"").append(entry.id).append("\",")
            sb.append("\"name\":\"").append(escapeJson(entry.fileName)).append("\",")
            sb.append("\"size\":").append(entry.fileSize).append(",")
            sb.append("\"isUpload\":").append(entry.isUpload).append(",")
            sb.append("\"status\":\"").append(escapeJson(entry.status)).append("\",")
            sb.append("\"remoteDevice\":\"").append(escapeJson(entry.remoteDevice)).append("\",")
            sb.append("\"timestamp\":").append(entry.timestamp)
            sb.append("}")
            if (index < historyList.size - 1) sb.append(",")
        }
        sb.append("]")
        
        sb.append("}")

        val responseBytes = sb.toString().toByteArray(StandardCharsets.UTF_8)
        sendJsonResponse(outputStream, 200, responseBytes)
    }

    private fun handleDelete(outputStream: OutputStream, request: HttpRequest, clientIp: String) {
        val params = parseQueryMap(request.query)
        val fileId = params["id"]

        // PIN protection validation
        if (!authenticatePin(clientIp, request, params)) {
            val jsonStr = getAuthJsonResponse(clientIp, request, params)
            val errorBytes = jsonStr.toByteArray(StandardCharsets.UTF_8)
            sendJsonResponse(outputStream, 401, errorBytes)
            return
        }

        if (fileId == null) {
            sendErrorResponse(outputStream, 400, "Bad Request - Missing ID")
            return
        }

        val isReceived = params["type"] == "received"
        if (isReceived) {
            val logId = fileId.toLongOrNull()
            if (logId != null) {
                val f = java.io.File(context.filesDir, "received/$logId")
                if (f.exists()) {
                    f.delete()
                }
                kotlinx.coroutines.runBlocking {
                    val entry = repository.getTransferById(logId)
                    if (entry != null) {
                        repository.update(entry.copy(status = "DELETED"))
                    }
                }
            }
        } else {
            FileSharingManager.removeSharedFile(fileId)
        }
        val successBytes = "{\"success\":true}".toByteArray(StandardCharsets.UTF_8)
        sendJsonResponse(outputStream, 200, successBytes)
    }

    private fun handleDownload(
        outputStream: OutputStream,
        request: HttpRequest,
        clientIp: String,
        userAgent: String
    ) {
        val params = parseQueryMap(request.query)
        val fileId = params["id"]

        // PIN protection validation
        if (!authenticatePin(clientIp, request, params)) {
            val errorMsg = getAuthErrorMessage(clientIp, request, params)
            sendErrorResponse(outputStream, 401, errorMsg)
            return
        }

        if (fileId == null) {
            sendErrorResponse(outputStream, 400, "Bad Request - Missing ID")
            return
        }

        val isReceived = params["type"] == "received"
        var matchedFileName = ""
        var matchedFileSize = 0L
        var matchedFileMime = "application/octet-stream"
        var localFile: java.io.File? = null
        var fileUri: Uri? = null

        if (isReceived) {
            val logId = fileId.toLongOrNull()
            if (logId != null) {
                val f = java.io.File(context.filesDir, "received/$logId")
                if (f.exists() && f.isFile) {
                    localFile = f
                    matchedFileSize = f.length()
                    val entity = kotlinx.coroutines.runBlocking { repository.getTransferById(logId) }
                    matchedFileName = entity?.fileName ?: f.name
                    matchedFileMime = getMimeType(matchedFileName)
                }
            }
            if (localFile == null) {
                sendErrorResponse(outputStream, 404, "Not Found")
                return
            }
        } else {
            val matchedFile = FileSharingManager.sharedFiles.value.find { it.id == fileId }
            if (matchedFile == null) {
                sendErrorResponse(outputStream, 404, "Not Found")
                return
            }
            matchedFileName = matchedFile.name
            matchedFileSize = matchedFile.size
            matchedFileMime = matchedFile.mimeType ?: "application/octet-stream"
            fileUri = matchedFile.uri
        }

        if (!request.method.equals("GET", ignoreCase = true) && !request.method.equals("HEAD", ignoreCase = true)) {
            sendErrorResponse(outputStream, 405, "Method Not Allowed")
            return
        }

        val contentResolver = context.contentResolver
        val fileSize = matchedFileSize
        val rangeHeader = request.headers["range"]

        kotlinx.coroutines.runBlocking {
            val logId = repository.insert(
                TransferEntity(
                    fileName = matchedFileName,
                    fileSize = fileSize,
                    isUpload = false,
                    remoteDevice = "$clientIp ($userAgent)",
                    status = "TRANSFERRING"
                )
            )

            try {
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val rawRangeStr = rangeHeader.substring(6).trim()
                    val rangeStr = if (rawRangeStr.contains(",")) rawRangeStr.split(",")[0].trim() else rawRangeStr
                    var start = 0L
                    var end = fileSize - 1
                    
                    if (rangeStr.startsWith("-")) {
                        val suffixLen = rangeStr.substring(1).toLongOrNull() ?: 0L
                        start = java.lang.Math.max(0L, fileSize - suffixLen)
                        end = fileSize - 1
                    } else {
                        val parts = rangeStr.split("-")
                        start = parts[0].toLongOrNull() ?: 0L
                        if (parts.size > 1 && parts[1].isNotEmpty()) {
                            end = parts[1].toLongOrNull() ?: (fileSize - 1)
                        } else {
                            end = fileSize - 1
                        }
                    }
                    
                    if (end >= fileSize) {
                        end = fileSize - 1
                    }

                    if (start > end || start >= fileSize) {
                        val header = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                                "Content-Range: bytes */$fileSize\r\n" +
                                "Connection: close\r\n\r\n"
                        outputStream.write(header.toByteArray(StandardCharsets.UTF_8))
                        outputStream.flush()
                        repository.updateProgress(logId, 0f, "FAILED")
                        return@runBlocking
                    }

                    val contentLength = end - start + 1
                    val contentType = matchedFileMime
                    val contentRange = "bytes $start-$end/$fileSize"
                    val header = "HTTP/1.1 206 Partial Content\r\n" +
                            "Content-Type: $contentType\r\n" +
                            "Content-Range: $contentRange\r\n" +
                            "Content-Disposition: attachment; filename=\"$matchedFileName\"\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Length: $contentLength\r\n" +
                            "Connection: close\r\n\r\n"
                    outputStream.write(header.toByteArray(StandardCharsets.UTF_8))

                    if (request.method.equals("GET", ignoreCase = true)) {
                        val ipStream = if (localFile != null) {
                            java.io.FileInputStream(localFile)
                        } else {
                            contentResolver.openInputStream(fileUri!!)
                        }
                        ipStream?.use { inputStream ->
                            var skipped = 0L
                            while (skipped < start) {
                                val n = inputStream.skip(start - skipped)
                                if (n <= 0) break
                                skipped += n
                            }
                            if (skipped < start) {
                                val skipBuffer = ByteArray(4096)
                                var remainingToSkip = start - skipped
                                while (remainingToSkip > 0) {
                                    val toRead = java.lang.Math.min(skipBuffer.size.toLong(), remainingToSkip).toInt()
                                    val r = inputStream.read(skipBuffer, 0, toRead)
                                    if (r == -1) break
                                    remainingToSkip -= r
                                }
                            }
                            val buffer = ByteArray(64 * 1024)
                            var bytesRemaining = contentLength
                            var totalBytesStreamed = 0L
                            var lastReportedTime = 0L

                            while (bytesRemaining > 0) {
                                val bytesToRead = java.lang.Math.min(buffer.size.toLong(), bytesRemaining).toInt()
                                val bytesRead = inputStream.read(buffer, 0, bytesToRead)
                                if (bytesRead == -1) break
                                
                                outputStream.write(buffer, 0, bytesRead)
                                bytesRemaining -= bytesRead
                                totalBytesStreamed += bytesRead
                                
                                val currentProg = if (fileSize > 0) (start + totalBytesStreamed).toFloat() / fileSize else 1f
                                val now = System.currentTimeMillis()
                                if (now - lastReportedTime >= 500L) {
                                    FileSharingManager.updateActiveTransfer(logId, currentProg)
                                    lastReportedTime = now
                                }
                            }
                        }
                    }
                } else {
                    // Start full file streaming path
                    val contentType = matchedFileMime
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: $contentType\r\n" +
                            "Content-Disposition: attachment; filename=\"$matchedFileName\"\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Length: $fileSize\r\n" +
                            "Connection: close\r\n\r\n"
                    outputStream.write(header.toByteArray(StandardCharsets.UTF_8))

                    if (request.method.equals("GET", ignoreCase = true)) {
                        val ipStream = if (localFile != null) {
                            java.io.FileInputStream(localFile)
                        } else {
                            contentResolver.openInputStream(fileUri!!)
                        }
                        ipStream?.use { inputStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var totalBytesStreamed = 0L
                            var lastReportedTime = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesStreamed += bytesRead
                                val currentProg = if (fileSize > 0) totalBytesStreamed.toFloat() / fileSize else 1f
                                val now = System.currentTimeMillis()
                                if (now - lastReportedTime >= 500L) {
                                    FileSharingManager.updateActiveTransfer(logId, currentProg)
                                    lastReportedTime = now
                                }
                            }
                        }
                    }
                }
                outputStream.flush()
                repository.updateProgress(logId, 1f, "SUCCESS")
            } catch (e: Exception) {
                Log.e("DownloadHandler", "Failed streaming download file", e)
                repository.updateProgress(logId, 0f, "FAILED")
            } finally {
                FileSharingManager.removeActiveTransfer(logId)
            }
        }
    }

    private fun handleUpload(
        outputStream: OutputStream,
        request: HttpRequest,
        inputStream: InputStream,
        clientIp: String,
        userAgent: String
    ) {
        val params = parseQueryMap(request.query)
        
        // Password verification check
        if (!authenticatePin(clientIp, request, params)) {
            val errorMsg = getAuthErrorMessage(clientIp, request, params)
            sendErrorResponse(outputStream, 401, errorMsg)
            return
        }

        if (!request.method.equals("POST", ignoreCase = true)) {
            sendErrorResponse(outputStream, 405, "Method Not Allowed")
            return
        }

        val rawName = params["name"] ?: "upload_${System.currentTimeMillis()}"
        val rawDecoded = URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
        val fileName = java.io.File(rawDecoded).name ?: "upload"
        val size = params["size"]?.toLongOrNull() ?: 0L

        kotlinx.coroutines.runBlocking {
            val logId = repository.insert(
                TransferEntity(
                    fileName = fileName,
                    fileSize = size,
                    isUpload = true,
                    remoteDevice = "$clientIp ($userAgent)",
                    status = "TRANSFERRING"
                )
            )

            try {
                // Combine the buffered headers excess bytes with raw stream
                val uploadInputStream = object : InputStream() {
                    private var bufIdx = request.bodyStartOffset
                    private val bufEnd = request.bodyStartOffset + request.bodyStartLength
                    private val buf = request.bodyStartBuffer

                    override fun read(): Int {
                        return if (bufIdx < bufEnd) {
                            buf[bufIdx++].toInt() and 0xFF
                        } else {
                            inputStream.read()
                        }
                    }

                    override fun read(b: ByteArray): Int {
                        return read(b, 0, b.size)
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (b == null) throw NullPointerException()
                        if (off < 0 || len < 0 || len > b.size - off) throw IndexOutOfBoundsException()
                        if (len == 0) return 0

                        val availableBuf = bufEnd - bufIdx
                        return if (availableBuf > 0) {
                            val toCopy = java.lang.Math.min(len, availableBuf)
                            System.arraycopy(buf, bufIdx, b, off, toCopy)
                            bufIdx += toCopy
                            toCopy
                        } else {
                            inputStream.read(b, off, len)
                        }
                    }
                }

                val contentType = request.headers["content-type"] ?: ""
                val isMultipart = contentType.contains("multipart/form-data", ignoreCase = true) && contentType.contains("boundary=")
                
                var finalFileName = fileName
                var finalSize = size
                var finalStream: InputStream = uploadInputStream
                
                if (isMultipart) {
                    val boundaryToken = contentType.substringAfter("boundary=", "").substringBefore(";").trim()
                    if (boundaryToken.isNotEmpty()) {
                        val parsed = parseMultipartHeaders(uploadInputStream, boundaryToken)
                        if (parsed != null) {
                            if (!parsed.first.isNullOrEmpty()) {
                                finalFileName = java.io.File(parsed.first!!).name ?: "upload_file"
                            }
                            finalStream = parsed.second
                            finalSize = if (size > 0) size else 0L
                            
                            val currentTransfer = repository.getTransferById(logId)
                            if (currentTransfer != null) {
                                repository.update(
                                    currentTransfer.copy(
                                        fileName = finalFileName,
                                        fileSize = finalSize
                                    )
                                )
                            }
                        }
                    }
                }

                val savedFile = saveUploadedFilePrivately(logId, finalStream, finalSize) { bytesSaved ->
                    val progress = if (finalSize > 0) bytesSaved.toFloat() / finalSize else 0f
                    FileSharingManager.updateActiveTransfer(logId, progress)
                }

                if (savedFile != null) {
                    val resp = "{\"status\":\"success\"}".toByteArray(StandardCharsets.UTF_8)
                    sendJsonResponse(outputStream, 200, resp)
                    repository.updateProgress(logId, 1.0f, "SUCCESS")
                } else {
                    sendErrorResponse(outputStream, 500, "Internal Server Error - could not save file")
                    repository.updateProgress(logId, 0f, "FAILED")
                }
            } catch (e: Exception) {
                Log.e("UploadHandler", "Failed parsing upload bytes stream", e)
                sendErrorResponse(outputStream, 500, "Internal Server Error")
                repository.updateProgress(logId, 0f, "FAILED")
            } finally {
                FileSharingManager.removeActiveTransfer(logId)
            }
        }
    }

    private fun saveUploadedFilePrivately(
        logId: Long,
        inputStream: InputStream,
        totalSize: Long,
        onProgress: (Long) -> Unit
    ): java.io.File? {
        val usableSpace = context.filesDir.usableSpace
        if (totalSize > 0 && totalSize > usableSpace) {
            Log.e("LocalFileSystemServer", "Insufficient storage space: required $totalSize, got $usableSpace")
            return null
        }

        val receivedDir = java.io.File(context.filesDir, "received")
        if (!receivedDir.exists()) {
            receivedDir.mkdirs()
        }
        val file = java.io.File(receivedDir, logId.toString())
        return try {
            java.io.FileOutputStream(file).use { os ->
                pipeStream(inputStream, os, totalSize, onProgress)
            }
            file
        } catch (e: Exception) {
            Log.e("LocalFileSystemServer", "Failed saving private file for log $logId", e)
            if (file.exists()) file.delete()
            null
        }
    }

    private fun parseMultipartHeaders(inputStream: InputStream, boundaryFirst: String): Pair<String?, InputStream>? {
        val boundaryLine = "--$boundaryFirst"
        val headerBuffer = java.io.ByteArrayOutputStream()
        var charRead: Int
        val lineB = java.io.ByteArrayOutputStream()
        var matchedInit = false
        val maxHeaderLineSize = 8192
        
        try {
            var lineBytesCount = 0
            while (inputStream.read().also { charRead = it } != -1) {
                lineB.write(charRead)
                lineBytesCount++
                if (lineBytesCount > maxHeaderLineSize) {
                    return null // Protect against infinite lines
                }
                if (charRead == '\n'.code) {
                    val s = lineB.toString("UTF-8").trim()
                    if (s.contains(boundaryLine)) {
                        matchedInit = true
                        break
                    }
                    lineB.reset()
                    lineBytesCount = 0
                }
            }
            if (!matchedInit) return null
            
            lineB.reset()
            var doubleCrLfFound = false
            var count = 0
            val maxHeaderSize = 8192
            val lastBytes = ByteArray(4)
            var lastIdx = 0
            
            while (count < maxHeaderSize) {
                val r = inputStream.read()
                if (r == -1) break
                headerBuffer.write(r)
                count++
                
                lastBytes[lastIdx] = r.toByte()
                lastIdx = (lastIdx + 1) % 4
                
                if (count >= 4) {
                    val b0 = lastBytes[lastIdx]
                    val b1 = lastBytes[(lastIdx + 1) % 4]
                    val b2 = lastBytes[(lastIdx + 2) % 4]
                    val b3 = lastBytes[(lastIdx + 3) % 4]
                    if (b0 == '\r'.toByte() && b1 == '\n'.toByte() && b2 == '\r'.toByte() && b3 == '\n'.toByte()) {
                        doubleCrLfFound = true
                        break
                    }
                }
                
                if (count >= 2) {
                    val b2 = lastBytes[(lastIdx + 2) % 4]
                    val b3 = lastBytes[(lastIdx + 3) % 4]
                    if (b2 == '\n'.toByte() && b3 == '\n'.toByte()) {
                        doubleCrLfFound = true
                        break
                    }
                }
            }
            if (!doubleCrLfFound) return null
            
            val headerText = headerBuffer.toString("UTF-8")
            var filename: String? = null
            val lines = headerText.split("\n")
            for (line in lines) {
                val trimLine = line.trim()
                if (trimLine.lowercase(Locale.ROOT).startsWith("content-disposition:")) {
                    val fnIdx = trimLine.indexOf("filename=")
                    if (fnIdx != -1) {
                        var fn = trimLine.substring(fnIdx + 9).trim()
                        if (fn.startsWith("\"") && fn.endsWith("\"")) {
                            fn = fn.substring(1, fn.length - 1)
                        } else if (fn.startsWith("'") && fn.endsWith("'")) {
                            fn = fn.substring(1, fn.length - 1)
                        }
                        filename = fn
                    }
                }
            }
            
            val boundaryMarker = "\r\n--$boundaryFirst".toByteArray(StandardCharsets.UTF_8)
            
            class BufferedBoundaryInputStream(
                private val raw: InputStream,
                private val boundary: ByteArray
            ) : java.io.InputStream() {
                private val buffer = ByteArray(128 * 1024) // 128KB high-speed buffer
                private var head = 0
                private var tail = 0
                private var eof = false
                private var boundaryFound = false
                private val m = boundary.size

                private fun fill() {
                    if (eof || boundaryFound) return
                    
                    if (head > 0) {
                        val len = tail - head
                        if (len > 0) {
                            System.arraycopy(buffer, head, buffer, 0, len)
                        }
                        head = 0
                        tail = len
                    }
                    
                    if (tail < buffer.size) {
                        val space = buffer.size - tail
                        val read = raw.read(buffer, tail, space)
                        if (read == -1) {
                            eof = true
                        } else if (read > 0) {
                            tail += read
                        }
                    }
                }

                private fun findBoundary(): Int {
                    val endSearchIdx = tail - m
                    for (i in head..endSearchIdx) {
                        var found = true
                        for (j in 0 until m) {
                            if (buffer[i + j] != boundary[j]) {
                                found = false
                                break
                            }
                        }
                        if (found) {
                            return i
                        }
                    }
                    return -1
                }

                override fun read(): Int {
                    val one = ByteArray(1)
                    val r = read(one, 0, 1)
                    return if (r == -1) -1 else one[0].toInt() and 0xFF
                }

                override fun read(bArray: ByteArray, off: Int, len: Int): Int {
                    if (len == 0) return 0
                    if (boundaryFound) return -1

                    while (true) {
                        fill()
                        
                        val bufferedCount = tail - head
                        if (bufferedCount == 0) {
                            if (eof) return -1
                            try { Thread.sleep(1) } catch (e: Exception) {}
                            continue
                        }

                        val matchIdx = findBoundary()
                        if (matchIdx != -1) {
                            val available = matchIdx - head
                            if (available > 0) {
                                val toRead = java.lang.Math.min(len, available)
                                System.arraycopy(buffer, head, bArray, off, toRead)
                                head += toRead
                                return toRead
                            } else {
                                head += m
                                boundaryFound = true
                                return -1
                            }
                        }

                        val available = if (eof) {
                            bufferedCount
                        } else {
                            bufferedCount - (m - 1)
                        }

                        if (available > 0) {
                            val toRead = java.lang.Math.min(len, available)
                            System.arraycopy(buffer, head, bArray, off, toRead)
                            head += toRead
                            return toRead
                        }

                        if (eof) {
                            if (bufferedCount > 0) {
                                val toRead = java.lang.Math.min(len, bufferedCount)
                                System.arraycopy(buffer, head, bArray, off, toRead)
                                head += toRead
                                return toRead
                            }
                            return -1
                        }

                        if (head > 0) {
                            System.arraycopy(buffer, head, buffer, 0, bufferedCount)
                            head = 0
                            tail = bufferedCount
                        }
                        val space = buffer.size - tail
                        val read = raw.read(buffer, tail, space)
                        if (read == -1) {
                            eof = true
                        } else {
                            tail += read
                        }
                    }
                }
            }

            val bodyStream = BufferedBoundaryInputStream(inputStream, boundaryMarker)
            return Pair(filename, bodyStream)
        } catch (e: Exception) {
            Log.e("LocalFileSystemServer", "Failed parsing multipart headers", e)
            return null
        }
    }

    private fun saveUploadedFile(
        fileName: String,
        inputStream: InputStream,
        totalSize: Long,
        onProgress: (Long) -> Unit
    ): Uri? {
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/LANDrop")
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { os ->
                        pipeStream(inputStream, os, totalSize, onProgress)
                    }
                    uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    null
                }
            } else null
        } else {
            val downloadDir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "LANDrop"
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val file = java.io.File(downloadDir, fileName)
            try {
                java.io.FileOutputStream(file).use { os ->
                    pipeStream(inputStream, os, totalSize, onProgress)
                }
                Uri.fromFile(file)
            } catch (e: Exception) {
                if (file.exists()) file.delete()
                null
            }
        }
    }

    private fun pipeStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalSize: Long,
        onProgress: (Long) -> Unit
    ) {
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        var totalBytesCopied = 0L
        var lastReportedTime = 0L

        while (true) {
            val bytesToRead = if (totalSize > 0) {
                java.lang.Math.min(buffer.size.toLong(), totalSize - totalBytesCopied).toInt()
            } else {
                buffer.size
            }
            if (totalSize > 0 && bytesToRead <= 0) break

            bytesRead = inputStream.read(buffer, 0, bytesToRead)
            if (bytesRead == -1) break
            
            outputStream.write(buffer, 0, bytesRead)
            totalBytesCopied += bytesRead

            // Continuous space validation during uploads to block system storage exhaustion DOS
            if (outputStream is java.io.FileOutputStream && totalBytesCopied % (10 * 1024 * 1024) == 0L) {
                val currentUsable = context.filesDir.usableSpace
                if (currentUsable < 20 * 1024 * 1024) { // Absolute 20MB emergency barrier
                    throw java.io.IOException("Device storage limit reached - operation cancelled safely")
                }
            }
            
            val now = System.currentTimeMillis()
            if (now - lastReportedTime >= 500L) {
                onProgress(totalBytesCopied)
                lastReportedTime = now
            }
        }
        if (totalSize > 0 && totalBytesCopied < totalSize) {
            throw java.io.IOException("Stream terminated prematurely. Copied $totalBytesCopied of expected $totalSize bytes.")
        }
        onProgress(totalBytesCopied) // Ensure 100% is updated
        outputStream.flush()
    }

    private fun parseQueryMap(query: String?): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isNullOrEmpty()) return result
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx != -1) {
                val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                result[key] = value
            } else {
                val key = URLDecoder.decode(pair, StandardCharsets.UTF_8.name())
                result[key] = ""
            }
        }
        return result
    }

    private fun escapeJson(input: String?): String {
        if (input == null) return ""
        val sb = java.lang.StringBuilder()
        for (element in input) {
            when (element) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '/' -> sb.append("\\/")
                '\b' -> sb.append("\\b")
                '\t' -> sb.append("\\t")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> sb.append(element)
            }
        }
        return sb.toString()
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast(".", "").lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    private fun getReceivedFiles(): List<TransferEntity> {
        val list = mutableListOf<TransferEntity>()
        val receivedDir = java.io.File(context.filesDir, "received")
        if (!receivedDir.exists() || !receivedDir.isDirectory) {
            return list
        }
        val dbEntries = kotlinx.coroutines.runBlocking { repository.getAllTransfersList() }
        for (entry in dbEntries) {
            if (entry.isUpload && entry.status == "SUCCESS") {
                val f = java.io.File(receivedDir, entry.id.toString())
                if (f.exists() && f.isFile) {
                    list.add(entry)
                }
            }
        }
        return list
    }

    private fun sendErrorResponse(outputStream: OutputStream, statusCode: Int, message: String) {
        try {
            val statusMap = mapOf(
                400 to "Bad Request",
                401 to "Unauthorized",
                404 to "Not Found",
                405 to "Method Not Allowed",
                416 to "Range Not Satisfiable",
                500 to "Internal Server Error"
            )
            val statusString = statusMap[statusCode] ?: "Error"
            
            val responseBytes = "{\"error\":\"$message\"}".toByteArray(StandardCharsets.UTF_8)
            val responseHeader = "HTTP/1.1 $statusCode $statusString\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                    "Content-Length: ${responseBytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
            outputStream.write(responseHeader.toByteArray(StandardCharsets.UTF_8))
            outputStream.write(responseBytes)
            outputStream.flush()
        } catch (e: Exception) {
            Log.e("LocalFileSystemServer", "Error sending error response", e)
        }
    }

    private fun sendJsonResponse(outputStream: OutputStream, statusCode: Int, responseBytes: ByteArray) {
        try {
            val statusMap = mapOf(
                200 to "OK",
                210 to "Partial Content",
                400 to "Bad Request",
                401 to "Unauthorized",
                404 to "Not Found",
                405 to "Method Not Allowed",
                500 to "Internal Server Error"
            )
            val statusString = statusMap[statusCode] ?: "OK"
            val responseHeader = "HTTP/1.1 $statusCode $statusString\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                    "Content-Length: ${responseBytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
            outputStream.write(responseHeader.toByteArray(StandardCharsets.UTF_8))
            outputStream.write(responseBytes)
            outputStream.flush()
        } catch (e: Exception) {
            Log.e("LocalFileSystemServer", "Error sending JSON response", e)
        }
    }

    private fun authenticatePin(clientIp: String, request: HttpRequest, params: Map<String, String>? = null): Boolean {
        if (!FileSharingManager.isPasswordEnabled.value) {
            return true
        }

        val reqPass = request.headers["x-lan-password"] 
            ?: params?.get("pin") 
            ?: parseQueryMap(request.query)["pin"]

        // If no PIN was provided on the request, just return false (unauthenticated)
        // without registering it as a failed passcode attempt.
        if (reqPass == null) {
            return false
        }

        // 1. Check lockout state
        val lockout = BruteForceControl.isLocked(clientIp)
        if (lockout is BruteForceControl.LockoutState.Locked) {
            return false
        }

        // 2. Format validation check (digits only, 4-8 range)
        if (reqPass.length !in 4..8 || !reqPass.all { it.isDigit() }) {
            // Count as a failed attempt to prevent brute force scan
            BruteForceControl.attemptLogin(clientIp, false)
            return false
        }

        // 3. Password match
        val correctPassword = FileSharingManager.serverPassword.value
        val isCorrect = (reqPass == correctPassword)

        val result = BruteForceControl.attemptLogin(clientIp, isCorrect)
        return result is BruteForceControl.AuthResult.Success
    }

    private fun getAuthErrorMessage(clientIp: String, request: HttpRequest, params: Map<String, String>? = null): String {
        val reqPass = request.headers["x-lan-password"] 
            ?: params?.get("pin") 
            ?: parseQueryMap(request.query)["pin"]

        val status = BruteForceControl.getIpStatus(clientIp)
        if (status.isLocked) {
            val sec = (status.remainingMs + 999) / 1000
            return "Locked out. Try again in $sec seconds."
        }
        
        if (reqPass != null) {
            return "Invalid PIN. Tries left: ${status.triesLeft}"
        }
        return "Invalid PIN"
    }

    private fun getAuthJsonResponse(clientIp: String, request: HttpRequest, params: Map<String, String>? = null): String {
        val reqPass = request.headers["x-lan-password"] 
            ?: params?.get("pin") 
            ?: parseQueryMap(request.query)["pin"]

        val status = BruteForceControl.getIpStatus(clientIp)
        if (status.isLocked) {
            val sec = (status.remainingMs + 999) / 1000
            val escapedMsg = "Locked out. Try again in $sec seconds."
            return "{\"error\":\"$escapedMsg\",\"locked\":true,\"remainingMs\":${status.remainingMs},\"triesLeft\":${status.triesLeft}}"
        }
        
        if (reqPass != null) {
            val escapedMsg = "Invalid PIN. Tries left: ${status.triesLeft}"
            return "{\"error\":\"$escapedMsg\",\"locked\":false,\"remainingMs\":0,\"triesLeft\":${status.triesLeft}}"
        }
        return "{\"error\":\"Invalid PIN\",\"locked\":false,\"remainingMs\":0,\"triesLeft\":${status.triesLeft}}"
    }
}

object BruteForceControl {
    private val lock = Any()
    
    class IpAuthRecord(
        var attempts: Int = 0,
        var lockedUntil: Long = 0,
        var lastActivity: Long = System.currentTimeMillis()
    )
    
    class IpStatus(
        val isLocked: Boolean,
        val remainingMs: Long,
        val triesLeft: Int,
        val attempts: Int
    )
    
    private val records = mutableMapOf<String, IpAuthRecord>()
    
    fun getIpStatus(ip: String): IpStatus {
        synchronized(lock) {
            val record = records[ip] ?: return IpStatus(false, 0, 5, 0)
            val now = System.currentTimeMillis()
            val isLocked = record.lockedUntil > now
            val remainingMs = if (isLocked) record.lockedUntil - now else 0L
            val triesLeft = when {
                record.attempts < 5 -> 5 - record.attempts
                record.attempts < 10 -> 10 - record.attempts
                record.attempts < 15 -> 15 - record.attempts
                record.attempts < 20 -> 20 - record.attempts
                record.attempts < 25 -> 25 - record.attempts
                else -> 0
            }
            return IpStatus(isLocked, remainingMs, triesLeft, record.attempts)
        }
    }
    
    fun cleanUpExpired() {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val iterator = records.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                // Inactive for more than 1 hour -> safe to remove to prevent memory leaks
                if (now - entry.value.lastActivity > 3600000) {
                    iterator.remove()
                }
            }
        }
    }
    
    fun attemptLogin(ip: String, isCorrect: Boolean): AuthResult {
        synchronized(lock) {
            cleanUpExpired()
            val record = records.getOrPut(ip) { IpAuthRecord() }
            val now = System.currentTimeMillis()
            record.lastActivity = now
            
            if (record.lockedUntil > now) {
                return AuthResult.Locked(record.lockedUntil - now)
            }
            
            if (isCorrect) {
                record.attempts = 0
                record.lockedUntil = 0
                return AuthResult.Success
            } else {
                record.attempts++
                val lockDurationMs = getLockoutDurationMs(record.attempts)
                if (lockDurationMs > 0) {
                    record.lockedUntil = now + lockDurationMs
                    return AuthResult.Locked(lockDurationMs)
                }
                return AuthResult.Failed
            }
        }
    }
    
    fun isLocked(ip: String): LockoutState {
        synchronized(lock) {
            val record = records[ip] ?: return LockoutState.NotLocked
            val now = System.currentTimeMillis()
            record.lastActivity = now
            if (record.lockedUntil > now) {
                return LockoutState.Locked(record.lockedUntil - now)
            }
            return LockoutState.NotLocked
        }
    }
    
    private fun getLockoutDurationMs(attempts: Int): Long {
        return when {
            attempts >= 25 -> 3600000L      // 1 hour
            attempts >= 20 -> 1800000L      // 30 minutes
            attempts >= 15 -> 600000L       // 10 minutes
            attempts >= 10 -> 120000L       // 2 minutes
            attempts >= 5 -> 30000L         // 30 seconds
            else -> 0L
        }
    }
    
    sealed class AuthResult {
        object Success : AuthResult()
        object Failed : AuthResult()
        class Locked(val remainingMs: Long) : AuthResult()
    }
    
    sealed class LockoutState {
        object NotLocked : LockoutState()
        class Locked(val remainingMs: Long) : LockoutState()
    }
}
