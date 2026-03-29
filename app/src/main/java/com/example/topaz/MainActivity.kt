package com.example.topaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var displayText by remember { mutableStateOf("Press the button to call Go!") }

    MaterialTheme {
        Scaffold(
                topBar = {
                    TopAppBar(
                            title = { Text("Topaz - Go + Compose") },
                            colors =
                                    TopAppBarDefaults.topAppBarColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.primaryContainer
                                    )
                    )
                }
        ) { paddingValues ->
            Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
            ) {
                Text(
                        text = displayText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                        onClick = {
                            // Call the Go function and update the text
                            val result = Backend.getMessage()
                            displayText = result
                        },
                        modifier = Modifier.fillMaxWidth(0.6f)
                ) { Text("Call Go Function") }
            }
        }
    }
}

