package com.petrkryze.vas.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.Player;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.Recording;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import static com.petrkryze.vas.DocumentUtils.getFullPathFromTreeUri;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_DIRECTORY;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_ERROR_TYPE;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_IS_OK;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_MISSING_CNT;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_MISSING_LIST;

/**
 * Created by Petr on 04.05.2021. Yay!
 */
public class RatingFragment extends Fragment {

    private static final String TAG = "RatingFragment";

    private SeekBar VASratingBar;
    private Button button_play_pause;
    private Button button_previous;
    private Button button_next;
    private TextView track_time;
    private TextView header_text;
    private SeekBar playerSeekBar;
    private ImageView checkMark;

    private Vibrator vibrator;
    private int VIBRATE_BUTTON_MS;
    private int VIBRATE_RATING_START;

    private Player player;
    private RatingManager ratingManager;

    private boolean isOverFlowOpen = false;
    private boolean initDone = false;
    private boolean isLoadingPlayProgress = false;

    private final View.OnClickListener playListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON PLAY CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            if (initDone && player.isPrepared() && !player.isSeeking()) {
                if (player.play()) {
                    button_play_pause.setText(getString(R.string.button_pause_label));
                    button_play_pause.setCompoundDrawablesWithIntrinsicBounds(null,
                            ContextCompat.getDrawable(requireContext(),
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
                        ContextCompat.getDrawable(requireContext(),
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
            int trackPointer = ratingManager.getTrackPointer();
            if (initDone && trackPointer > 0) {
                changeCurrentTrack(trackPointer, trackPointer-1);
                ratingManager.trackDecrement();
            }
        }
    };

    private final View.OnClickListener nextListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i(TAG, "onClick: BUTTON NEXT CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            int trackPointer = ratingManager.getTrackPointer();
            if (initDone && trackPointer < ratingManager.getTrackN()-1) {
                changeCurrentTrack(trackPointer, trackPointer+1);
                ratingManager.trackIncrement();
            }
        }
    };

    // VAS rating bar - enables users to rate current playing track in terms of overall intelligibility
    private final SeekBar.OnSeekBarChangeListener VASratingBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_RATING_START,VibrationEffect.DEFAULT_AMPLITUDE));
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            VASratingBar.playSoundEffect(SoundEffectConstants.CLICK);

