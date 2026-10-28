package cat.jordi.wakker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Estona
import cat.jordi.wakker.dades.Punt
import cat.jordi.wakker.dades.nivellPotencia
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/** Un pas rodó per a l'eix (en les unitats que sigui), perquè hi hagi entre 3 i 5 ratlles. */
private fun pas(maxim: Double): Double {
    val passos = listOf(0.1, 0.2, 0.25, 0.5, 1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0)
    return passos.firstOrNull { maxim / it <= 4 } ?: (ceil(maxim / 4 / 100) * 100)
}

/** Les etiquetes dels eixos: petites i apagades (maqueta 18). */
private val EstilEix get() = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = Eix, fontFeatureSettings = "tnum")

/**
 * La potència d'una estona, amb l'eix en kW a l'esquerra i les hores a sota.
 * - El total, en àrea i línia: contínua on les dades són exactes, i de punts on són aproximades (el núvol).
 * - [suavitza]: la mitjana mòbil de tants minuts (les 24 hores d'Ara, a 5, perquè no sigui soroll minut a minut).
 * - [escalaDades]: l'eix a l'escala del que hi ha (un dia tranquil, fins a 1 kW), no de la potència contractada. Les ratlles
 *   del 80 i el 90 % només surten si cauen dins; si no, [limitKw] només pinta els trams que hi passen.
 * - [marcaAra]: el punt i la ratlla d'«ara» a l'últim valor.
 * - La línia de la segona pinça, més clara.
 * - [marques]: ratlles verticals amb una etiqueta (els salts d'abans d'un pic), i [pic], un punt vermell.
 */
