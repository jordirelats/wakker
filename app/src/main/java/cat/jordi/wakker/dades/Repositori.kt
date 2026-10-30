package cat.jordi.wakker.dades

import android.content.Context
import androidx.annotation.StringRes
import cat.jordi.wakker.R
import cat.jordi.wakker.endolls.InfoEndoll
import cat.jordi.wakker.endolls.Klap
import cat.jordi.wakker.endolls.LectorEndoll
import cat.jordi.wakker.endolls.LecturaEndoll
import cat.jordi.wakker.endolls.ShellyEndoll
import cat.jordi.wakker.endolls.Tapo
import cat.jordi.wakker.shelly.Lectura
import cat.jordi.wakker.shelly.Origen
import cat.jordi.wakker.shelly.ShellyLocal
import cat.jordi.wakker.shelly.ShellyNuvol
import cat.jordi.wakker.shelly.normalitzaMac
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
data class Fitxa(
    val aparellId: Long, val kwhMes: Double, val copsMes: Int, val ultimes: List<Vegada>, val ensenyats: List<Ensenyat> = emptyList(),
    /** L'endoll que té posat, si en té (maqueta 19). */
    val endoll: EndollFitxa? = null,
)

/** L'endoll d'un aparell a la fitxa (maqueta 19): quin és, des de quan, què n'ha après l'app i el que ha mesurat. */
data class EndollFitxa(
    val endoll: Endoll, val periode: PeriodeEndoll, val apres: QueNHaApres?, val mesura: MesuraEndoll?, val lectura: LecturaEndoll?,
    /** El que l'endoll ha mesurat aquest mes (kWh), de les seves hores. */
    val kwhMes: Double,
)

/** Un endoll que ja es pot moure (maqueta 19): l'app encerta sola el seu aparell, i a quins aparells podria anar. */
data class PropostaMoure(val endoll: Endoll, val aparell: Aparell, val apres: QueNHaApres, val suggerits: List<Aparell>)

/** D'on ve una cosa ensenyada: una estona passada, en directe, una resposta (a «Què era?», a l'avís, a un pic) o el que es repeteix. */
enum class OrigenEnsenyat { ESTONA, DIRECTE, VEGADA, PREGUNTA, PIC, REPETIT, RESPOSTA, SESSIO }

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
    /** Una estona d'inici a fi desada: desfer-ho la torna a deixar per revisar (maqueta 21). */
    val sessio: SessioDesada? = null,
)

/** Una estona d'inici a fi tal com era a la configuració abans de desar-la. */
data class SessioDesada(val aparellId: Long, val inici: Long, val fi: Long, val base: Double)

/**
 * Les vegades que encara duraven a l'últim minut del Shelly (maqueta 15), si aquest minut és de fa poc ([fresques]: a casa,
 * amb les dades al dia); i la nevera que s'engega sola, amb el seu compressor.
 */
data class Obertes(val fresques: Boolean, val vegades: List<Vegada>, val nevera: Aparell?, val neveraW: Double?)

/**
 * Una vegada grossa que encara no té nom (maqueta 15): els euros, els aparells que s'hi poden assemblar (del més semblant al
 * que menys) i la potència minut a minut de la resta de la casa, amb un quart d'hora a cada costat, per a la gràfica.
 */
