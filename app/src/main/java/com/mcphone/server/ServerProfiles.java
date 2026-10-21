package com.mcphone.server;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Persistente Liste aller angelegten Serverprofile. */
final class ServerProfiles {
    private static final String STORE = "mc_profiles";
    private static final String IDS = "ids";

    private ServerProfiles() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    static synchronized void ensureMigrated(Context c) {
        SharedPreferences sp = prefs(c);
        if (!sp.getString(IDS, "").isEmpty()) return;
        SharedPreferences old = c.getApplicationContext().getSharedPreferences("mc", Context.MODE_PRIVATE);
        boolean wasSetup = Boolean.parseBoolean(old.getString("setup", "false"));
        boolean hasFiles = new File(c.getFilesDir(), "server/server.jar").isFile();
        if (wasSetup || hasFiles) {
            JSONArray a = new JSONArray();
            a.put("default");
            sp.edit().putString(IDS, a.toString()).putString("name.default", "Minecraft Server").apply();
        }
    }

    static synchronized List<String> ids(Context c) {
        ensureMigrated(c);
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(IDS, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    static synchronized String create(Context c, String name) {
        SharedPreferences sp = prefs(c);
        List<String> ids = ids(c);
        String id = "s" + Long.toString(System.currentTimeMillis(), 36) + "_" + Integer.toString(ids.size(), 36);
        while (ids.contains(id)) id = "s" + Long.toString(System.nanoTime(), 36);
        ids.add(id);
        JSONArray a = new JSONArray();
        for (String existing : ids) a.put(existing);
        sp.edit().putString(IDS, a.toString()).putString("name." + id, cleanName(name)).apply();
        return id;
    }

    static String name(Context c, String id) {
        return prefs(c).getString("name." + safeId(id), "default".equals(id) ? "Minecraft Server" : "Minecraft Server");
    }

    static void rename(Context c, String id, String name) {
        prefs(c).edit().putString("name." + safeId(id), cleanName(name)).apply();
    }

    static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) n = "Minecraft Server";
        return n.length() > 48 ? n.substring(0, 48) : n;
    }

    static String safeId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) return "default";
        return id;
    }
}
