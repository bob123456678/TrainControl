package regression;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * Inserting or removing a row or a column, and what happens to the setup on the squares that move.
 *
 * Adam, testing the shift operations: "Mostly OK after shifting, but links still got unlinked.  Seems
 * the coordinate mapping there may be an issue."
 *
 * A shift is a move like any other - it hands moveTiles a map of every square in the range and where it
 * is going - so the same rules apply and the same things can go wrong.  What makes it different from a
 * drag is SHAPE: the range covers half the page, every source lands on another source, and the one
 * destination outside the range is the row or column that has just been vacated at the far end.
 *
 * The link case is the sharp one because a pairing is two entries, one on each page, and only one of the
 * pages is being shifted.  The far end has to be rewritten by a move it is not part of.
 */
public class testDiagramShiftKeepsSetup
{
    private static final String PAGE = "1 - Main";
    private static final String FAR = "2 - Bottom";

    /**
     * A link on the shifted page keeps its partner, and its partner keeps it.
     */
    @Test
    public void testAShiftedLinkKeepsItsPairing()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey link = at(2, 5);
        TileKey partner = new TileKey(FAR, 1, 1);

        store.pairPortals(link, partner);
        store.setLinkName(link, "to the yard");
        store.setPortalDisabled(link, true);

        // A row inserted at 3: everything from row 3 down moves one row down
        store.moveTiles(shift(false, 3, 11, 0, 1), null);

        TileKey now = at(2, 6);

        assertEquals(store.getPortalPartner(now), partner,
            "the link arrived unpaired after a shift - which is what Adam saw");

        assertEquals(store.getPortalPartner(partner), now,
            "the far end is still pointing at the square the link used to be on, so the pairing is "
            + "broken from the page that was not shifted");

        assertEquals(store.getLinkName(now), "to the yard", "the link lost its name");

