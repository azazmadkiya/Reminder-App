package com.example.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

enum class BiometricStatus {
    READY,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

class AppSecurityManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_security_prefs", Context.MODE_PRIVATE)

    // In-memory unlock state for current session
    private val _isLocked = MutableStateFlow(isAppLockEnabled())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun isAppLockEnabled(): Boolean {
        // Enabled by default to secure sensitive ledger and records upon app launch
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, true)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        if (!enabled) {
            _isLocked.value = false
        } else {
            _isLocked.value = true
        }
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun hasCustomPinSet(): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null)
        return !hash.isNullOrEmpty()
    }

    fun hasPinSet(): Boolean {
        return true // Default PIN (1234) or user-configured PIN always exists
    }

    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        val hashed = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hashed).apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null)
        val isValid = if (savedHash.isNullOrEmpty()) {
            pin == DEFAULT_PIN
        } else {
            hashPin(pin) == savedHash
        }
        if (isValid) {
            _isLocked.value = false
        }
        return isValid
    }

    fun unlockSession() {
        _isLocked.value = false
    }

    fun lockSession() {
        if (isAppLockEnabled()) {
            _isLocked.value = true
        }
    }

    fun disableAppLock() {
        prefs.edit()
            .putBoolean(KEY_APP_LOCK_ENABLED, false)
            .remove(KEY_PIN_HASH)
            .apply()
        _isLocked.value = false
    }

    fun getBiometricStatus(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    fun canUseBiometrics(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return canAuth != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    }

    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Verify fingerprint or face to unlock sensitive records",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        if (!canUseBiometrics(activity)) {
            val status = getBiometricStatus(activity)
            when (status) {
                BiometricStatus.NOT_ENROLLED -> onError("No fingerprint/face enrolled in device Settings. Please use PIN 1234 or register biometrics.")
                BiometricStatus.NO_HARDWARE -> onError("Biometric hardware not detected. Please use 4-digit PIN.")
                else -> onError("Biometric sensor unavailable. Please use PIN.")
            }
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    _isLocked.value = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Code 10/13 is user canceled
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric not recognized. Please try again or enter PIN.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription("Protects confidential ledger accounts, vouchers, and tax records.")
            .setNegativeButtonText("Use 4-Digit PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError("Could not start biometric prompt: ${e.message}")
        }
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val salt = "reminder_compliance_app_salt_2026"
        val bytes = md.digest((pin + salt).toByteArray(Charsets.UTF_8))
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    companion object {
        private const val KEY_APP_LOCK_ENABLED = "key_app_lock_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_PIN_HASH = "key_pin_hash"
        const val DEFAULT_PIN = "1234"
    }
}
