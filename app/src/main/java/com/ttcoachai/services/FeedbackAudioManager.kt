package com.ttcoachai.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import com.ttcoachai.R

/**
 * Manages audio resources (SoundPool, ToneGenerator) for FeedbackGenerator.
 */
class FeedbackAudioManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var toneGenerator: ToneGenerator? = null
    private var ticSoundId: Int = 0
    private var tacSoundId: Int = 0
    private val loadedSounds = mutableSetOf<Int>()

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

    fun release() {
        soundPool?.release()
        soundPool = null
        toneGenerator?.release()
        toneGenerator = null
    }
}
