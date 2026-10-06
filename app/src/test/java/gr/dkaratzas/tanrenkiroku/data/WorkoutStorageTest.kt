package gr.dkaratzas.tanrenkiroku.data

import gr.dkaratzas.tanrenkiroku.data.model.WorkoutDay
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutEntry
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutSet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WorkoutStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun day(date: String = "2026-10-06", reps: Int = 10, kg: Double = 0.0) =
        WorkoutDay(date, listOf(WorkoutEntry("push_up", listOf(WorkoutSet(reps, kg)))))

    @Test fun slowFirstMutationCannotFinishAfterLaterMutationsOrSnapshot() = runBlocking {
        WorkoutStorageQueue().use { queue ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val order = mutableListOf<Int>()
            val first = queue.mutate("test", "a") {
                started.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                order.add(1)
            }
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS))
                queue.mutate("test", "b") { order.add(2) }
                queue.mutate("test", "c") { order.add(3) }
                val snapshot = queue.read("test", requireSaved = true) { order.toList() }
                assertFalse(snapshot.isCompleted)
                release.countDown()
                assertEquals(listOf(1, 2, 3), snapshot.await())
                first.await()
            } finally { release.countDown() }
        }
    }

    @Test fun rapidSavesThenImmediateSnapshotIncludesLatestEdit() = runBlocking {
        val directory = temporary.newFolder()
        val repo = WorkoutRepository(directory)
        repeat(100) { repo.saveWorkout(day(reps = it + 1)) }
        val snapshot = repo.captureSnapshot()
        val set = snapshot.files.getValue("2026-10-06.json").content.jsonObject.getValue("entries")
            .jsonArray[0].jsonObject.getValue("sets").jsonArray[0].jsonObject
        assertEquals("100", set.getValue("reps").jsonPrimitive.content)
        assertEquals(100, repo.loadWorkout(LocalDate.of(2026, 10, 6))!!.entries[0].sets[0].reps)
    }

    @Test fun failedMutationBlocksSnapshotsUntilRetrySucceeds() = runBlocking {
        WorkoutStorageQueue().use { queue ->
            var unavailable = true
            var value = 0
            val save = queue.mutate("phone", "day") {
                if (unavailable) throw IOException("disk unavailable")
                value = 7
            }
            assertTrue(runCatching { save.await() }.exceptionOrNull() is IOException)
            assertTrue(runCatching { queue.read("phone", true) { value }.await() }.exceptionOrNull() is IOException)
            assertEquals(0, queue.read("other-phone", true) { value }.await())
            unavailable = false
            queue.retry("phone").await()
            assertEquals(7, queue.read("phone", true) { value }.await())
        }
    }

    @Test fun successfulNewerSaveSupersedesFailedOlderSave() = runBlocking {
        WorkoutStorageQueue().use { queue ->
            var value = 0
            assertTrue(runCatching { queue.mutate("phone", "day") { throw IOException("old failed edit") }.await() }.isFailure)
            queue.mutate("phone", "day") { value = 20 }.await()
            queue.retry("phone").await()
            assertEquals(20, queue.read("phone", true) { value }.await())
        }
    }

    @Test fun failedBroadOperationCannotReplayAfterALaterSuccessfulEdit() = runBlocking {
        WorkoutStorageQueue().use { queue ->
            var unavailable = true
            var value = 0
            val broad = queue.mutate("phone", "restore") {
                if (unavailable) throw IOException("disk unavailable")
                value = 1
            }
            assertTrue(runCatching { broad.await() }.isFailure)
            val later = queue.mutate("phone", "day") { value = 2 }
            assertTrue(runCatching { later.await() }.isFailure)
            assertEquals(0, value)
            unavailable = false
            queue.retry("phone").await()
            assertEquals(2, queue.read("phone", true) { value }.await())
            queue.retry("phone").await()
            assertEquals(2, value)
        }
    }

    @Test fun repositoryRejectsInvalidWeightsWithoutOverwritingCurrentData() = runBlocking {
        val directory = temporary.newFolder()
        val repo = WorkoutRepository(directory)
        repo.saveWorkout(day()).await()
        val file = File(directory, "2026-10-06.json")
        val original = file.readText()
        for (kg in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0)) {
            assertTrue(runCatching { repo.saveWorkout(day(kg = kg)).await() }.isFailure)
            assertEquals(original, file.readText())
        }
        repo.saveWorkout(day(kg = 0.0)).await()
        assertEquals(1, repo.captureSnapshot().files.size)
    }

    @Test fun deleteUsesCapturedDayWhileOtherDayRemainsIntact() = runBlocking {
        val repo = WorkoutRepository(temporary.newFolder())
        val a = LocalDate.of(2026, 10, 5)
        val b = a.plusDays(1)
        repo.saveWorkout(day(a.toString())).await()
        repo.saveWorkout(day(b.toString())).await()
        var selected = a
        val delete = repo.deleteWorkout(selected)
        selected = b
        delete.await()
        assertNull(repo.loadWorkout(a))
        assertEquals(b.toString(), repo.loadWorkout(selected)!!.date)
    }

    @Test fun copyIsQueuedBeforeImmediateSnapshotAndUsesCapturedTarget() = runBlocking {
        val repo = WorkoutRepository(temporary.newFolder())
        val source = LocalDate.of(2026, 10, 4)
        var selected = source.plusDays(1)
        repo.saveWorkout(day(source.toString(), reps = 15)).await()
        val copy = repo.copyWorkout(source, selected)
        selected = source.plusDays(2)
        val snapshot = repo.captureSnapshot()
        assertEquals(setOf("2026-10-04.json", "2026-10-05.json"), snapshot.files.keys)
        copy.await()
        assertNull(repo.loadWorkout(selected))
        assertEquals(15, repo.loadWorkout(source.plusDays(1))!!.entries[0].sets[0].reps)
    }

    @Test fun dateGuardRejectsOldLoadsEvenWhenReturningToSameDate() {
        val a = LocalDate.of(2026, 10, 4)
        val guard = WorkoutDateGuard(a)
        val firstA = guard.begin(a)
        val b = guard.begin(a.plusDays(1))
        val c = guard.begin(a.plusDays(2))
        assertFalse(guard.accepts(firstA))
        assertFalse(guard.accepts(b))
        assertTrue(guard.accepts(c))
        val secondA = guard.begin(a)
        assertFalse(guard.accepts(firstA))
        assertFalse(guard.accepts(c))
        assertTrue(guard.accepts(secondA))
        guard.invalidate()
        assertFalse(guard.accepts(secondA))
    }

    @Test fun restoreKeepsCapturedContentAfterStagingFilesAreRemoved() = runBlocking {
        val source = temporary.newFolder()
        val file = File(source, "2026-10-06.json").apply { writeText("""{"date":"2026-10-06","entries":[]}""") }
        val repo = WorkoutRepository(temporary.newFolder())
        val restore = repo.restoreBackup(source)
        file.delete()
        restore.await()
        assertEquals(1, repo.captureSnapshot().files.size)
    }
}
