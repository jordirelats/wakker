@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.wakker.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Com
import cat.jordi.wakker.dades.Ensenyat
import cat.jordi.wakker.dades.OrigenEnsenyat
import cat.jordi.wakker.dades.mediana
import cat.jordi.wakker.dades.Font
import cat.jordi.wakker.dades.Funcionament
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.Vegada
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle

/**
 * La fitxa d'un aparell (maqueta 13): què ha gastat aquest mes i quantes vegades, la seva firma, on està connectat (una
 * sola opció) i les últimes vegades, cadascuna amb com se sap que és seva («ensenyat per tu» o «reconegut», amb «No era
 * ell»).
 */
@Composable
fun PantallaFitxa(vm: WakkerViewModel) {
    val a = vm.aparells.firstOrNull { it.id == vm.aparellObert }
    // Si s'ha esborrat (o encara no s'ha carregat la llista), a la llista
    LaunchedEffect(a == null && vm.aparells.isNotEmpty()) { if (a == null && vm.aparells.isNotEmpty()) vm.pantalla = Pantalla.PRINCIPAL }
    if (a == null) return
    // La fitxa carregada ha de ser d'aquest aparell (si se n'obren dos seguits, no la de l'altre)
    val f = vm.fitxa?.takeIf { it.aparellId == a.id }
    val r = vm.referencies[a.id] ?: vm.referenciesLinia[a.id]
    val nevera = vm.resumMes?.ranquing?.aparells?.firstOrNull { it.aparell?.id == a.id && it.compressorW != null }
    var perConfirmar by remember { mutableStateOf<Boolean?>(null) }
    val preu = vm.resumMes?.preuMitja ?: vm.config.tarifa.preuFix

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Targeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cercle(icona(a.categoria), EstatIcona.SAP, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(a.nom, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        if (vm.config.teLinia && a.linia) Etiqueta(vm.config.nomLinia)
                    }
                    Text(
                        listOf(stringResource(a.categoria.nomId), "${a.marca} ${a.model}".trim()).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 12.sp, color = Suau,
                    )
                }
            }
            // Aquest mes: les vegades (o la nevera, el compressor)
            val kwh = nevera?.kwh ?: f?.kwhMes ?: 0.0
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(num(kwh, 2), style = EstilMitja)
                Text(
                    " kWh   " + if (nevera != null) stringResource(R.string.fitxa_aquest_mes)
                    else pluralStringResource(R.plurals.fitxa_cops_mes, f?.copsMes ?: 0, f?.copsMes ?: 0),
                    Modifier.padding(bottom = 6.dp), fontSize = 14.sp, color = Suau,
                )
            }
            Text(stringResource(R.string.aparells_kwh_euros_any, num(a.kwhAny), num(a.kwhAny * preu)), fontSize = 13.sp, color = Suau)
            when {
                nevera != null -> Text(stringResource(R.string.fitxa_compressor, num(nevera.compressorW ?: 0.0), num(vm.config.neveraCicle * 100)), Modifier.padding(top = 4.dp), fontSize = 13.sp, color = Primari)
                r != null -> {
                    val potencia = if (r.wMin != null) "${num(r.min)}–${num(r.max)} W" else "${num(r.w)} W"
                    Text(
                        stringResource(R.string.fitxa_cada_vegada, listOfNotNull(potencia, textFirma(r)).joinToString(" · ")),
                        Modifier.padding(top = 4.dp), fontSize = 13.sp, color = Primari,
                    )
                }
            }
            a.esperaW?.let { Text(stringResource(R.string.aparells_en_espera, num(it, 1)), fontSize = 13.sp, color = Suau) }
            if (a.classe.isNotBlank()) Text(stringResource(R.string.aparells_classe, a.classe), fontSize = 13.sp, color = Suau)
            if (a.font == Font.TIPIC) Text(stringResource(R.string.aparells_es_tipic), fontSize = 13.sp, color = DesconegutText)
        }

        // On està connectat: una sola opció, només si hi ha la pinça 2
        if (vm.config.teLinia && a.funciona != Funcionament.SEMPRE && !a.nom.equals(vm.config.nomLinia, ignoreCase = true)) Targeta {
            TitolTargeta(stringResource(R.string.fitxa_on_connectat))
            Opcio(!a.linia, stringResource(R.string.fitxa_resta_casa), stringResource(R.string.fitxa_resta_casa_text)) { if (a.linia) vm.posaALinia(a, false) }
            Opcio(a.linia, stringResource(R.string.fitxa_pinca2, vm.config.nomLinia), stringResource(R.string.fitxa_pinca2_text)) { if (!a.linia) vm.posaALinia(a, true) }
        }

        Targeta {
            TitolTargeta(stringResource(R.string.aparells_com_funciona))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Funcionament.entries.forEach { fu ->
                    // Un aparell sempre encès va dins del fons de tota la casa: no pot ser a la línia
                    Xip(stringResource(fu.nomId), triat = a.funciona == fu) { vm.desaAparells(listOf(a.copy(funciona = fu, linia = a.linia && fu != Funcionament.SEMPRE))) }
                }
            }
            Nota(stringResource(a.funciona.textId))
        }

        if (a.funciona != Funcionament.SEMPRE && nevera == null) Targeta {
            TitolTargeta(stringResource(R.string.fitxa_ultimes))
            val ultimes = f?.ultimes.orEmpty()
            if (f != null && ultimes.isEmpty()) Nota(stringResource(R.string.fitxa_cap_vegada))
            ultimes.forEach { v -> FilaVegada(v) { vm.noEraEll(v) } }
            if (ultimes.isNotEmpty()) Nota(stringResource(R.string.fitxa_nota), Modifier.padding(top = 6.dp))
        }

        // El que li has ensenyat (maqueta 17): cada cosa es pot treure, si va ser per error
        val ensenyats = f?.ensenyats.orEmpty()
        if (ensenyats.isNotEmpty()) Targeta {
            TitolTargeta(stringResource(R.string.fitxa_ensenyat_titol))
            ensenyats.forEach { e -> FilaEnsenyat(e) { vm.treuEnsenyat(e) } }
            Nota(stringResource(R.string.fitxa_ensenyat_nota), Modifier.padding(top = 6.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r != null && !r.automatica) TextButton(onClick = { perConfirmar = false }) { Text(stringResource(R.string.aparells_torna_entrenar)) }
            TextButton(onClick = { perConfirmar = true }) { Text(stringResource(R.string.aparells_esborra), color = PuntaText) }
        }
        if (a.eprel.isNotBlank()) Text("EPREL ${a.eprel}", color = Suau, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
    }

    perConfirmar?.let { esborra ->
        AlertDialog(
            onDismissRequest = { perConfirmar = null },
            text = { Text(stringResource(if (esborra) R.string.aparells_confirma_esborra else R.string.aparells_confirma_entrena, a.nom)) },
            confirmButton = {
                TextButton(onClick = {
                    if (esborra) vm.esborraAparell(a.id) else vm.tornaAEntrenar(a.id)
                    perConfirmar = null
                }) {
                    Text(stringResource(if (esborra) R.string.aparells_esborra else R.string.aparells_torna_entrenar), color = PuntaText)
                }
            },
            dismissButton = { TextButton(onClick = { perConfirmar = null }) { Text(stringResource(R.string.escaneig_cancella)) } },
        )
    }
}

