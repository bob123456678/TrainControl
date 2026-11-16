package core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import static org.traincontrol.base.Accessory.accessoryDecoderType.MM2;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
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
     * A kickback spur: Middle lies on a switch's branch, trailing for a train from WestEnd, so the one way between them
     * runs through the switch to EastTurn, a square that is neither end and no station, turns there and comes back.
     * Autonomy never turns a train on the way, so both legs are drawn purple and the strip says why (FR-221).
     *
     * MUTATION: let `AutonomySession.testPathForAutonomy` turn wherever `mayTurnTiles` allows, and this fails.
     */
    @Test
    public void testALegThatTurnsOnTheWayIsDrawnAsOnlyAHandSendTakesIt() throws Exception
    {
        // The switch's toe faces east and its branch runs south to Middle, as in testAutonomyDiagramReversal.junction
        LayoutDiagram page = new LayoutDiagram("main", 10, 5, null, null);

        support.Diagrams.row(page, 1, 2, "FS", 5);
        page.addComponent(LayoutDiagramComponent.componentType.SWITCH_LEFT, 3, 2, 1, 0, 7, 7, MM2, null);
        support.Diagrams.row(page, 4, 2, "SFSF", 2, 6);
        page.addComponent(LayoutDiagramComponent.componentType.FEEDBACK, 3, 3, 1, 0, 9, 18, MM2, null);
        support.Diagrams.wireAccessories(Arrays.asList(page));

        session.open(Arrays.asList(page));
        session.initialize("Turning");

        // A switch runs out of its toe only until the operator opens it both ways
        session.setDirection(java.util.Collections.singleton(new TileKey("main", 3, 2)),
            org.traincontrol.automationui.TileGraph.Direction.BOTH);

        TileKey start = new TileKey("main", 1, 2);
        TileKey turn = new TileKey("main", 5, 2);
        TileKey spur = new TileKey("main", 3, 3);

        session.setPointName(turn, "EastTurn");
        session.setStation(start, true);
        session.setPointName(start, "WestEnd");
        session.setStation(spur, true);
        session.setPointName(spur, "Middle");

        for (TileKey square : Arrays.asList(turn, start, spur))
        {
            session.setPointFlag(square, AutonomyBuilder.CAN_REVERSE, true);
        }

        session.rebuild();

        assertNotNull(session.testPath(start, spur), "precondition: a hand send cannot turn at EastTurn for Middle");
        assertNull(session.testPathForAutonomy(start, spur), "precondition: autonomy reaches Middle without turning");

        AutonomyEditorPanel panel = armed();

        String[] legs = legs(panel, start, spur);

        String said = ((javax.swing.JLabel) Reflect.get(AutonomyEditorPanel.class, panel, "hint")).getText();

        assertNotEquals(legs[1], I18n.t("autosetup.ui.testLegBlocked"), "precondition: no route back from Middle");

        for (String[] leg : new String[][] {{"WestEnd", "Middle"}, {"Middle", "WestEnd"}})
        {
            assertTrue(said.contains(I18n.f("autosetup.ui.testTurnsOnTheWay", leg[0], leg[1])), "the strip does not say"
                + " the way from " + leg[0] + " to " + leg[1] + " turns on the way, which autonomy never does: " + said);
        }

        assertFalse(said.contains("(" + I18n.t("autosetup.ui.testColorYellow") + ")")
            || said.contains("(" + I18n.t("autosetup.ui.testColorAmber") + ")"), "a leg that turns at EastTurn is"
            + " called autonomy's color: " + said);

        @SuppressWarnings("unchecked")
        java.util.Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> drawn =
            (java.util.Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>>)
            Reflect.get(AutonomyEditorPanel.class, panel, "traces");

        boolean purple = false;

        for (org.traincontrol.automationui.TileAnnotation.Trace line : drawn.get(turn)) purple |= line.isManualOnly();

        assertTrue(purple, "the turn at EastTurn is not drawn purple: " + drawn.get(turn));
    }

    /**
     * A leg into a terminus, where every train turns, is autonomy's: the turn is on arriving, not on the way, so it is
     * drawn yellow (FR-221).
     *
     * MUTATION: drop `noTurn.remove(to)` from `AutonomySession.testPathForAutonomy`, and this fails.
     */
    @Test
    public void testALegIntoATerminusIsAutonomys() throws Exception
    {
        session.open(Arrays.asList(pageWithATwoEndedStation()));
        session.initialize("Terminus");

        TileKey east = new TileKey("main", 5, 1);

        session.setStation(west, true);
        session.setPointName(west, "WestEnd");
        session.setPointFlag(west, AutonomyBuilder.CAN_REVERSE, true);
        session.setStation(east, true);
        session.setPointName(east, "EastEnd");
        session.setPointFlag(east, AutonomyBuilder.MUST_REVERSE, true);
        session.rebuild();

        assertTrue(session.mandatoryTurnTiles().contains(east), "precondition: every train does not turn at EastEnd");

        AutonomyEditorPanel panel = armed();

        String[] legs = legs(panel, west, east);

        String said = ((javax.swing.JLabel) Reflect.get(AutonomyEditorPanel.class, panel, "hint")).getText();

        assertNotEquals(legs[0], I18n.t("autosetup.ui.testLegBlocked"), "precondition: no route to EastEnd");

        assertTrue(said.contains("(" + I18n.t("autosetup.ui.testColorYellow") + ")"), "the leg into the terminus"
            + " EastEnd is not drawn as autonomy's, in yellow: " + said);
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
