package com.hark.sothudiary

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.AppSettings
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.MemoryFileStore
import com.hark.sothudiary.domain.drive.RestoreChoice
import com.hark.sothudiary.domain.drive.mergeAiSettings
import com.hark.sothudiary.domain.drive.mergeAppData
import com.hark.sothudiary.domain.drive.showSignInStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 3.1.0: first-launch Google sign-in screen and AI key restore from Drive. */
class V310Test {
    @Test
    fun signInStepShowsOnceOnAndroidOnly() {
        assertTrue(showSignInStep(true, AppSettings()))
        assertFalse(showSignInStep(false, AppSettings()), "desktop has no Drive sign-in")
        assertFalse(showSignInStep(true, AppSettings(signInStepDone = true)), "finished or skipped")
        assertFalse(showSignInStep(true, AppSettings(driveAccount = "a@gmail.com")), "already connected in Settings")
    }

    @Test
    fun mergeTakesDriveKeyWhenPhoneHasNone() {
        val drive = AppSettings(llmProvider = "openai", llmKeys = mapOf("openai" to "sk-1"), llmModels = mapOf("openai" to "gpt"), geminiApiKey = "g-1", geminiModel = "flash")
        val m = mergeAiSettings(AppSettings(lang = Lang.TA), drive)
        assertEquals("openai", m.llmProvider)
        assertEquals("sk-1", m.llmKeys["openai"])
        assertEquals("g-1", m.geminiApiKey)
        assertEquals("flash", m.geminiModel)
        assertEquals(Lang.TA, m.lang)
    }

    @Test
    fun mergeKeepsPhoneKey() {
        val phone = AppSettings(llmProvider = "", geminiApiKey = "phone", geminiModel = "auto")
        val drive = AppSettings(llmProvider = "openai", llmKeys = mapOf("openai" to "sk-1"), geminiApiKey = "drive")
        val m = mergeAiSettings(phone, drive)
        assertEquals("phone", m.geminiApiKey)
        assertEquals("", m.llmProvider, "phone's provider choice is kept")
        assertEquals("sk-1", m.llmKeys["openai"], "extra keys from Drive are kept for later")
        assertEquals("phone", mergeAppData(AppData(settings = phone), AppData(settings = drive)).settings.geminiApiKey)
    }

    @Test
    fun useDriveRestoresAiKeyAndKeepsSignInFlag() {
        val repo = JsonRepository(MemoryFileStore())
        repo.setSignInStepDone()
        repo.applyDriveBackup(AppData(settings = AppSettings(geminiApiKey = "g-drive", signInStepDone = false)), RestoreChoice.USE_DRIVE)
        assertEquals("g-drive", repo.data.settings.geminiApiKey)
        assertTrue(repo.data.settings.signInStepDone)
    }
}
