package com.meshnet.chat.ui.peers;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.meshnet.chat.R;
import com.meshnet.chat.data.model.ConnectionState;
import com.meshnet.chat.data.model.MeshPeer;

/**
 * Adapter for displaying discovered mesh peers.
 */
public class PeerAdapter extends ListAdapter<MeshPeer, PeerAdapter.PeerViewHolder> {

    public PeerAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<MeshPeer> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MeshPeer>() {
                @Override
                public boolean areItemsTheSame(@NonNull MeshPeer oldItem, @NonNull MeshPeer newItem) {
                    return oldItem.getPeerId().equals(newItem.getPeerId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull MeshPeer oldItem, @NonNull MeshPeer newItem) {
                    return oldItem.getPeerId().equals(newItem.getPeerId())
                            && oldItem.getRssi() == newItem.getRssi()
                            && oldItem.getConnectionState() == newItem.getConnectionState();
                }
            };

    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_peer, parent, false);
        return new PeerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        MeshPeer peer = getItem(position);

        holder.peerName.setText(peer.getDisplayName());

        String addr = peer.getBluetoothAddress();
        if (addr == null) addr = peer.getWifiDirectAddress();
        if (addr == null) addr = peer.getPeerId();
        holder.peerAddress.setText(addr);

        holder.peerRssi.setText(peer.getRssi() + " dBm");

        // Color the status dot
        boolean isConnected = peer.getConnectionState() == ConnectionState.CONNECTED
                || peer.getConnectionState() == ConnectionState.DISCOVERED;
        int dotColor = ContextCompat.getColor(holder.itemView.getContext(),
                isConnected ? R.color.peer_online : R.color.peer_offline);

        if (holder.statusDot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) holder.statusDot.getBackground()).setColor(dotColor);
        }
    }

    static class PeerViewHolder extends RecyclerView.ViewHolder {
        View statusDot;
        TextView peerName, peerAddress, peerRssi;

        PeerViewHolder(@NonNull View itemView) {
            super(itemView);
            statusDot = itemView.findViewById(R.id.peerStatusDot);
            peerName = itemView.findViewById(R.id.peerName);
            peerAddress = itemView.findViewById(R.id.peerAddress);
            peerRssi = itemView.findViewById(R.id.peerRssi);
        }
    }
}
