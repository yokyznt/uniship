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
                    "temu" -> Log.d("UniShip", "dep temu -> " + runCatching { Temu.sincronizar(ctx) }.let { it.getOrNull() ?: it.exceptionOrNull()?.stackTraceToString()?.take(1500) })
                    else -> Log.d("UniShip", "dep ae -> " + runCatching { AliExpress.sincronizar(ctx) }.let { it.getOrNull() ?: it.exceptionOrNull()?.stackTraceToString()?.take(1500) })
                }
            } finally { p.finish() }
        }
    }
}
