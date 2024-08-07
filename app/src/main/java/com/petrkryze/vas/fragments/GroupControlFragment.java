package com.petrkryze.vas.fragments;

import static com.petrkryze.vas.adapters.GroupDirectoryRecyclerViewAdapter.AdapterListener;
import static com.petrkryze.vas.fragments.RatingFragment.GROUP_CHECK_RESULT_REQUEST_KEY;

import android.content.Context;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.petrkryze.vas.GroupFolder;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.adapters.GroupDirectoryRecyclerViewAdapter;
import com.petrkryze.vas.databinding.FragmentGroupControlBinding;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;

/**
 * A fragment representing a list of Items.
 */
public class GroupControlFragment extends VASFragment {

    private static final String TAG = "GroupControlFragment";
    public static final String GroupControlConfirmedKey = "groupControlConfirmedKey";
    public static final String GroupControlSourceRootURI = "sourceRootUri";
    public static final String GroupFolderListSerializedKey = "groupFolderList";

    private static final String LABELS_KEY = "labels";

    private FragmentGroupControlBinding binding;
    private RecyclerView groupFoldersListView;
    private Button buttonConfirm;

    private GroupDirectoryRecyclerViewAdapter adapter;

    private URI sourceRootURI;
    private ArrayList<GroupFolder> groupFolders;

