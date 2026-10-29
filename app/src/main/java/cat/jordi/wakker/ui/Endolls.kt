package cat.jordi.wakker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Outlet
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Aparell
import cat.jordi.wakker.dades.DiaEndoll
import cat.jordi.wakker.dades.Endoll
import cat.jordi.wakker.dades.EndollFitxa
import cat.jordi.wakker.dades.Idioma
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * Els endolls amb mesura (maqueta 19) a la Connexió: una línia que diu què són (amb la «i»), el compte de Tapo (només
 * se'n desa una empremta; els camps només surten quan calen), els endolls que hi ha i a quin aparell és cadascun, buscar-ne
 * a la wifi i afegir-ne un per l'adreça. Tot es desa sol, sense el «Desa» de sota.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetaEndolls(vm: WakkerViewModel, primerCop: Boolean = false) {
    val config = vm.config
    val context = LocalContext.current
    var correu by rememberSaveable { mutableStateOf(config.tapoCorreu) }
    var contrasenya by remember { mutableStateOf("") }
    var canviant by rememberSaveable { mutableStateOf(false) }
    var tincTapo by rememberSaveable { mutableStateOf(false) }
    var adreca by rememberSaveable { mutableStateOf("") }
    var perTreure by remember { mutableStateOf<Endoll?>(null) }
    val missatge = vm.missatgeEndolls
    // Els camps del compte: quan es canvia, quan l'usuari diu que té un Tapo, o quan un Tapo l'ha demanat
    val ambCamps = canviant || (!vm.teTapo && (tincTapo || missatge?.id == R.string.endolls_cal_compte))
    fun afegeix(host: String) {
        if (ambCamps && correu.isNotBlank() && contrasenya.isNotBlank()) vm.afegeixEndoll(host, correu, contrasenya) else vm.afegeixEndoll(host)
    }
    // Quan un endoll s'ha afegit, el formulari del compte es tanca i la contrasenya s'oblida; si ha fallat, es queda per tornar-ho a provar
    LaunchedEffect(missatge) {
        if (missatge?.id == R.string.endolls_afegit) {
            canviant = false
            tincTapo = false
            contrasenya = ""
        }
    }

    Targeta {
        TitolTargeta(stringResource(R.string.endolls_titol))
        OpcioAmbAjuda(stringResource(R.string.endolls_que_es), stringResource(R.string.endolls_nota)) {}
        if (ambCamps) {
            Nota(stringResource(R.string.endolls_compte_nota))
            OutlinedTextField(
                correu, { correu = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.endolls_correu)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                contrasenya, { contrasenya = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.endolls_contrasenya)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            )
            Nota(stringResource(R.string.endolls_despres), Modifier.padding(top = 2.dp))
            Row {
                Enllac(stringResource(R.string.escaneig_cancella)) { canviant = false; tincTapo = false }
                if (vm.teTapo) {
                    Spacer(Modifier.width(16.dp))
                    Enllac(stringResource(R.string.endolls_desconnecta)) { vm.desconnectaTapo(); canviant = false }
                }
            }
        } else if (vm.teTapo) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Ok)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.endolls_compte_connectat, config.tapoCorreu), Modifier.weight(1f), fontSize = 12.5.sp, color = Suau, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Enllac(stringResource(R.string.endolls_canvia_compte)) { canviant = true }
            }
        } else {
            Enllac(stringResource(R.string.endolls_tinc_tapo)) { tincTapo = true }
        }

        // Els endolls que hi ha, cadascun amb l'aparell que mesura
        vm.endolls.forEach { e ->
            Ratlla()
            FilaEndoll(vm, e) { perTreure = e }
        }

        // Buscar-ne a la wifi, i els trobats que encara no hi són
        Ratlla()
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = vm::buscaEndolls, enabled = !vm.buscantEndolls) {
                Icon(Icons.Outlined.Search, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.endolls_busca))
            }
            if (vm.buscantEndolls) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.endolls_buscant), fontSize = 12.sp, color = Suau)
            }
        }
        val nous = vm.endollsTrobats.filter { t -> vm.endolls.none { it.mac == t.mac || it.host == t.host } }
        if (nous.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            nous.forEach { t ->
                AssistChip(
                    onClick = { afegeix(t.host) }, enabled = !vm.provantEndoll,
                    label = { Text("${t.model} · ${t.host}") },
                    leadingIcon = { Icon(Icons.Outlined.Outlet, null, Modifier.size(16.dp)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                adreca, { adreca = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.endolls_adreca)) },
                placeholder = { Text("192.168.1.50") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (adreca.isNotBlank() && !vm.provantEndoll) { afegeix(adreca); adreca = "" } }),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { afegeix(adreca); adreca = "" }, enabled = adreca.isNotBlank() && !vm.provantEndoll) {
                Text(stringResource(R.string.endolls_afegeix))
            }
        }
        if (vm.provantEndoll) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.endolls_provant), fontSize = 12.sp, color = Suau)
        }
        missatge?.let { m ->
            val bo = m.id == R.string.endolls_afegit
            Text(m.text(context), Modifier.padding(top = 6.dp), fontSize = 13.sp, color = if (bo) Ok else DesconegutText)
        }
        if (!primerCop) Nota(stringResource(R.string.endolls_es_desa_sol), Modifier.padding(top = 6.dp))
    }

    perTreure?.let { e ->
        AlertDialog(
            onDismissRequest = { perTreure = null },
            text = { Text(stringResource(R.string.endolls_confirma_treu, e.nom)) },
            confirmButton = { TextButton(onClick = { vm.esborraEndoll(e.id); perTreure = null }) { Text(stringResource(R.string.endolls_treu), color = PuntaText) } },
            dismissButton = { TextButton(onClick = { perTreure = null }) { Text(stringResource(R.string.escaneig_cancella)) } },
        )
    }
}

