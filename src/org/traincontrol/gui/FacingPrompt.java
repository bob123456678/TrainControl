package org.traincontrol.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.swing.JOptionPane;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.util.I18n;

/**
 * Which way a train pasted onto a may-reverse square should face.
 *
 * Adam, 2026-09-13: **"If pasting at a 'may reverse' square, ask what direction the train should
 * face."**
 *
 * **Why the paste cannot work it out there, and can everywhere else.**  A train faces the way it will
 * leave.  On an ordinary square the walk answers that - `facingByPath` drives the train from where it
 * is standing to where it is being put down, and the copy it arrives on says which way it points.  A
 * may-reverse square is precisely where that stops being a fact: turning round is what the square is
 * FOR, so both headings are reachable and the walk's answer is whichever copy it happened to land on.
 *
 * That is the same reason `ArrivalSidePrompt` exists one question earlier, and the two are different
 * questions about the same paste: **which side the tail lies on** (where it came from) and **which way
 * the nose points** (where it will go).  On every other square the second follows from the first; on a
 * may-reverse square neither follows from anything, which is why both are asked there and nowhere
 * else.
 *
 * **The headings offered are the ones the square can hold** - `AutonomySession.facingsFor` - not the
 * four compass points.  A square with no rail to the north cannot hold a train facing north, and
 * offering it would teach the operator that the setting does nothing.  Where only one heading is
 * possible there is nothing to ask and nothing is shown.
 *
 * **A dismissed question moves nothing.**  Adam, on the arrival-side prompt and true of this one:
 * *"Simply don't place the train, leave it on the clipboard as if no paste had been done."*  So the
 * caller asks BEFORE the move, and `null` from `forPlacement` means "leave everything as it was" -
 * which is why `wouldAsk` exists separately, exactly as it does on the sibling prompt: a null that
 * means "nothing to ask" and a null that means "the operator said no" are different answers, and
 * reading them alike is `IND9-B5`.
 */
public class FacingPrompt
{
    /**
     * Never instantiated.
     */
    private FacingPrompt()
    {
    }

    /**
     * The answer the next question gets, for a test that has to drive a real paste (MT-394).
     *
     * A modal dialog in a test hung this suite for twenty minutes on 2026-09-13, and the only claim
     * written for this question read the source instead - which is how a paste that ignored the answer
     * passed.  Null, the default, means the dialog is shown.
     */
    private static volatile org.traincontrol.automationui.TilePorts.Side answeredByATest;

    /**
     * Makes every facing question answer this without showing a dialog, until reset with null.
     *
     * For tests only.  Nothing in the application calls it.
     *
     * @param answer the heading to answer with, or null to put the dialog back
     */
    public static void answerForTests(org.traincontrol.automationui.TilePorts.Side answer)
    {
        answeredByATest = answer;
    }

    /**
     * Asks which way a train being put down should face, where that is a real question.
     *
     * @param canHold the headings this square can hold, from `AutonomySession.facingsFor`
     * @param suggested the walk's own answer, offered as the default, or null
     * @param station what to call the square in the question
     * @param parent what to centre the dialog on
     * @return the chosen heading, or null when there was nothing to ask or the question was dismissed
     */
    public static Side forPlacement(Collection<Side> canHold, Side suggested, String station,
        Component parent)
    {
        if (!wouldAsk(canHold)) return null;

        if (answeredByATest != null)
        {
            return choicesFor(canHold).contains(answeredByATest) ? answeredByATest : null;
        }

        return ask(parent, I18n.f("autolayout.ui.askFacing", station), choicesFor(canHold), suggested);
    }

    /**
     * Asks which way a locomotive should face at a home it is not standing on (Adam, 2026-09-23, OB-282).
     *
     * *"prompt the user for the direction"* - a home is set facing the way the train stands there, and a train
     * standing elsewhere has no facing on that square to take.  Return Home brings it back facing the answer.
     *
     * @param canHold the headings a train may be homed in there - copies trains may arrive at
     * @param locomotive the locomotive being homed
     * @param station what to call the square in the question
     * @param parent what to centre the dialog on
     * @return the chosen heading, or null when there was nothing to ask or the question was dismissed
     */
    public static Side forHome(Collection<Side> canHold, String locomotive, String station, Component parent)
    {
        if (!wouldAsk(canHold)) return null;

        if (answeredByATest != null)
        {
            return choicesFor(canHold).contains(answeredByATest) ? answeredByATest : null;
        }

        return ask(parent, I18n.f("autolayout.ui.askHomeFacing", locomotive, station, I18n.t("ui.main.returnHome")),
            choicesFor(canHold), null);
    }

