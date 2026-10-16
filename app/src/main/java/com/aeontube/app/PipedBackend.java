package com.aeontube.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Piped API — https://docs.piped.video/docs/api-documentation */
public final class PipedBackend implements Backend {
    private final String base;

    public PipedBackend(String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override public String name() { return "Piped " + base.replace("https://", ""); }

    private JSONObject getObj(String path) throws Exception {
        return new JSONObject(Http.get(base + path));
    }

    private JSONArray getArr(String path) throws Exception {
        return new JSONArray(Http.get(base + path));
    }

    @Override public Models.Page trending(String region) throws Exception {
        JSONArray arr = getArr("/trending?region=" + Http.enc(region == null ? "US" : region));
        Models.Page p = new Models.Page();
        for (int i = 0; i < arr.length(); i++) {
            Models.VideoItem v = parseItem(arr.optJSONObject(i));
            if (v != null) p.items.add(v);
        }
        return p;
    }

    @Override public Models.Page search(String query, String nextPage) throws Exception {
        Models.Page p = new Models.Page();
        JSONObject o;
        if (nextPage == null) {
            o = getObj("/search?q=" + Http.enc(query) + "&filter=videos");
        } else {
            o = getObj("/nextpage/search?nextpage=" + Http.enc(nextPage)
                    + "&q=" + Http.enc(query) + "&filter=videos");
        }
        JSONArray items = o.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                Models.VideoItem v = parseItem(items.optJSONObject(i));
                if (v != null) p.items.add(v);
            }
        }
        String np = o.optString("nextpage", null);
        p.nextPage = (np == null || np.equals("null") || np.isEmpty()) ? null : np;
        return p;
    }

    @Override public Models.StreamInfo streams(String videoId) throws Exception {
        JSONObject o = getObj("/streams/" + videoId);
        Models.StreamInfo s = new Models.StreamInfo();
        s.id = videoId;
        s.title = o.optString("title", "");
        s.author = o.optString("uploader", "");
        s.authorId = idFromChannelUrl(o.optString("uploaderUrl", ""));
        s.description = o.optString("description", "");
        s.thumbnail = o.optString("thumbnailUrl", "");
        String hls = o.optString("hls", null);
        s.hlsUrl = (hls == null || hls.equals("null") || hls.isEmpty()) ? null : hls;
        String dash = o.optString("dash", null);
        s.dashUrl = (dash == null || dash.equals("null") || dash.isEmpty()) ? null : dash;

        JSONArray vs = o.optJSONArray("videoStreams");
        if (vs != null) {
            for (int i = 0; i < vs.length(); i++) {
                JSONObject f = vs.optJSONObject(i);
                if (f == null) continue;
                String url = f.optString("url", "");
                String quality = f.optString("quality", "");
                String mime = f.optString("mimeType", "");
                String format = f.optString("format", "");
                if (url.isEmpty()) continue;
                if ("HLS".equals(format) || url.contains(".m3u8")) {
                    if (s.hlsUrl == null) s.hlsUrl = url;
                    continue;
                }
                if (quality.contains("LBRY")) continue; // mirror of a different CDN; prefer native streams
                Models.StreamOption opt = new Models.StreamOption();
                opt.url = url;
                opt.mime = mime;
                opt.height = heightFromQuality(quality);
                opt.audioOnly = f.optBoolean("videoOnly", false);
                opt.bitrate = f.optLong("bitrate", 0);
                opt.label = (opt.height > 0 ? opt.height + "p" : quality) + " " + extOf(mime, format);
                if (!opt.audioOnly) s.muxed.add(opt);
                else if (mime.toLowerCase().contains("mp4")) s.videoOnly.add(opt);
            }
        }
        JSONArray as = o.optJSONArray("audioStreams");
        if (as != null) {
            for (int i = 0; i < as.length(); i++) {
                JSONObject f = as.optJSONObject(i);
                if (f == null) continue;
                String url = f.optString("url", "");
                if (url.isEmpty()) continue;
                Models.StreamOption opt = new Models.StreamOption();
                opt.url = url;
                opt.mime = f.optString("mimeType", "");
                opt.audioOnly = true;
                opt.bitrate = f.optLong("bitrate", 0);
                opt.label = "Audio " + (opt.bitrate > 0 ? (opt.bitrate / 1000) + " kbps " : "")
                        + extOf(opt.mime, f.optString("format", ""));
                s.audio.add(opt);
            }
        }
        JSONArray rel = o.optJSONArray("relatedStreams");
        if (rel != null) {
            for (int i = 0; i < rel.length(); i++) {
                Models.VideoItem v = parseItem(rel.optJSONObject(i));
                if (v != null && !v.isChannel) s.related.add(v);
            }
        }
        if (s.muxed.isEmpty() && s.hlsUrl == null && s.dashUrl == null)
            throw new Exception(name() + ": no playable streams");
        return s;
    }

    @Override public Models.ChannelInfo channel(String channelId) throws Exception {
        JSONObject o = getObj("/channel/" + channelId);
        Models.ChannelInfo ch = new Models.ChannelInfo();
        ch.id = channelId;
        ch.name = o.optString("name", "");
        ch.description = o.optString("description", "");
        ch.avatar = o.optString("avatarUrl", "");
        ch.subs = o.optLong("subscriberCount", -1);
        JSONArray rel = o.optJSONArray("relatedStreams");
        if (rel != null) {
            for (int i = 0; i < rel.length(); i++) {
                Models.VideoItem v = parseItem(rel.optJSONObject(i));
                if (v != null && !v.isChannel) ch.videos.add(v);
            }
        }
        String np = o.optString("nextpage", null);
        ch.nextPage = (np == null || np.equals("null") || np.isEmpty()) ? null : np;
        return ch;
    }

    @Override public Models.Page channelVideos(String channelId, String nextPage) throws Exception {
        JSONObject o = getObj("/nextpage/channel/" + channelId + "?nextpage=" + Http.enc(nextPage));
        Models.Page p = new Models.Page();
        JSONArray rel = o.optJSONArray("relatedStreams");
        if (rel != null) {
            for (int i = 0; i < rel.length(); i++) {
                Models.VideoItem v = parseItem(rel.optJSONObject(i));
                if (v != null && !v.isChannel) p.items.add(v);
            }
        }
        String np = o.optString("nextpage", null);
        p.nextPage = (np == null || np.equals("null") || np.isEmpty()) ? null : np;
        return p;
    }

    @Override public String resolveChannelUrl(String url) throws Exception {
        // Piped has no resolver; but a /channel/UC… URL carries the id directly.
        String id = ParseUtil.channelIdFromUrl(url);
        if (id != null) return id;
        throw new Exception(name() + " cannot resolve handles");
    }

    static Models.VideoItem parseItem(JSONObject o) {
        if (o == null) return null;
        String url = o.optString("url", "");
        String type = o.optString("type", "stream");
        Models.VideoItem v = new Models.VideoItem();
        if ("channel".equals(type)) {
            v.isChannel = true;
            v.id = idFromChannelUrl(url);
            v.title = o.optString("name", o.optString("title", ""));
            v.thumbnail = o.optString("thumbnail", o.optString("image", ""));
            return v;
        }
        String id = ParseUtil.videoIdFrom(url);
        if (id == null) id = o.optString("videoId", "");
        if (id == null || id.isEmpty()) return null;
        v.id = id;
        v.title = o.optString("title", "");
        v.author = o.optString("uploaderName", o.optString("uploader", ""));
        v.authorId = idFromChannelUrl(o.optString("uploaderUrl", ""));
        v.durationSec = o.optLong("duration", -1);
        v.views = o.optLong("views", -1);
        v.viewsText = v.views >= 0 ? ParseUtil.viewsLabel(v.views) : "";
        v.publishedText = o.optString("uploadedDate", "");
        v.thumbnail = o.optString("thumbnail", "");
        return v;
    }

    static String idFromChannelUrl(String u) {
        if (u == null) return "";
        int i = u.indexOf("/channel/");
        if (i >= 0) {
            String rest = u.substring(i + 9);
            int end = rest.length();
            for (int j = 0; j < rest.length(); j++) {
                char c = rest.charAt(j);
                if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) { end = j; break; }
            }
            return rest.substring(0, end);
        }
        return u.startsWith("UC") ? u : "";
    }

    static int heightFromQuality(String q) {
        if (q == null) return 0;
        String digits = q.replaceAll("[^0-9]", "");
        try { return digits.isEmpty() ? 0 : Integer.parseInt(digits); }
        catch (Exception e) { return 0; }
    }

    static String extOf(String mime, String format) {
        String m = (mime == null ? "" : mime).toLowerCase();
        if (m.contains("webm")) return "WEBM";
        if (m.contains("mp4")) return "MP4";
        if (m.contains("audio")) return "M4A";
        if (format != null && !format.isEmpty()) return format;
        return "MP4";
    }
}
