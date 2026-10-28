package cat.jordi.wakker.dades

import cat.jordi.wakker.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat

/** El que diu l'etiqueta energètica d'un aparell, tret de l'EPREL (la base de dades europea). */
data class FitxaEprel(
    val numero: String,
    val grup: String,
    val categoria: Categoria,
    val marca: String,
    val model: String,
    val classe: String,
    /** El consum anual que se'n dedueix (kWh/any), o null si l'etiqueta no el permet calcular. */
    val kwhAny: Double?,
    val esperaW: Double?,
    /** Com s'ha calculat el consum anual, per ensenyar-ho. */
    val explicacio: TextRecurs,
) {
    fun comAparell(nom: String) = Aparell(
        nom = nom, categoria = categoria, font = Font.ETIQUETA, kwhAny = kwhAny ?: categoria.kwhAnyTipic,
        esperaW = esperaW, marca = marca, model = model, classe = classe, eprel = numero,
    )
}

/**
 * L'EPREL: el QR de l'etiqueta porta a eprel.ec.europa.eu/qr/<número>, i /api/product/<número>
 * dona la fitxa sense cap clau. (L'API de cerca per marca i model sí que en demana: la cerca es fa a la web,
 * amb `CercaEprel`.)
 */
object Eprel {
    /** Els enllaços amb el número: el QR, la fitxa de la web, la fitxa en PDF i l'etiqueta. */
    private val reEnllac = Regex("""eprel\.ec\.europa\.eu/(?:qr/|screen/product/[^/\s]+/|fiches/[^/\s]+/Fiche_|labels/[^/\s]+/Label_)(\d+)""")
    private val reFitxer = Regex("""(?:Fiche|Label)_(\d+)""")

    /** El número de registre d'un QR escanejat, d'un enllaç, o d'un número escrit a mà. */
    fun numero(text: String): String? {
        enllac(text)?.let { return it }
        val net = text.trim()
        return net.takeIf { it.length in 4..10 && it.all(Char::isDigit) }
    }

    /** El número de registre d'un text que porta un enllaç de l'EPREL. */
    fun enllac(text: String): String? = reEnllac.find(text)?.groupValues?.get(1)

    /** El número d'una fitxa o d'una etiqueta baixades de l'EPREL, pel nom del fitxer (Fiche_1621246_ES.pdf). */
    fun deFitxer(nom: String): String? = reFitxer.find(nom)?.groupValues?.get(1)

    /** On buscar cada tipus d'aparell a la web de l'EPREL, per ordre: primer l'etiqueta d'ara, després l'antiga. */
    fun grups(c: Categoria): List<String> = when (c) {
        Categoria.NEVERA, Categoria.CONGELADOR -> listOf("refrigeratingappliances2019", "refrigeratingappliances")
        Categoria.RENTADORA -> listOf("washingmachines2019", "washerdriers2019", "washingmachines")
        Categoria.RENTAPLATS -> listOf("dishwashers2019", "dishwashers")
        Categoria.ASSECADORA -> listOf("tumbledryers20232534", "tumbledriers")
        Categoria.TELEVISOR -> listOf("electronicdisplays", "televisions")
        Categoria.FORN -> listOf("ovens")
        Categoria.CAMPANA -> listOf("rangehoods")
        Categoria.AIRE -> listOf("airconditioners")
        Categoria.TERMO -> listOf("waterheaters")
        Categoria.BOMBA_CALOR, Categoria.CALDERA -> listOf("spaceheaters")
        Categoria.ESTUFA -> listOf("localspaceheaters")
        Categoria.LLUMS -> listOf("lightsources")
        else -> emptyList()
    }

