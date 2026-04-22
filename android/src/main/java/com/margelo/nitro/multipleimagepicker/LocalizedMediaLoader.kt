package com.margelo.nitro.multipleimagepicker

import android.content.Context
import android.database.Cursor
import com.luck.picture.lib.config.SelectorConfig
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.entity.LocalMediaFolder
import com.luck.picture.lib.interfaces.OnQueryAlbumListener
import com.luck.picture.lib.interfaces.OnQueryAllAlbumListener
import com.luck.picture.lib.interfaces.OnQueryDataResultListener
import com.luck.picture.lib.loader.IBridgeMediaLoader
import com.luck.picture.lib.loader.LocalMediaLoader
import com.luck.picture.lib.loader.LocalMediaPageLoader
import java.util.Locale

/**
 * Localize album folder names returned by MediaStore, e.g. "Pictures"/"Screenshots".
 * This is lightweight and only transforms album list labels.
 */
class LocalizedMediaLoader(
    context: Context,
    selectorConfig: SelectorConfig,
    private val language: Language
) : IBridgeMediaLoader(context, selectorConfig) {

    private val delegate: IBridgeMediaLoader =
        if (selectorConfig.isPageStrategy) {
            LocalMediaPageLoader(context, selectorConfig)
        } else {
            LocalMediaLoader(context, selectorConfig)
        }

    override fun getAlbumFirstCover(bucketId: Long): String? {
        return delegate.getAlbumFirstCover(bucketId)
    }

    override fun loadAllAlbum(query: OnQueryAllAlbumListener<LocalMediaFolder>) {
        delegate.loadAllAlbum(OnQueryAllAlbumListener { folders ->
            folders?.forEach { folder ->
                folder.folderName = localizeFolderName(folder.folderName)
            }
            query.onComplete(folders)
        })
    }

    override fun loadPageMediaData(
        bucketId: Long,
        page: Int,
        pageSize: Int,
        query: OnQueryDataResultListener<LocalMedia>
    ) {
        delegate.loadPageMediaData(bucketId, page, pageSize, query)
    }

    override fun loadOnlyInAppDirAllMedia(query: OnQueryAlbumListener<LocalMediaFolder>) {
        delegate.loadOnlyInAppDirAllMedia(query)
    }

    override fun getSelection(): String {
        return ""
    }

    override fun getSelectionArgs(): Array<String> {
        return emptyArray()
    }

    override fun getSortOrder(): String {
        return ""
    }

    override fun parseLocalMedia(data: Cursor, isUsePool: Boolean): LocalMedia {
        return LocalMedia.create()
    }

    private fun localizeFolderName(rawName: String?): String {
        if (rawName.isNullOrBlank()) {
            return rawName ?: ""
        }
        val normalized = rawName.trim()
        val key = normalized.lowercase(Locale.ROOT)
            .replace("_", " ")
            .trim()

        return when (language) {
            Language.ZH_HANS -> mapSimplifiedChinese(key, normalized)
            Language.ZH_HANT -> mapTraditionalChinese(key, normalized)
            else -> normalized
        }
    }

    private fun mapSimplifiedChinese(key: String, fallback: String): String {
        return when (key) {
            "picture", "pictures" -> "图片"
            "screenshot", "screenshots" -> "截图"
            "camera" -> "相机"
            "camera roll" -> "相机胶卷"
            "download", "downloads" -> "下载"
            "videos", "movies" -> "视频"
            "dcim" -> "相机"
            else -> fallback
        }
    }

    private fun mapTraditionalChinese(key: String, fallback: String): String {
        return when (key) {
            "picture", "pictures" -> "圖片"
            "screenshot", "screenshots" -> "截圖"
            "camera" -> "相機"
            "camera roll" -> "相機膠卷"
            "download", "downloads" -> "下載"
            "videos", "movies" -> "影片"
            "dcim" -> "相機"
            else -> fallback
        }
    }
}
