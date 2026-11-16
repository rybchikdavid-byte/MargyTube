package com.wexresprise.margytube;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.wexresprise.margytube.plugin.PluginAdapter;
import com.wexresprise.margytube.plugin.PluginInfo;
import com.wexresprise.margytube.plugin.PluginManager;
import com.wexresprise.margytube.ui.Ui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends BaseActivity implements VideoAdapter.Listener {
    private View viewHome;
    private View viewSearch;
    private View viewPlugins;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private MaterialCardView signInBanner;
    private SwipeRefreshLayout homeRefresh;
    private SwipeRefreshLayout searchRefresh;
    private RecyclerView homeList;
    private RecyclerView searchList;
    private RecyclerView pluginsList;
    private VideoAdapter homeAdapter;
    private VideoAdapter searchAdapter;
    private PluginAdapter pluginAdapter;
    private View homeEmpty;
    private TextView homeEmptyTitle;
    private TextView homeEmptyHint;
    private MaterialButton homeRetry;
    private TextView searchEmpty;
    private TextInputEditText searchInput;
    private View pluginsEmpty;

    private int tab;
    private boolean homeLoaded;
    private boolean loadingHome;
    private boolean pluginsDirty;
    private boolean lastSignedIn;
    private String lastRegion = "";

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importPlugin(uri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        tintToolbarMenu();

        applyInsets(findViewById(R.id.root), findViewById(R.id.appbar),
                findViewById(R.id.bottomNav));
        lastRegion = Prefs.region(this);
        lastSignedIn = Prefs.isSignedIn(this);

        if (savedInstanceState != null) {
            tab = savedInstanceState.getInt("tab", 0);
        } else {
            tab = getIntent().getIntExtra("tab", 0);
        }
        if (tab < 0 || tab > 2) {
            tab = 0;
        }

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_search) {
                bottomNav.setSelectedItemId(R.id.tab_search);
                return true;
            }
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_home) {
                showTab(0);
                return true;
            }
            if (id == R.id.tab_search) {
                showTab(1);
                return true;
            }
            if (id == R.id.tab_plugins) {
                showTab(2);
                return true;
            }
            return false;
        });

        bottomNav.setSelectedItemId(tabId(tab));
        showTab(tab);
        loadPlugins();
    }

    private void bindViews() {
        viewHome = findViewById(R.id.viewHome);
        viewSearch = findViewById(R.id.viewSearch);
        viewPlugins = findViewById(R.id.viewPlugins);
        toolbar = findViewById(R.id.toolbar);
        bottomNav = findViewById(R.id.bottomNav);

        signInBanner = findViewById(R.id.demoBanner);
        MaterialButton signInButton = findViewById(R.id.demoBannerButton);
        signInButton.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        homeRefresh = findViewById(R.id.homeRefresh);
        homeList = findViewById(R.id.homeList);
        homeEmpty = findViewById(R.id.homeEmpty);
        homeEmptyTitle = findViewById(R.id.homeEmptyTitle);
        homeEmptyHint = findViewById(R.id.homeEmptyHint);
        homeRetry = findViewById(R.id.homeRetry);

        homeList.setLayoutManager(new LinearLayoutManager(this));
        homeAdapter = new VideoAdapter(this);
        homeList.setAdapter(homeAdapter);
        homeRefresh.setColorSchemeColors(Ui.attrColor(this,
                com.google.android.material.R.attr.colorPrimary));
        homeRefresh.setOnRefreshListener(() -> loadHome(true));
        homeRetry.setOnClickListener(v -> loadHome(true));

        searchInput = findViewById(R.id.searchInput);
        searchRefresh = findViewById(R.id.searchRefresh);
        searchList = findViewById(R.id.searchList);
        searchEmpty = findViewById(R.id.searchEmpty);
        MaterialButton searchButton = findViewById(R.id.searchButton);

        searchList.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new VideoAdapter(this);
        searchList.setAdapter(searchAdapter);
        searchRefresh.setColorSchemeColors(Ui.attrColor(this,
                com.google.android.material.R.attr.colorPrimary));
        searchRefresh.setOnRefreshListener(this::runSearch);
        searchButton.setOnClickListener(v -> runSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });

        pluginsList = findViewById(R.id.pluginsList);
        pluginsEmpty = findViewById(R.id.pluginsEmpty);
        pluginsList.setLayoutManager(new LinearLayoutManager(this));
        pluginAdapter = new PluginAdapter(new PluginAdapter.Listener() {
            @Override
            public void onToggle(PluginInfo plugin, boolean enabled) {
                PluginManager.setEnabled(MainActivity.this, plugin.id, enabled);
                pluginsDirty = true;
                homeLoaded = false;
                loadPlugins();
            }

            @Override
            public void onDelete(PluginInfo plugin) {
                confirmDelete(plugin);
            }
        });
        pluginsList.setAdapter(pluginAdapter);
        findViewById(R.id.importPluginButton).setOnClickListener(v ->
                importLauncher.launch(new String[]{"*/*"}));
        findViewById(R.id.examplePluginButton).setOnClickListener(v -> createExample());
    }

    private void tintToolbarMenu() {
        int color = Ui.attrColor(this, com.google.android.material.R.attr.colorOnSurface);
        for (int i = 0; i < toolbar.getMenu().size(); i++) {
            MenuItem item = toolbar.getMenu().getItem(i);
            Drawable icon = item.getIcon();
            if (icon == null) {
                continue;
            }
            Drawable tinted = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(tinted, color);
            item.setIcon(tinted);
        }
    }

    private static int tabId(int index) {
        if (index == 1) {
            return R.id.tab_search;
        }
        if (index == 2) {
            return R.id.tab_plugins;
        }
        return R.id.tab_home;
    }

    private void showTab(int index) {
        tab = index;
        viewHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        viewSearch.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        viewPlugins.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        hideKeyboard();
        if (index == 0) {
            updateBanner();
            loadHome(false);
        } else if (index == 2) {
            loadPlugins();
        }
    }

    private void updateBanner() {
        signInBanner.setVisibility(Prefs.isSignedIn(this) ? View.GONE : View.VISIBLE);
    }

    private void loadHome(boolean force) {
        if (loadingHome || (!force && homeLoaded)) {
            return;
        }
        updateBanner();
        loadingHome = true;
        homeRefresh.setRefreshing(true);
        InnerTube.feed(this, new InnerTube.Cb<List<Video>>() {
            @Override
            public void ok(List<Video> data) {
                loadingHome = false;
                homeLoaded = true;
                PluginManager.applyFeedAsync(MainActivity.this, data,
                        MainActivity.this::showHomeVideos);
            }

            @Override
            public void error(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                loadingHome = false;
                homeRefresh.setRefreshing(false);
                snackbar(message);
                if (homeAdapter.count() == 0) {
                    showHomeEmpty(getString(R.string.home_error), message, true);
                }
            }
        });
    }

    private void showHomeVideos(List<Video> videos) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        homeAdapter.submit(videos);
        homeRefresh.setRefreshing(false);
        if (videos.isEmpty()) {
            showHomeEmpty(getString(R.string.home_empty),
                    getString(R.string.home_empty_hint), false);
        } else {
            homeEmpty.setVisibility(View.GONE);
        }
    }

    private void showHomeEmpty(String title, String hint, boolean retry) {
        homeEmpty.setVisibility(View.VISIBLE);
        homeEmptyTitle.setText(title);
        homeEmptyHint.setText(hint);
        homeEmptyHint.setVisibility(hint == null || hint.isEmpty() ? View.GONE : View.VISIBLE);
        homeRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void runSearch() {
        String query = searchInput.getText() == null ? ""
                : searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            searchRefresh.setRefreshing(false);
            searchAdapter.submit(new ArrayList<>());
            searchEmpty.setText(R.string.search_start);
            searchEmpty.setVisibility(View.VISIBLE);
            return;
        }
        hideKeyboard();
        searchEmpty.setVisibility(View.GONE);
        searchRefresh.setRefreshing(true);
        InnerTube.search(this, query, new InnerTube.Cb<List<Video>>() {
            @Override
            public void ok(List<Video> data) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                searchRefresh.setRefreshing(false);
                searchAdapter.submit(data);
                if (data.isEmpty()) {
                    searchEmpty.setText(R.string.search_empty);
                    searchEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void error(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                searchRefresh.setRefreshing(false);
                searchEmpty.setText(message);
                searchEmpty.setVisibility(View.VISIBLE);
                snackbar(message);
            }
        });
    }

    private void loadPlugins() {
        List<PluginInfo> plugins = PluginManager.list(this);
        pluginAdapter.submit(plugins);
        pluginsEmpty.setVisibility(plugins.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void importPlugin(Uri uri) {
        snackbar(getString(R.string.plugin_importing));
        new Thread(() -> {
            String error = null;
            String name = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new Exception("Нет доступа к файлу");
                }
                name = PluginManager.install(this, in).name;
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String importedName = name;
            final String failure = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (failure == null) {
                    pluginsDirty = true;
                    homeLoaded = false;
                    loadPlugins();
                    snackbar(getString(R.string.plugin_imported) + ": " + importedName);
                } else {
                    snackbar(getString(R.string.plugin_import_failed) + ": " + failure);
                }
            });
        }, "mgtp-import").start();
    }

    private void createExample() {
        try {
            PluginManager.createExample(this);
            pluginsDirty = true;
            homeLoaded = false;
            loadPlugins();
            snackbar(getString(R.string.plugin_created));
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage();
            snackbar(getString(R.string.plugin_import_failed) + ": " + message);
        }
    }

    private void confirmDelete(PluginInfo plugin) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.plugin_delete_confirm, plugin.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    PluginManager.delete(this, plugin.id);
                    pluginsDirty = true;
                    homeLoaded = false;
                    loadPlugins();
                    snackbar(getString(R.string.plugin_deleted));
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String region = Prefs.region(this);
        boolean signedIn = Prefs.isSignedIn(this);
        if (!Objects.equals(region, lastRegion) || signedIn != lastSignedIn) {
            lastRegion = region;
            lastSignedIn = signedIn;
            homeLoaded = false;
            updateBanner();
            if (tab == 0) {
                loadHome(true);
            }
        }
        if (pluginsDirty) {
            pluginsDirty = false;
            homeLoaded = false;
            if (tab == 0) {
                loadHome(true);
            } else if (tab == 2) {
                loadPlugins();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", tab);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        int wanted = intent.getIntExtra("tab", -1);
        if (wanted >= 0 && wanted <= 2) {
            bottomNav.setSelectedItemId(tabId(wanted));
        }
    }

    @Override
    public void onVideo(Video video) {
        PlayerActivity.open(this, video);
    }

    private void snackbar(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Snackbar.make(findViewById(R.id.root), text, Snackbar.LENGTH_LONG).show();
    }
}