@Composable
fun GraficaPotencia(
    serie: List<Punt>, ambLinia: Boolean, limitKw: Double?,
    marques: List<Pair<Long, String>> = emptyList(), pic: Long? = null,
    suavitza: Int = 1, escalaDades: Boolean = false, marcaAra: Boolean = false,
) {
    val mesura = rememberTextMeasurer()
    val context = LocalContext.current
    val senseDades = stringResource(R.string.grafiques_sense_dades)
    val ara = stringResource(R.string.grafiques_ara)
    val contractats = limitKw?.let { stringResource(R.string.grafiques_contractats, num(it, 2)) }
    val maxPunt = serie.maxByOrNull { it.wTotal }
    val descripcio = if (maxPunt != null) stringResource(
        R.string.grafiques_desc_potencia, num(maxPunt.wTotal / 1000, 1), horaMinut(maxPunt.ts), num(serie.last().wTotal / 1000, 1),
    ) else ""
    val suau = if (suavitza > 1 && serie.size > suavitza) serie.indices.map { i ->
        val de = maxOf(0, i - suavitza / 2)
        val a = minOf(serie.size - 1, i + suavitza / 2)
        val tros = serie.subList(de, a + 1)
        serie[i].copy(wTotal = tros.sumOf { it.wTotal } / tros.size, wLinia = tros.sumOf { it.wLinia } / tros.size)
    } else serie
    Canvas(Modifier.fillMaxWidth().height(if (escalaDades) 130.dp else 170.dp).semantics { contentDescription = descripcio }) {
        val peu = 18.dp.toPx()
        val dalt = if (marcaAra) 10.dp.toPx() else 0f
        val alt = size.height - peu
        if (serie.size < 2) {
            drawText(mesura, senseDades, Offset(0f, alt / 2), EstilEix)
            return@Canvas
        }
        val limitW = limitKw?.times(1000)
        val maxDades = suau.maxOf { it.wTotal }
        val maxim = if (escalaDades) maxOf(maxDades * 1.05, 200.0) else maxOf(maxDades, (limitW ?: 0.0) * 1.08, 200.0)
        val pasKw = pas(maxim / 1000)
        val ymax = ceil(maxim / 1000 / pasKw) * pasKw * 1000
        val etiquetes = generateSequence(0.0) { it + pasKw }.takeWhile { it <= ymax / 1000 + 1e-9 }
            .map { kw -> kw to context.getString(R.string.grafiques_kw, num(kw, if (pasKw < 1) 1 else 0)) }.toList()
        val marge = etiquetes.maxOf { mesura.measure(it.second, EstilEix).size.width } + 6.dp.toPx()
        val ample = size.width - marge
        val t0 = suau.first().ts.toFloat()
        val t1 = suau.last().ts.toFloat().coerceAtLeast(t0 + 1)
        fun x(ts: Long) = marge + (ts - t0) / (t1 - t0) * ample
        fun y(w: Double) = dalt + (alt - dalt) - (w / ymax).toFloat().coerceIn(0f, 1f) * (alt - dalt)

        // L'eix en kW (el zero, sense etiqueta si l'eix és a l'escala de les dades)
        for ((kw, text) in etiquetes) {
            val yy = y(kw * 1000)
            if (kw > 0) drawLine(Graella, Offset(marge, yy), Offset(size.width, yy))
            if (kw > 0 || !escalaDades) {
                val mida = mesura.measure(text, EstilEix).size
                drawText(mesura, text, Offset(marge - 4.dp.toPx() - mida.width, yy - mida.height / 2f), EstilEix)
            }
        }
        drawLine(GrisBarra, Offset(marge, alt), Offset(size.width, alt))

        // L'àrea, per trams exactes i aproximats
        var i = 0
        while (i < suau.size - 1) {
            val aprox = suau[i + 1].aprox
            var j = i + 1
            while (j < suau.size - 1 && suau[j + 1].aprox == aprox) j++
            val area = Path().apply {
                moveTo(x(suau[i].ts), alt)
                for (k in i..j) lineTo(x(suau[k].ts), y(suau[k].wTotal))
                lineTo(x(suau[j].ts), alt)
                close()
            }
            drawPath(area, Primari.copy(alpha = if (aprox) 0.07f else 0.14f))
            i = j
        }

        // Les ratlles de la potència contractada, si cauen dins de l'escala. Les etiquetes, de dalt a baix, i només si no
        // trepitgen la d'abans: amb la lletra gran, «90 %», «80 %» i «X kW contractats» se solapaven (1.1.3)
        if (limitW != null && contractats != null) {
            // «ara» va a dalt a la dreta: cap etiqueta no hi pot anar a sobre (1.1.3)
            var ocupatFins = if (marcaAra) mesura.measure(ara, EstilEix.copy(fontWeight = FontWeight.SemiBold)).size.height.toFloat() else -1f
            fun etiqueta(text: String, yy: Float, estil: TextStyle) {
                val mida = mesura.measure(text, estil).size
                // A sobre de la ratlla; si no hi cap (hi ha la d'abans, o «ara»), a sota; si tampoc, no hi va
                val d = (yy - mida.height - 1.dp.toPx()).takeIf { it >= ocupatFins }
                    ?: (yy + 1.dp.toPx()).takeIf { it >= ocupatFins && it + mida.height <= alt }
                    ?: return
                // Amb el fons de la targeta: una ratlla que hi passi per sota no talla el text
                drawRect(Superficie, Offset(size.width - mida.width - 2.dp.toPx(), d), androidx.compose.ui.geometry.Size(mida.width + 2.dp.toPx(), mida.height.toFloat()))
                drawText(mesura, text, Offset(size.width - mida.width, d), estil)
                ocupatFins = d + mida.height
            }
            // Primer totes les ratlles i despr\u00E9s les etiquetes: si no, la del 90 % travessava l'etiqueta de la contractada
            val etiquetes = mutableListOf<Triple<String, Float, TextStyle>>()
            if (limitW <= ymax) {
                val yy = y(limitW)
                drawLine(Eix, Offset(marge, yy), Offset(size.width, yy), 1.dp.toPx())
                etiquetes += Triple(contractats, yy, EstilEix)
            }
            for ((part, color) in listOf(0.9 to PotenciaLimit, 0.8 to PotenciaAlta)) {
                if (limitW * part > ymax) continue
                val yy = y(limitW * part)
                drawLine(color, Offset(marge, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)))
                etiquetes += Triple("${num(part * 100)}\u00A0%", yy, EstilEix.copy(color = color))
            }
            for ((text, yy, estil) in etiquetes) etiqueta(text, yy, estil)
        }

        clipRect(marge, 0f, size.width, alt) {
            liniaNivells(suau, limitKw, ::x) { y(it.wTotal) }
            if (ambLinia) liniaPunts(suau, PrimariClar, ::x) { y(it.wLinia) }
        }

        // Els salts i el pic
        marques.filter { (ts, _) -> ts >= suau.first().ts && ts <= suau.last().ts }.forEachIndexed { k, (ts, text) ->
            val xx = x(ts)
            drawLine(Desconegut, Offset(xx, dalt), Offset(xx, alt), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
            val estil = EstilEix.copy(color = DesconegutText, fontWeight = FontWeight.Medium)
            val mida = mesura.measure(text, estil).size
            drawText(mesura, text, Offset((xx + 3.dp.toPx()).coerceAtMost(size.width - mida.width), alt - mida.height * (k + 1) - 2.dp.toPx()), estil)
        }
        if (pic != null) suau.firstOrNull { it.ts == pic }?.let { p ->
            val centre = Offset(x(p.ts), y(p.wTotal))
            drawCircle(PotenciaLimit, 4.dp.toPx(), centre)
            val text = horaMinut(p.ts)
            val estil = EstilEix.copy(color = PotenciaLimit, fontWeight = FontWeight.Medium)
            val mida = mesura.measure(text, estil).size
            drawText(mesura, text, Offset((centre.x - mida.width - 4.dp.toPx()).coerceAtLeast(marge), (centre.y - mida.height - 2.dp.toPx()).coerceAtLeast(0f)), estil)
        }
        // «Ara»: una ratlla de punts i el punt a l'últim valor
        if (marcaAra) {
            val u = suau.last()
            val xx = x(u.ts)
            drawLine(Primari, Offset(xx, dalt), Offset(xx, alt), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            drawCircle(Superficie, 4.5.dp.toPx(), Offset(xx, y(u.wTotal)))
            drawCircle(Primari, 3.dp.toPx(), Offset(xx, y(u.wTotal)))
            val estil = EstilEix.copy(color = Primari, fontWeight = FontWeight.SemiBold)
            val mida = mesura.measure(ara, estil).size
            drawText(mesura, ara, Offset((xx - mida.width - 3.dp.toPx()).coerceAtLeast(marge), 0f), estil)
        }

        // Les hores a sota, sense sortir dels marges: cada 6 hores, cada 2 o, en una estona curta, cada mitja hora
        val durada = suau.last().ts - suau.first().ts
        val pas = when {
            durada <= 4 * 3600 -> 1800L
            durada <= 12 * 3600 -> 7200L
            else -> 6 * 3600L
        }
        val zona = ZoneId.systemDefault()
        var ts = (suau.first().ts / 1800 + 1) * 1800
        while (ts <= suau.last().ts) {
            val hora = Instant.ofEpochSecond(ts).atZone(zona)
            if ((hora.hour * 3600L + hora.minute * 60L) % pas == 0L) {
                val etiqueta = if (pas < 3600) horaMinut(ts) else context.getString(R.string.grafiques_hora, hora.hour)
                val mida = mesura.measure(etiqueta, EstilEix).size
                val xx = (x(ts) - mida.width / 2).coerceIn(marge, size.width - mida.width)
                drawText(mesura, etiqueta, Offset(xx, alt + 4.dp.toPx()), EstilEix)
            }
            ts += 1800
        }
    }
    // La llegenda del 80 i el 90 % només si les ratlles es pinten: a l'escala de les dades, quan hi cauen
    val limitDins = limitKw != null && (!escalaDades || suau.isNotEmpty() && limitKw * 800 <= maxOf(suau.maxOf { it.wTotal } * 1.05, 200.0))
    Llegenda(serie.any { it.aprox }, limitDins)
}

/**
 * Una estona passada (maqueta 8): la potència, l'estona que ha dit l'usuari (ratlles de punts) i els trams que l'app
 * hi ha trobat, en verd els marcats i en gris els altres. [punts] porta un quart d'hora de més a cada costat.
 */
@Composable
fun GraficaEstona(estona: Estona, ambLinia: Boolean, triats: Set<Int>) {
    val mesura = rememberTextMeasurer()
    val context = LocalContext.current
    val serie = estona.punts
    val descripcio = stringResource(R.string.passat_desc_grafica, estona.trams.size, triats.size)
    Canvas(Modifier.fillMaxWidth().padding(top = 12.dp).height(150.dp).semantics { contentDescription = descripcio }) {
        val peu = 20.dp.toPx()
        val alt = size.height - peu
        if (serie.size < 2) return@Canvas
        fun w(p: Punt) = (p.wTotal - if (ambLinia) p.wLinia else 0.0).coerceAtLeast(0.0)
        val maxim = maxOf(serie.maxOf(::w), 200.0) * 1.05
        val pasKw = pas(maxim / 1000)
        val ymax = ceil(maxim / 1000 / pasKw) * pasKw * 1000
        val etiquetes = generateSequence(0.0) { it + pasKw }.takeWhile { it <= ymax / 1000 + 1e-9 }
            .map { kw -> kw to context.getString(R.string.grafiques_kw, num(kw, if (pasKw < 1) 1 else 0)) }.toList()
        val marge = etiquetes.maxOf { mesura.measure(it.second, EstilEix).size.width } + 6.dp.toPx()
        val ample = size.width - marge
        val t0 = serie.first().ts.toFloat()
        val t1 = serie.last().ts.toFloat().coerceAtLeast(t0 + 1)
        fun x(ts: Long) = marge + (ts - t0) / (t1 - t0) * ample
        fun y(v: Double) = alt - (v / ymax).toFloat().coerceIn(0f, 1f) * alt

        // Els trams, darrere de la línia
        estona.trams.forEachIndexed { i, tr ->
            val color = if (i in triats) Primari.copy(alpha = 0.18f) else Gris.copy(alpha = 0.18f)
            drawRect(color, Offset(x(tr.ts) - 1.dp.toPx(), 0f), Size(x(tr.fi + 60) - x(tr.ts) + 2.dp.toPx(), alt))
        }
        for ((kw, text) in etiquetes) {
            val yy = y(kw * 1000)
            if (kw > 0) drawLine(Graella, Offset(marge, yy), Offset(size.width, yy))
            val mida = mesura.measure(text, EstilEix).size
            drawText(mesura, text, Offset(marge - 4.dp.toPx() - mida.width, yy - mida.height / 2f), EstilEix)
        }
        drawLine(GrisBarra, Offset(marge, alt), Offset(size.width, alt))
        // L'estona que ha dit l'usuari
        val punts = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (ts in listOf(estona.desDe, estona.finsA)) drawLine(Primari, Offset(x(ts), 0f), Offset(x(ts), alt), 1.5f, pathEffect = punts)
        val linia = Path().apply {
            serie.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.ts), y(w(p))) else lineTo(x(p.ts), y(w(p))) }
        }
        drawPath(linia, Primari, style = Stroke(1.5.dp.toPx()))
        for (ts in listOf(estona.desDe, estona.finsA)) {
            val etiqueta = horaMinut(ts)
            val mida = mesura.measure(etiqueta, EstilEix).size
            val xx = (x(ts) - mida.width / 2).coerceIn(marge, size.width - mida.width)
            drawText(mesura, etiqueta, Offset(xx, alt + 4.dp.toPx()), EstilEix)
        }
    }
}

