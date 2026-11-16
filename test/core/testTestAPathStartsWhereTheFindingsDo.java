package core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.util.I18n;
import support.Reflect;

/**
 * The editor's Test a Path starts a train where the setup check does: on the copies a train sent to the square stands
 * on.  Started facing every way, it drew a route from a station the check says reaches nothing, a route no train
 * standing there could take.
 *
 * Driven as a person does it: Test a Path pressed, the two squares clicked, the message strip read.
 *
 * @author Adam
 */
public class testTestAPathStartsWhereTheFindingsDo extends AutonomySessionFixture
{
    /**
     * Middle refuses arrivals from the east, so a train sent there came in from the west and faces east, into a dead
     * end.  Only the barred copy faces west, to WestEnd, and no train is sent onto it.  So nothing is drawn from Middle,
     * as the check says, while WestEnd, which can turn around and leave, still draws its way to Middle.
     *
     * MUTATION: pass null for the starting sides in `AutonomyEditorPanel.applyTest`, so the walk starts facing every
     * way, and this fails.
     */
    @Test
    public void testNoPathIsDrawnFromAStationWhoseStoppingCopyCannotLeave() throws Exception
    {
        westEndAndMiddleBarred("Barred", Side.E);
        session.rebuild();

        assertTrue(subjectsOf(AutonomyChecks.STATION_REACHES_NOTHING).contains("Middle"),
            "precondition: the check does not say Middle reaches nothing: " + session.check());

        String[] legs = testAPath(middle, west);

        assertEquals(legs[0], I18n.t("autosetup.ui.testLegBlocked"),
            "Test a Path drew a route from Middle to WestEnd that only the copy trains may not stop at can take, while"
                + " the setup check says Middle reaches nothing");

        assertNotEquals(legs[1], I18n.t("autosetup.ui.testLegBlocked"),
            "WestEnd can turn around and reach Middle from the west, and Test a Path drew no route back");
    }

    /**
     * Middle is reached from the east only, as the run west of it is one way westward: its one copy faces west, into a
     * dead end.  EastEnd, which can turn around, still draws its way to Middle.
     *
     * MUTATION: pass null for the starting sides in `AutonomyEditorPanel.applyTest`, and this fails.
     */
    @Test
    public void testNoPathIsDrawnFromAStationWhoseOnlyCopyFacesADeadEnd() throws Exception
    {
        LayoutDiagram page = pageWithATwoEndedStation();

        session.open(Arrays.asList(page));
        session.initialize("OneCopy");

        TileKey west = new TileKey("main", 1, 1);
        TileKey middle = new TileKey("main", 3, 1);
        TileKey east = new TileKey("main", 5, 1);

        session.setStation(middle, true);
        session.setPointName(middle, "Middle");
        session.setStation(east, true);
        session.setPointName(east, "EastEnd");
        session.setPointFlag(east, AutonomyBuilder.CAN_REVERSE, true);
        session.rebuild();

        assertTrue(session.setOneWayRun(middle, west) > 0, "precondition: the run west of Middle could not be made one-way");
        session.rebuild();

        assertEquals(session.arrivalSides(middle), Arrays.asList(Side.E), "precondition: Middle is not reached from the east only");

        assertTrue(subjectsOf(AutonomyChecks.STATION_REACHES_NOTHING).contains("Middle"),
            "precondition: the check does not say Middle reaches nothing: " + session.check());

        String[] legs = testAPath(middle, east);

        assertEquals(legs[0], I18n.t("autosetup.ui.testLegBlocked"),
            "Test a Path drew a route east from Middle, whose only copy faces west into a dead end, while the setup check"
                + " says Middle reaches nothing");

        assertNotEquals(legs[1], I18n.t("autosetup.ui.testLegBlocked"),
            "EastEnd can turn around and reach Middle, and Test a Path drew no route back");
    }

