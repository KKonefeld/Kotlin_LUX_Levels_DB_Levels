package com.example.lux_db_meter

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

// Declare the auth variable as a top-level variable
val auth: FirebaseAuth = FirebaseAuth.getInstance()
val database: DatabaseReference = FirebaseDatabase.getInstance().getReference()