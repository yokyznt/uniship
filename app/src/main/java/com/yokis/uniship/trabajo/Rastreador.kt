package com.yokis.uniship.trabajo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yokis.uniship.Notificador
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.cuenta.Nube
import com.yokis.uniship.data.Estado
import com.yokis.uniship.data.Fuente
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.mercadolibre.MercadoLibre
import com.yokis.uniship.paqueterias.Registro
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class Rastreador(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = UniShipApp.repo
        val id = inputData.getLong(ID, -1)
        val lista = if (id > 0) listOfNotNull(repo.paquetes.porId(id)) else repo.paquetes.rastreables()
        lista.forEachIndexed { i, p ->
            if (i > 0) delay(1_500)
            runCatching { rastrear(applicationContext, p) }
        }
        if (id <= 0) {
            runCatching { MercadoLibre.sincronizar(applicationContext) }
            runCatching { com.yokis.uniship.tiendas.Tiendas.sincronizar(applicationContext) }
            runCatching { Nube.respaldoAutomatico() }
            runCatching { com.yokis.uniship.widget.Widget.actualizar(applicationContext) }
        }
        return Result.success()
    }

    companion object {
        const val ID = "id"
        private const val PERIODICO = "rastreo"

        private val red = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun programar(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<Rastreador>(2, TimeUnit.HOURS, 20, TimeUnit.MINUTES)
                .setConstraints(red)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODICO, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun ahora(ctx: Context, id: Long? = null) {
            val req = OneTimeWorkRequestBuilder<Rastreador>()
                .setConstraints(red)
                .setInputData(workDataOf(ID to (id ?: -1L)))
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("ahora-${id ?: "todo"}", ExistingWorkPolicy.REPLACE, req)
        }

        suspend fun rastrear(ctx: Context, p: Paquete): Boolean {
            val repo = UniShipApp.repo
            val guia = p.guia ?: return false
            val fija = Registro.porId(p.paqueteria)
            val candidatos = listOfNotNull(fija) + Registro.detectar(guia).filter { it.id != fija?.id }
            var aviso: String? = null
            for (c in candidatos) {
                val movs = runCatching { c.rastrear(guia) }.onFailure { e -> if (c.id == "jt" && e.message?.contains("dígitos") == true) aviso = "J&T dice que los 4 dígitos no coinciden con el celular de la dirección de envío" }.getOrNull().orEmpty()
                if (movs.isEmpty()) continue
                var actual = repo.paquetes.porId(p.id) ?: return false
                if (actual.paqueteria != c.id) {
                    actual = actual.copy(paqueteria = c.id)
                    repo.paquetes.actualizar(actual)
                }
                val nuevos = repo.aplicar(actual, movs, Fuente.PAQUETERIA)
                val final = repo.paquetes.porId(p.id) ?: actual
                Notificador.movimientos(ctx, final, nuevos)
                return true
            }
            val actual = repo.paquetes.porId(p.id) ?: return false
            val fallos = actual.fallos + 1
            repo.paquetes.actualizar(
                actual.copy(
                    fallos = fallos,
                    ultimoEvento = if (repo.eventos.listar(actual.id).isEmpty() && aviso != null) aviso else actual.ultimoEvento,
                    estado = if (fallos >= 3 && actual.estado == Estado.PENDIENTE) Estado.SIN_DATOS else actual.estado
                )
            )
            return false
        }
    }
}
