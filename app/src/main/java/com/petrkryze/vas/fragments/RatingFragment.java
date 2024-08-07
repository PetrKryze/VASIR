package com.petrkryze.vas.fragments;

import static com.petrkryze.vas.MainActivity.applyTintFilter;
import static com.petrkryze.vas.MainActivity.html;
import static com.petrkryze.vas.RatingModel.PLAYER_CURRENT_OUT_OF;
import static com.petrkryze.vas.RatingModel.PLAYER_LIST_ON_END;
import static com.petrkryze.vas.RatingModel.PLAYER_LIST_ON_START;
import static com.petrkryze.vas.RatingModel.PLAYER_PREPARED_SUCCESS;
import static com.petrkryze.vas.RatingModel.PLAYER_SAVED_PLAY_PROGRESS;
import static com.petrkryze.vas.RatingModel.PLAYER_TRACK_DURATION;
import static com.petrkryze.vas.RatingModel.PLAYER_TRACK_RATING;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.text.Spanned;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.petrkryze.vas.GroupFolder;
import com.petrkryze.vas.MainActivity;
import com.petrkryze.vas.R;
import com.petrkryze.vas.RatingManager.DirectoryCheckError;
import com.petrkryze.vas.RatingManager.LoadResult;
import com.petrkryze.vas.RatingModel;
import com.petrkryze.vas.RatingModel.SaveResultsCallback;
import com.petrkryze.vas.Recording;
import com.petrkryze.vas.Session;
import com.petrkryze.vas.databinding.FragmentRatingBinding;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * Created by Petr on 04.05.2021. Yay!
 */
@SuppressLint("ShowToast")
public class RatingFragment extends VASFragment {
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

    private RatingModel model;
    private boolean creatingNewSession = false;
    private static final String NewSessionFlagKey = "new_session_flag";

    private enum State {NULL, LOADING, PREPARING, PREPARED, ERROR, SELECTING_DIRECTORY, CHECKING_GROUPS}
    private State state = State.NULL;
    private static final String StateKey = "state";

