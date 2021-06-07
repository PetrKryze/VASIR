package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import static com.petrkryze.vas.RatingResult.LABEL_GENERATOR_MESSAGE;
import static com.petrkryze.vas.RatingResult.LABEL_SAVE_DATE;
import static com.petrkryze.vas.RatingResult.LABEL_SEED;
import static com.petrkryze.vas.RatingResult.LABEL_SESSION_ID;
import static com.petrkryze.vas.Recording.DEFAULT_UNSET_RATING;
import static com.petrkryze.vas.Recording.recordingComparator;


/**
 * Created by Petr on 09.02.2020. Yay!
 */
class RatingManager {

    public enum State {STATE_IN_PROGRESS, STATE_FINISHED, STATE_IDLE}
    public enum LoadResult {OK, NO_SESSION, CORRUPTED_SESSION}
    public enum DirectoryCheckError {NOT_EXIST, NOT_DIRECTORY, NOT_READABLE, NO_FILES, MISSING_FILES}

    private int session_ID;
    private State state;
    private long seed;
    private String generatorMessage;
    private ArrayList<Integer> lastSessionRatings;
    private String datadir_path;
    private ArrayList<String> filelist = null; // For file consistency check in storage

    private final SharedPreferences preferences;
    private final RecordingsFoundListener recordingsFoundListener;

    private static int DEFAULT_VALUE_NO_KEY;
    private static String KEY_PREFERENCES_SESSION_ID;
    private static String KEY_PREFERENCES_SEED;
    private static String KEY_PREFERENCES_GENERATOR_MESSAGE;
    private static String KEY_PREFERENCES_RATINGS;
    private final String KEY_PREFERENCES_STATE;
    private final String KEY_PREFERENCES_DATADIR_PATH;
    private final String KEY_PREFERENCES_FILELIST;

    public static final char separator = ';';
    public static final char headerTag = '#';
    private static final String[] SUPPORTED_EXTENSIONS = {"wav","mp3"};
    public static final String DIRCHECK_RESULT_IS_OK = "dircheck_result_is_ok";
    public static final String DIRCHECK_RESULT_ERROR_TYPE = "dircheck_result_error_type";
    public static final String DIRCHECK_RESULT_DIRECTORY = "dircheck_result_directory";
    public static final String DIRCHECK_RESULT_MISSING_CNT = "dircheck_result_missing_cnt";
    public static final String DIRCHECK_RESULT_MISSING_LIST = "dircheck_result_missing_list";

    private static final String TAG = "RatingManager";

    public interface RecordingsFoundListener {
        void onFoundRecordings(ArrayList<File> raw_audio_files);
    }

    RatingManager(@NonNull Activity context, RecordingsFoundListener recordingsFoundListener) {
        Resources resources = context.getResources();
        DEFAULT_VALUE_NO_KEY = resources.getInteger(R.integer.DEFAULT_VALUE_NO_KEY);
        KEY_PREFERENCES_SESSION_ID = resources.getString(R.string.KEY_PREFERENCES_SESSION_ID);
        KEY_PREFERENCES_SEED = resources.getString(R.string.KEY_PREFERENCES_SEED);
        KEY_PREFERENCES_GENERATOR_MESSAGE = resources.getString(R.string.KEY_PREFERENCES_GENERATOR_MESSAGE);
        KEY_PREFERENCES_RATINGS = resources.getString(R.string.KEY_PREFERENCES_RATINGS);
        KEY_PREFERENCES_STATE = resources.getString(R.string.KEY_PREFERENCES_STATE);
        KEY_PREFERENCES_DATADIR_PATH = resources.getString(R.string.KEY_PREFERENCES_DATADIR);
        KEY_PREFERENCES_FILELIST = resources.getString(R.string.KEY_PREFERENCES_FILELIST);

        this.preferences = context.getPreferences(Context.MODE_PRIVATE);
        this.recordingsFoundListener = recordingsFoundListener;

        // TODO Delet dis after production version is done
//        SharedPreferences.Editor editor = preferences.edit();
//        editor.clear();
//        editor.commit();
    }

    public Bundle checkDataDirectoryPath() {
        // Check data directory that has been successfully loaded
        return checkDataDirectoryPath(new File(this.datadir_path));
    }

