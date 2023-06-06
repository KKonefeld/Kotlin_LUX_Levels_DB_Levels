package com.example.lux_db_meter

import android.os.Bundle
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
                            timestampTextView.text = "Timestamp: $date"

                            val decibelsTextView = cardView.findViewById<TextView>(R.id.decibelsTextView)
                            decibelsTextView.text = "Decibels: $decibels"

                            val luminosityTextView = cardView.findViewById<TextView>(R.id.luminosityTextView)
                            luminosityTextView.text = "Luminosity: $luminosity"

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
}
