package org.librehu.btnremap.engine

import android.os.Handler
import android.os.Looper
import org.librehu.btnremap.data.ActionType
import org.librehu.btnremap.data.ButtonMapping
import org.librehu.btnremap.data.PressType

/**
 * Turns down / up events into short and long presses. A long press fires while the button is still held
 * (after [LONG_MS]); volume actions repeat while held instead.
 *
 * Pulsed buttons ([org.librehu.btnremap.data.HuKey.pulsed], CAN box): a held button comes as presses of ~100 ms
 * every ~380 ms. Their release counts only after [PULSE_GAP_MS] without a new press, so that holding gives a long
 * press; a volume button, which repeats on its own, takes each pulse as a step instead.
 */
class PressDetector(
    private val onPress: (ButtonMapping, PressType) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val held = mutableMapOf<String, Runnable>()
    private val longFired = mutableSetOf<String>()
    private val pendingUp = mutableMapOf<String, Runnable>()

    fun down(
        m: ButtonMapping,
        pulsed: Boolean = false,
    ) {
        if (pulsed && m.short.type.repeats) {
            onPress(m, PressType.SHORT)
            return
        }
        pendingUp.remove(m.id)?.let {
            // Next pulse of a button still held
            main.removeCallbacks(it)
            return
        }
        if (held.containsKey(m.id)) return // auto-repeat downs
        longFired.remove(m.id)
        val r: Runnable
        if (m.short.type.repeats) {
            onPress(m, PressType.SHORT)
            r =
                object : Runnable {
                    override fun run() {
                        onPress(m, PressType.SHORT)
                        main.postDelayed(this, REPEAT_MS)
                    }
                }
            main.postDelayed(r, REPEAT_DELAY_MS)
        } else {
            r =
                Runnable {
                    if (m.long.type != ActionType.DEFAULT) {
                        longFired += m.id
                        onPress(m, PressType.LONG)
                    }
                }
            main.postDelayed(r, LONG_MS)
        }
        held[m.id] = r
    }

    fun up(
        m: ButtonMapping,
        pulsed: Boolean = false,
    ) {
        // Without a long press, nothing to wait for: each pulse is a short press (as before)
        if (pulsed && m.long.type != ActionType.DEFAULT) {
            if (m.short.type.repeats || !held.containsKey(m.id)) return
            val r = Runnable {
                pendingUp.remove(m.id)
                release(m)
            }
            pendingUp.put(m.id, r)?.let { main.removeCallbacks(it) }
            main.postDelayed(r, PULSE_GAP_MS)
            return
        }
        release(m)
    }

    private fun release(m: ButtonMapping) {
        val r = held.remove(m.id) ?: return
        main.removeCallbacks(r)
        if (!m.short.type.repeats && m.id !in longFired) onPress(m, PressType.SHORT)
        longFired.remove(m.id)
    }

    private companion object {
        const val LONG_MS = 600L
        const val REPEAT_DELAY_MS = 500L
        const val REPEAT_MS = 150L
        const val PULSE_GAP_MS = 650L
    }
}
