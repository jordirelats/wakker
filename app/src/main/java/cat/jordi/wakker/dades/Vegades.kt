package cat.jordi.wakker.dades

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * El consum, repartit per vegades (maqueta 13): cada vegada que una cosa s'encén i s'apaga (un [Episodi]) és d'un sol
 * aparell, o de cap. Abans es repartia minut a minut combinant referències, i barrejava coses: després d'ensenyar el
 * rentaplats amb una estona passada (0,36 kWh), el rentaplats es quedava 1,9 kWh del mes i l'estufa passava de 5,7 a
 * 7,8 kWh sense tocar-la. Prototip: recerca/deteccio/repartiment_episodis.py.
 */

/** Com se sap de qui és una vegada. */
enum class Com {
    /** L'usuari ho ha dit: una estona passada, una pregunta, un moment punta, el que es repeteix, «L'he encès». */
    ENSENYAT,

    /** S'assembla a la firma d'un aparell, i només a la d'un. */
    RECONEGUT,
}

/**
 * El que l'usuari ha dit d'una estona d'un canal (0, la resta de la casa; 1, la línia de la pinça 2): que era l'aparell
 * [aparellId] o, amb [no], que no ho era («No era ell»). Un instant ([desDe] = [finsA]) és quan es va encendre: val per a
 * la vegada que comença a menys de 3 minuts. Una estona val per a les vegades que cobreix almenys a la meitat.
 */
data class Etiqueta(val canal: Int, val desDe: Long, val finsA: Long, val aparellId: Long, val no: Boolean = false, val id: Long = 0)

/** Una vegada desada: un episodi d'un canal, amb l'aparell (si se sap) i com se sap. */
data class Vegada(
    val id: Long, val canal: Int, val ts: Long, val fi: Long, val minuts: Int, val w: Double, val kwh: Double,
    val variacio: Double, val arrencada: Double, val aparellId: Long? = null, val com: Com? = null,
) {
    val forma get() = Forma.de(variacio, arrencada)

    companion object {
        fun de(canal: Int, e: Episodi) = Vegada(0, canal, e.ts, e.fi, e.minuts, e.w, e.kwh, e.variacio, e.arrencada)
    }
}

/**
 * Quant val una etiqueta per a una vegada (0: no hi té res a veure). Un instant, si la vegada comença a menys de 3 minuts
 * (una vegada llarga no s'emporta el que algú ha respost d'una cosa que s'hi va encendre a sobre). Una estona, el que se
 * superposen (s), si cobreix almenys la meitat de la vegada i la vegada en fa almenys una quarta part: la placa que s'encén
 * a sobre de la càrrega del cotxe (maqueta 16) no és del cotxe encara que l'estona de la càrrega la cobreixi sencera.
 */
internal fun encavalca(v: Vegada, e: Etiqueta): Long {
    val fi = v.fi + 60
    if (e.desDe == e.finsA) return if (kotlin.math.abs(e.desDe - v.ts) <= 180) 1 else 0
    val comu = max(0, min(fi, e.finsA + 1) - max(v.ts, e.desDe))
    return if (comu * 2 >= fi - v.ts && (fi - v.ts) * 4 >= e.finsA + 1 - e.desDe) comu else 0
}

/**
 * Les etiquetes per canal, ordenades per l'inici, per trobar de pressa les d'una vegada (n'hi pot haver milers, i vegades,
 * desenes de milers).
 */
class IndexEtiquetes(etiquetes: List<Etiqueta>) {
    private val perCanal = etiquetes.groupBy { it.canal }.mapValues { (_, l) -> l.sortedBy { it.desDe } }
    private val llargada = etiquetes.maxOfOrNull { it.finsA - it.desDe } ?: 0

    /** Les que poden tocar una vegada: les que comencen des de [llargada] + 3 minuts abans fins que s'acaba. */
    fun de(v: Vegada): List<Etiqueta> {
        val l = perCanal[v.canal] ?: return emptyList()
        val desDe = v.ts - 180 - llargada
        var lo = 0
        var hi = l.size
        while (lo < hi) {
            val mig = (lo + hi) / 2
            if (l[mig].desDe < desDe) lo = mig + 1 else hi = mig
        }
        val sortida = mutableListOf<Etiqueta>()
        var i = lo
        while (i < l.size && l[i].desDe <= v.fi + 60) sortida += l[i++]
        return sortida
    }
}

/**
 * De qui és una vegada. Primer, el que ha dit l'usuari (l'etiqueta que s'hi encavalca més), tret dels aparells de
 * [exclosos] (els que ara estan sempre encesos: van dins del fons). Si no, l'aparell de [candidats]
 * amb una referència apresa que hi encaixa: la potència (dins de l'interval, amb un 15 % de marge, o a menys de ×1,3 del
 * valor), la durada (si se sap, entre un terç i el triple) i que no en polsi un i l'altre no. Si n'encaixa més d'un, el que
 * s'hi assembla clarament més ([distancia], ×1,4). Si no n'encaixa cap, i una estona ensenyada la cobreix sencera (una cosa
 * curta dins del cicle d'un aparell), d'aquell aparell; si no, de ningú. Els aparells que l'usuari ha dit que no eren, no.
 */
