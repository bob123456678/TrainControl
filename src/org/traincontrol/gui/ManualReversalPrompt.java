package org.traincontrol.gui;

import java.awt.Component;
import javax.swing.JOptionPane;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * The question a hand-driven send asks before turning a train round.
 *
 * Adam, 2026-09-06: **"in manual mode, if the user decides to send a train to a 'may reverse' point,
 * explicitly ask the user if the train should change direction.  Make sure this gets asked both in the
 * locomotive commands tab, and when fired from the track."**
 *
 * **Why it has to be asked rather than worked out.** A reversing point turns whatever passes it, and
 * the reason for the turn lives in the leg AFTER this one - the train may be going there to back into
 * a berth next time, or may simply be passing through. Adam: *"the system has no way of knowing that
 * the intention is to reverse into a berth on the next turn.  So we can't possibly both allow that and
 * disallow it based on train."* Nothing in the path says which, so the operator says.
 *
 * **A true terminus is never asked about.** There the train has run out of track and the next move is
 * back the way it came, so the turn is not a choice - `Layout.executePathInternal` does not consult the
 * policy at a terminus at all. That is the whole of *"no unprompted reversals in manual mode unless
 * going to a true terminus (must reverse)"*.
 *
 * **One class for both doors.** Two spellings of one question drift into two different questions, and
 * the one that drifts is usually the one that stops asking - which is this codebase's commonest defect
 * and the reason `RouteCommand.isNameUsable` says the doors have to agree.
 *
 * @author Adam
 */
public final class ManualReversalPrompt
{
    private ManualReversalPrompt()
    {
    }

    /**
     * Asks about the whole journey BEFORE it starts, and answers for every point on it.
     *
     * Adam, 2026-09-06: **"I see the prompt now, but it is shown on arrival... make it be on
     * departure itself, that way there is no dispatch prior to user input."**
     *
     * **The question was right and its moment was wrong.**  Asked from inside the run, the train has
     * already been dispatched, already reserved its path and already travelled - and `DIR-A1` had to
     * stop it mid-journey so the dialog was not answered by somebody watching a moving train.  Asked
     * before departure there is nothing to stop: the train is standing where the operator left it, and
     * the answer is carried into the run.
     *
     * **Once for the journey, not once per point.**  A path may pass several squares trains may turn
     * at; being asked at each is unusable, and would let one journey end up half in each state.  The
     * answer given at departure is the answer everywhere on that path - which is also what makes it
     * answerable, since the operator is choosing what this MOVE is for.
     *
     * Nothing is asked when the path reaches no such square, so an ordinary send is unchanged.
     *
     * @param session the setup, which knows what the operator marked
     * @param parent what to centre the dialog on
     * @param path the journey about to be run
     * @param loc the train
     * @return the policy to hand to executePath
     */
    public static org.traincontrol.automation.Layout.ReversalPolicy forJourney(
        final org.traincontrol.automationui.AutonomySession session, final Component parent,
        final java.util.List<org.traincontrol.automation.Edge> path, final Locomotive loc)
    {
        final org.traincontrol.automation.Layout.ReversalPolicy asking = forOperator(session, parent);

        Point first = null;

        if (path != null)
        {
            for (org.traincontrol.automation.Edge edge : path)
            {
                if (edge == null || edge.getEnd() == null) continue;

                // A TERMINUS IS NOT ASKED ABOUT, here as inside the run: the train has run out of
                // track and the turn is how it gets there at all.
                if (edge.getEnd().isTerminus()) continue;

                if (asking.asksAbout(edge.getEnd()))
                {
                    first = edge.getEnd();

                    break;
                }
            }
        }

        // Nothing on this journey turns anybody, so nothing is asked and nothing is carried.
        if (first == null) return KEEP_DIRECTION;

        final boolean turn = ask(parent, loc, first);

        return new org.traincontrol.automation.Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return turn;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                // The same squares, so the run still STOPS at them - a train that is about to be
                // turned has to be standing still whether or not anybody is asked at that moment.
                return asking.asksAbout(at);
            }
        };
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
     * The policy both hand-driven doors hand to `executePath`.
     *
     * **`asksAbout` is answered from the SETUP, because the runtime cannot answer it.**  The
     * operator's "trains may turn round here" is `canReverse`, and `AutonomyBuilder` never emits it:
     * *"it is the instruction to split, not something parseAuto knows"*.  It is expressed by
     * splitting the square, and where a square cannot be split the instruction leaves no trace at all.
     *
     * Adam met exactly that - he marked a square may-reverse, sent a train to it, and nothing asked.
     * Twice, because the first two fixes both looked for a flag in the running layout.
     *
     * @param session the setup, which knows what the operator marked
     * @param parent what to centre the dialog on
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

                // The runtime's own half first - a square the build DID split carries the flag on its
                // turning copy, and that is knowable here without the setup.
                if (at.isReversing()) return true;

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
     * Puts the question to the operator and waits for the answer.
     *
     * **On the event thread, from a thread that is not it.** Both callers dispatch on a worker -
     * `executePath` blocks until the train arrives, so it cannot run on the event thread - and a modal
     * dialog has to be shown on it. `invokeAndWait` is what makes the answer available to the caller
     * that needs it.
     *
     * **The train is stopped before this is called**, and that was NOT true when this sentence first
     * claimed it.  Everything that stopped the train was inside the branch the answer decides, so it
     * ran past the point at line speed for as long as the dialog stood there - measured at 30 when
     * the question was put and still 30 five seconds later (`DIR-A1`).  A comment asserting the
     * comfortable version of what the code does is how that survived being written and reviewed.
     * `executePathInternal` now stops at any reversing point before deciding anything.
     *
     * **Defaults to KEEPING the direction**, which is Yes and which changes nothing. An interruption, a headless run or anything that goes wrong here
     * leaves the train pointing the way it already points, which is the answer that changes nothing on
     * the railway. Turning a train because a dialog failed is the worse of the two mistakes.
     *
     * @param parent what to centre the dialog on
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
                // showOptionDialog with the application's own words, NOT showConfirmDialog.
                //
                // A plain confirmation takes its buttons from the look-and-feel, which follows the
                // SYSTEM language rather than the one the user chose - so a German operator running an
                // English Windows gets a German question with English buttons.  `testMessageBundles`
                // enforces this and caught it here.
                //
                // It returns an INDEX into the array, not YES_OPTION, which is the other half of what
                // that check exists to stop.
                int chose = JOptionPane.showOptionDialog(parent,
                    I18n.f("autolayout.ui.confirmManualReversal", train.getName(), where.getName()),
                    I18n.t("autolayout.ui.confirmManualReversalTitle"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[0]);

                // "KEEP DIRECTION?" - so YES means do NOT reverse (Adam, 2026-09-06).
                //
                // **"It should be a yes/no keep direction, with no meaning change, and yes being
                // default."**  The question used to be "should it change direction here?", which put
                // the action on Yes and the safe answer on No - and a dialog whose default is the
                // one that does something is the wrong way round for a train standing on a headshunt.
                //
                // Index 0 is Yes, and it is the default: keeping the direction changes nothing on the
                // railway, so dismissing this dialog with the keyboard is safe.
                answer[0] = chose != 0;
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
