package com.petrkryze.vas;

import android.content.Context;
import android.util.Log;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.util.ArrayList;

/**
 * Created by Petr on 11.07.2021. Yay!
 */
public class ExcelUtils {
    private static final String TAG = "ExcelUtils";

    private static final int ROW_TITLE = 0;
    private static final int ROW_SESSION_ID = 1;
    private static final int ROW_SEED = 2;
    private static final int ROW_GENERATOR_DATE = 3;
    private static final int ROW_SAVE_DATE = 4;
    private static final int ROW_RECORDING_HEADER = 5;

    private static final int COLUMN_HEADER_LABEL = 1;
    private static final int COLUMN_HEADER_VALUE = 2;

    private static final int COLUMN_RECORDING_ID = 1;
    private static final int COLUMN_RECORDING_GROUP = 2;
    private static final int COLUMN_RECORDING_INDEX = 3;
    private static final int COLUMN_RECORDING_RATING = 4;

    public static File makeExcelFile(Context context,
                                     RatingResult ratingResult) throws IOException {
        // Prepare sheet name
        String sheetName = context.getString(R.string.excel_sheet_name,
                ratingResult.getSession_ID(), ratingResult.getSaveDate().replace(':','-'));

        // Create new workbook and sheet
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);

        // Write "header"
        Row titleRow = sheet.createRow(ROW_TITLE);
        Row sessionIDrow = sheet.createRow(ROW_SESSION_ID);
        Row seedRow = sheet.createRow(ROW_SEED);
        Row generatorDateRow = sheet.createRow(ROW_GENERATOR_DATE);
        Row saveDateRow = sheet.createRow(ROW_SAVE_DATE);

        titleRow.createCell(COLUMN_HEADER_LABEL,CellType.STRING).setCellValue(context.getString(R.string.excel_title_label));
        sessionIDrow.createCell(COLUMN_HEADER_LABEL,CellType.STRING).setCellValue(context.getString(R.string.excel_session_id_label));
        sessionIDrow.createCell(COLUMN_HEADER_VALUE,CellType.NUMERIC).setCellValue(String.valueOf(ratingResult.getSession_ID()));
        seedRow.createCell(COLUMN_HEADER_LABEL,CellType.STRING).setCellValue(context.getString(R.string.excel_seed_label));
        seedRow.createCell(COLUMN_HEADER_VALUE,CellType.NUMERIC).setCellValue(String.valueOf(ratingResult.getSeed()));
        generatorDateRow.createCell(COLUMN_HEADER_LABEL,CellType.STRING).setCellValue(context.getString(R.string.excel_generator_date_label));
        generatorDateRow.createCell(COLUMN_HEADER_VALUE,CellType.NUMERIC).setCellValue(ratingResult.getGeneratorMessage());
        saveDateRow.createCell(COLUMN_HEADER_LABEL,CellType.STRING).setCellValue(context.getString(R.string.excel_save_date_label));
        saveDateRow.createCell(COLUMN_HEADER_VALUE,CellType.NUMERIC).setCellValue(ratingResult.getSaveDate());

        // Write recordings header
        Row recordingsHeaderRow = sheet.createRow(ROW_RECORDING_HEADER);
        recordingsHeaderRow.createCell(COLUMN_RECORDING_ID,CellType.STRING).setCellValue(context.getString(R.string.excel_recording_id_header));
        recordingsHeaderRow.createCell(COLUMN_RECORDING_GROUP,CellType.STRING).setCellValue(context.getString(R.string.excel_recording_group_header));
        recordingsHeaderRow.createCell(COLUMN_RECORDING_INDEX,CellType.STRING).setCellValue(context.getString(R.string.excel_recording_index_header));
        recordingsHeaderRow.createCell(COLUMN_RECORDING_RATING,CellType.STRING).setCellValue(context.getString(R.string.excel_recording_rating_header));

        // Write recordings values
        ArrayList<Recording> recordings = new ArrayList<>(ratingResult.getRecordings());
        for (int i = 0; i < recordings.size(); i++) {
            Row row = sheet.createRow(ROW_RECORDING_HEADER + 1 + i);

            Recording recording = recordings.get(i);
            row.createCell(COLUMN_RECORDING_ID,CellType.STRING).setCellValue(recording.getID());
            row.createCell(COLUMN_RECORDING_GROUP,CellType.STRING).setCellValue(recording.getGroupName());
            row.createCell(COLUMN_RECORDING_INDEX,CellType.NUMERIC).setCellValue(String.valueOf(recording.getRandomIndex()));

            if (recording.getRating() != Recording.DEFAULT_UNSET_RATING) {
                row.createCell(COLUMN_RECORDING_RATING,CellType.NUMERIC).setCellValue(String.valueOf(recording.getRating()));
            }
        }

        // Temporary directory
        File tempDir = new File(context.getFilesDir(), context.getString(R.string.DIRECTORY_NAME_TEMP));
        if (!tempDir.exists()) {
            if (!tempDir.mkdirs()) throw new IOException("temp directory could not be created.");
        }

        // Create new file name for the new save
        String fileName = context.getString(R.string.RATING_EXCEL_FILE_NAME,
                ratingResult.getSession_ID(), ratingResult.getSaveDate().replace(':','-').replace(' ', '_'));

        File newExcelFile = new File(tempDir, fileName);
        // If the file already exists, delete it
        if (newExcelFile.exists()) {
            Log.i(TAG, "makeExcelFile: " + newExcelFile.getAbsolutePath() + " already exists - deleting.");
            if (!newExcelFile.delete()) {
                throw new IOException(newExcelFile.getName() + " could not be deleted!");
            }
        }

        // Create the new excel file
        if (!newExcelFile.createNewFile()) {
            Log.e(TAG, "makeExcelFile: File creation failed!");
            throw new FileSystemException("Failed to create excel file!");
        } else {
            // Write the data to the created file
            FileOutputStream fos = new FileOutputStream(newExcelFile);
            workbook.write(fos);
            fos.close();
        }

        Log.i(TAG, "makeExcelFile: File " + newExcelFile.getName() + " created successfully");
        return newExcelFile;
    }

}
