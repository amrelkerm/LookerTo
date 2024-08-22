package com.example.lookerto

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.lookerto.floting.Companion.CAPTURE_SCREENSHOT_REQUEST
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min



class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private lateinit var imageReader: ImageReader
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var floting: floting


    // ... (Your screen capture logic will go here)

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }
    private fun startForegroundService() {
        val notificationId = 1
        val channelId = "screen_capture_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Screen Capture Service"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Capture Service")
            .setContentText("Capturing screen in progress")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        startForeground(notificationId, notification)
    }


    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjectionIntent = intent?.getParcelableExtra<Intent>("mediaProjectionIntent")
        val resultCode = intent?.getIntExtra("mediaProjectionResultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED

        if (resultCode == Activity.RESULT_OK && mediaProjectionIntent != null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, mediaProjectionIntent)
            val x = intent.getFloatExtra("x", 0f)
            val y = intent.getFloatExtra("y", 0f)
            val squareSize = intent.getFloatExtra("squareSize", 0f)
            val parentX = intent.getIntExtra("parentX", 0)
            val parentY = intent.getIntExtra("parentY", 0)
            val chosenDirections = intent.getSerializableExtra("chosenDirections") as? ArrayList<Direction> ?: arrayListOf()

            captureSquareScreenshot(x, y, squareSize, parentX, parentY, chosenDirections)
           // floting.captureScreenshot3()
        }

        return START_NOT_STICKY
    }

    private fun captureSquareScreenshot(
        x: Float,
        y: Float,
        squareSize: Float,
        parentX: Int,
        parentY: Int,
        chosenDirections: List<Direction>
    ) {
        val metrics: DisplayMetrics = resources.displayMetrics
        val density = metrics.densityDpi
        val displayWidth = metrics.widthPixels
        val displayHeight = metrics.heightPixels

        val captureX = (parentX + x - squareSize / 2).toInt()
        val captureY = (parentY + y - squareSize / 2).toInt()

        val (totalAdjustX, totalAdjustY) = calculateAdjustments2(chosenDirections, metrics)

        imageReader = ImageReader.newInstance(displayWidth, displayHeight, PixelFormat.RGBA_8888, 2)

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

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
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

                val validX = max(0, captureX)
                val validY = max(0, captureY)

                val captureStartX = max(validX - totalAdjustX, 0)
                val captureStartY = max(validY - totalAdjustY, 0)
                val captureEndX = min(validX + squareSize.toInt() - totalAdjustX, fullBitmap.width)
                val captureEndY = min(validY + squareSize.toInt() - totalAdjustY, fullBitmap.height)

                capturedBitmap = Bitmap.createBitmap(
                    fullBitmap,
                    captureStartX,
                    captureStartY,
                    captureEndX - captureStartX,
                    captureEndY - captureStartY
                )

                saveBitmapToFile(capturedBitmap)
                fullBitmap.recycle()
            } catch (e: Exception) {
                Log.e("ScreenCaptureService", "Error capturing screenshot", e)
            } finally {
                image.close()
                stopSelf()  // Stop service after capture
            }
        }, null)
    }

    private fun saveBitmapToFile(bitmap: Bitmap?) {
        if (bitmap == null) return

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val imageFile = File(imagesDir, "Screenshot_$timeStamp.png")
       // Toast.makeText(this, "Service Screenshot saved to ", Toast.LENGTH_SHORT).show()

        try {
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                Log.e(TAG, "Screenshot saved to: ${imageFile.absolutePath}")

                Toast.makeText(this, "Service Screenshot saved to ", Toast.LENGTH_SHORT).show()

            }
        } catch (e: IOException) {
            Log.e("ScreenCaptureService", "Failed to save screenshot", e)
        }
    }

    private fun calculateAdjustments2(chosenDirections: List<Direction>, metrics: DisplayMetrics): Pair<Int, Int> {
        val adjustLeft = 10.dpToPx(metrics)
        val adjustRight = 10.dpToPx(metrics)
        val adjustUp = 25.dpToPx(metrics)
        val adjustDown = 25.dpToPx(metrics)

        val totalAdjustX = (if (chosenDirections.contains(Direction.LEFT)) adjustLeft else 0) -
                (if (chosenDirections.contains(Direction.RIGHT)) adjustRight else 0)

        val totalAdjustY = (if (chosenDirections.contains(Direction.UP)) adjustUp else 0) -
                (if (chosenDirections.contains(Direction.DOWN)) adjustDown else 0)

        return Pair(totalAdjustX, totalAdjustY)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    enum class Direction {
        LEFT, RIGHT, UP, DOWN
    }

    //Extension function to convert dp to pixels
    private fun Int.dpToPx(metrics: DisplayMetrics): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), metrics).toInt()
    }

    val TAG = " Service"
    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
        imageReader?.close()
    }

}
