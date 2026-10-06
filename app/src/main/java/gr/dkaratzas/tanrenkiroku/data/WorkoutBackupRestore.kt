package gr.dkaratzas.tanrenkiroku.data

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

private val restoreDirectoryPattern = Regex("\\.restore_[a-f0-9]{32}")

internal fun restoreWorkoutBackup(
    source: File,
    destination: File,
    writeFile: (File, ByteArray) -> Unit = ::atomicWrite,
    moveFile: (File, File) -> Unit = ::moveWorkoutFile
): Unit = restoreWorkoutSnapshot(SyncSnapshot.capture(source), destination, writeFile, moveFile)

internal fun restoreWorkoutSnapshot(
    snapshot: SyncSnapshot,
    destination: File,
    writeFile: (File, ByteArray) -> Unit = ::atomicWrite,
    moveFile: (File, File) -> Unit = ::moveWorkoutFile
): Unit = synchronized(workoutStorageGate) {
    if (snapshot.files.isEmpty()) throw IOException("No workout or custom exercise files found in backup")
    if (!destination.isDirectory && !destination.mkdirs()) throw IOException("Cannot create workout folder")
    recoverWorkoutRestores(destination)
    val originals = destination.listFiles { file -> isSyncableFile(file.name) }
        ?: throw IOException("Cannot read current workout files")
    if (originals.any { !it.isFile || Files.isSymbolicLink(it.toPath()) }) throw IOException("Expected regular workout files")
    val transaction = File(destination, ".restore_${UUID.randomUUID().toString().replace("-", "")}")
    val next = File(transaction, "next")
    val previous = File(transaction, "previous")
    if (!next.mkdirs() || !previous.mkdir()) throw IOException("Cannot stage backup replacement")
    try {
        // Complete every write before moving any original file. Commit/rollback then use renames,
        // so original bytes remain on disk even if space runs out or the process is interrupted.
        for ((name, file) in snapshot.files) {
            writeFile(File(next, name), syncJson.encodeToString(file.content).toByteArray(Charsets.UTF_8))
        }
        atomicWrite(File(transaction, "ready"), syncJson.encodeToString(snapshot.files.keys.toList()).toByteArray(Charsets.UTF_8))
        for (original in originals) moveFile(original, File(previous, original.name))
        for (name in snapshot.files.keys) moveFile(File(next, name), File(destination, name))
        Files.createFile(File(transaction, "committed").toPath())
    } catch (failure: Exception) {
        try { recoverTransaction(transaction, destination, moveFile) }
        catch (rollback: Exception) {
            failure.addSuppressed(rollback)
            throw IOException("Backup replacement failed. Recovery files are retained at ${transaction.absolutePath}", failure)
        }
        throw IOException("Could not restore backup: ${failure.message}. Previous files were restored.", failure)
    }
    // A completed replacement remains successful when old-file cleanup is temporarily blocked.
    try { cleanTransaction(transaction, destination) } catch (_: IOException) { }
}

internal fun recoverWorkoutRestores(directory: File): Unit = synchronized(workoutStorageGate) {
    if (!directory.isDirectory) return@synchronized
    val transactions = directory.listFiles { file -> file.isDirectory && file.name.matches(restoreDirectoryPattern) }
        ?: throw IOException("Cannot inspect workout recovery files")
    transactions.forEach { transaction ->
        if (File(transaction, "committed").exists()) {
            try { cleanTransaction(transaction, directory) } catch (_: IOException) { }
        } else recoverTransaction(transaction, directory, ::moveWorkoutFile)
    }
}

private fun recoverTransaction(transaction: File, directory: File, moveFile: (File, File) -> Unit) {
    require(transaction.canonicalFile.parentFile == directory.canonicalFile) { "Invalid recovery folder" }
    val ready = File(transaction, "ready")
    if (ready.exists() && !File(transaction, "committed").exists()) {
        val names = syncJson.decodeFromString<List<String>>(readBoundedFile(ready).toString(Charsets.UTF_8))
        require(names.distinct().size == names.size && names.all(::isSyncableFile)) { "Invalid backup recovery filenames" }
        val next = File(transaction, "next")
        val previous = File(transaction, "previous")
        for (name in names) {
            val staged = File(next, name)
            if (!staged.exists()) {
                val promoted = File(directory, name)
                if (promoted.exists()) moveFile(promoted, staged)
                else Files.createFile(staged.toPath()) // Record a missing promoted file before restoring originals.
            }
        }
        val originals = previous.listFiles() ?: throw IOException("Cannot read prior workout snapshot")
        for (file in originals) {
            require(file.isFile && isSyncableFile(file.name) && !Files.isSymbolicLink(file.toPath())) { "Invalid prior workout file" }
            moveFile(file, File(directory, file.name))
        }
    }
    cleanTransaction(transaction, directory)
}

private fun cleanTransaction(transaction: File, directory: File) {
    require(transaction.name.matches(restoreDirectoryPattern) && transaction.canonicalFile.parentFile == directory.canonicalFile) { "Invalid recovery folder" }
    // Files.walk does not follow symbolic links; only this generated child directory is removed.
    Files.walk(transaction.toPath()).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}

private fun moveWorkoutFile(source: File, destination: File) {
    try { Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
    catch (_: AtomicMoveNotSupportedException) { Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING) }
}
