@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.wakker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Aparell
import cat.jordi.wakker.dades.Funcionament
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.Pregunta
import cat.jordi.wakker.dades.Punt
import cat.jordi.wakker.dades.Referencia
import cat.jordi.wakker.dades.Repetit
import cat.jordi.wakker.dades.VegadaSenseNom
import cat.jordi.wakker.dades.endevina
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun hora(ts: Long) = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/**
 * «Per fer» (maqueta 18): la safata única amb tot el que l'app pregunta. A dalt, el que més gasta i encara no té nom; després
 * el que es repeteix sense nom i els avisos de consum inesperat. Cada cosa és una targeta amb els aparells que s'hi assemblen,
 * «Tots els aparells…» i «No ho sé». A sota, «Ensenya-li tu»: una estona passada, o en directe. Substitueix Entrena.
 */
@Composable
fun PantallaPerFer(vm: WakkerViewModel) {
    val avisos = rememberPermisAvisos()
    var directe by rememberSaveable { mutableStateOf(false) }
    // Mentre es mesura (28 s), la pantalla no s'apaga: amb la pantalla apagada no arriben lectures
    val vista = LocalView.current
    val mesurant = vm.mesurant != null
    DisposableEffect(mesurant) {
        vista.keepScreenOn = mesurant
        onDispose { vista.keepScreenOn = false }
    }
    val senseNom = vm.senseNom?.vegades.orEmpty()
    val res = senseNom.isEmpty() && vm.repetits.isEmpty() && vm.preguntes.isEmpty()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!avisos.actius) {
            // El botó a sota del text: amb la lletra gran, al costat el títol feia tres línies (1.1.3)
            Targeta(color = DesconegutFluix) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.NotificationsOff, null, tint = Desconegut, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.entrena_avisos_titol), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.perfer_avisos_text), fontSize = 12.sp, color = Suau, lineHeight = 16.sp)
                        Button(onClick = avisos.demana, Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.perfer_activa)) }
                    }
                }
            }
        }
        // El que més gasta i encara no té nom (maqueta 15), cada vegada una targeta
        senseNom.forEach { x -> key(x.vegada.ts) { TargetaSenseNom(vm, x) } }
        // El que es repeteix sempre igual (maqueta 12)
        vm.repetits.forEach { r -> key("r${r.id}") { TargetaRepetit(vm, r) } }
        // Els avisos de consum inesperat
        vm.preguntes.forEach { p -> key(p.id) { TargetaPregunta(vm, p) } }
        if (res) {
            Targeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cercle(Icons.Outlined.Check, EstatIcona.SAP)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.perfer_res), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.perfer_res_text), fontSize = 12.5.sp, color = Suau, lineHeight = 17.sp)
                    }
                }
            }
        }

        // Ensenyar-li tu: una estona passada (maqueta 8) o en directe
        Surface(Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(16.dp), color = Fons, border = BorderStroke(1.dp, Vora)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.perfer_ensenya), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.perfer_ensenya_text), fontSize = 12.sp, color = Suau)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.oblidaEstona(); vm.pantalla = Pantalla.PASSAT }, Modifier.weight(1f), enabled = vm.aparellsAEntrenar.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Icon(Icons.Outlined.History, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.perfer_estona), fontSize = 12.5.sp, lineHeight = 15.sp)
                    }
                    OutlinedButton(onClick = { directe = !directe }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.perfer_directe), fontSize = 12.5.sp, lineHeight = 15.sp)
                    }
                }
                if (directe) EnDirecte(vm)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Ensenyar-li en directe: la lectura d'ara i cada aparell amb «L'he encès» / «L'he apagat». */