    public Bundle checkDataDirectoryPath(File datadir) {
        // podiva se esli adresar existuje
        // podiva se jestli v nem jsou vsechny files ktery jsou v ulozenym filelistu
        // ty co nenajde fyzicky ve storage by mohlo vypsat v dialogu
        Bundle ret = new Bundle();
        ret.putBoolean(DIRCHECK_RESULT_IS_OK, false); // Default on false, when ok overwrite
        ret.putString(DIRCHECK_RESULT_DIRECTORY, datadir.getName());

        if (!datadir.exists()) {
            ret.putString(DIRCHECK_RESULT_ERROR_TYPE, DirectoryCheckError.NOT_EXIST.toString());
            return ret;
        } else if (!datadir.isDirectory()) {
            ret.putString(DIRCHECK_RESULT_ERROR_TYPE, DirectoryCheckError.NOT_DIRECTORY.toString());
            return ret;
        } else if (!datadir.canRead()) {
            ret.putString(DIRCHECK_RESULT_ERROR_TYPE, DirectoryCheckError.NOT_READABLE.toString());
            return ret;
        } else {
            Log.i(TAG, "checkDataDirectoryPath: " + datadir.getAbsolutePath() +
                    " exists, is a directory and can be read.");

            ArrayList<File> raw_audio_files = getFilelist(datadir);
            ArrayList<String> foundFiles = fileListToPathList(raw_audio_files);
            Log.i(TAG, "checkDataDirectoryPath: Found " + foundFiles.size() + " audio files.");
            if (foundFiles.isEmpty()) {
                ret.putString(DIRCHECK_RESULT_ERROR_TYPE, DirectoryCheckError.NO_FILES.toString());
            } else {
                if (this.filelist != null) { // Previous session found, check consistency
                    ArrayList<String> savedFiles = new ArrayList<>(this.filelist);

                    for (String filePath : this.filelist) {
                        if (foundFiles.contains(filePath)) {
                            savedFiles.remove(filePath);
                        }
                    }

                    if (savedFiles.isEmpty()) {
                        // All files from saved file list are accounted for in the storage
                        ret.putBoolean(DIRCHECK_RESULT_IS_OK, true);
                        recordingsFoundListener.onFoundRecordings(raw_audio_files);
                    } else {
                        ret.putString(DIRCHECK_RESULT_ERROR_TYPE, DirectoryCheckError.MISSING_FILES.toString());
                        ret.putInt(DIRCHECK_RESULT_MISSING_CNT, savedFiles.size());
                        StringBuilder sb = new StringBuilder();

                        for (String str : savedFiles) {
                            sb.append(str).append("\n");
                        }
                        ret.putString(DIRCHECK_RESULT_MISSING_LIST, sb.toString());
                    }
                } else { // No previous session found, just create new file list
                    makeNewSession(datadir.getAbsolutePath(), foundFiles);
                    ret.putBoolean(DIRCHECK_RESULT_IS_OK, true);
                    recordingsFoundListener.onFoundRecordings(raw_audio_files);
                }
            }
            return ret;
        }
    }

