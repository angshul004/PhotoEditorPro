package com.example.photoeditorpro

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Stack
import kotlin.math.min

class EditImageActivity : AppCompatActivity() {

    // UI Components
    private lateinit var imageView: ImageView
    private lateinit var seekBarBrightness: SeekBar
    private lateinit var seekBarContrast: SeekBar
    private lateinit var seekBarSaturation: SeekBar
    private lateinit var btnRotate: Button
    private lateinit var btnCrop: Button
    private lateinit var btnSave: Button

    // Image handling
    private var isApplyingFilters = false

    private var originalBitmap: Bitmap? = null
    private var workingBitmap: Bitmap? = null
    private var baseWorkingBitmap: Bitmap? = null
    private val undoStack = Stack<Bitmap>()
    private var currentRotation = 0f


    // UCrop launcher - Updated version
    private val cropImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when {
            result.resultCode == Activity.RESULT_OK -> {
                val resultUri = UCrop.getOutput(result.data!!)
                resultUri?.let { uri ->
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            // Recycle previous bitmaps
                            baseWorkingBitmap?.recycle()
                            workingBitmap?.recycle()

                            // Load the new cropped image as our base
                            baseWorkingBitmap = BitmapFactory.decodeStream(stream)

                            // Verify and correct bitmap configuration
                            if (baseWorkingBitmap?.config != Bitmap.Config.ARGB_8888) {
                                baseWorkingBitmap = baseWorkingBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            }

                            // Create working copy
                            workingBitmap = baseWorkingBitmap?.copy(Bitmap.Config.ARGB_8888, true)

                            // Update UI
                            imageView.setImageBitmap(workingBitmap)

                            // Save to undo stack
                            workingBitmap?.let {
                                undoStack.push(it.copy(Bitmap.Config.ARGB_8888, false))
                                updateUndoButton()
                            }

                            // Reset filters to neutral
                            resetSliders()
                        }
                    } catch (e: Exception) {
                        Log.e("PhotoEditor", "Error loading cropped image", e)
                        Toast.makeText(this, "Error loading cropped image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            result.resultCode == UCrop.RESULT_ERROR -> {
                val cropError = UCrop.getError(result.data!!)
                Toast.makeText(this, "Crop failed: ${cropError?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_image)

        setupViews()
        loadImageFromIntent()
        setupListeners()
    }

    private fun setupViews() {
        imageView = findViewById(R.id.imageView)
        seekBarBrightness = findViewById(R.id.seekBarBrightness)
        seekBarContrast = findViewById(R.id.seekBarContrast)
        seekBarSaturation = findViewById(R.id.seekBarSaturation)
        btnRotate = findViewById(R.id.btnRotate)
        btnCrop = findViewById(R.id.btnCrop)
        btnSave = findViewById(R.id.btnSave)

        val btnUndo: Button = findViewById(R.id.btnUndo)
        btnUndo.setOnClickListener { undoLastEdit() }
    }
    private fun updateUndoButton() {
        findViewById<Button>(R.id.btnUndo).isEnabled = undoStack.size > 1
    }

    private fun undoLastEdit() {
        if (undoStack.size > 1) {
            workingBitmap?.recycle()
            undoStack.pop()  // Remove current state
            workingBitmap = undoStack.peek().copy(Bitmap.Config.ARGB_8888, true)
            imageView.setImageBitmap(workingBitmap)
            updateUndoButton()
            resetSliders()
        }
    }
    private fun resetSlidersToCurrent() {
        // Implement this based on how you want sliders to behave
        // This is complex for filters - you might just reset them:
        seekBarBrightness.progress = 100
        seekBarContrast.progress = 100
        seekBarSaturation.progress = 100
        currentRotation = 0f
    }

    private fun loadImageFromIntent() {
        intent.getStringExtra("imageUri")?.let { uriString ->
            try {
                val imageUri = Uri.parse(uriString)
                contentResolver.openInputStream(imageUri)?.use { stream ->
                    // Clear previous states
                    originalBitmap?.recycle()
                    workingBitmap?.recycle()
                    baseWorkingBitmap?.recycle()
                    undoStack.clear()

                    // Load new image
                    originalBitmap = BitmapFactory.decodeStream(stream)
                    baseWorkingBitmap = originalBitmap?.copy(Bitmap.Config.ARGB_8888, true)
                    workingBitmap = baseWorkingBitmap?.copy(Bitmap.Config.ARGB_8888, true)

                    imageView.setImageBitmap(workingBitmap)
                    resetControls()

                    // Initialize undo stack
                    workingBitmap?.let { undoStack.push(it.copy(Bitmap.Config.ARGB_8888, false)) }
                    updateUndoButton()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } ?: run {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        // Only calculate if image dimensions are valid
        if (height > 0 && width > 0) {
            // Calculate ratios of requested vs actual dimensions
            val heightRatio = if (reqHeight > 0) height.toFloat() / reqHeight.toFloat() else 1f
            val widthRatio = if (reqWidth > 0) width.toFloat() / reqWidth.toFloat() else 1f

            // Choose the smallest ratio as inSampleSize value
            val ratio = min(heightRatio, widthRatio)

            // Round to nearest power of 2
            inSampleSize = ratio.toInt().takeHighestOneBit()

            // Ensure we don't make the image smaller than requested
            if (inSampleSize < 1) {
                inSampleSize = 1
            }
        }

        return inSampleSize
    }

    private fun resetControls() {
        // For brightness (-100% to +100%)
        seekBarBrightness.apply {
            max = 200  // Allows values from 0-200
            progress = 100  // Neutral position (100 = no change)

            // For API 26+ we can set min, otherwise the range is 0-max
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                min = 0
            }
        }

        // For contrast (0% to 200%)
        seekBarContrast.apply {
            max = 200
            progress = 100
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                min = 0
            }
        }

        // For saturation (0% to 200%)
        seekBarSaturation.apply {
            max = 200
            progress = 100
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                min = 0
            }
        }

        currentRotation = 0f
        updateUndoButton()
    }


    private fun setupListeners() {
        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                applyFilters()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekBarBrightness.setOnSeekBarChangeListener(seekBarListener)
        seekBarContrast.setOnSeekBarChangeListener(seekBarListener)
        seekBarSaturation.setOnSeekBarChangeListener(seekBarListener)

        btnRotate.setOnClickListener {
            currentRotation += 90f
            if (currentRotation >= 360f) currentRotation = 0f
            applyFilters()
        }

        btnCrop.setOnClickListener {
            showCropOptions()
        }

        btnSave.setOnClickListener {
            saveImageToGallery()
        }
    }


    private fun applyFilters() {
        if (isApplyingFilters || baseWorkingBitmap == null) return

        isApplyingFilters = true

        try {
            // Always start from the base (cropped) bitmap
            val filteredBitmap = baseWorkingBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(filteredBitmap)
            val paint = Paint().apply {
                isAntiAlias = true
                isDither = true
                isFilterBitmap = true
            }

            // Get current values
            val brightness = (seekBarBrightness.progress - 100) / 100f
            val contrast = seekBarContrast.progress / 100f
            val saturation = seekBarSaturation.progress / 100f

            // Create and apply color matrix
            val colorMatrix = ColorMatrix().apply {
                setSaturation(saturation)
                postConcat(ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, 0f,
                    0f, contrast, 0f, 0f, 0f,
                    0f, 0f, contrast, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
                postConcat(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, brightness * 255f,
                    0f, 1f, 0f, 0f, brightness * 255f,
                    0f, 0f, 1f, 0f, brightness * 255f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }

            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(filteredBitmap, 0f, 0f, paint)

            // Apply rotation if needed
            if (currentRotation != 0f) {
                val matrix = Matrix().apply {
                    postRotate(currentRotation,
                        filteredBitmap.width / 2f,
                        filteredBitmap.height / 2f)
                }
                workingBitmap = Bitmap.createBitmap(
                    filteredBitmap, 0, 0,
                    filteredBitmap.width, filteredBitmap.height,
                    matrix, true
                )
            } else {
                workingBitmap = filteredBitmap
            }

            imageView.setImageBitmap(workingBitmap)

            // Save to undo stack if this is a new state
            if (undoStack.isEmpty() || !bitmapEquals(undoStack.peek(), workingBitmap)) {
                workingBitmap?.let { undoStack.push(it.copy(Bitmap.Config.ARGB_8888, false)) }
                updateUndoButton()
            }

        } catch (e: Exception) {
            Log.e("PhotoEditor", "Filter error", e)
        } finally {
            isApplyingFilters = false
        }
    }

    // Helper method to compare bitmaps
    private fun bitmapEquals(b1: Bitmap?, b2: Bitmap?): Boolean {
        if (b1 == null || b2 == null) return false
        if (b1.width != b2.width || b1.height != b2.height) return false
        return b1.sameAs(b2)
    }


    private fun resetSliders() {
        seekBarBrightness.progress = 100
        seekBarContrast.progress = 100
        seekBarSaturation.progress = 100
        currentRotation = 0f
    }
    private fun canApplyFilters(): Boolean {
        return workingBitmap != null &&
                !workingBitmap!!.isRecycled &&
                Runtime.getRuntime().freeMemory() > workingBitmap!!.byteCount * 3
    }

    private fun showCropOptions() {
        val options = arrayOf("Free", "1:1 (Square)", "4:3", "16:9")
        AlertDialog.Builder(this)
            .setTitle("Select Crop Ratio")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startCropping(null) // Free ratio
                    1 -> startCropping(1f to 1f) // Square
                    2 -> startCropping(4f to 3f) // 4:3
                    3 -> startCropping(16f to 9f) // 16:9
                }
            }
            .show()
    }

    private fun startCropping(ratio: Pair<Float, Float>?) {
        workingBitmap?.let { bitmap ->
            try {
                // Create temp file for cropping
                val tempFile = File(cacheDir, "temp_crop_source.jpg").apply {
                    parentFile?.mkdirs()  // Ensure directory exists
                    outputStream().use { stream ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                            throw IOException("Failed to save bitmap for cropping")
                        }
                    }
                }

                val destinationUri = Uri.fromFile(File(cacheDir, "temp_crop_result.jpg"))

                // Create UCrop options
                val options = UCrop.Options().apply {
                    setToolbarTitle("Crop Image")
                    setHideBottomControls(false)
                    setFreeStyleCropEnabled(ratio == null)
                    setToolbarColor(ContextCompat.getColor(this@EditImageActivity, R.color.purple_500))
                    setStatusBarColor(ContextCompat.getColor(this@EditImageActivity, R.color.purple_700))
                    setToolbarWidgetColor(ContextCompat.getColor(this@EditImageActivity, R.color.white))
                }

                // Build UCrop intent
                val uCrop = UCrop.of(Uri.fromFile(tempFile), destinationUri)
                    .withOptions(options)
                    .withMaxResultSize(1920, 1080)

                ratio?.let { (first, second) ->
                    uCrop.withAspectRatio(first, second)
                }

                // Start UCrop
                cropImageLauncher.launch(uCrop.getIntent(this))

            } catch (e: Exception) {
                Log.e("PhotoEditor", "Error preparing crop", e)
                runOnUiThread {
                    Toast.makeText(this, "Error preparing image for cropping", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Toast.makeText(this, "No image available to crop", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToGallery() {
        workingBitmap?.let { bitmap ->
            // Generate unique filename with timestamp
            val timestamp = System.currentTimeMillis()
            val filename = "PhotoEditorPro_$timestamp.jpg"

            // Set up content values for MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

                // For Android 10+ we need to specify relative path
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            // Insert the image into MediaStore
            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val resolver = contentResolver
            var outputStream: OutputStream? = null
            var imageUri: Uri? = null

            try {
                imageUri = resolver.insert(contentUri, contentValues)

                if (imageUri == null) {
                    throw IOException("Failed to create new MediaStore record")
                }

                outputStream = resolver.openOutputStream(imageUri)

                if (outputStream == null) {
                    throw IOException("Failed to get output stream")
                }

                // Compress and save the bitmap
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                    throw IOException("Failed to save bitmap")
                }

                // For Android 10+, mark as not pending
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }

                // Show success message
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Image saved to Pictures folder",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                // Clean up if failed
                imageUri?.let { uri ->
                    resolver.delete(uri, null, null)
                }

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Failed to save image: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } finally {
                outputStream?.close()
            }

        } ?: run {
            Toast.makeText(
                this,
                "No image available to save",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun validateBitmap(bitmap: Bitmap?): Boolean {
        return bitmap != null &&
                !bitmap.isRecycled &&
                bitmap.width > 0 &&
                bitmap.height > 0
    }
    override fun onStop() {
        super.onStop()
        // Only keep original bitmap in memory
        workingBitmap?.recycle()
        workingBitmap = null
    }

    override fun onStart() {
        super.onStart()
        if (workingBitmap == null && originalBitmap != null) {
            workingBitmap = originalBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
            imageView.setImageBitmap(workingBitmap)
        }
    }
    override fun onResume() {
        super.onResume()
        // Ensure we have a valid bitmap if activity resumes
        if (workingBitmap == null && originalBitmap != null) {
            workingBitmap = originalBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
            imageView.setImageBitmap(workingBitmap)
        }
    }

    private fun validateViewDimensions(): Boolean {
        return imageView.width > 0 && imageView.height > 0
    }
    override fun onDestroy() {
        super.onDestroy()
        originalBitmap?.recycle()
        workingBitmap?.recycle()
        undoStack.forEach { it.recycle() }
        undoStack.clear()
    }
}