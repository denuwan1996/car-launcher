package com.example.carlauncher
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

/** Animated car on the shared Ticker. LOW tier: no reflection/pulse, and NO redraw while parked (zero idle CPU). */
class CarView(c: Context, a: AttributeSet?) : View(c, a) {
    enum class Variant(val color: Int) { BLACK(0xFF15181D.toInt()), GRAY(0xFF8A9099.toInt()), MAROON(0xFF6B1F2B.toInt()), WHITE(0xFFF2F4F7.toInt()), BLUE(0xFF2F6FDB.toInt()) }
    private val WHEEL_X = floatArrayOf(0.19f, 0.80f); private val WHEEL_Y = 0.80f; private val WHEEL_R = 0.085f; private val RATIO = 2.1f
    var variant = Variant.BLACK; set(v) { field = v; body?.colorFilter = PorterDuffColorFilter(v.color, PorterDuff.Mode.SRC_ATOP); invalidate() }
    var speed = 0
    var lightsOn = false
    private val body = ContextCompat.getDrawable(c, R.drawable.car_body)?.mutate()
    private val glass = ContextCompat.getDrawable(c, R.drawable.car_glass)
    private val wheel = ContextCompat.getDrawable(c, R.drawable.car_wheel)
    private val lights = ContextCompat.getDrawable(c, R.drawable.car_lights)?.mutate()
    private val accent = c.getColor(R.color.accent); private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f; private var pulse = 0.5f; private var wheelAngle = 0f; private var intro = 0f; private var lightsAmt = 0f
    private var ground = 0f; private var bar: Shader? = null; private var fade: Shader? = null
    init { variant = Variant.BLACK }

    private val tick: (Float) -> Unit = { dt ->
        t += dt; var dirty = false
        if (intro < 1f) { intro = min(1f, intro + dt / 1.1f); dirty = true }
        val lt = if (lightsOn) 1f else 0f
        if (lightsAmt != lt) { lightsAmt += sign(lt - lightsAmt) * dt * 2.5f; if (abs(lt - lightsAmt) < 0.05f) lightsAmt = lt; dirty = true }
        if (speed > 0) { wheelAngle = (wheelAngle + speed * 6f * dt) % 360f; dirty = true }
        if (DeviceTier.fancy) { pulse = sin(t * 2.6f) * 0.5f + 0.5f; dirty = true }
        if (dirty) invalidate() }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); Ticker.add(tick) }
    override fun onDetachedFromWindow() { Ticker.remove(tick); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); if (v == VISIBLE) Ticker.add(tick) else Ticker.remove(tick) }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        ground = h * 0.84f
        bar = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(0, accent, accent, 0), floatArrayOf(0f, .25f, .75f, 1f), Shader.TileMode.CLAMP)
        fade = LinearGradient(0f, ground - 60f, 0f, ground, 0, (accent and 0x00FFFFFF) or 0x55000000, Shader.TileMode.CLAMP) }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cw = min(w * 0.92f, h * 0.85f * RATIO); val ch = cw / RATIO
        val e = 1f - (1f - intro).pow(3)
        val left = (w - cw) / 2 - (1f - e) * w * 0.6f
        val top = ground - ch * 0.93f + sin(t * 2.6f) * (if (speed > 0) 2.5f else 1.2f)
        glowP.shader = bar; glowP.alpha = (110 + 120 * pulse).toInt(); cv.drawRect(0f, ground - 2f, w, ground + 2f, glowP)
        glowP.shader = fade; glowP.alpha = (70 + 100 * pulse).toInt(); cv.drawRect(w * .1f, ground - 60f, w * .9f, ground, glowP)
        val r = Rect(left.toInt(), top.toInt(), (left + cw).toInt(), (top + ch).toInt())
        if (DeviceTier.fancy) { cv.save(); cv.scale(1f, -1f, w / 2, ground); body?.alpha = 45; body?.bounds = r; body?.draw(cv); body?.alpha = 255; cv.restore() }
        body?.bounds = r; body?.draw(cv); glass?.bounds = r; glass?.draw(cv)
        if (lightsAmt > 0.01f) { lights?.alpha = (lightsAmt * 255).toInt(); lights?.bounds = r; lights?.draw(cv) }
        val wr = cw * WHEEL_R
        for (fx in WHEEL_X) { val cx = left + cw * fx; val cy = top + ch * WHEEL_Y
            cv.save(); cv.rotate(wheelAngle, cx, cy)
            wheel?.setBounds((cx - wr).toInt(), (cy - wr).toInt(), (cx + wr).toInt(), (cy + wr).toInt()); wheel?.draw(cv); cv.restore() }
    }
}
