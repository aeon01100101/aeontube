package com.aeontube.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "aeontube";
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String myChannelId() { return sp.getString("my_channel_id", ""); }
    public String myChannelName() { return sp.getString("my_channel_name", ""); }
    public void setMyChannel(String id, String name) {
        sp.edit().putString("my_channel_id", id).putString("my_channel_name", name).apply();
    }

    public String customPiped() { return sp.getString("custom_piped", ""); }
    public String customInvidious() { return sp.getString("custom_invidious", ""); }
    public void setCustomPiped(String v) { sp.edit().putString("custom_piped", v.trim()).apply(); }
    public void setCustomInvidious(String v) { sp.edit().putString("custom_invidious", v.trim()).apply(); }

    public boolean sponsorBlock() { return sp.getBoolean("sponsorblock", true); }
    public void setSponsorBlock(boolean b) { sp.edit().putBoolean("sponsorblock", b).apply(); }

    public String lastBackend() { return sp.getString("last_backend", ""); }
    public void setLastBackend(String name) { sp.edit().putString("last_backend", name).apply(); }
}
