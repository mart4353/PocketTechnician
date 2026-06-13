package com.pockettechnician.app.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sign

/** A bonded device the user can pick as the HID host. */
data class HidHost(val name: String, val address: String)

data class HidState(
    /** Device exposes the Bluetooth HID Device profile at all. */
    val supported: Boolean = true,
    /** Our HID app (SDP record + report map) is registered with the stack. */
    val registered: Boolean = false,
    /** A host is connected and ready to receive reports. */
    val connected: Boolean = false,
    val hostName: String? = null,
    val bondedHosts: List<HidHost> = emptyList(),
    val status: String = "Idle",
)

/**
 * Registers the tablet as a combined Bluetooth keyboard + mouse and sends
 * HID reports to a connected host (the target computer).
 *
 * Lifecycle: [start] grabs the BluetoothHidDevice profile proxy and registers
 * our app; the host then either connects on its own or we call [connect] for a
 * bonded device. [typeText] and [movePointer] stream reports once connected.
 */
class HidManager(private val context: Context) {

    private val _state = MutableStateFlow(HidState())
    val state: StateFlow<HidState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    /** Host we want to connect to, set before the profile proxy is ready. */
    private var pendingConnectAddress: String? = null

    private fun hasConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "Pocket Technician",
        "Keyboard and mouse control for on-site repair",
        "Pocket Technician",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        HidDescriptors.REPORT_MAP,
    )

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged registered=$registered")
            _state.value = _state.value.copy(
                registered = registered,
                status = if (registered) "Registered as HID — ready to connect" else "Unregistered",
            )
            if (registered) {
                refreshBondedHosts()
                pendingConnectAddress?.let { address ->
                    pendingConnectAddress = null
                    connect(address)
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val connected = state == BluetoothProfile.STATE_CONNECTED
            Log.d(TAG, "onConnectionStateChanged state=$state connected=$connected")
            connectedDevice = if (connected) device else null
            _state.value = _state.value.copy(
                connected = connected,
                hostName = if (connected) device.safeName() else null,
                status = when (state) {
                    BluetoothProfile.STATE_CONNECTED -> "Connected to ${device.safeName()}"
                    BluetoothProfile.STATE_CONNECTING -> "Connecting…"
                    BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting…"
                    else -> "Disconnected"
                },
            )
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = proxy as? BluetoothHidDevice
            Log.d(TAG, "HID_DEVICE proxy connected")
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = null
            _state.value = _state.value.copy(registered = false, connected = false, status = "HID service lost")
        }
    }

    /** Acquire the profile proxy and register our HID app. Safe to call repeatedly. */
    fun start() {
        val adapter = adapter
        if (adapter == null) {
            _state.value = _state.value.copy(supported = false, status = "Bluetooth not available")
            return
        }
        if (!hasConnectPermission()) {
            _state.value = _state.value.copy(status = "Bluetooth permission needed")
            return
        }
        if (hidDevice != null) {
            registerApp()
            return
        }
        val ok = adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        if (!ok) {
            _state.value = _state.value.copy(supported = false, status = "HID Device profile unsupported")
        } else {
            _state.value = _state.value.copy(status = "Starting HID service…")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val device = hidDevice ?: return
        if (!hasConnectPermission()) return
        runCatching {
            device.registerApp(sdpSettings, null, null, callbackExecutor, hidCallback)
        }.onFailure {
            Log.e(TAG, "registerApp failed", it)
            _state.value = _state.value.copy(status = "Registration failed: ${it.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedHosts() {
        if (!hasConnectPermission()) return
        val hosts = adapter?.bondedDevices.orEmpty().map { HidHost(it.safeName(), it.address) }
        _state.value = _state.value.copy(bondedHosts = hosts)
    }

    /** Connect to a bonded host by address; registers first if needed. */
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!hasConnectPermission()) {
            _state.value = _state.value.copy(status = "Bluetooth permission needed")
            return
        }
        val device = hidDevice
        if (device == null || !_state.value.registered) {
            pendingConnectAddress = address
            start()
            return
        }
        val target = adapter?.bondedDevices.orEmpty().firstOrNull { it.address == address }
        if (target == null) {
            _state.value = _state.value.copy(status = "Host not found among bonded devices")
            return
        }
        _state.value = _state.value.copy(status = "Connecting to ${target.safeName()}…")
        runCatching { device.connect(target) }.onFailure {
            _state.value = _state.value.copy(status = "Connect failed: ${it.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val device = hidDevice ?: return
        val target = connectedDevice ?: return
        if (!hasConnectPermission()) return
        runCatching { device.disconnect(target) }
    }

    /** Type a string by streaming key-press / key-release reports. Suspends until done. */
    suspend fun typeText(text: String): Boolean = withContext(Dispatchers.Default) {
        val device = hidDevice ?: return@withContext false
        val host = connectedDevice ?: return@withContext false
        if (!hasConnectPermission()) return@withContext false
        for (c in text) {
            val key = HidDescriptors.charToKey(c) ?: continue
            sendKeyboard(device, host, HidDescriptors.keyboardReport(key.first, key.second))
            delay(KEY_DELAY_MS)
            sendKeyboard(device, host, HidDescriptors.KEYBOARD_RELEASE)
            delay(KEY_DELAY_MS)
        }
        true
    }

    /**
     * Move the pointer by (dx, dy). HID mouse motion is relative, so a large
     * move is streamed as several reports each clamped to +/-127.
     */
    suspend fun movePointer(dx: Int, dy: Int): Boolean = withContext(Dispatchers.Default) {
        val device = hidDevice ?: return@withContext false
        val host = connectedDevice ?: return@withContext false
        if (!hasConnectPermission()) return@withContext false
        var remX = dx
        var remY = dy
        while (remX != 0 || remY != 0) {
            val stepX = clampStep(remX)
            val stepY = clampStep(remY)
            sendMouse(device, host, HidDescriptors.mouseReport(stepX, stepY))
            remX -= stepX
            remY -= stepY
            delay(MOUSE_DELAY_MS)
        }
        true
    }

    /**
     * Press and release a mouse button (left/right/middle bitmask from
     * [HidDescriptors]). Sends a button-down report, holds briefly, then
     * releases. Suspends until done.
     */
    suspend fun click(buttons: Int): Boolean = withContext(Dispatchers.Default) {
        val device = hidDevice ?: return@withContext false
        val host = connectedDevice ?: return@withContext false
        if (!hasConnectPermission()) return@withContext false
        sendMouse(device, host, HidDescriptors.mouseButtonReport(buttons))
        delay(CLICK_HOLD_MS)
        sendMouse(device, host, HidDescriptors.mouseButtonReport(0))
        true
    }

    @SuppressLint("MissingPermission")
    private fun sendKeyboard(device: BluetoothHidDevice, host: BluetoothDevice, report: ByteArray) {
        device.sendReport(host, HidDescriptors.REPORT_ID_KEYBOARD, report)
    }

    @SuppressLint("MissingPermission")
    private fun sendMouse(device: BluetoothHidDevice, host: BluetoothDevice, report: ByteArray) {
        device.sendReport(host, HidDescriptors.REPORT_ID_MOUSE, report)
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice?.safeName(): String {
        if (this == null) return "device"
        if (!hasConnectPermission()) return address
        return name ?: address
    }

    private fun clampStep(remaining: Int): Int {
        val limit = HidDescriptors.MOUSE_DELTA_LIMIT
        return if (abs(remaining) <= limit) remaining else limit * remaining.sign
    }

    fun stop() {
        scope.launch { /* reserved for cleanup */ }
        val device = hidDevice ?: return
        if (hasConnectPermission()) runCatching { device.unregisterApp() }
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
    }

    companion object {
        private const val TAG = "HidManager"
        private const val KEY_DELAY_MS = 12L
        private const val MOUSE_DELAY_MS = 8L
        private const val CLICK_HOLD_MS = 40L
    }
}
