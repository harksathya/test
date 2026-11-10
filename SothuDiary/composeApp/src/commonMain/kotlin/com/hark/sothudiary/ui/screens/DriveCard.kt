package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.domain.drive.DriveRemote
import com.hark.sothudiary.domain.drive.DriveSync
import com.hark.sothudiary.domain.drive.RestoreChoice
import com.hark.sothudiary.domain.formatDateTime
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.S30
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.components.SectionCard
import kotlinx.coroutines.launch

/** 3.0.0: Settings → Google Drive backup (connect, back up now, restore with a keep / use / merge choice). */
@Composable
fun DriveBackupCard(ctx: AppContext) {
    val st = ctx.repo.data.settings
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf<DriveRemote?>(null) }

    fun afterConnect(r: DriveRemote?, fromRestore: Boolean) {
        if (r == null) return
        val backup = r.backup
        when {
            backup == null -> if (!fromRestore) scope.launch { DriveSync.backup(ctx.repo, ctx.platform, interactive = false, force = true) }
            // A phone with no properties takes the Drive backup straight away (new phone, same Gmail).
            ctx.repo.data.properties.isEmpty() -> scope.launch {
                if (DriveSync.restore(ctx.repo, ctx.platform, r, RestoreChoice.USE_DRIVE)) ctx.platform.toast(S30.restoreDone.of(lang))
            }
            else -> choice = r
        }
    }

    SectionCard(title = S30.title.s()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S30.body.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ctx.platform.driveSupported) {
                Text(S30.desktop.s(), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            if (st.driveAccount.isBlank()) {
                Text(S30.testingNote.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { scope.launch { afterConnect(DriveSync.connect(ctx.repo, ctx.platform), fromRestore = false) } }, enabled = !DriveSync.busy) {
                    Text(S30.connect.s())
                }
            } else {
                Text(S30.account.f(st.driveAccount), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (st.driveLastBackupAt > 0) S30.lastBackup.f(formatDateTime(st.driveLastBackupAt, lang)) else S30.never.s(),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S30.auto.s(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = st.driveAuto, onCheckedChange = { ctx.repo.setDrive(auto = it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { DriveSync.backup(ctx.repo, ctx.platform, interactive = true) } }, enabled = !DriveSync.busy) {
                        Text(S30.backupNow.s())
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val r = DriveSync.connect(ctx.repo, ctx.platform)
                            if (r != null && r.backup == null) ctx.platform.toast(S30.noBackup.of(lang))
                            if (r?.backup != null) choice = r
                        }
                    }, enabled = !DriveSync.busy) { Text(S30.restore.s()) }
                }
                TextButton(onClick = { DriveSync.disconnect(ctx.repo) }, enabled = !DriveSync.busy) { Text(S30.disconnect.s()) }
            }
            if (DriveSync.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (DriveSync.status.isNotBlank()) Text(
                DriveSync.status, style = MaterialTheme.typography.bodySmall,
                color = if (DriveSync.lastError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val pending = choice
    val drv = pending?.backup
    if (pending != null && drv != null) {
        val run = { c: RestoreChoice ->
            choice = null
            scope.launch {
                if (DriveSync.restore(ctx.repo, ctx.platform, pending, c) && c != RestoreChoice.KEEP_PHONE) ctx.platform.toast(S30.restoreDone.of(lang))
            }
            Unit
        }
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(S30.chooseTitle.s()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(S30.chooseBody.f(ctx.repo.data.properties.size, drv.properties.size))
                    Text(S30.restoreBody.f(drv.properties.size), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { run(RestoreChoice.MERGE) }) { Text(S30.merge.s()) }
                    TextButton(onClick = { run(RestoreChoice.USE_DRIVE) }) { Text(S30.useDrive.s()) }
                    TextButton(onClick = { run(RestoreChoice.KEEP_PHONE) }) { Text(S30.keepPhone.s()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choice = null }) { Text(S.cancel.s()) } },
        )
    }
}
