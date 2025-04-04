package com.example.photoeditorpro

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY, // Using the secured key
            generationConfig = generationConfig {
                temperature = 0.7f
                topP = 0.9f
                topK = 40
            }
        )
    }

    private lateinit var btnSend: Button
    private lateinit var etMessage: EditText
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Initialize views
        btnSend = findViewById(R.id.btnSend)
        etMessage = findViewById(R.id.etMessage)
        chatContainer = findViewById(R.id.chatContainer)
        chatScroll = findViewById(R.id.chatScroll)

        btnSend.setOnClickListener {
            val message = etMessage.text.toString()
            if (message.isNotEmpty()) {
                addMessageToChat("You: $message", false)
                etMessage.text.clear()
                getAiResponse(message)
            }
        }
    }

    private fun getAiResponse(query: String) {
        lifecycleScope.launch {
            try {
                // 1. Use the simpler query format
                val response = generativeModel.generateContent(query)

                // 2. Add proper response validation
                val responseText = response.text ?: throw Exception("Empty response from server")

                // 3. Clean the response before display
                val cleanResponse = responseText
                    .replace("**", "") // Remove markdown
                    .trim()

                addMessageToChat("AI: $cleanResponse", true)

            } catch (e: Exception) {
                // 4. Better error reporting
                val errorMsg = when {
                    e.message?.contains("deserialize") == true -> "API returned unexpected format"
                    else -> "Error: ${e.message?.take(100) ?: "Unknown error"}"
                }
                addMessageToChat(errorMsg, true)
                Log.e("GeminiAPI", "Request failed for: '$query'", e)
            }
        }
    }

    private fun addMessageToChat(message: String, isAi: Boolean) {
        val tv = TextView(this).apply {
            text = formatMessage(message)  // Add formatting
            setTextColor(if (isAi) Color.BLUE else Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isAi) Gravity.START else Gravity.END
                setMargins(16, 8, 16, 8)
            }
            textSize = 16f
        }
        chatContainer.addView(tv)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // Add this new function for formatting
    private fun formatMessage(rawText: String): CharSequence {
        return rawText
            .replace("**", "")  // Remove markdown bold
            .replace("* ", "• ") // Convert bullets
            .replace("\n", "\n   ") // Indent new lines
    }
}