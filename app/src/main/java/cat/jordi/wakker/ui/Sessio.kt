@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.wakker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.EstonaSessio
import cat.jordi.wakker.dades.Funcionament
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.Repositori.EstatMarca
import cat.jordi.wakker.dades.Vegada
import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * L'aparell que s'està ensenyant d'inici a fi (maqueta 21), a Ara i a «Per fer»: mentre és engegat, des de quan i el que hi
 * posa ara, amb «L'he apagat» i «Cancel·la»; un cop apagat, l'estona per revisar.
 */
@Composable
fun TargetaSessio(vm: WakkerViewModel) {
    val s = vm.sessio ?: return
    var ara by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(s.finsA) {
        while (s.finsA == null) {
            ara = System.currentTimeMillis() / 1000
            delay(30_000)
        }
    }
    var perCancellar by remember { mutableStateOf(false) }
    Targeta {
        val acabada = s.finsA != null
        Text(
            stringResource(if (acabada) R.string.sessio_per_revisar else R.string.sessio_aprenent).uppercase(Idioma.locale),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (acabada) DesconegutText else Primari, letterSpacing = 0.06.em,
        )
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Cercle(icona(s.aparell.categoria), if (acabada) EstatIcona.CAL else EstatIcona.SAP, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.aparell.nom, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (acabada) stringResource(R.string.sessio_de_a, horaMinut(s.desDe), horaMinut(s.finsA!!))
                    else stringResource(R.string.sessio_engegat, horaMinut(s.desDe), durada(((ara - s.desDe) / 60).toInt().coerceAtLeast(0))),
                    fontSize = 12.sp, color = Suau,
                )
            }
            if (!acabada) vm.wSessio(vm.lectura)?.let { w ->
                Column(horizontalAlignment = Alignment.End) {
                    Text("${num(w)} W", style = EstilNumero)
                    Text(stringResource(R.string.sessio_per_sobre), fontSize = 10.5.sp, color = Suau)
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (acabada) {
                Button(onClick = vm::obreSessio, Modifier.weight(1f)) { Text(stringResource(R.string.sessio_revisa)) }
                OutlinedButton(onClick = { perCancellar = true }, Modifier.weight(1f)) { Text(stringResource(R.string.sessio_descarta)) }
            } else {
                Button(onClick = vm::acabaSessio, Modifier.weight(1f)) { Text(stringResource(R.string.sessio_apagat)) }
                OutlinedButton(onClick = { perCancellar = true }, Modifier.weight(1f)) { Text(stringResource(R.string.sessio_cancella)) }
            }
        }
        if (!acabada) Nota(stringResource(R.string.sessio_nota), Modifier.padding(top = 6.dp))
    }
    if (perCancellar) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { perCancellar = false },
            text = { Text(stringResource(R.string.sessio_confirma_cancella, s.aparell.nom)) },
            confirmButton = { TextButton(onClick = { vm.oblidaSessio(); perCancellar = false }) { Text(stringResource(R.string.sessio_descarta), color = PuntaText) } },
            dismissButton = { TextButton(onClick = { perCancellar = false }) { Text(stringResource(R.string.escaneig_cancella)) } },
        )
    }
}

/**
 * «L'he apagat» (maqueta 21): el que Wakker n'ha vist. La gràfica de l'estona (la casa abans, l'aparell en verd i el que
 * s'hi ha encès a sobre en gris), quant ha gastat, entre quins W va quan funciona, el pic en arrencar, i si va a cicles. Les
 * coses de sobre que no semblen seves no compten, però l'usuari pot dir que sí que ho eren.
 */
