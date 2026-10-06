package com.example.overlay

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

enum class BubbleState {
    IDLE,
    RECORDING,
    PROCESSING,
    SUCCESS,
    ERROR
}

/**
 * Premium semi-transparent blurry logo view drawn purely in code.
 * Features a frosted glass aesthetic with a soft ambient radial blur halo,
 * speech bubble brand silhouette with specular sheen, and voice frequency waveform bars.
 */
class VoiceBubbleLogoView(context: Context) : View(context) {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bubbleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glassSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var accentColor = Color.parseColor("#38BDF8") // Cyan default

    fun setAccentColor(color: Int) {
        accentColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val cx = w / 2f
        val cy = h / 2f

        val red = Color.red(accentColor)
        val green = Color.green(accentColor)
        val blue = Color.blue(accentColor)

        // 1. Soft Blurry Radial Glow (creates the frosted/blurry aura behind the logo)
        val glowRadius = w * 0.48f
        val glowShader = RadialGradient(
            cx, cy,
            glowRadius,
            intArrayOf(
                Color.argb(120, red, green, blue),
                Color.argb(55, red, green, blue),
                Color.argb(0, red, green, blue)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = glowShader
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // 2. Semi-transparent frosted speech bubble brand silhouette
        val bubbleLeft = w * 0.16f
        val bubbleRight = w * 0.84f
        val bubbleTop = h * 0.16f
        val bubbleBottom = h * 0.72f
        val cornerRadius = 8f * density

        val bubbleRect = RectF(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom)
        val bubblePath = Path().apply {
            addRoundRect(bubbleRect, cornerRadius, cornerRadius, Path.Direction.CW)
            // Speech tail at bottom left
            moveTo(w * 0.32f, bubbleBottom)
            lineTo(w * 0.22f, h * 0.88f)
            lineTo(w * 0.44f, bubbleBottom)
        }

        // Frosted semi-transparent fill
        bubbleFillPaint.color = Color.argb(45, 255, 255, 255)
        canvas.drawPath(bubblePath, bubbleFillPaint)

        // Subtle frosted glass interior tint
        bubbleFillPaint.color = Color.argb(40, red, green, blue)
        canvas.drawPath(bubblePath, bubbleFillPaint)

        // Frosted rim outline with specular gradient
        bubbleStrokePaint.strokeWidth = 1.8f * density
        val rimShader = LinearGradient(
            bubbleLeft, bubbleTop,
            bubbleRight, bubbleBottom,
            intArrayOf(
                Color.argb(220, 255, 255, 255),
                Color.argb(160, red, green, blue),
                Color.argb(210, 255, 255, 255)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        bubbleStrokePaint.shader = rimShader
        canvas.drawPath(bubblePath, bubbleStrokePaint)

        // 3. Top Frosted Glass Sheen Highlight
        val sheenRect = RectF(
            bubbleLeft + 1.5f * density,
            bubbleTop + 1.5f * density,
            bubbleRight - 1.5f * density,
            bubbleTop + (bubbleBottom - bubbleTop) * 0.42f
        )
        val sheenShader = LinearGradient(
            sheenRect.left, sheenRect.top,
            sheenRect.left, sheenRect.bottom,
            Color.argb(60, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        glassSheenPaint.shader = sheenShader
        canvas.drawRoundRect(sheenRect, cornerRadius, cornerRadius, glassSheenPaint)

        // 4. Iconic Voice Spectrum / Sound Frequency Wave Logo Emblem
        wavePaint.strokeWidth = 2.4f * density
        wavePaint.color = accentColor

        val barSpacing = 4.8f * density
        val waveCy = (bubbleTop + bubbleBottom) / 2f
        val bar1H = 6f * density
        val bar2H = 12f * density
        val bar3H = 17f * density
        val bar4H = 11f * density
        val bar5H = 6f * density

        // Draw the 5 voice frequency bars with smooth rounded caps
        canvas.drawLine(cx - 2 * barSpacing, waveCy - bar1H / 2f, cx - 2 * barSpacing, waveCy + bar1H / 2f, wavePaint)
        canvas.drawLine(cx - barSpacing, waveCy - bar2H / 2f, cx - barSpacing, waveCy + bar2H / 2f, wavePaint)
        canvas.drawLine(cx, waveCy - bar3H / 2f, cx, waveCy + bar3H / 2f, wavePaint)
        canvas.drawLine(cx + barSpacing, waveCy - bar4H / 2f, cx + barSpacing, waveCy + bar4H / 2f, wavePaint)
        canvas.drawLine(cx + 2 * barSpacing, waveCy - bar5H / 2f, cx + 2 * barSpacing, waveCy + bar5H / 2f, wavePaint)
    }
}

/**
 * Floating round microphone bubble widget with semi-transparent frosted styling,
 * smooth fast slide-in/out animations from the right edge, and voice wave logo.
 */
@SuppressLint("ViewConstructor")
class FloatingBubbleView(
    context: Context,
    private val onRecordStart: () -> Unit,
    private val onRecordEnd: () -> Unit,
    private val onRetry: () -> Unit
) : LinearLayout(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentState = BubbleState.IDLE
    private var touchDownTime = 0L

    // Explicit non-null view references
    private val statusPill: TextView
    private val bubbleCircle: FrameLayout
    private val circleBackground: GradientDrawable
    private val logoView: VoiceBubbleLogoView
    private val progressBar: ProgressBar
    private var pulseAnimator: ObjectAnimator? = null

    // Frosted glass colors (Semi-transparent / blurry appearance, ~55% opacity)
    private val colorIdleBg = Color.argb(140, 15, 23, 42) // Frosted deep slate
    private val colorIdleStroke = Color.argb(160, 56, 189, 248) // Translucent cyan ring
    private val colorRecordingBg = Color.argb(165, 75, 12, 18) // Frosted crimson
    private val colorRecordingStroke = Color.argb(210, 239, 68, 68) // Glowing red ring
    private val colorProcessingBg = Color.argb(160, 60, 30, 115) // Frosted violet
    private val colorProcessingStroke = Color.argb(200, 167, 139, 250) // Glowing violet ring
    private val colorSuccessBg = Color.argb(165, 6, 78, 59) // Frosted emerald
    private val colorSuccessStroke = Color.argb(210, 16, 185, 129) // Glowing emerald ring

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false

        val density = context.resources.displayMetrics.density
        val bubbleSizePx = (56 * density).toInt()

        // 1. Status Pill (Tooltip / Message on error or hint)
        statusPill = TextView(context).apply {
            text = "Hold to speak"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding((12 * density).toInt(), (5 * density).toInt(), (12 * density).toInt(), (5 * density).toInt())
            val pillBg = GradientDrawable().apply {
                setColor(Color.argb(200, 15, 23, 42))
                cornerRadius = 14 * density
                setStroke((1 * density).toInt(), Color.argb(140, 56, 189, 248))
            }
            background = pillBg
            visibility = View.GONE
            val pillParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (6 * density).toInt()
            }
            layoutParams = pillParams
        }
        addView(statusPill)

        // 2. Semi-transparent frosted circle background
        circleBackground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorIdleBg)
            setStroke((1.8f * density).toInt(), colorIdleStroke)
        }

        // 3. Create the FrameLayout circle and assign it immediately
        val circleLayout = FrameLayout(context).apply {
            val circleParams = LayoutParams(bubbleSizePx, bubbleSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            layoutParams = circleParams
            elevation = 12 * density
            background = circleBackground
        }
        bubbleCircle = circleLayout

        // 4. Inner Voice Bubble Logo (drawn in code with semi-transparent frosted styling)
        val logoSize = (36 * density).toInt()
        logoView = VoiceBubbleLogoView(context).apply {
            val logoParams = FrameLayout.LayoutParams(logoSize, logoSize).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = logoParams
        }
        bubbleCircle.addView(logoView)

        // 5. Inner Progress Bar for Processing State
        val progressSize = (30 * density).toInt()
        progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            visibility = View.GONE
            val progressParams = FrameLayout.LayoutParams(progressSize, progressSize).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = progressParams
        }
        bubbleCircle.addView(progressBar)

        addView(bubbleCircle)

        // Touch Listener: Press and hold to record
        bubbleCircle.setOnTouchListener { _, event ->
            handleTouchEvent(event)
        }
    }

    /**
     * Smoothly animates the bubble sliding in fast from the right edge of the screen,
     * mirroring how the keyboard appears from the bottom.
     */
    fun animateEnter() {
        val density = context.resources.displayMetrics.density
        val startOffset = 150f * density
        translationX = startOffset
        alpha = 0f
        scaleX = 0.9f
        scaleY = 0.9f

        animate()
            .translationX(0f)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()
    }

    /**
     * Smoothly animates the bubble sliding out fast to the right edge of the screen
     * as soon as the keyboard exits.
     */
    fun animateExit(onAnimationEnd: () -> Unit) {
        val density = context.resources.displayMetrics.density
        val exitOffset = 150f * density

        animate()
            .translationX(exitOffset)
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(140)
            .setInterpolator(AccelerateInterpolator(1.6f))
            .withEndAction {
                onAnimationEnd()
            }
            .start()
    }

    fun cancelExitAnimation() {
        animate().cancel()
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTime = System.currentTimeMillis()
                if (currentState == BubbleState.ERROR) {
                    onRetry()
                    return true
                }
                if (currentState == BubbleState.IDLE) {
                    setState(BubbleState.RECORDING)
                    onRecordStart()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val duration = System.currentTimeMillis() - touchDownTime
                if (currentState == BubbleState.RECORDING) {
                    if (duration < 400) {
                        // Quick tap: Show tooltip
                        showTemporaryStatus("Hold to record audio", 2000)
                        setState(BubbleState.IDLE)
                        onRecordEnd()
                    } else {
                        setState(BubbleState.PROCESSING, "Transcribing...")
                        onRecordEnd()
                    }
                }
                return true
            }
        }
        return false
    }

    fun setState(state: BubbleState, message: String? = null) {
        post {
            currentState = state
            pulseAnimator?.cancel()
            bubbleCircle.scaleX = 1f
            bubbleCircle.scaleY = 1f

            val density = context.resources.displayMetrics.density

            when (state) {
                BubbleState.IDLE -> {
                    circleBackground.setColor(colorIdleBg)
                    circleBackground.setStroke((1.8f * density).toInt(), colorIdleStroke)
                    logoView.setAccentColor(Color.parseColor("#38BDF8"))
                    logoView.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    statusPill.visibility = View.GONE
                }

                BubbleState.RECORDING -> {
                    circleBackground.setColor(colorRecordingBg)
                    circleBackground.setStroke((2 * density).toInt(), colorRecordingStroke)
                    logoView.setAccentColor(Color.parseColor("#EF4444"))
                    logoView.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    showStatus("Recording...")
                    startPulseAnimation()
                }

                BubbleState.PROCESSING -> {
                    circleBackground.setColor(colorProcessingBg)
                    circleBackground.setStroke((2 * density).toInt(), colorProcessingStroke)
                    logoView.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                    showStatus(message ?: "Working...")
                }

                BubbleState.SUCCESS -> {
                    circleBackground.setColor(colorSuccessBg)
                    circleBackground.setStroke((2 * density).toInt(), colorSuccessStroke)
                    logoView.setAccentColor(Color.parseColor("#10B981"))
                    logoView.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    showStatus("Pasted!")
                    mainHandler.postDelayed({
                        setState(BubbleState.IDLE)
                    }, 1500)
                }

                BubbleState.ERROR -> {
                    circleBackground.setColor(colorRecordingBg)
                    circleBackground.setStroke((2 * density).toInt(), colorRecordingStroke)
                    logoView.setAccentColor(Color.parseColor("#EF4444"))
                    logoView.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    showStatus(message ?: "Error. Tap to retry")
                }
            }
        }
    }

    private fun startPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            bubbleCircle,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f, 1f)
        ).apply {
            duration = 750
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun showStatus(text: String) {
        statusPill.text = text
        statusPill.visibility = View.VISIBLE
    }

    fun showTemporaryStatus(text: String, durationMs: Long = 2000) {
        post {
            statusPill.text = text
            statusPill.visibility = View.VISIBLE
            mainHandler.postDelayed({
                if (currentState == BubbleState.IDLE) {
                    statusPill.visibility = View.GONE
                }
            }, durationMs)
        }
    }
}
