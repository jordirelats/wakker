package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpisodisTest {
    private val t0 = 1_792_800_000L

    /** Una sèrie de [n] minuts de fons (100 W, constant) amb el que hi posa [aparell] (W mitjans, màxim i mínim del minut). */
    private fun serie(n: Int, aparell: (Int) -> Triple<Double, Double, Double>?): List<MinutW> = (0 until n).map { i ->
        val a = aparell(i)
        MinutW(t0 + i * 60L, 100.0 + (a?.first ?: 0.0), 102.0 + (a?.second ?: 0.0), 98.0 + (a?.third ?: 0.0))
    }

    private fun constant(w: Double) = Triple(w, w, w)

    @Test
    fun unaResistenciaEsConstant() {
        val eps = Episodis.troba(serie(200) { if (it in 100 until 110) constant(2000.0) else null })
        assertEquals(1, eps.size)
        val e = eps[0]
        assertEquals(10, e.minuts)
        assertEquals(2000.0, e.w, 1.0)
        assertEquals(Forma.CONSTANT, e.forma)
        assertEquals(2000.0 * 10 / 60_000, e.kwh, 0.001)
    }

    @Test
    fun laBombaDeCalorArrencaSuau() {
        // Com la de Guíxols: puja a poc a poc fins als 490 W
        val eps = Episodis.troba(serie(300) { i ->
            when (i - 100) {
                0 -> Triple(250.0, 300.0, 200.0)
                1 -> Triple(350.0, 380.0, 320.0)
                in 2 until 70 -> constant(490.0)
                else -> null
            }
        })
        assertEquals(1, eps.size)
        assertEquals(Forma.SUAU, eps[0].forma)
        assertEquals(490.0, eps[0].w, 5.0)
    }

    @Test
    fun unMotorFaUnPic() {
        // 370 W durant 6 minuts, amb un pic de 1.100 W en arrencar
        val eps = Episodis.troba(serie(200) { i ->
            when (i - 100) {
                0 -> Triple(370.0, 1100.0, 0.0)
                in 1 until 6 -> constant(370.0)
                else -> null
            }
        })
        assertEquals(1, eps.size)
        assertEquals(Forma.PIC, eps[0].forma)
    }

    @Test
    fun laPlacaPolsa() {
        val eps = Episodis.troba(serie(200) { if (it in 100 until 120) Triple(1100.0, 2200.0, 0.0) else null })
        assertEquals(1, eps.size)
        assertEquals(Forma.POLSA, eps[0].forma)
    }

    @Test
    fun elQueEsRepeteix() {
        // 380 W durant 6 minuts cada 3 hores, durant un dia i mig; i un rentaplats un sol cop
        val eps = Episodis.troba(serie(36 * 60) { i ->
            when {
                i >= 60 && (i - 60) % 180 < 6 -> constant(380.0)
                i in 1000 until 1010 -> constant(2100.0)
                else -> null
            }
        })
        val grups = Episodis.repetits(eps)
        assertEquals(1, grups.size)
        val r = Episodis.repetit(grups[0])
        assertTrue(r.cops.size >= 6)
        assertEquals(180.0, r.cadaMinuts, 1.0)
        assertEquals(380.0, r.w, 5.0)
        assertTrue(Episodis.mateix(r, 400.0, 6.0))
        assertTrue(!Episodis.mateix(r, 2100.0, 10.0))
    }

    private fun aparell(id: Long, c: Categoria) = Aparell(id, c.name, c, Font.TIPIC, c.kwhAnyTipic)

    @Test
    fun aQuinAparellDeLaLiniaToca() {
        val cotxe = aparell(1, Categoria.COTXE)
        val bomba = aparell(2, Categoria.TERMO)
        fun ep(w: Double, minuts: Int) = Episodi(t0, t0 + (minuts - 1) * 60L, minuts, w, w * minuts / 60_000, 0.0, 1.0, List(minuts) { w })
        // Sense cap mostra, amb la potència típica: 490 W, la bomba; 2,8 kW durant hores, el cotxe
        assertEquals(bomba, triaLinia(ep(490.0, 70), listOf(cotxe, bomba), emptyMap()))
        assertEquals(cotxe, triaLinia(ep(2800.0, 180), listOf(cotxe, bomba), emptyMap()))
        // Si només hi ha el cotxe, 490 W no són seus (massa lluny)
        assertNull(triaLinia(ep(490.0, 70), listOf(cotxe), emptyMap()))
        // Entre dos que s'hi assemblen igual, no se sap
        assertNull(triaLinia(ep(2000.0, 30), listOf(aparell(3, Categoria.BULLIDOR), aparell(4, Categoria.PLANXA)), emptyMap()))
        // Amb la bomba apresa (interval de 390 a 700 W), qualsevol potència de dins és seva
        val refs = mapOf(2L to Referencia(2, 490.0, 2, 70.0, 390.0, 700.0))
        assertEquals(bomba, triaLinia(ep(650.0, 60), listOf(cotxe, bomba), refs))
    }

    @Test
    fun laDistanciaTeEnCompteLaFirma() {
        val bullidor = aparell(1, Categoria.BULLIDOR)
        val rentaplats = aparell(2, Categoria.RENTAPLATS)
        val refs = mapOf(
            1L to Referencia(1, 2100.0, 3, 3.0, arrencada = 1.0, variacio = 0.0),
            2L to Referencia(2, 2100.0, 3, 10.0, arrencada = 1.0, variacio = 0.0),
        )
        // La mateixa potència: decideix la durada
        assertEquals(listOf(rentaplats), endevina(2100.0, listOf(bullidor, rentaplats), refs, 1, minuts = 11.0))
        assertEquals(listOf(bullidor), endevina(2100.0, listOf(bullidor, rentaplats), refs, 1, minuts = 3.0))
    }

    @Test
    fun lIntervalNoComencaAZero() {
        // Un minut per sota de la base dins de l'episodi no fa que l'interval comenci a 0
        val e = Episodi(t0, t0 + 14 * 60L, 15, 490.0, 0.1, 0.0, 0.7, List(13) { 490.0 } + listOf(0.0, 0.0))
        assertTrue(e.interval.first > 300.0)
    }

    @Test
    fun siSemblaQueSEngegaSol() {
        val zona = java.time.ZoneId.of("Europe/Madrid")
        fun r(cops: List<Long>) = Repetit(0, 380.0, 330.0, 400.0, 6.0, 180.0, 0.3, 0.0, 3.0, cops.map { Triple(it, 380.0, 6) }, EstatPregunta.PENDENT, null)
        // Cada 3 hores, també de nit: sí
        assertTrue(r((0 until 8).map { t0 + it * 3 * 3600L }).sembla(zona))
        // El bullidor de cada matí a les 8: no
        val mati = java.time.LocalDate.of(2026, 10, 19).atTime(8, 0).atZone(zona).toEpochSecond()
        assertTrue(!r((0 until 7).map { mati + it * 86400L }).sembla(zona))
    }

    @Test
    fun elQueSEncenASobreEsUnaAltraVegada() {
        // El cotxe (2,4 kW, 6 hores) i, a sobre, la placa (2,5 kW que polsa) durant 20 minuts, com el dissabte 24 a Guíxols
        val eps = Episodis.troba(serie(600) { i ->
            when {
                i in 150 until 170 -> Triple(2400.0 + 2500.0, 2400.0 + 4000.0, 2400.0 + 500.0)
                i in 100 until 460 -> constant(2400.0)
                else -> null
            }
        }, llargada = 2)
        assertEquals(2, eps.size)
        val (cotxe, placa) = eps.sortedByDescending { it.minuts }
        assertEquals(360, cotxe.minuts)
        assertEquals(2400.0, cotxe.w, 1.0)
        // El cotxe es queda el seu nivell, i la placa, el que hi passa per sobre: la suma és tot el que passava de la base
        assertEquals(2400.0 * 360 / 60_000, cotxe.kwh, 0.01)
        assertEquals(20, placa.minuts)
        assertEquals(2500.0, placa.w, 1.0)
        assertEquals(Forma.POLSA, placa.forma)
        assertEquals((2400.0 * 360 + 2500.0 * 20) / 60_000, cotxe.kwh + placa.kwh, 0.01)
        // La placa sola polsa: els seus minuts alts no són una altra cosa
        val sola = Episodis.troba(serie(300) { i ->
            if (i in 100 until 140) (if (i % 3 == 0) Triple(3500.0, 4000.0, 0.0) else Triple(1500.0, 4000.0, 0.0)) else null
        }, llargada = 2)
        assertEquals(1, sola.size)
        // Un aparell que va pujant (la bomba de calor) tampoc es parteix
        val puja = Episodis.troba(serie(300) { i -> if (i in 100 until 170) constant(330.0 + (i - 100) * 5.0) else null }, llargada = 2)
        assertEquals(1, puja.size)
    }

    /** Les dades d'una pinça amb tots els camps (fora del repositori): [ts, energia, ..., màx, mín, ...]. */
    private fun llegeix(nom: String): List<MinutW>? {
        val fitxer = File(System.getProperty("user.home"), "Projectes/Consum-Shelly/recerca/reactiva/$nom")
        if (!fitxer.exists()) return null
        return Regex("""\[(\d{10}),([^\[\]]*)]""").findAll(fitxer.readText()).map { m ->
            val v = m.groupValues[2].split(",").map { it.trim().toDouble() }
            // total_act_energy (Wh del minut), ..., max_act_power, min_act_power
            MinutW(m.groupValues[1].toLong(), v[0] * 60, v[4], v[5])
        }.toList()
    }

    /** Amb la setmana real de Guíxols (19–26/10): la bomba de calor al garatge i el que es repeteix a la resta. */
    @Test
    fun setmanaRealDeGuixols() {
        val total = llegeix("dades_completes_guixols.json") ?: return
        val garatge = llegeix("dades_completes_guixols_pinca2.json") ?: return
        // Al garatge, la bomba de calor de l'aigua: dues vegades, uns 490 W, més d'una hora, arrencada suau
        val bomba = Episodis.troba(garatge).filter { it.minuts >= 30 }
        assertEquals(2, bomba.size)
        bomba.forEach {
            assertEquals(490.0, it.w, 20.0)
            assertEquals(Forma.SUAU, it.forma)
        }
        // A la resta de la casa: la cosa de 330–460 W que fa un pic, cada 3 hores, i la de 3 minuts cada quart d'hora
        val g = garatge.associateBy { it.ts }
        val resta = total.map { m -> val l = g[m.ts]?.w ?: 0.0; MinutW(m.ts, m.w - l, m.max - l, m.min - l) }
        val repetits = Episodis.repetits(Episodis.troba(resta)).map(Episodis::repetit)
        val motor = repetits.single { it.minuts in 5.0..7.0 }
        assertEquals(Forma.PIC, motor.forma)
        assertTrue(motor.cadaMinuts in 120.0..240.0)
        // (des de la 0.28 també hi ha els cicles que s'encenen a sobre de la càrrega del cotxe: el més nombrós és el del cap de setmana)
        val curt = repetits.filter { it.minuts <= 3.0 }.maxBy { it.cops.size }
        assertTrue(curt.cops.size >= 30)
        assertEquals(Forma.CONSTANT, curt.forma)
        // Dissabte 24 (maqueta 16): la cuina de les 20:31, a sobre de la càrrega del cotxe de les 19:54, és una vegada a part
        val zona = java.time.ZoneId.of("Europe/Madrid")
        fun a(h: Int, m: Int) = java.time.LocalDate.of(2026, 10, 24).atTime(h, m).atZone(zona).toEpochSecond()
        val vegades = Episodis.troba(resta, llargada = 2)
        val cotxe = vegades.single { it.ts in a(19, 50)..a(19, 58) && it.minuts > 300 }
        val cuina = vegades.filter { it.ts in a(20, 28)..a(21, 10) && it.w > 1500 }
        assertTrue(cuina.isNotEmpty())
        // El cotxe es queda uns 14–16 kWh (abans, 18,05 kWh amb tot el que tenia a sobre)
        assertTrue(cotxe.kwh in 13.5..16.5)
    }
}
