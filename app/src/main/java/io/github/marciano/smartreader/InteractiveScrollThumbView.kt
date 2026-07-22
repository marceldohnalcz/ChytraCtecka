package io.github.marciano.smartreader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import androidx.core.content.ContextCompat

/**
 * Vlastní posuvník pro [EditText] - nahrazuje jak systémový
 * `android:scrollbars` (jen vizuální indikátor, nejde chytit prstem), tak
 * dřívější neviditelnou dotykovou vrstvu (`scrollDragHandle`). Kreslí si
 * pilulku sám, takže ji může animovat: v klidu je úzká, při podržení prstu
 * se plynule roztáhne a po puštění se zase zúží.
 *
 * Použití: nastavit [targetEditText], zavolat [refresh] po každé změně
 * scrollu (i té, kterou vyvolal někdo jiný, např. auto-scroll při čtení).
 */
class InteractiveScrollThumbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var targetEditText: EditText? = null
    private var onDragStateChanged: ((Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val restWidthPx = 6f * density
    private val expandedWidthPx = 14f * density
    private val minThumbHeightPx = 28f * density
    // O kolik rychleji se text posune oproti tomu, o kolik se posune prst.
    private val dragSpeedMultiplier = 3.5f

    private var currentWidthPx = restWidthPx
    private var widthAnimator: ValueAnimator? = null
    private var lastTouchY = 0f

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val editText = targetEditText ?: return
        val layout = editText.layout ?: return
        val visibleHeight = editText.height - editText.paddingTop - editText.paddingBottom
        val contentHeight = layout.height
        if (visibleHeight <= 0 || contentHeight <= visibleHeight) return

        val trackHeight = height.toFloat()
        val thumbHeightRatio = (visibleHeight.toFloat() / contentHeight).coerceIn(0.04f, 1f)
        val thumbHeight = (trackHeight * thumbHeightRatio).coerceAtLeast(minThumbHeightPx)

        val maxScroll = (contentHeight - visibleHeight).coerceAtLeast(1)
        val scrollRatio = (editText.scrollY.toFloat() / maxScroll).coerceIn(0f, 1f)
        val thumbTop = (trackHeight - thumbHeight) * scrollRatio

        val left = width - currentWidthPx
        val radius = currentWidthPx / 2f
        canvas.drawRoundRect(
            RectF(left, thumbTop, width.toFloat(), thumbTop + thumbHeight),
            radius, radius, thumbPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val editText = targetEditText ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                animateWidthTo(expandedWidthPx)
                onDragStateChanged?.invoke(true)
                scrollToTouchRatio(editText, event.y / height.toFloat())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - lastTouchY
                lastTouchY = event.y
                scrollByDelta(editText, deltaY * dragSpeedMultiplier)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateWidthTo(restWidthPx)
                onDragStateChanged?.invoke(false)
                return true
            }
        }
        return false
    }

    private fun animateWidthTo(target: Float) {
        widthAnimator?.cancel()
        widthAnimator = ValueAnimator.ofFloat(currentWidthPx, target).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentWidthPx = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun scrollToTouchRatio(editText: EditText, rawRatio: Float) {
        val layout = editText.layout ?: return
        val visibleHeight = editText.height - editText.paddingTop - editText.paddingBottom
        val maxScroll = (layout.height - visibleHeight).coerceAtLeast(0)
        if (maxScroll <= 0) return
        editText.scrollTo(0, (rawRatio.coerceIn(0f, 1f) * maxScroll).toInt())
        invalidate()
    }

    private fun scrollByDelta(editText: EditText, deltaY: Float) {
        val layout = editText.layout ?: return
        val visibleHeight = editText.height - editText.paddingTop - editText.paddingBottom
        val maxScroll = (layout.height - visibleHeight).coerceAtLeast(0)
        if (maxScroll <= 0) return
        val newScroll = (editText.scrollY + deltaY.toInt()).coerceIn(0, maxScroll)
        editText.scrollTo(0, newScroll)
        invalidate()
    }

    /** Zavolat po jakékoli změně scrollu textového pole (i té vyvolané odjinud). */
    fun refresh() = invalidate()

    fun setOnDragStateChanged(listener: (Boolean) -> Unit) {
        onDragStateChanged = listener
    }
}
