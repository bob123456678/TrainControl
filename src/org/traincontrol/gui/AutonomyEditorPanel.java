package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.util.I18n;

/**
 * Setting autonomy up by clicking on the track it runs over.
 *
 * Hand written rather than built in the designer, and mounted into a container the editor already has,
 * so no form file changes and no new window.  It is the first hand-written panel in this project;
 * LayoutGrid is the style it follows.
 *
 * The panel holds the tools and the feedback.  The clicking happens on the diagram itself - a tile is
 * handed here by the editor, and what happens to it depends on which tool is selected.  That is the
 * whole interaction: there is no separate list of things to configure, because the thing being
 * configured is the track that is already on the screen.
 *
 * @author Adam
 */
public class AutonomyEditorPanel extends JPanel
{
    /**
     * What a click on a tile does.
     */
    public static enum Tool
    {
        /**
         * Clicking does nothing; everything is set from the right-click menu.  The ordinary state.
         */
        NONE,

        /**
         * Ask why a particular TRAIN is not going anywhere, and see where it can go.
         *
         * The other test answers a question about the track: could anything get from here to there.
         * This one answers the question users actually ask, which is about a locomotive and about
         * now - every station it might be sent to, and for each one the reason it was refused.
         */
        WHY,

        /**
         * Ask whether a train could get from one sensor to another, and see the route it would take.
         *
         * The only thing here that genuinely needs a mode, because it takes two clicks to say one
         * thing.  Everything else names one tile and belongs on that tile's menu.
         */
        TEST,

        /**
         * Close a run of track in one direction, from one square to another.
         *
         * The second thing that takes two clicks, and it used to be offered from a tile's right-click
         * menu - which is where the comment above says such a thing does not belong.  Same shape as
         * TEST, and now in the same place.
         *
         * Unlike TEST this one WRITES, so it asks before it does: two squares name a run, and the run
         * has two directions.  Which one is being closed is the whole decision, and it is not
         * recoverable by looking at the diagram afterwards without reading every arrow on it.
         */
        ONE_WAY
    }

    private final AutonomySession session;
    private final Runnable onChanged;

    // Which page the editor is showing, so findings can be about the page in front of the user
    private final String page;

    // Called to scroll to and flash a tile, when a finding is clicked
    private java.util.function.Consumer<TileKey> onReveal;

    /**
     * What to do about a finding on a page this window is not showing: open an editor that is.
     */
    private java.util.function.Consumer<TileKey> onJumpToPage;

    /**
     * @param action given the square of a finding on another page
     */
    public void setOnJumpToPage(java.util.function.Consumer<TileKey> action)
    {
        this.onJumpToPage = action;
    }

    // Which tile each findings row is about; null for a heading or a finding with no tile
    private final List<TileKey> findingTiles = new java.util.ArrayList<>();

    // What each row is, so it can be coloured the same way the Auto tab colours its own list
    private final List<AutonomyChecks.Severity> findingSeverity = new java.util.ArrayList<>();

    private Tool tool = Tool.NONE;

    /**
     * How wide the panel is allowed to get.  The messages here are sentences rather than labels, and a
     * plain JLabel asks for however wide its text is - which stretched the editor's palette column
     * across the window the first time a long one appeared.
     */
    /**
     * How wide the column is.
     *
     * Narrower than it was, because nothing in it needs the room any more: the findings moved to the
     * foot of the window, the direction toggles moved into the window's own visibility box, and the
     * standing instruction that was three lines wide has gone.  What is left is two buttons and a hint.
     *
     * 150 since OB-019, matching LayoutEditor.SIDEBAR_WIDTH - the other fixed strip in this window, and
     * the nearest thing there is to a right answer. The palette this column replaces has no fixed width
     * at all: it is three columns of tile icons, so it is as wide as the tile size makes it, and
     * "match the track diagram editor" is therefore a number that changes with the zoom.
     */
    private static final int WIDTH = 150;

    // The EDITOR window's own conventions, which are not quite the main window's: its headings are
    // Semibold 13 in rgb(0,0,155) (jLabel1 "New Components", jLabel2 "Toggle Visibility") and its
    // buttons are bold 11 (saveButton, cancelButton), a size down from the main window's 12.
    static final java.awt.Font FONT_HEADING =
        new java.awt.Font("Segoe UI Semibold", java.awt.Font.PLAIN, 13);

