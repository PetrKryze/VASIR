package com.petrkryze.vas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HelpFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HelpFragment extends Fragment {

    private String helpTitle;
    private String helpContextMessage;

    private TextView TWtitleHelp;
    private TextView TWcontextHelp;

    private static final String KEY_HELP_TITLE = "help_title";
    private static final String KEY_HELP_CONTEXT_MSG = "help_context_message";

    public HelpFragment() {
        // Required empty public constructor
    }


    public static HelpFragment newInstance(String title, String contextMessage) {
        HelpFragment fragment = new HelpFragment();
        Bundle args = new Bundle();
        args.putString(KEY_HELP_TITLE, title);
        args.putString(KEY_HELP_CONTEXT_MSG, contextMessage);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            helpTitle = HelpFragmentArgs.fromBundle(getArguments()).getTitleHelp();
            helpContextMessage = HelpFragmentArgs.fromBundle(getArguments()).getContextHelp();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.help_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TWtitleHelp = view.findViewById(R.id.help_title_textview);
        TWcontextHelp = view.findViewById(R.id.help_body_textview2);

        TWtitleHelp.setText(helpTitle);
        TWcontextHelp.setText(helpContextMessage);
    }
}