    private void vibrate(int length) {
        vibrator.vibrate(VibrationEffect.createOneShot(length, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    // Play - Pause button Listeners
    private Drawable playIcon, pauseIcon;
    private final View.OnClickListener playListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: BUTTON PLAY CLICKED");
            if (v.getId() == buttonPlayPause.getId() && state == State.PREPARED) {
                vibrate(VIBRATE_BUTTON_MS);
                model.playerPlay();
            }
        }
    };
    private final View.OnClickListener pauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: BUTTON PAUSE CLICKED");
            if (v.getId() == buttonPlayPause.getId() && state == State.PREPARED) {
                vibrate(VIBRATE_BUTTON_MS);
                model.playerPause();
            }
        }
    };

    // Short player button Listeners
    private final View.OnClickListener previousListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: BUTTON PREVIOUS CLICKED");
            if (v.getId() == buttonPrevious.getId() && state == State.PREPARED) {
                vibrate(VIBRATE_BUTTON_MS);
                model.decrementTrack(requireContext());
            }
        }
    };
    private final View.OnClickListener nextListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "onClick: BUTTON NEXT CLICKED");
            if (v.getId() == buttonNext.getId() && state == State.PREPARED) {
                vibrate(VIBRATE_BUTTON_MS);
                model.incrementTrack(requireContext());
            }
        }
    };

    // Long player button Listeners
    private ValueAnimator playerButtonClickAnimation;
    private final View.OnLongClickListener previousLongListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            Log.d(TAG, "onLongClick: BUTTON PREVIOUS LONG CLICKED");
            if (v.getId() == buttonPrevious.getId() && state == State.PREPARED) {
                vibrate(VIBRATE_BUTTON_LONG_MS);
                model.changeTrackToFirst(requireContext());
                return true;
            }
            return false;
        }
    };
    private final View.OnLongClickListener nextLongListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            Log.d(TAG, "onLongClick: BUTTON NEXT LONG CLICKED");
            if (v.getId() == buttonNext.getId() && state == State.PREPARED) {
                vibrate(VIBRATE_BUTTON_LONG_MS);
                model.changeTrackToLast(requireContext());
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
            vibrate(VIBRATE_RATING_START_MS);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            VASratingBar.playSoundEffect(SoundEffectConstants.CLICK);

            if (state == State.PREPARED) { // Set the rating
                model.rate(seekBar.getProgress());

                if (model.isSessionFinished()) { // Checks if all recordings have been rated
                    Log.i(TAG, "onStopTrackingTouch: All recording have been rated!");
                    // Ratings will now save automatically on exit or new session
                }
                setCheckMarkDrawable(model.getRating());
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
            vibrate(VIBRATE_BUTTON_MS);
            isTouchingSeekBar = true;
            model.playerDontTick();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (state == State.PREPARED) {
                playerProgressBar.playSoundEffect(SoundEffectConstants.CLICK);
                model.playerSeekTo(seekBar.getProgress());
            }
            isTouchingSeekBar = false;
        }
    };

    ActivityResultLauncher<Uri> selectDirectory = registerForActivityResult(
            new SelectDirectory(),
            resultUri -> {
                if (resultUri != null) {
                    String fullPath = resultUri.getPath();
                    if (fullPath == null || fullPath.isEmpty()) { // Bad returned path somehow
                        state = State.ERROR;
                        creatingNewSession = false;
                        dialog = new MaterialAlertDialogBuilder(RatingFragment.this.requireContext())
                                .setTitle(RatingFragment.this.getString(R.string.dialog_internal_error_title))
                                .setMessage(RatingFragment.this.getString(R.string.dialog_internal_error_message))
                                .setIcon(applyTintFilter(
                                        ContextCompat.getDrawable(RatingFragment.this.requireContext(), R.drawable.ic_error),
                                        RatingFragment.this.requireContext().getColor(R.color.errorColor)))
                                .setPositiveButton(R.string.dialog_internal_error_return, (dialog, which) -> RatingFragment.this.fireSessionCreationCancelled())
                                .setCancelable(false).show();
                    } else { // Returned path seems ok, start the checks
                        state = State.PREPARING;
                        Log.d(TAG, "onActivityResult: Selected directory: " + fullPath);
                        RatingFragment.this.requireContext().getContentResolver()
                                .takePersistableUriPermission(resultUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        // Go check the selected directory and it's contents
                        loadingVisibility(true);
                        model.checkDirectoryFromUri(requireContext(), resultUri);
                    }
                } else {
                    Log.d(TAG, "onActivityResult: User left the directory selection activity without selecting.");
                    RatingFragment.this.fireSessionCreationCancelled();
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
        if (savedInstanceState != null) {
            State savedState = getSerializable(savedInstanceState, StateKey, State.class);
            if (savedState != null) state = savedState;

            creatingNewSession = savedInstanceState.getBoolean(NewSessionFlagKey);
        } else {
            state = State.NULL;
        }

        setGroupCheckCallback();
        isTouchingSeekBar = false;

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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = FragmentRatingBinding.inflate(inflater, container, false);
        Context context = requireContext();

        // UI Elements setup
        headerText = binding.ratingTitleLabel;
        playerTimeTracker = binding.ratingFragmentPlayerTrackTime;
        playerTimeTotal = binding.ratingFragmentPlayerTotalTime;
        buttonPlayPause = binding.ratingFragmentPlayerButtonPlayPause;
        buttonPrevious = binding.ratingFragmentPlayerButtonPrevious;
        buttonNext = binding.ratingFragmentPlayerButtonNext;
        playerProgressBar = binding.ratingFragmentPlayerProgressBar;
        checkMarkIcon = binding.ratingFragmentCheckMarkIcon;
        VASratingBar = binding.ratingFragmentRatingBar;
        TextView ratingLowestLabel = binding.ratingLowestLabel;
        TextView ratingHighestLabel = binding.ratingHighestLabel;

        // Set the rating labels from settings
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        headerText.setText(preferences.getString(context.getString(R.string.SETTING_KEY_RATING_TITLE_LABEL), context.getString(R.string.rating_title_label_default)));
        ratingLowestLabel.setText(preferences.getString(context.getString(R.string.SETTING_KEY_RATING_LOWEST_LABEL), context.getString(R.string.rating_lowest_label_default)));
        ratingHighestLabel.setText(preferences.getString(context.getString(R.string.SETTING_KEY_RATING_HIGHEST_LABEL), context.getString(R.string.rating_highest_label_default)));

        // Prepare play/pause icons
        playIcon = ContextCompat.getDrawable(context, R.drawable.ic_play_sized);
        pauseIcon = ContextCompat.getDrawable(context, R.drawable.ic_pause_sized);

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

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupMenu();

        model = new ViewModelProvider(this).get(RatingModel.class);

        model.getLoadingFinished().observe(getViewLifecycleOwner(), aBoolean ->
                loadingVisibility(!((Boolean) aBoolean)));

        model.getSession().observe(getViewLifecycleOwner(), this::onSessionLoaded);
        model.getDirectoryCheckError().observe(getViewLifecycleOwner(),
                directoryCheckError -> onDirectoryCheckError((DirectoryCheckError) directoryCheckError));
        model.getGroupCheckNeeded().observe(getViewLifecycleOwner(), uriArrayListPair -> {
            @SuppressWarnings("unchecked")
            Pair<Uri, ArrayList<GroupFolder>> pair = (Pair<Uri, ArrayList<GroupFolder>>) uriArrayListPair;
            onGroupCheckNeeded(pair.first, pair.second);
        });
        model.getSessionPrepared().observe(getViewLifecycleOwner(), aBoolean -> onSessionPrepared());

        model.getPlayerPrepared().observe(getViewLifecycleOwner(), result -> onPlayerPrepared((Bundle) result));
        model.getPlayerPlaying().observe(getViewLifecycleOwner(), playing -> onPlayerPlaying((Boolean) playing));
        model.getPlayerTrackFinished().observe(getViewLifecycleOwner(), aBoolean -> onPlayerTrackFinished());
        model.getPlayerHeadphonesMissing().observe(getViewLifecycleOwner(), aBoolean -> onPlayerHeadphonesMissing());
        model.getPlayerVolumeDown().observe(getViewLifecycleOwner(), aBoolean -> onPlayerVolumeDown());
        model.getPlayerTimeTick().observe(getViewLifecycleOwner(), time -> onPlayerTimeTick((Integer) time));
        model.getPlayerProgress().observe(getViewLifecycleOwner(), currentMs -> onPlayerProgressUpdate((Integer) currentMs));
        model.getPlayerError().observe(getViewLifecycleOwner(), o -> {
            @SuppressWarnings("unchecked")
            Pair<Integer, Integer> error = (Pair<Integer, Integer>) o;
            onPlayerError(error.first, error.second);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putSerializable(StateKey, state);
        outState.putBoolean(NewSessionFlagKey, creatingNewSession);
        super.onSaveInstanceState(outState);
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

    private void onSessionLoaded(Session session) {
        if (!creatingNewSession) {
            if (session != null) { // Session loaded
                state = State.PREPARING;
                model.checkDirectoryFromSession(requireContext(), session);
            } else { // Session loading failed
                state = State.ERROR;
                LoadResult loadResult = model.getSessionLoadResult();

                String message = "";
                String title = "";
                Drawable icon = null;

                switch (loadResult) {
                    case OK: return; // This option will not happen with session == null
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
                loadingVisibility(false);

                String finalTitle = title;
                String finalMessage = message;
                Drawable finalIcon = icon;
                dialog = new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(finalTitle)
                        .setMessage(finalMessage)
                        .setIcon(finalIcon)
                        .setPositiveButton(R.string.dialog_no_session_corrupted_session_create_new_session,
                                // Go select directory and check it afterwards
                                (dialog, which) -> fireSelectDirectory())
                        .setNegativeButton(R.string.dialog_no_session_corrupted_session_quit,
                                (dialog, which) -> requireActivity().finish())
                        .setCancelable(false).show();
            }
        }
    }


    private void onGroupCheckNeeded(Uri rootUri, ArrayList<GroupFolder> groupFoldersToCheck) {
        // This is the callback from when GroupCheckFragment is finished
        try { // Start the group control fragment
            state = State.CHECKING_GROUPS;
            URI outUri = new URI(rootUri.toString());
            NavDirections directions =
                    RatingFragmentDirections.actionRatingFragmentToGroupControlFragment(
                            groupFoldersToCheck, outUri);
            NavHostFragment.findNavController(RatingFragment.this).navigate(directions);
        } catch (URISyntaxException e) {
            Log.e(TAG,"Error during uri to URI transcription.",e);
        }
    }

    public static final String GROUP_CHECK_RESULT_REQUEST_KEY = "group_check_result";
    @SuppressWarnings("unchecked")
    private void setGroupCheckCallback() {
        getParentFragmentManager().setFragmentResultListener(
                GROUP_CHECK_RESULT_REQUEST_KEY, this,
                (requestKey, result) -> {
                    if (requestKey.equals(GROUP_CHECK_RESULT_REQUEST_KEY)) {
                        // The GroupCheck was confirmed, we can make a new session
                        if (result.getBoolean(GroupControlFragment.GroupControlConfirmedKey)) {
                            URI resultURI = getSerializable(result,GroupControlFragment.GroupControlSourceRootURI,URI.class);
                            Uri sourceRootUri = Uri.parse(resultURI.toString());
                            ArrayList<GroupFolder> validGroupFolders =
                                    getSerializable(result, GroupControlFragment.GroupFolderListSerializedKey, ArrayList.class);
                            Log.d(TAG, "groupCheckFinished: Group check exited via confirmation -" +
                                    " Rating initiation complete!");
                            // Go make new session from these groups
                            model.onGroupsConfirmed(sourceRootUri, validGroupFolders);
                        } else { // GroupControlFragment exit without confirm
                            onGroupCheckCancelled();
                        }
                    }
                }
        );
    }

    private void onGroupCheckCancelled() {
        Log.d(TAG, "onGroupCheckCancelled: Group check exited via cancel");
        creatingNewSession = false;
        Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                getString(R.string.snackbar_directory_check_canceled),
                BaseTransientBottomBar.LENGTH_SHORT)
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
        model.loadSession(); // Cycle back to the session check
    }

    private void onSessionPrepared() {
        state = State.PREPARED;
        if (creatingNewSession) {
            Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                    getString(R.string.snackbar_new_session_created),
                    BaseTransientBottomBar.LENGTH_SHORT)
                    .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
            creatingNewSession = false;
        } else {
            model.loadPlayerProgress();
        }

        model.changeTrackToPointer(requireContext());
    }

    private void onPlayerPrepared(Bundle result) {
        if (result.getBoolean(PLAYER_PREPARED_SUCCESS)) {
            // Manages visibility of player buttons according to the track being selected
            boolean hidePrevious = result.getBoolean(PLAYER_LIST_ON_START);
            buttonPrevious.setVisibility(hidePrevious ? View.INVISIBLE : View.VISIBLE);

            boolean hideNext = result.getBoolean(PLAYER_LIST_ON_END);
            buttonNext.setVisibility(hideNext ? View.INVISIBLE : View.VISIBLE);

            // Sets the header text - Recording n/N
            headerText.setText(getString(R.string.track_counter,
                    getString(R.string.track), result.getString(PLAYER_CURRENT_OUT_OF)));

            // Sets rating bar position to default or saved
            int savedRating = result.getInt(PLAYER_TRACK_RATING);
            int setTo = savedRating != Recording.DEFAULT_UNSET_RATING ? savedRating : getResources().getInteger(R.integer.DEFAULT_PROGRESS_CENTER);
            VASratingBar.setProgress(setTo, true);

            // Sets the checkmark depending on the current state (in progress or finished)
            setCheckMarkDrawable(savedRating);

            // Set maximum value for the player seek progress bar
            int duration = result.getInt(PLAYER_TRACK_DURATION);
            playerProgressBar.setMax(duration);
            playerTimeTotal.setText(formatDuration(duration));

            // Set the play progress to 0 or saved value if applicable
            int savedProgress = result.getInt(PLAYER_SAVED_PLAY_PROGRESS);
            playerTimeTracker.setText(formatDuration(savedProgress));
        } else {
            Log.e(TAG, "changeCurrentTrack: Player could not be initialized with a track!");
            Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                    getString(R.string.snackbar_rating_player_track_load_failed),
                    BaseTransientBottomBar.LENGTH_LONG)
                    .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
        }
    }

    private void onPlayerPlaying(Boolean playing) {
        if (playing) {
            buttonPlayPause.setText(getString(R.string.button_pause_label));
            buttonPlayPause.setCompoundDrawablesWithIntrinsicBounds(null, pauseIcon, null, null);
            buttonPlayPause.setOnClickListener(pauseListener);
        } else {
            buttonPlayPause.setText(getString(R.string.button_play_label));
            buttonPlayPause.setCompoundDrawablesWithIntrinsicBounds(null, playIcon, null, null);
            buttonPlayPause.setOnClickListener(playListener);
        }
    }

    private void onPlayerTrackFinished() {
        // "Force" set the play/pause button to the paused state
        buttonPlayPause.setText(getString(R.string.button_play_label));
        buttonPlayPause.setCompoundDrawablesWithIntrinsicBounds(null, playIcon, null, null);
        buttonPlayPause.setOnClickListener(playListener);
    }

    private void onPlayerHeadphonesMissing() {
        Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                getString(R.string.snackbar_connect_headphones),
                BaseTransientBottomBar.LENGTH_LONG)
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
    }

    private void onPlayerVolumeDown() {
        Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                getString(R.string.snackbar_increase_volume),
                BaseTransientBottomBar.LENGTH_LONG)
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
    }

    private void onPlayerTimeTick(int time) {
        playerTimeTracker.setText(formatDuration(time));
    }

    private void onPlayerProgressUpdate(int currentMs) {
        if (!isTouchingSeekBar) {
            playerProgressBar.setProgress(currentMs, true);
        }
    }

    private void onPlayerError(int what, int extra) {
        StringBuilder sb = new StringBuilder();

        switch (what) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN: break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                sb.append(getString(R.string.player_error_server_died)).append(": "); break;
            default: sb.append(getString(R.string.player_error_unknown)).append(": ");
        }

        switch (extra) {
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT: sb.append(getString(R.string.player_error_timeout)); break;
            case MediaPlayer.MEDIA_ERROR_IO: sb.append(getString(R.string.player_error_io)); break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED: sb.append(getString(R.string.player_error_unsupported)); break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED: sb.append(getString(R.string.player_error_malformed)); break;
            default: sb.append(getString(R.string.player_error_unknown));
        }

        String message;
        Runnable action;
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) { // We need to release and load player again
            message = getString(R.string.dialog_player_error_reset, sb.toString());
            action = () -> model.resetPlayer(requireContext());
        } else if (extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) {
            if (model.changeToValid(requireContext())) {
                message = getString(R.string.dialog_player_error, sb.toString());
                action = null;
            } else { // Total corner case, just one file and it is not supported
                message = getString(R.string.dialog_player_error_only_one_file_not_supported);
                action = this::fireSelectDirectory;
            }
        } else {
            message = getString(R.string.dialog_player_error_refresh, sb.toString());
            action = () -> model.changeTrackToPointer(requireContext());
        }

        dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_player_error_title))
                .setMessage(message)
                .setIcon(applyTintFilter(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_error),
                        requireContext().getColor(R.color.errorColor)))
                .setPositiveButton(getString(R.string.dialog_player_error_confirm), (dialog, which) -> {
                    if (action != null) action.run();
                }).setCancelable(false).show();
    }

    public void fireSelectDirectory() {
        // Fire up dialog to choose the data directory
        state = State.SELECTING_DIRECTORY;
        creatingNewSession = true;
        selectDirectory.launch(null);
    }

    private void onDirectoryCheckError(DirectoryCheckError errorInfo) {
        state = State.ERROR;
        creatingNewSession = false;
        Spanned message = null;
        String dirname = errorInfo.dirName;

        switch (errorInfo.errorType) {
            case NOT_EXIST:
                message = html(getString(R.string.dialog_invalid_directory_not_exist, dirname)); break;
            case NOT_DIRECTORY:
                message = html(getString(R.string.dialog_invalid_directory_not_directory, dirname)); break;
            case NOT_READABLE:
                message = html(getString(R.string.dialog_invalid_directory_not_readable, dirname)); break;
            case NO_FILES:
                message = html(getString(R.string.dialog_invalid_directory_no_files, dirname)); break;
            case MISSING_FILES:
                message = html(getString(R.string.dialog_invalid_directory_missing_files,
                        errorInfo.missingCnt, dirname, errorInfo.missingList));
                break;
        }

        Spanned finalMessage = message;
        dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_invalid_directory_title))
                .setMessage(finalMessage)
                .setIcon(applyTintFilter(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_error),
                        requireContext().getColor(R.color.errorColor)))
                .setPositiveButton(R.string.dialog_invalid_directory_choose_valid_data_directory,
                        (dialog, which) -> fireSelectDirectory())
                .setNegativeButton(R.string.dialog_invalid_directory_cancel, (dialog, which) -> {
                    Log.d(TAG, "fireInvalidDirectory: Invalid directory dialog cancelled");
                    model.loadSession();
                })
                .setCancelable(false).show();
    }

    private void fireSessionCreationCancelled() {
        creatingNewSession = false;
        Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                getString(R.string.snackbar_directory_selection_canceled),
                BaseTransientBottomBar.LENGTH_SHORT)
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
        model.loadSession(); // Cycle back to the session check
    }

    private void setCheckMarkDrawable(int rating) {
        if (checkMarkIcon != null) {
            if (rating == Recording.DEFAULT_UNSET_RATING) {
                checkMarkIcon.setVisibility(View.INVISIBLE);
            } else {
                checkMarkIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(),
                        (model.isSessionFinished() ?
                                R.drawable.ic_double_checkmark : R.drawable.ic_checkmark)));
                checkMarkIcon.setVisibility(View.VISIBLE);
            }
        } else {
            Log.w(TAG, "setCheckMarkDrawable: Not able to find checkmark view!");
        }
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

    @Override
    public void onStart() {
        super.onStart();

        if (state == State.PREPARED) {
            onSessionPrepared();
        } else {
            if (!creatingNewSession) {
                // Normal startup
                state = State.LOADING;
                model.loadSession();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        model.onPausePlayer();

        // Saves the session on every Pause occasion (change app, phone lock, change screen, ...)
        if (state == State.PREPARED) {
            model.saveSession();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (state == State.PREPARED && model != null && model.isSessionFinished()) { // Failsafe
            model.saveResults(requireContext(), null);
        }

        state = State.NULL;
        vibrator = null;
    }

    private void setupMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuProvider.super.onPrepareMenu(menu);

                int[] toEnable = {R.id.action_menu_help, R.id.action_menu_save,
                        R.id.action_menu_show_session_info, R.id.action_menu_new_session,
                        R.id.action_menu_show_saved_results, R.id.action_menu_quit, R.id.action_menu_settings};

                for (int item : toEnable) MainActivity.enableMenuItem(menu, item);
            }

            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {}

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int itemID = menuItem.getItemId();
                if (itemID == R.id.action_menu_help && state == State.PREPARED) {
                    onShowHelp();
                    return true;
                } else if (itemID == R.id.action_menu_save && state == State.PREPARED) {
                    model.saveResults(requireContext(), new SaveResultsCallback() {
                        @Override
                        public void onSuccess() {
                            Snackbar.make(requireActivity().findViewById(R.id.coordinator),
                                            getString(R.string.snackbar_save_success),
                                            BaseTransientBottomBar.LENGTH_SHORT)
                                    .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            onSaveFailed(errorMessage);
                        }
                    });
                    return true;
                } else if (itemID == R.id.action_menu_show_saved_results && state == State.PREPARED) {
                    onShowSavedResults(results -> {
                        NavDirections directions =
                                RatingFragmentDirections.actionRatingFragmentToResultFragment(results);
                        NavHostFragment.findNavController(RatingFragment.this).navigate(directions);
                    });
                    return true;
                } else if (itemID == R.id.action_menu_show_session_info && state == State.PREPARED) {
                    onShowSessionInfo(session -> {
                        NavDirections directions = RatingFragmentDirections
                                .actionRatingFragmentToCurrentSessionInfoFragment(session);
                        NavHostFragment.findNavController(RatingFragment.this).navigate(directions);
                    });
                    return true;
                } else if (itemID == R.id.action_menu_new_session && state == State.PREPARED) {
                    onNewSession();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void onShowHelp() {
        String contextHelpTitle = getString(R.string.help_context_title_rating_fragment);
        String[] contextHelpDescriptions = getResources().getStringArray(R.array.help_tag_description_rating_fragment);
        String contextHelpBody = getString(R.string.help_context_body_rating_fragment);

        NavDirections directions = RatingFragmentDirections.
                actionRatingFragmentToHelpFragment(contextHelpTitle, contextHelpDescriptions,
                        contextHelpBody, R.drawable.help_screen_rating_fragment);
        NavHostFragment.findNavController(this).navigate(directions);
    }

    private State statePrior;
    private void onNewSession() {
        statePrior = state;
        dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_make_new_session_title))
                .setMessage(getString(R.string.dialog_make_new_session_message))
                .setIcon(applyTintFilter(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_warning),
                        requireContext().getColor(R.color.secondaryColor)))
                .setPositiveButton(R.string.dialog_make_new_session_positive_button_label,
                        (dialog, which) -> {
                            loadingVisibility(true);
                            // If the rating is finished, try saving it
                            if (model.isSessionFinished()) {
                                model.saveResults(requireContext(), new SaveResultsCallback() {
                                    @Override
                                    public void onSuccess() {
                                        fireSelectDirectory();
                                    }

                                    @Override
                                    public void onError(String errorMessage) {
                                        onSaveFailedOnNewSession(errorMessage);
                                    }
                                });
                            } else { // Session is not complete, just proceed with selection
                                fireSelectDirectory();
                            }
                        })
                .setNegativeButton(R.string.dialog_quit_cancel, null).show();
    }

    private void onSaveFailed(String errorMessage) {
        String errorString = (errorMessage == null ?
                getString(R.string.snackbar_save_failed_error_unknown) : errorMessage);
        Spanned message = html(getString(R.string.snackbar_save_failed, errorString));
        Snackbar.make(requireActivity().findViewById(R.id.coordinator), message,
                BaseTransientBottomBar.LENGTH_LONG)
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE).show();
    }

    private void onSaveFailedOnNewSession(String errorMessage) {
        state = State.ERROR;
        String errorString = (errorMessage == null ?
                getString(R.string.dialog_save_failed_continue_error_unknown) : errorMessage);
        Spanned message = html(getString(R.string.dialog_save_failed_continue_message, errorString));
        loadingVisibility(false);
        dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_save_failed_continue_title))
                .setMessage(message)
                .setIcon(applyTintFilter(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_error),
                        requireContext().getColor(R.color.errorColor)))
                .setPositiveButton(R.string.dialog_save_failed_continue_continue,
                        (dialog1, which1) -> fireSelectDirectory())
                .setNegativeButton(R.string.dialog_save_failed_continue_return, (dialog1, which) -> state = statePrior)
                .setCancelable(false).show();
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
