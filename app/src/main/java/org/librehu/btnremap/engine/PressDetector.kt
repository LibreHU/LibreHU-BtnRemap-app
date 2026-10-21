package org.librehu.btnremap.engine

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.librehu.btnremap.data.ActionType
import org.librehu.btnremap.data.ButtonMapping
import org.librehu.btnremap.data.PressType

/**
 * Turns down / up events into short and long presses, and two buttons held together into a combination. A long press
 * fires while the button is still held (after [LONG_MS]); volume actions repeat while held instead.
 *
 * Pulsed buttons ([org.librehu.btnremap.data.HuKey.pulsed], CAN box): a held button comes as presses of ~100 ms
 * every ~380 ms. Their release counts only after [PULSE_GAP_MS] without a new press, so that holding gives a long
 * press; a volume button, which repeats on its own, takes each pulse as a step instead.
 *
 * Combination: a button pressed while another one is held (or was pulsing less than [PULSE_GAP_MS] ago) is the pair
 * [comboFor] gives, when it has an action: that action runs and neither button does its own on release. Every pair
 * held is reported to [onCombo] (the app lists it to be set).
 */
class PressDetector(
    private val comboFor: (String, String) -> ButtonMapping? = { _, _ -> null },
    private val onCombo: (ButtonMapping, ButtonMapping) -> Unit = { _, _ -> },
    private val onPress: (ButtonMapping, PressType) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    private class Hold(
        val m: ButtonMapping,
        val timer: Runnable?,
        var longFired: Boolean = false,
        /** Part of a combination that ran: no action of its own any more. */
        var consumed: Boolean = false,
    )

    private val holds = mutableMapOf<String, Hold>()
    private val pendingUp = mutableMapOf<String, Runnable>()

    /** Last pulse of a pulsed volume button (they are not held): it still counts for a combination meanwhile. */
    private val lastPulse = mutableMapOf<String, Pair<ButtonMapping, Long>>()

    fun down(
        m: ButtonMapping,
        pulsed: Boolean = false,
    ) {
        if (combo(m)) return
        if (pulsed && m.short.type.repeats) {
            lastPulse[m.id] = m to SystemClock.elapsedRealtime()
            onPress(m, PressType.SHORT)
            return
        }
        pendingUp.remove(m.id)?.let {
            // Next pulse of a button still held
            main.removeCallbacks(it)
            return
        }
        if (holds.containsKey(m.id)) return // auto-repeat downs
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
                    val h = holds[m.id] ?: return@Runnable
                    if (m.long.type != ActionType.DEFAULT && !h.consumed) {
                        h.longFired = true
                        onPress(m, PressType.LONG)
                    }
                }
            main.postDelayed(r, LONG_MS)
        }
        holds[m.id] = Hold(m, r)
    }

    fun up(
        m: ButtonMapping,
        pulsed: Boolean = false,
    ) {
        // Without a long press, nothing to wait for: each pulse is a short press (as before)
        if (pulsed && (m.long.type != ActionType.DEFAULT || holds[m.id]?.consumed == true)) {
            if (m.short.type.repeats || !holds.containsKey(m.id)) return
            val r =
                Runnable {
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
        val h = holds.remove(m.id) ?: return
        h.timer?.let { main.removeCallbacks(it) }
        if (!m.short.type.repeats && !h.longFired && !h.consumed) onPress(m, PressType.SHORT)
    }

    /** [m] goes down while another button is held: their combination, when it has an action. */
    private fun combo(m: ButtonMapping): Boolean {
        val now = SystemClock.elapsedRealtime()
        lastPulse.entries.removeAll { now - it.value.second > PULSE_GAP_MS }
        val others =
            holds.values.filter { it.m.id != m.id && !it.consumed }.map { it.m } +
                lastPulse.values.map { it.first }.filter { it.id != m.id }
        val other = others.lastOrNull() ?: return false
        onCombo(other, m)
        val c = comboFor(other.id, m.id)?.takeIf { it.isMapped } ?: return false
        // The held one stops (no long press, no repeat, nothing on release); the new one is taken as held too.
        holds[other.id]?.let { h ->
            h.consumed = true
            h.timer?.let { main.removeCallbacks(it) }
        }
        lastPulse.remove(other.id)
        holds[m.id]?.timer?.let { main.removeCallbacks(it) }
        holds[m.id] = Hold(m, null, consumed = true)
        onPress(c, PressType.SHORT)
        return true
    }

    private companion object {
        const val LONG_MS = 600L
        const val REPEAT_DELAY_MS = 500L
        const val REPEAT_MS = 150L
        const val PULSE_GAP_MS = 650L
    }
}
