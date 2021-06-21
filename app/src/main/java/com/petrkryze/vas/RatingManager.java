package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.Random;

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
public class RatingManager {

    public enum State {STATE_IN_PROGRESS, STATE_FINISHED, STATE_IDLE}
    public enum LoadResult {OK, NO_SESSION, CORRUPTED_SESSION}
    public enum DirectoryCheckError {NOT_EXIST, NOT_DIRECTORY, NOT_READABLE, NO_FILES, MISSING_FILES}

    // Session defining variables
    private int session_ID;
    private long seed;
    private String generatorMessage;
    private List<Recording> trackList;
    private ArrayList<Integer> ratings;

    private ArrayList<String> fileList = null; // For file consistency check in storage

    private State state;
    private String datadirPath;
    private int trackPointer = 0;
    private int savedPlayProgress = 0;

    private final SharedPreferences preferences;

    private static final String KEY_PREFERENCES_CURRENT_SESSION = "current_session";
    private static final String KEY_PREFERENCES_STATE = "state";
    private static final String KEY_PREFERENCES_DATADIR_PATH = "datadir";
    private static final String KEY_PREFERENCES_TRACK_POINTER = "track_pointer";
    private static final String KEY_PREFERENCES_SAVED_PLAY_PROGRESS = "saved_play_progress";

    public static final char separator = ';';
    public static final char headerTag = '#';
    private static final String[] SUPPORTED_EXTENSIONS = {"wav","mp3"};
    public static final String DIRCHECK_RESULT_IS_OK = "dircheck_result_is_ok";
    public static final String DIRCHECK_RESULT_ERROR_TYPE = "dircheck_result_error_type";
    public static final String DIRCHECK_RESULT_DIRECTORY = "dircheck_result_directory";
    public static final String DIRCHECK_RESULT_MISSING_CNT = "dircheck_result_missing_cnt";
    public static final String DIRCHECK_RESULT_MISSING_LIST = "dircheck_result_missing_list";

    private static final String TAG = "RatingManager";

    public RatingManager(@NonNull Activity context) {
        this.preferences = context.getPreferences(Context.MODE_PRIVATE);

        // TODO Delet dis after production version is done
//        SharedPreferences.Editor editor = preferences.edit();
//        editor.clear();
//        editor.commit();
    }

