package com.petrkryze.vas.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.Recording;
import com.petrkryze.vas.adapters.RecordingsRecyclerViewAdapter;
import com.petrkryze.vas.databinding.FragmentResultDetailBinding;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ResultDetailFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ResultDetailFragment extends Fragment {
    private static final String TAG = "ResultDetailFragment";
    private static final String RESULT_TO_DISPLAY = "result_to_display";

    private FragmentResultDetailBinding binding;
    private CheckedTextView headerID;
    private CheckedTextView headerGroup;
    private CheckedTextView headerIndex;
    private CheckedTextView headerRating;
    private RecordingsRecyclerViewAdapter recyclerViewAdapter;

    private RatingResult resultToDisplay;
    private List<Recording> recordingsToDisplay;
    private RecordingListSortBy currentSort = RecordingListSortBy.GROUP; // Default sort

    private Vibrator vibrator;
    private static int VIBRATE_BUTTON_MS;

    private final View.OnClickListener shareListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: SHARE BUTTON CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(
                    VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

            Context context = requireContext();

            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            String shareSubject = context.getString(R.string.share_subject, resultToDisplay.getSession_ID());
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
            sharingIntent.putExtra(Intent.EXTRA_TEXT, resultToDisplay.getRawContent());

            context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.share_using)));
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
        loadingVisibility(true);
        if (getArguments() != null) {
            resultToDisplay = ResultDetailFragmentArgs.fromBundle(getArguments()).getRatingResult();
            recordingsToDisplay = new ArrayList<>(resultToDisplay.getRecordings());
            recordingsToDisplay.sort(Recording.sortByGroup);
        }

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        binding = FragmentResultDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        binding.resultDetailButtonShare.setOnClickListener(shareListener);

        binding.resultDetailSessionID.setText(getString(R.string.result_detail_sessionID, resultToDisplay.getSession_ID()));
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

    private String formatDateTime(String rawDateTime) {
        String[] split = rawDateTime.split(" ");
        return split[0].replace("-", ".") + " " + split[1];
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull @NotNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toDisable = { R.id.action_menu_save, R.id.action_menu_reset_ratings};
        int[] toEnable = {R.id.action_menu_help, R.id.action_menu_show_session_info,
                R.id.action_menu_quit, R.id.action_menu_show_saved_results};

        for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull @NotNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_help) {
            String contextHelpMessage = getString(R.string.help_context_body_result_detail_fragment);
            String contextHelpTitle = getString(R.string.help_context_title_result_detail_fragment);

            NavDirections directions =
                    ResultDetailFragmentDirections.
                            actionResultDetailFragmentToHelpFragment(contextHelpMessage, contextHelpTitle);
            NavHostFragment.findNavController(this).navigate(directions);
            return true;
        } else if (itemID == R.id.action_menu_show_saved_results) {
            NavHostFragment.findNavController(this).navigateUp();
            return true;
        } else if (itemID == R.id.action_menu_show_session_info) {
            loadingVisibility(true);
            new Thread(() -> MainActivity.navigateToCurrentSessionInfo(
                    this, session -> {
                        loadingVisibility(false);
                        requireActivity().runOnUiThread(() -> {
                            NavDirections directions = ResultDetailFragmentDirections
                                    .actionResultDetailFragmentToCurrentSessionInfoFragment(session);
                            NavHostFragment.findNavController(ResultDetailFragment.this)
                                    .navigate(directions);
                        });
                    }
            ), "SessionLoadingThread").start();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void loadingVisibility(boolean show) {requireContext().sendBroadcast(
            new Intent().setAction(show ? MainActivity.ACTION_SHOW_LOADING : MainActivity.ACTION_HIDE_LOADING));}

    public enum RecordingListSortBy {
        ID, GROUP, INDEX, RATING
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
                ResultDetailFragment.this.currentSort = sortBy;
            }

            recyclerViewAdapter.notifyDataSetChanged();
        }
    }

}