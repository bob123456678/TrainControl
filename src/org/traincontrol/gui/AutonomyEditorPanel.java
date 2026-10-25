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
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.util.I18n;

/**
 * Setting autonomy up by clicking on the track it runs over.  Hand written, mounted into a container the editor
 * already has; the editor hands a clicked tile here and the selected tool treats it.
 *
 * @author Adam
 */
public class AutonomyEditorPanel extends JPanel
{
    /** What a click on a tile does. */
    public static enum Tool
    {
        /** Clicking does nothing; everything is set from the right-click menu.  The ordinary state. */
        NONE,

        /**
         * Ask why a particular TRAIN is not going anywhere, and see where it can go.
         *
         * TEST asks about the track; this asks about a locomotive now: every station it might go to, and why not.
         */
        WHY,

        /** Ask whether a train could get from one sensor to another: two clicks, so a tool and not a menu item. */
        TEST,

        /**
         * Close a run of track in one direction between two squares; it writes, so it asks which direction first.
         */
        ONE_WAY
    }

    /** The setup being edited, package-private for the editor's helpers; writes go through the panel. */
    final AutonomySession session;
    private final Runnable onChanged;

    final EditorTileMenus tileMenus = new EditorTileMenus(this);

    final EditorPrompts prompts = new EditorPrompts(this);

    final EditorSignals signalPairing = new EditorSignals(this);

    final EditorMassAssign massAssignment = new EditorMassAssign(this);

    final EditorWhy whyAnswers = new EditorWhy(this);

    // The page shown, so findings can be about it
    final String page;

    // Scrolls to and flashes a tile when a finding is clicked
    private java.util.function.Consumer<TileKey> onReveal;

    /** What to do about a finding on another page: open an editor that shows it. */
    private java.util.function.Consumer<TileKey> onJumpToPage;

    /** @param action given the square of a finding on another page */
    public void setOnJumpToPage(java.util.function.Consumer<TileKey> action)
    {
        this.onJumpToPage = action;
    }

    // A finding on another page naming squares beside its own (a guard's signal) goes there with them.
    private java.util.function.BiConsumer<TileKey, List<TileKey>> onJumpToNotice;

    /** @param action given the square of a finding on another page, and the squares it names beside it */
    public void setOnJumpToNotice(java.util.function.BiConsumer<TileKey, List<TileKey>> action)
    {
        this.onJumpToNotice = action;
    }

    // Which tile each findings row is about; null for a heading or a finding with no tile
    private final List<TileKey> findingTiles = new java.util.ArrayList<>();

    /** For each row of the findings list, the squares it is about beyond its own - a guard notice's signal (MT-505). */
    private final List<List<TileKey>> findingRelated = new java.util.ArrayList<>();

    // Each row's severity, coloured as the Auto tab colours its own list
    private final List<AutonomyChecks.Severity> findingSeverity = new java.util.ArrayList<>();

    private Tool tool = Tool.NONE;

    /** The column width: LayoutEditor.SIDEBAR_WIDTH, the window's other fixed strip (OB-019). */
    private static final int WIDTH = 150;

    // The editor window's conventions: headings Semibold 13 in rgb(0,0,155), buttons bold 11.
    static final java.awt.Font FONT_HEADING =
        new java.awt.Font("Segoe UI Semibold", java.awt.Font.PLAIN, 13);