    private ArrayList<File> getFilelist(File path) { // Extracts audio files recursively from folder
        ArrayList<File> foundFiles = new ArrayList<>();

        File[] files = path.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) { // File is directory
                    foundFiles.addAll(getFilelist(file));
                } else { // File is file (lol)
                    ArrayList<String> ext = new ArrayList<>(Arrays.asList(SUPPORTED_EXTENSIONS));

                    if (ext.contains(FilenameUtils.getExtension(file.getPath()).toLowerCase())) {
                        foundFiles.add(file);
                    } else {
                        Log.i(TAG, "getFilelist: " + file.getName() + " - unsupported extension.");
                    }
                }
            }
        }
        return foundFiles;
    }

    private ArrayList<String> fileListToPathList(ArrayList<File> filelist) {
        ArrayList<String> paths = new ArrayList<>();
        for (File f : filelist) {
            paths.add(f.getAbsolutePath());
        }
        return paths;
    }

    void makeNewSession(String new_datadir_path, ArrayList<String> foundAudioFiles) {
        this.session_ID = getNewSessionID(); // Increments last existing session number
        this.seed = System.nanoTime();
        Log.i(TAG, "RatingManager: Newly created seed: " + seed);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        this.generatorMessage = format.format(new Date(System.currentTimeMillis()));
        this.lastSessionRatings = new ArrayList<>();
        this.datadir_path = new_datadir_path;
        this.filelist = foundAudioFiles;
        this.state = State.STATE_IDLE;
    }

    void wipeCurrentSession() {
        Log.i(TAG, "wipeCurrentSession: Wiping session " + this.session_ID +
                ", (seed: )" + this.seed);
        this.session_ID = -1;
        this.seed = -1;
        this.generatorMessage = null;
        this.lastSessionRatings = null;
        this.datadir_path = null;
        this.filelist = null;
        this.state = null;
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

    public static final String GET_SESSION_INFO_LOAD_RESULT_KEY = "LoadResult";
    public static final String SESSION_INFO_BUNDLE_SESSION_ID = "sessionID";
    public static final String SESSION_INFO_BUNDLE_SEED = "seed";
    public static final String SESSION_INFO_BUNDLE_GENERATOR_MESSAGE = "generatorMessage";
    public static final String SESSION_INFO_BUNDLE_FINISHED_RATIO = "finishedRatio";

    public static Bundle getSessionInfo(Activity context) {
        Bundle out = new Bundle();
        SharedPreferences preferences = context.getPreferences(Context.MODE_PRIVATE);

        int lastSessionID = preferences.getInt(KEY_PREFERENCES_SESSION_ID, DEFAULT_VALUE_NO_KEY);
        if (lastSessionID == DEFAULT_VALUE_NO_KEY) {
            Log.i(TAG, "loadSession: No key for session ID found in preferences.");
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.NO_SESSION);
            return out;
        } else {
            out.putInt(SESSION_INFO_BUNDLE_SESSION_ID, lastSessionID);
        }

        long lastSeed = preferences.getLong(KEY_PREFERENCES_SEED, DEFAULT_VALUE_NO_KEY);
        if (lastSeed == DEFAULT_VALUE_NO_KEY) {
            Log.e(TAG, "loadSession: Error! Session saved without seed!");
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.CORRUPTED_SESSION);
            return out;
        } else {
            out.putLong(SESSION_INFO_BUNDLE_SEED, lastSeed);
        }

        String lastGenMessage = preferences.getString(KEY_PREFERENCES_GENERATOR_MESSAGE, "");
        if (lastGenMessage.equals("")) {
            Log.e(TAG, "loadSession: Error! Session saved without generator message!");
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.CORRUPTED_SESSION);
            return out;
        } else {
            out.putString(SESSION_INFO_BUNDLE_GENERATOR_MESSAGE, lastGenMessage);
        }

        Gson gson = new Gson();
        String json = preferences.getString(KEY_PREFERENCES_RATINGS, "");
        if (json.equals("")) {
            Log.e(TAG, "loadSession: Error! Session saved without ratings!");
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.CORRUPTED_SESSION);
            return out;
        } else {
            ArrayList<Integer> lastRatings = gson.fromJson(json,
                    new TypeToken<ArrayList<Integer>>(){}.getType());

            int cnt = 0;
            for (int rating : lastRatings) if (rating != DEFAULT_UNSET_RATING) cnt++;
            out.putString(SESSION_INFO_BUNDLE_FINISHED_RATIO, cnt + "/" + lastRatings.size());
        }

        out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.OK);
        return out;
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

    void saveSession(List<Recording> recordings) {
        if (recordings == null || recordings.size() <= 0) {
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
            editor.putString(KEY_PREFERENCES_RATINGS, gson.toJson(ratings));
            editor.putString(KEY_PREFERENCES_STATE, state.toString());
            editor.putString(KEY_PREFERENCES_DATADIR_PATH, datadir_path);
            editor.putString(KEY_PREFERENCES_FILELIST, gson.toJson(filelist));
            editor.apply();
        }
    }

    LoadResult loadSession() {
        // Load last saved session - returns false when no previous session is in memory
        // Load Session ID
        int lastSessionID = preferences.getInt(KEY_PREFERENCES_SESSION_ID, DEFAULT_VALUE_NO_KEY);
        if (lastSessionID == DEFAULT_VALUE_NO_KEY) {
            Log.i(TAG, "loadSession: No key for session ID found in preferences.");
            return LoadResult.NO_SESSION;
        } else {
            this.session_ID = lastSessionID;
            Log.i(TAG, "loadSession: Retrieved session ID: " + lastSessionID);
        }

        // Load last randomizer seed
        long lastSeed = preferences.getLong(KEY_PREFERENCES_SEED, DEFAULT_VALUE_NO_KEY);
        if (lastSeed == DEFAULT_VALUE_NO_KEY) {
            Log.e(TAG, "loadSession: Error! Session saved without seed!");
            return LoadResult.CORRUPTED_SESSION;
        } else {
            this.seed = lastSeed;
            Log.i(TAG, "loadSession: Retrieved seed: " + seed);
        }

        // Load last generator message
        String lastGenMessage = preferences.getString(KEY_PREFERENCES_GENERATOR_MESSAGE, "");
        if (lastGenMessage.equals("")) {
            Log.e(TAG, "loadSession: Error! Session saved without generator message!");
            return LoadResult.CORRUPTED_SESSION;
        } else {
            this.generatorMessage = lastGenMessage;
            Log.i(TAG, "loadSession: Retrieved generator message: " + generatorMessage);
        }

        // Load array list of last ratings
        Gson gson = new Gson();
        String json = preferences.getString(KEY_PREFERENCES_RATINGS, "");
        if (json.equals("")) {
            Log.e(TAG, "loadSession: Error! Session saved without ratings!");
            return LoadResult.CORRUPTED_SESSION;
        } else {
            ArrayList<Integer> lastRatings = gson.fromJson(json,
                    new TypeToken<ArrayList<Integer>>(){}.getType());
            this.lastSessionRatings = lastRatings;
            Log.i(TAG, "loadSession: Retrieved ratings: " + lastRatings.toString());
        }

        // Load last rating state
        String lastState = preferences.getString(KEY_PREFERENCES_STATE, "");
        if (lastState.equals("")) {
            Log.e(TAG, "loadSession: Error! Session saved without state!");
            return LoadResult.CORRUPTED_SESSION;
        } else {
            setState(lastState);
            Log.i(TAG, "loadSession: Retrieved state: " + state);
        }

        // Load data directory path
        String datadir_path = preferences.getString(KEY_PREFERENCES_DATADIR_PATH, "");
        if (datadir_path.equals("")) {
            Log.e(TAG, "loadSession: Error! Session saved without data directory path!");
            return LoadResult.CORRUPTED_SESSION;
        } else {
            this.datadir_path = datadir_path;
            Log.i(TAG, "loadSession: Retrieved local data directory path: " + datadir_path);
        }

        // Load array list of session files
        gson = new Gson();
        json = preferences.getString(KEY_PREFERENCES_FILELIST, "");
        if (json.equals("")) {
            Log.e(TAG, "loadSession: Error! Session saved without file list!");
            return LoadResult.CORRUPTED_SESSION;
        } else {
            this.filelist = gson.fromJson(json,
                    new TypeToken<ArrayList<String>>(){}.getType());
            Log.i(TAG, "loadSession: Retrieved file list: " + filelist.toString());
        }

        checkDataDirectoryPath();
        return LoadResult.OK;
    }

    static void checkResultsDirectory(File resultsDirectory) throws IOException {
        String path = resultsDirectory.getAbsolutePath();
        if (resultsDirectory.exists()) {
            Log.i(TAG, "saveResults: Results directory <" + path + "> exists.");

            if (resultsDirectory.isDirectory()) {
                Log.i(TAG, "checkResultsDirectory: Results directory <" +
                        path + "> is a directory.");
                if (resultsDirectory.canRead()) {
                    Log.i(TAG, "checkResultsDirectory: Results directory <" +
                            path + "> is readable.");
                    if (resultsDirectory.canWrite()) {
                        Log.i(TAG, "checkResultsDirectory: Results directory <" +
                                path + "> is writable.");
                    } else {
                        throw new IOException("Results directory <" + path + "> cannot be written to!");
                    }
                } else {
                    throw new IOException("Results directory <" + path + "> cannot be read!");
                }
            } else {
                throw new IOException("Results directory <" + path + "> is not a directory!");
            }
        } else {
            Log.i(TAG, "saveResults: Results directory <" + path + "> does not exist - creating");
            if (!resultsDirectory.mkdirs()) {
                throw new IOException("Results directory <" + path + "> could not be created!");
            } else {
                checkResultsDirectory(resultsDirectory);
            }
        }
    }

    public static ArrayList<RatingResult> loadResults(Context context) throws Exception {
        // TODO
        File resultsDir = new File(context.getFilesDir(), context.getString(R.string.DIRECTORY_NAME_RESULTS));
        checkResultsDirectory(resultsDir);

        ArrayList<RatingResult> foundResults = new ArrayList<>();

        // Find any previous save files for this session ID
        File[] resultFiles = resultsDir.listFiles();
        if (resultFiles == null) {
            throw new Exception("Results directory is invalid!");
        } else {
            if (resultFiles.length == 0) {
                Log.i(TAG, "saveResults: No result files found in the result directory.");
            } else {
                Log.i(TAG, "saveResults: FILE LIST:");
                for (File foundResultFile : resultFiles) {
                    Log.i(TAG, "saveResults: FILE: " + foundResultFile.getAbsolutePath());
                    if (foundResultFile.isFile() && foundResultFile.exists() &&
                            FilenameUtils.getExtension(foundResultFile.getName()).equalsIgnoreCase("txt")) {

                        foundResults.add(new RatingResult(foundResultFile));
                    }
                }
                Log.i(TAG, "saveResults: Found " + foundResults.size() + " save files.");
            }
        }

        return foundResults;
    }

    void saveResults(Context context, List<Recording> recordings) throws Exception {
        File resultsDir = new File(context.getFilesDir(), context.getString(R.string.DIRECTORY_NAME_RESULTS));
        checkResultsDirectory(resultsDir);

        // Find any previous save files for this session ID
        File[] resultFiles = resultsDir.listFiles();
        ArrayList<File> existingSaveFiles = new ArrayList<>();
        if (resultFiles == null) {
            throw new Exception("Results directory is invalid!");
        } else {
            if (resultFiles.length == 0) {
                Log.i(TAG, "saveResults: No result files found in the result directory.");
            } else {
                Log.i(TAG, "saveResults: FILE LIST:");
                for (File file : resultFiles) {
                    Log.i(TAG, "saveResults: FILE: " + file.getAbsolutePath());
                    if (file.isFile() && file.exists() &&
                            FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("txt")) {

                        String[] split = file.getName().split("_");
                        if (split.length > 2) {
                            String foundSessionId = split[1];
                            if (Integer.parseInt(foundSessionId) == session_ID) {
                                existingSaveFiles.add(file);
                            }
                        }
                    }
                }
                Log.i(TAG, "saveResults: Found " + existingSaveFiles.size() +
                        " existing save files for session ID " + session_ID);
            }
        }

        // Sort and log the randomized recordings
        recordings.sort(recordingComparator);
        Log.i(TAG, "saveResults: Sorted recordings:");
        for (Recording r : recordings) {
            Log.i(TAG, "saveResults: " + r.toString());
        }

        // Create new file name for the new save
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        String saveDate = format.format(new Date(System.currentTimeMillis()));
        String newRatingsFileName = context.getString(R.string.RATING_FILE_NAME,
                session_ID, saveDate);

        File newRatingsFile = new File(resultsDir, newRatingsFileName);
        FileWriter writer = new FileWriter(newRatingsFile);

        // Write file header
        String header = headerTag + LABEL_SESSION_ID + separator + session_ID + "\n" +
                headerTag + LABEL_SEED + separator + seed + "\n" +
                headerTag + LABEL_GENERATOR_MESSAGE + separator + generatorMessage + "\n" +
                headerTag + LABEL_SAVE_DATE + separator + saveDate + "\n";
        writer.append(header);

        // Row format: id;group;random_number;rating
        for (Recording recording : recordings) {
            // TODO Generalize check? ID and group type
            writer.append(recording.getID()).append(separator)
                    .append(recording.getGroupType().toString()).append(separator)
                    .append(String.valueOf(recording.getRandomized_number())).append(separator)
                    .append(String.valueOf(recording.getRating())).append("\n");
        }
        writer.flush();
        writer.close();

        MediaScannerConnection.scanFile(context, new String[]{newRatingsFile.getPath()}, null,
                (path, uri) -> Log.i(TAG, "onScanCompleted: SCAN OF FILE <" + path + "> completed!"));
        Log.i(TAG, "saveResults: File: <" + newRatingsFile.getName() + "> written successfully.");

        // Remove old save files for this session ID
        int backupsNumber = context.getResources().getInteger(R.integer.SAVE_FILE_BACKUPS_NUMBER);
        if (existingSaveFiles.size() > backupsNumber) {
            Collections.sort(existingSaveFiles); // Sorts ascending - oldest files are first

            for (int i = 0; i < (existingSaveFiles.size() - backupsNumber); i++) {
                File oldBackup = existingSaveFiles.get(i);

                if (oldBackup.delete()) {
                    Log.i(TAG, "saveResults: Old save file <" + oldBackup.getAbsolutePath() +
                            "> was successfully deleted.");
                } else {
                    Log.e(TAG, "saveResults: Old save file <" + oldBackup.getAbsolutePath() +
                            "> could not be deleted.");
                }
            }
        }
    }

    @Deprecated
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
