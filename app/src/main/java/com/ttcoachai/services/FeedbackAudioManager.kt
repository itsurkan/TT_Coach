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
                if (status == 0) loadedSounds.add(sampleId)
            }
                
            ticSoundId = soundPool?.load(context, R.raw.tic, 1) ?: 0
            tacSoundId = soundPool?.load(context, R.raw.tac, 1) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPool", e)
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ToneGenerator", e)
        }
    }

    fun playTic() = playSound(ticSoundId)
    fun playTac() = playSound(tacSoundId)

    private fun playSound(soundId: Int) {
        if (loadedSounds.contains(soundId)) {
            soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
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
