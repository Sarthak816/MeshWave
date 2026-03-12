package com.meshnet.chat.mesh.wifi;

import android.util.Log;

import com.google.gson.Gson;
import com.meshnet.chat.data.model.MeshPacket;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Socket-based data transfer over Wi-Fi Direct connections.
 *
 * The group owner runs a server socket; clients connect to it.
 * Messages are length-prefixed JSON payloads.
 */
public class WifiDirectDataTransfer {

    private static final String TAG = "WifiDirectData";
    private static final int PORT = 8470;
    private static final int SOCKET_TIMEOUT_MS = 10_000;

    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    /** Listener for incoming packets received over Wi-Fi Direct. */
    public interface DataListener {
        void onPacketReceived(MeshPacket packet);
        void onError(String error);
    }

    private DataListener dataListener;

    public void setDataListener(DataListener listener) {
        this.dataListener = listener;
    }

    // ── Server (Group Owner) ────────────────────────────────────

    /** Start listening for incoming connections. Call on group owner. */
    public void startServer() {
        if (running) return;
        running = true;

        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                serverSocket.setReuseAddress(true);
                Log.i(TAG, "Server listening on port " + PORT);

                while (running) {
                    Socket client = serverSocket.accept();
                    executor.execute(() -> handleClient(client));
                }
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Server error", e);
                    if (dataListener != null) dataListener.onError("Server: " + e.getMessage());
                }
            }
        });
    }

    private void handleClient(Socket client) {
        try (InputStream is = client.getInputStream()) {
            DataInputStream dis = new DataInputStream(is);

            while (running && !client.isClosed()) {
                int length = dis.readInt();
                if (length <= 0 || length > 1_000_000) break; // sanity check: max 1MB

                byte[] data = new byte[length];
                dis.readFully(data);

                String json = new String(data, StandardCharsets.UTF_8);
                MeshPacket packet = gson.fromJson(json, MeshPacket.class);

                if (dataListener != null) {
                    dataListener.onPacketReceived(packet);
                }
                Log.d(TAG, "Received packet via Wi-Fi Direct");
            }
        } catch (EOFException e) {
            Log.d(TAG, "Client disconnected");
        } catch (IOException e) {
            Log.e(TAG, "Client handler error", e);
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    public void stopServer() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
    }

    // ── Client (Peer) ───────────────────────────────────────────

    /** Send a packet to the group owner's server socket. */
    public void sendPacket(String groupOwnerAddress, MeshPacket packet) {
        executor.execute(() -> {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(groupOwnerAddress, PORT), SOCKET_TIMEOUT_MS);

                String json = gson.toJson(packet);
                byte[] data = json.getBytes(StandardCharsets.UTF_8);

                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeInt(data.length);
                dos.write(data);
                dos.flush();

                Log.d(TAG, "Sent packet to " + groupOwnerAddress);
            } catch (IOException e) {
                Log.e(TAG, "Send error to " + groupOwnerAddress, e);
                if (dataListener != null) dataListener.onError("Send: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        });
    }

    public void shutdown() {
        stopServer();
        executor.shutdownNow();
    }
}
