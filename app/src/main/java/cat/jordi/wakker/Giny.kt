package cat.jordi.wakker

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import java.time.temporal.ChronoUnit
import android.util.TypedValue
import cat.jordi.wakker.dades.Punt
import android.graphics.Path
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Bitmap
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import cat.jordi.wakker.dades.EstatGiny
import cat.jordi.wakker.dades.Franja
import kotlin.math.cos
import kotlin.math.sin
import cat.jordi.wakker.dades.NivellPotencia
import cat.jordi.wakker.dades.Repositori
import cat.jordi.wakker.dades.nivellPotencia
import cat.jordi.wakker.shelly.Lectura
import cat.jordi.wakker.ui.euros
import cat.jordi.wakker.ui.num
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** El giny gran de la pantalla d'inici (4 × 2; maqueta 16, i la gràfica de la maqueta 17): els watts d'ara, avui i el que més gasta. */
class GinyGran : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Ginys.pintaDeFons(this, context)

    // Si l'usuari el fa més gran o més petit, es torna a pintar a la mida nova
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, opcions: android.os.Bundle) =
        Ginys.pintaDeFons(this, context)
}

/** El giny petit (2 × 1): els watts d'ara i els kWh d'avui. */
class GinyPetit : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Ginys.pintaDeFons(this, context)
}

/**
 * Els ginys es pinten amb l'última lectura desada ([EstatGiny]): cada minut amb l'app oberta o mentre la vigilància vigila la
 * potència, i si no, cada 15 minuts amb el treball de fons. Tocant-los s'obre l'app.
 */
object Ginys {
    @Volatile
    private var darrer = 0L

