package com.petrkryze.vas.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.databinding.FragmentWelcomeBinding;

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

    private FragmentWelcomeBinding binding;

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
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        binding = FragmentWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().invalidateOptionsMenu();

        CheckBox checkBox = binding.welcomeFragmentCheckbox;
        ImageView scrollIcon = binding.welcomeFragmentScrollHintArrow;
        NestedScrollView scrollView = binding.welcomeFragmentScrollContainer;

        int colorFrom = getResources().getColor(R.color.primaryColor, null);
        int colorTo = getResources().getColor(R.color.secondaryColor, null);

        // App startup logo animation
        ValueAnimator colorIntroAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorIntroAnimation.setRepeatCount(1);
        colorIntroAnimation.setRepeatMode(ValueAnimator.REVERSE);
        colorIntroAnimation.setDuration(1000);
        colorIntroAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        colorIntroAnimation.addUpdateListener(animation ->
                binding.welcomeFragmentTitleImage.setBackgroundColor((int) animation.getAnimatedValue()));
        colorIntroAnimation.start();

        // "Easter egg" animation on logo press
        ValueAnimator colorTouchAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorTouchAnimation.addUpdateListener(animation ->
                binding.welcomeFragmentTitleImage.setBackgroundColor((int) animation.getAnimatedValue()));
        ValueAnimator elevationAnimation = ValueAnimator.ofFloat(0f,50f);
        elevationAnimation.addUpdateListener(animation ->
                binding.welcomeFragmentTitleImage.setElevation((Float) animation.getAnimatedValue()));
        AnimatorSet set = new AnimatorSet();
        set.playTogether(colorTouchAnimation, elevationAnimation);
        set.setInterpolator(new DecelerateInterpolator());
        set.setDuration(200);

        binding.welcomeFragmentTitleImage.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.playSoundEffect(SoundEffectConstants.CLICK);
                    set.start(); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    set.reverse(); return true;
                default: return false;
            }
        });

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

        binding.welcomeFragmentStartButton.setOnClickListener(v -> {
            NavDirections directions = WelcomeFragmentDirections.actionWelcomeFragmentToRatingFragment();
            NavHostFragment.findNavController(WelcomeFragment.this).navigate(directions);
        });

        // Part of code that handles the animated scroll hint button
        scrollIcon.setOnClickListener(v -> {
            TextView helpBody = scrollView.findViewById(R.id.welcome_body_textview);
            scrollView.smoothScrollBy(0, helpBody.getTop());
        });

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

    @SuppressLint("ShowToast")
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
                Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                        Html.fromHtml(getString(R.string.snackbar_ratings_loading_failed, e.getMessage()),Html.FROM_HTML_MODE_LEGACY),
                        BaseTransientBottomBar.LENGTH_LONG)
                        .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
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
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}