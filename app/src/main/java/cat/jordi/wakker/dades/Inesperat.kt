package cat.jordi.wakker.dades

import java.time.Instant
import java.time.ZoneId

/**
 * Quan avisar d'un consum inesperat: quan el que les referències no expliquen passa de [llindarW] durant [minuts]
 * minuts seguits. Un sol avís per episodi (s'acaba quan fa més de 15 minuts que no passa del llindar) i, com a molt,
 * un cada 2 hores. L'estat es desa entre crida i crida (a Config); la mateixa regla serveix per comptar quants avisos
 * hauria donat la setmana passada.
 * Amb una setmana real de prova, 2 kW i 30 minuts haurien avisat 2 vegades.
 */
class DetectorInesperat(
    private val llindarW: Double, private val minuts: Int,
    var des: Long = 0, var alt: Long = 0, var avisat: Boolean = false, var darrerAvis: Long = 0,
) {
    /**
     * El residu (W) d'ara mateix; torna true si cal avisar ara. [pausa]: quant pot passar sense mirar-ho sense que l'episodi
     * torni a començar; minut a minut, 15 minuts; amb la lectura del treball de fons, que passa cada 15 minuts o més, més
     * llarga (1.1.3: abans cada crida el tornava a començar i no s'avisava mai).
     */
    fun mira(ts: Long, residu: Double, pausa: Long = PAUSA_S): Boolean {
        if (residu >= llindarW) {
            if (des == 0L || ts - alt > pausa) {
                des = ts
                avisat = false
            }
            alt = ts
            if (!avisat && ts - des >= minuts * 60L && ts - darrerAvis >= ENTRE_AVISOS_S) {
                avisat = true
                darrerAvis = ts
                return true
            }
            return false
        }
        if (des != 0L && ts - alt > pausa) {
            des = 0
            avisat = false
        }
        return false
    }

    companion object {
        const val PAUSA_S = 15 * 60L
        const val ENTRE_AVISOS_S = 2 * 3600L
    }
}

/**
 * L'interval de potència d'un episodi (W per sobre del fons), per aprendre un aparell que s'engega sol i no gasta
 * sempre igual: del 10 % més baix (menys un 20 %) al màxim (més un 20 %). [exces]: minut a minut des que va
 * començar; l'episodi s'acaba quan baixa del 30 % de [dp] 3 minuts seguits. Null si dura menys de 5 minuts.
 * Una bomba de calor de l'aigua en dona, per exemple, 254–784 W.
 */
fun intervalEpisodi(exces: List<Double>, dp: Double): Pair<Double, Double>? {
    val dins = mutableListOf<Double>()
    var baixos = 0
    for (e in exces) {
        if (e < 0.3 * dp) {
            if (++baixos >= 3) break
        } else {
            baixos = 0
            dins += e
        }
    }
    if (dins.size < 5) return null
    val s = dins.sorted()
    return s[s.size / 10] * 0.8 to s.last() * 1.2
}

/**
 * El compressor de la nevera, trobat sol a les dades per minut de les nits (de 0 a 6 h), quan la casa és tranquil·la:
 * la base és el mínim de l'hora que envolta cada minut; un minut és «encès» si la passa de 30 a 150 W, i els trams
 * encesos de 8 a 60 minuts són cicles. Amb 15 cicles o més d'una potència semblant (el 90 % no passa d'1,4 vegades el
 * 10 %), en torna la mediana (W); si no, null. Una nevera típica: uns 50 W, cada ~30 minuts.
 * [punts]: (s, W de la casa sense la línia de la segona pinça), minut a minut.
 */
fun potenciaCompressor(punts: List<Pair<Long, Double>>, zona: ZoneId): Double? {
    val ws = punts.map { it.second }
    val cicles = mutableListOf<Double>()
    val tram = mutableListOf<Double>()
    for ((i, p) in punts.withIndex()) {
        val nit = Instant.ofEpochSecond(p.first).atZone(zona).hour < 6
        val base = ws.subList(maxOf(0, i - 30), minOf(ws.size, i + 30)).min()
        // Un forat de més d'un minut talla el tram
        val seguit = i > 0 && p.first - punts[i - 1].first <= 60
        if (nit && seguit && p.second - base in 30.0..150.0) {
            tram += p.second - base
        } else {
            if (tram.size in 8..60) cicles += mediana(tram)
            tram.clear()
        }
    }
    if (cicles.size < 15) return null
    val s = cicles.sorted()
    if (s[s.size * 9 / 10] > 1.4 * s[s.size / 10]) return null
    return mediana(s)
}

/**
 * Quina part del temps funciona el compressor de la nevera (de 0 a 1), mirat a les nits (de 0 a 6 h), quan gairebé només
 * funciona ell: els minuts dels seus cicles (com a [potenciaCompressor]) sobre tots els minuts de nit. Amb això, el que
 * gasta la nevera en un període és la seva potència per aquesta part del temps (maqueta 13). Null si no se'n troben cicles.
 */
fun cicleCompressor(punts: List<Pair<Long, Double>>, zona: ZoneId): Double? {
    val ws = punts.map { it.second }
    var nit = 0
    var enMarxa = 0
    val tram = mutableListOf<Double>()
    for ((i, p) in punts.withIndex()) {
        val esNit = Instant.ofEpochSecond(p.first).atZone(zona).hour < 6
        if (esNit) nit++
        val base = ws.subList(maxOf(0, i - 30), minOf(ws.size, i + 30)).min()
        val seguit = i > 0 && p.first - punts[i - 1].first <= 60
        if (esNit && seguit && p.second - base in 30.0..150.0) {
            tram += p.second - base
        } else {
            if (tram.size in 8..60) enMarxa += tram.size
            tram.clear()
        }
    }
    if (nit < 6 * 60 || enMarxa == 0) return null
    return enMarxa.toDouble() / nit
}
