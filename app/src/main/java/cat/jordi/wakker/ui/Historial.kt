package cat.jordi.wakker.ui

import android.app.DatePickerDialog
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.Periode
import cat.jordi.wakker.dades.Resum
import cat.jordi.wakker.dades.fiPeriode
import cat.jordi.wakker.dades.iniciPeriode
import cat.jordi.wakker.dades.periodesEnrere
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * L'Historial (maqueta 18): el període a la barra de dalt, les fletxes, el total amb la comparació, les barres apilades amb
 * els colors del rànquing, i «Qui ha gastat més» amb la resta en dues files. Un període anterior només porta una franja a dalt.
 */
@Composable
fun PantallaHistorial(vm: WakkerViewModel) {
    val r = vm.resum
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (r?.actual == false) FranjaPassat(vm)
        NavegaPeriode(vm, r)
        if (r == null) return@Column
        if (!r.ambDades) {
            Targeta {
                Nota(
                    r.primerDiaDades?.let { stringResource(R.string.historial_sense_dades, format(it, R.string.format_data_llarga)) }
                        ?: stringResource(R.string.historial_sense_dades_cap),
                )
            }
            return@Column
        }
        // El total i la comparació amb el període d'abans
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Bottom) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(num(r.total, 1), style = EstilMitja.copy(fontSize = 34.sp))
                Text(" kWh", Modifier.padding(bottom = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Suau)
                Text("  ${euros(r.euros.total)}", Modifier.padding(bottom = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Suau)
            }
            Spacer(Modifier.weight(1f))
            Comparacio(vm.periode, r)
        }
        // Les barres, apilades amb els mateixos colors que el rànquing
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Superficie, border = BorderStroke(1.dp, Vora)) {
            Column(Modifier.padding(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)) {
                val (apilades, llegenda) = barresApilades(r, vm.config.nomLinia)
                GraficaBarres(apilades, llegenda)
                if (r.aproximat) NotaAproximat(stringResource(R.string.historial_aproximat), vm.config.host.isNotBlank())
                if (r.barres.any { it.hora.nuvol > 0 }) Nota(stringResource(R.string.historial_nuvol_nota), Modifier.padding(top = 4.dp, start = 4.dp))
            }
        }
        // Qui ha gastat més, cada aparell un cop, i la resta en dues files (maqueta 13 i 18)
        r.ranquing?.let { rq ->
            Targeta {
                TitolTargeta(stringResource(R.string.ranquing_titol))
                LlistaRanquing(rq, vm.config.nomLinia) { vm.obreAparell(it) }
                RestaConsum(rq, r.fonsW, vm.config.nomLinia, obreSempre = vm::obreEspera, obreSenseId = { vm.pestanya = Pestanya.PER_FER })
                Text(
                    stringResource(R.string.historial_fixos_peu, euros(r.euros.fixos)), Modifier.padding(top = 6.dp, start = 20.dp),
                    fontSize = 11.sp, color = Suau,
                )
            }
        }
        // De l'any no se'n busquen: caldria llegir-ne tots els minuts
        if (vm.periode != Periode.ANY) MomentsPunta(vm)
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Les barres apilades (maqueta 18): el sempre encès en gris; els tres aparells que més han gastat, cadascun amb el seu verd;
 * els altres aparells, en verd pàl·lid; el que l'app no sap de qui és, en ambre; i el que ve del núvol, ratllat. La nevera
 * (el compressor, sense vegades) es reparteix proporcionalment entre les barres.
 */
