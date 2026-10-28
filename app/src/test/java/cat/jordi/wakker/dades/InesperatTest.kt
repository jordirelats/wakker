package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

class InesperatTest {
    private fun ref(id: Long, w: Double, min: Double? = null, max: Double? = null) = Referencia(id, w, 3, null, min, max)

    @Test
    fun explicadorAmbValorsComAbans() {
        val e = Explicador(listOf(ref(1, 100.0), ref(2, 2000.0)))
        val tot = e.explica(2100.0)
        assertEquals(0.0, tot.residu, 1e-9)
        assertEquals(setOf(1L, 2L), tot.aparells.map { it.first }.toSet())
        // 50 W no arriben als 100 W de la nevera, ni amb el marge
        assertEquals(50.0, e.explica(50.0).residu, 1e-9)
        assertEquals(500.0, e.explica(2600.0).residu, 1e-9)
    }

    @Test
    fun explicadorAmbInterval() {
        // La bomba de calor de l'aigua de Guíxols: 254–784 W; la nevera, 52 W
        val e = Explicador(listOf(ref(1, 52.0), ref(2, 500.0, 254.0, 784.0)))
        assertEquals(0.0, e.explica(600.0).residu, 1e-9)
        assertEquals(0.0, e.explica(300.0).residu, 1e-9)
        // Per sobre del màxim no se sap si la bomba és en marxa: no se'n resta res, només la nevera
        assertEquals(1000.0 - 52.0, e.explica(1000.0).residu, 1e-9)
        // Un consum gran desconegut no queda amagat per la bomba
        assertEquals(2500.0 - 52.0, e.explica(2500.0).residu, 1e-9)
        // Per sota del mínim, només la nevera
        val petit = e.explica(60.0)
        assertEquals(listOf(1L), petit.aparells.map { it.first })
        // El repartiment suma el que s'explica
        val r = e.explica(650.0)
        assertEquals(650.0, r.aparells.sumOf { it.second }, 1e-6)
    }

    @Test
    fun detectorAvisaUnCopPerEpisodi() {
        val d = DetectorInesperat(2000.0, 30)
        val t0 = 1_000_000L
        val avisos = mutableListOf<Long>()
        // 50 minuts a 2,5 kW, amb una baixada de 5 minuts al mig
        for (m in 0 until 50) {
            val w = if (m in 20..24) 1000.0 else 2500.0
            if (d.mira(t0 + m * 60, w)) avisos += t0 + m * 60
        }
        assertEquals(listOf(t0 + 30 * 60), avisos)
        // Baixa 20 minuts i torna a pujar: és un altre episodi, però encara no fa 2 hores de l'avís
        for (m in 50 until 70) assertTrue(!d.mira(t0 + m * 60, 100.0))
        for (m in 70 until 110) assertTrue(!d.mira(t0 + m * 60, 2500.0))
        // Passades les 2 hores, sí
        var tard = false
        for (m in 150 until 200) if (d.mira(t0 + m * 60, 2500.0)) tard = true
        assertTrue(tard)
    }

    @Test
    fun intervalDUnEpisodi() {
        // Una rampa de 300 a 650 W durant 60 minuts, i després s'apaga
        val exces = (0 until 60).map { 300.0 + it * 6 } + List(10) { 5.0 }
        val (lo, hi) = intervalEpisodi(exces, 500.0)!!
        assertEquals(0.8 * 336.0, lo, 1.0)
        assertEquals(1.2 * 654.0, hi, 1.0)
        assertNull(intervalEpisodi(listOf(600.0, 600.0, 5.0, 5.0, 5.0), 600.0))
    }

    /** Amb la setmana real de Guíxols (fora del repositori): el compressor de la nevera, 52 W. Només si hi ha el fitxer. */
    @Test
    fun neveraRealDeGuixols() {
        val fitxer = File(System.getProperty("user.home"), "Projectes/Consum-Shelly/maqueta/dades_setmana_guixols.json")
        if (!fitxer.exists()) return
        val minuts = Regex("""\[(\d+),\s*([\d.]+),\s*[\d.]+]""").findAll(fitxer.readText())
            .map { it.groupValues[1].toLong() to it.groupValues[2].toDouble() }.toList()
        val w = potenciaCompressor(minuts, ZoneId.of("Europe/Madrid"))
        assertNotNull(w)
        assertEquals(52.0, w!!, 6.0)
    }

    @Test
    fun senseCiclesNoHiHaNevera() {
        val pla = (0 until 2000).map { 1_792_800_000L + it * 60L to 90.0 }
        assertNull(potenciaCompressor(pla, ZoneId.of("Europe/Madrid")))
    }
}
