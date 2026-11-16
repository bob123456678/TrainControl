package org.traincontrol.gui.autonomy;

import java.awt.Component;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * The question a hand-driven send asks before turning a train round, from both the locomotive commands tab and the
 * track (one class, so the two doors cannot drift into two questions).
 *
 * It has to be asked rather than worked out: a reversing point turns whatever passes it, and the reason lives in the
 * leg AFTER this one.  A true terminus is never asked about: the turn is not a choice.
 *
 * @author Adam
 */
public final class ManualReversalPrompt
{
    private ManualReversalPrompt()
    {
    }

    /**
     * Asks about the whole journey BEFORE it starts, and answers for every point on it: once per journey, so it cannot
     * end up half in each state, and while the train stands.  Nothing is asked when the path reaches no such square.
     *
     * @param session the setup, which knows what the operator marked
     * @param parent what to center the dialog on
     * @param path the journey about to be run
     * @param loc the train
     * @param railway the railway the path was offered on, or null
     * @return the policy to hand to executePath
     */
    public static Layout.ReversalPolicy forJourney(
        final AutonomySession session, final Component parent,
        final List<Edge> path, final Locomotive loc,
        final Layout railway)
    {
        final Layout.ReversalPolicy asking = forOperator(session, parent);

        Point first = null;

        // A JOURNEY THAT ENDS AT A TERMINUS IS NOT ASKED ABOUT AT ALL.
        //
        // `Layout.shouldReverseAt` answers a journey to a terminus from the flag and ignores any policy: the reversal
        // on the way is how a train backs into a terminus (MT-245).
        if (path != null && !path.isEmpty())
        {
            Edge last = path.get(path.size() - 1);

            // UNLESS THE DOOR ASKS ABOUT IT (OB-205): `isTerminus()` is also true at the turning copy of a MAY-turn
            // square, which `asksAbout` (the setup's `mayTurnTiles()`) tells apart.
            if (last != null && last.getEnd() != null && last.getEnd().isTerminus()
                && !asking.asksAbout(last.getEnd()))
            {
                return KEEP_DIRECTION;
            }
        }

        // THE DESTINATION, AND ONLY THE DESTINATION.
        //
        // **"It is unnecessary to prompt on intermediates.  We care about the reversal if it's the
        // destination, since that dictates where the train can go, and where it is facing."**
        //
        // An intermediate turning copy exists because the path chose to turn there; asking would let "keep direction"
        // strand the train off its path.
        first = destinationAskedAbout(asking, railway, path);

        // AND A TRAIN THAT CANNOT REVERSE IS NOT ASKED (MT-368): a may-turn square is a preference, and a locomotive
        // that may not run backwards has none.  The setup's `mayTurnTiles()` fires here, the graph's `hasAWayThrough`
        // on arrival (`Layout.turnsOnArrival`).
        if (first != null && loc != null && !loc.isReversible()) return KEEP_DIRECTION;

        // Nothing on this journey turns anybody, so nothing is asked and nothing is carried.
        if (first == null) return KEEP_DIRECTION;

        final Point asked = first;

        final boolean turn = ask(parent, loc, first);

        return new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                // THE ANSWER IS ABOUT THE SQUARE IT WAS ASKED ABOUT: the destination is the only one asked.
                return turn && at == asked;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                // The destination only: `Layout.shouldReverseAt` reads this to tell a turn the operator has a say over
                // from one the route requires.
                //
                // Which squares the operator has a say over, not what the answer acts on: folding the answer in
                // would make every may-reverse turning copy look compulsory after "keep direction".
                return at == asked && asking.asksAbout(at);
            }
        };
    }

    /**
     * The destination the operator is asked about for this journey, or null when nothing is asked: the deciding part
     * of `forJourney`, without the dialog.
     *
     * @param asking the setup's answer to which squares the operator has a say over
     * @param railway the railway the path was offered on, or null
     * @param path the journey
     * @return the Point asked about, or null
     */
    public static Point destinationAskedAbout(Layout.ReversalPolicy asking,
        Layout railway, List<Edge> path)
    {
        Point arrival = path == null || path.isEmpty() ? null
            : path.get(path.size() - 1).getEnd();

        // No `isTerminus()` test (OB-205): `asksAbout` already excludes compulsory turns, while `isTerminus()` would
        // also exclude the turning copy of a may-turn square, the one square this door asks about.
        if (arrival == null || !asking.asksAbout(arrival)) return null;

        // NOT WHERE THE ANSWER COULD NOT BE HONORED: no copy of the square faces on from the way the train comes, so
        // the arrival turns it whatever is said (`Layout.turnsOnArrival`).
        if (railway != null && railway.noCopyFacesOnFrom(arrival, path.get(path.size() - 1).getStart())) return null;

        return arrival;
    }

    /** The answer for a journey that passes nothing anybody would be asked about: keep the direction, ask nothing. */
    public static final Layout.ReversalPolicy KEEP_DIRECTION =
        new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive loc, Point at)
            {
                return false;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return false;
            }
        };

    /**
     * The policy both hand-driven doors hand to `executePath`.  `asksAbout` is answered from the SETUP because the
     * runtime cannot: the operator's "trains may turn around here" is `canReverse`, which `AutonomyBuilder` never
     * emits.
     *
     * @param session the setup, which knows what the operator marked
     * @param parent what to center the dialog on
     * @return the policy
     */
    public static Layout.ReversalPolicy forOperator(
        final AutonomySession session, final Component parent)
    {
        return new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive loc, Point at)
            {
                return ask(parent, loc, at);
            }

            @Override
            public boolean asksAbout(Point at)
            {
                if (at == null) return false;

                // NO `at.isReversing()` CLAUSE HERE: IT WOULD PROMPT ON COMPULSORY TURNS, AND DECLINING ONE DRIVES A
                // TRAIN OFF ITS PATH.
                //
                // A must-reverse square has ONLY turning copies, all flagged `reversing`, so the flag cannot tell it
                // from a may-turn square; declining would send a train off its path.
                //
                // The setup is the only source: `canReverse` is never emitted to `parseAuto`.
                if (session == null) return false;

                // And the operator's own marking, by NAME (what a Point carries and the station index maps back).
                org.traincontrol.automationui.TileGraph.TileKey square =
                    session.getStationIndex() == null ? null
                        : session.getStationIndex().squareOf(at.getName());

                return square != null && session.mayTurnTiles().contains(square);
            }
        };
    }

    /**
     * The index of "No" in `TrainControlUI.YES_NO_OPTS`, which is the only answer that turns a train.
     *
     * Named rather than written as 1 at the point of use, because what went wrong here was a bare
     * integer comparison quietly changing meaning when the question was reworded.
     */
    public static final int NO = 1;

    /**
     * What a dialog answer means, kept where it can be run.
     *
     * `showOptionDialog` returns an index, or `JOptionPane.CLOSED_OPTION` (-1) for Escape or the X.  Only an explicit
     * No may move a train.  Kept apart so a test can run it (`testTheDialogAnswerIsReadThroughThisRule`).
     *
     * @param chose what showOptionDialog returned
     * @return whether to turn the train round
     */
    public static boolean reverseFor(int chose)
    {
        return chose == NO;
    }

    /**
     * Puts the question to the operator and waits for the answer.  Shown on the event thread (`invokeAndWait`) from
     * the worker that dispatches the run.
     *
     * **Nothing stops the train before this is called**, so it must not be reached from inside a run: the stop rule
     * is evaluated first and the train would keep running at line speed under the dialog.  Both doors ask at departure.
     *
     * **Defaults to KEEPING the direction** (Yes): an interruption, a headless run or any failure leaves the train
     * alone.
     *
     * @param parent what to center the dialog on
     * @param train the locomotive
     * @param where the point it has reached
     * @return whether the operator asked for it to be turned
     */
    public static boolean ask(Component parent, Locomotive train, Point where)
    {
        if (train == null || where == null) return false;

        final boolean[] answer = {false};

        try
        {
            Runnable prompt = () ->
            {
                // showOptionDialog with the application's own words, not showConfirmDialog, whose buttons follow the
                // SYSTEM language (`testMessageBundles`).  It returns an INDEX, not YES_OPTION.
                int chose = JOptionPane.showOptionDialog(
                    parent instanceof TrainControlUI ? ((TrainControlUI) parent).dialogParent() : parent,
                    I18n.f("autolayout.ui.confirmManualReversal", train.getName(), where.getName()),
                    I18n.t("autolayout.ui.confirmManualReversalTitle"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[0]);

                // "KEEP DIRECTION?": YES means do NOT reverse; index 0 is Yes and the default, so a keyboard dismissal
                // is safe.
                //
        // The test is for the ANSWER, not against its opposite (`chose != 0` would turn the train on Escape, since -1
        // is not 0): only an explicit No reverses.
                answer[0] = reverseFor(chose);
            };

            if (SwingUtilities.isEventDispatchThread()) prompt.run();
            else SwingUtilities.invokeAndWait(prompt);
        }
        catch (Exception cannotAsk)
        {
            // Nothing: false stands, the answer that leaves the railway as it is.
            return false;
        }

        return answer[0];
    }
}
