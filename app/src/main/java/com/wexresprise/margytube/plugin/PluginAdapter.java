package com.wexresprise.margytube.plugin;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.wexresprise.margytube.R;

import java.util.ArrayList;
import java.util.List;

public class PluginAdapter extends RecyclerView.Adapter<PluginAdapter.Holder> {
    public interface Listener {
        void onToggle(PluginInfo plugin, boolean enabled);

        void onDelete(PluginInfo plugin);
    }

    private final List<PluginInfo> items = new ArrayList<>();
    private final Listener listener;

    public PluginAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<PluginInfo> plugins) {
        items.clear();
        if (plugins != null) {
            items.addAll(plugins);
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
                .inflate(R.layout.item_plugin, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PluginInfo plugin = items.get(position);
        Context context = holder.itemView.getContext();

        holder.name.setText(plugin.name);
        StringBuilder meta = new StringBuilder();
        if (!plugin.version.isEmpty()) {
            meta.append("v").append(plugin.version);
        }
        if (!plugin.author.isEmpty()) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append(plugin.author);
        }
        if (!plugin.hooks.isEmpty()) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append(context.getString(R.string.plugin_hooks,
                    TextUtils.join(", ", plugin.hooks)));
        } else if (meta.length() > 0) {
            meta.append(" · ").append(context.getString(R.string.plugin_no_hooks));
        }
        holder.meta.setText(meta);
        holder.meta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);

        if (plugin.error != null && !plugin.error.isEmpty()) {
            holder.error.setVisibility(View.VISIBLE);
            holder.error.setText(context.getString(R.string.plugin_error, plugin.error));
        } else {
            holder.error.setVisibility(View.GONE);
        }

        holder.enable.setOnCheckedChangeListener(null);
        holder.enable.setChecked(plugin.enabled);
        holder.enable.setOnCheckedChangeListener((button, checked) -> {
            if (listener != null) {
                listener.onToggle(plugin, checked);
            }
        });
        holder.delete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(plugin);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final TextView error;
        final MaterialSwitch enable;
        final ImageButton delete;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            meta = itemView.findViewById(R.id.meta);
            error = itemView.findViewById(R.id.error);
            enable = itemView.findViewById(R.id.switchEnable);
            delete = itemView.findViewById(R.id.delete);
        }
    }
}
