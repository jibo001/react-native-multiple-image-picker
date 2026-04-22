package com.margelo.nitro.multipleimagepicker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.luck.picture.lib.basic.IPictureSelectorEvent
import com.luck.picture.lib.interfaces.OnCameraInterceptListener
import com.luck.picture.lib.permissions.PermissionChecker
import com.luck.picture.lib.permissions.PermissionConfig
import com.luck.picture.lib.permissions.PermissionResultCallback

class LimitedAccessPhotoPermissionEngine(
    private val context: Context,
    private val chooseMode: Int
) : OnCameraInterceptListener {

    override fun openCamera(fragment: Fragment, cameraMode: Int, requestCode: Int) {
        val permissionArray = PermissionConfig.getReadPermissionArray(context, chooseMode)
        PermissionChecker.getInstance().requestPermissions(
            fragment,
            permissionArray,
            object : PermissionResultCallback {
                override fun onGranted() {
                    refreshMediaGrid(fragment)
                }

                override fun onDenied() {
                    Toast.makeText(
                        context,
                        context.getString(com.luck.picture.lib.R.string.ps_jurisdiction),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun refreshMediaGrid(fragment: Fragment) {
        val event = fragment as? IPictureSelectorEvent ?: return
        event.loadAllAlbumData()

        // Keep it lightweight: one follow-up page load to stabilize visible count after system picker returns.
        Handler(Looper.getMainLooper()).postDelayed(
            { event.loadMoreMediaData() },
            220L
        )
    }
}
