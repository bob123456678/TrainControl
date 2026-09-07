package org.traincontrol.gui;

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
 *   from behind, so the tail is on the opposite side.  That is an assumption rather than a fact, and it
 *   is the right one - it is what a train that has not been turned round is doing.
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
     * The side a hand-placed train should be recorded as having arrived from.
     *
     * @param layout the running layout, which knows where the neighbours are
     * @param at the point the train is being put down on
     * @param facing which way it points, or null when nobody has said
     * @param mayReverse whether the operator marked this square as one trains may turn at
     * @param parent what to centre the dialog on
     * @return the side, or null when it cannot be worked out and was not answered
     */
    public static String forPlacement(Layout layout, Point at, String facing, boolean mayReverse,
        Component parent)
    {
        if (layout == null || at == null) return null;

        List<String> sides = sidesOf(layout, at);

        // NOTHING TO CHOOSE BETWEEN. A terminus has one way in, and a square with no track at all has
        // none - both answer themselves, and asking would be a dialog with one button.
        if (sides.isEmpty()) return null;

        if (sides.size() == 1) return sides.get(0);

        // THE ASSUMPTION, on a station where it holds: a train faces the way it will leave, so it came
        // from behind.
        if (!mayReverse) return opposite(facing);

        // AND THE QUESTION, on the squares where the assumption does not hold.
        return ask(parent, at, sides);
    }

    /**
     * Whether a placement here would actually put a question to the operator (IND9-B5).
     *
     * `forPlacement` returns null in three different situations and only one of them is "the operator
     * said no": the square may have no compass-resolvable sides at all, or the dialog may have been
     * impossible to show. The paste door was reading all three as a dismissal, so a square whose copy
     * connects only through neighbours the grid cannot place was **silently refused every paste,
     * forever**, with no dialog and no log line - the operator could not put a train there and nothing
     * said why.
     *
     * So the door asks this instead of inferring it from a null. Same rule, one place: this is exactly
     * the condition under which `forPlacement` reaches `ask`, and if that changes they change together.
     * The alternative - a sentinel value threaded back through a String - would be a second spelling of
     * the same predicate, which is what this project keeps being bitten by.
     *
     * @param layout the running layout
     * @param at the point being placed on
     * @param mayReverse whether the operator marked this square as one trains may turn at
     * @return whether a dialog would be shown
     */
    public static boolean wouldAsk(Layout layout, Point at, boolean mayReverse)
    {
        if (!mayReverse || layout == null || at == null) return false;

        return sidesOf(layout, at).size() > 1;
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
     * @param parent what to centre on
     * @param at the point
     * @param sides the sides that actually have track
     * @return the chosen side, or null when dismissed
     */
    private static String ask(Component parent, Point at, List<String> sides)
    {
        final String[] answer = {null};

        try
        {
            Runnable prompt = () ->
            {
                Object[] options = new Object[sides.size()];

                for (int i = 0; i < sides.size(); i++)
                {
                    // WHOLE KEYS, not a prefix plus a letter.  The bundle check reads the source for
                    // the keys it must find, and a concatenated one reads as "autolayout.ui.side" -
                    // which is in no bundle, so it reports a missing message that is not missing while
                    // saying nothing about the four that could really go absent.
                    options[i] = I18n.t(keyFor(sides.get(i)));
                }

                int chose = JOptionPane.showOptionDialog(parent,
                    I18n.f("autolayout.ui.askArrivalSide", at.getName()),
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
     * The message key naming one side, written out so the bundle check can see it.
     *
     * @param side "N", "S", "E" or "W"
     * @return the key, or the north one for anything unrecognised
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
     * The compass sides this point actually has track on.
     *
     * Both directions, because a tail is not directional - the train lies across that rail whether the
     * graph runs traffic into this point along it or out.
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
            Point other = edge.getStart() == at ? edge.getEnd() : edge.getStart();

            String side = layout.sideTowards(at, other);

            if (side != null) sides.add(side);
        }

        return new ArrayList<>(sides);
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
