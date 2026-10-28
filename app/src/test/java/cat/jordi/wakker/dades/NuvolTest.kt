package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvolTest {
    @Test
    fun horaSenseExactes() {
        val m = minutsDelNuvol(3600, 120.0, emptyMap())
        assertEquals(60, m.size)
        assertEquals(120.0, m.sumOf { it.second }, 1e-9)
        assertEquals(2.0, m.first().second, 1e-9)
    }

    @Test
    fun horaAmbMitjaHoraExacta() {
        // 30 minuts exactes amb 1,5 Wh cadascun (45 Wh): els 75 Wh que queden es reparteixen pels altres 30
        val exactes = (0 until 30).associate { 3600L + it * 60 to 1.5 }
        val m = minutsDelNuvol(3600, 120.0, exactes)
        assertEquals(30, m.size)
        assertTrue(m.none { it.first in exactes })
        assertEquals(75.0, m.sumOf { it.second }, 1e-9)
    }

    @Test
    fun horaCompleta() {
        val exactes = (0 until 60).associate { 3600L + it * 60 to 2.0 }
        assertTrue(minutsDelNuvol(3600, 120.0, exactes).isEmpty())
    }
}
