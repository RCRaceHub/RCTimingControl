package dev.monkeypatch.rctiming.domain.csvimport;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads an RC-Timing 2025 style driver CSV (#39). The header row names the columns, which may come
 * in any order and any subset. RC-Timing's format has no quoting, so a name or car make can't
 * contain a comma or a double quote: a row with either is reported, never guessed at.
 *
 * <p>Pure: no Spring, no database. {@link CsvImportService} turns the rows into an import plan.
 */
public final class RcTimingCsvParser {

    static final String NAME = "Name";
    static final String BRCA_NUMBER = "BRCA Number";
    static final String CLUB_NUMBER = "Club Number";
    static final String CLASS_NUMBER = "Class Number";
    static final String CLASS = "Class";
    static final String FORMULA_NUMBER = "Formula Number";
    static final String MEMBER_TYPE_NUMBER = "Member Type Number";
    static final String GRADE = "Grade";
    static final String PT_NO = "PT No";
    static final String PT_NO_2 = "PT No 2";
    static final String JUNIOR = "Junior";
    static final String PAID_STATUS = "Paid Status";
    static final String CAR_MAKE = "Car Make";
    static final String ENTRY_DESC = "Entry Desc";

    /** RCTC's own optional columns for pictures (#699); RC-Timing has none. */
    static final String HEADSHOT_URL = "Headshot URL";
    static final String CAR_PHOTO_URL = "Car Photo URL";
    static final String CAR_COLOURS = "Car Colours";

    /** The extra columns RCTC reads beyond RC-Timing's own. */
    static final List<String> EXTRA_COLUMNS = List.of(HEADSHOT_URL, CAR_PHOTO_URL, CAR_COLOURS);

    /** Every column RC-Timing 2025 defines, in its order. */
    static final List<String> COLUMNS = List.of(NAME, BRCA_NUMBER, CLUB_NUMBER, CLASS_NUMBER, CLASS, FORMULA_NUMBER,
            MEMBER_TYPE_NUMBER, GRADE, PT_NO, PT_NO_2, JUNIOR, PAID_STATUS, CAR_MAKE, ENTRY_DESC);

    /** Shown in the preview for the official's information only; RCTC has nowhere to keep them. */
    static final List<String> INFO_COLUMNS = List.of(GRADE, JUNIOR, MEMBER_TYPE_NUMBER, FORMULA_NUMBER, CAR_MAKE);

    private static final List<String> NUMBER_COLUMNS = List.of(BRCA_NUMBER, CLUB_NUMBER, CLASS_NUMBER, FORMULA_NUMBER,
            MEMBER_TYPE_NUMBER, GRADE, PT_NO, PT_NO_2, JUNIOR, PAID_STATUS);

    private static final Map<String, String> BY_LOWER_CASE = new HashMap<>();

    static {
        COLUMNS.forEach(c -> BY_LOWER_CASE.put(c.toLowerCase(Locale.ROOT), c));
        EXTRA_COLUMNS.forEach(c -> BY_LOWER_CASE.put(c.toLowerCase(Locale.ROOT), c));
    }

    private RcTimingCsvParser() {
    }

    /** What {@code Entry Desc} says to do with a row. */
    public enum Kind {
        /** Book the driver into the meeting. Also used when the file has no {@code Entry Desc}. */
        ENTRY,
        /** Change RC-Timing's member archive only. RCTC has no archive, so the row is skipped. */
        UPDATE
    }

    /**
     * One data row. Numbers RC-Timing writes as {@code 0} for "none" are null here; text columns
     * the file doesn't have are null. {@code carColours} is null when the file has no {@code Car Colours} column or the
     * cell is blank, so a row that names none leaves the entry's colours alone.
     */
    public record Row(int line, String name, Long brcaNumber, Integer classNumber, String className,
                      Long primaryTransponder, Long secondaryTransponder, Kind kind, Map<String, String> info,
                      String headshotUrl, String carPhotoUrl, List<String> carColours) {
    }

    /** The rows that could be read, and every problem found. Any error blocks the import. */
    public record ParsedCsv(List<Row> rows, List<String> errors, List<String> warnings) {
    }

    /**
     * The file's text. RC-Timing and Excel on Windows save in the Windows code page rather than
     * UTF-8, so bytes that aren't valid UTF-8 are read as Windows-1252 and accented names survive.
     */
    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    public static ParsedCsv parse(String content) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Row> rows = new ArrayList<>();

        String text = content == null ? "" : content;
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String[] lines = text.split("\r\n|\r|\n", -1);
        int headerIndex = 0;
        while (headerIndex < lines.length && lines[headerIndex].isBlank()) {
            headerIndex++;
        }
        if (headerIndex == lines.length) {
            errors.add("The file is empty. It needs a header row, such as Name,BRCA Number,Class,PT No");
            return new ParsedCsv(rows, errors, warnings);
        }

        List<String> header = new ArrayList<>();
        Map<String, Integer> columnIndex = new LinkedHashMap<>();
        String[] headerCells = lines[headerIndex].split(",", -1);
        for (int i = 0; i < headerCells.length; i++) {
            String cell = headerCells[i].trim();
            String column = BY_LOWER_CASE.get(cell.toLowerCase(Locale.ROOT));
            header.add(column);
            if (column == null) {
                warnings.add("The column \"" + cell + "\" isn't an RC-Timing column, so it is ignored");
            } else if (columnIndex.putIfAbsent(column, i) != null) {
                errors.add("The header names the column \"" + column + "\" more than once");
            }
        }
        if (!columnIndex.containsKey(NAME)) {
            errors.add("The header has no Name column");
        }
        if (!columnIndex.containsKey(CLASS) && !columnIndex.containsKey(CLASS_NUMBER)) {
            errors.add("The header has no Class or Class Number column");
        }
        if (!errors.isEmpty()) {
            return new ParsedCsv(rows, errors, warnings);
        }

        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int lineNumber = i + 1;
            if (line.indexOf('"') >= 0) {
                errors.add("Line " + lineNumber + " contains a double quote. RC-Timing files can't use double quotes "
                        + "or commas in names or car makes, so remove them and import again");
                continue;
            }
            String[] cells = line.split(",", -1);
            if (cells.length != headerCells.length) {
                errors.add("Line " + lineNumber + " has " + cells.length + " values but the header has "
                        + headerCells.length + ". A name or car make with a comma in it causes this; "
                        + "remove the comma and import again");
                continue;
            }
            Row row = readRow(lineNumber, cells, columnIndex, errors);
            if (row != null) {
                rows.add(row);
            }
        }
        return new ParsedCsv(rows, errors, warnings);
    }

    private static Row readRow(int line, String[] cells, Map<String, Integer> columnIndex, List<String> errors) {
        int errorsBefore = errors.size();
        Map<String, Long> numbers = new HashMap<>();
        for (String column : NUMBER_COLUMNS) {
            String value = cell(cells, columnIndex, column);
            if (value == null || value.isEmpty()) {
                continue;
            }
            try {
                long number = Long.parseLong(value);
                if (number < 0) {
                    throw new NumberFormatException();
                }
                numbers.put(column, number);
            } catch (NumberFormatException e) {
                errors.add("Line " + line + ": " + column + " should be a whole number, not \"" + value + "\"");
            }
        }

        String name = cell(cells, columnIndex, NAME);
        if (name == null || name.isEmpty()) {
            errors.add("Line " + line + " has no name");
        }
        String className = blankToNull(cell(cells, columnIndex, CLASS));
        Long classNumber = nonZero(numbers.get(CLASS_NUMBER));
        if (className == null && classNumber == null && errors.size() == errorsBefore) {
            errors.add("Line " + line + " (" + name + ") has no class");
        }
        if (classNumber != null && classNumber > Integer.MAX_VALUE) {
            errors.add("Line " + line + ": Class Number " + classNumber + " is too large");
        }

        Kind kind = Kind.ENTRY;
        String entryDesc = blankToNull(cell(cells, columnIndex, ENTRY_DESC));
        if (entryDesc != null) {
            switch (entryDesc.toLowerCase(Locale.ROOT)) {
                case "entry" -> kind = Kind.ENTRY;
                case "update" -> kind = Kind.UPDATE;
                default -> errors.add("Line " + line + ": Entry Desc should be entry or update, not \"" + entryDesc + "\"");
            }
        }
        if (errors.size() > errorsBefore) {
            return null;
        }

        Map<String, String> info = new LinkedHashMap<>();
        for (String column : INFO_COLUMNS) {
            String value = CAR_MAKE.equals(column)
                    ? blankToNull(cell(cells, columnIndex, column))
                    : numbers.containsKey(column) && numbers.get(column) != 0 ? String.valueOf(numbers.get(column)) : null;
            if (value != null) {
                info.put(column, value);
            }
        }
        return new Row(line, name.replaceAll("\\s+", " "), nonZero(numbers.get(BRCA_NUMBER)),
                classNumber == null ? null : classNumber.intValue(), className,
                nonZero(numbers.get(PT_NO)), nonZero(numbers.get(PT_NO_2)), kind, info,
                blankToNull(cell(cells, columnIndex, HEADSHOT_URL)), blankToNull(cell(cells, columnIndex, CAR_PHOTO_URL)),
                colours(cell(cells, columnIndex, CAR_COLOURS)));
    }

    /** Colours in a cell: {@code #rrggbb} values separated by spaces, semicolons or bars (a comma would split the cell). */
    private static List<String> colours(String cell) {
        String value = blankToNull(cell);
        return value == null ? null : List.of(value.split("[\\s;|]+"));
    }

    private static String cell(String[] cells, Map<String, Integer> columnIndex, String column) {
        Integer index = columnIndex.get(column);
        return index == null ? null : cells[index].trim();
    }

    private static Long nonZero(Long value) {
        return value == null || value == 0 ? null : value;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
