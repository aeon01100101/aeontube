package com.aeontube.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private Api api;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        api = Api.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout body = Ui.column(this);
        Ui.pad(body, this, 14, 12, 14, 16);

        LinearLayout top = Ui.row(this);
        Button back = Ui.button(this, "← Back");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView h = Ui.text(this, "  Settings", 22, Ui.WHITE, true);
        top.addView(h);
        body.addView(top);

        // ---- My Channel ----
        body.addView(section("📺 My Channel"));
        TextView current = Ui.text(this, currentChannelText(), 14, Ui.GREY, false);
        body.addView(current);
        body.addView(help("Paste your channel's @handle, its full URL, or its UC… id. "
                + "AeonTube resolves it once, then the My Channel tab lists your uploads "
                + "with a download button on every video."));
        EditText channelInput = input("e.g. @YourHandle");
        body.addView(channelInput);
        Button saveChannel = Ui.button(this, "Resolve & Save My Channel");
        saveChannel.setOnClickListener(v -> {
            String raw = channelInput.getText().toString().trim();
            if (raw.isEmpty()) { toast("Paste your @handle first"); return; }
            saveChannel.setEnabled(false);
            saveChannel.setText("Resolving…");
            api.run(() -> {
                String id = api.resolveChannel(raw);
                String name = "";
                try {
                    Models.ChannelInfo ch = api.channel(id);
                    name = ch.name;
                } catch (Exception ignored) {}
                return new String[]{id, name};
            }, new Api.Cb<String[]>() {
                @Override public void ok(String[] r) {
                    main.post(() -> {
                        prefs.setMyChannel(r[0], r[1]);
                        saveChannel.setEnabled(true);
                        saveChannel.setText("Resolve & Save My Channel");
                        current.setText(currentChannelText());
                        toast("Saved! Your channel is on the My Channel tab.");
                    });
                }
                @Override public void fail(String error) {
                    main.post(() -> {
                        saveChannel.setEnabled(true);
                        saveChannel.setText("Resolve & Save My Channel");
                        toast("Couldn't resolve that: " + error);
                    });
                }
            });
        });
        body.addView(saveChannel);
        Button clearChannel = Ui.button(this, "Clear My Channel");
        clearChannel.setOnClickListener(v -> {
            prefs.setMyChannel("", "");
            current.setText(currentChannelText());
            toast("Cleared");
        });
        body.addView(clearChannel);

        // ---- Playback ----
        body.addView(section("⏩ SponsorBlock"));
        CheckBox sb = new CheckBox(this);
        sb.setText("Auto-skip sponsor / intro / self-promo segments");
        sb.setTextColor(Ui.WHITE);
        sb.setChecked(prefs.sponsorBlock());
        sb.setOnCheckedChangeListener((b, checked) -> prefs.setSponsorBlock(checked));
        body.addView(sb);
        body.addView(help("Uses the community SponsorBlock database. Ads never exist in AeonTube "
                + "streams at all — this skips the ads baked into the video itself."));

        // ---- Servers ----
        body.addView(section("🌐 Servers (advanced)"));
        body.addView(help("AeonTube talks to public Piped / Invidious servers and hops to the next "
                + "one automatically when one is down. Current server: "
                + (api.lastGoodName().isEmpty() ? "(none yet)" : api.lastGoodName())
                + ". You can pin your own below (optional)."));
        body.addView(help("Custom Piped API URL:"));
        EditText pipedInput = input("https://pipedapi.example.com");
        pipedInput.setText(prefs.customPiped());
        body.addView(pipedInput);
        body.addView(help("Custom Invidious URL:"));
        EditText invInput = input("https://invidious.example.com");
        invInput.setText(prefs.customInvidious());
        body.addView(invInput);
        Button saveServers = Ui.button(this, "Save Servers");
        saveServers.setOnClickListener(v -> {
            prefs.setCustomPiped(pipedInput.getText().toString());
            prefs.setCustomInvidious(invInput.getText().toString());
            Api.reset();
            api = Api.get(this);
            toast("Saved — new server list is live");
        });
        body.addView(saveServers);

        // ---- About ----
        body.addView(section("ℹ️ How the ad blocking works"));
        body.addView(help("AeonTube never loads the official YouTube player — the thing that "
                + "serves pre-roll, mid-roll and banner ads. It fetches the plain video streams "
                + "through open-source Piped/Invidious frontends and plays them directly. "
                + "No Google account, no sign-in, no watch-history tracking. "
                + "Downloads land in Downloads/AeonTube on your phone."));

        scroll.addView(body);
        setContentView(scroll);
    }

    private String currentChannelText() {
        String id = prefs.myChannelId();
        if (id.isEmpty()) return "Not set yet.";
        String name = prefs.myChannelName();
        return "Saved: " + (name.isEmpty() ? id : name + "  (" + id + ")");
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s, 18, Ui.WHITE, true);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        return t;
    }

    private TextView help(String s) {
        TextView t = Ui.text(this, s, 13, Ui.GREY, false);
        t.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 6));
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(Ui.WHITE);
        e.setHintTextColor(Ui.GREY);
        e.setSingleLine(true);
        return e;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
