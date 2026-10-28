package cat.jordi.wakker.dades

import cat.jordi.wakker.R
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Els preus de mercat per hores, de fonts públiques que no demanen clau:
 * - Països Baixos: EnergyZero, la sèrie all_in_with_vat (EPEX + energiebelasting, amb IVA), en €/kWh.
 * - Espanya: Red Eléctrica, la sèrie PVPC (sense impostos), que ve en €/MWh i es desa en €/kWh.
 * Cada preu es desa amb l'inici de la seva hora (segons Unix).
 */
object Mercat {
    private fun llegeix(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    fun preus(pais: Pais, dia: LocalDate): List<Pair<Long, Double>> = when (pais) {
        Pais.NL -> {
            val data = dia.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val serie = llegeix(
                "https://public.api.energyzero.nl/public/v1/prices?date=$data&interval=INTERVAL_HOUR&energyType=ENERGY_TYPE_ELECTRICITY"
            ).getJSONArray("all_in_with_vat")
            List(serie.length()) { i ->
                val p = serie.getJSONObject(i)
                Instant.parse(p.getString("start")).epochSecond to p.getJSONObject("price").getString("value").toDouble()
            }
        }
        Pais.ES -> {
            val inclosos = llegeix(
                "https://apidatos.ree.es/es/datos/mercados/precios-mercados-tiempo-real?start_date=${dia}T00:00&end_date=${dia}T23:59&time_trunc=hour"
            ).getJSONArray("included")
            val pvpc = (0 until inclosos.length()).map { inclosos.getJSONObject(it) }.firstOrNull { it.optString("type") == "PVPC" }
                ?: throw ErrorUsuari(R.string.error_pvpc, dia.toString())
            val valors = pvpc.getJSONObject("attributes").getJSONArray("values")
            List(valors.length()) { i ->
                val v = valors.getJSONObject(i)
                OffsetDateTime.parse(v.getString("datetime")).toEpochSecond() to v.getDouble("value") / 1000
            }
        }
        Pais.ALTRE -> emptyList()
    }
}
