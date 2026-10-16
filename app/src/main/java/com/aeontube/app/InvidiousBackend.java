package com.aeontube.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Invidious API — https://docs.invidious.io/api/ */
public final class InvidiousBackend implements Backend {
    private final String base;

    public InvidiousBackend(String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override public String name() { return "Invidious " + base.replace("https://", ""); }

    private JSONObject getObj(String path) throws Exception {
        return new JSONObject(Http.get(base + path));
    }

    private JSONArray getArr(String path) throws Exception {
        return new JSONArray(Http.get(base + path));
    }

    @Override public Models.Page trending(String region) throws Exception {
        JSONArray arr = getArr("/api/v1/trending?type=default");
        Models.Page p = new Models.Page();
        for (int i = 0; i < arr.length(); i++) {
            Models.VideoItem v = parseItem(arr.optJSONObject(i));
            if (v != null) p.items.add(v);
        }
        if (p.items.isEmpty()) throw new Exception(name() + ": empty trending");
        return p;
    }

    @Override public Models.Page search(String query, String nextPage) throws Exception {
        int page = 1;
        if (nextPage != null) {
            try { page = Integer.parseInt(nextPage); } catch (Exception ignored) {}
        }
        JSONArray arr = getArr("/api/v1/search?q=" + Http.enc(query) + "&type=video&page=" + page);
        Models.Page p = new Models.Page();
        for (int i = 0; i < arr.length(); i++) {
            Models.VideoItem v = parseItem(arr.optJSONObject(i));
            if (v != null) p.items.add(v);
        }
        p.nextPage = p.items.isEmpty() ? null : String.valueOf(page + 1);
        return p;
    }

    @Override public Models.StreamInfo streams(String videoId) throws Exception {
        JSONObject o = getObj("/api/v1/videos/" + videoId);
        if (o.has("error")) throw new Exception(name() + ": " + o.optString("error"));
        Models.StreamInfo s = new Models.StreamInfo();
        s.id = videoId;
        s.title = o.optString("title", "");
        s.author = o.optString("author", "");
        s.authorId = o.optString("authorId", "");
        s.description = o.optString("description", "");
        s.thumbnail = bestThumb(o.optJSONArray("videoThumbnails"));
        String hls = o.optString("hlsUrl", null);
        s.hlsUrl = (hls == null || hls.equals("null") || hls.isEmpty()) ? null : abs(hls);
        String dash = o.optString("dashUrl", null);
        s.dashUrl = (dash == null || dash.equals("null") || dash.isEmpty()) ? null : abs(dash);

        JSONArray fs = o.optJSONArray("formatStreams");
        if (fs != null) {
            for (int i = 0; i < fs.length(); i++) {
                JSONObject f = fs.optJSONObject(i);
                if (f == null) continue;
                String url = f.optString("url", "");
                if (url.isEmpty()) continue;
                Models.StreamOption opt = new Models.StreamOption();
                opt.url = abs(url);
                opt.mime = f.optString("type", "");
                String q = f.optString("qualityLabel", f.optString("quality", ""));
                opt.height = PipedBackend.heightFromQuality(q);
                opt.label = q + " " + f.optString("container", "mp4").toUpperCase();
                s.muxed.add(opt);
            }
        }
        JSONArray af = o.optJSONArray("adaptiveFormats");
        if (af != null) {
            for (int i = 0; i < af.length(); i++) {
                JSONObject f = af.optJSONObject(i);
                if (f == null) continue;
                String type = f.optString("type", "");
                String url = f.optString("url", "");
                if (url.isEmpty()) continue;
                Models.StreamOption opt = new Models.StreamOption();
                opt.url = abs(url);
                opt.mime = type;
                opt.bitrate = f.optLong("bitrate", 0);
                if (type.startsWith("audio")) {
                    opt.audioOnly = true;
                    opt.label = "Audio " + (opt.bitrate > 0 ? (opt.bitrate / 1000) + " kbps " : "")
                            + f.optString("container", "m4a").toUpperCase();
                    s.audio.add(opt);
                } else if (type.startsWith("video") && type.toLowerCase().contains("mp4")) {
                    String res = f.optString("resolution", "");
                    opt.height = PipedBackend.heightFromQuality(res);
                    opt.label = res + " MP4 (video only)";
                    s.videoOnly.add(opt);
                }
            }
        }
        JSONArray rec = o.optJSONArray("recommendedVideos");
        if (rec != null) {
            for (int i = 0; i < rec.length(); i++) {
                Models.VideoItem v = parseItem(rec.optJSONObject(i));
                if (v != null) s.related.add(v);
            }
        }
        if (s.muxed.isEmpty() && s.hlsUrl == null && s.dashUrl == null)
            throw new Exception(name() + ": no playable streams");
        return s;
    }

    @Override public Models.ChannelInfo channel(String channelId) throws Exception {
        JSONObject o = getObj("/api/v1/channels/" + channelId);
        if (o.has("error")) throw new Exception(name() + ": " + o.optString("error"));
        Models.ChannelInfo ch = new Models.ChannelInfo();
        ch.id = channelId;
        ch.name = o.optString("author", "");
        ch.description = o.optString("description", "");
        ch.subs = o.optLong("subCount", -1);
        JSONArray thumbs = o.optJSONArray("authorThumbnails");
        if (thumbs != null && thumbs.length() > 0) {
            ch.avatar = abs(thumbs.optJSONObject(thumbs.length() - 1).optString("url", ""));
        }
        JSONArray latest = o.optJSONArray("latestVideos");
        if (latest != null) {
            for (int i = 0; i < latest.length(); i++) {
                Models.VideoItem v = parseItem(latest.optJSONObject(i));
                if (v != null) ch.videos.add(v);
            }
        }
        if (ch.videos.isEmpty()) {
            Models.Page p = channelVideos(channelId, null);
            ch.videos.addAll(p.items);
            ch.nextPage = p.nextPage;
        }
        return ch;
    }

    @Override public Models.Page channelVideos(String channelId, String nextPage) throws Exception {
        String path = "/api/v1/channels/" + channelId + "/videos?sort_by=newest";
        if (nextPage != null) path += "&continuation=" + Http.enc(nextPage);
        JSONObject o = getObj(path);
        Models.Page p = new Models.Page();
        JSONArray arr = o.optJSONArray("videos");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                Models.VideoItem v = parseItem(arr.optJSONObject(i));
                if (v != null) p.items.add(v);
            }
        }
        String cont = o.optString("continuation", null);
        p.nextPage = (cont == null || cont.equals("null") || cont.isEmpty()) ? null : cont;
        return p;
    }

    @Override public String resolveChannelUrl(String url) throws Exception {
        String direct = ParseUtil.channelIdFromUrl(url);
        if (direct != null) return direct;
        JSONObject o = getObj("/api/v1/resolveurl?url=" + Http.enc(url));
        String ucid = o.optString("ucid", o.optString("browseId", ""));
        if (ucid != null && ParseUtil.isChannelId(ucid)) return ucid;
        throw new Exception(name() + ": could not resolve " + url);
    }

    private String abs(String u) {
        if (u == null) return "";
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("/")) return base + u;
        return u;
    }

    private String bestThumb(JSONArray thumbs) {
        if (thumbs == null) return "";
        String best = "";
        int bestW = -1;
        for (int i = 0; i < thumbs.length(); i++) {
            JSONObject t = thumbs.optJSONObject(i);
            if (t == null) continue;
            int w = t.optInt("width", 0);
            if (w > bestW) { bestW = w; best = t.optString("url", ""); }
        }
        return abs(best);
    }

    Models.VideoItem parseItem(JSONObject o) {
        if (o == null) return null;
        if ("channel".equals(o.optString("type", ""))) return null;
        String id = o.optString("videoId", "");
        if (id.isEmpty()) return null;
        Models.VideoItem v = new Models.VideoItem();
        v.id = id;
        v.title = o.optString("title", "");
        v.author = o.optString("author", "");
        v.authorId = o.optString("authorId", "");
        v.durationSec = o.optLong("lengthSeconds", -1);
        v.views = o.optLong("viewCount", -1);
        String vt = o.optString("viewCountText", "");
        v.viewsText = !vt.isEmpty() ? vt : ParseUtil.viewsLabel(v.views);
        v.publishedText = o.optString("publishedText", "");
        v.thumbnail = bestThumb(o.optJSONArray("videoThumbnails"));
        if (v.thumbnail.isEmpty()) v.thumbnail = base + "/vi/" + id + "/hqdefault.jpg";
        return v;
    }
}
