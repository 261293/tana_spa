package com.tana.spa.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The line icons the design uses, drawn rather than shipped: Material's icon artifact is not on
 * the classpath and a vector asset per glyph would be a file each. Every shape is laid out on the
 * same 24-unit grid and stroked, so they stay a consistent weight at any size.
 */
enum class SpaIcon {
    ArrowRight,
    Bell,
    Calendar,
    CalendarCheck,
    CalendarPlus,
    Check,
    CheckCircle,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Clock,
    Close,
    Copy,
    CreditCard,
    External,
    Gear,
    Globe,
    Hourglass,
    Info,
    Leaf,
    Lotus,
    Note,
    Pencil,
    Percent,
    Phone,
    Plus,
    Prohibit,
    SignOut,
    Tag,
    Ticket,
    Warning,
}

@Composable
fun SpaIconImage(
    icon: SpaIcon,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    val described = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(described.size(size)) { drawSpaIcon(icon, tint) }
}

private fun DrawScope.drawSpaIcon(icon: SpaIcon, tint: Color) {
    val u = size.minDimension / 24f
    val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = stroke.width, cap = StrokeCap.Round)

    fun dot(x: Float, y: Float, r: Float) = drawCircle(tint, r * u, Offset(x * u, y * u))

    fun circle(x: Float, y: Float, r: Float) = drawCircle(tint, r * u, Offset(x * u, y * u), style = stroke)

    fun box(x: Float, y: Float, w: Float, h: Float, r: Float) = drawRoundRect(
        tint,
        Offset(x * u, y * u),
        Size(w * u, h * u),
        CornerRadius(r * u, r * u),
        style = stroke,
    )

    fun path(build: Path.() -> Unit) {
        val p = Path()
        p.build()
        drawPath(p, tint, style = stroke)
    }

    fun Path.at(x: Float, y: Float) = moveTo(x * u, y * u)
    fun Path.to(x: Float, y: Float) = lineTo(x * u, y * u)
    fun Path.curve(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(x1 * u, y1 * u, x2 * u, y2 * u, x3 * u, y3 * u)

    fun calendarBody() {
        box(3f, 5f, 18f, 16f, 4f)
        line(3f, 10f, 21f, 10f)
        line(8f, 3f, 8f, 7f)
        line(16f, 3f, 16f, 7f)
    }

    fun chevron() = path {
        at(9.5f, 5f)
        to(16.5f, 12f)
        to(9.5f, 19f)
    }

    when (icon) {
        SpaIcon.ArrowRight -> {
            line(4f, 12f, 19f, 12f)
            path {
                at(13.5f, 6f)
                to(19.5f, 12f)
                to(13.5f, 18f)
            }
        }

        SpaIcon.Bell -> {
            path {
                at(6f, 16.5f)
                to(6f, 11f)
                curve(6f, 7.1f, 8.7f, 4f, 12f, 4f)
                curve(15.3f, 4f, 18f, 7.1f, 18f, 11f)
                to(18f, 16.5f)
            }
            line(4.5f, 16.5f, 19.5f, 16.5f)
            path {
                at(10f, 19f)
                curve(10.6f, 20.4f, 13.4f, 20.4f, 14f, 19f)
            }
        }

        SpaIcon.Calendar -> calendarBody()

        SpaIcon.CalendarCheck -> {
            calendarBody()
            path {
                at(8.5f, 15.5f)
                to(11f, 18f)
                to(15.5f, 13.5f)
            }
        }

        SpaIcon.CalendarPlus -> {
            calendarBody()
            line(9f, 15.5f, 15f, 15.5f)
            line(12f, 12.5f, 12f, 18.5f)
        }

        SpaIcon.Check -> path {
            at(5f, 12.5f)
            to(10f, 17.5f)
            to(19f, 7f)
        }

        SpaIcon.CheckCircle -> {
            circle(12f, 12f, 9f)
            path {
                at(7.5f, 12.3f)
                to(10.8f, 15.6f)
                to(16.5f, 9.3f)
            }
        }

        SpaIcon.ChevronDown -> rotate(90f) { chevron() }

        SpaIcon.ChevronLeft -> rotate(180f) { chevron() }

        SpaIcon.ChevronRight -> chevron()

        SpaIcon.Clock -> {
            circle(12f, 12f, 9f)
            line(12f, 12f, 12f, 7f)
            line(12f, 12f, 16f, 14f)
        }

        SpaIcon.Close -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }

        SpaIcon.Copy -> {
            box(8f, 8f, 13f, 13f, 3f)
            path {
                at(16f, 5.5f)
                to(16f, 3f)
                to(3f, 3f)
                to(3f, 16f)
                to(5.5f, 16f)
            }
        }

        SpaIcon.CreditCard -> {
            box(3f, 5.5f, 18f, 13f, 3f)
            line(3f, 10f, 21f, 10f)
            line(6.5f, 14.5f, 10.5f, 14.5f)
        }

        SpaIcon.External -> {
            path {
                at(14f, 3.5f)
                to(20.5f, 3.5f)
                to(20.5f, 10f)
            }
            line(20.5f, 3.5f, 12f, 12f)
            path {
                at(17f, 14f)
                to(17f, 20.5f)
                to(3.5f, 20.5f)
                to(3.5f, 7f)
                to(10f, 7f)
            }
        }

        SpaIcon.Gear -> {
            // A ring with eight teeth: radiating lines alone read as a sun, not a gear.
            circle(12f, 12f, 6.3f)
            circle(12f, 12f, 2.4f)
            repeat(8) { i ->
                val a = (i * 45f + 22.5f) * (3.14159265f / 180f)
                line(12f + 5.8f * cos(a), 12f + 5.8f * sin(a), 12f + 8.7f * cos(a), 12f + 8.7f * sin(a))
            }
        }

        SpaIcon.Globe -> {
            circle(12f, 12f, 9f)
            drawOval(tint, Offset(7.5f * u, 3f * u), Size(9f * u, 18f * u), style = stroke)
            line(3.2f, 12f, 20.8f, 12f)
        }

        SpaIcon.Hourglass -> path {
            at(6f, 4f)
            to(18f, 4f)
            to(12f, 12f)
            to(18f, 20f)
            to(6f, 20f)
            to(12f, 12f)
            close()
        }

        SpaIcon.Info -> {
            circle(12f, 12f, 9f)
            line(12f, 11.5f, 12f, 16.5f)
            dot(12f, 8f, 1.1f)
        }

        SpaIcon.Leaf -> {
            path {
                at(4f, 20f)
                curve(4f, 10f, 10f, 4f, 20f, 4f)
                curve(20f, 14f, 14f, 20f, 4f, 20f)
                close()
            }
            line(4.5f, 19.5f, 13.5f, 10.5f)
        }

        SpaIcon.Lotus -> {
            path {
                at(12f, 20.5f)
                curve(8f, 16f, 8f, 9f, 12f, 3.5f)
                curve(16f, 9f, 16f, 16f, 12f, 20.5f)
                close()
            }
            path {
                at(12f, 20.5f)
                curve(6f, 18.5f, 3.5f, 13.5f, 4f, 8.5f)
                curve(9f, 10f, 11.5f, 15f, 12f, 20.5f)
            }
            path {
                at(12f, 20.5f)
                curve(18f, 18.5f, 20.5f, 13.5f, 20f, 8.5f)
                curve(15f, 10f, 12.5f, 15f, 12f, 20.5f)
            }
        }

        SpaIcon.Note -> {
            box(4f, 3.5f, 16f, 17f, 3f)
            line(8f, 9f, 16f, 9f)
            line(8f, 13f, 16f, 13f)
            line(8f, 17f, 13f, 17f)
        }

        SpaIcon.Pencil -> {
            path {
                at(4f, 20f)
                to(5f, 15.5f)
                to(16.5f, 4f)
                to(20f, 7.5f)
                to(8.5f, 19f)
                close()
            }
            line(14f, 6.5f, 17.5f, 10f)
        }

        SpaIcon.Percent -> {
            circle(8f, 8f, 2.6f)
            circle(16f, 16f, 2.6f)
            line(18.5f, 5.5f, 5.5f, 18.5f)
        }

        SpaIcon.Phone -> {
            box(7f, 2.5f, 10f, 19f, 3f)
            dot(12f, 18.5f, 1f)
        }

        SpaIcon.Plus -> {
            line(12f, 5f, 12f, 19f)
            line(5f, 12f, 19f, 12f)
        }

        SpaIcon.Prohibit -> {
            circle(12f, 12f, 9f)
            line(5.6f, 18.4f, 18.4f, 5.6f)
        }

        SpaIcon.SignOut -> {
            path {
                at(13f, 4.5f)
                to(5.5f, 4.5f)
                to(5.5f, 19.5f)
                to(13f, 19.5f)
            }
            line(10.5f, 12f, 20.5f, 12f)
            path {
                at(17f, 8.5f)
                to(20.5f, 12f)
                to(17f, 15.5f)
            }
        }

        SpaIcon.Tag -> {
            path {
                at(3.5f, 10.5f)
                to(3.5f, 4f)
                to(10f, 4f)
                to(20f, 14f)
                to(13.5f, 20.5f)
                close()
            }
            dot(7.4f, 7.9f, 1.3f)
        }

        SpaIcon.Ticket -> {
            box(2.5f, 6f, 19f, 12f, 3f)
            line(12f, 7.5f, 12f, 9.5f)
            line(12f, 11f, 12f, 13f)
            line(12f, 14.5f, 12f, 16.5f)
        }

        SpaIcon.Warning -> {
            circle(12f, 12f, 9f)
            line(12f, 7.5f, 12f, 13f)
            dot(12f, 16.4f, 1.1f)
        }
    }
}
