package cat.jordi.wakker.dades

import android.content.Context

/** La configuració de l'usuari: on és el Shelly, què mesura la segona pinça, el núvol i el preu. */
class Config(context: Context) {
    private val p = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    private fun text(clau: String) = p.getString(clau, "") ?: ""
    private fun desa(clau: String, valor: String) = p.edit().putString(clau, valor.trim()).apply()

    /** L'adreça del Shelly a la xarxa de casa, amb el port si no és el 80 (per exemple 192.168.1.40). */
    var host: String
        get() = text("host")
        set(v) = desa("host", v)

    /** L'identificador del Shelly al núvol: la MAC en minúscules. */
    var idDispositiu: String
        get() = text("id")
        set(v) = desa("id", v)

    /**
     * La MAC del Shelly de casa, en minúscules (1.1.3). Si a l'adreça de casa hi respon un altre aparell (el router li ha
     * donat l'adreça), no se'n fan servir les dades. Es desa en provar el Shelly a la Connexió; buida, s'aprèn a la primera
     * lectura.
     */
    var macShelly: String
        get() = text("mac_shelly")
        set(v) = desa("mac_shelly", v)

    var model: String
        get() = text("model")
        set(v) = desa("model", v)

    /** El nom de la línia que mesura la segona pinça. Buit vol dir que no n'hi ha. */
    var nomLinia: String
        get() = text("nom_linia")
        set(v) = desa("nom_linia", v)

    var servidorNuvol: String
        get() = text("servidor_nuvol")
        set(v) = desa("servidor_nuvol", v.removePrefix("https://").removeSuffix("/"))

    var clauNuvol: String
        get() = text("clau_nuvol")
        set(v) = desa("clau_nuvol", v)

    /** Com es paga la llum. Les versions 0.1 i 0.2 només desaven un preu: es converteix en una tarifa de preu únic. */
    var tarifa: Tarifa
        get() = synchronized(this) {
            // La pantalla la llegeix a cada recomposició: només es torna a llegir el JSON quan ha canviat
            val text = p.getString("tarifa", null)
            tarifaLlegida?.takeIf { text == tarifaText }
                ?: (Tarifa.de(text) ?: Tarifa(preuFix = p.getFloat("preu", 0.25f).toDouble())).also { tarifaText = text; tarifaLlegida = it }
        }
        set(v) = p.edit().putString("tarifa", v.json()).apply()
    private var tarifaText: String? = null
    private var tarifaLlegida: Tarifa? = null

    /** Totes les preferències, per a la còpia de seguretat. */
    fun totes(): Map<String, Any?> = p.all

    /**
     * Després de tornar a obrir l'app amb una còpia restaurada (1.1.3): la data de la còpia, o "" si no s'ha pogut; null si no
     * n'hi havia cap. Es llegeix un sol cop.
     */
    fun prenCopiaRestaurada(): String? {
        if (!p.contains(Copia.CLAU_RESTAURADA)) return null
        val v = p.getString(Copia.CLAU_RESTAURADA, "") ?: ""
        p.edit().remove(Copia.CLAU_RESTAURADA).apply()
        return v
    }

    /**
     * Els avisos de consum inesperat: si n'hi ha, a partir de quants W sense explicar i durant quants minuts. Fins a la
     * 0.19 eren 300 W durant 10 minuts, fixos, i saltaven massa (desenes de vegades en una setmana).
     */
    var avisInesperat: Boolean
        get() = p.getBoolean("avis_inesperat", true)
        set(v) = p.edit().putBoolean("avis_inesperat", v).apply()
    var llindarInesperat: Double
        get() = p.getFloat("llindar_inesperat", 2000f).toDouble()
        set(v) = p.edit().putFloat("llindar_inesperat", v.toFloat()).apply()
    var minutsInesperat: Int
        get() = p.getInt("minuts_inesperat", 30)
        set(v) = p.edit().putInt("minuts_inesperat", v).apply()

    // L'última vegada que el consum inesperat passava del llindar, i l'últim avís (s)
    var inesperatAlt: Long
        get() = p.getLong("inesperat_alt", 0)
        set(v) = p.edit().putLong("inesperat_alt", v).apply()
    var darrerAvisInesperat: Long
        get() = p.getLong("darrer_avis_inesperat", 0)
        set(v) = p.edit().putLong("darrer_avis_inesperat", v).apply()

