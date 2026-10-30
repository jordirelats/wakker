@file:OptIn(ExperimentalLayoutApi::class)

package cat.jordi.wakker.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Categoria
import cat.jordi.wakker.dades.Font
import cat.jordi.wakker.dades.Funcionament

/**
 * Editar un aparell (maqueta 21): el nom, el tipus, la marca i el model, la potència, el consum de l'any, com funciona i on
 * està connectat. El que l'app n'ha après (les vegades, la firma) es queda; abans calia esborrar-lo i tornar-lo a crear.
 */
@Composable
fun PantallaEdita(vm: WakkerViewModel) {
    val a = vm.aparells.firstOrNull { it.id == vm.aparellObert }
    LaunchedEffect(a == null && vm.aparells.isNotEmpty()) { if (a == null && vm.aparells.isNotEmpty()) vm.pantalla = Pantalla.PRINCIPAL }
    if (a == null) return
    var nom by rememberSaveable(a.id) { mutableStateOf(a.nom) }
    var categoria by rememberSaveable(a.id) { mutableStateOf(a.categoria) }
    var marca by rememberSaveable(a.id) { mutableStateOf(a.marca) }
    var model by rememberSaveable(a.id) { mutableStateOf(a.model) }
    var potencia by rememberSaveable(a.id) { mutableStateOf(a.potenciaW?.let { num(it) }.orEmpty()) }
    var kwhAny by rememberSaveable(a.id) { mutableStateOf(num(a.kwhAny)) }
    var funciona by rememberSaveable(a.id) { mutableStateOf(a.funciona) }
    var linia by rememberSaveable(a.id) { mutableStateOf(a.linia) }
    val w = llegeixNum(potencia)
    val kwh = llegeixNum(kwhAny)
    val potenciaBe = potencia.isBlank() || (w != null && w > 0)
    val kwhBe = kwh != null && kwh >= 0
    val esLinia = vm.config.teLinia && nom.trim().equals(vm.config.nomLinia, ignoreCase = true)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Targeta {
            OutlinedTextField(nom, { nom = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.edita_nom)) }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.edita_tipus), style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = Suau)
            Spacer(Modifier.height(4.dp))
            TriaCategoria(categoria) { c ->
                if (c != categoria) {
                    // Un consum de l'any que encara era el típic segueix el tipus nou; com funciona, també
                    if (a.font == Font.TIPIC && kwhAny == num(a.kwhAny)) kwhAny = num(c.kwhAnyTipic)
                    if (funciona == categoria.funciona) funciona = c.funciona
                    categoria = c
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(marca, { marca = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.edita_marca)) }, singleLine = true)
                OutlinedTextField(model, { model = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.edita_model)) }, singleLine = true)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    potencia, { potencia = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.edita_potencia)) }, singleLine = true,
                    isError = !potenciaBe, suffix = { Text("W") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text(stringResource(if (potencia.isBlank()) R.string.edita_potencia_tipica else R.string.edita_potencia_sota), maxLines = 2) },
                )
                OutlinedTextField(
                    kwhAny, { kwhAny = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.edita_any)) }, singleLine = true,
                    isError = !kwhBe, suffix = { Text("kWh") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text(stringResource(R.string.edita_any_sota), maxLines = 2) },
                )
            }
        }
        Targeta {
            TitolTargeta(stringResource(R.string.aparells_com_funciona))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Funcionament.entries.forEach { f -> Xip(stringResource(f.nomId), triat = funciona == f, grup = true) { funciona = f } }
            }
            Nota(stringResource(funciona.textId), Modifier.padding(top = 4.dp))
            // On està connectat: només si hi ha la pinça 2 (un aparell sempre encès va dins del fons de tota la casa)
            if (vm.config.teLinia && funciona != Funcionament.SEMPRE && !esLinia) {
                TitolTargeta(stringResource(R.string.fitxa_on_connectat), Modifier.padding(top = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Xip(stringResource(R.string.fitxa_resta_casa), triat = !linia, grup = true) { linia = false }
                    Xip(stringResource(R.string.fitxa_pinca2, vm.config.nomLinia), triat = linia, grup = true) { linia = true }
                }
            }
        }
        Nota(stringResource(R.string.edita_nota), Modifier.padding(horizontal = 4.dp))
        Button(
            onClick = {
                // Els números que no s'han tocat es queden com eren (la pantalla els ensenya arrodonits)
                val kwhNou = if (kwhAny == num(a.kwhAny)) a.kwhAny else kwh ?: a.kwhAny
                val ara = a.copy(
                    nom = nom.trim(), categoria = categoria, marca = marca.trim(), model = model.trim(),
                    potenciaW = when {
                        potencia.isBlank() -> null
                        potencia == a.potenciaW?.let { num(it) } -> a.potenciaW
                        else -> w
                    },
                    kwhAny = kwhNou,
                    // Un consum de l'any escrit a mà ja no és el típic
                    font = if (kwhNou != a.kwhAny && a.font == Font.TIPIC && kwhNou != categoria.kwhAnyTipic) Font.MANUAL else a.font,
                    funciona = funciona,
                    linia = linia && funciona != Funcionament.SEMPRE && !esLinia,
                )
                vm.editaAparell(a, ara)
            },
            Modifier.fillMaxWidth(),
            enabled = nom.isNotBlank() && potenciaBe && kwhBe,
        ) { Text(stringResource(R.string.edita_desa)) }
        Spacer(Modifier.height(24.dp))
    }
}
