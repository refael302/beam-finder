package io.github.refael302.beamfinder.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import io.github.refael302.beamfinder.settings.AudioStream
import io.github.refael302.beamfinder.settings.BeepStyle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Audio output driven by [BeepEngine]. Implementations must be thread-safe. */
interface BeepPlayer {
    fun configure(style: BeepStyle, volume: Float, stream: AudioStream)

    /**
     * Emits one beep sized to fit within [intervalMs].
     * @return the beep duration in ms (used to sync haptics).
     */
    fun beep(intervalMs: Long): Long

    /** Starts (or keeps) a continuous tone. */
    fun startContinuous()

    /** Stops any beep or continuous tone. */
    fun stop()

    /** Acquires the audio output. Idempotent; call before beeping. */
    fun open()

    /** Releases the audio output. [open] may be called again later. */
    fun release()
}

/**
 * Streaming [AudioTrack] synthesizer. While open, a dedicated thread continuously writes small
 * chunks (silence when idle) so beeps start with only the track's buffer latency and never click.
 */
class AudioTrackBeepPlayer : BeepPlayer {
    @Volatile private var style = BeepStyle.Pulse
    @Volatile private var gain = 0.8f
    @Volatile private var stream = AudioStream.Alarm
    @Volatile private var continuous = false

    /** Pending beep length in samples (0 = none). */
    private val pendingBeep = AtomicInteger(0)
    private val stopRequested = AtomicBoolean(false)

    // Synth state: touched only by the synth thread.
    private var phase = 0.0
    private var beepPos = 0
    private var beepLen = 0
    private var contLevel = 0f
    private var clickPos = 0

    /** The current synth thread; the loop exits when it is no longer this thread. */
    @Volatile private var thread: Thread? = null

    @Synchronized
    override fun open() {
        if (thread != null) return
        continuous = false
        pendingBeep.set(0)
        val t = Thread(::loop, "beep-synth").apply { isDaemon = true }
        thread = t // assign before start: the loop runs only while it is the current thread
        t.start()
    }

    override fun configure(style: BeepStyle, volume: Float, stream: AudioStream) {
        this.style = style
        this.gain = volume.coerceIn(0f, 1f)
        this.stream = stream
    }

    override fun beep(intervalMs: Long): Long {
        val ms = beepDurationMs(style, intervalMs)
        pendingBeep.set((ms * SAMPLE_RATE / 1000).toInt().coerceAtLeast(1))
        return ms
    }

    override fun startContinuous() {
        continuous = true
    }

    override fun stop() {
        continuous = false
        pendingBeep.set(0)
        stopRequested.set(true)
    }

    @Synchronized
    override fun release() {
        val t = thread ?: return
        thread = null
        continuous = false
        t.join(500)
    }

    // ---- synth thread ----

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val me = Thread.currentThread()
        phase = 0.0
        beepPos = 0
        beepLen = 0
        contLevel = 0f
        clickPos = 0
        val buf = ShortArray(CHUNK)
        var track: AudioTrack? = null
        var trackStream: AudioStream? = null
        try {
            while (thread === me) {
                val wantStream = stream
                var current = track
                if (current == null || trackStream != wantStream) {
                    current?.release()
                    current = createTrack(wantStream)
                    track = current
                    trackStream = wantStream
                    if (current == null) {
                        Thread.sleep(500)
                        continue
                    }
                    current.play()
                }
                render(buf)
                current.write(buf, 0, buf.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Beep synth stopped", e)
        } finally {
            track?.let {
                try {
                    it.pause()
                    it.flush()
                } catch (_: IllegalStateException) {
                }
                it.release()
            }
        }
    }

    private fun render(buf: ShortArray) {
        if (stopRequested.getAndSet(false)) {
            beepPos = beepLen
        }
        val newBeep = pendingBeep.getAndSet(0)
        if (newBeep > 0) {
            beepLen = newBeep
            beepPos = 0
        }
        val st = style
        val g = gain
        val contTarget = if (continuous) 1f else 0f
        val freq = frequency(st)
        val dPhase = 2.0 * PI * freq / SAMPLE_RATE
        val attack = SAMPLE_RATE * 3 / 1000
        val release = SAMPLE_RATE * 8 / 1000

        for (i in buf.indices) {
            contLevel += (contTarget - contLevel) * RAMP
            var env = 0f
            if (beepPos < beepLen) {
                env = when (st) {
                    // Click: sharp attack with exponential decay.
                    BeepStyle.Click -> exp(-beepPos.toFloat() / (SAMPLE_RATE * 0.0015f))
                    else -> min(
                        min(1f, beepPos.toFloat() / attack),
                        min(1f, (beepLen - beepPos).toFloat() / release),
                    )
                }
                beepPos++
            }
            var cont = contLevel
            if (st == BeepStyle.Click && cont > 0.001f) {
                // Continuous "click" style = rapid click train.
                cont *= exp(-clickPos.toFloat() / (SAMPLE_RATE * 0.0015f))
                clickPos = (clickPos + 1) % CLICK_TRAIN_PERIOD
            }
            val amp = maxOf(env, cont) * g * MASTER
            val sample = if (amp > 0f) waveform(st, phase) * amp else 0f
            phase += dPhase
            if (phase > 2 * PI) phase -= 2 * PI
            buf[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun waveform(st: BeepStyle, p: Double): Float = when (st) {
        BeepStyle.Pulse -> sin(p).toFloat()
        // Warmer tone: fundamental + a little 2nd harmonic.
        BeepStyle.Tone -> (0.8 * sin(p) + 0.2 * sin(2 * p)).toFloat()
        // Click: square-ish for a crisp transient.
        BeepStyle.Click -> if (sin(p) >= 0) 0.7f else -0.7f
    }

    private fun frequency(st: BeepStyle): Double = when (st) {
        BeepStyle.Pulse -> 1500.0
        BeepStyle.Tone -> 880.0
        BeepStyle.Click -> 2500.0
    }

    private fun createTrack(stream: AudioStream): AudioTrack? = try {
        val usage = when (stream) {
            AudioStream.Alarm -> AudioAttributes.USAGE_ALARM
            AudioStream.Media -> AudioAttributes.USAGE_MEDIA
        }
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, CHUNK * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    } catch (e: Exception) {
        Log.e(TAG, "AudioTrack creation failed", e)
        null
    }

    companion object {
        private const val TAG = "BeepPlayer"
        const val SAMPLE_RATE = 44_100
        private const val CHUNK = 256
        /** Per-sample smoothing for continuous on/off (~5 ms) to avoid clicks. */
        private const val RAMP = 0.005f
        private const val MASTER = 0.9f
        private const val CLICK_TRAIN_PERIOD = SAMPLE_RATE / 40 // 40 clicks/s

        /** Beep length per style, shortened to fit fast intervals. */
        fun beepDurationMs(style: BeepStyle, intervalMs: Long): Long {
            val nominal = when (style) {
                BeepStyle.Pulse -> 50L
                BeepStyle.Tone -> 110L
                BeepStyle.Click -> 10L
            }
            return min(nominal, (intervalMs * 6 / 10).coerceAtLeast(8L))
        }
    }
}