/** L'hora d'una lectura d'un endoll: «ara X W» si és d'ara, «de les 10:12» si és vella (fora de casa no es llegeixen). */
@Composable
private fun textLectura(l: cat.jordi.wakker.endolls.LecturaEndoll?): String {
    if (l == null) return stringResource(R.string.endolls_no_respon)
    val ara = System.currentTimeMillis() / 1000
    return if (ara - l.ts > 120) stringResource(R.string.endolls_de_les, horaMinut(l.ts), num(l.w)) else stringResource(R.string.endolls_ara_w, num(l.w))
}

/** Un endoll: el nom i el model, la potència d'ara, i el desplegable de l'aparell que mesura (amb «Treu» a dalt). */
@Composable
private fun FilaEndoll(vm: WakkerViewModel, e: Endoll, treu: () -> Unit) {
    val aparellId = vm.periodesEndoll.lastOrNull { it.endollId == e.id && it.finsA == null }?.aparellId
    val aparell = vm.aparells.firstOrNull { it.id == aparellId }
    val lectura = vm.lecturesEndolls[e.id]
    var obert by remember { mutableStateOf(false) }
    // Un aparell que encara no hi és (el primer cop no n'hi ha cap): el full de «Tots els aparells…», amb «Afegeix-ne un de nou»
    var triant by rememberSaveable { mutableStateOf(false) }
    if (triant) {
        TriaAparell(
            e.nom, emptyList(), vm.aparells, false,
            tria = { vm.posaEndollA(e.id, it.id); triant = false }, nou = { vm.posaEndollANou(e.id, it); triant = false },
            tanca = { triant = false },
        )
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Cercle(Icons.Outlined.Outlet, if (aparell != null) EstatIcona.SAP else EstatIcona.CAL, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.nom, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.model} · ${textLectura(lectura)}", fontSize = 11.sp, color = Suau, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box {
            Surface(
                Modifier.widthIn(max = 160.dp).clickable { obert = true }.semantics { role = Role.DropdownList }, shape = RoundedCornerShape(10.dp),
                color = if (aparell == null) DesconegutFluix else Superficie, border = BorderStroke(1.dp, if (aparell == null) Desconegut else Vora),
            ) {
                Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        aparell?.nom ?: stringResource(R.string.endolls_a_quin), Modifier.weight(1f, fill = false), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (aparell == null) DesconegutText else TextPrincipal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp), tint = Suau)
                }
            }
            DropdownMenu(obert, { obert = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.endolls_treu), color = PuntaText) }, onClick = { obert = false; treu() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.endolls_cap_aparell), color = Suau) }, onClick = { obert = false; vm.posaEndollA(e.id, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.endolls_un_altre), color = Primari) }, onClick = { obert = false; triant = true })
                vm.aparells.sortedBy { it.nom.lowercase() }.forEach { a ->
                    // Si l'aparell ja té un altre endoll, es diu: triar-lo li'l prendria
                    val altre = vm.endollDe[a.id]?.takeIf { it.id != e.id }
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(a.nom, fontWeight = if (a.id == aparellId) FontWeight.SemiBold else FontWeight.Normal)
                                if (altre != null) Text(stringResource(R.string.endolls_ja_te, altre.nom), fontSize = 11.sp, color = Suau)
                            }
                        },
                        leadingIcon = { Icon(icona(a.categoria), null, Modifier.size(18.dp)) },
                        onClick = { obert = false; if (a.id != aparellId) vm.posaEndollA(e.id, a.id) },
                    )
                }
            }
        }
    }
}

