package org.librehu.btnremap.engine

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import org.librehu.btnremap.RemapAccessibilityService
import org.librehu.btnremap.data.Action
import org.librehu.btnremap.data.ActionType
import org.librehu.btnremap.data.MappingStore
import org.librehu.btnremap.headunit.HeadUnit
import org.librehu.btnremap.headunit.androidVolume

/** Executes the action chosen for a button. */
class ActionRunner(
    private val context: Context,
    private val headUnit: HeadUnit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun run(action: Action) {
        Log.i(TAG, "action ${action.type} ${action.component ?: ""}")
        when (action.type) {
            ActionType.DEFAULT, ActionType.NOTHING -> {}

            ActionType.LAUNCH_APP -> {
                action.component?.let(::launch)
            }

            ActionType.CYCLE_APPS -> {
                MappingStore.get(context).nextCycleApp()?.let(::launch)
            }

            ActionType.HOME -> {
                start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            }

            ActionType.BACK -> {
                global(AccessibilityService.GLOBAL_ACTION_BACK)
            }

            ActionType.RECENTS -> {
                global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            }

            ActionType.NOTIFICATIONS -> {
                global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }

            ActionType.MEDIA_PLAY_PAUSE -> {
                media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }

            ActionType.MEDIA_NEXT -> {
                media(KeyEvent.KEYCODE_MEDIA_NEXT)
            }

            ActionType.MEDIA_PREVIOUS -> {
                media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }

            ActionType.VOLUME_UP -> {
                if (!headUnit.volume(+1)) androidVolume(context, AudioManager.ADJUST_RAISE)
            }

            ActionType.VOLUME_DOWN -> {
                if (!headUnit.volume(-1)) androidVolume(context, AudioManager.ADJUST_LOWER)
            }

            ActionType.MUTE -> {
                if (!headUnit.toggleMute()) androidVolume(context, AudioManager.ADJUST_TOGGLE_MUTE)
            }

            ActionType.NAVIGATION -> {
                start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))
            }

            ActionType.VOICE -> {
                start(Intent(Intent.ACTION_VOICE_COMMAND))
            }

            ActionType.PHONE -> {
                start(Intent(Intent.ACTION_DIAL))
            }
        }
    }

    /** Media key to the active media session (radio, music, …). */
    private fun media(code: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun global(action: Int) {
        if (RemapAccessibilityService.instance?.performGlobalAction(action) != true) {
            Log.w(TAG, "accessibility service not enabled: global action $action ignored")
        }
    }

    private fun launch(component: ComponentName) =
        start(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component),
        )

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no activity for $intent")
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot start $intent: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "LibreHU-BtnRemap"
    }
}
