package cat.jordi.wakker.dades

/** Un moment punta: el minut de més consum d'una estona (W de mitjana) i, si se sap, el pic instantani d'aquell minut. */
data class Pic(val ts: Long, val w: Double, val wMax: Double?)

/** Per sota d'això (W), la línia de la pinça 2 no surt a l'avís de potència. */
object AnalisiPics {
    const val LLINDAR = 400.0
}

/**
 * Una de les coses que gastaven en un pic (maqueta 16): amb el mateix criteri que el rànquing i «Què hi ha encès ara», les
 * vegades que passaven per aquell minut, cadascuna del seu aparell o de cap.
 */
sealed interface ElementPic {
    val w: Double
}

/**
 * Un aparell: des de quan (la seva vegada) i com se sap ([com]: ensenyat o reconegut). [linia]: és a la pinça 2.
 * [compressor]: la nevera trobada sola, sense vegada.
 */
data class AparellPic(
    val aparell: Aparell, override val w: Double, val desDe: Long?, val com: Com?, val linia: Boolean = false, val compressor: Boolean = false,
) : ElementPic

data class SempreEncesPic(override val w: Double) : ElementPic

/** La línia de la pinça 2: sencera si no s'hi ha dit cap aparell, o ([altres]) el que no és cap dels seus aparells. */
data class LiniaPic(override val w: Double, val altres: Boolean = false) : ElementPic

/** Una vegada que l'app no sap de qui és: se'n pot dir què era. [suggerits]: els aparells, del més semblant al que menys. */
data class DesconegutPic(val vegada: Vegada, override val w: Double, val suggerits: List<Aparell>) : ElementPic

/** El que s'acabava d'encendre al minut del pic (250 W o més, encara no era una vegada). */
data class SenseIdentificarPic(override val w: Double) : ElementPic

/** Els petits consums: per sota de 250 W per sobre del sempre encès. */
data class PetitsPic(override val w: Double) : ElementPic

/** Un pic, el que hi havia encès (de més a menys), les vegades que hi passaven i la potència de l'estona d'abans, per a la gràfica. */
data class AnalisiPic(val pic: Pic, val elements: List<ElementPic>, val vegades: List<Vegada>, val finestra: List<Punt>)
