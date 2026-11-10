package com.hark.sothudiary

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.AppSettings
import com.hark.sothudiary.data.Broker
import com.hark.sothudiary.data.DocKind
import com.hark.sothudiary.data.Interaction
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.MemoryFileStore
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyDocument
import com.hark.sothudiary.data.SketchOverlay
import com.hark.sothudiary.data.VoiceNote
import com.hark.sothudiary.domain.drive.DriveFile
import com.hark.sothudiary.domain.drive.DriveRules
import com.hark.sothudiary.domain.drive.RestoreChoice
import com.hark.sothudiary.domain.drive.StoredRef
import com.hark.sothudiary.domain.drive.authErrorText
import com.hark.sothudiary.domain.drive.mergeAppData
import com.hark.sothudiary.domain.drive.multipartBody
import com.hark.sothudiary.domain.drive.needsRestoreChoice
import com.hark.sothudiary.domain.drive.planBackup
import com.hark.sothudiary.domain.drive.planRestore
import com.hark.sothudiary.domain.drive.refFromDriveName
import com.hark.sothudiary.domain.drive.referencedFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 3.0.0: Google Drive backup – upload / delete diff, restore list, merge. */
class V300Test {
    private val prop = Property(
        id = "p1", title = "Plot", imageFile = "img_1.jpg", photos = listOf("post_2.jpg"),
        documents = listOf(PropertyDocument("d1", DocKind.EC, "doc_3.pdf", "application/pdf")),
        sketch = SketchOverlay("img_4.png", 9.0, 77.0),
    )
    private val data = AppData(
        properties = listOf(prop),
        interactions = listOf(Interaction("i1", propertyId = "p1", voiceNotes = listOf(VoiceNote("v1", "vn_5.m4a")))),
        settings = AppSettings(bgmFile = "bgm_6.mp3"),
    )

    @Test fun everyStoredFileIsReferenced() {
        val names = referencedFiles(data).map { it.driveName }
        assertEquals(
            listOf("images__img_1.jpg", "images__post_2.jpg", "images__doc_3.pdf", "images__img_4.png", "voice__vn_5.m4a", "music__bgm_6.mp3"),
            names,
        )
    }

    @Test fun driveNamesMapBackToFolders() {
        assertEquals(StoredRef("voice", "vn_5.m4a"), refFromDriveName("voice__vn_5.m4a"))
        assertNull(refFromDriveName(DriveRules.JSON_NAME))
        assertNull(refFromDriveName("other__x.jpg"))
        assertNull(refFromDriveName("images__"))
    }

    @Test fun backupUploadsOnlyNewFilesAndDeletesUnusedOnes() {
        val remote = listOf(
            DriveFile("j1", DriveRules.JSON_NAME),
            DriveFile("j2", DriveRules.JSON_NAME),
            DriveFile("a", "images__img_1.jpg"),
            DriveFile("b", "images__old_9.jpg"),
            DriveFile("c", "images__img_1.jpg"),
        )
        val missingOnPhone = setOf("post_2.jpg")
        val plan = planBackup(referencedFiles(data), { it.name !in missingOnPhone }, remote)
        assertEquals(listOf("doc_3.pdf", "img_4.png", "vn_5.m4a", "bgm_6.mp3"), plan.upload.map { it.name })
        assertEquals("j1", plan.jsonFileId)
        assertEquals(setOf("b", "c", "j2"), plan.delete.map { it.id }.toSet())
    }

    @Test fun firstBackupCreatesTheJson() {
        val plan = planBackup(referencedFiles(data), { true }, emptyList())
        assertNull(plan.jsonFileId)
        assertEquals(6, plan.upload.size)
        assertTrue(plan.delete.isEmpty())
    }

    @Test fun restoreDownloadsOnlyMissingFilesThatDriveHas() {
        val remote = listOf(DriveFile("a", "images__img_1.jpg"), DriveFile("v", "voice__vn_5.m4a"))
        val todo = planRestore(referencedFiles(data), { it.name == "img_1.jpg" }, remote)
        assertEquals(listOf("v"), todo.map { it.second.id })
        assertEquals("voice", todo.single().first.folder)
    }

    @Test fun aNewPhoneMustChooseBeforeOverwritingDrive() {
        assertTrue(needsRestoreChoice(driveHasBackup = true, lastBackupAt = 0))
        assertFalse(needsRestoreChoice(driveHasBackup = true, lastBackupAt = 5))
        assertFalse(needsRestoreChoice(driveHasBackup = false, lastBackupAt = 0))
    }

    @Test fun mergeKeepsNewestPropertyAndUnionOfTheRest() {
        val phone = AppData(
            properties = listOf(Property("p1", "Phone old", updatedAt = 10), Property("p2", "Phone only", updatedAt = 1), Property("p4", "Phone newer", updatedAt = 50)),
            brokers = listOf(Broker("b1", "Ravi")),
            settings = AppSettings(lang = Lang.TA),
        )
        val drive = AppData(
            properties = listOf(Property("p1", "Drive new", updatedAt = 20), Property("p3", "Drive only", updatedAt = 2), Property("p4", "Drive older", updatedAt = 40)),
            brokers = listOf(Broker("b1", "Ravi (drive)"), Broker("b2", "Kumar")),
            settings = AppSettings(lang = Lang.EN),
        )
        val m = mergeAppData(phone, drive)
        assertEquals(listOf("Drive new", "Phone only", "Phone newer", "Drive only"), m.properties.map { it.title })
        assertEquals(listOf("Ravi", "Kumar"), m.brokers.map { it.name })
        assertEquals(Lang.TA, m.settings.lang)
    }

    @Test fun restoreKeepsThisPhonesDriveConnectionAndLanguage() {
        val repo = JsonRepository(MemoryFileStore())
        repo.setLang(Lang.TA)
        repo.setDrive(account = "harksathya@gmail.com", lastBackupAt = 0)
        val drive = AppData(properties = listOf(Property("p9", "From Drive")), settings = AppSettings(driveAccount = "other@x.com", driveLastBackupAt = 99))
        repo.applyDriveBackup(drive, RestoreChoice.USE_DRIVE)
        assertEquals(listOf("p9"), repo.data.properties.map { it.id })
        assertEquals("harksathya@gmail.com", repo.data.settings.driveAccount)
        assertEquals(0L, repo.data.settings.driveLastBackupAt)
        assertEquals(Lang.TA, repo.data.settings.lang)
        // The exported JSON parses back as a backup.
        assertEquals(listOf("p9"), repo.parseBackup(repo.exportJson())?.properties?.map { it.id })
    }

    @Test fun multipartHasMetadataThenContent() {
        val body = multipartBody("B", """{"name":"x"}""", "image/jpeg", byteArrayOf(1, 2, 3)).decodeToString()
        assertTrue(body.startsWith("--B\r\nContent-Type: application/json"))
        assertTrue(body.contains("Content-Type: image/jpeg\r\n\r\n"))
        assertTrue(body.endsWith("\r\n--B--\r\n"))
    }

    @Test fun developerErrorExplainsWhatToCheck() {
        val t = authErrorText("ApiException 10: 10: ", Lang.EN)
        assertTrue("SHA-1" in t && "com.hark.sothudiary" in t && "harksathya@gmail.com" in t && "Drive API" in t)
        assertTrue("Google" in authErrorText("ApiException 8: internal", Lang.TA))
    }
}
