package home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.PermissionsControllerFactory
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import dev.theolm.record.Record
import dev.theolm.record.config.OutputFormat
import dev.theolm.record.config.OutputLocation
import dev.theolm.record.config.RecordConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class HomeScreen : Screen {
    @Composable
    override fun Content() {
        Screen()
    }

    @Composable
    private fun Screen() {
        val factory: PermissionsControllerFactory = rememberPermissionsControllerFactory()
        val controller: PermissionsController =
            remember(factory) { factory.createPermissionsController() }
        val coroutineScope: CoroutineScope = rememberCoroutineScope()

        BindEffect(controller)

        val screenModel = rememberScreenModel { HomeScreenModel() }
        var uiState by screenModel.uiState

        LaunchedEffect(Unit) {
            Record.setConfig(
                RecordConfig(
                    outputLocation = OutputLocation.Cache,
                    outputFormat = OutputFormat.MPEG_4
                )
            )
        }

        var recording by remember { mutableStateOf(false) }
        var lastRecordingPath by remember { mutableStateOf<String?>(null) }
        var isPlaying by remember { mutableStateOf(false) }

        // Only start monitoring when recording is active
        LaunchedEffect(recording) {
            if (recording) {
//                Record.setSilenceTimeoutCallback { isSilenced ->
//                    if (isSilenced) {
//                        println("Recording is silenced")
//                        Record.stopRecording().also {
//                            println("Recording stopped. File saved at $it")
//                            lastRecordingPath = it
//                            recording = false
//                        }
//                    } else {
//                        println("Recording is active")
//                    }
//                }
//            } else {
                // Stop monitoring when not recording
                Record.stopMonitoring()
            }
        }

        // Update playing state periodically
        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                while (Record.isPlaying()) {
                    kotlinx.coroutines.delay(500) // Check every 500ms
                }
                isPlaying = false // Playing finished
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            content = {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (!controller.isPermissionGranted(Permission.RECORD_AUDIO)) {
                                    controller.providePermission(Permission.RECORD_AUDIO)
                                } else {
                                    if (recording) {
                                        Record.stopRecording().also {
                                            println("Recording stopped. File saved at $it")
                                            lastRecordingPath = it
                                        }
                                        recording = false
                                    } else {
                                        runCatching {
                                            Record.startRecording()
                                            recording = true
                                        }.onFailure {
                                            println("Error: $it")
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        val text = if (recording) {
                            "Stop Recording"
                        } else {
                            "Start Recording"
                        }
                        Text(text)
                    }

                    // Show play button only when we have a recorded file
                    lastRecordingPath?.let { filePath ->
                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        Button(
                            onClick = {
                                if (isPlaying) {
                                    Record.stopPlaying()
                                    isPlaying = false
                                } else {
                                    Record.playRecording(filePath)
                                    isPlaying = true
                                }
                            },
                            enabled = !recording // Disable while recording
                        ) {
                            val playText = if (isPlaying) {
                                "Stop Playing"
                            } else {
                                "Play Recording"
                            }
                            Text(playText)
                        }
                    }
                }
            }
        )
    }
}
