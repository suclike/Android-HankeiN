package com.github.gfx.hankei_n.model;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.Marker;

import com.github.gfx.hankei_n.dependency.scope.ContextScope;

import android.support.annotation.NonNull;
import android.support.v4.util.LongSparseArray;

import java.util.NoSuchElementException;

import javax.inject.Inject;

@ContextScope
public class MarkerManager {

    final LongSparseArray<MarkerHolder> markers = new LongSparseArray<>();

    @Inject
    public MarkerManager() {
    }

    @NonNull
    public Marker get(@NonNull LocationMemo memo) {
        assert memo.id > 0;
        return markers.get(memo.id).marker;
    }


    public LocationMemo findMemoByMarker(Iterable<LocationMemo> memos, Marker marker) {
        for (LocationMemo item : memos) {
            if (marker.equals(get(item))) {
                return item;
            }
        }
        throw new NoSuchElementException("Marker not found for id=" + marker.getId());
    }

    public Marker create(@NonNull GoogleMap map, @NonNull LocationMemo memo) {
        assert memo.id > 0;

        remove(memo);

        Marker marker = map.addMarker(memo.buildMarkerOptions());
        Circle circle = null;
        if (memo.radius > 0 && memo.drawCircle) {
            circle = map.addCircle(memo.buildCircleOptions());
        }

        markers.put(memo.id, new MarkerHolder(marker, circle));

        return marker;
    }

    public void remove(LocationMemo memo) {
        assert memo.id > 0;
        MarkerHolder holder = markers.get(memo.id);
        if (holder != null) {
            holder.removeFromMap();
            markers.remove(memo.id);
        }
    }

    public void clear() {
        for (int i = 0; i < markers.size(); i++) {
            long id = markers.keyAt(i);
            markers.get(id).removeFromMap();
        }
    }

    static class MarkerHolder {

        Marker marker;

        Circle circle;

        public MarkerHolder(Marker marker, Circle circle) {
            this.marker = marker;
            this.circle = circle;
        }

        void removeFromMap() {
            if (marker != null) {
                marker.remove();
            }
            if (circle != null) {
                circle.remove();
            }
        }

    }
}
