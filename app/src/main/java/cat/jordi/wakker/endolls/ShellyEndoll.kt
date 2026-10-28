package cat.jordi.wakker.endolls

import cat.jordi.wakker.dades.TipusEndoll
import cat.jordi.wakker.shelly.ShellyLocal
import cat.jordi.wakker.shelly.ShellyNuvol
import cat.jordi.wakker.shelly.normalitzaMac
import org.json.JSONObject
import java.io.IOException

/**
 * Un Shelly amb mesura d'un sol canal fent d'endoll (maqueta 19): un Plus Plug S, un Plug S Gen3, un 1PM Mini… Parla el
 * mateix RPC de Gen2/Gen3 que l'EM de casa: `Switch.GetStatus` dona la potència (`apower`, W) i el comptador d'energia
 * (`aenergy.total`, Wh), que avança amb el consum. No guarda cap historial (`by_minute` només té els 3 últims minuts): les
 * hores se'n fan amb el comptador, al repositori. Com que és al núvol de Shelly, també es pot llegir des de fora
 * ([lecturaNuvol]), cosa que un Tapo no permet.
 */
class ShellyEndoll(val host: String, private val macEsperada: String = "") : LectorEndoll {
    private val shelly = ShellyLocal(host)
    @Volatile
    private var macComprovada = false

    override fun info(): InfoEndoll {
        val o = shelly.rpc("Shelly.GetDeviceInfo")
        if (o.optInt("gen") < 2) throw IOException("Un Shelly Gen1 ($host): l'app només sap els Gen2 i Gen3")
        val estat = shelly.rpc("Switch.GetStatus?id=0")
        if (!estat.has("apower")) throw IOException("El Shelly $host no mesura la potència")
        return InfoEndoll(
            host, nomModel(o), normalitzaMac(o.optString("mac")), o.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: o.optString("id"),
            o.optString("ver"), estat.optBoolean("output", true), null, TipusEndoll.SHELLY,
        )
    }

    override fun lectura(): LecturaEndoll {
        // Un cop per lector, que sigui l'endoll que es va afegir (per la MAC): amb DHCP les adreces canvien
        if (macEsperada.isNotBlank() && !macComprovada) {
            val mac = normalitzaMac(shelly.rpc("Shelly.GetDeviceInfo", 1500).optString("mac"))
            if (mac.isNotBlank() && mac != macEsperada) throw IOException("A $host hi respon un altre Shelly ($mac)")
            macComprovada = true
        }
        return lecturaDe(shelly.rpc("Switch.GetStatus?id=0", 1500))
    }

    companion object {
        /** Un nom entenedor en lloc del codi del model («S3PL-00112EU» → «Shelly Plug S Gen3»). */
        fun nomModel(info: JSONObject): String {
            val model = info.optString("model")
            val app = info.optString("app")
            return when {
                model.startsWith("S3PL") -> "Shelly Plug S Gen3"
                model.startsWith("SNPL") -> "Shelly Plus Plug S"
                model.startsWith("S3SW-001P8") || app.contains("Mini1PM", true) -> "Shelly 1PM Mini"
                app.isNotBlank() -> "Shelly $app"
                else -> model.ifBlank { "Shelly" }
            }
        }

        /** La lectura de `switch:0` (local o del núvol): la potència i el comptador. */
        fun lecturaDe(s: JSONObject): LecturaEndoll {
            val total = s.optJSONObject("aenergy")?.let { if (it.has("total")) it.optDouble("total") else null }
            return LecturaEndoll(s.optDouble("apower", 0.0), null, System.currentTimeMillis() / 1000, null, total)
        }

        /** Des de fora de casa, pel núvol de Shelly: l'endoll hi és amb la seva MAC, com l'EM. Null si el núvol no el coneix. */
        fun lecturaNuvol(servidor: String, clau: String, mac: String): LecturaEndoll? {
            val estat = ShellyNuvol(servidor, clau, mac).estat() ?: return null
            val s = estat.optJSONObject("switch:0") ?: return null
            return lecturaDe(s)
        }

        /** Si a [ip] hi ha un Shelly Gen2 o Gen3 amb un `switch:0` que mesura (i no és un EM), el que cal per afegir-lo. */
        fun a(ip: String): EndollTrobat? {
            val shelly = ShellyLocal(ip)
            val info = runCatching { shelly.rpc("Shelly.GetDeviceInfo", 800) }.getOrNull() ?: return null
            if (info.optInt("gen") < 2) return null
            val estat = runCatching { shelly.rpc("Shelly.GetStatus", 2000) }.getOrNull() ?: return null
            if (estat.has("em1:0") || estat.optJSONObject("switch:0")?.has("apower") != true) return null
            return EndollTrobat(ip, nomModel(info), normalitzaMac(info.optString("mac")), false, 80, TipusEndoll.SHELLY)
        }
    }
}
