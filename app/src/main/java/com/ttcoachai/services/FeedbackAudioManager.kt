package com.ttcoachai.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import com.ttcoachai.R

/**
 * Manages audio resources (SoundPool, ToneGenerator, voice-clip MediaPlayer) for FeedbackGenerator.
 */
class FeedbackAudioManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var toneGenerator: ToneGenerator? = null
    private var ticSoundId: Int = 0
    private var tacSoundId: Int = 0
    private val loadedSounds = mutableSetOf<Int>()
    private var voicePlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "FeedbackAudioManager"
    }

    init {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            soundPool = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build()
            
            soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loadedSounds.add(sampleId)
                    Log.d(TAG, "Sound loaded successfully: sampleId=$sampleId")
                } else {
                    Log.e(TAG, "Failed to load sound: sampleId=$sampleId, status=$status")
                }
            }
                
            ticSoundId = soundPool?.load(context, R.raw.tic, 1) ?: 0
            tacSoundId = soundPool?.load(context, R.raw.tac, 1) ?: 0
            Log.d(TAG, "Requesting sounds: tic=$ticSoundId, tac=$tacSoundId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPool", e)
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ToneGenerator", e)
        }
    }

    fun playTic() { 
        Log.v(TAG, "Playing Tic requested")
        playSound(ticSoundId) 
    }
    fun playTac() { 
        Log.v(TAG, "Playing Tac requested")
        playSound(tacSoundId) 
    }

    private fun playSound(soundId: Int) {
        if (soundId != 0 && loadedSounds.contains(soundId)) {
            soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
            Log.v(TAG, "Sound triggered: $soundId")
        } else {
            Log.d(TAG, "Sound not played: $soundId (loaded: ${loadedSounds.contains(soundId)})")
        }
    }

    fun playTone(type: Int, duration: Int) {
        toneGenerator?.startTone(type, duration)
    }

    /**
     * Play a pre-recorded voice clip by raw resource id. Releases any
     * in-flight clip first (barge-in: the latest feedback wins), then creates
     * a fresh [MediaPlayer] for [resId]. Returns true if playback started.
     */
    fun playVoiceClip(resId: Int, volumePercent: Int): Boolean {
        releaseVoicePlayer()
        return try {
            val player = MediaPlayer.create(context, resId)
            if (player == null) {
                Log.e(TAG, "Failed to create MediaPlayer for resId=$resId")
                return false
            }
            val v = volumePercent.coerceIn(0, 100) / 100f
            player.setVolume(v, v)
            player.setOnCompletionListener {
                it.release()
                if (voicePlayer === it) voicePlayer = null
            }
            voicePlayer = player
            player.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play voice clip resId=$resId", e)
            false
        }
    }

    private fun releaseVoicePlayer() {
        try {
            voicePlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release previous voice clip", e)
        } finally {
            voicePlayer = null
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        toneGenerator?.release()
        toneGenerator = null
        releaseVoicePlayer()
    }
}
