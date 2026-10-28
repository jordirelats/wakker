package cat.jordi.wakker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlegeixNumTest {
    @Test
    fun comaIPuntDecimals() {
        assertEquals(1.5, llegeixNum("1,5")!!, 1e-9)
        assertEquals(358.5, llegeixNum("358.5")!!, 1e-9)
        assertEquals(1150.0, llegeixNum("1150")!!, 1e-9)
    }

    @Test
    fun separadorDeMilers() {
        // El que posa num() en català i en neerlandès
        assertEquals(1150.0, llegeixNum("1.150")!!, 1e-9)
        assertEquals(2500000.0, llegeixNum("2.500.000")!!, 1e-9)
        assertEquals(1150.5, llegeixNum("1.150,5")!!, 1e-9)
        assertEquals(1150.5, llegeixNum("1,150.5")!!, 1e-9)
        // Un zero davant no és mai de milers
        assertEquals(0.198, llegeixNum("0.198")!!, 1e-9)
    }

    @Test
    fun preusSenseMilers() {
        assertEquals(1.306, llegeixNum("1.306", milers = false)!!, 1e-9)
        assertEquals(0.19815, llegeixNum("0,19815", milers = false)!!, 1e-9)
    }

    @Test
    fun textQueNoEsUnNumero() {
        assertNull(llegeixNum(""))
        assertNull(llegeixNum("abc"))
    }
}