/**
 * L'endoll d'un aparell a la fitxa (maqueta 19): quin és i des de quan, amb «Canvia» cap a la Connexió; què n'ha après
 * l'app (les vegades seves que ja reconeixia sola, les d'altres que li donava, i cada dia l'app sola contra l'endoll); i el
 * que l'endoll ha mesurat (la mitjana per dia, els últims dies, les hores encès i l'espera).
 */
@Composable
fun TargetaEndollFitxa(vm: WakkerViewModel, a: Aparell, f: EndollFitxa) {
    val zona = ZoneId.systemDefault()
    val desDe = Instant.ofEpochSecond(f.periode.desDe).atZone(zona).format(DateTimeFormatter.ofPattern(stringResource(R.string.format_dia_curt), Idioma.locale))
    Targeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cercle(Icons.Outlined.Outlet, EstatIcona.SAP, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.fitxa_endoll_titol, f.endoll.nom), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${stringResource(R.string.fitxa_endoll_desde, f.endoll.model, desDe)} · ${textLectura(f.lectura)}", fontSize = 11.sp, color = Suau)
            }
            Enllac(stringResource(R.string.endolls_canvia)) { vm.obreConnexio() }
        }
        // Fins que no hi ha cap hora sencera mesurada, no hi ha res més a ensenyar: es diu
        if (f.mesura == null) Nota(stringResource(R.string.fitxa_endoll_esperant), Modifier.padding(top = 6.dp))
    }

    val q = f.apres
    if (q != null && q.seves + q.deMes > 0) Targeta {
        TitolTargeta(stringResource(R.string.fitxa_apres_titol))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Estadistica(stringResource(R.string.fitxa_apres_seves, q.encertades, q.seves), stringResource(R.string.fitxa_apres_seves_text), Modifier.weight(1f))
            Estadistica(num(q.deMes.toDouble()), stringResource(R.string.fitxa_apres_demes_text), Modifier.weight(1f), ambre = q.deMes > 0)
        }
        if (q.dies.size >= 2) {
            GraficaDiesEndoll(q.dies)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Llegenda(Primari, stringResource(R.string.fitxa_apres_llegenda_app))
                Llegenda(TextPrincipal, stringResource(R.string.fitxa_apres_llegenda_endoll))
                Llegenda(Desconegut, stringResource(R.string.fitxa_apres_no_quadra), rodona = true)
            }
        } else Nota(stringResource(R.string.fitxa_apres_encara), Modifier.padding(top = 6.dp))
        Nota(stringResource(R.string.fitxa_apres_nota), Modifier.padding(top = 6.dp))
    }

    val m = f.mesura
    if (m != null) Targeta {
        TitolTargeta(stringResource(R.string.fitxa_mesura_titol))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num(m.whDia / 1000, 2), style = EstilMitja)
            Text(
                " " + stringResource(R.string.fitxa_mesura_dia) + "   " + pluralStringResource(R.plurals.fitxa_mesura_dies, m.dies, m.dies),
                Modifier.padding(bottom = 6.dp), fontSize = 13.sp, color = Suau,
            )
        }
        if (m.perDia.size >= 2) {
            val top = m.perDia.maxOf { it.second }.coerceAtLeast(1.0)
            val format = DateTimeFormatter.ofPattern("EEE d", Idioma.locale)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                m.perDia.forEach { (dia, wh) ->
                    val etiqueta = dia.format(format)
                    Column(
                        Modifier.weight(1f).semantics { contentDescription = "$etiqueta: ${num(wh / 1000, 2)} kWh" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(num(wh / 1000, 2), fontSize = 9.5.sp, lineHeight = 11.sp, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Box(Modifier.fillMaxWidth().height(max(2.0, 54 * wh / top).dp).background(Primari, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.height(2.dp))
                        Text(etiqueta, fontSize = 9.5.sp, lineHeight = 11.sp, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val preu = vm.resumMes?.preuMitja ?: vm.config.tarifa.preuFix
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.horesDia != null && m.encesW != null) {
                val h = m.horesDia.toInt()
                val min = ((m.horesDia - h) * 60).toInt() / 10 * 10
                DadaEndoll(stringResource(R.string.fitxa_mesura_ences), "$h h ${"%02d".format(min)}", stringResource(R.string.fitxa_mesura_hores, num(m.encesW)), Modifier.weight(1f))
            }
            if (m.esperaW != null) {
                DadaEndoll(stringResource(R.string.fitxa_mesura_espera), "${num(m.esperaW, 1)} W", stringResource(R.string.fitxa_mesura_espera_valor, euros(m.esperaW * 8.76 * preu)), Modifier.weight(1f))
            }
            DadaEndoll(stringResource(R.string.fitxa_mesura_any), "${num(m.whDia * 365 / 1000)} kWh", euros(m.whDia * 365 / 1000 * preu), Modifier.weight(1f))
        }
        // Un aparell petit (el que mesura l'endoll quan és encès, o el típic, no arriba als 250 W): al total no es veu
        if ((m.encesW ?: a.wTipic) < 250) Nota(stringResource(R.string.fitxa_mesura_nota), Modifier.padding(top = 6.dp))
    }
}

/** Una dada petita amb l'etiqueta a dalt, el valor i una línia a sota (com les d'Ara), sobre el fons de la pàgina. */
@Composable
private fun DadaEndoll(etiqueta: String, valor: String, sota: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Fons, RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 7.dp)) {
        Text(etiqueta.uppercase(Idioma.locale), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = Suau, letterSpacing = androidx.compose.ui.unit.TextUnit(0.05f, androidx.compose.ui.unit.TextUnitType.Em))
        Text(valor, style = EstilNumero, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sota, fontSize = 10.5.sp, color = Suau, lineHeight = 13.sp)
    }
}

@Composable
private fun Estadistica(valor: String, text: String, modifier: Modifier = Modifier, ambre: Boolean = false) {
    Column(modifier.background(Fons, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(valor, style = EstilMitja, color = if (ambre) DesconegutText else TextPrincipal)
        Text(text, fontSize = 11.sp, color = Suau, lineHeight = 14.sp)
    }
}

@Composable
private fun Llegenda(color: androidx.compose.ui.graphics.Color, text: String, rodona: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, if (rodona) CircleShape else RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = Suau)
    }
}

