package regression;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;

/**
 * FR-037: the one-way arrows on the ordinary diagram, and what decides whether they appear.
 *
 * Adam, 2026-08-27: "option to show RESTRICTION arrows in track diagram view mode - add this to the
 * layout preferences jmenu", then "move 'Grey Station Labels' into the Autonomy Preferences menu
 * subsection, group with the new one and with Show Inactive Labels, and add a divider above.  Make
 * sure that when autonomy isn't loaded, changing these settings doesn't blow anything up."
 *
 * **The visibility question is the one he asked for tests on, and it is the one worth having them.**
 * A one-way arrow is a statement about an autonomy SETUP. With autonomy switched off, or with no
 * configuration loaded, there is no setup - and drawing arrows anyway would describe restrictions that
 * are not in force on a railway somebody is driving by hand. The preference must therefore be the
 * LAST of several conditions rather than the only one.
 *
 * @author Adam
 */
public class testDiagramDrawingSettings
{
    /**
     * The arrows are ON by default, and the default is what is actually asked.
     *
     * Adam, 2026-08-28, having used it: "make this setting be on by default."  It shipped off, on the
     * reasoning that a page of arrows over a railway nobody is configuring is what kept directions in
     * the editor - but that was about ALL directions, and this draws only the restricted ones.
     *
     * **The stored value is removed first, and that is the whole point of this version.** The test
     * before it simply called the accessor and asserted the answer, which read whatever value some
     * earlier run had written into this machine's preferences - so when the default was changed from
     * false to true the assertion went on passing, because a stored false was answering it. It was
     * testing the developer's own settings, and on a clean install it would have said something else.
     *
     * The previous value is put back, because these are the real preferences of whoever is running the
     * suite and a test has no business changing what their application does tomorrow.
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
     * And the menu's tick starts in the same place the drawing does.
     *
     * Two reads of one default, in two files. A menu item that came up unticked while the arrows were
     * drawn would be a window disagreeing with itself about a setting the user has not touched.
     *
     * MUTATION: changing either default alone fails this.
     */
    @Test
    public void testTheMenuAgreesWithTheDrawingAboutTheDefault() throws Exception
    {
        String ui = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/TrainControlUI.java")), StandardCharsets.UTF_8);

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
     * Nothing draws arrows unless there is a setup to draw them from.
     *
     * Adam: "make sure that when autonomy isn't loaded, changing these settings doesn't blow anything
     * up."  The preference decides whether the operator WANTS them; these four conditions decide
     * whether there is anything to say. All of them are in `showStaticAutonomyLayer`, ahead of the
     * annotation being built at all, and each one clears the layer before returning - so switching
     * autonomy off does not leave the last drawing on screen.
     *
     * Read rather than run: reaching this needs a window, a control station and a railway, and what
     * can silently break is a guard being removed, which is visible.
     *
     * MUTATION: deleting any of the three returns, or moving the clear below them, fails this.
     */
    @Test
    public void testNoArrowsWithoutASetupToDrawThemFrom() throws Exception
    {
        String ui = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/TrainControlUI.java")), StandardCharsets.UTF_8);

        String layer = bodyOf(ui, "public void showStaticAutonomyLayer(boolean show)");

        assertFalse(layer.isEmpty(), "cannot find showStaticAutonomyLayer - has it been renamed?");

        int cleared = layer.indexOf("clearAnnotations()");

        assertTrue(cleared > 0,
            "the annotation layer is no longer cleared, so switching autonomy off leaves the last "
            + "drawing on the diagram describing a setup that is gone");

        int offOrNoGraph = layer.indexOf("if (!show || session == null || session.getGraph() == null)");

        assertTrue(offOrNoGraph > 0,
            "nothing checks that autonomy is showing and that there is a graph, so the arrows would "
            + "be drawn for a railway with no setup on it");

        int noConfiguration = layer.indexOf("if (activeDiagramConfiguration == null) return;");

        assertTrue(noConfiguration > 0,
            "nothing checks that a configuration is LOADED - the graph exists whenever there is "
            + "track, so without this every sensor would be described before any setup was chosen");

        // The clearing comes FIRST, which is what makes turning it off take effect.
        assertTrue(cleared < offOrNoGraph && cleared < noConfiguration,
            "the layer is cleared after the guards return, so switching autonomy off leaves every "
            + "arrow and badge on screen - the state this ordering was written to stop");
    }

    /**
     * The three drawing settings are one group, under autonomy, with a divider above.
     *
     * Adam asked for the grouping, and it is checkable: Grey Station Labels used to be added to the
     * LAYOUT menu, which is where the diagram itself is configured. The colour of a station caption is
     * not a fact about the diagram - it is a fact about what autonomy draws on one.
     *
     * MUTATION: adding any of the three to layoutMenuItem again, or dropping the separator, fails this.
     */
    @Test
    public void testTheDrawingSettingsAreOneGroupUnderAutonomy() throws Exception
    {
        String ui = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/TrainControlUI.java")), StandardCharsets.UTF_8);

        String group = bodyOf(ui, "private void buildDiagramDrawingMenu()");

        assertFalse(group.isEmpty(), "cannot find the group - has buildDiagramDrawingMenu been "
            + "renamed?");

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

        assertFalse(ui.contains("layoutMenuItem.add(this.greyStationLabelsMenuItem)"),
            "Grey Station Labels is back in the Layout menu, which is where the diagram is "
            + "configured rather than where what autonomy draws on it is");

        assertFalse(ui.contains("layoutMenuItem.add(this.restrictionArrowsMenuItem)"),
            "the one-way arrows setting is in the Layout menu rather than with the group it belongs "
            + "to");

        // And the group survives having no autonomy menu to go in.
        assertTrue(group.contains("if (autonomyToolbarMenu == null) return;"),
            "the group is built without checking that the menu it goes in exists, so a window built "
            + "before that menu throws on startup");
    }

    /**
     * Turning the arrows on asks the overlay what it is showing, rather than assuming.
     *
     * This is the "does not blow anything up" case that actually has teeth. The action could have
     * called the refresh with `true` - it looks harmless, and with a setup loaded it behaves
     * identically. With no setup, or with the overlay switched off, it would ask the diagram to draw a
     * layer the operator has turned off.
     *
     * MUTATION: passing `true` fails this.
     */
    @Test
    public void testTurningTheArrowsOnRefreshesWhatIsActuallyShown() throws Exception
    {
        String ui = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/TrainControlUI.java")), StandardCharsets.UTF_8);

        String group = bodyOf(ui, "private void buildDiagramDrawingMenu()");

        assertTrue(group.contains("autonomyOverlayToggle != null && autonomyOverlayToggle.isOverlayShown()"),
            "the arrows setting refreshes the autonomy layer without asking whether that layer is "
            + "being shown - so switching the arrows on would turn the whole static overlay on with "
            + "them, and with no toggle built yet it would throw");
    }

    /**
     * The EDITOR's arrival chevrons are not touched by the viewer's setting (Adam, 2026-08-28).
     *
     * "Be careful not to disturb their behavior in the editor."  The editor governs its arrivals with
     * its own four-way control - all, restrictions, none, arrivals - and one of those modes exists
     * precisely to show EVERY side of EVERY station so the setting can be read. That mode is the
     * reason `arrivalMarks` takes an `always` flag at all.
     *
     * So the editor must go on passing its own answer, and must never read this preference. The second
     * assertion is the one that matters: coupling the two windows would be easy to do by accident and
     * impossible to see afterwards.
     *
     * MUTATION: having the editor ask `diagramShowsRestrictionArrows` fails this.
     */
    @Test
    public void testTheEditorsArrivalsAreNotCoupledToTheViewersSetting() throws Exception
    {
        String panel = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/AutonomyEditorPanel.java")), StandardCharsets.UTF_8);

        assertTrue(panel.contains("session.arrivalMarks(tile, directions.getSelectedIndex() == VIEW_ARRIVALS)"),
            "the editor no longer asks its own directions control about arrivals, so the mode whose "
            + "whole purpose is showing every side of every station has lost its answer");

        assertFalse(panel.contains("diagramShowsRestrictionArrows"),
            "the autonomy editor now reads the ordinary diagram's preference, which is exactly what "
            + "Adam asked not to happen - the editor is where these are being decided, and it has its "
            + "own control for them");

        // The METHOD NAME is not the coupling; the STORED PREFERENCE is (TST-C16). Reading
        // DIAGRAM_RESTRICTION_ARROWS out of Preferences directly - skipping the accessor entirely -
        // would couple the two windows exactly as before while leaving the check above satisfied.
        assertFalse(panel.contains("DIAGRAM_RESTRICTION_ARROWS"),
            "the autonomy editor now reads the viewer's stored preference key directly, bypassing "
            + "diagramShowsRestrictionArrows() rather than calling it - the coupling Adam asked to "
            + "avoid, reached by a different door");
    }

    /**
     * A method, from its declaration to its closing brace.
     */
    private String bodyOf(String source, String declaration)
    {
        int at = source.indexOf(declaration);

        if (at < 0) return "";

        int open = source.indexOf('{', at + declaration.length());

        if (open < 0) return "";

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(at, i + 1);
        }

        return "";
    }
}