    /**
     * Whether putting a train down here would really put a question to the operator.
     *
     * One heading is not a choice, and no headings at all is a square that cannot hold a train facing
     * anywhere - in both cases the paste carries on with the answer it already had.
     *
     * @param canHold the headings this square can hold
     * @return whether a dialog would be shown
     */
    public static boolean wouldAsk(Collection<Side> canHold)
    {
        return choicesFor(canHold).size() > 1;
    }

    /**
     * The headings to offer, in compass order so the buttons do not move between squares.
     *
     * DEDUPLICATED and ORDERED here rather than at the caller, because `facingsFor` answers per COPY:
     * a square with three copies facing east hands back east three times, and three identical buttons
     * is a dialog that cannot be answered.
     *
     * @param canHold what the square can hold
     * @return the distinct headings, north, east, south, west
     */
    public static List<Side> choicesFor(Collection<Side> canHold)
    {
        List<Side> out = new ArrayList<>();

        if (canHold == null) return out;

        for (Side side : Arrays.asList(Side.N, Side.E, Side.S, Side.W))
        {
            if (canHold.contains(side)) out.add(side);
        }

        return out;
    }

    /**
     * What a heading is called on a button: "To the North (up)", not "From the North (up)" (OB-215).
     *
     * Adam, 2026-09-13: the first cut borrowed `ArrivalSidePrompt.labelFor`, whose words answer "where
     * did the train come from" - so a question about which way a train FACES offered "from the north".
     * The two questions are about opposite ends of the train and need opposite words.
     *
     * WHOLE KEYS, one per case, for the reason `ArrivalSidePrompt.keyFor` gives: the bundle check reads
     * the source for the keys it must find, and a concatenated one would read as a key in no bundle.
     *
     * @param heading the way the train would face
     * @return the button label, in the running language
     */
    public static String labelFor(org.traincontrol.automationui.TilePorts.Side heading)
    {
        if (heading == null) return I18n.t("autolayout.ui.headingN");

        switch (heading)
        {
            case E: return I18n.t("autolayout.ui.headingE");
            case S: return I18n.t("autolayout.ui.headingS");
            case W: return I18n.t("autolayout.ui.headingW");
            default: return I18n.t("autolayout.ui.headingN");
        }
    }

    /**
     * Puts the question.
     *
     * `showOptionDialog` for the reason every dialog in this application uses it: the buttons of the
     * input dialogs follow the SYSTEM language, so a German operator gets "OK" and "Abbrechen" mixed
     * into an English window or the other way about.
     *
     * The walk's own answer is the default button where the square can hold it, so the operator who
     * presses return gets what the paste would have done without asking.
     *
     * @param parent what to centre on
     * @param question the question, already worded
     * @param sides the headings to offer
     * @param suggested the default, or null
     * @return the chosen heading, or null when dismissed
     */
    private static Side ask(Component parent, String question, List<Side> sides, Side suggested)
    {
        final Side[] answer = {null};

        try
        {
            Runnable prompt = () ->
            {
                Object[] options = new Object[sides.size()];

                for (int i = 0; i < sides.size(); i++)
                {
                    options[i] = labelFor(sides.get(i));
                }

                int preferred = suggested == null ? 0 : Math.max(0, sides.indexOf(suggested));

                int chose = JOptionPane.showOptionDialog(parent,
                    question,
                    I18n.t("autolayout.ui.askFacingTitle"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    options, options[preferred]);

                if (chose >= 0 && chose < sides.size()) answer[0] = sides.get(chose);
            };

            if (javax.swing.SwingUtilities.isEventDispatchThread()) prompt.run();
            else javax.swing.SwingUtilities.invokeAndWait(prompt);
        }
        catch (Exception cannotAsk)
        {
            // Nothing, and null stands - the paste keeps the heading the walk worked out, which is
            // what it did before this question existed.
        }

        return answer[0];
    }
}
