package com.lrlr.carmigo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * 화면이 닫혀 있어도 기록이 이어지게 프로세스를 살려 두는 서비스.
 * 일은 Controller가 한다. 첫 입력을 하기 전이나 기록을 지운 뒤에는 뜨지 않는다.
 */
class RecorderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_watch, getString(R.string.app_name)), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_watching))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, n)
        } catch (e: Exception) {
            Log.w(TAG, "foreground start refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!(application as DaysApp).controller.isRecording()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
        private const val TAG = "DaysRecorder"
        private const val CHANNEL = "watch"
        private const val ID = 1

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RecorderService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "service start refused", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecorderService::class.java))
        }
    }
}
