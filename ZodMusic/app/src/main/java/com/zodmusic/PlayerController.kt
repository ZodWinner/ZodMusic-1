package com.zodmusic

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Ponte entre a interface e o serviço de reprodução. */
class PlayerController(private val context: Context) {
    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val isPlaying = MutableStateFlow(false)
    val current = MutableStateFlow<MediaItem?>(null)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerController.isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            current.value = mediaItem
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            isPlaying.value = c.isPlaying
            current.value = c.currentMediaItem
        }, ContextCompat.getMainExecutor(context))

        scope.launch {
            while (true) {
                controller?.let {
                    position.value = it.currentPosition
                    duration.value = it.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    fun play(items: List<MediaItem>, startIndex: Int) {
        val c = controller ?: return
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        scope.cancel()
    }
}
