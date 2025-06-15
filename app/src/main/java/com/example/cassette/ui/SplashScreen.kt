package com.example.cassette.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.cassette.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSplashScreen()
        }
        super.onCreate(savedInstanceState)

        // Directly proceed to the main activity.
        // The HomeFragment will handle its own permission requests.
        proceedToMainActivity()
    }

    private fun proceedToMainActivity() {
        // A small delay can be kept for branding purposes if desired.
        CoroutineScope(Dispatchers.Main).launch {
            delay(1500) // A slightly shorter delay is often better for user experience.
            startActivity(Intent(this@SplashScreen, MainActivity::class.java))
            finish() // Ensure this activity is removed from the back stack.
        }
    }
}