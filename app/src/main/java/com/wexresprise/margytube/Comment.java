package com.wexresprise.margytube;

import java.io.Serializable;

public class Comment implements Serializable {
    public String author = "";
    public String text = "";
    public String publishedAt = "";
    public long likes = 0;
    public boolean demo;

    public Comment() {
    }

    public Comment(String author, String text, String publishedAt, long likes, boolean demo) {
        this.author = author;
        this.text = text;
        this.publishedAt = publishedAt;
        this.likes = likes;
        this.demo = demo;
    }

    public String initial() {
        if (author == null || author.trim().isEmpty()) {
            return "?";
        }
        String trimmed = author.trim();
        int codePoint = trimmed.codePointAt(0);
        return new String(Character.toChars(codePoint)).toUpperCase();
    }
}
