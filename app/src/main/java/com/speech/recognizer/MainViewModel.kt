package com.speech.recognizer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.speech.recognizer.api.RetrofitBuilder
import java.io.File
class MainViewModel : ViewModel() {

    private var api = RetrofitBuilder

    val recordingState = MutableLiveData(RecordingState.IDLE)

    val apiSuccessResult = MutableLiveData<String?>()
    val apiErrorResult = MutableLiveData<Exception?>()
    val isPredictionProcessing = MutableLiveData(false)


    val smile = Transformations.map(apiSuccessResult) { res ->
        when (res) {
            null -> R.drawable.error
            "happy" -> R.drawable.happy
            "sad" -> R.drawable.sad
            "neutral" -> R.drawable.neutral
            "angry" -> R.drawable.angry
            else -> R.drawable.error
        }
    }

    val timerValue = MutableLiveData(0L)

    // Transform the timer value in text format
    val timerText = Transformations.map(timerValue) { time ->
        val sec = time % 60
        val min = (time / 60).toInt()
        val s = sec.toString().padStart(2, '0')
        val m = min.toString().padStart(2, '0')
        "$m:$s"
    }

    // Should the button be Play or Stop
    val isRecording = Transformations.map(recordingState) { state ->
        when (state) {
            RecordingState.RECORDING -> true
            else -> false
        }
    }

    // Should show Reset button or not?
    val isComplete = Transformations.map(recordingState) { state ->
        when (state) {
            RecordingState.COMPLETE -> true
            else -> false
        }
    }

    fun predict(audio: File) {
        // Show Progress bar
        isPredictionProcessing.postValue(true)
        api.predict(audio) { result ->
            // Prediction Completed make progress bar invisible
            isPredictionProcessing.postValue(false)
            result.fold(
                // If successful, set the error to null incase if it contained a previous non-null value
                onSuccess = { mRes ->
                    apiErrorResult.postValue(null)
                    apiSuccessResult.postValue("You are feeling ${mRes.result!!}")
                },
                // If Exception, set the success to null incase if it contained a previous valid value
                onFailure = { ex ->
                    ex.printStackTrace()
                    apiErrorResult.postValue(Exception(ex))
                    apiSuccessResult.postValue(null)
                }
            )
        }
    }

}

