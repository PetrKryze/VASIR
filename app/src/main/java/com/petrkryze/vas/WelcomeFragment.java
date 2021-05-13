package com.petrkryze.vas;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;

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
    // TODO Upravite tenhle text dialogu aby odpovídal tomu že se vybírá adresář
    private ImageView titleImage;
    private Button startButton;
    private CheckBox checkBox;

    private SharedPreferences preferences;

    public WelcomeFragment() {
        // Required empty public constructor
    }

    public static WelcomeFragment newInstance(String param1, String param2) {
        WelcomeFragment fragment = new WelcomeFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = requireContext().getSharedPreferences(getString(R.string.PREFERENCES_SETTINGS), Context.MODE_PRIVATE);
//        if (getArguments() != null) {
//            mParam1 = getArguments().getString(ARG_PARAM1);
//            mParam2 = getArguments().getString(ARG_PARAM2);
//        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.welcome_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        titleImage = view.findViewById(R.id.welcome_title_image);
        startButton = view.findViewById(R.id.welcome_start_button);
        checkBox = view.findViewById(R.id.welcome_checkbox);

        int colorFrom = getResources().getColor(R.color.primaryColor, null);
        int colorTo = getResources().getColor(R.color.titleLogoTransition, null);

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
        colorAnimation.setRepeatMode(ValueAnimator.REVERSE);
        colorAnimation.setDuration(3000);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                titleImage.setBackgroundColor((int) animation.getAnimatedValue());
            }
        });

        colorAnimation.start();

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(getString(R.string.KEY_PREFERENCES_SETTINGS_WELCOME_SHOW), !checkBox.isChecked());
                editor.apply();

                NavDirections directions = WelcomeFragmentDirections.actionWelcomeFragmentToRatingFragment();
                NavHostFragment.findNavController(WelcomeFragment.this).navigate(directions);
            }
        });
    }
}