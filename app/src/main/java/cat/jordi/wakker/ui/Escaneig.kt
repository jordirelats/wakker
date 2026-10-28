package cat.jordi.wakker.ui

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.CampFactura
import cat.jordi.wakker.dades.Contracte
import cat.jordi.wakker.dades.Document
import cat.jordi.wakker.dades.Factura
import cat.jordi.wakker.dades.LecturaFactura
import cat.jordi.wakker.dades.Pais
import cat.jordi.wakker.dades.Placa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Com es diu cada preu a la pantalla de revisió, i el número amb la seva unitat (els dos, recursos). */
private fun nomCamp(c: CampFactura): Pair<Int, Int> = when (c) {
    CampFactura.NL_NORMAAL -> R.string.escaneig_camp_nl_normaal to R.string.escaneig_unitat_eur_kwh
    CampFactura.NL_DAL -> R.string.escaneig_camp_nl_dal to R.string.escaneig_unitat_eur_kwh
    CampFactura.NL_ENKEL -> R.string.escaneig_camp_nl_enkel to R.string.escaneig_unitat_eur_kwh
    CampFactura.NL_EB -> R.string.escaneig_camp_nl_eb to R.string.escaneig_unitat_eur_kwh
    CampFactura.NL_VASTE -> R.string.escaneig_camp_nl_vaste to R.string.escaneig_unitat_eur_dag
    CampFactura.NL_NETBEHEER -> R.string.escaneig_camp_nl_netbeheer to R.string.escaneig_unitat_eur_dag
    CampFactura.NL_VERMINDERING -> R.string.escaneig_camp_nl_vermindering to R.string.escaneig_unitat_eur_dag
    CampFactura.NL_INKOOP -> R.string.escaneig_camp_nl_inkoop to R.string.escaneig_unitat_eur_kwh
    CampFactura.ES_POTENCIA -> R.string.escaneig_camp_es_potencia to R.string.escaneig_unitat_kw
    CampFactura.ES_P1 -> R.string.escaneig_camp_es_p1 to R.string.escaneig_unitat_eur_kw_dia
    CampFactura.ES_P2 -> R.string.escaneig_camp_es_p2 to R.string.escaneig_unitat_eur_kw_dia
    CampFactura.ES_PUNTA -> R.string.escaneig_camp_es_punta to R.string.escaneig_unitat_eur_kwh
    CampFactura.ES_PLA -> R.string.escaneig_camp_es_pla to R.string.escaneig_unitat_eur_kwh
    CampFactura.ES_VALL -> R.string.escaneig_camp_es_vall to R.string.escaneig_unitat_eur_kwh
    CampFactura.ES_FIX -> R.string.escaneig_camp_es_fix to R.string.escaneig_unitat_eur_kwh
    CampFactura.ES_IEE -> R.string.escaneig_camp_es_iee to R.string.escaneig_unitat_percent
    CampFactura.ES_IVA -> R.string.escaneig_camp_es_iva to R.string.escaneig_unitat_percent
    CampFactura.ES_COMPTADOR -> R.string.escaneig_camp_es_comptador to R.string.escaneig_unitat_eur_mes
    CampFactura.ES_BO_SOCIAL -> R.string.escaneig_camp_es_bo_social to R.string.escaneig_unitat_eur_dia
}

/** Els preus que hauria de tenir un document d'aquest país i contracte, per dir quins no s'han trobat. */
private fun esperats(l: LecturaFactura): List<CampFactura> = when (l.pais) {
    Pais.NL -> when (l.contracte) {
        Contracte.FRANGES -> listOf(CampFactura.NL_NORMAAL, CampFactura.NL_DAL)
        Contracte.FIX -> listOf(CampFactura.NL_ENKEL)
        Contracte.DINAMIC -> listOf(CampFactura.NL_INKOOP)
    } + listOf(CampFactura.NL_VASTE, CampFactura.NL_NETBEHEER, CampFactura.NL_VERMINDERING) +
        if (l.contracte == Contracte.DINAMIC) emptyList() else listOf(CampFactura.NL_EB)
    else -> listOf(CampFactura.ES_POTENCIA, CampFactura.ES_P1, CampFactura.ES_P2) + when (l.contracte) {
        Contracte.FRANGES -> listOf(CampFactura.ES_PUNTA, CampFactura.ES_PLA, CampFactura.ES_VALL)
        Contracte.FIX -> listOf(CampFactura.ES_FIX)
        Contracte.DINAMIC -> emptyList()
    } + listOf(CampFactura.ES_IEE, CampFactura.ES_IVA, CampFactura.ES_COMPTADOR, CampFactura.ES_BO_SOCIAL)
}

@Composable
private fun nomContracte(l: LecturaFactura) = stringResource(
    when (l.pais) {
        Pais.NL -> when (l.contracte) {
            Contracte.FIX -> R.string.escaneig_contracte_nl_fix
            Contracte.FRANGES -> R.string.escaneig_contracte_nl_franges
            Contracte.DINAMIC -> R.string.escaneig_contracte_nl_dinamic
        }
        else -> when (l.contracte) {
            Contracte.FIX -> R.string.escaneig_contracte_es_fix
            Contracte.FRANGES -> R.string.escaneig_contracte_es_franges
            Contracte.DINAMIC -> R.string.escaneig_contracte_es_pvpc
        }
    }
)

