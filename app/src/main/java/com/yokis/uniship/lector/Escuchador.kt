package com.yokis.uniship.lector

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class Escuchador : NotificationListenerService() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.packageName !in Fuentes.apps) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val e = n.extras
        val titulo = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val cuerpo = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val lectura = Detector.interpretar(sbn.packageName, titulo, cuerpo) ?: return
        val cuando = if (n.`when` > 0) n.`when` else sbn.postTime
        alcance.launch { runCatching { Detector.procesar(applicationContext, lectura, cuando) } }
    }

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }
}
