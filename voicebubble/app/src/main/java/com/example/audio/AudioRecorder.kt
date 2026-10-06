package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Handles audio recording to an AAC (.m4a) file for Groq Whisper transcription.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var isRecording = false

    fun start(outputFile: File): Boolean {
        if (isRecording) stop()

        return try {
            if (outputFile.exists()) {
                outputFile.delete()
            }

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording: ${e.message}", e)
            recorder?.release()
            recorder = null
            isRecording = false
            false
        }
    }

    fun stop(): Boolean {
        if (!isRecording) return false

        return try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null
            isRecording = false
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording cleanly: ${e.message}", e)
            recorder?.release()
            recorder = null
            isRecording = false
            false
        }
    }

    fun isCurrentlyRecording(): Boolean = isRecording
}
