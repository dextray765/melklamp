package com.dextray.melklamp.ble

import java.util.UUID
import kotlin.random.Random

/**
 * Протокол ламп серии ELK/MELK (в оригинальном приложении — класс E1Achieve).
 *
 * Кадр — 9 байт:  7E | длина | команда | p1 p2 p3 p4 p5 | EF
 * Незаполненные параметры остаются 0xFF, а байт [7] по умолчанию 0x00.
 * Для имён, содержащих "ELK-*", команды питания / режима / яркости дополнительно
 * шифруются (21 байт), см. [encrypt].
 */
object LampProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val WRITE_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    val NOTIFY_UUID: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")

    private const val CMD_BRIGHTNESS = 0x01
    private const val CMD_SPEED = 0x02
    private const val CMD_MODE = 0x03
    private const val CMD_MIC = 0x06
    private const val CMD_ON_OFF = 0x04
    private const val CMD_COLOR = 0x05

    private const val LAMP_TYPE_RGB = 1
    private const val PALETTE_RGB = 3
    private const val APP_TYPE = 2
    private const val APP_VERSION = 1

    fun power(on: Boolean, encrypted: Boolean): ByteArray =
        frame(
            CMD_ON_OFF, encrypted,
            if (on) 0xFF else 0x00, LAMP_TYPE_RGB, if (on) 1 else 0, APP_TYPE, APP_VERSION,
        )

    /** percent: 0..100 */
    fun brightness(percent: Int, encrypted: Boolean): ByteArray =
        frame(CMD_BRIGHTNESS, encrypted, percent.coerceIn(0, 100), LAMP_TYPE_RGB, 0xFF, APP_TYPE, APP_VERSION)

    fun color(r: Int, g: Int, b: Int): ByteArray =
        frame(CMD_COLOR, false, PALETTE_RGB, r, g, b, 0x10)

    fun mode(id: Int, encrypted: Boolean): ByteArray =
        frame(CMD_MODE, encrypted, id, 3)

    /** percent: 0..100. По умолчанию в оригинале 100. */
    fun speed(percent: Int, inverted: Boolean): ByteArray {
        val p = percent.coerceIn(0, 100)
        return frame(CMD_SPEED, false, if (inverted) 100 - p else p)
    }

    /** Режимы «под звук» встроенного микрофона платы: id 128..131, тип режима 4. */
    fun micMode(id: Int, encrypted: Boolean): ByteArray =
        frame(CMD_MODE, encrypted, id, 4)

    /** Чувствительность встроенного микрофона, 0..100 (в оригинале по умолчанию 50). */
    fun micSensitivity(percent: Int): ByteArray =
        frame(CMD_MIC, false, percent.coerceIn(0, 100))

    private fun frame(cmd: Int, encrypted: Boolean, vararg params: Int): ByteArray {
        val b = intArrayOf(0x7E, if (cmd == CMD_BRIGHTNESS) 4 else 7, cmd, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0xEF)
        var i = 3
        for (p in params) {
            if (i >= 8) break
            b[i++] = p and 0xFF
        }
        val shouldEncrypt = encrypted && (cmd == CMD_ON_OFF || cmd == CMD_MODE || cmd == CMD_BRIGHTNESS)
        if (shouldEncrypt) {
            b[0] = 0xAA
            b[8] = 0x55
        }
        val plain = ByteArray(9) { b[it].toByte() }
        return if (shouldEncrypt) encrypt(plain) else plain
    }

    // ---- шифрование (EncryptionDecryptionKt в оригинале) ----

    private val PRESET_KEY = byteArrayOf(
        42, 127, -63, -108, 51, -34, 69, -32, -117, 17, 92, -90, 9, -14, 125, -72,
    )

    private fun ub(x: Byte): Int = x.toInt() and 0xFF

    internal fun encrypt(plain: ByteArray, random: ByteArray = Random.nextBytes(12)): ByteArray {
        val out = ByteArray(21)
        for (i in 0 until 9) {
            val ks = (((ub(random[i]) * 27) and 0xFF) xor
                ((ub(random[(i + 3) % 12]) + 55) and 0xFF) xor
                ((ub(random[(i + 7) % 12]) shr 2) and 0xFF) xor
                ((i * 85) and 0xFF)) and 0xFF
            out[i] = (ub(plain[i]) xor ks).toByte()
        }
        for (j in 0 until 12) {
            out[9 + j] = (ub(random[j]) xor ub(PRESET_KEY[j % 16])).toByte()
        }
        return out
    }

    /** Имя вида "ELK-*…" помечает лампу с шифрованием. */
    fun isEncryptedName(name: String): Boolean = name.contains("ELK-*")

    /** Похоже ли имя на лампу этого семейства. */
    fun looksLikeLamp(name: String): Boolean {
        val n = name.uppercase()
        return n.startsWith("MELK") || n.startsWith("ELK-") || n.startsWith("ELBU") ||
            n.startsWith("ELK#") || n.startsWith("HELK")
    }
}
