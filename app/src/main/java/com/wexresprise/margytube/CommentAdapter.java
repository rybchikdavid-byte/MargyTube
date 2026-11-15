package com.wexresprise.margytube;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.Holder> {
    private final List<Comment> items = new ArrayList<>();

    public void submit(List<Comment> comments) {
        items.clear();
        if (comments != null) {
            items.addAll(comments);
        }
        notifyDataSetChanged();
    }

    public void addFirst(Comment comment) {
        if (comment == null) {
            return;
        }
        items.add(0, comment);
        notifyItemInserted(0);
    }

    public int count() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Comment comment = items.get(position);
        holder.author.setText(comment.author);
        holder.text.setText(comment.text);
        holder.avatarLetter.setText(comment.initial());
        holder.time.setText(Formats.relative(comment.publishedAt));
        if (comment.likes > 0) {
            holder.likes.setVisibility(View.VISIBLE);
            holder.likes.setText("\uD83D\uDC4D " + Formats.compact(comment.likes));
        } else {
            holder.likes.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView avatarLetter;
        final TextView author;
        final TextView text;
        final TextView time;
        final TextView likes;

        Holder(@NonNull View itemView) {
            super(itemView);
            avatarLetter = itemView.findViewById(R.id.avatarLetter);
            author = itemView.findViewById(R.id.author);
            text = itemView.findViewById(R.id.text);
            time = itemView.findViewById(R.id.time);
            likes = itemView.findViewById(R.id.likes);
        }
    }
}
