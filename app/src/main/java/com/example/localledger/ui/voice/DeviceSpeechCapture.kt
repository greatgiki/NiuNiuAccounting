package com.example.localledger.ui.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

internal interface SpeechCallbacks {
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(code: Int)
}

/**
 * On-device speech via the platform recognizer. HyperOS and other Chinese ROMs
 * expose their own [android.speech.RecognitionService]; this class does not call
 * a third-party cloud API and does not ship a model.
 */
internal class DeviceSpeechCapture(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var session = 0
    private var released = false

    fun route(): SpeechRoute {
        val available = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)
        val resolvable = runCatching {
            appContext.packageManager.resolveActivity(recognizeIntent(), PackageManager.MATCH_DEFAULT_ONLY) != null
        }.getOrDefault(false)
        return resolveSpeechRoute(available, resolvable)
    }

    fun recognizeIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "例如：午饭三十五，打车二十")
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
    }

    fun start(callbacks: SpeechCallbacks) {
        main.post {
            if (released) return@post
            try {
                startOnMain(callbacks)
            } catch (_: Throwable) {
                callbacks.onError(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    fun stop() {
        main.post {
            runCatching { recognizer?.stopListening() }
        }
    }

    fun release() {
        released = true
        main.post { destroyRecognizer() }
    }

    private fun startOnMain(callbacks: SpeechCallbacks) {
        destroyRecognizer()
        val current = ++session
        val created = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            private fun alive() = !released && session == current

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults.bestText()
                if (text.isBlank() || !alive()) return
                main.post { if (alive()) callbacks.onPartial(text) }
            }

            override fun onResults(results: Bundle?) {
                if (!alive()) return
                val text = results.bestText()
                main.post { if (alive()) callbacks.onFinal(text) }
            }

            override fun onError(error: Int) {
                if (!alive()) return
                main.post { if (alive()) callbacks.onError(error) }
            }
        })
        created.startListening(recognizeIntent())
    }

    private fun destroyRecognizer() {
        val current = recognizer ?: return
        recognizer = null
        runCatching { current.cancel() }
        runCatching { current.destroy() }
    }

    private fun Bundle?.bestText(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull { it.isNotBlank() }
            .orEmpty()
}

internal fun speechErrorGivesUp(code: Int): Boolean = when (code) {
    SpeechRecognizer.ERROR_AUDIO,
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_SERVER,
    -> true
    else -> false
}

internal fun speechErrorMessage(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "没有麦克风权限。请点输入框，使用输入法上的语音键，或直接输入。"
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> "没听清。请再说一次，或直接输入。"
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    -> "手机自带的语音服务暂时不可用。请点输入框，使用输入法上的语音键，或直接输入。"
    SpeechRecognizer.ERROR_AUDIO -> "麦克风没有录到声音。请重试，或直接输入。"
    else -> "语音识别没有完成。请重试，或直接输入。"
}
