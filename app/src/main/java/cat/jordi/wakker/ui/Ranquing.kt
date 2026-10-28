package cat.jordi.wakker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Aparell
import cat.jordi.wakker.dades.FilaRanquing
import cat.jordi.wakker.dades.Ranquing

/** Quants aparells del rànquing surten d'entrada; els altres, darrere d'«i N més». */
private const val D_ENTRADA = 4

/** Una barra prima que diu quina part del primer és (maqueta 18). */
@Composable
fun BarraPart(part: Double, color: Color = Primari) {
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        drawRoundRect(GrisFluix, Offset.Zero, size, CornerRadius(size.height / 2))
        drawRoundRect(color, Offset.Zero, Size(size.width * part.toFloat().coerceIn(0.01f, 1f), size.height), CornerRadius(size.height / 2))
    }
}

/**
 * Qui ha gastat més: els aparells de més a menys, cadascun un cop, amb la barra, els kWh i els euros. D'entrada, els
 * quatre primers; «i N més» obre la resta.
 */
@Composable
fun LlistaRanquing(r: Ranquing, nomLinia: String, obre: (Aparell) -> Unit) {
    if (r.aparells.isEmpty()) {
        Nota(stringResource(R.string.ranquing_cap))
        return
    }
    var tots by rememberSaveable { mutableStateOf(false) }
    val maxim = r.aparells.first().kwh.coerceAtLeast(0.001)
    val mostrats = if (tots) r.aparells else r.aparells.take(D_ENTRADA)
    mostrats.forEachIndexed { i, f ->
        if (i > 0) Ratlla()
        FilaDelRanquing(i + 1, f, f.kwh / maxim, nomLinia, obre)
    }
    val resta = r.aparells.drop(D_ENTRADA)
    if (resta.isNotEmpty()) {
        Ratlla()
        Row(
            Modifier.fillMaxWidth().clickable { tots = !tots }.padding(start = 20.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (tots) stringResource(R.string.ranquing_menys) else stringResource(R.string.ranquing_i_mes, resta.size, num(resta.sumOf { it.kwh }, 1)),
                fontSize = 12.sp, color = Suau,
            )
            Icon(if (tots) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(14.dp), tint = Suau)
        }
    }
}

@Composable
private fun FilaDelRanquing(posicio: Int, f: FilaRanquing, part: Double, nomLinia: String, obre: (Aparell) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (f.aparell != null) Modifier.clickable { obre(f.aparell) } else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$posicio", Modifier.width(14.dp), fontSize = 11.sp, color = Suau)
        Cercle(if (f.aparell != null) icona(f.aparell.categoria) else Icons.Outlined.Bolt, EstatIcona.SAP, 26.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.nom.ifBlank { nomLinia }, Modifier.weight(1f, fill = false), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (f.linia) Etiqueta(nomLinia.ifBlank { stringResource(R.string.pinca2_xip) })
            }
            if (f.compressorW != null) Text(stringResource(R.string.ranquing_compressor_curt), fontSize = 11.sp, color = Suau)
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

/**
 * La resta, en dues files (maqueta 18): el que la casa gasta sempre, i «L'app no sap de qui és» (sense nom, petits consums,
 * els altres de la pinça 2 i el de fora de casa, en una línia). Tocant el sempre encès s'obre la seva caça ([obreSempre]);
 * tocant el que no té nom, «Per fer» ([obreSenseId]).
 */
@Composable
fun RestaConsum(r: Ranquing, fonsW: Double?, nomLinia: String, obreSempre: (() -> Unit)? = null, obreSenseId: (() -> Unit)? = null) {
    val total = r.total.coerceAtLeast(0.001)
    Ratlla()
    FilaResta(
        Icons.Outlined.Power, EstatIcona.GRIS, stringResource(R.string.ranquing_sempre),
        fonsW?.let { stringResource(R.string.ranquing_sempre_sub, num(it)) } ?: stringResource(R.string.historial_cal_un_dia),
        r.sempre, r.sempre / total, obreSempre,
    )
    val noSap = r.senseIdentificar + r.petits + r.liniaAltres + r.senseDetall
    val parts = buildList {
        if (r.senseIdentificar >= 0.05) add(stringResource(R.string.ranquing_no_sap_sense_nom, num(r.senseIdentificar, 1)))
        if (r.petits >= 0.05) add(stringResource(R.string.ranquing_no_sap_petits, num(r.petits, 1)))
        if (r.liniaAltres >= 0.05) add(stringResource(R.string.ranquing_no_sap_linia, nomLinia, num(r.liniaAltres, 1)))
        if (r.senseDetall >= 0.05) add(stringResource(R.string.ranquing_no_sap_fora, num(r.senseDetall, 1)))
    }
    Ratlla()
    FilaResta(
        Icons.Outlined.QuestionMark, EstatIcona.CAL, stringResource(R.string.ranquing_no_sap), parts.joinToString(" · "),
        noSap, noSap / total, obreSenseId,
    )
}

@Composable
private fun FilaResta(icona: ImageVector, estat: EstatIcona, titol: String, sub: String, kwh: Double, part: Double, obre: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (obre != null) Modifier.clickable(onClick = obre) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(14.dp))
        Cercle(icona, estat, 26.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(titol, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 11.sp, color = Suau, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${num(kwh, 1)} kWh", style = EstilNumero)
            Text("${num(part * 100)} %", fontSize = 11.sp, color = Suau)
        }
    }
}