@Composable
fun PantallaSessio(vm: WakkerViewModel) {
    val e = vm.estonaSessio
    // Si Android ha tancat l'app amb aquesta pantalla oberta, l'estona es torna a buscar
    LaunchedEffect(e == null, vm.sessio?.finsA) {
        if (e == null && !vm.buscantSessio && vm.sessio?.finsA != null) vm.obreSessio()
    }
    val nom = vm.aparells.firstOrNull { it.id == (e?.aparellId ?: vm.sessio?.aparell?.id) }?.nom.orEmpty()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            vm.buscantSessio -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.sessio_buscant), color = Suau)
            }
            e == null -> {
                Nota(stringResource(R.string.sessio_cap))
                OutlinedButton(onClick = vm::enrere) { Text(stringResource(R.string.sessio_d_acord)) }
            }
            e.aproximada -> {
                Targeta {
                    Text(stringResource(R.string.sessio_de_a_nom, nom, horaMinut(e.desDe), horaMinut(e.finsA)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Nota(stringResource(R.string.sessio_sense_minuts), Modifier.padding(top = 6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.pantalla = Pantalla.PRINCIPAL; vm.pestanya = Pestanya.PER_FER }, Modifier.weight(1f)) { Text(stringResource(R.string.sessio_d_acord)) }
                    OutlinedButton(onClick = vm::oblidaSessio, Modifier.weight(1f)) { Text(stringResource(R.string.sessio_descarta)) }
                }
            }
            else -> ResultatSessio(vm, e, nom)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultatSessio(vm: WakkerViewModel, e: EstonaSessio, nom: String) {
    // Les coses de sobre que l'usuari diu que sí que eren seves (per l'inici, que no canvia)
    var seves by rememberSaveable(e.desDe) { mutableStateOf(emptyList<Long>()) }
    val fora = e.alienes.filter { it.ts !in seves }
    val f = remember(e, fora) { e.firma(fora) }
    val preu = vm.resumMes?.preuMitja ?: vm.config.tarifa.preuFix
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(nom, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.sessio_de_a, horaMinut(e.desDe), horaMinut(e.finsA)), fontSize = 12.5.sp, color = Suau)
    }
    Targeta {
        GraficaSessio(e, fora)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Llegenda(PrimariClar, stringResource(R.string.sessio_llegenda_seu, nom))
            if (fora.isNotEmpty()) Llegenda(GrisBarra, stringResource(R.string.sessio_llegenda_altra))
        }
    }
    if (f == null) {
        Targeta(color = DesconegutFluix) { Text(stringResource(R.string.sessio_res), fontSize = 13.sp, lineHeight = 18.sp) }
    } else {
        val v = f.vegada
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DadaSessio(stringResource(R.string.sessio_ha_gastat), "${num(v.kwh, if (v.kwh < 1) 2 else 1)} kWh", "${euros(v.kwh * preu)} · ${durada(v.minuts)}", Modifier.weight(1f))
            DadaSessio(
                stringResource(R.string.sessio_quan_funciona), "${deu(f.wMin)}–${deu(f.wMax)} W",
                stringResource(R.string.sessio_del_temps, num(f.partEnces * 100)), Modifier.weight(1f),
            )
            DadaSessio(stringResource(R.string.sessio_en_arrencar), "${deu(f.pic)} W", stringResource(R.string.sessio_primers_minuts), Modifier.weight(1f))
        }
        Targeta {
            TitolTargeta(stringResource(R.string.sessio_com_es))
            FilaCom(
                if (f.aCicles) Icons.Outlined.Autorenew else Icons.Outlined.Remove,
                stringResource(if (f.aCicles) R.string.sessio_cicles else R.string.sessio_constant),
                stringResource(if (f.aCicles) R.string.sessio_cicles_text else R.string.sessio_constant_text),
            )
        }
    }
    if (e.alienes.isNotEmpty()) Targeta {
        TitolTargeta(stringResource(R.string.sessio_a_sobre))
        Nota(stringResource(R.string.sessio_a_sobre_text))
        e.alienes.forEach { a ->
            val seva = a.ts in seves
            Row(
                Modifier.fillMaxWidth().toggleable(seva, role = Role.Checkbox) { seves = if (it) seves + a.ts else seves - a.ts }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cercle(Icons.Outlined.Layers, if (seva) EstatIcona.SAP else EstatIcona.GRIS, 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${horaMinut(a.ts)}–${horaMinut(a.fi + 60)} · ${num(a.w / 1000, 1)} kW", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(if (seva) R.string.sessio_era_seu else R.string.sessio_no_compta, num(a.kwh, 2)),
                        fontSize = 11.5.sp, color = Suau,
                    )
                }
                Checkbox(seva, null)
            }
        }
    }
    Button(onClick = { vm.desaSessio(e, fora) }, Modifier.fillMaxWidth(), enabled = f != null) { Text(stringResource(R.string.sessio_desa)) }
    TextButton(onClick = vm::oblidaSessio, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sessio_descarta), color = Suau) }
}

@Composable
private fun Llegenda(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 11.sp, color = Suau)
    }
}

/** Arrodonit a la desena: «200–450 W», no «213–447 W». */
private fun deu(w: Double) = num(Math.round(w / 10) * 10.0)

