package cat.jordi.wakker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Contracte
import cat.jordi.wakker.dades.Factura
import cat.jordi.wakker.dades.HoraPreu
import cat.jordi.wakker.dades.LecturaFactura
import cat.jordi.wakker.dades.Pais
import cat.jordi.wakker.dades.Tarifa
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Una línia que resumeix la tarifa, per a la configuració. */
@Composable
fun resumTarifa(t: Tarifa): String = when (t.pais) {
    Pais.ES -> stringResource(
        when (t.contracte) {
            Contracte.FIX -> R.string.preu_resum_es_fix
            Contracte.FRANGES -> R.string.preu_resum_es_franges
            Contracte.DINAMIC -> R.string.preu_resum_es_pvpc
        },
        num(t.potenciaKw, 1),
    )
    Pais.NL -> stringResource(
        when (t.contracte) {
            Contracte.FIX -> R.string.preu_resum_nl_fix
            Contracte.FRANGES -> R.string.preu_resum_nl_franges
            Contracte.DINAMIC -> R.string.preu_resum_nl_dinamic
        }
    )
    Pais.ALTRE -> stringResource(R.string.preu_resum_altre, num(t.preuFix, 3))
}

/** Què hi ha dins dels costos fixos, segons el país. */
@Composable
fun quinsFixos(t: Tarifa) = stringResource(
    when (t.pais) {
        Pais.ES -> R.string.preu_fixos_es
        Pais.NL -> R.string.preu_fixos_nl
        Pais.ALTRE -> R.string.preu_fixos_altre
    }
)

@Composable
private fun noms(pais: Pais) = when (pais) {
    Pais.ES -> listOf(stringResource(R.string.preu_contracte_fix), stringResource(R.string.preu_contracte_3_franges), "PVPC")
    Pais.NL -> listOf(
        stringResource(R.string.preu_contracte_fix), stringResource(R.string.preu_contracte_normaal_dal),
        stringResource(R.string.preu_contracte_dinamic),
    )
    Pais.ALTRE -> listOf(stringResource(R.string.preu_contracte_fix))
}

/** Un número per editar: coma decimal, sense separador de milers ni zeros de més. */
private fun textNum(x: Double, decimals: Int = 6): String =
    BigDecimal(x).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

/** Un camp numèric. Només avisa quan el text és un número vàlid. */
@Composable
private fun CampNum(etiqueta: String, valor: Double, unitat: String, ajuda: String? = null, enCanviar: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(textNum(valor)) }
    val bo = llegeixNum(text, milers = false) != null
    OutlinedTextField(
        text, { nou -> text = nou; llegeixNum(nou, milers = false)?.let(enCanviar) },
        Modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true, isError = !bo,
        suffix = { Text(unitat) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = ajuda?.let { { Text(it) } },
    )
}

/** Les hores d'avui (23 o 25 els dies del canvi d'hora) amb les franges d'una tarifa que encara no s'ha desat. */
private fun franges(t: Tarifa): List<HoraPreu> {
    val zona = ZoneId.systemDefault()
    val avui = LocalDate.now(zona)
    val inici = avui.atStartOfDay(zona).toEpochSecond()
    val dema = avui.plusDays(1).atStartOfDay(zona).toEpochSecond()
    return (inici until dema step 3600L).map { ts ->
        HoraPreu(ts, Instant.ofEpochSecond(ts).atZone(zona).hour, t.preuKwh(ts, zona) { null }, t.franja(ts, zona))
    }
}

