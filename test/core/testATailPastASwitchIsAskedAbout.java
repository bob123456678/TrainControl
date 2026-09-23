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
 * **Where the tail has not passed the switch, it is still not asked**: the two rails share the squares up to it, and
 * every answer covers the same track.  That boundary is worked out from the rails' own places rather than written down,
 * so a re-measured switch moves it with the railway.
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

        int shared = unitsBeforeTheRailsPart(platform);

        assertTrue(shared >= 1 && shared < 4, "precondition: the two rails into " + PLATFORM + " part " + shared
            + " units back, so no length both passes the switch and reaches neither sensor");

        assertFalse(TailCrossedPrompt.wouldAsk(layout, platform, "E", shared), "a " + shared + "-unit train at " + PLATFORM
            + " lies on the squares both rails share, and is asked which one - every answer covers the same track");

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
     * Told the tail lies towards RampDown, the three-unit train covers the straight rail past switch 51 and none of the
     * turned one's own squares.
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

        Set<String> turnedOnly = new LinkedHashSet<>(turned.getPlaceIds());

        turnedOnly.removeAll(straight.getPlaceIds());

        train.setTrainLength(3);

        platform.setLocomotive(train);

        try
        {
            platform.setArrivedFrom("E");

            // BY THE NAME THE LIST OFFERS: RampDown has two copies leaving by this rail, and the list keeps one (OB-276).
            String towardsRampDown = null;

            for (TailCrossedPrompt.Choice choice : TailCrossedPrompt.choicesFor(layout, platform, "E", 3, null))
            {
                if (choice.getFarthest().getName().startsWith("RampDown")) towardsRampDown = choice.getFarthest().getName();
            }

            assertNotNull(towardsRampDown, "the way towards RampDown is not offered to a three-unit train");

            TailCrossedPrompt.answerForTests(towardsRampDown);

            List<Edge> road = TailCrossedPrompt.askAfterPlacement(layout, platform, "E", 3, train.getName(), null, null)
                .getRoad();

            assertNotNull(road, "the question was not put, or the answer towards RampDown gave no road");

            platform.setArrivedAlong(road);

            List<String> covered = new ArrayList<>();

            for (Map.Entry<String, Locomotive> at : layout.placesCoveredByStandingTrains().entrySet())
            {
                if (at.getValue() == train) covered.add(at.getKey());
            }

            Set<String> wrong = new LinkedHashSet<>(covered);

            wrong.retainAll(turnedOnly);

            assertTrue(wrong.isEmpty(), "told the tail lies towards RampDown, the train is claimed on " + wrong + ", squares"
                + " only the turned rail from BottomCrossover runs over.  Adam, MT-477: \"the tail always follows switch 51"
                + " turned, rather than facing straight toward rampdown\".  Covered: " + covered);

            Set<String> straightOnly = new LinkedHashSet<>(straight.getPlaceIds());

            straightOnly.removeAll(turned.getPlaceIds());
            straightOnly.retainAll(covered);

            assertFalse(straightOnly.isEmpty(), "told the tail lies towards RampDown, the train covers no square of the"
                + " straight rail past switch 51: " + covered);
        }
        finally
        {
            platform.setLocomotive(null);
            platform.setArrivedAlong(null);
        }
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
     * How many units of train the two rails in carry over the same squares, counted from the platform - the length a
     * tail can have without passing switch 51.
     */
    private static int unitsBeforeTheRailsPart(Point platform)
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

        for (int i = 1; i <= Math.min(a.size(), b.size()); i++)
        {
            if (!a.get(a.size() - i).equals(b.get(b.size() - i))) break;

            units += Math.max(0, spans.get(spans.size() - i));
        }

        return units;
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