/** L'hora i el minut d'un instant, com a les etiquetes de la gràfica (20:48). */
fun horaMinut(ts: Long): String = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).let { "%d:%02d".format(it.hour, it.minute) }

/** El total, en trams del mateix aspecte: el color segons la potència i si és aproximat (de punts). */
private fun DrawScope.liniaNivells(serie: List<Punt>, limitKw: Double?, x: (Long) -> Float, y: (Punt) -> Float) {
    fun aspecte(a: Punt, b: Punt): Pair<Color, Boolean> {
        val n = maxOf(nivellPotencia(a.wTotal, limitKw), nivellPotencia(b.wTotal, limitKw))
        return colorNivell(n, if (b.aprox) Nuvol else Primari) to b.aprox
    }
    var i = 1
    while (i < serie.size) {
        val (color, aprox) = aspecte(serie[i - 1], serie[i])
        val cami = Path().apply { moveTo(x(serie[i - 1].ts), y(serie[i - 1])) }
        while (i < serie.size && aspecte(serie[i - 1], serie[i]) == color to aprox) {
            cami.lineTo(x(serie[i].ts), y(serie[i]))
            i++
        }
        drawPath(
            cami, color,
            style = Stroke(1.6.dp.toPx(), pathEffect = if (aprox) PathEffect.dashPathEffect(floatArrayOf(5f, 6f)) else null),
        )
    }
}