@Composable
private fun DadaSessio(etiqueta: String, valor: String, sota: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Superficie, border = BorderStroke(1.dp, Vora)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(etiqueta.uppercase(Idioma.locale), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Suau, letterSpacing = 0.05.em, maxLines = 1)
            Text(valor, style = EstilNumero, maxLines = 2)
            Text(sota, fontSize = 10.5.sp, color = Suau, lineHeight = 13.sp, maxLines = 2)
        }
    }
}

@Composable
private fun FilaCom(icona: androidx.compose.ui.graphics.vector.ImageVector, titol: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icona, null, Modifier.padding(top = 2.dp).size(18.dp), tint = Primari)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(titol, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(text, fontSize = 12.sp, color = Suau, lineHeight = 16.sp)
        }
    }
}

/** L'estona i la mitja hora d'abans: la potència, la base de la casa (de punts), l'aparell en verd i el que no és seu en gris. */
@Composable
private fun GraficaSessio(e: EstonaSessio, fora: List<Vegada>) {
    val mesura = rememberTextMeasurer()
    val context = androidx.compose.ui.platform.LocalContext.current
    val descripcio = stringResource(R.string.sessio_desc_grafica, horaMinut(e.desDe), horaMinut(e.finsA))
    val punts = e.punts
    Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(150.dp).semantics { contentDescription = descripcio }) {
        if (punts.size < 2) return@Canvas
        val peu = 20.dp.toPx()
        val alt = size.height - peu
        val maxim = maxOf(punts.maxOf { it.second }, e.base + 200) * 1.05
        val pasKw = pas(maxim / 1000)
        val ymax = ceil(maxim / 1000 / pasKw) * pasKw * 1000
        val etiquetes = generateSequence(0.0) { it + pasKw }.takeWhile { it <= ymax / 1000 + 1e-9 }
            .map { kw -> kw to context.getString(R.string.grafiques_kw, num(kw, if (pasKw < 1) 1 else 0)) }.toList()
        val marge = etiquetes.maxOf { mesura.measure(it.second, EstilEix).size.width } + 6.dp.toPx()
        val ample = size.width - marge
        val t0 = punts.first().first.toFloat()
        val t1 = (punts.last().first + 60).toFloat().coerceAtLeast(t0 + 1)
        fun x(ts: Long) = marge + (ts - t0) / (t1 - t0) * ample
        fun y(v: Double) = alt - (v / ymax).toFloat().coerceIn(0f, 1f) * alt
        for ((kw, text) in etiquetes) {
            val yy = y(kw * 1000)
            if (kw > 0) drawLine(Graella, Offset(marge, yy), Offset(size.width, yy))
            val mida = mesura.measure(text, EstilEix).size
            drawText(mesura, text, Offset(marge - 4.dp.toPx() - mida.width, yy - mida.height / 2f), EstilEix)
        }
        // Cada minut de l'estona: per sobre de la base, en verd si és seu i en gris si no
        val yBase = y(e.base)
        for ((ts, w) in punts) {
            if (ts < e.desDe || ts >= e.finsA || w <= e.base) continue
            val aliena = fora.any { ts in it.ts..it.fi }
            drawRect(if (aliena) GrisBarra else PrimariClar, Offset(x(ts), y(w)), Size(x(ts + 60) - x(ts) + 0.5f, yBase - y(w)))
        }
        drawLine(GrisBarra, Offset(marge, alt), Offset(size.width, alt))
        val ratlla = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        drawLine(Suau, Offset(marge, yBase), Offset(size.width, yBase), 1.2f, pathEffect = ratlla)
        for (ts in listOf(e.desDe, e.finsA)) drawLine(Primari, Offset(x(ts), 0f), Offset(x(ts), alt), 1.5f, pathEffect = ratlla)
        val linia = Path().apply { punts.forEachIndexed { i, (ts, w) -> if (i == 0) moveTo(x(ts), y(w)) else lineTo(x(ts), y(w)) } }
        drawPath(linia, Primari, style = Stroke(1.4.dp.toPx()))
        for (ts in listOf(e.desDe, e.finsA)) {
            val etiqueta = horaMinut(ts)
            val mida = mesura.measure(etiqueta, EstilEix).size
            val xx = (x(ts) - mida.width / 2).coerceIn(marge, size.width - mida.width)
            drawText(mesura, etiqueta, Offset(xx, alt + 4.dp.toPx()), EstilEix)
        }
    }
}

