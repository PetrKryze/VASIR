package com.petrkryze.vas.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HelpFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HelpFragment extends Fragment {

    private String helpTitle;
    private String helpBody;

    private static final String KEY_HELP_TITLE = "help_title";
    private static final String KEY_HELP_CONTEXT_MSG = "help_body";

    public HelpFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("unused")
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
            helpTitle = HelpFragmentArgs.fromBundle(getArguments()).getContextTitle();
            helpBody = HelpFragmentArgs.fromBundle(getArguments()).getContextBody();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_help, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView TWhelpTitle = view.findViewById(R.id.help_title_textview);
        TextView TWhelpBody = view.findViewById(R.id.help_body_textview2);

        TWhelpTitle.setText(helpTitle);
        TWhelpBody.setText(helpBody);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull @NotNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_show_session_info) {
            MainActivity.navigateToCurrentSessionInfo(
                    this, session -> {
                        NavDirections directions = HelpFragmentDirections
                                .actionHelpFragmentToCurrentSessionInfoFragment(session);
                        NavHostFragment.findNavController(HelpFragment.this)
                                .navigate(directions);
                    }
            );
        }

        return super.onOptionsItemSelected(item);
    }
}