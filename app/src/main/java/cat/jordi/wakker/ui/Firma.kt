package cat.jordi.wakker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Forma
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.Referencia
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/** La firma d'un aparell en text (maqueta 12): quant dura i com és. Null si encara no se'n sap res. */
@Composable
fun textFirma(r: Referencia?): String? {
    if (r == null) return null
    val parts = buildList {
        r.duradaMin?.let { add(stringResource(R.string.firma_minuts, num(it))) }
        r.forma?.let { add(stringResource(it.textId)) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Un dibuixet de com és la potència: constant, polsa, arrencada suau o amb un pic. */
@Composable
fun DibuixForma(forma: Forma, color: Color) {
    Canvas(Modifier.size(width = 26.dp, height = 11.dp)) {
        val w = size.width
        val h = size.height
        val dalt = h * 0.15f
        val baix = h * 0.95f
        val camí = Path().apply {
            moveTo(0f, baix)
            lineTo(w * 0.12f, baix)
            when (forma) {
                Forma.CONSTANT -> {
                    lineTo(w * 0.12f, dalt); lineTo(w * 0.88f, dalt)
                }
                Forma.POLSA -> {
                    var x = w * 0.12f
                    val pas = w * 0.76f / 7
                    for (i in 0 until 7) {
                        val y = if (i % 2 == 0) dalt else baix
                        lineTo(x, y); x += pas; lineTo(x, y)
                    }
                }
                Forma.SUAU -> {
                    cubicTo(w * 0.25f, baix, w * 0.25f, dalt, w * 0.45f, dalt); lineTo(w * 0.88f, dalt)
                }
                Forma.PIC -> {
                    lineTo(w * 0.12f, 0f); lineTo(w * 0.2f, h * 0.45f); lineTo(w * 0.88f, h * 0.45f)
                }
            }
            lineTo(w * 0.88f, baix)
            lineTo(w, baix)
        }
        drawPath(camí, color, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
    }
}

/**
 * Els últims 7 dies sencers (d'avui i els 6 d'abans) en una ratlla, amb un punt per a cada cop que ha passat una cosa i
 * el nom de cada dia a sota.
 */
@Composable
fun TiraSetmana(cops: List<Long>, color: Color, descripcio: String) {
    val zona = ZoneId.systemDefault()
    val primer = LocalDate.now(zona).minusDays(6)
    val dies = (0..7).map { primer.plusDays(it.toLong()).atStartOfDay(zona).toEpochSecond() }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).semantics(mergeDescendants = true) { contentDescription = descripcio }) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            // Cada dia ocupa el mateix, també els de 23 o 25 hores
            fun x(ts: Long): Float {
                val d = dies.indexOfLast { it <= ts }.coerceIn(0, 6)
                return (d + (ts - dies[d]).toFloat() / (dies[d + 1] - dies[d])) / 7 * size.width
            }
            for (d in 1 until 7) drawLine(Separador, Offset(d * size.width / 7, 0f), Offset(d * size.width / 7, size.height), strokeWidth = 1.dp.toPx())
            cops.filter { it >= dies.first() && it < dies.last() }
                .forEach { drawCircle(color.copy(alpha = 0.75f), 2.6.dp.toPx(), Offset(x(it), size.height / 2)) }
        }
        Row(Modifier.fillMaxWidth()) {
            for (d in 0 until 7) {
                Text(
                    primer.plusDays(d.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Idioma.locale).removeSuffix("."),
                    Modifier.weight(1f), fontSize = 10.sp, color = Suau, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