    /**
     * Middle takes trains from the east only, so from WestEnd the one way there runs past it to EastTurn, a square that
     * is no station, turns there and comes back.  Autonomy never turns a train on the way, so that leg is drawn purple
     * and the strip says why, while the leg back needs no turn and is autonomy's, in amber (FR-221).
     *
     * MUTATION: let `AutonomySession.testPathForAutonomy` turn wherever `mayTurnTiles` allows, and this fails.
     */
    @Test
    public void testALegThatTurnsOnTheWayIsDrawnAsOnlyAHandSendTakesIt() throws Exception
    {
        westEndAndMiddleBarred("Turning", Side.W);

        TileKey east = new TileKey("main", 5, 1);

        session.setPointName(east, "EastTurn");
        session.setPointFlag(east, AutonomyBuilder.CAN_REVERSE, true);
        session.rebuild();

        assertNotNull(session.testPath(west, middle), "precondition: a hand send cannot turn at EastTurn to reach Middle");

        AutonomyEditorPanel panel = armed();

        String[] legs = legs(panel, west, middle);

        assertNotEquals(legs[1], I18n.t("autosetup.ui.testLegBlocked"), "precondition: no route back from Middle");

        String said = ((javax.swing.JLabel) Reflect.get(AutonomyEditorPanel.class, panel, "hint")).getText();

        String turns = I18n.f("autosetup.ui.testTurnsOnTheWay", "WestEnd", "Middle");

        assertTrue(said.contains(turns), "the strip does not say the way to Middle turns on the way, which autonomy never"
            + " does: " + said);

        assertTrue(said.contains("(" + I18n.t("autosetup.ui.testColorPurple") + ")")
            && said.contains("(" + I18n.t("autosetup.ui.testColorAmber") + ")"), "the leg there is not called purple"
            + " and the leg back amber: " + said);

        @SuppressWarnings("unchecked")
        java.util.Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> drawn =
            (java.util.Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>>)
            Reflect.get(AutonomyEditorPanel.class, panel, "traces");

        boolean purple = false;

        for (org.traincontrol.automationui.TileAnnotation.Trace line : drawn.get(east)) purple |= line.isManualOnly();

        assertTrue(purple, "the turn at EastTurn is not drawn purple: " + drawn.get(east));
    }

    /**
     * On the frozen railway, Test a Path between every pair of stations draws a leg exactly where the reach the setup
     * check reads off the build (`BuiltReach.bySquare`) says a train there can be sent.
     *
     * MUTATION: pass null for the starting sides in `AutonomyEditorPanel.applyTest`, and this fails at TopMainR1Inter.
     */
    @Test
    public void testTestAPathAgreesWithTheFindingsOnTheFrozenRailway() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        org.traincontrol.marklin.MarklinControlStation model = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            model = support.TestStation.simulated();

            String path = "file:///"
                + sandbox.getFolder().getAbsolutePath().replace(java.io.File.separatorChar, '/') + "/";

            org.traincontrol.marklin.file.CS2File parser = new org.traincontrol.marklin.file.CS2File(path, model);
            parser.setLayoutDataLoc(path);

            session = new org.traincontrol.automationui.AutonomySession(sandbox.getFolder());
            session.open(support.LayoutSandbox.wired(model, parser));

            java.util.Map<TileKey, java.util.Set<TileKey>> reach = org.traincontrol.automationui.BuiltReach.of(
                new org.json.JSONObject(session.buildConfigurationForInspection()),
                session.builder(null).tilesByName()).bySquare();

            java.util.List<TileKey> stations = new java.util.ArrayList<>();

            for (org.traincontrol.automationui.GraphReducer.ReducedPoint point : session.getReducer().getPoints().values())
            {
                if (point.isStation()) stations.add(point.getTile());
            }

            assertTrue(stations.size() > 20, "precondition: the frozen railway has only " + stations.size() + " stations");

            AutonomyEditorPanel panel = armed();

            String blocked = I18n.t("autosetup.ui.testLegBlocked");

            java.util.List<String> disagree = new java.util.ArrayList<>();

            int drawn = 0;

