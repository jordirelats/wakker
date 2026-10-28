package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
