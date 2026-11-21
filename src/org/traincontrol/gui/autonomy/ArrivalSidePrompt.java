package org.traincontrol.gui.autonomy;

import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.util.I18n;

/**
 * Where the tail of a hand-placed train lies.
 *
 * Adam: **"outside of running autonomy/manual commands, if trains are placed/pasted by the user on a 'can reverse'
 * station, ask the user in the popup where the tail of the train is (where it arrived from).  Otherwise, assume
 * 'arrived from' to be the opposite of their facing on normal stations, and if the station is a terminus, there is only
 * one forced option."**
 *
 * A train put down by hand has no arrival (autonomy writes it as a train arrives), so it is worked out, and only one
 * of three cases is a question:
 *
 * - **A terminus** has one way in: nothing to ask.
 * - **An ordinary station** is answered by the heading: the tail is on the side the train is not pointing at (see
 *   `arrivedFrom`).
 * - **A may-reverse station** is where that stops holding: a train there may have backed in or driven in, so guessing
 *   could report a protection that is not there.
 *
 * The sides offered are the ones the railway actually has.
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
     * The answer the next arrival-side question gets, for a test driving a real paste (MT-394); null shows the dialog.
     */
    private static volatile String answeredByATest;

    /**
     * Makes every arrival-side question answer this side without a dialog, until reset with null.  For tests only.
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
     * What the last test-answered question named the square as.  For tests only.
     *
     * @return the square's name as asked, or null when nothing has been asked
     */
    public static String lastAskedAboutForTests()
    {
        return askedAboutByATest;
    }

    /**
     * What the arrival-side question calls the square: its name, not the copy's, since a split square's Points carry a
     * heading and this question is ABOUT direction.
     */
    static String squareNameFor(Point at)
    {
        return org.traincontrol.automationui.StationIndex.withoutArrivalSuffix(at.getName());
    }

    /**
     * The side a hand-placed train should be recorded as having arrived from, asking if it must.  The rule is
     * `suggestedFor` and the question is `ask`.
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
        List<TilePorts.Side> arrivalSides)
    {
        // AND THE QUESTION, where the assumption does not hold, through `wouldAsk`, which the paste door also reads.
        if (wouldAsk(layout, at, mayReverse, arrivalSides))
        {
            return ask(parent, at, choicesFor(layout, at, arrivalSides));
        }

        return suggestedFor(layout, at, facing, mayReverse, arrivalSides);
    }

    /**
     * What the rule alone says, with nobody asked: `forPlacement` without the dialog, and a form's visible starting
     * value.  A terminus answers its one side (a square with no track, null); an ordinary station answers by the
     * heading out of the sides it has; a may-reverse station answers NULL (pass `mayReverse` false for the assumption).
     */
    public static String suggestedFor(Layout layout, Point at, String facing, boolean mayReverse,
        List<TilePorts.Side> arrivalSides)
    {
        List<String> sides = choicesFor(layout, at, arrivalSides);

        // NOTHING TO CHOOSE BETWEEN. A terminus has one way in, and a square with no track at all has
        // none - both answer themselves, and asking would be a dialog with one button.
        if (sides.isEmpty()) return null;

        if (sides.size() == 1) return sides.get(0);

        // The squares where the assumption does not hold are not answered here.
        if (mayReverse) return null;

        // THE ASSUMPTION, on a station where it holds: a train faces the way it will leave, so it came
        // out of THE SIDES THIS SQUARE ACTUALLY HAS (see `arrivedFrom`), not the compass.
        return arrivedFrom(sides, facing);
    }

    /**
     * The sides a placement here may be recorded as having come from: the BUILD'S SIDES when the caller has them, else
     * `Layout.entrySideOf`.  One list for the buttons, the combo and `wouldAsk`.  Never null, in a stable order.
     */
    public static List<String> choicesFor(Layout layout, Point at,
        List<TilePorts.Side> arrivalSides)
    {
        if (layout == null || at == null) return new ArrayList<>();

        return arrivalSides == null || arrivalSides.isEmpty()
            ? sidesOf(layout, at) : sidesOf(arrivalSides);
    }

    /**
     * Behind a train that drove in forwards, in the vocabulary the tail walk compares against (OB-182).  A null arrival
     * side claims least.
     */
    private static String arrivedFrom(List<String> sides, String facing)
    {
        // A facing nobody has set is no evidence; blocking track on it would be a guess.
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
     * Whether a placement here would put a question to the operator: the exact condition under which `forPlacement`
     * reaches `ask`, since its null can also mean "no" or "nothing to ask".
     */
    public static boolean wouldAsk(Layout layout, Point at, boolean mayReverse,
        List<TilePorts.Side> arrivalSides)
    {
        if (!mayReverse) return false;

        // THE SAME LIST the paste door reads (via `choicesFor`).
        return choicesFor(layout, at, arrivalSides).size() > 1;
    }

    /**
     * Puts the question through `FacingPrompt.askAmong`.  A dismissed dialog answers null, which blocks no track.
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

        return FacingPrompt.askAmong(parent, I18n.f("autolayout.ui.askArrivalSide", squareNameFor(at)),
            I18n.t("autolayout.ui.askArrivalSideTitle"), sides, ArrivalSidePrompt::labelFor, 0);
    }

    /** What one side is called where an operator reads it (dialog buttons, Station Settings' Train tab). */
    public static String labelFor(String side)
    {
        return I18n.t(keyFor(side));
    }

    /**
     * The message key naming one side, written out so the bundle check can see it.
     *
     * WHOLE KEYS, not a prefix plus a letter: the bundle check reads the source for them.
     * @param side "N", "S", "E" or "W"
     * @return the key, or the north one for anything unrecognized
     */
    private static String keyFor(String side)
    {
        switch (recognized(side))
        {
            case "S": return "autolayout.ui.sideS";
            case "E": return "autolayout.ui.sideE";
            case "W": return "autolayout.ui.sideW";
            default: return "autolayout.ui.sideN";
        }
    }

    /** The side as the operator is shown it: "N", "S", "E" or "W", and north for anything else a file recorded. */
    public static String recognized(String side)
    {
        return "S".equals(side) || "E".equals(side) || "W".equals(side) ? side : "N";
    }

    /**
     * The sides this point actually has track on, asked of the graph rather than of the compass.
     *
     * Both directions, because a tail is not directional - the train lies across that rail whether the
     * graph runs traffic into this point along it or out.
     *
     * **Through `Layout.entrySideOf`, the ONE definition**: where an edge carries a side it is the build's, else
     * geometry.
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
     * The sides track reaches this square by, as the BUILD has them (`AutonomyBuilder.arrivalSidesOf`); the geometric
     * form above names the neighboring POINT's direction, wrong on a curve.
     */
    public static List<String> sidesOf(List<TilePorts.Side> sides)
    {
        Set<String> out = new LinkedHashSet<>();

        if (sides != null)
        {
            for (TilePorts.Side side : sides)
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
