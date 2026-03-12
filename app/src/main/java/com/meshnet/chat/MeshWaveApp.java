package com.meshnet.chat;

import android.app.Application;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Application class for MeshWave.
 * Handles device identity generation and global app state.
 */
public class MeshWaveApp extends Application {

    private static MeshWaveApp instance;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("meshwave", MODE_PRIVATE);
        ensureDeviceIdentity();
    }

    public static MeshWaveApp getInstance() {
        return instance;
    }

    public String getPeerId() {
        return prefs.getString("peer_id", "");
    }

    public String getDisplayName() {
        return prefs.getString("display_name", "");
    }

    public void setDisplayName(String name) {
        prefs.edit().putString("display_name", name).apply();
    }

    private void ensureDeviceIdentity() {
        if (!prefs.contains("peer_id")) {
            String id = UUID.randomUUID().toString();
            String name = "User-" + id.substring(0, 6);
            prefs.edit()
                    .putString("peer_id", id)
                    .putString("display_name", name)
                    .apply();
        }
    }
}
