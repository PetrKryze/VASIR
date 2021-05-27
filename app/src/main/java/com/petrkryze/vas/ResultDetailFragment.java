package com.petrkryze.vas;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ResultDetailFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ResultDetailFragment extends Fragment {

    // private static final String TAG = "ResultDetailFragment";
    private static final String RESULT_TO_DISPLAY = "result_to_display";

    private RatingResult resultToDisplay;

    public ResultDetailFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param resultToDisplay Rating result to be displayed in detail.
     * @return A new instance of fragment ResultDetailFragment.
     */
    @SuppressWarnings("unused")
    public static ResultDetailFragment newInstance(RatingResult resultToDisplay) {
        ResultDetailFragment fragment = new ResultDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(RESULT_TO_DISPLAY, resultToDisplay);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            resultToDisplay = ResultDetailFragmentArgs.fromBundle(getArguments()).getRatingResult();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.result_detail_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        TextView TWresultDetailID = view.findViewById(R.id.result_detail_sessionID);
        TextView TWresultDetailSaveDate = view.findViewById(R.id.result_detail_save_date);
        TextView TWresultDetailSeed = view.findViewById(R.id.result_detail_seed);
        TextView TWresultDetailGeneratorDate = view.findViewById(R.id.result_detail_generator_date);
        RecyclerView recordingListView = view.findViewById(R.id.result_detail_recordings_list);

        TWresultDetailID.setText(getString(R.string.result_detail_sessionID, resultToDisplay.getSession_ID()));
        TWresultDetailSaveDate.setText(formatDateTime(resultToDisplay.getSaveDate()));
        TWresultDetailSeed.setText(String.valueOf(resultToDisplay.getSeed()));
        TWresultDetailGeneratorDate.setText(resultToDisplay.getGeneratorMessage().replace("-",". "));

        recordingListView.setAdapter(
                new RecordingsRecyclerViewAdapter(context, resultToDisplay.getRecordings()));
        recordingListView.setLayoutManager(new LinearLayoutManager(context));
    }

    private String formatDateTime(String raw) {
        String[] split1 = raw.split("_");

        List<String> splitDate = Arrays.asList(split1[0].split("-"));
        Collections.reverse(splitDate);

        String date = TextUtils.join(". ", splitDate);
        return date + " " + split1[1];
    }
}