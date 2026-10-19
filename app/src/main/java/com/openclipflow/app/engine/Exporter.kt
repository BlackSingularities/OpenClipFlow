@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.openclipflow.app.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.openclipflow.app.model.ExportFormat
import com.openclipflow.app.model.ExportSettings
import com.openclipflow.app.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Wynik eksportu: plik tymczasowy (do udostępnienia) i wpis w galerii. */
data class ExportOutput(val file: File, val galleryUri: Uri?, val sizeBytes: Long, val durationMs: Long)

/** Eksport projektu do MP4 (H.264/H.265 + AAC) albo M4A przez Media3 Transformer. */
object Exporter {

    suspend fun export(
        context: Context,
        project: Project,
        settings: ExportSettings,
        onProgress: (Float) -> Unit,
    ): ExportOutput {
        val seq = settings.resolve(project.settings)
        val audioOnly = settings.format == ExportFormat.AUDIO
        val built = CompositionFactory.build(context, project, seq, includeTitles = true, audioOnly = audioOnly)
            ?: error("Oś czasu jest pusta – dodaj klipy")
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safeName = project.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "OpenClipFlow" }
        val out = File(dir, safeName + if (audioOnly) ".m4a" else ".mp4")

        val result = withContext(Dispatchers.Main) { run(context, built.composition, settings, seq, out, onProgress) }
        onProgress(1f)
        val gallery = withContext(Dispatchers.IO) { runCatching { saveToGallery(context, out, audioOnly) }.getOrNull() }
        return ExportOutput(out, gallery, out.length(), result.durationMs.takeIf { it > 0 } ?: built.durationMs)
    }

    private suspend fun run(
        context: Context,
        composition: Composition,
        settings: ExportSettings,
        seq: com.openclipflow.app.model.SequenceSettings,
        out: File,
        onProgress: (Float) -> Unit,
    ): ExportResult = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val encoders = DefaultEncoderFactory.Builder(context.applicationContext)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(settings.videoBitrate(seq)).build())
            .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(settings.audioBitrate).build())
            .setEnableFallback(true)
            .build()
        val holder = ProgressHolder()
        lateinit var poll: Runnable
        val transformer = Transformer.Builder(context.applicationContext)
            .setVideoMimeType(settings.codec.mime)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoders)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacks(poll)
                    if (cont.isActive) cont.resume(exportResult)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    handler.removeCallbacks(poll)
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(describe(exportException), exportException))
                }
            })
            .build()
        poll = Runnable {
            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
            handler.postDelayed(poll, 250)
        }
        cont.invokeOnCancellation {
            handler.post {
                handler.removeCallbacks(poll)
                transformer.cancel()
                out.delete()
            }
        }
        transformer.start(composition, out.absolutePath)
        handler.postDelayed(poll, 250)
    }

    private fun describe(e: ExportException): String = when (e.errorCode) {
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED, ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
            "Urządzenie nie obsługuje tych ustawień kodowania – spróbuj H.264 lub niższej rozdzielczości"
        ExportException.ERROR_CODE_DECODER_INIT_FAILED, ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "Nie można zdekodować jednego z plików źródłowych"
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND, ExportException.ERROR_CODE_IO_NO_PERMISSION ->
            "Brak dostępu do pliku źródłowego – zaimportuj go ponownie"
        else -> "Eksport nieudany: ${e.errorCodeName}"
    }

    /** Zapis gotowego pliku w galerii (Filmy/OpenClipFlow albo Muzyka/OpenClipFlow). */
    private fun saveToGallery(context: Context, file: File, audio: Boolean): Uri? {
        val resolver = context.contentResolver
        val collection = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.nameWithoutExtension + "-" + stamp + "." + file.extension)
            put(MediaStore.MediaColumns.MIME_TYPE, if (audio) "audio/mp4" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/OpenClipFlow")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Kopiuje plik wynikowy w miejsce wybrane przez użytkownika (Zapisz jako…). */
    suspend fun copyTo(context: Context, file: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Nie można zapisać pliku")
    }
}
