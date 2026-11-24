package com.example.chuvaradar.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.chuvaradar.data.NowcastEstimate

object RadarNotificationHelper {

    const val CHANNEL_ID = "chuva_radar_alerts"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.notification_channel_name)
                val descriptionText = context.getString(R.string.notification_channel_desc)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                }
                val notificationManager: NotificationManager? =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Log.e("RadarNotifHelper", "Erro ao criar canal: ${e.message}")
        }
    }

    fun showRainAlertNotification(context: Context, nowcast: NowcastEstimate) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = when {
                nowcast.isRainingNow -> "🌧️ Chuva agora em ${nowcast.addressLabel}!"
                nowcast.estimatedArrivalMinutes != null -> "⚠️ Chuva em ~${nowcast.estimatedArrivalMinutes} min em ${nowcast.addressLabel}!"
                else -> "Aviso Meteorológico Radar IAG-USP"
            }

            val text = nowcast.statusDetail

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val manager = NotificationManagerCompat.from(context)
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e("RadarNotifHelper", "Aviso ao exibir notificação: ${e.message}")
        }
    }
}
