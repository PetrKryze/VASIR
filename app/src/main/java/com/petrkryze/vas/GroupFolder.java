package com.petrkryze.vas;

import android.net.Uri;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Created by Petr on 22.06.2021. Yay!
 */
public class GroupFolder implements Serializable {

    private URI uri; // URI instead of Uri to make it serializable
    private String label;
    private final ArrayList<Uri> fileList;

    public static final Comparator<GroupFolder> groupComparator = (o1, o2) -> Integer.
            compare(o1.getLabel().compareToIgnoreCase(o2.getLabel()), 0);

    public GroupFolder(Uri uri, ArrayList<Uri> fileList) {
        try {
            this.uri = new URI(uri.toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        this.label = this.getFolderName();
        this.fileList = fileList;
    }

    public Uri getUri() {
        return Uri.parse(uri.toString());
    }

    public String getFolderName() {
        return FilenameUtils.getName(uri.getPath());
    }

    public String getLabel() {
        return label;
    }

    public ArrayList<Uri> getFileList() {
        return fileList;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getNaudioFiles() {
        return this.fileList.size();
    }

    @NotNull
    @Override
    public String toString() {
        return "Label: " + this.label + ", Path: " + this.uri + ", Nfiles = " + this.fileList.size();
    }
}
