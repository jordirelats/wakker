package cat.jordi.wakker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Categoria
import cat.jordi.wakker.dades.Com
import cat.jordi.wakker.dades.Contracte
import cat.jordi.wakker.dades.EncesAra
import cat.jordi.wakker.dades.Franja
import cat.jordi.wakker.dades.Funcionament
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.InfoPreu
import cat.jordi.wakker.dades.NivellPotencia
import cat.jordi.wakker.dades.Pais
import cat.jordi.wakker.dades.ResumSetmana
import cat.jordi.wakker.dades.TipusEnces
import cat.jordi.wakker.dades.endevina
import cat.jordi.wakker.dades.Vegada
import cat.jordi.wakker.dades.encesDelCanal
import cat.jordi.wakker.dades.nivellPotencia
import cat.jordi.wakker.shelly.Origen
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ara (maqueta 18): els watts com a protagonista amb l'arc de la potència contractada, avui i el preu d'ara al costat, les
 * últimes 24 hores suavitzades, i «Encès ara» amb els watts en gran. El tècnic queda darrere de «Detalls».
 */
@Composable
fun PantallaAra(vm: WakkerViewModel) {
    // Sense Shelly, només amb endolls (maqueta 20)
    if (vm.config.modeEndolls) return AraEndolls(vm)
    val l = vm.lectura
    val teLinia = vm.config.teLinia
    val info = vm.infoPreu
    val preu = info?.ara?.preu
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            l?.origen == Origen.LOCAL -> LiniaEstat(stringResource(R.string.ara_origen_local), Ok, Modifier.padding(start = 2.dp))
            // A l'adreça de casa hi respon un altre Shelly: no se'n fan servir les dades (1.1.3)
            vm.altreShelly != null -> LiniaEstat(stringResource(R.string.ara_altre_shelly), Desconegut, Modifier.padding(start = 2.dp))
            l?.origen == Origen.NUVOL -> LiniaEstat(stringResource(R.string.ara_origen_nuvol), PrimariClar, Modifier.padding(start = 2.dp))
            !vm.haLlegit -> LiniaEstat(stringResource(R.string.ara_connectant), Gris, Modifier.padding(start = 2.dp))
            vm.config.teNuvol -> LiniaEstat(stringResource(R.string.ara_sense_connexio), Desconegut, Modifier.padding(start = 2.dp))
            // Amb l'adreça del Shelly i sense núvol: és el Shelly que no respon, no que falti la clau (1.1.3)
            vm.config.host.isNotBlank() -> LiniaEstat(stringResource(R.string.ara_no_arribo_shelly), Desconegut, Modifier.padding(start = 2.dp))
            else -> LiniaEstat(stringResource(R.string.ara_falta_clau), Desconegut, Modifier.padding(start = 2.dp))
        }
        vm.versioNova?.let { v ->
            Targeta(color = PrimariFluix) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ara_versio_nova, v.versio), Modifier.weight(1f), fontSize = 13.sp)
                    Enllac(stringResource(R.string.ara_actualitza)) { vm.seccioConfig = SeccioConfig.SOBRE; vm.pantalla = Pantalla.CONNECTA }
                }
            }
        }
        val limit = vm.config.tarifa.limitKw
        val nivell = nivellPotencia(l?.total?.w ?: 0.0, limit)

        // Els watts, l'arc, i al costat avui i el preu d'ara
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    // Sense lectura, un guió gris i prim, no una barra negra (1.1.3)
                    Text(
                        l?.let { num(it.total.w) } ?: "–",
                        style = if (l != null) EstilHero.copy(color = colorNivell(nivell, TextPrincipal)) else EstilHero.copy(color = Gris, fontWeight = FontWeight.Normal),
                        maxLines = 1,
                    )
                    Text(" W", Modifier.padding(bottom = 6.dp), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Suau)
                }
                if (l != null && limit != null) Arc(l.total.w, limit, nivell)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Dada(
                    stringResource(R.string.ara_avui),
                    // Abans del primer refresc, «—» i no 0,00 kWh (1.1.3)
                    if (!vm.carregat) "—" else (if (vm.avuiAproximat) "≈ " else "") + "${num(vm.avuiKwh, 2)} kWh", if (vm.carregat) euros(vm.avuiEuros) else "",
                )
                val h = info?.ara
                Dada(
                    stringResource(R.string.ara_ara),
                    if (l != null && preu != null) context.getString(R.string.ara_euros_hora, euros(l.total.w / 1000 * preu)) else "—",
                    when {
                        h == null -> ""
                        h.preu == null -> stringResource(R.string.ara_sense_preu, stringResource(h.franja.nomId))
                        h.franja == Franja.UNICA -> stringResource(R.string.ara_preu_unic, num(h.preu, 3))
                        else -> stringResource(R.string.ara_preu_franja, nomFranja(info, h.franja), num(h.preu, 3))
                    },
                    obre = { vm.pantalla = Pantalla.PREU },
                )
            }
        }
        if (l != null && limit != null && nivell == NivellPotencia.LIMIT) {
            Targeta(color = LimitFluix) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = PotenciaLimit, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (l.total.w >= limit * 1000) R.string.ara_limit_passat else R.string.ara_limit_a_prop),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PotenciaLimit,
                    )
                }
                Nota(stringResource(R.string.ara_limit_text), Modifier.padding(top = 4.dp))
            }
        }

        // A Espanya, mentre no s'ha posat la potència contractada no s'avisa: es pregunta aquí
        var potenciaResposta by remember { mutableStateOf(false) }
        val tarifa = vm.config.tarifa
        if (!potenciaResposta && tarifa.pais == Pais.ES && limit == null && !vm.config.potenciaNoPreguntis) {
            PreguntaPotencia(
                desa = { vm.desaPotencia(it); potenciaResposta = true },
                // A la pantalla del preu, la foto o el PDF de la factura també en treuen la potència contractada
                factura = { vm.pantalla = Pantalla.PREU },
                araNo = { vm.config.potenciaNoPreguntis = true; potenciaResposta = true },
            )
        }

        // El dilluns (i fins que l'ha vist), el resum de la setmana passada en una línia (maqueta 15)
        vm.resumSetmana?.let { BannerSetmana(vm, it) }
        // L'aparell que s'ensenya d'inici a fi (maqueta 21)
        TargetaSessio(vm)

        // Les últimes 24 hores, suavitzades i a l'escala del dia
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Superficie, border = BorderStroke(1.dp, Vora)) {
            Column(Modifier.padding(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 6.dp)) {
                GraficaPotencia(vm.serie, teLinia, limit, suavitza = 5, escalaDades = true, marcaAra = true)
                when {
                    vm.serie.isEmpty() ->
                        Nota(stringResource(if (l?.origen == Origen.NUVOL) R.string.ara_dades_nuvol else R.string.ara_dades_wifi), Modifier.padding(start = 8.dp))
                    vm.serie.any { it.aprox } -> Box(Modifier.padding(start = 8.dp)) { NotaAproximat(stringResource(R.string.ara_aproximat), vm.config.host.isNotBlank()) }
                }
            }
        }

        if (info != null && info.tarifa.contracte != Contracte.FIX) {
            Targeta {
                TitolTargeta(stringResource(R.string.ara_mes_barat_avui))
                TiraPreus(info.avui, info.ara.ts)
                mesBarat(info)?.let { Nota(it, Modifier.padding(top = 4.dp)) }
            }
        }

        if (l != null) {
            EncesAraTargeta(vm, l.total.w, l.total.va, l.total.pf, l.total.v, l.linia?.w, preu)
            AvisCar(l.total.w, info)
        }
        if (vm.errorSincronitzacio != null && vm.haLlegit && l == null) {
            Nota(stringResource(R.string.ara_comprova_wifi))
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Com es diu la franja d'ara («punta fins a les 22 h», «normaal», «mercat»). */
@Composable
private fun nomFranja(info: InfoPreu?, franja: Franja): String {
    val nom = stringResource(franja.nomId)
    return when (franja) {
        Franja.UNICA, Franja.MERCAT -> nom
        else -> info?.canviaA?.let { stringResource(R.string.ara_franja_fins_a, nom, it) } ?: nom
    }
}

/** Una dada petita en una targeta: l'etiqueta en majúscules, el número i una línia a sota. */
@Composable
private fun Dada(etiqueta: String, valor: String, sota: String, obre: (() -> Unit)? = null) {
    Surface(
        Modifier.fillMaxWidth().then(if (obre != null) Modifier.clickable(onClick = obre) else Modifier),
        shape = RoundedCornerShape(12.dp), color = Superficie, border = BorderStroke(1.dp, Vora),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(etiqueta.uppercase(Idioma.locale), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Suau, letterSpacing = 0.05.em)
            Text(valor, style = EstilDada, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sota, fontSize = 11.sp, color = Suau, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        }
    }
}

/**
 * Un arc prim contra la potència contractada (maqueta 18): ple fins a la part que es fa servir, amb les marques del 80 % (gris)
 * i el 90 % (ambre), i el percentatge al mig.
 */
@Composable
private fun Arc(w: Double, limitKw: Double, nivell: NivellPotencia) {
    val mesura = rememberTextMeasurer()
    val part = (w / (limitKw * 1000)).toFloat().coerceIn(0f, 1f)
    val pct = "${num(part * 100.0)}\u00A0%"
    val de = stringResource(R.string.ara_arc_de, num(limitKw, 2))
    val estilPct = EstilNumero.copy(fontSize = 15.sp)
    val estilDe = EstilNumero.copy(fontSize = 10.sp, fontFamily = Inter, fontWeight = FontWeight.Normal, color = Suau)
    val colorPle = colorNivell(nivell, Primari)
    val gris = GrisFluix
    val marca80 = Suau
    val marca90 = Desconegut
    Canvas(Modifier.padding(top = 2.dp).width(150.dp).height(76.dp).semantics { contentDescription = "$pct $de" }) {
        val gruix = 7.dp.toPx()
        val r = 60.dp.toPx()
        val cx = size.width / 2
        val cy = 66.dp.toPx()
        val caixa = Size(2 * r, 2 * r)
        val dalt = Offset(cx - r, cy - r)
        drawArc(gris, 180f, 180f, false, dalt, caixa, style = Stroke(gruix, cap = StrokeCap.Round))
        drawArc(colorPle, 180f, 180f * part.coerceAtLeast(0.012f), false, dalt, caixa, style = Stroke(gruix, cap = StrokeCap.Round))
        for ((f, color) in listOf(0.8f to marca80, 0.9f to marca90)) {
            val a = PI * (1 - f)
            val p0 = Offset(cx + (r - 7.dp.toPx()) * cos(a).toFloat(), cy - (r - 7.dp.toPx()) * sin(a).toFloat())
            val p1 = Offset(cx + (r + 7.dp.toPx()) * cos(a).toFloat(), cy - (r + 7.dp.toPx()) * sin(a).toFloat())
            drawLine(color, p0, p1, 2.dp.toPx(), cap = StrokeCap.Round)
        }
        val m1 = mesura.measure(pct, estilPct).size
        drawText(mesura, pct, Offset(cx - m1.width / 2f, cy - 10.dp.toPx() - m1.height), estilPct)
        val m2 = mesura.measure(de, estilDe).size
        drawText(mesura, de, Offset(cx - m2.width / 2f, cy - 7.dp.toPx()), estilDe)
    }
}

/** El resum de la setmana passada, en una línia: el total, i «Veure» (maqueta 18). */
@Composable
private fun BannerSetmana(vm: WakkerViewModel, s: ResumSetmana) {
    val rq = s.resum.ranquing ?: return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PrimariFluix).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.ara_setmana_passada, num(rq.total, 1), euros(s.resum.euros.total)), Modifier.weight(1f),
            fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Enllac(stringResource(R.string.ara_veure)) { vm.tancaResum(); vm.miraSetmana(s.dilluns) }
        Icon(
            Icons.Outlined.Close, stringResource(R.string.resum_d_acord),
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = vm::tancaResum).padding(6.dp), tint = Suau,
        )
    }
}

