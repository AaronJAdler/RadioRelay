package com.radiorelay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Decides what to text, guards against repeats, and hands off to [SmsSender]. */
object Relay {

    /** Ignore anything older than this, e.g. old messages replayed after a phone restart. */
    private const val STALE_MS = 15 * 60_000L

    fun onChatMessages(ctx: Context, snapshot: ChatSnapshot, sourcePackage: String) {
        val prefs = Prefs(ctx)
        val title = snapshot.title?.takeIf { it.isNotBlank() } ?: return
        if (snapshot.messages.isNotEmpty()) prefs.noteChat(title)

        if (!prefs.enabled || !prefs.chatMatches(title)) return

        val now = System.currentTimeMillis()
        for (m in snapshot.messages) {
            if (m.time in 1 until prefs.armedAt) continue
            if (m.time > 0 && now - m.time > STALE_MS) continue

            val key = "${m.time}:${"$sourcePackage|$title|${m.sender}|${m.text}".hashCode()}"
            if (!prefs.markProcessed(key)) continue

            val commands = MessageParser.parse(m.text, prefs.stations)
            if (commands.isNotEmpty()) dispatch(ctx, prefs, title, commands, respectCooldown = true)
        }
    }

    fun dispatch(
        ctx: Context,
        prefs: Prefs,
        chat: String,
        commands: List<SendCommand>,
        respectCooldown: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val canSend = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

        for (c in commands) {
            val number = c.station.number
            val id = prefs.nextLogId()

            if (respectCooldown && !prefs.cooldownAllows(number, c.keyword, now)) {
                prefs.addLog(LogEntry(id, now, chat, number, c.keyword, "Skipped — already sent recently"))
                continue
            }
            if (!canSend) {
                prefs.addLog(LogEntry(id, now, chat, number, c.keyword, "Failed — SMS permission not granted"))
                continue
            }

            prefs.recordSend(number, c.keyword, now)
            prefs.addLog(LogEntry(id, now, chat, number, c.keyword, "Sending…"))
            try {
                SmsSender.send(ctx, number, c.keyword, id)
                Notifier.sent(ctx, c.keyword, number)
            } catch (e: Exception) {
                prefs.updateLogStatus(id, "Failed — ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
