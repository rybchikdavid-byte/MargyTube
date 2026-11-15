package com.wexresprise.margytube;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real, public YouTube ids so the embedded player and thumbnails actually work
 * before an API key is added. Labels stay honest: every card carries ДЕМО.
 */
public final class Demo {
    private Demo() {
    }

    public static List<Video> videos() {
        List<Video> list = new ArrayList<>();
        list.add(video("dQw4w9WgXcQ", "Rick Astley – Never Gonna Give You Up",
                "Rick Astley", "2009-10-25T00:00:00Z", "3:33", 1_600_000_000L));
        list.add(video("9bZkp7q19f0", "PSY – GANGNAM STYLE (강남스타일)",
                "officialpsy", "2012-07-15T00:00:00Z", "4:13", 5_100_000_000L));
        list.add(video("kJQP7kiw5Fk", "Luis Fonsi – Despacito ft. Daddy Yankee",
                "Luis Fonsi", "2017-01-12T00:00:00Z", "4:42", 8_500_000_000L));
        list.add(video("JGwWNGJdvx8", "Ed Sheeran – Shape of You",
                "Ed Sheeran", "2017-01-30T00:00:00Z", "4:24", 6_300_000_000L));
        list.add(video("RgKAFK5djSk", "Wiz Khalifa – See You Again ft. Charlie Puth",
                "Wiz Khalifa", "2015-04-06T00:00:00Z", "3:58", 6_500_000_000L));
        list.add(video("OPf0YbXqDm0", "Mark Ronson – Uptown Funk ft. Bruno Mars",
                "Mark Ronson", "2014-11-19T00:00:00Z", "4:31", 5_300_000_000L));
        list.add(video("fJ9rUzIMcZQ", "Queen – Bohemian Rhapsody (Official Video)",
                "Queen Official", "2008-08-01T00:00:00Z", "5:59", 1_900_000_000L));
        list.add(video("CevxZvSJLk8", "Katy Perry – Roar (Official)",
                "Katy Perry", "2013-09-05T00:00:00Z", "4:30", 4_200_000_000L));
        list.add(video("hT_nvWreIhg", "OneRepublic – Counting Stars",
                "OneRepublic", "2013-05-31T00:00:00Z", "4:44", 4_100_000_000L));
        list.add(video("YQHsXMglC9A", "Adele – Hello (Official Music Video)",
                "Adele", "2015-10-23T00:00:00Z", "6:07", 3_400_000_000L));
        for (Video v : list) {
            v.demo = true;
            v.likes = Math.max(1, v.views / 22);
            v.comments = Math.max(1, v.views / 900);
            v.description = "Демонстрационное видео MargyTube. Плеер и превью настоящие, "
                    + "а статистика — пример. Подключи ключ YouTube Data API в настройках, "
                    + "чтобы видеть реальные тренды, описание и комментарии.";
        }
        return list;
    }

    private static Video video(String id, String title, String channel,
                               String published, String duration, long views) {
        Video v = new Video();
        v.id = id;
        v.title = title;
        v.channel = channel;
        v.publishedAt = published;
        v.duration = duration;
        v.views = views;
        v.thumbnail = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        return v;
    }

    public static List<Video> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Video> out = new ArrayList<>();
        if (q.isEmpty()) {
            return out;
        }
        for (Video v : videos()) {
            if (v.title.toLowerCase(Locale.ROOT).contains(q)
                    || v.channel.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(v);
            }
        }
        return out;
    }

    public static List<Comment> comments() {
        List<Comment> list = new ArrayList<>();
        list.add(new Comment("Алексей", "Классика. Так выглядит демо-режим MargyTube.",
                OffsetDateTime.now().minusDays(2).toString(), 14, true));
        list.add(new Comment("Мария", "Комментарии читаются через официальный YouTube Data API.",
                OffsetDateTime.now().minusDays(5).toString(), 6, true));
        list.add(new Comment("Иван", "Подключи ключ API и OAuth-токен в настройках — "
                + "и здесь появятся настоящие комментарии.",
                OffsetDateTime.now().minusWeeks(3).toString(), 2, true));
        return list;
    }
}
