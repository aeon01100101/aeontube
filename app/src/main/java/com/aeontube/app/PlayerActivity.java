package com.aeontube.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Player built on ExoPlayer: HLS and DASH adaptive streams (what YouTube
 * increasingly serves exclusively) plus plain MP4 — all ad-free, because the
 * streams come from Piped/Invidious, never the official ad-serving player.
 */
public final class PlayerActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Api api;
    private Prefs prefs;
    private String videoId;

    private PlayerView playerView;
    private ExoPlayer player;
    private ProgressBar progress;
    private TextView titleView;
    private TextView authorView;
    private TextView descView;
    private Spinner qualitySpinner;
    private VideoAdapter relatedAdapter;

    private Models.StreamInfo info;
    private final List<String> qualityLabels = new ArrayList<>();
    private final List<String> qualityUrls = new ArrayList<>(); // index 0 = "Auto"
    private List<String> chain = new ArrayList<>();
    private int chainIdx = 0;
    private boolean userPickedQuality = false;

    private List<SponsorBlock.Segment> segments = new ArrayList<>();
    private boolean sbArmed = false;

    private final Runnable sbPoll = new Runnable() {
        @Override public void run() {
            if (sbArmed && player != null && player.isPlaying()) {
                double pos = player.getCurrentPosition() / 1000.0;
                for (SponsorBlock.Segment s : segments) {
                    if (pos >= s.start && pos < s.end - 0.3) {
                        player.seekTo((long) (s.end * 1000));
                        Toast.makeText(PlayerActivity.this,
                                "⏩ Skipped " + s.category + " (SponsorBlock)", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
            main.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = Api.get(this);
        prefs = new Prefs(this);
        videoId = getIntent().getStringExtra("video_id");
        buildUi();
        if (videoId == null || videoId.isEmpty()) {
            Toast.makeText(this, "No video id", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        load(videoId);
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        sbArmed = false;
        main.removeCallbacks(sbPoll);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                // Walk the fallback chain: HLS → DASH → MP4 qualities.
                if (!userPickedQuality && chainIdx + 1 < chain.size()) {
                    chainIdx++;
                    long keep = Math.max(0, player.getCurrentPosition());
                    Toast.makeText(PlayerActivity.this, "Trying backup stream…", Toast.LENGTH_SHORT).show();
                    playChainItem(chainIdx, keep);
                } else {
                    Toast.makeText(PlayerActivity.this,
                            "This video won't play on the current server — go back and retry; AeonTube will switch servers.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setBackgroundColor(0xFF000000);
        root.addView(playerView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 240)));

        progress = new ProgressBar(this);
        root.addView(progress);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = Ui.column(this);
        Ui.pad(body, this, 12, 8, 12, 12);

        titleView = Ui.text(this, "Loading…", 18, Ui.WHITE, true);
        body.addView(titleView);

        authorView = Ui.text(this, "", 14, Ui.GREY, false);
        authorView.setPadding(0, Ui.dp(this, 4), 0, 0);
        authorView.setOnClickListener(v -> {
            if (info != null && !info.authorId.isEmpty()) {
                Intent i = new Intent(this, ChannelActivity.class);
                i.putExtra("channel_id", info.authorId);
                i.putExtra("channel_name", info.author);
                startActivity(i);
            }
        });
        body.addView(authorView);

        LinearLayout qRow = Ui.row(this);
        qRow.setPadding(0, Ui.dp(this, 10), 0, 0);
        qRow.addView(Ui.text(this, "Quality: ", 14, Ui.WHITE, false));
        qualitySpinner = new Spinner(this);
        qualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (info == null || pos >= qualityUrls.size()) return;
                long keep = player.getCurrentPosition();
                if (pos == 0) {
                    userPickedQuality = false;
                    chainIdx = 0;
                    if (!chain.isEmpty()) playChainItem(0, keep);
                } else {
                    userPickedQuality = true;
                    playSingle(qualityUrls.get(pos), keep);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        qRow.addView(qualitySpinner, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(qRow);

        LinearLayout btnRow = Ui.row(this);
        btnRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        Button dlVideo = Ui.button(this, "⬇ Download video");
        dlVideo.setOnClickListener(v -> {
            if (info == null) return;
            Models.StreamOption m = selectedMuxed();
            if (m == null) m = info.bestMuxed();
            if (m != null) { Downloads.enqueue(this, m.url, info.title, false); return; }
            Models.StreamOption vo = info.bestVideoOnly();
            if (vo == null) { Toast.makeText(this, "No downloadable MP4 for this video", Toast.LENGTH_LONG).show(); return; }
            Toast.makeText(this, "Split-only video: downloading HD video file (no sound track) — grab ♪ Audio too.", Toast.LENGTH_LONG).show();
            Downloads.enqueue(this, vo.url, info.title, false);
        });
        Button dlAudio = Ui.button(this, "♪ Audio only");
        dlAudio.setOnClickListener(v -> {
            if (info == null) return;
            Models.StreamOption a = info.bestAudio();
            if (a == null) { Toast.makeText(this, "No audio stream for this video", Toast.LENGTH_LONG).show(); return; }
            Downloads.enqueue(this, a.url, info.title, true);
        });
        btnRow.addView(dlVideo, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(dlAudio, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(btnRow);

        descView = Ui.text(this, "", 13, Ui.GREY, false);
        descView.setPadding(0, Ui.dp(this, 10), 0, 0);
        body.addView(descView);

        TextView upNext = Ui.text(this, "Up next", 16, Ui.WHITE, true);
        upNext.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        body.addView(upNext);

        ListView relatedList = new ListView(this);
        relatedAdapter = new VideoAdapter(this, true);
        relatedList.setAdapter(relatedAdapter);
        relatedList.setOnItemClickListener((parent, view, pos, id) -> {
            Models.VideoItem item = relatedAdapter.items().get(pos);
            if (!item.isChannel) {
                Intent i = new Intent(this, PlayerActivity.class);
                i.putExtra("video_id", item.id);
                startActivity(i);
            }
        });
        body.addView(relatedList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 520)));

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private Models.StreamOption selectedMuxed() {
        if (info == null) return null;
        int pos = qualitySpinner.getSelectedItemPosition();
        if (pos <= 0 || pos >= qualityUrls.size()) return null;
        String url = qualityUrls.get(pos);
        for (Models.StreamOption o : info.muxed) if (o.url.equals(url)) return o;
        return null;
    }

    private MediaItem itemFor(String url) {
        MediaItem.Builder b = new MediaItem.Builder().setUri(url);
        if (info != null && url.equals(info.dashUrl)) b.setMimeType(MimeTypes.APPLICATION_MPD);
        else if (url.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8);
        return b.build();
    }

    private void playChainItem(int idx, long keepMs) {
        if (idx < 0 || idx >= chain.size()) return;
        player.setMediaItem(itemFor(chain.get(idx)));
        player.prepare();
        if (keepMs > 0) player.seekTo(keepMs);
        player.play();
    }

    private void playSingle(String url, long keepMs) {
        player.setMediaItem(itemFor(url));
        player.prepare();
        if (keepMs > 0) player.seekTo(keepMs);
        player.play();
    }

    private void load(String id) {
        progress.setVisibility(ProgressBar.VISIBLE);
        api.run(() -> api.streams(id), new Api.Cb<Models.StreamInfo>() {
            @Override public void ok(Models.StreamInfo s) {
                main.post(() -> {
                    progress.setVisibility(ProgressBar.GONE);
                    info = s;
                    titleView.setText(s.title);
                    authorView.setText(s.author.isEmpty() ? "" : "📺 " + s.author + "  (tap for channel)");
                    String d = s.description == null ? "" : s.description.trim();
                    descView.setText(d.length() > 1200 ? d.substring(0, 1200) + "…" : d);
                    buildQualityOptions(s);
                    relatedAdapter.setItems(s.related);
                    chain = s.playChain();
                    chainIdx = 0;
                    userPickedQuality = false;
                    if (chain.isEmpty()) {
                        Toast.makeText(PlayerActivity.this, "No playable stream found", Toast.LENGTH_LONG).show();
                    } else {
                        playChainItem(0, 0);
                    }
                    loadSponsorBlock(id);
                });
            }
            @Override public void fail(String error) {
                main.post(() -> {
                    progress.setVisibility(ProgressBar.GONE);
                    titleView.setText("Couldn't load this video");
                    descView.setText(error);
                });
            }
        });
    }

    private void buildQualityOptions(Models.StreamInfo s) {
        qualityLabels.clear();
        qualityUrls.clear();
        qualityLabels.add("Auto (best)");
        qualityUrls.add("");
        List<Models.StreamOption> sorted = new ArrayList<>(s.muxed);
        java.util.Collections.sort(sorted, (a, b) -> Integer.compare(b.height, a.height));
        for (Models.StreamOption o : sorted) {
            qualityLabels.add(o.label);
            qualityUrls.add(o.url);
        }
        ArrayAdapter<String> aa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, qualityLabels);
        qualitySpinner.setAdapter(aa);
    }

    private void loadSponsorBlock(String id) {
        if (!prefs.sponsorBlock()) return;
        api.run(() -> SponsorBlock.fetch(id), new Api.Cb<List<SponsorBlock.Segment>>() {
            @Override public void ok(List<SponsorBlock.Segment> segs) {
                main.post(() -> {
                    segments = segs;
                    if (!segs.isEmpty()) {
                        sbArmed = true;
                        main.removeCallbacks(sbPoll);
                        main.post(sbPoll);
                    }
                });
            }
            @Override public void fail(String error) { /* SponsorBlock is a bonus; stay silent. */ }
        });
    }
}
