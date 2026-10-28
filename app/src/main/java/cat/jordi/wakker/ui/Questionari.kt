@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.wakker.ui

import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Aparell
import cat.jordi.wakker.dades.Categoria
import cat.jordi.wakker.dades.Font
import cat.jordi.wakker.dades.Idioma

/** Una pregunta del qüestionari: si [unica], només s'hi pot triar una resposta. Null vol dir que no gasta llum. */
private class Pregunta(@StringRes val text: Int, val opcions: List<Pair<Int, Categoria?>>, val unica: Boolean = false)

private class Estanca(@StringRes val nom: Int, val preguntes: List<Pregunta>)

private val ESTANCES = listOf(
    Estanca(
        R.string.questionari_estanca_calefaccio, listOf(
            Pregunta(
                R.string.questionari_com_escalfa, listOf(
                    R.string.questionari_caldera_gas to Categoria.CALDERA, R.string.categoria_bomba_calor to Categoria.BOMBA_CALOR,
                    R.string.questionari_estufes_radiadors to Categoria.ESTUFA, R.string.questionari_calefaccio_barri to null,
                    R.string.questionari_no_n_hi_ha to null,
                ), unica = true,
            ),
            Pregunta(
                R.string.questionari_aigua_calenta, listOf(
                    R.string.questionari_mateixa_caldera to null, R.string.categoria_termo to Categoria.TERMO,
                    R.string.questionari_calefaccio_barri to null, R.string.questionari_escalfador_gas to null,
                ), unica = true,
            ),
            Pregunta(R.string.questionari_aire, listOf(R.string.categoria_aire to Categoria.AIRE)),
        )
    ),
    Estanca(
        R.string.questionari_estanca_cuina, listOf(
            Pregunta(
                R.string.questionari_que_cuina, listOf(
                    R.string.categoria_nevera to Categoria.NEVERA, R.string.categoria_congelador to Categoria.CONGELADOR,
                    R.string.categoria_forn to Categoria.FORN, R.string.categoria_microones to Categoria.MICROONES,
                    R.string.categoria_rentaplats to Categoria.RENTAPLATS, R.string.categoria_campana to Categoria.CAMPANA,
                    R.string.categoria_cafetera to Categoria.CAFETERA, R.string.categoria_bullidor to Categoria.BULLIDOR,
                    R.string.categoria_torradora to Categoria.TORRADORA, R.string.categoria_robot to Categoria.ROBOT,
                ),
            ),
            Pregunta(
                R.string.questionari_placa_es, listOf(
                    R.string.questionari_induccio to Categoria.PLACA, R.string.questionari_vitroceramica to Categoria.PLACA,
                    R.string.questionari_gas to null,
                ), unica = true,
            ),
        )
    ),
    Estanca(
        R.string.questionari_estanca_rentador, listOf(
            Pregunta(
                R.string.questionari_que_rentador, listOf(
                    R.string.categoria_rentadora to Categoria.RENTADORA, R.string.categoria_assecadora to Categoria.ASSECADORA,
                    R.string.categoria_planxa to Categoria.PLANXA, R.string.questionari_altre_congelador to Categoria.CONGELADOR,
                ),
            ),
        )
    ),
    Estanca(
        R.string.questionari_estanca_sala, listOf(
            Pregunta(
                R.string.questionari_que_sala, listOf(
                    R.string.categoria_televisor to Categoria.TELEVISOR, R.string.categoria_consola to Categoria.CONSOLA,
                    R.string.categoria_router to Categoria.ROUTER, R.string.categoria_llums to Categoria.LLUMS,
                    R.string.questionari_altaveus to Categoria.ALTRES,
                ),
            ),
        )
    ),
    Estanca(
        R.string.questionari_estanca_habitacions, listOf(
            Pregunta(
                R.string.questionari_que_habitacions, listOf(
                    R.string.categoria_ordinador to Categoria.ORDINADOR, R.string.questionari_altre_televisor to Categoria.TELEVISOR,
                    R.string.categoria_estufa to Categoria.ESTUFA, R.string.questionari_impressora to Categoria.ALTRES,
                ),
            ),
        )
    ),
    Estanca(
        R.string.questionari_estanca_bany, listOf(
            Pregunta(
                R.string.questionari_que_bany, listOf(
                    R.string.categoria_assecador_cabells to Categoria.ASSECADOR_CABELLS, R.string.categoria_tovalloler to Categoria.TOVALLOLER,
                    R.string.questionari_estufa_bany to Categoria.ESTUFA,
                ),
            ),
        )
    ),
    Estanca(
        R.string.questionari_estanca_exterior, listOf(
            Pregunta(
                R.string.questionari_que_fora, listOf(
                    R.string.categoria_cotxe to Categoria.COTXE, R.string.categoria_bomba_aigua to Categoria.BOMBA_AIGUA,
                    R.string.questionari_depuradora_piscina to Categoria.DEPURADORA, R.string.questionari_eines to Categoria.ALTRES,
                ),
            ),
        )
    ),
)

