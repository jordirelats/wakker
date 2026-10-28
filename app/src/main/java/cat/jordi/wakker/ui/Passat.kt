package cat.jordi.wakker.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Aparell
import cat.jordi.wakker.dades.Estona
import cat.jordi.wakker.dades.EstonaPassada
import cat.jordi.wakker.dades.Funcionament
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.mediana
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/**
 * Ensenyar un aparell amb una estona passada (maqueta 8): l'aparell, el dia (dels últims 7) i l'estona. L'app hi busca
 * els trams i l'usuari marca els que eren l'aparell.
 */
@Composable
fun PantallaPassat(vm: WakkerViewModel) {
    var aparellId by rememberSaveable { mutableStateOf<Long?>(null) }
    val aparell = vm.aparellsAEntrenar.firstOrNull { it.id == aparellId }
    val estona = vm.estona
    // Enrere des del resultat torna al formulari
    BackHandler(enabled = estona != null) { vm.oblidaEstona() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (estona != null && aparell != null) Resultat(vm, aparell, estona)
        else Formulari(vm, aparellId) { aparellId = it }
        Spacer(Modifier.height(16.dp))
    }
}

private fun dia(fa: Int): LocalDate = LocalDate.now().minusDays(fa.toLong())

/** «dg 25», com a l'Historial. */
private fun diaCurt(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Idioma.locale).removeSuffix(".") + " " + d.dayOfMonth

private fun hhmm(minuts: Int) = "%d:%02d".format(minuts / 60, minuts % 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Formulari(vm: WakkerViewModel, aparellId: Long?, triaAparell: (Long) -> Unit) {
    val context = LocalContext.current
    var fa by rememberSaveable { mutableIntStateOf(1) }
    var de by rememberSaveable { mutableStateOf<Int?>(null) }
    var fins by rememberSaveable { mutableStateOf<Int?>(null) }
    fun rellotge(ara: Int?, perDefecte: Int, desa: (Int) -> Unit) {
        val m = ara ?: perDefecte
        TimePickerDialog(context, { _, h, min -> desa(h * 60 + min) }, m / 60, m % 60, true).show()
    }

    Targeta {
        Text(stringResource(R.string.passat_quin_aparell), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (vm.aparellsAEntrenar.isEmpty()) Nota(stringResource(R.string.entrena_primer_afegeix))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.aparellsAEntrenar.forEach { a -> FilterChip(a.id == aparellId, { triaAparell(a.id) }, label = { Text(a.nom) }) }
        }
        Text(stringResource(R.string.passat_quin_dia), Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in 0..6) {
                val nom = when (k) {
                    0 -> stringResource(R.string.passat_avui)
                    1 -> stringResource(R.string.passat_ahir, diaCurt(dia(1)))
                    else -> diaCurt(dia(k))
                }
                FilterChip(fa == k, { fa = k }, label = { Text(nom) })
            }
        }
        Text(stringResource(R.string.passat_quina_hora), Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ rellotge(de, 17 * 60) { de = it } }, Modifier.weight(1f)) {
                Text(de?.let { stringResource(R.string.passat_de, hhmm(it)) } ?: stringResource(R.string.passat_de_buit))
            }
            OutlinedButton({ rellotge(fins, (de ?: 17 * 60) + 2 * 60) { fins = it } }, Modifier.weight(1f)) {
                Text(fins?.let { stringResource(R.string.passat_fins, hhmm(it)) } ?: stringResource(R.string.passat_fins_buit))
            }
        }
        Nota(stringResource(R.string.passat_nota), Modifier.padding(top = 4.dp))
        if (vm.buscantEstona) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text("  " + stringResource(R.string.passat_buscant), fontSize = 14.sp, color = Suau)
            }
        } else {
            Button(
                onClick = {
                    // L'hora del rellotge d'aquell dia: el dia del canvi d'hora en té 23 o 25, i no es pot sumar a la mitjanit
                    val zona = ZoneId.systemDefault()
                    val desDe = dia(fa).atTime(de!! / 60, de!! % 60).atZone(zona).toEpochSecond()
                    // Si «fins a» és abans de «de», és l'endemà (una estona de nit)
                    val diaFi = if (fins!! <= de!!) dia(fa - 1) else dia(fa)
                    val finsA = diaFi.atTime(fins!! / 60, fins!! % 60).atZone(zona).toEpochSecond()
                        .coerceAtMost(System.currentTimeMillis() / 1000)
                    if (finsA > desDe) vm.buscaEstona(desDe, finsA)
                },
                Modifier.fillMaxWidth().padding(top = 10.dp), enabled = aparellId != null && de != null && fins != null,
            ) { Text(stringResource(R.string.passat_busca)) }
        }
    }
}

