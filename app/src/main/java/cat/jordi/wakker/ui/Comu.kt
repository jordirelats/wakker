package cat.jordi.wakker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.Categoria
import cat.jordi.wakker.dades.Franja
import cat.jordi.wakker.dades.HoraPreu
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.NivellPotencia
import cat.jordi.wakker.dades.TextRecurs
import java.text.NumberFormat

/**
 * Clar o fosc (maqueta 16). Els colors de l'app es llegeixen d'aquí cada cop que es fan servir, també dins de les gràfiques:
 * en canviar-ho, tota la pantalla es torna a pintar. Ho decideix [MainActivity] amb la Configuració i el mòbil.
 */
object Aspecte {
    var fosc by mutableStateOf(false)
}

private fun tria(clar: Long, fosc: Long) = Color(if (Aspecte.fosc) fosc else clar)

/*
 * El sistema de colors (maqueta 18): un sol color de marca, el verd, per a l'app i per al que l'app sap; l'ambre només per
 * al que necessita l'usuari; i la resta en neutres càlids. El que ve del núvol és el mateix verd, més clar; els aparells de la
 * pinça 2 porten una marca grisa amb el nom de la línia. Els noms antics (Desconegut, Sempre, Linia, Nuvol…) es mantenen
 * perquè tot el codi els fa servir, però ara valen aquests colors.
 */
val Primari get() = tria(0xFF0F766E, 0xFF2DD4BF)
val PrimariFluix get() = tria(0xFFCCFBF1, 0xFF173A36)
/** Els altres verds de les gràfiques, del més fort al més pàl·lid: els aparells del rànquing. */
val PrimariMig get() = tria(0xFF2A9D94, 0xFF1FA396)
val PrimariClar get() = tria(0xFF8ED3CB, 0xFF7FCDC4)
val PrimariPal get() = tria(0xFFCFEAE6, 0xFF2E4B47)
/** La pàgina, blanc càlid; les targetes, blanques amb una vora fina. */
val Fons get() = tria(0xFFF7F6F2, 0xFF111614)
val Superficie get() = tria(0xFFFFFFFF, 0xFF1B2220)
val Vora get() = tria(0xFFE6E3DC, 0xFF2A3330)
val TextPrincipal get() = tria(0xFF1C2421, 0xFFE6ECEA)
val Suau get() = tria(0xFF6B7571, 0xFF9AA7A2)
val Separador get() = tria(0xFFE6E3DC, 0xFF2A3330)
/** La graella de les gràfiques, més fluixa que el separador. */
val Graella get() = tria(0xFFE9E7E1, 0xFF273029)
/** El gris: el sempre encès, les icones neutres i les marques. */
val Gris get() = tria(0xFF8A938F, 0xFF8F9A95)
val GrisFluix get() = tria(0xFFEDECE7, 0xFF242C29)
val GrisBarra get() = tria(0xFFC9CCC7, 0xFF4A5450)
val Sempre get() = Gris
val SempreFluix get() = GrisFluix
/** L'ambre: només per al que l'app no sap i et demana. */
val Desconegut get() = tria(0xFFD97706, 0xFFF5B24A)
val DesconegutFluix get() = tria(0xFFFEF3C7, 0xFF3A2F14)
/** El taronja dels textos: el [Desconegut] no arriba al contrast que cal per llegir-lo. */
val DesconegutText get() = tria(0xFFB45309, 0xFFF5B24A)
/** La línia de la pinça 2 ja no té color propi: una marca grisa amb el seu nom. */
val Linia get() = Gris
val Ok get() = tria(0xFF15803D, 0xFF4ADE80)
/** El que ve del núvol de Shelly i encara és aproximat: el mateix verd, ratllat o de punts. */
val Nuvol get() = PrimariMig
/** Des del 80 % de la potència contractada, i des del 90 %. */
val PotenciaAlta get() = Desconegut
val PotenciaLimit get() = tria(0xFFDC2626, 0xFFF87171)
val SobrePrimari get() = tria(0xFFFFFFFF, 0xFF06201D)
val LimitFluix get() = tria(0xFFFEF2F2, 0xFF3B1A1A)

/** Les etiquetes dels eixos de les gràfiques. */
val Eix get() = Suau

fun colorNivell(n: NivellPotencia, normal: Color) = when (n) {
    NivellPotencia.NORMAL -> normal
    NivellPotencia.ALT -> PotenciaAlta
    NivellPotencia.LIMIT -> PotenciaLimit
}

