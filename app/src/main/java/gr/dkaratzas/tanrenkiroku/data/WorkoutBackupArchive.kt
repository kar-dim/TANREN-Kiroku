package gr.dkaratzas.tanrenkiroku.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class BackupLimits(val entryBytes: Long = 8L * 1024 * 1024, val totalBytes: Long = 128L * 1024 * 1024, val entries: Int = 10000)

internal fun extractWorkoutBackup(input: InputStream, staging: File, limits: BackupLimits = BackupLimits(), checkActive: () -> Unit = {}) {
    var count = 0
    var total = 0L
    val accepted = mutableSetOf<String>()
    val buffer = ByteArray(8192)
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            checkActive()
            val entry = zip.nextEntry ?: break
            if (++count > limits.entries) throw IOException("Backup contains too many entries")
            val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
            val include = !entry.isDirectory && isSyncableFile(name)
            if (include && !accepted.add(name)) throw IOException("Backup contains duplicate file: $name")
            val out = if (include) File(staging, name).outputStream() else null
            try {
                var size = 0L
                while (true) {
                    checkActive()
                    val read = zip.read(buffer)
                    if (read < 0) break
                    size += read; total += read
                    if (size > limits.entryBytes || total > limits.totalBytes) throw IOException("Backup exceeds the allowed size")
                    out?.write(buffer, 0, read)
                }
            } finally { out?.close() }
            zip.closeEntry()
        }
    }
    if (accepted.isEmpty()) throw IOException("No workout or custom exercise files found in backup")
    SyncSnapshot.capture(staging) // Validate every accepted file before any replacement begins.
}

internal fun exportWorkoutBackup(snapshot: SyncSnapshot, output: OutputStream?) {
    val stream = output ?: throw IOException("Cannot open the backup output file")
    ZipOutputStream(stream.buffered()).use { zip ->
        for ((name, file) in snapshot.files) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(syncJson.encodeToString(file.content).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}

internal fun readBoundedFile(file: File, limit: Long = 8L * 1024 * 1024): ByteArray {
    if (file.length() > limit) throw IOException("${file.name} exceeds the allowed size")
    val bytes = ByteArrayOutputStream()
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (bytes.size().toLong() + count > limit) throw IOException("${file.name} exceeds the allowed size")
            bytes.write(buffer, 0, count)
        }
    }
    return bytes.toByteArray()
}
