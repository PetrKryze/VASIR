package com.petrkryze.vas;

import android.util.Log;

import org.apache.commons.io.FilenameUtils;

import java.io.Serializable;
import java.util.Comparator;

import androidx.annotation.NonNull;

/**
 * Created by Petr on 07.02.2020. Yay!
 */
public class Recording implements Serializable {
    private static final String TAG = "RecordingObject";

    private final String path;
    private final String ID;
    private final int randomIndex;
    private final String groupName;
    private int rating;

    public static final int DEFAULT_UNSET_RATING = -1;

    public static final Comparator<Recording> sortAlphabetically = (o1, o2) -> {
        int compareGroup = o1.getGroupName().compareToIgnoreCase(o2.getGroupName());

        if (compareGroup < 0) {
            return -1;
        } else if (compareGroup > 0) {
            return 1;
        } else { // Groups are the same
            int compareID = o1.getID().compareToIgnoreCase(o2.getID());
            return Integer.compare(compareID, 0);
        }
    };

    public static final Comparator<Recording> sortByID = (o1, o2) -> {
        int compareID = o1.getID().compareToIgnoreCase(o2.getID());

        if (compareID < 0) {
            return -1;
        } else if (compareID > 0) {
            return 1;
        } else {
            return Integer.compare(o1.getRandomIndex(), o2.getRandomIndex());
        }
    };

    public static final Comparator<Recording> sortByGroup = (o1, o2) -> {
        int compareGroup = o1.getGroupName().compareToIgnoreCase(o2.getGroupName());

        if (compareGroup < 0) {
            return -1;
        } else if (compareGroup > 0) {
            return 1;
        } else { // Groups are the same
            int compareID = o1.getID().compareToIgnoreCase(o2.getID());

            if (compareID < 0) {
                return -1;
            } else if (compareID > 0) {
                return 1;
            } else {
                return Integer.compare(o1.getRandomIndex(), o2.getRandomIndex());
            }
        }
    };

    public static final Comparator<Recording> sortByRandomIndex = (o1, o2) -> Integer.
            compare(o1.getRandomIndex(), o2.getRandomIndex());

    public static final Comparator<Recording> sortByRating = (o1, o2) -> {
        if (o1.getRating() == DEFAULT_UNSET_RATING && o2.getRating() == DEFAULT_UNSET_RATING) {
            int compareID = o1.getID().compareToIgnoreCase(o2.getID());

            if (compareID < 0) {
                return -1;
            } else if (compareID > 0) {
                return 1;
            } else {
                return Integer.compare(o1.getRandomIndex(), o2.getRandomIndex());
            }
        } else if (o1.getRating() == DEFAULT_UNSET_RATING) {
            return -1;
        } else if (o2.getRating() == DEFAULT_UNSET_RATING) {
            return 1;
        } else if (o1.getRating() == o2.getRating()) {
            int compareID = o1.getID().compareToIgnoreCase(o2.getID());

            if (compareID < 0) {
                return -1;
            } else if (compareID > 0) {
                return 1;
            } else {
                return Integer.compare(o1.getRandomIndex(), o2.getRandomIndex());
            }
        } else {
            return Integer.compare(o1.getRating(), o2.getRating());
        }
    };


    Recording(String path, String groupName, int randomIndex, int rating) {
        this.path = path;
        this.randomIndex = randomIndex;
        this.ID = FilenameUtils.getBaseName(path);
        this.groupName = groupName;
        this.rating = rating;
    }

    String getPath() {
        return path;
    }

    public String getID() {
        return ID;
    }

    public int getRandomIndex() {
        return randomIndex;
    }

    public String getGroupName() { return groupName; }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        if (rating < 0 || rating > 100) {
            Log.e(TAG, "setRating: Invalid rating value! Setting aborted.");
        } else {
            this.rating = rating;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "\nID:" + this.ID +
                ", Group name: " + this.groupName +
                ", Index: " + this.randomIndex +
                ", Rating: " + this.rating +
                ", Path: " + this.path;
    }
}
