package com.hark.sothudiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.VoiceNote
import com.hark.sothudiary.data.newId
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.formatDateTime
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import kotlinx.coroutines.delay

/** Shared playback state: only one clip plays at a time. */
object Playback {
    var file by mutableStateOf<String?>(null)
    var paused by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    fun reset() { file = null; paused = false; position = 0L; duration = 0L }
}

fun mmss(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return (total / 60).toString() + ":" + (total % 60).toString().padStart(2, '0')
}

@Composable
fun VoiceNotePlayer(ctx: AppContext, vn: VoiceNote, onDelete: (() -> Unit)?) {
    val lang = LocalLang.current
    val audio = ctx.platform.audio
    val isMine = Playback.file == vn.fileName
    val playing = isMine && !Playback.paused

    LaunchedEffect(playing) {
        while (playing && Playback.file == vn.fileName) {
            Playback.position = audio.positionMs()
            val d = audio.durationMs()
            if (d > 0) Playback.duration = d
            delay(200)
        }
    }

    val dur = if (isMine && Playback.duration > 0) Playback.duration else vn.durationMs
    val pos = if (isMine) Playback.position else 0L
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    when {
                        playing -> { audio.pause(); Playback.paused = true }
                        isMine -> { audio.resume(); Playback.paused = false }
                        else -> {
                            val ok = audio.play(vn.fileName) { Playback.reset() }
                            if (ok) {
                                Playback.file = vn.fileName; Playback.paused = false; Playback.position = 0L; Playback.duration = vn.durationMs
                            } else {
                                ctx.platform.toast(S.cannotPlay.of(lang))
                            }
                        }
                    }
                },
                enabled = audio.recordingSupported,
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) S.pause.s() else S.play.s(),
                    tint = MaterialTheme.colorScheme.secondary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    (if (vn.imported) S.attachedRecording.s() else S.voiceNote.s()) + " • " + formatDateTime(vn.createdAt, lang),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1,
                )
                LinearProgressIndicator(
                    progress = { if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(mmss(pos) + " / " + mmss(dur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = S.delete.s(), tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Record / attach / list voice notes for one call or visit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceNotesSection(ctx: AppContext, notes: List<VoiceNote>, onAdd: (VoiceNote) -> Unit, onRemove: (VoiceNote) -> Unit) {
    val lang = LocalLang.current
    val audio = ctx.platform.audio
    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var importing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<VoiceNote?>(null) }

    LaunchedEffect(recording) {
        while (recording) {
            elapsed = nowMillis() - startedAt
            delay(250)
        }
    }

    if (!audio.recordingSupported) {
        Text(S.voiceAndroidOnly.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (recording) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(0xFFD32F2F), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(S.recording.s() + "  " + mmss(elapsed), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { audio.cancelRecording(); recording = false }) { Icon(Icons.Filled.Close, contentDescription = S.cancel.s()) }
                Button(
                    onClick = {
                        recording = false
                        val clip = audio.stopRecording()
                        if (clip != null) {
                            onAdd(VoiceNote(newId(), clip.fileName, clip.durationMs, nowMillis(), imported = false))
                        } else {
                            ctx.platform.toast(S.recordFailed.of(lang))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(S.stopAndSave.s())
                }
            }
        }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = {
                audio.stopPlayback(); Playback.reset()
                audio.startRecording { ok ->
                    if (ok) {
                        startedAt = nowMillis(); elapsed = 0L; recording = true
                    } else {
                        ctx.platform.toast(S.micDenied.of(lang))
                    }
                }
            }) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.recordVoiceNote.s())
            }
            OutlinedButton(onClick = {
                importing = true
                audio.importAudio { clip ->
                    importing = false
                    if (clip != null) onAdd(VoiceNote(newId(), clip.fileName, clip.durationMs, nowMillis(), imported = true))
                }
            }, enabled = !importing) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.attachAudio.s())
            }
        }
        Text(S.attachHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
    if (notes.isNotEmpty()) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.forEach { vn -> VoiceNotePlayer(ctx, vn, onDelete = { confirmDelete = vn }) }
        }
    }
    confirmDelete?.let { vn ->
        ConfirmDialog(
            title = S.deleteVoiceQ.s(), body = null, confirm = S.delete.s(), destructive = true,
            onConfirm = {
                if (Playback.file == vn.fileName) { audio.stopPlayback(); Playback.reset() }
                onRemove(vn)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}
