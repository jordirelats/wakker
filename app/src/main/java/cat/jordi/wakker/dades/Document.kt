package cat.jordi.wakker.dades

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import cat.jordi.wakker.R

/**
 * Un contracte o una factura per llegir amb l'OCR: les pàgines d'un PDF (convertides en imatge a 200 ppp,
 * que és com el contracte d'Utrecht es llegeix bé) o una foto.
 */
object Document {
    /** Com a molt, les primeres pàgines: els preus hi són sempre al principi. */
    private const val MAX_PAGINES = 4

    fun pagines(context: Context, uri: Uri): List<Bitmap> =
        if (context.contentResolver.getType(uri) == "application/pdf") pdf(context, uri)
        else listOf(Placa.carrega(context.contentResolver, uri))

    private fun pdf(context: Context, uri: Uri): List<Bitmap> {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw ErrorUsuari(R.string.error_pdf)
        return fd.use {
            PdfRenderer(it).use { pdf ->
                (0 until minOf(pdf.pageCount, MAX_PAGINES)).map { i ->
                    pdf.openPage(i).use { pagina ->
                        val escala = 200f / 72
                        val b = Bitmap.createBitmap((pagina.width * escala).toInt(), (pagina.height * escala).toInt(), Bitmap.Config.ARGB_8888)
                        // Les pàgines del PDF són transparents: a sota hi va blanc, o l'OCR veuria el text sobre negre
                        b.eraseColor(Color.WHITE)
                        pagina.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        b
                    }
                }
            }
        }
    }
}
