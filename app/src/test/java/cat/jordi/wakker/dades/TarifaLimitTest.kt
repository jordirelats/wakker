package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TarifaLimitTest {
    @Test
    fun espanyaSenseConfirmarNoAvisa() {
        // Els 4,6 kW per defecte serveixen per al cost, però no per avisar
        val t = Tarifa(pais = Pais.ES)
        assertNull(t.limitKw)
        assertEquals(4.6, t.potenciaKw, 1e-9)
    }

    @Test
    fun espanyaAmbLaPotenciaPosada() {
        val t = Tarifa(pais = Pais.ES).ambLimit(5.75)
        assertEquals(5.75, t.limitKw!!, 1e-9)
        // Si s'esborra, deixa d'avisar però el cost continua amb l'última potència
        val sense = t.ambLimit(null)
        assertNull(sense.limitKw)
        assertEquals(5.75, sense.potenciaKw, 1e-9)
    }

    @Test
    fun paisosBaixosAmbLAansluiting() {
        assertNull(Tarifa(pais = Pais.NL).limitKw)
        assertEquals(17.0, Tarifa(pais = Pais.NL).ambLimit(17.0).limitKw!!, 1e-9)
    }
}
