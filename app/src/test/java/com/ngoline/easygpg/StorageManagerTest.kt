package com.ngoline.easygpg

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.FileNotFoundException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storage = StorageManager(context)

    @Test
    fun `a written file reads back byte for byte`() {
        val data = "-----BEGIN PGP PUBLIC KEY BLOCK-----".toByteArray()

        storage.writeToFile("keyring.pgp", data)

        assertArrayEquals(data, storage.readFromFile("keyring.pgp"))
    }

    @Test
    fun `arbitrary bytes survive the round trip`() {
        // Key rings are binary, so the full byte range has to come back unchanged.
        val data = ByteArray(256) { it.toByte() }

        storage.writeToFile("binary.pgp", data)

        assertArrayEquals(data, storage.readFromFile("binary.pgp"))
    }

    @Test
    fun `writing again replaces the previous contents rather than appending`() {
        storage.writeToFile("keyring.pgp", "a much longer original value".toByteArray())
        storage.writeToFile("keyring.pgp", "short".toByteArray())

        assertArrayEquals("short".toByteArray(), storage.readFromFile("keyring.pgp"))
    }

    @Test
    fun `files land in the app's private storage`() {
        storage.writeToFile("keyring.pgp", "secret".toByteArray())

        val file = context.getFileStreamPath("keyring.pgp")
        assertTrue("expected $file to exist", file.exists())
        assertEquals(context.filesDir, file.parentFile)
    }

    @Test(expected = FileNotFoundException::class)
    fun `reading a file that was never written fails loudly`() {
        storage.readFromFile("absent.pgp")
    }
}
