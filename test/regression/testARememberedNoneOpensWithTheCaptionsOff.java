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

    /** And the writing on the diagram that is not a caption, for each opening (OB-272) */
    private static List<String> ownUnderNamedFirst;
    private static List<String> ownUnderNone;
    private static List<String> withLabelsOnly;
    private static List<String> ownUnderLabelsOnly;
    private static boolean hiddenAfterLabelsOnly;

    /** The caption option after each press of Control+L, starting from Station Names */
    private static List<Integer> cycled;

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
        return org.traincontrol.gui.TrainControlUI.getPrefs();
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
        ownUnderNamedFirst = lastOwn;
        movableUnderNamedFirst = lastMovable;
        hiddenAfterNamedFirst = page.getEditHideText();

        withNone = openWith(AutonomyEditorPanel.CAPTIONS_NONE);
        ownUnderNone = lastOwn;
        hiddenAfterNone = page.getEditHideText();

        namedAgain = openWith(AutonomyEditorPanel.CAPTIONS_STATIONS);
        hiddenAfterNamedAgain = page.getEditHideText();

        withLabelsOnly = openWith(AutonomyEditorPanel.CAPTIONS_LABELS);
        ownUnderLabelsOnly = lastOwn;
        hiddenAfterLabelsOnly = page.getEditHideText();

        cycled = pressControlLFiveTimes();
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

        // THE SWITCH IS OFF UNDER STATION NAMES NOW, and the captions are drawn anyway (OB-272): the switch is the
        // writing's own, and captions follow the dropdown.  Until 2026-09-23 this asserted the switch was ON here,
        // which was FR-061's coupling - None being that switch turned off.
        assertTrue(hiddenAfterNamedFirst,
            "the diagram's text switch was on under Station Names, so his own writing is drawn beside the captions -"
            + " OB-272: hide the text labels unless that option is selected");

        assertEquals(ownUnderNamedFirst.size(), 0,
            "Station Names drew writing that is not a caption: " + ownUnderNamedFirst);
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

        // The captions came back without the switch (OB-272): it stays off under a caption mode, and the count
        // above is what says None did not latch.
        assertTrue(hiddenAfterNamedAgain,
            "the text switch came on with Station Names, so his own writing is drawn beside the captions (OB-272)");
    }

    /**
     * Labels Only draws the writing on the diagram and no caption (Adam, 2026-09-23, OB-272).
     *
     * *"make text labels be a dedicated setting, and hide the text labels unless it is selected"*, and *"add an
     * option to the dropdown that shows the labels only"*.  The page carries writing of his own - "Inner Loop" and
     * the rest - so this is not satisfied by a page with nothing to show; None, beside it, draws none of it.
     */
    @Test
    public void testLabelsOnlyDrawsTheWritingAndNoCaption()
    {
        assertEquals(withLabelsOnly.size(), 0, "Labels Only drew captions: " + withLabelsOnly);

        assertTrue(ownUnderLabelsOnly.size() > 0,
            "Labels Only drew none of the writing on the diagram, which is the one thing it is for");

        assertFalse(hiddenAfterLabelsOnly, "the text switch is off under Labels Only");

        assertEquals(ownUnderNone.size(), 0, "None drew writing that is not a caption: " + ownUnderNone);
    }

    /**
     * Control+L steps through all five options and wraps (OB-272: *"make control+L cycle the options"* - *"the
     * dropdown's 4, plus ... the labels only"*).  Pressed through the editor's own key handler, so the key and the
     * option are asked together.
     */
    @Test
    public void testControlLStepsThroughTheFiveAndWraps()
    {
        assertEquals(cycled, java.util.Arrays.asList(AutonomyEditorPanel.CAPTIONS_PARKED, AutonomyEditorPanel.CAPTIONS_HOMES,
            AutonomyEditorPanel.CAPTIONS_NONE, AutonomyEditorPanel.CAPTIONS_LABELS, AutonomyEditorPanel.CAPTIONS_STATIONS),
            "Control+L from Station Names did not step Parked, Homes, None, Labels Only and back to Station Names");
    }

    /** The writing that is not a caption, from the last `openWith` */
    private static List<String> lastOwn;

    /** The captions that can be picked up and moved, from the last `openWith` */
    private static List<String> lastMovable;

    private static List<String> movableUnderNamedFirst;

    /**
     * A caption can still be dragged under Station Names, with the text switch off (OB-272).
     *
     * The drag (FR-035) is installed only where the caption is drawn - OB-139 - and asked the TEXT switch whether it
     * was.  That switch is off under every caption mode since OB-272, so reading it would take the drag off every
     * caption the moment the captions and the writing were separated.  Every caption drawn must carry the move cursor
     * the drag puts on it.
     */
    @Test
    public void testACaptionCanStillBeDraggedWithTheTextSwitchOff()
    {
        assertTrue(namedFirst.size() > 0, "precondition: Station Names drew no captions to drag");

        assertEquals(movableUnderNamedFirst.size(), namedFirst.size(),
            "under Station Names only " + movableUnderNamedFirst.size() + " of " + namedFirst.size()
            + " captions can be picked up - the drag is asking the text switch, which OB-272 turned off here");
    }

    private static List<Integer> pressControlLFiveTimes() throws Exception
    {
        prefs().putInt(PREF_CAPTION_MODE, AutonomyEditorPanel.CAPTIONS_STATIONS);

        final LayoutEditor[] built = new LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        settle();

        final List<Integer> out = new ArrayList<>();

        final java.lang.reflect.Method press =
            LayoutEditor.class.getDeclaredMethod("formKeyPressed", java.awt.event.KeyEvent.class);

        press.setAccessible(true);

        for (int i = 0; i < 5; i++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    press.invoke(built[0], new java.awt.event.KeyEvent(built[0], java.awt.event.KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(), java.awt.event.InputEvent.CTRL_DOWN_MASK,
                        java.awt.event.KeyEvent.VK_L, 'L'));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            // READ AFTER THE HANDLER'S OWN POSTED WORK: `formKeyPressed` does everything in an `invokeLater`, so
            // the answer is one turn of the queue behind the press.
            javax.swing.SwingUtilities.invokeAndWait(() -> out.add(built[0].getAutonomyPanel().getCaptionMode()));
        }

        javax.swing.SwingUtilities.invokeAndWait(() -> built[0].dispose());

        return out;
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
        final List<String> own = new ArrayList<>();
        final List<String> movable = new ArrayList<>();

        javax.swing.SwingUtilities.invokeAndWait(() -> collect(built[0].getContentPane(), drawn, own, movable));

        lastOwn = own;
        lastMovable = movable;

        javax.swing.SwingUtilities.invokeAndWait(() -> built[0].dispose());

        return drawn;
    }

    /**
     * Every caption the container holds.
     *
     * `StationCaption` only - the window's own labels are ordinary JLabels, and counting those would
     * make this a test about the sidebar.
     */
    private static void collect(java.awt.Container container, List<String> into, List<String> own,
        List<String> movable)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof StationCaption)
            {
                String text = ((StationCaption) child).getText();

                // A caption is a pill; the user's own writing is not (FR-028) - and the two are counted apart
                // since OB-272 made them two settings.
                if (text != null && !text.trim().isEmpty())
                {
                    if (((StationCaption) child).isPill()) into.add(text);
                    else own.add(text);

                    // The drag's own mark (FR-035): it puts the move cursor on the caption it installs on.
                    if (((StationCaption) child).isPill()
                        && child.getCursor().getType() == java.awt.Cursor.MOVE_CURSOR) movable.add(text);
                }
            }

            if (child instanceof java.awt.Container) collect((java.awt.Container) child, into, own, movable);
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
