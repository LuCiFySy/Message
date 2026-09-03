package com.saurabh.messages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.ImageView

class RoundedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val path = Path()
    private val rect = RectF()

    private val cornerRadius =
        resources.displayMetrics.density * 14f

    override fun onDraw(canvas: Canvas) {
        rect.set(
            0f,
            0f,
            width.toFloat(),
            height.toFloat()
        )

        path.reset()
        path.addRoundRect(
            rect,
            cornerRadius,
            cornerRadius,
            Path.Direction.CW
        )

        canvas.save()
        canvas.clipPath(path)

        super.onDraw(canvas)

        canvas.restore()
    }
}
