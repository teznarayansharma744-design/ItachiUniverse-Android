package com.itachi.universe;

import java.util.ArrayList;
import java.util.List;

/** Holds the batches shown in the app. */
public final class Catalog {
    private Catalog() {}

    public static List<Models.Batch> batches = new ArrayList<>();
    public static boolean loading = false;
    public static String error = null;

    public interface Listener { void onChanged(); }

    public static void load(Listener l) {
        loading = true;
        error = null;
        if (l != null) l.onChanged();
        Api.async(() -> Models.parseCatalog(Api.catalogJson()), new Api.Callback<List<Models.Batch>>() {
            @Override public void onSuccess(List<Models.Batch> r) {
                batches = r;
                loading = false;
                if (l != null) l.onChanged();
            }
            @Override public void onError(Api.ApiException e) {
                error = e.getMessage();
                loading = false;
                if (l != null) l.onChanged();
            }
        });
    }
}
