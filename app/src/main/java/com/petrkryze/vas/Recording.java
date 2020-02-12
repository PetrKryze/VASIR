package com.petrkryze.vas;

import android.util.Log;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Comparator;

import androidx.annotation.NonNull;

/**
 * Created by Petr on 07.02.2020. Yay!
 */
class Recording {
    private final String path;
    private final String ID;
    private final GroupType group;
    private final int randomized_number;
    private int rating;

    static final int DEFAULT_UNSET_RATING = -1;

    private static final String TAG = "RecordingObject";

    public static Comparator<Recording> recordingComparator = new Comparator<Recording>() {
        @Override
        public int compare(Recording o1, Recording o2) {
            if (o1.getGroupType() == o2.getGroupType()) {
                String replace = o1.getGroupType().toString().split("_")[0];
                int id1 = Integer.parseInt(o1.getID().replace(replace,"").split("_")[0]);
                int id2 = Integer.parseInt(o2.getID().replace(replace,"").split("_")[0]);
                return Integer.compare(id1, id2);
            } else if (o1.getGroupType() == GroupType.HC) {
                return -1;
            } else if (o2.getGroupType() == GroupType.HC) {
                return 1;
            } else if (o1.getGroupType() == GroupType.DBS_OFF) {
                return -1;
            } else if (o1.getGroupType() == GroupType.DBS_130) {
                return 1;
            } else {
                Log.e(TAG, "compare: Sorting error - invalid group types.");
                return 0;
            }
        }
    };

    public enum GroupType {
        DBS_OFF, DBS_130, HC;

        public static GroupType getGroupFromString(String str) {
            switch (str) {
                case "DBS_OFF": return DBS_OFF;
                case "DBS_130": return DBS_130;
                case "HC": return HC;
                default:
                    Log.e(TAG, "getGroupFromString: Invalid parent folder name!");
                    return null;
            }
        }
    }

    public Recording(String path, int randomized_number) {
        this(path, randomized_number, DEFAULT_UNSET_RATING);
    }

    public Recording(String path, int randomized_number, int rating) {
        this.path = path;
        this.randomized_number = randomized_number;

        this.ID = FilenameUtils.getBaseName(path);

        File parent_path = (new File(path)).getParentFile();
        if (parent_path == null) {
            Log.e(TAG, "Recording: Invalid file path! Parent folder not found.");
            this.group = null;
        } else {
            this.group = GroupType.getGroupFromString(parent_path.getName());
        }
        this.rating = rating;
    }

    public String getPath() {
        return path;
    }

    public String getID() {
        return ID;
    }

    public int getRandomized_number() {
        return randomized_number;
    }

    public int getRating() {
        return rating;
    }

    public GroupType getGroupType() {
        return group;
    }

    public void setRating(int rating) {
        if (rating < 1 || rating > 100) {
            Log.e(TAG, "setRating: Invalid rating value! Setting aborted.");
        } else {
            this.rating = rating;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ID:" + this.ID +
                ", Group: " + this.group +
                ", NUM: " + this.randomized_number +
                ", Rating: " + this.rating +
                ", Path: " + this.path;
    }
}
