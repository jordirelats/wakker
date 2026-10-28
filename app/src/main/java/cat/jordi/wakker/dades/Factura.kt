package cat.jordi.wakker.dades

import kotlin.math.abs

/** Els preus que es poden llegir d'un contracte o d'una factura. */
enum class CampFactura {
    NL_NORMAAL, NL_DAL, NL_ENKEL, NL_EB, NL_VASTE, NL_NETBEHEER, NL_VERMINDERING, NL_INKOOP,
    ES_POTENCIA, ES_P1, ES_P2, ES_PUNTA, ES_PLA, ES_VALL, ES_FIX, ES_IEE, ES_IVA, ES_COMPTADOR, ES_BO_SOCIAL,
}

/**
 * Un preu trobat: el valor (ja en les unitats de la tarifa) i la línia del document d'on surt.
 * [deduit] vol dir que no surt tal qual al document (per exemple, la vall quan la factura només té dos preus).
 */
data class PreuTrobat(val camp: CampFactura, val valor: Double, val fragment: String, val deduit: Boolean = false)

data class LecturaFactura(val pais: Pais, val contracte: Contracte, val trobats: List<PreuTrobat>)

/**
 * Treu els preus del text d'un contracte o d'una factura (el que en llegeix l'OCR, línia a línia).
 * - Països Baixos: per seccions, com estan als contractes («Leveringstarief», «Energiebelasting»,
 *   «Netbeheerkosten»…); a cada secció, el preu de la línia. Els fixos es passen a per dia.
 * - Espanya: cada preu en «€/kW» o «€/kWh» de la línia, amb el període que té al davant (P1, punta…), perquè
 *   l'OCR pot ajuntar dues columnes en una sola línia. També l'impost elèctric, l'IVA, el bo social i el lloguer.
 * Provat amb un contracte holandès i amb factures espanyoles reals, que tenen
 * errors d'OCR com «0.042740» en lloc de «0,042740».
 */
object Factura {
    /** Un preu holandès: 1-3 xifres, coma i 2-6 decimals, que no sigui un tros d'una data o d'un número més llarg. */
    private val rePreu = Regex("""(?<![\d.,])(\d{1,3},\d{2,6})(?![\d.,])""")
    private const val MONEDA = """(?:€|eur(?:os)?)"""
    /** Un preu de 3-6 decimals; si l'OCR s'ha menjat la coma («0099189»), un 0 i 4-6 xifres. */
    private const val NUM = """(\d{1,2}[.,]\d{3,6}|0\d{4,6})"""
    private val rePotencia = Regex("""$NUM\s*$MONEDA\s*/\s*kW(?!h)""", RegexOption.IGNORE_CASE)
    private val reEnergia = Regex("""$NUM\s*$MONEDA\s*/\s*kWh""", RegexOption.IGNORE_CASE)
    private val reDia = Regex("""(\d{1,2}[.,]\d{3,6})\s*$MONEDA?\s*/\s*d[ií]a""", RegexOption.IGNORE_CASE)
    private val rePercent = Regex("""(\d{1,2}[.,]\d{1,8}|\d{1,2})\s*%""")
    private val reKwContractats = Regex("""contra(?:t|ct)ad[ao]s?\s*:\D*?(\d{1,2}[.,]?\d{0,3})\s*kW(?!h)""", RegexOption.IGNORE_CASE)
    /** El període; «P14:» és un «P1:» mal llegit (una xifra de més just abans dels dos punts). */
    private val rePeriode = Regex("""\bp\s?([123])(?:\d(?=\s*:))?\b|(punta)|(llano)|(valle)|\b(pla)\b|\b(vall)\b""", RegexOption.IGNORE_CASE)
    /** Preus que no són de la tarifa: el que es cobra pels excedents i la mitjana de tota la factura («ha salido a»). */
    private val reNoTarifa = Regex("""excedent|compensa|salido|sortit|media|mitj""", RegexOption.IGNORE_CASE)

    private fun num(s: String) =
        if ('.' !in s && ',' !in s && s.startsWith("0")) "0.${s.drop(1)}".toDouble() else s.replace(',', '.').toDouble()