    /** Fins on s'ha mirat el consum inesperat (s): el treball de fons continua des d'aquí amb els minuts del Shelly (1.1.3). */
    var inesperatMirat: Long
        get() = p.getLong("inesperat_mirat", 0)
        set(v) = p.edit().putLong("inesperat_mirat", v).apply()

    /**
     * Omplir el que falta amb el consum per hores del núvol (maqueta 11): les absències de més de 7 dies i el d'abans
     * d'instal·lar l'app. Els dies ja mirats (epoch day, separats per comes), perquè no es tornin a demanar, i el primer
     * dia que el núvol ja no té (no cal mirar més enrere).
     */
    var nuvolHores: Boolean
        get() = p.getBoolean("nuvol_hores", true)
        set(v) = p.edit().putBoolean("nuvol_hores", v).apply()
    var nuvolMirats: Set<Long>
        get() = text("nuvol_mirats").split(",").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = desa("nuvol_mirats", v.sorted().joinToString(","))
    var nuvolSenseAbans: Long
        get() = p.getLong("nuvol_sense_abans", Long.MIN_VALUE)
        set(v) = p.edit().putLong("nuvol_sense_abans", v).apply()
    var nuvolDarrer: Long
        get() = p.getLong("nuvol_darrer", 0)
        set(v) = p.edit().putLong("nuvol_darrer", v).apply()

    /** El compressor de la nevera trobat a les dades (W; 0 si no s'ha trobat) i quan s'ha mirat (s). */
    var neveraW: Double
        get() = p.getFloat("nevera_w", 0f).toDouble()
        set(v) = p.edit().putFloat("nevera_w", v.toFloat()).apply()
    var neveraMirada: Long
        get() = p.getLong("nevera_mirada", 0)
        set(v) = p.edit().putLong("nevera_mirada", v).apply()

    /** Quina part del temps funciona el compressor de la nevera (de 0 a 1), mirat a les nits (maqueta 13). */
    var neveraCicle: Double
        get() = p.getFloat("nevera_cicle", 0f).toDouble()
        set(v) = p.edit().putFloat("nevera_cicle", v.toFloat()).apply()

    /** Fins a quin minut s'han buscat les vegades (maqueta 13, s; 0: encara cap). */
    var vegadesFins: Long
        get() = p.getLong("vegades_fins", 0)
        set(v) = p.edit().putLong("vegades_fins", v).apply()

    /**
     * Amb quina manera de trobar-les s'han buscat les vegades desades: si una versió de l'app les troba d'una altra manera
     * (la 0.28 separa el que s'encén a sobre d'una vegada llarga), es tornen a buscar totes.
     */
    var versioVegades: Int
        get() = p.getInt("versio_vegades", 1)
        set(v) = p.edit().putInt("versio_vegades", v).apply()

    /** Les vegades grosses sense nom que l'usuari ha dit que no sap què eren (maqueta 15): l'inici de cadascuna (s). */
    var senseNomIgnorades: Set<Long>
        get() = text("sense_nom_ignorades").split(",").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = desa("sense_nom_ignorades", v.sorted().joinToString(","))

    /**
     * El compte de Tapo dels endolls (maqueta 19): el correu, per ensenyar-lo, i el resum que l'endoll accepta
     * (`sha256(sha1(correu) + sha1(contrasenya))`, en hexadecimal). La contrasenya no es desa mai.
     */
    var tapoCorreu: String
        get() = text("tapo_correu")
        set(v) = desa("tapo_correu", v)
    var tapoResum: String
        get() = text("tapo_resum")
        set(v) = desa("tapo_resum", v)
    val teTapo get() = tapoResum.isNotBlank()

    /** L'última vegada que s'ha llegit l'historial dels endolls (s). */
    var endollsMirat: Long
        get() = p.getLong("endolls_mirat", 0)
        set(v) = p.edit().putLong("endolls_mirat", v).apply()

    /**
     * Com entén l'endoll els timestamps de l'historial: 0, encara no se sap; 1, segons UTC de debò (el crate tapo); 2, l'hora
     * local escrita com si fos UTC (les versions d'abans). L'app ho esbrina comparant les hores d'avui amb el consum d'avui.
     */
    var endollHoraLocal: Int
        get() = p.getInt("endoll_hora_local", 0)
        set(v) = p.edit().putInt("endoll_hora_local", v).apply()

    /** L'últim comptador d'un endoll Shelly (instant i Wh): desat, perquè el treball de fons el pugui continuar en un procés nou. */
    fun comptadorEndoll(id: Long): Pair<Long, Double>? =
        text("endoll_comptador_$id").split(",").takeIf { it.size == 2 }?.let { (t, w) -> t.toLongOrNull()?.let { ts -> w.toDoubleOrNull()?.let { ts to it } } }

