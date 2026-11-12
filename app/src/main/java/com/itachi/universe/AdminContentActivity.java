package com.itachi.universe;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Manage batches > subjects > units > lectures. */
public class AdminContentActivity extends BaseActivity {
    private static final String[] KINDS = {"batches", "subjects", "units", "lectures"};
    private static final String[] WORDS = {"batch", "subject", "unit", "lecture"};

    private JsonArray data = new JsonArray();
    private final List<Integer> path = new ArrayList<>(); // ids: batch, subject, unit
    private LinearLayout headerBox, list;

    private int level() { return path.size(); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        LinearLayout col = column();
        headerBox = new LinearLayout(this);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(headerBox, UI.matchWrap());
        col.addView(UI.space(this, 20));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, UI.matchWrap());
        col.addView(UI.space(this, 90));
        root.addView(scroll(col), new android.widget.FrameLayout.LayoutParams(-1, -1));

        TextView fab = UI.tv(this, "+  Add", 16, true, android.graphics.Color.WHITE);
        fab.setGravity(Gravity.CENTER);
        fab.setPadding(UI.dp(this, 22), UI.dp(this, 16), UI.dp(this, 22), UI.dp(this, 16));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{UI.INDIGO, UI.TEAL});
        g.setCornerRadius(UI.dp(this, 20));
        fab.setBackground(g);
        fab.setOnClickListener(v -> onAdd());
        android.widget.FrameLayout.LayoutParams fp = new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, UI.dp(this, 20), UI.dp(this, 20));
        root.addView(fab, fp);
        setScreen(root);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goUp(); }
        });
        load();
    }

    private void goUp() {
        if (path.isEmpty()) { finish(); return; }
        path.remove(path.size() - 1);
        render();
    }

    private void toLogin() {
        startActivity(new android.content.Intent(this, AdminLoginActivity.class));
        finish();
    }

    private void load() {
        list.removeAllViews();
        list.addView(new ProgressBar(this));
        run(() -> Api.adminContent(), new Api.Callback<JsonArray>() {
            @Override public void onSuccess(JsonArray d) {
                data = d;
                // If the item we are inside was deleted elsewhere, go back to the top.
                if (level() >= 1 && find(data, path.get(0)) == null) path.clear();
                if (level() >= 2 && find(current(1).getAsJsonArray("subjects"), path.get(1)) == null) trimPath(1);
                if (level() >= 3 && find(current(2).getAsJsonArray("units"), path.get(2)) == null) trimPath(2);
                render();
            }
            @Override public void onError(Api.ApiException e) {
                if (e.unauthorized) { toLogin(); return; }
                list.removeAllViews();
                LinearLayout c = UI.card(AdminContentActivity.this, 22, 16, null, null);
                c.addView(UI.tv(AdminContentActivity.this, e.getMessage(), 14, false, UI.dim(AdminContentActivity.this)));
                c.addView(UI.textButton(AdminContentActivity.this, "Try again", v -> load()));
                list.addView(c, UI.matchWrap());
            }
        });
    }

    private void trimPath(int size) { while (path.size() > size) path.remove(path.size() - 1); }

    private JsonObject find(JsonArray arr, int id) {
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            if (Models.num(o, "id") == id) return o;
        }
        return null;
    }

    /** The batch (level 1), subject (2) or unit (3) we are inside. */
    private JsonObject current(int lvl) {
        JsonObject m = find(data, path.get(0));
        if (lvl >= 2) m = find(m.getAsJsonArray("subjects"), path.get(1));
        if (lvl >= 3) m = find(m.getAsJsonArray("units"), path.get(2));
        return m;
    }

    private JsonArray items() {
        switch (level()) {
            case 0: return data;
            case 1: return current(1).getAsJsonArray("subjects");
            case 2: return current(2).getAsJsonArray("units");
            default: return current(3).getAsJsonArray("lectures");
        }
    }

    private String titleOf(JsonObject m) {
        return m.has("name") ? Models.str(m, "name") : Models.str(m, "title");
    }

    private int lecturesIn(JsonObject m, int lvl) {
        if (lvl == 2) return m.getAsJsonArray("lectures").size();
        JsonArray children = m.getAsJsonArray(lvl == 0 ? "subjects" : "units");
        int n = 0;
        for (JsonElement c : children) n += lecturesIn(c.getAsJsonObject(), lvl + 1);
        return n;
    }

    private String subtitle(JsonObject m) {
        switch (level()) {
            case 0: return m.getAsJsonArray("subjects").size() + " subjects, " + lecturesIn(m, 0) + " lectures";
            case 1: return m.getAsJsonArray("units").size() + " units, " + lecturesIn(m, 1) + " lectures";
            case 2: return lecturesIn(m, 2) + " lectures";
            default:
                String s = Models.bool(m, "free") ? "FREE  " : "";
                if (!Models.str(m, "video_url").isEmpty()) s += "video ";
                if (!Models.str(m, "pdf_url").isEmpty()) s += "pdf";
                return s.trim();
        }
    }

    private void render() {
        headerBox.removeAllViews();
        String caption = level() == 0 ? "Admin" : "Back";
        String title = level() == 0 ? "Lectures" : titleOf(current(level()));
        headerBox.addView(UI.backHeader(this, caption, title, this::goUp));

        list.removeAllViews();
        JsonArray items = items();
        if (items.size() == 0) {
            TextView t = UI.tv(this, "No " + WORDS[level()] + "s yet. Tap Add.", 14, false, UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 30), 0, 0);
            list.addView(t, UI.matchWrap());
            return;
        }
        for (JsonElement e : items) list.addView(itemCard(e.getAsJsonObject()));
    }

    private View itemCard(JsonObject m) {
        LinearLayout card = UI.card(this, 20, 16, null, v -> {
            if (level() < 3) { path.add(Models.num(m, "id")); render(); }
            else showLectureDialog(m);
        });
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UI.tv(this, titleOf(m), 16, true, UI.text(this)));
        col.addView(UI.tv(this, subtitle(m), 12, false, UI.dim(this)));
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView more = UI.tv(this, "\u22EE", 22, true, UI.text(this));
        more.setPadding(UI.dp(this, 12), 0, UI.dp(this, 4), 0);
        more.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            if (level() < 3) pm.getMenu().add(0, 1, 0, "Rename");
            else pm.getMenu().add(0, 3, 0, "Edit");
            pm.getMenu().add(0, 2, 1, "Delete");
            pm.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) onRename(m);
                else if (item.getItemId() == 2) onDelete(m);
                else showLectureDialog(m);
                return true;
            });
            pm.show();
        });
        card.addView(more);

        LinearLayout.LayoutParams lp = UI.matchWrap();
        lp.bottomMargin = UI.dp(this, 10);
        card.setLayoutParams(lp);
        return card;
    }

    // ---------- actions ----------
    private void change(Api.Job<Object> job, String done) {
        run(job, new Api.Callback<Object>() {
            @Override public void onSuccess(Object r) { toast(done); load(); }
            @Override public void onError(Api.ApiException e) {
                if (e.unauthorized) toLogin(); else toast(e.getMessage());
            }
        });
    }

    private void askName(String title, String initial, java.util.function.Consumer<String> cb) {
        EditText e = UI.input(this, "Name");
        e.setText(initial);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(UI.dp(this, 20), UI.dp(this, 8), UI.dp(this, 20), 0);
        wrap.addView(e, UI.matchWrap());
        new AlertDialog.Builder(this).setTitle(title).setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String v = e.getText().toString().trim();
                    if (!v.isEmpty()) cb.accept(v);
                }).show();
    }

    private void onAdd() {
        if (level() == 3) { showLectureDialog(null); return; }
        int lvl = level();
        askName("New " + WORDS[lvl], "", name ->
                change(() -> { Api.addItem(KINDS[lvl], name, lvl == 0 ? null : path.get(lvl - 1)); return null; }, "Added."));
    }

    private void onRename(JsonObject m) {
        int lvl = level();
        askName("Rename " + WORDS[lvl], titleOf(m), name ->
                change(() -> { Api.renameItem(KINDS[lvl], Models.num(m, "id"), name); return null; }, "Renamed."));
    }

    private void onDelete(JsonObject m) {
        int lvl = level();
        new AlertDialog.Builder(this).setTitle("Delete " + WORDS[lvl] + "?")
                .setMessage("Everything inside it is deleted too.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        change(() -> { Api.deleteItem(KINDS[lvl], Models.num(m, "id")); return null; }, "Deleted."))
                .show();
    }

    private void showLectureDialog(JsonObject lecture) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UI.dp(this, 20), UI.dp(this, 8), UI.dp(this, 20), 0);

        EditText title = UI.input(this, "Lecture title");
        EditText video = UI.input(this, "Video link (YouTube or direct, optional)");
        EditText pdf = UI.input(this, "PDF link (optional)");
        video.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        pdf.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        Switch free = new Switch(this);
        free.setText("Free in Demo Mode");
        free.setTextColor(UI.text(this));
        Integer lectureId = null;
        if (lecture != null) {
            lectureId = Models.num(lecture, "id");
            title.setText(Models.str(lecture, "title"));
            video.setText(Models.str(lecture, "video_url"));
            pdf.setText(Models.str(lecture, "pdf_url"));
            free.setChecked(Models.bool(lecture, "free"));
        }
        for (View v : new View[]{title, video, pdf}) {
            box.addView(v, UI.matchWrap());
            box.addView(UI.space(this, 10));
        }
        box.addView(free, UI.matchWrap());

        final Integer id = lectureId;
        final Integer unitId = level() == 3 ? path.get(2) : null;
        new AlertDialog.Builder(this).setTitle(lecture == null ? "New lecture" : "Edit lecture").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String t = title.getText().toString().trim();
                    if (t.isEmpty()) { toast("Enter a title."); return; }
                    change(() -> {
                        Api.saveLecture(id, unitId, t, video.getText().toString().trim(),
                                pdf.getText().toString().trim(), free.isChecked());
                        return null;
                    }, "Saved.");
                }).show();
    }
}
