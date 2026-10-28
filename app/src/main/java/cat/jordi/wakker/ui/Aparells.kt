package cat.jordi.wakker.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.Outlet
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Aparell
import cat.jordi.wakker.dades.FilaRanquing
import cat.jordi.wakker.dades.Funcionament

/**
 * Els aparells (maqueta 18): a dalt el que ha costat el mes; després cada aparell una fila, amb la barra relativa al primer, els
 * kWh i els euros, i sense cap xip: una paraula en ambre sota el nom només quan encara l'està aprenent. Els sempre encesos i
 * els que encara no ha vist funcionar, plegats a sota. Tocant-ne un, la fitxa.
 */
@Composable
fun PantallaAparells(vm: WakkerViewModel) {
    val mes = vm.resumMes
    val r = mes?.ranquing
    val nomLinia = vm.config.nomLinia

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (mes != null) {
            Column(Modifier.padding(horizontal = 3.dp)) {
                Text(stringResource(R.string.aparells_aquest_mes), fontSize = 12.sp, color = Suau)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(euros(mes.euros.total), style = EstilMitja)
                    Text("  ${num(mes.total)} kWh", Modifier.padding(bottom = 6.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Suau)
                }
            }
        }

        if (vm.aparells.isEmpty()) {
            Targeta {
                Text(stringResource(R.string.aparells_cap_aparell), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Nota(stringResource(R.string.aparells_comenca_questionari), Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.pantalla = Pantalla.QUESTIONARI }) {
                    Icon(Icons.Outlined.Checklist, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.aparells_revisem_casa))
                }
            }
        } else {
            // Els aparells amb endoll (maqueta 19): el que gasten és mesurat
            val ambEndoll = vm.endollDe.size
            if (ambEndoll > 0) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = PrimariFluix) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Outlet, null, Modifier.size(16.dp), tint = Primari)
                        Spacer(Modifier.width(8.dp))
                        Text(pluralStringResource(R.plurals.aparells_amb_endoll, ambEndoll, ambEndoll), fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
            val mesurats = r?.aparells.orEmpty()
            // Els que l'app ja coneix però aquest mes encara no han funcionat (el dia 1, el cotxe après), a la llista, a 0
            // kWh: abans passaven a «sense mesurar» (1.1.3)
            val idsMes = mesurats.mapNotNull { it.aparell?.id }.toSet()
            val coneguts = vm.aparells.filter {
                it.funciona != Funcionament.SEMPRE && it.id !in idsMes && (vm.referencies[it.id] != null || vm.referenciesLinia[it.id] != null)
            }.map { FilaRanquing(it, it.nom, 0.0, 0.0, 0, 0, vm.config.teLinia && it.linia) }
            val llista = mesurats + coneguts
            if (llista.isNotEmpty()) Targeta {
                val maxim = (mesurats.firstOrNull()?.kwh ?: 0.0).coerceAtLeast(0.001)
                llista.forEachIndexed { i, f ->
                    if (i > 0) Ratlla()
                    FilaAparell(vm, f, f.kwh / maxim, nomLinia, encaraNo = f in coneguts)
                }
            }
            // Els sempre encesos van dins del que la casa gasta sempre
            val sempre = vm.aparells.filter { it.funciona == Funcionament.SEMPRE }
            if (sempre.isNotEmpty()) {
                Plegat(Icons.Outlined.Power, pluralStringResource(R.plurals.aparells_n_sempre, sempre.size, sempre.size, num(sempre.sumOf { it.wTipic }))) {
                    sempre.forEach { a ->
                        val etiqueta = if (vm.endollDe.containsKey(a.id)) stringResource(R.string.endoll_xip) else nomLinia.takeIf { vm.config.teLinia && a.linia }
                        FilaPetita(icona(a.categoria), EstatIcona.GRIS, a.nom, etiqueta, "${num(a.wTipic)} W") { vm.obreAparell(a) }
                    }
                }
            }
            val ambConsum = llista.mapNotNull { it.aparell?.id }.toSet()
            // Un aparell que es diu com la línia és la línia mateixa: si gasta, ja surt a dalt
            val senseMesurar = vm.aparells.filter {
                it.funciona != Funcionament.SEMPRE && it.id !in ambConsum && !(vm.config.teLinia && it.nom.equals(nomLinia, ignoreCase = true))
            }
            if (senseMesurar.isNotEmpty()) {
                // Obert si s'hi acaba d'afegir un aparell: si no, en tornar d'Afegir no es veia (1.1.3)
                Plegat(
                    Icons.Outlined.GridView, pluralStringResource(R.plurals.aparells_n_sense_mesurar, senseMesurar.size, senseMesurar.size),
                    obertDeEntrada = senseMesurar.any { it.nom == vm.darrerAfegit },
                ) {
                    Nota(stringResource(R.string.aparells_sense_mesurar_nota), Modifier.padding(bottom = 4.dp))
                    senseMesurar.forEach { a ->
                        FilaPetita(
                            icona(a.categoria), EstatIcona.NEUTRE, a.nom, nomLinia.takeIf { vm.config.teLinia && a.linia },
                            stringResource(R.string.aparells_etiqueta_mes, num(a.kwhAny / 12)),
                        ) { vm.obreAparell(a) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Enllac(stringResource(R.string.aparells_torna_questionari)) { vm.pantalla = Pantalla.QUESTIONARI }
            }
        }
        // Perquè el botó d'afegir no tapi l'últim
        Spacer(Modifier.height(72.dp))
    }
}

/** Si l'app encara l'està aprenent (no és segur): la paraula en ambre sota el nom. */
@Composable
private fun aprenent(vm: WakkerViewModel, f: FilaRanquing): Boolean {
    if (f.compressorW != null || f.aparell == null || f.ensenyats > 0 || f.linia) return false
    val r = vm.referencies[f.aparell.id] ?: vm.referenciesLinia[f.aparell.id]
    return r?.apresa != true
}

@Composable
private fun FilaAparell(vm: WakkerViewModel, f: FilaRanquing, part: Double, nomLinia: String, encaraNo: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().then(if (f.aparell != null) Modifier.clickable { vm.obreAparell(f.aparell) } else Modifier).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cercle(f.aparell?.let { icona(it.categoria) } ?: Icons.Outlined.Bolt, EstatIcona.SAP, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val teEndoll = f.aparell != null && vm.endollDe.containsKey(f.aparell.id)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.nom.ifBlank { nomLinia }, Modifier.weight(1f, fill = false), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 2, lineHeight = 17.sp, overflow = TextOverflow.Ellipsis)
                if (f.linia) Etiqueta(nomLinia.ifBlank { stringResource(R.string.pinca2_xip) })
                if (teEndoll) Etiqueta(stringResource(R.string.endoll_xip))
            }
            if (encaraNo) Text(stringResource(R.string.aparells_mes_encara_no), fontSize = 10.5.sp, color = Suau)
            // Amb l'endoll, l'app l'aprèn sola: ho diu en verd, no en ambre (maqueta 19)
            else if (teEndoll && aprenent(vm, f)) Text(stringResource(R.string.aparells_apren_endoll), fontSize = 10.5.sp, color = Primari, fontWeight = FontWeight.SemiBold)
            else if (aprenent(vm, f)) ParaulaAmbre(stringResource(R.string.aparells_nou))
            Spacer(Modifier.height(4.dp))
            BarraPart(part)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${num(f.kwh, 1)} kWh", style = EstilNumero)
            Text(euros(f.euros), fontSize = 11.sp, color = Suau)
        }
    }
}

/** Una targeta plegada: una icona, un text i la fletxa; tocant-la s'obre el que hi ha a dins. */
@Composable
private fun Plegat(icona: ImageVector, text: String, obertDeEntrada: Boolean = false, contingut: @Composable () -> Unit) {
    var obert by rememberSaveable(obertDeEntrada) { mutableStateOf(obertDeEntrada) }
    Targeta {
        Row(Modifier.fillMaxWidth().clickable { obert = !obert }, verticalAlignment = Alignment.CenterVertically) {
            Cercle(icona, EstatIcona.NEUTRE, 28.dp)
            Spacer(Modifier.width(10.dp))
            Text(text, Modifier.weight(1f), fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Icon(if (obert) Icons.Outlined.ExpandLess else Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Suau)
        }
        if (obert) Column(Modifier.padding(top = 6.dp)) { contingut() }
    }
}

@Composable
private fun FilaPetita(icona: ImageVector, estat: EstatIcona, nom: String, etiqueta: String?, valor: String, obre: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = obre).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Cercle(icona, estat, 28.dp)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(nom, Modifier.weight(1f, fill = false), fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (etiqueta != null) Etiqueta(etiqueta)
        }
        Text(valor, fontSize = 12.sp, color = Suau)
    }
}
