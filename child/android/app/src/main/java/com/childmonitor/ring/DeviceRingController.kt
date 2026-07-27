package com.childmonitor.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * Plays a looping alarm-style sound so a parent can locate a misplaced child phone.
 * Auto-stops after [DeviceRingPolicy.DURATION_MS]; a new start restarts the timer.
 */
object DeviceRingController {

    private const val TAG = "DeviceRing"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopRunnable: Runnable? = null
    private var savedAlarmVolume: Int = -1

    @Synchronized
    fun start(context: Context): Boolean {
        val app = context.applicationContext
        if (DeviceRingPolicy.shouldRestartIfAlreadyPlaying()) {
            stopInternal(app, restoreVolume = true)
        }

        return try {
            val uri =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: run {
                        Log.w(TAG, "no alarm/ringtone URI available")
                        return false
                    }

            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(app, uri)
                isLooping = true
                prepare()
                start()
            }
            player = mp

            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "childmonitor:device_ring").apply {
                    setReferenceCounted(false)
                    acquire(DeviceRingPolicy.wakeLockTimeoutMs())
                }

            val stop = Runnable { stop(app) }
            stopRunnable = stop
            mainHandler.postDelayed(stop, DeviceRingPolicy.DURATION_MS)
            Log.i(TAG, "ring started durationMs=${DeviceRingPolicy.DURATION_MS}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ring start failed", e)
            stopInternal(app, restoreVolume = true)
            false
        }
    }

    @Synchronized
    fun stop(context: Context) {
        stopInternal(context.applicationContext, restoreVolume = true)
    }

    private fun stopInternal(app: Context, restoreVolume: Boolean) {
        stopRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRunnable = null

        try {
            player?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "player stop failed", e)
        }
        player = null

        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "wakeLock release failed", e)
        }
        wakeLock = null

        if (restoreVolume && savedAlarmVolume >= 0) {
            try {
                val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            } catch (e: Exception) {
                Log.w(TAG, "volume restore failed", e)
            }
            savedAlarmVolume = -1
        }
    }
}
