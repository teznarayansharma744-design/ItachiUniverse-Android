package com.itachi.universe;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads the lecture's PDF (the server checks the key) and shows it page by page. */
public class PdfActivity extends BaseActivity {
    private static final float MIN_ZOOM = 1f, MAX_ZOOM = 3f;

    @Override protected boolean secure() { return true; }

    private int lectureId;
    private String title;
    private byte[] bytes;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pfd;
    private int pages;
    private float aspect = 1f / 1.414f; // page width / height, from page 1
    private float zoom = 1f;

    private FrameLayout body;
    private RecyclerView rv;
    private TextView pageLabel, zoomLabel;
    private PageAdapter adapter;
    private final ExecutorService renderThread = Executors.newSingleThreadExecutor(); // one page open at a time

    private final ActivityResultLauncher<String> saver =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), this::save);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        lectureId = getIntent().getIntExtra("id", -1);
        title = getIntent().getStringExtra("title");
        String subtitle = getIntent().getStringExtra("subtitle");
        if (title == null) title = "PDF";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(UI.dp(this, 24), UI.dp(this, 16), UI.dp(this, 12), UI.dp(this, 12));
        head.addView(UI.backHeader(this, subtitle == null ? "" : subtitle, title), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView dl = UI.tv(this, "\u2B07", 22, false, UI.text(this));
        dl.setGravity(Gravity.CENTER);
        dl.setOnClickListener(v -> {
            if (bytes == null) { toast("The PDF is still loading."); return; }
            saver.launch(fileName());
        });
        head.addView(dl, UI.lp(UI.dp(this, 44), UI.dp(this, 44)));
        root.addView(head, UI.matchWrap());

        body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        setScreen(root);
        load();
    }

    private String fileName() {
        return title.replaceAll("[^A-Za-z0-9._ -]", "_").trim() + ".pdf";
    }

    private void load() {
        body.removeAllViews();
        body.addView(new ProgressBar(this), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        run(() -> {
            Models.PlaySource src = Api.playSource(lectureId, "pdf");
            return Api.download(MediaLinks.directPdfUrl(src.url), src.headers);
        }, new Api.Callback<byte[]>() {
            @Override public void onSuccess(byte[] data) { openDocument(data); }
            @Override public void onError(Api.ApiException e) { showError(e.getMessage()); }
        });
    }

    private void showError(String message) {
        body.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView t = UI.tv(this, message, 14, false, UI.dim(this));
        t.setGravity(Gravity.CENTER);
        t.setPadding(UI.dp(this, 32), 0, UI.dp(this, 32), 0);
        box.addView(t);
        box.addView(UI.textButton(this, "Try again", v -> load()));
        body.addView(box, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openDocument(byte[] data) {
        try {
            File f = new File(getCacheDir(), "lecture_" + lectureId + ".pdf");
            try (OutputStream o = new FileOutputStream(f)) { o.write(data); }
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            pages = renderer.getPageCount();
            PdfRenderer.Page first = renderer.openPage(0);
            aspect = (float) first.getWidth() / first.getHeight();
            first.close();
            bytes = data;
        } catch (Exception e) {
            showError("Could not open this PDF.");
            return;
        }
        showPages();
    }

    private void showPages() {
        body.removeAllViews();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PageAdapter();
        rv.setAdapter(adapter);
        hs.addView(rv, new FrameLayout.LayoutParams(pageAreaWidth(), -1));
        body.addView(hs, new FrameLayout.LayoutParams(-1, -1));

        pageLabel = UI.tv(this, "1 / " + pages, 12, true, Color.WHITE);
        pageLabel.setPadding(UI.dp(this, 12), UI.dp(this, 6), UI.dp(this, 12), UI.dp(this, 6));
        pageLabel.setBackground(UI.box(0xAA000000, UI.dp(this, 999), 0, 0));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pl.setMargins(0, UI.dp(this, 12), UI.dp(this, 12), 0);
        body.addView(pageLabel, pl);

        // zoom controls
        LinearLayout zoomBar = new LinearLayout(this);
        zoomBar.setGravity(Gravity.CENTER_VERTICAL);
        zoomBar.setBackground(UI.box(0xCC000000, UI.dp(this, 999), 0, 0));
        zoomBar.setPadding(UI.dp(this, 6), 0, UI.dp(this, 6), 0);
        TextView minus = UI.tv(this, "\u2212", 22, true, Color.WHITE);
        TextView plus = UI.tv(this, "+", 22, true, Color.WHITE);
        zoomLabel = UI.tv(this, "100%", 13, true, Color.WHITE);
        for (TextView t : new TextView[]{minus, plus}) t.setPadding(UI.dp(this, 14), UI.dp(this, 6), UI.dp(this, 14), UI.dp(this, 6));
        minus.setOnClickListener(v -> setZoom(zoom / 1.25f));
        plus.setOnClickListener(v -> setZoom(zoom * 1.25f));
        zoomBar.addView(minus);
        zoomBar.addView(zoomLabel);
        zoomBar.addView(plus);
        FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        zp.bottomMargin = UI.dp(this, 16);
        body.addView(zoomBar, zp);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) r.getLayoutManager();
                int p = lm == null ? 0 : lm.findFirstVisibleItemPosition();
                pageLabel.setText(Math.max(1, Math.min(pages, p + 1)) + " / " + pages);
            }
        });
    }

    private int pageAreaWidth() {
        return Math.round(getResources().getDisplayMetrics().widthPixels * zoom);
    }

    private void setZoom(float z) {
        z = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        if (Math.abs(z - zoom) < 0.001f) return;
        zoom = z;
        zoomLabel.setText(Math.round(zoom * 100) + "%");
        ViewGroup.LayoutParams lp = rv.getLayoutParams();
        lp.width = pageAreaWidth();
        rv.setLayoutParams(lp);
        adapter.notifyDataSetChanged();
    }

    // ---------- save a copy ----------
    private void save(Uri uri) {
        if (uri == null || bytes == null) { toast("Download cancelled."); return; }
        try (OutputStream o = getContentResolver().openOutputStream(uri)) {
            if (o == null) throw new Exception();
            o.write(bytes);
            toast("PDF saved.");
        } catch (Exception e) {
            toast("Could not save the PDF.");
        }
    }

    // ---------- pages ----------
    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final ImageView img;
            int boundPage = -1;
            VH(ImageView v) { super(v); img = v; }
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int type) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setBackgroundColor(Color.WHITE);
            iv.setAdjustViewBounds(false);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            return new VH(iv);
        }

        @Override public void onBindViewHolder(VH h, int position) {
            int w = pageAreaWidth();
            int hgt = Math.round(w / aspect);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, hgt);
            lp.bottomMargin = UI.dp(PdfActivity.this, 8);
            h.img.setLayoutParams(lp);
            h.img.setImageDrawable(null);
            h.boundPage = position;
            final int page = position;
            renderThread.execute(() -> {
                Bitmap bmp;
                try {
                    synchronized (PdfActivity.this) {
                        if (renderer == null) return;
                        int rw = Math.min(w, 2000), rh = Math.round(rw / aspect);
                        bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        PdfRenderer.Page p = renderer.openPage(page);
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        p.close();
                    }
                } catch (Exception e) {
                    return;
                }
                runOnUiThread(() -> { if (h.boundPage == page) h.img.setImageBitmap(bmp); });
            });
        }

        @Override public int getItemCount() { return pages; }
    }

    @Override
    protected void onDestroy() {
        renderThread.shutdownNow();
        synchronized (this) {
            try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
            renderer = null;
        }
        super.onDestroy();
    }
}
