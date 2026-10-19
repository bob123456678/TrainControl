package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * The fixtures every topic class split from the former `testAutonomyDiagramSession` shares (FR-136 Phase 3):
 * a temporary layout folder and a fresh `AutonomySession` over it, built before each claim and deleted after,
 * and the pages and builders that more than one topic builds on.
 *
 * The whole chain, from a decision somebody made to the graph a train could run on.
 *
 * The unit tests below this cover each link separately; this covers the thing that goes wrong when they
 * are joined - an edit that changes the files but not the graph, or a graph that reflects an edit that
 * was never saved.  Both leave a user checking their work against the wrong answer.
 *
 * @author Adam
 */
abstract class AutonomySessionFixture
{
    File layout;
    AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-autonomy-session").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /** The pages the last `twoPagesWithATimetable` made. */
    LayoutDiagram[] twoPagesMade;

    /**
     * Two pages of three stations each, in a configuration of this name, and a timetable of one entry on each: its legs.
     */
    String[][] twoPagesWithATimetable(String configuration) throws Exception
    {
        LayoutDiagram main = new LayoutDiagram("main", 7, 3, null, null);
        LayoutDiagram second = new LayoutDiagram("second", 7, 3, null, null);

        int address = 90;

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
        session.initialize(configuration);

        twoPagesMade = new LayoutDiagram[] {main, second};

        String[] names = {"Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta"};

        for (int i = 0; i < 6; i++)
        {
            TileKey square = new TileKey(i < 3 ? "main" : "second", 1 + 2 * (i % 3), 1);

            session.setStation(square, true);
            session.setPointName(square, names[i]);
        }

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        String[] onMain = anEdgeBetween(built, "Beta", "Gamma");
        String[] onSecond = anEdgeBetween(built, "Epsilon", "Zeta");

        assertTrue(onMain != null && onSecond != null, "precondition: the two pages' edges are not built");

        session.setGlobal("timetable", aTimetableOf(onMain, onSecond));

        return new String[][] {onMain, onSecond};
    }

    /**
     * What a load of this session's setup builds, as the running layout then writes it: its configuration, and every
     * entry of the stored timetable - one the railway cannot build kept as it was written (Adam, 2026-09-30).
     */
    static org.json.JSONObject loadedFrom(AutonomySession of)
    {
        return new org.json.JSONObject(of.buildConfiguration());
    }

    /** What a load of the setup reads as its timetable: the stored one, after the build a load makes. */
    static org.json.JSONArray timetableAfterALoad(AutonomySession of)
    {
        of.buildConfiguration();

        return new org.json.JSONArray(String.valueOf(of.getGlobal("timetable")));
    }

    /** The first leg of each entry of the configuration's timetable, as "start -> end". */
    static List<String> legsOfTheTimetable(AutonomySession of)
    {
        List<String> out = new ArrayList<>();

        org.json.JSONArray table = new org.json.JSONArray(String.valueOf(of.getGlobal("timetable")));

        for (int i = 0; i < table.length(); i++)
        {
            org.json.JSONObject leg = table.getJSONObject(i).getJSONArray("path").getJSONObject(0);

            out.add(leg.getString("start") + " -> " + leg.getString("end"));
        }

        return out;
    }

    /** How many entries the configuration in use keeps in its timetable. */
    static int entriesKept(AutonomySession of)
    {
        return legsOfTheTimetable(of).size();
    }

