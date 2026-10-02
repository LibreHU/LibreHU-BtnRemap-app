package org.librehu.btnremap.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.btnremap.R
import org.librehu.btnremap.RemapAccessibilityService
import org.librehu.btnremap.RemapService
import org.librehu.btnremap.data.Action
import org.librehu.btnremap.data.ActionType
import org.librehu.btnremap.data.ButtonMapping
import org.librehu.btnremap.data.MappingStore

private class AppEntry(
    val component: ComponentName,
    val label: String,
    val icon: ImageBitmap,
)

private fun launchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    return pm
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map {
            AppEntry(
                ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                it.loadLabel(pm).toString(),
                it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
            )
        }.sortedBy { it.label.lowercase() }
}

private fun appLabel(
    context: Context,
    c: ComponentName?,
): String =
    c?.let {
        runCatching {
            val pm = context.packageManager
            pm.getActivityInfo(it, 0).loadLabel(pm).toString()
        }.getOrDefault(it.packageName)
    } ?: "?"

@Composable
private fun actionText(a: Action): String {
    val context = LocalContext.current
    val base = stringResource(a.type.label)
    return if (a.type == ActionType.LAUNCH_APP) "$base : ${appLabel(context, a.component)}" else base
}

@Composable
fun RemapScreen() {
    val context = LocalContext.current
    val store = MappingStore.get(context)
    val buttons by store.buttons.collectAsStateWithLifecycle()
    val last by store.lastKey.collectAsStateWithLifecycle()
    val source by RemapService.sourceName.collectAsStateWithLifecycle()
    val owns by RemapService.ownsKeys.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ButtonMapping?>(null) }
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CarColors.Background)
                .safeDrawingPadding()
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Text(stringResource(R.string.app_name), color = CarColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.source, source), color = CarColors.TextDim, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(if (owns) R.string.owns_keys else R.string.shared_keys), color = CarColors.TextDim, fontSize = 14.sp)
                if (RemapAccessibilityService.instance == null) {
                    Spacer(Modifier.height(8.dp))
                    Chip(stringResource(R.string.enable_accessibility)) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            Card {
                Text(stringResource(R.string.last_key), color = CarColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                val k = last
                if (k == null) {
                    Text(stringResource(R.string.press_a_key), color = CarColors.TextDim, fontSize = 16.sp)
                } else {
                    val m = buttons.firstOrNull { it.id == k.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { editing = m }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_wheel), null, tint = CarColors.Accent, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m?.name ?: k.defaultName, color = CarColors.Text, fontSize = 20.sp)
                            Text(k.id, color = CarColors.TextDim, fontSize = 13.sp)
                        }
                        Text(stringResource(R.string.edit), color = CarColors.Accent, fontSize = 16.sp)
                    }
                }
            }
            CycleCard(Modifier.weight(1f))
        }
        Column(
            Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.buttons), color = CarColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.buttons_hint), color = CarColors.TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(buttons, key = { it.id }) { m ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (m.id == last?.id) CarColors.Accent.copy(alpha = 0.18f) else CarColors.SurfaceHigh)
                            .clickable { editing = m }
                            .padding(14.dp),
                    ) {
                        Text(m.name, color = CarColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.short_press) + " : " + actionText(m.short),
                            color = CarColors.TextDim,
                            fontSize = 14.sp,
                        )
                        if (!m.short.type.repeats) {
                            Text(
                                stringResource(R.string.long_press) + " : " + actionText(m.long),
                                color = CarColors.TextDim,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
    editing?.let { m -> ButtonEditor(m, onDismiss = { editing = null }) }
}

@Composable
private fun ButtonEditor(
    initial: ButtonMapping,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = MappingStore.get(context)
    var m by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = short, false = long
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.button)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = m.name,
                    onValueChange = { m = m.copy(name = it) },
                    label = { Text(stringResource(R.string.name)) },
                )
                Text(m.id, fontSize = 13.sp)
                Chip(stringResource(R.string.short_press) + " : " + actionText(m.short)) { picking = true }
                if (!m.short.type.repeats) Chip(stringResource(R.string.long_press) + " : " + actionText(m.long)) { picking = false }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                store.update(m)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = {
                store.remove(m.id)
                onDismiss()
            }) { Text(stringResource(R.string.forget)) }
        },
    )
    picking?.let { short ->
        ActionPicker(onDismiss = { picking = null }) { a ->
            m = if (short) m.copy(short = a) else m.copy(long = a)
            picking = null
        }
    }
}

@Composable
private fun ActionPicker(
    onDismiss: () -> Unit,
    onPick: (Action) -> Unit,
) {
    var choosingApp by remember { mutableStateOf(false) }
    if (choosingApp) {
        AppPicker(onDismiss = { choosingApp = false }) { onPick(Action(ActionType.LAUNCH_APP, it)) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_action)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(ActionType.entries) { t ->
                    Text(
                        stringResource(t.label),
                        fontSize = 18.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (t == ActionType.LAUNCH_APP) choosingApp = true else onPick(Action(t)) }
                                .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AppPicker(
    onDismiss: () -> Unit,
    onPick: (ComponentName) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { launchableApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_app)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.component.flattenToString() }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app.component) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(app.icon, null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, fontSize = 17.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Apps visited by the "next source" action, in order. */
@Composable
private fun CycleCard(modifier: Modifier) {
    val context = LocalContext.current
    val store = MappingStore.get(context)
    val apps by store.cycleApps.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CarColors.Surface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cycle_title), color = CarColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.cycle_hint), color = CarColors.TextDim, fontSize = 13.sp)
            }
            IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, stringResource(R.string.add), tint = CarColors.Text) }
        }
        LazyColumn {
            items(apps.size) { i ->
                val c = apps[i]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${i + 1}. " + appLabel(context, c), color = CarColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (i > 0) store.setCycleApps(apps.toMutableList().apply { add(i - 1, removeAt(i)) }) }) {
                        Icon(Icons.Default.KeyboardArrowUp, null, tint = CarColors.TextDim)
                    }
                    IconButton(onClick = {
                        if (i <
                            apps.size - 1
                        ) {
                            store.setCycleApps(apps.toMutableList().apply { add(i + 1, removeAt(i)) })
                        }
                    }) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = CarColors.TextDim)
                    }
                    IconButton(onClick = { store.setCycleApps(apps - c) }) { Icon(Icons.Default.Close, null, tint = CarColors.TextDim) }
                }
            }
        }
    }
    if (adding) {
        AppPicker(onDismiss = { adding = false }) {
            store.setCycleApps(apps + it)
            adding = false
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CarColors.Surface)
            .padding(20.dp),
    ) { content() }
}

@Composable
private fun Chip(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = CarColors.OnAccent,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(CarColors.Accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}