@Composable
private fun barresApilades(r: Resum, nomLinia: String): Pair<List<BarraApilada>, List<Pair<String, androidx.compose.ui.graphics.Color>>> {
    val rq = r.ranquing
    val colors = listOf(Primari, PrimariMig, PrimariClar)
    val top = rq?.aparells?.filter { it.compressorW == null }?.take(3).orEmpty()
    val topIds = top.map { it.aparell?.id }
    val nevera = rq?.aparells?.firstOrNull { it.compressorW != null }
    val pes = r.barres.sumOf { (it.hora.total - it.hora.nuvol).coerceAtLeast(0.0) }.coerceAtLeast(0.001)
    val apilades = r.barres.map { b ->
        val h = b.hora
        val delShelly = (h.total - h.nuvol).coerceAtLeast(0.0)
        val escala = if (h.total > 0) delShelly / h.total else 0.0
        val sempre = h.sempre * escala
        // La línia sencera, si no s'hi ha dit cap aparell, és un més del rànquing: les seves vegades no tenen aparell
        val liniaSola = h.linia * escala
        var deTop = top.map { f -> if (f.esLinia) liniaSola else f.aparell?.let { b.perAparell[it.id] } ?: 0.0 }
        var altres = b.perAparell.filterKeys { it !in topIds }.values.sum() + (nevera?.kwh ?: 0.0) * delShelly / pes +
            (if (rq != null && rq.aparells.drop(top.size).any { it.esLinia }) liniaSola else 0.0)
        // Una vegada repartida pel temps pot passar del que hi ha a l'hora: els aparells s'escalen al que queda sobre el fons
        val lloc = (delShelly - sempre).coerceAtLeast(0.0)
        val suma = deTop.sum() + altres
        if (suma > lloc && suma > 0) {
            deTop = deTop.map { it * lloc / suma }
            altres *= lloc / suma
        }
        val noSap = (lloc - deTop.sum() - altres).coerceAtLeast(0.0)
        BarraApilada(
            b.etiqueta,
            listOf(Segment(sempre, GrisBarra)) + deTop.mapIndexed { i, kwh -> Segment(kwh, colors[i]) } +
                listOf(Segment(altres, PrimariPal), Segment(noSap, Desconegut), Segment(h.nuvol.coerceAtMost(h.total), Primari, ratllat = true)),
            nuvol = h.nuvol > 0,
        )
    }
    val llegenda = buildList {
        top.forEachIndexed { i, f -> add(f.nom.ifBlank { nomLinia } to colors[i]) }
        if (rq != null && rq.aparells.size > top.size) add(stringResource(R.string.historial_llegenda_altres) to PrimariPal)
        add(stringResource(R.string.ranquing_sempre) to GrisBarra)
        add(stringResource(R.string.historial_llegenda_sense) to Desconegut)
    }
    return apilades to llegenda
}

/** Una data amb un dels formats de la traducció (format_dia_llarg, format_data_llarga…), en la llengua de l'app. */
@Composable
private fun format(dia: LocalDate, @StringRes patro: Int): String =
    dia.format(DateTimeFormatter.ofPattern(stringResource(patro), Idioma.locale))

/** El període que es mira: «dimecres 21 d'octubre», «19–25 d'octubre», «octubre del 2026», «2026». */
@Composable
private fun etiquetaPeriode(p: Periode, dia: LocalDate): String = when (p) {
    Periode.DIA -> format(dia, R.string.format_dia_llarg)
    Periode.SETMANA -> {
        val inici = iniciPeriode(p, dia)
        val fi = fiPeriode(p, dia).minusDays(1)
        if (inici.month == fi.month) "${inici.dayOfMonth}–${format(fi, R.string.format_dia_mes)}"
        else "${format(inici, R.string.format_dia_mes)} – ${format(fi, R.string.format_dia_mes)}"
    }
    Periode.MES -> stringResource(R.string.historial_mes, dia.month.getDisplayName(TextStyle.FULL_STANDALONE, Idioma.locale), dia.year)
    Periode.ANY -> "${dia.year}"
}

/** Quant fa: «avui», «ahir», «fa 5 dies», «la setmana passada», «fa 2 anys»… */
@Composable
private fun relatiu(p: Periode, enrere: Long): String {
    val n = enrere.toInt()
    val (ara, passat, plural) = when (p) {
        Periode.DIA -> Triple(R.string.historial_rel_dia_0, R.string.historial_rel_dia_1, R.plurals.historial_rel_dia_n)
        Periode.SETMANA -> Triple(R.string.historial_rel_setmana_0, R.string.historial_rel_setmana_1, R.plurals.historial_rel_setmana_n)
        Periode.MES -> Triple(R.string.historial_rel_mes_0, R.string.historial_rel_mes_1, R.plurals.historial_rel_mes_n)
        Periode.ANY -> Triple(R.string.historial_rel_any_0, R.string.historial_rel_any_1, R.plurals.historial_rel_any_n)
    }
    return when (n) {
        0 -> stringResource(ara)
        1 -> stringResource(passat)
        else -> pluralStringResource(plural, n, n)
    }
}

