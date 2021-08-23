package com.petrkryze.vas.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
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
import com.petrkryze.vas.Session;
import com.petrkryze.vas.adapters.RecordingsRecyclerViewAdapter;
import com.petrkryze.vas.databinding.FragmentCurrentSessionInfoBinding;

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
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import static com.petrkryze.vas.fragments.ResultDetailFragment.RecordingListSortBy;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CurrentSessionInfoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CurrentSessionInfoFragment extends VASFragment {
    private static final String TAG = "CurrentSessionInfoFragment";
    private static final String CURRENT_SESSION = "current_session";

    private FragmentCurrentSessionInfoBinding binding;
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

    private RatingResult currentSession;
    private List<Recording> recordingsToDisplay;
    private RecordingListSortBy currentSort = RecordingListSortBy.GROUP; // Default sort

    private boolean isExcelSharing = false;
    private boolean isTextSharing = false;
    private Thread excelThread;
    private Thread textThread;

    private final View.OnClickListener shareListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.current_session_info_button_share) {
                Log.d(TAG, "onClick: SHARE BUTTON CLICKED");
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                buttonShare.setVisibility(View.INVISIBLE);
                buttonShareAsText.setVisibility(View.VISIBLE);
                buttonShareAsExcel.setVisibility(View.VISIBLE);
            }
        }
    };

    @SuppressLint("ShowToast")
    private final View.OnClickListener shareAsTextListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.current_session_info_button_results_share_as_text) {
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
                        if (currentSession.getPath() != null) {
                            resultFile = new File(currentSession.getPath());
                        } else {
                            resultFile = RatingManager.saveResultToTemp(context, currentSession);
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
                                            R.string.snackbar_share_current_session_text_failed, BaseTransientBottomBar.LENGTH_LONG)
                                            .setAnchorView(buttonBar)
                                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                                            .show());
                        } else {
                            startShareActivity(uri,
                                    getString(R.string.share_current_session_title, currentSession.toString()));
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
            if (v.getId() == R.id.current_session_info_button_results_share_as_excel) {
                Log.d(TAG, "onClick: SHARE AS EXCEL BUTTON CLICKED");
                Context context = requireContext();
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                // Do work in separate thread
                excelThread = new Thread(() -> {
                    isExcelSharing = true;

                    Uri uri = null;
                    try {
                        File excelFile = ExcelUtils.makeExcelFile(context, currentSession);
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
                                            R.string.snackbar_share_current_session_text_failed, BaseTransientBottomBar.LENGTH_LONG)
                                            .setAnchorView(buttonBar)
                                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                                            .show());
                        } else {
                            startShareActivity(uri,
                                    getString(R.string.share_current_session_title, currentSession.toString()));
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

    public CurrentSessionInfoFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("unused")
    public static CurrentSessionInfoFragment newInstance(RatingResult currentSession) {
        CurrentSessionInfoFragment fragment = new CurrentSessionInfoFragment();
        Bundle args = new Bundle();
        args.putSerializable(CURRENT_SESSION, currentSession);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            Session session = CurrentSessionInfoFragmentArgs.fromBundle(getArguments()).getCurrentSession();

            currentSession = new RatingResult(session);
            recordingsToDisplay = new ArrayList<>(currentSession.getRecordingList());
            recordingsToDisplay.sort(Recording.sortByGroup);
        }
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = FragmentCurrentSessionInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        buttonBar = binding.currentSessionInfoShareButtonBar;
        buttonShare = binding.currentSessionInfoButtonShare;
        buttonShareAsText = binding.currentSessionInfoButtonResultsShareAsText;
        buttonShareAsExcel = binding.currentSessionInfoButtonResultsShareAsExcel;
        progressTextLoading = binding.currentSessionInfoTextLoading;
        progressExcelLoading = binding.currentSessionInfoExcelLoading;

        buttonShare.setOnClickListener(shareListener);
        buttonShareAsText.setOnClickListener(shareAsTextListener);
        buttonShareAsExcel.setOnClickListener(shareAsExcelListener);

        binding.currentSessionInfoSessionID.setText(getString(R.string.current_session_info_sessionID, currentSession.getSessionID()));
        binding.currentSessionInfoRatedFraction.setText(currentSession.getRatedFractionString());
        binding.currentSessionInfoSeed.setText(String.valueOf(currentSession.getSeed()));
        binding.currentSessionInfoGeneratorDate.setText(currentSession.getGeneratorMessage().replace("-","."));

        recyclerViewAdapter = new RecordingsRecyclerViewAdapter(context, recordingsToDisplay);
        binding.currentSessionInfoRecordingsList.setAdapter(recyclerViewAdapter);
        binding.currentSessionInfoRecordingsList.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context,
                DividerItemDecoration.VERTICAL);
        binding.currentSessionInfoRecordingsList.addItemDecoration(dividerItemDecoration);

        headerID = binding.currentSessionInfoRecordingListColumnId;
        headerGroup = binding.currentSessionInfoRecordingListColumnGroup;
        headerIndex = binding.currentSessionInfoRecordingListColumnIndex;
        headerRating = binding.currentSessionInfoRecordingListColumnRating;

        headerID.setOnClickListener(new SortColumnListener(RecordingListSortBy.ID));
        headerGroup.setOnClickListener(new SortColumnListener(RecordingListSortBy.GROUP));
        headerIndex.setOnClickListener(new SortColumnListener(RecordingListSortBy.INDEX));
        headerRating.setOnClickListener(new SortColumnListener(RecordingListSortBy.RATING));

        headerGroup.setChecked(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void uncheckAll() {
        headerID.setChecked(false);
        headerGroup.setChecked(false);
        headerIndex.setChecked(false);
        headerRating.setChecked(false);
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
    public void onPrepareOptionsMenu(@NonNull @NotNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toDisable = { R.id.action_menu_save, R.id.action_menu_new_session,
                R.id.action_menu_show_session_info};
        int[] toEnable = {R.id.action_menu_help, R.id.action_menu_quit,
                R.id.action_menu_show_saved_results, R.id.action_menu_settings};

        for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
    }

    @SuppressLint("ShowToast")
    @Override
    public boolean onOptionsItemSelected(@NonNull @NotNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_help) {
            onShowHelp();
            return true;
        } else if (itemID == R.id.action_menu_show_saved_results) {
            onShowSavedResults(results -> {
                NavDirections directions =
                        CurrentSessionInfoFragmentDirections.actionCurrentSessionInfoFragmentToResultFragment(results);
                NavHostFragment.findNavController(this).navigate(directions);
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onShowHelp() {
        String contextHelpTitle = getString(R.string.help_context_title_current_session_info_fragment);
        String[] contextHelpDescriptions = getResources().getStringArray(R.array.help_tag_description_current_session_info_fragment);
        String contextHelpBody = getString(R.string.help_context_body_current_session_info_fragment);

        NavDirections directions = CurrentSessionInfoFragmentDirections.
                actionCurrentSessionInfoFragmentToHelpFragment(contextHelpTitle, contextHelpDescriptions,
                        contextHelpBody, R.drawable.help_screen_current_session_fragment);
        NavHostFragment.findNavController(this).navigate(directions);
    }

    class SortColumnListener implements View.OnClickListener {
        RecordingListSortBy sortBy;

        public SortColumnListener(RecordingListSortBy sortBy) {
            this.sortBy = sortBy;
        }

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
                CurrentSessionInfoFragment.this.currentSort = sortBy;
            }

            recyclerViewAdapter.notifyDataSetChanged();
        }
    }
}