    fun fitxa(numero: String): FitxaEprel {
        val c = URL("https://eprel.ec.europa.eu/api/product/$numero").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/json")
        val text = try {
            if (c.responseCode == 404) throw ErrorUsuari(R.string.error_eprel_no_hi_es, numero)
            if (c.responseCode !in 200..299) throw ErrorUsuari(R.string.error_eprel_resposta, c.responseCode)
            c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
        return llegeix(numero, JSONObject(text))
    }

    private fun JSONObject.num(vararg claus: String): Double? =
        claus.firstNotNullOfOrNull { k -> if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null }

    private fun categoria(grup: String) = when {
        grup.startsWith("refrigeratingappliances") -> Categoria.NEVERA
        grup.startsWith("washerdriers") -> Categoria.RENTADORA
        grup.startsWith("washingmachines") -> Categoria.RENTADORA
        grup.startsWith("dishwashers") -> Categoria.RENTAPLATS
        grup.startsWith("tumbledr") -> Categoria.ASSECADORA
        grup.startsWith("electronicdisplays") || grup.startsWith("televisions") -> Categoria.TELEVISOR
        grup.startsWith("ovens") -> Categoria.FORN
        grup.startsWith("rangehoods") -> Categoria.CAMPANA
        grup.startsWith("airconditioners") -> Categoria.AIRE
        grup.startsWith("waterheaters") -> Categoria.TERMO
        grup.startsWith("lightsources") -> Categoria.LLUMS
        else -> Categoria.ALTRES
    }

    fun llegeix(numero: String, o: JSONObject): FitxaEprel {
        val grup = o.optString("productGroup")
        val cat = categoria(grup)
        // Els usos anuals són els que fa servir l'etiqueta europea per a cada tipus d'aparell.
        val (kwh, explicacio) = when {
            // Les neveres noves el porten a energyConsAnnualV2 (energyConsAnnual hi és, però buit)
            o.num("energyConsAnnual", "energyConsAnnualV2", "consolidatedEnergyConsAnnual") != null ->
                o.num("energyConsAnnual", "energyConsAnnualV2", "consolidatedEnergyConsAnnual") to TextRecurs(R.string.eprel_consum_anual)
            cat == Categoria.RENTADORA && o.num("energyConsPer100Cycle") != null ->
                o.num("energyConsPer100Cycle")!! * 2.2 to TextRecurs(R.string.eprel_cada_100_rentades, fmt(o.num("energyConsPer100Cycle")!!), 220)
            cat == Categoria.RENTAPLATS && o.num("energyCons100") != null ->
                o.num("energyCons100")!! * 2.8 to TextRecurs(R.string.eprel_cada_100_rentades, fmt(o.num("energyCons100")!!), 280)
            o.num("powerOnModeSDR", "powerOnMode") != null -> {
                val w = o.num("powerOnModeSDR", "powerOnMode")!!
                val hores = if (cat == Categoria.LLUMS) 3 else 4
                w * hores * 365 / 1000 to TextRecurs(R.string.eprel_w_hores, fmt(w), hores)
            }
            else -> null to TextRecurs(R.string.eprel_sense_consum)
        }
        return FitxaEprel(
            numero = numero, grup = grup, categoria = cat,
            marca = o.optString("supplierOrTrademark"), model = o.optString("modelIdentifier"),
            classe = o.optString("energyClass"), kwhAny = kwh,
            esperaW = o.num("powerStandbyMode", "powerStandby", "powerNetworkStandby", "powerOffMode"),
            explicacio = explicacio,
        )
    }

    private fun fmt(x: Double) = NumberFormat.getNumberInstance(Idioma.locale).apply { maximumFractionDigits = 3 }.format(x)
}

/**
 * Les maneres de buscar un model a l'EPREL (maqueta 14), de més a menys exactes: tal com s'ha escrit (sense «E-Nr.»,
 * «Mod.» o «Type» al davant); sense el que la placa hi afegeix al final («3VS6364IA/01» → «3VS6364IA»); i escurçant-lo
 * d'una lletra en una, fins a 5 lletres i com a molt 4 cops. L'EPREL busca els models que contenen el que s'escriu i els
 * espais no hi compten (comprovat el 27/10: «3VS 6364 IA» i «GMJ96» troben; «3VS6364IA/01» no troba res; «3VS636» troba
 * els 5 Balay de la família). [exactes]: quantes de les primeres no estan escurçades.
 */
data class VariantsModel(val models: List<String>, val exactes: Int)

fun variantsModel(escrit: String): VariantsModel {
    val net = escrit.trim().replace(Regex("""\s+"""), " ")
        // Amb un punt, dos punts o un espai darrere: «MODENA123» és un model
        .replace(Regex("""^(E-?\s?NR|MOD(EL|ELO|ÈLE)?|TYP(E)?|PNC|REF)[.:\s]+""", RegexOption.IGNORE_CASE), "")
    if (net.isBlank()) return VariantsModel(emptyList(), 0)
    val sensePlaca = net.substringBefore('/').substringBefore('\\').trim()
    val exactes = listOf(escrit.trim(), net, sensePlaca).filter { it.length >= 3 }.distinct()
    val base = (if (sensePlaca.length >= 3) sensePlaca else net).replace(" ", "")
    val curts = (1..4).map { base.dropLast(it) }.filter { it.length >= 5 }
    val totes = (exactes + curts).distinctBy { it.replace(" ", "").uppercase() }
    return VariantsModel(totes, totes.count { it in exactes })
}
