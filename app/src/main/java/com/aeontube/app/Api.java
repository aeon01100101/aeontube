package com.aeontube.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Failover coordinator. Every operation walks the backend list
 * (last-known-good first) until one answers. Public Piped/Invidious
 * instances come and go — this is what keeps AeonTube working anyway.
 */
public final class Api {
    private static Api instance;

    public static synchronized Api get(Context c) {
        if (instance == null) instance = new Api(c.getApplicationContext());
        return instance;
    }

    /** Rebuild the backend list after Settings change a custom instance. */
    public static synchronized void reset() { instance = null; }

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final List<Backend> backends = new ArrayList<>();
    private final Prefs prefs;
    private volatile String lastGoodName = "";

    private Api(Context c) {
        prefs = new Prefs(c);
        String cp = prefs.customPiped();
        if (!cp.isEmpty()) backends.add(new PipedBackend(cp));
        backends.add(new PipedBackend("https://pipedapi.ducks.party"));
        backends.add(new PipedBackend("https://api.piped.private.coffee"));
        String ci = prefs.customInvidious();
        if (!ci.isEmpty()) backends.add(new InvidiousBackend(ci));
        backends.add(new InvidiousBackend("https://invidious.f5.si"));
        backends.add(new PipedBackend("https://pipedapi.kavin.rocks"));
        backends.add(new PipedBackend("https://pipedapi.leptons.xyz"));
        backends.add(new InvidiousBackend("https://inv.nadeko.net"));
        backends.add(new InvidiousBackend("https://invidious.privacyredirect.com"));
        lastGoodName = prefs.lastBackend();
    }

    public interface Cb<T> {
        void ok(T value);
        void fail(String error);
    }

    public <T> void run(Callable<T> job, Cb<T> cb) {
        pool.execute(() -> {
            try {
                T v = job.call();
                cb.ok(v);
            } catch (Exception e) {
                cb.fail(e.getMessage() == null ? "Network error" : e.getMessage());
            }
        });
    }

    private interface Op<T> { T apply(Backend b) throws Exception; }

    /** Try backends in order (last good first); first success wins and sticks. */
    private <T> T across(Op<T> op) throws Exception {
        List<Backend> order = new ArrayList<>(backends);
        java.util.Collections.sort(order, (a, b) -> {
            if (a.name().equals(lastGoodName)) return -1;
            if (b.name().equals(lastGoodName)) return 1;
            return 0;
        });
        Exception last = null;
        for (Backend b : order) {
            try {
                T v = op.apply(b);
                if (!b.name().equals(lastGoodName)) {
                    lastGoodName = b.name();
                    prefs.setLastBackend(lastGoodName);
                }
                return v;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new Exception("All servers failed. Last error: "
                + (last == null ? "unknown" : last.getMessage()));
    }

    public Models.Page trending() throws Exception {
        return across(b -> b.trending("US"));
    }

    public Models.Page search(String q, String nextPage, Backend sticky) throws Exception {
        if (sticky != null && nextPage != null) return sticky.search(q, nextPage);
        return across(b -> b.search(q, nextPage));
    }

    public Models.StreamInfo streams(String videoId) throws Exception {
        return across(b -> b.streams(videoId));
    }

    public Models.ChannelInfo channel(String channelId) throws Exception {
        return across(b -> b.channel(channelId));
    }

    public Models.Page channelVideos(String channelId, String nextPage, Backend sticky) throws Exception {
        if (sticky != null && nextPage != null) return sticky.channelVideos(channelId, nextPage);
        return across(b -> b.channelVideos(channelId, nextPage));
    }

    public String resolveChannel(String input) throws Exception {
        String url = ParseUtil.channelUrlFrom(input);
        if (url == null) throw new Exception("Couldn't understand \"" + input
                + "\" — paste your @handle, channel URL, or UC… id.");
        String direct = ParseUtil.channelIdFromUrl(url);
        if (direct != null) return direct;
        if (ParseUtil.isChannelId(input.trim())) return input.trim();
        return across(b -> b.resolveChannelUrl(url));
    }

    public String lastGoodName() { return lastGoodName; }

    /** Exposed so paged lists can stick to the backend that served page 1. */
    public Backend backendNamed(String name) {
        for (Backend b : backends) if (b.name().equals(name)) return b;
        return null;
    }

    public Backend lastGoodBackend() {
        Backend b = backendNamed(lastGoodName);
        return b != null ? b : backends.get(0);
    }
}
