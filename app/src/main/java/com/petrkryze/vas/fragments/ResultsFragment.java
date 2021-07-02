package com.petrkryze.vas.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.adapters.ResultsRecyclerViewAdapter;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.petrkryze.vas.MainActivity.applyTintFilter;

/**
 * A fragment representing a list of Items.
 */
public class ResultsFragment extends Fragment {
    private static final String TAG = "ResultFragment";
    public static final String ResultListSerializedKey = "resultsList";

    private ArrayList<RatingResult> ratingResults;
    private Snackbar hint;
    private RecyclerView resultsListView;
    private Button buttonShareAll;

    private Vibrator vibrator;
    private static int VIBRATE_BUTTON_MS;
    private static int VIBRATE_BUTTON_LONG_MS;
    private Handler handler;

    private final View.OnClickListener shareAllListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.button_results_share_all) {
                Log.i(TAG, "onClick: SHARE ALL BUTTON CLICKED");
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                Context context = getContext();
                if (context == null) {
                    Log.e(TAG, "onClick: No context!");
                    return;
                }

                ArrayList<Uri> uris = new ArrayList<>();

                for (RatingResult r : ratingResults) {
                    File f = new File(r.getPath());
                    try {
                        uris.add(FileProvider.getUriForFile(
                                getContext(),"com.petrkryze.vas.fileprovider", f));
                    } catch (IllegalArgumentException e) {
                        Log.e("File Selector",
                                "The selected file can't be shared: " + f.toString());
                        e.printStackTrace();
                    }
                }

                Intent sharingIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                sharingIntent.setType("text/plain");
                sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sharingIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                sharingIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.result_share_title, uris.size()));

                getContext().startActivity(Intent.createChooser(sharingIntent, getContext().getString(R.string.share_using)));
            }
        }
    };

    private final OnItemDetailListener onItemDetailListener = new OnItemDetailListener() {
        @Override
        public void onItemClick(RatingResult selectedResult) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            NavDirections directions =
                    ResultsFragmentDirections.actionResultFragmentToResultDetailFragment(selectedResult);
            NavHostFragment.findNavController(ResultsFragment.this).navigate(directions);
        }

        @SuppressLint("ShowToast")
        @Override
        public boolean onItemLongClick(RatingResult selectedResult, int position) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_LONG_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            Context context = ResultsFragment.this.requireContext();
            new MaterialAlertDialogBuilder(context)
                    .setTitle(getString(R.string.dialog_delete_result_title))
                    .setMessage(getString(R.string.dialog_delete_result_message))
                    .setIcon(applyTintFilter(
                            ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete),
                            context.getColor(R.color.secondaryColor)))
                    .setPositiveButton(R.string.dialog_delete_result_confirm, (dialog, which) -> {
                        Pair<Boolean, String> outcome = deleteResult(selectedResult);

                        if (outcome.first) {
                            assert resultsListView != null;
                            assert resultsListView.getAdapter() != null;
                            ratingResults.remove(selectedResult);
                            resultsListView.getAdapter().notifyDataSetChanged();
                        }

                        Snackbar.make(
                                requireActivity().findViewById(R.id.coordinator),
                                outcome.second, BaseTransientBottomBar.LENGTH_SHORT)
                                .setAnchorView(buttonShareAll)
                                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
                    })
                    .setNegativeButton(R.string.dialog_delete_result_cancel, null)
                    .create()
                    .show();
            return true;
        }
    };

    private Pair<Boolean, String> deleteResult(RatingResult toDelete) {
        try {
            Files.delete(new File(toDelete.getPath()).toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return Pair.create(false, getString(R.string.result_deleted_failure, e.getMessage()));
        }

        return Pair.create(true, getString(R.string.result_deleted_success));
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ResultsFragment() {
    }

    @SuppressWarnings("unused")
    public static ResultsFragment newInstance(ArrayList<RatingResult> ratingResults) {
        ResultsFragment fragment = new ResultsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ResultListSerializedKey, ratingResults);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (getArguments() != null) {
            //noinspection unchecked
            ratingResults = ResultsFragmentArgs.fromBundle(getArguments()).getRatings();
            ratingResults.sort(RatingResult.sortChronologically);
        }

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
        VIBRATE_BUTTON_LONG_MS = getResources().getInteger(R.integer.VIBRATE_LONG_CLICK_MS);
        this.handler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_results, container, false);
    }

    @SuppressLint("ShowToast")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        buttonShareAll = view.findViewById(R.id.button_results_share_all);
        buttonShareAll.setOnClickListener(shareAllListener);

        resultsListView = view.findViewById(R.id.results_list);
        resultsListView.setAdapter(new ResultsRecyclerViewAdapter(
                context, ratingResults, onItemDetailListener));
        resultsListView.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context,
                DividerItemDecoration.VERTICAL);
        resultsListView.addItemDecoration(dividerItemDecoration);

        hint = Snackbar.make(
                requireActivity().findViewById(R.id.coordinator),
                R.string.hint_results_select, BaseTransientBottomBar.LENGTH_LONG)
                .setAnchorView(buttonShareAll)
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE);

        handler.postDelayed(hint::show,context.getResources().getInteger(R.integer.SNACKBAR_HINT_DELAY_MS));
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull @NotNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toDisable = { R.id.action_menu_save, R.id.action_menu_show_saved_results,
                 R.id.action_menu_reset_ratings};
        int[] toEnable = {R.id.action_menu_help, R.id.action_menu_show_session_info,
                R.id.action_menu_quit};

        for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull @NotNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_help) {
            String contextHelpMessage = getString(R.string.help_context_body_results_fragment);
            String contextHelpTitle = getString(R.string.help_context_title_results_fragment);

            NavDirections directions =
                    ResultsFragmentDirections.
                            actionResultFragmentToHelpFragment(contextHelpMessage, contextHelpTitle);
            NavHostFragment.findNavController(this).navigate(directions);
            return true;
        } else if (itemID == R.id.action_menu_show_session_info) {
            MainActivity.navigateToCurrentSessionInfo(
                    this, session -> {
                        NavDirections directions = ResultsFragmentDirections
                                .actionResultFragmentToCurrentSessionInfoFragment(session);
                        NavHostFragment.findNavController(ResultsFragment.this)
                                .navigate(directions);
                    }
            );
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (hint != null) {
            hint.dismiss();
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    public interface OnItemDetailListener {
        void onItemClick(RatingResult selectedResult);
        boolean onItemLongClick(RatingResult selectedResult, int position);
    }
}