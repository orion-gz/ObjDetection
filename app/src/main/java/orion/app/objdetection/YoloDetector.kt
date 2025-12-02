package orion.app.objdetection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.nio.ByteBuffer

class YoloDetector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String
) {
    private var interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()
    private val INPUT_SIZE = 640

    data class BoundingBox(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val cx: Float, val cy: Float, val w: Float, val h: Float,
        val conf: Float, val clsIdx: Int, val clsName: String
    )

    fun setup() {
        val model = FileUtil.loadMappedFile(context, modelPath)

        // 쓰레드 수 4개
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            // setUseNNAPI(true) // 안드로이드 10 이상에서 더 빨라질 수 있음
        }
        interpreter = Interpreter(model, options)
        labels = FileUtil.loadLabels(context, labelPath)
    }

    fun detect(bitmap: Bitmap, rotation: Int): List<BoundingBox> {
        interpreter ?: return emptyList()

        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-rotation / 90))
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputShape = intArrayOf(1, 4 + labels.size, 8400)
        val outputBuffer = ByteBuffer.allocateDirect(outputShape[0] * outputShape[1] * outputShape[2] * 4)
        outputBuffer.order(java.nio.ByteOrder.nativeOrder())

        interpreter?.run(tensorImage.buffer, outputBuffer)

        outputBuffer.rewind()
        val floatArray = FloatArray(outputShape[0] * outputShape[1] * outputShape[2])
        outputBuffer.asFloatBuffer().get(floatArray)

        val boxes = mutableListOf<BoundingBox>()
        val numClasses = labels.size

        for (i in 0 until 8400) {
            var maxConf = 0f
            var maxIdx = -1

            for (c in 0 until numClasses) {
                val conf = floatArray[(4 + c) * 8400 + i]
                if (conf > maxConf) {
                    maxConf = conf
                    maxIdx = c
                }
            }

            if (maxConf > 0.3f) {
                val cx = floatArray[0 * 8400 + i]
                val cy = floatArray[1 * 8400 + i]
                val w = floatArray[2 * 8400 + i]
                val h = floatArray[3 * 8400 + i]

                val x1 = cx - (w / 2F)
                val y1 = cy - (h / 2F)
                val x2 = cx + (w / 2F)
                val y2 = cy + (h / 2F)

                boxes.add(BoundingBox(x1, y1, x2, y2, cx, cy, w, h, maxConf, maxIdx, labels[maxIdx]))
            }
        }

        return applyNMS(boxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>, threshold: Float = 0.5f): List<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.conf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.removeAt(0)
            selectedBoxes.add(first)
            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIoU(first, next) >= threshold) {
                    iterator.remove()
                }
            }
        }
        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        if (x1 >= x2 || y1 >= y2) return 0f
        val intersection = (x2 - x1) * (y2 - y1)
        val area1 = box1.w * box1.h
        val area2 = box2.w * box2.h
        return intersection / (area1 + area2 - intersection)
    }
}