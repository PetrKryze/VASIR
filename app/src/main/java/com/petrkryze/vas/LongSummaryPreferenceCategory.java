package com.petrkryze.vas;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

/**
 * Created by Petr on 02.02.2023. Yay!
 */
@SuppressWarnings("unused")
public class LongSummaryPreferenceCategory extends PreferenceCategory {

    public LongSummaryPreferenceCategory(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LongSummaryPreferenceCategory(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LongSummaryPreferenceCategory(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LongSummaryPreferenceCategory(@NonNull Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        summary.setMaxLines(4);
        summary.setSingleLine(false);
    }
}