/** La lletra de l'app (maqueta 18): Inter, i Inter Display per als números grans. Les xifres són tabulars a tot arreu. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold),
)
val InterDisplay = FontFamily(Font(R.font.inter_display_semibold, FontWeight.SemiBold), Font(R.font.inter_display_bold, FontWeight.Bold))

private const val TABULAR = "tnum"

/** Un número gran: el protagonista d'una pantalla (els watts d'Ara). */
val EstilHero get() = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 60.sp, letterSpacing = (-0.035).em, lineHeight = 60.sp, fontFeatureSettings = TABULAR, color = TextPrincipal)
/** Un número mitjà: el total d'un període. */
val EstilMitja get() = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-0.03).em, lineHeight = 40.sp, fontFeatureSettings = TABULAR, color = TextPrincipal)
/** El número d'una fila (kWh, W, €/h). */
val EstilNumero get() = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR, color = TextPrincipal)
/** El número d'una targeta petita (avui, ara). */
val EstilDada get() = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR, color = TextPrincipal)

@Composable
fun TemaWakker(contingut: @Composable () -> Unit) {
    val colors = if (Aspecte.fosc) darkColorScheme(
        primary = Primari, onPrimary = SobrePrimari, primaryContainer = PrimariFluix, onPrimaryContainer = Primari,
        secondaryContainer = PrimariFluix, onSecondaryContainer = Primari,
        background = Fons, onBackground = TextPrincipal, surface = Fons, onSurface = TextPrincipal,
        surfaceContainer = Superficie, surfaceContainerHigh = Superficie, surfaceContainerHighest = Superficie,
        onSurfaceVariant = Suau, outline = Gris, outlineVariant = Separador,
    ) else lightColorScheme(
        primary = Primari, onPrimary = SobrePrimari, primaryContainer = PrimariFluix, onPrimaryContainer = Primari,
        secondaryContainer = PrimariFluix, onSecondaryContainer = Primari,
        background = Fons, onBackground = TextPrincipal, surface = Fons, onSurface = TextPrincipal,
        surfaceContainer = Superficie, surfaceContainerHigh = Superficie, surfaceContainerHighest = Superficie,
        onSurfaceVariant = Suau, outline = Gris, outlineVariant = Separador,
    )
    // Tota la lletra és Inter, amb les xifres tabulars: els números de les columnes queden alineats
    val base = Typography()
    fun TextStyle.inter() = copy(fontFamily = Inter, fontFeatureSettings = TABULAR)
    val tipografia = Typography(
        displayLarge = base.displayLarge.inter(), displayMedium = base.displayMedium.inter(), displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(), headlineMedium = base.headlineMedium.inter(), headlineSmall = base.headlineSmall.inter(),
        titleLarge = base.titleLarge.inter(), titleMedium = base.titleMedium.inter(), titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter(), bodyMedium = base.bodyMedium.inter(), bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter(), labelMedium = base.labelMedium.inter(), labelSmall = base.labelSmall.inter(),
    )
    MaterialTheme(colorScheme = colors, typography = tipografia, content = contingut)
}