/**
 * Una línia que passa per tots els punts: contínua on les dades són exactes i de punts on són aproximades
 * (un tram és aproximat si el punt on acaba ho és).
 */
private fun DrawScope.liniaPunts(serie: List<Punt>, color: Color, x: (Long) -> Float, y: (Punt) -> Float) {
    val exacta = Path()
    val aproximada = Path()
    for (i in 1 until serie.size) {
        val (a, b) = serie[i - 1] to serie[i]
        val cami = if (b.aprox) aproximada else exacta
        if (i == 1 || serie[i - 1].aprox != b.aprox) cami.moveTo(x(a.ts), y(a))
        cami.lineTo(x(b.ts), y(b))
    }
    drawPath(exacta, color, style = Stroke(1.2.dp.toPx()))
    drawPath(aproximada, color, style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f))))
}

/** La llegenda de sota la gràfica: només el que hi surt. */
@Composable
private fun Llegenda(ambAproximat: Boolean, ambLimit: Boolean) {
    if (!ambAproximat && !ambLimit) return
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable
        fun Element(color: Color, text: String, ratllat: Boolean = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(14.dp).height(4.dp)) {
                    drawLine(
                        color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.5.dp.toPx(),
                        pathEffect = if (ratllat) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null,
                    )
                }
                Text(text, Modifier.padding(start = 4.dp), fontSize = 10.5.sp, color = Suau)
            }
        }
        Element(Primari, stringResource(R.string.grafiques_exacte))
        if (ambAproximat) Element(Nuvol, stringResource(R.string.grafiques_aproximat), ratllat = true)
        if (ambLimit) {
            Element(PotenciaAlta, "80 %")
            Element(PotenciaLimit, "90 %")
        }
    }
}

