package cat.jordi.wakker.ui

import cat.jordi.wakker.dades.Copia
import cat.jordi.wakker.dades.ErrorUsuari
import cat.jordi.wakker.dades.TipusExport
import cat.jordi.wakker.dades.PeriodeExport
import cat.jordi.wakker.dades.Com
import android.net.Uri
import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import cat.jordi.wakker.Actualitzacio
import cat.jordi.wakker.Avisos
import cat.jordi.wakker.WakkerApp
import cat.jordi.wakker.Ginys
import cat.jordi.wakker.R
import cat.jordi.wakker.VersioNova
import cat.jordi.wakker.Vigilancia
import cat.jordi.wakker.dades.AnalisiPic
import cat.jordi.wakker.dades.Aparell
import cat.jordi.wakker.dades.Avui
import cat.jordi.wakker.dades.ConsellLinia
import cat.jordi.wakker.dades.Estona
import cat.jordi.wakker.dades.EstonaSessio
import cat.jordi.wakker.dades.Repositori
import cat.jordi.wakker.dades.InfoPreu
import cat.jordi.wakker.dades.Periode
import cat.jordi.wakker.dades.Pic
import cat.jordi.wakker.dades.Pregunta
import cat.jordi.wakker.dades.Punt
import cat.jordi.wakker.dades.Desfer
import cat.jordi.wakker.dades.Ensenyat
import cat.jordi.wakker.dades.Fitxa
import cat.jordi.wakker.dades.Obertes
import cat.jordi.wakker.dades.ResumSetmana
import cat.jordi.wakker.dades.SenseNom
import cat.jordi.wakker.dades.Referencia
import cat.jordi.wakker.dades.Vegada
import cat.jordi.wakker.dades.Repetit
import cat.jordi.wakker.dades.Resum
import cat.jordi.wakker.dades.Tarifa
import cat.jordi.wakker.dades.TextRecurs
import cat.jordi.wakker.dades.TramPassat
import cat.jordi.wakker.dades.mediana
import cat.jordi.wakker.dades.missatge
import cat.jordi.wakker.dades.mou
import cat.jordi.wakker.dades.iniciPeriode
import cat.jordi.wakker.dades.Endoll
import cat.jordi.wakker.dades.Ranquing
import cat.jordi.wakker.dades.PeriodeEndoll
import cat.jordi.wakker.dades.PropostaMoure
import cat.jordi.wakker.endolls.CredencialsDolentes
import cat.jordi.wakker.endolls.DescobrimentTapo
import cat.jordi.wakker.endolls.EndollTrobat
import cat.jordi.wakker.endolls.ErrorEndoll
import cat.jordi.wakker.endolls.Klap
import cat.jordi.wakker.endolls.LecturaEndoll
import cat.jordi.wakker.endolls.ShellyEndoll
import cat.jordi.wakker.endolls.Tapo
import cat.jordi.wakker.shelly.buscaEndollsShelly
import kotlinx.coroutines.async
import cat.jordi.wakker.shelly.Lectura
import cat.jordi.wakker.shelly.Origen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class Pantalla { CONNECTA, PRINCIPAL, AFEGIR, QUESTIONARI, PREU, PASSAT, APARELL, ESPERA, EDITA, SESSIO, MARCA }

enum class Pestanya(@StringRes val nomId: Int) {
    ARA(R.string.pestanya_ara), APARELLS(R.string.pestanya_aparells), PER_FER(R.string.pestanya_per_fer), HISTORIAL(R.string.pestanya_historial)
}

/** Les seccions de la Configuració (maqueta 18): cadascuna una pantalla, amb el seu títol a dalt. */
enum class SeccioConfig(@StringRes val nomId: Int) {
    CONNEXIO(R.string.config_connexio), PREU(R.string.config_preu), AVISOS(R.string.config_avisos), DADES(R.string.config_dades),
    ASPECTE(R.string.config_aspecte), SOBRE(R.string.config_sobre),
}

class WakkerViewModel(app: Application, private val desat: SavedStateHandle) : AndroidViewModel(app) {
    val repo = (app as WakkerApp).repo
    val config get() = repo.config

    // La pantalla i la pestanya es desen: si Android tanca l'app (per exemple, amb la càmera oberta), hi torna
    private var pantallaEstat by mutableStateOf(
        desat.get<String>("pantalla")?.let { runCatching { Pantalla.valueOf(it) }.getOrNull() }
            ?: if (repo.config.configurat) Pantalla.PRINCIPAL else Pantalla.CONNECTA
    )
    var pantalla: Pantalla
        get() = pantallaEstat
        set(v) {
            pantallaEstat = v
            desat["pantalla"] = v.name
        }
    private var pestanyaEstat by mutableStateOf(desat.get<String>("pestanya")?.let { runCatching { Pestanya.valueOf(it) }.getOrNull() } ?: Pestanya.ARA)
    var pestanya: Pestanya
        get() = pestanyaEstat
        set(v) {
            pestanyaEstat = v
            desat["pestanya"] = v.name
        }

    /** La secció de la Configuració que hi ha oberta (maqueta 18); null, la llista de seccions. */
    private var seccioEstat by mutableStateOf(desat.get<String>("seccio")?.let { runCatching { SeccioConfig.valueOf(it) }.getOrNull() })
    var seccioConfig: SeccioConfig?
        get() = seccioEstat
        set(v) {
            seccioEstat = v
            desat["seccio"] = v?.name
        }

    /** El preu de la llum s'ha obert des de la Configuració: en tornar, s'hi torna (i no a la pantalla principal). */
    var preuDesDeConfig by mutableStateOf(false)

    /** Enrere des d'una pantalla que no és la principal: la secció de la Configuració, el preu obert des d'allà, o el principal. */
    fun enrere() {
        when {
            pantalla == Pantalla.CONNECTA && seccioConfig != null && config.configurat -> seccioConfig = null
            pantalla == Pantalla.PREU && preuDesDeConfig -> { preuDesDeConfig = false; pantalla = Pantalla.CONNECTA }
            pantalla == Pantalla.EDITA -> pantalla = Pantalla.APARELL
            else -> pantalla = Pantalla.PRINCIPAL
        }
    }

    /** Quantes coses hi ha a «Per fer»: el globus de la pestanya. */
    val pendents: Int get() = (senseNom?.vegades?.size ?: 0) + preguntes.size + repetits.size + propostesMoure.size

    /** La lectura en directe; null si ara mateix no s'arriba al Shelly ni pel núvol. */
    var lectura by mutableStateOf<Lectura?>(null)
        private set
    var haLlegit by mutableStateOf(false)
        private set

    var serie by mutableStateOf<List<Punt>>(emptyList())
        private set
    var avuiKwh by mutableStateOf(0.0)
        private set

    /** Si ja s'ha fet el primer refresc: abans, «Avui» diu «—» en lloc de 0,00 kWh (1.1.3). */
    var carregat by mutableStateOf(false)
        private set
    var avuiEuros by mutableStateOf(0.0)
        private set

    /** Una part del consum d'avui s'ha calculat des de fora i és aproximada. */
    var avuiAproximat by mutableStateOf(false)
        private set
    var infoPreu by mutableStateOf<InfoPreu?>(null)
        private set
    var consellLinia by mutableStateOf<ConsellLinia?>(null)
        private set
    var fonsW by mutableStateOf<Double?>(null)
        private set
    var periode by mutableStateOf(Periode.DIA)
        private set

    /**
     * El dia que es mira a l'Historial (maqueta 10): el període és el dia, la setmana, el mes o l'any on cau. Null vol
     * dir avui. Es desa, per si es gira la pantalla, i es manté en canviar de pestanya (de dimecres 21 a la seva setmana).
     */
    private var diaVistaEstat by mutableStateOf(desat.get<Long>("dia_historial")?.let(LocalDate::ofEpochDay))
    var diaVista: LocalDate?
        get() = diaVistaEstat
        private set(v) {
            diaVistaEstat = v
            desat["dia_historial"] = v?.toEpochDay()
        }
    val diaHistorial: LocalDate get() = diaVista ?: LocalDate.now()

