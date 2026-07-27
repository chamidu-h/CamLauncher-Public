package com.camlauncher.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class HudManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var hudView: FrameLayout? = null
    var previewView: PreviewView? = null
        private set
    private var hudScope: CoroutineScope? = null

    @SuppressLint("ClickableViewAccessibility")
    fun showHud(onStopClicked: () -> Unit) {
        if (hudView != null) return

        // 1. Root FrameLayout
        val root = FrameLayout(context).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#CC000000"))
                cornerRadius = dpToPx(context, 16f)
            }
            background = bg
            setPadding(dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt())
        }

        // 2. PreviewView
        previewView = PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(previewView)

        // 3. Status Bar (Red dot + Chronometer)
        val statusContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#80000000"))
                cornerRadius = dpToPx(context, 8f)
            }
            background = bg
            setPadding(dpToPx(context, 6f).toInt(), dpToPx(context, 4f).toInt(), dpToPx(context, 6f).toInt(), dpToPx(context, 4f).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(dpToPx(context, 4f).toInt(), dpToPx(context, 4f).toInt(), 0, 0)
            }
        }

        val redDot = View(context).apply {
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
            }
            background = dotBg
            layoutParams = LinearLayout.LayoutParams(dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt()).apply {
                marginEnd = dpToPx(context, 4f).toInt()
            }
        }
        statusContainer.addView(redDot)

        val chronometer = Chronometer(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            base = SystemClock.elapsedRealtime()
            start()
        }
        statusContainer.addView(chronometer)

        root.addView(statusContainer)

        // 4. Stop Button
        val stopButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.WHITE)
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#44FFFFFF"))
            }
            background = btnBg
            setPadding(dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt())
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(context, 32f).toInt(),
                dpToPx(context, 32f).toInt()
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(context, 8f).toInt()
            }
            setOnClickListener {
                onStopClicked()
            }
        }
        root.addView(stopButton)

        // 4.5. Flip Camera Button
        val flipButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_popup_sync)
            setColorFilter(Color.WHITE)
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#44FFFFFF"))
            }
            background = btnBg
            setPadding(dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt(), dpToPx(context, 8f).toInt())
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(context, 32f).toInt(),
                dpToPx(context, 32f).toInt()
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                bottomMargin = dpToPx(context, 8f).toInt()
                marginStart = dpToPx(context, 4f).toInt()
            }
            setOnClickListener {
                com.camlauncher.app.service.RecordingService.flipCamera()
            }
        }
        root.addView(flipButton)

        // 5. Expandable Zoom Menu
        val zoomLevels = listOf(0.5f, 1.0f, 2.0f)
        val zoomLabels = listOf(".5x", "1x", "2x")
        
        // Container for zoom options
        val zoomContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val scrollOuter = ScrollView(context).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(context, 40f).toInt(),
                dpToPx(context, 80f).toInt() // Limit height and make it scrollable
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = dpToPx(context, 4f).toInt()
                bottomMargin = dpToPx(context, 48f).toInt() // Above the toggle button
            }
            addView(zoomContainer)
        }

        // Add individual zoom level buttons to the container
        zoomLevels.forEachIndexed { index, ratio ->
            val btn = android.widget.Button(context).apply {
                text = zoomLabels[index]
                setTextColor(Color.WHITE)
                textSize = 8f
                setPadding(0, 0, 0, 0)
                val btnBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#99000000"))
                }
                background = btnBg
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(context, 28f).toInt(),
                    dpToPx(context, 28f).toInt()
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dpToPx(context, 4f).toInt()
                }
                setOnClickListener {
                    com.camlauncher.app.service.RecordingService.setLiveZoom(ratio)
                    scrollOuter.visibility = View.GONE
                }
            }
            zoomContainer.addView(btn)
        }
        root.addView(scrollOuter)

        // Main Zoom Toggle Button
        val zoomToggleButton = android.widget.Button(context).apply {
            text = "Z"
            setTextColor(Color.WHITE)
            textSize = 11f
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#44FFFFFF"))
            }
            background = btnBg
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(context, 32f).toInt(),
                dpToPx(context, 32f).toInt()
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = dpToPx(context, 8f).toInt()
                marginEnd = dpToPx(context, 4f).toInt()
            }
            setOnClickListener {
                scrollOuter.visibility = if (scrollOuter.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        root.addView(zoomToggleButton)

        // Observe camera lens changes to hide/show zoom
        hudScope = CoroutineScope(Dispatchers.Main + Job())
        hudScope?.launch {
            com.camlauncher.app.service.RecordingService.currentLensFlow.collectLatest { lens ->
                val isNotFrontCamera = lens != CameraSelector.DEFAULT_FRONT_CAMERA
                zoomToggleButton.visibility = if (isNotFrontCamera) View.VISIBLE else View.GONE
                if (!isNotFrontCamera) scrollOuter.visibility = View.GONE
            }
        }

        // WindowParams
        val params = WindowManager.LayoutParams(
            dpToPx(context, 120f).toInt(),
            dpToPx(context, 160f).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        // Dragging Logic
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        root.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(root, params)
            hudView = root
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismiss() {
        try {
            hudView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        hudView = null
        previewView = null
        hudScope?.cancel()
        hudScope = null
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
    }
}
