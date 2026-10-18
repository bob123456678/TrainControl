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
 * **The dropdown is four options and the Text Labels box is the fifth state's home (FR-133).** Adam:
 * *"move the "labels only" setting currently in the dropdown to a Text Labels checkbox below Show Trains."*
 * The box shows the writing on the diagram, the dropdown only decides the station captions, and a remembered
 * 4 (the old Labels Only) opens as Hide with the box ticked.
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

    /** A remembered 4, the old Labels Only (FR-133): what it drew, and how the controls came up */
    private static List<String> withOldLabelsOnly;
    private static List<String> ownUnderOldLabelsOnly;
    private static boolean hiddenAfterOldLabelsOnly;
    private static Opening oldLabelsOnly;

    /** The Station Names openings with the box unticked and ticked (FR-133) */
    private static Opening boxOff;
    private static Opening boxOn;
    private static List<String> namedWithBoxOn;
    private static List<String> ownWithBoxOn;

    /** What the controls looked like at an opening: the dropdown, the box, and where the box sits */
    private static final class Opening
    {
        int mode;
        int items;
        String lastItem;
        boolean box;
        boolean boxBelowTrains;
        boolean boxIsShown;
    }

    private static Opening lastOpening;

    /** Clicking the box on a Station Names editor (FR-133) */
    private static Clicked clicked;

    private static final class Clicked
    {
        int modeBefore;
        int modeAfter;
        boolean hiddenBefore;
        boolean hiddenAfter;
        int captionsBefore;
        int captionsAfter;
        int ownBefore;
        int ownAfter;
        boolean boxAfterClick;
        boolean prefAfterClick;
        boolean boxAfterTheDiagramSwitch;
        boolean hiddenAfterTheDiagramSwitch;
        int modeAfterTheDiagramSwitch;
    }

    /** The caption option after each press of Control+L, starting from Station Names */
    private static List<Integer> cycled;

    /** And the box and the diagram's text switch after each press, with the box ticked to begin with (FR-133) */
    private static List<Boolean> boxWhileCycling;
    private static List<Boolean> hiddenWhileCycling;

    /** What the preference said before this class touched it */
    private static int captionModeWas;

    /** The box's preference before this class touched it - null when it was never stored */
    private static String textWas;

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

    private static final String PREF_TEXT_LABELS = "autonomyEditorTextLabels";

    /** What an earlier version remembered for Labels Only, which was the fifth option (OB-272) */
    private static final int OLD_LABELS_ONLY = 4;

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
        textWas = prefs().get(PREF_TEXT_LABELS, null);

        namedFirst = openWith(AutonomyEditorPanel.CAPTIONS_STATIONS);
        ownUnderNamedFirst = lastOwn;
        movableUnderNamedFirst = lastMovable;
        boxOff = lastOpening;
        hiddenAfterNamedFirst = page.getEditHideText();

        withNone = openWith(AutonomyEditorPanel.CAPTIONS_NONE);
        ownUnderNone = lastOwn;
        hiddenAfterNone = page.getEditHideText();

        namedAgain = openWith(AutonomyEditorPanel.CAPTIONS_STATIONS);
        hiddenAfterNamedAgain = page.getEditHideText();

        withOldLabelsOnly = openWith(OLD_LABELS_ONLY);
        ownUnderOldLabelsOnly = lastOwn;
        hiddenAfterOldLabelsOnly = page.getEditHideText();
        oldLabelsOnly = lastOpening;

        namedWithBoxOn = openWith(AutonomyEditorPanel.CAPTIONS_STATIONS, true);
        ownWithBoxOn = lastOwn;
        boxOn = lastOpening;

        clicked = clickTheBox();

        pressControlLFiveTimes();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            prefs().putInt(PREF_CAPTION_MODE, captionModeWas);

            if (textWas == null) prefs().remove(PREF_TEXT_LABELS);
            else prefs().put(PREF_TEXT_LABELS, textWas);

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
     * A remembered 4, which was Labels Only, opens as Hide with the Text Labels box ticked (FR-133).
     *
     * The dropdown lost its fifth option and the box took its job, so somebody who had Labels Only keeps what they saw:
     * the writing on the diagram and no caption.  The page carries writing of his own - "Inner Loop" and the rest - so
     * this is not satisfied by a page with nothing to show; None, beside it, draws none of it.
     */
    @Test
    public void testARememberedFourOpensAsHideWithTheBoxTicked()
    {
        assertEquals(oldLabelsOnly.mode, AutonomyEditorPanel.CAPTIONS_NONE,
            "a remembered 4 did not open as Hide, the dropdown's last option");

        assertTrue(oldLabelsOnly.box, "a remembered 4 did not tick the Text Labels box");

        assertEquals(withOldLabelsOnly.size(), 0, "a remembered 4 drew captions: " + withOldLabelsOnly);

        assertTrue(ownUnderOldLabelsOnly.size() > 0,
            "a remembered 4 drew none of the writing on the diagram, which is the one thing it was for");

        assertFalse(hiddenAfterOldLabelsOnly, "the diagram's text switch is off after a remembered 4");

        assertEquals(ownUnderNone.size(), 0, "Hide drew writing that is not a caption: " + ownUnderNone);
    }

    /**
     * The dropdown is Station Names, Standing Locs, Home Locs and Hide - exactly four, Hide last (FR-133).
     */
    @Test
    public void testTheDropdownIsFourOptionsWithHideLast()
    {
        assertEquals(boxOff.items, 4, "the caption dropdown does not have exactly four options");

        assertEquals(boxOff.lastItem, org.traincontrol.util.I18n.t("autosetup.ui.captionsNone"),
            "the dropdown's last option is not the Hide one");

        assertEquals(boxOff.lastItem, "Hide", "the last option does not read Hide");
    }

    /**
     * The Text Labels box sits directly below Show Trains (FR-133: *"a Text Labels checkbox below Show Trains"*).
     */
    @Test
    public void testTheBoxSitsBelowShowTrains()
    {
        assertTrue(boxOff.boxIsShown, "the Text Labels box is not in the window");

        assertTrue(boxOff.boxBelowTrains, "the Text Labels box is not directly below Show Trains");
    }

    /**
     * The box is remembered, and it is independent of the dropdown (FR-133): with Station Names chosen, a ticked box
     * draws the writing as well as the captions, and an unticked one draws the captions alone.
     */
    @Test
    public void testTheBoxIsRememberedAndIndependentOfTheDropdown()
    {
        assertFalse(boxOff.box, "the box came up ticked with nothing remembered, so the text is drawn by default");

        assertTrue(boxOn.box, "a remembered tick did not open the box ticked");

        assertEquals(boxOn.mode, AutonomyEditorPanel.CAPTIONS_STATIONS, "the box changed the dropdown at open");

        assertTrue(ownWithBoxOn.size() > 0, "the ticked box drew none of the writing on the diagram");

        assertEquals(ownUnderNamedFirst.size(), 0, "the unticked box drew writing: " + ownUnderNamedFirst);

        assertEquals(namedWithBoxOn.size(), namedFirst.size(),
            "ticking the box changed the station captions drawn: " + namedWithBoxOn + " against " + namedFirst);
    }

    /**
     * Clicking the box shows and hides the diagram's text and leaves the dropdown alone (FR-133), and the diagram's own
     * text switch moving ticks and unticks the box without touching the dropdown either.
     */
    @Test
    public void testClickingTheBoxTogglesTheTextAndNotTheDropdown()
    {
        assertTrue(clicked.hiddenBefore, "precondition: the text was hidden before the click");
        assertFalse(clicked.hiddenAfter, "clicking the box did not show the diagram's text");
        assertTrue(clicked.boxAfterClick, "the box is not ticked after the click");
        assertTrue(clicked.prefAfterClick, "the tick was not remembered");

        assertEquals(clicked.ownBefore, 0, "precondition: no writing was drawn before the click");
        assertTrue(clicked.ownAfter > 0, "no writing was drawn after the click");

        assertEquals(clicked.modeAfter, clicked.modeBefore, "clicking the box changed the dropdown");
        assertEquals(clicked.captionsAfter, clicked.captionsBefore, "clicking the box changed the station captions");

        assertTrue(clicked.hiddenAfterTheDiagramSwitch, "precondition: the diagram's text switch went off");
        assertFalse(clicked.boxAfterTheDiagramSwitch, "the diagram's text switch went off and the box stayed ticked");
        assertEquals(clicked.modeAfterTheDiagramSwitch, clicked.modeBefore,
            "the diagram's text switch moved the dropdown");
    }

    /**
     * Control+L steps the dropdown's four options and wraps, and never touches the box (FR-133; OB-272 had it step the
     * dropdown's options plus Labels Only).  Pressed through the editor's own key handler, so the key and the option are
     * asked together.
     */
    @Test
    public void testControlLStepsThroughTheFourAndWraps()
    {
        assertEquals(cycled, java.util.Arrays.asList(AutonomyEditorPanel.CAPTIONS_PARKED, AutonomyEditorPanel.CAPTIONS_HOMES,
            AutonomyEditorPanel.CAPTIONS_NONE, AutonomyEditorPanel.CAPTIONS_STATIONS, AutonomyEditorPanel.CAPTIONS_PARKED),
            "Control+L from Station Names did not step Parked, Homes, Hide and back to Station Names");

        assertEquals(boxWhileCycling, java.util.Arrays.asList(true, true, true, true, true),
            "Control+L unticked the Text Labels box");

        assertEquals(hiddenWhileCycling, java.util.Arrays.asList(false, false, false, false, false),
            "Control+L moved the diagram's text switch");
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

    private static void pressControlLFiveTimes() throws Exception
    {
        prefs().putInt(PREF_CAPTION_MODE, AutonomyEditorPanel.CAPTIONS_STATIONS);
        prefs().putBoolean(PREF_TEXT_LABELS, true);

        final LayoutEditor[] built = new LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        settle();

        final List<Integer> out = new ArrayList<>();
        final List<Boolean> boxes = new ArrayList<>();
        final List<Boolean> hidden = new ArrayList<>();

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
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                out.add(built[0].getAutonomyPanel().getCaptionMode());
                boxes.add(built[0].getAutonomyPanel().getShowTextLabels().isSelected());
                hidden.add(page.getEditHideText());
            });
        }

        javax.swing.SwingUtilities.invokeAndWait(() -> built[0].dispose());

        cycled = out;
        boxWhileCycling = boxes;
        hiddenWhileCycling = hidden;
    }

    /**
     * Opens Station Names with the box unticked, clicks the box, then moves the diagram's own text switch back.
     */
    private static Clicked clickTheBox() throws Exception
    {
        prefs().putInt(PREF_CAPTION_MODE, AutonomyEditorPanel.CAPTIONS_STATIONS);
        prefs().putBoolean(PREF_TEXT_LABELS, false);

        final LayoutEditor[] built = new LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        settle();

        final Clicked out = new Clicked();
        final AutonomyEditorPanel panel = built[0].getAutonomyPanel();

        out.modeBefore = panel.getCaptionMode();
        out.hiddenBefore = page.getEditHideText();
        out.captionsBefore = drawnNow(built[0], true);
        out.ownBefore = drawnNow(built[0], false);

        javax.swing.SwingUtilities.invokeAndWait(() -> panel.getShowTextLabels().doClick());

        settle();

        out.modeAfter = panel.getCaptionMode();
        out.hiddenAfter = page.getEditHideText();
        out.captionsAfter = drawnNow(built[0], true);
        out.ownAfter = drawnNow(built[0], false);
        out.boxAfterClick = panel.getShowTextLabels().isSelected();
        out.prefAfterClick = prefs().getBoolean(PREF_TEXT_LABELS, false);

        // The diagram's own switch (the plain editor's checkbox, Control+L there): off again
        javax.swing.SwingUtilities.invokeAndWait(() -> built[0].toggleText());

        settle();

        out.hiddenAfterTheDiagramSwitch = page.getEditHideText();
        out.boxAfterTheDiagramSwitch = panel.getShowTextLabels().isSelected();
        out.modeAfterTheDiagramSwitch = panel.getCaptionMode();

        javax.swing.SwingUtilities.invokeAndWait(() -> built[0].dispose());

        return out;
    }

    private static int drawnNow(LayoutEditor editor, boolean captions) throws Exception
    {
        final List<String> drawn = new ArrayList<>();
        final List<String> own = new ArrayList<>();

        javax.swing.SwingUtilities.invokeAndWait(() -> collect(editor.getContentPane(), drawn, own, new ArrayList<>()));

        return captions ? drawn.size() : own.size();
    }

    /**
     * Opens the editor in autonomy mode with this caption mode REMEMBERED, and reads what it drew.
     */
    private static List<String> openWith(int mode) throws Exception
    {
        return openWith(mode, false);
    }

    private static List<String> openWith(int mode, boolean text) throws Exception
    {
        prefs().putInt(PREF_CAPTION_MODE, mode);
        prefs().putBoolean(PREF_TEXT_LABELS, text);

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

        final Opening opening = new Opening();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            collect(built[0].getContentPane(), drawn, own, movable);

            AutonomyEditorPanel panel = built[0].getAutonomyPanel();

            opening.mode = panel.getCaptionMode();
            opening.items = panel.getCaptionChoice().getItemCount();
            opening.lastItem = panel.getCaptionChoice().getItemAt(opening.items - 1);
            opening.box = panel.getShowTextLabels().isSelected();

            java.awt.Container column = panel.getShowTrains().getParent();
            java.awt.Component[] kids = column == null ? new java.awt.Component[0] : column.getComponents();

            // THE NEXT CONTROL BELOW, past the gap every checkbox there has above it (OB-312)
            for (int i = 0; i + 1 < kids.length; i++)
            {
                if (kids[i] != panel.getShowTrains()) continue;

                int next = i + 1;

                while (next < kids.length && kids[next] instanceof javax.swing.Box.Filler) next++;

                opening.boxBelowTrains = next < kids.length && kids[next] == panel.getShowTextLabels();
            }

            opening.boxIsShown = panel.getShowTextLabels().getParent() != null;
        });

        lastOpening = opening;
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
