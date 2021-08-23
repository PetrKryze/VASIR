package com.petrkryze.vas.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.adapters.HelpDescriptionsRecyclerViewAdapter;
import com.petrkryze.vas.databinding.FragmentHelpBinding;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HelpFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HelpFragment extends VASFragment {
    private static final String KEY_HELP_TITLE = "help_title";
    private static final String KEY_HELP_DESCRIPTIONS = "help_descriptions";
    private static final String KEY_HELP_BODY = "help_body";
    private static final String KEY_HELP_IMAGE_RESOURCE = "help_image_resource";

    private FragmentHelpBinding binding;
    private String helpTitle;
    private String[] helpDescriptions;
    private String helpBody;
    private int helpImageResource;

    public HelpFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("unused")
    public static HelpFragment newInstance(String title, String[] descriptions, String body,
                                           int imageResource) {
        HelpFragment fragment = new HelpFragment();
        Bundle args = new Bundle();
        args.putString(KEY_HELP_TITLE, title);
        args.putStringArray(KEY_HELP_DESCRIPTIONS, descriptions);
        args.putString(KEY_HELP_BODY, body);
        args.putInt(KEY_HELP_IMAGE_RESOURCE, imageResource);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            helpTitle = HelpFragmentArgs.fromBundle(getArguments()).getContextTitle();
            helpDescriptions = HelpFragmentArgs.fromBundle(getArguments()).getContextDescriptions();
            helpBody = HelpFragmentArgs.fromBundle(getArguments()).getContextBody();
            helpImageResource = HelpFragmentArgs.fromBundle(getArguments()).getContextImageResource();
        }
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = FragmentHelpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.helpTitleTextview.setText(helpTitle);

        RecyclerView list = binding.helpImageviewDescriptions;
        list.setAdapter(new HelpDescriptionsRecyclerViewAdapter(
                requireContext(), Arrays.asList(helpDescriptions)));
        list.setLayoutManager(new LinearLayoutManager(view.getContext()));

        binding.helpBodyTextview.setText(helpBody);
        binding.helpImageview.setImageDrawable(
                ResourcesCompat.getDrawable(getResources(), helpImageResource, null));
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
            onShowSavedResults(results -> {
                NavDirections directions =
                        HelpFragmentDirections.actionHelpFragmentToResultFragment(results);
                NavHostFragment.findNavController(this).navigate(directions);
            });
            return true;
        } else if (itemID == R.id.action_menu_show_session_info) {
            onShowSessionInfo(session -> {
                NavDirections directions = HelpFragmentDirections
                        .actionHelpFragmentToCurrentSessionInfoFragment(session);
                NavHostFragment.findNavController(HelpFragment.this).navigate(directions);
            });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}