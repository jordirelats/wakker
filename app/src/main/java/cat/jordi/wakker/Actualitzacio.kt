package cat.jordi.wakker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import cat.jordi.wakker.dades.ErrorUsuari
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una versió publicada a GitHub més nova que la instal·lada. */
data class VersioNova(val versio: String, val url: String, val notes: String)

/**
 * Les actualitzacions: l'app mira l'última versió publicada a GitHub, en baixa l'APK i obre l'instal·lador
 * d'Android. El primer cop, Android demana permís perquè Wakker pugui instal·lar apps.
 */
object Actualitzacio {
    const val REPO = "jordirelats/wakker"

    fun versioActual(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** Compara versions com «0.10» i «0.9» per parts numèriques. */
    fun esMesNova(a: String, b: String): Boolean {
        val pa = a.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** L'última versió publicada, si és més nova que la instal·lada. */
    fun comprova(context: Context): VersioNova? = darrera(REPO) { esMesNova(it, versioActual(context)) }

    /** L'última versió publicada d'un repositori, si [val] la dona per bona; null si no n'hi ha cap. */
    private fun darrera(repo: String, val_: (String) -> Boolean): VersioNova? {
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val o = try {
            if (c.responseCode == 404) return null
            if (c.responseCode !in 200..299) throw ErrorUsuari(R.string.error_github, c.responseCode)
            JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
        val versio = o.getString("tag_name").removePrefix("v")
        if (!val_(versio)) return null
        val actius = o.getJSONArray("assets")
        val apk = (0 until actius.length()).map { actius.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") }
            ?: return null
        return VersioNova(versio, apk.getString("browser_download_url"), o.optString("body"))
    }

    private fun fitxer(context: Context) = File(context.cacheDir, "actualitzacio/wakker.apk")

    /** Baixa l'APK. [progres] va de 0 a 1. */
    fun baixa(context: Context, v: VersioNova, progres: (Float) -> Unit): File {
        val desti = fitxer(context).apply { parentFile?.mkdirs(); delete() }
        val c = URL(v.url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        try {
            if (c.responseCode !in 200..299) throw ErrorUsuari(R.string.error_baixada, c.responseCode)
            val total = c.contentLengthLong.takeIf { it > 0 }
            var llegit = 0L
            var avisat = -1
            c.inputStream.use { entrada ->
                desti.outputStream().use { sortida ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        sortida.write(buffer, 0, n)
                        llegit += n
                        val percent = if (total != null) (llegit * 100 / total).toInt() else -1
                        if (total != null && percent != avisat) {
                            avisat = percent
                            progres(llegit.toFloat() / total)
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return desti
    }

    /** Si Android encara no deixa que Wakker instal·li apps, obre la pantalla per permetre-ho. Torna false en aquest cas. */
    fun potInstallar(context: Context): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }

    fun installa(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fitxers", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
