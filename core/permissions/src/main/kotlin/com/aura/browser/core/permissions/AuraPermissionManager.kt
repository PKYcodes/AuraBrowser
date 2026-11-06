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
        AuraPermissionGroup.VIDEO_MEDIA -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
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

    fun isPermissionGroupGranted(group: AuraPermissionGroup): Boolean {
        val perms = requiredAndroidPermissions(group)
        if (perms.isEmpty()) return true
        return perms.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
