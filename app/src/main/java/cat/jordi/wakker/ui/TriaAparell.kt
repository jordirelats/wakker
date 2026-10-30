@file:OptIn(ExperimentalMaterial3Api::class)

package cat.jordi.wakker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import cat.jordi.wakker.dades.Funcionament

/**
 * «Tots els aparells…» (maqueta 17): per dir què era un consum amb qualsevol aparell, no només amb els que s'hi assemblen.
 * A dalt, un cercador; després, els que s'hi assemblen ([suggerits]) i tots els altres que poden ser-ho ([tots]) per ordre
 * alfabètic; i al final, afegir-ne un de nou (el tipus i el nom), que queda afegit i triat d'un sol cop. Una vegada de la resta
 * de la casa no pot ser d'un aparell de la pinça 2 ([senseLinia]: es diu a sota), ni cap consum d'un de sempre encès (tret de
 * la caça del sempre encès): si se'ls donés, aprendrien una firma que no és seva.
 * Serveix igual a Entrena, a l'avís de consum inesperat, als moments punta i a la caça del sempre encès.
 */
@Composable
fun TriaAparell(
    subtitol: String, suggerits: List<Aparell>, tots: List<Aparell>, teLinia: Boolean,
    tria: (Aparell) -> Unit, nou: (Aparell) -> Unit, tanca: () -> Unit, senseLinia: Boolean = false, nomLinia: String = "",
    /** Per contestar de qui era una vegada (si no, per triar un aparell, com a «Ara l'engego»: maqueta 21). */
    perVegada: Boolean = true,
) {
    val estat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var afegint by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = tanca, sheetState = estat, containerColor = Superficie) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding()) {
            if (afegint) {
                AparellNou(subtitol, perVegada, enrere = { afegint = false }) { a ->
                    nou(a)
                    tanca()
                }
            } else {
                Llista(subtitol, suggerits, tots, teLinia, senseLinia, nomLinia, perVegada, { a -> tria(a); tanca() }) { afegint = true }
            }
        }
    }
}

@Composable
private fun Llista(
    subtitol: String, suggerits: List<Aparell>, tots: List<Aparell>, teLinia: Boolean, senseLinia: Boolean, nomLinia0: String,
    perVegada: Boolean, tria: (Aparell) -> Unit, afegeix: () -> Unit,
) {
    val nomLinia = nomLinia0.ifBlank { stringResource(R.string.pinca2_xip) }
    var cerca by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val buscat = pla(cerca)
    fun hiEs(a: Aparell) = buscat.isEmpty() || pla(a.nom).contains(buscat) || pla(context.getString(a.categoria.nomId)).contains(buscat)
    val idsSuggerits = suggerits.map { it.id }.toSet()
    val primers = suggerits.filter(::hiEs)
    val resta = tots.filter { it.id !in idsSuggerits && hiEs(it) }.sortedBy { pla(it.nom) }
    Text(stringResource(if (perVegada) R.string.tria_que_era else R.string.tria_quin), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Text(subtitol, fontSize = 13.sp, color = Suau)
    OutlinedTextField(
        cerca, { cerca = it }, Modifier.fillMaxWidth().padding(top = 10.dp), placeholder = { Text(stringResource(R.string.tria_busca)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
    )
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 6.dp)) {
        if (primers.isNotEmpty()) {
            item { Seccio(stringResource(R.string.tria_sassemblen)) }
            items(primers, key = { "s${it.id}" }) { a -> FilaAparell(a, teLinia, nomLinia) { tria(a) } }
        }
        if (resta.isNotEmpty()) {
            item { Seccio(stringResource(R.string.tria_tots_titol)) }
            items(resta, key = { "t${it.id}" }) { a -> FilaAparell(a, teLinia, nomLinia) { tria(a) } }
        }
        if (primers.isEmpty() && resta.isEmpty()) item { Nota(stringResource(R.string.tria_cap), Modifier.padding(vertical = 8.dp)) }
        if (senseLinia) item { Nota(stringResource(R.string.tria_sense_linia), Modifier.padding(top = 8.dp)) }
    }
    // Afegir-ne un de nou: sempre a la vista, a baix
    Targeta(Modifier.padding(top = 8.dp).clickable(onClick = afegeix), color = PrimariFluix) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Add, null, tint = Primari, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(stringResource(R.string.tria_nou), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Primari)
                Text(stringResource(if (perVegada) R.string.tria_nou_text else R.string.tria_nou_text_curt), fontSize = 12.sp, color = Suau)
            }
        }
    }
}

@Composable
private fun Seccio(text: String) {
    Text(text, Modifier.padding(top = 10.dp, bottom = 2.dp), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Primari)
}

@Composable
private fun FilaAparell(a: Aparell, teLinia: Boolean, nomLinia: String, tria: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = tria).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cercle(icona(a.categoria), EstatIcona.NEUTRE, 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(a.nom, Modifier.weight(1f, fill = false), fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        when {
            teLinia && a.linia -> Etiqueta(nomLinia)
            a.funciona == Funcionament.SEMPRE -> Etiqueta(stringResource(R.string.espera_sempre_ences))
        }
    }
}

/** Un aparell nou, sense sortir de la pregunta: el tipus (amb el mateix cercador que Afegir) i el nom. */
@Composable
private fun AparellNou(subtitol: String, perVegada: Boolean, enrere: () -> Unit, desa: (Aparell) -> Unit) {
    val context = LocalContext.current
    var categoria by rememberSaveable { mutableStateOf<Categoria?>(null) }
    var nom by rememberSaveable { mutableStateOf("") }
    var nomTocat by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = enrere) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.principal_enrere)) }
            Column {
                Text(stringResource(R.string.tria_nou_titol), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitol, fontSize = 13.sp, color = Suau)
            }
        }
        Text(stringResource(R.string.tria_tipus), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        TriaCategoria(categoria) { c ->
            categoria = c
            // El nom, el del tipus, mentre l'usuari no n'hagi escrit cap
            if (!nomTocat) nom = context.getString(c.nomId)
        }
        Text(stringResource(R.string.tria_nom_pregunta), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(nom, { nom = it; nomTocat = true }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.tria_nom)) }, singleLine = true)
        if (perVegada) Nota(stringResource(R.string.tria_nou_nota))
        val c = categoria
        Button(
            onClick = { if (c != null) desa(Aparell(nom = nom.trim(), categoria = c, font = Font.TIPIC, kwhAny = c.kwhAnyTipic)) },
            Modifier.fillMaxWidth(), enabled = c != null && nom.isNotBlank(),
        ) { Text(stringResource(if (perVegada) R.string.tria_afegeix else R.string.tria_afegeix_curt)) }
    }
}
