package com.github.gfx.hankei_n.model;

import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.github.gfx.hankei_n.model.gson.LatLngTypeAdapter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

import timber.log.Timber;

@ParametersAreNonnullByDefault
public class LocationMemoManager extends SQLiteOpenHelper {

    static final int VERSION = 1;

    static final String TABLE_NAME = "addresses";

    static final String[] columns = {
            "id",
            "address",
            "note",
            "latitude",
            "longitude",
            "radius",
            "marker_hue",
    };

    static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(\n"
            + "    id INTEGER PRIMARY KEY,\n"
            + "\n"
            + "    address TEXT NOT NULL,\n"
            + "    note TEXT NOT NULL,\n"
            + "\n"
            + "    latitude DOUBLE NOT NULL,\n"
            + "    longitude DOUBLE NOT NULL,\n"
            + "\n"
            + "    radius DOUBLE NOT NULL,\n"
            + "\n"
            + "    marker_hue DOUBLE NOT NULL\n"
            + ")";

    static final String DROP_TABLE = "DROP TABLE " + TABLE_NAME;

    static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LatLng.class, new LatLngTypeAdapter())
            .create();

    public LocationMemoManager(Context context, String name) {
        super(context.getApplicationContext(), name, null, VERSION);
    }

    public int count() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, columns, null, null, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        db.close();
        return count;
    }

    public List<LocationMemo> all() {
        SQLiteDatabase db = getReadableDatabase();

        List<LocationMemo> list = new ArrayList<>();

        Cursor cursor = db.query(TABLE_NAME, columns, null, null, null, null, "id ASC");
        for (boolean hasNext = cursor.moveToFirst(); hasNext; hasNext = cursor.moveToNext()) {
            JsonObject object = new JsonObject();

            for (int i = 0; i < cursor.getColumnCount(); i++) {
                String name = cursor.getColumnName(i);
                String value = cursor.getString(i);

                object.addProperty(name, value);
            }

            list.add(GSON.fromJson(object, LocationMemo.class));
        }
        cursor.close();
        db.close();

        return list;
    }

    public void upsert(LocationMemo memo) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        JsonObject object = (JsonObject)GSON.toJsonTree(memo);

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getKey().equals("id")) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        if (memo.id != 0) {
            int result = db.update(TABLE_NAME, values, "id = ?", new String[]{ String.valueOf(memo.id) });

            if (result != 1) {
                insert(db, memo, values);
            }
        } else {
            insert(db, memo, values);
        }

        db.close();
    }

    private void insert(SQLiteDatabase db, LocationMemo memo, ContentValues values) {
        long rowId = db.insert(TABLE_NAME, null, values);
        if (rowId == -1) {
            throw new RuntimeException("INSERT failed");
        }
        memo.id = rowId;
    }

    public boolean remove(LocationMemo memo) {
        SQLiteDatabase db = getWritableDatabase();

        int result = db.delete(TABLE_NAME, "id = ?", new String[]{String.valueOf(memo.id)});

        db.close();

        return result == 1;
    }

    public void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        db.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Timber.d("upgrade databases from " + oldVersion + " " + newVersion);
        db.execSQL(DROP_TABLE);
        db.execSQL(CREATE_TABLE);
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}