@Composable
private fun Resultat(vm: WakkerViewModel, aparell: Aparell, estona: Estona) {
    val zona = ZoneId.systemDefault()
    val dia = Instant.ofEpochSecond(estona.desDe).atZone(zona).toLocalDate()
    val diaLlarg = dia.dayOfWeek.getDisplayName(TextStyle.FULL, Idioma.locale) + " " + dia.dayOfMonth
    val trams = estona.trams
    // Els marcats; per defecte, els semblants al més gran
    var triats by rememberSaveable(estona.desDe, estona.finsA) { mutableStateOf(EstonaPassada.proposats(trams).toList()) }
    val dins = estona.punts.count { it.ts in estona.desDe until estona.finsA }
    Targeta {
        Text(
            stringResource(R.string.passat_titol, aparell.nom, diaLlarg, horaMinut(estona.desDe), horaMinut(estona.finsA)),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
        when {
            estona.aproximada -> Nota(stringResource(R.string.passat_aproximada), Modifier.padding(top = 6.dp))
            dins < 5 -> Nota(stringResource(R.string.passat_sense_dades), Modifier.padding(top = 6.dp))
            trams.isEmpty() -> {
                GraficaEstona(estona, vm.config.teLinia, emptySet())
                Nota(stringResource(R.string.passat_cap_salt), Modifier.padding(top = 6.dp))
            }
            else -> {
                GraficaEstona(estona, vm.config.teLinia, triats.toSet())
                Text(pluralStringResource(R.plurals.passat_trobats, trams.size, trams.size), Modifier.padding(top = 6.dp), fontSize = 13.sp)
                val proposats = EstonaPassada.proposats(trams)
                trams.forEachIndexed { i, t ->
                    val marcat = i in triats
                    Row(
                        Modifier.fillMaxWidth().toggleable(marcat, role = Role.Checkbox) { triats = if (it) triats + i else triats - i },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(marcat, null)
                        Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
                            Text(
                                stringResource(R.string.passat_tram, horaMinut(t.ts), horaMinut(t.fi), "${num(t.w / 1000, 1)} kW"),
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            )
                            Text(
                                stringResource(R.string.passat_tram_detall, t.minuts, num(t.kwh, 2)) +
                                    if (i in proposats) "" else stringResource(R.string.passat_tram_petit),
                                fontSize = 12.sp, color = Suau,
                            )
                        }
                    }
                }
            }
        }
        TextButton(onClick = vm::oblidaEstona) { Text(stringResource(R.string.passat_canvia)) }
    }
    val marcats = triats.sorted().mapNotNull { trams.getOrNull(it) }
    if (marcats.isNotEmpty()) {
        val sol = aparell.funciona == Funcionament.SOL
        val mostres = marcats.flatMap { it.mostres }
        Targeta {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.passat_quan_funciona), Modifier.weight(1f), fontSize = 14.sp)
                val tots = marcats.flatMap { it.exces }.sorted()
                Text(
                    if (sol) "${num(tots[tots.size / 10] * 0.8)}–${num(tots.last() * 1.2)} W" else "${num(mediana(mostres))} W",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(stringResource(R.string.passat_aquesta_vegada), Modifier.weight(1f), fontSize = 14.sp)
                Text("${num(marcats.sumOf { it.kwh }, 2)} kWh", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Nota(
                if (sol) stringResource(R.string.passat_interval_nota)
                else pluralStringResource(R.plurals.passat_mostres, mostres.size, mostres.size),
                Modifier.padding(top = 4.dp),
            )
            Button({ vm.ensenyaEstona(aparell, marcats) }, Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(stringResource(R.string.passat_desa, aparell.nom))
            }
        }
    }
}
