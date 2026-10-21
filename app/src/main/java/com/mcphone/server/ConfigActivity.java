package com.mcphone.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Einfacher Texteditor für Server- und Plugin-Konfigurationsdateien. */
public class ConfigActivity extends Activity {
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final String[] EXTENSIONS = {".properties", ".yml", ".yaml", ".json", ".txt", ".conf", ".toml", ".cfg", ".xml", ".mcmeta"};
    private String serverId;
    private File serverDir;
    private LinearLayout root;
    private String editingPath;
    private EditText editor;
    private TextView message;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Dirs.init(this);
        serverId = ServerProfiles.safeId(getIntent().getStringExtra("server_id"));
        serverDir = Dirs.server(this, serverId);
        if (!serverDir.exists()) serverDir.mkdirs();
        ensureServerProperties();
        showFileList();
    }

    private void newRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 16));
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        setContentView(root);
    }

    private void heading(String section, String name) {
        root.addView(Ui.text(this, section, 12, Ui.ACCENT));
        TextView title = Ui.text(this, name, 25, Ui.TEXT);
        title.setTypeface(Ui.font(this), android.graphics.Typeface.BOLD);
        root.addView(title, Ui.row(this, 4));
    }

    private void showFileList() {
        editingPath = null;
        editor = null;
        newRoot();
        heading("$ config list", ServerProfiles.name(this, serverId));
        root.addView(Ui.text(this, "Wähle eine Config-Datei zum Anzeigen oder Bearbeiten.", 13, Ui.DIM), Ui.row(this, 4));

        ScrollView scroll = new ScrollView(this);
        LinearLayout entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entries, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, 0, 1);
        ep.topMargin = Ui.dp(this, 14);
        root.addView(scroll, ep);

        List<File> files = new ArrayList<>();
        collect(serverDir, files, 0);
        Collections.sort(files, (a, b) -> relative(a).compareToIgnoreCase(relative(b)));
        for (File f : files) {
            final String rel = relative(f);
            LinearLayout card = Ui.card(this);
            TextView fileName = Ui.text(this, rel, 14, Ui.TEXT);
            card.addView(fileName);
            TextView edit = Ui.button(this, "Bearbeiten", false);
            edit.setOnClickListener(v -> showEditor(rel));
            card.addView(edit, Ui.row(this, 8));
            entries.addView(card, Ui.row(this, 8));
        }
        TextView create = Ui.button(this, "+  Config-Datei erstellen", true);
        create.setOnClickListener(v -> createFileDialog());
        root.addView(create, Ui.row(this, 10));
        TextView back = Ui.button(this, "Zurück zur Serverliste", false);
        back.setOnClickListener(v -> finish());
        root.addView(back, Ui.row(this, 8));
    }

    private void showEditor(String rel) {
        File f;
        try {
            f = safeFile(rel);
            if (!f.exists() || !f.isFile()) throw new java.io.IOException("Datei nicht gefunden.");
            if (f.length() > MAX_BYTES) throw new java.io.IOException("Datei ist größer als 1 MB. Große Dateien werden nicht im Editor geöffnet.");
        } catch (Exception e) {
            new AlertDialog.Builder(this).setMessage(String.valueOf(e.getMessage())).setPositiveButton("OK", null).show();
            return;
        }
        try {
            editingPath = relative(f);
            String content = read(f);
            newRoot();
            heading("$ config edit", editingPath);

            ScrollView scroll = new ScrollView(this);
            editor = new EditText(this);
            editor.setText(content);
            editor.setTextColor(Ui.TEXT);
            editor.setHintTextColor(Ui.DIM);
            editor.setTypeface(Ui.font(this));
            editor.setTextSize(13);
            editor.setGravity(Gravity.TOP | Gravity.START);
            editor.setSingleLine(false);
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setBackground(Ui.box(this, Ui.CARD, Ui.STROKE, 14));
            editor.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
            scroll.addView(editor, new ScrollView.LayoutParams(-1, -2));
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, 0, 1);
            el.topMargin = Ui.dp(this, 14);
            root.addView(scroll, el);

            message = Ui.text(this, "Änderungen an Server-Configs gelten meist nach dem nächsten Neustart.", 12, Ui.DIM);
            root.addView(message, Ui.row(this, 8));
            TextView save = Ui.button(this, "Änderungen speichern", true);
            save.setOnClickListener(v -> saveEditor());
            root.addView(save, Ui.row(this, 10));
            TextView files = Ui.button(this, "Dateiliste", false);
            files.setOnClickListener(v -> showFileList());
            root.addView(files, Ui.row(this, 8));
        } catch (Exception e) {
            new AlertDialog.Builder(this).setMessage("Datei konnte nicht gelesen werden: " + e.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    private void saveEditor() {
        if (editingPath == null || editor == null) return;
        String text = editor.getText().toString();
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            message.setText("✗ Datei ist größer als 1 MB und wurde nicht gespeichert.");
            message.setTextColor(Ui.ERR);
            return;
        }
        try {
            File f = safeFile(editingPath);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new java.io.IOException("Ordner konnte nicht erstellt werden.");
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"))) { w.write(text); }
            message.setText("✓ Gespeichert. Änderungen werden je nach Datei beim nächsten Start wirksam.");
            message.setTextColor(Ui.OK);
            new AlertDialog.Builder(this).setMessage("Datei gespeichert: " + editingPath).setPositiveButton("OK", (d, which) -> showFileList()).show();
        } catch (Exception e) {
            message.setText("✗ Speichern fehlgeschlagen: " + e.getMessage());
            message.setTextColor(Ui.ERR);
        }
    }

    private void createFileDialog() {
        EditText input = Ui.input(this, "", "z. B. plugins/Beispiel/config.yml", false);
        new AlertDialog.Builder(this)
                .setTitle("Config-Datei erstellen")
                .setMessage("Relativer Pfad im Server-Ordner; erlaubt sind gängige Text-Configs.")
                .setView(input)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Erstellen", (d, w) -> {
                    String path = input.getText().toString().trim();
                    try {
                        File f = safeFile(path);
                        if (!isConfigFile(f.getName())) throw new java.io.IOException("Dateiendung wird nicht unterstützt.");
                        if (f.exists()) throw new java.io.IOException("Die Datei existiert bereits.");
                        File parent = f.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new java.io.IOException("Ordner konnte nicht erstellt werden.");
                        if (!f.createNewFile()) throw new java.io.IOException("Datei konnte nicht erstellt werden.");
                        showEditor(path);
                    } catch (Exception e) {
                        new AlertDialog.Builder(this).setMessage("Datei konnte nicht erstellt werden: " + e.getMessage()).setPositiveButton("OK", null).show();
                    }
                }).show();
    }

    private void collect(File dir, List<File> out, int depth) {
        if (depth > 5) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, out, depth + 1);
            else if (f.isFile() && isConfigFile(f.getName()) && f.length() <= MAX_BYTES) out.add(f);
        }
    }

    private boolean isConfigFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) if (lower.endsWith(ext)) return true;
        return false;
    }

    private File safeFile(String rel) throws Exception {
        if (rel == null || rel.trim().isEmpty()) throw new java.io.IOException("Bitte einen Dateipfad angeben.");
        File f = new File(serverDir, rel.trim());
        String base = serverDir.getCanonicalPath() + File.separator;
        String full = f.getCanonicalPath();
        if (!full.startsWith(base)) throw new java.io.IOException("Pfad außerhalb des Serverordners ist nicht erlaubt.");
        if (!isConfigFile(f.getName())) throw new java.io.IOException("Nur Text-Config-Dateien können bearbeitet werden.");
        return f;
    }

    private String relative(File f) {
        return serverDir.toURI().relativize(f.toURI()).getPath();
    }

    private String read(File f) throws Exception {
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
        }
        return b.toString();
    }

    private void ensureServerProperties() {
        File f = new File(serverDir, "server.properties");
        if (f.exists()) return;
        Prefs p = new Prefs(this, serverId);
        // Vor Abschluss des Setups keine Standarddatei anlegen, die spätere Wizard-Werte blockiert.
        if (!p.b("setup", false)) return;
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
            w.write("server-port=" + p.i("port", 25565) + "\n");
            w.write("motd=" + p.s("motd", "Ein Minecraft-Server auf dem Handy") + "\n");
            w.write("max-players=" + p.i("maxPlayers", 10) + "\n");
            w.write("difficulty=" + p.s("difficulty", "normal") + "\n");
            w.write("gamemode=" + p.s("gamemode", "survival") + "\n");
            w.write("pvp=" + p.s("pvp", "true") + "\n");
            w.write("online-mode=" + p.s("onlineMode", "true") + "\n");
            w.write("view-distance=" + p.i("viewDistance", 6) + "\n");
        } catch (Exception ignored) { }
    }

    @Override public void onBackPressed() {
        if (editingPath != null) showFileList();
        else super.onBackPressed();
    }
}