/**
 * «Què hi ha encès ara?» (maqueta 21): l'usuari marca el que sap segur que funciona ara, i Wakker ho compara amb el que veu.
 * El que no tenia nom passa a ser de l'aparell marcat. Els que no es marquen no diuen res: no vol dir que siguin apagats.
 */
@Composable
fun PantallaMarca(vm: WakkerViewModel) {
    var marcats by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val resultat = vm.resultatMarca
    val noms = vm.aparells.associate { it.id to it.nom }
    // El que Wakker veu ara: les vegades que encara duren (amb nom o sense)
    val obertes = vm.obertes?.takeIf { it.fresques }?.vegades.orEmpty()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (resultat != null) {
            Targeta {
                TitolTargeta(stringResource(R.string.marca_veia))
                if (resultat.isEmpty()) Nota(stringResource(R.string.marca_cap))
                resultat.forEach { r ->
                    val (icona, estat, text) = when (r.estat) {
                        EstatMarca.QUADRA -> Triple(Icons.Outlined.Check, EstatIcona.SAP, stringResource(R.string.marca_quadra, num(r.w ?: 0.0)))
                        EstatMarca.ERA_SENSE_NOM -> Triple(Icons.Outlined.Check, EstatIcona.SAP, stringResource(R.string.marca_era_sense_nom, num(r.w ?: 0.0)))
                        EstatMarca.NO_VIST -> Triple(Icons.Outlined.HelpOutline, EstatIcona.CAL, stringResource(R.string.marca_no_vist))
                    }
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cercle(icona, estat, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.aparell.nom, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text, fontSize = 12.sp, color = Suau, lineHeight = 16.sp)
                        }
                    }
                }
            }
            Button(onClick = { vm.pantalla = Pantalla.PRINCIPAL; vm.pestanya = Pestanya.PER_FER }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sessio_d_acord)) }
            return@Column
        }
        Nota(stringResource(R.string.marca_nota), Modifier.padding(horizontal = 4.dp))
        if (vm.marcaFora) Targeta(color = DesconegutFluix) { Text(stringResource(R.string.marca_fora), fontSize = 13.sp, lineHeight = 18.sp) }
        // Els que poden funcionar ara (no els sempre encesos, que ja van dins del fons); a dalt, els que Wakker ja veu
        val llista = (vm.aparellsAEntrenar + vm.aparellsLinia).filter { it.funciona != Funcionament.SEMPRE }.distinctBy { it.id }
            .sortedWith(compareBy({ a -> obertes.none { it.aparellId == a.id } }, { it.nom.lowercase(Idioma.locale) }))
        Targeta {
            if (llista.isEmpty()) Nota(stringResource(R.string.entrena_primer_afegeix))
            llista.forEach { a ->
                val triat = a.id in marcats
                val veu = obertes.firstOrNull { it.aparellId == a.id }
                Row(
                    Modifier.fillMaxWidth().toggleable(triat, role = Role.Checkbox) { marcats = if (it) marcats + a.id else marcats - a.id }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(triat, null)
                    Spacer(Modifier.width(4.dp))
                    Cercle(icona(a.categoria), if (triat) EstatIcona.SAP else EstatIcona.NEUTRE, 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.nom, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        if (veu != null) Text(stringResource(R.string.marca_el_veu, num(veu.w)), fontSize = 11.5.sp, color = Primari)
                    }
                }
            }
        }
        Targeta {
            TitolTargeta(stringResource(R.string.marca_veu_ara))
            if (obertes.isEmpty()) Nota(stringResource(R.string.marca_veu_res))
            obertes.sortedByDescending { it.w }.forEach { v ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(v.aparellId?.let { noms[it] } ?: stringResource(R.string.marca_sense_nom), Modifier.weight(1f), fontSize = 13.sp, color = if (v.aparellId == null) DesconegutText else TextPrincipal)
                    Text("${num(v.w)} W · ${stringResource(R.string.marca_des_de, horaMinut(v.ts))}", fontSize = 12.sp, color = Suau)
                }
            }
        }
        Button(onClick = { vm.marcaEncesos(marcats.toSet()) }, Modifier.fillMaxWidth(), enabled = marcats.isNotEmpty() && !vm.marcant) {
            Text(stringResource(R.string.marca_desa))
        }
    }
}
