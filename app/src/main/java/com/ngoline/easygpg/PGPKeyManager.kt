package com.ngoline.easygpg

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.widget.Toast
import com.ngoline.easygpg.data.KeyItem
import java.io.ByteArrayInputStream
import java.io.File
import java.security.SecureRandom
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.collections.filter
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
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
import java.security.KeyStore
import androidx.preference.PreferenceManager
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream

const val BcPGPVersion: Int = 4 // Use version 4 for ECDH keys
const val LOG_TAG = "PGPKeyManager"
const val ANDROID_KEYSTORE = "AndroidKeyStore"
const val KEY_ALIAS = "easygpg_aes_key"

object PGPConstants {
    const val PGP_MARKER = "-----BEGIN PGP MESSAGE-----"
    const val OBFUSCATED_MARKER = "00023CD1"
}

class PGPKeyManager(private val context: Context) {


    fun generateAndSaveKeys(alias: String) {
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
            val bcEdKeyPair = BcPGPKeyPair(BcPGPVersion, PublicKeyAlgorithmTags.Ed25519, edKeyPair, Date())

            // --- Curve25519 for encryption (subkey) ---
            val ecdhGen = org.bouncycastle.crypto.generators.X25519KeyPairGenerator()
            ecdhGen.init(org.bouncycastle.crypto.params.X25519KeyGenerationParameters(SecureRandom()))
            val ecdhKeyPair = ecdhGen.generateKeyPair()
            val bcEcdhKeyPair = BcPGPKeyPair(BcPGPVersion, PublicKeyAlgorithmTags.ECDH, ecdhKeyPair, Date())

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

            saveKeyRing(secretKeyRing, "$alias.secret_keyring.pgp")
            saveKeyRing(publicKeyRing, "$alias.public_keyring.pgp")
        } catch (e: Exception) {
            Toast.makeText(context,
                context.getString(R.string.failed_to_generateandsavekeys, e.localizedMessage), Toast.LENGTH_LONG).show()
        }
    }

    fun hasKeys(): Boolean {
        val files = context.filesDir.listFiles() ?: return false
        return files.any { it.isFile && it.name.endsWith(".public_keyring.pgp") }
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
                    keys.add(
                        KeyItem(
                            file.name.replace(".imported.pgp", ""),
                            fingerprint,
                            publicKey,
                            it
                        )
                    )
                }
            }
        }
        return keys
    }

    fun getMyPublicKeys(): MutableList<KeyItem> {
        val keys = mutableListOf<KeyItem>()
        val files = context.filesDir.listFiles() ?: return keys
        files.filter { it.isFile && it.name.endsWith(".public_keyring.pgp") }.forEach { file ->
            val publicKeyRing = loadImportedKeyRing(file)
            publicKeyRing?.let {
                val publicKey = it.publicKeys.next()
                if (publicKey != null) {
                    val fingerprint = String(Hex.encode(publicKey.fingerprint))
                    keys.add(
                        KeyItem(
                            file.name.replace(".public_keyring.pgp", ""),
                            fingerprint,
                            publicKey,
                            it
                        )
                    )
                }
            }
        }
        return keys
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
                            Toast.makeText(context,
                                context.getString(R.string.public_key_imported_successfully), Toast.LENGTH_SHORT).show()
                            return obj.publicKey
                        }
                        is PGPPublicKey -> {
                            // Wrap single key in a keyring
                            val keyRing = PGPPublicKeyRing(listOf(obj))
                            saveImportedKeyRing(alias, keyRing)
                            Toast.makeText(context, context.getString(R.string.public_key_imported_successfully), Toast.LENGTH_SHORT).show()
                            return obj
                        }
                    }
                }
                Toast.makeText(context,
                    context.getString(R.string.invalid_public_key_format), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context,
                context.getString(R.string.failed_to_import_public_key, e.localizedMessage), Toast.LENGTH_LONG).show()
        }

        return null
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun encryptToFile(plainData: ByteArray, file: File) {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        file.outputStream().use { fos ->
            fos.write(iv.size)
            fos.write(iv)
            CipherOutputStream(fos, cipher).use { cos ->
                cos.write(plainData)
            }
        }
    }

    private fun decryptFromFile(file: File): ByteArray {
        val secretKey = getOrCreateSecretKey()
        FileInputStream(file).use { baseFis ->
            val fis = BufferedInputStream(baseFis)
            fis.mark(file.length().toInt() + 1) // mark at the start, with enough limit
            val ivSize = fis.read()
            if (ivSize in 12..16) { // likely encrypted (GCM IV is 12-16 bytes)
                val iv = ByteArray(ivSize)
                val readIv = fis.read(iv)
                if (readIv == ivSize) {
                    try {
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
                        CipherInputStream(fis, cipher).use { cis ->
                            return cis.readBytes()
                        }
                    } catch (e: Exception) {
                        // fall through to plaintext migration
                        Log.e(LOG_TAG, "Decryption failed: ${e.message}. File is plaintext, migrating.")
                    }
                }
            }
            // Not encrypted or failed to decrypt: migrate plaintext to encrypted
            fis.reset()
            val plainData = fis.readBytes()
            // Re-encrypt and overwrite file
            encryptToFile(plainData, file)
            return plainData
        }
    }

    private fun saveImportedKeyRing(alias: String, publicKeyRing: PGPPublicKeyRing) {
        val filename = "$alias.imported.pgp"
        val file = File(context.filesDir, filename)
        val baos = ByteArrayOutputStream()
        ArmoredOutputStream(baos).use { aos ->
            publicKeyRing.encode(aos)
        }
        encryptToFile(baos.toByteArray(), file)
        Toast.makeText(context,
            context.getString(R.string.public_key_saved_successfully_under_alias, alias), Toast.LENGTH_SHORT).show()
    }

    fun saveYubikeyKeyRing(alias: String, publicKeyRing: PGPPublicKeyRing) {
        val filename = "$alias.public_smartcard_keyring.pgp"
        val file = File(context.filesDir, filename)
        val baos = ByteArrayOutputStream()
        ArmoredOutputStream(baos).use { aos ->
            publicKeyRing.encode(aos)
        }
        encryptToFile(baos.toByteArray(), file)
        Toast.makeText(context,
            context.getString(R.string.public_key_saved_successfully_under_alias, alias), Toast.LENGTH_SHORT).show()
    }

    private fun saveKeyRing(keyRing: PGPKeyRing, filename: String) {
        val file = File(context.filesDir, filename)
        val baos = ByteArrayOutputStream()
        ArmoredOutputStream(baos).use { aos ->
            keyRing.encode(aos)
        }
        encryptToFile(baos.toByteArray(), file)
    }

    fun loadImportedKeyRing(file: File): PGPPublicKeyRing? {
        val data = decryptFromFile(file)
        return PGPPublicKeyRing(PGPUtil.getDecoderStream(data.inputStream()), BcKeyFingerprintCalculator())
    }

    private fun tryDecryptWithKeyring(secretKeyRing: PGPSecretKeyRing, encryptedMessage: String): String? {
        try {
            val decoderStream = PGPUtil.getDecoderStream(encryptedMessage.byteInputStream())
            val pgpFactory = PGPObjectFactory(decoderStream, BcKeyFingerprintCalculator())
            var encList: PGPEncryptedDataList? = null
            var obj = pgpFactory.nextObject()
            if (obj is PGPEncryptedDataList) {
                encList = obj
            } else {
                obj = pgpFactory.nextObject()
                if (obj is PGPEncryptedDataList) {
                    encList = obj
                }
            }
            if (encList == null) return null
            var privateKey: PGPPrivateKey? = null
            var encData: org.bouncycastle.openpgp.PGPPublicKeyEncryptedData? = null
            val it = encList.encryptedDataObjects
            while (it.hasNext()) {
                val edata = it.next()
                if (edata is org.bouncycastle.openpgp.PGPPublicKeyEncryptedData) {
                    val secretKey = secretKeyRing.getSecretKey(edata.keyIdentifier)
                    if (secretKey != null) {
                        privateKey = secretKey.extractPrivateKey(
                            BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build("passphrase".toCharArray())
                        )
                        encData = edata
                        break
                    }
                }
            }
            if (privateKey == null || encData == null) return null
            val clear = encData.getDataStream(BcPublicKeyDataDecryptorFactory(privateKey))
            val plainFactory = PGPObjectFactory(clear, BcKeyFingerprintCalculator())
            var message: Any? = plainFactory.nextObject()
            while (message != null) {
                if (message is PGPLiteralData) {
                    val inputStream = message.inputStream
                    return inputStream.readBytes().toString(Charsets.UTF_8)
                }
                message = plainFactory.nextObject()
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Decryption failed: ${e.message}")
            return null
        }
        return null
    }

    fun decryptMessage(encryptedMessage: String, passphrase: String = "passphrase"): String {
        val files = context.filesDir.listFiles()
        if (files == null) return context.getString(R.string.no_key_files_found)

        // Deobfuscate markers if present
        var encryptedMessage = encryptedMessage
        val foundObf = encryptedMessage.trimStart().startsWith(PGPConstants.OBFUSCATED_MARKER)
        if (foundObf) {
            Log.d(LOG_TAG, "Detected obfuscated PGP message, deobfuscating markers.")
            encryptedMessage = deobfuscateMarkers(encryptedMessage)
        }

        val secretKeyFiles = files.filter { it.isFile && it.name.endsWith(".secret_keyring.pgp") }
        if (secretKeyFiles.isEmpty()) return context.getString(R.string.no_private_keys_available)
        for (file in secretKeyFiles) {
            try {
                val data = decryptFromFile(file)
                val secretKeyRing = PGPSecretKeyRing(
                    PGPUtil.getDecoderStream(data.inputStream()),
                    BcKeyFingerprintCalculator()
                )
                try {
                    // Try to decrypt with this keyring
                    val decrypted = tryDecryptWithKeyring(secretKeyRing, encryptedMessage)
                    if (decrypted != null) return decrypted
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Failed to decrypt with keyring ${file.name}: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to load keyring ${file.name}: ${e.message}")
            }
        }
        return context.getString(R.string.failed_to_decrypt_with_any_available_private_key)
    }

    fun encryptMessage(message: String, publicKey: PGPPublicKey): String {
        try {
            val encryptedData = ByteArrayOutputStream()
            val armorStream = ArmoredOutputStream(encryptedData)

            val encGen = PGPEncryptedDataGenerator(
                JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                    .setWithIntegrityPacket(true)
                    .setSecureRandom(SecureRandom())
                    .setProvider("BC")
            )

            val keyEncryptionMethodGenerator = JcePublicKeyKeyEncryptionMethodGenerator(publicKey).setProvider("BC")
            encGen.addMethod(keyEncryptionMethodGenerator)

            val encOut = encGen.open(armorStream, ByteArray(4096))
            val lData = PGPLiteralDataGenerator()
            val pOut = lData.open(encOut, PGPLiteralData.BINARY, "filename", message.toByteArray().size.toLong(), Date())
            pOut.write(message.toByteArray())
            pOut.close()

            encOut.close()
            armorStream.close()

            var encryptedMessage = String(encryptedData.toByteArray())
            if (isObfuscateMarkersEnabled()) {
                Log.d(LOG_TAG, "Obfuscating PGP markers in encrypted message.")
                encryptedMessage = obfuscateMarkers(encryptedMessage)
            }
            return encryptedMessage
        } catch (e: Exception) {
            e.printStackTrace()
            return context.getString(R.string.encryption_failed)
        }
    }

    private fun isObfuscateMarkersEnabled(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(context.getString(R.string.obfuscate_pgp_markers), false)
    }

    private fun obfuscateMarkers(input: String): String {
        // Replace all PGP marker lines and version/comment lines with 00023CD1
        return input.lines().joinToString("") { line ->
            if (line.startsWith("-----BEGIN ") ||
                line.startsWith("-----END ") ||
                line.isBlank()) PGPConstants.OBFUSCATED_MARKER
            else if (line.startsWith("Version:") ||
                line.startsWith("Comment:")) ""
            else line
        }
    }

    private fun deobfuscateMarkers(input: String): String {
        // Remove all obfuscated markers, add BEGIN/END markers, and break lines to 64 chars
        val clean = input.replace(PGPConstants.OBFUSCATED_MARKER, "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        // Separate checksum if present (starts with '=')
        val checksumIndex = clean.lastIndexOf('=')
        val (base64Data, checksum) = if (checksumIndex != -1 && clean.length - checksumIndex <= 5) {
            clean.substring(0, checksumIndex) to clean.substring(checksumIndex)
        } else {
            clean to null
        }
        val sb = StringBuilder()
        sb.append(PGPConstants.PGP_MARKER).append("\n\n")
        var i = 0
        while (i < base64Data.length) {
            val end = (i + 64).coerceAtMost(base64Data.length)
            sb.append(base64Data.substring(i, end)).append("\n")
            i = end
        }
        if (checksum != null) {
            sb.append(checksum).append("\n")
        }
        val endMarker = PGPConstants.PGP_MARKER.replace("BEGIN", "END")
        sb.append(endMarker).append("\n")
        return sb.toString()
    }
}