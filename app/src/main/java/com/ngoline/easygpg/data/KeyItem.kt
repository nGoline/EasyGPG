package com.ngoline.easygpg.data

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing

data class KeyItem(
    val alias: String,
    val fingerprint: String,
    val publicKey: PGPPublicKey,
    val publicKeyRing: PGPPublicKeyRing
)

fun shortFingerprint(fingerprint: String): String = fingerprint.takeLast(8).uppercase()