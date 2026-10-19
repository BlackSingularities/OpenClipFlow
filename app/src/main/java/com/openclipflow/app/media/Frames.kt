package com.openclipflow.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.openclipflow.app.model.MediaAsset
import com.openclipflow.app.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Miniatury klatek (pasek filmowy na osi czasu, biblioteka, ekran główny). Pamięć podręczna
 * bitmap i otwartych dekoderów – MediaMetadataRetriever otwiera się wolno, więc jest wielokrotnie
 * używany dla tego samego pliku.
 */
object Frames {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val retrievers = LinkedHashMap<String, MediaMetadataRetriever>(8, 0.75f, true)
    private val lock = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(2)

    fun cached(key: String): Bitmap? = cache.get(key)

    private fun key(uri: String, timeMs: Long, height: Int) = "$uri|$timeMs|$height"

    /** Klatka w chwili [timeMs] (wideo) albo samo zdjęcie, przeskalowana do wysokości [height] px. */
    suspend fun frame(context: Context, asset: MediaAsset, timeMs: Long, height: Int): Bitmap? {
        if (asset.kind == MediaKind.AUDIO) return null
        val t = if (asset.kind == MediaKind.IMAGE) 0L else timeMs
        val k = key(asset.uri, t, height)
        cache.get(k)?.let { return it }
        return withContext(io) {
            cache.get(k) ?: runCatching {
                if (asset.kind == MediaKind.IMAGE) decodeImage(context, asset.uri, height) else decodeFrame(context, asset, t, height)
            }.getOrNull()?.also { cache.put(k, it) }
        }
    }

    private suspend fun decodeFrame(context: Context, asset: MediaAsset, timeMs: Long, height: Int): Bitmap? = lock.withLock {
        val r = retrievers.getOrPut(asset.uri) {
            MediaMetadataRetriever().apply { setDataSource(context, Uri.parse(asset.uri)) }
        }
        while (retrievers.size > 4) {
            val eldest = retrievers.entries.first()
            runCatching { eldest.value.release() }
            retrievers.remove(eldest.key)
        }
        val w = if (asset.displayHeight > 0) (height * asset.displayWidth / asset.displayHeight).coerceAtLeast(1) else height * 16 / 9
        r.getScaledFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, height)
            ?: r.getFrameAtTime(timeMs * 1000)?.let { Bitmap.createScaledBitmap(it, w, height, true) }
    }

    private fun decodeImage(context: Context, uri: String, height: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= height) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val w = (height * bmp.width / bmp.height).coerceAtLeast(1)
        return if (bmp.height == height) bmp else Bitmap.createScaledBitmap(bmp, w, height, true)
    }

    /** Zapis klatki źródłowej w pełnej rozdzielczości (eksport kadru do galerii). */
    fun fullFrame(context: Context, asset: MediaAsset, timeMs: Long): Bitmap? = runCatching {
        if (asset.kind == MediaKind.IMAGE) {
            context.contentResolver.openInputStream(Uri.parse(asset.uri))?.use { BitmapFactory.decodeStream(it) }
        } else {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(context, Uri.parse(asset.uri))
                    getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                } finally {
                    release()
                }
            }
        }
    }.getOrNull()

    fun clear() {
        cache.evictAll()
    }
}

/** Miniatura w Compose – ładowana w tle, od razu z pamięci podręcznej, jeśli już była. */
@Composable
fun rememberFrame(asset: MediaAsset?, timeMs: Long, height: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    val t = if (asset?.kind == MediaKind.IMAGE) 0L else timeMs
    val initial = asset?.let { Frames.cached("${it.uri}|$t|$height")?.asImageBitmap() }
    return produceState(initial, asset?.uri, t, height) {
        if (asset != null && value == null) value = Frames.frame(context, asset, t, height)?.asImageBitmap()
    }
}
