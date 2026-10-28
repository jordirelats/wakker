package cat.jordi.wakker.endolls

import cat.jordi.wakker.dades.TipusEndoll
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Un endoll amb mesura TP-Link Tapo (P110, P115) a la xarxa de casa (maqueta 19). Parla el protocol KLAP v2 dels Tapo,
 * que no és públic: el que se'n sap ve de python-kasa (klaptransport.py), que és el que fa servir Home Assistant.
 *
 * - L'endoll demana el compte de Tapo (el correu i la contrasenya), però només en fa servir un resum:
 *   `sha256(sha1(correu) + sha1(contrasenya))`. L'app desa aquest resum i mai la contrasenya ([Klap.resum]).
 * - El protocol: `handshake1` (16 bytes a l'atzar nostres → 16 de seus i el sha256 de tots dos amb el resum, que
 *   comprovem), `handshake2` (el sha256 al revés), i les peticions xifrades amb AES-128-CBC i signades, amb un número de
 *   seqüència que puja a cada petició. La sessió va amb la galeta `TP_SESSIONID` i caduca al cap d'un dia.
 * - Només funciona a casa: els Tapo no tenen cap núvol públic.
 */
class Tapo(val host: String, private val resum: ByteArray, private val tempsMs: Int = 2500, private val macEsperada: String = "") : LectorEndoll {
    private var sessio: SessioKlap? = null

    /** L'endoll ha dit que no sap un mètode (la potència de 5 minuts): per no tornar-l'hi a demanar. */
    @Volatile
    var senseMetode = false
        private set

    /** Qui és: el model, la MAC, el nom que té a l'app de Tapo i si el relé és tancat. */
    override fun info(): InfoEndoll = InfoEndoll.de(host, crida("get_device_info"))

    /** La lectura d'ara: la potència (W) i l'energia d'avui (Wh), amb l'hora local de l'endoll. */
    override fun lectura(): LecturaEndoll {
        val r = crida("get_energy_usage")
        // current_power és en mil·liwatts a get_energy_usage (python-kasa el divideix per 1.000)
        val mw = if (r.has("current_power") && !r.isNull("current_power")) r.optDouble("current_power") else null
        val w = mw?.div(1000) ?: crida("get_current_power").optDouble("current_power", 0.0)
        val avui = if (r.has("today_energy") && !r.isNull("today_energy")) r.optDouble("today_energy") else null
        return LecturaEndoll(w, avui, System.currentTimeMillis() / 1000, r.optString("local_time").takeIf { it.isNotBlank() })
    }

    /**
     * L'energia de cada hora (Wh) entre dos instants, com a molt 8 dies (el límit de l'endoll). Els timestamps són segons
     * UTC de debò (el crate tapo, que es prova amb endolls reals, els calcula així); amb [localComUtc], l'hora local de
     * l'endoll escrita com si fos UTC, que és com ho feien les versions d'abans. L'app comprova quina de les dues quadra
     * amb el consum d'avui de l'endoll, perquè no s'ha pogut provar amb un de debò.
     */
    override fun energiaHores(desDe: Long, finsA: Long, zona: ZoneId, localComUtc: Boolean): List<Pair<Long, Double>> =
        try {
            serie("get_energy_data", desDe, finsA, 60, zona, localComUtc)
        } catch (e: ErrorEndoll) {
            if (e.codi == -1002 || e.codi == -1001) emptyList() else throw e
        }

    /**
     * La potència mitjana (W) de cada 5 minuts, si el firmware ho sap fer (els més nous; si no, l'endoll respon amb un
     * error i es torna una llista buida). Com a molt 12 hores per petició. Amb això, amb l'app tancada, també se sap quan
     * l'aparell era encès.
     */
    override fun potencia5min(desDe: Long, finsA: Long, zona: ZoneId, localComUtc: Boolean): List<Pair<Long, Double>> =
        try {
            serie("get_power_data", desDe, finsA, 5, zona, localComUtc)
        } catch (e: ErrorEndoll) {
            if (e.codi == -1002 || e.codi == -1001) {
                senseMetode = true
                emptyList()
            } else throw e
        }