@Composable
private fun EnDirecte(vm: WakkerViewModel) {
    Column(Modifier.padding(top = 10.dp)) {
        Nota(stringResource(R.string.entrena_instruccions))
        Spacer(Modifier.height(6.dp))
        val l = vm.lectura
        LiniaEstat(
            when {
                l == null -> stringResource(R.string.entrena_sense_lectura)
                vm.config.teLinia -> stringResource(R.string.entrena_ara_w_sense_pinca, num(vm.resta(l)))
                else -> stringResource(R.string.entrena_ara_w, num(vm.resta(l)))
            },
            if (l != null) Ok else Desconegut,
        )
        if (vm.missatgeDe == null) {
            vm.missatgeEntrena?.let { Text(it.text(), Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Primari) }
        }
        // Només s'entrenen els que engega l'usuari: els que estan sempre encesos o s'engeguen sols, no cal
        val perEntrenar = vm.aparellsAEntrenar.filter { it.funciona == Funcionament.JO }
        if (vm.aparells.isEmpty()) Nota(stringResource(R.string.entrena_primer_afegeix), Modifier.padding(top = 8.dp))
        perEntrenar.forEach { a -> FilaEntrena(vm, a, vm.referencies[a.id]) }
        if (vm.aparells.any { it.funciona != Funcionament.JO }) Nota(stringResource(R.string.entrena_nota_sense_entrenar), Modifier.padding(top = 8.dp))
        Nota(stringResource(R.string.entrena_nota_casa), Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun FilaEntrena(vm: WakkerViewModel, a: Aparell, r: Referencia?) {
    val encès = a.id in vm.encesos
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Cercle(icona(a.categoria), if (r?.apresa == true) EstatIcona.SAP else EstatIcona.NEUTRE)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.nom, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        r == null -> stringResource(R.string.entrena_cap_mostra)
                        r.apresa -> pluralStringResource(R.plurals.entrena_apres, r.mostres, num(r.w), r.mostres)
                        else -> stringResource(R.string.entrena_mostres_de, num(r.w), r.mostres, Referencia.PER_APRENDRE)
                    },
                    fontSize = 11.5.sp, color = if (r?.apresa == true) Ok else Suau,
                )
            }
            when {
                vm.mesurant == a.id -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  " + stringResource(R.string.entrena_mesurant), fontSize = 13.sp, color = Suau)
                }
                encès -> Button(onClick = { vm.ensenya(a, false) }, enabled = vm.mesurant == null && vm.lectura != null) { Text(stringResource(R.string.entrena_he_apagat)) }
                else -> OutlinedButton(onClick = { vm.ensenya(a, true) }, enabled = vm.mesurant == null && vm.lectura != null) { Text(stringResource(R.string.entrena_he_ences)) }
            }
        }
        // El resultat de la mesura, a sota de l'aparell que s'ha mesurat
        if (vm.missatgeDe == a.id && vm.mesurant == null) {
            vm.missatgeEntrena?.let { Text(it.text(), Modifier.padding(start = 46.dp, bottom = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primari) }
        }
    }
}

/**
 * Una targeta de «Per fer»: la secció en ambre i majúscules (amb el que vagi a la dreta), el títol i el detall, la forma a la
 * dreta i, si n'hi ha, els kWh i els euros. A sota, els xips per contestar.
 */
@Composable
private fun TargetaPerFer(
    seccio: String, titol: String, detall: String, kwh: Double? = null, euros: Double? = null, subEuros: String? = null,
    dretaSeccio: @Composable () -> Unit = {}, forma: @Composable () -> Unit = {}, peu: @Composable () -> Unit = {},
    xips: @Composable FlowRowScope.() -> Unit,
) {
    Targeta {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(seccio.uppercase(Idioma.locale), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DesconegutText, letterSpacing = 0.06.em)
            Spacer(Modifier.weight(1f))
            dretaSeccio()
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(titol, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(detall, fontSize = 11.5.sp, color = Suau, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            forma()
            if (kwh != null) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${num(kwh, 1)} kWh", style = EstilNumero)
                    val sota = subEuros ?: euros?.let { euros(it) }
                    if (sota != null) Text(sota, fontSize = 11.sp, color = Suau)
                }
            }
        }
        peu()
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = xips)
    }
}

/** «S'engega sol», a la línia de la secció: si ho és, l'app n'aprèn l'interval de potència i no n'avisa més. */
@Composable
private fun SEngegaSol(sol: Boolean, canvia: (Boolean) -> Unit) {
    Row(Modifier.toggleable(sol, role = Role.Switch, onValueChange = canvia), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.funciona_sol), fontSize = 12.sp, color = Suau)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) { Switch(sol, null, Modifier.scale(0.7f)) }
    }
}

/** Els xips de resposta: els que s'hi assemblen, «Tots els aparells…» i «No ho sé». */
@Composable
private fun FlowRowScope.XipsResposta(suggerits: List<Aparell>, tria: (Aparell) -> Unit, tots: () -> Unit, noHoSe: () -> Unit) {
    suggerits.forEach { a -> Xip(a.nom) { tria(a) } }
    Xip(stringResource(R.string.tria_tots), color = PrimariFluix, onClick = tots)
    Xip(stringResource(R.string.entrena_no_ho_se), onClick = noHoSe)
}

