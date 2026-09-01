package com.ngoline.easygpg

import java.util.Date
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.openpgp.PGPEncryptedData
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
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
import java.io.ByteArrayOutputStream
import org.bouncycastle.bcpg.ArmoredOutputStream

/**
 * Key rings built in-process with Bouncy Castle alone, so tests can exercise the PGP paths without
 * going near the Android Keystore. Mirrors the shape `PGPKeyManager.generateAndSaveKeys` produces:
 * an Ed25519 signing primary key with an X25519 encryption subkey.
 *
 * Shared by the JVM (Robolectric) and instrumented test source sets — see the `testShared`
 * source directory wired up in `app/build.gradle.kts`.
 */
object TestKeyRings {

    /** A key ring whose secret keys are protected by [passphrase]. */
    fun generate(passphrase: CharArray, userId: String = "test@example.com"): Pair<PGPSecretKeyRing, PGPPublicKeyRing> {
        val digestCalculator = BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1)
        val encryptorBuilder = BcPBESecretKeyEncryptorBuilder(
            PGPEncryptedData.AES_256,
            BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1)
        ).build(passphrase)
        val signerBuilder =
            BcPGPContentSignerBuilder(PublicKeyAlgorithmTags.Ed25519, HashAlgorithmTags.SHA256)

        val edGen = Ed25519KeyPairGenerator()
        edGen.init(Ed25519KeyGenerationParameters(java.security.SecureRandom()))
        val signingPair = BcPGPKeyPair(
            BcPGPVersion, PublicKeyAlgorithmTags.Ed25519, edGen.generateKeyPair(), Date()
        )

        val ecdhGen = X25519KeyPairGenerator()
        ecdhGen.init(X25519KeyGenerationParameters(java.security.SecureRandom()))
        val encryptionPair = BcPGPKeyPair(
            BcPGPVersion, PublicKeyAlgorithmTags.ECDH, ecdhGen.generateKeyPair(), Date()
        )

        val primary = PGPSecretKey(
            PGPSignature.DEFAULT_CERTIFICATION,
            signingPair,
            userId,
            digestCalculator,
            null,
            null,
            signerBuilder,
            encryptorBuilder
        )
        val subkey = PGPSecretKey(
            signingPair, encryptionPair, digestCalculator, signerBuilder, encryptorBuilder
        )

        return PGPSecretKeyRing(listOf(primary, subkey)) to
            PGPPublicKeyRing(listOf(primary.publicKey, subkey.publicKey))
    }

    /** The subkey the app encrypts to — `EncryptFragment` picks by `isEncryptionKey`. */
    fun encryptionKey(publicKeyRing: PGPPublicKeyRing): PGPPublicKey =
        publicKeyRing.publicKeys.asSequence().first { it.isEncryptionKey }

    /** The Ed25519 primary key, which cannot encrypt. */
    fun signingKey(publicKeyRing: PGPPublicKeyRing): PGPPublicKey =
        publicKeyRing.publicKeys.asSequence().first { !it.isEncryptionKey }

    /** Armored secret key ring, as an older version of the app left on disk unencrypted. */
    fun armorSecret(secretKeyRing: PGPSecretKeyRing): String {
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { secretKeyRing.encode(it) }
        return String(out.toByteArray())
    }

    fun armor(publicKeyRing: PGPPublicKeyRing): String {
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { publicKeyRing.encode(it) }
        return String(out.toByteArray())
    }

    /**
     * Decrypts with Bouncy Castle only, so a round-trip test proves the app produced real OpenPGP
     * rather than merely something the app itself can read back.
     */
    fun decrypt(armoredMessage: String, secretKeyRing: PGPSecretKeyRing, passphrase: CharArray): String {
        val factory = PGPObjectFactory(
            PGPUtil.getDecoderStream(armoredMessage.byteInputStream()), BcKeyFingerprintCalculator()
        )
        var obj = factory.nextObject()
        if (obj !is PGPEncryptedDataList) obj = factory.nextObject()
        val encList = obj as PGPEncryptedDataList

        val encData = encList.encryptedDataObjects.asSequence()
            .filterIsInstance<PGPPublicKeyEncryptedData>()
            .first { secretKeyRing.getSecretKey(it.keyIdentifier) != null }
        val privateKey = secretKeyRing.getSecretKey(encData.keyIdentifier)
            .extractPrivateKey(BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(passphrase))

        val plainFactory = PGPObjectFactory(
            encData.getDataStream(BcPublicKeyDataDecryptorFactory(privateKey)),
            BcKeyFingerprintCalculator()
        )
        var message: Any? = plainFactory.nextObject()
        while (message != null) {
            if (message is PGPLiteralData) return String(message.inputStream.readBytes())
            message = plainFactory.nextObject()
        }
        error("no literal data in the decrypted message")
    }
}
