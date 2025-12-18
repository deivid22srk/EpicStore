package com.epicstore.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class CallbackActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            data = intent?.data
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}
