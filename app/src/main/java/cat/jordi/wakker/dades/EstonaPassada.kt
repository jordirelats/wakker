package cat.jordi.wakker.dades

import kotlin.math.max

/**
 * Un tros d'una estona passada en què la casa gastava clarament més que la base: un aparell encès. [puja] i [baixa]: el
 * salt en encendre's i en apagar-se (W), com quan s'ensenya en directe; [kwh]: l'energia per sobre de la base;
 * [exces]: minut a minut, el que passava de la base (W).
 */
data class TramPassat(
    val ts: Long, val fi: Long, val minuts: Int, val puja: Double, val baixa: Double, val kwh: Double, val exces: List<Double>,
) {
    /** El que passava de la base de mitjana (la mediana, W): és el que s'ensenya, i amb què es comparen els trams. */
    val w get() = mediana(exces)

    /**
     * Les mostres del tram: el salt en encendre's i en apagar-se, com en directe. Si un salt no s'assembla al tram
     * (±30 %), és que tocava a una altra cosa (començava just quan n'acabava una altra): es fa servir [w].
     */
    val mostres get() = listOf(puja, baixa).map { if (kotlin.math.abs(it - w) <= 0.3 * w) it else w }
}

/**
 * Ensenyar un aparell amb una estona passada (maqueta 8): «el rentaplats va funcionar diumenge de 17 a 21 h». L'app hi
 * busca els trams i l'usuari marca els que eren l'aparell. Amb un rentaplats de veritat: dos
 * trams de 2,2 kW (l'escalfament) i un de 0,3 kW.
 */
object EstonaPassada {
    /** El que ha de passar de la base un minut perquè compti (W). */
    const val MINIM = 250.0
    private const val LLARGADA = 3

    /**
     * Els trams d'una estona: [punts] (s, W de la casa sense la línia), minut a minut. La base és el 20 % més baix de
     * l'estona (la casa amb la nevera); un tram són 3 minuts o més que la passen 250 W o més. Un minut per sota no el
     * talla; un forat de dades de més de 2 minuts, sí.
     */
    fun trams(punts: List<Pair<Long, Double>>): List<TramPassat> {
        if (punts.size < 5) return emptyList()
        val ws = punts.map { it.second }
        val base = ws.sorted()[ws.size / 5]
        val grups = mutableListOf<List<Int>>()
        var tram = mutableListOf<Int>()
        var forat = 0
        fun tanca() {
            if (tram.size >= LLARGADA) grups += tram
            tram = mutableListOf()
            forat = 0
        }
        for ((i, p) in punts.withIndex()) {
            if (tram.isNotEmpty() && p.first - punts[tram.last()].first > 3 * 60) tanca()
            if (p.second - base >= MINIM) {
                tram += i
                forat = 0
            } else if (tram.isNotEmpty() && ++forat > 1) {
                tanca()
            }
        }
        tanca()
        return grups.map { g ->
            val a = g.first()
            val b = g.last()
            val abans = mediana(ws.subList(maxOf(0, a - 3), a).ifEmpty { listOf(base) })
            val despres = mediana(ws.subList(minOf(ws.size, b + 1), minOf(ws.size, b + 4)).ifEmpty { listOf(base) })
            // Sense el primer i l'últim minut, que poden ser a mitges
            val ple = if (b - a >= 3) ws.subList(a + 1, b) else ws.subList(a, b + 1)
            val exces = ws.subList(a, b + 1).map { max(it - base, 0.0) }
            TramPassat(
                punts[a].first, punts[b].first, b - a + 1,
                mediana(ple.take(3)) - abans, mediana(ple.takeLast(3)) - despres, exces.sum() / 60_000, exces,
            )
        }
    }

    /** Els que es proposen: els semblants al més gran (±30 %). Els més petits potser eren una altra cosa. */
    fun proposats(trams: List<TramPassat>): Set<Int> {
        val gran = trams.maxOfOrNull { it.w } ?: return emptySet()
        return trams.indices.filter { kotlin.math.abs(trams[it].w - gran) <= 0.3 * gran }.toSet()
    }
}
