package regression;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomy.ArrivalSidePrompt;
import org.traincontrol.gui.GraphLocAssign;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train put down by the assign dialog records which way it came in.
 *
 * Adam, settling the question `behaviour/facing.md` section 4 had left open: **"the missing arrival side should be
 * set - either from the data, by the user, or randomly"**, then **"proceed with this combo.  Try to reuse the
 * machinery/checks of the current popup."**
 *
 * `Layout.edgesCoveredByStandingTrains` walks back from a standing train along the side it came in by and blocks that
 * track for everything else.  Three doors put a train down by hand and only the diagram's drag and paste worked the
 * side out.  This drives the DOOR and not the rule: `testATrainCoversTheTrackBehindIt` pins `forPlacement` and
 * `wouldAsk`, and the rule was never wrong, it was never CALLED.
 *
 * ON THE FROZEN SNAPSHOT, never `cs2_sample_layout`.
 *
 * MUTATION: take the `session.setArrivedFrom` / `point.setArrivedFrom` pair back out of
 * `GraphLocAssign.commitAndRecord` and the first test fails; make `refreshArrivalSide` skip `recordedHere()` and the
 * second fails, naming the side it overwrote; drop the `size() < 2` guard in `buildArrivalSide` and the third fails on
 * the terminus.
 *
 * @author Adam
 */
