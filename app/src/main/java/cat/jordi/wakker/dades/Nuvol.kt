package cat.jordi.wakker.dades

import kotlin.math.max

/**
 * Els minuts d'una hora fets amb el consum per hores del núvol (maqueta 11): els que no té el Shelly. Si n'hi ha
 * d'exactes, es respecten, i el que queda de l'hora es reparteix a parts iguals pels minuts que falten. [inici]: l'inici
 * de l'hora (s); [wh]: el consum de l'hora segons el núvol; [exactes]: els minuts exactes de l'hora (inici → Wh).
 */
fun minutsDelNuvol(inici: Long, wh: Double, exactes: Map<Long, Double>): List<Pair<Long, Double>> {
    val falten = (0 until 60).map { inici + it * 60L }.filter { it !in exactes }
    if (falten.isEmpty()) return emptyList()
    val resta = max(wh - exactes.values.sum(), 0.0)
    return falten.map { it to resta / falten.size }
}