/** Un tros d'una barra apilada: quants kWh, de quin color, i si va ratllat (el que ve del núvol). */
class Segment(val kwh: Double, val color: Color, val ratllat: Boolean = false)

/** Una barra de l'historial, apilada: l'etiqueta de sota i els trossos de baix a dalt. */
class BarraApilada(val etiqueta: String, val segments: List<Segment>, val nuvol: Boolean = false) {
    val total get() = segments.sumOf { it.kwh }
}

/**
 * Les barres de l'historial (maqueta 18), apilades amb els mateixos colors que el rànquing. Els dies que venen del núvol
 * per hores porten «núvol» a sobre. [llegenda]: el nom de cada color, a sota.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GraficaBarres(barres: List<BarraApilada>, llegenda: List<Pair<String, Color>>) {
    val mesura = rememberTextMeasurer()
    val context = LocalContext.current
    val nuvolText = stringResource(R.string.grafiques_nuvol)
    val maxBarra = barres.maxByOrNull { it.total }
    val descripcio = maxBarra?.let { stringResource(R.string.grafiques_desc_barres, num(it.total, 1), it.etiqueta) } ?: ""
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = descripcio }) {
        val peu = 18.dp.toPx()
        val dalt = 12.dp.toPx()
        val alt = size.height - peu
        if (barres.isEmpty()) return@Canvas
        val maxim = (barres.maxOf { it.total } * 1.05).coerceAtLeast(0.1)
        val pasKwh = pas(maxim)
        val ymax = ceil(maxim / pasKwh) * pasKwh
        val etiquetes = generateSequence(pasKwh) { it + pasKwh }.takeWhile { it <= ymax + 1e-9 }
            .map { kwh -> kwh to context.getString(R.string.grafiques_kwh, num(kwh, if (pasKwh < 1) 1 else 0)) }.toList()
        val marge = etiquetes.maxOf { mesura.measure(it.second, EstilEix).size.width } + 6.dp.toPx()
        fun y(kwh: Double) = alt - (kwh / ymax * (alt - dalt)).toFloat()
        for ((kwh, text) in etiquetes) {
            val yy = y(kwh)
            drawLine(Graella, Offset(marge, yy), Offset(size.width, yy))
            val mida = mesura.measure(text, EstilEix).size
            drawText(mesura, text, Offset(marge - 4.dp.toPx() - mida.width, yy - mida.height / 2f), EstilEix)
        }
        drawLine(GrisBarra, Offset(marge, alt), Offset(size.width, alt))
        val pas = (size.width - marge) / barres.size
        val ample = (pas * 0.72f).coerceAtMost(28.dp.toPx())
        // Les etiquetes: cada 6 hores o dies; els 12 mesos de l'any (una lletra) i els 7 dies de la setmana, totes
        val cadaQuants = when {
            barres.size > 20 -> 6
            barres.size > 12 -> 5
            else -> 1
        }
        val ratlles = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
        barres.forEachIndexed { i, b ->
            val x = marge + i * pas + (pas - ample) / 2
            var base = 0.0
            b.segments.forEachIndexed { k, s ->
                if (s.kwh <= 0) return@forEachIndexed
                val y0 = y(base + s.kwh)
                val y1 = y(base)
                if (y1 - y0 > 0.5f) {
                    val primer = k == b.segments.indexOfLast { it.kwh > 0 }
                    val cantonada = if (primer) CornerRadius(2.dp.toPx()) else CornerRadius.Zero
                    if (s.ratllat) {
                        drawRoundRect(PrimariFluix, Offset(x, y0), Size(ample, y1 - y0), cantonada)
                        var xx = x + 2.dp.toPx()
                        while (xx < x + ample) {
                            drawLine(s.color, Offset(xx, y1), Offset(xx, y0), 1.dp.toPx(), pathEffect = ratlles)
                            xx += 4.dp.toPx()
                        }
                    } else {
                        drawRoundRect(s.color, Offset(x, y0), Size(ample, y1 - y0), cantonada)
                    }
                }
                base += s.kwh
            }
            if (b.nuvol && b.total > 0 && barres.size <= 7) {
                val estil = EstilEix.copy(fontSize = 8.5.sp, color = Primari, fontWeight = FontWeight.SemiBold)
                val mida = mesura.measure(nuvolText, estil).size
                drawText(mesura, nuvolText, Offset(x + ample / 2 - mida.width / 2, y(b.total) - mida.height - 1.dp.toPx()), estil)
            }
            if (i % cadaQuants == 0 || barres.size <= 10) {
                val mida = mesura.measure(b.etiqueta, EstilEix).size
                drawText(mesura, b.etiqueta, Offset(x + ample / 2 - mida.width / 2, alt + 3), EstilEix)
            }
        }
    }
    if (llegenda.isNotEmpty()) {
        FlowRow(Modifier.padding(top = 4.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            llegenda.forEach { (nom, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
                    Text(" $nom", fontSize = 10.sp, color = Suau)
                }
            }
            // El que ve del núvol: ratllat
            if (barres.any { it.nuvol }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(PrimariFluix)) {
                        for (xx in listOf(2.dp.toPx(), 6.dp.toPx())) drawLine(Primari, Offset(xx, 0f), Offset(xx, size.height), 1.dp.toPx())
                    }
                    Text(" " + nuvolText, fontSize = 10.sp, color = Suau)
                }
            }
        }
    }
}
