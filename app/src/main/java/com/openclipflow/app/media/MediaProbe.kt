package com.openclipflow.app.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.openclipflow.app.model.MediaAsset
import com.openclipflow.app.model.MediaKind

/** Odczyt metadanych importowanego pliku: rodzaj, długość, wymiary, obrót, ścieżka dźwięku. */
object MediaProbe {
    private val audioExt = setOf("mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "amr", "3ga")
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")

    fun probe(context: Context, uri: Uri): MediaAsset? {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "plik"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val mime = resolver.getType(uri).orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        val kind = when {
            mime.startsWith("image/") || ext in imageExt -> MediaKind.IMAGE
            mime.startsWith("audio/") || ext in audioExt -> MediaKind.AUDIO
            else -> MediaKind.VIDEO
        }
        return when (kind) {
            MediaKind.IMAGE -> probeImage(context, uri, name, size)
            else -> probeAv(context, uri, name, size, kind)
        }
    }

    private fun probeImage(context: Context, uri: Uri, name: String, size: Long): MediaAsset? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }
        if (opts.outWidth <= 0) return null
        return MediaAsset(
            uri = uri.toString(),
            name = name,
            kind = MediaKind.IMAGE,
            durationMs = 0,
            width = opts.outWidth,
            height = opts.outHeight,
            hasAudio = false,
            sizeBytes = size,
        )
    }

    private fun probeAv(context: Context, uri: Uri, name: String, size: Long, guess: MediaKind): MediaAsset? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val hasVideo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val kind = if (hasVideo) MediaKind.VIDEO else if (hasAudio) MediaKind.AUDIO else guess
            if (duration <= 0) return null
            MediaAsset(
                uri = uri.toString(),
                name = name,
                kind = kind,
                durationMs = duration,
                width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                hasAudio = hasAudio,
                sizeBytes = size,
                frameRate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f,
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Czy plik źródłowy jest wciąż dostępny (np. po odebraniu uprawnień przez dostawcę). */
    fun isAvailable(context: Context, uri: String): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    }.getOrDefault(false)
}
