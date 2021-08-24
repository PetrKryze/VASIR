package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.petrkryze.vas.databinding.ActivityMainBinding;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {

    private static int alphaDisabled;
    private static int alphaEnabled;

    private AlertDialog dialog;

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
            dialog = new MaterialAlertDialogBuilder(this)
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

    public static Spanned html(String string) {
        return Html.fromHtml(string, Html.FROM_HTML_MODE_LEGACY);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
