package com.petrkryze.vas.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.petrkryze.vas.GroupFolder;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.adapters.GroupDirectoryRecyclerViewAdapter;
import com.petrkryze.vas.adapters.GroupDirectoryRecyclerViewAdapter.ViewHolder;
import com.petrkryze.vas.databinding.FragmentGroupControlBinding;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.petrkryze.vas.adapters.GroupDirectoryRecyclerViewAdapter.AdapterListener;
import static com.petrkryze.vas.fragments.RatingFragment.GROUP_CHECK_RESULT_REQUEST_KEY;

/**
 * A fragment representing a list of Items.
 */
public class GroupControlFragment extends VASFragment {

    private static final String TAG = "GroupControlFragment";
    public static final String GroupControlConfirmedKey = "groupControlConfirmedKey";
    public static final String GroupControlSourceRootURI = "sourceRootUri";
    public static final String GroupFolderListSerializedKey = "groupFolderList";

    private FragmentGroupControlBinding binding;
    private RecyclerView groupFoldersListView;
    private Button buttonConfirm;

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
                    e.printStackTrace();
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
        Context context = view.getContext();

        binding.groupControlFoundFolders.setText(getResources().
                getQuantityString(R.plurals.group_control_found_folders_headline,
                        groupFolders.size(), groupFolders.size()));

        buttonConfirm = binding.groupControlButtonConfirm;
        buttonConfirm.setOnClickListener(confirmListener);

        groupFoldersListView = binding.groupControlGroupFolderList;
        GroupDirectoryRecyclerViewAdapter adapter =
                new GroupDirectoryRecyclerViewAdapter(context, groupFolders, adapterListener);
        groupFoldersListView.setAdapter(adapter);
        groupFoldersListView.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context,
                DividerItemDecoration.VERTICAL);
        groupFoldersListView.addItemDecoration(dividerItemDecoration);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private Pair<Boolean, ArrayList<GroupFolder>> checkEditTexts() throws Exception {
        GroupDirectoryRecyclerViewAdapter adapter =
                (GroupDirectoryRecyclerViewAdapter) groupFoldersListView.getAdapter();
        if (adapter == null) throw new NullPointerException("Fatal error! RecyclerView is missing adapter.");
        int nRows = groupFoldersListView.getAdapter().getItemCount();
        if (nRows <= 0) throw new Exception("Fatal error! RecyclerView adapter is empty.");

        EditText firstWrong = null;
        boolean flag = true;
        ArrayList<GroupFolder> selectedFolders = new ArrayList<>();

        for (int i = 0; i < nRows; i++) {
            ViewHolder row = (ViewHolder) groupFoldersListView.findViewHolderForAdapterPosition(i);
            if (row == null) throw new NullPointerException("Fatal error! ViewHolder for row " + i + " is null.");
            if (row.checkBox.isChecked()) {
                String inputText = row.viewRowGroupNameInput.getText().toString();
                Log.d(TAG, "onClick: Edit text on row " + i + ": " + inputText);

                // I fucking hate regex, but well, here we go - it should catch all of these chars
                if (inputText.matches(".*[~#^|$%&*!/<>?\"\\\\].*")) {
                    row.viewRowGroupNameInput.setError(getString(R.string.group_control_invalid_input));
                    if (firstWrong == null) firstWrong = row.viewRowGroupNameInput;
                    flag = false;
                } else { // Input is OK
                    this.groupFolders.get(i).setLabel(inputText);

                    // Put the selected folder with it's name user defined into output, so we don't
                    // change the original found group folders (for safety)
                    selectedFolders.add(this.groupFolders.get(i));
                }
            }
        }
        if (!flag) firstWrong.requestFocus();

        return new Pair<>(flag, selectedFolders);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull @NotNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toDisable = { R.id.action_menu_save, R.id.action_menu_show_saved_results,
                R.id.action_menu_new_session, R.id.action_menu_show_session_info};
        int[] toEnable = {R.id.action_menu_help, R.id.action_menu_quit, R.id.action_menu_settings};

        for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull @NotNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_help) {
            String contextHelpMessage = getString(R.string.help_context_body_group_control_fragment);
            String contextHelpTitle = getString(R.string.help_context_title_group_control_fragment);

            NavDirections directions = GroupControlFragmentDirections.
                            actionGroupControlFragmentToHelpFragment(contextHelpMessage, contextHelpTitle);
            NavHostFragment.findNavController(this).navigate(directions);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}