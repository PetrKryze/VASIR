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
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.TextView;

import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.Recording;
import com.petrkryze.vas.adapters.RecordingsRecyclerViewAdapter;

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
import androidx.recyclerview.widget.RecyclerView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ResultDetailFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ResultDetailFragment extends Fragment {
    private static final String TAG = "ResultDetailFragment";
    private static final String RESULT_TO_DISPLAY = "result_to_display";

    private RecordingListSortBy currentSort = RecordingListSortBy.GROUP; // Default sort

    private RatingResult resultToDisplay;
    private List<Recording> recordingsToDisplay;
    private RecordingsRecyclerViewAdapter recyclerViewAdapter;

    private CheckedTextView headerID;
    private CheckedTextView headerGroup;
    private CheckedTextView headerIndex;
    private CheckedTextView headerRating;

    private Vibrator vibrator;
    private static int VIBRATE_BUTTON_MS;

    private final View.OnClickListener shareListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: SHARE BUTTON CLICKED");
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
        setHasOptionsMenu(true);

        if (getArguments() != null) {
            resultToDisplay = ResultDetailFragmentArgs.fromBundle(getArguments()).getRatingResult();
            recordingsToDisplay = new ArrayList<>(resultToDisplay.getRecordings());
            recordingsToDisplay.sort(Recording.sortByGroup);
        }

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_result_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        Button buttonShare = view.findViewById(R.id.button_result_detail_share);
        buttonShare.setOnClickListener(shareListener);

        TextView TWresultDetailID = view.findViewById(R.id.result_detail_sessionID);
        TextView TWresultDetailSaveDate = view.findViewById(R.id.result_detail_save_date);
        TextView TWresultDetailSeed = view.findViewById(R.id.result_detail_seed);
        TextView TWresultDetailGeneratorDate = view.findViewById(R.id.result_detail_generator_date);

        TWresultDetailID.setText(getString(R.string.result_detail_sessionID, resultToDisplay.getSession_ID()));
        TWresultDetailSaveDate.setText(formatDateTime(resultToDisplay.getSaveDate()));
        TWresultDetailSeed.setText(String.valueOf(resultToDisplay.getSeed()));
        TWresultDetailGeneratorDate.setText(resultToDisplay.getGeneratorMessage().replace("-","."));

        RecyclerView recordingListView = view.findViewById(R.id.result_detail_recordings_list);
        recyclerViewAdapter = new RecordingsRecyclerViewAdapter(context, recordingsToDisplay);
        recordingListView.setAdapter(recyclerViewAdapter);
        recordingListView.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context,
                DividerItemDecoration.VERTICAL);
        recordingListView.addItemDecoration(dividerItemDecoration);

        headerID = view.findViewById(R.id.result_detail_recording_list_column_id);
        headerGroup = view.findViewById(R.id.result_detail_recording_list_column_group);
        headerIndex = view.findViewById(R.id.result_detail_recording_list_column_index);
        headerRating = view.findViewById(R.id.result_detail_recording_list_column_rating);

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
            MainActivity.navigateToCurrentSessionInfo(
                    this, session -> {
                        NavDirections directions = ResultDetailFragmentDirections
                                .actionResultDetailFragmentToCurrentSessionInfoFragment(session);
                        NavHostFragment.findNavController(ResultDetailFragment.this)
                                .navigate(directions);
                    }
            );
        }

        return super.onOptionsItemSelected(item);
    }

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
            Log.i(TAG, "onClick: COLUMN SORT BY " + sortBy + " BUTTON CLICKED");
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