package com.itachi.universe;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Draws the About Developer page from the JSON the server sends. */
final class AboutUi {
    private AboutUi() {}

    static void render(Activity a, LinearLayout box, JsonObject doc) {
        box.removeAllViews();
        int text = UI.text(a), dim = UI.dim(a);
        String name = Models.str(doc, "name"), tagline = Models.str(doc, "tagline"), photo = Models.str(doc, "photo");

        if (!name.isEmpty() || !tagline.isEmpty() || !photo.isEmpty()) {
            LinearLayout head = UI.card(a, 24, 20, null, null);
            if (!photo.isEmpty()) {
                head.setGravity(Gravity.CENTER_HORIZONTAL);
                head.addView(photoView(a, photo, 112), UI.lp(UI.dp(a, 112), UI.dp(a, 112)));
                head.addView(UI.space(a, 14));
            }
            if (!name.isEmpty()) {
                TextView n = UI.tv(a, name, 22, true, text);
                if (!photo.isEmpty()) n.setGravity(Gravity.CENTER);
                head.addView(n);
            }
            if (!tagline.isEmpty()) {
                head.addView(UI.space(a, 6));
                TextView tl = UI.tv(a, tagline, 15, false, dim);
                if (!photo.isEmpty()) tl.setGravity(Gravity.CENTER);
                head.addView(tl);
            }
            box.addView(head, UI.matchWrap());
            box.addView(UI.space(a, 12));
        }

        JsonArray sections = doc.has("sections") && doc.get("sections").isJsonArray() ? doc.getAsJsonArray("sections") : new JsonArray();
        if (sections.size() == 0 && name.isEmpty() && tagline.isEmpty() && photo.isEmpty()) {
            TextView t = UI.tv(a, "Nothing here yet.", 14, false, dim);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(a, 40), 0, 0);
            box.addView(t, UI.matchWrap());
            return;
        }
        for (JsonElement el : sections) {
            JsonObject s = el.getAsJsonObject();
            LinearLayout card = UI.card(a, 22, 18, null, null);
            String heading = Models.str(s, "heading"), body = Models.str(s, "body");
            if (!heading.isEmpty()) card.addView(UI.tv(a, heading, 17, true, text));
            if (!body.isEmpty()) {
                if (!heading.isEmpty()) card.addView(UI.space(a, 8));
                TextView b = UI.tv(a, body, 15, false, dim);
                b.setTextIsSelectable(true);
                card.addView(b);
            }
            JsonArray links = s.has("links") && s.get("links").isJsonArray() ? s.getAsJsonArray("links") : new JsonArray();
            for (JsonElement le : links) {
                JsonObject l = le.getAsJsonObject();
                final String url = Models.str(l, "url");
                card.addView(UI.space(a, 10));
                card.addView(UI.pillButton(a, Models.str(l, "label"), v -> open(a, url)), UI.matchWrap());
            }
            box.addView(card, UI.matchWrap());
            box.addView(UI.space(a, 12));
        }
    }

    private static void open(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException | SecurityException e) {
            GlassToast.show(a, "Could not open this link.");
        }
    }

    private static String cachedKey;
    private static Bitmap cachedBmp;

    /** A round profile picture from a data:image URI. Decodes off the main thread. */
    static ImageView photoView(Activity a, String dataUri, int sizeDp) {
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(UI.box(0x22FFFFFF, UI.dp(a, sizeDp), UI.border(a), 2));
        iv.setClipToOutline(true);
        iv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        if (!dataUri.startsWith("data:")) return iv;
        if (dataUri.equals(cachedKey) && cachedBmp != null) {
            iv.setImageBitmap(cachedBmp);
            return iv;
        }
        new Thread(() -> {
            try {
                int comma = dataUri.indexOf(',');
                byte[] raw = Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                if (bmp == null) return;
                cachedKey = dataUri;
                cachedBmp = bmp;
                iv.post(() -> { iv.setAlpha(0f); iv.setImageBitmap(bmp); iv.animate().alpha(1f).setDuration(300).start(); });
            } catch (Exception ignored) {
            }
        }).start();
        return iv;
    }
}