/** Una fila de «Encès ara», abans d'ordenar-les pel que gasten. [obre]: què passa en tocar-la. */
private class FilaAra(
    val icona: ImageVector, val estat: EstatIcona, val titol: String, val subtitol: String, val w: Double,
    val obre: (() -> Unit)? = null,
)

/**
 * Què hi ha encès ara: cada cosa una fila, de més a menys consum (maqueta 9), amb els watts en gran i els €/h en petit.
 * Amb el mateix criteri que el rànquing (maqueta 15): les vegades que encara duren, amb el seu aparell. El tècnic (VA,
 * factor de potència, tensió) i la nota de fora de casa, darrere de «Detalls».
 */
@Composable
private fun EncesAraTargeta(vm: WakkerViewModel, wTotal: Double, va: Double, pf: Double, volts: Double, wLinia: Double?, preu: Double?) {
    val context = LocalContext.current
    val teLinia = vm.config.teLinia
    var detalls by rememberSaveable { mutableStateOf(false) }
    // Una cosa encesa que l'app no sap què és: tocant-la, es diu aquí mateix (1.1.3; abans portava a «Per fer», on no hi era)
    var triant by remember { mutableStateOf<Vegada?>(null) }
    Targeta {
        TitolTargeta(stringResource(R.string.ara_ences_ara))
        val linia = if (teLinia) (wLinia ?: 0.0).coerceAtLeast(0.0) else 0.0
        val resta = (wTotal - linia).coerceAtLeast(0.0)
        val fons = vm.fonsW
        val sempre = if (fons != null) minOf(resta, fons) else 0.0
        val o = vm.obertes
        val vegades = if (o?.fresques == true) o.vegades else emptyList()
        val ara = System.currentTimeMillis() / 1000
        val noms = vm.aparells.associateBy { it.id }
        val files = mutableListOf<FilaAra>()
        @Composable
        fun fila(ences: EncesAra, alaLinia: Boolean) {
            // Si l'aparell s'acaba d'esborrar, fins al proper refresc és una cosa sense nom
            val a = ences.aparellId?.let(noms::get)
            val e = if (ences.tipus == TipusEnces.APARELL && a == null) ences.copy(tipus = TipusEnces.SENSE_IDENTIFICAR, com = null) else ences
            val desDe = e.desDe?.let { horaMinut(it) }
            val fins = e.kwh?.takeIf { it >= 0.05 }?.let { num(it, 1) }
            val quan = when {
                desDe != null && fins != null -> stringResource(R.string.ara_des_de_kwh, desDe, fins)
                desDe != null -> stringResource(R.string.ara_des_de, desDe)
                else -> stringResource(R.string.ara_acaba_encendre)
            }
            files += when (e.tipus) {
                TipusEnces.APARELL -> FilaAra(
                    icona(a?.categoria ?: Categoria.ALTRES), if (e.com == null) EstatIcona.NEUTRE else EstatIcona.SAP, a?.nom.orEmpty(),
                    when {
                        e.com == null && desDe != null -> stringResource(R.string.ara_sembla_ell_des_de, quan)
                        e.com == null -> stringResource(R.string.ara_sembla_ell)
                        alaLinia -> stringResource(R.string.ara_a_la_pinca, quan)
                        else -> quan
                    },
                    e.w, obre = a?.let { { vm.obreAparell(it) } },
                )
                TipusEnces.SENSE_IDENTIFICAR, TipusEnces.NOU -> FilaAra(
                    Icons.Outlined.QuestionMark, EstatIcona.CAL,
                    if (alaLinia) stringResource(R.string.ranquing_linia_altres, vm.config.nomLinia) else stringResource(R.string.ara_sense_identificar),
                    if (e.tipus == TipusEnces.NOU || e.vegada == null) stringResource(R.string.ara_acaba_encendre_no_se) else stringResource(R.string.ara_no_se_que_es, quan),
                    e.w, obre = e.vegada?.let { v -> { triant = v } },
                )
                TipusEnces.NEVERA -> FilaAra(
                    icona(o?.nevera?.categoria ?: Categoria.NEVERA), EstatIcona.SAP, o?.nevera?.nom.orEmpty(),
                    stringResource(R.string.ara_compressor_text), e.w, obre = o?.nevera?.let { { vm.obreAparell(it) } },
                )
                TipusEnces.PETITS -> if (alaLinia) FilaAra(
                    Icons.Outlined.Bolt, EstatIcona.NEUTRE, stringResource(R.string.ranquing_linia_altres, vm.config.nomLinia),
                    stringResource(R.string.ranquing_linia_altres_text), e.w,
                ) else FilaAra(
                    Icons.Outlined.LightMode, EstatIcona.NEUTRE, stringResource(R.string.ranquing_petits),
                    stringResource(R.string.ranquing_petits_text), e.w,
                )
            }
        }
        if (teLinia) {
            if (vm.aparellsLinia.isEmpty()) {
                // Sense aparells a la línia, la línia sencera, com al rànquing
                files += FilaAra(
                    Icons.Outlined.Bolt, EstatIcona.SAP, vm.config.nomLinia,
                    stringResource(if (linia < 10) R.string.ara_linia_apagat_text else R.string.ara_linia_text), linia,
                )
            } else {
                encesDelCanal(ara, 1, linia, vegades, vm.aparellsLinia, vm.referenciesLinia).forEach { fila(it, alaLinia = true) }
            }
        }
        if (fons != null) {
            // Els aparells sempre encesos (el router) diuen que funcionen, amb la seva potència; la resta del fons és «altres en espera».
            // Tocant-los, la caça del sempre encès
            val sempreEncesos = vm.aparells.filter { it.funciona == Funcionament.SEMPRE }
            val suma = sempreEncesos.sumOf { it.wTipic }
            val escala = if (suma > sempre && suma > 0) sempre / suma else 1.0
            sempreEncesos.forEach { a ->
                files += FilaAra(icona(a.categoria), EstatIcona.GRIS, a.nom, stringResource(R.string.ara_sempre_aparell_text), a.wTipic * escala, obre = vm::obreEspera)
            }
            val altres = sempre - suma * escala
            if (sempreEncesos.isEmpty()) {
                files += FilaAra(Icons.Outlined.Power, EstatIcona.GRIS, stringResource(R.string.ara_sempre_ences), stringResource(R.string.ara_sempre_text), sempre, obre = vm::obreEspera)
            } else if (altres >= 1) {
                files += FilaAra(Icons.Outlined.Power, EstatIcona.GRIS, stringResource(R.string.aparells_altres_espera), stringResource(R.string.ara_altres_espera_text), altres, obre = vm::obreEspera)
            }
            encesDelCanal(ara, 0, resta - sempre, vegades, vm.aparellsAEntrenar, vm.referencies, o?.neveraW).forEach { fila(it, alaLinia = false) }
        } else {
            files += FilaAra(Icons.Outlined.QuestionMark, EstatIcona.CAL, stringResource(R.string.ara_sense_identificar), stringResource(R.string.ara_residu_sense_fons_text), resta)
        }
        val ordenades = files.sortedByDescending { it.w }
        ordenades.forEachIndexed { i, f ->
            if (i > 0) Ratlla()
            Row(
                Modifier.fillMaxWidth().then(if (f.obre != null) Modifier.clickable(onClick = f.obre) else Modifier)
                    .then(if (f.w < 10) Modifier.alpha(0.55f) else Modifier).padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cercle(f.icona, f.estat, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.titol, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.subtitol, fontSize = 11.sp, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${num(f.w)} W", style = EstilNumero)
                    if (preu != null) Text(context.getString(R.string.ara_euros_hora, euros(f.w / 1000 * preu)), fontSize = 11.sp, color = Suau)
                }
            }
        }
        // El tècnic, i des de fora que només es mira la potència: darrere de «Detalls»
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.End) {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { detalls = !detalls }.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ara_detalls), fontSize = 12.sp, color = Suau)
                Icon(if (detalls) Icons.Outlined.ExpandLess else Icons.Outlined.ChevronRight, null, Modifier.size(14.dp), tint = Suau)
            }
        }
        if (detalls) {
            if (o?.fresques != true && fons != null) Nota(stringResource(R.string.ara_nomes_potencia), Modifier.padding(bottom = 4.dp))
            Nota(stringResource(R.string.ara_detall, num(va), num(pf, 2), num(volts, 1)))
        }
    }
    triant?.let { v ->
        val candidats = if (v.canal == 1) vm.aparellsLinia else vm.aparellsAEntrenar
        val refs = if (v.canal == 1) vm.referenciesLinia else vm.referencies
        val suggerits = endevina(v.w, candidats, refs, 3, v.minuts.toDouble(), v.forma)
        TriaAparell(
            stringResource(R.string.pics_vegada, horaMinut(v.ts), durada(v.minuts)), suggerits, candidats, teLinia,
            tria = { vm.responVegada(v, it.id) }, nou = { vm.responVegada(v, null, it.copy(linia = v.canal == 1)) }, tanca = { triant = null },
            senseLinia = v.canal == 0 && teLinia && vm.aparellsLinia.isNotEmpty(), nomLinia = vm.config.nomLinia,
        )
    }
}

