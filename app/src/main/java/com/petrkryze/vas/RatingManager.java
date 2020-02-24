package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import androidx.core.content.ContextCompat;

import static com.petrkryze.vas.Recording.DEFAULT_UNSET_RATING;
import static com.petrkryze.vas.Recording.recordingComparator;


/**
 * Created by Petr on 09.02.2020. Yay!
 */
class RatingManager {

    public enum State {STATE_IN_PROGRESS, STATE_FINISHED, STATE_IDLE}

    private int session_ID;
    private State state;
    private long seed;
    private String generatorMessage;
    private ArrayList<Integer> lastSessionRatings;

    private final SharedPreferences preferences;

    private final int DEFAULT_VALUE_NO_KEY;
    private final String KEY_PREFERENCES_SESSION_ID;
    private final String KEY_PREFERENCES_SEED;
    private final String KEY_PREFERENCES_GENERATOR_MESSAGE;
    private final String KEY_PREFERENCES_RATINGS;
    private final String KEY_PREFERENCES_STATE;

    private static final char separator = ';';
    private static final char headerTag = '#';
    private static final String RATINGS_FOLDER_NAME = "Ratings";

    private final String TAG = "RatingManager";

    RatingManager(Activity context) {
        Resources resources = context.getResources();
        DEFAULT_VALUE_NO_KEY = resources.getInteger(R.integer.DEFAULT_VALUE_NO_KEY);
        KEY_PREFERENCES_SESSION_ID = resources.getString(R.string.KEY_PREFERENCES_SESSION_ID);
        KEY_PREFERENCES_SEED = resources.getString(R.string.KEY_PREFERENCES_SEED);
        KEY_PREFERENCES_GENERATOR_MESSAGE = resources.getString(R.string.KEY_PREFERENCES_GENERATOR_MESSAGE);
        KEY_PREFERENCES_RATINGS = resources.getString(R.string.KEY_PREFERENCES_RATINGS);
        KEY_PREFERENCES_STATE = resources.getString(R.string.KEY_PREFERENCES_STATE);

        this.preferences = context.getPreferences(Context.MODE_PRIVATE);

        // TODO Delet dis after production version is done
//        SharedPreferences.Editor editor = preferences.edit();
//        editor.clear();
//        editor.commit();

        // If there is no session to load, make a fresh new one
        if (!loadSession()) {
            makeNewSession();
        }
    }

    void makeNewSession() {
        this.session_ID = getNewSessionID(); // Increments last existing session number
        this.seed = System.nanoTime();
        Log.i(TAG, "RatingManager: Newly created seed: " + seed);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        this.generatorMessage = format.format(new Date(System.currentTimeMillis()));
        this.lastSessionRatings = new ArrayList<>();
        this.state = State.STATE_IDLE;
    }

    int getLastSessionRating(int index) {
        if (lastSessionRatings.size() == 0) { // New session without saved previous ratings
            return DEFAULT_UNSET_RATING;
        } else {
            return lastSessionRatings.get(index);
        }
    }

    State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    private void setState(String state) {
        if (State.STATE_FINISHED.toString().equals(state)) {
            this.state = State.STATE_FINISHED;
        } else if (State.STATE_IDLE.toString().equals(state)) {
            this.state = State.STATE_IDLE;
        } else if (State.STATE_IN_PROGRESS.toString().equals(state)) {
            this.state = State.STATE_IN_PROGRESS;
        } else {
            this.state = State.STATE_IDLE;
        }
    }

    int getSession_ID() {
        return session_ID;
    }

    long getSeed() {
        return seed;
    }

    String getGeneratorMessage() {
        return generatorMessage;
    }

    private int getNewSessionID() {
        int lastSessionID = preferences.getInt(KEY_PREFERENCES_SESSION_ID, DEFAULT_VALUE_NO_KEY);
        if (lastSessionID == DEFAULT_VALUE_NO_KEY) {
            Log.i(TAG, "getNewSessionID: No key for session ID found in preferences.");
            return 1;
        } else {
            Log.i(TAG, "getNewSessionID: NEW SESSION ID = " + (lastSessionID + 1));
            return lastSessionID + 1;
        }
    }

