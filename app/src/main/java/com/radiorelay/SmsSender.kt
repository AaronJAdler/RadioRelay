package com.radiorelay

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager

object SmsSender {
    const val EXTRA_LOG_ID = "log_id"

    /** Sends a normal SMS in the background. It also shows up in your Messages app. */
    fun send(ctx: Context, number: String, text: String, logId: Long) {
        val maybeSms: SmsManager? = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val sms = maybeSms ?: throw IllegalStateException("this phone can't send SMS")
        val intent = Intent(ctx, SmsResultReceiver::class.java).putExtra(EXTRA_LOG_ID, logId)
        val sentIntent = PendingIntent.getBroadcast(
            ctx,
            logId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        sms.sendTextMessage(number, null, text, sentIntent, null)
    }
}

/** Gets told by the phone whether each text actually went out, and updates the log. */
class SmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(SmsSender.EXTRA_LOG_ID, -1L)
        if (id < 0) return
        val status = when (resultCode) {
            Activity.RESULT_OK -> "Sent ✓"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "Failed — no cell service"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Failed — airplane mode / radio off"
            SmsManager.RESULT_ERROR_NULL_PDU -> "Failed — message couldn't be built"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Failed — carrier rejected it"
            else -> "Failed (code $resultCode)"
        }
        Prefs(context).updateLogStatus(id, status)
    }
}
