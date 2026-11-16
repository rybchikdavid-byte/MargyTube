package com.wexresprise.margytube;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

public class SettingsActivity extends BaseActivity {
    private ChipGroup accentGroup;
    private ChipGroup themeGroup;
    private ChipGroup cardGroup;
    private Slider radiusSlider;
    private Slider fontSlider;
    private TextView radiusValue;
    private TextView fontValue;
    private TextView accountName;
    private TextView accountDetail;
    private ImageView accountAvatar;
    private MaterialButton signInButton;
    private MaterialButton signOutButton;
    private MaterialAutoCompleteTextView regionInput;
    private String[] regionNames;
    private String[] regionCodes;
    private AlertDialog signInDialog;
    private boolean fetchingAccount;

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
        accountName = findViewById(R.id.accountName);
        accountDetail = findViewById(R.id.accountDetail);
        accountAvatar = findViewById(R.id.accountAvatar);
        signInButton = findViewById(R.id.signInButton);
        signOutButton = findViewById(R.id.signOutButton);
        regionInput = findViewById(R.id.regionInput);

        setupAccount();
        setupAppearance();
        setupRegion();
        setupPlugins();
        setupAbout();
    }

    private void setupAccount() {
        updateAccount();
        signInButton.setOnClickListener(v -> startSignIn());
        signOutButton.setOnClickListener(v -> confirmSignOut());
    }

    private void updateAccount() {
        boolean signedIn = Prefs.isSignedIn(this);
        String name = Prefs.accountName(this);
        if (signedIn) {
            accountName.setText(name.isEmpty()
                    ? getString(R.string.account_signed) : name);
            accountDetail.setText(R.string.account_signed_hint);
            signInButton.setVisibility(View.GONE);
            signOutButton.setVisibility(View.VISIBLE);
            String avatar = Prefs.accountAvatar(this);
            if (avatar.isEmpty()) {
                accountAvatar.setVisibility(View.GONE);
            } else {
                accountAvatar.setVisibility(View.VISIBLE);
                ImageLoader.load(accountAvatar, avatar);
            }
        } else {
            accountName.setText(R.string.account_none);
            accountDetail.setText(R.string.account_hint);
            signInButton.setVisibility(View.VISIBLE);
            signOutButton.setVisibility(View.GONE);
            accountAvatar.setVisibility(View.GONE);
            accountAvatar.setImageDrawable(null);
        }
    }

    private void startSignIn() {
        signInButton.setEnabled(false);
        GoogleAuth.start(this, new InnerTube.Cb<GoogleAuth.Device>() {
            @Override
            public void ok(GoogleAuth.Device device) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                signInButton.setEnabled(true);
                showCodeDialog(device);
            }

            @Override
            public void error(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                signInButton.setEnabled(true);
                snackbar(message);
            }
        });
    }

    private void showCodeDialog(GoogleAuth.Device device) {
        if (signInDialog != null && signInDialog.isShowing()) {
            signInDialog.dismiss();
        }
        String message = getString(R.string.sign_in_code_text, device.userCode)
                + "\n\n" + getString(R.string.sign_in_waiting);
        signInDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sign_in_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.open_site, (dialog, which) ->
                        openSite(device.verificationUrl))
                .setNeutralButton(R.string.copy_code, (dialog, which) ->
                        copyCode(device.userCode))
                .setNegativeButton(R.string.cancel, (dialog, which) ->
                        GoogleAuth.cancel())
                .show();

        GoogleAuth.poll(this, device, new InnerTube.Cb<String>() {
            @Override
            public void ok(String name) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (signInDialog != null && signInDialog.isShowing()) {
                    signInDialog.dismiss();
                }
                updateAccount();
                snackbar(name == null || name.isEmpty()
                        ? getString(R.string.sign_in_done_empty)
                        : getString(R.string.sign_in_done, name));
            }

            @Override
            public void error(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateAccount();
                snackbar(message);
            }
        });
    }

    private void openSite(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            snackbar(getString(R.string.err_network));
        }
    }

    private void copyCode(String code) {
        ClipboardManager manager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("Google", code));
            snackbar(getString(R.string.code_copied));
        }
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.sign_out_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sign_out, (dialog, which) -> {
                    GoogleAuth.cancel();
                    Prefs.signOut(this);
                    updateAccount();
                    snackbar(getString(R.string.signed_out));
                })
                .show();
    }

    private void refreshAccountName() {
        if (fetchingAccount || !Prefs.isSignedIn(this)
                || !Prefs.accountName(this).isEmpty()) {
            return;
        }
        fetchingAccount = true;
        GoogleAuth.token(this, new InnerTube.Cb<String>() {
            @Override
            public void ok(String token) {
                InnerTube.fetchAccount(SettingsActivity.this, token,
                        new InnerTube.Cb<String>() {
                            @Override
                            public void ok(String name) {
                                fetchingAccount = false;
                                if (!isFinishing() && !isDestroyed()) {
                                    updateAccount();
                                }
                            }

                            @Override
                            public void error(String message) {
                                fetchingAccount = false;
                            }
                        });
            }

            @Override
            public void error(String message) {
                fetchingAccount = false;
            }
        });
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
            Prefs.setThemeMode(this, themeFor(checkedId));
        });

        cardGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            Prefs.setCardStyle(this, cardFor(checkedId));
        });

        radiusSlider.addOnChangeListener((slider, value, fromUser) ->
                radiusValue.setText(getString(R.string.radius_value, (int) value)));
        radiusSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                Prefs.setRadius(SettingsActivity.this, (int) slider.getValue());
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
                if (value != Prefs.fontScale(SettingsActivity.this)) {
                    Prefs.setFontScale(SettingsActivity.this, value);
                    recreate();
                }
            }
        });
    }

    private void setupRegion() {
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
    protected void onResume() {
        super.onResume();
        updateAccount();
        refreshAccountName();
    }

    @Override
    protected void onDestroy() {
        GoogleAuth.cancel();
        super.onDestroy();
    }

    private void snackbar(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Snackbar.make(findViewById(R.id.root), text, Snackbar.LENGTH_SHORT).show();
    }
}
