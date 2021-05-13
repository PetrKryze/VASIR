package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VisualAnalogScale";

    private Toolbar toolbar;

    private GestureDetector gestureDetector;

    RatingFragment ratingFragment = null;
    ResultsFragment resultFragment = null;

    private Vibrator vibrator;
    public static int VIBRATE_BUTTON_MS;
    public static int VIBRATE_RATING_START;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar setup
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Get vibrator service for UI vibration feedback
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
        VIBRATE_RATING_START = getResources().getInteger(R.integer.VIBRATE_RATING_BAR_START_MS);

        // Checks for single taps on screen to hide system UI
        gestureDetector = new GestureDetector(this, new MainActivity.MyGestureListener());

        hideSystemUI();

        SharedPreferences preferences = getSharedPreferences(getString(R.string.PREFERENCES_SETTINGS), MODE_PRIVATE);
        boolean showWelcomeScreen = preferences.getBoolean(getString(R.string.KEY_PREFERENCES_SETTINGS_WELCOME_SHOW), true);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        NavInflater navInflater = navController.getNavInflater();
        NavGraph navGraph = navInflater.inflate(R.navigation.nav_graph);

        showWelcomeScreen = true; // TODO DEVELOPEMENT ONLY
        navGraph.setStartDestination(showWelcomeScreen ? R.id.welcome_fragment : R.id.rating_fragment);
        navController.setGraph(navGraph);

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.toolbar_menu, menu);

        // Show icons in the overflow menu - restrictedAPi is an Android bug
        if (menu instanceof MenuBuilder) {
            MenuBuilder menuBuilder = (MenuBuilder) menu;
            menuBuilder.setOptionalIconsVisible(true);
        }

        // Tint color of all icons in the menu to primary text color
        ColorFilter filter = new PorterDuffColorFilter(getColor(R.color.primaryTextColor), PorterDuff.Mode.SRC_IN);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item != null) {
                Drawable icon = item.getIcon();
                if (icon != null) {
                    icon.setColorFilter(filter);
                    icon.setAlpha(128);
                }
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_help) {
            AlertDialog help_dialog = new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.help_message))
                    .setTitle(getString(R.string.help))
                    .setPositiveButton(R.string.ok, null)
                    .create();
            help_dialog.setIcon(ContextCompat.getDrawable(this,
                    android.R.drawable.ic_menu_help));
            help_dialog.show();
            return true;
        } else if (itemID == R.id.action_quit) {
            AlertDialog close_dialog = new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.close_confirm_prompt))
                    .setTitle(getString(R.string.alert))
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MainActivity.this.finish();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            close_dialog.setIcon(ContextCompat.getDrawable(this,
                    android.R.drawable.ic_menu_close_clear_cancel));
            close_dialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(0);
    }

    // Handles the auto-hide feature of toolbar to pause when overflow is opened
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (ratingFragment != null) {
            ratingFragment.onMenuOpened();
        }

        return super.onMenuOpened(featureId, menu);
    }

    // Re-enables the auto-hide after the overflow is closed
    @Override
    public void onPanelClosed(int featureId, @NonNull Menu menu) {
        if (ratingFragment != null) {
            ratingFragment.onPanelClosed();
        }

        super.onPanelClosed(featureId, menu);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
    }

    // This part ensures return back to 'fullscreen' mode on app single tap
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            hideSystemUI();
            return super.onSingleTapUp(event);
        }
    }

}
