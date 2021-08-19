package com.petrkryze.vas.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.databinding.FragmentHelpBinding;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import static com.petrkryze.vas.MainActivity.html;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HelpFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HelpFragment extends Fragment {
    private static final String KEY_HELP_TITLE = "help_title";
    private static final String KEY_HELP_CONTEXT_MSG = "help_body";

    private FragmentHelpBinding binding;
    private String helpTitle;
    private String helpBody;

    public HelpFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("unused")
    public static HelpFragment newInstance(String title, String contextMessage) {
        HelpFragment fragment = new HelpFragment();
        Bundle args = new Bundle();
        args.putString(KEY_HELP_TITLE, title);
        args.putString(KEY_HELP_CONTEXT_MSG, contextMessage);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            helpTitle = HelpFragmentArgs.fromBundle(getArguments()).getContextTitle();
            helpBody = HelpFragmentArgs.fromBundle(getArguments()).getContextBody();
        }
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        binding = FragmentHelpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.helpTitleTextview.setText(helpTitle);
        binding.helpBodyTextview2.setText(helpBody);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toDisable = {R.id.action_menu_help, R.id.action_menu_save,
                R.id.action_menu_new_session};
        int[] toEnable = {R.id.action_menu_show_saved_results, R.id.action_menu_quit,
                R.id.action_menu_show_session_info, R.id.action_menu_settings};

        for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
    }

    @SuppressLint("ShowToast")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_show_saved_results) {
            onShowSavedResults();
            return true;
        } else if (itemID == R.id.action_menu_show_session_info) {
            onShowSessionInfo();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("ShowToast")
    private void onShowSavedResults() {
        loadingVisibility(true);
        new Thread(() -> { // Threading for slow loading times
            try {
                ArrayList<RatingResult> ratings = RatingManager.loadResults(requireContext());

                requireActivity().runOnUiThread(() -> {
                    loadingVisibility(false);
                    NavDirections directions =
                            HelpFragmentDirections.actionHelpFragmentToResultFragment(ratings);
                    NavHostFragment.findNavController(this).navigate(directions);
                });
            } catch (Exception e) {
                e.printStackTrace();

                requireActivity().runOnUiThread(() -> {
                    loadingVisibility(false);
                    String message = html(getString(R.string.snackbar_ratings_loading_failed, e.getMessage()));
                    Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                            message, BaseTransientBottomBar.LENGTH_LONG)
                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
                });
            }
        }, "ResultsLoadingThread").start();
    }

    private void onShowSessionInfo() {
        loadingVisibility(true);
        new Thread(() ->
                MainActivity.navigateToCurrentSessionInfo(this,
                        session -> requireActivity().runOnUiThread(() -> {
                            loadingVisibility(false);
                            NavDirections directions = HelpFragmentDirections
                                    .actionHelpFragmentToCurrentSessionInfoFragment(session);
                            NavHostFragment.findNavController(HelpFragment.this)
                                    .navigate(directions);
                        })
                ), "SessionLoadingThread").start();
    }

    private void loadingVisibility(boolean show) {
        requireActivity().findViewById(R.id.general_loading_container)
                .setVisibility(show ? View.VISIBLE : View.GONE);
    }

}