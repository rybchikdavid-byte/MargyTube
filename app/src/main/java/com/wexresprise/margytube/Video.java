package com.wexresprise.margytube;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class Video implements Serializable {
    public String id = "";
    public String title = "";
    public String channel = "";
    public String channelId = "";
    public String description = "";
    public String publishedAt = "";
    public String publishedText = "";
    public String duration = "";
    public String thumbnail = "";
    public long views = -1;
    public long likes = -1;
    public long comments = -1;
    public boolean demo;

    public Video() {
    }

    public String thumbnailUrl() {
        if (thumbnail != null && !thumbnail.isEmpty()) {
            return thumbnail;
        }
        return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    }

    public String url() {
        return "https://www.youtube.com/watch?v=" + id;
    }

    public String shortUrl() {
        return "https://youtu.be/" + id;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("channel", channel);
        o.put("channelId", channelId);
        o.put("description", description);
        o.put("published", publishedAt);
        o.put("publishedText", publishedText);
        o.put("duration", duration);
        o.put("thumbnail", thumbnail);
        o.put("views", views);
        o.put("likes", likes);
        o.put("comments", comments);
        return o;
    }

    /** Used after plugins hand a feed back, so only the fields they may touch are trusted. */
    public static Video fromJson(JSONObject o) {
        Video v = new Video();
        v.id = o.optString("id", "");
        v.title = o.optString("title", "");
        v.channel = o.optString("channel", "");
        v.channelId = o.optString("channelId", "");
        v.description = o.optString("description", "");
        v.publishedAt = o.optString("published", o.optString("publishedAt", ""));
        v.publishedText = o.optString("publishedText", "");
        v.duration = o.optString("duration", "");
        v.thumbnail = o.optString("thumbnail", "");
        v.views = o.optLong("views", -1);
        v.likes = o.optLong("likes", -1);
        v.comments = o.optLong("comments", -1);
        return v;
    }
}
