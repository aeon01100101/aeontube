package com.aeontube.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class ChannelActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Api api;
    private String channelId;
    private TextView header;
    private TextView status;
    private ProgressBar progress;
    private VideoAdapter adapter;
    private String nextPage = null;
    private Backend sticky = null;
    private boolean loading = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = Api.get(this);
        channelId = getIntent().getStringExtra("channel_id");
        String name = getIntent().getStringExtra("channel_name");

        LinearLayout root = Ui.column(this);
        LinearLayout top = Ui.row(this);
        Ui.pad(top, this, 8, 8, 8, 2);
        Button back = Ui.button(this, "← Back");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        header = Ui.text(this, name == null || name.isEmpty() ? "Channel" : name, 19, Ui.WHITE, true);
        top.addView(header, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);

        progress = new ProgressBar(this);
        root.addView(progress);
        status = Ui.text(this, "Loading channel…", 13, Ui.GREY, false);
        Ui.pad(status, this, 12, 4, 12, 4);
        root.addView(status);

        ListView list = new ListView(this);
        list.setBackgroundColor(Ui.BG);
        list.setDividerHeight(Ui.dp(this, 4));
        adapter = new VideoAdapter(this, true);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Models.VideoItem item = adapter.items().get(pos);
            if (!item.isChannel) {
                Intent i = new Intent(this, PlayerActivity.class);
                i.putExtra("video_id", item.id);
                startActivity(i);
            }
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) {}
            @Override public void onScroll(AbsListView v, int first, int visible, int total) {
                if (!loading && total > 0 && first + visible >= total - 4) loadMore();
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        if (channelId == null || channelId.isEmpty()) {
            status.setText("No channel id given.");
            progress.setVisibility(ProgressBar.GONE);
            return;
        }
        load();
    }

    private void load() {
        loading = true;
        api.run(() -> api.channel(channelId), new Api.Cb<Models.ChannelInfo>() {
            @Override public void ok(Models.ChannelInfo ch) {
                main.post(() -> {
                    loading = false;
                    progress.setVisibility(ProgressBar.GONE);
                    if (!ch.name.isEmpty()) header.setText(ch.name);
                    status.setText((ch.subs >= 0 ? String.format("%,d", ch.subs) + " subscribers • " : "")
                            + ch.videos.size() + " videos • tap to watch, ⬇ to download");
                    adapter.setItems(ch.videos);
                    nextPage = ch.nextPage;
                    sticky = api.lastGoodBackend();
                });
            }
            @Override public void fail(String error) {
                main.post(() -> {
                    loading = false;
                    progress.setVisibility(ProgressBar.GONE);
                    status.setText("Couldn't load channel: " + error);
                });
            }
        });
    }

    private void loadMore() {
        if (nextPage == null) return;
        String np = nextPage;
        loading = true;
        api.run(() -> api.channelVideos(channelId, np, sticky), new Api.Cb<Models.Page>() {
            @Override public void ok(Models.Page p) {
                main.post(() -> { loading = false; adapter.addItems(p.items); nextPage = p.nextPage; });
            }
            @Override public void fail(String error) {
                main.post(() -> { loading = false; nextPage = null; });
            }
        });
    }
}
