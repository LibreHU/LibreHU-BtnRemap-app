package org.librehu.btnremap

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import org.librehu.btnremap.data.HuKey
import org.librehu.btnremap.data.MappingStore
import org.librehu.btnremap.headunit.AccessibilityKeys

/**
 * Optional accessibility service: global actions (back, recents, notifications) and, on plain Android, hardware
 * key filtering (a mapped key is consumed so that only our action runs).
 */
class RemapAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
        RemapService.start(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val sink = AccessibilityKeys.sink ?: return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val id = "android:${event.keyCode}"
        val name = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_").replace('_', ' ')
        val store = MappingStore.get(this)
        store.seen(HuKey(id, name, event.action == KeyEvent.ACTION_DOWN))
        if (store.get(id)?.isMapped != true) return false
        sink(HuKey(id, name, event.action == KeyEvent.ACTION_DOWN))
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: RemapAccessibilityService? = null
            private set
    }
}
