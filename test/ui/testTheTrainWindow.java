package ui;

import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.AskingWindows;
import support.Components;
import support.Mouse;
import support.Reflect;
import support.StationDoors;

/**
 * Station Settings' Train tab and the menus that lead to it and to the rest of Station Settings (FR-111), pressed as
 * the user presses them on the frozen railway.
 *
 * @author Adam
 */
public class testTheTrainWindow
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static org.json.JSONObject asOpened;

    private static final String PAGE = "1 - Main";

    private LayoutEditor editor;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;

        Components.settle();

        session = ui.getAutonomySession();

        assertNotNull(session, "no autonomy setup on this railway");

        asOpened = Components.onTheEventThread(() -> session.snapshotSetup());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    @BeforeMethod
    public void putTheSetupBack() throws Exception
    {
        support.FrozenEditor.putBack(ui, session, asOpened);
    }

    @AfterMethod(alwaysRun = true)
    public void closeWhatIsLeft() throws Exception
    {
        support.FrozenEditor.dispose(editor);

        editor = null;

        AskingWindows.closeEveryDialog();
    }

    private AutonomyEditorPanel theEditorsPanel() throws Exception
    {
        editor = support.FrozenEditor.editorOn(model, ui, session, PAGE);

        return Components.onTheEventThread(() -> editor.getAutonomyPanel());
    }

    private static TileKey aSquareWithATrain()
    {
        for (Map.Entry<TileKey, String> each : session.trainsWhereTheyStand().entrySet())
        {
            // The setup's own train, not one only the railway holds there, such as a train put at its home
            if (PAGE.equals(each.getKey().getPage()) && session.getStore().isStation(each.getKey())
                && each.getValue().equals(session.getLocomotiveNameAt(each.getKey()))) return each.getKey();
        }

        throw new AssertionError("precondition: no train stands on a station of " + PAGE);
    }

    private static TileKey anEmptyStation()
    {
        return anEmptyStationBut(null);
    }

    private static TileKey anEmptyStationBut(TileKey not)
    {
        return anEmptyStationWhere(each -> !each.equals(not));
    }

    /** An empty station on this page that passes the test. */
    private static TileKey anEmptyStationWhere(java.util.function.Predicate<TileKey> also)
    {
        for (TileKey each : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(each.getPage()) && session.getStore().isStation(each) && session.getLocomotiveNameAt(each) == null
                && also.test(each))
            {
                return each;
            }
        }

        throw new AssertionError("precondition: no empty station on " + PAGE);
    }

    private static TileKey aPlainSensor()
    {
        for (TileKey each : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(each.getPage()) && !session.getStore().isStation(each) && session.getLocomotiveNameAt(each) == null)
            {
                return each;
            }
        }

        throw new AssertionError("precondition: no sensor that is not a station on " + PAGE);
    }

    private static String aTrainNotPlaced()
    {
        for (String name : model.getLocList())
        {
            if (!session.placementsAutonomyWillWrite().containsValue(name)) return name;
        }

        throw new AssertionError("precondition: every locomotive is placed");
    }

    // ---------------------------------------------------------------------------------------------------- the window

    /**
     * Blue is for section headings and the Train tab has none: its row labels (Train, Active, Facing, Arrived from, Tail
     * lies) are black Semibold 13, as the Settings tab's are, and nothing in it is blue.
     *
     * MUTATION: build a row label with `UiStyle.label`, which is blue, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheRowLabelsAreBlack() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        java.awt.Container form = StationDoors.part(window, "train.form", java.awt.Container.class);

        java.util.List<String> blue = new java.util.ArrayList<>();

        for (javax.swing.JLabel each : Components.all(form, javax.swing.JLabel.class, null))
        {
            if (org.traincontrol.gui.widgets.UiStyle.BLUE.equals(each.getForeground())) blue.add(each.getText());
        }

        assertTrue(blue.isEmpty(), "blue in a tab with no section headings: " + blue);

        for (String key : new String[] {"autosetup.ui.trainTrain", "autosetup.ui.trainActive", "autosetup.ui.trainFacing",
            "autosetup.ui.trainArrived", "autosetup.ui.trainTail"})
        {
            java.util.List<javax.swing.JLabel> found = Components.all(form, javax.swing.JLabel.class,
                label -> I18n.t(key).equals(label.getText()));

            assertEquals(found.size(), 1, key + " is not one label of the tab");
            assertEquals(found.get(0).getForeground(), java.awt.Color.BLACK, key + " is not black");
            assertEquals(found.get(0).getFont(), org.traincontrol.gui.widgets.UiStyle.LABEL_FONT, key + " is not Semibold 13");
        }

        StationDoors.close(window);
    }

    /**
     * The train window's Locomotive Settings button wears the application's button font, Segoe UI Bold 12, as every other
     * window's buttons do (the tail's Pick button is held in testTheTailCanBeGivenInTheEditor).
     *
     * MUTATION: give `train.settings` a Plain 13 font again in `TrainTab`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheWindowsButtonsWearTheButtonFont() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        javax.swing.JButton settings = StationDoors.part(window, "train.settings", javax.swing.JButton.class);

        assertEquals(settings.getFont(), org.traincontrol.gui.widgets.UiStyle.BUTTON_FONT, "train.settings is not Bold 12");

        StationDoors.close(window);
    }

    /**
     * Done Editing stays where it was when the tabs change (OB-479: "the button shifts up/down by a few pixels when
     * changing between the Settings and Train Placement tabs").
     *
     * MUTATION: leave out the footer's strut in `StationSettingsWindow`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testDoneEditingStaysPutAcrossTheTabs() throws Exception
    {
        StationSettingsWindow window = StationDoors.openTrain(theEditorsPanel(), aSquareWithATrain());

        javax.swing.JTabbedPane tabs = StationDoors.part(window, "station.tabs", javax.swing.JTabbedPane.class);
        javax.swing.JButton done = StationDoors.part(window, "station.close", javax.swing.JButton.class);

        int onTrain = Components.onTheEventThread(() -> done.getLocationOnScreen().y);

        Components.onTheEventThread(() -> { tabs.setSelectedIndex(0); return null; });
        Components.settle();

        assertEquals((int) Components.onTheEventThread(() -> done.getLocationOnScreen().y), onTrain,
            "Done Editing moved between the Train and Settings tabs");

        StationDoors.close(window);
    }

    /**
     * The train popup and Station Settings both end with Done Editing, not Close (Adam): what is changed there is saved
     * as it is made, so the button only says the editing is over.
     *
     * MUTATION: give `StationWindowBase.assemble`'s button `autosetup.ui.stationClose` again, and this fails.
     */
    @Test(timeOut = 120000)
    public void testBothWindowsEndWithDoneEditing() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        java.awt.Window train = StationDoors.openTrain(panel, square);

        assertEquals(StationDoors.part(train, "station.close", javax.swing.JButton.class).getText(),
            said("autosetup.ui.stationDone", null), "the train popup does not end with Done Editing");

        StationDoors.close(train);

        java.awt.Window station = StationDoors.open(panel, square);

        assertEquals(StationDoors.part(station, "station.close", javax.swing.JButton.class).getText(),
            said("autosetup.ui.stationDone", null), "Station Settings does not end with Done Editing");

        StationDoors.close(station);
    }

    /**
     * The train is a tab of Station Settings (FR-195; Adam: "change the 'change train at station' to just open the new
     * tab"): Change Train at... on the editor's menu brings forward the Station Settings already open and switches it to
     * its Train tab, Station Settings... switches it back, and Place a Locomotive Here... on an empty station opens the
     * Train tab too.  No second window opens.
     *
     * MUTATION: leave `showTab` out of `AutonomyEditorPanel.openTrainTab`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testChangeTrainOpensStationSettingsOnItsTrainTab() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();
        TileKey empty = anEmptyStation();

        StationSettingsWindow open = StationDoors.open(panel, full);

        assertFalse(StationDoors.showsTheTrainTab(open), "precondition: Station Settings opened on its Train tab");

        fromTheEditorsMenu(panel, full, I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(full)));

        assertSame(StationDoors.showing(StationSettingsWindow.class), open, "Change Train at did not bring forward the"
            + " Station Settings already open");

        assertTrue(StationDoors.showsTheTrainTab(open), "Change Train at did not switch Station Settings to its Train tab");

        assertEquals(stationWindowsShowing(), 1, "Change Train at opened a second window");

        fromTheEditorsMenu(panel, full, I18n.t("autosetup.ui.menuStationSettings"));

        assertFalse(StationDoors.showsTheTrainTab(open), "Station Settings... did not switch back to the Settings tab");

        StationDoors.close(open);

        fromTheEditorsMenu(panel, empty, placeHere());

        StationSettingsWindow placing = StationDoors.showing(StationSettingsWindow.class);

        assertNotNull(placing, "Place a Locomotive Here did not open Station Settings");
        assertEquals(placing.squareForTest(), empty, "Place a Locomotive Here opened another square");
        assertTrue(StationDoors.showsTheTrainTab(placing), "Place a Locomotive Here did not open the Train tab");

        StationDoors.close(placing);
    }

    /**
     * Station Settings' tabs wear icons as Locomotive Settings' do (Adam: "a station icon and a locomotive icon, similar
     * to the style in the locomotive editor window"): the Settings tab the drawn station, the Train tab the locomotive
     * of Locomotive Settings' first tab, each beside its title.
     *
     * MUTATION: drop the icon from the Train tab's `tabs.addTab` in `StationSettingsWindow`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheTabsWearAStationAndALocomotive() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(theEditorsPanel(), aSquareWithATrain());

        javax.swing.JTabbedPane tabs = StationDoors.part(window, "station.tabs", javax.swing.JTabbedPane.class);

        javax.swing.Icon[] expected = {org.traincontrol.gui.widgets.TabIcons.station(),
            org.traincontrol.gui.widgets.TabIcons.locomotive()};

        for (int i = 0; i < 2; i++)
        {
            final int tab = i;

            javax.swing.Icon icon = Components.onTheEventThread(() -> tabs.getIconAt(tab));

            assertNotNull(icon, "tab " + i + " has no icon");
            assertEquals(painted(icon), painted(expected[i]), "tab " + i + " does not wear its drawn icon");
            assertFalse(Components.onTheEventThread(() -> tabs.getTitleAt(tab)).isEmpty(), "tab " + i + " lost its title");
        }

        StationDoors.close(window);
    }

    /** An icon's pixels, as one string to compare. */
    private static String painted(javax.swing.Icon icon)
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        icon.paintIcon(null, image.getGraphics(), 0, 0);

        return icon.getIconWidth() + "x" + icon.getIconHeight() + java.util.Arrays.toString(
            image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }

    /** Clicks an item of the editor's menu on a square, as a right-click and a click do. */
    private static void fromTheEditorsMenu(AutonomyEditorPanel panel, TileKey square, String item) throws Exception
    {
        JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

        AbstractButton chosen = Components.withText(menu, item);

        assertNotNull(chosen, "the editor's menu on " + square + " has no \"" + item + "\"");

        Components.onTheEventThread(() -> { chosen.doClick(); return null; });

        Components.settle();
    }

    /** How many of the autonomy editor's square windows are showing. */
    private static int stationWindowsShowing()
    {
        return Components.onTheEventThread(() ->
        {
            int out = 0;

            for (java.awt.Window each : java.awt.Window.getWindows())
            {
                if (each.isShowing() && each.getClass().getPackage().getName().endsWith(".autonomyeditor")) out++;
            }

            return out;
        });
    }

    /**
     * Every (i) of the train popup explains its row in every state (OB-429): where no train stands, each says what its
     * row is and that no train stands here; beside Tail lies, where the tail is not known, it says what Tail lies means
     * and why it is not known here.
     *
     * MUTATION: give the tail mark no tooltip where `tailFacts` finds no question, and this fails.
     */
    @Test(timeOut = 120000)
    public void testEveryMarkExplainsItsRowWithNoAnswer() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        for (TileKey square : new TileKey[] {anEmptyStation(), aSquareWithATrain()})
        {
            StationSettingsWindow window = StationDoors.openTrain(panel, square);

            try
            {
                java.util.List<String> marks = Components.onTheEventThread(() ->
                {
                    java.util.List<String> out = new java.util.ArrayList<>();

                    java.awt.Container form = Components.named(window, "train.form", java.awt.Container.class);

                    for (javax.swing.JLabel mark : Components.all(form, javax.swing.JLabel.class,
                        l -> l.getIcon() == org.traincontrol.gui.InfoMark.icon()))
                    {
                        out.add(mark.getToolTipText() == null ? "" : mark.getToolTipText().replaceAll("<[^>]*>", " ")
                            .replace("&nbsp;", " ").replaceAll("\\s+", " ").trim());
                    }

                    return out;
                });

                assertEquals(marks.size(), 4, "precondition: the popup has not its four (i) marks: " + marks);

                for (String each : marks)
                {
                    assertFalse(each.isEmpty(), "an (i) of the popup on " + square + " says nothing: " + marks);
                }

                String tail = marks.get(3);

                if (session.getLocomotiveNameAt(square) == null)
                {
                    assertTrue(tail.contains(said("autosetup.ui.flyNoTrain", null)), "the (i) beside Tail lies does not"
                        + " say no train stands here: " + tail);
                }
                else
                {
                    assertNotNull(Components.onTheEventThread(() -> Components.find(Components.named(window,
                        "train.tail", java.awt.Container.class), javax.swing.JLabel.class,
                        l -> I18n.t("autosetup.ui.tailCrossedNotKnown").equals(l.getText()))),
                        "precondition: the tail of the train on " + square + " is known");

                    assertTrue(tail.contains(I18n.t("autosetup.ui.flyTailNow").replaceAll("\\s+", " ")), "the (i)"
                        + " beside an unknown Tail lies does not say what Tail lies means: " + tail);
                }
            }
            finally
            {
                SwingUtilities.invokeAndWait(window::dispose);
            }
        }
    }

    /**
     * Opened and closed, the window changes no setup and dirties nothing.
     *
     * MUTATION: have `fill`'s `train.setSelectedItem` run outside `reading`, so `pressTrain` acts, and this fails.
     */
    @Test(timeOut = 120000)
    public void testLookingChangesNothing() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        String before = session.snapshotSetup().toString();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        StationDoors.close(window);

        Components.settle();

        assertEquals(session.snapshotSetup().toString(), before, "looking at a train changed the setup");

        assertFalse(panel.isDirty(), "looking at a train dirtied the setup");
    }

    /**
     * The list puts a train on the square, and None takes it off.
     *
     * MUTATION: have `pressTrain` skip `removeLocomotive`, and None leaves the train there and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheListPlacesATrainAndNoneTakesItOff() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = anEmptyStation();

        String train = aTrainNotPlaced();

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        assertFalse(StationDoors.live(window, "train.active"), "Active takes clicks with no train to pause");

        Map<TileKey, String> others = session.placementsAutonomyWillWrite();

        StationDoors.pick(window, "train.train", train);

        assertEquals(session.getLocomotiveNameAt(square), train, "the list did not put the train on the square");

        assertTrue(StationDoors.live(window, "train.active"), "Active stays gray with a train here");

        others.put(square, train);

        assertEquals(session.placementsAutonomyWillWrite(), others, "placing moved another train");

        StationDoors.pick(window, "train.train", I18n.t("autosetup.ui.labelNone"));

        assertNull(session.getLocomotiveNameAt(square), "None left the train on the square");
    }

    /**
     * After a run the track diagram's setup can still name Y on a square where the railway has X, and Y stand on another
     * station: the Train tab shows X, and None takes X off the railway and leaves Y where it stands (FR-195).
     *
     * MUTATION: have `AutonomyEditorPanel.trainsLeaving` name only `locomotiveAt(tile)`, and X stays, Y leaves.
     */
    @Test(timeOut = 240000)
    public void testNoneAfterARunTakesOffTheTrainTheTabShows() throws Exception
    {
        chooseAfterARun(aTrainPlacedNowhere(null), null);
    }

    /**
     * The same with another train chosen in place of None: it takes the square, the train the tab showed leaves the
     * railway, and the setup's train stays where the run left it (FR-195).
     *
     * MUTATION: have `AutonomyEditorPanel.placeLocomotive` name `locomotiveAt(tile)` too, and the setup's train leaves.
     */
    @Test(timeOut = 240000)
    public void testAnotherTrainChosenAfterARunLeavesTheSetupsTrainWhereItStands() throws Exception
    {
        String shown = aTrainPlacedNowhere(null);

        chooseAfterARun(shown, aTrainPlacedNowhere(shown));
    }

    /** A train neither the setup nor the railway has anywhere, other than `except`. */
    private static String aTrainPlacedNowhere(String except)
    {
        for (String name : model.getLocList())
        {
            if (!name.equals(except) && !session.placementsAutonomyWillWrite().containsValue(name)
                && model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name)) == null) return name;
        }

        throw new AssertionError("precondition: no train placed nowhere");
    }

    /**
     * The same where the train the run brought in is one the setup still has on the square it left: None takes it off
     * the railway, and does not stand it back there.
     *
     * MUTATION: leave out `removeLocomotive`'s clearing of `otherSquaresOf`, and it goes back.
     */
    @Test(timeOut = 240000)
    public void testNoneAfterARunDoesNotSendTheTrainShownBackToItsOldSquare() throws Exception
    {
        String brought = anotherPlacedTrainOnTheRailway(aSquareWithATrain());

        assertNotNull(brought, "precondition: no other placed train stands on the railway");

        chooseAfterARun(brought, null);
    }

    /** A train the setup places on a square other than this one, standing on the railway; null where there is none. */
    private static String anotherPlacedTrainOnTheRailway(TileKey square)
    {
        String found = null;

        for (Map.Entry<TileKey, String> each : session.placementsAutonomyWillWrite().entrySet())
        {
            if (!each.getKey().equals(square)
                && model.getAutoLayout().getLocomotiveLocation(model.getLocByName(each.getValue())) != null)
            {
                found = each.getValue();
            }
        }

        return found;
    }

    /** A free station copy of this square on the running layout, or null. */
    private static org.traincontrol.automation.Point aFreeCopyOf(TileKey square)
    {
        for (org.traincontrol.automation.Point p : model.getAutoLayout().getPoints())
        {
            if (square.equals(session.getStationIndex().squareOf(p)) && p.isDestination() && p.getCurrentLocomotive() == null)
            {
                return p;
            }
        }

        return null;
    }

    /** As a run leaves it, the railway alone told: the setup's train gone on to `other`, and `shown` on its copy. */
    private static void asAfterARun(String setupTrain, String copyName, org.traincontrol.automation.Point other, String shown)
    {
        org.traincontrol.automation.Layout running = model.getAutoLayout();

        running.moveLocomotive(null, copyName, false);
        assertTrue(running.moveLocomotive(setupTrain, other.getName(), false), "precondition: could not move " + setupTrain);
        assertTrue(running.moveLocomotive(shown, copyName, false), "precondition: could not stand " + shown);
    }

    /** The railway as the setup has it again, for the claims after: these trains lifted, the two stood back. */
    private static void standBack(String setupTrain, String copyName, String shown, org.traincontrol.automation.Point shownWas,
        String also)
    {
        Components.onTheEventThread(() ->
        {
            org.traincontrol.automation.Layout now = model.getAutoLayout();

            for (String each : new String[] {shown, also, setupTrain})
            {
                org.traincontrol.automation.Point at = each == null ? null
                    : now.getLocomotiveLocation(model.getLocByName(each));

                if (at != null) now.moveLocomotive(null, at.getName(), false);
            }

            now.moveLocomotive(setupTrain, copyName, false);

            if (shownWas != null) now.moveLocomotive(shown, shownWas.getName(), false);

            return null;
        });

        Components.settle();
    }

    /**
     * Moves the setup's train of a square on to an empty station and stands `shown` on the square, as a run would, then
     * chooses `pick` (null: None) on the diagram's Train tab: `shown` must leave the railway, the setup's train stay on
     * it, and `pick` stand on the square.
     */
    private void chooseAfterARun(final String shown, final String pick) throws Exception
    {
        final TileKey square = aSquareWithATrain();
        final String setupTrain = session.getLocomotiveNameAt(square);
        final TileKey elsewhere = anEmptyStation();

        org.traincontrol.automation.Point copy = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(setupTrain));
        org.traincontrol.automation.Point other = aFreeCopyOf(elsewhere);

        assertNotNull(copy, "precondition: the setup's train " + setupTrain + " stands nowhere on the railway");
        assertNotNull(other, "precondition: no free copy of " + elsewhere);

        final String copyName = copy.getName();
        final org.traincontrol.automation.Point shownWas = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(shown));

        try
        {
            asAfterARun(setupTrain, copyName, other, shown);

            assertEquals(session.getLocomotiveNameAt(square), setupTrain, "precondition: the setup no longer has its train here");

            Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, ui, square),
                I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(square))));

            Components.settle();

            final StationSettingsWindow window = StationDoors.showing(StationSettingsWindow.class);

            assertNotNull(window, "Change Train at did not open Station Settings from the track diagram");

            try
            {
                // A train the setup places elsewhere is marked so in the list
                Object showing = Components.onTheEventThread(() ->
                    StationDoors.part(window, "train.train", javax.swing.JComboBox.class).getSelectedItem());

                assertTrue(String.valueOf(showing).startsWith(shown), "precondition: the Train tab shows " + showing
                    + ", not the railway's train " + shown);

                org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

                StationDoors.pick(window, "train.train", pick != null ? pick : I18n.t("autosetup.ui.labelNone"));

                Components.drain(8);
                Components.settle();
            }
            finally
            {
                org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

                Components.onTheEventThread(() -> { window.dispose(); return null; });
            }

            org.traincontrol.automation.Layout after = model.getAutoLayout();
            org.traincontrol.automation.Point shownAt = after.getLocomotiveLocation(model.getLocByName(shown));

            String chose = pick != null ? pick : "None";

            assertNull(shownAt, chose + " on the Train tab, which showed " + shown + ", left it on "
                + (shownAt == null ? null : shownAt.getName()));
            assertNotNull(after.getLocomotiveLocation(model.getLocByName(setupTrain)), chose + " on the Train tab took "
                + setupTrain + " off the railway at " + other.getName() + ", a train the tab did not show");

            if (pick != null)
            {
                org.traincontrol.automation.Point pickAt = after.getLocomotiveLocation(model.getLocByName(pick));

                assertTrue(pickAt != null && square.equals(session.getStationIndex().squareOf(pickAt)),
                    pick + " chosen on the Train tab does not stand on " + square);
            }
        }
        finally
        {
            standBack(setupTrain, copyName, shown, shownWas, pick);
        }
    }

    /**
     * A key step in the Train list only shows a train: Down, or a typed letter, places nothing, Escape puts the list
     * back and leaves the window open, and Enter places the train shown.  A pick from the open list acts as the list
     * closes; a list closed by canceling puts the choice back.
     *
     * MUTATION: have `StationWindowBase.Choice.fireActionEvent` ignore `quiet`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAKeyStepInTheListPlacesNothingUntilEnter() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = anEmptyStation();

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        javax.swing.JComboBox<?> list = StationDoors.part(window, "train.train", javax.swing.JComboBox.class);

        String none = I18n.t("autosetup.ui.labelNone");

        support.Keys.press(list, java.awt.event.KeyEvent.VK_DOWN);

        Object shown = Components.onTheEventThread(list::getSelectedItem);

        assertFalse(none.equals(shown), "precondition: Down did not step the list");
        assertNull(session.getLocomotiveNameAt(square), "Down placed " + shown);

        // A typed letter steps to the next train whose name starts with it
        String last = String.valueOf(Components.onTheEventThread(() -> list.getItemAt(list.getItemCount() - 1)));
        char letter = Character.toUpperCase(last.charAt(0));

        if (letter >= 'A' && letter <= 'Z' || letter >= '0' && letter <= '9')
        {
            support.Keys.press(list, letter);

            assertNull(session.getLocomotiveNameAt(square), "typing " + letter + " placed "
                + Components.onTheEventThread(list::getSelectedItem));
        }

        support.Keys.press(list, java.awt.event.KeyEvent.VK_ESCAPE);

        assertEquals(Components.onTheEventThread(list::getSelectedItem), none, "Escape did not put the list back");
        assertTrue(Components.onTheEventThread(window::isShowing), "Escape on a stepped list closed the window");

        support.Keys.press(list, java.awt.event.KeyEvent.VK_DOWN);
        support.Keys.press(list, java.awt.event.KeyEvent.VK_ENTER);

        String placed = session.getLocomotiveNameAt(square);

        assertNotNull(placed, "Enter placed nothing");
        assertTrue(String.valueOf(shown).startsWith(placed), "Enter placed " + placed + ", not " + shown);

        // As a pick from the open list: chosen while it is open, acted on as it closes
        Components.onTheEventThread(() -> { list.setPopupVisible(true); list.setSelectedItem(none); return null; });

        assertEquals(session.getLocomotiveNameAt(square), placed, "a choice in the open list acted before it closed");

        Components.onTheEventThread(() -> { list.setPopupVisible(false); return null; });
        Components.settle();

        assertNull(session.getLocomotiveNameAt(square), "None picked from the list left the train there");

        Components.onTheEventThread(() ->
        {
            list.setPopupVisible(true);
            list.setSelectedIndex(1);
            list.firePopupMenuCanceled();
            list.setPopupVisible(false);

            return null;
        });
        Components.settle();

        assertNull(session.getLocomotiveNameAt(square), "a canceled list placed a train");
        assertEquals(Components.onTheEventThread(list::getSelectedItem), none, "a canceled list did not put the choice back");

        StationDoors.close(window);
    }

    /**
     * A train placed from the list records the side it came in by, in the setup and on the railway, as every door that
     * puts a train down by hand does (Adam: "the missing arrival side should be set - either from the data, by the
     * user, or randomly"): Tunnel's one way in is from the north, BottomMainB's from the west.  The squares are emptied
     * first, so nothing recorded before can answer.
     *
     * MUTATION: drop the `session.setArrivedFrom` from `AutonomyEditorPanel.placeLocomotive`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAPlacedTrainRecordsTheSideItCameIn() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            for (String[] each : new String[][] {{"Tunnel", "N"}, {"BottomMainB", "W"}})
            {
                final TileKey square = support.StandingTrain.squareNamed(session, each[0]);

                assertEquals(Components.onTheEventThread(() -> session.unbarredArrivalSides(square)).toString(),
                    "[" + each[1] + "]", "precondition: " + each[0] + "'s ways in have changed");

                Components.onTheEventThread(() -> { session.placeLocomotive(square, null); return null; });

                Components.settle();

                String train = aTrainNotPlaced();

                StationSettingsWindow window = StationDoors.openTrain(panel, square);

                StationDoors.pick(window, "train.train", train);

                assertEquals(session.getLocomotiveNameAt(square), train, "the list did not put the train on " + each[0]);

                assertEquals(session.getArrivedFrom(square), each[1], "the train put on " + each[0] + " recorded no side,"
                    + " or the wrong one, in the setup");

                final Locomotive placed = model.getLocByName(train);

                assertTrue(Components.waitFor(() -> model.getAutoLayout().getLocomotiveLocation(placed) != null, 20000),
                    "the rebuild did not put " + train + " on the railway");

                Components.settle();

                assertEquals(model.getAutoLayout().getLocomotiveLocation(placed).getArrivedFrom(), each[1], "the train put"
                    + " on " + each[0] + " has no side, or the wrong one, on the railway");

                StationDoors.close(window);
            }
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);
        }
    }

    /**
     * Active reads the train, not the setup's list, so a card's pause shows at once (OB-346 part 2), in the window and on the
     * editor's tick; clicking it sets the train going.
     *
     * MUTATION: have `AutonomyEditorPanel.isPaused` read the setup's list, and the tick still says going and this fails.
     */
    @Test(timeOut = 120000)
    public void testActiveReadsTheTrain() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        Locomotive train = model.getLocByName(session.getLocomotiveNameAt(square));

        try
        {
            train.setAutonomyPaused(true);

            assertFalse(session.getPausedLocomotives().contains(train.getName()), "precondition: the setup knows of the pause");

            JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

            AbstractButton tick = Components.withText(menu, I18n.f("autolayout.ui.menuLocomotiveActive", train.getName()));

            assertNotNull(tick, "precondition: the editor's menu has no Is Active");

            assertFalse(tick.isSelected(), "the editor's tick reads going for a train its card paused");

            StationSettingsWindow window = StationDoors.openTrain(panel, square);

            assertFalse(StationDoors.on(window, "train.active"), "the window reads going for a train its card paused");

            StationDoors.press(window, "train.active");

            assertFalse(train.isAutonomyPaused(), "ticking Active did not set the train going");
        }
        finally
        {
            train.setAutonomyPaused(false);
        }
    }

    /**
     * During a run the window is read-only but for Active, which pauses the train alone (FR-059), and the setup stays as it
     * was.
     *
     * MUTATION: have `fill` gray Active with the rest, or `pressActive` go through `act`, which refuses, and this fails.
     */
    @Test(timeOut = 180000)
    public void testDuringARunOnlyActiveIsLive() throws Exception
    {
        Thread driving = null;

        try
        {
            Components.settle();

            Object[] dispatched = regression.testTheImportDoorReadsAnOldFile.dispatchATrain(ui, null);

            driving = (Thread) dispatched[3];

            TileKey square = null;

            for (Map.Entry<TileKey, String> each : session.trainsWhereTheyStand().entrySet())
            {
                if (!each.getValue().equals(dispatched[0]) && !ui.getModel().getAutoLayout().isAlreadyUnderway(
                    model.getLocByName(each.getValue())) && session.getStore().isStation(each.getKey())
                    && each.getValue().equals(session.getLocomotiveNameAt(each.getKey()))) square = each.getKey();
            }

            assertNotNull(square, "precondition: no other train stands on a station during the run");

            final TileKey standing = square;

            Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, ui, standing),
                I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(standing))));

            Components.settle();

            StationSettingsWindow window = StationDoors.showing(StationSettingsWindow.class);

            assertNotNull(window, "the train window did not open during a run");

            assertTrue(window.isReadOnly(), "the train window is not read-only during a run");

            assertFalse(StationDoors.live(window, "train.train"), "the list is live during a run");

            assertTrue(StationDoors.live(window, "train.active"), "Active is gray during a run");

            String before = session.snapshotSetup().toString();

            Locomotive train = model.getLocByName(session.getLocomotiveNameAt(standing));

            StationDoors.press(window, "train.active");

            try
            {
                assertTrue(train.isAutonomyPaused(), "unticking Active during a run did not pause the train");

                assertEquals(session.snapshotSetup().toString(), before, "pausing a train during a run edited the setup");
            }
            finally
            {
                train.setAutonomyPaused(false);
            }

            // And FR-059: the running menu ticks the train it stands on, as its card does
            JPopupMenu menu = AskingWindows.menuOn(ui, ui, standing);

            JMenuItem tick = AskingWindows.itemOf(menu, I18n.f("autolayout.ui.menuLocomotiveActive", train.getName()));

            assertTrue(tick.isSelected(), "the running menu reads paused for a train that is going");

            Mouse.choose(tick);

            Components.settle();

            assertTrue(train.isAutonomyPaused(), "the running menu's tick did not pause the train");

            train.setAutonomyPaused(false);

            assertTrue(AskingWindows.hasItem(AskingWindows.menuOn(ui, ui, standing), I18n.t("autosetup.ui.menuStationSettings")),
                "the running menu has no Station Settings");

            Mouse.closeMenus();
        }
        finally
        {
            if (driving != null) driving.interrupt();
        }
    }

    /** The train window on this square, opened from the track diagram's menu. */
    private static StationSettingsWindow theDiagramsTrainWindow(TileKey square) throws Exception
    {
        Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, ui, square),
            I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(square))));

        Components.settle();

        StationSettingsWindow window = StationDoors.showing(StationSettingsWindow.class);

        assertNotNull(window, "the train window did not open from the track diagram");

        return window;
    }

    /**
     * While a route runs with autonomy idle, the train window's Active pauses the train alone, as the track diagram's
     * Is Active does (FR-187): live, and the setup is as it was.
     *
     * MUTATION: gray Active in `TrainTab.fill` unless autonomy itself runs, and this fails.
     */
    @Test(timeOut = 180000)
    public void testWhileARouteRunsActivePausesTheTrainAlone() throws Exception
    {
        TileKey square = aSquareWithATrain();

        Locomotive train = model.getLocByName(session.getLocomotiveNameAt(square));

        StationSettingsWindow window = theDiagramsTrainWindow(square);

        assertTrue(StationDoors.on(window, "train.active"), "precondition: Active is not ticked");

        regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, true);

        try
        {
            assertTrue(Components.waitFor(() -> Components.onTheEventThread(window::isReadOnly), 5000),
                "the window is not read-only while a route runs");

            assertTrue(StationDoors.live(window, "train.active"), "Active is gray while a route runs");

            String before = Components.onTheEventThread(() -> session.snapshotSetup().toString());

            StationDoors.press(window, "train.active");

            assertTrue(train.isAutonomyPaused(), "unticking Active while a route runs did not pause the train");
            assertEquals(Components.onTheEventThread(() -> session.snapshotSetup().toString()), before,
                "Active while a route runs edited the setup");
        }
        finally
        {
            regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, false);

            train.setAutonomyPaused(false);

            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A train stepped to in the list is placed when the keyboard moves on in the window (Tab, another control clicked),
     * as a typed field applies on leaving, and the choice still shown when the window closes is applied too.
     *
     * MUTATION: drop the focus listener from `StationWindowBase.Choice`, or the lists from `commitFields`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testLeavingTheListOrClosingTheWindowAppliesTheTrainShown() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = anEmptyStation();

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        javax.swing.JComboBox<?> list = StationDoors.part(window, "train.train", javax.swing.JComboBox.class);

        support.Keys.press(list, java.awt.event.KeyEvent.VK_DOWN);

        Object shown = Components.onTheEventThread(list::getSelectedItem);

        assertNull(session.getLocomotiveNameAt(square), "precondition: Down placed " + shown);

        // Tab takes the keyboard to the next control in the window
        StationDoors.loseKeyboard(list, false, StationDoors.part(window, "train.active", java.awt.Component.class));

        String placed = session.getLocomotiveNameAt(square);

        assertNotNull(placed, "leaving the list placed nothing");
        assertTrue(String.valueOf(shown).startsWith(placed), "leaving the list placed " + placed + ", not " + shown);

        support.Keys.press(list, java.awt.event.KeyEvent.VK_UP);

        assertEquals(session.getLocomotiveNameAt(square), placed, "precondition: Up took the train off");

        StationDoors.close(window);

        assertNull(session.getLocomotiveNameAt(square), "closing the window dropped None, shown in the list");
    }

    /**
     * A train stepped to in the list is put back, asking nothing, when another window is clicked, so a placement question
     * never swallows that click (the main window's Stop, the editor's Save).  Locomotive Settings... opens the train
     * standing, not the one shown.
     *
     * MUTATION: have the Choice's focus listener settle a loss for now, or the window's settle the lists, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAnotherWindowClickedPutsTheListBackAndAsksNothing() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();
        String standing = session.getLocomotiveNameAt(square);

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        javax.swing.JComboBox<?> list = StationDoors.part(window, "train.train", javax.swing.JComboBox.class);

        Object was = StationDoors.step(list);

        LocomotiveSettingsFrame frame = null;

        try (support.Answering answering = support.Answering.start(message -> null))
        {
            StationDoors.press(window, "train.settings");
            StationDoors.toAnotherWindow(window, list);

            frame = StationDoors.showing(LocomotiveSettingsFrame.class);

            assertEquals(answering.asked(), java.util.Collections.emptyList(), "another window clicked asked");
        }
        finally
        {
            if (frame != null) SwingUtilities.invokeAndWait(frame::dispose);
        }

        assertEquals(session.getLocomotiveNameAt(square), standing, "another window clicked placed the train shown");
        assertEquals(Components.onTheEventThread(list::getSelectedItem), was, "the list still shows a train not placed");
        assertNotNull(frame, "Locomotive Settings did not open");
        assertEquals(frame.getLocomotive(), model.getLocByName(standing), "Locomotive Settings opened on the train shown");

        StationDoors.close(window);
    }

    /**
     * In the autonomy editor's own train window, Active stays a setup edit, gray while a route runs: a pause of the
     * railway's train alone would be undone by the editor's draft (the track diagram's window pauses it).
     *
     * MUTATION: leave `editor.menuOnly` out of `TrainTab.pausesAlone`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testInTheEditorActiveIsGrayWhileARouteRuns() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        assertTrue(StationDoors.live(window, "train.active"), "precondition: Active is gray at rest");

        regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, true);

        try
        {
            assertTrue(Components.waitFor(() -> Components.onTheEventThread(window::isReadOnly), 5000),
                "precondition: the window is not read-only while a route runs");

            assertFalse(StationDoors.live(window, "train.active"), "the editor's Active is live while a route runs");
        }
        finally
        {
            regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, false);

            StationDoors.close(window);
        }
    }

    /**
     * Station Settings on a square with a train, opened from the track diagram's menu, with its watch stopped: a press
     * comes in the second before the watch looks again.
     */
    private static org.traincontrol.gui.autonomyeditor.StationSettingsWindow theDiagramsStationSettingsUnwatched()
        throws Exception
    {
        Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, ui, aSquareWithATrain()),
            I18n.t("autosetup.ui.menuStationSettings")));

        Components.settle();

        org.traincontrol.gui.autonomyeditor.StationSettingsWindow window =
            StationDoors.showing(org.traincontrol.gui.autonomyeditor.StationSettingsWindow.class);

        assertNotNull(window, "Station Settings did not open from the track diagram");

        Components.onTheEventThread(() ->
        {
            ((javax.swing.Timer) Reflect.get(window.getClass().getSuperclass(), window, "watch")).stop();

            return null;
        });

        return window;
    }

    /**
     * Station Settings opened from the track diagram goes, writing nothing, when a press comes after a configuration
     * was loaded and before its watch has looked again: the old setup is as it was.
     *
     * MUTATION: drop the `stillAbout` check from `StationWindowBase.refused`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testADiagramWindowWritesNothingOnceAnotherSetupIsLoaded() throws Exception
    {
        org.traincontrol.gui.autonomyeditor.StationSettingsWindow window = theDiagramsStationSettingsUnwatched();

        assertTrue(StationDoors.live(window, "station.fullAutonomy"), "precondition: the box is gray");

        String before = Components.onTheEventThread(() -> session.snapshotSetup().toString());

        try
        {
            // A load replaces the main window's setup
            Components.onTheEventThread(() ->
            {
                Reflect.set(TrainControlUI.class, ui, "autonomySession", null);

                return null;
            });

            assertTrue(Components.onTheEventThread(() -> ui.getAutonomySession() != session), "precondition: no other setup");

            StationDoors.press(window, "station.fullAutonomy", message -> null);

            assertEquals(Components.onTheEventThread(() -> session.snapshotSetup().toString()), before,
                "a press after a load wrote to the setup the window opened on");
            assertFalse(Components.onTheEventThread(window::isShowing), "the window stayed open on a setup no longer loaded");
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                Reflect.set(TrainControlUI.class, ui, "autonomySession", session);
                window.dispose();

                return null;
            });
        }
    }

    /**
     * Station Settings opened from the track diagram refuses a press made once an editor has opened, before its watch
     * has looked again: said over the window, as a run's refusal is, and the setup is as it was.
     *
     * MUTATION: drop the editor's check from `StationWindowBase.refused`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testADiagramWindowRefusesAPressOnceAnEditorOpens() throws Exception
    {
        org.traincontrol.gui.autonomyeditor.StationSettingsWindow window = theDiagramsStationSettingsUnwatched();

        assertFalse(window.isReadOnly(), "precondition: read-only at rest");

        editor = support.FrozenEditor.editorOn(model, ui, session, PAGE);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(ui::isLayoutEditorOpen), 10000),
            "precondition: the editor did not open");
        assertTrue(Components.onTheEventThread(window::isShowing), "precondition: the editor closed the window");
        assertTrue(StationDoors.live(window, "station.fullAutonomy"), "precondition: the box is gray");

        String before = Components.onTheEventThread(() -> session.snapshotSetup().toString());

        java.util.List<String> said = StationDoors.press(window, "station.fullAutonomy", message -> null);

        assertEquals(said, java.util.Collections.singletonList(I18n.t("autosetup.ui.menuEditorOpen")),
            "the press was not refused for the open editor");
        assertEquals(Components.onTheEventThread(() -> session.snapshotSetup().toString()), before,
            "a press with the editor open wrote to the setup");

        SwingUtilities.invokeAndWait(window::dispose);
    }

    /**
     * Hovering an (i) holds every tooltip up long enough to read, and only while the pointer is on it: a window closed
     * under the pointer, or a second arrival with no leaving between, still leaves the dismiss delay as it was.
     *
     * MUTATION: drop the hierarchy listener from `InfoMark.label`, and this fails.
     */
    @Test(timeOut = 60000)
    public void testAnInfoMarkPutsTheTooltipDelayBack() throws Exception
    {
        javax.swing.ToolTipManager tips = javax.swing.ToolTipManager.sharedInstance();

        int was = Components.onTheEventThread(tips::getDismissDelay);

        javax.swing.JLabel[] mark = new javax.swing.JLabel[1];

        javax.swing.JDialog shown = Components.onTheEventThread(() ->
        {
            TrainControlUI.installLookAndFeel();

            javax.swing.JDialog out = new javax.swing.JDialog();

            mark[0] = org.traincontrol.gui.InfoMark.label("a sentence");
            out.add(mark[0]);
            out.pack();
            out.setVisible(true);

            return out;
        });

        try
        {
            Mouse.enter(mark[0]);
            Mouse.enter(mark[0]);

            assertTrue(Components.onTheEventThread(tips::getDismissDelay) > was, "precondition: the (i) did not hold its tooltip up");

            Mouse.exit(mark[0]);

            assertEquals((int) Components.onTheEventThread(tips::getDismissDelay), was, "a second arrival kept the long delay");

            Mouse.enter(mark[0]);

            // Escape closes the window under the pointer: no exit comes
            SwingUtilities.invokeAndWait(shown::dispose);

            assertEquals((int) Components.onTheEventThread(tips::getDismissDelay), was,
                "a window closed under the pointer left every tooltip at the long delay");
        }
        finally
        {
            Components.onTheEventThread(() -> { tips.setDismissDelay(was); shown.dispose(); return null; });
        }
    }

    /**
     * Locomotive Settings opens that train's window on its Autonomy tab (FR-048), over the window that asked.
     *
     * MUTATION: have `pressSettings` open the Locomotive tab, and this fails.
     */
    @Test(timeOut = 120000)
    public void testLocomotiveSettingsOpensThatTrain() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        StationDoors.press(window, "train.settings");

        LocomotiveSettingsFrame frame = StationDoors.showing(LocomotiveSettingsFrame.class);

        assertNotNull(frame, "Locomotive Settings did not open");

        assertEquals(frame.getLocomotive(), model.getLocByName(session.getLocomotiveNameAt(square)),
            "Locomotive Settings opened on another locomotive");
        assertEquals(support.SettingsWindow.tabShowing(), LocomotiveSettingsFrame.Tab.AUTONOMY.ordinal(),
            "Locomotive Settings did not open on the Autonomy tab");

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * Locomotive Settings... sits in the lower left corner, on Done Editing's row at the form's left edge, and never sets
     * the window's width (Adam: "move the <wrench> locomotive settings label somewhere that doesn't widen the whole
     * window"; and to the lower left, on the close button's row).
     *
     * MUTATION: put the button back under the Train list in the form, and this fails.
     */
    @Test(timeOut = 120000)
    public void testLocomotiveSettingsDoesNotWidenTheWindow() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        try
        {
            int[] widths = Components.onTheEventThread(() ->
            {
                java.awt.Container root = window.getContentPane();
                java.awt.Container form = Components.named(window, "train.form", java.awt.Container.class);
                java.awt.Component close = Components.named(window, "station.close", java.awt.Component.class);
                java.awt.Component settings = Components.named(window, "train.settings", java.awt.Component.class);

                java.awt.Rectangle button = SwingUtilities.convertRectangle(settings.getParent(), settings.getBounds(), root);
                java.awt.Rectangle done = SwingUtilities.convertRectangle(close.getParent(), close.getBounds(), root);

                assertEquals(button.x, SwingUtilities.convertPoint(form, form.getInsets().left, 0, root).x,
                    "Locomotive Settings... is not at the form's left edge");

                assertTrue(button.y <= done.getCenterY() && done.getCenterY() <= button.y + button.height,
                    "Locomotive Settings... is not on Done Editing's row: " + button + " and " + done);

                assertTrue(button.x + button.width < done.x, "Locomotive Settings... is not left of Done Editing");

                window.pack();

                int with = window.getWidth();

                java.awt.Container holder = settings.getParent();

                holder.remove(settings);
                holder.revalidate();

                window.pack();

                return new int[] {with, window.getWidth()};
            });

            assertEquals(widths[0], widths[1], "Locomotive Settings... sets the train window's width");
        }
        finally
        {
            // Even after a failure: a window left open takes the class's later right-clicks
            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * The train list is as wide as the names it holds, and Active as its words, each at its row's left rather than
     * stretched across the window (OB-447: "The train name dropdown on the train tab of the station editor seems very
     * wide").
     *
     * MUTATION: drop the `fill = NONE` loop in `TrainTab`'s constructor, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheTrainListAndActiveAreNotStretched() throws Exception
    {
        StationSettingsWindow window = StationDoors.openTrain(theEditorsPanel(), aSquareWithATrain());

        try
        {
            Components.onTheEventThread(() ->
            {
                javax.swing.JComponent list = Components.named(window, "train.train", javax.swing.JComponent.class);
                javax.swing.JComponent active = Components.named(window, "train.active", javax.swing.JComponent.class);

                for (javax.swing.JComponent each : new javax.swing.JComponent[] {list, active})
                {
                    assertEquals(each.getWidth(), each.getPreferredSize().width, each.getName() + " is stretched to "
                        + each.getWidth() + " px for " + each.getPreferredSize().width + " px of content");
                }

                assertEquals(list.getX(), active.getX(), "the train list and Active do not start at one left edge");

                return null;
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A train named at great length leaves the train list narrower than the name, and the open list still shows the name
     * whole (OB-447).
     *
     * MUTATION: drop the cap from the train list's `getPreferredSize` in `TrainTab`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testALongTrainNameIsCappedInTheBoxAndWholeInTheList() throws Exception
    {
        StationSettingsWindow window = StationDoors.openTrain(theEditorsPanel(), aSquareWithATrain());

        final String longName = "Upper Ballymacarrett Junction Carriage Sidings Shunter (Northbound Loop) No. 2";

        try
        {
            final javax.swing.JComboBox<?> list = StationDoors.part(window, "train.train", javax.swing.JComboBox.class);

            int named = Components.onTheEventThread(() ->
            {
                // Added at the end, which changes no choice, so nothing is placed
                @SuppressWarnings("unchecked")
                javax.swing.DefaultComboBoxModel<String> names = (javax.swing.DefaultComboBoxModel<String>) list.getModel();

                names.addElement(longName);

                window.getContentPane().revalidate();
                window.validate();

                return list.getFontMetrics(list.getFont()).stringWidth(longName);
            });

            Components.settle();

            assertTrue(Components.onTheEventThread(list::getWidth) < named, "the train list grew to the long name's "
                + named + " px: " + Components.onTheEventThread(list::getWidth));

            Components.onTheEventThread(() -> { list.showPopup(); return null; });
            Components.settle();

            int open = Components.onTheEventThread(() ->
            {
                Object popup = list.getAccessibleContext().getAccessibleChild(0);

                return ((javax.swing.plaf.basic.ComboPopup) popup).getList().getWidth();
            });

            Components.onTheEventThread(() -> { list.hidePopup(); return null; });

            assertTrue(open >= named, "the open list is " + open + " px, too narrow for the long name's " + named + " px");
        }
        finally
        {
            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A click on an (i) shows its tooltip at once, without the hover delay (Adam: "click on any i should immediately
     * show the tooltip, so you don't have to wait for the hover timeout"), and leaves every other component's delay as
     * it was.  The hover delay is set to a minute, so a tooltip within the wait came from the click.
     *
     * MUTATION: drop the press listener from `InfoMark.label`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAClickOnAnInfoMarkShowsItsTooltipAtOnce() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        javax.swing.ToolTipManager tips = javax.swing.ToolTipManager.sharedInstance();

        int was = Components.onTheEventThread(tips::getInitialDelay);

        java.awt.Container form = StationDoors.part(window, "train.form", java.awt.Container.class);

        javax.swing.JLabel mark = Components.onTheEventThread(() -> Components.find(form, javax.swing.JLabel.class,
            l -> l.getIcon() == org.traincontrol.gui.InfoMark.icon() && l.getToolTipText() != null));

        try
        {
            Components.onTheEventThread(() -> { tips.setInitialDelay(60000); return null; });

            Mouse.click(mark);

            assertTrue(Components.waitFor(() -> tipShowingFor(mark), 1000), "a click on an (i) showed no tooltip");

            assertEquals((int) Components.onTheEventThread(tips::getInitialDelay), 60000, "the click changed the hover"
                + " delay of every other component");
        }
        finally
        {
            Mouse.exit(mark);

            Components.onTheEventThread(() -> { tips.setInitialDelay(was); return null; });

            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /** Whether a tooltip of this component is on screen, in any window. */
    private static boolean tipShowingFor(java.awt.Component owner)
    {
        return Components.onTheEventThread(() ->
        {
            for (java.awt.Window each : java.awt.Window.getWindows())
            {
                if (each.isShowing() && Components.find(each, javax.swing.JToolTip.class,
                    tip -> tip.isShowing() && tip.getComponent() == owner) != null) return true;
            }

            return false;
        });
    }

    // ------------------------------------------------------------------------------------------------------------ menus

    /**
     * The editor's menu on a sensor square leads to the windows, and holds no setting of the square any more.
     *
     * MUTATION: put the maximum length item back on the menu, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsMenuHoldsTheWindowsNotTheSettings() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(square);

        JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

        for (String item : new String[] {I18n.t("autosetup.ui.menuStationSettings"),
            I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(square)),
            I18n.f("layout.ui.menuRemoveLocomotive", train), I18n.f("autolayout.ui.menuLocomotiveActive", train)})
        {
            assertNotNull(Components.withText(menu, item), "the editor's menu has no \"" + item + "\"");
        }

        for (String gone : new String[] {I18n.t("autosetup.ui.menuAutoDestination"), I18n.t("autosetup.ui.stationSectionStop"),
            I18n.t("autosetup.ui.stationSectionThrough")})
        {
            assertNull(Components.withText(menu, gone), "the editor's menu still has \"" + gone + "\"");
        }

        AbstractButton opens = Components.withText(menu, I18n.t("autosetup.ui.menuStationSettings"));

        Components.onTheEventThread(() -> { opens.doClick(); return null; });

        assertEquals(panel.stationSettingsWindow().squareForTest(), square, "the menu opened another square's window");
    }

    /**
     * On an empty station the item says it places a train; the active train can still be placed straight from the diagram's
     * menu, and Remove is there where a train stands (Adam: "it should still allow you to place the train that is active
     * directly").
     *
     * MUTATION: drop the Place item from `LayoutRightclickAutonomyMenu`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuStillPlacesTheActiveTrain() throws Exception
    {
        TileKey full = aSquareWithATrain();
        TileKey empty = anEmptyStation();

        String train = session.getLocomotiveNameAt(full);
        String active = aTrainNotPlaced();

        makeActive(active);

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu onTheTrain = AskingWindows.menuOn(ui, ui, full);

        for (String item : new String[] {I18n.f("layout.ui.menuRemoveLocomotive", train),
            I18n.f("layout.ui.menuPlaceLocomotive", active), I18n.t("autosetup.ui.menuStationSettings"),
            I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(full))})
        {
            assertTrue(AskingWindows.hasItem(onTheTrain, item), "the diagram's menu over a train has no \"" + item + "\"");
        }

        Mouse.closeMenus();

        JPopupMenu onTheEmpty = AskingWindows.menuOn(ui, ui, empty);

        for (String item : new String[] {I18n.f("layout.ui.menuPlaceLocomotive", active), placeHere(),
            I18n.t("autosetup.ui.menuStationSettings")})
        {
            assertTrue(AskingWindows.hasItem(onTheEmpty, item), "the diagram's menu over an empty station has no \"" + item + "\"");
        }

        Mouse.closeMenus();
    }

    /** Whether any item of the menu, its submenus included, is Edit <name>... for any name. */
    private static boolean offersEdit(java.awt.Container menu)
    {
        String[] around = I18n.f("autolayout.ui.menuEditLocomotive", "#TRAIN#").split("#TRAIN#", -1);

        return Components.find(menu, JMenuItem.class, item -> item.getText() != null
            && item.getText().startsWith(around[0]) && item.getText().endsWith(around[1])) != null;
    }

    /**
     * The diagram's menu over a train, as Adam drew it (FR-182) and reordered it: the station's name heads it with
     * Station Settings... under it (MT-957); behind a divider Is Active and the facing items; the destinations; Change
     * Train at..., Place, Remove and Edit <name>... ("Edit Locomotive doesn't exist in the same grouping as any of the
     * autonomy options"); Autonomy Setup; then Start and Return Home last.  No divider twice or at the end.
     *
     * MUTATION: add the Start group first again in `LayoutRightclickAutonomyMenu`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuOverATrainIsGrouped() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);
        String active = aTrainNotPlaced();

        makeActive(active);

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, full);

        java.util.List<java.util.List<String>> groups = groups(menu);

        java.util.List<String> shape = shape(menu);

        Mouse.closeMenus();

        assertEquals(groups.get(0), java.util.Arrays.asList(session.describeTile(full),
            I18n.t("autosetup.ui.menuStationSettings")), "the menu does not start with the station's name and Station"
            + " Settings...: " + groups);

        java.util.List<String> own = groups.get(1);

        assertEquals(own.get(0), said("autolayout.ui.menuLocomotiveActive", train), "Is Active does not start the"
            + " train's own group: " + groups);

        for (String item : own.subList(1, own.size()))
        {
            assertTrue(item.startsWith(said("autosetup.ui.menuChangeDirectionUnknown", null))
                || item.startsWith(said("autosetup.ui.menuChangeArrivedFromUnknown", null))
                || item.equals(said("autosetup.ui.menuTailCrossedGroup", train)), "\"" + item + "\" is among the train's"
                + " own items: " + groups);
        }

        int hand = groups.get(2).get(0).equals(said("autolayout.ui.menuSendTo", train)) ? 3 : 2;

        assertEquals(groups.subList(hand, groups.size()), java.util.Arrays.asList(
            java.util.Arrays.asList(said("autosetup.ui.menuChangeTrainAt", session.describeTile(full)),
                said("layout.ui.menuPlaceLocomotive", active), said("layout.ui.menuRemoveLocomotive", train),
                said("autolayout.ui.menuEditLocomotive", train)),
            java.util.Collections.singletonList(I18n.t("autosetup.ui.menuAutonomySetup")),
            java.util.Arrays.asList(I18n.t("autolayout.ui.menuStartAutonomy"), I18n.t("autolayout.ui.menuReturnToHome"))),
            "the hand doors, Autonomy Setup and the autonomy items are not the menu's last groups, in that order: " + groups);

        assertNoStrayDividers(shape);
    }

    /**
     * Every other state's menu, as Adam ruled it: the square's items first, under its name;
     * Autonomy Setup; Start and Return Home last (Gracefully Stop while running); the blank diagram's menu only those.
     *
     * MUTATION: put Autonomy Setup's items on the menu itself in `LayoutRightclickAutonomyMenu.addSetupMenu`, and this
     * fails.
     */
    @Test(timeOut = 180000)
    public void testTheDiagramsMenuInEveryState() throws Exception
    {
        TileKey full = aSquareWithATrain();
        TileKey empty = anEmptyStation();
        TileKey sensor = aPlainSensor();
        TileKey track = aPlainTrackSquare();

        String train = session.getLocomotiveNameAt(full);
        String active = aTrainNotPlaced();

        makeActive(active);

        AskingWindows.showThePageOf(ui, full);

        java.util.List<String> setup = java.util.Collections.singletonList(I18n.t("autosetup.ui.menuAutonomySetup"));
        java.util.List<String> atRest = java.util.Arrays.asList(I18n.t("autolayout.ui.menuStartAutonomy"),
            I18n.t("autolayout.ui.menuReturnToHome"));
        java.util.List<String> settings = java.util.Arrays.asList(I18n.t("autosetup.ui.menuStationSettings"));

        java.util.Map<String, java.util.List<java.util.List<String>>> expected = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<java.util.List<String>>> found = new java.util.LinkedHashMap<>();

        expected.put("empty station", java.util.Arrays.asList(headed(empty, settings), java.util.Arrays.asList(
            said("layout.ui.menuPlaceLocomotive", active), said("autosetup.ui.menuPlaceAnotherHere", null)), setup, atRest));
        expected.put("sensor", java.util.Arrays.asList(headed(sensor, settings), setup, atRest));
        expected.put("plain track", java.util.Arrays.asList(java.util.Collections.singletonList(
            session.getGraph().getTiles().get(track).getUserFriendlyTypeName()), setup, atRest));
        expected.put("blank", java.util.Arrays.asList(atRest));
        expected.put("running, over a train", java.util.Arrays.asList(headed(full, settings),
            java.util.Collections.singletonList(said("autolayout.ui.menuLocomotiveActive", train)), java.util.Arrays.asList(
            said("autosetup.ui.menuChangeTrainAt", session.describeTile(full)), said("autolayout.ui.menuEditLocomotive", train)),
            java.util.Collections.singletonList(I18n.t("autolayout.ui.menuStopAutonomyGracefully"))));

        for (Map.Entry<String, TileKey> each : new java.util.LinkedHashMap<String, TileKey>()
            {{ put("empty station", empty); put("sensor", sensor); put("plain track", track); }}.entrySet())
        {
            AskingWindows.showThePageOf(ui, each.getValue());

            JPopupMenu menu = AskingWindows.menuOn(ui, ui, each.getValue());

            found.put(each.getKey(), groups(menu));

            assertNoStrayDividers(shape(menu));

            Mouse.closeMenus();
        }

        found.put("blank", groups(diagramMenuOn(null)));

        AskingWindows.showThePageOf(ui, full);

        found.put("running, over a train", groups(runningMenuOn(full)));

        Mouse.closeMenus();

        assertEquals(found, expected, "the diagram's menus are not as the rulings have them");
    }

    /**
     * Every square's menu starts with its name in bold, as the sensor menu did: at rest, running and with an
     * editor open.
     *
     * MUTATION: drop the bold font in `LayoutRightclickAutonomyMenu.headline`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testEverySquaresMenuStartsWithItsNameInBold() throws Exception
    {
        java.util.List<JPopupMenu> menus = new java.util.ArrayList<>();

        for (TileKey square : new TileKey[] {aSquareWithATrain(), anEmptyStation(), aPlainSensor(), aPlainTrackSquare()})
        {
            AskingWindows.showThePageOf(ui, square);

            menus.add(AskingWindows.menuOn(ui, ui, square));

            Mouse.closeMenus();
        }

        menus.add(runningMenuOn(aSquareWithATrain()));

        Mouse.closeMenus();

        theEditorsPanel();

        menus.add(AskingWindows.menuOn(ui, ui, aSquareWithATrain()));

        Mouse.closeMenus();

        for (JPopupMenu menu : menus)
        {
            JMenuItem top = Components.onTheEventThread(() -> (JMenuItem) menu.getComponent(0));

            assertTrue(!top.isEnabled() && top.getFont().isBold(), "the menu does not start with a bold heading: "
                + shape(menu));
        }
    }

    /**
     * Plain track is headed with what it is ("Straight Track"), not its grid square, and its Autonomy Setup holds no
     * second heading (OB-451).
     *
     * MUTATION: head the menu with `session.describeTile` again in `LayoutRightclickAutonomyMenu.headline`, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testPlainTrackIsHeadedWithWhatItIs() throws Exception
    {
        TileKey track = aPlainTrackSquare();

        AskingWindows.showThePageOf(ui, track);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, track);

        java.util.List<String> shape = shape(menu);
        java.util.List<String> setup = shape(setupMenuOf(menu));

        Mouse.closeMenus();

        String kind = session.getGraph().getTiles().get(track).getUserFriendlyTypeName();

        assertEquals(shape.get(0), "(heading) " + kind, "plain track is not headed with what it is: " + shape);

        assertFalse(setup.get(0).startsWith("(heading) "), "Autonomy Setup starts with a heading of its own: " + setup);
    }

    /**
     * Segment Length..., Bulk Tools and Open the Full Editor... are one level down under Autonomy Setup on every square
     * in that order with a divider between each, Bulk Tools as the editor builds it (OB-448's groups), and each
     * still does what it did: Segment Length asks for the length, Open the Full Editor opens it.
     *
     * MUTATION: put Autonomy Setup's items on the menu itself in `LayoutRightclickAutonomyMenu.addSetupMenu`, and this
     * fails.
     */
    @Test(timeOut = 180000)
    public void testTheSetupItemsAreOneLevelDownAndStillWork() throws Exception
    {
        String length = I18n.t("autosetup.ui.menuSetLength");
        String bulk = I18n.t("autosetup.ui.menuBulkTools");
        String full = I18n.t("autosetup.ui.menuOpenFullEditor");

        for (TileKey square : new TileKey[] {aSquareWithATrain(), anEmptyStation(), aPlainSensor(), aPlainTrackSquare()})
        {
            AskingWindows.showThePageOf(ui, square);

            JPopupMenu menu = AskingWindows.menuOn(ui, ui, square);

            java.util.List<String> top = shape(menu);
            JPopupMenu setup = setupMenuOf(menu);
            java.util.List<String> inside = shape(setup);

            Mouse.closeMenus();

            for (String item : new String[] {length, bulk, full})
            {
                assertFalse(top.contains(item), "\"" + item + "\" is at the top of the menu on " + square + ": " + top);
            }

            assertEquals(inside.subList(inside.size() - 4, inside.size()), java.util.Arrays.asList("----", bulk, "----", full),
                "Autonomy Setup does not end with Bulk Tools and Open the Full Editor... behind dividers on " + square + ": "
                + inside);

            assertTrue(inside.contains(length), "Autonomy Setup has no Segment Length... on " + square + ": " + inside);

            javax.swing.JPopupMenu editors = Components.onTheEventThread(() -> ui.buildAutonomyTileMenu(square));

            assertEquals(shape(((javax.swing.JMenu) Components.withText(setup, bulk)).getPopupMenu()),
                shape(((javax.swing.JMenu) Components.withText(editors, bulk)).getPopupMenu()),
                "Bulk Tools on the diagram is not the editor's on " + square);
        }

        TileKey sensor = aPlainSensor();

        int was = session.getStore().getTileLength(sensor);

        java.util.List<support.Answering.Question> asked = AskingWindows.choose((JMenuItem) Components.withText(
            setupMenuOf(AskingWindows.menuOn(ui, ui, sensor)), length), message -> support.Answering.pressAt(2), 1);

        assertEquals(asked.get(0).title, length, "Segment Length... under Autonomy Setup did not ask for the length");
        assertEquals(session.getStore().getTileLength(sensor), was, "precondition: Cancel wrote the length");

        Mouse.choose((JMenuItem) Components.withText(setupMenuOf(AskingWindows.menuOn(ui, ui, sensor)), full));

        try
        {
            assertTrue(Components.waitFor(() -> ui.isLayoutEditorOpen(), 30000),
                "Open the Full Editor... under Autonomy Setup opened no editor");
        }
        finally
        {
            support.EditorsOpen.closeAll();
        }
    }

    /**
     * The destinations sort as they are read, in the top-level list and in More Destinations: numbers counted as numbers
     * (ParkingTrack4 before ParkingTrack10), capitals not changing the order, by the rule Why not Moving? sorts by
     * (OB-450, MT-874).
     *
     * MUTATION: have `PathOptions.sortAsRead` sort by `Edge.pathToString` alone, and this fails on ParkingTrack10.
     */
    @Test(timeOut = 120000)
    public void testTheDestinationsSortAsRead() throws Exception
    {
        boolean numbered = false;

        for (TileKey square : squaresWithTheirTrains())
        {
            AskingWindows.showThePageOf(ui, square);

            JPopupMenu menu = AskingWindows.menuOn(ui, ui, square);

            java.util.List<java.util.List<String>> lists = new java.util.ArrayList<>();

            lists.add(destinations(shape(menu)));

            AbstractButton more = Components.withText(menu, I18n.t("autolayout.ui.menuMoreDestinations"));

            if (more != null) lists.add(destinations(shape(((javax.swing.JMenu) more).getPopupMenu())));

            Mouse.closeMenus();

            for (java.util.List<String> list : lists)
            {
                java.util.List<String> sorted = new java.util.ArrayList<>(list);

                sorted.sort(org.traincontrol.util.Util::compareAsRead);

                assertEquals(list, sorted, "the destinations of " + session.getLocomotiveNameAt(square) + " are not in the order they are read");

                for (String name : list) numbered |= name.matches(".*\\d\\d$");
            }
        }

        assertTrue(numbered, "precondition: no train on " + PAGE + " is offered a place whose name ends in 10 or more");
    }

    /**
     * Change Direction is left off the diagram's menu where the track allows one facing, and Change Arrived From where it
     * reaches the square from one side; the autonomy editor's own menu still offers them grayed with the reason.
     *
     * MUTATION: drop the `editor.menuOnly` returns in `EditorTileMenus.changeDirectionItem` and
     * `changeArrivedFromItem`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testWhatTheTrackNeverAllowsIsLeftOffNotGrayed() throws Exception
    {
        String turn = said("autosetup.ui.menuChangeDirectionUnknown", null);
        String arrived = said("autosetup.ui.menuChangeArrivedFromUnknown", null);

        java.util.Map<TileKey, java.util.List<String>> onTheDiagram = new java.util.LinkedHashMap<>();

        for (Map.Entry<TileKey, String> each : session.trainsWhereTheyStand().entrySet())
        {
            if (!PAGE.equals(each.getKey().getPage()) || !session.getStore().isStation(each.getKey())) continue;

            AskingWindows.showThePageOf(ui, each.getKey());

            onTheDiagram.put(each.getKey(), shape(AskingWindows.menuOn(ui, ui, each.getKey())));

            Mouse.closeMenus();
        }

        AutonomyEditorPanel panel = theEditorsPanel();

        int left = 0;

        for (Map.Entry<TileKey, java.util.List<String>> each : onTheDiagram.entrySet())
        {
            JPopupMenu editors = Components.onTheEventThread(() -> panel.buildTileMenu(each.getKey(), null));

            for (String[] item : new String[][] {{turn, "autosetup.ui.hintFacingOnlyOne"},
                {arrived, "autosetup.ui.hintArrivedFromOnlyOne"}})
            {
                JMenuItem there = Components.find(editors, JMenuItem.class, i -> i.getText() != null
                    && i.getText().startsWith(item[0]));

                if (there == null) continue;

                boolean never = !there.isEnabled() && plain(there.getToolTipText()).equals(plain(I18n.t(item[1])));

                boolean onIt = false;

                for (String row : each.getValue()) onIt |= row.startsWith(item[0]) || row.startsWith("(heading) " + item[0]);

                assertEquals(onIt, !never, "on " + each.getKey() + ", \"" + there.getText() + "\" is " + (never
                    ? "grayed in the editor for a reason that never passes, and still on the diagram's menu"
                    : "offered in the editor and missing from the diagram's menu") + ": " + each.getValue());

                if (never) left++;
            }
        }

        assertTrue(left > 0, "precondition: no train on " + PAGE + " stands where the track allows one facing or side");
    }

    /**
     * The two Place rows say which is which: with a locomotive selected, "Place <name> Here" and "Place Another
     * Locomotive Here...", which opens the Train tab; with none, "Place a Locomotive Here..." alone.  Over a standing
     * train, "Place <name> Here" too.
     *
     * MUTATION: drop the `setText` of Place Another Locomotive Here... in `LayoutRightclickAutonomyMenu.addHandDoors`,
     * and this fails.
     */
    @Test(timeOut = 120000)
    public void testThePlaceRowsSayWhichIsWhich() throws Exception
    {
        assertEquals(java.util.ResourceBundle.getBundle("org.traincontrol.resources.messages", java.util.Locale.ROOT)
            .getString("layout.ui.menuPlaceLocomotive"), "Place {0} Here", "the row placing the selected locomotive does"
            + " not say Here");

        TileKey empty = anEmptyStation();
        TileKey full = aSquareWithATrain();

        String active = aTrainNotPlaced();

        makeActive(active);

        AskingWindows.showThePageOf(ui, empty);

        java.util.List<String> selected = shape(AskingWindows.menuOn(ui, ui, empty));

        Mouse.closeMenus();

        java.util.List<String> overATrain = shape(AskingWindows.menuOn(ui, ui, full));

        Mouse.closeMenus();

        assertTrue(selected.containsAll(java.util.Arrays.asList(said("layout.ui.menuPlaceLocomotive", active),
            I18n.t("autosetup.ui.menuPlaceAnotherHere"))) && !selected.contains(I18n.t("autosetup.ui.menuPlaceHere")),
            "with a locomotive selected, the empty station's Place rows do not say which is which: " + selected);

        assertTrue(overATrain.contains(said("layout.ui.menuPlaceLocomotive", active)), "over a standing train, the"
            + " selected locomotive is not placed Here: " + overATrain);

        Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, ui, empty), I18n.t("autosetup.ui.menuPlaceAnotherHere")));

        Components.settle();

        StationSettingsWindow window = StationDoors.showing(StationSettingsWindow.class);

        assertTrue(window != null && StationDoors.showsTheTrainTab(window), "Place Another Locomotive Here... did not"
            + " open the Train tab");

        Components.onTheEventThread(() -> { window.dispose(); return null; });

        nothingIsSelected();

        java.util.List<String> none = shape(AskingWindows.menuOn(ui, ui, empty));

        Mouse.closeMenus();

        assertTrue(none.contains(I18n.t("autosetup.ui.menuPlaceHere")) && !none.contains(I18n.t(
            "autosetup.ui.menuPlaceAnotherHere")), "with no locomotive selected, the row is not Place a Locomotive Here...: "
            + none);
    }

    /**
     * With an editor open, every square's menu is its name, a short gray line and a live Go to the Opened Editor, the
     * menu bar's item, which brings the editor back.
     *
     * MUTATION: leave the Go to the Opened Editor item off in `LayoutRightclickAutonomyMenu`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testWithAnEditorOpenTheMenuLeadsToIt() throws Exception
    {
        TileKey full = aSquareWithATrain();

        theEditorsPanel();

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, full);

        java.util.List<String> shape = shape(menu);

        assertEquals(shape, java.util.Arrays.asList("(heading) " + session.describeTile(full), "----",
            "(heading) " + I18n.t("autosetup.ui.menuEditorIsOpen"), I18n.t("autosetup.ui.menuGoToOpenEditor")),
            "with an editor open, the diagram's menu is not its name, the line and the way to the editor");

        Components.onTheEventThread(() -> { editor.setVisible(false); return null; });

        Mouse.choose(AskingWindows.itemOf(menu, I18n.t("autosetup.ui.menuGoToOpenEditor")));

        assertTrue(Components.waitFor(() -> editor.isVisible(), 10000), "Go to the Opened Editor did not bring the editor"
            + " back");
    }

    /** The squares of this page where the setup's own train stands. */
    private static java.util.List<TileKey> squaresWithTheirTrains()
    {
        java.util.List<TileKey> out = new java.util.ArrayList<>();

        for (Map.Entry<TileKey, String> each : session.trainsWhereTheyStand().entrySet())
        {
            if (PAGE.equals(each.getKey().getPage()) && each.getValue().equals(session.getLocomotiveNameAt(each.getKey())))
            {
                out.add(each.getKey());
            }
        }

        return out;
    }

    /** A straight on this page that is not a sensor, for plain track's menu. */
    private static TileKey aPlainTrackSquare()
    {
        for (Map.Entry<TileKey, org.traincontrol.base.LayoutDiagramComponent> each : session.getGraph().getTiles().entrySet())
        {
            if (PAGE.equals(each.getKey().getPage()) && !session.getReducer().getPoints().containsKey(each.getKey())
                && each.getValue().getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT)
            {
                return each.getKey();
            }
        }

        throw new AssertionError("precondition: no plain straight on " + PAGE);
    }

    /** The square's name, then these items, as the first group of its menu. */
    private static java.util.List<String> headed(TileKey square, java.util.List<String> items)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        out.add(session.describeTile(square));
        out.addAll(items);

        return out;
    }

    /** The diagram's menu built as a right-click builds it, heading and all, for a square or none. */
    private static JPopupMenu diagramMenuOn(TileKey square) throws Exception
    {
        JPopupMenu menu = support.StationDoors.diagramMenu(ui, square, null);

        java.lang.reflect.Method headline = Reflect.method(menu.getClass(), "headline");

        Components.onTheEventThread(() -> headline.invoke(menu));

        return menu;
    }

    /** Autonomy Setup's own menu. */
    private static JPopupMenu setupMenuOf(JPopupMenu menu)
    {
        AbstractButton setup = Components.withText(menu, I18n.t("autosetup.ui.menuAutonomySetup"));

        assertNotNull(setup, "the menu has no Autonomy Setup: " + shape(menu));

        return ((javax.swing.JMenu) setup).getPopupMenu();
    }

    /** The place names of a shape's "-> " rows. */
    private static java.util.List<String> destinations(java.util.List<String> shape)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        for (String row : shape) if (row.startsWith("-> ")) out.add(row.substring(3));

        return out;
    }

    /** A tooltip's words, its HTML wrapping taken off. */
    private static String plain(String tip)
    {
        return tip == null ? "" : tip.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private static void assertNoStrayDividers(java.util.List<String> shape)
    {
        for (int at = 1; at < shape.size(); at++)
        {
            assertFalse("----".equals(shape.get(at)) && "----".equals(shape.get(at - 1)), "two dividers in a row: " + shape);
        }

        assertFalse("----".equals(shape.get(0)) || "----".equals(shape.get(shape.size() - 1)), "the menu starts or ends in"
            + " a divider: " + shape);
    }

    /**
     * Over a train, the destinations the diagram's menu offers are headed "Send <name> To:", not the train's bare name
     * (Adam: "change the gray <loc name> heading to Send <locname> To: if there are ANY destination options below it");
     * the heading is there only with a destination under it.
     *
     * MUTATION: head the destinations with `locomotive.getName()` again in `LayoutRightclickAutonomyMenu.addDestinations`,
     * and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheDestinationsAreHeadedSendTo() throws Exception
    {
        java.util.List<String> shape = null;
        String train = null;

        // A train with somewhere to go: under either heading, the new or the bare name
        for (TileKey square : squaresWithTheirTrains())
        {
            AskingWindows.showThePageOf(ui, square);

            java.util.List<String> menu = shape(AskingWindows.menuOn(ui, ui, square));

            Mouse.closeMenus();

            if (menu.contains("(heading) " + I18n.f("autolayout.ui.menuSendTo", session.getLocomotiveNameAt(square)))
                || menu.contains("(heading) " + session.getLocomotiveNameAt(square)))
            {
                shape = menu;
                train = session.getLocomotiveNameAt(square);

                break;
            }
        }

        assertNotNull(shape, "precondition: no train on " + PAGE + " has a destination on its menu");

        int heading = shape.indexOf("(heading) " + I18n.f("autolayout.ui.menuSendTo", train));

        assertTrue(heading >= 0, "the destinations are not headed Send " + train + " To: " + shape);

        assertFalse(shape.contains("(heading) " + train), "the train's bare name still heads something: " + shape);

        String first = shape.get(heading + 1);

        assertTrue(!first.startsWith("(heading)") && !"----".equals(first), "no destination follows the heading: " + shape);
    }

    /**
     * Over an empty station Station Settings... is the station's own item, and the two ways to put a train there are a
     * group of their own behind a divider, on the track diagram and in the autonomy editor (FR-182, as Adam
     * reordered it).
     *
     * MUTATION: put the editor's Station Settings back below Place, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAnEmptyStationsMenusAreGrouped() throws Exception
    {
        TileKey empty = anEmptyStation();

        String active = aTrainNotPlaced();

        makeActive(active);

        AskingWindows.showThePageOf(ui, empty);

        java.util.List<java.util.List<String>> diagram = groups(AskingWindows.menuOn(ui, ui, empty));

        Mouse.closeMenus();

        AutonomyEditorPanel panel = theEditorsPanel();

        java.util.List<java.util.List<String>> inTheEditor = groups(Components.onTheEventThread(()
            -> panel.buildTileMenu(empty, null)));

        for (java.util.List<java.util.List<String>> groups : java.util.Arrays.asList(diagram, inTheEditor))
        {
            java.util.List<String> hand = groupWith(groups, said("autosetup.ui.menuPlaceAnotherHere", null));

            int place = hand.indexOf(said("layout.ui.menuPlaceLocomotive", active));

            assertEquals(hand.subList(Math.max(0, place), hand.size()), java.util.Arrays.asList(
                said("layout.ui.menuPlaceLocomotive", active), said("autosetup.ui.menuPlaceAnotherHere", null)),
                "Place and Place Another Locomotive Here... are not together at the end of their group: " + groups);

            java.util.List<String> before = groups.get(groups.indexOf(hand) - 1);

            assertEquals(before.get(before.size() - 1), I18n.t("autosetup.ui.menuStationSettings"),
                "Station Settings does not stand just before the Place items' divider: " + groups);
        }
    }

    /**
     * On the track diagram Station Settings... is the first item under the station's name, with a train standing there
     * or not, above Is Active (MT-957).
     *
     * MUTATION: add the Is Active item before Station Settings in `LayoutRightclickAutonomyMenu`'s station block, and
     * the station with a train fails.
     */
    @Test(timeOut = 120000)
    public void testStationSettingsIsFirstUnderTheNameOnTheDiagram() throws Exception
    {
        for (TileKey square : new TileKey[] {aSquareWithATrain(), anEmptyStation()})
        {
            java.util.List<String> shape = shape(diagramMenuOn(square));

            int settings = shape.indexOf(I18n.t("autosetup.ui.menuStationSettings"));

            assertTrue(settings > 0, "precondition: no Station Settings on " + square + ": " + shape);
            assertTrue(settings == 1 && shape.get(0).startsWith("(heading) "),
                "Station Settings is not the first item under the name on " + square + ": " + shape);
        }
    }

    /**
     * The autonomy editor's station menu is grouped the same way, with no Edit (FR-182 as Adam reordered it): Station
     * Settings... alone right under the square's name (OB-437), the train's own items, then Change Train at..., Place
     * and Remove.
     *
     * MUTATION: put Change Train at... back with the train's own items in `EditorTileMenus.buildTileMenu`, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsStationMenuIsGrouped() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);
        String active = aTrainNotPlaced();

        makeActive(active);

        java.util.List<java.util.List<String>> groups = groups(Components.onTheEventThread(()
            -> panel.buildTileMenu(full, null)));

        java.util.List<String> own = groupWith(groups, said("autolayout.ui.menuLocomotiveActive", train));

        assertEquals(own.get(0), said("autolayout.ui.menuLocomotiveActive", train), "Is Active is not the first of the"
            + " train's own items: " + groups);

        assertEquals(groups.get(1), java.util.Collections.singletonList(I18n.t("autosetup.ui.menuStationSettings")),
            "Station Settings is not alone in the group right under the square's name: " + groups);

        assertTrue(own.get(own.size() - 1).startsWith(said("autosetup.ui.menuChangeArrivedFromUnknown", null)),
            "Change Arrived From is not the last of the train's own items: " + groups);

        assertEquals(groups.get(groups.indexOf(own) + 1), java.util.Arrays.asList(
            said("autosetup.ui.menuChangeTrainAt", session.describeTile(full)),
            said("layout.ui.menuPlaceLocomotive", active), said("layout.ui.menuRemoveLocomotive", train)),
            "Change Train at, Place and Remove do not follow the train's own items as a group: " + groups);
    }

    /**
     * The editor's menu starts with the square's name and a divider on an empty station, a plain sensor and a station
     * with a train (FR-182), and no divider stands twice in a row or at the end.
     *
     * MUTATION: leave `AutonomyEditorPanel.tidy` off the end of `EditorTileMenus.buildTileMenu`, and the plain sensor's
     * empty hand-door group leaves two dividers in a row and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsMenuStartsWithItsHeading() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        for (final TileKey square : new TileKey[] {anEmptyStation(), aPlainSensor(), aSquareWithATrain()})
        {
            final JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

            java.util.List<String> shape = shape(menu);

            String name = session.getStore().getPointName(square);

            assertTrue(shape.size() > 2 && shape.get(0).startsWith("(heading) ") && "----".equals(shape.get(1)),
                "the editor's menu on " + square + " does not start with its heading and a divider: " + shape);

            if (name != null && !name.trim().isEmpty())
            {
                assertEquals(shape.get(0), "(heading) " + name.trim(), "the editor's menu on " + square + " is not headed"
                    + " with its name: " + shape);
            }

            for (int at = 1; at < shape.size(); at++)
            {
                assertFalse("----".equals(shape.get(at)) && "----".equals(shape.get(at - 1)), "the editor's menu on "
                    + square + " has two dividers in a row: " + shape);
            }

            assertFalse("----".equals(shape.get(shape.size() - 1)), "the editor's menu on " + square + " ends in a"
                + " divider: " + shape);
        }
    }

    /**
     * On the track diagram, a station with every arrival barred has no copy that is a destination, and its menu is headed
     * all the same, with Station Settings... first under the name (FR-195: "move the Station Settings item to the top of
     * its divider section") and Place a Locomotive Here... among the hand doors.
     *
     * MUTATION: add the train item before Station Settings in `LayoutRightclickAutonomyMenu.addHandDoors`'s caller, and
     * this fails.
     */
    @Test(timeOut = 120000)
    public void testStationSettingsTopsAnUnheadedStationsGroupOnTheDiagram() throws Exception
    {
        TileKey sensor = anEmptyStation();

        Components.onTheEventThread(() ->
        {
            session.setBarredArrivals(sensor, new java.util.HashSet<>(session.arrivalSides(sensor)));
            ui.rebuildRunningLayoutFromSetup(false, new java.util.LinkedHashSet<>(session.placementsAutonomyWillWrite().values()));

            return null;
        });

        Components.settle();

        AskingWindows.showThePageOf(ui, sensor);

        java.util.List<java.util.List<String>> groups = groups(AskingWindows.menuOn(ui, ui, sensor));

        Mouse.closeMenus();

        assertEquals(groups.get(0), headed(sensor, java.util.Collections.singletonList(
            I18n.t("autosetup.ui.menuStationSettings"))), "Station Settings is not alone under the name: " + groups);
        assertTrue(groups.get(1).contains(I18n.t("autosetup.ui.menuPlaceHere")), "Place a Locomotive Here... is not in the"
            + " group after Station Settings: " + groups);
    }

    /**
     * The train popup's row is "Arrived", not "Arrived from" (FR-182): its choices already say "From the East (right)",
     * so the label must not end in the word they begin with, in any language.
     *
     * MUTATION: put "Arrived from" back, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheArrivedRowDoesNotSayFromTwice() throws Exception
    {
        for (String language : new String[] {"en", "da", "de", "es", "fr", "it", "nl", "pl"})
        {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org.traincontrol.resources.messages",
                new java.util.Locale(language));

            String[] label = bundle.getString("autosetup.ui.trainArrived").trim().split("\\s+");

            for (String side : new String[] {"N", "E", "S", "W"})
            {
                String choice = bundle.getString("autolayout.ui.side" + side).trim().split("\\s+")[0];

                assertFalse(label[label.length - 1].equalsIgnoreCase(choice), language + ": the row says \""
                    + bundle.getString("autosetup.ui.trainArrived") + "\" before a choice that says \""
                    + bundle.getString("autolayout.ui.side" + side) + "\"");
            }
        }
    }

    /** The menu's parts in order: "----" for a divider, "(heading) " before a grayed item's text. */
    private static java.util.List<String> shape(final JPopupMenu menu)
    {
        return Components.onTheEventThread(() ->
        {
            java.util.List<String> out = new java.util.ArrayList<>();

            for (java.awt.Component part : menu.getComponents())
            {
                out.add(part instanceof javax.swing.JSeparator ? "----" : part instanceof JMenuItem
                    ? (part.isEnabled() ? "" : "(heading) ") + ((JMenuItem) part).getText() : "?");
            }

            return out;
        });
    }

    /** The menu's own items, by text, in the groups its dividers make; empty groups left out. */
    private static java.util.List<java.util.List<String>> groups(final JPopupMenu menu)
    {
        return Components.onTheEventThread(() ->
        {
            java.util.List<java.util.List<String>> out = new java.util.ArrayList<>();

            java.util.List<String> group = new java.util.ArrayList<>();

            for (java.awt.Component part : menu.getComponents())
            {
                if (part instanceof javax.swing.JSeparator && !group.isEmpty())
                {
                    out.add(group);

                    group = new java.util.ArrayList<>();
                }
                else if (part instanceof JMenuItem)
                {
                    group.add(((JMenuItem) part).getText());
                }
            }

            if (!group.isEmpty()) out.add(group);

            return out;
        });
    }

    private static java.util.List<String> groupWith(java.util.List<java.util.List<String>> groups, String item)
    {
        for (java.util.List<String> group : groups) if (group.contains(item)) return group;

        throw new AssertionError("the menu has no \"" + item + "\": " + groups);
    }

    /** The message, or a line no menu says where this build's bundle has no such key. */
    private static String said(String key, String name)
    {
        try
        {
            return name == null ? I18n.t(key) : I18n.f(key, name);
        }
        catch (java.util.MissingResourceException none)
        {
            return "(no " + key + ")";
        }
    }

    /** The empty station's row that opens the Train tab: it says Another beside Place <selected> Here. */
    private static String placeHere()
    {
        return I18n.t(ui.getActiveLoc() == null ? "autosetup.ui.menuPlaceHere" : "autosetup.ui.menuPlaceAnotherHere");
    }

    /** Makes this locomotive the active one, as choosing it on a key does; mapping it again would take it off. */
    private static void makeActive(String name)
    {
        if (ui.getActiveLoc() == null || !name.equals(ui.getActiveLoc().getName())) ui.mapLocToCurrentButton(name);

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() != null && name.equals(ui.getActiveLoc().getName()), 10000),
            "precondition: " + name + " could not be made the active locomotive");
    }

    /**
     * The diagram's menu over a train always offers Edit Locomotive..., which opens that train's Locomotive Settings on
     * the Autonomy tab, beside Place for the selected locomotive and the train window (Adam: "there should always be an
     * edit loc, place current (if selected and different), and place new option").
     *
     * MUTATION: have the item open the Locomotive tab, or open the train window as the old dialog's item did, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuOverATrainOpensItsSettings() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        makeActive(aTrainNotPlaced());

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, full);

        for (String item : new String[] {I18n.f("autolayout.ui.menuEditLocomotive", train),
            I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName()),
            I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(full))})
        {
            assertTrue(AskingWindows.hasItem(menu, item), "the diagram's menu over a train has no \"" + item + "\"");
        }

        Mouse.choose(AskingWindows.itemOf(menu, I18n.f("autolayout.ui.menuEditLocomotive", train)));

        Components.settle();

        LocomotiveSettingsFrame frame = StationDoors.showing(LocomotiveSettingsFrame.class);

        try
        {
            assertNotNull(frame, "Edit Locomotive... opened no Locomotive Settings");
            assertEquals(frame.getLocomotive(), model.getLocByName(train), "Edit Locomotive... opened another locomotive");
            assertEquals(support.SettingsWindow.tabShowing(), LocomotiveSettingsFrame.Tab.AUTONOMY.ordinal(),
                "Edit Locomotive... did not open the Autonomy tab");
        }
        finally
        {
            support.SettingsWindow.closeIfOpen();
        }
    }

    /**
     * During a run the diagram's menu over a train still offers Edit Locomotive... (Adam: "there should always be an edit
     * loc"), on the running menu and on the menu while a run holds the setup, and the window opens locked ("open
     * locked").
     *
     * MUTATION: leave `editLocomotiveItem` off the running menu, and this fails.
     */
    @Test(timeOut = 120000)
    public void testDuringARunTheDiagramsMenuStillEditsTheTrain() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu running = runningMenuOn(full);

        assertTrue(AskingWindows.hasItem(running, I18n.f("autolayout.ui.menuEditLocomotive", train)),
            "the running menu over a train has no Edit Locomotive...");

        Mouse.closeMenus();

        model.getAutoLayout().setStagingInProgress(true);

        try
        {
            assertTrue(ui.aRunHoldsTheSetup(), "precondition: no run holds the setup");

            JPopupMenu held = AskingWindows.menuOn(ui, ui, full);

            Mouse.choose(AskingWindows.itemOf(held, I18n.f("autolayout.ui.menuEditLocomotive", train)));

            Components.settle();

            LocomotiveSettingsFrame frame = StationDoors.showing(LocomotiveSettingsFrame.class);

            assertNotNull(frame, "Edit Locomotive... during a run opened no Locomotive Settings");
            assertEquals(frame.getLocomotive(), model.getLocByName(train), "Edit Locomotive... opened another locomotive");

            support.SettingsWindow.assertLocked("the diagram's Edit Locomotive... during a run",
                I18n.t("loc.ui.settingsAutonomyStrip"));
        }
        finally
        {
            model.getAutoLayout().setStagingInProgress(false);

            support.SettingsWindow.closeIfOpen();
        }
    }

    /** The diagram's menu over the square while autonomy runs, its run flag set and put back around the right-click. */
    private static JPopupMenu runningMenuOn(TileKey square) throws Exception
    {
        final java.lang.reflect.Field staging = Reflect.field(TrainControlUI.class, "stagingFlowActive");

        try
        {
            Components.onTheEventThread(() -> { staging.set(ui, true); return null; });

            JPopupMenu running = AskingWindows.menuOn(ui, ui, square);

            assertTrue(AskingWindows.hasItem(running, I18n.t("autolayout.ui.menuStopAutonomyGracefully")),
                "precondition: this is not the running menu");

            return running;
        }
        finally
        {
            Components.onTheEventThread(() -> { staging.set(ui, false); return null; });
        }
    }

    /**
     * On the menu shown while autonomy runs, Edit <name>... is never in a group with the autonomy items (FR-182): not
     * with Stop or Is Active.  Change Train at... is a hand door beside it, as on the menu at rest.
     *
     * MUTATION: drop the divider before the hand doors in `LayoutRightclickAutonomyMenu`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testDuringARunEditIsApartFromTheAutonomyItems() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        AskingWindows.showThePageOf(ui, full);

        java.util.List<java.util.List<String>> groups = groups(runningMenuOn(full));

        Mouse.closeMenus();

        java.util.List<String> edit = groupWith(groups, said("autolayout.ui.menuEditLocomotive", train));

        for (String autonomy : new String[] {I18n.t("autolayout.ui.menuStopAutonomyGracefully"),
            said("autolayout.ui.menuLocomotiveActive", train)})
        {
            assertFalse(edit.contains(autonomy), "Edit is in a group with \"" + autonomy + "\" on the running menu: "
                + groups);
        }
    }

    /**
     * The menu while autonomy runs is ordered as the menu at rest is: the station's name heading Station Settings... and
     * Is Active, then Change Train at... and Edit <name>... behind a divider, Gracefully Stop last.  The items both
     * menus have stand in the same order and the same groups.
     *
     * MUTATION: put Station Settings... back behind a divider of its own at the end of the running menu, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheRunningMenuIsOrderedAsTheMenuAtRest() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        AskingWindows.showThePageOf(ui, full);

        java.util.List<java.util.List<String>> atRest = groups(AskingWindows.menuOn(ui, ui, full));

        Mouse.closeMenus();

        JPopupMenu runningMenu = runningMenuOn(full);

        java.util.List<String> shape = shape(runningMenu);
        java.util.List<java.util.List<String>> running = groups(runningMenu);

        Mouse.closeMenus();

        assertEquals(shape.get(shape.size() - 1), I18n.t("autolayout.ui.menuStopAutonomyGracefully"), "Gracefully Stop"
            + " does not end the running menu: " + shape);

        java.util.Set<String> both = new java.util.LinkedHashSet<>();

        for (java.util.List<String> group : atRest) both.addAll(group);

        java.util.Set<String> inRunning = new java.util.HashSet<>();

        for (java.util.List<String> group : running) inRunning.addAll(group);

        both.retainAll(inRunning);

        for (String item : new String[] {said("autolayout.ui.menuLocomotiveActive", train),
            I18n.t("autosetup.ui.menuStationSettings"), said("autosetup.ui.menuChangeTrainAt", session.describeTile(full)),
            said("autolayout.ui.menuEditLocomotive", train)})
        {
            assertTrue(both.contains(item), "precondition: \"" + item + "\" is not on both menus: " + atRest + " and "
                + running);
        }

        assertEquals(only(running, both), only(atRest, both), "the running menu's order or groups differ from the menu"
            + " at rest for the items both have");
    }

    /**
     * While autonomy runs, an empty station's menu offers no way to place a train there: neither Place a Locomotive
     * Here... nor Place <the active locomotive>.
     *
     * MUTATION: drop `busy && locomotive == null` from the train window's line in `LayoutRightclickAutonomyMenu`, and
     * this fails.
     */
    @Test
    public void testTheRunningMenuPlacesNothingOnAnEmptyStation() throws Exception
    {
        TileKey empty = anEmptyStation();

        AskingWindows.showThePageOf(ui, empty);

        JPopupMenu running = runningMenuOn(empty);

        Mouse.closeMenus();

        assertFalse(AskingWindows.hasItem(running, I18n.t("autosetup.ui.menuPlaceHere"))
            || AskingWindows.hasItem(running, I18n.t("autosetup.ui.menuPlaceAnotherHere")),
            "the running menu over an empty station offers Place a Locomotive Here...");

        if (ui.getActiveLoc() != null)
        {
            assertFalse(AskingWindows.hasItem(running, I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName())),
                "the running menu over an empty station offers to place the active locomotive");
        }
    }

    /**
     * The diagram's menu offers Change Direction only over a train on the railway: lifted off, its station's menu has
     * none, though the setup still records it there.
     *
     * MUTATION: drop the `trainOnTheRailway` check from `TrainControlUI.addAutonomyFacingItems`, and this fails.
     */
    @Test
    public void testTheDiagramsMenuTurnsOnlyATrainOnTheRailway() throws Exception
    {
        String turn = I18n.t("autosetup.ui.menuChangeDirectionUnknown");

        TileKey full = null;

        // One the train can turn on: where the track allows one facing the row is left off
        for (Map.Entry<TileKey, String> each : session.trainsWhereTheyStand().entrySet())
        {
            if (full != null || !PAGE.equals(each.getKey().getPage()) || !session.getStore().isStation(each.getKey())) continue;

            AskingWindows.showThePageOf(ui, each.getKey());

            if (offers(AskingWindows.menuOn(ui, ui, each.getKey()), turn)) full = each.getKey();

            Mouse.closeMenus();
        }

        assertNotNull(full, "precondition: no train on " + PAGE + " is offered Change Direction");

        String train = session.getLocomotiveNameAt(full);

        java.util.List<org.traincontrol.automation.Point> lifted = new java.util.ArrayList<>();
        Locomotive loc = null;

        try
        {
            for (org.traincontrol.automation.Point p : model.getAutoLayout().getPoints())
            {
                if (p.getCurrentLocomotive() != null && train.equals(p.getCurrentLocomotive().getName())) lifted.add(p);
            }

            loc = lifted.get(0).getCurrentLocomotive();

            for (org.traincontrol.automation.Point p : lifted) p.setLocomotive(null);

            assertEquals(session.getLocomotiveNameAt(full), train, "precondition: the setup no longer records " + train);
            assertFalse(offers(AskingWindows.menuOn(ui, ui, full), turn),
                "Change Direction is offered over a station the railway has no train on");

        }
        finally
        {
            Mouse.closeMenus();

            for (org.traincontrol.automation.Point p : lifted) p.setLocomotive(loc);
        }
    }

    /** Whether any item of the menu, its submenus included, starts with this text. */
    private static boolean offers(JPopupMenu menu, String start)
    {
        for (javax.swing.JMenuItem item : Components.all(menu, javax.swing.JMenuItem.class, null))
        {
            if (item.getText() != null && item.getText().startsWith(start)) return true;
        }

        return false;
    }

    /** The menu's groups with only these items, empty groups left out. */
    private static java.util.List<java.util.List<String>> only(java.util.List<java.util.List<String>> groups,
        java.util.Set<String> items)
    {
        java.util.List<java.util.List<String>> out = new java.util.ArrayList<>();

        for (java.util.List<String> group : groups)
        {
            java.util.List<String> kept = new java.util.ArrayList<>(group);

            kept.retainAll(items);

            if (!kept.isEmpty()) out.add(kept);
        }

        return out;
    }

    /**
     * Place is offered for the selected locomotive only when it is not the train standing there; Edit Locomotive... and
     * the train window stay.
     *
     * MUTATION: drop `!ui.getActiveLoc().equals(locomotive)` from the Place item's test, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuOffersPlaceOnlyForAnotherLocomotive() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        makeActive(train);

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, full);

        assertFalse(AskingWindows.hasItem(menu, I18n.f("layout.ui.menuPlaceLocomotive", train)),
            "the diagram's menu offers to place the train already standing there");
        assertTrue(AskingWindows.hasItem(menu, I18n.f("autolayout.ui.menuEditLocomotive", train)),
            "the diagram's menu lost Edit Locomotive... with its own train selected");
        assertTrue(AskingWindows.hasItem(menu, I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(full))),
            "the diagram's menu lost the train window with its own train selected");

        Mouse.closeMenus();
    }

    /**
     * On an empty station there is no train to edit: no Edit Locomotive..., while Place for the selected locomotive and
     * Place a Locomotive Here... stay.
     *
     * MUTATION: offer the item whether or not a train stands there, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuOverAnEmptyStationHasNoEditLocomotive() throws Exception
    {
        TileKey empty = anEmptyStation();

        makeActive(aTrainNotPlaced());

        AskingWindows.showThePageOf(ui, empty);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, empty);

        assertFalse(offersEdit(menu),
            "the diagram's menu offers Edit Locomotive... where no train stands");
        assertTrue(AskingWindows.hasItem(menu, I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName())),
            "the diagram's menu over an empty station cannot place the selected locomotive");
        assertTrue(AskingWindows.hasItem(menu, placeHere()),
            "the diagram's menu over an empty station has no Place Another Locomotive Here...");

        Mouse.closeMenus();
    }

    /**
     * The autonomy editor's menu has no Edit Locomotive (Adam: "no edit loc in the autonomy editor"): over a train it
     * offers the train window, and nothing that opens the old station dialog.
     *
     * MUTATION: add the diagram's Edit Locomotive... item to `EditorTileMenus`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsMenuHasNoEditLocomotive() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

        assertFalse(offersEdit(menu),
            "the editor's menu offers Edit Locomotive...");
        assertNotNull(Components.withText(menu, I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(square))),
            "the editor's menu has no train window");
    }

    // ------------------------------------------------------------------------------------- Place the selected train

    /** Takes the selected locomotive off its key, as choosing it again does. */
    private static void nothingIsSelected()
    {
        if (ui.getActiveLoc() != null) ui.mapLocToCurrentButton(ui.getActiveLoc().getName());

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() == null, 10000),
            "precondition: the selected locomotive could not be taken off its key");
    }

    private static JMenuItem placeItemOn(AutonomyEditorPanel panel, TileKey square, String train) throws Exception
    {
        JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

        return (JMenuItem) Components.withText(menu, I18n.f("layout.ui.menuPlaceLocomotive", train));
    }

    private static java.util.List<String> roadNames(java.util.List<org.traincontrol.automation.Edge> road)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        if (road != null) for (org.traincontrol.automation.Edge edge : road) out.add(edge.getEnd().getName());

        return out;
    }

    /**
     * The editor's station menu places the selected locomotive in one click, through the door the train window uses: it
     * records the side it came in by and asks where the tail lies, and the answer is kept (Adam: "there should always be
     * an edit loc, place current (if selected and different), and place new option").  Tunnel, one way in from the north,
     * and a train of 5.
     *
     * MUTATION: have the item call `session.placeLocomotive` instead of `AutonomyEditorPanel.placeLocomotive`, and the
     * side and the tail are not recorded and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheEditorsMenuPlacesTheSelectedTrainWithItsSideAndTail() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        final TileKey tunnel = support.StandingTrain.squareNamed(session, "Tunnel");

        String name = aTrainNotPlaced();

        Locomotive train = model.getLocByName(name);

        Integer lengthWas = train.getTrainLength();

        try
        {
            train.setTrainLength(5);

            Components.onTheEventThread(() -> { session.placeLocomotive(tunnel, null); return null; });

            Components.settle();

            org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPre = tunnelPreIsTheTail(tunnel);

            makeActive(name);

            JMenuItem item = placeItemOn(panel, tunnel, name);

            assertNotNull(item, "the editor's menu on Tunnel has no Place " + name);
            assertTrue(item.isEnabled(), "Place " + name + " is grayed with no route running");

            Components.onTheEventThread(() -> { item.doClick(); return null; });

            assertEquals(session.getLocomotiveNameAt(tunnel), name, "the item did not put the selected train on Tunnel");
            assertEquals(session.getArrivedFrom(tunnel), "N", "the placement recorded no arrival side, or the wrong one");

            assertStandsFromTheNorthWithItsTail(train, tunnelPre);
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            train.setTrainLength(lengthWas);
        }
    }

    /** Answers the tail question with TunnelPre, the tail of a five-unit train come into Tunnel from the north. */
    private static org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPreIsTheTail(TileKey tunnel) throws Exception
    {
        org.traincontrol.automation.Layout railway = model.getAutoLayout();

        org.traincontrol.automation.Point copy = railway.getPoint(session.facingsFor(tunnel).keySet().iterator().next());

        org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPre = org.traincontrol.gui.TailCrossedPrompt
            .choicesFor(railway, copy, "N", 5, null).stream()
            .filter(choice -> choice.getFarthest().getName().startsWith("TunnelPre")).findFirst().orElse(null);

        assertNotNull(tunnelPre, "precondition: a five-unit train at Tunnel from the north is not offered TunnelPre");

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(tunnelPre.getFarthest().getName());

        return tunnelPre;
    }

    /** Waits for the train to stand on the railway come in from the north, its tail along the road chosen. */
    private static void assertStandsFromTheNorthWithItsTail(Locomotive train,
        org.traincontrol.gui.TailCrossedPrompt.Choice tail)
    {
        final java.util.List<String> expected = roadNames(tail.getRoad());

        assertTrue(Components.waitFor(() ->
        {
            org.traincontrol.automation.Point standing = model.getAutoLayout().getLocomotiveLocation(train);

            return standing != null && "N".equals(standing.getArrivedFrom())
                && expected.equals(roadNames(standing.getArrivedAlong()));
        }, 20000), "the tail answer was not kept; expected the road " + expected);
    }

    /**
     * Placed over another train through the train window's list, the new one stands on Tunnel on the railway with its
     * side and tail, as on an empty station (`testTheEditorsMenuPlacesTheSelectedTrainWithItsSideAndTail`), and the
     * train it displaced leaves the setup and the railway, as the diagram's Place does (OB-416).
     *
     * MUTATION: name only `name` in `AutonomyEditorPanel.placeLocomotive`'s `placementChanged`, and the rebuild puts the
     * displaced train back over the new one and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheListOverAnotherTrainTakesItsPlace() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        placeOverTheTrainOnTunnel(theList(panel), false);
    }

    /**
     * After a run the railway can hold X where the setup names Y: placed over them through the list, the new train
     * takes the square and both leave it, on the railway and in the setup (OB-416).
     *
     * MUTATION: leave `trainOnTheRailway` out of `AutonomyEditorPanel.trainsLeaving`, and the rebuild puts X
     * back over the new train and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheListOverARailwaysOtherTrainTakesItsPlace() throws Exception
    {
        placeOverTheTrainOnTunnel(theList(theEditorsPanel()), true);
    }

    /** The train window's list on Tunnel. */
    private static PlacingDoor theList(AutonomyEditorPanel panel)
    {
        return name ->
        {
            StationSettingsWindow window = StationDoors.openTrain(panel, support.StandingTrain.squareNamed(session, "Tunnel"));

            StationDoors.pick(window, "train.train", name);

            return window;
        };
    }

    /**
     * The same through the editor's Place <selected> (OB-416).
     *
     * MUTATION: name only `name` in `AutonomyEditorPanel.placeLocomotive`'s `placementChanged`, and the rebuild puts the
     * displaced train back over the new one and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheEditorsPlaceOverAnotherTrainTakesItsPlace() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        placeOverTheTrainOnTunnel(name ->
        {
            makeActive(name);

            JMenuItem item = placeItemOn(panel, support.StandingTrain.squareNamed(session, "Tunnel"), name);

            assertNotNull(item, "the editor's menu on Tunnel has no Place " + name);

            Components.onTheEventThread(() -> { item.doClick(); return null; });

            return null;
        }, false);
    }

    /**
     * Cancel after placing over another train brings the station back as it was, as after a drop onto a train: the
     * displaced train is back in the setup and on the railway, and the placed one is off both (OB-416).
     *
     * MUTATION: pass null for `railwayAsPictured()` in `LayoutEditor.discardAutonomyWork`, and the placed train stays on
     * the railway and this fails.
     */
    @Test(timeOut = 180000)
    public void testCancelAfterPlacingOverAnotherTrainPutsTheStationBack() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();

        String was = session.getLocomotiveNameAt(full);
        String name = aTrainOffTheRailway();

        String wasBefore = where(was);
        String nameBefore = where(name);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            makeActive(name);

            JMenuItem item = placeItemOn(panel, full, name);

            assertNotNull(item, "precondition: the editor's menu has no Place " + name + " over " + was);

            Components.onTheEventThread(() -> { item.doClick(); return null; });

            cancelOnceOnTheRailway(name);

            assertEquals(where(was), wasBefore, "Cancel did not put the displaced train back");
            assertEquals(where(name), nameBefore, "Cancel did not take the placed train back off");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);
        }
    }

    /**
     * After a run the railway can hold Z on a square whose setup train X stands elsewhere: the editor's Place over Z,
     * then Cancel, stands Z where the railway had it, not on its old setup square, which would leave the square reading
     * free under a standing train; X stays where the run left it (OB-416).
     *
     * MUTATION: pass no `railwayAsPictured()` from `LayoutEditor.discardAutonomyWork` to the rebuild, and Z goes back
     * to its old setup square.
     */
    @Test(timeOut = 240000)
    public void testCancelAfterARunPutsTheRailwaysTrainBack() throws Exception
    {
        cancelAfterARun(false, testTheTrainWindow::placeAnotherTrain);
    }

    /**
     * The same where the railway moves the trains with the editor open, which no real door can (D114): Cancel stands
     * every train back where the railway had it when the editor opened, before the simulated run.
     *
     * MUTATION: call `pictureTheRailway()` at the top of `LayoutEditor.discardAutonomyWork`, and X stays where the
     * simulated run left it.
     */
    @Test(timeOut = 240000)
    public void testCancelAfterARunWithTheEditorOpenPutsTheRailwaysTrainBack() throws Exception
    {
        cancelAfterARun(true, testTheTrainWindow::placeAnotherTrain);
    }

    /** The editor's Place of a train standing nowhere, over the square. */
    private static String placeAnotherTrain(AutonomyEditorPanel panel, TileKey square) throws Exception
    {
        String name = aTrainOffTheRailway();

        makeActive(name);

        JMenuItem item = placeItemOn(panel, square, name);

        assertNotNull(item, "precondition: no Place " + name);

        Components.onTheEventThread(() -> { item.doClick(); return null; });

        return name;
    }

    /**
     * The same through None on the editor's Train tab, which lifts Z: Cancel stands it where the railway had it (OB-416).
     *
     * MUTATION: pass no `railwayAsPictured()` from `LayoutEditor.discardAutonomyWork` to the rebuild, and Z goes back
     * to its old setup square.
     */
    @Test(timeOut = 240000)
    public void testCancelAfterARunAndNonePutsTheRailwaysTrainBack() throws Exception
    {
        cancelAfterARun(false, (panel, square) ->
        {
            StationSettingsWindow window = StationDoors.openTrain(panel, square);

            StationDoors.pick(window, "train.train", I18n.t("autosetup.ui.labelNone"));

            Components.drain(8);

            Components.onTheEventThread(() -> { window.dispose(); return null; });

            return null;
        });
    }

    /**
     * A train the editor's Place moved, which the railway then moved on to a third station, goes back at Cancel where it
     * stood when the editor opened.  No real door moves a train while the editor is open (D114), so this simulates it.
     *
     * MUTATION: call `pictureTheRailway()` at the top of `LayoutEditor.discardAutonomyWork`, and the train stays on the
     * third station.
     */
    @Test(timeOut = 240000)
    public void testCancelPutsBackATrainTheRailwayMovedSinceThePlace() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        final TileKey square = aSquareWithATrain();
        final String name = session.getLocomotiveNameAt(square);
        final org.traincontrol.automation.Point copy = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));
        final TileKey empty = anEmptyStation();

        assertNotNull(copy, "precondition: " + name + " stands nowhere on the railway");

        offTheRailwayAt(empty);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            placeFromTheMenu(panel, empty, name);

            final org.traincontrol.automation.Point third = aFreeCopyOf(anEmptyStationBut(square));

            assertNotNull(third, "precondition: no free copy of a third station");

            Components.onTheEventThread(() -> { assertTrue(model.getAutoLayout().moveLocomotive(name, third.getName(), false),
                "precondition: the railway would not move " + name); return null; });

            Components.settle();

            cancelTheEditor();

            org.traincontrol.automation.Point now = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));

            assertEquals(now == null ? null : now.getName(), copy.getName(), "Cancel did not stand " + name
                + " back where it stood when the editor opened");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            standBack(name, copy.getName(), null, null, null);
        }
    }

    /**
     * The same through Clear All Locomotives: Cancel stands each lifted train where the railway had it, not on its old
     * setup square (OB-416).
     *
     * MUTATION: pass no `railwayAsPictured()` from `LayoutEditor.discardAutonomyWork` to the rebuild, and Z goes back
     * to its old setup square.
     */
    @Test(timeOut = 240000)
    public void testCancelAfterARunAndClearAllPutsTheRailwaysTrainsBack() throws Exception
    {
        cancelAfterARun(false, (panel, square) ->
        {
            bulkDoor(panel, "clearAllPlacements", () -> session.getLocomotiveNameAt(square) == null,
                "Clear All left the square placed");

            return null;
        });
    }

    /**
     * Place All Trains at Their Homes, then Cancel, stands every train back where the railway had it: the homed ones
     * too, which the door's rebuild stood on their homes (OB-416).
     *
     * MUTATION: pass null for `railwayAsPictured()` in `LayoutEditor.discardAutonomyWork`, and the homed trains stay on
     * their homes.
     */
    @Test(timeOut = 240000)
    public void testCancelAfterPlaceAllAtHomePutsEveryTrainBack() throws Exception
    {
        final TileKey square = aSquareWithATrain();
        final String train = session.getLocomotiveNameAt(square);
        final TileKey home = anEmptyStation();

        Components.onTheEventThread(() -> { session.setHome(home, train); ui.rebuildRunningLayoutFromSetup(); return null; });

        Components.settle();

        final Map<String, String> before = standingNow();

        assertTrue(before.containsKey(train), "precondition: " + train + " stands nowhere on the railway");

        bulkDoor(theEditorsPanel(), "placeEveryTrainAtHome", () -> !before.equals(standingNow()),
            "the railway did not follow Place All at Home");

        assertEquals(session.getLocomotiveNameAt(home), train, "precondition: Place All at Home did not put " + train
            + " on its home");

        cancelTheEditor();

        assertEquals(standingNow(), before, "Cancel after Place All at Home left trains moved on the railway");
    }

    /** The editor's Place of this train on the square, from its menu, waited on until the railway has it there. */
    private static void placeFromTheMenu(AutonomyEditorPanel panel, TileKey square, String name) throws Exception
    {
        makeActive(name);

        JMenuItem place = placeItemOn(panel, square, name);

        assertNotNull(place, "precondition: no Place " + name);

        Components.onTheEventThread(() -> { place.doClick(); return null; });

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> square.equals(session.getStationIndex()
            .squareOf(model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name))))), 20000),
            "precondition: Place did not move " + name + " on the railway");
    }

    /** Takes these trains off the railway alone, so none a claim stood there outlives it. */
    private static void offTheRailway(java.util.Collection<String> trains)
    {
        Components.onTheEventThread(() ->
        {
            for (String each : trains)
            {
                org.traincontrol.automation.Point at = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(each));

                if (at != null) model.getAutoLayout().moveLocomotive(null, at.getName(), false);
            }

            return null;
        });

        Components.settle();
    }

    /** The three ways an edit is thrown away: Cancel, a page switch's Discard, the exit's Discard. */
    private static final String[] DISCARDS = {"Cancel", "a page switch's Discard", "the exit's Discard"};

    /** Throws the edit away the given way (`DISCARDS`), answering yes, and closes the editor. */
    private void discard(int how) throws Exception
    {
        java.util.List<String> asked = leave(how);

        if (how > 0) assertEquals(asked.size(), 1, "precondition: " + DISCARDS[how] + " asked " + asked);
    }

    /** Leaves the editor the given way (`DISCARDS`), answering yes or Discard, and closes it; returns what it asked. */
    private java.util.List<String> leave(int how) throws Exception
    {
        if (how == 0)
        {
            String asked = cancelTheEditor();

            return asked == null ? java.util.Collections.<String>emptyList()
                : java.util.Collections.singletonList(asked);
        }

        final LayoutEditor closing = editor;
        final java.lang.reflect.Method settle = Reflect.method(LayoutEditor.class, "settleUnsavedWork");
        java.util.List<String> asked;

        try (support.Answering answering = support.Answering.start(
            message -> support.Answering.press(I18n.t("layout.ui.switchDiscard"))))
        {
            assertTrue(Components.onTheEventThread(() -> how == 1 ? (Boolean) settle.invoke(closing)
                : closing.maySettleBeforeExit()), DISCARDS[how] + " did not go ahead");

            asked = new java.util.ArrayList<>(answering.asked());
        }

        support.FrozenEditor.dispose(closing);

        editor = null;

        Components.settle();

        return asked;
    }

    /** Where a train stands and faces on the railway, and the setup's facing on that square. */
    private static String standingAndFacing(String name)
    {
        return Components.onTheEventThread(() ->
        {
            org.traincontrol.automation.Point at = model.getAutoLayout().getLocomotiveLocation(
                model.getLocByName(name));
            TileKey sq = at == null ? null : session.getStationIndex().squareOf(at.getName());

            return "on " + sq + " facing " + session.facingOf(name, model.getAutoLayout()) + ", setup facing "
                + (sq == null ? null : session.getFacing(sq));
        });
    }

    /** The other way a train on this square could face, from the way it faces on the railway now. */
    private static org.traincontrol.automationui.TilePorts.Side theOtherWay(TileKey square)
    {
        org.traincontrol.automationui.TilePorts.Side now = session.facingOnTheRailway(square, model.getAutoLayout());

        for (org.traincontrol.automationui.TilePorts.Side c : session.facingChoices(square)) if (c != now) return c;

        throw new AssertionError("precondition: " + square + " has no other facing");
    }

    /** Calls one of the editor's square menu doors (`EditorTileMenus`) on a square, as its item does, and waits. */
    private static void throughTheTileMenus(AutonomyEditorPanel panel, String door, Class<?> type, TileKey square,
        Object value) throws Exception
    {
        final Object menus = Reflect.get(AutonomyEditorPanel.class, panel, "tileMenus");
        final java.lang.reflect.Method call = Reflect.method(menus.getClass(), door, TileKey.class, type);

        Components.onTheEventThread(() -> { call.invoke(menus, square, value); return null; });

        Components.settle();
    }

    /** The editor's Facing door, turning the train on this square to `way`; asserts it turned on the railway. */
    private static void faceFromTheMenu(AutonomyEditorPanel panel, TileKey square, String name,
        org.traincontrol.automationui.TilePorts.Side way) throws Exception
    {
        final String before = copyOf(name);

        throughTheTileMenus(panel, "chooseFacing", org.traincontrol.automationui.TilePorts.Side.class, square, way);

        assertFalse(before.equals(copyOf(name)), "precondition: the Facing door did not turn " + name
            + " on the railway");
    }

    /**
     * The editor's Facing door alone, then Cancel or either Discard: nothing is asked and the train keeps the facing it
     * has, on the railway and in the setup, as Cancel never changes which way a train faces.
     *
     * MUTATION: compare `autonomyAsOpened` itself in `LayoutEditor.hasUnsavedAutonomyWork`, and each door asks and
     * turns it back.
     */
    @Test(timeOut = 400000)
    public void testCancelOrDiscardKeepsAFacingChangeAndAsksNothing() throws Exception
    {
        forEachWayOutWithATrainThatCanTurn((how, panel, square, name, copyBefore) ->
        {
            faceFromTheMenu(panel, square, name, theOtherWay(square));

            final String turned = standingAndFacing(name);

            java.util.List<String> asked = leave(how);

            assertEquals(asked, java.util.Collections.emptyList(), DISCARDS[how] + " after only a facing change of "
                + name + " asked");
            assertEquals(standingAndFacing(name), turned, DISCARDS[how] + " after a facing change turned " + name
                + " back (it stood on " + copyBefore + " at opening)");
        });
    }

    /**
     * Cancel and both Discards send the Central Station nothing, after an edit that placed a train elsewhere, turned it
     * there with the editor's Facing door and reversed it on its decoder, all with the editor open: the trains go back
     * in the model alone, and no train's direction is changed.  The turns are even, so the way out only rebuilds.
     *
     * MUTATION: in `LayoutEditor.discardAutonomyWork`, `switchDirection()` each train it puts back after the rebuild, and
     * Cancel sends a direction command.
     */
    @Test(timeOut = 400000)
    public void testCancelAndTheDiscardsSendTheCentralStationNothing() throws Exception
    {
        sendsTheCentralStationNothing(true);
    }

    /**
     * The same with the Facing door alone: one turn, so the way out also turns the train again (`turnAgain`), which
     * must change no train's direction either.
     *
     * MUTATION: in `LayoutEditor.turnAgain`, `switchDirection()` each train it turns, and Cancel sends a direction
     * command.
     */
    @Test(timeOut = 400000)
    public void testTurningAgainAfterAnOddTurnSendsTheCentralStationNothing() throws Exception
    {
        sendsTheCentralStationNothing(false);
    }

    private void sendsTheCentralStationNothing(final boolean reversedForReal) throws Exception
    {
        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        support.WireRecorder wire = support.WireRecorder.open();

        try
        {
            forEachWayOutWithATrainThatCanTurn((how, panel, square, name, copyBefore) ->
            {
                final TileKey elsewhere = anEmptyStationWhere(each -> session.trainsWhereTheyStand().get(each) == null
                    && session.facingChoices(each).size() == 2);

                placeFromTheMenu(panel, elsewhere, name);

                Components.settle();

                faceFromTheMenu(panel, elsewhere, name, theOtherWay(elsewhere));

                // Reversed for real: the command to its decoder, then its turn followed as the echo would be
                final org.traincontrol.marklin.MarklinLocomotive loc = model.getLocByName(name);
                final int sentBefore = wire.mark();
                final String facedBefore = copyOf(name);
                final org.traincontrol.base.Locomotive.locDirection ran = loc.getDirection();

                try
                {
                    if (reversedForReal)
                    {
                        loc.switchDirection();

                        Components.settle();

                        assertTrue(wire.directionCommandsSince(sentBefore, loc.getIntUID()).size() > 0, "precondition: the"
                            + " reversal of " + name + " sent its decoder nothing the recorder heard");

                        if (facedBefore.equals(copyOf(name))) reverse(name, facedBefore);
                    }

                    // A line of its own, as the log drops a line equal to the last, which would hide a repeated command
                    model.log("D130: " + DISCARDS[how]);

                    final int mark = wire.mark();

                    leave(how);

                    Components.settle();
                    Components.drain(8);

                    assertEquals(wire.sentSince(mark), java.util.Collections.emptyList(), DISCARDS[how] + " after "
                        + name + " was placed on " + elsewhere + ", turned there" + (reversedForReal
                        ? " and reversed on its decoder" : "") + " sent the Central Station " + wire.since(mark));
                }
                finally
                {
                    loc.setDirection(ran);
                }
            });
        }
        finally
        {
            wire.close();

            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);
        }
    }

    /** A claim's body, run once for each way out (`DISCARDS`). */
    private interface WayOut
    {
        void run(int how, AutonomyEditorPanel panel, TileKey square, String name, String copyBefore) throws Exception;
    }

    /** Runs a body for each way out on a train that can turn, in a fresh editor; railway and setup put back after. */
    private void forEachWayOutWithATrainThatCanTurn(WayOut body) throws Exception
    {
        for (int how = 0; how < DISCARDS.length; how++)
        {
            AutonomyEditorPanel panel = theEditorsPanel();
            TileKey square = aTrainThatCanTurn();
            String name = session.getLocomotiveNameAt(square);
            String copyBefore = copyOf(name);

            try
            {
                body.run(how, panel, square, name, copyBefore);
            }
            finally
            {
                standBack(name, copyBefore, null, null, null);

                support.FrozenEditor.putBack(ui, session, asOpened);
            }
        }
    }

    /**
     * The editor's Came In By door sets the side on the railway as well as in the setup; Cancel puts both back (D114).
     * The snapshot has no station a standing train could have come into from two sides, so a plain sensor is made one.
     *
     * MUTATION: pass null for `railwayAsPictured()` in `LayoutEditor.discardAutonomyWork`, and the new side stays.
     */
    @Test(timeOut = 240000)
    public void testCancelAfterAnArrivalSideChangePutsItBack() throws Exception
    {
        TileKey two = null;

        for (TileKey each : session.getReducer().getPoints().keySet())
        {
            if (!PAGE.equals(each.getPage()) || session.getStore().isStation(each) || session.getLocomotiveNameAt(each) != null)
            {
                continue;
            }

            final TileKey sq = each;

            Components.onTheEventThread(() -> { session.setStation(sq, true); ui.rebuildRunningLayoutFromSetup(); return null; });

            Components.settle();

            if (session.unbarredArrivalSides(each).size() >= 2 && aFreeCopyOf(each) != null)
            {
                two = each;
                break;
            }

            Components.onTheEventThread(() -> { session.setStation(sq, false); return null; });
        }

        assertNotNull(two, "precondition: no plain sensor on " + PAGE + " makes a station with two sides");

        final TileKey square = two;
        final String name = aTrainNotPlaced();
        final java.util.List<org.traincontrol.automationui.TilePorts.Side> sides = session.unbarredArrivalSides(square);

        try
        {
            Components.onTheEventThread(() ->
            {
                session.placeLocomotive(square, name);
                session.setArrivedFrom(square, sides.get(0).name());
                ui.rebuildRunningLayoutFromSetup();

                return null;
            });

            Components.settle();

            org.traincontrol.automation.Point on = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));

            assertNotNull(on, "precondition: " + name + " was not put on " + square);

            final String before = "railway " + on.getArrivedFrom() + ", setup " + session.getArrivedFrom(square);
            String other = null;

            for (org.traincontrol.automationui.TilePorts.Side each : sides)
            {
                if (!each.name().equals(on.getArrivedFrom())) other = each.name();
            }

            final String side = other;

            throughTheTileMenus(theEditorsPanel(), "setArrivedFrom", String.class, square, side);

            assertEquals(model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name)).getArrivedFrom(), side,
                "precondition: the side door did not reach the railway");

            cancelTheEditor();

            org.traincontrol.automation.Point now = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));

            assertEquals("railway " + (now == null ? null : now.getArrivedFrom()) + ", setup " + session.getArrivedFrom(square),
                before, "Cancel after setting " + name + "'s side on " + square + " to " + side + " did not put it back");
        }
        finally
        {
            offTheRailway(java.util.Collections.singleton(name));
        }
    }

    /**
     * A train placed in the editor and then renamed goes back where it stood at Cancel and either Discard: the picture
     * holds the locomotive, not its name (D114).
     *
     * MUTATION: key `LayoutEditor.railwayAsOpened` by name and look it up by the name now in `railwayAsPictured`, and the
     * renamed train stays where it was placed.
     */
    @Test(timeOut = 400000)
    public void testCancelOrDiscardAfterPlacingARenamedTrainPutsItBack() throws Exception
    {
        for (int how = 0; how < DISCARDS.length; how++)
        {
            AutonomyEditorPanel panel = theEditorsPanel();

            final TileKey square = aSquareWithATrain();
            final String name = session.getLocomotiveNameAt(square);
            final String renamed = name + " D114";
            final org.traincontrol.automation.Point copy = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));
            final TileKey empty = anEmptyStation();

            assertNotNull(copy, "precondition: " + name + " stands nowhere on the railway");

            org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

            // The main window's editor, as Edit opens it, so the rename reaches its Cancel snapshot
            final Object openBefore = Reflect.get(TrainControlUI.class, ui, "openEditor");

            Reflect.set(TrainControlUI.class, ui, "openEditor", editor);

            try
            {
                placeFromTheMenu(panel, empty, name);

                Components.settle();

                SwingUtilities.invokeAndWait(() -> assertTrue(model.renameLoc(name, renamed), "precondition: rename refused"));

                Components.settle();

                discard(how);

                org.traincontrol.automation.Point now = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(renamed));

                TileKey inSetup = null;

                for (Map.Entry<TileKey, String> each : session.placementsAutonomyWillWrite().entrySet())
                {
                    if (renamed.equals(each.getValue())) inSetup = each.getKey();
                }

                assertEquals("setup " + inSetup + ", railway " + (now == null ? null : now.getName()),
                    "setup " + square + ", railway " + copy.getName(), DISCARDS[how] + " after Place and a rename of "
                    + renamed + " with the editor open did not put it back");
            }
            finally
            {
                org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

                Reflect.set(TrainControlUI.class, ui, "openEditor", openBefore);

                if (model.getLocByName(renamed) != null) SwingUtilities.invokeAndWait(() -> model.renameLoc(renamed, name));

                Components.settle();

                standBack(name, copy.getName(), null, null, null);

                support.FrozenEditor.putBack(ui, session, asOpened);
            }
        }
    }

    /**
     * A train placed in the editor and then deleted is left out of Cancel, even where a new locomotive takes its name
     * meanwhile: every other train stands back where it stood, and nothing is written or stood under that name (D114).
     *
     * MUTATION: drop the `getLocByName(name) == each.getKey()` test from `LayoutEditor.railwayAsPictured`, and the new
     * locomotive is stood where the deleted one stood when the editor opened.
     */
    @Test(timeOut = 240000)
    public void testCancelAfterPlacingADeletedTrainPutsTheOthersBack() throws Exception
    {
        final String name = "D114 deleted meanwhile";
        final TileKey first = anEmptyStation();
        final TileKey second = anEmptyStationBut(first);

        final Object openBefore = Reflect.get(TrainControlUI.class, ui, "openEditor");

        Components.onTheEventThread(() -> model.newMM2Locomotive(name, 79));

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            Components.onTheEventThread(() -> { session.placeLocomotive(first, name); ui.rebuildRunningLayoutFromSetup(); return null; });

            Components.settle();

            assertNotNull(model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name)),
                "precondition: " + name + " was not put on " + first);

            final Map<String, String> others = standingNow();

            others.remove(name);

            AutonomyEditorPanel panel = theEditorsPanel();

            // The main window's editor, as Edit opens it, so the delete reaches its Cancel snapshot
            Reflect.set(TrainControlUI.class, ui, "openEditor", editor);

            placeFromTheMenu(panel, second, name);

            Components.settle();

            SwingUtilities.invokeAndWait(() -> assertTrue(model.deleteLoc(name), "precondition: delete refused"));

            Components.onTheEventThread(() -> model.newMM2Locomotive(name, 79));

            Components.settle();

            cancelTheEditor();

            assertFalse(session.placementsAutonomyWillWrite().containsValue(name), "Cancel wrote the deleted " + name
                + " back into the setup");
            assertFalse(standingNow().containsKey(name), "Cancel stood the deleted " + name + " on the railway");
            assertEquals(standingNow(), others, "Cancel after placing a train deleted meanwhile moved the others");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            Reflect.set(TrainControlUI.class, ui, "openEditor", openBefore);

            if (model.getLocByName(name) != null) SwingUtilities.invokeAndWait(() -> model.deleteLoc(name));

            Components.settle();
        }
    }

    /**
     * Where the edit moved trains, Cancel's question and the switch's add one sentence naming the trains a discard
     * stands back, five by name and then "and N more"; Cancel then stands them all back (D114).
     *
     * MUTATION: return `question` at the top of `LayoutEditor.withTheTrainsMovingBack`, and neither question names them.
     */
    @Test(timeOut = 240000)
    public void testTheDiscardQuestionsNameTheTrainsThatGoBack() throws Exception
    {
        // More than five trains placed, as the editor would
        final java.util.List<String> extras = new java.util.ArrayList<>();

        try
        {
            for (TileKey empty = anEmptyStation(); session.placementsAutonomyWillWrite().size() < 7; empty = anEmptyStation())
            {
                final TileKey square = empty;
                final String extra = aTrainOffTheRailway();

                extras.add(extra);

                Components.onTheEventThread(() -> { session.placeLocomotive(square, extra); ui.rebuildRunningLayoutFromSetup();
                    return null; });

                Components.settle();
            }

            discardQuestionsNameTheTrainsThatGoBack();
        }
        finally
        {
            offTheRailway(extras);
        }
    }

    private void discardQuestionsNameTheTrainsThatGoBack() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        final Map<String, String> before = standingNow();

        bulkDoor(panel, "clearAllPlacements", () -> !before.equals(standingNow()), "the railway did not follow Clear All");

        Map<String, String> now = standingNow();
        java.util.List<String> moved = new java.util.ArrayList<>();

        for (String train : new java.util.TreeSet<>(before.keySet())) if (!before.get(train).equals(now.get(train))) moved.add(train);

        assertTrue(moved.size() > 5, "precondition: Clear All moved only " + moved);

        String names = I18n.f("autolayout.ui.routeNamesAndMore", String.join(", ", moved.subList(0, 5)), moved.size() - 5);
        String sentence = "\n\n" + I18n.f("autosetup.ui.trainsGoBackOnDiscard", names);

        assertEquals(switchQuestion(), I18n.t("layout.ui.confirmSwitchWithUnsavedWork") + sentence,
            "the switch's question does not name the trains a Discard moves back");
        assertEquals(cancelTheEditor(), I18n.t("autosetup.ui.confirmExitWithoutSaving") + sentence,
            "Cancel's question does not name the trains it moves back");
        assertEquals(standingNow(), before, "Cancel after Clear All left trains moved on the railway");
    }

    /**
     * Where the edit moved no train, Cancel's question and the switch's are word for word as before (D114), after an
     * edit the railway is rebuilt for.
     *
     * MUTATION: drop `if (moving.isEmpty()) return question;` from `LayoutEditor.withTheTrainsMovingBack`, and both gain
     * a sentence naming no train.
     */
    @Test(timeOut = 240000)
    public void testTheDiscardQuestionsAreUnchangedWhereNoTrainMoved() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        final TileKey empty = anEmptyStation();
        final java.lang.reflect.Method changed = Reflect.method(AutonomyEditorPanel.class, "setupChanged");
        final Map<String, String> before = standingNow();

        Components.onTheEventThread(() -> { session.setPointProperty(empty, "active", false); changed.invoke(panel); return null; });

        Components.settle();

        assertEquals(standingNow(), before, "precondition: shutting an empty station moved a train");

        assertEquals(switchQuestion(), I18n.t("layout.ui.confirmSwitchWithUnsavedWork"),
            "the switch's question changed with no train moved");
        assertEquals(cancelTheEditor(), I18n.t("autosetup.ui.confirmExitWithoutSaving"),
            "Cancel's question changed with no train moved");
    }

    /** The question a page switch asks about the unsaved work, answered Cancel so nothing happens. */
    private String switchQuestion() throws Exception
    {
        final java.lang.reflect.Method settle = Reflect.method(LayoutEditor.class, "settleUnsavedWork");

        try (support.Answering answering = support.Answering.start(
            message -> support.Answering.press(I18n.t("layout.ui.switchCancel"))))
        {
            assertFalse(Components.onTheEventThread(() -> (Boolean) settle.invoke(editor)), "the switch went ahead");

            assertEquals(answering.asked().size(), 1, "precondition: the switch asked nothing");

            return answering.asked().get(0);
        }
    }

    /** One of the editor's bulk doors, answered Yes, waited on until `done`. */
    private static void bulkDoor(AutonomyEditorPanel panel, String door, java.util.function.BooleanSupplier done,
        String notDone) throws Exception
    {
        final java.lang.reflect.Method method = Reflect.method(AutonomyEditorPanel.class, door);

        try (support.Answering answering = support.Answering.start(message -> support.Answering.press(AskingWindows.YES)))
        {
            SwingUtilities.invokeLater(() -> Components.onTheEventThread(() -> method.invoke(panel)));

            assertTrue(Components.waitFor(done, 20000), "precondition: " + notDone);
        }

        Components.settle();
    }

    /** Each train on the railway and the Point it stands on. */
    private static Map<String, String> standingNow()
    {
        return Components.onTheEventThread(() ->
        {
            Map<String, String> out = new java.util.TreeMap<>();

            for (Map.Entry<String, String[]> each : TrainControlUI.whereTheTrainsAre(model.getAutoLayout()).entrySet())
            {
                out.put(each.getKey(), each.getValue()[0]);
            }

            return out;
        });
    }

    /** An editor door over a square; returns the train it put there, or null. */
    private interface AfterARunDoor
    {
        String act(AutonomyEditorPanel panel, TileKey square) throws Exception;
    }

    /**
     * As after a run, moves a square's setup train X on to an empty station and stands another placed train Z on its
     * copy, the railway alone told, with the editor open or before it opens; uses the door, Cancels, and asserts both
     * stand as they did when the editor opened.
     */
    private void cancelAfterARun(boolean editorOpen, AfterARunDoor door) throws Exception
    {
        final TileKey square = aSquareWithATrain();
        final String setupTrain = session.getLocomotiveNameAt(square);
        final String brought = anotherPlacedTrainOnTheRailway(square);

        assertNotNull(brought, "precondition: no other placed train stands on the railway");

        final org.traincontrol.automation.Point copy = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(setupTrain));
        final org.traincontrol.automation.Point broughtWas = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(brought));
        final org.traincontrol.automation.Point other = aFreeCopyOf(anEmptyStation());

        assertNotNull(copy, "precondition: " + setupTrain + " stands nowhere on the railway");
        assertNotNull(other, "precondition: no free copy of an empty station");

        final String copyName = copy.getName();

        String placed = null;

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            AutonomyEditorPanel panel = editorOpen ? theEditorsPanel() : null;

            Components.onTheEventThread(() -> { asAfterARun(setupTrain, copyName, other, brought); return null; });

            Components.settle();

            placed = door.act(panel != null ? panel : theEditorsPanel(), square);

            if (placed != null) cancelOnceOnTheRailway(placed);
            else cancelTheEditor();

            org.traincontrol.automation.Point broughtNow = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(brought));
            org.traincontrol.automation.Point setupNow = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(setupTrain));

            // Where the railway had them when the editor opened: after the run, or before it where it ran meanwhile
            assertEquals(broughtNow == null ? null : broughtNow.getName(), editorOpen ? broughtWas.getName() : copyName,
                "Cancel after a run did not put " + brought + " back where it stood when the editor opened");
            assertEquals(setupNow == null ? null : setupNow.getName(), editorOpen ? copyName : other.getName(),
                "Cancel after a run did not put " + setupTrain + " back where it stood when the editor opened");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            standBack(setupTrain, copyName, brought, broughtWas, placed);
        }
    }

    /** Where the setup and the railway have this train, with the side it came in by on the railway. */
    private static String where(String name)
    {
        TileKey inSetup = null;

        for (Map.Entry<TileKey, String> each : session.placementsAutonomyWillWrite().entrySet())
        {
            if (name.equals(each.getValue())) inSetup = each.getKey();
        }

        org.traincontrol.automation.Point on = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));

        return "setup " + inSetup + ", railway " + (on == null ? null : on.getName() + " from " + on.getArrivedFrom());
    }

    /** Waits for the placed train to stand on the railway, then Cancels the editor. */
    private void cancelOnceOnTheRailway(String name) throws Exception
    {
        assertTrue(Components.waitFor(() -> model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name)) != null,
            20000), "precondition: " + name + " was not put on the railway");

        Components.settle();

        cancelTheEditor();
    }

    /** Cancel on the editor, answering Yes to throwing the edits away; returns what it asked, or null. */
    private String cancelTheEditor() throws Exception
    {
        final LayoutEditor closing = editor;

        final java.lang.reflect.Method exit = Reflect.method(LayoutEditor.class, "confirmExit");

        String asked;

        try (support.Answering answering = support.Answering.start(message -> support.Answering.press(AskingWindows.YES)))
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    exit.invoke(closing);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(Components.waitFor(() -> !closing.isDisplayable(), 20000), "Cancel did not close the editor");

            asked = answering.asked().isEmpty() ? null : answering.asked().get(0);
        }

        Components.settle();

        editor = null;

        return asked;
    }

    /** A door that places the named train on Tunnel; returns the window it left open, or null. */
    private interface PlacingDoor
    {
        StationSettingsWindow place(String name) throws Exception;
    }

    /**
     * Stands a train on the empty Tunnel through the door, then places another over it through the same door;
     * `afterARun` first swaps the railway's train there for one the setup does not place.
     */
    private void placeOverTheTrainOnTunnel(PlacingDoor door, boolean afterARun) throws Exception
    {
        final TileKey tunnel = support.StandingTrain.squareNamed(session, "Tunnel");

        assertNull(session.getLocomotiveNameAt(tunnel), "precondition: Tunnel is not empty in the setup");

        // `putBack` leaves a train an earlier claim placed standing on the railway, so Tunnel is emptied there too
        offTheRailwayAt(tunnel);

        StationSettingsWindow window = null;

        String was = aTrainOffTheRailway();

        final Locomotive displaced = model.getLocByName(was);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        StationSettingsWindow first = door.place(was);

        if (first != null) StationDoors.close(first);

        assertTrue(Components.waitFor(() -> model.getAutoLayout().getLocomotiveLocation(displaced) != null, 20000),
            "precondition: " + was + " was not put on Tunnel on the railway");

        Components.settle();

        final String onTheRailway = afterARun ? aTrainOffTheRailway() : null;

        if (afterARun)
        {
            final String copy = model.getAutoLayout().getLocomotiveLocation(displaced).getName();

            Components.onTheEventThread(() ->
            {
                model.getAutoLayout().moveLocomotive(null, copy, false);

                assertTrue(model.getAutoLayout().moveLocomotive(onTheRailway, copy, false),
                    "precondition: " + onTheRailway + " could not be stood on Tunnel");

                return null;
            });

            Components.settle();

            assertEquals(session.trainOnTheRailway(tunnel, model.getAutoLayout()), onTheRailway,
                "precondition: the railway does not hold " + onTheRailway + " on Tunnel");
            assertEquals(session.getLocomotiveNameAt(tunnel), was, "precondition: the setup does not name " + was);
        }

        String name = aTrainOffTheRailway();

        Locomotive train = model.getLocByName(name);

        Integer lengthWas = train.getTrainLength();

        try
        {
            train.setTrainLength(5);

            org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPre = tunnelPreIsTheTail(tunnel);

            window = door.place(name);

            assertEquals(session.getLocomotiveNameAt(tunnel), name, "the train was not put on Tunnel in the setup");
            assertEquals(session.getArrivedFrom(tunnel), "N", "the placement recorded no arrival side, or the wrong one");

            assertStandsFromTheNorthWithItsTail(train, tunnelPre);

            assertFalse(session.placementsAutonomyWillWrite().containsValue(was), was + " is still in the setup");

            assertNull(model.getAutoLayout().getLocomotiveLocation(displaced), was + " still stands on the railway");

            if (onTheRailway != null)
            {
                assertNull(model.getAutoLayout().getLocomotiveLocation(model.getLocByName(onTheRailway)),
                    onTheRailway + ", which only the railway had on Tunnel, still stands on the railway");
            }
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            train.setTrainLength(lengthWas);

            if (window != null) StationDoors.close(window);

            offTheRailwayAt(tunnel);
        }
    }

    /** A train neither the setup nor the railway has standing anywhere. */
    private static String aTrainOffTheRailway()
    {
        return support.StandingTrain.standingNowhere(model, session);
    }

    /** Lifts whatever stands on this square's copies off the railway alone. */
    private static void offTheRailwayAt(TileKey square)
    {
        Components.onTheEventThread(() ->
        {
            for (String copy : session.facingsFor(square).keySet())
            {
                org.traincontrol.automation.Point point = model.getAutoLayout().getPoint(copy);

                if (point != null && point.getCurrentLocomotive() != null) model.getAutoLayout().moveLocomotive(null, copy, false);
            }

            return null;
        });

        Components.settle();
    }

    /**
     * No Place item for the selected train where nothing is selected, or where the selected train already stands.
     *
     * MUTATION: drop the `equals(standing)` test or the `isStation` test in `EditorTileMenus.placeSelectedItem`, and the
     * matching part of this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsMenuHasNoPlaceForNothingOrForTheTrainAlreadyThere() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();
        TileKey empty = anEmptyStation();

        String standing = session.getLocomotiveNameAt(full);
        String other = aTrainNotPlaced();

        nothingIsSelected();

        assertNull(placeItemOn(panel, empty, other), "Place was offered with nothing selected");
        assertNull(placeItemOn(panel, full, standing), "Place was offered with nothing selected, over a train");

        makeActive(standing);

        assertNull(placeItemOn(panel, full, standing), "Place was offered for the train already standing there");

        makeActive(other);

        assertNotNull(placeItemOn(panel, empty, other), "precondition: no Place for a selected train that stands nowhere");
        assertNotNull(placeItemOn(panel, full, other), "precondition: no Place over another train");

        // Only a station takes a train: a point that is none gets no Place
        TileKey plain = null;

        for (TileKey each : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(each.getPage()) && !session.getStore().isStation(each)) plain = each;
        }

        assertNotNull(plain, "precondition: " + PAGE + " has no point that is not a station");

        assertNull(placeItemOn(panel, plain, other), "Place was offered on a point that is not a station");
    }

    /**
     * Which train "stands there" is read off the railway, as the Train at item and the diagram's menu read it: after a
     * run the railway can hold X where the setup still names Y, and Place X is not offered on X's own square.
     *
     * MUTATION: pass `editor.locomotiveAt(target)` instead of `trainStanding(target)` to `placeSelectedItem`, and this
     * fails.
     */
    @Test(timeOut = 180000)
    public void testAfterARunPlaceReadsTheTrainOnTheRailway() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();

        String inTheSetup = session.getLocomotiveNameAt(full);

        org.traincontrol.automation.Layout railway = model.getAutoLayout();

        org.traincontrol.automation.Point copy = railway.getLocomotiveLocation(model.getLocByName(inTheSetup));

        assertNotNull(copy, "precondition: " + inTheSetup + " is not on the railway");

        String x = aTrainNotPlaced();

        try
        {
            Components.onTheEventThread(() ->
            {
                railway.moveLocomotive(null, copy.getName(), false);

                assertTrue(railway.moveLocomotive(x, copy.getName(), false), "precondition: " + x + " could not be stood there");

                return null;
            });

            Components.settle();

            assertEquals(Components.onTheEventThread(() -> session.trainOnTheRailway(full, model.getAutoLayout())), x,
                "precondition: the railway does not hold " + x + " on the square");

            makeActive(x);

            assertNull(placeItemOn(panel, full, x), "Place " + x + " is offered on the square the railway has it standing on");

            makeActive(inTheSetup);

            assertNotNull(placeItemOn(panel, full, inTheSetup),
                "Place " + inTheSetup + " is not offered where the railway has another train");
        }
        finally
        {
            // The railway as the setup has it again, for the claims after this one
            Components.onTheEventThread(() ->
            {
                railway.moveLocomotive(null, copy.getName(), false);
                railway.moveLocomotive(inTheSetup, copy.getName(), false);

                return null;
            });

            Components.settle();
        }
    }

    /**
     * While a route runs the item is not offered at all, and it is back once the route is over; one offered at rest and
     * pressed during the run places nothing.
     *
     * MUTATION: offer the item while `aRunHoldsTheSetup()`, grayed or not, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsPlaceFollowsTheRunRule() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey empty = anEmptyStation();

        String name = aTrainNotPlaced();

        makeActive(name);

        JMenuItem atRest = placeItemOn(panel, empty, name);

        assertNotNull(atRest, "precondition: the editor's menu has no Place " + name + " at rest");

        regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, true);

        try
        {
            assertNull(placeItemOn(panel, empty, name), "the editor's menu offers Place " + name + " while a route runs");

            StationDoors.pressThoughGrayed(atRest);

            assertNull(session.getLocomotiveNameAt(empty), "a Place offered at rest put a train down during the run");
        }
        finally
        {
            regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, false);
        }

        JMenuItem after = placeItemOn(panel, empty, name);

        assertNotNull(after, "Place is not offered again once the route is over");
        assertTrue(after.isEnabled(), "Place is grayed once the route is over");
    }

    // ------------------------------------------------------------------------------------- Cancel undoes a placement

    /**
     * Cancel undoes a train placed on an empty station from the train window's list, as it undoes a drag (MT-914): the
     * station and the train stand as when the editor opened, in the setup and on the railway.
     *
     * MUTATION: leave out of `LayoutEditor.railwayAsPictured` the trains the picture lacks, and the train stays on the
     * station and this fails.
     */
    @Test(timeOut = 180000)
    public void testCancelAfterPlacingOnAnEmptyStationPutsItBack() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey empty = anEmptyStation();

        offTheRailwayAt(empty);

        String name = aTrainOffTheRailway();

        org.json.JSONObject opened = Components.onTheEventThread(() -> session.snapshotSetup());

        String nameBefore = where(name);
        String stationBefore = station(empty);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            StationDoors.placeFromTheList(panel, empty, name);

            cancelOnceOnTheRailway(name);

            assertEquals(station(empty), stationBefore, "Cancel did not bring the station back as the editor opened it");
            assertEquals(where(name), nameBefore, "Cancel did not take the placed train back off");
            // Of this train only: the closing fold writes in what earlier claims left standing on the railway alone
            assertEquals(mentions(Components.onTheEventThread(() -> session.snapshotSetup()), name), mentions(opened, name),
                "Cancel left something of the placement in the setup");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            offTheRailwayAt(empty);
        }
    }

    /**
     * A train placed on an empty station through the editor's Place <selected> is back on its own station after Cancel
     * (MT-914), come in by the same side with its tail along the same road, in the setup and on the railway.
     *
     * MUTATION: pass null for `railwayAsPictured()` in `LayoutEditor.discardAutonomyWork`, and the train stays on the
     * empty station and this fails.
     */
    @Test(timeOut = 240000)
    public void testCancelPutsAPlacedTrainBackWithItsSideAndTail() throws Exception
    {
        AutonomyEditorPanel first = theEditorsPanel();

        final TileKey tunnel = support.StandingTrain.squareNamed(session, "Tunnel");

        offTheRailwayAt(tunnel);

        String name = aTrainOffTheRailway();

        Locomotive train = model.getLocByName(name);

        Integer lengthWas = train.getTrainLength();

        TileKey empty = null;

        try
        {
            train.setTrainLength(5);

            org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPre = tunnelPreIsTheTail(tunnel);

            makeActive(name);

            JMenuItem onTunnel = placeItemOn(first, tunnel, name);

            assertNotNull(onTunnel, "precondition: the editor's menu on Tunnel has no Place " + name);

            Components.onTheEventThread(() -> { onTunnel.doClick(); return null; });

            assertStandsFromTheNorthWithItsTail(train, tunnelPre);

            Components.settle();

            assertNotNull(session.getArrivedAlong(tunnel), "precondition: the setup recorded no tail on Tunnel");

            // Saved, as the closing fold writes what the railway holds into the setup; then a second editor opens
            saveTheEditor();

            AutonomyEditorPanel panel = theEditorsPanel();

            empty = anEmptyStation();

            offTheRailwayAt(empty);

            String nameBefore = whereWithItsTail(name);
            String tunnelBefore = station(tunnel);
            String emptyBefore = station(empty);

            org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

            JMenuItem item = placeItemOn(panel, empty, name);

            assertNotNull(item, "precondition: the editor's menu has no Place " + name + " on an empty station");

            Components.onTheEventThread(() -> { item.doClick(); return null; });

            final TileKey target = empty;

            assertTrue(Components.waitFor(() -> target.equals(session.getStationIndex().squareOf(
                model.getAutoLayout().getLocomotiveLocation(train))), 20000), "precondition: " + name
                + " was not moved onto " + target + " on the railway");

            Components.settle();

            cancelTheEditor();

            assertEquals(whereWithItsTail(name), nameBefore, "Cancel did not put the train back with its side and tail");
            assertEquals(station(tunnel), tunnelBefore, "Cancel did not bring Tunnel back as the editor opened it");
            assertEquals(station(empty), emptyBefore, "Cancel did not empty the station again");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            train.setTrainLength(lengthWas);

            offTheRailwayAt(tunnel);

            if (empty != null) offTheRailwayAt(empty);
        }
    }

    /**
     * Save instead of Cancel keeps a placement on an empty station, in the setup and on the railway.
     *
     * MUTATION: have the Save button's autonomy branch call `discardAutonomyWork()` before it closes, and this fails.
     */
    @Test(timeOut = 180000)
    public void testSaveKeepsAPlacementOnAnEmptyStation() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey empty = anEmptyStation();

        offTheRailwayAt(empty);

        String name = aTrainOffTheRailway();

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            StationDoors.placeFromTheList(panel, empty, name);

            saveTheEditor();

            assertEquals(session.getLocomotiveNameAt(empty), name, "Save did not keep the placement in the setup");

            assertTrue(Components.waitFor(() -> empty.equals(session.getStationIndex().squareOf(
                model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name)))), 20000),
                "Save did not keep " + name + " on " + empty + " on the railway: " + where(name));
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            offTheRailwayAt(empty);
        }
    }

    /** How often a setup names this train. */
    private static int mentions(org.json.JSONObject setup, String name)
    {
        return setup.toString().split(java.util.regex.Pattern.quote(org.json.JSONObject.quote(name)), -1).length - 1;
    }

    /** The editor's Save, pressed, which closes it. */
    private void saveTheEditor() throws Exception
    {
        final LayoutEditor closing = editor;

        AbstractButton save = Components.withText(closing, I18n.t("layout.ui.saveChanges"));

        assertNotNull(save, "precondition: the editor has no Save button");

        StationDoors.pressAndAnswer(save, message -> null);

        assertTrue(Components.waitFor(() -> !closing.isDisplayable(), 20000), "precondition: Save did not close the editor");

        Components.settle();

        editor = null;
    }

    /** {@link #where} with the road the train's tail lies along on the railway. */
    private static String whereWithItsTail(String name)
    {
        org.traincontrol.automation.Point on = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));

        return where(name) + " along " + (on == null ? null : roadNames(on.getArrivedAlong()));
    }

    /** What the setup and the railway's copies hold on a square: the train, its facing, its side and its tail. */
    private static String station(TileKey square)
    {
        return Components.onTheEventThread(() ->
        {
            StringBuilder out = new StringBuilder("setup " + session.getLocomotiveNameAt(square));

            for (String property : new String[] {"facing", "arrivedFrom", "arrivedAlong"})
            {
                out.append(' ').append(property).append('=').append(session.getPointProperty(square, property));
            }

            for (String copy : new java.util.TreeSet<>(session.facingsFor(square).keySet()))
            {
                org.traincontrol.automation.Point point = model.getAutoLayout().getPoint(copy);

                if (point == null) continue;

                out.append("; ").append(copy).append(' ').append(point.getCurrentLocomotive() == null ? null
                    : point.getCurrentLocomotive().getName()).append(" from ").append(point.getArrivedFrom())
                    .append(" along ").append(roadNames(point.getArrivedAlong()));
            }

            return out.toString();
        });
    }

    /** A standing train on this page that the setup has there too, on a square with two facings. */
    private static TileKey aTrainThatCanTurn()
    {
        for (Map.Entry<TileKey, String> each : session.trainsWhereTheyStand().entrySet())
        {
            TileKey sq = each.getKey();

            if (PAGE.equals(sq.getPage()) && each.getValue().equals(session.getLocomotiveNameAt(sq))
                && model.getAutoLayout().getLocomotiveLocation(model.getLocByName(each.getValue())) != null
                && session.facingChoices(sq).size() == 2) return sq;
        }

        throw new AssertionError("precondition: no standing train with two facings on " + PAGE);
    }

    /** A reversal on the throttle or the handset, followed as its echo is (`followTheTurn`); asserts it turned. */
    private static void reverse(String name, String copyBefore) throws Exception
    {
        assertNotNull(followed(name), "precondition: followTheTurn turned nothing");

        assertFalse(copyOf(name).equals(copyBefore), "precondition: the reversal did not turn " + name + " on the railway");
    }

    /** A reversal followed as its echo is (`followTheTurn`), written or not; the square it was written on, or null. */
    private static Object followed(String name) throws Exception
    {
        final java.lang.reflect.Method follow = Reflect.method(TrainControlUI.class, "followTheTurn", String.class);
        final Object written = Components.onTheEventThread(() -> follow.invoke(ui, name));

        Components.settle();

        return written;
    }

    /** The railway's copy this train stands on, or null. */
    private static String copyOf(String name)
    {
        org.traincontrol.automation.Point at = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));

        return at == null ? null : at.getName();
    }

    /**
     * A reversal on the throttle followed while the editor is open is the train's, not the editor's work: with nothing
     * edited Cancel asks nothing, and the train stays turned on the railway, in the setup and in the crash note.
     *
     * MUTATION: compare `autonomyAsOpened` itself in `LayoutEditor.hasUnsavedAutonomyWork`, and Cancel asks and turns
     * it back.
     */
    @Test(timeOut = 300000)
    public void testCancelAsksNothingAfterOnlyAThrottleTurn() throws Exception
    {
        theEditorsPanel();

        final TileKey square = aTrainThatCanTurn();
        final String name = session.getLocomotiveNameAt(square);
        final String copyBefore = copyOf(name);

        try
        {
            reverse(name, copyBefore);

            final String turned = copyOf(name);
            final Object facing = session.getFacing(square);
            final org.json.JSONObject note = session.getStore().unfinishedEdit();

            assertNotNull(note, "precondition: no crash note while the editor is open");
            assertEquals(note.getJSONObject("configurations").getJSONObject(session.getStore().getActiveConfiguration())
                .getJSONObject("points").getJSONObject(square.toString()).opt("facing"), String.valueOf(facing),
                "the crash note would turn " + name + " back after a restart");

            String asked = cancelTheEditor();

            assertNull(asked, "Cancel after only a throttle reversal of " + name + " asked about unsaved work");
            assertEquals("railway " + copyOf(name) + ", setup facing " + session.getFacing(square),
                "railway " + turned + ", setup facing " + facing, "Cancel turned " + name + " back on " + square);
        }
        finally
        {
            standBack(name, copyBefore, null, null, null);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /**
     * Cancel after an edit throws the edit away but keeps a reversal followed meanwhile, on the railway and in the setup,
     * so the train faces the way its decoder runs; the question does not name it as going back.
     *
     * MUTATION: return an empty set from `LayoutEditor.trainsTurnedSince`, and Cancel turns it back.
     */
    @Test(timeOut = 300000)
    public void testCancelKeepsAThrottleTurnMadeWhileOpen() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        final TileKey square = aTrainThatCanTurn();
        final String name = session.getLocomotiveNameAt(square);
        final String copyBefore = copyOf(name);
        final String unplaced = aTrainNotPlaced();
        final TileKey empty = anEmptyStation();

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            // An edit, so Cancel has something to throw away
            placeFromTheMenu(panel, empty, unplaced);

            Components.settle();

            reverse(name, copyBefore);

            final String turned = copyOf(name);
            final Object facing = session.getFacing(square);

            String asked = cancelTheEditor();

            assertNotNull(asked, "precondition: Cancel did not ask about the placement");
            assertFalse(asked.contains(name), "the question names " + name + " as going back: " + asked);
            assertEquals("railway " + copyOf(name) + ", setup facing " + session.getFacing(square),
                "railway " + turned + ", setup facing " + facing, "Cancel after an edit turned " + name + " back on "
                + square + " (before the reversal: " + copyBefore + ")");
            assertNull(copyOf(unplaced), "precondition: Cancel kept the placement of " + unplaced);
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            offTheRailway(java.util.Collections.singleton(unplaced));

            standBack(name, copyBefore, null, null, null);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /**
     * The editor moves a train, the throttle then reverses it, then Cancel or either Discard: the train goes back to
     * the square it stood on when the editor opened, turned round from the way it faced there, as its decoder now runs,
     * on the railway and in the setup; the question names it, for the move.
     *
     * MUTATION: return an empty set from `LayoutEditor.trainsTurnedSince`, and it goes back facing as at opening.
     */
    @Test(timeOut = 400000)
    public void testADiscardPutsAMovedTrainBackFacingTheWayItWasTurned() throws Exception
    {
        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            forEachWayOutWithATrainThatCanTurn((how, panel, square, name, copyBefore) ->
            {
                final TileKey elsewhere = anEmptyStationWhere(each -> session.trainsWhereTheyStand().get(each) == null
                    && session.facingChoices(each).size() == 2);
                final org.traincontrol.automationui.TilePorts.Side way = theOtherWay(square);

                placeFromTheMenu(panel, elsewhere, name);

                Components.settle();

                reverse(name, copyOf(name));

                java.util.List<String> asked = leave(how);

                if (how == 0)
                {
                    String expected = I18n.t("autosetup.ui.confirmExitWithoutSaving") + "\n\n"
                        + I18n.f("autosetup.ui.trainsGoBackOnDiscard", name);

                    assertEquals(asked, java.util.Collections.singletonList(expected), "Cancel's question does not"
                        + " name " + name + " alone as going back");
                }
                else
                {
                    assertEquals(asked.size(), 1, "precondition: " + DISCARDS[how] + " asked " + asked);
                }

                assertEquals(standingAndFacing(name), "on " + square + " facing " + way + ", setup facing " + way,
                    DISCARDS[how] + " after the editor moved " + name + " to " + elsewhere + " and the throttle"
                    + " reversed it there did not put it back turned round (it stood on " + copyBefore + ")");
            });
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);
        }
    }

    /**
     * The editor's Facing door turns a train, the throttle then reverses it, Cancel: nothing is asked, and the train
     * keeps the facing it has now, as both turns are kept.
     *
     * MUTATION: leave the `turned(name)` call out of `AutonomySession.setFacingAndMove`, and Cancel turns it round.
     */
    @Test(timeOut = 300000)
    public void testCancelKeepsTheFacingAfterAFacingChangeAndAThrottleTurn() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        final TileKey square = aTrainThatCanTurn();
        final String name = session.getLocomotiveNameAt(square);
        final String copyBefore = copyOf(name);

        try
        {
            faceFromTheMenu(panel, square, name, theOtherWay(square));

            reverse(name, copyOf(name));

            final String now = standingAndFacing(name);

            assertNull(cancelTheEditor(), "Cancel asked after a facing change of " + name + " and a reversal");
            assertEquals(standingAndFacing(name), now, "Cancel changed the facing of " + name);
        }
        finally
        {
            standBack(name, copyBefore, null, null, null);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /** An edit of a train in the editor, before its reversal. */
    private interface EditOfTheTrain
    {
        void run(AutonomyEditorPanel panel, TileKey square, String name) throws Exception;
    }

    /**
     * The editor edits a train, the throttle then reverses it (followed whether or not it can be written where the
     * editor put it), Cancel: the train goes back to the square it stood on at opening turned round, on the railway and
     * in the setup, as its decoder now runs.
     */
    private void cancelKeepsATurnAfter(EditOfTheTrain edit) throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        final TileKey square = aTrainThatCanTurn();
        final String name = session.getLocomotiveNameAt(square);
        final String copyBefore = copyOf(name);
        final org.traincontrol.automationui.TilePorts.Side way = theOtherWay(square);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            edit.run(panel, square, name);

            Components.settle();

            final String edited = standingAndFacing(name);
            final Object written = followed(name);

            assertNotNull(cancelTheEditor(), "precondition: Cancel did not ask about the edit");
            assertEquals(standingAndFacing(name), "on " + square + " facing " + way + ", setup facing " + way,
                "Cancel after the editor left " + name + " " + edited + " and the throttle reversed it (written on "
                + written + ") did not put it back turned round (it stood on " + copyBefore + ")");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            standBack(name, copyBefore, null, null, null);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /**
     * The editor lifts a train, the throttle reverses it, Cancel: it goes back turned round, though no square held it
     * when it was reversed.
     *
     * MUTATION: in `TrainControlUI.followTheTurn`, count the turn only where `flipFacing` wrote one, and it goes back
     * facing as at opening.
     */
    @Test(timeOut = 300000)
    public void testCancelKeepsATurnOfATrainTheEditorLifted() throws Exception
    {
        final java.lang.reflect.Method remove = Reflect.method(AutonomyEditorPanel.class, "removeLocomotive",
            TileKey.class);

        cancelKeepsATurnAfter((panel, square, name) -> Components.onTheEventThread(() -> remove.invoke(panel, square)));
    }

    /**
     * The editor moves a train onto a station with one facing, the throttle reverses it, Cancel: it goes back turned
     * round, though it could not be turned where it stood.
     *
     * MUTATION: in `TrainControlUI.followTheTurn`, count the turn only where `flipFacing` wrote one, and it goes back
     * facing as at opening.
     */
    @Test(timeOut = 300000)
    public void testCancelKeepsATurnOfATrainMovedWhereItCannotTurn() throws Exception
    {
        cancelKeepsATurnAfter((panel, square, name) -> placeFromTheMenu(panel, anEmptyStationWhere(each
            -> session.trainsWhereTheyStand().get(each) == null && session.facingChoices(each).size() == 1), name));
    }

    /**
     * The throttle reverses a train, it is then renamed, Cancel: nothing is asked, and it keeps the turn on the railway
     * and in the setup, as the turn is the locomotive's whatever it is called.
     *
     * MUTATION: in `AutonomySession.turnKey`, key by the name alone, and Cancel asks and turns it back.
     */
    @Test(timeOut = 300000)
    public void testCancelKeepsATurnOfATrainRenamedSince() throws Exception
    {
        theEditorsPanel();

        final TileKey square = aTrainThatCanTurn();
        final String name = session.getLocomotiveNameAt(square);
        final String renamed = name + " renamed";
        final String copyBefore = copyOf(name);
        final Object openBefore = Reflect.get(TrainControlUI.class, ui, "openEditor");

        // As the Edit menu registers it, so the rename reaches its snapshots
        Reflect.set(TrainControlUI.class, ui, "openEditor", editor);

        try
        {
            reverse(name, copyBefore);

            final String turned = standingAndFacing(name);

            SwingUtilities.invokeAndWait(() -> assertTrue(model.renameLoc(name, renamed), "precondition: rename refused"));

            Components.settle();

            assertNull(cancelTheEditor(), "Cancel after a reversal of " + name + " and its rename asked about unsaved"
                + " work");
            assertEquals(standingAndFacing(renamed), turned, "Cancel turned " + name + " back after its rename");
        }
        finally
        {
            Reflect.set(TrainControlUI.class, ui, "openEditor", openBefore);

            if (model.getLocByName(renamed) != null) SwingUtilities.invokeAndWait(() -> model.renameLoc(renamed, name));

            Components.settle();

            standBack(name, copyBefore, null, null, null);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /** The track editor on this page, as the Edit menu opens it. */
    private LayoutEditor theTrackEditor() throws Exception
    {
        final org.traincontrol.base.LayoutDiagram diagram = model.getLayout(PAGE);

        editor = Components.onTheEventThread(() ->
        {
            LayoutEditor built = new LayoutEditor(diagram, 30, ui, 0);

            built.render();

            return built;
        });

        Components.settle();

        return editor;
    }

    /**
     * Ctrl+Z in the track editor puts back the page's setup as it was before the edit, but a train turned on the
     * throttle meanwhile keeps its facing.
     *
     * MUTATION: drop the turning loop at the end of `AutonomySession.restorePage`, and Ctrl+Z turns it back.
     */
    @Test(timeOut = 300000)
    public void testTheTrackEditorsUndoKeepsAThrottleTurn() throws Exception
    {
        final TileKey square = aTrainThatCanTurn();
        final String name = session.getLocomotiveNameAt(square);
        final String copyBefore = copyOf(name);
        final LayoutEditor open = theTrackEditor();
        final java.lang.reflect.Method snapshot = Reflect.method(LayoutEditor.class, "snapshotLayout");

        try
        {
            Components.onTheEventThread(() -> { snapshot.invoke(open); return null; });

            reverse(name, copyBefore);

            final String turned = standingAndFacing(name);

            Components.onTheEventThread(() -> { open.undo(); return null; });

            Components.settle();

            assertEquals(standingAndFacing(name), turned, "Ctrl+Z in the track editor turned " + name + " back");
        }
        finally
        {
            standBack(name, copyBefore, null, null, null);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /** A standing train that can turn (`aTrainThatCanTurn`), with what a claim about its turn puts back after. */
    private static final class Turnable
    {
        final TileKey square = aTrainThatCanTurn();
        final String name = session.getLocomotiveNameAt(square);
        final String copyBefore = copyOf(name);
        final org.traincontrol.automationui.TilePorts.Side way = theOtherWay(square);
        final org.traincontrol.base.LayoutDiagramComponent tile = model.getLayout(PAGE).getComponent(square.getX(),
            square.getY());

        /** Where it stands and faces once turned round on its square (`standingAndFacing`). */
        String turnedRound()
        {
            return "on " + square + " facing " + way + ", setup facing " + way;
        }

        /** Its tile, railway and setup as they were. */
        void putBack()
        {
            putTheSquareBack(square, tile);

            standBack(name, copyBefore, null, null, null);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /** A track edit of the square a train stands on, in the open track editor. */
    private interface TrackEdit
    {
        void run(LayoutEditor open, TileKey square) throws Exception;
    }

    /** A track edit as the editor's gestures make one: the undo point, the change, and the setup written if it changed. */
    private static void asATrackEdit(LayoutEditor open, java.util.concurrent.Callable<Boolean> change) throws Exception
    {
        final java.lang.reflect.Method snapshot = Reflect.method(LayoutEditor.class, "snapshotLayout");
        final java.lang.reflect.Method remember = Reflect.method(LayoutEditor.class, "rememberAutonomy",
            AutonomySession.class);

        Components.onTheEventThread(() ->
        {
            snapshot.invoke(open);

            if (change.call()) remember.invoke(open, session);

            return null;
        });

        Components.settle();
    }

    /** As the track editor's Delete does for one square (`LayoutEditorSelection.delete`): the tile and its record go. */
    private static void deleteTheSquare(LayoutEditor open, TileKey square) throws Exception
    {
        asATrackEdit(open, () ->
        {
            model.getLayout(PAGE).addComponent(null, square.getX(), square.getY());

            return session.forgetTiles(java.util.Collections.singletonList(square));
        });
    }

    /**
     * As the track editor's move of a picked square does (`LayoutEditorSelection.moveSelection`): the tile and its record
     * onto an empty square beside it.
     */
    private static void moveTheSquare(LayoutEditor open, TileKey square) throws Exception
    {
        final org.traincontrol.base.LayoutDiagram diagram = model.getLayout(PAGE);
        TileKey empty = null;

        for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
        {
            int x = square.getX() + step[0];
            int y = square.getY() + step[1];

            if (empty == null && x >= 0 && y >= 0 && x < diagram.getSx() && y < diagram.getSy()
                && diagram.getComponent(x, y) == null) empty = new TileKey(PAGE, x, y);
        }

        assertNotNull(empty, "precondition: no empty square beside " + square);

        final TileKey onto = empty;

        asATrackEdit(open, () ->
        {
            org.traincontrol.base.LayoutDiagramComponent moved = new org.traincontrol.base.LayoutDiagramComponent(
                diagram.getComponent(square.getX(), square.getY()));

            diagram.addComponent(null, square.getX(), square.getY());

            moved.setX(onto.getX());
            moved.setY(onto.getY());

            diagram.addComponent(moved, onto.getX(), onto.getY());

            session.moveTile(square, onto);

            return true;
        });
    }

    /** The page's tile back on its square where a claim left it out, and the session built from it again. */
    private static void putTheSquareBack(TileKey square, org.traincontrol.base.LayoutDiagramComponent tile)
    {
        Components.onTheEventThread(() ->
        {
            org.traincontrol.base.LayoutDiagram diagram = model.getLayout(PAGE);

            if (diagram.getComponent(square.getX(), square.getY()) == null)
            {
                diagram.addComponent(tile, square.getX(), square.getY());

                session.rebuild();
            }

            return null;
        });

        Components.settle();
    }

    /**
     * A track edit takes the square from under a standing train, the throttle then reverses it (counted, though nothing
     * can be written where no square holds it), Ctrl+Z: the train is on its square again turned round, on the railway
     * and in the setup, as its decoder runs.
     */
    private void theTrackEditorsUndoKeepsATurnAfter(TrackEdit edit) throws Exception
    {
        final Turnable train = new Turnable();
        final LayoutEditor open = theTrackEditor();

        try
        {
            edit.run(open, train.square);

            final Object written = followed(train.name);

            Components.onTheEventThread(() -> { open.undo(); return null; });

            Components.settle();

            assertEquals(standingAndFacing(train.name), train.turnedRound(), "Ctrl+Z after a track edit of the square"
                + " under " + train.name + " and a reversal (written on " + written + ") did not put it back turned round"
                + " (it stood on " + train.copyBefore + ")");
        }
        finally
        {
            train.putBack();
        }
    }

    /**
     * The track editor deletes the square under a train, the throttle reverses it, Ctrl+Z: it is back turned round.
     *
     * MUTATION: in `AutonomySession.restorePage`, turn the trains before `touched()`, and Ctrl+Z puts it back facing as
     * before.
     */
    @Test(timeOut = 300000)
    public void testTheTrackEditorsUndoKeepsATurnOfATrainWhoseSquareWasDeleted() throws Exception
    {
        theTrackEditorsUndoKeepsATurnAfter(testTheTrainWindow::deleteTheSquare);
    }

    /**
     * The track editor moves the station square under a train, the throttle reverses it, Ctrl+Z: it is back on the
     * square it stood on, turned round.
     *
     * MUTATION: in `AutonomySession.restorePage`, turn the trains before `touched()`, and Ctrl+Z puts it back facing as
     * before.
     */
    @Test(timeOut = 300000)
    public void testTheTrackEditorsUndoKeepsATurnOfATrainWhoseSquareWasMoved() throws Exception
    {
        theTrackEditorsUndoKeepsATurnAfter(testTheTrainWindow::moveTheSquare);
    }

    /** Leaves the track editor without saving the given way (`DISCARDS`), answering the question with throwing away. */
    private void leaveTheTrackEditor(int how, LayoutEditor open) throws Exception
    {
        if (how == 0)
        {
            assertNotNull(cancelTheEditor(), "precondition: Cancel did not ask about the track edit");

            return;
        }

        try (support.Answering answering = support.Answering.start(
            message -> support.Answering.press(I18n.t("layout.ui.switchDiscard"))))
        {
            if (how == 1)
            {
                final java.lang.reflect.Method leaveFor = Reflect.method(LayoutEditor.class, "leaveFor", String.class,
                    boolean.class);

                Components.onTheEventThread(() -> leaveFor.invoke(open, PAGE, false));
            }
            else
            {
                assertTrue(Components.onTheEventThread(() -> open.maySettleBeforeExit()), "the exit's question refused");

                Components.onTheEventThread(() -> { open.completeExitDiscard(); return null; });
            }

            assertEquals(answering.asked().size(), 1, "precondition: " + DISCARDS[how] + " asked " + answering.asked());
        }

        // The switch's arrival waits for the pages read again
        if (how == 2) support.FrozenEditor.dispose(open);
    }

    /** Waits for the pages to be read again after a track edit ends (`layoutEditingComplete`); takes up its session. */
    private static void reloaded(AutonomySession was) throws Exception
    {
        final java.util.concurrent.atomic.AtomicInteger underWay = (java.util.concurrent.atomic.AtomicInteger)
            Reflect.get(TrainControlUI.class, ui, "refreshesUnderWay");

        assertTrue(Components.waitFor(() -> underWay.get() == 0 && Components.onTheEventThread(() ->
        {
            Object now = Reflect.get(TrainControlUI.class, ui, "autonomySession");

            return now != null && now != was;
        }), 60000), "precondition: the pages were not read again after the track editor");

        Components.settle();

        session = Components.onTheEventThread(() -> ui.getAutonomySession());
    }

    /** The facing the setup on disk records on a square, as a restart reads it. */
    private static String facingOnDisk(TileKey square) throws Exception
    {
        org.traincontrol.automationui.AutonomyCompanionStore store
            = new org.traincontrol.automationui.AutonomyCompanionStore(sandbox.getFolder());

        store.load();

        org.json.JSONObject point = store.getConfiguration(store.getActiveConfiguration()).getJSONObject("points")
            .optJSONObject(square.toString());

        return point == null ? null : point.optString("facing", null);
    }

    /**
     * The track editor deletes the square under a standing train, the throttle reverses it, then Cancel, a page switch's
     * Discard or the exit's Discard: the edit is thrown away but the turn kept.
     *
     * MUTATION: in `LayoutEditor.undoAutonomyEdits`, restore `autonomyAsOpened` itself and stand the railway as pictured
     * unturned (`railwayAsPictured(false)`), and each door puts it back facing as before.
     */
    @Test(timeOut = 600000)
    public void testTheTrackEditorsDiscardsKeepATurnOfATrainWhoseSquareWasDeleted() throws Exception
    {
        theTrackEditorsDiscardsKeepATurnAfter(testTheTrainWindow::deleteTheSquare);
    }

    /**
     * The same where the track edit leaves the train's square alone, so the reversal is written as it happens.
     *
     * MUTATION: in `LayoutEditor.undoAutonomyEdits`, restore `autonomyAsOpened` itself and stand the railway as pictured
     * unturned (`railwayAsPictured(false)`), and each door turns it back.
     */
    @Test(timeOut = 600000)
    public void testTheTrackEditorsDiscardsKeepAThrottleTurn() throws Exception
    {
        theTrackEditorsDiscardsKeepATurnAfter((open, square) -> asATrackEdit(open, () -> false));
    }

    /**
     * The track editor deletes two station squares, and between a page switch's Discard and the page's arrival (the
     * editor's `changingPage` latch is up) Ctrl+Z reaches its key handler: nothing is restored or saved, so the second
     * station is still gone from the diagram and from the setup on disk.  The latch is raised as `leaveFor` raises it:
     * the real gap is the worker's re-read, which a test cannot hold open.
     *
     * MUTATION: take `switching || changingPage` out of the guard in `LayoutEditor.formKeyPressed` above the selection
     * shortcuts, and Ctrl+Z puts the second station back, on disk too.
     */
    @Test(timeOut = 600000)
    public void testCtrlZWhileAPageSwitchIsUnderWayRestoresAndSavesNothing() throws Exception
    {
        final TileKey first = anEmptyStation();
        final TileKey second = anEmptyStationBut(first);
        final org.traincontrol.base.LayoutDiagramComponent firstTile = model.getLayout(PAGE).getComponent(first.getX(),
            first.getY());
        final org.traincontrol.base.LayoutDiagramComponent secondTile = model.getLayout(PAGE).getComponent(
            second.getX(), second.getY());
        final LayoutEditor open = theTrackEditor();
        final java.lang.reflect.Method key = Reflect.method(LayoutEditor.class, "formKeyPressed",
            java.awt.event.KeyEvent.class);

        assertNotNull(pointOnDisk(second), "precondition: the second station has no record on disk");

        try
        {
            deleteTheSquare(open, first);
            deleteTheSquare(open, second);

            assertNull(pointOnDisk(second), "precondition: the second delete did not reach the setup on disk");

            Reflect.set(LayoutEditor.class, open, "changingPage", true);

            Components.onTheEventThread(() ->
            {
                key.invoke(open, new java.awt.event.KeyEvent(open, java.awt.event.KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(), java.awt.event.InputEvent.CTRL_DOWN_MASK, java.awt.event.KeyEvent.VK_Z,
                    'z'));

                return null;
            });

            Components.settle();

            assertNull(pointOnDisk(second), "Ctrl+Z while a page switch was under way put the page's setup back as it"
                + " was before the last edit and saved it: " + second + " is on disk again");

            assertNull(model.getLayout(PAGE).getComponent(second.getX(), second.getY()), "Ctrl+Z while a page switch"
                + " was under way put the tile of " + second + " back");
        }
        finally
        {
            Reflect.set(LayoutEditor.class, open, "changingPage", false);

            support.FrozenEditor.dispose(open);

            editor = null;

            putTheSquareBack(first, firstTile);
            putTheSquareBack(second, secondTile);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /**
     * Between a page switch's Discard and the page's arrival the track editor's clicks write nothing: a palette piece
     * still armed puts nothing down, a middle click turns nothing, and a right click opens no menu (whose Undo, Paste
     * and Delete would write the page being left).  The latch is raised as `leaveFor` raises it, as the keys' claim does.
     *
     * MUTATION: take the `switching || changingPage` guard out of `LayoutEditor.receiveClickEvent`, and the piece lands.
     */
    @Test(timeOut = 300000)
    public void testAClickWhileAPageSwitchIsUnderWayWritesNothing() throws Exception
    {
        final LayoutEditor open = theTrackEditor();
        final GapSquares at = gapSquares(open);

        try
        {
            Components.onTheEventThread(() ->
            {
                org.traincontrol.gui.LayoutLabel piece = Components.all(
                    (java.awt.Component) Reflect.get(LayoutEditor.class, open, "newComponents"),
                    org.traincontrol.gui.LayoutLabel.class, each -> each.getComponent() != null
                    && each.getComponent().getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT)
                    .get(0);

                open.initCopy(piece, piece.getComponent(), false);

                return null;
            });

            assertTrue(Components.onTheEventThread(() -> open.hasToolFlag()), "precondition: no palette piece armed");

            Reflect.set(LayoutEditor.class, open, "changingPage", true);
            Reflect.set(LayoutEditor.class, open, "popup", null);

            Components.onTheEventThread(() ->
            {
                open.receiveClickEvent(click(at.empty, java.awt.event.MouseEvent.BUTTON1), at.empty);
                open.receiveClickEvent(click(at.track, java.awt.event.MouseEvent.BUTTON2), at.track);
                open.receiveClickEvent(click(at.track, java.awt.event.MouseEvent.BUTTON3), at.track);

                return null;
            });

            Components.settle();

            assertNull(at.emptyNow(), "a click with a palette piece armed put one down on the page being left while a"
                + " page switch was under way");
            assertEquals(at.trackNow(), at.trackAsWas, "a middle click turned a tile of the page being left while a"
                + " page switch was under way");
            assertNull(Reflect.get(LayoutEditor.class, open, "popup"), "a right click opened the square's menu, with"
                + " its Undo, Paste and Delete, while a page switch was under way");
        }
        finally
        {
            leaveTheGap(open, at);
        }
    }

    /**
     * Between a page switch's Discard and the page's arrival a press on a tile picks nothing up.
     *
     * MUTATION: take the `switching || changingPage` guard out of `LayoutEditor.beginDrag`, and the press lifts the tile.
     */
    @Test(timeOut = 300000)
    public void testAPressWhileAPageSwitchIsUnderWayPicksNothingUp() throws Exception
    {
        final LayoutEditor open = theTrackEditor();
        final GapSquares at = gapSquares(open);

        try
        {
            Reflect.set(LayoutEditor.class, open, "changingPage", true);

            Components.onTheEventThread(() ->
            {
                open.beginDrag(press(at.track), at.track);

                return null;
            });

            assertFalse(Components.onTheEventThread(() -> open.hasToolFlag()), "a press picked up a tile of the page"
                + " being left while a page switch was under way");
        }
        finally
        {
            leaveTheGap(open, at);
        }
    }

    /**
     * A tile dragged when a page switch's Discard comes, and let go before the page arrives, is put down nowhere: the
     * page being left keeps it where it stood.
     *
     * MUTATION: take the `switching || changingPage` guard out of `LayoutEditor.endDrag`, and the tile moves.
     */
    @Test(timeOut = 300000)
    public void testADragLetGoWhileAPageSwitchIsUnderWayMovesNothing() throws Exception
    {
        final LayoutEditor open = theTrackEditor();
        final GapSquares at = gapSquares(open);

        try
        {
            Components.onTheEventThread(() ->
            {
                open.receiveMoveEvent(press(at.track), at.track);
                open.beginDrag(press(at.track), at.track);

                return null;
            });

            assertNotNull(Reflect.get(LayoutEditor.class, open, "dragWindow"), "precondition: the press began no drag");

            Reflect.set(LayoutEditor.class, open, "changingPage", true);

            Components.onTheEventThread(() ->
            {
                open.receiveMoveEvent(press(at.empty), at.empty);
                open.endDrag(press(at.empty), at.empty);

                return null;
            });

            Components.settle();

            assertNull(at.emptyNow(), "a drag let go while a page switch was under way put a tile down on the page"
                + " being left");
            assertEquals(at.trackNow(), at.trackAsWas, "a drag let go while a page switch was under way took the tile"
                + " away from where it stood");
        }
        finally
        {
            leaveTheGap(open, at);
        }
    }

    /**
     * A right click handled just before a page switch begins posts its menu; the menu then opens on no page being
     * left: the latch is raised in the same turn of the event queue as the click, as `leaveFor` raises it.
     *
     * MUTATION: take the `switching || changingPage` guard out of the task `LayoutEditor.receiveClickEvent` posts for a
     * right click, and the menu opens.
     */
    @Test(timeOut = 300000)
    public void testARightClickPostedBeforeAPageSwitchOpensNoMenu() throws Exception
    {
        inTheGapSquares((open, at) ->
        {
            Reflect.set(LayoutEditor.class, open, "popup", null);

            Components.onTheEventThread(() ->
            {
                open.receiveClickEvent(click(at.track, java.awt.event.MouseEvent.BUTTON3), at.track);

                Reflect.set(LayoutEditor.class, open, "changingPage", true);

                return null;
            });

            Components.settle();

            assertNull(Reflect.get(LayoutEditor.class, open, "popup"), "a right click posted before a page switch began"
                + " opened its menu, with its Undo, Paste and Delete, on the page being left");
        });
    }

    /**
     * A page switch closes the square menu left open on the page being left, as it asks nothing first.
     *
     * MUTATION: drop `if (popup != null) popup.setVisible(false)` from `LayoutEditor.leaveFor`, and the menu stays.
     */
    @Test(timeOut = 300000)
    public void testAPageSwitchClosesTheOpenSquareMenu() throws Exception
    {
        inTheGapSquares((open, at) ->
        {
            Components.onTheEventThread(() ->
            {
                open.receiveClickEvent(click(at.track, java.awt.event.MouseEvent.BUTTON3), at.track);

                return null;
            });

            Components.settle();

            final JPopupMenu menu = (JPopupMenu) Reflect.get(LayoutEditor.class, open, "popup");

            assertNotNull(menu, "precondition: the right click opened no menu");
            assertTrue(Components.onTheEventThread(menu::isVisible), "precondition: the menu is not showing");

            final java.lang.reflect.Method leave = Reflect.method(LayoutEditor.class, "leaveFor", String.class,
                boolean.class);
            final AutonomySession was = session;

            boolean stillOpen = Components.onTheEventThread(() ->
            {
                leave.invoke(open, PAGE, false);

                return menu.isVisible();
            });

            assertFalse(stillOpen, "a page switch left the square menu open on the page being left");

            // The switch reads the pages again, which replaces the setup the other claims hold
            reloaded(was);
        });
    }

    /** A claim's body on a track editor and its two squares; the latch lowered and the squares put back after. */
    private interface InTheGap
    {
        void run(LayoutEditor open, GapSquares at) throws Exception;
    }

    private void inTheGapSquares(InTheGap body) throws Exception
    {
        final LayoutEditor open = theTrackEditor();
        final GapSquares at = gapSquares(open);

        try
        {
            body.run(open, at);
        }
        finally
        {
            leaveTheGap(open, at);
        }
    }

    private static GapSquares gapSquares(LayoutEditor open) throws Exception
    {
        final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) Reflect.get(LayoutEditor.class,
            open, "grid");
        final org.traincontrol.base.LayoutDiagram diagram = model.getLayout(PAGE);

        GapSquares at = Components.onTheEventThread(() ->
        {
            GapSquares found = new GapSquares();

            for (int x = 0; x < diagram.getSx(); x++)
            {
                for (int y = 0; y < diagram.getSy(); y++)
                {
                    org.traincontrol.base.LayoutDiagramComponent tile = diagram.getComponent(x, y);
                    org.traincontrol.gui.LayoutLabel square = grid.getValueAt(x, y);

                    if (square == null) continue;

                    if (tile == null && found.empty == null)
                    {
                        found.empty = square;
                        found.ex = x;
                        found.ey = y;
                    }
                    else if (tile != null && found.track == null && square.getComponent() != null
                        && tile.getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT
                        && !session.getReducer().getPoints().containsKey(new TileKey(PAGE, x, y)))
                    {
                        found.track = square;
                        found.tx = x;
                        found.ty = y;
                        found.trackTile = new org.traincontrol.base.LayoutDiagramComponent(tile);
                    }
                }
            }

            return found;
        });

        assertTrue(at.track != null && at.empty != null, "precondition: no plain straight and empty square on " + PAGE);

        at.trackAsWas = at.trackNow();

        return at;
    }

    /** Lowers the latch, closes the editor and puts both squares back as they were. */
    private void leaveTheGap(LayoutEditor open, GapSquares at) throws Exception
    {
        Reflect.set(LayoutEditor.class, open, "changingPage", false);

        Components.onTheEventThread(() ->
        {
            JPopupMenu menu = (JPopupMenu) Reflect.get(LayoutEditor.class, open, "popup");

            if (menu != null) menu.setVisible(false);

            org.traincontrol.base.LayoutDiagram diagram = model.getLayout(PAGE);

            diagram.addComponent(null, at.ex, at.ey);

            if (!at.trackAsWas.equals(at.trackNow())) diagram.addComponent(at.trackTile, at.tx, at.ty);

            return null;
        });

        support.FrozenEditor.dispose(open);

        editor = null;

        Components.settle();
    }

    private static java.awt.event.MouseEvent click(java.awt.Component on, int button)
    {
        return new java.awt.event.MouseEvent(on, java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
            1, 1, 1, false, button);
    }

    private static java.awt.event.MouseEvent press(java.awt.Component on)
    {
        return new java.awt.event.MouseEvent(on, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1);
    }

    /** A plain track square off the setup's Points and an empty square of the page, with the editor's squares on them. */
    private static final class GapSquares
    {
        org.traincontrol.gui.LayoutLabel track;
        org.traincontrol.gui.LayoutLabel empty;
        int tx, ty, ex, ey;
        org.traincontrol.base.LayoutDiagramComponent trackTile;
        String trackAsWas;

        String trackNow()
        {
            return Components.onTheEventThread(() -> describe(model.getLayout(PAGE).getComponent(tx, ty)));
        }

        String emptyNow()
        {
            return Components.onTheEventThread(() -> describe(model.getLayout(PAGE).getComponent(ex, ey)));
        }

        static String describe(org.traincontrol.base.LayoutDiagramComponent tile)
        {
            return tile == null ? null : tile.getType() + " turned " + tile.getOrientation();
        }
    }

    /** What the setup on disk records on a square, as a restart reads it, or null. */
    private static String pointOnDisk(TileKey square) throws Exception
    {
        org.traincontrol.automationui.AutonomyCompanionStore store
            = new org.traincontrol.automationui.AutonomyCompanionStore(sandbox.getFolder());

        store.load();

        org.json.JSONObject point = store.getConfiguration(store.getActiveConfiguration()).getJSONObject("points")
            .optJSONObject(square.toString());

        return point == null ? null : point.toString();
    }

    /**
     * A track edit, the throttle reverses the train on the square edited, then Cancel, a page switch's Discard or the
     * exit's Discard: the edit is thrown away but the turn kept, on the railway and in the setup the pages read again
     * build, and on disk where the exit leaves it.
     */
    private void theTrackEditorsDiscardsKeepATurnAfter(TrackEdit edit) throws Exception
    {
        for (int how = 0; how < DISCARDS.length; how++)
        {
            final Turnable train = new Turnable();
            final AutonomySession was = session;
            final LayoutEditor open = theTrackEditor();

            try
            {
                edit.run(open, train.square);

                followed(train.name);

                leaveTheTrackEditor(how, open);

                if (how == 2)
                {
                    assertEquals(facingOnDisk(train.square), train.way.name(), "the exit's Discard after a track edit of"
                        + " the square under " + train.name + " and a reversal left it on disk facing as before");

                    // As the restart reads the pages
                    Components.onTheEventThread(() -> { ui.layoutEditingComplete(); return null; });
                }

                reloaded(was);

                assertEquals(standingAndFacing(train.name), train.turnedRound(), DISCARDS[how] + " after a track edit of"
                    + " the square under " + train.name + " and a reversal did not keep the turn (it stood on "
                    + train.copyBefore + ")");
            }
            finally
            {
                support.FrozenEditor.dispose(open);

                editor = null;

                train.putBack();
            }
        }
    }

    /**
     * The track editor deletes the square under a standing train and the throttle reverses it: the crash note turns it
     * by the build as the edit began, so a restart's revert puts it on its square turned round, as Cancel would.
     *
     * MUTATION: in `AutonomySession.turned`, turn the note by `choicesWhereTrainsStand()`, and the note keeps it facing
     * as before.
     */
    @Test(timeOut = 300000)
    public void testTheCrashNoteKeepsATurnOfATrainWhoseSquareTheTrackEditorDeleted() throws Exception
    {
        final Turnable train = new Turnable();

        try
        {
            deleteTheSquare(theTrackEditor(), train.square);

            followed(train.name);

            org.json.JSONObject note = Components.onTheEventThread(() -> session.getStore().unfinishedEdit());

            assertNotNull(note, "precondition: the track editor wrote no crash note");

            org.json.JSONObject point = note.getJSONObject("configurations")
                .getJSONObject(session.getStore().getActiveConfiguration()).getJSONObject("points")
                .getJSONObject(train.square.toString());

            assertEquals(point.optString("facing", null), train.way.name(), "the crash note after the track editor"
                + " deleted the square under " + train.name + " and the throttle reversed it keeps it facing as before");
        }
        finally
        {
            train.putBack();
        }
    }

    /**
     * The editor lifts a train, the throttle reverses it, Place puts it back on its square, Save: it stands turned
     * round, on the railway and in the setup, as its decoder runs.
     *
     * MUTATION: leave `headingOfALiftedTrain` out of `AutonomyEditorPanel.placeLocomotive`, and Save keeps another
     * facing.
     */
    @Test(timeOut = 300000)
    public void testATrainPlacedBackAfterATurnWhileLiftedIsSavedTurned() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        final Turnable train = new Turnable();
        final java.lang.reflect.Method remove = Reflect.method(AutonomyEditorPanel.class, "removeLocomotive",
            TileKey.class);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            Components.onTheEventThread(() -> remove.invoke(panel, train.square));

            Components.settle();

            assertNull(followed(train.name), "precondition: the reversal of the lifted " + train.name + " was written");

            placeFromTheMenu(panel, train.square, train.name);

            Components.settle();

            final String placed = standingAndFacing(train.name);

            saveTheEditor();

            assertEquals(standingAndFacing(train.name), train.turnedRound(), "Save after " + train.name + " was lifted,"
                + " reversed and placed back (" + placed + ") did not keep the turn (it stood on " + train.copyBefore
                + ")");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            train.putBack();
        }
    }

    /**
     * A turn the railway made on arrival and written only once the editor is open (`faceTheWayItCameIn`) is no turn
     * since the editor opened: Cancel asks nothing and keeps the train as it stands.
     *
     * MUTATION: call `turned(locomotive)` at the end of `AutonomySession.faceTheWayItCameIn`, and Cancel asks and turns
     * it back.
     */
    @Test(timeOut = 300000)
    public void testAnArrivalTurnWrittenWithTheEditorOpenIsKeptByCancel() throws Exception
    {
        final Turnable train = new Turnable();
        final java.lang.reflect.Method fold = Reflect.method(TrainControlUI.class, "captureRunningLayout");

        try
        {
            // The railway turned it on arrival: on the copy facing the other way, in by that side
            Components.onTheEventThread(() ->
            {
                org.traincontrol.automation.Layout railway = model.getAutoLayout();
                org.traincontrol.automation.Point turnedOn = session.copyFacing(train.square, train.way, railway);

                railway.moveLocomotive(null, train.copyBefore, false);

                // Onto a barred copy too, as a turn on arrival can leave it on one
                assertTrue(railway.moveLocomotive(train.name, turnedOn.getName(), false, true), "precondition: "
                    + train.name + " could not be stood on " + turnedOn.getName());

                turnedOn.setArrivedFrom(train.way.name());

                return null;
            });

            // Edit's fold, before the editor takes its picture, once what the railway's change posted has run
            Components.settle();

            Components.onTheEventThread(() -> fold.invoke(ui));

            theEditorsPanel();

            // The write still owed, retried once the editor is open
            Components.onTheEventThread(() ->
            {
                model.getAutoLayout().restoreReversalsOnArrival(java.util.Collections.singletonMap(train.name,
                    copyOf(train.name)));

                ui.reconcileFacingWhenIdle();

                return null;
            });

            Components.settle();

            assertTrue(Components.onTheEventThread(() -> model.getAutoLayout().takeReversalsOnArrival().isEmpty()),
                "precondition: the turn owed on arrival was not written with the editor open");

            final String turned = standingAndFacing(train.name);

            assertEquals(turned, train.turnedRound(), "precondition: the arrival did not turn " + train.name);
            assertNull(cancelTheEditor(), "Cancel asked after only an arrival turn was written");
            assertEquals(standingAndFacing(train.name), turned, "Cancel turned " + train.name + " back after an arrival"
                + " turn was written with the editor open");
        }
        finally
        {
            Components.onTheEventThread(() -> model.getAutoLayout().takeReversalsOnArrival());

            train.putBack();
        }
    }

    /** What was said after this, each question answered with its first button. */
    private static java.util.List<String> saidAfter(java.util.concurrent.Callable<?> doThis) throws Exception
    {
        try (support.Answering answering = support.Answering.start(message -> support.Answering.pressAt(0)))
        {
            doThis.call();

            answering.awaitAsked(1, 8000);

            Components.settle();

            return answering.asked();
        }
    }

    /**
     * The refusal every link, address change and sync meets while an editor is open; the key where the bundle lacks it,
     * so a run on older code shows what was said instead.
     */
    private static java.util.List<String> refusedForTheEditor()
    {
        String key = "loc.ui.errorEditorOpenNoLinkOrSync";

        try
        {
            return java.util.Collections.singletonList(I18n.t(key));
        }
        catch (java.util.MissingResourceException none)
        {
            return java.util.Collections.singletonList(key);
        }
    }

    /** Opens Locomotive Settings on this train's tab. */
    private static void settingsOf(String name, LocomotiveSettingsFrame.Tab tab) throws Exception
    {
        assertTrue(support.SettingsWindow.open(ui, model.getLocByName(name), tab, null), "precondition: Settings did not open");
        assertNotNull(support.SettingsWindow.awaited(), "precondition: Settings is not on screen");
    }

    /**
     * Locomotive Settings refuses a multi-unit link while the editor is open, before anything is written, and stays
     * open with the tick: the link would take the member off the railway, and Cancel would then lift the head.
     *
     * MUTATION: leave the open-editor question out of `LocomotiveActions.refuseMultiUnitEdit`, and the link is made.
     */
    @Test(timeOut = 300000)
    public void testALinkIsRefusedWhileTheEditorIsOpen() throws Exception
    {
        java.util.List<String> standing = new java.util.ArrayList<>();

        for (Map.Entry<TileKey, String> each : session.trainsWhereTheyStand().entrySet())
        {
            Locomotive loc = model.getLocByName(each.getValue());

            if (PAGE.equals(each.getKey().getPage()) && model.getAutoLayout().getLocomotiveLocation(loc) != null
                && loc.getLinkedLocomotiveNames().isEmpty() && model.isLocLinkedToOthers(loc) == null
                && !standing.contains(each.getValue())) standing.add(each.getValue());
        }

        assertTrue(standing.size() >= 2, "precondition: fewer than two standing unlinked trains on " + PAGE);

        final String head = standing.get(0);
        final String member = standing.get(1);
        final String headCopy = copyOf(head);
        final org.traincontrol.automation.Point memberWas = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(member));

        theEditorsPanel();

        try
        {
            settingsOf(head, LocomotiveSettingsFrame.Tab.MULTI_UNIT);

            support.SettingsWindow.tickMember(member);

            java.util.List<String> said = saidAfter(() -> { Components.press(support.SettingsWindow.save()); return null; });

            assertTrue(model.getLocByName(head).getLinkedLocomotiveNames().isEmpty(), "Save with the editor open linked "
                + member + " to " + head + ", saying " + said);
            assertNotNull(copyOf(member), "the refused Save took " + member + " off the railway");
            assertEquals(said, refusedForTheEditor(), "linking " + member + " to " + head + " with the editor open was not refused");
            assertNotNull(support.SettingsWindow.showing(), "the refused Save closed the window");
            assertTrue(Components.onTheEventThread(support.SettingsWindow.save()::isEnabled), "the refused Save dropped the tick");
        }
        finally
        {
            support.SettingsWindow.closeIfOpen();

            Components.onTheEventThread(() -> model.getLocByName(head).setLinkedLocomotives(new java.util.HashMap<>()));

            standBack(head, headCopy, member, memberWas, null);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /**
     * Locomotive Settings refuses a new address while the editor is open, before anything is written, and stays open
     * with what was typed: an address equal to another train's would take one off the railway.
     *
     * MUTATION: leave the open-editor question out of `LocomotiveActions.writeNameAndAddress`, and the address is written.
     */
    @Test(timeOut = 300000)
    public void testAnAddressChangeIsRefusedWhileTheEditorIsOpen() throws Exception
    {
        final String name = session.getLocomotiveNameAt(aSquareWithATrain());
        final Locomotive loc = model.getLocByName(name);
        final int address = loc.getAddress();
        final Locomotive.decoderType type = loc.getDecoderType();

        String free = null;

        for (int a = 1; free == null; a++)
        {
            final int at = a;

            if (org.traincontrol.marklin.MarklinLocomotive.validateNewAddress(type, at)
                && model.getLocomotives().stream().noneMatch(l -> l.getAddress() == at)) free = Integer.toString(at);
        }

        final String typed = free;

        theEditorsPanel();

        try
        {
            settingsOf(name, LocomotiveSettingsFrame.Tab.LOCOMOTIVE);

            support.SettingsWindow.type("settings.address", typed);

            java.util.List<String> said = saidAfter(() -> { Components.press(support.SettingsWindow.save()); return null; });

            assertEquals(loc.getAddress(), address, "Save with the editor open wrote a new address for " + name + ", saying "
                + said);
            assertEquals(said, refusedForTheEditor(), "a new address for " + name + " with the editor open was not refused");
            assertNotNull(support.SettingsWindow.showing(), "the refused Save closed the window");
            assertEquals(support.SettingsWindow.text("settings.address"), typed, "the refused Save dropped the typing");
        }
        finally
        {
            support.SettingsWindow.closeIfOpen();

            if (loc.getAddress() != address || loc.getDecoderType() != type) model.changeLocAddress(name, address, type);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }

    /**
     * A Central Station sync is refused while the editor is open, by the main window's Sync, by Locomotive Settings' Sync
     * button, and by the sync every other door runs through: a sync can take a standing train off the railway.
     *
     * MUTATION: leave the open-editor question out of `TrainControlUI.syncWithCS2`, and its own door syncs.
     */
    @Test(timeOut = 300000)
    public void testASyncIsRefusedWhileTheEditorIsOpen() throws Exception
    {
        final String name = session.getLocomotiveNameAt(aSquareWithATrain());
        final Object simulated = Reflect.get(MarklinControlStation.class, model, "simulation");
        final boolean on = model.getNetworkCommState();

        theEditorsPanel();

        try
        {
            assertEquals(saidAfter(() -> { SwingUtilities.invokeLater(() -> ui.doSync(ui)); return null; }),
                refusedForTheEditor(), "the main window's Sync with the editor open was not refused");

            // The button is lit only with a station connected
            Reflect.set(MarklinControlStation.class, model, "simulation", false);
            model.setNetworkCommState(true);

            settingsOf(name, LocomotiveSettingsFrame.Tab.LOCOMOTIVE);

            final javax.swing.JButton sync = support.SettingsWindow.part("settings.sync", javax.swing.JButton.class);

            assertTrue(Components.waitFor(() -> Components.onTheEventThread(sync::isEnabled), 8000), "precondition: Sync stayed gray");
            assertEquals(saidAfter(() -> { Components.press(sync); return null; }), refusedForTheEditor(),
                "Locomotive Settings' Sync with the editor open was not refused");

            support.SettingsWindow.closeIfOpen();

            Reflect.set(MarklinControlStation.class, model, "simulation", simulated);
            model.setNetworkCommState(on);

            assertEquals(saidAfter(() -> ui.syncWithCS2()), refusedForTheEditor(),
                "the sync the other doors run through was not refused with the editor open");
        }
        finally
        {
            support.SettingsWindow.closeIfOpen();

            Reflect.set(MarklinControlStation.class, model, "simulation", simulated);
            model.setNetworkCommState(on);

            support.FrozenEditor.putBack(ui, session, asOpened);
        }
    }
}
