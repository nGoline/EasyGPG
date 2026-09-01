package com.ngoline.easygpg

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The stored-key-ring half of `PGPKeyManager`, which no JVM test can reach: every read and write
 * goes through an Android Keystore key, and Robolectric provides no `AndroidKeyStore` provider.
 *
 * These cover only the key that needs no user authentication (`KEY_ALIAS`, used for public and
 * imported key rings), so they run on a bare emulator with no lock screen configured. The
 * authentication-bound paths are in [PGPKeyManagerSecretKeyTest].
 */
@RunWith(AndroidJUnit4::class)
class PGPKeyManagerStorageTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var manager: PGPKeyManager

    private val passphrase = "correct horse".toCharArray()

    @Before
    fun setUp() {
        context.filesDir.listFiles()?.forEach { it.delete() }
        manager = PGPKeyManager(context)
    }

    /** `importPublicKey` shows a Toast, which needs a prepared Looper. */
    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runCatching(block)
        }
        return result!!.getOrThrow()
    }

    @Test
    fun anImportedPublicKeyComesBackWithTheSameFingerprint() {
        val (_, publicKeyRing) = TestKeyRings.generate(passphrase.copyOf())
        val expected = TestKeyRings.encryptionKey(publicKeyRing)

        val imported = onMain { manager.importPublicKey("alice", TestKeyRings.armor(publicKeyRing)) }

        assertNotNull("import returned no key", imported)
        val listed = manager.getAllPublicKeys()
        assertEquals(1, listed.size)
        assertEquals("alice", listed[0].alias)
        // The ring must survive the round trip, not just the primary key.
        assertTrue(
            "encryption subkey missing after import",
            listed[0].publicKeyRing.publicKeys.asSequence().any {
                it.keyID == expected.keyID
            },
        )
    }

    @Test
    fun anImportedKeyRingIsEncryptedOnDisk() {
        val (_, publicKeyRing) = TestKeyRings.generate(passphrase.copyOf())

        onMain { manager.importPublicKey("alice", TestKeyRings.armor(publicKeyRing)) }

        val file = File(context.filesDir, "alice.imported.pgp")
        assertTrue("expected $file", file.exists())
        val raw = file.readBytes()
        // The envelope is [IV length][IV][ciphertext]; armored text would start with '-'.
        assertFalse(
            "key ring was stored in the clear",
            String(raw.copyOfRange(0, minOf(10, raw.size))).contains("-----BEGIN"),
        )
    }

    @Test
    fun importedAndOwnKeysAreListedSeparately() {
        val (_, theirs) = TestKeyRings.generate(passphrase.copyOf())
        onMain { manager.importPublicKey("bob", TestKeyRings.armor(theirs)) }

        // A key ring the user owns is written with the .public_keyring.pgp suffix.
        val (_, mine) = TestKeyRings.generate(passphrase.copyOf())
        writeOwnPublicKeyRing("me", mine)

        assertEquals(listOf("bob"), manager.getAllPublicKeys().map { it.alias })
        assertEquals(listOf("me"), manager.getMyPublicKeys().map { it.alias })
    }

    @Test
    fun aPublicKeyRingLeftUnencryptedIsEncryptedOnFirstRead() {
        // Written by a version of the app that stored key rings in the clear.
        val (_, publicKeyRing) = TestKeyRings.generate(passphrase.copyOf())
        val file = File(context.filesDir, "legacy.imported.pgp")
        file.writeText(TestKeyRings.armor(publicKeyRing))

        val listed = manager.getAllPublicKeys()

        assertEquals(1, listed.size)
        assertFalse(
            "file should have been encrypted on read",
            file.readText().startsWith("-----BEGIN"),
        )
        // Still readable after the upgrade.
        assertEquals(1, PGPKeyManager(context).getAllPublicKeys().size)
    }

    @Test
    fun anInvalidPublicKeyIsRejected() {
        val imported = onMain { manager.importPublicKey("junk", "not a key at all") }

        assertNull(imported)
        assertTrue(manager.getAllPublicKeys().isEmpty())
    }

    @Test
    fun deletingAKeyRemovesBothItsFiles() {
        val (_, mine) = TestKeyRings.generate(passphrase.copyOf())
        writeOwnPublicKeyRing("me", mine)
        File(context.filesDir, "me$SECRET_KEYRING_SUFFIX").writeText("whatever")
        assertTrue(manager.hasKeys())

        assertTrue(manager.deleteMyKey("me"))

        assertFalse(manager.hasKeys())
        assertFalse(File(context.filesDir, "me$SECRET_KEYRING_SUFFIX").exists())
        assertFalse(File(context.filesDir, "me.public_keyring.pgp").exists())
    }

    @Test
    fun deletingAKeyThatIsNotThereReportsFailure() {
        assertFalse(manager.deleteMyKey("nobody"))
    }

    /** Stores a public key ring under the user's own alias, unencrypted so the app upgrades it. */
    private fun writeOwnPublicKeyRing(alias: String, ring: org.bouncycastle.openpgp.PGPPublicKeyRing) {
        File(context.filesDir, "$alias.public_keyring.pgp").writeText(TestKeyRings.armor(ring))
    }
}
