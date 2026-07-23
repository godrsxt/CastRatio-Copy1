package com.castratio.app

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// Correct Jetpack Media3 Imports (Replaces com.google.android.exoplayer2)
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow

// Global state to share the chosen aspect ratio with the TV Screen
val aspectRatioState = MutableStateFlow(16f / 9f)

class MainActivity : ComponentActivity() {
    private var currentPresentation: CastPresentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    onOpenCastSettings = {
                        startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                    }
                )
            }
        }
        setupSecondaryDisplayScanner()
    }

    private fun setupSecondaryDisplayScanner() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { updateTVDisplay() }
            override fun onDisplayRemoved(displayId: Int) { updateTVDisplay() }
            override fun onDisplayChanged(displayId: Int) { updateTVDisplay() }
        }
        displayManager.registerDisplayListener(displayListener, null)
        updateTVDisplay()
    }

    private fun updateTVDisplay() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

        if (displays.isEmpty()) {
            currentPresentation?.dismiss()
            currentPresentation = null
        } else {
            val display = displays[0]
            if (currentPresentation?.display?.displayId != display.displayId) {
                currentPresentation?.dismiss()
                currentPresentation = CastPresentation(this, display).apply { show() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPresentation?.dismiss()
        currentPresentation = null
    }
}

@Composable
fun MainScreen(onOpenCastSettings: () -> Unit) {
    var currentRatio by remember { mutableStateOf(16f / 9f) }

    LaunchedEffect(currentRatio) {
        aspectRatioState.value = currentRatio
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Anycast Ratio Controller", style = MaterialTheme.typography.headlineSmall)
        
        Spacer(modifier = Modifier.height(48.dp))

        Button(onClick = onOpenCastSettings, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text("1. Connect to Anycast")
        }

        Spacer(modifier = Modifier.height(48.dp))
        Text("2. Set TV Aspect Ratio:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { currentRatio = 16f / 9f }) { Text("16:9") }
            Button(onClick = { currentRatio = 4f / 3f }) { Text("4:3") }
            Button(onClick = { currentRatio = 21f / 9f }) { Text("21:9") }
        }
    }
}

class CastPresentation(context: Context, display: Display) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                val ratio by aspectRatioState.collectAsState()
                
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(ratio)
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        CastVideoPlayer(
                            videoUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                        )
                    }
                }
            }
        }
        setContentView(composeView)
    }
}

@Composable
fun CastVideoPlayer(videoUri: Uri) {
    val context = LocalContext.current
    
    // Modern Media3 initialization
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = false 
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
