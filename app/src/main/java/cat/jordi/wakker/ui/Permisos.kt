package cat.jordi.wakker.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Obre una altra app (la càmera, els fitxers). Si el mòbil no en té cap que ho faci, crida [siNoHiHa] en comptes de
 * tancar-se.
 */
fun <I> ActivityResultLauncher<I>.obre(entrada: I, siNoHiHa: () -> Unit) {
    try {
        launch(entrada)
    } catch (_: ActivityNotFoundException) {
        siNoHiHa()
    }
}

/** Si l'app pot enviar notificacions, i com demanar-ho. */
class PermisAvisos(val actius: Boolean, val demana: () -> Unit)

private fun Context.activitat(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activitat()
    else -> null
}

/**
 * El permís de notificacions. Es torna a mirar cada cop que es torna a l'app, perquè es pot haver canviat als
 * Ajustos. Si Android ja no ensenya la pregunta (s'ha dit que no dues vegades), s'obren els Ajustos de l'app.
 * [enCanviar] es crida quan canvia, per posar al dia la vigilància.
 */
@Composable
fun rememberPermisAvisos(enCanviar: () -> Unit = {}): PermisAvisos {
    val context = LocalContext.current
    fun mira() = NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun ajustos() {
        try {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        } catch (_: ActivityNotFoundException) {
        }
    }
    var actius by remember { mutableStateOf(mira()) }
    val demana = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { si ->
        actius = si || mira()
        val activitat = context.activitat()
        if (!actius && Build.VERSION.SDK_INT >= 33 && activitat != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activitat, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            ajustos()
        }
        enCanviar()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val ara = mira()
        if (ara != actius) {
            actius = ara
            enCanviar()
        }
    }
    return PermisAvisos(actius) {
        if (Build.VERSION.SDK_INT >= 33) demana.launch(Manifest.permission.POST_NOTIFICATIONS) else ajustos()
    }
}
