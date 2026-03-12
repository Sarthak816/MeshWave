package com.meshnet.chat.ui.chat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.meshnet.chat.MeshWaveApp;
import com.meshnet.chat.R;
import com.meshnet.chat.data.model.MeshMessage;
import com.meshnet.chat.data.model.MessagePriority;
import com.meshnet.chat.mesh.MeshService;

/**
 * Chat fragment showing messages and input controls.
 */
public class ChatFragment extends Fragment {

    private ChatViewModel viewModel;
    private MessageAdapter adapter;
    private RecyclerView messagesRecycler;
    private TextView emptyText;
    private EditText messageInput;
    private ImageButton sendButton;
    private com.google.android.material.button.MaterialButton sosButton;

    private MeshService meshService;
    private boolean bound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshService.MeshBinder binder = (MeshService.MeshBinder) service;
            meshService = binder.getService();
            bound = true;
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
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        messagesRecycler = view.findViewById(R.id.messagesRecycler);
        emptyText = view.findViewById(R.id.emptyText);
        messageInput = view.findViewById(R.id.messageInput);
        sendButton = view.findViewById(R.id.sendButton);
        sosButton = view.findViewById(R.id.sosButton);

        String localId = MeshWaveApp.getInstance().getPeerId();
        adapter = new MessageAdapter(localId);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        messagesRecycler.setLayoutManager(layoutManager);
        messagesRecycler.setAdapter(adapter);

        // Observe messages
        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            if (messages == null || messages.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
                messagesRecycler.setVisibility(View.GONE);
            } else {
                emptyText.setVisibility(View.GONE);
                messagesRecycler.setVisibility(View.VISIBLE);
                adapter.submitList(messages, () -> {
                    messagesRecycler.scrollToPosition(messages.size() - 1);
                });
            }
        });

        // SOS toggle
        viewModel.getSosMode().observe(getViewLifecycleOwner(), active -> {
            if (active) {
                sosButton.setStrokeColorResource(R.color.error);
                sosButton.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                getResources().getColor(R.color.bubble_sos, null)));
            } else {
                sosButton.setStrokeColorResource(R.color.divider);
                sosButton.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                getResources().getColor(R.color.surface, null)));
            }
        });

        sosButton.setOnClickListener(v -> viewModel.toggleSosMode());

        // Send
        sendButton.setOnClickListener(v -> sendMessage());
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        if (bound && meshService != null) {
            // Send via mesh service
            if (viewModel.isSosActive()) {
                MeshMessage msg = new MeshMessage(
                        MeshWaveApp.getInstance().getPeerId(),
                        MeshWaveApp.getInstance().getDisplayName(),
                        viewModel.getCurrentRoomId(),
                        text
                );
                msg.setPriority(MessagePriority.SOS);
                meshService.getRouter().sendLocal(msg);
                meshService.getRepository().insert(msg);
            } else {
                meshService.sendMessage(text, viewModel.getCurrentRoomId());
            }
        } else {
            // Mesh not running, save locally
            MeshMessage msg = new MeshMessage(
                    MeshWaveApp.getInstance().getPeerId(),
                    MeshWaveApp.getInstance().getDisplayName(),
                    viewModel.getCurrentRoomId(),
                    text
            );
            if (viewModel.isSosActive()) {
                msg.setPriority(MessagePriority.SOS);
            }
            viewModel.getRepository().insert(msg);
        }

        messageInput.setText("");
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
