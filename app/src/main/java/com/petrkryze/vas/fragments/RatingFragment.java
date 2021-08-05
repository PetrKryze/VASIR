package com.petrkryze.vas.fragments;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.GroupFolder;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.Player;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager;
import com.petrkryze.vas.RatingManager.State;
import com.petrkryze.vas.RatingResult;
import com.petrkryze.vas.Recording;
import com.petrkryze.vas.databinding.FragmentRatingBinding;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import static com.petrkryze.vas.MainActivity.applyTintFilter;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_DIRECTORY;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_ERROR_TYPE;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_IS_OK;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_MISSING_CNT;
import static com.petrkryze.vas.RatingManager.DIRCHECK_RESULT_MISSING_LIST;
import static com.petrkryze.vas.RatingManager.State.STATE_FINISHED;
import static com.petrkryze.vas.RatingManager.State.STATE_IDLE;
import static com.petrkryze.vas.RatingManager.State.STATE_IN_PROGRESS;

/**
 * Created by Petr on 04.05.2021. Yay!
 */
@SuppressLint("ShowToast")
public class RatingFragment extends Fragment {
    private static final String TAG = "RatingFragment";

    private FragmentRatingBinding binding;
    private SeekBar VASratingBar;
    private SeekBar playerProgressBar;
    private Button buttonPlayPause;
    private Button buttonPrevious;
    private Button buttonNext;
    private TextView playerTimeTracker;
    private TextView playerTimeTotal;
    private TextView headerText;
    private ImageView checkMarkIcon;

    private Player player;
    private RatingManager ratingManager;

    private boolean loadingFinishedFlag = false;
    private boolean initDone = false;
    private boolean isSeekingFromLoadedValue = false;

    private Vibrator vibrator;
    private static int VIBRATE_BUTTON_MS;
    private static int VIBRATE_BUTTON_LONG_MS;
    private static int VIBRATE_RATING_START_MS;

