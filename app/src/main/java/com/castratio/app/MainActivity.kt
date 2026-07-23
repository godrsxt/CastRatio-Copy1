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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.MimeTypes

// Global states
val aspectRatioState = MutableStateFlow(16f / 9f)
val currentVideoUriState = MutableStateFlow<Uri?>(null)

class MainActivity : ComponentActivity() {
    private var currentPresentation: CastPresentation? = null
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            currentVideoUriState.value = it
            Toast.makeText(this, "Video selected. Playing on TV...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    onOpenCastSettings = {
                        startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                    },
                    onPickVideo = { pickVideoLauncher.launch("video/*") }
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
        currentPresentation?.releasePlayer()
        currentPresentation?.dismiss()
        currentPresentation = null
    }
}

@Composable
fun MainScreen(
    onOpenCastSettings: () -> Unit,
    onPickVideo: () -> Unit
) {
    var currentRatio by remember { mutableStateOf(16f / 9f) }

    LaunchedEffect(currentRatio) {
        aspectRatioState.value = currentRatio
    }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("CastRatio Player", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = onOpenCastSettings, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text("1. Connect to Anycast")
        }

        Button(
            onClick = onPickVideo,
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("2. Pick Video from Storage")
        }

        Text("3. Set Fallback Aspect Ratio:", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { currentRatio = 16f / 9f }) { Text("16:9") }
            Button(onClick = { currentRatio = 4f / 3f }) { Text("4:3") }
            Button(onClick = { currentRatio = 21f / 9f }) { Text("21:9") }
        }
    }
}

class CastPresentation(context: Context, display: Display) : Presentation(context, display) {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                val videoUri by currentVideoUriState.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoUri != null) {
                        AndroidView(
                            factory = { ctx ->
                                val trackSelector = DefaultTrackSelector(ctx)
                                player = ExoPlayer.Builder(ctx)
                                    .setTrackSelector(trackSelector)
                                    .build()

                                PlayerView(ctx).apply {
                                    playerView = this
                                    this.player = player
                                    useController = true
                                    hideControllerTimeoutMs = 3000

                                    val mediaItem = MediaItem.Builder()
                                        .setUri(videoUri)
                                        .setMimeType(MimeTypes.VIDEO_MP4)
                                        .build()

                                    player?.setMediaItem(mediaItem)
                                    player?.prepare()
                                    player?.playWhenReady = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .aspectRatio(aspectRatioState.collectAsState().value)
                                .fillMaxSize()
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "CastRatio Player\nPick a video on phone",
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        setContentView(composeView)
    }

    override fun onStop() {
        player?.playWhenReady = false
        super.onStop()
    }

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }
}