/** Una vegada grossa dels últims 7 dies que no té nom (maqueta 15): quan, quant, la forma, els kWh i els euros. */
@Composable
private fun TargetaSenseNom(vm: WakkerViewModel, x: VegadaSenseNom) {
    val v = x.vegada
    var triant by rememberSaveable { mutableStateOf(false) }
    val zona = ZoneId.systemDefault()
    val dia = Instant.ofEpochSecond(v.ts).atZone(zona).format(DateTimeFormatter.ofPattern(stringResource(R.string.format_dia_curt), Idioma.locale))
    val suggerits = x.candidats.take(3)
    TargetaPerFer(
        stringResource(R.string.perfer_posa_nom), stringResource(R.string.sense_nom_quan, dia, hora(v.ts)),
        stringResource(R.string.sense_nom_detall, durada(v.minuts), num(v.w / 1000, 1)), v.kwh, x.euros,
        forma = { FormaVegada(x.punts, v.ts, v.fi + 60) },
    ) {
        XipsResposta(suggerits, { vm.responVegada(v, it.id) }, { triant = true }) { vm.ignoraVegada(v) }
    }
    if (triant) {
        // Tots els que poden ser-ho: els de la resta de la casa (i els de la pinça 2 si és d'abans de la pinça)
        TriaAparell(
            stringResource(R.string.sense_nom_full, dia, hora(v.ts), num(v.w / 1000, 1)), suggerits, x.candidats, vm.config.teLinia,
            tria = { vm.responVegada(v, it.id) }, nou = { vm.responVegada(v, null, it) }, tanca = { triant = false },
            senseLinia = vm.config.teLinia && x.candidats.none { it.linia } && vm.aparellsLinia.isNotEmpty(), nomLinia = vm.config.nomLinia,
        )
    }
}

/**
 * Una cosa que es repeteix sempre igual i encara no se sap què és (maqueta 12): quant dura i cada quant, quants cops, la
 * potència i com és, i la setmana en una ratlla. Amb una resposta, l'app n'aprèn tots els cops.
 */
@Composable
private fun TargetaRepetit(vm: WakkerViewModel, r: Repetit) {
    var totes by rememberSaveable { mutableStateOf(false) }
    // Si sembla que s'engega sol (de nit, molts cops al dia), l'app n'aprendrà l'interval i no n'avisarà
    var sol by remember(r.id) { mutableStateOf(r.sembla(ZoneId.systemDefault())) }
    val minuts = num(r.minuts)
    val titol = when {
        r.cadaMinuts >= 20 * 60 -> stringResource(R.string.repetit_un_cop_al_dia, minuts)
        r.cadaMinuts >= 90 -> stringResource(R.string.repetit_cada_hores, minuts, num(r.cadaMinuts / 60))
        else -> stringResource(R.string.repetit_cada_minuts, minuts, num(r.cadaMinuts))
    }
    val detall = listOfNotNull(
        pluralStringResource(R.plurals.repetit_cops, r.cops.size, r.cops.size), "${num(r.wMin)}–${num(r.wMax)} W",
        r.forma?.let { stringResource(it.textId) }, stringResource(r.quan(ZoneId.systemDefault()).textId),
    ).joinToString(" · ")
    val ordenats = endevina(r.w, vm.aparellsAEntrenar, vm.referencies, 3, r.minuts, r.forma)
    TargetaPerFer(
        stringResource(R.string.perfer_es_repeteix), titol, detall, r.kwh, null, subEuros = stringResource(R.string.perfer_la_setmana),
        dretaSeccio = { SEngegaSol(sol) { sol = it } },
        forma = { r.forma?.let { DibuixForma(it, Desconegut) } },
        peu = { TiraSetmana(r.cops.map { it.first }, Desconegut, stringResource(R.string.repetit_tira, r.cops.size)) },
    ) {
        XipsResposta(ordenats, { vm.responRepetit(r, it.id, sol) }, { totes = true }) { vm.responRepetit(r, null, false) }
    }
    if (totes) {
        TriaAparell(
            titol, ordenats, vm.aparellsAEntrenar, vm.config.teLinia,
            tria = { vm.responRepetit(r, it.id, sol) }, nou = { vm.responRepetit(r, null, sol, it) }, tanca = { totes = false },
            senseLinia = vm.aparellsLinia.isNotEmpty(), nomLinia = vm.config.nomLinia,
        )
    }
}

