package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor says what its items do, and shows where its tools can be used (Adam, 2026-09-24: OB-293, FR-102).
 *
 * @author Adam
 */
public class testTheEditorSaysWhatItsToolsDo
{
    private File folder;
    private AutonomySession session;

    private static final TileKey WITH_A_TRAIN = new TileKey("main", 3, 1);
    private static final TileKey EMPTY = new TileKey("main", 1, 1);

    @BeforeMethod
    public void setUp() throws IOException
    {
        folder = Files.createTempDirectory("tc-editor-tools").toFile();
        session = new AutonomySession(folder);

        // 1,1 station - 2,1 - 3,1 station, a train standing at 3,1.
        LayoutDiagram page = new LayoutDiagram("main", 6, 3, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Tools");
        session.setStation(EMPTY, true);
        session.setStation(WITH_A_TRAIN, true);
        session.placeLocomotive(WITH_A_TRAIN, "FR-102 train");
        session.rebuild();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown()
    {
        delete(folder);
    }

    /**
     * The exit and entry guard items on a station's menu say what the two guards do (OB-293).
     *
     * Adam, 2026-09-24: *"add brief tooltips on what entry guards and exit guards are to their items in the autonomy
     * right click menu"*.
     *
     * Each its OWN guard's text (TDD2-C15): the two items sit next to each other and read alike, and a tooltip on the
     * wrong one tells Adam the exit guard does what the entry guard does.
     *
     * MUTATION: leave either tooltip out, or give the two items each other's, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheGuardItemsSayWhatTheGuardsDo() throws Exception
    {
        // "OCCUPIED", NOT "TAKEN" (Adam, on MT-569: *"station is taken" -> station is occupied*)
        assertTrue(I18n.t("autosetup.ui.tooltipExitGuard").contains("while this station is occupied")
            && !I18n.t("autosetup.ui.tooltipExitGuard").contains("taken"), "the exit guard's tooltip does not say"
            + " \"occupied\" (MT-569): " + I18n.t("autosetup.ui.tooltipExitGuard"));

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            menu[0] = panel.buildTileMenu(EMPTY, session.getGraph().getTiles().get(EMPTY)));

        String[][] items = {{"autosetup.ui.menuPairSignal", "autosetup.ui.tooltipExitGuard"},
            {"autosetup.ui.menuPairEntrySignal", "autosetup.ui.tooltipEntryGuard"}};

        for (String[] pair : items)
        {
            String key = pair[0];

            javax.swing.JMenuItem item = find(menu[0], I18n.t(key));

            assertNotNull(item, "precondition: a station's menu has no " + I18n.t(key));

            String tip = item.getToolTipText();

            assertTrue(tip != null && !tip.replaceAll("<[^>]*>", "").trim().isEmpty(), I18n.t(key) + " has no tooltip -"
                + " Adam, OB-293: \"add brief tooltips on what entry guards and exit guards are\"");

            // ITS OWN GUARD'S TEXT (TDD2-C15), read past the wrapping.
            String said = tip.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
            String own = I18n.t(pair[1]).replaceAll("\\s+", " ").trim();

            assertTrue(said.startsWith(own.substring(0, Math.min(40, own.length()))), I18n.t(key) + " says what the"
                + " other guard does, or something else: " + said);
        }
    }

    /**
     * With Why Not Moving armed and nothing drawn yet, the squares with trains are outlined (FR-102).
     *
     * Adam, 2026-09-24: *"when the button is pressed an nothing is drawn yet, highlight stations w/ trains on the editor
     * diagram so it's clear what the user can click on"*.
     *
     * MUTATION: outline nothing while the tool waits, or every station, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testWhyNotMovingOutlinesTheTrains() throws Exception
    {
        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        assertFalse(panel.annotationFor(WITH_A_TRAIN).isSelected(), "precondition: the train's square is outlined before"
            + " the tool is armed");

        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("whyButton");

        field.setAccessible(true);

        final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field.get(panel);

        javax.swing.SwingUtilities.invokeAndWait(() -> why.doClick());

        assertTrue(why.isSelected(), "precondition: Why Not Moving did not arm");

        assertTrue(panel.annotationFor(WITH_A_TRAIN).isSelected(), "Why Not Moving is waiting for a click and the square"
            + " with a train on it is not outlined - Adam, FR-102: \"highlight stations w/ trains on the editor diagram so"
            + " it's clear what the user can click on\"");

        assertFalse(panel.annotationFor(EMPTY).isSelected(), "a station with no train on it is outlined, and there is"
            + " nothing there to ask about");
    }

    /**
     * Where a railway is running, the outline follows the trains on it, not the setup's placements - and once a square
     * is clicked the outlines go (FR-102; TDU-C11, TDD-C9).
     *
     * The javadoc of `whyWaitsOn`: *"The train as the running railway has it where there is one - a run moves trains the
     * setup has not been told about - and as the setup places it otherwise."*  The claim above builds the panel with no
     * railway, so only the second arm was asked.
     *
     * MUTATION: read the setup's placements while a railway runs, or keep the outlines after the click, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testWhyNotMovingFollowsTheRunningRailway() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        org.traincontrol.marklin.MarklinControlStation model = null;

        try
        {
            // A SANDBOX FIRST, so the model does not load the machine's own railway (OB-111).
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            org.traincontrol.base.Locomotive moved = model.newMM2Locomotive("FR-102 moved", 2301);

            assertNotNull(moved, "could not create this test's train");

            model.parseAuto(session.buildConfiguration());

            final org.traincontrol.automation.Layout railway = model.getAutoLayout();

            assertNotNull(railway, "precondition: the fixture built no railway");

            // ON THE RAILWAY THE TRAIN STANDS AT 1,1, where the setup has none; the setup's train at 3,1 is not there.
            for (org.traincontrol.automation.Point point : railway.getPoints()) point.setLocomotive(null);

            org.traincontrol.automation.Point atEmpty = null;

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (EMPTY.equals(session.getStationIndex().squareOf(point))) atEmpty = point;
            }

            assertNotNull(atEmpty, "precondition: the railway has no Point at 1,1");

            atEmpty.setLocomotive(moved);

            final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            panel.setRunningLayoutSource(() -> railway);
            panel.setLayoutSource(() -> railway);

            java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("whyButton");

            field.setAccessible(true);

            final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field.get(panel);

            javax.swing.SwingUtilities.invokeAndWait(() -> why.doClick());

            assertTrue(panel.annotationFor(EMPTY).isSelected(), "a railway is running with a train at 1,1 and Why Not"
                + " Moving does not outline it - it read the setup, which has none there");

            assertFalse(panel.annotationFor(WITH_A_TRAIN).isSelected(), "a railway is running with no train at 3,1 and"
                + " Why Not Moving outlines it - it read the setup's placement, not the railway");

            // AND ONCE A SQUARE IS CLICKED, THE OUTLINES GO: a second train on the railway at 3,1 is outlined while the
            // tool waits, and not once 1,1 has been asked about.
            org.traincontrol.base.Locomotive second = model.newMM2Locomotive("FR-102 second", 2302);

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (WITH_A_TRAIN.equals(session.getStationIndex().squareOf(point))) point.setLocomotive(second);
            }

            // Asked of the waiting outline itself: once a square is asked about, the answer draws outlines of its own.
            java.lang.reflect.Method waitsOn = AutonomyEditorPanel.class.getDeclaredMethod("whyWaitsOn", TileKey.class);

            waitsOn.setAccessible(true);

            assertTrue((Boolean) waitsOn.invoke(panel, WITH_A_TRAIN), "precondition: the second train at 3,1 is not"
                + " outlined as somewhere to click while Why Not Moving waits");

            javax.swing.SwingUtilities.invokeAndWait(() ->
                panel.tileClicked(EMPTY, session.getGraph().getTiles().get(EMPTY), false));

            assertFalse((Boolean) waitsOn.invoke(panel, WITH_A_TRAIN), "Why Not Moving was asked about 1,1 and still"
                + " outlines the train at 3,1 as somewhere to click");
        }
        finally
        {
            try
            {
                if (model != null)
                {
                    model.deleteLoc("FR-102 moved");
                    model.deleteLoc("FR-102 second");
                }
            }
            finally
            {
                if (sandbox != null) sandbox.close();
            }
        }
    }

    /** The menu item with this text, anywhere in the menu. */
    private static javax.swing.JMenuItem find(java.awt.Container container, String text)
    {
        for (java.awt.Component c : container instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) container).getMenuComponents() : container.getComponents())
        {
            if (c instanceof javax.swing.JMenuItem && text.equals(((javax.swing.JMenuItem) c).getText()))
            {
                return (javax.swing.JMenuItem) c;
            }

            if (c instanceof java.awt.Container)
            {
                javax.swing.JMenuItem inner = find((java.awt.Container) c, text);

                if (inner != null) return inner;
            }
        }

        return null;
    }

