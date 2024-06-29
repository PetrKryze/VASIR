package com.petrkryze.vas;

import static com.petrkryze.vas.RatingResult.LABEL_GENERATOR_MESSAGE;
import static com.petrkryze.vas.RatingResult.LABEL_SAVE_DATE;
import static com.petrkryze.vas.RatingResult.LABEL_SEED;
import static com.petrkryze.vas.RatingResult.LABEL_SESSION_ID;
import static com.petrkryze.vas.Recording.DEFAULT_UNSET_RATING;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.InvalidObjectException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Created by Petr on 06.08.2021. Yay!
 */
public class Session implements Serializable {

    public enum State {STATE_IN_PROGRESS, STATE_FINISHED, STATE_IDLE}

    private static final String TAG = "Session";
    // Session defining variables
    private final int sessionID;
    private final long seed;
    private final String generatorMessage;
    private List<Recording> recordingList;

    private State state;
    private final URI sourceDataRootUri;
    private String ratingResultFilePath;
    private int trackPointer;
    private int trackPlayProgress;

    public static final char separator = ';';
    public static final char headerTag = '#';

    public Session(int newSessionID, Uri newSourceDataRootUri,
                   ArrayList<GroupFolder> validGroupFolders) throws URISyntaxException {
        this.sessionID = newSessionID;
        this.seed = System.nanoTime();
        Log.i(TAG, "RatingManager: Newly created seed: " + seed);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format =
                new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        this.generatorMessage = format.format(new Date(System.currentTimeMillis()));
        this.ratingResultFilePath = null; // Will be filled when result is saved
        this.sourceDataRootUri = new URI(newSourceDataRootUri.toString());
        this.state = State.STATE_IDLE;
        this.trackPointer = 0;
        this.trackPlayProgress = Player.SAVED_PROGRESS_DEFAULT;

        // GroupFolders and FileLists in them are already sorted alphabetically in previous steps!
        int Nrec = 0; // Get total number of recordings
        for (GroupFolder gf : validGroupFolders) {
            Nrec = Nrec + gf.getFileList().size();
        }

        // Create list of random indexes by shuffling a list of integers from 1 to Nrec
        // Use the newly generated random seed for that
        List<Integer> rNums = RatingManager.linspace(1, Nrec);
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
        this.recordingList = joinedRecordingsList;
    }

    public void resortLists() {
        // Re-sort by random index to ensure the same sequence in the player
        List<Recording> loadedRecordingsList = new ArrayList<>(getRecordingList());
        loadedRecordingsList.sort(Recording.sortByRandomIndex);
        this.recordingList = loadedRecordingsList;
    }

    public void validateSession() throws Exception {
        if (this.sessionID <= 0) {
            throw new InvalidObjectException("Invalid session ID");
        } else if (this.seed <= 0) {
            throw new InvalidObjectException("Invalid session seed");
        } else if (this.generatorMessage == null || this.generatorMessage.isEmpty()) {
            throw new InvalidObjectException("Invalid session generation date");
        } else if (this.recordingList == null) {
            throw new InvalidObjectException("Invalid session recording list");
        } else if (this.state == null) {
            throw new InvalidObjectException("Invalid session state");
        } else if (this.sourceDataRootUri == null) {
            throw new InvalidObjectException("Invalid session source data root directory Uri");
        } else if (this.trackPointer < 0) {
            throw new InvalidObjectException("Invalid session track pointer");
        } else if (this.trackPlayProgress < 0) {
            throw new InvalidObjectException("Invalid session track play progress");
        }
    }

    @SuppressLint("SimpleDateFormat")
    public String generateSaveContent(Date date) {
        SimpleDateFormat dateFormatText = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        StringBuilder sb = new StringBuilder();
        sb.append(headerTag + LABEL_SESSION_ID + separator).append(sessionID).append("\n")
                .append(headerTag + LABEL_SEED + separator).append(seed).append("\n")
                .append(headerTag + LABEL_GENERATOR_MESSAGE + separator).append(generatorMessage).append("\n")
                .append(headerTag + LABEL_SAVE_DATE + separator).append(dateFormatText.format(date)).append("\n");

        // Row format: id;group;random_number;rating
        for (Recording recording : recordingList) {
            sb.append(recording.getID()).append(separator)
                    .append(recording.getGroupName()).append(separator)
                    .append(recording.getRandomIndex()).append(separator)
                    .append(recording.getRating()).append("\n");
        }
        return sb.toString();
    }

    public boolean isRatingFinished() {
        return state == State.STATE_FINISHED && areAllRecordingsRated();
    }

    public void rate(int rating) {
        if (recordingList != null && !recordingList.isEmpty()) {
            recordingList.get(trackPointer).setRating(rating);

            if (areAllRecordingsRated()) this.state = State.STATE_FINISHED;
        }
    }

    public int getRating() {
        if (recordingList != null && !recordingList.isEmpty()) {
            return recordingList.get(trackPointer).getRating();
        } else {
            return DEFAULT_UNSET_RATING;
        }
    }

    private boolean areAllRecordingsRated() {
        if (recordingList != null && !recordingList.isEmpty()) {
            for (Recording rec : recordingList) {
                if (rec.getRating() == DEFAULT_UNSET_RATING) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
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
        this.trackPointer = recordingList.size() - 1;
    }

    public int getSessionID() {
        return sessionID;
    }

    public Recording getRecording(int index) {
        if (recordingList == null || index < 0 || index > getTrackN()) {
            return null;
        } else {
            return recordingList.get(index);
        }
    }

    public List<Recording> getRecordingList() {
        return recordingList;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getTrackPlayProgress() {
        return trackPlayProgress;
    }

    public void setTrackPlayProgress(int savedPlayProgress) {
        this.trackPlayProgress = savedPlayProgress;
    }

    public long getSeed() {
        return seed;
    }

    public String getGeneratorMessage() {
        return generatorMessage;
    }

    public int getTrackN() {
        return recordingList.size();
    }

    public ArrayList<Uri> getRecordingUriList() {
        ArrayList<Uri> uris = new ArrayList<>();
        for (Recording r : recordingList) uris.add(r.getUri());
        Collections.sort(uris);
        return uris;
    }

    public String getRatingResultFilePath() {
        return ratingResultFilePath;
    }

    public void setRatingResultFilePath(String ratingResultFileUri) {
        this.ratingResultFilePath = ratingResultFileUri;
    }

    public Uri getSourceDataRootUri() {
        return Uri.parse(sourceDataRootUri.toString());
    }

    @NotNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Session ID: ").append(this.sessionID).append("\n")
                .append("Session Seed: ").append(this.seed).append("\n")
                .append("Session Generation Date: ").append(this.generatorMessage).append("\n");
        for (Recording recording : this.recordingList) sb.append(recording.toString()).append("\n");
        sb.append("Session State: ").append(this.state).append("\n")
                .append("Session Saved Rating File URI: ").append(this.sourceDataRootUri).append("\n")
                .append("Session Track Pointer: ").append(this.trackPointer).append("\n")
                .append("Session Track Play Progress: ").append(this.trackPlayProgress);
        return sb.toString();
    }
}
