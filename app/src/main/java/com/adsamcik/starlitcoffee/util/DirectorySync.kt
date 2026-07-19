package com.adsamcik.starlitcoffee.util

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

internal fun interface FileSync {
    @Throws(IOException::class)
    fun sync(file: File)
}

internal object AndroidFileSync : FileSync {
    override fun sync(file: File) {
        require(file.isFile) { "File sync target must be a regular file" }
        RandomAccessFile(file, "rw").use { handle ->
            handle.fd.sync()
        }
    }
}

internal fun interface DirectorySync {
    @Throws(IOException::class)
    fun sync(directory: File)
}

internal object AndroidDirectorySync : DirectorySync {
    override fun sync(directory: File) {
        require(directory.isDirectory) { "Directory sync target must be a directory" }
        val descriptor = try {
            Os.open(
                directory.absolutePath,
                OsConstants.O_RDONLY,
                0,
            )
        } catch (error: ErrnoException) {
            throw IOException("Could not open directory for fsync: ${directory.path}", error)
        }

        var failure: IOException? = null
        try {
            Os.fsync(descriptor)
        } catch (error: ErrnoException) {
            failure = IOException("Could not fsync directory: ${directory.path}", error)
        } finally {
            try {
                Os.close(descriptor)
            } catch (error: ErrnoException) {
                val closeFailure = IOException(
                    "Could not close directory after fsync: ${directory.path}",
                    error,
                )
                if (failure == null) {
                    failure = closeFailure
                } else {
                    failure.addSuppressed(closeFailure)
                }
            }
        }
        failure?.let { throw it }
    }
}
