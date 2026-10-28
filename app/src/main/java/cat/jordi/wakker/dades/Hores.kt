package cat.jordi.wakker.dades

import java.time.Instant
import java.time.ZoneId

/**
 * Quants segons passa l'hora local de l'hora en punt UTC: 0 a gairebé tot el món, 1.800 a l'Índia (+5:30) o a
 * Terranova (−3:30) i 2.700 al Nepal (+5:45). El consum i el preu s'agrupen per hores locals, que en aquestes
 * zones no comencen a l'hora en punt UTC.
 */
fun desfasHora(zona: ZoneId, ts: Long): Long = zona.rules.getOffset(Instant.ofEpochSecond(ts)).totalSeconds.toLong().mod(3600L)

/** L'inici (s) de l'hora local en què cau [ts]. */
fun iniciHora(ts: Long, zona: ZoneId): Long {
    val d = desfasHora(zona, ts)
    return Math.floorDiv(ts + d, 3600L) * 3600 - d
}
