package com.example.localledger.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRouteTest {
    @Test
    fun prefersThePlatformRecognizerThenTheSystemIntent() {
        assertEquals(SpeechRoute.RECOGNIZER, resolveSpeechRoute(recognizerAvailable = true, intentResolvable = true))
        assertEquals(SpeechRoute.RECOGNIZER, resolveSpeechRoute(recognizerAvailable = true, intentResolvable = false))
        assertEquals(SpeechRoute.INTENT, resolveSpeechRoute(recognizerAvailable = false, intentResolvable = true))
        assertEquals(SpeechRoute.TEXT, resolveSpeechRoute(recognizerAvailable = false, intentResolvable = false))
    }
}
