package com.meshnet.chat.mesh.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.meshnet.chat.data.model.ConnectionState;
import com.meshnet.chat.data.model.MeshPeer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BLE-based peer discovery.
 *
 * Advertises this device and scans for nearby mesh peers using
 * a custom service UUID. Discovered peers are emitted via LiveData.
 */
public class BleDiscoveryManager {

    private static final String TAG = "BleDiscovery";
    public static final UUID MESH_SERVICE_UUID =
            UUID.fromString("0000FACE-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private BluetoothLeScanner scanner;
    private BluetoothLeAdvertiser advertiser;

    private final MutableLiveData<Map<String, MeshPeer>> discoveredPeersLive = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Boolean> isScanningLive = new MutableLiveData<>(false);

    public BleDiscoveryManager(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    public LiveData<Map<String, MeshPeer>> getDiscoveredPeers() {
        return discoveredPeersLive;
    }

    public LiveData<Boolean> getIsScanning() {
        return isScanningLive;
    }

    private BluetoothAdapter getAdapter() {
        return bluetoothManager != null ? bluetoothManager.getAdapter() : null;
    }

    // ── Scanning ────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    public void startScan() {
        BluetoothAdapter adapter = getAdapter();
        if (adapter == null) {
            Log.w(TAG, "BluetoothAdapter not available");
            return;
        }
        BluetoothLeScanner bleScanner = adapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Log.w(TAG, "BLE scanner not available");
            return;
        }
        this.scanner = bleScanner;

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(MESH_SERVICE_UUID))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build();

        bleScanner.startScan(java.util.Collections.singletonList(filter), settings, scanCallback);
        isScanningLive.postValue(true);
        Log.i(TAG, "BLE scan started");
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (scanner != null) {
            scanner.stopScan(scanCallback);
        }
        isScanningLive.postValue(false);
        Log.i(TAG, "BLE scan stopped");
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String address = result.getDevice().getAddress();
            String name = result.getDevice().getName();
            if (name == null) name = "Unknown";

            MeshPeer peer = new MeshPeer(address, name, address);
            peer.setLastSeen(System.currentTimeMillis());
            peer.setRssi(result.getRssi());
            peer.setConnectionState(ConnectionState.DISCOVERED);

            Map<String, MeshPeer> current = discoveredPeersLive.getValue();
            if (current == null) current = new HashMap<>();
            Map<String, MeshPeer> updated = new HashMap<>(current);
            updated.put(address, peer);
            discoveredPeersLive.postValue(updated);

            Log.d(TAG, "Discovered peer: " + name + " (" + address + ") RSSI=" + result.getRssi());
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE scan failed: errorCode=" + errorCode);
            isScanningLive.postValue(false);
        }
    };

    // ── Advertising ─────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    public void startAdvertising() {
        BluetoothAdapter adapter = getAdapter();
        if (adapter == null) return;

        BluetoothLeAdvertiser bleAdvertiser = adapter.getBluetoothLeAdvertiser();
        if (bleAdvertiser == null) {
            Log.w(TAG, "BLE advertiser not available");
            return;
        }
        this.advertiser = bleAdvertiser;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(MESH_SERVICE_UUID))
                .build();

        bleAdvertiser.startAdvertising(settings, data, advertiseCallback);
        Log.i(TAG, "BLE advertising started");
    }

    @SuppressLint("MissingPermission")
    public void stopAdvertising() {
        if (advertiser != null) {
            advertiser.stopAdvertising(advertiseCallback);
        }
        Log.i(TAG, "BLE advertising stopped");
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "Advertising started successfully");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "Advertising failed: errorCode=" + errorCode);
        }
    };

    /** Remove peers not seen within maxAgeMs. */
    public void removeStalePeers(long maxAgeMs) {
        long now = System.currentTimeMillis();
        Map<String, MeshPeer> current = discoveredPeersLive.getValue();
        if (current == null) return;

        Map<String, MeshPeer> filtered = new HashMap<>();
        for (Map.Entry<String, MeshPeer> entry : current.entrySet()) {
            if (now - entry.getValue().getLastSeen() < maxAgeMs) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        discoveredPeersLive.postValue(filtered);
    }
}
