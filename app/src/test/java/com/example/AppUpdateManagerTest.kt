package com.example

import com.example.update.AppUpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testVersionComparison() {
        // Обычные переходы
        assertTrue(AppUpdateManager.isVersionNewer("0.1.1", "0.1.0"))
        assertTrue(AppUpdateManager.isVersionNewer("v0.1.1", "0.1.0"))
        assertTrue(AppUpdateManager.isVersionNewer("0.2.0", "0.1.9"))
        assertTrue(AppUpdateManager.isVersionNewer("1.0.0", "0.9.9"))

        // Переходы после 9.9.9
        assertTrue(AppUpdateManager.isVersionNewer("9.9.9a", "9.9.9"))
        assertTrue(AppUpdateManager.isVersionNewer("9.9.9b", "9.9.9a"))

        // Одинаковые версии
        assertFalse(AppUpdateManager.isVersionNewer("0.1.0", "0.1.0"))
        assertFalse(AppUpdateManager.isVersionNewer("v0.1.0", "0.1.0"))

        // Старые версии
        assertFalse(AppUpdateManager.isVersionNewer("0.0.9", "0.1.0"))
        assertFalse(AppUpdateManager.isVersionNewer("0.1.0", "0.1.1"))
    }
}