            if (initDone) {
                ratingManager.getTrackList().get(ratingManager.getTrackPointer()).setRating(seekBar.getProgress());

                // Checks if all recordings have been rated
                if (ratingManager.isRatingFinished(ratingManager.getTrackList())) {
                    Log.i(TAG, "onStopTrackingTouch: All recording have been rated!");
                    Toast.makeText(requireContext(), getString(R.string.all_rated), Toast.LENGTH_SHORT).show();
                    ratingManager.setState(RatingManager.State.STATE_FINISHED);
                    // Ratings will now save automatically on exit or new session
                }
                setCheckMarkDrawable(ratingManager.getTrackList().get(ratingManager.getTrackPointer()),
                        ratingManager.getState());
            }
        }
    };

    // Seek bar - enables users to seek back and forth over the current audio being played
    // Also indicated the progress
    private boolean isTouchingSeekBar = false; // Prevents automatic seek bar UI change while touching
    private final SeekBar.OnSeekBarChangeListener playerSeekBarListener = new SeekBar.OnSeekBarChangeListener() {
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
            playerSeekBar.playSoundEffect(SoundEffectConstants.CLICK);
            if (initDone && player.isPrepared() && !player.isSeeking()) {
                player.seekTo(seekBar.getProgress());
                ratingManager.setPlayProgress(seekBar.getProgress());
            }
            isTouchingSeekBar = false;
            player.doTick();
        }
    };

    ActivityResultLauncher<Uri> selectDirectory = registerForActivityResult(
            new SelectDirectory(),
            resultUri -> {
                if (resultUri != null) {
                    String fullpath = getFullPathFromTreeUri(resultUri, requireContext());

                    if (fullpath == null || fullpath.equals("")) {
                        AlertDialog failed_dialog = new AlertDialog.Builder(requireContext())
                                .setMessage(getString(R.string.internal_error_URI_extract_failed))
                                .setTitle(getString(R.string.error))
                                .setPositiveButton(R.string.action_quit, (dialog, which) -> requireActivity().finish())
                                .create();
                        failed_dialog.setIcon(ContextCompat.getDrawable(requireContext(),
                                android.R.drawable.ic_menu_close_clear_cancel));
                        failed_dialog.setCanceledOnTouchOutside(false);
                        failed_dialog.setCancelable(false);
                        failed_dialog.show();
                    } else {
                        requireContext().getContentResolver().takePersistableUriPermission(resultUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        File datadir = new File(fullpath);
                        Log.i(TAG, "onActivityResult: pickedDir == " + datadir.getAbsolutePath());

                        // Go check the selected directory and it's contents
                        manageDirectory(datadir);
                    }
                }
            }
    );

    public RatingFragment() {
    }

    @SuppressWarnings("unused")
    public static RatingFragment newInstance() {
        RatingFragment fragment = new RatingFragment();
        Bundle args = new Bundle();

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.i(TAG, "onCreate: Creating");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        initDone = false;
        isTouchingSeekBar = false;
        isLoadingPlayProgress = false;

        // Get vibrator service for UI vibration feedback
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
        VIBRATE_RATING_START = getResources().getInteger(R.integer.VIBRATE_RATING_BAR_START_MS);

        // Initialize the audio player and rating manager
        player = new Player(requireContext(), getPlayerListener());
        ratingManager = new RatingManager(requireActivity());

        // Normal startup
        manageLoading();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rating, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // UI Elements setup
        VASratingBar = view.findViewById(R.id.ratingBar);
        button_play_pause = view.findViewById(R.id.button_play_pause);
        button_previous = view.findViewById(R.id.button_previous);
        button_next = view.findViewById(R.id.button_next);
        track_time = view.findViewById(R.id.track_time);
        header_text = view.findViewById(R.id.header_text);
        playerSeekBar = view.findViewById(R.id.progressBar);
        checkMark = view.findViewById(R.id.check_mark);

        // Assign listeners to all buttons
        button_play_pause.setOnClickListener(playListener);
        button_previous.setOnClickListener(previousListener);
        button_next.setOnClickListener(nextListener);
        VASratingBar.setOnSeekBarChangeListener(VASratingBarListener);
        playerSeekBar.setOnSeekBarChangeListener(playerSeekBarListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initDone) {
            isLoadingPlayProgress = true;
            changeCurrentTrack(0, ratingManager.getTrackPointer());
        }
    }

    private void manageLoading() {
        String message = "";
        String title = "";
        Drawable icon = null;

        switch (ratingManager.loadSession()) {
            case OK:
                manageDirectory(new File(ratingManager.getDataDirPath()));
                return;
            case NO_SESSION:
                message = getString(R.string.no_session_found);
                title = getString(R.string.info);
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_info);
                break;
            case CORRUPTED_SESSION:
                message = getString(R.string.corrupted_session);
                title = getString(R.string.alert);
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_error_red_24dp);
                break;
        }

        AlertDialog loading_failed_dialog = new AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setTitle(title)
                .setPositiveButton(R.string.create_new_session, (dialog, which) -> {
                    // Go select directory and check it afterwards
                    fireSelectDirectory();
                })
                .setNegativeButton(R.string.action_quit, (dialog, which) -> requireActivity().finish())
                .create();
        loading_failed_dialog.setCanceledOnTouchOutside(false);
        loading_failed_dialog.setIcon(icon);
        loading_failed_dialog.show();
    }

    private void manageDirectory(File selected_dir) {
        Bundle checkResult = ratingManager.checkDataDirectoryPath(
                selected_dir,
                this,
                () -> {
                    Log.i(TAG, "manageDirectory: Group check done callback!");
                    ratingManager.setState(RatingManager.State.STATE_IN_PROGRESS);
                    initDone = true;
                });

        if (checkResult.getBoolean(DIRCHECK_RESULT_IS_OK)) {
            // Directory is ok and we can proceed to actual rating 😊
            Log.i(TAG, "manageDirectory: Complex directory check passed, waiting for callback!");
        } else { // Directory check was not ok
            String message = "";
            RatingManager.DirectoryCheckError err = RatingManager.DirectoryCheckError.valueOf(
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

    private void fireSelectDirectory() {
        // Fire up dialog to choose the data directory
        selectDirectory.launch(null);
    }

    private void fireInvalidDirectory(String message) {
        AlertDialog no_files_found_dialog = new AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setTitle(getString(R.string.alert))
                .setPositiveButton(R.string.choose_valid_data_dir, (dialog, which) -> fireSelectDirectory())
                .setNegativeButton(R.string.action_quit, (dialog, which) -> requireActivity().finish())
                .create();
        no_files_found_dialog.setCanceledOnTouchOutside(false);
        no_files_found_dialog.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_error_red_24dp));
        no_files_found_dialog.show();
    }

    private void changeCurrentTrack(int current, int changeTo) {
        if (ratingManager.getTrackN() <= 0) {
            Log.e(TAG, "changeCurrentTrack: Error! Invalid number of tracks!");
        } else {
            if (player.isPlaying()) {
                button_play_pause.callOnClick();
            }

            // Sets active track
            try {
                player.setCurrentTrack(ratingManager.getTrackList().get(changeTo));
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Manages logical button visibility
            if (changeTo == 0) {
                // Hide previous button
                button_previous.setVisibility(View.INVISIBLE);
            } else if (changeTo == ratingManager.getTrackN()-1) {
                // Hide next button
                button_next.setVisibility(View.INVISIBLE);
            } else if (current == 0 && changeTo == 1) {
                // Show previous button
                button_previous.setVisibility(View.VISIBLE);
            } else if (current == ratingManager.getTrackN()-1 && changeTo == (ratingManager.getTrackN()-2)) {
                // Show next button
                button_next.setVisibility(View.VISIBLE);
            }

            // Sets track counter text
            header_text.setText(getString(R.string.track_counter,
                    getString(R.string.track),
                    changeTo+1,
                    ratingManager.getTrackN()));

            // Set rating bar position to default or saved
            int savedRating = ratingManager.getTrackList().get(changeTo).getRating();
            int setTo = savedRating != Recording.DEFAULT_UNSET_RATING ?
                    savedRating : getResources().getInteger(R.integer.DEFAULT_PROGRESS_CENTER);

            VASratingBar.setProgress(setTo, true);

            Log.i(TAG, "changeCurrentTrack: current state = " + ratingManager.getState());
            setCheckMarkDrawable(ratingManager.getTrackList().get(changeTo),
                    ratingManager.getState());
        }
    }

    private void setCheckMarkDrawable(Recording recording, RatingManager.State state) {
        if (checkMark != null) {
            if (recording.getRating() == Recording.DEFAULT_UNSET_RATING) {
                checkMark.setVisibility(View.INVISIBLE);
            } else {
                if (state == RatingManager.State.STATE_FINISHED) {
                    checkMark.setImageDrawable(ContextCompat.getDrawable(requireContext(),
                            R.drawable.ic_done_all_green));
                } else {
                    checkMark.setImageDrawable(ContextCompat.getDrawable(requireContext(),
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
                playerSeekBar.setMax(duration);

                if (isLoadingPlayProgress) {
                    player.seekTo(ratingManager.getSavedPlayProgress());
                    isLoadingPlayProgress = false;
                } else {
                    player.seekTo(0);
                    ratingManager.setPlayProgress(0);
                }
            }

            @Override
            public void onTrackFinished() {
                button_play_pause.callOnClick();
                player.rewind();
                ratingManager.setPlayProgress(0);
            }

            @Override
            public void onHeadphonesMissing() {
                Toast.makeText(requireContext(),
                        getString(R.string.toast_connect_headphones), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVolumeDown() {
                Toast.makeText(requireContext(),
                        getString(R.string.toast_increase_volume), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTimeTick(final int time) {
                requireActivity().runOnUiThread(() -> track_time.setText(formatDuration(time)));
            }

            @Override
            public void onUpdateProgress(int current_ms) {
                if (!isTouchingSeekBar) {
                    playerSeekBar.setProgress(current_ms, true);
                    ratingManager.setPlayProgress(current_ms);
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

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: Pausing");
        if (player != null && player.isPlaying()) {
            button_play_pause.callOnClick();
        }

        // Saves the session on every Pause occasion (change app, phone lock, change screen, ...)
        if (initDone) {
            ratingManager.saveSession();
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy: Destroying");
        super.onDestroy();
        // Saves the results to the .txt file in memory if the rating is in a finished state
        if (ratingManager.getState() == RatingManager.State.STATE_FINISHED) {
            try {
                ratingManager.saveResults(requireContext());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (player != null) {
            player.clean();
            player = null;
        }
        vibrator = null;
        ratingManager = null;
        initDone = false;
        isLoadingPlayProgress = false;
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull @NotNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        int[] toEnable = {R.id.action_menu_help, R.id.action_menu_save,
                R.id.action_menu_show_session_info, R.id.action_menu_reset_ratings,
                R.id.action_menu_show_saved_results, R.id.action_menu_quit};

        for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_menu_help && initDone) {
            String contextHelpMessage = getString(R.string.help_context_body_rating_fragment);
            String contextHelpTitle = getString(R.string.help_context_title_rating_fragment);

            NavDirections directions =
                    RatingFragmentDirections.
                            actionRatingFragmentToHelpFragment(contextHelpMessage, contextHelpTitle);
            NavHostFragment.findNavController(this).navigate(directions);
            return true;
        } else if (itemID == R.id.action_menu_save && initDone) {
            try {
                ratingManager.saveResults(requireContext());

                Toast.makeText(requireContext(), getString(R.string.save_success),
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), getString(R.string.save_failed, e.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (itemID == R.id.action_menu_show_saved_results && initDone) {
            // TODO check
            try {
                ArrayList<RatingResult> ratings = RatingManager.loadResults(requireContext());

                NavDirections directions =
                        RatingFragmentDirections.actionRatingFragmentToResultFragment(ratings);
                NavHostFragment.findNavController(this).navigate(directions);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), getString(R.string.ratings_loading_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (itemID == R.id.action_menu_show_session_info && initDone) {
            ratingManager.saveSession();
            MainActivity.navigateToCurrentSessionInfo(
                    this, session -> {
                        NavDirections directions = RatingFragmentDirections
                                .actionRatingFragmentToCurrentSessionInfoFragment(session);
                        NavHostFragment.findNavController(RatingFragment.this)
                                .navigate(directions);
                    }
            );
        } else if (itemID == R.id.action_menu_reset_ratings && initDone) {
            AlertDialog confirm_dialog = new AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.reset_confirm_prompt))
                    .setTitle(getString(R.string.alert))
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        if (ratingManager.getState() == RatingManager.State.STATE_FINISHED) {
                            try {
                                ratingManager.saveResults(requireContext());

                                Toast.makeText(requireContext(), getString(R.string.save_success),
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                e.printStackTrace();

                                AlertDialog saveFailDialog = new AlertDialog.Builder(requireContext())
                                        .setMessage(getString(R.string.save_failed_continue_question))
                                        .setTitle(getString(R.string.save_failed_continue_title))
                                        .setPositiveButton(R.string.ok, (dialog1, which1) -> {
                                            initDone = false;
                                            isTouchingSeekBar = false;

                                            ratingManager.wipeCurrentSession();
                                            fireSelectDirectory();
                                        })
                                        .setNegativeButton(R.string.cancel, null)
                                        .create();
                                saveFailDialog.setIcon(ContextCompat.getDrawable(requireContext(),
                                        android.R.drawable.stat_sys_warning));
                                saveFailDialog.show();
                            }
                        }

                        initDone = false;
                        isTouchingSeekBar = false;

                        ratingManager.wipeCurrentSession();
                        fireSelectDirectory();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            confirm_dialog.setIcon(ContextCompat.getDrawable(requireContext(),
                    android.R.drawable.stat_sys_warning));
            confirm_dialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Handles the auto-hide feature of toolbar to pause when overflow is opened
    public void onMenuOpened() {
        if (!isOverFlowOpen) {
            if (player != null && player.isPlaying()) {
                button_play_pause.callOnClick();
            }
        }
        isOverFlowOpen = true;
    }

    // Re-enables the auto-hide after the overflow is closed
    public void onPanelClosed() {
        isOverFlowOpen = false;
    }

    private static class SelectDirectory extends ActivityResultContracts.OpenDocumentTree {
        @NonNull
        @NotNull
        @Override
        public Intent createIntent(@NonNull Context context, @Nullable Uri input) {
            Intent intent = super.createIntent(context, input);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return intent;
        }
    }
}
