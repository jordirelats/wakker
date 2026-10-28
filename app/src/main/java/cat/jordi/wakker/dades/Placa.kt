package cat.jordi.wakker.dades

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import cat.jordi.wakker.R
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** El que s'ha trobat a la placa: la potència (W), el tros de text d'on surt i si és calculada amb els amperes. */
data class LecturaPlaca(val w: Double?, val fragment: String?, val delsAmperes: Boolean, val text: String)

/**
 * La placa de característiques: es llegeix la foto amb Tesseract, dins del mòbil (no s'envia enlloc),
 * i se'n treu la potència.
 */
object Placa {
    /** Una potència: «2000W», «2,2 kW», «1850-2200 W». No pot anar enganxada a lletres o xifres (com «KD-32W800»). */
    private val reW = Regex("""(?<![A-Za-z0-9.,])(\d{1,5}(?:[.,]\d{1,2})?)\s*(?:[-–~]\s*(\d{1,5}(?:[.,]\d{1,2})?)\s*)?(kW|KW|Kw|kw|W)(?![A-Za-z0-9])""")
    private val reA = Regex("""(?<![A-Za-z0-9.,])(\d{1,2}(?:[.,]\d{1,2})?)\s*A(?![A-Za-z0-9])""")
    private val reV = Regex("""(?<![A-Za-z0-9.,])(\d{3})(?:\s*[-–/]\s*(\d{3}))?\s*V""")

    private fun numero(s: String) = s.replace(',', '.').toDoubleOrNull()

    /**
     * Els models que hi pot haver al text de la placa (maqueta 14), per buscar-los a l'EPREL: paraules de 6 a 20 lletres i
     * xifres, amb almenys dues lletres i dues xifres, que no siguin una tensió, una potència, una freqüència o uns amperes
     * («220-240V», «2400W», «50HZ»). Primer les que porten al davant «E-Nr», «Mod», «Model», «Type» o «PNC». Com a molt 3.
     */
    fun models(textOcr: String): List<String> {
        val text = textOcr.uppercase()
        // Una tensió, una potència o una freqüència: xifres (o un interval) i la unitat. «FTXM25A» sí que és un model
        val unitat = Regex("""^[\d.,\-/~]+(V|W|KW|VA|HZ)$""")
        val paraula = Regex("""[A-Z0-9][A-Z0-9/.\-]{5,19}""")
        val etiqueta = Regex("""(E-?\s?NR|MOD(EL)?|TYP(E)?|PNC)\.?\s*:?\s*$""")
        val trobades = paraula.findAll(text).mapNotNull { m ->
            val p = m.value.trimEnd('.', '-', '/')
            val lletres = p.count { it.isLetter() }
            val xifres = p.count { it.isDigit() }
            if (lletres < 2 || xifres < 2 || unitat.containsMatchIn(p)) null
            else p to etiqueta.containsMatchIn(text.substring(maxOf(0, m.range.first - 8), m.range.first))
        }.toList()
        return trobades.sortedByDescending { it.second }.map { it.first }.distinct().take(3)
    }

    /** Treu la potència del text llegit. Si no n'hi ha, la calcula amb els amperes i la tensió (o 230 V). */
    fun potencia(textOcr: String): LecturaPlaca {
        // Les confusions típiques de l'OCR: «VV» per «W», i la «O» enganxada a xifres per un zero
        val text = textOcr.replace("VV", "W").replace(Regex("""(?<=\d)[Oo]|[Oo](?=\d)"""), "0")
        val linies = text.lines()
        var millor: Pair<Double, String>? = null
        for (linia in linies) for (m in reW.findAll(linia)) {
            val factor = if (m.groupValues[3].startsWith("k", ignoreCase = true)) 1000.0 else 1.0
            val valors = listOfNotNull(numero(m.groupValues[1]), m.groupValues[2].takeIf { it.isNotEmpty() }?.let(::numero))
            val w = valors.maxOrNull()?.times(factor) ?: continue
            if (w in 1.0..20_000.0 && (millor == null || w > millor.first)) millor = w to linia.trim()
        }
        if (millor != null) return LecturaPlaca(millor.first, millor.second, false, textOcr)

        val volts = linies.flatMap { reV.findAll(it).flatMap { m -> listOfNotNull(numero(m.groupValues[1]), numero(m.groupValues[2])) }.toList() }
            .filter { it in 100.0..400.0 }.maxOrNull() ?: 230.0
        var amperes: Pair<Double, String>? = null
        for (linia in linies) for (m in reA.findAll(linia)) {
            val a = numero(m.groupValues[1]) ?: continue
            if (a in 0.1..32.0 && (amperes == null || a > amperes.first)) amperes = a to linia.trim()
        }
        return amperes?.let { LecturaPlaca(it.first * volts, it.second, true, textOcr) } ?: LecturaPlaca(null, null, false, textOcr)
    }

