package com.wexresprise.margytube;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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

public class PlayerActivity extends BaseActivity {
    private FrameLayout root;
    private View playerMain;
    private View playerScroll;
    private MaterialToolbar toolbar;
    private WebView web;
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
    private TextView commentsDemo;
    private MaterialButton commentsRetry;
    private TextInputEditText commentInput;
    private View commentBar;

    private CommentAdapter commentAdapter;
    private Video video;
    private boolean descriptionExpanded;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

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
        playerMain = findViewById(R.id.playerMain);
        playerScroll = findViewById(R.id.playerScroll);
        toolbar = findViewById(R.id.toolbar);
        web = findViewById(R.id.playerWeb);
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
        commentsDemo = findViewById(R.id.commentsDemo);
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
        setupWebView();
        loadPlayer();
        loadDetails();
        loadComments();
        loadPluginNotes();
    }

    private void renderVideo() {
        title.setText(video.title);

        StringBuilder metaText = new StringBuilder();
        if (video.views >= 0) {
            metaText.append(Formats.views(this, video.views));
        }
        String date = Formats.date(video.publishedAt);
        if (date.isEmpty()) {
            date = Formats.relative(video.publishedAt);
        }
        if (!date.isEmpty()) {
            if (metaText.length() > 0) {
                metaText.append(" • ");
            }
            metaText.append(date);
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

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        web.setBackgroundColor(Color.BLACK);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    progress.setVisibility(View.GONE);
                    snackbar(getString(R.string.err_network));
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreen();
            }
        });
    }

    private void loadPlayer() {
        String safeId = video.id == null ? "" : video.id.replaceAll("[^A-Za-z0-9_-]", "");
        if (safeId.isEmpty()) {
            progress.setVisibility(View.GONE);
            snackbar(getString(R.string.video_error));
            return;
        }
        String html = "<!DOCTYPE html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;padding:0;height:100%;background:#000;}"
                + "iframe{width:100%;height:100%;border:0;}</style></head><body>"
                + "<iframe src=\"https://www.youtube.com/embed/" + safeId
                + "?rel=0&playsinline=1&modestbranding=1\" "
                + "allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; "
                + "gyroscope; picture-in-picture; web-share\" allowfullscreen></iframe>"
                + "</body></html>";
        web.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    private void loadDetails() {
        if (!YouTubeApi.hasKey(this) || video.id.isEmpty()) {
            return;
        }
        YouTubeApi.video(this, video.id, new YouTubeApi.Cb<Video>() {
            @Override
            public void ok(Video fresh) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                video.title = fresh.title;
                video.channel = fresh.channel;
                video.description = fresh.description;
                video.publishedAt = fresh.publishedAt;
                video.views = fresh.views;
                video.likes = fresh.likes;
                video.comments = fresh.comments;
                video.duration = fresh.duration;
                renderVideo();
            }

            @Override
            public void error(String message) {
                // The card still works from the feed data; don't shout about it.
            }
        });
    }

    private void loadComments() {
        if (video.id.isEmpty()) {
            return;
        }
        commentsStatus.setVisibility(View.VISIBLE);
        commentsStatus.setText(R.string.comments_loading);
        commentsRetry.setVisibility(View.GONE);
        commentsDemo.setVisibility(View.GONE);

        if (!YouTubeApi.hasKey(this)) {
            List<Comment> demo = Demo.comments();
            commentAdapter.submit(demo);
            commentsStatus.setVisibility(View.GONE);
            commentsDemo.setVisibility(View.VISIBLE);
            updateCommentsCount(demo.size());
            return;
        }

        YouTubeApi.comments(this, video.id, new YouTubeApi.Cb<List<Comment>>() {
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
        if (!YouTubeApi.hasToken(this)) {
            showTokenDialog();
            return;
        }
        likeButton.setEnabled(false);
        YouTubeApi.rateLike(this, video.id, new YouTubeApi.Cb<Void>() {
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
        if (!YouTubeApi.hasToken(this)) {
            showTokenDialog();
            return;
        }
        sendButton.setEnabled(false);
        YouTubeApi.postComment(this, video.id, text, new YouTubeApi.Cb<Comment>() {
            @Override
            public void ok(Comment comment) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                sendButton.setEnabled(true);
                commentInput.setText("");
                commentAdapter.addFirst(comment);
                commentsStatus.setVisibility(View.GONE);
                commentsDemo.setVisibility(View.GONE);
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

    private void showTokenDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.need_token_title)
                .setMessage(R.string.comment_token_text)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.open_settings, (dialog, which) ->
                        startActivity(new Intent(this, SettingsActivity.class)))
                .show();
    }

    private void showFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreenView = view;
        fullscreenCallback = callback;
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toolbar.setVisibility(View.GONE);
        playerScroll.setVisibility(View.GONE);
        commentBar.setVisibility(View.GONE);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void hideFullscreen() {
        if (fullscreenView == null) {
            return;
        }
        root.removeView(fullscreenView);
        fullscreenView = null;
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
        toolbar.setVisibility(View.VISIBLE);
        playerScroll.setVisibility(View.VISIBLE);
        commentBar.setVisibility(View.VISIBLE);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .show(WindowInsetsCompat.Type.systemBars());
        updateSystemBars();
    }

    @Override
    public void onBackPressed() {
        if (fullscreenView != null) {
            hideFullscreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            web.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.removeAllViews();
            web.destroy();
            web = null;
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
