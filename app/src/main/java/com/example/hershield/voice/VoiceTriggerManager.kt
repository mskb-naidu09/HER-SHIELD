package com.example.hershield.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast

class VoiceTriggerManager(
    private val context: Context,
    private val onHelp: () -> Unit
) {

    private var recognizer: SpeechRecognizer? = null
    private var running = false

    private val handler = Handler(Looper.getMainLooper())

    private val recognizerIntent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "en-IN"
            )
        }

    fun start() {

        if (running) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {

            Toast.makeText(
                context,
                "Speech recognition not available on this device",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        running = true

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }

        listen()
    }

    fun stop() {

        running = false

        handler.removeCallbacksAndMessages(null)

        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {

        if (running) {
            recognizer?.startListening(recognizerIntent)
        }
    }

    private fun restart() {

        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({

            if (running) {

                recognizer?.cancel()

                listen()
            }

        }, 600)
    }

    private fun heardHelp(bundle: Bundle?): Boolean {

        val list =
            bundle?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            ) ?: return false

        return list.any {
            it.contains("help", ignoreCase = true)
        }
    }

    private val listener =
        object : RecognitionListener {

            override fun onPartialResults(
                partialResults: Bundle?
            ) {

                if (heardHelp(partialResults)) {

                    onHelp()

                    restart()
                }
            }

            override fun onResults(
                results: Bundle?
            ) {

                if (heardHelp(results)) {
                    onHelp()
                }

                restart()
            }

            override fun onError(error: Int) {
                restart()
            }

            override fun onReadyForSpeech(
                params: Bundle?
            ) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(
                rmsdB: Float
            ) {}

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {}

            override fun onEndOfSpeech() {}

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {}
        }
}