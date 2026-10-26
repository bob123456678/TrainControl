package org.traincontrol.gui.autonomy;

import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.util.I18n;

/**
 * Where the tail of a hand-placed train lies.
 *
 * Adam, 2026-09-07: **"outside of running autonomy/manual commands, if trains are placed/pasted by the
 * user on a 'can reverse' station, ask the user in the popup where the tail of the train is (where it
 * arrived from).  Otherwise, assume 'arrived from' to be the opposite of their facing on normal
 * stations, and if the station is a terminus, there is only one forced option."**
 *
 * **Three cases, and only one of them is a question.**  Autonomy writes this itself as a train arrives,
 * because the arrival knows.  A train put down by hand has no arrival, so it has to be worked out:
 *
 * - **A terminus** has one way in, so there is nothing to ask.  Whatever the train is doing, it came
 *   from the only direction that exists.
 * - **An ordinary station** is answered by the heading: a train faces the way it will leave and arrived
 *   from behind, so the tail is on the side it is not pointing at.  The side it names is one of the square's OWN,
 *   never a compass point the railway does not have there (see `arrivedFrom`).
 * - **A may-reverse station** is where that assumption stops holding, and it is the reason this class
 *   exists.  Turning round is what those squares are FOR, so a train standing on one is as likely to
 *   have backed in as to have driven in, and the two put its tail on opposite sides.  Guessing there
 *   would block one switch while leaving the one it is actually fouling open, which is worse than
 *   blocking nothing: it reports a protection that is not there.
 *
 * The sides offered are the ones the railway actually has, not the four compass points.  A question
 * whose answer is "north" about a square with no track to the north teaches the operator that the
 * setting does nothing.
 */
public class ArrivalSidePrompt
{
    /**
     * Never instantiated.
     */
    private ArrivalSidePrompt()
    {
    }

    /**
     * The answer the next arrival-side question gets, for a test that drives a real paste (MT-394).  A may-reverse
     * square puts this question before the facing one.  Null, the default, means the dialog is shown.
     */
    private static volatile String answeredByATest;

    /**
     * Makes every arrival-side question answer this side without showing a dialog, until reset with null.  For tests
     * only.
     *
     * @param side "N", "E", "S" or "W", or null to put the dialog back
     */
    public static void answerForTests(String side)
    {
        answeredByATest = side;
    }

    /** What the last question answered by a test called the square, for a claim about the wording. */
    private static volatile String askedAboutByATest;

    /**
     * What the last arrival-side question answered by a test named the square as.  For tests only.
     *
     * @return the square's name as asked, or null when nothing has been asked
     */
    public static String lastAskedAboutForTests()
    {
        return askedAboutByATest;
    }

    /**
     * What the arrival-side question calls the square: its name, not the copy's.  A split square's Points are named
     * with a heading ("BottomMainB (eastbound)") and this question is ABOUT direction, so the copy's name put an
     * arbitrary answer in the question.  The facing question after it names the square the same way (`TrainControlUI`'s
     * paste door).
     *
     * @param at the Point the train is being put on
     * @return the name the question shows
     */
    static String squareNameFor(Point at)
    {
        return org.traincontrol.automationui.StationIndex.withoutArrivalSuffix(at.getName());
    }

    /**
     * The side a hand-placed train should be recorded as having arrived from, asking if it must.
     *
     * Two surfaces: this door asks in a dialog of its own, right for a menu item that places a train with none;
     * `GraphLocAssign` already has a dialog, so it offers the same choice as a combo.  The rule is `suggestedFor` and
     * the question is `ask`; this dispatches to both rather than spelling the rule twice.
     *
     * @param layout the running layout, which knows where the neighbors are
     * @param at the point the train is being put down on
     * @param facing which way it points, or null when nobody has said
     * @param mayReverse whether the operator marked this square as one trains may turn at
     * @param parent what to center the dialog on
     * @param arrivalSides the build's own sides, where the caller has them
     * @return the side, or null when it cannot be worked out and was not answered
     */
    public static String forPlacement(Layout layout, Point at, String facing, boolean mayReverse,
        Component parent,
        List<org.traincontrol.automationui.TilePorts.Side> arrivalSides)
    {
        // AND THE QUESTION, on the squares where the assumption does not hold, asked through `wouldAsk`: the paste door
        // reads that same predicate to tell a dismissal from the other two nulls, so they agree by construction.
        if (wouldAsk(layout, at, mayReverse, arrivalSides))
        {
            return ask(parent, at, choicesFor(layout, at, arrivalSides));
        }

        return suggestedFor(layout, at, facing, mayReverse, arrivalSides);
    }

