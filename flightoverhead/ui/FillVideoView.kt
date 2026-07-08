package com.flightoverhead.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView

/**
 * TextureView-based video player that:
 * - Fills its container completely (no letterbox)
 * - Participates in View alpha animations (unlike SurfaceView/VideoView)
 *   so fade-in/out works perfectly in sync with other views.
 */
class FillVideoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var mediaPlayer: MediaPlayer? = null
    private var videoUri: Uri? = null
    private var surfaceReady = false

    var onFirstFrameReady: (() -> Unit)? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun setVideoURI(uri: Uri) {
        videoUri = uri
        if (surfaceReady) setupMediaPlayer()
    }

    private fun setupMediaPlayer() {
        val uri = videoUri ?: return
        mediaPlayer?.release()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, uri)
                setSurface(Surface(surfaceTexture))
                setVolume(0f, 0f)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        post { onFirstFrameReady?.invoke() }
                    }
                    false
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Force fill — ignore video's native aspect ratio
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        surfaceReady = true
        if (videoUri != null) setupMediaPlayer()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        mediaPlayer?.release(); mediaPlayer = null; surfaceReady = false
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    val isPlaying get() = mediaPlayer?.isPlaying == true
    fun pause()  { mediaPlayer?.pause() }
    fun resume() { mediaPlayer?.start() }
    fun seekAndPlay() { mediaPlayer?.seekTo(0); mediaPlayer?.start() }
    fun stopPlayback() { mediaPlayer?.release(); mediaPlayer = null }
}
