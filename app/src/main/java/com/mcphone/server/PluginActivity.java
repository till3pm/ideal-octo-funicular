package com.mcphone.server;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lädt ein vertrauenswürdiges Plugin-JAR in den plugins-Ordner eines Paper-Servers. */
public class PluginActivity extends Activity {
    private Prefs p;
    private String serverId;
    private LinearLayout root, installedList;
    private EditText urlInput;
    private TextView status;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Dirs.init(this);
        serverId = ServerProfiles.safeId(getIntent().getStringExtra("server_id"));
        p = new Prefs(this, serverId);
        build();
    }

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 16));
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        setContentView(root);

        TextView eyebrow = Ui.text(this, "$ plugin install", 12, Ui.ACCENT);
        root.addView(eyebrow);
        TextView title = Ui.text(this, ServerProfiles.name(this, serverId), 26, Ui.TEXT);
        title.setTypeface(Ui.font(this), android.graphics.Typeface.BOLD);
        root.addView(title, Ui.row(this, 4));
        root.addView(Ui.text(this, "Plugin-Installer", 14, Ui.DIM), Ui.row(this, 2));

        ScrollView sc = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sc.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout card = Ui.card(this);
        card.addView(Ui.label(this, "JAR-Downloadlink"));
        urlInput = Ui.input(this, p.s("pluginUrl", ""), "https://…/plugin.jar", false);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setSingleLine(true);
        card.addView(urlInput, Ui.row(this, 8));
        TextView note = Ui.text(this, "Füge einen direkten HTTPS-Link zu einer .jar-Datei ein. Nutze nur Plugins aus Quellen, denen du vertraust.", 12, Ui.DIM);
        note.setLineSpacing(0, 1.15f);
        card.addView(note, Ui.row(this, 8));
        TextView install = Ui.button(this, "Plugin installieren", true);
        install.setOnClickListener(v -> installPlugin());
        card.addView(install, Ui.row(this, 12));
        status = Ui.text(this, "", 12, Ui.DIM);
        status.setLineSpacing(0, 1.15f);
        card.addView(status, Ui.row(this, 8));
        body.addView(card);

        LinearLayout current = Ui.card(this);
        current.addView(Ui.label(this, "Installierte JAR-Dateien"));
        installedList = new LinearLayout(this);
        installedList.setOrientation(LinearLayout.VERTICAL);
        current.addView(installedList, Ui.row(this, 8));
        body.addView(current, Ui.row(this, 14));

        TextView back = Ui.button(this, "Zurück zur Serverliste", false);
        back.setOnClickListener(v -> finish());
        root.addView(back, Ui.row(this, 12));
        refreshPlugins();
        if (!"paper".equalsIgnoreCase(p.s("software", "paper"))) {
            status.setText("Dieser Server ist Vanilla. Plugins benötigen Paper; ändere die Server-Software im Setup.");
            status.setTextColor(Ui.WARN);
        }
    }

    private void installPlugin() {
        final String link = urlInput.getText().toString().trim();
        if (!"paper".equalsIgnoreCase(p.s("software", "paper"))) {
            status.setText("✗ Plugins funktionieren nur mit Paper, nicht mit Vanilla.");
            status.setTextColor(Ui.ERR);
            return;
        }
        if (ServerService.running && serverId.equals(ServerService.activeServerId)) {
            status.setText("Bitte stoppe diesen Server vor der Installation.");
            status.setTextColor(Ui.WARN);
            return;
        }
        final String filename;
        try {
            URL u = new URL(link);
            if (!"https".equalsIgnoreCase(u.getProtocol())) throw new IllegalArgumentException("Nur HTTPS-Links sind erlaubt.");
            String path = u.getPath();
            filename = path.substring(path.lastIndexOf('/') + 1);
            if (!filename.toLowerCase(Locale.ROOT).endsWith(".jar") || !filename.matches("[A-Za-z0-9._-]{1,120}\\.[Jj][Aa][Rr]"))
                throw new IllegalArgumentException("Der Link muss direkt auf einen Dateinamen mit .jar enden.");
        } catch (Exception e) {
            status.setText("✗ Ungültiger Link: " + e.getMessage());
            status.setTextColor(Ui.ERR);
            return;
        }
        urlInput.setEnabled(false);
        status.setText("Download wird gestartet: " + filename);
        status.setTextColor(Ui.DIM);
        p.put("pluginUrl", link);
        io.execute(() -> {
            try {
                File plugins = new File(Dirs.server(this, serverId), "plugins");
                if (!plugins.exists() && !plugins.mkdirs()) throw new java.io.IOException("Plugin-Ordner konnte nicht angelegt werden.");
                File dest = new File(plugins, filename);
                Net.download(link, dest, msg -> main.post(() -> status.setText(msg)), filename);
                main.post(() -> {
                    status.setText("✓ Installiert: " + filename + ". Der Server lädt das Plugin beim nächsten Start.");
                    status.setTextColor(Ui.OK);
                    urlInput.setEnabled(true);
                    refreshPlugins();
                });
            } catch (Exception e) {
                main.post(() -> {
                    status.setText("✗ Installation fehlgeschlagen: " + String.valueOf(e.getMessage()));
                    status.setTextColor(Ui.ERR);
                    urlInput.setEnabled(true);
                });
            }
        });
    }

    private void refreshPlugins() {
        if (installedList == null) return;
        installedList.removeAllViews();
        File dir = new File(Dirs.server(this, serverId), "plugins");
        File[] fs = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (fs == null || fs.length == 0) {
            installedList.addView(Ui.text(this, "Noch keine Plugins installiert.", 13, Ui.DIM));
            return;
        }
        Arrays.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : fs) {
            TextView t = Ui.text(this, "•  " + f.getName(), 13, Ui.TEXT);
            installedList.addView(t, Ui.row(this, 4));
        }
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
