package com.itachi.universe;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A PDF viewer that can sit on its own screen or below a video. Pages scroll, pinch or +/- zooms,
 * a handle on the right edge can be held and dragged to jump anywhere, and the toolbar downloads
 * the file or opens it in another PDF app. It sizes itself to whatever space it is given.
 */
public class PdfPanel extends LinearLayout {
    private static final float MIN_ZOOM = 1f, MAX_ZOOM = 4f;

    private final BaseActivity host;
    private final int lectureId;
    private final String title;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService renderThread = Executors.newSingleThreadExecutor(); // one page at a time

    private byte[] bytes;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pfd;
    private int pages;
    private float aspect = 1f / 1.414f;
    private float zoom = 1f;
    private int lastW = -1;
    private boolean released;

    private final FrameLayout body;
    private RecyclerView rv;
    private TextView pageLabel, zoomLabel, bubble;
    private View handle;
    private PageAdapter adapter;
    private boolean dragging;
    private Runnable onDownload;
    private Runnable onClose;
    private TextView offlineBtn;
    private String subtitle = "";
    private final Runnable dim = () -> { if (handle != null && !dragging) handle.animate().alpha(0.4f).setDuration(300).start(); };

    public PdfPanel(BaseActivity host, int lectureId, String title, boolean toolbar) {
        super(host);
        this.host = host;
        this.lectureId = lectureId;
        this.title = title == null || title.isEmpty() ? "PDF" : title;
        setOrientation(VERTICAL);

        if (toolbar && !Access.isDemo()) {   // demo users cannot save or export anything
            LinearLayout bar = new LinearLayout(host);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(UI.dp(host, 12), UI.dp(host, 2), UI.dp(host, 12), UI.dp(host, 2));
            offlineBtn = UI.textButton(host, "Offline", v -> toggleOffline());
            bar.addView(offlineBtn, new LayoutParams(0, -2, 1f));
            bar.addView(UI.withIcon(UI.textButton(host, "Save", v -> {
                if (bytes == null) host.toast("The PDF is still loading.");
                else if (onDownload != null) onDownload.run();
            }), R.drawable.ic_pdf, 15), new LayoutParams(0, -2, 1f));
            bar.addView(UI.withIcon(UI.textButton(host, "Open", v -> openIn()), R.drawable.ic_open, 15), new LayoutParams(0, -2, 1f));
            addView(bar, UI.matchWrap());
            refreshOffline();
        }

        body = new FrameLayout(host);
        addView(body, new LayoutParams(-1, 0, 1f));
        body.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            if (rv != null && w != lastW) {  // rotated or resized: fit the pages to the new width
                lastW = w;
                applyWidth();
            }
        });
        load();
    }

    public void setOnDownload(Runnable r) { onDownload = r; }
    public void setOnClose(Runnable r) { onClose = r; }
    public void setSubtitle(String s) { subtitle = s == null ? "" : s; }

    /** "Offline" saves the PDF inside the app (private, works without internet); tapping again removes it. */
    private void refreshOffline() {
        if (offlineBtn == null) return;
        boolean saved = Downloads.hasPdf(host, lectureId);
        offlineBtn.setText(saved ? "Saved offline" : "Offline");
        UI.withIcon(offlineBtn, saved ? R.drawable.ic_check : R.drawable.ic_download, 15);
    }

    private void toggleOffline() {
        if (Downloads.hasPdf(host, lectureId)) {
            new GlassDialog.Builder(host).setTitle("Remove offline PDF?")
                    .setMessage("You can save it again any time.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, w) -> { Downloads.deletePdf(host, lectureId); refreshOffline(); })
                    .show();
        } else if (bytes == null) {
            host.toast("The PDF is still loading.");
        } else {
            try {
                Downloads.savePdf(host, lectureId, title, subtitle, bytes);
                refreshOffline();
                host.toast("Saved. It opens without internet now.");
            } catch (Exception e) {
                host.toast("Could not save the PDF.");
            }
        }
    }

    public String fileName() { return title.replaceAll("[^A-Za-z0-9._ -]", "_").trim() + ".pdf"; }

    // ---------- loading ----------
    private void load() {
        body.removeAllViews();
        rv = null;
        body.addView(new ProgressBar(host), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        host.runJob(() -> {
            if (Downloads.hasPdf(host, lectureId)) {  // saved on this phone: no internet needed
                java.io.File f = Downloads.pdfFile(host, lectureId);
                byte[] data = new byte[(int) f.length()];
                try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                    int off = 0, n;
                    while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
                } catch (java.io.IOException e) {
                    throw new Api.ApiException("Could not read the saved PDF.");
                }
                return data;
            }
            Models.PlaySource src = Api.playSource(lectureId, "pdf");
            return Api.download(MediaLinks.directPdfUrl(src.url), src.headers);
        }, new Api.Callback<byte[]>() {
            @Override public void onSuccess(byte[] data) { if (!released) openDocument(data); }
            @Override public void onError(Api.ApiException e) { if (!released) showError(e.getMessage()); }
        });
    }

    private void showError(String message) {
        body.removeAllViews();
        rv = null;
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView t = UI.tv(host, message, 14, false, UI.dim(host));
        t.setGravity(Gravity.CENTER);
        t.setPadding(UI.dp(host, 32), 0, UI.dp(host, 32), 0);
        box.addView(t);
        box.addView(UI.textButton(host, "Try again", v -> load()));
        body.addView(box, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openDocument(byte[] data) {
        try {
            File f = new File(host.getCacheDir(), "lecture_" + lectureId + ".pdf");
            try (OutputStream o = new FileOutputStream(f)) { o.write(data); }
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            pages = renderer.getPageCount();
            PdfRenderer.Page first = renderer.openPage(0);
            aspect = (float) first.getWidth() / first.getHeight();
            first.close();
            bytes = data;
            refreshOffline();
        } catch (Exception e) {
            showError("Could not open this PDF.");
            return;
        }
        showPages();
    }

    // ---------- pages, zoom, handle ----------
    private int viewportW() {
        int w = body.getWidth();
        return w > 0 ? w : host.getResources().getDisplayMetrics().widthPixels;
    }

    private int pageAreaWidth() { return Math.round(viewportW() * zoom); }

    private void showPages() {
        body.removeAllViews();
        HorizontalScrollView hs = new HorizontalScrollView(host);
        hs.setHorizontalScrollBarEnabled(false);
        rv = new RecyclerView(host);
        rv.setLayoutManager(new LinearLayoutManager(host));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new PageAdapter();
        rv.setAdapter(adapter);
        hs.addView(rv, new FrameLayout.LayoutParams(pageAreaWidth(), -1));
        body.addView(hs, new FrameLayout.LayoutParams(-1, -1));

        // pinch to zoom: applied when the fingers lift, so the pages do not re-render on every frame
        final ScaleGestureDetector pinch = new ScaleGestureDetector(host, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            float acc = 1f;
            @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                acc = 1f;
                rv.setPivotX(d.getFocusX());
                rv.setPivotY(d.getFocusY());
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector d) {
                acc *= d.getScaleFactor();
                float preview = Math.max(0.75f, Math.min(1.6f, acc));
                rv.setScaleX(preview);
                rv.setScaleY(preview);
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                rv.setScaleX(1f);
                rv.setScaleY(1f);
                setZoom(zoom * acc);
                acc = 1f;
            }
        });
        rv.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override public boolean onInterceptTouchEvent(RecyclerView r, MotionEvent e) {
                pinch.onTouchEvent(e);
                return pinch.isInProgress();
            }
            @Override public void onTouchEvent(RecyclerView r, MotionEvent e) { pinch.onTouchEvent(e); }
            @Override public void onRequestDisallowInterceptTouchEvent(boolean disallow) {}
        });

        pageLabel = UI.tv(host, "1 / " + pages, 12, true, Color.WHITE);
        pageLabel.setPadding(UI.dp(host, 12), UI.dp(host, 6), UI.dp(host, 12), UI.dp(host, 6));
        pageLabel.setBackground(UI.box(0xAA000000, UI.dp(host, 999), 0, 0));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        pl.setMargins(UI.dp(host, 12), UI.dp(host, 12), 0, 0);
        body.addView(pageLabel, pl);

        LinearLayout zoomBar = new LinearLayout(host);
        zoomBar.setGravity(Gravity.CENTER_VERTICAL);
        zoomBar.setBackground(UI.box(0xCC000000, UI.dp(host, 999), 0, 0));
        zoomBar.setPadding(UI.dp(host, 6), 0, UI.dp(host, 6), 0);
        TextView minus = UI.tv(host, "−", 22, true, Color.WHITE);
        TextView plus = UI.tv(host, "+", 22, true, Color.WHITE);
        zoomLabel = UI.tv(host, Math.round(zoom * 100) + "%", 13, true, Color.WHITE);
        for (TextView t : new TextView[]{minus, plus}) t.setPadding(UI.dp(host, 14), UI.dp(host, 6), UI.dp(host, 14), UI.dp(host, 6));
        minus.setOnClickListener(v -> setZoom(zoom / 1.25f));
        plus.setOnClickListener(v -> setZoom(zoom * 1.25f));
        zoomBar.addView(minus);
        zoomBar.addView(zoomLabel);
        zoomBar.addView(plus);
        FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        zp.bottomMargin = UI.dp(host, 14);
        body.addView(zoomBar, zp);

        // the scroll handle: hold it and drag to jump to any page
        GradientDrawable hd = new GradientDrawable();
        hd.setColor(0xE6FFFFFF);
        hd.setCornerRadius(UI.dp(host, 14));
        hd.setStroke(UI.dp(host, 1), 0x55000000);
        handle = new View(host);
        handle.setBackground(hd);
        handle.setAlpha(0.4f);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(UI.dp(host, 22), UI.dp(host, 64), Gravity.TOP | Gravity.END);
        hp.rightMargin = UI.dp(host, 3);
        body.addView(handle, hp);

        bubble = UI.tv(host, "", 15, true, Color.WHITE);
        bubble.setPadding(UI.dp(host, 16), UI.dp(host, 10), UI.dp(host, 16), UI.dp(host, 10));
        bubble.setBackground(UI.box(0xDD000000, UI.dp(host, 14), 0, 0));
        bubble.setVisibility(View.GONE);
        body.addView(bubble, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        handle.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    handle.animate().cancel();
                    handle.setAlpha(1f);
                    bubble.setVisibility(View.VISIBLE);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int[] loc = new int[2];
                    body.getLocationOnScreen(loc);
                    float room = Math.max(1, body.getHeight() - handle.getHeight());
                    float frac = (e.getRawY() - loc[1] - handle.getHeight() / 2f) / room;
                    scrollToFraction(Math.max(0f, Math.min(1f, frac)));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    bubble.setVisibility(View.GONE);
                    ui.removeCallbacks(dim);
                    ui.postDelayed(dim, 1200);
                    return true;
                default:
                    return false;
            }
        });

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                int p = currentPage();
                pageLabel.setText(p + " / " + pages);
                if (dragging) bubble.setText(p + " / " + pages);
                moveHandle();
                if (!dragging) {
                    handle.setAlpha(1f);
                    ui.removeCallbacks(dim);
                    ui.postDelayed(dim, 1200);
                }
            }
        });
        lastW = body.getWidth();
        body.post(this::moveHandle);
    }

    private int currentPage() {
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        int p = lm == null ? 0 : lm.findFirstVisibleItemPosition();
        return Math.max(1, Math.min(pages, p + 1));
    }

    private int maxScroll() {
        return Math.max(0, rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent());
    }

    private void moveHandle() {
        if (rv == null || handle == null) return;
        int max = maxScroll();
        float frac = max <= 0 ? 0f : Math.max(0f, Math.min(1f, rv.computeVerticalScrollOffset() / (float) max));
        handle.setTranslationY(frac * Math.max(0, body.getHeight() - handle.getHeight()));
    }

    private void scrollToFraction(float frac) {
        int target = Math.round(frac * maxScroll());
        rv.scrollBy(0, target - rv.computeVerticalScrollOffset());
        moveHandle();
    }

    private void setZoom(float z) {
        if (rv == null) return;
        z = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        if (Math.abs(z - zoom) < 0.001f) return;
        zoom = z;
        zoomLabel.setText(Math.round(zoom * 100) + "%");
        applyWidth();
    }

    private void applyWidth() {
        if (rv == null) return;
        ViewGroup.LayoutParams lp = rv.getLayoutParams();
        lp.width = pageAreaWidth();
        rv.setLayoutParams(lp);
        adapter.notifyDataSetChanged();
        body.post(this::moveHandle);
    }

    // ---------- download and open in ----------
    public void saveTo(Uri uri) {
        if (uri == null || bytes == null) { host.toast("Download cancelled."); return; }
        try (OutputStream o = host.getContentResolver().openOutputStream(uri)) {
            if (o == null) throw new Exception();
            o.write(bytes);
            host.toast("PDF saved.");
        } catch (Exception e) {
            host.toast("Could not save the PDF.");
        }
    }

    private void openIn() {
        if (bytes == null) { host.toast("The PDF is still loading."); return; }
        try {
            File f = new File(host.getCacheDir(), "lecture_" + lectureId + ".pdf");
            Uri uri = FileProvider.getUriForFile(host, host.getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            host.startActivity(Intent.createChooser(i, "Open with"));
        } catch (ActivityNotFoundException e) {
            host.toast("No PDF app found on this phone.");
        } catch (Exception e) {
            host.toast("Could not open the PDF.");
        }
    }

    /** Call from the activity's onDestroy. */
    public void release() {
        released = true;
        ui.removeCallbacksAndMessages(null);
        renderThread.shutdownNow();
        synchronized (this) {
            try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
            renderer = null;
        }
    }

    // ---------- pages ----------
    /** The latest picture of a page and the width it was drawn at. */
    private static final class Rendered {
        final Bitmap bmp;
        final int width;
        Rendered(Bitmap b, int w) { bmp = b; width = w; }
    }

    private static final int MAX_RENDER_W = 2000;

    // Pages already drawn are kept, so zooming back out (or scrolling back) shows them instantly
    // and zooming in shows the old picture, stretched, until the sharper one is ready. No white flash.
    private final android.util.LruCache<Integer, Rendered> pageCache = new android.util.LruCache<Integer, Rendered>(
            (int) Math.min(96L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 5)) {
        @Override protected int sizeOf(Integer key, Rendered r) { return r.bmp.getByteCount(); }
    };

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final ImageView img;
            int boundPage = -1;
            volatile int token;
            VH(ImageView v) { super(v); img = v; }
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int type) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setBackgroundColor(0xFFEDEDF2);   // a quiet grey until the page appears, not a white flash
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            return new VH(iv);
        }

        @Override public void onBindViewHolder(VH h, int position) {
            final int w = pageAreaWidth();
            int hgt = Math.round(w / aspect);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, hgt);
            lp.bottomMargin = UI.dp(host, 8);
            h.img.setLayoutParams(lp);

            final int page = position;
            final int want = Math.min(Math.max(w, 1), MAX_RENDER_W);
            final int token = ++h.token;
            Rendered have = pageCache.get(page);
            if (have != null) h.img.setImageBitmap(have.bmp);          // old picture, stretched, while a sharper one loads
            else if (h.boundPage != page) h.img.setImageDrawable(null);
            h.boundPage = page;
            if (have != null && have.width >= want) return;             // already sharp enough

            renderThread.execute(() -> {
                if (h.token != token || released) return;               // scrolled away or zoomed again: skip the work
                Bitmap bmp;
                try {
                    synchronized (PdfPanel.this) {
                        if (renderer == null) return;
                        int rh = Math.round(want / aspect);
                        bmp = Bitmap.createBitmap(want, rh, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        PdfRenderer.Page p = renderer.openPage(page);
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        p.close();
                    }
                } catch (Exception e) {
                    return;
                }
                final Bitmap done = bmp;
                ui.post(() -> {
                    if (released) return;
                    pageCache.put(page, new Rendered(done, want));
                    if (h.token == token) h.img.setImageBitmap(done);
                });
            });
        }

        @Override public int getItemCount() { return pages; }
    }
}
