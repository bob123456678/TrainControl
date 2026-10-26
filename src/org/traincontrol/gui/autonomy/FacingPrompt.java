package org.traincontrol.gui.autonomy;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.swing.JOptionPane;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.util.I18n;

/**
 * Which way a train pasted onto a may-reverse square should face.  Adam: "If pasting at a 'may reverse' square, ask
 * what direction the train should face."
 *
 * The paste cannot work it out there: `facingByPath` answers on an ordinary square, but turning round is what a
 * may-reverse square is FOR, so both headings are reachable and the walk's answer is whichever copy it landed on.
 * `ArrivalSidePrompt` asks which side the tail lies on one question earlier; on a may-reverse square neither follows
 * from the other.
 *
 * The headings offered are the ones the square can hold (`AutonomySession.facingsFor`), not the four compass points;
 * where only one is possible nothing is shown.
 *
 * A dismissed question moves nothing (Adam, on the arrival-side prompt: "Simply don't place the train, leave it on the
 * clipboard as if no paste had been done."): the caller asks BEFORE the move, and `null` from `forPlacement` means
 * "leave everything as it was".  `wouldAsk` exists separately because "nothing to ask" and "the operator said no" are
 * different nulls.
 */
public class FacingPrompt
{
    /** Never instantiated. */
    private FacingPrompt()
    {
    }

    /**
     * The answer the next question gets, for a test that has to drive a real paste (MT-394): a modal dialog in a test
     * hangs the suite, and reading the source instead let a paste that ignored the answer pass.  Null means the dialog
     * is shown.
     */
    private static volatile org.traincontrol.automationui.TilePorts.Side answeredByATest;

    /**
     * Makes every facing question answer this without showing a dialog, until reset with null.  For tests only.
     *
     * @param answer the heading to answer with, or null to put the dialog back
     */
    public static void answerForTests(org.traincontrol.automationui.TilePorts.Side answer)
    {
        answeredByATest = answer;
    }

    /** What the last question answered by a test called the square, for a claim about the wording. */
    private static volatile String askedAboutByATest;

    /**
     * What the last facing question answered by a test named the square as, the text the dialog would have shown; for
     * tests only.
     *
     * @return the square's name as asked, or null when nothing has been asked
     */
    public static String lastAskedAboutForTests()
    {
        return askedAboutByATest;
    }

    /**
     * Asks which way a train being put down should face, where that is a real question.
     *
     * @param canHold the headings this square can hold, from `AutonomySession.facingsFor`
     * @param suggested the walk's own answer, offered as the default, or null
     * @param station what to call the square in the question
     * @param parent what to center the dialog on
     * @return the chosen heading, or null when there was nothing to ask or the question was dismissed
     */
    public static Side forPlacement(Collection<Side> canHold, Side suggested, String station,
        Component parent)
    {
        if (!wouldAsk(canHold)) return null;

        if (answeredByATest != null)
        {
            askedAboutByATest = station;

            return choicesFor(canHold).contains(answeredByATest) ? answeredByATest : null;
        }

        return ask(parent, I18n.f("autolayout.ui.askFacing", station), choicesFor(canHold), suggested);
    }

    /**
     * Asks which way a locomotive should face at a home it is not standing on (OB-282; Adam: "prompt the user for the
     * direction"): a home is set facing the way the train stands there, and a train elsewhere has no facing on that
     * square.
     *
     * @param canHold the headings a train may be homed in there - copies trains may arrive at
     * @param locomotive the locomotive being homed
     * @param station what to call the square in the question
     * @param parent what to center the dialog on
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
     * Whether putting a train down here would really put a question to the operator: one heading is not a choice, and
     * none is a square that cannot hold a train facing anywhere; in both the paste carries on with the answer it had.
     *
     * @param canHold the headings this square can hold
     * @return whether a dialog would be shown
     */
    public static boolean wouldAsk(Collection<Side> canHold)
    {
        return choicesFor(canHold).size() > 1;
    }

    /**
     * The headings to offer, in compass order so the buttons do not move between squares.  Deduplicated and ordered
     * here rather than at the caller, because `facingsFor` answers per COPY: three copies facing east would give three
     * identical buttons.
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
     * What a heading is called on a button: "To the North (up)", not "From the North (up)" (OB-215):
     * `ArrivalSidePrompt.labelFor` answers where the train came from, and a question about which way it FACES needs the
     * opposite end's words.  Whole keys, one per case, as `ArrivalSidePrompt.keyFor` explains: the bundle check reads
     * the source for the keys it must find.
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
     * Puts the question with `showOptionDialog`, as every dialog here does: the buttons of the input dialogs follow the
     * SYSTEM language.  The walk's own answer is the default button where the square can hold it, so return gives what
     * the paste would have done without asking.
     *
     * @param parent what to center on
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
            // Nothing, and null stands, which every caller reads as a dismissal: the paste moves nothing and the home
            // door sets no home (OB-282).
        }

        return answer[0];
    }
}
