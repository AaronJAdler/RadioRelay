package com.radiorelay

import android.app.Notification
import androidx.core.app.NotificationCompat

data class IncomingMessage(val sender: String?, val text: String, val time: Long)

/** What a messaging notification tells us: which chat, and the messages in it. */
data class ChatSnapshot(val title: String?, val isGroup: Boolean, val messages: List<IncomingMessage>)

object NotificationReader {

    /**
     * Google Messages, Samsung Messages, WhatsApp etc. post message notifications in
     * "MessagingStyle", which carries the chat name plus each message's sender, text and time.
     * Falls back to the plain title/text for apps that don't.
     */
    fun read(n: Notification, postTime: Long): ChatSnapshot? {
        val extras = n.extras

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (style != null) {
            val title = style.conversationTitle?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val messages = style.messages.mapNotNull { m ->
                val text = m.text?.toString() ?: return@mapNotNull null
                IncomingMessage(m.person?.name?.toString(), text, m.timestamp)
            }
            return ChatSnapshot(title, style.isGroupConversation, messages)
        }

        if (n.category != Notification.CATEGORY_MESSAGE) return null
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: return null
        return ChatSnapshot(title?.toString(), false, listOf(IncomingMessage(null, text.toString(), postTime)))
    }
}
