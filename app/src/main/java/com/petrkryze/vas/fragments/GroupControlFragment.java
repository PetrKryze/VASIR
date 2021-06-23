package com.petrkryze.vas.fragments;

import android.content.Context;
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
import android.widget.TextView;

import com.petrkryze.vas.GroupFolder;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.adapters.GroupDirectoryRecyclerViewAdapter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A fragment representing a list of Items.
 */
public class GroupControlFragment extends Fragment {
    private static final String TAG = "GroupControlFragment";
    public static final String GroupFolderListSerializedKey = "groupFolderList";

    private ArrayList<GroupFolder> groupFolders;

    private Vibrator vibrator;
    public static int VIBRATE_BUTTON_MS;

    private final View.OnClickListener confirmListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.group_control_button_confirm) {
                Log.i(TAG, "onClick: CONFIRM BUTTON CLICKED");
                vibrator.vibrate(VibrationEffect.createOneShot(
                        VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));


                // TODO confirm the folders and move on
                // oh god oh fuck

                Bundle bundle = new Bundle();
                bundle.putSerializable(GroupFolderListSerializedKey,
                        GroupControlFragment.this.groupFolders); // TODO is this okay?
                // TODO Maybe make the change of groupFolders differently?

                getParentFragmentManager()
                        .setFragmentResult(RatingManager.GROUP_CHECK_RESULT_REQUEST_KEY, bundle);
                NavHostFragment.findNavController(GroupControlFragment.this)
                        .navigateUp();
            }
        }
    };


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public GroupControlFragment() {
    }

    @SuppressWarnings("unused")
    public static GroupControlFragment newInstance(ArrayList<GroupFolder> groupFolders) {
        GroupControlFragment fragment = new GroupControlFragment();
        Bundle args = new Bundle();
        args.putSerializable(GroupFolderListSerializedKey, groupFolders);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (getArguments() != null) {
            //noinspection unchecked
            groupFolders = GroupControlFragmentArgs.fromBundle(getArguments()).getGroupFolders();
        }

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_control_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        TextView foundFoldersHeader = view.findViewById(R.id.group_control_found_folders);
        foundFoldersHeader.setText(getString(R.string.group_control_found_folders_headline, groupFolders.size()));

        Button buttonConfirm = view.findViewById(R.id.group_control_button_confirm);
        buttonConfirm.setOnClickListener(confirmListener);

        RecyclerView groupFoldersListView = view.findViewById(R.id.group_control_group_folder_list);
        groupFoldersListView.setAdapter(new GroupDirectoryRecyclerViewAdapter(context,
                groupFolders));
        groupFoldersListView.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(context,
                DividerItemDecoration.VERTICAL);
        groupFoldersListView.addItemDecoration(dividerItemDecoration);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull @NotNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toDisable = { R.id.action_menu_save, R.id.action_menu_show_saved_results,
                R.id.action_menu_reset_ratings, R.id.action_menu_show_session_info};
        int[] toEnable = {R.id.action_menu_help, R.id.action_menu_quit};

        for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull @NotNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_help) {
            String contextHelpMessage = getString(R.string.help_context_body_group_control_fragment);
            String contextHelpTitle = getString(R.string.help_context_title_group_control_fragment);

            NavDirections directions =
                    GroupControlFragmentDirections.
                            actionGroupControlFragmentToHelpFragment(contextHelpMessage, contextHelpTitle);
            NavHostFragment.findNavController(this).navigate(directions);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}