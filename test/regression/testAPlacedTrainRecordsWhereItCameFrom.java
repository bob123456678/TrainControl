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
import org.traincontrol.gui.ArrivalSidePrompt;
import org.traincontrol.gui.GraphLocAssign;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * REV9-B2: a train put down by the assign dialog records which way it came in.
 *
 * Adam, 2026-09-11, settling the question `behaviour.md` section 4 had left open: **"the missing
 * arrival side should be set - either from the data, by the user, or randomly"**, then **"proceed with
 * this combo.  Try to reuse the machinery/checks of the current popup."**
 *
 * **What the side is for.**  `Layout.edgesCoveredByStandingTrains` walks back from a standing train
 * along the side it came in by and blocks that track for everything else.  Three doors put a train
 * down by hand and only one of them - the diagram's drag and paste - worked the side out; the other
 * two placed an identical train and blocked nothing behind it, so the same placement protected the
 * track or did not depending on which menu the operator reached it through.
 *
 * **Why this drives the DOOR and not the rule.**  The rule has had tests since it was written
 * (`testATrainCoversTheTrackBehindIt` pins `forPlacement` and `wouldAsk` case by case) and it was
 * never wrong - it was never CALLED.  A test over `ArrivalSidePrompt` would have been green through
 * the whole life of this defect, which is this project's most repeated shape: the extracted rule is
 * covered and the call site is the uncovered part.
 *
 * ON THE FROZEN SNAPSHOT, never `cs2_sample_layout`.
 *
 * MUTATION: take the `session.setArrivedFrom` / `point.setArrivedFrom` pair back out of
 * `GraphLocAssign.commitAndRecord` and the first test fails; make `refreshArrivalSide` skip
 * `recordedHere()` and the second fails, naming the side it overwrote; drop the `size() < 2` guard in
 * `buildArrivalSide` and the third fails on the terminus.
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

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the assign dialog needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, false);

        // THE MODEL'S OWN PAGES, wired to their accessories - a second parse of the same bytes gives a
        // railway of five edges with most of its points isolated, and every square then has one side.
        // See `LayoutSandbox.wiredPages`.
        session = new AutonomySession(sandbox.getFolder());
        session.open(support.LayoutSandbox.wiredPages(model));

        model.parseAuto(session.buildConfiguration());

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new CountDownLatch(1));

        java.util.Set<Locomotive> running = model.getAutoLayout().getLocomotivesToRun();

        if (running.isEmpty()) throw new SkipException("no locomotive is in autonomy on this fixture");

        train = running.iterator().next();

        speedBefore = train.getPreferredSpeed();
        lengthBefore = train.getTrainLength();

        findTheSquares();
    }

    /**
     * A side the operator has barred is not a side a train can have arrived by (Adam, OB-204).
     *
     * *"when copying (moving) a locomotive from bottommainc to bottommaina, it does not always show the
     * orange tail facing west.  seems to appear about half the time."*
     *
     * **"About half the time" was the diagnosis.**  `AutonomyBuilder.splitSides` - which is what every
     * placement door reads, through `StationIndex.arrivalSidesAt` - walks the reduced edges arriving at
     * a square and collects their entry sides with no reference to the barred list. So a square with one
     * way in and one barred side looked two-sided, `ArrivalSidePrompt.suggestedFor`'s first and best rule
     * (*"one way in that is not the way it is pointing"*) could not fire, and it fell through to the
     * compass assumption - which reads the train's FACING. The facing alternates when the square the
     * train came from turns it round, and so did the recorded side.
     *
     * So the claim is about STABILITY, not about a particular side: the same square, the same train, two
     * facings, one answer. A test that asserted "west" would pass on a railway where the rule had simply
     * stopped working and always said west.
     *
     * **The control is the other half**, and it is what makes this a measurement: asked with the RAW
     * geometric sides - what the doors used to be handed - the two facings give different answers. If
     * they ever stop doing so, this fixture can no longer show the defect and the claim above is
     * vacuous, which the skip says out loud.
     *
     * Searched rather than named, like everything else in this class: a station named here is a station
     * somebody re-plumbs.
     */
    @Test
    public void testABarredSideIsNotASideATrainArrivedBy() throws Exception
    {
        Layout layout = model.getAutoLayout();

        Point narrowed = null;

        TileKey square = null;

        for (Point point : layout.getPoints())
        {
            TileKey at = session.getStationIndex().squareOf(point.getName());

            if (at == null) continue;

            // The shape the defect needs, defined from the STORED data rather than from the method
            // under test: the geometry says two ways in and the operator has closed all but one of
            // them. Asking `unbarredArrivalSides` here instead would make this search agree with
            // whatever that method does, so breaking it would SKIP this test rather than fail it.
            if (session.arrivalSides(at).size() > 1
                && session.arrivalSides(at).size() - session.getBarredArrivals(at).size() == 1)
            {
                narrowed = point;

                square = at;

                break;
            }
        }

        if (narrowed == null)
        {
            throw new SkipException("no square on this fixture has a barred arrival, so there is"
                + " nothing here that the barred list changes");
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

        // THE CONTROL: with the raw geometry - what the doors were handed before OB-204 - the two
        // facings really do disagree here, so the claim above is measuring something.
        String rawOne = ArrivalSidePrompt.suggestedFor(layout, narrowed, "E", false,
            session.arrivalSides(square));

        String rawOther = ArrivalSidePrompt.suggestedFor(layout, narrowed, "W", false,
            session.arrivalSides(square));

        if (java.util.Objects.equals(rawOne, rawOther))
        {
            throw new SkipException("the raw geometric sides give one answer for both facings at "
                + square + ", so this fixture cannot show the defect and the claim above passes"
                + " whatever the code does");
        }
    }

    /**
     * Picks one square with a choice of arrival sides and one with none.
     *
     * Searched rather than named: the fixture is a snapshot of a real railway and a station named here
     * would be a station somebody re-plumbs. What the tests need is the SHAPE - two ways in, and one -
     * and the search says plainly when the fixture stopped offering either.
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
            // THE DIALOG WRITES THESE BACK ON EVERY OK, and the locomotive database is the operator's
            // own - the sandbox covers the layout folder and nothing else. Both values are read out of
            // the locomotive when the form is built, so they normally round-trip unchanged; a
            // preferred speed of zero is the exception, because the form shows the layout default
            // instead and committing would make that default real.
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
     * Accepting the dialog records a side, in the setup AND on the railway.
     *
     * **Both stores, because they answer different questions.** The walk that blocks track reads the
     * live `Point`; the setup is the half that survives a restart and reaches the railway only at the
     * next rebuild. Written to one alone, the operator answers the question, watches nothing become
     * blocked, and cannot tell "it does not work" from "it has not been rebuilt yet" - which is the
     * shape `VAL8-A2` was filed for at the paste door.
     *
     * The square is CLEARED first, so this cannot pass on a value that was already there - which is
     * exactly how a test of a default passes while measuring nothing.
     */
    @Test
    public void testAcceptingTheDialogRecordsTheArrivalSide() throws Exception
    {
        if (junction == null) throw new SkipException("no square here has two ways in");

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
     * A side something WATCHED a train arrive by is not replaced by a derived one.
     *
     * Re-opening this dialog to change a function must not quietly overwrite a measured value with the
     * rule's guess. Autonomy writes the real side as a train arrives; the dialog's suggestion is worked
     * out from the heading and is right about an ordinary station and no more than a starting point
     * anywhere else.
     *
     * The side chosen is deliberately the one the rule would NOT pick - the other end of the list -
     * so that "the recorded value survived" cannot be satisfied by the suggestion happening to agree.
     */
    @Test
    public void testARecordedSideSurvivesAVisitToTheDialog() throws Exception
    {
        if (junction == null) throw new SkipException("no square here has two ways in");

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
     * The combo appears where there is a choice, and the rule answers where there is not.
     *
     * A terminus has one way in. Offering a one-entry combo there would be a control that looks like a
     * decision and is not - and the popup this borrows from says the same thing by never asking:
     * *"asking would be a dialog with one button"*. What matters is that the ANSWER is the same either
     * way, which is the second claim: the door gets a side out of the dialog whether or not the
     * operator was shown anything.
     */
    @Test
    public void testTheChoiceIsOfferedOnlyWhereThereIsOne() throws Exception
    {
        if (terminus == null) throw new SkipException("every square on this fixture has two ways in");

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

        if (junction == null) throw new SkipException("no square here has two ways in");

        GraphLocAssign asked = dialogOn(putTheTrainOn(junctionPoint));

        assertFalse(asked.asDialogContent() == asked,
            "a square with two ways in did not offer the choice, so the operator cannot correct a "
            + "side that is an assumption about a train nobody watched arrive");
    }

    /**
     * Every entry on the combo is a side, and a placement therefore always records one.
     *
     * Adam, 2026-09-07: **"clearing should not be possible, only setting."**  The arrived-from MENU
     * used to carry a "Not known" entry and it was taken out then, on the argument `behaviour.md`
     * still states: every side the square has is offered, so a wrong answer is corrected by choosing
     * the right one, and what a blank really offers is a way to switch the tail blocking off - a
     * preference about the check rather than a fact about the railway.
     *
     * This combo was built with a "---" row at the top before that ruling was re-read, which would
     * have put the cleared state back into the one surface it had been taken out of. The claim is
     * here because the mistake is easy: a combo that starts on a DERIVED value looks like it needs a
     * way to say "actually, I do not know".
     *
     * The entries are read off what the dialog would really show, not off the field, so a second row
     * added anywhere in the wrapper is caught too.
     *
     * MUTATION: put `entries.add(NONE_LABEL);` back at the top of `buildArrivalSide` and the first
     * claim fails, naming the entry that is not a side.
     */
    @Test
    public void testTheComboOffersSidesAndNothingElse() throws Exception
    {
        if (junction == null) throw new SkipException("no square here has two ways in");

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
     * The arrival-side combo as the operator would see it, found in what the dialog actually shows.
     *
     * Searched through the wrapper rather than read off a field: the form itself carries four combos
     * of its own, so the one that matters is the one OUTSIDE it - which is also the arrangement the
     * claim above is about.
     *
     * @param edit the dialog
     * @return the combo, or null when none is shown
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

    /**
     * Puts the train on a named copy, the way a placement door does.
     *
     * @param pointName the copy
     * @return the Point, with the train on it
     */
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

    /**
     * The dialog both assignment menus open, built on the event thread as they build it.
     *
     * @param point the point being assigned
     * @return the dialog, as though the operator had just pressed OK
     */
    private static GraphLocAssign dialogOn(Point point) throws Exception
    {
        final GraphLocAssign[] built = new GraphLocAssign[1];

        SwingUtilities.invokeAndWait(() ->
            built[0] = new GraphLocAssign(ui, point, false, session, model.getAutoLayout()));

        assertEquals(built[0].getLoc(), train.getName(),
            "the dialog opened on a different locomotive from the one standing here, so what it "
            + "commits is not the placement these tests are about");

        return built[0];
    }
}
