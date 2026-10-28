package cat.jordi.wakker.dades

import android.content.Context
import androidx.annotation.StringRes
import cat.jordi.wakker.R
import java.io.IOException
import java.util.Locale

/**
 * Un text per a l'usuari que es fa fora de la interfície: el recurs i els valors. Es tradueix quan s'ensenya,
 * a l'idioma de l'app en aquell moment.
 */
class TextRecurs(@StringRes val id: Int, vararg val valors: Any) {
    fun text(context: Context): String = context.getString(id, *valors)
}

/** Un error amb un missatge per a l'usuari, que es tradueix quan s'ensenya. */
class ErrorUsuari(@StringRes id: Int, vararg valors: Any) : IOException() {
    val recurs = TextRecurs(id, *valors)
}

/** El missatge d'un error per ensenyar-lo: traduït si és un [ErrorUsuari]; si no, el que porti l'excepció. */
fun Throwable.missatge(context: Context): String? = if (this is ErrorUsuari) recurs.text(context) else message

/**
 * Com s'escriuen els números, els euros i els dies en la llengua de l'app. Surt de la traducció
 * (`codi_idioma` i `format_euros`), no del mòbil: si el mòbil és en anglès i l'app en català, els decimals
 * van amb coma. Es torna a mirar quan s'obre l'app i quan canvia l'idioma.
 */
object Idioma {
    var locale: Locale = Locale.forLanguageTag("ca-ES")
        private set
    private var formatEuros = "%1\$s €"

    fun actualitza(context: Context) {
        locale = Locale.forLanguageTag(context.getString(R.string.codi_idioma))
        formatEuros = context.getString(R.string.format_euros)
    }

    fun euros(numero: String): String = formatEuros.format(numero)
}
