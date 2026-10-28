package cat.jordi.wakker.dades

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Una mostra de l'entrenament: el salt de potència (W) que va fer un aparell en encendre's o en apagar-se,
 * mesurat per l'app quan l'usuari ho va marcar o quan va respondre una pregunta. [durada] és el temps que va
 * estar encès (s), si es coneix. [wMin] i [wMax]: l'interval de potència de l'episodi d'una pregunta (W per sobre
 * del fons), que serveix per als aparells que s'engeguen sols i no gasten sempre igual (els inverter). [variacio] i
 * [arrencada]: la firma de l'episodi (vegeu [Episodi]), quan se sap.
 */
data class Mostra(
    val id: Long, val aparellId: Long, val ts: Long, val dp: Double, val durada: Long?,
    val wMin: Double? = null, val wMax: Double? = null, val font: String = "",
    val variacio: Double? = null, val arrencada: Double? = null,
)

/**
 * El que l'app sap d'un aparell: la potència típica (la mediana de les mostres) i quantes mostres en té. Si
 * s'engega sol i se n'ha après l'interval d'un episodi, [wMin] i [wMax]: explica qualsevol potència d'aquest
 * interval. [automatica]: l'app l'ha trobat sola a les dades (el compressor de la nevera). [variacio] i [arrencada]: la
 * mediana de la firma de les mostres (maqueta 12), d'on surt la [forma].
 */
data class Referencia(
    val aparellId: Long, val w: Double, val mostres: Int, val duradaMin: Double?,
    val wMin: Double? = null, val wMax: Double? = null, val automatica: Boolean = false,
    val variacio: Double? = null, val arrencada: Double? = null,
) {
    /** Amb tres mostres ja es considera après; si l'ha trobat sola o en sap l'interval, també. */
    val apresa get() = mostres >= PER_APRENDRE || automatica || wMin != null

    val min get() = wMin ?: w
    val max get() = wMax ?: w

    val forma get() = Forma.de(variacio, arrencada)

    companion object {
        const val PER_APRENDRE = 3

        /** [ambInterval]: l'aparell s'engega sol, i se'n fa servir l'interval de les preguntes respostes. */
        fun de(aparellId: Long, mostres: List<Mostra>, ambInterval: Boolean = false): Referencia? {
            if (mostres.isEmpty()) return null
            // En directe, la durada és des que es va marcar «L'he encès» fins a «L'he apagat», no el que dura cada vegada
            // (una rentadora: 90 minuts, amb escalfaments de 15): no compta per a la firma
            val durades = mostres.filter { it.font != "directe" }.mapNotNull { it.durada }
            val amb = if (ambInterval) mostres.filter { it.wMin != null && it.wMax != null } else emptyList()
            return Referencia(
                aparellId, mediana(mostres.map { it.dp }), mostres.size,
                durades.takeIf { it.isNotEmpty() }?.let { mediana(it.map(Long::toDouble)) / 60 },
                wMin = amb.minOfOrNull { it.wMin!! }, wMax = amb.maxOfOrNull { it.wMax!! },
                variacio = mostres.mapNotNull { it.variacio }.takeIf { it.isNotEmpty() }?.let(::mediana),
                arrencada = mostres.mapNotNull { it.arrencada }.takeIf { it.isNotEmpty() }?.let(::mediana),
            )
        }
    }
}

