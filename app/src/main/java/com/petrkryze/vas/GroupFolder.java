package com.petrkryze.vas;

import android.net.Uri;
import android.util.Log;

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
    private final ArrayList<URI> fileList = new ArrayList<>();

    public static final Comparator<GroupFolder> groupComparator = (o1, o2) -> Integer.
            compare(o1.getLabel().compareToIgnoreCase(o2.getLabel()), 0);

    public GroupFolder(Uri uri, ArrayList<Uri> uriList) {
        try {
            this.uri = new URI(uri.toString());

            for (Uri u : uriList) this.fileList.add(new URI(u.toString()));
        } catch (URISyntaxException e) {
            Log.e("GroupFolder","Error during conversion from uri to URI.",e);
        }
        this.label = this.getFolderName();
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
        ArrayList<Uri> output = new ArrayList<>();
        for (URI u : fileList) output.add(Uri.parse(u.toString()));
        return output;
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
