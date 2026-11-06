package com.aura.browser.navigation

enum class AuraDestination(
    val route: String,
    val label: String,
    val isBottomBarTab: Boolean = false,
) {
    HOME("home", "Home", isBottomBarTab = true),
    BROWSER("browser", "Browser", isBottomBarTab = true),
    YOUTUBE("youtube", "YouTube", isBottomBarTab = true),
    MUSIC("music", "Music", isBottomBarTab = true),
    VIDEO("video", "Video", isBottomBarTab = true),
    TABS("tabs", "Tabs"),
    BOOKMARKS("bookmarks", "Bookmarks"),
    HISTORY("history", "History"),
    DOWNLOADS("downloads", "Downloads"),
    EXTENSIONS("extensions", "Extensions"),
    FILE_MANAGER("filemanager", "Files"),
    PRIVACY("privacy", "Privacy"),
    SETTINGS("settings", "Settings"),
}
