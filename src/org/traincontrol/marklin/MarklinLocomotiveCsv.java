package org.traincontrol.marklin;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.LocomotiveNotes;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.model.ViewListener;

/**
 * Reads the file `MarklinControlStation.exportLocsToCSV` writes and adds its locomotives to a database (FR-049).
 *
 * The file is read whole into a plan first and nothing changes until `apply`.  A new name becomes a locomotive through
 * the model's own `new...Locomotive`; an existing name has empty fields filled, takes the file's address and decoder
 * (never into a multi-unit's trouble, never for a train with a speed), and takes the file's runtime only if the file's
 * last run day is later than its own.  A row that cannot be used is skipped, with its line and why.
 */
public final class MarklinLocomotiveCsv
{
    private static final Pattern DECODER = Pattern.compile("\\b(MFX|DCC|MM2)\\b", Pattern.CASE_INSENSITIVE);

    /** The most hours of running a row may claim (a bad total can never come down again). */
    private static final long MOST_HOURS = 100000;

    /** What the decoder column says. */
    public enum Kind { PLAIN, LINKED, MULTI, UNKNOWN }

    /** What the last run column says. */
    public enum Day { NONE, OK, BAD }

    /** Why a row is skipped. */
    public enum Why { UNREADABLE, NAME, MULTI_UNIT, ADDRESS }

    /** What a row does to a database. */
    public enum Fate { NEW, MERGE, SKIP }

    /** One readable row of the file, filled in by the reader. */
    public static final class Row
    {
        public int line;
        public String name;
        public Kind kind;
        /** The decoder of a plain row (MFX, DCC or MM2), otherwise null. */
        public Locomotive.decoderType type;
        public int address;
        public int preferredSpeed;
        public long runtimeMs;
        public int startYear;
        public int endYear;
        public String railway;
        public String notes;
        /** The day the file's runtime was last added to (yyyy-MM-dd), when `day` is OK. */
        public String lastRun;
        public Day day;
    }

    /** Locomotives that would stand on one decoder address. */
    public static final class Shared
    {
        public String address;
        public List<String> names = new ArrayList<>();
    }

    /** What `apply` did. */
    public static final class Result
    {
        public int added;
        public int merged;
        /** New locomotives that came in with no runtime because the file gave no last run day for it. */
        public int addedWithoutRuntime;
        /** Rows whose last run day could not be used, so their runtime was left alone or not given. */
        public int badLastRun;
        /** Existing locomotives whose address or decoder the file changed. */
        public final List<String> readdressed = new ArrayList<>();
        /** Address changes left out because they would break a multi-unit or clash with a train on the layout. */
        public int readdressKept;
        /** Existing locomotives that kept their address because the file's is not valid for its decoder. */
        public int addressInvalid;
        /** Multi-unit and linked rows left out. */
        public int leftOutMultiUnit;
        public final Map<Integer, Why> skipped = new TreeMap<>();
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<Integer, Why> unreadable = new TreeMap<>();
    private boolean isLocomotiveFile = true;

    private MarklinLocomotiveCsv()
    {
    }

    /** The text of a file's bytes: UTF-8 when it is, else Windows-1252, which is what Excel saves without asking. */
    public static String decode(byte[] bytes)
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException notUtf8)
        {
            return new String(bytes, Charset.forName("windows-1252"));
        }
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

    /** A name routes can store, and one line: the rule every other door asks of a new name. */
    private static boolean nameUsable(String name)
    {
        return RouteCommand.isNameUsable(name) && name.indexOf('\n') < 0 && name.indexOf('\r') < 0;
    }

    /** Whether this row would add, merge or be skipped against the model as it is now. */
    public static Fate fateOf(Row row, ViewListener model)
    {
        if (model.getLocByName(row.name) != null) return Fate.MERGE;

        return whyNot(row) == null ? Fate.NEW : Fate.SKIP;
    }

