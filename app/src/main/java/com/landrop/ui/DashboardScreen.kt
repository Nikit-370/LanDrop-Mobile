package com.landrop.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.landrop.data.TransferEntity
import com.landrop.server.ConnectedDevice
import com.landrop.server.QrCodeGenerator
import com.landrop.server.SharedFile
import com.landrop.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun rememberBatteryOptimizationState(context: Context): Boolean {
    var state by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = isBatteryOptimizationEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return state
}

fun isBatteryOptimizationEnabled(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        !pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRunning by viewModel.isServerRunning.collectAsState()
    val sharedFiles by viewModel.sharedFiles.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val serverPassword by viewModel.serverPassword.collectAsState()
    val isPasswordEnabled by viewModel.isPasswordEnabled.collectAsState()
    val isSavedPinInvalid by viewModel.isSavedPinInvalid.collectAsState()
    val customFolderUri by viewModel.customFolderUri.collectAsState()
    val devices by viewModel.connectedDevices.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val history by viewModel.transferHistory.collectAsState()
    val localIp by viewModel.localIpAddress.collectAsState()

    val serverMessage by viewModel.serverMessage.collectAsState()
    LaunchedEffect(serverMessage) {
        serverMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearServerMessage()
        }
    }

    var selectedTab by remember { mutableStateOf(0) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
            android.widget.Toast.makeText(context, "Added ${uris.size} file(s) for sharing.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.addFolders(listOf(uri))
            android.widget.Toast.makeText(context, "Added folder for sharing.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val dirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.saveCustomFolderUri(uri)
            android.widget.Toast.makeText(context, "Custom download folder saved", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicDarkBg),
        bottomBar = {
            BentoBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                sharedFilesCount = sharedFiles.size
            )
        },
        containerColor = CosmicDarkBg
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        
        // Reset scroll position smoothly to top when switching tabs
        LaunchedEffect(selectedTab) {
            scrollState.animateScrollTo(0, tween(300))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            BentoHeader(localIp = localIp, isRunning = isRunning, onRefreshIp = { viewModel.refreshIpAddress() })
            
            val isBatteryOptimized = rememberBatteryOptimizationState(context)
            if (isBatteryOptimized) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("battery_opt_warning_card"),
                    colors = CardDefaults.cardColors(containerColor = WarningRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, WarningRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Battery Optimization Warning",
                            tint = WarningRed
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery Optimization Active",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Battery optimization may stop LANDrop when the screen turns off.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(Settings.ACTION_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (ex: Exception) {
                                            // Fallback
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WarningRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp).testTag("disable_battery_optimization_button")
                            ) {
                                Text("Disable Optimization", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (isSavedPinInvalid) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = WarningRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, WarningRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning PIN invalid",
                            tint = WarningRed
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PIN Security Update Required",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Your current saved PIN does not meet the new security requirements (4–8 digits only). Please create or update your PIN in Settings.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 4 } + fadeIn(tween(220))).togetherWith(
                            slideOutHorizontally { width -> -width / 4 } + fadeOut(tween(220))
                        )
                    } else {
                        (slideInHorizontally { width -> -width / 4 } + fadeIn(tween(220))).togetherWith(
                            slideOutHorizontally { width -> width / 4 } + fadeOut(tween(220))
                        )
                    }
                },
                label = "TabContentAnimation",
                modifier = Modifier.fillMaxWidth()
            ) { targetTab ->
                when (targetTab) {
                    0 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Render beautiful Bento status bar inside Dashboard Tab 0
                            BentoServerStatusCard(
                                isRunning = isRunning,
                                ip = localIp,
                                port = serverPort,
                                passwordEnabled = isPasswordEnabled,
                                passwordCode = serverPassword,
                                onToggleServer = { viewModel.toggleServer() }
                            )

                            BentoGridRow(
                                isRunning = isRunning,
                                ip = localIp,
                                port = serverPort,
                                sharedFilesCount = sharedFiles.size,
                                historyCount = history.size,
                                onNavigateToTab = { selectedTab = it },
                                onToggleServer = { viewModel.toggleServer() }
                            )

                            BentoActiveTransfersCard(activeTransfers = activeTransfers)

                            BentoActiveListenersCard(devices = devices)
                        }
                    }
                    1 -> {
                        val receivedFiles = history.filter { it.isUpload && it.status == "SUCCESS" }
                        BentoSharedFilesTab(
                            sharedFiles = sharedFiles,
                            receivedFiles = receivedFiles,
                            isFileDownloaded = { viewModel.isFileDownloaded(it) },
                            onDownloadReceivedFile = { logId, fileName ->
                                viewModel.downloadFile(logId, fileName) { success ->
                                    if (success) {
                                        android.widget.Toast.makeText(context, "Saved to Downloads: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "Failed to save file.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onLaunchFilePicker = { fileLauncher.launch("*/*") },
                            onLaunchFolderPicker = { folderLauncher.launch(null) },
                            onRemoveFile = { viewModel.removeSharedFile(it) },
                            onClearAll = { viewModel.clearSharedFiles() }
                        )
                    }
                    2 -> BentoSettingsAndLogsTab(
                        port = serverPort,
                        password = serverPassword,
                        passwordEnabled = isPasswordEnabled,
                        customFolderUri = customFolderUri,
                        onLaunchDirPicker = { dirLauncher.launch(null) },
                        onClearCustomFolder = {
                            viewModel.clearCustomFolderUri()
                            android.widget.Toast.makeText(context, "Cleared custom folder, falling back to Downloads/LANDrop", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        history = history,
                        onSaveSettings = { p, pe, pwd -> 
                            viewModel.updateSettings(p, pe, pwd)
                            android.widget.Toast.makeText(context, "Configuration saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onClearHistory = { viewModel.clearAllHistory() }
                    )
                }
            }
        }
    }
}

@Composable
fun BentoHeader(localIp: String, isRunning: Boolean, onRefreshIp: () -> Unit) {
    val isOnLan = localIp.isNotEmpty() && localIp != "127.0.0.1" && localIp != "localhost"
    
    // Determine the border, background, and indicator colors dynamically based on isRunning and isOnLan
    val badgeBg = if (!isRunning) {
        Color.White.copy(alpha = 0.05f)
    } else if (isOnLan) {
        AccentGreen.copy(alpha = 0.12f)
    } else {
        WarningRed.copy(alpha = 0.12f)
    }

    val badgeBorder = if (!isRunning) {
        Color.White.copy(alpha = 0.12f)
    } else if (isOnLan) {
        AccentGreen.copy(alpha = 0.25f)
    } else {
        WarningRed.copy(alpha = 0.25f)
    }

    val indicatorColor = if (!isRunning) {
        Color(0xFF8E919F)
    } else if (isOnLan) {
        AccentGreen
    } else {
        WarningRed
    }

    val badgeText = if (!isRunning) {
        "Server Offline"
    } else if (isOnLan) {
        "Server Online"
    } else {
        "Server Online (No LAN)"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "LAN Drop",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "LOCAL NETWORK SHARING",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
        }

        Box(
            modifier = Modifier
                .background(badgeBg, RoundedCornerShape(99.dp))
                .border(1.dp, badgeBorder, RoundedCornerShape(99.dp))
                .clickable { onRefreshIp() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(indicatorColor, RoundedCornerShape(3.dp))
                )
                Text(
                     text = badgeText,
                     color = indicatorColor,
                     fontSize = 10.sp,
                     fontWeight = FontWeight.Bold,
                     letterSpacing = 0.25.sp
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh connection status",
                    tint = indicatorColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
fun BentoBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    sharedFilesCount: Int
) {
    NavigationBar(
        containerColor = CosmicSurface,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CosmicBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        val tabIcons = listOf(
            Icons.Default.Share,
            Icons.AutoMirrored.Filled.List,
            Icons.Default.Settings
        )
        val tabLabels = listOf(
            "Dashboard",
            "Shared ($sharedFilesCount)",
            "Control & Logs"
        )
        
        tabLabels.forEachIndexed { index, label ->
            val isSelected = selectedTab == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = tabIcons[index],
                        contentDescription = label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF001D36),
                    selectedTextColor = TextHeading,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = PrimaryBlue
                )
            )
        }
    }
}

@Composable
fun BentoServerStatusCard(
    isRunning: Boolean,
    ip: String,
    port: Int,
    passwordEnabled: Boolean,
    passwordCode: String,
    onToggleServer: () -> Unit
) {
    if (isRunning) {
        // Active Bento Card: Beautiful Light Blue container matching tailwind #D1E4FF
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            colors = CardDefaults.cardColors(containerColor = BentoLightBlueBg),
            shape = RoundedCornerShape(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Blur spot decorator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 16.dp, y = (-16).dp)
                        .size(96.dp)
                        .background(Color(0xFF0061A4).copy(alpha = 0.12f), RoundedCornerShape(48.dp))
                )

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF0061A4), RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = "SERVER ACTIVE",
                                color = Color(0xFF0061A4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                        }

                        // Version label moved to the top-right
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0061A4).copy(alpha = 0.1f), RoundedCornerShape(99.dp))
                                .border(1.dp, Color(0xFF0061A4).copy(alpha = 0.15f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v1.2.0",
                                color = Color(0xFF0061A4),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = ip,
                        color = Color(0xFF001D36),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Port: $port",
                            color = Color(0xFF001D36).copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (passwordEnabled && passwordCode.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF0061A4).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PIN: $passwordCode",
                                    color = Color(0xFF0061A4),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onToggleServer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB3261E),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text(
                                text = "Stop Server",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        val context = LocalContext.current
                        Button(
                            onClick = {
                                val shareUrl = "http://$ip:$port"
                                val shareMessage = buildString {
                                    append("Connect to my LAN Drop sharing portal to access or upload files over local Wi-Fi:\n\n")
                                    append(shareUrl)
                                    if (passwordEnabled && passwordCode.isNotEmpty()) {
                                        append("\n\nPIN: $passwordCode")
                                    }
                                }
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareMessage)
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Link")
                                context.startActivity(shareIntent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0061A4),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share address",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Offline Bento Card: Styled dark setup card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            colors = CardDefaults.cardColors(containerColor = BentoDarkCardBg),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, CosmicBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(WarningRed, RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "SERVER OFFLINE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Offline Mode",
                    color = TextHeading,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Start the network sharing portal to broadcast your files to nearby computers instantly.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onToggleServer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color(0xFF111318)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Start Network Server",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BentoGridRow(
    isRunning: Boolean,
    ip: String,
    port: Int,
    sharedFilesCount: Int,
    historyCount: Int,
    onNavigateToTab: (Int) -> Unit,
    onToggleServer: () -> Unit
) {
    var showLargeQrDialog by remember { mutableStateOf(false) }

    if (showLargeQrDialog && isRunning) {
        val url = "http://$ip:$port"
        val isLocalhost = ip == "127.0.0.1" || ip == "localhost" || ip.isEmpty()
        var largeQrBitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(url) {
            if (!isLocalhost) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    QrCodeGenerator.generateQrCode(url, size = 600)
                }.let { bitmap ->
                    largeQrBitmap = bitmap
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showLargeQrDialog = false },
            title = {
                Text(
                    text = if (isLocalhost) "Network Action Required" else "Scan to Connect",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLocalhost) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(WarningRed.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                .border(1.dp, WarningRed.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "No Network Connection Detected",
                                    tint = WarningRed,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Active Wi-Fi Connection Required",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "LANDrop requires your device to be connected to a local Wi-Fi router or have a portable hotspot active so other computers/devices on the same network can access your sharing portal. Please enable Wi-Fi in your system settings and connect.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Scan this QR code from another device on the same Wi-Fi network to open the file sharing portal instantly.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(12.dp)
                        ) {
                            if (largeQrBitmap != null) {
                                Image(
                                    bitmap = largeQrBitmap!!.asImageBitmap(),
                                    contentDescription = "Large QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = SecondaryBlue)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = url,
                            color = PrimaryBlue,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isLocalhost) {
                        Button(
                            onClick = {
                                val annotatedString = androidx.compose.ui.text.buildAnnotatedString { append(url) }
                                clipboardManager.setText(annotatedString)
                                android.widget.Toast.makeText(context, "Link copied!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Copy Link")
                        }
                    }
                    Button(
                        onClick = { showLargeQrDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoDarkCardBg, contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Text("Close")
                    }
                }
            },
            containerColor = CosmicSurface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(16.dp)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Column: QR Scan to Connect
        val isLocalhost = ip == "127.0.0.1" || ip == "localhost" || ip.isEmpty()
        Card(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .clickable { if (isRunning) showLargeQrDialog = true else onToggleServer() },
            colors = CardDefaults.cardColors(containerColor = BentoDarkCardBg),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, CosmicBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isRunning) {
                    if (isLocalhost) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(WarningRed.copy(alpha = 0.08f))
                                .border(1.dp, WarningRed.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "No Network Connection Detected",
                                    tint = WarningRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "NO LOCAL WI-FI",
                                    color = WarningRed,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 11.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tap to debug",
                                    color = TextMuted,
                                    fontSize = 7.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 9.sp
                                )
                            }
                        }
                    } else {
                        val url = "http://$ip:$port"
                        var qrBitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(url) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                QrCodeGenerator.generateQrCode(url)
                            }.let { bitmap ->
                                qrBitmap = bitmap
                            }
                        }

                        val currentBitmap = qrBitmap
                        if (currentBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = currentBitmap.asImageBitmap(),
                                    contentDescription = "Active sharing QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 2.dp)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(18.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "QR Offline",
                                tint = TextMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "PORTAL OFFLINE",
                                color = TextMuted.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (!isRunning) "TAP TO START" else if (isLocalhost) "TAP TO FIX" else "TAP TO ENLARGE",
                    color = if (!isRunning) PrimaryBlue else if (isLocalhost) WarningRed else AccentGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Right Column: Split Bento (2 stacked cards)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Mini Card: Shared Files Stats
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onNavigateToTab(1) },
                colors = CardDefaults.cardColors(containerColor = BentoMediumCardBg),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, CosmicBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "STORAGE",
                        color = PrimaryBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$sharedFilesCount Items",
                        color = TextHeading,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                    ) {
                        val fraction = if (sharedFilesCount > 0) 0.15f + (sharedFilesCount * 0.12f).coerceAtMost(0.85f) else 0f
                        if (fraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(PrimaryBlue, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            // Bottom Mini Card: Historical shared actions count
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onNavigateToTab(2) },
                colors = CardDefaults.cardColors(containerColor = BentoDarkCardBg),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, CosmicBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HISTORY",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$historyCount",
                            color = TextHeading,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Forward arrow",
                            tint = TextBody,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BentoActiveTransfersCard(
    activeTransfers: Map<Long, Float>
) {
    if (activeTransfers.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, CosmicBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE TRANSFERS",
                        color = TextHeading,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )

                    Box(
                        modifier = Modifier
                            .background(SecondaryBlue, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${activeTransfers.size} Active",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    activeTransfers.forEach { (id, progress) ->
                        BentoTransferRow(id = id, progress = progress)
                    }
                }
            }
        }
    }
}

@Composable
fun BentoTransferRow(id: Long, progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
            .border(1.dp, CosmicBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(PrimaryBlue, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NET",
                    color = Color(0xFF001D36),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Transfer #$id",
                        color = TextHeading,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(2.5.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(PrimaryBlue, RoundedCornerShape(2.5.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun BentoActiveListenersCard(
    devices: List<ConnectedDevice>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, CosmicBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "ACTIVE LISTENERS & CONNECTED DEVICES",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active laptop or PC sessions detected.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    devices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(0.75f)
                            ) {
                                Text(
                                    text = "💻",
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = device.ip,
                                        color = TextHeading,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = device.userAgent,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "Active",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoSharedFilesTab(
    sharedFiles: List<SharedFile>,
    receivedFiles: List<TransferEntity>,
    isFileDownloaded: (Long) -> Boolean,
    onDownloadReceivedFile: (Long, String) -> Unit,
    onLaunchFilePicker: () -> Unit,
    onLaunchFolderPicker: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var subTabSelected by remember { mutableStateOf(0) } // 0 = OUTGOING, 1 = INCOMING
    var sharedPage by remember(sharedFiles.size) { mutableStateOf(0) }
    var receivedPage by remember(receivedFiles.size) { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Switcher bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val options = listOf("SHARED OUTREG", "RECEIVED INCOMING")
            options.forEachIndexed { idx, label ->
                val active = subTabSelected == idx
                val cleanLabel = if (idx == 0) "SHARED OUTGOING" else "RECEIVED INCOMING"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Color(0xFF0061A4) else Color.Transparent)
                        .clickable { subTabSelected = idx }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cleanLabel,
                        color = if (active) Color.White else TextHeading.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        if (subTabSelected == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp)
                        .clickable { onLaunchFilePicker() },
                    colors = CardDefaults.cardColors(containerColor = BentoMediumCardBg),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, CosmicBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Files",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SELECT FILES",
                            color = TextHeading,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp)
                        .clickable { onLaunchFolderPicker() },
                    colors = CardDefaults.cardColors(containerColor = BentoDarkCardBg),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, CosmicBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "📁",
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SELECT FOLDER",
                            color = TextHeading,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Shared List Bento Container card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, CosmicBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENTLY SHARED",
                            color = TextHeading,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${sharedFiles.size} Items",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (sharedFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Selection placeholder",
                                    tint = TextMuted.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Your shared portal is empty.",
                                    color = TextHeading,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Select files or folders above to grant network access on your local devices.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Clear All Portal Items",
                                color = WarningRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { onClearAll() }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val itemsPerPage = 5
                        val totalSharedPages = (sharedFiles.size + itemsPerPage - 1) / itemsPerPage
                        val safeSharedPage = sharedPage.coerceIn(0, maxOf(0, totalSharedPages - 1))
                        val startSharedIndex = safeSharedPage * itemsPerPage
                        val endSharedIndex = minOf(startSharedIndex + itemsPerPage, sharedFiles.size)
                        val pageSharedItems = sharedFiles.subList(startSharedIndex, endSharedIndex)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pageSharedItems.forEach { file ->
                                BentoSharedFileRowItem(file = file, onRemove = { onRemoveFile(file.id) })
                            }

                            if (totalSharedPages > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = { if (sharedPage > 0) sharedPage-- },
                                        enabled = sharedPage > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.06f),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color.White.copy(alpha = 0.02f),
                                            disabledContentColor = TextMuted.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Previous Page",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Previous", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = "${safeSharedPage + 1} / $totalSharedPages",
                                        color = TextBody,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    FilledTonalButton(
                                        onClick = { if (sharedPage < totalSharedPages - 1) sharedPage++ },
                                        enabled = sharedPage < totalSharedPages - 1,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.06f),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color.White.copy(alpha = 0.02f),
                                            disabledContentColor = TextMuted.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Text("Next", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Next Page",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Incoming (Received) Files List Bento Container card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, CosmicBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECEIVED FILES",
                            color = TextHeading,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${receivedFiles.size} Items",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (receivedFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Received placeholder",
                                    tint = TextMuted.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No files received yet.",
                                    color = TextHeading,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Files sent from your computer via browser portal will appear here. Click Save to download them to your device.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))

                        val itemsPerPage = 5
                        val totalReceivedPages = (receivedFiles.size + itemsPerPage - 1) / itemsPerPage
                        val safeReceivedPage = receivedPage.coerceIn(0, maxOf(0, totalReceivedPages - 1))
                        val startReceivedIndex = safeReceivedPage * itemsPerPage
                        val endReceivedIndex = minOf(startReceivedIndex + itemsPerPage, receivedFiles.size)
                        val pageReceivedItems = receivedFiles.subList(startReceivedIndex, endReceivedIndex)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pageReceivedItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                                        .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(0.62f)
                                    ) {
                                        val ext = item.fileName.substringAfterLast('.', "").lowercase()
                                        val emoji = when (ext) {
                                            "png", "jpg", "jpeg", "webp", "gif" -> "🖼️"
                                            "mp4", "webm", "mkv", "mov" -> "🎬"
                                            "mp3", "wav", "m4a" -> "🎵"
                                            "pdf", "txt", "doc", "docx" -> "📄"
                                            "zip", "tar", "gz" -> "📦"
                                            else -> "📥"
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = emoji, fontSize = 18.sp)
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = item.fileName,
                                                color = TextHeading,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                                            Text(
                                                text = "${formatSize(item.fileSize)} • ${sdf.format(Date(item.timestamp))}",
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // Action Button
                                    val downloaded = isFileDownloaded(item.id)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (downloaded) {
                                            Text(
                                                text = "✓",
                                                color = AccentGreen,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Button(
                                            onClick = { onDownloadReceivedFile(item.id, item.fileName) },
                                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = "Save file",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .rotate(90f)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (downloaded) "Save Again" else "Save",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            if (totalReceivedPages > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = { if (receivedPage > 0) receivedPage-- },
                                        enabled = receivedPage > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.06f),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color.White.copy(alpha = 0.02f),
                                            disabledContentColor = TextMuted.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Previous Page",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Previous", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = "${safeReceivedPage + 1} / $totalReceivedPages",
                                        color = TextBody,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    FilledTonalButton(
                                        onClick = { if (receivedPage < totalReceivedPages - 1) receivedPage++ },
                                        enabled = receivedPage < totalReceivedPages - 1,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.06f),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color.White.copy(alpha = 0.02f),
                                            disabledContentColor = TextMuted.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Text("Next", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Next Page",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoSharedFileRowItem(
    file: SharedFile,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            val mime = file.mimeType ?: ""
            val ext = file.name.substringAfterLast('.', "").lowercase()
            val emoji = when {
                mime.startsWith("image") || ext in listOf("jpg", "png", "jpeg", "gif", "webp") -> "🖼️"
                mime.startsWith("video") || ext in listOf("mp4", "mkv", "webm", "mov") -> "🎬"
                mime.startsWith("audio") || ext in listOf("mp3", "wav", "m4a", "ogg") -> "🎵"
                else -> "📄"
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = file.name,
                    color = TextHeading,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSize(file.size),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove share item",
                tint = WarningRed,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun BentoSettingsAndLogsTab(
    port: Int,
    password: String,
    passwordEnabled: Boolean,
    customFolderUri: String,
    onLaunchDirPicker: () -> Unit,
    onClearCustomFolder: () -> Unit,
    history: List<TransferEntity>,
    onSaveSettings: (Int, Boolean, String) -> Unit,
    onClearHistory: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var editPort by remember(port) { mutableStateOf(port.toString()) }
    var usePassword by remember(passwordEnabled) { mutableStateOf(passwordEnabled) }
    var editPassword by remember(password) { mutableStateOf(password) }
    var currentPage by remember(history.size) { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bento config setup card
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, CosmicBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "LOCAL SERVER SETUP",
                        color = TextHeading,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = editPort,
                        onValueChange = { editPort = it },
                        label = { Text("Server Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = CosmicBorderStrong,
                            focusedLabelColor = PrimaryBlue,
                            unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextHeading,
                            unfocusedTextColor = TextBody,
                            focusedContainerColor = Color.White.copy(alpha = 0.02f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                            .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(0.8f)) {
                            Text(
                                text = "Require Verification PIN",
                                color = TextHeading,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Prompt browser sessions for security verification PIN",
                                color = TextMuted,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                        Switch(
                            checked = usePassword,
                            onCheckedChange = { usePassword = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SecondaryBlue,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BentoDarkCardBg
                            )
                        )
                    }

                    val isPinValid = editPassword.length in 4..8 && editPassword.all { it.isDigit() }
                    val isError = usePassword && !isPinValid
                    val canSave = !usePassword || isPinValid

                    if (usePassword) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = editPassword,
                            onValueChange = { newValue ->
                                val digitsOnly = newValue.filter { it.isDigit() }
                                editPassword = digitsOnly.take(8)
                            },
                            label = { Text("Access Entrance Verification PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = isError,
                            supportingText = {
                                Text(
                                    text = "PIN must contain 4–8 digits.",
                                    color = if (isError) WarningRed else TextMuted,
                                    fontSize = 11.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isError) WarningRed else PrimaryBlue,
                                unfocusedBorderColor = if (isError) WarningRed else CosmicBorderStrong,
                                errorBorderColor = WarningRed,
                                focusedLabelColor = if (isError) WarningRed else PrimaryBlue,
                                unfocusedLabelColor = TextMuted,
                                errorLabelColor = WarningRed,
                                focusedTextColor = TextHeading,
                                unfocusedTextColor = TextBody,
                                focusedContainerColor = Color.White.copy(alpha = 0.02f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (canSave) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                val targetPort = editPort.toIntOrNull() ?: 8080
                                onSaveSettings(targetPort, usePassword, editPassword)
                            }
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canSave) SecondaryBlue else CosmicBorderStrong,
                            contentColor = if (canSave) Color.White else TextMuted,
                            disabledContainerColor = CosmicBorderStrong,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (isPinValid || !usePassword) "Apply Configuration & Boot Server" else "Please enter a valid PIN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

        // Bento custom download directory card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, CosmicBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "DOWNLOAD DIRECTORY DESTINATION",
                    color = TextHeading,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (customFolderUri.isEmpty()) {
                    Text(
                        text = "Standard public Download directory (Downloads/LANDrop)",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                } else {
                    val decodedPath = try {
                        Uri.decode(customFolderUri)
                    } catch (e: Exception) {
                        customFolderUri
                    }
                    Text(
                        text = "Custom Folder: $decodedPath",
                        color = SecondaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onLaunchDirPicker() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SecondaryBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Choose Custom Folder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (customFolderUri.isNotEmpty()) {
                        Button(
                            onClick = { onClearCustomFolder() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BentoDarkCardBg,
                                contentColor = WarningRed
                            ),
                            border = BorderStroke(1.dp, CosmicBorder),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "Reset",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Bento registry logs card
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, CosmicBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRANSMISSION REGISTRY LOGS",
                            color = TextHeading,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        if (history.isNotEmpty()) {
                            Text(
                                text = "Wipe Logs",
                                color = WarningRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { onClearHistory() }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (history.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Your transmission workspace log history is crystal clean.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        val itemsPerPage = 5
                        val totalPages = (history.size + itemsPerPage - 1) / itemsPerPage
                        val safePage = currentPage.coerceIn(0, maxOf(0, totalPages - 1))
                        val startIndex = safePage * itemsPerPage
                        val endIndex = minOf(startIndex + itemsPerPage, history.size)
                        val pageItems = history.subList(startIndex, endIndex)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pageItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(0.78f)
                                    ) {
                                        val directionEmoji = if (item.isUpload) "📥" else "📤"
                                        val iconBg = if (item.isUpload) AccentGreen else PrimaryBlue
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(iconBg.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = directionEmoji,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = item.fileName,
                                                color = TextHeading,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${item.remoteDevice} • ${formatSize(item.fileSize)} • ${sdf.format(Date(item.timestamp))}",
                                                color = TextMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    val statusColor = when (item.status) {
                                        "SUCCESS" -> AccentGreen
                                        "FAILED" -> WarningRed
                                        else -> PrimaryBlue
                                    }
                                    Text(
                                        text = item.status,
                                        color = statusColor,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            if (totalPages > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = { if (currentPage > 0) currentPage-- },
                                        enabled = currentPage > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.06f),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color.White.copy(alpha = 0.02f),
                                            disabledContentColor = TextMuted.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Previous Page",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Previous", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = "${safePage + 1} / $totalPages",
                                        color = TextBody,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    FilledTonalButton(
                                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                        enabled = currentPage < totalPages - 1,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.06f),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color.White.copy(alpha = 0.02f),
                                            disabledContentColor = TextMuted.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Text("Next", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Next Page",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // Developer Profile Card
        val localUriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, CosmicBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ABOUT THE DEVELOPER",
                        color = TextHeading,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                    )
                    
                    Text(
                        text = "Made with ❤️ by Nikit Singh Kanyal",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { localUriHandler.openUri("https://github.com/Nikit-370/") },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("GitHub Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = { localUriHandler.openUri("https://nikit-370.github.io/") },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoDarkCardBg, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
                        ) {
                            Text("Portfolio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 Bytes"
    val kFraction = 1024L
    val sizes = listOf("Bytes", "KB", "MB", "GB", "TB")
    val digitGroup = (Math.log10(bytes.toDouble()) / Math.log10(kFraction.toDouble())).toInt()
    return String.format(Locale.US, "%.2f %s", bytes / Math.pow(kFraction.toDouble(), digitGroup.toDouble()), sizes[digitGroup])
}
