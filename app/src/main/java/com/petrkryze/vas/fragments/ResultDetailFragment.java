package com.petrkryze.vas.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ProgressBar;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.ExcelUtils;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.Recording;
import com.petrkryze.vas.adapters.RecordingsRecyclerViewAdapter;
import com.petrkryze.vas.databinding.FragmentResultDetailBinding;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ResultDetailFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ResultDetailFragment extends VASFragment {
    private static final String TAG = "ResultDetailFragment";
    private static final String RESULT_TO_DISPLAY = "result_to_display";

    private FragmentResultDetailBinding binding;
    private ConstraintLayout buttonBar;
    private Button buttonShare;
    private Button buttonShareAsText;
    private Button buttonShareAsExcel;
    private ProgressBar progressTextLoading;
    private ProgressBar progressExcelLoading;
    private CheckedTextView headerID;
    private CheckedTextView headerGroup;
    private CheckedTextView headerIndex;
    private CheckedTextView headerRating;
    private RecordingsRecyclerViewAdapter recyclerViewAdapter;

    private RatingResult resultToDisplay;
    private List<Recording> recordingsToDisplay;
    private RecordingListSortBy currentSort = RecordingListSortBy.GROUP; // Default sort

    private boolean isExcelSharing = false;
    private boolean isTextSharing = false;
    private Thread textThread;
    private Thread excelThread;

    private final View.OnClickListener shareListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: SHARE BUTTON CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(
                    VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

            buttonShare.setVisibility(View.INVISIBLE);
            buttonShareAsText.setVisibility(View.VISIBLE);
            buttonShareAsExcel.setVisibility(View.VISIBLE);
        }
    };

    @SuppressLint("ShowToast")
    private final View.OnClickListener shareAsTextListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.result_detail_button_results_share_as_text) {
                Log.d(TAG, "onClick: SHARE AS TEXT BUTTON CLICKED");
                Context context = requireContext();
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                // Do work in separate thread
                textThread = new Thread(() -> {
                    isTextSharing = true;

                    Uri uri = null;
                    try {
                        File resultFile;
                        if (resultToDisplay.getPath() != null) {
                            resultFile = new File(resultToDisplay.getPath());
                        } else {
                            resultFile = RatingManager.saveResultToTemp(context, resultToDisplay);
                        }

                        uri = FileProvider.getUriForFile(
                                context,"com.petrkryze.vas.fileprovider", resultFile);
                    } catch (IllegalArgumentException e ) {
                        Log.e(TAG, "URI could not be retrieved for this result.", e);
                        e.printStackTrace();
                    } catch (Exception e) {
                        Log.e(TAG, "onClick: Result could not be saved temporarily.", e);
                        e.printStackTrace();
                    }

                    if (!Thread.currentThread().isInterrupted() || getActivity() != null) {
                        if (uri == null) {
                            requireActivity().runOnUiThread(() ->
                                    Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                            R.string.snackbar_share_rating_detail_text_failed, BaseTransientBottomBar.LENGTH_LONG)
                                            .setAnchorView(buttonBar)
                                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                                            .show());
                        } else {
                            startShareActivity(uri,
                                    getString(R.string.share_rating_detail_title, resultToDisplay.toString()));
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

    @SuppressLint("ShowToast")
    private final View.OnClickListener shareAsExcelListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.result_detail_button_results_share_as_excel) {
                Log.d(TAG, "onClick: SHARE AS EXCEL BUTTON CLICKED");
                Context context = requireContext();
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                // Do work in separate thread
                excelThread = new Thread(() -> {
                    isExcelSharing = true;

                    Uri uri = null;
                    try {
                        File excelFile = ExcelUtils.makeExcelFile(context, resultToDisplay);
                        uri = FileProvider.getUriForFile(
                                context,"com.petrkryze.vas.fileprovider", excelFile);
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "URI could not be retrieved for this result.", e);
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.e(TAG, "makeExcelFile: Excel file for current session could not be created!", e);
                        e.printStackTrace();
                    }

                    if (!Thread.currentThread().isInterrupted() || getActivity() != null) {
                        if (uri == null) {
                            requireActivity().runOnUiThread(() ->
                                    Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                            R.string.snackbar_share_rating_detail_text_failed, BaseTransientBottomBar.LENGTH_LONG)
                                            .setAnchorView(buttonBar)
                                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                                            .show());
                        } else {
                            startShareActivity(uri,
                                    getString(R.string.share_rating_detail_title, resultToDisplay.toString()));
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

    public ResultDetailFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("unused")
    public static ResultDetailFragment newInstance(RatingResult resultToDisplay) {
        ResultDetailFragment fragment = new ResultDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(RESULT_TO_DISPLAY, resultToDisplay);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            resultToDisplay = ResultDetailFragmentArgs.fromBundle(getArguments()).getRatingResult();
            recordingsToDisplay = new ArrayList<>(resultToDisplay.getRecordingList());
            recordingsToDisplay.sort(Recording.sortByGroup);
        }
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = FragmentResultDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();
        setupMenu();

        buttonBar = binding.resultDetailShareButtonBar;
        buttonShare = binding.resultDetailButtonShare;
        buttonShareAsText = binding.resultDetailButtonResultsShareAsText;
        buttonShareAsExcel = binding.resultDetailButtonResultsShareAsExcel;
        progressTextLoading = binding.resultDetailTextLoading;
        progressExcelLoading = binding.resultDetailExcelLoading;

        buttonShare.setOnClickListener(shareListener);
        buttonShareAsText.setOnClickListener(shareAsTextListener);
        buttonShareAsExcel.setOnClickListener(shareAsExcelListener);

        binding.resultDetailSessionID.setText(getString(R.string.result_detail_sessionID, resultToDisplay.getSessionID()));
        binding.resultDetailSaveDate.setText(formatDateTime(resultToDisplay.getSaveDate()));
        binding.resultDetailSeed.setText(String.valueOf(resultToDisplay.getSeed()));
        binding.resultDetailGeneratorDate.setText(resultToDisplay.getGeneratorMessage().replace("-","."));

        recyclerViewAdapter = new RecordingsRecyclerViewAdapter(context, recordingsToDisplay);
        binding.resultDetailRecordingsList.setAdapter(recyclerViewAdapter);
        binding.resultDetailRecordingsList.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context,
                DividerItemDecoration.VERTICAL);
        binding.resultDetailRecordingsList.addItemDecoration(dividerItemDecoration);

        headerID = binding.resultDetailRecordingListColumnId;
        headerGroup = binding.resultDetailRecordingListColumnGroup;
        headerIndex = binding.resultDetailRecordingListColumnIndex;
        headerRating = binding.resultDetailRecordingListColumnRating;

        headerID.setOnClickListener(new SortColumnListener(RecordingListSortBy.ID));
        headerGroup.setOnClickListener(new SortColumnListener(RecordingListSortBy.GROUP));
        headerIndex.setOnClickListener(new SortColumnListener(RecordingListSortBy.INDEX));
        headerRating.setOnClickListener(new SortColumnListener(RecordingListSortBy.RATING));

        headerGroup.setChecked(true);
    }

    private void uncheckAll() {
        headerID.setChecked(false);
        headerGroup.setChecked(false);
        headerIndex.setChecked(false);
        headerRating.setChecked(false);
    }

    private String formatDateTime(String rawDateTime) {
        String[] split = rawDateTime.split(" ");
        return split[0].replace("-", ".") + " " + split[1];
    }

    private void setupMenu(){
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuProvider.super.onPrepareMenu(menu);

                int[] toDisable = { R.id.action_menu_save, R.id.action_menu_new_session};
                int[] toEnable = {R.id.action_menu_help, R.id.action_menu_show_session_info,
                        R.id.action_menu_quit, R.id.action_menu_show_saved_results, R.id.action_menu_settings};

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
                } else if (itemID == R.id.action_menu_show_saved_results) {
                    NavHostFragment.findNavController(ResultDetailFragment.this).navigateUp();
                    return true;
                } else if (itemID == R.id.action_menu_show_session_info) {
                    onShowSessionInfo(session -> {
                        NavDirections directions = ResultDetailFragmentDirections
                                .actionResultDetailFragmentToCurrentSessionInfoFragment(session);
                        NavHostFragment.findNavController(ResultDetailFragment.this).navigate(directions);
                    });
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void onShowHelp() {
        String contextHelpTitle = getString(R.string.help_context_title_result_detail_fragment);
        String[] contextHelpDescriptions = getResources().getStringArray(R.array.help_tag_description_result_detail_fragment);
        String contextHelpBody = getString(R.string.help_context_body_result_detail_fragment);

        NavDirections directions = ResultDetailFragmentDirections.
                actionResultDetailFragmentToHelpFragment(contextHelpTitle, contextHelpDescriptions,
                        contextHelpBody, R.drawable.help_screen_rating_detail_fragment);
        NavHostFragment.findNavController(this).navigate(directions);
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

    public enum RecordingListSortBy {
        ID, GROUP, INDEX, RATING
    }

    class SortColumnListener implements View.OnClickListener {
        RecordingListSortBy sortBy;

        public SortColumnListener(RecordingListSortBy sortBy) {
            this.sortBy = sortBy;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: COLUMN SORT BY " + sortBy + " BUTTON CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(
                    VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

            if (currentSort.equals(sortBy)) { // Clicked column is same as current selected sort
                // Do reverse sort, don't change current sort
                Collections.reverse(recordingsToDisplay);
            } else {
                uncheckAll();

                switch (sortBy) {
                    case ID: recordingsToDisplay.sort(Recording.sortByID); break;
                    case GROUP: recordingsToDisplay.sort(Recording.sortByGroup); break;
                    case INDEX: recordingsToDisplay.sort(Recording.sortByRandomIndex); break;
                    case RATING: recordingsToDisplay.sort(Recording.sortByRating); break;
                    default: break;
                }
                ((CheckedTextView) v).setChecked(true);
                ResultDetailFragment.this.currentSort = sortBy;
            }

            // List is being sorted and needs to be redrawn whole
            recyclerViewAdapter.notifyDataSetChanged();
        }
    }

}