    var resum by mutableStateOf<Resum?>(null)
        private set
    var resumMes by mutableStateOf<Resum?>(null)
        private set

    /** Els moments punta del període de l'Historial, amb què hi havia encès, i el que s'ensenya (el primer si no se n'ha triat cap). */
    var pics by mutableStateOf<List<AnalisiPic>>(emptyList())
        private set
    var picTriat by mutableStateOf<Long?>(null)
        private set
    var aparells by mutableStateOf<List<Aparell>>(emptyList())
        private set
    var sincronitzant by mutableStateOf(false)
        private set
    var errorSincronitzacio by mutableStateOf<TextRecurs?>(null)
        private set

    /** La MAC de l'altre aparell que respon a l'adreça del Shelly de casa (1.1.3), o null. */
    var altreShelly by mutableStateOf<String?>(null)
        private set

    // ---------- Entrenament ----------
    var referencies by mutableStateOf<Map<Long, Referencia>>(emptyMap())
        private set
    /** La fitxa d'un aparell (maqueta 13): quin és (desat, per si es gira la pantalla) i què ha gastat. */
    private var aparellObertEstat by mutableStateOf(desat.get<Long>("aparell_obert"))
    var aparellObert: Long?
        get() = aparellObertEstat
        private set(v) {
            aparellObertEstat = v
            desat["aparell_obert"] = v
        }
    var fitxa by mutableStateOf<Fitxa?>(null)
        private set
    var preguntes by mutableStateOf<List<Pregunta>>(emptyList())
        private set
    var aparellsAEntrenar by mutableStateOf<List<Aparell>>(emptyList())
        private set

    /** La línia de la pinça 2 (maqueta 12): els seus aparells, les seves referències i qui l'explica (null sense aparells). */
    var referenciesLinia by mutableStateOf<Map<Long, Referencia>>(emptyMap())
        private set
    var aparellsLinia by mutableStateOf<List<Aparell>>(emptyList())
        private set

    /** El que es repeteix sempre igual i encara no se sap què és (maqueta 12). */
    var repetits by mutableStateOf<List<Repetit>>(emptyList())
        private set

    /** Les vegades que encara duren (maqueta 15): el que hi ha encès ara, amb el mateix criteri que el rànquing. */
    var obertes by mutableStateOf<Obertes?>(null)
        private set

    /** Les vegades grosses dels últims 7 dies que encara no tenen nom (maqueta 15). */
    var senseNom by mutableStateOf<SenseNom?>(null)
        private set

    /** El resum de la setmana passada, mentre l'usuari no l'ha vist (maqueta 15). */
    var resumSetmana by mutableStateOf<ResumSetmana?>(null)
        private set

    // ---------- Els endolls amb mesura (maqueta 19) ----------
    var endolls by mutableStateOf<List<Endoll>>(emptyList())
        private set
    var periodesEndoll by mutableStateOf<List<PeriodeEndoll>>(emptyList())
        private set
    var lecturesEndolls by mutableStateOf<Map<Long, LecturaEndoll>>(emptyMap())
        private set

    /** Només amb endolls (maqueta 20): el que ha gastat avui cada aparell amb endoll. */
    var avuiEndolls by mutableStateOf<Ranquing?>(null)
        private set

    /** Els endolls que ja es poden moure a un altre aparell (Per fer). */
    var propostesMoure by mutableStateOf<List<PropostaMoure>>(emptyList())
        private set

    /** L'endoll que té posat cada aparell ara. */
    val endollDe: Map<Long, Endoll>
        get() {
            val perId = endolls.associateBy { it.id }
            return periodesEndoll.filter { it.finsA == null }.mapNotNull { p -> perId[p.endollId]?.let { p.aparellId to it } }.toMap()
        }

    /** La caça del sempre encès (maqueta 15): el que ha baixat la potència mentre la pantalla és oberta, del més nou al més vell. */
    var baixades by mutableStateOf<List<Baixada>>(emptyList())
        private set
    private var darreraBaixada = 0L

    /** L'aparell que s'està mesurant ara (si n'hi ha) i el missatge del resultat de l'últim. */
    var mesurant by mutableStateOf<Long?>(null)
        private set
    var missatgeEntrena by mutableStateOf<TextRecurs?>(null)
        private set

    /** De quin aparell és el missatge, perquè surti a sota seu (null: és general). */
    var missatgeDe by mutableStateOf<Long?>(null)
        private set

    /** Els aparells que l'usuari ha marcat com a encesos, i des de quan. */
    var encesos by mutableStateOf<Map<Long, Long>>(emptyMap())
        private set

    /** Les lectures dels últims dos minuts (instant, W sense la línia), per mesurar els salts. */
    private val viu = ArrayDeque<Pair<Long, Double>>()

    // ---------- Actualitzacions ----------
    var versioNova by mutableStateOf<VersioNova?>(null)
        private set
    var progresActualitzacio by mutableStateOf<Float?>(null)
        private set
    var missatgeActualitzacio by mutableStateOf<TextRecurs?>(null)
        private set

    private var bucles: Job? = null

    init {
        refresca()
        comprovaVersio(forcat = false)
    }

