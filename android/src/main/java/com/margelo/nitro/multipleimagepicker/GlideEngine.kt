package com.margelo.nitro.multipleimagepicker

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.luck.picture.lib.engine.ImageEngine
import com.luck.picture.lib.utils.ActivityCompatHelper
import java.io.File

class GlideEngine private constructor() : ImageEngine {

    // Memory-efficient options: RGB_565 uses 50% less memory than ARGB_8888
    private val memoryEfficientOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .downsample(DownsampleStrategy.AT_MOST)

    // Cache for generated video thumbnails to avoid regeneration
    private val thumbnailCache = mutableMapOf<String, Bitmap>()
    private val maxCacheSize = 50 // Increased cache size to prevent clearing during use

    // COMPLETELY DISABLE ImageView tracking - use pure cache strategy
    private val generatingUrls = mutableSetOf<String>() // Track URLs being generated

    /**
     * Clear thumbnail cache to prevent memory leaks
     */
    private fun clearThumbnailCache() {
        // Don't manually recycle bitmaps - let GC handle it to avoid "trying to use a recycled bitmap" errors
        thumbnailCache.clear()
        generatingUrls.clear() // Clear generating URLs tracking
        android.util.Log.d("GlideEngine", "Thumbnail cache and generating URLs cleared")
    }

    /**
     * Public method to clear cache - called when entering picker
     */
    fun clearCacheForNewSession() {
        clearThumbnailCache()
        android.util.Log.d("GlideEngine", "Cache cleared for new picker session")
    }

    /**
     * Manage cache size to prevent memory issues
     */
    private fun manageCacheSize() {
        if (thumbnailCache.size > maxCacheSize) {
            android.util.Log.d("GlideEngine", "Cache size exceeded (${thumbnailCache.size}), clearing oldest entries")
            // Remove oldest entries (simple FIFO approach) without manual recycling
            val iterator = thumbnailCache.iterator()
            var removed = 0
            while (iterator.hasNext() && removed < 10) { // Remove more entries at once
                iterator.next()
                iterator.remove()
                removed++
            }
            android.util.Log.d("GlideEngine", "Removed $removed old cache entries, new size: ${thumbnailCache.size}")
        }
    }

