package com.yokis.uniship.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yokis.uniship.MainActivity
import com.yokis.uniship.UniShipApp
import kotlinx.coroutines.launch

@Composable
fun Raiz(actividad: MainActivity) {
    val listo by UniShipApp.prefs.onboardingListo.collectAsState(initial = null)
    val nav = rememberNavController()
    val alcance = rememberCoroutineScope()
    val pendiente by actividad.abrirPaquete.collectAsState()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val l = listo ?: return@Box
        val inicio = remember { if (l) "lista" else "bienvenida" }
        NavHost(nav, startDestination = inicio) {
            composable("bienvenida") {
                Onboarding(alTerminar = {
                    alcance.launch { UniShipApp.prefs.terminarOnboarding() }
                    nav.navigate("lista") { popUpTo("bienvenida") { inclusive = true } }
                })
            }
            composable("lista") {
                Lista(
                    abrir = { nav.navigate("detalle/$it") },
                    agregar = { nav.navigate("agregar") },
                    ajustes = { nav.navigate("ajustes") }
                )
            }
            composable("detalle/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                Detalle(id = it.arguments?.getLong("id") ?: 0, volver = { nav.popBackStack() })
            }
            composable("agregar") { entrada ->
                val escaneada by entrada.savedStateHandle.getStateFlow<String?>("guia", null).collectAsState()
                Agregar(
                    escaneada = escaneada,
                    escanear = { nav.navigate("escanear") },
                    volver = { nav.popBackStack() },
                    guardado = { id -> nav.navigate("detalle/$id") { popUpTo("lista") } }
                )
            }
            composable("escanear") {
                Escaner(
                    leido = { guia ->
                        nav.previousBackStackEntry?.savedStateHandle?.set("guia", guia)
                        nav.popBackStack()
                    },
                    volver = { nav.popBackStack() }
                )
            }
            composable("ajustes") { Ajustes(actividad, volver = { nav.popBackStack() }) }
        }
        LaunchedEffect(pendiente, listo) {
            val id = pendiente ?: return@LaunchedEffect
            if (listo == true) {
                nav.navigate("detalle/$id")
                actividad.abrirPaquete.value = null
            }
        }
    }
}
