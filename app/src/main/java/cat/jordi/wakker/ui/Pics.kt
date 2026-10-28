package cat.jordi.wakker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.AnalisiPic
import cat.jordi.wakker.dades.AparellPic
import cat.jordi.wakker.dades.DesconegutPic
import cat.jordi.wakker.dades.ElementPic
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.LiniaPic
import cat.jordi.wakker.dades.NivellPotencia
import cat.jordi.wakker.dades.Periode
import cat.jordi.wakker.dades.SempreEncesPic
import cat.jordi.wakker.dades.SenseIdentificarPic
import cat.jordi.wakker.dades.Com
import cat.jordi.wakker.dades.PetitsPic
import cat.jordi.wakker.dades.nivellPotencia
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle

/** Els que s'ensenyen d'un pic, de més a menys: els altres van junts a «la resta». */
private const val QUANTS = 5

/**
 * Els moments punta del període. Amb el dia, el de les últimes 24 hores; amb la setmana i el mes, els tres dies
 * més alts, i el que es toca s'ensenya a sota.
 */
@Composable
fun MomentsPunta(vm: WakkerViewModel) {
    val pics = vm.pics
    if (pics.isEmpty()) return
    val limit = vm.config.tarifa.limitKw
    if (vm.periode != Periode.DIA && pics.size > 1) {
        Targeta {
            TitolTargeta(stringResource(if (vm.periode == Periode.SETMANA) R.string.pics_setmana else R.string.pics_mes))
            pics.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(color = Separador)
                val triat = a.pic.ts == (vm.picTriat ?: pics.first().pic.ts)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.triaPic(a.pic) }
                        .background(if (triat) PrimariFluix else Color.Transparent).padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pics_dia_hora, dia(a.pic.ts), horaMinut(a.pic.ts)), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        resumElements(a)?.let { Text(it, fontSize = 12.sp, color = Suau, maxLines = 1) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${num(a.pic.w)} W", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorNivell(nivellPotencia(a.pic.w, limit), Color.Unspecified))
                        if (limit != null) Text("${num(a.pic.w / (limit * 10))} %", fontSize = 11.sp, color = Suau)
                    }
                }
            }
            Nota(stringResource(R.string.pics_toca), Modifier.padding(top = 4.dp))
        }
    }
    val triat = pics.firstOrNull { it.pic.ts == vm.picTriat } ?: pics.first()
    TargetaPic(vm, triat, stringResource(R.string.pics_titol_dia, dia(triat.pic.ts)))
}

/** Els noms dels que gastaven més en un pic, per a la llista: els aparells coneguts o el salt dels desconeguts. */
@Composable
private fun resumElements(a: AnalisiPic): String? {
    val noms = a.elements.mapNotNull {
        when (it) {
            is AparellPic -> it.aparell.nom
            is DesconegutPic -> "+${num(it.w / 1000, 1)} kW"
            is LiniaPic -> null
            else -> null
        }
    }.take(3)
    return noms.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/** El dia d'un instant, curt: «dissabte 24». */
private fun dia(ts: Long): String = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).let {
    "${it.dayOfWeek.getDisplayName(TextStyle.FULL, Idioma.locale)} ${it.dayOfMonth}"
}

