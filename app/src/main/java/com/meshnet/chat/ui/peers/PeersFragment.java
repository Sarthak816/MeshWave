package com.meshnet.chat.ui.peers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.meshnet.chat.R;
import com.meshnet.chat.mesh.MeshService;

import java.util.List;

/**
 * Fragment showing nearby discovered peers and mesh control.
 */
public class PeersFragment extends Fragment {

    private PeersViewModel viewModel;
    private PeerAdapter adapter;
    private RecyclerView peersRecycler;
    private TextView emptyText;
    private MaterialButton meshToggleButton;

    private MeshService meshService;
    private boolean bound = false;
    private boolean meshRunning = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshService.MeshBinder binder = (MeshService.MeshBinder) service;
            meshService = binder.getService();
            bound = true;

            // Observe peer changes from the service
            meshService.getDiscoveredPeers().observe(getViewLifecycleOwner(), peerMap -> {
                viewModel.updatePeers(peerMap);
            });

            meshService.getMeshActive().observe(getViewLifecycleOwner(), active -> {
                meshRunning = active;
                viewModel.setMeshActive(active);
                meshToggleButton.setText(active ? R.string.stop_mesh : R.string.start_mesh);
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            meshService = null;
            bound = false;
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PeersViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_peers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        peersRecycler = view.findViewById(R.id.peersRecycler);
        emptyText = view.findViewById(R.id.emptyPeersText);
        meshToggleButton = view.findViewById(R.id.meshToggleButton);

        adapter = new PeerAdapter();
        peersRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        peersRecycler.setAdapter(adapter);

        // Observe peers list
        viewModel.getPeerList().observe(getViewLifecycleOwner(), peers -> {
            if (peers == null || peers.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
                peersRecycler.setVisibility(View.GONE);
            } else {
                emptyText.setVisibility(View.GONE);
                peersRecycler.setVisibility(View.VISIBLE);
                adapter.submitList(peers);
            }
        });

        // Mesh toggle
        meshToggleButton.setOnClickListener(v -> {
            if (!bound || meshService == null) return;

            if (meshRunning) {
                meshService.stopMesh();
            } else {
                meshService.startMesh();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getContext(), MeshService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (bound) {
            requireContext().unbindService(serviceConnection);
            bound = false;
        }
    }
}
