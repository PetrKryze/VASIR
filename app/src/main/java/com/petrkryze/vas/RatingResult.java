package com.petrkryze.vas;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.petrkryze.vas.Recording.DEFAULT_UNSET_RATING;

/**
 * Created by Petr on 27.05.2021. Yay!
 */
public class RatingResult implements Serializable {
    private int session_ID;
    private long seed;
    private String generatorMessage;
    private String saveDate;

    private final List<Recording> recordings;

    private final String path;
    private final String rawContent;

    static final String LABEL_SESSION_ID = "Session ID";
    static final String LABEL_SEED = "Randomizer Seed";
    static final String LABEL_GENERATOR_MESSAGE = "Generator Date";
    static final String LABEL_SAVE_DATE = "Save Date";

    public static Comparator<RatingResult> sortChronologically = (o1, o2) ->
            Integer.compare(0, o1.getSaveDate().compareTo(o2.getSaveDate()));

    public RatingResult(int session_ID, long seed, String generatorMessage, String saveDate,
                        List<Recording> recordings) {
        this.session_ID = session_ID;
        this.seed = seed;
        this.generatorMessage = generatorMessage;
        this.saveDate = saveDate;
        this.recordings = recordings;

        this.path = null;
        this.rawContent = null;
    }

    public RatingResult(File resultsTextFile) throws Exception {
        // Takes the already found file on a path in storage and parses its contents for data

        // Sanity checks
        if (!resultsTextFile.exists()) {
            throw new FileNotFoundException("Result text file " + resultsTextFile.getAbsolutePath() +
                    " does not exist!");
        } else if (!resultsTextFile.isFile()) {
            throw new Exception("Result text file " + resultsTextFile.getAbsolutePath() +
                    " is not a file!");
        } else if (!resultsTextFile.canRead()) {
            throw new Exception("Result text file " + resultsTextFile.getAbsolutePath() +
                    " cannot be read!");
        }

        ArrayList<Recording> loadedRecordings = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        BufferedReader br = new BufferedReader(new FileReader(resultsTextFile));
        String line;

        while ((line = br.readLine()) != null && line.length() > 0) {
            sb.append(line).append("\n");
            String[] split = line.split(String.valueOf(RatingManager.separator));
            if (line.charAt(0) == RatingManager.headerTag) { // Header row
                if (split.length < 2) {
                    throw new IOException("Invalid header formatting!");
                }

                if (line.contains(LABEL_SESSION_ID)) {
                    this.session_ID = Integer.parseInt(split[1]);
                } else if (line.contains(LABEL_SEED)) {
                    this.seed = Long.parseLong(split[1]);
                } else if (line.contains(LABEL_GENERATOR_MESSAGE)) {
                    this.generatorMessage = split[1];
                } else if (line.contains(LABEL_SAVE_DATE)) {
                    this.saveDate = split[1];
                } else {
                    throw new IOException("Invalid header line!");
                }
            } else { // Data row
                if (split.length < 4) {
                    throw new IOException("Invalid row formatting!");
                }

                loadedRecordings.add(new Recording(split[0], split[1],
                        Integer.parseInt(split[2]), Integer.parseInt(split[3])));
            }
        }
        br.close();

        this.recordings = loadedRecordings;
        this.path = resultsTextFile.getPath();
        this.rawContent = sb.toString();
    }

    public int getSession_ID() {
        return session_ID;
    }

    public long getSeed() {
        return seed;
    }

    public String getGeneratorMessage() {
        return generatorMessage;
    }

    public String getSaveDate() {
        return saveDate;
    }

    public List<Recording> getRecordings() {
        return recordings;
    }

    public String getPath() {
        return path;
    }

    public String getRawContent() {
        return rawContent;
    }

    public String getRatedFractionString() {
        int Nrated = 0;
        for (Recording r : this.recordings) if (r.getRating() != DEFAULT_UNSET_RATING) Nrated++;

        return Nrated + "/" + this.recordings.size();
    }
}
