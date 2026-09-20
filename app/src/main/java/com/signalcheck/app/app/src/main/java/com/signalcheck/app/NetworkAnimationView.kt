package com.signalcheck.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.sqrt
import kotlin.random.Random

class NetworkAnimationView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private data class Node(var x: Float, var y: Float, var vx: Float, var vy: Float)

    private val nodes = mutableListOf<Node>()
    private val nodeCount = 18
    private val linkDistance = 220f
    private val dotPaint = Paint().apply {
        color = Color.parseColor("#00D4FF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val linePaint = Paint().apply {
        color = Color.parseColor("#00D4FF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            updateNodes()
            invalidate()
            if (running) handler.postDelayed(this, 40L)
        }
    }

    private fun ensureNodes() {
        if (nodes.isNotEmpty() || width == 0 || height == 0) return
        repeat(nodeCount) {
            nodes.add(
                Node(
                    Random.nextFloat() * width,
                    Random.nextFloat() * height,
                    (Random.nextFloat() - 0.5f) * 1.2f,
                    (Random.nextFloat() - 0.5f) * 1.2f
                )
            )
        }
    }

    private fun updateNodes() {
        for (n in nodes) {
            n.x += n.vx
            n.y += n.vy
            if (n.x < 0 || n.x > width) n.vx *= -1
            if (n.y < 0 || n.y > height) n.vy *= -1
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureNodes()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ensureNodes()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < linkDistance) {
                    val alpha = (255 * (1f - dist / linkDistance) * 0.5f).toInt().coerceIn(0, 255)
                    linePaint.alpha = alpha
                    canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
                }
            }
        }
        for (n in nodes) {
            dotPaint.alpha = 220
            canvas.drawCircle(n.x, n.y, 4f, dotPaint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        handler.post(tick)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        handler.removeCallbacksAndMessages(null)
    }
}
