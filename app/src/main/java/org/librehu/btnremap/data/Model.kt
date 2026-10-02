package org.librehu.btnremap.data

import android.content.ComponentName
import org.json.JSONObject
import org.librehu.btnremap.R

/** A key event from the head unit: [id] is stable per physical button ("ivi:6", "can:10", "android:87"…). */
data class HuKey(
    val id: String,
    /** Default name shown before the user renames the button. */
    val defaultName: String,
    val down: Boolean,
)

enum class PressType { SHORT, LONG }

/** What a button press does. [DEFAULT] keeps the head unit's own action (or nothing when we own the keys). */
enum class ActionType(
    val label: Int,
) {
    DEFAULT(R.string.action_default),
    NOTHING(R.string.action_nothing),
    LAUNCH_APP(R.string.action_launch_app),
    CYCLE_APPS(R.string.action_cycle_apps),
    HOME(R.string.action_home),
    BACK(R.string.action_back),
    RECENTS(R.string.action_recents),
    NOTIFICATIONS(R.string.action_notifications),
    MEDIA_PLAY_PAUSE(R.string.action_play_pause),
    MEDIA_NEXT(R.string.action_next),
    MEDIA_PREVIOUS(R.string.action_previous),
    VOLUME_UP(R.string.action_volume_up),
    VOLUME_DOWN(R.string.action_volume_down),
    MUTE(R.string.action_mute),
    NAVIGATION(R.string.action_navigation),
    VOICE(R.string.action_voice),
    PHONE(R.string.action_phone),
    ;

    /** Repeats while the button is held (no long press for these). */
    val repeats: Boolean get() = this == VOLUME_UP || this == VOLUME_DOWN

    /** Needs the accessibility service (global actions). */
    val needsAccessibility: Boolean get() = this == BACK || this == RECENTS || this == NOTIFICATIONS
}

data class Action(
    val type: ActionType = ActionType.DEFAULT,
    val component: ComponentName? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("t", type.name).put("c", component?.flattenToString())

    companion object {
        fun fromJson(o: JSONObject?): Action {
            if (o == null) return Action()
            val type = runCatching { ActionType.valueOf(o.getString("t")) }.getOrDefault(ActionType.DEFAULT)
            val c = o.optString("c").takeIf { it.isNotEmpty() && it != "null" }?.let(ComponentName::unflattenFromString)
            return Action(type, c)
        }
    }
}

/** A button seen at least once, with its name and its short / long press actions. */
data class ButtonMapping(
    val id: String,
    val name: String,
    val short: Action = Action(),
    val long: Action = Action(),
) {
    val isMapped: Boolean get() = short.type != ActionType.DEFAULT || long.type != ActionType.DEFAULT

    fun action(press: PressType): Action = if (press == PressType.SHORT) short else long

    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("n", name)
            .put("s", short.toJson())
            .put("l", long.toJson())

    companion object {
        fun fromJson(o: JSONObject) =
            ButtonMapping(o.getString("id"), o.optString("n"), Action.fromJson(o.optJSONObject("s")), Action.fromJson(o.optJSONObject("l")))
    }
}
