package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.core.content.ContextCompat;


/**
 * Created by Petr on 09.02.2020. Yay!
 */
public class RatingManager {

    public enum State {STATE_IN_PROGRESS, STATE_FINISHED, STATE_IDLE}

    private State state;
    private long seed;
    private String generatorMessage;
    private ArrayList<Integer> lastSessionRatings;

    private int session_ID;
    private SharedPreferences preferences;

    private int DEFAULT_VALUE_NO_KEY;
    private String KEY_PREFERENCES_SESSION_ID;
    private String KEY_PREFERENCES_SEED;
    private String KEY_PREFERENCES_GENERATOR_MESSAGE;
    private String KEY_PREFERENCES_RATINGS;

    private final String TAG = "RatingManager";

    public RatingManager(Activity context) {
        Resources resources = context.getResources();
        DEFAULT_VALUE_NO_KEY = resources.getInteger(R.integer.DEFAULT_VALUE_NO_KEY);
        KEY_PREFERENCES_SESSION_ID = resources.getString(R.string.KEY_PREFERENCES_SESSION_ID);
        KEY_PREFERENCES_SEED = resources.getString(R.string.KEY_PREFERENCES_SEED);
        KEY_PREFERENCES_GENERATOR_MESSAGE = resources.getString(R.string.KEY_PREFERENCES_GENERATOR_MESSAGE);
        KEY_PREFERENCES_RATINGS = resources.getString(R.string.KEY_PREFERENCES_RATINGS);

        this.preferences = context.getPreferences(Context.MODE_PRIVATE);

//        SharedPreferences.Editor editor = preferences.edit();
//        editor.clear();
//        editor.commit();


        int lastSessionID = preferences.getInt(KEY_PREFERENCES_SESSION_ID, DEFAULT_VALUE_NO_KEY);
        if (lastSessionID == DEFAULT_VALUE_NO_KEY) {
            Log.i(TAG, "getNewSessionID: No key for session ID found in preferences.");
            // Make new-first session
            makeNewSession();
        } else {
            // Load last saved session
            Log.i(TAG, "RatingManager: Retrieved session ID: " + lastSessionID);
            this.session_ID = lastSessionID;

            long lastSeed = preferences.getLong(KEY_PREFERENCES_SEED, DEFAULT_VALUE_NO_KEY);
            if (lastSeed == DEFAULT_VALUE_NO_KEY) {
                throw new IllegalStateException("Error! Session saved without seed!");
            } else {
                this.seed = lastSeed;
                Log.i(TAG, "RatingManager: Retrieved seed: " + seed);
            }

            String lastGenMessage = preferences.getString(KEY_PREFERENCES_GENERATOR_MESSAGE, "");
            if (lastGenMessage.equals("")) {
                throw new IllegalStateException("Error! Session saved without generator message!");
            } else {
                this.generatorMessage = lastGenMessage;
                Log.i(TAG, "RatingManager: Retrieved generator message: " + generatorMessage);
            }

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

            this.state = State.STATE_IDLE;
        }
    }

    private void makeNewSession() {
        this.session_ID = getNewSessionID(); // Increments last existing session number
        this.seed = System.nanoTime();
        Log.i(TAG, "RatingManager: Newly created seed: " + seed);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        this.generatorMessage = format.format(new Date(System.currentTimeMillis()));
        this.lastSessionRatings = new ArrayList<>();
        this.state = State.STATE_IDLE;
    }

    public int getLastSessionRating(int index) {
        if (lastSessionRatings.size() == 0) { // New session without saved previous ratings
            return Recording.DEFAULT_UNSET_RATING;
        } else {
            return lastSessionRatings.get(index);
        }
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getSession_ID() {
        return session_ID;
    }

    public long getSeed() {
        return seed;
    }

    public String getGeneratorMessage() {
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

    public boolean isRatingFinished(List<Recording> recordings) {
        for (Recording rec : recordings) {
            if (rec.getRating() == Recording.DEFAULT_UNSET_RATING) {
                return false;
            }
        }
        return true;
    }

    public String getRatingFinishedRatio(List<Recording> recordings) {
        int cnt = 0;
        int size = recordings.size();

        for (Recording rec : recordings) {
            if (rec.getRating() != Recording.DEFAULT_UNSET_RATING) {
                cnt++;
            }
        }
        return cnt + "/" + size;
    }


    public void saveSession(List<Recording> recordings) {
        // Save seed and compare on load?
        // Preferences - save session ID
        // TODO Save Ratings Progress

        if (recordings.size() <= 0) {
            Log.e(TAG, "saveSession: Invalid recordings list size!");
        } else {
            ArrayList<Integer> ratings = new ArrayList<>(recordings.size());
            for (Recording rec : recordings) {
                ratings.add(rec.getRating());
            }
            Gson gson = new Gson();

            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(KEY_PREFERENCES_SESSION_ID, session_ID);
            editor.putLong(KEY_PREFERENCES_SEED, seed);
            editor.putString(KEY_PREFERENCES_GENERATOR_MESSAGE, generatorMessage);
            editor.putString(KEY_PREFERENCES_RATINGS ,gson.toJson(ratings));
            editor.apply();
        }
    }


    public void saveResults(Context context) throws Exception {
        // TODO Save ratings to simple text file

        // Get external storage
        boolean readable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ||
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY);
        if (!readable) {
            throw new Exception("External storage could not be read");
        }
        File[] externalStorageVolumes =
                ContextCompat.getExternalFilesDirs(context, null);
        File primaryExternalStorage = externalStorageVolumes[0];
        Log.i(TAG, "getRecordings: External Storage root: " + primaryExternalStorage.getPath());


        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Ratings");
//        File directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Ratings");
        if (!directory.exists()) {
            Log.i(TAG, "saveResults: Directory <" + directory.getPath() + "> does not exist.");
            Log.i(TAG, "saveResults: Directory <" + directory.getAbsolutePath() + "> does not exist.");
            if (!directory.mkdirs()) {
                Log.i(TAG, "saveResults: Directory could not be created.");
            }
        }

        File file = new File(directory, "test.txt");
        FileWriter writer = new FileWriter(file);
        writer.append("Tohle je test.");
        writer.append("Tohle je další řádek.");
        writer.flush();
        writer.close();

        MediaScannerConnection.scanFile(context, new String[]{file.getPath()}, null, null);
    }
}
