package cat.jordi.wakker.dades

import androidx.annotation.StringRes
import cat.jordi.wakker.R
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Pais(@StringRes val nomId: Int) { ES(R.string.pais_es), NL(R.string.pais_nl), ALTRE(R.string.pais_altre) }

enum class Contracte { FIX, FRANGES, DINAMIC }

enum class Franja(@StringRes val nomId: Int) {
    PUNTA(R.string.franja_punta), PLA(R.string.franja_pla), VALL(R.string.franja_vall), NORMAAL(R.string.franja_normaal),
    DAL(R.string.franja_dal), UNICA(R.string.franja_unica), MERCAT(R.string.franja_mercat)
}

/**
 * Com es paga la llum. Cada kWh es paga al preu de l'hora en què es gasta (amb els impostos per kWh);
 * el que és fix (potència, quotes, comptador, descompte d'impostos) va a part i no es reparteix pels aparells.
 *
 * - Espanya: els preus d'energia i de potència són sense impostos, com a la factura; s'hi afegeixen l'impost
 *   elèctric i l'IVA. Les franges són les de la 2.0TD. El dinàmic és el PVPC.
 * - Països Baixos: com al contracte. El leveringstarief (fix, o normaal i dal) porta l'IVA però no
 *   l'energiebelasting, que va a part; els costos fixos (vaste leveringskosten, netbeheer i la vermindering
 *   energiebelasting) són per dia. El dinàmic és el preu de mercat amb l'energiebelasting i l'IVA, més el
 *   recàrrec del proveïdor.
 * - Un altre país: un preu tot inclòs i uns fixos al mes.
 */
