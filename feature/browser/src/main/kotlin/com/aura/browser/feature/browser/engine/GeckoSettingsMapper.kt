package com.aura.browser.feature.browser.engine

import com.aura.browser.core.common.model.CookiePolicyMode
import com.aura.browser.core.common.model.TrackingProtectionPreset
import org.mozilla.geckoview.ContentBlocking

internal object GeckoSettingsMapper {

    fun antiTracking(preset: TrackingProtectionPreset): Int = when (preset) {
        TrackingProtectionPreset.STANDARD -> ContentBlocking.AntiTracking.DEFAULT
        TrackingProtectionPreset.STRICT -> ContentBlocking.AntiTracking.STRICT or ContentBlocking.AntiTracking.STP
        TrackingProtectionPreset.CUSTOM -> ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP
    }

    fun cookieBehavior(mode: CookiePolicyMode): Int = when (mode) {
        CookiePolicyMode.ACCEPT_ALL -> ContentBlocking.CookieBehavior.ACCEPT_ALL
        CookiePolicyMode.BLOCK_CROSS_SITE -> ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
        CookiePolicyMode.BLOCK_THIRD_PARTY -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
        CookiePolicyMode.BLOCK_ALL -> ContentBlocking.CookieBehavior.ACCEPT_NONE
    }
}
