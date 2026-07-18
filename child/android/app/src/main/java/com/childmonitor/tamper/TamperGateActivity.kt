package com.childmonitor.tamper

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.childmonitor.AppBlockingAccessibilityService
import com.childmonitor.MonitoringPrefs
import com.childmonitor.R

class TamperGateActivity : AppCompatActivity() {

    private enum class Mode { PIN, CODE }

    private var mode = Mode.PIN
    private var busy = false
    private var tamperTarget: String? = null

    private lateinit var title: TextView
    private lateinit var modeLink: TextView
    private lateinit var fieldLabel: TextView
    private lateinit var input: EditText
    private lateinit var info: TextView
    private lateinit var status: TextView
    private lateinit var okBtn: Button
    private lateinit var requestCodeBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tamperTarget = intent.getStringExtra(TamperGateController.EXTRA_TARGET)
        if (MonitoringPrefs.isTamperUnlocked(this)) {
            val afterUnlock = intent.getStringExtra(TamperGateController.EXTRA_AFTER_UNLOCK)
            TamperGateController.openAfterUnlock(this, afterUnlock)
            finish()
            return
        }
        setContentView(R.layout.activity_tamper_gate)
        title = findViewById(R.id.tamperTitle)
        modeLink = findViewById(R.id.tamperModeLink)
        fieldLabel = findViewById(R.id.tamperFieldLabel)
        input = findViewById(R.id.tamperInput)
        info = findViewById(R.id.tamperInfo)
        status = findViewById(R.id.tamperStatus)
        okBtn = findViewById(R.id.btnTamperOk)
        requestCodeBtn = findViewById(R.id.btnTamperRequestCode)
        val homeBtn = findViewById<TextView>(R.id.btnTamperHome)

        modeLink.setOnClickListener {
            mode = if (mode == Mode.PIN) Mode.CODE else Mode.PIN
            applyMode()
            if (mode == Mode.CODE) {
                requestCodeFromServer()
            }
        }
        requestCodeBtn.setOnClickListener { requestCodeFromServer() }
        okBtn.setOnClickListener { submit() }
        homeBtn.setOnClickListener { goHomeSafely() }

