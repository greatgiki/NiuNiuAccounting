package com.example.localledger.ui.voice

/** How a device can turn speech into text without bundling a model. */
enum class SpeechRoute {
    /** [android.speech.SpeechRecognizer], including OEM services on HyperOS. */
    RECOGNIZER,

    /** [android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH]. */
    INTENT,

    /** No recognizer. The user types or dictates with the input method. */
    TEXT,
}

fun resolveSpeechRoute(recognizerAvailable: Boolean, intentResolvable: Boolean): SpeechRoute = when {
    recognizerAvailable -> SpeechRoute.RECOGNIZER
    intentResolvable -> SpeechRoute.INTENT
    else -> SpeechRoute.TEXT
}
