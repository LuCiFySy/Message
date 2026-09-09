package com.saurabh.messages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class ModernToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var checked = false
    private var onChanged: ((Boolean) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.9f)
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }

    private val trackRect = RectF()
    private val thumbRect = RectF()

    init {
        isClickable = true
        isFocusable = true

        setOnClickListener {
            checked = !checked
            updateContentDescription()
            invalidate()
            onChanged?.invoke(checked)
        }

        updateContentDescription()
    }

    fun setChecked(value: Boolean) {
        checked = value
        updateContentDescription()
        invalidate()
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        onChanged = listener
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val width = dp(48f).toInt()
        val height = dp(24f).toInt()

        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val trackHeight = h
        val trackRadius = h / 2f

        // Track
        trackRect.set(
            0f,
            0f,
            w,
            trackHeight
        )

        if (checked) {
            trackPaint.style = Paint.Style.FILL
            trackPaint.color = ContextCompat.getColor(
                context,
                R.color.messages_primary
            )

            canvas.drawRoundRect(
                trackRect,
                trackRadius,
                trackRadius,
                trackPaint
            )
        } else {
            trackPaint.style = Paint.Style.FILL
            trackPaint.color = ContextCompat.getColor(
                context,
                R.color.messages_surface_variant
            )

            canvas.drawRoundRect(
                trackRect,
                trackRadius,
                trackRadius,
                trackPaint
            )

            trackPaint.style = Paint.Style.STROKE
            trackPaint.strokeWidth = dp(1.5f)
            trackPaint.color = ContextCompat.getColor(
                context,
                R.color.messages_text_secondary
            )

            canvas.drawRoundRect(
                RectF(
                    dp(0.75f),
                    dp(0.75f),
                    w - dp(0.75f),
                    h - dp(0.75f)
                ),
                trackRadius,
                trackRadius,
                trackPaint
            )
        }

        // Large circular thumb.
        val thumbSize = dp(20f)
        val thumbMargin = (h - thumbSize) / 2f

        val thumbLeft = if (checked) {
            w - thumbSize - thumbMargin
        } else {
            thumbMargin
        }

        val thumbTop = thumbMargin

        thumbRect.set(
            thumbLeft,
            thumbTop,
            thumbLeft + thumbSize,
            thumbTop + thumbSize
        )

        thumbPaint.style = Paint.Style.FILL
        thumbPaint.color = if (checked) {
            ContextCompat.getColor(
                context,
                R.color.messages_on_primary
            )
        } else {
            ContextCompat.getColor(
                context,
                R.color.messages_text_secondary
            )
        }

        canvas.drawOval(
            thumbRect,
            thumbPaint
        )

        // Check / X inside the thumb.
        iconPaint.color = if (checked) {
            ContextCompat.getColor(
                context,
                R.color.messages_primary
            )
        } else {
            ContextCompat.getColor(
                context,
                R.color.messages_background
            )
        }

        val cx = thumbLeft + thumbSize / 2f
        val cy = thumbTop + thumbSize / 2f
        val icon = dp(3.8f)

        if (checked) {
            val check = Path().apply {
                moveTo(
                    cx - icon,
                    cy
                )
                lineTo(
                    cx - dp(1.5f),
                    cy + icon
                )
                lineTo(
                    cx + icon,
                    cy - icon
                )
            }

            canvas.drawPath(
                check,
                iconPaint
            )
        } else {
            canvas.drawLine(
                cx - icon,
                cy - icon,
                cx + icon,
                cy + icon,
                iconPaint
            )

            canvas.drawLine(
                cx + icon,
                cy - icon,
                cx - icon,
                cy + icon,
                iconPaint
            )
        }
    }

    private fun updateContentDescription() {
        contentDescription = if (checked) {
            "On"
        } else {
            "Off"
        }
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
