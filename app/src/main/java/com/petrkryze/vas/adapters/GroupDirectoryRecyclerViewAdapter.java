package com.petrkryze.vas.adapters;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.petrkryze.vas.GroupFolder;
import com.petrkryze.vas.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GroupFolder}.
 */
public class GroupDirectoryRecyclerViewAdapter extends RecyclerView.Adapter<GroupDirectoryRecyclerViewAdapter.ViewHolder> {
    // private static final String TAG = "GroupDirectoryRecyclerViewAdapter";
    private final Context context;
    private final List<GroupFolder> groupFolders;

    private final ArrayList<Boolean> checks;
    private final ArrayList<String> labels;
    private final AdapterListener adapterListener;

    private final ArrayList<ViewHolder> viewHolders;

    private View focusedView;

    public interface AdapterListener {
        void onAllUnchecked();
        void onSomethingChecked();
    }

    public GroupDirectoryRecyclerViewAdapter(Context context, List<GroupFolder> groupFolders,
                                             AdapterListener listener, ArrayList<String> inLabels) {
        this.context = context;
        this.groupFolders = groupFolders;

        Boolean[] checksArray = new Boolean[groupFolders.size()];
        Arrays.fill(checksArray, true);
        this.checks = new ArrayList<>(Arrays.asList(checksArray));
        this.adapterListener = listener;

        if (inLabels != null && !inLabels.isEmpty() && inLabels.size() == groupFolders.size()) {
            this.labels = inLabels;
        } else {
            this.labels = new ArrayList<>(groupFolders.size());
            for (GroupFolder gf : groupFolders) labels.add(gf.getLabel());
        }

        this.viewHolders = new ArrayList<>(groupFolders.size());
        for (GroupFolder ignored : groupFolders) viewHolders.add(null);
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

        holder.viewRowFolderName.setText(context.getString(R.string.group_control_group_folder_list_row_folder_name, currentGFolder.getFolderName()));
        holder.viewRowAudioFilesFound.setText(
                context.getResources().getQuantityString(
                        R.plurals.group_control_group_folder_list_row_found_audio_files,
                        currentGFolder.getNaudioFiles(), currentGFolder.getNaudioFiles())
        );

        holder.viewRowGroupNameInput.setText(currentGFolder.getLabel(),
                TextView.BufferType.EDITABLE);
        holder.viewRowGroupNameInput.addTextChangedListener(new TextChangedListener(position));
        holder.viewRowGroupNameInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) focusedView = v;
        });

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checks.set(position, isChecked);
            if (isSomethingChecked()) {
                adapterListener.onSomethingChecked();
            } else {
                adapterListener.onAllUnchecked();
            }
        });

        viewHolders.add(position, holder);
    }

    @Override
    public int getItemCount() {
        return groupFolders.size();
    }

    public ArrayList<Boolean> getChecks() {
        return checks;
    }

    public ArrayList<String> getLabels() {
        return labels;
    }

    public void setError(int position, String error) {
        viewHolders.get(position).viewRowGroupNameInput.setError(error);
    }

    public void releaseFocus() {
        if (focusedView != null) {
            focusedView.clearFocus();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final ConstraintLayout container;
        public final TextView viewRowFolderName;
        public final TextView viewRowAudioFilesFound;
        public final EditText viewRowGroupNameInput;
        public final MaterialCheckBox checkBox;
        public GroupFolder mItem;

        public ViewHolder(@NotNull View view) {
            super(view);
            mView = view;
            container = view.findViewById(R.id.group_control_list_row);
            viewRowFolderName = view.findViewById(R.id.group_control_group_folder_list_row_folder_name);
            viewRowAudioFilesFound = view.findViewById(R.id.group_control_group_folder_list_row_found_audio_files);
            viewRowGroupNameInput = view.findViewById(R.id.group_control_group_folder_list_row_group_name_input);
            checkBox = view.findViewById(R.id.group_control_group_folder_list_row_checkbox);
        }

        @NotNull
        @Override
        public String toString() {
            return super.toString() + " Folder name: " + viewRowFolderName.getText() + ", " +
                    "Audio files found: " + viewRowAudioFilesFound.getText() + ", " +
                    "Group name: " + viewRowGroupNameInput.getText();
        }
    }

    public class TextChangedListener implements TextWatcher {
        private final int position;

        public TextChangedListener(int position) {
            this.position = position;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            labels.set(position, s.toString());
        }
    }

    public boolean isSomethingChecked() {
        return checks.contains(true);
    }
}