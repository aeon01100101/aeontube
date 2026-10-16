package com.aeontube.app;

import java.util.ArrayList;
import java.util.List;

public final class Models {
    private Models() {}

    public static final class VideoItem {
        public String id = "";
        public String title = "";
        public String author = "";
        public String authorId = "";
        public long durationSec = -1;
        public long views = -1;
        public String viewsText = "";
        public String publishedText = "";
        public String thumbnail = "";
        public boolean isChannel = false; // search hit that is a channel, not a video

        public String metaLine() {
            StringBuilder sb = new StringBuilder();
            if (!author.isEmpty()) sb.append(author);
            if (!viewsText.isEmpty()) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append(viewsText);
            }
            if (!publishedText.isEmpty()) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append(publishedText);
            }
            return sb.toString();
        }

        public String durationText() {
            if (durationSec < 0) return "";
            long h = durationSec / 3600, m = (durationSec % 3600) / 60, s = durationSec % 60;
            return h > 0 ? String.format("%d:%02d:%02d", h, m, s)
                         : String.format("%d:%02d", m, s);
        }
    }

    public static final class StreamOption {
        public String url = "";
        public String label = "";   // e.g. "720p MP4"
        public String mime = "";
        public int height = 0;
        public boolean audioOnly = false;
        public long bitrate = 0;
    }

    public static final class StreamInfo {
        public String id = "";
        public String title = "";
        public String author = "";
        public String authorId = "";
        public String description = "";
        public String thumbnail = "";
        public String hlsUrl = null;
        public String dashUrl = null;
        public List<StreamOption> muxed = new ArrayList<>(); // video+audio, playable & downloadable
        public List<StreamOption> videoOnly = new ArrayList<>(); // adaptive video, no sound track
        public List<StreamOption> audio = new ArrayList<>(); // audio only
        public List<VideoItem> related = new ArrayList<>();

        /** Playable URLs in preference order: HLS (adaptive) → DASH (adaptive) → best muxed MP4. */
        public List<String> playChain() {
            List<String> out = new ArrayList<>();
            if (hlsUrl != null && !hlsUrl.isEmpty()) out.add(hlsUrl);
            if (dashUrl != null && !dashUrl.isEmpty()) out.add(dashUrl);
            List<StreamOption> sorted = new ArrayList<>(muxed);
            sorted.sort((a, b) -> Integer.compare(b.height, a.height));
            for (StreamOption o : sorted) out.add(o.url);
            return out;
        }

        public StreamOption bestMuxed() {
            StreamOption best = null;
            for (StreamOption o : muxed) if (best == null || o.height > best.height) best = o;
            return best;
        }

        public StreamOption bestVideoOnly() {
            StreamOption best = null;
            for (StreamOption o : videoOnly) if (best == null || o.height > best.height) best = o;
            return best;
        }

        public StreamOption bestAudio() {
            StreamOption best = null;
            for (StreamOption o : audio) if (best == null || o.bitrate > best.bitrate) best = o;
            return best;
        }
    }

    public static final class ChannelInfo {
        public String id = "";
        public String name = "";
        public String description = "";
        public String avatar = "";
        public long subs = -1;
        public List<VideoItem> videos = new ArrayList<>();
        public String nextPage = null; // backend continuation token, if any
    }

    public static final class Page {
        public List<VideoItem> items = new ArrayList<>();
        public String nextPage = null;
    }
}
