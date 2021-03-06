package com.github.gfx.hankei_n.toolbox;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import com.github.gfx.hankei_n.model.Prefs;

import javax.inject.Inject;

public class MarkerHueAllocator {

    static final String kMarkerHue = "marker_hue";

    static float INITIAL_HUE = BitmapDescriptorFactory.HUE_RED;

    final Prefs prefs;

    @Inject
    public MarkerHueAllocator(Prefs prefs) {
        this.prefs = prefs;
    }

    public void reset() {
        prefs.put(kMarkerHue, INITIAL_HUE);
    }

    public float allocate() {
        float hue = prefs.get(kMarkerHue, INITIAL_HUE);
        prefs.put(kMarkerHue, (hue + 30.0f) % 360.0f);
        return hue;
    }
}
