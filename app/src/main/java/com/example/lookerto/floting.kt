package com.example.lookerto




import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaPlayer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.lookerto.databinding.ActivityFlotingBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.max
import kotlin.math.min
import android.os.Parcelable
import java.io.Serializable


class floting : AppCompatActivity(), View.OnTouchListener {
    lateinit var binding: ActivityFlotingBinding

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var excelPickerLauncher: ActivityResultLauncher<Intent>
    //private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjectionIntent: Intent? = null
    //private var mediaProjectionResultCode: Int =  Activity.RESULT_CANCELED
    private var hasPermissions = false
    private lateinit var windowManager: WindowManager
    //private lateinit var handler: Handler
    var selectedPhotoUris = ArrayList<Uri>()

    private val TAG = "FlotingActivty "
    private val TAG2 = "RunFun  In "
    //var areaSelectionOverlay = AreaSelectionOverlay()

    private lateinit var floatingView: View
    private var initialX = 0
    private var initialY = 0
    private var sizer = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var backgroundHandler: Handler
    private lateinit var handlerThread: HandlerThread
    private var selectedExcelFile: File? = null
    private var isPaused = false
    private var isStopped = false
    private var cumulativeDelay: Long = 0L
    private var cumulativeCountDecrement: Int = 0

    private var job: Job? = null
    private var comparisonCount = 0
    private var currentCount = 0

    private val SCREEN_CAPTURE_REQUEST_CODE = 1001
    //private val REQUEST_CODE_SCREEN_CAPTURE = 1001
    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 1
        const val CAPTURE_SCREENSHOT_REQUEST = 1
       //const val SCREEN_RECORD_REQUEST_CODE = 1000


        private val SCREEN_RECORD_REQUEST_CODE = 1000
        private const val PERMISSION_REQ_POST_NOTIFICATIONS = 1001
        private const val PERMISSION_REQ_ID_RECORD_AUDIO = 1002
        private const val PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = 1003

