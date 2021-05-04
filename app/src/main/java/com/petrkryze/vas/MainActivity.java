package com.petrkryze.vas;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.petrkryze.vas.RatingManager.DirectoryCheckError;
import com.petrkryze.vas.RatingManager.RatingResult;
import com.petrkryze.vas.RatingManager.RecordingsFoundListener;
import com.petrkryze.vas.RatingManager.State;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import static com.petrkryze.vas.DocumentUtils.getFullPathFromTreeUri;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_DIRECTORY;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_ERROR_TYPE;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_IS_OK;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_MISSING_CNT;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_MISSING_LIST;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VisualAnalogScale";

    private Toolbar toolbar;
    private SeekBar ratingbar;
    private Button button_play_pause;
    private Button button_previous;
    private Button button_next;
    private TextView track_time;
    private TextView header_text;
    private SeekBar progressBar;
    private ImageView checkMark;
    private FrameLayout windowInsertPoint;

    private GestureDetector gestureDetector;
    private Handler handler_main;
    private Vibrator vibrator;
    public static int VIBRATE_BUTTON_MS;
    public static int VIBRATE_RATING_START;

    private int Nrec = 0;
    private int trackPointer = 0;

    private List<Recording> trackList;
    private Player player;
    private RatingManager ratingManager;

    private boolean initDone = false;

    private final View.OnClickListener playListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON PLAY CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            if (initDone && player.isPrepared() && !player.isSeeking()) {
                if (player.play()) {
                    button_play_pause.setText(getString(R.string.button_pause_label));
                    button_play_pause.setCompoundDrawablesWithIntrinsicBounds(null,
                            ContextCompat.getDrawable(MainActivity.this,
                                    R.drawable.ic_pause), null, null);
                    button_play_pause.setOnClickListener(pauseListener);
                }
            }
        }
    };

    private final View.OnClickListener pauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON PAUSE CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            if (initDone && player.isPrepared() && !player.isSeeking()) {
                player.pause();

                button_play_pause.setText(getString(R.string.button_play_label));
                button_play_pause.setCompoundDrawablesWithIntrinsicBounds(null,
                        ContextCompat.getDrawable(MainActivity.this,
                                R.drawable.ic_play), null, null);
                button_play_pause.setOnClickListener(playListener);
            }
        }
    };

    private final View.OnClickListener previousListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON PREVIOUS CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            if (initDone && trackPointer > 0) {
                changeCurrentTrack(trackPointer, trackPointer-1);
                trackPointer--;
            }
        }
    };

    private final View.OnClickListener nextListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON NEXT CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            if (initDone && trackPointer < Nrec-1) {
                changeCurrentTrack(trackPointer, trackPointer+1);
                trackPointer++;
            }
        }
    };

    ResultFragment resultListFragment = null;
    public static final String ResultListSerializedKey = "resultsList";

    private final SeekBar.OnSeekBarChangeListener ratingBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//            vibrator.vibrate(30);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_RATING_START,VibrationEffect.DEFAULT_AMPLITUDE));
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            progressBar.playSoundEffect(SoundEffectConstants.CLICK);

            if (initDone) {
                trackList.get(trackPointer).setRating(seekBar.getProgress());

                // Checks if all recordings have been rated
                if (ratingManager.isRatingFinished(trackList)) {
                    Log.i(TAG, "onStopTrackingTouch: All recording have been rated!");
                    Toast.makeText(MainActivity.this, getString(R.string.all_rated), Toast.LENGTH_SHORT).show();
                    ratingManager.setState(State.STATE_FINISHED);
                    // Ratings will now save automatically on exit or new session
                }
                setCheckMarkDrawable(trackList.get(trackPointer), ratingManager.getState());
            }
        }
    };

    private boolean isTouchingSeekBar = false; // Prevents automatic seek bar UI change while touching
    private final SeekBar.OnSeekBarChangeListener progressBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            isTouchingSeekBar = true;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            progressBar.playSoundEffect(SoundEffectConstants.CLICK);
            if (initDone && player.isPrepared() && !player.isSeeking()) {
                player.seekTo(seekBar.getProgress());
            }
            isTouchingSeekBar = false;
            player.doTick();
        }
    };

    private int animationDuration;
    private boolean isOverFlowOpen = false;
    private int toolbarHeight;
    private int TOOLBAR_DELAY;
    private final Runnable hideToolbar = new Runnable() {
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
                header_text.animate()
                        .translationY(-toolbarHeight).setDuration(animationDuration)
                        .setInterpolator(new DecelerateInterpolator());
            }
        }
    };

    private final String STATE_TRACK_POINTER = "trackPointer";
    private final String STATE_PROGRESS_BAR = "progressBar";
    private boolean loadProgressfromSavedInstance = false;
    private int savedProgress = 0;

    private final RecordingsFoundListener recordingsFoundListener = new RecordingsFoundListener() {
        @Override
        public void onFoundRecordings(ArrayList<File> raw_audio_files) {
            Nrec = raw_audio_files.size();

            Collections.sort(raw_audio_files); // Ensures the same order after loading
            // Shuffle the loaded paths randomly using the generated seed
            Collections.shuffle(raw_audio_files, new Random(ratingManager.getSeed()));
            List<Integer> rnums = linspace(1,Nrec);

            List<Recording> recordings= new ArrayList<>();
            for (int n = 0; n < Nrec; n++) {
                recordings.add(new Recording(
                        raw_audio_files.get(n).getPath(),
                        rnums.get(n),
                        ratingManager.getLastSessionRating(n))
                );
            }
            trackList = recordings;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initDone = false;
        isTouchingSeekBar = false;

        // Restore previous UI state (e.g. before configuration change)
        if (savedInstanceState != null) {
            trackPointer = savedInstanceState.getInt(STATE_TRACK_POINTER);
            savedProgress = savedInstanceState.getInt(STATE_PROGRESS_BAR);
            loadProgressfromSavedInstance = true;
        } else {
            trackPointer = 0;
            savedProgress = 0;
            loadProgressfromSavedInstance = false;
        }

        setContentView(R.layout.activity_main);

        // Toolbar setup
        animationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
        TOOLBAR_DELAY = getResources().getInteger(R.integer.TOOLBAR_HIDE_DELAY_MS);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbarHeight = toolbar.getMinimumHeight();
        handler_main = new Handler(getMainLooper());

        // UI Elements setup
        ratingbar = findViewById(R.id.ratingBar);
        button_play_pause = findViewById(R.id.button_play_pause);
        button_previous = findViewById(R.id.button_previous);
        button_next = findViewById(R.id.button_next);
        track_time = findViewById(R.id.track_time);
        header_text = findViewById(R.id.header_text);
        progressBar = findViewById(R.id.progressBar);
        checkMark = findViewById(R.id.check_mark);
        windowInsertPoint = findViewById(R.id.window_insert_point);

        // Assign listeners to all buttons
        button_play_pause.setOnClickListener(playListener);
        button_previous.setOnClickListener(previousListener);
        button_next.setOnClickListener(nextListener);
        ratingbar.setOnSeekBarChangeListener(ratingBarListener);
        progressBar.setOnSeekBarChangeListener(progressBarListener);

        // Checks for single taps on screen to hide system UI
        gestureDetector = new GestureDetector(this, new MyGestureListener());

        // Initialize the audio player and rating manager
        player = new Player(this, getPlayerListener());
        ratingManager = new RatingManager(this, recordingsFoundListener);

        // Get vibrator service for UI vibration feedback
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
        VIBRATE_RATING_START = getResources().getInteger(R.integer.VIBRATE_RATING_BAR_START_MS);

        hideSystemUI();

        // If there is no previous UI state, show welcome dialog
        // Otherwise set active track to the previous one and do nothing
        if (savedInstanceState == null) {
            // First startup
            welcome();
        } else {
            // Normal startup
            manageLoading();
        }
    }

    private void welcome() {
        // Shows welcome info dialog
        // TODO Upravite tenhle text dialogu aby odpovídal tomu že se vybírá adresář
        AlertDialog welcome_dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.welcome_message))
                .setTitle(getString(R.string.welcome_title))
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        manageLoading();
                    }
                }).create();
        welcome_dialog.setCanceledOnTouchOutside(false);
        welcome_dialog.setIcon(ContextCompat.getDrawable(this, R.mipmap.symbol_cvut_plna_verze));
        welcome_dialog.show();
    }

    private void manageLoading() {
        String message = "";
        String title = "";
        Drawable icon = null;

        switch (ratingManager.loadSession()) {
            case OK:
                manageDirectory(null);
                return;
            case NO_SESSION:
                message = getString(R.string.no_session_found);
                title = getString(R.string.info);
                icon = ContextCompat.getDrawable(this, R.drawable.ic_info);
                break;
            case CORRUPTED_SESSION:
                message = getString(R.string.corrupted_session);
                title = getString(R.string.alert);
                icon = ContextCompat.getDrawable(this, R.drawable.ic_error_red_24dp);
                break;
        }

        AlertDialog loading_failed_dialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setTitle(title)
                .setPositiveButton(R.string.create_new_session, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Go select directory and check it afterwards
                        fireSelectDirectory();
                    }
                })
                .setNegativeButton(R.string.action_quit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivity.this.finish();
                    }
                })
                .create();
        loading_failed_dialog.setCanceledOnTouchOutside(false);
        loading_failed_dialog.setIcon(icon);
        loading_failed_dialog.show();
    }

    private void fireSelectDirectory() {
        // Fire up dialog to choose the data directory
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, getResources().getInteger(R.integer.REQUEST_CODE_FOLDER_PICKER));
    }

    private void manageDirectory(File selected_dir) {
        Bundle checkResult;
        if (selected_dir == null) {
            checkResult = ratingManager.checkDataDirectoryPath();
        } else {
            checkResult = ratingManager.checkDataDirectoryPath(selected_dir);
        }

        if (checkResult.getBoolean(DIRCHECK_RESULT_IS_OK)) {
            ratingManager.setState(State.STATE_IN_PROGRESS);
            changeCurrentTrack(0, 0);
            handler_main.postDelayed(hideToolbar, TOOLBAR_DELAY);

            initDone = true;
        } else { // Directory check was not ok
            String message = "";
            DirectoryCheckError err = DirectoryCheckError.valueOf(
                    checkResult.getString(DIRCHECK_RESULT_ERROR_TYPE));
            String dirname = checkResult.getString(DIRCHECK_RESULT_DIRECTORY, "???");
            switch (err) {
                case NOT_EXIST:
                    message = getString(R.string.directory_not_exist, dirname); break;
                case NOT_DIRECTORY:
                    message = getString(R.string.directory_not_directory, dirname); break;
                case NOT_READABLE:
                    message = getString(R.string.directory_not_readable, dirname); break;
                case NO_FILES:
                    message = getString(R.string.directory_no_files); break;
                case MISSING_FILES:
                    int cnt_missing = checkResult.getInt(DIRCHECK_RESULT_MISSING_CNT);
                    String list_missing = checkResult.getString(DIRCHECK_RESULT_MISSING_LIST);
                    message = getString(R.string.directory_missing_files,
                            cnt_missing, dirname, list_missing);
                    break;
            }

            fireInvalidDirectory(message);
        }
    }

    private void fireInvalidDirectory(String message) {
        AlertDialog no_files_found_dialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setTitle(getString(R.string.alert))
                .setPositiveButton(R.string.choose_valid_data_dir, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        fireSelectDirectory();
                    }
                })
                .setNegativeButton(R.string.action_quit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivity.this.finish();
                    }
                })
                .create();
        no_files_found_dialog.setCanceledOnTouchOutside(false);
        no_files_found_dialog.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_error_red_24dp));
        no_files_found_dialog.show();
    }

    private void changeCurrentTrack(int current, int changeTo) {
        if (Nrec <= 0) {
            Log.e(TAG, "changeCurrentTrack: Error! Invalid number of tracks!");
        } else {
            if (player.isPlaying()) {
                button_play_pause.callOnClick();
            }

            // Sets active track
            try {
                player.setCurrentTrack(trackList.get(changeTo));
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Manages logical button visibility
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

            // Sets track counter text
            header_text.setText(getString(R.string.track_counter,
                    getString(R.string.track),
                    changeTo+1,
                    Nrec));

            // Set rating bar position to default or saved
            int savedRating = trackList.get(changeTo).getRating();
            int setTo = savedRating != Recording.DEFAULT_UNSET_RATING ?
                    savedRating : getResources().getInteger(R.integer.DEFAULT_PROGRESS_CENTER);

            ratingbar.setProgress(setTo, true);

            Log.i(TAG, "changeCurrentTrack: current state = " + ratingManager.getState());
            setCheckMarkDrawable(trackList.get(changeTo), ratingManager.getState());
        }
    }

    private void setCheckMarkDrawable(Recording recording, State state) {
        if (checkMark != null) {
            if (recording.getRating() == Recording.DEFAULT_UNSET_RATING) {
                checkMark.setVisibility(View.INVISIBLE);
            } else {
                if (state == State.STATE_FINISHED) {
                    checkMark.setImageDrawable(ContextCompat.getDrawable(this,
                            R.drawable.ic_done_all_green));
                } else {
                    checkMark.setImageDrawable(ContextCompat.getDrawable(this,
                            R.drawable.ic_done_green));
                }
                checkMark.setVisibility(View.VISIBLE);
            }
        } else {
            Log.e(TAG, "setCheckMarkDrawable: Not able to find checkmark view!");
        }
    }

    private Player.PlayerListener getPlayerListener() {
        return new Player.PlayerListener() {
            @Override
            public void onTrackPrepared(int duration) {
                // Enable play
                track_time.setText(formatDuration(duration));
                progressBar.setMax(duration);

                if (loadProgressfromSavedInstance) {
                    player.seekTo(savedProgress);
                    loadProgressfromSavedInstance = false;
                    savedProgress = 0;
                } else {
                    player.seekTo(0);
                }
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
                if (!isTouchingSeekBar) {
                    progressBar.setProgress(current_ms, true);
                }
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

    @SuppressWarnings("SameParameterValue")
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

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: ");
        if (player != null && player.isPlaying()) {
            button_play_pause.callOnClick();
        }

        if (initDone) {
            ratingManager.saveSession(trackList);
        }
        if (ratingManager.getState() == State.STATE_FINISHED) {
            try {
                ratingManager.saveResults(MainActivity.this, trackList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
        int itemID = item.getItemId();
        if (itemID == R.id.action_help) {
            AlertDialog help_dialog = new AlertDialog.Builder(MainActivity.this)
                    .setMessage(getString(R.string.help_message))
                    .setTitle(getString(R.string.help))
                    .setPositiveButton(R.string.ok, null)
                    .create();
            help_dialog.setIcon(ContextCompat.getDrawable(this,
                    android.R.drawable.ic_menu_help));
            help_dialog.show();
            return true;
        } else if (itemID == R.id.action_save && initDone) {
            try {
                ratingManager.saveResults(MainActivity.this, trackList);

                Toast.makeText(MainActivity.this, getString(R.string.save_success),
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, getString(R.string.save_failed, e.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (itemID == R.id.action_show_saved_results && initDone) {
            // TODO check
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();

            Log.i(TAG, "onOptionsItemSelected: Window insert point children count == " + windowInsertPoint.getChildCount());
            if (windowInsertPoint.getChildCount() == 0) {
                LayoutInflater inflater = getLayoutInflater();
                View resultsWindow = inflater.inflate(R.layout.results_window, windowInsertPoint);
//                windowInsertPoint.addView(resultsWindow); // TODO Check if necessary

                try {
                    ArrayList<RatingResult> ratings = ratingManager.loadResults(MainActivity.this);

                    if (resultListFragment == null) {
                        resultListFragment = ResultFragment.newInstance(1, ratings);
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                        ft.add(R.id.window_insert_point, resultListFragment);
                    } else if (resultListFragment.isAdded()) {
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                        ft.remove(resultListFragment);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, getString(R.string.ratings_loading_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                }
            } else {
                if (resultListFragment != null && resultListFragment.isAdded()) {
                    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                    ft.remove(resultListFragment);
                }
                windowInsertPoint.removeAllViews();
            }

            ft.commit();
            return true;
        } else if (itemID == R.id.action_show_session_info && initDone) {
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
            info_dialog.setIcon(ContextCompat.getDrawable(this,
                    android.R.drawable.ic_dialog_info));
            info_dialog.show();
            return true;
        } else if (itemID == R.id.action_reset_ratings && initDone) {
            AlertDialog confirm_dialog = new AlertDialog.Builder(MainActivity.this)
                    .setMessage(getString(R.string.reset_confirm_prompt))
                    .setTitle(getString(R.string.alert))
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (ratingManager.getState() == State.STATE_FINISHED) {
                                try {
                                    ratingManager.saveResults(MainActivity.this, trackList);

                                    Toast.makeText(MainActivity.this, getString(R.string.save_success),
                                            Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    e.printStackTrace();

                                    AlertDialog saveFailDialog = new AlertDialog.Builder(MainActivity.this)
                                            .setMessage(getString(R.string.save_failed_continue_question))
                                            .setTitle(getString(R.string.save_failed_continue_title))
                                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    initDone = false;
                                                    isTouchingSeekBar = false;
                                                    trackPointer = 0;
                                                    savedProgress = 0;
                                                    loadProgressfromSavedInstance = false;
                                                    ratingManager.wipeCurrentSession();
                                                    fireSelectDirectory();
                                                }
                                            })
                                            .setNegativeButton(R.string.cancel, null)
                                            .create();
                                    saveFailDialog.setIcon(ContextCompat.getDrawable(MainActivity.this,
                                            android.R.drawable.stat_sys_warning));
                                    saveFailDialog.show();
                                }
                            }

                            initDone = false;
                            isTouchingSeekBar = false;
                            trackPointer = 0;
                            savedProgress = 0;
                            loadProgressfromSavedInstance = false;
                            ratingManager.wipeCurrentSession();
                            fireSelectDirectory();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            confirm_dialog.setIcon(ContextCompat.getDrawable(this,
                    android.R.drawable.stat_sys_warning));
            confirm_dialog.show();
            return true;
        } else if (itemID == R.id.action_quit) {
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
            close_dialog.setIcon(ContextCompat.getDrawable(this,
                    android.R.drawable.ic_menu_close_clear_cancel));
            close_dialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (velocityY >= 5000) {
                Log.i(TAG, "onFling: Showing action bar...");
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
                    header_text.animate()
                            .translationY(0).setDuration(animationDuration)
                            .setInterpolator(new AccelerateInterpolator());
                }
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_TRACK_POINTER, trackPointer);
        outState.putInt(STATE_PROGRESS_BAR, progressBar.getProgress());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == getResources().getInteger(R.integer.REQUEST_CODE_FOLDER_PICKER) && data != null) {
            String fullpath = getFullPathFromTreeUri(data.getData(), this);

            if (fullpath == null || fullpath.equals("")) {
                AlertDialog failed_dialog = new AlertDialog.Builder(MainActivity.this)
                        .setMessage(getString(R.string.internal_error_URI_extract_failed))
                        .setTitle(getString(R.string.error))
                        .setPositiveButton(R.string.action_quit, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MainActivity.this.finish();
                            }
                        })
                        .create();
                failed_dialog.setIcon(ContextCompat.getDrawable(this,
                        android.R.drawable.ic_menu_close_clear_cancel));
                failed_dialog.setCanceledOnTouchOutside(false);
                failed_dialog.setCancelable(false);
                failed_dialog.show();
            } else {
                getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                File datadir = new File(fullpath);
                Log.i(TAG, "onActivityResult: pickedDir == " + datadir.getAbsolutePath());

                manageDirectory(datadir);
            }
        }
    }

}
