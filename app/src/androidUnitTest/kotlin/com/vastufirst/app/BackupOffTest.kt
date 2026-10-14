package com.vastufirst.app

import android.content.pm.ApplicationInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * ⭐ NOTHING IS BACKED UP OFF THE PHONE (14 Oct 2026). The privacy policy says every home is "saved only
 * on this device". With backups allowed, Android copied the homes, the plan photos and the settings to
 * the user's Google Drive. This test reads the app's own manifest, so turning backups back on fails
 * here first — and then the privacy words have to change with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupOffTest {
    @Test fun the_app_does_not_allow_android_backup() {
        val flags = RuntimeEnvironment.getApplication().applicationInfo.flags
        assertEquals(0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP, "allowBackup must stay false")
    }
}
