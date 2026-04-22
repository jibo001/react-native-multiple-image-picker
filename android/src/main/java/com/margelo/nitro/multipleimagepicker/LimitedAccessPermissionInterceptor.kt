package com.margelo.nitro.multipleimagepicker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.luck.picture.lib.interfaces.OnPermissionsInterceptListener
import com.luck.picture.lib.interfaces.OnRequestPermissionListener
import com.luck.picture.lib.permissions.PermissionChecker
import com.luck.picture.lib.permissions.PermissionConfig

class LimitedAccessPermissionInterceptor(
    private val context: Context
) : OnPermissionsInterceptListener {

    override fun requestPermission(
        fragment: Fragment,
        permissionArray: Array<String>,
        call: OnRequestPermissionListener
    ) {
        if (isCameraPermissionRequest(permissionArray)) {
            call.onCall(permissionArray, true)
            return
        }

        // Do not prompt on first enter; defer permission request to explicit "add/access" action.
        call.onCall(permissionArray, true)
    }

    override fun hasPermissions(fragment: Fragment, permissionArray: Array<String>): Boolean {
        if (isCameraPermissionRequest(permissionArray)) {
            return true
        }
        if (PermissionChecker.isCheckSelfPermission(context, permissionArray)) {
            return true
        }
        return hasLimitedPhotoAccessGrant(permissionArray)
    }

    private fun hasLimitedPhotoAccessGrant(permissions: Array<String>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false
        }
        if (context.applicationInfo.targetSdkVersion < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false
        }
        val hasVisualSelected = ContextCompat.checkSelfPermission(
            context,
            PermissionConfig.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasVisualSelected) {
            return false
        }

        return permissions.any {
            it == PermissionConfig.READ_MEDIA_IMAGES || it == PermissionConfig.READ_MEDIA_VIDEO
        }
    }

    private fun isCameraPermissionRequest(permissions: Array<String>): Boolean {
        return permissions.size == 1 && permissions[0] == Manifest.permission.CAMERA
    }
}
