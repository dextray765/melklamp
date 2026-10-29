package com.dextray.melklamp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import com.dextray.melklamp.ble.LampController
import kotlin.math.roundToInt

/** Состояние лампы заранее неизвестно (ответов от неё мы не читаем), поэтому держим его локально. */
class LampViewModel(app: Application) : AndroidViewModel(app) {

    val controller = LampController(app)
    private val prefs = app.getSharedPreferences("melk_lamp", Context.MODE_PRIVATE)

    var power by mutableStateOf(true); private set
    var hue by mutableFloatStateOf(prefs.getFloat("hue", 285f)); private set
    var saturation by mutableFloatStateOf(prefs.getFloat("sat", 0.85f)); private set
    var brightness by mutableFloatStateOf(prefs.getFloat("bri", 100f)); private set

    // В оригинале скорость по умолчанию 100. Новый ключ, чтобы не подхватить старые 50%.
    var speed by mutableFloatStateOf(prefs.getFloat("speed_v2", 100f)); private set
    var speedInverted by mutableStateOf(prefs.getBoolean("speed_inv", false)); private set

    var effect by mutableIntStateOf(-1); private set   // -1 — «не встроенный эффект»
    var micMode by mutableIntStateOf(-1); private set  // -1 — микрофон не используется
    var micSens by mutableFloatStateOf(prefs.getFloat("mic_sens", 50f)); private set

    /** Лента сейчас показывает один статичный цвет. */
    val isStaticColor: Boolean get() = effect < 0 && micMode < 0

    val favorites = mutableStateListOf<Int>().apply {
        prefs.getString("favorites", "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .forEach { add(it) }
    }

    val lastAddress: String? get() = prefs.getString("last_address", null)
    val lastName: String? get() = prefs.getString("last_name", null)

    val currentColor: Color get() = Color.hsv(hue, saturation, 1f)

    fun connect(address: String, name: String) {
        prefs.edit().putString("last_address", address).putString("last_name", name).apply()
        controller.connect(address, name)
    }

    /** Вызывается после успешного подключения: переносим на лампу последние настройки. */
    fun pushState() {
        power = true
        controller.setPower(true)
        controller.setBrightness(brightness.roundToInt())
        controller.setSpeed(speed.roundToInt(), speedInverted)
        when {
            micMode >= 0 -> {
                controller.setMicSensitivity(micSens.roundToInt())
                controller.setMicMode(micMode)
            }
            effect >= 0 -> {
                controller.setEffect(effect)
                controller.setSpeed(speed.roundToInt(), speedInverted)
            }
            else -> sendColor()
        }
    }

    fun togglePower() {
        power = !power
        controller.setPower(power)
    }

    fun onWheel(h: Float, s: Float) {
        hue = h
        saturation = s
        effect = -1
        micMode = -1
        sendColor()
    }

    fun onWheelFinished() {
        prefs.edit().putFloat("hue", hue).putFloat("sat", saturation).apply()
    }

    fun applyColor(argb: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        effect = -1
        micMode = -1
        controller.setColor(
            android.graphics.Color.red(argb),
            android.graphics.Color.green(argb),
            android.graphics.Color.blue(argb),
        )
        ensurePowerOn()
        onWheelFinished()
    }

    private fun sendColor() {
        val c = currentColor.toArgb()
        controller.setColor(
            android.graphics.Color.red(c),
            android.graphics.Color.green(c),
            android.graphics.Color.blue(c),
        )
    }

    private fun ensurePowerOn() {
        if (!power) {
            power = true
            controller.setPower(true)
        }
    }

    fun onBrightness(value: Float) {
        brightness = value
        controller.setBrightness(value.roundToInt())
    }

    fun onBrightnessFinished() {
        prefs.edit().putFloat("bri", brightness).apply()
    }

    fun onSpeed(value: Float) {
        speed = value
        controller.setSpeed(value.roundToInt(), speedInverted)
    }

    fun onSpeedFinished() {
        prefs.edit().putFloat("speed_v2", speed).apply()
    }

    fun updateSpeedInverted(on: Boolean) {
        speedInverted = on
        prefs.edit().putBoolean("speed_inv", on).apply()
        controller.setSpeed(speed.roundToInt(), on)
    }

    fun selectEffect(id: Int) {
        effect = id
        micMode = -1
        ensurePowerOn()
        controller.setEffect(id)
        controller.setSpeed(speed.roundToInt(), speedInverted)
    }

    fun selectMicMode(id: Int) {
        micMode = id
        effect = -1
        ensurePowerOn()
        controller.setMicMode(id)
    }

    fun onMicSens(value: Float) {
        micSens = value
        controller.setMicSensitivity(value.roundToInt())
    }

    fun onMicSensFinished() {
        prefs.edit().putFloat("mic_sens", micSens).apply()
    }

    fun saveFavorite() {
        val c = currentColor.toArgb()
        if (c !in favorites) {
            favorites.add(0, c)
            while (favorites.size > 12) favorites.removeAt(favorites.lastIndex)
            persistFavorites()
        }
    }

    fun removeFavorite(argb: Int) {
        favorites.remove(argb)
        persistFavorites()
    }

    private fun persistFavorites() {
        prefs.edit().putString("favorites", favorites.joinToString(",")).apply()
    }

    override fun onCleared() {
        controller.release()
    }
}
