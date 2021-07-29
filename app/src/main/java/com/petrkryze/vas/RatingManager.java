package com.petrkryze.vas;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.petrkryze.vas.fragments.GroupControlFragment;
import com.petrkryze.vas.fragments.RatingFragmentDirections;

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
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import static com.petrkryze.vas.RatingResult.LABEL_GENERATOR_MESSAGE;
import static com.petrkryze.vas.RatingResult.LABEL_SAVE_DATE;
import static com.petrkryze.vas.RatingResult.LABEL_SEED;
import static com.petrkryze.vas.RatingResult.LABEL_SESSION_ID;
import static com.petrkryze.vas.Recording.DEFAULT_UNSET_RATING;

/**
 * Created by Petr on 09.02.2020. Yay!
 */
public class RatingManager {

    public enum State {STATE_IN_PROGRESS, STATE_FINISHED, STATE_IDLE}
    public enum LoadResult {OK, NO_SESSION, CORRUPTED_SESSION}
    public enum FileCheckError {NOT_EXIST, NOT_DIRECTORY, NOT_FILE, NOT_READABLE, NOT_WRITABLE,
        NO_FILES, MISSING_FILES, OK}

    public interface GroupFoldersUserCheckCallback {
        void groupCheckFinished(boolean confirmed);
    }

    // Session defining variables
    private int session_ID;
    private long seed;
    private String generatorMessage;
    private List<Recording> trackList;
    private ArrayList<Integer> ratings;
    private String currentSessionFilePath;

    private ArrayList<Uri> fileList = null; // For file consistency check in storage

