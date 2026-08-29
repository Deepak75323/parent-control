package com.parentscaffold.capture

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.oney.WebRTCModule.EglUtils
import com.oney.WebRTCModule.WebRTCModule
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.MediaStreamTrack
import org.webrtc.VideoTrack
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records a remote WebRTC [VideoTrack] to MP4 (H.264), optionally muxing remote playback audio (AAC).
 */
object StreamVideoRecorder {
    private const val VIDEO_WIDTH = 1280
    private const val VIDEO_HEIGHT = 720
    private const val VIDEO_BIT_RATE = 2_500_000
    private const val VIDEO_FRAME_RATE = 24

    private var videoTrack: VideoTrack? = null
    private var eglRenderer: EglRenderer? = null
    private var glDrawer: GlRectDrawer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var includeRemoteAudio = false
    private val running = AtomicBoolean(false)

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    @Synchronized
    fun start(
        context: Context,
        streamType: String,
        peerConnectionId: Int,
        videoTrackId: String,
        webrtcModule: WebRTCModule,
        includeRemoteAudio: Boolean,
    ) {
        if (!isSupported()) {
            throw IllegalStateException("Video recording requires Android 5.0 or newer")
        }
        if (running.get()) return

        val track = webrtcModule.getTrack(peerConnectionId, videoTrackId)
        if (track == null || track.kind() != MediaStreamTrack.VIDEO_TRACK_KIND) {
            throw IllegalStateException("Remote video track not found")
        }
        val video = track as VideoTrack

        val cacheDir = context.cacheDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeType = streamType.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        val out =
            File(cacheDir, "pcp_${safeType}_$stamp.mp4").apply {
                parentFile?.mkdirs()
            }

        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        recorder.setVideoEncodingBitRate(VIDEO_BIT_RATE)
        recorder.setVideoFrameRate(VIDEO_FRAME_RATE)
        recorder.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT)
        recorder.setOutputFile(out.absolutePath)
        recorder.prepare()

        val eglContext =
            EglUtils.getRootEglBaseContext()
                ?: throw IllegalStateException("WebRTC renderer is not ready yet")
        val drawer = GlRectDrawer()
        val renderer = EglRenderer("pcp-recorder")
        renderer.init(eglContext, EglBase.CONFIG_PLAIN, drawer)
        renderer.createEglSurface(recorder.surface)

        this.includeRemoteAudio = includeRemoteAudio && StreamPlaybackAudioCapture.isSupported()
        if (this.includeRemoteAudio) {
            StreamPlaybackAudioCapture.start(context, streamType)
        }

        video.addSink(renderer)
        recorder.start()

        videoTrack = video
        eglRenderer = renderer
        glDrawer = drawer
        mediaRecorder = recorder
        tempFile = out
        running.set(true)
    }

    @Synchronized
    fun stop(context: Context, streamType: String): String {
        if (!running.getAndSet(false)) {
            throw IllegalStateException("No active recording")
        }

        val track = videoTrack
        val renderer = eglRenderer
        val drawer = glDrawer
        val recorder = mediaRecorder
        val file = tempFile
        val captureAudio = includeRemoteAudio
        includeRemoteAudio = false

        // Tear down on the EglRenderer thread. Calling release() from the bridge thread while
        // renderFrameOnRenderThread is still active nulls GlDrawer and crashes the app.
        try {
            if (track != null && renderer != null) {
                track.removeSink(renderer)
            }
        } catch (_: Exception) {
        }

        val shutdownLatch = CountDownLatch(1)
        if (renderer != null) {
            try {
                renderer.pauseVideo()
            } catch (_: Exception) {
            }
            try {
                renderer.releaseEglSurface {
                    try {
                        recorder?.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        recorder?.release()
                    } catch (_: Exception) {
                    }
                    try {
                        renderer.release()
                    } catch (_: Exception) {
                    }
                    try {
                        drawer?.release()
                    } catch (_: Exception) {
                    }
                    shutdownLatch.countDown()
                }
            } catch (_: Exception) {
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                }
                try {
                    recorder?.release()
                } catch (_: Exception) {
                }
                shutdownLatch.countDown()
            }
        } else {
            try {
                recorder?.stop()
            } catch (_: Exception) {
            }
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            shutdownLatch.countDown()
        }
        shutdownLatch.await(8, TimeUnit.SECONDS)

        videoTrack = null
        eglRenderer = null
        glDrawer = null
        mediaRecorder = null
        tempFile = null

        val mp4 = file ?: throw IllegalStateException("Recording file missing")
        if (!mp4.exists() || mp4.length() < 1024) {
            mp4.delete()
            if (captureAudio) {
                StreamPlaybackAudioCapture.stop()?.delete()
            }
            throw IllegalStateException("Nothing was captured — wait for live video before saving")
        }

        var finalMp4 = mp4
        var hadMuxedAudio = false
        if (captureAudio) {
            val pcm = StreamPlaybackAudioCapture.stop()
            if (pcm != null) {
                val muxed =
                    File(context.cacheDir, "pcp_mux_${mp4.name}").apply {
                        parentFile?.mkdirs()
                    }
                if (StreamMp4AudioMuxer.mux(mp4, pcm, muxed)) {
                    mp4.delete()
                    finalMp4 = muxed
                    hadMuxedAudio = true
                }
                pcm.delete()
            }
        }

        return saveMp4ToDownloads(context, finalMp4, streamType, hadMuxedAudio)
    }

    private fun saveMp4ToDownloads(
        context: Context,
        mp4: File,
        streamType: String,
        includesAudio: Boolean,
    ): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeType = streamType.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        val suffix = if (includesAudio) "_av" else ""
        val displayName = "pcp_${safeType}_$stamp$suffix.mp4"
        val bytes = mp4.readBytes()
        mp4.delete()

        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ParentalControl")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create download file")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("Could not write recording")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
    }
}