    /** Why a row for a name the database does not have cannot be added, or null. */
    private static Why whyNot(Row row)
    {
        if (row.kind == Kind.LINKED || row.kind == Kind.MULTI) return Why.MULTI_UNIT;

        if (row.kind == Kind.UNKNOWN) return Why.UNREADABLE;

        if (!nameUsable(row.name)) return Why.NAME;

        if (!MarklinLocomotive.validateNewAddress(row.type, row.address)) return Why.ADDRESS;

        return null;
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

    /** The lines that will be skipped against the model as it is now, with why. */
    public Map<Integer, Why> skipped(ViewListener model)
    {
        Map<Integer, Why> lines = new TreeMap<>(this.unreadable);

        for (Row row : this.rows)
        {
            if (fateOf(row, model) == Fate.SKIP) lines.put(row.line, whyNot(row));
        }

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

    /** Whether the file would change this existing locomotive's address or decoder, and the decoder would take it. */
    public static boolean wouldReaddress(Row row, Locomotive loc)
    {
        if (row.type == null || loc.getDecoderType() == Locomotive.decoderType.MULTI_UNIT) return false;

        return (row.type != loc.getDecoderType() || row.address != loc.getAddress())
            && MarklinLocomotive.validateNewAddress(row.type, row.address);
    }

    /**
     * Whether giving this locomotive the row's address would break a multi-unit it belongs to (a head and its members
     * may not share an address) or take a train off the layout (the autonomy sweep removes a standing train with an
     * equivalent address).
     */
    private static boolean wouldBreakSomething(ViewListener model, Locomotive loc, Row row)
    {
        for (Locomotive head : model.getLocomotives())
        {
            Set<String> members = new HashSet<>(head.getLinkedLocomotiveNames().keySet());

            if (head.getModelMultiUnitLocomotiveNames() != null) members.addAll(head.getModelMultiUnitLocomotiveNames().keySet());

            if (members.isEmpty() || !(head == loc || members.contains(loc.getName()))) continue;

            members.add(head.getName());

            for (String name : members)
            {
                Locomotive other = model.getLocByName(name);

                if (other != null && other != loc && other.getDecoderType() == row.type && other.getAddress() == row.address)
                {
                    return true;
                }
            }
        }

        if (model.hasAutoLayout())
        {
            for (Locomotive other : model.getLocomotives())
            {
                if (other != loc && other.getDecoderType() == row.type && other.getAddress() == row.address
                    && model.getAutoLayout().getLocomotiveLocation(other) != null)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /** The locomotives whose address or decoder the file will change, by name. */
    public List<String> namesToReaddress(ViewListener model)
    {
        List<String> names = new ArrayList<>();

        for (Row row : this.rows)
        {
            Locomotive loc = model.getLocByName(row.name);

            if (loc != null && wouldReaddress(row, loc) && !wouldBreakSomething(model, loc, row)) names.add(row.name);
        }

        return names;
    }

    /** One line a locomotive's change shows as: name, then old and new decoder and address. */
    public String[] change(ViewListener model, String name)
    {
        Locomotive loc = model.getLocByName(name);

        for (Row row : this.rows)
        {
            if (row.name.equals(name)) return new String[] {name, loc.getDecoderType().name() + " " + loc.getAddress(),
                row.type.name() + " " + row.address};
        }

        return null;
    }

    /** Those among them that have a speed: the file must not re-address a train that is moving or only stopped by the power. */
    public List<String> movingToReaddress(ViewListener model)
    {
        List<String> moving = new ArrayList<>();

        for (String name : namesToReaddress(model))
        {
            if (model.getLocByName(name).getSpeed() > 0) moving.add(name);
        }

        return moving;
    }

    /** The locomotives that would end up on a decoder address another one holds, because of this import. */
    public List<Shared> sharedAddresses(ViewListener model)
    {
        Map<String, String> where = new LinkedHashMap<>();
        Set<String> touched = new HashSet<>();

        for (Locomotive loc : model.getLocomotives()) where.put(loc.getName(), loc.getDecoderType().name() + " " + loc.getAddress());

        for (Row row : this.rows)
        {
            Locomotive loc = model.getLocByName(row.name);

            if (loc == null ? fateOf(row, model) == Fate.NEW : wouldReaddress(row, loc) && !wouldBreakSomething(model, loc, row))
            {
                where.put(row.name, row.type.name() + " " + row.address);
                touched.add(row.name);
            }
        }

        Map<String, Shared> groups = new TreeMap<>();

        for (Map.Entry<String, String> e : where.entrySet())
        {
            if (!groups.containsKey(e.getValue()))
            {
                Shared shared = new Shared();

                shared.address = e.getValue();
                groups.put(e.getValue(), shared);
            }

            groups.get(e.getValue()).names.add(e.getKey());
        }

        List<Shared> out = new ArrayList<>();

        for (Shared shared : groups.values())
        {
            boolean ours = false;

            for (String name : shared.names) ours |= touched.contains(name);

            if (shared.names.size() > 1 && ours) out.add(shared);
        }

        return out;
    }

    /** Adds and merges; the single place that changes the model. */
    public Result apply(ViewListener model)
    {
        Result result = new Result();

        result.skipped.putAll(this.unreadable);

        for (Row row : this.rows)
        {
            Fate fate = fateOf(row, model);

            if (fate == Fate.SKIP)
            {
                Why why = whyNot(row);

                result.skipped.put(row.line, why);

                if (why == Why.MULTI_UNIT) result.leftOutMultiUnit++;
            }
            else if (fate == Fate.MERGE)
            {
                merge(model, model.getLocByName(row.name), row, result);
                result.merged++;
            }
            else
            {
                Locomotive loc = row.type == Locomotive.decoderType.MFX ? model.newMFXLocomotive(row.name, row.address)
                    : row.type == Locomotive.decoderType.DCC ? model.newDCCLocomotive(row.name, row.address)
                    : model.newMM2Locomotive(row.name, row.address);

                fillEmpty(loc, row);

                if (row.day == Day.OK)
                {
                    loc.raiseTotalRuntimeTo(row.runtimeMs, row.lastRun);
                }
                else if (row.runtimeMs > 0)
                {
                    if (row.day == Day.BAD) result.badLastRun++;
                    else result.addedWithoutRuntime++;
                }

                result.added++;
            }
        }

        return result;
    }

    /**
     * The file's runtime counts only when its last run day is later than this locomotive's (or it has none); then the
     * total becomes the larger of the two and the difference is filed on the file's day.  Address and decoder follow
     * the file unless that breaks something.
     */
    private static void merge(ViewListener model, Locomotive loc, Row row, Result result)
    {
        fillEmpty(loc, row);

        String have = loc.getLastRunDay();

        if (row.day == Day.OK && (have == null || row.lastRun.compareTo(have) > 0))
        {
            loc.raiseTotalRuntimeTo(row.runtimeMs, row.lastRun);
        }
        else if (row.day == Day.BAD && row.runtimeMs > 0)
        {
            result.badLastRun++;
        }

        if (row.kind == Kind.PLAIN && loc.getDecoderType() != Locomotive.decoderType.MULTI_UNIT
            && !MarklinLocomotive.validateNewAddress(row.type, row.address))
        {
            result.addressInvalid++;
        }
        else if (wouldReaddress(row, loc))
        {
            if (wouldBreakSomething(model, loc, row))
            {
                result.readdressKept++;
                return;
            }

            try
            {
                model.changeLocAddress(loc.getName(), row.address, row.type);
                result.readdressed.add(loc.getName());
            }
            catch (Exception refused)
            {
                result.readdressKept++;
            }
        }
    }

    /** Fills what is empty on `loc` from the row. */
    private static void fillEmpty(Locomotive loc, Row row)
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
    }

    // ------------------------------------------------------------------------------------------------ reading

    /** Reads the text of an export; never throws for a bad file. */
    public static MarklinLocomotiveCsv read(String text)
    {
        MarklinLocomotiveCsv csv = new MarklinLocomotiveCsv();

        // A file with carriage returns for line ends (an old Mac save) and no line feed anywhere
        if (text.indexOf('\n') < 0 && text.indexOf('\r') >= 0) text = text.replace('\r', '\n');

        List<Record> records = split(text, separator(text));

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
                csv.unreadable.put(record.line, Why.UNREADABLE);
            }
            else
            {
                csv.rows.add(row);
            }
        }

        return csv;
    }

    /** The separator the header line uses: a semicolon where Excel saves with the local list separator. */
    private static char separator(String text)
    {
        int commas = 0;
        int semicolons = 0;
        boolean quoted = false;

        for (int i = 0; i < text.length() && (quoted || (text.charAt(i) != '\n' && text.charAt(i) != '\r')); i++)
        {
            char c = text.charAt(i);

            if (c == '"') quoted = !quoted;
            else if (!quoted && c == ',') commas++;
            else if (!quoted && c == ';') semicolons++;
        }

        return semicolons > commas ? ';' : ',';
    }

    private static Row toRow(Record record, Map<String, Integer> column)
    {
        String name = field(record, column, "name");

        if (name.trim().isEmpty() || name.length() > TrainControlUI.MAX_LOC_NAME_DATABASE) return null;

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

        Row row = new Row();

        row.line = record.line;
        row.name = name;
        row.address = (int) address;
        row.preferredSpeed = (int) speed;
        row.runtimeMs = runtime;
        row.startYear = (int) start;
        row.endYear = (int) end;
        row.railway = field(record, column, "railwayname");
        row.notes = field(record, column, "notes");

        decoder(row, field(record, column, "decodertype").trim());
        lastRun(row, field(record, column, "lastrun").trim());

        return row;
    }

    /** What the decoder cell says: exactly MFX, DCC or MM2; those with more text are linked; other text is a multi-unit. */
    private static void decoder(Row row, String text)
    {
        Matcher found = DECODER.matcher(text);

        if (text.isEmpty())
        {
            row.kind = Kind.UNKNOWN;
        }
        else if (!found.find())
        {
            row.kind = Kind.MULTI;
        }
        else if (found.group(1).length() == text.length())
        {
            row.kind = Kind.PLAIN;
            row.type = Locomotive.decoderType.valueOf(found.group(1).toUpperCase(Locale.ROOT));
        }
        else
        {
            row.kind = Kind.LINKED;
        }
    }

    /** A day from 2018 to today as yyyy-MM-dd is usable; blank is none; anything else is bad (not an error). */
    private static void lastRun(Row row, String s)
    {
        row.day = Day.NONE;

        if (s.isEmpty()) return;

        row.day = Day.BAD;

        try
        {
            java.time.LocalDate day = java.time.LocalDate.parse(s);

            if (day.toString().equals(s) && s.compareTo("2018-01-01") >= 0 && !day.isAfter(java.time.LocalDate.now()))
            {
                row.day = Day.OK;
                row.lastRun = s;
            }
        }
        catch (java.time.format.DateTimeParseException unusable)
        {
            row.day = Day.BAD;
        }
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

    /** "H:MM:SS" as the export writes it, in milliseconds; empty is 0, unreadable (or minutes, seconds over 59) is -1. */
    private static long runtimeMs(String text)
    {
        String s = text.trim();

        if (s.isEmpty()) return 0;

        String[] parts = s.split(":", -1);

        if (parts.length != 3) return -1;

        long h = number(parts[0]);
        long m = number(parts[1]);
        long sec = number(parts[2]);

        if (h < 0 || m < 0 || sec < 0 || h > MOST_HOURS || m > 59 || sec > 59) return -1;

        return ((h * 60 + m) * 60 + sec) * 1000;
    }

    /** One CSV record: its fields, the line it starts on, and whether its quoting was broken. */
    private static final class Record
    {
        final int line;
        final List<String> fields = new ArrayList<>();
        boolean bad;
        boolean quotedAny;

        Record(int line)
        {
            this.line = line;
        }

        /** A line of nothing, as Excel pads a sheet with. */
        boolean isBlank()
        {
            for (String f : this.fields) if (!f.isEmpty()) return false;

            return !this.quotedAny;
        }
    }

    /**
     * Splits CSV text the way `Util.escapeCsv` writes it: fields with a separator, quote or line feed are quoted with ""
     * for a quote; a row ends at a line feed outside quotes (a carriage return before it is dropped); blank lines are
     * ignored.
     */
    private static List<Record> split(String text, char sep)
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

            if (c == sep)
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

                if (!record.isBlank()) records.add(record);

                record = null;
                wasQuoted = false;
                closed = false;
                line++;
            }
            else if (c == '"' && field.length() == 0 && !wasQuoted)
            {
                quoted = true;
                wasQuoted = true;
                record.quotedAny = true;
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

            if (!record.isBlank()) records.add(record);
        }

        return records;
    }
}
