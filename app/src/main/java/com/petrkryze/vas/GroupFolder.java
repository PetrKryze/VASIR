package com.petrkryze.vas;

import org.apache.commons.io.FilenameUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Created by Petr on 22.06.2021. Yay!
 */
public class GroupFolder implements Serializable {

    private final String path;
    private String label;
    private final ArrayList<String> fileList;

    public static Comparator<GroupFolder> groupComparator = (o1, o2) -> Integer.
            compare(o1.getLabel().compareToIgnoreCase(o2.getLabel()), 0);

    public GroupFolder(String path, ArrayList<String> fileList) {
        this.path = path;
        this.label = this.getFolderName();
        this.fileList = fileList;
    }

    public String getPath() {
        return path;
    }

    public String getFolderName() {
        return FilenameUtils.getName(this.path);
    }

    public String getLabel() {
        return label;
    }

    public ArrayList<String> getFileList() {
        return fileList;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getNaudioFiles() {
        return this.fileList.size();
    }
}