        assertTrue(store.isPortalDisabled(now), "and came back on");
    }

    /**
     * Both ends move when both pages are shifted in the same gesture.
     *
     * Not something the editor does today - a shift is one page - but the store is handed the map, and a
     * map covering two pages must not leave a pairing half-rewritten.
     */
    @Test
    public void testAPairingSurvivesBothEndsMoving()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        store.pairPortals(at(2, 5), new TileKey(FAR, 1, 1));

        Map<TileKey, TileKey> moving = shift(false, 3, 11, 0, 1);

        moving.put(new TileKey(FAR, 1, 1), new TileKey(FAR, 1, 2));

        store.moveTiles(moving, null);

        assertEquals(store.getPortalPartner(at(2, 6)), new TileKey(FAR, 1, 2),
            "the near end is pointing at where the far end used to be");

        assertEquals(store.getPortalPartner(new TileKey(FAR, 1, 2)), at(2, 6),
            "and the far end at where the near end used to be");
    }

    /**
     * A shift UP, where the range's first square lands on the row being written over.
     *
     * The opposite direction, and the one where something really is destroyed: the row above the range
     * is built over by the row below it, so whatever was on it goes.  A link there loses its pairing at
     * BOTH ends, which is correct - the tile is gone - and is worth pinning so that "links unpaired
     * after a shift" can be told apart from this.
     */
    @Test
    public void testAShiftUpDestroysWhatItWritesOver()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey doomed = at(2, 4);
        TileKey partner = new TileKey(FAR, 1, 1);

        store.pairPortals(doomed, partner);

        // Everything from row 5 up moves one row up, onto row 4
        store.moveTiles(shift(false, 5, 11, 0, -1), null);

        assertNull(store.getPortalPartner(doomed),
            "the link was built over by the row below it, so the pairing must go with it");

        assertNull(store.getPortalPartner(partner),
            "and the far end must not be left pointing at a square whose link has been destroyed");
    }

    /**
     * Everything else on a shifted square travels too.
     */
    @Test
    public void testAShiftedStationKeepsEverything()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey was = at(4, 6);

        store.setStation(was, true);
        store.setPointName(was, "Platform 3");
        store.setTileLength(was, 42);
        store.setCaption(at(4, 7), was);

        store.moveTiles(shift(false, 3, 11, 0, 1), null);

        TileKey now = at(4, 7);

        assertTrue(store.isStation(now), "the station did not travel with the shift");

        assertEquals(store.getPointName(now), "Platform 3", "nor did its name");

        assertEquals(store.getTileLength(now), 42, "nor its length");

        // The caption was on 4,7 and has itself shifted to 4,8, and still names the station
        assertEquals(store.getCaptionTarget(at(4, 8)), now,
            "the label shifted with everything else but is naming the old square");
    }

    /**
     * The same thing again through a live SESSION, with the diagram actually shifted underneath it.
     *
     * The tests above hand the store a map directly.  This one does what the editor does - shift the
     * page, then tell the session - so it also covers the rebuild the session runs afterwards, which
     * re-derives the whole graph from the pages and applies the store to it.  That rebuild is the only
     * part of the real path the store-level tests cannot see, and it is where a pairing could be
     * dropped for looking stale.
     */
    @Test
    public void testAShiftThroughTheSessionKeepsThePairing() throws Exception
    {
        File folder = Files.createTempDirectory("tc-shift").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            LayoutDiagram main = new LayoutDiagram(PAGE, 10, 12, null, null);
            LayoutDiagram far = new LayoutDiagram(FAR, 6, 6, null, null);

            // A run of track with a link at the end of it, on each page
            main.addComponent(componentType.FEEDBACK, 1, 5, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
            main.addComponent(componentType.STRAIGHT, 2, 5, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            main.addComponent(componentType.LINK, 3, 5, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

            far.addComponent(componentType.LINK, 1, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            far.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            far.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

            main.setPageId("1");
            far.setPageId("2");

            // What the parser does after building one, and what every page in the application has had
            // done to it: without it maxy is the row COUNT rather than the last INDEX, and shiftDown
            // writes a row past the end.  See testDiagramResize.
            main.checkBounds();
            far.checkBounds();

            session.open(Arrays.asList(main, far));

            TileKey link = at(3, 5);
            TileKey partner = new TileKey(FAR, 1, 1);

            session.getStore().pairPortals(link, partner);

            assertEquals(session.getStore().getPortalPartner(link), partner, "the fixture is wrong");

            // What the editor does: the map first, from the dimensions as they stand
            Map<TileKey, TileKey> moving = shift(false, 4, main.getSy() - 1, 0, 1);

            main.shiftDown(4);

            session.moveTiles(moving);

            TileKey now = at(3, 6);

            assertEquals(session.getStore().getPortalPartner(now), partner,
                "the link came unpaired when the page was shifted - which is what Adam reported, and "
                + "what the store-level tests above say cannot happen, so it happens in the rebuild");

            assertEquals(session.getStore().getPortalPartner(partner), now,
                "the far end is pointing at the square the link used to be on");
        }
        finally
        {
            delete(folder);
        }
    }

    /**
     * Shift LEFT through the session, which is the other axis and the other direction.
     *
     * Adam's suspicion was the coordinate mapping, and a transposition would live exactly here: the map
     * builder takes a boolean for which axis it is walking, and the two loops it drives are the same
     * shape with x and y the other way round.
     */
    @Test
    public void testAShiftLeftThroughTheSessionKeepsThePairing() throws Exception
    {
        File folder = Files.createTempDirectory("tc-shift-left").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            LayoutDiagram main = new LayoutDiagram(PAGE, 12, 10, null, null);
            LayoutDiagram far = new LayoutDiagram(FAR, 6, 6, null, null);

            main.addComponent(componentType.FEEDBACK, 5, 2, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
            main.addComponent(componentType.STRAIGHT, 6, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            main.addComponent(componentType.LINK, 7, 2, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

            far.addComponent(componentType.LINK, 1, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

            main.setPageId("1");
            far.setPageId("2");

            main.checkBounds();
            far.checkBounds();

            session.open(Arrays.asList(main, far));

            TileKey link = at(7, 2);
            TileKey partner = new TileKey(FAR, 1, 1);

            session.getStore().pairPortals(link, partner);

            // Everything from column 4 leftwards by one, which is what the editor asks for when the
            // pointer is on column 3
            Map<TileKey, TileKey> moving = shift(true, 4, main.getSx() - 1, -1, 0);

            main.shiftLeft(3);

            session.moveTiles(moving);

            TileKey now = at(6, 2);

            assertEquals(session.getStore().getPortalPartner(now), partner,
                "the link came unpaired when the page was shifted left");

            assertEquals(session.getStore().getPortalPartner(partner), now,
                "the far end is pointing at the square the link used to be on");
        }
        finally
        {
            delete(folder);
        }
    }

    /**
     * The real gesture - `LayoutEditor.shiftDown()`, not this file's own `shift()` helper - carries a
     * station on the far column.
     *
     * Every test above hands `moveTiles` a map this file builds itself, with `otherEnd` hardcoded to
     * 22. The only production producer of that map is the private `LayoutEditor.setupShift`, called
     * from `shiftUp`, `shiftDown`, `shiftLeft` and `shiftRight` - and grepping `test/` for `setupShift`
     * before this method found one hit, a comment on the `shift()` helper above. Adam's own report was
     * about this exact mapping: "Mostly OK after shifting, but links still got unlinked. Seems the
     * coordinate mapping there may be an issue."
     *
     * The page is 21 columns by 16 rows on purpose - the two dimensions have to differ or a
     * transposed bound is invisible - and the station sits on column 18: inside 0..20 (`getSx() - 1`,
     * the correct bound for a ROW shift) but outside 0..15 (`getSy() - 1`, the wrong one).
     *
     * MUTATION this catches: transpose `LayoutEditor.java`'s `setupShift` so a row shift
     * (`across == false`) bounds the column loop by `getSy() - 1` instead of `getSx() - 1`. On this
     * page that stops the loop at column 15 of 20, and the station on column 18 - deliberately placed
     * past that - never enters the map: `moveTiles` never hears about it, and it is left behind on the
     * row the shift moved everything else off of. Every test above stays green, because none of them
     * ever calls `setupShift` - they build the map by hand, at the width they choose.
     */
    @Test
    public void testTheEditorsShiftDownRespectsThePageWidth() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the editor is a window");
        }

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];
        final org.traincontrol.gui.LayoutEditor[] editor = new org.traincontrol.gui.LayoutEditor[1];

        try
        {
            // Before the model, not just before the window (OB-111) - constructing a TrainControlUI
            // reads the layout-path preference, and without the sandbox it is Adam's own railway.
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

            final org.traincontrol.marklin.MarklinControlStation finalModel = model;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui[0] = new org.traincontrol.gui.TrainControlUI();
                    ui[0].setViewListener(finalModel, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            // Our own page, in our own temp folder - not "1 - Main", so this does not depend on what
            // the sandboxed fixture happens to contain.
            File autonomyFolder = Files.createTempDirectory("tc-shift-gesture").toFile();

            AutonomySession session = new AutonomySession(autonomyFolder);

            LayoutDiagram diagram = new LayoutDiagram("Shift Gesture Page", 21, 16, null, null);

            // Every square counts towards the bounds - otherwise checkBounds ties an empty diagram's
            // extent to nothing and the grid built from it is too small to reach column 18 at all.
            diagram.setEdit(true);
            diagram.checkBounds();

            session.open(Arrays.asList(diagram));

            int farColumn = 18;
            int hoverRow = 5;

            session.getStore().setStation(at(diagram.getName(), farColumn, hoverRow), true);
            session.getStore().setPointName(at(diagram.getName(), farColumn, hoverRow),
                "Far Column Station");

            // getAutonomySession() only builds a session when the field is still null - pointed here
            // at ours instead, so the editor's real setupShift/moveTiles call writes to the session
            // this test can see.
            java.lang.reflect.Field sessionField =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomySession");
            sessionField.setAccessible(true);
            sessionField.set(ui[0], session);

            javax.swing.SwingUtilities.invokeAndWait(() ->
                editor[0] = new org.traincontrol.gui.LayoutEditor(diagram, 30, ui[0], 0));

            java.lang.reflect.Method drawGrid =
                org.traincontrol.gui.LayoutEditor.class.getDeclaredMethod("drawGrid");
            drawGrid.setAccessible(true);

            java.lang.reflect.Field hoveredY =
                org.traincontrol.gui.LayoutEditor.class.getDeclaredField("lastHoveredY");
            hoveredY.setAccessible(true);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    // Builds the grid the constructor leaves for the first paint to ask for.
                    drawGrid.invoke(editor[0]);

                    // What hovering row 5 before the drag would have set.
                    hoveredY.setInt(editor[0], hoverRow);

                    // The real gesture.
                    editor[0].shiftDown();
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            assertTrue(session.getStore().isStation(at(diagram.getName(), farColumn, hoverRow + 1)),
                "the far-column station did not follow the row shift through the real "
                + "LayoutEditor.shiftDown() gesture - the store-level tests above prove the RULE "
                + "carries it, so the gap is in the coordinate map the EDITOR builds for it");

            assertEquals(session.getStore().getPointName(at(diagram.getName(), farColumn,
                hoverRow + 1)), "Far Column Station", "it arrived unnamed");
        }
        finally
        {
            if (editor[0] != null)
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    private static void delete(File file)
    {
        File[] kids = file.listFiles();

        if (kids != null) for (File kid : kids) delete(kid);

        file.delete();
    }

    /**
     * The map a shift hands to the store: every square from one line to another, moved by one.
     *
     * The same shape LayoutEditor.setupShift builds - a square outside the range is left out entirely
     * rather than mapped to itself, so the store can tell a square that moved from a square something
     * arrived on.
     */
    private static Map<TileKey, TileKey> shift(boolean across, int from, int to, int dx, int dy)
    {
        Map<TileKey, TileKey> moving = new LinkedHashMap<>();

        int otherEnd = 22;

        for (int line = from; line <= to; line++)
        {
            for (int other = 0; other <= otherEnd; other++)
            {
                int x = across ? line : other;
                int y = across ? other : line;

                moving.put(at(x, y), at(x + dx, y + dy));
            }
        }

        return moving;
    }

    private static TileKey at(int x, int y)
    {
        return new TileKey(PAGE, x, y);
    }

    private static TileKey at(String page, int x, int y)
    {
        return new TileKey(page, x, y);
    }
}