/** Un avís de consum inesperat que encara no s'ha contestat: quan, quant de més, i la potència de l'estona. */
@Composable
private fun TargetaPregunta(vm: WakkerViewModel, p: Pregunta) {
    var totes by rememberSaveable { mutableStateOf(false) }
    var sol by remember { mutableStateOf(false) }
    val zona = ZoneId.systemDefault()
    val dia = Instant.ofEpochSecond(p.ts).atZone(zona).format(DateTimeFormatter.ofPattern(stringResource(R.string.format_dia_curt), Idioma.locale))
    val punts = vm.serie.filter { it.ts in (p.ts - 15 * 60)..(p.ts + 60 * 60) }
    val ordenats = endevina(p.dp, vm.aparellsAEntrenar, vm.referencies, 3)
    TargetaPerFer(
        stringResource(R.string.perfer_avis), stringResource(R.string.sense_nom_quan, dia, hora(p.ts)), stringResource(R.string.perfer_avis_detall, num(p.dp / 1000, 1)),
        dretaSeccio = { SEngegaSol(sol) { sol = it } },
        forma = { if (punts.size > 5) MiniGrafica(punts, p.ts) },
    ) {
        XipsResposta(ordenats, { vm.respon(p, it.id, sol) }, { totes = true }) { vm.respon(p, null) }
    }
    if (totes) {
        TriaAparell(
            stringResource(R.string.entrena_pregunta_text, hora(p.ts), num(p.dp)), ordenats, vm.aparellsAEntrenar, vm.config.teLinia,
            tria = { vm.respon(p, it.id, sol) }, nou = { vm.respon(p, null, sol, it) }, tanca = { totes = false },
            senseLinia = vm.aparellsLinia.isNotEmpty(), nomLinia = vm.config.nomLinia,
        )
    }
}

/** La potència (sense la línia) al voltant d'un consum desconegut, petita. */
@Composable
private fun MiniGrafica(punts: List<Punt>, inici: Long) {
    Canvas(Modifier.width(64.dp).height(30.dp)) {
        val t0 = punts.first().ts.toFloat()
        val t1 = punts.last().ts.toFloat().coerceAtLeast(t0 + 1)
        val ymax = punts.maxOf { it.wTotal - it.wLinia }.toFloat().coerceAtLeast(1f) * 1.1f
        fun x(ts: Long) = (ts - t0) / (t1 - t0) * size.width
        fun y(w: Double) = size.height - (w.toFloat() / ymax).coerceIn(0f, 1f) * size.height
        drawLine(Vora, Offset(x(inici), 0f), Offset(x(inici), size.height), strokeWidth = 2f)
        val camí = Path().apply {
            punts.forEachIndexed { i, p ->
                val w = p.wTotal - p.wLinia
                if (i == 0) moveTo(x(p.ts), y(w)) else lineTo(x(p.ts), y(w))
            }
        }
        drawPath(camí, Desconegut, style = Stroke(1.6.dp.toPx()))
    }
}

/** La forma d'una vegada, petita i sense eixos: la potència de la resta de la casa, amb la vegada pintada a sota. */
@Composable
fun FormaVegada(punts: List<Pair<Long, Double>>, desDe: Long, finsA: Long) {
    if (punts.size < 2) return
    Canvas(Modifier.width(64.dp).height(30.dp)) {
        val t0 = punts.first().first.toFloat()
        val t1 = punts.last().first.toFloat().coerceAtLeast(t0 + 1)
        val ymax = punts.maxOf { it.second }.toFloat().coerceAtLeast(1f) * 1.08f
        fun x(ts: Long) = (ts - t0) / (t1 - t0) * size.width
        fun y(w: Double) = size.height - (w.toFloat() / ymax).coerceIn(0f, 1f) * size.height
        val linia = Path()
        val area = Path()
        punts.forEachIndexed { i, (ts, w) ->
            if (i == 0) linia.moveTo(x(ts), y(w)) else linia.lineTo(x(ts), y(w))
        }
        val dins = punts.filter { it.first in desDe until finsA }
        if (dins.size >= 2) {
            area.moveTo(x(dins.first().first), size.height)
            dins.forEach { (ts, w) -> area.lineTo(x(ts), y(w)) }
            area.lineTo(x(dins.last().first), size.height)
            area.close()
            drawPath(area, Desconegut.copy(alpha = 0.18f))
        }
        drawPath(linia, Desconegut, style = Stroke(1.6.dp.toPx()))
    }
}

/** «6 h 7 min» o «45 min». */
@Composable
fun durada(minuts: Int): String =
    if (minuts >= 60) stringResource(R.string.durada_h_min, minuts / 60, minuts % 60) else stringResource(R.string.durada_min, minuts)
