package com.speech.recognizer

import android.content.Context
import android.media.AudioFormat
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import java.io.File
import java.io.IOException

class SoundRecorder(var outputFile: String) {
    private var mediaRecorder: MediaRecorder? = null

    init {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(AudioFormat.ENCODING_PCM_16BIT)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioChannels(1)
            setAudioEncodingBitRate(256_000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                setOutputFile(File(outputFile))
            else
                setOutputFile(outputFile)
        }
    }

    fun startRecording() {
        if (mediaRecorder == null)
            return
        try {
            mediaRecorder!!.prepare()
            mediaRecorder!!.start()
            log("Recording started!")

        } catch (e: IllegalStateException) {
            error("Error in prepare", e)
            e.printStackTrace()
        } catch (e: IOException) {
            error("Error in IO", e)
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        mediaRecorder?.stop()
        mediaRecorder?.release()
    }

    private fun play(context: Context) {
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(outputFile)
            mediaPlayer.prepare()
            mediaPlayer.start()
            Toast.makeText(context, "Playing Audio", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // make something
            error("Error playing media", e)
            e.printStackTrace()
        }
    }


    companion object {
        private const val SAMPLE_RATE: Int = 16000
    }
}