package cat.jordi.wakker.dades

import android.content.Context
import android.content.Intent
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
 * que diu de quina app i de quina versió és. Serveix per canviar de mòbil; Wakker també llegeix les còpies de Consum.
 *
 * Des de la 1.1.3, amb el que van trobar les revisions:
 * - **Desar** fa una foto coherent de la base: dins d'una transacció exclusiva, ningú no hi escriu ni fa checkpoint mentre es
 *   copia el fitxer ([fotografia]). La foto es comprova ([comprova]) i el zip es fa després, sense cap bloqueig (el Drive pot ser
 *   lent, i mentrestant les lectures en directe continuen).
 * - **Restaurar** no toca res fins que tot s'ha comprovat ([prepara]: la base amb `quick_check`, les taules i la versió; la
 *   configuració, entrada per entrada). Ho deixa a punt a `files/restaura/` i l'app es torna a obrir: és [aplicaPendent], a
 *   l'inici, abans d'obrir cap base de dades, qui canvia els fitxers (i, si alguna cosa falla, torna a posar els d'abans).
 */
object Copia {
    /** El nom que porta la còpia; Wakker també llegeix les de Consum. */
    const val APP = "wakker"
    private const val FORMAT = 1
    private val APPS = setOf("consum", "wakker")

    /** Les taules que té qualsevol base de l'app, des de la primera versió. */
    private val TAULES = setOf("minuts", "aparells")

    /** Una configuració més gran que això no és de l'app. */
    private const val MAX_CONFIG = 512 * 1024
    private const val DIR = "restaura"
    private const val MARCA = "llest"
    private val SUFIXOS = listOf("-wal", "-shm", "-journal")

    class Manifest(val app: String, val versioApp: String, val versioBd: Int, val data: String)

    /**
     * Una foto coherent de la base de dades en un fitxer temporal. Primer es passa al fitxer principal tot el que hi ha al diari
     * (WAL); després, dins d'una transacció exclusiva, es copia el fitxer: ningú no hi pot escriure ni fer checkpoint mentre dura.
     */
    fun fotografia(context: Context, bd: BaseDades): File {
        val db = bd.writableDatabase
        runCatching { db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() } }
        val temp = File(context.cacheDir, "copia-desa/base.db").apply { parentFile?.mkdirs(); delete() }
        db.beginTransaction()
        try {
            File(db.path).inputStream().use { e -> temp.outputStream().use { e.copyTo(it) } }
        } finally {
            db.endTransaction()
        }
        return temp
    }

    /** Escriu el zip amb la foto de la base ([fotografia]), que primer es comprova. */
    fun escriu(base: File, config: Config, versioApp: String, sortida: OutputStream) {
        val versioBd = comprova(base, Int.MAX_VALUE)
        val manifest = JSONObject().put("app", APP).put("format", FORMAT).put("versioApp", versioApp).put("versioBd", versioBd).put("data", LocalDate.now().toString())
        ZipOutputStream(sortida.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("config.json")); zip.write(configAJson(config.totes()).toString().toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("base.db")); base.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
        }
    }

    /**
     * Que un fitxer sigui una base de dades de l'app sencera: `quick_check`, les taules de sempre, cap trigger ni vista, i una
     * versió entre 1 i [maxVersio]. Torna la versió.
     */
    private fun comprova(base: File, maxVersio: Int): Int {
        val versio = try {
            SQLiteDatabase.openDatabase(base.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                val bona = db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
                val objectes = db.rawQuery("SELECT name, type FROM sqlite_master", null).use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
                }
                val estrany = objectes.any { it.second == "trigger" || it.second == "view" }
                if (!bona || estrany || !TAULES.all { t -> objectes.any { it.first == t && it.second == "table" } }) throw ErrorUsuari(R.string.copia_no_valida)
                db.version
            }
        } catch (e: ErrorUsuari) {
            throw e
        } catch (e: Exception) {
            throw ErrorUsuari(R.string.copia_no_valida)
        } finally {
            for (s in SUFIXOS) File(base.path + s).delete()
        }
        if (versio < 1) throw ErrorUsuari(R.string.copia_no_valida)
        if (versio > maxVersio) throw ErrorUsuari(R.string.copia_massa_nova)
        return versio
    }

    /**
     * Llegeix una còpia i la deixa a punt per restaurar a `files/restaura/`, després de comprovar-ho tot. No toca ni la base de
     * dades ni la configuració d'ara: ho fa [aplicaPendent] quan l'app es torna a obrir ([reinicia]).
     */
    fun prepara(context: Context, versioActual: Int, entrada: InputStream): Manifest {
        val dir = File(context.filesDir, DIR).apply { deleteRecursively(); mkdirs() }
        try {
            var manifest: Manifest? = null
            var config: JSONObject? = null
            val base = File(dir, "base.db")
            var ambBase = false
            ZipInputStream(entrada.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when (e.name) {
                        "manifest.json" -> JSONObject(llegeixFins(zip, MAX_CONFIG)).let {
                            manifest = Manifest(it.optString("app"), it.optString("versioApp"), it.optInt("versioBd"), it.optString("data"))
                        }
                        "config.json" -> config = JSONObject(llegeixFins(zip, MAX_CONFIG))
                        "base.db" -> if (!ambBase) {
                            base.outputStream().use { zip.copyTo(it) }
                            ambBase = true
                        }
                    }
                    zip.closeEntry()
                }
            }
            val m = manifest
            val c = config
            if (m == null || m.app !in APPS || !ambBase || c == null) throw ErrorUsuari(R.string.copia_no_valida)
            comprova(base, versioActual)
            // La configuració, entrada per entrada: si n'hi ha cap de mal feta, la còpia no es fa servir
            jsonAValors(c)
            File(dir, "config.json").writeText(c.toString())
            File(dir, MARCA).writeText(m.data)
            return m
        } catch (e: ErrorUsuari) {
            dir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw ErrorUsuari(R.string.copia_no_valida)
        }
    }

    /** Llegeix una entrada del zip com a text, com a molt [max] bytes. */
    private fun llegeixFins(zip: ZipInputStream, max: Int): String {
        val sortida = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            sortida.write(buffer, 0, n)
            if (sortida.size() > max) throw ErrorUsuari(R.string.copia_no_valida)
        }
        return sortida.toString(Charsets.UTF_8.name())
    }

    /** Si hi ha una còpia a punt per restaurar (l'app encara no s'ha tornat a obrir). */
    fun pendent(context: Context) = File(File(context.filesDir, DIR), MARCA).exists()

    /** Torna a obrir l'app: el procés s'acaba i l'activitat principal torna a començar en un de nou. */
    fun reinicia(context: Context) {
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(i.component))
        Runtime.getRuntime().exit(0)
    }

    /**
     * A l'inici de l'app, abans d'obrir la base de dades: si hi ha una còpia a punt ([prepara]), en posa la base i la
     * configuració en lloc de les d'ara. La base d'abans es guarda fins que la nova s'ha obert bé; si falla, es torna a posar.
     * Torna la data de la còpia restaurada, o null si no n'hi havia cap (o no s'ha pogut, i llavors tot queda com era).
     */
    fun aplicaPendent(context: Context): String? {
        val dir = File(context.filesDir, DIR)
        val marca = File(dir, MARCA)
        if (!marca.exists()) return null
        val desti = context.getDatabasePath(BaseDades.NOM)
        val abans = File(desti.path + ".abans")
        val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
        try {
            val data = marca.readText()
            val valors = jsonAValors(JSONObject(File(dir, "config.json").readText()))
            desti.parentFile?.mkdirs()
            abans.delete()
            if (desti.exists() && !desti.renameTo(abans)) throw IllegalStateException("no es pot apartar la base d'ara")
            for (s in SUFIXOS) File(desti.path + s).delete()
            val base = File(dir, "base.db")
            if (!base.renameTo(desti)) base.copyTo(desti, overwrite = true)
            // Que s'obri bé abans d'esborrar la d'abans
            SQLiteDatabase.openDatabase(desti.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).close()
            // La configuració, d'un sol cop: les claus que ja hi ha amb un altre tipus es deixen com són (no petaria en llegir-les)
            val ara = prefs.all
            prefs.edit().clear().apply {
                for ((clau, v) in valors) {
                    val actual = ara[clau]
                    if (actual != null && actual::class != v::class) continue
                    when (v) {
                        is String -> putString(clau, v)
                        is Boolean -> putBoolean(clau, v)
                        is Int -> putInt(clau, v)
                        is Long -> putLong(clau, v)
                        is Float -> putFloat(clau, v)
                    }
                }
                putString(CLAU_RESTAURADA, data)
            }.commit()
            abans.delete()
            for (s in SUFIXOS) File(abans.path + s).delete()
            return data
        } catch (e: Exception) {
            // Tot com era: la base d'abans al seu lloc i la configuració sense tocar
            if (abans.exists()) {
                desti.delete()
                for (s in SUFIXOS) File(desti.path + s).delete()
                abans.renameTo(desti)
            }
            prefs.edit().putString(CLAU_RESTAURADA, "").commit()
            return null
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Després de tornar a obrir l'app: la data de la còpia restaurada, o "" si no s'ha pogut; null si no n'hi havia cap. */
    const val CLAU_RESTAURADA = "copia_restaurada"

    /** Les preferències amb el seu tipus: {"clau": {"t": "s|b|i|l|f", "v": …}}. */
    private fun configAJson(totes: Map<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((clau, valor) in totes) {
            if (clau == CLAU_RESTAURADA) continue
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

    /** La configuració d'una còpia, amb els tipus; una entrada mal feta fa que la còpia no serveixi. */
    private fun jsonAValors(o: JSONObject): Map<String, Any> {
        val valors = HashMap<String, Any>()
        for (clau in o.keys()) {
            val e = o.getJSONObject(clau)
            valors[clau] = when (e.getString("t")) {
                "s" -> e.getString("v")
                "b" -> e.getBoolean("v")
                "i" -> e.getInt("v")
                "l" -> e.getLong("v")
                "f" -> e.getDouble("v").toFloat()
                else -> throw ErrorUsuari(R.string.copia_no_valida)
            }
        }
        return valors
    }
}
