package com.petrkryze.vas;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ResultDetailFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ResultDetailFragment extends Fragment {

    // private static final String TAG = "ResultDetailFragment";
    private static final String RESULT_TO_DISPLAY = "result_to_display";

    private RatingResult resultToDisplay;

    private final View.OnClickListener shareListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
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
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.result_detail_fragment, container, false);
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
        RecyclerView recordingListView = view.findViewById(R.id.result_detail_recordings_list);

        TWresultDetailID.setText(getString(R.string.result_detail_sessionID, resultToDisplay.getSession_ID()));
        TWresultDetailSaveDate.setText(formatDateTime(resultToDisplay.getSaveDate()));
        TWresultDetailSeed.setText(String.valueOf(resultToDisplay.getSeed()));
        TWresultDetailGeneratorDate.setText(resultToDisplay.getGeneratorMessage().replace("-",". "));

        recordingListView.setAdapter(
                new RecordingsRecyclerViewAdapter(context, resultToDisplay.getRecordings()));
        recordingListView.setLayoutManager(new LinearLayoutManager(context));
    }

    private String formatDateTime(String raw) {
        String[] split1 = raw.split("_");

        List<String> splitDate = Arrays.asList(split1[0].split("-"));
        Collections.reverse(splitDate);

        String date = TextUtils.join(". ", splitDate);
        return date + " " + split1[1];
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
        }
        return super.onOptionsItemSelected(item);
    }
}