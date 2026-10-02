package org.librehu.btnremap.headunit

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import androidx.core.content.ContextCompat
import org.librehu.btnremap.data.HuKey

/**
 * Jancar ivi-services: every key (CAN box, resistive wheel keys, front panel) goes through
 * `KeyCodeUtil.onKeyEvent`, which first broadcasts `com.jancar.services.action.key.event`
 * (`key_event_id` = IVIKey code, `key_event_type` = 1 down / 0 up / 2 click) then runs its own action:
 * ivi-services offers no way to block it, so [ownsKeys] is false.
 * Volume: IAudio parameter 10 + Jancar volume bar (raw binder calls).
 */
class IviKeys(
    private val context: Context,
) : HeadUnit {
    override val name = "Jancar ivi-services"
    override val ownsKeys = false

    private var onKey: (HuKey) -> Unit = {}

    @Volatile
    private var audio: IBinder? = null
    private var bound = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) {
                val code = intent.getIntExtra("key_event_id", -1)
                if (code < 0) return
                val id = "ivi:$code"
                val name = NAMES[code] ?: "IVI $code"
                when (intent.getIntExtra("key_event_type", 2)) {
                    1 -> {
                        onKey(HuKey(id, name, true))
                    }

                    0 -> {
                        onKey(HuKey(id, name, false))
                    }

                    else -> {
                        onKey(HuKey(id, name, true))
                        onKey(HuKey(id, name, false))
                    }
                }
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                audio = service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                audio = null
            }
        }

    override fun start(onKey: (HuKey) -> Unit) {
        this.onKey = onKey
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_KEY_EVENT), ContextCompat.RECEIVER_EXPORTED)
        bound =
            try {
                context.bindService(
                    Intent("com.jancar.services.action.audio").setPackage("com.jancar.services"),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            } catch (e: SecurityException) {
                false
            }
    }

    override fun stop() {
        context.unregisterReceiver(receiver)
        if (bound) context.unbindService(connection)
        bound = false
    }

    override fun volume(delta: Int): Boolean {
        val current = call(TX_GET_PARAM, PARAM_VOLUME) { it.readInt() } ?: return false
        val max = call(TX_GET_PARAM_MAX, PARAM_VOLUME) { it.readInt() } ?: return false
        call(TX_SET_PARAM, PARAM_VOLUME, (current + delta).coerceIn(0, max)) {}
        call(TX_SHOW_VOLUME_BAR) {}
        return true
    }

    private fun <T> call(
        code: Int,
        vararg args: Int,
        read: (Parcel) -> T,
    ): T? {
        val b = audio ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("com.jancar.services.audio.IAudio")
            args.forEach(data::writeInt)
            b.transact(code, data, reply, 0)
            reply.readException()
            read(reply)
        } catch (e: Exception) {
            Log.w("LibreHU-BtnRemap", "IAudio $code: ${e.message}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private companion object {
        const val ACTION_KEY_EVENT = "com.jancar.services.action.key.event"
        const val TX_GET_PARAM_MAX = 6
        const val TX_GET_PARAM = 8
        const val TX_SET_PARAM = 9
        const val TX_SHOW_VOLUME_BAR = 39
        const val PARAM_VOLUME = 10

        /** IVIKey.Key.Id names (ivi-services SDK). */
        val NAMES =
            mapOf(
                1 to "Volume +",
                2 to "Volume −",
                6 to "Mode / Source",
                7 to "Answer",
                8 to "Home",
                10 to "Radio",
                11 to "Play / Pause",
                14 to "Navigation",
                25 to "OK",
                30 to "FM",
                31 to "AM",
                32 to "Next",
                33 to "Previous",
                34 to "Hang up",
                35 to "Mute / Hang up",
                60 to "Left",
                61 to "Right",
                62 to "Knob CW",
                63 to "Knob CCW",
                66 to "Back",
                72 to "Power",
                89 to "Bluetooth",
                105 to "Mute",
                159 to "Multi function",
                1001 to "Previous / Answer",
                1002 to "Next / Hang up",
                1005 to "Answer / Hang up",
                1007 to "Answer or hang up",
                1011 to "Voice (BT)",
                1015 to "Voice assistant",
            )
    }
}
