package core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.nio.charset.Charset;
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
import org.traincontrol.marklin.MarklinLocomotiveCsv.Why;
import org.traincontrol.marklin.MarklinSimpleComponent;
import support.Reflect;

/**
 * Locomotives > Import from CSV (FR-049), at the model: the file `exportLocsToCSV` writes is read whole, and a locomotive
 * is added or merged from each row.  The door itself is `regression.testLocomotiveCsvImportDoor`.
 */
public class testLocomotiveCsvImport
{
    /** The export before the LastRun column: still a file the import reads. */
    private static final String OLD = "Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,Start Year,End Year,RailwayName,Notes\n";

    /** The export as it is now: Adam asked for LastRun, the day that decides whether a file's runtime counts. */
    private static final String HEADER = "Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,Start Year,End Year,RailwayName,Notes,LastRun\n";

    private static MarklinControlStation model;

    private static final List<String> made = new ArrayList<>();

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        try
        {
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulated();
        }
        catch (Exception e)
        {
            if (sandbox != null) sandbox.close();

            throw e;
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null) for (String name : made) model.deleteLoc(name);

        if (sandbox != null) sandbox.close();
    }

    /** A locomotive as the file can say it. */
    private static final class Snap
    {
        final String name;
        final decoderType type;
        final int address;
        final int speed;
        final long runtime;
        final String lastRun;
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
            lastRun = l.getLastRunDay();
            start = l.getStructuredNotes().getStartYear();
            end = l.getStructuredNotes().getEndYear();
            railway = l.getStructuredNotes().getRailway();
            notes = l.getStructuredNotes().getNotes();
        }

        @Override
        public String toString()
        {
            return name + "|" + type + "|" + address + "|" + speed + "|" + runtime + "|" + lastRun + "|" + start + "|" + end
                + "|" + railway + "|" + notes.replace("\n", "\\n").replace("\r", "\\r");
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

    /** The locomotives the file would re-address that have a speed. */
    private static List<String> moving(String text)
    {
        MarklinLocomotiveCsv csv = MarklinLocomotiveCsv.read(text);

        return csv.movingToReaddress(csv.plan(model), model);
    }

    private static MarklinLocomotiveCsv.Result importText(String text)
    {
        MarklinLocomotiveCsv csv = MarklinLocomotiveCsv.read(text);

        return csv.apply(model, csv.plan(model));
    }

    /**
     * An export comes back whole: locomotives deleted and imported again have every field they were exported with, awkward
     * notes and a quote in the name included, and their last run day; the locomotives that stayed are not changed.  A name
     * with a comma or a line break is not added again (routes re-parse names), and is counted with its reason.
     *
     * MUTATION: read a doubled quote as two quotes, end a row at a line feed inside quotes, split a quoted field at its
     * comma, or add a name with a comma, and this fails.
     */
    @Test
    public void testAnExportComesBackWhole()
    {
        String[] names = {"FR049 plain", "FR049 \"quoted\" name", "FR049 comma, name", "FR049 two\nlines"};
        decoderType[] types = {decoderType.MFX, decoderType.MM2, decoderType.DCC, decoderType.MFX};
        String[] notes = {"Notes, with a comma and \"quotes\"\nand a second line\n\nand a gap", "line one\r\nline two", "", "\"\""};

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

        MarklinLocomotiveCsv.Result result = importText(csv);

        assertEquals(result.added, 2, "the plain and the quoted name did not come back as new");

        for (String name : Arrays.asList(names[0], names[1]))
        {
            assertNotNull(model.getLocByName(name), name + " did not come back");
            assertEquals(new Snap(model.getLocByName(name)).toString(), before.get(name), "a field of " + name
                + " did not survive the export and import");
        }

        assertNull(model.getLocByName(names[2]), "a name with a comma was added, and no route can store it");
        assertNull(model.getLocByName(names[3]), "a name with a line break was added");
        assertEquals(result.skipped.get(importLine(csv, names[2])), Why.NAME, "the comma name is not skipped with its reason");

        assertEquals(everyoneElse(mine), others, "importing the export changed a locomotive that was already here");
    }

    /** The line of the row for this name in the export (the row starts with the escaped name). */
    private static int importLine(String csv, String name)
    {
        String escaped = org.traincontrol.util.Util.escapeCsv(name);
        int at = csv.indexOf("\n" + escaped + ",");

        assertTrue(at >= 0, "precondition: the export has no row for " + name);

        int line = 1;

        for (int i = 0; i <= at; i++) if (csv.charAt(i) == '\n') line++;

        return line;
    }

    /**
     * A name that is already here is merged however it is spelled: the rule against commas is for new names.
     *
     * MUTATION: ask the name rule before the lookup of an existing name, and this fails.
     */
    @Test
    public void testAnExistingNameWithACommaIsStillMerged()
    {
        String name = "FR049 old, name";
        MarklinLocomotive l = make(name, decoderType.MM2, 14);

        MarklinLocomotiveCsv.Result result = importText(OLD + "\"FR049 old, name\",,MM2,14,0,0:00:00,0,0,Rail,Note\n");

        assertEquals(result.merged, 1);
        assertTrue(result.skipped.isEmpty(), "the old name was skipped: " + result.skipped);
        assertEquals(l.getStructuredNotes().getRailway(), "Rail");
    }

    /** A file saved in `codePage` and read with that fallback is the locomotive that is here, not a new one. */
    private static void assertTheCodePageMatches(String codePage, String name, int address)
    {
        make(name, decoderType.MM2, address);
        int before = model.getLocomotives().size();

        byte[] ansi = (OLD + name + ",,MM2," + address + ",0,0:00:00,0,0,Rail,Note\n").getBytes(Charset.forName(codePage));

        MarklinLocomotiveCsv.Result result = importText(MarklinLocomotiveCsv.decode(ansi, Charset.forName(codePage)));

        assertEquals(result.merged, 1, "the file's " + name + " was not the locomotive that is here");
        assertEquals(result.added, 0, "the file added a locomotive for a name that is here");
        assertEquals(model.getLocomotives().size(), before);
        assertEquals(model.getLocByName(name).getStructuredNotes().getRailway(), "Rail");
    }

    /**
     * A file Excel saved in the Windows code page matches the locomotive it names: a name with a letter outside ASCII is
     * not read as junk and added beside it; a UTF-8 file is still read as UTF-8.
     *
     * MUTATION: decode every file as UTF-8 whatever it holds, and this fails.
     */
    @Test
    public void testAFileSavedInTheWindowsCodePageMatchesTheLocomotive() throws Exception
    {
        assertTheCodePageMatches("windows-1252", "FR049 Köf", 15);

        byte[] utf8 = (OLD + "FR049 Köf" + ",,MM2,15,0,0:00:00,0,0,Rail2,Note\n").getBytes("UTF-8");

        assertEquals(MarklinLocomotiveCsv.read(MarklinLocomotiveCsv.decode(utf8)).getRows().get(0).name, "FR049 Köf",
            "a UTF-8 file was not read as UTF-8");
    }

    /**
     * A Polish file Excel saved in Windows-1250 matches the locomotive it names when the machine's own code page is the
     * fallback: read as Windows-1252 the letters become others and a junk locomotive is added.
     *
     * MUTATION: read the fallback as Windows-1252 whatever it is given, and this fails.
     */
    @Test
    public void testAFileSavedInThePolishCodePageMatchesTheLocomotive()
    {
        assertTheCodePageMatches("windows-1250", "FR049 Łódź", 28);
    }

    /**
     * A new name is a plain locomotive: the file's decoder, address, preset speed and notes, and a button mapping in the
     * file is ignored.  With a last run day it takes the file's runtime on that day; with none it comes in with no runtime
     * and is counted.
     *
     * MUTATION: file the runtime under today, give a locomotive with no last run day the runtime, or read the address from
     * the wrong column, and this fails.
     */
    @Test
    public void testANewNameIsAddedWithTheFilesFieldsAndRuntimeOnItsLastRunDay()
    {
        String name = "FR049 new one";
        String bare = "FR049 new no day";
        made.add(name);
        made.add(bare);

        MarklinLocomotiveCsv.Result result = importText(HEADER
            + name + ",Page 2 Key 5;Page 3 Key 1,DCC,77,40,10:00:00,1962,1975,SNCF,Fast,2023-05-05\n"
            + bare + ",,MM2,5,0,4:00:00,0,0,,,\n");

        assertEquals(result.added, 2);
        assertEquals(result.addedWithoutRuntime, 1, "the locomotive that came in without a last run day was not counted");

        Locomotive l = model.getLocByName(name);

        assertNotNull(l, "the new name was not added");
        assertEquals(l.getDecoderType(), decoderType.DCC);
        assertEquals(l.getAddress(), 77);
        assertEquals(l.getPreferredSpeed(), 40);
        assertEquals(l.getStructuredNotes().getStartYear(), 1962);
        assertEquals(l.getStructuredNotes().getEndYear(), 1975);
        assertEquals(l.getStructuredNotes().getRailway(), "SNCF");
        assertEquals(l.getStructuredNotes().getNotes(), "Fast");
        assertEquals(l.getTotalRuntime(), 36000000L);
        assertEquals(l.getRuntimeOnDay("2023-05-05"), 36000000L, "the runtime is not on the file's last run day");
        assertEquals(l.getRuntimeToday(), 0L, "the file's runtime was filed under today");
        assertEquals(model.getLocByName(bare).getTotalRuntime(), 0L, "a locomotive with no last run day took the runtime");
    }

    /**
     * The file's runtime counts only when its last run day is later than this locomotive's: then the total is the larger of
     * the two and the difference is on that day, the other days untouched.  An earlier, equal, blank, missing or unreadable
     * day, or a file in the old format, leaves the runtime alone, and the same file twice changes nothing the second time.
     *
     * MUTATION: take the file's runtime whatever its day, add the two runtimes, or file the difference on another day, and
     * this fails.
     */
    @Test
    public void testALaterLastRunRaisesTheRuntimeAndNothingElseDoes()
    {
        String name = "FR049 runner";
        MarklinLocomotive l = make(name, decoderType.MFX, 31);

        l.getHistoricalOperatingTime().put("2024-01-01", 3600000L);
        l.getHistoricalOperatingTime().put("2024-01-02", 1800000L);

        Map<String, Long> days = new HashMap<>(l.getHistoricalOperatingTime());

        String same = "MFX,31,0,5:00:00,0,0,,";

        // NOT LATER: earlier, equal, blank, an unreadable day, one in the future, and the old format with no day at all
        for (String day : new String[] {"2023-12-31", "2024-01-02", "", "someday", "2999-01-01", "2024-1-5"})
        {
            importText(HEADER + name + ",," + same + "," + day + "\n");

            assertEquals(l.getTotalRuntime(), 5400000L, "a last run day of \"" + day + "\" changed the runtime");
            assertEquals(l.getHistoricalOperatingTime(), days, "a last run day of \"" + day + "\" changed the days");
        }

        importText(OLD + name + ",," + same + "\n");

        assertEquals(l.getTotalRuntime(), 5400000L, "a file in the old format changed the runtime");

        // LATER, BUT NOT MORE: the total is the larger of the two
        importText(HEADER + name + ",,MFX,31,0,1:00:00,0,0,,,2024-02-02\n");

        assertEquals(l.getTotalRuntime(), 5400000L, "a smaller runtime lowered the total");

        // LATER AND MORE: the difference is filed on the file's day
        importText(HEADER + name + ",,MFX,31,0,5:00:00,0,0,,,2024-03-03\n");

        assertEquals(l.getTotalRuntime(), 18000000L, "a later last run did not raise the runtime to the file's (or it was added on top)");
        assertEquals(l.getRuntimeOnDay("2024-01-01"), 3600000L);
        assertEquals(l.getRuntimeOnDay("2024-01-02"), 1800000L);
        assertEquals(l.getRuntimeOnDay("2024-03-03"), 18000000L - 5400000L, "the difference is not on the file's day");

        // THE SAME FILE AGAIN: its day is no longer later
        importText(HEADER + name + ",,MFX,31,0,5:00:00,0,0,,,2024-03-03\n");

        assertEquals(l.getTotalRuntime(), 18000000L, "importing the same file twice added its runtime twice");
    }

    /**
     * A locomotive that has never run takes the file's runtime: there is no last run day of its own to be later than.
     *
     * MUTATION: refuse the runtime when the locomotive has no last run day, and this fails.
     */
    @Test
    public void testALocomotiveThatNeverRanTakesTheFilesRuntime()
    {
        String name = "FR049 never ran";
        MarklinLocomotive l = make(name, decoderType.MM2, 16);

        assertNull(l.getLastRunDay(), "precondition: the locomotive has run");

        importText(HEADER + name + ",,MM2,16,0,2:00:00,0,0,,,2024-06-06\n");

        assertEquals(l.getTotalRuntime(), 7200000L, "a locomotive that never ran was refused the file's runtime");
        assertEquals(l.getRuntimeOnDay("2024-06-06"), 7200000L);
    }

    /**
     * A last run day that cannot be used (unreadable or in the future) is counted, for a merged row and for a new one, and
     * a new locomotive with no day at all is counted apart: the operator is told which, rather than "no date" for both.
     *
     * MUTATION: stop counting an unusable day, or count it as no day, and this fails.
     */
    @Test
    public void testAnUnusableLastRunDayIsCountedApartFromNoDay()
    {
        String old = "FR049 dated";
        make(old, decoderType.MM2, 17);
        made.add("FR049 dated new");
        made.add("FR049 undated new");

        MarklinLocomotiveCsv.Result result = importText(HEADER
            + old + ",,MM2,17,0,2:00:00,0,0,,,2999-01-01\n"
            + "FR049 dated new,,MM2,18,0,2:00:00,0,0,,,not a day\n"
            + "FR049 undated new,,MM2,19,0,2:00:00,0,0,,,\n");

        assertEquals(result.badLastRun, 2, "the unusable days are not counted");
        assertEquals(result.addedWithoutRuntime, 1, "the locomotive with no day is not counted apart");
        assertEquals(model.getLocByName(old).getTotalRuntime(), 0L);
    }

    /**
     * Minutes or seconds over 59, or an absurd number of hours, make a runtime unreadable: only the runtime is left alone
     * (a total that is too big can never come down), the rest of the row is used, and the row is counted.
     *
     * MUTATION: skip the row for its runtime, or stop checking minutes, seconds or hours, and this fails.
     */
    @Test
    public void testARuntimeThatCannotBeRightIsLeftAloneAndTheRowIsKept()
    {
        String name = "FR049 timed";
        MarklinLocomotive l = make(name, decoderType.MM2, 20);

        int bad = 0;

        for (String runtime : new String[] {"0:75:00", "1:00:61", "99999999:00:00", "xx"})
        {
            MarklinLocomotiveCsv.Result result = importText(HEADER + name + ",,MM2,20,0," + runtime + ",0,0,Rail" + bad + ",,2024-06-06\n");

            assertEquals(result.badRuntime, 1, "a runtime of " + runtime + " is not counted as unreadable");
            assertTrue(result.skipped.isEmpty(), "a runtime of " + runtime + " skipped the row");
            assertEquals(result.merged, 1);
            assertEquals(l.getTotalRuntime(), 0L, "a runtime of " + runtime + " was filed");

            bad++;
        }

        importText(HEADER + name + ",,DCC,21,0,0:75:00,0,0,,,2024-06-06\n");

        assertEquals(l.getAddress(), 21, "the address of a row with a bad runtime was not taken");
    }

    /**
     * An existing name takes the file's address and decoder, through the model's own door; a Multi-Unit row converts
     * nothing; an address the decoder cannot have is left and counted; the same file twice changes nothing the second time.
     *
     * MUTATION: leave the address alone, call setAddress instead of changeLocAddress, turn a row with the Multi-Unit
     * decoder into a change, or stop counting the address no decoder takes, and this fails.
     */
    @Test
    public void testAddressAndDecoderFollowTheFile()
    {
        String name = "FR049 moved";
        MarklinLocomotive l = make(name, decoderType.MFX, 33);

        l.setArrivalFunc(20);
        l.setDepartureFunc(10);

        assertEquals(l.getArrivalFunc(), Integer.valueOf(20), "precondition: an MFX locomotive has function 20");

        MarklinLocomotiveCsv.Result result = importText(OLD + name + ",,MM2,44,0,0:00:00,0,0,,\n");

        assertEquals(result.readdressed, Arrays.asList(name));
        assertEquals(l.getDecoderType(), decoderType.MM2);
        assertEquals(l.getAddress(), 44);
        assertEquals(model.getLocByName(name), l, "the locomotive was replaced rather than changed");
        assertNull(l.getArrivalFunc(), "an arrival function beyond the new decoder's functions was kept");
        assertNull(l.getDepartureFunc(), "a departure function beyond the new decoder's functions was kept");

        // THE SAME FILE AGAIN
        assertTrue(importText(OLD + name + ",,MM2,44,0,0:00:00,0,0,,\n").readdressed.isEmpty());

        // A MULTI-UNIT ROW
        assertTrue(importText(OLD + name + ",,Multi-Unit,55,0,0:00:00,0,0,,\n").readdressed.isEmpty());
        assertEquals(l.getDecoderType(), decoderType.MM2);
        assertEquals(l.getAddress(), 44, "a Multi-Unit row changed an existing locomotive");

        // AN ADDRESS NO DECODER OF THAT KIND TAKES
        MarklinLocomotiveCsv.Result refused = importText(OLD + name + ",,MM2,500,0,0:00:00,0,0,,\n");

        assertTrue(refused.readdressed.isEmpty());
        assertEquals(l.getAddress(), 44, "an address out of range was taken");
        assertEquals(refused.merged, 1, "the row was not merged");
        assertEquals(refused.addressInvalid, 1, "an address no decoder takes is not counted");
    }

    /**
     * A new Multi-Unit row, or a row for a locomotive linked into a multi-unit ("MU MFX"), in any language the program
     * speaks, is left out and counted apart with its reason: it would arrive as a plain locomotive with its links gone.  An
     * existing name with such a row is merged, keeps its decoder, and is counted with them.  Other text ("Steam") is
     * not a multi-unit, only unreadable.
     *
     * MUTATION: read any text as a multi-unit, a linked row as a plain one, or an existing row's decoder as nothing worth
     * counting, and this fails.
     */
    @Test
    public void testMultiUnitRowsAreLeftOutAndCounted()
    {
        String name = "FR049 head";
        MarklinLocomotive l = make(name, decoderType.MFX, 21);

        String german = java.util.ResourceBundle.getBundle("org.traincontrol.resources.messages", java.util.Locale.GERMAN)
            .getString("loc.multiUnit");

        MarklinLocomotiveCsv.Result result = importText(OLD
            + "FR049 mu one,,Multi-Unit,3,0,0:00:00,0,0,,\n"
            + "FR049 mu two,,MU MFX,22,0,0:00:00,0,0,,\n"
            + name + ",,MU MFX,99,0,0:00:00,0,0,Rail,\n"
            + "FR049 steam,,Steam,23,0,0:00:00,0,0,,\n"
            + "FR049 mu three,," + german + ",24,0,0:00:00,0,0,,\n");

        assertNull(model.getLocByName("FR049 mu one"));
        assertNull(model.getLocByName("FR049 mu two"), "a linked row arrived as a plain locomotive");
        assertNull(model.getLocByName("FR049 mu three"), "a German multi-unit row arrived as a plain locomotive");
        assertNull(model.getLocByName("FR049 steam"));
        assertEquals(result.leftOutMultiUnit, 4, "the new multi-unit rows and the existing locomotive's are not counted together");
        assertEquals(result.skipped.get(2), Why.MULTI_UNIT);
        assertEquals(result.skipped.get(3), Why.MULTI_UNIT);
        assertEquals(result.skipped.get(5), Why.UNREADABLE, "Steam was taken for a multi-unit");
        assertEquals(result.skipped.get(6), Why.MULTI_UNIT);
        assertEquals(result.merged, 1);
        assertEquals(l.getAddress(), 21, "a linked row changed an existing locomotive's address");
        assertEquals(l.getStructuredNotes().getRailway(), "Rail");
    }

    /**
     * A name of the longest length rename allows is read; one longer is not.
     *
     * MUTATION: refuse the longest name, or accept a longer one, and this fails.
     */
    @Test
    public void testTheLongestNameRenameAllowsIsRead()
    {
        String longest = "FR049 xxxxxxxxxxxxxxxxxxxxxxxx";
        String tooLong = longest + "y";
        made.add(longest);

        MarklinLocomotiveCsv.Result result = importText(OLD + longest + ",,MM2,23,0,0:00:00,0,0,,\n" + tooLong
            + ",,MM2,24,0,0:00:00,0,0,,\n");

        assertEquals(longest.length(), 30);
        assertNotNull(model.getLocByName(longest), "a name of thirty letters was not read");
        assertNull(model.getLocByName(tooLong));
        assertEquals(result.skipped.get(3), Why.UNREADABLE);
    }

    /**
     * Excel on a European Windows saves with semicolons: the separator is taken from the header, and a field holding the
     * other separator stays whole in quotes.
     *
     * MUTATION: always split on commas, and this fails.
     */
    @Test
    public void testAFileWithSemicolonsIsRead()
    {
        String name = "FR049 semi";
        made.add(name);

        MarklinLocomotiveCsv.Result result = importText(OLD.replace(',', ';')
            + name + ";;MM2;25;0;1:00:00;0;0;Rail, with comma;\"Note; with semicolon\"\n");

        assertEquals(result.added, 1, "a semicolon file was not read");
        assertEquals(model.getLocByName(name).getStructuredNotes().getNotes(), "Note; with semicolon");
        assertEquals(model.getLocByName(name).getStructuredNotes().getRailway(), "Rail, with comma");
    }

    /**
     * Rows Excel pads a sheet with (only separators) are no rows and are not counted as skipped; a file with carriage
     * returns for line ends reads like any other.
     *
     * MUTATION: count a row of nothing but separators, or read a carriage return as part of a field, and this fails.
     */
    @Test
    public void testBlankRowsAndACarriageReturnFileAreRead()
    {
        String name = "FR049 padded";
        made.add(name);

        MarklinLocomotiveCsv.Result result = importText(OLD.replace(',', ';') + name + ";;MM2;26;0;0:00:00;0;0;;\n;;;;;;;;;\n;;;;;;;;;\n");

        assertEquals(result.added, 1);
        assertTrue(result.skipped.isEmpty(), "blank rows were counted: " + result.skipped);

        String old = "FR049 cr only";
        made.add(old);

        assertEquals(importText((OLD + old + ",,MM2,27,0,0:00:00,0,0,,\n").replace('\n', '\r')).added, 1,
            "a file with carriage returns for line ends was not read");
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

        importText(OLD + name + ",,MM2,5,70,0:00:00,1940,1990,Theirs,From the file\n");

        assertEquals(l.getPreferredSpeed(), 33, "a preset speed already set was replaced");
        assertEquals(l.getStructuredNotes().getEndYear(), 1980, "an end year already set was replaced");
        assertEquals(l.getStructuredNotes().getRailway(), "Mine", "a railway already set was replaced");
        assertEquals(l.getStructuredNotes().getStartYear(), 1940, "an empty start year was not filled");
        assertEquals(l.getStructuredNotes().getNotes(), "From the file", "empty notes were not filled");

        String bare = "FR049 bare";
        MarklinLocomotive b = make(bare, decoderType.MM2, 6);

        importText(OLD + bare + ",,MM2,6,70,0:00:00,0,0,,\n");

        assertEquals(b.getPreferredSpeed(), 70, "an empty preset speed was not filled");
    }

    /**
     * A train with a speed is never re-addressed: the plan names it with the power on, and with the power off too, since
     * the old decoder resumes on Go while the stop went to the new address.  A standing train is not named.
     *
     * MUTATION: ask no speed, or look at the power first, and this fails.
     */
    @Test
    public void testATrainWithASpeedIsNamedWhateverThePower() throws Exception
    {
        String name = "FR049 rolling";
        MarklinLocomotive l = make(name, decoderType.MM2, 36);

        MarklinLocomotiveCsv csv = MarklinLocomotiveCsv.read(OLD + name + ",,DCC,37,0,0:00:00,0,0,,\n");

        // The simulated station starts with the power on and does not echo a stop, so the power is set where the model keeps it.
        java.lang.reflect.Field power = Reflect.field(MarklinControlStation.class, "powerState");

        try
        {
            assertTrue(model.getPowerState(), "precondition: the power is on");
            assertTrue(csv.movingToReaddress(csv.plan(model), model).isEmpty(), "a standing train is named");

            l.setSpeed(30);
            l.waitForSpeedAtOrAbove(1);

            assertEquals(csv.movingToReaddress(csv.plan(model), model), Arrays.asList(name), "a moving train the file would re-address is not named");
            assertTrue(moving(OLD + name + ",,MM2,36,0,0:00:00,0,0,,\n").isEmpty(),
                "a moving train the file would not re-address is named");

            power.setBoolean(model, false);

            assertTrue(l.getSpeed() > 0, "precondition: the train still has its speed");
            assertEquals(csv.movingToReaddress(csv.plan(model), model), Arrays.asList(name), "a train stopped by the power with a speed set is not named");
        }
        finally
        {
            power.setBoolean(model, true);

            l.setSpeed(0);
            l.waitForSpeedBelow(1);
        }
    }

    /**
     * An import never breaks a multi-unit: a member given the address of its head keeps its address and is counted, and the
     * consist stays whole.
     *
     * MUTATION: let the file give a member its head's address, and this fails.
     */
    @Test
    public void testAMemberKeepsItsAddressWhenTheFileWouldBreakItsConsist()
    {
        MarklinLocomotive head = make("FR049 consist head", decoderType.MM2, 40);
        MarklinLocomotive member = make("FR049 consist member", decoderType.MM2, 41);

        Map<String, Double> link = new HashMap<>();

        link.put(member.getName(), 1.0);

        assertEquals(head.setLinkedLocomotives(link), 1, "precondition: the consist was not made");

        MarklinLocomotiveCsv.Result result = importText(OLD + member.getName() + ",,MM2,40,0,0:00:00,0,0,,\n");

        assertEquals(result.kept.size(), 1, "the address change that breaks a consist is not counted");
        assertEquals(result.kept.get(0).name, member.getName(), "the locomotive that keeps its address is not named");
        assertEquals(result.kept.get(0).why, MarklinLocomotiveCsv.Keep.CONSIST);
        assertEquals(member.getAddress(), 41, "a member was given its head's address");
        assertTrue(head.getLinkedLocomotiveNames().containsKey(member.getName()), "the consist lost its member");
    }

    /**
     * The addresses are decided once, each change judged against the addresses as the earlier ones leave them: when the
     * file moves a member of a consist away first, a train with a speed that is moved onto the freed address is in the
     * plan, and is named, not re-addressed unasked.
     *
     * MUTATION: judge every change against the addresses as they are now, and this fails.
     */
    @Test
    public void testAClashFreedByAnEarlierRowStillMeetsTheMovingCheck() throws Exception
    {
        MarklinLocomotive head = make("FR049 plan head", decoderType.MM2, 60);
        MarklinLocomotive first = make("FR049 plan first", decoderType.MM2, 61);
        MarklinLocomotive second = make("FR049 plan second", decoderType.MM2, 62);

        Map<String, Double> link = new HashMap<>();

        link.put(first.getName(), 1.0);
        link.put(second.getName(), 1.0);

        assertEquals(head.setLinkedLocomotives(link), 2, "precondition: the consist was not made");

        second.setSpeed(40);
        second.waitForSpeedAtOrAbove(1);

        try
        {
            MarklinLocomotiveCsv csv = MarklinLocomotiveCsv.read(OLD
                + first.getName() + ",,MM2,63,0,0:00:00,0,0,,\n"
                + second.getName() + ",,MM2,61,0,0:00:00,0,0,,\n");

            MarklinLocomotiveCsv.Plan plan = csv.plan(model);

            assertEquals(plan.moves.size(), 2, "the second change was judged against the addresses as they are now");
            assertEquals(csv.movingToReaddress(plan, model), Arrays.asList(second.getName()), "the moving train in the plan is not named");
        }
        finally
        {
            second.setSpeed(0);
            second.waitForSpeedBelow(1);
        }
    }

    /**
     * The plan says which locomotives would end up sharing an address with another, so the question can name them.
     *
     * MUTATION: leave the new locomotive out of the count, and this fails.
     */
    @Test
    public void testTheLocomotivesThatWouldShareAnAddressAreNamed()
    {
        make("FR049 sits on 50", decoderType.MM2, 50);
        made.add("FR049 arrives on 50");

        MarklinLocomotiveCsv csv = MarklinLocomotiveCsv.read(OLD + "FR049 arrives on 50,,MM2,50,0,0:00:00,0,0,,\n");

        boolean found = false;

        for (MarklinLocomotiveCsv.Shared shared : csv.sharedAddresses(model, csv.plan(model)))
        {
            if (shared.names.contains("FR049 arrives on 50"))
            {
                found = true;
                assertTrue(shared.names.contains("FR049 sits on 50"), "the locomotive it shares with is not named: " + shared.names);
            }
        }

        assertTrue(found, "a new locomotive on an address another holds is not named");
    }

    /**
     * A row that cannot be read is skipped and its line is named; the rows around it still come in.  Lines count the
     * physical lines of the file, so a quoted note over several lines moves the next row down.  A last run day nobody can
     * read is no reason to skip a row.
     *
     * MUTATION: count rows rather than lines, stop at the first bad row, or skip a row for its last run day, and this fails.
     */
    @Test
    public void testRowsThatCannotBeReadAreSkippedAndNamed()
    {
        String ok1 = "FR049 ok one";
        String ok2 = "FR049 ok two";
        made.add(ok1);
        made.add(ok2);

        String csv = HEADER
            + ok1 + ",,MFX,41,0,0:00:00,0,0,,\"a\nb\nc\",\n"                          // line 2, to line 4
            + "FR049 bad address,,MFX,abc,0,0:00:00,0,0,,,\n"                         // line 5
            + "FR049 no such address,,MFX,0,0,0:00:00,0,0,,,\n"                       // line 6
            + "FR049 bad runtime,,MFX,42,0,xx,0,0,,,\n"                               // line 7, kept: only the runtime is bad
            + "FR049 short row,,MFX,43\n"                                             // line 8
            + "FR049 multi,,Multi-Unit,44,0,0:00:00,0,0,,,\n"                         // line 9
            + ",,MFX,45,0,0:00:00,0,0,,,\n"                                           // line 10
            + "FR049 speed,,MFX,46,101,0:00:00,0,0,,,\n"                              // line 11
            + ok1 + ",,MFX,47,0,0:00:00,0,0,,,\n"                                     // line 12 (the name twice)
            + "\n"                                                                    // line 13, blank: not a row
            + ok2 + ",,MM2,48,0,0:00:00,0,0,,,not a date\n"                           // line 14
            + "FR049 open quote,,MFX,49,0,0:00:00,0,0,,\"never closed\n";             // line 15 to the end

        MarklinLocomotiveCsv csv0 = MarklinLocomotiveCsv.read(csv);

        assertTrue(csv0.isLocomotiveFile());

        List<Integer> expected = Arrays.asList(5, 6, 8, 9, 10, 11, 12, 15);

        assertEquals(new ArrayList<>(csv0.skipped(model).keySet()), expected, "the lines the preview names are not the unusable ones");
        assertEquals(csv0.countNew(model), 3);

        int locsBefore = model.getLocomotives().size();

        MarklinLocomotiveCsv.Result result = csv0.apply(model, csv0.plan(model));

        made.add("FR049 bad runtime");

        assertEquals(new ArrayList<>(result.skipped.keySet()), expected, "the lines the result names are not the unusable ones");
        assertEquals(result.skipped.get(6), Why.ADDRESS);
        assertEquals(result.skipped.get(9), Why.MULTI_UNIT);
        assertEquals(result.added, 3);
        assertEquals(result.badRuntime, 1);
        assertEquals(model.getLocomotives().size(), locsBefore + 3, "a skipped row added a locomotive");
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

        importText("\uFEFF" + OLD.replace("\n", "\r\n") + name + ",,MFX,52,10,0:01:00,1900,1910,GWR,Plain\r\n");

        Locomotive l = model.getLocByName(name);

        assertNotNull(l);
        assertEquals(l.getStructuredNotes().getNotes(), "Plain", "a carriage return stayed on the last field");
    }

    /**
     * A train that begins to move after the plan was made is not re-addressed, and its address stays held for the rows
     * after it: the next row does not land on it (it would put two locomotives on one address, or drop a consist link).
     *
     * MUTATION: leave out the speed check in `apply`, or the held address, and this fails.
     */
    @Test
    public void testATrainThatBeginsToMoveIsKeptAndItsAddressStaysHeld() throws Exception
    {
        MarklinLocomotive moving = make("FR049 late mover", decoderType.MM2, 30);
        MarklinLocomotive next = make("FR049 late next", decoderType.MM2, 32);

        MarklinLocomotiveCsv csv = MarklinLocomotiveCsv.read(OLD + moving.getName() + ",,DCC,31,0,0:00:00,0,0,,\n"
            + next.getName() + ",,MM2,30,0,0:00:00,0,0,,\n");

        MarklinLocomotiveCsv.Plan plan = csv.plan(model);

        assertEquals(plan.moves.size(), 2, "precondition: both changes are in the plan");

        moving.setSpeed(40);
        moving.waitForSpeedAtOrAbove(1);

        try
        {
            MarklinLocomotiveCsv.Result result = csv.apply(model, plan);

            assertEquals(moving.getAddress(), 30, "a train that began to move was re-addressed");
            assertEquals(next.getAddress(), 32, "the next row landed on the address a moving train still holds");
            assertEquals(result.kept.size(), 2);
            assertEquals(result.kept.get(0).why, MarklinLocomotiveCsv.Keep.MOVING);
            assertEquals(result.kept.get(1).why, MarklinLocomotiveCsv.Keep.HELD);
        }
        finally
        {
            moving.setSpeed(0);
            moving.waitForSpeedBelow(1);
        }
    }

    /**
     * Only rows really left out are counted as multi-unit rows left out: an existing name with "Steam", with no decoder,
     * with no decoder column, or with a linked label at the address it already has was merged and left nothing out.
     *
     * MUTATION: count every existing row that is not plain, and this fails.
     */
    @Test
    public void testOnlyRowsReallyLeftOutAreCounted()
    {
        make("FR049 left steam", decoderType.MFX, 24);
        make("FR049 left blank", decoderType.MFX, 25);
        make("FR049 left same", decoderType.MFX, 26);
        make("FR049 left other", decoderType.MFX, 27);
        make("FR049 left nocolumn", decoderType.MFX, 29);

        MarklinLocomotiveCsv.Result result = importText(OLD
            + "FR049 left steam,,Steam,24,0,0:00:00,0,0,,\n"
            + "FR049 left blank,,,25,0,0:00:00,0,0,,\n"
            + "FR049 left same,,MU MFX,26,0,0:00:00,0,0,,\n"
            + "FR049 left other,,MU MFX,99,0,0:00:00,0,0,,\n");

        assertEquals(result.merged, 4);
        assertEquals(result.leftOutMultiUnit, 1, "rows that left nothing out were counted");

        assertEquals(importText("Name,Address,Notes\nFR049 left nocolumn,29,Note\n").leftOutMultiUnit, 0,
            "a file with no decoder column left something out");
    }

    /**
     * After UTF-8 fails a file is read in the machine's own code page when that is a single-byte one, else in Windows-1252.
     *
     * MUTATION: always answer Windows-1252, and this fails.
     */
    @Test
    public void testTheMachinesCodePageIsTheFallbackWhenItIsSingleByte()
    {
        assertEquals(MarklinLocomotiveCsv.fallbackFor(Charset.forName("windows-1250")), Charset.forName("windows-1250"));
        assertEquals(MarklinLocomotiveCsv.fallbackFor(Charset.forName("UTF-8")), Charset.forName("windows-1252"));
        assertEquals(MarklinLocomotiveCsv.fallbackFor(Charset.forName("UTF-16")), Charset.forName("windows-1252"));
    }

    /**
     * The export's columns and the saved form of a locomotive are as they were, apart from the LastRun column Adam asked
     * for: the header the importer reads, the saved record's version and fields (a change there is a change to
     * LocDB.data), and an imported locomotive saves and loads like any other.
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

        importText(HEADER + name + ",,MFX,61,20,2:00:00,1930,1940,LNER,Saved,2024-04-04\n");

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