@Composable
private fun Opcio(triada: Boolean, titol: String, text: String, tria: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(triada, role = Role.RadioButton, onClick = tria).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(triada, null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(titol, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text, fontSize = 12.sp, color = Suau)
        }
    }
}

/** Quan va ser una vegada: «dg 25 · 17:10». */
@Composable
private fun quan(ts: Long): String {
    val d = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault())
    return "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Idioma.locale).removeSuffix(".")} ${d.dayOfMonth} · ${"%02d:%02d".format(d.hour, d.minute)}"
}

@Composable
private fun FilaVegada(v: Vegada, noEra: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(quan(v.ts), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.fitxa_vegada_detall, v.minuts, num(v.w / 1000, 1)), fontSize = 12.sp, color = Suau)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${num(v.kwh, 2)} kWh", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            // «No era ell» a totes (maqueta 17): també a les que l'usuari havia dit per error
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (v.com == Com.ENSENYAT) R.string.fitxa_ensenyat else R.string.fitxa_reconegut), fontSize = 11.sp, color = Suau)
                Text(stringResource(R.string.fitxa_no_era), Modifier.clickable(onClick = noEra).padding(vertical = 4.dp), fontSize = 11.5.sp, color = Suau, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
            }
        }
    }
}

/** Una cosa ensenyada: quan (o de quan a quan), d'on ve, i «Treu-ho». */
@Composable
private fun FilaEnsenyat(e: Ensenyat, treu: () -> Unit) {
    val ws = e.mostres.map { it.dp }
    val w = if (ws.isEmpty()) "" else num(mediana(ws))
    // Una estona del mateix dia, de quan a quan: «dg 25 · 17:10–18:41»; si no, quan va començar
    val zona = ZoneId.systemDefault()
    val inici = Instant.ofEpochSecond(e.desDe).atZone(zona)
    val fi = Instant.ofEpochSecond(e.finsA).atZone(zona)
    val quan = if (e.finsA - e.desDe >= 60 && inici.toLocalDate() == fi.toLocalDate()) "${quan(e.desDe)}–${"%02d:%02d".format(fi.hour, fi.minute)}" else quan(e.desDe)
    val que = when (e.origen) {
        OrigenEnsenyat.ESTONA -> pluralStringResource(R.plurals.fitxa_ensenyat_estona, e.mostres.size, e.mostres.size, w)
        OrigenEnsenyat.DIRECTE -> stringResource(R.string.fitxa_ensenyat_directe, w)
        OrigenEnsenyat.VEGADA -> stringResource(R.string.fitxa_ensenyat_vegada)
        OrigenEnsenyat.PREGUNTA -> stringResource(R.string.fitxa_ensenyat_pregunta)
        OrigenEnsenyat.PIC -> stringResource(R.string.fitxa_ensenyat_pic)
        // Tots els cops (de cada un n'hi ha l'etiqueta; mostres, només dels 12 últims)
        OrigenEnsenyat.REPETIT -> maxOf(e.etiquetes.size, e.mostres.size).let { pluralStringResource(R.plurals.fitxa_ensenyat_repetit, it, it) }
        OrigenEnsenyat.RESPOSTA -> stringResource(R.string.fitxa_ensenyat_resposta)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(quan, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(que, fontSize = 12.sp, color = Suau)
        }
        Text(stringResource(R.string.fitxa_treu), Modifier.clickable(onClick = treu).padding(8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primari)
    }
}