fun assigna(
    v: Vegada, candidats: List<Aparell>, refs: Map<Long, Referencia>, etiquetes: List<Etiqueta>, exclosos: Set<Long> = emptySet(),
): Pair<Long?, Com?> {
    val seves = etiquetes.filter { it.canal == v.canal && it.aparellId !in exclosos }.map { it to encavalca(v, it) }.filter { it.second > 0 }
    seves.filter { !it.first.no }.maxByOrNull { it.second }?.let { return it.first.aparellId to Com.ENSENYAT }
    val no = seves.filter { it.first.no }.map { it.first.aparellId }.toSet()
    val encaixen = candidats.filter { a ->
        val r = refs[a.id] ?: return@filter false
        if (a.id in no) return@filter false
        val potencia = if (r.wMin != null) v.w in r.min * 0.85..r.max * 1.15 else v.w in r.w / 1.3..r.w * 1.3
        val durada = r.duradaMin?.let { d -> v.minuts.toDouble() in (d / 3 - 1)..(d * 3 + 1) } ?: true
        val f = v.forma
        val forma = r.forma == null || f == null || (f == Forma.POLSA) == (r.forma == Forma.POLSA)
        potencia && durada && forma
    }
    // Una cosa curta a sobre d'una estona ensenyada (l'escalfament dins del cicle de la rentadora): si no s'assembla a cap
    // altre aparell, és de l'estona
    val cobreix = {
        etiquetes.filter { e ->
            e.canal == v.canal && !e.no && e.aparellId !in exclosos && e.aparellId !in no && e.desDe != e.finsA &&
                e.desDe <= v.ts && e.finsA + 1 >= v.fi + 60
        }.maxByOrNull { it.finsA - it.desDe }?.let { it.aparellId to Com.ENSENYAT } ?: (null to null)
    }
    if (encaixen.isEmpty()) return cobreix()
    if (encaixen.size == 1) return encaixen[0].id to Com.RECONEGUT
    val d = encaixen.map { it to distancia(v.w, v.minuts.toDouble(), v.forma, it, refs[it.id]) }.sortedBy { it.second }
    return if (d[1].second - d[0].second >= ln(1.4)) d[0].first.id to Com.RECONEGUT else cobreix()
}

/** Un aparell al rànquing d'un període: quant ha gastat, quantes vegades i quantes n'ha ensenyat l'usuari. */
data class FilaRanquing(
    val aparell: Aparell?, val nom: String, val kwh: Double, val euros: Double, val cops: Int, val ensenyats: Int,
    /** És a la línia de la pinça 2 (o és la línia mateixa, si no s'hi ha dit cap aparell). */
    val linia: Boolean,
    /** La nevera: el compressor trobat sol a les dades, sense vegades. */
    val compressorW: Double? = null,
    /** La línia de la pinça 2 sencera, sense cap aparell que s'hi hagi dit (o l'aparell que es diu com ella). */
    val esLinia: Boolean = false,
)

/**
 * Tot el consum d'un període, sense barrejar res (maqueta 13): els aparells de més a menys, i la resta a part. La suma de
 * tot és el total.
 */
data class Ranquing(
    val aparells: List<FilaRanquing>,
    val total: Double,
    /** El que la casa gasta sempre (el fons, també el dels aparells sempre encesos). */
    val sempre: Double, val sempreEuros: Double,
    /** El que passa del fons però no és cap vegada de 250 W o més: llums, tele, carregadors. */
    val petits: Double, val petitsEuros: Double,
    /** Vegades que l'app no sap de qui són. */
    val senseIdentificar: Double, val senseIdentificarEuros: Double,
    /** A la línia de la pinça 2, el que no és cap dels seus aparells. */
    val liniaAltres: Double, val liniaAltresEuros: Double,
    /** Des de fora de casa o del núvol per hores: sense el detall de cada minut, no se sap de qui és. */
    val senseDetall: Double, val senseDetallEuros: Double,
) {
    val delsAparells get() = aparells.sumOf { it.kwh }
}

/** Què és cada cosa encesa ara (maqueta 15): amb el mateix criteri que el rànquing. */
enum class TipusEnces {
    /** Una vegada d'un aparell que encara dura; o una cosa que s'acaba d'encendre i, per la potència, només pot ser ell. */
    APARELL,

    /** Una vegada que encara dura i que l'app no sap de qui és. */
    SENSE_IDENTIFICAR,