    private State state;
    private Uri dataDirPath;
    private int trackPointer = -1;
    private int savedPlayProgress = Player.SAVED_PROGRESS_DEFAULT;

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
        this.state = State.STATE_IDLE;

//         TODO Delet dis after production version is done
//        SharedPreferences.Editor editor = preferences.edit();
//        editor.clear();
//        editor.commit();
    }

    public static final String GROUP_CHECK_RESULT_REQUEST_KEY = "group_check_result";
    @SuppressWarnings("unchecked")
    public Bundle checkDataDirectoryPath(Uri rootDataUri, boolean newSession, Fragment caller,
                                         GroupFoldersUserCheckCallback callback) {
        DocumentFile rootDataDir = DocumentFile.fromTreeUri(caller.requireContext(), rootDataUri);
        assert rootDataDir != null;

        DocumentFile[] documentFiles = rootDataDir.listFiles();
        for (DocumentFile document : documentFiles) {
            Log.d(TAG, "checkDataDirectoryPath: File: " + document.getName());
        }

        Bundle ret = new Bundle();
        ret.putBoolean(DIRCHECK_RESULT_IS_OK, false); // Default on false, when ok overwrite
        ret.putString(DIRCHECK_RESULT_DIRECTORY, rootDataDir.getName());

        // Basic checks for root directory
        Pair<Boolean, FileCheckError> rootCheck = existsIsDirCanRead(rootDataDir);
        if (!rootCheck.first) {
            ret.putString(DIRCHECK_RESULT_ERROR_TYPE, rootCheck.second.toString());
            return ret;
        }
        // Assess group folders placed directly in root
        ArrayList<GroupFolder> groupFolders = getGroupFolderList(rootDataDir);

        // If no good group folders found check for files only placed directly in root
        if (groupFolders.isEmpty()) {
            ArrayList<DocumentFile> foundFiles = getFileList(rootDataDir, false);
            if (!foundFiles.isEmpty()) {
                ArrayList<Uri> foundFilesPaths = fileListToUriList(foundFiles);
                Collections.sort(foundFilesPaths);
                groupFolders.add(new GroupFolder(rootDataDir.getUri(), foundFilesPaths));
            }
        }

        // If no suitable audio files have been found even in root, exit with error
        if (groupFolders.isEmpty()) {
            ret.putString(DIRCHECK_RESULT_ERROR_TYPE, FileCheckError.NO_FILES.toString());
        } else {
            for (GroupFolder gf : groupFolders) { // Just logging
                Log.d(TAG, "checkDataDirectoryPath: Group folder: " +
                        gf.getFolderName() + ", Files: " + gf.getNaudioFiles() + " - PASSED");
            }

            if (!newSession) { // Previous session found, check consistency
                ArrayList<Uri> lastSessionFiles = new ArrayList<>(this.fileList);

                for (Uri filePath : this.fileList) {
                    for (GroupFolder gf : groupFolders) {
                        if (gf.getFileList().contains(filePath)) {
                            lastSessionFiles.remove(filePath);
                        }
                    }
                }

                // All files from saved file list are accounted for in the storage
                if (lastSessionFiles.isEmpty()) {
                    ret.putBoolean(DIRCHECK_RESULT_IS_OK, true);
                    // Use callback to signal that the previous session data is checked and ok
                    callback.groupCheckFinished(true);
                } else {
                    ret.putString(DIRCHECK_RESULT_ERROR_TYPE, FileCheckError.MISSING_FILES.toString());
                    ret.putInt(DIRCHECK_RESULT_MISSING_CNT, lastSessionFiles.size());
                    StringBuilder sb = new StringBuilder();

                    for (Uri uri : lastSessionFiles) {
                        sb.append(uri).append("\n");
                    }
                    ret.putString(DIRCHECK_RESULT_MISSING_LIST, sb.toString());
                }
            } else { // No previous session found, fire GroupFolder user check fragment
                // Callback for the user check fragment, after check is confirmed, make new session
                caller.getParentFragmentManager().setFragmentResultListener(
                        GROUP_CHECK_RESULT_REQUEST_KEY, caller,
                        (requestKey, result) -> {
                            if (requestKey.equals(GROUP_CHECK_RESULT_REQUEST_KEY)) {
                                if (result.getBoolean(GroupControlFragment.GroupControlConfirmedKey)) {
                                    ArrayList<GroupFolder> validGroupFolders =
                                            (ArrayList<GroupFolder>) result.getSerializable(
                                                    GroupControlFragment.GroupFolderListSerializedKey
                                            );

                                    wipeCurrentSession();
                                    makeNewSession(rootDataDir, validGroupFolders, callback);
                                } else { // GroupControlFragment exit without confirm
                                    // Signal to RatingFragment that the process was cancelled
                                    callback.groupCheckFinished(false);
                                }
                            }
                        }
                );

                // Bring up the fragment for user to check the found folders
                groupFolders.sort(GroupFolder.groupComparator);
                NavDirections directions =
                        RatingFragmentDirections.actionRatingFragmentToGroupControlFragment(groupFolders);
                NavHostFragment.findNavController(caller).navigate(directions);

                ret.putBoolean(DIRCHECK_RESULT_IS_OK, true);
            }
        }
        return ret;
    }

    /**
     * @param dirOrFile Java {@link File} object of either a file or a directory to be checked for
     *                  existence, file-ness or directory-ness and readability.
     * @param checkForDir A boolean value to specify which type of file should the method check for
     *                    - {@code true} for a directory, {@code false} for a file.
     * @return Returns a {@link Pair} containing {@code true} in the first member if and only if the
     * {@link File} exists, is the specified type, and can be read. Second member contains a
     * {@link FileCheckError} enum instance of indicating the specific outcome of the check.
     */
    private static Pair<Boolean, FileCheckError> existsIsDirOrFileCanReadCanWrite(DocumentFile dirOrFile,
                                                                                  boolean checkForDir,
                                                                                  boolean checkForWritable) {
        if (!dirOrFile.exists()) {
            return Pair.create(false, FileCheckError.NOT_EXIST);
        } else {
            if (checkForDir) { // We are looking for directory
                if (!dirOrFile.isDirectory()) {
                    return Pair.create(false, FileCheckError.NOT_DIRECTORY);
                }
            } else { // We are looking for a file
                if (!dirOrFile.isFile()) {
                    return Pair.create(false, FileCheckError.NOT_FILE);
                }
            }

            if (!dirOrFile.canRead()) {
                return Pair.create(false, FileCheckError.NOT_READABLE);
            } else if (checkForWritable) {
                if (!dirOrFile.canWrite()) {
                    return Pair.create(false, FileCheckError.NOT_WRITABLE);
                }
            }
        }
        return Pair.create(true, FileCheckError.OK);
    }

   private static Pair<Boolean, FileCheckError> existsIsDirCanReadCanWrite(File dir) {
        return existsIsDirOrFileCanReadCanWrite(DocumentFile.fromFile(dir), true, true);
    }

    private static Pair<Boolean, FileCheckError> existsIsDirCanRead(DocumentFile dir) {
        return existsIsDirOrFileCanReadCanWrite(dir, true, false);
    }

    private static Pair<Boolean, FileCheckError> existsIsFileCanRead(File file) {
        return existsIsDirOrFileCanReadCanWrite(DocumentFile.fromFile(file), false, false);
    }

    private ArrayList<GroupFolder> getGroupFolderList(DocumentFile root) {
        ArrayList<GroupFolder> foundFolders = new ArrayList<>();

        DocumentFile[] files = root.listFiles();
        if (files.length > 0) { // Check for empty root directory
            for (DocumentFile potentialGroupFolder : files) {
                Pair<Boolean, FileCheckError> folderCheck = existsIsDirCanRead(potentialGroupFolder);

                if (folderCheck.first) { // That's what we want
                    // Goes in depth and looks if there are any usable files in this directory
                    ArrayList<DocumentFile> foundAudioFiles = getFileList(potentialGroupFolder);

                    if (!foundAudioFiles.isEmpty()) { // Some audio was found!
                        ArrayList<Uri> foundAudioUris = fileListToUriList(foundAudioFiles);
                        Collections.sort(foundAudioUris);
                        foundFolders.add(new GroupFolder(potentialGroupFolder.getUri(), foundAudioUris));
                    } // else ignore this folder and move on
                } else {
                    Log.d(TAG, "getGroupFolderList: File: " +
                            potentialGroupFolder.getUri() + ", Message: " +
                            folderCheck.second.toString());
                }
            }
        } // else Empty root
        return foundFolders;
    }

    private ArrayList<DocumentFile> getFileList(DocumentFile path) {
        return getFileList(path, true);
    }

    // Extracts audio files recursively from folder
    private ArrayList<DocumentFile> getFileList(DocumentFile path, boolean goDeep) {
        ArrayList<DocumentFile> foundFiles = new ArrayList<>();

        DocumentFile[] files = path.listFiles();
        if (files.length > 0) {
            for (DocumentFile file : files) {
                if (file.isDirectory() && goDeep) { // File is directory
                    foundFiles.addAll(getFileList(file));
                } else { // File is file (lol)
                    ArrayList<String> ext = new ArrayList<>(Arrays.asList(SUPPORTED_EXTENSIONS));

                    assert file.getName() != null;
                    if (ext.contains(FilenameUtils.getExtension(file.getName()).toLowerCase())) {
                        foundFiles.add(file);
                    } else {
                        Log.w(TAG, "getFilelist: " + file.getName() + " - unsupported extension.");
                    }
                }
            }
        }
        return foundFiles;
    }

    private ArrayList<Uri> fileListToUriList(ArrayList<DocumentFile> fileList) {
        ArrayList<Uri> paths = new ArrayList<>();
        for (DocumentFile f : fileList) {
            paths.add(f.getUri());
        }
        return paths;
    }

    void makeNewSession(DocumentFile rootDirectory,
                        ArrayList<GroupFolder> validGroupFolders,
                        GroupFoldersUserCheckCallback callback) {
        this.session_ID = getNewSessionID(); // Increments last existing session number
        this.seed = System.nanoTime();
        Log.i(TAG, "RatingManager: Newly created seed: " + seed);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        this.generatorMessage = format.format(new Date(System.currentTimeMillis()));
        this.currentSessionFilePath = null; // Will be filled when result is saved

        this.dataDirPath = rootDirectory.getUri();
        this.state = State.STATE_IDLE;
        this.trackPointer = 0;
        this.savedPlayProgress = 0;

        // GroupFolders and FileLists in them are already sorted alphabetically in previous steps!
        int Nrec = 0; // Get total number of recordings
        for (GroupFolder gf : validGroupFolders) {
            Nrec = Nrec + gf.getFileList().size();
        }

        // Create list of random indexes by shuffling a list of integers from 1 to Nrec
        // Use the newly generated random seed for that
        List<Integer> rNums = linspace(1, Nrec);
        Collections.shuffle(rNums, new Random(this.seed));

        // Create Recording objects for each file and assign a random index to it
        int counter = 0;
        ArrayList<Recording> joinedRecordingsList = new ArrayList<>();
        for (GroupFolder gf : validGroupFolders) {
            for (Uri fileUri : gf.getFileList()) {
                joinedRecordingsList.add(new Recording(
                        fileUri, gf.getLabel(), rNums.get(counter), DEFAULT_UNSET_RATING
                ));
                counter++;
            }
        }
        assert (Nrec == counter); // Sanity check, I hope this works jesus christ

        // Sorts using the random index (basically randomizing the files) for easy player access
        joinedRecordingsList.sort(Recording.sortByRandomIndex);
        this.trackList = joinedRecordingsList;

        ArrayList<Integer> newRatings = new ArrayList<>();
        ArrayList<Uri> newJoinedFileList = new ArrayList<>();
        for (Recording r : this.trackList) {
            newRatings.add(r.getRating());
            newJoinedFileList.add(r.getUri());
        }
        this.ratings = newRatings;
        Collections.sort(newJoinedFileList);
        this.fileList = newJoinedFileList;

        saveSession();
        callback.groupCheckFinished(true);
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
                ", (seed: " + this.seed + ")");
        this.session_ID = -1;
        this.seed = -1;
        this.generatorMessage = null;
        this.currentSessionFilePath = null;
        this.ratings = null;
        this.dataDirPath = null;
        this.fileList = null;
        this.trackList = null;
        this.state = null;
        this.trackPointer = -1;
        this.savedPlayProgress = Player.SAVED_PROGRESS_DEFAULT;
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

    public void trackToFirst() {
        this.trackPointer = 0;
    }

    public void trackToLast() {
        this.trackPointer = trackList.size() - 1;
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

    public Uri getDataDirPath() { return dataDirPath; }

    public static final String GET_SESSION_INFO_LOAD_RESULT_KEY = "LoadResult";
    public static final String SESSION_INFO_BUNDLE_SESSION = "session";

    public static Bundle getSessionInfo(Activity context) {
        Bundle out = new Bundle();
        SharedPreferences preferences = context.getPreferences(Context.MODE_PRIVATE);

        // Load last saved session - returns LoadResult.NO_SESSION when no previous session is in memory
        String json = preferences.getString(KEY_PREFERENCES_CURRENT_SESSION, "");
        if (json.equals("")) {
            Log.w(TAG, "loadSession: No session found in preferences.");
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
        // TODO Check if this works properly once the preferences wont change
        String json = preferences.getString(KEY_PREFERENCES_CURRENT_SESSION, "");
        if (json.equals("")) {
            Log.w(TAG, "getNewSessionID: No key for session ID found in preferences.");
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

    public boolean isRatingFinished() {
        if (trackList != null) {
            for (Recording rec : trackList) {
                if (rec.getRating() == DEFAULT_UNSET_RATING) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
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
                this.trackList,
                this.currentSessionFilePath
        );

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREFERENCES_CURRENT_SESSION, new Gson().toJson(currentSession))
                .putString(KEY_PREFERENCES_STATE, this.state.toString())
                .putString(KEY_PREFERENCES_DATADIR_PATH, this.dataDirPath.toString())
                .putInt(KEY_PREFERENCES_TRACK_POINTER, this.trackPointer)
                .putInt(KEY_PREFERENCES_SAVED_PLAY_PROGRESS, this.savedPlayProgress)
                .apply();

        Log.i(TAG, "saveSession: Saving current session data: " +
                "Session ID = " + session_ID + "\n" +
                "Seed = " + seed + "\n" +
                "Generator Message = " + generatorMessage + "\n" +
                "Save Date = " + saveDate + "\n" +
                "State = " + state + "\n" +
                "Recordings: " + trackList.size() + " files\n" +
                "Current Session File Path: " + currentSessionFilePath);

    }

    public LoadResult loadSession() {
        // Load last saved session - returns LoadResult.NO_SESSION when no previous session is in memory
        String json = preferences.getString(KEY_PREFERENCES_CURRENT_SESSION, "");
        if (json.equals("")) {
            Log.w(TAG, "loadSession: No session found in preferences.");
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
            this.currentSessionFilePath = currentSession.getPath();

            // Re-sort by random index to ensure the same sequence in the player
            List<Recording> currentTrackList = new ArrayList<>(currentSession.getRecordings());
            currentTrackList.sort(Recording.sortByRandomIndex);
            this.trackList = currentTrackList;

            ArrayList<Integer> lastRatings = new ArrayList<>();
            ArrayList<Uri> lastFileList = new ArrayList<>();
            for (Recording r : this.trackList) {
                lastRatings.add(r.getRating());
                lastFileList.add(r.getUri());
            }
            this.ratings = lastRatings;
            Collections.sort(lastFileList); // Ensures the same, alphabetical order after loading
            this.fileList = lastFileList;

            setState(lastState);
            this.dataDirPath = Uri.parse(datadirPath);
            this.trackPointer = trackPointer;
            this.savedPlayProgress = savedPlayProgress;
        } catch (Exception e) {
            e.printStackTrace();
            return LoadResult.CORRUPTED_SESSION;
        }

        String loadLog = "Retrieved session ID: " + this.session_ID + "\n" +
                "Retrieved seed: " + this.seed + "\n" +
                "Retrieved generator message: " + this.generatorMessage + "\n" +
                "Retrieved track list: " + this.trackList.toString() + "\n" +
                "Retrieved ratings: " + this.ratings.toString() + "\n" +
                "Retrieved file list: " + this.fileList.toString() + "\n" +
                "Retrieved state: " + this.state + "\n" +
                "Retrieved local data directory path: " + this.dataDirPath + "\n" +
                "Retrieved track pointer value: " + this.trackPointer + "\n" +
                "Retrieved saved play progress: " + this.savedPlayProgress;
        Log.d(TAG, "loadSession: " + loadLog);
        return LoadResult.OK;
    }

    static void checkResultsDirectory(File resultsDirectory) throws IOException {
        String path = resultsDirectory.getAbsolutePath();
        Pair<Boolean, FileCheckError> checkResult = existsIsDirCanReadCanWrite(resultsDirectory);

        if (!checkResult.first) { // Results directory check failed
            if (checkResult.second.equals(FileCheckError.NOT_EXIST)) {
                Log.w(TAG, "saveResults: Results directory <" + path + "> does not exist - creating");
                if (!resultsDirectory.mkdirs()) {
                    throw new IOException("Results directory <" + path + "> could not be created!");
                } else {
                    checkResultsDirectory(resultsDirectory);
                }
            } else {
                throw new IOException("checkResultsDirectory: Results directory <" + path +
                        "> check failed.\n" + "Reason: " + checkResult.second.toString());
            }
        }
    }

    public static ArrayList<RatingResult> loadResults(Context context) throws Exception {
        Log.i(TAG, "loadResults: Loading results...");
        File resultsDir = new File(context.getFilesDir(), context.getString(R.string.DIRECTORY_NAME_RESULTS));

        checkResultsDirectory(resultsDir); // Throws exception if the directory is not OK

        ArrayList<RatingResult> foundResults = new ArrayList<>();

        // Find any previous save files for this session ID
        File[] resultFiles = resultsDir.listFiles();

        if (resultFiles == null || resultFiles.length == 0) {
            // Don't throw exception, it is not an error (necessarily)
            Log.w(TAG, "loadResults: No result files found in the result directory.");
        } else {
            Log.d(TAG, "loadResults: Found result file list:");
            for (File foundResultFile : resultFiles) {
                Log.d(TAG, "loadResults: Result file: " + foundResultFile.getAbsolutePath());
                Pair<Boolean, FileCheckError> result = existsIsFileCanRead(foundResultFile);

                // Found result file is OK, check if it's text file
                if (result.first) {
                    if (!FilenameUtils.getExtension(foundResultFile.getName()).equalsIgnoreCase("txt")) {
                        Log.e(TAG, "loadResults: Result file " + foundResultFile + " is not a text file.");
                    } else {
                        foundResults.add(new RatingResult(foundResultFile));
                    }
                } else { // Found result file is not OK
                    Log.e(TAG, "loadResults: Result file " + foundResultFile + " error: " + result.second);
                }
            }
            Log.i(TAG, "loadResults: Found " + foundResults.size() + " save files.");
        }

        return foundResults;
    }

    @SuppressLint("SimpleDateFormat")
    public void saveResults(Context context) throws Exception {
        File resultsDir = new File(context.getFilesDir(), context.getString(R.string.DIRECTORY_NAME_RESULTS));
        checkResultsDirectory(resultsDir);

        // Find any previous save files for this session ID
        File[] resultFiles = resultsDir.listFiles();
        ArrayList<File> existingSaveFiles = new ArrayList<>();

        if (resultFiles == null || resultFiles.length == 0) {
            Log.i(TAG, "saveResults: No result files found in the result directory.");
        } else {
            Log.d(TAG, "saveResults: Found result file list:");
            for (File foundResultFile : resultFiles) {
                Log.d(TAG, "saveResults: Result file: " + foundResultFile.getAbsolutePath());
                Pair<Boolean, FileCheckError> result = existsIsFileCanRead(foundResultFile);

                // Found result file is OK, check if it's text file
                if (result.first) {
                    if (!FilenameUtils.getExtension(foundResultFile.getName()).equalsIgnoreCase("txt")) {
                        Log.e(TAG, "saveResults: Result file " + foundResultFile + " is not a text file.");
                    } else {
                        String[] split = foundResultFile.getName().split("_");
                        if (split.length > 2) {
                            String foundSessionId = split[1];
                            if (Integer.parseInt(foundSessionId) == session_ID) {
                                existingSaveFiles.add(foundResultFile);
                            }
                        }
                    }
                } else { // Found result file is not OK
                    Log.e(TAG, "saveResults: Result file " + foundResultFile + " error: " + result.second);
                }
            }
            Log.i(TAG, "saveResults: Found " + existingSaveFiles.size() +
                    " existing save files for session ID " + session_ID);
        }

        // Sort and log the randomized recordings
        this.trackList.sort(Recording.sortAlphabetically);
        Log.d(TAG, "saveResults: Saving! -> Sorted recordings:");
        for (Recording r : this.trackList) {
            Log.d(TAG, "saveResults: " + r.toString());
        }

        // Create new date
        Date currentDateTime = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormatFileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        SimpleDateFormat dateFormatText = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        // Create new file name for the new save
        String newResultFileName = context.getString(R.string.RATING_FILE_NAME,
                this.session_ID, dateFormatFileName.format(currentDateTime));

        // Start writing to the file
        File newResultFile = new File(resultsDir, newResultFileName);
        FileWriter writer = new FileWriter(newResultFile);

        // Write file header
        String header = headerTag + LABEL_SESSION_ID + separator + this.session_ID + "\n" +
                headerTag + LABEL_SEED + separator + this.seed + "\n" +
                headerTag + LABEL_GENERATOR_MESSAGE + separator + this.generatorMessage + "\n" +
                headerTag + LABEL_SAVE_DATE + separator + dateFormatText.format(currentDateTime) + "\n";
        writer.append(header);

        // Row format: id;group;random_number;rating
        for (Recording recording : this.trackList) {
            writer.append(recording.getID()).append(separator)
                    .append(recording.getGroupName()).append(separator)
                    .append(String.valueOf(recording.getRandomIndex())).append(separator)
                    .append(String.valueOf(recording.getRating())).append("\n");
        }
        writer.flush();
        writer.close();

        // This is important for sharing the current session info
        this.currentSessionFilePath = newResultFile.getAbsolutePath();
        saveSession();

        MediaScannerConnection.scanFile(context, new String[]{newResultFile.getPath()}, null,
                (path, uri) -> Log.d(TAG, "onScanCompleted: SCAN OF FILE <" + path + "> completed!"));
        Log.i(TAG, "saveResults: File: <" + newResultFile.getName() + "> written successfully.");

        // Remove old save files for this session ID
        int backupsNumber = context.getResources().getInteger(R.integer.SAVE_FILE_BACKUPS_NUMBER);
        if (existingSaveFiles.size() > backupsNumber) {
            Collections.sort(existingSaveFiles); // Sorts ascending - oldest files are first

            for (int i = 0; i < (existingSaveFiles.size() - backupsNumber); i++) {
                File oldBackup = existingSaveFiles.get(i);

                if (oldBackup.delete()) {
                    Log.d(TAG, "saveResults: Old save file <" + oldBackup.getAbsolutePath() +
                            "> was successfully deleted.");
                } else {
                    Log.w(TAG, "saveResults: Old save file <" + oldBackup.getAbsolutePath() +
                            "> could not be deleted.");
                }
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    public static File saveResultToTemp(Context context, RatingResult ratingResult) throws Exception {
        // Temporary directory
        File tempDir = new File(context.getFilesDir(), context.getString(R.string.DIRECTORY_NAME_TEMP));
        if (!tempDir.exists()) {
            if (!tempDir.mkdirs()) throw new IOException("temp directory could not be created.");
        }

        // Sort recordings
        List<Recording> trackList = new ArrayList<>(ratingResult.getRecordings());
        trackList.sort(Recording.sortAlphabetically);

        // Create new date
        Date currentDateTime = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormatFileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        SimpleDateFormat dateFormatText = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        // Start writing to the file
        String tempResultFileName = context.getString(R.string.RATING_FILE_NAME,
                ratingResult.getSession_ID(), dateFormatFileName.format(currentDateTime));
        File newTempResultFile = new File(tempDir, tempResultFileName);
        FileWriter writer = new FileWriter(newTempResultFile);

        // Write file header
        String header = headerTag + LABEL_SESSION_ID + separator + ratingResult.getSession_ID() + "\n" +
                headerTag + LABEL_SEED + separator + ratingResult.getSeed() + "\n" +
                headerTag + LABEL_GENERATOR_MESSAGE + separator + ratingResult.getGeneratorMessage() + "\n" +
                headerTag + LABEL_SAVE_DATE + separator + dateFormatText.format(currentDateTime) + "\n";
        writer.append(header);

        // Row format: id;group;random_number;rating
        for (Recording recording : trackList) {
            writer.append(recording.getID()).append(separator)
                    .append(recording.getGroupName()).append(separator)
                    .append(String.valueOf(recording.getRandomIndex())).append(separator)
                    .append(String.valueOf(recording.getRating())).append("\n");
        }
        writer.flush();
        writer.close();

        Log.i(TAG, "saveResults: Temporary file: <" + newTempResultFile.getName() +
                "> written successfully.");
        return newTempResultFile;
    }

    /* Utility method for creating randomized file names
    private final static String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static String getRandomName16() {
        StringBuilder sb = new StringBuilder(16);
        Random rnd = new Random();
        for (int i = 0; i < 16; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length() - 1)));
        return sb.toString();
    }
    */
}
