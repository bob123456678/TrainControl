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
     * Puts the question to the operator and waits for the answer.
     *
     * **On the event thread, from a thread that is not it.** Both callers dispatch on a worker -
     * `executePath` blocks until the train arrives, so it cannot run on the event thread - and a modal
     * dialog has to be shown on it. `invokeAndWait` is what makes the answer available to the caller
     * that needs it, and the train is standing still at the point while this is asked.
     *
     * **Defaults to NOT turning.** An interruption, a headless run or anything that goes wrong here
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
                    TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]);

                // Index 0 is Yes.  The DEFAULT is index 1 - No - so dismissing the dialog with the
                // keyboard leaves the train pointing the way it already points.
                answer[0] = chose == 0;
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
