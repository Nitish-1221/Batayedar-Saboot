package org.bataydar.evidence

import org.bataydar.evidence.export.ChecksumUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecksumUtilsTest {

    @Test
    fun testSha256OfKnownString() {
        val input = "Batayedar Saboot Crop Evidence".toByteArray(Charsets.UTF_8)
        val hash = ChecksumUtils.calculateSha256(input)
        
        // SHA-256 is 64 hex characters long
        assertEquals(64, hash.length)
        // Check idempotency
        val hash2 = ChecksumUtils.calculateSha256(input)
        assertEquals(hash, hash2)
    }

    @Test
    fun testSha256OfEmptyArray() {
        val empty = ByteArray(0)
        val hash = ChecksumUtils.calculateSha256(empty)
        // Standard SHA-256 of empty string: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
    }
}
