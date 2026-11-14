package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.List;

public class AdminPanelActivity extends BaseActivity {
    private LinearLayout statsRow, list;
    private String query = "";
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int dim = UI.dim(this), text = UI.text(this);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        LinearLayout col = column();

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(UI.backHeader(this, "Owner", "Admin panel"), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(UI.textButton(this, "Lectures", v -> startActivity(new Intent(this, AdminContentActivity.class))));
        head.addView(UI.textButton(this, "About", v -> startActivity(new Intent(this, AdminAboutActivity.class))));
        head.addView(UI.textButton(this, "Log out", v -> {
            Api.adminLogout();
            startActivity(new Intent(this, AdminLoginActivity.class));
            finish();
        }));
        col.addView(head, UI.matchWrap());
        col.addView(UI.space(this, 20));

        statsRow = new LinearLayout(this);
        col.addView(statsRow, UI.matchWrap());
        col.addView(UI.space(this, 16));

        EditText search = UI.input(this, "Search by key, device or name");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable e) {
                query = e.toString().trim();
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = AdminPanelActivity.this::load;
                handler.postDelayed(pendingSearch, 350);
            }
        });
        col.addView(search, UI.matchWrap());
        col.addView(UI.space(this, 16));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, UI.matchWrap());
        col.addView(UI.space(this, 90));

        root.addView(scroll(col), new android.widget.FrameLayout.LayoutParams(-1, -1));

        TextView fab = UI.tv(this, "+  New key", 16, true, android.graphics.Color.WHITE);
        fab.setGravity(Gravity.CENTER);
        fab.setPadding(UI.dp(this, 22), UI.dp(this, 16), UI.dp(this, 22), UI.dp(this, 16));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{UI.INDIGO, UI.TEAL});
        g.setCornerRadius(UI.dp(this, 20));
        fab.setBackground(g);
        fab.setOnClickListener(v -> showCreateDialog());
        android.widget.FrameLayout.LayoutParams fp = new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, UI.dp(this, 20), UI.dp(this, 20));
        root.addView(fab, fp);

        setScreen(root);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void toLogin() {
        Intent i = new Intent(this, AdminLoginActivity.class);
        startActivity(i);
        finish();
    }

    private void load() {
        list.removeAllViews();
        list.addView(new ProgressBar(this));
        run(() -> {
            Models.AdminStats s = Api.adminStats();
            List<Models.AdminKey> k = Api.adminKeys(query);
            return new Object[]{s, k};
        }, new Api.Callback<Object[]>() {
            @Override @SuppressWarnings("unchecked")
            public void onSuccess(Object[] r) {
                showStats((Models.AdminStats) r[0]);
                showKeys((List<Models.AdminKey>) r[1]);
            }
            @Override public void onError(Api.ApiException e) {
                if (e.unauthorized) { toLogin(); return; }
                list.removeAllViews();
                LinearLayout c = UI.card(AdminPanelActivity.this, 22, 16, null, null);
                c.addView(UI.tv(AdminPanelActivity.this, e.getMessage(), 14, false, UI.dim(AdminPanelActivity.this)));
                c.addView(UI.textButton(AdminPanelActivity.this, "Try again", v -> load()));
                list.addView(c, UI.matchWrap());
            }
        });
    }

    private void showStats(Models.AdminStats s) {
        statsRow.removeAllViews();
        String[] labels = {"Total", "Active", "Expired", "Revoked"};
        int[] vals = {s.total, s.active, s.expired, s.revoked};
        for (int i = 0; i < 4; i++) {
            LinearLayout c = UI.card(this, 16, 10, null, null);
            c.setGravity(Gravity.CENTER);
            c.addView(UI.tv(this, String.valueOf(vals[i]), 20, true, UI.text(this)));
            c.addView(UI.tv(this, labels[i], 11, false, UI.dim(this)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(i == 0 ? 0 : UI.dp(this, 4), 0, i == 3 ? 0 : UI.dp(this, 4), 0);
            statsRow.addView(c, lp);
        }
    }

    private void showKeys(List<Models.AdminKey> keys) {
        list.removeAllViews();
        if (keys.isEmpty()) {
            TextView t = UI.tv(this, "No keys here yet.", 14, false, UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 30), 0, 0);
            list.addView(t, UI.matchWrap());
            return;
        }
        for (Models.AdminKey k : keys) list.addView(keyCard(k));
    }

    private String date(String iso) { return iso != null && iso.length() >= 10 ? iso.substring(0, 10) : iso; }

    private View keyCard(Models.AdminKey k) {
        LinearLayout card = UI.card(this, 20, 16, null, null);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView key = UI.tv(this, k.key, 15, true, UI.text(this));
        key.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        top.addView(key, new LinearLayout.LayoutParams(0, -2, 1f));

        int color = "active".equals(k.status) ? UI.TEAL : "revoked".equals(k.status) ? 0xFFFF6B6B : 0xFFFFB84D;
        TextView chip = UI.tv(this, k.status.toUpperCase(), 10, true, color);
        chip.setPadding(UI.dp(this, 8), UI.dp(this, 3), UI.dp(this, 8), UI.dp(this, 3));
        chip.setBackground(UI.box((color & 0x00FFFFFF) | 0x30000000, UI.dp(this, 999), 0, 0));
        top.addView(chip);

        TextView more = UI.tv(this, "\u22EE", 22, true, UI.text(this));
        more.setPadding(UI.dp(this, 12), 0, UI.dp(this, 4), 0);
        more.setOnClickListener(v -> showMenu(v, k));
        top.addView(more);
        card.addView(top, UI.matchWrap());

        card.addView(UI.space(this, 4));
        String line = "active".equals(k.status) ? k.daysLeft + " days left  \u2022  until " + date(k.expiresAt)
                : "Expires " + date(k.expiresAt);
        card.addView(UI.tv(this, line, 12, false, UI.dim(this)));
        card.addView(UI.tv(this, "Device " + k.deviceId, 12, false, UI.dim(this)));
        if (k.note != null && !k.note.isEmpty()) card.addView(UI.tv(this, k.note, 13, true, UI.text(this)));

        LinearLayout.LayoutParams lp = UI.matchWrap();
        lp.bottomMargin = UI.dp(this, 10);
        card.setLayoutParams(lp);
        return card;
    }

    private void showMenu(View anchor, Models.AdminKey k) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Copy key");
        m.getMenu().add(0, 2, 1, "Extend 30 days");
        m.getMenu().add(0, 3, 2, "Change device / log out phone");
        if ("revoked".equals(k.status)) m.getMenu().add(0, 4, 3, "Restore");
        else m.getMenu().add(0, 5, 3, "Revoke");
        m.getMenu().add(0, 6, 4, "Delete");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: copyToClipboard(k.key, "Key copied."); break;
                case 2: change(() -> { Api.extendKey(k.key, 30); return null; }, "Extended by 30 days."); break;
                case 3: askText("Change device", "New Device ID", k.deviceId, id ->
                        change(() -> { Api.changeKeyDevice(k.key, id); return null; }, "Device changed.")); break;
                case 4: change(() -> { Api.restoreKey(k.key); return null; }, "Key restored."); break;
                case 5: change(() -> { Api.revokeKey(k.key); return null; }, "Key revoked."); break;
                case 6: new AlertDialog.Builder(this).setTitle("Delete this key?")
                        .setMessage("The student will lose access.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> change(() -> { Api.deleteKey(k.key); return null; }, "Key deleted."))
                        .show(); break;
            }
            return true;
        });
        m.show();
    }

    private void change(Api.Job<Object> job, String done) {
        run(job, new Api.Callback<Object>() {
            @Override public void onSuccess(Object r) { toast(done); load(); }
            @Override public void onError(Api.ApiException e) {
                if (e.unauthorized) toLogin(); else toast(e.getMessage());
            }
        });
    }

    interface TextResult { void got(String s); }

    private void askText(String title, String hint, String initial, TextResult cb) {
        EditText e = UI.input(this, hint);
        e.setText(initial);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(UI.dp(this, 20), UI.dp(this, 8), UI.dp(this, 20), 0);
        wrap.addView(e, UI.matchWrap());
        new AlertDialog.Builder(this).setTitle(title).setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String v = e.getText().toString().trim();
                    if (!v.isEmpty()) cb.got(v);
                }).show();
    }

    // ---------- create key ----------
    private void showCreateDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UI.dp(this, 20), UI.dp(this, 8), UI.dp(this, 20), 0);

        box.addView(UI.tv(this, "Device ID (from the student)", 13, false, UI.dim(this)));
        EditText device = UI.input(this, "A7F3-92KD-41QX");
        device.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        box.addView(device, UI.matchWrap());
        box.addView(UI.space(this, 12));

        box.addView(UI.tv(this, "Valid for (days)", 13, false, UI.dim(this)));
        EditText days = UI.input(this, "Number of days");
        days.setInputType(InputType.TYPE_CLASS_NUMBER);
        days.setText("30");
        LinearLayout chips = new LinearLayout(this);
        for (int d : new int[]{7, 30, 90, 180, 365}) {
            TextView c = UI.pillButton(this, String.valueOf(d), v -> days.setText(String.valueOf(d)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            cp.rightMargin = UI.dp(this, 4);
            chips.addView(c, cp);
        }
        box.addView(chips, UI.matchWrap());
        box.addView(UI.space(this, 8));
        box.addView(days, UI.matchWrap());
        box.addView(UI.space(this, 12));

        box.addView(UI.tv(this, "Student name or note (optional)", 13, false, UI.dim(this)));
        EditText note = UI.input(this, "Rahul, batch 1");
        box.addView(note, UI.matchWrap());

        new AlertDialog.Builder(this).setTitle("New key").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Generate key", (d, w) -> {
                    String dev = device.getText().toString().trim();
                    int n;
                    try { n = Integer.parseInt(days.getText().toString().trim()); } catch (Exception e) { n = 0; }
                    if (dev.isEmpty() || n <= 0) { toast("Enter the Device ID and number of days."); return; }
                    final int dd = n;
                    run(() -> Api.createKey(dev, dd, note.getText().toString().trim()), new Api.Callback<Models.AdminKey>() {
                        @Override public void onSuccess(Models.AdminKey k) { load(); showCreated(k); }
                        @Override public void onError(Api.ApiException e) {
                            if (e.unauthorized) toLogin(); else toast(e.getMessage());
                        }
                    });
                }).show();
    }

    private void showCreated(Models.AdminKey k) {
        String msg = "Your Itachi Universe premium key:\n" + k.key + "\n\nValid until " + date(k.expiresAt)
                + ". It works only on your device.";
        new AlertDialog.Builder(this).setTitle("Key created")
                .setMessage(k.key + "\n\nValid until " + date(k.expiresAt))
                .setNeutralButton("Copy key", (d, w) -> copyToClipboard(k.key, "Key copied."))
                .setNegativeButton("Copy message", (d, w) -> copyToClipboard(msg, "Message copied. Paste it in Telegram or WhatsApp."))
                .setPositiveButton("Done", null)
                .show();
    }
}
