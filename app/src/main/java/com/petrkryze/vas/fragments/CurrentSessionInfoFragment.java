package com.petrkryze.vas.fragments;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Html;
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
public class CurrentSessionInfoFragment extends Fragment {
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

    private Vibrator vibrator;
    private static int VIBRATE_BUTTON_MS;
    private boolean isExcelSharing = false;
    private boolean isTextSharing = false;

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
                Thread textThread = new Thread(() -> {
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

                    if (uri == null) {
                        requireActivity().runOnUiThread(() ->
                                Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                        R.string.snackbar_share_current_session_text_failed, BaseTransientBottomBar.LENGTH_LONG)
                                        .setAnchorView(buttonBar)
                                        .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                                        .show());
                    } else {
                        startShareActivity(uri,
                                getString(R.string.current_session_share_title, currentSession.toString()));
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
                Thread excelThread = new Thread(() -> {
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

                    if (uri == null) {
                        requireActivity().runOnUiThread(() ->
                                Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                        R.string.snackbar_share_current_session_text_failed, BaseTransientBottomBar.LENGTH_LONG)
                                        .setAnchorView(buttonBar)
                                        .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                                        .show());
                    } else {
                        startShareActivity(uri,
                                getString(R.string.current_session_share_title, currentSession.toString()));
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
        loadingVisibility(true);
        if (getArguments() != null) {
            currentSession = CurrentSessionInfoFragmentArgs.fromBundle(getArguments()).getRatingResult();
            recordingsToDisplay = new ArrayList<>(currentSession.getRecordings());
            recordingsToDisplay.sort(Recording.sortByGroup);
        }

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
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

        binding.currentSessionInfoSessionID.setText(getString(R.string.current_session_info_sessionID, currentSession.getSession_ID()));
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

        loadingVisibility(false);
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

    private void startShareActivity(Uri uri, String description) {
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

    @Override
    public void onPrepareOptionsMenu(@NonNull @NotNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toDisable = { R.id.action_menu_save, R.id.action_menu_reset_ratings,
                R.id.action_menu_show_session_info};
        int[] toEnable = {R.id.action_menu_help, R.id.action_menu_quit,
                R.id.action_menu_show_saved_results};

        for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
    }

    @SuppressLint("ShowToast")
    @Override
    public boolean onOptionsItemSelected(@NonNull @NotNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_help) {
            String contextHelpMessage = getString(R.string.help_context_body_current_session_info_fragment);
            String contextHelpTitle = getString(R.string.help_context_title_current_session_info_fragment);

            NavDirections directions =
                    CurrentSessionInfoFragmentDirections.
                            actionCurrentSessionInfoFragmentToHelpFragment(contextHelpMessage, contextHelpTitle);
            NavHostFragment.findNavController(this).navigate(directions);
            return true;
        } else if (itemID == R.id.action_menu_show_saved_results) {
            loadingVisibility(true);
            new Thread(() -> { // Threading for slow loading times
                try {
                    ArrayList<RatingResult> ratings = RatingManager.loadResults(requireContext());
                    loadingVisibility(false);
                    requireActivity().runOnUiThread(() -> {
                        NavDirections directions =
                                CurrentSessionInfoFragmentDirections.actionCurrentSessionInfoFragmentToResultFragment(ratings);
                        NavHostFragment.findNavController(this).navigate(directions);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    loadingVisibility(false);
                    requireActivity().runOnUiThread(() -> Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                            Html.fromHtml(getString(R.string.snackbar_ratings_loading_failed, e.getMessage()),Html.FROM_HTML_MODE_LEGACY),
                            BaseTransientBottomBar.LENGTH_LONG)
                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show()
                    );
                }
            }, "ResultsLoadingThread").start();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadingVisibility(boolean show) {requireContext().sendBroadcast(
            new Intent().setAction(show ? MainActivity.ACTION_SHOW_LOADING : MainActivity.ACTION_HIDE_LOADING));}

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