    fun llegeix(text: String): LecturaFactura? {
        val linies = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val tot = text.lowercase()
        val holandes = listOf("leveringstarief", "energiebelasting", "netbeheer").any { it in tot }
        val trobats = if (holandes) preusHolandesos(linies) else preusEspanyols(linies)
        if (trobats.isEmpty()) return null
        val camps = trobats.map { it.camp }.toSet()
        return if (holandes) {
            val contracte = when {
                CampFactura.NL_NORMAAL in camps && CampFactura.NL_DAL in camps -> Contracte.FRANGES
                CampFactura.NL_INKOOP in camps || "dynamisch" in tot -> Contracte.DINAMIC
                else -> Contracte.FIX
            }
            LecturaFactura(Pais.NL, contracte, trobats)
        } else {
            val contracte = when {
                CampFactura.ES_PUNTA in camps -> Contracte.FRANGES
                "pvpc" in tot -> Contracte.DINAMIC
                else -> Contracte.FIX
            }
            LecturaFactura(Pais.ES, contracte, trobats)
        }
    }

    private fun preusHolandesos(linies: List<String>): List<PreuTrobat> {
        val trobats = linkedMapOf<CampFactura, PreuTrobat>()
        var seccio: CampFactura? = null
        var lev = false
        linies.forEachIndexed { i, l ->
            val b = l.lowercase()
            when {
                "leveringstarief" in b -> { lev = true; seccio = null }
                "teruglever" in b -> { lev = false; seccio = null }
                "vermindering" in b -> { lev = false; seccio = CampFactura.NL_VERMINDERING }
                "energiebelasting" in b -> { lev = false; seccio = CampFactura.NL_EB }
                "netbeheer" in b -> { lev = false; seccio = CampFactura.NL_NETBEHEER }
                "vaste leveringskosten" in b || "vastrecht" in b -> { lev = false; seccio = CampFactura.NL_VASTE }
                "inkoopvergoeding" in b -> { lev = false; seccio = CampFactura.NL_INKOOP }
            }
            val preu = rePreu.findAll(l).lastOrNull()?.let { num(it.groupValues[1]) } ?: return@forEachIndexed
            val camp = when {
                lev -> when {
                    "normaal" in b -> CampFactura.NL_NORMAAL
                    Regex("""\bdal\b""").containsMatchIn(b) -> CampFactura.NL_DAL
                    else -> CampFactura.NL_ENKEL
                }
                else -> seccio
            } ?: return@forEachIndexed
            // Els fixos poden anar per dia, per mes o per any: es miren aquesta línia i les dues següents
            val unitat = linies.drop(i).take(3).joinToString(" ").lowercase()
            val valor = when {
                camp !in setOf(CampFactura.NL_VASTE, CampFactura.NL_NETBEHEER, CampFactura.NL_VERMINDERING) -> preu
                "maand" in unitat -> preu * 12 / 365
                "jaar" in unitat -> preu / 365
                else -> preu
            }
            if (camp !in trobats) trobats[camp] = PreuTrobat(camp, abs(valor), l)
        }
        return trobats.values.toList()
    }

    /** El període que surt primer en un tros de línia: l'etiqueta del preu (a «P1 PLA SOLAR», el P1). 0 si no n'hi ha. */
    private fun periode(tros: String): Int {
        val m = rePeriode.find(tros) ?: return 0
        val g = m.groupValues
        return when {
            g[1].isNotEmpty() -> g[1].toInt()
            g[2].isNotEmpty() -> 1
            g[3].isNotEmpty() || g[5].isNotEmpty() -> 2
            else -> 3
        }
    }

