package com.petrkryze.vas.adapters;

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
import android.widget.Toast;

import com.petrkryze.vas.R;
import com.petrkryze.vas.Recording;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by Petr on 05.05.2021. Yay!
 */
public class RecordingsRecyclerViewAdapter extends RecyclerView.Adapter<RecordingsRecyclerViewAdapter.ViewHolder> {

    private final Context context;
    private final List<Recording> recordings;

    private final int VIBRATE_LIST_ITEM_SELECT;

    public RecordingsRecyclerViewAdapter(Context context, List<Recording> recordings) {
        this.context = context;
        this.recordings = recordings;

        VIBRATE_LIST_ITEM_SELECT = context.getResources().getInteger(R.integer.VIBRATE_LONG_CLICK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.result_detail_recordings_list_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final Recording currentRecording = recordings.get(position);
        holder.mItem = currentRecording;

        String id = currentRecording.getID();
        String group = currentRecording.getGroupType().toString();
        String index = String.valueOf(currentRecording.getRandomized_number());
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

            Toast.makeText(context, context.getString(R.string.result_detail_copy_copied), Toast.LENGTH_SHORT).show();
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
