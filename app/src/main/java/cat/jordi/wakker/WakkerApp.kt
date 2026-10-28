package cat.jordi.wakker

import android.app.Application
import android.content.res.Configuration
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cat.jordi.wakker.dades.BaseDades
import cat.jordi.wakker.dades.Config
import cat.jordi.wakker.dades.Idioma
import cat.jordi.wakker.dades.Repositori
import cat.jordi.wakker.dades.calAvisarPotencia
import java.util.concurrent.TimeUnit

class WakkerApp : Application() {
    lateinit var repo: Repositori
        private set

    companion object {
        /** L'app és a la pantalla: la vigilància i el treball de fons no avisen, perquè ja ho ensenya ella. */
        @Volatile
        var oberta = false
    }

    override fun onCreate() {
        super.onCreate()
        Idioma.actualitza(this)
        // Una còpia de seguretat a punt per restaurar: abans d'obrir la base de dades (1.1.3)
        cat.jordi.wakker.dades.Copia.aplicaPendent(this)
        repo = Repositori(Config(this), BaseDades(this))
        Avisos.creaCanal(this)
        programaSincronitzacio(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Idioma.actualitza(this)
        Avisos.creaCanal(this)
    }
}

/**
 * Cada 15 minuts (el mínim d'Android per a una feina en segon pla):
 * - si el mòbil és a la wifi de casa, baixa del Shelly els minuts que falten (fora de casa no hi arriba i no
 *   passa res: el Shelly els guarda fins que torna);
 * - amb un contracte dinàmic, baixa els preus de mercat;
 * - mira si hi ha un consum que l'app no sap explicar i, si fa estona que dura, ho pregunta amb una notificació.
 */
class Sincronitza(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as WakkerApp).repo
        if (repo.config.configurat) {
            runCatching { repo.sincronitza() }
            // Si WorkManager l'ha aturat (el temps s'ha acabat), no continua (1.1.3)
            if (isStopped) return Result.success()
            // Qui només té el núvol no sincronitza: el resum per hores de les dades velles, aquí
            runCatching { repo.resumeixVells() }
            runCatching {
                repo.lectura()?.let { l ->
                    // Si la vigilància no és en marxa, la potència es mira aquí, cada 15 minuts
                    val limit = repo.config.tarifa.limitKw
                    if (!Vigilancia.enMarxa && limit != null && calAvisarPotencia(repo.config, l.total.w, WakkerApp.oberta)) {
                        Avisos.avisaPotencia(applicationContext, l.total.w, limit, runCatching { repo.encesAra(l) }.getOrDefault(emptyList()))
                    }
                    val resta = l.total.w - (if (repo.config.teLinia) (l.linia?.w ?: 0.0).coerceAtLeast(0.0) else 0.0)
                    // Amb l'app oberta ja ho mira ella, cada minut; si no, amb els minuts que ha portat la sincronització
                    if (!WakkerApp.oberta) repo.comprovaInesperatDeFons(resta)?.let { p ->
                        Avisos.avisa(applicationContext, p, repo.aparellsAEntrenar(), repo.referencies())
                    }
                    // El giny de la pantalla d'inici, a part: si falla, els avisos ja s'han mirat
                    runCatching { Ginys.actualitza(applicationContext, repo, l) }
                }
            }
        }
        if (isStopped) return Result.success()
        runCatching { repo.actualitzaPreus() }
        // El dilluns, el resum de la setmana passada (maqueta 15)
        runCatching { Avisos.miraResum(applicationContext, repo) }
        // El que falta, amb el núvol per hores (maqueta 11): com a molt cada 6 hores, i pocs dies per vegada
        if (repo.config.nuvolHores && System.currentTimeMillis() / 1000 - repo.config.nuvolDarrer > 6 * 3600) {
            runCatching { repo.omplNuvol(maxDies = 10) }
        }
        return Result.success()
    }
}

fun programaSincronitzacio(context: Context) {
    val feina = PeriodicWorkRequestBuilder<Sincronitza>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("sincronitza", ExistingPeriodicWorkPolicy.UPDATE, feina)
}
