package com.drivingcheckin.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 주간 값의 꺾은선과 본인 기준선(점선). 달라진 최근 3주는 강조색으로 그린다. */
@Composable
fun TrendChart(
    values: List<Double?>,
    baseline: Double?,
    highlight: Boolean,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val present = values.filterNotNull()
        if (present.isEmpty()) return@Canvas
        val all = present + listOfNotNull(baseline)
        var lo = all.min()
        var hi = all.max()
        if (hi - lo < 1e-9) { lo -= 1.0; hi += 1.0 }
        val pad = (hi - lo) * 0.2
        lo -= pad; hi += pad

        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        val n = values.size
        fun x(i: Int) = if (n == 1) (left + right) / 2 else left + (right - left) * i / (n - 1)
        fun y(v: Double) = (bottom - (bottom - top) * ((v - lo) / (hi - lo))).toFloat()

        baseline?.let {
            drawLine(
                C.sub, Offset(left, y(it)), Offset(right, y(it)),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
            )
        }

        val points = values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }
        val path = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(path, C.accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { i, p ->
            val hot = highlight && i >= points.size - 3
            drawCircle(C.bg, 9.dp.toPx(), p)
            drawCircle(if (hot) C.warn else C.accent, 6.dp.toPx(), p)
        }

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = C.sub.toArgb()
            textSize = 14.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        listOf(0, n - 1).distinct().forEach { i ->
            paint.textAlign = if (i == 0) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(
                labels[i] + " 주", if (i == 0) left else right, size.height - 6.dp.toPx(), paint
            )
        }
    }
}
