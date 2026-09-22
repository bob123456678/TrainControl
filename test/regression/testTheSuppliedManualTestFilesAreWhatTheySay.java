package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Every sample file shipped for a manual test still demonstrates the thing its entry says it does.
 *
 * **Adam, 2026-09-22:** *"Make sure all the MTs have clear, step by step tests to run, and that sample
 * files are supplied where needed."*  A file is supplied so that a step gets RUN rather than skipped -
 * `MT-470`'s step 7 said *"a legacy setup, if you have one to hand"* for a week, which is a step nobody
 * runs.  The cost of supplying one is this: a sample file that has quietly stopped showing what it was
 * made to show is worse than no file at all, because the step is then run and passes for the wrong
 * reason.
 *
 * **What this holds is the FILE, not the door.**  Whether unticking Atomic Routes is refused is
 * `ui.testNonAtomicRoutesNeedTheirLengths`'s business, and whether an import gap-fills is
 * `core.testASecondImportFillsGapsAndDoesNotOverwrite`'s.  This asks one question of each file: does
 * it still carry the state its entry sends Adam to look at?  A rule that changes what counts as
 * unmeasured would leave the doors' own tests green and make MT-470 step 7 meaningless, and nothing
 * would say so.
 *
 * **Read as bytes wherever it can be**, because a file that has to be parsed by the thing it is testing
 * cannot testify about the thing it is testing.  The two `MT-470` files are the exception: what they
 * claim IS what `unmeasuredTrackThatCouldBeReleased` answers about them, so that is what is asked.
 *
 * MUTATION: swap the two MT-470 file names in the claim below and both assertions fail, each naming
 * the file it read - the measured one is asserted to have nothing unmeasured and the unmeasured one to
 * have some, so a pair that had stopped differing could not pass either way round.
 *
 * @author Adam
 */
