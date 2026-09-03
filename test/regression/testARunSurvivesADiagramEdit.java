package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * What a run did is folded into the setup BEFORE the editor snapshots it, not after (OB-144).
 *
 * Adam, filed as critical: "from current config, run EN57-203 from BottomSecondary to BottomMainC.
 * Then, switch to track diagram page 2, click edit and save. EN57-203 is now back at BottomSecondary."
 *
 * **This is DW-A1 again, through a different door.** The rename door was fixed by an ORDER - capture
 * the running layout into the setup before the gesture that rekeys it - and the editor door was never
 * given the same treatment. The mechanism is identical and so is the consequence:
 *
 * 1. A run moves a locomotive. Where it ended up lives in `Point.currentLoc` and nowhere else;
 *    `captureFromLayout` is the only thing that folds it into the setup, and stopping autonomy is not
 *    one of its callers.
 * 2. The editor opens and snapshots the setup - which still says where the train started.
 * 3. Save writes that snapshot's descendant back to `configuration-&lt;name&gt;.json`.
 * 4. Closing calls `rebuildRunningLayoutFromSetup`, which loads with `captureRunningState` false and
 *    regenerates every placement from the file.
 * 5. `Layout.fromJSON` reads `loc` and calls `setLocomotive`. The train is back where it started.
 *
 * Step 4's comment is where the reasoning went wrong: "the setup is the newer of the two by
 * definition". It is newer than the setup WAS. It is older than the running layout, and that is the
 * comparison that decides whether a train teleports.
 *
 * **Why this is worse than losing a setting.** Occupancy is derived from placements - `isOccupied` is
 * `currentLoc != null` and `isPathClear` never consults the s88 - so after the teleport the model
 * believes a block is free that has a train standing in it, and Start can route another one into it.
 *
 * **What this test can and cannot do.** It cannot press Save; that lives in a window. So it performs
 * the same five steps in the same order and shows that the ORDER is the whole difference:
 * capture-then-edit keeps the run, edit-without-capturing loses it. Both orders in one test, because
 * either alone is a statement about one arrangement - together they say the difference is the order
 * and nothing else. `testTheWindowCapturesBeforeItOpensTheEditor` then checks that the window actually
 * asks for the first one, which is a separate claim from the rule being right.
 */
