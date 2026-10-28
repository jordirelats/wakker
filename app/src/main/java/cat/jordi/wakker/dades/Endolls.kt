package cat.jordi.wakker.dades

import kotlin.math.max

/**
 * Els endolls amb mesura (maqueta 19): un Tapo P110 o P115 posat a un aparell diu exactament quan l'aparell és encès i
 * quant gasta. L'app el llegeix a casa, com el Shelly, i amb això:
 * - de qui és cada vegada del total que coincideix amb l'endoll encès (sense preguntar res), i quines no eren d'aquell
 *   aparell encara que la firma s'hi assemblés ([veredicteEndoll]);
 * - l'aparell s'aprèn sol: cada vegada confirmada és una mostra de la seva firma;
 * - quant l'encertava l'app sense l'endoll ([QueNHaApres]), per saber quan es pot moure a un altre aparell;
 * - el consum exacte d'un aparell petit (una tele), que al total no es veu encendre's.
 */

/** De quina marca és un endoll: en canvia el protocol i el que sap fer. */
enum class TipusEndoll(val clau: String) {
    /** TP-Link Tapo (P110, P115): KLAP, amb el compte de Tapo; guarda l'historial per hores; només a casa. */
    TAPO("tapo"),

    /** Un Shelly amb mesura d'un sol canal (Plus Plug S, Plug S Gen3, 1PM Mini…): el RPC de l'EM, sense historial, també pel núvol. */
    SHELLY("shelly");

    companion object {
        fun de(clau: String?) = entries.firstOrNull { it.clau == clau } ?: TAPO
    }
}

/** Un endoll configurat: on és, qui és, i el nom que té a la seva app. */
data class Endoll(val id: Long, val host: String, val mac: String, val model: String, val nom: String, val tipus: TipusEndoll = TipusEndoll.TAPO)

/** A quin aparell ha estat posat un endoll i quan: [finsA] null vol dir que encara hi és. */
data class PeriodeEndoll(val id: Long, val endollId: Long, val aparellId: Long, val desDe: Long, val finsA: Long?) {
    fun cobreix(ts: Long) = ts >= desDe && (finsA == null || ts < finsA)
}

/** Un minut d'un endoll: l'energia (Wh) i, si ve d'una lectura en directe, la potència mitjana. [aprox]: repartit de 5 minuts. */
data class MinutEndoll(val ts: Long, val wh: Double, val aprox: Boolean = false) {
    val w get() = wh * 60
}

/** El que l'endoll diu d'una vegada del total. */
enum class Veredicte {
    /** L'endoll de l'aparell era encès a una potència comparable: la vegada és seva. */
    SEU,

    /** L'endoll era apagat (o gairebé): no era aquest aparell. */
    NO_SEU,

    /** No hi ha prou minuts de l'endoll d'aquella estona, o no es pot dir. */
    NO_SE_SAP,
}

/**
 * Què diu l'endoll d'un aparell d'una vegada del total ([vW], de [desDe] a [finsA], l'últim minut inclòs): amb els minuts
 * de l'endoll d'aquella estona. Cal que n'hi hagi almenys la meitat. Si la potència mitjana de l'endoll arriba a la
 * meitat de la de la vegada (l'aparell pot anar amb una altra cosa a sobre), és seva; si el màxim no arriba al 15 % (ni
 * a 50 W), l'endoll era apagat; entremig no se sap (l'endoll fa menys que la vegada: hi havia una altra cosa).
 */
fun veredicteEndoll(vW: Double, desDe: Long, finsA: Long, minuts: List<MinutEndoll>, abans: List<MinutEndoll> = emptyList()): Veredicte {
    val n = (finsA - desDe) / 60 + 1
    val dins = minuts.filter { it.ts in desDe..finsA }
    if (dins.size * 2 < n || dins.isEmpty()) return Veredicte.NO_SE_SAP
    val mitjana = dins.sumOf { it.w } / dins.size
    val maxim = dins.maxOf { it.w }
    if (maxim < max(0.15 * vW, 50.0)) return Veredicte.NO_SEU
    // El que compta és el salt de l'endoll, no la seva potència: una estufa amb endoll que ja anava a 2 kW no és el forn
    // que s'encén a sobre. Sense els minuts d'abans (l'endoll acabat de posar), no se sap.
    val previs = abans.filter { it.ts in (desDe - 600) until desDe }
    if (previs.size < 3) return Veredicte.NO_SE_SAP
    val abansW = previs.sumOf { it.w } / previs.size
    return if (mitjana - abansW >= 0.5 * vW) Veredicte.SEU else Veredicte.NO_SE_SAP
}

