package ui;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * Renaming a locomotive redraws the timetable it is named in (MT-149; Adam, filed critical: "On Rename, loc vanishes
 * from the station / autonomy setup, and the timetable is not updated").
 *
 * The data was never the problem: a probe renamed a locomotive with a timetable entry naming it and the entry followed
 * the rename, in place.  What did not change was the number `repaintTimetable` keys its redraw on: `MarklinLocomotive`
 * hashes by IDENTITY, deliberately (a rename leaves a locomotive inside the consists, exclusion sets and run lists that
 * hold it), and `TimetablePath.hashCode` is built from that hash.  So a rename changed the name in every row and no
 * hash at all, and the table went on naming a locomotive that no longer exists.  The guard is keyed on the TEXT of the
 * rows now, and the rename repair asks for the repaint at all.
 *
 * Not covered: the locomotive vanishing from the station and the panels reading "?????".  The probe shows the model
 * right on both counts after a rename, so those are a different fault on a different surface.
 *
 * MUTATION this catches: keying the guard on `timeTable.hashCode()` again.
 *
 * @author Adam
 */
public class testARenameReachesTheTimetableOnScreen
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static final String BEFORE = "TT rename before";
    private static final String AFTER = "TT rename after";

    private static final int ADDRESS = 74;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();
        model.stop();

        model.newMM2Locomotive(BEFORE, ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null)
        {
            try { model.deleteLoc(AFTER); } catch (Exception ignored) { }
            try { model.deleteLoc(BEFORE); } catch (Exception ignored) { }
        }

        if (sandbox != null) sandbox.close();
    }

    @Test(timeOut = 120000)
    public void testTheRedrawKeyChangesWhenTheNameDoes() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the timetable is a table in a window");
        }

        String config = ("{'points': ["
            + "{'name': 'TT A', 'station': true, 's88': 8490, 'loc': {'name': '" + BEFORE + "'}},"
            + "{'name': 'TT B', 'station': true, 's88': 8491}"
            + "],'edges': [{'start': 'TT A', 'end': 'TT B'}],"
            + "'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 35}").replace('\'', '"');

        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertTrue(layout.isValid(), "precondition: " + Layout.getLastError());

        MarklinLocomotive loc = model.getLocByName(BEFORE);

        assertNotNull(layout.getLocomotiveLocation(loc),
            "precondition: the locomotive has to be placed, or the rename has nothing to disturb");

        List<Edge> path = new ArrayList<>();

        path.add(layout.getEdge("TT A", "TT B"));

        List<TimetablePath> entries = new ArrayList<>();

        entries.add(new TimetablePath(loc, path, 111L));

        layout.setTimetable(entries);

        final TrainControlUI[] ui = new TrainControlUI[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> ui[0] = new TrainControlUI());

        try
        {
            java.lang.reflect.Method signature = TrainControlUI.class.getDeclaredMethod(
                "timetableSignature", List.class);

            signature.setAccessible(true);

            String before = (String) signature.invoke(ui[0], layout.getTimetableSnapshot());

            assertTrue(before.contains(BEFORE),
                "precondition: the key does not name the locomotive, so a rename could not move it");

            assertTrue(model.renameLoc(BEFORE, AFTER), "the rename itself failed");

            // The DATA, which was never the problem, asserted so that a future failure here says which half broke.
            MarklinLocomotive renamed = model.getLocByName(AFTER);

            assertNotNull(renamed, "the locomotive is not in the database under its new name");

            assertSame(renamed, loc, "the rename replaced the object instead of renaming it");

            assertNotNull(layout.getLocomotiveLocation(renamed),
                "the locomotive left the graph on being renamed");

            assertEquals(layout.getTimetableSnapshot().get(0).getLoc().getName(), AFTER,
                "the timetable entry still names the old locomotive");

            // AND THE REDRAW.
            String after = (String) signature.invoke(ui[0], layout.getTimetableSnapshot());

            assertNotEquals(after, before,
                "the key repaintTimetable skips its redraw on is unchanged by a rename, so the table "
                + "goes on naming a locomotive that no longer exists.  That is what keying it on "
                + "hashCode did: a locomotive hashes by identity, so every row's text changed and no "
                + "hash did");

            assertTrue(after.contains(AFTER), "the new key does not name the new locomotive: " + after);

            // AND THE GUARD ITSELF, not only the function it consults: the guard is one line in `repaintTimetable` (`if
            // (showing.equals(lastTimetableState)) return;`), where the mutation lives and nothing above reached.  A
            // guard keyed on anything a rename cannot move leaves `lastTimetableState` unchanged, which IS the redraw
            // being discarded.
            ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

            java.lang.reflect.Method repaint =
                TrainControlUI.class.getDeclaredMethod("repaintTimetable");

            repaint.setAccessible(true);

            java.lang.reflect.Field state =
                TrainControlUI.class.getDeclaredField("lastTimetableState");

            state.setAccessible(true);

            // Once, to establish what it believes it is showing.
            repaint.invoke(ui[0]);

            settle();

            String showing = String.valueOf(state.get(ui[0]));

            assertTrue(showing.contains(AFTER),
                "repaintTimetable did not record what the table shows, so the guard below it "
                + "is deciding on something else entirely.  Recorded: " + showing);

            // And a second rename has to move it again, which is the whole of the guard.
            assertTrue(model.renameLoc(AFTER, BEFORE), "the second rename failed");

            repaint.invoke(ui[0]);

            settle();

            assertNotEquals(String.valueOf(state.get(ui[0])), showing,
                "the key repaintTimetable skips its redraw on did not move when every row of "
                + "the table changed its text, so the redraw is discarded and the table goes "
                + "on naming a locomotive that is not there.  That is what keying it on "
                + "hashCode did, and it is the mutation this test is named for");
        }
        finally
        {
            final TrainControlUI closing = ui[0];

            if (closing != null) javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
        }
    }

    /** Lets what the window POSTED actually run: `repaintTimetable` does its work in an invokeLater. */
    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 5; pass++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