/** Un número com s'escriu en la llengua de l'app (en català, punt de milers i coma decimal). */
fun num(x: Double, decimals: Int = 0): String =
    NumberFormat.getNumberInstance(Idioma.locale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(x)

fun euros(x: Double) = Idioma.euros(num(x, 2))

/**
 * Un número escrit per l'usuari, amb coma o punt decimal: «358.5» i «1,5» són decimals. Si hi ha punt i coma, el
 * darrer és el decimal. Amb [milers], «1.150» (com el que posa [num]) són 1150; als preus, un punt sempre és decimal.
 */
fun llegeixNum(text: String, milers: Boolean = true): Double? {
    val t = text.trim().replace(" ", "").replace(" ", "")
    val decimal = maxOf(t.lastIndexOf(','), t.lastIndexOf('.'))
    val net = when {
        ',' in t && '.' in t -> t.substring(0, decimal).replace(".", "").replace(",", "") + "." + t.substring(decimal + 1)
        ',' in t -> t.replace(',', '.')
        milers && Regex("""[1-9]\d{0,2}(\.\d{3})+""").matches(t) -> t.replace(".", "")
        else -> t
    }
    return net.toDoubleOrNull()
}

/** Un text fet fora de la interfície (al ViewModel o a les dades), en la llengua d'ara. */
@Composable
fun TextRecurs.text(): String = text(LocalContext.current)

/** «X € de consum + Y € de fixos»; als Països Baixos els fixos poden ser negatius, i llavors es resten. */
@Composable
fun consumIFixos(consum: Double, fixos: Double) =
    if (fixos >= 0) stringResource(R.string.comu_consum_mes_fixos, euros(consum), euros(fixos))
    else stringResource(R.string.comu_consum_menys_fixos, euros(consum), euros(-fixos))

fun icona(c: Categoria): ImageVector = when (c) {
    Categoria.NEVERA -> Icons.Outlined.Kitchen
    Categoria.CONGELADOR, Categoria.AIRE -> Icons.Outlined.AcUnit
    Categoria.FORN -> Icons.Outlined.OutdoorGrill
    Categoria.PLACA -> Icons.Outlined.Whatshot
    Categoria.MICROONES -> Icons.Outlined.Microwave
    Categoria.RENTAPLATS -> Icons.Outlined.LocalDining
    Categoria.CAMPANA, Categoria.ASSECADOR_CABELLS -> Icons.Outlined.Air
    Categoria.CAFETERA -> Icons.Outlined.Coffee
    Categoria.BULLIDOR -> Icons.Outlined.EmojiFoodBeverage
    Categoria.TORRADORA -> Icons.Outlined.BreakfastDining
    Categoria.ROBOT -> Icons.Outlined.Blender
    Categoria.RENTADORA -> Icons.Outlined.LocalLaundryService
    Categoria.ASSECADORA -> Icons.Outlined.Dry
    Categoria.PLANXA -> Icons.Outlined.Iron
    Categoria.TELEVISOR -> Icons.Outlined.Tv
    Categoria.CONSOLA -> Icons.Outlined.SportsEsports
    Categoria.ROUTER -> Icons.Outlined.Router
    Categoria.LLUMS -> Icons.Outlined.Lightbulb
    Categoria.ORDINADOR -> Icons.Outlined.Computer
    Categoria.TOVALLOLER -> Icons.Outlined.Bathtub
    Categoria.ESTUFA, Categoria.CALDERA -> Icons.Outlined.LocalFireDepartment
    Categoria.BOMBA_CALOR -> Icons.Outlined.Thermostat
    Categoria.TERMO, Categoria.BOMBA_AIGUA -> Icons.Outlined.WaterDrop
    Categoria.COTXE -> Icons.Outlined.ElectricCar
    Categoria.DEPURADORA -> Icons.Outlined.Pool
    Categoria.ALTRES -> Icons.Outlined.DevicesOther
}

/** Una targeta: blanca, amb una vora fina i les cantonades rodones (maqueta 18). */
@Composable
fun Targeta(modifier: Modifier = Modifier, color: Color = Superficie, contingut: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = color, border = if (color == Superficie) BorderStroke(1.dp, Vora) else null) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), content = contingut)
    }
}

/** El títol d'una targeta: petit i apagat, perquè el que pesi siguin les dades. */
@Composable
fun TitolTargeta(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(bottom = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Suau, letterSpacing = 0.01.em)
}

/** Com es pinta una icona en cercle: neutra, «sap» (verd: l'app el reconeix), «cal» (ambre: et necessita) o grisa (el sempre encès). */
enum class EstatIcona { NEUTRE, SAP, CAL, GRIS }

/** Una icona dins d'un cercle, tenyida només quan vol dir alguna cosa (maqueta 18). */
@Composable
fun Cercle(icona: ImageVector, estat: EstatIcona = EstatIcona.NEUTRE, mida: Dp = 36.dp) {
    val (fons, color) = when (estat) {
        EstatIcona.NEUTRE -> GrisFluix to Suau
        EstatIcona.SAP -> PrimariFluix to Primari
        EstatIcona.CAL -> DesconegutFluix to Desconegut
        EstatIcona.GRIS -> GrisFluix to Gris
    }
    Box(Modifier.size(mida).clip(CircleShape).background(fons), contentAlignment = Alignment.Center) {
        Icon(icona, null, tint = color, modifier = Modifier.size(mida * 0.56f))
    }
}

/** Com abans: una icona amb el fons i el color que es vulgui, ara en cercle. */
@Composable
fun Bombolla(icona: ImageVector, fons: Color, color: Color) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(fons), contentAlignment = Alignment.Center) {
        Icon(icona, null, tint = color, modifier = Modifier.size(20.dp))
    }
}

