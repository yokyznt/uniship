package com.yokis.uniship

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yokis.uniship.data.Evento
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Registro

object Notificador {
    const val CANAL = "movimientos"
    const val EXTRA_PAQUETE = "paquete"

    fun titulo(p: Paquete, e: Evento): String {
        val quien = p.tienda ?: Registro.porId(p.paqueteria)?.nombre ?: p.titulo
        return "$quien · ${e.estado.texto}"
    }

    fun linea(e: Evento) = listOfNotNull(e.ubicacion, e.descripcion).distinct().joinToString(" · ")

    fun movimientos(ctx: Context, p: Paquete, nuevos: List<Evento>) {
        if (nuevos.isEmpty()) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val limite = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val orden = nuevos.filter { it.fecha > limite }.sortedByDescending { it.fecha }
        if (orden.isEmpty()) return
        val ultimo = orden.first()
        val abrir = PendingIntent.getActivity(
            ctx, p.id.toInt(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_PAQUETE, p.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val estilo = NotificationCompat.InboxStyle().setSummaryText(p.titulo)
        orden.take(5).forEach { estilo.addLine(linea(it)) }
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFFF26B12.toInt())
            .setContentTitle(titulo(p, ultimo))
            .setContentText(linea(ultimo))
            .setSubText(p.titulo)
            .setStyle(if (orden.size > 1) estilo else NotificationCompat.BigTextStyle().bigText(linea(ultimo)))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setGroup("uniship")
            .setWhen(minOf(ultimo.fecha, System.currentTimeMillis()))
            .setShowWhen(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify("paquete", p.id.toInt(), n) }
    }
}
