package com.yokis.uniship

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yokis.uniship.tiendas.AliExpress
import com.yokis.uniship.tiendas.Temu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Depurar : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                when (intent.getStringExtra("t")) {
                    "aer" -> Log.d("UniShip", "dep aer -> " + runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { AliExpress.rastreo(ctx, intent.getStringExtra("id")!!) }.joinToString { e -> "${e.guia} ${e.paqueteria} eta=${e.eta} ${e.movimientos.size} mov: " + e.movimientos.take(3).joinToString(" | ") { it.descripcion + "/" + it.estado } } }.let { it.getOrNull() ?: it.exceptionOrNull().toString() })
                    "jt" -> Log.d("UniShip", "dep jt cel=${UniShipApp.prefs.ultimos4()?.let { "****" } ?: "null"} -> " + runCatching { com.yokis.uniship.paqueterias.JtExpress.rastrear(intent.getStringExtra("g")!!).size.toString() }.let { it.getOrNull() ?: it.exceptionOrNull().toString() })
                    "conectar" -> com.yokis.uniship.tiendas.ConectarTienda.abrir(ctx.applicationContext.let { c -> object : android.content.ContextWrapper(c) { override fun startActivity(i: Intent) { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }, com.yokis.uniship.tiendas.Tienda.valueOf(intent.getStringExtra("tienda")!!))
                    "noti" -> {
                        val id = intent.getStringExtra("id")!!.toLong()
                        val p = UniShipApp.repo.paquetes.porId(id)!!
                        val estado = intent.getStringExtra("estado")?.let { com.yokis.uniship.data.Estado.valueOf(it) } ?: p.estado
                        Notificador.movimientos(ctx, p.copy(estado = estado), UniShipApp.repo.eventos.listar(id).take(1).map { it.copy(fecha = System.currentTimeMillis(), estado = estado) })
                    }
                    "singuia" -> Notificador.sinGuia(ctx, com.yokis.uniship.data.Paquete(id = 99, titulo = "Pedido de TikTok Shop", tienda = "TikTok Shop"), "Tu pedido de MayaCosmetics fue enviado")
                    "widget" -> {
                        val m = android.appwidget.AppWidgetManager.getInstance(ctx)
                        Log.d("UniShip", "dep widget pin=" + m.requestPinAppWidget(android.content.ComponentName(ctx, com.yokis.uniship.widget.ReceptorWidget::class.java), null, null))
                    }
                    "temu" -> Log.d("UniShip", "dep temu -> " + runCatching { Temu.sincronizar(ctx) }.let { it.getOrNull() ?: it.exceptionOrNull()?.stackTraceToString()?.take(1500) })
                    else -> Log.d("UniShip", "dep ae -> " + runCatching { AliExpress.sincronizar(ctx) }.let { it.getOrNull() ?: it.exceptionOrNull()?.stackTraceToString()?.take(1500) })
                }
            } finally { p.finish() }
        }
    }
}
