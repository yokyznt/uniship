package com.yokis.uniship

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.almacen by preferencesDataStore("uniship")

data class SesionMl(val acceso: String, val refresco: String, val expira: Long, val usuario: Long)

class Preferencias(private val ctx: Context) {
    private val onboarding = booleanPreferencesKey("onboarding")
    private val mlAcceso = stringPreferencesKey("ml_acceso")
    private val mlRefresco = stringPreferencesKey("ml_refresco")
    private val mlExpira = longPreferencesKey("ml_expira")
    private val mlUsuario = longPreferencesKey("ml_usuario")
    private val mlVerificador = stringPreferencesKey("ml_verificador")
    private val respaldo = longPreferencesKey("ultimo_respaldo")
    private val cel4 = stringPreferencesKey("cel4")
    private val privacidad = androidx.datastore.preferences.core.intPreferencesKey("privacidad")

    val onboardingListo = ctx.almacen.data.map { it[onboarding] ?: false }
    val mlConectado = ctx.almacen.data.map { it[mlRefresco] != null }
    val ultimoRespaldo = ctx.almacen.data.map { it[respaldo] ?: 0L }

    suspend fun terminarOnboarding() = ctx.almacen.edit { it[onboarding] = true; it[privacidad] = com.yokis.uniship.ui.AvisoPrivacidad.VERSION }

    val privacidadVista = ctx.almacen.data.map { it[privacidad] ?: 0 }
    suspend fun aceptarPrivacidad() = ctx.almacen.edit { it[privacidad] = com.yokis.uniship.ui.AvisoPrivacidad.VERSION }

    suspend fun borrarTodo() = ctx.almacen.edit {
        it.clear()
        it[onboarding] = true
        it[privacidad] = com.yokis.uniship.ui.AvisoPrivacidad.VERSION
    }

    suspend fun sesionMl(): SesionMl? {
        val d = ctx.almacen.data.first()
        val r = d[mlRefresco] ?: return null
        return SesionMl(d[mlAcceso].orEmpty(), r, d[mlExpira] ?: 0L, d[mlUsuario] ?: 0L)
    }

    suspend fun guardarMl(s: SesionMl) = ctx.almacen.edit {
        it[mlAcceso] = s.acceso
        it[mlRefresco] = s.refresco
        it[mlExpira] = s.expira
        it[mlUsuario] = s.usuario
    }

    suspend fun borrarMl() = ctx.almacen.edit {
        it.remove(mlAcceso); it.remove(mlRefresco); it.remove(mlExpira); it.remove(mlUsuario)
    }

    suspend fun guardarVerificador(v: String?) = ctx.almacen.edit { if (v == null) it.remove(mlVerificador) else it[mlVerificador] = v }
    suspend fun verificador() = ctx.almacen.data.first()[mlVerificador]

    val celular = ctx.almacen.data.map { it[cel4].orEmpty() }
    suspend fun ultimos4() = ctx.almacen.data.first()[cel4]?.takeIf { it.length == 4 }
    suspend fun guardarCelular(v: String) = ctx.almacen.edit { if (v.length == 4) it[cel4] = v else it.remove(cel4) }

    suspend fun marcarRespaldo(t: Long) = ctx.almacen.edit { it[respaldo] = t }
    suspend fun respaldoEn() = ctx.almacen.data.first()[respaldo] ?: 0L
}
