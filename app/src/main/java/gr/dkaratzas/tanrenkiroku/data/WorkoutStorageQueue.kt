package gr.dkaratzas.tanrenkiroku.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.io.IOException

// Enqueue on the calling thread, before launching any UI coroutine, to preserve edit order.
internal class WorkoutStorageQueue : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = Channel<() -> Unit>(Channel.UNLIMITED)
    private data class FailedWrite(val action: () -> Unit, val error: Exception)
    private val failed = linkedMapOf<String, FailedWrite>()

    init { scope.launch { for (task in tasks) synchronized(workoutStorageGate) { task() } } }

    private fun <T> submit(action: () -> T): Deferred<T> {
        val result = CompletableDeferred<T>()
        val task = {
            try { result.complete(action()) }
            catch (e: Exception) { result.completeExceptionally(e) }
            Unit
        }
        if (tasks.trySend(task).isFailure) result.completeExceptionally(IOException("Workout storage is unavailable"))
        return result
    }

    fun mutate(namespace: String, key: String, supersedesPrefixes: List<String> = emptyList(), action: () -> Unit): Deferred<Unit> = submit {
        val id = "$namespace|$key"
        try {
            // Finish older failed operations before accepting a later operation on another key.
            // A replacement of the same key (or an explicitly superseded group) wins instead.
            val older = failed.filterKeys { candidate ->
                candidate.startsWith("$namespace|") && candidate != id &&
                    supersedesPrefixes.none { candidate.startsWith("$namespace|$it") }
            }.toMap()
            for ((olderId, write) in older) {
                write.action()
                failed.remove(olderId)
            }
            action()
            failed.remove(id)
            failed.keys.removeAll { candidate -> supersedesPrefixes.any { candidate.startsWith("$namespace|$it") } }
            Unit
        }
        catch (e: Exception) { failed.remove(id); failed[id] = FailedWrite(action, e); throw e }
    }

    fun <T> read(namespace: String, requireSaved: Boolean = false, action: () -> T): Deferred<T> = submit {
        if (requireSaved) {
            val failure = failed.entries.firstOrNull { it.key.startsWith("$namespace|") }?.value
            if (failure != null) throw IOException("Some workout changes could not be saved. Retry saving before syncing or backing up.", failure.error)
        }
        action()
    }

    fun retry(namespace: String): Deferred<Unit> = submit {
        for ((id, failure) in failed.filterKeys { it.startsWith("$namespace|") }.toMap()) {
            try { failure.action(); failed.remove(id) }
            catch (e: Exception) { failed[id] = FailedWrite(failure.action, e); throw e }
        }
    }

    override fun close() { tasks.close(); scope.cancel() }
}

internal val workoutStorageQueue = WorkoutStorageQueue()
internal fun File.storageNamespace(): String = absoluteFile.normalize().path
internal suspend fun captureWorkoutSnapshot(directory: File): SyncSnapshot =
    workoutStorageQueue.read(directory.storageNamespace(), requireSaved = true) { SyncSnapshot.capture(directory) }.await()
