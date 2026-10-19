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
 * Setting autonomy up by clicking on the track it runs over.
 *
 * Hand written rather than built in the designer, and mounted into a container the editor already has, so no form
 * file changes and no new window.  LayoutGrid is the style it follows.
 *
 * The panel holds the tools and the feedback.  The clicking happens on the diagram itself: a tile is handed here by
 * the editor, and what happens to it depends on the selected tool.  There is no separate list of things to
 * configure, because the thing being configured is the track already on the screen.
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
         * TEST answers a question about the track; this answers one about a locomotive and about now: every station
         * it might be sent to, and for each one the reason it was refused.
         */
        WHY,

        /**
         * Ask whether a train could get from one sensor to another, and see the route it would take.
         *
         * The only tool that needs a mode, because it takes two clicks to say one thing.  Everything else names one
         * tile and belongs on that tile's menu.
         */
        TEST,

        /**
         * Close a run of track in one direction, from one square to another.
         *
         * Two clicks, like TEST, so it is a tool and not a menu item.  Unlike TEST it WRITES, so it asks before it
         * does: two squares name a run, the run has two directions, and which one is being closed is the whole
         * decision.
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

    // A finding on another page that names squares beside its own - a guard's signal - goes there with them (RLV9-C5).
    // Where this is not set, such a finding jumps as any other.
    private java.util.function.BiConsumer<TileKey, List<TileKey>> onJumpToNotice;

    /**
     * @param action given the square of a finding on another page, and the squares it names beside it
     */
    public void setOnJumpToNotice(java.util.function.BiConsumer<TileKey, List<TileKey>> action)
    {
        this.onJumpToNotice = action;
    }

    // Which tile each findings row is about; null for a heading or a finding with no tile
    private final List<TileKey> findingTiles = new java.util.ArrayList<>();

    /** For each row of the findings list, the squares it is about beyond its own - a guard notice's signal (MT-505). */
    private final List<List<TileKey>> findingRelated = new java.util.ArrayList<>();

    // What each row is, so it can be coloured the same way the Auto tab colours its own list
    private final List<AutonomyChecks.Severity> findingSeverity = new java.util.ArrayList<>();

    private Tool tool = Tool.NONE;

    /**
     * How wide the column is: 150, matching LayoutEditor.SIDEBAR_WIDTH, the other fixed strip in this window (OB-019).
     * Nothing in it needs more: two buttons and a hint.
     */
    private static final int WIDTH = 150;

    // The EDITOR window's own conventions, which are not quite the main window's: its headings are
    // Semibold 13 in rgb(0,0,155) (jLabel1 "New Components", toggleVisibility "Toggle Visibility") and its
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

    /**
     * The verdict and the count, on the one line under the findings.
     *
     * ITS WORDS ARE DATA, so it may be given less width than they need (OB-342).  A label asks for the width of its
     * text as its minimum (521 pixels in English, 655 in German), which at the window's smallest width pushed the
     * whole tools column off its right edge.  With no minimum width the line is cut with an ellipsis where the
     * window is narrow, and its tooltip has the whole of it (see `refresh`).
     */
    private final JLabel banner = new JLabel()
    {
        @Override
        public java.awt.Dimension getMinimumSize()
        {
            return new java.awt.Dimension(0, super.getMinimumSize().height);
        }
    };
    private final JLabel hint = new JLabel();
    private final DefaultListModel<String> findingsModel = new DefaultListModel<>();
    private final JList<String> findings = new JList<>(findingsModel);

    /**
     * How much of the direction information is drawn: all of it, only what is shut, only what is open, none, or
     * only where trains may pull in.  One control, because the answers are exclusive.
     */
    private final javax.swing.JComboBox<String> directions = new javax.swing.JComboBox<>(new String[]
    {
        I18n.t("autosetup.ui.directionsAll"),
        I18n.t("autosetup.ui.directionsRestrictions"),
        I18n.t("autosetup.ui.directionsAllowed"),
        I18n.t("autosetup.ui.directionsNone"),
        I18n.t("autosetup.ui.directionsArrivals")
    });

    /**
     * The view that shows nothing but where trains may pull in.  Its own entry because it answers a different
     * question and wants the diagram to itself: the arrival marks are small and sit at the tile edges where the
     * direction arrows already are.
     */
    private static final int VIEW_ARRIVALS = 3;

    /**
     * The view that shows only the directions trains may run: the green arrows, none of the red.
     */
    private static final int VIEW_ALLOWED = 4;

    /**
     * What each entry of the directions box means, by its place in the box: Show All, Restrictions only, Allowed only,
     * Hide All, Station Arrivals.  The MEANING is what is remembered, so adding an entry does not change what a
     * remembered choice means.
     */
    private static final int[] DIRECTIONS_BY_ENTRY = {0, 1, VIEW_ALLOWED, 2, VIEW_ARRIVALS};

    /**
     * What the visibility controls were last set to, remembered across openings of this window.
     *
     * In Preferences rather than in the setup: which arrows somebody likes to look at is a fact about them, not
     * about the railway, and it should not travel to another machine with an exported configuration.
     */
    private static final java.util.prefs.Preferences VIEW_PREFS =
        org.traincontrol.util.Util.preferencesFor(AutonomyEditorPanel.class);

    private static final String PREF_DIRECTIONS = "autonomyEditorDirections";
    private static final String PREF_LENGTHS = "autonomyEditorLengths";

    /** Remembered like Track Lengths above it. */
    private static final String PREF_UNMEASURED = "autonomyEditorUnmeasured";

    /** Remembered like the two above it (FR-119). */
    private static final String PREF_TRAINS = "autonomyEditorTrains";

    /**
     * Whether the captions in this editor name the train parked at a station or the station itself (FR-030).
     * Persisted beside the other view switches.
     */
    private static final String PREF_CAPTION_TRAINS = "autonomyEditorCaptionTrains";

    /**
     * What the captions say, as one choice rather than three switches (FR-061).  Adam: *"Station Names should be
     * default, with the setting remembered between open."*
     *
     * Four mutually exclusive options cannot be in a state that needs correcting, which a master switch with two
     * dependent boxes could (OB-174: ticking "Show parked trains" with Text Labels off changed nothing visible).
     */
    private static final String PREF_CAPTION_MODE = "autonomyEditorCaptionMode";

    /** Whether the diagram's text labels are shown, remembered beside the caption mode (FR-133). */
    private static final String PREF_TEXT_LABELS = "autonomyEditorTextLabels";

    /** Captions name the station.  The default, because this window is where a railway is named. */
    public static final int CAPTIONS_STATIONS = 0;

    /** Captions name whichever train is parked there (FR-030). */
    public static final int CAPTIONS_PARKED = 1;

    /** Captions name the locomotive that lives there (MT-261 ruling 2, R28-C3). */
    public static final int CAPTIONS_HOMES = 2;

    /** No captions at all - the option the dropdown calls Hide (FR-133). */
    public static final int CAPTIONS_NONE = 3;

    /**
     * What an earlier version stored for its fifth option, Labels Only (OB-272).  It opens as Station Names with the
     * Text Labels box ticked (FR-133); the dropdown has no such option.
     */
    private static final int LEGACY_LABELS_ONLY = 4;

    /**
     * Whether captions name each square's HOME locomotive (MT-261 ruling 2), in white when it is not there.
     *
     * Beside `PREF_CAPTION_TRAINS` because it is the same kind of question, and the answers are alternatives: a
     * caption has room for one name.
     */
    private static final String PREF_CAPTION_HOMES = "autonomyEditorCaptionHomes";

    /**
     * Restrictions only, by default: open track is most of a layout and its arrows say what the reader can already
     * assume, so a setup shows the decisions somebody has made.
     */
    private static final int DIRECTIONS_DEFAULT = 1;
    private final JCheckBox showLengths = new JCheckBox(I18n.t("autosetup.ui.btnShowLengths"), false);

    /**
     * Highlights every square a length rule reads that has no length.
     */
    private final JCheckBox showUnmeasured = new JCheckBox(I18n.t("autosetup.ui.btnShowUnmeasured"), false);

    /**
     * Draws the trains on this editor's diagram, or hides them (FR-119).  On by default and remembered like Track
     * Lengths; the track diagram's own trains are independent of this switch.
     */
    private final JCheckBox showTrains = new JCheckBox(I18n.t("autosetup.ui.btnShowTrains"), true);

    /**
     * Shows the writing on this editor's diagram, or hides it (FR-133).  Off by default and remembered beside the
     * caption mode; the caption dropdown decides the station captions and this box only the text.
     */
    private final JCheckBox showText = new JCheckBox(I18n.t("layout.ui.textLabels"), false);

    /**
     * The squares it highlights, worked out once per refresh rather than once per square painted - null until asked.
     */
    private java.util.Set<TileKey> unmeasuredSquares = null;

    /**
     * The keys `LayoutEditor` binds to this panel's actions, as their tooltips name them (OB-214).  A shortcut nobody
     * is told about goes unused.  Literal, as the Text Labels, Addresses and Grid toggles write theirs; public so a
     * test can ask for the same words the tooltip was given.
     */
    public static final String SHORTCUT_HOME = "Control+H";

    /** Names the square under the pointer - see `SHORTCUT_HOME`. */
    public static final String SHORTCUT_NAME = "Control+S";

    /** Sets the segment length of the square under the pointer - see `SHORTCUT_HOME`. */
    public static final String SHORTCUT_LENGTH = "Control+E";

    /** Shows a station's name on the square under the pointer - see `SHORTCUT_HOME` (FR-086). */
    public static final String SHORTCUT_STATION = "Control+N";

    /** Shows or hides the track lengths - see `SHORTCUT_HOME`. */
    public static final String SHORTCUT_LENGTHS = "Control+G";

    /**
     * Names the parked train instead of the station (FR-030).  Off by default: this editor is where a railway is
     * NAMED, and a caption about which locomotive happens to stand there answers a question about right now.
     */
    private final JCheckBox showParkedTrains =
        new JCheckBox(I18n.t("autosetup.ui.btnShowParkedTrains"), false);

    /**
     * Names each square's HOME locomotive on its caption (MT-261 ruling 2, R28-C3).  Off by default, like its
     * neighbour: the caption's own answer is the station's name.
     */
    private final JCheckBox showHomeLocomotives =
        new JCheckBox(I18n.t("autosetup.ui.btnShowHomeLocomotives"), false);

    /**
     * The one control that decides what a caption says (FR-061).
     *
     * The two boxes above stay as the INTERNAL representation, because everything that draws a caption asks them;
     * they are not mounted anywhere, this is.  Order matters and matches the constants: the index IS the mode, which
     * is what gets remembered.
     */
    private final javax.swing.JComboBox<String> captions = new javax.swing.JComboBox<>(new String[]
    {
        I18n.t("autosetup.ui.captionsStations"),
        I18n.t("autosetup.ui.captionsParked"),
        I18n.t("autosetup.ui.captionsHomes"),
        I18n.t("autosetup.ui.captionsNone")
    });


    // Built in the constructor, mounted by the window across the bottom of the diagram
    private JScrollPane findingsPanel;

    // Offered only while something is still unnamed, which is the only time it does anything
    private JButton nameAll;
    private javax.swing.JCheckBox excludePage;

    // Portal pairing takes two clicks, and the first is remembered here
    private TileKey pendingPortal;

    // Bulk selection, so a one-way run is set in one gesture rather than forty
    private final Set<TileKey> selection = new LinkedHashSet<>();

    /**
     * Run after this window has written to a track diagram page, to rebuild the grid it is drawn on.
     */
    private Runnable onDiagramChanged;

    // The path test takes two clicks; the first end is kept here
    private TileKey testFrom;

    /**
     * The two squares the last path test was between, so switching Path Type can redraw it (MT-399).
     *
     * Cleared wherever the drawn route is, so a radio click never brings back a route that was put away.
     */
    private TileKey lastTestFrom;
    private TileKey lastTestTo;

    /**
     * The square the last Why Not Moving? answer was about, so switching Path Type can ask it again (MT-434).
     *
     * Cleared with the path test's squares.
     */
    private TileKey lastWhyTile;

    /**
     * Which tier the routing check answers for, and the label that names the pair.  Auto is the default: somebody
     * opens this panel because autonomy is not doing something.
     */
    private JLabel pathTypeLabel;

    private javax.swing.JRadioButton pathTypeAuto;

    private javax.swing.JRadioButton pathTypeManual;

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

    // A one-way run started with the tool, waiting for its far end
    private TileKey oneWayFrom;

    // The station whose protecting signal is being picked by clicking one, waiting for that click
    private TileKey signalFor;

    // And which of its two lists the click is for (FR-096): the protecting signals or the entry guard
    private Guard signalForGuard = Guard.EXIT;

    /**
     * Which of a station's two signal lists a pairing works on (FR-096).  ONE dialog is told which list to read and
     * write and which sentences to say, rather than a second copy that would drift.
     */
    private enum Guard
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

    /**
     * @param guard which list
     * @param station the station's square
     * @return the signals on it
     */
    private java.util.List<TileKey> signalsOf(Guard guard, TileKey station)
    {
        return guard == Guard.ENTRY ? session.getEntrySignals(station) : session.getProtectingSignals(station);
    }

    /**
     * The station waiting to be told which square holds it back, or null (FR-025).  Kept exactly as `signalFor` is, one
     * field saying "the next click on the diagram is the answer to this question", so the two gestures are
     * cancelled, cleared and reasoned about in the same places.
     */
    private TileKey blockingFor;

    /** Whether the "which signals protect this station" window is on screen - see isFocusedOnSignals */
    private boolean signalWindowOpen;

    // The signals drawn outlined, so that "protected by signal 12" can be pointed at rather than read
    private final java.util.Set<TileKey> highlightedSignals = new java.util.LinkedHashSet<>();

    // The squares a clicked finding is about, drawn outlined (MT-505): its own and those it names beside it.  Not the bulk
    // selection, which the next length or direction is written to (RLV9-C3); cleared by the next click on the diagram, the
    // next finding, Escape or a tool.
    private final java.util.Set<TileKey> noticeOutline = new java.util.LinkedHashSet<>();

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
        // No inset either side: the window's own heading and its Save and Cancel buttons sit outside this panel and
        // run edge to edge, so padding here puts the column out of step with both.
        setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        add(buildTools(), BorderLayout.NORTH);

        // ESCAPE IS NOT BOUND HERE, deliberately (FR-065, B5).  A binding here would run FIRST and disarm the tool, and the
        // frame's deferred `formKeyPressed` branch would then find nothing armed and close the window: one press doing
        // both.  (It never fired anyway: `LayoutEditor` makes every control `setFocusable(false)` so the FRAME keeps the
        // keyboard.)  `putToolsDown` stays public and `LayoutEditor.letGoOfWhateverIsHeld` calls it.  One key, one
        // handler, in the one place that provably runs.

        // The findings are built here but mounted by the WINDOW, across the bottom and the full width, beside the
        // diagram they describe.
        findingsPanel = buildFindings();

        // Pinned, so a long sentence in the hint or a finding cannot widen the column it lives in.
        setPreferredSize(new Dimension(WIDTH, 640));
        setMinimumSize(new Dimension(WIDTH, 240));
        setMaximumSize(new Dimension(WIDTH, Integer.MAX_VALUE));

        // Nothing in this column is worked by keyboard, and a control that takes focus swallows the key presses the window
        // around it uses.  button() does this one control at a time; the sweep covers the lists and anything added later.
        AutonomyViewerPanel.unfocusable(this);

        refresh();
    }

    /**
     * Wraps a sentence to the panel's width instead of demanding a column as wide as the sentence.
     */
    private void say(JLabel label, String text)
    {
        // Straight to the banner across the top of the window, which has the width a sentence needs.  The label is the
        // fallback for a panel mounted without one, and is wrapped so that it cannot drag its column out of shape.
        if (messageBanner != null && label == hint)
        {
            messageBanner.show(text);
            return;
        }

        label.setText("<html><body style='width:" + (WIDTH - 30) + "px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
    }

    /**
     * Says something that has to stay until the user acts on it: a prompt for a second click, which a message that
     * faded after six seconds would strand them halfway through.
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

    // The strip across the top of the window: what just happened.  Not the status banner in the tools column, which
    // shows what the setup IS.
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
     * A vertical BoxLayout rather than a GridLayout, which gives EVERY row the height of the tallest: a hint wrapped to
     * three lines would grow every checkbox and button to three lines.  Here each row takes the height it needs.
     */
    private JPanel buildTools()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        // No heading of its own: the editor prints one above this column.  Every setting that names one tile is on that
        // tile's right-click menu; the two actions here are the ones not done by clicking the diagram, stacked because two
        // buttons in a row would set the column's width from their combined length.  The hint line below speaks about
        // whatever was last clicked.
        testButton = toolButton(Tool.TEST, I18n.t("autosetup.ui.toolTest"));

        // WHICH TIER THE CHECK ANSWERS FOR (Adam, 2026-09-05): **"Radio button Auto or Manual dictates what is in scope
        // for the routing check."**
        //
        // Autonomy and a hand dispatch disagree about where a train may be SENT (autonomy will not choose a station it is
        // told to leave alone; a person may), so a drawn route was true for one and silent about the other.  They agree
        // about everything else: an inactive square stops both, a barred arrival stops both, a must-turn square turns
        // both.  So the radio changes the destination test and nothing about how the track is walked.
        pathTypeLabel = new JLabel(I18n.t("autosetup.ui.labelPathType"));

        // THE WINDOW'S BLUE GROUP HEADING (OB-217): Segoe UI Semibold in navy, as every other section label in this
        // application, so it reads as the heading of the pair below it rather than as a third control.
        pathTypeLabel.setFont(AutonomyViewerPanel.FONT_GROUP);
        pathTypeLabel.setForeground(AutonomyViewerPanel.HEADING_COLOUR);

        pathTypeAuto = new javax.swing.JRadioButton(I18n.t("autosetup.ui.pathTypeAuto"), true);
        pathTypeManual = new javax.swing.JRadioButton(I18n.t("autosetup.ui.pathTypeManual"), false);

        javax.swing.ButtonGroup pathType = new javax.swing.ButtonGroup();
        pathType.add(pathTypeAuto);
        pathType.add(pathTypeManual);

        // On the LABEL and both radios, because a tooltip on one of a pair is found only by whoever hovers that half.
        String pathTypeTip = wrapped(I18n.t("autosetup.ui.tooltipPathType"));

        pathTypeLabel.setToolTipText(pathTypeTip);
        pathTypeAuto.setToolTipText(pathTypeTip);
        pathTypeManual.setToolTipText(pathTypeTip);

        pathTypeAuto.setFont(FONT_CONTROL);
        pathTypeManual.setFont(FONT_CONTROL);

        // THE ROUTE ON SCREEN FOLLOWS THE TIER (MT-399): the same two squares, through the same door the second click
        // uses.
        pathTypeAuto.addActionListener(e -> retestForTheNewTier());
        pathTypeManual.addActionListener(e -> retestForTheNewTier());

        whyButton = toolButton(Tool.WHY, I18n.t("autosetup.ui.toolWhy"));
        whyButton.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipWhy")));

        oneWayButton = toolButton(Tool.ONE_WAY, I18n.t("autosetup.ui.toolOneWay"));
        oneWayButton.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipOneWay")));

        nameAll = new JButton(I18n.t("autosetup.ui.btnNameEverything"));
        nameAll.addActionListener(e -> nameEverything());
        button(nameAll);


        // Leaving the page out, from the page itself, where somebody looking at findings about track nobody automates
        // decides it is not autonomy's business.  A checkbox because it describes a state: ticked, the page greys out at
        // once and the change waits for Save like every other decision in this window.
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

        // BELOW "WHY NOT MOVING?", AS A HEADING OF ITS OWN (OB-217).  The label keeps its own row above the pair: two
        // radios and a caption on one line would set this column's width from their combined length.
        panel.add(row(pathTypeLabel));
        panel.add(row(pathTypeAuto));
        panel.add(row(pathTypeManual));

        // ONE-WAY RUN AND NAME EVERYTHING ARE IN BULK TOOLS (OB-217), and only where this panel has a page.  The refresh
        // keeps Name Everything's state and tooltip, and the disarm path un-presses One-Way Run (MT-098: a control that
        // nothing reaches).

        // AND A HEADING FOR WHAT FOLLOWS (OB-217).
        JLabel pageSettings = new JLabel(I18n.t("autosetup.ui.labelPageSettings"));

        pageSettings.setFont(AutonomyViewerPanel.FONT_GROUP);
        pageSettings.setForeground(AutonomyViewerPanel.HEADING_COLOUR);

        panel.add(row(pageSettings));
        panel.add(row(excludePage));

        // The toggles change what is drawn, not what is decided, so all they do is redraw; they live in the window's own
        // Toggle visibility box, beside Addresses.
        //
        // Restored FIRST, before anything is listening: setting a combo box fires its listeners, and the one below
        // redraws the whole panel, which during construction is still being built.
        showDirectionsView(VIEW_PREFS.getInt(PREF_DIRECTIONS, DIRECTIONS_DEFAULT));

        showLengths.setSelected(VIEW_PREFS.getBoolean(PREF_LENGTHS, false));

        showUnmeasured.setSelected(VIEW_PREFS.getBoolean(PREF_UNMEASURED, false));

        showTrains.setSelected(VIEW_PREFS.getBoolean(PREF_TRAINS, true));

        // ONE REMEMBERED SETTING, and the two boxes follow it (FR-061).  The two older preferences are deliberately not
        // read: between them they could describe states this control cannot be in (both on, or a caption choice with the
        // text switched off), and migrating a contradiction opens a window that disagrees with itself.
        int rememberedMode = VIEW_PREFS.getInt(PREF_CAPTION_MODE, CAPTIONS_STATIONS);
        boolean rememberedText = VIEW_PREFS.getBoolean(PREF_TEXT_LABELS, false);

        // A remembered Labels Only opens as Station Names with the Text Labels box ticked, and is stored that way so the
        // box is not ticked again at every open (FR-133).
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

        // Control+L steps through these (OB-272), and says so the way the Text Labels box said it.
        captions.setToolTipText("Control+L");

        showParkedTrains.setFocusable(false);
        showHomeLocomotives.setFocusable(false);

        applyCaptionMode();

        // Not focusable, like every other control in this window (OB-019): a focusable control takes the focus off the
        // FRAME, which the editor's keyboard shortcuts are bound to, so ticking this box would turn Ctrl+Z and Delete
        // off until something else was clicked.
        showLengths.setFocusable(false);

        // Control+G, which `LayoutEditor` binds to this toggle (OB-214) - written as the Text Labels,
        // Addresses and Grid toggles beside it write theirs.
        showLengths.setToolTipText(SHORTCUT_LENGTHS);

        // Not focusable either, for OB-019's reason above: a focusable box here takes the editor's shortcuts away.
        showUnmeasured.setFocusable(false);
        showUnmeasured.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipShowUnmeasured")));

        // Nor this one (OB-019)
        showTrains.setFocusable(false);
        showTrains.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipShowTrains")));

        // Nor the Text Labels box under it (OB-019; FR-133)
        showText.setFocusable(false);
        showText.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipShowTextLabels")));

        // NOR THE DIRECTIONS BOX, which the window mounts in its Toggle visibility box (OB-200), for the same reason
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

        // Redrawn, the squares' annotations with them: the squares ask `isShowingTrains` as they paint, and the annotations
        // mark where the setup puts a train only while it says yes (RSA63-C3).  The whole window repaints as well as the
        // refresh (RSA64-C1): a refresh redraws only the squares whose annotation changes, but the trains' icons are
        // drawn from the RAILWAY, which can hold a train the setup does not place (the setup unable to build, the last
        // valid railway kept).
        showTrains.addActionListener(e ->
        {
            VIEW_PREFS.putBoolean(PREF_TRAINS, showTrains.isSelected());
            refresh();

            javax.swing.JRootPane window = javax.swing.SwingUtilities.getRootPane(this);

            if (window != null) window.repaint();
        });

        // The diagram's text follows the box and nothing else (FR-133): the dropdown below decides the captions.
        showText.addActionListener(e ->
        {
            VIEW_PREFS.putBoolean(PREF_TEXT_LABELS, showText.isSelected());

            applyTextLabels();
        });

        captions.addActionListener(e ->
        {
            VIEW_PREFS.putInt(PREF_CAPTION_MODE, captions.getSelectedIndex());

            applyCaptionMode();

            // The GRID, not just a repaint: a caption's text is decided when the diagram is built.
            if (onDiagramChanged != null) onDiagramChanged.run();
        });

        hint.setFont(FONT_HINT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(hint);

        // The count is mounted by the WINDOW, under the findings list, where it is a total.
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
     * Stretches buttons to the width of the column, as the window's own Save and Cancel are.  Matching the widest of
     * themselves left a ragged margin down the right of the column.
     */
    private void fillWidth(javax.swing.AbstractButton... buttons)
    {
        for (javax.swing.AbstractButton button : buttons)
        {
            button.setPreferredSize(new Dimension(WIDTH, button.getPreferredSize().height));
        }
    }

    /**
     * One control on its own line, flush left and no taller than it needs to be.
     */
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
     * Silent: it runs on the way into the right-click menu, whose own work would replace any message a moment later.
     */
    private void cancelPendingGesture()
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
     * Every tool toggle, so that arming one disarms the rest.  Deliberately NOT a ButtonGroup, which makes the selection
     * permanent: these have to be un-pressable to get back to the ordinary state where clicking a square edits it.
     */
    private final java.util.List<JToggleButton> toolButtons = new java.util.ArrayList<>();

    private JToggleButton toolButton(final Tool which, String text)
    {
        final JToggleButton button = new JToggleButton(text, false);

        toolButtons.add(button);

        button(button);

        button.addActionListener(e ->
        {
            // Every OTHER tool button comes up.  Otherwise two could look pressed at once, and un-pressing the stale one
            // set the tool to NONE while the other still looked armed; the next click then fell through to cycle(),
            // which CHANGES a square's direction, so an inspection tool that looked armed silently edited the railway.
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

    /**
     * Lets go of any half-finished gesture, without changing which tool is armed.  It must be complete: a one-way run
     * left waiting for its far end swallows the next click anywhere.
     */
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
     * Puts every tool down (OB-119), and the BUTTONS with them.
     *
     * Every tool, because a key that works for one of five teaches a rule and then breaks it.  The buttons must come up
     * too: a tool left looking armed while the panel thinks nothing is armed sends the next click to `cycle()`, which
     * CHANGES a square, so a read-only inspection tool that looked armed would silently edit the railway.
     *
     * It says whether there was anything to put down (FR-065): Escape either lets go of the gesture or, when there is
     * none, closes the editor, and returning the answer keeps the question and the act in one place.
     *
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
     * Whether a click on a train drawn on the diagram is a click on the station it stands on: while Test a Path or Why Not
     * Moving? waits for a station and no gesture has the click first (OB-354).
     *
     * @return true where the train's square is the answer
     */
    public boolean takesATrainForItsStation()
    {
        return (tool == Tool.TEST || tool == Tool.WHY) && tailFor == null && oneWayFrom == null && signalFor == null
            && blockingFor == null;
    }

    /**
     * Whether a tool is armed or a two-click gesture is half made.
     *
     * ONE DEFINITION, asked by `putToolsDown` and by `cancelPendingGesture`: a gesture missing from one list survives
     * being cancelled, and the click after it goes somewhere the user did not intend.
     *
     * @return whether this panel is holding anything
     */
    private boolean anythingIsArmed()
    {
        return tool != Tool.NONE || testFrom != null || oneWayFrom != null
            || pendingPortal != null || signalFor != null || blockingFor != null || tailFor != null;
    }

    private JScrollPane buildFindings()
    {
        findings.setVisibleRowCount(8);

        // NOT FOCUSABLE, like every other control of this window (OB-200).  The shortcuts are the window's own key
        // handler, so a click on a finding that took the keyboard from it would never give it back; a row is still
        // picked by the mouse.
        findings.setFocusable(false);

        // The window's control size, not the hint size: these are the sentences the reader is here to read.
        findings.setFont(FONT_CONTROL);

        // Same colours as the Auto tab's list: red for what must be fixed, amber for what is worth checking, grey for
        // the headings.
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

                // The section headings take the window's group-heading style (Segoe UI Semibold in navy).
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

        // Clicking a finding goes to the tile it is about: which square, on a page of two hundred.
        findings.addListSelectionListener(e ->
        {
            if (e.getValueIsAdjusting()) return;

            int row = findings.getSelectedIndex();

            // AND ITS TEXT ON THE CLIPBOARD, for sharing (MT-767): a notice only, a heading has no severity (RSA75-C3)
            if (row >= 0 && row < findingSeverity.size() && findingSeverity.get(row) != null)
            {
                copyNoticeToClipboard(findingsModel.get(row));
            }

            if (row >= 0 && row < findingTiles.size() && findingTiles.get(row) != null)
            {
                TileKey at = findingTiles.get(row);

                // AND THE SQUARES IT IS ABOUT BESIDE ITS OWN (MT-505): a guard notice's own square is the station but the
                // signal it names is what is wrong, so both are outlined.  Every finding is outlined and flashed the same
                // strong way, whether or not it names other squares (OB-352)
                List<TileKey> related = row < findingRelated.size() && findingRelated.get(row) != null
                    ? findingRelated.get(row) : java.util.Collections.<TileKey>emptyList();

                // A finding on another page is reached by opening that page, which this window (built around one diagram)
                // cannot do in place.  So it hands the square to the main window, which closes this editor and opens one
                // there, with the squares the finding names beside its own for that editor to outline (RLV9-C5).
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

        // The window's heading style on the border's title.
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
     * Everything that can be set on a tile, on one menu.
     *
     * Built the way LayoutEditorRightclickMenu builds its own (a bold disabled title, plain items in the look-and-feel's
     * font, separators between groups), so right-clicking in autonomy mode looks like right-clicking anywhere else in
     * this window.  The point settings are items rather than a dialog, in the order and with the wording keys of the
     * graph window's point menu.
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
     * The same menu, built and handed back rather than shown, so the main window's own right-click menu can carry it:
     * the track diagram there is the same diagram, and one menu built once cannot drift into offering different things
     * in the two places.
     *
     * @param tile which square
     * @param component what is drawn there, or null to read it off the page
     * @return the menu, or null where this square has nothing to offer
     */
    public javax.swing.JPopupMenu buildTileMenu(TileKey tile, LayoutDiagramComponent component)
    {
        if (tile == null || session == null || session.getGraph() == null) return null;

        if (component == null) component = componentAt(tile);

        // Right-clicking abandons whatever gesture was in progress: a half-finished path test left armed would swallow
        // the next ordinary click after the menu closes.
        cancelPendingGesture();

        // A text square carries no track, but a station can be WRITTEN on it, and that must not need another editor.
        LayoutDiagramComponent onPage = componentAt(tile);

        // The page has to be one the session knows, or "no component here" would mean "I could not find the page"
        // and every square on it would offer to become a station label.
        if (pageOf(tile) != null && (onPage == null || onPage.isText()))
        {
            return buildTextMenu(tile, onPage);
        }

        // Nothing on an ignored square is the user's to set, so it says so rather than offering a menu of no-ops.
        //
        // EXCEPT BULK TOOLS, which are not about this square (V35-C1): "ignored" includes the page being excluded from
        // autonomy, so on such a page every square answered this way and Clear All Home Locomotives could not be
        // reached, though its subject is not that page and its own guard does not ask about one.
        if (isIgnored(tile))
        {
            say(hint, I18n.t("autosetup.ui.infoTileIgnored"));

            javax.swing.JPopupMenu only = new javax.swing.JPopupMenu();

            // AND TAKING A CAPTION OFF, which the setup check names wherever it sits - on a page left out of
            // autonomy too (OB-341).
            if (!menuOnly && session.getCaptionTarget(tile) != null)
            {
                menuTarget = tile;

                addCaptionItems(only, tile, onPage, "", session.getCaptionTarget(tile), true);
                only.addSeparator();
            }

            only.add(bulkTools());

            return only;
        }

        // Right-clicking anywhere in a run opens the run's own menu: the greyed tiles hand the question to the tile that
        // answers it.  A new local rather than the parameter, which the lambdas below capture and so must stay
        // effectively final.
        final TileKey target = leaderOf(tile);

        // Remembered so every item on this menu can flash what it changed without being told twice.
        menuTarget = target;

        // AND ITS PAIR, when this square is joined to another one on this page (FR-056): Adam, *"when in the autonomy
        // editor and you right click a tunnel tile, highlight its pair."*  The menu's Go to the partner is right when the
        // partner is on another page and wrong on this one, where jumping moves the view away from the square asked
        // about.  Only on this page: a highlight on a page nobody is looking at is not one.
        highlightPartnerOf(target);

        rememberIfStation(target);

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        boolean isPoint = session.getReducer() != null
            && session.getReducer().getPoints().containsKey(target);

        title(menu, isPoint ? pointTitle(target) : component == null
            ? target.getX() + "," + target.getY() : component.getUserFriendlyTypeName());

        // Built where the station settings are and added beside its other half further down.  Null when the square has
        // only one way in, which is not a question worth asking.
        javax.swing.JMenu arrivals = null;

        // Everything a STATION can be tuned with, under one heading, and added LAST (OB-013).  Null on a square that is
        // not a station: Segment Length stays loose on the menu, so plain track can set a length in one click.
        javax.swing.JMenu advanced = null;

        // Held and added at the foot of the menu - see where it is built
        javax.swing.JMenuItem signalItem = null;

        // And the entry guard's, beside it (FR-096)
        javax.swing.JMenuItem entryItem = null;

        if (isPoint)
        {
            // Locomotives first, because placing one is the commonest reason to open this menu; the designations below
            // are settled early and rarely touched again.
            //
            // PUTTING one down is only at a station: a locomotive can only be SENT to a station.  TAKING one off is not,
            // because the square under a train can stop being a station while the train stays recorded on it (switched to
            // pass-through, or an imported setup), and the only way to clear the placement would be to make the square a
            // station again.  So that item follows the LOCOMOTIVE, as the track diagram's own menu does.
            final boolean isStation = session.getStore().isStation(target);
            final String standing = locomotiveAt(target);

            // The locomotive's own settings (Place, Facing, Remove) are left out of the DEEP menu: the track diagram's own
            // menu carries them one level up.  In the editor, with no menu above this one, they stay.
            // Adam's order (MT-053): put one here, edit what is here, take it away, then which way round it faces, then
            // which station it belongs to.
            //
            // Adding stays in the deep menu: the diagram's own menu places the locomotive selected on the KEYBOARD, while
            // this asks which locomotive, the only way to put a train on a square without selecting it first.
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

                        // The setup's new answer for this train is "nowhere", and it is an answer -
                        // without naming it the rebuild puts the train straight back (D2-A1).
                        placementChanged(java.util.Collections.singletonList(standing));
                    }));
            }

            // WHETHER IT RUNS (FR-117): an 'active' checkbox, shown where there is a locomotive at that station.  Read off
            // the setup and written to the train and the setup together.  Not in the deep menu, whose train items are on
            // the track diagram's own menu; and only at rest.
            if (standing != null && !menuOnly && (parentWindow() == null || !parentWindow().aRunHoldsTheSetup()))
            {
                menu.add(toggle(I18n.f("autolayout.ui.menuLocomotiveActive", standing),
                    "autolayout.ui.hintLocomotiveActive", !session.getPausedLocomotives().contains(standing),
                    on -> setActive(standing, on)));
            }

            if (isStation)
            {
                // No "Move a Locomotive to This Station..." (OB-009): the edit dialog below also asks which locomotive, and
                // does more.  The list "Add to Autonomy" offers is the whole roster, so it does what Move did.

                // Which way round the train is standing.  Asked here because the user is looking at the square, with the
                // track drawn either side of it.  Offered only where the answer could be more than one thing, and only
                // while a train is on it: it is a fact about the train rather than the track.
                //
                // Nobody has to answer it.  Left alone it takes the first facing, and the moment autonomy runs, where the
                // train ends up says which way it was pointing and that is written back.  This is the escape hatch for
                // the first run, and for a train put on the rails backwards.
                //
                // Built by buildFacingMenu so the two surfaces cannot drift (OB-039: a missing redraw had to be added to
                // both copies).  It returns null on exactly the conditions this would otherwise test itself.
                if (!menuOnly)
                {
                    javax.swing.JMenu facingMenu = buildFacingMenu(target);

                    // ONE MENU FOR BOTH ENDS OF THE TRAIN.  The tail question is inside that menu under its own heading, so
                    // both surfaces get it: the viewer asks TrainControlUI for `buildFacingMenu` and for nothing else.
                    if (facingMenu != null) menu.add(facingMenu);
                }

                // Last of the locomotive group (MT-104).  A home is a fact about a LOCOMOTIVE (which one belongs here), so it
                // goes with the items about the train rather than the ones about the platform.
                if (isStation)
                {
                    String home = homeOf(target);

                    javax.swing.JMenuItem homeItem = item(home == null
                        ? I18n.t("autosetup.ui.menuHomeNone") : I18n.f("autosetup.ui.menuHomeFor", home),
                        () -> promptHome(target));

                    // THE KEY THAT DOES THIS WITHOUT THE MENU (OB-214).  See `SHORTCUT_HOME`.  Not on the track diagram's
                    // menu, which borrows this one: the key works only in the editor.
                    if (!menuOnly) homeItem.setToolTipText(SHORTCUT_HOME);

                    menu.add(homeItem);
                }
            }

            if (standing != null || isStation) menu.addSeparator();

            javax.swing.JMenuItem renameItem =
                item(I18n.t("autosetup.ui.menuRename"), () -> promptName(target));

            // And Control+S, the key that names the square under the pointer (OB-214), in the editor only: on the track
            // diagram it swaps key mappings
            if (!menuOnly) renameItem.setToolTipText(SHORTCUT_NAME);

            menu.add(renameItem);

            menu.addSeparator();

            // What trains may do at this square, as one three-way choice: stop, pass through, or neither.  Mutually
            // exclusive by construction, so there is no combination to get wrong and nothing to grey out.
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

            // Out of service.  What the square IS is left alone, so switching back returns it to what it was.
            stationMenu.add(radio(group, I18n.t("autosetup.ui.menuNeither"),
                "autolayout.ui.tooltip.Active", !isOpen,
                () -> setUsage(target, isStation, false)));

            stationMenu.addSeparator();

            // Autonomy's own choosing, said directly.  Switched off, a route the user picks still reaches this station and
            // Return Home still fills it; only full autonomy leaves it alone.
            javax.swing.JCheckBoxMenuItem auto = toggle(I18n.t("autosetup.ui.menuAutoDestination"),
                "autosetup.ui.hintAutoDestination", session.isAutoDestination(target),
                on -> session.setAutoDestination(target, on));

            // Greyed rather than hidden, so the shape of the choice stays visible.
            //
            // NOT greyed for an out-of-service square (Adam, 2026-09-01): *"Can Be Chosen in Full Autonomy - does not apply
            // to returning home, of course.  these should be allowed."*  Requiring `isOpen` made the menu a trap: the
            // obvious way to say "do not let autonomy pick this" is to close the square, which disabled the control that
            // says it properly and left only the flag that ALSO stops trains going there at all.  `active` is whether the
            // railway may use the square; this is whether autonomy chooses it as a destination.  Homing never consults
            // it (HomeStaging does not mention it), which is what makes a parking berth work.
            //
            // Still greyed on a square that is not a station: autonomy cannot choose somewhere trains do not stop.
            auto.setEnabled(isStation);

            stationMenu.add(auto);

            // HOW LONG A TRAIN MAY BE TO STOP HERE (FR-046).  With the usage choices, because it answers the same
            // question: what may stand at this platform.  Added only when the square IS a station.  The label carries the
            // current value, as everything on this menu does.
            if (isStation)
            {
                stationMenu.addSeparator();

                int maxLength = number(target, "maxTrainLength", 0);

                javax.swing.JMenuItem maximum = item(I18n.f("autolayout.ui.menuMaxTrainLength",
                    maxLength == 0 ? I18n.t("autolayout.ui.any") : String.valueOf(maxLength)),
                    () -> promptNumber(target, "maxTrainLength",
                        "autolayout.ui.promptEnterMaxTrainLength", 0));

                // WHAT THE NUMBER MEANS, AND THE KEY THAT SETS IT.  The units are the operator's own, so "4" says nothing
                // about carriages, centimetres or squares, and a reader cannot discover by opening the dialog that a
                // shortcut exists.
                maximum.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipMaxTrainLength")
                    + (menuOnly ? "" : "  " + I18n.t("autosetup.ui.tooltipMaxTrainLengthKey"))));

                stationMenu.add(maximum);
            }


            menu.add(stationMenu);

            // Switching direction, on any square, and not entangled with anything above: a berth is usually both left
            // alone by autonomy AND reversing in.  Three answers, because "may" and "must" are different railways.  May
            // leaves the plain copies, so a train can pass straight through and the path finder chooses; must leaves only
            // the turning ones, so every arrival turns.  On a dead end they are the same thing.
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

            // Where trains may pull IN from, on the menu itself beside what the square IS: one says what the square is
            // for, the other which ends of it are open.
            //
            // Only where there is more than one way in.  Barring the only arrival side would leave a station no train
            // could be sent to; somebody who wants that wants a pass-through, which is the choice above.
            final java.util.List<org.traincontrol.automationui.TilePorts.Side> ways =
                session.arrivalSides(target);

            // Shown GREYED where there is only one way in, rather than left out (MT-079): an absent menu and a menu with
            // nothing to offer look identical from the outside, and a greyed one with a tooltip answers without being
            // asked.
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

                    // The LAST way in cannot be shut here either: unticking them one at a time is the same as barring a
                    // single-sided station.
                    allow.setEnabled(barred.contains(side)
                        || session.arrivalsCanBeClosed(target, java.util.Collections.singleton(side)));

                    arrivals.add(allow);
                }

                // Held rather than added here.  It is one half of a pair (which ends trains may come IN by, which ends they
                // may go OUT by) and the two are added together, below.
            }

            // The signal that is thrown to red while this platform is claimed.  Paired by hand rather than inferred: the
            // nearest signal on the approach is not always the one that protects a platform, and a wrong guess throws a
            // real signal on real hardware.
            //
            // Last of the station settings, under the arrivals it belongs beside: what the square IS, which way trains turn
            // on it, which ends they may come in by, then what is held against them while one is standing there, each
            // answer assuming the one above it.
            //
            // Labelled with the signal's ADDRESS, which is how anybody refers to a signal, and outlined on the diagram
            // while the item is being acted on.  Left out of the deep menu: pairing one means clicking the signal on the
            // diagram, which the deep menu is drawn over.
            if (isStation && !menuOnly)
            {
                java.util.List<TileKey> paired = session.getProtectingSignals(target);

                // HELD rather than added (MT-104).  It goes at the foot of the menu, under the arrive/depart pair and
                // beside Advanced Parameters, where Adam put it: the last of the platform's own settings.
                signalItem = item(
                    paired.isEmpty()
                        ? I18n.t("autosetup.ui.menuPairSignal")
                        : I18n.f(paired.size() == 1
                            ? "autosetup.ui.menuPairedSignal" : "autosetup.ui.menuPairedSignals",
                            signalAddresses(paired)),
                    () -> pairProtectingSignal(target));

                // WHAT THE GUARD DOES (OB-293), said as the runtime does it, `Layout.refreshProtectingSignal`.
                signalItem.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipExitGuard")));

                // THE ENTRY GUARD, the same dialog on the other list (FR-096), and labelled the same way.
                java.util.List<TileKey> guarding = session.getEntrySignals(target);

                entryItem = item(
                    guarding.isEmpty()
                        ? I18n.t("autosetup.ui.menuPairEntrySignal")
                        : I18n.f(guarding.size() == 1
                            ? "autosetup.ui.menuPairedEntrySignal" : "autosetup.ui.menuPairedEntrySignals",
                            signalAddresses(guarding)),
                    () -> pairGuardSignals(Guard.ENTRY, target));

                // And this one's, as `Layout.throwEntryGuard` does it (OB-293).
                entryItem.setToolTipText(wrapped(I18n.t("autosetup.ui.tooltipEntryGuard")));
            }


            // Everything a station can be TUNED with, under one heading: the settings a railway works without (station
            // priority, speed multiplier, which locomotives are kept out or let in, and blockedByPoints, FR-001).  Every
            // label carries its current value, as the graph window's did.
            advanced = new javax.swing.JMenu(
                I18n.t("autolayout.ui.menuEditAdvancedParameters"));

            // Maximum Train Length is not here: it is in the Station submenu (FR-046), because it is a fact about a
            // platform and means nothing on a square that is not one, while Advanced is shown for squares that are not
            // stations.  Adam: *"move the Maximum Train Length right click setting into the Station submenu, rather than
            // Advanced, and hide it if the point is not a station."*
            int priority = number(target, "priority", 0);

            advanced.add(item(I18n.f("autolayout.ui.menuStationPriority",
                priority == 0 ? I18n.t("autolayout.ui.default") : String.valueOf(priority)),
                () -> promptNumber(target, "priority",
                    "autolayout.ui.promptEnterStationPriority", 0)));

            advanced.add(item(I18n.f("autolayout.ui.menuSpeedMultiplier", percent(target)),
                () -> promptPercent(target)));

            // named for its mode (FR-121): the locomotives kept out, or the only ones let in
            advanced.add(item(I18n.f(allowsOnlyListed(target) ? "autolayout.ui.menuAllowedLocomotivesOnly"
                : "autolayout.ui.menuExcludedLocomotives", strings(target, "excludedLocs").size()),
                () -> promptLocomotives(target, "excludedLocs", allLocomotives())));

            // FR-001: the same idea as excluded locomotives, about PLACES rather than trains - *"we should be able to
            // exclude the autonomous selection of a station when another (specified) point is occupied"*.  It sits beside
            // them as a restriction on what autonomy may choose.
            advanced.add(item(I18n.f("autolayout.ui.menuBlockedByPoints",
                session.getStore().getBlockingPoints(target).size()),
                () -> promptBlockingPoints(target)));

            menu.addSeparator();
        }

        // Everything about where trains may run, under one heading: each item is a different sentence about the same
        // subject.
        javax.swing.JMenu connections = new javax.swing.JMenu(I18n.t("autosetup.ui.menuConnections"));

        // Headed, because there are four kinds of thing in here once a square has several arms
        title(connections, I18n.t("autosetup.ui.menuDepartHeading"));

        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes = session.getRoutes(target);

        // A link has no direction of its own, by decision: it just links, and the track either side of it governs which
        // way trains may run.  Its one route is a stub (the same side twice), so "toward A" and "toward B" name the same
        // place; offering the four answers would be a setting that silently does nothing.
        //
        // Pinned by testALinkIsNotOfferedADirection, because that is the whole guard - see the note on
        // TileGraph.PORTAL_ROUTE.  One-way cross-page running needs the jump itself to carry a direction (backlog).
        if (session.canCarryDirection(target))
        {
            boolean many = routes.size() > 1;

            // Every arm, tickable, all visible together.  A click steps through four common answers and cannot reach the
            // rest; these are the arrows themselves, one box each, so any combination is one look and one tick - and on a
            // switch they are the whole of its settings (OB-351).
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

            // NO ROAD OF ITS OWN WHERE ROADS SHARE AN ARM (OB-351; Adam: *"into spur should simply require S to be green,
            // and out of spur should require W to be green.  Align it so that the arrows tell the full story."*).  On a
            // switch a road's own submenu could shut its spur's way out at the toe while the main line's arrow there stayed
            // green, a closure the diagram does not show; the arm boxes above are the whole of what such a square can be
            // told.  A crossing's or a double curve's roads end at arms of their own, so each road's submenu stays.
            boolean byArmsOnly = many && session.getGraph().sharesAnArm(target);

            for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> entry : routes.entrySet())
            {
                org.traincontrol.automationui.TilePorts.Route route = entry.getValue();

                if (byArmsOnly) break;

                // A switch's branches each get their own submenu; a plain target puts its four answers straight on the menu.
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

        // No "Make a One-Way Run from Here" item (OB-006): everything else on this menu names ONE square and acts on it,
        // while a one-way run waits for a second click, which is a mode, and a mode advertised from a menu that closes the
        // moment you pick it shows the user nothing.  It is a button beside Test and Why.

        // The two halves of the same question, side by side: which ends trains may arrive by, and which they may leave
        // by.
        if (arrivals != null) menu.add(arrivals);

        // Only when there is something under the heading.  Everything that fills this submenu sits inside
        // canCarryDirection(target), false for any portal, so right-clicking a link or a tunnel would open a greyed
        // heading and a separator (the state hasItemsBesidesTitle detects, OB-032).
        if (hasItemsBesidesTitle(connections)) menu.add(connections);

        // Then a divider, the signal, and the drawer of numbers (MT-104).  Everything above is a decision about this
        // square; what is left is the platform's protection and the settings that change how well those decisions work.
        if (signalItem != null || advanced != null) menu.addSeparator();

        if (signalItem != null) menu.add(signalItem);

        if (entryItem != null) menu.add(entryItem);

        if (advanced != null) menu.add(advanced);

        // A link's own settings, on the menu ITSELF rather than inside the departures submenu.  A link is a jump to
        // another page, not track with directions, and the three things anybody does to one (use it or not, pair it,
        // unpair it) are the whole reason to right-click it.
        if (component != null && (component.isLink()
            || component.getType() == LayoutDiagramComponent.componentType.TUNNEL))
        {
            menu.addSeparator();

            // No heading of its own (OB-054): the menu is already titled with what the square IS, "Page Link", and the
            // separator above is enough to group them.
            // Autonomy can be told to leave a link alone entirely: a diagram can carry one that belongs to the drawing
            // rather than to the railway autonomy runs, and refusing to build until it is paired would insist on
            // something the user has decided against.
            menu.add(toggle(I18n.t("autosetup.ui.menuUseLink"),
                "autosetup.ui.hintUseLink",
                !session.getStore().isPortalDisabled(target),
                on ->
                {
                    session.setPortalDisabled(target, !on);

                    // A shut link is a missing edge in the running graph, so the railway has to be told (VD10-B2).  This
                    // writer is not inside a door, which is why it pushes no undo point (VD9-B3).
                    setupChanged();
                }));

            menu.add(item(I18n.t("autosetup.ui.menuPairLink"), () -> pairFromList(target)));

            final TileKey partner = session.getStore().getPortalPartner(target);

            if (partner != null)
            {
                // The other end: a pairing is the one thing on this menu ABOUT somewhere else, usually on another page,
                // which the drawing cannot show.
                menu.add(item(I18n.f("autosetup.ui.menuGoToLinkPartner", linkLabel(partner)),
                    () -> goToLink(partner)));

                // AND THE RAILWAY HAS TO BE TOLD, because this item REMOVES an edge (GSE-B1).  Like `pairFromList` and
                // `setPortalDisabled` it changes an edge in the running graph, while Go To Link Partner is navigation and
                // tells the railway nothing (VD17-C5).  `session.unpairPortal` rebuilds only the editor's own graph, so
                // on the track-diagram door, which saves at once, the unpair would reach disk while autonomy went on
                // routing through it.
                menu.add(item(I18n.t("autosetup.ui.menuUnpairLink"),
                    () ->
                    {
                        session.unpairPortal(target);

                        setupChanged();
                    }));
            }

            // And a rule under the group: above it is about the link as a link, below it about the SQUARE, the same items
            // any other square gets.
            menu.addSeparator();
        }

        // Naming a link, on the menu itself rather than inside Connections.  A link is FOUND by its name (the pairing
        // list offers every other link by name, and an unnamed one shows as a coordinate pair nobody recognises), so it
        // is the first thing done to a new link.  What a link is CALLED is not a connection; it sits beside Set Length,
        // the other property of the square that has nothing to do with what joins to it.
        if (component != null && (component.isLink()
            || component.getType() == LayoutDiagramComponent.componentType.TUNNEL))
        {
            menu.add(item(I18n.t("autosetup.ui.menuSetName"), () -> promptLinkName(target)));
        }

        // On the menu itself rather than inside Advanced Parameters (OB-013): Advanced belongs to a STATION and a length
        // to any square, so keeping it there would build that submenu for plain track too.
        //
        // OFFERED WHERE `offersALength` SAYS, AND BOUND TO THE SQUARE IT NAMES (FR-066).  Control+E and this item ask
        // the same question, and two copies of it agree only by being typed identically; so there is one.
        // Structurally the condition is already true at this point, and the call is what keeps the two doors together.
        if (offersALength(tile))
        {
            javax.swing.JMenuItem lengthItem = item(I18n.t("autosetup.ui.menuSetLength"),
                () -> applyLength(squareTheLengthWouldGoOn(tile)));

            // OB-214: the tooltip names Control+E, where Control+E does it
            if (!menuOnly) lengthItem.setToolTipText(SHORTCUT_LENGTH);

            menu.add(lengthItem);
        }


        // A station name can go on almost any square, not only on a text square: the label is drawn beside the tile
        // wherever it sits, and on a platform the sensible place for the name is the platform road itself.
        //
        // Allowed on curves and dead ends (OB-042, OB-044): a bumper at the end of a siding and the curve at the top of a
        // loop are often the only squares near a station with nothing else to say.  The rule is Adam's: *"the only fair
        // place to disallow them are clickable elements like switches and signals"*, because those already do something
        // when clicked, and a caption on one puts text over a control.
        //
        // The CLICKED square, not the run leader the rest of this menu acts on: a name belongs where it was put.
        //
        // Editor only, both of them: they write text onto the DIAGRAM, and the deep menu is reached by right-clicking the
        // diagram itself.
        final LayoutDiagramComponent here = componentAt(tile);

        if (mayCarryACaption(here) && !menuOnly)
        {
            menu.addSeparator();

            String text = here == null || here.getLabel() == null ? "" : here.getLabel();

            addCaptionItems(menu, tile, here, text, session.getCaptionTarget(tile), false);
        }
        else if (!menuOnly && session.getCaptionTarget(tile) != null)
        {
            // A caption sits on this square all the same (a switch or signal built where one was already shown, or a
            // setup edited by hand) and the setup check names it (OB-341), so it has to be possible to take it off here.
            menu.addSeparator();

            addCaptionItems(menu, tile, here, "", session.getCaptionTarget(tile), true);
        }

        // BULK TOOLS, LAST (Adam, 2026-09-11: *"can we make 'bulk tools' be the last item in the right-click menu in the
        // autonomy editor?  mind the separators."*).  They are about the WHOLE SETUP rather than this square (MT-257), so
        // they belong at the bottom behind a divider; in the middle, a bare "Clear All Home Locomotives" read as being
        // about the square under the pointer.  A submenu rather than loose items, as he asked.
        //
        // The separator is added unconditionally and `tidy` below decides whether it survives (it drops a divider with
        // nothing after it and collapses two in a row), so this cannot leave a stray line above the submenu.
        menu.addSeparator();

        menu.add(bulkTools());

        tidy(menu);

        return menu;
    }

    /**
     * Takes the gaps out of a menu that was built in sections.
     *
     * The menu is assembled by a dozen independent blocks, each adding a divider and then whatever it has to offer for
     * THIS square, and a block with nothing to offer leaves the divider behind (OB-054: a heading, a divider, nothing
     * at all, another divider).  Rather than teach every block to look ahead, the shape is corrected once, here, at
     * the end:
     *
     *   - a divider at the top or the bottom has nothing to separate
     *   - two dividers in a row have nothing between them
     *   - a heading with nothing under it is a title for an empty section
     *
     * A heading is an ITEM, so "heading, divider, divider" is not two adjacent dividers until the heading is
     * recognised and removed.  Repeated until nothing changes.
     *
     * Public so its rules can be given a menu and asked what they do with it: they are about the SHAPE of a list of
     * components and nothing else, and the half that matters most is the half the real menus never produce.
     *
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

                // A heading: the disabled item title() leaves behind.  Disabled and NOT a separator: an ordinary item that
                // is disabled is a real offer the user cannot take right now, and removing those would hide them.
                boolean heading = here instanceof javax.swing.JMenuItem
                    && !here.isEnabled();

                java.awt.Component next = at + 1 < menu.getComponentCount()
                    ? menu.getComponent(at + 1) : null;

                // PAST the divider (OB-112).  title() writes a heading and a divider TOGETHER, so asking whether a divider
                // followed the heading was yes for every heading, and the name at the top of the menu was deleted a line
                // after it was added.  An empty SECTION is a heading, a divider and then nothing: what says it is empty is
                // what comes after the divider.  A divider followed by another divider, or by the end, is still nothing
                // at all (OB-054).
                java.awt.Component afterNext = at + 2 < menu.getComponentCount()
                    ? menu.getComponent(at + 2) : null;

                // TWO tests, because the two rules ask different questions.  A DIVIDER has nothing to separate when nothing
                // follows it, or another divider does.  A HEADING is always followed by its own divider, so for a heading
                // the question is asked one component further along.  They must not share a variable: widening it for the
                // heading narrowed it for the divider, and two dividers in a row stopped being collapsed unless a THIRD
                // followed (OB-054).
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

    /**
     * Flashes the square the open menu belongs to, so a menu edit is as visible as a click.
     */
    private void flashMenuTarget()
    {
        if (menuTarget != null && onReveal != null) onReveal.accept(menuTarget);
    }

    /**
     * Flashes the square this one is joined to, when that square is on the page being looked at (FR-056).
     *
     * A link or a tunnel is the one kind of square whose meaning is somewhere ELSE; on a pair that is on one page (what a
     * tunnel usually is) the jump is the wrong gesture, because it moves the view away from the square just asked about.
     *
     * Silent where there is no partner, where the partner is on another page, and where the square is not a link at
     * all: a flash anywhere else would be a lie about what is joined to what.
     *
     * @param tile the square whose menu is opening
     */
    private void highlightPartnerOf(TileKey tile)
    {
        if (tile == null || onReveal == null || menuOnly) return;

        LayoutDiagramComponent here = componentAt(tile);

        if (here == null) return;

        if (!here.isLink() && here.getType() != LayoutDiagramComponent.componentType.TUNNEL) return;

        TileKey partner = session.getStore().getPortalPartner(tile);

        if (partner == null || !onThisPage(partner)) return;

        onReveal.accept(partner);
    }

    // The square numbers (coordinates) are on by default and follow the grid (OB-179): see `LayoutGrid.coordinatesVisible`,
    // the only place the rule is written.  Control+K turns the grid, and them with it.

    /**
     * The actions that are about the whole setup rather than about one square (MT-257, V35-C1).
     *
     * Built here because there are two doors onto it: the ordinary tile menu, and the one-item menu an IGNORED square
     * gets.  A square on a page excluded from autonomy has nothing of its own to set, but a setup-wide action is not
     * about that page.
     *
     * Each item greys on its OWN count, which is the guard's own question: the actions are independent and a setup can
     * easily have one and not the other.  Neither asks about the page.
     *
     * @return the submenu, always present, its items enabled or not
     */
    private javax.swing.JMenu bulkTools()
    {
        javax.swing.JMenu bulk = new javax.swing.JMenu(I18n.t("autosetup.ui.menuBulkTools"));

        bulk.setToolTipText(wrapped(I18n.t("autosetup.ui.hintBulkTools")));

        // ONE-WAY RUN AND NAME EVERYTHING, IN THE AUTONOMY EDITOR ONLY (OB-217).  The track diagram's tile menus are
        // this panel's menus served with no page, so `page` is what tells the two apart.  Both items drive the objects
        // whose state the refresh and the disarm path already keep.
        if (page != null)
        {
            if (oneWayButton != null)
            {
                javax.swing.JMenuItem oneWay = item(I18n.t("autosetup.ui.toolOneWay"), () ->
                {
                    // Through the button's own action, which un-presses every other tool and clears the gesture; the arming
                    // rules live there and are not repeated here.
                    if (!oneWayButton.isSelected()) oneWayButton.doClick();
                });

                oneWay.setToolTipText(oneWayButton.getToolTipText());

                // GREYED ON A PAGE LEFT OUT, as Mass Assign Lengths below it is (OB-235, MT-528).
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

            // MASS ASSIGN LENGTHS: cycles through each relevant square.  Greyed on its own count, which is the walk's
            // own question, the same shape as Name Everything above it.
            int piecesToMeasure = session == null ? 0 : session.stretchesNeedingALengthOn(page).size();
            int switchesToMeasure = session == null ? 0 : session.switchesNeedingALengthOn(page).size();

            // AND THE SQUARES TWO ROADS SHARE, asked for on their own as switches are.
            int crossingsToMeasure = session == null ? 0 : session.sharedSquaresNeedingALengthOn(page).size();

            boolean leftOut = session != null && session.getStore().getExcludedPages().contains(page);

            javax.swing.JMenuItem massAssign =
                item(I18n.t("autosetup.ui.menuMassAssignLengths"), () -> massAssignLengths());

            massAssign.setEnabled(piecesToMeasure + switchesToMeasure + crossingsToMeasure > 0);

            // A PAGE LEFT OUT says so (MAL-C8): its count is 0 because the graph has no such page, and "everything here
            // already has a length" would be vacuously true of a page nothing had looked at.
            massAssign.setToolTipText(wrapped(leftOut
                ? I18n.t("autosetup.ui.infoPageLeftOutNothingToMeasure")
                : piecesToMeasure + switchesToMeasure + crossingsToMeasure > 0
                    ? I18n.f("autosetup.ui.tooltipMassAssignLengths", piecesToMeasure, switchesToMeasure,
                        crossingsToMeasure)
                    : I18n.t("autosetup.ui.infoNothingToMeasure")));

            bulk.add(massAssign);

            // MASS ASSIGN STATION MAX TRAIN LENGTHS: walks the stations that have no maximum.  Greyed on the walk's own
            // count, as the item above.
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

            // MASS ASSIGN TRAIN LENGTHS, as MT-567 named it (FR-094).  Not a page's question, since a train is not on a
            // page, so a page left out does not change it.
            int trainsToMeasure = trainLengthDoor().trainsWithoutALength().size();

            // NEVER GREYED, AND THE COUNT IN ITS LABEL (MT-533: *"This option should never be greyed out completely (show
            // the number of missing trains in parens)"*).  With none missing, the walk goes through every train with the
            // length it has.  Named, because its text changes with the count.
            javax.swing.JMenuItem massAssignTrains = item(
                I18n.f("autosetup.ui.menuMassAssignTrainLengthsCounted", trainsToMeasure), () -> massAssignTrainLengths());

            massAssignTrains.setName("massAssignTrainLengths");

            // WHAT A CLICK WILL DO (TDU-C7): the missing ones, every train, or - with none placed - nothing, as the click
            // itself says.
            massAssignTrains.setToolTipText(wrapped(trainsToMeasure > 0
                ? I18n.f("autosetup.ui.tooltipMassAssignTrainLengths", trainsToMeasure)
                : trainLengthDoor().trainLengths().isEmpty() ? I18n.t("autosetup.ui.infoNoTrainToMeasure")
                : I18n.t("autosetup.ui.infoEveryTrainHasALength")));

            bulk.add(massAssignTrains);

            bulk.addSeparator();
        }

        // The same set the bulk home door writes (SEV-C3): `tilesWithALocomotive` reads every page and
        // the door skips excluded ones, so the two disagreed on a layout with a page left out.
        int placed = session == null ? 0 : session.placementsAutonomyWillWrite().size();
        int homed = session == null ? 0 : session.tilesWithAHome().size();

        javax.swing.JMenuItem clearLocs = item(
            I18n.f("autolayout.ui.menuClearLocomotives", placed), () -> clearAllPlacements());

        clearLocs.setEnabled(placed > 0);

        // THE DIALOG'S SENTENCE IN SHORT (OB-194; FR-112): the count and what Cancel does, through the builder that
        // picks the dialog's sentence, so the two cannot drift; concise, so a tooltip does not cover up other things.
        // The names are the dialog's, which shows them before anything is cleared.
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

        // CLEAR ALL TRACK LENGTHS (FR-069): clears the segment lengths across all pages, after the user confirms in a
        // popup.  Built like the two above it, down to the tooltip being the dialog's sentence in short (FR-112), through
        // the builder that picks the dialog's.
        int measured = session == null ? 0 : session.tilesWithALength().size();

        javax.swing.JMenuItem clearLengths = item(
            I18n.f("autolayout.ui.menuClearAllTrackLengths", measured),
            () -> clearAllTileLengths());

        clearLengths.setEnabled(measured > 0);
        // THE DIALOG'S SENTENCE IN SHORT, whichever of the two that is (AUS-C2; FR-112).
        clearLengths.setToolTipText(wrapped(measured > 0
            ? bulkClearWarning("autosetup.ui.tipClearAllTrackLengths", measured)
            : I18n.t("autosetup.ui.infoNoTrackLengthsToClear")));

        bulk.add(clearLengths);

        // CLEAR ALL MAX TRAIN LENGTHS, grouped with the other clear options and built like the three clears above it,
        // down to the tooltip being the dialog's sentence in short.
        int limited = session == null ? 0 : session.tilesWithAMaxTrainLength().size();

        javax.swing.JMenuItem clearMaxima = item(
            I18n.f("autolayout.ui.menuClearAllMaxTrainLengths", limited), () -> clearAllMaxTrainLengths());

        clearMaxima.setEnabled(limited > 0);
        clearMaxima.setToolTipText(wrapped(limited > 0
            ? bulkClearWarning("autosetup.ui.tipClearAllMaxTrainLengths", limited)
            : I18n.t("autosetup.ui.infoNoMaxTrainLengthsToClear")));

        bulk.add(clearMaxima);

        // ALLOW EVERY PATH (FR-108): removes any red arrows in one go, with a warning.  Built like the clears above it,
        // down to the tooltip being the dialog's sentence in short.
        int oneWay = session == null ? 0 : session.routesNotOpenBothWays().size();

        javax.swing.JMenuItem allowAll = item(I18n.f("autolayout.ui.menuAllowEveryPath", oneWay), () -> allowEveryPath());

        allowAll.setEnabled(oneWay > 0);
        allowAll.setToolTipText(wrapped(oneWay > 0
            ? bulkClearWarning("autosetup.ui.tipAllowEveryPath", oneWay)
            : I18n.t("autosetup.ui.infoNothingOneWay")));

        bulk.add(allowAll);

        // HOME EVERY TRAIN WHERE IT STANDS (FR-075): marks current train locations as their homes.  Beside the three
        // clears and built like them, down to the tooltip.  It is the only one of the four that ADDS rather than removes,
        // so its confirmation says what it will overwrite rather than what it will lose: homing a train here takes its
        // home away from wherever else it had one, and a square already homed to a different train is reassigned.
        //
        // Counted off the same question that greys it - `trainsWhereTheyStand`, the set the door actually writes
        // (RSA18-B2: where the railway has the trains, not where the last run set them off from) - so the affordance and
        // the guard cannot answer differently (`guard-and-affordance-same-question`, and SEV-C3 where they did).
        int standing = session == null ? 0 : session.trainsWhereTheyStand().size();

        javax.swing.JMenuItem homeHere = item(
            I18n.f("autolayout.ui.menuHomeEveryTrainWhereItStands", standing),
            () -> homeEveryPlacedTrain());

        homeHere.setEnabled(standing > 0);
        homeHere.setToolTipText(wrapped(standing > 0
            ? I18n.f("autosetup.ui.tipHomeEveryTrainWhereItStands", standing)
            : I18n.t("autosetup.ui.infoNoLocomotivesToHome")));

        bulk.add(homeHere);

        // PLACE ALL AT THEIR HOMES (FR-115): teleports locomotives to their home stations, facing the correct way, and
        // clears all other locomotives from other stations (without actually moving anything).  Beside Home All Trains
        // Where They Stand, its opposite: that one moves the homes to the trains, this one the trains to their homes.
        // Counted off the set the door walks (`homesToPlace`), so the affordance and the guard ask one question.
        int homes = session == null ? 0 : session.homesToPlace().size();

        javax.swing.JMenuItem placeHome = item(
            I18n.f("autolayout.ui.menuPlaceEveryTrainAtHome", homes), () -> placeEveryTrainAtHome());

        // ON THE TRACK DIAGRAM, RETURN HOME'S QUESTION (FR-129): the live setup, so the item is refused while anything runs
        String placeRefused = menuOnly && parentWindow() != null
            ? parentWindow().whyTheTrainsCannotBePlacedAtTheirHomes()
            : homes > 0 ? null : I18n.t("autosetup.ui.infoNoHomesToPlace");

        placeHome.setEnabled(placeRefused == null);
        placeHome.setToolTipText(wrapped(placeRefused == null
            ? I18n.f("autosetup.ui.tipPlaceEveryTrainAtHome", homes)
            : placeRefused));

        bulk.add(placeHome);

        // ACTIVATE AND DEACTIVATE EVERY TRAIN (FR-126): the Is Active tick for many trains at once, counted off the set
        // the tool walks, greyed on its own count with the reason in its tooltip, and saying how many it changed.
        // Activate covers every train that is paused, on the layout or not (`pausedTrains`); Deactivate every train
        // standing on the layout that is going (`goingTrainsStanding`), since pausing a train that is not placed would
        // only surprise the user when it is.  Not while trains run, as the tick is not: `item` refuses it, as it refuses
        // every setup edit.  Nothing to confirm: each tool undoes the other, and the tick sets one train back.
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
     * The trains standing on the layout, by name, each once - the ones the Is Active tick is offered for, and the ones
     * Activate All Trains and Deactivate All Trains walk (FR-126).  Where the railway has the trains, and the setup's
     * placements where it has not (`trainsWhereTheyStand`).
     *
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

    /**
     * The model's train of this name, or null where there is no window or the model has none (FR-126).
     *
     * @param name the train
     * @return the train
     */
    private org.traincontrol.base.Locomotive modelTrain(String name)
    {
        TrainControlUI window = parentWindow();

        return window == null || window.getModel() == null ? null : window.getModel().getLocByName(name);
    }

    /**
     * Whether a train is paused (FR-126): what the TRAIN says where the model has it, and the setup's list only for a name
     * the model has no train for.  The Auto tab's pause button changes the train alone and the setup learns of it later,
     * so the train is the later word: a list that still names a train the button has set going is out of date.
     *
     * @param name the train
     * @param listed the trains the setup lists as paused
     * @return whether it is paused
     */
    private boolean isPaused(String name, java.util.Set<String> listed)
    {
        org.traincontrol.base.Locomotive train = modelTrain(name);

        return train != null ? train.isAutonomyPaused() : listed.contains(name);
    }

    /**
     * Every train that is paused, on the layout or not (FR-126): the model's trains whose flag is set, and the names the
     * setup lists that the model has no train for.  What Activate All Trains counts and sets going.
     *
     * @return the names, sorted
     */
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

    /**
     * Every train standing on the layout that is going (FR-126).  What Deactivate All Trains counts and pauses.
     *
     * @return the names
     */
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
     * Sets every paused train going, or pauses every train standing on the layout that is going, and says how many that
     * changed (FR-126).  Through the window's door where there is a window and a train of that name, as the Is Active tick
     * goes - the train and the setup together, saved once - and through the setup alone for a name the model has no train
     * for; then the railway is told, as every setup door tells it.
     *
     * Activate also clears what the setup lists for a train the model has, going or not, so a record the card's button has
     * outrun does not stand.
     *
     * The emptiness check is kept though the items grey on the same count: the greying is the affordance and this is the
     * guard.
     *
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

        // THE TRAINS ALREADY AS ASKED GO THROUGH THE DOOR TOO.  The door writes the train and the setup's list as one, so
        // a record the card's button has outrun does not stand: Activate takes off the list every train it names, the
        // ones the button set going among them, which `covered` leaves out as it reads the train.  The rebuild's fold
        // applies only the trains the railway gained or lost against the record (OB-343), so a train the setup lists
        // that the railway was not built pausing would be paused again by the rebuild this asks for.  Deactivate's line
        // is not needed, since the fold lists a train a card paused all the same; it is harmless, because the door notes
        // in the record only the trains it changes (`notePausesAsBuilt`).
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
     * Makes every placed train's current square its home, after confirming (FR-075).
     *
     * Built like `clearAllHomes` beside it: the emptiness check is kept even though the item greys itself on the same
     * question, because the greying is the AFFORDANCE and this is the GUARD (a train can be moved between one refresh
     * and the next).
     *
     * The confirmation names what will be overwritten rather than what will be lost, because this is the one bulk tool
     * that adds: a train homed here loses its home elsewhere, and a square homed to another train is reassigned.  Both
     * follow from `AutonomySession.setHome`, which is where the one-locomotive-one-station rule lives.
     */
    private void homeEveryPlacedTrain()
    {
        // COUNTED OFF THE SAME SET THE DOOR WALKS (SEV-C3), where the railway has the trains (RSA18-B2).
        // `tilesWithALocomotive()` reads every page while the bulk door skips excluded ones, so on a layout with a page
        // left out the operator would be told a number the gesture would not deliver.
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

        // THE RUNNING LAYOUT TOO, for the reason `clearAllHomes` gives (VD10-B2): otherwise Return Home goes on offering
        // the arrangement the operator has just replaced.
        setupChanged();
    }

    /**
     * Puts every train that has a home on it, facing the way it was homed, and takes every other train off the layout,
     * after confirming - the setup only, nothing sent to the railway (FR-115).
     *
     * Built like `homeEveryPlacedTrain` beside it: the emptiness check is kept though the item greys itself on the same
     * question, because the greying is the affordance and this is the guard.  The rebuild that follows is told every
     * train the session touched, or it would put the ones it missed back where the railway had them.
     */
    private void placeEveryTrainAtHome()
    {
        // ON THE TRACK DIAGRAM AS FROM RETURN HOME (FR-129): there is no banner and no Cancel, so it asks as Return Home's
        // item asks, re-checks the same question, and says the result in the log
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
     * The door behind Place All Trains at Their Homes, for the editor's menu and for the main window's Return Home
     * button, which has no editor open (FR-129).  The caller's question is asked again once the confirmation has been
     * answered, because the railway can change while it is on screen.
     *
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

        // the trains standing that have no home, which this takes off
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
     * For each home square, the way its locomotive faces on the copy the running layout made its home - what
     * `placeEveryTrainAtHome` uses for a home recorded without a facing (FR-115).  Read off `getPoints` and the Points
     * themselves, which take none of the railway's monitor; empty where there is no running layout yet.
     *
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

    /**
     * For tests: the Bulk Tools menu as the right-click menu builds it (OB-194).
     *
     * A tooltip is a property the menu ITEM carries, so the only way to ask whether the warning reaches the menu is to
     * build the menu.  Through `bulkTools()` itself, for `clickCommandMarkForTest`'s reason: a second construction
     * could agree with itself while the real one is wrong.
     *
     * @return the menu, freshly built
     */
    public javax.swing.JMenu buildBulkMenuForTest()
    {
        return bulkTools();
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

    /**
     * Whether a setup edit is refused because trains are running, having said so (Adam, 2026-09-28: *"There should be no
     * setup edit possible during a run"*).  The diagram's Autonomy Setup menu is offered only at rest and the editor
     * cannot be opened during a run (OB-047), but a menu opened at rest can be clicked after a run has begun, so this is
     * asked at every item's click (RLV11-B1, RLV11-B2).
     *
     * @return true where the edit is refused
     */
    private boolean refusedWhileRunning()
    {
        TrainControlUI window = parentWindow();

        // A ROUTE TOO (`TrainControlUI.aRunHoldsTheSetup`)
        if (window == null || !window.aRunHoldsTheSetup()) return false;

        JOptionPane.showMessageDialog(owner(), window.whyTheSetupIsHeld("autolayout.errorCannotEditWhileRunning"));

        return true;
    }

    /**
     * The same refusal for a gesture on the diagram or a key, which has no menu item to ask first: said on the message
     * strip rather than in a dialog the next click would raise again.  An editor opened at rest stays open when a route
     * begins, so every write the diagram's clicks and the shortcuts make asks this first.
     *
     * @return true where the gesture is refused
     */
    private boolean heldByARun()
    {
        TrainControlUI window = parentWindow();

        if (window == null || !window.aRunHoldsTheSetup()) return false;

        say(hint, window.whyTheSetupIsHeld("autolayout.errorCannotEditWhileRunning"));

        return true;
    }

    /**
     * A menu item that runs something, redraws, and flashes the square it changed.  No font is set: the editor's own
     * menus use the look and feel's.
     *
     * The flash matters more here than on a click: the menu covers the tile while it is open, and on a run the square
     * that changes is the head of the run rather than the one right-clicked.
     */
    private javax.swing.JMenuItem item(String text, final Runnable action)
    {
        javax.swing.JMenuItem menuItem = new javax.swing.JMenuItem(text);

        menuItem.addActionListener(e ->
        {
            // NOT WHILE TRAINS RUN - see `refusedWhileRunning`
            if (refusedWhileRunning()) return;

            try
            {
                action.run();
            }
            catch (RuntimeException ex)
            {
                // The class as well as the message: a NullPointerException has no message, and a dialog whose whole
                // content is the word "null" tells nobody anything.
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
     * How wide to wrap text so the longest of these sentences takes two lines - and never narrower than `atLeast` (MT-479).
     *
     * Half the longest sentence's width, and the longest word over, because a line breaks between words and the half
     * rarely falls between two.
     *
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
     * Text wrapped at a width, as `wrapped` wraps a tooltip at its own.
     *
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
     * A tooltip that wraps instead of running off the screen.
     *
     * Swing lays a tooltip out on one line however long it is, and these explain what a setting MEANS rather than
     * naming it, so the widest ones ran past the edge of the display.  The width is in pixels because that is the only
     * unit the renderer here understands.
     *
     * @param text
     * @return the same text, wrapped
     */
    static String wrapped(String text)
    {
        if (text == null || text.trim().isEmpty()) return text;

        // Already somebody's own markup: left exactly as it is
        if (text.trim().toLowerCase().startsWith("<html")) return text;

        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

        // ONE LINE WHERE IT FITS ON ONE (MT-613): the width is the body's, so a short reason would be set in a box as
        // wide as a long one wraps at.  Still HTML, as every tooltip from here is (ADU-C4).
        if (fitsOnOneLine(text)) return "<html>" + escaped + "</html>";

        return "<html><body style='width: " + TOOLTIP_WIDTH + "px'>" + escaped + "</body></html>";
    }

    /**
     * A tooltip wrapped where it is too wide for one line, and left exactly as it was where it is not - a shortcut such as
     * "Control+A" stays plain text (FR-112).
     *
     * @param text the tooltip, or null
     * @return the tooltip, wrapped if it needs to be
     */
    public static String wrappedIfLong(String text)
    {
        if (text == null || text.trim().toLowerCase().startsWith("<html") || fitsOnOneLine(text)) return text;

        return wrapped(text);
    }

    /**
     * Every tooltip on a menu, its items and its submenus, wrapped where it is too wide for one line (FR-112).
     *
     * For the menus whose tooltips are set where nobody wraps them: the main window's menu bar, set in the generated
     * block, and the right-click menus, as they are shown.
     *
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
     * TOOLTIP_WIDTH in the pixels the text is measured in.  Swing draws a CSS width at the screen's resolution, so
     * on a 120-dpi screen the 320px body is 400 pixels; testing one line against 320 wrapped a tooltip that then
     * sat in a box 400 wide (OB-358).
     *
     * @return the wrapping width, in pixels
     */
    private static int wrappingWidth()
    {
        int dpi = java.awt.GraphicsEnvironment.isHeadless() ? 96
            : java.awt.Toolkit.getDefaultToolkit().getScreenResolution();

        return dpi > 0 ? (int) Math.round(TOOLTIP_WIDTH * dpi / 96.0) : TOOLTIP_WIDTH;
    }

    /**
     * Whether a tooltip's text is one line no wider than the wrapping width, in the font tooltips are drawn in.
     *
     * @param text the text
     * @return true where it fits
     */
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
            // NOT WHILE TRAINS RUN - see `refusedWhileRunning`
            if (refusedWhileRunning()) return;

            // Guarded, as `item` beside it is (C11): a RuntimeException out of setBarredArrivals, setPortalDisabled or
            // setAutoDestination would otherwise skip placementChanged entirely, so the box would show the new state, the
            // setup keep the old one, and the findings list and error count never be recomputed.  The redraw happens
            // either way: a menu that has thrown is a menu whose picture of the setup is least likely to be right.
            try
            {
                action.accept(menuItem.isSelected());
            }
            catch (RuntimeException ex)
            {
                // SAID, the way item() says it: a guard that makes a failure quieter than it was is not a guard.  The class
                // as well as the message, for item()'s reason: a NullPointerException has no message.
                String said = ex.getMessage() == null || ex.getMessage().trim().isEmpty()
                    ? ex.getClass().getSimpleName() : ex.getMessage();

                JOptionPane.showMessageDialog(owner(), I18n.f("error.generic", said));

                say(hint, said);
            }

            // placementChanged, not refresh (TD-1).  Every one of these writes the SETUP, and four are reachable from the
            // track diagram's own right-click menu, where nothing else picks the change up: whether trains may arrive by a
            // side, which way an arm runs, whether a link is switched off, and whether autonomy may choose this square.
            // The first decides how a square SPLITS (how many Points it becomes and what they are called), the very case
            // the rebuild exists for; refreshing alone redraws this menu over a running layout that still holds the old
            // Points.
            placementChanged(null);

            flashMenuTarget();
        });

        return menuItem;
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

        // What the square is showing: a station's caption, the user's own text, or nothing.  The caption is an autonomy
        // object drawn here, not text on the diagram, so it is asked for rather than read off the label.
        TileKey captioned = session.getCaptionTarget(tile);

        title(menu, captioned != null ? describeTile(captioned)
            : label.trim().isEmpty() ? I18n.t("autosetup.ui.titleEmptyText") : label);

        // Offered on a blank square too: writing a station name on a blank square is how a diagram with no text squares
        // gets its first one.
        //
        // And offered over somebody's own text, asking instead of refusing.  A refusal left a state with no way out (a
        // station moved and moved back, its square now holding leftover text, the item greyed so the label could not be put
        // back at all).  So it asks, naming the text it would replace: a question is a way out; a disabled item is not.
        addCaptionItems(menu, tile, component, label, captioned, false);

        // AND BULK TOOLS, as every square of track ends with (MT-474: *"when right clicking an empty square, show the
        // bulk tools menu option."*).  They are about the whole setup rather than the square under the pointer, so they go
        // at the bottom behind a divider, as `buildTileMenu` places them (MT-257).
        menu.addSeparator();

        menu.add(bulkTools());

        tidy(menu);

        return menu;
    }

    /**
     * "Show Station Here..." and its clear, added to whichever menu is being built.
     *
     * Shared so the two menus cannot drift into offering different things.
     *
     * @param menu the menu being built
     * @param tile the square
     * @param component what is drawn on it, or null for a blank square
     * @param label whatever text the square already carries
     * @param captioned the station this square is already showing, or null
     * @param stopOnly only the item that stops showing it, for a square that may not be given a caption but has one
     *        (OB-341)
     */
    private void addCaptionItems(javax.swing.JPopupMenu menu, final TileKey tile,
        final LayoutDiagramComponent component, String label, TileKey captioned, boolean stopOnly)
    {
        boolean mine = label.trim().isEmpty() || captioned != null;

        // Both items NAME the station when there is one (FR-014).  A caption draws the station's OCCUPANT, and an empty
        // station draws as GraphLocAssign.NONE_LABEL, so a square captioning a station with no train on it says nothing
        // about which station it is, the ordinary state of most of the railway.  Here rather than in the two menus,
        // because both build their caption items through this method.  The deep menu also carries a title() naming the
        // station; the editor's own menu has no title.
        String showing = captioned == null ? null : describeTile(captioned);

        // The name is NOT repeated here (MT-162): the item below already reads "Stop Showing <name>".  The two keys
        // still differ: "a Station Name" when the square shows none, "a Different Station" when it already shows one,
        // which says which situation you are in without spending a line on which station it is.
        // A STATION SQUARE HAS THREE STATES, AND ONLY ONE OF THEM HAS NOTHING TO DO (SVV-C6, SVX-B2).
        //
        // `promptStationLabel` captions a STATION square with itself whatever it is showing (the test there is
        // `isStation(tile)` alone), so no station square is ever offered a choice.  What differs is whether choosing it
        // would CHANGE anything:
        //
        //   already showing itself    nothing to do.  Greyed, and the label says why.
        //   showing another station   resets it to its own.  Live - a drag can produce this.
        //   showing nothing           captions it with itself.  Live, and this is the state "Stop Showing" leaves
        //                             behind - greying it here would make Stop Showing one-way (SVX-B2).
        //
        // Neither live case is offered as "a DIFFERENT station", because a station square cannot show one; both read as
        // "Show a Station Name Here", which is what the action does.  The greyed item is disabled and reworded rather
        // than removed, and its tooltip carries the reason.
        boolean showsItself = session.getStore().isStation(tile) && tile.equals(captioned);

        boolean aStationSquare = session.getStore().isStation(tile);

        // Not on a square that may not carry one: it can only be taken off there (OB-341)
        if (!stopOnly)
        {
            javax.swing.JMenuItem name = item(
                showsItself ? I18n.t("autosetup.ui.menuStationShowsItself")
                    : showing == null || aStationSquare ? I18n.t("autosetup.ui.menuShowStationHere")
                    : I18n.t("autosetup.ui.menuShowStationHereNamed"),
                () -> promptStationLabel(tile, component));

            // Greyed, with the reason, on a square the running diagram does not draw (AD-C6)
            boolean inTheDrawnArea = session.isInTheDrawnArea(tile);

            name.setEnabled(!showsItself && inTheDrawnArea);

            // WHAT A CAPTION IS, on the item that makes one: nothing else on this menu says that a caption is a live
            // readout of another square rather than a name plate.
            name.setToolTipText(!inTheDrawnArea && !showsItself ? wrapped(I18n.t("autosetup.ui.captionOutsideTheDrawnArea"))
                : wrapped(
                (showsItself ? I18n.t("autosetup.ui.tooltipStationShowsItself")
                    : !mine ? I18n.t("autosetup.ui.tooltipTextInTheWay")
                    : I18n.t("autosetup.ui.tooltipShowStationHere"))
                // AND ITS KEY (FR-086), added to the sentence rather than replacing it: this item's tooltip is the only
                // place that says what a caption is.
                //
                // IN THE EDITOR ONLY (MFX-C3): the main window builds this menu with `setMenuOnly`, and there Control+N
                // is the notes key.
                + (menuOnly ? "" : "  (" + SHORTCUT_STATION + ")")));

            menu.add(name);
        }

        if (captioned != null)
        {
            // Naming what is about to be cleared matters here: this item is destructive, and "Clear This Square" on a
            // square reading [---] asks the user to confirm removing something they cannot identify.
            menu.add(item(I18n.f("autosetup.ui.menuClearStationHereNamed", showing),
                () -> applyCaption(tile, null)));
        }
    }

    /**
     * Whether a station's name may be written on this square (OB-042, OB-044).
     *
     * Any square except a switch or a signal, which ALREADY do something when clicked: hanging a caption on one puts
     * text over a control.  A bumper at the end of a siding or the curve at the top of a loop is often the only square
     * near the station with nothing else to say, and a platform road is where a name usually belongs.
     *
     * @param component what is drawn on the square
     * @return whether to offer the caption items
     */
    private boolean mayCarryACaption(LayoutDiagramComponent component)
    {
        if (component == null) return true;

        // Switches and signals, and NOT everything isClickable() covers: that also counts feedback, uncouplers, links,
        // lamps and routes, and feedback is the platform road, the commonest place for a station name.
        //
        // A switch and a signal are refused because a click on them throws real ironwork, and a caption over a control is
        // a caption in the way of it.  A sensor's click toggles a feedback reading: nothing moves.
        return !component.isSwitch() && !component.isSignal();
    }

    /**
     * Whether a dragged station label may land here (FR-035).
     *
     * The predicate the drag mark asks WHILE dragging and `moveCaption` asks on release, so what is shown green is
     * exactly what will be accepted (a highlight with a rule of its own is the fault behind OB-057 and OB-090).
     *
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
     * Why a dragged label may not land here, as something to say, or null when it may.
     *
     * One method rather than a boolean and a separate message, because a refusal and its reason go out of step the
     * moment they are written twice.  Returns the EMPTY STRING for the one refusal with nothing to report: a label
     * dropped back on the square it came from is not a mistake.
     *
     * @param from the square the label is on now
     * @param to the square under the pointer
     * @param onTarget what is drawn there
     * @return the message, "" for a silent no, or null for yes
     */
    private String refuseCaptionDrop(TileKey from, TileKey to, LayoutDiagramComponent onTarget)
    {
        if (session == null || from == null) return "";

        // Nothing to move.
        if (session.getCaptionTarget(from) == null) return "";

        // Dropped outside the diagram, which is a slip rather than an instruction.
        if (to == null) return I18n.t("autosetup.ui.errorDropOffDiagram");

        // Put back where it started.
        if (to.equals(from)) return "";

        if (from.getPage() == null || !from.getPage().equals(to.getPage()))
        {
            return I18n.t("autosetup.ui.errorDropOffDiagram");
        }

        // Outside the part of the page the running diagram draws, where nobody would see it (AD-C6)
        if (!session.isInTheDrawnArea(to)) return I18n.t("autosetup.ui.captionOutsideTheDrawnArea");

        // A control, where a click throws real ironwork and a name would sit in the way of it.
        if (!mayCarryACaption(onTarget)) return I18n.t("autosetup.ui.errorDropOnControl");

        // REFUSED rather than replaced: the name already there would disappear with nothing on the diagram to say it
        // ever existed.
        if (session.getCaptionTarget(to) != null)
        {
            return I18n.f("autosetup.ui.errorDropOccupied", describeTile(to));
        }

        return null;
    }

    /**
     * Moves a station label from one square to another (FR-035).
     *
     * The caption command run twice - cleared where it was, set where it landed - and NOT a new stored position, so
     * there is no extra field to carry through a page move, a rename, an insert or an undo.  Adam: *"it is equivalent to
     * just adding the label at the destination when the mouse is released, since the command is wired."*
     *
     * Every refusal is spoken: a drag that snapped back silently is indistinguishable from one the window failed to
     * notice.  The landing rule is `mayCarryACaption`, the same method the right-click menu asks before offering the
     * item, so the menu cannot offer what the drag refuses.
     *
     * @param from the square the label is on now
     * @param to the square it was dropped on
     * @param onTarget what is drawn on that square
     * @return whether the label moved
     */
    public boolean moveCaption(TileKey from, TileKey to, LayoutDiagramComponent onTarget)
    {
        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`
        if (heldByARun()) return false;

        String refused = refuseCaptionDrop(from, to, onTarget);

        if (refused != null)
        {
            // The empty string is the silent refusal: dropped back where it started.
            if (!refused.isEmpty()) say(hint, refused);

            return false;
        }

        TileKey station = session.getCaptionTarget(from);

        // ONE move, through the session's own method.  Two calls (clear, then set) would each end in `touched()` ->
        // `rebuild()`, so the window between them held a whole graph rebuild, and an exception out of the first would
        // leave the station with no caption anywhere (Cancel would still rewind it, but a Save afterwards would commit
        // the loss).  `AutonomySession.moveCaption` does it at store level with one rebuild.
        if (!session.moveCaption(from, to)) return false;

        if (onDiagramChanged != null) onDiagramChanged.run();

        say(hint, I18n.f("autosetup.ui.setStationLabel", describeTile(station)));

        refresh();

        // The running layout is what the captions are drawn from (VD11-A1).
        setupChanged();

        return true;
    }

    /**
     * The station most recently clicked or right-clicked, so a label knows what it is probably for.  Naming a square is
     * two steps (look at the station, then put its name on a square beside it), and by the second the editor would
     * otherwise offer an alphabetical list of every station with the one they are standing next to buried in it.
     */
    private TileKey lastStationTouched;

    /**
     * The station nearest this square, by tile distance (FR-034).
     *
     * Adam: *"prefill it with the label of the nearest station by tile distance."*  A label is put on a square near the
     * station it names, so the nearest station is the default, and unlike the last-clicked memory it is right without
     * anybody having done anything first.
     *
     * ON THIS PAGE ONLY: a page is a separate drawing with its own coordinates, so a distance to a station on another
     * page is two unrelated pairs of numbers subtracted.  A page with no stations gets no default.
     *
     * Squared distance, because only the ORDER matters.  Straight-line rather than counting steps along the track: the
     * label is placed by eye.
     *
     * Ties are broken by whichever the store lists first, deliberately not by name: sorting a genuine toss-up would
     * only make the answer look considered.
     *
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
     * The nearest of a set of squares, by straight-line distance on the same page (FR-034).
     *
     * Public and taking squares rather than a session so the rule can be checked without an autonomy graph, a window or
     * a railway: a station on another page, a tie, and nothing on the page.  The caller turns the graph into a list of
     * squares; this picks one.
     *
     * @param from the square the label would go on
     * @param stations the squares to choose between
     * @return the nearest on the same page, or null when there is none
     */
    public static TileKey nearestOf(TileKey from, java.util.Collection<TileKey> stations)
    {
        if (from == null || from.getPage() == null || stations == null) return null;

        TileKey best = null;
        long closest = Long.MAX_VALUE;

        for (TileKey at : stations)
        {
            // ANOTHER PAGE is not further away, it is not on the same drawing at all.
            if (at == null || !from.getPage().equals(at.getPage())) continue;

            long dx = at.getX() - from.getX();
            long dy = at.getY() - from.getY();

            // Squared: only the ORDER matters, and the square root does not change it.
            long away = dx * dx + dy * dy;

            if (away < closest)
            {
                closest = away;
                best = at;
            }
        }

        return best;
    }

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
     * The answer the question about taking another station's only name off a station square is given without asking, for
     * a test that is not about the dialog; null to ask (GSE-C5).
     */
    private static volatile Boolean takeAnotherNameAnswerForTests;

    /**
     * @param answer true for Yes or false for No, to answer the question about taking another station's only name off a
     *        station square without asking it (GSE-C5), or null to ask it again
     */
    public static void answerTakeAnotherNameForTests(Boolean answer)
    {
        takeAnotherNameAnswerForTests = answer;
    }

    /** The question last put about taking another station's only name, or answered for a test (OB-341). */
    private static volatile String takeAnotherNameQuestion;

    /**
     * @return the words of the question about taking another station's only name that was last put, or answered through
     *         `answerTakeAnotherNameForTests`, for a test of what it says (OB-341)
     */
    public static String takeAnotherNameQuestionForTests()
    {
        return takeAnotherNameQuestion;
    }

    /**
     * Whether a station square may show its own name where it now shows the only caption of ANOTHER station ON THIS PAGE -
     * asked of the operator, naming both, and true without a question where nothing would be lost (GSE-C5).
     *
     * A drag can leave a station square showing another station's caption, and captioning the square with itself takes
     * that caption away: the other station is then named nowhere on the diagram, which `AutonomyChecks.checkStationLabels`
     * reports as an error.  The drop refuses the same loss outright (`refuseCaptionDrop`).  "Show a Station Name Here" is
     * live on such a square on purpose (SVV-C6, SVX-B2), so this asks rather than refuses.
     *
     * Per page, as the captions are (OB-341): a page shows a station's name at most once, so the name on this square is
     * the station's only one on this page when no other square of the page names it, whatever its captions on other
     * pages.
     *
     * @param tile the station square
     * @return whether to go on
     */
    private boolean mayTakeAnotherStationsOnlyName(TileKey tile)
    {
        TileKey other = session.getCaptionTarget(tile);

        if (other == null || other.equals(tile) || !session.getStore().isStation(other)) return true;

        java.util.Set<TileKey> elsewhere = new java.util.LinkedHashSet<>(session.captionsFor(other));

        elsewhere.remove(tile);

        // On another page too: Yes leaves it shown there, so the question says only this page (OB-341)
        boolean shownOnOtherPages = !elsewhere.isEmpty();

        elsewhere.removeIf(where -> !tile.getPage().equals(where.getPage()));

        if (!elsewhere.isEmpty()) return true;

        // Its only caption anywhere: Yes leaves it shown nowhere, and the question says off the diagram
        String question = I18n.f(shownOnOtherPages ? "autosetup.ui.confirmTakeAnotherStationsName"
            : "autosetup.ui.confirmTakeAnotherStationsOnlyName", describeTile(other), describeTile(tile));

        takeAnotherNameQuestion = question;

        Boolean given = takeAnotherNameAnswerForTests;

        if (given != null) return given;

        // TrainControl's own buttons, as the question about replacing text asks, and No the default: Yes takes a name off
        // the diagram.  An INDEX comes back, where 0 is Yes.
        return JOptionPane.showOptionDialog(owner(), question,
            I18n.t("autosetup.ui.titleStationLabel"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) == 0;
    }

    /**
     * Asks which station this square should show.
     */
    private void promptStationLabel(TileKey tile, LayoutDiagramComponent component)
    {
        // Text of the user's own on this square is replaced only if they say so, naming the text in the question: the
        // person is doing this because they have lost track of the square's contents.
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

        // A station labelling ITSELF has nothing to ask about: a list of every station with this one's name buried in it
        // asks a question whose answer is known, and getting it wrong would put another platform's name on this one.
        if (session.getStore().isStation(tile))
        {
            // NOT ANOTHER STATION'S ONLY NAME WITHOUT ASKING (GSE-C5) - see `mayTakeAnotherStationsOnlyName`
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

        // What this square already shows, or failing that the station the user was last looking at.  Not the first name
        // alphabetically: a list opened on whichever station sorts first is a list to be searched rather than an answer
        // to be confirmed.
        TileKey showing = session.getCaptionTarget(tile);

        // The station just clicked, and ONLY for the next square (FR-034).  Taken rather than read: consumed here whether
        // or not it is usable, so a click on a platform defaults the one label that follows it and nothing after.
        if (showing == null)
        {
            showing = lastStationTouched;

            lastStationTouched = null;
        }

        // Failing both, the station this square is actually next to.
        if (showing == null) showing = nearestStation(tile);

        // Matched by SQUARE rather than by name.  The list is built from the REDUCED points, whose names are generated for
        // each way into a station, while the name a square is stored under is the authored one; for any station split by
        // its arrival sides those differ, so a name match answered no on exactly the stations most worth defaulting to.
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
     * Shows a station's name on a square, or stops showing it, and says so.
     *
     * Nothing is written to the layout file.  A caption belongs to the autonomy setup, so it is saved by the button
     * that says it saves autonomy and discarded by the Cancel that says it discards it.
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

        // The running layout is what the captions are drawn from (VD11-A1).
        setupChanged();
    }

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

    /**
     * The square of the station with this authored name, for turning a chooser's answer back into the thing a caption
     * actually points at.
     */
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
        // Yes or no, and nothing else: the heading answers one question, may a train stop here, and the radio inside
        // answers the rest.  A square that is out of service reads "no", because no train can stop on one.
        boolean stops = isStation
            && !Boolean.FALSE.equals(session.getPointProperty(tile, "active"));

        // The one exception, because it changes what the square is FOR: a station autonomy will not choose is a berth,
        // worth a mark you can see without opening anything.
        String star = stops && !session.isAutoDestination(tile) ? " *" : "";

        if (!stops) return I18n.t("autosetup.ui.stationNo") + star;

        // And which KIND of stop, where the square turns trains round: does every train reverse here, or may it.  They
        // are the two settings people confuse.
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

        // Says why nothing happened: a silent no-op leaves the user unable to tell a refusal from a bug.
        if (why != null)
        {
            say(hint, I18n.f(why, describeTile(tile)));
            return;
        }

        if (onDiagramChanged != null) onDiagramChanged.run();
    }

    /**
     * The window this panel's dialogs belong to (MT-182).
     *
     * `owner()` already returns a Window whenever this panel is in one, and `SwingUtilities.getWindowAncestor(owner())`
     * walks UP from what it is given: a top-level frame has no window ancestor, so it returns null (which centred the
     * signal window's dialog instead of offsetting it, and left the dialog's own constructor unowned).
     *
     * @return the window, or null when this panel is in none
     */
    private java.awt.Window ownerWindow()
    {
        java.awt.Component anchor = owner();

        // Already a window: asking for its ancestor is asking what it is INSIDE, and a top-level
        // window is inside nothing.
        if (anchor instanceof java.awt.Window) return (java.awt.Window) anchor;

        return javax.swing.SwingUtilities.getWindowAncestor(anchor);
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
     * Where to find the running layout, when there is one.
     *
     * A supplier rather than a reference, for the reason the session keeps one: `parseAuto` replaces
     * the layout object, and a field captured once goes stale at the first rebuild.
     */
    private java.util.function.Supplier<org.traincontrol.automation.Layout> runningLayout;

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
     * A bold, disabled line naming what the items under it are about: the same device the right-click menu heads itself
     * with, because a set of options with no title is a set the reader has to infer the subject of.
     *
     * @param text what the set below is about
     * @return the heading item
     */
    private static javax.swing.JMenuItem heading(String text)
    {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(text);

        item.setEnabled(false);
        item.setFont(item.getFont().deriveFont(java.awt.Font.BOLD));

        return item;
    }
    /**
     * Where the tail of the train standing here lies - the "arrived from" side.
     *
     * Adam, 2026-09-07: **"expose this property in the right-click menu advanced settings, both for the autonomy editor
     * and track diagram viewer."**
     *
     * **Built here rather than twice.**  `TrainControlUI.buildAutonomyTileMenu` serves this panel's menu to the track
     * diagram, so one implementation reaches both surfaces (two copies is how `OB-039` survived being fixed).
     *
     * The sides offered are the ones the railway HAS: `ArrivalSidePrompt.sidesOf` is the same list the placement prompt
     * uses, so the menu and the prompt cannot disagree about which answers exist.
     *
     * Null when no train is standing here: a tail belongs to a train.
     *
     * @param target the square
     * @return the menu, or null when there is nothing to offer
     */
    public javax.swing.JMenu buildArrivedFromMenu(final TileKey target)
    {
        if (target == null || locomotiveAt(target) == null) return null;

        org.traincontrol.automation.Layout running =
            runningLayout == null ? null : runningLayout.get();

        if (running == null) return null;

        org.traincontrol.automation.Point point = pointOnTheLayout(running, target);

        if (point == null) return null;

        // A TRAIN HAS TO BE STANDING THERE ON THE RUNNING RAILWAY (OP3-C7).  The offer above asks the SETUP (`locomotiveAt`
        // reads `session.getLocomotiveNameAt`) and `pointOnTheLayout` falls back to any copy, so a square the setup
        // assigns a train to while the running layout has not placed one would write a side onto an unoccupied Point,
        // which `Point.toJSON` declines to save (AUR-C1).  `appendTailCrossed` and `armTailPick` ask this too.
        //
        // WHAT IT COSTS (FXV-C6): `setupChanged` asks for a rebuild of the running layout, so the placement normally
        // catches up within the gesture and the item comes back.  It does not while the running layout is held (during a
        // run, and before the first rebuild after a configuration is loaded), and in those two states the item is absent
        // rather than greyed.
        if (point.getCurrentLocomotive() == null) return null;

        // THE BUILD'S SIDES, which the model reads as well (OB-182): the entry side travels in the configuration, and
        // `Layout.entrySideOf` is what both the arrival write and the tail walk consult, so offering it here stores a
        // value they can match.
        java.util.List<String> sides = new java.util.ArrayList<>(
            // UNBARRED (OB-204): this menu records where a train CAME FROM, and a side nothing may
            // arrive by is not one of those.  The "Trains May Arrive" menu above still offers every
            // side, because its whole job is to open and close them.
            org.traincontrol.gui.ArrivalSidePrompt.sidesOf(session.unbarredArrivalSides(target)));

        final String recorded = session.getArrivedFrom(target);

        // A RECORDED SIDE THE TRACK DOES NOT HAVE IS SHOWN, NOT OFFERED (RGD-C4; Adam: **"75 407 DB's menu now lets you
        // select arrival from the west at BottomMainPost, which makes no sense"**).  The radios are a ButtonGroup ticked
        // by `side.equals(recorded)`, so a recorded side not in the list would tick NOTHING and the section would read "no
        // tail recorded" for a square that has one, a value still steering the tail walk.  A track edit that re-plumbs a
        // square under a standing train leaves exactly that state.
        //
        // Not added to the list, as the facing menu does (OB-177): a facing the square cannot hold is still a facing, and
        // `facingsThatCannotBeHeld` reports it, while an arrival side the track does not have is not an answer at all
        // (the tail walk matches stored sides against the GEOMETRY, so it can never match).  So it appears once, ticked,
        // DISABLED, saying what it is: the menu does not lie about what is recorded and every clickable entry works.
        //
        // "Not offered here" rather than "not on the track": the list is the sides of the COPY this train is standing on
        // (`pointOnTheLayout` picks the occupied one), and a split square has several, so the label says what is
        // certain.
        final String recordedButGone =
            recorded != null && !sides.contains(recorded) ? recorded : null;

        if (sides.isEmpty() && recordedButGone == null) return null;

        javax.swing.JMenu menu = new javax.swing.JMenu(I18n.t("autosetup.ui.menuArrivedFrom"));

        menu.setToolTipText(wrapped(I18n.t("autosetup.ui.hintArrivedFrom")));

        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();

        for (final String side : sides)
        {
            menu.add(radio(group, org.traincontrol.gui.ArrivalSidePrompt.labelFor(side),
                "autosetup.ui.hintArrivedFrom",
                side.equals(recorded),
                () ->
                {
                    session.setArrivedFrom(target, side);

                    // A ROAD BACK DESCRIBES ONE SIDE (2026-09-14): the tail cannot lie along a road that leaves by
                    // another, so a changed side forgets it, here and on the railway below.
                    if (!side.equals(recorded)) session.setArrivedAlong(target, null);

                    // SAVED AND SHOWN, like every other door that writes a point property.
                    setupChanged();

                    // THE RUNNING LAYOUT TOO, not only the setup: what blocks track is the Point, and a setting that
                    // changes the diagram while the railway uses the old value is the shape SPEC-B4 had.
                    org.traincontrol.automation.Layout now =
                        runningLayout == null ? null : runningLayout.get();

                    if (now != null)
                    {
                        org.traincontrol.automation.Point on = pointOnTheLayout(now, target);

                        if (on != null) on.setArrivedFrom(side);

                        if (on != null && !side.equals(recorded)) on.setArrivedAlong(null);
                    }
                }));
        }

        if (recordedButGone != null)
        {
            javax.swing.JRadioButtonMenuItem gone = new javax.swing.JRadioButtonMenuItem(
                I18n.f("autosetup.ui.arrivedFromNotOnTrack",
                    org.traincontrol.gui.ArrivalSidePrompt.labelFor(recordedButGone)), true);

            // Not in the group, and not enabled: it is a statement about the square, not a choice.
            gone.setEnabled(false);

            gone.setToolTipText(wrapped(I18n.t("autosetup.ui.hintArrivedFromNotOnTrack")));

            menu.add(gone);
        }

        // NO WAY TO CLEAR THE SIDE, by Adam's ruling (2026-09-07): **"clearing should not be possible, only setting."**
        // Every side the geometry offers is above, so correcting a wrong answer means picking the right one, and clearing
        // only switched the tail blocking off, a preference about the check rather than a fact about the railway.
        // Autonomy writes the side on arrival and a hand placement works it out, so every train that reaches a square has
        // one.
        //
        // The automatic clear stays in Point.setLocomotive: a square whose occupant changes has a new train that did not
        // arrive the way the old one did, which is the railway dropping a fact it no longer holds, not a person declaring
        // ignorance.
        return menu;
    }

    /**
     * The "{loc} Is Facing..." submenu for a square, or null when there is nothing to ask.
     *
     * Handed out on its own so that the track diagram's own right-click menu can carry it, beside the other things it
     * offers about the train standing there (Place, Facing, Remove and the locomotive's settings); the deep menu does
     * not offer it.
     *
     * Null unless a locomotive is standing there.  Where the square has no facing to choose - no track leads out of
     * it - the submenu is there and greyed, with the reason in its tooltip (OB-347).
     *
     * @param target the square
     * @return the submenu, or null
     */
    public javax.swing.JMenu buildFacingMenu(final TileKey target)
    {
        if (target == null) return null;

        org.traincontrol.automation.Layout showing =
            runningLayout == null ? null : runningLayout.get();

        // THE TRAIN THE RAILWAY HAS HERE, and only then the setup's (TDY4-C5).  After a run the setup still names each
        // square's pre-run occupant (behaviour.md 6a) and nothing captures before this menu, so it would name the setup's
        // train and tick the railway's, and a click would turn neither.  `setFacingAndMove` turns the one named here.
        String onTheRailway = session == null ? null : session.trainOnTheRailway(target, showing);

        final String standing = onTheRailway != null ? onTheRailway : locomotiveAt(target);

        if (standing == null) return null;

        final java.util.List<org.traincontrol.automationui.TilePorts.Side> facings =
            new java.util.ArrayList<>(session.facingChoices(target));

        // WHAT THE RAILWAY SAYS, and only then what the file says (OB-181).  The station label reads which COPY of the
        // square the train is standing on, which on a split square IS its direction however the train got there, while
        // the stored facing is written only when somebody places a train by hand; so the two agreed until anything moved
        // a placed train.  One question, one answer, and the file is the fallback: an empty square has no train whose
        // direction this could be.
        org.traincontrol.automationui.TilePorts.Side facingOnTheRailway =
            session.facingOnTheRailway(target, showing);

        // NOT ONE THE TRACK DOES NOT RUN (OB-348), which the caption does not show and the icon cannot be drawn along
        final org.traincontrol.automationui.TilePorts.Side recorded =
            facingOnTheRailway != null ? facingOnTheRailway : session.recordedFacingAlongTheTrack(target);

        // AND WHATEVER IS ACTUALLY RECORDED, even when this square cannot hold it (OB-177).  The radios are a ButtonGroup
        // ticked by `facing == recorded`, so a recorded facing not among the offered ones would tick NOTHING, which reads
        // as "this train has no facing" when it has one this square cannot hold (`facingsThatCannotBeHeld` reports that
        // state).  It gets an entry rather than being silently corrected, because which of the two is wrong, the record
        // or the track under it, is not this menu's to decide: the finding says the square cannot hold it, the menu says
        // what the train is down as, and clicking any other entry resolves both.
        //
        // NOT WHERE NO TRACK LEADS OUT (OB-348): there the menu is greyed (OB-347) whatever is recorded, and a record the
        // track does not run was ticked in an open menu while the icon lay along the track.
        if (recorded != null && !facings.isEmpty() && !facings.contains(recorded)) facings.add(recorded);

        // SHOWN EVEN WITH ONE ANSWER, so the menu explains itself instead of vanishing: "no menu" reads as a fault rather
        // than as an answer.  And not one this menu may override: a facing has to be one the BUILD can hold, because
        // `placementCopy` matches it against the copies the split made and anything else falls back to `startableCopy`
        // and quietly turns the train round.  Offering a second facing where the railway holds one would be
        // `facingsThatCannotBeHeld` manufactured on purpose.  So the one facing is shown, ticked, with a line saying why
        // there is nothing to choose.
        //
        // AND GREYED, NOT HIDDEN, WHERE NO TRACK LEADS OUT (OB-347; Adam: *"rather than hiding it, grey it out."*), for the
        // same reason; its tooltip says why.
        //
        // ONE SUBMENU, greyed where there is nothing to choose (OB-348): built once, here, so the track diagram and the
        // editor cannot offer two that drift apart.
        javax.swing.JMenu facingMenu = new javax.swing.JMenu(
            I18n.f("autosetup.ui.menuFacingGroup", standing));

        if (facings.isEmpty())
        {
            facingMenu.setEnabled(false);
            facingMenu.setToolTipText(wrapped(I18n.t("autosetup.ui.hintFacingNoWayOut")));

            return facingMenu;
        }

        facingMenu.setToolTipText(wrapped(I18n.t("autosetup.ui.hintFacing")));

        // A HEADING, because this menu answers two questions (Adam, 2026-09-07: *"Move the setting into the '<locomotive>
        // is facing' menu for both, with clear intro headings for each set of options."*).  Two sets of compass points
        // with nothing between them would read as eight ways of saying one thing, and they are opposite ends of the
        // train.
        facingMenu.add(heading(I18n.t("autosetup.ui.headingFacing")));

        javax.swing.ButtonGroup facingGroup = new javax.swing.ButtonGroup();

        for (final org.traincontrol.automationui.TilePorts.Side facing : facings)
        {
            facingMenu.add(radio(facingGroup,
                I18n.t("autosetup.ui.facing" + facing.name()), "autosetup.ui.hintFacing",
                // NOTHING TICKED WHERE NOTHING IS KNOWN (GUI3-C4): with no facing recorded and none on the railway,
                // the build stands the train on a copy it can start from, which need not be the first facing.  Where
                // the square holds one facing, that one IS known - the only copy is where the train stands (DCN4-C3).
                recorded != null ? facing == recorded : facings.size() == 1,
                // The redraw is in radio() itself (TD-1), where every one of these answers gets it.
                //
                // AND THE RAILWAY, NOT ONLY THE SETUP (SPEC-B4): `setFacingAndMove` stands the train on the copy that says
                // the new direction, so the caption and the train cannot disagree until the next build.
                () -> session.setFacingAndMove(target, facing)));
        }

        if (facings.size() == 1)
        {
            javax.swing.JMenuItem only = new javax.swing.JMenuItem(
                I18n.t("autosetup.ui.facingOnlyOne"));

            only.setEnabled(false);
            only.setToolTipText(wrapped(I18n.t("autosetup.ui.hintFacingOnlyOne")));

            facingMenu.add(only);
        }

        // AND WHERE ITS TAIL IS, in the same menu (Adam, 2026-09-07: *"The 'arrived from' menu option is only visible in
        // the autonomy editor, not the track diagram viewer."*).  The two questions are opposite ends of one train and
        // belong together, and the viewer only ever asks for this menu.  Under its own heading, after a separator, so the
        // two sets of compass points cannot be read as one list.
        javax.swing.JMenu tail = buildArrivedFromMenu(target);

        if (tail != null && tail.getMenuComponentCount() > 0)
        {
            facingMenu.addSeparator();
            facingMenu.add(heading(I18n.t("autosetup.ui.headingArrivedFrom")));

            // The ITEMS, not the submenu: a submenu inside this one would be the third click for a question already
            // two levels down.
            while (tail.getMenuComponentCount() > 0)
            {
                java.awt.Component item = tail.getMenuComponent(0);

                tail.remove(0);
                facingMenu.add(item);
            }
        }

        // AND HOW FAR BACK ITS TAIL REACHES (2026-09-14): the same list as the placement question, in the same menu as
        // the side it follows from, on both surfaces - and in the editor a way to click the sensor instead.
        appendTailCrossed(facingMenu, target);

        return facingMenu;
    }

    /**
     * The "farthest sensor the tail crossed" choices for the train standing here, where the answer matters.
     *
     * Nothing is added unless `TailCrossedPrompt.wouldAsk` says a junction behind the train has two roads back that put
     * the tail on different track - it has crossed a sensor on one of them, or passed the switch where they part
     * (MT-477): elsewhere every answer describes the same track.  The ticked entry is the recorded road -
     * the running layout's, since that is what blocks track - matched as the longest choice it ends with, because a
     * driven train's road starts wherever it set off.
     *
     * @param menu the facing menu
     * @param target the square
     */
    private void appendTailCrossed(javax.swing.JMenu menu, final TileKey target)
    {
        org.traincontrol.automation.Layout running = runningLayout == null ? null : runningLayout.get();

        if (running == null) return;

        org.traincontrol.automation.Point point = pointOnTheLayout(running, target);

        if (point == null || point.getCurrentLocomotive() == null) return;

        String side = point.getArrivedFrom() != null ? point.getArrivedFrom() : session.getArrivedFrom(target);

        Integer length = point.getCurrentLocomotive().getTrainLength();

        if (!TailCrossedPrompt.wouldAsk(running, point, side, length)) return;

        java.util.List<TailCrossedPrompt.Choice> choices =
            TailCrossedPrompt.choicesFor(running, point, side, length, session::baseNameOf);

        TailCrossedPrompt.Choice ticked = TailCrossedPrompt.recordedChoice(choices, point.getArrivedAlong());

        menu.addSeparator();
        menu.add(heading(I18n.t("autosetup.ui.headingTailCrossed")));

        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();

        for (final TailCrossedPrompt.Choice choice : choices)
        {
            menu.add(radio(group, choice.getLabel(), "autosetup.ui.hintTailCrossed", choice == ticked,
                () -> recordTailRoad(target, choice.getRoad())));
        }

        menu.add(radio(group, I18n.t("autosetup.ui.tailCrossedNotKnown"), "autosetup.ui.hintTailCrossed",
            ticked == null, () -> recordTailRoad(target, null)));

        // CLICKING IS THE EDITOR'S: on the track diagram's menu this panel has no grid to be clicked on.
        if (!menuOnly && page != null)
        {
            menu.add(item(I18n.t("autosetup.ui.menuPickTailCrossed"), () -> armTailPick(target)));
        }
    }

    /**
     * Writes the road the tail lies along - to the setup, and to the train on the running railway.
     *
     * The railway as well as the file, for the reason the side radio gives: what blocks track is the Point.
     *
     * @param target the square the train stands on
     * @param road the road, or null for Not Known
     */
    private void recordTailRoad(TileKey target, java.util.List<org.traincontrol.automation.Edge> road)
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
     * Hands the next click on the diagram to the tail of the train standing at `target` (2026-09-14).
     *
     * The sensors it can have crossed are outlined, a click on one records its road, and a click anywhere else says
     * so and stays armed - the blocker pick's rule, because missing a small square is the ordinary mistake.  Where
     * two roads reach the same sensor the click is followed by the list, narrowed to those two.
     *
     * @param target the square the train stands on
     */
    private void armTailPick(TileKey target)
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

    /**
     * The square whose train's tail the next click answers for, or null (2026-09-14).
     */
    private TileKey tailFor;

    /**
     * The sensors that click may land on, by square, with the road each one means.
     */
    private final java.util.Map<TileKey, java.util.List<TailCrossedPrompt.Choice>> tailPicks =
        new java.util.LinkedHashMap<>();

    /**
     * Whether a click on the diagram is being waited for to say where a tail lies - for tests.
     *
     * @return the square whose train it is about, or null
     */
    public TileKey tailPickFor()
    {
        return tailFor;
    }

    /**
     * The squares outlined for that click - for tests.
     *
     * @return the squares, in the order they were found
     */
    public java.util.Set<TileKey> tailPickSquares()
    {
        return java.util.Collections.unmodifiableSet(tailPicks.keySet());
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
     * How many things must be fixed, in words that agree with the number ("1 things" read wrong).
     *
     * @param errors how many
     * @return the line under the findings
     */
    public static String blockingCountText(int errors)
    {
        return errors == 1 ? I18n.t("autosetup.ui.labelBlockingCountOne") : I18n.f("autosetup.ui.labelBlockingCount", errors);
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
     * Radio rather than a checkbox because the answers cannot overlap: a square cannot be somewhere trains stop AND
     * somewhere they may not go.
     */
    private javax.swing.JMenuItem radio(javax.swing.ButtonGroup group, String text, String tooltipKey,
        boolean on, final Runnable action)
    {
        javax.swing.JRadioButtonMenuItem menuItem = new javax.swing.JRadioButtonMenuItem(text, on);

        if (tooltipKey != null) menuItem.setToolTipText(wrapped(I18n.t(tooltipKey)));

        group.add(menuItem);

        menuItem.addActionListener(e ->
        {
            // NOT WHILE TRAINS RUN - see `refusedWhileRunning`
            if (refusedWhileRunning()) return;

            action.run();

            // placementChanged, not refresh (TD-1).  These radios write the SETUP, and the captions and the running
            // railway are built from the RUNNING layout; refresh() redraws this panel and tells the other surface nothing,
            // so a change made from the track diagram's menu would be written and never appear.  The redraw belongs HERE,
            // where every radio gets it, not in whichever lambda somebody reported (OB-039 was the facing radio).
            placementChanged(null);

            flashMenuTarget();
        });

        return menuItem;
    }

    /**
     * Applies the turning answer, keeping the two stored flags in step.
     *
     * Both are cleared and only what is meant is written, rather than leaving "may" set underneath "must": two flags
     * describing one choice disagree the first time only one of them is updated.
     */
    private void setTurning(TileKey tile, boolean may, boolean must)
    {
        session.setPointFlag(tile, AutonomyBuilder.CAN_REVERSE, may && !must);
        session.setPointProperty(tile, AutonomyBuilder.MUST_REVERSE, must ? Boolean.TRUE : null);

        // Persisted, and the running layout told (MT-246).
        setupChanged();
    }

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

        // Arrival restrictions are one-way edges in the running graph (VD10-B2).
        setupChanged();
    }

    /**
     * Applies one of the three answers.
     *
     * @param station whether trains may stop here
     * @param open whether they may come here at all
     */
    private void setUsage(TileKey tile, boolean station, boolean open)
    {
        // A SENSOR WITH NO NAME IS NAMED AS IT BECOMES A STATION (FR-120): a box asks for its name, with a duplication
        // check.  Cancelled, or left blank, nothing changes; a square with a name, given here or before, is not asked
        // again.  The station menu is the one door that makes a square a station, in the editor and on the track
        // diagram's own menu.
        if (station && !session.getStore().isStation(tile) && !hasAName(tile))
        {
            String name = askTheNewStationsName(tile);

            if (name == null) return;

            // written here, with the rest of the answer, and announced below with it (MT-246)
            session.setPointName(tile, name);
        }

        setStation(tile, station);

        // Stored only when it is off, like every other default, so a square nobody has closed carries
        // nothing at all
        session.setPointProperty(tile, "active", open ? null : Boolean.FALSE);

        // Persisted, and the running layout told (MT-246).
        setupChanged();
    }

    /**
     * Asks for a new station's name (FR-120): the message and the name as typed so far, to the answer - null for Cancel.
     * A dialog over the editor; a claim answers it instead.
     */
    private java.util.function.BiFunction<String, String, String> askForAName =
        (message, typed) -> JOptionPane.showInputDialog(owner(), wrapped(message), typed);

    /**
     * @param tile a square
     * @return whether the setup has a name for it
     */
    private boolean hasAName(TileKey tile)
    {
        String name = session.getStore().getPointName(tile);

        return name != null && !name.trim().isEmpty();
    }

    /**
     * Asks a sensor becoming a station for its name (FR-120): again, saying why, for a name that cannot be a square's
     * (`AutonomySession.whyNotAPointName`) or that another square has, in any case.  Writes nothing.
     *
     * @param tile the sensor
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

            // no quotes in a name, as `promptName` takes them out
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

        // A new station gets its name on the diagram straight away: a station nobody can see is the commonest thing wrong
        // with a finished setup, and the moment somebody says "this is a station" is the moment they know where its name
        // should go.
        //
        // A caption only where the station has none (OB-310, the MT-116 rule).  Every answer under Station comes through
        // here, and `placeCaption` MOVES a caption the station already has, so each change would send the name to the
        // next free square beside the platform and the next change back (the label rotated between vertical and
        // horizontal).
        if (on && !session.getLabelledStationTiles().contains(tile)) placeLabelFor(tile);

        // A SENSOR DEMOTED TO A PLAIN POINT KEEPS ITS OLD "terminus" FLAG (GSE-C4), with the rest of what it had as a
        // station, ignored while it is not one: `AutonomySession.isTurnAround` and the checks read it of a station only,
        // so on a plain point it turns nothing round, and it is there again when the square is promoted.  Active is not
        // cleared either: it applies to any point, so clearing it here would silently re-enable a point somebody had
        // switched off.  What a square is and whether it is open are one three-way choice, and setUsage sets both
        // together.

        // Persisted, and the running layout told (MT-246).
        setupChanged();
    }

    private void setAllBranches(TileKey tile, Direction direction)
    {
        session.setDirection(new LinkedHashSet<>(java.util.Arrays.asList(tile)), direction);

        // THE ARROWS, NOT THE DIAGRAM (MT-334): a restriction changes no tile art, only the arrow drawn over the tile, and
        // rebuilding every label on the page to show it flashes (measured, 384 of 384).  The running layout is still
        // rebuilt: annotationsChanged does that half exactly as setupChanged does.  Directions are edges in the running
        // graph (VD10-B2).
        annotationsChanged();
    }

    /**
     * Why this number cannot be stored under this key, or null when it can (SET-B1).
     *
     * **Only the maximum train length has a floor, and it is 0.**  `promptNumber` is shared with `priority`, where a
     * negative is meaningful, so the refusal cannot live in the parse.  A negative maximum is refused a layer down by
     * `Layout.fromJSON`, which does not clamp it as `Point.setMaxTrainLength` does but invalidates the whole
     * configuration, taking the railway out of autonomy with only a log line to say so.
     *
     * Public and static so the rule can be asked of it directly; `promptNumber` is modal and no test can answer it.
     * `core.testMassAssignLengths.testANegativeMaximumTrainLengthIsRefused` is that test, and it also asks what the
     * clear makes of a negative already stored.
     *
     * @param key the point property being written
     * @param value the number typed
     * @return the sentence to show, already translated, or null when the number is fine
     */
    public static String whyNotThisNumber(String key, int value)
    {
        if ("maxTrainLength".equals(key) && value < 0) return I18n.t("autosetup.ui.errorMaxTrainLengthNegative");

        return null;
    }

    /**
     * Asks for a number, storing nothing when it is left at the value that means "no setting".
     */
    private void promptNumber(TileKey tile, String key, String promptKey, int unset)
    {
        Object current = session.getPointProperty(tile, key);

        // I18n.f, not t: two of these prompts name the point they are about, and I18n.t does no substitution.
        String entered = JOptionPane.showInputDialog(owner(),
            I18n.f(promptKey, describeTile(tile)),
            current instanceof Number ? String.valueOf(current) : String.valueOf(unset));

        if (entered == null) return;

        try
        {
            int value = Integer.parseInt(entered.trim());

            // REFUSED BEFORE IT IS WRITTEN, rather than by the layer that loads it (SET-B1).  Nothing is stored,
            // which is what the NumberFormatException arm below does and for the same reason.
            String refused = whyNotThisNumber(key, value);

            if (refused != null)
            {
                JOptionPane.showMessageDialog(owner(), wrapped(refused));

                return;
            }

            session.setPointProperty(tile, key, value == unset ? null : value);
        }
        catch (NumberFormatException e)
        {
            // About numbers, not about lengths: this prompt is also used for a station's priority, where negatives are
            // valid.
            JOptionPane.showMessageDialog(owner(), I18n.f("autosetup.ui.errorNotANumber", entered));
            // NOTHING WAS WRITTEN, so there is nothing to announce (VD10-C14): the call belongs after the last statement
            // of the success path, and after the failure path it would save the setup and rebuild the railway having
            // changed nothing.
            return;
        }

        // Persisted, and the running layout told (MT-246).
        setupChanged();
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
            // NOTHING WAS WRITTEN, so there is nothing to announce (VD10-C14): the call belongs after the last statement
            // of the success path, and after the failure path it would save the setup and rebuild the railway having
            // changed nothing.
            return;
        }

        // Persisted, and the running layout told (MT-246).
        setupChanged();
    }

    /**
     * Which locomotive calls this point home, or null.
     *
     * ONE locomotive: Point.homeLoc is a single String and the model allows a locomotive only one home.  A multi-select
     * wrote a JSON array that parseAuto read with optString into the literal text ["BR 111"], matching no locomotive.
     */
    private String homeOf(TileKey tile)
    {
        Object stored = session.getPointProperty(tile, "home");

        return stored instanceof String && !((String) stored).trim().isEmpty()
            ? (String) stored : null;
    }

    /**
     * One of the picker's shortcut buttons: choose this name and close the dialog.
     *
     * Written once because there are two of them, and two copies of a four-line listener would come to behave
     * differently.
     *
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

    /**
     * How far apart the picker's shortcut buttons stand (OB-188): two buttons drawn edge to edge read as one wide
     * control with a line down the middle, and these two answer different questions.
     */
    private static final int SHORTCUT_GAP = 8;

    /**
     * The strip the picker's shortcut buttons sit on.
     *
     * A method rather than a line inside `pickLocomotive` so the spacing can be measured without opening a modal
     * dialog.  Public because the test that measures the spacing lives outside this package.
     *
     * @return an empty row, laid out left to right with a gap between whatever is added to it
     */
    public static javax.swing.JPanel shortcutRow()
    {
        return new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, SHORTCUT_GAP, 0));
    }

    /**
     * Asks for one locomotive from a list, with a box to narrow it down.
     *
     * FR-010 and FR-011.  Both places that ask this question used a combo box, which is useless for sixty
     * locomotives: the only way to a name is to scroll to it.  A line of text that filters the list is the whole
     * feature.
     *
     * ONE component for both (FR-011): the two questions differ only in whether "the locomotive I am driving" is a
     * sensible answer.  For a HOME it is; for adding to autonomy the current locomotive is usually already in it, so the
     * button is left out rather than offered and refused.
     *
     * Hand-written rather than a form, and a dialog rather than a window: this is a question, and new UI is built as
     * panels inside what already exists.
     *
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

        // TWO SHORTCUTS, and they are different questions (Adam, 2026-09-07): *"'use current' should be the train
        // currently parked at the station.  Rename the current behavior to 'use active', and add a second button for 'use
        // current' if a train is parked there."*  Active is the locomotive in your hand; current is the one on the
        // platform in front of you, and assigning that one its own home is the more common gesture.  Each is offered only
        // when its locomotive is actually in the list, so neither button can pick something the list would refuse.
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

        // Only when there is something in it: an empty strip still takes height, and this dialog is
        // small enough for that to read as a gap somebody forgot to fill.
        if (row.getComponentCount() > 0) panel.add(row, java.awt.BorderLayout.SOUTH);

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
     * A checklist of the other stations, rather than a picker of one: a station may be held back by more than one place.
     *
     * Only NAMED points are offered: the restriction is written into the built configuration as lock edges pointing at
     * the watched square, and a square with no name is one the operator cannot recognise in a list.
     */
    private void promptBlockingPoints(TileKey station)
    {
        // Read FIRST, because what is already stored decides what has to be offered (FSR-C5).
        java.util.List<TileKey> already = session.getStore().getBlockingPoints(station);

        java.util.List<TileKey> choices = new java.util.ArrayList<>();

        for (TileKey tile : session.getStore().getNamedTiles())
        {
            // STATIONS ONLY (MT-376: *"works, but exclude non-station points from the list entirely."*).  A name is not
            // a station (a caption square, an approach guard, or a plain piece of track somebody labelled all carry one),
            // and what this setting watches is whether a place trains stop is OCCUPIED.
            //
            // ...unless it is ALREADY STORED (FSR-C5): this dialog is the only way to edit `blockedPoints`, so a restriction
            // it does not show is one nobody can take off, and `chosen` is built from what the list offers, so hiding a
            // stored entry would delete it the moment somebody pressed OK (FBR-A2).
            if (!session.getStore().isStation(tile) && !already.contains(tile)) continue;

            // Not the station itself: standing there already decides whether it is free, so watching
            // itself would make it a station nothing can be sent to.
            if (tile.equals(station)) continue;

            // And not a square that IS this station by another name (OB-083): a caption sits on a square of its own and
            // points AT the station, and it carries a name, so it would appear in this list as though it were somewhere
            // else; choosing it would hold the station back with itself.
            //
            // THIS station's captions only.  Widening to every caption square, and to squares absent from the graph, was
            // tried and withdrawn (FBR-C4): a caption square is often real track (`mayCarryACaption` allows one on a
            // feedback square, and importLegacy captions every imported station WITH ITSELF, FBR-B3), and `chosen` is built
            // only from what the list offers while setBlockingPoints REPLACES the stored list, so every restriction a
            // filter hid was deleted when somebody pressed OK (FBR-A2).
            //
            // ... unless it is ALREADY stored, in which case it is offered whatever the rules say (FSR-C5, which subsumes
            // FBR-A2).  The filters are about what may be CHOSEN; hiding an entry chosen under an earlier rule, or by an
            // import, makes it permanent, because this dialog is the only way to edit blockedPoints.  It appears ticked,
            // it can be unticked, and the filters still decide what may be added.
            if (station.equals(session.getCaptionTarget(tile)) && !already.contains(tile)) continue;

            choices.add(tile);
        }

        // And any stored entry the loop above never reached at all (a square that has since lost its name, or is the
        // station itself from before that was refused): visible, and therefore removable.
        for (TileKey held : already)
        {
            if (held != null && !choices.contains(held)) choices.add(held);
        }

        // ALPHABETICAL, BY WHAT IS ACTUALLY SHOWN (FR-074: *"sort unavailable while occupied list alphabetically."*), so
        // the ticked ones do not collect at the bottom.  Sorted on `describeTile`, the text on the check box: an entry
        // that has lost its name renders as its sensor or its coordinates, and sorting by a name it does not display
        // would put it somewhere the reader cannot predict.
        java.util.Collections.sort(choices, new java.util.Comparator<TileKey>()
        {
            @Override
            public int compare(TileKey a, TileKey b)
            {
                return String.CASE_INSENSITIVE_ORDER.compare(describeTile(a), describeTile(b));
            }
        });

        // Nothing to offer, which now also means nothing is stored - the loop above puts every stored
        // entry on the list. So returning here cannot hide anything.
        if (choices.isEmpty())
        {
            // GREYED, because there is nothing a click could find (MT-376: *"if there are no stations on the diagram yet,
            // the 'choose on diagram' button should be greyed out to match the message"*).  The list is stations only and
            // `whyNotABlocker` refuses a square that is not one, so with no other station an enabled button would offer a
            // gesture that can only be refused.  Shown greyed rather than left out, so the operator learns the way in
            // exists for when there are stations to pick.
            javax.swing.JButton pickNow = nothingToPickButton();

            final javax.swing.JButton close = new javax.swing.JButton(
                javax.swing.UIManager.getString("OptionPane.cancelButtonText"));

            final JOptionPane pane = new JOptionPane(
                wrapped(I18n.t("autosetup.ui.infoNoOtherPointsToBlockWith")),
                JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] { pickNow, close }, close);

            close.addActionListener(e -> pane.setValue(close));

            javax.swing.JDialog dialog =
                pane.createDialog(owner(), I18n.t("autosetup.ui.menuBlockedByPointsTitle"));

            dialog.setVisible(true);
            dialog.dispose();

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

        // WHICH OF THESE AUTONOMY WOULD NEVER CHOOSE (FR-074: *"lightly grey out stations that can't be chosen in full
        // autonomy."*).  Read once, outside the loop, because the rule walks the reduction.
        // `stationsAutonomyWillNotChoose` is the runtime's own rule, the same one the diagram's captions and the "no
        // available paths" window ask, and covers a square marked manual-only and one switched out of service.
        java.util.Set<TileKey> notChosen = session.stationsAutonomyWillNotChoose();

        for (TileKey tile : choices)
        {
            // describeTile, not getPointName: the carried entries above include "a square that has since lost its name",
            // for which getPointName returns null and the check box would render with NO TEXT AT ALL (RA-C2).
            // describeTile falls back to the s88 where the square is a sensor, its coordinates otherwise; the caption rule
            // in testEditorSurfaceRules pins it here for the same reason it pins it there.
            javax.swing.JCheckBox box = new javax.swing.JCheckBox(
                describeTile(tile), already.contains(tile));

            box.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            // LIGHTLY (FR-074), so a foreground colour rather than a disabled box: these squares are valid to block with (a
            // parking berth holding a platform back is the case FR-001 exists for), so the mark says what KIND of square it
            // is, not that it cannot be picked.
            if (notChosen.contains(tile))
            {
                box.setForeground(NOT_CHOSEN_BY_AUTONOMY);

                box.setToolTipText(I18n.t("autosetup.ui.tooltipBlockerNotAutoDestination"));
            }

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

        // A wheel notch moves a row and a bit rather than one pixel.
        scroll.getVerticalScrollBar().setUnitIncrement(BLOCKED_ROW_HEIGHT);
        scroll.getVerticalScrollBar().setBlockIncrement(BLOCKED_ROW_HEIGHT * 5);

        // 460 wide: the prompt names a station, and at 320 the name wrapped into three lines.
        scroll.setPreferredSize(new java.awt.Dimension(460,
            Math.min(360, 80 + choices.size() * BLOCKED_ROW_HEIGHT)));

        // Three answers, not two (FR-025: *"the 'unavailable when occupied' dialog should allow users to select a s88 tile
        // by clicking, similar to the signal guard popup."*).  A checklist is the faster way to answer "which of these"
        // and stays the default button; picking on the diagram is faster for "that one, there", when the operator does not
        // know what the square is called.  OK and Cancel take their words from the look and feel, so they read the same
        // as every other dialog.
        final String ok = javax.swing.UIManager.getString("OptionPane.okButtonText");
        final String cancel = javax.swing.UIManager.getString("OptionPane.cancelButtonText");
        final String pick = I18n.t("autosetup.ui.optionPickBlockerOnDiagram");

        Object[] answers = { ok, pick, cancel };

        int chose = JOptionPane.showOptionDialog(owner(), scroll,
            I18n.t("autosetup.ui.menuBlockedByPointsTitle"),
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, answers, ok);

        // Anything that is not one of the two doing answers - Cancel, Escape, the close box - changes
        // nothing, which is what Cancel has always meant here.
        if (chose != 0 && chose != 1) return;

        java.util.List<TileKey> chosen = new java.util.ArrayList<>();

        // Every stored entry is on the list above, so the boxes are the whole answer (FSR-C5).
        for (int at = 0; at < boxes.size(); at++)
        {
            if (boxes.get(at).isSelected()) chosen.add(choices.get(at));
        }

        session.getStore().setBlockingPoints(station, chosen);

        // The restriction is built into the configuration as lock edges, so the running layout has to
        // be regenerated for it to mean anything - the same seam every other setup edit uses.
        placementChanged(null);

        // Pick on the diagram: the ticks above are applied FIRST and then the next click is waited
        // for.  Arming without applying would throw away whatever had just been ticked, which is a
        // button that quietly undoes the work the operator did in the same dialog.
        if (chose == 1) armBlockerPick(station);
    }

    /**
     * The Pick on the Diagram button as the "nothing to list" message shows it: present and greyed
     * (MT-376).  Its own method so a test can read what the operator is shown without a modal dialog.
     *
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
     * `whyNotABlocker`, for a test - the rule a click on the diagram is judged by (MT-376).
     *
     * @param station the station being held back
     * @param tile the square that would be clicked
     * @return the refusal, or null when the square would be accepted
     */
    public String whyNotABlockerForTest(TileKey station, TileKey tile)
    {
        return whyNotABlocker(station, tile);
    }

    /**
     * Hands the next click on the diagram to this station's blocking list (FR-025).
     *
     * One method, because there are two doors into the same gesture (the checklist's button, and the "nothing is named
     * yet" message), and a gesture armed in two places gets cancelled properly in only one of them.
     *
     * @param station the station being held back
     */
    private void armBlockerPick(TileKey station)
    {
        // In the deep menu the diagram is not this panel's to be clicked on; needsTheGrid jumps to the page instead.
        if (needsTheGrid(station)) return;

        blockingFor = station;

        waitFor(I18n.f("autosetup.ui.promptClickBlocker", describeTile(station)));

        refresh();
    }

    /**
     * Why a clicked square cannot hold this station back, or null if it can (FR-025).
     *
     * Deliberately SHORTER than the checklist's filter: the list offers named points only because a square with no name
     * cannot be recognised in a list, while a square that has just been clicked needs no name (the click IS the
     * identification) and comes back on the checklist labelled by describeTile.
     *
     * What survives from the filter is what is about the RAILWAY: a square autonomy does not route over cannot hold
     * anything back, and a station cannot hold itself back (OB-083) whether it is picked by its own name or by a caption
     * that points at it.
     *
     * A blocker is resolved by TILE (AutonomyBuilder.nodesFor takes the key, not the name), so an unnamed square is not
     * silently dropped from the built configuration.
     *
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

        // A STATION, as the list offers (MT-376): the click and the list are two doors onto one setting and must refuse
        // the same squares, or the greyed button beside "nothing to choose" would be a lie the first time somebody
        // clicked a plain sensor.  An entry already stored is accepted, for the list's own reason: it is how an old
        // restriction is found again.
        if (!session.getStore().isStation(tile)
            && !session.getStore().getBlockingPoints(station).contains(tile))
        {
            return I18n.f("autosetup.ui.errorBlockerNotAStation", describeTile(tile));
        }

        return null;
    }

    /**
     * Sets a square's home locomotive, from somewhere other than its own right-click menu (R28-C5).
     *
     * Adam: *"set control+H for home."*  One line rather than making the prompt itself public, so the panel keeps
     * deciding what a home means and this only decides who may ask, the same door `promptNameFor` opened for Control+S.
     *
     * @param tile the square to set a home on, ignored when null
     */
    public void promptHomeFor(TileKey tile)
    {
        if (tile == null || session == null || session.getGraph() == null) return;

        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`
        if (heldByARun()) return;

        // A GESTURE IN PROGRESS IS PUT DOWN FIRST, as the right-click menu does (MFW-C1).
        cancelPendingGesture();

        // AND ONLY WHERE THE MENU OFFERS A HOME (MFW-B1).  The key asked nothing, so on plain track, a switch or a page
        // left out of autonomy it wrote a home onto a square that is not a station - and took the locomotive's home
        // off the platform it had.  `offersAHome` is the menu's own question.
        if (!offersAHome(tile))
        {
            // ITS OWN REASON (MFX-C1): the sentence it borrowed was Control+B's, about a maximum train length.
            say(hint, isIgnored(tile) ? I18n.t("autosetup.ui.infoTileIgnored")
                : I18n.t("autosetup.ui.infoNotAStationForAHome"));

            return;
        }

        promptHome(leaderOf(tile));
    }

    /**
     * Whether the right-click menu offers to set a home on this square (MFW-B1).
     *
     * The menu's questions, in its order: a text or blank square gets `buildTextMenu`, which offers no home; an
     * ignored square gets only the bulk tools; and otherwise the menu acts on the run's leader and offers the home item
     * only where that is a Point the reducer knows and a station.  Public so a test can put the key and the menu side by
     * side - `regression.testControlNAsksTheMenusQuestion` - because Control+H itself opens a modal dialog.
     *
     * @param tile the square
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

    private void promptHome(TileKey tile)
    {
        String current = homeOf(tile);

        List<String> names = homeChoices(I18n.t("autosetup.ui.labelNone"), placedLocomotives(), current);

        if (names.size() == 1)
        {
            JOptionPane.showMessageDialog(owner(), I18n.t("error.noLocs"));
            return;
        }

        // FR-010: filterable, and offering the locomotive being driven.  "Use current" is the common gesture this dialog
        // exists for, and it is only offered when that locomotive is one of the choices, so the button can never pick
        // something the list refuses.
        String driving = parentWindow() == null || parentWindow().getActiveLoc() == null
            ? null : parentWindow().getActiveLoc().getName();

        // AND THE ONE STANDING HERE, the more common gesture of the two (2026-09-07).  Read from the SETUP rather than the
        // running layout: there may be no run at all, and what the setup says is parked here is what the diagram draws.
        String parked = locomotiveAt(tile);

        String chosen = pickLocomotive(owner(), I18n.t("autosetup.ui.menuHomeNone"),
            I18n.t("autosetup.ui.promptHomeFor"), names,
            current == null ? names.get(0) : current, driving, parked);

        if (chosen == null) return;

        String picked = names.get(0).equals(chosen) ? null : chosen;

        // And a home this locomotive cannot actually rest at is worth saying out loud (OB-022): warned, not refused, since
        // the same state is reachable by editing the station afterwards and setting homes before finishing the track is
        // not a mistake.  Without it every future Return Home reports IMPOSSIBLE and advises checking the rails, when
        // nothing about the rails is at fault.
        if (picked != null && !mayRestHere(tile, picked) && !confirmedAnyway(picked, tile)) return;

        // And a locomotive already at home somewhere else (TD-8), the fourth of the rules the running layout has: warned
        // rather than refused, like the two above.  One locomotive has one station, so setting this one MOVES the other,
        // which is very often what was meant; what must not happen is moving it silently.
        TileKey already = picked == null ? null : session.homeElsewhere(tile, picked);

        if (already != null && JOptionPane.showOptionDialog(owner(),
            I18n.f("autolayout.ui.confirmHomeIsAlreadySet", picked, describeTile(already)),
            I18n.t("autolayout.ui.dialogSetHomeLocomotive"),
            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[0]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        // WHICH WAY IT SHOULD FACE THERE, where no train stands there facing a way it may be brought home in (OB-282:
        // *"prompt the user for the direction"*).  Only the facings a train may be brought home in are offered, and a
        // dismissed question sets no home - the rule every question this door asks already follows.
        org.traincontrol.automationui.TilePorts.Side facing = null;

        // ASKED EXACTLY WHEN THE SESSION HAS NO ANSWER (TDY2-C4).  The answer comes from the railway first, so a train the
        // setup had elsewhere but that stands here is not asked about, and one the setup still had here after it drove
        // off is not given the setup's stale facing.
        if (picked != null && session.knownHomeFacing(tile, picked) == null)
        {
            java.util.Set<org.traincontrol.automationui.TilePorts.Side> canHold = session.homeFacingsFor(tile);

            if (FacingPrompt.wouldAsk(canHold))
            {
                facing = FacingPrompt.forHome(canHold, picked, describeTile(tile), owner());

                if (facing == null) return;
            }
        }

        // Through setHome, which does the sweep: setPointProperty writes the one square and nothing else, which is how
        // two squares came to share a home.
        session.setHome(tile, picked, facing);

        refresh();

        // Persisted, and the running layout told (MT-246).
        setupChanged();
    }

    /**
     * Whether this locomotive could actually come to rest at this square.
     *
     * Asked of the RUNNING layout, because that is what HomeStaging reasons about and what Return Home uses.
     * Unanswerable before a configuration is loaded, and an unanswerable question is not a warning, so it says yes.
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

        // Only the "cannot come to rest" half, which is what this method is named for and what its caller warns about
        // (LD-9).  `whyNotAHome` has no other reason to give now (a home is the logical point, and the direction is
        // wherever the locomotive was facing when it started moving), so this filter is the identity; it is kept because
        // the filter is the statement: this method warns about resting and must not start warning about whatever reason
        // is added next.
        return !"autolayout.errorHomeCannotRestHere".equals(
            org.traincontrol.automation.HomeStaging.whyNotAHome(loc, point));
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
     */
    private List<String> allLocomotives()
    {
        return locomotiveNames == null
            ? java.util.Collections.<String>emptyList() : locomotiveNames.get();
    }

    /**
     * Only the locomotives placed somewhere on this layout: a home is where a particular engine belongs, so offering
     * the whole roster invites choosing one that autonomy has never heard of, and the setting would be stored and
     * quietly do nothing.
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
     * The locomotive's own settings - its functions, its speed, its length - on this square's menu.
     *
     * The same dialog the track diagram's menu opens, and the only place in the application where a locomotive's
     * ARRIVAL and DEPARTURE functions can be set: the horn on leaving, the lights on arriving.  Autonomy applies them
     * from Layout.applyDefaultLocCallbacks every time a configuration loads.
     *
     * It needs the RUNNING layout, because those settings belong to a Point rather than to a square: the dialog reads
     * and writes a Locomotive through it.  So the item appears only when there is one and it knows this square.
     *
     * The placement is written back into the SETUP afterwards: the dialog moves the locomotive in the layout, and a
     * train moved in the layout and not in the setup goes back where it was the next time the configuration is built
     * (see LayoutRightclickAutonomyMenu.placeFacing).
     */
    private void addLocomotiveSettings(javax.swing.JPopupMenu menu, TileKey target)
    {
        org.traincontrol.automation.Layout layout =
            layoutSource == null ? null : layoutSource.get();

        if (layout == null || layout.getLocomotivesToRun().isEmpty()) return;

        final org.traincontrol.automation.Point point = pointOnTheLayout(layout, target);

        if (point == null) return;

        // Offered whether or not a train is standing here (MT-101, MT-022).  The gate would ask the RUNNING layout what
        // is standing here, while the editor writes to the SETUP: a train just placed from this very menu is in the setup
        // and not yet on the running layout, so the item disappeared at precisely the moment somebody wanted to set its
        // arrival function (Adam: *"Critical: I no longer see the option to edit the locomotive."*).  menuLabelFor
        // already says the right thing either way ("Place Locomotive At..." or "Edit Locomotive At..."), so nothing has
        // to be decided here.

        menu.add(item(GraphLocAssign.menuLabelFor(point), () ->
        {
            // THE SETUP GOES IN WITH IT, because the dialog offers the arrival side and has to work out this square's
            // sides (REV9-B2).  This panel edits the session it was constructed with, which is not always the main
            // window's, so it is handed over rather than fetched.
            GraphLocAssign edit = new GraphLocAssign(parentWindow(), point, session,
                runningLayout == null ? null : runningLayout.get());

            // `asDialogContent`, not the panel: the arrival side sits under the form, and the dialog builds that wrapper
            // once for both doors.
            int answer = JOptionPane.showOptionDialog(owner(), edit.asDialogContent(),
                I18n.f("autolayout.ui.dialogEditOrAssignLocomotive", describeTile(target)),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                TrainControlUI.OK_CANCEL_OPTS, TrainControlUI.OK_CANCEL_OPTS[0]);

            if (answer != JOptionPane.OK_OPTION) return;

            // ONE DOOR FOR BOTH MENUS (REG8-B2; Adam: *"there needs to be parity across the board"*).  The rule (read the
            // arriving train's heading, commit, place, record the facing) lives in GraphLocAssign.commitAndRecord, which
            // both menus call: the track diagram's copy once lacked the second half, and an assignment made there
            // reverted on the next configuration load.
            GraphLocAssign.commitAndRecord(edit);
            // AND THE RAILWAY IS TOLD (VD11-A1): this door moves a train, the one thing the running layout most needs to
            // hear about, as every other placement door does through `placementChanged`.
            setupChanged();
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
     * Walking the window ancestry cannot arrive from inside the layout editor: the editor is a JFrame, a JFrame has no
     * owner, and the chain ends at it.  So every menu item that needed the main window (Place Locomotive, Edit
     * Locomotive) would get null and throw a NullPointerException, which item()'s handler shows as "null".
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

    /**
     * The Is Active tick (FR-117): the train and the setup together, through the window's door where there is a window
     * and a train of that name, and the setup alone where there is not - then the railway is told, as every setup door
     * tells it.
     *
     * @param name the train standing on the square
     * @param active whether autonomy may run it
     */
    private void setActive(String name, boolean active)
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

    /**
     * Which locomotive is standing on a point, or null.
     */
    private String locomotiveAt(TileKey tile)
    {
        // Asked of the session, as the label does: two copies of this answer written down separately are what let a
        // wrong third be written.
        return session == null ? null : session.getLocomotiveNameAt(tile);
    }

    /**
     * Puts a locomotive on a square, asked for by name.
     *
     * One question (OB-009): bringing a train INTO autonomy and moving one already in it are the same gesture, and the
     * edit dialog can do both and more.  The list is the whole roster, with the locomotives autonomy already runs
     * marked as such and sorted to the top; choosing one of those moves it.
     *
     * One at a time, and only where it is not already: a locomotive standing in two places at once is a state the
     * running layout cannot represent, so it is removed from wherever it was first.
     *
     * @param tile the square, which is a station or this item is not offered
     */
    private void placeLocomotive(TileKey tile)
    {
        List<String> placed = placedLocomotives();

        String here = locomotiveAt(tile);

        List<String> names = new java.util.ArrayList<>();

        // Already in autonomy first, and said so: a move is the commoner gesture once a railway is set up, and a name
        // on its own does not say whether choosing it takes a train off somewhere else.
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

        // FR-011: the same picker, filterable, without the "use current" button.  This list is the locomotives autonomy
        // does NOT already have, so the one being driven is usually not in it, and a button absent most of the time it is
        // looked for is worse than none.
        String chosen = pickLocomotive(owner(), I18n.t("autosetup.ui.menuAddToAutonomy"),
            I18n.t("autosetup.ui.promptAddToAutonomy"), names, names.get(0), null, null);

        if (chosen == null) return;

        String name = unmark(chosen, placed);

        if (name == null) return;

        // Taking it off wherever it was is the SESSION's job, in placeLocomotive: it works over the configuration, which
        // is complete, while the reducer's Points omit excluded pages, so a train standing on one of those would not be
        // lifted and the build would emit it at two Points and invalidate the whole layout.
        //
        // THE HEADING THIS TRAIN ALREADY HAS, read before the placement moves it (CONF-B2): `placeLocomotive` does not
        // clear the facing on the non-null path, so without this the PREVIOUS occupant's direction would stay and attach
        // to the arriving train.
        org.traincontrol.automationui.TilePorts.Side heading = session.facingOf(name);

        session.placeLocomotive(tile, name);

        // OVER THE COPIES IT COULD LEAVE (OB-284), as the paste records it: a heading only a copy trains may not arrive at
        // holds is kept - the build stands the train there and autonomy says why it will not start it - and one no copy
        // can leave by is not.
        session.setFacing(tile, org.traincontrol.automationui.AutonomySession.facingAfterAPaste(
            session.departableFacingsFor(tile, layoutSource == null ? null : layoutSource.get()), heading, null));

        // THE DOOR MT-337 WAS REPORTED AT.  This writes `loc` into the setup and nowhere else, so the rebuild that
        // follows is the only thing that puts the train on the running layout, and without naming it here the record read
        // a moment ago puts it back where it was.
        placementChanged(java.util.Collections.singletonList(name));
    }

    /**
     * Redraws whatever is showing this placement - which is not the same surface in both modes.
     *
     * In the EDITOR the caption is tile art in this window's own grid, so the grid is rebuilt (see applyCaption: the
     * annotation refresh does not touch tile art; OB-009, MT-101).
     *
     * From the track diagram's DEEP MENU the caption belongs to the main diagram, which draws from the RUNNING layout
     * rather than from the setup; so without a rebuild the setup gains a locomotive and nothing on screen changes, while
     * the facing menu, which reads the setup, lists it (OB-035).
     *
     * Rebuilding the running layout is what makes them agree, and is the same seam OB-034 uses.  It is unconditional
     * (NR-7), because the two surfaces are on screen together.
     *
     * @param placementsEdited the locomotives whose placement this gesture wrote into the setup
     *                         and nowhere else, or null where it changed no placement (D2-A1)
     */
    private void placementChanged(java.util.Collection<String> placementsEdited)
    {
        // WHICH TRAINS THE SETUP HAS JUST BEEN GIVEN A NEW ANSWER ABOUT (D2-A1 / W7-A1).  The rebuild regenerates every
        // placement from the setup and then has to decide, per train, whether the setup's answer or the running
        // railway's is the newer; a setup placement that disagrees with the railway is either an edit somebody just made
        // or a record a run has outrun, and the data cannot tell them apart.  So provenance is carried rather than
        // guessed: only the doors that write a placement into the SETUP and nowhere else name a locomotive here
        // (`placeLocomotive`, the per-square Remove, and Clear Every Locomotive).  Everything else passes null, including
        // the doors that move a train on the running layout too (`GraphLocAssign.commitAndRecord` and the track
        // diagram's own menu), which already agree with the record the rebuild reads.
        //
        // A COLLECTION AND NOT AN OPTIONAL EXTRA, so the compiler visits every call site: a placement door added later
        // cannot quietly inherit whichever rule happens to be here.
        if (placementsEdited != null)
        {
            for (String name : placementsEdited)
            {
                if (name != null) placementsJustEdited.add(name);
            }
        }

        // BOTH, not one or the other: the editor's own grid and the running layout are on screen at the same time, and a
        // change made from the track diagram while Autonomy Setup is open must reach both (MT-125: changing which way a
        // locomotive faces did not refresh in the viewer).  Rebuilding the running layout is safe to ask for
        // unconditionally: it declines while autonomy is busy.
        setupChanged();

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

        // Tall enough to choose from without scrolling on an ordinary roster.
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

        // AND THE LIST'S MODE, above it (FR-121): keep the chosen locomotives out (the default, a blacklist), or let only
        // them in (a whitelist: every other one, a locomotive added later included, is kept out).  Adam: *"we don't want
        // to allow new locomotives that get added later when that mode is on."*
        boolean exclusions = "excludedLocs".equals(key);

        javax.swing.JRadioButton keepOut = new javax.swing.JRadioButton(I18n.t("autosetup.ui.optionExcludeChosenLocs"));
        javax.swing.JRadioButton letInOnly = new javax.swing.JRadioButton(I18n.t("autosetup.ui.optionAllowOnlyChosenLocs"));

        javax.swing.ButtonGroup modes = new javax.swing.ButtonGroup();

        modes.add(keepOut);
        modes.add(letInOnly);

        (allowsOnlyListed(tile) ? letInOnly : keepOut).setSelected(true);

        javax.swing.JComponent asked = new JScrollPane(list);

        if (exclusions)
        {
            javax.swing.JPanel choices = new javax.swing.JPanel(new java.awt.GridLayout(0, 1));

            choices.add(keepOut);
            choices.add(letInOnly);

            javax.swing.JPanel both = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));

            both.add(choices, java.awt.BorderLayout.NORTH);
            both.add(asked, java.awt.BorderLayout.CENTER);

            asked = both;
        }

        // The "home" arm of this is not reached: the only caller passes "excludedLocs".  Kept because the shape is right
        // for any list-valued property, but anything that starts using it for a HOME has to go through session.setHome,
        // which sweeps that locomotive's home off every other square (TD-8).
        if (JOptionPane.showConfirmDialog(owner(), asked, I18n.t(key.equals("home")
                ? "autosetup.ui.labelHomeFor" : "autosetup.ui.labelLocomotiveExclusions"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        List<String> picked = list.getSelectedValuesList();

        boolean allowOnly = exclusions && letInOnly.isSelected();

        // Excluding a locomotive from the station that is its HOME contradicts the home (OB-022): the rule is
        // HomeStaging.homeBrokenByExcluding.  Warned rather than refused, like the home warning: an operator may well
        // mean it, and the same state is reachable from the other side by assigning a home the station already
        // excludes.
        if ("excludedLocs".equals(key))
        {
            String broken = homeBrokenBy(tile, picked, allowOnly);

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

        if (exclusions)
        {
            session.setPointProperty(tile, org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE,
                allowOnly ? org.traincontrol.automation.Point.ALLOW_ONLY_LISTED : null);
        }

        refresh();

        // Persisted, and the running layout told (MT-246).
        setupChanged();
    }

    /**
     * The home assignment that excluding these locomotives from this square would contradict.
     *
     * Read off the SETUP rather than the running layout: that is where this editor's home assignments live, and the
     * running Point's getHomeLoc is only populated once a configuration has been built.
     *
     * @param tile the square
     * @param chosen the locomotives the list names
     * @param allowOnly whether it names the only ones allowed (FR-121) rather than the ones shut out
     * @return the home that would be contradicted, or null
     */
    private String homeBrokenBy(TileKey tile, List<String> chosen, boolean allowOnly)
    {
        return homeBrokenBy(homeOf(tile), chosen, allowOnly);
    }

    /**
     * The home this exclusion list would break, or null.
     *
     * Static and public so the rule can be tested without a window (MT-112).  Excluding a locomotive from the station
     * it is homed at is not forbidden, but it is a contradiction the user should be shown rather than left to discover
     * when a train has nowhere to go at the end of a run.
     *
     * @param home the locomotive homed at this station, or null
     * @param excluded the locomotives being shut out of it
     * @return the home that would be broken, or null
     */
    public static String homeBrokenBy(String home, List<String> excluded)
    {
        return homeBrokenBy(home, excluded, false);
    }

    /**
     * The home this list would break, read in its mode (FR-121): a home the list names where it keeps the named
     * locomotives out, a home it does NOT name where it lets only the named ones in.
     *
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

    /**
     * Whether a square's locomotive list names the only locomotives allowed there (FR-121), as the setup says.
     *
     * @param tile the square
     * @return true in that mode
     */
    private boolean allowsOnlyListed(TileKey tile)
    {
        return org.traincontrol.automation.Point.ALLOW_ONLY_LISTED.equals(
            session.getPointProperty(tile, org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE));
    }

    /**
     * What the "home for a locomotive" list offers.
     *
     * Static and public so the rule can be tested without a window (MT-112, from OB-022 / DD-A6).
     *
     * "None" first, then the locomotives autonomy runs, and then - if it is not already among them - the home this
     * station HAS: an assignment may name a locomotive autonomy no longer runs, and a non-editable combo cannot
     * preselect a value its model does not hold, so without it the dialog opened showing "None" and pressing OK cleared
     * the station's home without anyone asking.  It is second rather than last: it is the current answer, next to the
     * only other answer that is not a locomotive.
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

    /**
     * Names a square, from somewhere other than its own right-click menu (MT-257 item 4).
     *
     * The editor's Control+S needs this: `promptName` is private and the key handler lives in `LayoutEditor`, which owns
     * this panel.  One line rather than making the prompt itself public, so the panel keeps deciding what naming means
     * and this only decides who may ask.
     *
     * @param tile the square to name, ignored when null
     */
    public void promptNameFor(TileKey tile)
    {
        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`
        if (heldByARun()) return;

        // A GESTURE IN PROGRESS IS PUT DOWN FIRST, as the right-click menu and Control+N do (MFV-C4).
        cancelPendingGesture();

        if (canBeNamed(tile)) promptName(tile);
    }

    /**
     * Whether the right-click menu offers Show a Station Name Here on this square, live (FR-086, MFR-B3).
     *
     * The menu's own questions, in its order: `buildTileMenu` gives a text or blank square `buildTextMenu`, which
     * always carries the caption items; it gives an ignored square nothing but the bulk tools; and otherwise it adds the
     * caption items where `mayCarryACaption` allows and the panel is the editor's, greying the name item on a station
     * already showing its own name.
     *
     * Public so a test can put the key and the menu side by side - `regression.testControlNAsksTheMenusQuestion` -
     * because Control+N itself opens a modal dialog.  The key asked three of these and not `isIgnored`, so on a page
     * left out of autonomy it wrote a caption where the menu offers none.
     *
     * @param tile the square
     * @return whether the menu offers the item, enabled
     */
    public boolean offersAStationName(TileKey tile)
    {
        if (tile == null || session == null || session.getGraph() == null) return false;

        LayoutDiagramComponent onPage = componentAt(tile);

        // Only on a square the running diagram draws (AD-C6): a name put anywhere else would never be seen
        if (pageOf(tile) != null && (onPage == null || onPage.isText())) return session.isInTheDrawnArea(tile);

        if (isIgnored(tile)) return false;

        if (menuOnly || !mayCarryACaption(onPage) || !session.isInTheDrawnArea(tile)) return false;

        return !(session.getStore().isStation(tile) && tile.equals(session.getCaptionTarget(tile)));
    }

    /**
     * Shows a station's name on a square, from somewhere other than its own right-click menu (FR-086).
     *
     * Adam, on MT-397 (2026-09-15): *"let's add a hotkey for 'show station name here' too"* - Control+N, which he chose.
     * The same relationship Control+S has to Rename: the key handler lives in `LayoutEditor`, and this acts only where
     * `offersAStationName` - the menu's own question - says the menu offers the item, so the key and the menu cannot
     * disagree.  Like the menu, it abandons a gesture in progress first, and on an ignored square it says so.
     *
     * @param tile the square under the pointer, ignored when null
     */
    public void showStationNameFor(TileKey tile)
    {
        if (tile == null || menuOnly || session == null || session.getGraph() == null) return;

        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`
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
     * Sets the length of the square under the pointer, from outside this panel (FR-066).
     *
     * Adam: *"a keyboard shortcut for setting a square's length"*, and on which key, **"let's do E"** - Control+D
     * being taken twice over, by the editor's address toggle and the main window's locomotive adder.
     *
     * The same relationship Control+S has to Rename: `applyLength` is private and the key handler lives in
     * `LayoutEditor`, which owns this panel.  One line, so the panel keeps deciding what setting a length means and
     * this only decides who may ask.
     *
     * **It asks `offersALength`, which is the menu's own question**, and it writes where the menu writes.  `isIgnored`
     * is one of THREE things `buildTileMenu` asks before it reaches Set Length (the missing one sends a TEXT square to
     * `buildTextMenu`, which has no length item), and the menu acts on `leaderOf(tile)`, the square that speaks for a
     * run of plain track, so a key acting on the hovered square would measure a run through both doors and count it
     * twice.
     *
     * A key that acts where the menu offers nothing is the shape of MT-313, where Control+S named plain track.  And it
     * says a sentence rather than doing nothing quietly: a shortcut that appears to be broken is worse than one that
     * explains itself.
     *
     * @param tile the square to measure, ignored when null
     */
    public void promptLengthFor(TileKey tile)
    {
        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`
        if (heldByARun()) return;

        // A GESTURE IN PROGRESS IS PUT DOWN FIRST, as the right-click menu and Control+N do (MFV-C4).
        cancelPendingGesture();

        if (!offersALength(tile))
        {
            if (tile != null && session != null && session.getGraph() != null)
            {
                say(hint, isIgnored(tile) ? I18n.t("autosetup.ui.infoTileIgnored")
                    : I18n.t("autosetup.ui.infoNothingToMeasureHere"));
            }

            return;
        }

        applyLength(squareTheLengthWouldGoOn(tile));
    }

    /**
     * The square a length typed for this one actually lands on, or null where none would be asked for.
     *
     * **The menu's target, not the pointer's.**  A run of plain track has one tile that speaks for it and
     * `buildTileMenu` hands every item on the menu that tile, so a length set by right-clicking anywhere in a run lands
     * on the leader.  Writing to the hovered square instead would put the same number in two places, and
     * `GraphReducer.sumLength` adds every tile of an edge, so a run measured through both doors would be counted twice.
     *
     * Public because `promptLengthFor` opens a modal dialog and no test can read its answer, so a test compares the two
     * doors' targets: `regression.testControlEAsksTheMenusQuestion`.
     *
     * **It is the square, not necessarily the only square.**  `applyLength` writes to the whole SELECTION when there is
     * one, and to this square when there is not; both doors reach the same method, so the two agree.  What this names
     * is where a length lands with nothing selected.
     *
     * @param tile the square under the pointer
     * @return the square the length would be written to, or null when nothing would be asked
     */
    public TileKey squareTheLengthWouldGoOn(TileKey tile)
    {
        return offersALength(tile) ? leaderOf(tile) : null;
    }

    /**
     * Whether **Set Length...** is on this square's right-click menu, and so whether Control+E means anything here.
     *
     * **`buildTileMenu`'s three early returns, and the menu asks this rather than repeating them.**  The item is added
     * inside `if (offersALength(tile))` and bound to `squareTheLengthWouldGoOn`, so the guard and the affordance are one
     * expression (two copies agreed only by being typed identically).  The three the menu reaches its length item by:
     *
     *   1. a square this panel can answer about at all - a page the session knows, a session with a
     *      graph;
     *   2. NOT a text label.  A page's text squares get `buildTextMenu`, whose subject is what is
     *      written on the square rather than what runs across it, and which offers no length;
     *   3. NOT ignored - `isIgnored` - which is the excluded page, the untraversable tile type and the
     *      route button that carries whatever line it sits on.
     *
     * Public so that a test can put the two doors side by side and fail when they part company again;
     * `regression.testControlEAsksTheMenusQuestion` is that test.
     *
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
     * The Control+B door: how long a train may be to stop at the station under the pointer.
     *
     * Adam, 2026-09-12: *"map it to the station maximum train length, and add a tooltip."*  He picked **B** off the free
     * list, for the word this project uses for the platform the maximum belongs to: the berth (`behaviour.md`,
     * `Layout.measuredRoomAtTheEndOf`).
     *
     * **The same method the menu item calls, on the same square**, as Control+E and Control+S follow: two doors onto
     * one action cannot disagree about what it does or where it lands.  `offersAMaximumTrainLength` is the predicate
     * both use.
     *
     * The refusal says which of the two reasons it is, because "nothing happened" on a key press is indistinguishable
     * from a key that is not bound at all.
     *
     * @param tile the square under the pointer
     */
    public void promptMaxTrainLengthFor(TileKey tile)
    {
        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`
        if (heldByARun()) return;

        // A GESTURE IN PROGRESS IS PUT DOWN FIRST, as the right-click menu does (MFW-C1).
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
     * The square a maximum typed for this one actually lands on, or null where none would be asked for.
     *
     * **The menu's target, not the pointer's** - the same reason `squareTheLengthWouldGoOn` exists: a run of plain track
     * has one tile that speaks for it, and a key that wrote to the hovered square would put the number somewhere the
     * menu never shows it.
     *
     * Public because `promptMaxTrainLengthFor` opens a modal dialog and no test can read its answer, so a test compares
     * the two doors' targets: `regression.testControlBAsksTheMenusQuestion`.
     *
     * @param tile the square under the pointer
     * @return the square the maximum would be written to, or null when nothing would be asked
     */
    public TileKey squareTheMaximumWouldGoOn(TileKey tile)
    {
        return offersAMaximumTrainLength(tile) ? leaderOf(tile) : null;
    }

    /**
     * Whether **Maximum Train Length** is on this square's right-click menu, and so whether Control+B means anything
     * here.
     *
     * The menu's own question rather than a second opinion (MT-313: Control+S asked only whether the tile was null, so
     * it renamed plain track).  The three conditions the menu reaches the item by:
     *
     *   1. everything `offersALength` asks - a page the session knows, not a text label, not ignored -
     *      because those are `buildTileMenu`'s early returns and the station menu is behind them too;
     *   2. the reduction has a POINT here, which is the `isPoint` the station menu is nested in;
     *   3. the square is a station, which is the `isStation` the item itself is nested in.
     *
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
     * Whether this square is one that can be named.
     * **The right-click menu's own question**, so the key and the menu cannot disagree about it.  The menu offers Rename
     * only inside `if (isPoint)`; Control+S reached `promptNameFor`, which asked only whether the tile was null, so the
     * key named plain track that has nothing to hold a name (MT-313: *"it works on any tile in the autonomy editor, not
     * just sensors."*).
     *
     * @param tile the square
     * @return whether naming it means anything
     */
    public boolean canBeNamed(TileKey tile)
    {
        return session != null && session.canBeNamed(tile);
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

        // A name that ends like one of the builder's direction copies is refused, and said (TDR-C11).
        String refused = AutonomySession.whyNotAPointName(name);

        if (refused != null)
        {
            JOptionPane.showMessageDialog(owner(), wrapped(refused));

            return;
        }

        // Nothing can fail here: a caption points at the station's SQUARE, so renaming one is a change to the setup and
        // to nothing else.
        session.setPointName(tile, name.trim());

        // The moment a station gets a name is the moment it has one worth writing on the diagram: marking a square as a
        // station cannot do it on its own, since a new one has only the coordinate the reducer invented.
        //
        // Only where it has NO caption yet (MT-116: the label moved around to adjacent cells on rename).
        // `placeCaption` MOVES a station's caption rather than refusing when it already has one, which is right when
        // somebody asks for the name to be shown here and wrong as a side effect of renaming: the rename says nothing
        // about where the label should go, and the search picks whichever neighbouring square is free THIS time.  Nothing
        // has to be re-placed for the text to change, because a caption points at the station's SQUARE and looks its
        // name up.
        if (session.getStore().isStation(tile)
            && !session.getLabelledStationTiles().contains(tile))
        {
            placeLabelFor(tile);
        }

        // And the RUNNING layout is rebuilt, or the label goes blank (OB-034).  A rename rebuilds the setup's own graph,
        // so the station index maps this square to the NEW name, while the running layout still holds the old one;
        // everything that goes through those names then looks up a Point the running layout has never heard of, and the
        // caption finds nothing and draws nothing.  `autonomyEditorClosed` does this rebuild on closing, but a station can
        // be renamed from the diagram's own menu without opening an editor at all.  Through setupChanged, which also
        // persists it (MT-246).
        setupChanged();
    }

    private void promptLinkName(TileKey tile)
    {
        String current = session.getStore().getLinkName(tile);

        String name = JOptionPane.showInputDialog(owner(),
            I18n.t("autosetup.ui.promptLinkName"), current == null ? "" : current);

        if (name == null) return;

        session.setLinkName(tile, name);

        // The running layout carries link names too (VD10-B2).
        setupChanged();
    }

    /**
     * Pairs a link by choosing its partner from a list of every link on the layout.
     *
     * A list rather than a second click, because a link almost always leads to ANOTHER PAGE, which is not on screen, so
     * there is nothing there to click.
     */
    private void pairFromList(TileKey tile)
    {
        List<TileKey> candidates = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();

        // Like pairs with like: a link with a link, a tunnel with a tunnel.  The two are the same thing to autonomy (both
        // are portals, traversed the same way, carrying a stub route) and differ only in what they may point at.  Pairing
        // a link to a tunnel works as a jump but draws a train leaving through an arrow and arriving out of a tunnel
        // mouth, which is not what either symbol says.
        LayoutDiagramComponent from =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        if (from == null) return;

        for (Map.Entry<TileKey, LayoutDiagramComponent> entry
            : session.getGraph().getTiles().entrySet())
        {
            LayoutDiagramComponent component = entry.getValue();

            if (component == null || entry.getKey().equals(tile)) continue;

            if (component.getType() != from.getType()) continue;

            // A TUNNEL pairs only within its own page (MT-047).  A link is how a train leaves one page and arrives on
            // another; a tunnel is a piece of track that goes behind the scenery and comes out further along the SAME
            // diagram, and paired to another page it would draw a train entering a hillside on one page and emerging from
            // a hillside on another.
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

        // The diagram follows the dropdown, not the OK button: the names in this list are names of things on a diagram of
        // two hundred squares, and a coordinate pair is not something anybody holds in their head, so the moment to be
        // shown which square is meant is while the choice is being made.  Built by hand rather than through
        // showInputDialog, which gives no way at the combo box.
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

        // A portal pair is an edge in the running graph (VD11-A1).
        setupChanged();
    }

    /**
     * Whether a square is something a station can be told is its protecting signal.
     *
     * A SIGNAL with an address, and nothing else: isSignal() answers true for a lamp as well (the two share a drawing
     * path), and a lamp is decoration with nothing behind it to throw.  No address means no way to command it.
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
     * Two ways, because two quite different people ask this question: somebody looking at the diagram knows the signal
     * by where it is and can point at it; somebody who set the layout up knows it by its address.
     *
     * Both ways ADD to a list rather than replace one answer, because a platform reachable from two directions needs a
     * signal on each approach.  What is paired so far is on screen the whole time.
     *
     * @param station the station's square
     */
    private void pairProtectingSignal(TileKey station)
    {
        pairGuardSignals(Guard.EXIT, station);
    }

    /**
     * The pairing window, for either of a station's two signal lists (FR-096) - the protecting signals, or the
     * signals guarding the way in.
     *
     * @param guard which list
     * @param station the station's square
     */
    private void pairGuardSignals(Guard guard, TileKey station)
    {
        signalWindowOpen = true;

        try
        {
            askAboutSignals(guard, station);
        }
        finally
        {
            // Cleared HERE, in the finally, and not in the button handlers: the window has four ways out (Done, Escape,
            // the close box, and "click it on the diagram"), and a flag that quietens the whole diagram strands the editor
            // grey and arrowless if any one of them forgets.  This is the only place all four pass through.
            signalWindowOpen = false;

            highlightedSignals.clear();
            refresh();
        }
    }

    /**
     * The list itself, with the highlight held around it by the caller.
     *
     * One window that stays open, because the answer is a LIST: adding a second signal, removing one paired by mistake
     * and looking at what is there are the same gesture repeated, and a window that closed and reopened between each
     * hid the list at the moment it changed.  The one answer that gives the window back is "click it on the diagram",
     * because the next click belongs to the diagram.
     *
     * @param station the station's square
     */
    private void askAboutSignals(final Guard guard, TileKey station)
    {
        // A dialog of its own, held open while the list is worked on: an option pane re-shown in a loop makes the whole
        // window blink on every change and jumps the list out from under the reader.
        final javax.swing.JDialog dialog = new javax.swing.JDialog(
            ownerWindow(),
            I18n.t(guard.title),
            java.awt.Dialog.ModalityType.APPLICATION_MODAL);

        // OB-124: built here rather than being a class of its own, and just as much a window.
        TrainControlUI.applyWindowIcon(dialog);

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

        // How wide the heading wraps - set once the buttons are laid out, below (MT-479).
        final int[] headingWidth = {0};

        // What is paired now, on screen and on the diagram behind.  Called again after every change
        // rather than rebuilding the window.
        final Runnable show = () ->
        {
            java.util.List<TileKey> paired = signalsOf(guard, station);

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

            heading.setText(wrappedAt(paired.isEmpty()
                ? I18n.f(guard.how, describeTile(station))
                : I18n.f(guard.paired, describeTile(station)), headingWidth[0]));

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

        // THE SENTENCE ON TWO LINES, NOT ONE (MT-479: *"make the sentence split over 2 lines so the window isn't too wide."*).
        // A plain label makes the window as wide as its sentence (the entry guard's is a hundred and fifty characters
        // before a signal is paired), so the heading wraps at half that first sentence, never narrower than the row of
        // buttons.  The longer sentence once signals are paired takes more lines at the same width rather than widening
        // the window, and the heading keeps the taller of the two heights, so pairing the first signal changes the words
        // and not the window.
        String[] sentences = { I18n.f(guard.how, describeTile(station)), I18n.f(guard.paired, describeTile(station)) };

        headingWidth[0] = twoLineWidth(heading.getFontMetrics(heading.getFont()), buttons.getPreferredSize().width,
            sentences[0]);

        int tallest = 0;

        for (String sentence : sentences)
        {
            heading.setText(wrappedAt(sentence, headingWidth[0]));

            tallest = Math.max(tallest, heading.getPreferredSize().height);
        }

        heading.setPreferredSize(new java.awt.Dimension(heading.getPreferredSize().width, tallest));

        byAddress.addActionListener(e ->
        {
            pairSignalsByAddress(guard, station);

            show.run();
        });

        remove.addActionListener(e ->
        {
            java.util.List<TileKey> paired = signalsOf(guard, station);

            int at = list.getSelectedIndex();

            if (at >= 0 && at < paired.size()) removeGuardSignal(guard, station, paired.get(at));

            show.run();
        });

        // The one answer that has to give the window back: the next click belongs to the diagram.  The list returns by
        // itself once the click lands (see the click handler).
        byClick.addActionListener(e ->
        {
            if (needsTheGrid(station))
            {
                dialog.dispose();
                return;
            }

            signalFor = station;
            signalForGuard = guard;

            dialog.dispose();

            waitFor(I18n.f(guard.click, describeTile(station)));

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

        // Not smaller than it packed to (OB-107): dragging the edge up would hide the buttons at the bottom, which are how
        // the pairing is finished.
        dialog.setMinimumSize(dialog.getSize());

        // Beside the diagram rather than on top of it (OB-107): this window's own text says the signals "are outlined on
        // the diagram behind this window", so centring covers the very thing it asks the reader to look at.  Offset from
        // centre rather than pinned to a corner, so it still reads as belonging to the window it came from, and clamped
        // to the owner so it cannot land off-screen on a small display or a second monitor.
        placeBesideOwner(dialog);

        dialog.setVisible(true);
    }

    /**
     * Puts a dialog beside the diagram rather than over the middle of it.
     * For the windows that describe something drawn on the diagram behind them (the paired signals are outlined while
     * this is open), where a window centred on them hides what it is describing (OB-107).
     *
     * Offset from the owner's centre rather than pinned to a corner: a dialog in the corner of the screen reads as
     * belonging to nothing.  Clamped so it stays within the owner, which keeps it on the same monitor.
     *
     * @param dialog a packed dialog, ready to show
     */
    private void placeBesideOwner(java.awt.Window dialog)
    {
        java.awt.Window parent = ownerWindow();

        if (parent == null)
        {
            dialog.setLocationRelativeTo(owner());

            return;
        }

        dialog.setLocation(besideOwner(parent.getBounds(), dialog.getSize()));
    }

    /**
     * Where a dialog goes so that it does not sit on the middle of the window behind it.
     *
     * Public and static, and taking rectangles rather than windows, so that the one thing worth checking about it can be
     * checked without a modal dialog: everything this decides is a function of the two rectangles.
     *
     * Up and to the left of centre rather than pinned to a corner: a window in the corner of the screen reads as
     * belonging to nothing.
     *
     * @param parent the bounds of the window the dialog belongs to, in screen coordinates
     * @param dialog the size the dialog packed to
     * @return the screen point to put the dialog's top left corner at
     */
    public static java.awt.Point besideOwner(java.awt.Rectangle parent, java.awt.Dimension dialog)
    {
        // A fraction of the slack rather than a fixed offset, so it scales with how much room there
        // is, with a floor for the case where there is barely any.
        int x = parent.x + Math.max(12, (parent.width - dialog.width) / 8);
        int y = parent.y + Math.max(12, (parent.height - dialog.height) / 6);

        // Never off the parent's bottom or right, for a dialog as large as the window it belongs to.
        // The inner max is what stops a dialog LARGER than its parent being pushed off the top left
        // instead - it would rather overhang the bottom right than start off-screen.
        x = Math.min(x, Math.max(parent.x, parent.x + parent.width - dialog.width));
        y = Math.min(y, Math.max(parent.y, parent.y + parent.height - dialog.height));

        return new java.awt.Point(x, y);
    }

    /**
     * Finds signals by the addresses written on them and adds them.
     *
     * The LOGICAL address, the number the user sees everywhere (on the tile, in the switch panel, in the accessory
     * list), taken off the diagram component.  The control station subtracts one to reach the accessory, because the
     * protocol counts from zero, and asking the accessory would mean asking the user to subtract that one themselves.
     *
     * SEVERAL at once, separated by commas or spaces, because somebody who knows the addresses knows all of them.  Every
     * address that resolves is added, and the ones that do not are reported together at the end: stopping at the first
     * would throw away the good ones typed after it.
     *
     * @param station the station's square
     */
    private void pairSignalsByAddress(Guard guard, TileKey station)
    {
        String typed = JOptionPane.showInputDialog(owner(),
            I18n.t("autosetup.ui.promptSignalAddress"), I18n.t(guard.title),
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

        // A SIGNAL FOUND AND REFUSED is said with the addresses that found nothing (MT-505): on the grey line alone
        // it was behind this window.
        java.util.List<String> refused = new java.util.ArrayList<>();

        for (TileKey one : found)
        {
            String why = addGuardSignal(guard, station, one, false);

            if (why != null) refused.add(why);
        }

        if (!found.isEmpty()) refresh();

        // Said once, however many went wrong, and after the good ones have been taken: a dialog per bad address would be
        // a row of them to dismiss before seeing whether anything worked.
        if (!notNumbers.isEmpty() || !notFound.isEmpty() || !refused.isEmpty())
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

            for (String why : refused)
            {
                if (trouble.length() > 0) trouble.append("\n");

                trouble.append(why);
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
     * A guard signal refused, said in a popup as well as on the grey line (MT-505).
     *
     * Adam: *"any rejection errors should trigger a popup."*  The guard window comes back the moment a click is
     * answered and stands in front of the grey line; a popup stands in front of the window.
     *
     * @param why the sentence
     */
    private void refuseGuard(String why)
    {
        say(hint, why);

        JOptionPane.showMessageDialog(owner(), wrapped(why));
    }

    /**
     * Adds one signal to a station's protection, and says so in words.
     *
     * A refusal is RETURNED rather than said, so the caller can say it where it will be seen (MT-505): a click in a
     * popup of its own, a list of addresses in the one popup that reports them all.
     *
     * @param station the station's square
     * @param signal the signal's square
     * @param redraw whether to repaint now - false while several are being added at once
     * @return why the signal was refused, or null when it was added
     */
    private String addGuardSignal(Guard guard, TileKey station, TileKey signal, boolean redraw)
    {
        java.util.List<TileKey> paired
            = new java.util.ArrayList<>(signalsOf(guard, station));

        if (paired.contains(signal))
        {
            return I18n.f("autosetup.ui.signalAlreadyPaired", addressOf(signal));
        }

        // NOT THE STATION'S OTHER GUARD (AUT-C2: *"make sure the entry guard can never be the same as the exit guard"*).
        // The store refuses it as well; this is where the operator is told why.
        if (signalsOf(guard == Guard.ENTRY ? Guard.EXIT : Guard.ENTRY, station).contains(signal))
        {
            return I18n.f(guard == Guard.ENTRY ? "autosetup.ui.signalIsTheExitGuard"
                : "autosetup.ui.signalIsTheEntryGuard", addressOf(signal), describeTile(station));
        }

        paired.add(signal);

        if (guard == Guard.ENTRY) session.setEntrySignals(station, paired);
        else session.setProtectingSignals(station, paired);

        say(hint, I18n.f(paired.size() == 1 ? guard.set : guard.added,
            describeTile(station), describeTile(signal)));

        if (redraw) refresh();

        // The running layout is what throws a protecting signal (VD10-B2).
        setupChanged();

        return null;
    }

    /**
     * Takes one signal out of a station's protection, and says so in words.
     *
     * @param station the station's square
     * @param signal the signal's square
     */
    private void removeGuardSignal(Guard guard, TileKey station, TileKey signal)
    {
        java.util.List<TileKey> paired
            = new java.util.ArrayList<>(signalsOf(guard, station));

        if (!paired.remove(signal)) return;

        if (guard == Guard.ENTRY) session.setEntrySignals(station, paired);
        else session.setProtectingSignals(station, paired);

        say(hint, paired.isEmpty()
            ? I18n.f(guard.cleared, describeTile(station))
            : I18n.f(guard.removed, describeTile(signal), describeTile(station)));

        refresh();

        // The running layout is what throws a protecting signal (VD10-B2).
        setupChanged();
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
     * A filter cannot be got wrong, needs no error message, and never holds something the dialog will refuse.  Empty
     * stays reachable on purpose: clearing the field is how a length is removed.
     *
     * @param initial what to show
     * @return the field
     */
    private javax.swing.JTextField digitsOnly(String initial)
    {
        javax.swing.JTextField field = new javax.swing.JTextField(initial, 8);

        // Three digits, 0 to 999 (OB-048), refused as typed like the non-digits: a length of 100000 is not one anybody
        // means.

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

    /**
     * Asks again once this panel is in a window, which is when half of it can be answered (MT-292).
     *
     * The remembered mode is restored during CONSTRUCTION and applied there, and the half of it that reaches the diagram
     * goes through `owner()` - `SwingUtilities.getWindowAncestor(this)` - which is null until the panel has been added to
     * the editor.  So the two tick boxes were set and the diagram's text switch was not, and the dropdown said None over a
     * diagram still drawing every station name, the state exclusive options exist to make impossible.
     *
     * Called by `LayoutEditor.setAutonomyMode` from the same posted block that redraws the grid, because the first draw
     * happened before this window knew what it was.  Idempotent, like everything else on this path.
     *
     * The Text Labels box is applied here too (FR-133): it is remembered and the diagram's text switch is not.
     */
    public void applyRememberedCaptionMode()
    {
        applyCaptionMode();
        applyTextLabels();
    }

    /**
     * Makes the window agree with the caption choice (FR-061).
     *
     * The two boxes are the internal representation (everything that draws a caption asks them), so this sets them from
     * the one control the operator sees.  They are mutually exclusive by construction, which retires OB-174: there is no
     * state where somebody has asked for parked trains while the text is off.
     *
     * **The text switch is not touched (OB-272, FR-133).**  It is the writing on the diagram and nothing else: whether
     * a caption is drawn is the dropdown's own answer, `isDrawingCaptions`, and the text is the Text Labels box's
     * (`applyTextLabels`).
     */
    private void applyCaptionMode()
    {
        int mode = captions.getSelectedIndex();

        showParkedTrains.setSelected(mode == CAPTIONS_PARKED);
        showHomeLocomotives.setSelected(mode == CAPTIONS_HOMES);
    }

    /**
     * Makes the diagram's text switch agree with the Text Labels box (FR-133).
     *
     * Applied when the box is clicked and at open, where it matters (RGD-C3): the box is REMEMBERED
     * between opens and the text switch is not, so a remembered unticked box would otherwise come back
     * as a box saying one thing over a diagram drawing the other.  The cost is the courtesy: opening
     * the autonomy editor imposes the box on the text switch.
     */
    private void applyTextLabels()
    {
        if (showText.isSelected()) turnTextLabelsOn();
        else turnTextLabelsOff();
    }

    /**
     * Whether the diagram draws station captions - Station Names, Standing Locs or Homes (OB-272).
     *
     * The grid asks this for a caption, and the editor's text switch for the writing on the diagram, so the two are
     * independent: a caption is drawn under the three caption modes whatever the Text Labels box says.
     *
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
     * Control+L: the next caption option, wrapping from the last to the first (OB-272).
     *
     * The dropdown's four options; the Text Labels box is not part of the cycle (FR-133).  Selecting fires the
     * dropdown's own listener, so the step is remembered and drawn like a choice made with the mouse.
     */
    public void cycleCaptionMode()
    {
        if (captions == null || captions.getItemCount() == 0) return;

        captions.setSelectedIndex((captions.getSelectedIndex() + 1) % captions.getItemCount());
    }

    /**
     * @return the caption option chosen, one of the `CAPTIONS_` constants
     */
    public int getCaptionMode()
    {
        return captions.getSelectedIndex();
    }

    /**
     * Told that the diagram's text switch moved on its own, so the Text Labels box can agree (RGD-C3, FR-133).
     *
     * The box's own clicks arrive already agreeing; this answers anything else that flips the switch (DCN2-C1).
     * The dropdown is not touched: it decides the captions and the switch only the text.  The state is stored like a
     * click on the box.
     *
     * @param shown whether the labels are now displayed
     */
    public void textLabelsChanged(boolean shown)
    {
        if (showText == null || showText.isSelected() == shown) return;

        showText.setSelected(shown);

        VIEW_PREFS.putBoolean(PREF_TEXT_LABELS, shown);
    }

    /**
     * The editor's own text switch turned off, for an unticked Text Labels box (FR-133).
     *
     * The twin of `turnTextLabelsOn`, and idempotent for the same reason: `toggleText` flips, which is
     * the wrong verb for a caller that needs a particular state.
     */
    private void turnTextLabelsOff()
    {
        java.awt.Component where = owner();

        if (where instanceof LayoutEditor) ((LayoutEditor) where).hideTextLabels();
    }
    /**
     * Turns the diagram's text labels on, if this panel is in an editor that has them - for a ticked Text Labels box
     * (FR-133).  Captions no longer depend on it: the grid asks `isDrawingCaptions` (OB-272).
     *
     * Quiet when there is no editor: this panel is also built with no window at all, only to make menus from.
     */
    private void turnTextLabelsOn()
    {
        java.awt.Component where = owner();

        if (where instanceof LayoutEditor) ((LayoutEditor) where).showTextLabels();
    }

    /**
     * The grey a square autonomy will never choose is written in, in the blocker list (FR-074).
     *
     * Light enough to read as a shade of the ordinary text rather than as a second colour ("lightly grey"), and dark
     * enough to stay legible on the white this dialog uses.  It is not `Color.GRAY`, which at 50% on white reads as
     * disabled, and these entries are not.
     */
    private static final java.awt.Color NOT_CHOSEN_BY_AUTONOMY = new java.awt.Color(120, 120, 120);

    /** 0 to 999: three digits is every length anybody means, and 100000 is not one (OB-048) */
    private static final int MAX_LENGTH_DIGITS = 3;

    private void applyLength(TileKey tile)
    {
        Set<TileKey> targets = selection.isEmpty()
            ? new LinkedHashSet<>(java.util.Arrays.asList(tile)) : new LinkedHashSet<>(selection);

        // read from a tile that is actually going to change: with a selection the clicked tile is not
        // necessarily among them, and prefilling from it would show a number the dialog will not touch
        TileKey sample = targets.iterator().next();

        // A field that will not accept anything but digits (OB-043): a number is the only answer this question has, so
        // refusing the keystroke is kinder than accepting it and throwing it away with an error box.
        final javax.swing.JTextField field = digitsOnly(segmentLengthPrefill(sample, selection.isEmpty()));

        // SELECTED, so the number can be typed over rather than cleared first (OB-176).  On the event thread AFTER the
        // dialog is up, because a component that is not showing cannot take focus: requesting it now would be dropped.
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

        // OK, CLEAR, CANCEL: a 0 typed here is an answer now, so taking a length away needs a door of its own.
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

        // Emptied and submitted still means none (OB-043): the Clear button's answer, now that 0 is kept.
        applyLengthAnswer(tile, entered.isEmpty() ? null : Integer.valueOf(entered));
    }

    /**
     * What Segment Length's box opens holding.
     *
     * @param sample the square read - the one clicked, or the first of a selection
     * @param wholeRun whether the answer is for the run the square is in, rather than for each selected square
     * @return the text to prefill
     */
    public String segmentLengthPrefill(TileKey sample, boolean wholeRun)
    {
        int measured = wholeRun ? lengthShownFor(sample) : session.getStore().getTileLength(sample);

        // EMPTY WHERE NOTHING HAS BEEN SAID (GUI-B2).  A 0 submitted here is an answer (kept, and taken off every list of
        // what still needs measuring), so a box that opened on "0" made OK, the default, write a deliberate answer nobody
        // gave.  Mass Assign Lengths' own prompt opens empty for the same reason.  A square answered 0 opens on its
        // answer.
        if (measured <= 0 && !session.getStore().isTileLengthAnswered(sample)) return "";

        return String.valueOf(measured);
    }

    /**
     * What Segment Length does with an answer: a length for the run, or for each selected square; null clears it.
     *
     * **0 is an answer, and clearing is null.**  A 0 typed here records the run - or each selected square - as answered
     * 0, as Mass Assign Lengths does (OB-274): not offered again, and read by every length rule but the own-tail one
     * (OB-300) as measured track of no length (TDU-C6).  The Clear button clears.
     *
     * The decision, apart from the dialog that asks for it, so it can be driven without a display.
     *
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
            // ONE SQUARE SPEAKS FOR THE RUN, which means the run measures what was typed (SET-B3).  This
            // announces the change itself, so the setupChanged below is for the selection arm.
            setRunLength(tile, typed);
            selection.clear();

            return;
        }
        else
        {
            // A SELECTION IS SQUARES, NOT RUNS: shift-clicking several and typing a number has always given that
            // number to each of them, and a run in the selection is reached through the leader that is in it.
            for (TileKey target : targets)
            {
                session.setTileLength(target, typed);
            }
        }

        selection.clear();

        // Lengths are what the reversal guard measures with, and it asks the RUNNING layout (VD10-B2): the same seam as
        // MT-246, one setter over.
        setupChanged();
    }

    /**
     * Whether Why Not Moving is waiting for its click and this square has a train on it to ask about (FR-102).
     *
     * The editor highlights stations with trains, so it is clear what the user can click on.  The train as the running
     * railway has it where there is one - a run moves trains the setup has not been told about - and as the setup
     * places it otherwise.
     *
     * @param tile the square
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

        // A clicked finding's outline goes with the next click on the diagram (RLV9-C3)
        if (!noticeOutline.isEmpty())
        {
            noticeOutline.clear();

            refresh();
        }

        rememberIfStation(tile);

        // A train's tail is waiting to be told the farthest sensor it crossed, and this is that click (2026-09-14).
        //
        // First, like the other gestures that asked for a particular square: what an ordinary click would do to this
        // square is not the question.
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

            // WHICH WAY, asked rather than assumed (OB-006): two squares describe a run, not a direction, and the direction
            // is the entire content of the decision.  Picking the order the user happened to click in is a guess, and a
            // wrong guess closes a stretch of railway the wrong way round with no trace on the diagram saying which way was
            // meant.  Named by the squares themselves, in the words this panel uses for a square, so the answer can be
            // checked against what is on screen.
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

            // THE ARROWS, NOT THE DIAGRAM (OB-185): a restriction changes no tile art, only the arrow drawn over the tile,
            // so redrawing every label to show it flickered the whole screen once per click on the one tool used many times
            // in a row.
            if (changed >= 0) annotationsChanged();

            // Armed for the next one: closing a run is rarely a single act (a yard is several).
            if (changed >= 0 && tool == Tool.ONE_WAY)
            {
                say(hint, I18n.f("autosetup.ui.oneWayDoneAgain", changed));
            }

            refresh();
            return;
        }

        // A station is waiting to be told which signal protects it, and this is that click.  Answered BEFORE the ignored
        // check below, because a signal is one of the squares that check greys out (autonomy routes no trains through a
        // signal); here it is the only thing worth clicking.
        if (signalFor != null)
        {
            if (heldByARun()) return;

            TileKey station = signalFor;

            // IN A POPUP as well as on the line (MT-505) - see `refuseGuard` - AND BACK TO THE GUARD WINDOW (MT-505, MT-588:
            // *"it should send the user back to the existing popup so they can click on another square."*).  The window has
            // Click It on the Diagram for another square.
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
        // ignored check, for the same reason as the signal gesture: a gesture that asked for a particular square has to
        // be able to answer about it, and the check below is about what an ORDINARY click does.
        if (blockingFor != null)
        {
            if (heldByARun()) return;

            TileKey station = blockingFor;

            String refusal = whyNotABlocker(station, tile);

            if (refusal != null)
            {
                // The gesture STAYS ARMED on a refusal, unlike the signal one, which drops it: missing a small square is
                // the ordinary way to get this wrong, and re-arming costs the whole right-click menu again.  Escape or a
                // right-click still cancels.
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

            // And back to the list, where the square just clicked is now ticked.  A second one is a
            // button and another click rather than the whole right-click menu again.
            promptBlockingPoints(station);
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
     * to None, clicking a square changed its direction and nothing on screen moved, which reads as a broken control, and
     * the natural response to a control that does nothing is to click it again, which cycles the square on to a state
     * nobody asked for.
     *
     * Restrictions rather than All, because it is the default for the same reason: open track is most of a layout.
     * Only from None, and only on an edit: somebody who has chosen All or Arrivals has chosen something that already
     * shows their edit.
     */
    private void showRestrictionsIfHidden()
    {
        if (directionsView() != 2) return;

        showDirectionsView(1);

        say(hint, I18n.t("autosetup.ui.infoDirectionsShownAgain"));
    }

    /**
     * Whether an ordinary left click on this square changes which way trains may run through it - what the diagram's
     * pointer and tooltip offer (FR-112; Adam: *"it is not clear to the user that tiles with arrows can be clicked"*).
     *
     * The questions `tileClicked` and `cycle` ask before a click changes anything, in their order: nothing armed (a tool
     * or a gesture takes the click for itself), a square autonomy takes notice of, not a link, and track whose next
     * state lands on something (a piece joined to nothing cannot be made one way, so its click says there is no path).
     * `regression.testTheEditorSaysWhatItsToolsDo.testTheClickAndItsPointerAgree` clicks every kind of square and holds
     * the two to one answer.
     *
     * @param tile a square, or null
     * @return true where a click there changes a direction
     */
    public boolean clickChangesDirections(TileKey tile)
    {
        if (tile == null || session.getGraph() == null || anythingIsArmed() || isIgnored(tile)) return false;

        TileKey target = leaderOf(tile);

        // NOR A SQUARE OUT OF SERVICE (RSA63-C5): nothing can pass it, it draws no arrows, and `cycle` declines it
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

    /**
     * Whether a square is out of service - nothing may pass or stop there - so that it has no directions to set.
     *
     * @param target the square that would be set
     * @return true where the square is switched off
     */
    private boolean isOutOfService(TileKey target)
    {
        return Boolean.FALSE.equals(session.getPointProperty(target, "active"));
    }

    /**
     * Whether a square is a link, which has no direction of its own - the track either side of it governs.
     *
     * @param target the square that would be set
     * @return true for a link
     */
    private boolean isALink(TileKey target)
    {
        LayoutDiagramComponent here = session.getGraph() == null ? null : session.getGraph().getTiles().get(target);

        return here != null && org.traincontrol.automationui.TilePorts.hasPortal(here.getType());
    }

    /**
     * Left-click: change which way trains may run through this piece of track.
     *
     * It cycles both ways -> one way -> the other way -> closed, and says in words what it just became.  A switch, a
     * crossing or a double curve steps through its arms instead (`cycleBranching`, `cycleCrossing`).
     *
     * Whatever is clicked, the change lands on the run: clicking any tile of a straight run sets the whole run.
     */
    private void cycle(TileKey tile)
    {
        TileKey target = leaderOf(tile);

        // A link has no direction of its own - the track either side of it governs - so a click on one has nothing to
        // change (its route is a stub, the same side twice).  The menu already declines to offer them, and the click has
        // to decline too, or the two disagree about the same square.
        if (isALink(target))
        {
            say(hint, I18n.t("autosetup.ui.infoLinkHasNoDirection"));
            return;
        }

        // NOR A SQUARE OUT OF SERVICE (RSA63-C5): its directions are kept for when it is back in service and are not drawn
        // meanwhile (round 127), so a click changed something nobody could see
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

        // The tile that changed can be some way from the one clicked, at the head of a long run, so it
        // is flashed as well as named - a message about a square nobody can find is half an answer.
        if (!target.equals(tile) && onReveal != null) onReveal.accept(target);

        // Say what actually happened, not a direction announced unconditionally: a run that could not be set must not
        // report success.
        say(hint, changed == 0 ? I18n.t("autosetup.ui.oneWayNoPath")
            : I18n.f("autosetup.ui.cycledTo", describeTile(target), describe(next, route)));

        if (changed != 0) showRestrictionsIfHidden();

        // THE ARROWS, NOT THE DIAGRAM (MT-334): a restriction changes no tile art, only the arrow drawn over the tile, so
        // rebuilding every label on the page to show it flashes (measured, 384 of 384).  The running layout is still
        // rebuilt: annotationsChanged does that half exactly as setupChanged does.  A run's direction is an edge in the
        // running graph (VD11-A1).
        annotationsChanged();
    }

    /**
     * Left-click on a switch, a crossing or a double curve: the next combination of open and shut ARMS.
     *
     * Not the next of four whole-tile states: a junction's states are not four (on a crossing they are sixteen), and
     * the useful ones, such as north-south open while east-west is shut, are exactly the ones a whole-tile cycle cannot
     * reach.  Nor can they be enumerated as Direction constants: TOWARD_A names a route's own first side, and nothing
     * makes those agree between branches.
     *
     * So the state cycled is what the drawing already shows: one bit per ARM of the tile, saying whether a train may
     * leave through it.  Every combination is reachable, and the click is a plain binary counter over them.
     *
     * The bits translate back to routes exactly.  A route (A,B) reads its two arms: both open is both ways, one open is
     * one way toward it, neither is closed.  An arm shared by two branches - a switch's toe - is one bit, so the two
     * branches cannot disagree about it (which drew a green arrow on top of a red one).
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

        // COUNTED FROM WHAT IS IN FORCE (VD18-C2), the state the arrows and the boxes show.  Safe because every state the
        // list holds stores answers the hardware allows (`cycleStates`) and each reads back in force as itself; counted
        // from what was stored, a closure stored on a permanent turnout as an arm toward its fork matched no state, and
        // the click went back to the list's first, closed, with nothing on screen changing.
        int current = armMask(target, routes, sides);

        int at = states.indexOf(current);

        int next = states.get((at + 1) % states.size());

        applyArmMask(target, routes, sides, next);

        say(hint, I18n.f("autosetup.ui.cycledSwitch", describeTile(target), armState(next, sides)));

        showRestrictionsIfHidden();

        // NOT REFRESHED HERE (speed, 2026-10-04): the click that brought this here refreshes straight after, whatever
        // happens - and a refresh is a whole setup check, the list and the strip.
    }

    /**
     * Left-click on a square of a two-square crossing - two permanent turnouts drawn toe to toe (OB-320): the next of
     * both tracks both ways, each track one way with the other both ways, the four ways the two can each run one way, and
     * both closed.
     *
     * Adam: *"Pretend these are two curved tracks on one tile, so we need to cycle through both possible directions on
     * both (4 combos)"* - both ways first and closed last, as a crossing's own first answers go; and on MT-688: *"when
     * cycling, there is no option for red in one direction and green in the other 3"* - one track one way and the
     * other open, the more often wanted, so straight after both ways.  Not the arms of `cycleBranching`: the two tracks
     * share the side the squares meet at, and one bit for it cannot say that one track runs onto the other square while
     * the other runs off it.
     *
     * Counted on the crossing's north or west square whichever square is clicked, so a click on either carries on from the
     * same place; the session sets the other square's half of each track with it.
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
     * Turns a set of open arms into a direction for each route, and applies them together.
     *
     * The arms are what the drawing shows and what the user is choosing between; the per-route directions are what the
     * model stores.  One translation, used by the click and by the checkboxes, so the two cannot come to different
     * conclusions about the same square.
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

        // THE ARROWS, NOT THE DIAGRAM (MT-334), as in `cycle`.  A direction is an edge in the running graph (VD11-A1),
        // which annotationsChanged rebuilds as setupChanged does.
        annotationsChanged();
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
     * The direction one route stores for a set of open arms: both its arms open is both ways, one open is one way
     * toward it, neither is closed.  `applyArmMask`'s translation, and the one the click's states are checked by
     * (VD18-C2), so the two cannot differ.
     *
     * @param route the route
     * @param sides the tile's arms, as `armsOf` orders them
     * @param mask the open arms
     * @return the direction
     */
    private static Direction answerFor(org.traincontrol.automationui.TilePorts.Route route,
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides, int mask)
    {
        // ONE TRANSLATION, the session's too (OB-351): every door that sets a direction stores its arms' answers
        return TileGraph.answerFor(route, sides, mask);
    }

    /**
     * Whether every route's answer for these open arms is one the hardware leaves a meaning (VD18-C2) - see
     * `cycleStates`.
     *
     * @param routes the tile's routes
     * @param sides its arms
     * @param mask the open arms
     * @return true when the click may store it
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

        // Then everything else.  The four above are the answers wanted most, so they come first and are reached in at
        // most four clicks; the rest follow, so clicking eventually reaches every combination rather than declaring some
        // unavailable (a four-state cycle could not express "north and west open, east shut", and a sixteen-state counter
        // put all-open fifteen clicks from all-shut).
        for (int mask = 1; mask < all; mask++)
        {
            if (!states.contains(mask)) states.add(mask);
        }

        // ONLY THE STATES THE CLICK STORES AS IT MEANS THEM (VD18-C2).  On a permanent turnout every route is directed at
        // the toe, so a combination opening any other arm stores `BOTH` or the way toward the fork: answers
        // `TileGraph.directionIsPossible` says the editor must not offer, and which read back in force as the toe-ward way
        // or as closed (the click could step from closed to closed for ever).  Every combination on an ordinary switch or
        // a crossing is possible, and everything shut always is, so the list is never empty.
        states.removeIf(state -> !everyAnswerIsPossible(routes, sides, state));

        return states;
    }

    /**
     * The arms of a tile - every side any of its routes touches - in a fixed order so the counter
     * always steps the same way.
     */
    private static java.util.List<org.traincontrol.automationui.TilePorts.Side> armsOf(
        Map<RouteId, org.traincontrol.automationui.TilePorts.Route> routes)
    {
        return TileGraph.armsOf(routes.values());
    }

    /**
     * Which arms are open IN FORCE, as a bitmask over armsOf() (VD18-C2).
     *
     * Read the way the arrows are drawn - an arm is open if ANY branch through it lets a train out -
     * so the number the counter advances from, and the boxes the menu ticks, are the state the user can see.
     *
     * In force rather than stored (`inForce`): a permanent turnout's routes are all directed at its toe, and its
     * untouched default `BOTH` read raw opened every arm.
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
     * The other test asks a question about the TRACK; this asks about a locomotive and about now: every station it might
     * be sent to, and for each one the reason it was refused (occupancy, exclusions, switched-off squares and parking
     * rules are all in the decision).  The list says why and the traces say where: a train with nowhere to go draws none,
     * which is visible from across the room.
     *
     * Against the layout as last LOADED, which while this editor is open is the last saved configuration; the hint says
     * unsaved edits are not in it, because an explanation that quietly describes a different railway is worse than none.
     *
     * **NOT ON THE EVENT THREAD** (D3-C1; Adam: *"I would rather take it off EDT.  It's not critical now, but we want to
     * avoid these pitfalls."*).  The answer comes from `Layout.explainDestinations`, which is `synchronized` on the
     * `Layout` (the monitor a dispatch holds for the whole of `configureAndLockPath`), and asking for it here parked the
     * event thread on that monitor (OB-192, the last exception in
     * `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor`).  A rule with one exception is a rule with a way
     * in.
     *
     * **WHAT STAYS HERE IS WHAT MUST.**  `getStationIndex` DERIVES the square-to-Point translation when nothing has yet,
     * and `layoutSource.get()` can build a `Layout` (it asks `getAutoLayoutIfLoaded` and may answer null), so both are
     * captured here, on the caller's thread as `refreshCoveredTrack` does, and handed over: the worker asks nothing
     * that can build anything.
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

        // Captured on the event thread, for the reason the javadoc gives: this derives when nothing has yet, and that
        // derivation belongs to the thread holding the setup.
        org.traincontrol.automationui.StationIndex index = session.getStationIndex();

        // AND WHAT THE SETUP SAYS ABOUT TURNING, ARRIVALS AND CLOSED SQUARES, captured here for the index's reason
        // (GSE-C3): they are the setup's live collections, which the event thread edits, and a click on the diagram while
        // the answer was worked out changed them under a walk that was iterating them.
        final WhySetup setup = new WhySetup(session);

        // WHICH TIER IT ANSWERS FOR (MT-434), read with the radio here on the event thread, and the square remembered so
        // switching Path Type can ask it again (a manual-mode answer must not say "will never be chosen in autonomy").
        final boolean byHand = pathTypeAuto != null && !pathTypeAuto.isSelected();

        lastWhyTile = tile;

        // SAID NOW, because the answer lands a beat later and a tool that goes quiet on a click reads as one that did
        // nothing.  The key is the locomotive panel's: the same question, asked there.
        say(hint, I18n.t("autolayout.ui.whyWorking"));

        // WHICH ASK THIS IS: two clicks in a row are two searches and the first started is not necessarily the first to
        // finish, so the answer carries the number of its ask and `paintWhy` throws away anything that is not the
        // latest.
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
     * Works the answer out, off the event thread, and hands it to the event thread to paint.
     * The shape `CoveredTrackRenderer` and `ReturnHomeTriageRenderer` use: the question takes the railway's monitor and
     * the painting takes the event thread, so the two are done in that order by two threads.
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
                // "Working out the reasons..." for ever.  The layout being rebuilt underneath the pass is the way this
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

    /**
     * Composes the answer on a worker thread rather than painting one: the stations autonomy could choose, then the
     * ones it never will, each alphabetical, with the page named for a station that is not on the train's (FR-080).
     *
     * The report and the lines are built into a value object and handed over instead of being written into the banner
     * and into `traces` from here: `traces` is read by the paint, so a worker writing into it would be drawing on the
     * diagram from the wrong thread.
     *
     * @param layout the railway
     * @param index the square-to-Point translation
     * @param setup what the setup says about turning, arrivals and closed squares, read on the event thread (GSE-C3)
     * @param tile the square that was clicked
     * @param byHand true when Path Type is Manual: the reasons a hand-driven send meets (MT-434)
     * @return what to say and what to draw
     */
    private WhyAnswer composeWhy(org.traincontrol.automation.Layout layout,
        org.traincontrol.automationui.StationIndex index, WhySetup setup, TileKey tile, boolean byHand)
    {
        // Which train is standing here, asked of the LAYOUT rather than of the setup, because the layout's opinion of
        // where trains are decides what runs.
        org.traincontrol.base.Locomotive standing = null;

        // Through StationIndex, the one place that knows a square is several Points and which ones.
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
            // NOTHING DRAWN AND NOTHING CLEARED, which is what a null trace map means: the three answers that are not
            // about a train leave whatever was on the diagram alone.
            return new WhyAnswer(I18n.t("autosetup.ui.whyNoTrainHere"), false, null);
        }

        // The lines this answer draws, built here and installed by the paint.  Empty rather than null from here on: an
        // answer about a train that draws nothing has to TAKE the previous answer's lines off the diagram.
        java.util.Map<TileKey, java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>>
            drawn = new java.util.LinkedHashMap<>();

        // FOR THE TIER ASKED (GUI3-C1): by hand, only what stops a route of any kind.
        String cannotStart = layout.explainCannotStart(standing, byHand);

        if (cannotStart != null)
        {
            return new WhyAnswer(I18n.f("autosetup.ui.whyCannotStart",
                escape(standing.getName()), escape(cannotStart)) + unsavedWarning(), true, drawn);
        }

        // THE REASONS AND WHICH OF THEM AUTONOMY WOULD NEVER CHOOSE, taken under one lock (FBR-C6) so a station cannot be
        // printed under the wrong heading.
        org.traincontrol.automation.Layout.Destinations grouped = layout.explainDestinationsGrouped(standing, byHand);

        java.util.Map<String, String> reasons = grouped.getReasons();
        java.util.Set<String> neverChosenPoints = grouped.getBarred();

        // STATIONS FIRST, THEN BERTHS, EACH ALPHABETICAL (FR-080: *"show all stations first, then show berths
        // (non-autonomy stations), both in alphabetical order"*).  The railway reports them in its priority order, which
        // is what `pickPath` walks and not how anybody reads a list.  Grouped as `AutoLocomotiveStatus.whyNotReport`
        // groups its window (a station with any copy autonomy could choose is filed with the choosable ones), so the two
        // answers to one question read alike.
        //
        // AND BY PAGE FIRST (MT-429: *"Update sorting to be by page then station name, rather than station name only, with
        // the current page being first, then others sequentially later"*).  The current page is the one this editor is
        // showing, or where the panel has no page (the track diagram's own menu) the page the train stands on; the others
        // follow in page-name order.  Each shown name's key is recorded before it is filed.
        final String currentPage = page != null ? page : tile.getPage();

        final java.util.Map<String, String[]> sortKey = new java.util.HashMap<>();

        java.util.Comparator<String> byPageThenName = (a, b) ->
        {
            String[] x = sortKey.get(a);
            String[] y = sortKey.get(b);

            int byPage = x[0].compareTo(y[0]);

            if (byPage != 0) return byPage;

            int byName = x[1].compareTo(y[1]);

            return byName != 0 ? byName : a.compareTo(b);
        };

        java.util.Set<String> available = new java.util.TreeSet<>(byPageThenName);
        java.util.Map<String, String> choosable = new java.util.TreeMap<>(byPageThenName);
        java.util.Map<String, String> neverChosen = new java.util.TreeMap<>(byPageThenName);

        // FROM THE EVENT THREAD'S COPIES, and never from the session: this runs on WhyRenderer (GSE-C3).
        java.util.Set<TileKey> mustTurn = setup.mustTurn;
        java.util.Set<TileKey> mayTurn = setup.mayTurn;

        // The red arrows, so the routes drawn here are routes a train would actually be offered (OB-120): drawing a run
        // into a station that refuses arrivals from that side answers wrongly, in the direction that wastes the most
        // time.
        java.util.Map<TileKey, java.util.Set<org.traincontrol.automationui.TilePorts.Side>> barred = setup.barred;

        // Collapsed to STATIONS, the way the locomotive panel's tooltip does: the reasons come back keyed by the running
        // graph's Points, and a square is several of those, so a derived-graph station would appear three times over under
        // generated names the user never chose.
        for (java.util.Map.Entry<String, String> entry : reasons.entrySet())
        {
            TileKey where = index.squareOf(entry.getKey());

            String station = where == null ? entry.getKey() : describeTile(where);

            // AND ITS PAGE, where that is not the current page (FR-080: *"If a point is on another page, show it"*;
            // MT-429): a name with no page is a name somebody has to go and look for, or two different squares that read
            // as one.
            String nameOnly = station;

            boolean elsewhere = where != null && where.getPage() != null && !where.getPage().equals(currentPage);

            if (elsewhere) station = I18n.f("autosetup.ui.whyStationOnPage", station, where.getPage());

            sortKey.put(station, new String[] {
                where == null || where.getPage() == null ? "2" : elsewhere ? "1" + where.getPage() : "0", nameOnly });

            if (entry.getValue() == null)
            {
                if (!available.add(station)) continue;

                // Drawn, so "where can it go" is read off the track rather than out of a list
                if (where != null && setup.reducer != null)
                {
                    // AND THE CLOSED SQUARES, so this tool and the findings panel walk one railway
                    // (DIR-B1).
                    trace(drawn, setup.reducer.findPath(tile, where, mayTurn, mustTurn, barred,
                        setup.shut), tile, true, where);
                }
            }
            else
            {
                // One line per station.  The first reason is the one that would have stopped it; the
                // others are the same square's other arrival sides saying the same thing.
                if (neverChosenPoints.contains(entry.getKey()))
                {
                    // Only when no copy of it has already gone into the other group.
                    if (!choosable.containsKey(station) && !neverChosen.containsKey(station))
                    {
                        neverChosen.put(station, entry.getValue());
                    }
                }
                else
                {
                    neverChosen.remove(station);

                    if (!choosable.containsKey(station)) choosable.put(station, entry.getValue());
                }
            }
        }

        // "Nowhere to go", with nothing after it, is not an answer: when no station was considered either, the report is
        // the word "nowhere" and nothing else, and "every station refused this train" cannot be told from "there were no
        // stations to refuse it", which need opposite things done.  So when nothing was considered, say what the setup
        // actually holds.
        String going = available.isEmpty()
            ? I18n.t("autosetup.ui.whyNowhere")
            : I18n.f("autosetup.ui.whyCanGo", available.size(), escape(join(new java.util.ArrayList<>(available))));

        StringBuilder blocked = new StringBuilder();

        // On Manual, the stations it cannot be sent to: autonomy's "could choose" is not the question asked (MT-434).
        whyGroup(blocked, I18n.f(byHand ? "autolayout.ui.whyHeaderByHand" : "autolayout.ui.whyHeaderCandidates",
            choosable.size()), choosable);
        whyGroup(blocked, I18n.f("autolayout.ui.whyHeaderBarred", neverChosen.size()), neverChosen);

        String detail = blocked.toString();

        if (available.isEmpty() && reasons.isEmpty())
        {
            detail = "<br>" + escape(I18n.f("autosetup.ui.whyNothingConsidered",
                countStations(layout), countDestinations(layout),
                escape(describeBlock(layout.getLocomotiveLocation(standing)))));
        }

        // The reason behind the reason: "No track route leads there", said once per station, is what a SEVERED railway
        // looks like from here, and the answer to the wrong question.  A switch or a signal drawn without an accessory
        // address cannot be routed over, so every run through it is cut.  So when nothing is available and the setup has
        // blocking findings, say that first: it is the one thing that has to be fixed before any of the rest can be true.
        if (available.isEmpty())
        {
            int blockingFindings = countBlockingFindings();

            if (blockingFindings > 0)
            {
                detail = "<br><b>" + escape(I18n.f("autosetup.ui.whyBlockedBySetup", blockingFindings))
                    + "</b>" + detail;
            }
        }

        return new WhyAnswer(I18n.f("autosetup.ui.whyReport", escape(standing.getName()),
            going, detail + unsavedWarning()), true, drawn);
    }

    /**
     * Puts the worker's answer in the banner and its lines on the diagram, on the event thread.
     *
     * ONLY THE LATEST ANSWER PAINTS: an answer to a question the user has moved on from must not replace the one they
     * are waiting for (`paintReturnHomeFromTheAnswer`'s rule about a Layout that has been replaced).
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
     * Waits until every "why is this train not moving" ask already started has been worked out.
     *
     * Asking and reading the answer are two moments, so anything that reads what it said (in practice a test) has to
     * wait in between; `TrainControlUI.awaitCoveredTrack` is the same method for the same reason.
     *
     * IT WAITS FOR THE ANSWER, NOT FOR THE PAINT.  The paint is an `invokeLater` queued before the worker gives the job
     * up, so a caller that wants to read the banner flushes the event queue after this, and one that calls this ON the
     * event thread waits for a paint that cannot run.
     *
     * BOUNDED, and it says which it did: an unbounded wait would turn a worker that has died into a hung window rather
     * than a stale answer.
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
     * on the event thread (GSE-C3).
     *
     * The answer is composed on `WhyRenderer`, and these are the setup's live collections, which the event thread edits,
     * so the worker is handed copies taken on the thread that owns them.  The session's own methods build each of the
     * four collections fresh.  The reduction is held by reference: a rebuild replaces it rather than editing it, so the
     * one captured goes on describing the setup the copies came from.
     */
    private static final class WhySetup
    {
        /** The squares where a run must turn round. */
        private final java.util.Set<TileKey> mustTurn;

        /** The squares where a run may turn round, less the ones where it must. */
        private final java.util.Set<TileKey> mayTurn;

        /** The sides each station refuses arrivals by. */
        private final java.util.Map<TileKey, java.util.Set<org.traincontrol.automationui.TilePorts.Side>> barred;

        /** The squares switched off. */
        private final java.util.Set<TileKey> shut;

        /** The reduction the routes are walked on, or null when there is none. */
        private final org.traincontrol.automationui.GraphReducer reducer;

        /**
         * Reads all five from the session, on the caller's thread - which has to be the event thread.
         *
         * @param session the setup being edited
         */
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
     * What one "why is this train not moving" ask worked out: a message, and the lines to draw.
     *
     * A value object handed from the worker to the event thread, which is what makes the move safe:
     * everything the answer is made of is finished before the event thread sees any of it.
     */
    private static final class WhyAnswer
    {
        /** The report, mark-up included when `rich`, plain text otherwise. */
        private final String message;

        /** Whether the message carries mark-up this class composed, or is a plain sentence. */
        private final boolean rich;

        /**
         * The lines to lay on the diagram, or null to leave whatever is drawn there alone.
         *
         * Null is not the same as empty.  An answer about a train replaces the traces; one that is not about a train (no
         * railway, not a station, no train standing here) leaves them.
         */
        private final java.util.Map<TileKey,
            java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> drawn;

        private WhyAnswer(String message, boolean rich, java.util.Map<TileKey,
            java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> drawn)
        {
            this.message = message;
            this.rich = rich;
            this.drawn = drawn;
        }
    }

    /**
     * The one thread the "why is this train not moving" answer is worked out on.
     * DAEMON, and it goes away when it is idle: this panel is built and thrown away every time the editor is opened, and
     * every tile menu builds one, so a core thread held for the life of each would be a thread per editor opened
     * (`allowCoreThreadTimeOut`).
     *
     * NOT COALESCED, unlike the window's renderers, which refresh on every railway event: this one is a click, and two
     * clicks are two questions, the second of which must win (`whyAsked`).
     */
    private final java.util.concurrent.ThreadPoolExecutor WhyRenderer = whyRenderer();

    /** Which ask is the current one, so a slow answer to an abandoned question is discarded. */
    private final java.util.concurrent.atomic.AtomicLong whyAsked =
        new java.util.concurrent.atomic.AtomicLong();

    /** How many asks have been started and not yet worked out, for `awaitWhy`. */
    private final java.util.concurrent.atomic.AtomicInteger whyWorking =
        new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Announced on whenever an ask is finished with, for `awaitWhy`.
     *
     * The count is changed under this monitor, so a waiter cannot read the count, find an ask
     * outstanding, and go to sleep in the instant between the worker clearing it and announcing it.
     */
    private final Object whySettled = new Object();

    /**
     * Builds the pool above.
     *
     * A method rather than an initialiser, because `allowCoreThreadTimeOut` has to be called on the pool after it is
     * constructed.
     *
     * @return the pool
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
            // THE RULE, ASKED, rather than spelled out again (MON-C2): a third copy of it, without `!isReversing()`,
            // overcounted every reversing station, and the number feeds "why is nothing considered".
            if (layout.isSendableDestination(point)) count++;
        }

        return count;
    }

    /**
     * One headed group of "station: reason" lines for the why answer, left out entirely when it is empty.
     *
     * The headings are the locomotive panel's window's own, for FR-080: the same two groups under the
     * same two names.  An empty heading reads as a group that failed to load, so there is none.
     *
     * @param into the answer being built
     * @param heading the group's heading, already translated
     * @param lines station to reason, in the order to show them
     */
    private static void whyGroup(StringBuilder into, String heading, java.util.Map<String, String> lines)
    {
        if (lines.isEmpty()) return;

        into.append("<br><b>").append(escape(heading)).append("</b>");

        for (java.util.Map.Entry<String, String> line : lines.entrySet())
        {
            into.append("<br>").append(escape(line.getKey())).append(": ").append(escape(line.getValue()));
        }
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
     * Only when there are: a caveat printed every time is a caveat nobody reads.
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

    /**
     * Asks whether a train could get from one sensor to another, and shows the route.
     *
     * Two clicks, like portal pairing.  The answer comes from the same reduction everything else uses, so what this says
     * is what a running train would find - not a second opinion.
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

        // Both ways, always: asking the user to nominate a direction only makes them run the test twice, and a one-way
        // run looks identical to a broken one until tried from the other end.
        //
        // The squares trains may turn round at are handed over, because everywhere else the run may not leave by the side
        // it came in at (otherwise the test drew routes that doubled back on themselves at ordinary track).  MAY, not
        // must: reversibleTiles() covers both, but the build emits no straight-through copy for a must-turn square, so a
        // train reaching it is turned back and cannot pass, and handing those over would draw a route through a square the
        // running railway turns every train at.  The same turn sets the reachability check uses, from the one place that
        // computes them, so the path test and the findings panel cannot disagree about which way a train may go.
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

        // Both directions, each as its own line: a direction with no path draws nothing, so "can it get there, can it get
        // back" is read off the track, and a one-way route is visibly one line.  Each leg is told where it ENDS, which
        // decides its colour: a run to a station autonomy will never choose is drawn in a third shade, so on a route
        // between an ordinary station and a parking berth the two legs deliberately differ.
        trace(traces, there, testFrom, true, tile);
        trace(traces, back, tile, false, testFrom);

        // AND WHETHER THE TIER IN THE RADIO WOULD ACTUALLY GO THERE.  A path existing and a train being sent along it are
        // different facts.  `stationsAutonomyWillNotChoose` is the runtime's rule asked of the diagram, in the two clauses
        // a SQUARE can answer (`isAutoDestination`, the switch the menu calls Can Be Chosen in Full Autonomy, and
        // `isActive`); `Layout.isSendableDestination` requires both, and its `!isReversing()` clause is about a COPY and
        // cannot be asked here.
        //
        // TWO DIFFERENT SENTENCES (E8V-B1).  A manual-only station is a preference about what autonomy picks, and a
        // person may still drive there.  A square switched OUT OF SERVICE is a fact about the railway that binds every
        // tier (`isPathClear` refuses a closed final point in all of them, and `isOfferableToOperator` refuses it
        // outright), so telling that operator to "switch Path Type to Manual" sends them to a shut door.  The shut
        // sentence is said on BOTH tiers; the manual-only one on Auto alone, where every station is somewhere a person
        // may send a train.
        //
        // Said as well as the route rather than instead of it: the track is passable as far as the walk is concerned, and
        // "no path" would be a lie about the railway to make a point about the settings.
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

        // Named, not just counted: "3 runs" says a route exists and nothing about which one, and the squares it crosses are
        // spread over a page too big to follow a line across; the sensors it calls at are the short way to say where it
        // went.
        sayRich(hint, I18n.f("autosetup.ui.testBothWays",
            escape(describeTile(testFrom)), escape(describeTile(tile)),
            leg(there), escape(via(there)), leg(back)) + tierNote);

        lastTestFrom = testFrom;
        lastTestTo = tile;

        testFrom = null;
    }

    /**
     * Runs the last path test again, for the tier the radio now says (MT-399).
     *
     * Only while the test tool is still what is armed and no new test is half-way through - a radio click is
     * not a reason to draw a route over whatever the user moved on to.
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

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    private String leg(java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run)
    {
        return run == null ? I18n.t("autosetup.ui.testLegBlocked")
            : I18n.f("autosetup.ui.testLegReachable", run.size());
    }

    /**
     * Lays one leg of a tested route onto the squares it crosses.
     *
     * **INTO A MAP IT IS GIVEN**, rather than into `traces` (D3-C1).  The "why is this train not
     * moving" tool works its answer out on a worker now, and `traces` is read by the paint - so a
     * caller on a worker builds its lines in a map of its own and the event thread installs them.  The
     * path test, which is still a straight-through gesture on the event thread, passes `traces` itself
     * and nothing about it changes.
     *
     * @param into where the lines go
     * @param run the reduced path, or null when there is no route that way
     * @param from the square it leaves
     * @param forward whether this is the outbound leg
     * @param to the square it ENDS at, which decides whether the leg is drawn as manual-only
     */
    private void trace(java.util.Map<TileKey,
        java.util.List<org.traincontrol.automationui.TileAnnotation.Trace>> into,
        java.util.List<org.traincontrol.automationui.GraphReducer.ReducedEdge> run,
        TileKey from, boolean forward, TileKey to)
    {
        if (run == null) return;

        // ASKED ONCE, FOR THE WHOLE LEG.  Every square of the run carries the answer, because what it
        // says is where the line goes - and a colour that only appeared on the last square would be
        // invisible on a route that runs off the edge of the page.
        //
        // `manualOnlyStations` AND NOT `stationsAutonomyWillNotChoose` (E8V-B1).  The colour was
        // asked for in these words: *"just use a different color going to manual-only points"* - and
        // since `isActive` joined the runtime set, a square the operator has switched OUT OF SERVICE
        // is in it too.  That is not a manual-only point: nothing may be sent there by any tier, so
        // colouring the leg as though a hand-driven run were the remedy points at a shut door.
        //
        // The set is the runtime's rule in the two clauses a SQUARE can answer - `isAutoDestination`
        // and `isActive` - less the shut ones.  `Layout.isSendableDestination` adds `!isReversing()`,
        // which is about a COPY rather than a square: a may-reverse square keeps a plain copy autonomy
        // can choose perfectly well, so the square-level answer is the honest one here rather than a
        // narrower version of the runtime's.  (It carried a compulsory-turn clause too until Adam's
        // ruling of 2026-09-09; see OB-195.)
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

            // Which way the line enters and leaves, worked out from the squares either side of this
            // one.  Null at the ends of the run, where the line stops in the middle of the square
            // rather than running off into track nobody asked about.
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
        return session.describeTile(tile);
    }

    // --- state ------------------------------------------------------------------------------------

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

    /**
     * Puts the directions box on a view, by its meaning - the default for one it does not have.
     *
     * @param view what the view means
     */
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

        // TRACK NO STATION IS ON is greyed and draws no arrows (OB-353) - drawn so only: `isIgnored` does not ask it, so
        // every other edit still works there, and its lengths and outlines are still drawn (Adam: "yes, it can accept
        // edits")
        boolean stationless = !ignored && session.reachesNoStation(tile);

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
        // states cannot be on screen at once.  Nor is anything greyed for its track meanwhile (OB-353).
        if (focused) { ignored = !isPairableSignal(componentAt(tile)); stationless = false; }

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

        if (isShowingDirections() && !focused && session.getGraph() != null && !ignored && !follower && !stationless)
        {
            // Asked of the SESSION since FR-037, so this window and the ordinary track diagram cannot
            // disagree about which way a piece of track runs.  What stayed here is every reason this
            // window has for saying nothing at all - see the condition above and the fallback below.
            //
            // Every state is drawn, both-ways included.
            //
            // It used to be hidden on plain track - both ways is the majority of a layout and is also
            // the default, so drawing it put an arrow on every square - and that was tolerable only
            // while the checkbox that turned it back on existed.  With the checkbox gone, "runs both
            // ways" became the one state of four that looked like nothing at all: a square cycled from
            // one-way, to the other way, to closed, to BLANK, and there was no way to tell the blank
            // apart from a square nobody had touched.
            //
            // A run that is open both ways is a decision like any other, and the run marker below
            // already keeps a bare layout from being a field of arrows.
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

        // Only the square a test is waiting on borrows the selection outline now.  The route itself is
        // drawn as a line through the track, which says which way it goes; an outline around every
        // square it crossed said only that it went somewhere.
        // The paired signal borrows the outline too, while its menu item is being acted on.  An
        // address names the signal but does not say where it is, and where it is is the thing somebody
        // checking a pairing actually wants to know.
        boolean outlined = isOutlined(tile);

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
            traces.get(tile), directionsView() == 1,
            ignored ? null
                : session.arrivalMarks(tile, directionsView() == VIEW_ARRIVALS))
            // Which, with FR-118's icons, draws every point's icon white with its colour round it - see
            // TileAnnotation.inTheEditor.  Here and nowhere else: this is the surface the direction
            // arrows are read on, and they are drawn over the icon.  With the classic icons, what stations
            // are drawn with, the editor's marks are the diagram's (round 131).
            .inTheEditor();

        // NONE WHERE A CROSSING'S SQUARES MEET, as on the track diagram (OB-322)
        session.withoutTheCrossingsMiddle(tile, annotation);

        // GREYED as a square autonomy takes no notice of is, where no station is on its track (OB-353)
        if (stationless && !ignored && componentAt(tile) != null) annotation.withNoStation();

        // ALLOWED ONLY: the green arrows, and none of the red
        if (directionsView() == VIEW_ALLOWED) annotation.withoutRestrictions();

        // A train the setup puts here gets a mark of its own.  The caption says which train, and the
        // caption is on another square, is sometimes on no square, and can be switched off - so
        // without this the diagram can be read right through without seeing where the trains are.
        //
        // NOT WITH SHOW TRAINS OFF (RSA63-C3): the switch hides the trains, and this mark is one of them - under the icon
        // while they show, it appeared when they were switched off.
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
        // THE SENSOR CURVES TOO, as on the track diagram (MT-712; Adam, 2026-10-10: "They are not parallel with the track
        // on one side.").  They were left out for square N/E/S/W arrows, which a curve's arrows have not been since they
        // were aimed from the middle of its track (TD-4) - along its chord, with the flag or without.  What the flag still
        // decided was a double curve's OTHER road, whose arrows were aimed from the badge's road, across their own rails.
        return session.isCurveSquare(tile);
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
     * The answers for one route, with the one-way options named by where they lead rather than by an A
     * and a B nobody can see.
     *
     * **ONLY THE ONES THE HARDWARE LEAVES ANY MEANING** (Adam, 2026-09-22: *"green arrows in the
     * direction that can't be chosen shouldn't be offered"*).  An authored direction ANDs with the
     * tile's own restriction, so on a permanent turnout - whose routes are all directed at the toe -
     * "toward the fork" and "both ways" change no train's road.  Offering them drew a green arrow for a
     * road no train can take, and is the control-that-offers-what-the-guard-refuses shape of OB-057 and
     * OB-090.  `TileGraph.directionIsPossible` is the question, and `directionAllows` beside it is the
     * walk's half of the same one.
     *
     * **The tick follows what is IN FORCE, not what is stored.**  `getDirection` answers `BOTH` for
     * these tiles by default - deliberately, because a default of "out of the toe" would AND with the
     * blades to leave the tile impassable - and `BOTH` is not an option here, so the radio group would
     * have shown nothing selected.  Where only one way is possible, that one is ticked.
     */
    private List<javax.swing.JMenuItem> directionItems(final TileKey tile, final RouteId routeId,
        org.traincontrol.automationui.TilePorts.Route route)
    {
        List<javax.swing.JMenuItem> items = new java.util.ArrayList<>();

        // WHAT IS IN FORCE, not what is stored (VD18-B1) - `inForce`, which the arm boxes beside these submenus ask
        // too (VD18-C2), so the two cannot tick different ways.
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
     * The answer in force on a route: what is stored, narrowed by the hardware (VD18-B1, VD18-C2).
     *
     * **TWO CASES, NOT ONE.**  A stored answer the editor does not offer on this route
     * (`TileGraph.directionIsPossible`) is either the default `BOTH` on a permanent turnout - whose routes are all
     * directed at the toe, so the blades narrow it to the one road there is - or an answer that permits only an entry
     * the blades refuse, which is a CLOSURE.  Reading "the possible way" for both would show a shut road as open.  Every
     * answer an ordinary switch or plain track stores is possible there, so this changes nothing on them.
     *
     * One answer for the branch submenus' tick and the arm boxes' (VD18-C2): the boxes read the stored direction, and
     * on an untouched permanent turnout ticked every arm while the submenus ticked the toe-ward way alone.
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
            // NOT WHILE TRAINS RUN (Adam, 2026-09-28) - see `refusedWhileRunning`
            if (refusedWhileRunning()) return;

            // Set on the run, not the tile: a run of plain track has one direction, and setting it a
            // tile at a time is both busywork and a way to end up with a run that contradicts itself.
            int set = session.setRunDirection(tile, routeId, direction);

            if (set == 0)
            {
                say(hint, I18n.t("autosetup.ui.oneWayNoPath"));
            }

            refresh();

            // IN THE LAMBDA, beside the write, not at the end of the builder (VD11-A1).
            //
            // A run's direction is an edge in the running graph, and this radio is the one-way choice
            // sitting on the same submenu as "All branches", which has announced since VD10-B2.  So
            // the bulk answer reached the railway and the per-route one beside it did not.
            //
            // Through the LIGHT door, like its three siblings (MT-334) - see `cycle`.
            annotationsChanged();
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

        // the road its contact is on, as on the track diagram (MT-712)
        org.traincontrol.automationui.TilePorts.Route route = session.stationRoad(tile);

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
            turns && !session.isMustTurnAround(tile),
            Boolean.FALSE.equals(session.getPointProperty(tile, "active")),
            // which way a station's track runs out, for the terminus's flat end - as the diagram's badge (FR-118)
            station ? session.deadEndSide(tile) : null);
    }

    /**
     * Re-derives what the MENU needs to bind an item to the right square, and announces nothing (OP2-B1).
     *
     * `refresh()` ends in `onChanged.run()`, and on the track diagram's own menu that runnable saves the setup file
     * and rebuilds the static autonomy layer - so calling it before every right-click, which AUS-B1 did, ran the
     * whole findings pass and wrote the file twice per menu.  What the menu actually needs is the run map: which
     * squares belong to a run, and which square speaks for each.  That is one cheap derivation.
     */
    public void refreshRunLeaders()
    {
        runLeaders = session.runLeaders();
    }

    /**
     * What Segment Length shows for this square: what the whole run measures, not one square's share (SET-B3).
     *
     * **A run of plain track has one square that speaks for it** (`behaviour.md` section 5b), and until Mass Assign
     * Lengths existed that square held the whole number, so the leader's own length WAS the run's.  The walk shares a
     * piece's whole length over every square it covers (MAL-B2), so after one the leader holds a share - a 1 or a 2 on
     * a run the operator measured as 7 - and this dialog opened on that share as if it were the run's length.
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
     * Gives a run the length typed for it: the leader holds it, and the squares that follow the leader hold nothing.
     *
     * The other half of `lengthShownFor` (SET-B3).  Writing the typed number to the leader alone left the shares Mass
     * Assign Lengths had spread over the followers in place, so a run given 4 through this door measured 4 plus
     * whatever the walk had left on the rest of it.  Zeroing the followers is how the run comes to measure exactly
     * what was typed, and 0 is what "no length" is stored as everywhere here.
     *
     * @param tile any square of the run
     * @param length the whole run's length
     */
    public void setRunLength(TileKey tile, int length)
    {
        TileKey leader = leaderOf(tile);

        // ONE RE-DERIVATION FOR THE WHOLE RUN (AUS-C1).  Writing square by square paid for a new graph, reduction
        // and station index per square, on the event thread.
        java.util.Map<TileKey, Integer> lengths = new java.util.LinkedHashMap<>();

        for (TileKey square : runTilesOf(tile))
        {
            lengths.put(square, square.equals(leader) ? length : 0);
        }

        session.setTileLengths(lengths);

        // AND THE RUNNING LAYOUT IS TOLD, because this is a door in its own right: it is public, the track
        // diagram's own panel has no Save, and lengths are what the room rule measures with (MT-246).
        setupChanged();
    }

    /**
     * Every square of the run this one belongs to - itself when it is in no run.
     *
     * `runLeaders` maps each follower to its leader, so the run is this square's leader together with every key that
     * answers with it.
     *
     * @param tile any square
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

    /**
     * Whether this tile takes its direction from another one, rather than carrying its own.
     */
    private boolean isFollower(TileKey tile)
    {
        // THE SESSION'S ANSWER, which the track diagram asks too (OB-361)
        return session.followsItsRun(tile);
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
        // route buttons beside the track and turntables rather than left looking like something to configure.
        if (component.getType() == LayoutDiagramComponent.componentType.LAMP) return true;

        // A ROUTE BUTTON ON A RUNNING LINE IS TRACK (OB-332; Adam, 2026-10-11: "sometimes a direction arrow is placed on a route tile, but those are greyed out in the autonomy editor and can't be clicked/changed.  We need the direction arrows to be editable and clickable.").
        // The graph runs the line through it (`TileGraph.transparentRoutes`), so a direction is stored on it and its
        // arrow drawn there - and greying every button left that arrow out of reach.  Only a button with no track
        // through it, beside the rails, is greyed now.  A click sets the button alone: it is not part of a run.
        if (org.traincontrol.automationui.TilePorts.isTransparent(component.getType()))
        {
            return session.getRoutes(tile).isEmpty();
        }

        return org.traincontrol.automationui.TilePorts.isDisqualified(component.getType());
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
     * Told when only the ANNOTATIONS need redrawing - the arrows, not the tiles under them.
     *
     * Adam, OB-185: **"changing any pathing arrow in the track autonomy editor makes the whole screen
     * flicker."** It did, because there was one redraw door and it rebuilt the grid: every label
     * destroyed and made again, which is a visible flash even when the tile art is identical.
     *
     * A direction restriction changes no tile art at all. What it changes is the arrow drawn over the
     * tile, and those are annotations - `LayoutEditor.refreshAutonomyAnnotations` sets them on the
     * labels that already exist. So this exists to say "the arrows moved" without saying "the diagram
     * changed".
     *
     * Null when the panel is not inside an editor, in which case there are no annotations to refresh
     * and `onDiagramChanged` is the only door there was.
     */
    private Runnable onAnnotationsChanged;

    /**
     * @param listener what to run when only the arrows need redrawing
     */
    public void setOnAnnotationsChanged(Runnable listener)
    {
        this.onAnnotationsChanged = listener;
    }

    /**
     * A change that moves the arrows and nothing else (OB-185).
     *
     * The running layout is still rebuilt - a one-way restriction is a real change to the graph, and a
     * diagram agreeing with a railway that has moved on is the failure `setupChanged` exists to avoid -
     * but the tiles are left alone and only the annotations over them are re-set.
     *
     * Falls back to the full redraw when there is no annotation door, so a caller cannot silently draw
     * nothing by choosing this one.
     */
    private void annotationsChanged()
    {
        // THE WASH DEPENDS ON THE DIRECTIONS THIS DOOR CHANGES (AUS-C3).
        //
        // A run clicked through to closed has no leg in the reduction, so its squares stop being unmeasured track
        // and the Unmeasured Track wash should leave them - and opening one back up is the mirror.  The cache was
        // filled before the click, and this door, unlike `setupChanged`, did not forget it: the count in the menu
        // moved and the wash did not, which is the one thing MT-455 says never happens.
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
     * Says that the setup changed: rebuild the railway it describes, and redraw.
     *
     * **The thing every door has to do and seven of nine did not** (MT-246). Adam, 2026-09-03:
     * *"Setting a home locomotive via track diagram viewer does not activate the send home button, and
     * it is not persisted when closing the app."* Both halves are this, and they are ONE cause - which
     * the first version of this javadoc got wrong (`VD10-A2`), so it is written out properly here.
     *
     * `rebuildRunningLayoutFromSetup` tells the running `Layout` about the edit. Every affordance that
     * asks a question about homes, lengths, priorities or usage reads the **running** layout rather
     * than the setup - so a home set from the diagram left "Return Locomotives Home" greyed, because
     * `triageReturnToHome` asked a layout nobody had told.
     *
     * **And that is also why it did not survive the application closing**, which is the half that
     * looks like a separate defect and is not. The exit path captures the RUNNING layout back into the
     * configuration (`TrainControlUI.saveState`, "the save on the way out"), and `captureFromLayout` walks
     * `POINT_OPERATIONAL_KEYS` writing what the layout has and **removing what it does not** - `home`
     * among them. So the edit was saved to disk correctly and then deleted from it on the way out, by
     * a running layout that had never heard of it.
     *
     * **What this does NOT do is save**, and the first version of this javadoc said it did.
     * `onDiagramChanged` is wired by `TrainControlUI` to `repaintLayout()`. The save is the panel's
     * other runnable, `onChanged`, which `refresh()` calls at its end - and `item()` calls `refresh()`
     * after every menu action, so every door here already reached disk before this method existed.
     *
     * **One exit rather than a line in each door**, because the two that already worked -
     * `placementChanged` and `promptName` - are the two that had been reported before, and adding a
     * third copy is how there comes to be an eighth door without one.
     * `testEditorSurfaceRules` fails if a writer does not end here.
     */
    private void setupChanged()
    {
        // THE HIGHLIGHT'S SQUARES ARE FORGOTTEN HERE AS WELL AS IN refresh() (MAL-B3).  Control+E writes a length
        // through applyLength and arrives here without ever running refresh(), so the square showed its new number and
        // its old amber.  Every door that changes the setup comes through this method.
        unmeasuredSquares = null;

        if (onDiagramChanged != null) onDiagramChanged.run();

        // ONE REBUILD PER GESTURE, NOT ONE PER WRITE (VD10-C2).
        //
        // `rebuildRunningLayoutFromSetup` is a full configuration load and layout regeneration on the
        // event thread.  Doors nest - picking "Can stop" runs `setUsage`, which calls `setStation`,
        // and the radio's own listener then calls `placementChanged` - so putting the exit on each of
        // them counted one click three times.  That is the cost `DY3-C5` and `REL-C4` spent two
        // findings taking out of the bulk doors, arriving at the single ones from the other side.
        //
        // Coalesced rather than pushed back onto the callers: which doors call which is exactly the
        // thing that drifts, and a rule that says "announce, unless your caller will" is the rule
        // that produced nine silent doors in the first place.
        //
        // It also fixes a second thing the count hid: the first of the three rebuilt the railway from
        // a HALF-APPLIED edit - the station flag written and `active` not - before rebuilding it from
        // the state the user actually asked for.  One rebuild, after the gesture, sees only the
        // finished edit.
        rebuildRunningLayoutSoon();
    }

    /**
     * The rebuild half of a setup change, coalesced - one per gesture, not one per write (VD10-C2).
     *
     * Split out so that an annotations-only change can ask for it without asking for a grid rebuild
     * too (OB-185). The comment below is the whole reason it is coalesced and is unchanged.
     */
    private void rebuildRunningLayoutSoon()
    {
        if (setupChangePending) return;

        setupChangePending = true;

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            setupChangePending = false;

            // ASKING TO BE TOLD IF IT IS DECLINED (VD11-C8).
            //
            // This is the door where a decline is a surprise.  The edit was made against a railway
            // that was not running - no item edits while one is (`refusedWhileRunning`) - and the
            // rebuild is posted one event later, so a run starting in between throws the edit away.
            // `TrainControlUI.autonomyEditorClosed()` passes false because it has already said it.
            // AND WHOSE PLACEMENTS THIS GESTURE EDITED, drained so each edit is claimed once
            // (D2-A1).  The rebuild is coalesced, so several writes can reach one rebuild and their
            // names accumulate until it happens; after it, nothing is pending and the running railway
            // is the only answer there is again.
            java.util.Set<String> edited = new java.util.LinkedHashSet<>(placementsJustEdited);

            placementsJustEdited.clear();

            if (parentWindow() != null) parentWindow().rebuildRunningLayoutFromSetup(true, edited);
        });
    }

    /** Set between a setup change and the single rebuild it coalesces into (VD10-C2). */
    private boolean setupChangePending;

    /**
     * Locomotives whose placement this panel has written into the setup since the last rebuild.
     *
     * See `placementChanged` for what this is for.  Held rather than passed straight through because
     * the rebuild is coalesced: one gesture can write several times and there is one rebuild after it.
     */
    private final java.util.Set<String> placementsJustEdited = new java.util.LinkedHashSet<>();
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

        // Forgotten rather than recomputed: worked out again the first time a square asks, and only if the display is
        // on - so a refresh with the highlight off costs nothing.
        unmeasuredSquares = null;
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

        // AND THE PATH TYPE THEY TEST BY (Adam, FR-105: "also disable/grey out the auto/manual radio buttons")
        if (pathTypeLabel != null) pathTypeLabel.setEnabled(!ignored);
        if (pathTypeAuto != null) pathTypeAuto.setEnabled(!ignored);
        if (pathTypeManual != null) pathTypeManual.setEnabled(!ignored);

        // AND ONE-WAY RUN, the third of them (OB-235; Adam, 2026-09-24, on MT-528: *"works, but one-way run isn't"*): a
        // run closed one way on a page nothing is built from closes nothing.  Put down if it was armed when the page
        // was left out - here rather than through cancelPendingGesture, which refreshes.
        if (oneWayButton != null)
        {
            oneWayButton.setEnabled(!ignored);

            if (ignored && tool == Tool.ONE_WAY)
            {
                tool = Tool.NONE;
                oneWayFrom = null;
                oneWayButton.setSelected(false);

                // AND ITS PROMPT (TDD2-C7), which asked for the first square, as putting a tool down says.
                say(hint, I18n.t("autosetup.ui.hintClickToCycle"));
            }
        }

        findingsModel.clear();
        findingTiles.clear();
        findingRelated.clear();
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
                ? finding.getSubject() : describeTile(finding.getAbout());

            // BOTH, because {0} alone cannot say which train (OB-153).
            //
            // The line above deliberately prefers the tile's description to the finding's own subject,
            // and is right to for every finding whose subject IS the point - an unnamed Point's name is
            // its coordinate, which tells the reader nothing. FR-046's train-length warning is the
            // first whose subject is a LOCOMOTIVE, and that preference swallowed it, so the warning
            // named the station the train was standing at instead of the train.
            //
            // Passing both keeps {0} meaning exactly what it has always meant, so no other message in
            // this list changes, and lets the two that need a name use {1}.
            String text = describe(finding.getMessageKey(), subject, finding.getSubject(),
                finding.getCount(), finding.getDetail(), finding.getThird());

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

            into.add(new Object[] {finding.getTile(), text, finding.getRelated()});
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
            banner.setText(blockingCountText(errors));
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

        // THE WHOLE SENTENCE, for where the window is too narrow to show it (OB-342)
        banner.setToolTipText(banner.getText());

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

        // NOT WHILE A RUN HOLDS THE SETUP - see `heldByARun`; the box goes back to what the setup says
        if (heldByARun())
        {
            excludePage.setSelected(!excluded);

            return;
        }

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

        // An excluded page is absent from the running graph entirely (VD11-A1).
        setupChanged();
    }

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

            // Asked again, for the same square, until the name is one the session will take (TDR-C11).
            while (name != null && !name.trim().isEmpty()
                && AutonomySession.whyNotAPointName(name) != null)
            {
                JOptionPane.showMessageDialog(owner(), wrapped(AutonomySession.whyNotAPointName(name)));

                name = askForName(I18n.f("autosetup.ui.promptNameEverything", i + 1, unnamed.size()));
            }

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

        // OB-034 AGAIN, IN BULK (VD10-B2).  `promptName` rebuilds for a single rename and says
        // why at length: the running layout keeps the old names, every caption looks up a Point
        // it has never heard of, and the labels go blank.  Naming sixty squares at once is that
        // failure sixty times over.
        setupChanged();
    }

    /**
     * Goes through every piece of track on this page that still has no length, and then its switches (Mass Assign
     * Lengths).
     *
     * **Name Everything's walk, for lengths.**  Each piece is outlined whole - it is one run somebody measures with one
     * tape, between two sensors or switches - and scrolled to.  The prompt asks for the WHOLE length of the piece and the
     * session shares it over the piece's squares (Adam, 2026-09-16: per stretch).  Then every switch on the page that has
     * no length is outlined and one turnout length is asked for, given to each (Adam: "One length for all switches").
     *
     * OK, Skip and Cancel as in Name Everything; Escape stops, like Cancel (MAL-B4).  0 is an answer - a stretch with
     * no length, kept apart from one nobody has measured (OB-274).  `setupChanged` once at the end, for the
     * running layout; the session itself rebuilds on every write, as it does when Name Everything names a square.
     */
    private void massAssignLengths()
    {
        // A NEW ROUND OPENS AFRESH; within it, each prompt opens where the last was left (Adam, on MT-454).
        walkPromptAt = null;

        java.util.List<AutonomySession.Stretch> pieces = session.stretchesNeedingALengthOn(page);
        java.util.Set<TileKey> switches = session.switchesNeedingALengthOn(page);
        java.util.Set<TileKey> crossings = session.sharedSquaresNeedingALengthOn(page);

        if (pieces.isEmpty() && switches.isEmpty() && crossings.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoNothingToMeasure"));

            return;
        }

        boolean wroteAny = false;
        boolean stopped = false;

        for (int i = 0; i < pieces.size() && !stopped; i++)
        {
            AutonomySession.Stretch piece = pieces.get(i);

            // Asked again rather than trusted from the list made before the walk began.  No two pieces share a square,
            // so nothing answered earlier can have filled this one - but a walk that trusts a stale list is how a prompt
            // ends up about nothing.
            if (!session.needsALength(piece)) continue;

            outlineAndReveal(piece.getTiles());

            String question = I18n.f("autosetup.ui.promptMassAssignLength", i + 1, pieces.size(),
                nameForPrompt(piece.getFrom()), nameForPrompt(piece.getTo()), piece.getTiles().size());

            Integer whole = askForWholeLength(question, LENGTHS_TITLE);

            // 0 IS AN ANSWER NOW (Adam, 2026-09-23, OB-274): *"allow a length of 0 as a length that is set
            // deliberately ... this will allow everything to get assigned without what appears to be a skip."*  It
            // used to be refused with "0 is the same as no length at all".  Skip, and a blank box, still leave the
            // piece as it was.
            if (whole == null) stopped = true;
            else if (whole >= 0 && session.assignStretchLength(piece, whole)) wroteAny = true;
        }

        if (!stopped)
        {
            switches = session.switchesNeedingALengthOn(page);

            if (!switches.isEmpty())
            {
                outlineAndReveal(switches);

                String question = I18n.f("autosetup.ui.promptMassAssignSwitches", switches.size());

                Integer turnout = askForWholeLength(question, LENGTHS_TITLE);

                // 0 answers the switches as well (OB-274) - two switches back to back are his adjacent tracks.
                if (turnout != null && turnout >= 0 && session.assignSwitchLength(switches, turnout)) wroteAny = true;

                if (turnout == null) stopped = true;
            }
        }

        // AND THE SQUARES TWO ROADS SHARE (Adam, 2026-09-19: *"For crossings: if its length is set, count that length
        // once in each direction"*).  Their own step, because a crossing is not a turnout and need not measure like
        // one; one length for all of them, which is the shape his switch ruling chose.
        if (!stopped)
        {
            crossings = session.sharedSquaresNeedingALengthOn(page);

            if (!crossings.isEmpty())
            {
                outlineAndReveal(crossings);

                String question = I18n.f("autosetup.ui.promptMassAssignCrossings", crossings.size());

                Integer across = askForWholeLength(question, LENGTHS_TITLE);

                if (across != null && across >= 0 && session.assignSwitchLength(crossings, across)) wroteAny = true;
            }
        }

        selection.clear();

        if (wroteAny) setupChanged();
        else refresh();
    }

    private static final String LENGTHS_TITLE = "autosetup.ui.menuMassAssignLengths";
    private static final String MAXIMA_TITLE = "autosetup.ui.menuMassAssignMaxTrainLengths";
    private static final String TRAINS_TITLE = "autosetup.ui.menuMassAssignTrainLengths";

    /**
     * Goes through every station on this page that will still take a train of any length, asking for its maximum (Mass
     * Assign Station Max Train Lengths).
     *
     * Adam, 2026-09-17: *"add a similar feature to walk stations that don't have a max length set up, so I can enter
     * it"*.  **Mass Assign Lengths' walk, for stations**: the same prompt, so the number box has the focus, Enter
     * submits, and each prompt opens where the last was left; OK, Skip and Cancel, with Escape stopping.  Each station
     * is outlined and scrolled to before it is asked about.  0 is refused with a sentence saying why - it means "any
     * length", which is what the station has now - and asked again.  The number lands where the right-click menu's
     * Maximum Train Length puts it.
     */
    private void massAssignMaxTrainLengths()
    {
        // A NEW ROUND OPENS AFRESH, as the other two walks.
        walkPromptAt = null;

        java.util.List<TileKey> stations = session.stationsWithoutAMaximumOn(page);

        if (stations.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoEveryStationHasAMaximum"));

            return;
        }

        boolean wroteAny = false;

        for (int i = 0; i < stations.size(); i++)
        {
            TileKey station = stations.get(i);

            // Asked again, as Mass Assign Lengths asks: a list made before the walk began is not trusted.
            if (!session.stationsWithoutAMaximumOn(page).contains(station)) continue;

            outlineAndReveal(java.util.Collections.singleton(station));

            String question = I18n.f("autosetup.ui.promptMassAssignMaxTrainLength", i + 1, stations.size(),
                nameForPrompt(station));

            Integer maximum = askForWholeLength(question, MAXIMA_TITLE);

            while (maximum != null && maximum >= 0 && !session.assignMaxTrainLength(station, maximum))
            {
                JOptionPane.showMessageDialog(owner(), wrapped(I18n.t("autosetup.ui.errorMaxTrainLengthZero")));

                maximum = askForWholeLength(question, MAXIMA_TITLE);
            }

            if (maximum == null) break;

            if (maximum >= 0) wroteAny = true;
        }

        selection.clear();

        // Persisted, and the running layout told - what `promptNumber` does for one station.
        if (wroteAny) setupChanged();
        else refresh();
    }

    /**
     * Goes through every locomotive autonomy would run that has no train length, asking for each one's (Mass Assign
     * Train Lengths, FR-094, named so on MT-567) - and where none is missing one, through every train with the length it has,
     * which Skip keeps (MT-533: the item is never greyed).
     *
     * Adam, 2026-09-23: *"Rather than adding complexity through new menus, add a bulk tool to the autonomy editor to set
     * missing train lengths, similar to how the station lengths are set."*  **Mass Assign Station Max Train Lengths'
     * walk, for trains**: the same prompt - the number box has the focus, Enter submits, each prompt opens where the
     * last was left; OK, Skip and Cancel, with Escape stopping - and the list asked again before each train rather than
     * trusted from the start.
     *
     * **Which trains: the list the Atomic Routes refusal names**, asked through `TrainControlUI.trainsWithoutALength`,
     * so the refusal cannot name a train this walk does not offer.  That is where FR-094 came from: on MT-470 the
     * refusal named the trains and there was nowhere to go.
     *
     * **A length is 1 to `TrainControlUI.ROUTE_TRAIN_LENGTH_MAX`**, the range the locomotive menu's own dropdown
     * offers - two places that set one number must not disagree about what a legal value is.  0 is refused with a
     * sentence saying why: it is what a train with no length already holds - and where the train has one, a sentence
     * that does not say otherwise (TDU-C7).  Skip leaves the train as it was.
     *
     * **Written through `applyTrainLength`**, the one door every other train-length gesture uses, so the findings and
     * the grey follow the number exactly as they do from the locomotive menu.  A train length belongs to the
     * locomotive, not to this setup, so the editor's Cancel does not take it back - and each prompt says so, before the
     * answer is given rather than after.
     *
     * A train standing on this page is outlined and scrolled to while it is asked about; one elsewhere, or standing
     * nowhere, is named and nothing is outlined.
     */
    private void massAssignTrainLengths()
    {
        // A NEW ROUND OPENS AFRESH, as the other walks.
        walkPromptAt = null;

        TrainLengthDoor door = trainLengthDoor();

        java.util.List<String> trains = door.trainsWithoutALength();

        // EVERY TRAIN WHEN NONE IS MISSING A LENGTH (MT-533): the item is never greyed, and this is what it offers then -
        // each train with the length it has, which Skip keeps.
        java.util.Map<String, Integer> known = trains.isEmpty() ? door.trainLengths() : null;

        if (known != null) trains = new java.util.ArrayList<>(known.keySet());

        if (trains.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoNoTrainToMeasure"));

            return;
        }

        for (int i = 0; i < trains.size(); i++)
        {
            String train = trains.get(i);

            // Asked again, as the other walks ask: a list made before the walk began is not trusted.  Not when going
            // through every train, where one with a length is the point.
            if (known == null && !door.trainsWithoutALength().contains(train)) continue;

            TileKey standing = squareOfTrain(train);

            if (standing != null && page != null && page.equals(standing.getPage()))
            {
                outlineAndReveal(java.util.Collections.singleton(standing));
            }
            else
            {
                selection.clear();
                refresh();
            }

            String question = known != null
                ? standing == null
                    ? I18n.f("autosetup.ui.promptMassAssignTrainLengthKnownNotPlaced", i + 1, trains.size(), train,
                        known.get(train))
                    : I18n.f("autosetup.ui.promptMassAssignTrainLengthKnown", i + 1, trains.size(), train,
                        nameForPrompt(standing), known.get(train))
                : standing == null
                    ? I18n.f("autosetup.ui.promptMassAssignTrainLengthNotPlaced", i + 1, trains.size(), train)
                    : I18n.f("autosetup.ui.promptMassAssignTrainLength", i + 1, trains.size(), train,
                        nameForPrompt(standing));

            // THE LENGTH IT HAS, in the box (Adam, on MT-566: "prefill the textbox with the current length, if any") -
            // selected, so a number typed replaces it.  Only when going through every train: a train missing one has none.
            Integer had = known != null ? known.get(train) : null;

            String prefill = had != null && had > 0 ? String.valueOf(had) : "";

            Integer units = askForWholeLength(question, TRAINS_TITLE, prefill);

            while (units != null && units >= 0 && !acceptsATrainLength(units))
            {
                JOptionPane.showMessageDialog(owner(), wrapped(I18n.f(known != null
                    ? "autosetup.ui.errorTrainLengthOutOfRangeKnown" : "autosetup.ui.errorTrainLengthOutOfRange",
                    TrainControlUI.ROUTE_TRAIN_LENGTH_MAX)));

                units = askForWholeLength(question, TRAINS_TITLE, prefill);
            }

            if (units == null) break;

            if (units > 0) door.applyTrainLength(train, units);
        }

        selection.clear();

        refresh();
    }

    /**
     * Whether a train length typed into the walk is one the railway takes: 1 to the locomotive menu's maximum.
     *
     * @param units what was typed
     * @return true when it is a length
     */
    public static boolean acceptsATrainLength(int units)
    {
        return units >= 1 && units <= TrainControlUI.ROUTE_TRAIN_LENGTH_MAX;
    }

    /**
     * @param train a locomotive's name
     * @return the square it stands on in this setup, or null when it stands nowhere
     */
    private TileKey squareOfTrain(String train)
    {
        if (session == null || train == null) return null;

        for (Map.Entry<TileKey, String> placed : session.placementsAutonomyWillWrite().entrySet())
        {
            if (train.equals(placed.getValue())) return placed.getKey();
        }

        return null;
    }

    /**
     * Where Mass Assign Train Lengths finds the trains to ask about, and where it writes each answer
     * (FR-094).
     *
     * Both halves live on the main window - the run list is the running layout's, and a length is written through
     * `TrainControlUI.applyTrainLength` - and this panel is also built with no window at all.  So the walk asks this
     * rather than the window, and without one there is simply nothing to ask about: the item greys and the walk says
     * every train has a length, which is what a railway with no running layout can honestly say.
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

    private TrainLengthDoor trainLengthDoor()
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
     * Puts a setup check notice on the system clipboard as the list shows it, without its indent: the one door both lists
     * of notices (this editor's and the autonomy setup's) copy through when a notice is clicked (MT-767).  The lists hold
     * plain text, so nothing is stripped: a station named "Yard <old>" is copied as it is named (RSA75-C3).  A clipboard
     * another program holds, or none at all, leaves it uncopied and says nothing: the click's own work goes on.
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
     * its own (MT-505).  Outlined, not selected (RLV9-C3): the bulk selection is what the next Segment Length or direction
     * is written to, and a click on a guard notice made it the station and its signal, so a length typed for any square
     * went to those two.
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
     * Whether a square is drawn outlined: the bulk selection, a clicked finding's squares, the first end of a path test,
     * the signals a menu item is acting on, or the square a test is waiting on.
     *
     * @param tile the square
     * @return whether it is outlined
     */
    public boolean isOutlined(TileKey tile)
    {
        return selection.contains(tile) || noticeOutline.contains(tile) || tile.equals(testFrom)
            || highlightedSignals.contains(tile) || whyWaitsOn(tile);
    }

    private void outlineAndReveal(java.util.Collection<TileKey> squares)
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
     * Where the walk's last prompt was left, so the next one opens there - null at the start of a round.
     *
     * Adam, on MT-454: *"when the popup closes and reopens, make sure it remembers its location unless I reopen a new
     * mass assignment round from the right click menu.  right now, every skip press re centers it, which covers some of
     * the track diagram."*  Each prompt is a new dialog, and `JOptionPane.createDialog` centres every one.  Name
     * Everything's walk had the same habit and shares the fix.
     */
    private java.awt.Point walkPromptAt;

    private void placeWalkPrompt(javax.swing.JDialog dialog)
    {
        if (walkPromptAt != null) dialog.setLocation(walkPromptAt);
    }

    /** The walk dialogs' answers: OK, Skip, or stop the walk. */
    public static final int ANSWER_OK = 0;
    public static final int ANSWER_SKIP = 1;
    public static final int ANSWER_STOP = -1;

    /**
     * What an OK / Skip / Cancel walk dialog was answered with (MAL-B4).
     *
     * **Only the two buttons that say so are answers; everything else stops the walk.**  Both dialogs checked for null
     * and for Cancel, and fell through to reading the field for anything else - but JDK 8's option pane answers Escape
     * with `Integer.valueOf(CLOSED_OPTION)`, not null (`BasicOptionPaneUI`, inherited by FlatLaf).  So Escape wrote
     * whatever had been typed: a length in Mass Assign Lengths, a name in Name Everything.  Asked the other way round,
     * a value nobody anticipated can only ever stop.
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

    /**
     * @return the name the setup gives this square, or where it is when it has none
     */
    private String nameForPrompt(TileKey tile)
    {
        if (tile == null) return "";

        // A piece ends at a sensor or at a switch.  A switch has no name of its own in the setup, so it is named by
        // where it is (MAL-C3: the first version named the sensor a leg led to, where no train need rest).
        LayoutDiagramComponent component = session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        if (component != null && component.isSwitch())
        {
            return I18n.f("autosetup.ui.switchAtSquare", tile.getX() + "," + tile.getY());
        }

        // A CROSSING HAS NO NAME EITHER (SVA-C3).  A piece now ends at one as well as at a switch, and naming it by
        // its raw key put `main:18,10` in the middle of a sentence.
        if (session.sharedSquaresALengthRuleReads().contains(tile))
        {
            return I18n.f("autosetup.ui.crossingAtSquare", tile.getX() + "," + tile.getY());
        }

        String name = session.getStore().getPointName(tile);

        if (name != null && !name.trim().isEmpty()) return name;

        // AN UNNAMED SENSOR IS NAMED BY WHERE IT IS (AUS-C4), as a switch and a crossing already are.  The raw key
        // - the page name, a colon and two numbers - reads as a fault code in a sentence about a stretch of track.
        return I18n.f("autosetup.ui.sensorAtSquare", tile.getX() + "," + tile.getY());
    }

    /**
     * Asks for one number, with Skip beside OK - the Name Everything dialog, for a number.  Both number walks use it: a
     * stretch's whole length, and a station's maximum train length.
     *
     * @param question what to say above the field
     * @param titleKey the bundle key of the walk's name, which is the prompt's title
     * @return the number; -1 for Skip or a blank answer; null for Stop
     */
    private Integer askForWholeLength(String question, String titleKey)
    {
        return askForWholeLength(question, titleKey, "");
    }

    /**
     * The same, the box holding a number to start from (MT-566): the length a train already has, in the walk through
     * every train.  Selected when the box takes the keyboard, so a number typed replaces it rather than being added to
     * it; OK or Enter keeps it, as Skip does.
     *
     * @param question what to say above the field
     * @param titleKey the bundle key of the walk's name, which is the prompt's title
     * @param prefill what the box holds to start with, or empty
     * @return the number; -1 for Skip or a blank answer; null for Stop
     */
    private Integer askForWholeLength(String question, String titleKey, String prefill)
    {
        final javax.swing.JTextField field = digitsOnly(prefill == null ? "" : prefill);

        if (prefill != null && !prefill.isEmpty())
        {
            field.addFocusListener(new java.awt.event.FocusAdapter()
            {
                @Override
                public void focusGained(java.awt.event.FocusEvent e)
                {
                    field.selectAll();
                }
            });
        }

        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));

        panel.add(new JLabel(wrapped(question)), java.awt.BorderLayout.NORTH);
        panel.add(field, java.awt.BorderLayout.CENTER);

        final Object[] answers = { I18n.t("ui.ok"), I18n.t("autosetup.ui.btnSkipOne"), I18n.t("ui.cancel") };

        final JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
            // NO INITIAL VALUE, so the field has the keyboard focus (Adam, on MT-454: "make sure the entry field is
            // focused").  With OK as the initial value, JOptionPane gave the focus to that button when the dialog gained
            // it, and asking for the field earlier - when it was added, before the dialog was on screen - lost to that;
            // so what was typed went nowhere, and Enter on the empty field counted as Skip.  With none, the field is the
            // first thing in the prompt that can take the focus, and takes it.  Enter in the field still answers OK,
            // through the field's own listener.
            JOptionPane.YES_NO_CANCEL_OPTION, null, answers, null);

        final javax.swing.JDialog dialog = pane.createDialog(owner(), I18n.t(titleKey));

        placeWalkPrompt(dialog);

        field.addActionListener(e ->
        {
            pane.setValue(answers[0]);
            dialog.dispose();
        });

        // AND ASKED FOR BY NAME WHENEVER THE PROMPT HAS THE KEYBOARD (Adam, 2026-09-23, MT-474: *"make sure the text
        // field is focused by default"*).  With no initial value the field is the first thing that can take the focus
        // and takes it, and `ui.testTheLengthPromptHasTheKeyboard` sees it do so in the real editor window - but that
        // is the focus policy's choice rather than this prompt's, and it was not what he saw.  So the prompt says which
        // component it means: when the window opens, and every time it gains the keyboard back.
        dialog.addWindowListener(new java.awt.event.WindowAdapter()
        {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e)
            {
                field.requestFocusInWindow();
            }
        });

        dialog.addWindowFocusListener(new java.awt.event.WindowAdapter()
        {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e)
            {
                field.requestFocusInWindow();
            }
        });

        dialog.setVisible(true);

        walkPromptAt = dialog.getLocation();

        dialog.dispose();

        int answer = dialogAnswer(pane.getValue(), answers);

        if (answer == ANSWER_STOP) return null;
        if (answer == ANSWER_SKIP) return -1;

        String entered = field.getText().trim();

        if (entered.isEmpty()) return -1;

        try
        {
            return Integer.parseInt(entered);
        }
        catch (NumberFormatException cannotHappen)
        {
            // digitsOnly admits three digits and nothing else, so this is unreachable; a whole piece is at most 999.
            return -1;
        }
    }

    /**
     * Opens every one-way and closed piece of track both ways, after a warning (FR-108).  The warning, the Cancel it
     * offers and the message after are the clears' beside it, for the same reasons: in the editor Cancel puts the
     * directions back; on the track diagram's own menu it is saved at once.
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
     * Forgets every length on every page, after asking (FR-069).
     *
     * **What it costs is stated in the dialog, and what it costs depends on the surface** (AUS-C2).  In the editor
     * the lengths come back on Cancel - they are in the setup snapshot taken when the window opened (OB-223), which
     * `discardAutonomyWork` restores - so the confirmation says so.  On the track diagram's own menu, where this
     * panel is served with no page and every gesture saves at once, it says the other thing.  Two sentences chosen
     * by one question, which is the shape WK7-C2 gave the locomotive clear after it was found saying the wrong one.
     *
     * The empty case is said in the hint line rather than in a dialog: a dialog for "there was nothing
     * to clear" is a second press to dismiss an answer nobody needed.  Kept even though the item greys
     * itself on the same question, because the greying is the AFFORDANCE and this is the GUARD - and
     * OB-057 and OB-090 are both what happens when one of the two is missing.
     */
    private void clearAllTileLengths()
    {
        java.util.List<TileKey> measured = session.tilesWithALength();

        if (measured.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoNoTrackLengthsToClear"));

            return;
        }

        // WHAT CANCEL DOES DEPENDS ON WHICH SURFACE THIS IS (AUS-C2, Adam 2026-09-19: *"fix the sentence"*).
        //
        // The sentence said the clear could not be undone.  That was true before OB-223 and is not now: tile
        // lengths are in the setup snapshot the editor takes when it opens, and Cancel restores it - so in the
        // EDITOR the lengths come back.  The track diagram's own menu is this panel served with no page, and it
        // saves as it goes, so there the old sentence is the true one.  Chosen the way `clearLocomotivesWarning`
        // chooses, for the same reason and by the same test (WK7-C2).
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

        // Lengths are what the room rule measures with, and it asks the RUNNING layout - so the same
        // door `applyLength` uses, one gesture over.
        setupChanged();
    }

    /**
     * Takes the maximum train length off every station on every page, after asking.
     *
     * Adam, 2026-09-17: *"Add a right click menu open to clear all max station train lengths (grouped with the other
     * clear options)"*.  `clearAllHomes`' shape: the emptiness guard is kept although the item greys on the same
     * question, and the confirmation is the item's tooltip.
     */
    private void clearAllMaxTrainLengths()
    {
        java.util.List<TileKey> limited = session.tilesWithAMaxTrainLength();

        if (limited.isEmpty())
        {
            say(hint, I18n.t("autosetup.ui.infoNoMaxTrainLengthsToClear"));

            return;
        }

        // The same two sentences as the track-length clear beside it (AUS-C2).
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

        // ONE REDRAW, NOT TWO (SET-C3, corrected by SVA-C6).  `item()` refreshes after every action it runs, so
        // the `refresh()` that stood here was a second full pass over the page for one press; `setupChanged`
        // rebuilds the RUNNING layout and does not redraw this panel.  Its sibling `clearAllTileLengths` has
        // never had the extra one.
        //
        // The maximum is read by the RUNNING layout's Points, so it is told - what `promptNumber` does for one.
        setupChanged();
    }

    /**
     * Takes the home locomotive off every square that has one (R28-C1).
     *
     * Adam, 2026-09-02: **"that option should be added back in to the autonomy editor, with a
     * confirmation."**
     *
     * Through the SESSION rather than by writing the property here, because that is the door the
     * per-square menu uses - `clearEveryHome()` since `DY3-C5`, which writes through the same property
     * writer `setHome` does and re-derives the station index once instead of sixty-two times (this
     * paragraph named `setHome(tile, null)` until `REL-C7`, and the body has called the bulk door since
     * the day it was built) and the one that keeps the one-home-one-square sweep honest.  A
     * null home has nothing to sweep, so the two are the same act here - but a second way of clearing
     * a home is exactly how the two doors would come to disagree later.
     *
     * The confirmation is the 2.8.1 wording, which is still in all eight bundles and which says the
     * thing that matters: with no homes recorded, Return Home sends each locomotive back to wherever
     * it was standing when autonomy loaded.  Nothing is written to disk here - like every other
     * decision in this window it waits for Save, so a mistaken press is undone by Cancel.
     */
    private void clearAllHomes()
    {
        java.util.List<TileKey> homed = session.tilesWithAHome();

        // Nothing to do, said in the hint line rather than in a dialog: a dialog for "there was
        // nothing to clear" is a second press to dismiss an answer nobody needed.
        //
        // Kept even though the button greys itself on the same question (DY3-C6), because the greying
        // is the AFFORDANCE and this is the GUARD, and OB-057 and OB-090 are both what happens when
        // one of the two is missing.  The button's state is refreshed when the panel is, and a home
        // can go away between one refresh and the next.
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

        // THE SESSION'S BULK DOOR, not sixty-two single ones (DY3-C5).
        //
        // Every `setHome` re-derives the station index, which is a full builder construction on the
        // event thread - so this loop cost one per square, for the gesture that exists precisely
        // because clearing them one at a time is too many right-clicks.  Same property, same method,
        // one re-derive.
        int cleared = session.clearEveryHome();

        say(hint, I18n.f("autosetup.ui.infoHomesCleared", cleared));

        refresh();

        // MT-246'S OWN PROPERTY, IN BULK (VD10-B2).
        //
        // Without this the setup is saved and the running layout keeps every home, so
        // `triageReturnToHome` goes on offering Return Home and sends each train to a home the
        // operator has just cleared.  `clearAllPlacements`, its sibling three items along the
        // same menu, has ended in `placementChanged()` all along.
        setupChanged();
    }

    /**
     * The sentence a bulk clear will show, for the surface this panel is serving (OP2-C2).
     *
     * **One builder, so the pair cannot drift.**  `clearLocomotivesWarning` below states the rule for its own
     * pair - *"used by the confirmation AND by the menu item's tooltip, so the warning cannot be shown in one
     * place and not the other"* - and AUS-C2 built the track-length and maximum pairs by copying the choice
     * instead, into four places for two menu items.  Nothing in that shape stops a fifth copy, or a pair of
     * keys swapped in one of them; the comment above the track-length tooltip already records that this pair
     * had drifted once.
     *
     * The choice itself is the one both sentences turn on: a panel serving the track diagram has no page and
     * saves as it goes, so the clear is immediate and says so; inside the editor it is undone by Cancel.
     *
     * **And the tooltip's short form through it too (FR-112):** a `tip` key with its own "AtOnce" sibling, so the menu
     * item says in brief what the dialog will say in full, and cannot promise a Cancel the dialog does not.
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
     * What the bulk clear says before it takes every locomotive off (OB-194).
     *
     * Adam, on MT-311, 2026-09-08: **"bug: clearning locomotives in the autonomy editor cannot be
     * undone by a cancel.  Make this clear in the popup."**  His ruling of 2026-09-09 was for the
     * warning rather than a real undo: *"Warning if using the bulk tool."*
     *
     * **What Cancel does, which has changed twice.**  MT-311's expectation read *"neither writes to disk -
     * Cancel puts everything back"*.  After OB-183 it did not - placements are carried across a rebuild
     * (`TrainControlUI.putTheTrainsBack`), and the old discard re-read a file the per-gesture save had already
     * written without them - so this warned that Cancel would not bring them back (OB-194).  Since OB-223
     * Cancel restores the setup as the editor opened it, placements included, and the rebuild when the editor
     * closes regenerates them from it: `putTheTrainsBack` only moves trains that were standing, which the
     * cleared ones are not.  So they come back, and the warning says so (WK7-C2, 2026-09-14);
     * `regression.testCancelUndoesAutonomyEdits.testCancelPutsBackTheLocomotivesABulkClearTook` runs it.
     *
     * **It names them**, because a bulk gesture is an answer about every one of them at once and a
     * count is not something anybody can check.  The same reason `placementChanged` is given the list
     * it lifted rather than being left to work it out afterwards.
     *
     * One builder, used by the confirmation, so the warning cannot be shown in one place and not the
     * other; the menu item's tooltip is its short form, `clearLocomotivesTip`, chosen the same way (FR-112).
     *
     * **On the track diagram's own menu there is no Cancel (WKV-B1).**  That menu is this panel served with no
     * page, its changes are saved as they are made, and it is not offered at all while an editor is open
     * (`TrainControlUI.buildAutonomyTileMenu`) - so no Cancel can reach a clear made there, and the warning says
     * it is saved at once instead.  `regression.testTheBulkClearSaysWhatCancelDoes` asks both doors.
     *
     * @return the warning, already translated
     */
    public String clearLocomotivesWarning()
    {
        if (session == null) return I18n.t("autosetup.ui.infoNoLocomotivesToClear");

        // THE SET THE CLEAR ACTUALLY WALKS, COUNTED AND NAMED FROM ONE PLACE (GSE-B2).
        //
        // This sentence held three populations: the names came from the panel's own
        // `placedLocomotives()`, the count from `session.tilesWithALocomotive()` - every page,
        // excluded ones included - and the clear itself walks `placementsAutonomyWillWrite()`, which
        // skips them.  With one page excluded the menu offered "Clear All Locomotives (9)" and its
        // confirmation said "12 locomotives" over a list of nine names.  The label and the guard were
        // swept onto the right question by `SEV-C3`; this one was not, and the comment above it says
        // in the past tense that it was.
        //
        // Sorted, because a map's iteration order is not an order and this list is read (VD15-C2).
        java.util.Map<TileKey, String> clearing = session.placementsAutonomyWillWrite();

        // ONE UNIT IN BOTH HALVES OF THE SENTENCE (VD17-C7).  The map is keyed by SQUARE and the
        // sentence is about LOCOMOTIVES, so counting the map while naming its values counted one
        // thing and listed another - and the sentence reads "N locomotives: a, b, c".
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
     * The menu item's tooltip: `clearLocomotivesWarning` in short - how many, and what Cancel does, chosen as that sentence
     * is (FR-112; Adam, 2026-10-05: *"make them be more concise so they don't cover up other things"*).  The names are the
     * dialog's, which shows them before anything is cleared.
     *
     * Counted as the warning counts: locomotives, not the squares they stand on (VD17-C7).
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
     * Takes every locomotive off the setup (MT-257 item 1).
     *
     * Adam: **"Yes, I want it back."**  2.8.1 had it on the graph window's own menu, and it went with
     * the window - `git show master:.../GraphRightClickGeneralMenu.java:111`.
     *
     * That version walked the RUNNING layout and called `moveLocomotive(null, ...)`, skipping
     * reversing points and non-destinations.  This one is the editor's, so it goes through
     * `placeLocomotive(tile, null)` - the same door the per-square "Remove" item uses, which also
     * clears the facing the placement left behind.  No skipping: a placement the editor can show is a
     * placement the editor can clear, and the 2.8.1 filter was about which squares the running model
     * would accept a MOVE to rather than about which ones may be emptied.
     *
     * **Cancel puts them back** (OB-223; WK7-C2): the discard restores the setup as the editor opened it and
     * the rebuild regenerates the placements from it.  `clearLocomotivesWarning` says so before they answer,
     * and why it once said the opposite.
     */
    private void clearAllPlacements()
    {
        // THE SET `clearEveryPlacement` ACTUALLY WALKS (SEV-C3, swept from its sibling).
        //
        // The reviewer found the home door counting `tilesWithALocomotive()` - every page - while the
        // bulk write walked `placedLocomotives()`, which skips pages excluded from autonomy. This door
        // had the same split and was not reported: `clearEveryPlacement` walks `placedLocomotives()`
        // too. So on a layout with an excluded page this dialog named a number the clear would not
        // reach.
        //
        // `fix-one-site-sweep-the-siblings`: the real defect in this file is usually beside a comment
        // describing the same fix somewhere else.
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

        // THE SESSION'S BULK DOOR, not one per square (REL-C4).
        //
        // Clearing one placement writes two properties - the placement and the facing that described
        // the train standing there - and each write re-derives the station index, which is a full
        // builder construction on the event thread.  So this cost 2N of them, for the gesture that
        // exists precisely because doing it one at a time is too many.  Same writer, one re-derive.
        // WHOSE PLACEMENTS THESE ARE, read before the clear takes them away (D2-A1).  Clearing is an
        // answer about every one of them, and an unnamed clear is undone by the rebuild that carries
        // it - the same defect as the single-square Remove, in the bulk door.
        java.util.List<String> lifted = placedLocomotives();

        int cleared = session.clearEveryPlacement();

        say(hint, I18n.f("autosetup.ui.infoLocomotivesCleared", cleared));

        placementChanged(lifted);
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
            // NO INITIAL VALUE, so the field has the keyboard focus (Adam, on MT-454: "make sure the entry field is
            // focused").  With OK as the initial value, JOptionPane gave the focus to that button when the dialog gained
            // it, and asking for the field earlier - when it was added, before the dialog was on screen - lost to that;
            // so what was typed went nowhere, and Enter on the empty field counted as Skip.  With none, the field is the
            // first thing in the prompt that can take the focus, and takes it.  Enter in the field still answers OK,
            // through the field's own listener.
            JOptionPane.YES_NO_CANCEL_OPTION, null, answers, null);

        final javax.swing.JDialog dialog = pane.createDialog(owner(),
            I18n.t("autosetup.ui.btnNameEverything"));

        placeWalkPrompt(dialog);

        // Enter in the box is OK, which showInputDialog gave for nothing and a dialog built by hand
        // has to be told
        field.addActionListener(e ->
        {
            pane.setValue(answers[0]);

            dialog.dispose();
        });

        dialog.setVisible(true);

        walkPromptAt = dialog.getLocation();

        dialog.dispose();

        // Closed with the window button or Escape, which is the same as changing your mind about the walk (MAL-B4).
        int answer = dialogAnswer(pane.getValue(), answers);

        if (answer == ANSWER_STOP) return null;

        if (answer == ANSWER_SKIP) return "";

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
     * @return whether it was written.  The caller closes the editor on the strength of this: a save
     *         that told the user it had failed used to close the window anyway, which reads as
     *         success and is the opposite of what the dialog just said
     */
    public boolean save()
    {
        try
        {
            // Shown through the one display every door uses now (DR-B10).  This was the only
            // door that showed it at all; the other five threw the answer away, including the refusal
            // that says a page's settings were left alone because its file is not loaded.
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
