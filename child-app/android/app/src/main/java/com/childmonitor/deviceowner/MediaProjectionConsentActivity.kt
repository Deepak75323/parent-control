package com.childmonitor.deviceowner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.childmonitor.capture.MediaProjectionConsentHelper
import com.childmonitor.capture.ScreenCaptureController
import com.childmonitor.capture.MediaProjectionLossHandler
import com.childmonitor.capture.MediaProjectionRecoveryManager
import com.childmonitor.capture.MediaProjectionSessionKeeper
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.capture.ScreenCaptureForegroundService
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.debug.PermissionDebugLogger
import com.childmonitor.deviceowner.DeviceOwnerManager

/**
 * MediaProjection consent — used during setup and on-demand from background streams.
 * Translucent, no child UI; accessibility can auto-tap "Start now" when armed.
 */
class MediaProjectionConsentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        PermissionDebugLogger.enter("MediaProjectionConsentActivity.onCreate")
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "MediaProjectionConsentActivity onCreate")
        @Suppress("DEPRECATION")
        startActivityForResult(ScreenCaptureController.buildConsentIntent(this), REQUEST_CODE)
        PermissionDebugLogger.exit("MediaProjectionConsentActivity.onCreate")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        PermissionDebugLogger.enter(
            "MediaProjectionConsentActivity.onActivityResult",
            "requestCode=$requestCode resultCode=$resultCode hasData=${data != null}",
        )
        super.onActivityResult(requestCode, resultCode, data)
        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "onActivityResult",
            "requestCode=$requestCode resultCode=$resultCode hasData=${data != null}",
        )
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // FGS startup uses CountDownLatch.await — must NOT run on the main thread
            // (onStartCommand also needs the main looper → deadlock → ANR).
            com.childmonitor.capture.ScreenFgsStartupGate.markReadyNow()
            DeviceOwnerManager.onDeviceOwnerEstablished(this)
            val app = applicationContext
            val consentResultCode = resultCode
            val consentData = data
            Thread {
                PermissionDebugLogger.enter("MediaProjectionConsentActivity.consentWorker")
                MediaProjectionTokenStore.save(app, consentResultCode, consentData)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val warmed = ScreenCaptureForegroundService.warmWithFreshConsent(
                        app,
                        consentResultCode,
                        consentData,
                    )
                    PcpFlowLogger.log(
                        PcpFlowLogger.Category.MEDIA_PROJECTION,
                        "warmWithFreshConsent after consent",
                        "warmed=$warmed validToken=${MediaProjectionTokenStore.hasValidToken(app)}",
                    )
                } else {
                    val projection = com.childmonitor.capture.ScreenCaptureController.obtainProjection(
                        app,
                        consentResultCode,
                        consentData,
                    )
                    PcpFlowLogger.log(
                        PcpFlowLogger.Category.MEDIA_PROJECTION,
                        "obtainProjection after consent",
                        "projection=${projection != null} validToken=${MediaProjectionTokenStore.hasValidToken(app)}",
                    )
                }
                MediaProjectionConsentHelper.signalConsentComplete()
                val granted = MediaProjectionTokenStore.hasValidToken(app)
                if (granted) {
                    MediaProjectionRecoveryManager.onRecoveryComplete(app, success = true, reason = "consent_granted")
                    MediaProjectionLossHandler.onProjectionRestored(app)
                } else {
                    MediaProjectionRecoveryManager.onRecoveryComplete(app, success = false, reason = "consent_incomplete")
                }
                PermissionDebugLogger.exit(
                    "MediaProjectionConsentActivity.consentWorker",
                    "granted=$granted",
                )
                runOnUiThread {
                    setResult(if (granted) RESULT_OK else RESULT_CANCELED)
                    finish()
                    overridePendingTransition(0, 0)
                    PermissionDebugLogger.exit("MediaProjectionConsentActivity.onActivityResult", "granted=$granted")
                }
            }.start()
            return
        }
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "consent denied or cancelled",
            "resultCode=$resultCode hasData=${data != null}",
        )
        MediaProjectionConsentHelper.signalConsentComplete()
        MediaProjectionRecoveryManager.onRecoveryComplete(
            applicationContext,
            success = false,
            reason = "consent_denied",
        )
        setResult(RESULT_CANCELED)
        finish()
        overridePendingTransition(0, 0)
        PermissionDebugLogger.exit("MediaProjectionConsentActivity.onActivityResult", "denied")
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val REQUEST_CODE = 9101

        fun startFromBackground(context: Context) {
            val intent = Intent(context, MediaProjectionConsentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
