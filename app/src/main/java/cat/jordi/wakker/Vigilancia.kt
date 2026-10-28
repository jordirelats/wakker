package cat.jordi.wakker

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.calAvisarPotencia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Vigila la potència amb l'app tancada: llegeix el Shelly (o el núvol) cada 10 segons i avisa si passa del 90 %
 * de la contractada. Android hi obliga a tenir una notificació fixa. De passada, cada lectura desa el comptador
 * (el consum aproximat de fora), i cada minut es mira si hi ha un consum inesperat.
 */
class Vigilancia : Service() {
    private val feina = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bucle: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tipus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, ID, notificacio(null), tipus)
        } catch (_: Exception) {
            // Android no la deixa començar (per exemple, des de segon pla): ja es tornarà a provar en obrir l'app
            stopSelf()
            return START_NOT_STICKY
        }
        enMarxa = true
        if (bucle == null) bucle = feina.launch { vigila() }
        return START_STICKY
    }

    private suspend fun vigila() {
        val repo = (application as WakkerApp).repo
        var comprovat = 0L
        while (feina.isActive) {
            val l = runCatching { repo.lectura() }.getOrNull()
            if (l != null) {
                val limit = repo.config.tarifa.limitKw
                if (limit != null && calAvisarPotencia(repo.config, l.total.w, WakkerApp.oberta)) {
                    // Primer l'avís, de seguida, amb el que se sap; després els minuts nous (a casa i si no s'està sincronitzant),
                    // i la mateixa notificació amb el que hi ha encès segons les vegades que encara duren (maqueta 15). Abans
                    // s'esperava la sincronització, que pot trigar un minut (1.1.3)
                    Avisos.avisaPotencia(this, l.total.w, limit, runCatching { repo.encesAra(l) }.getOrDefault(emptyList()))
                    repo.sincronitzaSiLliure(l)
                    Avisos.avisaPotencia(this, l.total.w, limit, runCatching { repo.encesAra(l) }.getOrDefault(emptyList()), callat = true)
                }
                runCatching { NotificationManagerCompat.from(this).notify(ID, notificacio(l.total.w)) }
                // El giny de la pantalla d'inici, com a molt un cop per minut
                runCatching { Ginys.actualitza(this, repo, l) }
                val ara = System.currentTimeMillis()
                if (ara - comprovat > 60_000 && !WakkerApp.oberta) {
                    comprovat = ara
                    runCatching {
                        val resta = l.total.w - (if (repo.config.teLinia) (l.linia?.w ?: 0.0).coerceAtLeast(0.0) else 0.0)
                        repo.comprovaInesperat(resta)?.let { p -> Avisos.avisa(this, p, repo.aparellsAEntrenar(), repo.referencies()) }
                    }
                }
            }
            delay(10_000)
        }
    }

    private fun notificacio(w: Double?): Notification {
        val limit = (application as WakkerApp).repo.config.tarifa.limitKw
        val text = when {
            w == null -> getString(R.string.vigilancia_engegant)
            limit == null -> getString(R.string.vigilancia_ara, NumberFormat.getIntegerInstance(Idioma.locale).format(w))
            else -> getString(
                R.string.vigilancia_ara_percent, NumberFormat.getIntegerInstance(Idioma.locale).format(w),
                NumberFormat.getIntegerInstance(Idioma.locale).format(w / (limit * 10)),
            )
        }
        return NotificationCompat.Builder(this, Avisos.CANAL_VIGILANCIA)
            .setSmallIcon(R.drawable.ic_llamp)
            .setContentTitle(getString(R.string.vigilancia_titol))
            .setContentText(text)
            .setContentIntent(Avisos.obreApp(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        enMarxa = false
        feina.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ID = 900_000

        /** Si ara vigila: amb l'app tancada, si no, el treball de fons fa la comprovació cada 15 minuts. */
        @Volatile
        var enMarxa = false
            private set

        /**
         * L'engega o l'atura segons la configuració: cal l'avís activat, al moment, la potència contractada i el permís
         * de notificacions. Android només la deixa engegar amb l'app oberta, en arrencar el mòbil o després d'una
         * actualització.
         */
        fun actualitza(context: Context) {
            val c = (context.applicationContext as WakkerApp).repo.config
            val permis = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val intent = Intent(context, Vigilancia::class.java)
            if (c.configurat && c.avisPotencia && c.avisAlMoment && c.tarifa.limitKw != null && permis) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}

/** En arrencar el mòbil o després d'actualitzar l'app, la vigilància torna a començar. */
class ArrencadaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Vigilancia.actualitza(context)
        }
    }
}
