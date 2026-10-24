package com.yokis.uniship

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.yokis.uniship.data.Db
import com.yokis.uniship.data.Repositorio
import com.yokis.uniship.paqueterias.Navegador
import com.yokis.uniship.trabajo.Rastreador

class UniShipApp : Application() {
    lateinit var repo: Repositorio
    lateinit var prefs: Preferencias

    override fun onCreate() {
        super.onCreate()
        instancia = this
        Navegador.app = this
        repo = Repositorio(Db.crear(this))
        prefs = Preferencias(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Notificador.CANAL, "Movimientos de paquetes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisa cada vez que un paquete cambia de estado o de ciudad"
            }
        )
        Rastreador.programar(this)
    }

    companion object {
        lateinit var instancia: UniShipApp
        val repo get() = instancia.repo
        val prefs get() = instancia.prefs
    }
}
