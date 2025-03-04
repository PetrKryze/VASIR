package com.petrkryze.vas.fragments;

import static com.petrkryze.vas.MainActivity.applyTintFilter;
import static com.petrkryze.vas.MainActivity.html;
import static com.petrkryze.vas.RatingManager.GET_SESSION_INFO_LOAD_RESULT_KEY;
import static com.petrkryze.vas.RatingManager.SESSION_INFO_LOADED_SESSION;
import static com.petrkryze.vas.fragments.VASFragment.getSerializable;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.Session;

import java.util.ArrayList;

/**
 * Created by Petr on 04.08.2021. Yay!
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    // private static final String TAG = "SettingsFragment";

    private Context context;
    private AlertDialog dialog;

    public SettingsFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("unused")
    public static SettingsFragment newInstance() {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = requireContext();
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setupMenu();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        SwitchPreferenceCompat welcomeScreenShowPreference = findPreference(getString(R.string.SETTING_KEY_SHOW_WELCOME_SCREEN));
        if (welcomeScreenShowPreference != null) {
            welcomeScreenShowPreference.setIcon(tintIcon(welcomeScreenShowPreference.getIcon()));
        }

        EditTextPreference backupNumberPreference = findPreference(getString(R.string.SETTING_KEY_BACKUPS_NUMBER));
        if (backupNumberPreference != null) {
            backupNumberPreference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setFilters(new InputFilter[]{new InputFilterMinMax(0, 100)});
            });
            backupNumberPreference.setSummaryProvider(preference ->
                    getString(R.string.setting_number_of_backups_summary, backupNumberPreference.getText()));
            backupNumberPreference.setIcon(tintIcon(backupNumberPreference.getIcon()));
        }

        SwitchPreferenceCompat requireHeadphonesPreference = findPreference(getString(R.string.SETTING_KEY_REQUIRE_HEADPHONES));
        if (requireHeadphonesPreference != null) {
            requireHeadphonesPreference.setIcon(tintIcon(requireHeadphonesPreference.getIcon()));
        }

        Preference versionInfoPreference = findPreference(getString(R.string.SETTING_KEY_VERSION_INFO));
        if (versionInfoPreference != null) {
            versionInfoPreference.setCopyingEnabled(true);
            versionInfoPreference.setSummaryProvider(preference -> {
                try {
                    PackageInfo packageInfo;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API Level 33
                        packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
                    } else { // API Level < 33
                        packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    }
                    return getString(R.string.setting_version_info_summary, packageInfo.versionName);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e("SettingsFragment","Unknown version of Android.",e);
                    return getString(R.string.setting_version_info_unknown_version);
                }
            });
            versionInfoPreference.setIcon(tintIcon(versionInfoPreference.getIcon()));

            versionInfoPreference.setOnPreferenceClickListener(preference -> {
                String versionInfoString = (String) preference.getSummary();
                if (versionInfoString != null) {
                    return showVersionInfoDialog(versionInfoString);
                } else {
                    return false;
                }
            });
        }

        Preference feedbackPreference = findPreference(getString(R.string.SETTING_KEY_FEEDBACK));
        if (feedbackPreference != null) {
            feedbackPreference.setIntent(getEmailIntent());
            feedbackPreference.setIcon(tintIcon(feedbackPreference.getIcon()));
        }

        EditTextPreference ratingTitleLabelPreference = findPreference(getString(R.string.SETTING_KEY_RATING_TITLE_LABEL));
        if (ratingTitleLabelPreference != null) {
            ratingTitleLabelPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue.toString().isEmpty()) {
                    ratingTitleLabelPreference.setText(getString(R.string.rating_title_label_default));
                    return false;
                }
                return true;
            });
            ratingTitleLabelPreference.setIcon(tintIcon(ratingTitleLabelPreference.getIcon()));
        }

        EditTextPreference ratingLowestLabelPreference = findPreference(getString(R.string.SETTING_KEY_RATING_LOWEST_LABEL));
        if (ratingLowestLabelPreference != null) {
            ratingLowestLabelPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue.toString().isEmpty()) {
                    ratingLowestLabelPreference.setText(getString(R.string.rating_lowest_label_default));
                    return false;
                }
                return true;
            });
            ratingLowestLabelPreference.setIcon(tintIcon(ratingLowestLabelPreference.getIcon()));
        }

        EditTextPreference ratingHighestLabelPreference = findPreference(getString(R.string.SETTING_KEY_RATING_HIGHEST_LABEL));
        if (ratingHighestLabelPreference != null) {
            ratingHighestLabelPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue.toString().isEmpty()) {
                    ratingHighestLabelPreference.setText(getString(R.string.rating_highest_label_default));
                    return false;
                }
                return true;
            });
            ratingHighestLabelPreference.setIcon(tintIcon(ratingHighestLabelPreference.getIcon()));
        }
    }

    private Drawable tintIcon(Drawable icon) {
        applyTintFilter(icon, ContextCompat.getColor(requireContext(), R.color.textPrimaryOnSurface));
        icon.setAlpha(255);
        return icon;
    }

    private Intent getEmailIntent() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.feedback_email_address)});
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_email_subject));
        return intent;
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean showVersionInfoDialog(String versionString) {
        View content = LayoutInflater.from(getContext()).inflate(R.layout.version_info_content,
                null, false);

        // Setting the logo to show a gradient animation on press
        ImageView logo = content.findViewById(R.id.version_info_content_logo);

        ColorDrawable primaryCD = new ColorDrawable(
                ContextCompat.getColor(logo.getContext(),R.color.primaryColor));
        ColorDrawable secondaryCD = new ColorDrawable(
                ContextCompat.getColor(logo.getContext(),R.color.secondaryColor));

        TransitionDrawable transitionDrawable = new TransitionDrawable(
                new Drawable[] {primaryCD,secondaryCD,primaryCD});

        logo.setBackground(transitionDrawable);

        logo.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                transitionDrawable.startTransition(250);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                event.getAction() == MotionEvent.ACTION_CANCEL) {
                transitionDrawable.reverseTransition(250);
                return true;
            }
            return false;
        });

        // Setting the content for text views showing version information
        TextView contentText1 = content.findViewById(R.id.version_info_content_text1);
        TextView contentText2 = content.findViewById(R.id.version_info_content_text2);

        String[] split = versionString.split(" ");
        String versionNumber = split[split.length - 1];
        contentText1.setText(getString(R.string.dialog_version_info_version_number, versionNumber));
        contentText2.setText(getString(R.string.dialog_version_info_subtitle));

        new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .show();
        return true;
    }

    private void setupMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuProvider.super.onPrepareMenu(menu);
                int[] toDisable = {R.id.action_menu_save, R.id.action_menu_settings,
                        R.id.action_menu_new_session};
                int[] toEnable = {R.id.action_menu_help, R.id.action_menu_show_saved_results,
                        R.id.action_menu_quit, R.id.action_menu_show_session_info};

                for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
                for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
            }

            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {}

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int itemID = menuItem.getItemId();
                if (itemID == R.id.action_menu_help) {
                    onShowHelp();
                    return true;
                }
                if (itemID == R.id.action_menu_show_saved_results) {
                    onShowSavedResults();
                    return true;
                } else if (itemID == R.id.action_menu_show_session_info) {
                    onShowSessionInfo();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void onShowHelp() {
        String contextHelpTitle = getString(R.string.help_context_title_settings_fragment);
        String[] contextHelpDescriptions = getResources().getStringArray(R.array.help_tag_description_settings_fragment);
        String contextHelpBody = getString(R.string.help_context_body_settings_fragment);

        NavDirections directions = SettingsFragmentDirections.
                actionActionMenuSettingsToHelpFragment(contextHelpTitle, contextHelpDescriptions,
                        contextHelpBody, R.drawable.help_screen_settings_fragment);
        NavHostFragment.findNavController(this).navigate(directions);
    }

    @SuppressLint("ShowToast")
    private void onShowSavedResults() {
        loadingVisibility(true);
        new Thread(() -> { // Threading for slow loading times
            try {
                ArrayList<RatingResult> ratings = RatingManager.loadResults(requireContext());

                requireActivity().runOnUiThread(() -> {
                    loadingVisibility(false);
                    NavDirections directions =
                            SettingsFragmentDirections.actionActionMenuSettingsToResultFragment(ratings);
                    NavHostFragment.findNavController(this).navigate(directions);
                });
            } catch (Exception e) {
                Log.e("SettingsFragment","Error while attempting to show saved results.",e);

                requireActivity().runOnUiThread(() -> {
                    loadingVisibility(false);
                    Spanned message = html(getString(R.string.snackbar_ratings_loading_failed, e.getMessage()));
                    Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                            message, BaseTransientBottomBar.LENGTH_LONG)
                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
                });
            }
        }, "ResultsLoadingThread").start();
    }

    private void onShowSessionInfo() {
        loadingVisibility(true);

        new Thread(() -> {
            Context context = requireContext();
            Bundle bundle = RatingManager.getSessionInfo(requireActivity());
            String message = "";
            String title = "";
            Drawable icon = null;

            switch (getSerializable(bundle, GET_SESSION_INFO_LOAD_RESULT_KEY, RatingManager.LoadResult.class)) {
                case OK:
                    requireActivity().runOnUiThread(() -> {
                        loadingVisibility(false);
                        Session session = getSerializable(bundle, SESSION_INFO_LOADED_SESSION, Session.class);
                        NavDirections directions =
                                SettingsFragmentDirections
                                        .actionActionMenuSettingsToCurrentSessionInfoFragment(session);
                        NavHostFragment.findNavController(SettingsFragment.this)
                                .navigate(directions);
                    });
                    return;
                case NO_SESSION:
                    title = context.getString(R.string.dialog_no_session_found_title);
                    message = context.getString(R.string.dialog_no_session_found_message);
                    icon = applyTintFilter(
                            ContextCompat.getDrawable(context, R.drawable.ic_info),
                            context.getColor(R.color.secondaryColor));
                    break;
                case CORRUPTED_SESSION:
                    title = context.getString(R.string.dialog_corrupted_session_title);
                    message = context.getString(R.string.dialog_corrupted_session_message);
                    icon = applyTintFilter(
                            ContextCompat.getDrawable(context, R.drawable.ic_error),
                            context.getColor(R.color.errorColor));
                    break;
            }

            String finalTitle = title;
            String finalMessage = message;
            Drawable finalIcon = icon;
            requireActivity().runOnUiThread(() -> dialog = new MaterialAlertDialogBuilder(context)
                    .setTitle(finalTitle).setMessage(finalMessage).setIcon(finalIcon)
                    .setPositiveButton(context.getString(R.string.dialog_quit_confirm), null)
                    .show());
        }, "SessionLoadingThread").start();
    }

    private void loadingVisibility(boolean show) {
        requireActivity().findViewById(R.id.general_loading_container)
                .setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    // Input filter to restrict number of backups input
    static class InputFilterMinMax implements InputFilter {
        private final int min;
        private final int max;

        public InputFilterMinMax(int min, int max) {
            this.min = min;
            this.max = max;
        }

        private boolean isInRange(int a, int b, int c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            try {
                // Removes string that is to be replaced from destination
                // and adds the new string in.
                String newVal = dest.subSequence(0, dstart)
                        // Note that below "toString()" is the only required:
                        + source.subSequence(start, end).toString()
                        + dest.subSequence(dend, dest.length());
                int input = Integer.parseInt(newVal);
                if (isInRange(min, max, input))
                    return null;
            } catch (NumberFormatException ignored) { }
            return "";
        }
    }

}
