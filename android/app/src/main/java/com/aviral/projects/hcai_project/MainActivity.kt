package com.aviral.projects.hcai_project

import android.content.pm.PackageManager
import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// ViewModel/Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

// Material Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HcaiProjectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: AudioAnalysisViewModel = viewModel()
                    MainScreen(viewModel)
                }
            }
        }
    }
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2A6F97),
    secondary = Color(0xFFA8DADC),
    tertiary = Color(0xFFE63946),
    background = Color(0xFFF1FAEE),
    surface = Color.White
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    )
)

@Composable
fun HcaiProjectTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        content = content
    )
}

// MainScreen.kt
@Composable
fun MainScreen(viewModel: AudioAnalysisViewModel) {
    val state by viewModel.uiState
    val context = LocalContext.current

    // Set context in ViewModel
    LaunchedEffect(Unit) {
        viewModel.setContext(context)
    }

    // Audio recording permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.toggleRecording()
        else viewModel.handleError("Microphone permission denied")
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                viewModel.handleFileUri(it)
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Upload Button
            Button(
                onClick = { filePickerLauncher.launch("audio/*") },
                modifier = Modifier.sizeIn(minWidth = 240.dp, minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Upload, contentDescription = "Upload audio")
                Spacer(Modifier.width(8.dp))
                Text("Upload Audio")
            }

            // Record Button
            Button(
                onClick = {
                    val permission = Manifest.permission.RECORD_AUDIO
                    if (ContextCompat.checkSelfPermission(
                            context,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.toggleRecording()
                    } else {
                        audioPermissionLauncher.launch(permission)
                    }
                },
                modifier = Modifier.sizeIn(minWidth = 240.dp, minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Record audio"
                )
                Spacer(Modifier.width(8.dp))
                Text(if (state.isRecording) "Stop Recording" else "Record Audio")
            }

            // Analyze Button and Loading Indicator
            if (state.showAnalyzeButton) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.onAnalyzeClicked() },
                        modifier = Modifier.sizeIn(minWidth = 240.dp, minHeight = 48.dp),
                        enabled = !state.isLoading
                    ) {
                        Text("Analyze Audio")
                    }

                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Error Dialog
        if (state.error != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onDismissResult() },
                title = { Text("Error") },
                text = { Text(state.error!!) },
                confirmButton = {
                    Button(onClick = { viewModel.onDismissResult() }) {
                        Text("OK")
                    }
                }
            )
        }

        // Result Dialog
        if (state.result != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onDismissResult() },
                title = {
                    Text(
                        text = when {
                            state.result!!.isScam && state.result!!.isDeepfake -> "⚠️ Double Warning"
                            state.result!!.isScam -> "⚠️ Scam Detected"
                            state.result!!.isDeepfake -> "⚠️ Deepfake Detected but not likely a scam"
                            else -> "✅ Safe"
                        },
                        color = when {
                            state.result!!.isScam || state.result!!.isDeepfake -> Color.Red
                            else -> Color.Green
                        }
                    )
                },
                text = {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Deepfake Detection: ${if (state.result!!.isDeepfake) "Likely Fake" else "Genuine"}")
                        Text("Confidence: ${"%.1f".format(state.result!!.deepfakeConfidence * 100)}%")
                        Spacer(Modifier.height(8.dp))
                        Text("Scam Detection: ${if (state.result!!.isScam) "Potential Scam" else "Safe Content"}")
                        Text("Confidence: ${"%.1f".format(state.result!!.scamConfidence * 100)}%")
                        Spacer(Modifier.height(16.dp))
                        Text("Transcript:", fontWeight = FontWeight.Bold)
                        Text(state.result!!.transcribedText)
                    }
                },
                confirmButton = {
                    Button(onClick = { viewModel.onDismissResult() }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}