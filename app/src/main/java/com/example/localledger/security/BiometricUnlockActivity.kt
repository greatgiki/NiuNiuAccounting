package com.example.localledger.security

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricUnlockActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    setResult(RESULT_OK)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val silent = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    val data = if (silent) null else Intent().putExtra(EXTRA_MESSAGE, errString.toString())
                    setResult(RESULT_CANCELED, data)
                    finish()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁牛牛记账")
                .setSubtitle("验证指纹或其他生物识别")
                .setNegativeButtonText("使用密码")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
    }
}
