package regression;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.StationCaption;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A remembered **None** opens with the captions actually gone (MT-292).
 *
 * Adam, 2026-09-12: *"none is shown in the dropdown, but station names are still drawn."*
 *
 * **The mechanism.** `AutonomyEditorPanel` restores the remembered caption mode while it is being
 * CONSTRUCTED and applies it there. Half of applying it is local - the two tick boxes behind the
 * dropdown - and the other half is the diagram's own text switch, reached through `owner()`, which is
 * `SwingUtilities.getWindowAncestor(this)` and is null until the panel has been added to the editor. So
 * the dropdown said None over a diagram still drawing every station name, which is the state the four
 * exclusive options exist to make impossible.
 *
 * **Three openings, because one proves nothing.** A window that draws no captions for its own reasons
 * satisfies the None claim on any code at all, and a switch that latches off satisfies it for ever
 * after. So the editor is opened with **Station Names** first, then with **None**, then with Station
 * Names again, and the middle one is the claim while the two around it are the controls.
 *
 * MUTATION: remove `applyRememberedCaptionMode` from `LayoutEditor.setAutonomyMode` and the None
 * opening draws every caption the Station Names ones do.
 *
 * @author Adam
 */
public class testARememberedNoneOpensWithTheCaptionsOff
{
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static support.LayoutSandbox sandbox;
    private static AutonomySession session;
    private static LayoutDiagram page;

    /** The page the editor is opened on */
    private static final String PAGE = "1 - Main";

    /** What was drawn on each of the three openings, and whether the text switch was off */
    private static List<String> namedFirst;
    private static List<String> withNone;
    private static List<String> namedAgain;

    private static boolean hiddenAfterNamedFirst;
    private static boolean hiddenAfterNone;
    private static boolean hiddenAfterNamedAgain;

    /** What the preference said before this class touched it */
    private static int captionModeWas;

    /**
     * The preference the panel restores from, named here rather than reached through the panel.
     *
     * `AutonomyEditorPanel.VIEW_PREFS` is private, and it is the same node either way -
     * `userNodeForPackage` of a class in `org.traincontrol.gui`. Writing it from outside is what makes
     * this test about a REMEMBERED setting: setting the dropdown by hand exercises the listener, which
     * is the path that already worked.
     */
    private static java.util.prefs.Preferences prefs()
    {
        return java.util.prefs.Preferences.userNodeForPackage(AutonomyEditorPanel.class);
    }

    private static final String PREF_CAPTION_MODE = "autonomyEditorCaptionMode";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("reading what the editor drew needs a display");
        }

        // THE FROZEN COPY, never the railway Adam is operating.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration to draw captions from");
        }

        page = model.getLayout(PAGE);

        if (page == null) throw new SkipException("no page " + PAGE);

        captionModeWas = prefs().getInt(PREF_CAPTION_MODE, AutonomyEditorPanel.CAPTIONS_STATIONS);

        namedFirst = openWith(AutonomyEditorPanel.CAPTIONS_STATIONS);
        hiddenAfterNamedFirst = page.getEditHideText();

        withNone = openWith(AutonomyEditorPanel.CAPTIONS_NONE);
        hiddenAfterNone = page.getEditHideText();

        namedAgain = openWith(AutonomyEditorPanel.CAPTIONS_STATIONS);
        hiddenAfterNamedAgain = page.getEditHideText();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            prefs().putInt(PREF_CAPTION_MODE, captionModeWas);

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The claim: with None remembered, the editor opens with no captions on the diagram.
     *
     * @throws Exception from Swing
     */
    @Test
    public void testNoneOpensWithNothingDrawn() throws Exception
    {
        assertEquals(withNone.size(), 0,
            "the editor opened with None remembered and drew " + withNone.size() + " captions:"
            + " " + withNone + ". Adam, MT-292: \"none is shown in the dropdown, but station names are"
            + " still drawn\" - the mode is applied while the panel is being built, and the half of it"
            + " that reaches the diagram cannot find a window yet");

        assertTrue(hiddenAfterNone,
            "the diagram's own text switch was still ON after opening with None remembered, so the"
            + " dropdown and the diagram disagree even where no caption happens to be drawn");
    }

    /**
     * The control before it: Station Names really does draw something on this page.
     *
     * Without this the claim above is satisfied by a fixture that draws no captions at all, which is
     * the way it would most easily pass for no reason.
     *
     * @throws Exception from Swing
     */
    @Test
    public void testStationNamesDrawsSomethingToBeginWith() throws Exception
    {
        assertTrue(namedFirst.size() > 0,
            "the editor drew no captions with Station Names remembered either, so this page states"
            + " nothing about captions and the None claim beside it means nothing");

        assertFalse(hiddenAfterNamedFirst,
            "the diagram's text switch was off with Station Names remembered, so the captions above"
            + " were suppressed by the switch rather than by anything this test is about");
    }

    /**
     * And the control after it: the captions come back, so None did not simply latch.
     *
     * @throws Exception from Swing
     */
    @Test
    public void testTheCaptionsComeBackWhenTheModeDoes() throws Exception
    {
        assertEquals(namedAgain.size(), namedFirst.size(),
            "reopening with Station Names drew " + namedAgain.size() + " captions where the first"
            + " opening drew " + namedFirst.size() + ". A switch that goes off and stays off satisfies"
            + " the None claim for ever after without ever being right");

        assertFalse(hiddenAfterNamedAgain,
            "the text switch stayed off when the mode came back, so None turns the captions off"
            + " permanently rather than for as long as it is chosen");
    }

    /**
     * Opens the editor in autonomy mode with this caption mode REMEMBERED, and reads what it drew.
     */
    private static List<String> openWith(int mode) throws Exception
    {
        prefs().putInt(PREF_CAPTION_MODE, mode);

        final LayoutEditor[] built = new LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        settle();

        final List<String> drawn = new ArrayList<>();

        javax.swing.SwingUtilities.invokeAndWait(() -> collect(built[0].getContentPane(), drawn));

        javax.swing.SwingUtilities.invokeAndWait(() -> built[0].dispose());

        return drawn;
    }

    /**
     * Every caption the container holds.
     *
     * `StationCaption` only - the window's own labels are ordinary JLabels, and counting those would
     * make this a test about the sidebar.
     */
    private static void collect(java.awt.Container container, List<String> into)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof StationCaption)
            {
                String text = ((StationCaption) child).getText();

                if (text != null && !text.trim().isEmpty()) into.add(text);
            }

            if (child instanceof java.awt.Container) collect((java.awt.Container) child, into);
        }
    }

    /**
     * Waits for the tiles, and then for the posted work the editor's own posted work starts.
     *
     * `setAutonomyMode` ends by posting a redraw which itself posts an annotation refresh, so one turn
     * of the queue is not enough to see the finished window.
     */
    private static void settle() throws Exception
    {
        final java.util.concurrent.CountDownLatch settled =
            new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

        for (int turn = 0; turn < 4; turn++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