@Composable
fun PantallaPreu(vm: WakkerViewModel) {
    // La tarifa que s'està editant es desa com a JSON, per si es gira la pantalla o Android tanca l'app amb la càmera oberta
    var t by rememberSaveable(stateSaver = Saver<Tarifa, String>(save = { it.json() }, restore = { Tarifa.de(it) })) { mutableStateOf(vm.config.tarifa) }
    val desada = vm.infoPreu
    // El que s'ha llegit d'un contracte o factura i s'està revisant; i quantes vegades se n'han posat preus,
    // perquè els camps es tornin a crear amb els valors nous
    var lectura by remember { mutableStateOf<LecturaFactura?>(null) }
    var posats by rememberSaveable { mutableIntStateOf(0) }

    lectura?.let { l ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
            RevisioDocument(l, enCancellar = { lectura = null }) { triats ->
                t = Factura.aplica(t, l, triats)
                posats++
                lectura = null
            }
            Spacer(Modifier.height(16.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TargetaEscaneig { lectura = it }
            // En canviar de país es comença amb els valors per defecte d'aquell país: el dinàmic funciona sense escriure res
            Pestanyes(Pais.entries, t.pais, { stringResource(it.nomId) }) { p ->
                if (p != t.pais) t = Tarifa(pais = p, contracte = if (p == Pais.ALTRE) Contracte.FIX else Contracte.DINAMIC, potenciaKw = t.potenciaKw, maxKw = t.maxKw, potenciaPosada = t.potenciaPosada)
            }
            if (t.pais != Pais.ALTRE) {
                Targeta {
                    TitolTargeta(stringResource(R.string.preu_quin_contracte))
                    Pestanyes(Contracte.entries, t.contracte, { noms(t.pais)[it.ordinal] }) { t = t.copy(contracte = it) }
                }
            }
            // Els camps es tornen a crear quan canvia el país o el contracte, o quan s'hi posen preus d'un document
            key(t.pais, t.contracte, posats) {
                when (t.pais) {
                    Pais.ES -> Espanya(t, desada?.takeIf { it.tarifa.pais == Pais.ES && it.tarifa.dinamica }?.avui) { t = it }
                    Pais.NL -> PaisosBaixos(t, desada?.takeIf { it.tarifa.pais == Pais.NL && it.tarifa.dinamica }?.avui) { t = it }
                    Pais.ALTRE -> Targeta {
                        CampNum(stringResource(R.string.preu_kwh_tot_inclos), t.preuFix, "€") { t = t.copy(preuFix = it) }
                        CampNum(
                            stringResource(R.string.preu_costos_fixos), t.fixosMes, stringResource(R.string.preu_unitat_mes),
                            stringResource(R.string.preu_costos_fixos_ajuda),
                        ) { t = t.copy(fixosMes = it) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = { vm.desaTarifa(t) }, Modifier.fillMaxWidth().padding(12.dp)) { Text(stringResource(R.string.preu_desa)) }
    }
}

@Composable
private fun Espanya(t: Tarifa, mercat: List<HoraPreu>?, canvia: (Tarifa) -> Unit) {
    fun ambImpostos(x: Double) = x * (1 + t.iee) * (1 + t.iva)
    Targeta {
        when (t.contracte) {
            Contracte.FIX -> {
                TitolTargeta(stringResource(R.string.preu_energia))
                CampNum(
                    stringResource(R.string.preu_kwh_sense_impostos), t.preuFix, "€", stringResource(R.string.preu_kwh_sense_impostos_ajuda),
                ) { canvia(t.copy(preuFix = it)) }
            }
            Contracte.FRANGES -> {
                TitolTargeta(stringResource(R.string.preu_energia_franges))
                TiraPreus(franges(t), null, 14.dp)
                Nota(stringResource(R.string.preu_franges_es_nota), Modifier.padding(vertical = 4.dp))
                CampNum(stringResource(R.string.franja_punta), t.punta, "€/kWh") { canvia(t.copy(punta = it)) }
                CampNum(stringResource(R.string.franja_pla), t.pla, "€/kWh") { canvia(t.copy(pla = it)) }
                CampNum(stringResource(R.string.franja_vall), t.vall, "€/kWh") { canvia(t.copy(vall = it)) }
                Nota(
                    stringResource(
                        R.string.preu_amb_impostos_franges,
                        num(ambImpostos(t.punta), 3), num(ambImpostos(t.pla), 3), num(ambImpostos(t.vall), 3),
                    )
                )
            }
            Contracte.DINAMIC -> {
                TitolTargeta(stringResource(R.string.preu_energia_pvpc))
                Nota(stringResource(R.string.preu_pvpc_nota), Modifier.padding(bottom = 6.dp))
                if (mercat != null) BarresPreu(mercat, null) else Nota(stringResource(R.string.preu_desa_i_es_baixaran))
            }
        }
    }
    Targeta {
        TitolTargeta(stringResource(R.string.preu_titol_potencia))
        CampNum(stringResource(R.string.preu_potencia), t.potenciaKw, "kW") { canvia(t.copy(potenciaKw = it, potenciaPosada = true)) }
        CampNum("P1", t.potP1, stringResource(R.string.preu_unitat_kw_dia)) { canvia(t.copy(potP1 = it)) }
        CampNum("P2", t.potP2, stringResource(R.string.preu_unitat_kw_dia), stringResource(R.string.preu_sense_impostos)) { canvia(t.copy(potP2 = it)) }
        Text(
            stringResource(R.string.preu_potencia_al_mes, euros(ambImpostos(t.potenciaKw * (t.potP1 + t.potP2)) * 365 / 12)),
            fontSize = 13.sp, fontWeight = FontWeight.Medium,
        )
    }
    Targeta {
        TitolTargeta(stringResource(R.string.preu_titol_impostos))
        CampNum(stringResource(R.string.preu_impost_electric), t.iee * 100, "%") { canvia(t.copy(iee = it / 100)) }
        CampNum(stringResource(R.string.preu_iva), t.iva * 100, "%") { canvia(t.copy(iva = it / 100)) }
        CampNum(
            stringResource(R.string.preu_comptador), t.comptadorMes, stringResource(R.string.preu_unitat_mes),
            stringResource(R.string.preu_comptador_ajuda),
        ) { canvia(t.copy(comptadorMes = it)) }
        CampNum(
            stringResource(R.string.preu_bo_social), t.boSocialDia, stringResource(R.string.preu_unitat_dia),
            stringResource(R.string.preu_bo_social_ajuda),
        ) { canvia(t.copy(boSocialDia = it)) }
    }
}

@Composable
private fun PaisosBaixos(t: Tarifa, mercat: List<HoraPreu>?, canvia: (Tarifa) -> Unit) {
    Nota(stringResource(R.string.preu_nl_nota), Modifier.padding(horizontal = 4.dp))
    Targeta {
        when (t.contracte) {
            Contracte.FIX -> {
                TitolTargeta(stringResource(R.string.escaneig_camp_nl_enkel))
                CampNum(stringResource(R.string.escaneig_camp_nl_enkel), t.preuFix, "€/kWh", stringResource(R.string.preu_amb_iva_sense_eb)) { canvia(t.copy(preuFix = it)) }
            }
            Contracte.FRANGES -> {
                TitolTargeta(stringResource(R.string.preu_titol_normaal_dal))
                TiraPreus(franges(t), null, 14.dp)
                Nota(stringResource(R.string.preu_dal_nota), Modifier.padding(vertical = 4.dp))
                CampNum(stringResource(R.string.escaneig_camp_nl_normaal), t.normaal, "€/kWh") { canvia(t.copy(normaal = it)) }
                CampNum(stringResource(R.string.escaneig_camp_nl_dal), t.dal, "€/kWh", stringResource(R.string.preu_amb_iva_sense_eb)) { canvia(t.copy(dal = it)) }
            }
            Contracte.DINAMIC -> {
                TitolTargeta(stringResource(R.string.preu_energia_hora))
                Nota(stringResource(R.string.preu_dinamic_nl_nota), Modifier.padding(bottom = 6.dp))
                if (mercat != null) BarresPreu(mercat, null) else Nota(stringResource(R.string.preu_desa_i_es_baixaran))
                Spacer(Modifier.height(6.dp))
                CampNum(stringResource(R.string.escaneig_camp_nl_inkoop), t.recarrec, "€/kWh", stringResource(R.string.preu_recarrec_ajuda)) { canvia(t.copy(recarrec = it)) }
            }
        }
        if (t.contracte != Contracte.DINAMIC) {
            CampNum(stringResource(R.string.escaneig_camp_nl_eb), t.ebKwh, "€/kWh", stringResource(R.string.preu_eb_ajuda)) { canvia(t.copy(ebKwh = it)) }
            val total = if (t.contracte == Contracte.FIX) stringResource(R.string.preu_cada_kwh, num(t.preuFix + t.ebKwh, 3))
            else stringResource(R.string.preu_cada_kwh_dos, num(t.normaal + t.ebKwh, 3), num(t.dal + t.ebKwh, 3))
            Text(total, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
    Targeta {
        TitolTargeta(stringResource(R.string.preu_titol_fixos))
        CampNum(stringResource(R.string.escaneig_camp_nl_vaste), t.proveidorDia, stringResource(R.string.preu_unitat_dag)) { canvia(t.copy(proveidorDia = it)) }
        CampNum(stringResource(R.string.escaneig_camp_nl_netbeheer), t.xarxaDia, stringResource(R.string.preu_unitat_dag)) { canvia(t.copy(xarxaDia = it)) }
        // Al contracte surt amb un menys davant («€- 1,72317»): es guarda sempre en positiu i es resta
        CampNum(
            stringResource(R.string.escaneig_camp_nl_vermindering), t.descompteDia, stringResource(R.string.preu_unitat_dag), stringResource(R.string.preu_descompte_ajuda),
        ) { canvia(t.copy(descompteDia = abs(it))) }
        Text(stringResource(R.string.preu_total_fixos, euros(t.fixosDia() * 365 / 12)), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
