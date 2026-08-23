package com.clarion.app.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private const val STROKE = 5.5f

@Composable
fun HomeTabIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.08f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.92f, h * 0.5f)
        }
        drawPath(path, color = color, style = stroke)
        drawRect(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.46f),
            size = Size(w * 0.56f, h * 0.46f),
            style = stroke,
        )
    }
}

@Composable
fun MapTabIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        drawCircle(color = color, radius = w * 0.15f, center = Offset(cx, h * 0.34f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, h * 0.94f)
            cubicTo(cx - w * 0.34f, h * 0.55f, cx - w * 0.3f, h * 0.1f, cx, h * 0.1f)
            cubicTo(cx + w * 0.3f, h * 0.1f, cx + w * 0.34f, h * 0.55f, cx, h * 0.94f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun CirclesTabIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = STROKE, cap = StrokeCap.Round)
        drawCircle(color = color, radius = w * 0.15f, center = Offset(w * 0.36f, h * 0.32f), style = stroke)
        drawCircle(color = color, radius = w * 0.13f, center = Offset(w * 0.74f, h * 0.4f), style = stroke)
        val bodyPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.08f, h * 0.92f)
            cubicTo(w * 0.08f, h * 0.62f, w * 0.18f, h * 0.55f, w * 0.36f, h * 0.55f)
            cubicTo(w * 0.54f, h * 0.55f, w * 0.64f, h * 0.62f, w * 0.64f, h * 0.92f)
        }
        drawPath(bodyPath, color = color, style = stroke)
        val bodyPath2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.58f, h * 0.92f)
            cubicTo(w * 0.58f, h * 0.7f, w * 0.65f, h * 0.63f, w * 0.74f, h * 0.63f)
            cubicTo(w * 0.86f, h * 0.63f, w * 0.94f, h * 0.7f, w * 0.94f, h * 0.9f)
        }
        drawPath(bodyPath2, color = color, style = stroke)
    }
}

@Composable
fun ProfileTabIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = STROKE, cap = StrokeCap.Round)
        drawCircle(color = color, radius = w * 0.19f, center = Offset(w * 0.5f, h * 0.28f), style = stroke)
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.12f, h * 0.94f)
            cubicTo(w * 0.12f, h * 0.62f, w * 0.28f, h * 0.5f, w * 0.5f, h * 0.5f)
            cubicTo(w * 0.72f, h * 0.5f, w * 0.88f, h * 0.62f, w * 0.88f, h * 0.94f)
        }
        drawPath(body, color = color, style = stroke)
    }
}
