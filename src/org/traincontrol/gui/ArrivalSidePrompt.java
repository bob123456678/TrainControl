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
 *   from behind, so the tail is on the side it is not pointing at.  That is an assumption rather than a
 *   fact, and it is the right one - it is what a train that has not been turned round is doing.  The
 *   side it names is one of the square's OWN, never a compass point the railway does not have there:
 *   "behind" and "the opposite of the facing" are the same square on a straight and different squares
 *   on a curve, and it is the first that this is about.  See `arrivedFrom`.
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
     * The answer the next arrival-side question gets, for a test that drives a real paste (MT-394).
     *
     * A may-reverse square puts this question before the facing one, so a test of the second cannot
     * reach it without an answer to the first.  Null, the default, means the dialog is shown.
     */
    private static volatile String answeredByATest;

    /**
     * Makes every arrival-side question answer this side without showing a dialog, until reset with
     * null.  For tests only; nothing in the application calls it.
     *
     * @param side "N", "E", "S" or "W", or null to put the dialog back
     */
    public static void answerForTests(String side)
    {
        answeredByATest = side;
    }

    /**
     * The side a hand-placed train should be recorded as having arrived from, asking if it must.
     *
     * **Two halves, because there are now two surfaces** (Adam, 2026-09-11).  This door puts the
     * question in a dialog of its own, which is right for a menu item that places a train with no
     * dialog of its own.  `GraphLocAssign` already HAS a dialog, so it offers the same choice as a
     * combo in the form it is already showing - and a second dialog on top of a dialog would be the
     * worse of the two.
     *
     * So the rule is `suggestedFor` and the question is `ask`, and this is the one that does both.
     * The combo calls the first and is the answer to the second.  Written as a dispatch rather than
     * duplicated, because a rule with two spellings is this project's most repeated defect.
     *
     * @param layout the running layout, which knows where the neighbours are
     * @param at the point the train is being put down on
     * @param facing which way it points, or null when nobody has said
     * @param mayReverse whether the operator marked this square as one trains may turn at
     * @param parent what to centre the dialog on
     * @param arrivalSides the build's own sides, where the caller has them
     * @return the side, or null when it cannot be worked out and was not answered
     */
    public static String forPlacement(Layout layout, Point at, String facing, boolean mayReverse,
        Component parent,
        List<org.traincontrol.automationui.TilePorts.Side> arrivalSides)
    {
        // AND THE QUESTION, on the squares where the assumption does not hold - asked through
        // `wouldAsk` rather than through a second spelling of its condition.  The paste door reads
        // that same predicate to tell a dismissal from the other two nulls (IND9-B5), so the two have
        // to agree; now they agree by construction rather than by inspection.
        if (wouldAsk(layout, at, mayReverse, arrivalSides))
        {
            return ask(parent, at, choicesFor(layout, at, arrivalSides));
        }

        return suggestedFor(layout, at, facing, mayReverse, arrivalSides);
    }

    /**
     * What the rule alone says, with nobody asked.
     *
     * The whole of `forPlacement` except the dialog, and the half a form can use: a combo can show
     * this as its starting value and let the operator change it, where a popup has to either guess
     * silently or interrupt.
     *
     * - **A terminus** has one way in, so there is nothing to choose: `sides` holds one entry and it
     *   is the answer.  A square with no track at all has none, and answers null.
     * - **An ordinary station** is answered by the heading - a train faces the way it will leave and
     *   arrived from behind - out of the sides this square actually has.
     * - **A may-reverse station** answers NULL here, because turning round is what those squares are
     *   for and the heading stops being evidence there.  `forPlacement` sends that case to the
     *   operator.  A caller with a form to put the question in can pass `mayReverse` as false to get
     *   the assumption as a visible starting value instead - which is what `GraphLocAssign` does, and
     *   the difference between the two is that a value in a combo can be seen and corrected before it
     *   is written, while a guess inside a popup cannot.
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
        // compass - see `arrivedFrom` for why those are not the same question on a curve (REV9-B3).
        return arrivedFrom(sides, facing);
    }

    /**
     * The sides a placement here may be recorded as having come from.
     *
     * **THE BUILD'S SIDES when the caller has them**, which every caller in the application does.
     * The fallback is for a caller with no session - the tests that build a Layout by hand - and it is
     * not a second opinion: since REV9-B3 it reads `Layout.entrySideOf` too, which is the build's own
     * answer where an edge carries one and the compass geometry where it does not.  A hand-built
     * Layout carries none, so those tests get exactly what they always got.
     *
     * One place, because three things read this list - the dialog's buttons, the combo's entries, and
     * `wouldAsk`, which the paste door uses to tell a dismissal from a square with nothing to ask
     * about.  Assembled separately they would drift, and the drift would be silent.
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
     * Behind a train that drove in forwards, named in the vocabulary the tail walk compares against.
     *
     * **REV9-B3.  "The opposite of the facing" is a compass answer, and everything else now speaks the
     * build's sides.**  §4's rule - *"an ordinary station assumes the opposite of the facing"* - is
     * about where the train is relative to itself, and on a straight the compass says the same thing.
     * On a curve it does not.  A rail that leaves a square northwards and turns east reaches a
     * neighbour lying east; the build enters that square by N and by E, and a train facing E did not
     * come from W, because there is no W.
     *
     * OB-182 moved the offered sides, the written arrival side and the walk's own comparison onto the
     * build's entry sides for exactly that reason.  This branch was not swept with them, so it went on
     * manufacturing a side no edge carries: `Layout.edgesCoveredByStandingTrains` then matched no
     * candidate on its first hop and took its `segment == null -> break` exit - whose comment blames
     * "a stale value after an edit", while this door was writing one on every such placement.  The
     * track behind a standing train was left open and the picture said it was protected.
     *
     * **So the sides are given, and the facing chooses between them.**  Not the other way about.
     *
     * - **One way in that is not the way it is pointing** - the ordinary case, and the whole of the
     *   answer on a two-sided square, straight or curved.  On a straight it is the compass opposite;
     *   on a curve it is the side that exists.
     * - **Otherwise the compass assumption, but only where the build agrees with it.**  A square with
     *   three ways in leaves two candidates behind the train and the facing cannot separate them; the
     *   documented assumption - it drove straight through - picks the one opposite its nose, and it is
     *   taken only when the build really does enter by that side.
     * - **Otherwise nothing**, which is the important half.  A NULL arrival side narrows the walk - it
     *   falls through to the deterministic rule and blocks less - while a WRONG one sends it down
     *   track the train is not on, or, as here, down no track at all.  Claiming least is the answer
     *   this class already gives a dismissed dialog, for the same reason.
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
    public static boolean wouldAsk(Layout layout, Point at, boolean mayReverse,
        List<org.traincontrol.automationui.TilePorts.Side> arrivalSides)
    {
        if (!mayReverse) return false;

        // THE SAME LIST, not a second assembly of it: the paste door reads a null from `forPlacement`
        // as a dismissal only when this says a question was really put (IND9-B5), so the two have to
        // offer the same sides.  Through `choicesFor` since 2026-09-11, so they cannot come apart.
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
     * @param parent what to centre on
     * @param at the point
     * @param sides the sides that actually have track
     * @return the chosen side, or null when dismissed
     */
    private static String ask(Component parent, Point at, List<String> sides)
    {
        if (answeredByATest != null)
        {
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
     * What one side is called where an operator reads it.
     *
     * Both surfaces come through here - the dialog's buttons and the combo in `GraphLocAssign` - so a
     * side is worded the same wherever it is offered.  Two spellings of "from the west" would read as
     * two different settings.
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
     * WHOLE KEYS, not a prefix plus a letter.  The bundle check reads the source for the keys it must
     * find, and a concatenated one reads as "autolayout.ui.side" - which is in no bundle, so it
     * reports a missing message that is not missing while saying nothing about the four that could
     * really go absent.
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
     * The sides this point actually has track on, asked of the graph rather than of the compass.
     *
     * Both directions, because a tail is not directional - the train lies across that rail whether the
     * graph runs traffic into this point along it or out.
     *
     * **Through `Layout.entrySideOf`, which is the ONE definition** (REV9-B3).  This read
     * `sideTowards` - where the neighbouring POINT lies - and a Point is the far end of a reduced edge
     * that may run several tiles and turn corners on the way, so on a curve it named a side the metal
     * does not leave by.  `entrySideOf` is the build's own answer where the edge carries one and that
     * same geometry where it does not, so a hand-built `Layout` - which is every layout in the tests
     * that construct one - gets exactly the answer it got before, and a real railway gets the sides
     * the tail walk is comparing against.  The walk reads `entrySideOf` too; a list assembled any
     * other way is a second author computing what the builder already decided.
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
     * The sides track actually reaches this square by, as the BUILD has them.
     *
     * Adam, 2026-09-07: pasting onto BottomMainPost "asks if the train arrived from the south or from
     * the west, rather than the north or the south."
     *
     * The geometric form above answers with the compass direction of the neighbouring POINT, and a
     * Point is the far end of a reduced edge that may run several tiles and turn corners on the way.
     * A rail leaving north and curving east reaches a neighbour that lies east, so the geometry says
     * "E" while the metal leaves by "N". On a straight the two agree, which is why this survived
     * everywhere anybody looked.
     *
     * The builder already knows the answer and splits the square on it: `arrivalSides` goes to
     * `StationIndex` to `AutonomyBuilder.arrivalSidesOf`, which reads each reduced edge's ENTRY SIDE -
     * the side the track comes in by. That is the same door `facingChoices` was moved onto (DR-B6),
     * for the same reason: a second author computing what the builder already decided.
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