    static final java.awt.Font FONT_BUTTON = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11);

    static final java.awt.Font FONT_CONTROL = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14);

    static final java.awt.Color HEADING_COLOUR = new java.awt.Color(0, 0, 155);

    /** The findings list and the sentences beside it, one size down to fit the column. */
    static final java.awt.Font FONT_HINT = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12);

    static <T extends javax.swing.JComponent> T control(T component)
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

    /**
     * The verdict and count under the findings, with no minimum width (OB-342): its text would push the column off the
     * window; cut with an ellipsis, the tooltip holds the whole.
     */
    private final JLabel banner = new JLabel()
    {
        @Override
        public java.awt.Dimension getMinimumSize()
        {
            return new java.awt.Dimension(0, super.getMinimumSize().height);
        }
    };
    final JLabel hint = new JLabel();
    private final DefaultListModel<String> findingsModel = new DefaultListModel<>();
    private final JList<String> findings = new JList<>(findingsModel);

    /** How much direction information is drawn: one control, as the answers are exclusive. */
    private final javax.swing.JComboBox<String> directions = new javax.swing.JComboBox<String>(new String[]
    {
        I18n.t("autosetup.ui.directionsAll"),
        I18n.t("autosetup.ui.directionsRestrictions"),
        I18n.t("autosetup.ui.directionsAllowed"),
        I18n.t("autosetup.ui.directionsNone"),
        I18n.t("autosetup.ui.directionsArrivals")
    })
    {
        private boolean laidOut;

        @Override
        public void doLayout()
        {
            laidOut = true;

            try
            {
                super.doLayout();
            }
            finally
            {
                laidOut = false;
            }
        }

        /** The popup takes its width from this call; the box keeps its real width. */
        @Override
        public java.awt.Dimension getSize()
        {
            java.awt.Dimension size = super.getSize();

            if (!laidOut)
            {
                int widest = 0;

                for (int i = 0; i < getItemCount(); i++)
                {
                    widest = Math.max(widest, getFontMetrics(getFont()).stringWidth(getItemAt(i)));
                }

                size.width = Math.max(size.width, widest + POPUP_PADDING);
            }

            return size;
        }
    };

    /** Room beyond the widest phrase for the list's border, cell insets and a scroll bar. */
    private static final int POPUP_PADDING = 32;

    /** The view showing only where trains may pull in, on its own, as its marks sit where the arrows are. */
    private static final int VIEW_ARRIVALS = 3;

    /** The view that shows only the directions trains may run: the green arrows, none of the red. */
    private static final int VIEW_ALLOWED = 4;

    /** What each entry of the directions box means, by place; the meaning is what is remembered. */
    private static final int[] DIRECTIONS_BY_ENTRY = {0, 1, VIEW_ALLOWED, 2, VIEW_ARRIVALS};

    /** The visibility controls, remembered in Preferences: a person's taste, not the railway's, so not exported. */
    private static final java.util.prefs.Preferences VIEW_PREFS =
        org.traincontrol.util.Util.preferencesFor(AutonomyEditorPanel.class);

    private static final String PREF_DIRECTIONS = "autonomyEditorDirections";
    private static final String PREF_LENGTHS = "autonomyEditorLengths";

    private static final String PREF_UNMEASURED = "autonomyEditorUnmeasured";

    private static final String PREF_TRAINS = "autonomyEditorTrains";

    /** Whether the captions name the train parked at a station or the station (FR-030). */
    private static final String PREF_CAPTION_TRAINS = "autonomyEditorCaptionTrains";

    /**
     * What the captions say, as one choice, not three switches that could contradict (FR-061, OB-174).  Adam:
     * *"Station Names should be default, with the setting remembered between open."*
     */
    private static final String PREF_CAPTION_MODE = "autonomyEditorCaptionMode";

    private static final String PREF_TEXT_LABELS = "autonomyEditorTextLabels";

    /** Captions name the station, the default: this window is where a railway is named. */
    public static final int CAPTIONS_STATIONS = 0;

    public static final int CAPTIONS_PARKED = 1;

    public static final int CAPTIONS_HOMES = 2;

    /** No captions at all, the dropdown's Hide (FR-133). */
    public static final int CAPTIONS_NONE = 3;

    /** The stored Labels Only (OB-272), no longer offered: opens as Station Names with Text Labels ticked (FR-133). */
    private static final int LEGACY_LABELS_ONLY = 4;

    /** Whether captions name each square's home locomotive (MT-261), white when it is not there. */
    private static final String PREF_CAPTION_HOMES = "autonomyEditorCaptionHomes";

    /** Restrictions only by default: open track is most of a layout. */
    private static final int DIRECTIONS_DEFAULT = 1;
    private final JCheckBox showLengths = new JCheckBox(I18n.t("autosetup.ui.btnShowLengths"), false);

    private final JCheckBox showUnmeasured = new JCheckBox(I18n.t("autosetup.ui.btnShowUnmeasured"), false);

    /** Draws the trains on this editor's diagram, or hides them (FR-119). */
    private final JCheckBox showTrains = new JCheckBox(I18n.t("autosetup.ui.btnShowTrains"), true);

    /** Shows the diagram's text, or hides it (FR-133); station captions are the dropdown's. */
    private final JCheckBox showText = new JCheckBox(I18n.t("layout.ui.textLabels"), false);

    /** The squares it highlights, worked out once per refresh; null until asked. */
    private java.util.Set<TileKey> unmeasuredSquares = null;

    /** The keys `LayoutEditor` binds to this panel's actions, as their tooltips name them (OB-214). */
    public static final String SHORTCUT_HOME = "Control+H";

    public static final String SHORTCUT_NAME = "Control+S";

    public static final String SHORTCUT_LENGTH = "Control+E";

    /** Shows a station's name on the square under the pointer (FR-086). */
    public static final String SHORTCUT_STATION = "Control+N";

    public static final String SHORTCUT_LENGTHS = "Control+G";

    /** Names the parked train instead of the station (FR-030); off by default. */
    private final JCheckBox showParkedTrains =
        new JCheckBox(I18n.t("autosetup.ui.btnShowParkedTrains"), false);

    /** Names each square's home locomotive (MT-261); off by default. */
    private final JCheckBox showHomeLocomotives =
        new JCheckBox(I18n.t("autosetup.ui.btnShowHomeLocomotives"), false);

    /**
     * The one control that decides what a caption says (FR-061); the two boxes above, unmounted, are what drawing asks.
     * The index is the mode, which is remembered.
     */
    private final javax.swing.JComboBox<String> captions = new javax.swing.JComboBox<>(new String[]
    {
        I18n.t("autosetup.ui.captionsStations"),
        I18n.t("autosetup.ui.captionsParked"),
        I18n.t("autosetup.ui.captionsHomes"),
        I18n.t("autosetup.ui.captionsNone")
    });


    // Built here, mounted by the window across the bottom of the diagram
    private JScrollPane findingsPanel;

    // Offered only while something is unnamed
    private JButton nameAll;
    private javax.swing.JCheckBox excludePage;

    // The first click of a portal pairing
    private TileKey pendingPortal;

    // Bulk selection, so a run is set in one gesture
    final Set<TileKey> selection = new LinkedHashSet<>();

    /** Run after this window has written to a track diagram page, to rebuild the grid it is drawn on. */
    private Runnable onDiagramChanged;

    // The first end of a path test
    private TileKey testFrom;

    /** The last path test's squares, so switching Path Type redraws it (MT-399); cleared with the route. */
    private TileKey lastTestFrom;
    private TileKey lastTestTo;

    /** The last Why Not Moving? square, so switching Path Type asks again (MT-434). */
    private TileKey lastWhyTile;

    /**
     * Which tier the routing check answers for, and the label that names the pair; Auto by default.
     */
    private JLabel pathTypeLabel;

    private javax.swing.JRadioButton pathTypeAuto;

    private javax.swing.JRadioButton pathTypeManual;

    /** The one-way run tool, a button and not a menu item (OB-006). */
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

    /** Held so a gesture can be called off from elsewhere: a tool left pressed with nothing waiting lies. */
    private JToggleButton testButton;
    // The squares a tested path runs through, and which way; cleared with the tool or a new test.
    private final Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>>
        traces = new java.util.LinkedHashMap<>();

    private TileKey oneWayFrom;

    // The station whose protecting signal is being picked by clicking one
    TileKey signalFor;

    // And which of its two lists (FR-096)
    Guard signalForGuard = Guard.EXIT;

    /** Which of a station's two signal lists a pairing works on (FR-096), so one dialog serves both. */
    enum Guard
    {
        /** Red while the platform is claimed: the menu's "Exit Guard Signal...". */
        EXIT("autosetup.ui.menuPairSignal", "autosetup.ui.promptSignalHow", "autosetup.ui.promptSignalsPaired",
            "autosetup.ui.promptClickSignal", "autosetup.ui.setSignal", "autosetup.ui.addedSignal",
            "autosetup.ui.removedSignal", "autosetup.ui.clearedSignal"),

        /** Red when a train arrives at the station as the end of its journey (FR-096). */
        ENTRY("autosetup.ui.menuPairEntrySignal", "autosetup.ui.promptEntrySignalHow",
            "autosetup.ui.promptEntrySignalsPaired", "autosetup.ui.promptClickEntrySignal",
            "autosetup.ui.setEntrySignal", "autosetup.ui.addedEntrySignal", "autosetup.ui.removedEntrySignal",
            "autosetup.ui.clearedEntrySignal");

        final String title;
        final String how;
        final String paired;
        final String click;
        final String set;
        final String added;
        final String removed;
        final String cleared;

        Guard(String title, String how, String paired, String click, String set, String added, String removed,
            String cleared)
        {
            this.title = title;
            this.how = how;
            this.paired = paired;
            this.click = click;
            this.set = set;
            this.added = added;
            this.removed = removed;
            this.cleared = cleared;
        }
    }

    /** The station waiting to be told which square holds it back (FR-025), handled as `signalFor` is. */
    private TileKey blockingFor;

    /** Whether the "which signals protect this station" window is on screen - see isFocusedOnSignals */
    boolean signalWindowOpen;

    // The signals drawn outlined, so a pairing can be pointed at
    final java.util.Set<TileKey> highlightedSignals = new java.util.LinkedHashSet<>();

    // The squares a clicked finding is about, outlined (MT-505); not the bulk selection.  Cleared by the next click,
    // finding, Escape or tool.
    private final java.util.Set<TileKey> noticeOutline = new java.util.LinkedHashSet<>();

    // The locomotive roster, supplied, as the session is headless
    private java.util.function.Supplier<List<String>> locomotiveNames;

    // One arrow per run between sensors, recomputed on refresh from the whole reduction
    private Map<TileKey, org.traincontrol.automationui.TileAnnotation.Mark> flowMarks =
        new java.util.LinkedHashMap<>();

    // Which tile speaks for each run; the rest follow and are drawn greyed
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
        // No inset either side, to stay in step with the heading and buttons that run edge to edge.
        setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        add(buildTools(), BorderLayout.NORTH);

        // Escape is not bound here (FR-065): it would disarm the tool first and the frame's handler would then close
        // the window.  `LayoutEditor.letGoOfWhateverIsHeld` calls `putToolsDown`.

        // Built here, mounted by the window across the bottom.
        findingsPanel = buildFindings();

        // Pinned, so a long sentence cannot widen the column.
        setPreferredSize(new Dimension(WIDTH, 640));
        setMinimumSize(new Dimension(WIDTH, 240));
        setMaximumSize(new Dimension(WIDTH, Integer.MAX_VALUE));

        // Nothing here is worked by keyboard, and a focusable control swallows the window's keys.
        AutonomyViewerPanel.unfocusable(this);

        refresh();
    }

    /** Wraps a sentence to the panel's width. */
    void say(JLabel label, String text)
    {
        // To the window's banner, which has the width; the label is the fallback for a panel mounted without one.
        if (messageBanner != null && label == hint)
        {
            messageBanner.show(text);
            return;
        }

        label.setText("<html><body style='width:" + (WIDTH - 30) + "px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
    }

    /** Says something that must stay until the user acts, such as a prompt for a second click. */
    void waitFor(String text)
    {
        if (messageBanner != null) messageBanner.showUntilChanged(text);
        else say(hint, text);
    }

    /** @param messageBanner the strip across the top of the editor, where messages go */
    public void setBanner(AutonomyBanner messageBanner)
    {
        this.messageBanner = messageBanner;
    }

    // The strip across the top: what just happened, not what the setup is
    private AutonomyBanner messageBanner;

    /** The same, for mark-up this class builds itself, never user text (say() escapes it). */
    private void sayRich(JLabel label, String html)
    {
        if (messageBanner != null && label == hint)
        {
            messageBanner.show("<html>" + html + "</html>");
            return;
        }

        label.setText("<html><body style='width:" + (WIDTH - 30) + "px'>" + html + "</body></html>");
    }

    /** The tools column, a BoxLayout: a GridLayout gives every row the tallest one's height. */
    private JPanel buildTools()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        // No heading: the editor prints one.  Stacked, as two buttons in a row would set the column's width.
        testButton = toolButton(Tool.TEST, I18n.t("autosetup.ui.toolTest"));

        // Which tier the check answers for (Adam: **"Radio button Auto or Manual dictates what is in scope for the
        // routing check."**): only which stations a train may be sent to differs.
        pathTypeLabel = new JLabel(I18n.t("autosetup.ui.labelPathType"));

        // The window's blue group heading (OB-217)
        pathTypeLabel.setFont(AutonomyViewerPanel.FONT_GROUP);
        pathTypeLabel.setForeground(AutonomyViewerPanel.HEADING_COLOUR);

        pathTypeAuto = new javax.swing.JRadioButton(I18n.t("autosetup.ui.pathTypeAuto"), true);
        pathTypeManual = new javax.swing.JRadioButton(I18n.t("autosetup.ui.pathTypeManual"), false);

        javax.swing.ButtonGroup pathType = new javax.swing.ButtonGroup();
        pathType.add(pathTypeAuto);
        pathType.add(pathTypeManual);

        // On the label and both radios, so either half shows it.
        String pathTypeTip = wrapped(I18n.t("autosetup.ui.tooltipPathType"));

        pathTypeLabel.setToolTipText(pathTypeTip);
        pathTypeAuto.setToolTipText(pathTypeTip);
        pathTypeManual.setToolTipText(pathTypeTip);

        pathTypeAuto.setFont(FONT_CONTROL);
        pathTypeManual.setFont(FONT_CONTROL);

        // The route on screen follows the tier (MT-399)
        pathTypeAuto.addActionListener(e -> retestForTheNewTier());
        pathTypeManual.addActionListener(e -> retestForTheNewTier());

        whyButton = toolButton(Tool.WHY, I18n.t("autosetup.ui.toolWhy"));
        whyButton.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipWhy")));

        oneWayButton = toolButton(Tool.ONE_WAY, I18n.t("autosetup.ui.toolOneWay"));
        oneWayButton.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipOneWay")));

        nameAll = new JButton(I18n.t("autosetup.ui.btnNameEverything"));
        nameAll.addActionListener(e -> nameEverything());
        button(nameAll);


        // Leaving the page out, from the page itself; ticked, it greys at once and waits for Save.
        excludePage = new javax.swing.JCheckBox(I18n.t("autosetup.ui.btnExcludeThisPage"));
        excludePage.setToolTipText(wrapped(I18n.t("autosetup.ui.hintExcludeThisPage")));
        excludePage.setFocusable(false);
        excludePage.setFont(FONT_CONTROL);
        excludePage.addActionListener(e -> setPageExcluded(excludePage.isSelected()));

        fillWidth(testButton, nameAll);
        fillWidth(whyButton, nameAll);
        fillWidth(oneWayButton, nameAll);

        panel.add(row(testButton));

        panel.add(row(whyButton));

        // Below Why Not Moving?, on its own row (OB-217), to keep the column narrow.
        panel.add(row(pathTypeLabel));
        panel.add(row(pathTypeAuto));
        panel.add(row(pathTypeManual));

        // One-Way Run and Name Everything are in Bulk Tools (OB-217).

        // And a heading for what follows (OB-217).
        JLabel pageSettings = new JLabel(I18n.t("autosetup.ui.labelPageSettings"));

        pageSettings.setFont(AutonomyViewerPanel.FONT_GROUP);
        pageSettings.setForeground(AutonomyViewerPanel.HEADING_COLOUR);

        panel.add(row(pageSettings));
        panel.add(row(excludePage));

        // The toggles only redraw; they live in the window's Toggle visibility box.  Restored first, before anything
        // listens, as setting a combo box fires a redraw of a half-built panel.
        showDirectionsView(VIEW_PREFS.getInt(PREF_DIRECTIONS, DIRECTIONS_DEFAULT));

        showLengths.setSelected(VIEW_PREFS.getBoolean(PREF_LENGTHS, false));

        showUnmeasured.setSelected(VIEW_PREFS.getBoolean(PREF_UNMEASURED, false));

        showTrains.setSelected(VIEW_PREFS.getBoolean(PREF_TRAINS, true));

        // One remembered setting, which the two boxes follow (FR-061); the older preferences could contradict it.
        int rememberedMode = VIEW_PREFS.getInt(PREF_CAPTION_MODE, CAPTIONS_STATIONS);
        boolean rememberedText = VIEW_PREFS.getBoolean(PREF_TEXT_LABELS, false);

        // A remembered Labels Only opens as Station Names with Text Labels ticked, stored so (FR-133).
        if (rememberedMode == LEGACY_LABELS_ONLY)
        {
            rememberedMode = CAPTIONS_STATIONS;
            rememberedText = true;

            VIEW_PREFS.putInt(PREF_CAPTION_MODE, rememberedMode);
            VIEW_PREFS.putBoolean(PREF_TEXT_LABELS, rememberedText);
        }

        captions.setSelectedIndex(Math.max(CAPTIONS_STATIONS, Math.min(CAPTIONS_NONE, rememberedMode)));

        showText.setSelected(rememberedText);

        captions.setFocusable(false);

        // Control+L steps through these (OB-272).
        captions.setToolTipText("Control+L");

        showParkedTrains.setFocusable(false);
        showHomeLocomotives.setFocusable(false);

        applyCaptionMode();

        // Not focusable (OB-019): the frame's shortcuts would go dead.
        showLengths.setFocusable(false);

        // Control+G, bound by `LayoutEditor` (OB-214)
        showLengths.setToolTipText(SHORTCUT_LENGTHS);

        // Not focusable either (OB-019).
        showUnmeasured.setFocusable(false);
        showUnmeasured.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipShowUnmeasured")));

        showTrains.setFocusable(false);
        showTrains.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipShowTrains")));

        showText.setFocusable(false);
        showText.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipShowTextLabels")));

        directions.setFocusable(false);

        directions.addActionListener(e ->
        {
            VIEW_PREFS.putInt(PREF_DIRECTIONS, directionsView());
            refresh();
        });

        showLengths.addActionListener(e ->
        {
            VIEW_PREFS.putBoolean(PREF_LENGTHS, showLengths.isSelected());
            refresh();
        });

        showUnmeasured.addActionListener(e ->
        {
            VIEW_PREFS.putBoolean(PREF_UNMEASURED, showUnmeasured.isSelected());
            refresh();
        });

        // Redrawn, and the whole window, as train icons come from the railway, which may hold trains the setup lacks.
        showTrains.addActionListener(e ->
        {
            VIEW_PREFS.putBoolean(PREF_TRAINS, showTrains.isSelected());
            refresh();

            javax.swing.JRootPane window = javax.swing.SwingUtilities.getRootPane(this);

            if (window != null) window.repaint();
        });

        // The text follows the box alone (FR-133); captions are the dropdown's.
        showText.addActionListener(e ->
        {
            VIEW_PREFS.putBoolean(PREF_TEXT_LABELS, showText.isSelected());

            applyTextLabels();
        });

        captions.addActionListener(e ->
        {
            VIEW_PREFS.putInt(PREF_CAPTION_MODE, captions.getSelectedIndex());

            applyCaptionMode();

            // The grid, not a repaint: a caption's text is decided when the diagram is built.
            if (onDiagramChanged != null) onDiagramChanged.run();
        });

        hint.setFont(FONT_HINT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(hint);

        // Mounted by the window, under the findings, where it is a total.
        banner.setOpaque(true);
        banner.setFont(FONT_HEADING);
        banner.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

        return panel;
    }

    /** @return the "so many things to fix" summary, for the window to put under the findings */
    public JLabel getBanner()
    {
        return banner;
    }

    /** Stretches buttons to the column's width, as the window's Save and Cancel are. */
    private void fillWidth(javax.swing.AbstractButton... buttons)
    {
        for (javax.swing.AbstractButton button : buttons)
        {
            button.setPreferredSize(new Dimension(WIDTH, button.getPreferredSize().height));
        }
    }

    /** One control on its own line, flush left and no taller than it needs to be. */
    private JPanel row(java.awt.Component component)
    {
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
        holder.setOpaque(false);
        holder.setAlignmentX(LEFT_ALIGNMENT);
        holder.setMaximumSize(new Dimension(WIDTH, 30));
        holder.add(component);

        return holder;
    }

    /** Drops any half-made multi-click gesture and un-presses its tool, silently (the right-click menu follows). */
    void cancelPendingGesture()
    {
        if (!anythingIsArmed()) return;

        tool = Tool.NONE;
        testFrom = null;
        lastTestFrom = null;
        lastTestTo = null;
        lastWhyTile = null;
        oneWayFrom = null;
        pendingPortal = null;
        signalFor = null;
        blockingFor = null;
        tailFor = null;
        tailPicks.clear();
        highlightedSignals.clear();
        traces.clear();

        if (testButton != null) testButton.setSelected(false);
        if (whyButton != null) whyButton.setSelected(false);
        if (oneWayButton != null) oneWayButton.setSelected(false);

        say(hint, I18n.t("autosetup.ui.hintClickToCycle"));

        refresh();
    }

    /**
     * Every tool toggle, so arming one disarms the rest.  Not a ButtonGroup, whose selection cannot be undone.
     */
    private final java.util.List<JToggleButton> toolButtons = new java.util.ArrayList<>();

    private JToggleButton toolButton(final Tool which, String text)
    {
        final JToggleButton button = new JToggleButton(text, false);

        toolButtons.add(button);

        button(button);

        button.addActionListener(e ->
        {
            // Every other tool comes up, or a stale one un-pressed would leave the next click to cycle(), which edits.
            for (JToggleButton other : toolButtons)
            {
                if (other != button) other.setSelected(false);
            }

            tool = button.isSelected() ? which : Tool.NONE;

            clearGesture();

            say(hint, tool == Tool.TEST ? I18n.t("autosetup.ui.promptTestStart")
                : tool == Tool.WHY ? I18n.t("autosetup.ui.promptWhy")
                : tool == Tool.ONE_WAY ? I18n.t("autosetup.ui.promptOneWayFrom")
                : I18n.t("autosetup.ui.hintClickToCycle"));

            refresh();
        });

        return button;
    }

    /** Lets go of any half-made gesture, keeping the tool armed; a waiting one-way run would swallow the next click. */
    private void clearGesture()
    {
        pendingPortal = null;
        selection.clear();
        noticeOutline.clear();
        testFrom = null;
        lastTestFrom = null;
        lastTestTo = null;
        lastWhyTile = null;
        traces.clear();
        oneWayFrom = null;
        signalFor = null;
        blockingFor = null;

        if (tailFor != null) highlightedSignals.clear();

        tailFor = null;
        tailPicks.clear();
    }

    /**
     * Puts every tool and its button down (OB-119): a button left looking armed sends the next click to `cycle()`,
     * which edits.  Says whether anything was down (FR-065), as Escape otherwise closes the editor.
     * @return whether anything was armed, and is not any more
     */
    public boolean putToolsDown()
    {
        if (!anythingIsArmed()) return false;

        for (JToggleButton other : toolButtons)
        {
            other.setSelected(false);
        }

        tool = Tool.NONE;

        clearGesture();

        say(hint, I18n.t("autosetup.ui.hintClickToCycle"));

        refresh();

        return true;
    }

    /**
     * Whether a click on a drawn train is a click on its station: while Test a Path or Why Not Moving? waits for one
     * (OB-354).
     * @return true where the train's square is the answer
     */
    public boolean takesATrainForItsStation()
    {
        return (tool == Tool.TEST || tool == Tool.WHY) && tailFor == null && oneWayFrom == null && signalFor == null
            && blockingFor == null;
    }

    /** Whether a tool is armed or a gesture half made: one definition for `putToolsDown` and `cancelPendingGesture`. */
    private boolean anythingIsArmed()
    {
        return tool != Tool.NONE || testFrom != null || oneWayFrom != null
            || pendingPortal != null || signalFor != null || blockingFor != null || tailFor != null;
    }

    private JScrollPane buildFindings()
    {
        findings.setVisibleRowCount(8);

        // Not focusable (OB-200): a finding taking the keyboard would never give the window's shortcuts back.
        findings.setFocusable(false);

        findings.setFont(FONT_CONTROL);

        // The Auto tab's colours: red must be fixed, amber worth checking, grey headings.
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

        // Clicking a finding goes to its tile.
        findings.addListSelectionListener(e ->
        {
            if (e.getValueIsAdjusting()) return;

            int row = findings.getSelectedIndex();

            // And its text onto the clipboard (MT-767), for a notice, not a heading
            if (row >= 0 && row < findingSeverity.size() && findingSeverity.get(row) != null)
            {
                copyNoticeToClipboard(findingsModel.get(row));
            }

            if (row >= 0 && row < findingTiles.size() && findingTiles.get(row) != null)
            {
                TileKey at = findingTiles.get(row);

                // And the squares it names beside its own (MT-505), outlined and flashed alike (OB-352).
                List<TileKey> related = row < findingRelated.size() && findingRelated.get(row) != null
                    ? findingRelated.get(row) : java.util.Collections.<TileKey>emptyList();

                // A finding on another page goes to the main window, which opens an editor there.
                if (!onThisPage(at))
                {
                    if (onJumpToNotice != null)
                    {
                        onJumpToNotice.accept(at, related);
                    }
                    else if (onJumpToPage != null)
                    {
                        onJumpToPage.accept(at);
                    }

                    return;
                }

                outlineTheNotice(at, related);
            }
        });

        JScrollPane scroll = new JScrollPane(findings);

        javax.swing.border.TitledBorder titled =
            BorderFactory.createTitledBorder(I18n.t("autosetup.ui.colWarnings"));

        titled.setTitleFont(AutonomyViewerPanel.FONT_GROUP);
        titled.setTitleColor(new java.awt.Color(0, 0, 155));

        scroll.setBorder(titled);
        scroll.setPreferredSize(new Dimension(WIDTH - 20, 160));

        return scroll;
    }


    /**
     * Everything that can be set on a tile, on one menu built like LayoutEditorRightclickMenu's, items in the graph
     * window's order and wording.
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
     * The same menu, handed back for the main window's right-click menu to carry, so the two cannot drift.
     * @param tile which square
     * @param component what is drawn there, or null to read it off the page
     * @return the menu, or null where this square has nothing to offer
     */
    public javax.swing.JPopupMenu buildTileMenu(TileKey tile, LayoutDiagramComponent component)
    {
        return tileMenus.buildTileMenu(tile, component);
    }

    /**
     * Takes the gaps out of a menu built in sections (OB-054), repeated until nothing changes: no divider at either
     * end or twice in a row, no heading over an empty section.  Public so its rules can be tested on menus the real
     * ones never produce.
     * @param menu the menu to tidy, in place
     */
    public static void tidy(javax.swing.JPopupMenu menu)
    {
        boolean again = true;

        while (again)
        {
            again = false;

            for (int at = 0; at < menu.getComponentCount(); at++)
            {
                java.awt.Component here = menu.getComponent(at);

                boolean separator = here instanceof javax.swing.JSeparator;

                // A heading is a disabled non-separator item from title(); a disabled ordinary item is a real offer.
                boolean heading = here instanceof javax.swing.JMenuItem
                    && !here.isEnabled();

                java.awt.Component next = at + 1 < menu.getComponentCount()
                    ? menu.getComponent(at + 1) : null;

                // Past the divider (OB-112): title() writes heading and divider together, so an empty section is a
                // heading, a divider and then nothing (OB-054).
                java.awt.Component afterNext = at + 2 < menu.getComponentCount()
                    ? menu.getComponent(at + 2) : null;

                // Two tests, not one variable: a divider looks one ahead, a heading two (OB-054).
                boolean nothingAfterTheDivider = next == null
                    || next instanceof javax.swing.JSeparator;

                boolean nothingAfterTheHeading = next == null
                    || (next instanceof javax.swing.JSeparator
                        && (afterNext == null || afterNext instanceof javax.swing.JSeparator));

                if (separator && (at == 0 || nothingAfterTheDivider))
                {
                    menu.remove(at);
                    again = true;
                    break;
                }

                if (heading && nothingAfterTheHeading)
                {
                    menu.remove(at);
                    again = true;
                    break;
                }
            }
        }
    }

    /** Flashes the square the open menu belongs to, so a menu edit is as visible as a click. */
    private void flashMenuTarget()
    {
        if (menuTarget != null && onReveal != null) onReveal.accept(menuTarget);
    }

    /**
     * Flashes the square this one is joined to, when it is on this page (FR-056), where jumping would move away.
     * Silent with no partner, another page, or not a link.
     * @param tile the square whose menu is opening
     */
    void highlightPartnerOf(TileKey tile)
    {
        if (tile == null || onReveal == null || menuOnly) return;

        LayoutDiagramComponent here = componentAt(tile);

        if (here == null) return;

        if (!here.isLink() && here.getType() != LayoutDiagramComponent.componentType.TUNNEL) return;

        TileKey partner = session.getStore().getPortalPartner(tile);

        if (partner == null || !onThisPage(partner)) return;

        onReveal.accept(partner);
    }

    // Square numbers follow the grid (OB-179, `LayoutGrid.coordinatesVisible`); Control+K toggles both.

    /**
     * The setup-wide actions (MT-257), for the tile menu and an ignored square's one-item menu.  Each greys on its own
     * count, the guard's own question.
     * @return the submenu, always present, its items enabled or not
     */
    javax.swing.JMenu bulkTools()
    {
        javax.swing.JMenu bulk = new javax.swing.JMenu(I18n.t("autosetup.ui.menuBulkTools"));

        bulk.setToolTipText(wrapped(I18n.t("autosetup.ui.hintBulkTools")));

        // One-Way Run and Name Everything in the autonomy editor only (OB-217): the track diagram's menus have no page.
        if (page != null)
        {
            if (oneWayButton != null)
            {
                javax.swing.JMenuItem oneWay = item(I18n.t("autosetup.ui.toolOneWay"), () ->
                {
                    // Through the button's own action, which holds the arming rules.
                    if (!oneWayButton.isSelected()) oneWayButton.doClick();
                });

                oneWay.setToolTipText(oneWayButton.getToolTipText());

                // Greyed on a page left out (OB-235, MT-528).
                if (session != null && session.getStore().getExcludedPages().contains(page))
                {
                    oneWay.setEnabled(false);
                    oneWay.setToolTipText(wrapped(I18n.t("autosetup.ui.infoPageLeftOutNothingToMeasure")));
                }

                bulk.add(oneWay);
            }

            if (nameAll != null)
            {
                javax.swing.JMenuItem nameEverythingItem =
                    item(I18n.t("autosetup.ui.btnNameEverything"), () -> nameEverything());

                nameEverythingItem.setEnabled(nameAll.isEnabled());
                nameEverythingItem.setToolTipText(nameAll.getToolTipText());

                bulk.add(nameEverythingItem);
            }

            // Mass Assign Lengths, greyed on the walk's own count
            int piecesToMeasure = session == null ? 0 : session.stretchesNeedingALengthOn(page).size();
            int switchesToMeasure = session == null ? 0 : session.switchesNeedingALengthOn(page).size();

            // And the squares two roads share
            int crossingsToMeasure = session == null ? 0 : session.sharedSquaresNeedingALengthOn(page).size();

            boolean leftOut = session != null && session.getStore().getExcludedPages().contains(page);

            javax.swing.JMenuItem massAssign =
                item(I18n.t("autosetup.ui.menuMassAssignLengths"), () -> massAssignLengths());

            massAssign.setEnabled(piecesToMeasure + switchesToMeasure + crossingsToMeasure > 0);

            // A page left out says so: its 0 would be vacuously true.
            massAssign.setToolTipText(wrapped(leftOut
                ? I18n.t("autosetup.ui.infoPageLeftOutNothingToMeasure")
                : piecesToMeasure + switchesToMeasure + crossingsToMeasure > 0
                    ? I18n.f("autosetup.ui.tooltipMassAssignLengths", piecesToMeasure, switchesToMeasure,
                        crossingsToMeasure)
                    : I18n.t("autosetup.ui.infoNothingToMeasure")));

            bulk.add(massAssign);

            // Mass Assign Station Max Train Lengths, greyed on the walk's own count
            int stationsToLimit = session == null ? 0 : session.stationsWithoutAMaximumOn(page).size();

            javax.swing.JMenuItem massAssignMaximum =
                item(I18n.t("autosetup.ui.menuMassAssignMaxTrainLengths"), () -> massAssignMaxTrainLengths());

            massAssignMaximum.setEnabled(stationsToLimit > 0);

            massAssignMaximum.setToolTipText(wrapped(leftOut
                ? I18n.t("autosetup.ui.infoPageLeftOutNothingToMeasure")
                : stationsToLimit > 0
                    ? I18n.f("autosetup.ui.tooltipMassAssignMaxTrainLengths", stationsToLimit)
                    : I18n.t("autosetup.ui.infoEveryStationHasAMaximum")));

            bulk.add(massAssignMaximum);

            // Mass Assign Train Lengths (FR-094), not a page's question
            int trainsToMeasure = trainLengthDoor().trainsWithoutALength().size();

            // Never greyed, the count in its label (MT-533: *"This option should never be greyed out completely (show
            // the number of missing trains in parens)"*).
            javax.swing.JMenuItem massAssignTrains = item(
                I18n.f("autosetup.ui.menuMassAssignTrainLengthsCounted", trainsToMeasure), () -> massAssignTrainLengths());

            massAssignTrains.setName("massAssignTrainLengths");

            massAssignTrains.setToolTipText(wrapped(trainsToMeasure > 0
                ? I18n.f("autosetup.ui.tooltipMassAssignTrainLengths", trainsToMeasure)
                : trainLengthDoor().trainLengths().isEmpty() ? I18n.t("autosetup.ui.infoNoTrainToMeasure")
                : I18n.t("autosetup.ui.infoEveryTrainHasALength")));

            bulk.add(massAssignTrains);

            bulk.addSeparator();
        }

        // The set the bulk home door writes
        int placed = session == null ? 0 : session.placementsAutonomyWillWrite().size();
        int homed = session == null ? 0 : session.tilesWithAHome().size();

        javax.swing.JMenuItem clearLocs = item(
            I18n.f("autolayout.ui.menuClearLocomotives", placed), () -> clearAllPlacements());

        clearLocs.setEnabled(placed > 0);

        // The dialog's sentence in short (OB-194, FR-112), from the same builder.
        clearLocs.setToolTipText(wrapped(placed > 0
            ? clearLocomotivesTip()
            : I18n.t("autosetup.ui.infoNoLocomotivesToClear")));

        bulk.add(clearLocs);

        javax.swing.JMenuItem clearHomesItem = item(
            I18n.f("autolayout.ui.menuClearAllHomeLocomotives", homed), () -> clearAllHomes());

        clearHomesItem.setEnabled(homed > 0);
        clearHomesItem.setToolTipText(wrapped(homed > 0
            ? I18n.t("autosetup.ui.tipClearAllHomeLocomotives")
            : I18n.t("autosetup.ui.infoNoHomesToClear")));

        bulk.add(clearHomesItem);

        // Clear All Track Lengths (FR-069), built like the two above
        int measured = session == null ? 0 : session.tilesWithALength().size();

        javax.swing.JMenuItem clearLengths = item(
            I18n.f("autolayout.ui.menuClearAllTrackLengths", measured),
            () -> clearAllTileLengths());

        clearLengths.setEnabled(measured > 0);
        clearLengths.setToolTipText(wrapped(measured > 0
            ? bulkClearWarning("autosetup.ui.tipClearAllTrackLengths", measured)
            : I18n.t("autosetup.ui.infoNoTrackLengthsToClear")));

        bulk.add(clearLengths);

        // Clear All Max Train Lengths, built like the clears above
        int limited = session == null ? 0 : session.tilesWithAMaxTrainLength().size();

        javax.swing.JMenuItem clearMaxima = item(
            I18n.f("autolayout.ui.menuClearAllMaxTrainLengths", limited), () -> clearAllMaxTrainLengths());

        clearMaxima.setEnabled(limited > 0);
        clearMaxima.setToolTipText(wrapped(limited > 0
            ? bulkClearWarning("autosetup.ui.tipClearAllMaxTrainLengths", limited)
            : I18n.t("autosetup.ui.infoNoMaxTrainLengthsToClear")));

        bulk.add(clearMaxima);

        // Allow Every Path (FR-108), built like the clears above
        int oneWay = session == null ? 0 : session.routesNotOpenBothWays().size();

        javax.swing.JMenuItem allowAll = item(I18n.f("autolayout.ui.menuAllowEveryPath", oneWay), () -> allowEveryPath());

        allowAll.setEnabled(oneWay > 0);
        allowAll.setToolTipText(wrapped(oneWay > 0
            ? bulkClearWarning("autosetup.ui.tipAllowEveryPath", oneWay)
            : I18n.t("autosetup.ui.infoNothingOneWay")));

        bulk.add(allowAll);

        // Home Every Train Where It Stands (FR-075): it adds, so its confirmation says what it overwrites.  Counted off
        // `trainsWhereTheyStand`, as the door is.
        int standing = session == null ? 0 : session.trainsWhereTheyStand().size();

        javax.swing.JMenuItem homeHere = item(
            I18n.f("autolayout.ui.menuHomeEveryTrainWhereItStands", standing),
            () -> homeEveryPlacedTrain());

        homeHere.setEnabled(standing > 0);
        homeHere.setToolTipText(wrapped(standing > 0
            ? I18n.f("autosetup.ui.tipHomeEveryTrainWhereItStands", standing)
            : I18n.t("autosetup.ui.infoNoLocomotivesToHome")));

        bulk.add(homeHere);

        // Place All at Their Homes (FR-115), counted off `homesToPlace`, as the door is.
        int homes = session == null ? 0 : session.homesToPlace().size();

        javax.swing.JMenuItem placeHome = item(
            I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome", homes), () -> placeEveryTrainAtHome());

        // On the track diagram, refused while anything runs (FR-129)
        String placeRefused = menuOnly && parentWindow() != null
            ? parentWindow().whyTheTrainsCannotBePlacedAtTheirHomes()
            : homes > 0 ? null : I18n.t("autosetup.ui.infoNoHomesToPlace");

        placeHome.setEnabled(placeRefused == null);
        placeHome.setToolTipText(wrapped(placeRefused == null
            ? I18n.f("autosetup.ui.tipPlaceEveryTrainAtHome", homes)
            : placeRefused));

        bulk.add(placeHome);

        // Activate and Deactivate Every Train (FR-126), counted off what each walks: every paused train; every going
        // train standing.  Nothing to confirm, as each undoes the other.
        int pausedTrains = pausedTrains().size();
        int goingTrains = goingTrainsStanding().size();

        javax.swing.JMenuItem activateAll = item(
            I18n.f("autosetup.ui.menuActivateAllTrains", pausedTrains), () -> setEveryTrainActive(true));

        activateAll.setEnabled(pausedTrains > 0);
        activateAll.setToolTipText(wrapped(pausedTrains > 0
            ? I18n.f("autosetup.ui.tipActivateAllTrains", pausedTrains)
            : I18n.t("autosetup.ui.infoNoTrainsToActivate")));

        bulk.add(activateAll);

        javax.swing.JMenuItem deactivateAll = item(
            I18n.f("autosetup.ui.menuDeactivateAllTrains", goingTrains), () -> setEveryTrainActive(false));

        deactivateAll.setEnabled(goingTrains > 0);
        deactivateAll.setToolTipText(wrapped(goingTrains > 0
            ? I18n.f("autosetup.ui.tipDeactivateAllTrains", goingTrains)
            : I18n.t("autosetup.ui.infoNoTrainsToDeactivate")));

        bulk.add(deactivateAll);

        return bulk;
    }

    /**
     * The trains standing on the layout, by name, once each (FR-126), as `trainsWhereTheyStand` has them.
     * @return the names, in no particular order
     */
    private java.util.List<String> trainsStanding()
    {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();

        if (session != null)
        {
            for (String name : session.trainsWhereTheyStand().values())
            {
                if (name != null && !name.trim().isEmpty()) names.add(name);
            }
        }

        return new java.util.ArrayList<>(names);
    }

    /** The model's train of this name, or null (FR-126). */
    private org.traincontrol.base.Locomotive modelTrain(String name)
    {
        TrainControlUI window = parentWindow();

        return window == null || window.getModel() == null ? null : window.getModel().getLocByName(name);
    }

    /**
     * Whether a train is paused (FR-126): the train's own flag where the model has it, the later word; else the list.
     */
    private boolean isPaused(String name, java.util.Set<String> listed)
    {
        org.traincontrol.base.Locomotive train = modelTrain(name);

        return train != null ? train.isAutonomyPaused() : listed.contains(name);
    }

    /** Every paused train, on the layout or not (FR-126): what Activate All Trains sets going. */
    private java.util.List<String> pausedTrains()
    {
        java.util.Set<String> names = new java.util.TreeSet<>();

        TrainControlUI window = parentWindow();

        if (window != null && window.getModel() != null)
        {
            for (org.traincontrol.base.Locomotive train : window.getModel().getLocomotives())
            {
                if (train != null && train.getName() != null && train.isAutonomyPaused()) names.add(train.getName());
            }
        }

        if (session != null)
        {
            for (String name : session.getPausedLocomotives())
            {
                if (modelTrain(name) == null) names.add(name);
            }
        }

        return new java.util.ArrayList<>(names);
    }

    /** Every going train standing on the layout (FR-126): what Deactivate All Trains pauses. */
    private java.util.List<String> goingTrainsStanding()
    {
        java.util.Set<String> listed = session == null ? new java.util.HashSet<String>() : session.getPausedLocomotives();

        java.util.List<String> going = new java.util.ArrayList<>();

        for (String name : trainsStanding())
        {
            if (!isPaused(name, listed)) going.add(name);
        }

        return going;
    }

    /**
     * Activates every paused train, or pauses every going one standing, and says how many changed (FR-126): through the
     * window's door per train where it can, else the setup alone; then the railway is told.  The emptiness check is the
     * guard behind the greying.
     * @param active true to activate every paused train, false to deactivate every train standing that is going
     */
    private void setEveryTrainActive(boolean active)
    {
        java.util.List<String> covered = active ? pausedTrains() : goingTrainsStanding();

        if (covered.isEmpty())
        {
            say(hint, I18n.t(active ? "autosetup.ui.infoNoTrainsToActivate" : "autosetup.ui.infoNoTrainsToDeactivate"));

            return;
        }

        java.util.Set<String> names = new java.util.LinkedHashSet<>(covered);

        // Trains already as asked go through the door too, so an outrun list entry does not stand: the rebuild would
        // pause them again (OB-343).
        if (active) names.addAll(session.getPausedLocomotives());
        else names.addAll(trainsStanding());

        TrainControlUI window = parentWindow();

        java.util.List<org.traincontrol.base.Locomotive> trains = new java.util.ArrayList<>();
        java.util.List<String> unknown = new java.util.ArrayList<>();

        for (String name : names)
        {
            org.traincontrol.base.Locomotive train = modelTrain(name);

            if (train != null) trains.add(train);
            else unknown.add(name);
        }

        try
        {
            if (window != null) window.setLocomotivesActive(session, trains, active);

            if (!unknown.isEmpty()) session.setLocomotivesPaused(unknown, !active);
        }
        catch (java.io.IOException cannotSave)
        {
            throw new IllegalStateException(cannotSave.getMessage(), cannotSave);
        }

        say(hint, active ? I18n.f("autosetup.ui.infoTrainsActivated", covered.size())
            : I18n.f("autosetup.ui.infoTrainsDeactivated", covered.size()));

        setupChanged();
    }

    /**
     * Makes every placed train's square its home, after confirming (FR-075).  The emptiness check is the guard behind
     * the greying; the confirmation names what will be overwritten (`AutonomySession.setHome` holds the rule).
     */
    private void homeEveryPlacedTrain()
    {
        // Counted off the set the door walks, excluded pages skipped
        java.util.List<TileKey> placed =
            new java.util.ArrayList<>(session.trainsWhereTheyStand().keySet());

        if (placed.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoNoLocomotivesToHome"));

            return;
        }

        if (JOptionPane.showOptionDialog(owner(),
            I18n.f("autolayout.ui.confirmHomeEveryTrainWhereItStands", placed.size()),
            I18n.f("autolayout.ui.menuHomeEveryTrainWhereItStands", placed.size()),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        int assigned = session.homeEveryPlacedTrain();

        say(hint, I18n.f("autosetup.ui.infoTrainsHomed", assigned));

        refresh();

        // And the running layout, or Return Home offers the arrangement just replaced (as `clearAllHomes`).
        setupChanged();
    }

    /**
     * Puts every homed train on its home and takes the rest off, after confirming, in the setup only (FR-115).  The
     * rebuild is told every train touched, or it puts the missed ones back.
     */
    private void placeEveryTrainAtHome()
    {
        // On the track diagram, as Return Home asks (FR-129): no banner, so said in the log
        final TrainControlUI window = menuOnly ? parentWindow() : null;

        if (window != null)
        {
            placeEveryTrainAtHome("autolayout.ui.confirmPlaceEveryTrainAtHomeByHand",
                window::whyTheTrainsCannotBePlacedAtTheirHomes, said -> window.getModel().log(said));

            return;
        }

        placeEveryTrainAtHome("autolayout.ui.confirmPlaceEveryTrainAtHome", () -> null, text -> say(hint, text));
    }

    /**
     * The door behind Place All Trains at Their Homes, for this menu and for Return Home with no editor open (FR-129);
     * the caller's question is asked again after the confirmation.
     * @param confirmKey the bundle key of the confirmation text, which takes the homed count and the homeless count
     * @param refusedBecause why the placement is not allowed now, or null; asked after the confirmation
     * @param saying where the outcome is said
     */
    void placeEveryTrainAtHome(String confirmKey, java.util.function.Supplier<String> refusedBecause,
        java.util.function.Consumer<String> saying)
    {
        java.util.Map<TileKey, String> homes = session.homesToPlace();

        if (homes.isEmpty())
        {
            saying.accept(I18n.t("autosetup.ui.infoNoHomesToPlace"));
            return;
        }

        java.util.Set<String> homed = new java.util.HashSet<>(homes.values());

        int homeless = 0;

        for (String standing : new java.util.LinkedHashSet<>(session.trainsWhereTheyStand().values()))
        {
            if (standing != null && !homed.contains(standing)) homeless++;
        }

        if (JOptionPane.showOptionDialog(owner(),
            I18n.f(confirmKey, homes.size(), homeless),
            I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome", homes.size()),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        String refused = refusedBecause.get();

        if (refused != null)
        {
            JOptionPane.showMessageDialog(owner(), refused);
            return;
        }

        java.util.Set<String> touched = session.placeEveryTrainAtHome(homeCopyFacings());

        saying.accept(I18n.f("autosetup.ui.infoTrainsPlacedAtHome", homes.size(), homeless));

        placementChanged(touched);
    }

    /**
     * For each home square, the facing of the copy the running layout made its home, for homes recorded without one
     * (FR-115); off the Points, taking no monitor.
     * @return home square to facing
     */
    private java.util.Map<TileKey, org.traincontrol.automationui.TilePorts.Side> homeCopyFacings()
    {
        java.util.Map<TileKey, org.traincontrol.automationui.TilePorts.Side> out = new java.util.HashMap<>();

        org.traincontrol.automation.Layout layout = layoutSource == null ? null : layoutSource.get();

        if (layout == null) return out;

        org.traincontrol.automationui.StationIndex index = session.getStationIndex();

        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.getHomeLoc() == null) continue;

            TileKey square = index.squareOf(point);

            if (square == null) continue;

            org.traincontrol.automationui.TilePorts.Side facing = index.facingsAt(square).get(point.getName());

            if (facing != null) out.put(square, facing);
        }

        return out;
    }

    /** For tests: the Bulk Tools menu as the right-click menu builds it, through `bulkTools()` itself (OB-194). */
    public javax.swing.JMenu buildBulkMenuForTest()
    {
        return bulkTools();
    }

    TileKey menuTarget;

    /** What the menu calls this point: its name, else what it is. */
    String pointTitle(TileKey tile)
    {
        String name = session.getStore().getPointName(tile);

        return name == null || name.trim().isEmpty() ? describeTile(tile) : name.trim();
    }

    /** A bold, disabled heading at the top of a menu. */
    void title(javax.swing.JMenu menu, String text)
    {
        title(menu.getPopupMenu(), text);
    }

    void title(javax.swing.JPopupMenu menu, String text)
    {
        javax.swing.JMenuItem titleItem = new javax.swing.JMenuItem(text);
        titleItem.setEnabled(false);
        titleItem.setFont(titleItem.getFont().deriveFont(java.awt.Font.BOLD));

        menu.add(titleItem);
        menu.addSeparator();
    }

    /**
     * Whether a submenu holds anything but its own heading (OB-032), which title() adds.
     * @return true when there is something under the heading
     */
    boolean hasItemsBesidesTitle(javax.swing.JMenu menu)
    {
        for (int i = 0; i < menu.getItemCount(); i++)
        {
            javax.swing.JMenuItem child = menu.getItem(i);

            // A separator is null from getItem, and no content
            if (child != null && child.isEnabled()) return true;
        }

        return false;
    }

    /**
     * Whether a setup edit is refused because trains are running, having said so (Adam: *"There should be no setup edit
     * possible during a run"*): a menu opened at rest can be clicked after a run begins (OB-047).
     * @return true where the edit is refused
     */
    private boolean refusedWhileRunning()
    {
        TrainControlUI window = parentWindow();

        // A route too
        if (window == null || !window.aRunHoldsTheSetup()) return false;

        JOptionPane.showMessageDialog(owner(), window.whyTheSetupIsHeld("autolayout.errorCannotEditWhileRunning"));

        return true;
    }

    /**
     * The same refusal for a diagram gesture or key, said on the message strip: an editor opened at rest stays open
     * when a route begins.
     * @return true where the gesture is refused
     */
    boolean heldByARun()
    {
        TrainControlUI window = parentWindow();

        if (window == null || !window.aRunHoldsTheSetup()) return false;

        say(hint, window.whyTheSetupIsHeld("autolayout.errorCannotEditWhileRunning"));

        return true;
    }

    /** A menu item that runs something, redraws, and flashes the square changed, which the menu was covering. */
    javax.swing.JMenuItem item(String text, final Runnable action)
    {
        javax.swing.JMenuItem menuItem = new javax.swing.JMenuItem(text);

        menuItem.addActionListener(e ->
        {
            // Not while trains run
            if (refusedWhileRunning()) return;

            try
            {
                action.run();
            }
            catch (RuntimeException ex)
            {
                // The class too: a NullPointerException has no message.
                String said = ex.getMessage() == null || ex.getMessage().trim().isEmpty()
                    ? ex.getClass().getSimpleName() : ex.getMessage();

                JOptionPane.showMessageDialog(owner(), I18n.f("error.generic", said));

                // And into the log, with the stack
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
     * How wide to wrap so the longest sentence takes two lines, at least `atLeast` (MT-479): half its width plus the
     * longest word, as lines break between words.
     * @param metrics the font the text is drawn in
     * @param atLeast the narrowest the text may be - the width of what sits under it
     * @param sentences the sentences
     * @return the width in pixels
     */
    static int twoLineWidth(java.awt.FontMetrics metrics, int atLeast, String... sentences)
    {
        int widest = 0;
        int longestWord = 0;

        for (String sentence : sentences)
        {
            widest = Math.max(widest, metrics.stringWidth(sentence));

            for (String word : sentence.split("\\s+")) longestWord = Math.max(longestWord, metrics.stringWidth(word));
        }

        return Math.max(atLeast, widest / 2 + longestWord);
    }

    /**
     * Text wrapped at a width, as `wrapped` wraps a tooltip.
     * @param text the text
     * @param width the width in pixels, or 0 or less to leave it on one line
     * @return the text, wrapped
     */
    static String wrappedAt(String text, int width)
    {
        if (text == null || width <= 0) return text;

        return "<html><body style='width: " + width + "px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            + "</body></html>";
    }

    /**
     * A tooltip that wraps instead of running off the screen, which Swing does with a long one.
     * @return the same text, wrapped
     */
    static String wrapped(String text)
    {
        if (text == null || text.trim().isEmpty()) return text;

        // Already markup: left as it is
        if (text.trim().toLowerCase().startsWith("<html")) return text;

        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

        // One line where it fits (MT-613), or a short reason sits in a wide box.
        if (fitsOnOneLine(text)) return "<html>" + escaped + "</html>";

        return "<html><body style='width: " + TOOLTIP_WIDTH + "px'>" + escaped + "</body></html>";
    }

    /**
     * A tooltip wrapped only where too wide for one line; "Control+A" stays plain text (FR-112).
     * @param text the tooltip, or null
     * @return the tooltip, wrapped if it needs to be
     */
    public static String wrappedIfLong(String text)
    {
        if (text == null || text.trim().toLowerCase().startsWith("<html") || fitsOnOneLine(text)) return text;

        return wrapped(text);
    }

    /**
     * Wraps every too-wide tooltip on a menu and its submenus (FR-112), for menus set where nobody wraps them.
     * @param menu a menu bar, a menu or a popup menu
     */
    public static void wrapTheTooltips(java.awt.Container menu)
    {
        java.awt.Component[] items = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component item : items)
        {
            if (item instanceof javax.swing.JComponent)
            {
                javax.swing.JComponent each = (javax.swing.JComponent) item;

                String tip = each.getToolTipText();

                if (tip != null) each.setToolTipText(wrappedIfLong(tip));
            }

            if (item instanceof javax.swing.JMenu) wrapTheTooltips((javax.swing.JMenu) item);
        }
    }

    /** The width a tooltip wraps at, in pixels. */
    private static final int TOOLTIP_WIDTH = 320;

    /**
     * TOOLTIP_WIDTH in measuring pixels: a CSS width is drawn at the screen's resolution, so 320 is 400 at 120 dpi
     * (OB-358).
     * @return the wrapping width, in pixels
     */
    private static int wrappingWidth()
    {
        int dpi = java.awt.GraphicsEnvironment.isHeadless() ? 96
            : java.awt.Toolkit.getDefaultToolkit().getScreenResolution();

        return dpi > 0 ? (int) Math.round(TOOLTIP_WIDTH * dpi / 96.0) : TOOLTIP_WIDTH;
    }

    /** Whether a tooltip's text fits one line of the wrapping width, in the tooltip font. */
    private static boolean fitsOnOneLine(String text)
    {
        if (text.indexOf('\n') >= 0) return false;

        java.awt.Font font = javax.swing.UIManager.getFont("ToolTip.font");

        if (font == null) font = new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 12);

        java.awt.Graphics2D scratch =
            new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();

        try
        {
            return scratch.getFontMetrics(font).stringWidth(text) <= wrappingWidth();
        }
        finally
        {
            scratch.dispose();
        }
    }

    /** @param tooltipKey the graph window's own tooltip for this setting */
    javax.swing.JCheckBoxMenuItem toggle(String text, String tooltipKey, boolean on,
        final java.util.function.Consumer<Boolean> action)
    {
        final javax.swing.JCheckBoxMenuItem menuItem = new javax.swing.JCheckBoxMenuItem(text, on);

        if (tooltipKey != null) menuItem.setToolTipText(wrapped(I18n.t(tooltipKey)));

        menuItem.addActionListener(e ->
        {
            // Not while trains run
            if (refusedWhileRunning()) return;

            // Guarded, as `item` is, so a throw cannot skip placementChanged and leave box and setup apart; redrawn
            // either way.
            try
            {
                action.accept(menuItem.isSelected());
            }
            catch (RuntimeException ex)
            {
                // Said as item() says it, class included.
                String said = ex.getMessage() == null || ex.getMessage().trim().isEmpty()
                    ? ex.getClass().getSimpleName() : ex.getMessage();

                JOptionPane.showMessageDialog(owner(), I18n.f("error.generic", said));

                say(hint, said);
            }

            // placementChanged, not refresh: these write the setup, some from the track diagram's menu, and an arrival
            // side decides how a square splits.
            placementChanged(null);

            flashMenuTarget();
        });

        return menuItem;
    }

    /**
     * Whether a station's name may be written on this square (OB-042, OB-044): anything but a switch or signal, whose
     * click does something.
     * @param component what is drawn on the square
     * @return whether to offer the caption items
     */
    boolean mayCarryACaption(LayoutDiagramComponent component)
    {
        if (component == null) return true;

        // Not everything isClickable() covers: a sensor is the platform road, the commonest place for a name.
        return !component.isSwitch() && !component.isSignal();
    }

    /**
     * Whether a dragged station label may land here (FR-035): asked while dragging and on release, so green is what is
     * accepted.
     * @param from the square the label is on now
     * @param to the square under the pointer
     * @param onTarget what is drawn there
     * @return whether the drop would work
     */
    public boolean canDropCaption(TileKey from, TileKey to, LayoutDiagramComponent onTarget)
    {
        return refuseCaptionDrop(from, to, onTarget) == null;
    }

    /**
     * Why a dragged label may not land here, or null when it may: one method, so refusal and reason cannot drift.  ""
     * is a silent no, for a drop back where it came from.
     * @return the message, "" for a silent no, or null for yes
     */
    private String refuseCaptionDrop(TileKey from, TileKey to, LayoutDiagramComponent onTarget)
    {
        if (session == null || from == null) return "";

        if (session.getCaptionTarget(from) == null) return "";

        // Dropped outside the diagram: a slip.
        if (to == null) return I18n.t("autosetup.ui.errorDropOffDiagram");

        if (to.equals(from)) return "";

        if (from.getPage() == null || !from.getPage().equals(to.getPage()))
        {
            return I18n.t("autosetup.ui.errorDropOffDiagram");
        }

        // Outside what the running diagram draws
        if (!session.isInTheDrawnArea(to)) return I18n.t("autosetup.ui.captionOutsideTheDrawnArea");

        // On a control, in the way of its click.
        if (!mayCarryACaption(onTarget)) return I18n.t("autosetup.ui.errorDropOnControl");

        // Refused, not replaced: the name there would vanish without trace.
        if (session.getCaptionTarget(to) != null)
        {
            return I18n.f("autosetup.ui.errorDropOccupied", describeTile(to));
        }

        return null;
    }

    /**
     * Moves a station label (FR-035): the caption command run twice, so no stored position needs carrying (Adam: *"it
     * is equivalent to just adding the label at the destination when the mouse is released, since the command is
     * wired."*).  Every refusal is spoken.
     * @param from the square the label is on now
     * @param to the square it was dropped on
     * @param onTarget what is drawn on that square
     * @return whether the label moved
     */
    public boolean moveCaption(TileKey from, TileKey to, LayoutDiagramComponent onTarget)
    {
        if (heldByARun()) return false;

        String refused = refuseCaptionDrop(from, to, onTarget);

        if (refused != null)
        {
            if (!refused.isEmpty()) say(hint, refused);

            return false;
        }

        TileKey station = session.getCaptionTarget(from);

        // One move through the session: two calls could leave the station uncaptioned between them.
        if (!session.moveCaption(from, to)) return false;

        if (onDiagramChanged != null) onDiagramChanged.run();

        say(hint, I18n.f("autosetup.ui.setStationLabel", describeTile(station)));

        refresh();

        // The running layout draws the captions.
        setupChanged();

        return true;
    }

    /** The station last clicked or right-clicked, so a label offered next defaults to it. */
    private TileKey lastStationTouched;

    /**
     * The station nearest this square on this page (FR-034; Adam: *"prefill it with the label of the nearest station by
     * tile distance."*); ties go to the store's order.
     * @param from the square the label would go on
     * @return the nearest station on the same page, or null when there is none
     */
    private TileKey nearestStation(TileKey from)
    {
        if (from == null || session == null || session.getReducer() == null) return null;

        java.util.List<TileKey> stations = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.GraphReducer.ReducedPoint point
            : session.getReducer().getPoints().values())
        {
            if (point.isStation() && point.getName() != null && point.getTile() != null)
            {
                stations.add(point.getTile());
            }
        }

        return nearestOf(from, stations);
    }

    /**
     * The nearest of a set of squares on the same page (FR-034), public and session-free so the rule can be tested
     * alone.
     * @return the nearest on the same page, or null when there is none
     */
    public static TileKey nearestOf(TileKey from, java.util.Collection<TileKey> stations)
    {
        if (from == null || from.getPage() == null || stations == null) return null;

        TileKey best = null;
        long closest = Long.MAX_VALUE;

        for (TileKey at : stations)
        {
            if (at == null || !from.getPage().equals(at.getPage())) continue;

            long dx = at.getX() - from.getX();
            long dy = at.getY() - from.getY();

            long away = dx * dx + dy * dy;

            if (away < closest)
            {
                closest = away;
                best = at;
            }
        }

        return best;
    }

    /** Notes a square if it is a station, so the next label defaults to it. */
    void rememberIfStation(TileKey tile)
    {
        if (tile != null && session != null && session.getStore().isStation(tile))
        {
            lastStationTouched = tile;
        }
    }

    /** The answer to the take-another-station's-name question for tests, or null to ask. */
    private static volatile Boolean takeAnotherNameAnswerForTests;

    /** @param answer true for Yes, false for No, null to ask again */
    public static void answerTakeAnotherNameForTests(Boolean answer)
    {
        takeAnotherNameAnswerForTests = answer;
    }

    /** The question last put about taking another station's only name (OB-341). */
    private static volatile String takeAnotherNameQuestion;

    /** @return the words of the last such question, for a test (OB-341) */
    public static String takeAnotherNameQuestionForTests()
    {
        return takeAnotherNameQuestion;
    }

    /**
     * Whether a station square may show its own name where it shows ANOTHER station's only caption on this page
     * (OB-341): asked, naming both, unless nothing is lost.  The drop refuses the same loss; this menu item asks.
     * @return whether to go on
     */
    private boolean mayTakeAnotherStationsOnlyName(TileKey tile)
    {
        TileKey other = session.getCaptionTarget(tile);

        if (other == null || other.equals(tile) || !session.getStore().isStation(other)) return true;

        java.util.Set<TileKey> elsewhere = new java.util.LinkedHashSet<>(session.captionsFor(other));

        elsewhere.remove(tile);

        boolean shownOnOtherPages = !elsewhere.isEmpty();

        elsewhere.removeIf(where -> !tile.getPage().equals(where.getPage()));

        if (!elsewhere.isEmpty()) return true;

        String question = I18n.f(shownOnOtherPages ? "autosetup.ui.confirmTakeAnotherStationsName"
            : "autosetup.ui.confirmTakeAnotherStationsOnlyName", describeTile(other), describeTile(tile));

        takeAnotherNameQuestion = question;

        Boolean given = takeAnotherNameAnswerForTests;

        if (given != null) return given;

        // No is the default: Yes takes a name off the diagram.  0 is Yes.
        return JOptionPane.showOptionDialog(owner(), question,
            I18n.t("autosetup.ui.titleStationLabel"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) == 0;
    }

    void promptStationLabel(TileKey tile, LayoutDiagramComponent component)
    {
        // The user's own text here is replaced only if they say so, named in the question.
        String standing = component == null || component.getLabel() == null
            ? "" : component.getLabel().trim();

        if (!standing.isEmpty() && session.getCaptionTarget(tile) == null)
        {
            // showOptionDialog for TrainControl's button text (showConfirmDialog's follow the JVM locale); 0 is the
            // first
            int answer = JOptionPane.showOptionDialog(owner(),
                I18n.f("autosetup.ui.confirmReplaceText", standing),
                I18n.t("autosetup.ui.titleStationLabel"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]);

            if (answer != 0) return;
        }

        // A station labelling itself has nothing to ask.
        if (session.getStore().isStation(tile))
        {
            if (!mayTakeAnotherStationsOnlyName(tile)) return;

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

        // What the square shows, else the station last looked at, not the first name alphabetically.
        TileKey showing = session.getCaptionTarget(tile);

        // The station just clicked, for the next square only (FR-034): consumed here either way.
        if (showing == null)
        {
            showing = lastStationTouched;

            lastStationTouched = null;
        }

        if (showing == null) showing = nearestStation(tile);

        // By square, not name: the list's reduced names differ per way in from the authored one.
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
     * Shows a station's name on a square, or stops showing it, and says so; the caption is the setup's, not the layout
     * file's.
     * @param tile the square the text goes on
     * @param station the sensor it is about, or null to clear the square
     */
    void applyCaption(TileKey tile, TileKey station)
    {
        session.setCaption(tile, station);

        // Rebuilt, not repainted: the caption is part of the tile art.
        if (onDiagramChanged != null) onDiagramChanged.run();

        say(hint, station == null ? I18n.t("autosetup.ui.clearedStationLabel")
            : I18n.f("autosetup.ui.setStationLabel", describeTile(station)));

        refresh();

        flashMenuTarget();

        // The running layout draws the captions.
        setupChanged();
    }

    /**
     * Which entry of a name list stands for a square: a split station has a name per way in, any will do.
     * @param tile the square to find, or null
     * @param names what the dropdown is offering
     * @return the entry to select, or null where the square is not in the list
     */
    private String nameInListFor(TileKey tile, java.util.List<String> names)
    {
        if (tile == null) return null;

        // The stored name first, exact for an unsplit station.
        String authored = session.getStore().getPointName(tile);

        if (authored != null && names.contains(authored)) return authored;

        for (String name : names)
        {
            if (tile.equals(session.tileForPointName(name))) return name;
        }

        return null;
    }

    /** The square of the station with this authored name, turning a chooser's answer back into a square. */
    private TileKey stationTileNamed(String name)
    {
        if (name == null || session.getReducer() == null) return null;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (name.equals(session.getStore().getPointName(tile))) return tile;
        }

        return null;
    }

    String stationSummary(TileKey tile, boolean isStation)
    {
        // Yes or no: may a train stop here.  Out of service reads no.
        boolean stops = isStation
            && !Boolean.FALSE.equals(session.getPointProperty(tile, "active"));

        // A berth (never chosen by autonomy) is marked with a star.
        String star = stops && !session.isAutoDestination(tile) ? " *" : "";

        if (!stops) return I18n.t("autosetup.ui.stationNo") + star;

        // And which kind of turn, must or may: the two settings people confuse.
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
     * Writes a station's name onto the diagram, rebuilding the grid if it went anywhere.  Where there is no room the
     * not-shown check reports it; a dialog would interrupt the click that made the station.
     */
    private void placeLabelFor(TileKey tile)
    {
        String why = session.placeCaption(tile);

        // Says why nothing happened.
        if (why != null)
        {
            say(hint, I18n.f(why, describeTile(tile)));
            return;
        }

        if (onDiagramChanged != null) onDiagramChanged.run();
    }

    /**
     * The window this panel's dialogs belong to (MT-182): `owner()` itself where it is one, as a top-level window has
     * no ancestor.
     * @return the window, or null when this panel is in none
     */
    java.awt.Window ownerWindow()
    {
        java.awt.Component anchor = owner();

        if (anchor instanceof java.awt.Window) return (java.awt.Window) anchor;

        return javax.swing.SwingUtilities.getWindowAncestor(anchor);
    }

    /** The window this panel lives in, for parenting dialogs: JOptionPane centres over its component, a side strip. */
    java.awt.Component owner()
    {
        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);

        if (window != null) return window;

        // A menu-only panel is in no window, and a dialog parented on it can land hidden behind the main window.
        return dialogOwner == null ? this : dialogOwner;
    }

    /** Where to parent dialogs when this panel is not in a window of its own. */
    private java.awt.Component dialogOwner;

    public void setDialogOwner(java.awt.Component dialogOwner)
    {
        this.dialogOwner = dialogOwner;
    }

    /**
     * Whether this panel only builds menus, with no diagram: items wanting a second click open the editor at that
     * square instead.
     */
    boolean menuOnly;

    /**
     * Where to find the running layout, when there is one.
     *
     * A supplier rather than a reference, for the reason the session keeps one: `parseAuto` replaces
     * the layout object, and a field captured once goes stale at the first rebuild.
     */
    java.util.function.Supplier<org.traincontrol.automation.Layout> runningLayout;

    /**
     * Tells this panel where the running layout is.
     *
     * @param source how to fetch it, or null when there is none
     */
    public void setRunningLayoutSource(
        java.util.function.Supplier<org.traincontrol.automation.Layout> source)
    {
        this.runningLayout = source;
    }
    /**
     * Where the tail of the train standing here lies - the "arrived from" side.
     *
     * Adam: **"expose this property in the right-click menu advanced settings, both for the autonomy editor and track
     * diagram viewer."**
     *
     * Built once, here, for both surfaces (`TrainControlUI.buildAutonomyTileMenu`).
     *
     * The sides offered are the railway's (`ArrivalSidePrompt.sidesOf`).
     *
     * Null when no train is standing here.
     *
     * @param target the square
     * @return the menu, or null when there is nothing to offer
     */
    public javax.swing.JMenu buildArrivedFromMenu(final TileKey target)
    {
        return tileMenus.buildArrivedFromMenu(target);
    }

    /**
     * The "{loc} Is Facing..." submenu, or null unless a locomotive stands there, for the track diagram's menu; greyed
     * with the reason where no facing can be chosen (OB-347).
     * @param target the square
     * @return the submenu, or null
     */
    public javax.swing.JMenu buildFacingMenu(final TileKey target)
    {
        return tileMenus.buildFacingMenu(target);
    }

    /**
     * Writes the road the tail lies along, to the setup and to the train on the railway.
     * @param target the square the train stands on
     * @param road the road, or null for Not Known
     */
    void recordTailRoad(TileKey target, java.util.List<org.traincontrol.automation.Edge> road)
    {
        String names = org.traincontrol.automation.Layout.namesOfRoad(road);

        session.setArrivedAlong(target, names);

        org.traincontrol.automation.Layout now = runningLayout == null ? null : runningLayout.get();

        if (now != null)
        {
            org.traincontrol.automation.Point on = pointOnTheLayout(now, target);

            if (on != null) on.setArrivedAlong(names == null ? null : now.roadNamed(names));
        }

        setupChanged();
    }

    /**
     * Hands the next click to the tail of the train at `target`: the sensors it can have crossed are outlined, a click
     * on one records its road, elsewhere it says so and stays armed.  Two roads to one sensor are then offered as a
     * list.
     * @param target the square the train stands on
     */
    void armTailPick(TileKey target)
    {
        if (needsTheGrid(target)) return;

        org.traincontrol.automation.Layout running = runningLayout == null ? null : runningLayout.get();

        if (running == null || session.getStationIndex() == null) return;

        org.traincontrol.automation.Point point = pointOnTheLayout(running, target);

        if (point == null || point.getCurrentLocomotive() == null) return;

        String side = point.getArrivedFrom() != null ? point.getArrivedFrom() : session.getArrivedFrom(target);

        tailPicks.clear();

        for (TailCrossedPrompt.Choice choice : TailCrossedPrompt.choicesFor(running, point, side,
            point.getCurrentLocomotive().getTrainLength(), session::baseNameOf))
        {
            TileKey square = session.getStationIndex().squareOf(choice.getFarthest());

            if (square == null) continue;

            tailPicks.computeIfAbsent(square, k -> new java.util.ArrayList<>()).add(choice);
        }

        if (tailPicks.isEmpty()) return;

        tailFor = target;

        highlightedSignals.clear();
        highlightedSignals.addAll(tailPicks.keySet());

        waitFor(I18n.f("autosetup.ui.promptClickTailCrossed", describeTile(target)));

        refresh();
    }

    private TileKey tailFor;

    /** The sensors that click may land on, with the road each means. */
    private final java.util.Map<TileKey, java.util.List<TailCrossedPrompt.Choice>> tailPicks =
        new java.util.LinkedHashMap<>();

    /** For tests: the square whose tail a click is awaited for, or null. */
    public TileKey tailPickFor()
    {
        return tailFor;
    }

    /** For tests: the squares outlined for that click, in order found. */
    public java.util.Set<TileKey> tailPickSquares()
    {
        return java.util.Collections.unmodifiableSet(tailPicks.keySet());
    }

    /**
     * Goes to a link's other end: on this page a scroll and flash, else the jump hook reopens the window there.  The
     * deep menu always jumps, having no page.
     */
    void goToLink(TileKey partner)
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

    String linkLabel(TileKey tile)
    {
        if (tile == null) return "";

        String named = session.getStore().getLinkName(tile);

        return named == null || named.trim().isEmpty() ? tile.toString() : named;
    }

    /**
     * Leaving the page for a link's other end: the user's choice, so unsaved work is asked about (unlike onJumpToPage).
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
     * How many things must be fixed, in words agreeing with the number.
     * @param errors how many
     * @return the line under the findings
     */
    public static String blockingCountText(int errors)
    {
        return errors == 1 ? I18n.t("autosetup.ui.labelBlockingCountOne") : I18n.f("autosetup.ui.labelBlockingCount", errors);
    }

    /** @return whether this had to be handed to the editor, and so must not be started here */
    boolean needsTheGrid(TileKey tile)
    {
        if (!menuOnly) return false;

        if (onJumpToPage != null) onJumpToPage.accept(tile);

        return true;
    }

    /** One of the three exclusive answers to "what may a train do here", as a radio item. */
    javax.swing.JMenuItem radio(javax.swing.ButtonGroup group, String text, String tooltipKey,
        boolean on, final Runnable action)
    {
        javax.swing.JRadioButtonMenuItem menuItem = new javax.swing.JRadioButtonMenuItem(text, on);

        if (tooltipKey != null) menuItem.setToolTipText(wrapped(I18n.t(tooltipKey)));

        group.add(menuItem);

        menuItem.addActionListener(e ->
        {
            if (refusedWhileRunning()) return;

            action.run();

            // placementChanged, not refresh: the radios write the setup, and the track diagram's menu needs the
            // rebuild.
            placementChanged(null);

            flashMenuTarget();
        });

        return menuItem;
    }

    /** Applies the turning answer, clearing both flags and writing only what is meant, so "may" and "must" agree. */
    void setTurning(TileKey tile, boolean may, boolean must)
    {
        session.setPointFlag(tile, AutonomyBuilder.CAN_REVERSE, may && !must);
        session.setPointProperty(tile, AutonomyBuilder.MUST_REVERSE, must ? Boolean.TRUE : null);

        setupChanged();
    }

    /**
     * Opens or shuts one side of a station to arriving trains, stored as barred sides so a new side arrives open.
     * @param tile the station
     * @param side which way in
     * @param allowed whether trains may arrive that way
     */
    void setArrivalAllowed(TileKey tile, org.traincontrol.automationui.TilePorts.Side side,
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

        setupChanged();
    }

    /**
     * Applies one of the three answers.
     * @param station whether trains may stop here
     * @param open whether they may come here at all
     */
    void setUsage(TileKey tile, boolean station, boolean open)
    {
        // A nameless sensor is named as it becomes a station (FR-120); Cancel or blank changes nothing.
        if (station && !session.getStore().isStation(tile) && !hasAName(tile))
        {
            String name = askTheNewStationsName(tile);

            if (name == null) return;

            session.setPointName(tile, name);
        }

        setStation(tile, station);

        // Stored only when off, like every default
        session.setPointProperty(tile, "active", open ? null : Boolean.FALSE);

        setupChanged();
    }

    /** Asks for a new station's name (FR-120): message and text so far to the answer, null for Cancel. */
    private java.util.function.BiFunction<String, String, String> askForAName =
        (message, typed) -> JOptionPane.showInputDialog(owner(), wrapped(message), typed);

    private boolean hasAName(TileKey tile)
    {
        String name = session.getStore().getPointName(tile);

        return name != null && !name.trim().isEmpty();
    }

    /**
     * Asks a sensor becoming a station for its name (FR-120), again with the reason for an unusable or taken name.
     * Writes nothing.
     * @return the name, or null for Cancel or a blank name, which change nothing
     */
    private String askTheNewStationsName(TileKey tile)
    {
        String prompt = I18n.t("autosetup.ui.promptNewStationName");
        String message = prompt;
        String typed = "";

        while (true)
        {
            String answer = askForAName.apply(message, typed);

            if (answer == null) return null;

            // No quotes, as `promptName` takes them out
            String name = answer.replace("\"", "").trim();

            if (name.isEmpty()) return null;

            String refused = AutonomySession.whyNotAPointName(name);

            if (refused == null && session.isPointNameTaken(name, tile))
            {
                refused = I18n.f("autosetup.ui.errorPointNameTaken", name);
            }

            if (refused == null) return name;

            message = refused + "  " + prompt;
            typed = name;
        }
    }

    private void setStation(TileKey tile, boolean on)
    {
        session.setStation(tile, on);

        // A new station gets its name on the diagram at once, only where it has no caption (OB-310).
        if (on && !session.getLabelledStationTiles().contains(tile)) placeLabelFor(tile);

        // A demoted sensor keeps its "terminus" flag, read only of stations, and its active state.

        setupChanged();
    }

    void setAllBranches(TileKey tile, Direction direction)
    {
        session.setDirection(new LinkedHashSet<>(java.util.Arrays.asList(tile)), direction);

        // The arrows, not the diagram (MT-334): rebuilding every label for a restriction flashes.
        annotationsChanged();
    }

    /**
     * Why this number cannot be stored under this key, or null.  Only maximum train length has a floor, 0:
     * `Layout.fromJSON` invalidates the whole configuration on a negative one.  Public and static so it can be tested
     * without the modal prompt.
     * @param key the point property being written
     * @param value the number typed
     * @return the sentence to show, already translated, or null when the number is fine
     */
    public static String whyNotThisNumber(String key, int value)
    {
        if ("maxTrainLength".equals(key) && value < 0) return I18n.t("autosetup.ui.errorMaxTrainLengthNegative");

        return null;
    }

    /** Delegates to `EditorPrompts.promptNumber`. */
    void promptNumber(TileKey tile, String key, String promptKey, int unset)
    {
        prompts.promptNumber(tile, key, promptKey, unset);
    }


    Set<String> strings(TileKey tile, String key)
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
    /** A stored number for a point, or the value meaning "not set". */
    int number(TileKey tile, String key, int fallback)
    {
        Object value = session.getPointProperty(tile, key);

        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    /** The speed multiplier as a percentage; the model stores a factor in (0, 2] and refuses anything else. */
    int percent(TileKey tile)
    {
        Object stored = session.getPointProperty(tile, "speedMultiplier");

        return stored instanceof Number
            ? (int) Math.round(((Number) stored).doubleValue() * 100) : 100;
    }

    void promptPercent(TileKey tile)
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

            // 100% is the default, stored as nothing
            session.setPointProperty(tile, "speedMultiplier",
                value == 100 ? null : value / 100.0);
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(owner(), I18n.t("autolayout.ui.errorInvalidSpeedMultiplier"));
            // Nothing written, so nothing to announce.
            return;
        }

        setupChanged();
    }

    /**
     * Which locomotive calls this point home, or null.  One: Point.homeLoc is a single String, and an array would be
     * read by parseAuto as the literal text ["BR 111"].
     */
    String homeOf(TileKey tile)
    {
        Object stored = session.getPointProperty(tile, "home");

        return stored instanceof String && !((String) stored).trim().isEmpty()
            ? (String) stored : null;
    }

    /**
     * One of the picker's shortcut buttons: choose this name and close the dialog.
     * @param label what the button says, already formatted with the name
     * @param name the locomotive it picks
     * @param answer where the choice is left for the caller
     * @param pane the dialog to close
     * @return the button
     */
    private static javax.swing.JButton shortcut(String label, final String name,
        final String[] answer, final javax.swing.JOptionPane pane)
    {
        javax.swing.JButton pick = new javax.swing.JButton(label);

        pick.addActionListener(new java.awt.event.ActionListener()
        {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                answer[0] = name;

                pane.setValue(javax.swing.JOptionPane.OK_OPTION);
            }
        });

        return pick;
    }

    /** The gap between the picker's shortcut buttons (OB-188), which edge to edge read as one control. */
    private static final int SHORTCUT_GAP = 8;

    /**
     * The strip the picker's shortcut buttons sit on, public so a test elsewhere can measure the spacing.
     * @return an empty row, laid out left to right with a gap between whatever is added to it
     */
    public static javax.swing.JPanel shortcutRow()
    {
        return new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, SHORTCUT_GAP, 0));
    }

    /**
     * Asks for one locomotive from a filterable list (FR-010, FR-011), as a combo box is useless for sixty.  One
     * component for homes and for adding to autonomy, which differ in which shortcut buttons make sense.
     * @param owner what to centre the dialog on
     * @param title the dialog's title
     * @param prompt the question
     * @param names what may be chosen, in the order they should appear
     * @param current the one to start selected, or null
     * @param useActive the locomotive being driven, behind a "use active" button, or null for none
     * @param useParked the locomotive standing at this station, behind a "use current" button, or
     *        null when nothing is parked there
     * @return the chosen name, or null if the dialog was cancelled
     */
    static String pickLocomotive(java.awt.Component owner, String title, String prompt,
        List<String> names, String current, final String useActive, final String useParked)
    {
        final javax.swing.DefaultListModel<String> shown = new javax.swing.DefaultListModel<>();

        for (String name : names) shown.addElement(name);

        final javax.swing.JList<String> list = new javax.swing.JList<>(shown);

        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(12);
        list.setSelectedValue(current == null ? (names.isEmpty() ? null : names.get(0)) : current, true);

        final javax.swing.JTextField filter = new javax.swing.JTextField();

        filter.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipFilterLocomotives")));

        // Re-filtered on every keystroke against all the names, so deleting brings entries back.
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

                // Keep a selection that still exists, else select the first match, so Enter means the obvious thing.
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

        // By hand, not showInputDialog: "use current" is a third answer.
        final javax.swing.JOptionPane pane = new javax.swing.JOptionPane(panel,
            JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

        final String[] answer = new String[1];

        // Two shortcuts (Adam: *"'use current' should be the train currently parked at the station.  Rename the current
        // behavior to 'use active', and add a second button for 'use current' if a train is parked there."*), each
        // only when its locomotive is in the list.
        JPanel row = shortcutRow();

        if (useParked != null && names.contains(useParked))
        {
            row.add(shortcut(I18n.f("autosetup.ui.btnUseParkedLocomotive", useParked),
                useParked, answer, pane));
        }

        if (useActive != null && names.contains(useActive) && !useActive.equals(useParked))
        {
            row.add(shortcut(I18n.f("autosetup.ui.btnUseActiveLocomotive", useActive),
                useActive, answer, pane));
        }

        // Only when there is something in it.
        if (row.getComponentCount() > 0) panel.add(row, java.awt.BorderLayout.SOUTH);

        // Double-click chooses, as OK does.
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

        // The filter has the caret: typing is what this dialog is for
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

    /** One station's row height in the blocked-points picker, and so one wheel notch. */
    static final int BLOCKED_ROW_HEIGHT = 24;

    void promptBlockingPoints(TileKey station)
    {
        prompts.promptBlockingPoints(station);
    }

    /**
     * The Pick on the Diagram button as the "nothing to list" message shows it, greyed (MT-376), for a test.
     * @return a disabled button carrying the option's own words
     */
    public static javax.swing.JButton nothingToPickButton()
    {
        javax.swing.JButton button =
            new javax.swing.JButton(I18n.t("autosetup.ui.optionPickBlockerOnDiagram"));

        button.setEnabled(false);

        return button;
    }

    /**
     * `whyNotABlocker`, for a test (MT-376).
     * @return the refusal, or null when the square would be accepted
     */
    public String whyNotABlockerForTest(TileKey station, TileKey tile)
    {
        return whyNotABlocker(station, tile);
    }

    /**
     * Hands the next click to this station's blocking list (FR-025): one method for both doors into the gesture.
     * @param station the station being held back
     */
    void armBlockerPick(TileKey station)
    {
        // In the deep menu, needsTheGrid jumps to the page instead.
        if (needsTheGrid(station)) return;

        blockingFor = station;

        waitFor(I18n.f("autosetup.ui.promptClickBlocker", describeTile(station)));

        refresh();
    }

    /**
     * Why a clicked square cannot hold this station back, or null (FR-025): a square autonomy does not route over, or
     * the station itself, by name or caption (OB-083).  A click needs no name, as blockers resolve by tile.
     * @param station the station being held back
     * @param tile the square that was clicked
     * @return the message to show, or null to accept it
     */
    private String whyNotABlocker(TileKey station, TileKey tile)
    {
        if (tile == null || station == null) return I18n.t("autosetup.ui.errorNotABlockablePoint");

        if (tile.equals(station) || station.equals(session.getCaptionTarget(tile)))
        {
            return I18n.f("autosetup.ui.errorBlockerIsTheStation", describeTile(station));
        }

        if (session.getReducer() == null
            || !session.getReducer().getPoints().containsKey(tile))
        {
            return I18n.t("autosetup.ui.errorNotABlockablePoint");
        }

        // A station, as the list offers (MT-376), so both doors refuse alike; a stored entry is accepted.
        if (!session.getStore().isStation(tile)
            && !session.getStore().getBlockingPoints(station).contains(tile))
        {
            return I18n.f("autosetup.ui.errorBlockerNotAStation", describeTile(tile));
        }

        return null;
    }

    /**
     * Sets a square's home locomotive from outside its menu (Adam: *"set control+H for home."*).
     * @param tile the square to set a home on, ignored when null
     */
    public void promptHomeFor(TileKey tile)
    {
        if (tile == null || session == null || session.getGraph() == null) return;

        if (heldByARun()) return;

        // A gesture in progress is put down first, as the menu does.
        cancelPendingGesture();

        // Only where the menu offers a home (`offersAHome`), or the key would home a train on a non-station.
        if (!offersAHome(tile))
        {
            // Its own reason
            say(hint, isIgnored(tile) ? I18n.t("autosetup.ui.infoTileIgnored")
                : I18n.t("autosetup.ui.infoNotAStationForAHome"));

            return;
        }

        promptHome(leaderOf(tile));
    }

    /**
     * Whether the right-click menu offers a home here, asking the menu's questions in its order; public so a test can
     * compare key and menu (`regression.testControlNAsksTheMenusQuestion`).
     * @return whether the menu offers the item
     */
    public boolean offersAHome(TileKey tile)
    {
        if (tile == null || session == null || session.getGraph() == null) return false;

        LayoutDiagramComponent onPage = componentAt(tile);

        if (pageOf(tile) != null && (onPage == null || onPage.isText())) return false;

        if (isIgnored(tile)) return false;

        TileKey target = leaderOf(tile);

        return session.getReducer() != null && session.getReducer().getPoints().containsKey(target)
            && session.getStore().isStation(target);
    }

    void promptHome(TileKey tile)
    {
        String current = homeOf(tile);

        List<String> names = homeChoices(I18n.t("autosetup.ui.labelNone"), placedLocomotives(), current);

        if (names.size() == 1)
        {
            JOptionPane.showMessageDialog(owner(), I18n.t("error.noLocs"));
            return;
        }

        // FR-010: offering the locomotive being driven, only if it is one of the choices.
        String driving = parentWindow() == null || parentWindow().getActiveLoc() == null
            ? null : parentWindow().getActiveLoc().getName();

        // And the one standing here, from the setup, which the diagram draws.
        String parked = locomotiveAt(tile);

        String chosen = pickLocomotive(owner(), I18n.t("autosetup.ui.menuHomeNone"),
            I18n.t("autosetup.ui.promptHomeFor"), names,
            current == null ? names.get(0) : current, driving, parked);

        if (chosen == null) return;

        String picked = names.get(0).equals(chosen) ? null : chosen;

        // A home it cannot rest at is warned about, not refused (OB-022): the track may not be finished.
        if (picked != null && !mayRestHere(tile, picked) && !confirmedAnyway(picked, tile)) return;

        // A locomotive at home elsewhere is warned about: setting this one moves it, never silently.
        TileKey already = picked == null ? null : session.homeElsewhere(tile, picked);

        if (already != null && JOptionPane.showOptionDialog(owner(),
            I18n.f("autolayout.ui.confirmHomeIsAlreadySet", picked, describeTile(already)),
            I18n.t("autolayout.ui.dialogSetHomeLocomotive"),
            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[0]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        // Which way it should face there, where unknown (OB-282: *"prompt the user for the direction"*); dismissed sets
        // no home.
        org.traincontrol.automationui.TilePorts.Side facing = null;

        // Asked exactly when the session has no answer (the railway first).
        if (picked != null && session.knownHomeFacing(tile, picked) == null)
        {
            java.util.Set<org.traincontrol.automationui.TilePorts.Side> canHold = session.homeFacingsFor(tile);

            if (FacingPrompt.wouldAsk(canHold))
            {
                facing = FacingPrompt.forHome(canHold, picked, describeTile(tile), owner());

                if (facing == null) return;
            }
        }

        // Through setHome, which sweeps the locomotive's other homes.
        session.setHome(tile, picked, facing);

        refresh();

        setupChanged();
    }

    /** Whether this locomotive could come to rest here, by the running layout; yes before one is loaded. */
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

        // Only the cannot-rest reason, so a new reason does not start warning here.
        return !"autolayout.errorHomeCannotRestHere".equals(
            org.traincontrol.automation.HomeStaging.whyNotAHome(loc, point));
    }

    /** Asks whether to set an unreachable home, defaulting to No. */
    private boolean confirmedAnyway(String locomotive, TileKey tile)
    {
        return JOptionPane.showOptionDialog(owner(),
            I18n.f("autolayout.ui.confirmCannotBeHomeHere", locomotive, describeTile(tile)),
            I18n.t("autolayout.ui.dialogSetHomeLocomotive"),
            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) == JOptionPane.YES_OPTION;
    }

    /** Every locomotive the control station knows about. */
    List<String> allLocomotives()
    {
        return locomotiveNames == null
            ? java.util.Collections.<String>emptyList() : locomotiveNames.get();
    }

    /** Only the locomotives placed on this layout: a home for one autonomy never heard of would do nothing. */
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
     * The locomotive's own settings on this square's menu, the dialog the track diagram opens: the only place for
     * arrival and departure functions.  Needs the running layout's Point; the placement is written back to the setup
     * afterwards (`LayoutRightclickAutonomyMenu.placeFacing`).
     */
    void addLocomotiveSettings(javax.swing.JPopupMenu menu, TileKey target)
    {
        org.traincontrol.automation.Layout layout =
            layoutSource == null ? null : layoutSource.get();

        if (layout == null || layout.getLocomotivesToRun().isEmpty()) return;

        final org.traincontrol.automation.Point point = pointOnTheLayout(layout, target);

        if (point == null) return;

        // Offered whether or not a train stands here (MT-101); menuLabelFor says "Place" or "Edit".

        menu.add(item(GraphLocAssign.menuLabelFor(point), () ->
        {
            // With this panel's session, for the arrival side.
            GraphLocAssign edit = new GraphLocAssign(parentWindow(), point, session,
                runningLayout == null ? null : runningLayout.get());

            // `asDialogContent`, with the arrival side under the form.
            int answer = JOptionPane.showOptionDialog(owner(), edit.asDialogContent(),
                I18n.f("autolayout.ui.dialogEditOrAssignLocomotive", describeTile(target)),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                TrainControlUI.OK_CANCEL_OPTS, TrainControlUI.OK_CANCEL_OPTS[0]);

            if (answer != JOptionPane.OK_OPTION) return;

            // One door for both menus (Adam: *"there needs to be parity across the board"*).
            GraphLocAssign.commitAndRecord(edit);
            // And the railway is told.
            setupChanged();
        }));
    }

    /** The running layout's Point for a square, preferring the copy holding a train. */
    org.traincontrol.automation.Point pointOnTheLayout(
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
     * The main window, told by setMainWindow where it is not up the tree (inside the layout editor's JFrame); the walk
     * stays as the fallback.
     */
    TrainControlUI parentWindow()
    {
        if (mainWindow != null) return mainWindow;

        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);

        while (window != null && !(window instanceof TrainControlUI))
        {
            window = window.getOwner();
        }

        return window instanceof TrainControlUI ? (TrainControlUI) window : null;
    }

    /** @param window the main window, for the surfaces that are not inside it */
    public void setMainWindow(TrainControlUI window)
    {
        this.mainWindow = window;
    }

    private TrainControlUI mainWindow;

    /**
     * The Is Active tick (FR-117): train and setup together through the window's door, else the setup alone; then the
     * railway is told.
     * @param name the train standing on the square
     * @param active whether autonomy may run it
     */
    void setActive(String name, boolean active)
    {
        TrainControlUI window = parentWindow();

        org.traincontrol.base.Locomotive train = window == null || window.getModel() == null ? null
            : window.getModel().getLocByName(name);

        if (window != null && train != null)
        {
            window.setLocomotiveActive(session, train, active);
        }
        else
        {
            try
            {
                session.setLocomotivePaused(name, !active);
            }
            catch (java.io.IOException cannotSave)
            {
                throw new IllegalStateException(cannotSave.getMessage(), cannotSave);
            }
        }

        setupChanged();
    }

    String locomotiveAt(TileKey tile)
    {
        return session == null ? null : session.getLocomotiveNameAt(tile);
    }

    /**
     * Puts a locomotive on a square, chosen from the whole roster with those autonomy runs marked first; choosing one
     * moves it (OB-009).
     * @param tile the square, which is a station or this item is not offered
     */
    void placeLocomotive(TileKey tile)
    {
        List<String> placed = placedLocomotives();

        String here = locomotiveAt(tile);

        List<String> names = new java.util.ArrayList<>();

        // Already in autonomy first, and marked: a move is the commoner gesture.
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

        // FR-011: without "use current", as the driven train is rarely in this list.
        String chosen = pickLocomotive(owner(), I18n.t("autosetup.ui.menuAddToAutonomy"),
            I18n.t("autosetup.ui.promptAddToAutonomy"), names, names.get(0), null, null);

        if (chosen == null) return;

        String name = unmark(chosen, placed);

        if (name == null) return;

        // The session lifts it off everywhere, excluded pages too.  Its heading is read first: the facing would
        // otherwise be the previous occupant's.
        org.traincontrol.automationui.TilePorts.Side heading = session.facingOf(name);

        session.placeLocomotive(tile, name);

        // Over the copies it could leave (OB-284), as a paste records it.
        session.setFacing(tile, org.traincontrol.automationui.AutonomySession.facingAfterAPaste(
            session.departableFacingsFor(tile, layoutSource == null ? null : layoutSource.get()), heading, null));

        // Named (MT-337): only the rebuild puts the train on the railway.
        placementChanged(java.util.Collections.singletonList(name));
    }

    /**
     * Redraws whatever shows this placement: this window's grid, and the running layout, which the track diagram draws
     * from (OB-034, OB-035); both, as both are on screen.
     * @param placementsEdited the locomotives whose placement this gesture wrote into the setup and nowhere else, or
     *        null where it changed no placement
     */
    void placementChanged(java.util.Collection<String> placementsEdited)
    {
        // Which trains the setup has a newer answer about, which the data cannot tell: only setup-only placement doors
        // name them.  A required argument, so every call site decides.
        if (placementsEdited != null)
        {
            for (String name : placementsEdited)
            {
                if (name != null) placementsJustEdited.add(name);
            }
        }

        // Both, as both are on screen (MT-125); the rebuild declines while autonomy is busy.
        setupChanged();

        refresh();
    }

    /**
     * The plain name behind a marked row, matched against the names rather than parsed: a locomotive may be called "BR
     * 218 (elsewhere)".
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

    /** Delegates to `EditorPrompts.promptLocomotives`. */
    void promptLocomotives(TileKey tile, String key, List<String> names)
    {
        prompts.promptLocomotives(tile, key, names);
    }

    /**
     * The home that excluding these locomotives here would contradict, read off the setup, where homes live.
     * @param allowOnly whether the list names the only ones allowed (FR-121) rather than the ones shut out
     * @return the home that would be contradicted, or null
     */
    String homeBrokenBy(TileKey tile, List<String> chosen, boolean allowOnly)
    {
        return homeBrokenBy(homeOf(tile), chosen, allowOnly);
    }

    /**
     * The home this exclusion list would break, or null: allowed, but shown rather than found at the end of a run
     * (MT-112).
     * @param home the locomotive homed at this station, or null
     * @param excluded the locomotives being shut out of it
     * @return the home that would be broken, or null
     */
    public static String homeBrokenBy(String home, List<String> excluded)
    {
        return homeBrokenBy(home, excluded, false);
    }

    /**
     * The same, read in the list's mode (FR-121): a named home where it shuts out, an unnamed one where it allows only.
     * @param home the station's home locomotive, or null
     * @param chosen the locomotives the list names
     * @param allowOnly whether the list names the only locomotives allowed
     * @return the home that would be contradicted, or null
     */
    public static String homeBrokenBy(String home, List<String> chosen, boolean allowOnly)
    {
        if (home == null || chosen == null) return null;

        return chosen.contains(home) != allowOnly ? home : null;
    }

    /** Whether a square's locomotive list names the only ones allowed there (FR-121). */
    boolean allowsOnlyListed(TileKey tile)
    {
        return org.traincontrol.automation.Point.ALLOW_ONLY_LISTED.equals(
            session.getPointProperty(tile, org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE));
    }

    /**
     * What the home list offers (MT-112): "None", the station's current home if autonomy no longer runs it (or OK would
     * clear it unasked, a combo cannot preselect what it lacks), then the locomotives autonomy runs.
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

    /**
     * Names a square from outside its menu, for the editor's Control+S (MT-257).
     * @param tile the square to name, ignored when null
     */
    public void promptNameFor(TileKey tile)
    {
        if (heldByARun()) return;

        // A gesture in progress is put down first.
        cancelPendingGesture();

        if (canBeNamed(tile)) promptName(tile);
    }

    /**
     * Whether the menu offers Show a Station Name Here, live, on this square (FR-086), by the menu's own questions;
     * public so a test can compare key and menu (`regression.testControlNAsksTheMenusQuestion`).
     * @param tile the square
     * @return whether the menu offers the item, enabled
     */
    public boolean offersAStationName(TileKey tile)
    {
        if (tile == null || session == null || session.getGraph() == null) return false;

        LayoutDiagramComponent onPage = componentAt(tile);

        // Only where the running diagram draws
        if (pageOf(tile) != null && (onPage == null || onPage.isText())) return session.isInTheDrawnArea(tile);

        if (isIgnored(tile)) return false;

        if (menuOnly || !mayCarryACaption(onPage) || !session.isInTheDrawnArea(tile)) return false;

        return !(session.getStore().isStation(tile) && tile.equals(session.getCaptionTarget(tile)));
    }

    /**
     * Control+N: shows a station's name on a square where `offersAStationName` says the menu would (FR-086; MT-397:
     * *"let's add a hotkey for 'show station name here' too"*).
     * @param tile the square under the pointer, ignored when null
     */
    public void showStationNameFor(TileKey tile)
    {
        if (tile == null || menuOnly || session == null || session.getGraph() == null) return;

        if (heldByARun()) return;

        cancelPendingGesture();

        if (!offersAStationName(tile))
        {
            if (isIgnored(tile)) say(hint, I18n.t("autosetup.ui.infoTileIgnored"));

            return;
        }

        promptStationLabel(tile, componentAt(tile));
    }

    /**
     * Control+E: the length of the square under the pointer (FR-066; Adam: *"a keyboard shortcut for setting a square's
     * length"*), asked by `offersALength` and written where the menu writes, the run's leader, or a run would be
     * counted twice.  Where the menu offers nothing it says so (MT-313).
     * @param tile the square to measure, ignored when null
     */
    public void promptLengthFor(TileKey tile)
    {
        prompts.promptLengthFor(tile);
    }

    /**
     * Where a length typed for this square lands with nothing selected, or null: the menu's target, the run's leader,
     * not the pointer's (`regression.testControlEAsksTheMenusQuestion`).
     * @param tile the square under the pointer
     * @return the square the length would be written to, or null when nothing would be asked
     */
    public TileKey squareTheLengthWouldGoOn(TileKey tile)
    {
        return offersALength(tile) ? leaderOf(tile) : null;
    }

    /**
     * Whether Set Length... is on this square's menu, and so whether Control+E means anything: `buildTileMenu`'s early
     * returns, asked rather than repeated (`regression.testControlEAsksTheMenusQuestion`).
     * @param tile the square
     * @return whether the menu offers to measure it
     */
    public boolean offersALength(TileKey tile)
    {
        if (tile == null || session == null || session.getGraph() == null) return false;

        LayoutDiagramComponent onPage = componentAt(tile);

        if (pageOf(tile) != null && (onPage == null || onPage.isText())) return false;

        return !isIgnored(tile);
    }

    /**
     * Control+B: the maximum train length of the station under the pointer (Adam: *"map it to the station maximum train
     * length, and add a tooltip."*), through the menu item's method and `offersAMaximumTrainLength`; a refusal says
     * which reason.
     * @param tile the square under the pointer
     */
    public void promptMaxTrainLengthFor(TileKey tile)
    {
        if (heldByARun()) return;

        // A gesture in progress is put down first.
        cancelPendingGesture();

        if (!offersAMaximumTrainLength(tile))
        {
            if (tile != null && session != null && session.getGraph() != null)
            {
                say(hint, isIgnored(tile) ? I18n.t("autosetup.ui.infoTileIgnored")
                    : I18n.t("autosetup.ui.infoNotAStationHere"));
            }

            return;
        }

        promptNumber(squareTheMaximumWouldGoOn(tile), "maxTrainLength",
            "autolayout.ui.promptEnterMaxTrainLength", 0);
    }

    /**
     * Where a maximum typed for this square lands, or null: the menu's target
     * (`regression.testControlBAsksTheMenusQuestion`).
     * @param tile the square under the pointer
     * @return the square the maximum would be written to, or null when nothing would be asked
     */
    public TileKey squareTheMaximumWouldGoOn(TileKey tile)
    {
        return offersAMaximumTrainLength(tile) ? leaderOf(tile) : null;
    }

    /**
     * Whether Maximum Train Length is on this square's menu (MT-313): `offersALength`, a Point, and a station.
     * @param tile the square
     * @return whether the menu offers to set a maximum on it
     */
    public boolean offersAMaximumTrainLength(TileKey tile)
    {
        if (!offersALength(tile)) return false;

        TileKey target = leaderOf(tile);

        return session.getReducer() != null
            && session.getReducer().getPoints().containsKey(target)
            && session.getStore().isStation(target);
    }

    /**
     * Whether this square can be named: the menu's own question (MT-313).
     * @return whether naming it means anything
     */
    public boolean canBeNamed(TileKey tile)
    {
        return session != null && session.canBeNamed(tile);
    }

    /**
     * Asks for a square's name and writes it (MT-257); others go through `promptNameFor`.
     * @param tile the square to name
     */
    void promptName(TileKey tile)
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

        // A name ending like a builder copy's is refused, and said.
        String refused = AutonomySession.whyNotAPointName(name);

        if (refused != null)
        {
            JOptionPane.showMessageDialog(owner(), wrapped(refused));

            return;
        }

        // Cannot fail: captions point at the square.
        session.setPointName(tile, name.trim());

        // A new name goes on the diagram only where the station has no caption (MT-116); `placeCaption` would move one.
        if (session.getStore().isStation(tile)
            && !session.getLabelledStationTiles().contains(tile))
        {
            placeLabelFor(tile);
        }

        // And the running layout is rebuilt, or the label goes blank (OB-034): it still holds the old name.  Persisted
        // too (MT-246).
        setupChanged();
    }

    /** Delegates to `EditorPrompts.promptLinkName`. */
    void promptLinkName(TileKey tile)
    {
        prompts.promptLinkName(tile);
    }

    /** Pairs a link by choosing its partner from a list: its partner is usually on another page. */
    void pairFromList(TileKey tile)
    {
        List<TileKey> candidates = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();

        // Like pairs with like: a link with a tunnel would draw a train out of the wrong symbol.
        LayoutDiagramComponent from =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        if (from == null) return;

        for (Map.Entry<TileKey, LayoutDiagramComponent> entry
            : session.getGraph().getTiles().entrySet())
        {
            LayoutDiagramComponent component = entry.getValue();

            if (component == null || entry.getKey().equals(tile)) continue;

            if (component.getType() != from.getType()) continue;

            // A tunnel pairs only within its own page (MT-047).
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

        // The diagram follows the dropdown, not OK, so the square is shown while choosing; by hand for the combo box.
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

        if (onReveal != null) onReveal.accept(candidates.get(0));

        if (JOptionPane.showConfirmDialog(owner(), panel, I18n.t("autosetup.ui.toolPortals"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        int at = choice.getSelectedIndex();

        if (at < 0) return;

        session.pairPortals(tile, candidates.get(at));

        // A portal pair is an edge of the running graph.
        setupChanged();
    }

    /**
     * Whether a square can be a station's protecting signal: a SIGNAL with an address (isSignal() is true of a lamp).
     * @param component what is drawn on the square, or null for an empty one
     */
    boolean isPairableSignal(LayoutDiagramComponent component)
    {
        return component != null
            && component.getType() == LayoutDiagramComponent.componentType.SIGNAL
            && component.getAccessory() != null;
    }

    /** Delegates to `EditorSignals.pairProtectingSignal`. */
    void pairProtectingSignal(TileKey station)
    {
        signalPairing.pairProtectingSignal(station);
    }

    /** Delegates to `EditorSignals.pairGuardSignals`. */
    void pairGuardSignals(Guard guard, TileKey station)
    {
        signalPairing.pairGuardSignals(guard, station);
    }

    /** Delegates to `EditorSignals.askAboutSignals`. */
    private void askAboutSignals(final Guard guard, TileKey station)
    {
        signalPairing.askAboutSignals(guard, station);
    }

    /**
     * Where a dialog goes so it does not cover the middle of its window: up and left of centre.  Public and static on
     * rectangles, to be tested without a dialog.
     * @param parent the bounds of the window the dialog belongs to, in screen coordinates
     * @param dialog the size the dialog packed to
     * @return the screen point to put the dialog's top left corner at
     */
    public static java.awt.Point besideOwner(java.awt.Rectangle parent, java.awt.Dimension dialog)
    {
        // A fraction of the slack, with a floor.
        int x = parent.x + Math.max(12, (parent.width - dialog.width) / 8);
        int y = parent.y + Math.max(12, (parent.height - dialog.height) / 6);

        // Never off the parent's bottom or right; a dialog larger than its parent overhangs the bottom right instead.
        x = Math.min(x, Math.max(parent.x, parent.x + parent.width - dialog.width));
        y = Math.min(y, Math.max(parent.y, parent.y + parent.height - dialog.height));

        return new java.awt.Point(x, y);
    }

    /**
     * A guard signal refused, said in a popup too (MT-505; Adam: *"any rejection errors should trigger a popup."*): the
     * guard window covers the grey line.
     * @param why the sentence
     */
    private void refuseGuard(String why)
    {
        say(hint, why);

        JOptionPane.showMessageDialog(owner(), wrapped(why));
    }

    /** Delegates to `EditorSignals.addGuardSignal`. */
    private String addGuardSignal(Guard guard, TileKey station, TileKey signal, boolean redraw)
    {
        return signalPairing.addGuardSignal(guard, station, signal, redraw);
    }

    /** Delegates to `EditorSignals.signalAddresses`. */
    String signalAddresses(java.util.List<TileKey> signals)
    {
        return signalPairing.signalAddresses(signals);
    }

    /**
     * A digits-only text field; empty stays reachable, as clearing it removes a length.
     * @param initial what to show
     * @return the field
     */
    javax.swing.JTextField digitsOnly(String initial)
    {
        javax.swing.JTextField field = new javax.swing.JTextField(initial, 8);

        // Three digits, 0 to 999 (OB-048).

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

                /** Digits, at most three once this edit is applied. */
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

    /**
     * Applies the remembered caption mode and Text Labels once this panel is in a window (MT-292, FR-133), as the
     * diagram half goes through `owner()`, null until then.  Called by `LayoutEditor.setAutonomyMode`; idempotent.
     */
    public void applyRememberedCaptionMode()
    {
        applyCaptionMode();
        applyTextLabels();
    }

    /**
     * Makes the two internal boxes agree with the caption choice (FR-061, OB-174).  The text switch is not touched
     * (OB-272, FR-133): that is the Text Labels box's (`applyTextLabels`).
     */
    private void applyCaptionMode()
    {
        int mode = captions.getSelectedIndex();

        showParkedTrains.setSelected(mode == CAPTIONS_PARKED);
        showHomeLocomotives.setSelected(mode == CAPTIONS_HOMES);
    }

    /** Makes the diagram's text switch agree with the remembered Text Labels box (FR-133), at open and on a click. */
    private void applyTextLabels()
    {
        if (showText.isSelected()) turnTextLabelsOn();
        else turnTextLabelsOff();
    }

    /**
     * Whether the diagram draws station captions: under the three caption modes, whatever Text Labels says (OB-272).
     * @return true under a caption mode
     */
    public boolean isDrawingCaptions()
    {
        return isACaptionMode(captions.getSelectedIndex());
    }

    private static boolean isACaptionMode(int mode)
    {
        return mode == CAPTIONS_STATIONS || mode == CAPTIONS_PARKED || mode == CAPTIONS_HOMES;
    }

    /**
     * Control+L: the next of the dropdown's four caption options, wrapping (OB-272), through its own listener.
     */
    public void cycleCaptionMode()
    {
        if (captions == null || captions.getItemCount() == 0) return;

        captions.setSelectedIndex((captions.getSelectedIndex() + 1) % captions.getItemCount());
    }

    /** @return the caption option chosen, one of the `CAPTIONS_` constants */
    public int getCaptionMode()
    {
        return captions.getSelectedIndex();
    }

    /**
     * Told the diagram's text switch moved on its own, so the Text Labels box agrees and is stored (FR-133).
     * @param shown whether the labels are now displayed
     */
    public void textLabelsChanged(boolean shown)
    {
        if (showText == null || showText.isSelected() == shown) return;

        showText.setSelected(shown);

        VIEW_PREFS.putBoolean(PREF_TEXT_LABELS, shown);
    }

    /** Turns the editor's text switch off for an unticked box (FR-133); idempotent, unlike `toggleText`. */
    private void turnTextLabelsOff()
    {
        java.awt.Component where = owner();

        if (where instanceof LayoutEditor) ((LayoutEditor) where).hideTextLabels();
    }
    /** Turns the diagram's text labels on for a ticked box (FR-133), if in an editor that has them. */
    private void turnTextLabelsOn()
    {
        java.awt.Component where = owner();

        if (where instanceof LayoutEditor) ((LayoutEditor) where).showTextLabels();
    }

    /** The grey of a square autonomy never chooses in the blocker list (FR-074): a shade of text, not disabled grey. */
    static final java.awt.Color NOT_CHOSEN_BY_AUTONOMY = new java.awt.Color(120, 120, 120);

    /** 0 to 999 (OB-048) */
    private static final int MAX_LENGTH_DIGITS = 3;

    /**
     * Asks for a length and writes it to the selection, or the square's leader with none (FR-066); others go through
     * `promptLengthFor`.
     * @param tile the square the length was asked on
     */
    void applyLength(TileKey tile)
    {
        Set<TileKey> targets = selection.isEmpty()
            ? new LinkedHashSet<>(java.util.Arrays.asList(tile)) : new LinkedHashSet<>(selection);

        // Read from a square that will change, which with a selection need not be the clicked one.
        TileKey sample = targets.iterator().next();

        // Digits only (OB-043).
        final javax.swing.JTextField field = digitsOnly(segmentLengthPrefill(sample, selection.isEmpty()));

        // Selected, so it can be typed over (OB-176), once the dialog is showing.
        field.addAncestorListener(new javax.swing.event.AncestorListener()
        {
            @Override
            public void ancestorAdded(javax.swing.event.AncestorEvent event)
            {
                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    field.requestFocusInWindow();
                    field.selectAll();
                });
            }

            @Override
            public void ancestorMoved(javax.swing.event.AncestorEvent event) { }

            @Override
            public void ancestorRemoved(javax.swing.event.AncestorEvent event) { }
        });

        // OK, Clear, Cancel: a 0 is an answer, so clearing needs its own button.
        Object ok = javax.swing.UIManager.getString("OptionPane.okButtonText");
        Object clear = I18n.t("autosetup.ui.optionClearLength");
        Object cancel = javax.swing.UIManager.getString("OptionPane.cancelButtonText");

        int chose = JOptionPane.showOptionDialog(owner(),
            new Object[] {I18n.t("autosetup.ui.promptTileLength"), field},
            I18n.t("autosetup.ui.menuSetLength"),
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[] {ok, clear, cancel}, ok);

        if (chose == 1)
        {
            applyLengthAnswer(tile, null);

            return;
        }

        if (chose != 0) return;

        String entered = field.getText().trim();

        // Empty and submitted still means none (OB-043).
        applyLengthAnswer(tile, entered.isEmpty() ? null : Integer.valueOf(entered));
    }

    /**
     * What Segment Length's box opens holding.
     * @param sample the square read: the one clicked, or the first of a selection
     * @param wholeRun whether the answer is for the run the square is in, rather than for each selected square
     * @return the text to prefill
     */
    public String segmentLengthPrefill(TileKey sample, boolean wholeRun)
    {
        int measured = wholeRun ? lengthShownFor(sample) : session.getStore().getTileLength(sample);

        // Empty where nothing was said, or OK would write a deliberate 0 nobody gave.
        if (measured <= 0 && !session.getStore().isTileLengthAnswered(sample)) return "";

        return String.valueOf(measured);
    }

    /**
     * What Segment Length does with an answer: a length for the run or each selected square; 0 is an answer (OB-274),
     * null clears.  Apart from the dialog, to run without a display.
     * @param tile the square the dialog was opened on
     * @param length the length typed, or null to clear
     */
    public void applyLengthAnswer(TileKey tile, Integer length)
    {
        Set<TileKey> targets = selection.isEmpty()
            ? new LinkedHashSet<>(java.util.Arrays.asList(tile)) : new LinkedHashSet<>(selection);

        if (length != null && length == 0)
        {
            session.answerTileLengthsZero(selection.isEmpty() ? runTilesOf(tile) : targets);

            selection.clear();

            setupChanged();

            return;
        }

        int typed = length == null ? 0 : length;

        if (selection.isEmpty())
        {
            // ONE SQUARE SPEAKS FOR THE RUN, which means the run measures what was typed.  This
            // announces the change itself, so the setupChanged below is for the selection arm.
            setRunLength(tile, typed);
            selection.clear();

            return;
        }
        else
        {
            // A SELECTION IS SQUARES, NOT RUNS: several shift-clicked get the number each, and a run in the selection
            // is reached through the leader that is in it.
            for (TileKey target : targets)
            {
                session.setTileLength(target, typed);
            }
        }

        selection.clear();

        // Lengths are what the reversal guard measures with, and it asks the RUNNING layout: the same seam as
        // MT-246, one setter over.
        setupChanged();
    }

    /**
     * Whether Why Not Moving is waiting for its click and this square has a train on it to ask about (FR-102): the
     * editor highlights stations with trains, so it is clear what the user can click on.  The train as the running
     * railway has it where there is one (a run moves trains the setup has not been told about), and as the setup places
     * it otherwise.
     *
     * @return whether to outline it
     */
    private boolean whyWaitsOn(TileKey tile)
    {
        if (tool != Tool.WHY || lastWhyTile != null || tile == null) return false;

        org.traincontrol.automation.Layout now = runningLayout == null ? null : runningLayout.get();

        if (now != null && session.getStationIndex() != null)
        {
            for (org.traincontrol.automation.Point point : now.getPoints())
            {
                if (point.getCurrentLocomotive() != null && tile.equals(session.getStationIndex().squareOf(point)))
                {
                    return true;
                }
            }

            return false;
        }

        return session.getLocomotiveNameAt(tile) != null;
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

        // A clicked finding's outline goes with the next click on the diagram
        if (!noticeOutline.isEmpty())
        {
            noticeOutline.clear();

            refresh();
        }

        rememberIfStation(tile);

        // A train's tail is waiting to be told the farthest sensor it crossed, and this is that click.  First, like the
        // other gestures that asked for a particular square: what an ordinary click would do to it is not the question.
        if (tailFor != null)
        {
            if (heldByARun()) return;

            java.util.List<TailCrossedPrompt.Choice> here = tailPicks.get(tile);

            if (here == null)
            {
                // Stays armed, as the blocker pick does: Escape or a right-click cancels.
                say(hint, I18n.t("autosetup.ui.errorNotATailCrossing"));

                return;
            }

            TileKey standing = tailFor;

            TailCrossedPrompt.Choice chosen = here.size() == 1 ? here.get(0)
                : TailCrossedPrompt.ask(owner(), locomotiveAt(standing), describeTile(standing), here);

            tailFor = null;
            tailPicks.clear();
            highlightedSignals.clear();

            if (chosen != null) recordTailRoad(standing, chosen.getRoad());

            say(hint, I18n.t("autosetup.ui.hintClickToCycle"));

            refresh();

            return;
        }

        // The one-way tool: the first click names one end.
        if (tool == Tool.ONE_WAY && oneWayFrom == null)
        {
            if (heldByARun()) return;

            if (needsTheGrid(tile)) return;

            // NOT ON A SQUARE AUTONOMY TAKES NO NOTICE OF (OB-235): the tool's clicks came before that question.
            if (isIgnored(tile))
            {
                say(hint, I18n.t("autosetup.ui.infoTileIgnored"));

                return;
            }

            oneWayFrom = tile;

            say(hint, I18n.f("autosetup.ui.promptOneWayTo", describeTile(tile)));

            refresh();
            return;
        }

        // ... and the second names the other, after which it asks which way round it goes.
        if (oneWayFrom != null)
        {
            if (heldByARun()) return;

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

            // WHICH WAY, asked rather than assumed (OB-006): two squares describe a run, not a direction, and the
            // direction is the entire content of the decision; a wrong guess closes a stretch of railway the wrong way
            // round with no trace.  Named by the squares themselves, so the answer can be checked against what is on
            // screen.
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

            // THE ARROWS, NOT THE DIAGRAM (OB-185): a restriction changes no tile art, only the arrow over the tile,
            // and redrawing every label flickers the whole screen once per click on a tool used many times in a row.
            if (changed >= 0) annotationsChanged();

            // Armed for the next one: closing a run is rarely a single act (a yard is several).
            if (changed >= 0 && tool == Tool.ONE_WAY)
            {
                say(hint, I18n.f("autosetup.ui.oneWayDoneAgain", changed));
            }

            refresh();
            return;
        }

        // A station is waiting to be told which signal protects it, and this is that click.  Answered BEFORE the
        // ignored check below, as a signal is one of the squares that check greys out (no trains are routed through
        // one).
        if (signalFor != null)
        {
            if (heldByARun()) return;

            TileKey station = signalFor;

            // IN A POPUP as well as on the line (see `refuseGuard`), AND BACK TO THE GUARD WINDOW (MT-588: *"it should
            // send the user back to the existing popup so they can click on another square."*), whose Click It on the
            // Diagram button picks another.
            if (!isPairableSignal(component))
            {
                refuseGuard(I18n.t("autosetup.ui.errorNotASignal"));

                signalFor = null;

                pairGuardSignals(signalForGuard, station);
                return;
            }

            signalFor = null;

            Guard guard = signalForGuard;

            String refused = addGuardSignal(guard, station, tile, true);

            // SAID BEFORE THE LIST COMES BACK (MT-505): the window stands in front of the line that says it.
            if (refused != null) refuseGuard(refused);

            // And straight back to the list, which is where the signal just clicked now appears.  A
            // second one is a button and another click rather than the whole right-click menu again.
            pairGuardSignals(guard, station);
            return;
        }

        // A station is waiting to be told which square holds it back, and this is that click (FR-025).  Before the
        // ignored check, for the signal gesture's reason: a gesture that asked for a particular square must be able to
        // answer about it, and the check below is about what an ORDINARY click does.
        if (blockingFor != null)
        {
            if (heldByARun()) return;

            TileKey station = blockingFor;

            String refusal = whyNotABlocker(station, tile);

            if (refusal != null)
            {
                // The gesture STAYS ARMED on a refusal, unlike the signal one, which drops it: missing a small square
                // is the ordinary way to get this wrong, and re-arming costs the whole right-click menu again.  Escape
                // or a right-click still cancels.
                say(hint, refusal);
                return;
            }

            blockingFor = null;

            java.util.List<TileKey> held =
                new java.util.ArrayList<>(session.getStore().getBlockingPoints(station));

            if (!held.contains(tile)) held.add(tile);

            session.getStore().setBlockingPoints(station, held);

            // Built into the configuration as lock edges, so the running layout has to be regenerated
            // for it to mean anything - the same seam the checklist uses.
            placementChanged(null);

            // And back to the list, where the square just clicked is now ticked; a second one is a button and another
            // click, not the whole right-click menu again.
            promptBlockingPoints(station);
            return;
        }

        // Autonomy takes no notice of this square, so a click here changes nothing.  Route buttons are the case that
        // matters: their connections are INFERRED from the track around them, so setting them by hand would offer a
        // decision the next rebuild would silently discard.
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
                default: if (!heldByARun()) cycle(tile); break;
            }
        }
        catch (RuntimeException e)
        {
            JOptionPane.showMessageDialog(owner(), String.valueOf(e.getMessage()));
        }

        refresh();
    }

    /**
     * Turns the arrows back on when an edit would otherwise happen invisibly (OB-008): with the visibility control set
     * to None, clicking a square changes its direction and nothing on screen moves, which reads as a broken control,
     * and clicking it again cycles the square on to a state nobody asked for.  Restrictions rather than All, as it is
     * the default (open track is most of a layout); only from None and only on an edit, as somebody who has chosen All
     * or Arrivals has chosen something that already shows their edit.
     */
    private void showRestrictionsIfHidden()
    {
        if (directionsView() != 2) return;

        showDirectionsView(1);

        say(hint, I18n.t("autosetup.ui.infoDirectionsShownAgain"));
    }

    /**
     * Whether an ordinary left click on this square changes which way trains may run through it, which is what the
     * diagram's pointer and tooltip offer (FR-112; Adam: *"it is not clear to the user that tiles with arrows can be
     * clicked"*).  The questions `tileClicked` and `cycle` ask before a click changes anything, in their order: nothing
     * armed (a tool or a gesture takes the click for itself), a square autonomy takes notice of, not a link, and track
     * whose next state lands on something (a piece joined to nothing cannot be made one way, so its click says there is
     * no path).  `regression.testTheEditorSaysWhatItsToolsDo.testTheClickAndItsPointerAgree` holds the click and its
     * pointer to one answer.
     *
     * @param tile a square, or null
     * @return true where a click there changes a direction
     */
    public boolean clickChangesDirections(TileKey tile)
    {
        if (tile == null || session.getGraph() == null || anythingIsArmed() || isIgnored(tile)) return false;

        TileKey target = leaderOf(tile);

        // NOR A SQUARE OUT OF SERVICE: nothing can pass it, it draws no arrows, and `cycle` declines it
        // - NOR TRACK NO STATION IS ON, which draws none either (OB-353)
        if (isALink(target) || isOutOfService(target) || session.reachesNoStation(target)) return false;

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(target);

        if (routes.isEmpty()) return false;

        // A switch, a crossing or a double curve steps through its arms, which a click always does
        if (routes.size() > 1) return true;

        // One track: the click's next state, which goes on whole unless it is one way - and one way lands only on a run
        // with track between its ends (`cycle`, which says there is no path where it does not)
        Direction next = after(session.directionAsDrawn(target, routes.keySet().iterator().next()));

        return next == Direction.BOTH || next == Direction.NONE || session.canRunOneWay(target);
    }

    /** Whether a square is out of service (nothing may pass or stop there), so that it has no directions to set. */
    private boolean isOutOfService(TileKey target)
    {
        return Boolean.FALSE.equals(session.getPointProperty(target, "active"));
    }

    /** Whether a square is a link, which has no direction of its own: the track either side of it governs. */
    private boolean isALink(TileKey target)
    {
        LayoutDiagramComponent here = session.getGraph() == null ? null : session.getGraph().getTiles().get(target);

        return here != null && org.traincontrol.automationui.TilePorts.hasPortal(here.getType());
    }

    /**
     * Left-click: change which way trains may run through this piece of track.  It cycles both ways -> one way -> the
     * other way -> closed, and says in words what it just became; a switch, a crossing or a double curve steps through
     * its arms instead (`cycleBranching`, `cycleCrossing`).  The change lands on the run: clicking any tile of a
     * straight run sets the whole run.
     */
    private void cycle(TileKey tile)
    {
        TileKey target = leaderOf(tile);

        // A link has no direction of its own, so a click on one has nothing to change (its route is a stub, the same
        // side twice); the menu already declines to offer them, and the click has to decline too, or the two disagree
        // about the same square.
        if (isALink(target))
        {
            say(hint, I18n.t("autosetup.ui.infoLinkHasNoDirection"));
            return;
        }

        // NOR A SQUARE OUT OF SERVICE: its directions are kept for when it is back in service and not drawn meanwhile,
        // so a click would change something nobody could see
        if (isOutOfService(target))
        {
            say(hint, I18n.t("autosetup.ui.infoOutOfServiceHasNoDirection"));
            return;
        }

        // NOR TRACK NO STATION IS ON (OB-353): it draws no arrows, so a click there has none to change
        if (session.reachesNoStation(target))
        {
            say(hint, I18n.t("autosetup.ui.infoNoStationHasNoDirection"));
            return;
        }

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(target);

        if (routes.isEmpty()) return;

        // A TWO-SQUARE CROSSING steps through its tracks' ways rather than its arms (OB-320)
        if (routes.size() == 2 && session.getGraph().crossingPartner(target) != null)
        {
            cycleCrossing(target);
            return;
        }

        if (routes.size() > 1)
        {
            cycleBranching(target, routes);
            return;
        }

        Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> only =
            routes.entrySet().iterator().next();

        org.traincontrol.automationui.TilePorts.Route route = only.getValue();

        Direction next = after(session.directionAsDrawn(target, only.getKey()));

        int changed = session.setRunDirection(target, only.getKey(), next);

        // The tile that changed can be some way from the one clicked, at the head of a long run, so it is flashed as
        // well as named.
        if (!target.equals(tile) && onReveal != null) onReveal.accept(target);

        // Say what actually happened: a run that could not be set must not report success.
        say(hint, changed == 0 ? I18n.t("autosetup.ui.oneWayNoPath")
            : I18n.f("autosetup.ui.cycledTo", describeTile(target), describe(next, route)));

        if (changed != 0) showRestrictionsIfHidden();

        // THE ARROWS, NOT THE DIAGRAM (MT-334): see `cycle`.  A run's direction is an edge in the running graph.
        annotationsChanged();
    }

    /**
     * Left-click on a switch, a crossing or a double curve: the next combination of open and shut ARMS.  Not the next
     * of four whole-tile states: a junction's states are not four (on a crossing they are sixteen), the useful ones,
     * such as north-south open while east-west is shut, are exactly what a whole-tile cycle cannot reach, and they
     * cannot be enumerated as Direction constants (TOWARD_A names a route's own first side, which nothing makes agree
     * between branches).  So the state cycled is what the drawing already shows: one bit per ARM, saying whether a
     * train may leave through it, and the click is a plain binary counter over every combination.  The bits translate
     * back to routes exactly: a route (A,B) reads its two arms, both open is both ways, one open is one way toward it,
     * neither is closed; an arm shared by two branches (a switch's toe) is one bit, so the two branches cannot disagree
     * about it.
     */
    private void cycleBranching(TileKey target,
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes)
    {
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides = armsOf(routes);

        if (sides.isEmpty()) return;

        // Every combination, in order of how often it is wanted: everything, then each ROUTE on its own, then nothing, then all
        // the rest (on a crossing the first four read as all ways, north-south only, east-west only, nothing).
        java.util.List<Integer> states = cycleStates(routes, sides);

        // COUNTED FROM WHAT IS IN FORCE, the state the arrows and boxes show: every state in the list reads back in
        // force as itself, whereas from what is stored a closure on a permanent turnout matches no state and the click
        // would go back to the first.
        int current = armMask(target, routes, sides);

        int at = states.indexOf(current);

        int next = states.get((at + 1) % states.size());

        applyArmMask(target, routes, sides, next);

        say(hint, I18n.f("autosetup.ui.cycledSwitch", describeTile(target), armState(next, sides)));

        showRestrictionsIfHidden();

        // NOT REFRESHED HERE: the click that brought this here refreshes straight after, whatever happens, and a
        // refresh is a whole setup check, the list and the strip.
    }

    /**
     * Left-click on a square of a two-square crossing (two permanent turnouts drawn toe to toe, OB-320): the next of
     * both tracks both ways, each track one way with the other both ways, the four ways the two can each run one way,
     * and both closed.  Adam: *"Pretend these are two curved tracks on one tile, so we need to cycle through both
     * possible directions on both (4 combos)"* - both ways first and closed last, as a crossing's own first answers go;
     * and on MT-688: *"when cycling, there is no option for red in one direction and green in the other 3"* - one track
     * one way and the other open, the more often wanted, so straight after both ways.  Not the arms of
     * `cycleBranching`: the two tracks share the side the squares meet at, and one bit for it cannot say that one track
     * runs onto the other square while the other runs off it.  Counted on the crossing's north or west square whichever
     * square is clicked, so a click on either carries on from the same place; the session sets the other square's half
     * of each track with it.
     */
    private void cycleCrossing(TileKey clicked)
    {
        org.traincontrol.automationui.TileGraph graph = session.getGraph();

        TileKey other = graph.crossingPartner(clicked);

        org.traincontrol.automationui.TilePorts.Side clickedToe = graph.sideTowardNeighbour(clicked, other);

        boolean clickedLeads = clickedToe == org.traincontrol.automationui.TilePorts.Side.S
            || clickedToe == org.traincontrol.automationui.TilePorts.Side.E;

        TileKey leader = clickedLeads ? clicked : other;
        TileKey follower = clickedLeads ? other : clicked;

        org.traincontrol.automationui.TilePorts.Side toe = graph.sideTowardNeighbour(leader, follower);

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(leader);

        java.util.List<RouteId> tracks = new java.util.ArrayList<>(routes.keySet());

        if (tracks.size() != 2 || toe == null) return;

        // Onto the other square is toward the toe
        Direction[] onto = new Direction[2];
        Direction[] off = new Direction[2];

        for (int i = 0; i < 2; i++)
        {
            onto[i] = routes.get(tracks.get(i)).getA() == toe ? Direction.TOWARD_A : Direction.TOWARD_B;
            off[i] = onto[i] == Direction.TOWARD_A ? Direction.TOWARD_B : Direction.TOWARD_A;
        }

        Direction[][] states =
        {
            {Direction.BOTH, Direction.BOTH},
            // ONE TRACK ONE WAY, the other both ways (MT-688)
            {onto[0], Direction.BOTH}, {off[0], Direction.BOTH}, {Direction.BOTH, onto[1]}, {Direction.BOTH, off[1]},
            {onto[0], onto[1]}, {onto[0], off[1]}, {off[0], onto[1]}, {off[0], off[1]},
            {Direction.NONE, Direction.NONE}
        };

        Direction[] now = {graph.getDirection(leader, tracks.get(0)), graph.getDirection(leader, tracks.get(1))};

        int at = -1;

        for (int i = 0; i < states.length; i++)
        {
            if (java.util.Arrays.equals(states[i], now)) at = i;
        }

        Direction[] next = states[(at + 1) % states.length];

        Map<RouteId, Direction> wanted = new java.util.LinkedHashMap<>();

        wanted.put(tracks.get(0), next[0]);
        wanted.put(tracks.get(1), next[1]);

        session.setDirections(leader, wanted);

        String[] said = new String[4];

        for (int i = 0; i < 2; i++)
        {
            org.traincontrol.automationui.TilePorts.Side leg = routes.get(tracks.get(i)).other(toe);

            // Each end named by its corner of the crossing - or by its side, where the track runs straight on through
            // the toes: the leader is on the side its toe faces away from
            String near = end(toe.opposite(), leg);
            String far = end(toe, leg.opposite());

            boolean nearFirst = near.contains("N") || !far.contains("N") && near.contains("W");

            said[2 * i] = nearFirst ? near + "-" + far : far + "-" + near;

            said[2 * i + 1] = next[i] == Direction.BOTH ? I18n.t("autosetup.ui.dirBoth")
                : next[i] == Direction.NONE ? I18n.t("autosetup.ui.dirNone")
                : I18n.f("autosetup.ui.dirToward", next[i] == onto[i] ? far : near);
        }

        say(hint, I18n.f("autosetup.ui.cycledCrossing", describeTile(clicked), said[0], said[1], said[2], said[3]));

        showRestrictionsIfHidden();

        annotationsChanged();
    }

    /** Where a crossing's track ends: a square's side, where the track runs straight on, else its corner - NW, SE. */
    private static String end(org.traincontrol.automationui.TilePorts.Side square,
        org.traincontrol.automationui.TilePorts.Side leg)
    {
        return leg == square ? leg.toString() : corner(square, leg);
    }

    /** A corner of a square, named north or south first: NW, SE. */
    private static String corner(org.traincontrol.automationui.TilePorts.Side one,
        org.traincontrol.automationui.TilePorts.Side two)
    {
        boolean oneIsNorthOrSouth = one == org.traincontrol.automationui.TilePorts.Side.N
            || one == org.traincontrol.automationui.TilePorts.Side.S;

        return oneIsNorthOrSouth ? one.toString() + two : two.toString() + one;
    }

    /**
     * Turns a set of open arms into a direction for each route, and applies them together: the arms are what the
     * drawing shows and the user chooses between, the per-route directions what the model stores.  One translation,
     * used by the click and by the checkboxes, so the two cannot come to different conclusions about the same square.
     */
    private void applyArmMask(TileKey target,
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes,
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides, int mask)
    {
        Map<RouteId, Direction> wanted = new java.util.LinkedHashMap<>();

        for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            wanted.put(entry.getKey(), answerFor(entry.getValue(), sides, mask));
        }

        // One re-derivation for the tile, not one per branch
        session.setDirections(target, wanted);

        // THE ARROWS, NOT THE DIAGRAM (MT-334), as in `cycle`.  A direction is an edge in the running graph,
        // which annotationsChanged rebuilds as setupChanged does.
        annotationsChanged();
    }

    /** Opens or shuts one arm of a square, leaving the others as they are. */
    void setArm(TileKey target, org.traincontrol.automationui.TilePorts.Side arm, boolean open)
    {
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(target);

        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides = armsOf(routes);

        int at = sides.indexOf(arm);

        if (at < 0) return;

        int mask = armMask(target, routes, sides);

        applyArmMask(target, routes, sides, open ? mask | (1 << at) : mask & ~(1 << at));
    }

    /**
     * The direction one route stores for a set of open arms: both its arms open is both ways, one open is one way
     * toward it, neither is closed.  `applyArmMask`'s translation, and the one the click's states are checked by, so
     * the two cannot differ.
     */
    private static Direction answerFor(org.traincontrol.automationui.TilePorts.Route route,
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides, int mask)
    {
        // ONE TRANSLATION, the session's too (OB-351): every door that sets a direction stores its arms' answers
        return TileGraph.answerFor(route, sides, mask);
    }

    /**
     * Whether every route's answer for these open arms is one the hardware leaves a meaning (see `cycleStates`); true
     * when the click may store it.
     */
    private static boolean everyAnswerIsPossible(Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes,
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides, int mask)
    {
        for (org.traincontrol.automationui.TilePorts.Route route : routes.values())
        {
            if (!TileGraph.directionIsPossible(answerFor(route, sides, mask), route)) return false;
        }

        return true;
    }

    /** The masks a click steps through: everything open, each route's own arms, then everything shut. */
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

        // Then everything else: the four above are wanted most and are reached in at most four clicks, and the rest follow so
        // clicking eventually reaches every combination rather than declaring some unavailable.
        for (int mask = 1; mask < all; mask++)
        {
            if (!states.contains(mask)) states.add(mask);
        }

        // ONLY THE STATES THE CLICK STORES AS IT MEANS THEM: on a permanent turnout every route is directed at the toe,
        // so a combination opening another arm stores `BOTH` or the way toward the fork, which
        // `TileGraph.directionIsPossible` refuses and which read back in force as the toe-ward way or as closed.
        // Everything shut is always possible, so the list is never empty.
        states.removeIf(state -> !everyAnswerIsPossible(routes, sides, state));

        return states;
    }

    /**
     * The arms of a tile - every side any of its routes touches - in a fixed order so the counter always steps the same
     * way.
     */
    static java.util.List<org.traincontrol.automationui.TilePorts.Side> armsOf(
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes)
    {
        return TileGraph.armsOf(routes.values());
    }

    /**
     * Which arms are open IN FORCE, as a bitmask over armsOf(): read the way the arrows are drawn (an arm is open if
     * ANY branch through it lets a train out), so the number the counter advances from, and the boxes the menu ticks,
     * are the state the user can see.  In force rather than stored (`inForce`): a permanent turnout's routes are all
     * directed at its toe, and its untouched default `BOTH` read raw would open every arm.
     */
    int armMask(TileKey target,
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes,
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides)
    {
        int mask = 0;

        for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry
            : routes.entrySet())
        {
            org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

            Direction direction = inForce(session.getGraph().getDirection(target, entry.getKey()), route);

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
     * Says why the train on this square is not going anywhere, and draws where it could go.
     *
     * The other test asks a question about the TRACK; this asks about a locomotive and about now: every station it
     * might be sent to, and the reason each was refused (occupancy, exclusions, switched-off squares and parking rules
     * are all in the decision).  The list says why and the traces say where.  Against the layout as last LOADED, the
     * last saved configuration while this editor is open; the hint says unsaved edits are not in it, as an explanation
     * that quietly describes a different railway is worse than none.
     *
     * **NOT ON THE EVENT THREAD** (Adam: *"I would rather take it off EDT.  It's not critical now, but we want to avoid
     * these pitfalls."*).  `Layout.explainDestinations` is `synchronized` on the `Layout` (the monitor a dispatch holds
     * for the whole of `configureAndLockPath`), and asking for it here parked the event thread on that monitor (OB-192,
     * the last exception in `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor`).  **WHAT STAYS HERE IS
     * WHAT MUST**: `getStationIndex` DERIVES the square-to-Point translation when nothing has yet and
     * `layoutSource.get()` can build a `Layout`, so both are captured here on the caller's thread, as
     * `refreshCoveredTrack` does, and handed over: the worker asks nothing that can build anything.
     *
     * @param tile the square that was clicked
     * @param component what is drawn on it
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

        // Captured on the event thread: this derives when nothing has yet, and that derivation belongs to the thread
        // holding the setup.
        org.traincontrol.automationui.StationIndex index = session.getStationIndex();

        // AND WHAT THE SETUP SAYS ABOUT TURNING, ARRIVALS AND CLOSED SQUARES, captured here for the index's reason:
        // they are the setup's live collections, which the event thread edits, and a click while the answer was worked
        // out would change them under a walk iterating them.
        final WhySetup setup = new WhySetup(session);

        // WHICH TIER IT ANSWERS FOR (MT-434), read with the radio here on the event thread, and the square remembered so
        // switching Path Type can ask it again (a manual-mode answer must not say "will never be chosen in autonomy").
        final boolean byHand = pathTypeAuto != null && !pathTypeAuto.isSelected();

        lastWhyTile = tile;

        // SAID NOW, because the answer lands a beat later and a tool that goes quiet on a click reads as one that did
        // nothing; the key is the locomotive panel's, the same question asked there.
        say(hint, I18n.t("autolayout.ui.whyWorking"));

        // WHICH ASK THIS IS: two clicks in a row are two searches and the first started is not necessarily the first to
        // finish, so the answer carries its number and `paintWhy` throws away anything that is not the latest.
        long asked = whyAsked.incrementAndGet();

        whyWorking.incrementAndGet();

        try
        {
            WhyRenderer.submit(() -> workOutWhy(asked, layout, index, setup, tile, byHand));
        }
        catch (RuntimeException refused)
        {
            // Counted down before it is thrown on, so a refusal cannot leave `awaitWhy` waiting for an answer nobody is
            // working on; thrown on because the caller puts it in a dialog.
            settleWhy();

            throw refused;
        }
    }

    /**
     * Works the answer out, off the event thread, and hands it to the event thread to paint: the shape
     * `CoveredTrackRenderer` and `ReturnHomeTriageRenderer` use (the question takes the railway's monitor, the painting
     * takes the event thread).
     *
     * @param asked which ask this is, so a stale answer can be discarded
     * @param layout the railway, captured on the event thread
     * @param index the square-to-Point translation, captured with it
     * @param setup the setup's turn squares, barred sides, closed squares and reduction, captured with it
     * @param tile the square that was clicked
     * @param byHand true when Path Type is Manual
     */
    private void workOutWhy(long asked, org.traincontrol.automation.Layout layout,
        org.traincontrol.automationui.StationIndex index, WhySetup setup, TileKey tile, boolean byHand)
    {
        try
        {
            WhyAnswer answer;

            try
            {
                answer = composeWhy(layout, index, setup, tile, byHand);
            }
            catch (RuntimeException failed)
            {
                // SAID, NOT SWALLOWED: an exception on a worker reaches no catch, and the user would be left looking at
                // "Working out the reasons..." for ever; the layout being rebuilt underneath the pass is how it
                // happens.
                answer = new WhyAnswer(String.valueOf(failed.getMessage()), false, null);
            }

            WhyAnswer painted = answer;

            javax.swing.SwingUtilities.invokeLater(() -> paintWhy(asked, painted));
        }
        finally
        {
            settleWhy();
        }
    }

    /** Delegates to `EditorWhy.composeWhy`. */
    private WhyAnswer composeWhy(org.traincontrol.automation.Layout layout,
        org.traincontrol.automationui.StationIndex index, WhySetup setup, TileKey tile, boolean byHand)
    {
        return whyAnswers.composeWhy(layout, index, setup, tile, byHand);
    }

    /**
     * Puts the worker's answer in the banner and its lines on the diagram, on the event thread.  ONLY THE LATEST ANSWER
     * PAINTS: an answer to a question the user has moved on from must not replace the one they are waiting for.
     *
     * @param asked which ask this answer is about
     * @param answer what to say and what to draw
     */
    private void paintWhy(long asked, WhyAnswer answer)
    {
        if (asked != whyAsked.get()) return;

        if (answer.drawn != null)
        {
            traces.clear();
            traces.putAll(answer.drawn);
        }

        if (answer.rich) sayRich(hint, answer.message);
        else say(hint, answer.message);

        refresh();
    }

    /** Gives the why job up, from wherever it ended, and tells anybody waiting. */
    private void settleWhy()
    {
        synchronized (whySettled)
        {
            whyWorking.decrementAndGet();

            whySettled.notifyAll();
        }
    }

    /**
     * Waits until every "why is this train not moving" ask already started has been worked out: asking and reading the
     * answer are two moments, so anything that reads what it said (in practice a test) has to wait in between
     * (`TrainControlUI.awaitCoveredTrack` is the same method for the same reason).  IT WAITS FOR THE ANSWER, NOT FOR
     * THE PAINT: the paint is an `invokeLater` queued before the worker gives the job up, so a caller that wants to
     * read the banner flushes the event queue after this, and one that calls this ON the event thread waits for a paint
     * that cannot run.  BOUNDED, and it says which it did: an unbounded wait would turn a dead worker into a hung
     * window rather than a stale answer.
     *
     * @param millis how long to wait at most
     * @return true when nothing is outstanding, false when the wait ran out
     */
    boolean awaitWhy(long millis)
    {
        long deadline = System.currentTimeMillis() + millis;

        synchronized (whySettled)
        {
            while (whyWorking.get() > 0)
            {
                long left = deadline - System.currentTimeMillis();

                if (left <= 0) return false;

                try
                {
                    whySettled.wait(left);
                }
                catch (InterruptedException stopped)
                {
                    Thread.currentThread().interrupt();

                    return false;
                }
            }
        }

        return true;
    }

    /**
     * What the setup says about turning, arrivals and closed squares, read for one "why is this train not moving" ask
     * on the event thread: the answer is composed on `WhyRenderer`, and these are the setup's live collections, which
     * the event thread edits, so the worker is handed copies taken on the thread that owns them (the session's methods
     * build each of the four fresh).  The reduction is held by reference: a rebuild replaces it rather than editing it,
     * so the one captured goes on describing the setup the copies came from.
     */
    static final class WhySetup
    {
        /** The squares where a run must turn round. */
        final java.util.Set<TileKey> mustTurn;

        /** The squares where a run may turn round, less the ones where it must. */
        final java.util.Set<TileKey> mayTurn;

        /** The sides each station refuses arrivals by. */
        final java.util.Map<TileKey, java.util.Set<org.traincontrol.automationui.TilePorts.Side>> barred;

        /** The squares switched off. */
        final java.util.Set<TileKey> shut;

        /** The reduction the routes are walked on, or null when there is none. */
        final org.traincontrol.automationui.GraphReducer reducer;

        /** Reads all five from the session, on the caller's thread, which has to be the event thread. */
        private WhySetup(AutonomySession session)
        {
            this.mustTurn = session.mandatoryTurnTiles();
            this.mayTurn = session.mayTurnTiles();
            this.barred = session.barredArrivals();
            this.shut = session.shutTiles();
            this.reducer = session.getReducer();
        }
    }

    /**
     * What one "why is this train not moving" ask worked out: a message, and the lines to draw.  A value object handed
     * from the worker to the event thread, everything it is made of finished before the event thread sees any of it.
     */
    static final class WhyAnswer
    {
        /** The report, mark-up included when `rich`, plain text otherwise. */
        private final String message;

        /** Whether the message carries mark-up this class composed, or is a plain sentence. */
        private final boolean rich;

        /**
         * The lines to lay on the diagram, or null to leave whatever is drawn there alone (not the same as empty): an
         * answer about a train replaces the traces, one that is not about a train leaves them.
         */
        private final java.util.Map<TileKey,
            java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> drawn;

        WhyAnswer(String message, boolean rich, java.util.Map<TileKey,
            java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> drawn)
        {
            this.message = message;
            this.rich = rich;
            this.drawn = drawn;
        }
    }

    /**
     * The one thread the "why is this train not moving" answer is worked out on.  DAEMON, and it goes away when idle:
     * this panel is built and thrown away every time the editor is opened and every tile menu builds one, so a held
     * core thread each would be a thread per editor opened (`allowCoreThreadTimeOut`).  NOT COALESCED, unlike the
     * window's renderers, which refresh on every railway event: this one is a click, and two clicks are two questions,
     * the second of which must win (`whyAsked`).
     */
    private final java.util.concurrent.ThreadPoolExecutor WhyRenderer = whyRenderer();

    /** Which ask is the current one, so a slow answer to an abandoned question is discarded. */
    private final java.util.concurrent.atomic.AtomicLong whyAsked =
        new java.util.concurrent.atomic.AtomicLong();

    /** How many asks have been started and not yet worked out, for `awaitWhy`. */
    private final java.util.concurrent.atomic.AtomicInteger whyWorking =
        new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Announced on whenever an ask is finished with, for `awaitWhy`; the count is changed under this monitor, so a
     * waiter cannot read it, find an ask outstanding, and sleep in the instant between the worker clearing it and
     * announcing it.
     */
    private final Object whySettled = new Object();

    /**
     * Builds the pool above: a method rather than an initialiser, as `allowCoreThreadTimeOut` is called on the pool
     * after it is constructed.
     */
    private static java.util.concurrent.ThreadPoolExecutor whyRenderer()
    {
        java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 10, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<Runnable>(), runnable ->
            {
                Thread worker = new Thread(runnable, "WhyRenderer");

                worker.setDaemon(true);

                return worker;
            });

        pool.allowCoreThreadTimeOut(true);

        return pool;
    }

    /**
     * Asks whether a train could get from one sensor to another, and shows the route: two clicks, like portal pairing,
     * the answer from the same reduction everything else uses, so what this says is what a running train would find.
     */
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

        // Both ways, always: asking the user to nominate a direction only makes them run the test twice.  The squares
        // trains may turn round at are handed over, as everywhere else the run may not leave by the side it came in at;
        // MAY, not must, as the build emits no straight-through copy for a must-turn square.  The same turn sets the
        // reachability check uses, so the path test and the findings panel cannot disagree.
        java.util.Set<TileKey> mustTurn = session.mandatoryTurnTiles();

        java.util.Set<TileKey> mayTurn = session.mayTurnTiles();

        // The red arrows, from the same place the build reads them (OB-120).
        java.util.Map<TileKey, java.util.Set<org.traincontrol.automationui.TilePorts.Side>> barred =
            session.barredArrivals();

        java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> there =
            session.getReducer() == null ? null
                : session.getReducer().findPath(testFrom, tile, mayTurn, mustTurn, barred,
                    session.shutTiles());

        java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> back =
            session.getReducer() == null ? null
                : session.getReducer().findPath(tile, testFrom, mayTurn, mustTurn, barred,
                    session.shutTiles());

        traces.clear();

        // Both directions, each as its own line: a direction with no path draws nothing, so "can it get there, can it get back" is
        // read off the track.  Each leg is told where it ENDS, which decides its colour (a run to a station autonomy will never
        // choose is drawn in a third shade).
        trace(traces, there, testFrom, true, tile);
        trace(traces, back, tile, false, testFrom);

        // AND WHETHER THE TIER IN THE RADIO WOULD ACTUALLY GO THERE: a path existing and a train being sent along it
        // are different facts.  `stationsAutonomyWillNotChoose` is the runtime's rule asked of the diagram.  TWO
        // SENTENCES: a manual-only station is a preference about what autonomy picks, and a person may still drive
        // there; a square OUT OF SERVICE binds every tier, so telling that operator to switch Path Type to Manual sends
        // them to a shut door.  The shut sentence is said on both tiers, the manual-only one on Auto alone, and as well
        // as the route, not instead of it: the track is passable.
        String tierNote = "";

        if (session != null && session.shutTiles().contains(tile))
        {
            tierNote = "<br>" + escape(I18n.f("autosetup.ui.testSquareIsOutOfService",
                describeTile(tile)));
        }
        else if (pathTypeAuto != null && pathTypeAuto.isSelected() && session != null
            && session.manualOnlyStations().contains(tile))
        {
            tierNote = "<br>" + escape(I18n.f("autosetup.ui.testNotAnAutoDestination",
                describeTile(tile)));
        }

        // Named, not just counted: "3 runs" says a route exists and nothing about which one; the sensors it calls at are
        // the short way to say where it went.
        sayRich(hint, I18n.f("autosetup.ui.testBothWays",
            escape(describeTile(testFrom)), escape(describeTile(tile)),
            leg(there), escape(via(there)), leg(back)) + tierNote);

        lastTestFrom = testFrom;
        lastTestTo = tile;

        testFrom = null;
    }

    /**
     * Runs the last path test again, for the tier the radio now says (MT-399): only while the test tool is still what
     * is armed and no new test is half-way through, as a radio click is not a reason to draw a route over whatever the
     * user moved on to.
     */
    private void retestForTheNewTier()
    {
        // AND THE WHY ANSWER (MT-434) follows Path Type too: the square last asked about is asked again through the door
        // the click uses, so the answer on screen and the radio cannot disagree.
        if (tool == Tool.WHY && lastWhyTile != null)
        {
            try
            {
                applyWhy(lastWhyTile, componentAt(lastWhyTile));
            }
            catch (RuntimeException e)
            {
                JOptionPane.showMessageDialog(owner(), String.valueOf(e.getMessage()));
            }

            refresh();

            return;
        }

        if (tool != Tool.TEST || testFrom != null || lastTestFrom == null || lastTestTo == null) return;

        TileKey from = lastTestFrom;
        TileKey to = lastTestTo;

        testFrom = from;

        applyTest(to, componentAt(to));

        refresh();
    }

    static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    private String leg(java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run)
    {
        return run == null ? I18n.t("autosetup.ui.testLegBlocked")
            : I18n.f("autosetup.ui.testLegReachable", run.size());
    }

    /**
     * Lays one leg of a tested route onto the squares it crosses, **INTO A MAP IT IS GIVEN** rather than into `traces`,
     * which the paint reads: a caller on a worker builds its lines in a map of its own and the event thread installs
     * them, while the path test, on the event thread, passes `traces` itself.
     *
     * @param into where the lines go
     * @param run the reduced path, or null when there is no route that way
     * @param from the square it leaves
     * @param forward whether this is the outbound leg
     * @param to the square it ENDS at, which decides whether the leg is drawn as manual-only
     */
    void trace(java.util.Map<TileKey,
        java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> into,
        java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run,
        TileKey from, boolean forward, TileKey to)
    {
        if (run == null) return;

        // ASKED ONCE, FOR THE WHOLE LEG: every square of the run carries the answer, as a colour only on the last
        // square would be invisible on a route running off the page.  `manualOnlyStations` AND NOT
        // `stationsAutonomyWillNotChoose` (the colour is for *"going to manual-only points"*): a square OUT OF SERVICE
        // is in the runtime set too but is no manual-only point, since no tier may send anything there.
        // `!isReversing()` is about a COPY, and a may-reverse square keeps a plain copy autonomy can choose (OB-195).
        final boolean manualOnly = to != null && session != null
            && session.manualOnlyStations().contains(to);

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

            // Which way the line enters and leaves, from the squares either side; null at the ends of the run, where
            // the line stops in the middle of the square rather than running off into track nobody asked about.
            org.traincontrol.automationui.TilePorts.Side in =
                i == 0 ? null : towards(at, seq.get(i - 1));

            org.traincontrol.automationui.TilePorts.Side out =
                i == seq.size() - 1 ? null : towards(at, seq.get(i + 1));

            java.util.List<org.traincontrol.automationui.TileAnnotation.Trace> here = into.get(at);

            if (here == null)
            {
                here = new java.util.ArrayList<>();
                into.put(at, here);
            }

            here.add(new org.traincontrol.automationui.TileAnnotation.Trace(in, out, forward,
                manualOnly));
        }
    }

    /**
     * Which side of one square faces another, or null when they are not neighbours (a jump through a link has no side
     * on the grid to draw it as).  The running diagram lays its line across the same grid, so the answer lives with the
     * grid.
     */
    private org.traincontrol.automationui.TilePorts.Side towards(TileKey from, TileKey to)
    {
        return org.traincontrol.automationui.TileGraph.gridSideTowards(from, to);
    }

    /**
     * The sensors a route calls at, in order, excluding the two ends: the names of the reduction's edges between
     * Points, which makes a disputed route checkable (a line drawn across a page can be followed wrongly, a list of
     * places cannot).
     */
    private String via(java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run)
    {
        if (run == null || run.size() < 2) return "";

        java.util.List<String> names = new java.util.ArrayList<>();

        for (int i = 0; i < run.size() - 1; i++)
        {
            names.add(describeTile(run.get(i).getEnd()));
        }

        // The space belongs HERE, not on the front of the message: Properties strips leading whitespace from a value,
        // so " via {0}" would load as "via {0}" and run into the word before it.
        return " " + I18n.f("autosetup.ui.testVia", String.join(", ", names));
    }

    /**
     * The address written on a signal, which is how anybody refers to one: describeTile falls back to a grid
     * coordinate, not on the tile and not in the accessory list, where the LOGICAL address is both.
     *
     * @param tile the signal's square
     */
    String addressOf(TileKey tile)
    {
        LayoutDiagramComponent component = componentAt(tile);

        return component == null ? describeTile(tile) : String.valueOf(component.getAddress());
    }

    /**
     * What a square is, for a message that would otherwise be a coordinate.
     */
    String describeTile(TileKey tile)
    {
        return session.describeTile(tile);
    }

    public Tool getTool()
    {
        return tool;
    }

    public boolean isShowingDirections()
    {
        int view = directionsView();

        return view == 0 || view == 1 || view == VIEW_ALLOWED;
    }

    /**
     * The view the directions box is on, by its meaning (`DIRECTIONS_BY_ENTRY`).
     *
     * @return 0 Show All, 1 Restrictions only, 2 Hide All, `VIEW_ARRIVALS` or `VIEW_ALLOWED`
     */
    private int directionsView()
    {
        int entry = directions.getSelectedIndex();

        return entry < 0 || entry >= DIRECTIONS_BY_ENTRY.length ? DIRECTIONS_DEFAULT : DIRECTIONS_BY_ENTRY[entry];
    }

    /** Puts the directions box on a view, by its meaning - the default for one it does not have. */
    private void showDirectionsView(int view)
    {
        int entry = -1;
        int fallback = 0;

        for (int i = 0; i < DIRECTIONS_BY_ENTRY.length; i++)
        {
            if (DIRECTIONS_BY_ENTRY[i] == view) entry = i;

            if (DIRECTIONS_BY_ENTRY[i] == DIRECTIONS_DEFAULT) fallback = i;
        }

        directions.setSelectedIndex(entry >= 0 ? entry : fallback);
    }

    /**
     * Whether the diagram is currently being used to point at a signal: while the protecting-signals window is up, and
     * while the click it hands back is being waited for (OB-040: *"while the window is open, de-clutter the diagram as
     * much as possible so users can clearly see the signals.  turn off arrows, labels, etc."*).  Arrows and lengths are
     * conditional on this as the greying is, since the one gesture that asks somebody to FIND a particular square must
     * not leave the two things most likely to cover it on.
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

    /** Whether there are edits the user has not saved - what decides whether closing has to ask. */
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
     * What the editor should draw over one square: its routes and their directions, its length, and whether it is part
     * of the bulk selection.  Computed per tile on request rather than published as a map, as the editor already walks
     * its own grid to redraw and knows exactly which page is showing, which this panel does not.
     *
     * @return the annotation, possibly blank, never null
     */
    public org.traincontrol.automationui.TileAnnotation annotationFor(TileKey tile)
    {
        java.util.List<org.traincontrol.automationui.TileAnnotation.Mark> marks = new java.util.ArrayList<>();

        boolean ignored = isIgnored(tile);

        // TRACK NO STATION IS ON is greyed and draws no arrows (OB-353) - drawn so only: `isIgnored` does not ask it, so
        // every other edit still works there, and its lengths and outlines are still drawn.
        boolean stationless = !ignored && session.reachesNoStation(tile);

        // Everything that is not a signal gets out of the way (OB-040): greyed, and below, no arrows and no lengths.
        // Asked once and used three times, as a de-clutter on for one of the three and off for the others looks
        // half-dressed.
        final boolean focused = isFocusedOnSignals();

        // While a signal is being picked, everything that is not one is greyed so the squares it could mean are the
        // only ones left in colour: the inverse of grey's usual meaning (a signal is normally the greyed thing), so the
        // two states cannot be on screen at once.  Nor is anything greyed for its track meanwhile (OB-353).
        if (focused) { ignored = !isPairableSignal(componentAt(tile)); stationless = false; }

        // A link autonomy has been told to ignore carries no trains, so no arrows either: its route is still a stub the
        // port map knows about, and drawn from that it would keep an arrow saying traffic could leave through it.
        LayoutDiagramComponent atTile =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        if (atTile != null && org.traincontrol.automationui.TilePorts.hasPortal(atTile.getType())
            && session.getStore().isPortalDisabled(tile))
        {
            ignored = true;
        }

        // A tile that merely follows its run draws no arrows of its own, so the run reads as one decision made at one
        // end rather than eleven waiting to be made.  It is NOT shaded: grey means autonomy cannot use a square, and a
        // follower is usable.
        boolean follower = isFollower(tile);

        if (isShowingDirections() && !focused && session.getGraph() != null && !ignored && !follower && !stationless)
        {
            // Asked of the SESSION (FR-037), so this window and the ordinary track diagram cannot disagree about which way a piece of
            // track runs.  Every state is drawn, both-ways included: drawn as nothing it would look like a square nobody had touched.
            // The run marker below keeps a bare layout from being a field of arrows.
            marks.addAll(session.directionMarks(tile));

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

        // Only the square a test is waiting on borrows the selection outline now: the route itself is drawn as a line
        // through the track.  The paired signal borrows the outline too, while its menu item is being acted on, because
        // an address names the signal but does not say where it is.
        boolean outlined = isOutlined(tile);

        // In the arrivals view every station shows every side it has, so the setting can be READ.  Shaded from the SAME
        // answer everything else here was decided from, not `isDimmed`, which works isIgnored out again and loses both
        // refinements above.  An empty square is never shaded: shading says "autonomy cannot use this piece of track",
        // and an empty square is not one.
        boolean shaded = ignored && componentAt(tile) != null;

        org.traincontrol.automationui.TileAnnotation annotation =
            new org.traincontrol.automationui.TileAnnotation(marks, length, outlined,
            badgeFor(tile), shaded, isCurved(tile), isPairedPortal(tile),
            traces.get(tile), directionsView() == 1,
            ignored ? null
                : session.arrivalMarks(tile, directionsView() == VIEW_ARRIVALS))
            // FR-118's icons: every point's icon white with its colour round it (TileAnnotation.inTheEditor), here and
            // nowhere else, because this is the surface the direction arrows are read on and they are drawn over the icon.
            .inTheEditor();

        // NONE WHERE A CROSSING'S SQUARES MEET, as on the track diagram (OB-322)
        session.withoutTheCrossingsMiddle(tile, annotation);

        // GREYED as a square autonomy takes no notice of is, where no station is on its track (OB-353)
        if (stationless && !ignored && componentAt(tile) != null) annotation.withNoStation();

        // ALLOWED ONLY: the green arrows, and none of the red
        if (directionsView() == VIEW_ALLOWED) annotation.withoutRestrictions();

        // A train the setup puts here gets a mark of its own: the caption says which train, but it is on another square,
        // sometimes on none, and can be switched off.  NOT WITH SHOW TRAINS OFF: the mark is one of the trains
        // the switch hides.
        if (locomotiveAt(tile) != null && isShowingTrains()) annotation.withTrain();

        // UNMEASURED TRACK: every square a length rule reads that has no length, asked of the session's one answer so
        // the highlight and Mass Assign Lengths cannot disagree about which squares those are.
        if (showUnmeasured.isSelected())
        {
            if (unmeasuredSquares == null) unmeasuredSquares = session.squaresNeedingALength();

            if (unmeasuredSquares.contains(tile)) annotation.needsALength();
        }

        return annotation;
    }

    /**
     * Whether this square is a link that has been paired with another.  Unpaired links are left alone: a two-way door
     * drawn on one that leads nowhere would promise a route that does not exist, and the missing pairing has a finding
     * of its own.
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
     * Whether this square's track is drawn as a chord cutting a corner: curves and double curves only, by TYPE rather
     * than by geometry (a switch's diagonal arm is a chord too, and the tilt was asked for on curves alone).
     */
    private boolean isCurved(TileKey tile)
    {
        // THE SENSOR CURVES TOO, as on the track diagram (MT-712): a curve's arrows are aimed from the middle of its
        // track, along its chord, with the flag or without; what the flag still decides is a double curve's OTHER road,
        // whose arrows would otherwise be aimed from the badge's road.
        return session.isCurveSquare(tile);
    }

    /**
     * The answers for one route, with the one-way options named by where they lead rather than by an A and a B nobody
     * can see.  **ONLY THE ONES THE HARDWARE LEAVES ANY MEANING** (Adam: *"green arrows in the direction that can't be
     * chosen shouldn't be offered"*): an authored direction ANDs with the tile's own restriction, so on a permanent
     * turnout (all routes directed at the toe) "toward the fork" and "both ways" change no train's road, and offering
     * them would draw a green arrow for a road no train can take; `TileGraph.directionIsPossible` is the question.
     * **The tick follows what is IN FORCE, not what is stored**: `getDirection` answers `BOTH` for these tiles by
     * default (a default of "out of the toe" would AND with the blades to leave the tile impassable), and `BOTH` is not
     * an option here, so the radio group would show nothing selected.  Where only one way is possible, that one is
     * ticked.
     */
    List<javax.swing.JMenuItem> directionItems(final TileKey tile, final RouteId routeId,
        org.traincontrol.automationui.TilePorts.Route route)
    {
        List<javax.swing.JMenuItem> items = new java.util.ArrayList<>();

        // WHAT IS IN FORCE, not what is stored - `inForce`, which the arm boxes beside these submenus ask
        // too, so the two cannot tick different ways.
        Direction current = inForce(session.directionAsDrawn(tile, routeId), route);

        if (TileGraph.directionIsPossible(Direction.BOTH, route))
        {
            items.add(directionItem(tile, routeId, Direction.BOTH,
                I18n.t("autosetup.ui.menuRouteBoth"), current));
        }

        if (TileGraph.directionIsPossible(Direction.TOWARD_A, route))
        {
            items.add(directionItem(tile, routeId, Direction.TOWARD_A,
                I18n.f("autosetup.ui.menuRouteToward", String.valueOf(route.getA())), current));
        }

        if (TileGraph.directionIsPossible(Direction.TOWARD_B, route))
        {
            items.add(directionItem(tile, routeId, Direction.TOWARD_B,
                I18n.f("autosetup.ui.menuRouteToward", String.valueOf(route.getB())), current));
        }

        items.add(directionItem(tile, routeId, Direction.NONE,
            I18n.t("autosetup.ui.menuRouteNone"), current));

        return items;
    }

    /**
     * The answer in force on a route: what is stored, narrowed by the hardware.  **TWO CASES, NOT ONE**: a stored
     * answer the editor does not offer on this route (`TileGraph.directionIsPossible`) is either the default `BOTH` on
     * a permanent turnout, which the blades narrow to the one road there is, or an answer that permits only an entry
     * the blades refuse, which is a CLOSURE; reading "the possible way" for both would show a shut road as open.  One
     * answer for the branch submenus' tick and the arm boxes': the boxes read the stored direction, and on an untouched
     * permanent turnout would tick every arm while the submenus ticked the toe-ward way alone.
     *
     * @param stored what `TileGraph.getDirection` answers - the authored direction, or the default
     * @param route the route
     * @return the direction trains actually have on it
     */
    private static Direction inForce(Direction stored, org.traincontrol.automationui.TilePorts.Route route)
    {
        return TileGraph.inForce(stored, route);
    }

    private javax.swing.JMenuItem directionItem(final TileKey tile, final RouteId routeId,
        final Direction direction, String text, Direction current)
    {
        javax.swing.JRadioButtonMenuItem item =
            new javax.swing.JRadioButtonMenuItem(text, direction == current);

        item.addActionListener(e ->
        {
            // NOT WHILE TRAINS RUN - see `refusedWhileRunning`
            if (refusedWhileRunning()) return;

            // Set on the run, not the tile: a run of plain track has one direction, and setting it a
            // tile at a time is both busywork and a way to end up with a run that contradicts itself.
            int set = session.setRunDirection(tile, routeId, direction);

            if (set == 0)
            {
                say(hint, I18n.t("autosetup.ui.oneWayNoPath"));
            }

            refresh();

            // IN THE LAMBDA, beside the write, not at the end of the builder: a run's direction is an edge in the
            // running graph, and this per-route radio must reach the railway as the bulk "All branches" answer does.
            // Through the LIGHT door, like its three siblings (MT-334), see `cycle`.
            annotationsChanged();
        });

        return item;
    }

    /** What this sensor has been designated as, or null when it is not a station. */
    private org.traincontrol.automationui.TileAnnotation.Badge badgeFor(TileKey tile)
    {
        // Every sensor that made it into the graph gets a badge, not only the stations: a plain point is a decision not to
        // make a station, and should look like one.
        if (session.getReducer() == null
            || !session.getReducer().getPoints().containsKey(tile)) return null;

        String name = session.getStore().getPointName(tile);

        // the road its contact is on, as on the track diagram (MT-712)
        org.traincontrol.automationui.TilePorts.Route route = session.stationRoad(tile);

        boolean station = session.getStore().isStation(tile);

        // Read off the three switches, not off the flags they compile to, which are derived at build time and never
        // authored.  A station that turns trains round wears the terminus badge and anything else that does wears the
        // reversing one.
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
            turns && !session.isMustTurnAround(tile),
            Boolean.FALSE.equals(session.getPointProperty(tile, "active")),
            // which way a station's track runs out, for the terminus's flat end - as the diagram's badge (FR-118)
            station ? session.deadEndSide(tile) : null);
    }

    /**
     * Re-derives what the MENU needs to bind an item to the right square, and announces nothing: `refresh()` ends in
     * `onChanged.run()`, which on the track diagram's own menu saves the setup file and rebuilds the static autonomy
     * layer, so calling it before every right-click would run the whole findings pass and write the file twice per
     * menu.  The menu needs only the run map: which squares belong to a run, and which speaks for each.
     */
    public void refreshRunLeaders()
    {
        runLeaders = session.runLeaders();
    }

    /**
     * What Segment Length shows for this square: what the whole run measures, not one square's share.  **A run of plain
     * track has one square that speaks for it** (`behaviour.md` section 5b), and the walk shares a piece's whole length
     * over every square it covers, so after Mass Assign Lengths the leader holds a share, a 1 or a 2 on a run measured
     * as 7, and this dialog would open on that share as if it were the run's length.
     *
     * @param tile any square of the run
     * @return the sum over every square the run covers
     */
    public int lengthShownFor(TileKey tile)
    {
        int total = 0;

        for (TileKey square : runTilesOf(tile)) total += Math.max(0, session.getStore().getTileLength(square));

        return total;
    }

    /**
     * Gives a run the length typed for it: the leader holds it, and the squares that follow hold nothing.  The other
     * half of `lengthShownFor`: writing the number to the leader alone would leave the shares Mass Assign Lengths
     * spread over the followers in place, so the run would measure the number plus whatever the walk left on the rest;
     * 0 is what "no length" is stored as everywhere here.
     *
     * @param tile any square of the run
     * @param length the whole run's length
     */
    public void setRunLength(TileKey tile, int length)
    {
        TileKey leader = leaderOf(tile);

        // ONE RE-DERIVATION FOR THE WHOLE RUN: writing square by square pays for a new graph, reduction and station
        // index per square, on the event thread.
        java.util.Map<TileKey, Integer> lengths = new java.util.LinkedHashMap<>();

        for (TileKey square : runTilesOf(tile))
        {
            lengths.put(square, square.equals(leader) ? length : 0);
        }

        session.setTileLengths(lengths);

        // AND THE RUNNING LAYOUT IS TOLD, because this is a door in its own right (public, and the track diagram's panel
        // has no Save), and lengths are what the room rule measures with (MT-246).
        setupChanged();
    }

    /**
     * Every square of the run this one belongs to - itself when it is in no run: `runLeaders` maps each follower to its
     * leader, so the run is this square's leader together with every key that answers with it.
     *
     * @return the squares of its run, the leader included
     */
    private java.util.Set<TileKey> runTilesOf(TileKey tile)
    {
        TileKey leader = leaderOf(tile);

        java.util.Set<TileKey> run = new LinkedHashSet<>();

        run.add(leader);

        for (java.util.Map.Entry<TileKey, TileKey> entry : runLeaders.entrySet())
        {
            if (leader.equals(entry.getValue())) run.add(entry.getKey());
        }

        return run;
    }

    /** Whether this tile takes its direction from another one, rather than carrying its own. */
    private boolean isFollower(TileKey tile)
    {
        // THE SESSION'S ANSWER, which the track diagram asks too (OB-361)
        return session.followsItsRun(tile);
    }

    /**
     * The tile that actually gets set when the user acts on this one.
     */
    TileKey leaderOf(TileKey tile)
    {
        TileKey leader = runLeaders.get(tile);

        return leader == null ? tile : leader;
    }

    /**
     * Whether autonomy takes no notice of this square, so nothing here is the user's to decide: the page is excluded,
     * the tile type cannot be routed over (turntables, scissors), or the tile carries whatever line it sits on rather
     * than a line of its own (route buttons, INFERRED from the neighbours and so not something to click).  The drawing
     * does not distinguish them: the answer to all three is to leave it alone.
     */
    boolean isIgnored(TileKey tile)
    {
        if (session.getStore().getExcludedPages().contains(tile.getPage())) return true;

        org.traincontrol.base.LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        // A blank square is not track, so there is nothing to set on it.
        if (component == null) return true;

        // A lamp is decoration with no track, so it is greyed rather than left looking like something to configure.
        if (component.getType() == LayoutDiagramComponent.componentType.LAMP) return true;

        // A ROUTE BUTTON ON A RUNNING LINE IS TRACK (OB-332; Adam: *"We need the direction arrows to be editable and clickable."*):
        // the graph runs the line through it (`TileGraph.transparentRoutes`), so a direction is stored on it and its arrow drawn
        // there.  Only a button with no track through it is greyed.  A click sets the button alone: it is not part of a run.
        if (org.traincontrol.automationui.TilePorts.isTransparent(component.getType()))
        {
            return session.getRoutes(tile).isEmpty();
        }

        return org.traincontrol.automationui.TilePorts.isDisqualified(component.getType());
    }

    /**
     * The diagram square at a key, read from the PAGE rather than the graph: the graph omits excluded pages, so asking
     * it whether a square on such a page is blank always answers yes and everything there would stop being shaded.
     */
    org.traincontrol.base.LayoutDiagramComponent componentAt(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagram page = pageOf(tile);

        return page == null ? null : page.getComponent(tile.getX(), tile.getY());
    }

    /** The page a key names, or null if the session has never heard of it. */
    org.traincontrol.base.LayoutDiagram pageOf(TileKey tile)
    {
        for (org.traincontrol.base.LayoutDiagram diagram : session.getPages())
        {
            if (diagram.getName().equals(tile.getPage())) return diagram;
        }

        return null;
    }

    /**
     * Told when only the ANNOTATIONS need redrawing - the arrows, not the tiles under them (OB-185: *"changing any
     * pathing arrow in the track autonomy editor makes the whole screen flicker."*): a direction restriction changes no
     * tile art, and the one redraw door rebuilt the grid, remaking every label.
     * `LayoutEditor.refreshAutonomyAnnotations` sets annotations on the labels that already exist.  Null when the panel
     * is not inside an editor, in which case `onDiagramChanged` is the only door there is.
     */
    private Runnable onAnnotationsChanged;

    /** @param listener what to run when only the arrows need redrawing */
    public void setOnAnnotationsChanged(Runnable listener)
    {
        this.onAnnotationsChanged = listener;
    }

    /**
     * A change that moves the arrows and nothing else (OB-185): the running layout is still rebuilt (a one-way
     * restriction is a real change to the graph) but the tiles are left alone and only the annotations over them
     * re-set.  Falls back to the full redraw when there is no annotation door, so a caller cannot silently draw
     * nothing.
     */
    private void annotationsChanged()
    {
        // THE WASH DEPENDS ON THE DIRECTIONS THIS DOOR CHANGES (MT-455): a run clicked through to closed has no leg in the
        // reduction, so its squares stop being unmeasured track and the Unmeasured Track wash must leave them; this door, unlike
        // `setupChanged`, would otherwise leave the cache filled before the click.
        unmeasuredSquares = null;

        if (onAnnotationsChanged == null)
        {
            setupChanged();

            return;
        }

        onAnnotationsChanged.run();

        rebuildRunningLayoutSoon();
    }

    /**
     * Says that the setup changed: rebuild the railway it describes, and redraw.  **The thing every door has to do**
     * (MT-246: a home set from the diagram left "Return Locomotives Home" greyed and was not persisted on closing).
     * `rebuildRunningLayoutFromSetup` tells the running `Layout` about the edit, and every affordance that asks about
     * homes, lengths, priorities or usage reads the **running** layout rather than the setup; the exit path captures
     * the RUNNING layout back into the configuration (`TrainControlUI.saveState`), and `captureFromLayout` walks
     * `POINT_OPERATIONAL_KEYS` writing what the layout has and **removing what it does not**, so an edit the layout
     * never heard of would be saved and then deleted on the way out.  **It does NOT save**: `onDiagramChanged` is wired
     * by `TrainControlUI` to `repaintLayout()`; the save is the panel's other runnable, `onChanged`, which `refresh()`
     * calls at its end, and `item()` calls `refresh()` after every menu action.  **One exit rather than a line in each
     * door**, because a copy per door is how an eighth door comes to lack one; `testEditorSurfaceRules` fails if a
     * writer does not end here.
     */
    void setupChanged()
    {
        // THE HIGHLIGHT'S SQUARES ARE FORGOTTEN HERE AS WELL AS IN refresh(): Control+E writes a length through
        // applyLength and arrives here without running refresh().
        unmeasuredSquares = null;

        if (onDiagramChanged != null) onDiagramChanged.run();

        // ONE REBUILD PER GESTURE, NOT ONE PER WRITE: `rebuildRunningLayoutFromSetup` is a full configuration load and
        // layout regeneration on the event thread, and doors nest (picking "Can stop" runs `setUsage`, which calls
        // `setStation`, and the radio's listener then calls `placementChanged`).  Coalesced rather than pushed back
        // onto the callers, as "announce, unless your caller will" leaves silent doors; it also means no rebuild sees a
        // HALF-APPLIED edit.
        rebuildRunningLayoutSoon();
    }

    /**
     * The rebuild half of a setup change, coalesced - one per gesture, not one per write; split out so an
     * annotations-only change can ask for it without a grid rebuild (OB-185).
     */
    private void rebuildRunningLayoutSoon()
    {
        if (setupChangePending) return;

        setupChangePending = true;

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            setupChangePending = false;

            // ASKING TO BE TOLD IF IT IS DECLINED: the edit was made against a railway that was not running, and the
            // rebuild is posted one event later, so a run starting in between throws the edit away;
            // `TrainControlUI.autonomyEditorClosed()` passes false because it has already said it.  AND WHOSE
            // PLACEMENTS THIS GESTURE EDITED are drained so each edit is claimed once: the rebuild is coalesced, so
            // names accumulate until it happens.
            java.util.Set<String> edited = new java.util.LinkedHashSet<>(placementsJustEdited);

            placementsJustEdited.clear();

            if (parentWindow() != null) parentWindow().rebuildRunningLayoutFromSetup(true, edited);
        });
    }

    /** Set between a setup change and the single rebuild it coalesces into. */
    private boolean setupChangePending;

    /**
     * Locomotives whose placement this panel has written into the setup since the last rebuild (see
     * `placementChanged`); held, not passed straight through, because the rebuild is coalesced.
     */
    private final java.util.Set<String> placementsJustEdited = new java.util.LinkedHashSet<>();
    /**
     * Re-reads the setup and shows what it says, after every edit: affordable because the derivation is cheap, and the
     * alternative, a panel that agrees with itself while the graph has moved on, is the failure this design exists to
     * avoid.
     */
    public final void refresh()
    {
        flowMarks = session.flowMarks();

        // Forgotten rather than recomputed: worked out again the first time a square asks, and only if the display is
        // on - so a refresh with the highlight off costs nothing.
        unmeasuredSquares = null;
        runLeaders = session.runLeaders();

        // The box says what the setup says, so that opening the editor on a page already left out shows
        // it ticked rather than inviting the user to tick it again.
        boolean ignored = page != null && session.getStore().getExcludedPages().contains(page);

        if (excludePage != null) excludePage.setSelected(ignored);

        // Nothing else in this column can do anything on a page autonomy takes no notice of; greyed rather than hidden, so
        // the column keeps its shape.
        if (testButton != null) testButton.setEnabled(!ignored);
        if (whyButton != null) whyButton.setEnabled(!ignored);

        // AND THE PATH TYPE THEY TEST BY (Adam, FR-105: "also disable/grey out the auto/manual radio buttons")
        if (pathTypeLabel != null) pathTypeLabel.setEnabled(!ignored);
        if (pathTypeAuto != null) pathTypeAuto.setEnabled(!ignored);
        if (pathTypeManual != null) pathTypeManual.setEnabled(!ignored);

        // AND ONE-WAY RUN, the third of them (OB-235): a run closed one way on a page nothing is built from closes
        // nothing.  Put down if it was armed when the page was left out - here rather than through cancelPendingGesture,
        // which refreshes.
        if (oneWayButton != null)
        {
            oneWayButton.setEnabled(!ignored);

            if (ignored && tool == Tool.ONE_WAY)
            {
                tool = Tool.NONE;
                oneWayFrom = null;
                oneWayButton.setSelected(false);

                // AND ITS PROMPT, which asked for the first square, as putting a tool down says.
                say(hint, I18n.t("autosetup.ui.hintClickToCycle"));
            }
        }

        findingsModel.clear();
        findingTiles.clear();
        findingRelated.clear();
        findingSeverity.clear();

        // Split into what must be fixed and what is only worth checking.  The WHOLE layout, not only this page: a setup refuses to
        // load because of a problem anywhere, and the count on the diagram counts the layout.  Rows about elsewhere say where, and
        // clicking one goes there.
        List<Object[]> errorRows = new java.util.ArrayList<>();
        List<Object[]> warningRows = new java.util.ArrayList<>();
        List<Object[]> noticeRows = new java.util.ArrayList<>();

        // The same three, for everywhere that is not the page in front of the reader: this window can act on one page, so
        // "here" and "not here" is the first thing a reader needs to know about a row.
        List<Object[]> elsewhereErrors = new java.util.ArrayList<>();
        List<Object[]> elsewhereWarnings = new java.util.ArrayList<>();
        List<Object[]> elsewhereNotices = new java.util.ArrayList<>();

        // The graph's own problems are NOT gathered separately: AutonomyChecks.run already copies every one into its
        // findings, and listing them again would show each twice and make the count disagree with the diagram's.
        for (AutonomyChecks.Finding finding : session.check())
        {
            String subject = finding.getTile() == null
                ? finding.getSubject() : describeTile(finding.getAbout());

            // BOTH, because {0} alone cannot say which train (OB-153): the line above prefers the tile's description to
            // the finding's own subject, but FR-046's train-length warning has a LOCOMOTIVE as its subject; {1} serves
            // the messages that need a name.
            String text = describe(finding.getMessageKey(), subject, finding.getSubject(),
                finding.getCount(), finding.getDetail(), finding.getThird());

            boolean here = onThisPage(finding.getTile());

            // Named where it is somewhere else, since its own section says only that it is not here
            if (!here)
            {
                text = I18n.f("autosetup.ui.findingOnPage", finding.getTile().getPage(), text);
            }

            AutonomyChecks.Severity severity = finding.getSeverity();

            // Warnings are what says WARNING, and everything else that is not an error is a notice: the count on the
            // diagram adds up errors and warnings only, so INFO findings counted as warnings here would make the two
            // numbers disagree.
            List<Object[]> into = here
                ? (severity == AutonomyChecks.Severity.ERROR ? errorRows
                    : severity == AutonomyChecks.Severity.WARNING ? warningRows : noticeRows)
                : (severity == AutonomyChecks.Severity.ERROR ? elsewhereErrors
                    : severity == AutonomyChecks.Severity.WARNING ? elsewhereWarnings
                    : elsewhereNotices);

            into.add(new Object[] {finding.getTile(), text, finding.getRelated()});
        }

        // Severity first, everywhere: an error is an error wherever it is (the setup will not load until it is fixed),
        // so a list showing things merely worth checking first would disagree with the count beside it.  A row about
        // another page still says which page and clicking it still goes there; within each section this page comes
        // first.
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

        // Unnamed points are errors now, so they are already in the list below with a square to jump to each.
        int unnamed = unnamedPoints().size();

        // Greyed, never hidden - the same as Test a path above it: a button that disappears takes the column's shape with
        // it, and one that is there and unavailable says "nothing to do here yet".
        if (nameAll != null)
        {
            nameAll.setEnabled(unnamed > 0 && !ignored);

            nameAll.setToolTipText(unnamed > 0
                ? null : wrapped(I18n.t("autosetup.ui.hintNothingToName")));
        }


        if (errors > 0)
        {
            banner.setText(blockingCountText(errors));
            banner.setBackground(new java.awt.Color(255, 210, 210));
        }
        else
        {
            // What the user wants to know is whether it works and how many places autonomy will actually send a train; the second
            // number counts every station, berths and the ones switched off included, so the difference says how much of the layout is
            // held back.
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

        // THE WHOLE SENTENCE, for where the window is too narrow to show it (OB-342)
        banner.setToolTipText(banner.getText());

        if (!selection.isEmpty())
        {
            say(hint, I18n.f("autosetup.ui.labelTilesSelected", selection.size()));
        }

        if (onChanged != null) onChanged.run();
    }

    /**
     * Adds one severity section to the list, headed and indented as the Auto tab's is; a null `heading` adds the rows
     * under whatever heading is already above them.
     */
    private void section(String heading, List<Object[]> rows, AutonomyChecks.Severity severity)
    {
        if (rows.isEmpty()) return;

        if (heading != null)
        {
            findingsModel.addElement(heading);
            findingTiles.add(null);
            findingRelated.add(null);
            findingSeverity.add(null);
        }

        for (Object[] row : rows)
        {
            findingsModel.addElement("   " + row[1]);
            findingTiles.add((TileKey) row[0]);

            @SuppressWarnings("unchecked")
            List<TileKey> related = row.length > 2 && row[2] != null ? (List<TileKey>) row[2] : null;

            findingRelated.add(related);
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
     * Takes this page out of autonomy, or puts it back.  Asked about only on the way OUT: leaving a page out stops
     * every sensor on it being part of the railway, while putting one back costs nothing, so a confirmation there would
     * have one sensible answer.  Not saved here: Save in this window means the autonomy setup, and this is part of it;
     * ticking the box greys the page at once and Cancel throws it away with everything else, which makes trying it out
     * safe.
     */
    private void setPageExcluded(boolean excluded)
    {
        if (page == null) return;

        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`; the box goes back to what the setup says
        if (heldByARun())
        {
            excludePage.setSelected(!excluded);

            return;
        }

        if (excluded)
        {
            // showOptionDialog with TrainControl's own button text, not showConfirmDialog, whose buttons follow the JVM's
            // locale rather than the language the user picked.  An INDEX comes back, where 0 is the first option.
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

        // An excluded page is absent from the running graph entirely.
        setupChanged();
    }

    /**
     * Walks the unnamed sensors on this page, one prompt each, highlighting the one being named: the alternative is
     * hunting for them, and a generated name is a valid name, so nothing points at them and they turn up as coordinates
     * in a timetable weeks later.
     */
    private void nameEverything()
    {
        // The same as Mass Assign Lengths: a new round opens afresh, and each prompt where the last was left.
        walkPromptAt = null;

        List<TileKey> unnamed = unnamedPoints();

        if (unnamed.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoEverythingNamed"));
            return;
        }

        for (int i = 0; i < unnamed.size(); i++)
        {
            TileKey tile = unnamed.get(i);

            // Shown before asking, so the question is about a square the user can see: outlined as well as flashed, as
            // the flash fades after a couple of seconds and naming forty points is minutes of typing, so the selection
            // mark, which does not fade, stays until the walk ends.
            selection.clear();
            selection.add(tile);
            refresh();

            if (onReveal != null) onReveal.accept(tile);

            String name = askForName(I18n.f("autosetup.ui.promptNameEverything", i + 1,
                unnamed.size()));

            // Asked again, for the same square, until the name is one the session will take.
            while (name != null && !name.trim().isEmpty()
                && AutonomySession.whyNotAPointName(name) != null)
            {
                JOptionPane.showMessageDialog(owner(), wrapped(AutonomySession.whyNotAPointName(name)));

                name = askForName(I18n.f("autosetup.ui.promptNameEverything", i + 1, unnamed.size()));
            }

            // Cancel stops the walk rather than skipping one square, because a walk of forty needs a way out; Skip is a
            // button of its own.
            if (name == null) break;

            if (name.trim().isEmpty()) continue;

            session.setPointName(tile, name.trim());

            // A station that has just been given a name has somewhere obvious for it to go: the same "only if it has
            // none" test as the single rename (MT-116), written the same way although this walk visits only UNNAMED
            // squares, as two rename paths asking different questions is how one of them ends up wrong.
            if (session.getStore().isStation(tile)
                && !session.getLabelledStationTiles().contains(tile))
            {
                placeLabelFor(tile);
            }
        }

        selection.clear();

        refresh();

        // OB-034 again, in bulk: `promptName` rebuilds for a single rename and says why (the running layout keeps the
        // old names and the captions go blank); naming sixty squares is that failure sixty times over.
        setupChanged();
    }

    /** Delegates to `EditorMassAssign.massAssignLengths`. */
    private void massAssignLengths()
    {
        massAssignment.massAssignLengths();
    }

    static final String LENGTHS_TITLE = "autosetup.ui.menuMassAssignLengths";
    static final String MAXIMA_TITLE = "autosetup.ui.menuMassAssignMaxTrainLengths";
    static final String TRAINS_TITLE = "autosetup.ui.menuMassAssignTrainLengths";

    /** Delegates to `EditorMassAssign.massAssignMaxTrainLengths`. */
    private void massAssignMaxTrainLengths()
    {
        massAssignment.massAssignMaxTrainLengths();
    }

    /** Delegates to `EditorMassAssign.massAssignTrainLengths`. */
    private void massAssignTrainLengths()
    {
        massAssignment.massAssignTrainLengths();
    }

    /** Whether a train length typed into the walk is one the railway takes: 1 to the locomotive menu's maximum. */
    public static boolean acceptsATrainLength(int units)
    {
        return units >= 1 && units <= TrainControlUI.ROUTE_TRAIN_LENGTH_MAX;
    }

    /**
     * Where Mass Assign Train Lengths finds the trains to ask about, and where it writes each answer (FR-094): both
     * halves live on the main window (the run list is the running layout's, a length is written through
     * `TrainControlUI.applyTrainLength`) and this panel is also built with no window at all, so the walk asks this;
     * with no window there is nothing to ask about.
     */
    public interface TrainLengthDoor
    {
        /**
         * @return the locomotives autonomy would run that have no train length, in the order to ask them
         */
        java.util.List<String> trainsWithoutALength();

        /**
         * @param train a locomotive's name
         * @param units its length
         */
        void applyTrainLength(String train, int units);

        /**
         * Every locomotive autonomy would run, with the length it has - what the walk goes through when none is missing
         * one (MT-533).
         *
         * @return name to length, in the order to ask them
         */
        default java.util.Map<String, Integer> trainLengths()
        {
            return new java.util.LinkedHashMap<>();
        }
    }

    private TrainLengthDoor trainLengthDoor;

    /**
     * @param door stands in for the main window's two halves, or null to go back to the window
     */
    public void setTrainLengthDoorForTest(TrainLengthDoor door)
    {
        this.trainLengthDoor = door;
    }

    TrainLengthDoor trainLengthDoor()
    {
        if (trainLengthDoor != null) return trainLengthDoor;

        return new TrainLengthDoor()
        {
            @Override
            public java.util.List<String> trainsWithoutALength()
            {
                TrainControlUI window = parentWindow();

                return window == null ? new java.util.ArrayList<String>() : window.trainsWithoutALength();
            }

            @Override
            public java.util.Map<String, Integer> trainLengths()
            {
                TrainControlUI window = parentWindow();

                return window == null ? new java.util.LinkedHashMap<String, Integer>() : window.trainLengths();
            }

            @Override
            public void applyTrainLength(String train, int units)
            {
                TrainControlUI window = parentWindow();

                if (window == null) return;

                window.applyTrainLength(window.getModel().getLocByName(train), units);
            }
        };
    }

    /**
     * Puts a setup check notice on the system clipboard as the list shows it, without its indent: the one door both
     * lists of notices (this editor's and the autonomy setup's) copy through when a notice is clicked (MT-767).  The
     * lists hold plain text, so nothing is stripped (a station named "Yard <old>" is copied as it is named).  A
     * clipboard another program holds, or none at all, leaves it uncopied and says nothing: the click's own work goes
     * on.
     *
     * @param shown the notice as the list holds it
     */
    static void copyNoticeToClipboard(String shown)
    {
        if (shown == null) return;

        String plain = shown.trim();

        if (plain.isEmpty()) return;

        try
        {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(plain);

            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        }
        catch (IllegalStateException | java.awt.HeadlessException | SecurityException e)
        {
            // NOT COPIED, AND THE CLICK STILL WORKS: the clipboard is busy with another program, or there is none
        }
    }

    /**
     * Outlines the squares a clicked finding is about - its own, and those it names beside it on this page - and shows
     * its own (MT-505).  Outlined, not selected: the bulk selection is what the next Segment Length or direction is
     * written to, and a click on a guard notice would make it the station and its signal, so a length typed for any
     * square would go to those two.
     *
     * @param at the finding's own square
     * @param related the squares it names beside it
     */
    public void outlineTheNotice(TileKey at, java.util.Collection<TileKey> related)
    {
        noticeOutline.clear();

        if (at != null && onThisPage(at)) noticeOutline.add(at);

        for (TileKey square : related == null ? java.util.Collections.<TileKey>emptyList() : related)
        {
            if (square != null && onThisPage(square)) noticeOutline.add(square);
        }

        refresh();

        if (at != null && onThisPage(at) && onReveal != null) onReveal.accept(at);
    }

    /**
     * Whether a square is drawn outlined: the bulk selection, a clicked finding's squares, the first end of a path
     * test, the signals a menu item is acting on, or the square a test is waiting on.
     *
     * @return whether it is outlined
     */
    public boolean isOutlined(TileKey tile)
    {
        return selection.contains(tile) || noticeOutline.contains(tile) || tile.equals(testFrom)
            || highlightedSignals.contains(tile) || whyWaitsOn(tile);
    }

    void outlineAndReveal(java.util.Collection<TileKey> squares)
    {
        selection.clear();
        selection.addAll(squares);
        refresh();

        for (TileKey square : squares)
        {
            if (onReveal != null && page != null && page.equals(square.getPage()))
            {
                onReveal.accept(square);

                return;
            }
        }
    }

    /**
     * Where the walk's last prompt was left, so the next one opens there; null at the start of a round.  Each prompt is
     * a new dialog and `JOptionPane.createDialog` centres every one, which covered the track diagram (MT-454: *"when
     * the popup closes and reopens, make sure it remembers its location unless I reopen a new mass assignment round"*).
     * Name Everything's walk shares it.
     */
    java.awt.Point walkPromptAt;

    void placeWalkPrompt(javax.swing.JDialog dialog)
    {
        if (walkPromptAt != null) dialog.setLocation(walkPromptAt);
    }

    /** The walk dialogs' answers: OK, Skip, or stop the walk. */
    public static final int ANSWER_OK = 0;
    public static final int ANSWER_SKIP = 1;
    public static final int ANSWER_STOP = -1;

    /**
     * What an OK / Skip / Cancel walk dialog was answered with.  **Only the two buttons that say so are answers;
     * everything else stops the walk**: JDK 8's option pane answers Escape with `Integer.valueOf(CLOSED_OPTION)`, not
     * null (`BasicOptionPaneUI`, inherited by FlatLaf), so reading the field for anything that was not null or Cancel
     * would write whatever had been typed on Escape.
     *
     * @param chosen the option pane's value
     * @param answers OK, Skip and Cancel, in that order
     * @return ANSWER_OK, ANSWER_SKIP or ANSWER_STOP
     */
    public static int dialogAnswer(Object chosen, Object[] answers)
    {
        if (chosen != null && chosen.equals(answers[0])) return ANSWER_OK;
        if (chosen != null && chosen.equals(answers[1])) return ANSWER_SKIP;

        return ANSWER_STOP;
    }

    /** Delegates to `EditorMassAssign.nameForPrompt`. */
    private String nameForPrompt(TileKey tile)
    {
        return massAssignment.nameForPrompt(tile);
    }

    /**
     * Opens every one-way and closed piece of track both ways, after a warning (FR-108); the warning, the Cancel it
     * offers and the message after are the clears' beside it: in the editor Cancel puts the directions back, on the
     * track diagram's own menu it is saved at once.
     */
    private void allowEveryPath()
    {
        int shut = session.routesNotOpenBothWays().size();

        if (shut == 0)
        {
            say(hint, I18n.t("autosetup.ui.infoNothingOneWay"));

            return;
        }

        if (JOptionPane.showOptionDialog(owner(),
            bulkClearWarning("autolayout.ui.confirmAllowEveryPath", shut),
            I18n.f("autolayout.ui.menuAllowEveryPath", shut),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        int opened = session.allowEveryPath();

        selection.clear();

        say(hint, I18n.f("autosetup.ui.infoEveryPathAllowed", opened));

        // Directions are what the running railway is built from, so the door a direction's own change takes
        setupChanged();
    }

    /**
     * Forgets every length on every page, after asking (FR-069).  **What it costs is stated in the dialog, and depends
     * on the surface**: in the editor the lengths come back on Cancel (they are in the setup snapshot taken when the
     * window opens, OB-223, which `discardAutonomyWork` restores); on the track diagram's own menu, served with no page
     * and saving at once, they do not.  Two sentences chosen by one question, as the locomotive clear does.  The empty
     * case is said in the hint line rather than in a dialog, and kept though the item greys on the same question,
     * because the greying is the AFFORDANCE and this is the GUARD (OB-057 and OB-090).
     */
    private void clearAllTileLengths()
    {
        java.util.List<TileKey> measured = session.tilesWithALength();

        if (measured.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoNoTrackLengthsToClear"));

            return;
        }

        // WHAT CANCEL DOES DEPENDS ON WHICH SURFACE THIS IS: in the EDITOR the lengths come back on Cancel (OB-223),
        // while the track diagram's own menu saves as it goes, so there the clear cannot be undone; chosen as
        // `clearLocomotivesWarning` does.
        if (JOptionPane.showOptionDialog(owner(),
            bulkClearWarning("autolayout.ui.confirmClearAllTrackLengths", measured.size()),
            I18n.f("autolayout.ui.menuClearAllTrackLengths", measured.size()),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        int cleared = session.clearEveryTileLength();

        selection.clear();

        say(hint, I18n.f("autosetup.ui.infoTrackLengthsCleared", cleared));

        // Lengths are what the room rule measures with, and it asks the RUNNING layout: the same door `applyLength` uses.
        setupChanged();
    }

    /**
     * Takes the maximum train length off every station on every page, after asking: `clearAllHomes`' shape, the
     * emptiness guard kept although the item greys on the same question, and the confirmation the item's tooltip.
     */
    private void clearAllMaxTrainLengths()
    {
        java.util.List<TileKey> limited = session.tilesWithAMaxTrainLength();

        if (limited.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoNoMaxTrainLengthsToClear"));

            return;
        }

        // The same two sentences as the track-length clear beside it.
        if (JOptionPane.showOptionDialog(owner(),
            bulkClearWarning("autolayout.ui.confirmClearAllMaxTrainLengths", limited.size()),
            I18n.f("autolayout.ui.menuClearAllMaxTrainLengths", limited.size()),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        int cleared = session.clearEveryMaxTrainLength();

        say(hint, I18n.f("autosetup.ui.infoMaxTrainLengthsCleared", cleared));

        // ONE REDRAW, NOT TWO: `item()` refreshes after every action, and `setupChanged` rebuilds the RUNNING layout
        // without redrawing this panel; the maximum is read by the RUNNING layout's Points, so it is told, as
        // `promptNumber` does for one.
        setupChanged();
    }

    /**
     * Takes the home locomotive off every square that has one (Adam: **"that option should be added back in to the
     * autonomy editor, with a confirmation."**).  Through the SESSION: `clearEveryHome()` writes through the property
     * writer `setHome` does, re-derives the station index once instead of sixty-two times, and keeps the
     * one-home-one-square sweep honest; a second way of clearing a home is how the two doors would come to disagree.
     * The confirmation is the 2.8.1 wording, still in all eight bundles: with no homes recorded, Return Home sends each
     * locomotive back to wherever it was standing when autonomy loaded.  Nothing is written to disk here: like every
     * other decision in this window it waits for Save, so Cancel undoes a mistaken press.
     */
    private void clearAllHomes()
    {
        java.util.List<TileKey> homed = session.tilesWithAHome();

        // Nothing to do, said in the hint line rather than in a dialog.  Kept even though the button greys itself on
        // the same question, because the greying is the AFFORDANCE and this is the GUARD (OB-057 and OB-090), and a
        // home can go away between one refresh and the next.
        if (homed.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoNoHomesToClear"));
            return;
        }

        if (JOptionPane.showOptionDialog(owner(),
            I18n.t("autolayout.ui.confirmClearAllHomeLocomotives"),
            I18n.f("autolayout.ui.menuClearAllHomeLocomotives", homed.size()),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        // THE SESSION'S BULK DOOR, not sixty-two single ones: every `setHome` re-derives the station index, a full
        // builder construction on the event thread.
        int cleared = session.clearEveryHome();

        say(hint, I18n.f("autosetup.ui.infoHomesCleared", cleared));

        refresh();

        // MT-246'S OWN PROPERTY, IN BULK: without this the setup is saved and the running layout keeps every home, so
        // `triageReturnToHome` goes on offering Return Home and sends each train to a home the operator has cleared.
        setupChanged();
    }

    /**
     * The sentence a bulk clear will show, for the surface this panel is serving.  **One builder, so the pair cannot
     * drift**: a copy of the choice per menu item leaves nothing to stop a fifth copy, or a pair of keys swapped in one
     * of them; `clearLocomotivesWarning` states the same rule for its own pair, used by the confirmation AND by the
     * menu item's tooltip, so the warning cannot be shown in one place and not the other.  A panel serving the track
     * diagram has no page and saves as it goes, so the clear is immediate and says so; inside the editor Cancel undoes
     * it.  The tooltip's short form goes through it too (FR-112): a `tip` key with its own "AtOnce" sibling, so the
     * menu item says in brief what the dialog says in full and cannot promise a Cancel the dialog does not.
     *
     * @param key the message key for the editor's sentence, whose "AtOnce" sibling is the other one
     * @param count how many squares the clear will touch
     * @return the sentence, already translated
     */
    private String bulkClearWarning(String key, int count)
    {
        return I18n.f(page == null ? key + "AtOnce" : key, count);
    }

    /**
     * What the bulk clear says before it takes every locomotive off (OB-194).  Adam, on MT-311: **"bug: clearning
     * locomotives in the autonomy editor cannot be undone by a cancel.  Make this clear in the popup."**, and then
     * *"Warning if using the bulk tool."*  **What Cancel does**: it restores the setup as the editor opened it,
     * placements included (OB-223), and the rebuild when the editor closes regenerates them from it
     * (`TrainControlUI.putTheTrainsBack` only moves trains that were standing, which the cleared ones are not), so they
     * come back, and the warning says so
     * (`regression.testCancelUndoesAutonomyEdits.testCancelPutsBackTheLocomotivesABulkClearTook`).  **It names them**,
     * because a bulk gesture answers for every one at once and a count is not something anybody can check; the menu
     * item's tooltip is its short form, `clearLocomotivesTip`, chosen the same way (FR-112).  **On the track diagram's
     * own menu there is no Cancel**: that menu is this panel served with no page, its changes are saved as they are
     * made, and it is not offered while an editor is open (`TrainControlUI.buildAutonomyTileMenu`), so the warning says
     * it is saved at once (`regression.testTheBulkClearSaysWhatCancelDoes` asks both doors).
     *
     * @return the warning, already translated
     */
    public String clearLocomotivesWarning()
    {
        if (session == null) return I18n.t("autosetup.ui.infoNoLocomotivesToClear");

        // THE SET THE CLEAR ACTUALLY WALKS, COUNTED AND NAMED FROM ONE PLACE: `placementsAutonomyWillWrite()` skips
        // excluded pages, so counting `tilesWithALocomotive()` would confirm "12 locomotives" over nine names.  Sorted,
        // because a map's iteration order is not an order and this list is read.
        java.util.Map<TileKey, String> clearing = session.placementsAutonomyWillWrite();

        // ONE UNIT IN BOTH HALVES OF THE SENTENCE: the map is keyed by SQUARE and the sentence is about
        // LOCOMOTIVES.
        java.util.Set<String> locomotives = new java.util.TreeSet<>(clearing.values());

        StringBuilder names = new StringBuilder();

        for (String name : locomotives)
        {
            if (names.length() > 0) names.append(", ");

            names.append(name);
        }

        if (page == null)
        {
            return I18n.f("autolayout.ui.confirmClearLocomotivesAtOnce",
                locomotives.size(), names.toString());
        }

        return I18n.f("autolayout.ui.confirmClearLocomotives",
            locomotives.size(), names.toString());
    }

    /**
     * The menu item's tooltip: `clearLocomotivesWarning` in short (how many, and what Cancel does), chosen as that
     * sentence is (FR-112).  The names are the dialog's, which shows them before anything is cleared; counted as it
     * counts, locomotives and not the squares they stand on.
     *
     * @return the tooltip, already translated
     */
    public String clearLocomotivesTip()
    {
        if (session == null) return I18n.t("autosetup.ui.infoNoLocomotivesToClear");

        return bulkClearWarning("autosetup.ui.tipClearLocomotives",
            new java.util.TreeSet<>(session.placementsAutonomyWillWrite().values()).size());
    }

    /**
     * Takes every locomotive off the setup (MT-257 item 1; Adam: **"Yes, I want it back."**).  Through
     * `placeLocomotive(tile, null)`, the door the per-square "Remove" item uses, which also clears the facing the
     * placement left behind; no skipping: a placement the editor can show is a placement the editor can clear.
     * **Cancel puts them back** (OB-223): the discard restores the setup as the editor opened it and the rebuild
     * regenerates the placements from it, as `clearLocomotivesWarning` says before they answer.
     */
    private void clearAllPlacements()
    {
        // THE SET `clearEveryPlacement` ACTUALLY WALKS: `placedLocomotives()` skips pages excluded from autonomy, so
        // counting `tilesWithALocomotive()` (every page) would name a number the clear would not reach.
        java.util.List<TileKey> placed =
            new java.util.ArrayList<>(session.placementsAutonomyWillWrite().keySet());

        if (placed.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoNoLocomotivesToClear"));
            return;
        }

        if (JOptionPane.showOptionDialog(owner(),
            clearLocomotivesWarning(),
            I18n.f("autolayout.ui.menuClearLocomotives", placed.size()),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        // THE SESSION'S BULK DOOR, not one per square: clearing one placement writes two properties (the placement and
        // the facing), and each write re-derives the station index, a full builder construction on the event thread.
        // WHOSE PLACEMENTS THESE ARE is read before the clear takes them away: an unnamed clear is undone by the
        // rebuild that carries it.
        java.util.List<String> lifted = placedLocomotives();

        int cleared = session.clearEveryPlacement();

        say(hint, I18n.f("autosetup.ui.infoLocomotivesCleared", cleared));

        placementChanged(lifted);
    }

    /**
     * Asks for one name, with somewhere to go for a square the user does not want to name: three answers rather than
     * two, as naming forty points is a long walk and pressing OK with the box empty is not something anybody would
     * guess.  Built as a dialog rather than through showInputDialog so that Enter still means OK.
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
            // NO INITIAL VALUE, so the field has the keyboard focus (MT-454), as in `askForWholeLength`: with OK as the
            // initial value JOptionPane gives the focus to that button, so what is typed goes nowhere and Enter on the
            // empty field counts as Skip.
            JOptionPane.YES_NO_CANCEL_OPTION, null, answers, null);

        final javax.swing.JDialog dialog = pane.createDialog(owner(),
            I18n.t("autosetup.ui.btnNameEverything"));

        placeWalkPrompt(dialog);

        // Enter in the box is OK, which showInputDialog gives for nothing and a dialog built by hand has to be told.
        field.addActionListener(e ->
        {
            pane.setValue(answers[0]);

            dialog.dispose();
        });

        dialog.setVisible(true);

        walkPromptAt = dialog.getLocation();

        dialog.dispose();

        // Closed with the window button or Escape, which is the same as changing your mind about the walk.
        int answer = dialogAnswer(pane.getValue(), answers);

        if (answer == ANSWER_STOP) return null;

        if (answer == ANSWER_SKIP) return "";

        return field.getText();
    }

    /**
     * @param onDiagramChanged run after a track diagram page is written, so the grid can be rebuilt
     */
    public void setOnDiagramChanged(Runnable onDiagramChanged)
    {
        this.onDiagramChanged = onDiagramChanged;
    }

    /**
     * @param onReveal called with a tile that should be scrolled to and flashed
     */
    public void setOnReveal(java.util.function.Consumer<TileKey> onReveal)
    {
        this.onReveal = onReveal;
    }

    /**
     * @return the Unmeasured Track display choice, for the window to mount under Track Lengths
     */
    public JCheckBox getShowUnmeasured()
    {
        return control(showUnmeasured);
    }

    /**
     * @return the Show Trains switch, for the window to mount under Unmeasured Track (FR-119)
     */
    public JCheckBox getShowTrains()
    {
        return control(showTrains);
    }

    /**
     * @return the Text Labels switch, for the window to mount under Show Trains (FR-133)
     */
    public JCheckBox getShowTextLabels()
    {
        return control(showText);
    }

    /**
     * Whether this editor's diagram draws its trains (FR-119) - asked by its squares as they paint.
     *
     * @return the Show Trains switch's state
     */
    public boolean isShowingTrains()
    {
        return showTrains.isSelected();
    }

    /**
     * @return the track-lengths toggle, so the window can keep it exclusive with its own Addresses box
     */
    public JCheckBox getShowLengths()
    {
        return control(showLengths);
    }

    /**
     * @return the caption choice, for the window's visibility box (FR-061)
     */
    public javax.swing.JComboBox<String> getCaptionChoice()
    {
        // The Track Directions box's font, from the same place (FR-133)
        return control(captions);
    }

    /**
     * @return the parked-train toggle, no longer mounted - the internal half of the choice (FR-061)
     */
    public JCheckBox getShowParkedTrains()
    {
        return control(showParkedTrains);
    }

    /**
     * Whether captions in this editor should name the parked train rather than the station (FR-030).
     *
     * @return true to name the train
     */
    public boolean isShowingParkedTrains()
    {
        return showParkedTrains.isSelected();
    }

    /**
     * @return the home-locomotive toggle, for the window's Toggle visibility box
     */
    public JCheckBox getShowHomeLocomotives()
    {
        return control(showHomeLocomotives);
    }

    /**
     * Whether captions in this editor should name each square's home locomotive (MT-261 ruling 2).
     *
     * @return true to name the home
     */
    public boolean isShowingHomeLocomotives()
    {
        return showHomeLocomotives.isSelected();
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

    private String describe(String key, Object... args)
    {
        try
        {
            return I18n.f(key, args);
        }
        catch (RuntimeException e)
        {
            // a key that has not reached the bundles yet should not blank the whole list
            return key + " " + (args.length > 0 ? args[0] : "");
        }
    }

    /**
     * Writes the setup.
     *
     * @return whether it was written.  The caller closes the editor on the strength of this: a save that fails must not
     * close the
     *         window, which would read as success
     */
    public boolean save()
    {
        try
        {
            // Shown through the one display every door uses, including the refusal that says a page's settings were
            // left alone because its file is not loaded.
            AutonomyReport.show(owner(), session.save());

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