        private const val REQUEST_CODE = 1001

    }


    private var galleryImageUris = mutableListOf<Uri>()
    private var selectedImageFiles = mutableListOf<File>()
    private lateinit var mediaPlayer: MediaPlayer
    private var isRunning = false


    private val pauseMutex = Mutex()

    @RequiresApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       // setContentView(R.layout.activity_floting)
        //ActivityReferenceHolder.setActivity(this)
        binding = ActivityFlotingBinding.inflate(layoutInflater)
        floatingView = binding.root
        ActivityReferenceHolder.setActivity(this)


        //val receiver = ScreenCaptureReceiver()



        //registerReceiver(receiver, filter)


        mediaPlayer = MediaPlayer.create(
            this,
            R.raw.alarm
        ) // replace 'alarm_sound' with your actual sound file in res/raw
        mediaPlayer.setOnCompletionListener {
            it.seekTo(0)
        }

       // handler = Handler(Looper.getMainLooper())
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        floatingView.setOnTouchListener(this)


        //startScreenCapture()

        // windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        val savedImageUris = loadImageUrisFromPreferences()
        if (savedImageUris.isNotEmpty()) {
            galleryImageUris.addAll(savedImageUris)
            savedImageUris.forEach { uri ->
                setSelectedImageFileFromUri(uri)
            }
            binding.tvImg.text = "You selected ${savedImageUris.size} photo(s)"
        } else {
            requestStoragePermission()
        }


        // Initialize the launchers
        imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.let { intent ->
                        handleImagePickerResult(intent)
                    }
                }
            }

        loadSquareValues(binding)

        binding.saveB.setOnClickListener {

            saveSquareValues(binding)
        }
        binding.rooWidth.setOnFocusChangeListener { _, hasFocus -> updateWindowParams(hasFocus) }
        binding.ediHeigAria.setOnFocusChangeListener { _, hasFocus -> updateWindowParams(hasFocus) }
        binding.etCountManually.setOnFocusChangeListener { _, hasFocus ->
            updateWindowParams(
                hasFocus
            )
        }
        binding.editTextLeft.setOnFocusChangeListener { _, hasFocus -> updateWindowParams(hasFocus) }
        binding.editTextRight.setOnFocusChangeListener { _, hasFocus -> updateWindowParams(hasFocus) }
        binding.editTextUp.setOnFocusChangeListener { _, hasFocus -> updateWindowParams(hasFocus) }
        binding.editTextDawn.setOnFocusChangeListener { _, hasFocus -> updateWindowParams(hasFocus) }
        binding.etDelay.setOnFocusChangeListener { _, hasFocus -> updateWindowParams(hasFocus) }
        binding.etCountA.setOnFocusChangeListener { _, hasFocus -> updateWindowParams(hasFocus) }

        val EdTTT = binding.rooWidth.text.toString()
        val EdAria = binding.ediHeigAria.text.toString()
        val RNumber = binding.etCountManually.text.toString()
        val leftValue = binding.editTextLeft.text.toString()
        val rightValue = binding.editTextRight.text.toString()
        val upValue = binding.editTextUp.text.toString()
        val downValue = binding.editTextDawn.text.toString()
        val delay = binding.etDelay.text.toString()
        val countA = binding.etCountA.text.toString()

        // Remove focus when clicking on any space
        binding.applyAdjustmentsButton.setOnClickListener {

            binding.rooWidth.clearFocus()
            binding.ediHeigAria.clearFocus()
            binding.etCountManually.clearFocus()
            binding.editTextLeft.clearFocus()
            binding.editTextRight.clearFocus()
            binding.editTextUp.clearFocus()
            binding.editTextDawn.clearFocus()
            binding.etDelay.clearFocus()
            binding.etCountA.clearFocus()
            Log.d(TAG, "Success applyAdjustmentsButton is clicked ")
            //            // Example directions
            val chosenDirections = listOf(Direction.RIGHT, Direction.UP)

            // Get display metrics
            val metrics = resources.displayMetrics

            // Calculate adjustments based on chosen directions and display metrics
            val adjustments = calculateAdjustments(chosenDirections, metrics)

            // Use the adjustments as needed
            Log.d(TAG, "The  X: ${adjustments.first}, And The  Y: ${adjustments.second}")

            setAriaHeightFromInput()
            transferEditTextToTextView(binding.etCountA, binding.roundNumber)
        }

        // Optionally, call updateDirectionDisplay() to show the initial direction when the activity starts
        updateDirectionDisplay()

        //GetSquare frome Hear
        binding.getSquareSize.setOnClickListener {
            binding.areaSelctor.getSquareToTextViewValues(binding)
        }

        binding.resetSquare.setOnClickListener {

            if (isValidFloat(binding.sqSize.text.toString()) && isValidFloat(binding.posX.text.toString()) && isValidFloat(
                    binding.posY.text.toString()
                )
            ) {
                binding.areaSelctor.resetSquareToTextViewValues2(binding)

                Toast.makeText(this, "Values reset successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show()
            }

        }
        // start from hear
        binding.areaSelctor.onAreaSelectedListener = { selectedArea ->
            // Handle the selected area here
            // For example, store it for later use in screenshot capture
            // or update UI elements accordingly
            initialTouchX = selectedArea.centerX().toFloat()
            initialTouchY = selectedArea.centerY().toFloat()
            sizer = selectedArea.width().toFloat() // Assuming width == height for the selected area
        }


        // isAreaSelectorVisible
        var isAreaSelectorVisible = false
        binding.selectAria.setOnClickListener {

            if (isAreaSelectorVisible) {
                // If areaSelector is visible, hide it
                binding.areaSelctor.visibility = View.INVISIBLE

                binding.areaSelctor.resetSquarePosition()

            } else {
                // If areaSelector is not visible, show it
                binding.areaSelctor.visibility = View.VISIBLE

                // Optionally: selectAreaButton.visibility = View.GONE
            }
            isAreaSelectorVisible = !isAreaSelectorVisible
        }

        binding.selectImg.setOnClickListener {
            requestStoragePermission()

        }

        binding.One.setOnClickListener {
            startSingleComparison()
        }

        //do you think if i try another one from ORB, BRIEF, BRISK, or AKAZE. the freeze is gone ?





        binding.screeshot.setOnClickListener {

//
            startScreenCapture()

        }
    }



    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjectionIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(mediaProjectionIntent, REQUEST_CODE)
    }