public class testAPlacedTrainRecordsWhereItCameFrom
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static TrainControlUI ui;

    /** A square with a real choice of arrival sides, and one with only one. */
    private static TileKey junction;
    private static TileKey terminus;

    private static String junctionPoint;
    private static String terminusPoint;

    private static Locomotive train;

    /** What the dialog is allowed to write back onto a real locomotive, put back afterwards. */
    private static Integer speedBefore;
    private static Integer lengthBefore;

    /** Why the set-up found no fixture, or null; each claim asks it, so it fails rather than skips. */
    private static String unbuilt;

    /** Fails the claim when the set-up found no fixture: a skip or an assert in `setUpClass` reads as skipped tests. */
    private static void assertBuilt()
    {
        if (unbuilt != null) throw new AssertionError(unbuilt);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the assign dialog needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();

        // The model's own pages, wired to their accessories (`LayoutSandbox.wiredPages`): a second parse gives a
        // railway of five edges with most points isolated.
        session = new AutonomySession(sandbox.getFolder());
        session.open(support.LayoutSandbox.wiredPages(model));

        // A station with two ways in, made in this copy: on Adam's railway his one-way track and barred sides close the
        // second way into every square a train may stop at.  RampDown's barred south side is lifted here, in the
        // sandbox only; BottomMainA keeps its bar, which the barred-side claim needs.
        session.setBarredArrivals(new TileKey("1 - Main", 21, 6),
            java.util.Collections.<org.traincontrol.automationui.TilePorts.Side>emptySet());

        model.parseAuto(session.buildConfiguration());

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new CountDownLatch(1));

        java.util.Set<Locomotive> running = model.getAutoLayout().getLocomotivesToRun();

        if (running.isEmpty())
        {
            unbuilt = "no locomotive is in autonomy on this fixture";
            return;
        }

        train = running.iterator().next();

        speedBefore = train.getPreferredSpeed();
        lengthBefore = train.getTrainLength();

        findTheSquares();
    }

    /**
     * A side the operator has barred is not a side a train can have arrived by (OB-204).  Adam: *"when copying (moving)
     * a locomotive from bottommainc to bottommaina, it does not always show the orange tail facing west.  seems to
     * appear about half the time."*
     *
     * `AutonomyBuilder.splitSides`, which every placement door reads through `StationIndex.arrivalSidesAt`, collected
     * entry sides with no reference to the barred list, so a square with one way in and one barred side looked
     * two-sided, `ArrivalSidePrompt.suggestedFor`'s first rule could not fire, and the compass fallback read the
     * train's FACING, which alternates.  So the claim is STABILITY, not a particular side: the same square, the same
     * train, two facings, one answer.
     *
     * The control is the other half: with the RAW geometric sides the two facings give different answers; if they stop
     * doing so the fixture can no longer show the defect and this fails out loud.  Squares are searched, not named.
     */
    @Test
    public void testABarredSideIsNotASideATrainArrivedBy() throws Exception
    {
        assertBuilt();

        Layout layout = model.getAutoLayout();

        Point narrowed = null;

        TileKey square = null;

        java.util.List<TileKey> tried = new java.util.ArrayList<>();

        for (Point point : layout.getPoints())
        {
            TileKey at = session.getStationIndex().squareOf(point.getName());

            if (at == null) continue;

            // The shape the defect needs, defined from the STORED data rather than the method under test: otherwise
            // breaking that method would SKIP this test rather than fail it.
            if (session.arrivalSides(at).size() > 1
                && session.arrivalSides(at).size() - session.getBarredArrivals(at).size() == 1)
            {
                // And one where the control below can see the defect: the raw sides give the two facings different
                // answers.
                String rawOne = ArrivalSidePrompt.suggestedFor(layout, point, "E", false, session.arrivalSides(at));
                String rawOther = ArrivalSidePrompt.suggestedFor(layout, point, "W", false, session.arrivalSides(at));

                tried.add(at);

                if (java.util.Objects.equals(rawOne, rawOther)) continue;

                narrowed = point;

                square = at;

                break;
            }
        }

        if (narrowed == null)
        {
            throw new AssertionError("no square on this fixture has a barred arrival where the raw sides tell the"
                + " two facings apart (tried " + tried + "), so there is nothing here that the barred list changes");
        }

        assertEquals(session.unbarredArrivalSides(square).size(), 1,
            "this square has " + session.arrivalSides(square).size() + " sides and "
            + session.getBarredArrivals(square) + " barred, so one way in is left - and"
            + " `unbarredArrivalSides` answered " + session.unbarredArrivalSides(square)
            + ". A side the operator has closed is not a side a train arrived by (Adam, OB-204)");

        // THE CLAIM: the same square, two facings, one answer.
        String facingOne = ArrivalSidePrompt.suggestedFor(layout, narrowed, "E", false,
            session.unbarredArrivalSides(square));

        String facingOther = ArrivalSidePrompt.suggestedFor(layout, narrowed, "W", false,
            session.unbarredArrivalSides(square));

        assertEquals(facingOne, facingOther,
            "the arrival side recorded at " + square + " depends on which way the train happens to be"
            + " facing - " + facingOne + " against " + facingOther + " - and the facing alternates every"
            + " time the train is turned round somewhere. That is Adam's \"about half the time\":"
            + " the square has ONE way in, and a side the operator barred was being counted as a second");

        assertNotNull(facingOne,
            "a square with one unbarred way in answered nothing at all, so a train placed there records"
            + " no tail and the track behind it is left open");

        // The control: with the raw geometry the two facings really do disagree here, so the claim above is measuring
        // something.
        String rawOne = ArrivalSidePrompt.suggestedFor(layout, narrowed, "E", false,
            session.arrivalSides(square));

        String rawOther = ArrivalSidePrompt.suggestedFor(layout, narrowed, "W", false,
            session.arrivalSides(square));

        if (java.util.Objects.equals(rawOne, rawOther))
        {
            throw new AssertionError("the raw geometric sides give one answer for both facings at "
                + square + ", so this fixture cannot show the defect and the claim above passes"
                + " whatever the code does");
        }
    }

    /**
     * Picks one square with a choice of arrival sides and one with none; searched, not named, as the fixture is a
     * snapshot of a real railway.
     */
    private static void findTheSquares()
    {
        Layout layout = model.getAutoLayout();

        for (Point point : layout.getPoints())
        {
            if (!point.isDestination()) continue;

            TileKey square = session.getStationIndex().squareOf(point.getName());

            if (square == null) continue;

            int sides = ArrivalSidePrompt.choicesFor(layout, point, session.unbarredArrivalSides(square)).size();

            if (sides > 1 && junction == null)
            {
                junction = square;
                junctionPoint = point.getName();
            }

            if (sides == 1 && terminus == null)
            {
                terminus = square;
                terminusPoint = point.getName();
            }
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            // The dialog writes these back on every OK, and the locomotive database is the operator's own.  A preferred
            // speed of zero is the exception to a round trip: the form shows the layout default and committing would
            // make that default real.
            if (train != null)
            {
                train.setPreferredSpeed(speedBefore == null ? 0 : speedBefore);
                train.setTrainLength(lengthBefore == null ? 0 : lengthBefore);
            }

            if (ui != null) SwingUtilities.invokeAndWait(() -> ui.dispose());
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Accepting the dialog records a side, in the setup AND on the railway: the walk that blocks track reads the live
     * `Point`, the setup survives a restart, and written to one alone the operator cannot tell "it does not work" from
     * "it has not been rebuilt yet".  The square is CLEARED first, so this cannot pass on a value already there.
     */
    @Test
    public void testAcceptingTheDialogRecordsTheArrivalSide() throws Exception
    {
        assertBuilt();

        if (junction == null) throw new AssertionError("no square here has two ways in");

        Point point = putTheTrainOn(junctionPoint);

        session.setArrivedFrom(junction, null);
        point.setArrivedFrom(null);

        assertNull(session.getArrivedFrom(junction), "the square did not clear, so this proves nothing");

        GraphLocAssign edit = dialogOn(point);

        GraphLocAssign.commitAndRecord(edit);

        String recorded = session.getArrivedFrom(junction);

        assertNotNull(recorded,
            "the assign dialog placed a train and recorded no arrival side, so nothing behind it is "
            + "blocked - while the identical placement by drag works the side out and blocks it "
            + "(REV9-B2, settled by Adam 2026-09-11: the missing arrival side should be set)");

        assertEquals(point.getArrivedFrom(), recorded,
            "the setup was told where the tail is and the railway was not. What blocks track is the "
            + "live Point, so the operator answers the question and watches nothing happen until a "
            + "rebuild (VAL8-A2)");

        assertTrue(ArrivalSidePrompt.choicesFor(model.getAutoLayout(), point,
            session.arrivalSides(junction)).contains(recorded),
            "the side recorded (" + recorded + ") is not one this square has track on, so the tail "
            + "walk can never match it and it blocks nothing at all");
    }

    /**
     * A side something WATCHED a train arrive by is not replaced by a derived one: re-opening the dialog to change a
     * function must not overwrite a measured value with the rule's guess.  The side chosen is the one the rule would
     * NOT pick, so agreement cannot satisfy it.
     */
    @Test
    public void testARecordedSideSurvivesAVisitToTheDialog() throws Exception
    {
        assertBuilt();

        if (junction == null) throw new AssertionError("no square here has two ways in");

        Point point = putTheTrainOn(junctionPoint);

        List<String> sides = ArrivalSidePrompt.choicesFor(model.getAutoLayout(), point,
            session.arrivalSides(junction));

        String derived = ArrivalSidePrompt.suggestedFor(model.getAutoLayout(), point,
            session.facingOf(train.getName(), model.getAutoLayout()) == null ? null
                : session.facingOf(train.getName(), model.getAutoLayout()).name(),
            false, session.arrivalSides(junction));

        String measured = sides.get(sides.size() - 1).equals(derived) ? sides.get(0)
            : sides.get(sides.size() - 1);

        assertFalse(measured.equals(derived),
            "the fixture offers only one side to pick from, so this could not tell a kept value from "
            + "a re-derived one");

        session.setArrivedFrom(junction, measured);
        point.setArrivedFrom(measured);

        GraphLocAssign edit = dialogOn(point);

        GraphLocAssign.commitAndRecord(edit);

        assertEquals(session.getArrivedFrom(junction), measured,
            "opening the dialog on a train that is already standing here lost the recorded arrival "
            + "side - replaced by a derived one, or by nothing at all: `placeLocomotive` clears the "
            + "property on every commit, so the dialog is what has to put it back. Something WATCHED "
            + "that train arrive; the suggestion is worked out from its heading and is an assumption. "
            + "A visit to this dialog to change an arrival function must not move the tail");

        assertEquals(point.getArrivedFrom(), measured, "and the railway kept it too");
    }

    /**
     * The combo appears where there is a choice, and the rule answers where there is not: a one-entry combo at a
     * terminus would look like a decision and not be one ("asking would be a dialog with one button").  The ANSWER is
     * the same either way: the door gets a side whether or not the operator was shown anything.
     */
    @Test
    public void testTheChoiceIsOfferedOnlyWhereThereIsOne() throws Exception
    {
        assertBuilt();

        if (terminus == null) throw new AssertionError("every square on this fixture has two ways in");

        Point single = putTheTrainOn(terminusPoint);

        GraphLocAssign forced = dialogOn(single);

        assertTrue(forced.asDialogContent() == forced,
            "a square with one way in offered a combo to choose it with, which is a control that "
            + "looks like a decision and is not");

        List<String> only = ArrivalSidePrompt.choicesFor(model.getAutoLayout(), single,
            session.unbarredArrivalSides(terminus));

        assertEquals(forced.getArrivedFrom(), only.get(0),
            "the dialog showed no combo AND answered nothing, so a train placed at a terminus - where "
            + "the side is not in doubt at all - records no tail");

        if (junction == null) throw new AssertionError("no square here has two ways in");

        GraphLocAssign asked = dialogOn(putTheTrainOn(junctionPoint));

        assertFalse(asked.asDialogContent() == asked,
            "a square with two ways in did not offer the choice, so the operator cannot correct a "
            + "side that is an assumption about a train nobody watched arrive");
    }

    /**
     * Every entry on the combo is a side, and a placement therefore always records one.  Adam: **"clearing should not
     * be possible, only setting."**  A "Not known" entry was taken out of the arrived-from MENU on the argument that
     * every side the square has is offered, so a wrong answer is corrected by choosing the right one; a blank only
     * offers a way to switch the tail blocking off.  The entries are read off what the dialog would really show, so a
     * second row added anywhere in the wrapper is caught too.
     *
     * MUTATION: put `entries.add(NONE_LABEL);` back at the top of `buildArrivalSide` and the first claim fails, naming
     * the entry that is not a side.
     */
    @Test
    public void testTheComboOffersSidesAndNothingElse() throws Exception
    {
        assertBuilt();

        if (junction == null) throw new AssertionError("no square here has two ways in");

        Point point = putTheTrainOn(junctionPoint);

        GraphLocAssign edit = dialogOn(point);

        javax.swing.JComboBox<?> combo = theArrivalSideCombo(edit);

        assertNotNull(combo, "the dialog showed no arrival-side combo on a square with two ways in");

        List<String> sides = ArrivalSidePrompt.choicesFor(model.getAutoLayout(), point,
            session.arrivalSides(junction));

        for (int i = 0; i < combo.getItemCount(); i++)
        {
            String entry = String.valueOf(combo.getItemAt(i));

            assertFalse(GraphLocAssign.NONE_LABEL.equals(entry),
                "the arrival-side combo offers \"" + entry + "\", which records nothing. Clearing is "
                + "not offered for this property (Adam, 2026-09-07) - a wrong side is corrected by "
                + "choosing the right one, and a blank is a way to switch the tail blocking off");
        }

        assertEquals(combo.getItemCount(), sides.size(),
            "the combo offers " + combo.getItemCount() + " entries for a square with " + sides.size()
            + " ways in, so one of them is not a side this train could have come from");

        assertNotNull(edit.getArrivedFrom(),
            "the dialog would record nothing for this placement, which is the state Adam's ruling of "
            + "2026-09-11 closed: the missing arrival side should be set - from the data, by the user, "
            + "or randomly");
    }

    /**
     * The Edit Locomotive view offers every length the locomotive menu does, up to forty (OB-294).  Adam: *"separately,
     * increase choosable train lengths up to 40 in the dropdown."*  The two lists are one question; this one wrote its
     * own 0 to 20 while the menu read `TrainControlUI.ROUTE_TRAIN_LENGTH_MAX`.
     *
     * MUTATION: leave the limit at 20, or write this list out by hand again, and this fails.
     */
    @Test
    public void testTheEditViewOffersEveryLengthTheMenuDoes() throws Exception
    {
        assertBuilt();

        assertEquals(TrainControlUI.ROUTE_TRAIN_LENGTH_MAX, 40, "the longest train the length lists offer is not forty -"
            + " Adam, OB-294: \"increase choosable train lengths up to 40 in the dropdown\"");

        if (junction == null) throw new AssertionError("no square here has two ways in");

        Point point = putTheTrainOn(junctionPoint);

        Integer lengthBefore = train.getTrainLength();

        try
        {
            // THE LOCOMOTIVE MENU'S LIST, AS IT OPENS (MT-572 steps 1 and 2): 0 to 40, and 40 chosen.
            train.setTrainLength(0);

            SwingUtilities.invokeLater(() -> ui.promptTrainLength(train, null));

            javax.swing.JOptionPane pane = null;

            for (int wait = 0; wait < 100 && pane == null; wait++)
            {
                Thread.sleep(100);

                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (!window.isShowing() || !(window instanceof javax.swing.JDialog)) continue;

                    java.awt.Container content = ((javax.swing.JDialog) window).getContentPane();

                    for (java.awt.Component child : content.getComponents())
                    {
                        if (child instanceof javax.swing.JOptionPane
                            && org.traincontrol.util.I18n.t("autolayout.ui.trainLength").equals(
                                ((javax.swing.JOptionPane) child).getMessage()))
                        {
                            pane = (javax.swing.JOptionPane) child;
                        }
                    }
                }
            }

            assertNotNull(pane, "precondition: the locomotive menu's train-length question did not open");

            Object[] offered = pane.getSelectionValues();

            assertEquals(offered.length, 41, "the locomotive menu's train-length list does not run 0 to 40 (MT-572): "
                + java.util.Arrays.toString(offered));

            assertEquals(offered[0], "0", "the locomotive menu's list does not start at 0 (MT-572)");
            assertEquals(offered[40], "40", "the locomotive menu's list does not end at 40 (MT-572)");

            final javax.swing.JOptionPane answering = pane;

            SwingUtilities.invokeAndWait(() ->
            {
                answering.setInputValue("40");
                answering.setValue(Integer.valueOf(javax.swing.JOptionPane.OK_OPTION));
            });

            for (int wait = 0; wait < 50 && !Integer.valueOf(40).equals(train.getTrainLength()); wait++) Thread.sleep(100);

            assertEquals(train.getTrainLength(), Integer.valueOf(TrainControlUI.ROUTE_TRAIN_LENGTH_MAX), "40 chosen from"
                + " the locomotive menu's list did not give the train a length of 40 (MT-572)");

            // THE EDIT LOCOMOTIVE VIEW SHOWS IT (step 3), AND OK KEEPS IT.
            GraphLocAssign edit = dialogOn(point);

            assertEquals(edit.getTrainLength(), Integer.valueOf(TrainControlUI.ROUTE_TRAIN_LENGTH_MAX), "a train of "
                + TrainControlUI.ROUTE_TRAIN_LENGTH_MAX + " units - the longest the locomotive menu offers - opened in the"
                + " Edit Locomotive view as " + edit.getTrainLength() + ", which OK would save");

            // OK in that window commits through `commitAndRecord` (AutonomyEditorPanel's Edit Locomotive item).
            GraphLocAssign.commitAndRecord(edit);

            assertEquals(train.getTrainLength(), Integer.valueOf(TrainControlUI.ROUTE_TRAIN_LENGTH_MAX), "after OK in the"
                + " Edit Locomotive view the train is no longer 40 long (MT-572)");
        }
        finally
        {
            train.setTrainLength(lengthBefore == null ? 0 : lengthBefore);
        }
    }

    /**
     * OK on a placement the railway refuses leaves the locomotive as it was.  `Layout.moveLocomotive` refuses while
     * autonomy runs, for a point the railway does not have, and for one that is no station; `commitChanges` threw the
     * answer away and wrote the form's settings onto the locomotive anyway.
     *
     * Refused here by a point the railway does not have, so nothing on the fixture is changed to get the refusal; a
     * different length is chosen in the form so a write cannot pass for the value already there.
     *
     * MUTATION: take the `if (!... moveLocomotive(...)) return false;` guard out of `commitChanges`, and this fails.
     */
    @Test
    public void testARefusedPlacementLeavesTheTrainAsItWas() throws Exception
    {
        assertBuilt();

        final Point nowhere = new Point("GST-B2 not on this railway", false, null);

        assertNull(model.getAutoLayout().getPoint(nowhere.getName()), "precondition: the railway has a point called "
            + nowhere.getName() + ", so nothing below is refused");

        final GraphLocAssign[] built = new GraphLocAssign[1];

        SwingUtilities.invokeAndWait(() ->
            built[0] = new GraphLocAssign(ui, nowhere, session, model.getAutoLayout()));

        final GraphLocAssign edit = built[0];

        final Locomotive subject = model.getLocByName(edit.getLoc());

        assertNotNull(subject, "precondition: the dialog offers no locomotive to place");

        final javax.swing.JComboBox<?> lengths = theLengthCombo(edit);

        assertNotNull(lengths, "precondition: the dialog shows no train-length list");

        final Integer lengthWas = subject.getTrainLength();
        final int speedWas = subject.getPreferredSpeed();

        final String chosen = Integer.valueOf(7).equals(lengthWas) ? "8" : "7";

        try
        {
            SwingUtilities.invokeAndWait(() -> lengths.setSelectedItem(chosen));

            assertEquals(edit.getTrainLength(), Integer.valueOf(chosen),
                "precondition: the form did not take the length chosen in it");

            GraphLocAssign.commitAndRecord(edit);

            assertEquals(subject.getTrainLength(), lengthWas, "OK on a placement the railway refused changed "
                + subject.getName() + "'s length to " + chosen + " anyway - the log says the train was not placed, and"
                + " its settings changed regardless (GST-B2)");
        }
        finally
        {
            subject.setTrainLength(lengthWas == null ? 0 : lengthWas);
            subject.setPreferredSpeed(speedWas);
        }
    }

    /**
     * The form's train-length list, found by what it offers: its field is generated and private, so it is the one list
     * that runs from 0 to `TrainControlUI.ROUTE_TRAIN_LENGTH_MAX`.
     */
    private static javax.swing.JComboBox<?> theLengthCombo(java.awt.Container in)
    {
        for (java.awt.Component child : in.getComponents())
        {
            if (child instanceof javax.swing.JComboBox)
            {
                javax.swing.JComboBox<?> list = (javax.swing.JComboBox<?>) child;

                if (list.getItemCount() == TrainControlUI.ROUTE_TRAIN_LENGTH_MAX + 1
                    && "0".equals(String.valueOf(list.getItemAt(0))))
                {
                    return list;
                }
            }
            else if (child instanceof java.awt.Container)
            {
                javax.swing.JComboBox<?> inside = theLengthCombo((java.awt.Container) child);

                if (inside != null) return inside;
            }
        }

        return null;
    }

    /**
     * The arrival-side combo as the operator would see it, searched through the wrapper: the form carries four combos
     * of its own and the one that matters is OUTSIDE it.
     */
    private static javax.swing.JComboBox<?> theArrivalSideCombo(GraphLocAssign edit)
    {
        java.awt.Component shown = edit.asDialogContent();

        if (shown == edit || !(shown instanceof java.awt.Container)) return null;

        for (java.awt.Component child : ((java.awt.Container) shown).getComponents())
        {
            if (child == edit || !(child instanceof java.awt.Container)) continue;

            for (java.awt.Component inner : ((java.awt.Container) child).getComponents())
            {
                if (inner instanceof javax.swing.JComboBox) return (javax.swing.JComboBox<?>) inner;
            }
        }

        return null;
    }

    /** Puts the train on a named copy, the way a placement door does. */
    private static Point putTheTrainOn(String pointName) throws Exception
    {
        model.getAutoLayout().moveLocomotive(train.getName(), pointName, false);

        Point point = model.getAutoLayout().getPoint(pointName);

        assertNotNull(point, "the fixture no longer has a point called " + pointName);

        assertNotNull(point.getCurrentLocomotive(),
            "the railway refused to put " + train.getName() + " on " + pointName + ", so every claim "
            + "below would be about an empty square");

        return point;
    }

    /** The dialog both assignment menus open, built on the event thread as they build it. */
    private static GraphLocAssign dialogOn(Point point) throws Exception
    {
        final GraphLocAssign[] built = new GraphLocAssign[1];

        SwingUtilities.invokeAndWait(() ->
            built[0] = new GraphLocAssign(ui, point, session, model.getAutoLayout()));

        assertEquals(built[0].getLoc(), train.getName(),
            "the dialog opened on a different locomotive from the one standing here, so what it "
            + "commits is not the placement these tests are about");

        return built[0];
    }
}
