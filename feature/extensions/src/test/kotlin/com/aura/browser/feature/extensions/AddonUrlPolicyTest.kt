package com.aura.browser.feature.extensions

import com.aura.browser.feature.extensions.manager.isTrustedAddonUrl
import com.aura.browser.feature.extensions.manager.resolveAddonInstallUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonUrlPolicyTest {

    @Test
    fun `accepts https links on addons dot mozilla dot org`() {
        assertTrue(isTrustedAddonUrl("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"))
    }

    @Test
    fun `rejects other hosts even when the path ends in xpi`() {
        assertFalse(isTrustedAddonUrl("https://evil.example/ublock.xpi"))
        assertFalse(isTrustedAddonUrl("http://evil.example/ublock.xpi"))
    }

    @Test
    fun `rejects cleartext http and host spoofing tricks`() {
        assertFalse(isTrustedAddonUrl("http://addons.mozilla.org/x.xpi"))
        assertFalse(isTrustedAddonUrl("https://addons.mozilla.org@evil.example/x.xpi"))
        assertFalse(isTrustedAddonUrl("https://addons.mozilla.org.evil.example/x.xpi"))
        assertFalse(isTrustedAddonUrl("not a url"))
    }

    private val ublock = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"

    @Test
    fun `resolves normal firefox add-on page links`() {
        assertEquals(ublock, resolveAddonInstallUrl("https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/"))
        assertEquals(ublock, resolveAddonInstallUrl("https://addons.mozilla.org/hi/firefox/addon/ublock-origin/?utm_source=x"))
        assertEquals(ublock, resolveAddonInstallUrl("  https://addons.mozilla.org/en-US/android/addon/ublock-origin  "))
        assertEquals(ublock, resolveAddonInstallUrl("addons.mozilla.org/addon/ublock-origin/"))
    }

    @Test
    fun `resolves bare slug and keeps direct xpi links`() {
        assertEquals(ublock, resolveAddonInstallUrl("ublock-origin"))
        val direct = "https://addons.mozilla.org/firefox/downloads/file/4000000/ublock_origin-1.0.xpi"
        assertEquals(direct, resolveAddonInstallUrl(direct))
    }

    @Test
    fun `rejects foreign hosts and non add-on pages`() {
        assertNull(resolveAddonInstallUrl("https://evil.example/en-US/firefox/addon/ublock-origin/"))
        assertNull(resolveAddonInstallUrl("https://addons.mozilla.org/en-US/firefox/"))
        assertNull(resolveAddonInstallUrl("http://addons.mozilla.org/en-US/firefox/addon/x/"))
        assertNull(resolveAddonInstallUrl(""))
    }
}
