package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Test

class VegadesTest {
    private val t0 = 1_792_800_000L

    private fun aparell(id: Long, c: Categoria) = Aparell(id, c.name, c, Font.TIPIC, c.kwhAnyTipic)
    private val rentaplats = aparell(1, Categoria.RENTAPLATS)
    private val bullidor = aparell(2, Categoria.BULLIDOR)
    private val estufa = aparell(3, Categoria.ESTUFA)
    private val placa = aparell(4, Categoria.PLACA)

    /** Una vegada de [w] W i [minuts] minuts; [variacio] 0,9 polsa, com la placa. */
    private fun vegada(w: Double, minuts: Int, variacio: Double = 0.0, canal: Int = 0) =
        Vegada(0, canal, t0, t0 + (minuts - 1) * 60L, minuts, w, w * minuts / 60_000, variacio, 1.0)

    private val refs = mapOf(
        1L to Referencia(1, 2175.0, 4, 10.0, variacio = 0.0, arrencada = 1.0),
        2L to Referencia(2, 2100.0, 3, 3.0, variacio = 0.0, arrencada = 1.0),
        3L to Referencia(3, 964.0, 12, 3.0, 771.0, 1211.0, variacio = 0.0, arrencada = 1.0),
        4L to Referencia(4, 1500.0, 3, null, variacio = 0.9, arrencada = 0.6),
    )
    private val tots = listOf(rentaplats, bullidor, estufa, placa)

    @Test
    fun elQueSEnsenyaEsSeu() {
        // Un rentaplats ensenyat amb una estona passada: és seu encara que s'assembli al bullidor
        val e = listOf(Etiqueta(0, t0 - 60, t0 + 9 * 60, 1))
        assertEquals(1L to Com.ENSENYAT, assigna(vegada(2100.0, 3), tots, refs, e))
        // A la línia (un altre canal), aquesta etiqueta no hi compta
        assertEquals(null to null, assigna(vegada(2100.0, 3, canal = 1), emptyList(), emptyMap(), e))
        // Un instant (una pregunta respost) dins de la vegada també
        assertEquals(3L to Com.ENSENYAT, assigna(vegada(500.0, 4), tots, refs, listOf(Etiqueta(0, t0 + 120, t0 + 120, 3))))
    }

    @Test
    fun unaEtiquetaNoSEmportaElQueNoEsSeu() {
        // La càrrega del cotxe i la placa que s'hi encén a sobre 37 minuts després són una sola vegada: la resposta «Placa» (un instant
        // lluny de l'inici) no se l'emporta; la del cotxe, sí
        val llarga = Vegada(0, 0, t0, t0 + 300 * 60L, 301, 2800.0, 14.0, 0.0, 1.0)
        assertEquals(null to null, assigna(llarga, tots, refs, listOf(Etiqueta(0, t0 + 37 * 60L, t0 + 37 * 60L, 4))))
        assertEquals(9L to Com.ENSENYAT, assigna(llarga, tots, refs, listOf(Etiqueta(0, t0, t0, 9))))
        // Una estona que només en cobreix un trosset (una rentadora ensenyada 20 minuts dins d'una vegada de 5 hores), tampoc
        assertEquals(null to null, assigna(llarga, tots, refs, listOf(Etiqueta(0, t0 + 3600, t0 + 3600 + 20 * 60L, 1))))
        // El que ara està sempre encès no es queda res, encara que se n'hagués dit
        assertEquals(null to null, assigna(vegada(2100.0, 3), emptyList(), refs, listOf(Etiqueta(0, t0, t0, 2)), exclosos = setOf(2L)))
    }

