package com.rishit.blemesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Foreground service that keeps scanning and relaying while the app is in the background. */
class MeshService : Service() {

    private var transport: BleTransport? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        val t = BleTransport(this) { p -> MeshHub.node?.onReceive(p) }
        val node = MeshNode(t) { text -> MeshHub.post("Rx: $text") }
        transport = t
        MeshHub.node = node
        t.startScan()
        MeshHub.post(
            if (t.extendedSupported) "Mesh on. Extended adverts, max ${t.maxPayloadBytes} bytes per message."
            else "Mesh on. Legacy adverts only, max ${t.maxPayloadBytes} bytes per message."
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        transport?.stop()
        transport = null
        MeshHub.node = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mesh relay", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE Mesh running")
            .setContentText("Relaying nearby messages")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val CHANNEL_ID = "mesh_relay"
        private const val NOTIF_ID = 1
    }
}