data class VegadaSenseNom(
    val vegada: Vegada, val euros: Double, val candidats: List<Aparell>, val punts: List<Pair<Long, Double>>,
    /** L'aparell que l'app li hauria donat, però el seu endoll era apagat (maqueta 19). */
    val noEraEndoll: Aparell? = null,
)

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
            // El que els endolls confirmen, com a mostres (maqueta 19)
            aprenDelsEndolls()
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
        // Només amb endolls (maqueta 20): el que han gastat avui els endolls
        if (config.modeEndolls) resum(Periode.DIA).let { return Avui(it.total, it.euros.consum, false) }
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
        if (config.modeEndolls) return resumEndolls(periode, dia)
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

    /**
     * L'energia de cada endoll per hores entre dos instants, amb l'aparell que tenia posat aquella hora: les hores de l'endoll
     * i, de les que encara no té (l'hora d'ara, o un Shelly sense prou minuts), la suma dels seus minuts.
     */
    private fun horesEndolls(desDe: Long, finsA: Long): List<Triple<Long, Long, Double>> {
        val periodes = bd.periodesEndoll()
        val sortida = mutableListOf<Triple<Long, Long, Double>>()
        for (e in bd.endolls()) {
            val hores = bd.horesEndoll(e.id, desDe, finsA).toMutableList()
            val desDeMinuts = max(desDe, (hores.maxOfOrNull { it.first } ?: (desDe - 3600)) + 3600)
            if (desDeMinuts < finsA) {
                bd.minutsEndoll(e.id, desDeMinuts, finsA - 1).groupBy { iniciHora(it.ts, zona) }
                    .forEach { (h, m) -> hores += h to m.sumOf { it.wh } }
            }
            for ((h, wh) in hores) {
                val p = periodes.lastOrNull { it.endollId == e.id && it.cobreix(h) } ?: continue
                sortida += Triple(h, p.aparellId, wh)
            }
        }
        return sortida
    }

    /**
     * El resum d'un període només amb endolls (maqueta 20): cada hora, el que han gastat els endolls (tot va al «sense
     * identificar» de l'hora, que és el que hi ha), les barres per aparells i el rànquing amb els aparells que tenen endoll.
     * Sense fixos: són de la casa, i aquí no es veu la casa.
     */
    private fun resumEndolls(periode: Periode, dia: LocalDate): Resum {
        val avui = LocalDate.now(zona)
        val primer = iniciPeriode(periode, dia)
        val fi = fiPeriode(periode, dia)
        val desDe = iniciDia(primer)
        val finsA = minOf(iniciDia(fi), ara() + 60)
        val dades = if (finsA > desDe) horesEndolls(desDe, finsA) else emptyList()
        val perHora = dades.groupBy { it.first }
        val llista = perHora.map { (h, l) -> Hora(h, 0.0, 0.0, l.sumOf { it.third } / 1000) }.sortedBy { it.ts }
        fun perAparell(t0: Long, t1: Long) = dades.filter { it.first in t0 until t1 }.groupBy { it.second }.mapValues { (_, l) -> l.sumOf { it.third } / 1000 }
        fun suma(t0: Long, t1: Long) = Hora(t0, 0.0, 0.0, llista.filter { it.ts in t0 until t1 }.sumOf { it.desconegut })
        val barres = when (periode) {
            Periode.DIA -> (desDe until iniciDia(fi) step 3600L).map { ts ->
                Barra("${Instant.ofEpochSecond(ts).atZone(zona).hour}", suma(ts, ts + 3600), perAparell(ts, ts + 3600))
            }
            Periode.SETMANA, Periode.MES -> generateSequence(primer) { it.plusDays(1) }.takeWhile { it.isBefore(fi) }.map { d ->
                val etiqueta = if (periode == Periode.SETMANA) d.dayOfWeek.getDisplayName(TextStyle.SHORT, Idioma.locale).removeSuffix(".")
                else d.dayOfMonth.toString()
                Barra(etiqueta, suma(iniciDia(d), iniciDia(d.plusDays(1))), perAparell(iniciDia(d), iniciDia(d.plusDays(1))))
            }.toList()
            Periode.ANY -> (1..12).map { m ->
                val mes = primer.withMonth(m)
                Barra(
                    mes.month.getDisplayName(TextStyle.NARROW_STANDALONE, Idioma.locale).uppercase(Idioma.locale),
                    suma(iniciDia(mes), iniciDia(mes.plusMonths(1))), perAparell(iniciDia(mes), iniciDia(mes.plusMonths(1))),
                )
            }
        }
        val preus = Preus(desDe, finsA)
        val euros = preus.euros(llista, 0.0)
        val primerDades = bd.endolls().mapNotNull { bd.primeraHoraEndoll(it.id) ?: bd.primerMinutEndoll(it.id) }.minOrNull()
            ?.let { Instant.ofEpochSecond(it).atZone(zona).toLocalDate() }
        val anterior = mou(periode, primer, -1)
        val actual = !avui.isBefore(primer) && avui.isBefore(fi)
        val finsAnterior = if (actual) minOf(iniciDia(anterior) + (ara() - desDe), desDe) else desDe
        val anteriorKwh = if (primerDades != null && !anterior.isBefore(primerDades))
            horesEndolls(iniciDia(anterior), finsAnterior).sumOf { it.third } / 1000 else null
        val aparells = bd.aparells().associateBy { it.id }
        val files = dades.groupBy { it.second }.mapNotNull { (id, l) ->
            val a = aparells[id] ?: return@mapNotNull null
            FilaRanquing(a, a.nom, l.sumOf { it.third } / 1000, l.sumOf { it.third / 1000 * preus.de(it.first) }, 0, 0, false, endoll = true)
        }.filter { it.kwh >= 0.005 }.sortedByDescending { it.kwh }
        val total = llista.sumOf { it.desconegut }
        val rq = Ranquing(files, total, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        return Resum(barres, 0.0, 0.0, total, null, euros, false, primer, fi, actual, anteriorKwh, primerDades, rq)
    }

    /** El consum d'aquest mes, des del dia 1, amb els fixos dels dies que han passat. */
    fun resumMes(): Resum {
        if (config.modeEndolls) return resumEndolls(Periode.MES, LocalDate.now(zona))
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
        class Acc(var kwh: Double = 0.0, var euros: Double = 0.0, var cops: Int = 0, var ensenyats: Int = 0, val vegades: MutableList<Triple<Long, Long, Double>> = mutableListOf())
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
            x.vegades += Triple(v.ts, v.fi + 60, kwh)
            if (v.ts >= desDe) x.cops++
            if (v.com == Com.ENSENYAT && v.ts >= desDe) x.ensenyats++
        }
        // El que no té el detall de cada minut: de la resta de la casa i de la línia
        val liniaAprox = bd.liniaAproximada(desDe, finsA)
        val senseDetall = bd.senseDetall(desDe, finsA, fonsWh) + liniaAprox
        val preuResta = if (desconegut > 0.01) euros.desconegut / desconegut else p.de(desDe)
        // Els aparells amb endoll (maqueta 19): el que gasten és el que l'endoll ha mesurat, no les seves vegades. El que
        // l'endoll mesura de més (les bombes del rentaplats, l'espera de la tele) era als petits consums o al sempre encès
        val ambEndoll = mutableSetOf<Long>()
        var extraCasa = 0.0
        var extraLinia = 0.0
        for (pe in bd.periodesEndoll()) {
            val a = perId[pe.aparellId] ?: continue
            val d = max(desDe, pe.desDe)
            val f = min(finsA, pe.finsA ?: finsA)
            if (d >= f) continue
            val hores = bd.horesEndoll(pe.endollId, d, f)
            if (hores.isEmpty()) continue
            val presents = hores.map { it.first }.toHashSet()
            val mesurat = hores.sumOf { it.second } / 1000
            val x = acc.getOrPut(a.id) { Acc() }
            // La part de cada vegada que cau en hores que l'endoll ha mesurat de debò: aquella la substitueix l'endoll
            val dins = x.vegades.sumOf { (ts, fi, kwh) -> kwh * partEnHores(ts, fi, presents) }
            x.kwh += mesurat - dins
            x.euros = x.kwh * preuResta
            when {
                // L'endoll diu menys que les vegades que té dins: la diferència no era seva, torna a «sense identificar»
                mesurat < dins -> { senseId += dins - mesurat; senseIdEuros += (dins - mesurat) * preuResta }
                aLaLinia(a) -> extraLinia += mesurat - dins
                else -> extraCasa += mesurat - dins
            }
            ambEndoll += a.id
        }
        // Un aparell amb endoll que avui només ha gastat l'espera (17 Wh) no surt com a «0,0 kWh»
        val files = acc.filter { (id, x) -> id !in ambEndoll || x.kwh >= 0.01 }.map { (id, x) ->
            val a = perId.getValue(id)
            FilaRanquing(a, a.nom, x.kwh, x.euros, x.cops, x.ensenyats, aLaLinia(a), endoll = id in ambEndoll)
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
        var liniaAltres = if (ambAparells) max(linia - liniaAssignada - liniaAprox, 0.0) else 0.0
        val preuLinia = if (linia > 0.01) euros.linia / linia else preuResta
        var petits = max(desconegut - ep0 - nevera - (senseDetall - liniaAprox), 0.0)
        var detall = if (ambAparells) senseDetall else senseDetall - liniaAprox
        var sempreNet = sempre
        var sempreEuros = euros.sempre
        // El que els endolls han mesurat de més era en altres calaixos: a la línia, a «altres de la línia»; a la casa, als petits
        // consums, després al sempre encès i al que no té detall; si encara en sobra, es retalla dels aparells amb endoll (la
        // suma segueix sent el total)
        var sobra = extraCasa
        run { val d = min(liniaAltres, extraLinia); liniaAltres -= d; sobra += extraLinia - d }
        run { val d = min(petits, sobra); petits -= d; sobra -= d }
        run { val d = min(sempreNet, sobra); if (sempreNet > 0.001) sempreEuros *= (sempreNet - d) / sempreNet; sempreNet -= d; sobra -= d }
        run { val d = min(detall, sobra); detall -= d; sobra -= d }
        if (sobra > 0.001) {
            val totEndoll = files.filter { it.endoll }.sumOf { it.kwh }
            if (totEndoll > 0.001) {
                val f = (1 - sobra / totEndoll).coerceAtLeast(0.0)
                files.replaceAll { if (it.endoll) it.copy(kwh = it.kwh * f, euros = it.euros * f) else it }
            }
        }
        return Ranquing(
            files.sortedByDescending { it.kwh }, sempre + linia + desconegut, sempreNet, sempreEuros, petits, petits * preuResta,
            senseId, senseIdEuros, liniaAltres, liniaAltres * preuLinia, detall, detall * preuResta,
        )
    }

    /** Quina part de l'estona [ts, fi) cau dins de les hores (l'inici de cada hora local) de [presents]. */
    private fun partEnHores(ts: Long, fi: Long, presents: Set<Long>): Double {
        if (fi <= ts) return 0.0
        var dins = 0L
        var h = iniciHora(ts, zona)
        while (h < fi) {
            if (h in presents) dins += min(fi, h + 3600) - max(ts, h)
            h += 3600
        }
        return dins.toDouble() / (fi - ts)
    }

    /** Els aparells que tenen un endoll posat ara (maqueta 19). */
    private fun aparellsAmbEndoll(): Set<Long> = bd.periodesEndoll().filter { it.finsA == null }.map { it.aparellId }.toSet()

    /** La nevera o el congelador que s'engega sol i no té mostres: el compressor trobat a les dades li fa de referència. */
    private fun neveraAutomatica(aparells: List<Aparell>): Aparell? {
        if (config.neveraW <= 0) return null
        val ambMostres = bd.aparellsAmbMostres()
        // Una nevera amb endoll ja es mesura: el compressor trobat sol no li fa falta (sortiria dues vegades)
        val ambEndoll = aparellsAmbEndoll()
        return listOf(Categoria.NEVERA, Categoria.CONGELADOR).firstNotNullOfOrNull { cat ->
            aparells.firstOrNull { it.categoria == cat && it.funciona == Funcionament.SOL && it.id !in ambMostres && it.id !in ambEndoll && !aLaLinia(it) }
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
        val sessions = bd.etiquetes().filter { it.aparellId == id && it.sessio != null }
        bd.esborraAparell(id)
        bd.esborraEtiquetes(id)
        // El seu endoll, si en tenia, queda sense aparell (maqueta 19)
        bd.treuEndollsDe(id, ara())
        if (config.sessioAparell == id) oblidaSessio()
        haCanviat()
        rebusca(sessions)
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

    /** Des d'on cal tornar a repartir: els minuts nous d'un endoll només toquen les vegades d'aquells dies (maqueta 19). */
    @Volatile
    private var reassignarDesDe = 0L

    /** El bloqueig petit dels dos camps del canvi: [haCanviat] no s'espera al repartiment, que pot trigar segons. */
    private val bloqueigCanvi = Any()

    private fun haCanviat(desDe: Long = 0) {
        synchronized(bloqueigCanvi) {
            reassignarDesDe = if (calReassignar) min(reassignarDesDe, desDe) else desDe
            calReassignar = true
        }
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
        val canals = canals()
        val sessions = sessionsDesades(canals)
        val noves = mutableListOf<Vegada>()
        var s = desDe
        while (s <= darrer) s = cercaTros(s, minOf(s + 7 * 86400, darrer + 60), canals, sessions, noves)
        noves.sortBy { it.ts }
        bd.substitueixVegades(desDe, assignaTotes(noves))
        config.vegadesFins = darrer
        config.versioVegades = VERSIO_VEGADES
    }

    private fun canals() = if (config.teLinia) listOf(0 to false, 1 to true) else listOf(0 to false)

    /** Les estones d'inici a fi desades (maqueta 21): cadascuna és una sola vegada, i el que hi havia a dins, fora (tret del que no era seu). */
    private fun sessionsDesades(canals: List<Pair<Int, Boolean>>) =
        bd.etiquetes().filter { it.sessio != null && !it.no && canals.any { c -> c.first == it.canal } }

    /**
     * Un aparell que s'ha dit «Ja està engegat» des d'una hora potser ja anava uns minuts abans: el que comença fins a un quart
     * d'hora abans és seu. El que ja anava de més abans (el cotxe carregant), per defecte no.
     */
    private val MARGE_SESSIO = 15 * 60L

    /**
     * Les vegades que comencen d'[s] a [e] (afegides a [noves]); torna on comença el tros següent (més enllà d'[e] si alguna
     * vegada o estona d'inici a fi hi passa). Primer on s'acaben les que comencen dins del tros; després, totes les que comencen
     * abans d'aquell punt: també les que s'han encès a sobre d'una de llarga després del final del tros.
     */
    private fun cercaTros(s: Long, e: Long, canals: List<Pair<Int, Boolean>>, sessions: List<Etiqueta>, noves: MutableList<Vegada>): Long {
        val perCanal = canals.map { (canal, linia) -> canal to Episodis.troba(bd.minutsW(linia, s - 2 * 3600, e + 24 * 3600), llargada = 2) }
        var seguent = e
        for ((_, trobades) in perCanal) {
            trobades.filter { it.ts in s until e && it.fi >= e }.maxOfOrNull { it.fi + 60 }?.let { seguent = maxOf(seguent, it) }
        }
        val delTros = sessions.filter { it.desDe in s until e }
        delTros.maxOfOrNull { it.finsA + 1 }?.let { seguent = maxOf(seguent, it) }
        for ((canal, trobades) in perCanal) {
            val seves = delTros.filter { it.canal == canal }
            // Una vegada d'una estona que no compta: la que hi cap (com a molt 2 minuts més a cada costat)
            fun dinsDe(ep: Episodi, f: Pair<Long, Long>) = ep.ts >= f.first - 120 && ep.fi <= f.second + 120
            // El que toca una estona d'inici a fi és seu, tret del que l'usuari va dir que no ho era, i del que en surt més d'un
            // quart d'hora per un costat (el cotxe que ja carregava, l'aparell que no es va apagar quan es va dir): aquell es
            // queda, i els seus minuts no compten a l'estona. Si no, l'energia d'aquells minuts sortiria dues vegades (1.4.0)
            fun toca(ep: Episodi, se: Etiqueta) = ep.ts <= se.finsA && ep.fi >= se.desDe - 60
            fun surt(ep: Episodi, se: Etiqueta) = ep.ts < se.desDe - MARGE_SESSIO || ep.fi > se.finsA + MARGE_SESSIO
            trobades.filter { ep ->
                ep.ts in s until seguent && seves.none { se -> toca(ep, se) && !surt(ep, se) && se.sessio!!.none { f -> dinsDe(ep, f) } }
            }.forEach { noves += Vegada.de(canal, it) }
            for (se in seves) {
                val queSurten = trobades.filter { toca(it, se) && surt(it, se) }.map { it.ts to it.fi }
                vegadaSessio(se, queSurten)?.let { noves += it }
                // El que no comptava i no és cap vegada a part (anava dins de la de l'aparell): una vegada feta amb els seus minuts
                val linia = canal == 1
                for (f in se.sessio!!) {
                    // Si ja hi ha una vegada seva, o una que es queda i la cobreix (la porta dins), no se'n fa cap altra
                    if (trobades.any { dinsDe(it, f) } || queSurten.any { (a, b) -> a <= f.second && b >= f.first }) continue
                    val minuts = bd.minutsW(linia, f.first, f.second + 60)
                    if (minuts.size < 2) continue
                    val base = Sessio.base(bd.minutsW(linia, se.desDe - Sessio.ABANS, se.desDe), minuts)
                    noves += Sessio.vegadaDe(canal, minuts, base)
                }
            }
        }
        return seguent
    }

    /** La vegada d'una estona d'inici a fi desada ([Etiqueta.sessio]), amb els minuts exactes d'ara. */
    private fun vegadaSessio(e: Etiqueta, mesFora: List<Pair<Long, Long>> = emptyList()): Vegada? {
        val linia = e.canal == 1
        val abans = bd.minutsW(linia, e.desDe - Sessio.ABANS, e.desDe)
        val dins = bd.minutsW(linia, e.desDe, e.finsA + 1)
        return Sessio.calcula(e.canal, Sessio.base(abans, dins), dins, e.sessio.orEmpty() + mesFora)?.vegada
    }

    /**
     * Torna a buscar les vegades d'una estona d'inici a fi que s'ha desat, tret o tornat a posar: la vegada sencera (o el que
     * hi havia a dins) ha de canviar, no només de qui és. Només l'estona (amb les vegades que la toquen), no tot el que ve
     * després; i no si els minuts ja s'han esborrat (de fa més de 400 dies): les vegades d'aleshores es queden.
     */
    private fun rebusca(etiquetes: Collection<Etiqueta>) {
        val tocades = etiquetes.filter { it.sessio != null }
        if (tocades.isEmpty()) return
        synchronized(bloqueigVegades) {
            val primer = bd.primerExacte() ?: return
            val canals = canals()
            val sessions = sessionsDesades(canals)
            for (et in tocades) {
                // Des de la primera vegada que toca l'estona (també la d'abans, que pot venir del marge d'un quart d'hora)
                val a = minOf(et.desDe - MARGE_SESSIO, bd.vegades(et.desDe - MARGE_SESSIO, et.finsA + 1).minOfOrNull { it.ts } ?: et.desDe)
                if (a < primer + 2 * 3600) continue
                val noves = mutableListOf<Vegada>()
                val fins = cercaTros(a, et.finsA + 1, canals, sessions, noves)
                bd.substitueixVegades(a, assignaTotes(noves.filter { it.ts < fins }.sortedBy { it.ts }), fins)
            }
        }
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
        // Els endolls (maqueta 19): només els dels aparells que poden ser cada vegada (mai els sempre encesos), amb les
        // seves dades carregades per finestres, i el que gastaven les altres vegades de les mateixes hores
        val totsPeriodes = bd.periodesEndoll().filter { p -> aparells.any { it.id == p.aparellId && it.funciona != Funcionament.SEMPRE } }
        val periodesCasa = totsPeriodes.filter { p -> casa.any { it.id == p.aparellId } }
        val periodesLinia = totsPeriodes.filter { p -> linia.any { it.id == p.aparellId } }
        val dades = HashMap<Long, DadesEndoll>()
        val hores = perHora(vegades)
        // L'últim minut de cada canal: una vegada que hi arriba encara dura
        val darrers = mapOf(0 to bd.darrerTs(0), 1 to bd.darrerTs(1))
        return vegades.map { v ->
            val oberta = darrers[v.canal]?.let { v.fi >= it - 120 } ?: false
            val e = index.de(v)
            val (a, com) = when {
                // Només les etiquetes dels aparells que hi ha ara a la línia: si s'han tret, la línia sencera ja surt com un més
                // al rànquing, i comptar-la també a l'aparell la sumaria dues vegades (1.1.3)
                v.canal == 1 -> {
                    val (endoll, noEndoll) = veredictes(v, periodesLinia, dades, altresWh(v, hores))
                    assigna(v, linia, refsLinia, e.filter { et -> linia.any { it.id == et.aparellId } }, exclosos, endoll, noEndoll, oberta)
                }
                abansDeLaLinia(v, iniciLinia) -> {
                    val (endoll, noEndoll) = veredictes(v, totsPeriodes, dades, altresWh(v, hores))
                    assigna(v, tots, refsTots, e, exclosos, endoll, noEndoll, oberta)
                }
                else -> {
                    val (endoll, noEndoll) = veredictes(v, periodesCasa, dades, altresWh(v, hores))
                    assigna(v, casa, refsCasa, e, exclosos, endoll, noEndoll, oberta)
                }
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
            val desDe = synchronized(bloqueigCanvi) {
                if (!calReassignar) return
                calReassignar = false
                reassignarDesDe.also { reassignarDesDe = 0 }
            }
            val totes = bd.vegades(desDe, Long.MAX_VALUE)
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
        // Repartir de nou pot trigar: fora del bloqueig del repositori, que aturaria les lectures en directe. Si era una estona
        // d'inici a fi, el que hi havia a dins torna a ser vegades soltes
        rebusca(d.tornaEtiquetes)
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
        val sessions = synchronized(this) {
            val existeixen = bd.aparells().associateBy { it.id }
            // Les estones d'inici a fi que es treuen o es tornen a posar: les vegades s'han de tornar a buscar
            val tretes = bd.etiquetes().filter { it.id in d.etiquetes && it.sessio != null }
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
            d.sessio?.takeIf { it.aparellId in existeixen && config.sessioAparell == 0L }?.let {
                config.sessioAparell = it.aparellId
                config.sessioInici = it.inici
                config.sessioFi = it.fi
                config.sessioBase = it.base
            }
            haCanviat()
            tretes + d.tornaEtiquetes.filter { it.aparellId in existeixen }
        }
        rebusca(sessions)
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
                "sessio" -> OrigenEnsenyat.SESSIO
                "repetit" -> OrigenEnsenyat.REPETIT
                else -> OrigenEnsenyat.RESPOSTA
            }
            val es = etiquetes.filter { e -> e.id !in usades && ms.any { lliga(e, it) } }
            usades += es.map { it.id }
            // Fins on arriba: l'última mostra o, d'una estona d'inici a fi, la seva etiqueta
            llista += Ensenyat(origen, ms.minOf { iniciMostra(it) }, maxOf(ms.maxOf { it.ts }, es.maxOfOrNull { it.finsA } ?: 0), ms, es)
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
        rebusca(e.etiquetes)
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
            ensenyats(aparellId), fitxaEndoll(aparellId),
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
        val periodes = bd.periodesEndoll()
        val tots = bd.aparells()
        val dadesEndolls = HashMap<Long, DadesEndoll>()
        val grosses = sense.filter { it.ts !in ignorades && it.kwh >= 0.3 }.sortedByDescending { it.kwh }.take(quants).map { v ->
            // Els que l'usuari ha dit que no eren, i els que tenien l'endoll apagat (maqueta 19), no es proposen
            val (_, noEndoll) = veredictes(v, periodes.filter { p -> candidatsDe(v).any { it.id == p.aparellId } }, dadesEndolls, 0.0)
            val candidats = candidatsDe(v).filter { a -> a.id !in noEndoll && nos.none { it.aparellId == a.id && encavalca(v, it) > 0 } }
            // Si sense l'endoll l'app l'hauria donada a un d'aquells aparells, es diu
            val sola = if (noEndoll.isEmpty()) null else assigna(v, candidatsDe(v), refs, emptyList()).first?.takeIf { it in noEndoll }
            VegadaSenseNom(
                v, eurosDe(v, v.ts, v.fi + 60, p), endevina(v.w, candidats, refs, candidats.size, v.minuts.toDouble(), v.forma),
                bd.minutsW(linia = false, v.ts - 15 * 60, v.fi + 16 * 60).map { it.ts to it.w },
                tots.firstOrNull { it.id == sola },
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
        // Els minuts dels endolls, igual (maqueta 19); les seves hores es queden
        bd.esborraMinutsEndoll(iniciDia(avui.minusDays(DIES_AMB_MINUTS)))
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

    /** Les firmes dels aparells de la resta de la casa. Amb [senseFont], sense les mostres d'aquella font (les de l'endoll, maqueta 19). */
    fun referencies(senseFont: String? = null): Map<Long, Referencia> {
        val aparells = aparellsAEntrenar()
        val sols = aparells.filter { it.funciona == Funcionament.SOL }.map { it.id }.toSet()
        val refs = bd.mostres().filter { m -> m.font != senseFont && aparells.any { it.id == m.aparellId } }.groupBy { it.aparellId }
            .mapNotNull { (id, m) -> Referencia.de(id, m, ambInterval = id in sols) }.associateBy { it.aparellId }
        // El compressor de la nevera, trobat sol a les dades: per a la nevera que s'engega sola i no té mostres
        val w = config.neveraW.takeIf { it > 0 } ?: return refs
        val ambEndoll = aparellsAmbEndoll()
        val nevera = listOf(Categoria.NEVERA, Categoria.CONGELADOR).firstNotNullOfOrNull { cat ->
            aparells.firstOrNull { it.categoria == cat && it.funciona == Funcionament.SOL && it.id !in refs && it.id !in ambEndoll }
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
    fun tornaAEntrenar(aparellId: Long) {
        val sessions = synchronized(this) {
            val sessions = bd.etiquetes().filter { it.aparellId == aparellId && it.sessio != null }
            bd.esborraMostres(aparellId)
            bd.esborraEtiquetes(aparellId)
            haCanviat()
            sessions
        }
        rebusca(sessions)
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

    // ---------- Ensenyar d'inici a fi (maqueta 21) ----------

    /** El canal d'un aparell: el de la línia de la pinça 2 si hi és, o la resta de la casa. */
    private fun canalDe(a: Aparell) = if (aLaLinia(a)) 1 else 0

    /**
     * Comença a ensenyar un aparell d'inici a fi: «Ara l'engego» (ara) o «Ja està engegat» ([desDe], abans). La base de la
     * casa és la mediana de la mitja hora d'abans, si ja hi ha els minuts (si no, -1, i la pantalla fa servir les lectures).
     */
    fun comencaSessio(aparellId: Long, desDe: Long) {
        val a = bd.aparells().firstOrNull { it.id == aparellId } ?: return
        // No pot començar dins d'una estona d'inici a fi ja desada del mateix canal: els minuts serien de dues alhora
        val inici = bd.etiquetes().filter { it.sessio != null && !it.no && it.canal == canalDe(a) && it.finsA >= desDe && it.desDe <= ara() }
            .maxOfOrNull { it.finsA + 1 }?.let { maxOf(it, desDe) } ?: desDe
        val abans = bd.minutsW(canalDe(a) == 1, inici - Sessio.ABANS, inici)
        config.sessioAparell = aparellId
        config.sessioInici = inici
        config.sessioFi = 0
        config.sessioBase = if (abans.size >= 5) mediana(abans.map { it.w }) else -1.0
    }

    /** «L'he apagat»: l'estona acaba ara (o a [finsA]) i queda per revisar. */
    fun acabaSessio(finsA: Long = ara()) {
        if (config.sessioAparell != 0L && config.sessioFi == 0L) config.sessioFi = maxOf(finsA, config.sessioInici + 60)
    }

    /** Ni l'estona ni el que n'hagi sortit: com si no s'hagués començat. */
    fun oblidaSessio() {
        config.sessioAparell = 0
        config.sessioInici = 0
        config.sessioFi = 0
        config.sessioBase = -1.0
    }

    /**
     * L'estona d'inici a fi que s'ha acabat, per revisar-la: els minuts del canal de l'aparell des del minut en què es va
     * engegar fins al que es va apagar, la base de la mitja hora d'abans, i les vegades de dins que no semblen seves (les
     * d'un altre aparell segons l'usuari o un endoll, i les massa grosses per a ell). Null si no n'hi ha cap d'acabada.
     */
    fun estonaSessio(): EstonaSessio? {
        val a = bd.aparells().firstOrNull { it.id == config.sessioAparell } ?: return null
        val fi = config.sessioFi.takeIf { it > 0 } ?: return null
        val canal = canalDe(a)
        val desDe = config.sessioInici / 60 * 60
        val linia = canal == 1
        val abans = bd.minutsW(linia, desDe - Sessio.ABANS, desDe)
        val dins = bd.minutsW(linia, desDe, fi)
        val serie = bd.serie(desDe - Sessio.ABANS, fi + 60)
        val esperats = ((fi - desDe) / 60).coerceAtLeast(1)
        // Des de fora de casa no hi ha els minuts exactes: s'espera que el mòbil torni a la wifi de casa
        val aproximada = serie.any { it.ts >= desDe && it.aprox } || dins.size < esperats * 0.8
        val base = Sessio.base(abans, dins)
        val nivell = Sessio.nivell(dins, base)
        val r = (if (linia) referenciesLinia() else referencies())[a.id]
        val limit = maxOf(a.wTipic * 1.3, (r?.max ?: 0.0) * 1.15)
        val vegades = bd.vegades(desDe, fi).filter { it.canal == canal }
        // El que ja anava de més d'un quart d'hora abans no és seu (per defecte): el cotxe que carregava
        val alienes = (vegades.filter { it.ts < desDe - MARGE_SESSIO } + Sessio.alienes(vegades, dins, base, desDe, fi, a.id, nivell, limit))
            .distinctBy { it.ts to it.fi }.sortedBy { it.ts }
        val punts = serie.map { it.ts to if (linia) it.wLinia else max(it.wTotal - (if (config.teLinia) it.wLinia else 0.0), 0.0) }
        return EstonaSessio(a.id, canal, desDe, fi, base, punts, dins, alienes, aproximada)
    }

    /**
     * Desa l'estona d'inici a fi: una etiqueta de tota l'estona (amb les vegades de [fora], que no compten) i una mostra amb
     * la firma (la potència de quan funciona, entre quins W va, i la durada si no va a cicles). Tota l'estona passa a ser
     * una sola vegada de l'aparell. Null si no s'hi veu res.
     */
    fun desaSessio(e: EstonaSessio, fora: List<Vegada>): Desfer? {
        val f = e.firma(fora) ?: return null
        val v = f.vegada
        val etiqueta = Etiqueta(e.canal, v.ts, v.fi + 59, e.aparellId, sessio = fora.map { it.ts to it.fi })
        val d = synchronized(this) {
            val id = bd.desaEtiqueta(etiqueta)
            val mostra = bd.desaMostra(
                e.aparellId, v.ts, v.w, if (f.aCicles) null else v.fi + 60 - v.ts, "sessio", f.wMin to f.wMax, v.variacio, v.arrencada,
            )
            // Desfer-ho torna l'estona a «per revisar»
            val estona = SessioDesada(e.aparellId, config.sessioInici, config.sessioFi, config.sessioBase)
            oblidaSessio()
            haCanviat()
            Desfer(listOf(id), listOf(mostra), sessio = estona)
        }
        // Fora del bloqueig del repositori: buscar les vegades de nou pot trigar
        rebusca(listOf(etiqueta))
        reassignaVegades()
        return d
    }

    /** Com quadra el que l'usuari diu que hi ha encès amb el que veia Wakker (maqueta 21). */
    enum class EstatMarca {
        /** Wakker ja el veia (una vegada seva, o el compressor de la nevera). */
        QUADRA,

        /** Era una cosa que Wakker veia sense nom: ara ja és seva. */
        ERA_SENSE_NOM,

        /** Wakker no hi veia res seu: és petit (va als petits consums) o fa estona que és engegat i varia. */
        NO_VIST,
    }

    data class FilaMarca(val aparell: Aparell, val estat: EstatMarca, val w: Double? = null)

    /**
     * «Què hi ha encès ara?» (maqueta 21): els aparells que l'usuari sap segur que funcionen ara ([ids]). Cada un es compara
     * amb el que veia Wakker ([encesDelCanal], com la pantalla Ara): si una vegada que encara dura ja és seva, quadra; si
     * n'hi ha una sense nom, la que s'hi assembla més passa a ser seva (una etiqueta al seu inici i una mostra amb la seva
     * potència); si no, no se'n diu res. Que un aparell no estigui marcat no vol dir que estigui apagat. Null si les dades no
     * són d'ara (a fora de casa).
     */
    fun marcaEncesos(ids: Set<Long>, l: Lectura): Pair<List<FilaMarca>, Desfer?>? {
        val o = obertes()
        val fons = fonsW()
        if (!o.fresques || fons == null) return null
        val ara = ara()
        val tots = bd.aparells().filter { it.id in ids }
        val wLinia = if (config.teLinia) (l.linia?.w ?: 0.0).coerceAtLeast(0.0) else 0.0
        val ences = encesDelCanal(ara, 0, l.total.w - wLinia - fons, o.vegades, aparellsAEntrenar(), referencies(), o.neveraW) +
            (if (config.teLinia) encesDelCanal(ara, 1, wLinia, o.vegades, aparellsLinia(), referenciesLinia()) else emptyList())
        val usades = mutableSetOf<EncesAra>()
        val files = mutableListOf<FilaMarca>()
        val etiquetes = mutableListOf<Long>()
        val mostres = mutableListOf<Long>()
        val refs = referencies() + referenciesLinia()
        synchronized(this) {
            // Primer els que ja quadren, perquè no s'emportin una cosa sense nom que és d'un altre
            val pendents = mutableListOf<Aparell>()
            for (a in tots) {
                val seva = ences.firstOrNull { it.aparellId == a.id && it !in usades }
                    ?: ences.firstOrNull { it.tipus == TipusEnces.NEVERA && o.nevera?.id == a.id && it !in usades }
                if (seva != null) {
                    usades += seva
                    files += FilaMarca(a, EstatMarca.QUADRA, seva.w)
                } else pendents += a
            }
            for (a in pendents) {
                val canal = canalDe(a)
                val sense = ences.filter {
                    it.canal == canal && it !in usades && (it.tipus == TipusEnces.SENSE_IDENTIFICAR || it.tipus == TipusEnces.NOU)
                }.minByOrNull { distancia(it.w, it.vegada?.minuts?.toDouble(), it.vegada?.forma, a, refs[a.id]) }
                if (sense == null || a.funciona == Funcionament.SEMPRE) {
                    files += FilaMarca(a, EstatMarca.NO_VIST)
                    continue
                }
                usades += sense
                // Una vegada que encara dura: l'etiqueta a l'inici; una cosa que s'acaba d'encendre (encara no és vegada), fa poc
                val inici = sense.vegada?.ts ?: (ara - 120)
                etiquetes += bd.desaEtiqueta(Etiqueta(canal, inici, inici, a.id))
                mostres += bd.desaMostra(a.id, inici, sense.w, null, "marca")
                files += FilaMarca(a, EstatMarca.ERA_SENSE_NOM, sense.w)
            }
            if (etiquetes.isNotEmpty()) haCanviat()
        }
        if (etiquetes.isNotEmpty()) reassignaVegades()
        return files to (if (etiquetes.isEmpty()) null else Desfer(etiquetes, mostres))
    }

    // ---------- Els endolls amb mesura (maqueta 19) ----------

    /** Els lectors de cada endoll (un Tapo guarda la sessió KLAP; tots dos comproven la MAC), per id. */
    private val lectors = java.util.concurrent.ConcurrentHashMap<Long, LectorEndoll>()

    /** Els endolls que no responen: fins quan no es tornen a provar, i quants cops seguits han fallat (retrocés). */
    private val fallades = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long, Int>>()

    /** Les lectures en directe de cada endoll que encara no han fet el minut: el minut, la suma de W i quantes. */
    private val acumulatsEndoll = HashMap<Long, Triple<Long, Double, Int>>()

    /** L'última lectura de cada endoll (es conserva encara que una vegada no respongui), per a les pantalles. */
    @Volatile
    var lecturesEndolls: Map<Long, LecturaEndoll> = emptyMap()
        private set

    private val sincronitzantEndolls = Mutex()

    fun endolls() = bd.endolls()
    fun periodesEndoll() = bd.periodesEndoll()

    /** L'endoll que té posat un aparell ara, i des de quan. */
    fun endollDe(aparellId: Long): Pair<Endoll, PeriodeEndoll>? {
        val p = bd.periodesEndoll().lastOrNull { it.aparellId == aparellId && it.finsA == null } ?: return null
        val e = bd.endolls().firstOrNull { it.id == p.endollId } ?: return null
        return e to p
    }

    /** L'aparell que mesura un endoll ara. */
    fun aparellDe(endollId: Long): Long? = bd.periodesEndoll().lastOrNull { it.endollId == endollId && it.finsA == null }?.aparellId

    private fun lector(e: Endoll): LectorEndoll = lectors.getOrPut(e.id) {
        when (e.tipus) {
            TipusEndoll.SHELLY -> ShellyEndoll(e.host, macEsperada = e.mac)
            TipusEndoll.TAPO -> Tapo(e.host, Klap.deHex(config.tapoResum), macEsperada = e.mac)
        }
    }

    /** Si un endoll es pot llegir ara: un Tapo necessita el compte. */
    fun llegible(e: Endoll) = e.tipus != TipusEndoll.TAPO || config.teTapo

    /** El compte de Tapo ha canviat: les sessions d'abans ja no valen. */
    fun oblidaSessionsEndolls() {
        lectors.clear()
        fallades.clear()
    }

    /** Un endoll nou (o el mateix, si ja hi era per la MAC, amb l'adreça nova); torna l'id. */
    @Synchronized
    fun afegeixEndoll(info: InfoEndoll): Long {
        val existent = bd.endolls().firstOrNull { it.mac.isNotBlank() && it.mac == info.mac }
        val id = bd.desaEndoll(Endoll(existent?.id ?: 0, info.host, info.mac, info.model, info.nom.ifBlank { info.model }, info.tipus))
        lectors.remove(id)
        fallades.remove(id)
        return id
    }

    @Synchronized
    fun esborraEndoll(id: Long) {
        bd.esborraEndoll(id)
        lectors.remove(id)
        fallades.remove(id)
        synchronized(acumulatsEndoll) { acumulatsEndoll.remove(id) }
        haCanviat()
    }

    /**
     * L'endoll passa a mesurar un aparell (o cap). Un aparell només té un endoll: el que tenia queda sense. Si l'endoll no
     * ha estat mai a cap altre aparell, el període comença amb el seu historial (fins a 8 dies enrere): l'endoll ja era a
     * l'aparell abans de dir-ho a l'app, i les vegades d'aquells dies també se'n beneficien. Si ve d'un altre aparell, des d'ara.
     */
    @Synchronized
    fun posaEndollA(endollId: Long, aparellId: Long?) {
        val ara = ara()
        val periodes = bd.periodesEndoll()
        if (aparellId != null) {
            periodes.filter { it.aparellId == aparellId && it.finsA == null && it.endollId != endollId }.forEach { bd.posaEndollA(it.endollId, null, ara) }
        }
        val seus = periodes.filter { it.endollId == endollId }
        if (aparellId != null && seus.all { it.aparellId == aparellId }) {
            bd.esborraPeriodesEndoll(endollId)
            val inici = listOfNotNull(bd.primeraHoraEndoll(endollId), bd.primerMinutEndoll(endollId), ara).min()
            bd.posaEndollA(endollId, aparellId, inici)
            haCanviat(inici - 86400)
        } else {
            bd.posaEndollA(endollId, aparellId, ara)
            haCanviat()
        }
    }

    /**
     * Llegeix els endolls, tots alhora: la potència d'ara. Cada minut acabat es desa com un minut de l'endoll, amb la mitjana
     * de les lectures que n'hi ha hagut. Un que no respon no es torna a provar fins al cap d'un minut (de 5, després de tres
     * cops), i es conserva la seva última lectura. Amb [nomesNuvol] (des de fora de casa), només els Shelly, pel núvol de Shelly.
     */
    suspend fun llegeixEndolls(nomesNuvol: Boolean = false): Map<Long, LecturaEndoll> = withContext(Dispatchers.IO) {
        val ara = ara()
        val llista = bd.endolls().filter { llegible(it) && (fallades[it.id]?.first ?: 0) <= ara && (!nomesNuvol || it.tipus == TipusEndoll.SHELLY) }
        if (llista.isEmpty()) return@withContext lecturesEndolls
        val noves = coroutineScope { llista.map { e -> async { e.id to llegeix(e, nomesNuvol) } }.awaitAll() }
        val resultat = HashMap(lecturesEndolls)
        for ((id, l) in noves) if (l != null) {
            resultat[id] = l
            acumula(id, l)
        }
        lecturesEndolls = resultat
        resultat
    }

    private fun llegeix(e: Endoll, nomesNuvol: Boolean): LecturaEndoll? {
        val local = if (nomesNuvol) null else runCatching { lector(e).lectura() }.getOrNull()
        val l = local ?: if (e.tipus == TipusEndoll.SHELLY && config.teNuvol) {
            runCatching { ShellyEndoll.lecturaNuvol(config.servidorNuvol, config.clauNuvol, e.mac) }.getOrNull()
        } else null
        if (l == null) {
            val cops = (fallades[e.id]?.second ?: 0) + 1
            fallades[e.id] = (ara() + if (cops >= 3) 300 else 60) to cops
            // Potser ha canviat d'adreça o de sessió: la propera vegada, de nou
            if (cops >= 3) lectors.remove(e.id)
        } else fallades.remove(e.id)
        return l
    }

    private fun acumula(endollId: Long, l: LecturaEndoll) {
        val minut = l.ts / 60 * 60
        val acabat = synchronized(acumulatsEndoll) {
            val a = acumulatsEndoll[endollId]
            if (a == null || a.first == minut) {
                acumulatsEndoll[endollId] = Triple(minut, (a?.second ?: 0.0) + l.w, (a?.third ?: 0) + 1)
                null
            } else {
                acumulatsEndoll[endollId] = Triple(minut, l.w, 1)
                a
            }
        }
        // El minut acabat: la potència mitjana, en Wh (amb una sola lectura, aproximat: la de 5 minuts de l'endoll el pot millorar)
        if (acabat != null) bd.insereixMinutsEndoll(endollId, listOf(MinutEndoll(acabat.first, acabat.second / acabat.third / 60, aprox = acabat.third < 2)))
        // Amb comptador (els Shelly): entre dues lectures separades (des de fora, cada 15 minuts; o després d'una estona sense
        // llegir), l'energia es reparteix a parts iguals pels minuts d'entremig, com el comptador de l'EM des de fora. El
        // comptador es desa a la configuració: el treball de fons corre en un procés nou cada vegada
        val wh = l.comptadorWh ?: return
        val abans = config.comptadorEndoll(endollId)
        config.posaComptadorEndoll(endollId, l.ts, wh)
        if (abans == null) return
        val minuts = ((minut - abans.first / 60 * 60) / 60).toInt()
        if (minuts in 2..7 * 24 * 60 && wh >= abans.second) {
            val perMinut = (wh - abans.second) / minuts
            bd.insereixMinutsEndoll(endollId, (0 until minuts).map { i -> MinutEndoll(abans.first / 60 * 60 + i * 60L, perMinut, aprox = true) })
        }
    }

    /**
     * L'historial dels endolls, com a molt un cop cada 10 minuts (o amb [forca]) i mai dos alhora: les hores (fins a 8 dies
     * enrere la primera vegada, el que l'endoll guarda; les dues últimes es tornen a demanar, per si l'endoll les ha acabat
     * de tancar) i la potència de cada 5 minuts de les últimes 12 hores, si el firmware la dona. Dels Shelly, les hores es fan
     * amb els seus minuts. Després, el que se n'aprèn. Torna si algun ha respost.
     */
    suspend fun sincronitzaEndolls(forca: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val llista = bd.endolls().filter { llegible(it) }
        if (llista.isEmpty() || (!forca && ara() - config.endollsMirat < 10 * 60)) return@withContext false
        if (!sincronitzantEndolls.tryLock()) return@withContext false
        try {
            config.endollsMirat = ara()
            var algun = false
            var desDeMinim = Long.MAX_VALUE
            if (llista.any { it.tipus == TipusEndoll.SHELLY }) {
                runCatching { llegeixEndolls() }
                for (e in llista.filter { it.tipus == TipusEndoll.SHELLY }) {
                    horesDelsMinuts(e)
                    algun = true
                }
            }
            for (e in llista.filter { it.tipus == TipusEndoll.TAPO }) {
                val t = lector(e)
                runCatching {
                    val lectura = t.lectura()
                    acumula(e.id, lectura)
                    val horaAra = iniciHora(ara(), zona)
                    val limit = horaAra - 8 * 86400
                    if (config.endollHoraLocal == 0) esbrinaHora(t, lectura)
                    if (config.endollHoraLocal != 0) {
                        val desDe = max(bd.darreraHoraEndoll(e.id)?.minus(3600) ?: limit, limit)
                        if (desDe < horaAra) {
                            // Només les hores acabades: la d'ara la fan els minuts
                            val hores = horesEndoll(t, desDe, horaAra).filter { it.first < horaAra }
                            bd.insereixHoresEndoll(e.id, hores)
                            hores.minOfOrNull { it.first }?.let { desDeMinim = min(desDeMinim, it) }
                        }
                    }
                    if (e.mac !in config.endollsSensePotencia) {
                        val darrer = bd.darrerMinutEndoll(e.id) ?: (ara() - 12 * 3600)
                        val d5 = max(darrer + 60, ara() - 12 * 3600) / 300 * 300
                        if (ara() - d5 > 600) {
                            val punts = t.potencia5min(d5, ara() / 300 * 300, zona, config.endollHoraLocal == 2)
                            if (punts.isEmpty() && (t as? Tapo)?.senseMetode == true) config.endollsSensePotencia = config.endollsSensePotencia + e.mac
                            val minuts = punts.flatMap { (ts, w) -> (0 until 5).map { i -> MinutEndoll(ts + i * 60L, w / 60, aprox = true) } }.filter { it.ts < ara() - 60 }
                            if (minuts.isNotEmpty()) {
                                bd.insereixMinutsEndoll(e.id, minuts)
                                desDeMinim = min(desDeMinim, minuts.first().ts)
                            }
                        }
                    }
                    algun = true
                }
            }
            if (desDeMinim != Long.MAX_VALUE) haCanviat(desDeMinim - 86400)
            if (algun) aprenDelsEndolls()
            algun
        } finally {
            sincronitzantEndolls.unlock()
        }
    }

    /**
     * Les hores d'un endoll sense historial (els Shelly), fetes amb els seus minuts: cada hora acabada des de l'última que
     * té (com a molt 8 dies enrere) amb 55 minuts o més, la suma a escala de 60. Una hora amb forats (l'app tancada, sense
     * vigilància ni núvol) es deixa estar: les vegades d'aquella hora van com sense endoll.
     */
    private fun horesDelsMinuts(e: Endoll) {
        val horaAra = iniciHora(ara(), zona)
        var h = max(bd.darreraHoraEndoll(e.id)?.plus(3600) ?: 0, horaAra - 8 * 86400)
        val noves = mutableListOf<Pair<Long, Double>>()
        while (h < horaAra) {
            val m = bd.minutsEndoll(e.id, h, h + 3599)
            if (m.size >= 55) noves += h to m.sumOf { it.wh } * 60.0 / m.size
            h += 3600
        }
        bd.insereixHoresEndoll(e.id, noves)
    }

    /** Les hores d'un endoll entre dos instants, per trossos de 8 dies, amb la manera d'entendre els timestamps ja esbrinada. */
    private fun horesEndoll(t: LectorEndoll, desDe: Long, finsA: Long): List<Pair<Long, Double>> {
        val local = config.endollHoraLocal == 2
        val sortida = mutableListOf<Pair<Long, Double>>()
        var s = desDe
        while (s < finsA) {
            val e = min(s + 8 * 86400, finsA)
            sortida += t.energiaHores(s, e - 1, zona, local).filter { it.first in s until e }
            s = e
        }
        return sortida
    }

    @Volatile
    private var esbrinatA = 0L

    /**
     * Com entén l'endoll els timestamps: segons UTC de debò (el crate tapo d'avui) o l'hora local escrita com si fos UTC
     * (les versions d'abans). Amb cada manera es demanen les hores d'avui; la que suma el consum d'avui de l'endoll
     * (today_energy) és la bona, i només es decideix si guanya clarament (l'altra s'equivoca el doble). Si no es pot (poc
     * consum avui), es torna a provar al cap d'una hora; mentrestant no es desa cap hora.
     */
    private fun esbrinaHora(t: LectorEndoll, l: LecturaEndoll) {
        if (config.endollsHoraDesDe == 0L) config.endollsHoraDesDe = ara()
        // Un dia sense poder-ho decidir (un endoll que gairebé no gasta): les de debò
        if (config.endollsHoraDesDe < ara() - 86400) { config.endollHoraLocal = 1; return }
        val avui = l.whAvui ?: return
        if (avui < 5 || ara() - esbrinatA < 3600) return
        esbrinatA = ara()
        val inici = iniciDia(LocalDate.now(zona))
        fun suma(local: Boolean) = runCatching { t.energiaHores(inici, ara(), zona, local).filter { it.first in inici..ara() }.sumOf { it.second } }.getOrNull()
        val utc = suma(false) ?: return
        val loc = suma(true) ?: return
        val eUtc = abs(utc - avui)
        val eLoc = abs(loc - avui)
        val tolerancia = max(30.0, 0.2 * avui)
        // Amb un consum pla (una tele, un endoll que gasta sempre igual), les dues maneres donen gairebé el mateix i
        // abans no es decidia mai: no es desava cap hora i Wakker només comptava les lectures en directe (molt menys
        // que l'app de Tapo). Ara, si totes dues quadren, la que s'equivoca menys; si cap no quadra, es torna a provar
        // d'aquí a una hora, i després d'un dia sense decidir, la d'avui del crate tapo (segons de debò)
        config.endollHoraLocal = when {
            eUtc <= tolerancia || eLoc <= tolerancia -> if (eLoc < eUtc) 2 else 1
            config.endollsHoraDesDe in 1 until ara() - 86400 -> 1
            else -> 0
        }
        if (config.endollsHoraDesDe == 0L) config.endollsHoraDesDe = ara()
    }

    /**
     * El que l'app aprèn dels endolls: cada vegada que l'endoll confirma ([Com.ENDOLL]) és una mostra de la firma de
     * l'aparell (font «endoll»; com a molt les 20 últimes per aparell). Si n'hi ha de noves, les vegades d'aquells dies es
     * tornen a repartir: l'aparell ja es reconeix sol en altres moments.
     */
    @Synchronized
    private fun aprenDelsEndolls() {
        val periodes = bd.periodesEndoll()
        if (periodes.isEmpty()) return
        val aparells = bd.aparells()
        var desDeNova = Long.MAX_VALUE
        for (p in periodes) {
            val a = aparells.firstOrNull { it.id == p.aparellId } ?: continue
            var nova = false
            for (v in bd.vegadesEndoll(a.id, p.desDe, 20).filter { p.finsA == null || it.ts < p.finsA }) {
                if (bd.teMostra(a.id, v.ts, "endoll")) continue
                val interval = if (a.funciona == Funcionament.SOL) v.w * 0.8 to v.w * 1.2 else null
                bd.desaMostra(a.id, v.ts, v.w, v.fi - v.ts + 60, "endoll", interval, v.variacio, v.arrencada)
                desDeNova = min(desDeNova, v.ts)
                nova = true
            }
            if (nova) bd.retallaMostres(a.id, "endoll", 20)
        }
        if (desDeNova != Long.MAX_VALUE) haCanviat(desDeNova - 86400)
    }

    /**
     * Els minuts i les hores d'un endoll, carregats per finestres de 7 dies: en repartir milers de vegades no es fa una
     * consulta per cada una. Els minuts van ordenats: es busquen per cerca binària.
     */
    private inner class DadesEndoll(private val endollId: Long) {
        private var desDe = 1L
        private var finsA = 0L
        private var minuts: List<MinutEndoll> = emptyList()
        private var hores: List<Pair<Long, Double>> = emptyList()

        private fun carrega(a: Long, b: Long) {
            if (a >= desDe && b <= finsA) return
            desDe = a - 600
            finsA = max(b + 3600, desDe + 7 * 86400)
            minuts = bd.minutsEndoll(endollId, desDe, finsA)
            hores = bd.horesEndoll(endollId, iniciHora(desDe, zona), finsA)
        }

        private fun primer(ts: Long): Int {
            var lo = 0
            var hi = minuts.size
            while (lo < hi) {
                val mig = (lo + hi) / 2
                if (minuts[mig].ts < ts) lo = mig + 1 else hi = mig
            }
            return lo
        }

        /** Els minuts de [a] a [b], tots dos inclosos. */
        fun minuts(a: Long, b: Long): List<MinutEndoll> {
            carrega(a, b)
            return minuts.subList(primer(a), primer(b + 1))
        }

        fun hores(a: Long, b: Long): List<Pair<Long, Double>> {
            carrega(a, b)
            return hores.filter { it.first in a until b }
        }
    }

    /**
     * Què diuen els endolls d'una vegada (maqueta 19): l'aparell que tenia l'endoll encès mentre durava (el primer que ho
     * compleixi), i els que el tenien apagat. Només els endolls dels aparells que poden ser aquesta vegada ([periodes], ja
     * filtrats pel canal), i només si eren posats a l'aparell en aquell moment. [altresWh]: el que han gastat les altres
     * vegades de les mateixes hores, per al veredicte amb les hores.
     */
    private fun veredictes(v: Vegada, periodes: List<PeriodeEndoll>, dades: HashMap<Long, DadesEndoll>, altresWh: Double): Pair<Long?, Set<Long>> {
        var seu: Long? = null
        val no = mutableSetOf<Long>()
        for (p in periodes) {
            if (p.desDe > v.fi || (p.finsA != null && p.finsA <= v.ts)) continue
            when (veredicte(v, dades.getOrPut(p.endollId) { DadesEndoll(p.endollId) }, altresWh)) {
                Veredicte.SEU -> if (seu == null) seu = p.aparellId
                Veredicte.NO_SEU -> no += p.aparellId
                Veredicte.NO_SE_SAP -> {}
            }
        }
        return seu to no
    }

    /** Què diu un endoll d'una vegada: amb els seus minuts (i els 10 d'abans) si n'hi ha prou; si no, amb les seves hores. */
    private fun veredicte(v: Vegada, d: DadesEndoll, altresWh: Double): Veredicte {
        val ambMinuts = veredicteEndoll(v.w, v.ts, v.fi, d.minuts(v.ts, v.fi), d.minuts(v.ts - 600, v.ts - 60))
        if (ambMinuts != Veredicte.NO_SE_SAP) return ambMinuts
        return veredicteHores(v.kwh, v.ts, v.fi, d.hores(iniciHora(v.ts, zona), v.fi + 1), altresWh) { iniciHora(it, zona) }
    }

    /** Les vegades agrupades per cada hora que toquen, per saber què més hi havia a la mateixa hora. */
    private fun perHora(vegades: List<Vegada>): Map<Long, List<Vegada>> {
        val perHora = HashMap<Long, MutableList<Vegada>>()
        for (v in vegades) {
            var h = iniciHora(v.ts, zona)
            while (h <= v.fi) {
                perHora.getOrPut(h) { mutableListOf() } += v
                h += 3600
            }
        }
        return perHora
    }

    /** El que han gastat les altres vegades de les hores d'una vegada (Wh). */
    private fun altresWh(v: Vegada, perHora: Map<Long, List<Vegada>>): Double {
        val vistes = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Vegada, Boolean>())
        var h = iniciHora(v.ts, zona)
        while (h <= v.fi) {
            perHora[h]?.forEach { if (it !== v) vistes += it }
            h += 3600
        }
        return vistes.sumOf { it.kwh } * 1000
    }

    /** L'endoll d'un aparell per a la fitxa: què n'ha après l'app i el que ha mesurat. */
    private fun fitxaEndoll(aparellId: Long): EndollFitxa? {
        val (e, p) = endollDe(aparellId) ?: return null
        val mes = iniciDia(LocalDate.now(zona).withDayOfMonth(1))
        val kwhMes = bd.horesEndoll(e.id, max(mes, p.desDe), ara()).sumOf { it.second } / 1000
        return EndollFitxa(e, p, queNHaApres(aparellId, e, p), mesuraEndoll(e, p), lecturesEndolls[e.id], kwhMes)
    }

    /**
     * Què n'ha après l'app d'un aparell amb l'endoll, dels últims 7 dies sencers: de les vegades que l'endoll confirma,
     * quantes hauria reconegut sola (amb la firma que té sense les mostres de l'endoll mateix, que si no es valida a si
     * mateixa); quantes d'altres li hauria donat i l'endoll diu que no; i cada dia, el que l'app sola li donava i el que
     * l'endoll ha mesurat.
     */
    private fun queNHaApres(aparellId: Long, e: Endoll, p: PeriodeEndoll): QueNHaApres? {
        val avui = LocalDate.now(zona)
        val desDe = max(p.desDe, iniciDia(avui.minusDays(7)))
        val finsA = iniciDia(avui)
        val vegades = bd.vegades(desDe, finsA).filter { it.ts >= desDe }
        if (vegades.isEmpty() && bd.horesEndoll(e.id, desDe, finsA).isEmpty()) return null
        val index = IndexEtiquetes(bd.etiquetes())
        val exclosos = bd.aparells().filter { it.funciona == Funcionament.SEMPRE }.map { it.id }.toSet()
        val refs = referencies(senseFont = "endoll") + referenciesLinia()
        val sola = HashMap<Long, Long?>()
        fun sola(v: Vegada) = sola.getOrPut(v.id) { assigna(v, candidatsDe(v), refs, index.de(v), exclosos).first }
        val seves = vegades.filter { it.aparellId == aparellId && it.com == Com.ENDOLL }
        val encertades = seves.count { sola(it) == aparellId }
        val dades = DadesEndoll(e.id)
        val hores = perHora(vegades)
        val deMes = vegades.count { v ->
            (v.aparellId != aparellId || v.com != Com.ENDOLL) && sola(v) == aparellId && veredicte(v, dades, altresWh(v, hores)) == Veredicte.NO_SEU
        }
        val primerDia = Instant.ofEpochSecond(p.desDe).atZone(zona).toLocalDate()
        val dies = (7 downTo 1).map { avui.minusDays(it.toLong()) }.filter { !it.isBefore(primerDia) }.map { dia ->
            val d0 = iniciDia(dia)
            val d1 = iniciDia(dia.plusDays(1))
            val appSola = vegades.filter { sola(it) == aparellId }.sumOf { it.kwh * part(it, d0, d1) }
            val endoll = bd.horesEndoll(e.id, d0, d1).sumOf { it.second } / 1000
            // Quadra si l'app sola hi veu entre el 60 i el 130 % del que diu l'endoll (l'app no compta el que fa menys de
            // 250 W: les bombes del rentaplats), o si la diferència és petita
            val quadra = abs(appSola - endoll) <= 0.15 || appSola in endoll * 0.6..endoll * 1.3
            DiaEndoll(dia, appSola, endoll, quadra)
        }
        return QueNHaApres(seves.size, encertades, deMes, dies, p.desDe)
    }

    /**
     * El que l'endoll ha mesurat d'un aparell: la mitjana per dia dels dies sencers (sense avui ni el dia que s'hi va
     * posar, si en queden), els últims 7 dies, i de les lectures en directe de l'última setmana, les hores encès i l'espera.
     */
    private fun mesuraEndoll(e: Endoll, p: PeriodeEndoll): MesuraEndoll? {
        val hores = bd.horesEndoll(e.id, p.desDe, ara())
        if (hores.isEmpty()) return null
        val perDia = hores.groupBy { Instant.ofEpochSecond(it.first).atZone(zona).toLocalDate() }.mapValues { (_, l) -> l.sumOf { it.second } }.toSortedMap()
        val avui = LocalDate.now(zona)
        val sencers = perDia.filterKeys { it != avui && iniciDia(it) >= p.desDe }
        val base = sencers.ifEmpty { perDia }
        val us = usEndoll(bd.minutsEndoll(e.id, max(p.desDe, ara() - 7 * 86400), ara()).filter { !it.aprox })
        return MesuraEndoll(base.values.average(), base.size, perDia.toList().takeLast(7), us?.first, us?.second, us?.third)
    }

    /**
     * Els endolls que ja es poden moure (Per fer): l'app encerta sola totes les vegades del seu aparell des de fa una
     * setmana i no li'n dona cap d'altre. Es proposen els aparells que encara no té apresos i els que més vegades fan
     * (on més pot aprendre), sense els que ja tenen endoll. El que l'usuari ha dit de deixar com és, no.
     */
    fun propostesMoure(): List<PropostaMoure> {
        val oberts = bd.periodesEndoll().filter { it.finsA == null }
        if (oberts.isEmpty()) return emptyList()
        val ignorats = config.moureIgnorats
        val aparells = bd.aparells()
        val endolls = bd.endolls()
        val ambEndoll = oberts.map { it.aparellId }.toSet()
        val refs = referencies()
        val setmana = bd.vegades(ara() - 7 * 86400, ara() + 60)
        val candidats = aparellsAEntrenar().filter { it.id !in ambEndoll }
        val suggerits = (candidats.filter { refs[it.id] == null } +
            candidats.filter { refs[it.id] != null }.sortedByDescending { c -> setmana.count { it.aparellId == c.id } }).take(3)
        return oberts.mapNotNull { p ->
            if ("${p.endollId}:${p.aparellId}" in ignorats) return@mapNotNull null
            val e = endolls.firstOrNull { it.id == p.endollId } ?: return@mapNotNull null
            val a = aparells.firstOrNull { it.id == p.aparellId } ?: return@mapNotNull null
            val q = queNHaApres(a.id, e, p)?.takeIf { it.apres } ?: return@mapNotNull null
            PropostaMoure(e, a, q, suggerits)
        }
    }

    /** «Deixa'l on és»: aquesta proposta no es torna a fer mentre l'endoll sigui en aquest aparell. */
    fun ignoraMoure(endollId: Long, aparellId: Long) {
        config.moureIgnorats = config.moureIgnorats + "$endollId:$aparellId"
    }
}
