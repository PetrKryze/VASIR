package com.petrkryze.vas.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.petrkryze.vas.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import static com.petrkryze.vas.adapters.HelpDescriptionsRecyclerViewAdapter.ViewHolder;

/**
 * Created by Petr on 20.08.2021. Yay!
 */
public class HelpDescriptionsRecyclerViewAdapter extends RecyclerView.Adapter<ViewHolder> {

    private final Context context;
    private final List<String> descriptions;

    public HelpDescriptionsRecyclerViewAdapter(Context context, List<String> descriptions) {
        this.context = context;
        this.descriptions = descriptions;
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.help_imageview_description_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String rowNum = context.getString(R.string.help_tag_description_row_number, (position + 1));
        holder.viewRowNumber.setText(rowNum);
        holder.viewRowContent.setText(descriptions.get(position));
    }

    @Override
    public int getItemCount() {
        return descriptions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView viewRowNumber;
        public final TextView viewRowContent;

        public ViewHolder(@NotNull View view) {
            super(view);
            mView = view;
            viewRowNumber = view.findViewById(R.id.help_imageview_description_row_number);
            viewRowContent = view.findViewById(R.id.help_imageview_description_row_content);
        }

        @NotNull
        @Override
        public String toString() {
            return super.toString() + " Row number: " + viewRowNumber.getText() + ", " +
                    "Row content: " + viewRowContent.getText();
        }
    }
}
