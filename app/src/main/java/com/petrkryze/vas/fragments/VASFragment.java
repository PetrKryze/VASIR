package com.petrkryze.vas.fragments;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.Session;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import static com.petrkryze.vas.MainActivity.applyTintFilter;
import static com.petrkryze.vas.MainActivity.html;
import static com.petrkryze.vas.RatingManager.SESSION_INFO_LOADED_SESSION;

/**
 * Created by Petr on 20.08.2021. Yay!
 */
public class VASFragment extends Fragment {

    AlertDialog dialog;
    Vibrator vibrator;
    static int VIBRATE_BUTTON_MS;
    static int VIBRATE_BUTTON_LONG_MS;
    static int VIBRATE_RATING_START_MS;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get vibrator service for UI vibration feedback
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
        VIBRATE_BUTTON_LONG_MS = getResources().getInteger(R.integer.VIBRATE_LONG_CLICK_MS);
        VIBRATE_RATING_START_MS = getResources().getInteger(R.integer.VIBRATE_RATING_BAR_START_MS);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        loadingVisibility(true);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    void startShareActivity(Uri uri, String description) {
        String mimeType = "text/plain";
        String[] mimeTypeArray = new String[] { mimeType };

        ClipDescription clipDescription = new ClipDescription(description, mimeTypeArray);
        ClipData clipData = new ClipData(clipDescription, new ClipData.Item(uri));

        // Note possible duplicate mimetype and data declaration, maybe fix when ClipData framework
        // is not a complete joke
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setClipData(clipData);
        sharingIntent.setType(mimeType)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TITLE, description)
                .putExtra(Intent.EXTRA_SUBJECT, description);

        requireContext().startActivity(Intent.createChooser(sharingIntent,
                requireContext().getString(R.string.share_using)));
    }

    void startShareActivity(ArrayList<Uri> uris) {
        String mimeType = "text/plain";
        String[] mimeTypeArray = new String[] { mimeType };

        ArrayList<ClipData.Item> items = new ArrayList<>();
        for (Uri uri : uris) items.add(new ClipData.Item(uri));

        ClipDescription clipDescription = new ClipDescription(getString(R.string.share_result_title), mimeTypeArray);
        ClipData clipData = new ClipData(clipDescription, items.get(0));
        items.remove(0);
        for (ClipData.Item item : items) clipData.addItem(item);

        // Note possible duplicate mimetype and data declaration, maybe fix when ClipData framework
        // is not a complete joke
        Intent sharingIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        sharingIntent.setClipData(clipData);
        sharingIntent.setType(mimeType)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .putExtra(Intent.EXTRA_TITLE, getString(R.string.share_result_title, uris.size()))
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_result_title, uris.size()));

        requireContext().startActivity(Intent.createChooser(sharingIntent,
                requireContext().getString(R.string.share_using)));
    }

    public interface ShowSavedResultsCallback { void onNavigateToSavedResults(ArrayList<RatingResult> results); }
    @SuppressLint("ShowToast")
    void onShowSavedResults(ShowSavedResultsCallback callback) {
        loadingVisibility(true);
        new Thread(() -> { // Threading for slow loading times
            try {
                ArrayList<RatingResult> results = RatingManager.loadResults(requireContext());

                requireActivity().runOnUiThread(() -> {
                    loadingVisibility(false);
                    callback.onNavigateToSavedResults(results);
                });
            } catch (Exception e) {
                e.printStackTrace();

                requireActivity().runOnUiThread(() -> {
                    loadingVisibility(false);
                    String message = html(getString(R.string.snackbar_ratings_loading_failed, e.getMessage()));
                    Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                            message, BaseTransientBottomBar.LENGTH_LONG)
                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
                });
            }
        }, "ResultsLoadingThread").start();
    }

    public interface ShowSessionInfoCallback { void onNavigateToSessionInfo(Session session); }
    void onShowSessionInfo(ShowSessionInfoCallback callback) {
        loadingVisibility(true);

        new Thread(() -> {
            Context context = requireContext();
            Bundle bundle = RatingManager.getSessionInfo(requireActivity());
            String message = "";
            String title = "";
            Drawable icon = null;

            switch ((RatingManager.LoadResult) bundle.getSerializable(RatingManager.GET_SESSION_INFO_LOAD_RESULT_KEY)) {
                case OK:
                    requireActivity().runOnUiThread(() -> {
                        loadingVisibility(false);
                        callback.onNavigateToSessionInfo((Session) bundle.getSerializable(SESSION_INFO_LOADED_SESSION));
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

    void loadingVisibility(boolean show) {
        requireActivity().findViewById(R.id.general_loading_container)
                .setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onStart() {
        super.onStart();
        loadingVisibility(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