//step edit the startactvtyforRusalut than edit the captureScreenshot function ... and

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQ_POST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO)
                } else {
                    hasPermissions = false
                    showLongToast("No permission for ${Manifest.permission.POST_NOTIFICATIONS}")
                }
            }
            PERMISSION_REQ_ID_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE)
                } else {
                    hasPermissions = false
                    showLongToast("No permission for ${Manifest.permission.RECORD_AUDIO}")
                }
            }
            PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    hasPermissions = true
                   // startScreenCapture()
                } else {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        hasPermissions = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                          //  startScreenCapture()
                        }
                    } else {
                        hasPermissions = false
                        showLongToast("No permission for ${Manifest.permission.WRITE_EXTERNAL_STORAGE}")
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Get the position and size of the overlay in your floating activity
            val overlayLocation = IntArray(2)
            binding.areaSelctor.getLocationOnScreen(overlayLocation)
            val overlayX = overlayLocation[0]
            val overlayY = overlayLocation[1]

            val (newX, newY, newSquareSize) = binding.areaSelctor.resetSquareToTextViewValues(binding)
            val chosenDirections = getCurrentDirections()

            // Start the service with the screen capture intent and necessary extras
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("mediaProjectionIntent", data)
                putExtra("mediaProjectionResultCode", resultCode)
                putExtra("x", newX)
                putExtra("y", newY)
                putExtra("squareSize", newSquareSize)
                putExtra("parentX", overlayX)
                putExtra("parentY", overlayY)
                putExtra("chosenDirections", (chosenDirections))
            }
            startService(serviceIntent)
        }
    }





//    private fun requestScreenCapturePermission() {
//        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
//    }



    private fun startScreenCaptureService() {
       // val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("mediaProjectionIntent", mediaProjectionIntent)
           // putExtra("mediaProjectionResultCode", mediaProjectionResultCode)
            //putExtra("mediaProjectionManager", mediaProjectionManager)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }



    private fun stopScreenCaptureService() {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        stopService(serviceIntent)
    }

    private fun showLongToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun checkSelfPermission(permission: String, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            return false
        }
        return true
    }

    // Function to capture square screenshot
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun captureScreenshot3() {
        val overlayLocation = IntArray(2)
        binding.areaSelctor.getLocationOnScreen(overlayLocation)
        val overlayX = overlayLocation[0]
        val overlayY = overlayLocation[1]

        val (newX, newY, newSquareSize) =  binding.areaSelctor.resetSquareToTextViewValues(binding)

        // Get the current chosen directions
        val chosenDirections = getCurrentDirections()

        captureSquareScreenshot(
            newX,
            newY,
            newSquareSize,
            overlayX,
            overlayY,
            { screenshotBitmap ->
                if (screenshotBitmap != null) {
                    saveBitmapToFile(screenshotBitmap) { savedFile ->
                        runOnUiThread {
                            if (savedFile != null) {
                                Log.e(TAG, "Screenshot saved to: ${savedFile.absolutePath}")
                                Log.d(TAG, "Floting fun captureScreenshot newx is  ${newX} and newY is${newY} and  newSquareSize is ${newSquareSize} and overlayX is  ${overlayX} and overlayY is  ${overlayY} ")
                                Toast.makeText(this, "Screenshot saved", Toast.LENGTH_SHORT).show()

                            } else {
                                Log.e(TAG, "Failed to save screenshot")
                                Toast.makeText(this, "Failed to save screenshot", Toast.LENGTH_SHORT).show()

                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        Log.e(TAG, "Failed to capture screenshot")

                    }
                }
            },
            chosenDirections.toList()
        )
    }


    fun captureSquareScreenshot(
        x: Float,
        y: Float,
        squareSize: Float,
        parentX: Int,
        parentY: Int,
        onComplete: (Bitmap?) -> Unit,
        chosenDirections: List<Direction>
    ) {
        val metrics: DisplayMetrics
        val density: Int
        val displayWidth: Int
        val displayHeight: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            displayWidth = bounds.width()
            displayHeight = bounds.height()
            density = resources.displayMetrics.densityDpi
        } else {
            metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            density = metrics.densityDpi
            displayWidth = metrics.widthPixels
            displayHeight = metrics.heightPixels
        }

        val captureX = (parentX + x - squareSize / 2).toInt()
        val captureY = (parentY + y - squareSize / 2).toInt()

        val (totalAdjustX, totalAdjustY) = calculateAdjustments(chosenDirections, resources.displayMetrics)

        val captureWidth = squareSize.toInt()
        val captureHeight = squareSize.toInt()

        val imageReader = ImageReader.newInstance(
            displayWidth,
            displayHeight,
            PixelFormat.RGBA_8888,
            2
        )

        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            displayWidth,
            displayHeight,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )

        val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
            val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
            var capturedBitmap: Bitmap? = null

            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * displayWidth

                val fullBitmap = Bitmap.createBitmap(
                    displayWidth + rowPadding / pixelStride,
                    displayHeight,
                    Bitmap.Config.ARGB_8888
                )
                fullBitmap.copyPixelsFromBuffer(buffer)

                // Adjust capture coordinates
                val validX = max(0, captureX)
                val validY = max(0, captureY)

                val captureStartX = max(validX - totalAdjustX, 0)
                val captureStartY = max(validY - totalAdjustY, 0)
                val captureEndX = min(validX + captureWidth - totalAdjustX, fullBitmap.width)
                val captureEndY = min(validY + captureHeight - totalAdjustY, fullBitmap.height)

                capturedBitmap = Bitmap.createBitmap(
                    fullBitmap,
                    captureStartX,
                    captureStartY,
                    captureEndX - captureStartX,
                    captureEndY - captureStartY
                )

                fullBitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing screenshot", e)
            } finally {
                image.close()
                onComplete(capturedBitmap)
                imageReader.setOnImageAvailableListener(null, null)
            }
        }
        imageReader.setOnImageAvailableListener(onImageAvailableListener, null)
    }
