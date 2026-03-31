package com.fiatjaf.topaz

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import backend.Backend
import backend.StatsCallback
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.delay

// Kotlin implementation of the Go StatsCallback interface
class StatsCallbackImpl(
    private val onStatsUpdate:
        (eventCount: Long, activeSubscriptions: Long, connectedClients: Long) -> Unit,
) : StatsCallback {
    override fun onStatsUpdate(
        eventCount: Long,
        activeSubscriptions: Long,
        connectedClients: Long,
    ) {
        onStatsUpdate(eventCount, activeSubscriptions, connectedClients)
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TopazApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopazApp() {
    val context = LocalContext.current
    var displayText by remember { mutableStateOf("relay not started") }
    var relayRunning by remember { mutableStateOf(false) }
    var relayURLs by remember { mutableStateOf(listOf<String>()) }
    var eventCount by remember { mutableStateOf(0L) }
    var activeSubscriptions by remember { mutableStateOf(0L) }
    var connectedClients by remember { mutableStateOf(0L) }
    var statsCallback by remember { mutableStateOf<StatsCallback?>(null) }
    val port = "4869"

    fun copyToClipboard(url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("relay url", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "copied: $url", Toast.LENGTH_SHORT).show()
    }

    fun getRelayURLs(port: String): List<String> {
        val urls = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val interfaceName = networkInterface.name

                // skip loopback, down, and fake/virtual interfaces
                if (
                    !networkInterface.isUp ||
                    networkInterface.isLoopback ||
                    interfaceName.startsWith("docker") ||
                    interfaceName.startsWith("veth") ||
                    interfaceName.startsWith("br-") ||
                    interfaceName.startsWith("bridge") ||
                    interfaceName.startsWith("tun") ||
                    interfaceName.startsWith("tap") ||
                    interfaceName.startsWith("ip6") ||
                    interfaceName.startsWith("sit") ||
                    interfaceName.startsWith("dummy")
                ) {
                    continue
                }

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address.isLoopbackAddress) continue
                    val hostAddress = address.hostAddress
                    if (hostAddress != null) {
                        if (address is Inet4Address) {
                            urls.add("ws://$hostAddress:$port")
                        }
                        // ignore ipv6 for now since most are bogus
                    }
                }
            }
        } catch (_: Exception) {
        }
        // ensure localhost is always present
        val localhostUrl = "ws://localhost:$port"
        if (!urls.contains(localhostUrl)) {
            urls.add(0, localhostUrl)
        }
        return urls
    }

    fun startRelay() {
        try {
            val dataDir = context.filesDir.absolutePath
            statsCallback = StatsCallbackImpl { events, subs, clients ->
                eventCount = events
                activeSubscriptions = subs
                connectedClients = clients
            }
            Backend.startRelay(dataDir, port, statsCallback!!)
            relayRunning = true
            relayURLs = getRelayURLs(port)
            displayText = Backend.getRelayStatus()
            RelayService.start(context)
        } catch (e: Exception) {
            displayText = "error: ${e.message}"
        }
    }

    // poll relay status every 3 seconds
    LaunchedEffect(relayRunning) {
        while (relayRunning) {
            delay(3000)
            try {
                displayText = Backend.getRelayStatus()
            } catch (e: Exception) {
                // ignore errors during status polling
            }
        }
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                // permission granted, start the relay
                startRelay()
            }
        }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("topaz") },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Stats display
                if (relayRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatItem(label = "events", value = eventCount.toString())
                        StatItem(label = "clients", value = connectedClients.toString())
                        StatItem(label = "subscriptions", value = activeSubscriptions.toString())
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(0.8f),
                ) {
                    Button(
                        onClick = {
                            // check notification permission on Android 13+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val permission = Manifest.permission.POST_NOTIFICATIONS
                                if (
                                    ContextCompat.checkSelfPermission(context, permission) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    // permission already granted, start the relay
                                    startRelay()
                                } else {
                                    // Request permission
                                    notificationPermissionLauncher.launch(permission)
                                }
                            } else {
                                // no permission needed, start the relay
                                startRelay()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !relayRunning,
                    ) {
                        Text("start")
                    }

                    Button(
                        onClick = {
                            try {
                                Backend.stopRelay()
                            } catch (e: Exception) {
                                // ignore errors when stopping relay
                            }
                            relayRunning = false
                            relayURLs = emptyList()
                            eventCount = 0
                            activeSubscriptions = 0L
                            connectedClients = 0L
                            displayText = "relay stopped"
                        },
                        modifier = Modifier.weight(1f),
                        enabled = relayRunning,
                    ) {
                        Text("stop")
                    }
                }

                if (relayRunning && relayURLs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(relayURLs) { url ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth().clickable { copyToClipboard(url) },
                            ) {
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "copy",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
