package cat.jordi.wakker.dades

import androidx.annotation.StringRes
import cat.jordi.wakker.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** Què s'exporta (maqueta 16): el consum de cada hora, cada vegada que una cosa s'ha encès, o cada minut. */
enum class TipusExport(@StringRes val nomId: Int, @StringRes val textId: Int, val fitxer: String) {
    HORES(R.string.exporta_hores, R.string.exporta_hores_text, "hores"),
    VEGADES(R.string.exporta_vegades, R.string.exporta_vegades_text, "vegades"),
    MINUTS(R.string.exporta_minuts, R.string.exporta_minuts_text, "minuts"),
}

/** De quin període: aquest mes, l'any o totes les dades. */
enum class PeriodeExport(@StringRes val nomId: Int) {
    MES(R.string.exporta_mes), ANY(R.string.exporta_any), TOT(R.string.exporta_tot)
}

/**
 * Un fitxer CSV per obrir amb un full de càlcul. Com els fulls de càlcul de Catalunya, Espanya i els Països Baixos: les
 * columnes separades amb punt i coma, i la coma decimal (o el que digui la llengua de l'app), sense separador de milers.
 */
class Csv(private val sortida: Appendable) {
    private val format = DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Idioma.locale)).apply { isGroupingUsed = false }

    fun num(x: Double): String = format.format(x)

    fun fila(vararg camps: Any?) {
        sortida.append(camps.joinToString(";") { c ->
            when (c) {
                null -> ""
                is Double -> num(c)
                // Un text amb punt i coma, cometes o salts de línia, entre cometes
                is String -> if (c.any { it == ';' || it == '"' || it == '\n' }) "\"" + c.replace("\"", "\"\"") + "\"" else c
                else -> c.toString()
            }
        })
        sortida.append("\r\n")
    }
}
