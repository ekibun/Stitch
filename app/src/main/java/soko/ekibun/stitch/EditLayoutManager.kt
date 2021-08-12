package soko.ekibun.stitch

import android.graphics.Path
import android.graphics.RectF
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class EditLayoutManager : RecyclerView.LayoutManager() {
    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun getTranslate(): Array<Float> {
        val scale = scale
        val transX = max(0f, (width - rangeX * scale) / 2) -
                offsetX * scale
        val transY = max(0f, (height - rangeY * scale) / 2) -
                offsetY * scale
        return arrayOf(transX, transY, scale)
    }

    fun update() {
        var lastX = 0
        var lastY = 0
        var lastW = 0
        var lastH = 0
        maxX = 0
        maxY = 0
        minX = 0
        minY = 0
        App.stitchInfo.forEachIndexed { i, it ->
            it.x = if (i == 0) 0 else lastX + it.dx - (it.width - lastW) / 2
            it.y = if (i == 0) 0 else lastY + it.dy - (it.height - lastH) / 2

            minX = min(minX, it.x)
            minY = min(minY, it.y)
            maxX = max(it.x + it.width, maxX)
            maxY = max(it.y + it.height, maxY)

            val transX = it.x + it.width / 2f - lastX - lastW / 2f
            val transY = it.y + it.height / 2f - lastY - lastH / 2f

            val overL = max(it.x, lastX)
            val overT = max(it.y, lastY)
            val overR = min(it.x + it.width, lastX + lastW)
            val overB = min(it.y + it.height, lastY + lastH)
            lastX = it.x
            lastY = it.y
            lastW = it.width
            lastH = it.height
            // computePath
            it.path.reset()
            it.path.addRect(
                it.x.toFloat(),
                it.y.toFloat(),
                it.x + it.width.toFloat(),
                it.y + it.height.toFloat(),
                Path.Direction.CW
            )
            val over = if (overR < overL || overB < overT) {
                RectF(0f, 0f, 0f, 0f)
            } else if (abs(transX) < abs(transY)) {
                if (transY > 0) {
                    RectF(
                        overL.toFloat(),
                        overT.toFloat(),
                        overR.toFloat(),
                        overT * (1 - it.trim) + overB * it.trim
                    )
                } else {
                    RectF(
                        overL.toFloat(),
                        overT * it.trim + overB * (1 - it.trim),
                        overR.toFloat(),
                        overB.toFloat()
                    )
                }
            } else {
                if (transX > 0) {
                    RectF(
                        overL.toFloat(),
                        overT.toFloat(),
                        overL * (1 - it.trim) + overR * it.trim,
                        overB.toFloat()
                    )
                } else {
                    RectF(
                        overL * it.trim + overR * (1 - it.trim),
                        overT.toFloat(),
                        overR.toFloat(),
                        overB.toFloat()
                    )
                }
            }
            it.path.addRect(over, Path.Direction.CCW)
            it.bound.left =
                if (over.height() == it.height.toFloat() && over.left == it.x.toFloat())
                    over.right else it.x.toFloat()
            it.bound.right =
                if (over.height() == it.height.toFloat() && over.right == it.x.toFloat() + it.width)
                    over.left else it.x.toFloat() + it.width
            it.bound.top =
                if (over.width() == it.width.toFloat() && over.top == it.y.toFloat())
                    over.bottom else it.y.toFloat()
            it.bound.bottom =
                if (over.width() == it.width.toFloat() && over.bottom == it.y.toFloat() + it.width)
                    over.top else it.y.toFloat() + it.height
        }
        offsetX = max(minX * scale, min(maxX * scale - width, offsetX * scale)) / scale
        offsetY = max(minY * scale, min(maxY * scale - height, offsetY * scale)) / scale
    }

    var minX = 0
    var minY = 0
    private var maxX = 0
    private var maxY = 0
    var scale = 0.8f
    private var offsetX = 0f
    private var offsetY = 0f

    val rangeX get() = maxX - minX
    val rangeY get() = maxY - minY

    override fun canScrollHorizontally(): Boolean = true
    override fun canScrollVertically(): Boolean = true

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        val oldX = offsetX * scale
        val newX = max(minX * scale, min(maxX * scale - width, oldX + dx))
        offsetX = newX / scale
        return (newX - oldX).toInt()
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        val oldY = offsetY * scale
        val newY = max(minY * scale, min(maxY * scale - height, oldY + dy))
        offsetY = newY / scale
        return (newY - oldY).toInt()
    }

    override fun computeHorizontalScrollRange(state: RecyclerView.State): Int =
        (scale * rangeX).toInt()

    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int =
        (scale * (offsetX - minX)).toInt()

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int =
        (scale * rangeY).toInt()

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int =
        (scale * (offsetY - minY)).toInt()

    override fun computeHorizontalScrollExtent(state: RecyclerView.State): Int = width
    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = height
}