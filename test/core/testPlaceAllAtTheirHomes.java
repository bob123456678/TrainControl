package core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Place All at Their Homes (FR-115; Adam, 2026-10-09: "Place all at their homes.  Teleports locomotives to their home
 * stations, facing the correct way, and clears all other locomotives from other stations (without actually moving
 * anything)").
 *
 * On Adam's frozen railway (`test/operator_layout`), with three locomotives of this class's own: one homed on BottomMainA
 * facing west and standing on RampDown, one homed on BottomMainC with no facing recorded and standing nowhere, and one
 * with no home standing on BottomSecondary.  The setup the tool writes, and the running layout built from it, must have
 * the first two on their homes the right way round and the third nowhere - and nothing else placed.
 */
public class testPlaceAllAtTheirHomes
{
    private static final String HOMED = "FR-115 homed west";
    private static final String HOMED_TOO = "FR-115 homed, no facing";
    private static final String HOMELESS = "FR-115 no home";
    private static final String TWICE = "FR-115 homed twice";

    /**
     * Every train with a home is put on it, facing the way it was homed, and every other train is taken off - in the
     * setup, and so in the layout built from it.
     *
     * MUTATION: leave the trains without a home where they stand, or ignore the home's facing, or leave a train out of
     * the names the rebuild is given, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testEveryTrainIsPutOnItsHomeFacingAsHomedAndTheRestTakenOff() throws Exception
    {
        File frozen = new File("test/operator_layout");

        if (!frozen.isDirectory()) throw new AssertionError("test/operator_layout is not here");

        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;

        try
        {
            sandbox = support.LayoutSandbox.open(frozen);

            model = support.TestStationAddress.withNoStation(
                () -> MarklinControlStation.init(null, true, false, false, true));
            model.stop();

            model.newMM2Locomotive(HOMED, 2401);
            model.newMM2Locomotive(HOMED_TOO, 2402);
            model.newMM2Locomotive(HOMELESS, 2403);

            List<LayoutDiagram> pages = new ArrayList<>();

            for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

            AutonomySession session = new AutonomySession(sandbox.getFolder());

            session.open(pages);

            TileKey mainA = square(session, "BottomMainA");
            TileKey mainC = square(session, "BottomMainC");
            TileKey ramp = square(session, "RampDown");
            TileKey secondary = square(session, "BottomSecondary");

            // Both plain copies of BottomMainA placeable, as testATrainComesHomeFacingTheWayItWasHomed has it
            session.setBarredArrivals(mainA, Collections.<Side>emptySet());

            // NOTHING OF HIS STANDING OR HOMED, so the answer is about these three alone
            session.clearEveryPlacement();
            session.clearEveryHome();

            session.setHome(mainA, HOMED, Side.W);
            session.setHome(mainC, HOMED_TOO, null);

            session.placeLocomotive(ramp, HOMED);
            session.placeLocomotive(secondary, HOMELESS);

            // The way a train on BottomMainC faces, from its own copies - what the running layout's home copy would give
            Map<String, Side> facingsAtC = session.getStationIndex().facingsAt(mainC);

            assertTrue(facingsAtC != null && !facingsAtC.isEmpty(), "precondition: BottomMainC has no copy to face");

            Side fallback = facingsAtC.values().iterator().next();

            Set<String> touched = session.placeEveryTrainAtHome(Collections.singletonMap(mainC, fallback));

            // THE SETUP
            Map<TileKey, String> placed = session.placementsAutonomyWillWrite();

            assertEquals(placed.size(), 2, "after Place All at Their Homes the setup places " + placed + ", where it should"
                + " place the two trains with a home and nothing else");

            assertEquals(placed.get(mainA), HOMED, "the train homed on BottomMainA is not placed there: " + placed);
            assertEquals(placed.get(mainC), HOMED_TOO, "the train homed on BottomMainC is not placed there: " + placed);

            assertEquals(String.valueOf(session.getPointProperty(mainA, AutonomyBuilder.FACING)), "W", "the train homed"
                + " on BottomMainA facing west is placed facing " + session.getPointProperty(mainA, AutonomyBuilder.FACING));

            assertEquals(String.valueOf(session.getPointProperty(mainC, AutonomyBuilder.FACING)), fallback.name(), "a home"
                + " recorded with no facing did not take the facing it was given (" + fallback + ")");

            assertTrue(touched.containsAll(java.util.Arrays.asList(HOMED, HOMED_TOO, HOMELESS)), "the names the rebuild"
                + " is told about leave one out, and it would put that train back where it stood: " + touched);

            // AND THE LAYOUT BUILT FROM IT
            model.parseAuto(session.buildConfiguration());

            Layout layout = model.getAutoLayout();

            assertNotNull(layout, "the setup did not build: " + Layout.getLastError());

            Point a = layout.getLocomotiveLocation(model.getLocByName(HOMED));

            assertNotNull(a, "the train homed on BottomMainA is nowhere on the layout built from the setup");

            assertEquals(a.getName(), "BottomMainA (westbound)", "the train homed on BottomMainA facing west stands on "
                + a.getName());

            Point c = layout.getLocomotiveLocation(model.getLocByName(HOMED_TOO));

            assertTrue(c != null && c.getName().startsWith("BottomMainC"), "the train homed on BottomMainC stands on "
                + (c == null ? "nothing" : c.getName()));

            assertNull(layout.getLocomotiveLocation(model.getLocByName(HOMELESS)), "the train with no home is still on the"
                + " layout, where every train without one should have been taken off");
        }
        finally
        {
            if (model != null)
            {
                for (String name : new String[] {HOMED, HOMED_TOO, HOMELESS})
                {
                    try
                    {
                        model.deleteLoc(name);
                    }
                    catch (Exception alreadyGone)
                    {
                    }
                }

                model.stop();
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A locomotive homed on two squares - an older or hand-edited setup, since the editor's own door refuses it - is put
     * on ONE of them, the one the loader keeps, and counted once (FR-115).  The loader (`Layout.rebuildHomeStations`)
     * drops the second with a warning, and a locomotive placed on both squares invalidates the whole configuration.
     *
     * Every pair of four stations, because which of two the loader keeps is the order its points iterate in, and one pair
     * agreeing with a guess proves nothing.
     *
     * MUTATION: offer the locomotive on every square it is homed on, or offer the one the loader drops, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testALocomotiveHomedTwiceIsPlacedOnceWhereTheLoaderKeepsItsHome() throws Exception
    {
        File frozen = new File("test/operator_layout");

        if (!frozen.isDirectory()) throw new AssertionError("test/operator_layout is not here");

        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;

        try
        {
            sandbox = support.LayoutSandbox.open(frozen);

            model = support.TestStationAddress.withNoStation(
                () -> MarklinControlStation.init(null, true, false, false, true));
            model.stop();

            model.newMM2Locomotive(TWICE, 2404);

            List<LayoutDiagram> pages = new ArrayList<>();

            for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

            AutonomySession session = new AutonomySession(sandbox.getFolder());

            session.open(pages);

            String[] names = {"BottomMainA", "BottomMainB", "BottomMainC", "BottomInner"};

            List<TileKey> squares = new ArrayList<>();

            for (String name : names) squares.add(square(session, name));

            int pairs = 0;

            for (int first = 0; first < squares.size(); first++)
            {
                for (int second = first + 1; second < squares.size(); second++)
                {
                    TileKey one = squares.get(first);
                    TileKey other = squares.get(second);

                    session.clearEveryPlacement();
                    session.clearEveryHome();

                    // Written straight to the setup, as an older build left it: the editor's door would sweep the first
                    session.setPointProperty(one, "home", TWICE);
                    session.setPointProperty(other, "home", TWICE);

                    String where = names[first] + " and " + names[second];

                    assertEquals(session.homesToPlace().size(), 1, "a locomotive homed on " + where + " is offered "
                        + session.homesToPlace() + ", where Place All should count it once");

                    // WHICH ONE THE LOADER KEEPS, by loading the setup with both homes still on it
                    model.parseAuto(session.buildConfiguration());

                    Layout loaded = model.getAutoLayout();

                    assertNotNull(loaded, "the setup homing a locomotive on " + where + " did not build: "
                        + Layout.getLastError());

                    Point kept = loaded.getHomeStation(model.getLocByName(TWICE));

                    assertNotNull(kept, "the loader kept no home for a locomotive homed on " + where);

                    TileKey keptSquare = session.getStationIndex().squareOf(kept);

                    assertEquals(session.homesToPlace().keySet().iterator().next(), keptSquare, "Place All would home a"
                        + " locomotive homed on " + where + " on a different square than the loader keeps (" + kept.getName()
                        + ")");

                    session.placeEveryTrainAtHome(Collections.<TileKey, Side>emptyMap());

                    Map<TileKey, String> placed = session.placementsAutonomyWillWrite();

                    assertEquals(placed.size(), 1, "after Place All the setup places " + placed + " for a locomotive homed on "
                        + where + ", where it should be on one square");

                    assertEquals(placed.keySet().iterator().next(), keptSquare, "Place All stood the locomotive on a square"
                        + " other than the one the loader keeps its home on");

                    // AND THE CONFIGURATION STILL LOADS VALID
                    model.parseAuto(session.buildConfiguration());

                    assertNotNull(model.getAutoLayout(), "after Place All the setup with a locomotive homed on " + where
                        + " no longer loads: " + Layout.getLastError());

                    pairs++;
                }
            }

            assertEquals(pairs, 6);
        }
        finally
        {
            if (model != null)
            {
                try
                {
                    model.deleteLoc(TWICE);
                }
                catch (Exception alreadyGone)
                {
                }

                model.stop();
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The square a station of this name is on. */
    private static TileKey square(AutonomySession session, String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new AssertionError("his railway has no " + name);
    }
}
