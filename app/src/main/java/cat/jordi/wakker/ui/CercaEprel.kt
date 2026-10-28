package cat.jordi.wakker.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cat.jordi.wakker.R
import cat.jordi.wakker.dades.ErrorUsuari
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/**
 * La cerca per model a l'EPREL. L'API de cerca demana una clau, però la web pública no: s'obre en un WebView
 * invisible, s'hi escriu el model, es prem «Search» i es llegeixen els números de registre dels resultats.
 * La fitxa de cada número es demana després a l'API oberta (`Eprel.fitxa`). Les estadístiques de visites de
 * la web (webanalytics.europa.eu) no s'envien.
 *
 * El JavaScript es prova al portàtil amb `recerca/eprel/prova_cerca_app.py`, que el llegeix d'aquest fitxer:
 * si es canvia aquí, s'hi ha de tornar a passar.
 */
object CercaEprel {
    const val WEB = "https://eprel.ec.europa.eu/screen/product/"
    const val MAX_RESULTATS = 8

    /**
     * Si la pàgina ja és la del grup i ja té el camp del model i la primera llista de productes, acabada de carregar.
     * Abans de la cerca, la pàgina ja ensenya una llista de productes qualsevol. Mentre carrega, hi ha la
     * rodeta i, alhora, «No result».
     */
    const val JS_LLEST = """(function () {
  var llista = document.querySelector('app-product-search-list-container');
  return {
    ruta: location.pathname,
    camp: !!document.querySelector('input#modelIdentifier'),
    llista: !!(llista && !llista.querySelector('ecl-loading-indicator') && llista.querySelector('app-search-result-card, h2'))
  };
})()"""

    /**
     * Escriu el model i prem el botó de cercar. Abans, escolta la resposta de la cerca que fa la pàgina
     * (/api/products/<grup>?...&modelIdentifier=...), que porta el número de registre de cada resultat.
     * Els productes que ja hi havia es marquen, per no confondre'ls amb els resultats. També busca els models
     * que ja no es venen, que són molts dels que hi ha a les cases. El botó es busca per l'atribut i no pel
     * text, perquè la web surt en la llengua del mòbil («Zoeken»).
     */
    const val JS_CERCA = """(function () {
  var camp = document.querySelector('input#modelIdentifier');
  if (!camp) return 'sense camp';
  if (!window.consumEscolta) {
    window.consumEscolta = true;
    var obre = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (metode, url) {
      this.consumUrl = String(url);
      return obre.apply(this, arguments);
    };
    var envia = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function () {
      var x = this;
      var url = x.consumUrl || '';
      if (/\/api\/products\/[^\/?]+\?/.test(url) && url.indexOf('modelIdentifier=') >= 0) {
        x.addEventListener('load', function () {
          try {
            var r = x.responseType == 'json' ? x.response : JSON.parse(x.responseText);
            if (x.status == 200 && r && r.hits) window.consumResposta = r;
          } catch (e) {}
        });
      }
      return envia.apply(this, arguments);
    };
  }
  document.querySelectorAll('app-search-result-card, app-product-search-list-container h2').forEach(function (e) {
    e.setAttribute('data-consum', 'vell');
  });
  var antics = document.querySelector('input#checkbox-includeOldProducts');
  if (antics && !antics.checked) antics.click();
  camp.value = "__MODEL__";
  camp.dispatchEvent(new Event('input', { bubbles: true }));
  camp.dispatchEvent(new Event('change', { bubbles: true }));
  var boto = document.querySelector('button[data-e2e=searchButtonBasicSearch]')
    || Array.prototype.filter.call(document.querySelectorAll('button'), function (b) { return /search/i.test(b.textContent); })[0];
  if (!boto) return 'sense boto';
  window.consumResposta = null;
  boto.click();
  return 'ok';
})()"""

    /**
     * Els números de registre dels resultats (com a molt els 25 de la primera pàgina), i si no n'hi ha cap.
     * Si no s'ha pogut escoltar la resposta, es miren les caselles «Compare» dels resultats nous (no tots els
     * grups les tenen). Mentre hi ha la rodeta, encara no se sap res: alhora, la pàgina ensenya «No result».
     */
    const val JS_RESULTATS = """(function () {
  var numeros = [];
  function afegeix(n) {
    n = String(n || '');
    if (n && !/[^0-9]/.test(n) && numeros.indexOf(n) < 0) numeros.push(n);
  }
  var r = window.consumResposta;
  if (r) {
    r.hits.forEach(function (h) { afegeix(h.eprelRegistrationNumber); });
    return { numeros: numeros, buit: numeros.length == 0 };
  }
  var llista = document.querySelector('app-product-search-list-container');
  if (!llista || llista.querySelector('ecl-loading-indicator')) return { numeros: numeros, buit: false };
  llista.querySelectorAll('app-search-result-card:not([data-consum]) input[id^=res_compareModel_]').forEach(function (e) {
    afegeix(e.id.replace('res_compareModel_', ''));
  });
  return { numeros: numeros, buit: numeros.length == 0 && !!llista.querySelector('h2:not([data-consum])') };
})()"""