/**
 * «Omple-ho amb el contracte»: una foto o un PDF del contracte o de la factura. Es llegeix dins del mòbil amb
 * Tesseract, pàgina a pàgina, i el que es troba es passa a [enLlegir] perquè l'usuari ho revisi.
 */
@Composable
fun TargetaEscaneig(enLlegir: (LecturaFactura?) -> Unit) {
    val context = LocalContext.current
    val abast = rememberCoroutineScope()
    var progres by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun llegeix(uri: Uri) {
        error = null
        progres = context.getString(R.string.escaneig_obrint)
        abast.launch {
            val r = withContext(Dispatchers.Default) {
                runCatching {
                    val pagines = Document.pagines(context, uri)
                    val text = pagines.mapIndexed { i, p ->
                        withContext(Dispatchers.Main) { progres = context.getString(R.string.escaneig_llegint_pagina, i + 1, pagines.size) }
                        Placa.llegeix(context, p, document = true)
                    }.joinToString("\n")
                    // Només a la versió de prova: el text llegit, per afinar el lector amb factures noves
                    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) text.lines().forEach { Log.d("WakkerOCR", it) }
                    Factura.llegeix(text)
                }
            }
            progres = null
            r.onSuccess { l -> if (l == null) error = context.getString(R.string.escaneig_cap_preu) else enLlegir(l) }
                .onFailure { error = context.getString(R.string.escaneig_error_document) }
        }
    }

    val uriFoto = remember {
        val f = File(context.cacheDir, "fotos/document.jpg").apply { parentFile?.mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.fitxers", f)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { feta -> if (feta) llegeix(uriFoto) }
    val permis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { si ->
        if (si) camera.obre(uriFoto) { error = context.getString(R.string.error_sense_app) } else error = context.getString(R.string.escaneig_sense_permis_camera)
    }
    val fitxer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::llegeix) }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Superficie, border = BorderStroke(2.dp, Primari)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, null, tint = Primari, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                TitolTargeta(stringResource(R.string.escaneig_titol), Modifier.padding(top = 6.dp))
            }
            Nota(stringResource(R.string.escaneig_nota))
            Spacer(Modifier.height(10.dp))
            if (progres != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("  $progres", fontSize = 14.sp, color = Suau)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera.obre(uriFoto) { error = context.getString(R.string.error_sense_app) }
                        else permis.launch(Manifest.permission.CAMERA)
                    }) {
                        Icon(Icons.Outlined.PhotoCamera, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.escaneig_foto))
                    }
                    OutlinedButton(onClick = { fitxer.obre(arrayOf("application/pdf", "image/*")) { error = context.getString(R.string.error_sense_app) } }) { Text(stringResource(R.string.escaneig_tria)) }
                }
            }
            error?.let { Text(it, Modifier.padding(top = 8.dp), color = DesconegutText, fontSize = 13.sp) }
        }
    }
}

/** El que s'ha trobat al document: cada preu amb el tros d'on surt, per marcar o desmarcar. */
@Composable
fun RevisioDocument(l: LecturaFactura, enCancellar: () -> Unit, enPosar: (Set<CampFactura>) -> Unit) {
    var triats by remember { mutableStateOf(l.trobats.map { it.camp }.toSet()) }
    val trobats = l.trobats.map { it.camp }.toSet()
    val falten = esperats(l).filter { it !in trobats }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Targeta {
            TitolTargeta(stringResource(R.string.escaneig_he_trobat))
            Text(stringResource(R.string.escaneig_contracte, nomContracte(l)), fontSize = 13.sp, color = Suau)
            Spacer(Modifier.height(4.dp))
            // En el mateix ordre que el formulari, no en el que surten al document
            l.trobats.sortedBy { it.camp.ordinal }.forEach { p ->
                val (nom, unitat) = nomCamp(p.camp)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = p.camp in triats, onCheckedChange = { si -> triats = if (si) triats + p.camp else triats - p.camp })
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(nom), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (p.deduit) stringResource(R.string.escaneig_vall_deduida) else stringResource(R.string.escaneig_fragment, p.fragment),
                            fontSize = 11.sp, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontFamily = if (p.deduit) FontFamily.Default else FontFamily.Monospace,
                        )
                    }
                    Text(stringResource(unitat, textValor(p.camp, p.valor)), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Nota(stringResource(R.string.escaneig_nota_revisio), Modifier.padding(top = 4.dp))
        }
        Targeta {
            TitolTargeta(stringResource(R.string.escaneig_no_trobat))
            val noms = falten.map { stringResource(nomCamp(it).first) }
            Nota(if (noms.isEmpty()) stringResource(R.string.escaneig_no_falta_res) else stringResource(R.string.escaneig_falten, noms.joinToString(", ")))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = enCancellar, Modifier.weight(1f)) { Text(stringResource(R.string.escaneig_cancella)) }
            Button(onClick = { enPosar(triats) }, Modifier.weight(1f), enabled = triats.isNotEmpty()) { Text(stringResource(R.string.escaneig_posa_ls)) }
        }
    }
}

private fun textValor(c: CampFactura, v: Double) = when (c) {
    CampFactura.ES_POTENCIA -> num(v, 2)
    CampFactura.ES_IEE, CampFactura.ES_IVA -> num(v, 2)
    CampFactura.ES_COMPTADOR -> num(v, 2)
    else -> num(v, 6).trimEnd('0').trimEnd(',')
}
