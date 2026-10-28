package cat.jordi.wakker.shelly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescobrimentTest {
    private fun ip(vararg b: Int) = b.map { it.toByte() }.toByteArray()

    @Test
    fun unaXarxaNormal() {
        val a = adrecesAMirar(ip(192, 168, 50, 60), 24)
        assertEquals(253, a.size)
        assertFalse("192.168.50.60" in a)
        assertFalse("192.168.50.0" in a)
        assertFalse("192.168.50.255" in a)
        // Primer les del costat
        assertEquals(setOf("192.168.50.59", "192.168.50.61"), a.take(2).toSet())
    }

    @Test
    fun unaXarxaMesGran() {
        // Una /23: el Shelly pot ser a l'altra meitat
        val a = adrecesAMirar(ip(192, 168, 0, 10), 23)
        assertEquals(509, a.size)
        assertTrue("192.168.1.198" in a)
    }

    @Test
    fun unaXarxaMoltGranEsLimitaAUna22() {
        val a = adrecesAMirar(ip(10, 0, 5, 7), 8)
        assertEquals(1021, a.size)
        assertTrue(a.all { it.startsWith("10.0.") })
        assertTrue("10.0.4.1" in a && "10.0.7.254" in a)
        assertFalse("10.0.8.1" in a)
    }
}
