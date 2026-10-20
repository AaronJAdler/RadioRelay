package com.radiorelay

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** A quiet "Texted Minnie to (916) 909-0985" notification so you know it fired. */
object Notifier {
    private const val CHANNEL = "texts_sent"

    @SuppressLint("MissingPermission")
    fun sent(ctx: Context, keyword: String, number: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Texts sent")
                .build()
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_radio)
            .setContentTitle("Texted “$keyword”")
            .setContentText("to ${PhoneUtil.format(number)}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(number.hashCode(), notification)
        } catch (e: SecurityException) {
            // notifications not allowed — the in-app log still has it
        }
    }
}