    private final View.OnClickListener confirmListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.group_control_button_confirm) {
                Log.d(TAG, "onClick: CONFIRM BUTTON CLICKED");
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                try {
                    Pair<Boolean, ArrayList<GroupFolder>> checkResults = checkEditTexts();
                    if (checkResults.first) {
                        Log.i(TAG, "onClick: Selected folders:\n");
                        for(GroupFolder gf : checkResults.second) Log.i(TAG, "onClick: " + gf.toString());

                        Bundle bundle = new Bundle();
                        bundle.putBoolean(GroupControlConfirmedKey, true);
                        bundle.putSerializable(GroupControlSourceRootURI, sourceRootURI);
                        bundle.putSerializable(GroupFolderListSerializedKey, checkResults.second);

                        getParentFragmentManager()
                                .setFragmentResult(GROUP_CHECK_RESULT_REQUEST_KEY, bundle);
                        NavHostFragment.findNavController(GroupControlFragment.this)
                                .navigateUp();
                    }
                } catch (Exception e) {
                    Log.e(TAG,"Error during folder confirmation and fragment leaving.",e);
                }
            }
        }
    };

    private final AdapterListener adapterListener = new AdapterListener() {
        @Override
        public void onAllUnchecked() {
            if (buttonConfirm != null) buttonConfirm.setEnabled(false);
        }

        @Override
        public void onSomethingChecked() {
            if (buttonConfirm != null) buttonConfirm.setEnabled(true);
        }
    };

    public GroupControlFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("unused")
    public static GroupControlFragment newInstance(ArrayList<GroupFolder> groupFolders) {
        GroupControlFragment fragment = new GroupControlFragment();
        Bundle args = new Bundle();
        args.putSerializable(GroupFolderListSerializedKey, groupFolders);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            sourceRootURI = GroupControlFragmentArgs.fromBundle(getArguments()).getSourceRootUri();
            groupFolders = GroupControlFragmentArgs.fromBundle(getArguments()).getGroupFolders();
        }

        ArrayList<String> labels = null;
        if (savedInstanceState != null) {
            labels = getSerializable(savedInstanceState, LABELS_KEY, ArrayList.class);
        }
        adapter = new GroupDirectoryRecyclerViewAdapter(requireContext(), groupFolders, adapterListener, labels);

        // Important! Set default return bundle, so it can signal there was no confirmation on exit
        // (navigateUp event) to the RatingManager and RatingFragment above in the hierarchy
        Bundle bundle = new Bundle();
        bundle.putBoolean(GroupControlConfirmedKey, false);
        bundle.putSerializable(GroupFolderListSerializedKey, null); // Technically unnecessary
        getParentFragmentManager().setFragmentResult(GROUP_CHECK_RESULT_REQUEST_KEY, bundle);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = FragmentGroupControlBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        setupMenu();

        binding.groupControlFoundFolders.setText(getResources().
                getQuantityString(R.plurals.group_control_found_folders_headline,
                        groupFolders.size(), groupFolders.size()));

        buttonConfirm = binding.groupControlButtonConfirm;
        buttonConfirm.setOnClickListener(confirmListener);

        groupFoldersListView = binding.groupControlGroupFolderList;
        groupFoldersListView.setAdapter(adapter);
        groupFoldersListView.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context,
                DividerItemDecoration.VERTICAL);
        groupFoldersListView.addItemDecoration(dividerItemDecoration);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        GroupDirectoryRecyclerViewAdapter adapter =
                (GroupDirectoryRecyclerViewAdapter) groupFoldersListView.getAdapter();
        if (adapter == null) throw new NullPointerException("Fatal error! RecyclerView is missing adapter.");
        outState.putSerializable(LABELS_KEY, adapter.getLabels());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        adapter = null;
    }

    @Override
    public void onPause() {
        adapter.releaseFocus();
        super.onPause();
    }

    private Pair<Boolean, ArrayList<GroupFolder>> checkEditTexts() throws Exception {
        GroupDirectoryRecyclerViewAdapter adapter =
                (GroupDirectoryRecyclerViewAdapter) groupFoldersListView.getAdapter();
        if (adapter == null) throw new NullPointerException("Fatal error! RecyclerView is missing adapter.");
        int nRows = groupFoldersListView.getAdapter().getItemCount();
        if (nRows <= 0) throw new Exception("Fatal error! RecyclerView adapter is empty.");

        boolean checkOK = true;
        ArrayList<GroupFolder> selectedFolders = new ArrayList<>();

        for (int i = 0; i < nRows; i++) {
            if (adapter.getChecks().get(i)) {
                String inputText = adapter.getLabels().get(i);
                Log.d(TAG, "onClick: Edit text on row " + i + ": " + inputText);

                // I fucking hate regex, but well, here we go - it should catch all of these chars
                if (inputText.isEmpty()) {
                    groupFoldersListView.scrollToPosition(i);
                    adapter.setError(i, getString(R.string.group_control_empty_input));
                    checkOK = false;
                } else if (inputText.matches(".*[~#^|$%&*!/<>?\"\\\\].*")) {
                    groupFoldersListView.scrollToPosition(i);
                    adapter.setError(i, getString(R.string.group_control_invalid_input));
                    checkOK = false;
                } else { // Input is OK
                    this.groupFolders.get(i).setLabel(inputText);

                    // Put the selected folder with it's name user defined into output, so we don't
                    // change the original found group folders (for safety)
                    selectedFolders.add(this.groupFolders.get(i));
                }
            }
        }

        return new Pair<>(checkOK, selectedFolders);
    }

    private void setupMenu(){
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuProvider.super.onPrepareMenu(menu);
                int[] toDisable = { R.id.action_menu_save, R.id.action_menu_show_saved_results,
                        R.id.action_menu_new_session, R.id.action_menu_show_session_info};
                int[] toEnable = {R.id.action_menu_help, R.id.action_menu_quit, R.id.action_menu_settings};

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
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void onShowHelp() {
        String contextHelpTitle = getString(R.string.help_context_title_group_control_fragment);
        String[] contextHelpDescriptions = getResources().getStringArray(R.array.help_tag_description_group_control_fragment);
        String contextHelpBody = getString(R.string.help_context_body_group_control_fragment);

        NavDirections directions = GroupControlFragmentDirections.
                actionGroupControlFragmentToHelpFragment(contextHelpTitle, contextHelpDescriptions,
                        contextHelpBody, R.drawable.help_screen_group_check_fragment);
        NavHostFragment.findNavController(this).navigate(directions);
    }

}