package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trobar l'aparell sense saber-ne el model exacte (maqueta 14). */
class ModelsTest {
    @Test
    fun lesVariantsDelModel() {
        // Un rentaplats, tal com surt a la placa: amb el «/02» l'EPREL no el troba; sense, sí
        val v = variantsModel("4SX7251KB/02")
        assertEquals(listOf("4SX7251KB/02", "4SX7251KB", "4SX7251K", "4SX7251", "4SX725", "4SX72"), v.models)
        assertEquals(2, v.exactes)
        // El que hi ha escrit al davant a la placa no compta
        assertEquals("4SX7251KB", variantsModel("E-Nr. 4SX7251KB/02").models[2])
        assertEquals(listOf("QRT840LNPE", "QRT840LNP", "QRT840LN", "QRT840L", "QRT840"), variantsModel(" QRT840LNPE ").models)
        assertEquals(1, variantsModel("QRT840LNPE").exactes)
        // Amb espais, una sola variant exacta (l'EPREL no els té en compte), i escurçat sense espais
        val espais = variantsModel("4SX 7251 KB")
        assertEquals(1, espais.exactes)
        assertEquals("4SX7251K", espais.models[1])
        // Un model que comença com «Mod» no perd el principi
        assertEquals("MODENA123", variantsModel("MODENA123").models.first())
        assertEquals(1, variantsModel("MODENA123").exactes)
        // Un model curt no s'escurça per sota de 5 lletres
        assertEquals(listOf("ABC12"), variantsModel("ABC12").models)
        assertTrue(variantsModel("   ").models.isEmpty())
    }

    @Test
    fun elModelDeLaPlaca() {
        val placa = "MARCA\nE-Nr. 4SX7251KB/02   FD 9812\n220-240 V ~ 50 Hz   2400 W\nMade in Spain"
        assertEquals(listOf("4SX7251KB/02"), Placa.models(placa))
        // Ni la tensió, ni la potència, ni la freqüència; i el que porta «Mod.» al davant, primer
        val altra = "SERIE 1850-2200W 220-240V 50/60HZ AB12CD34\nMod. KT-2017"
        assertEquals("KT-2017", Placa.models(altra).first())
        assertTrue("1850-2200W" !in Placa.models(altra))
        assertTrue("220-240V" !in Placa.models(altra))
        // Un aire condicionat: «KPLM35A» acaba amb una A, però no són amperes
        assertEquals(listOf("KPLM35A2V1B"), Placa.models("MARCA KPLM35A2V1B 1~ 220-240V"))
    }
}