            for (int i = 0; i < stations.size(); i++)
            {
                for (int j = i + 1; j < stations.size(); j++)
                {
                    TileKey a = stations.get(i);
                    TileKey b = stations.get(j);

                    String[] legs = legs(panel, a, b);

                    boolean[] tested = {!legs[0].equals(blocked), !legs[1].equals(blocked)};
                    boolean[] found = {reach.getOrDefault(a, java.util.Collections.<TileKey>emptySet()).contains(b),
                        reach.getOrDefault(b, java.util.Collections.<TileKey>emptySet()).contains(a)};

                    for (int leg = 0; leg < 2; leg++)
                    {
                        if (tested[leg]) drawn++;

                        if (tested[leg] != found[leg])
                        {
                            disagree.add(session.describeTile(leg == 0 ? a : b) + " to "
                                + session.describeTile(leg == 0 ? b : a) + (tested[leg] ? " drawn" : " not drawn"));
                        }
                    }
                }
            }

            assertTrue(drawn > 100, "precondition: Test a Path drew only " + drawn + " legs on the frozen railway");

            assertTrue(disagree.isEmpty(), "Test a Path and the setup check's reach disagree on the frozen railway: "
                + disagree);
        }
        finally
        {
            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    private final TileKey west = new TileKey("main", 1, 1);
    private final TileKey middle = new TileKey("main", 3, 1);

    /** The two-ended page: WestEnd, where trains may turn, and Middle, which refuses arrivals by `barred`. */
    private void westEndAndMiddleBarred(String configuration, Side barred) throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));
        session.initialize(configuration);

        session.setStation(west, true);
        session.setPointName(west, "WestEnd");
        session.setPointFlag(west, AutonomyBuilder.CAN_REVERSE, true);
        session.setStation(middle, true);
        session.setPointName(middle, "Middle");
        session.setBarredArrivals(middle, new LinkedHashSet<>(Arrays.asList(barred)));
    }

    /** Test a Path pressed and the two squares clicked: what the strip says of the leg there and of the leg back. */
    private String[] testAPath(TileKey from, TileKey to) throws Exception
    {
        return legs(armed(), from, to);
    }

    /** An editor panel over this session with Test a Path pressed. */
    private AutonomyEditorPanel armed() throws Exception
    {
        AutonomyEditorPanel panel = new AutonomyEditorPanel(session, null, () -> { });

        ((javax.swing.AbstractButton) Reflect.get(AutonomyEditorPanel.class, panel, "testButton")).doClick();

        return panel;
    }

    /** The word for a leg's color: purple where only a hand send gets there (FR-221), else the leg's own. */
    private String colorOf(TileKey from, TileKey end, String ordinary)
    {
        boolean byHand = session.testPathForAutonomy(from, end) == null && session.testPath(from, end) != null;

        return I18n.t(byHand ? "autosetup.ui.testColorPurple" : ordinary);
    }

    /** The two squares clicked: what the strip says of the leg there and of the leg back. */
    private String[] legs(AutonomyEditorPanel panel, TileKey from, TileKey to) throws Exception
    {
        panel.tileClicked(from, session.getGraph().getTiles().get(from), false);
        panel.tileClicked(to, session.getGraph().getTiles().get(to), false);

        String said = ((javax.swing.JLabel) Reflect.get(AutonomyEditorPanel.class, panel, "hint")).getText();

        // The legs read out between the message's fixed words, so the claim holds in any language
        String shape = I18n.f("autosetup.ui.testBothWays", session.describeTile(from), session.describeTile(to),
            "\u0001", "\u0002", "\u0003", colorOf(from, to, "autosetup.ui.testColorYellow"),
            colorOf(to, from, "autosetup.ui.testColorAmber"));

        String before = shape.substring(0, shape.indexOf('\u0001'));
        String afterThere = shape.substring(shape.indexOf('\u0001') + 1, shape.indexOf('\u0002'));
        String beforeBack = shape.substring(shape.indexOf('\u0002') + 1, shape.indexOf('\u0003'));
        String afterBack = shape.substring(shape.indexOf('\u0003') + 1);

        assertTrue(said != null && said.contains(before), "the strip does not report the test: " + said);

        String rest = said.substring(said.indexOf(before) + before.length());

        String there = rest.substring(0, rest.indexOf(afterThere));
        String back = rest.substring(rest.lastIndexOf(beforeBack) + beforeBack.length(), rest.lastIndexOf(afterBack));

        return new String[] {there, back};
    }
}
