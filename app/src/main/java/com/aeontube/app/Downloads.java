package com.aeontube.app;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

/** Downloads go through the system DownloadManager into Downloads/AeonTube. */
public final class Downloads {
    private Downloads() {}

    public static void enqueue(Context c, String url, String title, boolean audio) {
        try {
            String ext = audio ? guessAudioExt(url) : "mp4";
            String fileName = ParseUtil.safeFileName(title, ext);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setTitle(title);
            r.setDescription(audio ? "AeonTube audio download" : "AeonTube video download");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AeonTube/" + fileName);
            r.addRequestHeader("User-Agent", Http.UA);
            DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(r);
            Toast.makeText(c, "Downloading: " + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(c, "Download failed to start: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String guessAudioExt(String url) {
        String u = url.toLowerCase();
        if (u.contains("webm") || u.contains("opus")) return "webm";
        return "m4a";
    }

    /** Fetch the stream list, then download the best copy of a video. Used by list rows. */
    public static void downloadVideo(Context c, Models.VideoItem v, boolean audioOnly) {
        Toast.makeText(c, "Finding download for: " + v.title, Toast.LENGTH_SHORT).show();
        Api api = Api.get(c);
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        api.run(() -> api.streams(v.id), new Api.Cb<Models.StreamInfo>() {
            @Override public void ok(Models.StreamInfo s) {
                main.post(() -> {
                    if (audioOnly) {
                        Models.StreamOption a = s.bestAudio();
                        if (a == null) { toast(c, "No audio stream for this video"); return; }
                        enqueue(c, a.url, s.title, true);
                    } else {
                        Models.StreamOption m = s.bestMuxed();
                        if (m != null) { enqueue(c, m.url, s.title, false); return; }
                        // YouTube serves some videos split-only: grab the HD video file
                        // and tell Isaiah plainly that it has no sound track baked in.
                        Models.StreamOption vo = s.bestVideoOnly();
                        if (vo == null) { toast(c, "No downloadable MP4 for this video"); return; }
                        toast(c, "This video is split-only on YouTube — downloading the HD video file (no sound track). Use ♪ Audio for the sound.");
                        enqueue(c, vo.url, s.title, false);
                    }
                });
            }
            @Override public void fail(String error) {
                main.post(() -> toast(c, "Download lookup failed: " + error));
            }
        });
    }

    private static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
    }
}
