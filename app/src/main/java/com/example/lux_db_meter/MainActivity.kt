package com.example.lux_db_meter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.log10
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

// admin@admin.com 123456

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var brightness: Sensor? = null
    private var luminosity: Sensor? = null
    private lateinit var text: TextView
    private lateinit var decibel: TextView

    private lateinit var startMeasuringButton: Button
    private var isRecording: Boolean = false
    private var luminosityEnabled: Boolean = false
    private var maxLuminosityLevel: Float = 0f

    private lateinit var audioRecord: AudioRecord
    private var bufferSize: Int = 0

    private val stopRecordingHandler = Handler()
    private val stopRecordingRunnable = Runnable {
        stopRecording()
    }

    private lateinit var loginButton: Button

    companion object {
        private const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val REQUEST_PERMISSION_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        text = findViewById(R.id.luminosityTextView)
        decibel = findViewById(R.id.decibelTextView)

        startMeasuringButton = findViewById(R.id.startMeasuringButton)
        startMeasuringButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        loginButton = findViewById(R.id.loginButton)

        loginButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        setUpSensorStuff()
        requestRecordAudioPermission()
    }

    private fun setUpSensorStuff() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        brightness = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        luminosity = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        luminosityEnabled = true
    }

    private fun requestRecordAudioPermission() {
        val permission = RECORD_AUDIO_PERMISSION
        val grant = ContextCompat.checkSelfPermission(this, permission)
        if (grant != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PERMISSION_CODE)
        }
    }

    private fun startRecording() {
        isRecording = true
        startMeasuringButton.text = "Stop Recording"
        decibel.text = "Decibels: Recording"
        maxLuminosityLevel = 0f

        // Start luminosity recording
        if (luminosityEnabled) {
            sensorManager.registerListener(this, luminosity, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Start audio recording
        val audioSource = MediaRecorder.AudioSource.MIC
        val sampleRateInHz = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            bufferSize
        )

        val outputFile = File(getExternalFilesDir(null), "temp_audio.pcm")

        audioRecord.startRecording()

        Thread {
            val buffer = ByteArray(bufferSize)
            outputFile.outputStream().use { outputStream ->
                while (isRecording) {
                    val readBytes = audioRecord.read(buffer, 0, bufferSize)
                    outputStream.write(buffer, 0, readBytes)
                }
            }
        }.start()

        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()

        // Schedule stop recording after 3 seconds
        stopRecordingHandler.postDelayed(stopRecordingRunnable, 3000)
    }

    private fun stopRecording() {
        isRecording = false
        startMeasuringButton.text = "Start Measuring"
        decibel.text = "Decibels: Not Recording"

        // Stop luminosity recording
        if (luminosityEnabled) {
            sensorManager.unregisterListener(this, luminosity)
        }

        audioRecord.stop()
        audioRecord.release()

        val outputFile = File(getExternalFilesDir(null), "temp_audio.pcm")
        val maxAmplitude = calculateMaxAmplitude(outputFile)
        val decibels = (20 * log10(maxAmplitude.toDouble())).toInt()
        text.text = "Luminosity: $maxLuminosityLevel lx"
        decibel.text = "Decibels: $decibels dB"

        outputFile.delete()
    }

    private fun calculateMaxAmplitude(file: File): Int {
        val data = file.readBytes()
        var maxAmplitude = 0
        for (i in 0 until data.size step 2) {
            val amplitude = Math.abs(data[i].toInt() or (data[i + 1].toInt() shl 8))
            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude
            }
        }
        return maxAmplitude
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val light = event.values[0]


            // Update maximum luminosity level
            if (light > maxLuminosityLevel) {
                maxLuminosityLevel = light
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Empty implementation
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, brightness, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }



}