    @Test
    fun lIndexTrobaLesEtiquetesDUnaVegada() {
        val e = listOf(
            Etiqueta(0, t0 - 4 * 3600, t0 - 3 * 3600, 1), Etiqueta(0, t0 - 60, t0 - 60, 2),
            Etiqueta(0, t0 + 2 * 60, t0 + 2 * 60, 3), Etiqueta(1, t0, t0, 4), Etiqueta(0, t0 + 3600, t0 + 3600, 5),
        )
        val trobades = IndexEtiquetes(e).de(vegada(2000.0, 10)).map { it.aparellId }.toSet()
        // La de fa 3 hores, la de la línia i la d'una hora després, no; les dues d'a prop, sí
        assertEquals(setOf(2L, 3L), trobades)
    }

    @Test
    fun laMateixaPotenciaLaDecideixLaDurada() {
        assertEquals(1L to Com.RECONEGUT, assigna(vegada(2150.0, 11), tots, refs, emptyList()))
        assertEquals(2L to Com.RECONEGUT, assigna(vegada(2150.0, 3), tots, refs, emptyList()))
    }

    @Test
    fun unIntervalIUnaFormaQueNoQuadra() {
        // L'estufa, dins del seu interval (amb el 15 % de marge)
        assertEquals(3L to Com.RECONEGUT, assigna(vegada(1300.0, 2), tots, refs, emptyList()))
        // 1,5 kW que polsen: la placa, no l'estufa ni el bullidor
        assertEquals(4L to Com.RECONEGUT, assigna(vegada(1500.0, 20, variacio = 0.9), tots, refs, emptyList()))
        // 1,5 kW constants durant 20 minuts: ningú
        assertEquals(null to null, assigna(vegada(1500.0, 20), tots, refs, emptyList()))
    }

    @Test
    fun sNoEraEllNoTornaASerSeu() {
        val no = listOf(Etiqueta(0, t0, t0 + 10 * 60, 1, no = true))
        assertEquals(null to null, assigna(vegada(2150.0, 11), tots, refs, no))
    }

    @Test
    fun senseReferenciaNoEsReconeix() {
        // Un aparell amb només la potència típica (sense mostres) no es queda res
        assertEquals(null to null, assigna(vegada(2000.0, 10), listOf(aparell(9, Categoria.RENTADORA)), emptyMap(), emptyList()))
    }

    @Test
    fun unaEstonaLlargaNoSEmportaElQueTeASobre() {
        // L'estona de la càrrega del cotxe (6 hores) cobreix sencera la placa de 20 minuts que s'hi va encendre a sobre: si la
        // placa s'hi assembla, és de la placa
        val e = listOf(Etiqueta(0, t0 - 30 * 60, t0 + 330 * 60, 9))
        assertEquals(4L to Com.RECONEGUT, assigna(vegada(1600.0, 20, variacio = 0.9), tots, refs, e))
        // La vegada del cotxe és del cotxe
        assertEquals(9L to Com.ENSENYAT, assigna(vegada(2400.0, 360), tots, refs, e))
        // Una cosa a sobre que no s'assembla a cap aparell (l'escalfament dins del cicle de la rentadora) és de l'estona
        assertEquals(9L to Com.ENSENYAT, assigna(vegada(5000.0, 20), tots, refs, e))
        // Però no si l'usuari ha dit que no era ell
        val no = e + Etiqueta(0, t0, t0 + 19 * 60 + 59, 9, no = true)
        assertEquals(null to null, assigna(vegada(5000.0, 20), tots, refs, no))
    }

