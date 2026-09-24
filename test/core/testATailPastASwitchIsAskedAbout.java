package core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train at BottomSecondary whose tail has passed switch 51 without reaching a sensor is asked which way it lies, and
 * the answer is the rail it covers (MT-477).
 *
 * Adam, 2026-09-23, on MT-477: *"when set to lenth 3, the tail always follows switch 51 turned, rather than facing
 * straight toward rampdown.  Not a major issue, but technically that length should qualify for the prompt.  Also, since
 * there is only one choice, it should be auto selected without a prompt."*
 *
 * **On his measured railway.**  Two rails come into BottomSecondary from the east: RampDown's, 4 units, straight over
 * switch 51, and BottomCrossover's, 5 units, turned.  At 3 units the tail reached neither sensor, so the question - which
 * asked only where a sensor had been crossed - was not put, and the tail was laid on whichever rail the walk met first:
 * the turned one.  At 4 it reached RampDown only, and the list offered RampDown alone, which is the one choice he saw;
 * the tail could as well be 4 units up the turned rail.
 *
 * **Three units is exactly the switch.**  The two rails share every square up to and including switch 51 at 18,11, three
 * units in all, so a three-unit tail covers the same squares on either - and ends on the points, on one leg of them or
 * the other, which is what he saw drawn turned.  A tail that reaches into the square where the rails part has passed the
 * switch.  One that ends before it is still not asked: every answer covers the same track.  That boundary is worked out
 * from the rails' own places rather than written down, so a re-measured switch moves it with the railway.
 *
 * ON THE FROZEN RAILWAY (OB-111).
 *
 * @author Adam
 */
