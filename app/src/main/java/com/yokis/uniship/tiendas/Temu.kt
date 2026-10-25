package com.yokis.uniship.tiendas

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.yokis.uniship.BuildConfig
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Origen
import com.yokis.uniship.paqueterias.Registro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Temu {
    private const val GANCHO = """
        (function(){
          if (window.__uni) return; window.__uni = 1;
          function mandar(u, t){ try { if (u.indexOf('/api/') >= 0 && t && t.charAt(0) == '{') UniShip.recibir(u, t); } catch(e){} }
          var of = window.fetch;
          window.fetch = function(i, o){
            var u = (typeof i === 'string') ? i : (i && i.url) || '';
            return of.apply(this, arguments).then(function(r){ try { r.clone().text().then(function(t){ mandar(u, t); }); } catch(e){} return r; });
          };
          var oo = XMLHttpRequest.prototype.open, os = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function(m, u){ this.__u = String(u); return oo.apply(this, arguments); };
          XMLHttpRequest.prototype.send = function(){ var x = this; x.addEventListener('load', function(){ mandar(x.__u || '', x.responseText); }); return os.apply(this, arguments); };
        })();
    """

    data class Hallazgo(val pedido: String?, val guia: String, val paqueteria: String, val titulo: String?)

    private val clavesGuia = Regex("(?i)(tracking|waybill|shipping_?no|mail_?no|express_?no|logistics_?no)")

    fun buscar(texto: String): List<Hallazgo> {
        val raiz = runCatching { JSONObject(texto) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Hallazgo>()
        fun recorrer(v: Any?, pedido: String?, titulo: String?) {
            when (v) {
                is JSONObject -> {
                    val po = v.optString("parent_order_sn").ifBlank { null } ?: pedido
                    val ti = listOf("goods_name", "goods_title", "title").firstNotNullOfOrNull { v.optString(it).ifBlank { null } } ?: titulo
                    v.keys().forEach { k ->
                        val x = v.opt(k)
                        if (x is String && clavesGuia.containsMatchIn(k)) {
                            val g = Registro.normalizar(x)
                            Registro.detectar(g).firstOrNull()?.let { out += Hallazgo(po, g, it.id, ti) }
                        } else recorrer(x, po, ti)
                    }
                }
                is JSONArray -> for (i in 0 until v.length()) recorrer(v.opt(i), pedido, titulo)
            }
        }
        recorrer(raiz, null, null)
        return out.distinctBy { it.guia }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun capturar(ctx: Context, url: String, esperaMs: Long): List<Pair<String, String>> = withContext(Dispatchers.Main) {
        val respuestas = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return@withContext respuestas
        var url2: String? = null
        val web = WebView(ctx.applicationContext)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun recibir(u: String, t: String) { respuestas += u to t }
        }, "UniShip")
        WebViewCompat.addDocumentStartJavaScript(web, GANCHO, setOf("https://www.temu.com"))
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, u: String?) { url2 = u }
        }
        try {
            web.loadUrl(url)
            delay(esperaMs)
            if (url2?.contains("login") == true) throw SesionCaducada(Tienda.TEMU)
            respuestas.toList()
        } finally {
            web.stopLoading()
            web.removeJavascriptInterface("UniShip")
            web.destroy()
        }
    }

    suspend fun sincronizar(ctx: Context): Int {
        val repo = UniShipApp.repo
        val r = capturar(ctx, "https://www.temu.com/mx/bgt_orders.html?type=2", 14_000)
        if (BuildConfig.DEBUG) withContext(Dispatchers.IO) {
            File(ctx.filesDir, "temu.txt").writeText(r.joinToString("\n\n") { it.first + "\n" + it.second.take(20000) })
            r.forEach { android.util.Log.d("UniShip", "temu ${it.first.take(160)} ${it.second.length}") }
        }
        val hallazgos = r.flatMap { buscar(it.second) }.distinctBy { it.guia }
        var n = 0
        for (h in hallazgos) {
            if (repo.paquetes.porGuia(h.guia) != null) continue
            val p = repo.agregarGuia(h.guia, h.paqueteria, h.titulo, "Temu", Origen.TEMU)
            if (h.pedido != null && p.pedidoExterno == null && repo.paquetes.porPedido("temu:${h.pedido}") == null) repo.paquetes.actualizar(p.copy(pedidoExterno = "temu:${h.pedido}"))
            runCatching { com.yokis.uniship.trabajo.Rastreador.rastrear(ctx, repo.paquetes.porId(p.id) ?: p) }
            n++
        }
        Tiendas.marcar(Tienda.TEMU, true)
        return n
    }
}