    boolean isRatingFinished(List<Recording> recordings) {
        for (Recording rec : recordings) {
            if (rec.getRating() == DEFAULT_UNSET_RATING) {
                return false;
            }
        }
        return true;
    }

    String getRatingFinishedRatio(List<Recording> recordings) {
        int cnt = 0;
        int size = recordings.size();

        for (Recording rec : recordings) {
            if (rec.getRating() != DEFAULT_UNSET_RATING) {
                cnt++;
            }
        }
        return cnt + "/" + size;
    }

    void saveSession(List<Recording> recordings) {
        if (recordings.size() <= 0) {
            Log.e(TAG, "saveSession: Invalid recordings list size!");
        } else {
            ArrayList<Integer> ratings = new ArrayList<>(recordings.size());
            for (Recording rec : recordings) {
                ratings.add(rec.getRating());
            }
            Gson gson = new Gson();

            Log.i(TAG, "saveSession: Saving current session data: " +
                    "Session ID = " + session_ID + "\n" +
                    "Seed = " + seed + "\n" +
                    "Generator Message = " + generatorMessage + "\n" +
                    "State = " + state + "\n" +
                    "Ratings = " + ratings.toString());

            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(KEY_PREFERENCES_SESSION_ID, session_ID);
            editor.putLong(KEY_PREFERENCES_SEED, seed);
            editor.putString(KEY_PREFERENCES_GENERATOR_MESSAGE, generatorMessage);
            editor.putString(KEY_PREFERENCES_RATINGS ,gson.toJson(ratings));
            editor.putString(KEY_PREFERENCES_STATE, state.toString());
            editor.apply();
        }
    }

    private boolean loadSession() {
        // Load last saved session - returns false when no previous session is in memory
        // Load Session ID
        int lastSessionID = preferences.getInt(KEY_PREFERENCES_SESSION_ID, DEFAULT_VALUE_NO_KEY);
        if (lastSessionID == DEFAULT_VALUE_NO_KEY) {
            Log.i(TAG, "loadSession: No key for session ID found in preferences.");
            return false;
        } else {
            this.session_ID = lastSessionID;
            Log.i(TAG, "loadSession: Retrieved session ID: " + lastSessionID);
        }

        // Load last randomizer seed
        long lastSeed = preferences.getLong(KEY_PREFERENCES_SEED, DEFAULT_VALUE_NO_KEY);
        if (lastSeed == DEFAULT_VALUE_NO_KEY) {
            throw new IllegalStateException("Error! Session saved without seed!");
        } else {
            this.seed = lastSeed;
            Log.i(TAG, "RatingManager: Retrieved seed: " + seed);
        }

        // Load last generator message
        String lastGenMessage = preferences.getString(KEY_PREFERENCES_GENERATOR_MESSAGE, "");
        if (lastGenMessage.equals("")) {
            throw new IllegalStateException("Error! Session saved without generator message!");
        } else {
            this.generatorMessage = lastGenMessage;
            Log.i(TAG, "RatingManager: Retrieved generator message: " + generatorMessage);
        }

        // Load array list of last ratings
        Gson gson = new Gson();
        String json = preferences.getString(KEY_PREFERENCES_RATINGS, "");
        if (json.equals("")) {
            throw new IllegalStateException("Error! Session saved without ratings!");
        } else {
            ArrayList<Integer> lastRatings = gson.fromJson(json,
                    new TypeToken<ArrayList<Integer>>(){}.getType());
            this.lastSessionRatings = lastRatings;
            Log.i(TAG, "RatingManager: Retrieved ratings: " + lastRatings.toString());
        }

        // Load last rating state
        String lastState = preferences.getString(KEY_PREFERENCES_STATE, "");
        if (lastState.equals("")) {
            throw new IllegalStateException("Error! Session saved without state!");
        } else {
            setState(lastState);
            Log.i(TAG, "RatingManager: Retrieved state: " + state);
        }

        return true;
    }

