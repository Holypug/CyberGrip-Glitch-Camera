package com.cybergrip.glitch

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

/** Experimental silent MP4 recorder. Encodes the same processed frames as live preview. */
class GlitchVideoEncoder(private val context: Context, private val width: Int = 1920, private val height: Int = 1080, private val bitrate: Int = 12_000_000) {
    private val fps = 30
    private lateinit var codec: MediaCodec
    private lateinit var muxer: MediaMuxer
    private lateinit var temp: File
    private var track = -1
    private var muxerStarted = false
    private var started = false
    private var lastPts = -1L
    private var firstFrameTimeUs = -1L
    private val info = MediaCodec.BufferInfo()

    fun start() {
        temp = File.createTempFile("cybergrip_", ".mp4", context.cacheDir)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            started = true
        } catch (e: Exception) {
            codec.release()
            temp.delete()
            throw e
        }
    }

    fun addFrame(bitmap: Bitmap) {
        if (!started) return
        // Center crop to 16:9 before scaling; do not distort the original 4:3 preview.
        val targetAspect = width.toFloat() / height
        val srcAspect = bitmap.width.toFloat() / bitmap.height
        val cropW = if (srcAspect > targetAspect) (bitmap.height * targetAspect).toInt() else bitmap.width
        val cropH = if (srcAspect < targetAspect) (bitmap.width / targetAspect).toInt() else bitmap.height
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - cropW) / 2, (bitmap.height - cropH) / 2, cropW, cropH)
        val scaled = if (cropped.width == width && cropped.height == height) cropped else Bitmap.createScaledBitmap(cropped, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val data = ByteArray(width * height * 3 / 2)
        var uv = width * height
        for (y in 0 until height) for (x in 0 until width) {
            val c = pixels[y * width + x]
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            val yy = ((66*r + 129*g + 25*b + 128) shr 8) + 16
            data[y * width + x] = yy.coerceIn(0, 255).toByte()
            if (y % 2 == 0 && x % 2 == 0) {
                val u = ((-38*r - 74*g + 112*b + 128) shr 8) + 128
                val v = ((112*r - 94*g - 18*b + 128) shr 8) + 128
                data[uv++] = u.coerceIn(0, 255).toByte()
                data[uv++] = v.coerceIn(0, 255).toByte()
            }
        }
        val index = codec.dequeueInputBuffer(10000)
        if (index >= 0) {
            val buffer = codec.getInputBuffer(index) ?: error("Encoder buffer unavailable")
            buffer.clear(); buffer.put(data)
            val nowUs = android.os.SystemClock.elapsedRealtimeNanos() / 1000L
            if (firstFrameTimeUs < 0) firstFrameTimeUs = nowUs
            val pts = (nowUs - firstFrameTimeUs).coerceAtLeast(lastPts + 1)
            lastPts = pts
            codec.queueInputBuffer(index, 0, data.size, pts, 0)
        }
        drain(false)
        if (scaled !== cropped) scaled.recycle()
        if (cropped !== bitmap) cropped.recycle()
    }

    private fun drain(end: Boolean) {
        var retries = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (end) 10000 else 0)
            when (index) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!end || ++retries > 300) break
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start(); muxerStarted = true
                    }
                }
                else -> if (index >= 0) {
                    val output = codec.getOutputBuffer(index)
                    if (output != null && info.size > 0 && muxerStarted && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        output.position(info.offset); output.limit(info.offset + info.size)
                        muxer.writeSampleData(track, output, info)
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                    if (eos) return
                }
            }
        }
    }

    fun finish() {
        if (!started) return
        try {
            val index = codec.dequeueInputBuffer(1_000_000)
            if (index >= 0) codec.queueInputBuffer(index, 0, 0, lastPts.coerceAtLeast(0) + 33333, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            if (!muxerStarted) error("Nessun fotogramma registrato")
            muxer.stop()
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "CYBERGRIP_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CyberGrip")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Impossibile creare MP4 nella galleria")
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                    ?: error("Impossibile scrivere MP4")
                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
        } finally {
            started = false
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { muxer.release() } catch (_: Exception) {}
            temp.delete()
        }
    }
}