    /**
     * On the frozen railway, a station's exit guard chosen as its entry guard is refused, with the sentence saying the
     * signal is already the exit guard and one signal cannot guard both the way in and the way out (MT-504; Adam,
     * 2026-09-25: automated tests supersede the MTs they answer).
     *
     * Both guards are given as the steps give them: the station's own menu item, the window it opens, Click It on the
     * Diagram, and a click on the signal.  The exit guard first, because the frozen railway has none and step 1 starts
     * from a station that has one.  `core.testAutonomyDiagramSession.testAStationsEntryGuardIsNeverItsExitGuard` holds the
     * store's own refusal below this.
     *
     * MUTATION: take the editor's refusal out, or have it say the other guard's sentence, and this fails.
     *
     * @throws Exception from the sandbox and the event thread
     */
    @Test
    public void testHisExitGuardIsRefusedAsTheEntryGuard() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the guard window needs a display");

        support.LayoutSandbox sandbox = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            org.traincontrol.marklin.MarklinControlStation model =
                org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            try
            {
                AutonomySession his = new AutonomySession(sandbox.getFolder());

                his.open(support.LayoutSandbox.wiredPages(model));

                // STEP 1'S STATION: one of his with an exit guard - or, where none has, one given an exit guard through
                // Exit Guard Signal..., as a person gives one.
                TileKey[] pair = aStationWithAnExitGuard(his);

                final boolean hisOwn = pair != null;

                if (pair == null) pair = aStationAndASignalBesideIt(his);

                assertNotNull(pair, "precondition: no page of the frozen railway has both a station and a signal");

                final TileKey station = pair[0];
                final TileKey signal = pair[1];

                final AutonomyEditorPanel panel = new AutonomyEditorPanel(his, station.getPage(), () -> { });

                if (!hisOwn) pickTheGuard(panel, his, station, signal, false, new java.util.ArrayList<>());

                final java.util.List<TileKey> exitGuards = new java.util.ArrayList<>(his.getProtectingSignals(station));

                final java.util.List<TileKey> entryGuards = new java.util.ArrayList<>(his.getEntrySignals(station));

                assertTrue(exitGuards.contains(signal) && !entryGuards.contains(signal), "precondition: " + signal + " is"
                    + " not the exit guard of " + station + " alone: exit " + exitGuards + ", entry " + entryGuards);

                // STEP 2: Entry Guard Signal..., and the same signal.
                final java.util.List<String> popups = new java.util.ArrayList<>();

                String said = pickTheGuard(panel, his, station, signal, true, popups);

                assertEquals(said, I18n.f("autosetup.ui.signalIsTheExitGuard", privately(panel, "addressOf", signal),
                    privately(panel, "describeTile", station)), "choosing the exit guard as the entry guard is not refused"
                    + " with the sentence saying it is already the exit guard (MT-504)");

                // AND IN A POPUP, in front of the guard window that comes back (MT-505)
                assertEquals(popups, Arrays.asList(plain(said)), "the refusal is not said in a popup (MT-505)");

                assertEquals(his.getEntrySignals(station), entryGuards, "the exit guard was made the entry guard as well"
                    + " (MT-504)");

                assertEquals(his.getProtectingSignals(station), exitGuards, "the refusal changed the exit guards");
            }
            finally
            {
                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A signal refused as a guard is refused in a popup, not only on the grey line (MT-505).
     *
     * Adam, 2026-09-29, on MT-505: *"There may have been a gray message, but it wouldn't have been clear since the popup
     * was in the foreground, so any rejection errors should trigger a popup."*  The guard window comes back the moment the
     * click is answered and sits in front of the line that said why nothing was added; his Signal 86 "doesn't get added to
     * the list in the popup after clicking on it on the diagram", and nothing he could see said why.
     *
     * Both refusals a click can meet: a signal already on the list, and the station's other guard (AUT-C2).
     *
     * MUTATION: say either refusal on the grey line alone, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testARefusedGuardClickIsAPopup() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the guard window needs a display");

        final AutonomySession his = withTwoSignals();
        final AutonomyEditorPanel panel = new AutonomyEditorPanel(his, "main", () -> { });

        try
        {
            java.util.List<String> popups = new java.util.ArrayList<>();

            pickTheGuard(panel, his, SIGNALLED, SIGNAL_86, false, popups);

            assertTrue(popups.isEmpty(), "precondition: pairing a signal nothing refuses raised a popup: " + popups);
            assertEquals(his.getProtectingSignals(SIGNALLED), Arrays.asList(SIGNAL_86), "precondition: the exit guard was"
                + " not paired");

            // ALREADY ON THE LIST
            pickTheGuard(panel, his, SIGNALLED, SIGNAL_86, false, popups);

            assertEquals(popups, Arrays.asList(plain(I18n.f("autosetup.ui.signalAlreadyPaired",
                privately(panel, "addressOf", SIGNAL_86)))), "a signal already on the exit guard list is not refused in a"
                + " popup (MT-505)");

            assertEquals(his.getProtectingSignals(SIGNALLED), Arrays.asList(SIGNAL_86), "the refusal changed the list");

            // THE STATION'S OTHER GUARD (AUT-C2)
            popups.clear();

            pickTheGuard(panel, his, SIGNALLED, SIGNAL_86, true, popups);

            assertEquals(popups, Arrays.asList(plain(I18n.f("autosetup.ui.signalIsTheExitGuard",
                privately(panel, "addressOf", SIGNAL_86), privately(panel, "describeTile", SIGNALLED)))), "the station's"
                + " exit guard, clicked as its entry guard, is not refused in a popup (MT-505)");

            assertTrue(his.getEntrySignals(SIGNALLED).isEmpty(), "the exit guard was made the entry guard as well");
        }
        finally
        {
            closeEveryDialog();
        }
    }

