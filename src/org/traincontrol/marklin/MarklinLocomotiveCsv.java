package org.traincontrol.marklin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.LocomotiveNotes;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.model.ViewListener;

/**
 * Reads the file `MarklinControlStation.exportLocsToCSV` writes and adds its locomotives to a database (FR-049).
 *
 * The file is read whole into a plan first and nothing changes until `apply`.  A new name becomes a locomotive through
 * the model's own `new...Locomotive`; an existing name keeps its decoder and address and only has empty fields filled
 * and its runtime raised to the file's if that is larger.  A row that cannot be read is skipped and its line kept.
 */
public final class MarklinLocomotiveCsv
{
    private static final Pattern DECODER = Pattern.compile("\\b(MFX|DCC|MM2)\\b", Pattern.CASE_INSENSITIVE);

    /** One readable row of the file. */
    public static final class Row
    {
        public final int line;
        public final String name;
        /** Null when the decoder column names none of MFX, DCC or MM2 (a multi-unit cannot be built from a file). */
        public final Locomotive.decoderType type;
        public final int address;
        public final int preferredSpeed;
        public final long runtimeMs;
        public final int startYear;
        public final int endYear;
        public final String railway;
        public final String notes;

        Row(int line, String name, Locomotive.decoderType type, int address, int preferredSpeed, long runtimeMs,
            int startYear, int endYear, String railway, String notes)
        {
            this.line = line;
            this.name = name;
            this.type = type;
            this.address = address;
            this.preferredSpeed = preferredSpeed;
            this.runtimeMs = runtimeMs;
            this.startYear = startYear;
            this.endYear = endYear;
            this.railway = railway;
            this.notes = notes;
        }
    }

    /** What a row does to a database. */
    public enum Fate { NEW, MERGE, SKIP }

    /** What `apply` did. */
    public static final class Result
    {
        public int added;
        public int merged;
        public final List<Integer> skippedLines = new ArrayList<>();
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Integer> unreadableLines = new ArrayList<>();
    private boolean isLocomotiveFile = true;

    private MarklinLocomotiveCsv()
    {
    }

    /** Whether the text has the export's header; false means nothing of it is read. */
    public boolean isLocomotiveFile()
    {
        return this.isLocomotiveFile;
    }

    /** The readable rows, in file order. */
    public List<Row> getRows()
    {
        return this.rows;
    }

    /** Whether this row would add, merge or be skipped against the model as it is now. */
    public static Fate fateOf(Row row, ViewListener model)
    {
        if (model.getLocByName(row.name) != null) return Fate.MERGE;

        if (row.type == null || !MarklinLocomotive.validateNewAddress(row.type, row.address)) return Fate.SKIP;

        return Fate.NEW;
    }

    /** The names in the file that the model already has. */
    public List<String> existingNames(ViewListener model)
    {
        List<String> names = new ArrayList<>();

        for (Row row : this.rows)
        {
            if (fateOf(row, model) == Fate.MERGE) names.add(row.name);
        }

        return names;
    }

    /** The lines that will be skipped against the model as it is now, in file order. */
    public List<Integer> skippedLines(ViewListener model)
    {
        List<Integer> lines = new ArrayList<>(this.unreadableLines);

        for (Row row : this.rows)
        {
            if (fateOf(row, model) == Fate.SKIP) lines.add(row.line);
        }

        java.util.Collections.sort(lines);

        return lines;
    }

    /** How many rows would add a locomotive. */
    public int countNew(ViewListener model)
    {
        int n = 0;

        for (Row row : this.rows)
        {
            if (fateOf(row, model) == Fate.NEW) n++;
        }

        return n;
    }

    /** Adds and merges; the single place that changes the model. */
    public Result apply(ViewListener model)
    {
        Result result = new Result();

        result.skippedLines.addAll(this.unreadableLines);

        for (Row row : this.rows)
        {
            Fate fate = fateOf(row, model);

            if (fate == Fate.SKIP)
            {
                result.skippedLines.add(row.line);
            }
            else if (fate == Fate.MERGE)
            {
                merge(model.getLocByName(row.name), row);
                result.merged++;
            }
            else
            {
                Locomotive loc = row.type == Locomotive.decoderType.MFX ? model.newMFXLocomotive(row.name, row.address)
                    : row.type == Locomotive.decoderType.DCC ? model.newDCCLocomotive(row.name, row.address)
                    : model.newMM2Locomotive(row.name, row.address);

                merge(loc, row);
                result.added++;
            }
        }

        java.util.Collections.sort(result.skippedLines);

        return result;
    }

    /** Fills what is empty on `loc` from the row and raises the runtime; decoder and address are never touched. */
    private static void merge(Locomotive loc, Row row)
    {
        if (loc.getPreferredSpeed() == 0 && row.preferredSpeed > 0) loc.setPreferredSpeed(row.preferredSpeed);

        LocomotiveNotes have = loc.getStructuredNotes();

        int start = have.getStartYear() == 0 ? row.startYear : have.getStartYear();
        int end = have.getEndYear() == 0 ? row.endYear : have.getEndYear();
        String railway = have.getRailway().isEmpty() ? row.railway : have.getRailway();
        String notes = have.getNotes().isEmpty() ? row.notes : have.getNotes();

        if (start != have.getStartYear() || end != have.getEndYear() || !railway.equals(have.getRailway())
            || !notes.equals(have.getNotes()))
        {
            loc.setStructuredNotes(start, end, railway, notes);
        }

        loc.raiseTotalRuntimeTo(row.runtimeMs);
    }

    // ------------------------------------------------------------------------------------------------ reading