    static final java.awt.Font FONT_BUTTON = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11);

    static final java.awt.Font FONT_CONTROL = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14);

    static final java.awt.Color HEADING_COLOUR = new java.awt.Color(0, 0, 155);

    /**
     * The findings list and the sentences beside it, one size down: they are dense reading rather than
     * labels, and at 14 a real layout's list does not fit the column.
     */
    static final java.awt.Font FONT_HINT = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12);

    private static <T extends javax.swing.JComponent> T control(T component)
    {
        component.setFont(FONT_CONTROL);
        return component;
    }

    private static <T extends javax.swing.AbstractButton> T button(T component)
    {
        component.setFont(FONT_BUTTON);
        component.setFocusable(false);
        return component;
    }

    private final JLabel banner = new JLabel();
    private final JLabel hint = new JLabel();
    private final DefaultListModel<String> findingsModel = new DefaultListModel<>();
    private final JList<String> findings = new JList<>(findingsModel);

    /**
     * How much of the direction information is drawn: all of it, only what is shut, or none.
     *
     * One control rather than two checkboxes, because the three answers are exclusive and as two boxes
     * one of the four combinations - "no directions, but do show the open ones" - meant nothing.
     */
    private final javax.swing.JComboBox<String> directions = new javax.swing.JComboBox<>(new String[]
    {
        I18n.t("autosetup.ui.directionsAll"),
        I18n.t("autosetup.ui.directionsRestrictions"),
        I18n.t("autosetup.ui.directionsNone"),
        I18n.t("autosetup.ui.directionsArrivals")
    });

    /**
     * The view that shows nothing but where trains may pull in.
     *
     * Its own entry rather than a fourth checkbox, because it answers a different question from the
     * other three and wants the diagram to itself: the direction arrows are most of the ink on a busy
     * page, and the arrival marks are small and sit at the tile edges where the arrows already are.
     */
    private static final int VIEW_ARRIVALS = 3;

    /**
     * What the visibility controls were last set to, remembered across openings of this window.
     *
     * The window is built fresh every time, so without this every visit started from the defaults and
     * anybody who works with one setting had to set it again on each visit.
     *
     * In Preferences rather than in the setup: which arrows somebody likes to look at is a fact about
     * them, not about the railway, and it should not travel to another machine with an exported
     * configuration.
     */
    private static final java.util.prefs.Preferences VIEW_PREFS =
        java.util.prefs.Preferences.userNodeForPackage(AutonomyEditorPanel.class);

    private static final String PREF_DIRECTIONS = "autonomyEditorDirections";
    private static final String PREF_LENGTHS = "autonomyEditorLengths";

    /**
     * Restrictions only, by default.
     *
     * Open track is most of a layout and its arrows say what the reader can already assume, so a
     * setup opened for the first time shows the decisions somebody has made rather than a field of
     * arrows they have to read past to find them.
     */
    private static final int DIRECTIONS_DEFAULT = 1;
    private final JCheckBox showLengths = new JCheckBox(I18n.t("autosetup.ui.btnShowLengths"), false);


    // Built in the constructor, mounted by the window across the bottom of the diagram
    private JScrollPane findingsPanel;

    // Offered only while something is still unnamed, which is the only time it does anything
    private JButton nameAll;
    private javax.swing.JCheckBox excludePage;

    // Portal pairing takes two clicks, and the first is remembered here
    private TileKey pendingPortal;

    // Bulk selection, so a one-way run is set in one gesture rather than forty
    private final Set<TileKey> selection = new LinkedHashSet<>();

    // The path test also takes two clicks; the first end and the last found route live here
    /**
     * Run after this window has written to a track diagram page, to rebuild the grid it is drawn on.
     */
    private Runnable onDiagramChanged;

    private TileKey testFrom;

    /** The one-way run tool, since OB-006 moved it off the right-click menu */
    private JToggleButton oneWayButton;

    private javax.swing.JToggleButton whyButton;

    /**
     * How the panel reaches the running layout, for the "why" test.
     *
     * A supplier rather than the Layout itself, because the layout is replaced wholesale when a
     * configuration is loaded and a held reference would go stale without saying so.
     */
    private java.util.function.Supplier<org.traincontrol.automation.Layout> layoutSource;

    /**
     * Where the "why" test looks for the running layout.
     */
    public void setLayoutSource(java.util.function.Supplier<org.traincontrol.automation.Layout> source)
    {
        this.layoutSource = source;
    }

    /**
     * Held so that a gesture can be called off from somewhere other than the button itself - a tool
     * left pressed while nothing is waiting for a click is a control lying about what it is doing.
     */
    private JToggleButton testButton;
    // Which squares a tested path runs through, and which way.  Cleared when the tool is switched off
    // or a new test is started.
    private final Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>>
        traces = new java.util.LinkedHashMap<>();

    // A one-way run started from the right-click menu, waiting for its far end
    private TileKey oneWayFrom;

    // The station whose protecting signal is being picked by clicking one, waiting for that click
    private TileKey signalFor;

    /** Whether the "which signals protect this station" window is on screen - see isFocusedOnSignals */
    private boolean signalWindowOpen;

    // The signals drawn outlined, so that "protected by signal 12" can be pointed at rather than read
    private final java.util.Set<TileKey> highlightedSignals = new java.util.LinkedHashSet<>();

    // Where the locomotive roster comes from.  Supplied rather than read here, because the session is
    // headless and knows nothing about the control station.
    private java.util.function.Supplier<List<String>> locomotiveNames;

    // One arrow per run of track between sensors.  Recomputed on refresh rather than per tile, because
    // it is derived from the whole reduction and the editor asks about every square in turn.
    private Map<TileKey, org.traincontrol.automationui.TileAnnotation.Mark> flowMarks =
        new java.util.LinkedHashMap<>();

    // Which tile speaks for each run of plain track.  A run has one direction, so only its first tile
    // is set; the rest follow and are drawn greyed so nobody tries to set them separately.
    private Map<TileKey, TileKey> runLeaders = new java.util.LinkedHashMap<>();

    /**
     * @param session the setup being edited
     * @param page the diagram page this editor window is showing
     * @param onChanged run after every edit, so the diagram can redraw itself
     */
    public AutonomyEditorPanel(AutonomySession session, String page, Runnable onChanged)
    {
        this.session = session;
        this.page = page;
        this.onChanged = onChanged;

        setLayout(new BorderLayout(4, 4));
        // No inset either side: the window's own heading and its Save and Cancel buttons sit outside
        // this panel and run edge to edge, so any padding here puts the column out of step with both.
        setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        add(buildTools(), BorderLayout.NORTH);

        // The findings are built here but mounted by the WINDOW, across the bottom and the full width.
        // In this column they were a narrow box with sentences wrapped to four words a line, beside a
        // diagram they are describing - and the diagram is where the reader has to look to act on them.
        findingsPanel = buildFindings();

        // Pinned, so a long sentence in the hint or a finding cannot widen the column it lives in.
        setPreferredSize(new Dimension(WIDTH, 640));
        setMinimumSize(new Dimension(WIDTH, 240));
        setMaximumSize(new Dimension(WIDTH, Integer.MAX_VALUE));

        // Nothing in this column is worked by keyboard, and a control that takes focus swallows the
        // key presses the window around it uses.  button() already does this one control at a time;
        // the sweep covers the lists and anything added later that forgets.
        AutonomyViewerPanel.unfocusable(this);

        refresh();
    }

    /**
     * Wraps a sentence to the panel's width instead of demanding a column as wide as the sentence.
     */
    private void say(JLabel label, String text)
    {
        // Straight to the banner across the top of the window, which has the width a sentence needs.
        // The label stays as the fallback for a panel mounted without one, and is wrapped so that it
        // cannot drag its column out of shape the way it used to.
        if (messageBanner != null && label == hint)
        {
            messageBanner.show(text);
            return;
        }

        label.setText("<html><body style='width:" + (WIDTH - 30) + "px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
    }

    /**
     * Says something that has to stay until the user acts on it - a prompt for a second click, which a
     * message that faded after six seconds would leave them stranded halfway through.
     */
    private void waitFor(String text)
    {
        if (messageBanner != null) messageBanner.showUntilChanged(text);
        else say(hint, text);
    }

    /**
     * @param banner the strip across the top of the editor, where messages go
     */
    public void setBanner(AutonomyBanner messageBanner)
    {
        this.messageBanner = messageBanner;
    }

    // The strip across the top of the window.  Named apart from the status banner in the tools column,
    // which is a different thing: that one shows what the setup IS, this one what just happened.
    private AutonomyBanner messageBanner;

    /**
     * The same, for a message that carries its own mark-up.  Only for text this class builds itself -
     * never for anything a user typed, which is what say() escapes.
     */
    private void sayRich(JLabel label, String html)
    {
        if (messageBanner != null && label == hint)
        {
            messageBanner.show("<html>" + html + "</html>");
            return;
        }

        label.setText("<html><body style='width:" + (WIDTH - 30) + "px'>" + html + "</body></html>");
    }

    /**
     * The tools column.
     *
     * A vertical BoxLayout rather than a GridLayout, because GridLayout gives EVERY row the height of
     * the tallest one - so the moment the hint wrapped to three lines, every checkbox and button grew
     * to three lines too and the column came apart.  Here each row takes the height it needs.
     */
    private JPanel buildTools()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        // No heading of its own: the editor already prints one above this column, and setAutonomyMode
        // retitles it, so a second said the same thing twice.
        //
        // One tool.  Connections, points, links and lengths were all buttons that only changed what a
        // click MEANT, and every one of them acted on a single named tile - which is what a right-click
        // menu is for.  They are all on the tile's own menu now, where the thing being configured is
        // the thing under the pointer.
        // The two actions together, because they are the two things here that are NOT done by clicking
        // the diagram.  Stacked rather than side by side: this column is narrow, and two buttons in a
        // row set its width from their combined length rather than from anything that has to fit.
        //
        // No sentence above them explaining that the work happens on the diagram, either.  It was true
        // and it was three lines wide, which made the column wider than everything else in it needed.
        // The hint line below already speaks, and it speaks about whatever was last clicked.
        testButton = toolButton(Tool.TEST, I18n.t("autosetup.ui.toolTest"));

        whyButton = toolButton(Tool.WHY, I18n.t("autosetup.ui.toolWhy"));
        whyButton.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipWhy")));

        oneWayButton = toolButton(Tool.ONE_WAY, I18n.t("autosetup.ui.toolOneWay"));
        oneWayButton.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipOneWay")));

        nameAll = new JButton(I18n.t("autosetup.ui.btnNameEverything"));
        nameAll.addActionListener(e -> nameEverything());
        button(nameAll);

        // Leaving the page out, from the page itself.
        //
        // The setting lives in a submenu of the menu bar, which is the right home for "which pages does
        // autonomy use" as a whole - and the wrong place to reach for while looking at a page full of
        // findings about track nobody automates.  This is the moment somebody decides a page is not
        // autonomy's business, so the decision belongs within reach of it.
        // A checkbox rather than a button, because it describes a state rather than performing an act.
        // As a button it excluded the page, saved, and closed the window in one press - three things,
        // one of them irreversible-looking, with no way to see what the page would be like without
        // committing to it.  Ticked, the page greys out at once and the change waits for Save like
        // every other decision in this window.
        excludePage = new javax.swing.JCheckBox(I18n.t("autosetup.ui.btnExcludeThisPage"));
        excludePage.setToolTipText(wrapped(I18n.t("autosetup.ui.hintExcludeThisPage")));
        excludePage.setFocusable(false);
        excludePage.setFont(FONT_CONTROL);
        excludePage.addActionListener(e -> setPageExcluded(excludePage.isSelected()));

        // All three the width of the column, like the window's own Save and Cancel below them
        fillWidth(testButton, nameAll);
        fillWidth(whyButton, nameAll);
        fillWidth(oneWayButton, nameAll);

        panel.add(row(testButton));
        panel.add(row(whyButton));

        // MT-098: this was built, given a tooltip, wired into the Tool enum and into the disarm path,
        // and never added to anything. Adam: "I don't see such a button." Nothing failed and nothing
        // warned - an unmounted Swing component is simply a live object with no parent.
        panel.add(row(oneWayButton));
        panel.add(row(nameAll));
        panel.add(row(excludePage));

        // The toggles change what is drawn, not what is decided, so all they do is redraw.  They live
        // in the window's own Toggle visibility box now, beside Addresses, which is where somebody
        // looking for "stop showing me that" already goes.
        //
        // "Also show track that runs both ways" is hidden pending a decision on what replaces it: it
        // answers which arrows are hidden, when the question nobody can answer from this diagram is
        // where the track actually breaks.  See the plan.
        // Restored FIRST, before anything is listening.  Setting a combo box fires its listeners, and
        // the one below redraws the whole panel - which during construction means redrawing a panel
        // that is still being built.
        directions.setSelectedIndex(
            Math.max(0, Math.min(VIEW_ARRIVALS, VIEW_PREFS.getInt(PREF_DIRECTIONS, DIRECTIONS_DEFAULT))));

        showLengths.setSelected(VIEW_PREFS.getBoolean(PREF_LENGTHS, false));

        // Not focusable, like every other control in this window (OB-019).
        //
        // It went through control(), which sets the font and nothing else, while excludePage beside it
        // sets this explicitly. A focusable control in here takes the focus off the FRAME, and the
        // frame is what the editor's keyboard shortcuts are bound to - so ticking this box quietly
        // turned Ctrl+Z and Delete off until something else was clicked.
        showLengths.setFocusable(false);

        directions.addActionListener(e ->
        {
            VIEW_PREFS.putInt(PREF_DIRECTIONS, directions.getSelectedIndex());
            refresh();
        });

        showLengths.addActionListener(e ->
        {
            VIEW_PREFS.putBoolean(PREF_LENGTHS, showLengths.isSelected());
            refresh();
        });

        hint.setFont(FONT_HINT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(hint);

        // The count is mounted by the WINDOW, under the findings list.  Up here it was a headline
        // above a column that did not contain the things it was counting; under the list it is a
        // total, which is what it always was.
        banner.setOpaque(true);
        banner.setFont(FONT_HEADING);
        banner.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

        return panel;
    }

    /**
     * @return the "so many things to fix" summary, for the window to put under the findings
     */
    public JLabel getBanner()
    {
        return banner;
    }

    /**
     * One control on its own line, flush left and no taller than it needs to be.
     */
    /**
     * Stretches buttons to the width of the column, as the window's own Save and Cancel are.
     *
     * Matching them to the WIDEST of themselves left a ragged margin down the right of the column that
     * belonged to nothing; matching them to the column makes the two of them read as the panel's own
     * controls rather than as two labels that happen to be the same length.
     */
    private void fillWidth(javax.swing.AbstractButton... buttons)
    {
        for (javax.swing.AbstractButton button : buttons)
        {
            button.setPreferredSize(new Dimension(WIDTH, button.getPreferredSize().height));
        }
    }

    private JPanel row(java.awt.Component component)
    {
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
        holder.setOpaque(false);
        holder.setAlignmentX(LEFT_ALIGNMENT);
        holder.setMaximumSize(new Dimension(WIDTH, 30));
        holder.add(component);

        return holder;
    }

    /**
     * Drops any multi-click gesture that is part way through, and un-presses the tool that started it.
     *
     * Silent: it runs on the way into the right-click menu, where a message about what was abandoned
     * would be replaced by the menu's own work a moment later anyway.
     */
    private void cancelPendingGesture()
    {
        boolean pending = tool != Tool.NONE || testFrom != null || oneWayFrom != null
            || pendingPortal != null || signalFor != null;

        if (!pending) return;

        tool = Tool.NONE;
        testFrom = null;
        oneWayFrom = null;
        pendingPortal = null;
        signalFor = null;
        highlightedSignals.clear();
        traces.clear();

        if (testButton != null) testButton.setSelected(false);
        if (whyButton != null) whyButton.setSelected(false);
        if (oneWayButton != null) oneWayButton.setSelected(false);

        say(hint, I18n.t("autosetup.ui.hintClickToCycle"));

        refresh();
    }

    /**
     * The one tool.  Deliberately NOT in a ButtonGroup: with a single toggle, a group would make it
     * impossible to switch back off again.
     */
    /**
     * Every tool toggle, so that arming one disarms the rest.
     *
     * A ButtonGroup would not do: it makes the selection permanent, and these have to be un-pressable
     * to get back to the ordinary state where clicking a square edits it.
     */
    private final java.util.List<JToggleButton> toolButtons = new java.util.ArrayList<>();

    private JToggleButton toolButton(final Tool which, String text)
    {
        final JToggleButton button = new JToggleButton(text, false);

        toolButtons.add(button);

        button(button);

        button.addActionListener(e ->
        {
            // Every OTHER tool button comes up.
            //
            // There was one tool button and no group, and a comment saying so deliberately.  A second
            // arrived and inherited the arrangement, so both could look pressed at once - and
            // un-pressing the stale one set the tool to NONE while the other still looked armed.  The
            // next click then fell through to cycle(), which CHANGES a square's direction: a read-only
            // inspection tool that appeared to be armed silently edited the railway.
            for (JToggleButton other : toolButtons)
            {
                if (other != button) other.setSelected(false);
            }

            tool = button.isSelected() ? which : Tool.NONE;
            pendingPortal = null;
            selection.clear();
            testFrom = null;
            traces.clear();

            // A one-way run waiting for its far end survived this, so the next click anywhere was
            // swallowed by a gesture the user had already moved on from.
            oneWayFrom = null;
            signalFor = null;

            say(hint, tool == Tool.TEST ? I18n.t("autosetup.ui.promptTestStart")
                : tool == Tool.WHY ? I18n.t("autosetup.ui.promptWhy")
                : tool == Tool.ONE_WAY ? I18n.t("autosetup.ui.promptOneWayFrom")
                : I18n.t("autosetup.ui.hintClickToCycle"));

            refresh();
        });

        return button;
    }

    private JScrollPane buildFindings()
    {
        findings.setVisibleRowCount(8);

        // The window's control size, not the hint size.  These are the sentences the reader is here to
        // read; set smaller than everything around them they looked like a footnote to the diagram
        // rather than the list of things it is waiting on.
        findings.setFont(FONT_CONTROL);

        // Same colours as the Auto tab's list: red for what must be fixed, amber for what is worth
        // checking, grey for the headings.  The two views describe the same setup.
        findings.setCellRenderer(new javax.swing.DefaultListCellRenderer()
        {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                AutonomyChecks.Severity severity =
                    index < findingSeverity.size() ? findingSeverity.get(index) : null;

                boolean heading = index < findingTiles.size()
                    && findingTiles.get(index) == null && severity == null;

                // The section headings take the window's own group-heading style - Segoe UI Semibold
                // in navy - so "Must be fixed" and "Warnings" read as headings of the same kind as
                // every other blue label in this application rather than as bold grey list rows.
                setFont(heading ? AutonomyViewerPanel.FONT_GROUP : FONT_CONTROL);

                if (!isSelected)
                {
                    setForeground(heading ? AutonomyViewerPanel.HEADING_COLOUR
                        : severity == AutonomyChecks.Severity.ERROR
                            ? AutonomyViewerPanel.ERROR_COLOUR
                        : severity == AutonomyChecks.Severity.WARNING
                            ? AutonomyViewerPanel.WARNING_COLOUR
                        : java.awt.Color.DARK_GRAY);
                }

                return this;
            }
        });

        // Clicking a finding goes to the tile it is about.  Reading "no train can leave Platform 3" is
        // only half an answer; the other half is which square that is, on a page of two hundred.
        findings.addListSelectionListener(e ->
        {
            if (e.getValueIsAdjusting()) return;

            int row = findings.getSelectedIndex();

            if (row >= 0 && row < findingTiles.size() && findingTiles.get(row) != null)
            {
                TileKey at = findingTiles.get(row);

                // A finding on another page is reached by opening that page, which this window cannot
                // do in place - it is built around one diagram.  So it hands the square to the main
                // window, which closes this editor and opens one there.
                if (!onThisPage(at))
                {
                    if (onJumpToPage != null) onJumpToPage.accept(at);

                    return;
                }

                if (onReveal != null) onReveal.accept(at);
            }
        });

        JScrollPane scroll = new JScrollPane(findings);

        // The window's own heading style on the border's title, so it reads as a heading of the same
        // kind as the ones beside it rather than as a plain caption on a box.
        javax.swing.border.TitledBorder titled =
            BorderFactory.createTitledBorder(I18n.t("autosetup.ui.colWarnings"));

        titled.setTitleFont(AutonomyViewerPanel.FONT_GROUP);
        titled.setTitleColor(new java.awt.Color(0, 0, 155));

        scroll.setBorder(titled);
        scroll.setPreferredSize(new Dimension(WIDTH - 20, 160));

        return scroll;
    }


    // --- what a click does ------------------------------------------------------------------------

    /**
     * A tile on the diagram was clicked while autonomy mode is on.
     *
     * @param tile which square
     * @param component what is drawn there
     * @param addToSelection whether the click was a shift-click, which adds to a bulk selection instead
     *        of acting immediately
     */
    /**
     * Everything that can be set on a tile, on one menu.
     *
     * Built the way LayoutEditorRightclickMenu builds its own - a bold disabled title, plain items in
     * the look-and-feel's own font, separators between groups - so that right-clicking in autonomy mode
     * looks like right-clicking anywhere else in this window.
     *
     * The point settings are items rather than a dialog, and in the order the graph window's point menu
     * used them, reusing its wording key for key.  A dialog had to lay out nine unrelated controls and
     * did it badly; a menu of checkboxes says the same things in the place the user already looks.
     *
     * @param tile which square
     * @param component what is drawn there
     * @param invoker the component to show the menu over
     * @param x where on it
     * @param y
     */
    public void tileRightClicked(TileKey tile, LayoutDiagramComponent component,
        java.awt.Component invoker, int x, int y)
    {
        javax.swing.JPopupMenu menu = buildTileMenu(tile, component);

        if (menu != null) menu.show(invoker, x, y);
    }

    /**
     * The same menu, built and handed back rather than shown.
     *
     * Split off so the main window's own right-click menu can carry it: the track diagram there is
     * the same diagram, and being sent to a different window to say "this platform is a station" is
     * the round trip the whole surface exists to remove.  The items are identical - one menu, built
     * once, so the two places can never drift into offering different things.
     *
     * @param tile which square
     * @param component what is drawn there, or null to read it off the page
     * @return the menu, or null where this square has nothing to offer
     */
    public javax.swing.JPopupMenu buildTileMenu(TileKey tile, LayoutDiagramComponent component)
    {
        if (tile == null || session == null || session.getGraph() == null) return null;

        if (component == null) component = componentAt(tile);

        // Right-clicking abandons whatever gesture was in progress.  Opening a menu is how somebody
        // says "not that, this instead", and a half-finished path test that stayed armed underneath it
        // would swallow the next ordinary click on the far side of the menu closing.
        cancelPendingGesture();

        // A text square carries no track, so everything below would be a no-op on it - but it is the
        // one thing on the diagram a station can be WRITTEN on, and being sent to a different editor to
        // do that is the round trip this surface exists to remove.
        LayoutDiagramComponent onPage = componentAt(tile);

        // The page has to be one the session knows, or "no component here" would mean "I could not
        // find the page" and every square on it would offer to become a station label.
        if (pageOf(tile) != null && (onPage == null || onPage.isText()))
        {
            return buildTextMenu(tile, onPage);
        }

        // Nothing on an ignored square is the user's to set, so it says so rather than offering a menu
        // whose every item would be a no-op.
        if (isIgnored(tile))
        {
            say(hint, I18n.t("autosetup.ui.infoTileIgnored"));
            return null;
        }

        // Right-clicking anywhere in a run opens the run's own menu, so the greyed tiles are not dead
        // - they simply hand the question to the tile that answers it.  A new local rather than
        // reassigning the parameter, which the lambdas below capture and so must stay effectively final.
        final TileKey target = leaderOf(tile);

        // Remembered so every item on this menu can flash what it changed without being told twice.
        menuTarget = target;

        rememberIfStation(target);

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        boolean isPoint = session.getReducer() != null
            && session.getReducer().getPoints().containsKey(target);

        title(menu, isPoint ? pointTitle(target) : component == null
            ? target.getX() + "," + target.getY() : component.getUserFriendlyTypeName());

        // Built where the station settings are and added beside its other half further down - see the
        // note there.  Null when the square has only one way in, which is not a question worth asking.
        javax.swing.JMenu arrivals = null;

        // Everything a STATION can be tuned with, under one heading, and added LAST (OB-013).
        //
        // Null on a square that is not a station, which is what it was before and is again: this held
        // Segment Length for a day, which meant building it for every square so that a length could
        // still be set on plain track - and the cost was an extra click for the commonest setting
        // there is. Segment Length is loose on the menu again, where it can be reached in one.
        javax.swing.JMenu advanced = null;

        // Held and added at the foot of the menu - see where it is built
        javax.swing.JMenuItem signalItem = null;

        if (isPoint)
        {
            // Locomotives first, because placing one is the commonest reason to open this menu once a
            // layout is set up - the designations below it are settled early and rarely touched again.
            //
            // PUTTING one down is only at a station: a locomotive can only be SENT to a station, so
            // standing one anywhere else records a position autonomy could never route away from.
            //
            // TAKING one off is not, because the square underneath a train can stop being a station
            // while the train stays recorded on it - switch it to pass-through, or import a setup that
            // placed one where this build draws no station.  Gated on the designation, the whole
            // locomotive group vanished with it and the only way to clear the placement was to make the
            // square a station again, take the train off, and put the designation back.  So this item
            // follows the LOCOMOTIVE, as the track diagram's own menu already does.
            final boolean isStation = session.getStore().isStation(target);
            final String standing = locomotiveAt(target);

            // Everything about the locomotive itself is left out of the DEEP menu.
            //
            // The track diagram's own right-click menu already carries Place, Facing, Remove and the
            // locomotive's settings, one level up from here - so inside "Autonomy Setup" they were the
            // same four answers a second time, under different words.  In the editor, where there is no
            // menu above this one, they are the only way to reach them and they stay.
            // Adam's order (MT-053): put one here, edit what is here, take it away - then which way
            // round it faces, then which station it belongs to. The group reads as the life of a
            // locomotive on this square, which is how somebody arrives at it.
            //
            // This one stays in the deep menu.
            //
            // The diagram's own menu places the locomotive selected on the KEYBOARD, which is the fast
            // way when that is the train being worked on and no way at all otherwise.  This asks which
            // locomotive, so it is not the same answer under different words - it is the only way to
            // put a train on a square from the diagram without selecting it first.
            if (isStation)
            {
                menu.add(item(I18n.t("autosetup.ui.menuAddToAutonomy"),
                    () -> placeLocomotive(target)));
            }

            if (!menuOnly) addLocomotiveSettings(menu, target);

            if (standing != null && !menuOnly)
            {
                menu.add(item(I18n.f("autosetup.ui.menuRemoveLocomotive", standing),
                    () ->
                    {
                        session.placeLocomotive(target, null);

                        placementChanged();
                    }));
            }

            if (isStation)
            {
                // "Move a Locomotive to This Station..." used to sit here (OB-009).
                //
                // Three items on one menu asked which locomotive: this one, that one, and the edit
                // dialog below - which does the same job and more, since it can also change what the
                // train is set up to do once it is there.  Two of the three are enough, and the one
                // that went is the one whose only advantage was a shorter list.
                //
                // The list this one offers is the whole roster now rather than only the locomotives
                // autonomy has never run, so it can still do what Move did.

                // Which way round the train is standing.
                //
                // Asked here and nowhere else, because here is the only place it can be answered: the
                // user is looking at the square, with the track drawn either side of it.  Offered only
                // where the answer could be more than one thing - a square one line reaches has a facing
                // too, but not a question - and only while a train is on it, since it is a fact about
                // the train rather than about the track.
                //
                // Nobody has to answer it.  Left alone it takes the first facing, and the moment
                // autonomy runs, where the train ends up says which way it was pointing and that is
                // written back.  This is the escape hatch for the first run, and for a train somebody
                // put on the rails backwards.
                // Built by buildFacingMenu rather than here.
                //
                // This was a second copy of it, and the copies drifted the way copies do: OB-039 - "when
                // changing the orientation of a loc from the track diagram, the direction on the label is
                // not updated" - was a missing redraw that had to be added to both, and only one of them
                // was in front of anybody. buildFacingMenu already returns null on exactly the two
                // conditions this used to test itself, so nothing here needs to know them.
                if (!menuOnly)
                {
                    javax.swing.JMenu facingMenu = buildFacingMenu(target);

                    if (facingMenu != null) menu.add(facingMenu);
                }

                // Last of the locomotive group (MT-104).
                //
                // It sat beside the signal, on the reasoning that both are answers about a station.
                // Adam's order puts it here instead, and it is the better reading: a home is a fact
                // about a LOCOMOTIVE - which one belongs here - so it belongs with the items that are
                // about the train rather than with the ones about the platform.
                if (isStation)
                {
                    String home = homeOf(target);

                    menu.add(item(home == null ? I18n.t("autosetup.ui.menuHomeNone")
                                               : I18n.f("autosetup.ui.menuHomeFor", home),
                        () -> promptHome(target)));
                }
            }

            if (standing != null || isStation) menu.addSeparator();

            menu.add(item(I18n.t("autosetup.ui.menuRename"), () -> promptName(target)));

            menu.addSeparator();

            // What trains may do at this square, as one three-way choice rather than two checkboxes
            // that overlap.  Stop, pass through, or neither - mutually exclusive by construction, so
            // there is no combination to get wrong and nothing to grey out.
            //
            // It replaces "mark as a station" and "active" together, because between them those said
            // the same three things in four states, one of which - not a station, and inactive - meant
            // exactly what another already did.
            final boolean isOpen = !Boolean.FALSE.equals(session.getPointProperty(target, "active"));

            javax.swing.JMenu stationMenu = new javax.swing.JMenu(
                I18n.f("autosetup.ui.menuStationGroup", stationSummary(target, isStation)));

            stationMenu.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipStationGroup")));

            javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();

            // Headed: four answers under one menu, and the heading says what the four are about
            title(stationMenu, I18n.t("autosetup.ui.menuStationHeading"));

            stationMenu.add(radio(group, I18n.t("autosetup.ui.menuCanStop"),
                "autolayout.ui.tooltip.Station", isOpen && isStation,
                () -> setUsage(target, true, true)));

            stationMenu.add(radio(group, I18n.t("autosetup.ui.menuCanTraverse"),
                "autosetup.ui.hintCanTraverse", isOpen && !isStation,
                () -> setUsage(target, false, true)));

            // Out of service.  What the square IS - station or not - is left alone, so switching back
            // returns it to what it was rather than to a default nobody chose.
            stationMenu.add(radio(group, I18n.t("autosetup.ui.menuNeither"),
                "autolayout.ui.tooltip.Active", !isOpen,
                () -> setUsage(target, isStation, false)));

            stationMenu.addSeparator();

            // Autonomy's own choosing, said directly instead of through a reversing station - which
            // was the only way to say it before, and which also reversed every arriving train and
            // refused any path through.  Switched off, a route the user picks still reaches this
            // station and Return Home still fills it; only full autonomy leaves it alone.
            javax.swing.JCheckBoxMenuItem auto = toggle(I18n.t("autosetup.ui.menuAutoDestination"),
                "autosetup.ui.hintAutoDestination", session.isAutoDestination(target),
                on -> session.setAutoDestination(target, on));

            // Greyed rather than hidden, so the shape of the choice stays visible: somebody looking for
            // it finds it, sees it is unavailable, and can tell why from the items above.
            auto.setEnabled(isOpen && isStation);

            stationMenu.add(auto);


            menu.add(stationMenu);

            // Switching direction, on any square, and not entangled with anything above.  A berth is
            // usually both - autonomy leaves it alone AND trains reverse in it - and that combination
            // used to be unauthorable, because it meant a terminus and a reversing flag on one Point,
            // which the model refuses in either order.
            // Three answers, because "may" and "must" are different railways.  May leaves the plain
            // copies, so a train can pass straight through and the path finder chooses; must leaves
            // only the turning ones, so every arrival turns.  On a dead end they are the same thing.
            javax.swing.JMenu turning = new javax.swing.JMenu(
                I18n.t("autosetup.ui.menuTurningGroup"));

            javax.swing.ButtonGroup turns = new javax.swing.ButtonGroup();

            boolean must = session.isMustTurnAround(target);
            boolean may = session.isTurnAround(target) && !must;

            title(turning, I18n.t("autosetup.ui.menuTurningHeading"));

            turning.add(radio(turns, I18n.t("autosetup.ui.menuTurnNever"),
                "autosetup.ui.hintTurnNever", !may && !must,
                () -> setTurning(target, false, false)));

            turning.add(radio(turns, I18n.t("autosetup.ui.menuTurnMay"),
                "autosetup.ui.hintCanReverse", may,
                () -> setTurning(target, true, false)));

            turning.add(radio(turns, I18n.t("autosetup.ui.menuTurnMust"),
                "autosetup.ui.hintTurnMust", must,
                () -> setTurning(target, true, true)));

            menu.add(turning);

            // Where trains may pull IN from - on the menu itself, beside what the square IS, rather
            // than buried under it.
            //
            // It sat inside the station group, which put a question people ask often two levels down
            // from the tile they are pointing at.  It belongs beside the usage choice, not inside it:
            // one says what the square is for and the other says which ends of it are open.
            //
            // Only where there is more than one way in.  A square with a single arrival side has no
            // choice to offer, and barring its only side would leave a station no train could ever be
            // sent to - somebody who wants that wants a pass-through, which is the choice above.
            final java.util.List<org.traincontrol.automationui.TilePorts.Side> ways =
                session.arrivalSides(target);

            // Shown GREYED where there is only one way in, rather than left out (MT-079).
            //
            // Adam, testing BottomInner: "I don't even see the Trains May Arrive menu (only depart).
            // But this is OK because it's implicit since it's not connected to anything else - it
            // would be clearer to show it as greyed out."
            //
            // Exactly right, and the reason is that an absent menu and a menu with nothing to offer
            // look identical from the outside. A reader who knows this menu exists and does not find
            // it has to work out whether the square is special or the application is broken; a greyed
            // one with a tooltip answers that without them asking.
            if (isStation && ways.size() == 1)
            {
                javax.swing.JMenu only = new javax.swing.JMenu(
                    I18n.t("autosetup.ui.menuArrivalsGroup"));

                only.setEnabled(false);

                only.setToolTipText(wrapped(I18n.f("autosetup.ui.hintOneWayIn",
                    I18n.t("autosetup.ui.side" + ways.get(0).name()))));

                arrivals = only;
            }
            else if (isStation && ways.size() > 1)
            {
                arrivals = new javax.swing.JMenu(
                    I18n.t("autosetup.ui.menuArrivalsGroup"));

                arrivals.setToolTipText(wrapped(I18n.t("autosetup.ui.hintArrivals")));

                title(arrivals, I18n.t("autosetup.ui.menuArrivalsHeading"));

                final java.util.Set<org.traincontrol.automationui.TilePorts.Side> barred =
                    session.getBarredArrivals(target);

                for (final org.traincontrol.automationui.TilePorts.Side side : ways)
                {
                    javax.swing.JCheckBoxMenuItem allow = toggle(
                        I18n.f("autosetup.ui.menuArrivalFrom",
                            I18n.t("autosetup.ui.side" + side.name())),
                        "autosetup.ui.hintArrivals", !barred.contains(side),
                        on -> setArrivalAllowed(target, side, on));

                    // The LAST way in cannot be shut here either.  Unticking them one at a time is
                    // the same mistake as barring a single-sided station, arrived at more slowly.
                    allow.setEnabled(barred.contains(side) || barred.size() < ways.size() - 1);

                    arrivals.add(allow);
                }

                // Held rather than added here.  It is one half of a pair - which ends trains may come
                // IN by, which ends they may go OUT by - and the two were a dozen items apart with the
                // station settings between them.  Added together, below.
                //
                // (Nothing else is deferred: everything between here and there is about the square
                // itself rather than about the track either side of it.)
            }

            // The signal that is thrown to red while this platform is claimed.
            //
            // Paired by hand rather than inferred: the nearest signal on the approach is not always the
            // one that protects a platform, and a wrong guess here throws a real signal on real
            // hardware.
            //
            // Last of the station settings, under the arrivals it belongs beside.  What the square IS,
            // then which way trains turn on it, then which ends they may come in by, then what is held
            // against them while one is standing there - each answer assuming the one above it.
            //
            // Labelled with the signal's ADDRESS, which is how anybody refers to a signal, and outlined
            // on the diagram while the item is being acted on: the address says which signal, the
            // outline says where.
            // Left out of the deep menu: pairing one means clicking the signal on the diagram, and
            // the diagram is what the deep menu is drawn over rather than part of.
            if (isStation && !menuOnly)
            {
                java.util.List<TileKey> paired = session.getProtectingSignals(target);

                // HELD rather than added (MT-104).  It goes at the foot of the menu, under the
                // arrive/depart pair and beside Advanced Parameters - which is where Adam put it, and
                // it reads as the last of the platform's own settings rather than as an afterthought
                // to the station designation.
                signalItem = item(
                    paired.isEmpty()
                        ? I18n.t("autosetup.ui.menuPairSignal")
                        : I18n.f(paired.size() == 1
                            ? "autosetup.ui.menuPairedSignal" : "autosetup.ui.menuPairedSignals",
                            signalAddresses(paired)),
                    () -> pairProtectingSignal(target));
            }


            // Everything a station can be TUNED with, under one heading.
            //
            // Four settings that a railway works without: how long a train may be, which order
            // stations are preferred in, how fast trains run through here, and which locomotives are
            // not allowed.  Two of them were loose on the menu above and two were already down here,
            // which is the worst of both - the menu was longer for no reason and the grouping said
            // nothing about which settings were which.
            //
            // Every label carries its current value, as the graph window's did: a menu that says
            // "Speed multiplier" and nothing else makes the user open it to find out what it is.
            advanced = new javax.swing.JMenu(
                I18n.t("autolayout.ui.menuEditAdvancedParameters"));

            // Back inside Advanced Parameters (MT-104).
            //
            // OB-013 brought it out on the reasoning that a train too long for a platform is refused
            // outright, so it decides whether a station can be used at all rather than how well. Adam
            // put it back, and his order is the one that ships: it is a number you set once and rarely
            // look at, which is what that submenu is for.
            int maxLength = number(target, "maxTrainLength", 0);

            advanced.add(item(I18n.f("autolayout.ui.menuMaxTrainLength",
                maxLength == 0 ? I18n.t("autolayout.ui.any") : String.valueOf(maxLength)),
                () -> promptNumber(target, "maxTrainLength",
                    "autolayout.ui.promptEnterMaxTrainLength", 0)));

            int priority = number(target, "priority", 0);

            advanced.add(item(I18n.f("autolayout.ui.menuStationPriority",
                priority == 0 ? I18n.t("autolayout.ui.default") : String.valueOf(priority)),
                () -> promptNumber(target, "priority",
                    "autolayout.ui.promptEnterStationPriority", 0)));

            advanced.add(item(I18n.f("autolayout.ui.menuSpeedMultiplier", percent(target)),
                () -> promptPercent(target)));

            advanced.add(item(I18n.f("autolayout.ui.menuExcludedLocomotives",
                strings(target, "excludedLocs").size()),
                () -> promptLocomotives(target, "excludedLocs", allLocomotives())));

            // FR-001: the same idea as excluded locomotives, about PLACES rather than trains - "we
            // should be able to exclude the autonomous selection of a station when another (specified)
            // point is occupied".  It sits beside them because it is the same kind of setting: a
            // restriction on what autonomy may choose, which the railway works perfectly well without.
            advanced.add(item(I18n.f("autolayout.ui.menuBlockedByPoints",
                session.getStore().getBlockingPoints(target).size()),
                () -> promptBlockingPoints(target)));

            menu.addSeparator();
        }

        // Everything about where trains may run, under one heading.  These were loose items at the
        // bottom of the menu - a branch submenu each, then All branches, then One-way run, then a
        // link's pairing - and each is a different sentence about the same subject.  Read as a list
        // they looked like unrelated leftovers after the point settings.
        javax.swing.JMenu connections = new javax.swing.JMenu(I18n.t("autosetup.ui.menuConnections"));

        // Headed, because there are four kinds of thing in here once a square has several arms
        title(connections, I18n.t("autosetup.ui.menuDepartHeading"));

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(target);

        // A link has no direction of its own, by decision: it just links, and the track either side of
        // it governs which way trains may run.  Its one route is a stub - the same side twice - so
        // "toward A" and "toward B" name the same place, and the traversal would allow both whichever
        // was chosen.  Offering the four answers here would be a setting that silently does nothing.
        //
        // Pinned by testALinkIsNotOfferedADirection, because that is the whole guard - see the note on
        // TileGraph.PORTAL_ROUTE.  One-way cross-page running is a feature that needs the jump itself
        // to carry a direction, and it is on the backlog rather than bolted onto this.
        if (session.canCarryDirection(target))
        {
            boolean many = routes.size() > 1;

            // Every arm, tickable, all visible together.  A click steps through four common answers,
            // which is right for the common case and cannot reach the rest; the per-branch submenus
            // below can reach the rest but only by working out which branch owns which arm.  These are
            // the arrows themselves, one box each, so any combination is one look and one tick.
            if (many)
            {
                final java.util.List<org.traincontrol.automationui.TilePorts.Side> arms =
                    armsOf(routes);

                int mask = armMask(target, routes, arms);

                title(connections, I18n.t("autosetup.ui.menuArmsHeading"));

                for (int i = 0; i < arms.size(); i++)
                {
                    final org.traincontrol.automationui.TilePorts.Side arm = arms.get(i);

                    connections.add(toggle(I18n.f("autosetup.ui.menuArm", String.valueOf(arm)),
                        "autosetup.ui.hintArms", (mask & (1 << i)) != 0,
                        on -> setArm(target, arm, on)));
                }

                connections.addSeparator();
            }

            for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry : routes.entrySet())
            {
                org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

                // A switch's branches each get their own submenu; a plain target puts its four answers
                // straight on the menu rather than burying them one level down.
                if (many)
                {
                    // no font set: submenus inherit the look and feel's, as the editor's own do
                    javax.swing.JMenu branch = new javax.swing.JMenu(
                        I18n.f("autosetup.ui.menuBranch",
                            String.valueOf(route.getA()), String.valueOf(route.getB())));

                    for (javax.swing.JMenuItem option : directionItems(target, entry.getKey(), route))
                    {
                        branch.add(option);
                    }

                    connections.add(branch);
                }
                else
                {
                    for (javax.swing.JMenuItem option : directionItems(target, entry.getKey(), route))
                    {
                        connections.add(option);
                    }
                }
            }

            if (many)
            {
                javax.swing.JMenu all = new javax.swing.JMenu(
                    I18n.t("autosetup.ui.menuAllBranches"));

                all.add(item(I18n.t("autosetup.ui.menuRouteBoth"),
                    () -> setAllBranches(target, Direction.BOTH)));
                all.add(item(I18n.t("autosetup.ui.menuRouteNone"),
                    () -> setAllBranches(target, Direction.NONE)));

                connections.add(all);
            }

        }

        // "Make a One-Way Run from Here" used to be here, with a separator above it (OB-006).
        //
        // It never belonged on a tile menu.  Everything else on this menu names ONE square and acts on
        // it; that named one square and then waited for a second click somewhere else, which is a mode,
        // and a mode advertised from a menu that closes the moment you pick it gives the user no way to
        // see that they are in one.  It is a button beside Test and Why now, where the other gesture
        // that takes two clicks already lives.

        // The two halves of the same question, side by side: which ends trains may arrive by, and
        // which ends they may leave by.  "Connections and Direction" described the machinery rather
        // than the question, and sat nowhere near its other half.
        if (arrivals != null) menu.add(arrivals);

        // Only when there is something under the heading.  Everything that fills this submenu sits
        // inside canCarryDirection(target), which is false for any portal - so right-clicking a link or
        // a tunnel opened "Connections and Direction" onto a greyed heading and a separator, which is
        // exactly the state hasItemsBesidesTitle was written to detect for OB-032 and was then never
        // called from anywhere.
        if (hasItemsBesidesTitle(connections)) menu.add(connections);

        // Then a divider, the signal, and the drawer of numbers (MT-104).
        //
        // Everything above is a decision about this square. What is left is the platform's protection
        // and the settings that change how well those decisions work, and both belong at the bottom.
        if (signalItem != null || advanced != null) menu.addSeparator();

        if (signalItem != null) menu.add(signalItem);

        if (advanced != null) menu.add(advanced);

        // A link's own settings, on the menu ITSELF rather than inside the departures submenu.
        //
        // They have been walking up this menu one level at a time, and this is where they stop.  A
        // link is not a piece of track with directions: it is a jump to another page, and the three
        // things anybody does to one - use it or not, pair it, unpair it - are the whole reason to
        // right-click a link at all.  Putting them under a heading about which way trains may depart
        // asked the user to read past a question that does not apply to a link before reaching the
        // ones that do.
        if (component != null && (component.isLink()
            || component.getType() == LayoutDiagramComponent.componentType.TUNNEL))
        {
            menu.addSeparator();

            // No heading of its own.
            //
            // The menu is already titled with what the square IS - "Page Link" - so a "This Link"
            // heading three items below says the same word twice and buys a divider for it.  Adam,
            // OB-054: "The 'this link' heading isn't necessary".  The separator above is enough to
            // group them, which is what the heading was really doing.
            // Autonomy can be told to leave a link alone entirely.  A diagram can carry one that
            // belongs to the drawing rather than to the railway autonomy runs, and refusing to build
            // until it is paired would be insisting on something the user has decided against.
            menu.add(toggle(I18n.t("autosetup.ui.menuUseLink"),
                "autosetup.ui.hintUseLink",
                !session.getStore().isPortalDisabled(target),
                on -> session.setPortalDisabled(target, !on)));

            menu.add(item(I18n.t("autosetup.ui.menuPairLink"), () -> pairFromList(target)));

            final TileKey partner = session.getStore().getPortalPartner(target);

            if (partner != null)
            {
                // The other end.
                //
                // A pairing is the one thing on this menu that is ABOUT somewhere else, and until now
                // the only way to see where was to open the pairing list and read the coordinates off
                // the selected row.  The two ends of a link are usually on different pages, which is
                // the entire reason links exist, so "where does this one go" was a question the
                // drawing could not answer and neither could the menu.
                menu.add(item(I18n.f("autosetup.ui.menuGoToLinkPartner", linkLabel(partner)),
                    () -> goToLink(partner)));

                menu.add(item(I18n.t("autosetup.ui.menuUnpairLink"),
                    () -> session.unpairPortal(target)));
            }

            // And a rule under the group.
            //
            // Everything above this line is about the link as a link - whether autonomy uses it, what
            // it is joined to, where that is.  Everything below is about the SQUARE, the same items any
            // other square gets.  Without the rule the two ran together and the link items read as the
            // first four of a list of nine.
            menu.addSeparator();
        }

        // Naming a link, on the menu itself rather than inside Connections.
        //
        // A link is FOUND by its name: the pairing list offers every other link on the railway by
        // name, and an unnamed one shows there as a coordinate pair nobody recognises.  So it is the
        // first thing done to a new link and the thing done most often - and until now it was the
        // one thing on this menu that had to be gone looking for.
        //
        // Not inside Connections either, which is where it was: what a link is CALLED is not a
        // connection, it is the label on the thing being connected.  Beside Set Length, which is the
        // other property of the square that has nothing to do with what joins to it.
        if (component != null && (component.isLink()
            || component.getType() == LayoutDiagramComponent.componentType.TUNNEL))
        {
            menu.add(item(I18n.t("autosetup.ui.menuSetName"), () -> promptLinkName(target)));
        }

        // On the menu itself rather than inside Advanced Parameters (OB-013, revised).
        //
        // It went in there for a day. Advanced Parameters belongs to a STATION, and a length belongs
        // to any square, so keeping it there meant building that submenu for plain track too - which
        // put the commonest setting on the menu one click further away on every square that has
        // nothing else to tune. The name is the part that was worth keeping: "Length..." did not say
        // length of what.
        menu.add(item(I18n.t("autosetup.ui.menuSetLength"), () -> applyLength(target)));


        // A station name can go on almost any square, not only on a text square.  The label is drawn
        // beside the tile wherever it sits, so there is no reason to make the user find a text square
        // first - and on a platform the sensible place for the name is the platform road itself.
        //
        // It used to be offered only where the track ran STRAIGHT THROUGH, on the reasoning that a
        // curve or a dead end has no room beside the track for a name.  Adam found both ends of that
        // being wrong on the same evening (OB-042, OB-044): a bumper at the end of a siding and the
        // curve at the top of a loop are often the only squares near a station with nothing else to
        // say.  His rule is the one in the code now - "the only fair place to disallow them are
        // clickable elements like switches and signals" - because those already do something when
        // clicked, and a caption on one puts text over a control.
        //
        // The CLICKED square, not the run leader the rest of this menu acts on: a name belongs where
        // it was put, and moving it to the head of the run would drop it somewhere else entirely.
        //
        // Editor only, both of them: they write text onto the DIAGRAM, which is a diagram edit wearing
        // an autonomy hat, and the deep menu is reached by right-clicking the diagram itself.
        final LayoutDiagramComponent here = componentAt(tile);

        if (mayCarryACaption(here) && !menuOnly)
        {
            menu.addSeparator();

            String text = here == null || here.getLabel() == null ? "" : here.getLabel();

            addCaptionItems(menu, tile, here, text, session.getCaptionTarget(tile));
        }

        tidy(menu);

        return menu;
    }

    /**
     * Takes the gaps out of a menu that was built in sections.
     *
     * This menu is assembled by a dozen independent blocks, each adding a divider and then whatever it
     * has to offer for THIS square - and a block with nothing to offer leaves the divider behind.  Two
     * in a row leave an empty band between two lines, which is what Adam saw on a page link (OB-054):
     * a heading, a divider, nothing at all, another divider.
     *
     * Rather than teach every block to look ahead - twelve places to get it right, and the next one
     * added would be the thirteenth - the shape is corrected once, here, at the end:
     *
     *   - a divider at the top or the bottom has nothing to separate
     *   - two dividers in a row have nothing between them
     *   - a heading with nothing under it is a title for an empty section
     *
     * The last of those is why this cannot simply be a loop over separators: a heading is an ITEM, so
     * "heading, divider, divider" is not two adjacent dividers until the heading is recognised and
     * removed.  Repeated until nothing changes, so removing a heading can collapse the dividers that
     * were around it.
     *
     * @param menu the menu to tidy, in place
     */
    private static void tidy(javax.swing.JPopupMenu menu)
    {
        boolean again = true;

        while (again)
        {
            again = false;

            for (int at = 0; at < menu.getComponentCount(); at++)
            {
                java.awt.Component here = menu.getComponent(at);

                boolean separator = here instanceof javax.swing.JSeparator;

                // A heading: the disabled item title() leaves behind.  Disabled and NOT a separator -
                // an ordinary item that happens to be disabled is a real offer the user cannot take
                // right now, and removing those would hide them.  Only a heading is followed by a
                // divider or by the end of the menu, which is the test below.
                boolean heading = here instanceof javax.swing.JMenuItem
                    && !here.isEnabled();

                java.awt.Component next = at + 1 < menu.getComponentCount()
                    ? menu.getComponent(at + 1) : null;

                boolean nothingFollows = next == null || next instanceof javax.swing.JSeparator;

                if (separator && (at == 0 || nothingFollows))
                {
                    menu.remove(at);
                    again = true;
                    break;
                }

                if (heading && nothingFollows)
                {
                    menu.remove(at);
                    again = true;
                    break;
                }
            }
        }
    }

    /**
     * Flashes the square the open menu belongs to, so a menu edit is as visible as a click.
     */
    private void flashMenuTarget()
    {
        if (menuTarget != null && onReveal != null) onReveal.accept(menuTarget);
    }

    // Which square the open right-click menu is acting on
    private TileKey menuTarget;

    /**
     * What the menu calls this point: its name where it has one, and what it is where it does not.
     */
    private String pointTitle(TileKey tile)
    {
        String name = session.getStore().getPointName(tile);

        return name == null || name.trim().isEmpty() ? describeTile(tile) : name.trim();
    }

    /**
     * A bold, disabled heading at the top of a menu - the editor's own idiom.
     */
    private void title(javax.swing.JMenu menu, String text)
    {
        title(menu.getPopupMenu(), text);
    }

    private void title(javax.swing.JPopupMenu menu, String text)
    {
        javax.swing.JMenuItem titleItem = new javax.swing.JMenuItem(text);
        titleItem.setEnabled(false);
        titleItem.setFont(titleItem.getFont().deriveFont(java.awt.Font.BOLD));

        menu.add(titleItem);
        menu.addSeparator();
    }

    /**
     * A menu item that runs something and then redraws.  No font is set: the editor's own menus use the
     * look and feel's, and one that did not would be the only odd menu in the window.
     */
    /**
     * A menu item that runs something, redraws, and flashes the square it changed.
     *
     * The flash matters more here than on a click: the menu covers the tile while it is open, and on a
     * run the square that changes is the head of the run rather than the one right-clicked - so
     * without it the one square that moved was the one the user could not see.
     */
    /**
     * Whether a submenu holds anything but its own heading (OB-032).
     *
     * title() adds a disabled item at the top, so a submenu with nothing in it has a count of one
     * rather than zero - and "is it empty" asked as getItemCount() == 0 is therefore always false.
     *
     * @param menu the submenu
     * @return true when there is something under the heading
     */
    private boolean hasItemsBesidesTitle(javax.swing.JMenu menu)
    {
        for (int i = 0; i < menu.getItemCount(); i++)
        {
            javax.swing.JMenuItem child = menu.getItem(i);

            // A separator comes back as null from getItem, and a separator is not content either
            if (child != null && child.isEnabled()) return true;
        }

        return false;
    }

    private javax.swing.JMenuItem item(String text, final Runnable action)
    {
        javax.swing.JMenuItem menuItem = new javax.swing.JMenuItem(text);

        menuItem.addActionListener(e ->
        {
            try
            {
                action.run();
            }
            catch (RuntimeException ex)
            {
                // The class as well as the message.  A NullPointerException has no message, so this
                // showed a dialog whose entire content was the word "null" - which tells the user
                // nothing and told me nothing either, for a week.
                String said = ex.getMessage() == null || ex.getMessage().trim().isEmpty()
                    ? ex.getClass().getSimpleName() : ex.getMessage();

                JOptionPane.showMessageDialog(owner(), I18n.f("error.generic", said));

                // And into the log, with the stack, which is the only thing that says WHERE
                if (parentWindow() != null && parentWindow().getModel() != null)
                {
                    parentWindow().getModel().log(ex);
                }
            }

            refresh();

            flashMenuTarget();
        });

        return menuItem;
    }

    private javax.swing.JCheckBoxMenuItem toggle(String text, boolean on,
        final java.util.function.Consumer<Boolean> action)
    {
        return toggle(text, null, on, action);
    }

    /**
     * A tooltip that wraps instead of running off the screen.
     *
     * Swing lays a tooltip out on one line however long it is, and these explain what a setting MEANS
     * rather than naming it - so the most useful ones were the widest, and the widest were the ones
     * that ran past the edge of the display.  The width is in pixels because that is the only unit the
     * renderer here understands.
     *
     * @param text
     * @return the same text, wrapped
     */
    static String wrapped(String text)
    {
        if (text == null || text.trim().isEmpty()) return text;

        // Already somebody's own markup: left exactly as it is
        if (text.trim().toLowerCase().startsWith("<html")) return text;

        return "<html><body style='width: 320px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            + "</body></html>";
    }

    /**
     * @param tooltipKey the graph window's own tooltip for this setting, so the explanation travels
     *        with the option rather than being lost when the dialog that carried it went
     */
    private javax.swing.JCheckBoxMenuItem toggle(String text, String tooltipKey, boolean on,
        final java.util.function.Consumer<Boolean> action)
    {
        final javax.swing.JCheckBoxMenuItem menuItem = new javax.swing.JCheckBoxMenuItem(text, on);

        if (tooltipKey != null) menuItem.setToolTipText(wrapped(I18n.t(tooltipKey)));

        menuItem.addActionListener(e ->
        {
            action.accept(menuItem.isSelected());

            // placementChanged, not refresh (TD-1).
            //
            // Every one of these writes the SETUP, and four of them are reachable from the track
            // diagram's own right-click menu, where nothing else picks the change up: whether trains
            // may arrive by a side, which way an arm runs, whether a link is switched off, and whether
            // autonomy may choose this square.  The first of those decides how a square SPLITS - how
            // many Points it becomes and what they are called - which is the very example the rebuild
            // exists for.  Refreshing alone redrew this menu over a running layout that still held the
            // old Points.
            placementChanged();

            flashMenuTarget();
        });

        return menuItem;
    }

    /**
     * The menu for a text square: which station, if any, it is showing.
     */
    private void showTextMenu(final TileKey tile, final LayoutDiagramComponent component,
        java.awt.Component invoker, int x, int y)
    {
        buildTextMenu(tile, component).show(invoker, x, y);
    }

    /**
     * The text-square menu, built and handed back.  See buildTileMenu.
     */
    private javax.swing.JPopupMenu buildTextMenu(final TileKey tile,
        final LayoutDiagramComponent component)
    {
        menuTarget = tile;

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        String label = component == null || component.getLabel() == null ? "" : component.getLabel();

        // What the square is showing: a station’s caption, the user’s own text, or nothing.  The
        // caption is no longer text on the diagram at all - it is an autonomy object drawn here - so it
        // is asked for rather than read off the label.
        TileKey captioned = session.getCaptionTarget(tile);

        title(menu, captioned != null ? describeTile(captioned)
            : label.trim().isEmpty() ? I18n.t("autosetup.ui.titleEmptyText") : label);

        // Offered on a blank square too, not only on one that already carries text: writing a station
        // name on a blank square is how a diagram with no text squares at all gets its first one.
        //
        // And offered over somebody's own text, having been refused there.  The reasoning was that a
        // square carrying text which is not a caption is part of the user's drawing - a yard name, a
        // note - and this editor writes autonomy rather than diagrams.  True, but it left a state
        // with no way out: Adam moved a station, replaced its label, moved it back and cleared the
        // label, and the square he wanted the name on now held leftover text of its own.  The item
        // was greyed, so the label could not be put back at all, from here or from anywhere.
        //
        // So it asks instead, naming the text it would replace.  A question is a way out; a disabled
        // menu item is not.
        addCaptionItems(menu, tile, component, label, captioned);

        return menu;
    }

    /**
     * "Show Station Here..." and its clear, added to whichever menu is being built.
     *
     * Shared so the two cannot drift into offering different things - the same reason buildTileMenu is
     * handed to the main window rather than copied there.
     *
     * @param menu the menu being built
     * @param tile the square
     * @param component what is drawn on it, or null for a blank square
     * @param label whatever text the square already carries
     * @param captioned the station this square is already showing, or null
     */
    private void addCaptionItems(javax.swing.JPopupMenu menu, final TileKey tile,
        final LayoutDiagramComponent component, String label, TileKey captioned)
    {
        boolean mine = label.trim().isEmpty() || captioned != null;

        // Both items NAME the station when there is one (FR-014).
        //
        // Adam: "the show station name here right click menu option in the autonomy editor should
        // clearly indicate the current station being shown, in cases where the user just sees [---] on
        // the diagram." A caption draws the station's OCCUPANT, and an empty station draws as
        // GraphLocAssign.NONE_LABEL - so a square captioning a station with no train on it says
        // nothing at all about which station it is. That is the ordinary state of most of the railway.
        //
        // Here rather than in the two menus, because both build their caption items through this
        // method and a name added to one of them would be missing from the other - which is the shape
        // of half the defects in this file's history. The deep menu also carries a title() naming the
        // station; the editor's own menu has no title, and that is the menu Adam was looking at.
        String showing = captioned == null ? null : describeTile(captioned);

        javax.swing.JMenuItem name = item(showing == null ? I18n.t("autosetup.ui.menuShowStationHere")
            : I18n.f("autosetup.ui.menuShowStationHereNamed", showing),
            () -> promptStationLabel(tile, component));

        name.setToolTipText(mine ? null : wrapped(I18n.t("autosetup.ui.tooltipTextInTheWay")));

        menu.add(name);

        if (captioned != null)
        {
            // Naming what is about to be cleared matters more here than anywhere: this item is
            // destructive, and "Clear This Square" on a square reading [---] asks the user to confirm
            // the removal of something they cannot see the identity of.
            menu.add(item(I18n.f("autosetup.ui.menuClearStationHereNamed", showing),
                () -> applyCaption(tile, null)));
        }
    }

    /**
     * Whether a station's name may be written on this square.
     *
     * OB-042 and OB-044, which are one bug reported from two squares: "the option to place a station
     * label is not shown in the curved track right click menu", and "bumpers don't allow station labels
     * to be placed via the right click menu. check other components that also don't. only fair place to
     * disallow them are clickable elements like switches and signals."
     *
     * It used to be offered only on a blank square or one already carrying text, because that is where
     * a label normally goes - a platform road usually has no room for it. But "usually" is not a rule,
     * and a bumper at the end of a siding or the curve at the top of a loop is often the only square
     * near the station with nothing else to say.
     *
     * Clickable squares are the exception, and Adam's reasoning is the right one: a switch or a signal
     * ALREADY does something when clicked, and hanging a caption on it puts text over a control.
     *
     * @param component what is drawn on the square
     * @return whether to offer the caption items
     */
    private boolean mayCarryACaption(LayoutDiagramComponent component)
    {
        if (component == null) return true;

        // Switches and signals, and NOT everything isClickable() covers.
        //
        // The first version of this asked isClickable(), which reads like Adam's sentence and is not
        // it: that method also counts feedback, uncouplers, links, lamps and routes. Feedback is the
        // platform road - the square the comment above recommends for a station name, and the one the
        // old rule explicitly allowed - so widening the rule for curves and bumpers quietly took away
        // the commonest place of all. Caught in review before he saw it.
        //
        // A switch and a signal are refused because a click on them throws real ironwork, and a
        // caption over a control is a caption in the way of it. A sensor's click toggles a feedback
        // reading, which is a different kind of thing: nothing moves, and the platform road is where
        // the name belongs.
        return !component.isSwitch() && !component.isSignal();
    }

    /**
     * The station most recently clicked or right-clicked, so a label knows what it is probably for.
     *
     * Naming a square is a two-step job: look at the station, then put its name on a square beside
     * it - a platform road usually has no room for the text, so the label goes on the blank square
     * above or below.  By the time the user gets to the second step the editor has forgotten the
     * first, and offered them an alphabetical list of every station on the railway with the one they
     * are standing next to buried somewhere in it.
     */
    private TileKey lastStationTouched;

    /**
     * Notes a square if it is a station, so the next label offered defaults to it.
     */
    private void rememberIfStation(TileKey tile)
    {
        if (tile != null && session != null && session.getStore().isStation(tile))
        {
            lastStationTouched = tile;
        }
    }

    /**
     * Asks which station this square should show.
     */
    private void promptStationLabel(TileKey tile, LayoutDiagramComponent component)
    {
        // Text of the user's own on this square is replaced only if they say so.
        //
        // The text is named in the question, because "there is something here" is not enough to
        // decide on - the whole reason somebody is doing this is that they are looking at a square
        // whose contents they have lost track of.
        String standing = component == null || component.getLabel() == null
            ? "" : component.getLabel().trim();

        if (!standing.isEmpty() && session.getCaptionTarget(tile) == null)
        {
            // showOptionDialog with TrainControl's own button text, not showConfirmDialog, whose
            // buttons come from the look-and-feel and so follow the JVM's locale rather than the
            // language the user picked.  Note the return: an INDEX, where 0 is the first option.
            int answer = JOptionPane.showOptionDialog(owner(),
                I18n.f("autosetup.ui.confirmReplaceText", standing),
                I18n.t("autosetup.ui.titleStationLabel"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]);

            if (answer != 0) return;
        }

        // A station labelling ITSELF has nothing to ask about.  Offering a list of every station on
        // the layout, with this one's own name buried in it, is a question whose answer is already
        // known - and getting it wrong would put another platform's name on this platform.
        if (session.getStore().isStation(tile))
        {
            applyCaption(tile, tile);
            return;
        }

        java.util.List<String> names = new java.util.ArrayList<>();

        if (session.getReducer() != null)
        {
            for (org.traincontrol.automationui.GraphReducer.ReducedPoint point
                : session.getReducer().getPoints().values())
            {
                if (point.isStation() && point.getName() != null) names.add(point.getName());
            }
        }

        java.util.Collections.sort(names);

        if (names.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.errorNoStationsToLabel"));
            return;
        }

        javax.swing.JComboBox<String> choice =
            new javax.swing.JComboBox<>(names.toArray(new String[0]));

        // What this square already shows, or failing that the station the user was last looking at.
        //
        // Not the first name alphabetically, which is what it fell back to: a list of every station
        // on the railway, opened on whichever one happens to sort first, is a list to be searched
        // rather than an answer to be confirmed.
        TileKey showing = session.getCaptionTarget(tile);

        if (showing == null) showing = lastStationTouched;

        // Matched by SQUARE rather than by name, which is why the default never appeared.
        //
        // The list above is built from the REDUCED points, whose names are the ones the builder
        // generates for each way into a station - and the name a square is stored under is the
        // authored one it was given.  For any station split by its arrival sides those are different
        // strings, so asking whether the list contained the stored name answered no every time, on
        // exactly the stations most worth defaulting to.  Two names for one square; the square is
        // the thing they agree on.
        String named = nameInListFor(showing, names);

        if (named != null) choice.setSelectedItem(named);

        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));
        panel.add(new JLabel(I18n.t("autosetup.ui.promptStationLabel")), java.awt.BorderLayout.NORTH);
        panel.add(choice, java.awt.BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(owner(), panel, I18n.t("autosetup.ui.titleStationLabel"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        applyCaption(tile, stationTileNamed((String) choice.getSelectedItem()));
    }

    /**
     * Shows a station’s name on a square, or stops showing it, and says so.
     *
     * Nothing is written to the layout file.  A caption belongs to the autonomy setup now, so it is
     * saved by the button that says it saves autonomy and discarded by the Cancel that says it discards
     * it - which is what the old behaviour, writing the page here and now, could not offer either way.
     *
     * @param tile the square the text goes on
     * @param station the sensor it is about, or null to clear the square
     */
    private void applyCaption(TileKey tile, TileKey station)
    {
        session.setCaption(tile, station);

        // The editor's own grid has to be REBUILT, not repainted: the caption is part of the tile art,
        // and the annotation refresh that follows every other edit does not touch it.
        if (onDiagramChanged != null) onDiagramChanged.run();

        say(hint, station == null ? I18n.t("autosetup.ui.clearedStationLabel")
            : I18n.f("autosetup.ui.setStationLabel", describeTile(station)));

        refresh();

        flashMenuTarget();
    }

    /**
     * The square of the station with this authored name, for turning a chooser’s answer back into
     * the thing a caption actually points at.
     */
    /**
     * Which entry of a name list stands for a given square.
     *
     * A station square can appear in the list under more than one name - one per way in, where its
     * arrivals have been split - and any of them names the same place, so the first is as good an
     * answer as the rest.
     *
     * @param tile the square to find, or null
     * @param names what the dropdown is offering
     * @return the entry to select, or null where the square is not in the list
     */
    private String nameInListFor(TileKey tile, java.util.List<String> names)
    {
        if (tile == null) return null;

        // The stored name first, for a station that was never split: it is the exact answer where it
        // is an answer at all.
        String authored = session.getStore().getPointName(tile);

        if (authored != null && names.contains(authored)) return authored;

        for (String name : names)
        {
            if (tile.equals(session.tileForPointName(name))) return name;
        }

        return null;
    }

    private TileKey stationTileNamed(String name)
    {
        if (name == null || session.getReducer() == null) return null;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (name.equals(session.getStore().getPointName(tile))) return tile;
        }

        return null;
    }

    /**
     * What the Station heading says about a sensor without being opened.
     */
    private String stationSummary(TileKey tile, boolean isStation)
    {
        // Yes or no, and nothing else.  The heading answers one question - may a train stop here -
        // and the radio inside answers the rest; spelling every designation out again made a line
        // long enough that the answer was the hardest part of it to find.
        //
        // A square that is out of service reads "no", because no train can stop on one.  Why is a
        // click away and does not belong in a heading.
        boolean stops = isStation
            && !Boolean.FALSE.equals(session.getPointProperty(tile, "active"));

        // The one exception, because it changes what the square is FOR rather than merely describing
        // it: a station autonomy will not choose is a berth, and that is worth a mark you can see
        // without opening anything.
        String star = stops && !session.isAutoDestination(tile) ? " *" : "";

        if (!stops) return I18n.t("autosetup.ui.stationNo") + star;

        // And which KIND of stop, where the square turns trains round.  "Yes" alone answered the
        // heading's question and left the more interesting half - does every train reverse here, or
        // may it - to be found by opening the group.  Both are worth seeing at a glance, and they are
        // the two settings people confuse.
        if (session.isMustTurnAround(tile))
        {
            return I18n.t("autosetup.ui.stationYesTerminus") + star;
        }

        if (session.isTurnAround(tile))
        {
            return I18n.t("autosetup.ui.stationYesReversing") + star;
        }

        return I18n.t("autosetup.ui.stationYes") + star;
    }

    /**
     * Writes a station's name onto the diagram, and rebuilds the grid if it went anywhere.
     *
     * Quiet when there is nowhere to put it: the square is boxed in, or somebody's own caption is
     * already there.  The check that says a station is not shown anywhere still reports it, which is a
     * better place for the news than a dialog interrupting the click that created the station.
     */
    private void placeLabelFor(TileKey tile)
    {
        String why = session.placeCaption(tile);

        // Says why nothing happened.  A silent no-op is the worst answer here: the user cannot tell a
        // refusal from a bug, and neither could I - "no label appears" was reported three times before
        // this told anybody which of four conditions had turned it down.
        if (why != null)
        {
            say(hint, I18n.f(why, describeTile(tile)));
            return;
        }

        if (onDiagramChanged != null) onDiagramChanged.run();
    }

    /**
     * The window this panel lives in, for parenting dialogs.
     *
     * Not the panel itself.  JOptionPane centres over the COMPONENT it is given, and this one is a
     * narrow strip down the side of the editor - so every prompt appeared over that strip, hard against
     * one edge of the window rather than in the middle of it.
     */
    private java.awt.Component owner()
    {
        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);

        if (window != null) return window;

        // A panel held only to build menus is in no window at all, and a dialog parented on it lands
        // wherever the platform feels like putting it - which on Windows is behind the main window,
        // where a modal prompt that has to be answered is invisible and the application looks hung.
        return dialogOwner == null ? this : dialogOwner;
    }

    /**
     * Where to parent dialogs when this panel is not in a window of its own.
     */
    private java.awt.Component dialogOwner;

    public void setDialogOwner(java.awt.Component dialogOwner)
    {
        this.dialogOwner = dialogOwner;
    }

    /**
     * Whether this panel exists only to build menus, with no diagram of its own behind it.
     *
     * Two items on the tile menu ask for a SECOND click - one-way running wants the far end of the
     * run, a protecting signal wants the signal - and the click they wait for is one the editor's own
     * grid routes here.  Borrowed by the main window's diagram, those clicks go somewhere else
     * entirely, so the prompt would sit in a banner nobody is looking at and the gesture would never
     * finish.  They open the editor at that square instead, which is where the job can be done.
     */
    private boolean menuOnly;

    /**
     * The "{loc} Is Facing..." submenu for a square, or null when there is nothing to ask.
     *
     * Handed out on its own so that the track diagram's own right-click menu can carry it, beside the
     * other things it offers about the train standing there - Place, Facing, Remove and the
     * locomotive's settings.  Inside Autonomy Setup it was one level further down than everything it
     * belongs with, and the deep menu no longer offers it.
     *
     * Null unless a locomotive is standing there AND the square has more than one facing to choose
     * between: on a square one line reaches, the facing is not a question.
     *
     * @param target the square
     * @return the submenu, or null
     */
    public javax.swing.JMenu buildFacingMenu(final TileKey target)
    {
        if (target == null) return null;

        final String standing = locomotiveAt(target);

        if (standing == null) return null;

        final java.util.List<org.traincontrol.automationui.TilePorts.Side> facings =
            session.facingChoices(target);

        if (facings.size() <= 1) return null;

        javax.swing.JMenu facingMenu = new javax.swing.JMenu(
            I18n.f("autosetup.ui.menuFacingGroup", standing));

        facingMenu.setToolTipText(wrapped(I18n.t("autosetup.ui.hintFacing")));

        javax.swing.ButtonGroup facingGroup = new javax.swing.ButtonGroup();

        org.traincontrol.automationui.TilePorts.Side recorded = session.getFacing(target);

        for (final org.traincontrol.automationui.TilePorts.Side facing : facings)
        {
            facingMenu.add(radio(facingGroup,
                I18n.t("autosetup.ui.facing" + facing.name()), "autosetup.ui.hintFacing",
                recorded == null ? facing == facings.get(0) : facing == recorded,
                // The redraw is in radio() itself now (TD-1), which is where every one of these
                // answers gets it. OB-039 fixed it here, on the one radio that had been reported, and
                // left the station and turning radios beside it still telling nobody.
                () -> session.setFacing(target, facing)));
        }

        return facingMenu;
    }

    /**
     * Goes to a link's other end.
     *
     * On this page it is a scroll and a flash; anywhere else the window has to be reopened on that
     * page, which is what the jump hook is for - the editor is built around one diagram.
     *
     * The deep menu always jumps: it is opened from the track diagram, where this panel is a menu
     * builder with no page of its own, so "is it on this page" has no answer here.
     */
    private void goToLink(TileKey partner)
    {
        if (partner == null) return;

        if (!menuOnly && onThisPage(partner))
        {
            if (onReveal != null) onReveal.accept(partner);

            return;
        }

        if (onJumpToLink != null) onJumpToLink.accept(partner);
        else if (onJumpToPage != null) onJumpToPage.accept(partner);
    }

    /**
     * What to call a link in a menu: its name, or its square when it has none.
     */
    private String linkLabel(TileKey tile)
    {
        if (tile == null) return "";

        String named = session.getStore().getLinkName(tile);

        return named == null || named.trim().isEmpty() ? tile.toString() : named;
    }

    /**
     * Leaving this page to look at a link's other end.
     *
     * Separate from onJumpToPage, which a finding uses: that one is the window taking the user
     * somewhere as part of showing them a result, while this is the user choosing to leave, and the
     * editor asks before it closes on unsaved work.
     */
    private java.util.function.Consumer<TileKey> onJumpToLink;

    public void setOnJumpToLink(java.util.function.Consumer<TileKey> action)
    {
        this.onJumpToLink = action;
    }

    public void setMenuOnly(boolean menuOnly)
    {
        this.menuOnly = menuOnly;
    }

    /**
     * @return whether this had to be handed to the editor, and so must not be started here
     */
    private boolean needsTheGrid(TileKey tile)
    {
        if (!menuOnly) return false;

        if (onJumpToPage != null) onJumpToPage.accept(tile);

        return true;
    }

    /**
     * One of the three mutually exclusive answers to "what may a train do here", as a radio item.
     *
     * Radio rather than a checkbox because the three cannot overlap: a square cannot be somewhere
     * trains stop AND somewhere they may not go.  Expressed as checkboxes that was a combination the
     * user could author and nothing could honour.
     */
    private javax.swing.JMenuItem radio(javax.swing.ButtonGroup group, String text, String tooltipKey,
        boolean on, final Runnable action)
    {
        javax.swing.JRadioButtonMenuItem menuItem = new javax.swing.JRadioButtonMenuItem(text, on);

        if (tooltipKey != null) menuItem.setToolTipText(wrapped(I18n.t(tooltipKey)));

        group.add(menuItem);

        menuItem.addActionListener(e ->
        {
            action.run();

            // placementChanged, not refresh (TD-1).
            //
            // These radios write the SETUP - whether trains may stop here, whether they may turn round
            // - and the captions and the running railway are built from the RUNNING layout. refresh()
            // redraws this panel and tells the other surface nothing, so a change made from the track
            // diagram's menu was written and never appeared.
            //
            // Exactly the shape of OB-039, which was the facing radio one submenu along. That one was
            // fixed in its own lambda, which left its two neighbours - sitting in the same helper,
            // reached by the same gesture - still calling refresh(). The redraw belongs HERE, where
            // every radio gets it, rather than in whichever lambda somebody reported.
            placementChanged();

            flashMenuTarget();
        });

        return menuItem;
    }

    /**
     * Applies the turning answer, keeping the two stored flags in step.
     *
     * Both are cleared and only what is meant is written, rather than leaving "may" set underneath
     * "must" - two flags describing one choice will disagree the first time only one of them is
     * updated, and then the square says two things.
     */
    private void setTurning(TileKey tile, boolean may, boolean must)
    {
        session.setPointFlag(tile, AutonomyBuilder.CAN_REVERSE, may && !must);
        session.setPointProperty(tile, AutonomyBuilder.MUST_REVERSE, must ? Boolean.TRUE : null);
    }

    /**
     * Applies one of the three answers.
     *
     * @param station whether trains may stop here
     * @param open whether they may come here at all
     */
    /**
     * Opens or shuts one side of a station to arriving trains.
     *
     * Stored as the set of BARRED sides, so a station nobody has restricted carries nothing at all -
     * and a side added to the diagram later arrives open, which is what somebody who never opened this
     * setting would expect.
     *
     * @param tile the station
     * @param side which way in
     * @param allowed whether trains may arrive that way
     */
    private void setArrivalAllowed(TileKey tile, org.traincontrol.automationui.TilePorts.Side side,
        boolean allowed)
    {
        java.util.Set<org.traincontrol.automationui.TilePorts.Side> barred =
            new java.util.LinkedHashSet<>(session.getBarredArrivals(tile));

        if (allowed)
        {
            barred.remove(side);
        }
        else
        {
            barred.add(side);
        }

        session.setBarredArrivals(tile, barred);
    }

    private void setUsage(TileKey tile, boolean station, boolean open)
    {
        setStation(tile, station);

        // Stored only when it is off, like every other default, so a square nobody has closed carries
        // nothing at all
        session.setPointProperty(tile, "active", open ? null : Boolean.FALSE);
    }

    private void setStation(TileKey tile, boolean on)
    {
        session.setStation(tile, on);

        // A new station gets its name on the diagram straight away.  A station nobody can see is the
        // commonest thing wrong with a finished setup - it has a warning of its own - and the moment
        // somebody says "this is a station" is the moment they know where its name should go.
        if (on) placeLabelFor(tile);

        // A sensor demoted back to a plain point keeps no designation nobody can see any more.
        //
        // Active is NOT cleared with it.  It applies to any point, station or not - the graph menu
        // offered it on all of them - so clearing it here would silently re-enable a point somebody had
        // switched off, on a gesture that says nothing about that.
        // Active is not cleared here any more: what a square is and whether it is open are the same
        // three-way choice now, and setUsage sets both together.
        if (!on) session.setPointProperty(tile, "terminus", null);
    }

    private void setAllBranches(TileKey tile, Direction direction)
    {
        session.setDirection(new LinkedHashSet<>(java.util.Arrays.asList(tile)), direction);
    }

    /**
     * Asks for a number, storing nothing when it is left at the value that means "no setting".
     */
    private void promptNumber(TileKey tile, String key, String promptKey, int unset)
    {
        Object current = session.getPointProperty(tile, key);

        // I18n.f, not t: two of these prompts name the point they are about, and I18n.t does no
        // substitution at all - so the user was asked to "Enter the priority for {0}".
        String entered = JOptionPane.showInputDialog(owner(),
            I18n.f(promptKey, describeTile(tile)),
            current instanceof Number ? String.valueOf(current) : String.valueOf(unset));

        if (entered == null) return;

        try
        {
            int value = Integer.parseInt(entered.trim());

            session.setPointProperty(tile, key, value == unset ? null : value);
        }
        catch (NumberFormatException e)
        {
            // About numbers, not about lengths.  This prompt is also used for a station's priority,
            // where negatives are perfectly valid - and the length message told the user their answer
            // had to be "0 or more", which was wrong advice for the field they were standing in.
            JOptionPane.showMessageDialog(owner(), I18n.f("autosetup.ui.errorNotANumber", entered));
        }
    }


    private Set<String> strings(TileKey tile, String key)
    {
        Set<String> out = new LinkedHashSet<>();

        Object value = session.getPointProperty(tile, key);

        if (value instanceof org.json.JSONArray)
        {
            for (Object o : (org.json.JSONArray) value) out.add(String.valueOf(o));
        }
        else if (value != null)
        {
            out.add(String.valueOf(value));
        }

        return out;
    }
    /**
     * A stored number for a point, or the value that means "not set".
     */
    private int number(TileKey tile, String key, int fallback)
    {
        Object value = session.getPointProperty(tile, key);

        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    /**
     * The speed multiplier as a PERCENTAGE, which is what the user works in.
     *
     * The model stores a factor and refuses anything outside (0, 2] - so writing the percentage
     * straight through made any real value, 80 or 120, invalidate the whole configuration on load.
     */
    private int percent(TileKey tile)
    {
        Object stored = session.getPointProperty(tile, "speedMultiplier");

        return stored instanceof Number
            ? (int) Math.round(((Number) stored).doubleValue() * 100) : 100;
    }

    private void promptPercent(TileKey tile)
    {
        String entered = JOptionPane.showInputDialog(owner(),
            I18n.f("autolayout.ui.promptEnterSpeedMultiplier", describeTile(tile)),
            String.valueOf(percent(tile)));

        if (entered == null) return;

        try
        {
            int value = Integer.parseInt(entered.trim());

            if (value <= 0 || value > 200)
            {
                JOptionPane.showMessageDialog(owner(),
                    I18n.t("autolayout.ui.errorInvalidSpeedMultiplier"));
                return;
            }

            // 100% is the default and is stored as nothing, so a file records decisions only
            session.setPointProperty(tile, "speedMultiplier",
                value == 100 ? null : value / 100.0);
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(owner(), I18n.t("autolayout.ui.errorInvalidSpeedMultiplier"));
        }
    }

    /**
     * Which locomotive calls this point home, or null.
     *
     * ONE locomotive: Point.homeLoc is a single String, and the model allows a locomotive only one
     * home.  A multi-select wrote a JSON array here, which parseAuto read with optString and turned
     * into the literal text ["BR 111"] - matching no locomotive, and then captured back in that form
     * so the damage outlived the code that caused it.
     */
    private String homeOf(TileKey tile)
    {
        Object stored = session.getPointProperty(tile, "home");

        return stored instanceof String && !((String) stored).trim().isEmpty()
            ? (String) stored : null;
    }

    /**
     * Asks for one locomotive from a list, with a box to narrow it down.
     *
     * FR-010 and FR-011. Both places that ask this question used `JOptionPane.showInputDialog` with a
     * combo box, which is fine for six locomotives and useless for sixty: the only way to a name is to
     * scroll to it. A line of text that filters the list is the whole feature.
     *
     * ONE component for both, which is what FR-011 asks for - "if it makes sense, reuse the same
     * component as home locomotives while disabling its use current button". It made sense: the two
     * questions differ only in whether "the locomotive I am driving" is a sensible answer. For a HOME
     * it is - that is the common case, assigning the loco in your hand to the station in front of you.
     * For adding to autonomy the current locomotive is usually already in it, so the button is left
     * out rather than offered and refused.
     *
     * Hand-written rather than a form, and a dialog rather than a window: this is a question, and the
     * project's rule is that new UI is built as panels inside what already exists.
     *
     * @param owner what to centre the dialog on
     * @param title the dialog's title
     * @param prompt the question
     * @param names what may be chosen, in the order they should appear
     * @param current the one to start selected, or null
     * @param useCurrent the name behind a "use current" button, or null for no such button
     * @return the chosen name, or null if the dialog was cancelled
     */
    static String pickLocomotive(java.awt.Component owner, String title, String prompt,
        List<String> names, String current, final String useCurrent)
    {
        final javax.swing.DefaultListModel<String> shown = new javax.swing.DefaultListModel<>();

        for (String name : names) shown.addElement(name);

        final javax.swing.JList<String> list = new javax.swing.JList<>(shown);

        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(12);
        list.setSelectedValue(current == null ? (names.isEmpty() ? null : names.get(0)) : current, true);

        final javax.swing.JTextField filter = new javax.swing.JTextField();

        filter.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipFilterLocomotives")));

        // Re-filtered on every keystroke, against the names as they were handed in - so deleting a
        // character brings entries back rather than filtering what is left of a previous filter.
        final List<String> all = new java.util.ArrayList<>(names);

        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            private void refilter()
            {
                String was = list.getSelectedValue();
                String wanted = filter.getText().trim().toLowerCase();

                shown.clear();

                for (String name : all)
                {
                    if (wanted.isEmpty() || name.toLowerCase().contains(wanted)) shown.addElement(name);
                }

                // Keep the selection where it still exists, so typing does not silently move the
                // answer; otherwise select the first match, so Enter means the obvious thing.
                if (was != null && shown.contains(was)) list.setSelectedValue(was, true);
                else if (!shown.isEmpty()) list.setSelectedIndex(0);
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refilter(); }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refilter(); }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refilter(); }
        });

        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));

        JPanel top = new JPanel(new java.awt.BorderLayout(0, 4));

        top.add(new javax.swing.JLabel(prompt), java.awt.BorderLayout.NORTH);
        top.add(filter, java.awt.BorderLayout.CENTER);

        panel.add(top, java.awt.BorderLayout.NORTH);
        panel.add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);

        // The dialog is built by hand rather than through showInputDialog, because "use current" is a
        // third answer and showInputDialog offers exactly two.
        final javax.swing.JOptionPane pane = new javax.swing.JOptionPane(panel,
            JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

        final String[] answer = new String[1];

        if (useCurrent != null && names.contains(useCurrent))
        {
            javax.swing.JButton pick =
                new javax.swing.JButton(I18n.f("autosetup.ui.btnUseCurrentLocomotive", useCurrent));

            pick.addActionListener(new java.awt.event.ActionListener()
            {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e)
                {
                    answer[0] = useCurrent;

                    pane.setValue(javax.swing.JOptionPane.OK_OPTION);
                }
            });

            JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

            row.add(pick);

            panel.add(row, java.awt.BorderLayout.SOUTH);
        }

        // Double-clicking a name is the same as choosing it and pressing OK, which is what a list
        // invites and what a combo box could not offer.
        list.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null)
                {
                    answer[0] = list.getSelectedValue();

                    pane.setValue(javax.swing.JOptionPane.OK_OPTION);
                }
            }
        });

        javax.swing.JDialog dialog = pane.createDialog(owner, title);

        // The filter has the caret, because typing is what this dialog is for
        dialog.addWindowListener(new java.awt.event.WindowAdapter()
        {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) { filter.requestFocusInWindow(); }
        });

        dialog.setVisible(true);
        dialog.dispose();

        if (answer[0] != null) return answer[0];

        Object chose = pane.getValue();

        if (!(chose instanceof Integer) || (Integer) chose != JOptionPane.OK_OPTION) return null;

        return list.getSelectedValue();
    }

    /**
     * How tall one station's row is in the blocked-points picker, and therefore how far one notch of
     * the wheel moves.  A row and a bit, so scrolling a long list feels like reading it rather than
     * dragging it.
     */
    private static final int BLOCKED_ROW_HEIGHT = 24;

    /**
     * Asks which squares hold this station back (FR-001).
     *
     * A checklist of the other named points, rather than a picker of one: a station may be held back by
     * more than one place, and the question "which of these" is answered faster by reading a list than
     * by opening the same dialog repeatedly.
     *
     * Only NAMED points are offered. The restriction is written into the built configuration as lock
     * edges pointing at the watched square, and a square with no name is one the operator cannot
     * recognise in a list - the name is how they know which place it is.
     */
    private void promptBlockingPoints(TileKey station)
    {
        // Read FIRST, because what is already stored decides what has to be offered (FSR-C5).
        java.util.List<TileKey> already = session.getStore().getBlockingPoints(station);

        java.util.List<TileKey> choices = new java.util.ArrayList<>();

        for (TileKey tile : session.getStore().getNamedTiles())
        {
            // Not the station itself: standing there already decides whether it is free, so watching
            // itself would make it a station nothing can be sent to.
            if (tile.equals(station)) continue;

            // And not a square that IS this station by another name (OB-083, "ensure self-selection
            // is impossible"). A caption sits on a square of its own and points AT the station, and it
            // carries a name, so it appeared in this list as though it were somewhere else - choosing
            // it would have held the station back with itself, by the back door the check above closes
            // at the front.
            //
            // THIS station's captions only, and it was briefly widened to every caption square and to
            // squares absent from the graph (FBR-C4). Both were withdrawn, and the reasons are worth
            // leaving here because the widening looked obviously right:
            //
            //   - The premise was false. It rested on AutonomyBuilder dropping an unresolvable blocker
            //     with a bare `continue`, and that line cannot be reached: nodesFor never returns an
            //     empty list (FBR-C7). Nothing was being silently dropped.
            //   - A caption square is often real track. `mayCarryACaption` allows one on a feedback
            //     square, calling it the commonest place of all, and importLegacy captions every
            //     imported station WITH ITSELF - so after a legacy import the wider filter refused
            //     every station square on the railway (FBR-B3).
            //   - And it destroyed data. `chosen` is built only from what the list offers and
            //     setBlockingPoints REPLACES the stored list, so every restriction the filter hid was
            //     deleted the moment somebody pressed OK (FBR-A2). The finding this came from opened
            //     with "a safety restriction the operator believes is on and is not is worse than one
            //     they were never offered", and the fix for it did that from the other end.
            // ... unless it is ALREADY stored, in which case it is offered whatever the rules say
            // (FSR-C5, and it subsumes FBR-A2).
            //
            // The filters above are about what may be CHOSEN. An entry that is already there was
            // chosen under some earlier version of them, or by an import, and hiding it makes it
            // permanent: this dialog is the only way to edit blockedPoints, so a restriction it does
            // not show is one nobody can ever take off. Carrying it silently past the dialog - which
            // is what the first repair did - fixes the deletion and leaves that.
            //
            // Offering it costs nothing. It appears ticked, it can be unticked, and the filters still
            // decide what may be added.
            if (station.equals(session.getCaptionTarget(tile)) && !already.contains(tile)) continue;

            choices.add(tile);
        }

        // And any stored entry the loop above never reached at all - a square that has since lost its
        // name, or is the station itself from before that was refused. Same reasoning: visible, and
        // therefore removable.
        for (TileKey held : already)
        {
            if (held != null && !choices.contains(held)) choices.add(held);
        }

        // Nothing to offer, which now also means nothing is stored - the loop above puts every stored
        // entry on the list. So returning here cannot hide anything.
        if (choices.isEmpty())
        {
            JOptionPane.showMessageDialog(owner(),
                I18n.t("autosetup.ui.infoNoOtherPointsToBlockWith"));
            return;
        }

        JPanel panel = new JPanel();

        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        // Wrapped, because the message names a station and ran off the side of a dialog sized for
        // check boxes (OB-083).
        panel.add(new javax.swing.JLabel(wrapped(
            I18n.f("autosetup.ui.promptBlockedByPoints", describeTile(station)))));

        panel.add(javax.swing.Box.createVerticalStrut(LayoutEditor.HEADING_GAP));

        java.util.List<javax.swing.JCheckBox> boxes = new java.util.ArrayList<>();

        for (TileKey tile : choices)
        {
            javax.swing.JCheckBox box = new javax.swing.JCheckBox(
                session.getStore().getPointName(tile), already.contains(tile));

            box.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            boxes.add(box);
            panel.add(box);
        }

        // White behind the list rather than the panel's default grey: this reads as a list of things
        // to pick from, and every other list in the application is white (OB-083).
        panel.setBackground(java.awt.Color.WHITE);
        panel.setOpaque(true);

        for (javax.swing.JCheckBox box : boxes)
        {
            box.setBackground(java.awt.Color.WHITE);
            box.setOpaque(true);
        }

        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(panel);

        scroll.getViewport().setBackground(java.awt.Color.WHITE);
        scroll.setBackground(java.awt.Color.WHITE);

        // A wheel notch moves a row and a bit rather than three pixels. The default unit increment is
        // one pixel, so a list of thirty stations took an unreasonable amount of scrolling.
        scroll.getVerticalScrollBar().setUnitIncrement(BLOCKED_ROW_HEIGHT);
        scroll.getVerticalScrollBar().setBlockIncrement(BLOCKED_ROW_HEIGHT * 5);

        // Wider than the 320 it was: the prompt above names a station, and at 320 the name wrapped
        // into three lines while the check boxes beside it used a third of the width.
        scroll.setPreferredSize(new java.awt.Dimension(460,
            Math.min(360, 80 + choices.size() * BLOCKED_ROW_HEIGHT)));

        if (JOptionPane.showConfirmDialog(owner(), scroll,
            I18n.t("autosetup.ui.menuBlockedByPointsTitle"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        java.util.List<TileKey> chosen = new java.util.ArrayList<>();

        // Every stored entry is on the list above, so the boxes are the whole answer (FSR-C5).
        //
        // This used to carry the unoffered ones through here instead (FBR-A2). That stopped OK from
        // deleting what the dialog did not show, and left them unremovable - the same restriction,
        // permanent, with nothing on screen. Offering them is the smaller change and answers both.
        for (int at = 0; at < boxes.size(); at++)
        {
            if (boxes.get(at).isSelected()) chosen.add(choices.get(at));
        }

        session.getStore().setBlockingPoints(station, chosen);

        // The restriction is built into the configuration as lock edges, so the running layout has to
        // be regenerated for it to mean anything - the same seam every other setup edit uses.
        placementChanged();
    }

    private void promptHome(TileKey tile)
    {
        String current = homeOf(tile);

        List<String> names = homeChoices(I18n.t("autosetup.ui.labelNone"), placedLocomotives(), current);

        if (names.size() == 1)
        {
            JOptionPane.showMessageDialog(owner(), I18n.t("error.noLocs"));
            return;
        }

        // FR-010: filterable, and offering the locomotive being driven.
        //
        // "Use current" is the common gesture this dialog exists for - the locomotive in your hand,
        // the station in front of you - and it is only offered when that locomotive is actually one of
        // the choices, so the button can never be a way to pick something the list refuses.
        String driving = parentWindow() == null || parentWindow().getActiveLoc() == null
            ? null : parentWindow().getActiveLoc().getName();

        String chosen = pickLocomotive(owner(), I18n.t("autosetup.ui.menuHomeNone"),
            I18n.t("autosetup.ui.promptHomeFor"), names,
            current == null ? names.get(0) : current, driving);

        if (chosen == null) return;

        String picked = names.get(0).equals(chosen) ? null : chosen;

        // And a home this locomotive cannot actually rest at is worth saying out loud (OB-022).
        //
        // Warned, not refused. The same state is reachable by editing the station afterwards, so
        // refusing at this one door would be arbitrary, and setting homes before finishing the track
        // is not a mistake. What IS a mistake is finding out later from a dialog that blames the
        // track: without this, every future Return Home reports IMPOSSIBLE and advises checking the
        // rails, when nothing about the rails is at fault.
        if (picked != null && !mayRestHere(tile, picked) && !confirmedAnyway(picked, tile)) return;

        // And a locomotive already at home somewhere else, which is the fourth of the rules the running
        // layout has and this door did not (TD-8).
        //
        // Warned rather than refused, like the two above: one locomotive has one station, so setting
        // this one MOVES the other, and moving it is very often what was meant. What must not happen is
        // it being moved silently - which is what used to happen on the next load instead, where the
        // loser was decided by iteration order and the only notice was a line in the log.
        TileKey already = picked == null ? null : session.homeElsewhere(tile, picked);

        if (already != null && JOptionPane.showOptionDialog(owner(),
            I18n.f("autolayout.ui.confirmHomeIsAlreadySet", picked, describeTile(already)),
            I18n.t("autolayout.ui.dialogSetHomeLocomotive"),
            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[0]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        // Through setHome, which does the sweep. setPointProperty writes the one square and nothing
        // else, which is how two squares came to share a home in the first place.
        session.setHome(tile, picked);

        refresh();
    }

    /**
     * Whether this locomotive could actually come to rest at this square.
     *
     * Asked of the RUNNING layout, because that is what HomeStaging reasons about and what Return Home
     * will use when the time comes. Unanswerable before a configuration is loaded - there is no graph
     * to reason over - and an unanswerable question is not a warning, so it says yes.
     */
    private boolean mayRestHere(TileKey tile, String locomotive)
    {
        org.traincontrol.automation.Layout layout =
            layoutSource == null ? null : layoutSource.get();

        if (layout == null || parentWindow() == null) return true;

        org.traincontrol.automation.Point point = pointOnTheLayout(layout, tile);

        if (point == null) return true;

        org.traincontrol.base.Locomotive loc =
            parentWindow().getModel() == null ? null
                : parentWindow().getModel().getLocByName(locomotive);

        if (loc == null) return true;

        return org.traincontrol.automation.HomeStaging.canBeHome(loc, point);
    }

    /**
     * Asks whether to set a home that cannot be reached, defaulting to NO.
     *
     * Defaulted to no unlike the other confirmations here, because this one answers a question the
     * operator did not ask, about a choice that cannot work as things stand.
     */
    private boolean confirmedAnyway(String locomotive, TileKey tile)
    {
        return JOptionPane.showOptionDialog(owner(),
            I18n.f("autolayout.ui.confirmCannotBeHomeHere", locomotive, describeTile(tile)),
            I18n.t("autolayout.ui.dialogSetHomeLocomotive"),
            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) == JOptionPane.YES_OPTION;
    }

    /**
     * Every locomotive the control station knows about.
     */    /**
     * Every locomotive the control station knows about.
     */
    private List<String> allLocomotives()
    {
        return locomotiveNames == null
            ? java.util.Collections.<String>emptyList() : locomotiveNames.get();
    }

    /**
     * Only the locomotives placed somewhere on this layout.
     *
     * A home is where a particular engine belongs, so offering the whole roster invites choosing one
     * that autonomy has never heard of - the setting would be stored and quietly do nothing.
     */
    private List<String> placedLocomotives()
    {
        List<String> out = new java.util.ArrayList<>();

        if (session.getReducer() == null) return out;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            String at = locomotiveAt(tile);

            if (at != null && !out.contains(at)) out.add(at);
        }

        return out;
    }

    /**
     * Which locomotive is standing on a point, or null.
     */
    /**
     * The locomotive's own settings - its functions, its speed, its length - on this square's menu.
     *
     * The same dialog the track diagram's menu opens, and the only place in the application where a
     * locomotive's ARRIVAL and DEPARTURE functions can be set: the horn on leaving, the lights on
     * arriving.  Autonomy applies them from Layout.applyDefaultLocCallbacks every time a
     * configuration loads, so they go on firing whether or not anybody can reach this - which is
     * exactly why it was worth noticing that only one window could.
     *
     * It needs the RUNNING layout, because those settings belong to a Point rather than to a square:
     * the dialog reads and writes a Locomotive through it.  So the item appears only when there is
     * one and it knows this square, and says nothing at all otherwise rather than opening a dialog
     * with nothing behind it.
     *
     * The placement is written back into the SETUP afterwards.  The dialog moves the locomotive in
     * the layout, which is the diagram menu's whole job and only half of this one's: a train moved
     * in the layout and not in the setup is a train that goes back where it was the next time the
     * configuration is built.  The diagram's own menu learned this the hard way - see
     * LayoutRightclickAutonomyMenu.placeFacing.
     */
    private void addLocomotiveSettings(javax.swing.JPopupMenu menu, TileKey target)
    {
        org.traincontrol.automation.Layout layout =
            layoutSource == null ? null : layoutSource.get();

        if (layout == null || layout.getLocomotivesToRun().isEmpty()) return;

        final org.traincontrol.automation.Point point = pointOnTheLayout(layout, target);

        if (point == null) return;

        // Offered whether or not a train is standing here - the gate that used to stop it has lost the
        // thing that made it safe (MT-101, MT-022).
        //
        // It read: "Only when there is a train to EDIT. With the square empty this item reads 'Place
        // Locomotive...', which is the third way of saying the same thing on one menu - 'Add a
        // Locomotive to Autonomy...' and 'Move a Locomotive to This Station...' are both directly
        // above it." That was true, and then OB-009 retired Move, so there were two doors and now one.
        //
        // Worse, the gate asks the RUNNING layout what is standing here, and the editor writes to the
        // SETUP. A train just placed from this very menu is in the setup and not yet on the running
        // layout, so the item disappeared at precisely the moment somebody had placed a train and
        // wanted to set its arrival function. Adam: "Critical: I no longer see the option to edit the
        // locomotive."
        //
        // menuLabelFor already says the right thing either way - "Place Locomotive At..." or "Edit
        // Locomotive At..." - so nothing has to be decided here.

        menu.add(item(GraphLocAssign.menuLabelFor(point), () ->
        {
            GraphLocAssign edit = new GraphLocAssign(parentWindow(), point, false);

            int answer = JOptionPane.showOptionDialog(owner(), edit,
                I18n.f("autolayout.ui.dialogEditOrAssignLocomotive", describeTile(target)),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                TrainControlUI.OK_CANCEL_OPTS, TrainControlUI.OK_CANCEL_OPTS[0]);

            if (answer != JOptionPane.OK_OPTION) return;

            edit.commitChanges();

            // And into the setup, so the next build puts the train where it now is
            session.placeLocomotive(target,
                point.getCurrentLocomotive() == null ? null : point.getCurrentLocomotive().getName());
        }));
    }

    /**
     * The running layout's Point for a square, preferring one with a train on it.
     *
     * A square is several Points once its arrivals have been split, and they are not
     * interchangeable here: the one holding a locomotive is the one whose settings somebody means.
     */
    private org.traincontrol.automation.Point pointOnTheLayout(
        org.traincontrol.automation.Layout layout, TileKey tile)
    {
        org.traincontrol.automation.Point first = null;

        for (String name : session.getStationIndex().pointNamesAt(tile))
        {
            org.traincontrol.automation.Point one = layout.getPoint(name);

            if (one == null) continue;

            if (one.getCurrentLocomotive() != null) return one;

            if (first == null) first = one;
        }

        return first;
    }

    /**
     * The main window, which is not always up the tree from here.
     *
     * This used to walk the window ancestry, and inside the layout editor that walk cannot arrive: the
     * editor is a JFrame, a JFrame has no owner, and the chain therefore ends at it.  So every menu
     * item that needed the main window - Place Locomotive and Edit Locomotive, both of which build a
     * GraphLocAssign from it - got null, threw a NullPointerException on the first dereference, and
     * was caught by item()'s handler, which showed a dialog saying the exception's message.  A
     * NullPointerException has no message, so the dialog said "null".
     *
     * Told rather than found, whenever the owner can tell it - see setMainWindow.  The walk stays as
     * the fallback for the surfaces that really are inside the main window.
     */
    private TrainControlUI parentWindow()
    {
        if (mainWindow != null) return mainWindow;

        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);

        while (window != null && !(window instanceof TrainControlUI))
        {
            window = window.getOwner();
        }

        return window instanceof TrainControlUI ? (TrainControlUI) window : null;
    }

    /**
     * @param window the main window, for the surfaces that are not inside it
     */
    public void setMainWindow(TrainControlUI window)
    {
        this.mainWindow = window;
    }

    private TrainControlUI mainWindow;

    private String locomotiveAt(TileKey tile)
    {
        // This one was right, which is how the label's version was diagnosed - and having two right
        // answers written down separately is what let a third be written that was wrong.
        return session == null ? null : session.getLocomotiveNameAt(tile);
    }

    /**
     * Puts a locomotive on a point.
     *
     * One at a time, and only where it is not already: a locomotive standing in two places at once is
     * a state the running layout cannot represent, so it is removed from wherever it was first.
     */
    /**
     * Puts a locomotive on a square, asked for by name.
     *
     * One question rather than the two this used to be (OB-009). Bringing a train INTO autonomy and
     * moving one already in it were separate items with separate lists, on the reasoning that a roster
     * of forty should not have to be read through to find the four that matter. The cost was that the
     * two were indistinguishable on the menu unless you already knew the difference, and the edit
     * dialog - which can do both, and more - sat directly underneath them.
     *
     * So the list is the whole roster, with the locomotives autonomy already runs marked as such and
     * sorted to the top. Choosing one of those moves it, which is what the item that went used to do.
     *
     * @param tile the square, which is a station or this item is not offered
     */
    private void placeLocomotive(TileKey tile)
    {
        List<String> placed = placedLocomotives();

        String here = locomotiveAt(tile);

        List<String> names = new java.util.ArrayList<>();

        // Already in autonomy first, and said so. A move is the commoner gesture once a railway is set
        // up, and a name on its own does not say whether choosing it will take a train off somewhere
        // else - which is the one consequence worth knowing before answering.
        for (String name : placed)
        {
            if (!name.equals(here)) names.add(I18n.f("autosetup.ui.locomotiveElsewhere", name));
        }

        for (String name : allLocomotives())
        {
            if (!placed.contains(name)) names.add(name);
        }

        if (names.isEmpty())
        {
            JOptionPane.showMessageDialog(owner(),
                I18n.t("autosetup.ui.infoAllLocomotivesInAutonomy"));
            return;
        }

        // FR-011: the same picker, filterable, without the "use current" button.
        //
        // Left out rather than shown and refused: this list is the locomotives autonomy does NOT
        // already have, so the one being driven is usually not in it, and a button that is absent
        // most of the time it is looked for is worse than no button.
        String chosen = pickLocomotive(owner(), I18n.t("autosetup.ui.menuAddToAutonomy"),
            I18n.t("autosetup.ui.promptAddToAutonomy"), names, names.get(0), null);

        if (chosen == null) return;

        String name = unmark(chosen, placed);

        if (name == null) return;

        // Taking it off wherever it was is the SESSION's job, and it does it in placeLocomotive.
        //
        // This used to do it here as well, by walking the reducer's Points - which is not the same set:
        // the reduction omits excluded pages, so a train standing on one of those was not lifted, and
        // the build then emitted it at two Points and invalidated the whole layout. The session works
        // over the configuration, which is complete.
        session.placeLocomotive(tile, name);

        placementChanged();
    }

    /**
     * Redraws whatever is showing this placement - which is not the same surface in both modes.
     *
     * In the EDITOR the caption is tile art in this window's own grid, so the grid is rebuilt.
     * applyCaption says why refresh() is the wrong tool for it: "the caption is part of the tile art,
     * and the annotation refresh that follows every other edit does not touch it" (OB-009, MT-101).
     *
     * From the track diagram's DEEP MENU the caption being looked at belongs to the main diagram, which
     * draws from the RUNNING layout rather than from the setup. So the setup gained a locomotive and
     * nothing on screen changed, while the facing menu - which reads the setup - listed it happily.
     * That is OB-035, and the inconsistency Adam spotted is exactly the two surfaces disagreeing about
     * where the truth lives.
     *
     * Rebuilding the running layout is what makes them agree, and is the same seam OB-034 uses.
     *
     * Two sentences that used to be here have been taken out, because both had stopped being true and
     * both would be read as decisions (NR-7). "onDiagramChanged is null, because this panel is a menu
     * builder with no window" - it is set, at TrainControlUI's deep-menu site, and the body below calls
     * it. And "not done while an editor is open, because the editor defers that to closing on purpose"
     * - the body rebuilds unconditionally now, deliberately, because the two surfaces are on screen
     * together. That second sentence is the one somebody would have used to argue the stale-state
     * defect could not happen.
     */
    private void placementChanged()
    {
        // BOTH, not one or the other.
        //
        // It used to refresh the editor's own grid where there was one and rebuild the running layout
        // only where there was not - on the reasoning that whoever made the change was looking at
        // whichever surface it came from. They are not: the two are on screen at the same time, and a
        // change made from the track diagram while Autonomy Setup is open went to the editor and left
        // the diagram under the pointer stating the old answer.
        //
        // MT-125, Adam, on changing which way a locomotive faces: "Does not refresh in the viewer.
        // Works in the autonomy editor." Exactly that seam.
        //
        // Rebuilding the running layout is safe to ask for unconditionally: it declines while autonomy
        // is busy, and it is what every placement made from the diagram has always done.
        if (onDiagramChanged != null) onDiagramChanged.run();

        if (parentWindow() != null) parentWindow().rebuildRunningLayoutFromSetup();

        refresh();
    }

    /**
     * The plain name behind a row of the list above.
     *
     * The marked-up rows are built from the same names they mark, so this matches rather than parses:
     * a locomotive called "BR 218 (elsewhere)" would defeat anything that trusted the suffix.
     *
     * @param chosen what the user picked
     * @param placed the locomotives autonomy already runs
     * @return the locomotive's real name, or null if the row matched nothing
     */
    private String unmark(String chosen, List<String> placed)
    {
        for (String name : placed)
        {
            if (chosen.equals(I18n.f("autosetup.ui.locomotiveElsewhere", name))) return name;
        }

        return chosen;
    }

    /**
     * Asks which locomotives, as a multi-select list rather than typed names - a name that does not
     * match the roster exactly would silently do nothing.
     *
     * @param tile
     * @param key the point property to store the choice under
     * @param names the locomotives worth offering, which is not always the whole roster
     */
    private void promptLocomotives(TileKey tile, String key, List<String> names)
    {
        if (names.isEmpty())
        {
            JOptionPane.showMessageDialog(owner(), I18n.t("error.noLocs"));
            return;
        }

        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();

        for (String name : names) model.addElement(name);

        javax.swing.JList<String> list = new javax.swing.JList<>(model);

        // Tall enough to choose from without scrolling on an ordinary roster; the old dialog showed
        // four rows and made picking three engines out of twenty a scrolling exercise.
        list.setVisibleRowCount(Math.max(8, Math.min(18, names.size())));
        control(list);

        Set<String> chosen = strings(tile, key);
        List<Integer> indexes = new java.util.ArrayList<>();

        for (int i = 0; i < names.size(); i++)
        {
            if (chosen.contains(names.get(i))) indexes.add(i);
        }

        int[] selection = new int[indexes.size()];

        for (int i = 0; i < indexes.size(); i++) selection[i] = indexes.get(i);

        list.setSelectedIndices(selection);

        // The "home" arm of this is not reached: the only caller passes "excludedLocs".  Kept rather
        // than deleted because the shape is right for any list-valued property - but anything that
        // starts using it for a HOME has to go through session.setHome instead, which sweeps that
        // locomotive's home off every other square (TD-8).  Writing the property straight through here
        // would put things back exactly as they were before that rule existed.
        if (JOptionPane.showConfirmDialog(owner(), new JScrollPane(list), I18n.t(key.equals("home")
                ? "autosetup.ui.labelHomeFor" : "autosetup.ui.labelExcludedLocs"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        List<String> picked = list.getSelectedValuesList();

        // Excluding a locomotive from the station that is its HOME contradicts the home (OB-022).
        //
        // The rule existed - HomeStaging.homeBrokenByExcluding - and had no production caller: its only
        // one went with the graph window, so the live path wrote straight through and left a station
        // and a locomotive disagreeing about each other, silently, until Return Home could not explain
        // itself.
        //
        // Warned rather than refused, like the home warning: an operator may well mean it, and the
        // same state is reachable from the other side by assigning a home the station already
        // excludes.
        if ("excludedLocs".equals(key))
        {
            String broken = homeBrokenBy(tile, picked);

            if (broken != null && JOptionPane.showOptionDialog(owner(),
                I18n.f("autolayout.ui.confirmExcludingHome", broken, describeTile(tile)),
                I18n.t("autosetup.ui.labelExcludedLocs"),
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != JOptionPane.YES_OPTION)
            {
                return;
            }
        }

        session.setPointProperty(tile, key,
            picked.isEmpty() ? null : new org.json.JSONArray(picked));

        refresh();
    }

    /**
     * The home assignment that excluding these locomotives from this square would contradict.
     *
     * Read off the SETUP rather than the running layout, because that is where this editor's home
     * assignments live - the running Point's getHomeLoc is only populated once a configuration has
     * been built, and a setup being edited may never have been.
     *
     * @param tile the square
     * @param excluded the locomotives about to be shut out of it
     * @return the home that would be contradicted, or null
     */
    private String homeBrokenBy(TileKey tile, List<String> excluded)
    {
        return homeBrokenBy(homeOf(tile), excluded);
    }

    /**
     * The home this exclusion list would break, or null.
     *
     * Static and public so the rule can be tested without a window (MT-112). Excluding a locomotive
     * from the station it is homed at is not forbidden - somebody may mean it - but it is a
     * contradiction the user should be shown rather than left to discover when a train has nowhere to
     * go at the end of a run.
     *
     * @param home the locomotive homed at this station, or null
     * @param excluded the locomotives being shut out of it
     * @return the home that would be broken, or null
     */
    public static String homeBrokenBy(String home, List<String> excluded)
    {
        if (home == null || excluded == null) return null;

        for (String name : excluded)
        {
            if (home.equals(name)) return home;
        }

        return null;
    }

    /**
     * What the "home for a locomotive" list offers.
     *
     * Static and public so the rule can be tested without a window (MT-112, from OB-022 / DD-A6).
     *
     * "None" first, then the locomotives autonomy runs, and then - if it is not already among them -
     * the home this station HAS. That last clause is the whole of the fix: an assignment may name a
     * locomotive autonomy no longer runs, and it stays that way until somebody changes it. Leaving the
     * name out made the existing assignment the one thing that could not be chosen, because a
     * non-editable combo cannot preselect a value its model does not hold. The dialog opened showing
     * "None", and pressing OK cleared the station's home without anyone asking for it.
     *
     * `HomeLocomotiveMenu` wrote that trap down in words - "opening this dialog and pressing OK would
     * then quietly reassign the station" - and then lost the callers that ran the code.
     *
     * Second in the list rather than last: it is the current answer, so it belongs where the eye
     * lands, next to the only other answer that is not a locomotive.
     *
     * @param none the label for "no home"
     * @param placed the locomotives autonomy runs
     * @param current the home this station has, or null
     * @return what the list should offer, in order
     */
    public static List<String> homeChoices(String none, List<String> placed, String current)
    {
        List<String> names = new java.util.ArrayList<>();

        names.add(none);

        if (placed != null) names.addAll(placed);

        if (current != null && !names.contains(current)) names.add(1, current);

        return names;
    }

    private void promptName(TileKey tile)
    {

        String current = session.getStore().getPointName(tile);

        String name = JOptionPane.showInputDialog(owner(),
            I18n.t("autosetup.ui.promptPointName"), current == null ? "" : current);

        if (name == null) return;

        if (name.contains("\""))
        {
            JOptionPane.showMessageDialog(owner(), I18n.t("autosetup.ui.warnQuotesStrippedFromName"));
            name = name.replace("\"", "");
        }

        // Nothing can fail here any more.  A caption points at the station’s SQUARE, so renaming
        // one is a change to the setup and to nothing else - it used to rewrite every page showing the
        // old name, and could half-succeed.
        session.setPointName(tile, name.trim());

        // The moment a station gets a name is the moment it has one worth writing on the diagram.
        // Marking a square as a station cannot do it on its own: a new one has no name yet, only the
        // coordinate the reducer invented, and nobody wants that on their track plan.
        //
        // Only where it has NO caption yet (MT-116). Adam: "Weird - the label moves around to adjacent
        // cells on rename."
        //
        // `placeCaption` MOVES a station's caption rather than refusing when it already has one, which
        // is right when somebody has asked for the name to be shown here - "asking to show a name is
        // asking for it to be here". It is wrong as a side effect of renaming: the label had a place
        // somebody chose, the rename says nothing about where it should go, and the search picks
        // whichever neighbouring square is free THIS time. So the label wandered.
        //
        // Nothing has to be re-placed for the text to change: a caption points at the station's SQUARE
        // and looks its name up, so a rename is already visible wherever the label happens to be.
        if (session.getStore().isStation(tile)
            && !session.getLabelledStationTiles().contains(tile))
        {
            placeLabelFor(tile);
        }

        // And the RUNNING layout is rebuilt, or the label goes blank (OB-034).
        //
        // A rename rebuilds the setup's own graph - touched() does that - so the station index now
        // maps this square to the NEW name. The running layout was built from the configuration as it
        // was, and still holds the old one. Everything that goes through those names then looks up a
        // Point the running layout has never heard of, so the caption finds nothing and draws nothing.
        // Rename it back and it works again, which is exactly what Adam saw.
        //
        // autonomyEditorClosed has done this rebuild all along, which is why the editor never showed
        // it: closing was the only door to it, and a station can be renamed from the diagram's own
        // menu without opening an editor at all.
        //
        // The failure is written down in that method's comment, in advance: "from that moment the
        // running layout holds names the setup no longer knows ... The caption looks up its station
        // and finds nothing, so the label goes blank."
        if (parentWindow() != null) parentWindow().rebuildRunningLayoutFromSetup();
    }

    private void promptLinkName(TileKey tile)
    {
        String current = session.getStore().getLinkName(tile);

        String name = JOptionPane.showInputDialog(owner(),
            I18n.t("autosetup.ui.promptLinkName"), current == null ? "" : current);

        if (name != null) session.setLinkName(tile, name);
    }

    /**
     * Pairs a link by choosing its partner from a list of every link on the layout.
     *
     * A list rather than a second click, because a link almost always leads to ANOTHER PAGE - and the
     * other page is not on screen, so there is nothing there to click. Two-click pairing only ever
     * worked for the rare same-page case.
     */
    private void pairFromList(TileKey tile)
    {
        List<TileKey> candidates = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();

        // Like pairs with like: a link with a link, a tunnel with a tunnel.
        //
        // The two are the same thing to autonomy - both are portals, both are traversed the same way,
        // both carry a stub route - and they differ only in what they may point at.  This list offered
        // every portal of either kind whatever it was asked about, so a link could be paired to a
        // tunnel: the jump then worked, and the diagram showed a train leaving through an arrow and
        // arriving out of a tunnel mouth, which is not what either symbol says.
        LayoutDiagramComponent from =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        if (from == null) return;

        for (Map.Entry<TileKey, LayoutDiagramComponent> entry
            : session.getGraph().getTiles().entrySet())
        {
            LayoutDiagramComponent component = entry.getValue();

            if (component == null || entry.getKey().equals(tile)) continue;

            if (component.getType() != from.getType()) continue;

            // A TUNNEL pairs only within its own page (MT-047).
            //
            // The two portal kinds are the same thing to autonomy, and that is exactly why they need
            // different rules about WHAT they may point at. A link is how a train leaves one page and
            // arrives on another - crossing pages is its whole purpose. A tunnel is a piece of track
            // that goes behind the scenery and comes out further along the SAME diagram; paired to
            // another page it would draw a train entering a hillside on one page and emerging from a
            // hillside on another, which is not what the symbol says and not what the track does.
            if (from.getType() == LayoutDiagramComponent.componentType.TUNNEL
                && !entry.getKey().getPage().equals(tile.getPage())) continue;

            String named = session.getStore().getLinkName(entry.getKey());

            candidates.add(entry.getKey());
            labels.add(named == null || named.trim().isEmpty()
                ? I18n.f("autosetup.ui.labelUnnamedLink", entry.getKey().toString())
                : named + "  -  " + entry.getKey().toString());
        }

        if (candidates.isEmpty())
        {
            JOptionPane.showMessageDialog(owner(), I18n.t("autosetup.ui.errorNoOtherLinks"));
            return;
        }

        // The diagram follows the dropdown.
        //
        // It used to follow the OK button: pick a partner, watch the pair light up, and if it was the
        // wrong one, undo it and open the list again.  The names in this list are the names of things
        // on a diagram of two hundred squares, and a coordinate pair is not something anybody holds
        // in their head - so the moment to be shown which square is meant is while the choice is
        // being made, not after it has been committed.
        //
        // Built by hand rather than through showInputDialog, which gives no way at the combo box.
        final javax.swing.JComboBox<String> choice =
            new javax.swing.JComboBox<>(labels.toArray(new String[0]));

        choice.addActionListener(e ->
        {
            int at = choice.getSelectedIndex();

            if (at >= 0 && at < candidates.size() && onReveal != null)
            {
                onReveal.accept(candidates.get(at));
            }
        });

        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));

        panel.add(new JLabel(I18n.t("autosetup.ui.promptPickPartner")), java.awt.BorderLayout.NORTH);
        panel.add(choice, java.awt.BorderLayout.CENTER);

        // The one it opens on is shown too, so the diagram and the list agree before anything is
        // touched - otherwise the first entry looks like nothing until it is scrolled past and back.
        if (onReveal != null) onReveal.accept(candidates.get(0));

        if (JOptionPane.showConfirmDialog(owner(), panel, I18n.t("autosetup.ui.toolPortals"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        int at = choice.getSelectedIndex();

        if (at < 0) return;

        session.pairPortals(tile, candidates.get(at));
    }

    /**
     * Whether a square is something a station can be told is its protecting signal.
     *
     * A SIGNAL with an address, and nothing else.  isSignal() answers true for a lamp as well - the two
     * share a drawing path - and a lamp is decoration with nothing behind it to throw.  No address means
     * no way to command it, which is the whole point of the pairing.
     *
     * @param component what is drawn on the square, or null for an empty one
     */
    private boolean isPairableSignal(LayoutDiagramComponent component)
    {
        return component != null
            && component.getType() == LayoutDiagramComponent.componentType.SIGNAL
            && component.getAccessory() != null;
    }

    /**
     * Asks which signals protect a station, and how the user would like to say.
     *
     * Two ways, because two quite different people ask this question.  Somebody looking at the diagram
     * knows the signal by where it is and can point at it; somebody who set the layout up knows it by
     * its address and would have to hunt for it on screen.  A list of every signal on the railway served
     * neither: it named them by accessory and coordinate, which is the one description nobody holds in
     * their head.
     *
     * Both ways ADD to a list rather than replace one answer, because a platform reachable from two
     * directions needs a signal on each approach.  What is paired so far is on screen the whole time -
     * that is the difference between a list and being asked the same question twice.
     *
     * @param station the station's square
     */
    private void pairProtectingSignal(TileKey station)
    {
        signalWindowOpen = true;

        try
        {
            askAboutProtectingSignals(station);
        }
        finally
        {
            // Cleared HERE, in the finally, and not in the button handlers.
            //
            // The window has four ways out - Done, Escape, the close box, and "click it on the diagram"
            // - and a flag that quietens the whole diagram is one that strands the editor grey and
            // arrowless if any one of them forgets.  This is the only place all four pass through.
            signalWindowOpen = false;

            highlightedSignals.clear();
            refresh();
        }
    }

    /**
     * The list itself, with the highlight held around it by the caller.
     *
     * One window that stays open, because the answer is a LIST: adding a second signal, removing one
     * that was paired by mistake and looking at what is there are all the same gesture repeated, and a
     * window that closed and reopened between each of them hid the list at the moment it changed.  The
     * one answer that does give the window back is "click it on the diagram", which has to, because
     * the next click belongs to the diagram.
     *
     * @param station the station's square
     */
    private void askAboutProtectingSignals(TileKey station)
    {
        // A dialog of its own, held open while the list is worked on.
        //
        // It was an option pane re-shown in a loop, which is the same window closing and opening again
        // on every change - so removing one signal of three made the whole thing blink, and the list
        // somebody was reading jumped out from under them.  The list is the point of this window; it
        // has to survive being edited.
        final javax.swing.JDialog dialog = new javax.swing.JDialog(
            javax.swing.SwingUtilities.getWindowAncestor(owner()),
            I18n.t("autosetup.ui.menuPairSignal"),
            java.awt.Dialog.ModalityType.APPLICATION_MODAL);

        final javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
        final javax.swing.JList<String> list = new javax.swing.JList<>(model);
        final javax.swing.JLabel heading = new javax.swing.JLabel();

        final javax.swing.JButton byClick =
            new javax.swing.JButton(I18n.t("autosetup.ui.optionClickSignal"));
        final javax.swing.JButton byAddress =
            new javax.swing.JButton(I18n.t("autosetup.ui.optionSignalAddress"));
        final javax.swing.JButton remove =
            new javax.swing.JButton(I18n.t("autosetup.ui.optionRemoveSignal"));
        final javax.swing.JButton done =
            new javax.swing.JButton(I18n.t("autosetup.ui.optionSignalsDone"));

        // What is paired now, on screen and on the diagram behind.  Called again after every change
        // rather than rebuilding the window.
        final Runnable show = () ->
        {
            java.util.List<TileKey> paired = session.getProtectingSignals(station);

            highlightedSignals.clear();
            highlightedSignals.addAll(paired);
            refresh();

            model.clear();

            for (TileKey one : paired)
            {
                model.addElement(I18n.f("autosetup.ui.signalListEntry",
                    addressOf(one), describeTile(one)));
            }

            if (paired.isEmpty()) model.addElement(I18n.t("autosetup.ui.signalListEmpty"));

            heading.setText(paired.isEmpty()
                ? I18n.f("autosetup.ui.promptSignalHow", describeTile(station))
                : I18n.f("autosetup.ui.promptSignalsPaired", describeTile(station)));

            list.setEnabled(!paired.isEmpty());
            remove.setEnabled(!paired.isEmpty());

            if (!paired.isEmpty()) list.setSelectedIndex(0);
        };

        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(6);

        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));

        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(heading, java.awt.BorderLayout.NORTH);
        panel.add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);

        javax.swing.JPanel buttons = new javax.swing.JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));

        buttons.add(byClick);
        buttons.add(byAddress);
        buttons.add(remove);
        buttons.add(done);

        panel.add(buttons, java.awt.BorderLayout.SOUTH);

        byAddress.addActionListener(e ->
        {
            pairSignalsByAddress(station);

            show.run();
        });

        remove.addActionListener(e ->
        {
            java.util.List<TileKey> paired = session.getProtectingSignals(station);

            int at = list.getSelectedIndex();

            if (at >= 0 && at < paired.size()) removeProtectingSignal(station, paired.get(at));

            show.run();
        });

        // The one answer that has to give the window back: the next click belongs to the diagram.
        // The list returns by itself once the click lands - see the click handler - so a second signal
        // is another click and a button rather than the whole menu again.
        byClick.addActionListener(e ->
        {
            if (needsTheGrid(station))
            {
                dialog.dispose();
                return;
            }

            signalFor = station;

            dialog.dispose();

            waitFor(I18n.f("autosetup.ui.promptClickSignal", describeTile(station)));

            refresh();
        });

        done.addActionListener(e -> dialog.dispose());

        // Escape closes it, which means the same as Done: every change was applied as it was made, so
        // there is nothing half-finished to discard.
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.getRootPane().setDefaultButton(done);

        show.run();

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(owner());
        dialog.setVisible(true);
    }

    /**
     * Finds signals by the addresses written on them and adds them.
     *
     * The LOGICAL address, which is the number the user sees everywhere - on the tile, in the switch
     * panel, in the accessory list.  Taken off the diagram component, which holds exactly that number:
     * the control station subtracts one from it to reach the accessory, because the protocol counts
     * from zero.  Asking the accessory instead would mean asking the user to subtract that one
     * themselves, from the label in front of them.
     *
     * SEVERAL at once, separated by commas or spaces, because somebody who knows the addresses knows
     * all of them and should not have to reopen this once per signal.  Every address that resolves is
     * added, and the ones that do not are reported together at the end rather than one at a time -
     * stopping at the first would throw away the good ones typed after it.
     *
     * @param station the station's square
     */
    private void pairSignalsByAddress(TileKey station)
    {
        String typed = JOptionPane.showInputDialog(owner(),
            I18n.t("autosetup.ui.promptSignalAddress"), I18n.t("autosetup.ui.menuPairSignal"),
            JOptionPane.PLAIN_MESSAGE);

        if (typed == null) return;

        java.util.List<String> notNumbers = new java.util.ArrayList<>();
        java.util.List<String> notFound = new java.util.ArrayList<>();
        java.util.List<TileKey> found = new java.util.ArrayList<>();

        for (String piece : typed.split("[,;\\s]+"))
        {
            if (piece.trim().isEmpty()) continue;

            int wanted;

            try
            {
                wanted = Integer.parseInt(piece.trim());
            }
            catch (NumberFormatException e)
            {
                notNumbers.add(piece.trim());
                continue;
            }

            TileKey at = signalAtAddress(wanted);

            if (at == null) notFound.add(String.valueOf(wanted));
            else if (!found.contains(at)) found.add(at);
        }

        for (TileKey one : found)
        {
            addProtectingSignal(station, one, false);
        }

        if (!found.isEmpty()) refresh();

        // Said once, however many went wrong, and after the good ones have been taken.  A dialog per
        // bad address would be a row of them to dismiss before seeing whether anything worked.
        if (!notNumbers.isEmpty() || !notFound.isEmpty())
        {
            StringBuilder trouble = new StringBuilder();

            if (!notNumbers.isEmpty())
            {
                trouble.append(I18n.f("autosetup.ui.errorSignalAddressNotANumber",
                    joined(notNumbers)));
            }

            if (!notFound.isEmpty())
            {
                if (trouble.length() > 0) trouble.append("\n");

                trouble.append(I18n.f("autosetup.ui.errorNoSignalAtAddress", joined(notFound)));
            }

            JOptionPane.showMessageDialog(owner(), trouble.toString());
        }
    }

    /**
     * @param wanted a logical address
     * @return the square of a signal carrying it, or null
     */
    private TileKey signalAtAddress(int wanted)
    {
        if (session.getGraph() == null) return null;

        for (java.util.Map.Entry<TileKey, LayoutDiagramComponent> entry
            : session.getGraph().getTiles().entrySet())
        {
            if (!isPairableSignal(entry.getValue())) continue;

            if (entry.getValue().getAddress() == wanted) return entry.getKey();
        }

        return null;
    }

    /**
     * Adds one signal to a station's protection, and says so in words.
     *
     * @param station the station's square
     * @param signal the signal's square
     * @param redraw whether to repaint now - false while several are being added at once
     */
    private void addProtectingSignal(TileKey station, TileKey signal, boolean redraw)
    {
        java.util.List<TileKey> paired
            = new java.util.ArrayList<>(session.getProtectingSignals(station));

        if (paired.contains(signal))
        {
            say(hint, I18n.f("autosetup.ui.signalAlreadyPaired", addressOf(signal)));
            return;
        }

        paired.add(signal);

        session.setProtectingSignals(station, paired);

        say(hint, I18n.f(paired.size() == 1 ? "autosetup.ui.setSignal" : "autosetup.ui.addedSignal",
            describeTile(station), describeTile(signal)));

        if (redraw) refresh();
    }

    /**
     * Takes one signal out of a station's protection, and says so in words.
     *
     * @param station the station's square
     * @param signal the signal's square
     */
    private void removeProtectingSignal(TileKey station, TileKey signal)
    {
        java.util.List<TileKey> paired
            = new java.util.ArrayList<>(session.getProtectingSignals(station));

        if (!paired.remove(signal)) return;

        session.setProtectingSignals(station, paired);

        say(hint, paired.isEmpty()
            ? I18n.f("autosetup.ui.clearedSignal", describeTile(station))
            : I18n.f("autosetup.ui.removedSignal", describeTile(signal), describeTile(station)));

        refresh();
    }

    /**
     * @param signals squares carrying signals
     * @return their addresses, for a menu label
     */
    private String signalAddresses(java.util.List<TileKey> signals)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        for (TileKey one : signals)
        {
            out.add(addressOf(one));
        }

        return joined(out);
    }

    private static String joined(java.util.List<String> pieces)
    {
        StringBuilder out = new StringBuilder();

        for (String piece : pieces)
        {
            if (out.length() > 0) out.append(", ");

            out.append(piece);
        }

        return out.toString();
    }

    /**
     * A text field that only lets digits be typed into it.
     *
     * Nothing else is a length, and a filter is a better answer than validation after the fact: it
     * cannot be got wrong, it needs no error message, and there is no moment where the field holds
     * something the dialog will refuse.
     *
     * Empty stays reachable on purpose - clearing the field is how a length is removed.
     *
     * @param initial what to show
     * @return the field
     */
    private javax.swing.JTextField digitsOnly(String initial)
    {
        javax.swing.JTextField field = new javax.swing.JTextField(initial, 8);

        // Three digits, 0 to 999 (OB-048).
        //
        // Refused as it is typed, like the non-digits: a length of 100000 is not a length anybody
        // means, and the alternative - accepting it and complaining afterwards - is the shape this
        // filter exists to avoid.

        ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(
            new javax.swing.text.DocumentFilter()
            {
                @Override
                public void insertString(FilterBypass fb, int offset, String text,
                    javax.swing.text.AttributeSet attr) throws javax.swing.text.BadLocationException
                {
                    if (fits(fb, 0, text)) super.insertString(fb, offset, text, attr);
                }

                @Override
                public void replace(FilterBypass fb, int offset, int length, String text,
                    javax.swing.text.AttributeSet attr) throws javax.swing.text.BadLocationException
                {
                    if (fits(fb, length, text)) super.replace(fb, offset, length, text, attr);
                }

                /**
                 * Digits, and not more than three of them once this edit has been applied.
                 */
                private boolean fits(FilterBypass fb, int replacing, String text)
                {
                    if (!digits(text)) return false;

                    int after = fb.getDocument().getLength() - replacing
                        + (text == null ? 0 : text.length());

                    return after <= MAX_LENGTH_DIGITS;
                }

                private boolean digits(String text)
                {
                    if (text == null) return true;

                    for (char c : text.toCharArray())
                    {
                        if (!Character.isDigit(c)) return false;
                    }

                    return true;
                }
            });

        return field;
    }

    /** 0 to 999: three digits is every length anybody means, and 100000 is not one (OB-048) */
    private static final int MAX_LENGTH_DIGITS = 3;

    private void applyLength(TileKey tile)
    {
        Set<TileKey> targets = selection.isEmpty()
            ? new LinkedHashSet<>(java.util.Arrays.asList(tile)) : new LinkedHashSet<>(selection);

        // read from a tile that is actually going to change: with a selection the clicked tile is not
        // necessarily among them, and prefilling from it would show a number the dialog will not touch
        TileKey sample = targets.iterator().next();

        // A field that will not accept anything but digits, rather than an open one that complains
        // afterwards (OB-043).  A number is the only answer this question has, so refusing the keystroke
        // is kinder than accepting it and then throwing it away with an error box.
        final javax.swing.JTextField field = digitsOnly(
            String.valueOf(session.getStore().getTileLength(sample)));

        int chose = JOptionPane.showConfirmDialog(owner(),
            new Object[] {I18n.t("autosetup.ui.promptTileLength"), field},
            I18n.t("autosetup.ui.menuSetLength"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (chose != JOptionPane.OK_OPTION) return;

        String entered = field.getText().trim();

        // Cleared and submitted means none (OB-043).  Adam: "if the segment length is cleared and
        // submitted, treat it as 0."  Emptying a field is how somebody says "I do not want this any
        // more", and 0 is exactly what "no length" is stored as everywhere else here.
        int length = entered.isEmpty() ? 0 : Integer.parseInt(entered);

        for (TileKey target : targets)
        {
            session.setTileLength(target, length);
        }

        selection.clear();
    }

    /**
     * A tile on the diagram was clicked while autonomy mode is on.
     *
     * @param tile which square
     * @param component what is drawn there
     * @param addToSelection whether the click was a shift-click, which adds to a bulk selection instead
     *        of acting immediately
     */
    public void tileClicked(TileKey tile, LayoutDiagramComponent component, boolean addToSelection)
    {
        if (tile == null || session.getGraph() == null) return;

        rememberIfStation(tile);

        // The one-way tool: the first click names one end.
        if (tool == Tool.ONE_WAY && oneWayFrom == null)
        {
            if (needsTheGrid(tile)) return;

            oneWayFrom = tile;

            say(hint, I18n.f("autosetup.ui.promptOneWayTo", describeTile(tile)));

            refresh();
            return;
        }

        // ... and the second names the other, after which it asks which way round it goes.
        if (oneWayFrom != null)
        {
            TileKey from = oneWayFrom;

            // Cleared before the dialog, not after: a modal dialog runs its own event loop, and a
            // second click landing while it is open would otherwise start a third square.
            oneWayFrom = null;

            if (from.equals(tile))
            {
                say(hint, I18n.t("autosetup.ui.oneWaySameSquare"));

                oneWayFrom = from;

                refresh();
                return;
            }

            // WHICH WAY, asked rather than assumed (OB-006).
            //
            // Two squares describe a run; they do not describe a direction, and the direction is the
            // entire content of the decision.  Picking the order the user happened to click in is a
            // guess, and a wrong guess here closes a stretch of railway the wrong way round and leaves
            // no trace on the diagram saying which way the user meant.
            //
            // Named by the squares themselves, in the same words the rest of this panel uses for a
            // square, so the answer can be checked against what is on screen.
            String there = I18n.f("autosetup.ui.oneWayLeaving",
                describeTile(from), describeTile(tile));

            String back = I18n.f("autosetup.ui.oneWayLeaving",
                describeTile(tile), describeTile(from));

            Object[] answers = { there, back, I18n.t("autosetup.ui.oneWayCancel") };

            int answer = javax.swing.JOptionPane.showOptionDialog(this,
                I18n.t("autosetup.ui.oneWayWhichWay"),
                I18n.t("autosetup.ui.oneWayTitle"),
                javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE,
                null, answers, answers[0]);

            if (answer != 0 && answer != 1)
            {
                say(hint, I18n.t("autosetup.ui.promptOneWayFrom"));

                refresh();
                return;
            }

            int changed = answer == 0
                ? session.setOneWayRun(from, tile)
                : session.setOneWayRun(tile, from);

            say(hint, changed < 0 ? I18n.t("autosetup.ui.oneWayNoPath")
                : I18n.f("autosetup.ui.oneWayDone", changed));

            if (changed >= 0) showRestrictionsIfHidden();

            // Armed for the next one.  Closing a run is rarely a single act - a yard is several - and
            // the alternative is pressing the button again between each.
            if (changed >= 0 && tool == Tool.ONE_WAY)
            {
                say(hint, I18n.f("autosetup.ui.oneWayDoneAgain", changed));
            }

            refresh();
            return;
        }

        // A station is waiting to be told which signal protects it, and this is that click.
        //
        // Answered BEFORE the ignored check below, because a signal is one of the squares that check
        // greys out: autonomy routes no trains through a signal, so ordinarily there is nothing to set
        // on one.  Here it is the only thing worth clicking.
        if (signalFor != null)
        {
            TileKey station = signalFor;

            if (!isPairableSignal(component))
            {
                say(hint, I18n.t("autosetup.ui.errorNotASignal"));
                return;
            }

            signalFor = null;

            addProtectingSignal(station, tile, true);

            // And straight back to the list, which is where the signal just clicked now appears.  A
            // second one is a button and another click rather than the whole right-click menu again.
            pairProtectingSignal(station);
            return;
        }

        // Autonomy takes no notice of this square, so a click here changes nothing.  Route buttons are
        // the case that matters: their connections are INFERRED from the track around them, so letting
        // them be set by hand would offer a decision the next rebuild would silently discard.
        if (isIgnored(tile))
        {
            say(hint, I18n.t("autosetup.ui.infoTileIgnored"));
            return;
        }

        if (addToSelection)
        {
            if (!selection.remove(tile)) selection.add(tile);

            refresh();
            return;
        }

        try
        {
            switch (tool)
            {
                case TEST: applyTest(tile, component); break;
                case WHY: applyWhy(tile, component); break;
                default: cycle(tile); break;
            }
        }
        catch (RuntimeException e)
        {
            JOptionPane.showMessageDialog(owner(), String.valueOf(e.getMessage()));
        }

        refresh();
    }

    /**
     * Left-click: change which way trains may run through this piece of track.
     *
     * The obvious gesture, and the one the tools used to provide - click the track, watch it change.
     * It cycles both ways -> one way -> the other way -> closed, and says in words what it just became,
     * so nobody has to infer the order from the arrows.
     *
     * A tile with more than one branch is not cycled: a click cannot say WHICH branch is meant, and
     * changing all of them at once is how a switch ends up set in a way nobody chose.  Those say so and
     * point at the menu, where the branches are listed separately.
     *
     * Whatever is clicked, the change lands on the run - clicking any tile of a straight run sets the
     * whole run, which is what makes one click enough.
     */
    /**
     * Turns the arrows back on when an edit would otherwise happen invisibly.
     *
     * OB-008: with the visibility control set to None, clicking a square changed its direction and
     * nothing on screen moved. The click worked, the hint line said so, and the diagram - which is
     * where the user was looking - was identical before and after. That reads as a broken control, and
     * the natural response to a control that does nothing is to click it again, which cycles the
     * square on to a state nobody asked for.
     *
     * Restrictions rather than All, because it is the default for the same reason: open track is most
     * of a layout and its arrows say what the reader can already assume. It shows the decision that
     * has just been made without burying it.
     *
     * Only from None, and only on an edit. Somebody who has chosen All or Arrivals has chosen
     * something that already shows their edit, and moving them off it would be the same rudeness in
     * the other direction.
     */
    private void showRestrictionsIfHidden()
    {
        if (directions.getSelectedIndex() != 2) return;

        directions.setSelectedIndex(1);

        say(hint, I18n.t("autosetup.ui.infoDirectionsShownAgain"));
    }

    private void cycle(TileKey tile)
    {
        TileKey target = leaderOf(tile);

        // A link has no direction of its own - the track either side of it governs - so a click on one
        // has nothing to change.  Its route is a stub, the same side twice, and cycling that produced
        // states that meant nothing and drew as nothing.  The menu already declines to offer them; the
        // click has to decline too, or the two disagree about the same square.
        LayoutDiagramComponent here =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(target);

        if (here != null && org.traincontrol.automationui.TilePorts.hasPortal(here.getType()))
        {
            say(hint, I18n.t("autosetup.ui.infoLinkHasNoDirection"));
            return;
        }

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(target);

        if (routes.isEmpty()) return;

        if (routes.size() > 1)
        {
            cycleBranching(target, routes);
            return;
        }

        Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> only =
            routes.entrySet().iterator().next();

        org.traincontrol.automationui.TilePorts.Route route = only.getValue();

        Direction next = after(session.getGraph().getDirection(target, only.getKey()));

        int changed = session.setRunDirection(target, only.getKey(), next);

        // The tile that changed can be some way from the one clicked, at the head of a long run, so it
        // is flashed as well as named - a message about a square nobody can find is half an answer.
        if (!target.equals(tile) && onReveal != null) onReveal.accept(target);

        // Say what actually happened.  This used to announce a direction unconditionally, so a run that
        // could not be set - a dead end, in the days when that was possible - reported success.
        say(hint, changed == 0 ? I18n.t("autosetup.ui.oneWayNoPath")
            : I18n.f("autosetup.ui.cycledTo", describeTile(target), describe(next, route)));

        if (changed != 0) showRestrictionsIfHidden();
    }

    /**
     * Left-click on a switch, a crossing or a double curve: the next combination of open and shut ARMS.
     *
     * Not the next of four whole-tile states, which is what this did before and what kept being wrong.
     * The states a junction has are not four; on a crossing they are sixteen, and the useful ones -
     * north-south open while east-west is shut - are exactly the ones a whole-tile cycle cannot reach.
     * Neither can they be enumerated as Direction constants: TOWARD_A names a route's own first side,
     * and nothing makes those agree between branches, so "TOWARD_A everywhere" on a switch with its toe
     * at S means toward N and toward S at once.
     *
     * So the state cycled is what the drawing already shows: one bit per ARM of the tile, saying
     * whether a train may leave through it.  Every combination is reachable, and the click is a plain
     * binary counter over them, which makes the order predictable without anybody having to learn it.
     *
     * The bits translate back to routes exactly.  A route (A,B) reads its two arms: both open is both
     * ways, one open is one way toward it, neither is closed.  An arm shared by two branches - a
     * switch's toe - is one bit, so the two branches cannot end up disagreeing about it, which is the
     * state that used to draw a green arrow on top of a red one.
     */
    private void cycleBranching(TileKey target,
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes)
    {
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides = armsOf(routes);

        if (sides.isEmpty()) return;

        // Every combination, in order of how often it is wanted: everything, then each ROUTE on its
        // own, then nothing, then all the rest.  On a crossing the first four read as all ways,
        // north-south only, east-west only, nothing - which is what a crossing is usually being asked -
        // and a fifth click carries on into the combinations that are only occasionally wanted.
        java.util.List<Integer> states = cycleStates(routes, sides);

        int current = armMask(target, routes, sides);

        int next = states.get(0);

        for (int i = 0; i < states.size(); i++)
        {
            if (states.get(i) == current)
            {
                next = states.get((i + 1) % states.size());
                break;
            }
        }

        applyArmMask(target, routes, sides, next);

        say(hint, I18n.f("autosetup.ui.cycledSwitch", describeTile(target), armState(next, sides)));

        showRestrictionsIfHidden();

        refresh();
    }

    /**
     * Turns a set of open arms into a direction for each route, and applies them together.
     *
     * The arms are what the drawing shows and what the user is choosing between; the per-route
     * directions are what the model stores.  One translation, used by the click and by the checkboxes,
     * so the two cannot come to different conclusions about the same square.
     */
    private void applyArmMask(TileKey target,
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes,
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides, int mask)
    {
        Map<RouteId, Direction> wanted = new java.util.LinkedHashMap<>();

        for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

            boolean openA = (mask & (1 << sides.indexOf(route.getA()))) != 0;
            boolean openB = (mask & (1 << sides.indexOf(route.getB()))) != 0;

            wanted.put(entry.getKey(), openA && openB ? Direction.BOTH
                : openA ? Direction.TOWARD_A
                : openB ? Direction.TOWARD_B : Direction.NONE);
        }

        // One re-derivation for the tile, not one per branch
        session.setDirections(target, wanted);
    }

    /**
     * Opens or shuts one arm of a square, leaving the others as they are.
     */
    private void setArm(TileKey target, org.traincontrol.automationui.TilePorts.Side arm, boolean open)
    {
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(target);

        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides = armsOf(routes);

        int at = sides.indexOf(arm);

        if (at < 0) return;

        int mask = armMask(target, routes, sides);

        applyArmMask(target, routes, sides, open ? mask | (1 << at) : mask & ~(1 << at));
    }

    /**
     * The masks a click steps through: everything open, each route's own arms, then everything shut.
     */
    private java.util.List<Integer> cycleStates(
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes,
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides)
    {
        java.util.List<Integer> states = new java.util.ArrayList<>();

        int all = (1 << sides.size()) - 1;

        states.add(all);

        // One state per route, with only that route's own arms open.  Skipped where it would repeat
        // one already listed - a two-route tile whose routes share every arm has nothing in between.
        for (org.traincontrol.automationui.TilePorts.Route route : routes.values())
        {
            int mask = 0;

            if (sides.indexOf(route.getA()) >= 0) mask |= 1 << sides.indexOf(route.getA());
            if (sides.indexOf(route.getB()) >= 0) mask |= 1 << sides.indexOf(route.getB());

            if (mask != 0 && mask != all && !states.contains(mask)) states.add(mask);
        }

        states.add(0);

        // Then everything else.  The four above are the answers somebody wants most of the time, so
        // they come first and are reached in at most four clicks; the rest follow, so clicking does
        // eventually reach every combination rather than declaring some of them unavailable.
        //
        // This is what both earlier attempts were missing.  A four-state cycle could not express
        // "north and west open, east shut"; a sixteen-state counter could, and put all-open fifteen
        // clicks from all-shut.  Ordering the same sixteen answers by how often they are wanted costs
        // nothing and gives both.
        for (int mask = 1; mask < all; mask++)
        {
            if (!states.contains(mask)) states.add(mask);
        }

        return states;
    }

    /**
     * The arms of a tile - every side any of its routes touches - in a fixed order so the counter
     * always steps the same way.
     */
    private static java.util.List<org.traincontrol.automationui.TilePorts.Side> armsOf(
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes)
    {
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.TilePorts.Side side
            : org.traincontrol.automationui.TilePorts.Side.values())
        {
            for (org.traincontrol.automationui.TilePorts.Route route : routes.values())
            {
                if ((route.getA() == side || route.getB() == side) && !sides.contains(side))
                {
                    sides.add(side);
                }
            }
        }

        return sides;
    }

    /**
     * Which arms are currently open, as a bitmask over armsOf().
     *
     * Read the way the arrows are drawn - an arm is open if ANY branch through it lets a train out -
     * so the number the counter advances from is the state the user can see.
     */
    private int armMask(TileKey target,
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes,
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides)
    {
        int mask = 0;

        for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

            Direction direction = session.getGraph().getDirection(target, entry.getKey());

            if (direction == Direction.BOTH || direction == Direction.TOWARD_A)
            {
                mask |= 1 << sides.indexOf(route.getA());
            }

            if (direction == Direction.BOTH || direction == Direction.TOWARD_B)
            {
                mask |= 1 << sides.indexOf(route.getB());
            }
        }

        return mask;
    }

    /**
     * The open arms in words - "N, W" - or the whole-tile answer when they are all open or all shut.
     */
    private String armState(int mask, java.util.List<org.traincontrol.automationui.TilePorts.Side> sides)
    {
        if (mask == 0) return I18n.t("autosetup.ui.dirNone");

        if (mask == (1 << sides.size()) - 1) return I18n.t("autosetup.ui.dirBoth");

        java.util.List<String> open = new java.util.ArrayList<>();

        for (int i = 0; i < sides.size(); i++)
        {
            if ((mask & (1 << i)) != 0) open.add(String.valueOf(sides.get(i)));
        }

        return I18n.f("autosetup.ui.dirOpenArms", String.join(", ", open));
    }

    /**
     * The next state in the cycle: both ways -> one way -> the other way -> closed -> both ways.
     */
    private static Direction after(Direction current)
    {
        switch (current)
        {
            case BOTH: return Direction.TOWARD_A;
            case TOWARD_A: return Direction.TOWARD_B;
            case TOWARD_B: return Direction.NONE;
            default: return Direction.BOTH;
        }
    }

    /**
     * What a direction means, in words, naming the side rather than an A or a B nobody can see.
     */
    private String describe(Direction direction, org.traincontrol.automationui.TilePorts.Route route)
    {
        switch (direction)
        {
            case TOWARD_A: return I18n.f("autosetup.ui.dirToward", String.valueOf(route.getA()));
            case TOWARD_B: return I18n.f("autosetup.ui.dirToward", String.valueOf(route.getB()));
            case NONE: return I18n.t("autosetup.ui.dirNone");
            default: return I18n.t("autosetup.ui.dirBoth");
        }
    }

    /**
     * Asks whether a train could get from one sensor to another, and shows the route.
     *
     * Two clicks, like portal pairing.  The answer comes from the same reduction everything else uses,
     * so what this says is what a running train would find - not a second opinion.
     */
    /**
     * Says why the train on this square is not going anywhere, and draws where it could go.
     *
     * The other test asks a question about the TRACK - could anything get from here to there - and
     * answers it from the reduction. This one asks the question users actually ask, which is about a
     * locomotive and about now: every station it might be sent to, and for each one the reason it was
     * refused. Occupancy, exclusions, switched-off squares and parking rules are all in the answer,
     * because all of them are in the decision.
     *
     * Both halves matter. The list says why, and the traces say where: a train with somewhere to go
     * draws lines, and a train with nowhere draws none - which is the same answer read two ways, and
     * the second one is visible from across the room.
     *
     * Against the layout as last LOADED, which while this editor is open is the last saved
     * configuration. Unsaved edits are not in it, and the hint says so rather than letting the user
     * assume otherwise - an explanation that quietly describes a different railway is worse than none.
     */
    private void applyWhy(TileKey tile, LayoutDiagramComponent component)
    {
        org.traincontrol.automation.Layout layout =
            layoutSource == null ? null : layoutSource.get();

        if (layout == null)
        {
            say(hint, I18n.t("autosetup.ui.whyNoLayout"));
            return;
        }

        if (component == null || !component.isFeedback())
        {
            say(hint, I18n.t("autosetup.ui.labelPointNotStation"));
            return;
        }

        // Which train is standing here.  Asked of the LAYOUT rather than of the setup, because it is
        // the layout's opinion of where trains are that decides what runs.
        org.traincontrol.base.Locomotive standing = null;

        // Through StationIndex, which is the one place that knows a square is several Points and which
        // ones.  Asking the builder again would be a second opinion about the same thing.
        org.traincontrol.automationui.StationIndex index = session.getStationIndex();

        for (String pointName : index.pointNamesAt(tile))
        {
            org.traincontrol.automation.Point p = layout.getPoint(pointName);

            if (p != null && p.getCurrentLocomotive() != null)
            {
                standing = p.getCurrentLocomotive();
                break;
            }
        }

        if (standing == null)
        {
            say(hint, I18n.t("autosetup.ui.whyNoTrainHere"));
            return;
        }

        traces.clear();

        String cannotStart = layout.explainCannotStart(standing);

        if (cannotStart != null)
        {
            sayRich(hint, I18n.f("autosetup.ui.whyCannotStart",
                escape(standing.getName()), escape(cannotStart)) + unsavedWarning());

            refresh();
            return;
        }

        java.util.Map<String, String> reasons = layout.explainDestinations(standing);

        java.util.List<String> available = new java.util.LinkedList<>();
        StringBuilder blocked = new StringBuilder();

        java.util.Set<TileKey> mustTurn = session.mandatoryTurnTiles();
        java.util.Set<TileKey> mayTurn = session.mayTurnTiles();

        // Collapsed to STATIONS, the way the locomotive panel's tooltip does.  The reasons come back
        // keyed by the running graph's Points, and a square is several of those - so a derived-graph
        // station appeared three times over, under generated names the user never chose.
        java.util.Set<String> listed = new java.util.LinkedHashSet<>();

        for (java.util.Map.Entry<String, String> entry : reasons.entrySet())
        {
            TileKey where = index.squareOf(entry.getKey());

            String station = where == null ? entry.getKey() : describeTile(where);

            if (entry.getValue() == null)
            {
                if (!listed.add("ok:" + station)) continue;

                available.add(station);

                // Drawn, so "where can it go" is read off the track rather than out of a list
                if (where != null && session.getReducer() != null)
                {
                    trace(session.getReducer().findPath(tile, where, mayTurn, mustTurn), tile, true);
                }
            }
            else
            {
                // One line per station.  The first reason is the one that would have stopped it; the
                // others are the same square's other arrival sides saying the same thing.
                if (!listed.add("no:" + station)) continue;

                blocked.append("<br>").append(escape(station)).append(": ")
                       .append(escape(entry.getValue()));
            }
        }

        // "Nowhere to go", with nothing after it, is not an answer.
        //
        // The list below names the stations that were considered and says why each was refused - so
        // when it is empty as well, the report is the word "nowhere" and nothing else, and there is
        // no way to tell "every station refused this train" from "there were no stations to refuse
        // it".  Those need opposite things done about them, and Adam met the second one: a train
        // sitting at a station the report said had nowhere to go, with no reason given for anywhere.
        //
        // So when nothing was considered, say what the setup actually holds.
        String going = available.isEmpty()
            ? I18n.t("autosetup.ui.whyNowhere")
            : I18n.f("autosetup.ui.whyCanGo", available.size(), escape(join(available)));

        String detail = blocked.toString();

        if (available.isEmpty() && reasons.isEmpty())
        {
            detail = "<br>" + escape(I18n.f("autosetup.ui.whyNothingConsidered",
                countStations(layout), countDestinations(layout),
                escape(describeBlock(layout.getLocomotiveLocation(standing)))));
        }

        // The reason behind the reason.
        //
        // "No track route leads there", said once per station, is what a SEVERED railway looks like
        // from here - and it is the answer to the wrong question.  A switch or a signal drawn without
        // an accessory address cannot be routed over, so every run through it is cut, and a setup with
        // a few dozen of those has stations that are all perfectly fine and no way between any of them.
        // The sample layout is exactly that: 79 blocking findings, seven edges, and a "why" report that
        // named twenty-eight stations and blamed the track.
        //
        // So when nothing is available and the setup has blocking findings, say that first.  It is the
        // one thing that has to be fixed before any of the rest can be true.
        if (available.isEmpty())
        {
            int blockingFindings = countBlockingFindings();

            if (blockingFindings > 0)
            {
                detail = "<br><b>" + escape(I18n.f("autosetup.ui.whyBlockedBySetup", blockingFindings))
                    + "</b>" + detail;
            }
        }

        sayRich(hint, I18n.f("autosetup.ui.whyReport", escape(standing.getName()),
            going, detail + unsavedWarning()));

        refresh();
    }

    /**
     * How many findings would stop this setup being built at all.
     *
     * The same list the findings box shows, counted rather than read: a report that says a train cannot
     * go anywhere is describing a symptom, and when there are blocking findings they are the cause.
     */
    private int countBlockingFindings()
    {
        int count = 0;

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR) count++;
        }

        return count;
    }

    /**
     * How many stations the running setup holds at all.
     *
     * Counted from the RUNNING layout rather than from the store, because that is what the answer is
     * about: the store can hold a station the last build refused, and the question being asked is why
     * the train in front of the user is not moving now.
     */
    private static int countStations(org.traincontrol.automation.Layout layout)
    {
        int count = 0;

        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.isDestination()) count++;
        }

        return count;
    }

    /**
     * And how many of those a train may actually be SENT to - a station can be a place trains stop
     * without being a place autonomy chooses.
     */
    private static int countDestinations(org.traincontrol.automation.Layout layout)
    {
        int count = 0;

        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.isDestination() && point.isActive() && point.isAutoDestination()) count++;
        }

        return count;
    }

    /**
     * What the train is standing on, for a report that has nothing else to say.
     *
     * The block matters here and nowhere else in this report: every station sharing a block with the
     * train is skipped before it can be given a reason, so on a layout where one block holds all the
     * platforms the considered list comes back empty and nothing says why.
     */
    private static String describeBlock(org.traincontrol.automation.Point standing)
    {
        if (standing == null) return "?";

        return standing.getBlock() == null ? standing.getName() : String.valueOf(standing.getBlock());
    }

    /**
     * A line warning that this answer is about the SAVED setup, when there are edits that are not in it.
     *
     * Only when there are.  A caveat printed every time is a caveat nobody reads, and on the ordinary
     * path - open the editor, ask why, close it - there is nothing to caveat: the running layout and
     * the setup on screen are the same thing.
     *
     * @return the warning, or an empty string when the setup is saved
     */
    private String unsavedWarning()
    {
        return session != null && session.isDirty()
            ? "<br><br>" + escape(I18n.t("autosetup.ui.whyUnsaved")) : "";
    }

    /**
     * Station names, comma separated, capped so one line stays one line.
     */
    private static String join(java.util.List<String> names)
    {
        StringBuilder out = new StringBuilder();

        int shown = 0;

        for (String name : names)
        {
            if (shown == 4)
            {
                out.append(", ...");
                break;
            }

            if (shown > 0) out.append(", ");

            out.append(name);
            shown++;
        }

        return out.toString();
    }

    private void applyTest(TileKey tile, LayoutDiagramComponent component)
    {
        if (component == null || !component.isFeedback())
        {
            say(hint, I18n.t("autosetup.ui.labelPointNotStation"));
            return;
        }

        if (testFrom == null)
        {
            testFrom = tile;
            traces.clear();
            waitFor(I18n.t("autosetup.ui.promptTestDestination"));
            return;
        }

        if (testFrom.equals(tile))
        {
            say(hint, I18n.t("autosetup.ui.testSameTile"));
            return;
        }

        // Both ways, always.  Asking the user to nominate a direction only makes them run the test
        // twice to learn the thing they actually wanted to know - a one-way run looks identical to a
        // broken one until you have tried it from the other end.
        // The squares trains may turn round at are handed over, because everywhere else the run may not
        // leave by the side it came in at.  Without that the test drew routes that doubled back on
        // themselves at ordinary track - the reduction knows the track runs both ways, and knowing that
        // is not the same as a train being able to use both in one journey.
        //
        // MAY, not must.  reversibleTiles() covers both, and a must-turn square is the opposite case:
        // the build emits no straight-through copy for one, so a train reaching it is turned back and
        // cannot pass.  Handing those over as well let the test draw a route straight through a square
        // the running railway turns every train at - which is the test giving a second opinion instead
        // of reporting what a train would find, the one thing it exists not to do.
        // The same turn sets the reachability check uses, from the one place that computes them, so
        // the path test and the findings panel cannot disagree about which way a train may go.
        java.util.Set<TileKey> mustTurn = session.mandatoryTurnTiles();

        java.util.Set<TileKey> mayTurn = session.mayTurnTiles();

        java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> there =
            session.getReducer() == null ? null
                : session.getReducer().findPath(testFrom, tile, mayTurn, mustTurn);

        java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> back =
            session.getReducer() == null ? null
                : session.getReducer().findPath(tile, testFrom, mayTurn, mustTurn);

        traces.clear();

        // Both directions, each as its own line.  A direction with no path draws nothing, so the two
        // questions the test answers - can it get there, can it get back - are read off the track
        // rather than out of a sentence, and a one-way route is visibly one line.
        trace(there, testFrom, true);
        trace(back, tile, false);

        // Named, not just counted.  "3 runs" says a route exists and nothing about which one, so a
        // route the user believes impossible cannot be argued with - and the squares it crosses are
        // spread over a page too big to follow a line across.  The sensors it calls at are the short
        // way to say where it went.
        sayRich(hint, I18n.f("autosetup.ui.testBothWays",
            escape(describeTile(testFrom)), escape(describeTile(tile)),
            leg(there), escape(via(there)), leg(back)));

        testFrom = null;
    }

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    private String leg(java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run)
    {
        return run == null ? I18n.t("autosetup.ui.testLegBlocked")
            : I18n.f("autosetup.ui.testLegReachable", run.size());
    }

    private void trace(java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run,
        TileKey from, boolean forward)
    {
        if (run == null) return;

        // The squares in order, which the reduction does not hand over as one list: each edge carries
        // the track BETWEEN its two Points, so the Points themselves have to be put back between them.
        java.util.List<TileKey> seq = new java.util.ArrayList<>();
        seq.add(from);

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge : run)
        {
            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                seq.add(step.getTile());
            }

            seq.add(edge.getEnd());
        }

        for (int i = 0; i < seq.size(); i++)
        {
            TileKey at = seq.get(i);

            // Which way the line enters and leaves, worked out from the squares either side of this
            // one.  Null at the ends of the run, where the line stops in the middle of the square
            // rather than running off into track nobody asked about.
            org.traincontrol.automationui.TilePorts.Side in =
                i == 0 ? null : towards(at, seq.get(i - 1));

            org.traincontrol.automationui.TilePorts.Side out =
                i == seq.size() - 1 ? null : towards(at, seq.get(i + 1));

            java.util.List<org.traincontrol.automationui.TileAnnotation.Trace> here = traces.get(at);

            if (here == null)
            {
                here = new java.util.ArrayList<>();
                traces.put(at, here);
            }

            here.add(new org.traincontrol.automationui.TileAnnotation.Trace(in, out, forward));
        }
    }

    /**
     * Which side of one square faces another, or null when they are not neighbours - which is what a
     * jump through a link looks like, and there is no side on the grid to draw it as.
     *
     * The running diagram lays its line across the same grid, so the answer lives with the grid rather
     * than being worked out again here.
     */
    private org.traincontrol.automationui.TilePorts.Side towards(TileKey from, TileKey to)
    {
        return org.traincontrol.automationui.TileGraph.gridSideTowards(from, to);
    }

    /**
     * The sensors a route calls at, in order, excluding the two ends.
     *
     * The reduction knows a path as a list of edges between Points, so this is simply their names -
     * and it is what makes a disputed route checkable: a line drawn across a page can be followed
     * wrongly, a list of places it stopped at cannot.
     */
    private String via(java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run)
    {
        if (run == null || run.size() < 2) return "";

        java.util.List<String> names = new java.util.ArrayList<>();

        for (int i = 0; i < run.size() - 1; i++)
        {
            names.add(describeTile(run.get(i).getEnd()));
        }

        // The space belongs HERE, not on the front of the message.  Properties strips leading
        // whitespace from a value, so " via {0}" loaded as "via {0}" and ran into the word before it.
        return " " + I18n.f("autosetup.ui.testVia", String.join(", ", names));
    }

    /**
     * What a square is, for a message that would otherwise be a coordinate.
     */
    /**
     * The address written on a signal, which is how anybody refers to one.
     *
     * describeTile falls back to a grid coordinate, and a coordinate is the one description of a signal
     * that means nothing to a person: it is not on the tile, not in the accessory list, and not what
     * they would say out loud.  The LOGICAL address is all three.
     *
     * @param tile the signal's square
     */
    private String addressOf(TileKey tile)
    {
        LayoutDiagramComponent component = componentAt(tile);

        return component == null ? describeTile(tile) : String.valueOf(component.getAddress());
    }

    private String describeTile(TileKey tile)
    {
        String named = session.getStore().getPointName(tile);

        if (named != null && !named.trim().isEmpty()) return named.trim();

        org.traincontrol.base.LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        return component != null && component.isFeedback()
            ? "s88 " + component.getRawAddress() : tile.getX() + "," + tile.getY();
    }

    // --- state ------------------------------------------------------------------------------------

    public Tool getTool()
    {
        return tool;
    }

    public boolean isShowingDirections()
    {
        return directions.getSelectedIndex() < 2;
    }

    /**
     * Whether the diagram is currently being used to point at a signal.
     *
     * True while the protecting-signals window is up, and while the click it hands back is being waited
     * for.  OB-040: "while the window is open, de-clutter the diagram as much as possible so users can
     * clearly see the signals.  turn off arrows, labels, etc."
     *
     * The greying was already conditional on the click half of this.  Arrows and lengths were not, so
     * the one gesture that asks somebody to FIND a particular square left the two things most likely to
     * cover it switched on.
     *
     * @return whether everything that is not a signal should get out of the way
     */
    public boolean isFocusedOnSignals()
    {
        return signalFor != null || signalWindowOpen;
    }

    public boolean isShowingLengths()
    {
        return showLengths.isSelected();
    }

    public Set<TileKey> getSelection()
    {
        return java.util.Collections.unmodifiableSet(selection);
    }

    /**
     * Whether there are edits the user has not saved - what decides whether closing has to ask.
     * @return
     */
    public boolean isDirty()
    {
        return session.isDirty();
    }

    /**
     * Throws away everything done in this editor since the last save.
     *
     * @return null when it worked, or the reason it did not
     */
    public String discardEdits()
    {
        try
        {
            session.discardEdits();

            return null;
        }
        catch (java.io.IOException e)
        {
            return String.valueOf(e.getMessage());
        }
    }

    /**
     * What the editor should draw over one square: its routes and their directions, its length, and
     * whether it is part of the bulk selection.
     *
     * Computed per tile on request rather than published as a map, because the editor already walks its
     * own grid to redraw and knows exactly which page is showing - this panel does not.
     *
     * @param tile
     * @return the annotation, possibly blank, never null
     */
    public org.traincontrol.automationui.TileAnnotation annotationFor(TileKey tile)
    {
        java.util.List<org.traincontrol.automationui.TileAnnotation.Mark> marks = new java.util.ArrayList<>();

        boolean ignored = isIgnored(tile);

        // Everything that is not a signal gets out of the way - greyed, and below, no arrows and no
        // lengths (OB-040).  Asked once and used three times, because a de-clutter that is on for one
        // of the three and off for the others is worse than not having it: the diagram then looks
        // deliberately half-dressed rather than focused.
        final boolean focused = isFocusedOnSignals();

        // While a signal is being picked, everything that is not one is greyed.
        //
        // The gesture is "click the signal", and on a diagram of several hundred squares that is a
        // sentence rather than an instruction until the squares it could possibly mean are the only
        // ones left in colour.  It inverts the usual meaning of grey here - a signal is normally the
        // greyed thing, because autonomy runs no trains through one - which is exactly why the two
        // states cannot be on screen at once.
        if (focused) ignored = !isPairableSignal(componentAt(tile));

        // A link autonomy has been told to ignore carries no trains, so it carries no arrows either.
        // Its route is still a stub the port map knows about, and drawn from that it kept an arrow
        // saying traffic could leave through it - which is exactly what switching it off denied.
        LayoutDiagramComponent atTile =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        if (atTile != null && org.traincontrol.automationui.TilePorts.hasPortal(atTile.getType())
            && session.getStore().isPortalDisabled(tile))
        {
            ignored = true;
        }

        // A tile that merely follows its run draws no arrows of its own, so the run reads as one
        // decision made at one end rather than eleven waiting to be made.  It is NOT shaded: grey on
        // this diagram means autonomy cannot use a square, and a follower is perfectly usable.
        boolean follower = isFollower(tile);

        if (isShowingDirections() && !focused && session.getGraph() != null && !ignored && !follower)
        {
            Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(tile);

            // more than one route means a switch, a crossing or a double curve - somewhere a train has
            // a choice, and somewhere the user needs to see every option rather than only the closed
            boolean branching = routes.size() > 1;

            for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
                : routes.entrySet())
            {
                org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

                Direction direction = session.getGraph().getDirection(tile, entry.getKey());

                // A route the hardware restricts is one-way whatever the user chose: the graph leaves the
                // authored direction BOTH there (see defaultDirection), but a train still cannot pass
                // against the blades, and the drawing has to say what a train can actually do.
                if (route.getDirectedToward() != null && direction != Direction.NONE)
                {
                    direction = route.getDirectedToward() == route.getA()
                        ? Direction.TOWARD_A : Direction.TOWARD_B;
                }

                // Every state is drawn, both-ways included.
                //
                // It used to be hidden on plain track - both ways is the majority of a layout and is
                // also the default, so drawing it put an arrow on every square - and that was tolerable
                // only while the checkbox that turned it back on existed.  With the checkbox gone,
                // "runs both ways" became the one state of four that looked like nothing at all: a
                // square cycled from one-way, to the other way, to closed, to BLANK, and there was no
                // way to tell the blank apart from a square nobody had touched.
                //
                // A run that is open both ways is a decision like any other, and the run marker below
                // already keeps a bare layout from being a field of arrows.
                marks.add(new org.traincontrol.automationui.TileAnnotation.Mark(
                    route.getA(), route.getB(), direction));
            }

            // ...but a bare layout cannot answer "does this sensor reach that one, and which way", so
            // each run of track between two sensors carries one arrow in the middle of it.
            if (marks.isEmpty() && flowMarks.containsKey(tile))
            {
                marks.add(flowMarks.get(tile));
            }
        }

        // 0 means "does not count" and is the default everywhere, so drawing it would number every tile
        int length = -1;

        if (showLengths.isSelected() && !focused)
        {
            int stored = session.getStore().getTileLength(tile);

            if (stored > 0) length = stored;
        }

        // Only the square a test is waiting on borrows the selection outline now.  The route itself is
        // drawn as a line through the track, which says which way it goes; an outline around every
        // square it crossed said only that it went somewhere.
        // The paired signal borrows the outline too, while its menu item is being acted on.  An
        // address names the signal but does not say where it is, and where it is is the thing somebody
        // checking a pairing actually wants to know.
        boolean outlined = selection.contains(tile) || tile.equals(testFrom)
            || highlightedSignals.contains(tile);

        // In the arrivals view every station shows every side it has, so the setting can be READ -
        // an unrestricted station drawing nothing is right on the running diagram and useless in the
        // one place somebody has come to look at exactly this.
        // Shaded from the SAME answer everything else here was decided from.
        //
        // It used to call isDimmed, which worked isIgnored out again from scratch - and so threw away
        // both of the refinements made above.  A link switched OFF therefore never greyed, and while a
        // signal was being picked nothing greyed at all, which is the whole of that gesture.
        //
        // The empty-square rule is the one part of isDimmed worth keeping: shading is a message about
        // a DRAWING - "autonomy cannot use this piece of track" - and an empty square is not a piece of
        // track.  Shading them turned the gaps between the lines into a field of grey boxes.
        boolean shaded = ignored && componentAt(tile) != null;

        org.traincontrol.automationui.TileAnnotation annotation =
            new org.traincontrol.automationui.TileAnnotation(marks, length, outlined,
            badgeFor(tile), shaded, isCurved(tile), isPairedPortal(tile),
            traces.get(tile), directions.getSelectedIndex() == 1,
            ignored ? null
                : session.arrivalMarks(tile, directions.getSelectedIndex() == VIEW_ARRIVALS))
            // Which moves a badge on a bend out to the corner - see TileAnnotation.inTheEditor.
            // Here and nowhere else: this is the only surface that draws direction arrows and
            // arrival chevrons on the same square, and so the only one where the badge has to give
            // way.  On a diagram it goes back onto the track it is about.
            .inTheEditor();

        // A train the setup puts here gets a mark of its own.  The caption says which train, and the
        // caption is on another square, is sometimes on no square, and can be switched off - so
        // without this the diagram can be read right through without seeing where the trains are.
        if (locomotiveAt(tile) != null) annotation.withTrain();

        return annotation;
    }

    /**
     * Whether this square is a link that has been paired with another.
     *
     * Unpaired links are left alone: a two-way door drawn on one that leads nowhere would promise a
     * route that does not exist, and the missing pairing already has a finding of its own.
     */
    private boolean isPairedPortal(TileKey tile)
    {
        if (session.getStore().isPortalDisabled(tile)) return false;

        LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        return component != null
            && org.traincontrol.automationui.TilePorts.hasPortal(component.getType())
            && session.getStore().getPortalPartner(tile) != null;
    }

    /**
     * Whether this square's track is drawn as a chord cutting a corner.
     *
     * Curves and double curves only, by TYPE rather than by geometry: a switch's diagonal arm is a
     * chord too, and the author asked for the tilt on curves alone.
     */
    private boolean isCurved(TileKey tile)
    {
        LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        if (component == null) return false;

        LayoutDiagramComponent.componentType type = component.getType();

        // Only the curves WITHOUT a sensor.
        //
        // Following the chord puts the arrow along the rail, which reads beautifully on plain track and
        // disappears on a sensor: a feedback curve carries the heaviest art on the diagram, and a red
        // arrow laid at forty-five degrees across it is the one thing on the page nobody can see.
        // Square N/E/S/W arrows sit clear of the icon and are legible - which is what an arrow is for.
        //
        // The sensor tiles are also the ones that matter most to read: they are the Points.
        return type == LayoutDiagramComponent.componentType.CURVE
            || type == LayoutDiagramComponent.componentType.DOUBLE_CURVE;
    }

    /**
     * Whether a tile should be pushed back rather than drawn at full strength.
     *
     * Signals only.  They sit on almost every run, their art is the heaviest on the diagram, and
     * autonomy commands them green as a matter of course - so at full strength they read as the most
     * important thing on a page where they are usually the least interesting.  Still configurable:
     * a signal can be restricted like any other tile, and a restriction on one still draws.
     */
    /**
     * The three answers for one route, with the two one-way options named by where they lead rather
     * than by an A and a B nobody can see.
     */
    private List<javax.swing.JMenuItem> directionItems(final TileKey tile, final RouteId routeId,
        org.traincontrol.automationui.TilePorts.Route route)
    {
        List<javax.swing.JMenuItem> items = new java.util.ArrayList<>();

        Direction current = session.getGraph().getDirection(tile, routeId);

        items.add(directionItem(tile, routeId, Direction.BOTH,
            I18n.t("autosetup.ui.menuRouteBoth"), current));
        items.add(directionItem(tile, routeId, Direction.TOWARD_A,
            I18n.f("autosetup.ui.menuRouteToward", String.valueOf(route.getA())), current));
        items.add(directionItem(tile, routeId, Direction.TOWARD_B,
            I18n.f("autosetup.ui.menuRouteToward", String.valueOf(route.getB())), current));
        items.add(directionItem(tile, routeId, Direction.NONE,
            I18n.t("autosetup.ui.menuRouteNone"), current));

        return items;
    }

    private javax.swing.JMenuItem directionItem(final TileKey tile, final RouteId routeId,
        final Direction direction, String text, Direction current)
    {
        javax.swing.JRadioButtonMenuItem item =
            new javax.swing.JRadioButtonMenuItem(text, direction == current);

        item.addActionListener(e ->
        {
            // Set on the run, not the tile: a run of plain track has one direction, and setting it a
            // tile at a time is both busywork and a way to end up with a run that contradicts itself.
            if (session.setRunDirection(tile, routeId, direction) == 0)
            {
                say(hint, I18n.t("autosetup.ui.oneWayNoPath"));
            }

            refresh();
        });

        return item;
    }

    /**
     * What this sensor has been designated as, or null when it is not a station.
     */
    private org.traincontrol.automationui.TileAnnotation.Badge badgeFor(TileKey tile)
    {
        // Every sensor that made it into the graph gets a badge, not only the stations.  A plain point
        // is a thing the user has decided NOT to make a station, and it should look like a decision
        // rather than like an ordinary tile nobody has reached yet.
        if (session.getReducer() == null
            || !session.getReducer().getPoints().containsKey(tile)) return null;

        String name = session.getStore().getPointName(tile);

        org.traincontrol.automationui.TilePorts.Route route = firstRoute(tile);

        boolean station = session.getStore().isStation(tile);

        // Read off the three switches, not off the flags they compile to: those are derived at build
        // time now and are never authored, so reading them would leave every badge blank.  A station
        // that turns trains round wears the terminus badge and anything else that does wears the
        // reversing one, which is what each COMPILES to and what the graph window drew.
        boolean turns = session.isTurnAround(tile);

        return new org.traincontrol.automationui.TileAnnotation.Badge(
            station,
            station && turns,
            !station && turns,
            Boolean.FALSE.equals(session.getPointProperty(tile, "active"))
                || !session.isAutoDestination(tile),
            name != null && !name.trim().isEmpty(),
            route == null ? null : route.getA(),
            route == null ? null : route.getB(),
            turns && !session.isMustTurnAround(tile));
    }

    /**
     * The tile's first route, which is where its badge is drawn.
     */
    private org.traincontrol.automationui.TilePorts.Route firstRoute(TileKey tile)
    {
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(tile);

        return routes.isEmpty() ? null : routes.values().iterator().next();
    }

    /**
     * Whether this tile takes its direction from another one, rather than carrying its own.
     */
    private boolean isFollower(TileKey tile)
    {
        TileKey leader = runLeaders.get(tile);

        return leader != null && !leader.equals(tile);
    }

    /**
     * The tile that actually gets set when the user acts on this one.
     */
    private TileKey leaderOf(TileKey tile)
    {
        TileKey leader = runLeaders.get(tile);

        return leader == null ? tile : leader;
    }

    /**
     * Whether autonomy takes no notice of this square, so nothing here is the user's to decide.
     *
     * Three ways that happens, and the drawing does not distinguish them because the answer to all
     * three is the same: leave it alone.
     *   - the page is excluded
     *   - the tile type cannot be routed over (turntables, scissors)
     *   - the tile carries whatever line it sits on rather than a line of its own (route buttons),
     *     which is INFERRED from its neighbours and so is not something to click
     */
    private boolean isIgnored(TileKey tile)
    {
        if (session.getStore().getExcludedPages().contains(tile.getPage())) return true;

        org.traincontrol.base.LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        // A blank square is not track, so there is nothing to set on it.  Treating it as configurable
        // opened a menu offering a one-way run and a length on empty space.
        if (component == null) return true;

        // A lamp carries no track at all - it is decoration on the diagram - so it is greyed with the
        // route buttons and turntables rather than left looking like something to configure.
        if (component.getType() == LayoutDiagramComponent.componentType.LAMP) return true;

        return org.traincontrol.automationui.TilePorts.isDisqualified(component.getType())
            || org.traincontrol.automationui.TilePorts.isTransparent(component.getType());
    }

    /**
     * The diagram square at a key, read from the PAGE rather than the graph.
     *
     * The graph omits excluded pages entirely, so asking it whether a square on such a page is blank
     * always answers yes - and everything on an excluded page would then stop being shaded, losing the
     * one thing the shading is there to say.
     */
    private org.traincontrol.base.LayoutDiagramComponent componentAt(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagram page = pageOf(tile);

        return page == null ? null : page.getComponent(tile.getX(), tile.getY());
    }

    /**
     * The page a key names, or null if the session has never heard of it.
     */
    private org.traincontrol.base.LayoutDiagram pageOf(TileKey tile)
    {
        for (org.traincontrol.base.LayoutDiagram diagram : session.getPages())
        {
            if (diagram.getName().equals(tile.getPage())) return diagram;
        }

        return null;
    }

    /**
     * Re-reads the setup and shows what it says.
     *
     * Runs after every edit, which is affordable because the derivation is cheap and the alternative -
     * a panel that agrees with itself while the graph has moved on - is the failure this whole design
     * exists to avoid.
     */
    public final void refresh()
    {
        flowMarks = session.flowMarks();
        runLeaders = session.runLeaders();

        // The box says what the setup says, so that opening the editor on a page already left out shows
        // it ticked rather than inviting the user to tick it again.
        boolean ignored = page != null && session.getStore().getExcludedPages().contains(page);

        if (excludePage != null) excludePage.setSelected(ignored);

        // Nothing else in this column can do anything on a page autonomy takes no notice of: there are
        // no Points to name and no run to test.  Greyed rather than hidden, so the column keeps its
        // shape and it is clear they come back when the box is unticked.
        if (testButton != null) testButton.setEnabled(!ignored);
        if (whyButton != null) whyButton.setEnabled(!ignored);

        findingsModel.clear();
        findingTiles.clear();
        findingSeverity.clear();

        // Split into what must be fixed and what is only worth checking.
        //
        // The WHOLE layout, not only this page.  Restricting it to the page in front of the user made
        // the list agree with nothing: the count on the diagram counts the layout, a setup refuses to
        // load because of a problem anywhere, and somebody working through the list had no way of
        // knowing there was more of it on a page they had not opened.  Rows about elsewhere say where,
        // and clicking one goes there.
        List<Object[]> errorRows = new java.util.ArrayList<>();
        List<Object[]> warningRows = new java.util.ArrayList<>();
        List<Object[]> noticeRows = new java.util.ArrayList<>();

        // The same three, for everywhere that is not the page in front of the reader.  Kept apart
        // rather than mixed in and labelled: this window can act on one page, so "here" and "not here"
        // is the first thing a reader needs to know about a row - before how serious it is.
        List<Object[]> elsewhereErrors = new java.util.ArrayList<>();
        List<Object[]> elsewhereWarnings = new java.util.ArrayList<>();
        List<Object[]> elsewhereNotices = new java.util.ArrayList<>();

        // The graph's own problems are NOT gathered separately here.
        //
        // AutonomyChecks.run already copies every problem the graph and the reducer raised into its
        // findings - so listing them again put each scissors crossing, unaddressed switch and unpaired
        // link into this window twice, once from each source.  A list that says the same thing twice
        // reads as two faults, and the count beside it then disagrees with the count on the diagram,
        // which gathers them once.
        for (AutonomyChecks.Finding finding : session.check())
        {
            String subject = finding.getTile() == null
                ? finding.getSubject() : describeTile(finding.getTile());

            String text = describe(finding.getMessageKey(), subject);

            boolean here = onThisPage(finding.getTile());

            // Named where it is somewhere else, since its own section says only that it is not here
            if (!here)
            {
                text = I18n.f("autosetup.ui.findingOnPage", finding.getTile().getPage(), text);
            }

            AutonomyChecks.Severity severity = finding.getSeverity();

            // Warnings are what says WARNING, and everything else that is not an error is a notice.
            //
            // This used to read "not an error and not a notice, therefore a warning", which quietly
            // made INFO findings warnings here while the count on the diagram - which adds up errors
            // and warnings only - ignored them entirely.  So closing a run both ways showed "1
            // warning" and an amber banner in this window and nothing at all on the strip, and the two
            // numbers a reader is asked to reconcile disagreed by exactly the INFO findings.
            List<Object[]> into = here
                ? (severity == AutonomyChecks.Severity.ERROR ? errorRows
                    : severity == AutonomyChecks.Severity.WARNING ? warningRows : noticeRows)
                : (severity == AutonomyChecks.Severity.ERROR ? elsewhereErrors
                    : severity == AutonomyChecks.Severity.WARNING ? elsewhereWarnings
                    : elsewhereNotices);

            into.add(new Object[] {finding.getTile(), text});
        }

        // Severity first, everywhere.  Rows about other pages used to sit under a heading of their
        // own BELOW all three sections for this page - which put a notice here above an error there.
        // An error is an error wherever it is: the setup will not load until it is fixed, so a list
        // that shows things merely worth checking first disagrees with both the count beside it and
        // the reason the reader opened it.
        //
        // Nothing is lost by the merge - a row about another page still says which page, and clicking
        // it still goes there - and within each section this page still comes first, so the things
        // that can be acted on without moving are still at the top of their group.
        errorRows.addAll(elsewhereErrors);
        warningRows.addAll(elsewhereWarnings);
        noticeRows.addAll(elsewhereNotices);

        int errors = errorRows.size();

        section(I18n.f("autosetup.ui.headingErrors", errorRows.size()), errorRows,
            AutonomyChecks.Severity.ERROR);
        section(I18n.f("autosetup.ui.headingWarningsShort", warningRows.size()), warningRows,
            AutonomyChecks.Severity.WARNING);
        section(I18n.f("autosetup.ui.headingNotices", noticeRows.size()), noticeRows,
            AutonomyChecks.Severity.NOTICE);

        // Unnamed points are checks now, and errors, so they are already in the list below with a
        // square to jump to each.  Saying it again up here was the same news twice, in a colour that
        // made it look like a third thing.
        int unnamed = unnamedPoints().size();

        // Greyed, never hidden - the same as Test a path above it.  A button that disappears takes the
        // column's shape with it and leaves the reader wondering whether they imagined it; one that is
        // there and unavailable says "nothing to do here yet" without moving anything.
        if (nameAll != null)
        {
            nameAll.setEnabled(unnamed > 0 && !ignored);

            nameAll.setToolTipText(unnamed > 0
                ? null : wrapped(I18n.t("autosetup.ui.hintNothingToName")));
        }

        if (errors > 0)
        {
            banner.setText(I18n.f("autosetup.ui.labelBlockingCount", errors));
            banner.setBackground(new java.awt.Color(255, 210, 210));
        }
        else
        {
            // Points and connections were a measurement of the GRAPH, and nobody sets a railway up in
            // order to have a number of connections.  What the user wants to know is whether it works,
            // and how many places autonomy will actually send a train - the second number being every
            // station, including the berths and the ones switched off, so the difference between the
            // two says how much of the layout is being held back.
            int stations = 0;
            int choosable = 0;

            for (org.traincontrol.automationui.GraphReducer.ReducedPoint point
                : session.getReducer().getPoints().values())
            {
                if (!point.isStation()) continue;

                stations++;

                if (session.isAutoDestination(point.getTile())
                    && !Boolean.FALSE.equals(
                        session.getPointProperty(point.getTile(), "active"))) choosable++;
            }

            banner.setText(I18n.f(warningRows.size() > 0
                    ? "autosetup.ui.labelValidWithWarnings" : "autosetup.ui.labelValid",
                choosable, stations));

            banner.setBackground(warningRows.size() > 0
                ? new java.awt.Color(255, 240, 200) : new java.awt.Color(214, 245, 214));
        }

        if (!selection.isEmpty())
        {
            say(hint, I18n.f("autosetup.ui.labelTilesSelected", selection.size()));
        }

        if (onChanged != null) onChanged.run();
    }

    /**
     * Adds one severity section to the list, headed and indented as the Auto tab's is.
     */
    /**
     * @param heading the section title, or null to add the rows under whatever heading is already above
     *        them - which is how the three severities of "elsewhere" share one
     */
    private void section(String heading, List<Object[]> rows, AutonomyChecks.Severity severity)
    {
        if (rows.isEmpty()) return;

        if (heading != null)
        {
            findingsModel.addElement(heading);
            findingTiles.add(null);
            findingSeverity.add(null);
        }

        for (Object[] row : rows)
        {
            findingsModel.addElement("   " + row[1]);
            findingTiles.add((TileKey) row[0]);
            findingSeverity.add(severity);
        }
    }

    private boolean onThisPage(TileKey tile)
    {
        return tile == null || page == null || page.equals(tile.getPage());
    }

    /**
     * The sensors on this page that nobody has named, in reading order.
     */
    private List<TileKey> unnamedPoints()
    {
        List<TileKey> out = new java.util.ArrayList<>();

        if (session.getReducer() == null) return out;

        for (org.traincontrol.automationui.GraphReducer.ReducedPoint point
            : session.getReducer().getPoints().values())
        {
            if (!onThisPage(point.getTile())) continue;

            String named = session.getStore().getPointName(point.getTile());

            if (named == null || named.trim().isEmpty()) out.add(point.getTile());
        }

        java.util.Collections.sort(out, new java.util.Comparator<TileKey>()
        {
            @Override
            public int compare(TileKey a, TileKey b)
            {
                return a.getY() != b.getY() ? a.getY() - b.getY() : a.getX() - b.getX();
            }
        });

        return out;
    }

    /**
     * Walks the unnamed sensors on this page, one prompt each, highlighting the one being named.
     *
     * The alternative is hunting for them: a generated name is a valid name, so nothing refuses to work
     * and nothing points at them - they simply turn up as coordinates in a timetable weeks later.
     */
    /**
     * Takes this page out of autonomy, or puts it back.
     *
     * Asked about only on the way OUT.  Leaving a page out is the answer that costs something - every
     * sensor on it stops being part of the railway - and putting one back costs nothing, so a
     * confirmation there would be a question with one sensible answer.
     *
     * Not saved here.  Save in this window means the autonomy setup, and this is part of it: ticking
     * the box greys the page at once so the effect is visible, and Cancel throws it away with
     * everything else, which is what makes trying it out safe.
     */
    private void setPageExcluded(boolean excluded)
    {
        if (page == null) return;

        if (excluded)
        {
            // showOptionDialog with TrainControl's own button text, not showConfirmDialog, whose
            // buttons come from the look-and-feel and so follow the JVM's locale rather than the
            // language the user picked.  Note the return: an INDEX, where 0 is the first option.
            int answer = JOptionPane.showOptionDialog(owner(),
                I18n.f("autosetup.ui.confirmExcludePage", page),
                I18n.t("autosetup.ui.btnExcludeThisPage"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]);

            if (answer != 0)
            {
                excludePage.setSelected(false);
                return;
            }
        }

        session.setPageExcluded(page, excluded);

        if (onDiagramChanged != null) onDiagramChanged.run();

        refresh();
    }

    private void nameEverything()
    {
        List<TileKey> unnamed = unnamedPoints();

        if (unnamed.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoEverythingNamed"));
            return;
        }

        for (int i = 0; i < unnamed.size(); i++)
        {
            TileKey tile = unnamed.get(i);

            // Shown before asking, so the question is about a square the user can see.
            //
            // Outlined as well as flashed.  The flash fades after a couple of seconds, and naming
            // forty points is minutes of typing - so by the time somebody had thought of a name, the
            // square they were naming looked exactly like every other one.  The outline is the
            // selection mark, which does not fade, and it is cleared when the walk ends.
            selection.clear();
            selection.add(tile);
            refresh();

            if (onReveal != null) onReveal.accept(tile);

            String name = askForName(I18n.f("autosetup.ui.promptNameEverything", i + 1,
                unnamed.size()));

            // Cancel stops the walk rather than skipping one square, because a walk of forty needs a
            // way out - and Skip is now a button of its own rather than a blank field and OK, which
            // worked and which nobody would ever have guessed at.
            if (name == null) break;

            if (name.trim().isEmpty()) continue;

            session.setPointName(tile, name.trim());

            // A station that has just been given a name has somewhere obvious for it to go, and this
            // is the one moment the user is thinking about that station in particular.
            //
            // The same "only if it has none" test as the single rename (MT-116), although this walk
            // visits only UNNAMED squares and placeCaption refuses to caption a nameless station - so
            // nothing here can already have one. It is written the same way regardless: two rename
            // paths that ask different questions is how one of them ends up wrong, and the cost of
            // asking is nothing.
            if (session.getStore().isStation(tile)
                && !session.getLabelledStationTiles().contains(tile))
            {
                placeLabelFor(tile);
            }
        }

        selection.clear();

        refresh();
    }

    /**
     * Asks for one name, with somewhere to go for a square the user does not want to name.
     *
     * Three answers rather than two.  Naming forty points is a long walk, and there was no way along
     * it that was not either typing a name or abandoning the whole thing - the way to skip one was to
     * press OK with the box empty, which works and which nobody would ever guess.
     *
     * Built as a dialog rather than through showInputDialog so that Enter still means OK: typing a
     * name and reaching for the mouse forty times is the difference between a tool and a chore.
     *
     * @param question which square this is, and how far along the walk
     * @return the name, "" to skip this one, or null to stop
     */
    private String askForName(String question)
    {
        // Focuses itself, so a name can be typed the moment the dialog is up
        final javax.swing.JTextField field = new javax.swing.JTextField(18)
        {
            @Override
            public void addNotify()
            {
                super.addNotify();

                requestFocusInWindow();
            }
        };

        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));

        panel.add(new JLabel(question), java.awt.BorderLayout.NORTH);
        panel.add(field, java.awt.BorderLayout.CENTER);

        final Object[] answers = { I18n.t("ui.ok"), I18n.t("autosetup.ui.btnSkipOne"),
            I18n.t("ui.cancel") };

        final JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
            JOptionPane.YES_NO_CANCEL_OPTION, null, answers, answers[0]);

        final javax.swing.JDialog dialog = pane.createDialog(owner(),
            I18n.t("autosetup.ui.btnNameEverything"));

        // Enter in the box is OK, which showInputDialog gave for nothing and a dialog built by hand
        // has to be told
        field.addActionListener(e ->
        {
            pane.setValue(answers[0]);

            dialog.dispose();
        });

        dialog.setVisible(true);
        dialog.dispose();

        Object chosen = pane.getValue();

        // Closed with the window button, which is the same as changing your mind about the walk
        if (chosen == null || answers[2].equals(chosen)) return null;

        if (answers[1].equals(chosen)) return "";

        return field.getText();
    }

    /**
     * @param onReveal called with a tile that should be scrolled to and flashed
     */
    /**
     * @param onDiagramChanged run after a track diagram page is written, so the grid can be rebuilt
     */
    public void setOnDiagramChanged(Runnable onDiagramChanged)
    {
        this.onDiagramChanged = onDiagramChanged;
    }

    public void setOnReveal(java.util.function.Consumer<TileKey> onReveal)
    {
        this.onReveal = onReveal;
    }

    /**
     * @return the track-lengths toggle, so the window can keep it exclusive with its own Addresses box
     */
    public JCheckBox getShowLengths()
    {
        return control(showLengths);
    }

    /**
     * @return the arrows toggle, for the window's Toggle visibility box
     */
    public javax.swing.JComboBox<String> getShowDirections()
    {
        return control(directions);
    }

    /**
     * @return the findings list, for the window to put across the bottom
     */
    public JScrollPane getFindingsPanel()
    {
        return findingsPanel;
    }

    /**
     * @param locomotiveNames supplies the roster, for exclusion and home lists
     */
    public void setLocomotiveNames(java.util.function.Supplier<List<String>> locomotiveNames)
    {
        this.locomotiveNames = locomotiveNames;
    }

    private String describe(String key, String subject)
    {
        try
        {
            return I18n.f(key, subject);
        }
        catch (RuntimeException e)
        {
            // a key that has not reached the bundles yet should not blank the whole list
            return key + " " + subject;
        }
    }

    /**
     * Writes the setup.
     *
     * @return whether it was written.  The caller closes the editor on the strength of this: a save
     *         that told the user it had failed used to close the window anyway, which reads as
     *         success and is the opposite of what the dialog just said
     */
    public boolean save()
    {
        try
        {
            AutonomyCompanionStore.Reconciliation report = session.save();

            if (!report.isClean())
            {
                // Say WHAT happened and WHY, not just a list of names (OB-052).
                //
                // This showed the names alone, with no title and no sentence - Adam: "I got a popup
                // message with no context and just a list of stations. unclear why." Worse, the two
                // lists mean opposite things: one names stations that have been FORGOTTEN, the other
                // names stations that have been KEPT because something still refers to them. Run
                // together with no headings, the reader cannot tell which of their stations they have
                // just lost.
                StringBuilder text = new StringBuilder();

                if (!report.getForgottenNames().isEmpty())
                {
                    text.append(I18n.t("autosetup.ui.infoNamesForgotten")).append("\n\n");

                    for (String forgotten : report.getForgottenNames())
                    {
                        text.append("    ").append(forgotten).append("\n");
                    }
                }

                if (!report.getNamesStillReferenced().isEmpty())
                {
                    if (text.length() > 0) text.append("\n");

                    text.append(I18n.t("autosetup.ui.infoNamesStillReferenced")).append("\n\n");

                    for (Map.Entry<String, List<String>> entry
                        : report.getNamesStillReferenced().entrySet())
                    {
                        text.append("    ").append(entry.getKey())
                            .append(" - ").append(entry.getValue()).append("\n");
                    }
                }

                if (text.length() > 0)
                {
                    JOptionPane.showMessageDialog(owner(), text.toString(),
                        I18n.t("autosetup.ui.titleSetupTidied"), JOptionPane.INFORMATION_MESSAGE);
                }
            }

            refresh();

            return true;
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(owner(), String.valueOf(e.getMessage()));

            return false;
        }
    }
}
