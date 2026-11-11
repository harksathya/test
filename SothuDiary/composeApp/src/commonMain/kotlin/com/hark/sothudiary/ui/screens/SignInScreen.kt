package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.domain.drive.DriveRemote
import com.hark.sothudiary.domain.drive.DriveSync
import com.hark.sothudiary.domain.drive.RestoreChoice
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S30
import com.hark.sothudiary.i18n.S310
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import kotlinx.coroutines.launch

/**
 * 3.1.0: first-launch screen, before Home and the AI key card: Sign in with Google (Drive appDataFolder). If Drive
 * already has a backup it is restored (properties, files and the saved AI key); otherwise the first backup is made.
 * "Skip" keeps the app fully offline. Shown once (see [com.hark.sothudiary.domain.drive.showSignInStep]).
 */
@Composable
fun FirstSignInScreen(ctx: AppContext) {
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf<DriveRemote?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun finish(message: String?) {
        message?.let { ctx.platform.toast(it) }
        ctx.repo.setSignInStepDone()
    }

    fun signIn() {
        failed = false
        scope.launch {
            val r = DriveSync.connect(ctx.repo, ctx.platform)
            when {
                r == null -> failed = true
                r.backup == null -> {
                    DriveSync.backup(ctx.repo, ctx.platform, interactive = false, force = true)
                    finish(S310.ready.format(lang, r.email))
                }
                ctx.repo.data.properties.isEmpty() -> {
                    if (DriveSync.restore(ctx.repo, ctx.platform, r, RestoreChoice.USE_DRIVE)) {
                        finish(S310.restored.format(lang, ctx.repo.data.properties.size))
                    } else failed = true
                }
                else -> choice = r
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Text(S310.title.s(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(S310.body.s(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(S310.privacy.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Button(onClick = { signIn() }, enabled = !DriveSync.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (failed) S310.tryAgain.s() else S310.signIn.s())
            }
            if (DriveSync.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (DriveSync.status.isNotBlank()) Text(
                DriveSync.status, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = if (DriveSync.lastError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(S30.testingNote.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            TextButton(onClick = { finish(null) }, enabled = !DriveSync.busy) { Text(S310.skip.s()) }
            Text(S310.skipNote.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }

    choice?.let { pending ->
        DriveRestoreChoiceDialog(ctx, pending, onClose = { choice = null }) { c ->
            scope.launch {
                if (DriveSync.restore(ctx.repo, ctx.platform, pending, c)) finish(S310.restored.format(lang, ctx.repo.data.properties.size))
                else failed = true
            }
        }
    }
}
