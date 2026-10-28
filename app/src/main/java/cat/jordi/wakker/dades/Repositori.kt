package cat.jordi.wakker.dades

import android.content.Context
import androidx.annotation.StringRes
import cat.jordi.wakker.R
import cat.jordi.wakker.shelly.Lectura
import cat.jordi.wakker.shelly.Origen
import cat.jordi.wakker.shelly.ShellyLocal
import cat.jordi.wakker.shelly.ShellyNuvol
import cat.jordi.wakker.shelly.normalitzaMac
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Periode(@StringRes val nomId: Int) {
    DIA(R.string.periode_dia), SETMANA(R.string.periode_setmana), MES(R.string.periode_mes), ANY(R.string.periode_any)
}

/** Una estona passada: la potència minut a minut (amb un marge a cada costat) i els trams que hi ha. */
data class Estona(val desDe: Long, val finsA: Long, val punts: List<Punt>, val trams: List<TramPassat>, val aproximada: Boolean)

/** Una barra de l'historial: una hora o un dia. */
/**
 * Una barra de l'historial: una hora, un dia o un mes. [perAparell]: els kWh de cada aparell dins de la barra (per id), de les
 * seves vegades, repartides pel temps que hi cauen (maqueta 18: les barres amb els colors del rànquing).
 */
data class Barra(val etiqueta: String, val hora: Hora, val perAparell: Map<Long, Double> = emptyMap())

/** Els euros d'un període: cada kWh al preu de la seva hora, i els costos fixos dels dies del període a part. */
data class Euros(val sempre: Double, val linia: Double, val desconegut: Double, val fixos: Double) {
    val consum get() = sempre + linia + desconegut
    val total get() = consum + fixos
}

/**
 * El consum d'un període (kWh), repartit. [fonsW] és el consum que no s'apaga mai. [aproximat]: una part s'ha
 * calculat des de fora i es corregirà a casa.
 */
data class Resum(
    val barres: List<Barra>, val sempre: Double, val linia: Double, val desconegut: Double, val fonsW: Double?, val euros: Euros,
    val aproximat: Boolean = false,
    /** El període de calendari que es mira (maqueta 10): [primerDia] i, sense incloure'l, [fiDia]. */
    val primerDia: LocalDate? = null, val fiDia: LocalDate? = null,
    /** Si és el període d'ara (si no, l'Historial el pinta de color sèpia). */
    val actual: Boolean = true,
    /** El total del període d'abans, per comparar; null si l'app encara no en tenia totes les dades. */
    val anteriorKwh: Double? = null,
    /** El primer dia amb dades de l'app; si el període és anterior, no en té cap. */
    val primerDiaDades: LocalDate? = null,
    /** Tot el consum del període, per aparells i la resta a part (maqueta 13). */
    val ranquing: Ranquing? = null,
) {
    val ambDades get() = primerDiaDades != null && (fiDia == null || fiDia.isAfter(primerDiaDades))

    val total get() = sempre + linia + desconegut

    /** El que ha pagat de mitjana cada kWh de la casa, sense els fixos. */
    val preuMitja get() = if (total > 0.05) euros.consum / total else null
}

/** El consum d'avui (kWh i euros sense els fixos). [aproximat]: una part s'ha calculat des de fora. */
data class Avui(val kwh: Double, val euros: Double, val aproximat: Boolean)

/** Una hora amb el seu preu (€/kWh, amb impostos; null si és dinàmic i encara no se sap) i la seva franja. */
data class HoraPreu(val ts: Long, val hora: Int, val preu: Double?, val franja: Franja)

data class InfoPreu(
    val tarifa: Tarifa,
    val ara: HoraPreu,
    /** L'hora en què s'acaba la franja d'ara (null si el preu no va per franges). */
    val canviaA: Int?,
    val avui: List<HoraPreu>,
    /** Les 24 hores a partir d'ara, per trobar la més barata. */
    val properes: List<HoraPreu>,
)

/**
 * La fitxa d'un aparell (maqueta 13): el que ha gastat aquest mes, quantes vegades, les últimes, i (maqueta 17) el que
 * l'usuari li ha ensenyat, per poder-ho treure.
 */
data class Fitxa(val aparellId: Long, val kwhMes: Double, val copsMes: Int, val ultimes: List<Vegada>, val ensenyats: List<Ensenyat> = emptyList())

/** D'on ve una cosa ensenyada: una estona passada, en directe, una resposta (a «Què era?», a l'avís, a un pic) o el que es repeteix. */
enum class OrigenEnsenyat { ESTONA, DIRECTE, VEGADA, PREGUNTA, PIC, REPETIT, RESPOSTA }

/** Una cosa que l'usuari ha ensenyat d'un aparell (maqueta 17): les seves mostres i el que en va dir (les etiquetes). */
data class Ensenyat(val origen: OrigenEnsenyat, val desDe: Long, val finsA: Long, val mostres: List<Mostra>, val etiquetes: List<Etiqueta>)

/**
 * El que ha canviat una resposta o un entrenament, per desfer-ho (maqueta 17): les etiquetes i les mostres noves (es
 * treuen), les que s'han tret (es tornen a posar), la pregunta o el que es repeteix (tornen a ser pendents), com era
 * l'aparell abans, l'aparell que s'ha creat i la vegada que s'havia deixat de preguntar.
 */
data class Desfer(
    val etiquetes: List<Long> = emptyList(), val mostres: List<Long> = emptyList(),
    val tornaEtiquetes: List<Etiqueta> = emptyList(), val tornaMostres: List<Mostra> = emptyList(),
    val pregunta: Long? = null, val repetit: Long? = null, val aparellAbans: Aparell? = null, val aparellNou: Long? = null,
    val ignorada: Long? = null,
)

/**
 * Les vegades que encara duraven a l'últim minut del Shelly (maqueta 15), si aquest minut és de fa poc ([fresques]: a casa,
 * amb les dades al dia); i la nevera que s'engega sola, amb el seu compressor.
 */
data class Obertes(val fresques: Boolean, val vegades: List<Vegada>, val nevera: Aparell?, val neveraW: Double?)

/**
 * Una vegada grossa que encara no té nom (maqueta 15): els euros, els aparells que s'hi poden assemblar (del més semblant al
 * que menys) i la potència minut a minut de la resta de la casa, amb un quart d'hora a cada costat, per a la gràfica.
 */
data class VegadaSenseNom(val vegada: Vegada, val euros: Double, val candidats: List<Aparell>, val punts: List<Pair<Long, Double>>)

/** El que més gasta i encara no té nom: les vegades més grosses dels últims 7 dies, i quant és tot el que no en té (kWh, part). */
data class SenseNom(val vegades: List<VegadaSenseNom>, val kwh: Double, val part: Double)

/** El resum d'una setmana (maqueta 15), de dilluns a diumenge, i quantes coses que es repeteixen encara no tenen nom. */
data class ResumSetmana(val dilluns: LocalDate, val resum: Resum, val repetits: Int)

/** Si canvia com es troben les vegades, s'han de tornar a buscar totes (vegeu [Config.versioVegades]). */
private const val VERSIO_VEGADES = 2

/** Les dades de fa més de 35 dies es llegeixen del resum per hores, i el detall de cada minut s'esborra quan fa 400 dies. */
private const val DIES_SENSE_RESUM = 35L
private const val DIES_AMB_MINUTS = 400L

/** Què costaria la línia de la segona pinça si gastés a les hores barates. */
data class ConsellLinia(val estalviMes: Double, val percentCar: Int)

class Repositori(val config: Config, private val bd: BaseDades) {
    init {
        bd.resumFins = config.resumFins
    }

    private val zona get() = ZoneId.systemDefault()
    private val sincronitzant = Mutex()
    /** Quan s'ha provat per últim cop de baixar els preus de cada dia, per país (un canvi de país no ha d'esperar). */
    private val intentsPreus = java.util.concurrent.ConcurrentHashMap<Pair<Pais, LocalDate>, Long>()

    private fun ara() = System.currentTimeMillis() / 1000

    /**
     * La lectura del moment: primer el Shelly de casa; si no hi arriba, el núvol. De passada, en desa el
     * comptador d'energia ([desaComptador]).
     */
    suspend fun lectura(): Lectura? = withContext(Dispatchers.IO) {
        val local = if (config.host.isNotBlank()) runCatching { ShellyLocal(config.host).lectura() }.getOrNull() else null
        // Si a l'adreça de casa hi respon un altre Shelly, la seva lectura no és de casa: es va al núvol (1.1.3)
        val l = local?.takeIf { esElDeCasa(it.mac) }
            ?: if (config.teNuvol) {
                runCatching { ShellyNuvol(config.servidorNuvol, config.clauNuvol, config.idDispositiu).lectura() }.getOrNull()
            } else null
        if (l != null) runCatching { desaComptador(l) }
        l
    }

    /**
     * La MAC del Shelly que respon a l'adreça de casa quan no és el de casa (1.1.3): el router ha donat l'adreça a un altre
     * aparell. Null si el que respon és el bo, o si encara no se sap.
     */
    @Volatile var altreShelly: String? = null
        private set

    /**
     * Si el Shelly que ha respost (la seva [mac]) és el de casa. La primera vegada, la MAC s'aprèn (qui ja tenia l'app
     * configurada abans de la 1.1.3); un firmware que no la digués no es pot comprovar, i es dona per bo.
     */
    private fun esElDeCasa(mac: String?): Boolean {
        val m = mac?.let(::normalitzaMac)?.takeIf { it.isNotBlank() } ?: return true
        val esperada = config.macShelly
        if (esperada.isBlank()) config.macShelly = m
        val bo = esperada.isBlank() || m == esperada
        altreShelly = if (bo) null else m
        return bo
    }

    /** En desar la Connexió: el Shelly pot ser un altre, i el que es va trobar abans ja no compta. */
    fun shellyCanviat() {
        altreShelly = null
    }

    /**
     * Des de fora de casa, el núvol només dona els comptadors d'energia, que avancen cada minut. L'energia
     * entre dues lectures es reparteix a parts iguals pels minuts d'entremig, que queden com a aproximats fins
     * que el mòbil torna a casa i es baixen els exactes del Shelly. Amb una lectura de casa només es desa el
     * comptador, perquè els minuts exactes ja vindran amb la sincronització. D'un en un: l'app i el treball de
     * fons poden llegir alhora.
     */
    private val bloqueigComptador = Any()

    /** Amb el seu bloqueig (1.1.3): només s'ha d'excloure a si mateix, i les lectures en directe no han d'esperar res més. */
    private fun desaComptador(l: Lectura) = synchronized(bloqueigComptador) { desaComptadorAra(l) }

    private fun desaComptadorAra(l: Lectura) {
        val wh = l.whTotal ?: return
        val minut = l.ts / 60 * 60
        val abans = config.comptador
        if (abans != null && minut <= abans.ts) return
        config.comptador = Comptador(minut, wh, l.whLinia)
        if (abans == null || l.origen == Origen.LOCAL) return
        val minuts = ((minut - abans.ts) / 60).toInt()
        // Més d'una setmana sense cap lectura: no es reparteix, i es torna a començar
        if (minuts > 7 * 24 * 60) return
        fun reparteix(canal: Int, ara: Double?, aleshores: Double?) {
            // Si el comptador ha anat enrere, s'ha posat a zero: es torna a començar
            if (ara == null || aleshores == null || ara < aleshores) return
            val perMinut = (ara - aleshores) / minuts
            bd.insereixAproximats(canal, List(minuts) { i -> Minut(abans.ts + i * 60L, perMinut, perMinut * 60, perMinut * 60) })
        }
        reparteix(0, wh, abans.wh)
        if (config.teLinia) reparteix(1, l.whLinia, abans.whLinia)
    }

