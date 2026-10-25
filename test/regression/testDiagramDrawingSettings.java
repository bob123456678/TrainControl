package regression;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import support.SourceOf;
import org.traincontrol.gui.TrainControlUI;

/**
 * FR-037: the one-way arrows on the ordinary diagram, and what decides whether they appear. A one-way arrow is a
 * statement about an autonomy SETUP: with autonomy switched off, or with no configuration loaded, drawing arrows would
 * describe restrictions not in force on a railway somebody is driving by hand, so the preference must be the LAST of
 * several conditions rather than the only one.
 *
 * @author Adam
 */
public class testDiagramDrawingSettings
{
    /**
     * The arrows are ON by default, and the default is what is actually asked. The stored value is removed first: a
     * test that only called the accessor read whatever an earlier run had written into this machine's preferences, so a
     * stored false kept answering after the default changed to true. The previous value is put back, since these are
     * the real preferences of whoever runs the suite.
     */
    @Test
    public void testTheArrowsAreOnByDefault()
    {
        java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

        boolean had = prefs.get(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, null) != null;
        boolean was = prefs.getBoolean(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);

        try
        {
            prefs.remove(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);

            assertTrue(TrainControlUI.diagramShowsRestrictionArrows(),
                "with nothing stored, the travel restrictions are off - so a new installation shows "
                + "none of them, which is what Adam asked to change");
        }
        finally
        {
            if (had) prefs.putBoolean(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, was);
            else prefs.remove(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);
        }
    }

    /**
     * And the menu's tick starts in the same place the drawing does: two reads of one default, in two files, and a menu
     * item unticked while the arrows are drawn is a window disagreeing with itself.
     *
     * MUTATION: changing either default alone fails this.
     */
    @Test
    public void testTheMenuAgreesWithTheDrawingAboutTheDefault() throws Exception
    {
        // The program's code, wherever it holds the two reads.
        String ui = SourceOf.allCode();

        int defaults = 0;

        for (int at = ui.indexOf("DIAGRAM_RESTRICTION_ARROWS,"); at >= 0;
            at = ui.indexOf("DIAGRAM_RESTRICTION_ARROWS,", at + 1))
        {
            int end = ui.indexOf(')', at);

            if (end > at && ui.substring(at, end).contains("true")) defaults++;
        }

        assertEquals(defaults, 2,
            "the menu's tick and the drawing read different defaults for the travel restrictions, so "
            + "on a fresh installation the window disagrees with itself about a setting nobody has "
            + "touched yet");
    }

    /**
     * Nothing draws arrows unless there is a setup to draw them from. The preference decides whether the operator WANTS
     * them; these four conditions decide whether there is anything to say. All are in `showStaticAutonomyLayer`, ahead
     * of the annotation being built, and each clears the layer before returning, so switching autonomy off does not
     * leave the last drawing on screen. Read rather than run: reaching this needs a window, a control station and a
     * railway, and what can silently break is a guard being removed.
     *
     * MUTATION: deleting either return, or the clear before either, fails this.
     */
    @Test
    public void testNoArrowsWithoutASetupToDrawThemFrom() throws Exception
    {
        String layer = SourceOf.declaration("public void showStaticAutonomyLayer(boolean show)");

        int cleared = layer.indexOf("clearAnnotations()");

        assertTrue(cleared > 0,
            "the annotation layer is no longer cleared, so switching autonomy off leaves the last "
            + "drawing on the diagram describing a setup that is gone");

        int offOrNoGraph = layer.indexOf("if (!show || session == null || session.getGraph() == null)");

        assertTrue(offOrNoGraph > 0,
            "nothing checks that autonomy is showing and that there is a graph, so the arrows would "
            + "be drawn for a railway with no setup on it");

        int noConfiguration = layer.indexOf("if (activeDiagramConfiguration == null)");

        assertTrue(noConfiguration > 0,
            "nothing checks that a configuration is LOADED - the graph exists whenever there is "
            + "track, so without this every sensor would be described before any setup was chosen");

        // Each return clears the layer first, which makes turning it off take effect: a redraw with something to draw
        // keeps the marks that have not changed (speed), and a guard that returns without the clear leaves every arrow
        // and badge on screen.
        for (int guard : new int[] {offOrNoGraph, noConfiguration})
        {
            int returns = layer.indexOf("return;", guard);

            int clears = layer.indexOf("clearAnnotations()", guard);

            assertTrue(returns > guard && clears > guard && clears < returns,
                "a guard returns before clearing the layer, so switching autonomy off leaves every arrow and badge on"
                + " screen - the state this ordering was written to stop");
        }
    }

