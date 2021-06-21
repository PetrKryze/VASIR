package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.petrkryze.vas.RatingManager.LoadResult;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import static com.petrkryze.vas.RatingManager.SESSION_INFO_BUNDLE_SESSION;

public class MainActivity extends AppCompatActivity {

//    private static final String TAG = "VisualAnalogScale";

    private static int alphaDisabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar setup
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Menu icon disabled look alpha
        alphaDisabled = getResources().getInteger(R.integer.DISABLED_ICON_ALPHA);

        SharedPreferences preferences = getSharedPreferences(getString(R.string.PREFERENCES_SETTINGS), MODE_PRIVATE);
        boolean showWelcomeScreen = preferences.getBoolean(getString(R.string.KEY_PREFERENCES_SETTINGS_WELCOME_SHOW), true);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        NavInflater navInflater = navController.getNavInflater();
        NavGraph navGraph = navInflater.inflate(R.navigation.nav_graph);

        showWelcomeScreen = true; // TODO DEVELOPMENT ONLY, delete later!
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
        if (itemID == R.id.action_menu_quit) {
            AlertDialog close_dialog = new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.close_confirm_prompt))
                    .setTitle(getString(R.string.alert))
                    .setPositiveButton(R.string.ok, (dialog, which) -> MainActivity.this.finish())
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            close_dialog.setIcon(ContextCompat.getDrawable(this,
                    android.R.drawable.ic_menu_close_clear_cancel));
            close_dialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static void disableMenuItem(final Menu menu, final int itemID) {
        menu.findItem(itemID).setEnabled(false).getIcon().setAlpha(alphaDisabled);
    }

    public static void enableMenuItem(final Menu menu, final int itemID) {
        menu.findItem(itemID).setEnabled(true).getIcon().setAlpha(255);
    }

    public interface navigateToCSIInterface { void sailTheShip(RatingResult session); }
    public static void navigateToCurrentSessionInfo(Fragment fragment,
                                                    navigateToCSIInterface callback) {
        Context context = fragment.requireContext();
        Bundle bundle = RatingManager.getSessionInfo(fragment.requireActivity());
        String message = "";
        String title = "";
        Drawable icon = null;

        switch ((LoadResult) bundle.getSerializable(RatingManager.GET_SESSION_INFO_LOAD_RESULT_KEY)) {
            case OK:
                callback.sailTheShip((RatingResult) bundle.getSerializable(SESSION_INFO_BUNDLE_SESSION));
                return;
            case NO_SESSION:
                message = context.getString(R.string.no_session_found);
                title = context.getString(R.string.info);
                icon = ContextCompat.getDrawable(context, R.drawable.ic_info);
                break;
            case CORRUPTED_SESSION:
                message = context.getString(R.string.corrupted_session);
                title = context.getString(R.string.alert);
                icon = ContextCompat.getDrawable(context, R.drawable.ic_error_red_24dp);
                break;
        }

        AlertDialog loading_failed_dialog = new AlertDialog.Builder(context)
                .setMessage(message)
                .setTitle(title)
                .setPositiveButton(context.getString(R.string.ok), null)
                .create();
        loading_failed_dialog.setIcon(icon);
        loading_failed_dialog.show();
    }

}
