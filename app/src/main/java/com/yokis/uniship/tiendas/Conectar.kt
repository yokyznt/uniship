package com.yokis.uniship.tiendas

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.yokis.uniship.ui.UniShipTema
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConectarTienda : ComponentActivity() {
    private var revisando: Job? = null
    private var verificando by mutableStateOf(false)
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tienda = Tienda.valueOf(intent.getStringExtra(TIENDA) ?: Tienda.ALIEXPRESS.name)
        CookieManager.getInstance().setAcceptCookie(true)
        val barra = ComposeView(this).apply {
            setContent {
                UniShipTema {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.statusBarsPadding()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                                IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
                                Text("Entrar a ${tienda.nombre}", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                "Inicia sesión con tu correo o teléfono. Tu contraseña la recibe ${tienda.nombre}, no UniShip. «Continuar con Google» no funciona aquí.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                            if (verificando) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (com.yokis.uniship.BuildConfig.DEBUG) android.util.Log.d("UniShip", "conectar $url")
                    if (url != null && tienda.listo.containsMatchIn(url) && !url.contains("login")) revisar(tienda)
                }
            }
        }
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(barra, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(raiz)
        web.loadUrl(tienda.entrada)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    private fun revisar(t: Tienda) {
        if (revisando?.isActive == true) return
        revisando = lifecycleScope.launch {
            verificando = true
            delay(1500)
            CookieManager.getInstance().flush()
            val ok = when (t) {
                Tienda.ALIEXPRESS -> AliExpress.probar(this@ConectarTienda)
                Tienda.AMAZON -> Amazon.probar(this@ConectarTienda)
                Tienda.TEMU -> true
            }
            verificando = false
            if (!ok) return@launch
            Tiendas.marcar(t, true)
            Toast.makeText(this@ConectarTienda, "${t.nombre} conectada, buscando tus pedidos", Toast.LENGTH_LONG).show()
            com.yokis.uniship.trabajo.Rastreador.ahora(applicationContext)
            finish()
        }
    }

    companion object {
        const val TIENDA = "tienda"
        fun abrir(ctx: Context, t: Tienda) = ctx.startActivity(Intent(ctx, ConectarTienda::class.java).putExtra(TIENDA, t.name))
    }
}
