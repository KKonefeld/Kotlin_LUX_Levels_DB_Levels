package com.example.lux_db_meter

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*

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
                    val historyDataTextView = findViewById<TextView>(R.id.historyDataTextView)
                    historyDataTextView.text = ""

                    // Iterate through timestamps
                    for (timestampSnapshot in dataSnapshot.children) {
                        val timestamp = timestampSnapshot.key // Get the timestamp

                        // Iterate through measurements under each timestamp
                        for (measurementSnapshot in timestampSnapshot.children) {
                            val decibels = measurementSnapshot.child("decibels").getValue(Int::class.java)
                            val luminosity = measurementSnapshot.child("luminosity").getValue(Float::class.java)

                            // Append measurement information to the TextView
                            val measurementInfo = "Timestamp: $timestamp\nDecibels: $decibels, Luminosity: $luminosity\n"
                            historyDataTextView.append(measurementInfo)
                        }
                    }
                }


                override fun onCancelled(databaseError: DatabaseError) {
                    // Handle database error
                    // ...
                }
            })
        }
    }
}
