package com.aeontube.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny thumbnail loader with an in-memory cache. */
public final class Thumbs {
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(24 * 1024 * 1024) {
                @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
            };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Thumbs() {}

    public static void load(ImageView iv, String url) {
        iv.setTag(url);
        if (url == null || url.isEmpty()) { iv.setImageBitmap(null); return; }
        Bitmap hit = CACHE.get(url);
        if (hit != null) { iv.setImageBitmap(hit); return; }
        iv.setImageBitmap(null);
        POOL.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("User-Agent", Http.UA);
                try (InputStream in = c.getInputStream()) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) {
                        CACHE.put(url, b);
                        MAIN.post(() -> {
                            if (url.equals(iv.getTag())) iv.setImageBitmap(b);
                        });
                    }
                }
            } catch (Exception ignored) {}
        });
    }
}