data class Tarifa(
    val pais: Pais = Pais.ALTRE,
    val contracte: Contracte = Contracte.FIX,
    val preuFix: Double = 0.25,
    val punta: Double = 0.0,
    val pla: Double = 0.0,
    val vall: Double = 0.0,
    val normaal: Double = 0.0,
    val dal: Double = 0.0,
    /** Països Baixos, dinàmic: el recàrrec del proveïdor (€/kWh, amb IVA). */
    val recarrec: Double = 0.02,
    val potenciaKw: Double = 4.6,
    /**
     * Espanya: el terme de potència, €/kW i dia, sense impostos. Per defecte, aproximadament el del PVPC 2026
     * (peatges i càrrecs, 27,70 €/kW i any, més el marge de comercialització).
     */
    val potP1: Double = 0.0844,
    val potP2: Double = 0.0020,
    /** Espanya: el lloguer del comptador, €/mes sense IVA. */
    val comptadorMes: Double = 0.81,
    /** Espanya: el finançament del bo social, €/dia sense impostos (2026: 0,024688). */
    val boSocialDia: Double = 0.024688,
    val iee: Double = 0.0511269632,
    val iva: Double = 0.21,
    /** Països Baixos: l'energiebelasting per kWh, amb IVA (2026: 0,11085). Se suma al leveringstarief. */
    val ebKwh: Double = 0.11085,
    /**
     * Països Baixos, per dia i amb IVA, com al contracte: vaste leveringskosten, netbeheerkosten i la vermindering
     * energiebelasting (que es resta). Per defecte, els valors típics del 2026.
     */
    val proveidorDia: Double = 0.33,
    val xarxaDia: Double = 1.31,
    val descompteDia: Double = 1.72317,
    /** Un altre país: els fixos al mes. */
    val fixosMes: Double = 0.0,
    /**
     * Països Baixos i un altre país: la potència màxima de la connexió (kW), per exemple l'aansluiting 1×35 A
     * són 8 kW. A Espanya és [potenciaKw]. Null si no s'ha posat.
     */
    val maxKw: Double? = null,
    /**
     * Espanya: si l'usuari ha posat la [potenciaKw] (a mà o des de la factura). Fins aleshores, els 4,6 kW per
     * defecte serveixen per calcular el terme de potència, però no per avisar: amb una potència més alta, l'avís
     * del 90 % saltaria sense motiu.
     */
    val potenciaPosada: Boolean = false,
) {
    /** La potència contractada (kW): fins on es pot arribar abans que salti. Null si no se sap. */
    val limitKw: Double? get() = if (pais == Pais.ES) potenciaKw.takeIf { potenciaPosada } else maxKw

    fun ambLimit(kw: Double?) =
        if (pais == Pais.ES) copy(potenciaKw = kw ?: potenciaKw, potenciaPosada = kw != null) else copy(maxKw = kw)

    val dinamica get() = contracte == Contracte.DINAMIC && pais != Pais.ALTRE

    private fun ambImpostosEs(x: Double) = x * (1 + iee) * (1 + iva)

    fun franja(ts: Long, zona: ZoneId): Franja {
        if (pais == Pais.ALTRE || contracte == Contracte.FIX) return Franja.UNICA
        if (contracte == Contracte.DINAMIC) return Franja.MERCAT
        val t = Instant.ofEpochSecond(ts).atZone(zona)
        val festa = t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY || festiu(pais, t.toLocalDate())
        val h = t.hour
        return when (pais) {
            Pais.ES -> when {
                festa || h < 8 -> Franja.VALL
                h in 10..13 || h in 18..21 -> Franja.PUNTA
                else -> Franja.PLA
            }
            else -> if (festa || h >= 23 || h < 7) Franja.DAL else Franja.NORMAAL
        }
    }

    /**
     * El preu d'un kWh gastat a l'instant [ts], amb els impostos per kWh. [mercat] dona el preu de mercat
     * desat d'aquella hora; si el contracte és dinàmic i no el tenim, torna null.
     */
    fun preuKwh(ts: Long, zona: ZoneId, mercat: (Long) -> Double?): Double? = when (pais) {
        Pais.ES -> when (contracte) {
            Contracte.FIX -> ambImpostosEs(preuFix)
            Contracte.FRANGES -> ambImpostosEs(
                when (franja(ts, zona)) {
                    Franja.PUNTA -> punta
                    Franja.PLA -> pla
                    else -> vall
                }
            )
            Contracte.DINAMIC -> mercat(ts)?.let(::ambImpostosEs)
        }
        Pais.NL -> when (contracte) {
            Contracte.FIX -> preuFix + ebKwh
            Contracte.FRANGES -> (if (franja(ts, zona) == Franja.DAL) dal else normaal) + ebKwh
            // El preu de mercat d'EnergyZero ja porta l'energiebelasting
            Contracte.DINAMIC -> mercat(ts)?.plus(recarrec)
        }
        Pais.ALTRE -> preuFix
    }

    /** Els costos fixos d'un dia, amb impostos. Als Països Baixos poden ser negatius: el descompte supera les quotes. */
    fun fixosDia(): Double = when (pais) {
        // El bo social porta l'impost elèctric (a les factures entra a la base de l'impost); el comptador, només l'IVA
        Pais.ES -> ambImpostosEs(potenciaKw * (potP1 + potP2) + boSocialDia) + comptadorMes * 12 / 365 * (1 + iva)
        Pais.NL -> proveidorDia + xarxaDia - descompteDia
        Pais.ALTRE -> fixosMes * 12 / 365
    }

    fun json(): String = JSONObject().apply {
        put("pais", pais.name); put("contracte", contracte.name); put("preuFix", preuFix)
        put("punta", punta); put("pla", pla); put("vall", vall); put("normaal", normaal); put("dal", dal)
        put("recarrec", recarrec); put("potenciaKw", potenciaKw); put("potP1", potP1); put("potP2", potP2)
        put("comptadorMes", comptadorMes); put("boSocialDia", boSocialDia); put("iee", iee); put("iva", iva); put("ebKwh", ebKwh)
        put("proveidorDia", proveidorDia); put("xarxaDia", xarxaDia); put("descompteDia", descompteDia); put("fixosMes", fixosMes)
        maxKw?.let { put("maxKw", it) }
        put("potenciaPosada", potenciaPosada)
    }.toString()

    companion object {
        fun de(text: String?): Tarifa? = text?.let {
            runCatching {
                val o = JSONObject(it)
                val d = Tarifa()
                val pais = Pais.valueOf(o.optString("pais", d.pais.name))
                // Fins a la 0.8, als Països Baixos el preu era tot inclòs i els fixos anaven al mes o a l'any:
                // es converteixen, i l'EB es deixa a 0 perquè el total del kWh no canviï
                val antiga = !o.has("ebKwh")
                Tarifa(
                    pais = pais,
                    contracte = Contracte.valueOf(o.optString("contracte", d.contracte.name)),
                    preuFix = o.optDouble("preuFix", d.preuFix),
                    punta = o.optDouble("punta", d.punta), pla = o.optDouble("pla", d.pla), vall = o.optDouble("vall", d.vall),
                    normaal = o.optDouble("normaal", d.normaal), dal = o.optDouble("dal", d.dal),
                    recarrec = o.optDouble("recarrec", d.recarrec), potenciaKw = o.optDouble("potenciaKw", d.potenciaKw),
                    potP1 = o.optDouble("potP1", d.potP1), potP2 = o.optDouble("potP2", d.potP2),
                    comptadorMes = o.optDouble("comptadorMes", d.comptadorMes), iee = o.optDouble("iee", d.iee),
                    // Les tarifes desades abans de la 0.10 no el tenien: es queden sense, com fins ara
                    boSocialDia = o.optDouble("boSocialDia", 0.0),
                    iva = o.optDouble("iva", d.iva),
                    ebKwh = if (antiga && pais == Pais.NL) 0.0 else o.optDouble("ebKwh", d.ebKwh),
                    proveidorDia = if (antiga) o.optDouble("proveidorMes", 10.59) * 12 / 365 else o.optDouble("proveidorDia", d.proveidorDia),
                    xarxaDia = if (antiga) o.optDouble("xarxaMes", 39.83) * 12 / 365 else o.optDouble("xarxaDia", d.xarxaDia),
                    descompteDia = if (antiga) o.optDouble("descompteAny", 628.96) / 365 else o.optDouble("descompteDia", d.descompteDia),
                    fixosMes = o.optDouble("fixosMes", d.fixosMes),
                    maxKw = o.optDouble("maxKw").takeIf { !it.isNaN() },
                    // Fins a la 0.17 no es desava: si la potència no és la de per defecte, és que l'usuari l'ha posada
                    potenciaPosada = o.optBoolean("potenciaPosada", o.has("potenciaKw") && o.optDouble("potenciaKw") != d.potenciaKw),
                )
            }.getOrNull()
        }
    }
}

