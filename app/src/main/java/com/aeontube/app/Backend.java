package com.aeontube.app;

/**
 * A public, keyless, ad-free YouTube frontend backend (Piped or Invidious).
 * Streams come straight from the backend's proxied googlevideo URLs —
 * there are no ad break URLs in them, ever. That's the ad blocking:
 * the official player's ad stack is simply never in the picture.
 */
public interface Backend {
    String name();

    Models.Page trending(String region) throws Exception;

    Models.Page search(String query, String nextPage) throws Exception;

    Models.StreamInfo streams(String videoId) throws Exception;

    /** Channel info including the first page of its videos. */
    Models.ChannelInfo channel(String channelId) throws Exception;

    /** Next page of a channel's videos (token from ChannelInfo.nextPage / Page.nextPage). */
    Models.Page channelVideos(String channelId, String nextPage) throws Exception;

    /** Turn any youtube channel URL / @handle URL into a UC id. May throw if unsupported. */
    String resolveChannelUrl(String url) throws Exception;
}
