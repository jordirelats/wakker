package cat.jordi.wakker.endolls

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * El protocol KLAP v2 dels Tapo (maqueta 19), contra els vectors fets amb python-kasa 0.12 (recerca/endolls/vectors_klap.py):
 * el compte «prova@example.com» / «contrasenya», l'atzar local 00..0f i el remot 64..73.
 */
class KlapTest {
    private val local = ByteArray(16) { it.toByte() }
    private val remot = ByteArray(16) { (100 + it).toByte() }
    private val resum = Klap.resum("prova@example.com", "contrasenya")

    @Test
    fun elResumDelCompteComPythonKasa() {
        assertEquals("df5abf4b959d64463309f6c3065149810a389b803fc5b9c824678359268bb558", Klap.resumHex("prova@example.com", "contrasenya"))
        assertArrayEquals(resum, Klap.deHex(Klap.resumHex("prova@example.com", "contrasenya")))
    }

    @Test
    fun elsDosHandshakes() {
        assertEquals("cb1aabaa7d43cc903b4f66afe5f7e777f66ddfebc84409a56d27e93531c79be8", hex(Klap.hashHandshake1(local, remot, resum)))
        assertEquals("035bcecf7612acef7d34c83dfec018cdcddc8f3933a24c2b1dd9f33df9042646", hex(Klap.hashHandshake2(local, remot, resum)))
    }

    @Test
    fun laSessioXifraComPythonKasa() {
        val s = SessioKlap("x", null, local, remot, resum, Long.MAX_VALUE, 1000)
        assertEquals(-489697502, s.seq)
        val (cos, seq) = s.xifra("{\"method\":\"get_energy_usage\"}")
        assertEquals(-489697501, seq)
        assertEquals(
            "a8f153bf858a76d21723334563a36fec580b95b095e64906ce1b22aba63d8a2d51077bc5aee7e1061d7797ecdbc4fca4c83cb8daebeb3ad3522a76f5e6a385ee",
            hex(cos),
        )
        // La resposta ve amb la mateixa seqüència
        assertEquals("{\"method\":\"get_energy_usage\"}", s.desxifra(cos, seq))
        assertFalse(s.caducada)
    }

    @Test
    fun lHoraLocalComSiFosUtc() {
        val zona = ZoneId.of("Europe/Madrid")
        // Les 17:00 del 25/10/2026 a Madrid (UTC+2 encara? no: el 25/10 és el dia del canvi, a les 17 ja és UTC+1)
        val ts = java.time.ZonedDateTime.of(2026, 10, 25, 17, 0, 0, 0, zona).toEpochSecond()
        val local = Klap.aLocal(ts, zona)
        assertEquals(java.time.LocalDateTime.of(2026, 10, 25, 17, 0).toEpochSecond(java.time.ZoneOffset.UTC), local)
        assertEquals(ts, Klap.deLocal(local, zona))
    }

    @Test
    fun laRespostaDelDescobriment() {
        val json = """{"result":{"device_id":"8006","owner":"A1","device_type":"SMART.TAPOPLUG","device_model":"P115(EU)","ip":"192.168.1.23",
            "mac":"AA-BB-CC-DD-EE-01","is_support_iot_cloud":true,"obd_src":"tplink","factory_default":false,
            "mgt_encrypt_schm":{"is_support_https":false,"encrypt_type":"KLAP","http_port":80,"lv":2}},"error_code":0}"""
        val t = DescobrimentTapo.llegeix(json, "192.168.1.23")!!
        assertEquals("192.168.1.23", t.host)
        assertEquals("P115", t.model)
        assertEquals("aabbccddee01", t.mac)
        assertTrue(t.klap)
        // Un altre port (l'endoll fals a l'emulador), i una bombeta, que no és cap endoll
        assertEquals("10.0.2.2:8081", DescobrimentTapo.llegeix(json.replace("\"http_port\":80", "\"http_port\":8081").replace("192.168.1.23", "10.0.2.2"), "10.0.2.2")!!.host)
        assertNull(DescobrimentTapo.llegeix(json.replace("SMART.TAPOPLUG", "SMART.TAPOBULB"), "192.168.1.23"))
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
