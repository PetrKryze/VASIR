package com.petrkryze.vas;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import com.petrkryze.vas.RatingManager.DirectoryCheckCallback;
import com.petrkryze.vas.RatingManager.DirectoryCheckError;
import com.petrkryze.vas.RatingManager.LoadResult;
import com.petrkryze.vas.livedata.EventLiveData;
import com.petrkryze.vas.livedata.MutableEventLiveData;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

/**
 * Created by Petr on 06.08.2021. Yay!
 */
public class RatingModel extends AndroidViewModel {
    private static final String TAG = "RatingModel";

    private Player player;
    private final RatingManager ratingManager;

    private final MutableEventLiveData<Boolean> loadingFinished = new MutableEventLiveData<>();

    private final MutableEventLiveData<Session> currentSession = new MutableEventLiveData<>();
    private LoadResult sessionLoadResult;

    private final MutableEventLiveData<Boolean> sessionPrepared =
            new MutableEventLiveData<>();
    private final MutableEventLiveData<DirectoryCheckError> directoryCheckError =
            new MutableEventLiveData<>();
    private final MutableEventLiveData<Pair<Uri, ArrayList<GroupFolder>>> groupCheckNeeded =
            new MutableEventLiveData<>();

    // Player callbacks
    private final MutableEventLiveData<Bundle> playerPrepared = new MutableEventLiveData<>();
    private final MutableEventLiveData<Boolean> playerPlaying = new MutableEventLiveData<>();
    private final MutableEventLiveData<Boolean> playerTrackFinished = new MutableEventLiveData<>();
    private final MutableEventLiveData<Boolean> playerHeadphonesMissing = new MutableEventLiveData<>();
    private final MutableEventLiveData<Boolean> playerVolumeDown = new MutableEventLiveData<>();
    private final MutableEventLiveData<Integer> playerTimeTick = new MutableEventLiveData<>();
    private final MutableEventLiveData<Integer> playerProgress = new MutableEventLiveData<>();
    private final MutableEventLiveData<Pair<Integer, Integer>> playerError = new MutableEventLiveData<>();

    public RatingModel(@NonNull Application application) {
        super(application);

        // Initialize the audio player and rating manager
        player = new Player(application, getPlayerListener());
        ratingManager = new RatingManager(application);
    }

    private @NonNull Session session() {
        if (currentSession.getValue() != null) {
            return currentSession.getValue();
        } else {
            throw new NullPointerException("Session is null.");
        }
    }

    public EventLiveData<Boolean> getLoadingFinished() {
        return loadingFinished;
    }

    public LiveData<Session> getSession() {
        return currentSession;
    }

    public LoadResult getSessionLoadResult() {
        return sessionLoadResult;
    }

    public EventLiveData<Boolean> getSessionPrepared() {
        return sessionPrepared;
    }

    public EventLiveData<DirectoryCheckError> getDirectoryCheckError() {
        return directoryCheckError;
    }
    
    public EventLiveData<Pair<Uri, ArrayList<GroupFolder>>> getGroupCheckNeeded() {
        return groupCheckNeeded;
    }

    public EventLiveData<Bundle> getPlayerPrepared() {
        return playerPrepared;
    }

    public EventLiveData<Boolean> getPlayerPlaying() {
        return playerPlaying;
    }

    public EventLiveData<Boolean> getPlayerTrackFinished() {
        return playerTrackFinished;
    }

    public EventLiveData<Boolean> getPlayerHeadphonesMissing() {
        return playerHeadphonesMissing;
    }

    public EventLiveData<Boolean> getPlayerVolumeDown() {
        return playerVolumeDown;
    }

    public EventLiveData<Integer> getPlayerTimeTick() {
        return playerTimeTick;
    }

    public EventLiveData<Integer> getPlayerProgress() {
        return playerProgress;
    }

    public EventLiveData<Pair<Integer, Integer>> getPlayerError() {
        return playerError;
    }

    public void loadSession() {
        loadingFinished.postValue(false);
        Session session = currentSession.getValue();

        if (session == null) {
            new Thread(() -> {
                Pair<LoadResult, Session> loadResult = ratingManager.loadSession();
                this.sessionLoadResult = loadResult.first;
                currentSession.postValue(loadResult.second);
            }, "SessionLoadingThread").start();
        } else {
            currentSession.postValue(session);
        }
    }

