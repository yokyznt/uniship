package com.yokis.uniship

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.yokis.uniship.mercadolibre.MercadoLibre
import com.yokis.uniship.trabajo.Rastreador
import com.yokis.uniship.ui.Raiz
import com.yokis.uniship.ui.UniShipTema
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    val abrirPaquete = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        recibir(intent)
        setContent { UniShipTema { Raiz(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recibir(intent)
    }

    override fun onResume() {
        super.onResume()
        val ahora = System.currentTimeMillis()
        if (ahora - ultimaRevision > 5 * 60_000) {
            ultimaRevision = ahora
            Rastreador.ahora(this)
        }
    }

    companion object {
        private var ultimaRevision = 0L
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        lifecycleScope.launch {
            avisar(runCatching { com.yokis.uniship.lector.Compartido.revisarPortapapeles(this@MainActivity) }.getOrDefault(emptyList()))
        }
    }

    private fun avisar(nuevos: List<com.yokis.uniship.data.Paquete>) {
        if (nuevos.isEmpty()) return
        val texto = if (nuevos.size == 1) "Agregué la guía ${nuevos[0].guia}" else "Agregué ${nuevos.size} guías"
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
        if (nuevos.size == 1) abrirPaquete.value = nuevos[0].id
    }

    private fun recibir(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND) {
            val texto = listOfNotNull(i.getStringExtra(Intent.EXTRA_SUBJECT), i.getStringExtra(Intent.EXTRA_TEXT)).joinToString("\n")
            i.action = null
            lifecycleScope.launch {
                val nuevos = com.yokis.uniship.lector.Compartido.agregar(this@MainActivity, texto, com.yokis.uniship.data.Origen.COMPARTIDO)
                if (nuevos.isEmpty()) Toast.makeText(this@MainActivity, "No encontré una guía nueva en lo que compartiste", Toast.LENGTH_LONG).show() else avisar(nuevos)
            }
        }
        val id = i.getLongExtra(Notificador.EXTRA_PAQUETE, -1)
        if (id > 0) abrirPaquete.value = id
        val datos = i.data
        if (datos != null && datos.scheme == "uniship" && datos.host == "ml") {
            i.data = null
            lifecycleScope.launch {
                runCatching { MercadoLibre.completar(datos) }
                    .onSuccess {
                        Toast.makeText(this@MainActivity, "Mercado Libre conectado", Toast.LENGTH_SHORT).show()
                        Rastreador.ahora(this@MainActivity)
                    }
                    .onFailure { Toast.makeText(this@MainActivity, it.message ?: "No se pudo conectar", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