/**
 * Cada dia, dues barres: el que l'app sola donava a l'aparell (verd) i el que l'endoll ha mesurat (fosc); si no quadren, el
 * dia en ambre amb un punt. Cada columna diu els seus números per a TalkBack.
 */
@Composable
private fun GraficaDiesEndoll(dies: List<DiaEndoll>) {
    val top = dies.maxOf { max(it.appSola, it.endoll) }.coerceAtLeast(0.05)
    // El dia abreujat («dj. 22»): el nom sencer no hi cap en set columnes
    val format = DateTimeFormatter.ofPattern("EEE d", Idioma.locale)
    val app = stringResource(R.string.fitxa_apres_llegenda_app)
    val endoll = stringResource(R.string.fitxa_apres_llegenda_endoll)
    val noQuadra = stringResource(R.string.fitxa_apres_no_quadra)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        dies.forEach { d ->
            val etiqueta = d.dia.format(format)
            val descripcio = "$etiqueta: $app ${num(d.appSola, 2)} kWh, $endoll ${num(d.endoll, 2)} kWh" + if (d.quadra) "" else ", $noQuadra"
            Column(Modifier.weight(1f).semantics { contentDescription = descripcio }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (d.quadra) Spacer(Modifier.size(6.dp)) else Box(Modifier.size(6.dp).background(Desconegut, CircleShape))
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.width(11.dp).height(max(1.5, 64 * d.appSola / top).dp).background(Primari, RoundedCornerShape(2.dp)))
                    Box(Modifier.width(11.dp).height(max(1.5, 64 * d.endoll / top).dp).background(TextPrincipal, RoundedCornerShape(2.dp)))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    etiqueta, fontSize = 9.5.sp, lineHeight = 11.sp, color = if (d.quadra) Suau else DesconegutText,
                    fontWeight = if (d.quadra) FontWeight.Normal else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
