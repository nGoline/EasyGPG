package com.ngoline.easygpg

import org.bouncycastle.openpgp.PGPPublicKey

data class KeyItem(
    val alias: String,
    val fingerprint: String,
    val publicKey: PGPPublicKey
)