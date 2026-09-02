package com.rork.weatherloom.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.rork.weatherloom.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/** One-shot cues. Everything else in the mix is a continuous layer. */
enum class Sfx { Tap, ThreadDraw, LoomStart, Bloom, Frost, Wilt, Collect }

/**
 * The whole soundtrack: one music bed, two ambience layers whose volumes follow the
 * simulation, and a small bank of one-shots.
 *
 * Every clip is bundled in res/raw, so audio never touches the network. Ambience
 * volumes are ramped rather than snapped — weather should arrive, not switch on.
 */
object LoomAudio {

    private const val MUSIC_GAIN = 0.38f
    private const val RAIN_GAIN = 0.60f
    private const val WIND_GAIN = 0.34f
    private const val STEP = 0.05f

    private var pool: SoundPool? = null
    private val soundIds = HashMap<Sfx, Int>()
    private val ready = HashSet<Int>()

    private var music: MediaPlayer? = null
    private var rain: MediaPlayer? = null
    private var wind: MediaPlayer? = null

    private var musicEnabled = true
    private var sfxEnabled = true
    private var foreground = true
    private var started = false

    private var musicTarget = 0f
    private var rainTarget = 0f
    private var windTarget = 0f
    private var musicNow = 0f
    private var rainNow = 0f
    private var windNow = 0f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ramp: Job? = null

    fun init(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        sp.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready.add(sampleId)
        }
        pool = sp
        runCatching {
            soundIds[Sfx.Tap] = sp.load(app, R.raw.ui_tap, 1)
            soundIds[Sfx.ThreadDraw] = sp.load(app, R.raw.thread_draw, 1)
            soundIds[Sfx.LoomStart] = sp.load(app, R.raw.loom_start, 1)
            soundIds[Sfx.Bloom] = sp.load(app, R.raw.bloom_cue, 1)
            soundIds[Sfx.Frost] = sp.load(app, R.raw.frost_cue, 1)
            soundIds[Sfx.Wilt] = sp.load(app, R.raw.wilt_cue, 1)
            soundIds[Sfx.Collect] = sp.load(app, R.raw.collect_cue, 1)
        }

        music = loopPlayer(app, R.raw.loom_ambient)
        rain = loopPlayer(app, R.raw.rain_loop)
        wind = loopPlayer(app, R.raw.wind_loop)
        applyVolumes()
    }

    private fun loopPlayer(context: Context, res: Int): MediaPlayer? = runCatching {
        MediaPlayer.create(context, res)?.apply {
            isLooping = true
            setVolume(0f, 0f)
        }
    }.getOrNull()

    // ------------------------------------------------------------------ settings

    fun setMusicEnabled(value: Boolean) {
        musicEnabled = value
        musicTarget = if (value && foreground) 1f else 0f
        ensureRamp()
    }

    fun setSfxEnabled(value: Boolean) {
        sfxEnabled = value
        if (!value) {
            rainTarget = 0f
            windTarget = 0f
            ensureRamp()
        }
    }

    /** Called when the music bed should be audible at all (i.e. the app is on screen). */
    fun enterForeground() {
        foreground = true
        musicTarget = if (musicEnabled) 1f else 0f
        ensureRamp()
    }

    fun enterBackground() {
        foreground = false
        musicTarget = 0f
        rainTarget = 0f
        windTarget = 0f
        // Snap silent immediately — no music leaking over other apps.
        musicNow = 0f; rainNow = 0f; windNow = 0f
        applyVolumes()
        runCatching { music?.pause(); rain?.pause(); wind?.pause() }
    }

    // ----------------------------------------------------------------- ambience

    /**
     * Sets how loud the weather is, 0..1 each. Values are ramped over roughly a
     * second so a passing shower fades in instead of clicking on.
     */
    fun setAmbience(rainLevel: Float, windLevel: Float) {
        if (!sfxEnabled) return
        rainTarget = rainLevel.coerceIn(0f, 1f)
        windTarget = windLevel.coerceIn(0f, 1f)
        ensureRamp()
    }

    fun silenceAmbience() {
        rainTarget = 0f
        windTarget = 0f
        ensureRamp()
    }

    // --------------------------------------------------------------------- sfx

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!sfxEnabled || !foreground) return
        val sp = pool ?: return
        val id = soundIds[sfx] ?: return
        if (id !in ready) return
        // Tiny pitch drift keeps repeated stitches from sounding mechanical.
        val rate = when (sfx) {
            Sfx.ThreadDraw, Sfx.Tap -> 0.94f + Random.nextFloat() * 0.12f
            else -> 1f
        }
        val v = volume.coerceIn(0f, 1f)
        runCatching { sp.play(id, v, v, 1, 0, rate) }
    }

    // ------------------------------------------------------------------ ramping

    private fun ensureRamp() {
        if (ramp?.isActive == true) return
        ramp = scope.launch {
            while (true) {
                val settled = approach()
                applyVolumes()
                if (settled) break
                delay(45L)
            }
        }
    }

    /** Steps every level toward its target. Returns true once nothing is moving. */
    private fun approach(): Boolean {
        fun step(now: Float, target: Float): Float = when {
            abs(target - now) <= STEP -> target
            target > now -> now + STEP
            else -> now - STEP
        }
        musicNow = step(musicNow, musicTarget)
        rainNow = step(rainNow, rainTarget)
        windNow = step(windNow, windTarget)
        return musicNow == musicTarget && rainNow == rainTarget && windNow == windTarget
    }

    private fun applyVolumes() {
        runCatching {
            music?.let { p ->
                p.setVolume(musicNow * MUSIC_GAIN, musicNow * MUSIC_GAIN)
                if (musicNow > 0f && !p.isPlaying) p.start()
                if (musicNow == 0f && p.isPlaying) p.pause()
            }
            rain?.let { p ->
                p.setVolume(rainNow * RAIN_GAIN, rainNow * RAIN_GAIN)
                if (rainNow > 0f && !p.isPlaying) p.start()
                if (rainNow == 0f && p.isPlaying) p.pause()
            }
            wind?.let { p ->
                p.setVolume(windNow * WIND_GAIN, windNow * WIND_GAIN)
                if (windNow > 0f && !p.isPlaying) p.start()
                if (windNow == 0f && p.isPlaying) p.pause()
            }
        }
    }

    fun release() {
        ramp?.cancel()
        runCatching {
            music?.release(); rain?.release(); wind?.release(); pool?.release()
        }
        music = null; rain = null; wind = null; pool = null
        soundIds.clear(); ready.clear()
        started = false
    }
}