    @Test
    fun elQueHiHaEncesAra() {
        val ara = t0 + 30 * 60
        // A l'últim minut del Shelly hi havia el rentaplats (des de fa 20 minuts) i una vegada sense nom
        val rentaplats = Vegada(1, 0, t0 + 10 * 60, ara - 120, 19, 2175.0, 0.69, 0.0, 1.0, 1, Com.RECONEGUT)
        val desconeguda = Vegada(2, 0, t0 + 20 * 60, ara - 120, 9, 600.0, 0.09, 0.0, 1.0)
        val tot = encesDelCanal(ara, 0, 2175.0 + 600.0 + 80.0, listOf(rentaplats, desconeguda), tots, refs, neveraW = 52.0)
        assertEquals(listOf(TipusEnces.APARELL, TipusEnces.SENSE_IDENTIFICAR, TipusEnces.NEVERA, TipusEnces.PETITS), tot.map { it.tipus })
        assertEquals(1L, tot[0].aparellId)
        assertEquals(t0 + 10 * 60, tot[0].desDe)
        // El que porta: el de la vegada i, des de l'últim minut, a la seva potència
        assertEquals(0.69 + 2175.0 * 60 / 3_600_000, tot[0].kwh!!, 0.001)
        assertEquals(28.0, tot[3].w, 0.1)
        // El rentaplats s'ha apagat després de l'últim minut: ja no hi cap, i no surt
        val apagat = encesDelCanal(ara, 0, 600.0, listOf(rentaplats, desconeguda), tots, refs)
        assertEquals(listOf(TipusEnces.SENSE_IDENTIFICAR), apagat.map { it.tipus })
        // Una vegada sense nom de 3 minuts que fa la potència del rentaplats (sense el bullidor, que en fa una de semblant):
        // sembla ell; de 60 minuts, ja no (dura massa, i el rànquing tampoc no l'hi dona)
        val sensBullidor = tots.filter { it.categoria != Categoria.BULLIDOR }
        val jove = Vegada(3, 0, ara - 240, ara - 120, 3, 2175.0, 0.1, 0.0, 1.0)
        assertEquals(1L, encesDelCanal(ara, 0, 2175.0, listOf(jove), sensBullidor, refs).single().aparellId)
        val llarga = jove.copy(ts = ara - 3720, minuts = 60)
        assertEquals(TipusEnces.SENSE_IDENTIFICAR, encesDelCanal(ara, 0, 2175.0, listOf(llarga), sensBullidor, refs).single().tipus)
        // En un moment punta (ja passat) no s'endevina res per la potència: el que no té nom al rànquing, tampoc en té aquí
        assertEquals(TipusEnces.SENSE_IDENTIFICAR, encesDelCanal(ara, 0, 2175.0, listOf(jove), sensBullidor, refs, perPotencia = false).single().tipus)
        assertEquals(TipusEnces.NOU, encesDelCanal(ara, 0, 960.0, emptyList(), tots, refs, perPotencia = false).single().tipus)
        // I el que passa per sobre de les vegades en el minut del pic és de la que polsa (la placa), no una cosa nova
        val cotxe = Vegada(5, 0, ara - 3600, ara - 60, 60, 2800.0, 2.8, 0.0, 1.0, 9, Com.ENSENYAT)
        val cuina = Vegada(6, 0, ara - 360, ara - 60, 6, 1700.0, 0.17, 0.9, 1.0)
        val pic = encesDelCanal(ara, 0, 2800.0 + 1700.0 + 1300.0, listOf(cotxe, cuina), tots, refs, perPotencia = false)
        assertEquals(2800.0, pic.single { it.aparellId == 9L }.w, 0.1)
        assertEquals(3000.0, pic.single { it.tipus == TipusEnces.SENSE_IDENTIFICAR }.w, 0.1)
        assertEquals(2, pic.size)
        // Amb el bullidor, per la potència sola no se sap quin dels dos és
        assertEquals(TipusEnces.SENSE_IDENTIFICAR, encesDelCanal(ara, 0, 2175.0, listOf(jove), tots, refs).single().tipus)
        // S'acaba d'encendre una cosa de 960 W: per la potència només pot ser l'estufa
        val nou = encesDelCanal(ara, 0, 960.0, emptyList(), tots, refs)
        assertEquals(3L, nou.single().aparellId)
        assertEquals(null, nou.single().com)
        // I una de 5 kW no s'assembla a res
        assertEquals(TipusEnces.NOU, encesDelCanal(ara, 0, 5000.0, emptyList(), tots, refs).single().tipus)
    }
}
