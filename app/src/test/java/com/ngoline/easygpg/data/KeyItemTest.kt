package com.ngoline.easygpg.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyItemTest {

    @Test
    fun `a short fingerprint is the last eight characters, upper case`() {
        assertEquals("9AB4CDEF", shortFingerprint("1a2b3c4d5e6f70819ab4cdef"))
    }

    @Test
    fun `a fingerprint shorter than eight characters is kept whole`() {
        assertEquals("ABCD", shortFingerprint("abcd"))
        assertEquals("", shortFingerprint(""))
    }
}
