package com.jiexian.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jiexian.app.ui.theme.Forest

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    color: Color = Forest,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.065f
        drawArc(
            color = color,
            startAngle = 38f,
            sweepAngle = 296f,
            useCenter = false,
            topLeft = Offset(stroke * 0.7f, stroke * 0.7f),
            size = this.size.copy(width = this.size.width - stroke * 1.4f, height = this.size.height - stroke * 1.4f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val stem =
            Path().apply {
                moveTo(this@Canvas.size.width * 0.43f, this@Canvas.size.height * 0.65f)
                cubicTo(
                    this@Canvas.size.width * 0.47f,
                    this@Canvas.size.height * 0.49f,
                    this@Canvas.size.width * 0.58f,
                    this@Canvas.size.height * 0.39f,
                    this@Canvas.size.width * 0.7f,
                    this@Canvas.size.height * 0.34f,
                )
            }
        drawPath(stem, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawCircle(
            color = color,
            radius = stroke * 0.55f,
            center = Offset(this.size.width * 0.38f, this.size.height * 0.7f),
        )
    }
}
