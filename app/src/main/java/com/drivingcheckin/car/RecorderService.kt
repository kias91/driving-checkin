package com.drivingcheckin.car

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * 화면이 꺼지거나 다른 앱으로 넘어가도 차량 신호 리스너를 유지한다.
 * 콜백이 올 때만 일하고 스스로 폴링하지 않는다.
 */
class RecorderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "운전 기록", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("운전 안부")
            .setContentText("운전 기록을 차 안에 남기고 있어요")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        (application as App).recorder.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_FROM_BOOT, false) == true) {
            getSharedPreferences("recorder", Context.MODE_PRIVATE).edit()
                .putLong("autoStartedAt", System.currentTimeMillis()).apply()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        (application as App).recorder.flush()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "recorder"
        const val EXTRA_FROM_BOOT = "fromBoot"

        fun start(context: Context, fromBoot: Boolean = false) {
            context.startForegroundService(
                Intent(context, RecorderService::class.java).putExtra(EXTRA_FROM_BOOT, fromBoot)
            )
        }
    }
}
