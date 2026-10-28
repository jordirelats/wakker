package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class EstonaPassadaTest {
    @Test
    fun dosTramsSintetics() {
        // Base de 100 W; un aparell de 2 kW 10 minuts, un de 300 W 6 minuts i un altre cop el de 2 kW
        val ws = List(20) { 100.0 } + List(10) { 2100.0 } + List(10) { 100.0 } + List(6) { 400.0 } + List(20) { 100.0 } +
            List(9) { 2100.0 } + List(20) { 100.0 }
        val punts = ws.mapIndexed { i, w -> 1_000_000L + i * 60 to w }
        val trams = EstonaPassada.trams(punts)
        assertEquals(3, trams.size)
        assertEquals(2000.0, trams[0].puja, 1.0)
        assertEquals(2000.0, trams[0].baixa, 1.0)
        assertEquals(10, trams[0].minuts)
        assertEquals(2000.0, trams[0].w, 1.0)
        assertEquals(setOf(0, 2), EstonaPassada.proposats(trams))
    }

    @Test
    fun unMinutPerSotaNoTallaElTram() {
        val ws = List(10) { 100.0 } + List(5) { 2100.0 } + listOf(300.0) + List(5) { 2100.0 } + List(10) { 100.0 }
        val trams = EstonaPassada.trams(ws.mapIndexed { i, w -> 1_000_000L + i * 60 to w })
        assertEquals(1, trams.size)
        assertEquals(11, trams[0].minuts)
    }

    /** El rentaplats de Guíxols, diumenge 25/10 de 17 a 21 h (fora del repositori). Només si hi ha el fitxer. */
    @Test
    fun rentaplatsRealDeGuixols() {
        val fitxer = File(System.getProperty("user.home"), "Projectes/Consum-Shelly/maqueta/dades_setmana_guixols.json")
        if (!fitxer.exists()) return
        val zona = ZoneId.of("Europe/Madrid")
        val inici = LocalDateTime.of(2026, 10, 25, 17, 0).atZone(zona).toEpochSecond()
        val fi = LocalDateTime.of(2026, 10, 25, 21, 0).atZone(zona).toEpochSecond()
        val punts = Regex("""\[(\d+),\s*([\d.]+),\s*[\d.]+]""").findAll(fitxer.readText())
            .map { it.groupValues[1].toLong() to it.groupValues[2].toDouble() }.filter { it.first in inici until fi }.toList()
        val trams = EstonaPassada.trams(punts)
        val triats = EstonaPassada.proposats(trams).map { trams[it] }
        assertEquals(2, triats.size)
        triats.forEach { assertTrue(it.w in 2000.0..2400.0 && it.mostres.all { m -> m in 1900.0..2400.0 }) }
        assertEquals(0.72, triats.sumOf { it.kwh }, 0.05)
    }
}
