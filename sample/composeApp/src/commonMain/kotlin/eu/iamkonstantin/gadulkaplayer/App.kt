package eu.iamkonstantin.gadulkaplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import eu.iamkonstantin.kotlin.gadulka.GadulkaPlayerState
import eu.iamkonstantin.kotlin.gadulka.rememberGadulkaLiveState
import eu.iamkonstantin.kotlin.gadulka.MediaControlListener
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
@Preview
fun App() {
    MaterialTheme {
        val url = remember {
            mutableStateOf("https://download.samplelib.com/wav/sample-12s.wav")
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AudioPlayer(audioUrl = url.value, 
            onFinished = {
                url.value = "https://samplelib.com/mp3/sample-6s.mp3"
            },
             onPrevious = {
                url.value = "https://download.samplelib.com/wav/sample-12s.wav"
            },
            
            )
        }
    }
}

@Composable
fun AudioPlayer(
    audioUrl: String,
    onFinished: () -> Unit,
    onPrevious: () -> Unit,
) {
    val gadulka = rememberGadulkaLiveState()

    gadulka.player.setOnMediaControlListener(object : MediaControlListener {
        override fun onNext() {
            onFinished()
            // No action needed here since we're already tracking state with LaunchedEffect
        }

        override fun onPrevious() {
            onPrevious()
        }
    })

    LaunchedEffect(audioUrl) {
            gadulka.player.play(audioUrl)
    }

    LaunchedEffect(gadulka.state.name, gadulka.position) {
        val marginOfErrorMs = 600

        if (gadulka.state.name != "PLAYING" &&
            gadulka.duration > 0 &&
            (gadulka.duration - gadulka.position) <= marginOfErrorMs
        ) {
            onFinished()
        }


    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row {
            Column {
                Text("Gadulka Demo", style = MaterialTheme.typography.headlineMedium)

                Text(getPlatform().name, style = MaterialTheme.typography.bodyMedium)

                Text(gadulka.state.name)

                Text("Volume: ${gadulka.volume}")

                Text("Position: ${gadulka.position / 1000}s / ${gadulka.duration / 1000}s")
            }
        }

        Row {
            Button(
                onClick = {
                    if (gadulka.state == GadulkaPlayerState.PAUSED) {
                        // resume from current position
                        gadulka.player.play()
                    } else {
                        // play something new
                        gadulka.player.play(
                            audioUrl
                        )
                    }
                }) {
                Text("Play")
            }
            Button(
                onClick = {
                    gadulka.player.pause()
                },
                enabled = gadulka.state == GadulkaPlayerState.PLAYING
            ) {
                Text("Pause")
            }
            Button(
                onClick = {
                    gadulka.player.stop()
                }) {
                Text("Stop")
            }
        }

        Row {
            VolumeSlider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                initialVolume = 1.0f
            ) { newVolume ->
                gadulka.player.setVolume(newVolume)
            }
        }
    }
}


@Composable
fun VolumeSlider(
    modifier: Modifier = Modifier,
    initialVolume: Float = 0.5f,
    onVolumeChange: (Float) -> Unit = {}
) {
    var volume by remember { mutableStateOf(initialVolume) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        // Triangle visual representation
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            val path = Path().apply {
                moveTo(0f, size.height) // Bottom-left corner
                lineTo(size.width * volume, size.height) // Bottom-right corner (scaled by volume)
                lineTo(0f, 0f) // Top-left corner
                close()
            }
            drawPath(path, color = Color.Blue)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Slider for volume control
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                onVolumeChange(it)
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
