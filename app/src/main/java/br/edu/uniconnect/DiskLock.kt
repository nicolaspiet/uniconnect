package br.edu.uniconnect

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** AtomicFile itself is not a cross-process lock; readers and writers share this lock. */
object DiskLock {
    @Synchronized
    fun <T> run(context: Context, block: () -> T): T =
        FileOutputStream(File(context.filesDir, "uniconnect-storage.lock"), true).use { output ->
            output.channel.lock().use { block() }
        }
}
