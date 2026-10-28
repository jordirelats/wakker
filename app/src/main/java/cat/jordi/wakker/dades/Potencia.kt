package cat.jordi.wakker.dades

/** Com d'a prop de la potència contractada: per sota del 80 %, des del 80 % i des del 90 %. */
enum class NivellPotencia { NORMAL, ALT, LIMIT }

fun nivellPotencia(w: Double, limitKw: Double?): NivellPotencia {
    if (limitKw == null || limitKw <= 0) return NivellPotencia.NORMAL
    val part = w / (limitKw * 1000)
    return when {
        part >= 0.9 -> NivellPotencia.LIMIT
        part >= 0.8 -> NivellPotencia.ALT
        else -> NivellPotencia.NORMAL
    }
}

/**
 * Si una lectura passa del 90 % de la potència contractada, si cal avisar-ne: amb l'avís activat, amb l'app
 * tancada (oberta ja ho ensenya la pantalla) i com a molt un cop cada 10 minuts. Desa l'hora de l'avís.
 */
fun calAvisarPotencia(config: Config, w: Double, appOberta: Boolean): Boolean {
    if (!config.avisPotencia || appOberta) return false
    if (nivellPotencia(w, config.tarifa.limitKw) != NivellPotencia.LIMIT) return false
    val ara = System.currentTimeMillis() / 1000
    if (ara - config.darrerAvisPotencia < 10 * 60) return false
    config.darrerAvisPotencia = ara
    return true
}
