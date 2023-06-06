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
import android.view.View
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
import com.example.lux_db_meter.auth

// admin@admin.com 123456

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var brightness: Sensor? = null
    private var luminosity: Sensor? = null
    private lateinit var text: TextView
    private lateinit var decibel: TextView

    private lateinit var startMeasuringButton: Button
    private lateinit var saveMeasuresButton: Button
    private var isRecording: Boolean = false
    private var luminosityEnabled: Boolean = false

    private var maxLuminosityLevel: Float = 0f
    private var decibels: Int? = null

    private lateinit var audioRecord: AudioRecord
    private var bufferSize: Int = 0

    private val stopRecordingHandler = Handler()
    private val stopRecordingRunnable = Runnable {
        stopRecording()
    }

    private lateinit var loginButton: Button
    private lateinit var historyDataButton: Button

    companion object {
        private const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val REQUEST_PERMISSION_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Views
        text = findViewById(R.id.luminosityTextView)
        decibel = findViewById(R.id.decibelTextView)

        // Buttons
        startMeasuringButton = findViewById(R.id.startMeasuringButton)
        loginButton = findViewById(R.id.loginButton)
        saveMeasuresButton = findViewById(R.id.saveMeasuresButton)
        historyDataButton = findViewById(R.id.historyDataButton)

        // Firebase Authentication
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                setLoggedInState()
            } else {
                setLoggedOutState()
            }
        }

        // Button Click Listeners
        startMeasuringButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        saveMeasuresButton.setOnClickListener {
            if (decibels != null && maxLuminosityLevel != null) {
                saveMeasurementsToDatabase(decibels!!, maxLuminosityLevel)
            } else {
                Toast.makeText(this, "Invalid data", Toast.LENGTH_SHORT).show()
            }
        }
        historyDataButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
            finish()
        }
        loginButton.setOnClickListener {
            if (auth.currentUser != null) {
                logout()
            } else {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        // Set up sensor and request permission
        setUpSensorStuff()
        requestRecordAudioPermission()
    }

    private fun setLoggedInState() {
        loginButton.text = "Log Out"
        historyDataButton.visibility = View.VISIBLE
        saveMeasuresButton.visibility = View.VISIBLE
    }

    private fun setLoggedOutState() {
        loginButton.text = "Log In"
        saveMeasuresButton.visibility = View.INVISIBLE
        historyDataButton.visibility = View.INVISIBLE
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
        decibels = (20 * log10(maxAmplitude.toDouble())).toInt()
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

    private fun logout() {
        auth.signOut()
        Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show()
        loginButton.text = "Log In"
    }
    private fun saveMeasurementsToDatabase(decibels: Int, maxLuminosityLevel: Float) {
        val user: FirebaseUser? = auth.currentUser
        user?.let {
            val userId = user.uid
            val measurementsRef = database.child("measurements").child(userId)

            val measurementId = measurementsRef.push().key // Generate a unique key for the measurement

            measurementId?.let {
                val measurementData = mutableMapOf<String, Any>()
                measurementData["decibels"] = decibels
                measurementData["luminosity"] = maxLuminosityLevel

                val timestamp = System.currentTimeMillis() // Get the current timestamp

                val measurementUpdates = mutableMapOf<String, Any>()
                measurementUpdates["$measurementId"] = measurementData

                measurementsRef.child(timestamp.toString()).updateChildren(measurementUpdates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Measurement saved successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Failed to save measurement: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }




}
