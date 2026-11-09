package br.edu.uniconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    @Test fun `release assets are restricted to our HTTPS repository`() {
        assertTrue(UpdatePolicy.releaseAssetUrl("https://github.com/nicolaspiet/uniconnect/releases/download/v0.6.0/uniconnect.apk"))
        listOf(
            "http://github.com/nicolaspiet/uniconnect/releases/download/v1/app.apk",
            "https://github.com/another/repo/releases/download/v1/app.apk",
            "https://github.com.evil.example/nicolaspiet/uniconnect/releases/download/v1/app.apk",
            "https://github.com@nasty.example/nicolaspiet/uniconnect/releases/download/v1/app.apk",
            "https://github.com:8443/nicolaspiet/uniconnect/releases/download/v1/app.apk",
            "https://github.com/nicolaspiet/uniconnect/releases/download/../app.apk"
        ).forEach { assertFalse(it, UpdatePolicy.releaseAssetUrl(it)) }
    }

    @Test fun `redirects stay on GitHub HTTPS download hosts`() {
        assertTrue(UpdatePolicy.downloadHostUrl("https://release-assets.githubusercontent.com/path?signature=example"))
        assertFalse(UpdatePolicy.downloadHostUrl("https://release-assets.githubusercontent.com.evil.example/path"))
        assertFalse(UpdatePolicy.downloadHostUrl("file:///tmp/app.apk"))
        assertFalse(UpdatePolicy.downloadHostUrl("https://github.com/path#fragment"))
    }

    @Test fun `download progress remains bounded`() {
        assertEquals(null, DownloadProgress(100, -1).percent)
        assertEquals(50, DownloadProgress(50, 100).percent)
        assertEquals(100, DownloadProgress(150, 100).percent)
    }

    @Test fun `checksums keep leading zeros and validate full length`() {
        assertEquals("000fff", UpdatePolicy.hex(byteArrayOf(0, 15, -1)))
        assertTrue(UpdatePolicy.validSha256("a".repeat(64)))
        assertFalse(UpdatePolicy.validSha256("a".repeat(63)))
        assertFalse(UpdatePolicy.validSha256("g".repeat(64)))
    }
}