    /**
     * Baixa del Shelly els minuts que falten (només a casa), també els que ara són aproximats. Retorna quants n'ha
     * afegit. Llança una excepció si el Shelly no respon. Sense l'adreça del Shelly (només núvol) no fa res.
     */
    suspend fun sincronitza(): Int = withContext(Dispatchers.IO) {
        if (config.host.isBlank()) return@withContext 0
        sincronitzant.withLock {
            val shelly = ShellyLocal(config.host)
            // Abans de baixar cap minut, que sigui el Shelly de casa: els d'un altre aparell es barrejarien amb els de casa (1.1.3)
            if (!esElDeCasa(shelly.info().idDispositiu)) throw ErrorUsuari(R.string.vm_altre_shelly)
            var n = 0
            val canals = if (config.teLinia) listOf(0, 1) else listOf(0)
            for (canal in canals) {
                val desDe = bd.darrerTs(canal)?.plus(60) ?: (ara() - 14 * 86400)
                shelly.minuts(canal, desDe) { files ->
                    // Cap minut de més de 5 minuts endavant: deixaria la sincronització encallada al futur (1.1.3)
                    bd.insereixMinuts(canal, files.filter { it.ts <= ara() + 300 })
                    n += files.size
                }
            }
            // Amb les dades noves: la nevera, l'interval dels aparells que s'engeguen sols, els aparells de la línia i el
            // que es repeteix (maqueta 12)
            actualitzaNevera()
            completaIntervals()
            aprenLinia()
            actualitzaRepetits()
            // Les vegades noves, i de qui són totes (maqueta 13)
            actualitzaVegades()
            reassignaVegades()
            resumeixVells()
            n
        }
    }

    /**
     * Per a l'avís de potència: els minuts nous, només si es llegeix el Shelly de casa i ara no se sincronitza (si no,
     * l'avís esperaria tota la sincronització, o els 10 s que triga a no trobar el Shelly des de fora).
     */
    suspend fun sincronitzaSiLliure(l: Lectura) {
        if (l.origen != Origen.LOCAL || sincronitzant.isLocked) return
        runCatching { sincronitza() }
    }

    /**
     * Amb un contracte dinàmic, baixa els preus de mercat dels dies que tenen dades i encara no tenen preu,
     * i els de demà quan ja s'han publicat: primer demà i avui, i després cap enrere, perquè el preu d'ara surti de
     * seguida (de 35 dies, la baixada triga). Amb [nomesRecents], només demà, avui i ahir. Un dia que falla no es torna a
     * provar fins al cap de mitja hora.
     */
    suspend fun actualitzaPreus(nomesRecents: Boolean = false) = withContext(Dispatchers.IO) {
        val t = config.tarifa
        if (!t.dinamica) return@withContext
        val avui = LocalDate.now(zona)
        val primer = if (nomesRecents) avui.minusDays(1)
        else bd.primerTs()?.let { Instant.ofEpochSecond(it).atZone(zona).toLocalDate() }?.coerceAtLeast(avui.minusDays(35)) ?: avui.minusDays(1)
        var dia = avui.plusDays(1)
        while (!dia.isBefore(primer)) {
            val falten = bd.preus(t.pais, iniciDia(dia), iniciDia(dia.plusDays(1))).size < 23
            val clau = t.pais to dia
            val provat = intentsPreus[clau]?.let { ara() - it < 1800 } == true
            if (falten && !provat) {
                intentsPreus[clau] = ara()
                runCatching { bd.desaPreus(t.pais, Mercat.preus(t.pais, dia)) }
            }
            dia = dia.minusDays(1)
        }
    }

    /** El preu de cada hora segons la tarifa, amb els preus de mercat desats si és dinàmica. */
    private inner class Preus(desDe: Long, finsA: Long) {
        val tarifa = config.tarifa
        private val mercat = if (tarifa.dinamica) bd.preus(tarifa.pais, desDe - 86400, finsA + 86400) else emptyMap()
        private val mitja = mercat.values.average().takeIf { !it.isNaN() }

        /** Si el període no té preus de mercat (un dia vell omplert pel núvol): la mitjana de tots els desats (1.1.3). */
        private val mitjaTotes by lazy {
            if (tarifa.dinamica) bd.preus(tarifa.pais, 0, ara() + 2 * 86400).values.average().takeIf { !it.isNaN() } else null
        }

        fun mercatDe(ts: Long) = mercat[(ts / 3600) * 3600]

        /** Si és dinàmica i no tenim el preu d'aquella hora, fa servir la mitjana dels que tenim. */
        fun de(ts: Long): Double =
            tarifa.preuKwh(ts, zona, ::mercatDe) ?: tarifa.preuKwh(ts, zona) { mitja ?: mitjaTotes } ?: tarifa.preuFix

        fun euros(hores: List<Hora>, dies: Double) = Euros(
            hores.sumOf { it.sempre * de(it.ts) }, hores.sumOf { it.linia * de(it.ts) },
            hores.sumOf { it.desconegut * de(it.ts) }, tarifa.fixosDia() * dies,
        )
    }

    /**
     * El fons en Wh per minut, calculat amb els últims 3 dies: amb els minuts exactes, o, si encara no n'hi ha
     * prou (qui només mira el núvol), amb els aproximats.
     */
    private fun fonsMinut(): Double? =
        bd.fons(ara() - 3 * 86400, ara() + 60, nomesExactes = true) ?: bd.fons(ara() - 3 * 86400, ara() + 60, nomesExactes = false)

    private fun fonsWh(): Double = fonsMinut() ?: 0.0

    fun fonsW(): Double? = fonsMinut()?.times(60)

    fun serie24h(): List<Punt> = bd.serie(ara() - 86400, ara() + 60)

    /** La potència de les últimes [hores], per a la gràfica del giny (maqueta 17). */
    fun serieDarreres(hores: Int): List<Punt> = bd.serie(ara() - hores * 3600L, ara() + 60)

    private fun iniciDia(data: LocalDate) = data.atStartOfDay(zona).toEpochSecond()

    /** L'energia per hores locals, també a les zones que no van per hores en punt UTC (l'Índia, el Nepal...). */
    private fun perHores(desDe: Long, finsA: Long, fonsWh: Double) = bd.perHores(desDe, finsA, fonsWh, desfasHora(zona, ara()))

    /** El consum d'avui: kWh i euros (sense els fixos), i si una part és aproximada. */
    fun avui(): Avui {
        val desDe = iniciDia(LocalDate.now(zona))
        val hores = perHores(desDe, ara() + 60, fonsWh())
        return Avui(hores.sumOf { it.total }, Preus(desDe, ara()).euros(hores, 0.0).consum, bd.teAproximats(desDe, ara() + 60))
    }

