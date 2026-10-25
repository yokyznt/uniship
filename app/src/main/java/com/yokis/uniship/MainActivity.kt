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

    private fun recibir(i: Intent?) {
        if (i == null) return
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