    fun posaComptadorEndoll(id: Long, ts: Long, wh: Double) = desa("endoll_comptador_$id", "$ts,$wh")

    /** Els endolls (per MAC) que no saben la potència de cada 5 minuts: no se'ls torna a demanar. */
    var endollsSensePotencia: Set<String>
        get() = text("endolls_sense_potencia").split(",").filter { it.isNotBlank() }.toSet()
        set(v) = desa("endolls_sense_potencia", v.sorted().joinToString(","))

    /** Les propostes de moure un endoll que l'usuari ha dit de deixar com són («endoll:aparell»). */
    var moureIgnorats: Set<String>
        get() = text("moure_ignorats").split(",").filter { it.isNotBlank() }.toSet()
        set(v) = desa("moure_ignorats", v.sorted().joinToString(","))

    /**
     * El resum de la setmana (maqueta 15): si s'avisa els dilluns, i el dilluns (epoch day) de l'última setmana que se n'ha
     * avisat i de l'última que l'usuari ha vist a la pantalla Ara.
     */
    var resumSetmana: Boolean
        get() = p.getBoolean("resum_setmana", true)
        set(v) = p.edit().putBoolean("resum_setmana", v).apply()
    var resumAvisat: Long
        get() = p.getLong("resum_avisat", 0)
        set(v) = p.edit().putLong("resum_avisat", v).apply()
    var resumVist: Long
        get() = p.getLong("resum_vist", 0)
        set(v) = p.edit().putLong("resum_vist", v).apply()

    /** Quan s'han buscat per últim cop els episodis de la línia i el que es repeteix (maqueta 12, s). */
    var liniaMirada: Long
        get() = p.getLong("linia_mirada", 0)
        set(v) = p.edit().putLong("linia_mirada", v).apply()
    var repetitsMirats: Long
        get() = p.getLong("repetits_mirats", 0)
        set(v) = p.edit().putLong("repetits_mirats", v).apply()

    // El consum inesperat que s'està seguint: des de quan, de quants W, i la pregunta que ja se n'ha fet (0 si cap)
    var inesperatDes: Long
        get() = p.getLong("inesperat_des", 0)
        set(v) = p.edit().putLong("inesperat_des", v).apply()
    var inesperatW: Double
        get() = p.getFloat("inesperat_w", 0f).toDouble()
        set(v) = p.edit().putFloat("inesperat_w", v.toFloat()).apply()
    var inesperatPregunta: Long
        get() = p.getLong("inesperat_pregunta", 0)
        set(v) = p.edit().putLong("inesperat_pregunta", v).apply()

    /**
     * L'última lectura dels comptadors d'energia: el minut i els Wh de cada pinça. És el punt de partida del
     * consum aproximat de fora de casa. Es desa com a text perquè un Float perdria els decimals dels Wh.
     */
    var comptador: Comptador?
        get() = text("comptador").split(";").takeIf { it.size == 3 }?.let { (ts, wh, linia) ->
            Comptador(ts.toLongOrNull() ?: return null, wh.toDoubleOrNull() ?: return null, linia.toDoubleOrNull())
        }
        set(v) = desa("comptador", v?.let { "${it.ts};${it.wh};${it.whLinia ?: ""}" } ?: "")

    /** L'avís quan la potència passa del 90 % de la contractada. */
    var avisPotencia: Boolean
        get() = p.getBoolean("avis_potencia", true)
        set(v) = p.edit().putBoolean("avis_potencia", v).apply()

    /** Si l'usuari ha dit «Ara no» a la pregunta de la potència contractada, a la pantalla Ara. */
    var potenciaNoPreguntis: Boolean
        get() = p.getBoolean("potencia_no_preguntis", false)
        set(v) = p.edit().putBoolean("potencia_no_preguntis", v).apply()

    /** Amb l'app tancada: al moment (el servei que vigila cada 10 s) o cada 15 minuts (el treball de fons). */
    var avisAlMoment: Boolean
        get() = p.getBoolean("avis_al_moment", true)
        set(v) = p.edit().putBoolean("avis_al_moment", v).apply()

    /** L'últim avís de potència (s), perquè no se n'enviïn seguits. */
    var darrerAvisPotencia: Long
        get() = p.getLong("darrer_avis_potencia", 0)
        set(v) = p.edit().putLong("darrer_avis_potencia", v).apply()