    /**
     * A click on a square that is not a signal, while a guard is being chosen, is refused in a popup (MT-505).
     *
     * The same words as ever, and the choice stays armed: the next click on a signal still pairs it.
     *
     * MUTATION: say it on the grey line alone, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testANonSignalClickedAsAGuardIsAPopup() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the guard window needs a display");

        final AutonomySession his = withTwoSignals();
        final AutonomyEditorPanel panel = new AutonomyEditorPanel(his, "main", () -> { });

        try
        {
            final String title = I18n.t("autosetup.ui.menuPairEntrySignal");

            javax.swing.JDialog window = openTheGuardWindow(panel, SIGNALLED, title, title);

            clickOnTheDiagram(window, title);

            // THE STATION ITSELF, which is no signal
            final org.traincontrol.base.LayoutDiagramComponent station = his.getGraph().getTiles().get(SIGNALLED);

            javax.swing.SwingUtilities.invokeLater(() -> panel.tileClicked(SIGNALLED, station, false));

            String said = awaitAPopup(null, 5000);

            assertEquals(said, plain(I18n.t("autosetup.ui.errorNotASignal")), "a square that is not a signal, clicked as"
                + " the entry guard, is not refused in a popup (MT-505)");

            // STILL ARMED: the next click on a signal pairs it, and the window comes back
            final org.traincontrol.base.LayoutDiagramComponent signal = his.getGraph().getTiles().get(SIGNAL_87);

            javax.swing.SwingUtilities.invokeLater(() -> panel.tileClicked(SIGNAL_87, signal, false));

            javax.swing.JDialog back = awaitWindowTitled(title, window);

            assertEquals(his.getEntrySignals(SIGNALLED), Arrays.asList(SIGNAL_87), "after the refusal, a click on a signal"
                + " no longer pairs it");

            javax.swing.SwingUtilities.invokeAndWait(buttonIn(back, I18n.t("autosetup.ui.optionSignalsDone"))::doClick);
        }
        finally
        {
            closeEveryDialog();
        }
    }

    /**
     * Signals refused when given by address are refused in the one popup that reports the addresses (MT-505).
     *
     * By Address already raised a popup for an address that is no number or no signal; a signal it found and could not
     * pair was said on the grey line alone, behind the guard window.  Now every refusal is in that one popup, however many
     * addresses were typed - a popup per address would be a row of them to dismiss.
     *
     * MUTATION: leave the refusals out of that popup, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testARefusedGuardAddressIsAPopup() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the guard window needs a display");

        final AutonomySession his = withTwoSignals();

        his.setProtectingSignals(SIGNALLED, Arrays.asList(SIGNAL_86));
        his.setEntrySignals(SIGNALLED, Arrays.asList(SIGNAL_87));

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(his, "main", () -> { });

        try
        {
            final String title = I18n.t("autosetup.ui.menuPairSignal");

            javax.swing.JDialog window = openTheGuardWindow(panel, SIGNALLED, labelOf(panel, his, SIGNALLED, false), title);

            final javax.swing.AbstractButton byAddress = buttonIn(window, I18n.t("autosetup.ui.optionSignalAddress"));

            assertNotNull(byAddress, "the " + title + " window has no By Address button");

            javax.swing.SwingUtilities.invokeLater(byAddress::doClick);

            // THE QUESTION: both addresses, one already on the list and one the station's entry guard
            final javax.swing.JOptionPane asked = awaitAnOptionPane(null, 10000);

            assertNotNull(asked, "By Address asked for no address");

            final String typed = privately(panel, "addressOf", SIGNAL_86) + ", " + privately(panel, "addressOf", SIGNAL_87);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                textFieldIn(asked).setText(typed);

                buttonIn(asked, javax.swing.UIManager.getString("OptionPane.okButtonText")).doClick();
            });

            String said = awaitAPopup(asked, 5000);

            assertNotNull(said, "two signals refused by address raised no popup (MT-505)");

            assertTrue(said.contains(plain(I18n.f("autosetup.ui.signalAlreadyPaired", privately(panel, "addressOf",
                SIGNAL_86)))) && said.contains(plain(I18n.f("autosetup.ui.signalIsTheEntryGuard", privately(panel,
                "addressOf", SIGNAL_87), privately(panel, "describeTile", SIGNALLED)))), "the popup does not say why each"
                + " signal was refused (MT-505): " + said);

            assertEquals(his.getProtectingSignals(SIGNALLED), Arrays.asList(SIGNAL_86), "the refusals changed the list");
        }
        finally
        {
            closeEveryDialog();
        }
    }

    private static final TileKey SIGNALLED = new TileKey("main", 1, 1);
    private static final TileKey SIGNAL_86 = new TileKey("main", 2, 0);
    private static final TileKey SIGNAL_87 = new TileKey("main", 3, 0);

    /** A page of its own: a station at 1,1 on a line to 3,1, and signals 86 and 87 beside it, both addressed. */
    private AutonomySession withTwoSignals() throws Exception
    {
        File own = new File(folder, "signals");

        assertTrue(own.mkdirs(), "precondition: could not make " + own);

        AutonomySession his = new AutonomySession(own);

        LayoutDiagram page = new LayoutDiagram("main", 6, 3, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SIGNAL, 2, 0, 0, 0, 86, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.SIGNAL, 3, 0, 0, 0, 87, 0, accessoryDecoderType.MM2, null);

        // Wired, as parsing a real layout does: a signal with no accessory is not one a guard can be
        for (int x : new int[] {2, 3})
        {
            int address = page.getComponent(x, 0).getAddress();

            page.getComponent(x, 0).setAccessory(new org.traincontrol.marklin.MarklinAccessory(null, address,
                org.traincontrol.base.Accessory.accessoryType.SIGNAL, accessoryDecoderType.MM2, "Signal " + address, false,
                0));
        }

        page.setPageId("1");

        his.open(Arrays.asList(page));
        his.initialize("Signals");
        his.setStation(SIGNALLED, true);
        his.rebuild();

        return his;
    }