    private fun serie(metode: String, desDe: Long, finsA: Long, intervalMin: Int, zona: ZoneId, localComUtc: Boolean): List<Pair<Long, Double>> {
        val a = if (localComUtc) Klap.aLocal(desDe, zona) else desDe
        val b = if (localComUtc) Klap.aLocal(finsA, zona) else finsA
        val params = JSONObject().put("start_timestamp", a).put("end_timestamp", b).put("interval", intervalMin)
        val r = crida(metode, params)
        val dades = r.optJSONArray("data") ?: return emptyList()
        val inici = r.optLong("start_timestamp", a)
        val interval = r.optInt("interval", intervalMin) * 60L
        // Un interval sense dades ve com a null o -1 (el crate tapo tracta tots dos)
        return (0 until dades.length()).mapNotNull { i ->
            val v = if (dades.isNull(i)) -1.0 else dades.optDouble(i, -1.0)
            if (v < 0) null else (inici + i * interval).let { if (localComUtc) Klap.deLocal(it, zona) else it } to v
        }
    }

    /** Una petició, amb la sessió oberta (o una de nova si no n'hi ha o ha caducat), i un segon intent si l'endoll la tanca. */
    @Synchronized
    private fun crida(metode: String, params: JSONObject? = null): JSONObject {
        var intents = 0
        while (true) {
            val s = sessio?.takeIf { !it.caducada } ?: Klap.obre(host, resum, tempsMs).also { comprovaMac(it); sessio = it }
            try {
                val resposta = s.peticio(metode, params)
                val codi = resposta.optInt("error_code", 0)
                if (codi == 0) return resposta.optJSONObject("result") ?: throw IOException("L'endoll $host respon sense resultat a $metode")
                // La sessió s'ha acabat: una de nova, un sol cop
                if (codi == 9999 && intents++ == 0) { sessio = null; continue }
                throw ErrorEndoll(codi, "L'endoll $host respon $codi a $metode")
            } catch (e: SessioTancada) {
                if (intents++ == 0) { sessio = null; continue }
                throw IOException("L'endoll $host ha tancat la sessió", e)
            }
        }
    }

    /**
     * A cada sessió nova, que l'endoll sigui el que es va afegir (per la MAC): amb DHCP, dos endolls es poden intercanviar
     * l'adreça i les dades del rentaplats anirien a la tele. Com la comprovació del Shelly de casa (1.1.3).
     */
    private fun comprovaMac(s: SessioKlap) {
        if (macEsperada.isBlank()) return
        val r = s.peticio("get_device_info", null).optJSONObject("result") ?: return
        val mac = normalitzaMacEndoll(r.optString("mac"))
        if (mac.isNotBlank() && mac != macEsperada) throw IOException("A $host hi respon un altre endoll ($mac)")
    }
}

/** Un error de l'endoll amb el seu codi (-1501 i 1111: el correu o la contrasenya no són els seus; -1002: no sap el mètode). */
class ErrorEndoll(val codi: Int, missatge: String) : IOException(missatge) {
    val credencials get() = codi == -1501 || codi == 1111 || codi == 1003
}

/** L'endoll ha respost 403: la sessió ja no val. */
internal class SessioTancada : IOException()

/** El correu o la contrasenya del compte de Tapo no són els de l'endoll (ho diu el handshake1). */
class CredencialsDolentes : IOException("El resum del compte no és el de l'endoll")

/**
 * El que l'app necessita d'un endoll, sigui de la marca que sigui: qui és, què marca ara i, si en guarda, l'historial.
 * El Tapo ho fa tot; un Shelly ([ShellyEndoll]) no té historial i les hores se'n fan amb el comptador.
 */
interface LectorEndoll {
    fun info(): InfoEndoll
    fun lectura(): LecturaEndoll
    fun energiaHores(desDe: Long, finsA: Long, zona: ZoneId = ZoneId.systemDefault(), localComUtc: Boolean = false): List<Pair<Long, Double>> = emptyList()
    fun potencia5min(desDe: Long, finsA: Long, zona: ZoneId = ZoneId.systemDefault(), localComUtc: Boolean = false): List<Pair<Long, Double>> = emptyList()
}

