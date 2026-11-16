package com.wexresprise.margytube;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.wexresprise.margytube.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays the video with Google's Media3 player (ExoPlayer), fed by the streams
 * YouTube's own player endpoint hands out. The IFrame page is gone: this is a
 * real native player with the system's hardware decoding.
 */
public class PlayerActivity extends BaseActivity {
    private View root;
    private PlayerView playerView;
    private MaterialToolbar toolbar;
    private ProgressBar progress;
    private TextView title;
    private TextView meta;
    private TextView channel;
    private MaterialButton likeButton;
    private MaterialButton sendButton;
    private TextView descriptionText;
    private MaterialButton descriptionToggle;
    private View pluginCard;
    private TextView pluginNotes;
    private RecyclerView commentsList;
    private TextView commentsCount;
    private TextView commentsStatus;
    private MaterialButton commentsRetry;
    private TextInputEditText commentInput;
    private View commentBar;

    private CommentAdapter commentAdapter;
    private Video video;
    private ExoPlayer player;
    private InnerTube.Playback playback;
    private boolean usingMerged;
    private boolean descriptionExpanded;

    public static void open(Context context, Video video) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("video", video);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        root = findViewById(R.id.playerRoot);
        playerView = findViewById(R.id.playerView);
        toolbar = findViewById(R.id.toolbar);
        progress = findViewById(R.id.playerProgress);
        title = findViewById(R.id.videoTitle);
        meta = findViewById(R.id.videoMeta);
        channel = findViewById(R.id.videoChannel);
        likeButton = findViewById(R.id.likeButton);
        sendButton = findViewById(R.id.sendButton);
        descriptionText = findViewById(R.id.descriptionText);
        descriptionToggle = findViewById(R.id.descriptionToggle);
        pluginCard = findViewById(R.id.pluginCard);
        pluginNotes = findViewById(R.id.pluginNotes);
        commentsList = findViewById(R.id.commentsList);
        commentsCount = findViewById(R.id.commentsCount);
        commentsStatus = findViewById(R.id.commentsStatus);
        commentsRetry = findViewById(R.id.commentsRetry);
        commentInput = findViewById(R.id.commentInput);
        commentBar = findViewById(R.id.commentBar);

        applyInsets(root, toolbar, commentBar, true);

        toolbar.setNavigationOnClickListener(v -> finish());

        java.io.Serializable extra = getIntent().getSerializableExtra("video");
        if (!(extra instanceof Video)) {
            finish();
            return;
        }
        video = (Video) extra;

        commentAdapter = new CommentAdapter();
        commentsList.setLayoutManager(new LinearLayoutManager(this));
        commentsList.setAdapter(commentAdapter);

        descriptionToggle.setOnClickListener(v -> toggleDescription());
        likeButton.setOnClickListener(v -> like());
        findViewById(R.id.shareButton).setOnClickListener(v -> share());
        findViewById(R.id.openButton).setOnClickListener(v -> openExternal());
        sendButton.setOnClickListener(v -> sendComment());
        commentsRetry.setOnClickListener(v -> loadComments());