    private fun ids(context: Context, classe: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, classe))

    /** Si n'hi ha cap a la pantalla d'inici: si no, no cal calcular res. */
    private fun hiHa(context: Context) = ids(context, GinyGran::class.java).isNotEmpty() || ids(context, GinyPetit::class.java).isNotEmpty()

    /**
     * Amb una lectura nova, desa què cal ensenyar (els watts, el consum d'avui i el que més gasta) i ho pinta. Com a molt un
     * cop per minut (la vigilància llegeix cada 10 segons), tret de [forca] (la tarifa ha canviat).
     */
    fun actualitza(context: Context, repo: Repositori, l: Lectura?, forca: Boolean = false) {
        if (!hiHa(context)) return
        val ara = System.currentTimeMillis()
        if (!forca && ara - darrer < 55_000) return
        val avui = runCatching { repo.avui() }.getOrNull()
        // El preu de l'hora i el seu text, com a la targeta Ara de l'app (1.1.2)
        val info = runCatching { repo.infoPreu() }.getOrNull()
        val h = info?.ara
        val preu = h?.preu
        val preuText = when {
            h == null -> null
            preu == null -> context.getString(R.string.ara_sense_preu, context.getString(h.franja.nomId))
            h.franja == Franja.UNICA -> context.getString(R.string.ara_preu_unic, num(preu, 3))
            else -> {
                val nom = context.getString(h.franja.nomId)
                val franja = if (h.franja == Franja.MERCAT) nom else info.canviaA?.let { context.getString(R.string.ara_franja_fins_a, nom, it) } ?: nom
                context.getString(R.string.ara_preu_franja, franja, num(preu, 3))
            }
        }
        if (l == null) {
            // Sense lectura (la tarifa ha canviat i encara no se n'ha fet cap): els euros d'avui nous, amb la lectura d'abans
            val e = repo.config.giny ?: return
            if (!forca) return
            repo.config.giny = e.copy(kwh = avui?.kwh ?: e.kwh, euros = avui?.euros ?: e.euros, preu = preu, preuText = preuText)
        } else {
            darrer = ara
            val mes = runCatching { repo.encesAra(l).firstOrNull() }.getOrNull()
            repo.config.giny = EstatGiny(l.ts, l.total.w, avui?.kwh, avui?.euros, mes?.first, mes?.second, preu, preuText)
        }
        pinta(context)
    }

    /**
     * Des d'Android (en afegir el giny, cada mitja hora, o en canviar-ne la mida): la lectura i la gràfica van per la xarxa i la
     * base de dades, i el fil de la pantalla no les ha d'esperar.
     */
    fun pintaDeFons(receptor: AppWidgetProvider, context: Context) {
        val pendent = receptor.goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    // Si la lectura desada té més de 2 minuts (o no n'hi ha cap), se'n fa una de nova: així el giny s'omple de
                    // seguida en afegir-lo i es posa al dia cada mitja hora encara que la vigilància no sigui en marxa
                    val repo = (context.applicationContext as WakkerApp).repo
                    val e = repo.config.giny
                    if (repo.config.configurat && (e == null || System.currentTimeMillis() / 1000 - e.ts > 120)) {
                        val l = repo.lectura()
                        if (l != null) {
                            actualitza(context, repo, l, forca = true)
                            return@runCatching
                        }
                    }
                    pinta(context)
                }
            } finally {
                pendent.finish()
            }
        }
    }

    fun pinta(context: Context) {
        val repo = (context.applicationContext as WakkerApp).repo
        val config = repo.config
        val e = config.giny
        val limit = config.tarifa.limitKw
        val gestor = AppWidgetManager.getInstance(context)
        val grans = ids(context, GinyGran::class.java)
        // La gràfica de les 3 últimes hores, una sola lectura per a tots els ginys grans
        val desades = if (grans.isNotEmpty()) runCatching { repo.serieDarreres(3) }.getOrDefault(emptyList()) else emptyList()
        // Els minuts es desen un cop acabats: la lectura d'ara és l'últim punt, perquè la gràfica arribi fins al número de dalt
        val serie = if (e != null && !vella(e) && desades.isNotEmpty() && e.ts > desades.last().ts) desades + Punt(e.ts, e.w, 0.0) else desades
        for (id in grans) gestor.updateAppWidget(id, gran(context, gestor, id, e, limit, serie))
        for (id in ids(context, GinyPetit::class.java)) gestor.updateAppWidget(id, petit(context, e, limit))
    }

    /**
     * Una lectura de fa més de 35 minuts ja no és «ara»: la feina de fons passa cada 15 minuts, però Android la pot endarrerir
     * força; amb 20 minuts (fins a la 1.1.0) el giny deia «—» massa sovint. Vella, el número es queda, en gris, i l'hora ho diu.
     */
    private fun vella(e: EstatGiny) = System.currentTimeMillis() / 1000 - e.ts > 35 * 60

    /** Els watts, del color del nivell (taronja des del 80 %, vermell des del 90 %); el launcher reaprofita la vista: sempre es posa. */
    private fun watts(context: Context, v: RemoteViews, e: EstatGiny, limitKw: Double?) {
        v.setTextViewText(R.id.watts, num(e.w))
        v.setTextColor(
            R.id.watts,
            when {
                vella(e) -> ContextCompat.getColor(context, R.color.giny_suau)
                nivellPotencia(e.w, limitKw) == NivellPotencia.ALT -> 0xFFD97706.toInt()
                nivellPotencia(e.w, limitKw) == NivellPotencia.LIMIT -> 0xFFDC2626.toInt()
                else -> ContextCompat.getColor(context, R.color.giny_text)
            },
        )
    }

    private fun petit(context: Context, e: EstatGiny?, limitKw: Double?): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.giny_petit)
        v.setOnClickPendingIntent(R.id.giny, Avisos.obreApp(context))
        if (e == null) return v
        watts(context, v, e, limitKw)
        // Vella, el petit diu de quina hora és en lloc del d'avui
        val hora = Instant.ofEpochSecond(e.ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        v.setTextViewText(R.id.avui, if (vella(e)) context.getString(R.string.giny_de_les, hora) else e.kwh?.let { context.getString(R.string.giny_avui_curt, num(it, 1)) } ?: "")
        return v
    }

    /**
     * El gran (maqueta 17, proposta B): la capçalera, els watts i el d'avui, la gràfica de les 3 últimes hores i el que més
     * gasta. Segons l'alçada que li dona el launcher, sense la gràfica (i, encara més baix, sense la fila de baix): el text no es
     * talla mai.
     */
    private fun gran(context: Context, gestor: AppWidgetManager, id: Int, e: EstatGiny?, limitKw: Double?, serie: List<Punt>): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.giny_gran)
        v.setOnClickPendingIntent(R.id.giny, Avisos.obreApp(context))
        if (e == null) return v
        val opcions = gestor.getAppWidgetOptions(id)
        // En vertical, l'amplada és la mínima i l'alçada la màxima
        val ampleDp = opcions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
        val altDp = opcions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 130
        val hora = Instant.ofEpochSecond(e.ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        v.setTextViewText(R.id.hora, if (vella(e)) hora else context.getString(R.string.giny_ara_hora, hora))
        v.setTextColor(R.id.punt, ContextCompat.getColor(context, if (vella(e)) R.color.giny_suau else R.color.giny_ok))
        watts(context, v, e, limitKw)
        // Les targetes Avui i Ara, com a l'app (1.1.2)
        v.setTextViewText(R.id.avui_titol, context.getString(R.string.ara_avui).uppercase())
        v.setTextViewText(R.id.avui, e.kwh?.let { "${num(it, 2)} kWh" } ?: "—")
        v.setTextViewText(R.id.euros, e.kwh?.let { euros(e.euros ?: 0.0) } ?: "")
        v.setTextViewText(R.id.ara_titol, context.getString(R.string.ara_ara).uppercase())
        val preu = e.preu
        v.setTextViewText(R.id.ara_eur, if (preu != null && !vella(e)) context.getString(R.string.ara_euros_hora, euros(e.w / 1000 * preu)) else "—")
        v.setTextViewText(R.id.ara_text, e.preuText ?: "")
        // Si és baix (menys de 135 dp), sense l'arc ni la targeta Ara: la capçalera, els watts i Avui fan uns 92 dp; si no,
        // la fila de les targetes fa uns 100 dp i l'arc hi cap sota els watts (146 dp en total). La resta, la gràfica
        val compacte = altDp < 135
        v.setViewVisibility(R.id.ara_targeta, if (compacte) View.GONE else View.VISIBLE)
        val arcBmp = if (!compacte && limitKw != null) runCatching { arc(context, e.w, limitKw, (ampleDp - 32) * 0.47, 58) }.getOrNull() else null
        if (arcBmp != null) {
            v.setViewVisibility(R.id.arc, View.VISIBLE)
            v.setImageViewBitmap(R.id.arc, arcBmp)
        } else {
            v.setViewVisibility(R.id.arc, View.GONE)
        }
        val base = if (compacte) 92 else 146
        val nom = e.mesNom
        val w = e.mesW
        val ambFila = altDp - base >= 30 && nom != null && w != null && !vella(e)
        if (ambFila) {
            v.setViewVisibility(R.id.fila_mes, View.VISIBLE)
            v.setTextViewText(R.id.mes, context.getString(R.string.giny_ara_aparell, nom))
            v.setTextViewText(R.id.mes_w, if (w!! >= 1000) "${num(w / 1000, 1)} kW" else "${num(w)} W")
        } else {
            v.setViewVisibility(R.id.fila_mes, View.GONE)
        }
        val altGrafica = altDp - base - (if (ambFila) 30 else 0)
        val bitmap = if (altGrafica >= 44) runCatching { grafica(context, serie, ampleDp - 24, altGrafica) }.getOrNull() else null
        if (bitmap != null) {
            v.setViewVisibility(R.id.grafica, View.VISIBLE)
            v.setImageViewBitmap(R.id.grafica, bitmap)
        } else {
            v.setViewVisibility(R.id.grafica, View.GONE)
        }
        return v
    }

    /**
     * L'arc de la potència contractada (1.1.2), com el d'Ara: la part que es fa servir, del color del nivell, les marques del
     * 80 % (gris) i el 90 % (ambre), i al mig el percentatge i «de X kW».
     */
    private fun arc(context: Context, w: Double, limitKw: Double, ampleDp: Double, altDp: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val escala = minOf(d, 2f)
        val ample = (ampleDp * escala).toInt().coerceIn(80, 600)
        val alt = (altDp * escala).toInt()
        val bmp = Bitmap.createBitmap(ample, alt, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val part = (w / (limitKw * 1000)).toFloat().coerceIn(0f, 1f)
        val gruix = 6 * escala
        val r = minOf(ample / 2f - gruix, alt - gruix - 2 * escala)
        val cx = ample / 2f
        val cy = alt - gruix / 2 - 2 * escala
        val caixa = android.graphics.RectF(cx - r, cy - r, cx + r, cy + r)
        val nivell = nivellPotencia(w, limitKw)
        val colorPle = when (nivell) {
            NivellPotencia.ALT -> ContextCompat.getColor(context, R.color.giny_ambre)
            NivellPotencia.LIMIT -> 0xFFDC2626.toInt()
            else -> ContextCompat.getColor(context, R.color.giny_primari)
        }
        fun pinzell(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = gruix; strokeCap = Paint.Cap.ROUND }
        c.drawArc(caixa, 180f, 180f, false, pinzell(ContextCompat.getColor(context, R.color.giny_targeta)))
        c.drawArc(caixa, 180f, 180f * part.coerceAtLeast(0.012f), false, pinzell(colorPle))
        for ((f, color) in listOf(0.8f to R.color.giny_suau, 0.9f to R.color.giny_ambre)) {
            val a = Math.PI * (1 - f)
            val marca = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ContextCompat.getColor(context, color); strokeWidth = 2 * escala; strokeCap = Paint.Cap.ROUND }
            c.drawLine(cx + (r - 6 * escala) * cos(a).toFloat(), cy - (r - 6 * escala) * sin(a).toFloat(), cx + (r + 6 * escala) * cos(a).toFloat(), cy - (r + 6 * escala) * sin(a).toFloat(), marca)
        }
        fun lletra(sp: Float, color: Int, negreta: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics) * escala / d
            textAlign = Paint.Align.CENTER
            if (negreta) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        c.drawText("${num(part * 100.0)} %", cx, cy - 9 * escala, lletra(13f, ContextCompat.getColor(context, R.color.giny_text), true))
        c.drawText(context.getString(R.string.ara_arc_de, num(limitKw, 2)), cx, cy - 1 * escala, lletra(9f, ContextCompat.getColor(context, R.color.giny_suau), false))
        return bmp
    }

    /**
     * La gràfica de les últimes hores: l'àrea i la línia del color de l'app (clar o fosc, segons el mòbil), i a sota les
     * hores en punt i la d'ara.
     */
    private fun grafica(context: Context, punts: List<Punt>, ampleDp: Int, altDp: Int): Bitmap? {
        if (punts.size < 2) return null
        // A tota la densitat del mòbil, però com a molt el doble de dp: el giny s'envia sencer a cada actualització
        val d = context.resources.displayMetrics.density
        val escala = minOf(d, 2f)
        val ample = (ampleDp * escala).toInt().coerceIn(120, 900)
        val alt = (altDp * escala).toInt().coerceIn(40, 400)
        val bmp = Bitmap.createBitmap(ample, alt, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val color = ContextCompat.getColor(context, R.color.giny_primari)
        // La mida de la lletra amb l'escala de l'usuari (també la no lineal d'Android 14)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = ContextCompat.getColor(context, R.color.giny_suau)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, context.resources.displayMetrics) * escala / d
        }
        val baix = alt - text.textSize - 3 * escala
        val desDe = punts.first().ts
        val finsA = punts.last().ts.coerceAtLeast(desDe + 60)
        val maxim = punts.maxOf { it.wTotal }.coerceAtLeast(200.0) * 1.08
        fun x(ts: Long) = (ts - desDe).toFloat() / (finsA - desDe) * (ample - 1)
        fun y(w: Double) = baix - (w / maxim).toFloat().coerceIn(0f, 1f) * (baix - 2 * escala)
        val linia = Path()
        punts.forEachIndexed { i, p -> if (i == 0) linia.moveTo(x(p.ts), y(p.wTotal)) else linia.lineTo(x(p.ts), y(p.wTotal)) }
        val area = Path(linia).apply {
            lineTo(x(punts.last().ts), baix)
            lineTo(x(punts.first().ts), baix)
            close()
        }
        c.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 46; style = Paint.Style.FILL })
        c.drawPath(linia, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.6f * escala; strokeJoin = Paint.Join.ROUND })
        // Les hores en punt (si no topen amb la d'ara) i la d'ara, a la dreta
        val zona = ZoneId.systemDefault()
        val format = DateTimeFormatter.ofPattern("HH:mm")
        val ara = Instant.ofEpochSecond(finsA).atZone(zona).format(format)
        val ampleAra = text.measureText(ara)
        c.drawText(ara, ample - ampleAra, alt - 2 * escala, text)
        // Les hores en punt de l'hora local (a l'Índia o al Nepal, no són les hores en punt UTC)
        var h = Instant.ofEpochSecond(desDe).atZone(zona).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        while (h.toEpochSecond() < finsA) {
            val etiqueta = h.format(format)
            val xe = x(h.toEpochSecond()) - text.measureText(etiqueta) / 2
            if (xe > 0 && xe + text.measureText(etiqueta) < ample - ampleAra - 8 * escala) c.drawText(etiqueta, xe, alt - 2 * escala, text)
            h = h.plusHours(1)
        }
        return bmp
    }
}
