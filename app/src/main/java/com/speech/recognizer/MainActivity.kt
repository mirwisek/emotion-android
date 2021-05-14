package com.speech.recognizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.speech.recognizer.databinding.ActivityMainBinding
import java.io.File
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var vm: MainViewModel

    private var timer: Timer? = null
    private var soundRecorder: SoundRecorder? = null

    private val task = object: TimerTask() {
        override fun run() {
            // Can't update value from background thread, so update the value on UI Thread
            runOnUiThread {
                vm.timerValue.value = vm.timerValue.value!! + 1
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this).get(MainViewModel::class.java)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        binding.vm = vm

        setContentView(binding.root)

        binding.btnPlay.setOnClickListener {
            if(vm.isComplete.value == true) {
                Toast.makeText(this, "Please reset the timer to start again", Toast.LENGTH_SHORT).show()
            } else {
                when (vm.recordingState.value) {
                    RecordingState.IDLE -> {
                        proceedAfterPermission()
                    }
                    RecordingState.RECORDING -> {
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
                    binding.btnPlay.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play))
                }
                RecordingState.RECORDING -> { // While Recording
                    binding.btnPlay.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_stop))
                }
                RecordingState.PLAYING -> {
                    // Just for debugging sake
                }
                RecordingState.COMPLETE -> {
                    // When recording is completed, perform prediction by sending the file to API
                    soundRecorder?.outputFile?.let { fname ->
                        vm.predict(File(fname))
                    }
                    log("The complete status ${soundRecorder?.outputFile}")
                }
                else -> {}
            }
        }


        vm.isRecording.observe(this) {

        }

    }

    // Stop recording and reset time, change state RECORDING -> COMPLETE
    private fun stopRecording() {
        soundRecorder?.stopRecording()
        // Reset TIMER
        vm.recordingState.value = RecordingState.COMPLETE
        timer?.cancel()
        timer = null
    }

    private fun startRecording() {
        if(soundRecorder == null) {
            val filePath = getExternalFilesDir(null)?.absolutePath + "/recording.wav"
            soundRecorder = SoundRecorder(filePath)
        }
        soundRecorder!!.startRecording()
        vm.recordingState.value = RecordingState.RECORDING
        // Start Timer
        timer = Timer(true)
        timer!!.scheduleAtFixedRate(task, 0L, 1000L)
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
        const val RC_RECORD_AUDIO = 1023
    }
}