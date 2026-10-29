package com.dextray.melklamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Круг «оттенок × насыщенность». Яркость вынесена в отдельный слайдер,
 * потому что у лампы это отдельная команда.
 */
@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    onChange: (hue: Float, saturation: Float) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColors = remember { List(7) { Color.hsv((it * 60f).coerceAtMost(360f), 1f, 1f) } }
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnFinished by rememberUpdatedState(onChangeFinished)
    val selected = Color.hsv(hue, saturation, 1f)
    val outline = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                fun pick(pos: Offset) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = minOf(size.width, size.height) / 2f
                    val d = pos - c
                    var deg = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat()
                    if (deg < 0f) deg += 360f
                    currentOnChange(deg, (hypot(d.x, d.y) / r).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pick(down.position)
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            pressed = false
                        } else {
                            pick(change.position)
                            change.consume()
                        }
                    }
                    currentOnFinished()
                }
            },
    ) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(brush = Brush.sweepGradient(colors = hueColors, center = c), radius = r, center = c)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
        drawCircle(color = outline, radius = r, center = c, style = Stroke(width = 2.dp.toPx()))

        val a = Math.toRadians(hue.toDouble())
        val p = Offset(
            c.x + (cos(a) * saturation * r).toFloat(),
            c.y + (sin(a) * saturation * r).toFloat(),
        )
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = 20.dp.toPx(), center = p)
        drawCircle(Color.White, radius = 17.dp.toPx(), center = p)
        drawCircle(selected, radius = 13.dp.toPx(), center = p)
    }
}
