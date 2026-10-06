package gr.dkaratzas.tanrenkiroku.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun atomicWrite(destination: File, bytes: ByteArray) {
    val parent = requireNotNull(destination.absoluteFile.parentFile) { "Workout file needs a parent folder" }
    val temporary = Files.createTempFile(parent.toPath(), ".${destination.name}.", ".tmp")
    try {
        Files.write(temporary, bytes)
        try {
            Files.move(temporary, destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}
