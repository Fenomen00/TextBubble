package com.example.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.example.service.AccessibilityDiagnostics

/**
 * Manages attaching, updating, and detaching the floating microphone bubble
 * via WindowManager using TYPE_ACCESSIBILITY_OVERLAY on the main thread.
 *
 * Supports smooth, fast slide-in from the right edge and slide-out on exit.
 */
class FloatingBubbleManager(
    private val context: Context,
    private val onRecordStart: () -> Unit,
    private val onRecordEnd: () -> Unit,
    private val onRetry: () -> Unit
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleView: FloatingBubbleView? = null
    private var isViewAttached = false
    private var isHiding = false
    private var currentLayoutParams: WindowManager.LayoutParams? = null

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Shows the bubble at explicit (x, y) coordinates on the main thread.
     */
    fun showBubbleAt(x: Int, y: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showBubbleInternal(x, y)
        } else {
            mainHandler.post { showBubbleInternal(x, y) }
        }
    }

    /**
     * Backward-compatible showBubble: shows at default position or delegates to showBubbleAt.
     */
    fun showBubble() {
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val defaultX = screenWidth - (56 * density).toInt() - (16 * density).toInt()
        val defaultY = ((screenHeight * 0.40f) - (28 * density)).toInt()
        showBubbleAt(defaultX.coerceAtLeast(0), defaultY.coerceAtLeast(0))
    }

    private fun showBubbleInternal(x: Int, y: Int) {
        if (isViewAttached) {
            if (isHiding) {
                isHiding = false
                bubbleView?.cancelExitAnimation()
                bubbleView?.animateEnter()
            }
            moveBubbleToInternal(x, y)
            return
        }

        try {
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                // Critical: TYPE_ACCESSIBILITY_OVERLAY works directly from AccessibilityService
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Critical: FLAG_NOT_FOCUSABLE prevents stealing focus from active text fields
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Explicit coordinates from top-start of screen
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            }
            currentLayoutParams = layoutParams

            val view = FloatingBubbleView(
                context = context,
                onRecordStart = onRecordStart,
                onRecordEnd = onRecordEnd,
                onRetry = onRetry
            )
            bubbleView = view
            isHiding = false

            windowManager.addView(view, layoutParams)
            isViewAttached = true
            AccessibilityDiagnostics.setBubbleAttached(true)
            AccessibilityDiagnostics.setLastError(null)

            // Smooth fast slide-in animation from the right edge
            view.animateEnter()

            Log.d("FloatingBubbleManager", "Bubble view successfully attached at ($x, $y)")
        } catch (t: Throwable) {
            val stackTraceLines = t.stackTrace.take(3).joinToString("\n") { elem ->
                "  at ${elem.className}.${elem.methodName}(${elem.fileName}:${elem.lineNumber})"
            }
            val formattedError = "${t.javaClass.name}: ${t.message ?: "No message"}\n$stackTraceLines"
            Log.e("FloatingBubbleManager", "Failed to add floating bubble: $formattedError", t)
            isViewAttached = false
            bubbleView = null
            currentLayoutParams = null
            AccessibilityDiagnostics.setBubbleAttached(false)
            AccessibilityDiagnostics.setLastError(formattedError)
        }
    }

    /**
     * Moves the bubble to new (x, y) coordinates via updateViewLayout.
     * If not currently attached, attaches it at (x, y).
     */
    fun moveBubbleTo(x: Int, y: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            moveBubbleToInternal(x, y)
        } else {
            mainHandler.post { moveBubbleToInternal(x, y) }
        }
    }

    private fun moveBubbleToInternal(x: Int, y: Int) {
        if (!isViewAttached || bubbleView == null) {
            showBubbleInternal(x, y)
            return
        }

        if (isHiding) {
            isHiding = false
            bubbleView?.cancelExitAnimation()
            bubbleView?.animateEnter()
        }

        try {
            val params = currentLayoutParams ?: return
            if (params.x != x || params.y != y) {
                params.x = x
                params.y = y
                windowManager.updateViewLayout(bubbleView, params)
                Log.d("FloatingBubbleManager", "Updated bubble position to ($x, $y)")
            }
        } catch (t: Throwable) {
            Log.e("FloatingBubbleManager", "Failed to update bubble layout: ${t.message}", t)
        }
    }

    /**
     * Ensures detachment code always executes on the Main thread with a smooth exit animation.
     */
    fun hideBubble() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideBubbleInternal()
        } else {
            mainHandler.post { hideBubbleInternal() }
        }
    }

    private fun hideBubbleInternal() {
        if (!isViewAttached || bubbleView == null || isHiding) return

        val view = bubbleView ?: return
        isHiding = true

        view.animateExit {
            try {
                windowManager.removeView(view)
                Log.d("FloatingBubbleManager", "Bubble view removed from WindowManager")
            } catch (t: Throwable) {
                Log.e("FloatingBubbleManager", "Failed to remove bubble view: ${t.message}", t)
            } finally {
                isHiding = false
                isViewAttached = false
                bubbleView = null
                currentLayoutParams = null
                AccessibilityDiagnostics.setBubbleAttached(false)
            }
        }
    }

    fun setBubbleState(state: BubbleState, message: String? = null) {
        bubbleView?.setState(state, message)
    }

    fun isAttached(): Boolean = isViewAttached && !isHiding
}
