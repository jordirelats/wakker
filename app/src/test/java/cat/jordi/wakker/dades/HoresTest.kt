package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HoresTest {
    private fun ts(zona: String, data: String) = LocalDateTime.parse(data).atZone(ZoneId.of(zona)).toEpochSecond()

    @Test
    fun hora_en_punt_UTC() {
        val z = ZoneId.of("Europe/Madrid")
        assertEquals(0L, desfasHora(z, ts("Europe/Madrid", "2026-10-26T10:00")))
        assertEquals(ts("Europe/Madrid", "2026-10-26T10:00"), iniciHora(ts("Europe/Madrid", "2026-10-26T10:47"), z))
    }

    @Test
    fun zones_de_mitja_hora() {
        // A l'Índia (+5:30), les 10:47 són de l'hora de les 10:00 locals, no de la que comença a les 10:30
        val india = ZoneId.of("Asia/Kolkata")
        assertEquals(1800L, desfasHora(india, ts("Asia/Kolkata", "2026-10-26T10:47")))
        assertEquals(ts("Asia/Kolkata", "2026-10-26T10:00"), iniciHora(ts("Asia/Kolkata", "2026-10-26T10:47"), india))
        assertEquals(ts("Asia/Kolkata", "2026-10-26T10:00"), iniciHora(ts("Asia/Kolkata", "2026-10-26T10:00"), india))
        // Terranova (−3:30 a l'hivern) i el Nepal (+5:45)
        val terranova = ZoneId.of("America/St_Johns")
        assertEquals(ts("America/St_Johns", "2026-12-01T23:00"), iniciHora(ts("America/St_Johns", "2026-12-01T23:59"), terranova))
        val nepal = ZoneId.of("Asia/Kathmandu")
        assertEquals(2700L, desfasHora(nepal, ts("Asia/Kathmandu", "2026-10-26T06:10")))
        assertEquals(ts("Asia/Kathmandu", "2026-10-26T06:00"), iniciHora(ts("Asia/Kathmandu", "2026-10-26T06:10"), nepal))
    }
}
