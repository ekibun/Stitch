package soko.ekibun.stitch

import android.graphics.Path
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

    fun updateRange() {
        var lastX = 0
        var lastY = 0
        var lastW = 0
        var lastH = 0
        maxX = 0
        maxY = 0
        minX = 0
        minY = 0
        App.stitchInfo.forEach {
            it.dx = it.dx.coerceIn(-1f, 1f)
            it.dy = it.dy.coerceIn(-1f, 1f)
            it.x = lastX + (it.dx * lastW).roundToInt()
            it.y = lastY + (it.dy * lastH).roundToInt()
            val transX = it.x + it.width / 2f - lastX - lastW / 2f
            val transY = it.y + it.height / 2f - lastY - lastH / 2f

            val overL = max(it.x, lastX)
            val overT = max(it.y, lastY)
            val overR = min(it.x + it.width, lastX + lastW)
            val overB = min(it.y + it.height, lastY + lastH)
            lastX = it.x
            lastY = it.y

            minX = min(minX, lastX)
            minY = min(minY, lastY)
            maxX = max(lastX + it.width, maxX)
            maxY = max(lastY + it.height, maxY)
            // computePath
            it.path.reset()
            it.path.addRect(
                it.x.toFloat(),
                it.y.toFloat(),
                it.x + it.width.toFloat(),
                it.y + it.height.toFloat(),
                Path.Direction.CW
            )
            if (abs(transX) < abs(transY)) {
                if (transY > 0) {
                    it.path.addRect(
                        overL.toFloat(),
                        overT.toFloat(),
                        overR.toFloat(),
                        overT * (1 - it.trim) + overB * it.trim,
                        Path.Direction.CCW
                    )
                } else {
                    it.path.addRect(
                        overL.toFloat(),
                        overT * it.trim + overB * (1 - it.trim),
                        overR.toFloat(),
                        overB.toFloat(),
                        Path.Direction.CCW
                    )
                }
            } else {
                if (transX > 0) {
                    it.path.addRect(
                        overL.toFloat(),
                        overT.toFloat(),
                        overL * (1 - it.trim) + overR * it.trim,
                        overB.toFloat(),
                        Path.Direction.CCW
                    )
                } else {
                    it.path.addRect(
                        it.x + overL * it.trim + overR * (1 - it.trim),
                        overT.toFloat(),
                        overR.toFloat(),
                        overB.toFloat(),
                        Path.Direction.CCW
                    )
                }
            }
            lastW = it.width
            lastH = it.height
        }
        offsetX = max(minX * scale, min(maxX * scale - width, offsetX))
        offsetY = max(minY * scale, min(maxY * scale - width, offsetY))
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