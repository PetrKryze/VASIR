package com.petrkryze.vas.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import static com.petrkryze.vas.MainActivity.applyTintFilter;

/**
 * Created by Petr on 04.08.2021. Yay!
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = "SettingsFragment";

    private Context context;

    public SettingsFragment() {
        // Required empty public constructor
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

        Preference versionInfoPreference = findPreference(getString(R.string.SETTING_KEY_VERSION_INFO));
        if (versionInfoPreference != null) {
            versionInfoPreference.setCopyingEnabled(true);
            versionInfoPreference.setSummaryProvider(preference -> {
                try {
                    PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    return getString(R.string.setting_version_info_summary, packageInfo.versionName);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                    return getString(R.string.setting_version_info_unknown_version);
                }
            });
            versionInfoPreference.setIcon(tintIcon(versionInfoPreference.getIcon()));

            versionInfoPreference.setOnPreferenceClickListener(preference -> showVersionInfoDialog((String) preference.getSummary()));
        }

        Preference feedbackPreference = findPreference(getString(R.string.SETTING_KEY_FEEDBACK));
        if (feedbackPreference != null) {
            feedbackPreference.setIntent(getEmailIntent());
            feedbackPreference.setIcon(tintIcon(feedbackPreference.getIcon()));
        }
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
        setHasOptionsMenu(true);
        context = requireContext();
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

    private boolean showVersionInfoDialog(String versionString) {
        View content = LayoutInflater.from(getContext()).inflate(R.layout.version_info_content,
                null, false);

        TextView contentText1 = content.findViewById(R.id.version_info_content_text1);
        TextView contentText2 = content.findViewById(R.id.version_info_content_text2);

        String[] split = versionString.split(" ");
        String versionNumber = split[split.length - 1];
        contentText1.setText(getString(R.string.version_info_dialog_version_number, versionNumber));
        contentText2.setText(getString(R.string.version_info_dialog_subtitle));

        new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .show();
        return true;
    }








    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toDisable = {R.id.action_menu_help, R.id.action_menu_save, R.id.action_menu_settings};
        int[] toEnable = {R.id.action_menu_show_saved_results, R.id.action_menu_quit,
                R.id.action_menu_show_session_info, R.id.action_menu_new_session};

        for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
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
