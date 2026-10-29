package com.dextray.melklamp.ui

import androidx.compose.ui.graphics.Color

data class LampEffect(val id: Int, val name: String, val swatch: Color? = null)
data class EffectGroup(val title: String, val effects: List<LampEffect>)

/** Режимы из ELKRGBMode оригинального приложения (id — значение, которое уходит в лампу). */
val EffectGroups: List<EffectGroup> = listOf(
    EffectGroup(
        "Статичные",
        listOf(
            LampEffect(128, "Красный", Color(0xFFFF0000)),
            LampEffect(129, "Синий", Color(0xFF0000FF)),
            LampEffect(130, "Зелёный", Color(0xFF00FF00)),
            LampEffect(131, "Голубой", Color(0xFF00FFFF)),
            LampEffect(132, "Жёлтый", Color(0xFFFFFF00)),
            LampEffect(133, "Пурпурный", Color(0xFFFF00FF)),
            LampEffect(134, "Белый", Color(0xFFFFFFFF)),
        ),
    ),
    EffectGroup(
        "Прыжки",
        listOf(
            LampEffect(135, "3 цвета"),
            LampEffect(136, "7 цветов"),
        ),
    ),
    EffectGroup(
        "Плавные переходы",
        listOf(
            LampEffect(137, "3 цвета"),
            LampEffect(138, "7 цветов"),
            LampEffect(160, "3 цвета · мягко"),
            LampEffect(139, "Красный", Color(0xFFFF0000)),
            LampEffect(140, "Зелёный", Color(0xFF00FF00)),
            LampEffect(141, "Синий", Color(0xFF0000FF)),
            LampEffect(142, "Жёлтый", Color(0xFFFFFF00)),
            LampEffect(143, "Голубой", Color(0xFF00FFFF)),
            LampEffect(144, "Пурпурный", Color(0xFFFF00FF)),
            LampEffect(145, "Белый", Color(0xFFFFFFFF)),
            LampEffect(146, "Красный–зелёный"),
            LampEffect(147, "Красный–синий"),
            LampEffect(148, "Зелёный–синий"),
            LampEffect(173, "Текущий цвет"),
        ),
    ),
    EffectGroup(
        "Стробоскоп",
        listOf(
            LampEffect(149, "7 цветов"),
            LampEffect(161, "3 цвета"),
            LampEffect(150, "Красный", Color(0xFFFF0000)),
            LampEffect(151, "Зелёный", Color(0xFF00FF00)),
            LampEffect(152, "Синий", Color(0xFF0000FF)),
            LampEffect(153, "Жёлтый", Color(0xFFFFFF00)),
            LampEffect(154, "Голубой", Color(0xFF00FFFF)),
            LampEffect(155, "Пурпурный", Color(0xFFFF00FF)),
            LampEffect(156, "Белый", Color(0xFFFFFFFF)),
            LampEffect(159, "Текущий цвет"),
            LampEffect(174, "Текущий цвет · 2"),
        ),
    ),
    EffectGroup(
        "Сцены",
        listOf(
            LampEffect(157, "Рассвет", Color(0xFFFFB74D)),
            LampEffect(158, "Закат", Color(0xFFFF7043)),
            LampEffect(162, "Дыхание · 7 цветов"),
        ),
    ),
)

fun swatchOf(effectId: Int): Color? =
    EffectGroups.firstNotNullOfOrNull { g -> g.effects.firstOrNull { it.id == effectId } }?.swatch

/** Режимы встроенного микрофона платы (ELKMicRhythmMode в оригинале). */
val MicModes: List<LampEffect> = listOf(
    LampEffect(128, "Плавно · 7 цветов"),
    LampEffect(129, "Плавно · один цвет"),
    LampEffect(130, "Строб · 7 цветов"),
    LampEffect(131, "Вспышки · 7 цветов"),
)