fun mediana(xs: List<Double>): Double {
    val s = xs.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

enum class EstatPregunta { PENDENT, RESPOSTA, IGNORADA }

/** Un consum que l'app no ha sabut explicar: des de quan i quants W de més hi havia. */
data class Pregunta(val id: Long, val ts: Long, val dp: Double, val estat: EstatPregunta, val aparellId: Long?)

/** Quins aparells expliquen un excés de potència i quant en queda sense explicar (W). */
data class Explicacio(val aparells: List<Pair<Long, Double>>, val residu: Double)

/**
 * Explica un excés de potència sobre el fons amb les referències apreses: busca la combinació d'1 a 3 aparells
 * que s'hi acosta més sense passar-se'n (amb un marge del 15 %, o 40 W). Si la suma es passa una mica, es
 * retalla; el que no s'explica queda com a residu. És aproximat: dos aparells de la mateixa potència es confonen.
 * Un aparell amb interval (un inverter que s'engega sol) pot posar-hi qualsevol potència del seu interval, però
 * només explica un excés que hi cap: si n'hi ha més, no se sap si és en marxa, i no se'n resta res (si no, un
 * consum gran desconegut quedaria per sota del llindar dels avisos).
 */
class Explicador(referencies: List<Referencia>) {
    private class Combinacio(val min: Double, val max: Double, val membres: List<Referencia>)

    /** Les combinacions de valors fixos, de suma més petita a més gran; a igual suma, la de menys aparells l'última. */
    private val fixes: List<Combinacio>

    /** Les que tenen algun aparell amb interval, de menys a més aparells. */
    private val ambInterval: List<Combinacio>

    init {
        val r = referencies.filter { it.w > 0 }.sortedByDescending { it.max }.take(20)
        val llista = mutableListOf<Combinacio>()
        fun afegeix(m: List<Referencia>) {
            llista += Combinacio(m.sumOf { it.min }, m.sumOf { it.max }, m)
        }
        for (i in r.indices) {
            afegeix(listOf(r[i]))
            for (j in i + 1 until r.size) {
                afegeix(listOf(r[i], r[j]))
                for (k in j + 1 until r.size) afegeix(listOf(r[i], r[j], r[k]))
            }
        }
        val (interval, fix) = llista.partition { c -> c.membres.any { it.wMin != null } }
        fixes = (listOf(Combinacio(0.0, 0.0, emptyList())) + fix).sortedWith(compareBy<Combinacio> { it.min }.thenBy { -it.membres.size })
        ambInterval = interval.sortedBy { it.membres.size }
    }

    fun explica(exces: Double): Explicacio {
        if (exces <= 0 || (fixes.size == 1 && ambInterval.isEmpty())) return Explicacio(emptyList(), max(exces, 0.0))
        val marge = max(40.0, 0.15 * exces)
        // La combinació fixa més gran que no passa del sostre (cerca binària)
        var lo = 0
        var hi = fixes.lastIndex
        while (lo < hi) {
            val mig = (lo + hi + 1) / 2
            if (fixes[mig].min <= exces + marge) lo = mig else hi = mig - 1
        }
        val fixa = fixes[lo]
        // Amb un interval, només si l'excés hi cap (dins del marge); i si la fixa ja ho explica amb menys aparells, aquella
        val interval = ambInterval.firstOrNull { it.min <= exces + marge && it.max >= exces - marge }
        val tria = when {
            interval == null -> fixa
            fixa.membres.isNotEmpty() && exces - fixa.max <= marge && fixa.membres.size <= interval.membres.size -> fixa
            else -> interval
        }
        if (tria.membres.isEmpty()) return Explicacio(emptyList(), exces)
        val explicat = min(exces, tria.max)
        val parts = if (tria.min >= explicat) {
            // El mínim ja s'hi passa una mica: es retalla tot igual
            tria.membres.map { it.aparellId to it.min * explicat / tria.min }
        } else {
            // Cada interval s'emporta la seva part del que queda per sobre dels mínims
            val ample = tria.max - tria.min
            tria.membres.map { it.aparellId to it.min + if (ample > 0) (it.max - it.min) / ample * (explicat - tria.min) else 0.0 }
        }
        return Explicacio(parts, max(exces - tria.max, 0.0))
    }
}

/**
 * Els aparells que més s'assemblen a un consum desconegut, per proposar-los a la pregunta: per la potència i, si se'n sap
 * la firma (maqueta 12), també per quant dura i com és. Dos de la mateixa potència ja no queden empatats si un dura 3
 * minuts i l'altre 10, o si un polsa i l'altre és constant.
 */
fun endevina(
    w: Double, aparells: List<Aparell>, referencies: Map<Long, Referencia>, quants: Int = 2,
    minuts: Double? = null, forma: Forma? = null,
): List<Aparell> = aparells.sortedBy { a -> distancia(w, minuts, forma, a, referencies[a.id]) }.take(quants)

/**
 * Com s'assembla un consum a un aparell (0: igual): la potència (un interval après la conté sencera), i la durada i la forma
 * quan se saben de totes dues bandes. Les unitats són logaritmes: ×2 de potència fa 0,7.
 */
fun distancia(w: Double, minuts: Double?, forma: Forma?, a: Aparell, r: Referencia?): Double {
    val dw = if (r?.wMin != null && w in r.min..r.max) 0.0
    else abs(ln(max(r?.w ?: a.wTipic, 1.0) / max(w, 1.0)))
    val dd = if (minuts != null && r?.duradaMin != null) 0.5 * abs(ln(max(r.duradaMin, 1.0) / max(minuts, 1.0))) else 0.0
    val df = if (forma != null && r?.forma != null && forma != r.forma) 0.5 else 0.0
    return dw + dd + df
}