    private ButtonState playPauseButtonState = ButtonState.PAUSED;
    private enum ButtonState {PLAYING, PAUSED}
    private Drawable playIcon;
    private Drawable pauseIcon;
    private final View.OnClickListener playListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: BUTTON PLAY CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            if (initDone && !player.isSeeking() && playPauseButtonState == ButtonState.PAUSED) {
                if (player.start()) {
                    buttonPlayPause.setText(getString(R.string.button_pause_label));
                    buttonPlayPause.setCompoundDrawablesWithIntrinsicBounds(null, pauseIcon, null, null);
                    buttonPlayPause.setOnClickListener(pauseListener);
                    playPauseButtonState = ButtonState.PLAYING;
                }
            }
        }
    };
    private final View.OnClickListener pauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: BUTTON PAUSE CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            if (initDone && !player.isSeeking() && playPauseButtonState == ButtonState.PLAYING) {
                if (player.pause()) {
                    buttonPlayPause.setText(getString(R.string.button_play_label));
                    buttonPlayPause.setCompoundDrawablesWithIntrinsicBounds(null, playIcon, null, null);
                    buttonPlayPause.setOnClickListener(playListener);
                    playPauseButtonState = ButtonState.PAUSED;
                }
            }
        }
    };

    private final View.OnClickListener previousListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: BUTTON PREVIOUS CLICKED");
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
            Log.d(TAG, "onClick: BUTTON NEXT CLICKED");
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            int trackPointer = ratingManager.getTrackPointer();
            if (initDone && trackPointer < ratingManager.getTrackN()-1) {
                changeCurrentTrack(trackPointer, trackPointer+1);
                ratingManager.trackIncrement();
            }
        }
    };

    private ValueAnimator playerButtonClickAnimation;
    private final View.OnLongClickListener previousLongListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (v.getId() == buttonPrevious.getId()) {
                Log.d(TAG, "onLongClick: BUTTON PREVIOUS LONG CLICKED");
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_LONG_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                int trackPointer = ratingManager.getTrackPointer();
                if (initDone && trackPointer > 0) {
                    changeCurrentTrack(trackPointer, 0);
                    ratingManager.trackToFirst();
                }
                return true;
            }
            return false;
        }
    };

    private final View.OnLongClickListener nextLongListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (v.getId() == buttonNext.getId()) {
                Log.d(TAG, "onLongClick: BUTTON NEXT LONG CLICKED");
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_LONG_MS, VibrationEffect.DEFAULT_AMPLITUDE));

                int trackPointer = ratingManager.getTrackPointer();
                if (initDone && trackPointer < ratingManager.getTrackN()-1) {
                    changeCurrentTrack(trackPointer, ratingManager.getTrackN() - 1);
                    ratingManager.trackToLast();
                }
                return true;
            }
            return false;
        }
    };

    // VAS rating bar - enables users to rate current playing track in terms of overall intelligibility
    private final SeekBar.OnSeekBarChangeListener VASratingBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_RATING_START_MS,VibrationEffect.DEFAULT_AMPLITUDE));
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            VASratingBar.playSoundEffect(SoundEffectConstants.CLICK);

            if (initDone) { // Set the rating
                ratingManager.getTrackList().get(ratingManager.getTrackPointer()).setRating(seekBar.getProgress());

                if (ratingManager.isRatingFinished()) { // Checks if all recordings have been rated
                    Log.i(TAG, "onStopTrackingTouch: All recording have been rated!");
                    ratingManager.setState(STATE_FINISHED);
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
            playerTimeTracker.setText(formatDuration(progress));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_BUTTON_MS,VibrationEffect.DEFAULT_AMPLITUDE));
            isTouchingSeekBar = true;
            player.dontTick();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            playerProgressBar.playSoundEffect(SoundEffectConstants.CLICK);
            if (initDone) {
                player.seekTo(seekBar.getProgress());
                ratingManager.setPlayProgress(seekBar.getProgress());
            }
            isTouchingSeekBar = false;
        }
    };

    ActivityResultLauncher<Uri> selectDirectory = registerForActivityResult(
            new SelectDirectory(),
            resultUri -> {
                if (resultUri != null) {
                    String fullPath = resultUri.getPath();
                    if (fullPath == null || fullPath.equals("")) { // Bad returned path somehow
                        new MaterialAlertDialogBuilder(RatingFragment.this.requireContext())
                                .setTitle(RatingFragment.this.getString(R.string.dialog_internal_error_title))
                                .setMessage(RatingFragment.this.getString(R.string.dialog_internal_error_message))
                                .setIcon(applyTintFilter(
                                        ContextCompat.getDrawable(RatingFragment.this.requireContext(), R.drawable.ic_error),
                                        RatingFragment.this.requireContext().getColor(R.color.errorColor)))
                                .setPositiveButton(R.string.dialog_internal_error_quit, (dialog, which) -> RatingFragment.this.requireActivity().finish())
                                .setCancelable(false).show();
                    } else { // Returned path seems ok, start the checks
                        Log.d(TAG, "onActivityResult: Selected directory: " + fullPath);
                        RatingFragment.this.requireContext().getContentResolver()
                                .takePersistableUriPermission(resultUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        // Go check the selected directory and it's contents
                        showLoading();
                        new Thread(() -> RatingFragment.this.manageDirectory(resultUri, true)
                                , "GroupControlCheckThread").start();
                    }
                } else {
                    Log.d(TAG, "onActivityResult: User left the directory selection activity without selecting.");
                    RatingFragment.this.fireSessionCreationCancelled();
                    RatingFragment.this.manageLoading(); // Cycle back to the session check
                }
            }
    );

    public RatingFragment() {
        // Required empty public constructor
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
        super.onCreate(savedInstanceState);
        showLoading();
        loadingFinishedFlag = false;
        initDone = false;
        isTouchingSeekBar = false;
        isSeekingFromLoadedValue = false;

        // Get vibrator service for UI vibration feedback
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        VIBRATE_BUTTON_MS = getResources().getInteger(R.integer.VIBRATE_BUTTON_MS);
        VIBRATE_BUTTON_LONG_MS = getResources().getInteger(R.integer.VIBRATE_LONG_CLICK_MS);
        VIBRATE_RATING_START_MS = getResources().getInteger(R.integer.VIBRATE_RATING_BAR_START_MS);

        // Get colorPrimarySurface - resolve the ?attr
        TypedValue typedValue = new TypedValue();
        requireActivity().getTheme().resolveAttribute(R.attr.colorPrimarySurface, typedValue, true);

        int colorFrom;
        if (typedValue.data != TypedValue.DATA_NULL_EMPTY &&
                (typedValue.type == TypedValue.TYPE_INT_COLOR_ARGB8 || typedValue.type == TypedValue.TYPE_INT_COLOR_RGB8)) {
            colorFrom = typedValue.data;
        } else {
            colorFrom = ContextCompat.getColor(requireContext(), R.color.primaryColor);
        }
        // Setup long click animation
        int colorTo = ContextCompat.getColor(requireContext(), R.color.secondaryColor);
        playerButtonClickAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo)
                .setDuration(100);

        // Initialize the audio player and rating manager
        player = new Player(requireContext(), getPlayerListener());
        ratingManager = new RatingManager(requireActivity());

        // Normal startup
        new Thread(this::manageLoading, "RatingLoadingThread").start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        binding = FragmentRatingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // UI Elements setup
        headerText = binding.ratingFragmentHeaderText;
        playerTimeTracker = binding.ratingFragmentPlayerTrackTime;
        playerTimeTotal = binding.ratingFragmentPlayerTotalTime;
        buttonPlayPause = binding.ratingFragmentPlayerButtonPlayPause;
        buttonPrevious = binding.ratingFragmentPlayerButtonPrevious;
        buttonNext = binding.ratingFragmentPlayerButtonNext;
        playerProgressBar = binding.ratingFragmentPlayerProgressBar;
        checkMarkIcon = binding.ratingFragmentCheckMarkIcon;
        VASratingBar = binding.ratingFragmentRatingBar;

        // Prepare play/pause icons
        playIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_play_sized);
        pauseIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_pause_sized);

        // Assign listeners to all buttons
        buttonPlayPause.setOnClickListener(playListener);
        buttonPrevious.setOnClickListener(previousListener);
        buttonNext.setOnClickListener(nextListener);

        setPlayerButtonAnimation(buttonPlayPause);
        setPlayerButtonAnimation(buttonNext);
        setPlayerButtonAnimation(buttonPrevious);

        // Assign long press listeners to the previous and next buttons for going to start/end
        buttonPrevious.setOnLongClickListener(previousLongListener);
        buttonNext.setOnLongClickListener(nextLongListener);

        VASratingBar.setOnSeekBarChangeListener(VASratingBarListener);
        playerProgressBar.setOnSeekBarChangeListener(playerSeekBarListener);

        // If loading view was not available when loading actually finished, hide it now
        if (loadingFinishedFlag) hideLoading();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setPlayerButtonAnimation(Button button) {
        button.setOnTouchListener((v, event) -> {
            if (v.getId() == button.getId()) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
                    playerButtonClickAnimation.removeAllUpdateListeners();
                    playerButtonClickAnimation.addUpdateListener(animation ->
                            TextViewCompat.setCompoundDrawableTintList(button,
                                    ColorStateList.valueOf((Integer) animation.getAnimatedValue())));
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        playerButtonClickAnimation.reverse();
                    } else {
                        playerButtonClickAnimation.start();
                    }
                }
            }
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initDone) {
            /*
            Sets the active track in cases when user is returning to this fragment from elsewhere
            in the app the chain:
                onCreate > manageLoading > manageDirectory > checkDataDirectoryPath callback > changeCurrentTrack()
            would not trigger, therefore we need to do it here
            + it also works when user gets back from GroupControlFragment when creating a new
            session, so the changeCurrentTrack does not have to be in the callback
            */
            isSeekingFromLoadedValue = true;
            changeCurrentTrack(0, ratingManager.getTrackPointer());
        }
    }

    private void manageLoading() {
        String message = "";
        String title = "";
        Drawable icon = null;

        switch (ratingManager.loadSession()) {
            case OK:
                manageDirectory(ratingManager.getDataDirPath(), false);
                return;
            case NO_SESSION:
                title = getString(R.string.dialog_no_session_found_title);
                message = getString(R.string.dialog_no_session_found_message);
                icon = applyTintFilter(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_info),
                        requireContext().getColor(R.color.secondaryColor));
                break;
            case CORRUPTED_SESSION:
                title = getString(R.string.dialog_corrupted_session_title);
                message = getString(R.string.dialog_corrupted_session_message);
                icon = applyTintFilter(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_error),
                        requireContext().getColor(R.color.errorColor));
                break;
        }

        // This branch is finished here, hide the loading view
        hideLoading();

        String finalTitle = title;
        String finalMessage = message;
        Drawable finalIcon = icon;
        requireActivity().runOnUiThread(() -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(finalTitle)
                .setMessage(finalMessage)
                .setIcon(finalIcon)
                .setPositiveButton(R.string.dialog_no_session_corrupted_session_create_new_session, (dialog, which) -> {
                    // Go select directory and check it afterwards
                    fireSelectDirectory();
                })
                .setNegativeButton(R.string.dialog_no_session_corrupted_session_quit, (dialog, which) -> requireActivity().finish())
                .setCancelable(false).show());
    }

    private void manageDirectory(Uri selectedDir, boolean newSession) {
        Bundle checkResult = ratingManager.checkDataDirectoryPath(
                selectedDir, newSession, this, new RatingManager.GroupFoldersUserCheckCallback() {
                    @Override
                    public void groupCheckFinished(boolean confirmed) {
                        Log.d(TAG, "manageDirectory: Group check done callback triggered!");

                        if (confirmed) {
                            Log.d(TAG, "groupCheckFinished: Group check exited via confirmation -" +
                                    " Rating initiation complete!");
                            initDone = true;

                            // Sets the "in progress" state if the session is not finished
                            if (ratingManager.getState() == STATE_IDLE) {
                                ratingManager.setState(STATE_IN_PROGRESS);
                            }

                            requireActivity().runOnUiThread(() -> {
                                if (newSession) {
                                    // We don't have to call changeCurrentTrack here, it is already
                                    // called in onResume when user gets back from GroupControlFragment
                                    Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                            getString(R.string.snackbar_new_session_created),
                                            BaseTransientBottomBar.LENGTH_SHORT)
                                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
                                } else {
                                    // Must be called when an already established session is being loaded
                                    isSeekingFromLoadedValue = true;
                                }
                                changeCurrentTrack(0, ratingManager.getTrackPointer());
                            });

                            // Hide the loading overlay
                            hideLoading();
                        } else { // Group check cancelled - new session creation cancelled
                            Log.d(TAG, "groupCheckFinished: Group check exited via cancel");
                            fireSessionCreationCancelled();
                            manageLoading(); // Cycle back to the session check
                        }
                    }

                    @Override
                    public void groupCheckStart(ArrayList<GroupFolder> groupFolders) {
                        hideLoading();
                        NavDirections directions =
                                RatingFragmentDirections.actionRatingFragmentToGroupControlFragment(groupFolders);
                        NavHostFragment.findNavController(RatingFragment.this).navigate(directions);
                    }
                }
        );

        if (checkResult.getBoolean(DIRCHECK_RESULT_IS_OK)) {
            // Directory is ok and we can proceed to actual rating 😊
            Log.i(TAG, "manageDirectory: Complex directory check passed!");
        } else { // Directory check was NOT ok
            Spanned message = null;
            RatingManager.FileCheckError err = RatingManager.FileCheckError.valueOf(
                    checkResult.getString(DIRCHECK_RESULT_ERROR_TYPE));
            String dirname = checkResult.getString(DIRCHECK_RESULT_DIRECTORY, "???");
            switch (err) {
                case NOT_EXIST:
                    message = Html.fromHtml(getString(R.string.dialog_invalid_directory_not_exist, dirname), Html.FROM_HTML_MODE_LEGACY); break;
                case NOT_DIRECTORY:
                    message = Html.fromHtml(getString(R.string.dialog_invalid_directory_not_directory, dirname), Html.FROM_HTML_MODE_LEGACY); break;
                case NOT_READABLE:
                    message = Html.fromHtml(getString(R.string.dialog_invalid_directory_not_readable, dirname), Html.FROM_HTML_MODE_LEGACY); break;
                case NO_FILES:
                    message = Html.fromHtml(getString(R.string.dialog_invalid_directory_no_files, dirname), Html.FROM_HTML_MODE_LEGACY); break;
                case MISSING_FILES:
                    int cnt_missing = checkResult.getInt(DIRCHECK_RESULT_MISSING_CNT);
                    String list_missing = checkResult.getString(DIRCHECK_RESULT_MISSING_LIST);
                    message = Html.fromHtml(getString(R.string.dialog_invalid_directory_missing_files,
                            cnt_missing, dirname, list_missing), Html.FROM_HTML_MODE_LEGACY);
                    break;
            }
            hideLoading();
            fireInvalidDirectory(message);
        }
    }

    private void fireSelectDirectory() {
        // Fire up dialog to choose the data directory
        initDone = false;
        selectDirectory.launch(null);
    }

    private void fireInvalidDirectory(Spanned message) {
        requireActivity().runOnUiThread(() ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.dialog_invalid_directory_title))
                        .setMessage(message)
                        .setIcon(applyTintFilter(
                                ContextCompat.getDrawable(requireContext(), R.drawable.ic_error),
                                requireContext().getColor(R.color.errorColor)))
                        .setPositiveButton(R.string.dialog_invalid_directory_choose_valid_data_directory, (dialog, which) -> fireSelectDirectory())
                        .setNegativeButton(R.string.dialog_invalid_directory_cancel, (dialog, which) -> {
                            Log.d(TAG, "fireInvalidDirectory: Invalid directory dialog cancelled");
                            manageLoading();
                        })
                        .setCancelable(false).show());
    }

    private void fireSessionCreationCancelled() {
        requireActivity().runOnUiThread(() ->
                Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                        getString(R.string.snackbar_directory_selection_canceled),
                        BaseTransientBottomBar.LENGTH_SHORT)
                        .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show());
    }

    private void changeCurrentTrack(int current, int changeTo) {
        if (ratingManager.getTrackN() <= 0) {
            Log.w(TAG, "changeCurrentTrack: Error! Invalid number of tracks!");
        } else {
            // Get the recording that is to be made active
            Recording selectedRecording = ratingManager.getTrackList().get(changeTo);
            if (player.isPlaying()) { // Pause player on track change
                buttonPlayPause.callOnClick();
            }

            // Calls the player instance to try to set a new active track
            try {
                // Track progress timer and bar will be set when the track is prepared by the player
                if (!player.setCurrentTrack(requireContext(), selectedRecording.getUri())) {
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "changeCurrentTrack: Player could not be initialized with a track!", e);
                Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                        getString(R.string.snackbar_rating_player_track_load_failed),
                        BaseTransientBottomBar.LENGTH_LONG)
                        .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
                return;
            }

            // Manages visibility of player buttons according to the track being selected
            int indexLastTrack = ratingManager.getTrackN()-1;
            if (changeTo == 0) {
                buttonPrevious.setVisibility(View.INVISIBLE); // Hide previous button
            } else if (changeTo == indexLastTrack) {
                buttonNext.setVisibility(View.INVISIBLE); // Hide next button
            }
            if (current == 0 && changeTo > 0) {
                buttonPrevious.setVisibility(View.VISIBLE); // Show previous button
            } else if (current == indexLastTrack && changeTo < indexLastTrack) {
                buttonNext.setVisibility(View.VISIBLE); // Show next button
            }

            // Sets the header text - Recording n/N
            headerText.setText(getString(R.string.track_counter, getString(R.string.track), changeTo+1, indexLastTrack+1));

            // Sets rating bar position to default or saved
            int savedRating = selectedRecording.getRating();
            int setTo = savedRating != Recording.DEFAULT_UNSET_RATING ? savedRating : getResources().getInteger(R.integer.DEFAULT_PROGRESS_CENTER);
            VASratingBar.setProgress(setTo, true);

            // Sets the checkmark depending on the current state (in progress or finished)
            Log.d(TAG, "changeCurrentTrack: current state = " + ratingManager.getState());
            setCheckMarkDrawable(selectedRecording, ratingManager.getState());
        }
    }

    private void setCheckMarkDrawable(Recording recording, State state) {
        if (checkMarkIcon != null) {
            if (recording.getRating() == Recording.DEFAULT_UNSET_RATING) {
                checkMarkIcon.setVisibility(View.INVISIBLE);
            } else {
                if (state == STATE_FINISHED) {
                    checkMarkIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(),
                            R.drawable.ic_double_checkmark));
                } else {
                    checkMarkIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(),
                            R.drawable.ic_checkmark));
                }
                checkMarkIcon.setVisibility(View.VISIBLE);
            }
        } else {
            Log.w(TAG, "setCheckMarkDrawable: Not able to find checkmark view!");
        }
    }

    private Player.PlayerListener getPlayerListener() {
        return new Player.PlayerListener() {
            @Override
            public void onTrackPrepared(int duration) {
                // Track is ready, set maximum value for the player seek progress bar
                playerProgressBar.setMax(duration);
                playerTimeTotal.setText(formatDuration(duration));

                int savedProgress = ratingManager.getSavedPlayProgress();
                if (isSeekingFromLoadedValue && savedProgress != Player.SAVED_PROGRESS_DEFAULT) {
                    player.seekTo(savedProgress);
                    playerTimeTracker.setText(formatDuration(savedProgress));

                    isSeekingFromLoadedValue = false;
                } else {
                    player.seekTo(0);
                    ratingManager.setPlayProgress(0);
                    playerTimeTracker.setText(formatDuration(0));
                }

                // Failsafe so we dont accidentally end up with buttons invisible when they shouldnt
                if (buttonNext.getVisibility() == View.INVISIBLE) {
                    if (ratingManager.getTrackPointer() < ratingManager.getTrackN()-1) {
                        buttonNext.setVisibility(View.VISIBLE);
                    }
                }
                if (buttonPrevious.getVisibility() == View.INVISIBLE) {
                    if (ratingManager.getTrackPointer() > 0) {
                        buttonPrevious.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onTrackFinished() {
                // "Force" set the play/pause button to the paused state
                buttonPlayPause.setText(getString(R.string.button_play_label));
                buttonPlayPause.setCompoundDrawablesWithIntrinsicBounds(null, playIcon, null, null);
                buttonPlayPause.setOnClickListener(playListener);
                playPauseButtonState = ButtonState.PAUSED;
                player.rewind();
                ratingManager.setPlayProgress(0);
            }

            @Override
            public void onHeadphonesMissing() {
                Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                        getString(R.string.snackbar_connect_headphones),
                        BaseTransientBottomBar.LENGTH_LONG)
                        .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
            }

            @Override
            public void onVolumeDown() {
                Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                        getString(R.string.snackbar_increase_volume),
                        BaseTransientBottomBar.LENGTH_LONG)
                        .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
            }

            @Override
            public void onTimeTick(final int time) {
                requireActivity().runOnUiThread(() -> playerTimeTracker.setText(formatDuration(time)));
            }

            @Override
            public void onUpdateProgress(int current_ms) {
                if (!isTouchingSeekBar) {
                    playerProgressBar.setProgress(current_ms, true);
                    ratingManager.setPlayProgress(current_ms);
                }
            }

            @Override
            public void onError() {
                changeCurrentTrack(0, ratingManager.getTrackPointer());
            }
        };
    }

    private String formatDuration(int time_ms) {
        if (time_ms >= (60 * 60 * 1000)) {
            // If somehow longer than one hour
            Log.wtf(TAG, "formatDuration: I don't know how, but the audio is too damn long.");
            return "Error";
        } else {
            int min = (int) Math.floor((double) time_ms / 60000);
            int sec = (int) Math.floor(((double) time_ms % 60000) / 1000);

            return (min >= 10 ? String.valueOf(min) : "0" + min) +
                    ":" +
                    (sec >= 10 ? String.valueOf(sec) : "0" + sec);
        }
    }

    private void changeLoadingVisibility(boolean show) {
        requireContext().sendBroadcast(
                new Intent().setAction(show ? MainActivity.ACTION_SHOW_LOADING : MainActivity.ACTION_HIDE_LOADING));
        loadingFinishedFlag = !show;
    }
    private void hideLoading() { changeLoadingVisibility(false);}
    private void showLoading() { changeLoadingVisibility(true);}

    @Override
    public void onPause() {
        super.onPause();
        if (player != null) {
            player.dontTick();
            if (playPauseButtonState == ButtonState.PLAYING) {
                buttonPlayPause.callOnClick();
            }
        }

        // Saves the session on every Pause occasion (change app, phone lock, change screen, ...)
        if (initDone) {
            ratingManager.saveSession();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Saves the results to the .txt file in memory if the rating is in a finished state
        if (ratingManager.getState() == STATE_FINISHED) {
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
            new Thread(() -> {
                try {
                    ratingManager.saveResults(requireContext());
                    requireActivity().runOnUiThread(() ->
                            Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                    getString(R.string.snackbar_save_success),
                                    BaseTransientBottomBar.LENGTH_SHORT)
                                    .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    String errorMessage = (e.getMessage() == null ? getString(R.string.snackbar_save_failed_error_unknown) : e.getMessage());
                    requireActivity().runOnUiThread(() ->
                            Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                    Html.fromHtml(getString(R.string.snackbar_save_failed, errorMessage),Html.FROM_HTML_MODE_LEGACY),
                                    BaseTransientBottomBar.LENGTH_LONG)
                                    .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show()
                    );
                }
            }, "SaveResultsThread").start();
            return true;
        } else if (itemID == R.id.action_menu_show_saved_results && initDone) {
            showLoading();
            new Thread(() -> { // Threading for slow loading times
                try {
                    ArrayList<RatingResult> ratings = RatingManager.loadResults(requireContext());

                    hideLoading();
                    requireActivity().runOnUiThread(() -> {
                        NavDirections directions =
                                RatingFragmentDirections.actionRatingFragmentToResultFragment(ratings);
                        NavHostFragment.findNavController(this).navigate(directions);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    hideLoading();
                    requireActivity().runOnUiThread(() -> Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                            Html.fromHtml(getString(R.string.snackbar_ratings_loading_failed, e.getMessage()),Html.FROM_HTML_MODE_LEGACY),
                            BaseTransientBottomBar.LENGTH_LONG)
                            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show()
                    );
                }
            }, "ResultsLoadingThread").start();

            return true;
        } else if (itemID == R.id.action_menu_show_session_info && initDone) {
            showLoading();
            new Thread(() -> MainActivity.navigateToCurrentSessionInfo(
                    this, session -> {
                        hideLoading();
                        requireActivity().runOnUiThread(() -> {
                            NavDirections directions = RatingFragmentDirections
                                    .actionRatingFragmentToCurrentSessionInfoFragment(session);
                            NavHostFragment.findNavController(RatingFragment.this)
                                    .navigate(directions);
                        });
                    }
            ), "SessionLoadingThread").start();
            return true;
        } else if (itemID == R.id.action_menu_reset_ratings && initDone) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.dialog_make_new_session_title))
                    .setMessage(getString(R.string.dialog_make_new_session_message))
                    .setIcon(applyTintFilter(
                            ContextCompat.getDrawable(requireContext(), R.drawable.ic_warning),
                            requireContext().getColor(R.color.secondaryColor)))
                    .setPositiveButton(R.string.dialog_make_new_session_positive_button_label, (dialog, which) -> {
                        showLoading();
                        new Thread(() -> {
                            // If the rating is finished, try saving it
                            if (ratingManager.getState() == STATE_FINISHED) {
                                try {
                                    ratingManager.saveResults(requireContext());
                                    fireSelectDirectory();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    String errorMessage = (e.getMessage() == null ? getString(R.string.dialog_save_failed_continue_error_unknown) : e.getMessage());
                                    hideLoading();
                                    requireActivity().runOnUiThread(() ->
                                            new MaterialAlertDialogBuilder(requireContext())
                                                    .setTitle(getString(R.string.dialog_save_failed_continue_title))
                                                    .setMessage(Html.fromHtml(getString(R.string.dialog_save_failed_continue_message, errorMessage), Html.FROM_HTML_MODE_LEGACY))
                                                    .setIcon(applyTintFilter(
                                                            ContextCompat.getDrawable(requireContext(), R.drawable.ic_error),
                                                            requireContext().getColor(R.color.errorColor)))
                                                    .setPositiveButton(R.string.dialog_save_failed_continue_continue,
                                                            (dialog1, which1) -> fireSelectDirectory())
                                                    .setNegativeButton(R.string.dialog_save_failed_continue_return, null)
                                                    .setCancelable(false)
                                                    .show()
                                    );
                                }
                            } else { // Session is not complete, just proceed with selection
                                fireSelectDirectory();
                            }
                        }, "SaveResultsThread").start();
                    })
                    .setNegativeButton(R.string.dialog_quit_cancel, null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class SelectDirectory extends ActivityResultContracts.OpenDocumentTree {
        @NotNull
        @Override
        public Intent createIntent(@NonNull Context context, @Nullable Uri input) {
            Intent intent = super.createIntent(context, input);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            return intent;
        }
    }
}
