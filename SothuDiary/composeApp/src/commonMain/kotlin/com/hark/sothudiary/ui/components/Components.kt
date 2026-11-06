package com.hark.sothudiary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hark.sothudiary.ui.theme.LocalBrand
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.domain.formatDate
import com.hark.sothudiary.domain.localToPickerUtc
import com.hark.sothudiary.domain.pickerUtcToLocal
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.s

/** Top bar used on every screen: title, optional back arrow, the "த / EN" toggle and extra actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onToggleLang: () -> Unit,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = LocalBrand.current.onBar.copy(alpha = 0.6f), maxLines = 1)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.back.s()) }
            }
        },
        actions = {
            actions()
            MusicToggle()
            LangToggle(onToggleLang)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LocalBrand.current.bar,
            titleContentColor = LocalBrand.current.onBar,
            navigationIconContentColor = LocalBrand.current.onBar,
            actionIconContentColor = LocalBrand.current.onBar,
        ),
    )
}

/** Background music on / off for the top bar (one tap stops it; the choice is saved). */
data class MusicToggleState(val available: Boolean, val playing: Boolean, val toggle: () -> Unit)

val LocalMusicToggle = androidx.compose.runtime.staticCompositionLocalOf { MusicToggleState(false, false) {} }

@Composable
fun MusicToggle() {
    val m = LocalMusicToggle.current
    if (!m.available) return
    IconButton(onClick = m.toggle) {
        Icon(
            if (m.playing) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
            contentDescription = if (m.playing) S.bgmMute.s() else S.bgmUnmute.s(),
            tint = LocalBrand.current.onBar.copy(alpha = if (m.playing) 0.9f else 0.55f),
        )
    }
}

@Composable
fun LangToggle(onToggle: () -> Unit) {
    val lang = LocalLang.current
    val fg = LocalBrand.current.onBar
    val sel = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "த", color = if (lang == Lang.TA) MaterialTheme.colorScheme.onPrimary else fg.copy(alpha = 0.7f), fontSize = 13.sp,
                fontWeight = if (lang == Lang.TA) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.background(if (lang == Lang.TA) sel else Color.Transparent, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            )
            Text(
                "EN", color = if (lang == Lang.EN) MaterialTheme.colorScheme.onPrimary else fg.copy(alpha = 0.7f), fontSize = 12.sp,
                fontWeight = if (lang == Lang.EN) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.background(if (lang == Lang.EN) sel else Color.Transparent, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Small coloured pill. */
@Composable
fun Pill(text: String, color: Color, filled: Boolean = false, modifier: Modifier = Modifier) {
    val dark = LocalBrand.current.dark
    // On dark surfaces an outlined pill uses a lighter tint of its colour so the text stays readable.
    val fg = if (dark && !filled) androidx.compose.ui.graphics.lerp(color, Color.White, 0.55f) else color
    val onFilled = if (color.luminance() > 0.45f) Color(0xFF1D1C1A) else Color.White
    Box(
        modifier
            .background(if (filled) color else color.copy(alpha = if (dark) 0.22f else 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text, color = if (filled) onFilled else fg, fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    icon: ImageVector? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.45f) ?: MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (LocalBrand.current.dark) 0.dp else 1.5.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (accent != null && icon == null) {
                        Box(Modifier.size(10.dp).background(accent, CircleShape))
                        Spacer(Modifier.width(8.dp))
                    }
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = accent ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    trailing()
                }
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/** Single-select chips in a wrapping row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** Multi-select chips in a wrapping row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChips(options: List<T>, selected: Set<T>, label: @Composable (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o in selected,
                onClick = { onToggle(o) },
                label = { Text(label(o)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supporting: String? = null,
    error: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    autoFilled: Boolean = false,
) {
    val auto = autoFilled && error == null
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        supportingText = when {
            error != null -> ({ Text(error, color = MaterialTheme.colorScheme.error) })
            auto -> ({ Text("✨ " + S.autoFilledCheck.s(), color = AutoFillColor, fontWeight = FontWeight.SemiBold) })
            supporting != null -> ({ Text(supporting) })
            else -> null
        },
        isError = error != null,
        trailingIcon = trailingIcon,
        shape = MaterialTheme.shapes.medium,
        colors = if (auto) androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = AutoFillColor, focusedBorderColor = AutoFillColor,
            unfocusedContainerColor = AutoFillColor.copy(alpha = 0.06f), focusedContainerColor = AutoFillColor.copy(alpha = 0.06f),
        ) else androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
    )
}

/** A button that opens a dropdown of options (safer than ExposedDropdownMenuBox across versions). */
@Composable
fun <T> DropdownSelector(
    label: String,
    current: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(current, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { onSelect(o); open = false })
                }
            }
        }
    }
}

/** Date picker field. [millis] null = no date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, millis: Long?, onChange: (Long?) -> Unit, allowClear: Boolean = true, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { open = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (millis != null) formatDate(millis, lang) else S.pickDate.s())
            }
            if (allowClear && millis != null) {
                TextButton(onClick = { onChange(null) }) { Text(S.clear.s()) }
            }
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = millis?.let { localToPickerUtc(it) })
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis
                    if (picked != null) onChange(pickerUtcToLocal(picked))
                    open = false
                }) { Text(S.ok.s()) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(S.cancel.s()) } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text)
    }
}

@Composable
fun ClickableRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) { content() }
}

/** Standard confirmation dialog. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = if (body != null) ({ Text(body) }) else null,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
    )
}

/** Big number + label tile used on the dashboard. */
@Composable
fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.10f),
            disabledContainerColor = color.copy(alpha = 0.10f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = com.hark.sothudiary.ui.theme.readable(color))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Highlight colour for values filled in automatically from a shared post / poster. */
val AutoFillColor = Color(0xFFB8740A)