public class testATailPastASwitchIsAskedAbout
{
    private static final String PLATFORM = "BottomSecondary";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Layout layout;
    private static Locomotive train;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);
        model.stop();

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(pages);

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "precondition: the frozen railway built no autonomy layout");

        model.newMM2Locomotive("MT477 train", 2477);

        train = model.getLocByName("MT477 train");
    }

    @AfterMethod(alwaysRun = true)
    public void putTheDialogBack()
    {
        TailCrossedPrompt.answerForTests(null);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (model != null) model.deleteLoc("MT477 train");
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Past the switch and short of both sensors, he is asked - offered the way towards RampDown and towards BottomCrossover;
     * short of the switch he is not.
     */
    @Test
    public void testATailPastSwitch51ShortOfBothSensorsIsAsked()
    {
        Point platform = platform();

        int before = unitsBeforeTheSwitch(platform);

        assertTrue(before >= 1 && before < 3, "precondition: switch 51 is " + before + " units behind " + PLATFORM
            + ", so three units does not reach into it, or a shorter train than three already does");

        assertFalse(TailCrossedPrompt.wouldAsk(layout, platform, "E", before), "a " + before + "-unit train at " + PLATFORM
            + " ends before switch 51, on squares both rails share, and is asked which rail - every answer covers the same"
            + " track");

        List<TailCrossedPrompt.Choice> choices = TailCrossedPrompt.choicesFor(layout, platform, "E", 3, null);

        assertEquals(towards(choices), setOf("RampDown", "BottomCrossover"), "a three-unit train at " + PLATFORM + " has"
            + " passed switch 51 and reached neither RampDown nor BottomCrossover, and the list offers something else: "
            + labels(choices));

        for (TailCrossedPrompt.Choice choice : choices)
        {
            assertFalse(choice.isReached(), choice.getLabel() + " is offered as a sensor the three-unit tail crossed, and"
                + " it is at least four units back");
        }

        assertTrue(TailCrossedPrompt.wouldAsk(layout, platform, "E", 3), "a three-unit train at " + PLATFORM + " lies"
            + " past switch 51 on one rail or the other, and is not asked which.  Adam, MT-477: \"technically that length"
            + " should qualify for the prompt\"");
    }

    /**
     * At four units RampDown is reached and BottomCrossover is not: both are offered, so the list no longer has one entry.
     */
    @Test
    public void testAtFourUnitsBothRailsAreOffered()
    {
        List<TailCrossedPrompt.Choice> choices = TailCrossedPrompt.choicesFor(layout, platform(), "E", 4, null);

        assertEquals(towards(choices), setOf("RampDown", "BottomCrossover"), "a four-unit train at " + PLATFORM + " has"
            + " reached RampDown on the straight rail or lies four units up the turned one, and the list offers "
            + labels(choices) + " - one entry, where the train could as well be on the rail it leaves out");

        for (TailCrossedPrompt.Choice choice : choices)
        {
            boolean isRampDown = choice.getFarthest().getName().startsWith("RampDown");

            assertEquals(choice.isReached(), isRampDown, choice.getLabel() + ": RampDown is four units back and"
                + " BottomCrossover five, so a four-unit tail reaches the one and not the other");
        }

        // THE ONE SENSOR CROSSED IS WHERE THE LIST STARTS (FR-088): the way towards BottomCrossover is no sensor.
        int start = TailCrossedPrompt.preselectedIndex(choices, null);

        assertTrue(start >= 0 && choices.get(start).getFarthest().getName().startsWith("RampDown"),
            "the list does not start on RampDown, the one sensor a four-unit tail can have crossed: " + labels(choices));
    }

    /**
     * Told the tail lies towards RampDown, a three-unit train is laid on the straight rail - the leg of switch 51 its end
     * is on - and a four-unit one covers the straight rail's own squares past the switch and none of the turned one's.
     *
     * At three units the squares are the same either way, so what the answer changes there is the rail the tail is
     * recorded on, which is the leg the orange line is drawn along and the one Adam saw drawn turned.
     */
    @Test
    public void testTheAnswerPutsTheTailOnTheStraightRail()
    {
        Point platform = platform();

        Edge straight = null;
        Edge turned = null;

        for (Edge rail : railsIn(platform))
        {
            if (rail.getStart().getName().startsWith("RampDown")) straight = rail;
            if (rail.getStart().getName().startsWith("BottomCrossover")) turned = rail;
        }

        assertNotNull(straight, "precondition: no rail from RampDown comes into " + PLATFORM);
        assertNotNull(turned, "precondition: no rail from BottomCrossover comes into " + PLATFORM);

        // THREE UNITS: the rail.
        Map<Edge, Locomotive> rails = standAnswered(platform, 3);

        boolean onStraight = false;

        for (Edge rail : rails.keySet())
        {
            if (rails.get(rail) == train && rail.getEnd() == platform && rail.getStart().getName().startsWith("RampDown"))
            {
                onStraight = true;
            }
        }

        assertTrue(onStraight, "told the three-unit tail lies towards RampDown, the train is not laid on the straight rail"
            + " into " + PLATFORM + ".  Adam, MT-477: \"the tail always follows switch 51 turned, rather than facing"
            + " straight toward rampdown\"");

        assertFalse(rails.containsKey(turned) && rails.get(turned) == train, "told the three-unit tail lies towards"
            + " RampDown, the train is laid on the turned rail from BottomCrossover");

        platform.setLocomotive(null);
        platform.setArrivedAlong(null);

        // FOUR UNITS: the squares.
        standAnswered(platform, 4);

        List<String> covered = new ArrayList<>();

        for (Map.Entry<String, Locomotive> at : layout.placesCoveredByStandingTrains().entrySet())
        {
            if (at.getValue() == train) covered.add(at.getKey());
        }

        platform.setLocomotive(null);
        platform.setArrivedAlong(null);

        Set<String> turnedOnly = new LinkedHashSet<>(turned.getPlaceIds());

        turnedOnly.removeAll(straight.getPlaceIds());

        Set<String> wrong = new LinkedHashSet<>(covered);

        wrong.retainAll(turnedOnly);

        assertTrue(wrong.isEmpty(), "told the four-unit tail crossed RampDown, the train is claimed on " + wrong + ","
            + " squares only the turned rail from BottomCrossover runs over.  Covered: " + covered);

        Set<String> straightOnly = new LinkedHashSet<>(straight.getPlaceIds());

        straightOnly.removeAll(turned.getPlaceIds());
        straightOnly.retainAll(covered);

        assertFalse(straightOnly.isEmpty(), "told the four-unit tail crossed RampDown, the train covers no square of the"
            + " straight rail past switch 51: " + covered);
    }

    /**
     * With no answer - or Not known - a tail past switch 51 stops at the switch (Adam, 2026-09-24, on MT-477: *"stop at
     * the switch"*).
     *
     * Every other fork stops the tail where the roads part (his rule of 2026-09-07, "end locking at the switch").  The
     * fork right behind the platform did not: with no road to follow, the walk took the first rail by that side - the
     * turned one from BottomCrossover - and laid a four-unit tail up it, claiming track the train may not be on and none
     * of the rail it may be on.  So the squares both rails share are claimed, up to and including the switch, and none
     * that only one of them runs over.
     *
     * MUTATION: take the first rail by the side again, and this fails.
     */
    @Test
    public void testWithNoAnswerTheTailStopsAtTheSwitch()
    {
        Point platform = platform();

        Edge straight = null;
        Edge turned = null;

        for (Edge rail : railsIn(platform))
        {
            if (rail.getStart().getName().startsWith("RampDown")) straight = rail;
            if (rail.getStart().getName().startsWith("BottomCrossover")) turned = rail;
        }

        assertNotNull(straight, "precondition: no rail from RampDown comes into " + PLATFORM);
        assertNotNull(turned, "precondition: no rail from BottomCrossover comes into " + PLATFORM);

        train.setTrainLength(4);

        platform.setLocomotive(train);
        platform.setArrivedFrom("E");
        platform.setArrivedAlong(null);

        List<String> covered = new ArrayList<>();

        for (Map.Entry<String, Locomotive> at : layout.placesCoveredByStandingTrains().entrySet())
        {
            if (at.getValue() == train) covered.add(at.getKey());
        }

        platform.setLocomotive(null);
        platform.setArrivedAlong(null);

        Set<String> shared = new LinkedHashSet<>(straight.getPlaceIds());

        shared.retainAll(turned.getPlaceIds());

        Set<String> oneRailOnly = new LinkedHashSet<>(straight.getPlaceIds());

        oneRailOnly.addAll(turned.getPlaceIds());
        oneRailOnly.removeAll(shared);

        Set<String> wrong = new LinkedHashSet<>(covered);

        wrong.retainAll(oneRailOnly);

        assertTrue(covered.containsAll(shared), "a four-unit tail at " + PLATFORM + " with no answer does not cover the"
            + " squares both rails share up to switch 51, which it lies on whichever rail it is: " + shared + ".  Covered: "
            + covered);

        assertTrue(wrong.isEmpty(), "with no answer, a four-unit tail at " + PLATFORM + " is claimed past switch 51 on " + wrong
            + " - squares only one rail runs over, so it may not be there at all; it should stop at the switch (MT-477)."
            + "  Covered: " + covered);
    }

    /**
     * Stands the train at the platform at this length, answers the question with the way towards RampDown, and hands back
     * the rails it then covers.  The train is left standing, for the caller to read and then clear.
     */
    private static Map<Edge, Locomotive> standAnswered(Point platform, int length)
    {
        train.setTrainLength(length);

        platform.setLocomotive(train);
        platform.setArrivedFrom("E");
        platform.setArrivedAlong(null);

        // BY THE NAME THE LIST OFFERS: RampDown has two copies leaving by this rail, and the list keeps one (OB-276).
        String towardsRampDown = null;

        for (TailCrossedPrompt.Choice choice : TailCrossedPrompt.choicesFor(layout, platform, "E", length, null))
        {
            if (choice.getFarthest().getName().startsWith("RampDown")) towardsRampDown = choice.getFarthest().getName();
        }

        assertNotNull(towardsRampDown, "the way towards RampDown is not offered to a " + length + "-unit train");

        TailCrossedPrompt.answerForTests(towardsRampDown);

        List<Edge> road = TailCrossedPrompt.askAfterPlacement(layout, platform, "E", length, train.getName(), null, null)
            .getRoad();

        assertNotNull(road, "the question was not put to a " + length + "-unit train, or the answer gave no road");

        platform.setArrivedAlong(road);

        return new java.util.HashMap<>(layout.edgesCoveredByStandingTrains());
    }

    // ---------------------------------------------------------------------------------------------

    private static Point platform()
    {
        Point found = layout.getPoint(PLATFORM);

        assertNotNull(found, "precondition: the frozen railway has no " + PLATFORM);

        return found;
    }

    /** The rails that bring a train into the platform from the east. */
    private static List<Edge> railsIn(Point platform)
    {
        List<Edge> rails = new ArrayList<>();

        for (Edge rail : layout.getIncomingEdges(platform))
        {
            if (rail.getEnd() == platform && rail.getStart() != null && "E".equalsIgnoreCase(layout.entrySideOf(rail, platform)))
            {
                rails.add(rail);
            }
        }

        return rails;
    }

    /**
     * How many units of train the two rails in carry over the same squares before the last of them, which holds switch
     * 51 - the longest tail that does not reach the points.
     */
    private static int unitsBeforeTheSwitch(Point platform)
    {
        Edge straight = null;
        Edge turned = null;

        for (Edge rail : railsIn(platform))
        {
            if (rail.getStart().getName().startsWith("RampDown")) straight = rail;
            if (rail.getStart().getName().startsWith("BottomCrossover")) turned = rail;
        }

        assertNotNull(straight, "precondition: no rail from RampDown");
        assertNotNull(turned, "precondition: no rail from BottomCrossover");

        List<String> a = straight.getPlaceIds();
        List<String> b = turned.getPlaceIds();
        List<Integer> spans = straight.getPlaceLengths();

        int units = 0;
        int last = 0;

        for (int i = 1; i <= Math.min(a.size(), b.size()); i++)
        {
            if (!a.get(a.size() - i).equals(b.get(b.size() - i))) break;

            last = Math.max(0, spans.get(spans.size() - i));

            units += last;
        }

        // The switch square is the last they share; what is before it is everything else.
        return units - last;
    }

    /** Which of the two ways each choice names, by the sensor it leads to. */
    private static Set<String> towards(List<TailCrossedPrompt.Choice> choices)
    {
        Set<String> names = new LinkedHashSet<>();

        for (TailCrossedPrompt.Choice choice : choices)
        {
            String name = choice.getFarthest().getName();

            names.add(name.startsWith("RampDown") ? "RampDown" : name.startsWith("BottomCrossover") ? "BottomCrossover" : name);
        }

        return names;
    }

    private static Set<String> setOf(String... names)
    {
        return new LinkedHashSet<>(java.util.Arrays.asList(names));
    }

    private static List<String> labels(List<TailCrossedPrompt.Choice> choices)
    {
        List<String> out = new ArrayList<>();

        for (TailCrossedPrompt.Choice choice : choices) out.add(choice.getLabel());

        return out;
    }
}
