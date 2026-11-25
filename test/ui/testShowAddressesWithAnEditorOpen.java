package ui;

import java.awt.Frame;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.LayoutPopupWindow;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.layout.AddressLabel;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.Mouse;
import support.Reflect;
import support.Settle;
import support.WindowField;

/**
 * Show Addresses (D200) with an editor open: the main window's redraws leave the page the editor edits to the editor's
 * own Addresses box, while the menu still reaches every page and its windows, and the page takes the menu's setting
 * again once the editor closes.  On a copy of the frozen railway, the menu's Show Addresses off between claims.
 */
public class testShowAddressesWithAnEditorOpen
{
    private static final String PAGE = "1 - Main";
    private static final String LENGTHS_PREF = "autonomyEditorLengths";
    private static final String OTHER_PAGE = "2 - Bottom";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static boolean lastEditorWasAutonomy;
    private static boolean lengthsWereShown;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;
        lastEditorWasAutonomy = TrainControlUI.getPrefs().getBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, false);
        lengthsWereShown = TrainControlUI.getPrefs().getBoolean(LENGTHS_PREF, false);
        Components.settle();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, lastEditorWasAutonomy);
            // Show Lengths too, which a claim ticks: the next class in the run reads it
            TrainControlUI.getPrefs().putBoolean(LENGTHS_PREF, lengthsWereShown);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @BeforeMethod
    public void showTheMainPage() throws Exception
    {
        Components.select(pages(), PAGE);
        Settle.theDiagram(ui);
        assertFalse(Components.onTheEventThread(menu()::isSelected), "precondition: the menu's Show Addresses is on");
    }

    /** An editor left open is canceled, page windows closed, and the menu's Show Addresses put back off. */
    @AfterMethod(alwaysRun = true)
    public void putItBack() throws Exception
    {
        AskingWindows.cancelTheEditor(editorOpen());

        Components.waitFor(() -> editButton().isEnabled(), 30000);
        Components.onTheEventThread(() ->
        {
            for (Frame frame : Frame.getFrames()) if (frame instanceof LayoutPopupWindow) frame.dispose();

            return null;
        });

        if (Components.onTheEventThread(menu()::isSelected)) Components.press(menu());

        Settle.theDiagram(ui);
        Components.settle();
    }

    /**
     * The track editor's box ticked with the menu off, another page picked in the main window, then a square turned:
     * the editor still draws the addresses, and the next click on the box takes them off and unticks it.
     *
     * MUTATION: in `TrainControlUI.ensureShowLayoutPreference`, drop `page != editing`, and the turn draws no
     * addresses.
     */
    @Test(timeOut = 200000)
    public void testAPagePickedInTheMainWindowLeavesTheEditorsBoxAlone() throws Exception
    {
        final LayoutEditor editor = theEditorTheEditButtonOpens(false);
        final JCheckBox box = (JCheckBox) Reflect.get(LayoutEditor.class, editor, "showAddressCheckbox");

        assertFalse(Components.onTheEventThread(box::isSelected), "precondition: the editor's box is ticked already");
        Components.press(box);
        Components.settle();

        final int shown = addresses(editor);

        assertTrue(Components.onTheEventThread(box::isSelected) && shown > 0,
            "precondition: ticking the box showed no addresses (" + shown + ")");

        pickTheOtherPage();

        // Any edit draws the editor's diagram again
        final LayoutLabel straight = Components.find(editor, LayoutLabel.class, l -> l.getComponent() != null
            && l.getComponent().getType() == LayoutDiagramComponent.componentType.STRAIGHT);

        Mouse.enter(straight);
        SwingUtilities.invokeLater(() -> editor.dispatchEvent(new KeyEvent(editor, KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_R, KeyEvent.CHAR_UNDEFINED)));
        Components.settle();

        assertTrue(Components.onTheEventThread(box::isSelected), "the page pick unticked the editor's box");
        assertEquals(addresses(editor), shown, "the editor's Addresses box is still ticked but its diagram lost its"
            + " addresses after a page was picked in the main window and a square turned");

        Components.press(box);
        Components.settle();

        assertFalse(Components.onTheEventThread(box::isSelected), "a click on the ticked box left it ticked");
        assertEquals(addresses(editor), 0, "a click that unticked the box left the addresses on the diagram");
    }

    /**
     * The menu's Show Addresses ticked while the track editor is open and the main window shows another page still
     * reaches the page window of the page being edited.
     *
     * MUTATION: in `TrainControlUI.ensureShowLayoutPreference`, spare the editor's page whatever `spareTheEditorsPage`
     * says, and the window shows no addresses.
     */
    @Test(timeOut = 200000)
    public void testTheMenuReachesTheWindowOfThePageBeingEdited() throws Exception
    {
        final LayoutPopupWindow window = AskingWindows.openThePage(ui);

        Components.settle();
        assertEquals(addresses(window), 0, "precondition: the page window shows addresses with the menu off");

        theEditorTheEditButtonOpens(false);
        pickTheOtherPage();
        assertTrue(Components.onTheEventThread(menu()::isEnabled), "precondition: the menu item is grayed out with an"
            + " editor open");
        Components.press(menu());
        Settle.theDiagram(ui);
        Components.settle();
        Components.drain(10);

        assertTrue(addresses(window) > 0, "the menu's Show Addresses was ticked while the track editor was open, and"
            + " the page window of the page being edited shows no addresses");
    }

    /**
     * The autonomy editor with its lengths shown, which turns its page's addresses off, and the menu's Show Addresses
     * on: a page picked in the main window leaves the editor's page without addresses, and once the editor closes the
     * page and its windows, old and new, show them again.
     *
     * MUTATION: leave out `ensureShowLayoutPreference(true)` in `TrainControlUI.repaintLayout`, and the page keeps its
     * addresses off after the editor closes.
     */
    @Test(timeOut = 200000)
    public void testThePageTakesTheMenusAddressesAgainAfterTheAutonomyEditor() throws Exception
    {
        Components.press(menu());
        Settle.theDiagram(ui);
        Components.settle();

        final LayoutPopupWindow window = AskingWindows.openThePage(ui);

        Components.settle();

        final int shown = addresses(window);

        assertTrue(shown > 0, "precondition: the page window shows no addresses with the menu on");

        final LayoutEditor editor = theEditorTheEditButtonOpens(true);

        if (!Components.onTheEventThread(() -> editor.getAutonomyPanel().getShowLengths().isSelected()))
        {
            Components.press(editor.getAutonomyPanel().getShowLengths());
            Components.settle();
        }

        assertFalse(model.getLayout(PAGE).getShowAddress(), "precondition: showing lengths left the page's addresses"
            + " on");

        pickTheOtherPage();
        assertFalse(model.getLayout(PAGE).getShowAddress(), "a page picked in the main window drew the autonomy"
            + " editor's page with addresses over its lengths");

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            Components.press(Components.withText(editor, I18n.t("ui.cancel")));
            assertTrue(Components.waitFor(() -> !editor.isDisplayable(), 10000), "Cancel did not close the editor");
        }

        Settle.theDiagram(ui);
        Components.settle();
        Components.drain(10);

        assertTrue(model.getLayout(PAGE).getShowAddress(), "the page the autonomy editor showed lengths on is left"
            + " without addresses after it closed, the menu's Show Addresses on");
        assertEquals(addresses(window), shown, "the page window open throughout lost its addresses");

        Components.select(pages(), PAGE);
        Settle.theDiagram(ui);

        final LayoutPopupWindow fresh = AskingWindows.openThePage(ui);

        Components.settle();
        assertEquals(addresses(fresh), shown, "a page window opened after the autonomy editor closed shows no"
            + " addresses");
    }

    /** The editor the Edit button opens on the page shown, the autonomy editor or the track editor as asked. */
    private static LayoutEditor theEditorTheEditButtonOpens(boolean autonomy) throws Exception
    {
        TrainControlUI.getPrefs().putBoolean(TrainControlUI.LAST_EDITOR_AUTONOMY_PREF, autonomy);
        assertTrue(Components.waitFor(() -> editButton().isEnabled(), 30000), "the Edit button never came back");
        Components.press(editButton());
        assertTrue(Components.waitFor(() -> editorOpen() != null && (autonomy
            ? editorOpen().isAutonomyMode() && editorOpen().getAutonomyPanel() != null
            : Components.find(editorOpen(), LayoutLabel.class, l -> l.getComponent() != null) != null), 30000),
            "the Edit button opened no editor");
        Components.settle();

        final LayoutEditor editor = editorOpen();

        assertEquals(editor.isAutonomyMode(), autonomy, "the Edit button opened the other editor");

        return editor;
    }

    /**
     * A link has no accessory address: with Show Addresses on, a page window draws an address on every clickable
     * square but its links, which showed their target page's name.
     *
     * MUTATION: in `LayoutGrid`, drop `!c.isLink()` from the address labels' condition, and the links get one.
     */
    @Test(timeOut = 200000)
    public void testALinkShowsNoAddress() throws Exception
    {
        final LayoutPopupWindow window = AskingWindows.openThePage(ui);

        Components.settle();
        Components.press(menu());
        Settle.theDiagram(ui);
        Components.settle();
        Components.drain(10);

        final int links = Components.onTheEventThread(() -> Components.all(window, LayoutLabel.class, each ->
            each.getComponent() != null && each.getComponent().isLink() && each.getComponent().isClickable()).size());
        final int others = Components.onTheEventThread(() -> Components.all(window, LayoutLabel.class, each ->
            each.getComponent() != null && !each.getComponent().isLink() && !each.getComponent().isText()
            && each.getComponent().isClickable()).size());

        assertTrue(links > 0 && others > 0, "precondition: the page has " + links + " links and " + others
            + " other squares to address");
        assertEquals(addresses(window), others, "the page window's addresses are not one for each square but a link");
    }

    private static void pickTheOtherPage() throws Exception
    {
        assertTrue(Components.onTheEventThread(pages()::isEnabled), "precondition: the page list is grayed out with an"
            + " editor open");
        Components.select(pages(), OTHER_PAGE);
        Settle.theDiagram(ui);
        Components.settle();
    }

    private static int addresses(java.awt.Window window)
    {
        return Components.all(window, AddressLabel.class, each -> each.isShowing()).size();
    }

    private static JComboBox<?> pages()
    {
        return (JComboBox<?>) WindowField.of(ui, "LayoutList");
    }

    private static AbstractButton menu()
    {
        return (AbstractButton) WindowField.of(ui, "menuItemShowLayoutAddresses");
    }

    private static AbstractButton editButton()
    {
        return Components.withText(ui, I18n.t("ui.main.editLayout"));
    }

    private static LayoutEditor editorOpen()
    {
        return Components.onTheEventThread(() ->
        {
            for (Frame frame : Frame.getFrames())
            {
                if (frame instanceof LayoutEditor && frame.isDisplayable()) return (LayoutEditor) frame;
            }

            return null;
        });
    }
}
