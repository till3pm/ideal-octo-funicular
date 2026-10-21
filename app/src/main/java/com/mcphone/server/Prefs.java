package com.mcphone.server;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private final SharedPreferences sp;
    private final String prefix;
    private final String serverId;

    Prefs(Context c) { this(c, "default"); }

    Prefs(Context c, String id) {
        sp = c.getApplicationContext().getSharedPreferences("mc", 0);
        serverId = ServerProfiles.safeId(id);
        // Das alte Standardprofil bleibt kompatibel zu vorhandenen Installationen.
        prefix = "default".equals(serverId) ? "" : "srv." + serverId + ".";
    }

    String serverId() { return serverId; }

    String s(String k, String d) { return sp.getString(prefix + k, d); }

    int i(String k, int d) {
        try { return Integer.parseInt(sp.getString(prefix + k, String.valueOf(d)).trim()); } catch (Exception e) { return d; }
    }

    boolean b(String k, boolean d) { return Boolean.parseBoolean(sp.getString(prefix + k, String.valueOf(d))); }

    void put(String k, Object v) { sp.edit().putString(prefix + k, String.valueOf(v)).apply(); }
}
