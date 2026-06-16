package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.FirebaseSyncManager
import com.example.ui.EsportsApp
import com.example.ui.EsportsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase SDK
        FirebaseApp.initializeApp(this)

        // Initialize local Room Database caching replica
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "esports_database"
        )
        .fallbackToDestructiveMigration()
        .build()

        // Create the real-time continuous sync manager
        val syncManager = FirebaseSyncManager(
            context = applicationContext,
            dao = database.dao,
            scope = lifecycleScope
        )

        // Create ViewModel
        val viewModel = EsportsViewModel(
            context = applicationContext,
            dao = database.dao,
            syncManager = syncManager
        )

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                EsportsApp(viewModel = viewModel)
            }
        }
    }
}
