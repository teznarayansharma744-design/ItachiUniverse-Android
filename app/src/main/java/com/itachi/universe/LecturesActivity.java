package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/** Every lecture of one unit as a card, with "Play video" and "View PDF" buttons. */
public class LecturesActivity extends BaseActivity {
    private static final int ALL = 0, VIDEO = 1, PDF = 2;

    private Models.Subject subject;
    private Models.UnitItem unit;
    private int filter = ALL;
    private LinearLayout tabsRow, list;
    private final Map<Integer, TextView> dlButtons = new HashMap<>();
    private final Runnable dlRefresh = () -> { if (alive()) for (Map.Entry<Integer, TextView> e : dlButtons.entrySet()) styleDl(e.getKey(), e.getValue()); };

    @Override protected void onStart() { super.onStart(); Downloads.addListener(dlRefresh); }
    @Override protected void onStop() { super.onStop(); Downloads.removeListener(dlRefresh); }

    private void styleDl(int id, TextView b) {
        int p = Downloads.progress(id);
        int col = b.getCurrentTextColor();
        if (p >= 0) { b.setText("Downloading " + p + "%  \u00B7  tap to cancel"); UI.withIcon(b, R.drawable.ic_close, 16); }
        else if (Downloads.has(this, id)) { b.setText("Saved offline  \u00B7  tap to remove"); UI.withIcon(b, R.drawable.ic_check, 16); }
        else { b.setText("Download"); UI.withIcon(b, R.drawable.ic_download, 16); }
    }

    private void onDlTap(Models.Lecture l) {
        if (Downloads.progress(l.id) >= 0) { Downloads.cancel(l.id); return; }
        if (Downloads.has(this, l.id)) {
            new GlassDialog.Builder(this).setTitle("Remove this download?")
                    .setMessage("You can download it again any time.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, w) -> Downloads.delete(this, l.id)).show();
            return;
        }
        toast("Download started. It is saved privately inside the app.");
        Downloads.start(this, l.id, l.title, subject.name + " / " + unit.name, msg -> { if (alive()) toast(msg); });
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int bi = getIntent().getIntExtra("b", 0), si = getIntent().getIntExtra("s", 0), ui = getIntent().getIntExtra("u", 0);
        try {
            Models.Batch batch = Catalog.batches.get(bi);
            if (si < 0) {            // lectures that sit straight inside the batch
                subject = new Models.Subject();
                subject.name = batch.name;
                unit = batch.direct;
            } else {
                subject = batch.subjects.get(si);
                unit = ui < 0 ? subject.direct : subject.units.get(ui);
            }
        } catch (Exception e) {
            finish();
            return;
        }

        LinearLayout col = column();
        col.addView(si < 0 ? UI.backHeader(this, "Batch", subject.name)
                : ui < 0 ? UI.backHeader(this, subject.name, "Lectures")
                : UI.backHeader(this, subject.name + " / Unit " + (ui + 1), unit.name));
        col.addView(UI.space(this, 20));

        tabsRow = UI.card(this, 16, 5, null, null);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(tabsRow, UI.matchWrap());
        col.addView(UI.space(this, 16));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, UI.matchWrap());

        setScreen(scroll(col));
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (list != null) render();  // refresh the watched marks after coming back from the player
    }

    private void render() {
        tabsRow.removeAllViews();
        String[] names = {"All", "Video", "PDF"};
        int[] counts = {unit.lectures.size(), unit.videos(), unit.pdfs()};
        for (int i = 0; i < 3; i++) {
            final int f = i;
            boolean sel = filter == i;
            TextView t = UI.tv(this, names[i] + " (" + counts[i] + ")", 14, sel, sel ? UI.text(this) : UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 10), 0, UI.dp(this, 10));
            if (sel) UI.glassBg(t, 12, 2.2f);
            t.setOnClickListener(v -> { filter = f; render(); });
            tabsRow.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }

