package com.meshnet.chat.ui.peers;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.meshnet.chat.data.model.MeshPeer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for the peers screen.
 * Exposes discovered peers and mesh status.
 */
public class PeersViewModel extends AndroidViewModel {

    private final MutableLiveData<List<MeshPeer>> peerList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> meshActive = new MutableLiveData<>(false);

    public PeersViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<MeshPeer>> getPeerList() {
        return peerList;
    }

    public LiveData<Boolean> getMeshActive() {
        return meshActive;
    }

    public void updatePeers(Map<String, MeshPeer> peerMap) {
        if (peerMap != null) {
            peerList.postValue(new ArrayList<>(peerMap.values()));
        }
    }

    public void setMeshActive(boolean active) {
        meshActive.postValue(active);
    }
}
