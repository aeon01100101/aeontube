package com.aeontube.app;

/**
 * Pure-Java helpers (no Android deps) — host-tested.
 * Handles everything Isaiah might paste as "my channel":
 * a UC… id, an @handle, or a full youtube.com channel URL.
 */
public final class ParseUtil {
    private ParseUtil() {}

    /** True for a raw channel id: UC + 22 base64url chars. */
    public static boolean isChannelId(String s) {
        if (s == null) return false;
        s = s.trim();
        return s.length() == 24 && s.startsWith("UC")
                && s.matches("[A-Za-z0-9_-]{24}");
    }

    /** Extract a video id (11 chars) from a watch URL / youtu.be link / raw id, else null. */
    public static String videoIdFrom(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.matches("[A-Za-z0-9_-]{11}")) return s;
        // youtu.be/VIDEOID
        int i = s.indexOf("youtu.be/");
        if (i >= 0) {
            String rest = s.substring(i + 9);
            return firstToken(rest);
        }
        // watch?v=VIDEOID
        i = s.indexOf("v=");
        if (i >= 0) {
            String rest = s.substring(i + 2);
            String tok = firstToken(rest);
            if (tok != null && tok.length() == 11) return tok;
        }
        // /shorts/VIDEOID or /live/VIDEOID
        for (String marker : new String[]{"/shorts/", "/live/", "/embed/"}) {
            i = s.indexOf(marker);
            if (i >= 0) {
                String tok = firstToken(s.substring(i + marker.length()));
                if (tok != null && tok.length() == 11) return tok;
            }
        }
        return null;
    }

    private static String firstToken(String rest) {
        if (rest == null) return null;
        int end = rest.length();
        for (int j = 0; j < rest.length(); j++) {
            char ch = rest.charAt(j);
            if (!(Character.isLetterOrDigit(ch) || ch == '-' || ch == '_')) { end = j; break; }
        }
        String tok = rest.substring(0, end);
        return tok.isEmpty() ? null : tok;
    }

    /**
     * Normalize a channel reference into a youtube URL that a resolver can turn
     * into a UC id. Returns null if it cannot be interpreted at all.
     */
    public static String channelUrlFrom(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        if (isChannelId(s)) return "https://www.youtube.com/channel/" + s;
        if (s.startsWith("@")) return "https://www.youtube.com/" + s;
        if (s.contains("youtube.com/")) return s.startsWith("http") ? s : "https://" + s;
        if (s.matches("[A-Za-z0-9_.-]+")) return "https://www.youtube.com/@" + s; // bare handle
        return null;
    }

    /** Pull a UC id straight out of a /channel/UC… URL, else null. */
    public static String channelIdFromUrl(String s) {
        if (s == null) return null;
        int i = s.indexOf("/channel/");
        if (i < 0) return null;
        String rest = s.substring(i + 9);
        String tok = firstToken(rest);
        return isChannelId(tok) ? tok : null;
    }

    /** Safe file name for downloads: strip path-hostile chars, cap length. */
    public static String safeFileName(String title, String ext) {
        String t = title == null ? "video" : title.trim();
        t = t.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ");
        t = t.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) t = "video";
        if (t.length() > 80) t = t.substring(0, 80).trim();
        return t + "." + ext;
    }

    /** "1,234,567 views" / "12K views" style label from a raw count (used when backend gives no text). */
    public static String viewsLabel(long views) {
        if (views < 0) return "";
        if (views < 1000) return views + " views";
        if (views < 1_000_000) return trimNum(views / 1000.0) + "K views";
        if (views < 1_000_000_000) return trimNum(views / 1_000_000.0) + "M views";
        return trimNum(views / 1_000_000_000.0) + "B views";
    }

    private static String trimNum(double v) {
        return v >= 100 ? String.format("%d", Math.round(v))
                : String.format(java.util.Locale.US, "%.1f", v).replaceAll("\\.0$", "");
    }
}
