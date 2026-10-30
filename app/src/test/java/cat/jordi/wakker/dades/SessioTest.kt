package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ensenyar un aparell d'inici a fi (maqueta 21): la base d'abans, l'estona com una sola vegada, i el que hi ha a sobre. */
class SessioTest {
    private val t0 = 1_700_000_000L / 60 * 60

    private fun m(i: Int, w: Double) = MinutW(t0 + i * 60L, w, w * 1.02, w * 0.98)

    /**
     * Una casa de 120 W. Un aire inverter s'engega al minut 30: 700 W el primer minut i després cicles de 300–420 W, 2 de
     * cada 3 minuts. Del minut 100 al 120, una placa de 2,5 kW a sobre.
     */
    private val casa = (0 until 200).map { i ->
        val aire = when {
            i < 30 -> 0.0
            i == 30 -> 700.0
            i % 3 == 0 -> 0.0
            else -> 300.0 + (i % 5) * 30
        }
        m(i, 120 + aire + if (i in 100..120) 2500.0 else 0.0)
    }
    private val abans = casa.filter { it.ts < t0 + 30 * 60 }
    private val dins = casa.filter { it.ts >= t0 + 30 * 60 }

    @Test
    fun laBaseEsLaDAbans() {
        assertEquals(120.0, Sessio.base(abans, dins), 0.01)
        // Sense la mitja hora d'abans, el 20 % més baix de l'estona (l'aire parat)
        assertEquals(120.0, Sessio.base(emptyList(), dins), 0.01)
    }

    @Test
    fun laPlacaDeSobreNoEsSeva() {
        val placa = Vegada(0, 0, t0 + 100 * 60, t0 + 120 * 60, 21, 2500.0, 0.88, 0.01, 1.0)
        val cicle = Vegada(0, 0, t0 + 40 * 60, t0 + 41 * 60, 2, 400.0, 0.01, 0.01, 1.0)
        val nivell = Sessio.nivell(dins, 120.0)
        val alienes = Sessio.alienes(listOf(placa, cicle), dins, 120.0, t0 + 30 * 60, t0 + 200 * 60, 7, nivell, 900 * 1.3)
        assertEquals(listOf(placa), alienes)
        // Sense cap vegada de la placa (anava dins de la de l'aire), es troba minut a minut
        val perMinuts = Sessio.alienes(emptyList(), dins, 120.0, t0 + 30 * 60, t0 + 200 * 60, 7, nivell, 900 * 1.3)
        assertEquals(1, perMinuts.size)
        assertEquals(t0 + 100 * 60, perMinuts[0].ts)
        assertEquals(21, perMinuts[0].minuts)
        // Si l'usuari (o un endoll) diu que el cicle era de l'estufa, tampoc és seu; si només s'hi assemblava, sí
        val senseSobre = dins.filter { it.ts !in t0 + 100 * 60..t0 + 120 * 60 }
        val estufa = cicle.copy(aparellId = 9, com = Com.ENSENYAT)
        assertEquals(listOf(estufa), Sessio.alienes(listOf(estufa), senseSobre, 120.0, t0 + 30 * 60, t0 + 200 * 60, 7, nivell, null))
        assertTrue(Sessio.alienes(listOf(cicle.copy(aparellId = 9, com = Com.RECONEGUT)), senseSobre, 120.0, t0 + 30 * 60, t0 + 200 * 60, 7, nivell, null).isEmpty())
    }

