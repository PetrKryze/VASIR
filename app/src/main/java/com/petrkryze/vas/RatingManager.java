package com.petrkryze.vas;

import static com.petrkryze.vas.RatingResult.LABEL_GENERATOR_MESSAGE;
import static com.petrkryze.vas.RatingResult.LABEL_SAVE_DATE;
import static com.petrkryze.vas.RatingResult.LABEL_SEED;
import static com.petrkryze.vas.RatingResult.LABEL_SESSION_ID;
import static com.petrkryze.vas.Session.State.STATE_IDLE;
import static com.petrkryze.vas.Session.State.STATE_IN_PROGRESS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by Petr on 09.02.2020. Yay!
 */
public class RatingManager {

    public enum LoadResult {OK, NO_SESSION, CORRUPTED_SESSION}
    public enum FileCheckError {NOT_EXIST, NOT_DIRECTORY, NOT_FILE, NOT_READABLE, NOT_WRITABLE,
        NO_FILES, MISSING_FILES, OK}

    public interface DirectoryCheckCallback {
        void onError(DirectoryCheckError errorInfo);
        void onSuccess();
        void onGroupCheckNeeded(Uri rootDirUri, ArrayList<GroupFolder> groupFolders);
    }

    private final SharedPreferences preferences;

    private static final String KEY_PREFERENCES_CURRENT_SESSION = "current_session";

    public static final char separator = ';';
    public static final char headerTag = '#';
    private static final String[] SUPPORTED_EXTENSIONS = {"wav","mp3","aac","flac","m4a","ogg","opus"};

    private static final String TAG = "RatingManager";

