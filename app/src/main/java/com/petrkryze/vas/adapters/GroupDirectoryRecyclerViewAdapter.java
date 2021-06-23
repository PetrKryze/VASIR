package com.petrkryze.vas.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.petrkryze.vas.GroupFolder;
import com.petrkryze.vas.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GroupFolder}.
 */
public class GroupDirectoryRecyclerViewAdapter extends RecyclerView.Adapter<GroupDirectoryRecyclerViewAdapter.ViewHolder> {

    private final Context context;
    private final List<GroupFolder> groupFolders;

    public GroupDirectoryRecyclerViewAdapter(Context context, List<GroupFolder> groupFolders) {
        this.context = context;
        this.groupFolders = groupFolders;
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.group_control_list_row, parent, false);
        return new ViewHolder(view);

    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final GroupFolder currentGFolder = groupFolders.get(position);
        holder.mItem = currentGFolder;

        holder.viewRowFolderName.setText(currentGFolder.getFolderName());
        holder.viewRowAudioFilesFound.setText(
                context.getString(R.string.group_control_group_folder_list_row_found_audio_files, currentGFolder.getNaudioFiles())
        );

        holder.viewRowGroupNameInput.setText(currentGFolder.getLabel(),
                TextView.BufferType.EDITABLE);

        // TODO anything else to do with this edit text??

    }

    @Override
    public int getItemCount() {
        return groupFolders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final ConstraintLayout container;
        public final TextView viewRowFolderName;
        public final TextView viewRowAudioFilesFound;
        public final EditText viewRowGroupNameInput;
        public GroupFolder mItem;

        public ViewHolder(@NotNull View view) {
            super(view);
            mView = view;
            container = view.findViewById(R.id.group_control_list_row);
            viewRowFolderName = view.findViewById(R.id.group_control_group_folder_list_row_folder_name);
            viewRowAudioFilesFound = view.findViewById(R.id.group_control_group_folder_list_row_found_audio_files);
            viewRowGroupNameInput = view.findViewById(R.id.group_control_group_folder_list_row_group_name_input);
        }

        @NotNull
        @Override
        public String toString() {
            return super.toString() + " Folder name: " + viewRowFolderName.getText() + ", " +
                    "Audio files found: " + viewRowAudioFilesFound.getText() + ", " +
                    "Group name: " + viewRowGroupNameInput.getText();
        }
    }
}