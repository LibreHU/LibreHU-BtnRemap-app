package org.librehu.btnremap

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librehu.btnremap.data.Action
import org.librehu.btnremap.data.ActionType
import org.librehu.btnremap.data.ButtonMapping
import org.librehu.btnremap.data.HuKey
import org.librehu.btnremap.data.MappingStore
import org.librehu.btnremap.data.PressType
import org.librehu.btnremap.engine.ActionRunner
import org.librehu.btnremap.engine.PressDetector
import org.librehu.btnremap.headunit.HeadUnit

/** Receives the button presses from the head unit and runs the chosen actions. */
class RemapService : Service() {
    private lateinit var store: MappingStore
    private lateinit var headUnit: HeadUnit
    private lateinit var runner: ActionRunner
    private val detector = PressDetector(::onPress)

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        store = MappingStore.get(this)
        headUnit = HeadUnit.create(this)
        runner = ActionRunner(this, headUnit)
        _sourceName.value = headUnit.name
        _ownsKeys.value = headUnit.ownsKeys
        headUnit.start(::onKey)
    }

    private fun onKey(key: HuKey) {
        store.seen(key)
        val m = store.get(key.id) ?: return
        if (!handles(m)) return
        if (key.down) detector.down(m) else detector.up(m)
    }

    /** A button is handled when mapped, or when we own the keys and it has a default action. */
    private fun handles(m: ButtonMapping): Boolean =
        m.isMapped ||
            (
                headUnit.ownsKeys &&
                    (headUnit.defaultAction(m.id, PressType.SHORT) != null || headUnit.defaultAction(m.id, PressType.LONG) != null)
            )

    private fun onPress(
        m: ButtonMapping,
        press: PressType,
    ) {
        val action = m.action(press)
        if (action.type == ActionType.DEFAULT) {
            if (headUnit.ownsKeys) headUnit.defaultAction(m.id, press)?.let { runner.run(Action(it)) }
            return
        }
        runner.run(action)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        headUnit.stop()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN))
        val n =
            Notification
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_wheel)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(true)
                .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    companion object {
        private const val CHANNEL_ID = "remap"
        private val _sourceName = MutableStateFlow("")
        private val _ownsKeys = MutableStateFlow(true)
        val sourceName: StateFlow<String> = _sourceName.asStateFlow()
        val ownsKeys: StateFlow<Boolean> = _ownsKeys.asStateFlow()

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RemapService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