        list.removeAllViews();
        dlButtons.clear();
        List<Models.Lecture> items = new ArrayList<>();
        for (Models.Lecture l : unit.lectures) {
            if (filter == ALL || (filter == VIDEO && l.hasVideo) || (filter == PDF && l.hasPdf)) items.add(l);
        }
        if (items.isEmpty()) {
            TextView t = UI.tv(this, "Nothing here yet.", 14, false, UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 40), 0, 0);
            list.addView(t, UI.matchWrap());
        }
        for (Models.Lecture l : items) list.addView(lectureCard(l));
    }

    private View lectureCard(Models.Lecture l) {
        boolean locked = Access.isDemo() && !l.free;
        boolean pdfOnly = !l.hasVideo;
        LinearLayout card = UI.card(this, 22, 16, null, null);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(UI.iconTile(this, pdfOnly ? R.drawable.ic_pdf : R.drawable.ic_play, pdfOnly ? UI.PINK : UI.INDIGO, 40));
        TextView title = UI.tv(this, l.title, 16, true, UI.text(this));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = UI.dp(this, 12);
        top.addView(title, tp);
        if (l.hasVideo && Progress.watched(this, l.id)) {
            top.addView(UI.withIcon(UI.tv(this, "Watched", 11, true, UI.TEAL), R.drawable.ic_check, 13));
        } else if (l.hasVideo && Progress.percent(this, l.id) > 0) {
            top.addView(UI.tv(this, Progress.percent(this, l.id) + "%", 11, true, UI.INDIGO));
        } else if (Access.isDemo() && l.free) top.addView(UI.tv(this, "FREE", 11, true, UI.TEAL));
        else if (locked) top.addView(UI.iconView(this, R.drawable.ic_lock, 17, UI.dim(this)));
        card.addView(top, UI.matchWrap());
        card.addView(UI.space(this, 12));

        LinearLayout actions = new LinearLayout(this);
        if (l.hasVideo) {
            actions.addView(UI.iconPill(this, locked ? R.drawable.ic_lock : R.drawable.ic_play, "Play video",
                    v -> { if (locked) Access.showPremiumRequired(this); else play(l); }),
                    new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (l.hasVideo && l.hasPdf) actions.addView(UI.space(this, 1), UI.lp(UI.dp(this, 10), 1));
        if (l.hasPdf) {
            actions.addView(UI.iconPill(this, locked ? R.drawable.ic_lock : R.drawable.ic_pdf, "View PDF",
                    v -> { if (locked) Access.showPremiumRequired(this); else openPdf(l); }),
                    new LinearLayout.LayoutParams(0, -2, 1f));
        }
        card.addView(actions, UI.matchWrap());
        if (l.hasVideo) {
            TextView dl = UI.pillButton(this, "", v -> { if (locked) Access.showPremiumRequired(this); else onDlTap(l); });
            styleDl(l.id, dl);
            dlButtons.put(l.id, dl);
            LinearLayout.LayoutParams dp = UI.matchWrap();
            dp.topMargin = UI.dp(this, 10);
            card.addView(dl, dp);
        }

        LinearLayout.LayoutParams lp = UI.matchWrap();
        lp.bottomMargin = UI.dp(this, 12);
        card.setLayoutParams(lp);
        return card;
    }

    private void play(Models.Lecture l) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("id", l.id);
        i.putExtra("title", l.title);
        i.putExtra("subtitle", subject.name + " / " + unit.name);
        i.putExtra("pdf", l.hasPdf);
        // the other videos in this folder, so the player can list them and play the next one
        List<Models.Lecture> vids = new ArrayList<>();
        for (Models.Lecture x : unit.lectures) if (x.hasVideo) vids.add(x);
        int n = vids.size();
        int[] ids = new int[n];
        String[] titles = new String[n];
        boolean[] frees = new boolean[n], pdfs = new boolean[n];
        for (int k = 0; k < n; k++) {
            ids[k] = vids.get(k).id;
            titles[k] = vids.get(k).title;
            frees[k] = vids.get(k).free;
            pdfs[k] = vids.get(k).hasPdf;
        }
        i.putExtra("ids", ids);
        i.putExtra("titles", titles);
        i.putExtra("frees", frees);
        i.putExtra("pdfs", pdfs);
        startActivity(i);
    }

    private void openPdf(Models.Lecture l) {
        Intent i = new Intent(this, PdfActivity.class);
        i.putExtra("id", l.id);
        i.putExtra("title", l.title);
        i.putExtra("subtitle", subject.name + " / " + unit.name);
        startActivity(i);
    }
}
