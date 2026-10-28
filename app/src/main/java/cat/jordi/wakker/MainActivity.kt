package cat.jordi.wakker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import cat.jordi.wakker.dades.ASPECTE_CLAR
import cat.jordi.wakker.dades.ASPECTE_FOSC
import cat.jordi.wakker.dades.Eprel
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.Periode
import cat.jordi.wakker.ui.Aspecte
import cat.jordi.wakker.ui.Compartit
import cat.jordi.wakker.ui.WakkerViewModel
import cat.jordi.wakker.ui.Desconegut
import cat.jordi.wakker.ui.Fons
import cat.jordi.wakker.ui.Pantalla
import cat.jordi.wakker.ui.PantallaAfegir
import cat.jordi.wakker.ui.PantallaAparells
import cat.jordi.wakker.ui.PantallaAra
import cat.jordi.wakker.ui.PantallaConnecta
import cat.jordi.wakker.ui.PantallaEspera
import cat.jordi.wakker.ui.PantallaFitxa
import cat.jordi.wakker.ui.PantallaHistorial
import cat.jordi.wakker.ui.PantallaPassat
import cat.jordi.wakker.ui.PantallaPerFer
import cat.jordi.wakker.ui.PantallaPreu
import cat.jordi.wakker.ui.PantallaQuestionari
import cat.jordi.wakker.ui.Pestanya
import cat.jordi.wakker.ui.Pestanyes
import cat.jordi.wakker.ui.Primari
import cat.jordi.wakker.ui.PrimariFluix
import cat.jordi.wakker.ui.Rebut
import cat.jordi.wakker.ui.SobrePrimari
import cat.jordi.wakker.ui.Suau
import cat.jordi.wakker.ui.Superficie
import cat.jordi.wakker.ui.TemaWakker
import cat.jordi.wakker.ui.TextPrincipal

