package cat.jordi.wakker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Idioma

/**
 * La pantalla Ara sense Shelly, només amb endolls (maqueta 20): la suma del que fan ara els endolls (no és tota la casa, i
 * ho diu), el que han gastat avui, i cada aparell amb endoll amb el que fa ara, el que ha gastat avui i el cost per hora.
 * A baix, que amb un Shelly es veuria tota la casa.
 */
@Composable
fun AraEndolls(vm: WakkerViewModel) {
    val periodes = vm.periodesEndoll.filter { it.finsA == null }
    val avui = vm.avuiEndolls?.aparells.orEmpty().associate { it.aparell?.id to it.kwh }
    val preu = vm.infoPreu?.ara?.preu
    // Cada endoll posat a un aparell, amb la lectura d'ara (o la darrera, si és vella)
    val files = vm.endolls.mapNotNull { e ->
        val p = periodes.lastOrNull { it.endollId == e.id } ?: return@mapNotNull null
        val a = vm.aparells.firstOrNull { it.id == p.aparellId } ?: return@mapNotNull null
        Triple(e, a, vm.lecturesEndolls[e.id])
    }.sortedByDescending { it.third?.w ?: -1.0 }
    val total = files.sumOf { it.third?.w ?: 0.0 }
    val algunaLectura = files.any { it.third != null }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LiniaEstat(
            if (algunaLectura) stringResource(R.string.mode_ara_estat, files.size) else stringResource(R.string.mode_ara_cap),
            if (algunaLectura) Ok else Desconegut, Modifier.padding(start = 2.dp),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (algunaLectura) num(total) else "–", style = EstilHero, maxLines = 1)
                    Text(" W", Modifier.padding(bottom = 6.dp), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Suau)
                }
                Text(stringResource(R.string.mode_ara_suma), fontSize = 12.sp, color = Suau)
            }
            Surface(Modifier.padding(top = 6.dp).width(140.dp), shape = RoundedCornerShape(12.dp), color = Superficie, border = BorderStroke(1.dp, Vora)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text(stringResource(R.string.ara_avui).uppercase(Idioma.locale), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Suau, letterSpacing = 0.05.em)
                    Text("${num(vm.avuiKwh, 2)} kWh", style = EstilDada, maxLines = 1)
                    Text(euros(vm.avuiEuros), fontSize = 11.sp, color = Suau)
                }
            }
        }
        Targeta {
            TitolTargeta(stringResource(R.string.ara_ences_ara))
            if (files.isEmpty()) Nota(stringResource(R.string.mode_ara_sense_aparell))
            files.forEachIndexed { i, (e, a, l) ->
                if (i > 0) Ratlla()
                Row(Modifier.fillMaxWidth().clickable { vm.obreAparell(a) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cercle(icona(a.categoria), if ((l?.w ?: 0.0) >= 2) EstatIcona.SAP else EstatIcona.NEUTRE, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.nom, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.mode_ara_fila, e.nom, num(avui[a.id] ?: 0.0, 2)), fontSize = 11.sp, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(l?.let { "${num(it.w)} W" } ?: "–", style = EstilNumero)
                        if (l != null && preu != null) Text("${euros(l.w / 1000 * preu)}/h", fontSize = 11.sp, color = Suau)
                    }
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = PrimariFluix) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bolt, null, Modifier.size(16.dp), tint = Primari)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mode_ara_avis), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}
