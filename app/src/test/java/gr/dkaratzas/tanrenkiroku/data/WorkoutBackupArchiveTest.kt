package gr.dkaratzas.tanrenkiroku.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorkoutBackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private val workout = """{"date":"2026-10-06","entries":[]}"""
    private fun archive(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test fun validArchiveIsStreamedValidatedAndCanBeExportedAgain() {
        val directory = temporary.newFolder()
        extractWorkoutBackup(ByteArrayInputStream(archive("workouts/2026-10-06.json" to workout)), directory)
        val bytes = ByteArrayOutputStream()
        exportWorkoutBackup(SyncSnapshot.capture(directory), bytes)
        val restored = temporary.newFolder()
        extractWorkoutBackup(ByteArrayInputStream(bytes.toByteArray()), restored)
        assertEquals(workout, File(restored, "2026-10-06.json").readText())
    }

    @Test fun nullExportStreamIsAnError() {
        assertTrue(runCatching { exportWorkoutBackup(SyncSnapshot.capture(temporary.newFolder()), null) }.exceptionOrNull() is IOException)
    }

    @Test fun invalidDatedEntryAndNonZipDataCannotQualifyAsRestore() {
        for (bytes in listOf(archive("2026-10-06.json" to "broken"), "not a ZIP".toByteArray())) {
            assertTrue(runCatching { extractWorkoutBackup(ByteArrayInputStream(bytes), temporary.newFolder()) }.isFailure)
        }
    }

    @Test fun duplicateFlattenedNamesAreRejected() {
        val bytes = archive("a/2026-10-06.json" to workout, "b/2026-10-06.json" to workout)
        assertTrue(runCatching { extractWorkoutBackup(ByteArrayInputStream(bytes), temporary.newFolder()) }.exceptionOrNull() is IOException)
    }

    @Test fun sizeAndCountLimitsIncludeIgnoredEntries() {
        val scenarios = listOf(
            archive("2026-10-06.json" to workout) to BackupLimits(entryBytes = 10),
            archive("2026-10-06.json" to workout, "ignored.bin" to "x".repeat(100)) to BackupLimits(totalBytes = 50),
            archive("2026-10-06.json" to workout, "ignored.bin" to "x") to BackupLimits(entries = 1),
            archive("2026-10-06.json" to workout, "ignored.bin" to "x".repeat(100)) to BackupLimits(entryBytes = 50)
        )
        for ((bytes, limits) in scenarios) {
            assertTrue(runCatching { extractWorkoutBackup(ByteArrayInputStream(bytes), temporary.newFolder(), limits) }.exceptionOrNull() is IOException)
        }
    }
}