    /** Una cosa de 250 W o més que s'acaba d'encendre (encara no és una vegada) i que no se sap què és. */
    NOU,

    /** El compressor de la nevera, trobat sol a les dades. */
    NEVERA,

    /** El que queda per sota de 250 W: a la resta de la casa, els petits consums; a la línia, «altres». */
    PETITS,
}

/** Una cosa encesa ara en un canal: quants W, de quin aparell, des de quan i quants kWh porta (si és una vegada, [vegada]). */
data class EncesAra(
    val tipus: TipusEnces, val canal: Int, val w: Double, val aparellId: Long? = null, val desDe: Long? = null,
    val kwh: Double? = null, val com: Com? = null, val vegada: Vegada? = null,
)

/**
 * Què hi ha encès ara en un canal (maqueta 15). Primer, les vegades que encara duraven a l'últim minut del Shelly
 * ([obertes]), de més a menys potència, mentre l'excés d'ara ([exces]: W per sobre del fons, o de la línia) encara les hi
 * cap almenys a la meitat (si no, s'han apagat després). El que queda: si passa de 250 W, una cosa que s'acaba d'encendre
 * (si per la potència només hi encaixa un dels [candidats], és ell); si no, la nevera ([neveraW], només a la resta de la
 * casa) i els petits consums. Dona el mateix nom que el rànquing: les vegades són les mateixes. Sense [perPotencia] (els
 * moments punta, que ja han passat), cap nom per la potència sola: el d'una vegada és el del rànquing.
 */
fun encesDelCanal(
    ara: Long, canal: Int, exces: Double, obertes: List<Vegada>, candidats: List<Aparell>, refs: Map<Long, Referencia>,
    neveraW: Double? = null, perPotencia: Boolean = true,
): List<EncesAra> {
    val llista = mutableListOf<EncesAra>()
    var queda = max(exces, 0.0)
    // El que s'acaba d'encendre encara no té durada ni forma: qui s'hi assembla per la potència
    val nomesPotencia = refs.mapValues { (_, r) -> r.copy(duradaMin = null, variacio = null, arrencada = null) }
    fun sembla(w: Double) =
        if (perPotencia) assigna(Vegada(0, canal, ara, ara, 1, w, 0.0, 0.0, 1.0), candidats, nomesPotencia, emptyList()).first else null
    for (v in obertes.filter { it.canal == canal }.sortedByDescending { it.w }) {
        if (queda < 0.5 * v.w) continue
        val w = min(v.w, queda)
        queda -= w
        // El que porta: el de la vegada fins a l'últim minut, i des d'aleshores a la seva potència
        val kwh = v.kwh + v.w * max(0L, ara - (v.fi + 60)) / 3_600_000.0
        // Una vegada sense nom de pocs minuts encara pot ser d'un aparell que dura més: per la potència, si encara hi cap la
        // seva durada. Després, el que diu el rànquing (el cotxe no és el rentaplats perquè fa la mateixa potència)
        val semblant = if (v.aparellId == null && v.minuts <= 5) sembla(v.w)?.takeIf { id ->
            val d = refs[id]?.duradaMin
            d == null || v.minuts <= d * 3 + 1
        } else null
        val tipus = if (v.aparellId != null || semblant != null) TipusEnces.APARELL else TipusEnces.SENSE_IDENTIFICAR
        llista += EncesAra(tipus, canal, w, v.aparellId ?: semblant, v.ts, kwh, v.com, v)
    }
    if (!perPotencia && queda >= Episodis.MINIM && llista.isNotEmpty()) {
        // En un moment punta, les vegades que hi passaven anaven per sobre de la seva potència de sempre (la placa polsa): el
        // que sobra és seu, de les que polsen si n'hi ha; si no, de totes, segons la potència. Si s'hagués encès una cosa nova
        // en aquell minut, també seria una vegada
        val polsen = llista.filter { it.vegada?.forma == Forma.POLSA }.ifEmpty { llista }
        val suma = polsen.sumOf { it.w }.coerceAtLeast(1.0)
        val extra = queda
        llista.replaceAll { e -> if (e in polsen) e.copy(w = e.w + extra * e.w / suma) else e }
        queda = 0.0
    }
    if (queda >= Episodis.MINIM) {
        // Encara no és una vegada: només se'n sap la potència
        val a = sembla(queda)
        llista += if (a != null) EncesAra(TipusEnces.APARELL, canal, queda, a) else EncesAra(TipusEnces.NOU, canal, queda)
        queda = 0.0
    }
    if (neveraW != null && neveraW > 0 && queda >= 0.6 * neveraW) {
        val w = min(queda, neveraW)
        llista += EncesAra(TipusEnces.NEVERA, canal, w)
        queda -= w
    }
    if (queda >= 1) llista += EncesAra(TipusEnces.PETITS, canal, queda)
    return llista
}
