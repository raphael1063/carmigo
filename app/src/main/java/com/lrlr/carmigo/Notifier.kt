package com.lrlr.carmigo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 알림 세 가지: 화면을 열지 않은 사이에 남은 새 순간, 휴대폰에서 저장을 확인했다는 것(둘 다 주차 중),
 * 그리고 달리는 중 이정표 3km 전에 음성 안내와 함께 잠깐 내려오는 한 줄(몇 초 뒤 저절로 사라진다).
 * 달리는 동안 카미고 화면을 앞으로 꺼내지 않는다. 마지막 주행을 먼저 꺼내는 알림은 없다.
 * Pleos는 종류(category)를 메시지로 준 알림만 화면 위에 띄운다(에뮬레이터에서 확인).
 */
class Notifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    /** 운전자가 알림을 허용했는지 */
    fun enabled(): Boolean = nm.areNotificationsEnabled()

    private fun channel() {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_moments), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun post(id: Int, title: String, text: String, timeoutMs: Long = 0) {
        if (!enabled()) return
        try {
            channel()
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setLargeIcon(android.graphics.drawable.Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(open)
                .setAutoCancel(true)
                .apply { if (timeoutMs > 0) setTimeoutAfter(timeoutMs) }
                .build()
            nm.notify(id, n)
        } catch (e: Exception) {
            Log.w(TAG, "notify failed", e)
        }
    }

    /** 방금 남은 순간의 제목들. 여러 장이면 첫 장과 장수만 적는다. */
    fun moments(titles: List<String>) {
        if (titles.isEmpty()) return
        val text = if (titles.size == 1) titles[0] else "${titles[0]} 외 ${titles.size - 1}장"
        post(ID_MOMENT, context.getString(R.string.notify_moment), text)
    }

    fun saved(canWipe: Boolean) {
        post(
            ID_SAVED, context.getString(R.string.notify_saved),
            context.getString(if (canWipe) R.string.notify_saved_wipe else R.string.notify_saved_plain),
        )
    }

    /** 이정표 3km 전: 음성 안내와 같은 문장을 화면 위에 한 줄로 잠깐 띄운다. */
    fun ahead(speech: String) {
        post(ID_AHEAD, context.getString(R.string.app_name), speech.replace("킬로미터", "km"), AHEAD_MS)
    }

    /** 화면을 열면 알림은 할 일을 다했다 */
    fun clear() {
        try { nm.cancel(ID_MOMENT); nm.cancel(ID_SAVED) } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "DaysNotifier"
        private const val CHANNEL = "moments"
        private const val ID_MOMENT = 2
        private const val ID_SAVED = 3
        private const val ID_AHEAD = 4
        private const val AHEAD_MS = 8_000L
    }
}