    fun jsCerca(model: String) = JS_CERCA.replace("\"__MODEL__\"", JSONObject.quote(model))
}

/** El WebView de la cerca, i si la pàgina principal no ha carregat (sense xarxa, o un error del servidor). */
private class NavegadorEprel(context: Context) {
    var fallada = false

    @SuppressLint("SetJavaScriptEnabled")
    val web = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.blockNetworkImage = true
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) fallada = true
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true) fallada = true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                if (request?.url?.host?.endsWith("webanalytics.europa.eu") == true) WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                else null
        }
    }

    suspend fun js(codi: String): String? = suspendCancellableCoroutine { c ->
        web.evaluateJavascript(codi) { r -> if (c.isActive) c.resume(r) }
    }

    suspend fun objecte(codi: String): JSONObject? = js(codi)?.let { runCatching { JSONObject(it) }.getOrNull() }
}

/** El que ha trobat la cerca: amb quin model (de les variants) i els números de registre; [exacte]: no l'ha hagut d'escurçar. */
data class TrobatEprel(val model: String, val numeros: List<String>, val exacte: Boolean)

/**
 * Busca a l'EPREL, grup per grup, els [models] (les variants de [variantsModel]: les [exactes] primer, i després les
 * escurçades). En cada grup prova les variants en ordre, a la mateixa pàgina, i s'atura a la primera que en troba. Si
 * una d'exacta en troba, acaba; si només en troba una d'escurçada, se la guarda i prova les exactes als altres grups.
 * Acaba amb el que ha trobat (com a molt [CercaEprel.MAX_RESULTATS] números de registre), amb null si no ha trobat res,
 * o amb un error si la web no ha carregat. El WebView és d'1 dp i no es veu. Cada cerca n'ha de fer un de nou.
 * La primera vegada, amb una connexió lenta, la web pot trigar més de 20 s (amb un 3G simulat, en va trigar més).
 */
@Composable
fun CercaEprelWeb(grups: List<String>, models: List<String>, exactes: Int, enAcabar: (Result<TrobatEprel?>) -> Unit) {
    val context = LocalContext.current
    val navegador = remember { NavegadorEprel(context) }
    val acaba by rememberUpdatedState(enAcabar)
    AndroidView(
        factory = { navegador.web }, modifier = Modifier.size(1.dp),
        onRelease = {
            it.stopLoading()
            it.destroy()
        },
    )

    LaunchedEffect(navegador) {
        var carregada = false
        var escurcat: TrobatEprel? = null
        for (grup in grups) {
            navegador.fallada = false
            navegador.web.loadUrl(CercaEprel.WEB + grup)
            // L'aplicació de la pàgina triga uns segons a posar el camp del model i la primera llista
            var camp = false
            withTimeoutOrNull(ESPERA_CARREGA_MS) {
                while (!navegador.fallada) {
                    delay(500)
                    val o = navegador.objecte(CercaEprel.JS_LLEST) ?: continue
                    if (!o.optString("ruta").endsWith("/$grup")) continue
                    camp = o.optBoolean("camp")
                    if (camp && o.optBoolean("llista")) break
                }
            }
            // Si no carrega, no es prova el grup següent: la segona vegada la web ja és a la memòria cau i va més de pressa
            if (!camp) break
            for ((i, model) in models.withIndex()) {
                // Amb una d'escurçada ja trobada, en els altres grups només es proven les exactes
                if (escurcat != null && i >= exactes) break
                if (withTimeoutOrNull(5_000) { navegador.js(CercaEprel.jsCerca(model)) } != "\"ok\"") break
                carregada = true
                var numeros = emptyList<String>()
                val respost = withTimeoutOrNull(ESPERA_RESULTATS_MS) {
                    while (true) {
                        delay(500)
                        val o = navegador.objecte(CercaEprel.JS_RESULTATS) ?: continue
                        val a = o.optJSONArray("numeros") ?: continue
                        numeros = (0 until a.length()).map { a.getString(it) }.take(CercaEprel.MAX_RESULTATS)
                        if (numeros.isNotEmpty() || o.optBoolean("buit")) break
                    }
                    true
                }
                // Si la web no ha respost, no es proven més variants en aquest grup: cadascuna esperaria el mateix
                if (respost == null) break
                if (numeros.isEmpty()) continue
                if (i < exactes) {
                    acaba(Result.success(TrobatEprel(model, numeros, true)))
                    return@LaunchedEffect
                }
                if (escurcat == null) escurcat = TrobatEprel(model, numeros, false)
                break
            }
        }
        acaba(if (carregada || escurcat != null) Result.success(escurcat) else Result.failure(ErrorUsuari(R.string.cerca_no_carrega)))
    }
}

private const val ESPERA_CARREGA_MS = 20_000L
private const val ESPERA_RESULTATS_MS = 12_000L
