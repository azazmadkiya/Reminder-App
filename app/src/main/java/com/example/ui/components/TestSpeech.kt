package com.example.ui.components

import android.content.Context
import android.speech.SpeechRecognizer

fun checkSpeech(context: Context): Boolean {
    return SpeechRecognizer.isRecognitionAvailable(context)
}
