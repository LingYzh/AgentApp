package com.example.myapplication.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-started task lifetime and an always reachable notification stop control. */
class DeviceTaskService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "设备控制任务", NotificationManager.IMPORTANCE_LOW)
            )
        }
        service = this
        updateNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) stopAll()
        if (jobs.isEmpty()) stopSelf() else updateNotification()
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, DeviceTaskService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("UAH 设备任务运行中")
            .setContentText("点击返回 UAH；可随时停止全部设备任务")
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "停止任务", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION, notification)
    }

    override fun onDestroy() {
        service = null
        stopAll()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "device_tasks"
        private const val NOTIFICATION = 4501
        private const val STOP = "com.Ling.actant.STOP_DEVICE_TASKS"
        private val jobs = linkedMapOf<String, Job>()
        private var service: DeviceTaskService? = null
        private val _active = MutableStateFlow(0)
        val active = _active.asStateFlow()

        /** Called on Main before a lazy job starts, while the user is in the app. */
        fun register(context: Context, token: String, job: Job) {
            jobs[token] = job
            _active.value = jobs.size
            try {
                ContextCompat.startForegroundService(context, Intent(context, DeviceTaskService::class.java))
            } catch (e: Exception) {
                jobs.remove(token)
                _active.value = jobs.size
                throw e
            }
        }

        fun finish(token: String) {
            jobs.remove(token)
            _active.value = jobs.size
            if (jobs.isEmpty()) service?.stopSelf()
        }

        fun stopAll() {
            jobs.values.toList().forEach { it.cancel() }
        }
    }
}