/** El nom de l'aparell que es crea amb cada tipus de placa. */
private val NOMS_PLACA = mapOf(
    R.string.questionari_induccio to R.string.questionari_placa_induccio,
    R.string.questionari_vitroceramica to R.string.questionari_placa_vitroceramica,
)

@Composable
fun PantallaQuestionari(vm: WakkerViewModel) {
    val context = LocalContext.current
    var pas by rememberSaveable { mutableIntStateOf(0) }
    // La clau és "estança|pregunta|opció", amb els ids dels textos. Es desen les marcades, per si es gira la pantalla
    val triat = rememberSaveable(
        saver = listSaver(save = { m -> m.filterValues { it }.keys.toList() }, restore = { l -> mutableStateMapOf(*l.map { it to true }.toTypedArray()) }),
    ) { mutableStateMapOf<String, Boolean>() }
    val estanca = ESTANCES[pas]

    fun clau(p: Pregunta, opcio: Int) = "${estanca.nom}|${p.text}|$opcio"

    fun acaba() {
        val existents = vm.aparells.map { it.nom.lowercase() }.toMutableSet()
        val nous = mutableListOf<Aparell>()
        val bombaJa = mutableSetOf<Categoria>()
        for (e in ESTANCES) for (p in e.preguntes) for ((opcio, cat) in p.opcions) {
            if (cat == null || triat["${e.nom}|${p.text}|$opcio"] != true) continue
            // La placa: el nom porta el tipus; la bomba de calor no es compta dues vegades
            val nom = context.getString(if (cat == Categoria.PLACA) NOMS_PLACA[opcio] ?: opcio else opcio)
            if (cat == Categoria.BOMBA_CALOR && !bombaJa.add(cat)) continue
            if (nom.lowercase() in existents) continue
            existents += nom.lowercase()
            nous += Aparell(nom = nom, categoria = cat, font = Font.TIPIC, kwhAny = cat.kwhAnyTipic)
        }
        vm.desaAparells(nous)
        vm.pantalla = Pantalla.PRINCIPAL
        vm.pestanya = Pestanya.APARELLS
    }

    val desplaçament = rememberScrollState()
    LaunchedEffect(pas) { desplaçament.scrollTo(0) }

    // El contingut es desplaça; els botons queden fixos a baix.
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(desplaçament).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LinearProgressIndicator(progress = { (pas + 0.5f) / ESTANCES.size }, Modifier.fillMaxWidth())
            Text(stringResource(R.string.questionari_estanca_n, pas + 1, ESTANCES.size, stringResource(estanca.nom)), fontSize = 14.sp, color = Suau)
            for (p in estanca.preguntes) {
                Targeta {
                    TitolTargeta(stringResource(p.text))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((opcio, _) in p.opcions) {
                            val k = clau(p, opcio)
                            val si = triat[k] == true
                            FilterChip(
                                selected = si,
                                onClick = {
                                    if (p.unica) p.opcions.forEach { (o, _) -> triat[clau(p, o)] = false }
                                    triat[k] = !si
                                },
                                label = { Text(stringResource(opcio)) },
                                leadingIcon = if (si) ({ Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }) else null,
                            )
                        }
                    }
                }
            }
            Targeta {
                ESTANCES.forEachIndexed { i, e ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (i < pas) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                            tint = if (i < pas) Ok else if (i == pas) Primari else Suau, modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "  " + stringResource(e.nom), fontSize = 13.sp, color = if (i == pas) Primari else Suau,
                            fontWeight = if (i == pas) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Nota(stringResource(R.string.questionari_nota))
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pas > 0) OutlinedButton(onClick = { pas-- }) { Text(stringResource(R.string.questionari_enrere)) }
            Button(
                onClick = { if (pas < ESTANCES.lastIndex) pas++ else acaba() },
                Modifier.weight(1f),
            ) {
                Text(
                    if (pas < ESTANCES.lastIndex) stringResource(R.string.questionari_seguent, stringResource(ESTANCES[pas + 1].nom).lowercase(Idioma.locale))
                    else stringResource(R.string.questionari_fet)
                )
            }
        }
    }
}
