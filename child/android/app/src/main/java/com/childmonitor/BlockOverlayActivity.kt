package com.childmonitor

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BlockOverlayActivity : AppCompatActivity() {

    private var requestSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_overlay)

        val packageName = intent.getStringExtra("packageName") ?: "app"
        val reason = intent.getStringExtra("blockReason") ?: "blocked"
        val appLabel = resolveLabel(packageName)

        val titleView = findViewById<TextView>(R.id.blockTitle)
        val messageView = findViewById<TextView>(R.id.blockMessage)
        val statusView = findViewById<TextView>(R.id.requestStatus)
        val askBtn = findViewById<Button>(R.id.btnAskParent)

        if (reason == "time_limit") {
            titleView.text = "Time limit exceeded"
            messageView.text = "Daily limit reached for $appLabel.\n\n($packageName)"
        } else {
            titleView.text = "App blocked"
            messageView.text = "This app is blocked by your parent.\n\n$appLabel ($packageName)"
        }

        askBtn.setOnClickListener {
            if (requestSent) return@setOnClickListener
            askBtn.isEnabled = false
            Thread {
                val ok = TimeExtensionUploader.requestMoreTime(
                    applicationContext,
                    packageName,
                    appLabel,
                    30,
                )
                runOnUiThread {
                    requestSent = true
                    statusView.visibility = View.VISIBLE
                    statusView.text = if (ok) {
                        "Request sent — waiting for parent approval"
                    } else {
                        "Could not send request. Try again later."
                    }
                    askBtn.isEnabled = !ok
                }
            }.start()
        }

        findViewById<Button>(R.id.btnGoHome).setOnClickListener {
            finish()
            moveTaskToBack(true)
        }
    }

    private fun resolveLabel(packageName: String): String {
        return try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
