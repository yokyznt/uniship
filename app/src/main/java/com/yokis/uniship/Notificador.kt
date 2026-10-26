package com.yokis.uniship

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yokis.uniship.data.Estado
import com.yokis.uniship.data.Evento
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Registro
import com.yokis.uniship.ui.pasoDe

object Notificador {
    const val CANAL = "movimientos"
    const val CANAL_AVISOS = "avisos"
    const val EXTRA_PAQUETE = "paquete"
    private const val NARANJA = 0xFFE05A00.toInt()
    private const val GRIS = 0xFFF2EEEA.toInt()

    fun titulo(p: Paquete, e: Evento): String {
        val quien = p.tienda ?: Registro.porId(p.paqueteria)?.nombre ?: p.titulo
        return "$quien · ${e.estado.texto}"
    }

    fun linea(e: Evento) = if (e.ubicacion != null && !e.descripcion.contains(e.ubicacion, ignoreCase = true)) "${e.ubicacion} · ${e.descripcion}" else e.descripcion

    private fun permitido(ctx: Context) = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun abrir(ctx: Context, p: Paquete) = PendingIntent.getActivity(
        ctx, p.id.toInt(),
        Intent(ctx, MainActivity::class.java).putExtra(EXTRA_PAQUETE, p.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun progreso(e: Estado): NotificationCompat.ProgressStyle {
        val paso = pasoDe(e)
        val total = 300
        val valor = when (e) {
            Estado.ENTREGADO -> total
            Estado.SIN_DATOS -> 0
            else -> paso * 100 + 55
        }
        return NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments((0 until 3).map { NotificationCompat.ProgressStyle.Segment(100).setColor(NARANJA) })
            .setProgressPoints((1..2).map { NotificationCompat.ProgressStyle.Point(it * 100).setColor(if (paso >= it) NARANJA else GRIS) })
            .setProgress(valor)
            .setProgressTrackerIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(UniShipApp.instancia, R.drawable.ic_notificacion))
    }

    fun movimientos(ctx: Context, p: Paquete, nuevos: List<Evento>) {
        com.yokis.uniship.widget.Widget.refrescar(ctx)
        if (nuevos.isEmpty() || !permitido(ctx)) return
        val limite = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val orden = nuevos.filter { it.fecha > limite }.sortedByDescending { it.fecha }
        if (orden.isEmpty()) return
        val ultimo = orden.first()
        val enReparto = p.estado == Estado.EN_REPARTO
        val b = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(NARANJA)
            .setContentTitle(titulo(p, ultimo))
            .setContentText(linea(ultimo))
            .setSubText(p.titulo)
            .setStyle(progreso(p.estado))
            .setContentIntent(abrir(ctx, p))
            .addAction(R.drawable.ic_notificacion, "Ver ruta", abrir(ctx, p))
            .setAutoCancel(!enReparto)
            .setOngoing(enReparto)
            .setGroup("uniship")
            .setWhen(minOf(ultimo.fecha, System.currentTimeMillis()))
            .setShowWhen(true)
            .setShortCriticalText(if (enReparto) "Hoy" else p.estado.texto)
        if (enReparto) b.setRequestPromotedOngoing(true)
        p.entregaEstimada?.let { b.setSubText("${p.titulo} · llega $it") }
        runCatching { NotificationManagerCompat.from(ctx).notify("paquete", p.id.toInt(), b.build()) }
    }

    fun sinGuia(ctx: Context, p: Paquete, texto: String) {
        if (!permitido(ctx)) return
        val app = com.yokis.uniship.lector.Fuentes.paqueteDe(p.tienda)?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
        val b = NotificationCompat.Builder(ctx, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(NARANJA)
            .setContentTitle("Tu pedido de ${p.tienda} ya va en camino")
            .setContentText("Copia la guía en ${p.tienda} y abre UniShip para rastrearlo")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$texto\n\nCopia la guía en ${p.tienda} y abre UniShip: se agrega sola."))
            .setContentIntent(abrir(ctx, p))
            .setAutoCancel(true)
        if (app != null) b.addAction(R.drawable.ic_notificacion, "Abrir ${p.tienda}", PendingIntent.getActivity(ctx, 9000 + p.id.toInt(), app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
        runCatching { NotificationManagerCompat.from(ctx).notify("singuia", p.id.toInt(), b.build()) }
    }
}
