package org.librehu.btnremap.data

import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Buttons, their actions and the app list of the "next source" action, in SharedPreferences. */
class MappingStore private constructor(
    private val context: Context,
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

    /**
     * Mapping that applies to [id]: its own, or for a CAN code above 0x1F not set yet, the one of its low 5 bits
     * (buttons mapped when only D2 & 0x1F was read).
     */
    fun mappingFor(id: String): ButtonMapping? {
        val own = get(id)
        if (own?.isMapped == true || !id.startsWith("can:")) return own
        val code = id.removePrefix("can:").toIntOrNull() ?: return own
        if (code <= 0x1F) return own
        return get("can:${code and 0x1F}")?.takeIf { it.isMapped }?.copy(id = id) ?: own
    }

    private val _detected = MutableStateFlow<List<HuKey>>(emptyList())

    /** Combinations pressed that are not in the list yet (offered to be added and set). */
    val detected: StateFlow<List<HuKey>> = _detected.asStateFlow()

    /** Two buttons held together: offered in [detected] until added. */
    fun comboSeen(
        a: ButtonMapping,
        b: ButtonMapping,
    ) {
        val id = comboId(a.id, b.id)
        if (get(id) != null || _detected.value.any { it.id == id }) return
        val names = listOf(a, b).sortedBy { it.id }.joinToString(" + ") { it.name }
        _detected.value = _detected.value + HuKey(id, names, true)
    }

    /** Adds a detected key or combination to the list (to give it actions). */
    fun add(key: HuKey): ButtonMapping {
        get(key.id)?.let { return it }
        val m = ButtonMapping(key.id, key.defaultName)
        save(_buttons.value + m)
        _detected.value = _detected.value.filterNot { it.id == key.id }
        return m
    }

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
        ServiceClaims.publish(context, list)
    }

    /** Sends the handled buttons to LibreHU-service again (start of the app's service). */
    fun publishClaims() = ServiceClaims.publish(context, _buttons.value)

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
        const val COMBO = "combo:"

        /** Id of the combination of two buttons, whatever the order they were pressed in. */
        fun comboId(
            a: String,
            b: String,
        ) = COMBO + listOf(a, b).sorted().joinToString("+")

        @Volatile
        private var instance: MappingStore? = null

        fun get(context: Context): MappingStore =
            instance ?: synchronized(this) { instance ?: MappingStore(context.applicationContext).also { instance = it } }
    }
}
