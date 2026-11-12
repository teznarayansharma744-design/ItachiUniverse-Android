package com.itachi.universe;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Short Device ID like A7F3-92KD-41QX. Computed EXACTLY like the old Flutter app
 * (sha256 of "itachi-universe|<ANDROID_ID>"), so existing keys keep working on the same phone.
 */
public final class DeviceId {
    private DeviceId() {}

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static String cached;

    private static String format(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int part = 0; part < 3; part++) {
            if (part > 0) sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(CHARS.charAt((b[part * 4 + i] & 0xFF) % CHARS.length()));
            }
        }
        return sb.toString();
    }

    public static synchronized String get(Context ctx) {
        if (cached != null) return cached;
        try {
            String androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isEmpty()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] d = md.digest(("itachi-universe|" + androidId).getBytes(StandardCharsets.UTF_8));
                return cached = format(d);
            }
        } catch (Exception ignored) {
        }
        SharedPreferences p = ctx.getSharedPreferences("device", Context.MODE_PRIVATE);
        String id = p.getString("device_id", null);
        if (id == null) {
            byte[] r = new byte[12];
            new SecureRandom().nextBytes(r);
            id = format(r);
            p.edit().putString("device_id", id).apply();
        }
        return cached = id;
    }
}
