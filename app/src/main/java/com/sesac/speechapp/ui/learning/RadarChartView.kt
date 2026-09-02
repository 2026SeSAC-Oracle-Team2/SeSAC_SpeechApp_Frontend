package com.sesac.speechapp.ui.learning

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * P3-26 방사형(레이더) 그래프 — 커스텀 Canvas (외부 라이브러리 없음).
 *
 * 4축: LISTEN / NAMING / SHADOWING / SELF_TALK (지시문 §3-4 확정 축)
 * 각 축에 지표 평균 점수(0~100) 배치. 데이터 미제출 축은 0으로 그려짐.
 *
 * 사용: radarChartView.setData(listOf("알아듣기" to 80f, "이름대기" to 65f, ...))
 *      4개 순서대로 전달 (시계방향 위쪽부터).
 */
class RadarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class AxisData(val label: String, val value: Float)

    private var axes: List<AxisData> = emptyList()

    // 격자/다각형 페인트
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D8DFEA")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#664A90D9")
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A90D9")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
    }
    private val vertexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A7BC8")
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A2E")
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A7BC8")
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val path = Path()

    fun setData(data: List<AxisData>) {
        axes = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (axes.size < 3) return

        val cx = width / 2f
        val cy = height / 2f
        val labelSpace = 56f
        val radius = min(width, height) / 2f - labelSpace
        val n = axes.size

        // 1) 동심원 격자 4단 (25/50/75/100)
        for (ring in 1..4) {
            path.reset()
            val r = radius * ring / 4f
            for (i in 0 until n) {
                val angle = Math.PI * 2 * i / n - Math.PI / 2
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, gridPaint)
        }

        // 2) 축 선
        for (i in 0 until n) {
            val angle = Math.PI * 2 * i / n - Math.PI / 2
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            canvas.drawLine(cx, cy, x, y, gridPaint)
        }

        // 3) 데이터 다각형 (값 0~100 정규화)
        path.reset()
        axes.forEachIndexed { i, axis ->
            val clamped = axis.value.coerceIn(0f, 100f)
            val r = radius * clamped / 100f
            val angle = Math.PI * 2 * i / n - Math.PI / 2
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        // 4) 꼭짓점 + 값 라벨
        axes.forEachIndexed { i, axis ->
            val clamped = axis.value.coerceIn(0f, 100f)
            val r = radius * clamped / 100f
            val angle = Math.PI * 2 * i / n - Math.PI / 2
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            canvas.drawCircle(x, y, 7f, vertexPaint)

            // 값 표시 (꼭짓점 근처)
            val valueX = cx + ((r + 30f) * cos(angle)).toFloat()
            val valueY = cy + ((r + 30f) * sin(angle)).toFloat() + 10f
            canvas.drawText(axis.value.toInt().toString(), valueX, valueY, valuePaint)

            // 축 라벨 (바깥쪽)
            val labelX = cx + ((radius + 36f) * cos(angle)).toFloat()
            val labelY = cy + ((radius + 36f) * sin(angle)).toFloat() + 12f
            canvas.drawText(axis.label, labelX, labelY, labelPaint)
        }
    }
}