package com.petrkryze.vas;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.File;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A fragment representing a list of Items.
 */
public class ResultsFragment extends Fragment {

    private static final String TAG = "ResultFragment";
    public static final String ResultListSerializedKey = "resultsList";

    private ArrayList<RatingResult> ratingResults;

    private Button buttonClose;
    private RecyclerView resultsListView;

    private Vibrator vibrator;

    private final View.OnClickListener shareAllListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.button_results_share_all) {
                Log.i(TAG, "onClick: SHARE ALL BUTTON CLICKED");
                vibrator.vibrate(VibrationEffect.createOneShot(
                        MainActivity.VIBRATE_BUTTON_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                Context context = getContext();
                if (context == null) {
                    Log.e(TAG, "onClick: No context!");
                    return;
                }

                ArrayList<Uri> uris = new ArrayList<>();

                for (RatingResult r : ratingResults) {
                    File f = new File(r.getPath());
                    try {
                        uris.add(FileProvider.getUriForFile(
                                getContext(),"com.petrkryze.vas.fileprovider", f));
                    } catch (IllegalArgumentException e) {
                        Log.e("File Selector",
                                "The selected file can't be shared: " + f.toString());
                        e.printStackTrace();
                    }
                }

                Intent sharingIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                sharingIntent.setType("text/plain");
                sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sharingIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                sharingIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.result_share_title, uris.size()));

                getContext().startActivity(Intent.createChooser(sharingIntent, getContext().getString(R.string.share_using)));
            }
        }
    };


    private final OnItemDetailListener onItemDetailListener = selectedResult -> {
        NavDirections directions =
                ResultsFragmentDirections.actionResultFragmentToResultDetailFragment(selectedResult);
        NavHostFragment.findNavController(ResultsFragment.this).navigate(directions);
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ResultsFragment() {
    }

    @SuppressWarnings("unused")
    public static ResultsFragment newInstance(int columnCount, ArrayList<RatingResult> ratingResults) {
        ResultsFragment fragment = new ResultsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ResultListSerializedKey, ratingResults);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            ratingResults = ResultsFragmentArgs.fromBundle(getArguments()).getRatings();
        }

        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.result_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = view.getContext();

        buttonClose = view.findViewById(R.id.button_results_share_all);
        buttonClose.setOnClickListener(shareAllListener);

        resultsListView = view.findViewById(R.id.results_list);
        resultsListView.setAdapter(new ResultsRecyclerViewAdapter(
                context, ratingResults, onItemDetailListener));
        resultsListView.setLayoutManager(new LinearLayoutManager(context));
    }

    interface OnItemDetailListener {
        void onItemClick(RatingResult selectedRating);
    }
}