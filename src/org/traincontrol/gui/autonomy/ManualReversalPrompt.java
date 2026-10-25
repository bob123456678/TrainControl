package org.traincontrol.gui.autonomy;

import java.awt.Component;
import javax.swing.JOptionPane;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * The question a hand-driven send asks before turning a train round, from both the locomotive commands tab and the
 * track (one class, so the two doors cannot drift into two questions).
 *
 * It has to be asked rather than worked out: a reversing point turns whatever passes it, and the reason lives in the
 * leg AFTER this one (the train may be going there to back into a berth next time).  A true terminus is never asked
 * about: the turn is not a choice, and `Layout.executePathInternal` does not consult the policy there.
 *
 * @author Adam
 */
public final class ManualReversalPrompt
{
    private ManualReversalPrompt()
    {
    }

    /**
     * Asks about the whole journey BEFORE it starts, and answers for every point on it.  Asked from inside the run the
     * train would already be dispatched and have to be stopped mid-journey; asked before departure it is standing and
     * the answer is carried into the run.  Once for the journey, not once per point, or one journey could end up half
     * in each state.  Nothing is asked when the path reaches no such square.
     *
     * @param session the setup, which knows what the operator marked
     * @param parent what to center the dialog on
     * @param path the journey about to be run
     * @param loc the train
     * @param railway the railway the path was offered on, or null
     * @return the policy to hand to executePath
     */
    public static org.traincontrol.automation.Layout.ReversalPolicy forJourney(
        final org.traincontrol.automationui.AutonomySession session, final Component parent,
        final java.util.List<org.traincontrol.automation.Edge> path, final Locomotive loc,
        final org.traincontrol.automation.Layout railway)
    {
        final org.traincontrol.automation.Layout.ReversalPolicy asking = forOperator(session, parent);

        Point first = null;

        // A JOURNEY THAT ENDS AT A TERMINUS IS NOT ASKED ABOUT AT ALL.
        //
        // `Layout.shouldReverseAt` answers a journey to a terminus from the flag and ignores any
        // policy - and it is right to: the reversal on the way is how a train backs into a terminus,
        // and `testATrainThatCannotReverseMayBackIntoATerminus` fails the moment that becomes a
        // preference.  MT-245 is Adam's own ruling on it.
        //
        // So the dialog that used to be shown for these journeys was reporting a decision that was
        // never taken: the operator was asked, answered, and the answer was thrown away.  A question
        // nobody acts on is worse than no question, because it teaches the operator that the setting
        // does something.
        if (path != null && !path.isEmpty())
        {
            org.traincontrol.automation.Edge last = path.get(path.size() - 1);

            // UNLESS THE DOOR ASKS ABOUT IT (OB-205): `isTerminus()` is true at the turning copy of a MAY-turn square
            // as well as at a real terminus, while `asksAbout` is answered from the setup (`mayTurnTiles()`, the
            // reversible squares minus the compulsory ones) and separates the two where the flag cannot.
            if (last != null && last.getEnd() != null && last.getEnd().isTerminus()
                && !asking.asksAbout(last.getEnd()))
            {
                return KEEP_DIRECTION;
            }
        }

        // THE DESTINATION, AND ONLY THE DESTINATION (Adam, 2026-09-07).
        //
        // **"It is unnecessary to prompt on intermediates.  We care about the reversal if it's the
        // destination, since that dictates where the train can go, and where it is facing."**
        //
        // This used to name the destination when it qualified and fall back to the first may-turn
        // square on the route otherwise.  That was the defect: a journey could depend on
        // a turn at an intermediate, the operator was asked about it, and "keep direction" - the
        // default, the Escape answer and the cannot-ask answer - stranded the train off its path.
        //
        // Asking only about the end removes the question from every square where the answer was not
        // the operator's to give.  An intermediate turning copy exists BECAUSE the path chose to turn
        // there; that is the route's business, and it now behaves exactly as it does for autonomy.
        // The destination is different: which way a train faces when it stops decides where it can go
        // next, and that is a decision rather than a consequence.
        first = destinationAskedAbout(asking, railway, path);

        // AND A TRAIN THAT CANNOT REVERSE IS NOT ASKED (MT-368): a square that MAY turn is a preference, and a
        // locomotive that may not run backwards has no preference to express.
        //
        // Only where the turn is optional, which is what `asksAbout` already means (`mayTurnTiles()`); a terminus is
        // not reached by this line, and must not be, as backing in is how a non-reversible train gets there (MT-245).
        // `Layout.turnsOnArrival` carries the matching rule: the policy returned here says "no opinion" rather than
        // "no".
        //
        // THE TWO HALVES ASK DIFFERENT QUESTIONS.  This half fires on `asksAbout`, the SETUP's `mayTurnTiles()`; the
        // arrival's half fires on `hasAWayThrough`, the GRAPH's "some copy of this square is neither a terminus nor a
        // reversing point".  They differ at a square marked `canReverse` where an arriving train has nowhere to go but
        // back (`AutonomyBuilder` emits no plain copy there): the train IS turned, as a dead end must turn it (MT-245),
        // while this line has decided not to ask.  Unreachable on Adam's railway, and kept as two rules because the
        // setup is what the operator edited.
        if (first != null && loc != null && !loc.isReversible()) return KEEP_DIRECTION;

        // Nothing on this journey turns anybody, so nothing is asked and nothing is carried.
        if (first == null) return KEEP_DIRECTION;

        final Point asked = first;

        final boolean turn = ask(parent, loc, first);

        return new org.traincontrol.automation.Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                // THE ANSWER IS ABOUT THE SQUARE IT WAS ASKED ABOUT, and no other.
                //
                // It used to be returned for every point on the path, so "yes" turned the train at a
                // plain copy it merely passed through as well as at the end.  Now the destination is
                // the only square anybody is asked about, so it is the only square the answer speaks
                // for.
                return turn && at == asked;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                // THE DESTINATION ONLY, which is what makes an intermediate turning copy behave as it
                // does for autonomy: `Layout.shouldReverseAt` reads this to tell a turn the operator
                // has a say over from one the route requires, and an intermediate is now the latter.
                //
                // WHICH SQUARES THE OPERATOR HAS A SAY OVER - not which ones the answer acts on.
                //
                // This briefly returned `turn && asking.asksAbout(at)`, to stop a journey nobody was
                // turning from braking at every may-turn square on it.  That made the
                // answer change the QUESTION, and `Layout.shouldReverseAt` reads this to tell a
                // compulsory turn from a may-reverse one: a reversing copy nobody is asked about is a
                // compulsory turn.  So "keep direction" made every may-reverse turning copy look
                // compulsory, and the train turned against the operator's explicit no.
                //
                // That braking is fixed where it belongs instead: the stop now asks `shouldReverseAt`
                // itself, so no stop happens where no turn happens, and this answer can go back to
                // being about the railway rather than about what was said.
                return at == asked && asking.asksAbout(at);
            }
        };
    }

    /**
     * The destination the operator is asked about for this journey, or null when nothing is asked - the part of
     * `forJourney` that decides, without the dialog, so that it can be asked.
     *
     * @param asking the setup's answer to which squares the operator has a say over
     * @param railway the railway the path was offered on, or null
     * @param path the journey
     * @return the Point asked about, or null
     */
    public static Point destinationAskedAbout(org.traincontrol.automation.Layout.ReversalPolicy asking,
        org.traincontrol.automation.Layout railway, java.util.List<org.traincontrol.automation.Edge> path)
    {
        Point arrival = path == null || path.isEmpty() ? null
            : path.get(path.size() - 1).getEnd();

        // No `isTerminus()` test (OB-205): `asksAbout` is `mayTurnTiles()`, which already excludes the compulsory
        // turns, while `isTerminus()` would also exclude the turning copy of a may-turn square, the one square this
        // door exists to ask about.
        if (arrival == null || !asking.asksAbout(arrival)) return null;

        // NOT WHERE THE ANSWER COULD NOT BE HONORED: no copy of the square faces on from the way the train
        // comes, so the arrival turns it whatever is said - `Layout.turnsOnArrival`, the same question
        if (railway != null && railway.noCopyFacesOnFrom(arrival, path.get(path.size() - 1).getStart())) return null;

        return arrival;
    }

    /**
     * The answer for a journey that passes nothing anybody would be asked about.
     *
     * Keeps the direction and asks about nothing, so an ordinary send neither stops nor prompts.
     */
    public static final org.traincontrol.automation.Layout.ReversalPolicy KEEP_DIRECTION =
        new org.traincontrol.automation.Layout.ReversalPolicy()
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
     * runtime cannot answer it: the operator's "trains may turn round here" is `canReverse`, which `AutonomyBuilder`
     * never emits (it is the instruction to split), so where a square cannot be split the instruction leaves no trace.
     *
     * @param session the setup, which knows what the operator marked
     * @param parent what to center the dialog on
     * @return the policy
     */
    public static org.traincontrol.automation.Layout.ReversalPolicy forOperator(
        final org.traincontrol.automationui.AutonomySession session, final Component parent)
    {
        return new org.traincontrol.automation.Layout.ReversalPolicy()
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

                // THE `at.isReversing()` CLAUSE THAT USED TO BE HERE PROMPTED ON
                // COMPULSORY TURNS, AND DECLINING ONE DRIVES A TRAIN OFF ITS PATH.
                //
                // For a must-reverse square `AutonomyBuilder` emits ONLY turning copies and flags
                // every one of them `reversing`, so "is this copy reversing" cannot tell a square the
                // operator marked may-turn from one the railway turns every train at.  The clause
                // below carefully excludes `mandatoryTurnTiles()`; that one put them straight back.
                //
                // What made it dangerous rather than merely wrong is the default.  A turning copy's
                // only outgoing edges leave by the side the train came in at, so "keep direction" -
                // which is the default answer, the Escape answer and the cannot-ask answer - sends a
                // train forward onto track its path does not hold.
                //
                // So the setup is the only source, which is right for a second reason: `canReverse`
                // is never emitted to `parseAuto` at all, so the runtime cannot answer this question
                // and anything here that appears to answer it is answering a different one.
                if (session == null) return false;

                // And the operator's own marking, for every square including the ones the build could
                // not express.  By NAME, because that is what a Point carries and what the station
                // index maps back to a square.
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
     * `showOptionDialog` returns an index into the options array, or `JOptionPane.CLOSED_OPTION` (-1)
     * when the operator pressed Escape or used the X.  Three inputs, and only one of them may move a
     * train.
     *
     * Separated from the dialog because the dialog cannot be shown in a test and this can - and the
     * one line left behind at the call site is asserted as a call site by `testTheDialogAnswerIsRead
     * ThroughThisRule`, so `extracted-rule-moves-the-bug-to-the-call` does not get a second go.
     *
     * @param chose what showOptionDialog returned
     * @return whether to turn the train round
     */
    public static boolean reverseFor(int chose)
    {
        return chose == NO;
    }

    /**
     * Puts the question to the operator and waits for the answer.
     *
     * **On the event thread, from a thread that is not it.** Both callers dispatch on a worker -
     * `executePath` blocks until the train arrives, so it cannot run on the event thread - and a modal
     * dialog has to be shown on it. `invokeAndWait` is what makes the answer available to the caller
     * that needs it.
     *
     * **NOTHING STOPS THE TRAIN BEFORE THIS IS CALLED ANY MORE, so it must not be reached from inside
     * a run.**  This sentence has now been wrong twice, in opposite directions, which is the reason it
     * is written out rather than trimmed.
     *
     * It first claimed the train was stopped when it was not: everything that stopped it was inside
     * the branch the answer decides, so it ran past the point at line speed for as long as the dialog
     * stood there - measured at 30 when the question was put and still 30 five seconds later.
     * Then `executePathInternal` was made to stop at any reversing point first, and the
     * sentence became true.  Then the stop was collapsed into the rule itself - a train is stopped
     * exactly when it is about to be turned - and the rule is evaluated BEFORE the stop, so it is
     * false again.
     *
     * It is safe today only because nothing calls this from inside a run: both doors ask at departure
     * and hand `executePath` a constant answer.  `forOperator` is public, and a future caller that
     * hands it straight to `executePath` gets the first failure back in full.  A comment asserting the
     * comfortable version of what the code does is how the first one survived being written and
     * reviewed.
     *
     * **Defaults to KEEPING the direction**, which is Yes and changes nothing: an interruption, a headless run or
     * anything that goes wrong here leaves the train pointing the way it already does, and turning a train because a
     * dialog failed is the worse mistake.
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
                // showOptionDialog with the application's own words, NOT showConfirmDialog: a plain confirmation takes
                // its buttons from the look-and-feel, which follows the SYSTEM language (`testMessageBundles` enforces
                // this).  It returns an INDEX into the array, not YES_OPTION.
                int chose = JOptionPane.showOptionDialog(parent,
                    I18n.f("autolayout.ui.confirmManualReversal", train.getName(), where.getName()),
                    I18n.t("autolayout.ui.confirmManualReversalTitle"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[0]);

                // "KEEP DIRECTION?" - so YES means do NOT reverse: index 0 is Yes and the default, and keeping the
                // direction changes nothing on the railway, so dismissing this dialog with the keyboard is safe.
                //
                // AND IT WAS NOT, FOR HALF A DAY.  The line below read `chose != 0`, and
                // `showOptionDialog` answers -1 for a dialog closed with Escape or the X - so -1 was
                // not 0, and dismissing this turned the train at every asking square on the journey.
                // The comment three lines up promised the opposite in the same commit that broke it.
                //
                // The polarity flip is where it came from.  While the question was "should it change
                // direction here?", the action sat on Yes and `chose == 0` was dismiss-safe by
                // accident.  Turning it into "keep direction?" moved the action to No and the
                // negation went with it, which quietly promoted every non-Yes answer - including the
                // one that means "I did not answer" - to the one that moves a train.
                //
                // So the test is for the ANSWER, not against its opposite: only an explicit No
                // reverses.  Anything else - Yes, Escape, the X, a dialog that could not be shown -
                // leaves the train pointing the way it already points.
                answer[0] = reverseFor(chose);
            };

            if (javax.swing.SwingUtilities.isEventDispatchThread()) prompt.run();
            else javax.swing.SwingUtilities.invokeAndWait(prompt);
        }
        catch (Exception cannotAsk)
        {
            // Nothing, and false stands.  See the note above: not turning is the answer that leaves the
            // railway as it is.
            return false;
        }

        return answer[0];
    }
}