    /**
     * What the rule alone says, with nobody asked: the whole of `forPlacement` except the dialog, and the half a form
     * can use as a visible starting value.
     *
     * - **A terminus** has one way in: `sides` holds one entry and it is the answer.  A square with no track answers
     * null.  - **An ordinary station** is answered by the heading (it faces the way it will leave and arrived from
     * behind), out
     *   of the sides this square actually has.
     * - **A may-reverse station** answers NULL, because turning round is what those squares are for.  `forPlacement`
     *   sends it to the operator; a form can pass `mayReverse` false to get the assumption as a starting value the
     *   operator can see and correct, which a guess inside a popup is not (`GraphLocAssign` does).
     *
     * @param layout the running layout
     * @param at the point the train is being put down on
     * @param facing which way it points, or null when nobody has said
     * @param mayReverse whether the operator marked this square as one trains may turn at
     * @param arrivalSides the build's own sides, where the caller has them
     * @return the side the rule gives, or null where only the operator can say
     */
    public static String suggestedFor(Layout layout, Point at, String facing, boolean mayReverse,
        List<org.traincontrol.automationui.TilePorts.Side> arrivalSides)
    {
        List<String> sides = choicesFor(layout, at, arrivalSides);

        // NOTHING TO CHOOSE BETWEEN. A terminus has one way in, and a square with no track at all has
        // none - both answer themselves, and asking would be a dialog with one button.
        if (sides.isEmpty()) return null;

        if (sides.size() == 1) return sides.get(0);

        // THE SQUARES WHERE THE ASSUMPTION DOES NOT HOLD are not this method's to answer.
        if (mayReverse) return null;

        // THE ASSUMPTION, on a station where it holds: a train faces the way it will leave, so it came
        // from behind.  Answered out of THE SIDES THIS SQUARE ACTUALLY HAS rather than off the
        // compass - see `arrivedFrom` for why those are not the same question on a curve.
        return arrivedFrom(sides, facing);
    }

    /**
     * The sides a placement here may be recorded as having come from: the BUILD'S SIDES when the caller has them
     * (every caller in the application).  The fallback for a caller with no session reads `Layout.entrySideOf` too, so
     * hand-built test Layouts get the geometry they always got.  One place, because the dialog's buttons, the combo's
     * entries and `wouldAsk` all read this list and would drift silently if assembled separately.
     *
     * @param layout the running layout
     * @param at the point
     * @param arrivalSides the build's own sides, where the caller has them
     * @return the sides, in a stable order, never null
     */
    public static List<String> choicesFor(Layout layout, Point at,
        List<org.traincontrol.automationui.TilePorts.Side> arrivalSides)
    {
        if (layout == null || at == null) return new ArrayList<>();

        return arrivalSides == null || arrivalSides.isEmpty()
            ? sidesOf(layout, at) : sidesOf(arrivalSides);
    }

    /**
     * Behind a train that drove in forwards, named in the vocabulary the tail walk compares against.  The sides are
     * given and the facing chooses between them, not the other way about: "the opposite of the facing" is a compass
     * answer, which on a curve names a side no edge carries (OB-182 moved the offered sides, the written arrival side
     * and the walk onto the build's entry sides, and this branch was missed).
     *
     * - **One way in that is not the way it is pointing** - the whole answer on a two-sided square, straight or curved.
     * - **Otherwise the compass assumption (it drove straight through), but only where the build enters by that side.**
     * - **Otherwise nothing.**  A NULL arrival side narrows the walk and blocks less, while a WRONG one sends it down
     *   track the train is not on.  Claiming least is also what a dismissed dialog answers.
     *
     * @param sides the sides track reaches this square by - the build's where the caller has them
     * @param facing which way its front points, or null when nobody has said
     * @return the side to record, or null when this cannot be worked out
     */
    private static String arrivedFrom(List<String> sides, String facing)
    {
        // A facing nobody has set is no evidence at all, and blocking track on it would be a guess
        // wearing a measurement's clothes.
        if (facing == null || sides == null) return null;

        String front = facing.toUpperCase();

        List<String> behind = new ArrayList<>();

        for (String side : sides)
        {
            if (side != null && !side.equalsIgnoreCase(front)) behind.add(side);
        }

        if (behind.size() == 1) return behind.get(0);

        String opposite = opposite(front);

        if (opposite != null)
        {
            for (String side : sides)
            {
                if (opposite.equalsIgnoreCase(side)) return opposite;
            }
        }

        return null;
    }

    /**
     * Whether a placement here would actually put a question to the operator.  `forPlacement` returns null in three
     * situations and only one is "the operator said no"; the paste door read all three as a dismissal and silently
     * refused every paste on a square with no placeable sides.  So the door asks this, the exact condition under which
     * `forPlacement` reaches `ask`, rather than inferring it from a null.
     *
     * @param layout the running layout
     * @param at the point being placed on
     * @param mayReverse whether the operator marked this square as one trains may turn at
     * @return whether a dialog would be shown
     */
    public static boolean wouldAsk(Layout layout, Point at, boolean mayReverse,
        List<org.traincontrol.automationui.TilePorts.Side> arrivalSides)
    {
        if (!mayReverse) return false;

        // THE SAME LIST the paste door reads from `forPlacement` (via `choicesFor`), so the two cannot come apart.
        return choicesFor(layout, at, arrivalSides).size() > 1;
    }