/** La marca dels aparells que són a la línia de la pinça 2: el nom de la línia, en gris, al costat del nom. */
@Composable
fun Etiqueta(text: String) {
    Text(
        text, Modifier.clip(RoundedCornerShape(6.dp)).background(GrisFluix).padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = Suau, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Una paraula petita en ambre sota un nom, només quan cal («nou»). */
@Composable
fun ParaulaAmbre(text: String) {
    Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = DesconegutText)
}

/**
 * Una fila amb una icona, un títol i un detall, i a la dreta un número gran i un de petit (maqueta 18). [valor] en
 * negreta amb xifres tabulars; [subValor] petit i apagat.
 */
@Composable
fun FilaConsum(
    icona: ImageVector, fons: Color, color: Color, titol: String, subtitol: String, valor: String,
    subtitolColor: Color = Suau, modifier: Modifier = Modifier, liniesSubtitol: Int = 1, subValor: String? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Bombolla(icona, fons, color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(titol, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitol, fontSize = 11.5.sp, lineHeight = 15.sp, color = textFort(subtitolColor), maxLines = liniesSubtitol, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(valor, style = EstilNumero, maxLines = 1)
            if (subValor != null) Text(subValor, fontSize = 11.sp, color = Suau, maxLines = 1)
        }
    }
}

/** Una ratlla fina entre files. */
@Composable
fun Ratlla() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Vora))
}

@Composable
fun Pastilla(text: String, color: Color, fons: Color, punt: Color? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(fons).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (punt != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(punt))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 13.sp, color = textFort(color))
    }
}

/** Una línia d'estat, petita: un punt de color i un text apagat («A casa · en directe»). */
@Composable
fun LiniaEstat(text: String, punt: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(punt))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Suau)
    }
}

/**
 * Un control segmentat (maqueta 18): les opcions en una pista grisa, i la triada en blanc. Serveix per a Dia / Setmana / Mes /
 * Any, Al moment / Cada 15 min, i l'aspecte.
 */
@Composable
fun <T> Pestanyes(opcions: List<T>, triada: T, nom: @Composable (T) -> String, linies: Int = 2, enTriar: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GrisFluix).padding(3.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (o in opcions) {
            val actiu = o == triada
            // Amb selected, el lector de pantalla diu quina pestanya està triada
            Surface(
                selected = actiu, onClick = { enTriar(o) }, modifier = Modifier.weight(1f).semantics { role = Role.Tab }, shape = RoundedCornerShape(9.dp),
                color = if (actiu) Superficie else Color.Transparent, shadowElevation = if (actiu) 1.dp else 0.dp,
            ) {
                // Dues línies si cal: amb la lletra gran, «Països Baixos» o «Normaal en dal» no es tallen (1.1.3)
                Text(
                    nom(o), Modifier.padding(vertical = 6.dp, horizontal = 4.dp), fontSize = 13.sp, maxLines = linies, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
                    fontWeight = if (actiu) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (actiu) TextPrincipal else Suau, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Un xip per triar (un aparell, una opció): blanc amb vora; triat, verd. */
@Composable
fun Xip(text: String, triat: Boolean = false, icona: ImageVector? = null, color: Color? = null, grup: Boolean = false, onClick: () -> Unit) {
    val fons = when {
        triat -> Primari
        color != null -> color
        else -> Superficie
    }
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(fons)
            .then(if (!triat && color == null) Modifier.border(1.dp, Vora, RoundedCornerShape(10.dp)) else Modifier)
            // En un grup d'opcions, el lector de pantalla diu quina és la triada (1.1.3)
            .then(if (grup) Modifier.selectable(selected = triat, role = Role.RadioButton, onClick = onClick) else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icona != null) {
            Icon(icona, null, Modifier.size(15.dp), tint = if (triat) SobrePrimari else Suau)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (triat || color != null) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                triat -> SobrePrimari
                color == PrimariFluix -> Primari
                else -> TextPrincipal
            },
        )
    }
}

/** Un enllaç petit en verd («Veure», «Canvia», «Detalls»). */
@Composable
fun Enllac(text: String, modifier: Modifier = Modifier, icona: ImageVector? = null, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Primari)
        if (icona != null) Icon(icona, null, Modifier.size(14.dp), tint = Primari)
    }
}

/** Una línia apagada, amb la «i» al davant: la informació que abans era un paràgraf sempre a la vista. */
@Composable
fun Nota(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 12.5.sp, color = Suau, lineHeight = 17.sp)
}

