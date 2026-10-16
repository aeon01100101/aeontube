import com.aeontube.app.ParseUtil;

public class TestParseUtil {
    static int passed = 0, failed = 0;

    static void eq(Object actual, Object expected, String label) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) { passed++; }
        else { failed++; System.out.println("FAIL " + label + ": expected=" + expected + " actual=" + actual); }
    }

    static void isTrue(boolean v, String label) { eq(v, Boolean.TRUE, label); }
    static void isFalse(boolean v, String label) { eq(v, Boolean.FALSE, label); }

    public static void main(String[] args) {
        // channel ids
        isTrue(ParseUtil.isChannelId("UCBR8-60-B28hp2BmDPdntcQ"), "ucid valid");
        isFalse(ParseUtil.isChannelId("UCBR8-60-B28hp2BmDPdntc"), "ucid short");
        isFalse(ParseUtil.isChannelId("@YouTube"), "handle not ucid");
        isFalse(ParseUtil.isChannelId(null), "null ucid");

        // video ids
        eq(ParseUtil.videoIdFrom("dQw4w9WgXcQ"), "dQw4w9WgXcQ", "raw id");
        eq(ParseUtil.videoIdFrom("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"), "dQw4w9WgXcQ", "watch url");
        eq(ParseUtil.videoIdFrom("https://youtu.be/dQw4w9WgXcQ?si=xyz"), "dQw4w9WgXcQ", "youtu.be");
        eq(ParseUtil.videoIdFrom("https://www.youtube.com/shorts/dQw4w9WgXcQ"), "dQw4w9WgXcQ", "shorts");
        eq(ParseUtil.videoIdFrom("https://example.com/nothing"), null, "no id");

        // channel url normalization
        eq(ParseUtil.channelUrlFrom("UCBR8-60-B28hp2BmDPdntcQ"),
                "https://www.youtube.com/channel/UCBR8-60-B28hp2BmDPdntcQ", "ucid -> url");
        eq(ParseUtil.channelUrlFrom("@YouTube"), "https://www.youtube.com/@YouTube", "handle -> url");
        eq(ParseUtil.channelUrlFrom("youtube.com/@YouTube"), "https://youtube.com/@YouTube", "bare domain url");
        eq(ParseUtil.channelUrlFrom("MyHandle"), "https://www.youtube.com/@MyHandle", "bare handle");
        eq(ParseUtil.channelUrlFrom(""), null, "empty");
        eq(ParseUtil.channelUrlFrom("  "), null, "blank");

        // id from url
        eq(ParseUtil.channelIdFromUrl("https://www.youtube.com/channel/UCBR8-60-B28hp2BmDPdntcQ/videos"),
                "UCBR8-60-B28hp2BmDPdntcQ", "id from /channel/ url");
        eq(ParseUtil.channelIdFromUrl("https://www.youtube.com/@YouTube"), null, "no id in handle url");

        // filenames
        eq(ParseUtil.safeFileName("Hello: World? *2026*", "mp4"), "Hello World 2026.mp4", "filename sanitize");
        eq(ParseUtil.safeFileName("", "mp4"), "video.mp4", "filename empty");
        eq(ParseUtil.safeFileName(null, "m4a"), "video.m4a", "filename null");
        isTrue(ParseUtil.safeFileName("a".repeat(200), "mp4").length() == 84, "filename capped");

        // views label
        eq(ParseUtil.viewsLabel(999), "999 views", "views small");
        eq(ParseUtil.viewsLabel(1500), "1.5K views", "views K");
        eq(ParseUtil.viewsLabel(2_300_000), "2.3M views", "views M");
        eq(ParseUtil.viewsLabel(-1), "", "views unknown");

        System.out.println("ParseUtil: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