    private fun preusEspanyols(linies: List<String>): List<PreuTrobat> {
        val trobats = linkedMapOf<CampFactura, PreuTrobat>()
        fun posa(c: CampFactura, v: Double, l: String) { if (c !in trobats) trobats[c] = PreuTrobat(c, v, l) }
        for (l in linies) {
            val b = l.lowercase()
            val kw = reKwContractats.find(l)
            if (kw != null) {
                posa(CampFactura.ES_POTENCIA, num(kw.groupValues[1]), l)
                continue
            }
            // Cada preu de la línia, amb el període que té al davant des del preu anterior
            var inici = 0
            val preus = (rePotencia.findAll(l).map { it to true } + reEnergia.findAll(l).map { it to false }).sortedBy { it.first.range.first }
            for ((m, potencia) in preus) {
                val tros = l.substring(inici, m.range.first)
                inici = m.range.last + 1
                if (reNoTarifa.containsMatchIn(tros)) continue
                val p = periode(tros)
                val v = num(m.groupValues[1])
                if (potencia) {
                    // Algunes factures diuen «P3» a la potència de la vall; a la tarifa és el P2
                    if (p == 1) posa(CampFactura.ES_P1, v, l) else if (p > 1) posa(CampFactura.ES_P2, v, l)
                } else {
                    posa(
                        when (p) {
                            1 -> CampFactura.ES_PUNTA
                            2 -> CampFactura.ES_PLA
                            3 -> CampFactura.ES_VALL
                            else -> CampFactura.ES_FIX
                        },
                        v, l,
                    )
                }
            }
            when {
                ("impuesto" in b || "impost" in b) && "ctric" in b ->
                    rePercent.find(l)?.let { val v = num(it.groupValues[1]); if (v > 1 && v < 10) posa(CampFactura.ES_IEE, v, l) }
                Regex("""\biva\b""").containsMatchIn(b) -> rePercent.find(l)?.let { posa(CampFactura.ES_IVA, num(it.groupValues[1]), l) }
                "bono social" in b || "bo social" in b -> reDia.find(l)?.let { posa(CampFactura.ES_BO_SOCIAL, num(it.groupValues[1]), l) }
                "alquiler" in b || "lloguer" in b -> reDia.find(l)?.let { posa(CampFactura.ES_COMPTADOR, num(it.groupValues[1]) * 365 / 12, l) }
            }
        }
        // Un sol preu d'energia (encara que digui «punta») vol dir preu fix; si n'hi ha dos, la vall és com el pla
        val franges = listOf(CampFactura.ES_PUNTA, CampFactura.ES_PLA, CampFactura.ES_VALL).filter { it in trobats }
        if (franges.size == 1 && CampFactura.ES_FIX !in trobats) {
            val unic = trobats.remove(franges.first())!!
            trobats[CampFactura.ES_FIX] = unic.copy(camp = CampFactura.ES_FIX)
        } else if (CampFactura.ES_PUNTA in trobats && CampFactura.ES_PLA in trobats && CampFactura.ES_VALL !in trobats) {
            val pla = trobats.getValue(CampFactura.ES_PLA)
            trobats[CampFactura.ES_VALL] = PreuTrobat(CampFactura.ES_VALL, pla.valor, pla.fragment, deduit = true)
        }
        return trobats.values.toList()
    }

    /**
     * Posa a la tarifa els preus triats. Si el document és d'un altre país que la tarifa, es comença una tarifa
     * nova d'aquell país; el tipus de contracte és el que s'ha deduït del document.
     */
    fun aplica(t: Tarifa, l: LecturaFactura, triats: Set<CampFactura>): Tarifa {
        var r = if (t.pais == l.pais) t.copy(contracte = l.contracte) else Tarifa(pais = l.pais, contracte = l.contracte, potenciaKw = t.potenciaKw, maxKw = t.maxKw, potenciaPosada = t.potenciaPosada)
        for (p in l.trobats) {
            if (p.camp !in triats) continue
            val v = p.valor
            r = when (p.camp) {
                CampFactura.NL_NORMAAL -> r.copy(normaal = v)
                CampFactura.NL_DAL -> r.copy(dal = v)
                CampFactura.NL_ENKEL -> r.copy(preuFix = v)
                CampFactura.NL_EB -> r.copy(ebKwh = v)
                CampFactura.NL_VASTE -> r.copy(proveidorDia = v)
                CampFactura.NL_NETBEHEER -> r.copy(xarxaDia = v)
                CampFactura.NL_VERMINDERING -> r.copy(descompteDia = v)
                CampFactura.NL_INKOOP -> r.copy(recarrec = v)
                CampFactura.ES_POTENCIA -> r.copy(potenciaKw = v, potenciaPosada = true)
                CampFactura.ES_P1 -> r.copy(potP1 = v)
                CampFactura.ES_P2 -> r.copy(potP2 = v)
                CampFactura.ES_PUNTA -> r.copy(punta = v)
                CampFactura.ES_PLA -> r.copy(pla = v)
                CampFactura.ES_VALL -> r.copy(vall = v)
                CampFactura.ES_FIX -> r.copy(preuFix = v)
                CampFactura.ES_IEE -> r.copy(iee = v / 100)
                CampFactura.ES_IVA -> r.copy(iva = v / 100)
                CampFactura.ES_COMPTADOR -> r.copy(comptadorMes = v)
                CampFactura.ES_BO_SOCIAL -> r.copy(boSocialDia = v)
            }
        }
        return r
    }
}