data class InfoEndoll(
    val host: String, val model: String, val mac: String, val nom: String, val firmware: String, val relé: Boolean, val desfasMin: Int?,
    val tipus: TipusEndoll = TipusEndoll.TAPO,
) {
    companion object {
        fun de(host: String, r: JSONObject): InfoEndoll {
            val nick = r.optString("nickname")
            // El nom ve en base64; si no es pot llegir o porta caràcters de control, el text tal qual, i com a molt 40 lletres
            val nom = (runCatching { String(Base64.getDecoder().decode(nick), Charsets.UTF_8) }.getOrNull()
                ?.takeIf { s -> s.isNotBlank() && s.none { it.isISOControl() } } ?: nick).trim().take(40)
            return InfoEndoll(
                host, r.optString("model").ifBlank { "Tapo" }, normalitzaMacEndoll(r.optString("mac")), nom, r.optString("fw_ver").substringBefore(" "),
                r.optBoolean("device_on", true), if (r.has("time_diff")) r.optInt("time_diff") else null,
            )
        }
    }
}

/** La lectura d'un endoll: la potència (W), l'energia d'avui si la diu (Wh) i, als Shelly, el comptador acumulat (Wh). */
data class LecturaEndoll(val w: Double, val whAvui: Double?, val ts: Long, val horaLocal: String?, val comptadorWh: Double? = null)

/** Un endoll trobat a la wifi amb el descobriment: l'adreça, el model, la MAC i si parla KLAP (l'únic Tapo que l'app sap). */
data class EndollTrobat(val host: String, val model: String, val mac: String, val klap: Boolean, val port: Int, val tipus: TipusEndoll = TipusEndoll.TAPO)

/** La MAC d'un endoll en minúscules i sense separadors («AA-BB-CC-DD-EE-01» → «aabbccddee01»). */
fun normalitzaMacEndoll(s: String) = s.trim().lowercase().replace(":", "").replace("-", "")

object Klap {
    private val atzar = SecureRandom()

