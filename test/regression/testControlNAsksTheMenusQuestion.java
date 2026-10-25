package regression;

import java.util.Arrays;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.util.I18n;

/**
 * Control+N offers a station name exactly where the right-click menu does (FR-086).
 *
 * The key asks what the menu asks, put side by side through a public predicate because the key itself opens a modal
 * dialog (the shape `testControlEAsksTheMenusQuestion` holds for Control+E).  Read-only: its own diagram in a
 * temporary folder.
 *
 * @author Adam
 */
public class testControlNAsksTheMenusQuestion
{
    /**
     * On plain track the menu offers a name and so does the key; on the same track on an ignored page, neither does.
     */
    @Test
    public void testTheKeyOffersNoNameWhereTheMenuOffersNone() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-n").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main")));

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            TileKey track = new TileKey("main", 3, 1);

            // The control: a square the menu offers a name on, and the key agrees.
            javax.swing.JPopupMenu menu = panel.buildTileMenu(track, null);

            assertNotNull(menu, "precondition: no menu on the plain track at " + track);

            assertTrue(offersAName(menu), "precondition: the menu on plain track offers no station name - "
                + itemNames(menu) + " - so there is nothing for the key to agree with");

            assertTrue(keyOffersAName(panel, track),
                "the key offers no station name on plain track where the menu does: " + itemNames(menu));

            // The squares the menu sends to its text menu (a label, an empty square) carry no track and count as
            // ignored; a predicate asking `isIgnored` before the text branch would refuse them.
            for (TileKey square : new TileKey[] { new TileKey("main", 3, 3), new TileKey("main", 4, 2) })
            {
                javax.swing.JPopupMenu there = panel.buildTileMenu(square, null);

                assertTrue(there != null && offersAName(there),
                    "precondition: the menu offers no station name on the text or empty square " + square + ": "
                    + itemNames(there));

                assertTrue(keyOffersAName(panel, square),
                    "Control+N offers no station name on " + square + ", a text or empty square, where the menu offers "
                    + itemNames(there) + " (MFV-C3)");
            }

            // An ignored square: the same track, its page left out of autonomy.
            session.setPageExcluded("main", true);

            javax.swing.JPopupMenu ignored = panel.buildTileMenu(track, null);

            assertFalse(ignored != null && offersAName(ignored),
                "precondition: the menu still offers a station name on an ignored square: " + itemNames(ignored));

            assertFalse(keyOffersAName(panel, track),
                "Control+N offers the station chooser on " + track + ", a square on a page left out of autonomy, where"
                + " the right-click menu offers " + (ignored == null ? "nothing" : itemNames(ignored).toString())
                + " (MFR-B3)");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * Every key in the editor puts down a gesture in progress, as the right-click menu does.
     *
     * Asked on a page left out of autonomy, where no key opens a dialog; each key's own predicate is checked first, so
     * a key that would open one fails a precondition rather than hanging the run.
     */
    @Test
    public void testEveryEditorKeyPutsDownAGestureAsTheMenuDoes() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-keys").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main")));

            final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            final TileKey track = new TileKey("main", 3, 1);

            session.setPageExcluded("main", true);

            assertFalse(asked(panel, "canBeNamed", track), "precondition: Control+S would open a dialog on " + track);
            assertFalse(panel.offersALength(track), "precondition: Control+E would open a dialog on " + track);
            assertFalse(keyOffersAName(panel, track), "precondition: Control+N would open a dialog on " + track);
            assertFalse(panel.offersAMaximumTrainLength(track), "precondition: Control+B would open a dialog on " + track);
            assertFalse(keyOffers(panel, "offersAHome", track), "precondition: Control+H would open a dialog on " + track);

            // Every key that is a door onto the menu: S, E, N, B and H.
            String[] keys = { "Control+S", "Control+E", "Control+N", "Control+B", "Control+H" };
            Runnable[] presses = { () -> panel.promptNameFor(track), () -> panel.promptLengthFor(track),
                () -> panel.showStationNameFor(track), () -> panel.promptMaxTrainLengthFor(track),
                () -> panel.promptHomeFor(track) };

            // Armed through the panel's own field, not its button: on an ignored page the refresh disables Test a Path,
            // so a second doClick would do nothing.
            final java.lang.reflect.Field tool = AutonomyEditorPanel.class.getDeclaredField("tool");

            tool.setAccessible(true);

            @SuppressWarnings({ "unchecked", "rawtypes" })
            final Object testTool = Enum.valueOf((Class) tool.getType(), "TEST");

            for (int i = 0; i < keys.length; i++)
            {
                if (!asked(panel, "anythingIsArmed", null)) tool.set(panel, testTool);

                assertTrue(asked(panel, "anythingIsArmed", null), "precondition: Test a Path did not arm before " + keys[i]);

                presses[i].run();

                assertFalse(asked(panel, "anythingIsArmed", null),
                    keys[i] + " left Test a Path armed, where the right-click menu puts it down before offering anything"
                    + " - one key disarms a gesture and another leaves it waiting for a click (MFV-C4)");
            }
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * A station square showing another station's only name asks before it shows its own, and No leaves that name where
     * it is; Yes still shows its own.
     *
     * Otherwise the other station is named nowhere on the diagram, which the setup's checks call an error
     * (`AutonomyChecks.checkStationLabels`).  Driven through Control+N and answered through
     * `answerTakeAnotherNameForTests`, so no dialog is put on screen.
     *
     * MUTATION: drop the question from `promptStationLabel`'s station branch, and the No half fails.
     */
    @Test
    public void testAStationSquareAsksBeforeTakingAnotherStationsOnlyName() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-n-other").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main")));

            TileKey here = new TileKey("main", 1, 1);
            TileKey other = new TileKey("main", 6, 1);

            session.setStation(here, true);
            session.setStation(other, true);

            // The other station's name on this station's square, and nowhere else - as a drag leaves it
            session.setCaption(here, other);

            assertEquals(session.captionsFor(other), java.util.Collections.singleton(here),
                "precondition: the other station is not named on this square alone: " + session.captionsFor(other));

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            assertTrue(panel.offersAStationName(here), "precondition: the menu offers no live Show a Station Name Here on a"
                + " station square showing another station's name, which SVV-C6 and SVX-B2 made live");

            AutonomyEditorPanel.answerTakeAnotherNameForTests(false);

            panel.showStationNameFor(here);

            assertEquals(session.getCaptionTarget(here), other, "Show a Station Name Here took the only name another"
                + " station had off the diagram without asking, or after being answered No (GSE-C5)");

            // And it says the station is left off the diagram, which Yes does: this is its only name anywhere (OB-341)
            assertEquals(AutonomyEditorPanel.takeAnotherNameQuestionForTests(),
                I18n.f("autosetup.ui.confirmTakeAnotherStationsOnlyName", session.describeTile(other), session.describeTile(here)),
                "the question does not say that Yes leaves the station shown nowhere");

            // And Yes still shows its own: the item is live, and asking is not refusing
            AutonomyEditorPanel.answerTakeAnotherNameForTests(true);

            panel.showStationNameFor(here);

            assertEquals(session.getCaptionTarget(here), here, "answered Yes, the station square still does not show its"
                + " own name - the question became a refusal, which SVV-C6 and SVX-B2 ruled out");
        }
        finally
        {
            AutonomyEditorPanel.answerTakeAnotherNameForTests(null);

            deleteRecursively(folder);
        }
    }

    /**
     * The question about taking another station's only name is about this page (OB-341): it is asked when no other
     * square of the page names the station, whatever other pages do.
     *
     * MUTATION: look at the station's captions on every page, as before, and the first half fails.
     */
    @Test
    public void testAStationSquareAsksAboutAnotherStationsOnlyNameOnThisPageOnly() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-n-page").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main"), aPageWithARun("second")));

            TileKey here = new TileKey("main", 1, 1);
            TileKey other = new TileKey("main", 6, 1);

            session.setStation(here, true);
            session.setStation(other, true);

            // The other station's name on this station's square, and on the other page - its only one on this page
            session.getStore().setCaption(here, other);
            session.getStore().setCaption(new TileKey("second", 3, 2), other);

            assertEquals(session.captionsFor(other).size(), 2, "precondition: " + session.captionsFor(other));

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            AutonomyEditorPanel.answerTakeAnotherNameForTests(false);

            panel.showStationNameFor(here);

            assertEquals(session.getCaptionTarget(here), other, "Show a Station Name Here took the only name another station"
                + " had on this page off it without asking, because it is named on another page as well");

            // And it says only this page, since the station stays shown on the other (OB-341)
            assertEquals(AutonomyEditorPanel.takeAnotherNameQuestionForTests(),
                I18n.f("autosetup.ui.confirmTakeAnotherStationsName", session.describeTile(other), session.describeTile(here)),
                "the question says more than that the station leaves this page");

            // Named on another square of this page too, through the store's door: not its only name
            session.getStore().setCaption(new TileKey("main", 3, 2), other);

            panel.showStationNameFor(here);

            assertEquals(session.getCaptionTarget(here), here, "the question was asked, and answered No, about a name the"
                + " station has on another square of this page as well");

            assertTrue(session.captionsFor(other).contains(new TileKey("second", 3, 2)),
                "showing this station's name took the other station's caption on the other page off");
        }
        finally
        {
            AutonomyEditorPanel.answerTakeAnotherNameForTests(null);

            deleteRecursively(folder);
        }
    }

    /**
     * A label can be dropped only on its own page, and moves only that page's caption (OB-341).
     *
     * MUTATION: let `moveCaption` take the station's captions off every page, and the last assertion fails.
     */
    @Test
    public void testALabelDropsOnlyOnItsOwnPageAndMovesOnlyThatPagesCaption() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-drop-page").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main"), aPageWithARun("second")));

            TileKey station = new TileKey("main", 1, 1);
            TileKey here = new TileKey("main", 1, 2);
            TileKey there = new TileKey("second", 3, 2);

            session.setStation(station, true);
            session.getStore().setCaption(here, station);
            session.getStore().setCaption(there, station);

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            assertFalse(panel.canDropCaption(here, new TileKey("second", 3, 3), null),
                "a label can be dropped on a square of another page");

            assertFalse(panel.moveCaption(here, new TileKey("second", 3, 3), null), "a label was moved onto another page");

            assertEquals(session.captionsFor(station), new java.util.LinkedHashSet<>(Arrays.asList(here, there)),
                "a refused drop changed the captions");

            // On its own page it moves, and the other page's is left alone
            TileKey free = new TileKey("main", 1, 3);

            assertTrue(panel.canDropCaption(here, free, null), "a label cannot be dropped on a free square of its own page");
            assertTrue(panel.moveCaption(here, free, null), "the label did not move on its own page");

            assertEquals(session.captionsFor(station), new java.util.LinkedHashSet<>(Arrays.asList(free, there)),
                "moving the label took the other page's caption of the station off");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * No station name is offered on a square the track diagram does not draw: the editor shows the whole page, padding
     * included, but a caption is not a component, so one there would never be shown.  The menu item is present but
     * grayed with the reason on its tooltip, the key agrees, a dragged label is refused, and a blank square inside the
     * area is still offered.
     *
     * MUTATION: leave the area test out of `offersAStationName`, the menu item or `refuseCaptionDrop`, and this fails.
     */
    @Test
    public void testNoNameIsOfferedOnASquareTheRunningDiagramDoesNotDraw() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-outside-area").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main")));

            TileKey station = new TileKey("main", 1, 1);
            TileKey inside = new TileKey("main", 4, 2);
            TileKey outside = new TileKey("main", 9, 2);

            session.setStation(station, true);
            session.setPointName(station, "Hauptbahnhof");
            session.getStore().setCaption(new TileKey("main", 5, 2), station);

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            String name = I18n.t("autosetup.ui.menuShowStationHere");

            // The control: a blank square inside the drawn area is offered, live
            javax.swing.JMenuItem live = itemNamed(panel.buildTileMenu(inside, null), name);

            assertTrue(live != null && live.isEnabled(), "precondition: no live name item on a blank square inside the area");
            assertTrue(keyOffersAName(panel, inside), "precondition: Control+N offers no name on a blank square inside the area");
            assertTrue(panel.canDropCaption(new TileKey("main", 5, 2), inside, null), "precondition: a label cannot be dropped"
                + " on a blank square inside the area");

            // Outside it: present, grayed, and the reason on the tooltip
            javax.swing.JMenuItem shown = itemNamed(panel.buildTileMenu(outside, null), name);

            assertNotNull(shown, "the item is gone from the menu of a square outside the area, rather than greyed");
            assertFalse(shown.isEnabled(), "Show a Station Name Here is live on a square the diagram does not draw");

            assertTrue(shown.getToolTipText() != null
                && shown.getToolTipText().contains(I18n.t("autosetup.ui.captionOutsideTheDrawnArea")),
                "the greyed item does not say why: " + shown.getToolTipText());

            assertFalse(keyOffersAName(panel, outside), "Control+N offers a name on a square the diagram does not draw");

            panel.showStationNameFor(outside);

            assertTrue(session.getCaptionTarget(outside) == null, "Control+N put a caption on a square the diagram does not draw");

            assertFalse(panel.canDropCaption(new TileKey("main", 5, 2), outside, null),
                "a label can be dropped on a square the diagram does not draw");

            assertFalse(panel.moveCaption(new TileKey("main", 5, 2), outside, null), "a label was moved off the diagram");

            assertEquals(session.getCaptionTarget(new TileKey("main", 5, 2)), station, "a refused drop took the caption off");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /** A private boolean question of the panel's, with the square or with nothing. */
    private static boolean asked(AutonomyEditorPanel panel, String name, TileKey tile) throws Exception
    {
        java.lang.reflect.Method method = tile == null
            ? AutonomyEditorPanel.class.getDeclaredMethod(name)
            : AutonomyEditorPanel.class.getDeclaredMethod(name, TileKey.class);

        method.setAccessible(true);

        return (Boolean) (tile == null ? method.invoke(panel) : method.invoke(panel, tile));
    }

    private static Object field(Object on, String name) throws Exception
    {
        java.lang.reflect.Field f = on.getClass().getDeclaredField(name);

        f.setAccessible(true);

        return f.get(on);
    }

    /**
     * Control+H offers a home exactly where the right-click menu does: only on a station, so the key cannot write a
     * home onto a square that is not one.
     */
    @Test
    public void testControlHOffersAHomeOnlyWhereTheMenuDoes() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-h").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main")));

            TileKey station = new TileKey("main", 1, 1);
            TileKey track = new TileKey("main", 3, 1);

            session.setStation(station, true);

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            // The control: a station, where the menu offers a home and so must the key.
            javax.swing.JPopupMenu atTheStation = panel.buildTileMenu(station, null);

            assertTrue(offersAHome(atTheStation), "precondition: the menu offers no home on the station at " + station
                + ": " + itemNames(atTheStation));

            assertTrue(keyOffers(panel, "offersAHome", station),
                "Control+H offers no home on the station at " + station + ", where the menu does");

            // Plain track: no home on the menu, none from the key.
            javax.swing.JPopupMenu onTrack = panel.buildTileMenu(track, null);

            assertFalse(offersAHome(onTrack), "precondition: the menu offers a home on plain track: " + itemNames(onTrack));

            assertFalse(keyOffers(panel, "offersAHome", track),
                "Control+H offers a home on the plain track at " + track + ", where the menu offers " + itemNames(onTrack)
                + " - a home written onto a square that is not a station (MFW-B1)");

            // And the station on a page left out of autonomy: only the bulk tools on the menu.
            session.setPageExcluded("main", true);

            javax.swing.JPopupMenu ignored = panel.buildTileMenu(station, null);

            assertFalse(offersAHome(ignored), "precondition: the menu offers a home on an ignored square: " + itemNames(ignored));

            assertFalse(keyOffers(panel, "offersAHome", station),
                "Control+H offers a home on " + station + ", on a page left out of autonomy, where the menu offers "
                + itemNames(ignored) + " (MFW-B1)");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * Control+H refused on a square that is not a station says why in its own words, not Control+B's maximum-length
     * sentence.
     */
    @Test
    public void testControlHSaysWhyItOffersNoHome() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-h-why").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main")));

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            TileKey track = new TileKey("main", 3, 1);

            assertFalse(keyOffers(panel, "offersAHome", track), "precondition: Control+H would open a dialog on " + track);

            panel.promptHomeFor(track);

            Object hint = field(panel, "hint");
            String shown = hint instanceof javax.swing.JLabel ? String.valueOf(((javax.swing.JLabel) hint).getText()) : "";

            String borrowed = I18n.t("autosetup.ui.infoNotAStationHere");
            String borrowedStart = borrowed.substring(0, borrowed.indexOf('.'));

            assertFalse(shown.contains(borrowedStart),
                "Control+H refused a home on plain track with Control+B's reason, about a maximum train length: " + shown
                + " (MFX-C1)");

            String own = I18n.t("autosetup.ui.infoNotAStationForAHome");
            String ownStart = own.substring(0, Math.max(0, own.indexOf('.')));

            assertTrue(!ownStart.isEmpty() && shown.contains(ownStart),
                "Control+H refused a home on plain track without saying a home needs a station: " + shown);
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * The main window's menu does not name Control+N, which is the notes key there (the same menu is built for its
     * track diagram with `setMenuOnly`).
     */
    @Test
    public void testTheMainWindowsMenuDoesNotNameTheEditorsKey() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ctrl-n-main").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(aPageWithARun("main")));

            TileKey empty = new TileKey("main", 4, 2);

            // The control: the editor's menu names the key.
            AutonomyEditorPanel editor = new AutonomyEditorPanel(session, "main", () -> { });

            String inTheEditor = nameTooltip(editor.buildTileMenu(empty, null));

            assertTrue(inTheEditor != null && inTheEditor.contains(AutonomyEditorPanel.SHORTCUT_STATION),
                "precondition: the editor's menu does not name " + AutonomyEditorPanel.SHORTCUT_STATION + " on " + empty
                + ": " + inTheEditor);

            // The main window's: the same menu, menu-only.
            AutonomyEditorPanel mainWindow = new AutonomyEditorPanel(session, "main", () -> { });

            mainWindow.setMenuOnly(true);

            String inTheMainWindow = nameTooltip(mainWindow.buildTileMenu(empty, null));

            assertNotNull(inTheMainWindow, "precondition: the main window's menu offers no station name on " + empty);

            assertFalse(inTheMainWindow.contains(AutonomyEditorPanel.SHORTCUT_STATION),
                "the main window's menu names " + AutonomyEditorPanel.SHORTCUT_STATION + ", which is the notes key in that"
                + " window: " + inTheMainWindow + " (MFX-C3)");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /**
     * "Stop Showing" is on the menu of every square a caption sits on, so the setup check's error can always be cleared
     * from the editor (OB-341), including a switch or signal and a page left out of autonomy, which a hand-edited setup
     * can caption.  The name item is still not offered there: a caption may not be put there, only taken off.
     *
     * MUTATION: leave the stop item out of either branch, and this fails.
     */
    @Test
    public void testStopShowingIsOnTheMenuOfEverySquareACaptionSitsOn() throws Exception
    {
        needsADisplay();

        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-stop-showing").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            LayoutDiagram page = aPageWithARun("main");

            // A signal, which a caption may not be put on
            page.addComponent(componentType.SIGNAL, 8, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

            session.open(Arrays.asList(page));

            TileKey station = new TileKey("main", 1, 1);
            TileKey signal = new TileKey("main", 8, 1);
            TileKey track = new TileKey("main", 3, 1);

            session.setStation(station, true);
            session.setPointName(station, "Hauptbahnhof");

            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            String stop = I18n.f("autosetup.ui.menuClearStationHereNamed", session.describeTile(station));

            // The control: plain track with no caption on it has nothing to stop
            assertFalse(itemNames(panel.buildTileMenu(track, null)).contains(stop),
                "precondition: the menu offers to stop a caption that is not there: " + itemNames(panel.buildTileMenu(track, null)));

            // A caption on the signal, through the store's own door, which does not refuse where the menu does
            session.getStore().setCaption(signal, station);

            javax.swing.JPopupMenu onTheSignal = panel.buildTileMenu(signal, null);

            assertFalse(offersAName(onTheSignal), "precondition: the menu offers a station name on a signal: " + itemNames(onTheSignal));

            javax.swing.JMenuItem stopOnTheSignal = itemNamed(onTheSignal, stop);

            assertNotNull(stopOnTheSignal, "a caption on a signal cannot be stopped from the menu: " + itemNames(onTheSignal));

            stopOnTheSignal.doClick();

            assertEquals(session.getCaptionTarget(signal), null, "the menu's Stop Showing left the caption on the signal");

            // A caption on plain track on a page left out of autonomy
            session.getStore().setCaption(track, station);
            session.setPageExcluded("main", true);

            javax.swing.JPopupMenu onTheIgnoredTrack = panel.buildTileMenu(track, null);

            assertFalse(offersAName(onTheIgnoredTrack),
                "precondition: the menu offers a station name on an ignored square: " + itemNames(onTheIgnoredTrack));

            javax.swing.JMenuItem stopOnTheTrack = itemNamed(onTheIgnoredTrack, stop);

            assertNotNull(stopOnTheTrack,
                "a caption on a page left out of autonomy cannot be stopped from the menu: " + itemNames(onTheIgnoredTrack));

            stopOnTheTrack.doClick();

            assertEquals(session.getCaptionTarget(track), null, "the menu's Stop Showing left the caption on the ignored page");
        }
        finally
        {
            deleteRecursively(folder);
        }
    }

    /** The menu's item with this text, or null. */
    private static javax.swing.JMenuItem itemNamed(javax.swing.JPopupMenu menu, String text)
    {
        if (menu == null) return null;

        for (java.awt.Component each : menu.getComponents())
        {
            if (each instanceof javax.swing.JMenuItem && text.equals(((javax.swing.JMenuItem) each).getText()))
            {
                return (javax.swing.JMenuItem) each;
            }
        }

        return null;
    }

    /** The tooltip of the Show a Station Name Here item, in any of its three wordings, or null. */
    private static String nameTooltip(javax.swing.JPopupMenu menu)
    {
        if (menu == null) return null;

        java.util.List<String> names = java.util.Arrays.asList(I18n.t("autosetup.ui.menuShowStationHere"),
            I18n.t("autosetup.ui.menuShowStationHereNamed"), I18n.t("autosetup.ui.menuStationShowsItself"));

        for (java.awt.Component each : menu.getComponents())
        {
            if (each instanceof javax.swing.JMenuItem && names.contains(((javax.swing.JMenuItem) each).getText()))
            {
                return ((javax.swing.JMenuItem) each).getToolTipText();
            }
        }

        return null;
    }

    /** A public question of the panel's by name, failing plainly where the panel does not have it. */
    private static boolean keyOffers(AutonomyEditorPanel panel, String name, TileKey tile) throws Exception
    {
        java.lang.reflect.Method asked;

        try
        {
            asked = AutonomyEditorPanel.class.getMethod(name, TileKey.class);
        }
        catch (NoSuchMethodException none)
        {
            fail("AutonomyEditorPanel has no " + name + ": the key asks its own question, not the menu's");

            return false;
        }

        return (Boolean) asked.invoke(panel, tile);
    }

    private static boolean offersAHome(javax.swing.JPopupMenu menu)
    {
        String none = I18n.t("autosetup.ui.menuHomeNone");
        String named = I18n.t("autosetup.ui.menuHomeFor");
        String namedStart = named.contains("{0}") ? named.substring(0, named.indexOf("{0}")) : named;

        for (String name : itemNames(menu))
        {
            if (name == null) continue;

            if (name.equals(none) || (!namedStart.isEmpty() && name.startsWith(namedStart))) return true;
        }

        return false;
    }

    /** What the key acts on - public, so the two doors can be put side by side. */
    private static boolean keyOffersAName(AutonomyEditorPanel panel, TileKey tile) throws Exception
    {
        java.lang.reflect.Method asked;

        try
        {
            asked = AutonomyEditorPanel.class.getMethod("offersAStationName", TileKey.class);
        }
        catch (NoSuchMethodException none)
        {
            fail("AutonomyEditorPanel has no offersAStationName: Control+N asks its own question, which is not the"
                + " menu's - it does not ask isIgnored (MFR-B3)");

            return false;
        }

        return (Boolean) asked.invoke(panel, tile);
    }

    private static boolean offersAName(javax.swing.JPopupMenu menu)
    {
        java.util.List<String> names = itemNames(menu);

        return names.contains(I18n.t("autosetup.ui.menuShowStationHere"))
            || names.contains(I18n.t("autosetup.ui.menuShowStationHereNamed"));
    }

    private static LayoutDiagram aPageWithARun(String name) throws java.io.IOException
    {
        LayoutDiagram page = new LayoutDiagram(name, 12, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);

        for (int x = 2; x <= 5; x++)
        {
            page.addComponent(componentType.STRAIGHT, x, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        }

        page.addComponent(componentType.FEEDBACK, 6, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        // A label, the other square the menu sends to its text menu; (4,2) is blank inside the drawn area, (9,2)
        // outside.
        page.addComponent(componentType.TEXT, 3, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, "Yard");

        page.setPageId(name);

        return page;
    }

    private static java.util.List<String> itemNames(javax.swing.JPopupMenu menu)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        if (menu == null) return out;

        for (java.awt.Component each : menu.getComponents())
        {
            if (each instanceof javax.swing.JMenuItem) out.add(((javax.swing.JMenuItem) each).getText());
        }

        return out;
    }

    private static void needsADisplay()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("AutonomyEditorPanel builds real Swing components - this needs a display");
        }
    }

    private static void deleteRecursively(java.io.File file)
    {
        java.io.File[] children = file.listFiles();

        if (children != null)
        {
            for (java.io.File child : children) deleteRecursively(child);
        }

        file.delete();
    }
}