/** Les fletxes per anar al període d'abans o al de després; tocant la data, el calendari. */
@Composable
private fun NavegaPeriode(vm: WakkerViewModel, r: Resum?) {
    val context = LocalContext.current
    val p = vm.periode
    val dia = vm.diaHistorial
    val enrere = periodesEnrere(p, dia, LocalDate.now())
    val passat = enrere > 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Enrere fins al primer període sense dades, i prou
        IconButton({ vm.mouHistorial(-1) }, enabled = r?.ambDades != false) {
            Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.historial_anterior), tint = Suau)
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable {
                DatePickerDialog(context, { _, a, m, d -> vm.veesA(LocalDate.of(a, m + 1, d)) }, dia.year, dia.monthValue - 1, dia.dayOfMonth).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                    r?.primerDiaDades?.let { datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                }.show()
            }.padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(etiquetaPeriode(p, dia), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(relatiu(p, enrere), fontSize = 11.5.sp, color = Suau)
        }
        IconButton({ vm.mouHistorial(1) }, enabled = passat) {
            Icon(Icons.Outlined.ChevronRight, stringResource(R.string.historial_seguent), tint = if (passat) Suau else Vora)
        }
    }
}

/** A dalt d'un període anterior: una franja prima amb què s'està mirant, i «Avui» (maqueta 18). */
@Composable
private fun FranjaPassat(vm: WakkerViewModel) {
    val p = vm.periode
    val dia = vm.diaHistorial
    val que = when (p) {
        Periode.DIA -> format(dia, R.string.format_dia_curt)
        Periode.ANY -> stringResource(R.string.historial_mirant_any, dia.year)
        else -> relatiu(p, periodesEnrere(p, dia, LocalDate.now()))
    }
    Row(
        Modifier.fillMaxWidth().background(GrisFluix, RoundedCornerShape(10.dp)).padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.historial_mirant, que), Modifier.weight(1f), fontSize = 12.sp, color = Suau)
        Enllac(stringResource(R.string.historial_avui), onClick = vm::tornaAvui)
    }
}

/** «+13 %» amb «vs la setmana abans» a sota; o, si l'app no en tenia totes les dades, una línia que ho diu. */
@Composable
private fun Comparacio(p: Periode, r: Resum) {
    val quin = stringResource(
        when (p) {
            Periode.DIA -> if (r.actual) R.string.historial_comp_dia_ara else R.string.historial_comp_dia_abans
            Periode.SETMANA -> if (r.actual) R.string.historial_comp_setmana_ara else R.string.historial_comp_setmana_abans
            Periode.MES -> if (r.actual) R.string.historial_comp_mes_ara else R.string.historial_comp_mes_abans
            Periode.ANY -> if (r.actual) R.string.historial_comp_any_ara else R.string.historial_comp_any_abans
        }
    )
    val abans = r.anteriorKwh
    Column(horizontalAlignment = Alignment.End) {
        if (abans != null && abans > 0.05) {
            val pct = ((r.total / abans - 1) * 100).roundToInt()
            Text((if (pct > 0) "+" else "") + "$pct\u00A0%", style = EstilNumero.copy(fontSize = 15.sp))
            // El període d'ara és a mitges: es compara amb el mateix tros de l'anterior, i es diu (1.1.3)
            val qui = quin.replaceFirstChar { it.lowercase(Idioma.locale) }
            Text(stringResource(if (r.actual) R.string.historial_vs_altura else R.string.historial_vs, qui), fontSize = 10.5.sp, color = Suau, textAlign = TextAlign.End)
        } else {
            Text(stringResource(R.string.historial_comp_sense, quin), fontSize = 10.5.sp, color = Suau, textAlign = TextAlign.End, modifier = Modifier.width(150.dp))
        }
    }
}
