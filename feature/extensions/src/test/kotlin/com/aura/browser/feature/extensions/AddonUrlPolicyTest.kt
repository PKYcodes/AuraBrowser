package com.aura.browser.feature.extensions

import com.aura.browser.feature.extensions.manager.isTrustedAddonUrl
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
}
