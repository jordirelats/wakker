package cat.jordi.wakker.dades

import kotlin.math.max
import kotlin.math.min

/**
 * Ensenyar un aparell d'inici a fi (maqueta 21): «ara l'engego» i, minuts o hores després, «l'he apagat». Un aparell que
 * varia i dura hores (un aire inverter que fa cicles de 200–450 W) no surt mai com a vegada: la base dels episodis (el 20 %
 * més baix de les 2 hores d'abans) puja fins a ell. Aquí la base és la de la casa abans d'engegar-lo, i tota l'estona és una
 * sola vegada seva, menys el que s'hi ha encès a sobre i no era seu.
 */
object Sessio {
    /** L'estona d'abans d'engegar-lo que dona la base de la casa (s). */
    const val ABANS = 30 * 60L

    /** Un minut en què funciona: passa de la base almenys això (W), o el 15 % del que fa quan va de debò. */
    const val ENCES_MIN = 30.0

    /** La base de la casa: la mediana de la mitja hora d'abans; si no n'hi ha prou, el 20 % més baix de l'estona. */
    fun base(abans: List<MinutW>, dins: List<MinutW>): Double = when {
        abans.size >= 5 -> mediana(abans.map { it.w })
        dins.isNotEmpty() -> dins.map { it.w }.sorted()[dins.size / 5]
        else -> 0.0
    }

    /**
     * Les vegades de dins de l'estona que no són de l'aparell: les que l'usuari (o un endoll) ha dit que eren d'un altre, i
     * les massa grosses per ser seves: que el 10 % dels seus minuts més alts passin de [limit] W per sobre de la [base] (per
     * defecte, el doble del que fa l'aparell i 600 W més). No la mediana de la vegada: una placa que polsa la té baixa (a
     * una casa de prova, una placa de 2,5 kW feia una mediana de 817 W a sobre d'un aire). Les dels 5 primers minuts, no: són
     * l'arrencada. [nivell]: el que fa quan funciona ([nivell]).
     */
    fun alienes(
        vegades: List<Vegada>, dins: List<MinutW>, base: Double, desDe: Long, finsA: Long, aparellId: Long, nivell: Double, limit: Double?,
    ): List<Vegada> {
        val sostre = max(limit ?: 0.0, max(2 * nivell, nivell + 600))
        val perVegades = vegades.filter { v ->
            if (v.ts < desDe + 5 * 60 || v.ts > finsA) return@filter false
            if (v.aparellId != null && v.aparellId != aparellId && (v.com == Com.ENSENYAT || v.com == Com.ENDOLL)) return@filter true
            val s = dins.filter { it.ts in v.ts..v.fi }.map { it.w - base }.sorted()
            val alt = if (s.isEmpty()) v.w else s[s.size * 9 / 10]
            alt > sostre
        }
        // I minut a minut: el que s'encén a sobre de l'aparell sovint no és una vegada a part, sinó dins de la seva (la placa de
        // 2,5 kW a sobre d'un aire que ja anava). 2 minuts seguits o més per sobre del sostre, després de l'arrencada
        val trams = mutableListOf<List<MinutW>>()
        var tram = mutableListOf<MinutW>()
        for (m in dins) {
            if (m.ts >= desDe + 5 * 60 && m.w - base > sostre && (tram.isEmpty() || m.ts - tram.last().ts <= 120)) tram += m
            else {
                if (tram.size >= 2) trams += tram
                tram = if (m.ts >= desDe + 5 * 60 && m.w - base > sostre) mutableListOf(m) else mutableListOf()
            }
        }
        if (tram.size >= 2) trams += tram
        val perMinuts = trams.filter { t -> perVegades.none { v -> v.ts <= t.last().ts && v.fi >= t.first().ts } }.map { t ->
            vegadaDe(0, t, base)
        }
        return (perVegades + perMinuts).sortedBy { it.ts }
    }

    /** Una vegada feta amb uns minuts i la base de l'estona (el que passa de la base, tot). */
    fun vegadaDe(canal: Int, minuts: List<MinutW>, base: Double): Vegada {
        val exces = minuts.map { max(it.w - base, 0.0) }
        val w = mediana(exces)
        return Vegada(
            0, canal, minuts.first().ts, minuts.last().ts, ((minuts.last().ts - minuts.first().ts) / 60 + 1).toInt(), w, exces.sum() / 60_000,
            mediana(minuts.map { (it.max - it.min) / max(w, 1.0) }), exces.take(2).max() / max(w, 1.0),
        )
    }