    /** The station's guard item as its menu labels it now, with what is paired so far. */
    private static String labelOf(AutonomyEditorPanel panel, AutonomySession his, TileKey station, boolean entry)
        throws Exception
    {
        java.util.List<TileKey> paired = entry ? his.getEntrySignals(station) : his.getProtectingSignals(station);

        if (paired.isEmpty()) return I18n.t(entry ? "autosetup.ui.menuPairEntrySignal" : "autosetup.ui.menuPairSignal");

        return I18n.f(paired.size() == 1
            ? (entry ? "autosetup.ui.menuPairedEntrySignal" : "autosetup.ui.menuPairedSignal")
            : (entry ? "autosetup.ui.menuPairedEntrySignals" : "autosetup.ui.menuPairedSignals"),
            addressesOf(panel, paired));
    }

    /** The station's menu item labelled so, clicked, and the guard window titled so that it opens. */
    private static javax.swing.JDialog openTheGuardWindow(final AutonomyEditorPanel panel, final TileKey station,
        final String label, String title) throws Exception
    {
        final javax.swing.JMenuItem[] item = new javax.swing.JMenuItem[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> item[0] = itemNamed(panel.buildTileMenu(station, null), label));

        assertNotNull(item[0], "the station's menu has no " + label + " item");

        javax.swing.SwingUtilities.invokeLater(item[0]::doClick);

        return awaitWindowTitled(title, null);
    }

