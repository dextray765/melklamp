package com.dextray.melklamp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

data class LampDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isLamp: Boolean,
)

sealed interface LinkState {
    data object Disconnected : LinkState
    data class Connecting(val name: String) : LinkState
    data class Connected(val name: String, val address: String) : LinkState
    data class Error(val message: String) : LinkState
}

/**
 * Поиск, подключение и отправка команд.
 * Команды одного типа (например, цвет при перетаскивании) схлопываются: в очереди
 * остаётся только последнее значение, поэтому BLE не «захлёбывается».
 */
@SuppressLint("MissingPermission")
class LampController(context: Context) {

    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<LampDevice>>(emptyList())
    val devices: StateFlow<List<LampDevice>> = _devices

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _link = MutableStateFlow<LinkState>(LinkState.Disconnected)
    val link: StateFlow<LinkState> = _link

    val isBluetoothOn: Boolean get() = adapter?.isEnabled == true
    val hasBluetooth: Boolean get() = adapter != null

    // ---------- поиск ----------

    private val found = LinkedHashMap<String, LampDevice>()
    private var scanStopJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { handle(it) }
        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
            _link.value = LinkState.Error("Не удалось начать поиск (код $errorCode)")
        }
    }

    private fun handle(r: ScanResult) {
        val name = r.scanRecord?.deviceName ?: r.device.name ?: return
        val advertisesService = r.scanRecord?.serviceUuids?.any { it.uuid == LampProtocol.SERVICE_UUID } == true
        val d = LampDevice(
            address = r.device.address,
            name = name,
            rssi = r.rssi,
            isLamp = LampProtocol.looksLikeLamp(name) || advertisesService,
        )
        synchronized(found) {
            found[d.address] = d
            _devices.value = found.values.sortedWith(
                compareByDescending<LampDevice> { it.isLamp }.thenByDescending { it.rssi },
            )
        }
    }

    fun startScan() {
        if (_scanning.value) return
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _link.value = LinkState.Error("Bluetooth выключен")
            return
        }
        synchronized(found) {
            found.clear()
            _devices.value = emptyList()
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(null, settings, scanCallback) }
            .onSuccess { _scanning.value = true }
            .onFailure { _link.value = LinkState.Error("Не удалось начать поиск") }
        scanStopJob?.cancel()
        scanStopJob = scope.launch {
            delay(12_000)
            stopScan()
        }
    }

    fun stopScan() {
        scanStopJob?.cancel()
        if (_scanning.value) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
            _scanning.value = false
        }
    }

    // ---------- подключение ----------

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var encrypted = false
    private var connectJob: Job? = null
    private var discoverJob: Job? = null
    private val discoveryStarted = AtomicBoolean(false)

    fun connect(address: String, name: String) {
        stopScan()
        teardown()
        val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: run {
            _link.value = LinkState.Error("Неверный адрес устройства")
            return
        }
        encrypted = LampProtocol.isEncryptedName(name)
        discoveryStarted.set(false)
        _link.value = LinkState.Connecting(name)
        gatt = device.connectGatt(app, false, GattCallback(name, address), BluetoothDevice.TRANSPORT_LE)
        connectJob?.cancel()
        connectJob = scope.launch {
            delay(15_000)
            if (_link.value is LinkState.Connecting) {
                teardown()
                _link.value = LinkState.Error("Лампа не отвечает. Подойдите ближе и попробуйте ещё раз")
            }
        }
    }

    fun disconnect() {
        teardown()
        _link.value = LinkState.Disconnected
    }

    fun clearError() {
        if (_link.value is LinkState.Error) _link.value = LinkState.Disconnected
    }

    private fun teardown() {
        connectJob?.cancel()
        discoverJob?.cancel()
        senderJob?.cancel()
        senderJob = null
        synchronized(pending) { pending.clear() }
        writeAck?.complete(false)
        writeChar = null
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
    }

    private inner class GattCallback(
        private val name: String,
        private val address: String,
    ) : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "connection callback status=$status state=$newState current=${g === gatt}")
            if (g !== gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                // Шифрованные кадры занимают 21 байт; остальным командам хватает
                // стандартного MTU. Не меняем приоритет соединения без необходимости.
                if (encrypted) main.post { runCatching { g.requestMtu(185) } }
                discoverJob?.cancel()
                discoverJob = scope.launch {
                    delay(1_500) // не все лампы отвечают на запрос MTU
                    startDiscovery(g)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val previousLink = _link.value
                teardown()
                _link.value = when (previousLink) {
                    is LinkState.Connecting -> LinkState.Error("Не удалось подключиться (GATT $status)")
                    is LinkState.Connected -> LinkState.Error("Соединение потеряно (GATT $status)")
                    else -> LinkState.Disconnected
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU callback mtu=$mtu status=$status current=${g === gatt}")
            if (g === gatt) startDiscovery(g)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.i(TAG, "services callback status=$status current=${g === gatt}")
            if (g !== gatt) return
            val ch = g.getService(LampProtocol.SERVICE_UUID)?.getCharacteristic(LampProtocol.WRITE_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || ch == null) {
                teardown()
                _link.value = LinkState.Error("Не найден сервис FFF0 — это точно лампа?")
                return
            }
            writeChar = ch
            connectJob?.cancel()
            startSender()
            _link.value = LinkState.Connected(name, address)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "write callback status=$status current=${g === gatt}")
            if (g === gatt) writeAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun startDiscovery(g: BluetoothGatt) {
        if (discoveryStarted.compareAndSet(false, true)) {
            main.post { runCatching { g.discoverServices() } }
        }
    }

    // ---------- отправка ----------

    private val pending = LinkedHashMap<Int, ByteArray>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var senderJob: Job? = null
    @Volatile private var writeAck: CompletableDeferred<Boolean>? = null

    private fun startSender() {
        senderJob?.cancel()
        senderJob = scope.launch {
            for (ignored in wake) {
                while (true) {
                    val frame = synchronized(pending) {
                        val it = pending.entries.iterator()
                        if (it.hasNext()) {
                            val e = it.next()
                            it.remove()
                            e.value
                        } else {
                            null
                        }
                    } ?: break
                    write(frame)
                    // The original controller spaces SPEED, MODE, and MIC commands
                    // by 100 ms; rapid slider writes can be dropped by lamp firmware.
                    delay(if (frame.size > 2 && frame[2].toInt() in SLOW_COMMANDS) 100 else 5)
                }
            }
        }
    }

    /**
     * Запись без долгих ожиданий. Если стек занят (предыдущая запись ещё в пути),
     * повторяем через 4 мс, но не дольше 300 мс. Подтверждение ждём коротко:
     * оно нужно лишь затем, чтобы не упереться в «busy», а не как гарантия доставки.
     */
    @Suppress("DEPRECATION")
    private suspend fun write(frame: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = writeChar ?: return false
        // The original app uses acknowledged GATT writes when available. Some lamp
        // firmwares disconnect when mode changes are sent as write-without-response.
        val supportsWrite = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val supportsWriteNoResponse = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val noResponse = !supportsWrite && supportsWriteNoResponse
        val type = if (noResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val deadline = SystemClock.elapsedRealtime() + 300
        while (true) {
            val ack = CompletableDeferred<Boolean>()
            writeAck = ack
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, frame, type) == BluetoothStatusCodes.SUCCESS
            } else {
                ch.writeType = type
                ch.value = frame
                g.writeCharacteristic(ch)
            }
            Log.d(TAG, "write cmd=${frame[2].toInt() and 0xFF} bytes=${frame.size} type=$type started=$started")
            if (started) {
                // Android can deliver a successful write callback several hundred
                // milliseconds late (observed on the target phone). Keep the next
                // command behind it so a late callback cannot be mistaken for the
                // following write.
                val result = withTimeoutOrNull(if (noResponse) 30L else 1_000L) { ack.await() }
                if (result == null) Log.w(TAG, "write callback timeout cmd=${frame[2].toInt() and 0xFF}")
                if (result == false) Log.w(TAG, "write rejected by GATT cmd=${frame[2].toInt() and 0xFF}")
                return true
            }
            if (SystemClock.elapsedRealtime() > deadline) return false
            delay(4)
        }
    }

    private fun enqueue(key: Int, frame: ByteArray) {
        if (_link.value !is LinkState.Connected) return
        synchronized(pending) {
            pending.remove(key) // новое значение уходит в конец очереди
            pending[key] = frame
        }
        wake.trySend(Unit)
    }

    fun setPower(on: Boolean) = enqueue(KEY_POWER, LampProtocol.power(on, encrypted))
    fun setBrightness(percent: Int) = enqueue(KEY_BRIGHTNESS, LampProtocol.brightness(percent, encrypted))
    fun setColor(r: Int, g: Int, b: Int) = enqueue(KEY_COLOR, LampProtocol.color(r, g, b))
    fun setEffect(id: Int) = enqueue(KEY_MODE, LampProtocol.mode(id, encrypted))
    fun setSpeed(percent: Int, inverted: Boolean) = enqueue(KEY_SPEED, LampProtocol.speed(percent, inverted))

    /** Режим «под звук» встроенного микрофона. Делит ключ с обычными режимами — побеждает последний. */
    fun setMicMode(id: Int) = enqueue(KEY_MODE, LampProtocol.micMode(id, encrypted))
    fun setMicSensitivity(percent: Int) = enqueue(KEY_MIC, LampProtocol.micSensitivity(percent))

    fun release() {
        stopScan()
        teardown()
        scope.cancel()
    }

    private companion object {
        const val TAG = "MELK_BLE"
        val SLOW_COMMANDS = setOf(0x02, 0x03, 0x06)
        const val KEY_POWER = 1
        const val KEY_BRIGHTNESS = 2
        const val KEY_COLOR = 3
        const val KEY_MODE = 4
        const val KEY_SPEED = 5
        const val KEY_MIC = 6
    }
}
