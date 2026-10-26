package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * A station's timetable, and the roads its trains stand on, follow it through a rename, a moved square, a ticked-out
 * page, a canceled edit and an undo.  `AutonomySessionFixture` builds the session and layout folder every claim starts
 * from.
 *
 * @author Adam
 */
public class testAStationsTimetableFollowsItThroughRenamesAndEdits extends AutonomySessionFixture
{

    /**
     * A station renamed keeps its timetable: every leg through it is carried to the new name.  Beta is somewhere trains
     * may turn round, so two of its copies run to Alpha, and only the heading says which a leg meant.
     *
     * MUTATION: carry nothing across a rebuild, or carry a leg to a copy other than the one its heading names, and this
     * fails.
     */
    @Test
    public void testARenamedStationKeepsItsTimetable() throws Exception
    {
        threeStationsInARow("B1 rename");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 3, 1), "Beta");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");
        session.setPointFlag(new TileKey("main", 3, 1), AutonomyBuilder.CAN_REVERSE, true);

        org.json.JSONObject before = new org.json.JSONObject(session.buildConfiguration());

        String[] leg = anEdgeBetween(before, "Beta", "Gamma");

        // THE TURNING COPY'S WAY BACK, beside the plain copy's to the same place
        String[] turned = null;

        for (Object o : before.getJSONArray("edges"))
        {
            org.json.JSONObject e = (org.json.JSONObject) o;

            if (e.getString("start").startsWith("Beta") && e.getString("start").contains("reverse")
                && e.getString("end").startsWith("Alpha")) turned = new String[] {e.getString("start"), e.getString("end")};
        }

        assertNotNull(leg, "precondition: no edge from Beta to Gamma");
        assertNotNull(turned, "precondition: no edge from Beta's turning copy to Alpha");

        session.setGlobal("timetable", aTimetableOf(leg, turned));

        // THE RENAME, as the editor's Name item makes it
        session.setPointName(new TileKey("main", 3, 1), "Bravo");

        assertTheTimetableIsLoadable(session, "renaming Beta to Bravo", 2);

        assertEquals(legOf(session, 0)[0], "Bravo" + leg[0].substring("Beta".length()), "the leg did not follow the rename"
            + " to the same copy of the square (RSA3-B1)");

        assertEquals(legOf(session, 1)[0], "Bravo" + turned[0].substring("Beta".length()), "the turning copy's leg was"
            + " carried to another copy running to the same place, or to none (RSA3-B1)");
    }

    /**
     * A square split by its ways out keeps a timetable leg written under its old, whole name, read as the first start
     * after the upgrade reads it: by a session that has built nothing yet.
     *
     * MUTATION: carry nothing a build of this session did not name, and this fails.
     */
    @Test
    public void testASquareSplitByItsWaysOutKeepsItsTimetable() throws Exception
    {
        LayoutDiagram page = threeStationsInARow("B1 split");

        oneWayAwayFromTheMiddle();

        // A leg as the old build wrote it: the square's own name, whole, and saved
        session.setGlobal("timetable", aTimetableOf(new String[] {"main 3,1", "main 5,1"}));

        // THE NEXT START
        AutonomySession next = new AutonomySession(layout);

        next.open(Arrays.asList(page));

        assertTheTimetableIsLoadable(next, "the square being split by its ways out", 1);

        assertEquals(legOf(next, 0)[0], "main 3,1 (eastbound)", "the leg was not carried to the copy it leaves by");
    }

    /**
     * A station renamed keeps every configuration's timetable, not only the one in use: another configuration's legs
     * kept the old names and its next load dropped them.
     *
     * MUTATION: carry the configuration in use alone, and this fails.
     */
    @Test
    public void testARenamedStationKeepsEveryConfigurationsTimetable() throws Exception
    {
        LayoutDiagram page = threeStationsInARow("A");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 3, 1), "Beta");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "Beta", "Gamma");

        assertNotNull(leg, "precondition: no edge from Beta to Gamma");

        session.setGlobal("timetable", aTimetableOf(leg));

        // B, A's copy, not in use
        session.getStore().createConfiguration("B", "A");
        session.save();

        // THE RENAME, with A in use
        session.setPointName(new TileKey("main", 3, 1), "Bravo");
        session.save();

        // THE NEXT START, with B made the one in use
        AutonomySession next = new AutonomySession(layout);

        next.open(Arrays.asList(page));
        next.getStore().setActiveConfiguration("B");
        next.rebuild();

        assertTheTimetableIsLoadable(next, "renaming Beta to Bravo while another configuration was in use", 1);

        assertEquals(legOf(next, 0)[0], "Bravo" + leg[0].substring("Beta".length()), "the other configuration's leg"
            + " did not follow the rename (RSA4-B1)");
    }

    /**
     * A station renamed carries the road a standing train came in on: the road is stored as Point names, and one naming
     * a Point no longer built is dropped whole.
     *
     * MUTATION: carry the timetable and not the roads, and this fails.
     */
    @Test
    public void testARenamedStationKeepsATrainsRoad() throws Exception
    {
        threeStationsInARow("C4");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 3, 1), "Beta");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "Beta", "Gamma");

        assertNotNull(leg, "precondition: no edge from Beta to Gamma");

        // A TRAIN AT GAMMA, come in from the west along Beta -> Gamma
        TileKey gamma = new TileKey("main", 5, 1);

        session.placeLocomotive(gamma, "C4 train");
        session.setArrivedFrom(gamma, "W");
        session.setArrivedAlong(gamma, new org.json.JSONArray().put(new org.json.JSONArray().put(leg[0]).put(leg[1]))
            .toString());

        session.setPointName(new TileKey("main", 3, 1), "Bravo");

        org.json.JSONArray road = new org.json.JSONArray(String.valueOf(session.getArrivedAlong(gamma)));

        assertEquals(road.getJSONArray(0).getString(0), "Bravo" + leg[0].substring("Beta".length()), "the road the train"
            + " came in on did not follow the rename, so the load drops it and its tail stops at the fork (RSA4-C4): "
            + road);

        assertEquals(road.getJSONArray(0).getString(1), leg[1], "the road's other end changed");
    }

    /**
     * A station renamed keeps a timetable leg only another configuration's build names: a copy that configuration alone
     * has is remembered by the square its name begins with when the session is opened.
     *
     * MUTATION: remember no name this session has not built, and this fails.
     */
    @Test
    public void testARenamedStationKeepsALegOnlyAnotherConfigurationBuilds() throws Exception
    {
        LayoutDiagram page = threeStationsInARow("A");

        TileKey beta = new TileKey("main", 3, 1);

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(beta, "Beta");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        // B, A's copy, where Beta alone turns trains round - and a leg from the turning copy
        session.getStore().createConfiguration("B", "A");
        session.getStore().setActiveConfiguration("B");
        session.setPointFlag(beta, AutonomyBuilder.CAN_REVERSE, true);

        String[] turned = null;

        for (Object o : new org.json.JSONObject(session.buildConfiguration()).getJSONArray("edges"))
        {
            org.json.JSONObject e = (org.json.JSONObject) o;

            if (e.getString("start").startsWith("Beta") && e.getString("start").contains("reverse")
                && e.getString("end").startsWith("Alpha")) turned = new String[] {e.getString("start"), e.getString("end")};
        }

        assertNotNull(turned, "precondition: no edge from Beta's turning copy to Alpha in B");

        session.setGlobal("timetable", aTimetableOf(turned));

        // A IN USE AGAIN, and saved so
        session.getStore().setActiveConfiguration("A");
        session.save();

        // THE NEXT START, with A in use: a session that has never built B - and the rename
        AutonomySession next = new AutonomySession(layout);

        next.open(Arrays.asList(page));

        assertEquals(next.getStore().getActiveConfiguration(), "A", "precondition: A is not the one in use");

        next.setPointName(beta, "Bravo");
        next.save();

        // AND B CHOSEN, by a session of its own
        AutonomySession after = new AutonomySession(layout);

        after.open(Arrays.asList(page));
        after.getStore().setActiveConfiguration("B");
        after.rebuild();

        assertTheTimetableIsLoadable(after, "renaming Beta to Bravo with A in use, B alone building the leg's copy", 1);

        assertEquals(legOf(after, 0)[0], "Bravo" + turned[0].substring("Beta".length()), "the leg from a copy only B"
            + " builds did not follow the rename (RSA4-B1)");
    }

    /**
     * A station renamed to the name another station has keeps the legs out of that other station: the name moves to the
     * renamed square, and a leg that named the other square is carried to what it is called now.
     *
     * MUTATION: leave alone a name the build still gives, whatever square it is on now, and this fails.
     */
    @Test
    public void testARenameToAnotherStationsNameCarriesItsLegs() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 3, null, null);

        for (int i = 0; i < 4; i++)
        {
            page.addComponent(componentType.FEEDBACK, 1 + 2 * i, 1, 0, 0, 111 + i, 111 + i, accessoryDecoderType.MM2, null);

            if (i < 3) page.addComponent(componentType.STRAIGHT, 2 + 2 * i, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        }

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("A1 moved");

        String[] names = {"Alpha", "Beta", "Gamma", "Delta"};

        for (int i = 0; i < 4; i++)
        {
            session.setStation(new TileKey("main", 1 + 2 * i, 1), true);
            session.setPointName(new TileKey("main", 1 + 2 * i, 1), names[i]);
        }

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "Gamma", "Delta");

        assertNotNull(leg, "precondition: no edge from Gamma to Delta");

        session.setGlobal("timetable", aTimetableOf(leg));

        // BETA RENAMED GAMMA: main 3,1 takes the name, main 5,1 becomes Gamma (2)
        session.setPointName(new TileKey("main", 3, 1), "Gamma");

        assertEquals(legOf(session, 0)[0], "Gamma (2)" + leg[0].substring("Gamma".length()), "the leg out of the station"
            + " that was Gamma was not carried to what it is called now (RSA5-A1)");

        assertTheTimetableIsLoadable(session, "renaming Beta to the name Gamma had", 1);
    }

    /**
     * A page renamed keeps the timetable legs and the roads through its squares with no name of their own, which are
     * named after their page.
     *
     * MUTATION: rename the page in the setup's keys alone, and this fails.
     */
    @Test
    public void testAPageRenamedKeepsTheLegsThroughItsUnnamedSquares() throws Exception
    {
        threeStationsInARow("B2 page");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "main 3,1", "Gamma");

        assertNotNull(leg, "precondition: no edge from the unnamed station to Gamma");

        session.setGlobal("timetable", aTimetableOf(leg));

        // AND A TRAIN AT ALPHA, come in along the rail from the unnamed station
        String[] step = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "main 3,1", "Alpha");

        assertNotNull(step, "precondition: no edge from the unnamed station to Alpha");

        TileKey alpha = new TileKey("main", 1, 1);

        session.placeLocomotive(alpha, "B2 train");
        session.setArrivedAlong(alpha, new org.json.JSONArray().put(new org.json.JSONArray().put(step[0]).put(step[1]))
            .toString());

        // THE PAGE RENAMED, as the track diagram's rename makes it: the setup, then the session left for a new one
        session.getStore().renamePage("main", "yard");
        session.markPagesStale();
        session.saveWithoutReconciling();

        LayoutDiagram yard = new LayoutDiagram("yard", 7, 3, null, null);

        yard.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 64, 64, accessoryDecoderType.MM2, null);
        yard.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        yard.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 65, 65, accessoryDecoderType.MM2, null);
        yard.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        yard.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 66, 66, accessoryDecoderType.MM2, null);
        yard.setPageId("1");

        AutonomySession next = new AutonomySession(layout);

        next.open(Arrays.asList(yard));

        assertEquals(legOf(next, 0)[0], "yard" + leg[0].substring("main".length()), "the leg through the unnamed"
            + " station did not follow the page's rename (RSA5-B2)");

        assertTheTimetableIsLoadable(next, "the page renamed", 1);

        org.json.JSONArray road = new org.json.JSONArray(String.valueOf(next.getArrivedAlong(new TileKey("yard", 1, 1))));

        assertEquals(road.getJSONArray(0).getString(0), "yard" + step[0].substring("main".length()), "the road through"
            + " the unnamed station did not follow the page's rename (RSA5-B2): " + road);
    }

    /**
     * A page renamed while an edit waits keeps the setup's renamed timetable: the fold behind the reset wrote the
     * railway's timetable, in the Point names the rename had just replaced, over it.
     *
     * MUTATION: fold the timetable while the pages are stale, and this fails.
     */
    @Test
    public void testARenameWhileAnEditWaitsKeepsTheRenamedTimetable() throws Exception
    {
        threeStationsInARow("C1 rename");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "main 3,1", "Gamma");

        assertNotNull(leg, "precondition: no edge from the unnamed station to Gamma");

        session.setGlobal("timetable", aTimetableOf(leg));

        // THE RAILWAY AS BUILT, and as the run left it: an entry more, captured
        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        org.json.JSONObject asBuilt = AutonomySession.globalsOf(built);

        built.put("timetable", aTimetableOf(leg, leg));

        // THE PAGE RENAMED, as the track diagram's rename makes it, and the reset's fold while an edit waits
        session.renamePage("main", "yard");

        session.captureWhatTheRailwayOwns(built.toString(), "C1 rename", asBuilt);

        org.json.JSONArray table = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

        assertEquals(table.length(), 1, "the fold after a rename wrote the railway's timetable over the setup's renamed one"
            + " (RSA22-C1): " + table);

        assertEquals(legOf(session, 0)[0], "yard" + leg[0].substring("main".length()), "the fold after a rename put the"
            + " Point names the rename replaced back into the timetable (RSA22-C1)");
    }

    /**
     * An edit session's Cancel keeps the timetable the railway changed alone across a station's rename, naming the
     * station as the restored setup names it (OB-343).  The carry traces a name the build lacks through the names its
     * builds have seen, and Cancel put that memory back before the legs were carried; the names the edit's builds gave
     * are kept in it now.
     *
     * MUTATION: forget the names the builds of the edit gave when the memory is put back, and this fails.
     */
    @Test
    public void testACancelKeepsTheTimetableTheRailwayChangedAloneAcrossAStationRename() throws Exception
    {
        threeStationsInARow("B rename");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "main 3,1", "Gamma");

        assertNotNull(leg, "precondition: no edge from the middle station to Gamma");

        // THE EDITOR OPENS on a setup whose stations are called Alpha and Gamma
        org.json.JSONObject opened = session.snapshotSetup();

        session.beginEditSession();

        try
        {
            // GAMMA RENAMED in the editor, and the railway rebuilt with the new name
            session.setPointName(new TileKey("main", 5, 1), "Omega");

            org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

            String[] renamed = anEdgeBetween(built, "main 3,1", "Omega");

            assertNotNull(renamed, "precondition: no edge from the middle station to Omega");

            // THE TIMETABLE CHANGES ON THE RAILWAY ALONE, in the new name, and a fold takes it
            org.json.JSONObject asBuilt = AutonomySession.globalsOf(built);

            built.put("timetable", aTimetableOf(renamed, renamed));

            session.captureTheRailwaysSettings(built.toString(), "B rename", asBuilt);

            assertEquals(new org.json.JSONArray(String.valueOf(session.getGlobal("timetable"))).length(), 2, "precondition: the"
                + " fold did not take the timetable");

            // CANCEL
            session.restoreSetup(opened);

            assertTheTimetableIsLoadable(session, "the editor's Cancel", 2);

            assertTrue(legOf(session, 0)[1].startsWith("Gamma"), "the legs of the timetable the railway changed alone name "
                + legOf(session, 0)[1] + " after Cancel, not the station as the restored setup calls it (OB-343)");
        }
        finally
        {
            session.endEditSession();
        }
    }

    /**
     * The same through Cancel's own fold, with no gesture in between (OB-343): the rebuild Cancel forces folds the
     * railway's timetable into the restored setup, and the load after it carries the names against the memory.
     *
     * MUTATION: forget the names the builds of the edit gave when the memory is put back, and this fails.
     */
    @Test
    public void testCancelsOwnFoldKeepsTheTimetableAcrossAStationRename() throws Exception
    {
        threeStationsInARow("B rename fold");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        org.json.JSONObject opened = session.snapshotSetup();

        session.beginEditSession();

        try
        {
            session.setPointName(new TileKey("main", 5, 1), "Omega");

            org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

            String[] renamed = anEdgeBetween(built, "main 3,1", "Omega");

            assertNotNull(renamed, "precondition: no edge from the middle station to Omega");

            // CANCEL FIRST, and then its own fold of the railway - built with the new name, its timetable changed
            org.json.JSONObject asBuilt = AutonomySession.globalsOf(built);

            built.put("timetable", aTimetableOf(renamed));

            session.restoreSetup(opened);

            session.captureTheRailwaysSettings(built.toString(), "B rename fold", asBuilt);

            // and the load after it, which carries the names against the memory
            session.rebuild();

            assertTheTimetableIsLoadable(session, "the editor's Cancel and its own fold", 1);

            assertTrue(legOf(session, 0)[1].startsWith("Gamma"), "the leg the railway's timetable names, " + legOf(session, 0)[1]
                + ", is not the station as the restored setup calls it (OB-343)");
        }
        finally
        {
            session.endEditSession();
        }
    }

    /**
     * A name an edit gives to two stations in turn does not carry a timetable leg to the wrong one (OB-343): each name
     * the carried legs hold is one square's when Cancel comes.
     *
     * MUTATION: carry the taken legs only at Cancel, against the memory as the whole edit left it, and this fails.
     */
    @Test
    public void testACancelMapsATakenLegThroughANameTheEditGaveTwoStations() throws Exception
    {
        threeStationsInARow("C1 reuse");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        org.json.JSONObject opened = session.snapshotSetup();

        session.beginEditSession();

        try
        {
            // GAMMA BECOMES OMEGA, and the railway captures a leg through it
            session.setPointName(new TileKey("main", 5, 1), "Omega");

            org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

            String[] renamed = anEdgeBetween(built, "main 3,1", "Omega");

            assertNotNull(renamed, "precondition: no edge from the middle station to Omega");

            org.json.JSONObject asBuilt = AutonomySession.globalsOf(built);

            built.put("timetable", aTimetableOf(renamed));

            session.captureTheRailwaysSettings(built.toString(), "C1 reuse", asBuilt);

            // THAT STATION GIVES THE NAME UP and the first one takes it
            session.setPointName(new TileKey("main", 5, 1), "Beta");
            session.setPointName(new TileKey("main", 1, 1), "Omega");

            // CANCEL
            session.restoreSetup(opened);

            assertTheTimetableIsLoadable(session, "the editor's Cancel after a name was reused", 1);

            assertTrue(legOf(session, 0)[1].startsWith("Gamma"), "the leg through the station that was Gamma names "
                + legOf(session, 0)[1] + " after Cancel, the station that took its name later (OB-343)");
        }
        finally
        {
            session.endEditSession();
        }
    }

    /**
     * The names an edit's builds gave are forgotten when the edit session ends (OB-343), so the next edit's opening
     * memory cannot beat a legitimate reassignment of the name.
     *
     * MUTATION: keep the names after the edit session, and this fails.
     */
    @Test
    public void testTheNamesAnEditGaveAreForgottenWhenItEnds() throws Exception
    {
        threeStationsInARow("C1 forget");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        org.json.JSONObject opened = session.snapshotSetup();

        session.beginEditSession();

        session.setPointName(new TileKey("main", 5, 1), "Omega");

        session.restoreSetup(opened);

        session.endEditSession();

        java.lang.reflect.Field seen = AutonomySession.class.getDeclaredField("squareOfNameSeen");

        seen.setAccessible(true);

        assertFalse(((java.util.Map<?, ?>) seen.get(session)).containsKey("Omega"), "the memory still knows a name only the"
            + " edit gave, after the edit session: it would be the next one's opening memory (OB-343)");
    }

    /**
     * Squares moved while an edit waits keep the setup's moved timetable: the fold behind the editor's Save wrote the
     * railway's timetable, in the Point names the move had just replaced, over it.
     *
     * MUTATION: fold the timetable after squares moved, and this fails.
     */
    @Test
    public void testASquareMovedWhileAnEditWaitsKeepsTheMovedTimetable() throws Exception
    {
        threeStationsInARow("C3 move");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "main 3,1", "Gamma");

        assertNotNull(leg, "precondition: no edge from the unnamed station to Gamma");

        session.setGlobal("timetable", aTimetableOf(leg));

        // THE RAILWAY AS BUILT, and as the run left it: an entry more, captured
        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        org.json.JSONObject asBuilt = AutonomySession.globalsOf(built);

        built.put("timetable", aTimetableOf(leg, leg));

        // MOVED, as the track editor moves a tile, and the fold behind its Save while an edit waits
        java.util.Map<TileKey, TileKey> moves = new java.util.LinkedHashMap<>();

        moves.put(new TileKey("main", 3, 1), new TileKey("main", 3, 2));

        assertTrue(session.moveTiles(moves), "precondition: the square did not move");

        session.captureWhatTheRailwayOwns(built.toString(), "C3 move", asBuilt);

        org.json.JSONArray table = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

        assertEquals(table.length(), 1, "the fold after squares moved wrote the railway's timetable over the setup's moved"
            + " one (RSA23-C3): " + table);

        assertFalse(legOf(session, 0)[0].startsWith("main 3,1"), "the fold after squares moved put the Point names the"
            + " move replaced back into the timetable (RSA23-C3): " + table);
    }

    /**
     * A page deleted is not folded back: the fold behind the reset worked its keys out from the session's pages, the
     * deleted one among them, with an edit waiting or not.
     *
     * MUTATION: forget the page without marking the pages stale, and this fails.
     */
    @Test
    public void testADeletedPageIsNotFoldedBack() throws Exception
    {
        threeStationsInARow("C2 delete");

        TileKey alpha = new TileKey("main", 1, 1);

        session.setPointName(alpha, "Alpha");
        session.placeLocomotive(alpha, "C2 train");
        session.setPointProperty(new TileKey("main", 3, 1), "maxTrainLength", 4);

        // THE RAILWAY, built before the delete
        String railway = session.buildConfiguration();

        assertTrue(railway.contains("C2 train"), "precondition: the railway has no train standing on the page");

        session.deletePage("main");

        // THE FOLDS BEHIND THE RESET: the whole one, and the one while an edit waits
        session.captureFromLayout(railway, "C2 delete");
        session.captureWhatTheRailwayOwns(railway, "C2 delete", null);

        org.json.JSONObject points = session.getStore().getConfiguration("C2 delete").optJSONObject("points");

        java.util.List<String> back = new ArrayList<>();

        if (points != null)
        {
            for (String key : points.keySet())
            {
                if (key.startsWith("main:") || key.startsWith("1:")) back.add(key + "=" + points.get(key));
            }
        }

        assertTrue(back.isEmpty(), "the fold after a page was deleted wrote the page back into the setup (RSA22-C2): "
            + back);
    }

    /**
     * A square with no name of its own moved on its page takes its stored names with it: it is named after where it is.
     *
     * MUTATION: move the setup's keys alone, and this fails.
     */
    @Test
    public void testAnUnnamedSquareMovedTakesItsLegsWithIt() throws Exception
    {
        threeStationsInARow("B2 move");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "main 3,1", "Gamma");

        assertNotNull(leg, "precondition: no edge from the unnamed station to Gamma");

        session.setGlobal("timetable", aTimetableOf(leg));

        // MOVED, as the track editor moves a tile
        java.util.Map<TileKey, TileKey> moves = new java.util.LinkedHashMap<>();

        moves.put(new TileKey("main", 3, 1), new TileKey("main", 3, 2));

        session.getStore().moveTiles(moves);

        String moved = new org.json.JSONArray(String.valueOf(session.getStore().getConfiguration("B2 move")
            .getJSONObject("globals").get("timetable"))).getJSONObject(0).getJSONArray("path").getJSONObject(0)
            .getString("start");

        assertEquals(moved, "main 3,2" + leg[0].substring("main 3,1".length()), "the leg through the unnamed station"
            + " still names the square it was moved off (RSA5-B2)");
    }

    /** A built configuration of this session with the timetable given, as the running layout writes it. */
    private static org.json.JSONObject runningWith(AutonomySession of, org.json.JSONObject... entries)
    {
        org.json.JSONObject running = new org.json.JSONObject(of.buildConfiguration());

        org.json.JSONArray table = new org.json.JSONArray();

        for (org.json.JSONObject entry : entries) table.put(entry);

        running.put("timetable", table);

        return running;
    }

    /**
     * A station renamed to the name another has keeps another configuration's legs out of that other station too.
     *
     * MUTATION: leave alone a name the build still gives in another configuration, and this fails.
     */
    @Test
    public void testARenameToAnotherStationsNameCarriesAnotherConfigurationsLegs() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 3, null, null);

        for (int i = 0; i < 4; i++)
        {
            page.addComponent(componentType.FEEDBACK, 1 + 2 * i, 1, 0, 0, 121 + i, 121 + i, accessoryDecoderType.MM2, null);

            if (i < 3) page.addComponent(componentType.STRAIGHT, 2 + 2 * i, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        }

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("A");

        String[] names = {"Alpha", "Beta", "Gamma", "Delta"};

        for (int i = 0; i < 4; i++)
        {
            session.setStation(new TileKey("main", 1 + 2 * i, 1), true);
            session.setPointName(new TileKey("main", 1 + 2 * i, 1), names[i]);
        }

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "Gamma", "Delta");

        assertNotNull(leg, "precondition: no edge from Gamma to Delta");

        session.setGlobal("timetable", aTimetableOf(leg));

        // B, A's copy, not in use
        session.getStore().createConfiguration("B", "A");
        session.save();

        // BETA RENAMED GAMMA, with A in use
        session.setPointName(new TileKey("main", 3, 1), "Gamma");
        session.save();

        AutonomySession next = new AutonomySession(layout);

        next.open(Arrays.asList(page));
        next.getStore().setActiveConfiguration("B");
        next.rebuild();

        assertEquals(legOf(next, 0)[0], "Gamma (2)" + leg[0].substring("Gamma".length()), "another configuration's leg"
            + " out of the station that was Gamma was not carried to what it is called now (RSA5-A1)");

        assertTheTimetableIsLoadable(next, "renaming Beta to the name Gamma had, another configuration in use", 1);
    }

    /**
     * A page ticked out and back in from the Autonomy menu keeps its timetable entries, once each: the menu's tick
     * saves and reloads with a fold of the railway still running.  The fold asks the running layout which pages it was
     * built without, not the setup.
     *
     * MUTATION: ask the setup which pages are out, and this fails.
     */
    @Test
    public void testAPageTickedFromTheMenuKeepsItsEntries() throws Exception
    {
        twoPagesWithATimetable("B1 menu");

        org.json.JSONArray stored = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

        // THE RAILWAY RUNNING, both pages in, both entries loaded
        org.json.JSONObject running = runningWith(session, stored.getJSONObject(0), stored.getJSONObject(1));

        // OUT, AS THE MENU DOES IT: the page, the save, the reload's fold of the railway still running
        session.setPageExcluded("second", true);
        session.save();
        session.captureFromLayout(running.toString());

        assertEquals(entriesKept(session), 2, "ticking a page"
            + " out from the menu wrote its entries twice (RSA6-B1): " + session.getGlobal("timetable"));

        // THE RELOAD'S BUILD, without the page: its entry dropped
        org.json.JSONObject without = loadedFrom(session);

        // AND BACK IN, AS THE MENU DOES IT: the page, the save, the fold of the railway built without it
        session.setPageExcluded("second", false);
        session.save();
        session.captureFromLayout(without.toString());

        assertEquals(entriesKept(session), 2, "ticking a page"
            + " back in from the menu erased its entries (RSA6-B1): " + session.getGlobal("timetable"));

        assertTheTimetableIsLoadable(session, "the page ticked out and back in from the menu", 2);
    }

    /**
     * A page ticked out after a run keeps where the run left the trains on it: the fold reads each running Point by the
     * square it is a copy of, not by its name.
     *
     * MUTATION: fold the running layout by its Points' names alone, and this fails.
     */
    @Test
    public void testAPageTickedOutKeepsWhereTheRunLeftItsTrains() throws Exception
    {
        twoPagesWithATimetable("A2 tick");

        TileKey epsilon = new TileKey("second", 3, 1);
        TileKey zeta = new TileKey("second", 5, 1);

        session.placeLocomotive(epsilon, "A2 tick train");
        session.rebuild();

        // THE RUN'S RESULT, on the running layout alone: the train on Zeta
        org.json.JSONObject running = new org.json.JSONObject(session.buildConfiguration());

        for (Object o : running.getJSONArray("points"))
        {
            org.json.JSONObject point = (org.json.JSONObject) o;

            if (point.getString("name").startsWith("Epsilon")) point.remove("loc");

            if (point.getString("name").startsWith("Zeta"))
            {
                point.put("loc", new org.json.JSONObject().put("name", "A2 tick train"));
            }
        }

        // OUT, AS THE MENU DOES IT: the page, then the fold of the railway still running
        session.setPageExcluded("second", true);
        session.captureFromLayout(running.toString());

        assertEquals(session.getLocomotiveNameAt(zeta), "A2 tick train", "a page ticked out after a run forgot where the"
            + " run left its train, which comes back where the setup last had it (RSA6-A2)");

        assertNull(session.getLocomotiveNameAt(epsilon), "the train is still recorded where the run began (RSA6-A2)");
    }

    /**
     * A square moved in the track editor is not folded over at its Save by the railway built before the move, which
     * would write the old names back over the ones the move carried.
     *
     * MUTATION: let the fold run after a move, and this fails.
     */
    @Test
    public void testAMoveIsNotFoldedOverByTheRailwayBuiltBeforeIt() throws Exception
    {
        threeStationsInARow("B2 save");

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        String[] leg = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "main 3,1", "Gamma");

        assertNotNull(leg, "precondition: no edge from the unnamed station to Gamma");

        session.setGlobal("timetable", aTimetableOf(leg));

        // THE RAILWAY RUNNING, built before the move
        org.json.JSONObject running = new org.json.JSONObject(session.buildConfiguration());

        java.util.Map<TileKey, TileKey> moves = new java.util.LinkedHashMap<>();

        moves.put(new TileKey("main", 3, 1), new TileKey("main", 3, 2));

        session.moveTiles(moves);

        // THE SAVE'S FOLD
        session.captureFromLayout(running.toString());

        assertEquals(legOf(session, 0)[0], "main 3,2" + leg[0].substring("main 3,1".length()), "the Save's fold wrote the"
            + " names from before the move back over the ones the move carried (RSA6-B2)");
    }

    /**
     * A station name two pages share keeps its timetable entries through a page ticked out and back in: the names are
     * settled over the squares of every page, so neither moves while one page is out.
     *
     * MUTATION: settle the names over the pages in play alone, and this fails.
     */
    @Test
    public void testAStationNameTwoPagesShareKeepsItsEntriesThroughATick() throws Exception
    {
        LayoutDiagram main = new LayoutDiagram("main", 7, 3, null, null);
        LayoutDiagram second = new LayoutDiagram("second", 7, 3, null, null);

        int address = 140;

        for (LayoutDiagram page : new LayoutDiagram[] {main, second})
        {
            page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, address, address, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, address + 1, address + 1, accessoryDecoderType.MM2,
                null);
            page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, address + 2, address + 2, accessoryDecoderType.MM2,
                null);

            address += 10;
        }

        main.setPageId("1");
        second.setPageId("2");

        session.open(Arrays.asList(main, second));
        session.initialize("C1 shared");

        String[] names = {"Alpha", "Beta", "Gamma", "Delta", "Beta", "Zeta"};

        for (int i = 0; i < 6; i++)
        {
            TileKey square = new TileKey(i < 3 ? "main" : "second", 1 + 2 * (i % 3), 1);

            session.setStation(square, true);
            session.setPointName(square, names[i]);
        }

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        String[] onMain = anEdgeBetween(built, "Beta", "Gamma");
        String[] onSecond = anEdgeBetween(built, "Beta (2)", "Zeta");

        assertTrue(onMain != null && onSecond != null, "precondition: the two Betas' legs are not built");

        session.setGlobal("timetable", aTimetableOf(onMain, onSecond));

        org.json.JSONArray stored = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

        // MAIN OUT AND BACK IN, as the menu does it
        org.json.JSONObject running = runningWith(session, stored.getJSONObject(0), stored.getJSONObject(1));

        session.setPageExcluded("main", true);
        session.save();
        session.captureFromLayout(running.toString());

        org.json.JSONObject without = loadedFrom(session);

        session.setPageExcluded("main", false);
        session.save();
        session.captureFromLayout(without.toString());

        assertEquals(timetableAfterALoad(session).length(), 2, "the two Betas'"
            + " entries were not both kept through a tick of main (RSA6-C1): " + session.getGlobal("timetable"));

        assertEquals(legOf(session, 0)[0], onMain[0], "main's Beta leg was carried to the other page's Beta through a tick"
            + " of main (RSA6-C1)");

        assertEquals(legOf(session, 1)[0], onSecond[0], "second's Beta leg was renamed through a tick of main (RSA6-C1)");

        assertTheTimetableIsLoadable(session, "main ticked out and back in, a station name shared with second", 2);
    }

    /**
     * A page ticked out keeps its timetable entries across a restart: the session after it has built none of the
     * page's Points, so it finds the squares its entries name by the names of the page's own squares.
     *
     * MUTATION: look for a page out's squares in the graph, and this fails.
     */
    @Test
    public void testAPageOutKeepsItsEntriesAcrossARestart() throws Exception
    {
        twoPagesWithATimetable("B1 restart");

        LayoutDiagram[] pages = twoPagesMade;

        org.json.JSONArray stored = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

        org.json.JSONObject running = runningWith(session, stored.getJSONObject(0), stored.getJSONObject(1));

        // OUT, AS THE MENU DOES IT, and saved
        session.setPageExcluded("second", true);
        session.save();
        session.captureFromLayout(running.toString());
        session.save();

        // THE NEXT START, the page still out: its load, and the exit's fold of what it loaded
        AutonomySession next = new AutonomySession(layout);

        next.open(Arrays.asList(pages));

        next.captureFromLayout(loadedFrom(next).toString());

        assertEquals(entriesKept(next), 2, "a page out of"
            + " autonomy lost its timetable entries at the first fold after a restart (RSA6-B1): "
            + next.getGlobal("timetable"));
    }

    /**
     * An entry set aside comes back after the entry it followed, though that entry's station was renamed while it was
     * aside.
     *
     * MUTATION: carry the entries and not what each aside one followed, and this fails.
     */
    @Test
    public void testAnEntryAsideComesBackAfterWhatItFollowedThroughARename() throws Exception
    {
        String[][] legs = twoPagesWithATimetable("aside anchor");

        String[] back = anEdgeBetween(new org.json.JSONObject(session.buildConfiguration()), "Beta", "Alpha");

        assertNotNull(back, "precondition: no edge from Beta to Alpha");

        session.setGlobal("timetable", aTimetableOf(legs[0], legs[1], back));

        // OUT, and its build
        session.setPageExcluded("second", true);
        session.rebuild();
        session.buildConfiguration();

        // BETA RENAMED BRAVO while the page is out
        session.setPointName(new TileKey("main", 3, 1), "Bravo");

        // AND BACK
        session.setPageExcluded("second", false);
        session.rebuild();

        org.json.JSONArray now = timetableAfterALoad(session);

        assertEquals(now.length(), 3, "precondition: the three entries did not all come back: " + now);

        assertEquals(legOf(session, 1)[0], legs[1][0], "the entry set aside did not come back after the entry it"
            + " followed, renamed meanwhile: " + now);
    }

    /**
     * A square deleted in the track editor, and the edit canceled, keeps its train and its settings: no fold follows a
     * square removed until the setup is opened again, as none follows a move.
     *
     * MUTATION: let the fold run after a square is removed, and this fails.
     */
    @Test
    public void testACancelledDeleteKeepsTheSquaresSetup() throws Exception
    {
        LayoutDiagram page = threeStationsInARow("A1 cancel");

        TileKey beta = new TileKey("main", 3, 1);

        session.setPointName(new TileKey("main", 1, 1), "Alpha");
        session.setPointName(beta, "Beta");
        session.setPointName(new TileKey("main", 5, 1), "Gamma");

        session.placeLocomotive(beta, "A1 train");
        session.setPointProperty(beta, "maxTrainLength", 321);
        session.rebuild();

        // THE RAILWAY RUNNING, and the editor opened: its fold, then its undo point
        org.json.JSONObject running = new org.json.JSONObject(session.buildConfiguration());

        session.captureFromLayout(running.toString());

        org.json.JSONObject asOpened = session.snapshotSetup();

        // BETA DELETED, as the editor deletes a square: the page's own square emptied, and the setup told
        page.addComponent(null, 3, 1);
        session.forgetTiles(java.util.Collections.singletonList(beta));

        assertNull(session.getLocomotiveNameAt(beta), "precondition: the delete forgot nothing");

        // CANCEL: the setup put back as the editor opened it, then the reset's fold of the railway running
        assertTrue(session.restoreSetup(asOpened), "precondition: Cancel could not put the setup back");

        assertEquals(session.getLocomotiveNameAt(beta), "A1 train", "precondition: Cancel did not put the train back");

        session.captureFromLayout(running.toString());

        assertEquals(session.getLocomotiveNameAt(beta), "A1 train", "the fold after a Cancel erased the train the Cancel"
            + " had put back on a square deleted and restored (RSA7-A1)");

        assertEquals(String.valueOf(session.getPointProperty(beta, "maxTrainLength")), "321", "the fold after a Cancel"
            + " erased the settings the Cancel had put back (RSA7-A1)");
    }

    /**
     * An entry deleted from the railway's timetable, its page then ticked out before any fold, stays deleted when the
     * page comes back.
     *
     * MUTATION: let a build for inspection keep what it settled, and this fails.
     */
    @Test
    public void testAnEntryDeletedOnTheAutoTabStaysDeletedThroughATick() throws Exception
    {
        twoPagesWithATimetable("C2 deleted");

        org.json.JSONArray stored = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

        // THE RAILWAY RUNNING WITHOUT THE PAGE'S ENTRY, deleted on the Auto tab
        org.json.JSONObject running = runningWith(session, stored.getJSONObject(0));

        // OUT, AS THE MENU DOES IT
        session.setPageExcluded("second", true);
        session.save();
        session.check();
        session.captureFromLayout(running.toString());

        // BACK IN, AS THE EDITOR'S BOX DOES IT: the page, and the rebuild
        session.setPageExcluded("second", false);
        session.rebuild();

        assertEquals(timetableAfterALoad(session).length(), 1, "an entry deleted on the Auto tab came back with its page"
            + " (RSA8-C2): " + legsOfTheTimetable(session));
    }

    /**
     * A configuration written by a build that kept a page's timetable entries in a list of their own has them back in
     * its timetable when read, each after the entry it followed, and the list gone (Adam: "Just keep the entries").
     *
     * MUTATION: read a configuration as it is, and this fails.
     */
    @Test
    public void testEntriesSetAsideByAnEarlierBuildAreBackInTheTimetable() throws Exception
    {
        String[][] legs = twoPagesWithATimetable("aside read");

        org.json.JSONArray stored = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

        assertEquals(stored.length(), 2, "precondition: the timetable is not two entries");

        // AS SUCH A BUILD WROTE IT: the second page's entry in a list of its own, after the first
        org.json.JSONObject configuration =
            session.getStore().getConfiguration(session.getStore().getActiveConfiguration());

        configuration.getJSONObject("globals").put("timetable", new org.json.JSONArray().put(stored.getJSONObject(0)));

        configuration.put("timetableAside", new org.json.JSONArray().put(new org.json.JSONObject()
            .put("entry", stored.getJSONObject(1)).put("after", stored.getJSONObject(0))));

        session.saveWithoutReconciling();

        // THE NEXT START
        AutonomySession next = new AutonomySession(layout);

        next.open(Arrays.asList(twoPagesMade));

        assertEquals(legsOfTheTimetable(next), Arrays.asList(legs[0][0] + " -> " + legs[0][1],
            legs[1][0] + " -> " + legs[1][1]), "an entry an earlier build set aside is not back in the timetable, after"
            + " the entry it followed");

        assertFalse(next.getStore().getConfiguration(next.getStore().getActiveConfiguration()).has("timetableAside"),
            "the list of entries set aside is still there");
    }

    /**
     * An undo puts each standing train's road back on its own train: the legs are held by where each is (its entry and
     * leg, or its square and step), not by position, since two roads whose squares share a bucket were written into
     * each other.
     *
     * MUTATION: put the legs back by position, and this fails.
     */
    @Test
    public void testAnUndoPutsEachRoadBackOnItsOwnTrain() throws Exception
    {
        // "second124" puts its 5,1 in the bucket of main's 5,1, after it
        LayoutDiagram main = new LayoutDiagram("main", 7, 3, null, null);
        LayoutDiagram second = new LayoutDiagram("second124", 7, 3, null, null);

        int address = 140;

        for (LayoutDiagram page : new LayoutDiagram[] {main, second})
        {
            page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, address, address, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, address + 1, address + 1, accessoryDecoderType.MM2,
                null);
            page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, address + 2, address + 2, accessoryDecoderType.MM2,
                null);

            address += 10;
        }

        main.setPageId("1");
        second.setPageId("2");

        session.open(Arrays.asList(main, second));
        session.initialize("C1 roads");

        for (LayoutDiagram page : new LayoutDiagram[] {main, second})
        {
            for (int x : new int[] {1, 3, 5}) session.setStation(new TileKey(page.getName(), x, 1), true);
        }

        // TWO STANDING TRAINS' ROADS, one on each page, as a fold writes them
        org.json.JSONObject configuration =
            session.getStore().getConfiguration(session.getStore().getActiveConfiguration());

        if (!configuration.has("points")) configuration.put("points", new org.json.JSONObject());

        org.json.JSONObject points = configuration.getJSONObject("points");

        points.put("main:5,1", new org.json.JSONObject().put("arrivedAlong",
            new org.json.JSONArray().put(new org.json.JSONArray().put("C1 a").put("C1 b"))));
        points.put("second124:5,1", new org.json.JSONObject().put("arrivedAlong",
            new org.json.JSONArray().put(new org.json.JSONArray().put("C1 y").put("C1 z"))));

        // THE UNDO POINT, and Ctrl+Z with nothing between
        java.util.Map<String, Object> undoPoint = session.snapshotPage("main");

        session.restorePage("main", undoPoint);

        org.json.JSONObject now = session.getStore().getConfiguration(session.getStore().getActiveConfiguration())
            .getJSONObject("points");

        assertEquals(now.getJSONObject("main:5,1").getJSONArray("arrivedAlong").toString(), "[[\"C1 a\",\"C1 b\"]]",
            "an undo wrote another train's road onto main 5,1 (RSA9-C1)");

        assertEquals(now.getJSONObject("second124:5,1").getJSONArray("arrivedAlong").toString(), "[[\"C1 y\",\"C1 z\"]]",
            "an undo wrote another train's road onto second124 5,1 (RSA9-C1)");
    }

    /** The leg of one entry of the configuration's stored timetable. */
    private static String[] legOf(AutonomySession of, int entry)
    {
        org.json.JSONObject leg = new org.json.JSONArray(String.valueOf(of.getGlobal("timetable")))
            .getJSONObject(entry).getJSONArray("path").getJSONObject(0);

        return new String[] {leg.getString("start"), leg.getString("end")};
    }

    /** Every leg of the stored timetable is an edge the next build has - what the load reads it against. */
    private static void assertTheTimetableIsLoadable(AutonomySession of, String after, int entries)
    {
        org.json.JSONObject built = new org.json.JSONObject(of.buildConfiguration());

        java.util.Set<String> edges = new java.util.HashSet<>();

        for (Object o : built.getJSONArray("edges"))
        {
            org.json.JSONObject e = (org.json.JSONObject) o;

            edges.add(e.getString("start") + " -> " + e.getString("end"));
        }

        org.json.JSONArray table = built.optJSONArray("timetable");

        assertNotNull(table, "precondition: the build carries no timetable");
        assertEquals(table.length(), entries, "precondition: the build's timetable is not the entries stored");

        for (int i = 0; i < table.length(); i++)
        {
            for (Object p : table.getJSONObject(i).getJSONArray("path"))
            {
                org.json.JSONObject leg = (org.json.JSONObject) p;

                String name = leg.getString("start") + " -> " + leg.getString("end");

                assertTrue(edges.contains(name), "after " + after + ", the timetable's leg " + name + " is no edge of the"
                    + " build, so the next load drops the entry and the capture after erases it (RSA3-B1).  Edges: "
                    + edges);
            }
        }
    }
}