    void saveResults(Context context, List<Recording> recordings) throws Exception {
        // Fire this only and only when saving the final results
        if (state != State.STATE_FINISHED) {
            Log.e(TAG, "saveResults: Rating manager is not in a finished state!");
        } else {
            File sdcardAppFolder = getRootAppDirOnSdCard(context);
            if (sdcardAppFolder == null) {
                throw new Exception("No usable external storage location found!");
            }

            File directory = new File(sdcardAppFolder, RATINGS_FOLDER_NAME);
            if (!directory.exists()) {
                Log.i(TAG, "saveResults: Directory <" + directory.getPath() +
                        "> does not exist.");
                if (!directory.mkdirs()) {
                    Log.i(TAG, "saveResults: Directory " + directory.getName() +
                            " could not be created.");
                } else {
                    Log.i(TAG, "saveResults: Directory <" + directory.getPath()
                            + "> created successfully.");
                }
            }

            // Sort the randomized recordings
            Collections.sort(recordings, recordingComparator);

            Log.i(TAG, "saveResults: Sorted recordings:");
            for (Recording r : recordings) {
                Log.i(TAG, "saveResults: " + r.toString());
            }

            @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                    new SimpleDateFormat("dd-MM-yyyy");
            String date = format.format(new Date(System.currentTimeMillis()));
            String newRatingsFileName = context.getString(R.string.RATING_FILE_NAME,
                    session_ID, date);

            File file = new File(directory, newRatingsFileName);
            FileWriter writer = new FileWriter(file);

            String header = headerTag + "Session ID: " + session_ID + "\n" +
                    headerTag + "Randomizer Seed: " + seed + "\n" +
                    headerTag + "Generator Date: " + generatorMessage + "\n";
            writer.append(header);

            // Row format: id;group;random_number;rating
            for (Recording recording : recordings) {
                writer.append(recording.getID()).append(separator)
                        .append(recording.getGroupType().toString()).append(separator)
                        .append(String.valueOf(recording.getRandomized_number())).append(separator)
                        .append(String.valueOf(recording.getRating())).append("\n");
            }
            writer.flush();
            writer.close();

            MediaScannerConnection.scanFile(context, new String[]{file.getPath()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i(TAG, "onScanCompleted: SCAN OF FILE <" + path + "> completed!");
                        }
                    });

            Log.i(TAG, "saveResults: File: <" + file.getName() + "> written successfully.");
        }
    }

    static File getRootAppDirOnSdCard(Context context) {
        final String TAG = "getRootAppDirOnSdCard";
        // Get all available storage (internal, external, mounted, emulated, ...)
        File[] externalStorageVolumes =
                ContextCompat.getExternalFilesDirs(context, null);

        File sdcardAppFolder = null;
        for (int i = 0; i <= externalStorageVolumes.length; i++) {
            File f = externalStorageVolumes[i];
            Log.i(TAG, "saveResults: Storage no." + i);
            Log.i(TAG, "saveResults: External Storage path: " + f.getPath());
            Log.i(TAG, "saveResults: Is this storage emulated? " +
                    Environment.isExternalStorageEmulated(f));
            Log.i(TAG, "saveResults: Is this storage removable? " +
                    Environment.isExternalStorageRemovable(f));
            Log.i(TAG, "saveResults: State of this external storage: " +
                    Environment.getExternalStorageState(f) + "\n");

            if (Environment.isExternalStorageRemovable(f) &&
                    !Environment.isExternalStorageEmulated(f) &&
                    Environment.getExternalStorageState(f).equals(Environment.MEDIA_MOUNTED)) {
                // This storage is <<probably>> a external SD-card
                sdcardAppFolder = f;
                // Fuck everything else
                break;
            }
        }
        return sdcardAppFolder;
    }
}
