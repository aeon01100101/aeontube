package com.aeontube.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Api api;
    private Prefs prefs;

    private TextView status;
    private TextView serverLabel;
    private ProgressBar progress;
    private ListView list;
    private VideoAdapter adapter;
    private EditText searchBox;
    private Button tabHome;
    private Button tabMine;

    private enum Mode { HOME, SEARCH, MY_CHANNEL }
    private Mode mode = Mode.HOME;
    private String searchQuery = "";
    private String nextPage = null;
    private Backend sticky = null;
    private boolean loading = false;
    private String myChannelNext = null;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = Api.get(this);
        prefs = new Prefs(this);
        buildUi();
        handleDeepLink(getIntent());
        if (adapter.getCount() == 0) loadHome();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        serverLabel.setText("Server: " + (api.lastGoodName().isEmpty() ? "auto (tap ⚙ for options)" : api.lastGoodName()));
        if (mode == Mode.MY_CHANNEL) loadMyChannel(); // picks up a channel saved in Settings
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null) return;
        String vid = ParseUtil.videoIdFrom(data.toString());
        if (vid != null) {
            openPlayer(vid);
            return;
        }
        String cid = ParseUtil.channelIdFromUrl(data.toString());
        if (cid != null) openChannel(cid, "");
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);

        LinearLayout header = Ui.row(this);
        Ui.pad(header, this, 12, 10, 12, 4);
        TextView title = Ui.text(this, "▶ AeonTube", 24, Ui.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        serverLabel = Ui.text(this, "", 11, Ui.GREY, false);
        header.addView(serverLabel);
        Button settings = Ui.button(this, "⚙");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings);
        root.addView(header);

        TextView tagline = Ui.text(this, "No ads. No tracking. Your channel, downloadable.", 12, Ui.GREY, false);
        Ui.pad(tagline, this, 12, 0, 12, 6);
        root.addView(tagline);

        LinearLayout searchRow = Ui.row(this);
        Ui.pad(searchRow, this, 8, 2, 8, 2);
        searchBox = new EditText(this);
        searchBox.setHint("Search YouTube…");
        searchBox.setTextColor(Ui.WHITE);
        searchBox.setHintTextColor(Ui.GREY);
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                doSearch();
                return true;
            }
            return false;
        });
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button go = Ui.button(this, "Search");
        go.setOnClickListener(v -> doSearch());
        searchRow.addView(go);
        root.addView(searchRow);

        LinearLayout tabs = Ui.row(this);
        tabHome = Ui.tabButton(this, "🏠 Home", true);
        tabMine = Ui.tabButton(this, "📺 My Channel", false);
        Button tabDl = Ui.tabButton(this, "⬇ Downloads", false);
        tabHome.setOnClickListener(v -> loadHome());
        tabMine.setOnClickListener(v -> loadMyChannel());
        tabDl.setOnClickListener(v -> {
            try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); }
            catch (Exception e) { Toast.makeText(this, "Downloads are in your Files app → Downloads/AeonTube", Toast.LENGTH_LONG).show(); }
        });
        tabs.addView(tabHome);
        tabs.addView(tabMine);
        tabs.addView(tabDl);
        root.addView(tabs);

        progress = new ProgressBar(this);
        progress.setVisibility(ProgressBar.GONE);
        root.addView(progress);

        status = Ui.text(this, "", 13, Ui.GREY, false);
        Ui.pad(status, this, 12, 6, 12, 6);
        root.addView(status);

        list = new ListView(this);
        list.setBackgroundColor(Ui.BG);
        list.setDividerHeight(Ui.dp(this, 4));
        adapter = new VideoAdapter(this, true);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Models.VideoItem item = adapter.items().get(pos);
            if (item.isChannel) openChannel(item.id, item.title);
            else openPlayer(item.id);
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
    }

    private void setTabs() {
        tabHome.setBackgroundColor(mode == Mode.MY_CHANNEL ? Ui.PANEL : Ui.RED);
        tabHome.setTextColor(mode == Mode.MY_CHANNEL ? Ui.GREY : Ui.WHITE);
        tabMine.setBackgroundColor(mode == Mode.MY_CHANNEL ? Ui.RED : Ui.PANEL);
        tabMine.setTextColor(mode == Mode.MY_CHANNEL ? Ui.WHITE : Ui.GREY);
    }

    private void setLoading(boolean on, String msg) {
        loading = on;
        progress.setVisibility(on ? ProgressBar.VISIBLE : ProgressBar.GONE);
        if (msg != null) status.setText(msg);
    }

    private void loadHome() {
        mode = Mode.HOME;
        setTabs();
        setLoading(true, "Loading trending…");
        api.run(() -> api.trending(), new Api.Cb<Models.Page>() {
            @Override public void ok(Models.Page p) {
                main.post(() -> {
                    setLoading(false, "Trending now • via " + api.lastGoodName());
                    adapter.setItems(p.items);
                    nextPage = null;
                });
            }
            @Override public void fail(String error) {
                main.post(() -> setLoading(false, "Couldn't load: " + error));
            }
        });
    }

    private void doSearch() {
        String q = searchBox.getText().toString().trim();
        if (q.isEmpty()) return;
        // Paste a video link into search = open it directly.
        String vid = ParseUtil.videoIdFrom(q);
        if (vid != null && (q.contains("youtu") || q.length() == 11)) { openPlayer(vid); return; }
        mode = Mode.SEARCH;
        setTabs();
        searchQuery = q;
        nextPage = null;
        sticky = null;
        setLoading(true, "Searching for \"" + q + "\"…");
        api.run(() -> api.search(q, null, null), new Api.Cb<Models.Page>() {
            @Override public void ok(Models.Page p) {
                main.post(() -> {
                    setLoading(false, p.items.size() + " results for \"" + q + "\" • via " + api.lastGoodName());
                    adapter.setItems(p.items);
                    nextPage = p.nextPage;
                    sticky = api.lastGoodBackend();
                });
            }
            @Override public void fail(String error) {
                main.post(() -> setLoading(false, "Search failed: " + error));
            }
        });
    }

    private void loadMyChannel() {
        mode = Mode.MY_CHANNEL;
        setTabs();
        String cid = prefs.myChannelId();
        if (cid.isEmpty()) {
            adapter.setItems(new java.util.ArrayList<>());
            setLoading(false, "You haven't set your channel yet. Tap ⚙ Settings (top right), paste your @handle, and hit Save — then your uploads live here with download buttons.");
            return;
        }
        setLoading(true, "Loading your channel…");
        api.run(() -> api.channel(cid), new Api.Cb<Models.ChannelInfo>() {
            @Override public void ok(Models.ChannelInfo ch) {
                main.post(() -> {
                    String name = ch.name.isEmpty() ? prefs.myChannelName() : ch.name;
                    setLoading(false, "📺 " + name
                            + (ch.subs >= 0 ? " • " + String.format("%,d", ch.subs) + " subscribers" : "")
                            + " • tap a video to watch, ⬇ to keep it");
                    adapter.setItems(ch.videos);
                    myChannelNext = ch.nextPage;
                    sticky = api.lastGoodBackend();
                    if (!ch.name.isEmpty()) prefs.setMyChannel(cid, ch.name);
                });
            }
            @Override public void fail(String error) {
                main.post(() -> setLoading(false, "Couldn't load your channel: " + error));
            }
        });
    }

    private void loadMore() {
        if (mode == Mode.SEARCH && nextPage != null) {
            String np = nextPage;
            setLoading(true, null);
            api.run(() -> api.search(searchQuery, np, sticky), new Api.Cb<Models.Page>() {
                @Override public void ok(Models.Page p) {
                    main.post(() -> { setLoading(false, null); adapter.addItems(p.items); nextPage = p.nextPage; });
                }
                @Override public void fail(String error) {
                    main.post(() -> { setLoading(false, null); nextPage = null; });
                }
            });
        } else if (mode == Mode.MY_CHANNEL && myChannelNext != null) {
            String np = myChannelNext;
            String cid = prefs.myChannelId();
            setLoading(true, null);
            api.run(() -> api.channelVideos(cid, np, sticky), new Api.Cb<Models.Page>() {
                @Override public void ok(Models.Page p) {
                    main.post(() -> { setLoading(false, null); adapter.addItems(p.items); myChannelNext = p.nextPage; });
                }
                @Override public void fail(String error) {
                    main.post(() -> { setLoading(false, null); myChannelNext = null; });
                }
            });
        }
    }

    private void openPlayer(String videoId) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("video_id", videoId);
        startActivity(i);
    }

    private void openChannel(String channelId, String name) {
        Intent i = new Intent(this, ChannelActivity.class);
        i.putExtra("channel_id", channelId);
        i.putExtra("channel_name", name == null ? "" : name);
        startActivity(i);
    }
}
