package com.example.lookerto


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lookerto.databinding.ActivityFlotingBinding
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt




class AreaSelectionOverlay2(context: Context, attrs: AttributeSet) : View(context, attrs) {
    var onAreaSelectedListener: ((Rect) -> Unit)? = null
    private  var windowManager: WindowManager
    private val context: Context // Store the context
    private var TAG = "My error in "
    private var currentCenter = PointF() // Center of the square
    private var squareSize = 100f // Initial square size
    private var initialCenterX: Float = 0f
    private var initialCenterY: Float = 0f
    private var initialSquareSize: Float = 75f // Or your desired default size
    // val screenshot1 = ScreenshotUtil
    private var areaX = 0
    private var areaY = 0
    private var areaWidth = 0
    private var areaHeight = 0
    private var rectPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    enum class Mode { MOVE, RESIZE }

    private var currentMode = Mode.MOVE // Initial mode is moving
    private var initialX = 0f
    private var initialY = 0f
    private var currentX = 0f
    private var currentY = 0f
    init {
        // Initialize the context
        this.context = context
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Set initial center to the middle of the view
        currentCenter.x = w / 2f
        currentCenter.y = h / 2f

        initialCenterX = w / 2f
        initialCenterY = h / 2f
        initialSquareSize = squareSize
    }
    fun getSquareToTextViewValues(binding: ActivityFlotingBinding): Triple<Float, Float, Float> {
        // Update the text views with current square position and size
        binding.posX.text = currentCenter.x.toString()
        binding.posY.text = currentCenter.y.toString()
        binding.sqSize.text = squareSize.toString()
        Log.d(TAG, "getSquareToTextViewValues: currentX=${currentCenter.x}, currentY=${currentCenter.y}")

        // Return the current x, y, and squareSize as a Triple
        return Triple(currentCenter.x, currentCenter.y, squareSize)
    }


    fun resetSquareToTextViewValues(binding: ActivityFlotingBinding): Triple<Float, Float, Float> {
        // Retrieve the position and size from the text views
        val newSquareSize = if (binding.sqSize.text.isNullOrEmpty()) 66.00619f else binding.sqSize.text.toString().toFloat()
        val newX = if (binding.posX.text.isNullOrEmpty()) 102.24995f else binding.posX.text.toString().toFloat()
        val newY = if (binding.posY.text.isNullOrEmpty()) 35.716938f else binding.posY.text.toString().toFloat()

        Log.d(TAG, "resetSquareToTextViewValues: newX=$newX, newY=$newY, newSquareSize=$newSquareSize")

        // Update the current center and square size
        currentCenter.x = newX
        currentCenter.y = newY
        squareSize = newSquareSize

        // Redraw the view
        invalidate()

        // Return the new x, y, and squareSize
        return Triple(newX, newY, newSquareSize)
    }


    fun resetSquareToTextViewValues2(binding: ActivityFlotingBinding): Triple<Float, Float, Float> {
        // Retrieve the position and size from the text views
        val newSquareSize = binding.sqSize.text.toString().toFloat()
        val newX = binding.posX.text.toString().toFloat()
        val newY = binding.posY.text.toString().toFloat()

        Log.d(TAG, "resetSquareToTextViewValues2: newX=$newX, newY=$newY, newSquareSize=$newSquareSize")

        // Update the current center and square size
        currentCenter.x = newX
        currentCenter.y = newY
        squareSize = newSquareSize

        // Redraw the view
        invalidate()

        // Return the new x, y, and squareSize
        return Triple(newX, newY, newSquareSize)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Determine whether the touch event is within the square or near the corner
                val distanceToCenter = distance(event.x, event.y, currentCenter.x, currentCenter.y)
                val distanceToCorner = distance(
                    event.x,
                    event.y,
                    currentCenter.x + squareSize / 2,
                    currentCenter.y + squareSize / 2
                )

                if (distanceToCorner < 50f) {
                    // If the touch is near the corner, switch to resize mode
                    currentMode = Mode.RESIZE
                } else if (distanceToCenter < squareSize / 2) {
                    // If the touch is within the square, switch to move mode
                    currentMode = Mode.MOVE
                }

                // Store initial touch coordinates
                initialX = event.x
                initialY = event.y
                currentX = initialX
                currentY = initialY
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentMode == Mode.MOVE) {
                    // Handle square movement
                    val deltaX = event.x - currentX
                    val deltaY = event.y - currentY
                    currentCenter.x += deltaX
                    currentCenter.y += deltaY

                    // Ensure the square remains within the view bounds
                    currentCenter.x =
                        currentCenter.x.coerceIn(squareSize / 2, width - squareSize / 2)
                    currentCenter.y =
                        currentCenter.y.coerceIn(squareSize / 2, height - squareSize / 2)

                    currentX = event.x
                    currentY = event.y
                } else if (currentMode == Mode.RESIZE) {
                    // Handle square resizing
                    squareSize += event.x - initialX  // Resize based on difference from initialX
                    squareSize = squareSize.coerceAtMost(dpToPx(100f))

                    initialX = event.x  // Update initialX to track changes in resize
                }
                invalidate() // Redraw the view
            }

            MotionEvent.ACTION_UP -> {
                // Perform any actions when the touch is released (e.g., notify listeners)
                val selectedArea = getSelectedArea()
                onAreaSelectedListener?.invoke(selectedArea)

                // Calculate the selected area (rectangle) based on touch coordinates
                areaX = min(initialX, currentX).toInt()
                areaY = min(initialY, currentY).toInt()
                areaWidth = (max(initialX, currentX) - areaX).toInt()
                areaHeight = (max(initialY, currentY) - areaY).toInt()
            }
        }
        return true // Indicate that the touch event was handled
    }


    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }


    fun resetSquarePosition() {
        currentCenter.x = width / 1f
        currentCenter.y = height / 1f
        invalidate()
    }
    fun getSelectedArea(): Rect {
        val left = min(initialX, currentX).toInt()
        val top = min(initialY, currentY).toInt()
        val right = max(initialX, currentX).toInt()
        val bottom = max(initialY, currentY).toInt()
        return Rect(left, top, right, bottom)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Calculate corners of the square
        val left = currentCenter.x - squareSize / 2
        val top = currentCenter.y - squareSize / 2
        val right = currentCenter.x + squareSize / 2
        val bottom = currentCenter.y + squareSize / 2

        // Update the current corner being dragged (bottom-right for now)
        currentX = right
        currentY = bottom

        Log.d(TAG, "onDraw: left=$left, top=$top, right=$right, bottom=$bottom")
        canvas.drawRect(left, top, right, bottom, rectPaint)
    }
    // Helper function to calculate distance between two points
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }
}


