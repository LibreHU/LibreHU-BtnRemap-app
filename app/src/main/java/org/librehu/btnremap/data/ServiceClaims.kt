package org.librehu.btnremap.data

import android.content.Context
import android.content.Intent

/**
 * Tells LibreHU-service which CAN box buttons this app handles ("can:<code>", the ones with an action set here), so
 * that the service no longer runs its own action for them: one action per press. The other buttons keep the
 * service's action. Without LibreHU-service, nothing happens.
 */
object ServiceClaims {
    private const val SERVICE = "org.librehu.service"
    private const val ACTION = "org.librehu.service.CLAIM_KEYS"

    fun publish(
        context: Context,
        buttons: List<ButtonMapping>,
    ) {
        val keys = ArrayList(buttons.filter { it.isMapped && it.id.startsWith("can:") }.map { it.id })
        try {
            context.sendBroadcast(
                Intent(ACTION)
                    .setPackage(SERVICE)
                    .putExtra("package", context.packageName)
                    .putStringArrayListExtra("keys", keys),
            )
        } catch (_: Exception) {
        }
    }
}
