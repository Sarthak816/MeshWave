package com.meshnet.chat.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.util.Log
import com.meshnet.chat.data.model.MeshMessage
import com.meshnet.chat.data.model.MeshPacket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * BLE GATT‑based data channel for short message exchange.
 *
 * Uses a custom GATT service with one writable characteristic
 * for relaying serialized [MeshPacket] payloads (≤ 512 bytes typical).
 * For larger payloads, upgrade to Wi‑Fi Direct after initial handshake.
 */
class BleConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "BleConnection"
        val MESSAGE_CHAR_UUID: UUID = UUID.fromString("0000CAFE-0000-1000-8000-00805F9B34FB")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = mutableMapOf<String, BluetoothDevice>()

    private val _incomingPackets = MutableSharedFlow<Pair<String, MeshPacket>>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<Pair<String, MeshPacket>> = _incomingPackets.asSharedFlow()

    // ── GATT Server (receiver side) ────────────────────────────

    @SuppressLint("MissingPermission")
    fun startGattServer() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val characteristic = BluetoothGattCharacteristic(
            MESSAGE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val service = BluetoothGattService(
            BleDiscoveryManager.MESH_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        ).apply { addCharacteristic(characteristic) }

        gattServer = manager.openGattServer(context, gattCallback).apply {
            addService(service)
        }
        Log.i(TAG, "GATT server started")
    }

    fun stopGattServer() {
        gattServer?.close()
        gattServer = null
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices[address] = device
                Log.d(TAG, "Device connected: $address")
            } else {
                connectedDevices.remove(address)
                Log.d(TAG, "Device disconnected: $address")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid == MESSAGE_CHAR_UUID && value != null) {
                try {
                    val payload = String(value, Charsets.UTF_8)
                    val packet = json.decodeFromString<MeshPacket>(payload)
                    _incomingPackets.tryEmit(device.address to packet)
                    Log.d(TAG, "Received packet from ${device.address}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse packet", e)
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    // ── GATT Client (sender side) ──────────────────────────────

    @SuppressLint("MissingPermission")
    fun sendPacket(deviceAddress: String, packet: MeshPacket) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return
        val device = adapter.getRemoteDevice(deviceAddress)

        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(BleDiscoveryManager.MESH_SERVICE_UUID) ?: return
                val char = service.getCharacteristic(MESSAGE_CHAR_UUID) ?: return

                val payload = json.encodeToString(packet).toByteArray(Charsets.UTF_8)
                char.value = payload
                gatt.writeCharacteristic(char)
                Log.d(TAG, "Sent packet to $deviceAddress")
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
            ) {
                gatt.disconnect()
                gatt.close()
            }
        })
    }
}
