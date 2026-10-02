package org.librehu.btnremap.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import org.librehu.btnremap.data.ActionType
import org.librehu.btnremap.data.HuKey
import org.librehu.btnremap.data.PressType
import org.librehu.service.ILibreHuCallback
import org.librehu.service.ILibreHuService

/**
 * LibreHU-service: this app is the only one receiving the buttons, so it owns them.
 * - CAN box (Hiworld): frame 0x11, byte 6 & 0x1F = button, byte 7 = state (0 released, 1/2 pressed,
 *   5 knob step), from the MCU 0x10 data;
 * - resistive steering wheel / front panel keys: MCU frame 0x20 (ADC readings, quantised to tell buttons apart).
 */
class LibreHuKeys(
    private val context: Context,
) : HeadUnit {
    override val name = "LibreHU-service"
    override val ownsKeys = true

    @Volatile
    private var service: ILibreHuService? = null
    private var bound = false
    private val main = Handler(Looper.getMainLooper())
    private var onKey: (HuKey) -> Unit = {}
    private var canPressed: Int = 0
    private val adcPressed = mutableMapOf<Int, String>()

    private val hiworld = HiworldParser { cmd, f -> if (cmd == 0x11 && f.size >= 9) onCanKey(f) }

    private val callback =
        object : ILibreHuCallback.Stub() {
            override fun onVehicleFlags(flags: Int) {}

            override fun onAudioChanged() {}

            override fun onMcuFrame(
                cmd: Int,
                data: ByteArray?,
                fromMcu: Boolean,
            ) {}

            override fun onKey(
                channel: Int,
                values: IntArray?,
                released: Boolean,
                learning: Boolean,
            ) {
                if (values != null && !learning) main.post { onAdcKey(channel, values, released) }
            }

            override fun onCanData(data: ByteArray?) {
                if (data != null) main.post { hiworld.feed(data) }
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                val s = binder?.let { ILibreHuService.Stub.asInterface(it) } ?: return
                service = s
                try {
                    s.registerCallback(callback)
                } catch (e: RemoteException) {
                    Log.w(TAG, "LibreHU-service: ${e.message}")
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }

    override fun start(onKey: (HuKey) -> Unit) {
        this.onKey = onKey
        bound =
            try {
                context.bindService(Intent(ACTION_BIND).setPackage(PACKAGE), connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "LibreHU-service: ${e.message}")
                false
            }
    }

    override fun stop() {
        try {
            service?.unregisterCallback(callback)
        } catch (_: RemoteException) {
        }
        if (bound) context.unbindService(connection)
        bound = false
    }

    private fun onCanKey(f: ByteArray) {
        val code = f[6].toInt() and 0x1F
        val state = f[7].toInt() and 0xFF
        when {
            state == STATE_KNOB && code != 0 -> {
                click("can:$code", canName(code))
            }

            code != 0 && state != 0 -> {
                if (canPressed != code) {
                    if (canPressed != 0) onKey(HuKey("can:$canPressed", canName(canPressed), false))
                    canPressed = code
                    onKey(HuKey("can:$code", canName(code), true))
                }
            }

            else -> {
                if (canPressed != 0) onKey(HuKey("can:$canPressed", canName(canPressed), false))
                canPressed = 0
            }
        }
    }

    /** Wheel: (AA, low, mid, high) then (AA, FF, FF, FF); panel: (value, FF, ...) then (FF, ...); knob: (id, direction). */
    private fun onAdcKey(
        channel: Int,
        values: IntArray,
        released: Boolean,
    ) {
        if (channel == 2) {
            if (values.size >= 2) click("knob:${values[0]}:${values[1]}", "Knob ${values[0]} ${if (values[1] == 0) "−" else "+"}")
            return
        }
        if (released) {
            adcPressed.remove(channel)?.let { onKey(HuKey(it, adcName(channel, it), false)) }
            return
        }
        val id =
            if (channel >= 5 && values.size >= 4) {
                "adc:$channel:${values[1] / ADC_STEP}:${values[2] / ADC_STEP}:${values[3] / ADC_STEP}"
            } else {
                "adc:$channel:${values[0] / ADC_STEP}"
            }
        if (adcPressed[channel] == id) return // repeated frames while held
        adcPressed[channel]?.let { onKey(HuKey(it, adcName(channel, it), false)) }
        adcPressed[channel] = id
        onKey(HuKey(id, adcName(channel, id), true))
    }

    private fun click(
        id: String,
        name: String,
    ) {
        onKey(HuKey(id, name, true))
        onKey(HuKey(id, name, false))
    }

    private fun adcName(
        channel: Int,
        id: String,
    ) = (if (channel >= 5) "Wheel " else "Panel ") + id.substringAfter("adc:$channel:")

    private fun canName(code: Int) = CAN_NAMES[code] ?: "CAN $code"

    override fun volume(delta: Int): Boolean {
        val s = service ?: return false
        return try {
            s.setVolume((s.volume + delta).coerceIn(0, s.maxVolume))
            true
        } catch (_: RemoteException) {
            false
        }
    }

    override fun toggleMute(): Boolean {
        val s = service ?: return false
        return try {
            s.setMuted(!s.isMuted)
            true
        } catch (_: RemoteException) {
            false
        }
    }

    /** Usual function of the CAN box buttons when the user has not mapped them. */
    override fun defaultAction(
        id: String,
        press: PressType,
    ): ActionType? {
        if (press != PressType.SHORT || !id.startsWith("can:")) return null
        return when (id.removePrefix("can:").toIntOrNull()) {
            1 -> ActionType.VOLUME_UP
            2 -> ActionType.VOLUME_DOWN
            3 -> ActionType.MUTE
            4 -> ActionType.NAVIGATION
            10 -> ActionType.CYCLE_APPS
            13 -> ActionType.MEDIA_PREVIOUS
            14 -> ActionType.MEDIA_NEXT
            16 -> ActionType.PHONE
            49 -> ActionType.HOME
            else -> null
        }
    }

    private companion object {
        const val TAG = "LibreHU-BtnRemap"
        const val PACKAGE = "org.librehu.service"
        const val ACTION_BIND = "org.librehu.service.BIND"
        const val STATE_KNOB = 5
        const val ADC_STEP = 16

        /** Buttons of the Hiworld box for Renault (LNP002 decoder of com.can.activity). */
        val CAN_NAMES =
            mapOf(
                1 to "Volume +",
                2 to "Volume −",
                3 to "Mute / Call",
                4 to "Navigation",
                8 to "Right",
                9 to "Left",
                10 to "Mode / Source",
                13 to "Previous",
                14 to "Next",
                15 to "OK",
                16 to "Phone",
                37 to "Repeat",
                49 to "Home",
            )
    }
}
