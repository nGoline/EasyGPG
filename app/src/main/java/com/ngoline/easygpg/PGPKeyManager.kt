package com.ngoline.easygpg

import android.content.Context
import android.widget.Toast
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPEncryptedData
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPKeyRing
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.security.Security
import java.util.Date

class PGPKeyManager(private val context: Context) {

    init {
        val bcProvider = BouncyCastleProvider()
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME) // Remove old BC
        Security.insertProviderAt(bcProvider, 1) // Insert your BC at highest priority
    }

    fun generateAndSaveKeys() {
        try {
            // --- Ed25519 for signing (primary key) ---
            val edKeyGen = Ed25519KeyPairGenerator()
            edKeyGen.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val edKeyPair = edKeyGen.generateKeyPair()

            val digestCalculator = BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1)
            val encryptorBuilder = BcPBESecretKeyEncryptorBuilder(
                PGPEncryptedData.AES_256,
                digestCalculator
            ).setSecureRandom(SecureRandom()).build("passphrase".toCharArray())
            val signerBuilder = BcPGPContentSignerBuilder(PublicKeyAlgorithmTags.Ed25519, HashAlgorithmTags.SHA256)
            val bcEdKeyPair = BcPGPKeyPair(PublicKeyAlgorithmTags.Ed25519, edKeyPair, Date())

            // --- Curve25519 for encryption (subkey) ---
            val ecdhGen = org.bouncycastle.crypto.generators.X25519KeyPairGenerator()
            ecdhGen.init(org.bouncycastle.crypto.params.X25519KeyGenerationParameters(SecureRandom()))
            val ecdhKeyPair = ecdhGen.generateKeyPair()
            val bcEcdhKeyPair = BcPGPKeyPair(PublicKeyAlgorithmTags.ECDH, ecdhKeyPair, Date())

            // --- Create secret key ring with subkey ---
            val secretKey = PGPSecretKey(
                PGPSignature.DEFAULT_CERTIFICATION,
                bcEdKeyPair,
                "user@example.com",
                digestCalculator,
                null,
                null,
                signerBuilder,
                encryptorBuilder
            )
            val subkey = PGPSecretKey(
                bcEdKeyPair,
                bcEcdhKeyPair,
                digestCalculator,
                signerBuilder,
                encryptorBuilder
            )

            val secretKeyRing = PGPSecretKeyRing(listOf(secretKey, subkey))
            val publicKeyRing = PGPPublicKeyRing(listOf(secretKey.publicKey, subkey.publicKey))

            saveKeyRing(secretKeyRing, "secret_keyring.pgp")
            saveKeyRing(publicKeyRing, "public_keyring.pgp")
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to generateAndSaveKeys: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
                    keys.add(KeyItem(file.name.replace(".imported.pgp", ""), fingerprint, publicKey, it))
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
                val pgpObjFactory = PGPObjectFactory(PGPUtil.getDecoderStream(ais), BcKeyFingerprintCalculator())
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
                val pgpObjectFactory = PGPObjectFactory(PGPUtil.getDecoderStream(ais), BcKeyFingerprintCalculator())
                var obj: Any?

                while (pgpObjectFactory.nextObject().also { obj = it } != null) {
                    when (obj) {
                        is PGPPublicKeyRing -> {
                            saveImportedKeyRing(alias, obj)
                            Toast.makeText(context, "Public key imported successfully", Toast.LENGTH_SHORT).show()
                            return obj.publicKey
                        }
                        is PGPPublicKey -> {
                            // Wrap single key in a keyring
                            val keyRing = PGPPublicKeyRing(listOf(obj))
                            saveImportedKeyRing(alias, keyRing)
                            Toast.makeText(context, "Public key imported successfully", Toast.LENGTH_SHORT).show()
                            return obj
                        }
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

    public fun loadImportedKeyRing(file: File): PGPPublicKeyRing? {
        file.inputStream().use { fis ->
            return PGPPublicKeyRing(PGPUtil.getDecoderStream(fis), BcKeyFingerprintCalculator())
        }
    }

    fun decryptMessage(encryptedMessage: String, passphrase: String = "passphrase"): String {
        val secretKeyRing = loadSecretKeyRing() ?: throw Exception("No secret key ring found")
        val decoderStream = PGPUtil.getDecoderStream(ByteArrayInputStream(encryptedMessage.toByteArray()))
        val pgpFactory = PGPObjectFactory(decoderStream, BcKeyFingerprintCalculator())
        var encList: PGPEncryptedDataList? = null

        // Find the encrypted data list
        var obj = pgpFactory.nextObject()
        if (obj is PGPEncryptedDataList) {
            encList = obj
        } else {
            // Sometimes the first object is a marker packet, try next
            obj = pgpFactory.nextObject()
            if (obj is PGPEncryptedDataList) {
                encList = obj
            }
        }
        if (encList == null) throw Exception("No encrypted data found")

        // Find the secret key and private key
        var privateKey: PGPPrivateKey? = null
        var encData: org.bouncycastle.openpgp.PGPPublicKeyEncryptedData? = null
        val it = encList.encryptedDataObjects
        while (it.hasNext()) {
            val edata = it.next()
            if (edata is org.bouncycastle.openpgp.PGPPublicKeyEncryptedData) {
                val secretKey: PGPSecretKey? = secretKeyRing.getSecretKey(edata.keyID)
                if (secretKey != null) {
                    privateKey = secretKey.extractPrivateKey(
                        BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(passphrase.toCharArray())
                    )
                    encData = edata
                    break
                }
            }
        }
        if (privateKey == null || encData == null) throw Exception("No matching private key found")

        // Decrypt the data
        val clear = encData.getDataStream(BcPublicKeyDataDecryptorFactory(privateKey))
        val plainFactory = PGPObjectFactory(clear, BcKeyFingerprintCalculator())
        var message: Any? = plainFactory.nextObject()
        while (message != null) {
            if (message is PGPLiteralData) {
                val inputStream: InputStream = message.inputStream
                return inputStream.readBytes().toString(Charsets.UTF_8)
            }
            message = plainFactory.nextObject()
        }
        throw Exception("No literal data found")
    }
}