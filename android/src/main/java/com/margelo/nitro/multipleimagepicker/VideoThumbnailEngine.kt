package com.margelo.nitro.multipleimagepicker

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import com.luck.picture.lib.interfaces.OnKeyValueResultCallbackListener
import com.luck.picture.lib.interfaces.OnVideoThumbnailEventListener
import com.luck.picture.lib.utils.PictureFileUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class VideoThumbnailEngine(private val targetPath: String) : OnVideoThumbnailEventListener {

    init {
        android.util.Log.d("VideoThumbnailEngine", "Initialized with targetPath: $targetPath")
    }

    override fun onVideoThumbnail(
        context: Context, videoPath: String, call: OnKeyValueResultCallbackListener
    ) {
        android.util.Log.d("VideoThumbnailEngine", "=== Starting thumbnail generation for: $videoPath")
        try {
            // Handle content:// URIs differently
            val cleanPath = if (videoPath.startsWith("content://")) {
                android.util.Log.d("VideoThumbnailEngine", "Processing content:// URI")
                videoPath // Keep content:// URI as is for ThumbnailUtils
            } else {
                videoPath.replace("file://", "")
            }
            android.util.Log.d("VideoThumbnailEngine", "Cleaned path: $cleanPath")

            // Use Android native ThumbnailUtils for better memory control
            // INCREASED SIZE: Use 512x512 for much better quality
            val thumbnail: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.util.Log.d("VideoThumbnailEngine", "Using Android 10+ ThumbnailUtils")
                // Android 10+ - create thumbnail with higher quality
                try {
                    val bitmap = if (cleanPath.startsWith("content://")) {
                        // For content:// URIs, use ContentResolver.loadThumbnail
                        context.contentResolver.loadThumbnail(
                            android.net.Uri.parse(cleanPath),
                            Size(512, 512), // Increased from 100x100 for much better quality
                            null
                        )
                    } else {
                        // For file paths, use ThumbnailUtils
                        ThumbnailUtils.createVideoThumbnail(
                            File(cleanPath),
                            Size(512, 512), // Increased from 100x100 for much better quality
                            null
                        )
                    }
                    android.util.Log.d("VideoThumbnailEngine", "Android 10+ thumbnail created: ${bitmap?.width}x${bitmap?.height}")
                    bitmap
                } catch (e: Exception) {
                    android.util.Log.e("VideoThumbnailEngine", "Android 10+ method failed, fallback to legacy: ${e.message}")
                    // Fallback to legacy method
                    val bitmap = ThumbnailUtils.createVideoThumbnail(
                        cleanPath,
                        android.provider.MediaStore.Video.Thumbnails.MINI_KIND // Changed from MICRO to MINI for better quality
                    )
                    bitmap?.let {
                        android.util.Log.d("VideoThumbnailEngine", "Legacy fallback thumbnail created: ${it.width}x${it.height}")
                        Bitmap.createScaledBitmap(it, 512, 512, true).also { scaled -> // Increased to 512x512
                            if (scaled != it) it.recycle()
                        }
                    }
                }
            } else {
                android.util.Log.d("VideoThumbnailEngine", "Using legacy ThumbnailUtils")
                // Pre-Android 10 - use legacy method with MINI kind for better quality
                val bitmap = ThumbnailUtils.createVideoThumbnail(
                    cleanPath,
                    android.provider.MediaStore.Video.Thumbnails.MINI_KIND // Changed from MICRO to MINI for better quality
                )
                // Scale to high quality size
                bitmap?.let {
                    android.util.Log.d("VideoThumbnailEngine", "Legacy thumbnail created: ${it.width}x${it.height}")
                    Bitmap.createScaledBitmap(it, 512, 512, true).also { scaled -> // Increased to 512x512
                        if (scaled != it) it.recycle() // Recycle original if new bitmap created
                    }
                }
            }

            if (thumbnail != null) {
                android.util.Log.d("VideoThumbnailEngine", "Thumbnail bitmap created successfully, compressing...")
                val stream = ByteArrayOutputStream()
                // Use HIGH quality compression for clear thumbnails (90% instead of 30%)
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, stream)

                var fos: FileOutputStream? = null
                var result: String? = null
                try {
                    val targetFile =
                        File(targetPath, "thumbnails_" + System.currentTimeMillis() + ".jpg")
                    android.util.Log.d("VideoThumbnailEngine", "Saving thumbnail to: ${targetFile.absolutePath}")
                    fos = FileOutputStream(targetFile)
                    fos.write(stream.toByteArray())
                    fos.flush()
                    result = targetFile.absolutePath
                    android.util.Log.d("VideoThumbnailEngine", "Thumbnail saved successfully: $result")
                } catch (e: IOException) {
                    android.util.Log.e("VideoThumbnailEngine", "Failed to save thumbnail: ${e.message}", e)
                    e.printStackTrace()
                } finally {
                    PictureFileUtils.close(fos)
                    PictureFileUtils.close(stream)
                    // Recycle bitmap to free memory immediately
                    thumbnail.recycle()
                }
                android.util.Log.d("VideoThumbnailEngine", "Calling callback with result: $result")
                call.onCallback(videoPath, result)
            } else {
                android.util.Log.w("VideoThumbnailEngine", "No thumbnail generated for: $videoPath")
                // No thumbnail generated, return empty
                call.onCallback(videoPath, "")
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoThumbnailEngine", "Error generating thumbnail for $videoPath: ${e.message}", e)
            e.printStackTrace()
            call.onCallback(videoPath, "")
        }
    }
}