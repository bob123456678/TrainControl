package core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Locomotive.decoderType;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinLocomotiveCsv;
import org.traincontrol.marklin.MarklinSimpleComponent;

/**
 * Locomotives > Import from CSV (FR-049), at the model: the file `exportLocsToCSV` writes is read whole, and a locomotive
 * is added or merged from each row.  The door itself is `ui.testLocomotiveCsvImportDoor`.
 */
public class testLocomotiveCsvImport
{
    private static final String HEADER = "Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,Start Year,End Year,RailwayName,Notes\n";

    private static MarklinControlStation model;

    private static final List<String> made = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulated();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null) for (String name : made) model.deleteLoc(name);
    }

    /** A locomotive as the file can say it. */
    private static final class Snap
    {
        final String name;
        final decoderType type;
        final int address;
        final int speed;
        final long runtime;
        final int start;
        final int end;
        final String railway;
        final String notes;

        Snap(Locomotive l)
        {
            name = l.getName();
            type = l.getDecoderType();
            address = l.getAddress();
            speed = l.getPreferredSpeed();
            runtime = l.getTotalRuntime();
            start = l.getStructuredNotes().getStartYear();
            end = l.getStructuredNotes().getEndYear();
            railway = l.getStructuredNotes().getRailway();
            notes = l.getStructuredNotes().getNotes();
        }

        @Override
        public String toString()
        {
            return name + "|" + type + "|" + address + "|" + speed + "|" + runtime + "|" + start + "|" + end + "|"
                + railway + "|" + notes.replace("\n", "\\n").replace("\r", "\\r");
        }
    }

    private static MarklinLocomotive make(String name, decoderType type, int address)
    {
        made.add(name);

        return type == decoderType.MFX ? model.newMFXLocomotive(name, address)
            : type == decoderType.DCC ? model.newDCCLocomotive(name, address) : model.newMM2Locomotive(name, address);
    }

    private static Map<String, String> everyoneElse(Iterable<String> except)
    {
        Map<String, String> all = new HashMap<>();

        for (Locomotive l : model.getLocomotives()) all.put(l.getName(), new Snap(l).toString());

        for (String name : except) all.remove(name);

        return all;
    }

    /**
     * An export comes back whole: locomotives deleted and imported again have every field they were exported with, awkward
     * names and notes included, and the locomotives that stayed are not changed.
     *
     * MUTATION: read a doubled quote as two quotes, end a row at a line feed inside quotes, or split a quoted field at its
     * comma, and this fails.
     */
    @Test
    public void testAnExportComesBackWhole()
    {
        String[] names = {"FR049 plain", "FR049 comma, name", "FR049 \"quoted\" name", "FR049 two\nlines"};
        decoderType[] types = {decoderType.MFX, decoderType.DCC, decoderType.MM2, decoderType.MFX};
        String[] notes = {"", "Notes, with a comma and \"quotes\"\nand a second line\n\nand a gap",
            "\"\"", "line one\r\nline two"};

        for (int i = 0; i < names.length; i++)
        {
            MarklinLocomotive l = make(names[i], types[i], 10 + i * 7);

            l.setPreferredSpeed(i * 20);
            l.getHistoricalOperatingTime().put("2024-03-0" + (i + 1), (3725L + i * 100000L) * 1000);
            l.setStructuredNotes(1950 + i, i == 0 ? 0 : 1990 + i, i == 1 ? "Rail, \"Co\"" : "", notes[i]);
        }

        List<String> mine = Arrays.asList(names);
        Map<String, String> before = new HashMap<>();

        for (String name : names) before.put(name, new Snap(model.getLocByName(name)).toString());

        Map<String, String> others = everyoneElse(mine);

        String csv = model.exportLocsToCSV();

        for (String name : names) model.deleteLoc(name);

        MarklinLocomotiveCsv.Result result = MarklinLocomotiveCsv.read(csv).apply(model);

        assertEquals(result.added, names.length, "the deleted locomotives did not all come back as new");

        for (String name : names)
        {
            assertNotNull(model.getLocByName(name), name + " did not come back");
            assertEquals(new Snap(model.getLocByName(name)).toString(), before.get(name), "a field of " + name
                + " did not survive the export and import");
        }

        assertEquals(everyoneElse(mine), others, "importing the export changed a locomotive that was already here");
    }

    /**
     * A new name is a plain locomotive: the file's decoder, address, preset speed, runtime and notes, and a button
     * mapping in the file is ignored.  A runtime from a file is a total, filed under one marker day, so no day of running is
     * invented.
     *
     * MUTATION: file the runtime under today, or read the address from the wrong column, and this fails.
     */
    @Test
    public void testANewNameIsAddedWithTheFilesFieldsAndNoDayInvented()
    {
        String name = "FR049 new one";
        made.add(name);

        String csv = HEADER + name + ",Page 2 Key 5;Page 3 Key 1,DCC,77,40,10:00:00,1962,1975,SNCF,Fast\n";

        MarklinLocomotiveCsv.Result result = MarklinLocomotiveCsv.read(csv).apply(model);

        assertEquals(result.added, 1);

        Locomotive l = model.getLocByName(name);

        assertNotNull(l, "the new name was not added");
        assertEquals(l.getDecoderType(), decoderType.DCC);
        assertEquals(l.getAddress(), 77);
        assertEquals(l.getPreferredSpeed(), 40);
        assertEquals(l.getTotalRuntime(), 36000000L);
        assertEquals(l.getStructuredNotes().getStartYear(), 1962);
        assertEquals(l.getStructuredNotes().getEndYear(), 1975);
        assertEquals(l.getStructuredNotes().getRailway(), "SNCF");
        assertEquals(l.getStructuredNotes().getNotes(), "Fast");
        assertEquals(l.getRuntimeToday(), 0L, "the file's runtime was filed under today");
        assertEquals(l.getHistoricalOperatingTime().keySet(), new TreeSet<>(Arrays.asList(Locomotive.IMPORTED_RUNTIME_DAY)),
            "the runtime was filed under a day of running");
    }

    /**
     * An existing name's runtime becomes the larger of the two, in both directions, and the days already recorded are not
     * touched; its decoder and address stay whatever the file says.
     *
     * MUTATION: add the two runtimes, take the file's whatever it is, or let the file change the address, and this fails.
     */
    @Test
    public void testAnExistingNamesRuntimeTakesTheLargerAndItsAddressStays()
    {
        String name = "FR049 runner";
        MarklinLocomotive l = make(name, decoderType.MFX, 31);

        l.getHistoricalOperatingTime().put("2024-01-01", 3600000L);
        l.getHistoricalOperatingTime().put("2024-01-02", 1800000L);

        Map<String, Long> days = new HashMap<>(l.getHistoricalOperatingTime());

        // THE FILE HAS LESS (1:00:00 against 1:30:00), AT ANOTHER ADDRESS AND DECODER
        MarklinLocomotiveCsv.read(HEADER + name + ",,DCC,999,0,1:00:00,0,0,,\n").apply(model);

        assertEquals(l.getTotalRuntime(), 5400000L, "a smaller runtime in the file lowered or changed the total");
        assertEquals(l.getHistoricalOperatingTime(), days, "a smaller runtime in the file changed the recorded days");
        assertEquals(l.getAddress(), 31, "the file changed the address");
        assertEquals(l.getDecoderType(), decoderType.MFX, "the file changed the decoder");

        // THE FILE HAS MORE (5:00:00): THE DIFFERENCE IS FILED, THE DAYS STAY
        MarklinLocomotiveCsv.read(HEADER + name + ",,DCC,999,0,5:00:00,0,0,,\n").apply(model);

        assertEquals(l.getTotalRuntime(), 18000000L, "a larger runtime in the file was not taken (or was added on top)");
        assertEquals(l.getRuntimeOnDay("2024-01-01"), 3600000L);
        assertEquals(l.getRuntimeOnDay("2024-01-02"), 1800000L);
        assertEquals(l.getRuntimeOnDay(Locomotive.IMPORTED_RUNTIME_DAY), 18000000L - 5400000L);
        assertEquals(l.getAddress(), 31);
        assertEquals(l.getDecoderType(), decoderType.MFX);

        // THE SAME FILE AGAIN ADDS NOTHING: a file from a copy of this database is not counted twice
        MarklinLocomotiveCsv.read(HEADER + name + ",,DCC,999,0,5:00:00,0,0,,\n").apply(model);

        assertEquals(l.getTotalRuntime(), 18000000L, "importing the same runtime twice added it twice");
    }

    /**
     * What is set here stays; what is empty here is filled from the file.
     *
     * MUTATION: let the file win, or fill nothing, and this fails.
     */
    @Test
    public void testSetFieldsStayAndEmptyOnesFill()
    {
        String name = "FR049 half full";
        MarklinLocomotive l = make(name, decoderType.MM2, 5);

        l.setPreferredSpeed(33);
        l.setStructuredNotes(0, 1980, "Mine", "");

        MarklinLocomotiveCsv.read(HEADER + name + ",,MM2,5,70,0:00:00,1940,1990,Theirs,From the file\n").apply(model);

        assertEquals(l.getPreferredSpeed(), 33, "a preset speed already set was replaced");
        assertEquals(l.getStructuredNotes().getEndYear(), 1980, "an end year already set was replaced");
        assertEquals(l.getStructuredNotes().getRailway(), "Mine", "a railway already set was replaced");
        assertEquals(l.getStructuredNotes().getStartYear(), 1940, "an empty start year was not filled");
        assertEquals(l.getStructuredNotes().getNotes(), "From the file", "empty notes were not filled");

        String bare = "FR049 bare";
        MarklinLocomotive b = make(bare, decoderType.MM2, 6);

        MarklinLocomotiveCsv.read(HEADER + bare + ",,MM2,6,70,0:00:00,0,0,,\n").apply(model);

        assertEquals(b.getPreferredSpeed(), 70, "an empty preset speed was not filled");
    }

    /**
     * A row that cannot be read is skipped and its line is named; the rows around it still come in.  Lines count the
     * physical lines of the file, so a quoted note over several lines moves the next row down.
     *
     * MUTATION: count rows rather than lines, or stop at the first bad row, and this fails.
     */
    @Test
    public void testRowsThatCannotBeReadAreSkippedAndNamed()
    {
        String ok1 = "FR049 ok one";
        String ok2 = "FR049 ok two";
        made.add(ok1);
        made.add(ok2);

        String csv = HEADER
            + ok1 + ",,MFX,41,0,0:00:00,0,0,,\"a\nb\nc\"\n"                          // line 2, to line 4
            + "FR049 bad address,,MFX,abc,0,0:00:00,0,0,,\n"                          // line 5
            + "FR049 no such address,,MFX,0,0,0:00:00,0,0,,\n"                        // line 6
            + "FR049 bad runtime,,MFX,42,0,xx,0,0,,\n"                                // line 7
            + "FR049 short row,,MFX,43\n"                                             // line 8
            + "FR049 multi,,Multi-Unit,44,0,0:00:00,0,0,,\n"                          // line 9
            + ",,MFX,45,0,0:00:00,0,0,,\n"                                            // line 10
            + "FR049 speed,,MFX,46,101,0:00:00,0,0,,\n"                               // line 11
            + ok1 + ",,MFX,47,0,0:00:00,0,0,,\n"                                      // line 12 (the name twice)
            + "\n"                                                                    // line 13, blank: not a row
            + ok2 + ",,MM2,48,0,0:00:00,0,0,,\n"                                      // line 14
            + "FR049 open quote,,MFX,49,0,0:00:00,0,0,,\"never closed\n";             // line 15 to the end

        MarklinLocomotiveCsv csv0 = MarklinLocomotiveCsv.read(csv);

        assertTrue(csv0.isLocomotiveFile());

        List<Integer> expected = Arrays.asList(5, 6, 7, 8, 9, 10, 11, 12, 15);

        assertEquals(csv0.skippedLines(model), expected, "the lines the preview names are not the unreadable ones");
        assertEquals(csv0.countNew(model), 2);

        int locsBefore = model.getLocomotives().size();

        MarklinLocomotiveCsv.Result result = csv0.apply(model);

        assertEquals(result.skippedLines, expected, "the lines the result names are not the unreadable ones");
        assertEquals(result.added, 2);
        assertEquals(model.getLocomotives().size(), locsBefore + 2, "a skipped row added a locomotive");
        assertNull(model.getLocByName("FR049 bad address"));
        assertNull(model.getLocByName("FR049 no such address"));
        assertNull(model.getLocByName("FR049 multi"));
        assertEquals(model.getLocByName(ok1).getAddress(), 41, "the second row of one name changed the first");
        assertEquals(model.getLocByName(ok1).getStructuredNotes().getNotes(), "a\nb\nc");
        assertEquals(model.getLocByName(ok2).getDecoderType(), decoderType.MM2);
    }

    /**
     * A file that is not a locomotive export is refused whole, and reading changes nothing.
     *
     * MUTATION: accept a file with no Name column, and this fails.
     */
    @Test
    public void testAFileThatIsNotAnExportIsRefused()
    {
        int locsBefore = model.getLocomotives().size();

        for (String text : new String[] {"", "\n\n", "{\"routes\": []}", "Address,Notes\n1,x\n", "\"Name,unclosed\n"})
        {
            MarklinLocomotiveCsv csv = MarklinLocomotiveCsv.read(text);

            assertFalse(csv.isLocomotiveFile(), "read as a locomotive file: " + text.replace("\n", "\\n"));
            assertTrue(csv.getRows().isEmpty());
        }

        assertEquals(model.getLocomotives().size(), locsBefore);
    }

    /**
     * A file saved with Windows line endings, or with a byte order mark, reads as the export does.
     *
     * MUTATION: keep the carriage return in the last field, and this fails.
     */
    @Test
    public void testWindowsLineEndingsAndAByteOrderMarkRead()
    {
        String name = "FR049 crlf";
        made.add(name);

        String csv = "\uFEFF" + HEADER.replace("\n", "\r\n") + name + ",,MFX,52,10,0:01:00,1900,1910,GWR,Plain\r\n";

        MarklinLocomotiveCsv.read(csv).apply(model);

        Locomotive l = model.getLocByName(name);

        assertNotNull(l);
        assertEquals(l.getStructuredNotes().getNotes(), "Plain", "a carriage return stayed on the last field");
        assertEquals(l.getTotalRuntime(), 60000L);
    }

    /**
     * The export's columns and the saved form of a locomotive are as they were: the header the importer reads, the saved
     * record's version and fields (a change there is a change to LocDB.data), and an imported locomotive saves and loads like
     * any other.
     *
     * MUTATION: add a column to the export or a field to MarklinSimpleComponent, and this fails.
     */
    @Test
    public void testTheExportAndTheSavedFormAreUnchanged() throws Exception
    {
        assertTrue(model.exportLocsToCSV().startsWith(HEADER), "the export's header changed");

        ObjectStreamClass saved = ObjectStreamClass.lookup(MarklinSimpleComponent.class);

        assertEquals(saved.getSerialVersionUID(), -9111893030704758839L, "the saved record's version changed");

        List<String> fields = new ArrayList<>();

        for (ObjectStreamField f : saved.getFields()) fields.add(f.getName());

        assertEquals(new TreeSet<>(fields), new TreeSet<>(Arrays.asList("address", "arrivalFunction", "centralStationLinkedLocomotives",
            "conditions", "customFunctions", "departureFunction", "functionTriggerTypes", "functionTypes", "functions",
            "historicalOperatingTimeNew", "linkedLocomotives", "localFunctionImageURLs", "localImageURL", "name", "notes",
            "numActuations", "preferredFunctions", "preferredSpeed", "accessoryDecoderType", "reversible", "route",
            "routeEnabled", "s88", "s88TriggerType", "state", "trainLength", "type")), "the saved record's fields changed");

        String name = "FR049 saved";
        made.add(name);

        MarklinLocomotiveCsv.read(HEADER + name + ",,MFX,61,20,2:00:00,1930,1940,LNER,Saved\n").apply(model);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(bytes))
        {
            out.writeObject(new MarklinSimpleComponent(model.getLocByName(name)));
        }

        MarklinSimpleComponent back;

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
        {
            back = (MarklinSimpleComponent) in.readObject();
        }

        assertEquals(back.getName(), name);
        assertEquals(back.getAddress(), 61);
        assertEquals(back.getPreferredSpeed(), 20);
        assertEquals(back.getHistoricalOperatingTime().values().stream().mapToLong(Long::longValue).sum(), 7200000L);
        assertTrue(back.getLocNotes().contains("LNER"));
    }
}
