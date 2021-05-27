package com.petrkryze.vas;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * {@link RecyclerView.Adapter} that can display a {@link RatingResult}.
 */
public class ResultsRecyclerViewAdapter extends RecyclerView.Adapter<ResultsRecyclerViewAdapter.ViewHolder> {

    private final Context context;
    private final List<RatingResult> ratingResults;
    private final ResultsFragment.OnItemDetailListener onItemDetailListener;

    public ResultsRecyclerViewAdapter(Context context, List<RatingResult> items,
                                      ResultsFragment.OnItemDetailListener onItemDetailListener) {
        this.context = context;
        this.ratingResults = items;
        this.onItemDetailListener = onItemDetailListener;
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.result_fragment_result_list_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final RatingResult currentResult = ratingResults.get(position);
        holder.mItem = currentResult;

        holder.viewRowNumber.setText(String.valueOf(position + 1));
        holder.viewSessionID.setText(context.getString(R.string.rating_list_row_sessionID, currentResult.getSession_ID()));
        holder.viewSaveDate.setText(formatDateTime(currentResult.getSaveDate()));

        holder.shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                String shareSubject = context.getString(R.string.share_subject, currentResult.getSession_ID());
                sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, shareSubject);
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, currentResult.getRawContent());

                context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.share_using)));
            }
        });

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onItemDetailListener.onItemClick(currentResult);
            }
        });
    }

    @Override
    public int getItemCount() {
        return ratingResults.size();
    }

    private String formatDateTime(String raw) {
        String[] split1 = raw.split("_");

        List<String> splitDate = Arrays.asList(split1[0].split("-"));
        Collections.reverse(splitDate);

        String date = TextUtils.join(". ", splitDate);
        return date + " - " + split1[1];
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView viewRowNumber;
        public final TextView viewSessionID;
        public final TextView viewSaveDate;
        public final ImageButton shareButton;
        public RatingResult mItem;

        public ViewHolder(@NonNull View view) {
            super(view);
            mView = view;
            viewRowNumber = view.findViewById(R.id.result_list_row_number);
            viewSessionID = view.findViewById(R.id.result_list_row_sessionid);
            viewSaveDate = view.findViewById(R.id.result_list_row_date);
            shareButton = view.findViewById(R.id.shareButton);
        }

        @NotNull
        @Override
        public String toString() {
            return super.toString() + " Row number: " + viewRowNumber.getText() + ", " +
                    "Session ID: " + viewSessionID.getText() + ", " +
                    "Save date: " + viewSaveDate.getText();
        }
    }
}