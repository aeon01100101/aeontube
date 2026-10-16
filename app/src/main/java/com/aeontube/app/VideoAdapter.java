package com.aeontube.app;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class VideoAdapter extends BaseAdapter {
    private final Activity activity;
    private final List<Models.VideoItem> items = new ArrayList<>();
    private final boolean showDownloads;

    public VideoAdapter(Activity activity, boolean showDownloads) {
        this.activity = activity;
        this.showDownloads = showDownloads;
    }

    public void setItems(List<Models.VideoItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void addItems(List<Models.VideoItem> list) {
        items.addAll(list);
        notifyDataSetChanged();
    }

    public List<Models.VideoItem> items() { return items; }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int pos) { return items.get(pos); }
    @Override public long getItemId(int pos) { return pos; }

    @Override public View getView(int pos, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(activity).inflate(R.layout.item_video, parent, false);
        }
        Models.VideoItem item = items.get(pos);
        ImageView thumb = v.findViewById(R.id.thumb);
        TextView title = v.findViewById(R.id.title);
        TextView meta = v.findViewById(R.id.meta);
        TextView duration = v.findViewById(R.id.duration);
        Button dl = v.findViewById(R.id.btn_download);
        Button au = v.findViewById(R.id.btn_audio);

        Thumbs.load(thumb, item.thumbnail);
        title.setText(item.title);
        meta.setText(item.isChannel ? "Channel — tap to open" : item.metaLine());
        duration.setText(item.durationText());
        duration.setVisibility(item.durationSec >= 0 ? View.VISIBLE : View.GONE);

        int vis = showDownloads && !item.isChannel ? View.VISIBLE : View.GONE;
        dl.setVisibility(vis);
        au.setVisibility(vis);
        dl.setFocusable(false);
        au.setFocusable(false);
        dl.setOnClickListener(x -> Downloads.downloadVideo(activity, item, false));
        au.setOnClickListener(x -> Downloads.downloadVideo(activity, item, true));
        return v;
    }
}
