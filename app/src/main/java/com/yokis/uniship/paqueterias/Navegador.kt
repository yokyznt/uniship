package com.yokis.uniship.paqueterias

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object Navegador {
    lateinit var app: Context

    private fun gancho(filtro: String) = """
        (function(){
          if (window.__uni) return; window.__uni = 1;
          var f = ${JSONObject.quote(filtro)};
          var of = window.fetch;
          window.fetch = function(i, o){
            var u = (typeof i === 'string') ? i : (i && i.url) || '';
            return of.apply(this, arguments).then(function(r){
              if (u.indexOf(f) >= 0) { try { r.clone().text().then(function(t){ UniShip.recibir(r.status, t); }); } catch(e){} }
              return r;
            });
          };
          var oo = XMLHttpRequest.prototype.open, os = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function(m, u){ this.__u = u; return oo.apply(this, arguments); };
          XMLHttpRequest.prototype.send = function(){
            var x = this;
            x.addEventListener('load', function(){ if ((x.__u || '').indexOf(f) >= 0) { try { UniShip.recibir(x.status, x.responseText); } catch(e){} } });
            return os.apply(this, arguments);
          };
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun capturar(url: String, filtro: String, aceptar: (Int, String) -> Boolean, esperaMs: Long = 45_000): String? = withContext(Dispatchers.Main) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return@withContext null
        val resultado = CompletableDeferred<String>()
        val web = WebView(app)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun recibir(estado: Int, texto: String) {
                if (aceptar(estado, texto)) resultado.complete(texto)
            }
        }, "UniShip")
        WebViewCompat.addDocumentStartJavaScript(web, gancho(filtro), setOf("*"))
        web.webViewClient = WebViewClient()
        try {
            web.loadUrl(url)
            withTimeoutOrNull(esperaMs) { resultado.await() }
        } finally {
            web.stopLoading()
            web.removeJavascriptInterface("UniShip")
            web.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extraer(url: String, script: String, esperaMs: Long = 25_000, intervaloMs: Long = 1_500): String? = withContext(Dispatchers.Main) {
        val resultado = CompletableDeferred<String>()
        val web = WebView(app)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        val h = Handler(Looper.getMainLooper())
        fun intentar() {
            if (resultado.isCompleted) return
            web.evaluateJavascript("(function(){try{return $script}catch(e){return null}})()") { r ->
                val s = runCatching { org.json.JSONTokener(r).nextValue() as? String }.getOrNull()
                if (!s.isNullOrBlank()) resultado.complete(s) else h.postDelayed({ intentar() }, intervaloMs)
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, u: String?) { h.postDelayed({ intentar() }, intervaloMs) }
        }
        try {
            web.loadUrl(url)
            withTimeoutOrNull(esperaMs) { resultado.await() }
        } finally {
            h.removeCallbacksAndMessages(null)
            web.stopLoading()
            web.destroy()
        }
    }
}