    public RatingManager(@NonNull Context context) {
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public Session makeNewSession(Uri newRootUri, ArrayList<GroupFolder> newGroupFolders) throws URISyntaxException {
        return new Session(getNewSessionID(), newRootUri, newGroupFolders);
    }

    private void checkDataDirectoryPath(Context context, Uri rootDataUri, @Nullable Session session,
                                         DirectoryCheckCallback callback) {
        DocumentFile rootDataDir = DocumentFile.fromTreeUri(context, rootDataUri);
        assert rootDataDir != null;

        DocumentFile[] documentFiles = rootDataDir.listFiles();
        for (DocumentFile document : documentFiles) { // Logging only
            Log.d(TAG, "checkDataDirectoryPath: File: " + document.getName());
        }

        // Basic checks for root directory
        Pair<Boolean, FileCheckError> rootCheck = existsIsDirCanRead(rootDataDir);
        if (!rootCheck.first) {
            callback.onError(new DirectoryCheckError(rootDataDir.getName(), rootCheck.second, null, null));
            return;
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
            callback.onError(new DirectoryCheckError(rootDataDir.getName(), FileCheckError.NO_FILES, null, null));
        } else {
            for (GroupFolder gf : groupFolders) { // Just logging
                Log.d(TAG, "checkDataDirectoryPath: Group folder: " +
                        gf.getFolderName() + ", Files: " + gf.getNaudioFiles() + " - PASSED");
            }

            if (session != null) { // Previous session found, check consistency
                ArrayList<Uri> lastSessionFiles = new ArrayList<>(session.getRecordingUriList());

                for (Uri filePath : session.getRecordingUriList()) {
                    for (GroupFolder gf : groupFolders) {
                        if (gf.getFileList().contains(filePath)) {
                            lastSessionFiles.remove(filePath);
                        }
                    }
                }

                // All files from saved file list are accounted for in the storage
                if (lastSessionFiles.isEmpty()) {
                    // Use callback to signal that the previous session data is checked and ok
                    // Sets the "in progress" state if the session is not finished
                    if (session.getState() == STATE_IDLE) session.setState(STATE_IN_PROGRESS);
                    callback.onSuccess();
                } else { // Some files are missing
                    StringBuilder sb = new StringBuilder();
                    for (Uri uri : lastSessionFiles) {
                        sb.append(uri).append("\n");
                    }
                    callback.onError(new DirectoryCheckError(rootDataDir.getName(), FileCheckError.MISSING_FILES,
                            lastSessionFiles.size(), sb.toString()));
                }
            } else { // No previous session found, fire GroupFolder user check fragment
                groupFolders.sort(GroupFolder.groupComparator);
                // Callback to start the user Group check, make new session after confirmation
                callback.onGroupCheckNeeded(rootDataUri, groupFolders);
            }
        }
    }

    // For saved sessions from loaded Session object
    public void checkSavedDataDirectoryPath(Context context, Session loadedSession, DirectoryCheckCallback callback) {
        checkDataDirectoryPath(context, loadedSession.getSourceDataRootUri(), loadedSession, callback);
    }

    // For new sessions from user selected directory
    public void checkNewDataDirectoryPath(Context context, Uri rootDataUri, DirectoryCheckCallback callback) {
        checkDataDirectoryPath(context, rootDataUri, null, callback);
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
        return foundFolders;
    }

    private ArrayList<DocumentFile> getFileList(DocumentFile path) {
        return getFileList(path, true);
    }

    // Extracts audio files recursively from folder
    private ArrayList<DocumentFile> getFileList(DocumentFile path, boolean goDeep) {
        ArrayList<DocumentFile> foundFiles = new ArrayList<>();

        DocumentFile[] files = path.listFiles();
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
        return foundFiles;
    }

    private ArrayList<Uri> fileListToUriList(ArrayList<DocumentFile> fileList) {
        ArrayList<Uri> paths = new ArrayList<>();
        for (DocumentFile f : fileList) {
            paths.add(f.getUri());
        }
        return paths;
    }

    @SuppressWarnings("SameParameterValue")
    public static List<Integer> linspace(int start, int end) {
        if (end < start) {
            Log.e(TAG, "linspace: Invalid input parameters!");
            return new ArrayList<>();
        } else {
            List<Integer> list = new ArrayList<>();
            for (int i = start; i <= end; i++) list.add(i);
            return list;
        }
    }

    public static final String GET_SESSION_INFO_LOAD_RESULT_KEY = "LoadResult";
    public static final String SESSION_INFO_LOADED_SESSION = "session";

    public static Bundle getSessionInfo(Context context) {
        Bundle out = new Bundle();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Load last saved session - returns LoadResult.NO_SESSION when no previous session is in memory
        String json = preferences.getString(KEY_PREFERENCES_CURRENT_SESSION, "");
        if (json.isEmpty()) {
            Log.w(TAG, "loadSession: No session found in preferences.");
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.NO_SESSION);
            return out;
        }

        try {
            Session loadedSession = new Gson().fromJson(json, new TypeToken<Session>(){}.getType());

            out.putSerializable(SESSION_INFO_LOADED_SESSION, loadedSession);
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.OK);
        } catch (Exception e) {
            Log.e(TAG,"Session loading from JSON file was not successful.",e);
            out.putSerializable(GET_SESSION_INFO_LOAD_RESULT_KEY, LoadResult.CORRUPTED_SESSION);
            return out;
        }

