package cat.jordi.wakker.dades

import cat.jordi.wakker.shelly.horesDelNuvol
import cat.jordi.wakker.shelly.normalitzaMac
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** El que van trobar les revisions de la 1.1.3, perquè no torni. */
class RevisioTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    /** El dia que es retarda l'hora (25/10/2026, resposta real del núvol): les dues «02:00» són dues hores diferents. */
    @Test
    fun nuvolCanviHora() {
        val hores = horesDelNuvol(
            listOf("2026-10-25 01:00:00" to 2633.66, "2026-10-25 02:00:00" to 19.82, "2026-10-25 02:00:00" to 2656.82, "2026-10-25 03:00:00" to 650.34),
            madrid, LocalDate.of(2026, 10, 25),
        )
        assertEquals(4, hores.size)
        assertEquals(4, hores.map { it.first }.distinct().size)
        assertEquals(3600L, hores[2].first - hores[1].first)
        assertEquals(hores.sumOf { it.second }, 2633.66 + 19.82 + 2656.82 + 650.34, 1e-9)
    }

    /** Les hores que falten no hi són, però compten per saber si la següent és la repetida. */
    @Test
    fun nuvolHoresQueFalten() {
        val hores = horesDelNuvol(listOf("2026-10-28 10:00:00" to 100.0, "2026-10-28 11:00:00" to null), madrid, LocalDate.of(2026, 10, 28))
        assertEquals(1, hores.size)
    }

    /** Amb el treball de fons (una crida cada 15 minuts i escaig) i la pausa llarga, el consum que dura avisa. */
    @Test
    fun inesperatCada15Minuts() {
        var c = DetectorInesperat(2000.0, 30)
        var avisos = 0
        var ts = 1_792_800_000L
        repeat(16) {
            c = DetectorInesperat(2000.0, 30, c.des, c.alt, c.avisat, c.darrerAvis)
            if (c.mira(ts, 3000.0, pausa = 45 * 60L)) avisos++
            ts += 15 * 60 + 5
        }
        assertEquals(1, avisos)
    }

    /** Amb la pausa de sempre (minut a minut), una crida cada 15 minuts no avisaria mai: per això el treball de fons fa servir la llarga. */
    @Test
    fun inesperatPausaCurta() {
        var c = DetectorInesperat(2000.0, 30)
        var avisos = 0
        var ts = 1_792_800_000L
        repeat(16) {
            c = DetectorInesperat(2000.0, 30, c.des, c.alt, c.avisat, c.darrerAvis)
            if (c.mira(ts, 3000.0)) avisos++
            ts += 15 * 60 + 5
        }
        assertEquals(0, avisos)
    }

    /** Una vegada de la pinça 2 sense aparells a la línia: si no es passen etiquetes d'altres aparells, és de ningú. */
    @Test
    fun etiquetaLiniaSenseCandidats() {
        val t0 = 1_792_900_000L
        val v = Vegada(0, 1, t0, t0 + 69 * 60L, 70, 490.0, 0.57, 0.0, 0.7)
        val etiquetes = listOf(Etiqueta(1, t0, t0, 2L)).filter { e -> emptyList<Long>().contains(e.aparellId) }
        val (a, _) = assigna(v, emptyList(), emptyMap(), etiquetes)
        assertNull(a)
        assertTrue(etiquetes.isEmpty())
    }

    @Test
    fun laMacComLaDesaLApp() {
        // La de Shelly.GetStatus (sys.mac), la de GetDeviceInfo i la que escriu algú a mà acaben igual
        assertEquals("aabbcc123456", normalitzaMac("AABBCC123456"))
        assertEquals("aabbcc123456", normalitzaMac(" AA:BB:CC:12:34:56 "))
        assertEquals("aabbcc123456", normalitzaMac("aa-bb-cc-12-34-56"))
    }
}
