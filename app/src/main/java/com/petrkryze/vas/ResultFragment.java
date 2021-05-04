package com.petrkryze.vas;

import android.content.Context;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.petrkryze.vas.RatingManager.RatingResult;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A fragment representing a list of Items.
 */
public class ResultFragment extends Fragment implements View.OnClickListener {

    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private static final String TAG = "ResultsFragment";

    private ArrayList<RatingResult> ratingResults;

    private Button buttonClose;
    private RecyclerView recyclerView;

    private Vibrator vibrator;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ResultFragment() {
    }

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static ResultFragment newInstance(int columnCount, ArrayList<RatingResult> ratingResults) {
        ResultFragment fragment = new ResultFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        args.putSerializable(MainActivity.ResultListSerializedKey, ratingResults);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
            ratingResults = (ArrayList<RatingResult>) getArguments().getSerializable(MainActivity.ResultListSerializedKey);
        }

        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.results_window, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        buttonClose = getView().findViewById(R.id.window_button_close);
        buttonClose.setOnClickListener(this);

        recyclerView = view.findViewById(R.id.results_list);
        recyclerView.setAdapter(new MyResultRecyclerViewAdapter(context, ratingResults));

        if (mColumnCount <= 1) {
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }
    }

    @Override
    public void onClick(View v) {
        // TODO check
        if (v.getId() == R.id.window_button_close) {
            Log.i(TAG, "onClick: BUTTON CLOSE CLICKED");

            vibrator.vibrate(VibrationEffect.createOneShot(
                    MainActivity.VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

            getFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .remove(ResultFragment.this)
                    .commit();
        }
    }
}