package com.itachi.universe;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Edit the About Developer page: header, sections, buttons with links. Every change saves at once. */
public class AdminAboutActivity extends BaseActivity {
    private JsonObject doc = new JsonObject();
    private LinearLayout headerBox, list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        LinearLayout col = column();
        col.addView(UI.backHeader(this, "Admin", "About Developer"));
        col.addView(UI.space(this, 20));
        headerBox = new LinearLayout(this);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(headerBox, UI.matchWrap());
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, UI.matchWrap());
        col.addView(UI.space(this, 90));
        root.addView(scroll(col), new FrameLayout.LayoutParams(-1, -1));

        TextView fab = UI.tv(this, "+  Add section", 16, true, android.graphics.Color.WHITE);
        fab.setGravity(Gravity.CENTER);
        fab.setPadding(UI.dp(this, 22), UI.dp(this, 16), UI.dp(this, 22), UI.dp(this, 16));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{UI.INDIGO, UI.TEAL});
        g.setCornerRadius(UI.dp(this, 20));
        fab.setBackground(g);
        fab.setOnClickListener(v -> editSection(-1));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, UI.dp(this, 20), UI.dp(this, 20));
        root.addView(fab, fp);

        setScreen(root);
        load();
    }

    private JsonArray sections() {
        if (!doc.has("sections") || !doc.get("sections").isJsonArray()) doc.add("sections", new JsonArray());
        return doc.getAsJsonArray("sections");
    }

    private void load() {
        list.removeAllViews();
        list.addView(new ProgressBar(this));
        run(Api::about, new Api.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject d) { doc = d; render(); }
            @Override public void onError(Api.ApiException e) {
                list.removeAllViews();
                LinearLayout c = UI.card(AdminAboutActivity.this, 22, 16, null, null);
                c.addView(UI.tv(AdminAboutActivity.this, e.getMessage(), 14, false, UI.dim(AdminAboutActivity.this)));
                c.addView(UI.textButton(AdminAboutActivity.this, "Try again", v -> load()));
                list.addView(c, UI.matchWrap());
            }
        });
    }

    /** Sends the whole page to the server, then redraws from what the server kept. */
    private void save(String done) {
        run(() -> Api.saveAbout(doc), new Api.Callback<JsonObject>() {
            @Override public void onSuccess(JsonObject d) { doc = d; render(); toast(done); }
            @Override public void onError(Api.ApiException e) { toast(e.getMessage()); load(); }
        });
    }

    private void render() {
        int text = UI.text(this), dim = UI.dim(this);
        headerBox.removeAllViews();
        LinearLayout head = UI.card(this, 22, 16, null, null);
        String name = Models.str(doc, "name"), tag = Models.str(doc, "tagline");
        head.addView(UI.tv(this, name.isEmpty() ? "(no name)" : name, 18, true, text));
        head.addView(UI.tv(this, tag.isEmpty() ? "(no tagline)" : tag, 14, false, dim));
        head.addView(UI.textButton(this, "Edit name and tagline", v -> editHeader()));
        headerBox.addView(head, UI.matchWrap());
        headerBox.addView(UI.space(this, 12));

        list.removeAllViews();
        JsonArray secs = sections();
        if (secs.size() == 0) {
            TextView t = UI.tv(this, "No sections yet. Tap + Add section.", 14, false, dim);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 30), 0, 0);
            list.addView(t, UI.matchWrap());
        }
        for (int i = 0; i < secs.size(); i++) {
            final int idx = i;
            JsonObject s = secs.get(i).getAsJsonObject();
            LinearLayout card = UI.card(this, 22, 16, null, null);
            String h = Models.str(s, "heading"), body = Models.str(s, "body");
            card.addView(UI.tv(this, h.isEmpty() ? "(no heading)" : h, 16, true, text));
            if (!body.isEmpty()) {
                TextView bt = UI.tv(this, body, 14, false, dim);
                bt.setMaxLines(3);
                bt.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(bt);
            }
            int links = s.has("links") ? s.getAsJsonArray("links").size() : 0;
            if (links > 0) card.addView(UI.tv(this, links + (links == 1 ? " button" : " buttons"), 12, true, UI.TEAL));

            LinearLayout row = new LinearLayout(this);
            row.addView(UI.textButton(this, "Edit", v -> editSection(idx)));
            row.addView(UI.textButton(this, "↑", v -> move(idx, -1)));
            row.addView(UI.textButton(this, "↓", v -> move(idx, 1)));
            row.addView(UI.textButton(this, "Delete", v -> confirmDelete(idx)));
            card.addView(row);
            LinearLayout.LayoutParams lp = UI.matchWrap();
            lp.bottomMargin = UI.dp(this, 12);
            list.addView(card, lp);
        }
    }

    private void move(int idx, int by) {
        JsonArray secs = sections();
        int to = idx + by;
        if (to < 0 || to >= secs.size()) return;
        JsonArray out = new JsonArray();
        java.util.List<JsonElement> items = new java.util.ArrayList<>();
        for (JsonElement e : secs) items.add(e);
        JsonElement moved = items.remove(idx);
        items.add(to, moved);
        for (JsonElement e : items) out.add(e);
        doc.add("sections", out);
        save("Moved.");
    }

    private void confirmDelete(int idx) {
        new AlertDialog.Builder(this).setTitle("Delete this section?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    JsonArray out = new JsonArray();
                    JsonArray secs = sections();
                    for (int i = 0; i < secs.size(); i++) if (i != idx) out.add(secs.get(i));
                    doc.add("sections", out);
                    save("Deleted.");
                }).show();
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UI.dp(this, 20), UI.dp(this, 8), UI.dp(this, 20), 0);
        return box;
    }

    private void editHeader() {
        LinearLayout box = dialogBox();
        EditText name = UI.input(this, "Name (for example your name or brand)");
        EditText tag = UI.input(this, "Tagline");
        name.setText(Models.str(doc, "name"));
        tag.setText(Models.str(doc, "tagline"));
        box.addView(name, UI.matchWrap());
        box.addView(UI.space(this, 10));
        box.addView(tag, UI.matchWrap());
        new AlertDialog.Builder(this).setTitle("Name and tagline").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    doc.addProperty("name", name.getText().toString().trim());
                    doc.addProperty("tagline", tag.getText().toString().trim());
                    save("Saved.");
                }).show();
    }

    /** idx = -1 adds a new section at the end. */
    private void editSection(int idx) {
        JsonObject cur = idx >= 0 ? sections().get(idx).getAsJsonObject() : new JsonObject();
        LinearLayout box = dialogBox();
        EditText heading = UI.input(this, "Heading");
        EditText body = UI.input(this, "Text");
        body.setSingleLine(false);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setMinLines(4);
        body.setGravity(Gravity.TOP);
        EditText links = UI.input(this, "Buttons, one per line:\nTelegram | https://t.me/yourname");
        links.setSingleLine(false);
        links.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        links.setMinLines(3);
        links.setGravity(Gravity.TOP);

        heading.setText(Models.str(cur, "heading"));
        body.setText(Models.str(cur, "body"));
        StringBuilder sb = new StringBuilder();
        if (cur.has("links")) {
            for (JsonElement e : cur.getAsJsonArray("links")) {
                JsonObject l = e.getAsJsonObject();
                if (sb.length() > 0) sb.append('\n');
                sb.append(Models.str(l, "label")).append(" | ").append(Models.str(l, "url"));
            }
        }
        links.setText(sb.toString());
        for (View v : new View[]{heading, body, links}) {
            box.addView(v, UI.matchWrap());
            box.addView(UI.space(this, 10));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);

        new AlertDialog.Builder(this).setTitle(idx >= 0 ? "Edit section" : "New section").setView(sv)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    JsonArray parsed = new JsonArray();
                    for (String line : links.getText().toString().split("\n")) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        int bar = line.indexOf('|');
                        String label = bar > 0 ? line.substring(0, bar).trim() : "";
                        String url = bar > 0 ? line.substring(bar + 1).trim() : "";
                        if (label.isEmpty() || url.isEmpty()) { toast("Write each button as: Label | https://link"); return; }
                        JsonObject l = new JsonObject();
                        l.addProperty("label", label);
                        l.addProperty("url", url);
                        parsed.add(l);
                    }
                    JsonObject s = new JsonObject();
                    s.addProperty("heading", heading.getText().toString().trim());
                    s.addProperty("body", body.getText().toString().trim());
                    s.add("links", parsed);
                    JsonArray secs = sections();
                    if (idx >= 0) secs.set(idx, s); else secs.add(s);
                    save("Saved.");
                }).show();
    }
}