    /**
     * The three drawing settings are one group, under autonomy, with a divider above: the color of a station caption is
     * a fact about what autonomy draws, not about the diagram, so it does not belong in the LAYOUT menu.
     *
     * MUTATION: adding any of the three to layoutMenuItem again, or dropping the separator, fails this.
     */
    @Test
    public void testTheDrawingSettingsAreOneGroupUnderAutonomy() throws Exception
    {
        String group = SourceOf.declaration("private void buildDiagramDrawingMenu()");

        assertTrue(group.contains("autonomyToolbarMenu.addSeparator()"),
            "the divider above the group is gone, so the three settings run on from whatever is "
            + "above them in the menu");

        for (String item : new String[] {"greyStationLabelsMenuItem", "restrictionArrowsMenuItem",
            "menuShowInactiveLabels"})
        {
            assertTrue(group.contains(item),
                item + " is not in the group, so the three settings that decide what autonomy draws "
                + "on the diagram are not together");
        }

        assertFalse(SourceOf.allCode().contains("layoutMenuItem.add(this.greyStationLabelsMenuItem)"),
            "Grey Station Labels is back in the Layout menu, which is where the diagram is "
            + "configured rather than where what autonomy draws on it is");

        assertFalse(SourceOf.allCode().contains("layoutMenuItem.add(this.restrictionArrowsMenuItem)"),
            "the one-way arrows setting is in the Layout menu rather than with the group it belongs "
            + "to");

        // And the group survives having no autonomy menu to go in.
        assertTrue(group.contains("if (autonomyToolbarMenu == null) return;"),
            "the group is built without checking that the menu it goes in exists, so a window built "
            + "before that menu throws on startup");
    }

    /**
     * Turning the arrows on asks the overlay what it is showing, rather than assuming: calling the refresh with `true`
     * looks harmless and behaves identically with a setup loaded, but with no setup, or the overlay switched off, it
     * would ask the diagram to draw a layer the operator turned off.
     *
     * MUTATION: passing `true` fails this.
     */
    @Test
    public void testTurningTheArrowsOnRefreshesWhatIsActuallyShown() throws Exception
    {
        String group = SourceOf.declaration("private void buildDiagramDrawingMenu()");

        assertTrue(group.contains("autonomyOverlayToggle != null && autonomyOverlayToggle.isOverlayShown()"),
            "the arrows setting refreshes the autonomy layer without asking whether that layer is "
            + "being shown - so switching the arrows on would turn the whole static overlay on with "
            + "them, and with no toggle built yet it would throw");
    }

    /**
     * The EDITOR's arrival chevrons are not touched by the viewer's setting (*"Be careful not to disturb their behavior
     * in the editor."*). The editor governs its arrivals with its own four-way control, one mode of which shows EVERY
     * side of EVERY station so the setting can be read (the reason `arrivalMarks` takes an `always` flag). It must go
     * on passing its own answer and never read this preference; coupling the two windows would be easy to do by
     * accident and impossible to see afterwards.
     *
     * MUTATION: having the editor ask `diagramShowsRestrictionArrows` fails this.
     */
    @Test
    public void testTheEditorsArrivalsAreNotCoupledToTheViewersSetting() throws Exception
    {
        assertTrue(SourceOf.allCode().contains("session.arrivalMarks(tile, directionsView() == VIEW_ARRIVALS)"),
            "the editor no longer asks its own directions control about arrivals, so the mode whose "
            + "whole purpose is showing every side of every station has lost its answer");

        // The editor's code is not one file, so the claim asks the whole program: the accessor is named by its
        // declaration, the drawing's own read in TrainControlUI and the session's static annotation, and nothing else.
        // A fourth place is either legitimate (count it here) or the editor reading the viewer's setting. Place by
        // place as well (FR-136): a total of three holds when the session's read goes and the editor gains one in the
        // same change.
        String readers = "something else now reads the ordinary diagram's preference besides its accessor, the"
            + " window's drawing and the session's static annotation - if it is the autonomy editor, that is exactly"
            + " what Adam asked not to happen: the editor is where these are being decided, and it has its own control"
            + " for them";

        assertEquals(SourceOf.occurrences("diagramShowsRestrictionArrows"), 3, readers);

        assertEquals(countsIn("diagramShowsRestrictionArrows",
                "public static boolean diagramShowsRestrictionArrows()",
                "public void showStaticAutonomyLayer(boolean show)",
                "public TileAnnotation staticAnnotationFor(TileKey tile)"),
            java.util.Arrays.asList(1, 1, 1), readers);

        // The METHOD NAME is not the coupling; the STORED PREFERENCE is: reading DIAGRAM_RESTRICTION_ARROWS out of
        // Preferences directly would couple the two windows while leaving the check above satisfied.
        String keyNamed = "the stored preference key is now named somewhere beyond its declaration, the accessor, and"
            + " the menu's tick and write - if it is the autonomy editor reading it directly, bypassing "
            + "diagramShowsRestrictionArrows() rather than calling it, that is the coupling Adam asked to "
            + "avoid, reached by a different door";

        assertEquals(SourceOf.occurrences("DIAGRAM_RESTRICTION_ARROWS"), 4, keyNamed);

        // Place by place as well: the declaration, the accessor, and the menu's tick and write.
        assertEquals(countsIn("DIAGRAM_RESTRICTION_ARROWS", "public static final String DIAGRAM_RESTRICTION_ARROWS",
            "public static boolean diagramShowsRestrictionArrows()", "private void buildDiagramDrawingMenu()"),
            java.util.Arrays.asList(1, 1, 2), keyNamed);
    }

    /** How many times a text is in the code of each of these declarations, found wherever the program keeps them. */
    private static java.util.List<Integer> countsIn(String needle, String... signatures)
    {
        java.util.List<Integer> counts = new java.util.ArrayList<>();

        for (String signature : signatures)
        {
            String code = SourceOf.withoutComments(SourceOf.declaration(signature));

            int found = 0;

            for (int at = code.indexOf(needle); at >= 0; at = code.indexOf(needle, at + needle.length())) found++;

            counts.add(found);
        }

        return counts;
    }
}
