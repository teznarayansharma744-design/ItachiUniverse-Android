package com.itachi.universe;

import android.net.Uri;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Looks at a link and decides how the app should open it. */
public final class MediaLinks {
    private MediaLinks() {}

    private static final Pattern YT_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern DRIVE_ID = Pattern.compile("/file/d/([A-Za-z0-9_-]+)");

    private static String valid(String id) {
        return (id != null && YT_ID.matcher(id).matches()) ? id : null;
    }

    /** The 11-character video id when url is a YouTube link, otherwise null. */
    public static String youtubeId(String url) {
        Uri u;
        try { u = Uri.parse(url.trim()); } catch (Exception e) { return null; }
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase().replaceFirst("^(www|m|music)\\.", "");
        List<String> seg = u.getPathSegments();
        if (host.equals("youtu.be")) return seg.isEmpty() ? null : valid(seg.get(0));
        if (host.equals("youtube.com") || host.equals("youtube-nocookie.com")) {
            if ("/watch".equals(u.getPath())) return valid(u.getQueryParameter("v"));
            if (seg.size() >= 2) {
                String s0 = seg.get(0);
                if (s0.equals("shorts") || s0.equals("embed") || s0.equals("live") || s0.equals("v")) {
                    return valid(seg.get(1));
                }
            }
        }
        return null;
    }

    private static String driveId(Uri u) {
        Matcher m = DRIVE_ID.matcher(u.getPath() == null ? "" : u.getPath());
        if (m.find()) return m.group(1);
        return u.getQueryParameter("id");
    }

    /** Google Drive "share" link -> direct download link. Any other link is returned unchanged. */
    public static String directPdfUrl(String url) {
        Uri u;
        try { u = Uri.parse(url.trim()); } catch (Exception e) { return url; }
        if (u.getHost() == null || !u.getHost().toLowerCase().endsWith("drive.google.com")) return url;
        String id = driveId(u);
        if (id == null) return url;
        return "https://drive.google.com/uc?export=download&id=" + id;
    }
}
