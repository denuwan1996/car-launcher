package com.example.carlauncher
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/** Static dial (unlit ticks + labels) is pre-rendered ONCE into a bitmap; each frame only draws lit ticks + number. Redraws only when value changes. */
class GaugeView(c: Context, a: AttributeSet?) : View(c, a) {
    private val max = 180f; private val n = 48
    private var shown = 0f; private var target = 0f
    private val tickP = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 7f; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val accent = c.getColor(R.color.accent); private val dim = c.getColor(R.color.text_dim); private val main = c.getColor(R.color.text)
    private var cache: Bitmap? = null; private val pts = FloatArray(n * 4); private var cx = 0f; private var cy = 0f; private var r = 0f

    private val step: (Float) -> Unit = { dt ->
        if (abs(target - shown) > 0.25f) { shown += (target - shown) * min(1f, dt * 8f); invalidate() }
        else if (shown != target) { shown = target; invalidate() } }
    fun setSpeed(kmh: Int) { target = kmh.toFloat().coerceIn(0f, max) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); Ticker.add(step) }
    override fun onDetachedFromWindow() { Ticker.remove(step); super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        cx = w / 2f; cy = h / 2f; r = min(w, h) / 2f - 30f
        cache?.recycle(); cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(cache!!); tickP.color = (dim and 0x00FFFFFF) or 0x55000000
        for (i in 0 until n) {
            val f = i / (n - 1f); val ang = Math.toRadians((135 + 270 * f).toDouble()); val len = 12f + 16f * f
            val x0 = cx + cos(ang).toFloat() * (r - len); val y0 = cy + sin(ang).toFloat() * (r - len)
            val x1 = cx + cos(ang).toFloat() * r; val y1 = cy + sin(ang).toFloat() * r
            pts[i * 4] = x0; pts[i * 4 + 1] = y0; pts[i * 4 + 2] = x1; pts[i * 4 + 3] = y1
            cv.drawLine(x0, y0, x1, y1, tickP) }
        txt.color = dim; txt.textSize = r / 7
        for (v in intArrayOf(0, 60, 120, 180)) { val an = Math.toRadians((135 + 270 * v / max).toDouble()); val rr = r - 48f
            cv.drawText("$v", cx + cos(an).toFloat() * rr, cy + sin(an).toFloat() * rr + r / 20, txt) }
    }
    override fun onDraw(cv: Canvas) {
        cache?.let { cv.drawBitmap(it, 0f, 0f, null) }
        val lit = if (shown > 0.5f) ((shown / max) * (n - 1)).toInt() + 1 else 0
        tickP.color = accent; if (lit > 0) cv.drawLines(pts, 0, min(lit, n) * 4, tickP)
        txt.color = main; txt.textSize = r / 2.1f; txt.isFakeBoldText = true
        cv.drawText(shown.toInt().toString(), cx, cy + r / 6, txt)
        txt.color = dim; txt.textSize = r / 8; txt.isFakeBoldText = false
        cv.drawText("KM/H", cx, cy + r / 2.6f, txt)
    }
}