/** Els festius en què tot el dia és vall (Espanya: els nacionals de data fixa) o dal (Països Baixos). */
fun festiu(pais: Pais, dia: LocalDate): Boolean {
    val md = dia.monthValue * 100 + dia.dayOfMonth
    return when (pais) {
        Pais.ES -> md in setOf(101, 106, 501, 815, 1012, 1101, 1206, 1208, 1225)
        Pais.NL -> {
            val pasqua = pasqua(dia.year)
            // Koningsdag és el 27 d'abril, o el 26 si el 27 és diumenge
            val koningsdag = LocalDate.of(dia.year, 4, 27).let { if (it.dayOfWeek == DayOfWeek.SUNDAY) it.minusDays(1) else it }
            md in setOf(101, 1225, 1226) || dia == koningsdag ||
                dia == pasqua.plusDays(1) || dia == pasqua.plusDays(39) || dia == pasqua.plusDays(50)
        }
        Pais.ALTRE -> false
    }
}

/** El diumenge de Pasqua (algorisme de Meeus/Jones/Butcher, calendari gregorià). */
fun pasqua(any: Int): LocalDate {
    val a = any % 19
    val b = any / 100
    val c = any % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val n = h + l - 7 * m + 114
    return LocalDate.of(any, n / 31, n % 31 + 1)
}
