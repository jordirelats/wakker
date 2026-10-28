package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Els endolls amb mesura (maqueta 19): què diuen d'una vegada, i com manen en repartir-la. */
class EndollsTest {
    private val t0 = 1_700_000_000L / 60 * 60

    /** Els minuts d'un endoll de [desDe] a [finsA] (inclòs), a [w] W. */
    private fun minuts(desDe: Long, finsA: Long, w: Double) = (desDe..finsA step 60).map { MinutEndoll(it, w / 60) }

    private val apagatAbans = minuts(t0 - 600, t0 - 60, 0.8)

    @Test
    fun lEndollEncesEsSeu() {
        // Una vegada de 10 minuts a 2.200 W; l'endoll, apagat abans, a 2.150 W tota l'estona
        assertEquals(Veredicte.SEU, veredicteEndoll(2200.0, t0, t0 + 9 * 60, minuts(t0, t0 + 9 * 60, 2150.0), apagatAbans))
        // L'endoll a 1.200 W: l'aparell hi era, amb una altra cosa a sobre
        assertEquals(Veredicte.SEU, veredicteEndoll(2200.0, t0, t0 + 9 * 60, minuts(t0, t0 + 9 * 60, 1200.0), apagatAbans))
        // Sense els minuts d'abans no se sap
        assertEquals(Veredicte.NO_SE_SAP, veredicteEndoll(2200.0, t0, t0 + 9 * 60, minuts(t0, t0 + 9 * 60, 2150.0)))
    }

    @Test
    fun lEndollQueJaAnavaNoSEmportaElQueSEncenASobre() {
        // Una estufa amb endoll a 2.000 W des d'abans; el forn (2.500 W) s'encén a sobre: la vegada no és de l'estufa
        val estufa = minuts(t0 - 600, t0 + 9 * 60, 2000.0)
        assertEquals(Veredicte.NO_SE_SAP, veredicteEndoll(2500.0, t0, t0 + 9 * 60, estufa, estufa))
    }

    @Test
    fun lEndollApagatNoEsSeu() {
        assertEquals(Veredicte.NO_SEU, veredicteEndoll(2200.0, t0, t0 + 9 * 60, minuts(t0, t0 + 9 * 60, 0.8)))
        // 300 W de màxim no arriben al 15 % de 2.200: apagat (una bomba, no l'escalfament)
        assertEquals(Veredicte.NO_SEU, veredicteEndoll(2200.0, t0, t0 + 9 * 60, minuts(t0, t0 + 9 * 60, 300.0)))
    }

    @Test
    fun senseProuMinutsNoEsSap() {
        // Només 3 dels 10 minuts
        assertEquals(Veredicte.NO_SE_SAP, veredicteEndoll(2200.0, t0, t0 + 9 * 60, minuts(t0, t0 + 2 * 60, 2150.0)))
        assertEquals(Veredicte.NO_SE_SAP, veredicteEndoll(2200.0, t0, t0 + 9 * 60, emptyList()))
        // Entremig: l'endoll fa 700 W d'una vegada de 2.200: hi havia una altra cosa, no se sap
        assertEquals(Veredicte.NO_SE_SAP, veredicteEndoll(2200.0, t0, t0 + 9 * 60, minuts(t0, t0 + 9 * 60, 700.0)))
    }

