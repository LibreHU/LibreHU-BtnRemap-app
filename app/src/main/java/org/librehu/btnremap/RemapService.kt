package org.librehu.btnremap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librehu.btnremap.data.Action
import org.librehu.btnremap.data.ActionType
import org.librehu.btnremap.data.ButtonMapping
import org.librehu.btnremap.data.HuKey
import org.librehu.btnremap.data.KeyLog
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
    private val detector =
        PressDetector(
            comboFor = { a, b -> store.get(MappingStore.comboId(a, b)) },
            onCombo = { a, b -> store.comboSeen(a, b) },
            onPress = ::onPress,
        )

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        store = MappingStore.get(this)
        store.publishClaims()
        headUnit = HeadUnit.create(this)
        runner = ActionRunner(this, headUnit)
        _sourceName.value = headUnit.name
        _ownsKeys.value = headUnit.ownsKeys
        defaults = headUnit::defaultAction
        // Ignition on (start, or screen on after standby): some cars send a button press by themselves then (Clio 3)
        guard()
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
        headUnit.start(::onKey)
    }

    private fun onKey(key: HuKey) {
        store.seen(key)
        log(key.id, if (key.down) KeyLog.DOWN else KeyLog.UP)
        if (key.down && SystemClock.elapsedRealtime() < ignoreUntil) {
            Log.i("LibreHU-BtnRemap", "${key.id} ignored (head unit just started)")
            log(key.id, KeyLog.IGNORED)
            return
        }
        val m = store.mappingFor(key.id) ?: return
        // Every button goes through the detector: one with no action of its own can still be half of a combination
        if (key.down) detector.down(m, key.pulsed) else detector.up(m, key.pulsed)
    }

    /** Until when the buttons are ignored (elapsedRealtime), after the start and when the screen comes on. */
    private var ignoreUntil = 0L

    private fun guard() {
        val s = ignoreAfterOnS(this)
        ignoreUntil = if (s > 0) SystemClock.elapsedRealtime() + s * 1000L else 0L
    }

    private val screenOn =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = guard()
        }

    private fun onPress(
        m: ButtonMapping,
        press: PressType,
    ) {
        val action = m.action(press)
        val run =
            if (action.type == ActionType.DEFAULT) {
                if (headUnit.ownsKeys) headUnit.defaultAction(m.id, press)?.let { Action(it) } else null
            } else {
                action
            }
        log(m.id, if (press == PressType.LONG) KeyLog.LONG else if (m.id.startsWith(MappingStore.COMBO)) KeyLog.COMBO else KeyLog.SHORT, run?.type)
        // Key test: what would run, nothing runs
        if (run == null || _testMode.value) return
        runner.run(run)
    }

    private fun log(
        id: String,
        kind: Int,
        action: ActionType? = null,
    ) {
        val name = store.get(id)?.name ?: store.lastKey.value?.takeIf { it.id == id }?.defaultName ?: id
        _events.value = (listOf(KeyLog(SystemClock.elapsedRealtime(), id, name, kind, action)) + _events.value).take(MAX_EVENTS)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOn) }
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

        private const val MAX_EVENTS = 40
        private val _events = MutableStateFlow<List<KeyLog>>(emptyList())

        /** Last button events, newest first (key test). */
        val events: StateFlow<List<KeyLog>> = _events.asStateFlow()

        private val _testMode = MutableStateFlow(false)

        /** Key test: presses are shown with what they would do, and no action runs. Off at each start. */
        val testMode: StateFlow<Boolean> = _testMode.asStateFlow()

        fun setTestMode(on: Boolean) {
            _testMode.value = on
            if (on) _events.value = emptyList()
        }

        /** What an unmapped button does (the head unit's default action), for the screen. */
        @Volatile
        var defaults: (String, PressType) -> ActionType? = { _, _ -> null }

        private const val PREF_IGNORE = "ignore_after_on_s"
        const val IGNORE_DEFAULT_S = 5

        /** Seconds the buttons are ignored after the head unit starts or wakes (ignition on). */
        fun ignoreAfterOnS(context: Context): Int =
            context.getSharedPreferences("mappings", Context.MODE_PRIVATE).getInt(PREF_IGNORE, IGNORE_DEFAULT_S)

        fun setIgnoreAfterOnS(
            context: Context,
            s: Int,
        ) = context.getSharedPreferences("mappings", Context.MODE_PRIVATE).edit().putInt(PREF_IGNORE, s).apply()

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RemapService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
