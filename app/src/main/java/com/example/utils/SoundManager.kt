package com.example.utils

import android.content.Context
import android.os.Build
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SoundManager(private val context: Context) {
    private var isSoundEnabled = true
    private var isMusicEnabled = true
    var isMusicBlocked = false
    
    // Volume levels (0.0f to 1.0f)
    private var musicVolume = 0.60f
    private var sfxVolume = 0.80f
    
    private var soundPool: SoundPool? = null
    private var mediaPlayer: MediaPlayer? = null
    
    // Loaded sound pool IDs
    private var tapSoundId = -1
    private var movementSoundId = -1
    private var switchSoundId = -1
    private var scoreSoundId = -1
    private var crashSoundId = -1
    private var newBestSoundId = -1
    private var coinSoundId = -1
    private var purchaseSoundId = -1

    init {
        // Build SoundPool with high performance configuration for real-time game audio
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(attributes)
            .build()
            
        // Generate and load high-quality audio assets asynchronously to prevent any main-thread blocking on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                generateAndLoadSounds()
            } catch (e: Exception) {
                Log.e("SoundManager", "Error pre-synthesizing and loading sound assets", e)
            }
        }
    }

    private fun generateAndLoadSounds() {
        val cacheDir = context.cacheDir
        val sampleRate = 22050

        // Define files in cache
        val tapFile = File(cacheDir, "tap.wav")
        val movementFile = File(cacheDir, "movement.wav")
        val switchFile = File(cacheDir, "switch.wav")
        val scoreFile = File(cacheDir, "score.wav")
        val crashFile = File(cacheDir, "crash.wav")
        val newBestFile = File(cacheDir, "new_best.wav")
        val coinFile = File(cacheDir, "coin.wav")
        val purchaseFile = File(cacheDir, "purchase.wav")

        // Pre-synthesize and write WAV files to disk
        writeWavFile(tapFile, sampleRate, generateTapSamples(sampleRate))
        writeWavFile(movementFile, sampleRate, generateMovementSamples(sampleRate))
        writeWavFile(switchFile, sampleRate, generateSwitchSamples(sampleRate))
        writeWavFile(scoreFile, sampleRate, generateScoreSamples(sampleRate))
        writeWavFile(crashFile, sampleRate, generateCrashSamples(sampleRate))
        writeWavFile(newBestFile, sampleRate, generateNewBestSamples(sampleRate))
        writeWavFile(coinFile, sampleRate, generateCoinSamples(sampleRate))
        writeWavFile(purchaseFile, sampleRate, generatePurchaseSamples(sampleRate))

        // Load files into SoundPool for instantaneous low-latency playback
        soundPool?.let { pool ->
            tapSoundId = pool.load(tapFile.absolutePath, 1)
            movementSoundId = pool.load(movementFile.absolutePath, 1)
            switchSoundId = pool.load(switchFile.absolutePath, 1)
            scoreSoundId = pool.load(scoreFile.absolutePath, 1)
            crashSoundId = pool.load(crashFile.absolutePath, 1)
            newBestSoundId = pool.load(newBestFile.absolutePath, 1)
            coinSoundId = pool.load(coinFile.absolutePath, 1)
            purchaseSoundId = pool.load(purchaseFile.absolutePath, 1)
        }

        // Setup MediaPlayer with the uploaded background music from raw resources on the Main thread
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val mp = MediaPlayer.create(context, com.example.R.raw.hello)
                if (mp != null) {
                    mp.isLooping = true
                    // Use the configured music volume
                    mp.setVolume(musicVolume, musicVolume)
                    mediaPlayer = mp
                    
                    // Auto start if music is enabled
                    if (isMusicEnabled) {
                        startMusic()
                    }
                } else {
                    Log.e("SoundManager", "MediaPlayer.create returned null for R.raw.hello")
                }
            } catch (e: Exception) {
                Log.e("SoundManager", "Failed to setup background music loop from raw resources", e)
            }
        }
    }

    fun setMusicVolume(volume: Float) {
        musicVolume = volume.coerceIn(0.0f, 1.0f)
        try {
            mediaPlayer?.setVolume(musicVolume, musicVolume)
        } catch (e: Exception) {
            Log.e("SoundManager", "Error updating MediaPlayer volume", e)
        }
    }

    fun setSfxVolume(volume: Float) {
        sfxVolume = volume.coerceIn(0.0f, 1.0f)
    }

    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
    }

    fun isSoundEnabled(): Boolean = isSoundEnabled

    fun setMusicEnabled(enabled: Boolean) {
        isMusicEnabled = enabled
        if (enabled) {
            startMusic()
        } else {
            stopMusic()
        }
    }

    fun isMusicEnabled(): Boolean = isMusicEnabled

    fun startMusic() {
        if (!isMusicEnabled || isMusicBlocked) return
        try {
            mediaPlayer?.let { mp ->
                if (!mp.isPlaying) {
                    mp.start()
                }
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error starting background music", e)
        }
    }

    fun stopMusic() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.pause()
                }
            }
        } catch (e: Exception) {
            Log.e("SoundManager", "Error stopping background music", e)
        }
    }

    // Playback routines via SoundPool (guarantees zero-latency and non-blocking)
    fun playTapSound() {
        if (!isSoundEnabled) return
        val id = tapSoundId
        if (id != -1) {
            val vol = 0.90f * sfxVolume
            soundPool?.play(id, vol, vol, 1, 0, 1.0f)
        }
    }

    fun playMovementSound() {
        if (!isSoundEnabled) return
        val id = movementSoundId
        if (id != -1) {
            val vol = 0.38f * sfxVolume
            soundPool?.play(id, vol, vol, 1, 0, 1.0f) // Subtle soft whoosh
        }
    }

    fun playSwitchSound() {
        if (!isSoundEnabled) return
        val id = switchSoundId
        if (id != -1) {
            val vol = 0.70f * sfxVolume
            soundPool?.play(id, vol, vol, 1, 0, 1.0f)
        }
    }

    fun playScoreSound() {
        if (!isSoundEnabled) return
        val id = scoreSoundId
        if (id != -1) {
            val vol = 0.85f * sfxVolume
            soundPool?.play(id, vol, vol, 1, 0, 1.0f)
        }
    }

    fun playCrashSound() {
        if (!isSoundEnabled) return
        val id = crashSoundId
        if (id != -1) {
            val vol = 0.95f * sfxVolume
            soundPool?.play(id, vol, vol, 2, 0, 1.0f) // Higher priority to cut off other channels if needed
        }
    }

    fun playNewBestSound() {
        if (!isSoundEnabled) return
        val id = newBestSoundId
        if (id != -1) {
            val vol = 0.95f * sfxVolume
            soundPool?.play(id, vol, vol, 2, 0, 1.0f)
        }
    }

    fun playCoinSound() {
        if (!isSoundEnabled) return
        val id = coinSoundId
        if (id != -1) {
            val vol = 0.85f * sfxVolume
            soundPool?.play(id, vol, vol, 1, 0, 1.0f)
        }
    }

    fun playPurchaseSound() {
        if (!isSoundEnabled) return
        val id = purchaseSoundId
        if (id != -1) {
            val vol = 0.90f * sfxVolume
            soundPool?.play(id, vol, vol, 1, 0, 1.0f)
        }
    }

    fun release() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            
            soundPool?.release()
            soundPool = null
        } catch (e: Exception) {
            Log.e("SoundManager", "Error releasing audio players", e)
        }
    }

    // AUDIO SYNTHESIS ENGINE (generates high-fidelity mono 16-bit PCM WAV samples)

    private fun generateTapSamples(sampleRate: Int): ShortArray {
        val durationMs = 25
        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 1000.0 - 700.0 * progress
            phase += freq / sampleRate
            val env = Math.exp(-progress * 15.0)
            val signal = Math.sin(2.0 * Math.PI * phase) * env * 0.75
            samples[i] = (signal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    private fun generateMovementSamples(sampleRate: Int): ShortArray {
        val durationMs = 80
        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 280.0 - 180.0 * progress
            phase += freq / sampleRate
            val env = Math.sin(progress * Math.PI) * 0.16 // Smooth sine-curved volume envelope
            val sine = Math.sin(2.0 * Math.PI * phase)
            val noise = Math.random() * 2.0 - 1.0
            val signal = (sine * 0.85 + noise * 0.15) * env
            samples[i] = (signal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    private fun generateSwitchSamples(sampleRate: Int): ShortArray {
        val durationMs = 40
        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 600.0 + 600.0 * progress
            phase += freq / sampleRate
            val env = Math.exp(-progress * 8.0)
            val signal = Math.sin(2.0 * Math.PI * phase) * env * 0.65
            samples[i] = (signal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    private fun generateScoreSamples(sampleRate: Int): ShortArray {
        val durationMs = 150
        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            phase1 += 1200.0 / sampleRate
            phase2 += 1500.0 / sampleRate
            val env = Math.exp(-progress * 5.5)
            // Beautiful major third chord harmony
            val signal = (Math.sin(2.0 * Math.PI * phase1) * 0.6 + Math.sin(2.0 * Math.PI * phase2) * 0.4) * env * 0.45
            samples[i] = (signal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    private fun generateNewBestSamples(sampleRate: Int): ShortArray {
        val durationMs = 600
        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)
        var phase = 0.0
        
        // 4 notes in ascending arpeggio: C5, E5, G5, C6
        val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        val noteLength = numSamples / 4
        
        for (i in 0 until numSamples) {
            val noteIndex = (i / noteLength).coerceIn(0, 3)
            val currentFreq = freqs[noteIndex]
            phase += currentFreq / sampleRate
            
            // Local decay within the active note
            val sampleInNote = i % noteLength
            val progressInNote = sampleInNote.toDouble() / noteLength
            val noteEnv = Math.exp(-progressInNote * 3.0)
            
            // Global volume ramp-up then fade-out
            val progressGlobal = i.toDouble() / numSamples
            val globalEnv = if (progressGlobal < 0.2) progressGlobal / 0.2 else (1.0 - progressGlobal) / 0.8
            
            val sine = Math.sin(2.0 * Math.PI * phase)
            // Sparkle high chime: add a small amount of octave harmonic
            val harmonic = Math.sin(2.0 * Math.PI * phase * 2.0) * 0.3
            
            val signal = (sine + harmonic) * noteEnv * globalEnv * 0.55
            samples[i] = (signal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    private fun generateCrashSamples(sampleRate: Int): ShortArray {
        val durationMs = 250
        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 150.0 - 110.0 * progress
            phase += freq / sampleRate
            val env = Math.exp(-progress * 5.0)
            val sine = Math.sin(2.0 * Math.PI * phase)
            val noise = Math.random() * 2.0 - 1.0
            val signal = (sine * 0.8 + noise * 0.2) * env * 0.55
            samples[i] = (signal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    private fun generateCoinSamples(sampleRate: Int): ShortArray {
        val durationMs = 180
        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)
        var phase = 0.0
        val transitionSample = (sampleRate * 0.06).toInt() // transition to high note after 60ms
        
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = if (i < transitionSample) 987.77 else 1318.51 // B5 to E6
            phase += freq / sampleRate
            
            // Clean decay
            val env = Math.exp(-progress * 6.0)
            val signal = Math.sin(2.0 * Math.PI * phase) * env * 0.45
            samples[i] = (signal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    private fun generatePurchaseSamples(sampleRate: Int): ShortArray {
        val durationMs = 300
        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq1 = 523.25 // C5
            val freq2 = 659.25 // E5
            val freq3 = 783.99 // G5
            
            phase1 += freq1 / sampleRate
            phase2 += freq2 / sampleRate
            phase3 += freq3 / sampleRate
            
            val env = Math.exp(-progress * 4.5)
            val signal = (Math.sin(2.0 * Math.PI * phase1) * 0.3 + 
                          Math.sin(2.0 * Math.PI * phase2) * 0.3 + 
                          Math.sin(2.0 * Math.PI * phase3) * 0.3) * env * 0.50
            samples[i] = (signal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    private fun writeWavFile(file: File, sampleRate: Int, samples: ShortArray) {
        val totalAudioLen = samples.size * 2
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = sampleRate.toLong()
        val channels = 1
        val byteRate = longSampleRate * channels * 2

        val header = ByteArray(44)
        header[0] = 'R'.toByte() // RIFF
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.toByte() // WAVE
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte() // 'fmt ' chunk
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // Header length (16)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format (1)
        header[21] = 0
        header[22] = channels.toByte() // mono (1)
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2.toByte() // Block align (channels * bitsPerSample / 8)
        header[33] = 0
        header[34] = 16 // bits per sample (16)
        header[35] = 0
        header[36] = 'd'.toByte() // 'data' chunk header
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        FileOutputStream(file).use { out ->
            out.write(header)
            val byteBuffer = ByteBuffer.allocate(samples.size * 2)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                byteBuffer.putShort(sample)
            }
            out.write(byteBuffer.array())
        }
    }
}