/** Un pic: quant i quan, la gràfica de l'estona d'abans amb els salts, i els que gastaven més. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetaPic(vm: WakkerViewModel, a: AnalisiPic, titol: String) {
    val limit = vm.config.tarifa.limitKw
    val nivell = nivellPotencia(a.pic.w, limit)
    Targeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ShowChart, null, Modifier.size(18.dp), tint = colorNivell(nivell, Primari))
            Spacer(Modifier.width(6.dp))
            TitolTargeta(titol, Modifier.padding(top = 6.dp))
        }
        Text("${num(a.pic.w)} W", fontSize = 30.sp, fontWeight = FontWeight.Light, color = colorNivell(nivell, Color.Unspecified))
        Text(
            if (limit != null) stringResource(R.string.pics_quan_percent, horaMinut(a.pic.ts), num(a.pic.w / (limit * 10)))
            else stringResource(R.string.pics_quan, horaMinut(a.pic.ts)),
            fontSize = 13.sp, color = if (nivell == NivellPotencia.NORMAL) Suau else colorNivell(nivell, Suau),
        )
        a.pic.wMax?.takeIf { it > a.pic.w * 1.1 }?.let { Nota(stringResource(R.string.pics_instantani, num(it))) }
        Spacer(Modifier.height(6.dp))
        // Quan es va encendre cada cosa que hi havia en el pic (les que ja hi eren abans de la gràfica, no)
        val marques = a.vegades.filter { v -> a.finestra.firstOrNull()?.let { v.ts >= it.ts } ?: false }.sortedBy { it.ts }
            .map { it.ts to "${horaMinut(it.ts)} · +${num(it.w / 1000, 1)} kW" }
        GraficaPotencia(a.finestra, vm.config.teLinia, limit, marques, a.pic.ts)
    }
    Targeta {
        val n = minOf(QUANTS, a.elements.size)
        TitolTargeta(pluralStringResource(R.plurals.pics_gastaven_mes, n, n))
        val mostrats = a.elements.take(QUANTS)
        val resta = a.elements.drop(QUANTS).sumOf { it.w }
        mostrats.forEach { e -> ElementDelPic(vm, e, a.pic.w) }
        if (resta > 0) FilaPic(Icons.Outlined.QuestionMark, Sempre, stringResource(R.string.pics_la_resta), "", resta, a.pic.w)
        if (mostrats.any { it is DesconegutPic }) {
            Nota(stringResource(if (vm.aparellsAEntrenar.isEmpty()) R.string.entrena_primer_afegeix else R.string.pics_aprendra), Modifier.padding(top = 6.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElementDelPic(vm: WakkerViewModel, e: ElementPic, total: Double) {
    when (e) {
        is AparellPic -> FilaPic(
            icona(e.aparell.categoria), if (e.linia) Linia else Primari, e.aparell.nom,
            if (e.compressor) stringResource(R.string.pics_compressor)
            else listOfNotNull(
                stringResource(R.string.pinca2_xip).takeIf { e.linia },
                e.desDe?.let { stringResource(R.string.pics_des_de, horaMinut(it)) },
                when (e.com) {
                    Com.ENSENYAT -> stringResource(R.string.fitxa_ensenyat)
                    Com.RECONEGUT -> stringResource(R.string.fitxa_reconegut)
                    null -> null
                },
            ).joinToString(" · "),
            e.w, total,
        )
        is SempreEncesPic -> FilaPic(Icons.Outlined.Power, Sempre, stringResource(R.string.historial_sempre_ences), stringResource(R.string.pics_sempre_ences), e.w, total)
        is LiniaPic -> if (e.altres) FilaPic(Icons.Outlined.Bolt, Linia, stringResource(R.string.ranquing_linia_altres, vm.config.nomLinia), stringResource(R.string.ranquing_linia_altres_text), e.w, total)
        else FilaPic(Icons.Outlined.Bolt, Linia, vm.config.nomLinia, stringResource(R.string.pics_linia), e.w, total)
        is PetitsPic -> FilaPic(Icons.Outlined.LightMode, Sempre, stringResource(R.string.ranquing_petits), stringResource(R.string.ranquing_petits_text), e.w, total)
        is SenseIdentificarPic -> FilaPic(Icons.Outlined.QuestionMark, Desconegut, stringResource(R.string.historial_sense_identificar), stringResource(R.string.pics_acabava), e.w, total)
        is DesconegutPic -> {
            var tots by remember(e.vegada.ts) { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(DesconegutFluix)
                    .border(1.dp, Desconegut.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("+${num(e.w / 1000, 1)} kW", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DesconegutText)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.pics_vegada, horaMinut(e.vegada.ts), durada(e.vegada.minuts)),
                        fontSize = 12.sp, color = Suau,
                    )
                }
                Text(stringResource(R.string.pics_que_era), Modifier.padding(top = 2.dp), fontSize = 12.5.sp, color = Suau)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.suggerits.take(3).forEach { ap -> AssistChip(onClick = { vm.responVegada(e.vegada, ap.id) }, label = { Text(ap.nom) }) }
                    AssistChip(onClick = { tots = true }, label = { Text(stringResource(R.string.tria_tots)) })
                }
            }
            if (tots) {
                TriaAparell(
                    stringResource(R.string.pics_vegada, horaMinut(e.vegada.ts), durada(e.vegada.minuts)), e.suggerits.take(3), e.suggerits, vm.config.teLinia,
                    tria = { vm.responVegada(e.vegada, it.id) }, tanca = { tots = false },
                    // Un aparell nou d'una vegada de la pinça 2 és de la pinça 2
                    nou = { vm.responVegada(e.vegada, null, it.copy(linia = e.vegada.canal == 1)) },
                    senseLinia = e.vegada.canal == 0 && vm.config.teLinia && e.suggerits.none { it.linia } && vm.aparellsLinia.isNotEmpty(),
                    nomLinia = vm.config.nomLinia,
                )
            }
        }
    }
}

/** Una fila d'un pic: la icona, el nom, els W, una barra amb la part del total i el detall. */
@Composable
private fun FilaPic(icona: ImageVector, color: Color, nom: String, detall: String, w: Double, total: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icona, null, Modifier.size(18.dp), tint = color)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(nom, Modifier.weight(1f), fontSize = 14.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("${num(w)} W", fontSize = 14.5.sp)
            }
            Box(Modifier.padding(vertical = 3.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Separador)) {
                Box(Modifier.fillMaxWidth((w / total).toFloat().coerceIn(0.01f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
            }
            if (detall.isNotBlank()) Text(detall, fontSize = 11.5.sp, color = Suau, maxLines = 1)
        }
    }
}
