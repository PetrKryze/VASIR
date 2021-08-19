package com.petrkryze.vas.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.fragments.ResultsFragment;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.RecyclerView;

import static com.petrkryze.vas.adapters.ResultsRecyclerViewAdapter.ViewHolder;

/**
 * {@link RecyclerView.Adapter} that can display a {@link RatingResult}.
 */
public class ResultsRecyclerViewAdapter extends RecyclerView.Adapter<ViewHolder> {

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
                .inflate(R.layout.results_list_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final RatingResult currentResult = ratingResults.get(position);
        holder.mItem = currentResult;

        String index = (position + 1) >= 10 ? String.valueOf(position + 1) : "0" + (position + 1);
        holder.viewRowNumber.setText(index);
        holder.viewSessionID.setText(context.getString(R.string.rating_list_row_sessionID, currentResult.getSessionID()));

        Pair<String, String> dateTime = formatDateTime(currentResult.getSaveDate());
        holder.viewSaveDate.setText(dateTime.first);
        holder.viewSaveTime.setText(dateTime.second);

        holder.mView.setOnClickListener(v -> onItemDetailListener.onItemClick(currentResult));
        holder.mView.setOnLongClickListener(v -> onItemDetailListener.onItemLongClick(currentResult, position));

        holder.container.setBackground(AppCompatResources.getDrawable(context, R.drawable.result_list_item_background));
    }

    @Override
    public int getItemCount() {
        return ratingResults.size();
    }

    private Pair<String, String> formatDateTime(String raw) {
        String[] split = raw.split(" ");
        return new Pair<>(split[0].replace("-", "."), split[1]);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final ConstraintLayout container;
        public final TextView viewRowNumber;
        public final TextView viewSessionID;
        public final TextView viewSaveDate;
        public final TextView viewSaveTime;
        public RatingResult mItem;

        public ViewHolder(@NonNull View view) {
            super(view);
            mView = view;
            container = view.findViewById(R.id.result_list_row);
            viewRowNumber = view.findViewById(R.id.result_list_row_number);
            viewSessionID = view.findViewById(R.id.result_list_row_sessionid);
            viewSaveDate = view.findViewById(R.id.result_list_row_date);
            viewSaveTime = view.findViewById(R.id.result_list_row_time);
        }

        @NotNull
        @Override
        public String toString() {
            return super.toString() + " Row number: " + viewRowNumber.getText() + ", " +
                    "com.petrkryze.vas.Session ID: " + viewSessionID.getText() + ", " +
                    "Save date: " + viewSaveDate.getText() + ", " +
                    "Save time: " + viewSaveTime.getText();
        }
    }
}