//End captureScreenshot

    private fun calculateAdjustments(
        chosenDirections: List<Direction>,
        metrics: DisplayMetrics
    ): Pair<Int, Int> {
        val adjustLeft = getAdjustmentValue(binding.editTextLeft, metrics)
        val adjustRight = getAdjustmentValue(binding.editTextRight, metrics)
        val adjustUp = getAdjustmentValue(binding.editTextUp, metrics)
        val adjustDown = getAdjustmentValue(binding.editTextDawn, metrics)

        // Calculate total adjustments
        val totalAdjustX = (if (chosenDirections.contains(Direction.LEFT)) adjustLeft else 0) -
                (if (chosenDirections.contains(Direction.RIGHT)) adjustRight else 0)

        val totalAdjustY = (if (chosenDirections.contains(Direction.UP)) adjustUp else 0) -
                (if (chosenDirections.contains(Direction.DOWN)) adjustDown else 0)

        return Pair(totalAdjustX, totalAdjustY)
    }

    // Function to get adjustment value from EditText and convert to pixels
    private fun getAdjustmentValue(editText: EditText, metrics: DisplayMetrics): Int {
        val value = editText.text.toString().toIntOrNull() ?: 0
        return value.dpToPx(metrics)
    }

    // Extension function to convert dp to pixels
    private fun Int.dpToPx(metrics: DisplayMetrics): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), metrics).toInt()
    }

    // Enum class for directions
    enum class Direction {
        LEFT, RIGHT, UP, DOWN
    }

    // Function to save the bitmap to a file
    internal fun saveBitmapToFile(bitmap: Bitmap, onComplete: (File?) -> Unit) {
        if (bitmap == null) return
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imagesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val imageFile = File(imagesDir, "Screenshot_$timeStamp.png")

        try {
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                onComplete(imageFile)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save screenshot", e)
            onComplete(null)
        }
    }


    private val directionPairs = arrayOf(
        arrayOf(Direction.RIGHT, Direction.UP),  // Item 0
        arrayOf(Direction.LEFT, Direction.DOWN)     // Item 1
    )
    private var currentDirectionIndex = 0  // Start with the first direction pair

    private fun toggleDirections() {
        // Toggle between the two direction pairs
        currentDirectionIndex = (currentDirectionIndex + 1) % directionPairs.size
        updateDirectionDisplay()  // Update the display with the new directions
    }

    fun getCurrentDirections(): Array<Direction> {
        return directionPairs[currentDirectionIndex]
    }

    private fun updateDirectionDisplay() {
        val directions = getCurrentDirections()
        // binding.directionDisplayTextView.text = "Current Directions: ${directions.joinToString(", ") { it.name }}"
        Toast.makeText(this, "Current Directions: ${directions.joinToString(", ") { it.name }}", Toast.LENGTH_SHORT).show()
    }

    // Function to calculate adjustments based on directions and metrics




    private fun launchImagePicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            imagePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "launchImagePicker", e)
        }
    }

    private fun handleImagePickerResult(intent: Intent) {
        galleryImageUris.clear()
        selectedImageFiles.clear()

        intent.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                val imageUri = clipData.getItemAt(i).uri
                galleryImageUris.add(imageUri)
                setSelectedImageFileFromUri(imageUri)
            }
            binding.tvImg.text = "You selected ${clipData.itemCount} photo(s)"
        } ?: run {
            // Single image selected
            intent.data?.let { imageUri ->
                galleryImageUris.add(imageUri)
                setSelectedImageFileFromUri(imageUri)
                binding.tvImg.text = "You selected 1 photo"
            }
        }
    }

    fun isValidFloat(text: String): Boolean {
        return try {
            text.toFloat()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }


    private fun startSingleComparison() {
        // Get the latest value from roundNumber
        val countInput = binding.roundNumber.text.toString()
        val initialCount = countInput.toIntOrNull()

        if (initialCount == null) {
            Toast.makeText(this, "Invalid count value", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedImageFiles.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "selectedImageFiles is Empty")
            return
        }

        if (selectedExcelFile == null) {
            Toast.makeText(this, "Please select an Excel file", Toast.LENGTH_SHORT).show()
            return
        }

        // Calculate the adjusted count
        val adjustedCount = initialCount - cumulativeCountDecrement

        // Retrieve the user input value for delay
        val delayInput = binding.etDelay.text.toString()
        val delaySeconds = delayInput.toLongOrNull() ?: 0L

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentBitmap = captureCurrentAreaSuspend()
                if (currentBitmap != null) {
                    val matchedFiles = compareWithSavedScreenshots(currentBitmap)
                    withContext(Dispatchers.Main) {
                        if (matchedFiles.isNotEmpty()) {
                            val matchedNames = matchedFiles.joinToString(", ") { it.name.substringBeforeLast(".") }
                            binding.roundImg.text = matchedNames

                            Log.i(TAG2, "Successfully Match No: $matchedNames")
                            Log.w(TAG2, "Comparison Count: $adjustedCount")

                            // Calculate the date and time with the cumulative delay
                            cumulativeDelay += delaySeconds
                            val currentDateTime = LocalDateTime.now().minusSeconds(cumulativeDelay)
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            val formattedDateTime = currentDateTime.format(formatter)
                            Log.d(TAG2, "Current Date and Time (adjusted): $formattedDateTime")
                            changeTextViewBackgroundColor(binding.roundNumber, Color.WHITE) // Change yourTextView to your actual TextView ID

                            binding.roundDate.text = formattedDateTime

                            // Increment the cumulative count decrement only on successful match
                            cumulativeCountDecrement++
                        } else {
                            binding.roundImg.text = "Error: No match found"
                            Log.e(TAG2, "Error: No match found")
                            playAlarmSound()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.roundImg.text = "Error: Failed to capture current area"
                        Log.e(TAG2, "Error: Failed to capture current area")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@floting, "An error occurred: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Error in startSingleComparison", e)
                }
            }
        }
    }

    private fun playAlarmSound() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
        }
    }


    private fun loadImageUrisFromPreferences(): List<Uri> {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val uriStrings = sharedPreferences.getStringSet("image_uris", emptySet())
        return uriStrings?.map { Uri.parse(it) } ?: emptyList()
    }

    private suspend fun captureCurrentAreaSuspend(): Bitmap? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Bitmap?> { continuation ->
            val overlayLocation = IntArray(2)
            binding.areaSelctor.getLocationOnScreen(overlayLocation)
            val overlayX = overlayLocation[0]
            val overlayY = overlayLocation[1]

            val (newX, newY, newSquareSize) = binding.areaSelctor.resetSquareToTextViewValues(binding)

            val chosenDirections = mutableListOf<Direction>()
            chosenDirections.add(Direction.RIGHT) //9
            chosenDirections.add(Direction.UP) //36

            Log.d(TAG, "fun captureCurrentArea newx is $newX and newY is $newY and newSquareSize is $newSquareSize and overlayX is $overlayX and overlayY is $overlayY")
        }
    }

    private suspend fun compareWithSavedScreenshots(currentBitmap: Bitmap): List<File> = withContext(Dispatchers.Default) {
        val matchedFiles = mutableListOf<File>()
        try {
            val currentMat = BitmapToMat(currentBitmap)
            for (file in selectedImageFiles) {
                val savedBitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (savedBitmap == null) {
                    Log.e(TAG, "Failed to decode bitmap from file: ${file.absolutePath}")
                    continue
                }
                val savedMat = BitmapToMat(savedBitmap)
                val matchScore = compareImages2(savedMat, currentMat)
                Log.d(TAG, "Comparing ${file.name} with match score: $matchScore")
                if (matchScore > 0.8) {
                    matchedFiles.add(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing screenshots", e)
        }
        matchedFiles
    }


    private fun BitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, mat)
        Log.d(TAG, "Bitmap converted to Mat")
        return mat
    }

    fun compareImages2(galleryImage: Mat, screenshot: Mat): Double {
        return try {
            // Convert images to grayscale
            val grayGalleryImage = Mat()
            val grayScreenshot = Mat()
            Imgproc.cvtColor(galleryImage, grayGalleryImage, Imgproc.COLOR_BGR2GRAY)
            Imgproc.cvtColor(screenshot, grayScreenshot, Imgproc.COLOR_BGR2GRAY)
            Log.d(TAG, "Images converted to grayscale")

            // Resize images to a standard size (e.g., 500x500)
            val resizedGalleryImage = Mat()
            val resizedScreenshot = Mat()
            Imgproc.resize(grayGalleryImage, resizedGalleryImage, Size(500.0, 500.0))
            Imgproc.resize(grayScreenshot, resizedScreenshot, Size(500.0, 500.0))
            Log.d(TAG, "Images resized to 500x500")

            // Create FAST detector and ORB extractor
            val fast = FastFeatureDetector.create()
            val orb = ORB.create()
            val descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

            // Detect keypoints using FAST
            val keypoints1 = MatOfKeyPoint()
            val keypoints2 = MatOfKeyPoint()
            fast.detect(resizedGalleryImage, keypoints1)
            fast.detect(resizedScreenshot, keypoints2)
            Log.d(TAG, "FAST detected ${keypoints1.size().height.toInt()} keypoints in gallery image and ${keypoints2.size().height.toInt()} keypoints in screenshot")

            // Compute descriptors using ORB
            val descriptors1 = Mat()
            val descriptors2 = Mat()
            orb.compute(resizedGalleryImage, keypoints1, descriptors1)
            orb.compute(resizedScreenshot, keypoints2, descriptors2)
            Log.d(TAG, "ORB descriptors computed")

            // Print descriptor information
            Log.d(TAG, "Descriptors1: type=${descriptors1.type()} size=${descriptors1.size()}")
            Log.d(TAG, "Descriptors2: type=${descriptors2.type()} size=${descriptors2.size()}")

            // Check if descriptors are empty
            if (descriptors1.empty() || descriptors2.empty()) {
                Log.e(TAG, "One of the descriptors is empty")
                return -1.0
            }

            // Match descriptors using BRUTEFORCE_HAMMING
            val matches = MatOfDMatch()
            descriptorMatcher.match(descriptors1, descriptors2, matches)
            Log.d(TAG, "Descriptors matched")

            // Filter good matches
            val goodMatchesList = matches.toList().filter { it.distance < 30.0 }
            val matchScore = goodMatchesList.size.toDouble() / matches.rows()
            Log.d(TAG, "Match score: $matchScore")

            matchScore
        } catch (e: Exception) {
            Log.e(TAG, "Error in compareImages2", e)
            -1.0
        }
    }

    private fun requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION)
        } else {
            launchImagePicker()
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        val contentResolver = contentResolver

        // Check if the URI is a document URI
        if (DocumentsContract.isDocumentUri(this, uri)) {
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":").toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return "${Environment.getExternalStorageDirectory()}/${split[1]}"
                    }
                }
                "com.android.providers.downloads.documents" -> {
                    val id = DocumentsContract.getDocumentId(uri)
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), id.toLong()
                    )
                    return getDataColumn(contentUri, null, null)
                }
                "com.android.providers.media.documents" -> {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":").toTypedArray()
                    val contentUri: Uri = when (split[0]) {
                        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> return null
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(split[1])
                    return getDataColumn(contentUri, selection, selectionArgs)
                }
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            // If the URI is a content URI
            return getDataColumn(uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            // If the URI is a file URI
            return uri.path
        }
        return null
    }

    private fun getDataColumn(uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return cursor.getString(columnIndex)
            }
        }
        return null
    }


    private fun setSelectedImageFileFromUri(uri: Uri) {
        val filePath = getFilePathFromUri(uri)
        if (filePath != null) {
            selectedImageFiles.add(File(filePath))
            Toast.makeText(this, "Image selected: ${selectedImageFiles.last().name}", Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG, "Failed to get file path from URI: $uri")
        }
    }

    private fun saveSquareValues(binding: ActivityFlotingBinding) {
        val sharedPreferences = getSharedPreferences("SquareValues", AppCompatActivity.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        editor.putFloat("posX", binding.posX.text.toString().toFloat())
        editor.putFloat("posY", binding.posY.text.toString().toFloat())
        editor.putFloat("sqSize", binding.sqSize.text.toString().toFloat())

        editor.putString("rightDirection", binding.editTextRight.text.toString())
        editor.putString("upDirection", binding.editTextUp.text.toString())
        editor.putString("leftDirection", binding.editTextLeft.text.toString())
        editor.putString("downDirection", binding.editTextDawn.text.toString())

        editor.apply()

        Toast.makeText(this, "Values saved", Toast.LENGTH_SHORT).show()
    }

    private fun loadSquareValues(binding: ActivityFlotingBinding) {
        val sharedPreferences = getSharedPreferences("SquareValues", AppCompatActivity.MODE_PRIVATE)

        val posX = sharedPreferences.getFloat("posX", 0f)
        val posY = sharedPreferences.getFloat("posY", 0f)
        val sqSize = sharedPreferences.getFloat("sqSize", 0f)

        val rightDirection = sharedPreferences.getString("rightDirection", "")
        val upDirection = sharedPreferences.getString("upDirection", "")
        val leftDirection = sharedPreferences.getString("leftDirection", "")
        val downDirection = sharedPreferences.getString("downDirection", "")

        binding.posX.text = posX.toString()
        binding.posY.text = posY.toString()
        binding.sqSize.text = sqSize.toString()

        binding.editTextRight.setText(rightDirection)
        binding.editTextUp.setText(upDirection)
        binding.editTextLeft.setText(leftDirection)
        binding.editTextDawn.setText(downDirection)
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Save the initial position and touch coordinates
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Calculate the difference in coordinates
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()

                // Update the position of the view
                params.x = initialX + deltaX
                params.y = initialY + deltaY

                // Update the view with the new parameters
                windowManager.updateViewLayout(floatingView, params)
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
            handlerThread.quitSafely()
            mediaProjection?.stop()
            //ActivityReferenceHolder.clear()
        }
    }
    private fun updateWindowParams(hasFocus: Boolean) {
        if (hasFocus) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(binding.root, params)
    }
    private fun setAriaHeightFromInput() {
        val inputText = binding.ediHeigAria.text.toString()

        // Check if the input is not empty and is a valid number
        if (inputText.isNotEmpty()) {
            val heightInDp = inputText.toIntOrNull()

            if (heightInDp != null) {
                // Convert dp to pixels
                val displayMetrics = resources.displayMetrics
                val heightInPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightInDp.toFloat(), displayMetrics).toInt()

                // Set the height of the layout
                binding.aria.layoutParams.height = heightInPx

                // Request layout update
                binding.aria.requestLayout()
            } else {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Please enter a height", Toast.LENGTH_SHORT).show()
        }
    }
    private fun transferEditTextToTextView(editText: EditText, textView: TextView) {

        try {
            val value = editText.text.toString()
            textView.text = value
        }catch (e: Exception) {
            Toast.makeText(this, "EditText=0", Toast.LENGTH_SHORT).show()
        }
        //  transferEditTextToTextView(binding.etCountA, binding.roundNumber)
    }

    private fun changeTextViewBackgroundColor(textView: TextView, color: Int) {
        textView.setBackgroundColor(color)
    }
}
