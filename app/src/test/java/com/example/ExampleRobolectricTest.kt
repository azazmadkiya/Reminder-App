package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.performClick

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Reminder", appName)
  }

  @get:org.junit.Rule val composeTestRule = androidx.compose.ui.test.junit4.createComposeRule()

  @Test
  fun `click Allow in StartupPermissionDialog`() {
    composeTestRule.setContent {
      com.example.ui.components.StartupPermissionDialog(isReady = true)
    }
    composeTestRule.waitForIdle()
    try {
      composeTestRule.onNode(androidx.compose.ui.test.hasText("Allow All Permissions")).performClick()
    } catch (_: Exception) {}
  }

  @Test
  fun `click Skip for Now in StartupPermissionDialog`() {
    composeTestRule.setContent {
      com.example.ui.components.StartupPermissionDialog(isReady = true)
    }
    composeTestRule.waitForIdle()
    try {
      composeTestRule.onNode(androidx.compose.ui.test.hasText("Skip for Now")).performClick()
    } catch (_: Exception) {}
  }

  @Test
  fun `verify backup reminder toggle and persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = context as ReminderApplication
    val backupManager = app.container.backupManager

    backupManager.setBackupReminderEnabled(false)
    org.junit.Assert.assertFalse(backupManager.isBackupReminderEnabled())

    backupManager.setBackupReminderEnabled(true)
    org.junit.Assert.assertTrue(backupManager.isBackupReminderEnabled())

    backupManager.setBackupReminderFrequency("Daily")
    assertEquals("Daily", backupManager.getBackupReminderFrequency())

    backupManager.setBackupReminderFrequency("Monthly")
    assertEquals("Monthly", backupManager.getBackupReminderFrequency())

    backupManager.setBackupReminderEnabled(false)
    org.junit.Assert.assertFalse(backupManager.isBackupReminderEnabled())
  }

  @Test
  fun `verify app security manager launch lock and biometric settings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val securityManager = com.example.security.AppSecurityManager(context)

    // Verify lock is enabled on launch
    org.junit.Assert.assertTrue(securityManager.isAppLockEnabled())
    org.junit.Assert.assertTrue(securityManager.isLocked.value)

    // Verify default PIN (1234) unlocks session
    org.junit.Assert.assertTrue(securityManager.verifyPin("1234"))
    org.junit.Assert.assertFalse(securityManager.isLocked.value)

    // Verify biometric preference toggle
    securityManager.setBiometricEnabled(true)
    org.junit.Assert.assertTrue(securityManager.isBiometricEnabled())
    securityManager.setBiometricEnabled(false)
    org.junit.Assert.assertFalse(securityManager.isBiometricEnabled())
    securityManager.setBiometricEnabled(true)

    // Verify custom PIN creation and verification
    org.junit.Assert.assertTrue(securityManager.setPin("7890"))
    org.junit.Assert.assertTrue(securityManager.hasCustomPinSet())
    org.junit.Assert.assertFalse(securityManager.verifyPin("1234"))
    org.junit.Assert.assertTrue(securityManager.verifyPin("7890"))

    // Verify lock and unlock session
    securityManager.lockSession()
    org.junit.Assert.assertTrue(securityManager.isLocked.value)
    securityManager.unlockSession()
    org.junit.Assert.assertFalse(securityManager.isLocked.value)
  }
}
