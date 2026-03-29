package com.fiatjaf.topaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import backend.Backend

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

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
          Button(
              onClick = {
                // start the relay on localhost:4869
                try {
                  val dataDir = context.filesDir.absolutePath
                  Backend.startRelay(dataDir, "4869")
                  relayRunning = true
                  displayText = Backend.getRelayStatus()
                } catch (e: Exception) {
                  displayText = "error: ${e.message}"
                }
              },
              modifier = Modifier.weight(1f),
              enabled = !relayRunning,
          ) {
            Text("start")
          }

          Button(
              onClick = {
                Backend.stopRelay()
                relayRunning = false
                displayText = "relay stopped"
              },
              modifier = Modifier.weight(1f),
              enabled = relayRunning,
          ) {
            Text("stop")
          }
        }
      }
    }
  }
}
