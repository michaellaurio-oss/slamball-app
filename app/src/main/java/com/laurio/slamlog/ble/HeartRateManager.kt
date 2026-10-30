package com.laurio.slamlog.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import com.laurio.slamlog.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Talks to any chest strap that uses the standard Bluetooth Heart Rate service
 * (the COOSPO H808S does), so no vendor SDK is needed.
 */
@SuppressLint("MissingPermission") // Permissions are requested in MainActivity; calls are wrapped in try/catch.
class HeartRateManager(context: Context, private val store: SettingsStore) {

    enum class State { IDLE, SCANNING, CONNECTING, CONNECTED, WAITING }

    data class Device(val name: String, val address: String)

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter?
        get() = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _deviceName = MutableStateFlow(store.deviceName)
    val deviceName: StateFlow<String?> = _deviceName

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var gatt: BluetoothGatt? = null
    private var userDisconnected = false
    @Volatile private var lastReadingAt = 0L

    val hasSavedStrap: Boolean get() = store.deviceAddress != null

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    private val _bluetoothOn = MutableStateFlow(isBluetoothOn())
    /** Follows Bluetooth being switched on or off, so the screen can update right away. */
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn

    /** Set by the owner once the Bluetooth permission is granted; used to auto-reconnect. */
    @Volatile var canConnect = false

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val st = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            _bluetoothOn.value = st == BluetoothAdapter.STATE_ON
            when (st) {
                BluetoothAdapter.STATE_ON -> if (canConnect) autoConnect()
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    stopScan()
                    closeGatt()
                    _heartRate.value = null
                    _state.value = State.IDLE
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext, btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Latest heart rate, or null if the strap hasn't sent a valid reading in the last 5 seconds. */
    fun currentBpm(): Int? {
        val v = _heartRate.value ?: return null
        return if (SystemClock.elapsedRealtime() - lastReadingAt <= 5_000) v else null
    }

    // ---------- Scanning ----------

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothOn()) {
            _error.value = "Turn on Bluetooth first."
            return
        }
        _error.value = null
        _devices.value = emptyList()
        try {
            // Unfiltered scan, then keep only heart rate devices. Some straps don't put the
            // service UUID in every advertisement, so we also match on common names.
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(null, settings, scanCallback)
            _state.value = State.SCANNING
            handler.removeCallbacks(stopScanRunnable)
            handler.postDelayed(stopScanRunnable, 15_000)
        } catch (e: SecurityException) {
            _error.value = "Allow the Nearby devices permission to find your strap."
        }
    }

    private val stopScanRunnable = Runnable { stopScan() }

    fun stopScan() {
        handler.removeCallbacks(stopScanRunnable)
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        if (_state.value == State.SCANNING) _state.value = State.IDLE
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val name = record?.deviceName ?: runCatching { result.device.name }.getOrNull()
            val advertisesHr = record?.serviceUuids?.contains(ParcelUuid(HR_SERVICE)) == true
            val looksLikeHr = name != null && NAME_HINT.containsMatchIn(name)
            if (!advertisesHr && !looksLikeHr) return
            val address = result.device.address
            if (_devices.value.none { it.address == address }) {
                _devices.value = _devices.value + Device(name ?: "Heart rate monitor", address)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _error.value = "Bluetooth scan failed (code $errorCode). Try again."
            _state.value = State.IDLE
        }
    }

    // ---------- Connecting ----------

    /** Connect to a strap the user picked from the scan list. */
    fun connect(device: Device) {
        stopScan()
        store.deviceAddress = device.address
        store.deviceName = device.name
        _deviceName.value = device.name
        open(device.address, autoConnect = false)
    }

    /** On app start: quietly wait for the saved strap and connect whenever it shows up. */
    fun autoConnect() {
        val address = store.deviceAddress ?: return
        if (gatt != null || !isBluetoothOn()) return
        open(address, autoConnect = true)
    }

    private fun open(address: String, autoConnect: Boolean) {
        userDisconnected = false
        _error.value = null
        closeGatt()
        try {
            val device = adapter?.getRemoteDevice(address) ?: return
            _state.value = if (autoConnect) State.WAITING else State.CONNECTING
            gatt = device.connectGatt(appContext, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _state.value = State.IDLE
            _error.value = "Allow the Nearby devices permission to connect."
        } catch (e: IllegalArgumentException) {
            _state.value = State.IDLE
        }
    }

    fun disconnect() {
        userDisconnected = true
        handler.removeCallbacksAndMessages(null)
        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }
        closeGatt()
        _heartRate.value = null
        _state.value = State.IDLE
    }

    /** Disconnect and stop auto-connecting to the saved strap. */
    fun forgetStrap() {
        disconnect()
        store.deviceAddress = null
        store.deviceName = null
        _deviceName.value = null
    }

    fun release() {
        runCatching { appContext.unregisterReceiver(btReceiver) }
        stopScan()
        disconnect()
    }

    private fun closeGatt() {
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Small delay makes service discovery more reliable on some phones.
                handler.postDelayed({
                    try {
                        g.discoverServices()
                    } catch (_: SecurityException) {
                    }
                }, 300)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post {
                    if (gatt !== g) {
                        // An old connection we already replaced; just clean it up.
                        runCatching { g.close() }
                        return@post
                    }
                    closeGatt()
                    _heartRate.value = null
                    if (userDisconnected) {
                        _state.value = State.IDLE
                    } else {
                        // Strap dropped (out of range, taken off, battery). Wait for it to come back.
                        store.deviceAddress?.let { open(it, autoConnect = true) } ?: run { _state.value = State.IDLE }
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (ch == null) {
                _error.value = "That device doesn't offer heart rate."
                handler.post { disconnect() }
                return
            }
            try {
                g.setCharacteristicNotification(ch, true)
                val cccd = ch.getDescriptor(CCCD)
                if (cccd != null) {
                    // "Connected" is shown once the strap confirms notifications (onDescriptorWrite),
                    // or when the first reading arrives, whichever comes first.
                    if (Build.VERSION.SDK_INT >= 33) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                } else {
                    _state.value = State.CONNECTED // no config descriptor; readings may still arrive
                }
            } catch (_: SecurityException) {
                _error.value = "Allow the Nearby devices permission to read heart rate."
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (d.uuid != CCCD) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.CONNECTED
            } else {
                _error.value = "The strap didn't start sending heart rate (error $status). Tap Reconnect."
                handler.post { disconnect() }
            }
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            if (ch.uuid == HR_MEASUREMENT) parse(value)
        }

        // Android 12 and older
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = ch.value ?: return
            if (ch.uuid == HR_MEASUREMENT) parse(value)
        }
    }

    /** Parse a standard Heart Rate Measurement packet (Bluetooth SIG 0x2A37). */
    private fun parse(data: ByteArray) {
        if (data.size < 2) return
        val flags = data[0].toInt() and 0xFF
        val sixteenBit = flags and 0x01 != 0
        val contactSupported = flags and 0x04 != 0
        val contactDetected = flags and 0x02 != 0
        val bpm = if (sixteenBit) {
            if (data.size < 3) return
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            data[1].toInt() and 0xFF
        }
        if (contactSupported && !contactDetected) {
            _heartRate.value = null // strap isn't touching skin
            return
        }
        if (bpm in 25..250) {
            lastReadingAt = SystemClock.elapsedRealtime()
            _heartRate.value = bpm
            if (_state.value != State.CONNECTED) _state.value = State.CONNECTED
        }
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val NAME_HINT = Regex("(?i)coospo|h808|h6m|h9z|hrm|heart|polar h|tickr")
    }
}
