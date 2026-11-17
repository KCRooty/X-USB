package com.xusb.common.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xusb.common.ui.state.OpRunner
import com.xusb.common.ui.theme.DataFont
import com.xusb.common.ui.theme.LocalStatusColors
import com.xusb.common.ui.tr
import com.xusb.common.usb.UsbDriveInfo

/** Panel plano: superficie, filo de 1 dp y radio de 8 dp. Sin sombra. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Valor técnico en monoespaciada y seleccionable (rutas, tamaños, errores). */
@Composable
fun DataText(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    SelectionContainer {
        Text(text, fontFamily = DataFont, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (danger) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
}

/** Opción excluyente (sistema de archivos, tabla de particiones…). El acento marca la elegida. */
@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline),
        modifier = modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    selected -> accent
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyMedium.let { if (mono) it.copy(fontFamily = DataFont) else it },
        trailingIcon = trailing,
        modifier = modifier.fillMaxWidth()
    )
}

enum class NoteKind { INFO, WARN, ERROR, OK }

/** Aviso con causa y siguiente paso. El color acompaña al texto, nunca lo sustituye. */
@Composable
fun Note(title: String, body: String? = null, kind: NoteKind = NoteKind.INFO, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val tint = when (kind) {
        NoteKind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        NoteKind.WARN -> MaterialTheme.colorScheme.primary
        NoteKind.ERROR -> status.danger
        NoteKind.OK -> status.ok
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (kind == NoteKind.INFO) MaterialTheme.colorScheme.onSurface else tint)
            if (body != null) {
                // Los errores llevan la salida del sistema tal cual: monoespaciada y seleccionable. El resto es prosa.
                if (kind == NoteKind.ERROR) DataText(body, color = MaterialTheme.colorScheme.onSurface)
                else Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(4.dp)); action() }
    }
}

/** Barra de progreso. fraction = null → indeterminada (no se conoce el total). */
@Composable
fun ProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
        if (fraction != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(fill))
        } else {
            val t = rememberInfiniteTransition()
            val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart))
            val seg = maxWidth * 0.3f
            Box(Modifier.offset(x = (maxWidth + seg) * x - seg).width(seg).fillMaxHeight().background(fill))
        }
    }
}

/** Estado de la última operación: progreso real, resultado o el error tal cual lo dio el sistema. */
@Composable
fun OperationPanel(runner: OpRunner) {
    if (!runner.started) return
    val p = runner.progress
    Panel {
        when {
            p.error != null -> Note(tr("No se completó", "Did not complete"), p.error, NoteKind.ERROR)
            p.isComplete -> Note(tr("Hecho", "Done"), p.label.ifBlank { null }, NoteKind.OK)
            else -> {
                Text(p.label.ifBlank { tr("Trabajando…", "Working…") }, style = MaterialTheme.typography.titleSmall)
                ProgressBar(if (p.totalBytes > 0) p.progressPct else null)
                if (p.totalBytes > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DataText("${p.writtenDisplay} / ${p.totalDisplay}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DataText("${(p.progressPct * 100).toInt()}%  ·  %.1f MB/s".format(p.speedMbs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    tr("No cierres la aplicación ni desconectes la unidad hasta que termine.", "Do not close the app or unplug the drive until it finishes."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!runner.running) SecondaryButton(tr("Cerrar", "Dismiss"), onClick = { runner.clear() })
    }
}

/** Resumen de la unidad destino, siempre con nombre, tamaño y ruta a la vista. */
@Composable
fun DriveSummary(drive: UsbDriveInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(drive.name, style = MaterialTheme.typography.titleMedium)
        DataText("${drive.sizeDisplay}  ·  ${drive.path}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DriveRow(drive: UsbDriveInfo, selected: Boolean, onSelect: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = null)
            DriveSummary(drive)
        }
    }
}

/**
 * Confirmación de una operación destructiva: resume qué se perderá y exige escribir el
 * identificador de la unidad. Un botón de "Aceptar" no basta para borrar un disco.
 */
@Composable
fun ConfirmDestructive(
    title: String,
    lines: List<String>,
    drive: UsbDriveInfo,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DriveSummary(drive)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    tr("Esta acción no se puede deshacer. Escribe «${drive.shortId}» para confirmar.", "This cannot be undone. Type “${drive.shortId}” to confirm."),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
                TextInput(typed, { typed = it }, label = drive.shortId, mono = true)
            }
        },
        confirmButton = {
            PrimaryButton(confirmLabel, onClick = onConfirm, danger = true, enabled = typed.trim().equals(drive.shortId, ignoreCase = true))
        },
        dismissButton = { SecondaryButton(tr("Cancelar", "Cancel"), onClick = onDismiss) }
    )
}