    /** Stations at 1,1, 3,1 and 5,1 of one line, both ways, in a configuration of this name: its page. */
    LayoutDiagram threeStationsInARow(String configuration) throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 7, 3, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 64, 64, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 65, 65, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 66, 66, accessoryDecoderType.MM2, null);
        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize(configuration);

        for (int x : new int[] {1, 3, 5}) session.setStation(new TileKey("main", x, 1), true);

        return page;
    }

    /** One of the editor panel's own controls, by its field. */
    static java.awt.Component panelPart(org.traincontrol.gui.AutonomyEditorPanel panel, String field)
        throws Exception
    {
        java.lang.reflect.Field f = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField(field);

        f.setAccessible(true);

        return (java.awt.Component) f.get(panel);
    }

    /** The track either side of the middle station made one-way away from it, so nothing arrives there. */
    void oneWayAwayFromTheMiddle()
    {
        session.setDirection(new TileKey("main", 2, 1), new RouteId(0, 0), Direction.TOWARD_B);
        session.setDirection(new TileKey("main", 4, 1), new RouteId(0, 0), Direction.TOWARD_A);
        session.rebuild();
    }

    /** The first built edge from a Point of this base name to one of that. */
    static String[] anEdgeBetween(org.json.JSONObject built, String from, String to)
    {
        for (Object o : built.getJSONArray("edges"))
        {
            org.json.JSONObject e = (org.json.JSONObject) o;

            if (e.getString("start").startsWith(from) && e.getString("end").startsWith(to))
            {
                return new String[] {e.getString("start"), e.getString("end")};
            }
        }

        return null;
    }

    /** A captured timetable of one entry per leg, each one leg long, as the capture stores it. */
    static org.json.JSONArray aTimetableOf(String[]... legs)
    {
        org.json.JSONArray table = new org.json.JSONArray();

        for (String[] leg : legs)
        {
            table.put(new org.json.JSONObject().put("loc", "B1 train").put("executionTime", 1L).put("secondsToNext", 5L)
                .put("path", new org.json.JSONArray().put(new org.json.JSONObject().put("start", leg[0]).put("end", leg[1]))));
        }

        return table;
    }

    /**
     * A straight run of track with a sensor at each end and one in the middle.
     *
     * Three sensors rather than two, because the shape that needs catching is a station with track on
     * both sides of it whose onward track goes nowhere - which needs somewhere for the onward track to
     * end.
     */
    LayoutDiagram deadEndRun() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 10, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 5, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    LayoutDiagram runOfTrack() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    LayoutDiagram secondPage() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("second", 6, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 10, 21, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        page.setPageId("2");

        return page;
    }

    void delete(File file)
    {
        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children)
            {
                delete(child);
            }
        }

        file.delete();
    }

    /**
     * The squares a given check named, so a test can tell which one it fired about.
     */
    java.util.List<String> subjectsOf(String messageKey)
    {
        java.util.List<String> subjects = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey)) subjects.add(finding.getSubject());
        }

        return subjects;
    }

    boolean hasFinding(String messageKey)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey)) return true;
        }

        return false;
    }

    /**
     * A page whose file really exists, so that "this was not written" is a claim that can be checked.
     */
    File pageFile;

    /**
     * A page whose middle sensor has track on BOTH sides, so a train can arrive at it either way.
     *
     * pageOnDisk puts its two sensors at the ends of a line, where there is only one way in - which is
     * fine for naming and captions and useless for anything about arrival sides, since a square with
     * one way in has no choice to restrict.
     */
    LayoutDiagram pageWithATwoEndedStation() throws IOException
    {
        File pages = new File(layout, "config/gleisbilder");

        assertTrue(pages.mkdirs() || pages.isDirectory(), "could not create " + pages);

        pageFile = new File(pages, "main.cs2");

        Files.write(pageFile.toPath(),
            "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + pageFile.getAbsolutePath().replace('\\', '/');

        LayoutDiagram page = new LayoutDiagram("main", 8, 4, url, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    LayoutDiagram pageOnDisk() throws IOException
    {
        File pages = new File(layout, "config/gleisbilder");

        assertTrue(pages.mkdirs() || pages.isDirectory(), "could not create " + pages);

        pageFile = new File(pages, "main.cs2");

        Files.write(pageFile.toPath(),
            "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + pageFile.getAbsolutePath().replace('\\', '/');

        LayoutDiagram page = new LayoutDiagram("main", 8, 4, url, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 4, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /** How many findings of this key the setup gives about this square. */
    int findingsAbout(String messageKey, TileKey square)
    {
        int seen = 0;

        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey) && square.equals(finding.getTile())) seen++;
        }

        return seen;
    }

    /**
     * `platformBehindASwitch`, and track on past the platform to a second sensor - so the platform has an approach
     * from each side, one of which can be barred.
     */
    LayoutDiagram platformWithTwoApproaches() throws IOException
    {
        LayoutDiagram page = platformBehindASwitch();

        page.addComponent(componentType.STRAIGHT, 6, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 1, 0, 0, 8, 14, accessoryDecoderType.MM2, null);

        return page;
    }

    org.traincontrol.automationui.AutonomyChecks.Finding findingFor(String messageKey)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getMessageKey().equals(messageKey)) return finding;
        }

        return null;
    }

    /**
     * A platform with a switch in its approach, which is Adam's own example of a station whose track is
     * finer than one number: *"a station of length 4 may have two segments of tracks on either side of a
     * switch, of length 2."*
     *
     * The switch needs an address wired onto it or autonomy will not route over it, and its branch needs
     * somewhere to go or it is a dead end rather than a fork.
     */
    LayoutDiagram platformBehindASwitch() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SWITCH_LEFT, 3, 1, 3, 0, 7, 7, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        // The branch off the switch, lying north-south so that it has a port facing the switch at all.
        page.addComponent(componentType.FEEDBACK, 3, 0, 1, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.getComponent(3, 1).setAccessory(new org.traincontrol.marklin.MarklinAccessory(
            null, 7, org.traincontrol.base.Accessory.accessoryType.SWITCH, accessoryDecoderType.MM2,
            "Switch 7", false, 0));

        page.setPageId("1");

        return page;
    }

    /** The finding among these that is about this station, or null. */
    static org.traincontrol.automationui.AutonomyChecks.Finding findingFor(
        List<org.traincontrol.automationui.AutonomyChecks.Finding> findings, TileKey station)
    {
        for (org.traincontrol.automationui.AutonomyChecks.Finding finding : findings)
        {
            if (station.equals(finding.getAbout())) return finding;
        }

        return null;
    }

    /**
     * A line with a feedback in the MIDDLE, so that square has two ways in and can be split.
     */
    LayoutDiagram throughStationPage() throws IOException
    {
        File pages = new File(layout, "config/gleisbilder");

        assertTrue(pages.mkdirs() || pages.isDirectory(), "could not create " + pages);

        pageFile = new File(pages, "main.cs2");

        Files.write(pageFile.toPath(),
            "[gleisbildseite]\nversion\n .major=1\n".getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + pageFile.getAbsolutePath().replace('\\', '/');

        LayoutDiagram page = new LayoutDiagram("main", 8, 4, url, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    /**
     * Answers the station menu over a square as the operator does - Yes, or No - Trains Can Only Pass Through - through
     * the editor's own `setUsage`, on the event thread (GSE-C4).
     *
     * @param panel the editor
     * @param square the square
     * @param station true to make it a station, false for a square trains only pass through
     * @throws Exception from the event thread or the reflection
     */
    static void answerTheStationMenu(final org.traincontrol.gui.AutonomyEditorPanel panel, final TileKey square,
        final boolean station) throws Exception
    {
        final java.lang.reflect.Method usage = org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("setUsage",
            TileKey.class, boolean.class, boolean.class);

        usage.setAccessible(true);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                usage.invoke(panel, square, station, true);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new RuntimeException(failed);
            }
        });
    }

    void wire(LayoutDiagram page, int x, int y, int address)
    {
        page.getComponent(x, y).setAccessory(new org.traincontrol.marklin.MarklinAccessory(
            null, address, org.traincontrol.base.Accessory.accessoryType.SIGNAL,
            accessoryDecoderType.MM2, "Signal " + address, false, 0));
    }
}