class MainActivity : ComponentActivity() {
    private val vm: WakkerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Idioma.actualitza(this)
        barres(fosc = false)
        // En girar la pantalla torna a arribar el mateix intent: l'avís i el que s'havia compartit ja s'han recollit
        if (savedInstanceState == null) {
            obre(intent)
            rep(intent)
            // Si l'app s'acaba de tornar a obrir per restaurar una còpia (1.1.3)
            vm.config.prenCopiaRestaurada()?.let { data ->
                // La data de la còpia com a la resta de l'app («28 d'octubre»)
                val dia = runCatching { java.time.LocalDate.parse(data).format(java.time.format.DateTimeFormatter.ofPattern(getString(R.string.format_dia_mes), Idioma.locale)) }.getOrDefault(data)
                Toast.makeText(this, if (data.isNotEmpty()) getString(R.string.copia_restaurada, dia) else getString(R.string.copia_error, getString(R.string.copia_no_valida)), Toast.LENGTH_LONG).show()
            }
        }
        setContent {
            // Clara o fosca (maqueta 16): com el mòbil, o el que s'hagi triat a la Configuració
            val fosc = when (vm.aspecte) {
                ASPECTE_CLAR -> false
                ASPECTE_FOSC -> true
                else -> isSystemInDarkTheme()
            }
            Aspecte.fosc = fosc
            LaunchedEffect(fosc) { barres(fosc) }
            TemaWakker { Arrel(vm) }
        }
    }

    /** Les icones de les barres d'Android: fosques sobre l'app clara, i clares sobre la fosca. */
    private fun barres(fosc: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val estil = if (fosc) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = estil, navigationBarStyle = estil)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        obre(intent)
        rep(intent)
    }

    /** Des d'un avís, l'app s'obre a «Per fer», on hi ha la pregunta. */
    private fun obre(intent: Intent?) {
        if (intent?.getStringExtra(Avisos.OBRE) == Avisos.ENTRENA && vm.config.configurat) {
            vm.pantalla = Pantalla.PRINCIPAL
            vm.pestanya = Pestanya.PER_FER
            vm.refresca()
        }
    }

    /**
     * El que s'ha compartit amb Wakker des d'una altra app: un enllaç de l'EPREL (se'n busca la fitxa), o una foto
     * o un PDF (es llegeix com la placa). Una fitxa o una etiqueta baixades de l'EPREL es reconeixen pel nom.
     */
    private fun rep(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND || !vm.config.configurat) return
        val rebut: Rebut = if (intent.type?.startsWith("text/") == true) {
            val numero = Eprel.enllac(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())
            if (numero == null) {
                Toast.makeText(this, R.string.marca_compartit_no_eprel, Toast.LENGTH_LONG).show()
                return
            }
            Rebut.Registre(numero)
        } else {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.takeIf { it.scheme == "content" } ?: return
            nomFitxer(uri)?.let(Eprel::deFitxer)?.let { Rebut.Registre(it) } ?: Rebut.Fitxer(uri)
        }
        Compartit.rebut = rebut
        vm.pantalla = Pantalla.AFEGIR
    }

    private fun nomFitxer(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment

    override fun onResume() {
        super.onResume()
        vm.enPrimerPla(true)
    }

    override fun onPause() {
        vm.enPrimerPla(false)
        super.onPause()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Arrel(vm: WakkerViewModel) {
    val principal = vm.pantalla == Pantalla.PRINCIPAL
    // Dins d'una secció de la Configuració, enrere torna a la llista de seccions
    BackHandler(enabled = !principal && vm.config.configurat) { vm.enrere() }

    val titol = when (vm.pantalla) {
        Pantalla.CONNECTA -> when {
            !vm.config.configurat -> stringResource(R.string.principal_connecta)
            vm.seccioConfig != null -> stringResource(vm.seccioConfig!!.nomId)
            else -> stringResource(R.string.principal_configuracio)
        }
        Pantalla.AFEGIR -> stringResource(R.string.principal_afegeix_aparell)
        Pantalla.QUESTIONARI -> stringResource(R.string.principal_revisem_casa)
        Pantalla.PREU -> stringResource(R.string.principal_preu_llum)
        Pantalla.PASSAT -> stringResource(R.string.principal_estona_passada)
        Pantalla.APARELL -> vm.aparells.firstOrNull { it.id == vm.aparellObert }?.nom ?: ""
        Pantalla.ESPERA -> stringResource(R.string.ranquing_sempre)
        Pantalla.PRINCIPAL -> stringResource(if (vm.pestanya == Pestanya.ARA) R.string.principal_casa else vm.pestanya.nomId)
    }
    // L'avís per desfer l'última resposta (maqueta 17): uns 10 segons
    val avisos = remember { SnackbarHostState() }
    val context = LocalContext.current
    val desfer = vm.desfer
    LaunchedEffect(desfer) {
        if (desfer == null) return@LaunchedEffect
        // En girar la pantalla l'avís es tornaria a ensenyar de nou: si ja fa 10 segons que hi era, s'acaba
        if (System.currentTimeMillis() - desfer.creat > 10_000) {
            vm.oblidaDesfer(desfer)
            return@LaunchedEffect
        }
        val r = avisos.showSnackbar(desfer.text.text(context), context.getString(R.string.desfer_accio), duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) vm.desfes(desfer) else vm.oblidaDesfer(desfer)
    }
    val pendents = vm.pendents
    Scaffold(
        containerColor = Fons,
        snackbarHost = { SnackbarHost(avisos) },
        topBar = {
            TopAppBar(
                title = {
                    // A l'Historial, el període va a la barra de dalt (maqueta 18)
                    if (principal && vm.pestanya == Pestanya.HISTORIAL) {
                        Box(Modifier.padding(end = 12.dp)) { Pestanyes(Periode.entries, vm.periode, { stringResource(it.nomId) }, linies = 1, enTriar = vm::triaPeriode) }
                    } else {
                        Text(titol, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Fons, titleContentColor = TextPrincipal, actionIconContentColor = Suau, navigationIconContentColor = TextPrincipal),
                navigationIcon = {
                    if (!principal && vm.config.configurat) {
                        IconButton(onClick = vm::enrere) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.principal_enrere))
                        }
                    }
                },
                actions = {
                    if (principal) {
                        if (vm.pestanya == Pestanya.PER_FER && pendents > 0) {
                            Text(pluralStringResource(R.plurals.perfer_pendents, pendents, pendents), Modifier.padding(end = 4.dp), fontSize = 13.sp, color = Suau)
                        }
                        // A l'Historial el període ocupa el títol: el botó de sincronitzar no hi cap amb un mòbil estret (es pot fer des de les altres pestanyes)
                        if (vm.pestanya != Pestanya.HISTORIAL) {
                            if (vm.sincronitzant || vm.recalculant) {
                                Box(Modifier.padding(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                            } else {
                                IconButton(onClick = vm::sincronitza) { Icon(Icons.Outlined.Sync, stringResource(R.string.principal_recull_dades)) }
                            }
                        }
                        // A l'Historial, el període ocupa la barra: amb la lletra gran «Setmana» no hi cabria (1.1.3). La Configuració
                        // és a les altres pestanyes
                        if (vm.pestanya != Pestanya.HISTORIAL) {
                            IconButton(onClick = { vm.seccioConfig = null; vm.pantalla = Pantalla.CONNECTA }) { Icon(Icons.Outlined.Settings, stringResource(R.string.principal_configuracio)) }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (principal && vm.pestanya == Pestanya.APARELLS) {
                // Sense la descripció, l'accessibilitat (TalkBack) el llegeix buit.
                val afegeix = stringResource(R.string.principal_afegeix_aparell)
                ExtendedFloatingActionButton(
                    onClick = { vm.pantalla = Pantalla.AFEGIR },
                    modifier = Modifier.semantics { contentDescription = afegeix },
                    containerColor = Primari, contentColor = SobrePrimari,
                    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.aparells_afegeix)) },
                )
            }
        },
        bottomBar = {
            if (principal) {
                NavigationBar(containerColor = Superficie) {
                    Pestanya.entries.forEach { p ->
                        NavigationBarItem(
                            selected = vm.pestanya == p,
                            onClick = { vm.pestanya = p },
                            icon = {
                                val icona = when (p) {
                                    Pestanya.ARA -> Icons.Outlined.Home
                                    Pestanya.APARELLS -> Icons.AutoMirrored.Outlined.FormatListBulleted
                                    Pestanya.PER_FER -> Icons.Outlined.Inbox
                                    Pestanya.HISTORIAL -> Icons.Outlined.BarChart
                                }
                                // El globus de «Per fer»: quantes coses hi ha per contestar (maqueta 18)
                                if (p == Pestanya.PER_FER && pendents > 0) {
                                    BadgedBox(badge = { Badge(containerColor = Desconegut, contentColor = if (Aspecte.fosc) androidx.compose.ui.graphics.Color(0xFF1C2421) else androidx.compose.ui.graphics.Color.White) { Text("$pendents") } }) { Icon(icona, null) }
                                } else {
                                    Icon(icona, null)
                                }
                            },
                            // Una sola línia: amb la lletra gran, «Geschiedenis» es partia en dues
                            label = { Text(stringResource(p.nomId), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primari, selectedTextColor = Primari, indicatorColor = PrimariFluix,
                                unselectedIconColor = Suau, unselectedTextColor = Suau,
                            ),
                        )
                    }
                }
            }
        },
    ) { marge ->
        // Amb Android 15 l'app ocupa tota la pantalla i el sistema ja no l'escurça quan surt el teclat: ho fa ella, i
        // així el camp on s'escriu queda per sobre del teclat a totes les pantalles
        Box(Modifier.padding(marge).consumeWindowInsets(marge).imePadding()) {
            when (vm.pantalla) {
                Pantalla.CONNECTA -> PantallaConnecta(vm)
                Pantalla.AFEGIR -> PantallaAfegir(vm)
                Pantalla.QUESTIONARI -> PantallaQuestionari(vm)
                Pantalla.PREU -> PantallaPreu(vm)
                Pantalla.PASSAT -> PantallaPassat(vm)
                Pantalla.APARELL -> PantallaFitxa(vm)
                Pantalla.ESPERA -> PantallaEspera(vm)
                Pantalla.PRINCIPAL -> when (vm.pestanya) {
                    Pestanya.ARA -> PantallaAra(vm)
                    Pestanya.APARELLS -> PantallaAparells(vm)
                    Pestanya.PER_FER -> PantallaPerFer(vm)
                    Pestanya.HISTORIAL -> PantallaHistorial(vm)
                }
            }
        }
    }
}
