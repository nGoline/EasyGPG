package com.ngoline.easygpg

import android.content.Context

class StorageManager(private val context: Context) {
    fun writeToFile(fileName: String, data: ByteArray) {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use { outputStream ->
            outputStream.write(data)
        }
    }

    fun readFromFile(fileName: String): ByteArray? {
        return context.openFileInput(fileName).use { inputStream ->
            inputStream.readBytes()
        }
    }
}