package cat.jordi.wakker.dades

import androidx.annotation.StringRes
import cat.jordi.wakker.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/** Un minut d'una pinça, o de la resta de la casa: la potència mitjana, la màxima i la mínima del minut (W). */
data class MinutW(val ts: Long, val w: Double, val max: Double, val min: Double)

/**
 * Com és la potència d'un aparell quan funciona (maqueta 12). Mesurat a Guíxols (recerca/deteccio/): el rentaplats i el
 * cotxe són constants i salten de cop; la placa d'inducció polsa (el màxim i el mínim de cada minut se separen un 89 % de
 * la seva potència); la bomba de calor de l'aigua arrenca suau (els dos primers minuts no hi arriben: 0,7); una cosa de
 * 370 W que s'engega cada 3 hores fa un pic de 2–3 vegades la seva potència en arrencar, com els motors.
 */
enum class Forma(@StringRes val textId: Int) {
    CONSTANT(R.string.forma_constant),
    POLSA(R.string.forma_polsa),
    SUAU(R.string.forma_suau),
    PIC(R.string.forma_pic);

    companion object {
        /** Des d'aquí, polsa: el màxim i el mínim del minut se separen un 30 % de la potència. */
        const val POLSA_MIN = 0.3
        const val SUAU_MAX = 0.85
        const val PIC_MIN = 1.6

        fun de(variacio: Double?, arrencada: Double?): Forma? = when {
            variacio != null && variacio >= POLSA_MIN -> POLSA
            arrencada == null -> if (variacio != null) CONSTANT else null
            arrencada < SUAU_MAX -> SUAU
            arrencada > PIC_MIN -> PIC
            else -> CONSTANT
        }
    }
}

/**
 * Una estona en què una pinça (o la resta de la casa) passa clarament de la seva base: un aparell funcionant. [w]: el que
 * passa de la base (la mediana dels minuts de dins, W); [variacio]: com se separen el màxim i el mínim de cada minut,
 * sobre [w]; [arrencada]: el màxim dels dos primers minuts, sobre [w]; [exces]: minut a minut, el que passa de la base.
 */
data class Episodi(
    val ts: Long, val fi: Long, val minuts: Int, val w: Double, val kwh: Double, val variacio: Double, val arrencada: Double,
    val exces: List<Double>,
) {
    val forma get() = Forma.de(variacio, arrencada)

    /** Quant va durar (s). */
    val durada get() = fi - ts + 60

    /**
     * L'interval de potència per a un aparell que s'engega sol, com el d'una pregunta resposta ([intervalEpisodi]): sense
     * els minuts que no arriben al 30 % (un minut per sota de la base dins de l'episodi), que el farien començar a 0.
     */
    val interval: Pair<Double, Double>
        get() {
            val s = exces.filter { it >= 0.3 * w }.sorted().ifEmpty { listOf(w) }
            return s[s.size / 10] * 0.8 to s.last() * 1.2
        }
}

/** Quan passa el que es repeteix: per dir-ho a la pregunta. */
enum class QuanPassa(@StringRes val textId: Int) {
    NITS_CAP_DE_SETMANA(R.string.repetit_nits_cap_setmana),
    NITS(R.string.repetit_nits),
    CAP_DE_SETMANA(R.string.repetit_cap_setmana),
    SEMPRE(R.string.repetit_sempre),
}

/**
 * Una cosa que es repeteix sempre igual (maqueta 12): una potència semblant, una durada semblant, 6 cops o més en una
 * setmana. [cops]: quan va començar cada cop, amb la seva potència i la seva durada (minuts).
 */
data class Repetit(
    val id: Long, val w: Double, val wMin: Double, val wMax: Double, val minuts: Double, val cadaMinuts: Double, val kwh: Double,
    val variacio: Double?, val arrencada: Double?, val cops: List<Triple<Long, Double, Int>>, val estat: EstatPregunta, val aparellId: Long?,
) {
    val forma get() = Forma.de(variacio, arrencada)

    /**
     * Si sembla que s'engega sol: una persona no engega una cosa sempre igual a la nit, ni 4 cops o més cada dia. El bullidor
     * de cada matí, no.
     */
    fun sembla(zona: ZoneId): Boolean {
        val hores = cops.map { Instant.ofEpochSecond(it.first).atZone(zona) }
        val nit = hores.count { it.hour >= 23 || it.hour < 7 } >= 0.3 * hores.size
        val dies = hores.map { it.toLocalDate() }.distinct().size
        return nit || cops.size >= 4 * dies
    }

    fun quan(zona: ZoneId): QuanPassa {
        val hores = cops.map { Instant.ofEpochSecond(it.first).atZone(zona) }
        val nit = hores.count { it.hour >= 22 || it.hour < 8 } >= 0.7 * hores.size
        // El cap de setmana: de divendres a la nit a dilluns al matí
        val capDeSetmana = hores.count {
            it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY ||
                (it.dayOfWeek == DayOfWeek.FRIDAY && it.hour >= 18) || (it.dayOfWeek == DayOfWeek.MONDAY && it.hour < 8)
        } >= 0.8 * hores.size
        return when {
            nit && capDeSetmana -> QuanPassa.NITS_CAP_DE_SETMANA
            nit -> QuanPassa.NITS
            capDeSetmana -> QuanPassa.CAP_DE_SETMANA
            else -> QuanPassa.SEMPRE
        }
    }
}