    /**
     * Get system-cached video thumbnail URI from MediaStore
     */
    private fun getVideoThumbnailUri(context: Context, videoPath: String): Uri? {
        try {
            val cleanPath = videoPath.replace("file://", "")
            android.util.Log.d("GlideEngine", "Looking for thumbnail for video: $cleanPath")

            // Query MediaStore to find the video ID
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            val selectionArgs = arrayOf(cleanPath)

            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val videoId = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    android.util.Log.d("GlideEngine", "Found video ID: $videoId")

                    // Return thumbnail URI for Android 10+
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use loadThumbnail for Android 10+
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            videoId
                        )
                        android.util.Log.d("GlideEngine", "Android 10+ thumbnail URI: $uri")
                        uri
                    } else {
                        // For older versions, query thumbnail table
                        val thumbProjection = arrayOf(MediaStore.Video.Thumbnails.DATA)
                        val thumbSelection = "${MediaStore.Video.Thumbnails.VIDEO_ID} = ?"
                        val thumbSelectionArgs = arrayOf(videoId.toString())

                        val thumbCursor = context.contentResolver.query(
                            MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                            thumbProjection,
                            thumbSelection,
                            thumbSelectionArgs,
                            null
                        )

                        thumbCursor?.use { tc ->
                            if (tc.moveToFirst()) {
                                val thumbPath = tc.getString(tc.getColumnIndexOrThrow(MediaStore.Video.Thumbnails.DATA))
                                android.util.Log.d("GlideEngine", "Found legacy thumbnail: $thumbPath")
                                Uri.parse("file://$thumbPath")
                            } else {
                                android.util.Log.d("GlideEngine", "No legacy thumbnail found for video ID: $videoId")
                                null
                            }
                        }
                    }
                } else {
                    android.util.Log.d("GlideEngine", "Video not found in MediaStore: $cleanPath")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GlideEngine", "Error getting video thumbnail: ${e.message}", e)
            e.printStackTrace()
        }
        return null
    }

    /**
     * Check if URL is an original video file
     */
    private fun isOriginalVideoFile(url: String): Boolean {
        try {
            // Generated thumbnails are safe to load - these are JPEG files created by VideoThumbnailEngine
            if (url.contains("/Thumbnail/") || url.contains("/thumbnails_") || url.endsWith(".jpg") || url.endsWith(".jpeg")) {
                android.util.Log.d("GlideEngine", "Detected generated thumbnail, safe to load: $url")
                return false
            }

            // Check for content:// URIs with video in path
            if (url.startsWith("content://") && url.contains("/video/")) {
                android.util.Log.d("GlideEngine", "Detected content:// video URI: $url")
                return true
            }

            // Check file extension
            val cleanPath = url.replace("file://", "")
            val extension = cleanPath.substringAfterLast('.', "").lowercase()

            val isVideo = extension in setOf("mp4", "avi", "mov", "mkv", "3gp", "webm", "m4v", "flv", "wmv", "mpeg", "mpg")
            if (isVideo) {
                android.util.Log.d("GlideEngine", "Detected video file by extension: $url")
            }
            return isVideo
        } catch (e: Exception) {
            return false
        }
    }

    override fun loadImage(context: Context, url: String, imageView: ImageView) {
        if (!ActivityCompatHelper.assertValidRequest(context)) {
            return
        }

        // Check if this ImageView is already loading/showing this URL to prevent unnecessary reloading
        val currentTag = imageView.tag as? String
        if (currentTag == url) {
            android.util.Log.d("GlideEngine", "ImageView already showing URL: $url, skipping reload")
            return
        }

        // Handle video files with thumbnail generation (for preview mode)
        if (isOriginalVideoFile(url)) {
            android.util.Log.d("GlideEngine", "Loading video thumbnail in preview mode for: $url")

            // Check cache first
            val cachedThumbnail = thumbnailCache[url]
            if (cachedThumbnail != null) {
                android.util.Log.d("GlideEngine", "Using cached thumbnail in preview for: $url")
                imageView.tag = url
                imageView.setImageBitmap(cachedThumbnail)
                return
            }

            // Set placeholder only if we don't have cache
            imageView.setImageResource(com.luck.picture.lib.R.drawable.ps_image_placeholder)
            imageView.tag = url

            // Generate thumbnail in background thread
            Thread {
                try {
                    val cleanPath = if (url.startsWith("content://")) {
                        url // Keep content:// URI as is
                    } else {
                        url.replace("file://", "")
                    }

                    val thumbnail: android.graphics.Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        android.util.Log.d("GlideEngine", "Generating preview thumbnail with Android 10+")
                        try {
                            if (cleanPath.startsWith("content://")) {
                                context.contentResolver.loadThumbnail(
                                    android.net.Uri.parse(cleanPath),
                                    Size(1024, 1024), // Increased for very high quality
                                    null
                                )
                            } else {
                                ThumbnailUtils.createVideoThumbnail(
                                    File(cleanPath),
                                    Size(1024, 1024), // Increased for very high quality
                                    null
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GlideEngine", "Preview Android 10+ failed, fallback: ${e.message}")
                            ThumbnailUtils.createVideoThumbnail(
                                cleanPath,
                                android.provider.MediaStore.Video.Thumbnails.MINI_KIND // Use MINI for better quality than MICRO
                            )?.let { bitmap ->
                                android.graphics.Bitmap.createScaledBitmap(bitmap, 1024, 1024, true) // Increased to 1024x1024
                            }
                        }
                    } else {
                        android.util.Log.d("GlideEngine", "Generating preview thumbnail with legacy ThumbnailUtils")
                        ThumbnailUtils.createVideoThumbnail(
                            cleanPath,
                            android.provider.MediaStore.Video.Thumbnails.MINI_KIND
                        )?.let { bitmap ->
                            android.graphics.Bitmap.createScaledBitmap(bitmap, 1024, 1024, true).also {
                                if (it != bitmap) bitmap.recycle()
                            }
                        }
                    }

                    // Set thumbnail on UI thread
                    (context as? android.app.Activity)?.runOnUiThread {
                        if (thumbnail != null) {
                            android.util.Log.d("GlideEngine", "Setting preview thumbnail: ${thumbnail.width}x${thumbnail.height}")
                            // Cache the thumbnail for future use
                            thumbnailCache[url] = thumbnail
                            manageCacheSize()

                            // Only set if this ImageView is still for the same URL
                            if (imageView.tag == url) {
                                imageView.setImageBitmap(thumbnail)
                            }
                        } else {
                            android.util.Log.w("GlideEngine", "Failed to generate preview thumbnail, keeping placeholder")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GlideEngine", "Error generating preview thumbnail: ${e.message}", e)
                }
            }.start()
            return
        }

        android.util.Log.d("GlideEngine", "Loading with Glide: $url")
        imageView.tag = url
        Glide.with(context)
            .load(url)
            .apply(memoryEfficientOptions)
            .placeholder(com.luck.picture.lib.R.drawable.ps_image_placeholder) // Add placeholder for images
            .into(imageView)
    }

    override fun loadImage(
        context: Context,
        imageView: ImageView,
        url: String,
        maxWidth: Int,
        maxHeight: Int
    ) {
        if (!ActivityCompatHelper.assertValidRequest(context)) {
            return
        }

        // Check if this ImageView is already loading/showing this URL to prevent unnecessary reloading
        val currentTag = imageView.tag as? String
        if (currentTag == url) {
            android.util.Log.d("GlideEngine", "ImageView already showing URL: $url, skipping reload")
            return
        }

        // Handle video files with thumbnail generation (for preview mode)
        if (isOriginalVideoFile(url)) {
            android.util.Log.d("GlideEngine", "Loading video thumbnail in preview mode (with size) for: $url")

            // Check cache first
            val cachedThumbnail = thumbnailCache[url]
            if (cachedThumbnail != null) {
                android.util.Log.d("GlideEngine", "Using cached thumbnail in preview (with size) for: $url")
                imageView.tag = url
                imageView.setImageBitmap(cachedThumbnail)
                return
            }

            // Set placeholder only if we don't have cache
            imageView.setImageResource(com.luck.picture.lib.R.drawable.ps_image_placeholder)
            imageView.tag = url

            // Generate thumbnail in background thread
            Thread {
                try {
                    val cleanPath = if (url.startsWith("content://")) {
                        url // Keep content:// URI as is
                    } else {
                        url.replace("file://", "")
                    }

                    val thumbnailSize = minOf(maxWidth, maxHeight, 1024) // Use provided size but cap at 1024 for very high quality
                    val thumbnail: android.graphics.Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        android.util.Log.d("GlideEngine", "Generating sized preview thumbnail with Android 10+")
                        try {
                            if (cleanPath.startsWith("content://")) {
                                context.contentResolver.loadThumbnail(
                                    android.net.Uri.parse(cleanPath),
                                    Size(thumbnailSize, thumbnailSize),
                                    null
                                )
                            } else {
                                ThumbnailUtils.createVideoThumbnail(
                                    File(cleanPath),
                                    Size(thumbnailSize, thumbnailSize),
                                    null
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GlideEngine", "Sized preview Android 10+ failed, fallback: ${e.message}")
                            ThumbnailUtils.createVideoThumbnail(
                                cleanPath,
                                android.provider.MediaStore.Video.Thumbnails.MINI_KIND
                            )?.let { bitmap ->
                                android.graphics.Bitmap.createScaledBitmap(bitmap, thumbnailSize, thumbnailSize, true).also {
                                    if (it != bitmap) bitmap.recycle()
                                }
                            }
                        }
                    } else {
                        android.util.Log.d("GlideEngine", "Generating sized preview thumbnail with legacy ThumbnailUtils")
                        ThumbnailUtils.createVideoThumbnail(
                            cleanPath,
                            android.provider.MediaStore.Video.Thumbnails.MINI_KIND
                        )?.let { bitmap ->
                            android.graphics.Bitmap.createScaledBitmap(bitmap, thumbnailSize, thumbnailSize, true).also {
                                if (it != bitmap) bitmap.recycle()
                            }
                        }
                    }

                    // Set thumbnail on UI thread
                    (context as? android.app.Activity)?.runOnUiThread {
                        if (thumbnail != null) {
                            android.util.Log.d("GlideEngine", "Setting sized preview thumbnail: ${thumbnail.width}x${thumbnail.height}")
                            // Cache the thumbnail for future use
                            thumbnailCache[url] = thumbnail
                            manageCacheSize()

                            // Only set if this ImageView is still for the same URL
                            if (imageView.tag == url) {
                                imageView.setImageBitmap(thumbnail)
                            }
                        } else {
                            android.util.Log.w("GlideEngine", "Failed to generate sized preview thumbnail, keeping placeholder")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GlideEngine", "Error generating sized preview thumbnail: ${e.message}", e)
                }
            }.start()
            return
        }

        android.util.Log.d("GlideEngine", "Loading with Glide (${maxWidth}x${maxHeight}): $url")
        imageView.tag = url
        Glide.with(context)
            .load(url)
            .apply(memoryEfficientOptions)
            .placeholder(com.luck.picture.lib.R.drawable.ps_image_placeholder) // Add placeholder for images
            .override(maxWidth, maxHeight)
            .into(imageView)
    }

    override fun loadAlbumCover(context: Context, url: String, imageView: ImageView) {
        if (!ActivityCompatHelper.assertValidRequest(context)) {
            return
        }

        // Check if this ImageView is already loading/showing this URL to prevent unnecessary reloading
        val currentTag = imageView.tag as? String
        if (currentTag == url) {
            android.util.Log.d("GlideEngine", "ImageView already showing URL: $url, skipping reload")
            return
        }

        // Block original video files to prevent OOM
        if (isOriginalVideoFile(url)) {
            android.util.Log.d("GlideEngine", "Blocking original video file to prevent OOM: $url")
            imageView.tag = url
            imageView.setImageResource(com.luck.picture.lib.R.drawable.ps_image_placeholder)
            return
        }

        android.util.Log.d("GlideEngine", "Loading album cover with Glide: $url")
        imageView.tag = url
        Glide.with(context)
            .asBitmap()
            .load(url)
            .apply(memoryEfficientOptions)
            .placeholder(com.luck.picture.lib.R.drawable.ps_image_placeholder) // Add placeholder for album covers
            .override(1024, 1024) // Increased for very high quality album cover
            .sizeMultiplier(0.5f)
            .transform(CenterCrop(), RoundedCorners(8))
            .into(imageView)
    }

    override fun loadGridImage(context: Context, url: String, imageView: ImageView) {
        if (!ActivityCompatHelper.assertValidRequest(context)) {
            return
        }

        // CACHE FIRST: If we have a cached thumbnail, use it immediately
        val cachedThumbnail = thumbnailCache[url]
        if (cachedThumbnail != null) {
            android.util.Log.d("GlideEngine", "Using cached thumbnail: $url")
            imageView.setImageBitmap(cachedThumbnail)
            return
        }

        // If it's a video, generate thumbnail directly
        if (isOriginalVideoFile(url)) {
            android.util.Log.d("GlideEngine", "Loading video thumbnail for: $url")

            // Check if we're already generating this thumbnail
            if (generatingUrls.contains(url)) {
                android.util.Log.d("GlideEngine", "Thumbnail already generating for: $url, showing placeholder")
                imageView.setImageResource(com.luck.picture.lib.R.drawable.ps_image_placeholder)
                return
            }

            // Mark as generating
            generatingUrls.add(url)

            // Always set placeholder first
            imageView.setImageResource(com.luck.picture.lib.R.drawable.ps_image_placeholder)

            // Generate thumbnail in background thread
            Thread {
                try {
                    val cleanPath = if (url.startsWith("content://")) {
                        url // Keep content:// URI as is
                    } else {
                        url.replace("file://", "")
                    }

                    val thumbnail: android.graphics.Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        android.util.Log.d("GlideEngine", "Generating thumbnail with Android 10+ loadThumbnail")
                        try {
                            if (cleanPath.startsWith("content://")) {
                                context.contentResolver.loadThumbnail(
                                    android.net.Uri.parse(cleanPath),
                                    Size(1024, 1024), // Increased for very high quality
                                    null
                                )
                            } else {
                                ThumbnailUtils.createVideoThumbnail(
                                    File(cleanPath),
                                    Size(1024, 1024), // Increased for very high quality
                                    null
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GlideEngine", "Android 10+ failed, fallback: ${e.message}")
                            ThumbnailUtils.createVideoThumbnail(
                                cleanPath,
                                android.provider.MediaStore.Video.Thumbnails.MINI_KIND // Use MINI for better quality than MICRO
                            )?.let { bitmap ->
                                android.graphics.Bitmap.createScaledBitmap(bitmap, 1024, 1024, true) // Increased to 1024x1024
                            }
                        }
                    } else {
                        android.util.Log.d("GlideEngine", "Generating thumbnail with legacy ThumbnailUtils")
                        ThumbnailUtils.createVideoThumbnail(
                            cleanPath,
                            android.provider.MediaStore.Video.Thumbnails.MINI_KIND
                        )?.let { bitmap ->
                            android.graphics.Bitmap.createScaledBitmap(bitmap, 1024, 1024, true).also {
                                if (it != bitmap) bitmap.recycle()
                            }
                        }
                    }

                    // Set thumbnail on UI thread
                    (context as? android.app.Activity)?.runOnUiThread {
                        // Always remove from generating set
                        generatingUrls.remove(url)

                        if (thumbnail != null) {
                            android.util.Log.d("GlideEngine", "Caching and setting thumbnail: ${thumbnail.width}x${thumbnail.height} - $url")
                            // Cache the thumbnail FIRST
                            thumbnailCache[url] = thumbnail
                            manageCacheSize()

                            // SIMPLIFIED: Always try to set the thumbnail
                            // RecyclerView reuse is handled by caching
                            try {
                                imageView.setImageBitmap(thumbnail)
                                android.util.Log.d("GlideEngine", "Successfully set generated thumbnail: $url")
                            } catch (e: Exception) {
                                android.util.Log.e("GlideEngine", "Failed to set thumbnail to ImageView: ${e.message}")
                            }
                        } else {
                            android.util.Log.w("GlideEngine", "Failed to generate thumbnail: $url")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GlideEngine", "Error generating thumbnail: ${e.message}", e)
                    (context as? android.app.Activity)?.runOnUiThread {
                        // Always remove from generating set, even on error
                        generatingUrls.remove(url)
                    }
                }
            }.start()
            return
        }

        // Load images with Glide
        android.util.Log.d("GlideEngine", "Loading image with Glide: $url")
        Glide.with(context)
            .load(url)
            .apply(memoryEfficientOptions)
            .placeholder(com.luck.picture.lib.R.drawable.ps_image_placeholder) // Add placeholder for images too
            .override(1024, 1024)
            .centerCrop()
            .into(imageView)
    }

    override fun pauseRequests(context: Context) {
        if (!ActivityCompatHelper.assertValidRequest(context)) {
            return
        }
        Glide.with(context).pauseRequests()
        // Don't clear cache on pause to avoid bitmap recycling issues
    }

    override fun resumeRequests(context: Context) {
        if (!ActivityCompatHelper.assertValidRequest(context)) {
            return
        }
        Glide.with(context).resumeRequests()
    }

    private object InstanceHolder {
        val instance = GlideEngine()
    }

    companion object {
        fun createGlideEngine(): GlideEngine {
            return InstanceHolder.instance
        }
    }
}