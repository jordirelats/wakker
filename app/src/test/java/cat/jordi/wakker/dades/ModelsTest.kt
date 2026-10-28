package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trobar l'aparell sense saber-ne el model exacte (maqueta 14). */
class ModelsTest {
    @Test
    fun lesVariantsDelModel() {
        // El rentaplats de Guíxols, tal com surt a la placa: amb el «/01» l'EPREL no el troba; sense, sí
        val v = variantsModel("3VS6364IA/01")
        assertEquals(listOf("3VS6364IA/01", "3VS6364IA", "3VS6364I", "3VS6364", "3VS636", "3VS63"), v.models)
        assertEquals(2, v.exactes)
        // El que hi ha escrit al davant a la placa no compta
        assertEquals("3VS6364IA", variantsModel("E-Nr. 3VS6364IA/01").models[2])
        assertEquals(listOf("GMJ960MBJE", "GMJ960MBJ", "GMJ960MB", "GMJ960M", "GMJ960"), variantsModel(" GMJ960MBJE ").models)
        assertEquals(1, variantsModel("GMJ960MBJE").exactes)
        // Amb espais, una sola variant exacta (l'EPREL no els té en compte), i escurçat sense espais
        val espais = variantsModel("3VS 6364 IA")
        assertEquals(1, espais.exactes)
        assertEquals("3VS6364I", espais.models[1])
        // Un model que comença com «Mod» no perd el principi
        assertEquals("MODENA123", variantsModel("MODENA123").models.first())
        assertEquals(1, variantsModel("MODENA123").exactes)
        // Un model curt no s'escurça per sota de 5 lletres
        assertEquals(listOf("ABC12"), variantsModel("ABC12").models)
        assertTrue(variantsModel("   ").models.isEmpty())
    }

    @Test
    fun elModelDeLaPlaca() {
        val placa = "BALAY\nE-Nr. 3VS6364IA/01   FD 9812\n220-240 V ~ 50 Hz   2400 W\nMade in Spain"
        assertEquals(listOf("3VS6364IA/01"), Placa.models(placa))
        // Ni la tensió, ni la potència, ni la freqüència; i el que porta «Mod.» al davant, primer
        val altra = "SERIE 1850-2200W 220-240V 50/60HZ AB12CD34\nMod. KT-2017"
        assertEquals("KT-2017", Placa.models(altra).first())
        assertTrue("1850-2200W" !in Placa.models(altra))
        assertTrue("220-240V" !in Placa.models(altra))
        // El Daikin: «FTXM25A» acaba amb una A, però no són amperes
        assertEquals(listOf("FTXM25A2V1B"), Placa.models("DAIKIN FTXM25A2V1B 1~ 220-240V"))
    }
}
