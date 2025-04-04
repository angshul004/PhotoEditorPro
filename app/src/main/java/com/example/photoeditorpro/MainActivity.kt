package com.example.photoeditorpro

import com.example.photoeditorpro.ChatActivity
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {//chat
    private lateinit var btnSelectPhoto: Button
    private lateinit var btnAiChat: Button  // Add reference to the AI Chat button

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val intent = Intent(this, EditImageActivity::class.java)
            intent.putExtra("imageUri", it.toString())
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize buttons
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto)
        btnAiChat = findViewById(R.id.btnAiChat)  // Match the ID in your XML

        // Photo selection (existing code)
        btnSelectPhoto.setOnClickListener {
            pickImage.launch("image/*")
        }

        // AI Chat button (new code)
        btnAiChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }
}