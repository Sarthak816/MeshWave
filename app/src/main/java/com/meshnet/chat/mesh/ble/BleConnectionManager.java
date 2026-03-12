package com.meshnet.chat.mesh.ble;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.meshnet.chat.data.model.MeshPacket;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BLE GATT-based data channel for short message exchange.
 *
 * Uses a custom GATT service with one writable characteristic
 * for relaying serialized MeshPacket payloads.
 * For larger payloads, upgrade to Wi-Fi Direct after initial handshake.
 */
public class BleConnectionManager {

    private static final String TAG = "BleConnection";
    public static final UUID MESSAGE_CHAR_UUID =
            UUID.fromString("0000CAFE-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BluetoothGattServer gattServer;
    private final Map<String, BluetoothDevice> connectedDevices = new ConcurrentHashMap<>();

    /** Listener for incoming packets from peers. */
    public interface PacketListener {
        void onPacketReceived(String fromAddress, MeshPacket packet);
    }

    private PacketListener packetListener;

    public BleConnectionManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setPacketListener(PacketListener listener) {
        this.packetListener = listener;
    }

    // ── GATT Server (receiver side) ────────────────────────────

    @SuppressLint("MissingPermission")
    public void startGattServer() {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) return;

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                MESSAGE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        BluetoothGattService service = new BluetoothGattService(
                BleDiscoveryManager.MESH_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );
        service.addCharacteristic(characteristic);

        gattServer = manager.openGattServer(context, gattCallback);
        if (gattServer != null) {
            gattServer.addService(service);
            Log.i(TAG, "GATT server started");
        }
    }

    @SuppressLint("MissingPermission")
    public void stopGattServer() {
        if (gattServer != null) {
            gattServer.close();
            gattServer = null;
        }
        connectedDevices.clear();
    }

    private final BluetoothGattServerCallback gattCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            String address = device.getAddress();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.put(address, device);
                Log.d(TAG, "Device connected: " + address);
            } else {
                connectedDevices.remove(address);
                Log.d(TAG, "Device disconnected: " + address);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(
                BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic,
                boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value
        ) {
            if (MESSAGE_CHAR_UUID.equals(characteristic.getUuid()) && value != null) {
                try {
                    String payload = new String(value, StandardCharsets.UTF_8);
                    MeshPacket packet = gson.fromJson(payload, MeshPacket.class);
                    if (packetListener != null) {
                        packetListener.onPacketReceived(device.getAddress(), packet);
                    }
                    Log.d(TAG, "Received packet from " + device.getAddress());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse packet", e);
                }
            }

            if (responseNeeded && gattServer != null) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }
        }
    };

    // ── GATT Client (sender side) ──────────────────────────────

    @SuppressLint("MissingPermission")
    public void sendPacket(String deviceAddress, MeshPacket packet) {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) return;

        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null) return;

        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);

        device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close();
                }
            }

            @SuppressLint("MissingPermission")
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                BluetoothGattService service = gatt.getService(BleDiscoveryManager.MESH_SERVICE_UUID);
                if (service == null) {
                    gatt.disconnect();
                    return;
                }

                BluetoothGattCharacteristic charac = service.getCharacteristic(MESSAGE_CHAR_UUID);
                if (charac == null) {
                    gatt.disconnect();
                    return;
                }

                String json = gson.toJson(packet);
                charac.setValue(json.getBytes(StandardCharsets.UTF_8));
                gatt.writeCharacteristic(charac);
                Log.d(TAG, "Sent packet to " + deviceAddress);
            }

            @SuppressLint("MissingPermission")
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                gatt.disconnect();
            }
        });
    }

    public int getConnectedCount() {
        return connectedDevices.size();
    }
}