/**
 * El mateix, només amb les hores de l'endoll (Wh per hora, [hores]: l'inici de cada hora i els Wh), quan no hi ha els
 * minuts (abans de posar l'endoll a l'app, només hi ha el seu historial per hores). Cal tenir totes les hores que la vegada
 * toca. Si l'endoll ha gastat en aquelles hores entre el 60 % i el 150 % del que ha gastat la vegada, és seva (dues
 * vegades iguals a la mateixa hora farien el doble: no se sap); si no arriba al 15 % (ni a 20 Wh), era apagat.
 */
fun veredicteHores(kwh: Double, desDe: Long, finsA: Long, hores: List<Pair<Long, Double>>, altresWh: Double = 0.0, iniciHora: (Long) -> Long): Veredicte {
    val primera = iniciHora(desDe)
    val darrera = iniciHora(finsA)
    val dins = hores.filter { it.first in primera..darrera }
    // Totes les hores, cada una un cop (una hora de 25 el dia del canvi no s'hi té en compte)
    val n = ((darrera - primera) / 3600 + 1).toInt()
    if (dins.map { it.first }.distinct().size < n) return Veredicte.NO_SE_SAP
    val wh = dins.sumOf { it.second }
    val vegadaWh = kwh * 1000
    return when {
        wh < max(0.15 * vegadaWh, 20.0) -> Veredicte.NO_SEU
        wh in vegadaWh * 0.6..vegadaWh * 1.5 && vegadaWh >= 0.6 * (vegadaWh + altresWh) -> Veredicte.SEU
        else -> Veredicte.NO_SE_SAP
    }
}

/** Un dia de la comparació: el que l'app donava a l'aparell sense l'endoll i el que l'endoll ha mesurat (kWh). */
data class DiaEndoll(val dia: java.time.LocalDate, val appSola: Double, val endoll: Double, val quadra: Boolean)

/**
 * Què n'ha après l'app d'un aparell amb l'endoll (la fitxa): de les vegades que l'endoll confirma, quantes hauria
 * reconegut sola ([encertades] de [seves]); quantes d'altres aparells li donava i l'endoll diu que no ([deMes]); i la
 * comparació de cada dia. Quan l'encerta totes, de [seves] >= 3 i cap de més durant una setmana sencera, es pot moure
 * l'endoll ([apres]).
 */
data class QueNHaApres(val seves: Int, val encertades: Int, val deMes: Int, val dies: List<DiaEndoll>, val desDe: Long) {
    /** Els dies són sencers (d'ahir enrere): set dies amb l'endoll, 3 vegades seves o més, totes encertades i cap de més. */
    val apres get() = seves >= 3 && encertades == seves && deMes == 0 && dies.size >= 7
}

/** El consum d'un aparell petit mesurat per l'endoll: la mitjana per dia, les hores encès i l'espera. */
data class MesuraEndoll(
    val whDia: Double, val dies: Int, val perDia: List<Pair<java.time.LocalDate, Double>>,
    /** Només amb minuts en directe: les hores encès al dia, la potència quan és encès i la d'espera. */
    val horesDia: Double?, val encesW: Double?, val esperaW: Double?,
)

/**
 * Les hores encès al dia, la potència encès i la d'espera, dels minuts en directe d'un endoll: l'espera és el percentil 10
 * de la potència; encès, de 10 W o més i 3 vegades l'espera. Null si hi ha menys de 6 hores de minuts.
 */
fun usEndoll(minuts: List<MinutEndoll>): Triple<Double, Double, Double>? {
    if (minuts.size < 6 * 60) return null
    val ws = minuts.map { it.w }.sorted()
    val espera = ws[ws.size / 10]
    val llindar = max(10.0, espera * 3)
    val encesos = ws.filter { it >= llindar }
    val hores = encesos.size / 60.0 / (minuts.size / 1440.0)
    val encesW = if (encesos.isEmpty()) 0.0 else encesos.sum() / encesos.size
    return Triple(hores, encesW, espera)
}