/**
 * Els episodis d'una sèrie de minuts (maqueta 12; prototip: recerca/deteccio/episodis_setmana.py). La base de cada minut és
 * el 20 % més baix de les dues hores d'abans, i un episodi són [llargada] minuts o més (3, o 2 per repartir el consum:
 * l'estufa de Guíxols fa molts cicles de 2 minuts) que la passen 250 W o més. Un minut per sota no el talla; dos, o un forat
 * de dades de més de 3 minuts, sí. El que s'encén a sobre d'un episodi llarg i pla és un episodi a part ([ambElDeSobre]).
 */
object Episodis {
    const val MINIM = 250.0
    private const val FINESTRA = 120

    fun troba(minuts: List<MinutW>, llargada: Int = 3): List<Episodi> {
        if (minuts.size < 5) return emptyList()
        val ws = minuts.map { it.w }
        val bases = DoubleArray(minuts.size)
        for (i in minuts.indices) {
            val finestra = ws.subList(max(0, i - FINESTRA), i + 1).sorted()
            bases[i] = finestra[finestra.size / 5]
        }
        val grups = mutableListOf<Pair<List<Int>, Double>>()
        var tram = mutableListOf<Int>()
        var base = 0.0
        var forat = 0
        fun tanca() {
            if (tram.size >= llargada) grups += tram.toList() to base
            tram = mutableListOf()
            forat = 0
        }
        for (i in minuts.indices) {
            if (tram.isNotEmpty() && minuts[i].ts - minuts[tram.last()].ts > 3 * 60) tanca()
            // Dins d'un episodi, la base és la de quan va començar
            val b = if (tram.isEmpty()) bases[i] else base
            if (ws[i] - b >= MINIM) {
                if (tram.isEmpty()) base = b
                tram += i
                forat = 0
            } else if (tram.isNotEmpty() && ++forat > 1) {
                tanca()
            }
        }
        tanca()
        return grups.flatMap { (g, b) -> ambElDeSobre(minuts, g.first(), g.last(), b, llargada) }.sortedBy { it.ts }
    }

    /**
     * Un episodi llarg i pla amb el que s'hi encén a sobre (maqueta 16): la placa mentre es carrega el cotxe. Dins de
     * l'episodi, els trams de [llargada] minuts o més que passen 250 W del seu nivell (la mediana) són episodis a part, amb
     * aquell nivell com a base; i el llarg es queda, en aquells minuts, el seu nivell. Només si el llarg és pla (la majoria dels
     * minuts de fora dels trams, a menys d'un 20 % o 150 W del nivell) i els trams en són menys de la meitat: la placa sola
     * polsa, i els seus minuts alts no són una altra cosa.
     */
    private fun ambElDeSobre(m: List<MinutW>, a: Int, b: Int, base: Double, llargada: Int): List<Episodi> {
        val ep = descriu(m, a, b, base) ?: return emptyList()
        if (b - a + 1 < 10 || ep.forma == Forma.POLSA) return listOf(ep)
        val nivell = base + ep.w
        val trams = mutableListOf<IntRange>()
        var ini = -1
        var fi = -1
        var forat = 0
        fun tanca() {
            if (ini >= 0 && fi - ini + 1 >= llargada) trams += ini..fi
            ini = -1
            forat = 0
        }
        // Els minuts de les puntes són a mitges: un tram ha de ser de dins
        for (i in a + 1 until b) {
            if (m[i].ts - m[i - 1].ts > 3 * 60) tanca()
            if (m[i].w - nivell >= MINIM) {
                if (ini < 0) ini = i
                fi = i
                forat = 0
            } else if (ini >= 0 && ++forat > 1) {
                tanca()
            }
        }
        tanca()
        if (trams.isEmpty()) return listOf(ep)
        val dins = trams.sumOf { it.count() }
        if (dins * 2 > b - a + 1) return listOf(ep)
        val fora = (a + 1 until b).filter { i -> trams.none { i in it } }
        val plans = fora.count { i -> abs(m[i].w - nivell) <= max(0.2 * ep.w, 150.0) }
        if (plans < 0.7 * fora.size) return listOf(ep)
        val sobre = trams.mapNotNull { t -> descriu(m, t.first, t.last, nivell) }
        if (sobre.isEmpty()) return listOf(ep)
        // El llarg, sense el que hi havia a sobre
        val exces = ep.exces.mapIndexed { j, x -> if (trams.any { (a + j) in it }) minOf(x, ep.w) else x }
        return listOf(ep.copy(exces = exces, kwh = exces.sum() / 60_000)) + sobre
    }

