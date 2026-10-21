package com.mcphone.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** Startseite mit allen gespeicherten Serverprofilen. */
public class ServerListActivity extends Activity {
    private LinearLayout list;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Dirs.init(this);
        ServerProfiles.ensureMigrated(this);
        build();
    }

    private void build() {
        int pad = Ui.dp(this, 20);
        LinearLayout outer = new LinearLayout(this);
        outer.setFitsSystemWindows(true);
        outer.setBackgroundColor(Ui.BG);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(pad, pad, pad, pad);
        setContentView(outer);

        TextView eyebrow = Ui.text(this, "$ mc-server --profiles", 12, Ui.ACCENT);
        outer.addView(eyebrow);
        TextView title = Ui.text(this, "Deine Server", 29, Ui.TEXT);
        title.setTypeface(Ui.font(this), android.graphics.Typeface.BOLD);
        outer.addView(title, Ui.row(this, 4));
        TextView sub = Ui.text(this, "Verwalte deine Minecraft-Welten an einem Ort.", 14, Ui.DIM);
        outer.addView(sub, Ui.row(this, 4));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, 0, 1);
        sl.topMargin = Ui.dp(this, 18);
        outer.addView(scroll, sl);

        TextView add = Ui.button(this, "+  Server erstellen", true);
        add.setOnClickListener(v -> createServer());
        outer.addView(add, Ui.row(this, 12));
        render();
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        List<String> ids = ServerProfiles.ids(this);
        if (ids.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            TextView h = Ui.text(this, "Noch keine Server", 18, Ui.TEXT);
            h.setTypeface(Ui.font(this), android.graphics.Typeface.BOLD);
            empty.addView(h);
            TextView d = Ui.text(this, "Erstelle deinen ersten Server. Du kannst mehrere Profile mit eigenen Einstellungen, Plugins und Config-Dateien verwalten.", 14, Ui.DIM);
            d.setLineSpacing(0, 1.2f);
            empty.addView(d, Ui.row(this, 8));
            list.addView(empty, Ui.row(this, 0));
            return;
        }
        for (String id : ids) addServerCard(id);
    }

    private void addServerCard(final String id) {
        Prefs p = new Prefs(this, id);
        LinearLayout card = Ui.card(this);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(this, ServerProfiles.name(this, id), 18, Ui.TEXT);
        name.setTypeface(Ui.font(this), android.graphics.Typeface.BOLD);
        TextView details = Ui.text(this,
                p.b("setup", false) ? p.s("software", "paper") + " " + p.s("version", "") + " · Port " + p.i("port", 25565) : "Noch nicht eingerichtet",
                12, Ui.DIM);
        names.addView(name);
        names.addView(details, Ui.row(this, 3));
        boolean same = ServerService.running && id.equals(ServerService.activeServerId);
        TextView status = Ui.text(this, same ? (ServerService.ready ? "● online" : "● startet") : "● offline", 11, same ? (ServerService.ready ? Ui.OK : Ui.WARN) : Ui.DIM);
        status.setPadding(Ui.dp(this, 9), Ui.dp(this, 6), Ui.dp(this, 9), Ui.dp(this, 6));
        status.setBackground(Ui.box(this, Ui.FIELD, Ui.STROKE, 20));
        heading.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(status);
        card.addView(heading);

        LinearLayout primaryRow = new LinearLayout(this);
        TextView open = Ui.button(this, p.b("setup", false) ? "Öffnen" : "Einrichten", true);
        open.setOnClickListener(v -> startActivity(new Intent(this, ConsoleActivity.class).putExtra("server_id", id)));
        primaryRow.addView(open, new LinearLayout.LayoutParams(0, -2, 1));
        TextView rename = Ui.button(this, "Name", false);
        LinearLayout.LayoutParams rn = new LinearLayout.LayoutParams(0, -2, 0.65f);
        rn.leftMargin = Ui.dp(this, 8);
        primaryRow.addView(rename, rn);
        rename.setOnClickListener(v -> renameServer(id));
        card.addView(primaryRow, Ui.row(this, 12));

        LinearLayout tools = new LinearLayout(this);
        TextView plugins = Ui.button(this, "Plugins", false);
        plugins.setOnClickListener(v -> startActivity(new Intent(this, PluginActivity.class).putExtra("server_id", id)));
        TextView config = Ui.button(this, "Configs", false);
        config.setOnClickListener(v -> startActivity(new Intent(this, ConfigActivity.class).putExtra("server_id", id)));
        tools.addView(plugins, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
        cp.leftMargin = Ui.dp(this, 8);
        tools.addView(config, cp);
        card.addView(tools, Ui.row(this, 8));
        list.addView(card, Ui.row(this, 12));
    }

    private void createServer() {
        EditText input = Ui.input(this, "", "z. B. Survival-Welt", false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(this)
                .setTitle("Neuer Server")
                .setMessage("Gib deinem Server einen Namen.")
                .setView(input)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Weiter", (d, w) -> {
                    String id = ServerProfiles.create(this, input.getText().toString());
                    startActivity(new Intent(this, SetupActivity.class).putExtra("server_id", id));
                }).show();
    }

    private void renameServer(final String id) {
        EditText input = Ui.input(this, ServerProfiles.name(this, id), "Servername", false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(this)
                .setTitle("Server benennen")
                .setView(input)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", (d, w) -> {
                    ServerProfiles.rename(this, id, input.getText().toString());
                    render();
                }).show();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }
}
