package com.yokis.uniship.cuenta

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.yokis.uniship.BuildConfig
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Respaldo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.yokis.uniship.paqueterias.Http

object Nube {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val CADA = 12 * 60 * 60 * 1000L

    fun disponible(ctx: Context = UniShipApp.instancia) = BuildConfig.FIREBASE && FirebaseApp.getApps(ctx).isNotEmpty()

    val usuario: FirebaseUser? get() = if (disponible()) FirebaseAuth.getInstance().currentUser else null

    suspend fun iniciar(activity: Activity): FirebaseUser {
        if (!disponible(activity)) error("Falta configurar Firebase en esta versión")
        val idWeb = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (idWeb == 0) error("Falta el client ID web de Firebase")
        val opcion = GetSignInWithGoogleOption.Builder(activity.getString(idWeb)).build()
        val req = GetCredentialRequest.Builder().addCredentialOption(opcion).build()
        val cred = CredentialManager.create(activity).getCredential(activity, req).credential
        if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) error("Credencial no válida")
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        return FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user
            ?: error("No se pudo iniciar sesión")
    }

    suspend fun cerrar(ctx: Context) {
        if (!disponible(ctx)) return
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(ctx).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
    }

    private suspend fun token(): String {
        val u = usuario ?: error("Inicia sesión primero")
        return u.getIdToken(false).await().token ?: error("Sin token")
    }

    suspend fun subir() {
        val datos = json.encodeToString(Respaldo.serializer(), UniShipApp.repo.respaldo())
        val t = token()
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("${BuildConfig.SERVER_URL}/api/respaldo")
                .header("Authorization", "Bearer $t")
                .put(datos.toRequestBody("application/json".toMediaType()))
                .build()
            Http.cliente.newCall(req).execute().use { if (!it.isSuccessful) error("El servidor respondió ${it.code}") }
        }
        UniShipApp.prefs.marcarRespaldo(System.currentTimeMillis())
    }

    suspend fun restaurar(): Int {
        val t = token()
        val cuerpo = withContext(Dispatchers.IO) {
            val req = Request.Builder().url("${BuildConfig.SERVER_URL}/api/respaldo").header("Authorization", "Bearer $t").get().build()
            Http.cliente.newCall(req).execute().use {
                if (it.code == 404) return@withContext null
                if (!it.isSuccessful) error("El servidor respondió ${it.code}")
                it.body?.string()
            }
        } ?: return 0
        val r = json.decodeFromString(Respaldo.serializer(), cuerpo)
        UniShipApp.repo.restaurar(r)
        return r.paquetes.size
    }

    suspend fun borrar() {
        val t = token()
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("${BuildConfig.SERVER_URL}/api/respaldo").header("Authorization", "Bearer $t").delete().build()
            Http.cliente.newCall(req).execute().use { if (!it.isSuccessful) error("El servidor respondió ${it.code}") }
        }
        UniShipApp.prefs.marcarRespaldo(0)
    }

    suspend fun respaldoAutomatico() {
        if (usuario == null) return
        if (System.currentTimeMillis() - UniShipApp.prefs.respaldoEn() < CADA) return
        subir()
    }
}