/** Quina potència hi ha contractada, perquè l'app sàpiga quan avisar. */
@Composable
private fun PreguntaPotencia(desa: (Double) -> Unit, factura: () -> Unit, araNo: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val kw = llegeixNum(text, milers = false)?.takeIf { it in 1.0..100.0 }
    Targeta(color = PrimariFluix) {
        Text(stringResource(R.string.ara_potencia_titol), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Nota(stringResource(R.string.ara_potencia_text))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f), singleLine = true,
                suffix = { Text("kW") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { kw?.let(desa) }, enabled = kw != null) { Text(stringResource(R.string.connecta_desa)) }
        }
        Row {
            TextButton(onClick = factura) { Text(stringResource(R.string.ara_potencia_factura)) }
            TextButton(onClick = araNo) { Text(stringResource(R.string.ara_potencia_ara_no)) }
        }
    }
}

/** L'hora més barata de les properes 24, si val la pena esperar-la. */
@Composable
private fun mesBarat(info: InfoPreu): String? {
    val ara = info.ara.preu ?: return null
    val barata = info.properes.filter { it.preu != null }.minByOrNull { it.preu!! } ?: return null
    if (barata.preu!! > ara * 0.9) return stringResource(R.string.ara_ja_barat)
    return stringResource(R.string.ara_mes_barata, barata.hora, num(barata.preu, 3), num((1 - barata.preu / ara) * 100))
}

/** Si la casa gasta molt en una hora cara, ho diu i proposa quan seria més barat. */
@Composable
private fun AvisCar(w: Double, info: InfoPreu?) {
    val ara = info?.ara?.preu ?: return
    val barata = info.properes.filter { it.preu != null }.minByOrNull { it.preu!! } ?: return
    if (w < 1500 || barata.preu!! > ara * 0.85) return
    Targeta(color = DesconegutFluix) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Desconegut, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ara_avis_car_titol), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.ara_avis_car_text, num(w), num(ara, 3), barata.hora, euros(w / 1000 * barata.preu), euros(w / 1000 * ara)),
            Modifier.padding(top = 4.dp), fontSize = 13.sp, lineHeight = 18.sp, color = TextPrincipal,
        )
    }
}
