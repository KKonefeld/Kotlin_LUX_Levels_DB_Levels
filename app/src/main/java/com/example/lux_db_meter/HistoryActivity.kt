package com.example.lux_db_meter

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)




        val userId = auth.currentUser?.uid
        userId?.let {
            val measurementsRef = database.child("measurements").child(userId)
            measurementsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    // Clear existing views
                    val linearLayout = findViewById<LinearLayout>(R.id.linearLayout)
                    linearLayout.removeAllViews()

                    // Iterate through timestamps
                    for (timestampSnapshot in dataSnapshot.children) {
                        val timestamp = timestampSnapshot.key // Get the timestamp

                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(timestamp?.let { it1 -> Date(it1.toLong()) })
                        val cardSpacingPx = resources.dpToPx(16)
                        var isFirstEntry = true
                        // Iterate through measurements under each timestamp
                        for (measurementSnapshot in timestampSnapshot.children) {
                            val decibelsSnapshot = measurementSnapshot.child("decibels")
                            val decibels = decibelsSnapshot.getValue(Int::class.java)

                            val luminositySnapshot = measurementSnapshot.child("luminosity")
                            val luminosity = luminositySnapshot.getValue(Float::class.java)

                            // Create a new CardView for each measurement entry
                            val cardView =
                                layoutInflater.inflate(R.layout.card_measurement_entry, null) as CardView

                            // Update the child views within the CardView
                            val timestampTextView = cardView.findViewById<TextView>(R.id.timestampTextView)
                            timestampTextView.text = "Date: $date"

                            val decibelsTextView = cardView.findViewById<TextView>(R.id.decibelsTextView)
                            decibelsTextView.text = "Decibels: $decibels"

                            val luminosityTextView = cardView.findViewById<TextView>(R.id.luminosityTextView)
                            luminosityTextView.text = "Luminosity: $luminosity"

                            val shareButton = cardView.findViewById<Button>(R.id.shareButton)
                            shareButton.setOnClickListener {
                                shareMeasurement(luminosity, decibels, date)
                            }

                            // Set bottom margin for spacing
                            if (isFirstEntry) {
                                val layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                layoutParams.bottomMargin = cardSpacingPx
                                cardView.layoutParams = layoutParams
                            }
                            // Add the CardView to the LinearLayout
                            linearLayout.addView(cardView)


                        }
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Handle database error, e.g., display an error message
                    Toast.makeText(this@HistoryActivity, "Database Error: ${databaseError.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
    private fun shareMeasurement(luminosity: Float?, decibels: Int?, date: String) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Luminosity: $luminosity\nDecibels: $decibels\nDate: $date")



        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    fun Resources.dpToPx(dp: Int): Int {
        val density = displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onBackPressed() {
        startMainActivity()
        finish() // Finish the LoginActivity
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
}