        return out;
    }

    private int getNewSessionID() {
        String json = preferences.getString(KEY_PREFERENCES_CURRENT_SESSION, "");
        if (json.isEmpty()) {
            Log.w(TAG, "loadSession: No session found in preferences.");
            return 1;
        } else {
            Session loadedSession = new Gson().fromJson(json, new TypeToken<Session>(){}.getType());

            if (loadedSession != null) {
                int lastSessionID = loadedSession.getSessionID();
                Log.i(TAG, "getNewSessionID: NEW SESSION ID = " + (lastSessionID + 1));
                return lastSessionID + 1;
            } else {
                Log.w(TAG, "getNewSessionID: Session failed to load from JSON String.");
                return 1;
            }
        }
    }

    public void saveSession(Session session) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREFERENCES_CURRENT_SESSION, new Gson().toJson(session))
                .apply();

        Log.i(TAG, "saveSession: Saving Session >\n" + session.toString());
    }

    public Pair<LoadResult, Session> loadSession() {
        // Load last saved session - returns LoadResult.NO_SESSION when no previous session is in memory
        String json = preferences.getString(KEY_PREFERENCES_CURRENT_SESSION, "");
        if (json.isEmpty()) {
            Log.w(TAG, "loadSession: No session found in preferences.");
            return Pair.create(LoadResult.NO_SESSION, null);
        }
        try {
            Session loadedSession = new Gson().fromJson(json, new TypeToken<Session>(){}.getType());
            if (loadedSession == null) throw new NullPointerException("Session failed to load from JSON String.");

            loadedSession.resortLists(); // Just to be sure
            loadedSession.validateSession();

            Log.d(TAG, "loadSession: Loaded Session >\n" + loadedSession);
            return Pair.create(LoadResult.OK, loadedSession);
        } catch (Exception e) {
            Log.e(TAG,"Session loading from JSON file was not successful.",e);
            return Pair.create(LoadResult.CORRUPTED_SESSION, null);
        }
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
                        "> check failed.\n" + "Reason: " + checkResult.second);
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
    public void saveResults(Context context, Session session) throws Exception {
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
                            if (Integer.parseInt(foundSessionId) == session.getSessionID()) {
                                existingSaveFiles.add(foundResultFile);
                            }
                        }
                    }
                } else { // Found result file is not OK
                    Log.e(TAG, "saveResults: Result file " + foundResultFile + " error: " + result.second);
                }
            }
            Log.i(TAG, "saveResults: Found " + existingSaveFiles.size() +
                    " existing save files for session ID " + session.getSessionID());
        }

        // Sort and log the randomized recordings
        List<Recording> toLog = new ArrayList<>(session.getRecordingList());
        toLog.sort(Recording.sortAlphabetically);
        Log.d(TAG, "saveResults: Saving! -> Sorted recordings:");
        for (Recording recording : toLog) {
            Log.d(TAG, "saveResults: " + recording.toString());
        }

        // Create new date
        Date currentDateTime = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormatFileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

        // Create new file name for the new save
        String newResultFileName = context.getString(R.string.RATING_FILE_NAME,
                session.getSessionID(), dateFormatFileName.format(currentDateTime));

        // Start writing to the file
        File newResultFile = new File(resultsDir, newResultFileName);
        FileWriter writer = new FileWriter(newResultFile);

        // Write file content
        writer.write(session.generateSaveContent(currentDateTime));
        writer.flush();
        writer.close();

        // This is important for sharing the current session info
        session.setRatingResultFilePath(newResultFile.getAbsolutePath());
        saveSession(session);

        MediaScannerConnection.scanFile(context, new String[]{newResultFile.getPath()}, null,
                (path, uri) -> Log.d(TAG, "onScanCompleted: SCAN OF FILE <" + path + "> completed!"));
        Log.i(TAG, "saveResults: File: <" + newResultFile.getName() + "> written successfully.");

        // Remove old save files for this session ID
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int backupsNumber = Integer.parseInt(preferences.getString(context.getString(R.string.SETTING_KEY_BACKUPS_NUMBER),
                String.valueOf(context.getResources().getInteger(R.integer.SAVE_FILE_BACKUPS_NUMBER_DEFAULT))));
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
        List<Recording> trackList = new ArrayList<>(ratingResult.getRecordingList());
        trackList.sort(Recording.sortAlphabetically);

        // Create new date
        Date currentDateTime = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormatFileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        SimpleDateFormat dateFormatText = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        // Start writing to the file
        String tempResultFileName = context.getString(R.string.RATING_FILE_NAME,
                ratingResult.getSessionID(), dateFormatFileName.format(currentDateTime));
        File newTempResultFile = new File(tempDir, tempResultFileName);
        FileWriter writer = new FileWriter(newTempResultFile);

        // Write file header
        String header = headerTag + LABEL_SESSION_ID + separator + ratingResult.getSessionID() + "\n" +
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

    public static class DirectoryCheckError {
        public final String dirName;
        public final FileCheckError errorType;
        public @Nullable
        final Integer missingCnt;
        public @Nullable
        final String missingList;

        public DirectoryCheckError(String dirName, FileCheckError errorType,
                                   @Nullable Integer missingCnt, @Nullable String missingList) {
            this.dirName = dirName;
            this.errorType = errorType;
            this.missingCnt = missingCnt;
            this.missingList = missingList;
        }
    }
}
