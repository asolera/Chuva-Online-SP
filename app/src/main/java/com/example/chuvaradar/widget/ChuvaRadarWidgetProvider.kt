package com.example.chuvaradar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.chuvaradar.background.RadarAlarmReceiver
import com.example.chuvaradar.data.NowcastEstimate
import com.example.chuvaradar.data.RadarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChuvaRadarWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.chuvaradar.ACTION_REFRESH_WIDGET"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            nowcast: NowcastEstimate?,
            spRainPercent: Float?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_chuva_radar)

            // Intent para abrir o App ao tocar no widget
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // Intent para o botão de atualizar
            val refreshIntent = Intent(context, ChuvaRadarWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

            if (nowcast != null) {
                // Endereço ativo
                views.setTextViewText(R.id.widget_address_text, "📍 " + nowcast.addressLabel)

                if (nowcast.isDataOutdated) {
                    views.setTextViewText(R.id.widget_status_text, "⚠️ Radar desatualizado")
                } else {
                    // Status resumido para 1 linha
                    val shortStatus = when {
                        nowcast.isRainingNow -> "🌧️ Chovendo agora"
                        nowcast.estimatedArrivalMinutes != null -> "🌧️ Chuva em ~${nowcast.estimatedArrivalMinutes}m"
                        else -> "☀️ Sem chuva"
                    }
                    views.setTextViewText(R.id.widget_status_text, shortStatus)
                }

                // Linha do tempo dos próximos 60 min (+0m, +15m, +30m, +45m, +60m)
                val stepsLabels = listOf(
                    R.id.widget_t0_label, R.id.widget_t1_label, R.id.widget_t2_label,
                    R.id.widget_t3_label, R.id.widget_t4_label
                )
                val stepsBoxes = listOf(
                    R.id.widget_t0_box, R.id.widget_t1_box, R.id.widget_t2_box,
                    R.id.widget_t3_box, R.id.widget_t4_box
                )
                val stepsTimes = listOf(
                    R.id.widget_t0_time, R.id.widget_t1_time, R.id.widget_t2_time,
                    R.id.widget_t3_time, R.id.widget_t4_time
                )

                nowcast.timeline.take(5).forEachIndexed { idx, step ->
                    if (idx < stepsLabels.size) {
                        views.setTextViewText(stepsLabels[idx], "+${step.minutesAhead}m")
                        val probText = if (step.intensity.isRaining && !nowcast.isDataOutdated) "${step.probabilityPercent}%" else "--"
                        views.setTextViewText(stepsBoxes[idx], probText)
                        views.setTextViewText(stepsTimes[idx], step.timeLabel)

                        val bgRes = if (step.intensity.isRaining && !nowcast.isDataOutdated) R.drawable.widget_box_rain else R.drawable.widget_box_none
                        views.setInt(stepsBoxes[idx], "setBackgroundResource", bgRes)
                    }
                }

                // Linha de rodapé
                val spCoverage = spRainPercent ?: 0f
                if (nowcast.isDataOutdated) {
                    views.setTextViewText(
                        R.id.widget_coverage_text,
                        "Radar IAG: ${nowcast.lastScanTimeText} (${nowcast.dataAgeDescription}) • Alertas pausados"
                    )
                } else {
                    views.setTextViewText(
                        R.id.widget_coverage_text,
                        "Radar IAG: ${nowcast.lastScanTimeText} • SP: ${spCoverage.toInt()}% com chuva"
                    )
                }
            } else {
                views.setTextViewText(R.id.widget_address_text, "📍 Radar IAG-USP (36km)")
                views.setTextViewText(R.id.widget_status_text, "Analisando...")
                views.setTextViewText(R.id.widget_coverage_text, "Toque no widget para abrir")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context, nowcast: NowcastEstimate?, spRainPercent: Float?) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ChuvaRadarWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (widgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, widgetId, nowcast, spRainPercent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = RadarRepository.getInstance(context)
                val state = repository.uiState.value
                val nowcast = state.nowcast ?: repository.refreshRadar().getOrNull()
                val spPercent = state.cityCoverage?.totalRainPercent

                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId, nowcast, spPercent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = RadarRepository.getInstance(context)
                    val result = repository.refreshRadar()
                    val state = repository.uiState.value
                    updateAllWidgets(context, result.getOrNull(), state.cityCoverage?.totalRainPercent)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RadarAlarmReceiver.scheduleAlarm(context)
    }
}