        applyMode()
        requestAttemptInfo()
    }

    /**
     * finish()/moveTaskToBack alone reveals the Device Admin Settings page underneath.
     * Leave to the real launcher Home, and BACK out of any pending deactivate dialog first.
     */
    private fun goHomeSafely() {
        val a11y = AppBlockingAccessibilityService.instance
        // Dismiss system "Parent verification…" dialog (Cancel) and DeviceAdminAdd if still under us.
        a11y?.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
        )
        a11y?.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
        )
        try {
            val home =
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(home)
        } catch (_: Exception) {
        }
        a11y?.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME,
        )
        finish()
    }

    private fun applyMode() {
        input.text?.clear()
        status.visibility = View.GONE
        if (mode == Mode.PIN) {
            title.setText(R.string.tamper_gate_title_pin)
            modeLink.setText(R.string.tamper_gate_use_code)
            fieldLabel.setText(R.string.tamper_gate_pin_label)
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            info.text = getString(R.string.tamper_gate_info_pin)
            requestCodeBtn.visibility = View.VISIBLE
            requestCodeBtn.setText(R.string.tamper_gate_get_code)
        } else {
            title.setText(R.string.tamper_gate_title_code)
            modeLink.setText(R.string.tamper_gate_use_pin)
            fieldLabel.setText(R.string.tamper_gate_code_label)
            input.inputType = InputType.TYPE_CLASS_NUMBER
            info.setText(R.string.tamper_gate_info_code)
            requestCodeBtn.visibility = View.VISIBLE
            requestCodeBtn.setText(R.string.tamper_gate_get_code)
        }
    }

    private fun requestAttemptInfo() {
        Thread {
            when (val outcome = TamperApiClient.reportDisableAttemptDetailed(applicationContext, tamperTarget)) {
                    is TamperApiClient.AttemptOutcome.Success -> runOnUiThread {
                    status.visibility = View.VISIBLE
                    status.setTextColor(getColor(android.R.color.holo_green_dark))
                    if (!outcome.result.parentPinConfigured && mode == Mode.PIN) {
                        status.setTextColor(getColor(android.R.color.holo_orange_dark))
                        status.setText(R.string.tamper_gate_no_parent_password)
                    } else if (outcome.result.message == "offline") {
                        status.setTextColor(getColor(android.R.color.holo_orange_dark))
                        status.setText(R.string.tamper_gate_info_pin)
                    } else {
                        status.setText(R.string.tamper_gate_code_sent)
                    }
                }
                is TamperApiClient.AttemptOutcome.Failure -> runOnUiThread {
                    status.visibility = View.VISIBLE
                    status.setTextColor(getColor(android.R.color.holo_red_dark))
                    status.text = outcome.userMessageOverride
                        ?: getString(outcome.userMessageRes)
                }
            }
        }.start()
    }

    private fun requestCodeFromServer() {
        if (busy) return
        busy = true
        requestCodeBtn.isEnabled = false
        Thread {
            val outcome = TamperApiClient.reportDisableAttemptDetailed(applicationContext, tamperTarget)
            runOnUiThread {
                busy = false
                requestCodeBtn.isEnabled = true
                status.visibility = View.VISIBLE
                when (outcome) {
                    is TamperApiClient.AttemptOutcome.Success -> {
                        status.setTextColor(getColor(android.R.color.holo_green_dark))
                        status.setText(R.string.tamper_gate_code_sent)
                    }
                    is TamperApiClient.AttemptOutcome.Failure -> {
                        status.setTextColor(getColor(android.R.color.holo_red_dark))
                        status.text = outcome.userMessageOverride
                            ?: getString(outcome.userMessageRes)
                    }
                }
            }
        }.start()
    }

    private fun submit() {
        if (busy) return
        val value = input.text?.toString()?.trim().orEmpty()
        if (value.length < 4) {
            status.visibility = View.VISIBLE
            status.setTextColor(getColor(android.R.color.holo_red_dark))
            status.setText(R.string.tamper_gate_invalid_input)
            return
        }
        busy = true
        okBtn.isEnabled = false
        Thread {
            val outcome = if (mode == Mode.PIN) {
                TamperApiClient.verifyDetailed(applicationContext, pin = value, code = null, target = tamperTarget)
            } else {
                TamperApiClient.verifyDetailed(applicationContext, pin = null, code = value, target = tamperTarget)
            }
            runOnUiThread {
                busy = false
                okBtn.isEnabled = true
                when (outcome) {
                    is TamperApiClient.VerifyOutcome.Success -> {
                        if (outcome.result.verified) {
                            val untilMs = outcome.result.unlockExpiresAt?.toEpochMilli()
                                ?: (System.currentTimeMillis() + 10 * 60_000L)
                            applyUnlockAndFinish(untilMs)
                            return@runOnUiThread
                        }
                        status.visibility = View.VISIBLE
                        status.setTextColor(getColor(android.R.color.holo_red_dark))
                        status.setText(R.string.tamper_gate_wrong_secret)
                    }
                    is TamperApiClient.VerifyOutcome.Failure -> {
                        status.visibility = View.VISIBLE
                        status.setTextColor(getColor(android.R.color.holo_red_dark))
                        status.text = outcome.userMessageOverride
                            ?: getString(outcome.userMessageRes)
                    }
                }
            }
        }.start()
    }

    private fun applyUnlockAndFinish(unlockUntilMs: Long) {
        MonitoringPrefs.setTamperUnlockUntilMs(applicationContext, unlockUntilMs)
        val afterUnlock = intent.getStringExtra(TamperGateController.EXTRA_AFTER_UNLOCK)
        TamperGateController.openAfterUnlock(applicationContext, afterUnlock)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHomeSafely()
    }
}
