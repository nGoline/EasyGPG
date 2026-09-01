package com.ngoline.easygpg

import android.app.KeyguardManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The authentication-bound half: secret key rings are sealed with a Keystore key created with
 * `setUserAuthenticationRequired(true)`, so these need a device with a secure lock screen that has
 * been unlocked recently — the key is valid for [SECRET_KEY_AUTH_VALIDITY_SECONDS] after an unlock,
 * and a device credential unlock counts as the authentication.
 *
 * On a device with no lock screen the Keystore key cannot even be created, so these skip rather
 * than fail. CI sets a PIN and unlocks before running them; see the `instrumented` job in
 * `.github/workflows/ci.yml`.
 */
@RunWith(AndroidJUnit4::class)
class PGPKeyManagerSecretKeyTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var manager: PGPKeyManager

    private val passphrase = "correct horse".toCharArray()

    @Before
    fun setUp() {
        val keyguard = context.getSystemService<KeyguardManager>()!!
        assumeTrue(
            "needs a secure lock screen; run with a PIN set and the device unlocked",
            keyguard.isDeviceSecure,
        )
        context.filesDir.listFiles()?.forEach { it.delete() }
        manager = PGPKeyManager(context)
    }

    @Test
    fun aGeneratedKeyRingDecryptsItsOwnMessages() {
        assertTrue(manager.generateAndSaveKeys("me", passphrase.copyOf()))

        val publicKey = TestKeyRings.encryptionKey(manager.getMyPublicKeys().single().publicKeyRing)
        val armored = manager.encryptMessage("attack at dawn".toCharArray(), publicKey)

        val result = manager.decryptMessage(armored, passphrase.copyOf())

        assertTrue("expected Decrypted, got $result", result is DecryptionResult.Decrypted)
        assertEquals(
            "attack at dawn",
            String((result as DecryptionResult.Decrypted).plaintext),
        )
    }

    @Test
    fun aGeneratedSecretKeyRingIsEncryptedOnDisk() {
        assertTrue(manager.generateAndSaveKeys("me", passphrase.copyOf()))

        val file = File(context.filesDir, "me$SECRET_KEYRING_SUFFIX")
        assertTrue("expected $file", file.exists())
        val head = String(file.readBytes().let { it.copyOfRange(0, minOf(20, it.size)) })
        assertFalse("secret key ring stored in the clear", head.contains("-----BEGIN"))
    }

    @Test
    fun theWrongPassphraseIsReportedRatherThanTreatedAsNoKey() {
        assertTrue(manager.generateAndSaveKeys("me", passphrase.copyOf()))
        val publicKey = TestKeyRings.encryptionKey(manager.getMyPublicKeys().single().publicKeyRing)
        val armored = manager.encryptMessage("attack at dawn".toCharArray(), publicKey)

        val result = manager.decryptMessage(armored, "wrong".toCharArray())

        // The UI distinguishes these: a wrong passphrase is worth re-prompting for, a missing key
        // is not.
        assertTrue("expected WrongPassphrase, got $result", result is DecryptionResult.WrongPassphrase)
    }

    @Test
    fun aMessageForSomebodyElseReportsNoUsableKey() {
        assertTrue(manager.generateAndSaveKeys("me", passphrase.copyOf()))

        // Encrypted to a key ring this device does not hold.
        val (_, strangers) = TestKeyRings.generate("other".toCharArray())
        val armored = manager.encryptMessage(
            "attack at dawn".toCharArray(),
            TestKeyRings.encryptionKey(strangers),
        )

        val result = manager.decryptMessage(armored, passphrase.copyOf())

        assertTrue("expected NoUsableKey, got $result", result is DecryptionResult.NoUsableKey)
    }

    @Test
    fun anObfuscatedMessageRoundTripsThroughDecrypt() {
        assertTrue(manager.generateAndSaveKeys("me", passphrase.copyOf()))
        val publicKey = TestKeyRings.encryptionKey(manager.getMyPublicKeys().single().publicKeyRing)
        val armored = manager.encryptMessage("attack at dawn".toCharArray(), publicKey)

        // decryptMessage deobfuscates on the way in, so an obfuscated message must decrypt too.
        val obfuscated = armored.lines().joinToString("") { line ->
            if (line.startsWith("-----BEGIN ") || line.startsWith("-----END ") || line.isBlank()) {
                PGPConstants.OBFUSCATED_MARKER
            } else if (line.startsWith("Version:") || line.startsWith("Comment:")) {
                ""
            } else {
                line
            }
        }

        val result = manager.decryptMessage(obfuscated, passphrase.copyOf())

        assertTrue("expected Decrypted, got $result", result is DecryptionResult.Decrypted)
        assertEquals(
            "attack at dawn",
            String((result as DecryptionResult.Decrypted).plaintext),
        )
    }

    @Test
    fun aKeyRingLeftUnderTheLegacyPassphraseIsMigrated() {
        // An older version protected every key ring with a passphrase baked into the app and left
        // the file unencrypted; both must be upgraded.
        val legacyPassphrase = "passphrase".toCharArray()
        val (secretKeyRing, _) = TestKeyRings.generate(legacyPassphrase.copyOf())
        val file = File(context.filesDir, "old$SECRET_KEYRING_SUFFIX")
        file.writeText(TestKeyRings.armorSecret(secretKeyRing))

        assertTrue("legacy key ring not detected", manager.hasLegacyProtectedKeys())

        assertEquals(1, manager.migrateLegacyKeyPassphrases(passphrase.copyOf()))

        assertFalse("still reported as legacy", PGPKeyManager(context).hasLegacyProtectedKeys())
        assertFalse(
            "file left unencrypted after migration",
            file.readText().startsWith("-----BEGIN"),
        )
    }

    @Test
    fun aMigratedKeyRingDecryptsWithTheNewPassphrase() {
        val legacyPassphrase = "passphrase".toCharArray()
        val (secretKeyRing, publicKeyRing) = TestKeyRings.generate(legacyPassphrase.copyOf())
        File(context.filesDir, "old$SECRET_KEYRING_SUFFIX")
            .writeText(TestKeyRings.armorSecret(secretKeyRing))

        assertEquals(1, manager.migrateLegacyKeyPassphrases(passphrase.copyOf()))

        val armored = manager.encryptMessage(
            "attack at dawn".toCharArray(),
            TestKeyRings.encryptionKey(publicKeyRing),
        )
        val result = manager.decryptMessage(armored, passphrase.copyOf())

        assertTrue("expected Decrypted, got $result", result is DecryptionResult.Decrypted)
        assertEquals("attack at dawn", String((result as DecryptionResult.Decrypted).plaintext))

        // And the legacy passphrase must no longer work.
        assertTrue(manager.decryptMessage(armored, "passphrase".toCharArray()) is DecryptionResult.WrongPassphrase)
    }

    @Test
    fun keyRingsAlreadyOnANewPassphraseAreNotReportedAsLegacy() {
        assertTrue(manager.generateAndSaveKeys("me", passphrase.copyOf()))

        assertFalse(manager.hasLegacyProtectedKeys())
        assertEquals(0, manager.migrateLegacyKeyPassphrases("another".toCharArray()))
    }
}
