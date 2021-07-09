package com.petrkryze.vas.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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
import android.widget.TextView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
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

import static com.petrkryze.vas.fragments.ResultDetailFragment.RecordingListSortBy;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CurrentSessionInfoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CurrentSessionInfoFragment extends Fragment {
    private static final String TAG = "CurrentSessionInfoFragment";
    private static final String CURRENT_SESSION = "current_session";

    private RecordingListSortBy currentSort = RecordingListSortBy.GROUP; // Default sort

    private RatingResult currentSession;
    private List<Recording> recordingsToDisplay;
    private RecordingsRecyclerViewAdapter recyclerViewAdapter;
    private Button buttonShare;

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
            String shareSubject = context.getString(R.string.share_subject, currentSession.getSession_ID());
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
            sharingIntent.putExtra(Intent.EXTRA_TEXT, currentSession.getRawContent());

            context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.share_using)));
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
        setHasOptionsMenu(true);

        if (getArguments() != null) {
            currentSession = CurrentSessionInfoFragmentArgs.fromBundle(getArguments()).getRatingResult();
            recordingsToDisplay = new ArrayList<>(currentSession.getRecordings());
            recordingsToDisplay.sort(Recording.sortByGroup);
        }

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_current_session_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        buttonShare = view.findViewById(R.id.current_session_info_button_share);
        buttonShare.setOnClickListener(shareListener);

        TextView TWcurrentSessionID = view.findViewById(R.id.current_session_info_sessionID);
        TextView TWcurrentSessionRatedFraction = view.findViewById(R.id.current_session_info_rated_fraction);
        TextView TWcurrentSessionSeed = view.findViewById(R.id.current_session_info_seed);
        TextView TWcurrentSessionGeneratorDate = view.findViewById(R.id.current_session_info_generator_date);

        TWcurrentSessionID.setText(getString(R.string.current_session_info_sessionID, currentSession.getSession_ID()));
        TWcurrentSessionRatedFraction.setText(currentSession.getRatedFractionString());
        TWcurrentSessionSeed.setText(String.valueOf(currentSession.getSeed()));
        TWcurrentSessionGeneratorDate.setText(currentSession.getGeneratorMessage().replace("-","."));

        RecyclerView recordingListView = view.findViewById(R.id.current_session_info_recordings_list);
        recyclerViewAdapter = new RecordingsRecyclerViewAdapter(context, recordingsToDisplay);
        recordingListView.setAdapter(recyclerViewAdapter);
        recordingListView.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context,
                DividerItemDecoration.VERTICAL);
        recordingListView.addItemDecoration(dividerItemDecoration);

        headerID = view.findViewById(R.id.current_session_info_recording_list_column_id);
        headerGroup = view.findViewById(R.id.current_session_info_recording_list_column_group);
        headerIndex = view.findViewById(R.id.current_session_info_recording_list_column_index);
        headerRating = view.findViewById(R.id.current_session_info_recording_list_column_rating);

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
            try {
                ArrayList<RatingResult> ratings = RatingManager.loadResults(requireContext());

                NavDirections directions =
                        CurrentSessionInfoFragmentDirections.actionCurrentSessionInfoFragmentToResultFragment(ratings);
                NavHostFragment.findNavController(this).navigate(directions);
            } catch (Exception e) {
                e.printStackTrace();
                Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                        Html.fromHtml(getString(R.string.snackbar_ratings_loading_failed, e.getMessage()),Html.FROM_HTML_MODE_LEGACY),
                        BaseTransientBottomBar.LENGTH_LONG)
                        .setAnchorView(buttonShare)
                        .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                CurrentSessionInfoFragment.this.currentSort = sortBy;
            }

            recyclerViewAdapter.notifyDataSetChanged();
        }
    }
}