        renderVideo();
        setupPlayer();
        loadPlayback();
        loadComments();
        loadPluginNotes();
    }

    private void setupPlayer() {
        playerView.setKeepScreenOn(true);
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(), true);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    progress.setVisibility(View.GONE);
                } else if (state == Player.STATE_BUFFERING) {
                    progress.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_ENDED) {
                    progress.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (usingMerged && playback != null && !playback.muxedUrl.isEmpty()) {
                    usingMerged = false;
                    player.setMediaSource(progressive(playback.muxedUrl));
                    player.prepare();
                    player.setPlayWhenReady(true);
                    snackbar(getString(R.string.player_fallback));
                    return;
                }
                progress.setVisibility(View.GONE);
                snackbar(getString(R.string.player_failed, error.getErrorCodeName()));
            }
        });
    }

    private void loadPlayback() {
        if (video.id == null || video.id.isEmpty()) {
            progress.setVisibility(View.GONE);
            snackbar(getString(R.string.video_error));
            return;
        }
        InnerTube.playback(this, video.id, new InnerTube.Cb<InnerTube.Playback>() {
            @Override
            public void ok(InnerTube.Playback data) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                playback = data;
                if (data.video != null) {
                    mergeDetails(data.video);
                }
                prepare(data);
            }

            @Override
            public void error(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                snackbar(message);
            }
        });
    }

    private void mergeDetails(Video fresh) {
        if (fresh.title != null && !fresh.title.isEmpty()) {
            video.title = fresh.title;
        }
        if (fresh.channel != null && !fresh.channel.isEmpty()) {
            video.channel = fresh.channel;
        }
        if (fresh.channelId != null && !fresh.channelId.isEmpty()) {
            video.channelId = fresh.channelId;
        }
        if (fresh.description != null && !fresh.description.isEmpty()) {
            video.description = fresh.description;
        }
        if (!fresh.publishedAt.isEmpty()) {
            video.publishedAt = fresh.publishedAt;
        }
        if (fresh.views >= 0) {
            video.views = fresh.views;
        }
        if (!fresh.duration.isEmpty()) {
            video.duration = fresh.duration;
        }
        if (!fresh.thumbnail.isEmpty()) {
            video.thumbnail = fresh.thumbnail;
        }
        renderVideo();
    }

    private void prepare(InnerTube.Playback data) {
        MediaSource source = null;
        if (data.canMerge()) {
            source = merge(data.videoUrl, data.audioUrl);
            usingMerged = true;
        }
        if (source == null && !data.muxedUrl.isEmpty()) {
            source = progressive(data.muxedUrl);
            usingMerged = false;
        }
        if (source == null) {
            progress.setVisibility(View.GONE);
            snackbar(getString(R.string.player_no_stream));
            return;
        }
        player.setMediaSource(source);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private MediaSource progressive(String url) {
        DefaultDataSource.Factory factory = new DefaultDataSource.Factory(this);
        return new ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(url));
    }

    private MediaSource merge(String videoUrl, String audioUrl) {
        DefaultDataSource.Factory factory = new DefaultDataSource.Factory(this);
        ProgressiveMediaSource.Factory sourceFactory = new ProgressiveMediaSource.Factory(factory);
        MediaSource videoSource = sourceFactory.createMediaSource(MediaItem.fromUri(videoUrl));
        MediaSource audioSource = sourceFactory.createMediaSource(MediaItem.fromUri(audioUrl));
        return new MergingMediaSource(videoSource, audioSource);
    }

    private void renderVideo() {
        title.setText(video.title);
        toolbar.setTitle("");

        StringBuilder metaText = new StringBuilder();
        if (video.views >= 0) {
            metaText.append(Formats.views(this, video.views));
        }
        String date = Formats.date(video.publishedAt);
        if (date.isEmpty()) {
            date = Formats.relative(video.publishedAt);
        }
        if (date.isEmpty() && video.publishedText != null) {
            date = video.publishedText;
        }
        if (!date.isEmpty()) {
            if (metaText.length() > 0) {
                metaText.append(" • ");
            }
            metaText.append(date);
        }
        if (!video.duration.isEmpty()) {
            if (metaText.length() > 0) {
                metaText.append(" • ");
            }
            metaText.append(video.duration);
        }
        meta.setText(metaText);
        meta.setVisibility(metaText.length() == 0 ? View.GONE : View.VISIBLE);
        channel.setText(video.channel);

        likeButton.setText(video.likes >= 0
                ? Formats.compact(video.likes) : getString(R.string.like));

        descriptionText.setText(video.description == null || video.description.isEmpty()
                ? getString(R.string.description_empty) : video.description);
        descriptionText.setMaxLines(4);
        descriptionText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        descriptionExpanded = false;
        descriptionToggle.setText(R.string.show_more);
        descriptionToggle.setVisibility(video.description != null
                && video.description.length() > 220 ? View.VISIBLE : View.GONE);
    }

    private void toggleDescription() {
        descriptionExpanded = !descriptionExpanded;
        descriptionText.setMaxLines(descriptionExpanded ? Integer.MAX_VALUE : 4);
        descriptionText.setEllipsize(descriptionExpanded ? null
                : android.text.TextUtils.TruncateAt.END);
        descriptionToggle.setText(descriptionExpanded ? R.string.show_less : R.string.show_more);
    }

    private void loadComments() {
        if (video.id.isEmpty()) {
            return;
        }
        commentsStatus.setVisibility(View.VISIBLE);
        commentsStatus.setText(R.string.comments_loading);
        commentsRetry.setVisibility(View.GONE);

        InnerTube.comments(this, video.id, new InnerTube.Cb<List<Comment>>() {
            @Override
            public void ok(List<Comment> data) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                commentAdapter.submit(data);
                commentsStatus.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                if (data.isEmpty()) {
                    commentsStatus.setText(R.string.comments_empty);
                }
                updateCommentsCount(data.size());
            }

            @Override
            public void error(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                commentAdapter.submit(new ArrayList<>());
                commentsStatus.setVisibility(View.VISIBLE);
                commentsStatus.setText(message);
                commentsRetry.setVisibility(View.VISIBLE);
                updateCommentsCount(0);
            }
        });
    }

    private void updateCommentsCount(int count) {
        commentsCount.setText(count <= 0 ? "" : Formats.comments(this, count));
    }

    private void loadPluginNotes() {
        PluginManager.notesAsync(this, video.id, new PluginManager.NotesCallback() {
            @Override
            public void done(List<String> notes) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (notes == null || notes.isEmpty()) {
                    pluginCard.setVisibility(View.GONE);
                    return;
                }
                StringBuilder builder = new StringBuilder();
                for (String note : notes) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append("• ").append(note);
                }
                pluginNotes.setText(builder);
                pluginCard.setVisibility(View.VISIBLE);
            }
        });
    }

    private void like() {
        if (!Prefs.isSignedIn(this)) {
            showLoginDialog();
            return;
        }
        likeButton.setEnabled(false);
        InnerTube.rateLike(this, video.id, new InnerTube.Cb<Void>() {
            @Override
            public void ok(Void data) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                likeButton.setEnabled(true);
                video.likes = Math.max(0, video.likes) + 1;
                likeButton.setText(Formats.compact(video.likes));
                snackbar(getString(R.string.like_done));
            }

            @Override
            public void error(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                likeButton.setEnabled(true);
                snackbar(message);
            }
        });
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, video.shortUrl());
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    private void openExternal() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(video.url())));
        } catch (ActivityNotFoundException e) {
            snackbar(getString(R.string.video_error));
        }
    }

    private void sendComment() {
        String text = commentInput.getText() == null ? ""
                : commentInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (!Prefs.isSignedIn(this)) {
            showLoginDialog();
            return;
        }
        sendButton.setEnabled(false);
        InnerTube.postComment(this, video.id, text, new InnerTube.Cb<Comment>() {
            @Override
            public void ok(Comment comment) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                sendButton.setEnabled(true);
                commentInput.setText("");
                commentAdapter.addFirst(comment);
                commentsStatus.setVisibility(View.GONE);
                updateCommentsCount(commentAdapter.count());
                snackbar(getString(R.string.comment_sent));
            }

            @Override
            public void error(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                sendButton.setEnabled(true);
                snackbar(message);
            }
        });
    }

    private void showLoginDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.need_login_title)
                .setMessage(R.string.need_login_text)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.open_settings, (dialog, which) ->
                        startActivity(new Intent(this, SettingsActivity.class)))
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (playerView != null) {
            playerView.setPlayer(null);
        }
        super.onDestroy();
    }

    private void snackbar(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Snackbar.make(root, text, Snackbar.LENGTH_LONG).show();
    }
}