    /**
     * La foto preparada per a l'OCR: girada com toca (EXIF), en grisos, amb el contrast estirat (el 2 % més fosc
     * a negre i el 2 % més clar a blanc) i una vegada i mitja més gran. Amb les plaques platejades i borroses,
     * això és el que fa que Tesseract llegeixi bé les xifres (provat amb fotos de prova).
     */
    fun carrega(resolver: ContentResolver, uri: Uri): Bitmap {
        val mida = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, mida) }
        var mostreig = 1
        while (maxOf(mida.outWidth, mida.outHeight) / mostreig > 2000) mostreig *= 2
        val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = mostreig }) }
            ?: throw ErrorUsuari(R.string.error_foto)
        val graus = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val girada = if (graus == 0f) original else
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(graus) }, true)
        val grisos = Bitmap.createBitmap(girada.width, girada.height, Bitmap.Config.ARGB_8888)
        Canvas(grisos).drawBitmap(girada, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) })
        val (fosc, clar) = percentils(grisos)
        val escala = 255f / maxOf(clar - fosc, 1)
        val desplaçament = -fosc * escala
        val contrast = ColorMatrix(
            floatArrayOf(
                escala, 0f, 0f, 0f, desplaçament,
                0f, escala, 0f, 0f, desplaçament,
                0f, 0f, escala, 0f, desplaçament,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        val gran = Bitmap.createBitmap(grisos.width * 3 / 2, grisos.height * 3 / 2, Bitmap.Config.ARGB_8888)
        Canvas(gran).drawBitmap(
            grisos, null, android.graphics.Rect(0, 0, gran.width, gran.height),
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(contrast) },
        )
        return gran
    }

    /** El nivell de gris (0–255) per sota del qual hi ha el 2 % dels píxels, i per sobre del qual n'hi ha el 2 %. */
    private fun percentils(gris: Bitmap): Pair<Int, Int> {
        val petita = Bitmap.createScaledBitmap(gris, 300, maxOf(1, gris.height * 300 / gris.width), true)
        val pixels = IntArray(petita.width * petita.height).also { petita.getPixels(it, 0, petita.width, 0, 0, petita.width, petita.height) }
        val histograma = IntArray(256)
        pixels.forEach { histograma[(it shr 16) and 0xff]++ }
        val tall = pixels.size / 50
        var acumulat = 0
        val fosc = histograma.indexOfFirst { acumulat += it; acumulat > tall }.coerceAtLeast(0)
        acumulat = 0
        val clar = 255 - histograma.reversed().indexOfFirst { acumulat += it; acumulat > tall }.coerceAtLeast(0)
        return fosc to clar
    }

    /** Tesseract necessita el fitxer de l'idioma en una carpeta «tessdata»: es copia dels assets el primer cop. */
    private fun dades(context: Context): File {
        val arrel = File(context.filesDir, "ocr")
        val fitxer = File(arrel, "tessdata/eng.traineddata")
        if (!fitxer.exists()) {
            fitxer.parentFile?.mkdirs()
            // Primer a un fitxer temporal: si l'app es tanca a mitja còpia, no queda un model trencat per sempre
            val temporal = File(fitxer.parentFile, "eng.traineddata.part")
            context.assets.open("tessdata/eng.traineddata").use { entrada -> temporal.outputStream().use { entrada.copyTo(it) } }
            if (!temporal.renameTo(fitxer)) throw ErrorUsuari(R.string.error_ocr)
        }
        return arrel
    }

    /**
     * Llegeix el text de la foto. Triga uns segons. Una placa té el text escampat en blocs; un [document]
     * (contracte o factura) es llegeix per línies, que és com el lector de preus el necessita.
     */
    fun llegeix(context: Context, foto: Bitmap, document: Boolean = false): String {
        val api = TessBaseAPI()
        try {
            if (!api.init(dades(context).absolutePath, "eng")) throw ErrorUsuari(R.string.error_ocr)
            api.setPageSegMode(if (document) TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN else TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
            api.setImage(foto)
            return api.utF8Text ?: ""
        } finally {
            api.recycle()
        }
    }
}
