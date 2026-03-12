package com.meshnet.chat.mesh.wifi;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.*;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * Wi-Fi Direct peer discovery and group formation.
 *
 * Discovers nearby Wi-Fi Direct peers, negotiates group ownership,
 * and provides connection info for socket-based data transfer.
 */
public class WifiDirectManager {

    private static final String TAG = "WifiDirect";

    private final Context context;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private boolean receiverRegistered = false;

    private final MutableLiveData<List<WifiP2pDevice>> peersLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<WifiP2pInfo> connectionInfoLive = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isConnectedLive = new MutableLiveData<>(false);

    public WifiDirectManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public LiveData<List<WifiP2pDevice>> getPeers() { return peersLive; }
    public LiveData<WifiP2pInfo> getConnectionInfo() { return connectionInfoLive; }
    public LiveData<Boolean> getIsConnected() { return isConnectedLive; }

    // ── Lifecycle ───────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    public void initialize() {
        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (manager == null) {
            Log.e(TAG, "Wi-Fi Direct not supported on this device");
            return;
        }
        channel = manager.initialize(context, context.getMainLooper(), null);

        receiver = new WifiDirectReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        context.registerReceiver(receiver, filter);
        receiverRegistered = true;
        Log.i(TAG, "Wi-Fi Direct initialized");
    }

    public void cleanup() {
        if (receiverRegistered && receiver != null) {
            context.unregisterReceiver(receiver);
            receiverRegistered = false;
        }
        disconnect();
    }

    // ── Discovery ───────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    public void discoverPeers() {
        if (manager == null || channel == null) return;

        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Peer discovery started");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Peer discovery failed: reason=" + reason);
            }
        });
    }

    public void stopDiscovery() {
        if (manager == null || channel == null) return;

        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Peer discovery stopped");
            }

            @Override
            public void onFailure(int reason) {
                Log.w(TAG, "Stop discovery failed: reason=" + reason);
            }
        });
    }

    // ── Connection ──────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    public void connectToPeer(WifiP2pDevice device) {
        if (manager == null || channel == null) return;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Connection initiated to " + device.deviceName);
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Connection failed: reason=" + reason);
            }
        });
    }

    public void disconnect() {
        if (manager == null || channel == null) return;

        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                isConnectedLive.postValue(false);
                Log.i(TAG, "Disconnected from Wi-Fi Direct group");
            }

            @Override
            public void onFailure(int reason) {
                Log.w(TAG, "Disconnect failed: reason=" + reason);
            }
        });
    }

    // ── Broadcast Receiver ──────────────────────────────────────

    private class WifiDirectReceiver extends BroadcastReceiver {

        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION: {
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        Log.w(TAG, "Wi-Fi Direct is disabled");
                        peersLive.postValue(new ArrayList<>());
                    }
                    break;
                }

                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION: {
                    if (manager != null) {
                        manager.requestPeers(channel, peerList -> {
                            List<WifiP2pDevice> devices = new ArrayList<>(peerList.getDeviceList());
                            peersLive.postValue(devices);
                            Log.d(TAG, "Peers updated: " + devices.size() + " found");
                        });
                    }
                    break;
                }

                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION: {
                    if (manager != null) {
                        manager.requestConnectionInfo(channel, info -> {
                            connectionInfoLive.postValue(info);
                            isConnectedLive.postValue(info != null && info.groupFormed);
                            if (info != null && info.groupFormed) {
                                Log.i(TAG, "Connected. Group owner: " + info.isGroupOwner
                                        + " Host: " + info.groupOwnerAddress);
                            }
                        });
                    }
                    break;
                }
            }
        }
    }
}