    /**
     * El resum d'un període de calendari (maqueta 10): el dia, la setmana de dilluns a diumenge, el mes o l'any on cau
     * [dia]. Les barres són les hores del dia (23 o 25 el dia del canvi d'hora), els dies de la setmana o del mes, o els
     * mesos de l'any. Els fixos compten els dies amb dades fins avui. També el total del període d'abans, per comparar.
     */
    fun resum(periode: Periode, dia: LocalDate = LocalDate.now(zona)): Resum {
        val fons = fonsWh()
        val avui = LocalDate.now(zona)
        val primer = iniciPeriode(periode, dia)
        val fi = fiPeriode(periode, dia)
        val desDe = iniciDia(primer)
        val finsA = minOf(iniciDia(fi), ara() + 60)
        val llista = if (finsA > desDe) perHores(desDe, finsA, fons) else emptyList()
        fun suma(hores: List<Hora>, ts: Long) =
            Hora(ts, hores.sumOf { it.sempre }, hores.sumOf { it.linia }, hores.sumOf { it.desconegut }, hores.sumOf { it.nuvol })
        // Les vegades del període, un sol cop: per a les barres (les que tenen aparell, repartides pel temps que hi cauen) i el rànquing
        val totesVegades = if (finsA > desDe) bd.vegades(desDe, finsA) else emptyList()
        val ambAparell = totesVegades.filter { it.aparellId != null }
        fun perAparell(t0: Long, t1: Long): Map<Long, Double> {
            val m = HashMap<Long, Double>()
            for (v in ambAparell) {
                val vf = v.fi + 60
                val solap = minOf(vf, t1) - maxOf(v.ts, t0)
                if (solap <= 0) continue
                val a = v.aparellId!!
                m[a] = (m[a] ?: 0.0) + v.kwh * solap / (vf - v.ts).coerceAtLeast(1)
            }
            return m
        }
        val barres = when (periode) {
            Periode.DIA -> {
                val hores = llista.associateBy { it.ts }
                (desDe until iniciDia(fi) step 3600L).map { ts ->
                    Barra("${Instant.ofEpochSecond(ts).atZone(zona).hour}", hores[ts] ?: Hora(ts, 0.0, 0.0, 0.0), perAparell(ts, ts + 3600))
                }
            }
            Periode.SETMANA, Periode.MES -> {
                val perDia = llista.groupBy { Instant.ofEpochSecond(it.ts).atZone(zona).toLocalDate() }
                generateSequence(primer) { it.plusDays(1) }.takeWhile { it.isBefore(fi) }.map { d ->
                    val etiqueta = if (periode == Periode.SETMANA)
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, Idioma.locale).removeSuffix(".")
                    else d.dayOfMonth.toString()
                    Barra(etiqueta, suma(perDia[d].orEmpty(), iniciDia(d)), perAparell(iniciDia(d), iniciDia(d.plusDays(1))))
                }.toList()
            }
            Periode.ANY -> {
                val perMes = llista.groupBy { Instant.ofEpochSecond(it.ts).atZone(zona).monthValue }
                (1..12).map { m ->
                    val mes = primer.withMonth(m)
                    Barra(
                        mes.month.getDisplayName(TextStyle.NARROW_STANDALONE, Idioma.locale).uppercase(Idioma.locale), suma(perMes[m].orEmpty(), iniciDia(mes)),
                        perAparell(iniciDia(mes), iniciDia(mes.plusMonths(1))),
                    )
                }
            }
        }
        // Els fixos: els dies del període que tenen dades, fins avui
        val primerDades = bd.primerTs()?.let { Instant.ofEpochSecond(it).atZone(zona).toLocalDate() }
        val diesFixos = if (primerDades == null) 0.0
        else maxOf(0L, ChronoUnit.DAYS.between(maxOf(primer, primerDades), minOf(fi, avui.plusDays(1)))).toDouble()
        val preus = Preus(desDe, finsA)
        val euros = preus.euros(llista, diesFixos)
        // El període d'abans, si l'app ja en tenia totes les dades
        val anterior = mou(periode, primer, -1)
        // Si és el d'ara (a mitges), el mateix tros del d'abans: de l'inici fins al mateix temps transcorregut (1.1.3)
        val actual = !avui.isBefore(primer) && avui.isBefore(fi)
        val finsAnterior = if (actual) minOf(iniciDia(anterior) + (ara() - desDe), desDe) else desDe
        val anteriorKwh = if (primerDades != null && !anterior.isBefore(primerDades)) bd.kwhEntre(iniciDia(anterior), finsAnterior) else null
        val sempre = llista.sumOf { it.sempre }
        val linia = llista.sumOf { it.linia }
        val desconegut = llista.sumOf { it.desconegut }
        return Resum(
            barres, sempre, linia, desconegut,
            fons.takeIf { it > 0 }?.times(60), euros, finsA > desDe && bd.teAproximats(desDe, finsA),
            primer, fi, !avui.isBefore(primer) && avui.isBefore(fi), anteriorKwh, primerDades,
            if (finsA > desDe) ranquing(desDe, finsA, fons, sempre, linia, desconegut, euros, preus, totesVegades) else null,
        )
    }

    /** El consum d'aquest mes, des del dia 1, amb els fixos dels dies que han passat. */
    fun resumMes(): Resum {
        val fons = fonsWh()
        val avui = LocalDate.now(zona)
        val desDe = iniciDia(avui.withDayOfMonth(1))
        val hores = perHores(desDe, ara() + 60, fons)
        val preus = Preus(desDe, ara())
        // Els fixos dels dies del mes que tenen dades, com l'Historial (1.1.3)
        val primerDades = bd.primerTs()?.let { Instant.ofEpochSecond(it).atZone(zona).toLocalDate() }
        val dies = if (primerDades == null) 0.0 else maxOf(0L, ChronoUnit.DAYS.between(maxOf(avui.withDayOfMonth(1), primerDades), avui.plusDays(1))).toDouble()
        val euros = preus.euros(hores, dies)
        val sempre = hores.sumOf { it.sempre }
        val linia = hores.sumOf { it.linia }
        val desconegut = hores.sumOf { it.desconegut }
        return Resum(
            emptyList(), sempre, linia, desconegut, fons.takeIf { it > 0 }?.times(60), euros,
            ranquing = ranquing(desDe, ara() + 60, fons, sempre, linia, desconegut, euros, preus),
        )
    }

    /**
     * Tot el consum d'un període sense barrejar res (maqueta 13): cada aparell amb les seves vegades (les de la resta de la
     * casa i les de la línia de la pinça 2) i la nevera amb el seu compressor; i a part, el que la casa gasta sempre, els
     * petits consums (per sota de 250 W), les vegades sense identificar, la resta de la línia i el que no té el detall de
     * cada minut. La suma de tot és el total. Si a la línia no s'hi ha dit cap aparell, la línia surt com un més.
     */
    private fun ranquing(
        desDe: Long, finsA: Long, fonsWh: Double, sempre: Double, linia: Double, desconegut: Double, euros: Euros, p: Preus,
        vegades: List<Vegada> = bd.vegades(desDe, finsA),
    ): Ranquing {
        val tots = bd.aparells()
        val perId = tots.associateBy { it.id }
        class Acc(var kwh: Double = 0.0, var euros: Double = 0.0, var cops: Int = 0, var ensenyats: Int = 0)
        val acc = mutableMapOf<Long, Acc>()
        var senseId = 0.0
        var senseIdEuros = 0.0
        var ep0 = 0.0
        var liniaAssignada = 0.0
        for (v in vegades) {
            // Una vegada que passa de mitjanit (la càrrega d'un cotxe de nit) es reparteix entre els dos dies
            val kwh = v.kwh * part(v, desDe, finsA)
            if (v.canal == 0) ep0 += kwh
            val a = v.aparellId?.let(perId::get)
            if (a == null) {
                if (v.canal == 0) {
                    senseId += kwh
                    senseIdEuros += eurosDe(v, desDe, finsA, p)
                }
                continue
            }
            if (v.canal == 1) liniaAssignada += kwh
            val x = acc.getOrPut(a.id) { Acc() }
            x.kwh += kwh
            x.euros += eurosDe(v, desDe, finsA, p)
            if (v.ts >= desDe) x.cops++
            if (v.com == Com.ENSENYAT && v.ts >= desDe) x.ensenyats++
        }
        // El que no té el detall de cada minut: de la resta de la casa i de la línia
        val liniaAprox = bd.liniaAproximada(desDe, finsA)
        val senseDetall = bd.senseDetall(desDe, finsA, fonsWh) + liniaAprox
        val preuResta = if (desconegut > 0.01) euros.desconegut / desconegut else p.de(desDe)
        val files = acc.map { (id, x) ->
            val a = perId.getValue(id)
            FilaRanquing(a, a.nom, x.kwh, x.euros, x.cops, x.ensenyats, aLaLinia(a))
        }.toMutableList()
        // La nevera: el compressor trobat sol, la part del temps que funciona
        var nevera = 0.0
        neveraAutomatica(tots)?.let { a ->
            val w = config.neveraW
            nevera = (w * config.neveraCicle.coerceAtLeast(0.0) * bd.minutsExactes(desDe, finsA) / 60_000)
                .coerceAtMost(max(desconegut - ep0 - (senseDetall - liniaAprox), 0.0))
            if (nevera > 0) files += FilaRanquing(a, a.nom, nevera, nevera * preuResta, 0, 0, false, compressorW = w)
        }
        val ambAparells = aparellsLinia().isNotEmpty()
        if (!ambAparells && linia > 0) {
            // La línia sense aparells surt sencera, com un més; si hi ha un aparell amb el seu nom (d'abans de la 0.25), és ell.
            // Sense nom (s'ha tret la pinça 2 però hi ha dades d'abans), la pantalla hi posa «pinça 2»
            val ella = tots.firstOrNull { esLinia(it) }
            files += FilaRanquing(ella, ella?.nom ?: config.nomLinia, linia, euros.linia, 0, 0, true, esLinia = true)
        }
        val liniaAltres = if (ambAparells) max(linia - liniaAssignada - liniaAprox, 0.0) else 0.0
        val preuLinia = if (linia > 0.01) euros.linia / linia else preuResta
        val petits = max(desconegut - ep0 - nevera - (senseDetall - liniaAprox), 0.0)
        val detall = if (ambAparells) senseDetall else senseDetall - liniaAprox
        return Ranquing(
            files.sortedByDescending { it.kwh }, sempre + linia + desconegut, sempre, euros.sempre, petits, petits * preuResta,
            senseId, senseIdEuros, liniaAltres, liniaAltres * preuLinia, detall, detall * preuResta,
        )
    }

    /** La nevera o el congelador que s'engega sol i no té mostres: el compressor trobat a les dades li fa de referència. */
    private fun neveraAutomatica(aparells: List<Aparell>): Aparell? {
        if (config.neveraW <= 0) return null
        val ambMostres = bd.aparellsAmbMostres()
        return listOf(Categoria.NEVERA, Categoria.CONGELADOR).firstNotNullOfOrNull { cat ->
            aparells.firstOrNull { it.categoria == cat && it.funciona == Funcionament.SOL && it.id !in ambMostres && !aLaLinia(it) }
        }
    }

    fun infoPreu(): InfoPreu {
        val ara = ara()
        val hora = iniciHora(ara, zona)
        val avuiInici = iniciDia(LocalDate.now(zona))
        val p = Preus(avuiInici, avuiInici + 2 * 86400)
        fun hp(ts: Long) = HoraPreu(
            ts, Instant.ofEpochSecond(ts).atZone(zona).hour,
            if (p.tarifa.dinamica && p.mercatDe(ts) == null) null else p.de(ts), p.tarifa.franja(ts, zona),
        )
        val properes = (0 until 24).map { hp(hora + it * 3600L) }
        val franja = properes.first().franja
        val canvia = if (franja == Franja.UNICA || franja == Franja.MERCAT) null
        else properes.drop(1).firstOrNull { it.franja != franja }?.hora
        // Les hores d'avui fins a l'inici de demà: els dies del canvi d'hora en tenen 23 o 25
        val dema = iniciDia(LocalDate.now(zona).plusDays(1))
        return InfoPreu(p.tarifa, properes.first(), canvia, (avuiInici until dema step 3600L).map { hp(it) }, properes)
    }

    /**
     * Si la línia de la segona pinça gastés a les hores barates del mes (el terç més barat),
     * quant s'estalviaria. Null si no hi ha línia o la diferència és petita.
     */
    fun consellLinia(): ConsellLinia? {
        if (!config.teLinia) return null
        val desDe = iniciDia(LocalDate.now(zona).withDayOfMonth(1))
        val hores = perHores(desDe, ara() + 60, fonsWh())
        val p = Preus(desDe, ara())
        val preus = hores.map { p.de(it.ts) }.sorted()
        if (preus.size < 24) return null
        val barat = preus.take(preus.size / 3).average()
        val car = preus[preus.size * 2 / 3]
        val kwh = hores.sumOf { it.linia }
        if (kwh < 0.5) return null
        val estalvi = hores.sumOf { it.linia * (p.de(it.ts) - barat) }
        val percentCar = (hores.filter { p.de(it.ts) >= car }.sumOf { it.linia } / kwh * 100).toInt()
        return ConsellLinia(estalvi, percentCar).takeIf { estalvi >= 0.3 }
    }

    fun aparells() = bd.aparells()
    fun desaAparell(a: Aparell) {
        bd.desaAparell(a)
        haCanviat()
    }

    fun esborraAparell(id: Long) {
        bd.esborraAparell(id)
        bd.esborraEtiquetes(id)
        haCanviat()
    }

    // ---------- Les vegades (maqueta 13) ----------

    /**
     * Les vegades tenen el seu bloqueig: buscar-les o repartir-les pot trigar, i el de tot el repositori aturaria les
     * lectures en directe i els avisos.
     */
    private val bloqueigVegades = Any()

    /** Hi ha hagut un canvi (una mostra, una etiqueta, un aparell): les vegades s'han de tornar a repartir. */
    @Volatile
    private var calReassignar = true

    /** La pinça 2 ha canviat: les vegades s'han de tornar a buscar de cap a peus. */
    @Volatile
    private var calRebuscar = false

    private fun haCanviat() {
        calReassignar = true
    }

    /**
     * La còpia de seguretat (1.0.2): la base de dades i la configuració en un zip. La foto de la base es fa amb les vegades i el
     * resum aturats (un moment, és un fitxer local); el zip, que pot anar al Drive i trigar, sense cap bloqueig (1.1.3).
     */
    fun copiaSeguretat(context: Context, versioApp: String, sortida: java.io.OutputStream) {
        val foto = synchronized(bloqueigVegades) { synchronized(bloqueigResum) { Copia.fotografia(context, bd) } }
        try {
            Copia.escriu(foto, config, versioApp, sortida)
        } finally {
            foto.delete()
        }
    }

    /** Deixa una còpia a punt per restaurar, ja comprovada: es posa en lloc del que hi ha en tornar a obrir l'app (1.1.3). */
    fun preparaRestauracio(context: Context, entrada: java.io.InputStream): Copia.Manifest =
        Copia.prepara(context, BaseDades.VERSIO, entrada)

    /** Després de posar o treure la pinça 2: la propera vegada, totes les vegades de nou. */
    fun tornaABuscarVegades() {
        calRebuscar = true
        calReassignar = true
        // I des de quan hi ha la pinça 2, de nou
        config.iniciLinia = 0
    }

    /**
     * Busca les vegades (episodis de 2 minuts o més) als minuts exactes que encara no s'han mirat: de la resta de la casa i de
     * la línia de la pinça 2. El primer cop (o si no n'hi ha cap de desada, o ha canviat la pinça 2), totes les dades. Després,
     * des de la vegada que encara durava a l'última mirada (o havia acabat feia menys de mitja hora): es torna a buscar sencera,
     * perquè la base dels minuts d'enmig ja seria la seva i hi trobaria el que tenia a sobre com una vegada nova. Per trossos
     * d'una setmana, amb les 2 hores d'abans per a la base i 24 hores de més per acabar les del final; el tros següent comença
     * quan s'acaben les que hi passen.
     */
    fun actualitzaVegades() {
        synchronized(bloqueigVegades) { buscaVegades() }
    }

    private fun buscaVegades() {
        val darrer = bd.darrerTs(0) ?: return
        // També quan aquesta versió les troba d'una altra manera (la 0.28 separa el que s'encén a sobre d'una vegada llarga)
        val rebuscar = calRebuscar || config.versioVegades < VERSIO_VEGADES
        calRebuscar = false
        val fins = if (rebuscar || !bd.hiHaVegades()) 0L else config.vegadesFins
        val desDe = if (fins == 0L) bd.primerExacte() ?: return
        else minOf(bd.vegadaOberta(fins - 30 * 60) ?: fins, fins - 30 * 60)
        val canals = if (config.teLinia) listOf(0 to false, 1 to true) else listOf(0 to false)
        val noves = mutableListOf<Vegada>()
        var s = desDe
        while (s <= darrer) {
            val e = minOf(s + 7 * 86400, darrer + 60)
            // Primer on s'acaben les que comencen dins del tros; després, totes les que comencen abans d'aquell punt: també les
            // que s'han encès a sobre d'una de llarga després del final del tros (el tros següent comença on acaba la llarga)
            val perCanal = canals.map { (canal, linia) -> canal to Episodis.troba(bd.minutsW(linia, s - 2 * 3600, e + 24 * 3600), llargada = 2) }
            var seguent = e
            for ((_, trobades) in perCanal) {
                trobades.filter { it.ts in s until e && it.fi >= e }.maxOfOrNull { it.fi + 60 }?.let { seguent = maxOf(seguent, it) }
            }
            for ((canal, trobades) in perCanal) trobades.filter { it.ts in s until seguent }.forEach { noves += Vegada.de(canal, it) }
            s = seguent
        }
        bd.substitueixVegades(desDe, assignaTotes(noves))
        config.vegadesFins = darrer
        config.versioVegades = VERSIO_VEGADES
    }

    /** De qui és cada vegada, amb el que l'app sap ara ([assigna]). */
    private fun assignaTotes(vegades: List<Vegada>): List<Vegada> {
        if (vegades.isEmpty()) return vegades
        val index = IndexEtiquetes(bd.etiquetes())
        val aparells = bd.aparells()
        // Els sempre encesos van dins del fons: el que se n'hagués dit no compta
        val exclosos = aparells.filter { it.funciona == Funcionament.SEMPRE }.map { it.id }.toSet()
        val casa = aparellsAEntrenar()
        val refsCasa = referencies()
        val linia = aparellsLinia()
        val refsLinia = referenciesLinia()
        // Abans de la pinça 2, els aparells de la línia també eren a la resta de la casa (un cotxe que ja carregava abans de posar-hi la pinça)
        val iniciLinia = iniciLinia()
        val tots = casa + linia
        val refsTots = refsCasa + refsLinia
        return vegades.map { v ->
            val e = index.de(v)
            val (a, com) = when {
                // Només les etiquetes dels aparells que hi ha ara a la línia: si s'han tret, la línia sencera ja surt com un més
                // al rànquing, i comptar-la també a l'aparell la sumaria dues vegades (1.1.3)
                v.canal == 1 -> assigna(v, linia, refsLinia, e.filter { et -> linia.any { it.id == et.aparellId } }, exclosos)
                abansDeLaLinia(v, iniciLinia) -> assigna(v, tots, refsTots, e, exclosos)
                else -> assigna(v, casa, refsCasa, e, exclosos)
            }
            v.copy(aparellId = a, com = com)
        }
    }

    /** El primer minut de la pinça 2: es desa, perquè no es mogui quan se n'esborrin els minuts vells. */
    private fun iniciLinia(): Long? = config.iniciLinia.takeIf { it > 0 } ?: bd.primerLinia()?.also { config.iniciLinia = it }

    /** Una vegada de la resta de la casa d'abans de la pinça 2: hi podia haver qualsevol aparell, també els de la línia. */
    private fun abansDeLaLinia(v: Vegada, iniciLinia: Long?) = v.canal == 0 && linia() && (iniciLinia == null || v.ts < iniciLinia)

    private fun linia() = config.teLinia

    /**
     * Torna a mirar de qui és cada vegada, si hi ha hagut algun canvi des de l'última vegada: després que l'usuari
     * ensenyi, respongui o canviï un aparell, i quan l'app aprèn alguna cosa nova. Si no, no fa res.
     */
    fun reassignaVegades() {
        synchronized(bloqueigVegades) {
            if (!calReassignar) return
            calReassignar = false
            val totes = bd.vegades(0, Long.MAX_VALUE)
            val noves = assignaTotes(totes)
            bd.reassigna(totes.zip(noves).filter { (a, b) -> a.aparellId != b.aparellId || a.com != b.com }.map { (_, b) -> Triple(b.id, b.aparellId, b.com) })
        }
    }

    /**
     * «No era ell»: aquesta vegada no era d'aquest aparell, i l'app no l'hi tornarà a donar. Si l'hi havia dit l'usuari
     * (maqueta 17), fora també el que ho deia: les etiquetes que la fan seva segons el rànquing ([encavalca]: no una estona
     * llarga que només la cobreix, com la de la càrrega del cotxe amb la placa a sobre) i les mostres lligades a aquelles
     * etiquetes. Es decideix amb el que hi ha ara a la base de dades, no amb la vegada que ensenyava la pantalla.
     */
    fun noEraEll(v: Vegada): Desfer? {
        val a = v.aparellId ?: return null
        val d = synchronized(this) {
            val etiquetes = bd.etiquetes().filter { it.aparellId == a && !it.no && encavalca(v, it) > 0 }
            val mostres = bd.mostres().filter { m -> m.aparellId == a && m.font != "linia" && etiquetes.any { lliga(it, m) } }
            bd.esborra(etiquetes.map { it.id }, mostres.map { it.id })
            val no = bd.desaEtiqueta(Etiqueta(v.canal, v.ts, v.fi + 59, a, no = true))
            haCanviat()
            Desfer(etiquetes = listOf(no), tornaEtiquetes = etiquetes, tornaMostres = mostres)
        }
        // Repartir de nou pot trigar: fora del bloqueig del repositori, que aturaria les lectures en directe
        reassignaVegades()
        return d
    }

    /** On comença el que diu una mostra: en directe es desa en apagar-lo, i l'etiqueta és de quan es va encendre. */
    private fun iniciMostra(m: Mostra) = if (m.font == "directe" && m.durada != null) m.ts - m.durada else m.ts

    /**
     * Si una etiqueta i una mostra són de la mateixa resposta o del mateix entrenament: comencen alhora (±2 s), o, d'una estona
     * passada, la mostra és la de quan es va apagar (l'etiqueta acaba al final d'aquell minut).
     */
    private fun lliga(e: Etiqueta, m: Mostra) =
        abs(e.desDe - iniciMostra(m)) <= 2 || (m.font == "passat" && abs(e.finsA - (m.ts + 59)) <= 2)

    /**
     * Desfà una resposta o un entrenament (maqueta 17). El que es torna a posar només si l'aparell encara hi és; de l'aparell
     * d'abans, només el que la resposta havia canviat (com funciona, la potència, el consum de l'any i en espera): si
     * mentrestant se n'ha canviat una altra cosa, es queda.
     */
    fun desfes(d: Desfer) {
        synchronized(this) {
            val existeixen = bd.aparells().associateBy { it.id }
            bd.esborra(d.etiquetes, d.mostres)
            d.tornaEtiquetes.filter { it.aparellId in existeixen }.forEach { bd.desaEtiqueta(it) }
            d.tornaMostres.filter { it.aparellId in existeixen }.forEach { m ->
                val interval = if (m.wMin != null && m.wMax != null) m.wMin to m.wMax else null
                bd.desaMostra(m.aparellId, m.ts, m.dp, m.durada, m.font, interval, m.variacio, m.arrencada)
            }
            d.pregunta?.let { bd.respon(it, EstatPregunta.PENDENT, null) }
            d.repetit?.let { bd.responRepetit(it, EstatPregunta.PENDENT, null) }
            d.aparellAbans?.let { abans ->
                existeixen[abans.id]?.let { ara ->
                    bd.desaAparell(ara.copy(funciona = abans.funciona, potenciaW = abans.potenciaW, kwhAny = abans.kwhAny, esperaW = abans.esperaW))
                }
            }
            d.aparellNou?.let {
                bd.esborraAparell(it)
                bd.esborraEtiquetes(it)
            }
            d.ignorada?.let { ts -> config.senseNomIgnorades = config.senseNomIgnorades - ts }
            haCanviat()
        }
        reassignaVegades()
    }

    /** Un aparell nou, des de «Tots els aparells…» (maqueta 17): torna el seu id. */
    fun afegeixAparell(a: Aparell): Long {
        val id = bd.desaAparell(a.copy(id = 0))
        haCanviat()
        return id
    }

    /**
     * El que l'usuari ha ensenyat d'un aparell (maqueta 17), del més nou al més vell: cada estona passada (les mostres d'un
     * mateix dia), cada «L'he encès / L'he apagat», cada resposta, i cada resposta a una cosa que es repeteix (amb totes les
     * etiquetes dels seus cops, també dels que no tenen mostra). Cada etiqueta va amb les mostres de la mateixa resposta
     * ([lliga]); les que no en tenen cap (d'abans de la 0.26), soles. El que l'app ha après sola (la línia), no.
     */
    private fun ensenyats(aparellId: Long): List<Ensenyat> {
        val mostres = bd.mostres().filter { it.aparellId == aparellId && it.font != "linia" && it.font.isNotBlank() }
        val etiquetes = bd.etiquetes().filter { it.aparellId == aparellId && !it.no }
        val usades = mutableSetOf<Long>()
        val usadesM = mutableSetOf<Long>()
        val llista = mutableListOf<Ensenyat>()
        // El que es repeteix: cada resposta, amb els seus cops
        for (r in bd.repetits().filter { it.estat == EstatPregunta.RESPOSTA && it.aparellId == aparellId }) {
            val inicis = r.cops.map { it.first }
            val ms = mostres.filter { m -> m.font == "repetit" && m.id !in usadesM && inicis.any { abs(it - m.ts) <= 2 } }
            val es = etiquetes.filter { e -> e.id !in usades && inicis.any { abs(it - e.desDe) <= 2 } }
            if (ms.isEmpty() && es.isEmpty()) continue
            usadesM += ms.map { it.id }
            usades += es.map { it.id }
            llista += Ensenyat(OrigenEnsenyat.REPETIT, inicis.min(), inicis.max(), ms, es)
        }
        val grups = mostres.filter { it.id !in usadesM }.groupBy { m ->
            when (m.font) {
                "passat" -> "passat " + Instant.ofEpochSecond(m.ts).atZone(zona).toLocalDate()
                else -> "${m.font} ${m.id}"
            }
        }.values
        for (ms in grups) {
            val origen = when (ms.first().font) {
                "passat" -> OrigenEnsenyat.ESTONA
                "directe" -> OrigenEnsenyat.DIRECTE
                "vegada" -> OrigenEnsenyat.VEGADA
                "pregunta" -> OrigenEnsenyat.PREGUNTA
                "pic" -> OrigenEnsenyat.PIC
                "repetit" -> OrigenEnsenyat.REPETIT
                else -> OrigenEnsenyat.RESPOSTA
            }
            val es = etiquetes.filter { e -> e.id !in usades && ms.any { lliga(e, it) } }
            usades += es.map { it.id }
            llista += Ensenyat(origen, ms.minOf { iniciMostra(it) }, ms.maxOf { it.ts }, ms, es)
        }
        llista += etiquetes.filter { it.id !in usades }.map { Ensenyat(OrigenEnsenyat.RESPOSTA, it.desDe, it.finsA, emptyList(), listOf(it)) }
        return llista.sortedByDescending { it.desDe }
    }

    /** Treu d'un aparell una cosa que li havia ensenyat l'usuari (maqueta 17): ja no compta per reconèixer-lo. */
    fun treuEnsenyat(e: Ensenyat): Desfer {
        val d = synchronized(this) {
            bd.esborra(e.etiquetes.map { it.id }, e.mostres.map { it.id })
            haCanviat()
            Desfer(tornaEtiquetes = e.etiquetes, tornaMostres = e.mostres)
        }
        reassignaVegades()
        return d
    }

    /** El que ha gastat un aparell aquest mes, les seves últimes vegades i el que se li ha ensenyat, per a la fitxa. */
    fun fitxa(aparellId: Long): Fitxa {
        val desDe = iniciDia(LocalDate.now(zona).withDayOfMonth(1))
        val finsA = ara() + 60
        val mes = bd.vegades(desDe, finsA, aparellId)
        return Fitxa(
            aparellId, mes.sumOf { it.kwh * part(it, desDe, finsA) }, mes.count { it.ts >= desDe }, bd.ultimesVegades(aparellId, 8),
            ensenyats(aparellId),
        )
    }

    /** Quina part d'una vegada cau dins d'un període (les que passen de mitjanit, o del dia 1, es reparteixen). */
    private fun part(v: Vegada, desDe: Long, finsA: Long): Double {
        val fi = v.fi + 60
        return (min(fi, finsA) - max(v.ts, desDe)).coerceAtLeast(0).toDouble() / (fi - v.ts)
    }

    /**
     * Els euros de la part d'una vegada que cau dins de [desDe, finsA): cada hora al seu preu (1.1.3). Abans tota la vegada
     * anava al preu de l'hora en què començava: una càrrega del cotxe que començava en hora punta es pagava tota a preu de punta.
     */
    private fun eurosDe(v: Vegada, desDe: Long, finsA: Long, p: Preus): Double {
        val fi = v.fi + 60
        val t0 = max(v.ts, desDe)
        val t1 = min(fi, finsA)
        if (t1 <= t0) return 0.0
        val perSegon = v.kwh / (fi - v.ts).coerceAtLeast(1)
        var euros = 0.0
        var t = t0
        while (t < t1) {
            val h = iniciHora(t, zona)
            val seguent = min(t1, h + 3600)
            euros += perSegon * (seguent - t) * p.de(t)
            t = seguent
        }
        return euros
    }

    // ---------- Què hi ha encès ara (maqueta 15) ----------

    /** Les vegades que encara duraven a l'últim minut del Shelly: només si és de fa menys de 10 minuts (a casa i al dia). */
    fun obertes(): Obertes {
        val nevera = neveraAutomatica(bd.aparells())
        val neveraW = config.neveraW.takeIf { it > 0 && nevera != null }
        val darrer = bd.darrerTs(0)
        if (darrer == null || ara() - darrer > 10 * 60) return Obertes(false, emptyList(), nevera, neveraW)
        return Obertes(true, bd.vegades(darrer - 2 * 60, ara() + 60).filter { it.fi >= darrer - 2 * 60 }, nevera, neveraW)
    }

    /**
     * Els aparells encesos ara (com a molt 3), per dir-ho a l'avís de potència: el nom i els W. Amb el mateix criteri que la
     * pantalla Ara i el rànquing ([encesDelCanal]); el que no se sap què és, no hi surt, i la línia sense aparells, amb el seu nom.
     */
    fun encesAra(l: Lectura): List<Pair<String, Double>> {
        val fons = fonsW() ?: return emptyList()
        val o = obertes()
        val noms = aparells().associate { it.id to it.nom }
        val wLinia = if (config.teLinia) (l.linia?.w ?: 0.0).coerceAtLeast(0.0) else 0.0
        val casa = encesDelCanal(ara(), 0, l.total.w - wLinia - fons, o.vegades, aparellsAEntrenar(), referencies())
        val apLinia = aparellsLinia()
        val linia = when {
            wLinia < AnalisiPics.LLINDAR -> emptyList()
            apLinia.isEmpty() -> listOf(EncesAra(TipusEnces.PETITS, 1, wLinia))
            else -> encesDelCanal(ara(), 1, wLinia, o.vegades, apLinia, referenciesLinia())
        }
        return (casa + linia).mapNotNull { e ->
            when {
                e.aparellId != null -> noms[e.aparellId]?.let { it to e.w }
                e.canal == 1 && e.w >= AnalisiPics.LLINDAR -> config.nomLinia to e.w
                else -> null
            }
        }.sortedByDescending { it.second }.take(3)
    }

    // ---------- El que més gasta i encara no té nom (maqueta 15) ----------

    /** Els aparells que poden ser una vegada: els de la resta de la casa, o els de la línia si és d'abans de la pinça 2. */
    private fun candidatsDe(v: Vegada): List<Aparell> = when {
        v.canal == 1 -> aparellsLinia()
        abansDeLaLinia(v, iniciLinia()) -> aparellsAEntrenar() + aparellsLinia()
        else -> aparellsAEntrenar()
    }

    /**
     * Les vegades més grosses dels últims 7 dies que encara no tenen nom (com a molt [quants], de 0,3 kWh o més; no les que
     * l'usuari ha dit que no sap què eren), per preguntar-ne d'un toc; i quant és tot el que no té nom i quina part del total.
     */
    fun senseNom(quants: Int = 3): SenseNom {
        val desDe = ara() - 7 * 86400
        val finsA = ara() + 60
        val sense = bd.vegades(desDe, finsA).filter { it.canal == 0 && it.aparellId == null }
        val kwh = sense.sumOf { it.kwh * part(it, desDe, finsA) }
        val total = bd.kwhEntre(desDe, finsA)
        val ignorades = config.senseNomIgnorades
        val p = Preus(desDe, finsA)
        val refs = referencies() + referenciesLinia()
        val nos = bd.etiquetes().filter { it.no }
        val grosses = sense.filter { it.ts !in ignorades && it.kwh >= 0.3 }.sortedByDescending { it.kwh }.take(quants).map { v ->
            // Els que l'usuari ha dit que no eren, no es proposen
            val candidats = candidatsDe(v).filter { a -> nos.none { it.aparellId == a.id && encavalca(v, it) > 0 } }
            VegadaSenseNom(
                v, eurosDe(v, v.ts, v.fi + 60, p), endevina(v.w, candidats, refs, candidats.size, v.minuts.toDouble(), v.forma),
                bd.minutsW(linia = false, v.ts - 15 * 60, v.fi + 16 * 60).map { it.ts to it.w },
            )
        }
        return SenseNom(grosses, kwh, if (total > 0.05) kwh / total else 0.0)
    }

    /**
     * De qui és una vegada sense nom: una mostra de l'aparell amb la potència, la durada i la firma de la vegada (si s'engega
     * sol, amb un interval al voltant de la potència) i una etiqueta a l'inici, que no s'emporta el que s'hi va encendre a
     * sobre. Les vegades que s'hi assemblen seran seves quan es tornin a repartir.
     */
    @Synchronized
    fun responVegada(v: Vegada, aparellId: Long): Desfer? {
        val a = bd.aparells().firstOrNull { it.id == aparellId } ?: return null
        val mostra = if (!bd.teMostra(aparellId, v.ts, "vegada")) {
            val interval = if (a.funciona == Funcionament.SOL) v.w * 0.8 to v.w * 1.2 else null
            bd.desaMostra(aparellId, v.ts, v.w, v.fi - v.ts + 60, "vegada", interval, v.variacio, v.arrencada)
        } else null
        val etiqueta = bd.desaEtiqueta(Etiqueta(v.canal, v.ts, v.ts, aparellId))
        haCanviat()
        return Desfer(etiquetes = listOf(etiqueta), mostres = listOfNotNull(mostra))
    }

    /** «No ho sé»: aquesta vegada ja no es pregunta (se n'oblida al cap d'una setmana, quan ja no surt). */
    @Synchronized
    fun ignoraVegada(v: Vegada): Desfer {
        config.senseNomIgnorades = config.senseNomIgnorades.filter { it > ara() - 8 * 86400 }.toSet() + v.ts
        return Desfer(ignorada = v.ts)
    }

    // ---------- El resum de la setmana i el sempre encès (maqueta 15) ----------

    /** La setmana passada, de dilluns a diumenge, si l'app en té dades; null si no. */
    fun resumSetmana(): ResumSetmana? {
        val dilluns = iniciPeriode(Periode.SETMANA, LocalDate.now(zona)).minusWeeks(1)
        val r = resum(Periode.SETMANA, dilluns)
        if (!r.ambDades || (r.ranquing?.total ?: 0.0) < 0.5) return null
        return ResumSetmana(dilluns, r, bd.repetits(EstatPregunta.PENDENT).size)
    }

    /**
     * Què s'ha apagat a la caça del sempre encès: si l'aparell està sempre encès, aquesta és la seva potència (i el que gasta
     * l'any); si no, el que gasta en espera.
     */
    fun desaEspera(aparellId: Long, w: Double): Desfer? {
        val a = bd.aparells().firstOrNull { it.id == aparellId } ?: return null
        bd.desaAparell(if (a.funciona == Funcionament.SEMPRE) a.copy(potenciaW = w, kwhAny = w * 8.76) else a.copy(esperaW = w))
        haCanviat()
        return Desfer(aparellAbans = a)
    }

    // ---------- El resum per hores de les dades velles (maqueta 16) ----------

    /**
     * Un cop al dia: els dies de fa més de 35 dies passen al resum per hores (cada un amb el fons d'aquells dies; sense minuts
     * exactes, amb el d'ara), i d'aquí en endavant les sumes de l'Historial en surten. Els minuts de fa més de 400 dies
     * s'esborren: a l'any, la base de dades creixia uns 60 MB, i la vista de l'any llegia mig milió de minuts.
     */
    fun resumeixVells() {
        synchronized(bloqueigResum) { resumeix() }
    }

    private fun resumeix() {
        if (ara() - config.resumMirat < 20 * 3600) return
        config.resumMirat = ara()
        // Amb el rellotge del mòbil malament (en el futur), no: el resum i l'esborrat van amb les dades que hi ha de debò
        val darrer = bd.darrerMinut() ?: return
        val avui = minOf(LocalDate.now(zona), Instant.ofEpochSecond(darrer).atZone(zona).toLocalDate())
        val nouFins = iniciDia(avui.minusDays(DIES_SENSE_RESUM))
        val primer = bd.primerMinut() ?: return
        var dia = Instant.ofEpochSecond(maxOf(config.resumFins, primer)).atZone(zona).toLocalDate()
        val fonsAra = fonsWh()
        while (iniciDia(dia) < nouFins) {
            resumeixDia(dia, fonsAra)
            dia = dia.plusDays(1)
        }
        if (nouFins > config.resumFins) {
            config.resumFins = nouFins
            bd.resumFins = nouFins
        }
        bd.esborraMinuts(minOf(config.resumFins, iniciDia(avui.minusDays(DIES_AMB_MINUTS))))
    }

    private fun resumeixDia(dia: LocalDate, fonsAra: Double) {
        val desDe = iniciDia(dia)
        val finsA = iniciDia(dia.plusDays(1))
        val fons = bd.fons(finsA - 3 * 86400, finsA, nomesExactes = true) ?: fonsAra
        bd.resumeix(desDe, finsA, fons, desfasHora(zona, desDe))
    }

    /**
     * El resum per hores té el seu bloqueig: fer-lo pot trigar (el primer cop, mesos de dades), i el del repositori aturaria
     * les lectures en directe. També el fa servir [refesResum], perquè un dia que el núvol omple mentre es resumeix no es perdi.
     */
    private val bloqueigResum = Any()

    /** Han canviat els minuts d'uns dies que ja tenen resum (el núvol per hores): se'n torna a fer. */
    private fun refesResum(desDe: Long, finsA: Long) {
        synchronized(bloqueigResum) { refes(desDe, finsA) }
    }

    private fun refes(desDe: Long, finsA: Long) {
        if (desDe >= config.resumFins) return
        val fonsAra = fonsWh()
        var dia = Instant.ofEpochSecond(desDe).atZone(zona).toLocalDate()
        while (iniciDia(dia) < minOf(finsA, config.resumFins)) {
            resumeixDia(dia, fonsAra)
            dia = dia.plusDays(1)
        }
    }

    // ---------- Exportar les dades (maqueta 16) ----------

    /**
     * Escriu en CSV el consum de cada hora (amb la línia de la pinça 2 i els euros), cada vegada (amb l'aparell) o cada minut,
     * del període triat. Torna quantes files ha escrit. Cada minut es llegeix per setmanes: d'un any són mig milió de files.
     */
    fun exporta(tipus: TipusExport, periode: PeriodeExport, sortida: Appendable, capcalera: List<String>, textCom: Map<Com, String>): Int {
        val avui = LocalDate.now(zona)
        val desDe = when (periode) {
            PeriodeExport.MES -> iniciDia(avui.withDayOfMonth(1))
            PeriodeExport.ANY -> iniciDia(avui.withDayOfYear(1))
            PeriodeExport.TOT -> bd.primerTs() ?: ara()
        }
        val finsA = ara() + 60
        // La marca d'UTF-8 al davant: sense, l'Excel no llegeix bé els accents dels noms
        sortida.append('\uFEFF')
        val csv = Csv(sortida)
        csv.fila(*capcalera.toTypedArray())
        val dia = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val hora = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        fun local(ts: Long) = Instant.ofEpochSecond(ts).atZone(zona)
        var n = 0
        when (tipus) {
            TipusExport.HORES -> {
                val p = Preus(desDe, finsA)
                for (h in perHores(desDe, finsA, fonsWh())) {
                    val t = local(h.ts)
                    csv.fila(t.format(dia), t.format(hora), h.total, if (config.teLinia) h.linia else null, h.total * p.de(h.ts), h.nuvol.takeIf { it > 0 })
                    n++
                }
            }
            TipusExport.VEGADES -> {
                val noms = bd.aparells().associate { it.id to it.nom }
                for (v in bd.vegades(desDe, finsA)) {
                    val t = local(v.ts)
                    csv.fila(
                        t.format(dia), t.format(hora), local(v.fi + 60).format(hora), v.minuts, v.w.toInt(), v.kwh,
                        v.aparellId?.let(noms::get), v.com?.let(textCom::get), if (v.canal == 1) config.nomLinia else null,
                    )
                    n++
                }
            }
            TipusExport.MINUTS -> {
                var s = desDe
                while (s < finsA) {
                    val e = minOf(s + 7 * 86400, finsA)
                    for (m in bd.serie(s, e)) {
                        val t = local(m.ts)
                        csv.fila(t.format(dia), t.format(hora), m.wTotal.toInt(), if (config.teLinia) m.wLinia.toInt() else null, if (m.aprox) 1 else 0)
                        n++
                    }
                    s = e
                }
            }
        }
        return n
    }

    // ---------- Entrenament ----------

    /** L'aparell que es diu com la línia de la segona pinça es mesura directament: no s'entrena ni s'endevina. */
    private fun esLinia(a: Aparell) = config.teLinia && a.nom.equals(config.nomLinia, ignoreCase = true)

    /** Un aparell que és a la línia de la segona pinça (maqueta 12): s'aprèn de la línia, i no es busca a la resta. */
    private fun aLaLinia(a: Aparell) = config.teLinia && a.linia && !esLinia(a)

    /**
     * Els aparells que poden ser un consum de la resta de la casa: per aprendre'ls, endevinar-los i respondre les
     * preguntes. Ni la línia de la segona pinça ni els seus aparells (es mesuren), ni els que estan sempre encesos (van
     * dins del fons).
     */
    fun aparellsAEntrenar() = bd.aparells().filterNot { esLinia(it) || aLaLinia(it) || it.funciona == Funcionament.SEMPRE }

    /** Els aparells de la línia de la segona pinça que poden ser un consum. */
    fun aparellsLinia() = bd.aparells().filter { aLaLinia(it) && it.funciona != Funcionament.SEMPRE }

    /**
     * Posa un aparell a la línia de la pinça 2, o el treu. En treure'l, fora el que n'havia après de la línia: si no, el
     * que era d'un altre aparell (marcat per error) explicaria consums de la resta de la casa.
     */
    @Synchronized
    fun posaALinia(a: Aparell, si: Boolean) {
        bd.desaAparell(a.copy(linia = si))
        if (!si) bd.esborraMostres(a.id, "linia")
        haCanviat()
    }

    /** Les referències dels aparells de la línia, apreses de la línia. */
    fun referenciesLinia(): Map<Long, Referencia> {
        val aparells = aparellsLinia()
        val sols = aparells.filter { it.funciona == Funcionament.SOL }.map { it.id }.toSet()
        return bd.mostres().filter { m -> aparells.any { it.id == m.aparellId } }.groupBy { it.aparellId }
            .mapNotNull { (id, m) -> Referencia.de(id, m, ambInterval = id in sols) }.associateBy { it.aparellId }
    }

    /**
     * Aprèn sola els aparells de la línia (maqueta 12): cada episodi acabat de la línia, de 10 minuts o més, és una mostra
     * de l'aparell de la línia que s'hi assembla clarament ([triaLinia]). Ningú més no hi gasta, i per això no cal
     * preguntar-ho. Com a molt cada mitja hora.
     * D'un en un (la sincronització i els canvis de l'usuari): si no, un episodi faria dues mostres.
     */
    @Synchronized
    fun aprenLinia(forca: Boolean = false) {
        if (!config.teLinia) return
        if (!forca && ara() - config.liniaMirada < 30 * 60) return
        config.liniaMirada = ara()
        val aparells = aparellsLinia()
        if (aparells.isEmpty()) return
        val minuts = bd.minutsW(linia = true, ara() - 8 * 86400, ara() + 60)
        val darrer = minuts.lastOrNull()?.ts ?: return
        val sols = aparells.filter { it.funciona == Funcionament.SOL }.map { it.id }.toSet()
        var refs = referenciesLinia()
        var noves = 0
        for (ep in Episodis.troba(minuts)) {
            // Només els que ja han acabat, els llargs i un sol cop cadascun
            if (ep.fi > darrer - 5 * 60 || ep.minuts < 10 || bd.hiHaMostra(ep.ts, "linia")) continue
            val a = triaLinia(ep, aparells, refs) ?: continue
            bd.desaMostra(a.id, ep.ts, ep.w, ep.durada, "linia", if (a.id in sols) ep.interval else null, ep.variacio, ep.arrencada)
            refs = referenciesLinia()
            noves++
        }
        if (noves > 0) haCanviat()
    }

    /**
     * Busca a la resta de la casa el que es repeteix sempre igual (maqueta 12) i que encara no se sap què és, per
     * preguntar-ho un cop. Amb l'última setmana de dades exactes, com a molt cada 6 hores. Els que ja s'han respost o
     * ignorat no es tornen a preguntar. Amb el mateix bloqueig que [responRepetit], perquè una resposta no torni com a
     * pendent.
     */
    @Synchronized
    fun actualitzaRepetits(forca: Boolean = false) {
        if (!forca && ara() - config.repetitsMirats < 6 * 3600) return
        config.repetitsMirats = ara()
        val minuts = bd.minutsW(linia = false, ara() - 7 * 86400, ara() + 60)
        // Amb menys d'un dia no es pot dir que res es repeteixi
        if (minuts.size < 24 * 60) return
        val e = explicador()
        val vistos = bd.repetits().filter { it.estat != EstatPregunta.PENDENT }
        val nous = Episodis.repetits(Episodis.troba(minuts)).map(Episodis::repetit).filter { r ->
            vistos.none { Episodis.mateix(it, r.w, r.minuts) } && e.explica(r.w).residu > 0.25 * r.w
        }.sortedByDescending { it.kwh }.take(3)
        bd.substitueixRepetits(nous)
    }

    fun repetitsPendents() = bd.repetits(EstatPregunta.PENDENT)

    /**
     * Respon què és una cosa que es repeteix: cada cop (els 12 més recents) passa a ser una mostra de l'aparell, amb la
     * seva durada i la seva firma. Si s'engega sol, amb l'interval de potència del grup. Torna false si ja estava resposta.
     */
    @Synchronized
    fun responRepetit(id: Long, aparellId: Long?, sol: Boolean = false): Desfer? {
        val r = bd.repetits(EstatPregunta.PENDENT).firstOrNull { it.id == id } ?: return null
        if (aparellId == null) {
            bd.responRepetit(id, EstatPregunta.IGNORADA, null)
            return Desfer(repetit = id)
        }
        val abans = bd.aparells().firstOrNull { it.id == aparellId } ?: return null
        var a = abans
        if (sol && a.funciona != Funcionament.SOL) {
            a = a.copy(funciona = Funcionament.SOL)
            bd.desaAparell(a)
        }
        val interval = if (a.funciona == Funcionament.SOL) r.wMin * 0.9 to r.wMax * 1.1 else null
        val mostres = r.cops.takeLast(12).mapNotNull { (ts, w, minuts) ->
            if (!bd.teMostra(aparellId, ts, "repetit")) bd.desaMostra(aparellId, ts, w, minuts * 60L, "repetit", interval, r.variacio, r.arrencada) else null
        }
        // Cada cop és d'aquest aparell (maqueta 13)
        val etiquetes = r.cops.map { (ts, _, minuts) -> bd.desaEtiqueta(Etiqueta(0, ts, ts + minuts * 60L - 1, aparellId)) }
        bd.responRepetit(id, EstatPregunta.RESPOSTA, aparellId)
        haCanviat()
        return Desfer(etiquetes, mostres, repetit = id, aparellAbans = abans.takeIf { it != a })
    }

    fun referencies(): Map<Long, Referencia> {
        val aparells = aparellsAEntrenar()
        val sols = aparells.filter { it.funciona == Funcionament.SOL }.map { it.id }.toSet()
        val refs = bd.mostres().filter { m -> aparells.any { it.id == m.aparellId } }.groupBy { it.aparellId }
            .mapNotNull { (id, m) -> Referencia.de(id, m, ambInterval = id in sols) }.associateBy { it.aparellId }
        // El compressor de la nevera, trobat sol a les dades: per a la nevera que s'engega sola i no té mostres
        val w = config.neveraW.takeIf { it > 0 } ?: return refs
        val nevera = listOf(Categoria.NEVERA, Categoria.CONGELADOR).firstNotNullOfOrNull { cat ->
            aparells.firstOrNull { it.categoria == cat && it.funciona == Funcionament.SOL && it.id !in refs }
        } ?: return refs
        return refs + (nevera.id to Referencia(nevera.id, w, 0, null, automatica = true))
    }

    // ---------- Les dades per hores del núvol (maqueta 11) ----------

    private val omplintNuvol = Mutex()

    /**
     * Omple el que falta amb el consum per hores del núvol: els dies sense totes les dades del Shelly (unes vacances de
     * més de 7 dies, o d'abans d'instal·lar l'app), d'avui cap enrere, fins a un any o fins que el núvol ja no en té (3
     * dies seguits sense res abans de la primera dada del Shelly). Una petició per dia i per pinça, a menys d'una per
     * segon, i com a molt [maxDies] per vegada: la vegada següent continua. Els dies de fa més d'una setmana ja mirats no
     * es tornen a demanar; els recents sí, perquè el núvol els va completant. Només les hores ja passades: l'hora d'ara
     * la fa el comptador. Torna quants dies ha omplert.
     */
    suspend fun omplNuvol(maxDies: Int = 40): Int = withContext(Dispatchers.IO) {
        val c = config
        if (!c.nuvolHores || !c.teNuvol) return@withContext 0
        omplintNuvol.withLock {
            val nuvol = ShellyNuvol(c.servidorNuvol, c.clauNuvol, c.idDispositiu)
            val avui = LocalDate.now(zona)
            // Fins a un any: els minuts se'n guarden 400 dies, i el resum dels dies que el núvol omple es torna a fer
            val limit = avui.minusDays(365)
            val primerExacte = bd.primerExacte()?.let { Instant.ofEpochSecond(it).atZone(zona).toLocalDate() }
            val mirats = c.nuvolMirats.toMutableSet()
            var demanats = 0
            var omplerts = 0
            var buitsSeguits = 0
            var dia = avui
            fun omple(canal: Int, hores: List<Pair<Long, Double>>, exactes: Map<Long, Double>) {
                val minuts = hores.filter { (ts, _) -> ts + 3600 <= ara() }
                    .flatMap { (ts, wh) -> minutsDelNuvol(ts, wh, exactes.filterKeys { it >= ts && it < ts + 3600 }) }
                bd.insereixNuvol(canal, minuts)
                // Si aquell dia ja tenia resum per hores, se'n torna a fer
                if (minuts.isNotEmpty()) refesResum(minuts.minOf { it.first }, minuts.maxOf { it.first } + 60)
            }
            suspend fun demana(canal: Int, d: LocalDate): List<Pair<Long, Double>> {
                if (demanats++ > 0) delay(1100)
                return nuvol.consumHores(canal, d)
            }
            try {
                while (dia.isAfter(limit) && demanats < maxDies && dia.toEpochDay() > c.nuvolSenseAbans) {
                    val vell = dia.isBefore(avui.minusDays(7))
                    val e = dia.toEpochDay()
                    val desDe = iniciDia(dia)
                    val finsA = minOf(iniciDia(dia.plusDays(1)), ara())
                    val exactes = bd.minutsExactes(0, desDe, finsA)
                    val complet = exactes.size >= (finsA - desDe) / 60 - 5
                    if (complet || (vell && e in mirats)) {
                        if (vell) mirats += e
                        dia = dia.minusDays(1)
                        continue
                    }
                    val hores = demana(0, dia)
                    if (hores.isEmpty()) {
                        if (vell) mirats += e
                        // Abans de la primera dada del Shelly, 14 dies seguits sense res: el núvol ja no en té més (fins a la
                        // 1.1.2 eren 3, i un cap de setmana sense wifi tallava l'historial per sempre)
                        if ((primerExacte == null || dia.isBefore(primerExacte)) && ++buitsSeguits >= 14) {
                            c.nuvolSenseAbans = e
                            break
                        }
                    } else {
                        buitsSeguits = 0
                        omple(0, hores, exactes)
                        if (c.teLinia) omple(1, demana(1, dia), bd.minutsExactes(1, desDe, finsA))
                        if (vell) mirats += e
                        omplerts++
                    }
                    dia = dia.minusDays(1)
                }
            } finally {
                c.nuvolMirats = mirats.filter { it > limit.toEpochDay() }.toSet()
                c.nuvolDarrer = ara()
            }
            omplerts
        }
    }

    /** Apaga l'opció: fora els minuts del núvol per hores, i la vegada que s'encengui es tornarà a mirar tot. */
    fun oblidaNuvol() {
        bd.esborraNuvol()
        // El resum per hores dels dies que en tenien, sense el núvol
        bd.primerMinut()?.let { refesResum(it, config.resumFins) }
        config.nuvolMirats = emptySet()
        config.nuvolSenseAbans = Long.MIN_VALUE
    }

    fun resumNuvol() = bd.resumNuvol()

    /** Busca el compressor de la nevera a les dades de l'última setmana, com a molt un cop al dia. */
    fun actualitzaNevera() {
        // Un cop al dia; i de seguida si encara no se sap quina part del temps funciona (des de la 0.26)
        if (ara() - config.neveraMirada < 20 * 3600 && !(config.neveraW > 0 && config.neveraCicle == 0.0)) return
        val linia = config.teLinia
        val punts = bd.serie(ara() - 7 * 86400, ara() + 60).filterNot { it.aprox }
            .map { it.ts to (it.wTotal - if (linia) it.wLinia else 0.0) }
        config.neveraW = potenciaCompressor(punts, zona) ?: 0.0
        config.neveraCicle = if (config.neveraW > 0) cicleCompressor(punts, zona) ?: -1.0 else 0.0
        config.neveraMirada = ara()
    }

    /**
     * Els aparells que s'engeguen sols aprenen l'interval de potència de l'episodi de cada pregunta resposta, quan
     * ja n'hi ha les dades exactes (a casa). Des de fora, o si l'episodi va ser curt, es queda amb el salt.
     */
    fun completaIntervals() {
        val sols = bd.aparells().filter { it.funciona == Funcionament.SOL }.map { it.id }.toSet()
        if (sols.isEmpty()) return
        val fons = fonsW() ?: return
        val linia = config.teLinia
        for (m in bd.mostres()) {
            if (m.aparellId !in sols || m.font != "pregunta" || m.wMin != null) continue
            // L'episodi ha d'haver tingut temps d'acabar, i les dades per minut només duren una setmana al Shelly
            if (m.ts > ara() - 20 * 60 || m.ts < ara() - 8 * 86400) continue
            val serie = bd.serie(m.ts, m.ts + 3 * 3600)
            if (serie.isEmpty() || serie.any { it.aprox }) continue
            val exces = serie.map { it.wTotal - (if (linia) it.wLinia else 0.0) - fons }
            intervalEpisodi(exces, m.dp)?.let { (lo, hi) ->
                bd.desaInterval(m.id, lo, hi)
                haCanviat()
            }
        }
    }

    fun explicador() = Explicador(referencies().values.toList())

    /**
     * Una mostra ensenyada en directe. En apagar-lo ([durada]: des que es va encendre), aquella estona és d'aquest aparell
     * (maqueta 13).
     */
    @Synchronized
    fun desaMostra(aparellId: Long, dp: Double, durada: Long?): Desfer {
        // Un sol «ara»: l'etiqueta i la mostra han de quedar lligades (la mostra comença una durada abans)
        val ara = ara()
        val mostra = bd.desaMostra(aparellId, ara, dp, durada, "directe")
        val etiqueta = if (durada != null) bd.desaEtiqueta(Etiqueta(0, ara - durada, ara - durada, aparellId)) else null
        haCanviat()
        return Desfer(listOfNotNull(etiqueta), listOf(mostra))
    }

    /** Torna a començar: fora les mostres i el que l'usuari n'havia dit. */
    @Synchronized
    fun tornaAEntrenar(aparellId: Long) {
        bd.esborraMostres(aparellId)
        bd.esborraEtiquetes(aparellId)
        haCanviat()
    }

    fun preguntesPendents() = bd.preguntes(EstatPregunta.PENDENT)

    fun pregunta(id: Long) = bd.pregunta(id)

    /**
     * Respon una pregunta. Si és un aparell, el salt de la pregunta passa a ser una mostra seva. Torna false si ja
     * estava resposta (des de la notificació i des de l'app, o amb dos tocs): no es fa una mostra repetida.
     */
    @Synchronized
    fun respon(preguntaId: Long, aparellId: Long?, sol: Boolean = false): Desfer? {
        val p = bd.pregunta(preguntaId)?.takeIf { it.estat == EstatPregunta.PENDENT } ?: return null
        if (aparellId == null) {
            bd.respon(preguntaId, EstatPregunta.IGNORADA, null)
            return Desfer(pregunta = preguntaId)
        }
        // «S'engega sol»: a partir d'ara se n'aprèn l'interval i no s'avisa quan s'engegui
        val abans = if (sol) bd.aparells().firstOrNull { it.id == aparellId && it.funciona != Funcionament.SOL } else null
        abans?.let { bd.desaAparell(it.copy(funciona = Funcionament.SOL)) }
        val mostra = bd.desaMostra(aparellId, p.ts, p.dp, null, "pregunta")
        bd.respon(preguntaId, EstatPregunta.RESPOSTA, aparellId)
        completaIntervals()
        // La vegada de la pregunta és d'aquest aparell (maqueta 13)
        val etiqueta = bd.desaEtiqueta(Etiqueta(0, p.ts, p.ts, aparellId))
        haCanviat()
        return Desfer(listOf(etiqueta), listOf(mostra), pregunta = preguntaId, aparellAbans = abans)
    }

    /**
     * Mira si ara hi ha un consum que les referències no expliquen: si passa del llindar que ha triat l'usuari durant
     * els minuts que ha triat, en fa una pregunta (amb avís). [restaW] és el total menys la línia.
     */
    @Synchronized
    fun comprovaInesperat(restaW: Double, pausa: Long = DetectorInesperat.PAUSA_S): Pregunta? {
        val c = config
        if (!c.avisInesperat) return null
        val fons = fonsW() ?: return null
        val residu = explicador().explica(restaW - fons).residu
        val d = DetectorInesperat(c.llindarInesperat, c.minutsInesperat, c.inesperatDes, c.inesperatAlt, c.inesperatPregunta != 0L, c.darrerAvisInesperat)
        val avisa = d.mira(ara(), residu, pausa)
        c.inesperatMirat = ara()
        return desaDetector(d, if (avisa) d.des to residu else null)
    }

    /**
     * Des del treball de fons (1.1.3): amb els minuts exactes nous que ha portat la sincronització, minut a minut (com
     * [simulaAvisos]); si no n'hi ha (fora de casa), amb la lectura d'ara ([restaAra]) i una pausa més llarga que el període
     * del treball de fons. Abans, cada crida, cada 15 minuts o més, tornava a començar l'episodi i no s'avisava mai.
     */
    @Synchronized
    fun comprovaInesperatDeFons(restaAra: Double?): Pregunta? {
        val c = config
        if (!c.avisInesperat) return null
        val fons = fonsW() ?: return null
        val minuts = bd.serie(maxOf(c.inesperatMirat + 1, ara() - 3 * 3600), ara() + 60).filterNot { it.aprox }
        if (minuts.isEmpty()) return restaAra?.let { comprovaInesperat(it, pausa = 45 * 60L) }
        val e = explicador()
        val linia = c.teLinia
        val d = DetectorInesperat(c.llindarInesperat, c.minutsInesperat, c.inesperatDes, c.inesperatAlt, c.inesperatPregunta != 0L, c.darrerAvisInesperat)
        var avis: Pair<Long, Double>? = null
        for (p in minuts) {
            val r = e.explica(max(p.wTotal - (if (linia) p.wLinia else 0.0), 0.0) - fons).residu
            if (d.mira(p.ts, r)) avis = d.des to r
        }
        c.inesperatMirat = minuts.last().ts
        return desaDetector(d, avis)
    }

    /** Desa l'estat del detector i, si cal avisar, en fa la pregunta. */
    private fun desaDetector(d: DetectorInesperat, avis: Pair<Long, Double>?): Pregunta? {
        val c = config
        c.inesperatDes = d.des
        c.inesperatAlt = d.alt
        c.darrerAvisInesperat = d.darrerAvis
        if (!d.avisat) c.inesperatPregunta = 0
        if (avis == null) return null
        val id = bd.creaPregunta(avis.first, avis.second)
        c.inesperatPregunta = id
        return bd.pregunta(id)
    }

    /**
     * Quins avisos de consum inesperat hauria donat la setmana passada amb un llindar i uns minuts, amb el que l'app
     * sap ara: quan va començar cada episodi i quants W sense explicar hi havia. Null si encara no hi ha un dia de
     * dades exactes.
     */
    fun simulaAvisos(llindarW: Double, minuts: Int): List<Pair<Long, Double>>? {
        val serie = bd.serie(ara() - 7 * 86400, ara() + 60).filterNot { it.aprox }
        if (serie.size < 24 * 60) return null
        val fons = fonsW() ?: return null
        val e = explicador()
        val d = DetectorInesperat(llindarW, minuts)
        val linia = config.teLinia
        return serie.mapNotNull { p ->
            val r = e.explica(max(p.wTotal - (if (linia) p.wLinia else 0.0), 0.0) - fons).residu
            if (d.mira(p.ts, r)) d.des to r else null
        }
    }

    /**
     * Els moments punta d'un període de calendari: el minut de més consum del dia, o, per a la setmana i el mes, el de
     * cada dia, i els [quants] més alts. De l'any, cap: seria llegir-ne tots els minuts.
     */
    fun pics(periode: Periode, quants: Int = 3, dia: LocalDate = LocalDate.now(zona)): List<Pic> {
        if (periode == Periode.ANY) return emptyList()
        val desDe = iniciDia(iniciPeriode(periode, dia))
        // Els minuts que no són del Shelly (del núvol) són mitjanes: no poden ser un moment punta
        val serie = bd.serie(desDe, minOf(iniciDia(fiPeriode(periode, dia)), ara() + 60)).filterNot { it.aprox }
        if (serie.isEmpty()) return emptyList()
        val maxims = if (periode == Periode.DIA) listOf(serie.maxBy { it.wTotal })
        else serie.groupBy { Instant.ofEpochSecond(it.ts).atZone(zona).toLocalDate() }.values.map { dia -> dia.maxBy { it.wTotal } }
        return maxims.sortedByDescending { it.wTotal }.take(if (periode == Periode.DIA) 1 else quants)
            .map { Pic(it.ts, it.wTotal, bd.pmax(it.ts)) }
    }

    /**
     * Què hi havia encès en un pic (maqueta 16), amb el mateix criteri que el rànquing: les vegades que passaven per aquell
     * minut, cadascuna amb el seu aparell, o de cap (i llavors se'n pot dir què era); el fons, la nevera i els petits consums;
     * i la línia de la pinça 2, amb els seus aparells. Sense noms per la potència sola: si una vegada no en té al rànquing,
     * aquí tampoc.
     */
    fun analitzaPics(pics: List<Pic>): List<AnalisiPic> {
        if (pics.isEmpty()) return emptyList()
        // El que és comú a tots els pics es llegeix un sol cop
        val fons = fonsW() ?: 0.0
        val tots = aparells()
        val perId = tots.associateBy { it.id }
        val casa = aparellsAEntrenar()
        val linia = aparellsLinia()
        val refs = referencies() + referenciesLinia()
        val nevera = neveraAutomatica(tots)
        val neveraW = config.neveraW.takeIf { it > 0 && nevera != null }
        val iniciLinia = iniciLinia()
        val nos = bd.etiquetes().filter { it.no }
        return pics.map { pic ->
            val minut = bd.serie(pic.ts, pic.ts + 60).firstOrNull()
            val wLinia = if (config.teLinia) (minut?.wLinia ?: 0.0).coerceAtLeast(0.0) else 0.0
            val resta = max(pic.w - wLinia, 0.0)
            // Les vegades que passaven pel minut del pic
            val vegades = bd.vegades(pic.ts, pic.ts + 60).filter { it.ts <= pic.ts }
            val elements = mutableListOf<ElementPic>()
            val sempre = min(fons, resta)
            if (sempre > 0) elements += SempreEncesPic(sempre)
            fun afegeix(e: EncesAra, aLaLinia: Boolean) {
                val a = e.aparellId?.let(perId::get)
                val v = e.vegada
                elements += when {
                    e.tipus == TipusEnces.APARELL && a != null -> AparellPic(a, e.w, e.desDe, e.com, linia = aLaLinia)
                    e.tipus == TipusEnces.NEVERA && nevera != null -> AparellPic(nevera, e.w, null, null, compressor = true)
                    e.tipus == TipusEnces.PETITS -> if (aLaLinia) LiniaPic(e.w, altres = true) else PetitsPic(e.w)
                    v != null -> {
                        val candidats = when {
                            v.canal == 1 -> linia
                            abansDeLaLinia(v, iniciLinia) -> casa + linia
                            else -> casa
                        }.filter { a -> nos.none { it.aparellId == a.id && encavalca(v, it) > 0 } }
                        DesconegutPic(v, e.w, endevina(v.w, candidats, refs, candidats.size, v.minuts.toDouble(), v.forma))
                    }
                    else -> SenseIdentificarPic(e.w)
                }
            }
            if (wLinia > 0) {
                if (linia.isEmpty()) elements += LiniaPic(wLinia)
                else encesDelCanal(pic.ts, 1, wLinia, vegades, linia, refs, perPotencia = false).forEach { afegeix(it, aLaLinia = true) }
            }
            encesDelCanal(pic.ts, 0, resta - sempre, vegades, casa, refs, neveraW, perPotencia = false).forEach { afegeix(it, aLaLinia = false) }
            // La gràfica: des d'una mica abans de la primera vegada (com a molt 90 minuts abans del pic) fins a mitja hora després
            val primer = vegades.minOfOrNull { it.ts } ?: pic.ts
            val inici = max(minOf(primer, pic.ts - 30 * 60) - 15 * 60, pic.ts - 105 * 60L)
            AnalisiPic(pic, elements.sortedByDescending { it.w }, vegades, bd.serie(inici, pic.ts + 30 * 60))
        }
    }

    /**
     * Una estona passada, per ensenyar-hi un aparell: la potència de l'estona (amb un quart d'hora més a cada costat,
     * per a la gràfica) i els trams que hi ha. Si una part és aproximada (des de fora), no s'hi busca res.
     */
    fun estona(desDe: Long, finsA: Long): Estona {
        val serie = bd.serie(desDe - 15 * 60, finsA + 15 * 60)
        val dins = serie.filter { it.ts in desDe until finsA }
        val aproximada = dins.any { it.aprox }
        val linia = config.teLinia
        val trams = if (aproximada) emptyList()
        else EstonaPassada.trams(dins.map { it.ts to max(it.wTotal - (if (linia) it.wLinia else 0.0), 0.0) })
        return Estona(desDe, finsA, serie, trams, aproximada)
    }

    /**
     * Els trams que l'usuari ha marcat d'una estona passada, com a mostres de l'aparell. Si l'engega l'usuari, els salts
     * en encendre's i en apagar-se (com en directe); si s'engega sol, l'interval de cada tram. Dues vegades la mateixa
     * estona no fan mostres repetides.
     */
    @Synchronized
    fun ensenyaEstona(aparellId: Long, trams: List<TramPassat>): Desfer? {
        val a = bd.aparells().firstOrNull { it.id == aparellId } ?: return null
        val mostres = mutableListOf<Long>()
        val etiquetes = mutableListOf<Long>()
        for (t in trams) {
            val durada = t.fi - t.ts + 60
            if (a.funciona == Funcionament.SOL) {
                if (bd.teMostra(aparellId, t.ts, "passat")) continue
                val id = bd.desaMostra(aparellId, t.ts, mediana(t.exces), durada, "passat")
                val s = t.exces.sorted()
                bd.desaInterval(id, s[s.size / 10] * 0.8, s.last() * 1.2)
                mostres += id
            } else {
                val (puja, baixa) = t.mostres
                if (!bd.teMostra(aparellId, t.ts, "passat")) mostres += bd.desaMostra(aparellId, t.ts, puja, durada, "passat")
                if (!bd.teMostra(aparellId, t.fi, "passat")) mostres += bd.desaMostra(aparellId, t.fi, baixa, durada, "passat")
            }
            // Aquesta estona és d'aquest aparell: el seu consum hi va (maqueta 13)
            etiquetes += bd.desaEtiqueta(Etiqueta(0, t.ts, t.fi + 59, aparellId))
        }
        haCanviat()
        return Desfer(etiquetes, mostres)
    }
}
