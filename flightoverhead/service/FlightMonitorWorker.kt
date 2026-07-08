package com.flightoverhead.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flightoverhead.MainActivity
import com.flightoverhead.R
import com.flightoverhead.data.FlightRepository

class FlightMonitorWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME    = "flight_monitor"
        private const val CHANNEL_ID = "flights_overhead"
        private const val NOTIF_ID   = 1001
    }

    override suspend fun doWork(): Result {
        return try {
            val flights = FlightRepository.getFlightsOverhead()
            if (flights.isNotEmpty()) {
                createChannel()
                val body = flights.take(3).joinToString(" · ") {
                    "${it.callsignClean} ${it.altitudeFt}ft"
                } + if (flights.size > 3) " +${flights.size - 3} more" else ""
                notify("${flights.size} flights overhead", body)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createChannel() {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Flights Overhead",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when aircraft pass over your home"
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun notify(title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_plane)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, notif)
    }
}