    /** L'última vegada que s'ha mirat si hi ha una versió nova (ms). */
    var darreraComprovacio: Long
        get() = p.getLong("darrera_comprovacio", 0)
        set(v) = p.edit().putLong("darrera_comprovacio", v).apply()

    /**
     * El resum per hores de les dades velles (maqueta 16): fins on arriba (una mitjanit, s; 0: encara no n'hi ha) i quan s'ha
     * mirat per últim cop. I el primer minut de la pinça 2, que no es mou encara que se n'esborrin els minuts vells.
     */
    var resumFins: Long
        get() = p.getLong("resum_fins", 0)
        set(v) = p.edit().putLong("resum_fins", v).apply()
    var resumMirat: Long
        get() = p.getLong("resum_mirat", 0)
        set(v) = p.edit().putLong("resum_mirat", v).apply()
    var iniciLinia: Long
        get() = p.getLong("inici_pinca2", 0)
        set(v) = p.edit().putLong("inici_pinca2", v).apply()

    /** Clara, fosca o com el mòbil (maqueta 16): [ASPECTE_MOBIL], [ASPECTE_CLAR] o [ASPECTE_FOSC]. */
    var aspecte: Int
        get() = p.getInt("aspecte", ASPECTE_MOBIL)
        set(v) = p.edit().putInt("aspecte", v).apply()

    /** El que ensenya el giny de la pantalla d'inici (maqueta 16): l'última lectura, el consum d'avui i el que més gasta. */
    var giny: EstatGiny?
        get() = if (!p.contains("giny_ts")) null else EstatGiny(
            p.getLong("giny_ts", 0), p.getFloat("giny_w", 0f).toDouble(),
            p.getFloat("giny_kwh", -1f).takeIf { it >= 0 }?.toDouble(), p.getFloat("giny_euros", 0f).toDouble(),
            p.getString("giny_mes_nom", null), p.getFloat("giny_mes_w", -1f).takeIf { it >= 0 }?.toDouble(),
            p.getFloat("giny_preu", -1f).takeIf { it >= 0 }?.toDouble(), p.getString("giny_preu_text", null),
        )
        set(v) {
            if (v == null) return
            p.edit().putLong("giny_ts", v.ts).putFloat("giny_w", v.w.toFloat()).putFloat("giny_kwh", (v.kwh ?: -1.0).toFloat())
                .putFloat("giny_euros", (v.euros ?: 0.0).toFloat()).putString("giny_mes_nom", v.mesNom)
                .putFloat("giny_mes_w", (v.mesW ?: -1.0).toFloat())
                .putFloat("giny_preu", (v.preu ?: -1.0).toFloat()).putString("giny_preu_text", v.preuText).apply()
        }

    /** Configurada vol dir que sap on llegir: el Shelly de casa, el núvol o tots dos. */
    /**
     * Sense Shelly, només amb endolls (maqueta 20): qui encara no té el Shelly (la Mònica, fins al desembre) veu el consum de
     * cada aparell amb endoll. En posar-hi el Shelly (l'adreça o el núvol), el mode s'acaba sol.
     */
    var nomesEndolls: Boolean
        get() = p.getBoolean("nomes_endolls", false)
        set(v) = p.edit().putBoolean("nomes_endolls", v).apply()
    val teShelly get() = host.isNotBlank() || teNuvol
    val modeEndolls get() = nomesEndolls && !teShelly
    val configurat get() = teShelly || nomesEndolls
    val teLinia get() = nomLinia.isNotBlank()
    val teNuvol get() = servidorNuvol.isNotBlank() && clauNuvol.isNotBlank() && idDispositiu.isNotBlank()
}

const val ASPECTE_MOBIL = 0
const val ASPECTE_CLAR = 1
const val ASPECTE_FOSC = 2

/**
 * El que ensenya el giny: la lectura (instant i W), el consum d'avui (kWh, €), el que més gasta ara (nom i W), i el preu de
 * l'hora (€/kWh) amb el seu text («preu únic, 0,207 €/kWh»; 1.1.2).
 */
data class EstatGiny(
    val ts: Long, val w: Double, val kwh: Double?, val euros: Double?, val mesNom: String?, val mesW: Double?,
    val preu: Double? = null, val preuText: String? = null,
)

/** Els comptadors d'energia del Shelly (Wh acumulats) en un minut. */
data class Comptador(val ts: Long, val wh: Double, val whLinia: Double?)
