package com.yokis.uniship.tiendas

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.yokis.uniship.UniShipApp
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class Tienda(val nombre: String, val entrada: String, val dominio: String, val listo: Regex) {
    ALIEXPRESS("AliExpress", "https://login.aliexpress.com/?return_url=https%3A%2F%2Fm.aliexpress.com%2F", "https://www.aliexpress.com", Regex("^https://(m|www|es)\\.aliexpress\\.(com|us)")),
    TEMU("Temu", "https://www.temu.com/mx/bgt_orders.html", "https://www.temu.com", Regex("temu\\.com/(mx/)?bgt_orders"))
}

class SesionCaducada(val tienda: Tienda) : Exception("Vuelve a entrar a ${tienda.nombre}")

object Tiendas {
    private val prefs get() = UniShipApp.instancia.getSharedPreferences("tiendas", Context.MODE_PRIVATE)

    fun conectada(t: Tienda) = prefs.getBoolean(t.name, false)
    fun caducada(t: Tienda) = prefs.getBoolean(t.name + ".caducada", false)

    fun marcar(t: Tienda, conectada: Boolean, caducada: Boolean = false) {
        prefs.edit().putBoolean(t.name, conectada).putBoolean(t.name + ".caducada", caducada).apply()
    }

    fun desconectar(t: Tienda) {
        val cm = CookieManager.getInstance()
        val url = t.dominio
        cm.getCookie(url)?.split(";")?.map { it.substringBefore("=").trim() }?.forEach { n ->
            val raiz = url.substringAfter("://www").let { if (it.startsWith(".")) it else ".$it" }
            cm.setCookie(url, "$n=; Max-Age=0; Path=/")
            cm.setCookie(url, "$n=; Max-Age=0; Path=/; Domain=$raiz")
        }
        cm.flush()
        marcar(t, false)
    }

    fun desconectarTodas() = Tienda.entries.forEach { desconectar(it) }

    fun agente(ctx: Context): String = runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrDefault(com.yokis.uniship.paqueterias.Http.UA)

    fun cookie(url: String, nombre: String): String? =
        CookieManager.getInstance().getCookie(url)?.split(";")?.map { it.trim() }
            ?.filter { it.startsWith("$nombre=") }?.map { it.substringAfter("=") }?.maxByOrNull { it.substringAfter("_").toLongOrNull() ?: 0 }

    val cliente: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val cm = CookieManager.getInstance()
                    cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
                    cm.flush()
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> =
                    CookieManager.getInstance().getCookie(url.toString())?.split(";")?.mapNotNull { Cookie.parse(url, it.trim()) }.orEmpty()
            })
            .build()
    }

    suspend fun sincronizar(ctx: Context) {
        if (conectada(Tienda.ALIEXPRESS)) runCatching { AliExpress.sincronizar(ctx) }.onFailure { if (it is SesionCaducada) marcar(Tienda.ALIEXPRESS, true, true) }
        if (conectada(Tienda.TEMU)) runCatching { Temu.sincronizar(ctx) }.onFailure { if (it is SesionCaducada) marcar(Tienda.TEMU, true, true) }
    }
}
