package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;

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
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import static com.petrkryze.vas.RatingManager.SESSION_INFO_BUNDLE_SESSION;

public class MainActivity extends AppCompatActivity {

    private static int alphaDisabled;
    private static int alphaEnabled;

    private RelativeLayout loadingScrim;

    public static final String ACTION_SHOW_LOADING = "com.petrkryze.vas.intent.SHOW_LOADING";
    public static final String ACTION_HIDE_LOADING = "com.petrkryze.vas.intent.HIDE_LOADING";

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

        // Loading scrim and BroadcastReceiver
        loadingScrim = binding.generalLoadingContainer;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_SHOW_LOADING);
        intentFilter.addAction(ACTION_HIDE_LOADING);
        intentFilter.addCategory("com.petrkryze.vas");
        this.registerReceiver(new LoadingReceiver(), intentFilter);

        // Menu icon disabled look alpha
        alphaDisabled = getResources().getInteger(R.integer.DISABLED_ICON_ALPHA);
        alphaEnabled = getResources().getInteger(R.integer.ENABLED_ICON_ALPHA);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showWelcomeScreen = preferences.getBoolean(getString(R.string.SETTING_KEY_SHOW_WELCOME_SCREEN), true);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();
        NavInflater navInflater = navController.getNavInflater();
        NavGraph navGraph = navInflater.inflate(R.navigation.nav_graph);

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
        if (itemID == R.id.action_menu_settings) {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
            return NavigationUI.onNavDestinationSelected(item, navController);
        } else if (itemID == R.id.action_menu_quit) {
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

    public class LoadingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("BroadcastReceiver", "onReceive: Action = " + action);
            if (action.equals(ACTION_HIDE_LOADING)) {
                hideLoading();
            } else if (action.equals(ACTION_SHOW_LOADING)) {
                showLoading();
            }
        }

        private void changeLoadingVisibility(boolean show) {
            if (loadingScrim != null) {
                runOnUiThread(() -> loadingScrim.setVisibility(
                        show ? View.VISIBLE : View.GONE
                ));
            }
        }
        private void hideLoading() { changeLoadingVisibility(false);}
        private void showLoading() { changeLoadingVisibility(true);}
    }
}
