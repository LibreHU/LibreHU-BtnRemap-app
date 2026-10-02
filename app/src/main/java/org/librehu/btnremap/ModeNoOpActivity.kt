package org.librehu.btnremap

import android.app.Activity
import android.os.Bundle

/**
 * Harmless target for ivi-services' MODE list ([ModeControl] of /jancar/config/ivi-settings.ini, see README):
 * opens and closes at once, so that only the action chosen in this app runs for MODE.
 */
class ModeNoOpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        overridePendingTransition(0, 0)
    }
}
