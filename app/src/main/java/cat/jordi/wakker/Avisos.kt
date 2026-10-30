package cat.jordi.wakker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cat.jordi.wakker.dades.Aparell
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.Pregunta
import cat.jordi.wakker.dades.Referencia
import cat.jordi.wakker.dades.Repositori
import cat.jordi.wakker.ui.euros
import cat.jordi.wakker.ui.num
import java.time.DayOfWeek
import java.time.LocalDateTime
import cat.jordi.wakker.dades.endevina
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Els avisos de consum inesperat: una notificació que pregunta què és, amb dos aparells proposats
 * (els que s'hi assemblen més) i «Un altre…», que obre l'app a Entrena.
 */
object Avisos {
    private const val CANAL = "avisos"
    private const val CANAL_POTENCIA = "potencia"
    const val CANAL_VIGILANCIA = "vigilancia"
    private const val ID_POTENCIA = 900_001
    private const val CANAL_RESUM = "resum"
    private const val ID_RESUM = 900_002
    const val OBRE = "obre"
    const val ENTRENA = "entrena"
    private const val CANAL_SESSIO = "sessio"
    private const val ID_SESSIO = 900_003

    /** Des de la notificació d'una estona d'inici a fi: «L'he apagat» (acaba l'estona) o tocar-la (obre «Per fer»). */
    const val APAGAT = "apagat"
    const val INICI = "inici"

    fun creaCanal(context: Context) {
        val canal = NotificationChannel(CANAL, context.getString(R.string.avis_canal), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.avis_canal_descripcio)
        }
        val potencia = NotificationChannel(CANAL_POTENCIA, context.getString(R.string.avis_canal_potencia), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.avis_canal_potencia_descripcio)
        }
        val vigilancia = NotificationChannel(CANAL_VIGILANCIA, context.getString(R.string.avis_canal_vigilancia), NotificationManager.IMPORTANCE_MIN).apply {
            description = context.getString(R.string.avis_canal_vigilancia_descripcio)
            setShowBadge(false)
        }
        val resum = NotificationChannel(CANAL_RESUM, context.getString(R.string.avis_canal_resum), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.avis_canal_resum_descripcio)
        }
        val sessio = NotificationChannel(CANAL_SESSIO, context.getString(R.string.avis_canal_sessio), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.avis_canal_sessio_descripcio)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(canal, potencia, vigilancia, resum, sessio))
    }

    fun obreApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Mentre s'ensenya un aparell d'inici a fi (maqueta 21): «Wakker aprèn …», amb el temps que fa que és engegat i «L'he
     * apagat», perquè es pugui dir encara que l'app sigui tancada. No es pot treure lliscant (fins a Android 13).
     */
    fun avisaSessio(context: Context, nom: String, desDe: Long) {
        val gestor = NotificationManagerCompat.from(context)
        if (!gestor.areNotificationsEnabled()) return
        val apagat = PendingIntent.getActivity(
            // Amb l'inici: si l'app es torna a obrir des de les recents amb aquest intent, no ha d'acabar una estona nova
            context, 3, Intent(context, MainActivity::class.java).putExtra(OBRE, APAGAT).putExtra(INICI, desDe).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CANAL_SESSIO)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle(context.getString(R.string.avis_sessio_titol, nom))
            .setContentText(context.getString(R.string.avis_sessio_text))
            .setWhen(desDe * 1000)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(obreEntrena(context))
            .addAction(0, context.getString(R.string.sessio_apagat), apagat)
            .build()
        runCatching { gestor.notify(ID_SESSIO, n) }
    }

    fun treuSessio(context: Context) = NotificationManagerCompat.from(context).cancel(ID_SESSIO)

    /** La potència ha passat del 90 % de la contractada ([limitKw]). [ences]: els aparells que l'expliquen, si se'n sap cap. */
    fun avisaPotencia(context: Context, w: Double, limitKw: Double, ences: List<Pair<String, Double>> = emptyList(), callat: Boolean = false) {
        val gestor = NotificationManagerCompat.from(context)
        if (!gestor.areNotificationsEnabled()) return
        val watts = NumberFormat.getIntegerInstance(Idioma.locale).format(w)
        val percent = NumberFormat.getIntegerInstance(Idioma.locale).format(w / (limitKw * 10))
        val kw = NumberFormat.getNumberInstance(Idioma.locale).apply { maximumFractionDigits = 2 }.format(limitKw)
        val titol = if (w >= limitKw * 1000) context.getString(R.string.avis_potencia_passada, watts, kw)
        else context.getString(R.string.avis_potencia_titol, watts, percent)
        val quilos = NumberFormat.getNumberInstance(Idioma.locale).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
        val text = if (ences.isEmpty()) context.getString(R.string.avis_potencia_text)
        else context.getString(R.string.avis_potencia_ences, ences.joinToString(", ") { (nom, w) -> "$nom ${quilos.format(w / 1000)} kW" })
        val n = NotificationCompat.Builder(context, CANAL_POTENCIA)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle(titol)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(obreApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(15 * 60_000L)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // Posar al dia la que ja hi és (amb el que hi ha encès, després de sincronitzar) no torna a sonar
            .setOnlyAlertOnce(callat)
            .build()
        try {
            gestor.notify(ID_POTENCIA, n)
        } catch (_: SecurityException) {
        }
    }

    /**
     * El resum de la setmana (maqueta 15): el dilluns des de les 9 (o el primer cop que es mira després), un sol cop per
     * setmana, si l'app té dades de la setmana passada. El total, qui ha gastat més i el que queda per fer.
     */
    fun miraResum(context: Context, repo: Repositori) {
        val c = repo.config
        if (!c.resumSetmana) return
        val ara = LocalDateTime.now()
        val dilluns = ara.toLocalDate().with(DayOfWeek.MONDAY)
        if (ara.toLocalDate() == dilluns && ara.hour < 9) return
        val setmana = dilluns.minusWeeks(1).toEpochDay()
        if (c.resumAvisat >= setmana) return
        val s = repo.resumSetmana()
        c.resumAvisat = setmana
        val rq = s?.resum?.ranquing ?: return
        val gestor = NotificationManagerCompat.from(context)
        if (!gestor.areNotificationsEnabled()) return
        val titol = context.getString(R.string.avis_resum_titol, num(rq.total, 1), euros(s.resum.euros.total))
        val text = buildList {
            rq.aparells.firstOrNull()?.let { add(context.getString(R.string.avis_resum_qui_mes, it.nom, num(it.kwh, 1))) }
            val sempre = rq.sempre / rq.total.coerceAtLeast(0.001)
            when {
                s.repetits > 0 -> add(context.resources.getQuantityString(R.plurals.resum_repetits, s.repetits, s.repetits))
                rq.senseIdentificar / rq.total.coerceAtLeast(0.001) >= 0.2 ->
                    add(context.getString(R.string.resum_sense_nom, num(rq.senseIdentificar / rq.total * 100)))
                sempre >= 0.15 -> add(context.getString(R.string.resum_sempre, num(sempre * 100)))
            }
        }.joinToString(" ")
        val n = NotificationCompat.Builder(context, CANAL_RESUM)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle(titol)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(obreApp(context))
            .setAutoCancel(true)
            .build()
        try {
            gestor.notify(ID_RESUM, n)
        } catch (_: SecurityException) {
        }
    }

    private fun obreEntrena(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).putExtra(OBRE, ENTRENA).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun resposta(context: Context, pregunta: Long, aparell: Long): PendingIntent = PendingIntent.getBroadcast(
        context, (pregunta * 1000 + aparell).toInt(),
        Intent(context, RespostaReceiver::class.java).putExtra("pregunta", pregunta).putExtra("aparell", aparell),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun avisa(context: Context, p: Pregunta, aparells: List<Aparell>, referencies: Map<Long, Referencia>) {
        val gestor = NotificationManagerCompat.from(context)
        if (!gestor.areNotificationsEnabled()) return
        val kw = NumberFormat.getNumberInstance(Idioma.locale).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }.format(p.dp / 1000) + " kW"
        val hora = Instant.ofEpochSecond(p.ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        val minuts = ((System.currentTimeMillis() / 1000 - p.ts) / 60).toInt()
        val text = context.getString(R.string.avis_text, hora, minuts)
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle(context.getString(R.string.avis_titol, kw))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(obreEntrena(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        endevina(p.dp, aparells, referencies).forEach { a -> n.addAction(0, a.nom, resposta(context, p.id, a.id)) }
        n.addAction(0, context.getString(R.string.avis_un_altre), obreEntrena(context))
        try {
            gestor.notify(p.id.toInt(), n.build())
        } catch (_: SecurityException) {
            // Sense permís de notificacions: la pregunta queda igualment a Entrena
        }
    }

    fun confirma(context: Context, preguntaId: Long, text: String) {
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle(text)
            .setContentIntent(obreEntrena(context))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(preguntaId.toInt(), n)
        } catch (_: SecurityException) {
        }
    }
}

/** La resposta a una pregunta des de la mateixa notificació. */
class RespostaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pregunta = intent.getLongExtra("pregunta", 0)
        val aparell = intent.getLongExtra("aparell", 0)
        val repo = (context.applicationContext as WakkerApp).repo
        val pendent = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (repo.respon(pregunta, aparell) == null) {
                    // Ja s'havia respost des de l'app
                    NotificationManagerCompat.from(context).cancel(pregunta.toInt())
                    return@launch
                }
                val nom = repo.aparells().firstOrNull { it.id == aparell }?.nom
                Avisos.confirma(
                    context, pregunta,
                    if (nom != null) context.getString(R.string.avis_respost, nom) else context.getString(R.string.avis_respost_sense_nom),
                )
            } finally {
                pendent.finish()
            }
        }
    }
}
