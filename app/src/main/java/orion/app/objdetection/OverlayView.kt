package orion.app.objdetection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<YoloDetector.BoundingBox> = listOf()
    private val boxPaint = Paint()
    private val textPaint = Paint()
    private val bounds = Rect()
    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0

    init {
        boxPaint.color = Color.RED
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 8f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f
    }

    fun setResults(boundingBoxes: List<YoloDetector.BoundingBox>, imgSrcW: Int, imgSrcH: Int) {
        this.results = boundingBoxes
        this.sourceWidth = imgSrcW
        this.sourceHeight = imgSrcH
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sourceWidth == 0 || sourceHeight == 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val scale = min(viewW / sourceWidth, viewH / sourceHeight)

        val drawnW = sourceWidth * scale
        val drawnH = sourceHeight * scale

        val offsetX = (viewW - drawnW) / 2
        val offsetY = (viewH - drawnH) / 2

        for (result in results) {
            // (좌표 * 실제그림크기) + 여백
            val left = result.x1 * drawnW + offsetX
            val top = result.y1 * drawnH + offsetY
            val right = result.x2 * drawnW + offsetX
            val bottom = result.y2 * drawnH + offsetY

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val drawableText = "${result.clsName} ${(result.conf * 100).toInt()}%"
            textPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)

            canvas.drawRect(
                left, top - bounds.height(),
                left + bounds.width(), top,
                boxPaint.apply { style = Paint.Style.FILL }
            )
            boxPaint.style = Paint.Style.STROKE

            canvas.drawText(drawableText, left, top, textPaint)
        }
    }
}
