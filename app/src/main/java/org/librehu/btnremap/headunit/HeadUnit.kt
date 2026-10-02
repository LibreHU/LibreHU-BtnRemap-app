package org.librehu.btnremap.headunit

import android.content.Context
import android.media.AudioManager
import org.librehu.btnremap.data.ActionType
import org.librehu.btnremap.data.HuKey
import org.librehu.btnremap.data.PressType

/**
 * Where the button presses come from and how volume is driven. Each branch provides its implementation:
 * `main` Android keys through the accessibility service, `ivi` Jancar ivi-services key broadcast,
 * `librehu-service` CAN box (Hiworld) and steering wheel ADC keys through LibreHU-service.
 */
interface HeadUnit {
    val name: String

    /**
     * True when our actions replace the head unit's own (we receive the keys before anyone acts on them);
     * false when the head unit still runs its default action (ivi-services).
     */
    val ownsKeys: Boolean

    fun start(onKey: (HuKey) -> Unit)

    fun stop()

    /** Volume step on the head unit; false to fall back to Android's media volume. */
    fun volume(delta: Int): Boolean = false

    /** Mute toggle on the head unit; false to fall back to Android. */
    fun toggleMute(): Boolean = false

    /** When we own the keys: what an unmapped button does by default (volume keys keep adjusting the volume…). */
    fun defaultAction(
        id: String,
        press: PressType,
    ): ActionType? = null

    companion object {
        fun create(context: Context): HeadUnit = if (isInstalled(context, "com.jancar.services")) IviKeys(context) else AccessibilityKeys

        private fun isInstalled(
            context: Context,
            pkg: String,
        ): Boolean =
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
    }
}

/**
 * Plain Android: hardware keys (media, volume, …) filtered by [org.librehu.btnremap.RemapAccessibilityService].
 * Mapped keys are consumed, so our action replaces the default one.
 */
object AccessibilityKeys : HeadUnit {
    override val name = "Android (accessibility)"
    override val ownsKeys = true

    @Volatile
    var sink: ((HuKey) -> Unit)? = null

    override fun start(onKey: (HuKey) -> Unit) {
        sink = onKey
    }

    override fun stop() {
        sink = null
    }
}

/** Android media volume with the system panel. */
fun androidVolume(
    context: Context,
    direction: Int,
) {
    context
        .getSystemService(AudioManager::class.java)
        .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
}
