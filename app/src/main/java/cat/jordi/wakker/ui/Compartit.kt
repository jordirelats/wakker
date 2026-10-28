package cat.jordi.wakker.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** El que s'ha compartit amb Wakker des d'una altra app. */
sealed interface Rebut {
    /** Un enllaç de l'EPREL, o una fitxa o una etiqueta baixades de l'EPREL: se'n busca la fitxa (pestanya QR). */
    data class Registre(val numero: String) : Rebut

    /** Una foto o un PDF: es llegeix com la foto de la placa (pestanya Placa). */
    data class Fitxer(val uri: Uri) : Rebut
}

/** On MainActivity deixa el que s'ha compartit, perquè la pantalla Afegir ho reculli. Qui ho llegeix ho buida. */
object Compartit {
    var rebut by mutableStateOf<Rebut?>(null)
}