    public Bundle checkDataDirectoryPath() {
        // Check data directory that has been successfully loaded
        return checkDataDirectoryPath(new File(this.datadirPath));
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

            ArrayList<File> foundFilesFiles = getFilelist(datadir);
            ArrayList<String> foundFilesPaths = fileListToPathList(foundFilesFiles);
            Log.i(TAG, "checkDataDirectoryPath: Found " + foundFilesPaths.size() + " audio files.");
            if (foundFilesPaths.isEmpty()) {
                ret.putString(DIRCHECK_RESULT_ERROR_TYPE, DirectoryCheckError.NO_FILES.toString());
            } else {
                if (this.fileList != null) { // Previous session found, check consistency
                    ArrayList<String> savedFiles = new ArrayList<>(this.fileList);

                    for (String filePath : this.fileList) {
                        if (foundFilesPaths.contains(filePath)) {
                            savedFiles.remove(filePath);
                        }
                    }

                    if (savedFiles.isEmpty()) {
                        // All files from saved file list are accounted for in the storage
                        ret.putBoolean(DIRCHECK_RESULT_IS_OK, true);
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
                    makeNewSession(datadir.getAbsolutePath(), foundFilesPaths);
                    ret.putBoolean(DIRCHECK_RESULT_IS_OK, true);
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

    void makeNewSession(String newDatadirPath, ArrayList<String> foundFilesPaths) {
        this.session_ID = getNewSessionID(); // Increments last existing session number
        this.seed = System.nanoTime();
        Log.i(TAG, "RatingManager: Newly created seed: " + seed);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        this.generatorMessage = format.format(new Date(System.currentTimeMillis()));
        this.ratings = new ArrayList<>();
        this.datadirPath = newDatadirPath;
        this.fileList = foundFilesPaths;
        this.state = State.STATE_IDLE;
        this.trackPointer = 0;
        this.savedPlayProgress = 0;

        int Nrec = foundFilesPaths.size();
        Collections.sort(foundFilesPaths); // Ensures the same order after loading
        // Shuffle the loaded paths randomly using the generated seed
        Collections.shuffle(foundFilesPaths, new Random(this.seed));
        List<Integer> rnums = linspace(1, Nrec);

        List<Recording> recordings = new ArrayList<>();
        for (int n = 0; n < foundFilesPaths.size(); n++) {
            recordings.add(new Recording(
                    foundFilesPaths.get(n),
                    rnums.get(n),
                    DEFAULT_UNSET_RATING
            ));
        }
        this.trackList = recordings;

        saveSession();
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

    public void wipeCurrentSession() {
        Log.i(TAG, "wipeCurrentSession: Wiping session " + this.session_ID +
                ", (seed: )" + this.seed);
        this.session_ID = -1;
        this.seed = -1;
        this.generatorMessage = null;
        this.ratings = null;
        this.datadirPath = null;
        this.fileList = null;
        this.trackList = null;
        this.state = null;
        this.trackPointer = -1;
        this.savedPlayProgress = -1;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
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

    public int getTrackPointer() {
        return trackPointer;
    }

    public void trackIncrement() {
        int current = this.trackPointer;
        this.trackPointer = current + 1;
    }

    public void trackDecrement() {
        int current = this.trackPointer;
        this.trackPointer = current - 1;
    }

    public int getSavedPlayProgress() {
        return savedPlayProgress;
    }

    public void setPlayProgress(int savedPlayProgress) {
        this.savedPlayProgress = savedPlayProgress;
    }

    public List<Recording> getTrackList() {
        return trackList;
    }

    public int getTrackN() {
        return trackList.size();
    }

    public static final String GET_SESSION_INFO_LOAD_RESULT_KEY = "LoadResult";
    public static final String SESSION_INFO_BUNDLE_SESSION = "session";

    public static Bundle getSessionInfo(Activity context) {
        Bundle out = new Bundle();
        SharedPreferences preferences = context.getPreferences(Context.MODE_PRIVATE);

        // Load last saved session - returns LoadResult.NO_SESSION when no previous session is in memory
        String json = preferences.getString(KEY_PREFERENCES_CURRENT_SESSION, "");
        if (json.equals("")) {
            Log.i(TAG, "loadSession: No session found in preferences.");
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.NO_SESSION);
            return out;
        }

        try {
            RatingResult currentSession = new Gson().fromJson(json,
                    new TypeToken<RatingResult>(){}.getType());
            out.putSerializable(SESSION_INFO_BUNDLE_SESSION, currentSession);
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.OK);
        } catch (Exception e) {
            e.printStackTrace();
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.CORRUPTED_SESSION);
            return out;
        }

        return out;
    }

    private int getNewSessionID() {
        String json = preferences.getString(KEY_PREFERENCES_CURRENT_SESSION, "");
        if (json.equals("")) {
            Log.i(TAG, "getNewSessionID: No key for session ID found in preferences.");
            return 1;
        } else {
            Gson gson = new Gson();
            RatingResult currentSession = gson.fromJson(json,
                    new TypeToken<RatingResult>(){}.getType());

            int lastSessionID = currentSession.getSession_ID();
            Log.i(TAG, "getNewSessionID: NEW SESSION ID = " + (lastSessionID + 1));
            return lastSessionID + 1;
        }
    }

    public boolean isRatingFinished(List<Recording> recordings) {
        for (Recording rec : recordings) {
            if (rec.getRating() == DEFAULT_UNSET_RATING) {
                return false;
            }
        }
        return true;
    }

    public void saveSession() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        String saveDate = format.format(new Date(System.currentTimeMillis()));

        RatingResult currentSession = new RatingResult(
                this.session_ID,
                this.seed,
                this.generatorMessage,
                saveDate,
                this.trackList
        );

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREFERENCES_CURRENT_SESSION, new Gson().toJson(currentSession))
                .putString(KEY_PREFERENCES_STATE, this.state.toString())
                .putString(KEY_PREFERENCES_DATADIR_PATH, this.datadirPath)
                .putInt(KEY_PREFERENCES_TRACK_POINTER, this.trackPointer)
                .putInt(KEY_PREFERENCES_SAVED_PLAY_PROGRESS, this.savedPlayProgress)
                .apply();

        Log.i(TAG, "saveSession: Saving current session data: " +
                "Session ID = " + session_ID + "\n" +
                "Seed = " + seed + "\n" +
                "Generator Message = " + generatorMessage + "\n" +
                "Save Date = " + saveDate + "\n" +
                "State = " + state + "\n" +
                "Recordings: " + this.trackList.size() + " files");

    }

    public LoadResult loadSession() {
        // Load last saved session - returns LoadResult.NO_SESSION when no previous session is in memory
        String json = preferences.getString(KEY_PREFERENCES_CURRENT_SESSION, "");
        if (json.equals("")) {
            Log.i(TAG, "loadSession: No session found in preferences.");
            return LoadResult.NO_SESSION;
        }

        // Load last rating state
        String lastState = preferences.getString(KEY_PREFERENCES_STATE, "");
        if (lastState.equals("")) {
            Log.e(TAG, "loadSession: Error! Session saved without state!");
            return LoadResult.CORRUPTED_SESSION;
        }

        // Load data directory path
        String datadirPath = preferences.getString(KEY_PREFERENCES_DATADIR_PATH, "");
        if (datadirPath.equals("")) {
            Log.e(TAG, "loadSession: Error! Session saved without data directory path!");
            return LoadResult.CORRUPTED_SESSION;
        }

        // Load track pointer
        int trackPointer = preferences.getInt(KEY_PREFERENCES_TRACK_POINTER, -1);
        if (trackPointer == -1) {
            Log.e(TAG, "loadSession: Error! Session saved without track pointer value!");
            return LoadResult.CORRUPTED_SESSION;
        }

        // Load saved play progress
        int savedPlayProgress = preferences.getInt(KEY_PREFERENCES_SAVED_PLAY_PROGRESS, -1);
        if (savedPlayProgress == -1) {
            Log.e(TAG, "loadSession: Error! Session saved without saved play progress!");
            return LoadResult.CORRUPTED_SESSION;
        }

        RatingResult currentSession = new Gson().fromJson(json,
                new TypeToken<RatingResult>(){}.getType());

        try {
            this.session_ID = currentSession.getSession_ID();
            this.seed = currentSession.getSeed();
            this.generatorMessage = currentSession.getGeneratorMessage();
            this.trackList = currentSession.getRecordings();

            ArrayList<Integer> lastRatings = new ArrayList<>();
            ArrayList<String> lastFileList = new ArrayList<>();
            for (Recording r : currentSession.getRecordings()) {
                lastRatings.add(r.getRating());
                lastFileList.add(r.getPath());
            }
            this.ratings = lastRatings;
            this.fileList = lastFileList;

            setState(lastState);
            this.datadirPath = datadirPath;
            this.trackPointer = trackPointer;
            this.savedPlayProgress = savedPlayProgress;
        } catch (Exception e) {
            e.printStackTrace();
            return LoadResult.CORRUPTED_SESSION;
        }

        Log.i(TAG, "loadSession: Retrieved session ID: " + this.session_ID);
        Log.i(TAG, "loadSession: Retrieved seed: " + this.seed);
        Log.i(TAG, "loadSession: Retrieved generator message: " + this.generatorMessage);
        Log.i(TAG, "loadSession: Retrieved tracklist: " + this.trackList.toString());
        Log.i(TAG, "loadSession: Retrieved ratings: " + this.ratings.toString());
        Log.i(TAG, "loadSession: Retrieved file list: " + this.fileList.toString());

        Log.i(TAG, "loadSession: Retrieved state: " + this.state);
        Log.i(TAG, "loadSession: Retrieved local data directory path: " + this.datadirPath);
        Log.i(TAG, "loadSession: Retrieved track pointer value: " + this.trackPointer);
        Log.i(TAG, "loadSession: Retrieved saved play progress: " + this.savedPlayProgress);
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
        Log.i(TAG, "loadResults: Loading results...");
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

    public void saveResults(Context context) throws Exception {
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
        this.trackList.sort(recordingComparator);
        Log.i(TAG, "saveResults: Sorted recordings:");
        for (Recording r : this.trackList) {
            Log.i(TAG, "saveResults: " + r.toString());
        }

        // Create new file name for the new save
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        String saveDate = format.format(new Date(System.currentTimeMillis()));
        String newRatingsFileName = context.getString(R.string.RATING_FILE_NAME,
                this.session_ID, saveDate);

        File newRatingsFile = new File(resultsDir, newRatingsFileName);
        FileWriter writer = new FileWriter(newRatingsFile);

        // Write file header
        String header = headerTag + LABEL_SESSION_ID + separator + this.session_ID + "\n" +
                headerTag + LABEL_SEED + separator + this.seed + "\n" +
                headerTag + LABEL_GENERATOR_MESSAGE + separator + this.generatorMessage + "\n" +
                headerTag + LABEL_SAVE_DATE + separator + saveDate + "\n";
        writer.append(header);

        // Row format: id;group;random_number;rating
        for (Recording recording : this.trackList) {
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