    private fun sha256(vararg parts: ByteArray) = MessageDigest.getInstance("SHA-256").run { parts.forEach(::update); digest() }
    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b)

    /** El resum del compte de Tapo que l'endoll accepta: l'únic que l'app en desa. */
    fun resum(correu: String, contrasenya: String): ByteArray = sha256(sha1(correu.toByteArray()), sha1(contrasenya.toByteArray()))

    fun resumHex(correu: String, contrasenya: String) = resum(correu, contrasenya).joinToString("") { "%02x".format(it) }

    /** Segons d'un rellotge que només avança (no el de paret). */
    fun monoton(): Long = System.nanoTime() / 1_000_000_000

    fun deHex(hex: String): ByteArray = ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }

    /** El que l'endoll ha de respondre al handshake1 si el resum és el seu. */
    fun hashHandshake1(local: ByteArray, remot: ByteArray, resum: ByteArray) = sha256(local, remot, resum)

    /** El que s'envia al handshake2. */
    fun hashHandshake2(local: ByteArray, remot: ByteArray, resum: ByteArray) = sha256(remot, local, resum)

    /**
     * L'hora local escrita com si fos UTC, que és com l'endoll entén els timestamps (el crate tapo fa el mateix): les 17:00
     * d'un dia d'octubre a Europa són «17:00 UTC» per a l'endoll.
     */
    fun aLocal(ts: Long, zona: ZoneId): Long = Instant.ofEpochSecond(ts).atZone(zona).toLocalDateTime().toEpochSecond(ZoneOffset.UTC)

    /** El contrari: d'un timestamp de l'endoll («hora local com a UTC») als segons de debò. */
    fun deLocal(local: Long, zona: ZoneId): Long = LocalDateTime.ofEpochSecond(local, 0, ZoneOffset.UTC).atZone(zona).toEpochSecond()

    /** Els dos handshakes: obre una sessió xifrada amb l'endoll, o llança [CredencialsDolentes] si el resum no és el seu. */
    fun obre(host: String, resum: ByteArray, tempsMs: Int): SessioKlap {
        val local = ByteArray(16).also(atzar::nextBytes)
        val c1 = post("http://$host/app/handshake1", local, null, tempsMs)
        val cos = llegeixCos(c1, 64)
        if (c1.responseCode != 200 || cos.size < 48) throw IOException("L'endoll $host respon ${c1.responseCode} al handshake1")
        val remot = cos.copyOfRange(0, 16)
        val hashServidor = cos.copyOfRange(16, 48)
        // Com python-kasa: si el compte no quadra, també es proven el compte de prova de TP-Link i el buit (un endoll
        // restablert de fàbrica i no lligat a cap compte els accepta)
        val valid = listOf(resum, resum("test@tp-link.net", "test"), resum("", "")).firstOrNull { hashHandshake1(local, remot, it).contentEquals(hashServidor) }
            ?: throw CredencialsDolentes()
        // La galeta de la sessió i quant dura (TIMEOUT, en segons; un dia si no ho diu)
        var galeta: String? = null
        var durada = 86400L
        c1.headerFields.filterKeys { it.equals("Set-Cookie", ignoreCase = true) }.values.flatten().forEach { h ->
            h.split(";").map { it.trim() }.forEach { tros ->
                val (k, v) = tros.split("=", limit = 2).let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
                if (k.equals("TP_SESSIONID", true)) galeta = "TP_SESSIONID=$v"
                if (k.equals("TIMEOUT", true)) v.toLongOrNull()?.let { durada = it }
            }
        }
        val c2 = post("http://$host/app/handshake2", hashHandshake2(local, remot, valid), galeta, tempsMs)
        llegeixCos(c2, 64)
        if (c2.responseCode != 200) throw IOException("L'endoll $host respon ${c2.responseCode} al handshake2")
        // Un TIMEOUT massa curt (o absent) no val: un dia, com python-kasa; el rellotge és el monòton, que un canvi d'hora no mou
        if (durada <= 20 * 60) durada = 86400
        return SessioKlap(host, galeta, local, remot, valid, monoton() + durada - 20 * 60, tempsMs)
    }

    internal fun post(url: String, cos: ByteArray, galeta: String?, tempsMs: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = tempsMs
        c.readTimeout = tempsMs
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/octet-stream")
        if (galeta != null) c.setRequestProperty("Cookie", galeta)
        c.outputStream.use { it.write(cos) }
        return c
    }

    internal fun llegeixCos(c: HttpURLConnection, maxim: Int): ByteArray {
        val codi = runCatching { c.responseCode }.getOrDefault(0)
        val entrada = if (codi in 200..299) c.inputStream else c.errorStream ?: return ByteArray(0)
        // (InputStream.readNBytes és d'Android 13: a mà, per al minSdk 26)
        return entrada.use {
            val sortida = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (sortida.size() < maxim) {
                val n = it.read(buf, 0, minOf(buf.size, maxim - sortida.size()))
                if (n < 0) break
                sortida.write(buf, 0, n)
            }
            sortida.toByteArray()
        }
    }
}

/**
 * Una sessió xifrada amb un endoll: les claus que surten dels dos atzars i del resum, i el número de seqüència, que puja a
 * cada petició. Igual que KlapEncryptionSession de python-kasa.
 */