    /** Click It on the Diagram, and the window gone to let the click through. */
    private static void clickOnTheDiagram(javax.swing.JDialog window, String title) throws Exception
    {
        final javax.swing.AbstractButton byClick = buttonIn(window, I18n.t("autosetup.ui.optionClickSignal"));

        assertNotNull(byClick, "the " + title + " window has no Click It on the Diagram button");

        javax.swing.SwingUtilities.invokeAndWait(byClick::doClick);

        awaitNoWindowTitled(title);

        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * The guard window titled so, back again - with every popup raised on the way read and dismissed into `popups`.
     */
    private static javax.swing.JDialog awaitWindowTitledDismissing(String title, javax.swing.JDialog notThisOne,
        java.util.List<String> popups) throws Exception
    {
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            javax.swing.JOptionPane popup = awaitAnOptionPane(null, 0);

            if (popup != null)
            {
                popups.add(dismiss(popup));

                continue;
            }

            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window != notThisOne && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle())
                    && optionPaneIn((javax.swing.JDialog) window) == null) return (javax.swing.JDialog) window;
            }

            Thread.sleep(50);
        }

        fail("no window titled \"" + title + "\" appeared");

        return null;
    }

    /** What the next popup says, once dismissed - or null if none shows within the time. */
    private static String awaitAPopup(javax.swing.JOptionPane notThisOne, long millis) throws Exception
    {
        javax.swing.JOptionPane popup = awaitAnOptionPane(notThisOne, millis);

        return popup == null ? null : dismiss(popup);
    }

    /** A showing window's option pane - a message or a question - other than the one given, within the time, or null. */
    private static javax.swing.JOptionPane awaitAnOptionPane(javax.swing.JOptionPane notThisOne, long millis)
        throws Exception
    {
        long giveUp = System.currentTimeMillis() + millis;

        do
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                javax.swing.JOptionPane pane = optionPaneIn((javax.swing.JDialog) window);

                if (pane != null && pane != notThisOne) return pane;
            }

            if (millis > 0) Thread.sleep(50);
        }
        while (System.currentTimeMillis() < giveUp);

        return null;
    }

    private static javax.swing.JOptionPane optionPaneIn(java.awt.Container container)
    {
        for (java.awt.Component child : container instanceof javax.swing.JDialog
            ? ((javax.swing.JDialog) container).getContentPane().getComponents() : container.getComponents())
        {
            if (child instanceof javax.swing.JOptionPane) return (javax.swing.JOptionPane) child;

            if (child instanceof java.awt.Container)
            {
                javax.swing.JOptionPane found = optionPaneIn((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static javax.swing.JTextField textFieldIn(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JTextField) return (javax.swing.JTextField) child;

            if (child instanceof java.awt.Container)
            {
                javax.swing.JTextField found = textFieldIn((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    /** A popup's words, as read - and the popup answered, as OK answers it. */
    private static String dismiss(final javax.swing.JOptionPane popup) throws Exception
    {
        String said = plain(String.valueOf(popup.getMessage()));

        javax.swing.SwingUtilities.invokeAndWait(() -> popup.setValue(javax.swing.JOptionPane.OK_OPTION));

        return said;
    }

    /** A sentence as a reader sees it: no markup, and the spacing between sentences as one space. */
    private static String plain(String text)
    {
        return text.replaceAll("<[^>]*>", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ").trim();
    }

    /** Every window a claim left open, closed - so that a red claim leaves nothing modal in front of the next. */
    private static void closeEveryDialog() throws Exception
    {
        for (int round = 0; round < 5; round++)
        {
            boolean any = false;

            for (final java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing())
                {
                    any = true;

                    javax.swing.SwingUtilities.invokeLater(window::dispose);
                }
            }

            if (!any) return;

            Thread.sleep(200);
        }
    }

    /** The first station, by name, with an exit guard, and that guard. */
    private static TileKey[] aStationWithAnExitGuard(AutonomySession his)
    {
        java.util.List<TileKey> stations = new java.util.ArrayList<>();

        for (TileKey key : his.getStore().getNamedTiles())
        {
            if (his.getStore().isStation(key) && !his.getProtectingSignals(key).isEmpty()) stations.add(key);
        }

        stations.sort(java.util.Comparator.comparing(TileKey::toString));

        for (TileKey station : stations)
        {
            for (TileKey signal : his.getProtectingSignals(station))
            {
                if (!his.getEntrySignals(station).contains(signal)) return new TileKey[] {station, signal};
            }
        }

        return null;
    }

    /** The first station, by name, on a page that has a pairable signal - and the first such signal on that page. */
    private static TileKey[] aStationAndASignalBesideIt(AutonomySession his)
    {
        java.util.List<TileKey> stations = new java.util.ArrayList<>();

        for (TileKey key : his.getStore().getNamedTiles())
        {
            if (his.getStore().isStation(key)) stations.add(key);
        }

        stations.sort(java.util.Comparator.comparing(TileKey::toString));

        for (TileKey station : stations)
        {
            TileKey found = null;

            for (java.util.Map.Entry<TileKey, org.traincontrol.base.LayoutDiagramComponent> entry
                : his.getGraph().getTiles().entrySet())
            {
                org.traincontrol.base.LayoutDiagramComponent component = entry.getValue();

                if (!station.getPage().equals(entry.getKey().getPage()) || component == null
                    || component.getType() != componentType.SIGNAL || component.getAccessory() == null) continue;

                if (found == null || entry.getKey().toString().compareTo(found.toString()) < 0) found = entry.getKey();
            }

            if (found != null) return new TileKey[] {station, found};
        }

        return null;
    }

    /**
     * One guard given as a person gives it: the station's menu item, its window, Click It on the Diagram, a click on the
     * signal - and Done on the window that comes back.  Returns what the editor's hint line said once the signal was
     * clicked; every popup raised on the way is read and dismissed into `popups` (MT-505).
     */
    private static String pickTheGuard(final AutonomyEditorPanel panel, AutonomySession his, final TileKey station,
        final TileKey signal, boolean entry, java.util.List<String> popups) throws Exception
    {
        // THE WINDOW'S TITLE is the item's name with nothing paired, whatever the item's label now says.
        final String title = I18n.t(entry ? "autosetup.ui.menuPairEntrySignal" : "autosetup.ui.menuPairSignal");

        javax.swing.JDialog window = openTheGuardWindow(panel, station, labelOf(panel, his, station, entry), title);

        clickOnTheDiagram(window, title);

        final org.traincontrol.base.LayoutDiagramComponent component = his.getGraph().getTiles().get(signal);

        javax.swing.SwingUtilities.invokeLater(() -> panel.tileClicked(signal, component, false));

        // THE WINDOW COMES BACK once the click is answered; the hint line says what the click did.
        javax.swing.JDialog back = awaitWindowTitledDismissing(title, window, popups);

        final String[] said = new String[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> said[0] = hintOf(panel));

        final javax.swing.AbstractButton done = buttonIn(back, I18n.t("autosetup.ui.optionSignalsDone"));

        assertNotNull(done, "the " + title + " window has no Done button");

        javax.swing.SwingUtilities.invokeAndWait(done::doClick);

        awaitNoWindowTitled(title);

        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        return said[0];
    }

    private static javax.swing.JMenuItem itemNamed(java.awt.Container menu, String text)
    {
        java.awt.Component[] children = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component child : children)
        {
            if (child instanceof javax.swing.JMenu)
            {
                javax.swing.JMenuItem found = itemNamed((javax.swing.JMenu) child, text);

                if (found != null) return found;
            }
            else if (child instanceof javax.swing.JMenuItem && text.equals(((javax.swing.JMenuItem) child).getText()))
            {
                return (javax.swing.JMenuItem) child;
            }
        }

        return null;
    }

    private static javax.swing.AbstractButton buttonIn(java.awt.Container container, String text)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.AbstractButton && text.equals(((javax.swing.AbstractButton) child).getText()))
            {
                return (javax.swing.AbstractButton) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.AbstractButton found = buttonIn((java.awt.Container) child, text);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static javax.swing.JDialog awaitWindowTitled(String title, javax.swing.JDialog notThisOne) throws Exception
    {
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window != notThisOne && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle())) return (javax.swing.JDialog) window;
            }

            Thread.sleep(50);
        }

        fail("no window titled \"" + title + "\" appeared");

        return null;
    }

    private static void awaitNoWindowTitled(String title) throws Exception
    {
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            boolean showing = false;

            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle())) showing = true;
            }

            if (!showing) return;

            Thread.sleep(50);
        }

        fail("the window titled \"" + title + "\" did not close");
    }

    private static String hintOf(AutonomyEditorPanel panel)
    {
        try
        {
            java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("hint");

            field.setAccessible(true);

            String text = ((javax.swing.JLabel) field.get(panel)).getText();

            return text == null ? null : text.replaceAll("<[^>]*>", "").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").trim();
        }
        catch (ReflectiveOperationException cannot)
        {
            throw new IllegalStateException(cannot);
        }
    }

    /** The addresses of these signals, as the station's menu lists them in its guard items' labels. */
    private static String addressesOf(AutonomyEditorPanel panel, java.util.List<TileKey> signals) throws Exception
    {
        java.lang.reflect.Method describe = AutonomyEditorPanel.class.getDeclaredMethod("signalAddresses",
            java.util.List.class);

        describe.setAccessible(true);

        return (String) describe.invoke(panel, signals);
    }

    /** One of the panel's private one-square describers, as its sentences use them. */
    private static String privately(AutonomyEditorPanel panel, String method, TileKey tile) throws Exception
    {
        java.lang.reflect.Method describe = AutonomyEditorPanel.class.getDeclaredMethod(method, TileKey.class);

        describe.setAccessible(true);

        return (String) describe.invoke(panel, tile);
    }

    private static void delete(File file)
    {
        if (file == null) return;

        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children) delete(child);
        }

        file.delete();
    }
}
