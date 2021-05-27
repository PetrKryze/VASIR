package com.petrkryze.vas;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import static android.app.Activity.RESULT_OK;
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

    private SeekBar ratingbar;
    private Button button_play_pause;
    private Button button_previous;
    private Button button_next;
    private TextView track_time;
    private TextView header_text;
    private SeekBar progressBar;
    private ImageView checkMark;

    private Vibrator vibrator;
    public static int VIBRATE_BUTTON_MS;
    public static int VIBRATE_RATING_START;

    private int Nrec = 0;
    private int trackPointer = 0;

    private List<Recording> trackList;
    private Player player;
    private RatingManager ratingManager;

    private boolean isOverFlowOpen = false;
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
                    Toast.makeText(requireContext(), getString(R.string.all_rated), Toast.LENGTH_SHORT).show();
                    ratingManager.setState(RatingManager.State.STATE_FINISHED);
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

    private final String STATE_TRACK_POINTER = "trackPointer";
    private final String STATE_PROGRESS_BAR = "progressBar";
    private boolean loadProgressfromSavedInstance = false;
    private int savedProgress = 0;

    private final RatingManager.RecordingsFoundListener recordingsFoundListener = new RatingManager.RecordingsFoundListener() {
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

        // Restore previous UI state (e.g. before configuration change)
        if (savedInstanceState != null) {
            Log.i(TAG, "onCreate: Loading instance state");
            // Mainly for configuration changes - portrait to landscape
            trackPointer = savedInstanceState.getInt(STATE_TRACK_POINTER);
            savedProgress = savedInstanceState.getInt(STATE_PROGRESS_BAR);
            loadProgressfromSavedInstance = true;
        } else {
            trackPointer = 0;
            savedProgress = 0;
            loadProgressfromSavedInstance = false;
        }

        // Get vibrator service for UI vibration feedback
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
        VIBRATE_RATING_START = getResources().getInteger(R.integer.VIBRATE_RATING_BAR_START_MS);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.rating_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // UI Elements setup
        ratingbar = view.findViewById(R.id.ratingBar);
        button_play_pause = view.findViewById(R.id.button_play_pause);
        button_previous = view.findViewById(R.id.button_previous);
        button_next = view.findViewById(R.id.button_next);
        track_time = view.findViewById(R.id.track_time);
        header_text = view.findViewById(R.id.header_text);
        progressBar = view.findViewById(R.id.progressBar);
        checkMark = view.findViewById(R.id.check_mark);

        // Assign listeners to all buttons
        button_play_pause.setOnClickListener(playListener);
        button_previous.setOnClickListener(previousListener);
        button_next.setOnClickListener(nextListener);
        ratingbar.setOnSeekBarChangeListener(ratingBarListener);
        progressBar.setOnSeekBarChangeListener(progressBarListener);

        // Initialize the audio player and rating manager
        player = new Player(requireContext(), getPlayerListener());
        ratingManager = new RatingManager(requireActivity(), recordingsFoundListener);

        // Normal startup
        manageLoading();
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
                        requireActivity().finish();
                    }
                })
                .create();
        loading_failed_dialog.setCanceledOnTouchOutside(false);
        loading_failed_dialog.setIcon(icon);
        loading_failed_dialog.show();
    }

    private void manageDirectory(File selected_dir) {
        Bundle checkResult;
        if (selected_dir == null) {
            checkResult = ratingManager.checkDataDirectoryPath();
        } else {
            checkResult = ratingManager.checkDataDirectoryPath(selected_dir);
        }

        if (checkResult.getBoolean(DIRCHECK_RESULT_IS_OK)) {
            ratingManager.setState(RatingManager.State.STATE_IN_PROGRESS);
            changeCurrentTrack(0, 0);
            initDone = true;
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
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, getResources().getInteger(R.integer.REQUEST_CODE_FOLDER_PICKER));
    }

    private void fireInvalidDirectory(String message) {
        AlertDialog no_files_found_dialog = new AlertDialog.Builder(requireContext())
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
                        requireActivity().finish();
                    }
                })
                .create();
        no_files_found_dialog.setCanceledOnTouchOutside(false);
        no_files_found_dialog.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_error_red_24dp));
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
                requireActivity().runOnUiThread(new Runnable() {
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
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: Pausing");
        if (player != null && player.isPlaying()) {
            button_play_pause.callOnClick();
        }

        if (initDone) {
            ratingManager.saveSession(trackList);
        }
        if (ratingManager.getState() == RatingManager.State.STATE_FINISHED) {
            try {
                ratingManager.saveResults(requireContext(), trackList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy: Destroying");
        super.onDestroy();
        player.clean();
        player = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_help && initDone) {
            // TODO Check
            NavDirections directions = RatingFragmentDirections.actionRatingFragmentToHelpFragment("a","b");
            NavHostFragment.findNavController(this).navigate(directions);
        } else if (itemID == R.id.action_save && initDone) {
            try {
                ratingManager.saveResults(requireContext(), trackList);

                Toast.makeText(requireContext(), getString(R.string.save_success),
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), getString(R.string.save_failed, e.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (itemID == R.id.action_show_saved_results && initDone) {
            // TODO check
            try {
                ArrayList<RatingResult> ratings = ratingManager.loadResults(requireContext());

                NavDirections directions =
                        RatingFragmentDirections.actionRatingFragmentToResultFragment(ratings);
                NavHostFragment.findNavController(this).navigate(directions);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), getString(R.string.ratings_loading_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }

            return true;
        } else if (itemID == R.id.action_show_session_info && initDone) {
            String message = getString(R.string.session_info_message,
                    String.valueOf(ratingManager.getSession_ID()),
                    ratingManager.getGeneratorMessage(),
                    String.valueOf(ratingManager.getSeed()),
                    ratingManager.getRatingFinishedRatio(trackList)
            );

            AlertDialog info_dialog = new AlertDialog.Builder(requireContext())
                    .setMessage(message)
                    .setTitle(getString(R.string.session_info_title))
                    .setPositiveButton(getString(R.string.ok), null)
                    .create();
            info_dialog.setIcon(ContextCompat.getDrawable(requireContext(),
                    android.R.drawable.ic_dialog_info));
            info_dialog.show();
            return true;
        } else if (itemID == R.id.action_reset_ratings && initDone) {
            AlertDialog confirm_dialog = new AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.reset_confirm_prompt))
                    .setTitle(getString(R.string.alert))
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (ratingManager.getState() == RatingManager.State.STATE_FINISHED) {
                                try {
                                    ratingManager.saveResults(requireContext(), trackList);

                                    Toast.makeText(requireContext(), getString(R.string.save_success),
                                            Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    e.printStackTrace();

                                    AlertDialog saveFailDialog = new AlertDialog.Builder(requireContext())
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
                                    saveFailDialog.setIcon(ContextCompat.getDrawable(requireContext(),
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == getResources().getInteger(R.integer.REQUEST_CODE_FOLDER_PICKER) && data != null) {
            String fullpath = getFullPathFromTreeUri(data.getData(), requireContext());

            if (fullpath == null || fullpath.equals("")) {
                AlertDialog failed_dialog = new AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.internal_error_URI_extract_failed))
                        .setTitle(getString(R.string.error))
                        .setPositiveButton(R.string.action_quit, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requireActivity().finish();
                            }
                        })
                        .create();
                failed_dialog.setIcon(ContextCompat.getDrawable(requireContext(),
                        android.R.drawable.ic_menu_close_clear_cancel));
                failed_dialog.setCanceledOnTouchOutside(false);
                failed_dialog.setCancelable(false);
                failed_dialog.show();
            } else {
                requireContext().getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                File datadir = new File(fullpath);
                Log.i(TAG, "onActivityResult: pickedDir == " + datadir.getAbsolutePath());

                manageDirectory(datadir);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Log.i(TAG, "onSaveInstanceState: Saving instance state.");
        // Mainly for configuration changes - portrait to landscape
        outState.putInt(STATE_TRACK_POINTER, trackPointer);
        outState.putInt(STATE_PROGRESS_BAR, progressBar.getProgress());
        super.onSaveInstanceState(outState);
    }

}
