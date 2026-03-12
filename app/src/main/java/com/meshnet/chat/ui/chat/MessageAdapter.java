package com.meshnet.chat.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.meshnet.chat.R;
import com.meshnet.chat.data.model.MessageEntity;
import com.meshnet.chat.data.model.MessagePriority;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Adapter for displaying chat messages in a RecyclerView.
 * Shows outgoing messages on the right and incoming on the left.
 */
public class MessageAdapter extends ListAdapter<MessageEntity, MessageAdapter.MessageViewHolder> {

    private final String localPeerId;
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    public MessageAdapter(String localPeerId) {
        super(DIFF_CALLBACK);
        this.localPeerId = localPeerId;
    }

    private static final DiffUtil.ItemCallback<MessageEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MessageEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull MessageEntity oldItem, @NonNull MessageEntity newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull MessageEntity oldItem, @NonNull MessageEntity newItem) {
                    return oldItem.getId().equals(newItem.getId())
                            && oldItem.getText().equals(newItem.getText());
                }
            };

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageEntity msg = getItem(position);
        boolean isOutgoing = msg.getSenderId() != null && msg.getSenderId().equals(localPeerId);
        boolean isSos = MessagePriority.SOS.name().equals(msg.getPriority());
        String time = TIME_FORMAT.format(new Date(msg.getTimestamp()));

        if (isOutgoing) {
            holder.outgoingContainer.setVisibility(View.VISIBLE);
            holder.incomingContainer.setVisibility(View.GONE);
            holder.outgoingText.setText(msg.getText());
            holder.outgoingTime.setText(time);

            if (isSos) {
                holder.outgoingContainer.setBackgroundResource(R.drawable.bg_bubble_sos);
            } else {
                holder.outgoingContainer.setBackgroundResource(R.drawable.bg_bubble_outgoing);
            }
        } else {
            holder.incomingContainer.setVisibility(View.VISIBLE);
            holder.outgoingContainer.setVisibility(View.GONE);
            holder.incomingSender.setText(msg.getSenderName());
            holder.incomingText.setText(msg.getText());
            holder.incomingTime.setText(time);

            if (msg.getHopCount() > 0) {
                holder.incomingHops.setVisibility(View.VISIBLE);
                holder.incomingHops.setText(msg.getHopCount() + " hop" + (msg.getHopCount() == 1 ? "" : "s"));
            } else {
                holder.incomingHops.setVisibility(View.GONE);
            }

            if (isSos) {
                holder.incomingContainer.setBackgroundResource(R.drawable.bg_bubble_sos);
            } else {
                holder.incomingContainer.setBackgroundResource(R.drawable.bg_bubble_incoming);
            }
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout outgoingContainer, incomingContainer;
        TextView outgoingText, outgoingTime;
        TextView incomingSender, incomingText, incomingTime, incomingHops;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            outgoingContainer = itemView.findViewById(R.id.outgoingContainer);
            incomingContainer = itemView.findViewById(R.id.incomingContainer);
            outgoingText = itemView.findViewById(R.id.outgoingText);
            outgoingTime = itemView.findViewById(R.id.outgoingTime);
            incomingSender = itemView.findViewById(R.id.incomingSender);
            incomingText = itemView.findViewById(R.id.incomingText);
            incomingTime = itemView.findViewById(R.id.incomingTime);
            incomingHops = itemView.findViewById(R.id.incomingHops);
        }
    }
}
