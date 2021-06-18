package com.petrkryze.vas.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.adapters.RecordingsRecyclerViewAdapter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CurrentSessionInfoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CurrentSessionInfoFragment extends Fragment {

    private static final String CURRENT_SESSION = "current_session";

    private RatingResult currentSession;

    private final View.OnClickListener shareListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
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
        }
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

        Button buttonShare = view.findViewById(R.id.button_current_session_info_share);
        buttonShare.setOnClickListener(shareListener);

        TextView TWcurrentSessionID = view.findViewById(R.id.current_session_info_sessionID);
        TextView TWcurrentSessionRatedFraction = view.findViewById(R.id.current_session_info_rated_fraction);
        TextView TWcurrentSessionSeed = view.findViewById(R.id.current_session_info_seed);
        TextView TWcurrentSessionGeneratorDate = view.findViewById(R.id.current_session_info_generator_date);
        RecyclerView recordingListView = view.findViewById(R.id.current_session_info_recordings_list);
        
        TWcurrentSessionID.setText(getString(R.string.current_session_info_sessionID, currentSession.getSession_ID()));
        TWcurrentSessionRatedFraction.setText(currentSession.getRatedFractionString());
        TWcurrentSessionSeed.setText(String.valueOf(currentSession.getSeed()));
        TWcurrentSessionGeneratorDate.setText(currentSession.getGeneratorMessage().replace("-",". "));

        recordingListView.setAdapter(
                new RecordingsRecyclerViewAdapter(context, currentSession.getRecordings()));
        recordingListView.setLayoutManager(new LinearLayoutManager(context));
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
                Toast.makeText(requireContext(), getString(R.string.ratings_loading_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}