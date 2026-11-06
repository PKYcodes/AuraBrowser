package com.aura.browser.core.common.model

enum class SearchEngine(
    val displayName: String,
    val queryTemplate: String,
    val homeUrl: String,
) {
    DUCKDUCKGO(
        displayName = "DuckDuckGo",
        queryTemplate = "https://duckduckgo.com/?q=%s",
        homeUrl = "https://duckduckgo.com",
    ),
    GOOGLE(
        displayName = "Google",
        queryTemplate = "https://www.google.com/search?q=%s",
        homeUrl = "https://www.google.com",
    ),
    BRAVE(
        displayName = "Brave Search",
        queryTemplate = "https://search.brave.com/search?q=%s",
        homeUrl = "https://search.brave.com",
    ),
    STARTPAGE(
        displayName = "Startpage",
        queryTemplate = "https://www.startpage.com/do/dsearch?query=%s",
        homeUrl = "https://www.startpage.com",
    ),
}

enum class TrackingProtectionPreset(val title: String, val description: String) {
    STANDARD(
        title = "Standard",
        description = "Blocks known social, analytics, and cryptomining trackers while preserving site compatibility.",
    ),
    STRICT(
        title = "Strict",
        description = "Blocks all known trackers, fingerprinting scripts, and third-party tracking cookies.",
    ),
    CUSTOM(
        title = "Custom",
        description = "Fine-grained control over anti-tracking, cookie policies, and HTTPS enforcement.",
    ),
}

enum class CookiePolicyMode(val label: String) {
    ACCEPT_ALL("Accept all cookies"),
    BLOCK_CROSS_SITE("Block cross-site tracking cookies"),
    BLOCK_THIRD_PARTY("Block all third-party cookies"),
    BLOCK_ALL("Block all cookies (may break sign-in)"),
}
