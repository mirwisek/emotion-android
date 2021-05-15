package com.speech.recognizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.speech.recognizer.databinding.ActivityMainBinding
import java.io.File
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var vm: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var snackbar: Snackbar
    private lateinit var snackProgressBar: ProgressBar
    private var snackProgress = 5
    private var snackTimerStart = MAX_AUDIO_LENGTH-snackProgress

    private var timer: Timer? = null
//    private var soundRecorder: SoundRecorder? = null

    private var audioRecorder: AudioRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this).get(MainViewModel::class.java)

        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        binding.vm = vm

        setContentView(binding.root)

        snackbar = Snackbar.make(
            binding.root,
            "The recording will auto-stop in $snackProgress sec",
            Snackbar.LENGTH_INDEFINITE
        )
//        snackProgressBar = ProgressBar(this).apply {
//            max = 5
//            progress = 5
//            isIndeterminate = false
//        }

//        val snackText = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
//        (snackText.parent as ViewGroup).addView(snackProgressBar)

        binding.btnPlay.setOnClickListener {
            if(vm.isComplete.value == true) {
                Toast.makeText(this, "Please reset the timer to start again", Toast.LENGTH_SHORT).show()
            } else {
                when (vm.recordingState.value) {
                    RecordingState.IDLE -> {
                        proceedAfterPermission()
                    }
                    RecordingState.RECORDING -> {
                        // Make sure the audio is at least the minimum duration before stopping
                        if (vm.timerValue.value!! < MIN_AUDIO_LENGTH)
                            Snackbar.make(
                                binding.root,
                                "The audio duration must be at least 5 sec",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        else
                            stopRecording()
                    }
                    else -> { }
                }
            }
        }

        // When result button is clicked reset Timer and RecordingState back to IDLE
        binding.btnReset.setOnClickListener {
            vm.recordingState.value = RecordingState.IDLE
            vm.timerValue.value = 0L
        }

        // When API returns a result label or error, update the image (SMILE) accordingly
        vm.smile.observe(this) { drawable ->
            binding.smile.setImageDrawable(ContextCompat.getDrawable(this, drawable))
        }

        // Observe different states of Recording Phases and reflect accordingly
        vm.recordingState.observe(this) { state ->
            when (state) {
                RecordingState.IDLE -> { // When initialized to IDLE or from COMPLETE -> IDLE
                    binding.btnPlay.setImageDrawable(
                        ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_play
                        )
                    )
                }
                RecordingState.RECORDING -> { // While Recording
                    binding.btnPlay.setImageDrawable(
                        ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_stop
                        )
                    )
                }
                RecordingState.PLAYING -> {
                    // Just for debugging sake
                }
                RecordingState.COMPLETE -> {
                    // When recording is completed, perform prediction by sending the file to API
                    vm.predict(File(getFile()))
                }
                else -> {}
            }
        }
    }

    private fun getTask(): TimerTask {
        return object: TimerTask() {
            override fun run() {
                // Can't update value from background thread, so update the value on UI Thread
                runOnUiThread {
                    vm.timerValue.value = vm.timerValue.value!! + 1
                    val time = vm.timerValue.value!!

                    // Show a snackbar 5 sec before auto-stop
                    if(time == snackTimerStart) {
                        snackbar.show()
                    } else if(time in snackTimerStart..MAX_AUDIO_LENGTH) {
//                        snackProgressBar.progress = --snackProgress
                        --snackProgress
                        snackbar.setText("The recording will auto-stop in $snackProgress sec")
                    }

                    if(time == MAX_AUDIO_LENGTH) {
                        snackbar.dismiss()
                        stopRecording()
                        snackProgress = 5   // Rest value
                    }
                }
            }
        }
    }

    // Stop recording and reset time, change state RECORDING -> COMPLETE
    private fun stopRecording() {
//        soundRecorder?.stopRecording()
        audioRecorder?.stopRecording()
        // Reset TIMER
        timer?.cancel()
        timer = null
    }

//    private fun startRecording() {
//        if(soundRecorder == null) {
//            val filePath = getExternalFilesDir(null)?.absolutePath + "/recording.wav"
//            soundRecorder = SoundRecorder(filePath)
//        }
//        soundRecorder!!.startRecording()
//        vm.recordingState.value = RecordingState.RECORDING
//        // Start Timer
//        timer = Timer(true)
//        timer!!.scheduleAtFixedRate(task, 0L, 1000L)
//    }

    private fun startRecording() {
        if(audioRecorder == null) {
            val listener = object: OnAudioRecordedListener {
                override fun onComplete() {
                    runOnUiThread {
                        vm.recordingState.value = RecordingState.COMPLETE
                    }
                }

                override fun onError(ex: Exception?) {
                    // TODO: Update the error
                    runOnUiThread {
                        vm.recordingState.value = RecordingState.COMPLETE
                        ex?.printStackTrace()
                    }
                }

            }
            audioRecorder = AudioRecorder(getFile(), listener)
        }
        audioRecorder!!.startRecording()
        vm.recordingState.value = RecordingState.RECORDING
        // Start Timer
        timer = Timer(true)
        timer!!.scheduleAtFixedRate(getTask(), 0L, 1000L)
    }

    private fun getFile(): String {
        return getExternalFilesDir(null)?.absolutePath + "/recording.wav"
    }


    // make sure the appropriate permissions are granted
    private fun proceedAfterPermission() {
        if (!hasPermissions()) {
            requestPermissions()
        } else {
            startRecording()
        }
    }

    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
//                && ContextCompat.checkSelfPermission(this,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE
//        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE,
//            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, permissions, RC_RECORD_AUDIO)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == RC_RECORD_AUDIO && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        }
    }

    companion object {
        // Maximum length/limit of audio
        const val MAX_AUDIO_LENGTH = 20L
        const val MIN_AUDIO_LENGTH = 5L
        const val RC_RECORD_AUDIO = 1023
    }
}