    /** Reads the text of an export; never throws for a bad file. */
    public static MarklinLocomotiveCsv read(String text)
    {
        MarklinLocomotiveCsv csv = new MarklinLocomotiveCsv();

        List<Record> records = split(text);

        if (records.isEmpty())
        {
            csv.isLocomotiveFile = false;
            return csv;
        }

        Map<String, Integer> column = new HashMap<>();
        List<String> header = records.get(0).fields;

        for (int i = 0; i < header.size(); i++)
        {
            String title = header.get(i).replace("﻿", "").trim().toLowerCase(Locale.ROOT);

            if (!column.containsKey(title)) column.put(title, i);
        }

        if (records.get(0).bad || !column.containsKey("name"))
        {
            csv.isLocomotiveFile = false;
            return csv;
        }

        Set<String> seen = new HashSet<>();

        for (int r = 1; r < records.size(); r++)
        {
            Record record = records.get(r);
            Row row = record.bad || record.fields.size() != header.size() ? null : toRow(record, column);

            if (row == null || !seen.add(row.name))
            {
                csv.unreadableLines.add(record.line);
            }
            else
            {
                csv.rows.add(row);
            }
        }

        return csv;
    }

    private static Row toRow(Record record, Map<String, Integer> column)
    {
        String name = field(record, column, "name");

        if (name.trim().isEmpty() || name.length() >= TrainControlUI.MAX_LOC_NAME_DATABASE) return null;

        long address = number(field(record, column, "address"));
        long speed = number(field(record, column, "preferredspeed"));
        long start = number(field(record, column, "start year"));
        long end = number(field(record, column, "end year"));
        long runtime = runtimeMs(field(record, column, "totalruntime"));

        if (address < 0 || address > Integer.MAX_VALUE || speed < 0 || speed > 100 || start < 0 || start > Integer.MAX_VALUE
            || end < 0 || end > Integer.MAX_VALUE || runtime < 0)
        {
            return null;
        }

        Matcher decoder = DECODER.matcher(field(record, column, "decodertype"));

        Locomotive.decoderType type = decoder.find()
            ? Locomotive.decoderType.valueOf(decoder.group(1).toUpperCase(Locale.ROOT)) : null;

        return new Row(record.line, name, type, (int) address, (int) speed, runtime, (int) start, (int) end,
            field(record, column, "railwayname"), field(record, column, "notes"));
    }

    private static String field(Record record, Map<String, Integer> column, String title)
    {
        Integer at = column.get(title);

        return at == null ? "" : record.fields.get(at);
    }

    /** A whole number in any script's digits; empty is 0, anything else unreadable is -1. */
    private static long number(String text)
    {
        String s = text.trim();

        if (s.isEmpty()) return 0;

        if (s.length() > 10) return -1;

        long n = 0;

        for (int i = 0; i < s.length(); i++)
        {
            int digit = Character.digit(s.charAt(i), 10);

            if (digit < 0) return -1;

            n = n * 10 + digit;
        }

        return n;
    }

    /** "H:MM:SS" as the export writes it, in milliseconds; empty is 0, unreadable is -1. */
    private static long runtimeMs(String text)
    {
        String s = text.trim();

        if (s.isEmpty()) return 0;

        String[] parts = s.split(":", -1);

        if (parts.length != 3) return -1;

        long h = number(parts[0]);
        long m = number(parts[1]);
        long sec = number(parts[2]);

        if (h < 0 || m < 0 || sec < 0 || h > 100000000L) return -1;

        return ((h * 60 + m) * 60 + sec) * 1000;
    }

    /** One CSV record: its fields, the line it starts on, and whether its quoting was broken. */
    private static final class Record
    {
        final int line;
        final List<String> fields = new ArrayList<>();
        boolean bad;

        Record(int line)
        {
            this.line = line;
        }
    }

    /**
     * Splits CSV text the way `Util.escapeCsv` writes it: fields with a comma, quote or line feed are quoted with ""
     * for a quote; a row ends at a line feed outside quotes (a carriage return before it is dropped); blank lines are
     * ignored.
     */
    private static List<Record> split(String text)
    {
        List<Record> records = new ArrayList<>();

        StringBuilder field = new StringBuilder();
        Record record = null;
        boolean quoted = false;      // inside a quoted field
        boolean wasQuoted = false;   // the current field began with a quote
        boolean closed = false;      // right after the closing quote
        int line = 1;

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);

            if (record == null) record = new Record(line);

            if (quoted)
            {
                if (c == '"')
                {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"')
                    {
                        field.append('"');
                        i++;
                    }
                    else
                    {
                        quoted = false;
                        closed = true;
                    }
                }
                else
                {
                    if (c == '\n') line++;

                    field.append(c);
                }

                continue;
            }

            if (c == ',')
            {
                record.fields.add(field.toString());
                field.setLength(0);
                wasQuoted = false;
                closed = false;
            }
            else if (c == '\n')
            {
                if (!wasQuoted && field.length() > 0 && field.charAt(field.length() - 1) == '\r')
                {
                    field.setLength(field.length() - 1);
                }

                record.fields.add(field.toString());
                field.setLength(0);

                if (!(record.fields.size() == 1 && record.fields.get(0).isEmpty() && !wasQuoted)) records.add(record);

                record = null;
                wasQuoted = false;
                closed = false;
                line++;
            }
            else if (c == '"' && field.length() == 0 && !wasQuoted)
            {
                quoted = true;
                wasQuoted = true;
            }
            else if (closed)
            {
                if (c != '\r') record.bad = true;
            }
            else
            {
                field.append(c);
            }
        }

        if (record != null)
        {
            if (quoted) record.bad = true;

            record.fields.add(field.toString());

            if (!(record.fields.size() == 1 && record.fields.get(0).isEmpty() && !wasQuoted)) records.add(record);
        }

        return records;
    }
}
