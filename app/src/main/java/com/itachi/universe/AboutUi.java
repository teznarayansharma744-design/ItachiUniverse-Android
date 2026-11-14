package com.itachi.universe;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Draws the About Developer page from the JSON the server sends. */
final class AboutUi {
    private AboutUi() {}

    static void render(Activity a, LinearLayout box, JsonObject doc) {
        box.removeAllViews();
        int text = UI.text(a), dim = UI.dim(a);
        String name = Models.str(doc, "name"), tagline = Models.str(doc, "tagline");

        if (!name.isEmpty() || !tagline.isEmpty()) {
            LinearLayout head = UI.card(a, 24, 20, null, null);
            if (!name.isEmpty()) head.addView(UI.tv(a, name, 22, true, text));
            if (!tagline.isEmpty()) {
                head.addView(UI.space(a, 6));
                head.addView(UI.tv(a, tagline, 15, false, dim));
            }
            box.addView(head, UI.matchWrap());
            box.addView(UI.space(a, 12));
        }

        JsonArray sections = doc.has("sections") && doc.get("sections").isJsonArray() ? doc.getAsJsonArray("sections") : new JsonArray();
        if (sections.size() == 0 && name.isEmpty() && tagline.isEmpty()) {
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
            Toast.makeText(a, "Could not open this link.", Toast.LENGTH_SHORT).show();
        }
    }
}
