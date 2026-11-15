package com.wexresprise.margytube;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.wexresprise.margytube.ui.Ui;

import java.util.ArrayList;
import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder> {
    public interface Listener {
        void onVideo(Video video);
    }

    private final List<Video> items = new ArrayList<>();
    private final Listener listener;

    public VideoAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Video> videos) {
        items.clear();
        if (videos != null) {
            items.addAll(videos);
        }
        notifyDataSetChanged();
    }

    public int count() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Video video = items.get(position);
        Context context = holder.itemView.getContext();

        holder.title.setText(video.title);
        holder.channel.setText(video.channel);
        holder.avatarLetter.setText(initial(video.channel));

        StringBuilder meta = new StringBuilder();
        if (video.views >= 0) {
            meta.append(Formats.views(context, video.views));
        }
        String relative = Formats.relative(video.publishedAt);
        if (!relative.isEmpty()) {
            if (meta.length() > 0) {
                meta.append(" • ");
            }
            meta.append(relative);
        }
        holder.meta.setText(meta);
        holder.meta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);

        holder.duration.setText(video.duration);
        holder.duration.setVisibility(video.duration == null || video.duration.isEmpty()
                ? View.GONE : View.VISIBLE);
        holder.demoBadge.setVisibility(video.demo ? View.VISIBLE : View.GONE);

        ImageLoader.load(holder.thumbnail, video.thumbnailUrl());
        holder.thumbnail.setContentDescription(video.title);

        applyCardStyle(holder.card, context);

        holder.card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVideo(video);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static void applyCardStyle(MaterialCardView card, Context context) {
        int radius = Prefs.radius(context);
        card.setRadius(Ui.dp(context, radius));
        int style = Prefs.cardStyle(context);
        if (style == Prefs.CARD_ELEVATED) {
            card.setCardElevation(Ui.dp(context, 2));
            card.setStrokeWidth(0);
            card.setCardBackgroundColor(Ui.attrColor(context,
                    com.google.android.material.R.attr.colorSurface));
        } else if (style == Prefs.CARD_OUTLINED) {
            card.setCardElevation(0);
            card.setStrokeWidth(Ui.dp(context, 1));
            card.setStrokeColor(Ui.attrColor(context,
                    com.google.android.material.R.attr.colorOutlineVariant));
            card.setCardBackgroundColor(Ui.attrColor(context,
                    com.google.android.material.R.attr.colorSurface));
        } else {
            card.setCardElevation(0);
            card.setStrokeWidth(0);
            card.setCardBackgroundColor(Ui.attrColor(context,
                    com.google.android.material.R.attr.colorSurfaceVariant));
        }
    }

    private static String initial(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String trimmed = name.trim();
        return new String(Character.toChars(trimmed.codePointAt(0))).toUpperCase();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final com.wexresprise.margytube.ui.AspectImageView thumbnail;
        final TextView duration;
        final TextView demoBadge;
        final TextView avatarLetter;
        final TextView title;
        final TextView channel;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            duration = itemView.findViewById(R.id.duration);
            demoBadge = itemView.findViewById(R.id.demoBadge);
            avatarLetter = itemView.findViewById(R.id.avatarLetter);
            title = itemView.findViewById(R.id.title);
            channel = itemView.findViewById(R.id.channel);
            meta = itemView.findViewById(R.id.meta);
        }
    }
}
