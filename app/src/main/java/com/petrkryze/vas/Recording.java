package com.petrkryze.vas;

import android.util.Log;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;

import androidx.annotation.NonNull;

/**
 * Created by Petr on 07.02.2020. Yay!
 */
public class Recording implements Serializable {
    private final String path;
    private final String ID;
    private final GroupType group;
    private final int randomized_number;
    private int rating;

    public static final int DEFAULT_UNSET_RATING = -1;

    private static final String TAG = "RecordingObject";

    static final Comparator<Recording> recordingComparator = (o1, o2) -> {
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
    };

    public enum GroupType {
        DBS_OFF, DBS_130, HC;

        static GroupType getGroupFromString(String str) {
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

    Recording(String path, int randomized_number, int rating) {
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

    Recording(String ID, String group_string, int randomized_number, int rating) {
        this.path = null;
        this.ID = ID;
        this.group = GroupType.getGroupFromString(group_string);
        this.randomized_number = randomized_number;
        this.rating = rating;
    }

    String getPath() {
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
        if (rating < 0 || rating > 100) {
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
