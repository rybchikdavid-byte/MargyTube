package com.wexresprise.margytube;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends BaseActivity {
    private ChipGroup accentGroup;
    private ChipGroup themeGroup;
    private ChipGroup cardGroup;
    private Slider radiusSlider;
    private Slider fontSlider;
    private TextView radiusValue;
    private TextView fontValue;
    private TextInputEditText apiKeyInput;
    private TextInputEditText tokenInput;
    private MaterialButton apiTestButton;
    private MaterialButton tokenTestButton;
    private TextView apiResult;
    private TextView tokenResult;
    private MaterialAutoCompleteTextView regionInput;
    private String[] regionNames;
    private String[] regionCodes;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        applyInsets(findViewById(R.id.root), toolbar,
                findViewById(R.id.settingsScroll), true);

        accentGroup = findViewById(R.id.chipGroupAccent);
        themeGroup = findViewById(R.id.chipGroupTheme);
        cardGroup = findViewById(R.id.chipGroupCard);
        radiusSlider = findViewById(R.id.sliderRadius);
        fontSlider = findViewById(R.id.sliderFont);
        radiusValue = findViewById(R.id.radiusValue);
        fontValue = findViewById(R.id.fontValue);
        apiKeyInput = findViewById(R.id.apiKeyInput);
        tokenInput = findViewById(R.id.tokenInput);
        apiTestButton = findViewById(R.id.apiTestButton);
        tokenTestButton = findViewById(R.id.tokenTestButton);
        apiResult = findViewById(R.id.apiResult);
        tokenResult = findViewById(R.id.tokenResult);
        regionInput = findViewById(R.id.regionInput);

        setupAppearance();
        setupYouTube();
        setupComments();
        setupPlugins();
        setupAbout();
    }

    private void setupAppearance() {
        checkAccent(Prefs.accent(this));
        checkTheme(Prefs.themeMode(this));
        checkCard(Prefs.cardStyle(this));

        int radius = Prefs.radius(this);
        radiusSlider.setValue(radius);
        radiusValue.setText(getString(R.string.radius_value, radius));
        int font = Prefs.fontScale(this);
        fontSlider.setValue(font);
        fontValue.setText(getString(R.string.font_value, font));

        accentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            String accent = accentFor(checkedId);
            if (!accent.equals(Prefs.accent(this))) {
                Prefs.setAccent(this, accent);
                recreate();
            }
        });

        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            int mode = themeFor(checkedId);
            if (mode != Prefs.themeMode(this)) {
                Prefs.setThemeMode(this, mode);
            }
        });

        cardGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            int style = cardFor(checkedId);
            if (style != Prefs.cardStyle(this)) {
                Prefs.setCardStyle(this, style);
            }
        });

        radiusSlider.addOnChangeListener((slider, value, fromUser) ->
                radiusValue.setText(getString(R.string.radius_value, (int) value)));
        radiusSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                int value = (int) slider.getValue();
                if (value != Prefs.radius(this)) {
                    Prefs.setRadius(this, value);
                }
            }
        });

        fontSlider.addOnChangeListener((slider, value, fromUser) ->
                fontValue.setText(getString(R.string.font_value, (int) value)));
        fontSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                int value = (int) slider.getValue();
                if (value != Prefs.fontScale(this)) {
                    Prefs.setFontScale(this, value);
                    recreate();
                }
            }
        });
    }

    private void setupYouTube() {
        apiKeyInput.setText(Prefs.apiKey(this));

        regionNames = getResources().getStringArray(R.array.region_names);
        regionCodes = getResources().getStringArray(R.array.region_codes);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, regionNames);
        regionInput.setAdapter(adapter);
        String currentRegion = Prefs.region(this);
        for (int i = 0; i < regionCodes.length; i++) {
            if (regionCodes[i].equals(currentRegion)) {
                regionInput.setText(regionNames[i], false);
                break;
            }
        }
        regionInput.setOnItemClickListener((parent, view, position, id) ->
                Prefs.setRegion(this, regionCodes[position]));

        findViewById(R.id.saveButton).setOnClickListener(v -> {
            saveCredentials();
            snackbar(getString(R.string.settings_saved));
        });

        apiTestButton.setOnClickListener(v -> {
            String key = textOf(apiKeyInput);
            apiTestButton.setEnabled(false);
            showResult(apiResult, getString(R.string.testing));
            YouTubeApi.testKey(this, key, new YouTubeApi.Cb<Integer>() {
                @Override
                public void ok(Integer count) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    apiTestButton.setEnabled(true);
                    showResult(apiResult, getString(R.string.api_ok, count));
                }

                @Override
                public void error(String message) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    apiTestButton.setEnabled(true);
                    showResult(apiResult, getString(R.string.api_bad, message));
                }
            });
        });
    }

    private void setupComments() {
        tokenInput.setText(Prefs.token(this));
        tokenTestButton.setOnClickListener(v -> {
            String token = textOf(tokenInput);
            tokenTestButton.setEnabled(false);
            showResult(tokenResult, getString(R.string.testing));
            YouTubeApi.testToken(this, token, new YouTubeApi.Cb<String>() {
                @Override
                public void ok(String channelTitle) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    tokenTestButton.setEnabled(true);
                    if (channelTitle == null || channelTitle.isEmpty()) {
                        showResult(tokenResult, getString(R.string.token_ok_empty));
                    } else {
                        showResult(tokenResult, getString(R.string.token_ok, channelTitle));
                    }
                }

                @Override
                public void error(String message) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    tokenTestButton.setEnabled(true);
                    showResult(tokenResult, getString(R.string.token_bad, message));
                }
            });
        });
    }

    private void setupPlugins() {
        MaterialButton open = findViewById(R.id.pluginsButton);
        open.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("tab", 2);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
    }

    private void setupAbout() {
        TextView version = findViewById(R.id.versionText);
        version.setText(getString(R.string.version, BuildConfig.VERSION_NAME));
        TextView source = findViewById(R.id.sourceLink);
        source.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/rybchikdavid-byte/MargyTube")));
            } catch (ActivityNotFoundException e) {
                snackbar(getString(R.string.err_network));
            }
        });
    }

    private void saveCredentials() {
        String key = textOf(apiKeyInput);
        String token = textOf(tokenInput);
        if (!key.equals(Prefs.apiKey(this))) {
            Prefs.setApiKey(this, key);
        }
        if (!token.equals(Prefs.token(this))) {
            Prefs.setToken(this, token);
        }
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showResult(TextView view, String text) {
        view.setText(text);
        view.setVisibility(View.VISIBLE);
    }

    private void checkAccent(String accent) {
        int id;
        if (Prefs.ACCENT_GREEN.equals(accent)) {
            id = R.id.chipAccentGreen;
        } else if (Prefs.ACCENT_BLUE.equals(accent)) {
            id = R.id.chipAccentBlue;
        } else if (Prefs.ACCENT_PURPLE.equals(accent)) {
            id = R.id.chipAccentPurple;
        } else if (Prefs.ACCENT_PINK.equals(accent)) {
            id = R.id.chipAccentPink;
        } else if (Prefs.ACCENT_ORANGE.equals(accent)) {
            id = R.id.chipAccentOrange;
        } else if (Prefs.ACCENT_RED.equals(accent)) {
            id = R.id.chipAccentRed;
        } else {
            id = R.id.chipAccentDynamic;
        }
        accentGroup.check(id);
    }

    private static String accentFor(int id) {
        if (id == R.id.chipAccentGreen) {
            return Prefs.ACCENT_GREEN;
        }
        if (id == R.id.chipAccentBlue) {
            return Prefs.ACCENT_BLUE;
        }
        if (id == R.id.chipAccentPurple) {
            return Prefs.ACCENT_PURPLE;
        }
        if (id == R.id.chipAccentPink) {
            return Prefs.ACCENT_PINK;
        }
        if (id == R.id.chipAccentOrange) {
            return Prefs.ACCENT_ORANGE;
        }
        if (id == R.id.chipAccentRed) {
            return Prefs.ACCENT_RED;
        }
        return Prefs.ACCENT_DYNAMIC;
    }

    private void checkTheme(int mode) {
        if (mode == Prefs.THEME_LIGHT) {
            themeGroup.check(R.id.chipThemeLight);
        } else if (mode == Prefs.THEME_DARK) {
            themeGroup.check(R.id.chipThemeDark);
        } else {
            themeGroup.check(R.id.chipThemeSystem);
        }
    }

    private static int themeFor(int id) {
        if (id == R.id.chipThemeLight) {
            return Prefs.THEME_LIGHT;
        }
        if (id == R.id.chipThemeDark) {
            return Prefs.THEME_DARK;
        }
        return Prefs.THEME_SYSTEM;
    }

    private void checkCard(int style) {
        if (style == Prefs.CARD_ELEVATED) {
            cardGroup.check(R.id.chipCardElevated);
        } else if (style == Prefs.CARD_OUTLINED) {
            cardGroup.check(R.id.chipCardOutlined);
        } else {
            cardGroup.check(R.id.chipCardFilled);
        }
    }

    private static int cardFor(int id) {
        if (id == R.id.chipCardElevated) {
            return Prefs.CARD_ELEVATED;
        }
        if (id == R.id.chipCardOutlined) {
            return Prefs.CARD_OUTLINED;
        }
        return Prefs.CARD_FILLED;
    }

    @Override
    protected void onPause() {
        saveCredentials();
        super.onPause();
    }

    private void snackbar(String text) {
        Snackbar.make(findViewById(R.id.root), text, Snackbar.LENGTH_SHORT).show();
    }
}
