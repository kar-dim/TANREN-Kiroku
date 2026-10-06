package gr.dkaratzas.tanrenkiroku.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SyncSnapshotTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun unreadableOrMissingFolderCannotBecomeEmptyManifest() {
        assertTrue(runCatching { SyncSnapshot.capture(File(temporary.root, "missing")) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { SyncSnapshot.capture(temporary.newFile()) }.exceptionOrNull() is IOException)
    }

    @Test fun validEmptyFolderIsCompleteAndUnrelatedFilesAreExcluded() {
        val directory = temporary.newFolder()
        File(directory, "preferences.json").writeText("unrelated")
        val snapshot = SyncSnapshot.capture(directory)
        assertEquals(2, snapshot.manifest.protocolVersion)
        assertTrue(snapshot.manifest.complete)
        assertTrue(snapshot.manifest.files.isEmpty())
    }

    @Test fun rejectsCorruptOrInvalidWorkoutFilesInsteadOfDroppingThem() {
        val directory = temporary.newFolder()
        val file = File(directory, "2026-10-06.json")
        for (content in listOf(
            "", "{broken", "null", "[]", "{}",
            """{"date":"2026-10-05","entries":[]}""",
            """{"date":"2026-10-06"}""",
            """{"date":"2026-10-06","entries":[{"exerciseId":"x"}]}""",
            """{"date":"2026-10-06","entries":[{"exerciseId":"","sets":[]}]}""",
            """{"date":"2026-10-06","entries":[{"exerciseId":"x","sets":[{"reps":0,"kg":0}]}]}""",
            """{"date":"2026-10-06","entries":[{"exerciseId":"x","sets":[{"reps":1,"kg":-1}]}]}""",
            """{"date":"2026-10-06","entries":[{"exerciseId":"x","sets":[{"reps":"1","kg":1}]}]}""",
            """{"date":"2026-10-06","entries":[{"exerciseId":"x","sets":[{"reps":1}]}]}""",
            """{"date":"2026-10-06","entries":[{"exerciseId":"x","sets":[{"reps":2,"kg":1e308}]}]}"""
        )) {
            file.writeText(content)
            val failure = runCatching { SyncSnapshot.capture(directory) }.exceptionOrNull()
            assertTrue("Should reject $content", failure is IOException)
            assertTrue(failure!!.message!!.contains(file.name))
        }
    }

    @Test fun rejectsImpossibleDateAndDirectoryMasqueradingAsWorkout() {
        val directory = temporary.newFolder()
        val invalid = File(directory, "2026-02-30.json").apply { writeText("""{"date":"2026-02-30","entries":[]}""") }
        assertTrue(runCatching { SyncSnapshot.capture(directory) }.isFailure)
        invalid.delete()
        File(directory, "2026-10-06.json").mkdir()
        assertTrue(runCatching { SyncSnapshot.capture(directory) }.isFailure)
    }

    @Test fun validatesCustomExerciseMusclesAndUniqueIds() {
        val directory = temporary.newFolder()
        val file = File(directory, CUSTOM_EXERCISES_FILENAME)
        for (content in listOf(
            "null", "{}", "[{}]",
            """[{"id":"x","name":"X","primaryMuscles":[]}]""",
            """[{"id":"x","name":"X","primaryMuscles":["Unknown"]}]""",
            """[{"id":"x","name":"X","primaryMuscles":["Chest"],"secondaryMuscles":["chest"]}]""",
            """[{"id":"x","name":"X","primaryMuscles":["Chest"]},{"id":"x","name":"Y","primaryMuscles":["Back"]}]"""
        )) {
            file.writeText(content)
            assertTrue("Should reject $content", runCatching { SyncSnapshot.capture(directory) }.isFailure)
        }
        file.writeText("""[{"id":"x","name":"X","primaryMuscles":["Chest"]}]""")
        assertEquals(1, SyncSnapshot.capture(directory).files.size)
    }

    @Test fun emptyEntriesAndZeroAddedWeightAreValid() {
        validateSyncContent("2026-10-06.json", Json.parseToJsonElement("""{"date":"2026-10-06","entries":[]}"""))
        validateSyncContent("2026-10-06.json", Json.parseToJsonElement("""{"date":"2026-10-06","entries":[{"exerciseId":"x","sets":[{"reps":10,"kg":0}]}]}"""))
    }

    @Test fun atomicWriteReplacesFileAndLeavesNoTemporaryFiles() {
        val directory = temporary.newFolder()
        val file = File(directory, "2026-10-06.json").apply { writeText("old") }
        atomicWrite(file, "new".toByteArray())
        assertEquals("new", file.readText())
        assertEquals(listOf(file.name), directory.listFiles()!!.map { it.name })
    }

    @Test fun invalidBackupLeavesCurrentDataUntouched() {
        val source = temporary.newFolder()
        File(source, "2026-10-06.json").writeText("broken")
        val destination = temporary.newFolder()
        val existing = File(destination, "2026-10-05.json").apply { writeText("original bytes") }
        assertTrue(runCatching { restoreWorkoutBackup(source, destination) }.isFailure)
        assertEquals("original bytes", existing.readText())
        assertEquals(listOf(existing.name), destination.listFiles()!!.map { it.name })
    }

    @Test fun validBackupReplacesSyncableFilesAndPreservesUnrelatedFiles() {
        val source = temporary.newFolder()
        File(source, "2026-10-06.json").writeText("""{"date":"2026-10-06","entries":[]}""")
        val destination = temporary.newFolder()
        File(destination, "2026-10-05.json").writeText("old")
        File(destination, CUSTOM_EXERCISES_FILENAME).writeText("old catalog")
        File(destination, "preferences.json").writeText("keep me")
        restoreWorkoutBackup(source, destination)
        assertEquals(setOf("2026-10-06.json", "preferences.json"), destination.listFiles()!!.map { it.name }.toSet())
        assertEquals("keep me", File(destination, "preferences.json").readText())
        assertEquals(1, SyncSnapshot.capture(destination).files.size)
    }

    @Test fun failedRestoreRollsBackOverwritesAndRemovesNewFiles() {
        val source = temporary.newFolder()
        for (date in listOf("2026-10-03", "2026-10-04", "2026-10-06")) {
            File(source, "$date.json").writeText("""{"date":"$date","entries":[]}""")
        }
        val destination = temporary.newFolder()
        File(destination, "2026-10-04.json").writeText("original fourth")
        File(destination, "2026-10-06.json").writeText("original sixth")
        var failOnce = true
        val failure = runCatching {
            restoreWorkoutBackup(source, destination, writeFile = { file, bytes ->
                if (file.name == "2026-10-06.json" && failOnce) {
                    failOnce = false
                    throw IOException("Simulated disk failure")
                }
                atomicWrite(file, bytes)
            })
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertFalse(failOnce)
        assertEquals(setOf("2026-10-04.json", "2026-10-06.json"), destination.listFiles()!!.map { it.name }.toSet())
        assertEquals("original fourth", File(destination, "2026-10-04.json").readText())
        assertEquals("original sixth", File(destination, "2026-10-06.json").readText())
    }

    @Test fun commitFailureRestoresOriginalFilesUsingRenames() {
        val source = temporary.newFolder()
        for (date in listOf("2026-10-03", "2026-10-04", "2026-10-06")) File(source, "$date.json").writeText("""{"date":"$date","entries":[]}""")
        val destination = temporary.newFolder()
        File(destination, "2026-10-04.json").writeText("original fourth")
        File(destination, "2026-10-06.json").writeText("original sixth")
        var failOnce = true
        val failure = runCatching {
            restoreWorkoutBackup(source, destination, moveFile = { from, to ->
                if (from.parentFile!!.name == "next" && from.name == "2026-10-06.json" && failOnce) {
                    failOnce = false
                    throw IOException("Simulated commit failure")
                }
                java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            })
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertFalse(failOnce)
        assertEquals(setOf("2026-10-04.json", "2026-10-06.json"), destination.listFiles()!!.map { it.name }.toSet())
        assertEquals("original fourth", File(destination, "2026-10-04.json").readText())
        assertEquals("original sixth", File(destination, "2026-10-06.json").readText())
    }

    @Test fun interruptedReplacementRecoversOriginalBeforeSnapshot() {
        val directory = temporary.newFolder()
        val transaction = File(directory, ".restore_${"a".repeat(32)}").apply { mkdir() }
        File(transaction, "next").mkdir()
        val previous = File(transaction, "previous").apply { mkdir() }
        val original = """{"date":"2026-10-06","entries":[]}"""
        File(previous, "2026-10-06.json").writeText(original)
        File(directory, "2026-10-06.json").writeText("""{"date":"2026-10-06","entries":[{"exerciseId":"x","sets":[{"reps":1,"kg":10}]}]}""")
        File(transaction, "ready").writeText("""["2026-10-06.json"]""")
        val snapshot = SyncSnapshot.capture(directory)
        assertEquals(original, snapshot.files.getValue("2026-10-06.json").content.toString())
        assertFalse(transaction.exists())
    }

    @Test fun interruptedCleanupPreservesCommittedReplacement() {
        val directory = temporary.newFolder()
        val transaction = File(directory, ".restore_${"b".repeat(32)}").apply { mkdir() }
        File(transaction, "next").mkdir()
        val previous = File(transaction, "previous").apply { mkdir() }
        File(previous, "2026-10-05.json").writeText("old bytes")
        File(directory, "2026-10-06.json").writeText("""{"date":"2026-10-06","entries":[]}""")
        File(transaction, "ready").writeText("""["2026-10-06.json"]""")
        File(transaction, "committed").writeText("")
        assertEquals(setOf("2026-10-06.json"), SyncSnapshot.capture(directory).files.keys)
        assertFalse(transaction.exists())
    }

    @Test fun failedRollbackRetainsOriginalOnDiskForRecovery() {
        val source = temporary.newFolder()
        File(source, "2026-10-06.json").writeText("""{"date":"2026-10-06","entries":[]}""")
        val destination = temporary.newFolder()
        val original = """{"date":"2026-10-05","entries":[]}"""
        File(destination, "2026-10-05.json").writeText(original)
        val failure = runCatching {
            restoreWorkoutBackup(source, destination, moveFile = { from, to ->
                if (from.parentFile!!.name in setOf("next", "previous")) throw IOException("Simulated locked file")
                java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            })
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        val transaction = destination.listFiles()!!.single { it.isDirectory }
        assertEquals(original, File(transaction, "previous/2026-10-05.json").readText())
        assertEquals(setOf("2026-10-05.json"), SyncSnapshot.capture(destination).files.keys)
        assertFalse(transaction.exists())
    }
}
