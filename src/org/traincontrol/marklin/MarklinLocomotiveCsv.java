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

    /** Why a change of address is not made. */
    public enum Keep { CONSIST, STANDING, MOVING, HELD }

    /** One locomotive the import will give another address or decoder. */
    public static final class Move
    {
        public String name;
        /** Decoder and address before and after, as "MFX 5". */
        public String from;
        public String to;
    }

    /** One locomotive that keeps its address, and why. */
    public static final class Kept
    {
        public String name;
        public Keep why;
    }

    /** What the import will do to addresses, worked out once: the question shows it and `apply` carries it out. */
    public static final class Plan
    {
        public final List<Move> moves = new ArrayList<>();
        public final List<Kept> kept = new ArrayList<>();
    }

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
        /** The runtime cell could not be read; the row is used without it. */
        public boolean runtimeBad;
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
        /** Rows whose running time could not be read, so it was left alone. */
        public int badRuntime;
        /** Locomotives that kept their address, and why. */
        public final List<Kept> kept = new ArrayList<>();
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

    /** The text of a file's bytes: UTF-8 when it is, else what Excel saved in on this machine. */
    public static String decode(byte[] bytes)
    {
        return decode(bytes, fallbackFor(Charset.defaultCharset()));
    }

    /** What to read a file in after UTF-8 fails: the machine's own code page when it is a single-byte one, else Windows-1252. */
    public static Charset fallbackFor(Charset machine)
    {
        return machine.newEncoder().maxBytesPerChar() == 1.0f ? machine : Charset.forName("windows-1252");
    }

    /** The text of a file's bytes: UTF-8 when it is, else read in `fallback`. */
    public static String decode(byte[] bytes, Charset fallback)
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException notUtf8)
        {
            return new String(bytes, fallback);
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

    /** The names a head drives: its own links and the Central Station's, either of which may be absent. */
    private static Set<String> membersOf(Locomotive head)
    {
        Set<String> members = new HashSet<>();

        if (head.getLinkedLocomotiveNames() != null) members.addAll(head.getLinkedLocomotiveNames().keySet());

        if (head.getModelMultiUnitLocomotiveNames() != null) members.addAll(head.getModelMultiUnitLocomotiveNames().keySet());

        return members;
    }

    /**
     * Why the file may not give this locomotive the address `target` ("MFX 5"), judged against `where`, the addresses as
     * they will stand after the changes planned before it: it would break a multi-unit it belongs to (a head and its
     * members may not share an address), or it is the address of a train standing on the layout or of a member of a standing
     * consist (the autonomy sweep takes a standing train off the layout for either).  Null when it may.
     */
    private static Keep keepReason(ViewListener model, Locomotive loc, String target, Map<String, String> where)
    {
        for (Locomotive head : model.getLocomotives())
        {
            Set<String> members = membersOf(head);

            if (members.isEmpty() || !(head == loc || members.contains(loc.getName()))) continue;

            members.add(head.getName());

            for (String name : members)
            {
                if (!name.equals(loc.getName()) && target.equals(where.get(name))) return Keep.CONSIST;
            }
        }

        if (model.hasAutoLayout())
        {
            for (Locomotive standing : model.getLocomotives())
            {
                if (standing == loc || model.getAutoLayout().getLocomotiveLocation(standing) == null) continue;

                Set<String> train = membersOf(standing);

                train.add(standing.getName());

                for (String name : train)
                {
                    if (!name.equals(loc.getName()) && target.equals(where.get(name))) return Keep.STANDING;
                }
            }
        }

        return null;
    }

    /** The decoder and address of a locomotive, as "MFX 5". */
    private static String label(Locomotive.decoderType type, int address)
    {
        return type.name() + " " + address;
    }

    /**
     * Works out, once and in file order, which locomotives change address or decoder and which keep theirs, each change
     * judged against the addresses as the earlier ones leave them.  The question shows this plan and `apply` carries it out.
     */
    public Plan plan(ViewListener model)
    {
        Plan plan = new Plan();
        Map<String, String> where = new HashMap<>();

        for (Locomotive loc : model.getLocomotives()) where.put(loc.getName(), label(loc.getDecoderType(), loc.getAddress()));

        for (Row row : this.rows)
        {
            Locomotive loc = model.getLocByName(row.name);

            if (loc == null || !wouldReaddress(row, loc)) continue;

            String target = label(row.type, row.address);
            Keep why = keepReason(model, loc, target, where);

            if (why != null)
            {
                Kept kept = new Kept();

                kept.name = loc.getName();
                kept.why = why;
                plan.kept.add(kept);
            }
            else
            {
                Move move = new Move();

                move.name = loc.getName();
                move.from = where.get(loc.getName());
                move.to = target;
                plan.moves.add(move);
                where.put(loc.getName(), target);
            }
        }

        return plan;
    }

    /** Those the plan re-addresses that have a speed: the file must not re-address a train that is moving or only stopped by the power. */
    public List<String> movingToReaddress(Plan plan, ViewListener model)
    {
        List<String> moving = new ArrayList<>();

        for (Move move : plan.moves)
        {
            if (model.getLocByName(move.name).getSpeed() > 0) moving.add(move.name);
        }

        return moving;
    }

    /** The locomotives that would end up on a decoder address another one holds, because of this import. */
    public List<Shared> sharedAddresses(ViewListener model, Plan plan)
    {
        Map<String, String> where = new LinkedHashMap<>();
        Set<String> touched = new HashSet<>();

        for (Locomotive loc : model.getLocomotives()) where.put(loc.getName(), label(loc.getDecoderType(), loc.getAddress()));

        for (Move move : plan.moves)
        {
            where.put(move.name, move.to);
            touched.add(move.name);
        }

        for (Row row : this.rows)
        {
            if (model.getLocByName(row.name) == null && fateOf(row, model) == Fate.NEW)
            {
                where.put(row.name, label(row.type, row.address));
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

    /** Adds and merges, and changes the addresses of the plan and no others; the single place that changes the model. */
    public Result apply(ViewListener model, Plan plan)
    {
        Result result = new Result();
        Map<String, Move> moves = new HashMap<>();
        Set<String> held = new HashSet<>();

        for (Move move : plan.moves) moves.put(move.name, move);

        result.skipped.putAll(this.unreadable);
        result.kept.addAll(plan.kept);

        for (Row row : this.rows)
        {
            Fate fate = fateOf(row, model);

            if (row.runtimeBad) result.badRuntime++;

            if (fate == Fate.SKIP)
            {
                Why why = whyNot(row);

                result.skipped.put(row.line, why);

                if (why == Why.MULTI_UNIT) result.leftOutMultiUnit++;
            }
            else if (fate == Fate.MERGE)
            {
                merge(model, model.getLocByName(row.name), row, moves.get(row.name), held, result);
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
     * total becomes the larger of the two and the difference is filed on the file's day.  The address and decoder change
     * only if the plan says so, and not if the train has begun to move since.
     */
    private static void merge(ViewListener model, Locomotive loc, Row row, Move move, Set<String> held, Result result)
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

        if (row.kind != Kind.PLAIN)
        {
            // Left out only where the file's address differs: "Steam", no decoder, or the same address asked nothing
            if ((row.kind == Kind.MULTI || row.kind == Kind.LINKED) && row.address != loc.getAddress()) result.leftOutMultiUnit++;
        }
        else if (loc.getDecoderType() != Locomotive.decoderType.MULTI_UNIT
            && !MarklinLocomotive.validateNewAddress(row.type, row.address))
        {
            result.addressInvalid++;
        }
        else if (move != null)
        {
            Kept late = new Kept();

            late.name = loc.getName();
            late.why = Keep.MOVING;

            // An address a train that began to move still holds is not free for the rows after it
            if (held.contains(move.to))
            {
                late.why = Keep.HELD;
                result.kept.add(late);
                return;
            }

            if (loc.getSpeed() > 0)
            {
                held.add(label(loc.getDecoderType(), loc.getAddress()));
                result.kept.add(late);
                return;
            }

            try
            {
                model.changeLocAddress(loc.getName(), row.address, row.type);
                result.readdressed.add(loc.getName());
            }
            catch (Exception refused)
            {
                held.add(label(loc.getDecoderType(), loc.getAddress()));
                result.kept.add(late);
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
            || end < 0 || end > Integer.MAX_VALUE)
        {
            return null;
        }

        Row row = new Row();

        row.line = record.line;
        row.name = name;
        row.address = (int) address;
        row.preferredSpeed = (int) speed;
        row.runtimeBad = runtime < 0;
        row.runtimeMs = Math.max(runtime, 0);
        row.startYear = (int) start;
        row.endYear = (int) end;
        row.railway = field(record, column, "railwayname");
        row.notes = field(record, column, "notes");

        decoder(row, field(record, column, "decodertype").trim());
        lastRun(row, field(record, column, "lastrun").trim());

        return row;
    }

    /** The words a decoder cell may hold for a multi-unit in any language, and the patterns for a linked locomotive. */
    private static final Set<String> MULTI_UNIT_LABELS = new HashSet<>();
    private static final List<Pattern> LINKED_LABELS = new ArrayList<>();

    static
    {
        MULTI_UNIT_LABELS.add("multi_unit");

        for (String language : new String[] {"", "da", "de", "es", "fr", "it", "nl", "pl"})
        {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org.traincontrol.resources.messages",
                language.isEmpty() ? Locale.ROOT : new Locale(language));

            MULTI_UNIT_LABELS.add(bundle.getString("loc.multiUnit").toLowerCase(Locale.ROOT));

            LINKED_LABELS.add(Pattern.compile(Pattern.quote(bundle.getString("loc.multiUnitLinked")).replace("{0}",
                "\\E(MFX|DCC|MM2)\\Q"), Pattern.CASE_INSENSITIVE));
        }
    }

    /** What the decoder cell says: exactly MFX, DCC or MM2; a linked locomotive's label; a multi-unit's; else unknown. */
    private static void decoder(Row row, String text)
    {
        Matcher found = DECODER.matcher(text);

        row.kind = Kind.UNKNOWN;

        if (MULTI_UNIT_LABELS.contains(text.toLowerCase(Locale.ROOT)))
        {
            row.kind = Kind.MULTI;
            return;
        }

        for (Pattern linked : LINKED_LABELS)
        {
            if (linked.matcher(text).matches()) row.kind = Kind.LINKED;
        }

        if (row.kind == Kind.UNKNOWN && found.matches())
        {
            row.kind = Kind.PLAIN;
            row.type = Locomotive.decoderType.valueOf(found.group(1).toUpperCase(Locale.ROOT));
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
