package com.speech.recognizer

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.lang.Exception

fun log(text: String, tag: String = "ffnet") {
    Log.i(tag, text)
}

fun error(text: String, exception: Throwable? = null, tag: String = "ffnet") {
    Log.e(tag, text, exception)
}

val Context.sharedPrefs: SharedPreferences
get() {
    return getSharedPreferences("SpeechRecognizer", Context.MODE_PRIVATE)
}
