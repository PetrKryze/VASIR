package com.petrkryze.vas;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link WelcomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class WelcomeFragment extends Fragment {
//    private static final String TAG = "WelcomeFragment";

    private ImageView titleImage;
    private CheckBox checkBox;

    private SharedPreferences preferences;

    public WelcomeFragment() {
        // Required empty public constructor
    }

    @SuppressWarnings("unused")
    public static WelcomeFragment newInstance() {
        WelcomeFragment fragment = new WelcomeFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = requireContext().getSharedPreferences(getString(R.string.PREFERENCES_SETTINGS), Context.MODE_PRIVATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_welcome, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().invalidateOptionsMenu();

        titleImage = view.findViewById(R.id.welcome_title_image);
        Button startButton = view.findViewById(R.id.welcome_start_button);
        checkBox = view.findViewById(R.id.welcome_checkbox);

        int colorFrom = getResources().getColor(R.color.primaryColor, null);
        int colorTo = getResources().getColor(R.color.titleLogoTransition, null);

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimation.setRepeatMode(ValueAnimator.REVERSE);
        colorAnimation.setDuration(3000);
        colorAnimation.addUpdateListener(animation -> titleImage.setBackgroundColor((int) animation.getAnimatedValue()));
        colorAnimation.start();

        startButton.setOnClickListener(v -> {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(getString(R.string.KEY_PREFERENCES_SETTINGS_WELCOME_SHOW), !checkBox.isChecked());
            editor.apply();

            NavDirections directions = WelcomeFragmentDirections.actionWelcomeFragmentToRatingFragment();
            NavHostFragment.findNavController(WelcomeFragment.this).navigate(directions);
        });
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toDisable = {R.id.action_menu_help, R.id.action_menu_save,
                R.id.action_menu_reset_ratings};
        int[] toEnable = {R.id.action_menu_show_saved_results, R.id.action_menu_quit,
                R.id.action_menu_show_session_info};

        for (int item : toDisable) MainActivity.disableMenuItem(menu, item);
        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull @NotNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_show_saved_results) {
            try {
                ArrayList<RatingResult> ratings = RatingManager.loadResults(requireContext());

                NavDirections directions =
                        WelcomeFragmentDirections.actionWelcomeFragmentToResultFragment(ratings);
                NavHostFragment.findNavController(this).navigate(directions);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), getString(R.string.ratings_loading_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (itemID == R.id.action_menu_show_session_info) {
            MainActivity.navigateToCurrentSessionInfo(
                    this, session -> {
                        NavDirections directions = WelcomeFragmentDirections
                                .actionWelcomeFragmentToCurrentSessionInfoFragment(session);
                        NavHostFragment.findNavController(WelcomeFragment.this)
                                .navigate(directions);
                    }
            );
        }

        return super.onOptionsItemSelected(item);
    }
}