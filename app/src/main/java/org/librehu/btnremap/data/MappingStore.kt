package org.librehu.btnremap.data

import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Buttons, their actions and the app list of the "next source" action, in SharedPreferences. */
class MappingStore private constructor(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("mappings", Context.MODE_PRIVATE)

    private val _buttons = MutableStateFlow(loadButtons())
    val buttons: StateFlow<List<ButtonMapping>> = _buttons.asStateFlow()

    private val _cycleApps = MutableStateFlow(loadCycle())
    val cycleApps: StateFlow<List<ComponentName>> = _cycleApps.asStateFlow()

    /** Last key received (for the learning screen). */
    private val _lastKey = MutableStateFlow<HuKey?>(null)
    val lastKey: StateFlow<HuKey?> = _lastKey.asStateFlow()

    fun get(id: String): ButtonMapping? = _buttons.value.firstOrNull { it.id == id }

    /** Records a key seen for the first time (so it shows up in the app) and publishes it as the last key. */
    fun seen(key: HuKey) {
        if (key.down) _lastKey.value = key
        if (get(key.id) == null) save(_buttons.value + ButtonMapping(key.id, key.defaultName))
    }

    fun update(mapping: ButtonMapping) = save(_buttons.value.map { if (it.id == mapping.id) mapping else it })

    fun remove(id: String) = save(_buttons.value.filterNot { it.id == id })

    fun setCycleApps(list: List<ComponentName>) {
        prefs.edit().putString("cycle", list.joinToString("\n") { it.flattenToString() }).apply()
        _cycleApps.value = list
    }

    /** Next app of the cycle (and remembers the position). */
    fun nextCycleApp(): ComponentName? {
        val list = _cycleApps.value
        if (list.isEmpty()) return null
        val i = (prefs.getInt("cycle_index", -1) + 1) % list.size
        prefs.edit().putInt("cycle_index", i).apply()
        return list[i]
    }

    private fun save(list: List<ButtonMapping>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        prefs.edit().putString("buttons", a.toString()).apply()
        _buttons.value = list
    }

    private fun loadButtons(): List<ButtonMapping> =
        try {
            val a = JSONArray(prefs.getString("buttons", "[]"))
            List(a.length()) { ButtonMapping.fromJson(a.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }

    private fun loadCycle(): List<ComponentName> =
        prefs
            .getString("cycle", null)
            ?.split("\n")
            ?.mapNotNull { ComponentName.unflattenFromString(it) }
            .orEmpty()

    companion object {
        @Volatile
        private var instance: MappingStore? = null

        fun get(context: Context): MappingStore =
            instance ?: synchronized(this) { instance ?: MappingStore(context.applicationContext).also { instance = it } }
    }
}