    @Test
    fun totaLEstonaEsUnaVegada() {
        val fora = listOf(t0 + 100 * 60 to t0 + 120 * 60)
        val f = Sessio.calcula(0, 120.0, dins, fora)!!
        val v = f.vegada
        assertEquals(t0 + 30 * 60, v.ts)
        assertEquals(170, v.minuts)
        // Va a cicles: funciona 2 de cada 3 minuts, entre 300 i 420 W
        assertTrue(f.aCicles)
        assertEquals(0.67, f.partEnces, 0.03)
        assertTrue(f.wMin in 290.0..340.0)
        assertTrue(f.wMax in 380.0..430.0)
        // El pic: el màxim instantani del primer minut (700 W, i un 2 % més dins del minut) per sobre de la base
        assertEquals(716.0, f.pic, 2.0)
        // L'energia, sense la placa: uns 0,6 kWh (149 minuts propis, 2/3 a uns 360 W)
        val esperat = dins.filter { m -> fora.none { m.ts in it.first..it.second } }.sumOf { it.w - 120 } / 60_000
        assertEquals(esperat, v.kwh, 1e-9)
        // Amb la placa dins, molt més
        assertTrue(Sessio.calcula(0, 120.0, dins, emptyList())!!.vegada.kwh > v.kwh + 0.8)
    }

    @Test
    fun senseCapCanviNoHiHaFirma() {
        val pla = (0 until 30).map { m(it, 120.0) }
        assertNull(Sessio.calcula(0, 120.0, pla, emptyList()))
    }

    @Test
    fun lEtiquetaNomesEsDeLaVegadaSencera() {
        val e = Etiqueta(0, t0 + 30 * 60, t0 + 199 * 60 + 59, 7, sessio = listOf(t0 + 100 * 60 to t0 + 120 * 60))
        val sencera = Vegada(0, 0, t0 + 30 * 60, t0 + 199 * 60, 170, 360.0, 0.6, 0.05, 1.9)
        val placa = Vegada(0, 0, t0 + 100 * 60, t0 + 120 * 60, 21, 2500.0, 0.88, 0.01, 1.0)
        assertTrue(encavalca(sencera, e) > 0)
        assertEquals(0L, encavalca(placa, e))
        val aire = Aparell(7, "Aire", Categoria.AIRE, Font.TIPIC, 300.0)
        assertEquals(7L to Com.ENSENYAT, assigna(sencera, listOf(aire), emptyMap(), listOf(e)))
        // La placa de sobre no és de l'aire perquè l'estona la cobreixi (no és una estona passada)
        assertNull(assigna(placa, listOf(aire), emptyMap(), listOf(e)).first)
    }

    @Test
    fun lEstonaEsDesaComAText() {
        val fora = listOf(10L to 20L, 300L to 360L)
        assertEquals(fora, Sessio.llegeix(Sessio.text(fora)))
        assertEquals(emptyList<Pair<Long, Long>>(), Sessio.llegeix(Sessio.text(emptyList())))
        assertNull(Sessio.llegeix(null))
    }

    @Test
    fun lIntervalDUnaEstonaValPerAUnAparellQueEngegaLUsuari() {
        val mostra = Mostra(1, 7, t0, 360.0, null, 300.0, 420.0, "sessio")
        val r = Referencia.de(7, listOf(mostra))!!
        assertEquals(300.0, r.min, 0.0)
        assertEquals(420.0, r.max, 0.0)
        // Una mostra d'un salt (sense interval), no
        assertNull(Referencia.de(7, listOf(mostra.copy(font = "directe")))!!.wMin)
    }

    @Test
    fun unaEstonaQueVariaNoSEmportaLesCosesCurtes() {
        // Només se'n sap una estona que variava (sense durada): una cosa de 6 minuts a 350 W no és seva; una de 20, sí
        val mostra = Mostra(1, 7, t0, 360.0, null, 300.0, 420.0, "sessio")
        val r = Referencia.de(7, listOf(mostra))!!
        val aire = Aparell(7, "Aire", Categoria.AIRE, Font.TIPIC, 300.0)
        val curta = Vegada(0, 0, t0, t0 + 5 * 60, 6, 350.0, 0.035, 0.01, 1.0)
        assertNull(assigna(curta, listOf(aire), mapOf(7L to r), emptyList()).first)
        assertEquals(7L, assigna(curta.copy(minuts = 20, fi = t0 + 19 * 60), listOf(aire), mapOf(7L to r), emptyList()).first)
    }
}