    @Test
    fun ambLesHoresDeLEndoll() {
        val hora = { ts: Long -> ts / 3600 * 3600 }
        val h0 = hora(t0)
        // Una vegada de 10 minuts a 2.200 W (0,37 kWh) dins d'una hora en què l'endoll ha gastat 380 Wh: seva
        assertEquals(Veredicte.SEU, veredicteHores(0.37, h0 + 600, h0 + 600 + 9 * 60, listOf(h0 to 380.0), iniciHora = hora))
        // L'endoll hi ha gastat 5 Wh: apagat
        assertEquals(Veredicte.NO_SEU, veredicteHores(0.37, h0 + 600, h0 + 600 + 9 * 60, listOf(h0 to 5.0), iniciHora = hora))
        // El doble (dues vegades iguals a la mateixa hora), o sense l'hora: no se sap
        assertEquals(Veredicte.NO_SE_SAP, veredicteHores(0.37, h0 + 600, h0 + 600 + 9 * 60, listOf(h0 to 740.0), iniciHora = hora))
        assertEquals(Veredicte.NO_SE_SAP, veredicteHores(0.37, h0 + 600, h0 + 600 + 9 * 60, emptyList(), iniciHora = hora))
        // El forn (0,5 kWh) a la mateixa hora que el rentaplats (0,37; l'endoll 400 Wh): la del forn no és del rentaplats
        assertEquals(Veredicte.NO_SE_SAP, veredicteHores(0.5, h0 + 1800, h0 + 1800 + 14 * 60, listOf(h0 to 400.0), 370.0, iniciHora = hora))
        assertEquals(Veredicte.SEU, veredicteHores(0.37, h0 + 600, h0 + 600 + 9 * 60, listOf(h0 to 400.0), 100.0, iniciHora = hora))
        // Una vegada que passa d'una hora a l'altra necessita les dues hores
        assertEquals(Veredicte.NO_SE_SAP, veredicteHores(0.37, h0 + 3300, h0 + 3300 + 9 * 60, listOf(h0 to 200.0), iniciHora = hora))
        assertEquals(Veredicte.SEU, veredicteHores(0.37, h0 + 3300, h0 + 3300 + 9 * 60, listOf(h0 to 200.0, h0 + 3600 to 180.0), iniciHora = hora))
    }

    private val rentaplats = Aparell(1, "Rentaplats", Categoria.RENTAPLATS, Font.TIPIC, 200.0)
    private val rentadora = Aparell(2, "Rentadora", Categoria.RENTADORA, Font.TIPIC, 150.0)
    private val refs = mapOf(
        1L to Referencia(1, 2200.0, 3, 10.0),
        2L to Referencia(2, 2150.0, 3, 10.0),
    )
    private val vegada = Vegada(0, 0, t0, t0 + 9 * 60, 10, 2180.0, 0.36, 0.01, 1.0)

    @Test
    fun lEndollManaSobreLaFirma() {
        // Dues firmes iguals: sense l'endoll, de ningú; amb l'endoll de la rentadora encès, de la rentadora
        assertNull(assigna(vegada, listOf(rentaplats, rentadora), refs, emptyList()).first)
        assertEquals(2L to Com.ENDOLL, assigna(vegada, listOf(rentaplats, rentadora), refs, emptyList(), endoll = 2))
        // L'endoll del rentaplats apagat: queda la rentadora, reconeguda
        assertEquals(2L to Com.RECONEGUT, assigna(vegada, listOf(rentaplats, rentadora), refs, emptyList(), noEndoll = setOf(1)))
    }

    @Test
    fun lEndollManaSobreElQueHaDitLUsuari() {
        // L'usuari havia dit que era el rentaplats, però el seu endoll era apagat: no pot ser
        val etiqueta = Etiqueta(0, t0, t0, 1)
        assertEquals(1L to Com.ENSENYAT, assigna(vegada, listOf(rentaplats, rentadora), refs, listOf(etiqueta)))
        assertEquals(2L to Com.RECONEGUT, assigna(vegada, listOf(rentaplats, rentadora), refs, listOf(etiqueta), noEndoll = setOf(1)))
        // «No era ell» de l'usuari sí que mana sobre l'endoll
        val no = Etiqueta(0, t0, t0 + 9 * 60 + 59, 2, no = true)
        assertEquals(1L to Com.RECONEGUT, assigna(vegada, listOf(rentaplats, rentadora), refs, listOf(no), endoll = 2))
    }

    @Test
    fun lUsDUnaTele() {
        // 24 hores: 20 h a 0,8 W (espera) i 4 h a 85 W
        val m = (0 until 24 * 60).map { i -> MinutEndoll(t0 + i * 60L, (if (i % 1440 < 240) 85.0 else 0.8) / 60) }
        val (hores, ences, espera) = usEndoll(m)!!
        assertEquals(4.0, hores, 0.05)
        assertEquals(85.0, ences, 0.5)
        assertEquals(0.8, espera, 0.05)
        assertNull(usEndoll(m.take(100)))
    }
}
