package com.meshnet.chat.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.meshnet.chat.data.model.MessageEntity;

/** Room database for MeshWave local message persistence. */
@Database(entities = {MessageEntity.class}, version = 1, exportSchema = false)
public abstract class MeshDatabase extends RoomDatabase {

    private static final String DB_NAME = "meshwave_db";
    private static volatile MeshDatabase INSTANCE;

    public abstract MessageDao messageDao();

    public static MeshDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MeshDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MeshDatabase.class,
                            DB_NAME
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
