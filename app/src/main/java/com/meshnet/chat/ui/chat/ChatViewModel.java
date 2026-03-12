package com.meshnet.chat.ui.chat;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.meshnet.chat.data.local.MeshDatabase;
import com.meshnet.chat.data.local.MessageRepository;
import com.meshnet.chat.data.model.MessageEntity;

import java.util.List;

/**
 * ViewModel for the chat screen.
 * Exposes messages as LiveData and handles sending.
 */
public class ChatViewModel extends AndroidViewModel {

    private final MessageRepository repository;
    private final LiveData<List<MessageEntity>> messages;
    private final MutableLiveData<Boolean> sosMode = new MutableLiveData<>(false);

    private String currentRoomId = "general";

    public ChatViewModel(@NonNull Application application) {
        super(application);
        MeshDatabase db = MeshDatabase.getInstance(application);
        repository = new MessageRepository(db.messageDao());
        messages = repository.getMessagesByRoom(currentRoomId);
    }

    public LiveData<List<MessageEntity>> getMessages() {
        return messages;
    }

    public LiveData<Boolean> getSosMode() {
        return sosMode;
    }

    public void toggleSosMode() {
        Boolean current = sosMode.getValue();
        sosMode.setValue(current != null && !current);
    }

    public boolean isSosActive() {
        Boolean val = sosMode.getValue();
        return val != null && val;
    }

    public String getCurrentRoomId() {
        return currentRoomId;
    }

    public MessageRepository getRepository() {
        return repository;
    }
}
