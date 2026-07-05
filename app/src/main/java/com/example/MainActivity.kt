package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.install.PackageInstaller
import com.example.proot.RootfsBootstrapper
import com.example.service.ServerForegroundService
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppLogger
import com.example.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bootstrapper: RootfsBootstrapper
    private lateinit var installer: PackageInstaller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bootstrapper = RootfsBootstrapper(applicationContext)
        installer = PackageInstaller(applicationContext)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    MainControlPanel(
                        modifier = Modifier.padding(innerPadding),
                        bootstrapper = bootstrapper,
                        installer = installer,
                        onLaunchWebView = { isSandbox ->
                            val intent = Intent(this, WebViewActivity::class.java).apply {
                                putExtra(WebViewActivity.EXTRA_SANDBOX_MODE, isSandbox)
                            }
                            startActivity(intent)
                        },
                        onStartServer = {
                            val intent = Intent(this, ServerForegroundService::class.java).apply {
                                action = ServerForegroundService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                        },
                        onStopServer = {
                            val intent = Intent(this, ServerForegroundService::class.java).apply {
                                action = ServerForegroundService.ACTION_STOP
                            }
                            startService(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainControlPanel(
    modifier: Modifier = Modifier,
    bootstrapper: RootfsBootstrapper,
    installer: PackageInstaller,
    onLaunchWebView: (Boolean) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Service State
    val isServerRunning by ServerForegroundService.serverState.collectAsState()

    // Config options
    var selectedDistro by remember { mutableStateOf("Alpine") } // Alpine is ultra-fast and tiny!
    var useFastInstall by remember { mutableStateOf(true) }
    var customRootfsUrl by remember { mutableStateOf("") }
    var isSettingsExpanded by remember { mutableStateOf(false) }

    // Bootstrapper states
    val bootstrapProgress by bootstrapper.progress.collectAsState()
    val bootstrapStatus by bootstrapper.status.collectAsState()
    var isSettingUp by remember { mutableStateOf(false) }
    var setupFinished by remember { mutableStateOf(bootstrapper.isSetupComplete()) }

    // Installer states
    val installProgress by installer.progress.collectAsState()
    val installStatus by installer.status.collectAsState()
    val isInstalling by installer.isInstalling.collectAsState()
    var installFinished by remember { mutableStateOf(installer.isInstalled()) }

    // Logger states
    val logs by AppLogger.logs.collectAsState()
    val lastLogLine by AppLogger.lastLogLine.collectAsState()
    var showLogs by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val copyLogsToClipboard: () -> Unit = {
        if (logs.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
            Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No logs to copy", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission States
    var hasPermissions by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            Toast.makeText(context, "Permissions granted successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notifications are required for the server supervisor.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(isServerRunning) {
        // Automatically check if setup and install completed state changed
        setupFinished = bootstrapper.isSetupComplete()
        installFinished = installer.isInstalled()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Header
        AppHeader()

        // Instant Local Sandbox Card
        InstantSandboxCard(onLaunchSandbox = { onLaunchWebView(true) })

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Permission panel if missing
            if (!hasPermissions) {
                item {
                    PermissionCard(onRequestPermissions = {
                        permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                    })
                }
            }

            // Linux environment settings / configuration panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSettingsExpanded) Color(0xFF332D41) else Color(0xFF2B2930)),
                    border = BorderStroke(1.dp, if (isSettingsExpanded) Color(0x4DD0BCFF) else Color(0x4D49454F))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSettingsExpanded = !isSettingsExpanded },
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Config",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Container Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isSettingsExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Toggle",
                                tint = Color.Gray
                            )
                        }

                        AnimatedVisibility(
                            visible = isSettingsExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider(color = Color(0xFF49454F))
                                
                                Text(
                                    text = "Select Linux Distro:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { selectedDistro = "Alpine" }
                                    ) {
                                        RadioButton(
                                            selected = selectedDistro == "Alpine",
                                            onClick = { selectedDistro = "Alpine" },
                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                        )
                                        Text("Alpine (3.5MB, Tiny)", color = Color.White)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { selectedDistro = "Debian" }
                                    ) {
                                        RadioButton(
                                            selected = selectedDistro == "Debian",
                                            onClick = { selectedDistro = "Debian" },
                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                        )
                                        Text("Debian (40MB, Full)", color = Color.White)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Fast Precompiled Install",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Bypasses slow compilation of VS Code",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Switch(
                                        checked = useFastInstall,
                                        onCheckedChange = { useFastInstall = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                                    )
                                }

                                OutlinedTextField(
                                    value = customRootfsUrl,
                                    onValueChange = { customRootfsUrl = it },
                                    label = { Text("Custom rootfs URL (Optional)") },
                                    placeholder = { Text("Leave empty for official repo") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color(0xFF49454F),
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLabelColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }

            // Step 1: Bootstrap Rootfs Card
            item {
                StepCard(
                    title = "1. Bootstrap Environment",
                    description = "Download and extract the lightweight rootfs jail of $selectedDistro and the static proot binary.",
                    icon = Icons.Default.Download,
                    isActive = hasPermissions,
                    isFinished = setupFinished,
                    isLoading = isSettingUp,
                    progress = bootstrapProgress,
                    statusText = if (isSettingUp) bootstrapStatus else if (setupFinished) "Environment is boots-ready!" else "Needs Setup",
                    buttonText = "Bootstrap",
                    onAction = {
                        scope.launch {
                            isSettingUp = true
                            AppLogger.clear()
                            val ok = bootstrapper.bootstrap(
                                distro = selectedDistro,
                                customRootfsUrl = if (customRootfsUrl.isBlank()) null else customRootfsUrl
                            )
                            isSettingUp = false
                            setupFinished = ok
                            if (ok) {
                                Toast.makeText(context, "Bootstrap complete!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Bootstrap failed. Check logs.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            // Step 2: Install code-server and git
            item {
                StepCard(
                    title = "2. Install Server Packages",
                    description = "Install git, curl, ca-certificates, and VS Code (code-server) inside the container.",
                    icon = Icons.Default.InstallMobile,
                    isActive = setupFinished && !isSettingUp,
                    isFinished = installFinished,
                    isLoading = isInstalling,
                    progress = installProgress,
                    statusText = if (isInstalling) installStatus else if (installFinished) "VS Code is installed!" else "Pending Packages Installation",
                    buttonText = "Install Packages",
                    onAction = {
                        scope.launch {
                            val ok = installer.installPackages(
                                distro = selectedDistro,
                                useFastInstall = useFastInstall
                            )
                            installFinished = ok
                            if (ok) {
                                Toast.makeText(context, "Installation Successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Installation Failed. See logs.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            // Step 3: Run Server and load IDE
            item {
                ServerStateCard(
                    isInstalled = installFinished,
                    isServerRunning = isServerRunning,
                    onStartServer = onStartServer,
                    onStopServer = onStopServer,
                    onOpenIDE = { onLaunchWebView(false) }
                )
            }

            // Active logs header
            if (lastLogLine.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showLogs = !showLogs },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Terminal",
                                tint = Color(0xFF00FF00),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Container Output",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { copyLogsToClipboard() }, enabled = logs.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy logs",
                                    tint = if (logs.isNotEmpty()) Color(0xFFD0BCFF) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(
                                imageVector = if (showLogs) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Expand",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Live terminal logger
            if (showLogs && lastLogLine.isNotBlank()) {
                item {
                    TerminalConsole(logs = logs)
                }
            } else if (!showLogs && lastLogLine.isNotBlank()) {
                item {
                    Text(
                        text = "Last output: $lastLogLine",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFD0BCFF),
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B2930), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .clickable { showLogs = true }
                    )
                }
            }
        }
    }
}

@Composable
fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFD0BCFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = "VS Code",
                tint = Color(0xFF381E72),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "VS Code Server",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE6E1E5),
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "proot-distro local sandbox",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCAC4D0)
            )
        }
    }
}

@Composable
fun PermissionCard(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1E1E)),
        border = BorderStroke(1.dp, Color(0xFF5E2E2E))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Warning",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notification Permission Needed",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Android requires notification authorization to prevent killing the server in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFCA5A5)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InstantSandboxCard(onLaunchSandbox: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("instant_sandbox_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        border = BorderStroke(1.dp, Color(0x4D49454F))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = "Fast Sandbox",
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Instant Code Sandbox",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No installation required! Launch an offline-ready VS Code Monaco playground immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCAC4D0)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onLaunchSandbox,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.testTag("launch_sandbox_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Launch", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StepCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    isFinished: Boolean,
    isLoading: Boolean,
    progress: Float,
    statusText: String,
    buttonText: String,
    onAction: () -> Unit
) {
    val containerColor = if (isLoading) Color(0xFF332D41) else Color(0xFF2B2930)
    val borderColor = if (isLoading) Color(0x4DD0BCFF) else Color(0x4D49454F)
    val contentAlpha = if (isActive || isFinished || isLoading) 1f else 0.4f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val iconBg = if (isFinished) Color(0xFF381E72) else Color.Transparent
                    val iconTint = if (isFinished) Color(0xFFD0BCFF) else if (isActive || isLoading) Color(0xFFD0BCFF) else Color(0xFF938F99)
                    
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFinished) Icons.Default.CheckCircle else icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = contentAlpha)
                    )
                }
                
                if (isFinished && !isLoading) {
                    Text(
                        text = "Ready",
                        color = Color(0xFFD0BCFF),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray.copy(alpha = contentAlpha)
            )

            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFF49454F)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onAction,
                        enabled = isActive && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFinished) Color(0xFF49454F) else Color(0xFFD0BCFF),
                            contentColor = if (isFinished) Color(0xFFE6E1E5) else Color(0xFF381E72),
                            disabledContainerColor = Color(0xFF49454F).copy(alpha = 0.5f),
                            disabledContentColor = Color(0xFFE6E1E5).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = if (isFinished) "Redo" else buttonText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServerStateCard(
    isInstalled: Boolean,
    isServerRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onOpenIDE: () -> Unit
) {
    val containerColor = if (isServerRunning) Color(0xFF332D41) else Color(0xFF2B2930)
    val borderColor = if (isServerRunning) Color(0x4DD0BCFF) else Color(0x4D49454F)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server_state_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeveloperMode,
                        contentDescription = "Server Status",
                        tint = if (isServerRunning) Color(0xFFD0BCFF) else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "3. Linux Server Control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isServerRunning) Color(0xFF381E72) else Color(0xFF49454F),
                    modifier = Modifier.padding(2.dp)
                ) {
                    Text(
                        text = if (isServerRunning) "RUNNING" else "OFFLINE",
                        color = if (isServerRunning) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Launches code-server on the loopback address. Start the server first, then tap Open Workspace to access the desktop IDE.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isServerRunning) {
                    Button(
                        onClick = onStartServer,
                        enabled = isInstalled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72),
                            disabledContainerColor = Color(0xFF49454F).copy(alpha = 0.5f),
                            disabledContentColor = Color(0xFFE6E1E5).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("start_server_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Server", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = onStopServer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB3261E),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("stop_server_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Server", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onOpenIDE,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_ide_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open IDE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalConsole(logs: List<String>) {
    val listState = rememberLazyListState()

    // Auto scroll to the latest logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Color(0xFF1C1B1F), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (log.contains("[Error]") || log.contains("failed") || log.contains("Error:")) Color(0xFFFCA5A5) else Color(0xFFCAC4D0),
                    fontSize = 11.sp
                )
            }
        }
    }
}