    final DirectoryCheckCallback directoryCheckCallback = new DirectoryCheckCallback() {
        @Override
        public void onError(DirectoryCheckError errorInfo) {
            directoryCheckError.postValue(errorInfo);
        }

        @Override
        public void onSuccess() {
            sessionPrepared.postValue(true);
            loadingFinished.postValue(true);
        }

        @Override
        public void onGroupCheckNeeded(Uri rootDirUri, ArrayList<GroupFolder> groupFolders) {
            groupCheckNeeded.postValue(new Pair<>(rootDirUri, groupFolders));
        }
    };

    // Directory loading for saved, loaded session
    public void checkDirectoryFromSession(Context context, Session loadedSession) {
        loadingFinished.postValue(false);
        new Thread(() -> ratingManager.checkSavedDataDirectoryPath(context, loadedSession,
                directoryCheckCallback), "DirectoryLoadingThread").start();
    }

    // Directory loading for a session to be created from Uri, if successfully checked
    public void checkDirectoryFromUri(Context context, Uri selectedDir) {
        loadingFinished.postValue(false);
        new Thread(() -> ratingManager.checkNewDataDirectoryPath(context, selectedDir,
                directoryCheckCallback), "DirectoryLoadingThread").start();
    }

    public void onGroupsConfirmed(Uri newRootUri, ArrayList<GroupFolder> selectedGroupFolders) {
        try {
            Session newSession = ratingManager.makeNewSession(newRootUri, selectedGroupFolders);
            currentSession.postValue(newSession);

            sessionPrepared.postValue(true);
            loadingFinished.postValue(true);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void saveSession() {
        new Thread(() -> ratingManager.saveSession(session()), "SessionSavingThread").start();
    }

    public interface SaveResultsCallback {
        void onSuccess();
        void onError(String errorMessage);
    }
    public void saveResults(Context context, @Nullable SaveResultsCallback callback) {
        new Thread(() -> {
            try {
                ratingManager.saveResults(context, session());
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        }, "ResultsSavingThread").start();
    }

    public boolean isSessionFinished() {
        return session().isRatingFinished();
    }

    public void incrementTrack(Context context) {
        int trackPointer = session().getTrackPointer();
        if (trackPointer < session().getTrackN() - 1) {
            changeCurrentTrack(context, trackPointer + 1);
            session().trackIncrement();
        }
    }

    public void decrementTrack(Context context) {
        int trackPointer = session().getTrackPointer();
        if (trackPointer > 0) {
            changeCurrentTrack(context, trackPointer - 1);
            session().trackDecrement();
        }
    }

    public void changeTrackToPointer(Context context) {
        changeCurrentTrack(context, session().getTrackPointer());
    }

    public void changeTrackToFirst(Context context) {
        if (session().getTrackPointer() > 0) {
            changeCurrentTrack(context, 0);
            session().trackToFirst();
        }
    }

    public void changeTrackToLast(Context context) {
        int trackN = session().getTrackN();
        if (session().getTrackPointer() < trackN - 1) {
            changeCurrentTrack(context, trackN - 1);
            session().trackToLast();
        }
    }

    public static final String PLAYER_PREPARED_SUCCESS = "player_prepared_success";
    public static final String PLAYER_LIST_ON_START = "player_list_on_start";
    public static final String PLAYER_LIST_ON_END = "player_list_on_end";
    public static final String PLAYER_CURRENT_OUT_OF = "player_current_out_of";
    public static final String PLAYER_TRACK_RATING = "player_track_rating";
    public static final String PLAYER_TRACK_DURATION = "player_track_duration";
    public static final String PLAYER_SAVED_PLAY_PROGRESS = "player_saved_play_progress";
    private Bundle playerPreparedBundle = null;
    private void changeCurrentTrack(Context context, int changeTo) {
        if (session().getTrackN() <= 0) {
            Log.w(TAG, "changeCurrentTrack: Error! Invalid number of tracks!");
        } else {
            // Get the recording that is to be made active
            Recording selectedRecording = session().getRecording(changeTo);
            if (player.isPlaying()) { // Pause player on track change
                playerPause();
            }

            // Calls the player instance to try to set a new active track
            try {
                // Track progress timer and bar will be set when the track is prepared by the player
                if (!player.setCurrentTrack(context, selectedRecording.getUri())) {
                    return;
                }

                playerPreparedBundle = new Bundle();
                playerPreparedBundle.putBoolean(PLAYER_PREPARED_SUCCESS, true);

                int indexLastTrack = session().getTrackN()-1;
                if (changeTo == 0) {
                    playerPreparedBundle.putBoolean(PLAYER_LIST_ON_START, true);
                }

                if (changeTo == indexLastTrack) {
                    playerPreparedBundle.putBoolean(PLAYER_LIST_ON_END, true);
                }

                playerPreparedBundle.putString(PLAYER_CURRENT_OUT_OF, ((changeTo+1) + "/" + (indexLastTrack+1)));
                playerPreparedBundle.putInt(PLAYER_TRACK_RATING, selectedRecording.getRating());
                // PlayerPreparedBundle gets sent in the onTrackPrepared method in the listener
            } catch (IOException e) {
                e.printStackTrace();
                Bundle out = new Bundle();
                out.putBoolean(PLAYER_PREPARED_SUCCESS, false);
                playerPrepared.postValue(out);
            }
        }
    }

    /** This method rates the current active track in the player.
     * @param rating Rating integer from 0 to 100.
     */
    public void rate(int rating) {
        session().rate(rating);
    }

    public int getRating() {
        return session().getRating();
    }

    public void onPausePlayer() {
        if (player.isPlaying()) {
            player.dontTick();
            playerPause();
        }
    }

    public void playerPlay() {
        playerPlaying.setValue(player.start());
    }

    public void playerPause() {
        playerPlaying.setValue(!player.pause());
    }

    public void playerDontTick() {
        player.dontTick();
    }

    public void playerSeekTo(int seekTo) {
        player.seekTo(seekTo);
    }

    private void setPlayProgress(int progress) {
        session().setTrackPlayProgress(progress);
    }

    private boolean loadPlayerProgress = false;
    public void loadPlayerProgress() {
        loadPlayerProgress = true;
    }

    private Player.PlayerListener getPlayerListener() {
        return new Player.PlayerListener() {
            @Override
            public void onTrackPrepared(int duration) {
                if (playerPreparedBundle != null) {
                    playerPreparedBundle.putInt(PLAYER_TRACK_DURATION, duration);

                    int savedProgress = session().getTrackPlayProgress();
                    if (savedProgress != Player.SAVED_PROGRESS_DEFAULT && loadPlayerProgress) {
                        player.seekTo(savedProgress);
                        playerPreparedBundle.putInt(PLAYER_SAVED_PLAY_PROGRESS, savedProgress);
                        loadPlayerProgress = false;
                    } else {
                        player.seekTo(0);
                        playerPreparedBundle.putInt(PLAYER_SAVED_PLAY_PROGRESS, 0);
                    }
                    playerPrepared.postValue(playerPreparedBundle);
                }
            }

            @Override
            public void onTrackFinished() {
                player.rewind();
                playerTrackFinished.postValue(true);
            }

            @Override
            public void onHeadphonesMissing() {
                playerHeadphonesMissing.postValue(true);
            }

            @Override
            public void onVolumeDown() {
                playerVolumeDown.postValue(true);
            }

            @Override
            public void onTimeTick(final int time) {
                playerTimeTick.postValue(time);
            }

            @Override
            public void onUpdateProgress(int currentMs) {
                playerProgress.postValue(currentMs);
                setPlayProgress(currentMs);
            }

            @Override
            public void onError(int what, int extra) {
                playerError.postValue(Pair.create(what, extra));
            }
        };
    }

    public void resetPlayer(Context context) {
        if (player != null) player.clean(); player = null;

        player = new Player(context, getPlayerListener());
        changeTrackToFirst(context);
    }

    public boolean changeToValid(Context context) {
        int pointer = session().getTrackPointer();
        int trackN = session().getTrackN();

        if (trackN == 1) { // Fuck, only one track, nowhere to go
            return false;
        } else { // trackN > 1, More than one track
            if (pointer == 0) { // First in list, must switch to second
                changeCurrentTrack(context, 1);
                session().trackToFirst();
                session().trackIncrement();
            } else { // Anywhere else, switch to previous
                decrementTrack(context);
            }
            return true;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (player != null) {
            player.clean();
            player = null;
        }
    }
}
