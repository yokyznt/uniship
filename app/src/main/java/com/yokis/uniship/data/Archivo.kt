package com.yokis.uniship.data

import android.content.Context
import android.net.Uri
import com.yokis.uniship.UniShipApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object Archivo {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun nombre() = "UniShip-respaldo-" + java.time.LocalDate.now() + ".json"

    fun codificar(r: Respaldo) = json.encodeToString(Respaldo.serializer(), r)
    fun decodificar(t: String) = json.decodeFromString(Respaldo.serializer(), t)

    suspend fun exportar(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val r = UniShipApp.repo.respaldo()
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(codificar(r).toByteArray()) }
        r.paquetes.size
    }

    suspend fun importar(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val texto = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val r = decodificar(texto)
        UniShipApp.repo.restaurar(r)
        r.paquetes.size
    }
}