/**
 * Una opció amb la seva «i» (maqueta 18): el text, i si es toca la «i», la línia d'ajuda a sota. El control ([dret]) va a la
 * dreta; si [apilat], a sota, perquè és ample (uns xips, un segmentat).
 */
@Composable
fun OpcioAmbAjuda(text: String, ajuda: String?, sota: String? = null, apilat: Boolean = false, dret: @Composable () -> Unit) {
    var oberta by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (ajuda != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.Info, stringResource(R.string.comu_mes_info), Modifier.size(22.dp).clip(CircleShape).clickable { oberta = !oberta }.padding(4.dp),
                            tint = if (oberta) Primari else Suau,
                        )
                    }
                }
                if (sota != null) Text(sota, fontSize = 11.5.sp, color = Suau, lineHeight = 15.sp)
            }
            if (!apilat) dret()
        }
        if (apilat) Box(Modifier.padding(top = 6.dp)) { dret() }
        if (oberta && ajuda != null) Nota(ajuda, Modifier.padding(top = 4.dp))
    }
}

/** Que una part del consum és aproximada; si hi ha l'adreça del Shelly de casa, que s'hi corregirà. */
@Composable
fun NotaAproximat(text: String, esCorregira: Boolean) {
    Nota(if (esCorregira) "$text ${stringResource(R.string.comu_es_corregira)}" else text, Modifier.padding(top = 4.dp))
}

val PuntaColor get() = tria(0xFFDC2626, 0xFFF87171)
val PuntaText get() = tria(0xFFB91C1C, 0xFFFCA5A5)
val VallColor get() = tria(0xFF16A34A, 0xFF4ADE80)
val VallText get() = tria(0xFF15803D, 0xFF86EFAC)

/** Per escriure amb el to d'un color de les gràfiques: el mateix to, prou fosc per llegir-lo. */
fun textFort(c: Color): Color = when (c) {
    Desconegut -> DesconegutText
    PuntaColor -> PuntaText
    VallColor -> VallText
    Separador, Sempre -> Suau
    else -> c
}

/** El color d'una hora: el de la seva franja, o, si el preu és de mercat, segons si és dels més barats o dels més cars del dia. */
fun colorPreu(h: HoraPreu, dia: List<HoraPreu>): Color = when (h.franja) {
    Franja.PUNTA -> PuntaColor
    Franja.PLA, Franja.NORMAAL -> Desconegut
    Franja.VALL, Franja.DAL -> VallColor
    Franja.UNICA -> Primari
    Franja.MERCAT -> {
        val preus = dia.mapNotNull { it.preu }.sorted()
        val p = h.preu
        when {
            p == null || preus.size < 3 -> Separador
            p <= preus[preus.size / 3] -> VallColor
            p >= preus[preus.size * 2 / 3] -> PuntaColor
            else -> Desconegut
        }
    }
}

/** Les 24 hores d'un dia en una tira de colors, amb l'hora d'ara marcada. */
@Composable
fun TiraPreus(hores: List<HoraPreu>, ara: Long?, alcada: Dp = 20.dp) {
    Column {
        Row(Modifier.fillMaxWidth().height(alcada), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            hores.forEach { h ->
                val actual = ara != null && h.ts == ara
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(colorPreu(h, hores))
                        .then(if (actual) Modifier.border(2.dp, TextPrincipal, RoundedCornerShape(3.dp)) else Modifier)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(stringResource(R.string.comu_hora_zero), "6", "12", "18", "24").forEach { Text(it, fontSize = 10.sp, color = Suau) }
        }
    }
}

/** El preu de cada hora en barres (per als contractes dinàmics). */
@Composable
fun BarresPreu(hores: List<HoraPreu>, ara: Long?) {
    val preus = hores.mapNotNull { it.preu }
    if (preus.isEmpty()) {
        Nota(stringResource(R.string.comu_sense_preus_avui))
        return
    }
    val maxim = preus.max() * 1.08
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        hores.forEach { h ->
            val actual = ara != null && h.ts == ara
            Box(
                Modifier.weight(1f).fillMaxHeight(((h.preu ?: 0.0) / maxim).toFloat().coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(colorPreu(h, hores).copy(alpha = if (actual) 1f else 0.6f))
                    .then(if (actual) Modifier.border(1.5.dp, TextPrincipal, RoundedCornerShape(2.dp)) else Modifier)
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(stringResource(R.string.comu_hora_zero), "6", "12", "18", "24").forEach { Text(it, fontSize = 10.sp, color = Suau) }
    }
}
