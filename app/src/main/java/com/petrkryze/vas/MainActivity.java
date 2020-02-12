package com.petrkryze.vas;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.petrkryze.vas.RatingManager.State;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VisualAnalogScale";

    private Toolbar toolbar;
    private SeekBar ratingbar;
    private Button button_play_pause;
    private Button button_previous;
    private Button button_next;
    private TextView track_time;
    private TextView header_text;
    private RelativeLayout top_container;
    private ProgressBar progressBar;
    private ImageView checkMark;

    private GestureDetector gestureDetector;
    private Handler handler_main;
    private Vibrator vibrator;
    private int VIBRATE_BUTTON_MS;

    private int Nrec = 0;
    private int trackPointer = 0;

    private List<Recording> trackList;
    private Player player;
    private RatingManager ratingManager;

    private boolean initDone = false;

    private View.OnClickListener playListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON PLAY CLICKED");
            vibrator.vibrate(VIBRATE_BUTTON_MS);
            if (initDone && player.isPrepared() && !player.isSeeking()) {
                if (player.play()) {
                    button_play_pause.setText(getString(R.string.button_pause_label));
                    button_play_pause.setCompoundDrawablesWithIntrinsicBounds(null,
                            getDrawable(R.drawable.pause_larger), null, null);
                    button_play_pause.setOnClickListener(pauseListener);
                }
            }
        }
    };

    private View.OnClickListener pauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON PAUSE CLICKED");
            vibrator.vibrate(VIBRATE_BUTTON_MS);
            if (initDone && player.isPrepared() && !player.isSeeking()) {
                player.pause();

                button_play_pause.setText(getString(R.string.button_play_label));
                button_play_pause.setCompoundDrawablesWithIntrinsicBounds(null,
                        getDrawable(R.drawable.play_larger), null, null);
                button_play_pause.setOnClickListener(playListener);
            }
        }
    };

    private View.OnClickListener previousListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON PREVIOUS CLICKED");
            vibrator.vibrate(VIBRATE_BUTTON_MS);
            if (trackPointer > 0) {
                changeCurrentTrack(trackPointer, trackPointer-1);
                trackPointer--;
            }
        }
    };

    private View.OnClickListener nextListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON NEXT CLICKED");
            vibrator.vibrate(VIBRATE_BUTTON_MS);
            if (trackPointer < Nrec-1) {
                changeCurrentTrack(trackPointer, trackPointer+1);
                trackPointer++;
            }
        }
    };

    private SeekBar.OnSeekBarChangeListener ratingBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            vibrator.vibrate(30);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            vibrator.vibrate(100);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Log.i(TAG, "onStopTrackingTouch: PROGRESS: " + seekBar.getProgress());
            trackList.get(trackPointer).setRating(seekBar.getProgress());
            checkMark.setVisibility(View.VISIBLE);
            if (ratingManager.getState() == State.STATE_FINISHED) {
                checkMark.setImageDrawable(getDrawable(R.drawable.ic_done_all_green));
            } else {
                checkMark.setImageDrawable(getDrawable(R.drawable.ic_done_green));
            }

            if (ratingManager.isRatingFinished(trackList)) {
                Log.i(TAG, "onStopTrackingTouch: All recording have been rated!");
                ratingManager.setState(State.STATE_FINISHED);
                try {
                    ratingManager.saveResults(MainActivity.this, trackList);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private int animationDuration;
    private boolean isOverFlowOpen = false;
    private int toolbarHeight;
    private final int TOOLBAR_DELAY = 2000;
    private Runnable hideToolbar = new Runnable() {
        @Override
        public void run() {
            final ActionBar actionBar = getSupportActionBar();
            if (actionBar != null && actionBar.isShowing()) {
                Log.i(TAG, "run: Hiding toolbar");
                toolbar.animate()
                        .alpha(0f).setDuration(animationDuration)
                        .translationY(-toolbarHeight)
                        .setInterpolator(new DecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                toolbar.setVisibility(View.INVISIBLE);
                            }
                        });
                top_container.animate()
                        .translationY(-toolbarHeight).setDuration(animationDuration)
                        .setInterpolator(new DecelerateInterpolator());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initDone = false;
        trackPointer = 0;
        setContentView(R.layout.activity_main);
        hideSystemUI();

        // Toolbar setup
        animationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbarHeight = toolbar.getMinimumHeight();
        handler_main = new Handler(getMainLooper());

        ratingbar = findViewById(R.id.ratingBar);
        button_play_pause = findViewById(R.id.button_play_pause);
        button_previous = findViewById(R.id.button_previous);
        button_next = findViewById(R.id.button_next);
        track_time = findViewById(R.id.track_time);
        top_container = findViewById(R.id.top_container);
        header_text = findViewById(R.id.header_text);
        progressBar = findViewById(R.id.progressBar);
        checkMark = findViewById(R.id.check_mark);

        // Assign listeners to all buttons
        button_play_pause.setOnClickListener(playListener);
        button_previous.setOnClickListener(previousListener);
        button_next.setOnClickListener(nextListener);
        ratingbar.setOnSeekBarChangeListener(ratingBarListener);

        // Checks for single taps on screen to hide system UI
        gestureDetector = new GestureDetector(this, new MyGestureListener());

        // Initialize the audio player and rating manager
        player = new Player(this, getPlayerListener());

        ratingManager = new RatingManager(this);

        // Get vibrator service for UI vibration feedback
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);

        accommodateNightMode(); // TODO Check




        // Load the tracks from storage
        try {
            trackList = getRecordings();
            for (Recording r : trackList) { // TODO delet dis
                Log.i(TAG, "onCreate: " + r.toString());
            }
            welcome();
            initDone = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void welcome() {
        // Shows welcome info dialog
        AlertDialog welcome_dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.welcome_message))
                .setTitle(getString(R.string.welcome_title))
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        beginNewRating();
                    }
                }).create();
        welcome_dialog.setCanceledOnTouchOutside(false);
        welcome_dialog.setIcon(getDrawable(R.drawable.symbol_cvut_plna_verze));
        welcome_dialog.show();
    }

    private void beginNewRating() {
        // Set first track to play
        changeCurrentTrack(0,0);
        ratingManager.setState(State.STATE_IN_PROGRESS);

        handler_main.postDelayed(hideToolbar, TOOLBAR_DELAY);
    }



    private void changeCurrentTrack(int current, int changeTo) {
        if (Nrec <= 0) {
            Log.e(TAG, "changeCurrentTrack: Error! Invalid number of tracks!");
        } else {
            if (player.isPlaying()) {
                button_play_pause.callOnClick();
            }

            try {
                player.setCurrentTrack(trackList.get(changeTo));
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (changeTo == 0) {
                // Hide previous button
                button_previous.setVisibility(View.INVISIBLE);
            } else if (changeTo == Nrec-1) {
                // Hide next button
                button_next.setVisibility(View.INVISIBLE);
            } else if (current == 0 && changeTo == 1) {
                // Show previous button
                button_previous.setVisibility(View.VISIBLE);
            } else if (current == Nrec-1 && changeTo == (Nrec-2)) {
                // Show next button
                button_next.setVisibility(View.VISIBLE);
            }

            header_text.setText(getString(R.string.track_counter,
                    getString(R.string.track),
                    changeTo+1,
                    Nrec));
            progressBar.setProgress(0);

            // Set rating bar position to default or saved
            // Gets the value from current session
            int savedRating = trackList.get(changeTo).getRating();
            int lastSessionSavedRating = ratingManager.getLastSessionRating(changeTo);
            int setTo;
            if (savedRating != Recording.DEFAULT_UNSET_RATING) {
                setTo = savedRating;
                checkMark.setVisibility(View.VISIBLE);
            } else if (lastSessionSavedRating != Recording.DEFAULT_UNSET_RATING) {
                setTo = lastSessionSavedRating;
                checkMark.setVisibility(View.VISIBLE);
            } else {
                setTo = 50; // Default middle
                checkMark.setVisibility(View.INVISIBLE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ratingbar.setProgress(setTo, true);
            } else {
                ratingbar.setProgress(setTo);
            }

            if (ratingManager.getState() == State.STATE_FINISHED) {
                checkMark.setImageDrawable(getDrawable(R.drawable.ic_done_all_green));
            } else {
                checkMark.setImageDrawable(getDrawable(R.drawable.ic_done_green));
            }
        }
    }

    private Player.PlayerListener getPlayerListener() {
        return new Player.PlayerListener() {
            @Override
            public void onTrackPrepared(int duration) {
                // Enable play
                track_time.setText(formatDuration(duration));
                progressBar.setMax(duration);
            }

            @Override
            public void onTrackFinished() {
                button_play_pause.callOnClick();
                player.rewind();
            }

            @Override
            public void onHeadphonesMissing() {
                Toast.makeText(MainActivity.this,
                        getString(R.string.toast_connect_headphones), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVolumeDown() {
                Toast.makeText(MainActivity.this,
                        getString(R.string.toast_increase_volume), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTimeTick(final int time) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        track_time.setText(formatDuration(time));
                    }
                });
            }

            @Override
            public void onUpdateProgress(int current_ms) {
                progressBar.setProgress(current_ms);
            }
        };
    }

    private String formatDuration(int time_ms) {
        if (time_ms >= (60 * 60 * 1000)) {
            // If somehow longer than one hour
            Log.e(TAG, "formatDuration: I don't know how, but the audio is too damn long.");
            return "Error";
        } else {
            int min = (int) Math.floor((double) time_ms / 60000);
            int sec = (int) Math.floor(((double) time_ms % 60000) / 1000);

            return (min >= 10 ? String.valueOf(min) : "0" + min) +
                    ":" +
                    (sec >= 10 ? String.valueOf(sec) : "0" + sec);
        }
    }

    private List<Recording> getRecordings() throws Exception {
        boolean readable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ||
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY);
        if (!readable) {
            throw new Exception("External storage could not be read.");
        }

        File[] externalStorageVolumes =
                ContextCompat.getExternalFilesDirs(getApplicationContext(), null);
        File primaryExternalStorage = externalStorageVolumes[0];
        Log.i(TAG, "getRecordings: External Storage root: " + primaryExternalStorage.getPath());

        ArrayList<File> raw_audio_list = getAudioRecursive(primaryExternalStorage);
        Nrec = raw_audio_list.size();
        if (Nrec <= 0) {
            throw new Exception("No audio files found on external storage!");
        }

        Collections.sort(raw_audio_list); // Ensures the same order after loading
        // Shuffle the loaded paths randomly using the generated seed
        Collections.shuffle(raw_audio_list, new Random(ratingManager.getSeed()));
        List<Integer> rnums = linspace(1,Nrec);

        List<Recording> recordings= new ArrayList<>();
        for (int n = 0; n < Nrec; n++) {
            recordings.add(new Recording(
                    raw_audio_list.get(n).getPath(),
                    rnums.get(n),
                    ratingManager.getLastSessionRating(n))
            );
        }
        return recordings;
    }

    private List<Integer> linspace(int start, int end) {
        if (end < start) {
            Log.e(TAG, "linspace: Invalid input parameters!");
            return new ArrayList<>();
        } else {
            List<Integer> list = new ArrayList<>();
            for (int i = start; i <= end; i++) list.add(i);
            return list;
        }
    }

    private ArrayList<File> getAudioRecursive(File path) {
        ArrayList<File> output = new ArrayList<>();

        if (path.exists()) {
            if (path.isFile()) {
                // Only add wav-files
                String ext = FilenameUtils.getExtension(path.getPath());
                if (ext.toLowerCase().equals("mp3")) {
                    output.add(path);
                }
            } else if (path.isDirectory()) {
                File[] files = path.listFiles();
                if (files != null && files.length > 0) {
                    for (File file : files) {
                        output.addAll(getAudioRecursive(file));
                    }
                }
            }
        } else {
            Log.e(TAG, "getAudioRecursive: Path <" + path + "> does not exist.");
        }
        return output;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: ");
        if (player != null && player.isPlaying()) {
            button_play_pause.callOnClick();
        }

        ratingManager.saveSession(trackList);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.clean();
        player = null;
    }

    // Handles the auto-hide feature of toolbar to pause when overflow is opened
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (!isOverFlowOpen) {
            if (player != null && player.isPlaying()) {
                button_play_pause.callOnClick();
            }
            handler_main.removeCallbacks(hideToolbar);
        }
        isOverFlowOpen = true;
        return super.onMenuOpened(featureId, menu);
    }

    // Re-enables the auto-hide after the overflow is closed
    @Override
    public void onPanelClosed(int featureId, @NonNull Menu menu) {
        if (isOverFlowOpen) {
            handler_main.postDelayed(hideToolbar, TOOLBAR_DELAY);
        }
        isOverFlowOpen = false;
        super.onPanelClosed(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // TODO Change language CS/EN, maybe more?
                Toast.makeText(this, "Not yet implemented.", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_show_session_info:
                String message = getString(R.string.session_info_message,
                        String.valueOf(ratingManager.getSession_ID()),
                        ratingManager.getGeneratorMessage(),
                        String.valueOf(ratingManager.getSeed()),
                        ratingManager.getRatingFinishedRatio(trackList)
                        );

                AlertDialog info_dialog = new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setTitle(getString(R.string.session_info_title))
                        .setPositiveButton(getString(R.string.ok), null)
                        .create();
                info_dialog.setIcon(getDrawable(android.R.drawable.ic_dialog_info));
                info_dialog.show();
                return true;
            case R.id.action_reset_ratings:
                AlertDialog confirm_dialog = new AlertDialog.Builder(MainActivity.this)
                        .setMessage(getString(R.string.reset_confirm_prompt))
                        .setTitle(getString(R.string.alert))
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                initDone = false;
                                trackPointer = 0;
                                ratingManager.makeNewSession();
                                try {
                                    trackList = getRecordings();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                changeCurrentTrack(0,0);

                                ratingManager.setState(State.STATE_IN_PROGRESS);

                                checkMark.setVisibility(View.INVISIBLE);
                                checkMark.setImageDrawable(getDrawable(R.drawable.ic_done_green));
                                ratingbar.setProgress(50);

                                handler_main.postDelayed(hideToolbar, TOOLBAR_DELAY);
                                initDone = true;
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .create();
                confirm_dialog.setIcon(getDrawable(android.R.drawable.stat_sys_warning));
                confirm_dialog.show();
                return true;
            case R.id.action_quit:
                AlertDialog close_dialog = new AlertDialog.Builder(MainActivity.this)
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
                close_dialog.setIcon(getDrawable(android.R.drawable.ic_menu_close_clear_cancel));
                close_dialog.show();
                return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Log.i(TAG, "onWindowFocusChanged: GOT FOCUS");
            hideSystemUI();
        } else {
            Log.i(TAG, "onWindowFocusChanged: LOST FOCUS");
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
        private static final String GESTURE_DETECTOR_TAG = "GestureDetector";

        @Override
        public boolean onDown(MotionEvent event) {
            Log.d(GESTURE_DETECTOR_TAG, "onDown: " + event.toString());
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            Log.d(GESTURE_DETECTOR_TAG, "onSingleTapUp: " + event.toString());
            hideSystemUI();
            return super.onSingleTapUp(event);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (velocityY >= 5000) {
                Log.i(TAG, "onFling: Showing action bar");
                final ActionBar actionBar = getSupportActionBar();
                if (actionBar != null && !actionBar.isShowing()) {
                    toolbar.setVisibility(View.VISIBLE);
                    toolbar.animate()
                            .alpha(1f).setDuration(animationDuration)
                            .translationY(0)
                            .setInterpolator(new AccelerateInterpolator())
                            .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            handler_main.postDelayed(hideToolbar, TOOLBAR_DELAY);
                        }
                    });
                    top_container.animate()
                            .translationY(0).setDuration(animationDuration)
                            .setInterpolator(new AccelerateInterpolator());
                }
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }

    private void accommodateNightMode() {
        accommodateNightMode(null);
    }

    private void accommodateNightMode(Configuration cnf) {
        int currentNightMode;
        if (cnf != null) {
            currentNightMode = cnf.uiMode;
        } else {
            currentNightMode = getApplicationContext().getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
        }

        if (currentNightMode != Configuration.UI_MODE_NIGHT_YES) {
            // Night mode is not active, invert icons so they're black
            final float[] NEGATIVE = {
                    -1.0f,     0,     0,    0, 255, // red
                    0, -1.0f,     0,    0, 255, // green
                    0,     0, -1.0f,    0, 255, // blue
                    0,     0,     0, 1.0f,   0  // alpha
            };

            Drawable[] d = {getDrawable(R.drawable.previous_larger),
                    getDrawable(R.drawable.play_larger),
                    getDrawable(R.drawable.pause_larger),
                    getDrawable(R.drawable.next_larger)};

            for (Drawable drawable : d) {
                if (drawable != null) {
                    drawable.setColorFilter(new ColorMatrixColorFilter(NEGATIVE));
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        accommodateNightMode(newConfig);

        // TODO Something fucky about restarting after change to nightmode
        // Also something fucky about changing between landscape and portrait
    }
}
