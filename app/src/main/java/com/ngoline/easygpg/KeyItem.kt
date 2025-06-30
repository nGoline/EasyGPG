package com.ngoline.easygpg

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing

data class KeyItem(
    val alias: String,
    val fingerprint: String,
    val publicKey: PGPPublicKey,
    val publicKeyRing: PGPPublicKeyRing
)