class SessioKlap internal constructor(
    private val host: String, private val galeta: String?, local: ByteArray, remot: ByteArray, resum: ByteArray,
    private val caducaTs: Long, private val tempsMs: Int,
) {
    private val clau: ByteArray
    private val iv: ByteArray
    private val signatura: ByteArray
    internal var seq: Int

    init {
        fun sha256(vararg parts: ByteArray) = MessageDigest.getInstance("SHA-256").run { parts.forEach(::update); digest() }
        clau = sha256("lsk".toByteArray(), local, remot, resum).copyOfRange(0, 16)
        val ivSencer = sha256("iv".toByteArray(), local, remot, resum)
        iv = ivSencer.copyOfRange(0, 12)
        seq = java.nio.ByteBuffer.wrap(ivSencer, 28, 4).int
        signatura = sha256("ldk".toByteArray(), local, remot, resum).copyOfRange(0, 28)
    }

    val caducada get() = Klap.monoton() >= caducaTs

    private fun ivDe(seq: Int) = IvParameterSpec(iv + java.nio.ByteBuffer.allocate(4).putInt(seq).array())

    /** Xifra un missatge amb la seqüència següent: la signatura (32 bytes) i el text xifrat. Torna el cos i la seqüència. */
    fun xifra(missatge: String): Pair<ByteArray, Int> {
        seq += 1
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clau, "AES"), ivDe(seq))
        val xifrat = c.doFinal(missatge.toByteArray(Charsets.UTF_8))
        val sig = MessageDigest.getInstance("SHA-256").run {
            update(signatura); update(java.nio.ByteBuffer.allocate(4).putInt(seq).array()); update(xifrat); digest()
        }
        return (sig + xifrat) to seq
    }

    /** Desxifra una resposta, que ve amb la mateixa seqüència que la petició. */
    fun desxifra(cos: ByteArray, seq: Int): String {
        if (cos.size < 48 || (cos.size - 32) % 16 != 0) throw IOException("L'endoll $host respon mal format (${cos.size} bytes)")
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(clau, "AES"), ivDe(seq))
        return try {
            String(c.doFinal(cos, 32, cos.size - 32), Charsets.UTF_8)
        } catch (e: java.security.GeneralSecurityException) {
            throw IOException("No es pot desxifrar la resposta de l'endoll $host", e)
        }
    }

    fun peticio(metode: String, params: JSONObject?): JSONObject {
        val cos = JSONObject().put("method", metode).put("request_time_milis", System.currentTimeMillis()).put("terminal_uuid", TERMINAL)
        if (params != null) cos.put("params", params)
        val (xifrat, s) = xifra(cos.toString())
        val c = Klap.post("http://$host/app/request?seq=$s", xifrat, galeta, tempsMs)
        val resposta = Klap.llegeixCos(c, 1024 * 1024)
        if (c.responseCode == 403) throw SessioTancada()
        if (c.responseCode != 200) throw IOException("L'endoll $host respon ${c.responseCode}")
        return try {
            JSONObject(desxifra(resposta, s))
        } catch (e: org.json.JSONException) {
            throw IOException("La resposta de l'endoll $host no és JSON", e)
        }
    }

    companion object {
        private val TERMINAL = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
    }
}

/**
 * Troba els endolls Tapo de la wifi: un datagrama UDP al port 20002 a tota la xarxa, i cada endoll respon amb qui és
 * (python-kasa fa el mateix). Només es tornen els que parlen KLAP; els Shelly Plug no responen a això.
 */
object DescobrimentTapo {
    private val CONSULTA = Klap.deHex("020000010000000000000000463cb5d3")

    fun busca(tempsMs: Int = 3000, adreces: List<InetAddress> = listOf(InetAddress.getByName("255.255.255.255"))): List<EndollTrobat> {
        val trobats = linkedMapOf<String, EndollTrobat>()
        DatagramSocket().use { s ->
            s.broadcast = true
            s.soTimeout = 500
            for (a in adreces) runCatching { s.send(DatagramPacket(CONSULTA, CONSULTA.size, a, 20002)) }
            val fi = System.currentTimeMillis() + tempsMs
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < fi) {
                val p = DatagramPacket(buf, buf.size)
                try { s.receive(p) } catch (_: SocketTimeoutException) { continue }
                if (p.length <= 16) continue
                val t = runCatching { llegeix(String(buf, 16, p.length - 16, Charsets.UTF_8), p.address.hostAddress ?: "") }.getOrNull() ?: continue
                // Només els que l'app sap llegir (KLAP v2): un Kasa amb KLAP v1 o un Tapo de firmware vell no s'hi afegirien
                if (t.klap) trobats.putIfAbsent(t.mac.ifBlank { t.host }, t)
            }
        }
        return trobats.values.toList()
    }

    internal fun llegeix(json: String, ip: String): EndollTrobat? {
        val r = JSONObject(json).optJSONObject("result") ?: return null
        val esquema = r.optJSONObject("mgt_encrypt_schm")
        val tipus = r.optString("device_type")
        if (!tipus.contains("PLUG", ignoreCase = true)) return null
        val port = esquema?.optInt("http_port", 80) ?: 80
        // L'adreça és la d'on ha vingut el datagrama, no la que diu el paquet (python-kasa fa igual)
        val host = ip + if (port != 80) ":$port" else ""
        val klap = esquema?.optString("encrypt_type") == "KLAP" && esquema.optInt("lv") == 2
        return EndollTrobat(host, r.optString("device_model").substringBefore("("), normalitzaMacEndoll(r.optString("mac")), klap, port)
    }
}

@Suppress("unused")
private fun JSONArray.sumaWh() = (0 until length()).sumOf { optDouble(it, 0.0) }
