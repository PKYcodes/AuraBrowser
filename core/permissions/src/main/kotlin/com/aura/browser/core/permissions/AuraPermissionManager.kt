package com.aura.browser.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AuraPermissionGroup {
    AUDIO_MEDIA,
    VIDEO_MEDIA,
    NOTIFICATIONS,
    CAMERA,
    MICROPHONE,
    LOCATION,
}

@Singleton
class AuraPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun requiredAndroidPermissions(group: AuraPermissionGroup): List<String> = when (group) {
        AuraPermissionGroup.AUDIO_MEDIA -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        AuraPermissionGroup.VIDEO_MEDIA -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                // Android 14+: the user may grant access to selected items only.
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_VIDEO)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        AuraPermissionGroup.NOTIFICATIONS -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
        }
        AuraPermissionGroup.CAMERA -> listOf(Manifest.permission.CAMERA)
        AuraPermissionGroup.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
        AuraPermissionGroup.LOCATION -> listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    /** A group counts as granted when at least one of its permissions is granted (covers partial media access). */
    fun isPermissionGroupGranted(group: AuraPermissionGroup): Boolean {
        val perms = requiredAndroidPermissions(group)
        if (perms.isEmpty()) return true
        return perms.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Android permission strings that still need to be requested for the given groups. */
    fun permissionsToRequest(vararg groups: AuraPermissionGroup): Array<String> =
        groups
            .filterNot { isPermissionGroupGranted(it) }
            .flatMap { requiredAndroidPermissions(it) }
            .distinct()
            .toTypedArray()

    fun isGranted(androidPermission: String): Boolean =
        ContextCompat.checkSelfPermission(context, androidPermission) == PackageManager.PERMISSION_GRANTED
}
