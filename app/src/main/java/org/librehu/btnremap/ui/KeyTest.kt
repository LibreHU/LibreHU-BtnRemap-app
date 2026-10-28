package org.librehu.btnremap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.btnremap.R
import org.librehu.btnremap.RemapService
import org.librehu.btnremap.data.ButtonMapping
import org.librehu.btnremap.data.HuKey
import org.librehu.btnremap.data.KeyLog
import org.librehu.btnremap.data.MappingStore

/**
 * Key test, full screen: while it is open no action runs; each button event is listed (down / up, then the short,
 * long or combination press it made, and the action it would give). A button or combination not in the list yet can
 * be added from here; tapping an event opens its settings.
 */
@Composable
fun KeyTestDialog(
    onEdit: (ButtonMapping) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = MappingStore.get(context)
    val events by RemapService.events.collectAsStateWithLifecycle()
    val detected by store.detected.collectAsStateWithLifecycle()
    val buttons by store.buttons.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        RemapService.setTestMode(true)
        onDispose { RemapService.setTestMode(false) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(
            Modifier
                .fillMaxSize()
                .background(CarColors.Background)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .carPanel(28.dp, CarColors.Surface)
                    .padding(20.dp),
            ) {
                Text(stringResource(R.string.test_title), color = CarColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.test_hint), color = CarColors.TextDim, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(events, key = { "${it.time}-${it.id}-${it.kind}" }) { e ->
                        val known = buttons.firstOrNull { it.id == e.id }
                        EventRow(e, known != null) {
                            onEdit(known ?: store.add(HuKey(e.id, e.name, true)))
                        }
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .carPanel(28.dp, CarColors.Surface)
                    .padding(20.dp),
            ) {
                Text(stringResource(R.string.detected_title), color = CarColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.detected_hint), color = CarColors.TextDim, fontSize = 13.sp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    items(detected, key = { it.id }) { k ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .carPanel(16.dp, CarColors.SurfaceHigh)
                                .clickable { onEdit(store.add(k)) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(k.defaultName, color = CarColors.Text, fontSize = 16.sp)
                                Text(k.id, color = CarColors.TextDim, fontSize = 12.sp)
                            }
                            Text(stringResource(R.string.add), color = CarColors.Accent, fontSize = 15.sp)
                        }
                    }
                }
                Text(
                    stringResource(R.string.test_close),
                    color = CarColors.OnAccent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(50))
                            .background(CarColors.Accent)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun EventRow(
    e: KeyLog,
    known: Boolean,
    onClick: () -> Unit,
) {
    val what =
        stringResource(
            when (e.kind) {
                KeyLog.DOWN -> R.string.test_down
                KeyLog.UP -> R.string.test_up
                KeyLog.SHORT -> R.string.short_press
                KeyLog.LONG -> R.string.long_press
                KeyLog.COMBO -> R.string.test_combo
                else -> R.string.test_ignored
            },
        )
    val press = e.kind == KeyLog.SHORT || e.kind == KeyLog.LONG || e.kind == KeyLog.COMBO
    Row(
        Modifier
            .fillMaxWidth()
            .clip(carShape(14.dp))
            .background(if (press) CarColors.Accent.copy(alpha = 0.18f) else CarColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, color = CarColors.Text, fontSize = 16.sp)
            Text(e.id + if (!known) " · " + stringResource(R.string.test_not_listed) else "", color = CarColors.TextDim, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(what, color = if (press) CarColors.Accent else CarColors.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (press) {
                Text(
                    e.action?.let { stringResource(it.label) } ?: stringResource(R.string.action_nothing),
                    color = CarColors.TextDim,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
