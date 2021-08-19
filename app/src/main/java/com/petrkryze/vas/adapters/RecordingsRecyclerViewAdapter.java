package com.petrkryze.vas.adapters;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.R;
import com.petrkryze.vas.Recording;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import static com.petrkryze.vas.adapters.RecordingsRecyclerViewAdapter.ViewHolder;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Recording}.
 */
public class RecordingsRecyclerViewAdapter extends RecyclerView.Adapter<ViewHolder> {

    private final Context context;
    private final List<Recording> recordings;

    private final int VIBRATE_LIST_ITEM_SELECT;

    public RecordingsRecyclerViewAdapter(Context context, List<Recording> recordings) {
        this.context = context;
        this.recordings = recordings;

        VIBRATE_LIST_ITEM_SELECT = context.getResources().getInteger(R.integer.VIBRATE_LONG_CLICK_MS);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recordings_list_row, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("ShowToast")
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final Recording currentRecording = recordings.get(position);
        holder.mItem = currentRecording;

        String id = currentRecording.getID();
        String group = currentRecording.getGroupName();
        String index = String.valueOf(currentRecording.getRandomIndex());
        String rating = currentRecording.getRating() == -1 ? "-" : String.valueOf(currentRecording.getRating());

        holder.viewID.setText(id);
        holder.viewGroup.setText(group);
        holder.viewIndex.setText(index);
        holder.viewRating.setText(rating);

        holder.mView.setOnLongClickListener(v -> {
            holder.mView.requestFocus();
            holder.mView.playSoundEffect(SoundEffectConstants.CLICK);
            ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE)).
                    vibrate(VibrationEffect.createOneShot(VIBRATE_LIST_ITEM_SELECT, VibrationEffect.DEFAULT_AMPLITUDE));

            ClipboardManager clipboardManager = context.getSystemService(ClipboardManager.class);
            ClipData clip = ClipData.newPlainText("Recording data",
                    context.getString(R.string.result_detail_copy, id, group, index, rating));
            clipboardManager.setPrimaryClip(clip);

            Snackbar.make(context, holder.mView,
                    context.getString(R.string.result_detail_copy_copied),
                    BaseTransientBottomBar.LENGTH_SHORT)
                    .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView viewID;
        public final TextView viewGroup;
        public final TextView viewIndex;
        public final TextView viewRating;
        public Recording mItem;

        public ViewHolder(@NonNull View view) {
            super(view);
            mView = view;
            viewID = view.findViewById(R.id.result_detail_recording_list_row_id);
            viewGroup = view.findViewById(R.id.result_detail_recording_list_row_group);
            viewIndex = view.findViewById(R.id.result_detail_recording_list_row_index);
            viewRating = view.findViewById(R.id.result_detail_recording_list_row_rating);
        }

        @NotNull
        @Override
        public String toString() {
            return super.toString() + " ID: " + viewID.getText() + ", " +
                    "Group: " + viewGroup.getText() + ", " +
                    "Index: " + viewIndex.getText() + ", " +
                    "Rating: " + viewRating.getText();
        }
    }
}
