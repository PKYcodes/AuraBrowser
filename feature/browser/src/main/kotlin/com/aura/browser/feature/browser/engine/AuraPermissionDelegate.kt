package com.aura.browser.feature.browser.engine

import com.aura.browser.core.database.dao.SitePermissionDao
import com.aura.browser.core.database.entity.SitePermissionEntity
import com.aura.browser.core.network.url.AuraUrlSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Handles web content permissions (location, notifications, camera, microphone, autoplay) and the
 * Android runtime permissions that back them. Decisions the user chooses to remember are stored per origin.
 */
internal class AuraPermissionDelegate(
    private val scope: CoroutineScope,
    private val queue: BrowserPromptQueue,
    private val sitePermissionDao: SitePermissionDao,
    private val urlSanitizer: AuraUrlSanitizer,
) : GeckoSession.PermissionDelegate {

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) {
        val requested = permissions?.toList().orEmpty()
        if (requested.isEmpty()) {
            callback.grant()
            return
        }
        queue.enqueue(
            BrowserPrompt.AndroidPermissions(
                id = queue.nextId(),
                permissions = requested,
                onResult = { granted -> if (granted) callback.grant() else callback.reject() },
            )
        )
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission,
    ): GeckoResult<Int>? {
        val origin = urlSanitizer.extractOrigin(perm.uri)
        val result = GeckoResult<Int>()
        val allow = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        val deny = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY

        scope.launch {
            val saved = sitePermissionDao.getPermissionForOrigin(origin)
            when (perm.permission) {
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
                GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> result.complete(allow)

                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ->
                    result.complete(if (saved?.autoplayAllowed == true || isMediaFriendlyOrigin(origin)) allow else deny)

                GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION ->
                    askOrApply(origin, SitePermissionKind.LOCATION, saved?.locationAllowed == true, result, allow, deny)

                GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION ->
                    askOrApply(origin, SitePermissionKind.NOTIFICATIONS, saved?.notificationsAllowed == true, result, allow, deny)

                else -> result.complete(deny)
            }
        }
        return result
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) {
        val origin = urlSanitizer.extractOrigin(uri)
        val wantsVideo = !video.isNullOrEmpty()
        val wantsAudio = !audio.isNullOrEmpty()
        if (!wantsVideo && !wantsAudio) {
            callback.reject()
            return
        }

        scope.launch {
            val saved = sitePermissionDao.getPermissionForOrigin(origin)
            val alreadyAllowed = (!wantsVideo || saved?.cameraAllowed == true) &&
                (!wantsAudio || saved?.microphoneAllowed == true)

            val grant = {
                callback.grant(video?.firstOrNull(), audio?.firstOrNull())
            }

            if (alreadyAllowed) {
                grant()
                return@launch
            }

            val kinds = buildList {
                if (wantsVideo) add(SitePermissionKind.CAMERA)
                if (wantsAudio) add(SitePermissionKind.MICROPHONE)
            }
            queue.enqueue(
                BrowserPrompt.SitePermission(
                    id = queue.nextId(),
                    origin = origin,
                    kinds = kinds,
                    onResult = { allowed, remember ->
                        if (allowed) {
                            if (remember) remember(origin, kinds)
                            grant()
                        } else {
                            callback.reject()
                        }
                    },
                )
            )
        }
    }

    private fun askOrApply(
        origin: String,
        kind: SitePermissionKind,
        alreadyAllowed: Boolean,
        result: GeckoResult<Int>,
        allow: Int,
        deny: Int,
    ) {
        if (alreadyAllowed) {
            result.complete(allow)
            return
        }
        queue.enqueue(
            BrowserPrompt.SitePermission(
                id = queue.nextId(),
                origin = origin,
                kinds = listOf(kind),
                onResult = { allowed, remember ->
                    if (allowed && remember) remember(origin, listOf(kind))
                    result.complete(if (allowed) allow else deny)
                },
            )
        )
    }

    private fun remember(origin: String, kinds: List<SitePermissionKind>) {
        scope.launch {
            val current = sitePermissionDao.getPermissionForOrigin(origin)
                ?: SitePermissionEntity(origin = origin, autoplayAllowed = false)
            sitePermissionDao.upsertSitePermission(
                current.copy(
                    locationAllowed = current.locationAllowed || SitePermissionKind.LOCATION in kinds,
                    notificationsAllowed = current.notificationsAllowed || SitePermissionKind.NOTIFICATIONS in kinds,
                    cameraAllowed = current.cameraAllowed || SitePermissionKind.CAMERA in kinds,
                    microphoneAllowed = current.microphoneAllowed || SitePermissionKind.MICROPHONE in kinds,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /** Video/music sites where audible autoplay after a navigation is the expected behaviour. */
    private fun isMediaFriendlyOrigin(origin: String): Boolean {
        val host = origin.substringAfter("://").substringBefore(':').lowercase()
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    }
}
