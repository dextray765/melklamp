package com.dextray.melklamp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dextray.melklamp.LampViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

private data class Preset(val name: String, val argb: Int)

private val Presets = listOf(
    Preset("Тёплый", 0xFFFF9329.toInt()),
    Preset("Нейтральный", 0xFFFFD6AA.toInt()),
    Preset("Холодный", 0xFFC9E2FF.toInt()),
    Preset("Красный", 0xFFFF0000.toInt()),
    Preset("Оранжевый", 0xFFFF6E00.toInt()),
    Preset("Жёлтый", 0xFFFFDC00.toInt()),
    Preset("Зелёный", 0xFF00FF3C.toInt()),
    Preset("Бирюзовый", 0xFF00FFC8.toInt()),
    Preset("Синий", 0xFF0046FF.toInt()),
    Preset("Фиолетовый", 0xFF8C00FF.toInt()),
    Preset("Розовый", 0xFFFF008C.toInt()),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(vm: LampViewModel, deviceName: String, onDisconnect: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(deviceName, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Отключиться")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard(vm)
            BrightnessCard(vm)

            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Цвет") },
                    icon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Эффекты") },
                    icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text("Микрофон") },
                    icon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
                )
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> ColorTab(vm)
                    1 -> EffectsTab(vm)
                    else -> MicTab(vm)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- верхняя карточка

@Composable
private fun HeroCard(vm: LampViewModel) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    val target = when {
        !vm.power -> scheme.surfaceVariant
        vm.micMode >= 0 -> scheme.tertiary
        vm.effect >= 0 -> swatchOf(vm.effect) ?: scheme.primary
        else -> vm.currentColor
    }
    val lamp by animateColorAsState(target, tween(450), label = "lamp")
    val glowAlpha = if (vm.power) 0.30f + 0.55f * (vm.brightness / 100f) else 0f
    val onLamp = if (lamp.luminance() > 0.5f) Color.Black.copy(alpha = 0.8f) else Color.White

    Card(
        shape = RoundedCornerShape(40.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Brush.radialGradient(listOf(lamp.copy(alpha = glowAlpha), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.togglePower()
                    },
                    modifier = Modifier.size(104.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (vm.power) lamp else scheme.surfaceContainerHighest,
                        contentColor = if (vm.power) onLamp else scheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Питание", modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (vm.power) "Включено" else "Выключено",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun BrightnessCard(vm: LampViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Яркость", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${vm.brightness.roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BrightnessLow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = vm.brightness,
                    onValueChange = { vm.onBrightness(it) },
                    onValueChangeFinished = { vm.onBrightnessFinished() },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Icon(Icons.Rounded.BrightnessHigh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------- вкладка «Цвет»

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ColorTab(vm: LampViewModel) {
    val container = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = container) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                ColorWheel(
                    hue = vm.hue,
                    saturation = vm.saturation,
                    onChange = { h, s -> vm.onWheel(h, s) },
                    onChangeFinished = { vm.onWheelFinished() },
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                )
            }
        }

        Card(colors = container) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Быстрые цвета", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Presets.forEach { p ->
                        ColorDot(
                            argb = p.argb,
                            selected = vm.isStaticColor && nearColor(p.argb, vm.currentColor.toArgb()),
                            onClick = { vm.applyColor(p.argb) },
                        )
                    }
                }
            }
        }

        Card(colors = container) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Мои цвета", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { vm.saveFavorite() }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Сохранить текущий цвет")
                    }
                }
                if (vm.favorites.isEmpty()) {
                    Text(
                        "Выберите цвет на круге и нажмите «+», чтобы сохранить его. Долгое нажатие удаляет цвет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        vm.favorites.toList().forEach { argb ->
                            ColorDot(
                                argb = argb,
                                selected = vm.isStaticColor && nearColor(argb, vm.currentColor.toArgb()),
                                onClick = { vm.applyColor(argb) },
                                onLongClick = { vm.removeFavorite(argb) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorDot(
    argb: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = CircleShape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

private fun nearColor(a: Int, b: Int, tolerance: Int = 4): Boolean =
    abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= tolerance &&
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= tolerance &&
        abs((a and 0xFF) - (b and 0xFF)) <= tolerance

// ---------------------------------------------------------------- вкладка «Эффекты»

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectsTab(vm: LampViewModel) {
    val container = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = container) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Скорость", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${vm.speed.roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = vm.speed,
                        onValueChange = { vm.onSpeed(it) },
                        onValueChangeFinished = { vm.onSpeedFinished() },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
                Text(
                    "Скорость действует на анимированные эффекты; статичные цвета её игнорируют.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Инвертировать скорость",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = vm.speedInverted, onCheckedChange = { vm.updateSpeedInverted(it) })
                }
            }
        }

        EffectGroups.forEach { group ->
            Card(colors = container) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        group.effects.forEach { e ->
                            FilterChip(
                                selected = vm.effect == e.id,
                                onClick = { vm.selectEffect(e.id) },
                                label = { Text(e.name) },
                                leadingIcon = if (e.swatch != null) {
                                    {
                                        Box(
                                            Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(e.swatch)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}


// ---------------------------------------------------------------- вкладка «Микрофон»

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MicTab(vm: LampViewModel) {
    val scheme = MaterialTheme.colorScheme
    val container = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer)) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = scheme.onSecondaryContainer)
                Text(
                    "Работает встроенный микрофон на плате ленты: она сама реагирует на звук, " +
                        "телефон для этого не нужен.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSecondaryContainer,
                )
            }
        }

        Card(colors = container) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Чувствительность", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${vm.micSens.roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                }
                Slider(
                    value = vm.micSens,
                    onValueChange = { vm.onMicSens(it) },
                    onValueChangeFinished = { vm.onMicSensFinished() },
                    valueRange = 0f..100f,
                )
            }
        }

        Card(colors = container) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Режимы", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MicModes.forEach { m ->
                        FilterChip(
                            selected = vm.micMode == m.id,
                            onClick = { vm.selectMicMode(m.id) },
                            label = { Text(m.name) },
                        )
                    }
                }
            }
        }
    }
}
