package com.meshnet.chat.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.meshnet.chat.MeshWaveApp;
import com.meshnet.chat.R;
import com.meshnet.chat.mesh.MeshService;
import com.meshnet.chat.ui.chat.ChatFragment;
import com.meshnet.chat.ui.peers.PeersFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Main activity with tab navigation between Chat and Peers screens.
 * Handles permissions and starts the mesh foreground service.
 */
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private TabLayout tabLayout;
    private View statusDot;
    private TextView statusText;
    private TextView peerCountText;

    private MeshService meshService;
    private boolean bound = false;

    private final ChatFragment chatFragment = new ChatFragment();
    private final PeersFragment peersFragment = new PeersFragment();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshService.MeshBinder binder = (MeshService.MeshBinder) service;
            meshService = binder.getService();
            bound = true;
            observeServiceState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            meshService = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tabLayout);
        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        peerCountText = findViewById(R.id.peerCountText);

        // Toolbar menu
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                showSettingsDialog();
                return true;
            }
            return false;
        });

        // Tabs
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_chat));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_peers));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchFragment(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Default to chat tab
        if (savedInstanceState == null) {
            switchFragment(0);
        }

        // Request permissions then start service
        if (checkPermissions()) {
            startMeshService();
        } else {
            requestPermissions();
        }
    }

    private void switchFragment(int position) {
        Fragment fragment = position == 0 ? chatFragment : peersFragment;
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void observeServiceState() {
        if (meshService == null) return;

        meshService.getStatusText().observe(this, text -> {
            statusText.setText(text);
        });

        meshService.getPeerCount().observe(this, count -> {
            if (count == 0) {
                peerCountText.setText(R.string.peer_count_zero);
            } else if (count == 1) {
                peerCountText.setText(R.string.peer_count_one);
            } else {
                peerCountText.setText(getString(R.string.peer_count_many, count));
            }
        });

        meshService.getMeshActive().observe(this, active -> {
            int color = ContextCompat.getColor(this,
                    active ? R.color.peer_online : R.color.peer_offline);
            if (statusDot.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) statusDot.getBackground()).setColor(color);
            }
        });
    }

    // Permissions

    private boolean checkPermissions() {
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                getRequiredPermissions().toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
    }

    private List<String> getRequiredPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        return perms;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startMeshService();
            } else {
                statusText.setText(R.string.permission_required);
            }
        }
    }

    // Service lifecycle

    private void startMeshService() {
        Intent intent = new Intent(this, MeshService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            unbindService(serviceConnection);
            bound = false;
        }
        super.onDestroy();
    }

    // Settings dialog

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        EditText nameInput = dialogView.findViewById(R.id.nameInput);
        nameInput.setText(MeshWaveApp.getInstance().getDisplayName());

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (!name.isEmpty()) {
                        MeshWaveApp.getInstance().setDisplayName(name);
                        if (bound && meshService != null) {
                            meshService.setLocalDisplayName(name);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
