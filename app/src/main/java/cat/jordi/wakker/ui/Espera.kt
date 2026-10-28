@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.wakker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Aparell
import cat.jordi.wakker.dades.Categoria
import cat.jordi.wakker.dades.Funcionament
import cat.jordi.wakker.shelly.Origen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/** Un any en hores, per passar W de tot l'any a kWh: 8.760 h / 1.000. */
private const val KWH_ANY_PER_W = 8.76

/**
 * La caça del sempre encès (maqueta 15): el que la casa gasta quan no fas res, i quant costa l'any. Apagues o desendolles una
 * cosa i la pantalla ensenya de seguida quant ha baixat; si hi dius què era, l'aparell en guarda el consum en espera (o, si
 * està sempre encès, la seva potència). A Guíxols, 79 W tot l'any són uns 113 €.
 */
@Composable
fun PantallaEspera(vm: WakkerViewModel) {
    val l = vm.lectura
    val fons = vm.fonsW
    val preu = vm.preuMitja
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Targeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Power, null, tint = Suau, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.espera_titol), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            if (fons != null) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(num(fons), fontSize = 44.sp, fontWeight = FontWeight.Light)
                    Text(" W", Modifier.padding(bottom = 8.dp), fontSize = 20.sp, color = Suau)
                    if (preu != null) {
                        Text(
                            "   " + stringResource(R.string.espera_any, euros(fons * KWH_ANY_PER_W * preu)),
                            Modifier.padding(bottom = 9.dp), fontSize = 16.sp, color = Suau,
                        )
                    }
                }
                Nota(stringResource(if (vm.config.teLinia) R.string.espera_que_es_linia else R.string.espera_que_es, vm.config.nomLinia))
            } else {
                Nota(stringResource(R.string.espera_cal_un_dia), Modifier.padding(top = 4.dp))
            }
        }

        Targeta {
            TitolTargeta(stringResource(R.string.espera_apaga_titol))
            Nota(stringResource(R.string.espera_apaga_text))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pastilla(
                    if (l != null) stringResource(R.string.espera_ara, num(vm.resta(l))) else stringResource(R.string.entrena_sense_lectura),
                    if (l != null) Primari else Desconegut, if (l != null) PrimariFluix else DesconegutFluix, punt = if (l != null) Ok else Desconegut,
                )
            }
            if (l != null && l.origen != Origen.LOCAL) Nota(stringResource(R.string.espera_nomes_casa), Modifier.padding(top = 6.dp))
            if (vm.baixades.isEmpty()) {
                Nota(stringResource(R.string.espera_esperant), Modifier.padding(top = 8.dp))
            }
            vm.baixades.forEach { b -> key(b.ts) { FilaBaixada(vm, b) } }
            if (preu != null) {
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PrimariFluix).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Check, null, tint = Primari, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.espera_cada_10, euros(10 * KWH_ANY_PER_W * preu)), fontSize = 12.5.sp, lineHeight = 17.sp)
                }
            }
        }

        // El que ja se sap: els aparells sempre encesos i els que gasten en espera
        val sempre = vm.aparells.filter { it.funciona == Funcionament.SEMPRE }
        val espera = vm.aparells.filter { it.funciona != Funcionament.SEMPRE && (it.esperaW ?: 0.0) > 0 }
        if (sempre.isNotEmpty() || espera.isNotEmpty()) {
            Targeta {
                TitolTargeta(stringResource(R.string.espera_ja_saps))
                sempre.forEach { a ->
                    FilaConsum(icona(a.categoria), SempreFluix, Sempre, a.nom, stringResource(R.string.espera_sempre_ences), "${num(a.wTipic)} W")
                }
                espera.forEach { a ->
                    FilaConsum(icona(a.categoria), SempreFluix, Sempre, a.nom, stringResource(R.string.espera_en_espera), "${num(a.esperaW ?: 0.0, 1)} W")
                }
                if (fons != null) {
                    val sabut = sempre.sumOf { it.wTipic } + espera.sumOf { it.esperaW ?: 0.0 }
                    Nota(stringResource(R.string.espera_sense_nom, num(max(fons - sabut, 0.0))), Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Una baixada de la potència: quan, quant, i què s'ha apagat (si encara no s'ha dit, els aparells que s'hi poden assemblar). */
@Composable
private fun FilaBaixada(vm: WakkerViewModel, b: Baixada) {
    var totes by remember { mutableStateOf(false) }
    val hora = Instant.ofEpochSecond(b.ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    val nom = b.aparellId?.let { id -> vm.aparells.firstOrNull { it.id == id }?.nom }
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hora, fontSize = 12.sp, color = Suau)
            Spacer(Modifier.width(8.dp))
            Text("−${num(b.w)} W", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ok)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    nom != null -> nom
                    b.respost -> stringResource(R.string.espera_no_ho_saps)
                    else -> stringResource(R.string.espera_que_has_apagat)
                },
                fontSize = 13.sp, color = Suau,
            )
        }
        if (!b.respost) {
            val ordenats = perEspera(vm.aparells, b.w)
            FlowRow(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ordenats.take(4).forEach { a ->
                    SuggestionChip(onClick = { vm.responBaixada(b, a.id) }, label = { Text(a.nom) })
                }
                SuggestionChip(onClick = { totes = true }, label = { Text(stringResource(R.string.tria_tots)) })
                SuggestionChip(onClick = { vm.responBaixada(b, null) }, label = { Text(stringResource(R.string.entrena_no_ho_se)) })
            }
        }
        if (totes) {
            TriaAparell(
                "$hora · −${num(b.w)} W", perEspera(vm.aparells, b.w).take(4), vm.aparells, vm.config.teLinia,
                tria = { vm.responBaixada(b, it.id) }, nou = { vm.responBaixada(b, null, it) }, tanca = { totes = false },
            )
        }
    }
}

/** Els tipus d'aparell que solen quedar en espera, endollats: els primers a proposar si no se'n sap res més. */
private val solenEstarEnEspera = setOf(
    Categoria.TELEVISOR, Categoria.CONSOLA, Categoria.ORDINADOR, Categoria.ROUTER, Categoria.LLUMS, Categoria.MICROONES,
    Categoria.CAFETERA, Categoria.FORN, Categoria.RENTADORA, Categoria.ASSECADORA, Categoria.RENTAPLATS, Categoria.ALTRES,
)

/**
 * Els aparells, del que més s'assembla a una baixada al que menys: primer els que se'n sap el consum sempre encès o en espera;
 * després, els que solen quedar en espera; al final, els que no (el cotxe, el termo).
 */
private fun perEspera(aparells: List<Aparell>, w: Double): List<Aparell> = aparells.sortedBy { a ->
    val ref = when {
        a.funciona == Funcionament.SEMPRE -> a.wTipic
        a.esperaW != null && a.esperaW > 0 -> a.esperaW
        else -> null
    }
    when {
        ref != null -> abs(ln(max(ref, 0.5) / max(w, 0.5)))
        a.categoria in solenEstarEnEspera -> 5.0
        else -> 10.0
    }
}
