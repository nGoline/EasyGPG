package com.ngoline.easygpg

import android.content.Context
import android.widget.Toast
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPEncryptedData
import org.bouncycastle.openpgp.PGPKeyRing
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.util.Date

class PGPKeyManager(private val context: Context) {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    fun generateAndSaveKeys() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        val pgpKeyPair = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, keyPair, Date())
        val digestCalculator = BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1)
        val signerBuilder = JcaPGPContentSignerBuilder(pgpKeyPair.publicKey.algorithm, HashAlgorithmTags.SHA256)
        val encryptorBuilder = JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, digestCalculator).setSecureRandom(SecureRandom()).build("passphrase".toCharArray())

        val secretKey = PGPSecretKey(
            PGPSignature.DEFAULT_CERTIFICATION,
            pgpKeyPair,
            "user@example.com",
            digestCalculator,
            null,
            null,
            signerBuilder,
            encryptorBuilder
        )

        val secretKeyRing = PGPSecretKeyRing(listOf(secretKey))
        val publicKeyRing = PGPPublicKeyRing(listOf(secretKey.publicKey))

        saveKeyRing(secretKeyRing, "secret_keyring.pgp")
        saveKeyRing(publicKeyRing, "public_keyring.pgp")
    }

    fun getAllPublicKeys(): MutableList<KeyItem> {
        val keys = mutableListOf<KeyItem>()
        val files = context.filesDir.listFiles() ?: return keys
        files.filter { it.isFile && it.name.endsWith(".imported.pgp") }.forEach { file ->
            val publicKeyRing = loadImportedKeyRing(file)
            publicKeyRing?.let {
                val publicKey = it.publicKeys.next()
                if (publicKey != null) {
                    val fingerprint = String(Hex.encode(publicKey.fingerprint))
                    keys.add(KeyItem(file.name.replace(".imported.pgp", ""), fingerprint, publicKey))
                }
            }
        }
        return keys
    }

    fun loadPublicKeyRing(): PGPPublicKeyRing? {
        context.openFileInput("public_keyring.pgp").use { fis ->
            // Use an ArmoredInputStream if the data is stored in armored format.
            ArmoredInputStream(fis).use { ais ->
                val pgpObjectFactory = PGPObjectFactory(PGPUtil.getDecoderStream(ais), BcKeyFingerprintCalculator())
                var obj: Any?
                while (pgpObjectFactory.nextObject().also { obj = it } != null) {
                    // Check if the decoded object is a public key ring
                    if (obj is PGPPublicKeyRing) {
                        return obj as PGPPublicKeyRing
                    }
                }
            }
        }
        return null  // Return null if no public key ring found
    }

    fun loadSecretKeyRing(): PGPSecretKeyRing? {
        context.openFileInput("secret_keyring.pgp").use { fis ->
            // Use ArmoredInputStream if the keyring is stored in an armored format.
            ArmoredInputStream(fis).use { ais ->
                val pgpObjFactory = PGPObjectFactory(PGPUtil.getDecoderStream(ais), JcaKeyFingerprintCalculator())
                var obj: Any?

                while (pgpObjFactory.nextObject().also { obj = it } != null) {
                    if (obj is PGPSecretKeyRing) {
                        return obj as PGPSecretKeyRing
                    }
                }
            }
        }
        return null  // Return null if no secret key ring found
    }

    fun importPublicKey(alias: String, keyData: String): PGPPublicKey? {
        try {
            val inputStream = ByteArrayInputStream(keyData.toByteArray())
            ArmoredInputStream(inputStream).use { ais ->
                val pgpObjectFactory = PGPObjectFactory(PGPUtil.getDecoderStream(ais), JcaKeyFingerprintCalculator())
                var obj: Any?

                while (pgpObjectFactory.nextObject().also { obj = it } != null) {
                    if (obj is PGPPublicKeyRing) {
                        val pkr = obj as PGPPublicKeyRing
                        // Process the imported public key ring
                        saveImportedKeyRing(alias, pkr)
                        Toast.makeText(context, "Public key imported successfully", Toast.LENGTH_SHORT).show()

                        return pkr.publicKey
                    }
                }
                Toast.makeText(context, "Invalid public key format", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to import public key: ${e.message}", Toast.LENGTH_LONG).show()
        }

        return null
    }

    private fun saveImportedKeyRing(alias: String, publicKeyRing: PGPPublicKeyRing) {
        val filename = "$alias.imported.pgp" // Name of the file to save the keys

        context?.openFileOutput(filename, Context.MODE_PRIVATE).use { fos ->
            ArmoredOutputStream(fos).use { aos ->
                publicKeyRing.encode(aos)
            }
        }

        // Notify the user or the app that the key has been saved successfully
        Toast.makeText(context, "Public key saved successfully under alias '$alias'", Toast.LENGTH_SHORT).show()
    }

    private fun saveKeyRing(keyRing: PGPKeyRing, filename: String) {
        context.openFileOutput(filename, Context.MODE_PRIVATE).use { fos ->
            ArmoredOutputStream(fos).use { aos ->
                keyRing.encode(aos)
            }
        }
    }

    private fun loadImportedKeyRing(file: File): PGPPublicKeyRing? {
        file.inputStream().use { fis ->
            return PGPPublicKeyRing(PGPUtil.getDecoderStream(fis), JcaKeyFingerprintCalculator())
        }
    }
}