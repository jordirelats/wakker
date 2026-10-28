package cat.jordi.wakker.dades

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import cat.jordi.wakker.R
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * La còpia de seguretat (1.0.2): un zip amb la base de dades sencera (`base.db`: els minuts, les vegades, els aparells, el
 * que s'ha ensenyat), la configuració (`config.json`: el Shelly, la clau del núvol, la tarifa, els avisos) i un `manifest.json`
 * que diu de quina app i de quina versió és. Serveix per canviar de mòbil, i per passar de Consum a Wakker.
 */
object Copia {
    /** El nom que porta la còpia; Wakker també llegeix les de Consum. */
    const val APP = "wakker"
    private const val FORMAT = 1
    private val APPS = setOf("consum", "wakker")

    class Manifest(val app: String, val versioApp: String, val versioBd: Int, val data: String)

    /** Escriu la còpia. La base de dades no s'ha d'estar escrivint mentrestant: qui crida ho garanteix. */
    fun escriu(context: Context, bd: BaseDades, config: Config, versioApp: String, sortida: OutputStream) {
        val db = bd.writableDatabase
        // Tot el que hi hagi al diari (WAL), a dins del fitxer principal, que és l'únic que es copia
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        val fitxer = File(db.path)
        val manifest = JSONObject().put("app", APP).put("format", FORMAT).put("versioApp", versioApp).put("versioBd", db.version).put("data", LocalDate.now().toString())
        ZipOutputStream(sortida.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("config.json")); zip.write(configAJson(config).toString().toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("base.db")); fitxer.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
        }
    }

    /**
     * Llegeix una còpia i la posa en lloc del que hi ha: la base de dades (tancant la d'ara i substituint-ne el fitxer; es torna
     * a obrir sola al proper ús) i la configuració. Una còpia d'una versió més nova de la base de dades no es pot restaurar.
     */
    fun llegeix(context: Context, bd: BaseDades, config: Config, entrada: InputStream): Manifest {
        val temp = File(context.cacheDir, "copia").apply { deleteRecursively(); mkdirs() }
        var manifest: Manifest? = null
        var configJson: JSONObject? = null
        var baseTemp: File? = null
        ZipInputStream(entrada.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when (e.name) {
                    "manifest.json" -> JSONObject(zip.readBytes().toString(Charsets.UTF_8)).let {
                        manifest = Manifest(it.optString("app"), it.optString("versioApp"), it.optInt("versioBd"), it.optString("data"))
                    }
                    "config.json" -> configJson = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    "base.db" -> baseTemp = File(temp, "base.db").also { f -> f.outputStream().use { zip.copyTo(it) } }
                }
                zip.closeEntry()
            }
        }
        val m = manifest
        val base = baseTemp
        if (m == null || m.app !in APPS || base == null || configJson == null) throw ErrorUsuari(R.string.copia_no_valida)
        // Que el fitxer sigui una base de dades SQLite i no sigui d'una versió més nova que la que entén aquesta app
        val versio = runCatching {
            SQLiteDatabase.openDatabase(base.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        }.getOrElse { throw ErrorUsuari(R.string.copia_no_valida) }
        val actual = bd.writableDatabase.version
        if (versio > actual) throw ErrorUsuari(R.string.copia_massa_nova)
        val desti = File(bd.writableDatabase.path)
        bd.close()
        for (sufix in listOf("-wal", "-shm", "-journal")) File(desti.path + sufix).delete()
        base.copyTo(desti, overwrite = true)
        temp.deleteRecursively()
        // En tornar-la a obrir, SQLiteOpenHelper la puja de versió si cal
        bd.writableDatabase
        jsonAConfig(configJson!!, config)
        return m
    }

    /** Les preferències amb el seu tipus: {"clau": {"t": "s|b|i|l|f", "v": …}}. */
    private fun configAJson(config: Config): JSONObject {
        val o = JSONObject()
        for ((clau, valor) in config.totes()) {
            val (t, v) = when (valor) {
                is String -> "s" to valor
                is Boolean -> "b" to valor
                is Int -> "i" to valor
                is Long -> "l" to valor
                is Float -> "f" to valor.toDouble()
                else -> continue
            }
            o.put(clau, JSONObject().put("t", t).put("v", v))
        }
        return o
    }

    private fun jsonAConfig(o: JSONObject, config: Config) {
        val valors = HashMap<String, Any>()
        for (clau in o.keys()) {
            val e = o.getJSONObject(clau)
            valors[clau] = when (e.getString("t")) {
                "s" -> e.getString("v")
                "b" -> e.getBoolean("v")
                "i" -> e.getInt("v")
                "l" -> e.getLong("v")
                "f" -> e.getDouble("v").toFloat()
                else -> continue
            }
        }
        config.posaTotes(valors)
    }
}