    /**
     * Puts the question to the operator.
     *
     * `showOptionDialog` rather than `showInputDialog`, for the reason every dialog in this
     * application uses it: the buttons of the input dialogs follow the SYSTEM language, so a German
     * operator gets "OK" and "Abbrechen" mixed into an English window or the other way about.
     *
     * A dismissed dialog answers null - nothing is recorded, and nothing is blocked.  That is the
     * answer that claims least: a tail nobody has placed blocks no track, which is the state the
     * railway was in before the question was asked.
     *
     * @param parent what to center on
     * @param at the point
     * @param sides the sides that actually have track
     * @return the chosen side, or null when dismissed
     */
    private static String ask(Component parent, Point at, List<String> sides)
    {
        if (answeredByATest != null)
        {
            askedAboutByATest = squareNameFor(at);

            for (String side : sides)
            {
                if (answeredByATest.equalsIgnoreCase(side)) return side;
            }

            return sides.isEmpty() ? null : sides.get(0);
        }

        final String[] answer = {null};

        try
        {
            Runnable prompt = () ->
            {
                Object[] options = new Object[sides.size()];

                for (int i = 0; i < sides.size(); i++)
                {
                    options[i] = labelFor(sides.get(i));
                }

                int chose = JOptionPane.showOptionDialog(parent,
                    I18n.f("autolayout.ui.askArrivalSide", squareNameFor(at)),
                    I18n.t("autolayout.ui.askArrivalSideTitle"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    options, options[0]);

                if (chose >= 0 && chose < sides.size()) answer[0] = sides.get(chose);
            };

            if (javax.swing.SwingUtilities.isEventDispatchThread()) prompt.run();
            else javax.swing.SwingUtilities.invokeAndWait(prompt);
        }
        catch (Exception cannotAsk)
        {
            // Nothing, and null stands. See the note above: an unanswered question blocks no track.
        }

        return answer[0];
    }

    /**
     * What one side is called where an operator reads it, for both surfaces (the dialog's buttons and the combo in
     * `GraphLocAssign`) so a side is worded the same wherever it is offered.
     *
     * @param side "N", "S", "E" or "W"
     * @return the operator's name for it
     */
    public static String labelFor(String side)
    {
        return I18n.t(keyFor(side));
    }

    /**
     * The message key naming one side, written out so the bundle check can see it.
     *
     * WHOLE KEYS, not a prefix plus a letter: the bundle check reads the source for the keys it must find, and a
     * concatenated one reads as "autolayout.ui.side", which is in no bundle.
     *
     * @param side "N", "S", "E" or "W"
     * @return the key, or the north one for anything unrecognized
     */
    private static String keyFor(String side)
    {
        switch (side)
        {
            case "S": return "autolayout.ui.sideS";
            case "E": return "autolayout.ui.sideE";
            case "W": return "autolayout.ui.sideW";
            default: return "autolayout.ui.sideN";
        }
    }

    /**
     * The sides this point actually has track on, asked of the graph rather than of the compass.
     *
     * Both directions, because a tail is not directional - the train lies across that rail whether the
     * graph runs traffic into this point along it or out.
     *
     * **Through `Layout.entrySideOf`, the ONE definition.**  `sideTowards` named where the neighboring POINT lies,
     * which on a curve is a side the metal does not leave by.  `entrySideOf` is the build's answer where an edge
     * carries one and that geometry otherwise, so hand-built test Layouts get what they always got.
     *
     * @param layout the layout
     * @param at the point
     * @return the sides, in a stable order
     */
    public static List<String> sidesOf(Layout layout, Point at)
    {
        Set<String> sides = new LinkedHashSet<>();

        for (Edge edge : layout.getNeighborsAndIncoming(at))
        {
            String side = layout.entrySideOf(edge, at);

            if (side != null) sides.add(side);
        }

        return new ArrayList<>(sides);
    }

    /**
     * The sides track actually reaches this square by, as the BUILD has them.  The geometric form above answers with
     * the compass direction of the neighboring POINT, the far end of a reduced edge that may turn corners: a rail
     * leaving north and curving east gives "E" while the metal leaves by "N".  The builder already knows the answer
     * (`arrivalSides` to `StationIndex` to `AutonomyBuilder.arrivalSidesOf`, each reduced edge's ENTRY SIDE); this is
     * the same door `facingChoices` was moved onto.
     *
     * @param sides the arrival sides from the session, as the build split them
     * @return their names, in the build's own order
     */
    public static List<String> sidesOf(List<org.traincontrol.automationui.TilePorts.Side> sides)
    {
        Set<String> out = new LinkedHashSet<>();

        if (sides != null)
        {
            for (org.traincontrol.automationui.TilePorts.Side side : sides)
            {
                if (side != null) out.add(side.name());
            }
        }

        return new ArrayList<>(out);
    }

    /**
     * The other side of the compass, which is where a train that drove in forwards has its tail.
     *
     * @param facing the side its front faces
     * @return the opposite side, or null when the facing is not one this understands
     */
    public static String opposite(String facing)
    {
        if (facing == null) return null;

        switch (facing.toUpperCase())
        {
            case "N": return "S";
            case "S": return "N";
            case "E": return "W";
            case "W": return "E";
            default: return null;
        }
    }
}
