package xyz.nextalone.nagram.helper

import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import xyz.nextalone.nagram.NaConfig

object AudioEnhance {
    // Use private vars to prevent external modification and potential resource leaks
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @Synchronized
    fun initVoiceEnhance(audioRecord: AudioRecord) {
        // Release any existing resources first to prevent leaks
        releaseVoiceEnhance()
        
        if (!NaConfig.noiseSuppressAndVoiceEnhance.Bool()) return
        
        if (AutomaticGainControl.isAvailable()) {
            automaticGainControl = AutomaticGainControl.create(audioRecord.audioSessionId)
            automaticGainControl?.enabled = true
        }
        if (AcousticEchoCanceler.isAvailable()) {
            acousticEchoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
            acousticEchoCanceler?.enabled = true
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)
            noiseSuppressor?.enabled = true
        }
    }

    @Synchronized
    fun releaseVoiceEnhance() {
        automaticGainControl?.release()
        automaticGainControl = null
        
        acousticEchoCanceler?.release()
        acousticEchoCanceler = null
        
        noiseSuppressor?.release()
        noiseSuppressor = null
    }

    fun isAvailable(): Boolean {
        return AutomaticGainControl.isAvailable() || NoiseSuppressor.isAvailable() || AcousticEchoCanceler.isAvailable()
    }
}
