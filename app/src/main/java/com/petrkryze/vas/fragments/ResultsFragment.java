package com.petrkryze.vas.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.ExcelUtils;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.adapters.ResultsRecyclerViewAdapter;
import com.petrkryze.vas.databinding.FragmentResultsBinding;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.petrkryze.vas.MainActivity.applyTintFilter;

/**
 * A fragment representing a list of Items.
 */
@SuppressLint("ShowToast")
public class ResultsFragment extends VASFragment {
    private static final String TAG = "ResultFragment";
    public static final String ResultListSerializedKey = "resultsList";

    private FragmentResultsBinding binding;
    private TextView TWnoResults;
    private RecyclerView resultsListView;
    private ConstraintLayout buttonBar;
    private Button buttonShareAll;
    private Button buttonShareAsText;
    private Button buttonShareAsExcel;
    private ProgressBar progressTextLoading;
    private ProgressBar progressExcelLoading;
    private Snackbar hint;

    private ArrayList<RatingResult> ratingResults;

    private Handler handler;
    private boolean isExcelSharing = false;
    private boolean isTextSharing = false;
    private Thread excelThread;
    private Thread textThread;

    private final View.OnClickListener shareAllListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.results_fragment_button_results_share_all) {
                Log.d(TAG, "onClick: SHARE ALL BUTTON CLICKED");
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                buttonShareAll.setVisibility(View.INVISIBLE);
                buttonShareAsText.setVisibility(View.VISIBLE);
                buttonShareAsExcel.setVisibility(View.VISIBLE);
            }
        }
    };

    private final View.OnClickListener shareAllAsTextListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.results_fragment_button_results_share_as_text) {
                Log.d(TAG, "onClick: SHARE AS TEXT BUTTON CLICKED");
                Context context = requireContext();
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                // Do work in separate thread
                textThread = new Thread(() -> {
                    isTextSharing = true;
                    ArrayList<Uri> uris = new ArrayList<>();
                    for (RatingResult r : new ArrayList<>(ratingResults)) {
                        File resultFile = new File(r.getPath());
                        try {
                            uris.add(FileProvider.getUriForFile(
                                    context,"com.petrkryze.vas.fileprovider", resultFile));
                        } catch (IllegalArgumentException e) {
                            Log.e("File Selector",
                                    "The selected file can't be shared: " + resultFile);
                            e.printStackTrace();
                        }
                    }

                    if (!Thread.currentThread().isInterrupted() || getActivity() != null) {
                        if (uris.isEmpty()) {
                            requireActivity().runOnUiThread(() ->
                                    Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                            R.string.snackbar_share_all_text_failed, BaseTransientBottomBar.LENGTH_LONG)
                                            .setAnchorView(buttonBar)
                                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                                            .show());
                        } else {
                            startShareActivity(uris);
                        }
                    }
                });

                // Show loading circle over the excel button and start the work
                buttonShareAsText.setTextColor(context.getColor(R.color.invisible));
                buttonShareAsText.setClickable(false);

                progressTextLoading.setVisibility(View.VISIBLE);
                textThread.start();
            }
        }
    };

    private final View.OnClickListener shareAllAsExcelListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.results_fragment_button_results_share_as_excel) {
                Log.d(TAG, "onClick: SHARE AS EXCEL BUTTON CLICKED");
                Context context = requireContext();
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                // Do work in separate thread
                excelThread = new Thread(() -> {
                    isExcelSharing = true;
                    ArrayList<Uri> uris = new ArrayList<>();
                    for (RatingResult result : new ArrayList<>(ratingResults)) {
                        try {
                            File excelFile = ExcelUtils.makeExcelFile(context, result);
                            uris.add(FileProvider.getUriForFile(
                                    context,"com.petrkryze.vas.fileprovider", excelFile));
                        } catch (IllegalArgumentException e) {
                            Log.e("File Selector",
                                    "Selected file can't be shared: " + result.getPath());
                            e.printStackTrace();
                        } catch (IOException e) {
                            Log.e(TAG, "makeExcelFile: Excel file " + result.getPath() +
                                    " could not be created!", e);
                            e.printStackTrace();
                        }
                    }

                    if (!Thread.currentThread().isInterrupted() || getActivity() != null) {
                        if (uris.isEmpty()) {
                            requireActivity().runOnUiThread(() ->
                                    Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                            R.string.snackbar_share_all_excel_failed, BaseTransientBottomBar.LENGTH_LONG)
                                            .setAnchorView(buttonBar)
                                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                                            .show());
                        } else {
                            startShareActivity(uris);
                        }
                    }
                });

                // Show loading circle over the excel button and start the work
                buttonShareAsExcel.setTextColor(context.getColor(R.color.invisible));
                buttonShareAsExcel.setClickable(false);

                progressExcelLoading.setVisibility(View.VISIBLE);
                excelThread.start();
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
                            assert ratingResults != null;
                            assert resultsListView != null;
                            assert resultsListView.getAdapter() != null;
                            ratingResults.remove(selectedResult);
                            resultsListView.getAdapter().notifyDataSetChanged();

                            if (ratingResults.isEmpty()) {
                                buttonShareAll.setEnabled(false);
                                resultsListView.setVisibility(View.GONE);
                                TWnoResults.setVisibility(View.VISIBLE);
                            }
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

    public ResultsFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("unused")
    public static ResultsFragment newInstance(ArrayList<RatingResult> ratingResults) {
        ResultsFragment fragment = new ResultsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ResultListSerializedKey, ratingResults);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ratingResults = ResultsFragmentArgs.fromBundle(getArguments()).getRatings();
            ratingResults.sort(RatingResult.sortChronologically);
        }

        this.handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = FragmentResultsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ShowToast")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();
        setupMenu();

        buttonBar = binding.resultsFragmentShareButtonBar;
        buttonShareAll = binding.resultsFragmentButtonResultsShareAll;
        buttonShareAsText = binding.resultsFragmentButtonResultsShareAsText;
        buttonShareAsExcel = binding.resultsFragmentButtonResultsShareAsExcel;
        progressTextLoading = binding.resultsFragmentTextLoading;
        progressExcelLoading = binding.resultsFragmentExcelLoading;
        resultsListView = binding.resultsFragmentResultsList;
        TWnoResults = binding.resultsFragmentTextviewNoResults;

        if (ratingResults != null && !ratingResults.isEmpty()) {
            buttonShareAll.setOnClickListener(shareAllListener);
            buttonShareAsText.setOnClickListener(shareAllAsTextListener);
            buttonShareAsExcel.setOnClickListener(shareAllAsExcelListener);

            resultsListView.setAdapter(new ResultsRecyclerViewAdapter(
                    context, ratingResults, onItemDetailListener));
            resultsListView.setLayoutManager(new LinearLayoutManager(context));
            DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context,
                    DividerItemDecoration.VERTICAL);
            resultsListView.addItemDecoration(dividerItemDecoration);

            hint = Snackbar.make(
                    requireActivity().findViewById(R.id.coordinator),
                    R.string.snackbar_results_select_hint, BaseTransientBottomBar.LENGTH_LONG)
                    .setAnchorView(buttonBar)
                    .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE);

            handler.postDelayed(hint::show, context.getResources().getInteger(R.integer.SNACKBAR_HINT_DELAY_MS));
        } else { // No results found
            buttonShareAll.setEnabled(false);
            resultsListView.setVisibility(View.GONE);
            TWnoResults.setVisibility(View.VISIBLE);
        }
    }

    private Pair<Boolean, String> deleteResult(RatingResult toDelete) {
        try {
            Files.delete(new File(toDelete.getPath()).toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return Pair.create(false, getString(R.string.snackbar_result_deleted_failure, e.getMessage()));
        }

        return Pair.create(true, getString(R.string.snackbar_result_deleted_success));
    }

    public interface OnItemDetailListener {
        void onItemClick(RatingResult selectedResult);
        boolean onItemLongClick(RatingResult selectedResult, int position);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isExcelSharing) {
            progressExcelLoading.setVisibility(View.GONE);
            buttonShareAsExcel.setTextColor(requireContext().getColor(R.color.textPrimaryOnPrimary));
            buttonShareAsExcel.setClickable(true);

            isExcelSharing = false;
        }

        if (isTextSharing) {
            progressTextLoading.setVisibility(View.GONE);
            buttonShareAsText.setTextColor(requireContext().getColor(R.color.textPrimaryOnSecondary));
            buttonShareAsText.setClickable(true);

            isTextSharing = false;
        }

        ExcelUtils.dumpTempFolder(requireContext());
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (textThread != null && textThread.isAlive()) {
            textThread.interrupt();
        }
        if (excelThread != null && excelThread.isAlive()) {
            excelThread.interrupt();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuProvider.super.onPrepareMenu(menu);

                int[] toDisable = { R.id.action_menu_save, R.id.action_menu_show_saved_results,
                        R.id.action_menu_new_session};
                int[] toEnable = {R.id.action_menu_help, R.id.action_menu_show_session_info,
                        R.id.action_menu_quit, R.id.action_menu_settings};

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
                } else if (itemID == R.id.action_menu_show_session_info) {
                    onShowSessionInfo(session -> {
                        NavDirections directions = ResultsFragmentDirections
                                .actionResultFragmentToCurrentSessionInfoFragment(session);
                        NavHostFragment.findNavController(ResultsFragment.this).navigate(directions);
                    });
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void onShowHelp() {
        String contextHelpTitle = getString(R.string.help_context_title_results_fragment);
        String[] contextHelpDescriptions = getResources().getStringArray(R.array.help_tag_description_results_fragment);
        String contextHelpBody = getString(R.string.help_context_body_results_fragment);

        NavDirections directions = ResultsFragmentDirections.
                actionResultFragmentToHelpFragment(contextHelpTitle, contextHelpDescriptions,
                        contextHelpBody, R.drawable.help_screen_results_fragment);
        NavHostFragment.findNavController(this).navigate(directions);
    }
}