public class testARunSurvivesADiagramEdit
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model, not after it (OB-111).
        //
        // `init` reads the machine-global layout preference and loads whatever it names, which on
        // Adam's machine is his real railway. This class did not have it on its first run and the
        // ratchet in testSwitchingToACentralStationLayout caught it as the 57th such class - which is
        // the guard doing exactly its job, and the reason the answer is a sandbox rather than a bigger
        // number. The fixture this test actually reads is a temp copy of test/test_layout made below; the
        // sandbox is about what `init` opens behind it.
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null)
        {
            model.clearAutoLayout();
            model.stop();
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * Capturing before the editor opens keeps what the run did; not capturing loses it.
     */
    @Test
    public void testTheOrderOfTheCaptureDecidesWhetherTheRunSurvives() throws Exception
    {
        assertEquals(runThenEdit(true), "kept",
            "capturing BEFORE the editor snapshots the setup lost the run's placement anyway, so the "
            + "fix for OB-144 does not work: opening the setup editor after a run and pressing Save "
            + "still puts every train back where it started");

        assertEquals(runThenEdit(false), "lost",
            "NOT capturing kept the run's placement, which would mean this test is not reproducing "
            + "OB-144 at all - and then the assertion above proves nothing.  Either something else "
            + "now folds the running layout into the setup, or the fixture stopped modelling a run");
    }

    /**
     * The window captures the running layout before it constructs the editor.
     *
     * The test above proves the RULE. It drives both orders itself, so it says nothing about which one
     * `TrainControlUI` asks for - and the editor takes its undo point inside its own constructor, so
     * "before the editor is constructed" is the last moment the capture can happen at all.
     *
     * Bounded to `openLayoutEditor`'s body. Both terms are proved present before they are compared,
     * because an absent term's index is -1, which is less than every real one and would make a
     * reversed call pass silently - the vacuous-ordering-scan failure TST-B7 found.
     *
     * MUTATION this catches: move the `captureRunningLayout();` call below `new LayoutEditor(`. Both
     * substrings are still present, so only the ordering assertion goes red. Deleting either line
     * instead is what the presence assertions catch.
     */
    @Test
    public void testTheWindowCapturesBeforeItOpensTheEditor() throws Exception
    {
        String ui = new String(Files.readAllBytes(
            new File("src/org/traincontrol/gui/TrainControlUI.java").toPath()), StandardCharsets.UTF_8);

        // The four-argument overload is the one that does the work; the shorter ones delegate to it.
        // Anchored on its LAST parameter rather than on the whole signature, because a signature that
        // wraps is a signature whose exact text is a formatting decision - my first attempt at this
        // pinned the line break and failed on the anchor rather than on the claim.
        int methodStart = ui.indexOf("TileGraph.TileKey reveal, boolean remember)");

        assertTrue(methodStart >= 0,
            "openLayoutEditor's working overload has moved or changed shape - this test is reading the "
            + "wrong method, or none at all");

        int methodEnd = ui.indexOf("\n    public ", methodStart + 1);

        assertTrue(methodEnd > methodStart, "could not find the end of the method to bound the scan");

        String body = ui.substring(methodStart, methodEnd);

        int captured = body.indexOf("captureRunningLayout();");
        int opened = body.indexOf("new LayoutEditor(");

        assertTrue(captured >= 0,
            "captureRunningLayout() is no longer called from openLayoutEditor - OB-144 is back, "
            + "because nothing folds the run's placements into the setup before the editor snapshots "
            + "it, and Save then writes the pre-run placements back over them");

        assertTrue(opened >= 0,
            "new LayoutEditor( is no longer constructed in openLayoutEditor - the scan needs updating "
            + "for whatever replaced it");

        assertTrue(captured < opened,
            "captureRunningLayout() no longer runs before the editor is constructed - which is exactly "
            + "OB-144: the editor takes its undo point in its own constructor, so a capture after that "
            + "is too late, and every train goes back to where it stood before the run");
    }

    /**
     * A run, a stop, an editor opened and saved - with or without the capture that precedes it.
     *
     * @param captureFirst true to fold the run in before the editor snapshots, as the window now does
     * @return "kept" if the run's placement survived the rebuild, "lost" otherwise
     */
    private String runThenEdit(boolean captureFirst) throws Exception
    {
        File folder = aWorkingCopy();

        List<LayoutDiagram> pages = pagesIn(folder);

        AutonomySession session = new AutonomySession(folder);
        session.open(pages);

        String active = session.getStore().getActiveConfiguration();

        assertNotNull(active, "the sample setup has no active configuration");

        // The running layout, as loading a configuration builds it.
        model.parseAuto(session.buildConfiguration());

        org.traincontrol.automation.Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration did not build");

        // THE RUN, in the only part of it that matters here: a train ends up somewhere it did not
        // start.  Moving the locomotive between two Points is what a completed path leaves behind.
        org.traincontrol.automation.Point from = occupiedPoint(layout);

        assertNotNull(from, "no locomotive is placed in this configuration, so no run can be modelled");

        org.traincontrol.automation.Point to = emptyPoint(layout);

        assertNotNull(to, "every point is occupied, so there is nowhere for a train to have gone");

        org.traincontrol.base.Locomotive moved = from.getCurrentLocomotive();

        String movedName = moved.getName();
        String destination = to.getName();

        from.setLocomotive(null);
        to.setLocomotive(moved);

        assertFalse(layout.isRunning(), "the fixture must be stopped before an editor may open");

        // THE CAPTURE, or its absence.  This is the whole variable.
        if (captureFirst)
        {
            session.captureFromLayout(layout.toJSON(), active);
            session.saveWithoutReconciling();
        }

        // THE EDITOR OPENING.  Its constructor snapshots the setup for Cancel to restore; whatever the
        // setup says at this instant is what Save will eventually write.
        org.json.JSONObject asOpened = session.snapshotSetup();

        assertNotNull(asOpened, "the editor's undo point is empty, so this is not modelling an open");

        // THE SAVE, with no edit made at all - which is enough, and is what Adam did.
        session.save();

        // THE CLOSE, which is rebuildRunningLayoutFromSetup: load without capturing, and regenerate
        // every placement from the file.
        model.parseAuto(session.buildConfiguration());

        org.traincontrol.automation.Layout rebuilt = model.getAutoLayout();

        assertNotNull(rebuilt, "the configuration did not rebuild after the save");

        for (org.traincontrol.automation.Point point : rebuilt.getPoints())
        {
            if (point.getCurrentLocomotive() == null) continue;

            if (movedName.equals(point.getCurrentLocomotive().getName()))
            {
                return destination.equals(point.getName()) ? "kept" : "lost";
            }
        }

        return "lost";
    }

    /**
     * The window's own capture method actually captures - and refuses while trains are moving (A105).
     *
     * `runThenEdit` above tests the RULE and `testTheWindowCapturesBeforeItOpensTheEditor` tests that
     * the window asks for it, by reading the source. Nothing ran `captureRunningLayout` itself. It is
     * six conditions and a call, and every one of the six is a way for the fix to be silently absent:
     * the whole body is `if (...) return;` followed by the thing that matters.
     *
     * Both directions, because either alone is satisfiable by a broken method. A capture that always
     * runs would pass the first assertion and lose the "not while running" rule, which exists because
     * a snapshot of a moving railway is a snapshot of a state no longer true by the time it lands. A
     * capture that never runs would pass the second.
     *
     * Driven through the private method by reflection rather than through the window's menus: the
     * doors that call it are dialogs and a page change, and this is a claim about the method they all
     * share.
     *
     * MUTATION this catches: deleting the `captureFromLayout` call fails the first assertion; deleting
     * `|| this.model.getAutoLayout().isRunning()` from the guard fails the second.
     */
    @Test
    public void testTheWindowsCaptureActuallyCapturesAndStopsWhenTrainsMove() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("captureRunningLayout is a method on the window");
        }

        File folder = aWorkingCopy();

        AutonomySession session = new AutonomySession(folder);
        session.open(pagesIn(folder));

        String active = session.getStore().getActiveConfiguration();

        assertNotNull(active, "the sample setup has no active configuration");

        model.parseAuto(session.buildConfiguration());

        org.traincontrol.automation.Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration did not build");

        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        javax.swing.SwingUtilities.invokeAndWait(
            () -> ui[0] = new org.traincontrol.gui.TrainControlUI());

        try
        {
            // THE MODEL TOO, which the guard reads first.
            //
            // A bare window has none - setViewListener wires it, and that starts the threads a window
            // needs to be a window.  The field is what captureRunningLayout actually asks, so it is
            // what this sets: the claim is about the method's six conditions, not about start-up.
            set(ui[0], "model", model);
            set(ui[0], "autonomySession", session);
            set(ui[0], "activeDiagramConfiguration", active);

            java.lang.reflect.Method capture =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod("captureRunningLayout");
            capture.setAccessible(true);

            // The run: a train ends up somewhere it did not start.
            org.traincontrol.automation.Point from = occupiedPoint(layout);
            org.traincontrol.automation.Point to = emptyPoint(layout);

            assertNotNull(from, "no locomotive is placed, so no run can be modelled");
            assertNotNull(to, "every point is occupied, so there is nowhere for a train to have gone");

            org.traincontrol.base.Locomotive moved = from.getCurrentLocomotive();

            String movedName = moved.getName();
            String destination = to.getName();

            from.setLocomotive(null);
            to.setLocomotive(moved);

            assertFalse(layout.isRunning(), "precondition: the layout must be stopped for the capture");

            capture.invoke(ui[0]);

            assertEquals(whereItIsAfterARebuild(session, movedName), destination,
                "the window's capture did not fold the run into the setup, so the next rebuild puts "
                + "the train back where it started - which is OB-144, through the method every door "
                + "that fixes it calls");

            // --- and NOT while the railway is moving ------------------------------------------
            //
            // ON THE CURRENT LAYOUT, not the one above.  whereItIsAfterARebuild calls parseAuto, which
            // REPLACES the Layout object - so a second move made on the old one, and a running flag
            // set on the old one, are made to something the window no longer holds.  The window then
            // captures the new layout, which nothing has touched, and the assertion passes whatever
            // the guard does.  It did: mutating the guard away left this test green until the second
            // half was rewritten to re-read.
            org.traincontrol.automation.Layout current = model.getAutoLayout();

            assertNotNull(current, "the rebuild left no layout");

            org.traincontrol.automation.Point at = null;

            for (org.traincontrol.automation.Point point : current.getPoints())
            {
                if (point.getCurrentLocomotive() != null
                    && movedName.equals(point.getCurrentLocomotive().getName()))
                {
                    at = point;
                    break;
                }
            }

            assertNotNull(at, "the rebuilt layout has the locomotive nowhere");

            assertEquals(at.getName(), destination,
                "precondition: the rebuild should have it where the capture put it");

            org.traincontrol.automation.Point moveTo = emptyPoint(current);

            assertNotNull(moveTo, "nowhere left to model a second move");

            String second = moveTo.getName();

            assertNotEquals(second, destination, "the second move has to go somewhere new");

            at.setLocomotive(null);
            moveTo.setLocomotive(moved);

            setRunning(current, true);

            try
            {
                capture.invoke(ui[0]);
            }
            finally
            {
                setRunning(current, false);
            }

            assertEquals(whereItIsAfterARebuild(session, movedName), destination,
                "the capture ran while autonomy was going.  A snapshot of a moving railway is a "
                + "snapshot of a state that is not true by the time it lands, which is why the guard "
                + "is there");
        }
        finally
        {
            if (ui[0] != null) javax.swing.SwingUtilities.invokeLater(() -> ui[0].dispose());
        }
    }

    /**
     * Where the setup says a locomotive is, asked the way the editor's close asks it - by rebuilding
     * the running layout from what was saved.
     *
     * @param session the setup
     * @param locName the locomotive
     * @return the point's name, or null if it is on none
     */
    private String whereItIsAfterARebuild(AutonomySession session, String locName) throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        org.traincontrol.automation.Layout rebuilt = model.getAutoLayout();

        assertNotNull(rebuilt, "the configuration did not rebuild");

        for (org.traincontrol.automation.Point point : rebuilt.getPoints())
        {
            if (point.getCurrentLocomotive() == null) continue;

            if (locName.equals(point.getCurrentLocomotive().getName())) return point.getName();
        }

        return null;
    }

    /** Sets a private field on the window, which is how the running application wires these two. */
    private static void set(Object on, String field, Object value) throws Exception
    {
        java.lang.reflect.Field f = on.getClass().getDeclaredField(field);

        f.setAccessible(true);
        f.set(on, value);
    }

    /**
     * Turns the layout's running flag, which has no setter - it is set by the dispatch loop.
     *
     * @param layout the layout
     * @param running what to say
     */
    private static void setRunning(org.traincontrol.automation.Layout layout, boolean running)
        throws Exception
    {
        java.lang.reflect.Field f =
            org.traincontrol.automation.Layout.class.getDeclaredField("running");

        f.setAccessible(true);
        f.setBoolean(layout, running);
    }

    // --- the running layout ------------------------------------------------------------------

    private static org.traincontrol.automation.Point occupiedPoint(
        org.traincontrol.automation.Layout layout)
    {
        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.isOccupied() && point.getCurrentLocomotive() != null) return point;
        }

        return null;
    }

    private static org.traincontrol.automation.Point emptyPoint(
        org.traincontrol.automation.Layout layout)
    {
        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.isDestination() && !point.isOccupied()) return point;
        }

        return null;
    }

    // --- the fixture -------------------------------------------------------------------------

    private static List<LayoutDiagram> pagesIn(File folder) throws Exception
    {
        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        return parser.parseLayout(new LinkedList<MarklinAccessory>());
    }

    private static File aWorkingCopy() throws Exception
    {
        File from = new File(System.getProperty("user.dir"), "test/test_layout");

        assertTrue(from.isDirectory(), "sample layout not found");

        File temp = File.createTempFile("tc-run-edit", "");

        assertTrue(temp.delete(), "making room for a directory of the same name");

        copyTree(from, temp);

        temp.deleteOnExit();

        return temp;
    }

    private static void copyTree(File from, File to) throws Exception
    {
        assertTrue(to.mkdirs() || to.isDirectory(), "could not make " + to);

        File[] children = from.listFiles();

        if (children == null) return;

        for (File one : children)
        {
            File target = new File(to, one.getName());

            if (one.isDirectory()) copyTree(one, target);
            else Files.copy(one.toPath(), target.toPath());

            target.deleteOnExit();
        }
    }
}