public class testTheSuppliedManualTestFilesAreWhatTheySay
{
    /** Where a manual test's sample files live (`traincontrol-mt-files-provided`). */
    private static final File FILES = new File("docs" + File.separator + "manual-tests"
        + File.separator + "files");

    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, false);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Every file an entry names is there, and no file is shipped that no entry names.
     *
     * Both directions, because they fail differently: a named file that is absent is a step that
     * cannot be run, and a shipped file nobody names is one nobody will ever open - and the second is
     * how a file comes to be stale without anybody noticing.
     *
     * @throws Exception from reading the records
     */
    @Test
    public void testEveryShippedFileIsNamedByItsEntryAndEveryNamedFileIsThere() throws Exception
    {
        assertTrue(FILES.isDirectory(), "there is no " + FILES + ", so no manual test can supply one");

        String tests = new String(Files.readAllBytes(
            new File("docs" + File.separator + "manual-tests", "tests.md").toPath()),
            StandardCharsets.UTF_8);

        String[] shipped = FILES.list();

        assertNotNull(shipped, "could not list " + FILES);

        assertTrue(shipped.length > 0, "no sample files are shipped at all");

        for (String name : shipped)
        {
            assertTrue(tests.contains(name),
                "the file " + name + " is shipped for a manual test and no entry names it, so nobody"
                + " will open it and nothing will notice when it stops being what it was made to be."
                + "  Either an entry should send Adam to it, or it should go");

            // NAMED FOR ITS ENTRY, which is what makes the two rules above checkable at a glance.
            assertTrue(name.startsWith("MT-"),
                "the file " + name + " does not begin with the MT number it belongs to, so there is no"
                + " way to tell from the folder which entry it serves");
        }
    }

    /**
     * MT-470's two legacy setups still differ in exactly the one thing the step turns on.
     *
     * Step 7 loads each and expects opposite answers: the unmeasured file refused at both doors, the
     * measured one accepted at both.  A pair that had stopped differing would pass the step whichever
     * file he loaded, and the step would be checking nothing.
     *
     * Both carry `atomicRoutes` false, which is the state the step says to load them in - loading one
     * that already had atomic routes on would not exercise the load door at all.
     *
     * @throws Exception from reading the files
     */
    @Test
    public void testTheTwoLegacySetupsForAtomicRoutesStillDisagree() throws Exception
    {
        Layout unmeasured = read("MT-470-legacy-unmeasured.json");
        Layout measured = read("MT-470-legacy-measured.json");

        assertFalse(unmeasured.isAtomicRoutes(),
            "MT-470-legacy-unmeasured.json has Atomic Routes ON, so loading it does not put the"
            + " railway in the state step 7 is about");

        assertFalse(measured.isAtomicRoutes(),
            "MT-470-legacy-measured.json has Atomic Routes ON, so loading it does not put the railway"
            + " in the state step 7 is about");

        List<String> hasSome = unmeasured.unmeasuredTrackThatCouldBeReleased();

        List<String> hasNone = measured.unmeasuredTrackThatCouldBeReleased();

        assertFalse(hasSome.isEmpty(),
            "MT-470-legacy-unmeasured.json has nothing left unmeasured, so step 7's refusing half"
            + " cannot be refused and the step passes for the wrong reason");

        assertEquals(hasNone.size(), 0,
            "MT-470-legacy-measured.json still has unmeasured track that could be released, so step"
            + " 7's accepting half is refused too - and a pair that is refused both ways round reads"
            + " as a rule that refuses whatever the railway looks like, which is the failure Adam's"
            + " standing rule is about.  Still unmeasured: " + hasNone);

        // AND THEY ARE THE SAME RAILWAY OTHERWISE, or the pair proves nothing about measurement: two
        // files differing in their track as well would be two different tests wearing one number.
        assertEquals(measured.getPoints().size(), unmeasured.getPoints().size(),
            "the two MT-470 files describe railways of different sizes, so the difference step 7 sees"
            + " is not the one it is about");

        assertEquals(measured.getEdges().size(), unmeasured.getEdges().size(),
            "the two MT-470 files carry different numbers of rails, so the difference step 7 sees is"
            + " not the one it is about");
    }

    /**
     * MT-452's and MT-453's files still carry the one broken thing each entry sends him to look at.
     *
     * Read as text rather than parsed: both files exist to be REFUSED in one particular way by the
     * loader, so asking the loader what it thinks of them would be asking the accused.
     *
     * @throws Exception from reading the files
     */
    @Test
    public void testTheTwoBrokenConfigurationsAreStillBroken() throws Exception
    {
        String placed = text("MT-452-placed-train-not-in-database.json");

        org.json.JSONObject train = new org.json.JSONObject(placed);

        String named = null;

        for (Object o : train.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            if (point.has("loc") && model.getLocByName(point.getJSONObject("loc")
                .optString("name", "")) == null)
            {
                named = point.getJSONObject("loc").optString("name", "");

                break;
            }
        }

        assertNotNull(named,
            "MT-452-placed-train-not-in-database.json places no train this database has not got, so"
            + " loading it drops nothing and the entry's step checks nothing.  Either a locomotive of"
            + " that name has since been added to the database, or the file has been replaced");

        String locks = text("MT-453-lock-naming-missing-track.json");

        org.json.JSONObject held = new org.json.JSONObject(locks);

        java.util.Set<String> rails = new java.util.LinkedHashSet<>();

        for (Object o : held.getJSONArray("edges"))
        {
            org.json.JSONObject edge = (org.json.JSONObject) o;

            rails.add(edge.getString("start") + ">" + edge.getString("end"));
        }

        String dangling = null;

        for (Object o : held.getJSONArray("edges"))
        {
            org.json.JSONObject edge = (org.json.JSONObject) o;

            for (Object l : edge.optJSONArray("lockedges") == null
                ? new org.json.JSONArray() : edge.getJSONArray("lockedges"))
            {
                org.json.JSONObject lock = (org.json.JSONObject) l;

                String names = lock.getString("start") + ">" + lock.getString("end");

                if (!rails.contains(names)) dangling = names;
            }
        }

        assertNotNull(dangling,
            "MT-453-lock-naming-missing-track.json holds no lock naming track the file does not"
            + " contain, so nothing is dropped and nothing is logged loudly - which is the whole of"
            + " what its entry asks Adam to watch for");
    }

    /**
     * The two shipped LAYOUT folders are still shaped the way the steps that name them assume.
     *
     * MT-380 sends Adam to a ONE-page layout with nothing set up, and the whole point of its import
     * step is that four of the export's five pages have no home here.  A second page appearing in it -
     * or an autonomy setup - would make the step pass with nothing left out and nothing to warn about.
     *
     * MT-244's is the five-page one the other was trimmed from, and its step needs it to have no
     * autonomy setup inside the layout folder either.
     *
     * @throws Exception from reading the folders
     */
    @Test
    public void testTheTwoShippedLayoutsStillHaveTheShapeTheirStepsAssume() throws Exception
    {
        File onePage = new File(FILES, "MT-380-one-page-layout");

        File pages = new File(new File(new File(onePage, "layout"), "config"), "gleisbilder");

        assertTrue(pages.isDirectory(), "MT-380's layout has no pages folder at " + pages);

        List<String> drawn = new java.util.ArrayList<>();

        for (String name : pages.list())
        {
            if (name.endsWith(".cs2")) drawn.add(name);
        }

        assertEquals(drawn.size(), 1,
            "MT-380's layout has " + drawn + " rather than one page, so its import step leaves nothing"
            + " out and the warning it is about has nothing to name");

        String index = text("MT-380-one-page-layout" + File.separator + "layout" + File.separator
            + "config" + File.separator + "gleisbild.cs2");

        assertEquals(count(index, ".name="), 1,
            "MT-380's layout index still names several pages, so the arrows on the page it kept index"
            + " into a list that is not there.  The index and the folder must agree");

        // NO ARROWS, because one holds a page's POSITION in the name-sorted list (behaviour.md 8) and
        // a one-page layout has nowhere for one to point.  The application re-aims them when the set
        // of pages changes; this folder was trimmed with a text editor, so it cannot.
        String only = text("MT-380-one-page-layout" + File.separator + "layout" + File.separator
            + "config" + File.separator + "gleisbilder" + File.separator + drawn.get(0));

        assertEquals(count(only, ".typ=pfeil"), 0,
            "MT-380's one page still carries page-link tiles, which point at pages this layout has not"
            + " got - a sample file shipping the defect behaviour.md section 8 is about");

        for (String folder : new String[] {"MT-380-one-page-layout",
            "MT-244-layout-without-autonomy-setup"})
        {
            File setup = new File(new File(new File(FILES, folder), "layout"), "config");

            assertFalse(new File(setup, "autonomy").exists(),
                folder + " carries an autonomy setup inside its layout, and both entries that name it"
                + " start from a layout with nothing set up");
        }
    }

    /**
     * @param text what to look in
     * @param needle what to look for
     * @return how many times it occurs
     */
    private static int count(String text, String needle)
    {
        int n = 0;

        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length()))
        {
            n++;
        }

        return n;
    }

    /**
     * Reads one of the shipped configurations as a Layout.
     *
     * @param name the file
     * @return the layout it describes
     * @throws Exception from reading it
     */
    private static Layout read(String name) throws Exception
    {
        Layout layout = Layout.fromJSON(text(name), model);

        assertNotNull(layout, name + " did not parse into a layout at all");

        assertTrue(layout.isValid(),
            name + " does not load: " + layout.getInvalidReason() + ".  A sample file that will not"
            + " load is a step Adam cannot run");

        return layout;
    }

    /**
     * @param name the file
     * @return its contents
     * @throws Exception when it is not there
     */
    private static String text(String name) throws Exception
    {
        File file = new File(FILES, name);

        assertTrue(file.exists(),
            "the manual test that names " + name + " cannot be run: it is not in " + FILES);

        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
