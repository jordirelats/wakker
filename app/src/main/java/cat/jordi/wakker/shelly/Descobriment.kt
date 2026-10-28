package cat.jordi.wakker.shelly

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.util.ArrayDeque
import kotlin.math.abs

/** Un Shelly trobat a la wifi. */
data class Trobat(val nom: String, val host: String)

/**
 * Busca Shelly a la xarxa local de dues maneres alhora:
 * - per mDNS (els Gen2 i Gen3 s'anuncien com a _shelly._tcp), que molts routers no deixen passar
 *   (sobretot entre la banda de 2,4 GHz i la de 5, o amb repetidors);
 * - preguntant a cada adreça de la wifi si és un Shelly amb mesura d'energia ([escaneja]). Si el Shelly que ja hi ha
 *   configurat ([actual]) respon, no cal: no es fan 254 peticions cada cop que s'obre la configuració.
 * Crida [enTrobar] al fil principal per cada un que troba. Retorna una funció per aturar la cerca.
 */
fun descobreix(context: Context, actual: String, enTrobar: (Trobat) -> Unit): () -> Unit {
    val principal = Handler(Looper.getMainLooper())
    val troba: (Trobat) -> Unit = { t -> principal.post { enTrobar(t) } }
    val atura = mdns(context, troba)
    val feina = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    feina.launch {
        val ja = actual.takeIf { it.isNotBlank() }?.let(::shellyA)
        // Amb un Shelly configurat que no respon, només s'escaneja si és a la xarxa d'ara (ha canviat d'adreça a casa): fora
        // de casa, a la wifi de la feina o d'un hotel, no es pregunta a mil adreces d'una xarxa aliena (1.1.3)
        if (ja != null) troba(ja) else escaneja(context, troba, nomesSiHiEs = actual.substringBefore(':').takeIf { it.isNotBlank() })
    }
    return {
        atura()
        feina.cancel()
    }
}

private fun mdns(context: Context, enTrobar: (Trobat) -> Unit): () -> Unit {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    val cua = ArrayDeque<NsdServiceInfo>()
    var resolent = false

    // L'NsdManager només resol un servei alhora: es resolen d'un en un, en cua.
    fun seguent(): Unit = synchronized(cua) {
        if (resolent) return
        val servei = cua.pollFirst() ?: return
        resolent = true
        @Suppress("DEPRECATION")
        try { nsd.resolveService(servei, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, codi: Int) {
                resolent = false
                seguent()
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val ip = (info.host as? Inet4Address)?.hostAddress
                if (ip != null) enTrobar(Trobat(info.serviceName, if (info.port == 80) ip else "$ip:${info.port}"))
                resolent = false
                seguent()
            }
        }) } catch (_: IllegalArgumentException) {
            // Un oient que encara es fa servir (alguns Android antics): se salta aquest servei
            resolent = false
        }
    }

    val oient = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(tipus: String) {}
        override fun onDiscoveryStopped(tipus: String) {}
        override fun onStartDiscoveryFailed(tipus: String, codi: Int) {}
        override fun onStopDiscoveryFailed(tipus: String, codi: Int) {}
        override fun onServiceLost(info: NsdServiceInfo) {}
        override fun onServiceFound(info: NsdServiceInfo) {
            synchronized(cua) { cua.addLast(info) }
            seguent()
        }
    }
    nsd.discoverServices("_shelly._tcp", NsdManager.PROTOCOL_DNS_SD, oient)
    return { runCatching { nsd.stopServiceDiscovery(oient) } }
}

/**
 * Pregunta a les adreces de la wifi del mòbil si són un Shelly: /shelly, i després, si té mesura d'energia (em1:0
 * a Shelly.GetStatus). Amb 48 preguntes alhora, una /24 (254 adreces) es mira en pocs segons.
 */
private suspend fun escaneja(context: Context, enTrobar: (Trobat) -> Unit, nomesSiHiEs: String? = null) {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val xarxa = cm.activeNetwork ?: return
    if (cm.getNetworkCapabilities(xarxa)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return
    val enllac = cm.getLinkProperties(xarxa)?.linkAddresses?.firstOrNull { it.address is Inet4Address } ?: return
    val adreces = adrecesAMirar(enllac.address.address, enllac.prefixLength)
    if (nomesSiHiEs != null && nomesSiHiEs !in adreces) return
    val alhora = Semaphore(48)
    coroutineScope {
        for (ip in adreces) {
            launch { alhora.withPermit { shellyA(ip)?.let(enTrobar) } }
        }
    }
}

/** Com a molt, una /22: 1.022 adreces, que amb 48 preguntes alhora es miren en uns 15 segons. */
private const val PREFIX_MINIM = 22

/**
 * Les adreces de la xarxa de [propia] (4 bytes) amb el prefix [prefix], sense la de la xarxa, la de difusió ni la
 * pròpia, de les més properes a les més llunyanes: el Shelly sol ser a prop del mòbil, i surt abans. Si la xarxa
 * és més gran que una /22, es mira la /22 on és el mòbil.
 */
internal fun adrecesAMirar(propia: ByteArray, prefix: Int): List<String> {
    val ip = propia.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
    val p = prefix.coerceIn(PREFIX_MINIM, 30)
    val mida = 1L shl (32 - p)
    val inici = ip and (0xffffffffL xor (mida - 1))
    return (inici + 1 until inici + mida - 1)
        .filter { it != ip }
        .sortedBy { abs(it - ip) }
        .map { a -> "${a shr 24 and 0xff}.${a shr 16 and 0xff}.${a shr 8 and 0xff}.${a and 0xff}" }
}

/** Si a [ip] hi ha un Shelly Gen2 o Gen3 amb mesura d'energia, el seu nom; si no, null. */
private fun shellyA(ip: String): Trobat? {
    val info = runCatching { JSONObject(llegeixUrl("http://$ip/shelly", 600)) }.getOrNull() ?: return null
    if (info.optInt("gen") < 2) return null
    val estat = runCatching { JSONObject(llegeixUrl("http://$ip/rpc/Shelly.GetStatus", 2000)) }.getOrNull() ?: return null
    if (!estat.has("em1:0")) return null
    val nom = info.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: info.optString("id")
    return Trobat(nom, ip)
}

/** Com a molt [max] bytes d'una resposta: un aparell que no l'acaba no pot omplir la memòria (1.1.3). */
internal fun llegeixFins(entrada: java.io.InputStream, max: Int): String {
    val sortida = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    entrada.use {
        while (true) {
            val n = it.read(buffer)
            if (n < 0) break
            sortida.write(buffer, 0, n)
            if (sortida.size() > max) throw java.io.IOException("resposta massa gran")
        }
    }
    return sortida.toString(Charsets.UTF_8.name())
}

private fun llegeixUrl(url: String, tempsMs: Int): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = tempsMs
    c.readTimeout = tempsMs
    try {
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        return llegeixFins(c.inputStream, 256 * 1024)
    } finally {
        c.disconnect()
    }
}
