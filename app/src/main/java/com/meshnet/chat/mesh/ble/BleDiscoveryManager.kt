package com.meshnet.chat.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.meshnet.chat.data.model.ConnectionState
import com.meshnet.chat.data.model.MeshPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE‑based peer discovery.
 *
 * Advertises this device and scans for nearby mesh peers using
 * a custom service UUID. When a peer is found, it emits into
 * [discoveredPeers].
 */
class BleDiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "BleDiscovery"
        val MESH_SERVICE_UUID: UUID = UUID.fromString("0000FACE-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    private val _discoveredPeers = MutableStateFlow<Map<String, MeshPeer>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, MeshPeer>> = _discoveredPeers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // ── Scanning ────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startScan() {
        val bleScanner = adapter?.bluetoothLeScanner ?: run {
            Log.w(TAG, "BLE scanner not available")
            return
        }
        scanner = bleScanner

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        bleScanner.startScan(listOf(filter), settings, scanCallback)
        _isScanning.value = true
        Log.i(TAG, "BLE scan started")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
        _isScanning.value = false
        Log.i(TAG, "BLE scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            val name = device.name ?: "Unknown"

            val peer = MeshPeer(
                peerId = address,
                displayName = name,
                bluetoothAddress = address,
                lastSeen = System.currentTimeMillis(),
                rssi = result.rssi,
                connectionState = ConnectionState.DISCOVERED
            )

            _discoveredPeers.value = _discoveredPeers.value + (address to peer)
            Log.d(TAG, "Discovered peer: $name ($address) RSSI=${result.rssi}")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: errorCode=$errorCode")
            _isScanning.value = false
        }
    }

    // ── Advertising ─────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startAdvertising(localName: String) {
        val bleAdvertiser = adapter?.bluetoothLeAdvertiser ?: run {
            Log.w(TAG, "BLE advertiser not available")
            return
        }
        advertiser = bleAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .build()

        bleAdvertiser.startAdvertising(settings, data, advertiseCallback)
        Log.i(TAG, "BLE advertising started as '$localName'")
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        Log.i(TAG, "BLE advertising stopped")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed: errorCode=$errorCode")
        }
    }

    fun removeStalePeers(maxAgeMs: Long = 30_000) {
        val now = System.currentTimeMillis()
        _discoveredPeers.value = _discoveredPeers.value.filter {
            now - it.value.lastSeen < maxAgeMs
        }
    }
}
