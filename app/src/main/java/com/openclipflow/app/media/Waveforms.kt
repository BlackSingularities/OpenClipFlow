package com.openclipflow.app.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.openclipflow.app.model.MediaAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Przebieg fali dźwięku: szczyty głośności (0..1) w krokach [STEP_MS]. Liczone raz przez
 * zdekodowanie ścieżki do PCM i zapisywane na dysku, żeby ponowne otwarcie projektu było natychmiastowe.
 */
object Waveforms {
    const val STEP_MS = 20L
    private val memory = HashMap<String, FloatArray>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    fun cached(uri: String): FloatArray? = synchronized(memory) { memory[uri] }

    suspend fun load(context: Context, asset: MediaAsset): FloatArray? {
        if (!asset.hasAudio) return null
        cached(asset.uri)?.let { return it }
        return withContext(io) {
            cached(asset.uri) ?: run {
                val file = File(File(context.cacheDir, "waveforms").apply { mkdirs() }, asset.uri.hashCode().toUInt().toString(16) + "_" + asset.durationMs + ".wf")
                val data = readFile(file) ?: decode(context, asset)?.also { writeFile(file, it) }
                data?.also { synchronized(memory) { memory[asset.uri] = it } }
            }
        }
    }

    private fun readFile(f: File): FloatArray? = runCatching {
        if (!f.exists()) return null
        DataInputStream(f.inputStream().buffered()).use { s ->
            val n = s.readInt()
            FloatArray(n) { (s.readByte().toInt() and 0xFF) / 255f }
        }
    }.getOrNull()

    private fun writeFile(f: File, data: FloatArray) = runCatching {
        DataOutputStream(f.outputStream().buffered()).use { s ->
            s.writeInt(data.size)
            data.forEach { s.writeByte((it.coerceIn(0f, 1f) * 255).toInt()) }
        }
    }

    private suspend fun decode(context: Context, asset: MediaAsset): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(asset.uri), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val buckets = ((asset.durationMs / STEP_MS) + 1).toInt().coerceAtLeast(1)
            val peaks = FloatArray(buckets)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var frameIndex = 0L
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIdx = c.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = c.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = c.dequeueOutputBuffer(info, 5_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        sampleRate = c.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIdx >= 0 -> {
                        val out = c.getOutputBuffer(outIdx)
                        if (out != null && info.size > 0) {
                            val shorts = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val count = info.size / 2
                            var i = info.offset / 2
                            val end = i + count
                            // Co drugą próbkę wystarczy do obwiedni – szybciej przy długich plikach.
                            while (i < end) {
                                val frame = frameIndex + (i - info.offset / 2) / channels
                                val bucket = (frame * 1000 / sampleRate / STEP_MS).toInt()
                                if (bucket in peaks.indices) {
                                    val v = abs(shorts.get(i).toInt()) / 32768f
                                    if (v > peaks[bucket]) peaks[bucket] = v
                                }
                                i += 2
                            }
                            frameIndex += count / channels
                        }
                        c.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            // Normalizacja do najgłośniejszego miejsca (z lekką kompresją, żeby ciche nagrania też było widać).
            val maxPeak = peaks.maxOrNull()?.takeIf { it > 0.001f } ?: return peaks
            for (i in peaks.indices) peaks[i] = Math.sqrt((peaks[i] / max(maxPeak, 0.05f)).toDouble()).toFloat().coerceIn(0f, 1f)
            return peaks
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}

@Composable
fun rememberWaveform(asset: MediaAsset?): State<FloatArray?> {
    val context = LocalContext.current
    return produceState(asset?.let { Waveforms.cached(it.uri) }, asset?.uri) {
        if (asset != null && value == null) value = Waveforms.load(context, asset)
    }
}
