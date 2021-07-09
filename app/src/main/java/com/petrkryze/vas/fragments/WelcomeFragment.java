package com.petrkryze.vas.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.RatingResult;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
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
    private ImageView scrollIcon;

    private SharedPreferences preferences;

    private boolean hasScrolled = false;

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
        scrollIcon = view.findViewById(R.id.welcome_fragment_scroll_hint_arrow);
        NestedScrollView scrollView = view.findViewById(R.id.welcome_fragment_scroll_container);

        int colorFrom = getResources().getColor(R.color.primaryColor, null);
        int colorTo = getResources().getColor(R.color.titleLogoTransition, null);

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimation.setRepeatMode(ValueAnimator.REVERSE);
        colorAnimation.setDuration(3000);
        colorAnimation.addUpdateListener(animation -> titleImage.setBackgroundColor((int) animation.getAnimatedValue()));
        colorAnimation.start();

        // Checkbox logic
        boolean showWelcomeScreen = preferences.getBoolean(getString(R.string.KEY_PREFERENCES_SETTINGS_WELCOME_SHOW), false);
        checkBox.setChecked(!showWelcomeScreen);
        checkBox.setAlpha(checkBox.isChecked() ? 1 : (float) 0.5);
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checkBox.setAlpha(isChecked ? 1 : (float) 0.5);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(getString(R.string.KEY_PREFERENCES_SETTINGS_WELCOME_SHOW), !checkBox.isChecked());
            editor.apply();
        });

        startButton.setOnClickListener(v -> {
            NavDirections directions = WelcomeFragmentDirections.actionWelcomeFragmentToRatingFragment();
            NavHostFragment.findNavController(WelcomeFragment.this).navigate(directions);
        });

        // Part of code that handles the animated scroll hint button
        scrollIcon.setOnClickListener(v -> {
            TextView helpBody = scrollView.findViewById(R.id.welcome_body_textview);
            scrollView.smoothScrollBy(0, helpBody.getTop());
        });

        // Filter to make the arrow black
        ColorFilter filter = new PorterDuffColorFilter(
                requireContext().getColor(android.R.color.black), PorterDuff.Mode.SRC_IN);
        scrollIcon.getDrawable().setColorFilter(filter);

        // Suggestive animation of slight hopping up and down
        TranslateAnimation translateAnimation = new TranslateAnimation(0, 0, 0, 20);
        translateAnimation.setRepeatCount(Animation.INFINITE);
        translateAnimation.setRepeatMode(Animation.REVERSE);
        translateAnimation.setDuration(500);

        // After set delay shows the arrow and starts it's hopping animation
        (new Handler()).postDelayed(() -> {
            if (!hasScrolled) {
                scrollIcon.setVisibility(View.VISIBLE);
                scrollIcon.animate()
                        .alpha((float) 0.2)
                        .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        scrollIcon.startAnimation(translateAnimation);
                    }
                });
            }
        }, 2000);

        // Makes the arrow disappear on scroll / not appear at all
        scrollView.setSmoothScrollingEnabled(true);
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            hasScrolled = true;
            if (scrollIcon.getVisibility() == View.VISIBLE) {

                scrollIcon.animate()
                        .alpha((float) 0.0)
                        .setDuration(200)
                        .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        scrollIcon.clearAnimation();
                        scrollIcon.setVisibility(View.GONE);
                    }
                });
            }
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
                Toast.makeText(requireContext(), getString(R.string.snackbar_ratings_loading_failed, e.getMessage()),
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