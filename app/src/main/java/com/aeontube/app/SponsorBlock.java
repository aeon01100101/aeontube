package com.aeontube.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * SponsorBlock (sponsor.ajay.app) — community-flagged sponsor / intro /
 * self-promo segments inside videos. AeonTube skips them automatically.
 */
public final class SponsorBlock {
    private SponsorBlock() {}

    public static final class Segment {
        public double start, end;
        public String category;
    }

    public static List<Segment> fetch(String videoId) throws Exception {
        String url = "https://sponsor.ajay.app/api/skipSegments?videoID=" + Http.enc(videoId)
                + "&categories=" + Http.enc("[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\"]");
        JSONArray arr;
        try {
            arr = new JSONArray(Http.get(url));
        } catch (Exception e) {
            // 404 from SponsorBlock just means "no segments for this video".
            if (e.getMessage() != null && e.getMessage().contains("404")) return new ArrayList<>();
            throw e;
        }
        List<Segment> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            JSONArray seg = o.optJSONArray("segment");
            if (seg == null || seg.length() < 2) continue;
            Segment s = new Segment();
            s.start = seg.optDouble(0, 0);
            s.end = seg.optDouble(1, 0);
            s.category = o.optString("category", "sponsor");
            if (s.end > s.start) out.add(s);
        }
        return out;
    }
}