    /**
     * El que fa quan funciona: la mediana dels minuts que passen [ENCES_MIN] de la base. No un percentil més alt: en una estona
     * curta, el que s'encén a sobre pot ser una quarta part dels minuts (5 minuts de placa en 20 d'aire).
     */
    fun nivell(dins: List<MinutW>, base: Double): Double {
        val s = dins.map { it.w - base }.filter { it >= ENCES_MIN }
        return if (s.isEmpty()) 0.0 else mediana(s)
    }

    /**
     * La vegada de l'estona i la firma que en surt. [dins]: els minuts de l'estona (del canal), en ordre; [fora]: les estones
     * de les vegades que no són seves ([alienes]), que no compten. Null si no s'hi veu res: menys de 2 minuts que passin de
     * la base (un aparell de menys de 30 W no es veu al total).
     */
    fun calcula(canal: Int, base: Double, dins: List<MinutW>, fora: List<Pair<Long, Long>>): FirmaSessio? {
        if (dins.isEmpty()) return null
        val propis = dins.filter { m -> fora.none { m.ts in it.first..it.second } }
        if (propis.isEmpty()) return null
        val exces = propis.map { max(it.w - base, 0.0) }
        val ordenats = exces.sorted()
        val llindar = max(ENCES_MIN, 0.15 * ordenats[ordenats.size * 9 / 10])
        val ences = propis.zip(exces).filter { it.second >= llindar }
        if (ences.size < 2) return null
        val ws = ences.map { it.second }.sorted()
        val w = mediana(ws)
        // Com polsa dins del minut quan funciona (com els episodis), i com arrenca: els dos primers minuts sobre el que fa
        val variacio = mediana(ences.map { (m, e) -> ((m.max - m.min) / max(e, 1.0)).coerceIn(0.0, 2.0) })
        val arrencada = dins.take(2).maxOf { it.w - base } / w
        val pic = dins.take(3).maxOf { it.max - base }
        val vegada = Vegada(
            0, canal, dins.first().ts, dins.last().ts, ((dins.last().ts - dins.first().ts) / 60 + 1).toInt(), w,
            exces.sum() / 60_000, variacio, arrencada,
        )
        return FirmaSessio(vegada, ws[ws.size / 10], ws[min(ws.size - 1, ws.size * 9 / 10)], ences.size.toDouble() / propis.size, max(pic, 0.0))
    }

    /** Les estones que no compten, com es desen a l'etiqueta: «inici-fi,inici-fi» (buit si no n'hi ha cap). */
    fun text(fora: List<Pair<Long, Long>>) = fora.joinToString(",") { "${it.first}-${it.second}" }

    fun llegeix(text: String?): List<Pair<Long, Long>>? = text?.split(",")?.filter { it.isNotBlank() }?.mapNotNull {
        val p = it.split("-")
        if (p.size == 2) p[0].toLongOrNull()?.let { a -> p[1].toLongOrNull()?.let { b -> a to b } } else null
    }
}

/**
 * La vegada d'una estona d'inici a fi i la firma de l'aparell: entre quins W va quan funciona ([wMin], [wMax]: el percentil
 * 10 i el 90), quina part del temps funciona ([partEnces]: menys d'un 80 %, va a cicles) i el pic en arrencar ([pic], W).
 */
data class FirmaSessio(val vegada: Vegada, val wMin: Double, val wMax: Double, val partEnces: Double, val pic: Double) {
    /**
     * Varia sol: s'engega i s'atura (una estufa amb termòstat) o canvia molt de potència (un aire inverter, de 75 a 340 W
     * per sobre de la casa). La durada de l'estona no és la de cada cop.
     */
    val aCicles get() = partEnces < 0.8 || wMin < 0.4 * wMax
}

/**
 * Una estona d'inici a fi ja acabada, per revisar abans de desar-la: l'aparell, el canal (0, la resta de la casa; 1, la línia
 * de la pinça 2), la base, la potència minut a minut (amb la mitja hora d'abans, per a la gràfica), les vegades de dins que
 * no semblen seves i la firma amb totes elles fora. [aproximada]: hi falten els minuts exactes (des de fora de casa).
 */
data class EstonaSessio(
    val aparellId: Long, val canal: Int, val desDe: Long, val finsA: Long, val base: Double,
    val punts: List<Pair<Long, Double>>, val dins: List<MinutW>, val alienes: List<Vegada>, val aproximada: Boolean,
) {
    /** La firma amb les vegades de [fora] fora (l'usuari pot dir que alguna sí que era seva). */
    fun firma(fora: List<Vegada>) = Sessio.calcula(canal, base, dins, fora.map { it.ts to it.fi })
}
