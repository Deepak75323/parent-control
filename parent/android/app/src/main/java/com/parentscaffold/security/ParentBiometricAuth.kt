package com.parentscaffold.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Promise

/**
 * Strong-biometric prompt for the parent clock lock. Does not accept the device PIN.
 */
object ParentBiometricAuth {
    fun authenticate(activity: FragmentActivity, promise: Promise) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val can = BiometricManager.from(activity).canAuthenticate(authenticators)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            promise.resolve("unavailable")
            return
        }

        var settled = false
        fun settle(value: String) {
            if (settled) return
            settled = true
            promise.resolve(value)
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt =
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        settle("succeeded")
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                            errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                            errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE
                        ) {
                            settle("unavailable")
                            return
                        }
                        settle("failed")
                    }

                    override fun onAuthenticationFailed() {
                        // Keep the sheet open for another try; do not settle yet.
                    }
                },
            )

        val info =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock")
                .setSubtitle("Use your fingerprint")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(authenticators)
                .build()

        activity.runOnUiThread { prompt.authenticate(info) }
    }
}