    /** Mira a GitHub si hi ha una versió nova: cada cop que s'obre l'app, com a molt un cop per hora; amb el botó, sempre. */
    fun comprovaVersio(forcat: Boolean) {
        val ara = System.currentTimeMillis()
        if (!forcat && ara - config.darreraComprovacio < 3_600_000L) return
        if (forcat) missatgeActualitzacio = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Actualitzacio.comprova(getApplication()) } }
            config.darreraComprovacio = ara
            r.onSuccess {
                versioNova = it
                if (forcat && it == null) missatgeActualitzacio = TextRecurs(R.string.vm_ultima_versio)
            }.onFailure {
                if (forcat) missatgeActualitzacio = TextRecurs(R.string.vm_no_he_pogut_mirar, it.missatge(getApplication<Application>()).orEmpty())
            }
        }
    }

    /** Baixa la versió nova i obre l'instal·lador. El primer cop cal permetre que Consum instal·li apps. */
    /** Baixa i instal·la la versió nova. */
    fun actualitza() {
        val v = versioNova ?: return
        val context = getApplication<Application>()
        if (!Actualitzacio.potInstallar(context)) {
            missatgeActualitzacio = TextRecurs(R.string.vm_permet_installar)
            return
        }
        viewModelScope.launch {
            progresActualitzacio = 0f
            val r = withContext(Dispatchers.IO) {
                runCatching { Actualitzacio.baixa(context, v) { p -> viewModelScope.launch { progresActualitzacio = p } } }
            }
            progresActualitzacio = null
            r.onSuccess { apk ->
                // Que l'APK sigui d'aquesta app i amb la mateixa signatura (1.1.3); Android ja rebutja una altra signatura del
                // mateix paquet, però no un altre paquet que es digui igual
                if (!Actualitzacio.esNostre(context, apk)) {
                    apk.delete()
                    missatgeActualitzacio = TextRecurs(R.string.vm_actualitzacio_no_nostra)
                    return@onSuccess
                }
                // Des de segon pla, Android no deixa obrir l'instal·lador: s'obre en tornar a l'app
                if (WakkerApp.oberta) Actualitzacio.installa(context, apk) else apkPendent = apk
            }.onFailure { missatgeActualitzacio = TextRecurs(R.string.vm_no_s_ha_pogut_baixar, it.missatge(context).orEmpty()) }
        }
    }

    /** Un APK baixat amb l'app en segon pla: l'instal·lador s'obre en tornar-hi. */
    private var apkPendent: java.io.File? = null

    // ---------- La còpia de seguretat (1.0.2) ----------
    var copiant by mutableStateOf(false)
        private set
    var missatgeCopia by mutableStateOf<TextRecurs?>(null)
        private set

    /** Desa la còpia (la base de dades i la configuració) al fitxer que ha triat l'usuari. */
    fun desaCopia(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            copiant = true
            missatgeCopia = TextRecurs(R.string.copia_desant)
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    (context.contentResolver.openOutputStream(uri, "wt") ?: throw ErrorUsuari(R.string.exporta_error)).use { repo.copiaSeguretat(context, Actualitzacio.versioActual(context), it) }
                }
            }
            copiant = false
            missatgeCopia = r.fold({ TextRecurs(R.string.copia_desada) }, { TextRecurs(R.string.copia_error, it.missatge(context).orEmpty()) })
        }
    }

    /** Restaura una còpia en lloc del que hi ha, i torna a començar com si s'acabés d'obrir l'app. */
    /**
     * Restaura una còpia (1.1.3): es comprova i es deixa a punt, i l'app es torna a obrir per posar-la en lloc del que hi ha
     * abans d'obrir la base de dades. Si l'usuari ha sortit de l'app mentrestant, es fa en tornar-hi ([enPrimerPla]).
     */
    fun restauraCopia(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            copiant = true
            missatgeCopia = TextRecurs(R.string.copia_restaurant)
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    (context.contentResolver.openInputStream(uri) ?: throw ErrorUsuari(R.string.exporta_error)).use { repo.preparaRestauracio(context, it) }
                }
            }
            copiant = false
            r.onSuccess { if (WakkerApp.oberta) Copia.reinicia(context) }
                .onFailure { missatgeCopia = TextRecurs(R.string.copia_error, it.missatge(context).orEmpty()) }
        }
    }

    /** Només es llegeix en directe mentre l'app és a la pantalla. */
    fun enPrimerPla(si: Boolean) {
        // Una còpia que s'ha acabat de preparar amb l'app en segon pla: ara que torna a ser a la pantalla, es posa
        if (si && Copia.pendent(getApplication())) {
            Copia.reinicia(getApplication())
            return
        }
        bucles?.cancel()
        bucles = null
        WakkerApp.oberta = si
        if (si && (lectura?.ts ?: 0) < System.currentTimeMillis() / 1000 - 60) lectura = null
        // Amb l'app oberta, Android deixa engegar la vigilància; si cal, continuarà amb l'app tancada
        if (si) Vigilancia.actualitza(getApplication())
        if (si) apkPendent?.let { apkPendent = null; if (it.exists()) Actualitzacio.installa(getApplication(), it) }
        if (si) comprovaVersio(forcat = false)
        // El que falta, amb el núvol per hores (maqueta 11), com a molt un cop per hora
        if (si && config.nuvolHores && config.teNuvol && System.currentTimeMillis() / 1000 - config.nuvolDarrer > 3600) omplNuvol()
        if (!si || !config.configurat) return
        bucles = viewModelScope.launch {
            launch {
                var comprovat = 0L
                while (isActive) {
                    val l = repo.lectura()
                    lectura = l
                    altreShelly = repo.altreShelly
                    haLlegit = true
                    if (l != null) {
                        val ara = System.currentTimeMillis()
                        val resta = resta(l)
                        viu.addLast(ara to resta)
                        while (viu.isNotEmpty() && viu.first().first < ara - 120_000) viu.removeFirst()
                        if (pantalla == Pantalla.ESPERA && l.origen == Origen.LOCAL) miraBaixada(ara)
                        // Amb l'app oberta, el consum inesperat es mira cada minut
                        if (ara - comprovat > 60_000) {
                            comprovat = ara
                            comprovaInesperat(resta)
                        }
                    }
                    delay(if (l?.origen == Origen.NUVOL) 3000L else 1000L)
                }
            }
            launch {
                // Cada minut, els minuts nous del Shelly i les vegades que encara duren (per a «Què hi ha encès ara»); cada 5
                // minuts, tot
                var n = 0
                while (isActive) {
                    if (n++ % 5 == 0) sincronitzaAra() else sincronitzaLleuger()
                    delay(60_000L)
                }
            }
            launch {
                // Els endolls amb mesura (maqueta 19), en un bucle a part perquè un que no respon no aturi els watts d'Ara: a
                // casa cada 15 s; des de fora, els Shelly pel núvol cada minut
                var volta = 0
                while (isActive) {
                    val l = lectura
                    if (endolls.isNotEmpty()) {
                        // Només amb endolls (maqueta 20) no hi ha cap Shelly per saber si s'és a casa: es proven, amb el retrocés
                        if (l?.origen == Origen.LOCAL || config.modeEndolls) lecturesEndolls = repo.llegeixEndolls()
                        else if (l?.origen == Origen.NUVOL && volta % 4 == 0) lecturesEndolls = repo.llegeixEndolls(nomesNuvol = true)
                    }
                    volta++
                    delay(15_000L)
                }
            }
        }
    }

    // Hi pot haver dues sincronitzacions alhora (la periòdica i el botó): es compten.
    private var enCurs = 0

    private suspend fun sincronitzaAra() {
        enCurs++
        sincronitzant = true
        // Amb try/finally, perquè la roda de dalt no es quedi girant si la cancel·lació arriba en qualsevol punt (1.1.3)
        try {
            errorSincronitzacio = try {
                repo.sincronitza()
                // L'historial dels endolls (maqueta 19), si no és des de fora
                if (lectura?.origen != Origen.NUVOL) runCatching { repo.sincronitzaEndolls() }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErrorUsuari) {
                e.recurs
            } catch (_: Exception) {
                TextRecurs(R.string.vm_error_sincronitzacio)
            }
            try {
                repo.actualitzaPreus()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            val l = lectura
            withContext(Dispatchers.IO) { runCatching { Ginys.actualitza(getApplication(), repo, l) } }
        } finally {
            enCurs--
            sincronitzant = enCurs > 0
        }
        refrescaAra()
    }

    fun sincronitza() {
        viewModelScope.launch { sincronitzaAra() }
    }

    /** Només els minuts nous i les vegades que encara duren, sense refer tota la resta. */
    private suspend fun sincronitzaLleuger() {
        val l = lectura
        obertes = withContext(Dispatchers.IO) {
            // Des de fora (pel núvol) el Shelly de casa no respon: no cal esperar-lo cada minut
            if (l?.origen != Origen.NUVOL) try {
                repo.sincronitza()
                repo.sincronitzaEndolls()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            // El giny de la pantalla d'inici, cada minut mentre l'app és oberta
            runCatching { Ginys.actualitza(getApplication(), repo, l) }
            // Si la base de dades falla, la pantalla es queda com era en lloc de tancar l'app (1.1.3)
            runCatching { repo.obertes() }.getOrDefault(obertes)
        }
    }

    /** Cada tarifa desada: un refresc començat amb la d'abans no ha d'escriure els seus euros a sobre dels nous. */
    @Volatile
    private var generacioTarifa = 0

    private suspend fun refrescaAra() {
        val p = periode
        val d = diaHistorial
        val generacio = generacioTarifa
        val dades = try { withContext(Dispatchers.IO) {
            // Si s'ha ensenyat, respost o canviat alguna cosa, de qui és cada vegada (maqueta 13)
            repo.reassignaVegades()
            Dades(
                repo.analitzaPics(repo.pics(p, dia = d)),
                repo.serie24h(), repo.avui(), repo.fonsW(), repo.resum(p, d), repo.resumMes(), repo.aparells(),
                repo.infoPreu(), repo.consellLinia(),
                repo.referencies(), repo.preguntesPendents(), repo.aparellsAEntrenar(),
                repo.referenciesLinia(), repo.repetitsPendents(),
                repo.obertes(), repo.senseNom(),
                resumDeLaSetmana(),
                repo.aparellsLinia(),
                repo.endolls(), repo.periodesEndoll(), repo.propostesMoure(),
                if (config.modeEndolls) repo.resum(Periode.DIA).ranquing else null,
            )
        } } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Una base de dades que falla no ha de tancar l'app cada cop que s'obre: es diu i prou (1.1.3)
            errorSincronitzacio = TextRecurs(R.string.vm_error_sincronitzacio)
            return
        }
        if (generacio != generacioTarifa) return
        carregat = true
        serie = dades.serie
        avuiKwh = dades.avui.kwh
        avuiEuros = dades.avui.euros
        avuiAproximat = dades.avui.aproximat
        infoPreu = dades.preu
        consellLinia = dades.consell
        referencies = dades.referencies
        preguntes = dades.preguntes
        aparellsAEntrenar = dades.aEntrenar
        referenciesLinia = dades.referenciesLinia
        repetits = dades.repetits
        obertes = dades.obertes
        senseNom = dades.senseNom
        resumSetmana = dades.resumSetmana
        aparellsLinia = dades.aparellsLinia
        endolls = dades.endolls
        periodesEndoll = dades.periodesEndoll
        propostesMoure = dades.propostesMoure
        avuiEndolls = dades.avuiEndolls
        lecturesEndolls = repo.lecturesEndolls
        fonsW = dades.fons
        resumMes = dades.mes
        aparells = dades.aparells
        llegeixSessio()
        if (p == periode && d == diaHistorial) {
            resum = dades.resum
            pics = dades.pics
        }
        if (pantalla == Pantalla.APARELL) aparellObert?.let { id ->
            val f = withContext(Dispatchers.IO) { repo.fitxa(id) }
            if (aparellObert == id) fitxa = f
        }
    }

    /** Obre la fitxa d'un aparell. */
    fun obreAparell(a: Aparell) {
        aparellObert = a.id
        fitxa = null
        pantalla = Pantalla.APARELL
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) { repo.fitxa(a.id) }
            // Si mentrestant se n'ha obert un altre, aquesta ja no és la seva
            if (aparellObert == a.id) fitxa = f
        }
    }

    /** S'ha posat o tret la pinça 2: les vegades es tornaran a buscar de cap a peus. */
    fun tornaABuscarVegades() = repo.tornaABuscarVegades()

    /** «No era ell»: aquesta vegada no era d'aquest aparell. */
    fun noEraEll(v: Vegada) {
        val nom = aparells.firstOrNull { it.id == v.aparellId }?.nom.orEmpty()
        desa(TextRecurs(R.string.desfer_no_era, nom)) { repo.noEraEll(v) }
    }

    // ---------- Desfer (maqueta 17) ----------

    /** L'última resposta o entrenament, que encara es pot desfer uns segons. */
    var desfer by mutableStateOf<AvisDesfer?>(null)
        private set

    /** Una resposta s'està desant: un segon toc no en fa una altra (ni un «Desfés» que no desfaria la primera). */
    private var desant = false

    /**
     * Desa (al fil de fons) i, si ha anat bé, ofereix desfer-ho de seguida, abans de refrescar la pantalla (que pot trigar).
     * [desfetALaPantalla]: el que cal tornar com era a la pantalla si es desfà.
     */
    private fun desa(text: TextRecurs, desfetALaPantalla: (() -> Unit)? = null, accio: () -> Desfer?) {
        if (desant) return
        desant = true
        viewModelScope.launch {
            try {
                val d = withContext(Dispatchers.IO) { accio() }
                if (d != null) desfer = AvisDesfer(text, d, desfetALaPantalla)
            } finally {
                desant = false
            }
            refrescaAra()
        }
    }

    /**
     * Respon amb un aparell que ja hi és ([aparellId]) o amb un de nou ([nou], des de «Tots els aparells…»): primer es crea,
     * i desfer-ho també el treu.
     */
    private fun respon(
        aparellId: Long?, nou: Aparell?, text: (String) -> TextRecurs, fet: ((Long) -> Unit)? = null, desfetALaPantalla: (() -> Unit)? = null,
        accio: (Long) -> Desfer?,
    ) {
        if (desant) return
        desant = true
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    val id = aparellId ?: nou?.let { repo.afegeixAparell(it) } ?: return@withContext null
                    val d = accio(id)
                    if (d == null) {
                        if (nou != null) repo.desfes(Desfer(aparellNou = id))
                        return@withContext null
                    }
                    val nom = repo.aparells().firstOrNull { it.id == id }?.nom.orEmpty()
                    Triple(id, if (nou != null) TextRecurs(R.string.desfer_nou, nom) else text(nom), if (nou != null) d.copy(aparellNou = id) else d)
                }
                r?.let { (id, t, d) ->
                    fet?.invoke(id)
                    desfer = AvisDesfer(t, d, desfetALaPantalla)
                }
            } finally {
                desant = false
            }
            refrescaAra()
        }
    }

    /** «Desfés» de l'avís [d]: si mentrestant n'ha arribat un altre, aquest ja no es desfà. */
    fun desfes(d: AvisDesfer) {
        if (desfer !== d) return
        desfer = null
        d.desfetALaPantalla?.invoke()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.desfes(d.accio) }
            refrescaAra()
        }
    }

    /** L'avís de desfer ja ha passat (o se n'ha fet un altre). */
    fun oblidaDesfer(d: AvisDesfer) {
        if (desfer === d) desfer = null
    }

    /** «Treu-ho»: una cosa ensenyada a un aparell, des de la fitxa. */
    fun treuEnsenyat(e: Ensenyat) {
        val nom = aparells.firstOrNull { a -> (e.mostres.firstOrNull()?.aparellId ?: e.etiquetes.firstOrNull()?.aparellId) == a.id }?.nom.orEmpty()
        desa(TextRecurs(R.string.desfer_tret, nom)) { repo.treuEnsenyat(e) }
    }

    private class Dades(
        val pics: List<AnalisiPic>,
        val serie: List<Punt>, val avui: Avui, val fons: Double?, val resum: Resum, val mes: Resum,
        val aparells: List<Aparell>, val preu: InfoPreu, val consell: ConsellLinia?,
        val referencies: Map<Long, Referencia>, val preguntes: List<Pregunta>,
        val aEntrenar: List<Aparell>,
        val referenciesLinia: Map<Long, Referencia>, val repetits: List<Repetit>,
        val obertes: Obertes, val senseNom: SenseNom, val resumSetmana: ResumSetmana?, val aparellsLinia: List<Aparell>,
        val endolls: List<Endoll>, val periodesEndoll: List<PeriodeEndoll>, val propostesMoure: List<PropostaMoure>,
        val avuiEndolls: Ranquing?,
    )

    fun refresca() {
        viewModelScope.launch { refrescaAra() }
    }

    fun triaPeriode(p: Periode) {
        periode = p
        picTriat = null
        // Mentre es calcula, sense les barres ni el percentatge del període d'abans (1.1.3)
        resum = null
        pics = emptyList()
        refresca()
    }

    /** El període d'abans o el de després a l'Historial ([n] = -1 o 1); el d'ara, o el que conté [dia]. */
    fun mouHistorial(n: Long) = veesA(mou(periode, diaHistorial, n))

    fun tornaAvui() = veesA(null)

    fun veesA(dia: LocalDate?) {
        val avui = LocalDate.now()
        // No es pot anar més enllà d'avui
        diaVista = dia?.takeIf { it.isBefore(avui) }
        picTriat = null
        resum = null
        refresca()
    }

    fun triaPic(p: Pic) {
        picTriat = p.ts
    }

    fun desaAparells(llista: List<Aparell>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { llista.forEach(repo::desaAparell) }
            refrescaAra()
        }
    }

    /**
     * Posa un aparell a la línia de la pinça 2, o el treu (maqueta 12). Tot seguit l'app n'aprèn el que ja hi ha a la
     * línia, i torna a mirar què es repeteix a la resta de la casa, que ja no el té.
     */
    fun posaALinia(a: Aparell, si: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.posaALinia(a, si)
                repo.aprenLinia(forca = true)
                repo.actualitzaRepetits(forca = true)
            }
            refrescaAra()
        }
    }

    /** Què és una cosa que es repeteix ([aparellId] null: «No ho sé»). [sol]: s'engega sola. */
    fun responRepetit(r: Repetit, aparellId: Long?, sol: Boolean, nou: Aparell? = null) {
        if (aparellId == null && nou == null) {
            desa(TextRecurs(R.string.desfer_no_preguntis)) { repo.responRepetit(r.id, null, false) }
            return
        }
        respon(aparellId, nou, { TextRecurs(R.string.desfer_era, it) }) { id -> repo.responRepetit(r.id, id, sol) }
    }

    /** De qui és una vegada grossa sense nom (maqueta 15). */
    fun responVegada(v: Vegada, aparellId: Long?, nou: Aparell? = null) =
        respon(aparellId, nou, { TextRecurs(R.string.desfer_era, it) }) { id -> repo.responVegada(v, id) }

    /** «No ho sé»: aquesta vegada ja no es pregunta. */
    fun ignoraVegada(v: Vegada) = desa(TextRecurs(R.string.desfer_no_preguntis)) { repo.ignoraVegada(v) }

    @Volatile
    private var resumCalculat = 0L

    /** La setmana passada, mentre no s'ha vist: com a molt un cop cada mitja hora (és una setmana sencera de dades). */
    private fun resumDeLaSetmana(): ResumSetmana? {
        val dilluns = iniciPeriode(Periode.SETMANA, LocalDate.now()).minusWeeks(1)
        if (dilluns.toEpochDay() <= config.resumVist) return null
        val ara = System.currentTimeMillis()
        if (resumSetmana?.dilluns == dilluns && ara - resumCalculat < 30 * 60_000L) return resumSetmana
        resumCalculat = ara
        return repo.resumSetmana()
    }

    /** El resum de la setmana ja s'ha vist: no torna a sortir fins a la setmana que ve. */
    fun tancaResum() {
        resumSetmana?.let { config.resumVist = it.dilluns.toEpochDay() }
        resumSetmana = null
    }

    /** Del resum de la setmana a l'Historial d'aquella setmana. */
    fun miraSetmana(dilluns: LocalDate) {
        periode = Periode.SETMANA
        pestanya = Pestanya.HISTORIAL
        veesA(dilluns)
    }

    /** El que ha costat de mitjana cada kWh aquest mes (o el preu d'ara), per passar W a euros l'any. */
    val preuMitja: Double? get() = resumMes?.preuMitja ?: infoPreu?.ara?.preu

    /** Obre la caça del sempre encès, amb la llista de baixades buida. */
    fun obreEspera() {
        baixades = emptyList()
        pantalla = Pantalla.ESPERA
    }

    /**
     * Si la potència acaba de baixar (la mediana dels últims 8 s, 4 W o més per sota de la de fa 13 a 25 s), una baixada nova:
     * l'usuari hi pot dir què ha apagat. La del compressor de la nevera (±30 % de la seva potència) es diu sola.
     */
    private fun miraBaixada(ara: Long) {
        if (ara - darreraBaixada < 20_000) return
        val abans = medianaDes(ara - 25_000, ara - 13_000) ?: return
        val ara8 = medianaDes(ara - 8_000) ?: return
        val dw = abans - ara8
        if (dw < 4) return
        darreraBaixada = ara
        val nevera = obertes?.neveraW?.takeIf { dw in it * 0.7..it * 1.3 }?.let { obertes?.nevera }
        baixades = (listOf(Baixada(ara / 1000, dw, nevera?.id, nevera != null)) + baixades).take(12)
    }

    /** Què s'ha apagat en una baixada ([aparellId] null: «No ho sé»). */
    fun responBaixada(b: Baixada, aparellId: Long?, nou: Aparell? = null) {
        if (aparellId == null && nou == null) {
            baixades = baixades.map { if (it.ts == b.ts) it.copy(respost = true) else it }
            return
        }
        // Les baixades es toquen aquí, al fil de la pantalla (la caça hi afegeix les noves): en desar, i en desfer-ho
        respon(
            aparellId, nou, { TextRecurs(R.string.desfer_espera, it, num(b.w)) },
            fet = { id -> baixades = baixades.map { if (it.ts == b.ts) it.copy(aparellId = id, respost = true) else it } },
            desfetALaPantalla = { baixades = baixades.map { if (it.ts == b.ts) it.copy(aparellId = null, respost = false) else it } },
        ) { id -> repo.desaEspera(id, b.w) }
    }

    /** Clara, fosca o com el mòbil (maqueta 16). */
    var aspecte by mutableStateOf(config.aspecte)
        private set

    fun triaAspecte(a: Int) {
        config.aspecte = a
        aspecte = a
    }

    /** Exportar les dades (maqueta 16): si s'està escrivint el fitxer, i com ha anat. */
    var exportant by mutableStateOf(false)
        private set
    var missatgeExport by mutableStateOf<TextRecurs?>(null)
        private set

    /** Escriu el CSV al fitxer que l'usuari ha triat (Baixades, Drive…). */
    fun exporta(tipus: TipusExport, periode: PeriodeExport, uri: Uri, capcalera: List<String>, textCom: Map<Com, String>) {
        viewModelScope.launch {
            exportant = true
            missatgeExport = null
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { repo.exporta(tipus, periode, it, capcalera, textCom) }
                        ?: error("no s'ha pogut obrir")
                }
            }
            exportant = false
            missatgeExport = r.fold({ TextRecurs(R.string.exporta_fet, it) }, { TextRecurs(R.string.exporta_error) })
        }
    }

    /** Si ja n'hi ha un amb el mateix nom que encara té el valor típic (del qüestionari), el substitueix. */
    /** El nom de l'últim aparell afegit: Aparells obre la llista on ha anat a parar (1.1.3). */
    var darrerAfegit by mutableStateOf<String?>(null)
        private set

    fun afegeixAparell(a: Aparell) {
        darrerAfegit = a.nom
        val existent = aparells.firstOrNull { it.perCompletar && it.nom.equals(a.nom, ignoreCase = true) }
        desaAparells(listOf(a.copy(id = existent?.id ?: 0)))
    }

    /** Quins avisos hauria donat la setmana passada amb l'ajust de la Configuració. Null mentre es calcula o sense dades. */
    var previsioAvisos by mutableStateOf<List<Pair<Long, Double>>?>(null)
        private set
    var previsioSenseDades by mutableStateOf(false)
        private set
    private var feinaPrevisio: Job? = null

    /** L'estona passada que s'ha buscat per ensenyar-hi un aparell (maqueta 8), i si s'està buscant. */
    var estona by mutableStateOf<Estona?>(null)
        private set
    var buscantEstona by mutableStateOf(false)
        private set

    fun buscaEstona(desDe: Long, finsA: Long) {
        viewModelScope.launch {
            buscantEstona = true
            // Si és a casa, primer recull els minuts que encara no té (l'estona pot ser de fa poc)
            estona = withContext(Dispatchers.IO) {
                try {
                    repo.sincronitza()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                repo.estona(desDe, finsA)
            }
            buscantEstona = false
        }
    }

    fun oblidaEstona() {
        estona = null
    }

    /** Desa els trams marcats com a mostres de l'aparell i torna a Entrena, amb el que n'ha après. */
    fun ensenyaEstona(aparell: Aparell, trams: List<TramPassat>) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { repo.ensenyaEstona(aparell.id, trams) }
            d?.let { desfer = AvisDesfer(TextRecurs(R.string.desfer_estona, aparell.nom), it) }
            estona = null
            refrescaAra()
            // Sense missatge a «En directe» (plegat, no es veia i sortia dies després): ja hi ha l'avís amb «Desfés» (1.1.3)
            missatgeDe = null
            missatgeEntrena = null
            pantalla = Pantalla.PRINCIPAL
            pestanya = Pestanya.PER_FER
        }
    }

    // ---------- Ensenyar d'inici a fi i què hi ha encès (maqueta 21) ----------

    /** L'aparell que s'està ensenyant d'inici a fi: des de quan, fins a quan (null: encara és engegat) i la base de la casa. */
    data class SessioEnMarxa(val aparell: Aparell, val desDe: Long, val finsA: Long?, val base: Double?)

    var sessio by mutableStateOf<SessioEnMarxa?>(null)
        private set

    init {
        viewModelScope.launch {
            llegeixSessio(withContext(Dispatchers.IO) { repo.aparells() })
            // Si s'està ensenyant un aparell d'inici a fi, la notificació hi ha de ser (Android 14 la deixa treure)
            sessio?.takeIf { it.finsA == null }?.let { Avisos.avisaSessio(getApplication(), it.aparell.nom, it.desDe) }
        }
    }

    private fun llegeixSessio(llista: List<Aparell> = aparells) {
        val c = config
        sessio = llista.firstOrNull { it.id == c.sessioAparell }?.let {
            SessioEnMarxa(it, c.sessioInici, c.sessioFi.takeIf { f -> f > 0 }, c.sessioBase.takeIf { b -> b >= 0 })
        }
    }

    /**
     * «Ara l'engego» ([desDe] null) o «Ja està engegat» des de [desDe]. Primer es recullen els minuts d'abans (si és a casa),
     * per saber la base de la casa; si encara no hi són, la de les lectures dels últims segons.
     */
    /** S'està començant una estona: un segon toc no en comença una altra a sobre. */
    var comencant by mutableStateOf(false)
        private set

    fun comencaSessio(a: Aparell, desDe: Long? = null) {
        if (comencant || sessio != null) return
        comencant = true
        val ara = System.currentTimeMillis()
        val viuAbans = if (desDe == null) medianaDes(ara - 15_000) else null
        val liniaAra = lectura?.linia?.w
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (lectura?.origen != Origen.NUVOL) runCatching { repo.sincronitza() }
                    // Un aparell nou, des de «Tots els aparells…» → «Afegeix-ne un de nou»
                    val id = if (a.id == 0L) repo.afegeixAparell(a) else a.id
                    repo.comencaSessio(id, desDe ?: (ara / 1000))
                    if (config.sessioBase < 0) {
                        val linia = config.teLinia && a.linia && !a.nom.equals(config.nomLinia, ignoreCase = true)
                        (if (linia) liniaAra else viuAbans)?.let { config.sessioBase = it }
                    }
                }
                refrescaAra()
                llegeixSessio()
            } finally {
                comencant = false
            }
            sessio?.let { Avisos.avisaSessio(getApplication(), it.aparell.nom, it.desDe) }
        }
    }

    /** El que l'aparell que s'ensenya posa ara per sobre de la base de la casa (W), si se sap. */
    fun wSessio(l: Lectura?): Double? {
        val s = sessio ?: return null
        val base = s.base ?: return null
        l ?: return null
        val linia = config.teLinia && s.aparell.linia && !s.aparell.nom.equals(config.nomLinia, ignoreCase = true)
        return ((if (linia) l.linia?.w ?: 0.0 else resta(l)) - base).coerceAtLeast(0.0)
    }

    /** L'estona acabada que es revisa abans de desar-la, i si s'està buscant. */
    var estonaSessio by mutableStateOf<EstonaSessio?>(null)
        private set
    var buscantSessio by mutableStateOf(false)
        private set

    /** «L'he apagat» (des de l'app o la notificació): l'estona acaba ara, i s'obre per revisar-la. */
    fun acabaSessio() {
        repo.acabaSessio()
        Avisos.treuSessio(getApplication())
        viewModelScope.launch {
            llegeixSessio(withContext(Dispatchers.IO) { repo.aparells() })
            if (sessio != null) obreSessio()
        }
    }

    /** Obre l'estona acabada: primer es recullen els minuts que falten (si és a casa). */
    fun obreSessio() {
        pantalla = Pantalla.SESSIO
        viewModelScope.launch {
            buscantSessio = true
            estonaSessio = withContext(Dispatchers.IO) {
                if (lectura?.origen != Origen.NUVOL) runCatching { repo.sincronitza() }
                runCatching { repo.estonaSessio() }.getOrNull()
            }
            buscantSessio = false
        }
    }

    /** Desa l'estona, amb les vegades de [fora] fora (no eren seves), i torna a «Per fer». */
    fun desaSessio(e: EstonaSessio, fora: List<Vegada>) {
        // Si se n'està desant una altra cosa, aquest toc no faria res: que no se'n vagi de la pantalla
        if (desant) return
        val nom = aparells.firstOrNull { it.id == e.aparellId }?.nom.orEmpty()
        desa(TextRecurs(R.string.desfer_sessio, nom)) { repo.desaSessio(e, fora) }
        estonaSessio = null
        sessio = null
        pantalla = Pantalla.PRINCIPAL
        pestanya = Pestanya.PER_FER
    }

    /** «Cancel·la» o «Descarta»: fora l'estona, sense desar-ne res. */
    fun oblidaSessio() {
        repo.oblidaSessio()
        Avisos.treuSessio(getApplication())
        estonaSessio = null
        sessio = null
        if (pantalla == Pantalla.SESSIO) {
            pantalla = Pantalla.PRINCIPAL
            pestanya = Pestanya.PER_FER
        }
    }

    /** El que ha sortit de marcar què hi ha encès (null: encara no s'ha desat); [marcaFora]: no és a casa, no es pot. */
    var resultatMarca by mutableStateOf<List<Repositori.FilaMarca>?>(null)
        private set
    var marcaFora by mutableStateOf(false)
        private set
    var marcant by mutableStateOf(false)
        private set

    fun obreMarca() {
        resultatMarca = null
        marcaFora = false
        pantalla = Pantalla.MARCA
    }

    /** «Què hi ha encès ara?»: compara els aparells marcats amb el que veu Wakker, i aprèn el que no tenia nom. */
    fun marcaEncesos(ids: Set<Long>) {
        val l = lectura ?: run { marcaFora = true; return }
        if (marcant) return
        marcant = true
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    if (l.origen != Origen.NUVOL) runCatching { repo.sincronitza() }
                    repo.marcaEncesos(ids, l)
                }
                if (r == null) {
                    marcaFora = true
                } else {
                    resultatMarca = r.first
                    r.second?.let { desfer = AvisDesfer(TextRecurs(R.string.desfer_marca), it) }
                }
            } finally {
                marcant = false
            }
            refrescaAra()
        }
    }

    /** Desa els canvis d'un aparell (nom, tipus, marca, model, potència, l'any, com funciona, on està connectat). */
    fun editaAparell(abans: Aparell, ara: Aparell) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Posar-lo o treure'l de la pinça 2 també treu el que n'havia après de la línia
                if (abans.linia != ara.linia) repo.posaALinia(abans, ara.linia)
                repo.desaAparell(ara)
                if (abans.linia != ara.linia) {
                    repo.aprenLinia(forca = true)
                    repo.actualitzaRepetits(forca = true)
                }
            }
            pantalla = Pantalla.APARELL
            refrescaAra()
        }
    }

    /** Les dades per hores del núvol (maqueta 11): si s'està omplint, i què se n'ha recuperat (dies, kWh, des de quan). */
    var omplintNuvol by mutableStateOf(false)
        private set
    var resumNuvol by mutableStateOf<Triple<Int, Double, Long?>?>(null)
        private set

    fun omplNuvol() {
        if (omplintNuvol) return
        viewModelScope.launch {
            omplintNuvol = true
            try {
                withContext(Dispatchers.IO) { runCatching { repo.omplNuvol() } }
                resumNuvol = withContext(Dispatchers.IO) { repo.resumNuvol() }
                refrescaAra()
            } finally {
                omplintNuvol = false
            }
        }
    }

    fun miraNuvol() {
        viewModelScope.launch { resumNuvol = withContext(Dispatchers.IO) { repo.resumNuvol() } }
    }

    /** Encén o apaga l'opció; apagada, fora les dades del núvol per hores. */
    fun ajustaNuvol(actiu: Boolean) {
        config.nuvolHores = actiu
        if (actiu) {
            omplNuvol()
        } else {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { repo.oblidaNuvol() }
                resumNuvol = Triple(0, 0.0, null)
                refrescaAra()
            }
        }
    }

    /** Desa l'ajust dels avisos de consum inesperat i torna a comptar quants n'hauria donat. */
    fun ajustaInesperat(actiu: Boolean, llindarW: Double, minuts: Int) {
        config.avisInesperat = actiu
        config.llindarInesperat = llindarW
        config.minutsInesperat = minuts
        feinaPrevisio?.cancel()
        previsioAvisos = null
        if (!actiu) return
        feinaPrevisio = viewModelScope.launch {
            val r = withContext(Dispatchers.Default) { repo.simulaAvisos(llindarW, minuts) }
            previsioSenseDades = r == null
            previsioAvisos = r
        }
    }

    fun esborraAparell(id: Long) {
        if (pantalla == Pantalla.APARELL) pantalla = Pantalla.PRINCIPAL
        // Si l'estava ensenyant d'inici a fi, la notificació també se'n va
        if (sessio?.aparell?.id == id) {
            Avisos.treuSessio(getApplication())
            sessio = null
        }
        // El que es podia desfer d'abans ja no té sentit: tornaria a posar coses d'un aparell que no hi és
        desfer = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.esborraAparell(id) }
            refrescaAra()
        }
    }

    /** El consum sense la línia de la segona pinça, que ja es mesura a part. */
    fun resta(l: Lectura) = (l.total.w - if (config.teLinia) (l.linia?.w ?: 0.0).coerceAtLeast(0.0) else 0.0).coerceAtLeast(0.0)

    private suspend fun comprovaInesperat(resta: Double) {
        val p = withContext(Dispatchers.IO) { repo.comprovaInesperat(resta) } ?: return
        Avisos.avisa(getApplication(), p, aparellsAEntrenar, referencies)
        refrescaAra()
    }

    private fun medianaDes(desDe: Long, finsA: Long = Long.MAX_VALUE) =
        viu.filter { it.first in desDe..finsA }.map { it.second }.takeIf { it.size >= 2 }?.let(::mediana)

    /**
     * L'usuari diu que acaba d'encendre (o apagar) un aparell. Es compara la potència dels 10 segons d'abans
     * amb la dels 8 segons després d'esperar-ne 20, perquè l'aparell arribi al seu règim. El salt és una mostra.
     */
    fun ensenya(aparell: Aparell, encen: Boolean) {
        if (mesurant != null) return
        val ara = System.currentTimeMillis()
        val abans = medianaDes(ara - 10_000)
        missatgeDe = aparell.id
        if (abans == null) {
            missatgeEntrena = TextRecurs(R.string.vm_entrena_poques_lectures)
            return
        }
        mesurant = aparell.id
        missatgeEntrena = null
        viewModelScope.launch {
            delay(28_000)
            val despres = medianaDes(System.currentTimeMillis() - 8_000)
            val salt = if (despres == null) null else if (encen) despres - abans else abans - despres
            missatgeEntrena = when {
                salt == null -> TextRecurs(R.string.vm_entrena_no_arribo)
                salt < 15 -> TextRecurs(R.string.vm_entrena_cap_canvi, num(salt))
                else -> {
                    // Si es va encendre fa més d'un dia, segurament no es va marcar quan es va apagar: no se'n sap la durada
                    val durada = if (encen) null else encesos[aparell.id]?.let { (ara - it) / 1000 }?.takeIf { it <= 24 * 3600 }
                    val d = withContext(Dispatchers.IO) { repo.desaMostra(aparell.id, salt, durada) }
                    // Desfer-ho torna l'aparell a com estava a Entrena (encès o apagat)
                    val abans = encesos
                    desfer = AvisDesfer(TextRecurs(R.string.desfer_directe, aparell.nom, num(salt)), d) { encesos = abans }
                    // Només canvia d'encès a apagat (o al revés) si s'ha vist el salt
                    encesos = if (encen) encesos + (aparell.id to ara) else encesos - aparell.id
                    val n = (referencies[aparell.id]?.mostres ?: 0) + 1
                    if (n > Referencia.PER_APRENDRE) {
                        TextRecurs(if (encen) R.string.vm_entrena_desat_encen_apres else R.string.vm_entrena_desat_apaga_apres, aparell.nom, num(salt), n)
                    } else {
                        TextRecurs(
                            if (encen) R.string.vm_entrena_desat_encen else R.string.vm_entrena_desat_apaga,
                            aparell.nom, num(salt), n, Referencia.PER_APRENDRE,
                        )
                    }
                }
            }
            mesurant = null
            refrescaAra()
        }
    }

    /** [sol]: l'usuari diu que l'aparell s'engega sol; se n'aprèn l'interval i no se n'avisa més. */
    fun respon(pregunta: Pregunta, aparellId: Long?, sol: Boolean = false, nou: Aparell? = null) {
        // Si la pregunta també és a les notificacions, ja no cal
        NotificationManagerCompat.from(getApplication<Application>()).cancel(pregunta.id.toInt())
        if (aparellId == null && nou == null) {
            desa(TextRecurs(R.string.desfer_no_preguntis)) { repo.respon(pregunta.id, null) }
            return
        }
        respon(aparellId, nou, { TextRecurs(R.string.desfer_era, it) }) { id -> repo.respon(pregunta.id, id, sol) }
    }

    fun tornaAEntrenar(aparellId: Long) {
        desfer = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.tornaAEntrenar(aparellId) }
            refrescaAra()
        }
    }

    /** La potència contractada, des de la pregunta de la pantalla Ara. */
    /** La potència contractada (null per treure-la): la tarifa, la vigilància, i els euros de nou (a Espanya és el terme fix). */
    fun desaPotencia(kw: Double?) {
        config.tarifa = config.tarifa.ambLimit(kw)
        Vigilancia.actualitza(getApplication())
        refresca()
    }

    /** Mentre es tornen a calcular els euros amb una tarifa nova (la roda de dalt). */
    var recalculant by mutableStateOf(false)
        private set

    /**
     * Amb la tarifa nova, primer el que depèn del preu (el d'ara, avui, el període de l'Historial, el mes i la setmana
     * passada), que és de seguida; amb un preu de mercat, primer els preus d'avui i de demà; i després tota la resta.
     */
    fun desaTarifa(t: Tarifa) {
        config.tarifa = t
        generacioTarifa++
        Vigilancia.actualitza(getApplication())
        pantalla = if (preuDesDeConfig) Pantalla.CONNECTA else Pantalla.PRINCIPAL
        preuDesDeConfig = false
        resumCalculat = 0
        viewModelScope.launch {
            recalculant = true
            try {
                refrescaPreus()
                runCatching { repo.actualitzaPreus(nomesRecents = true) }
                refrescaPreus()
                withContext(Dispatchers.IO) { runCatching { Ginys.actualitza(getApplication(), repo, lectura, forca = true) } }
                refrescaAra()
            } finally {
                recalculant = false
            }
            // Els dies d'abans, sense pressa
            runCatching { repo.actualitzaPreus() }
            refrescaAra()
        }
    }

    /** Només el que depèn del preu: ràpid, perquè després de canviar la tarifa no es vegin els euros d'abans. */
    private suspend fun refrescaPreus() {
        val p = periode
        val d = diaHistorial
        val generacio = generacioTarifa
        val r = withContext(Dispatchers.IO) {
            resumCalculat = 0
            listOf(repo.infoPreu(), repo.avui(), repo.resum(p, d), repo.resumMes(), resumDeLaSetmana())
        }
        if (generacio != generacioTarifa) return
        infoPreu = r[0] as InfoPreu
        (r[1] as Avui).let { avuiKwh = it.kwh; avuiEuros = it.euros; avuiAproximat = it.aproximat }
        if (p == periode && d == diaHistorial) resum = r[2] as Resum
        resumMes = r[3] as Resum
        resumSetmana = r[4] as ResumSetmana?
    }

    /** Després de canviar la configuració: torna a començar les lectures. */
    // ---------- Els endolls amb mesura (maqueta 19) ----------

    /** Si hi ha el compte de Tapo (com a estat, perquè la pantalla canviï en desar-lo). */
    var teTapo by mutableStateOf(config.teTapo)
        private set
    var buscantEndolls by mutableStateOf(false)
        private set
    var endollsTrobats by mutableStateOf<List<EndollTrobat>>(emptyList())
        private set
    var provantEndoll by mutableStateOf(false)
        private set
    var missatgeEndolls by mutableStateOf<TextRecurs?>(null)
        private set

    /** Busca els endolls de la wifi (uns 4 s): els Tapo per UDP i els Shelly per mDNS i preguntant a cada adreça. */
    fun buscaEndolls() {
        if (buscantEndolls) return
        viewModelScope.launch {
            buscantEndolls = true
            missatgeEndolls = null
            endollsTrobats = withContext(Dispatchers.IO) {
                val tapo = async { runCatching { DescobrimentTapo.busca(4000) }.getOrDefault(emptyList()) }
                val shelly = async { runCatching { buscaEndollsShelly(getApplication(), config.host) }.getOrDefault(emptyList()) }
                tapo.await() + shelly.await()
            }
            buscantEndolls = false
            if (endollsTrobats.isEmpty()) missatgeEndolls = TextRecurs(R.string.endolls_cap_trobat)
        }
    }

    /**
     * Afegeix un endoll per l'adreça. Primer es mira si és un Shelly (respon al RPC sense cap compte); si no, un Tapo, amb el
     * resum del compte (el nou, si es dona el correu i la contrasenya; si no, el desat). Si l'endoll l'accepta, es desa el
     * compte (només el resum) i l'endoll, i se'n llegeix l'historial.
     */
    fun afegeixEndoll(host: String, correu: String? = null, contrasenya: String? = null) {
        if (provantEndoll) return
        viewModelScope.launch {
            provantEndoll = true
            missatgeEndolls = null
            // L'adreça neta: sense «http://», barres ni res més (ve d'un camp lliure)
            val net = netejaHost(host)
            if (net == null) {
                provantEndoll = false
                missatgeEndolls = TextRecurs(R.string.endolls_no_arribo, host.trim())
                return@launch
            }
            val shelly = withContext(Dispatchers.IO) { runCatching { ShellyEndoll(net).info() }.getOrNull() }
            val nou = !correu.isNullOrBlank() && !contrasenya.isNullOrBlank()
            val resum = if (nou) Klap.resumHex(correu!!.trim(), contrasenya!!) else config.tapoResum
            if (shelly == null && resum.isBlank()) {
                provantEndoll = false
                missatgeEndolls = TextRecurs(R.string.endolls_cal_compte)
                return@launch
            }
            // Un Tapo: qui és i que mesuri (un P100 no té get_energy_usage)
            val r = if (shelly != null) Result.success(shelly) else withContext(Dispatchers.IO) {
                runCatching { Tapo(net, Klap.deHex(resum)).let { t -> t.info().also { t.lectura() } } }
            }
            provantEndoll = false
            r.onSuccess { info ->
                // El compte de Tapo només es desa si l'ha acceptat un Tapo
                if (shelly == null && resum != config.tapoResum) {
                    config.tapoResum = resum
                    config.tapoCorreu = correu?.trim().orEmpty()
                    repo.oblidaSessionsEndolls()
                }
                if (shelly == null) teTapo = true
                withContext(Dispatchers.IO) { repo.afegeixEndoll(info) }
                val avis = TextRecurs(R.string.endolls_afegit, info.nom.ifBlank { info.model })
                missatgeEndolls = avis
                endollsTrobats = endollsTrobats.filter { it.mac != info.mac }
                refrescaEndolls()
                withContext(Dispatchers.IO) { runCatching { repo.sincronitzaEndolls(forca = true) } }
                refrescaAra()
                // El missatge d'èxit no es queda per sempre
                launch {
                    delay(8_000)
                    if (missatgeEndolls === avis) missatgeEndolls = null
                }
            }.onFailure { e ->
                missatgeEndolls = when {
                    e is CredencialsDolentes || (e is ErrorEndoll && e.credencials) -> TextRecurs(R.string.endolls_credencials)
                    e is ErrorEndoll && e.codi == -1002 -> TextRecurs(R.string.endolls_sense_mesura)
                    else -> TextRecurs(R.string.endolls_no_arribo, net)
                }
            }
        }
    }

    /** Obre la Configuració a la Connexió (des de la fitxa d'un aparell amb endoll). */
    fun obreConnexio() {
        seccioConfig = SeccioConfig.CONNEXIO
        pantalla = Pantalla.CONNECTA
    }

    /** L'adreça d'un endoll tal com l'ha escrita l'usuari: sense l'esquema, el camí ni res després; null si no serveix. */
    private fun netejaHost(s: String): String? {
        val net = s.trim().replaceFirst(Regex("^[a-zA-Z]+://"), "").substringBefore('/').substringBefore('?').substringBefore('#')
        if (net.isBlank() || net.contains('@') || net.any { it.isWhitespace() }) return null
        return net
    }

    /** L'endoll passa a mesurar un aparell (o cap). */
    fun posaEndollA(endollId: Long, aparellId: Long?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.posaEndollA(endollId, aparellId) }
            refrescaEndolls()
            refrescaAra()
        }
    }

    /** L'endoll passa a un aparell nou, que es crea aquí mateix (des de «Tots els aparells…»). */
    fun posaEndollANou(endollId: Long, nou: Aparell) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val id = repo.afegeixAparell(nou)
                repo.posaEndollA(endollId, id)
            }
            refrescaEndolls()
            refrescaAra()
        }
    }

    fun esborraEndoll(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.esborraEndoll(id) }
            refrescaEndolls()
            refrescaAra()
        }
    }

    /** Fora el compte de Tapo: els endolls es queden, però no es llegiran fins que se'n torni a posar un. */
    fun desconnectaTapo() {
        config.tapoResum = ""
        config.tapoCorreu = ""
        repo.oblidaSessionsEndolls()
        lecturesEndolls = emptyMap()
        teTapo = false
    }

    /** «Deixa'l on és»: la proposta de moure l'endoll no es torna a fer. */
    fun ignoraMoure(p: PropostaMoure) {
        repo.ignoraMoure(p.endoll.id, p.aparell.id)
        propostesMoure = propostesMoure - p
    }

    private suspend fun refrescaEndolls() {
        val (e, pe) = withContext(Dispatchers.IO) { repo.endolls() to repo.periodesEndoll() }
        endolls = e
        periodesEndoll = pe
    }

    fun configuracioDesada() {
        // El primer cop, a la pantalla principal; després, a la llista de seccions de la Configuració
        if (seccioConfig != null && config.configurat) seccioConfig = null else pantalla = Pantalla.PRINCIPAL
        lectura = null
        haLlegit = false
        repo.shellyCanviat()
        altreShelly = null
        enPrimerPla(true)
    }
}

/** Una baixada de la potència a la caça del sempre encès: quan, quants W, i què era (si s'ha dit). */
data class Baixada(val ts: Long, val w: Double, val aparellId: Long? = null, val respost: Boolean = false)

/**
 * L'avís per desfer l'última resposta o entrenament (maqueta 17): què s'ha desat, com desfer-ho, i què cal tornar com era a
 * la pantalla.
 */
class AvisDesfer(val text: TextRecurs, val accio: Desfer, val desfetALaPantalla: (() -> Unit)? = null) {
    /** Quan s'ha creat: l'avís dura uns 10 segons, també si es gira la pantalla. */
    val creat: Long = System.currentTimeMillis()
}