    private fun descriu(m: List<MinutW>, a: Int, b: Int, base: Double): Episodi? {
        val dins = m.subList(a, b + 1)
        // Els minuts de dins: el primer i l'últim són a mitges (s'engega o s'apaga a mig minut). D'un de 2 minuts, el més alt
        val interiors = if (b - a >= 2) m.subList(a + 1, b) else listOf(dins.maxBy { it.w })
        val w = mediana(interiors.map { it.w }) - base
        if (w <= 0) return null
        val abans = m.subList(max(0, a - 10), a)
        val varBase = if (abans.isEmpty()) 0.0 else mediana(abans.map { it.max - it.min })
        val variacio = max(mediana(interiors.map { it.max - it.min }) - varBase, 0.0) / w
        val arrencada = (dins.take(2).maxOf { it.max } - base) / w
        val exces = dins.map { max(it.w - base, 0.0) }
        return Episodi(m[a].ts, m[b].ts, b - a + 1, w, exces.sum() / 60_000, variacio, arrencada, exces)
    }

    /**
     * Els que es repeteixen: potència semblant (±25 %) i durada semblant (±2 minuts o ±30 %), [minim] cops o més. A
     * Guíxols, la setmana del 19 al 26/10: 330–460 W durant 6 minuts cada 3 hores (10 cops) i 500–830 W durant 3 minuts
     * cada quart d'hora les nits del cap de setmana (65 cops).
     */
    fun repetits(episodis: List<Episodi>, minim: Int = 6): List<List<Episodi>> {
        val grups = mutableListOf<MutableList<Episodi>>()
        for (e in episodis.sortedBy { it.w }) {
            val g = grups.firstOrNull { g ->
                val w = mediana(g.map { it.w })
                val d = mediana(g.map { it.minuts.toDouble() })
                abs(e.w - w) <= 0.25 * w && abs(e.minuts - d) <= max(2.0, 0.3 * d)
            }
            if (g != null) g += e else grups += mutableListOf(e)
        }
        return grups.filter { it.size >= minim }.map { g -> g.sortedBy { it.ts } }
    }

    /** Un grup d'episodis que es repeteixen, com a [Repetit] per preguntar-ne. */
    fun repetit(g: List<Episodi>): Repetit {
        val ts = g.map { it.ts }
        val cada = if (ts.size > 1) mediana(ts.zipWithNext { a, b -> (b - a) / 60.0 }) else 0.0
        return Repetit(
            0, mediana(g.map { it.w }), g.minOf { it.w }, g.maxOf { it.w }, mediana(g.map { it.minuts.toDouble() }), cada, g.sumOf { it.kwh },
            mediana(g.map { it.variacio }), mediana(g.map { it.arrencada }), g.map { Triple(it.ts, it.w, it.minuts) }, EstatPregunta.PENDENT, null,
        )
    }

    /** Si un grup trobat ara és el mateix que un que ja s'havia preguntat. */
    fun mateix(r: Repetit, w: Double, minuts: Double) = abs(w - r.w) <= 0.25 * r.w && abs(minuts - r.minuts) <= max(2.0, 0.3 * r.minuts)
}

/**
 * A quin aparell de la línia de la pinça 2 toca un episodi (maqueta 12): el que s'hi assembla ([distancia]), si s'hi
 * assembla prou (a menys de ×4) i clarament més que el segon (×1,4). Si no, no se sap. Al principi, sense mostres, es
 * compara amb la potència típica: a Guíxols, 490 W s'assemblen més a una bomba de calor (1.500 W) que al cotxe (3.500 W).
 */
fun triaLinia(ep: Episodi, aparells: List<Aparell>, refs: Map<Long, Referencia>): Aparell? {
    val d = aparells.map { it to distancia(ep.w, ep.minuts.toDouble(), ep.forma, it, refs[it.id]) }.sortedBy { it.second }
    val (millor, dm) = d.firstOrNull() ?: return null
    if (dm > ln(4.0)) return null
    if (d.size > 1 && d[1].second - dm < ln(1.4)) return null
    return millor
}
