package com.itachi.universe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Lectures saved on this phone. They play without internet and are invisible to other apps. */
public class DownloadsActivity extends BaseActivity {
    private LinearLayout list;
    private TextView summary;

    @Override protected boolean secure() { return true; }

    private final Runnable refresh = () -> { if (alive()) render(); };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = column();
        String openFolder = getIntent().getStringExtra("folder");
        col.addView(openFolder == null ? UI.backHeader(this, "Offline", "Downloads") : UI.backHeader(this, "Downloads", openFolder));
        col.addView(UI.space(this, 8));
        summary = UI.tv(this, "", 13, false, UI.dim(this));
        summary.setPadding(UI.dp(this, 56), 0, 0, 0);
        col.addView(summary);
        col.addView(UI.space(this, 16));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, UI.matchWrap());
        setScreen(scroll(col));
        render();
    }

    @Override protected void onStart() { super.onStart(); Downloads.addListener(refresh); }
    @Override protected void onStop() { super.onStop(); Downloads.removeListener(refresh); }

    private static String mb(long bytes) {
        double m = bytes / 1048576.0;
        return m >= 1024 ? String.format(Locale.US, "%.2f GB", m / 1024) : String.format(Locale.US, "%.0f MB", m);
    }

    private static final String OTHER = "Other";

    private static String folderOf(Downloads.Item it) { return it.subtitle == null || it.subtitle.trim().isEmpty() ? OTHER : it.subtitle.trim(); }

    private void render() {
        list.removeAllViews();
        final String folder = getIntent().getStringExtra("folder");   // null = the list of folders
        List<Downloads.Item> items = Downloads.list(this);
        List<Downloads.Item> pdfs = Downloads.listPdfs(this);
        long pdfBytes = 0;
        for (Downloads.Item q : pdfs) pdfBytes += q.size;
        int total = items.size() + pdfs.size();
        summary.setText(folder != null ? "" : total == 0 ? "" : items.size() + (items.size() == 1 ? " video" : " videos") + ", " + pdfs.size()
                + (pdfs.size() == 1 ? " PDF" : " PDFs") + " \u00B7 " + mb(Downloads.totalBytes(this) + pdfBytes) + " on this phone");
        if (total == 0) {
            TextView t = UI.tv(this, "Nothing downloaded yet.\nOpen a lecture and tap Download to watch it offline.", 14, false, UI.dim(this));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(this, 48), 0, 0);
            list.addView(t, UI.matchWrap());
            return;
        }

        if (folder == null) {   // one folder per parent (Subject / Unit), videos and notes together
            java.util.LinkedHashMap<String, int[]> counts = new java.util.LinkedHashMap<>();   // name -> {videos, pdfs}
            java.util.HashMap<String, Long> bytes = new java.util.HashMap<>();
            for (Downloads.Item it : items) {
                int[] c = counts.get(folderOf(it));
                if (c == null) counts.put(folderOf(it), c = new int[2]);
                c[0]++;
                bytes.put(folderOf(it), (bytes.containsKey(folderOf(it)) ? bytes.get(folderOf(it)) : 0L) + it.size);
            }
            for (Downloads.Item it : pdfs) {
                int[] c = counts.get(folderOf(it));
                if (c == null) counts.put(folderOf(it), c = new int[2]);
                c[1]++;
                bytes.put(folderOf(it), (bytes.containsKey(folderOf(it)) ? bytes.get(folderOf(it)) : 0L) + it.size);
            }
            for (java.util.Map.Entry<String, int[]> e : counts.entrySet()) {
                final String name = e.getKey();
                LinearLayout card = UI.card(this, 22, 16, null, v -> {
                    Intent i = new Intent(this, DownloadsActivity.class);
                    i.putExtra("folder", name);
                    startActivity(i);
                });
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.addView(UI.iconTile(this, R.drawable.ic_folder, UI.INDIGO, 44));
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.addView(UI.tv(this, name, 16, true, UI.text(this)));
                int[] c = e.getValue();
                col.addView(UI.tv(this, c[0] + (c[0] == 1 ? " video" : " videos") + "  \u00B7  " + c[1] + (c[1] == 1 ? " PDF" : " PDFs")
                        + "  \u00B7  " + mb(bytes.get(name)), 12, false, UI.dim(this)));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
                cp.leftMargin = UI.dp(this, 14);
                card.addView(col, cp);
                card.addView(UI.iconView(this, R.drawable.ic_chevron, 20, UI.dim(this)));
                LinearLayout.LayoutParams lp = UI.matchWrap();
                lp.bottomMargin = UI.dp(this, 12);
                list.addView(card, lp);
            }
            return;
        }

        // inside a folder: each lecture shows its video and its PDF together
        java.util.HashSet<Integer> withVideo = new java.util.HashSet<>();
        for (Downloads.Item it : items) {
            if (!folderOf(it).equals(folder)) continue;
            withVideo.add(it.id);
            LinearLayout card = UI.card(this, 22, 16, null, null);
            card.addView(UI.tv(this, it.title, 17, true, UI.text(this)));
            boolean pdf = Downloads.hasPdf(this, it.id);
            long size = it.size + (pdf ? Downloads.pdfFile(this, it.id).length() : 0);
            card.addView(UI.tv(this, "Video" + (pdf ? " + PDF" : "") + "  \u00B7  " + mb(size), 12, false, UI.dim(this)));
            card.addView(UI.space(this, 12));
            LinearLayout row = new LinearLayout(this);
            row.addView(UI.iconPill(this, R.drawable.ic_play, "Play", v -> {
                Intent i = new Intent(this, PlayerActivity.class);
                i.putExtra("id", it.id);
                i.putExtra("title", it.title);
                i.putExtra("subtitle", it.subtitle);
                i.putExtra("pdf", Downloads.hasPdf(this, it.id));   // notes show under the player
                startActivity(i);
            }), new LinearLayout.LayoutParams(0, -2, 1f));
            if (pdf) {
                row.addView(UI.space(this, 1), UI.lp(UI.dp(this, 10), 1));
                row.addView(UI.iconPill(this, R.drawable.ic_pdf, "PDF", v -> {
                    Intent i = new Intent(this, PdfActivity.class);
                    i.putExtra("id", it.id);
                    i.putExtra("title", it.title);
                    i.putExtra("subtitle", it.subtitle);
                    startActivity(i);
                }), new LinearLayout.LayoutParams(0, -2, 1f));
            }
            row.addView(UI.space(this, 1), UI.lp(UI.dp(this, 10), 1));
            row.addView(UI.iconPill(this, R.drawable.ic_trash, "Remove", v -> new GlassDialog.Builder(this)
                    .setTitle("Remove this download?")
                    .setMessage("The video and its PDF are removed from this phone. You can download them again any time.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, w) -> { Downloads.delete(this, it.id); Downloads.deletePdf(this, it.id); })
                    .show()), new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(row, UI.matchWrap());
            LinearLayout.LayoutParams lp = UI.matchWrap();
            lp.bottomMargin = UI.dp(this, 12);
            list.addView(card, lp);
        }
        for (Downloads.Item it : pdfs) {   // notes saved without a video
            if (!folderOf(it).equals(folder) || withVideo.contains(it.id)) continue;
            LinearLayout card = UI.card(this, 22, 16, null, null);
            card.addView(UI.tv(this, it.title, 17, true, UI.text(this)));
            card.addView(UI.tv(this, "PDF notes  \u00B7  " + mb(it.size), 12, false, UI.PINK));
            card.addView(UI.space(this, 12));
            LinearLayout row = new LinearLayout(this);
            row.addView(UI.iconPill(this, R.drawable.ic_pdf, "Open", v -> {
                Intent i = new Intent(this, PdfActivity.class);
                i.putExtra("id", it.id);
                i.putExtra("title", it.title);
                i.putExtra("subtitle", it.subtitle);
                startActivity(i);
            }), new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(UI.space(this, 1), UI.lp(UI.dp(this, 10), 1));
            row.addView(UI.iconPill(this, R.drawable.ic_trash, "Remove", v -> new GlassDialog.Builder(this)
                    .setTitle("Remove this PDF?")
                    .setMessage("You can save it again any time.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, w) -> Downloads.deletePdf(this, it.id))
                    .show()), new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(row, UI.matchWrap());
            LinearLayout.LayoutParams lp = UI.matchWrap();
            lp.bottomMargin = UI.dp(this, 12);
            list.addView(card, lp);
        }
    }
}
