package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.petrkryze.vas.RatingManager.LoadResult;
import com.petrkryze.vas.databinding.ActivityMainBinding;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
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

    private static int alphaDisabled;
    private static int alphaEnabled;

    // This stuff needs to be here to make the excel creation work
    static {
        System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLInputFactory",
                "com.fasterxml.aalto.stax.InputFactoryImpl"
        );
        System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLOutputFactory",
                "com.fasterxml.aalto.stax.OutputFactoryImpl"
        );
        System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLEventFactory",
                "com.fasterxml.aalto.stax.EventFactoryImpl"
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Toolbar setup
        setSupportActionBar(binding.toolbar);

        // Menu icon disabled look alpha
        alphaDisabled = getResources().getInteger(R.integer.DISABLED_ICON_ALPHA);
        alphaEnabled = getResources().getInteger(R.integer.ENABLED_ICON_ALPHA);

        SharedPreferences preferences = getSharedPreferences(getString(R.string.PREFERENCES_SETTINGS), MODE_PRIVATE);
        boolean showWelcomeScreen = preferences.getBoolean(getString(R.string.KEY_PREFERENCES_SETTINGS_WELCOME_SHOW), true);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();
        NavInflater navInflater = navController.getNavInflater();
        NavGraph navGraph = navInflater.inflate(R.navigation.nav_graph);

        showWelcomeScreen = true; // TODO DEVELOPMENT ONLY, delete later!
        navGraph.setStartDestination(showWelcomeScreen ? R.id.welcome_fragment : R.id.rating_fragment);
        navController.setGraph(navGraph);

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupWithNavController(binding.toolbar, navController, appBarConfiguration);
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
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item != null) {
                Drawable icon = item.getIcon();
                applyTintFilter(icon, getColor(R.color.textPrimaryOnSurface));
                icon.setAlpha(128);
            }
        }

        return true;
    }

    public static Drawable applyTintFilter(Drawable icon, @ColorInt final int color) {
        if (icon != null) {
            icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }
        return icon;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_quit) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.dialog_quit_title))
                    .setMessage(getString(R.string.dialog_quit_message))
                    .setIcon(applyTintFilter(
                            ContextCompat.getDrawable(this, R.drawable.ic_exit),
                            getColor(R.color.secondaryColor)))
                    .setPositiveButton(R.string.dialog_quit_confirm, (dialog, which) -> MainActivity.this.finish())
                    .setNegativeButton(R.string.dialog_quit_cancel, null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static void disableMenuItem(final Menu menu, final int itemID) {
        menu.findItem(itemID).setEnabled(false).getIcon().setAlpha(alphaDisabled);
    }

    public static void enableMenuItem(final Menu menu, final int itemID) {
        menu.findItem(itemID).setEnabled(true).getIcon().setAlpha(alphaEnabled);
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
                title = context.getString(R.string.dialog_no_session_found_title);
                message = context.getString(R.string.dialog_no_session_found_message);
                icon = applyTintFilter(
                        ContextCompat.getDrawable(context, R.drawable.ic_info),
                        context.getColor(R.color.secondaryColor));
                break;
            case CORRUPTED_SESSION:
                title = context.getString(R.string.dialog_corrupted_session_title);
                message = context.getString(R.string.dialog_corrupted_session_message);
                icon = applyTintFilter(
                        ContextCompat.getDrawable(context, R.drawable.ic_error),
                        context.getColor(R.color.errorColor));
                break;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(title).setMessage(message).setIcon(icon)
                .setPositiveButton(context.getString(R.string.dialog_quit_confirm), null).show();
    }
}
