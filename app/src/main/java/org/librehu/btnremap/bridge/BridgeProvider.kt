package org.librehu.btnremap.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.librehu.btnremap.R
import org.librehu.btnremap.RemapService
import org.librehu.btnremap.data.Action
import org.librehu.btnremap.data.ActionType
import org.librehu.btnremap.data.MappingStore

/**
 * The buttons for LibreHU's phone app (through LibreHU-service's bridge, [BridgeKitProvider]): for every button seen
 * by the head unit, its name and what its short and long presses do (the app to open for "open an app"), and the
 * buttons ignored after the head unit starts. Buttons are learnt by pressing them on the head unit; the "next source"
 * list stays on its screen.
 */
class BridgeProvider : BridgeKitProvider() {
    override fun pages(context: Context): List<KitPage> {
        val store = MappingStore.get(context)
        val apps = launchable(context)
        return listOf(
            page(context, "buttons", context.getString(R.string.buttons), "keys") {
                val ignore = RemapService.ignoreAfterOnS(context)
                slider("ignore", context.getString(R.string.ignore_after_on, if (ignore == 0) "—" else "$ignore s"), ignore, 0, 15, unit = "s", hint = s(R.string.ignore_after_on_hint)) { v ->
                    RemapService.setIgnoreAfterOnS(context, v)
                }
                for (b in store.buttons.value) {
                    section(b.name)
                    val key = b.id.replace(Regex("[^A-Za-z0-9]"), "_")
                    text("${key}_name", s(R.string.bridge_button_name), b.name, 40) { v -> v.trim().takeIf { it.isNotEmpty() }?.let { store.update(b.copy(name = it)) } }
                    press("${key}_short", s(R.string.short_press), b.short, apps) { a -> store.update(store.get(b.id)!!.copy(short = a)) }
                    press("${key}_long", s(R.string.long_press), b.long, apps) { a -> store.update(store.get(b.id)!!.copy(long = a)) }
                }
            },
        )
    }

    /** The action of a press, and the app when it opens one. */
    private fun KitBuilder.press(
        id: String,
        label: String,
        a: Action,
        apps: List<Pair<String, String>>,
        save: (Action) -> Unit,
    ) {
        choice(id, label, a.type, ActionType.entries.map { it to s(it.label) }) { t -> save(a.copy(type = t, component = if (t == ActionType.LAUNCH_APP) a.component else null)) }
        if (a.type == ActionType.LAUNCH_APP) {
            choice("${id}_app", "$label · ${s(R.string.choose_app)}", a.component?.flattenToString() ?: "", listOf("" to "—") + apps) { c ->
                save(a.copy(component = ComponentName.unflattenFromString(c)))
            }
        }
    }

    /** Apps of the app drawer: component → label. */
    private fun launchable(c: Context): List<Pair<String, String>> =
        c.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString() to it.loadLabel(c.packageManager).toString() }
            .sortedBy { it.second.lowercase() }
}
