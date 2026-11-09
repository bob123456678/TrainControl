package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentListener;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Locomotive.decoderType;
import org.traincontrol.gui.widgets.RowIcons;
import org.traincontrol.gui.widgets.TabIcons;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * The Locomotive Settings window (FR-048): one non-modal window, reused, for a locomotive's name, address and picture,
 * its functions, its notes, its multi-unit and what autonomy needs of it; and Add Locomotive's (FR-179), the first tab
 * alone with Add for Save.  Everything waits for Save except the picture's overlay icons, which apply at once.  A
 * locomotive that cannot be edited now cannot be saved (see {@link LocomotiveSettings}).
 */
public class LocomotiveSettingsFrame extends javax.swing.JFrame
{
    // A static block, not the constructor: JFrame's constructor creates the root pane before any constructor body here.
    static
    {
        if (!java.awt.GraphicsEnvironment.isHeadless()) TrainControlUI.installLookAndFeel();
    }

    /** The tabs, in order. */
    public enum Tab
    {
        LOCOMOTIVE, FUNCTIONS, NOTES, MULTI_UNIT, AUTONOMY
    }

    private static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 12);

    /** The margin round every tab's content and the footer: the route editor's. */
    private static final int PAD = 10;

    private static final Color STRIP_BACKGROUND = new Color(255, 243, 205);
    private static final Color STRIP_TEXT = new Color(102, 77, 3);

    /** The first tab's picture box: wide like the application's locomotive pictures, taller to balance the fields. */
    private static final int PICTURE_WIDTH = 278;
    private static final int PICTURE_HEIGHT = 150;

    /** What a test answers the unsaved-changes question with: 0 Save, 1 Discard, 2 Cancel, or null to ask. */
    public static Integer unsavedAnswerForTest;

    private final TrainControlUI main;
    private Locomotive loc;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel content = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new BorderLayout());
    private final JLabel strip = new JLabel();

    /** Under the lock strip, when the warning triangle was clicked: who else has this address and decoder (OB-417). */
    private final JPanel sharedStrip = new JPanel(new BorderLayout());
    private final JLabel sharedText = new JLabel();
    private java.util.List<String> sharers = java.util.Collections.emptyList();
    private boolean sharedOpen;

    private final JButton save = footerButton(I18n.t("loc.ui.settingsSave"), "settings.save");
    private final JButton cancel = footerButton(I18n.t("ui.cancel"), "settings.cancel");

    private LocomotiveTab locomotiveTab;
    private LocomotiveFunctionsTab functionsTab;
    private NotesTab notesTab;
    private MultiUnitTab multiUnitTab;
    private AutonomyTab autonomyTab;
    private boolean blocked;

    /** Whether this opening of the window has written the duplicate report to the log. */
    private boolean reported;

    /** Whether the window is Add Locomotive's: no locomotive yet, the first tab alone and Add for Save. */
    private boolean adding;

    /** The least width that keeps Basic Settings, the tab strip and the Multi-Unit list whole, as `fit` measured it. */
    private int least;

    /** Asks the rule again every second while the window is open: a run starting or stopping raises no single event. */
    private final javax.swing.Timer watch = new javax.swing.Timer(1000, e -> refresh());

    LocomotiveSettingsFrame(TrainControlUI main)
    {
        this.main = main;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                closeAsking();
            }
        });

        TrainControlUI.applyWindowIcon(this);

        strip.setName("settings.strip");
        sharedStrip.setName("settings.sharedStrip");
        sharedText.setName("settings.sharedText");

        for (JLabel line : new JLabel[] { strip, sharedText })
        {
            line.setOpaque(true);
            line.setBackground(STRIP_BACKGROUND);
            line.setForeground(STRIP_TEXT);
        }

        strip.setVisible(false);
        sharedStrip.setVisible(false);
        sharedStrip.setBackground(STRIP_BACKGROUND);
        strip.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        sharedText.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JButton closeShared = new JButton("✕");

        closeShared.setName("settings.sharedClose");
        closeShared.setToolTipText(I18n.t("autosetup.ui.stationClose"));
        closeShared.setFocusable(false);
        closeShared.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        closeShared.setForeground(STRIP_TEXT);
        closeShared.putClientProperty("JButton.buttonType", "toolBarButton");
        closeShared.addActionListener(e -> showShared(false));

        sharedStrip.add(sharedText, BorderLayout.CENTER);
        sharedStrip.add(closeShared, BorderLayout.EAST);

        tabs.setName("settings.tabs");

        // The application's compact tabs show the title of the chosen tab only; these keep every title beside its icon
        tabs.putClientProperty("JTabbedPane.tabWidthMode", "preferred");

        save.addActionListener(e -> save());
        cancel.addActionListener(e -> close());

        // Save, then Cancel, at the bottom right, spaced as the route editor's are
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

        // What Save does for every tab, as a mark beside it
        buttons.add(InfoMark.label(I18n.t("loc.ui.settingsKeptHere")));
        buttons.add(save);
        buttons.add(cancel);

        footer.setBorder(BorderFactory.createEmptyBorder(6, PAD, PAD, PAD));
        footer.add(buttons, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout());

        top.add(strip, BorderLayout.NORTH);
        top.add(sharedStrip, BorderLayout.CENTER);

        content.add(top, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);

        setContentPane(content);

        // Escape is Cancel
        getRootPane().registerKeyboardAction(e -> closeAsking(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        setAlwaysOnTop(main.isAlwaysOnTop());
    }

    private static JButton footerButton(String text, String name)
    {
        JButton b = new JButton(text);

        b.setFont(BUTTON_FONT);
        b.setName(name);

        return b;
    }

    /**
     * Points the window at a locomotive and shows a tab.  The same locomotive keeps what was typed; another one asks
     * first when there is something typed.
     *
     * @param function the function to show on the Functions tab, or null
     * @return false when the operator canceled the question about unsaved changes
     */
    boolean aim(Locomotive l, Tab tab, Integer function)
    {
        if ((adding || loc != null && loc != l) && isDirty() && !settleUnsaved()) return false;

        if (adding || loc != l)
        {
            adding = false;
            loc = l;

            build();
        }

        tabs.setSelectedIndex(tab.ordinal());

        watch.start();

        if (function != null && tab == Tab.FUNCTIONS) functionsTab.select(function);

        refresh();
        reportDuplicatesOnce();

        return true;
    }

    /**
     * Points the window at a locomotive still to be made, with blank fields; asks first when there is something typed.
     *
     * @return false when the operator canceled the question about unsaved changes
     */
    boolean aimAtANewLocomotive()
    {
        if (isDirty() && !settleUnsaved()) return false;

        loc = null;
        adding = true;

        build();

        watch.start();

        refresh();
        reportDuplicatesOnce();

        return true;
    }

    /** The first check of an opening also writes the whole-database duplicate report to the log. */
    private void reportDuplicatesOnce()
    {
        if (reported) return;

        reported = true;
        main.locomotiveActions.reportDuplicates();
    }

    /** @return the locomotive the window is showing, or null */
    public Locomotive getLocomotive()
    {
        return loc;
    }

    /** Builds the tabs afresh from the locomotive, or the first tab alone for one still to be made. */
    private void build()
    {
        tabs.removeAll();

        sharers = java.util.Collections.emptyList();
        sharedOpen = false;
        sharedStrip.setVisible(false);

        locomotiveTab = new LocomotiveTab();
        locomotiveTab.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));

        save.setText(I18n.t(adding ? "ui.add" : "loc.ui.settingsSave"));

        // One width, so the two buttons share an edge, measured again as Save's text changes
        save.setPreferredSize(null);
        cancel.setPreferredSize(null);

        Dimension both = new Dimension(Math.max(save.getPreferredSize().width, cancel.getPreferredSize().width),
            save.getPreferredSize().height);

        save.setPreferredSize(both);
        cancel.setPreferredSize(both);

        // Add shows the first tab's content with no tab strip: one tab alone only takes room
        if (adding)
        {
            showInTheMiddle(locomotiveTab);
            setTitle(I18n.t("loc.ui.addLocomotive"));
            showStrip(false);
            fit();

            return;
        }

        showInTheMiddle(tabs);
        tabs.addTab(I18n.t("loc.ui.settingsTabLocomotive"), TabIcons.locomotive(), locomotiveTab);

        functionsTab = new LocomotiveFunctionsTab(main, loc, this::changed);
        notesTab = new NotesTab();
        multiUnitTab = new MultiUnitTab();
        autonomyTab = new AutonomyTab();

        for (JPanel tab : new JPanel[] { functionsTab, notesTab, multiUnitTab, autonomyTab })
        {
            tab.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
        }

        // The other tabs' controls carry a 4 pixel inset of their own: the table and its buttons line up with them
        functionsTab.setBorder(BorderFactory.createEmptyBorder(PAD + 4, PAD + 4, PAD + 4, PAD + 4));

        tabs.addTab(I18n.t("loc.ui.settingsTabFunctions"), TabIcons.functions(), functionsTab);
        tabs.addTab(I18n.t("loc.ui.settingsTabNotes"), TabIcons.notes(), notesTab);
        tabs.addTab(I18n.t("loc.ui.settingsTabMultiUnit"), TabIcons.multiUnit(), multiUnitTab);
        tabs.addTab(I18n.t("loc.ui.settingsTabAutonomy"), TabIcons.autonomy(), autonomyTab);

        setTitle(I18n.f("loc.ui.settingsTitle", loc.getName()));

        // The strip is up before the window is sized, so a locked window opens with its tabs whole
        String reason = LocomotiveSettings.stripFor(main, loc);

        if (reason != null) strip.setText(reason);

        showStrip(reason != null);

        fit();
    }

    /** Puts what the window shows between the strips and the footer: the tab strip, or Add's one page. */
    private void showInTheMiddle(java.awt.Component what)
    {
        java.awt.Component now = ((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.CENTER);

        if (now == what) return;

        if (now != null) content.remove(now);

        content.add(what, BorderLayout.CENTER);
        content.revalidate();
    }

    private void showStrip(boolean up)
    {
        strip.setVisible(up);
    }

    /** What the tab found: every other locomotive at the typed address and decoder.  None puts the strip away. */
    private void setSharers(java.util.List<String> names)
    {
        sharers = names;

        if (names.isEmpty()) sharedOpen = false;

        showShared(sharedOpen);
    }

    /** Puts the shared-address strip up or away, and grows or shrinks the window by its height. */
    private void showShared(boolean up)
    {
        sharedOpen = up && !sharers.isEmpty();

        if (sharedOpen)
        {
            sharedText.setText("<html><div style='width:360px'>"
                + I18n.f("loc.ui.settingsAlsoUsedBy", String.join(", ", sharers)).replace("&", "&amp;").replace("<", "&lt;")
                + "</div></html>");
        }

        if (sharedStrip.isVisible() == sharedOpen && !sharedOpen) return;

        int before = sharedStrip.isVisible() ? sharedStrip.getHeight() : 0;

        sharedStrip.setVisible(sharedOpen);
        getContentPane().validate();

        int after = sharedOpen ? sharedStrip.getPreferredSize().height : 0;

        if (isShowing() && (getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) == 0 && before != after)
        {
            setSize(getWidth(), getHeight() + after - before);
        }
    }

    private void fit()
    {
        boolean first = !isVisible();

        // The last build's least width would hold the window wider than this one opens
        setMinimumSize(null);
        pack();

        // Measured here, where the tabs are built, and not on the watch's every tick: measuring resizes the strip, and
        // lays every tab out as wide as it, so the strip goes last.  Basic Settings at its least keeps its labels whole.
        least = Math.max(locomotiveTab.getMinimumSize().width + getWidth() - locomotiveTab.getWidth(),
            footer.getPreferredSize().width + getWidth() - footer.getWidth());

        if (!adding)
        {
            if (multiUnitTab.members != null) least = Math.max(least, getWidth() + multiUnitTab.members.shortfall());

            least = Math.max(least, tabsOnOneRow() + getWidth() - tabs.getWidth());
        }

        keepTheRowsWhole();

        if (first) setLocationRelativeTo(main);
    }

    /**
     * The window is never narrower than Basic Settings with its labels whole, the footer, its tabs on one row, the
     * Multi-Unit list's columns beside its scroll bar, or the Functions tab's button row, so its two groups cannot
     * overlap.  All are measured, so this holds in every language and at every scale.
     */
    private void keepTheRowsWhole()
    {
        int buttons = adding ? 0 : functionsTab.buttonRowWidth() + getWidth() - functionsTab.getWidth();

        setMinimumSize(new java.awt.Dimension(Math.max(buttons, least), 0));
    }

    /** How wide the tab strip is with every tab on one row: it is laid out once wide enough for them, then put back. */
    private int tabsOnOneRow()
    {
        java.awt.Rectangle was = tabs.getBounds();

        tabs.setSize(Short.MAX_VALUE, Math.max(was.height, 1));
        tabs.doLayout();

        java.awt.Rectangle first = tabs.getBoundsAt(0);
        java.awt.Rectangle last = tabs.getBoundsAt(tabs.getTabCount() - 1);

        tabs.setBounds(was);
        tabs.doLayout();

        // The strip's margin on the right is the one on its left
        return last.x + last.width + first.x;
    }

    /** Re-reads the moving rule and the locomotive: the window is told whenever either may have changed. */
    void refresh()
    {
        if (adding)
        {
            changed();

            return;
        }

        if (loc == null) return;

        String reason = LocomotiveSettings.stripFor(main, loc);

        blocked = reason != null;

        if (blocked) strip.setText(reason);

        if (strip.isVisible() != blocked)
        {
            showStrip(blocked);

            // The strip takes its room from the tabs: the window is not resized under the operator
            getContentPane().revalidate();
        }

        locomotiveTab.setBlocked(blocked);
        locomotiveTab.refreshSync();
        locomotiveTab.refreshPicture();
        functionsTab.setBlocked(blocked);
        functionsTab.refresh();
        keepTheRowsWhole();
        changed();
    }

    /** Whether the window is showing anything the operator has typed and not saved. */
    boolean isDirty()
    {
        if (adding) return locomotiveTab.isDirty();

        return locomotiveTab != null && (locomotiveTab.isDirty() || functionsTab.isDirty() || notesTab.panel.isDirty()
            || multiUnitTab.isDirty() || autonomyTab.isDirty());
    }

    private void changed()
    {
        // Add is always pressable: a blank name is refused with today's message
        save.setEnabled(adding || isDirty() && !blocked);
    }

    /** The X and Escape: closes, asking first what to do with typing that is not saved. */
    void closeAsking()
    {
        if (isDirty() && !settleUnsaved()) return;

        close();
    }

    /** Closes the window, throwing away what was typed. */
    void close()
    {
        loc = null;
        adding = false;
        dispose();
    }

    @Override
    public void dispose()
    {
        watch.stop();

        reported = false;

        super.dispose();
    }

    /**
     * The application's exit asks this: whether a window holding unsaved work may be closed.
     *
     * @return true when the exit may carry on
     */
    boolean maySettleBeforeExit()
    {
        if (!isDirty()) return true;

        if ((getExtendedState() & java.awt.Frame.ICONIFIED) != 0)
        {
            setExtendedState(getExtendedState() & ~java.awt.Frame.ICONIFIED);
        }

        toFront();

        return settleUnsaved();
    }

    /** Asks what to do with typing that is not saved; true when the caller may go on. */
    private boolean settleUnsaved()
    {
        int answer;

        if (unsavedAnswerForTest != null)
        {
            answer = unsavedAnswerForTest;
        }
        else
        {
            Object[] options = { I18n.t("loc.ui.settingsSave"), I18n.t("loc.ui.settingsDiscard"), I18n.t("ui.cancel") };

            answer = JOptionPane.showOptionDialog(this, I18n.f("loc.ui.settingsUnsaved",
                adding ? I18n.t("loc.ui.addLocomotive") : loc.getName()),
                I18n.t("route.ui.titleDiscardChanges"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[2]);
        }

        if (answer == 0) return write();

        return answer == 1;
    }

    /** Save: writes, and closes when it went through.  Add stays open, on the locomotive it made. */
    void save()
    {
        if (adding)
        {
            add();

            return;
        }

        if (write()) close();
    }

    /** Makes the locomotive typed on the first tab, and shows it with all its tabs. */
    private boolean add()
    {
        Locomotive made = main.locomotiveActions.addLocomotive(this, locomotiveTab.name.getText(),
            locomotiveTab.address.getText(), (decoderType) locomotiveTab.decoder.getSelectedItem());

        if (made == null) return false;

        adding = false;

        return aim(made, Tab.LOCOMOTIVE, null);
    }

    /**
     * Writes everything typed onto the locomotive, with today's refusals asked once and in today's order: the moving
     * rule, then autonomy and routes for what they refuse, then the name before the address.  A refusal leaves the
     * window as it was.
     *
     * @return true when everything went through
     */
    boolean write()
    {
        if (adding) return add();

        if (loc == null) return false;

        stopEditing();

        if (LocomotiveSettings.refuseIfNotEditable(main, loc, this))
        {
            refresh();
            return false;
        }

        // The Autonomy tab writes into the setup, which no edit touches during a run
        if (autonomyTab.isDirty() && main.refuseWhileARunHoldsTheSetup(this)) return false;

        boolean identity = locomotiveTab.identityChanged();
        boolean members = multiUnitTab.isDirty();

        if (identity && main.refuseWhileARouteDrivesIt(this, loc.getName())) return false;

        if (members && main.locomotiveActions.refuseMultiUnitEdit(this, loc, multiUnitTab.chosen().keySet())) return false;

        // Only what the operator changed from the loaded values: another door may have changed the rest since
        if (identity && !main.locomotiveActions.writeNameAndAddress(this, loc,
            locomotiveTab.nameChanged() ? locomotiveTab.name.getText() : null,
            locomotiveTab.addressText(),
            (decoderType) locomotiveTab.decoder.getSelectedItem()))
        {
            return false;
        }

        setTitle(I18n.f("loc.ui.settingsTitle", loc.getName()));

        if (locomotiveTab.speedChanged())
        {
            loc.setPreferredSpeed(locomotiveTab.speedValue());

            main.recordPresetSpeedInTheSetup(loc);
        }

        if (functionsTab.isDirty())
        {
            boolean reset = functionsTab.resets();

            functionsTab.apply();

            main.repaintLoc(true, null);

            if (reset) new Thread(() -> main.syncWithCS2()).start();
        }

        if (notesTab.panel.isDirty()) notesTab.panel.apply(loc);

        if (autonomyTab.isDirty()) autonomyTab.apply();

        if (members && !main.locomotiveActions.applyLinkedLocomotives(this, loc, multiUnitTab.chosen())) return false;

        return true;
    }

    /**
     * The Sync button: syncs the locomotive with the Central Station at once, not on Save.  Afterwards the whole window
     * is reloaded from the locomotive, overwriting anything typed.
     */
    private void syncNow()
    {
        final Locomotive target = loc;

        if (target == null) return;

        main.syncLocomotive(target, () -> afterSync(target));
    }

    private void afterSync(Locomotive synced)
    {
        if (loc != synced || adding || !isDisplayable()) return;

        int shown = tabs.getSelectedIndex();
        Dimension size = getSize();

        build();

        tabs.setSelectedIndex(shown);
        setSize(size);
        refresh();
    }

    private void stopEditing()
    {
        functionsTab.commitEdit();
    }

    // ------------------------------------------------------------------------------------------------------------

    /** A field's label: black, under a blue section heading. */
    private static JLabel label(String text)
    {
        return UiStyle.label(text);
    }

    /** A number spinner whose typed value counts as it is typed, as a text box's does, not only on Enter or leaving it. */
    private static JSpinner typedAtOnce(JSpinner spinner)
    {
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor)
        {
            javax.swing.JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();

            if (field.getFormatter() instanceof javax.swing.text.DefaultFormatter)
            {
                ((javax.swing.text.DefaultFormatter) field.getFormatter()).setCommitsOnValidEdit(true);
            }
        }

        return spinner;
    }

    /**
     * The first tab: picture, name, decoder, address and preset speed, with the trash can and Check Duplicates.  For a
     * locomotive still to be made, the name, the decoders Add Locomotive offers and the address alone.
     */
    private final class LocomotiveTab extends JPanel
    {
        final JTextField name = new JTextField(16);
        final JComboBox<decoderType> decoder = new JComboBox<>(adding
            ? new decoderType[] { decoderType.MFX, decoderType.MM2, decoderType.DCC } : decoderType.values());
        final JTextField address = new JTextField(16);
        final JSpinner speed = typedAtOnce(new JSpinner(new SpinnerNumberModel(0, 0, 100, 1)));
        final JLabel sameAddress = new JLabel();
        final JButton sync = new JButton(I18n.t("loc.ui.menuSyncCentralStation"));
        final JLabel picture = new JLabel("", JLabel.CENTER);
        final JButton trash = new JButton(RowIcons.trash(18));
        final JLabel wrench = new JLabel(new LocIconActions.WrenchMark());
        final JLabel crop = new JLabel(new LocIconActions.CropMark());
        final JLabel revert = new JLabel(new LocIconActions.RevertMark());

        private final String loadedName = adding ? "" : loc.getName();
        private final String loadedAddress = adding ? "" : Integer.toString(loc.getAddress());

        // DCC is what Add Locomotive started on
        private final decoderType loadedDecoder = adding ? decoderType.DCC : loc.getDecoderType();
        private final int loadedSpeed = adding ? 0 : loc.getPreferredSpeed();
        private String shownPicture = "";

        LocomotiveTab()
        {
            super(new GridBagLayout());

            name.setText(loadedName);
            address.setText(loadedAddress);
            decoder.setSelectedItem(loadedDecoder);
            speed.setValue(loadedSpeed);

            // The decoder shows each type's user label, not the enum's name
            decoder.setRenderer(new javax.swing.DefaultListCellRenderer()
            {
                @Override
                public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                    boolean selected, boolean focus)
                {
                    return super.getListCellRendererComponent(list,
                        value == decoderType.MULTI_UNIT ? I18n.t("loc.multiUnit") : value, index, selected, focus);
                }
            });

            name.setName("settings.name");
            sameAddress.setName("settings.sameAddress");
            decoder.setName("settings.decoder");
            address.setName("settings.address");
            speed.setName("settings.speed");
            trash.setName("settings.trash");
            wrench.setName("settings.wrench");
            crop.setName("settings.crop");
            revert.setName("settings.revert");
            picture.setName("settings.picture");

            // Always the icon's size, so the row does not change width when the triangle appears
            sameAddress.setPreferredSize(new Dimension(RowIcons.warning(16).getIconWidth() + 4,
                RowIcons.warning(16).getIconHeight()));
            sameAddress.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseReleased(MouseEvent e)
                {
                    if (sameAddress.getIcon() != null) showShared(true);
                }
            });

            sync.setName("settings.sync");
            sync.setFont(BUTTON_FONT);
            sync.setFocusable(false);
            sync.addActionListener(e -> syncNow());

            // The spinner is as tall as the fields above it
            speed.setPreferredSize(new Dimension(speed.getPreferredSize().width, name.getPreferredSize().height));

            DocumentListener tell = Typing.tell(this::typed);

            name.getDocument().addDocumentListener(tell);
            address.getDocument().addDocumentListener(tell);

            // The same filter and limit as Add Locomotive's address box
            address.addKeyListener(new java.awt.event.KeyAdapter()
            {
                @Override
                public void keyReleased(KeyEvent e)
                {
                    TrainControlUI.validateInt(e, true);
                    TrainControlUI.limitLength(e, 6);
                }
            });
            decoder.addActionListener(e -> typed());
            speed.addChangeListener(e -> typed());

            // Add Locomotive's limit on a new name; a rename is refused at Save instead
            if (adding)
            {
                name.addKeyListener(new java.awt.event.KeyAdapter()
                {
                    @Override
                    public void keyReleased(KeyEvent e)
                    {
                        TrainControlUI.limitLength(e, TrainControlUI.MAX_LOC_NAME_DATABASE);
                    }
                });
            }

            // The picture and the marks that change it, which apply at once.
            picture.setPreferredSize(new Dimension(PICTURE_WIDTH, PICTURE_HEIGHT));
            picture.setBorder(BorderFactory.createLineBorder(new Color(204, 204, 204)));
            picture.setLayout(new BorderLayout());

            JPanel corner = new JPanel(new BorderLayout());
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));

            corner.setOpaque(false);
            left.setOpaque(false);
            left.add(wrench);
            left.add(revert);
            corner.add(left, BorderLayout.WEST);
            corner.add(crop, BorderLayout.EAST);
            picture.add(corner, BorderLayout.NORTH);

            mark(wrench, noted(I18n.t("loc.ui.menuSetLocalLocomotiveIcon")), e -> main.setLocIcon(loc, e));
            mark(crop, noted(I18n.t("loc.ui.tooltip.recrop")), e -> main.recropLocIcon(loc, LocomotiveSettingsFrame.this));
            mark(revert, noted(I18n.t("loc.ui.menuClearLocalLocomotiveIcon")), e -> main.clearLocIcon(loc));

            trash.setToolTipText(I18n.t("loc.ui.menuDeleteFromDatabase"));
            trash.setFocusable(false);
            trash.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            trash.setBorderPainted(false);
            trash.setContentAreaFilled(false);
            trash.addActionListener(e -> delete());

            // The fields are at the tab's left edge under their heading, the picture at its right under its own, level
            // with Name; the divider is midway between Check Duplicates and the next heading, the trash can at the lower left
            final Insets edge = new Insets(4, 4, 4, 6);
            final Insets inner = new Insets(4, 4, 4, 6);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = inner;
            gbc.anchor = GridBagConstraints.WEST;

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            gbc.insets = edge;
            add(UiStyle.heading(I18n.t("loc.ui.settingsNameAndAddress")), gbc);
            gbc.gridwidth = 1;

            if (!adding)
            {
                // The picture's heading starts where the picture does
                gbc.gridx = 3;
                gbc.insets = new Insets(4, 6, 4, 4);
                gbc.anchor = GridBagConstraints.NORTHWEST;
                add(UiStyle.heading(I18n.t("loc.ui.settingsLocomotiveIcon")), gbc);

                // Sync is a locomotive's own (Add has none yet): under the picture, at its left edge; the picture
                // takes the column's width
                JPanel syncRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                syncRow.setOpaque(false);
                syncRow.add(sync);
                syncRow.add(javax.swing.Box.createHorizontalStrut(4));
                syncRow.add(InfoMark.label(I18n.t("loc.ui.settingsSyncHint")));

                JPanel pictureColumn = new JPanel(new BorderLayout(0, 6));
                pictureColumn.setOpaque(false);
                pictureColumn.setName("settings.pictureColumn");
                pictureColumn.add(picture, BorderLayout.CENTER);
                pictureColumn.add(syncRow, BorderLayout.SOUTH);

                gbc.gridy = 1;
                gbc.gridheight = 9;
                add(pictureColumn, gbc);

                gbc.gridheight = 1;
                gbc.anchor = GridBagConstraints.WEST;
            }

            JComponent[] fields = { name, decoder, address };
            String[] labels = { "ui.name", "loc.ui.decoderType", "layout.ui.address" };

            for (int i = 0; i < fields.length; i++)
            {
                JComponent labelCell = label(I18n.t(labels[i]));
                JComponent fieldCell = fields[i];

                if (fields[i] == address)
                {
                    // The duplicate warning sits beside the label, its room always held so the columns never shift
                    // (OB-417); the (i) ends where the dropdown above ends, so the field is shortened by its width
                    JPanel labelRow = new JPanel(new BorderLayout(4, 0));
                    labelRow.setOpaque(false);
                    labelRow.add(labelCell, BorderLayout.WEST);
                    labelRow.add(sameAddress, BorderLayout.CENTER);
                    labelCell = labelRow;

                    JLabel addressHint = InfoMark.label(I18n.t("loc.ui.settingsAddressHint"));

                    addressHint.setName("settings.addressHint");
                    address.setPreferredSize(new Dimension(name.getPreferredSize().width
                        - addressHint.getPreferredSize().width - 4, name.getPreferredSize().height));

                    JPanel addressRow = new JPanel(new BorderLayout(4, 0));
                    addressRow.setOpaque(false);
                    addressRow.add(address, BorderLayout.CENTER);
                    addressRow.add(addressHint, BorderLayout.EAST);
                    fieldCell = addressRow;
                }

                gbc.gridy = i + 1;
                gbc.gridx = 0;
                gbc.weightx = 0;
                gbc.fill = GridBagConstraints.NONE;
                gbc.insets = edge;
                add(labelCell, gbc);
                gbc.gridx = 1;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = inner;
                add(fieldCell, gbc);
            }

            gbc.fill = GridBagConstraints.NONE;
            gbc.gridx = 1;
            gbc.gridy = 4;

            if (adding)
            {
                gbc.gridy = 5;
                gbc.weighty = 1;
                add(new JLabel(), gbc);

                warnAboutTheSameAddress();

                return;
            }

            gbc.gridx = 0;
            gbc.gridy = 6;
            gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            // As much air under the line as over it, with the heading's own top inset
            gbc.insets = new Insets(6, 4, 6, 6);
            add(new javax.swing.JSeparator(), gbc);
            gbc.gridwidth = 1;
            gbc.fill = GridBagConstraints.NONE;

            gbc.gridy = 7;
            gbc.gridwidth = 2;
            gbc.insets = edge;
            add(UiStyle.heading(I18n.t("loc.ui.settingsCustomizeBehavior")), gbc);
            gbc.gridwidth = 1;

            gbc.gridy = 8;
            add(label(I18n.t("loc.ui.settingsPresetSpeed")), gbc);
            gbc.gridx = 1;
            gbc.insets = inner;
            JPanel speedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            speedRow.setOpaque(false);
            speedRow.add(speed);
            speedRow.add(javax.swing.Box.createHorizontalStrut(4));
            speedRow.add(new JLabel("%"));
            speedRow.add(javax.swing.Box.createHorizontalStrut(8));
            speedRow.add(InfoMark.label(I18n.t("loc.ui.settingsPresetSpeedHint")));
            add(speedRow, gbc);

            // The room between the fields and the picture, and the rest of the tab below
            gbc.gridx = 2;
            gbc.gridy = 1;
            gbc.weightx = 1;
            gbc.insets = new Insets(0, 3, 0, 3);
            add(new JLabel(), gbc);

            gbc.gridx = 0;
            gbc.gridy = 9;
            gbc.weightx = 0;
            gbc.weighty = 1;
            add(new JLabel(), gbc);

            gbc.gridy = 10;
            gbc.weighty = 0;
            gbc.insets = edge;
            gbc.anchor = GridBagConstraints.SOUTHWEST;
            add(trash, gbc);

            warnAboutTheSameAddress();
        }

        /** What a picture mark does, and that it does it at once. */
        private String noted(String what)
        {
            return "<html>" + what.replace("&", "&amp;").replace("<", "&lt;") + "<br>"
                + I18n.t("loc.ui.settingsPictureNote") + "</html>";
        }

        private void mark(JLabel mark, String tip, java.util.function.Consumer<MouseEvent> doThis)
        {
            mark.setToolTipText(tip);
            mark.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            mark.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseReleased(MouseEvent e)
                {
                    if (!mark.isVisible() || !moveAllowed()) return;

                    doThis.accept(e);
                }
            });
        }

        /** The rule for the picture's marks and the trash can, asked at the click. */
        boolean moveAllowed()
        {
            return !LocomotiveSettings.refuseIfNotEditable(main, loc, LocomotiveSettingsFrame.this);
        }

        private void delete()
        {
            if (!moveAllowed()) return;

            main.locomotiveActions.deleteLoc(loc.getName(), LocomotiveSettingsFrame.this);
        }

        private void typed()
        {
            warnAboutTheSameAddress();
            changed();
        }

        /** A warning, not a refusal: the model allows two locomotives at one address. */
        private void warnAboutTheSameAddress()
        {
            java.util.List<String> also = new java.util.ArrayList<>();

            try
            {
                decoderType type = (decoderType) decoder.getSelectedItem();
                int typed = org.traincontrol.util.Conversion.parseTypedAddress(address.getText().trim(),
                    type == decoderType.MFX);

                for (Locomotive other : main.getModel().getLocomotives())
                {
                    if (other != loc && other.getAddress() == typed && other.getDecoderType() == type)
                    {
                        also.add(other.getName());
                    }
                }
            }
            catch (RuntimeException notANumberYet)
            {
                // Said by Save, if it is still not one then.
            }

            sameAddress.setIcon(also.isEmpty() ? null : RowIcons.warning(16));
            sameAddress.setToolTipText(also.isEmpty() ? null : org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel
                .wrapped(I18n.f("loc.ui.settingsAlsoUsedBy", String.join(", ", also))));
            sameAddress.setCursor(java.awt.Cursor.getPredefinedCursor(
                also.isEmpty() ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.HAND_CURSOR));

            setSharers(also);
        }

        /** Sync needs a Central Station on the wire; the lock does not matter, it only reads. */
        void refreshSync()
        {
            boolean connected = main.isCentralStationConnected();

            sync.setEnabled(connected);
            sync.setToolTipText(connected ? null : I18n.t("loc.ui.settingsSyncNoStation"));
        }

        boolean nameChanged()
        {
            return !name.getText().equals(loadedName);
        }

        /**
         * The address to write: the typed one when it was changed; the locomotive's own when only the decoder was, since
         * the write needs an address and a sync may have changed it since; none otherwise.
         */
        String addressText()
        {
            if (!address.getText().equals(loadedAddress)) return address.getText();

            return decoder.getSelectedItem() != loadedDecoder ? Integer.toString(loc.getAddress()) : "";
        }

        boolean identityChanged()
        {
            return !name.getText().equals(loadedName) || !address.getText().equals(loadedAddress)
                || decoder.getSelectedItem() != loadedDecoder;
        }

        boolean speedChanged()
        {
            return speedValue() != loadedSpeed;
        }

        int speedValue()
        {
            return ((Number) speed.getValue()).intValue();
        }

        boolean isDirty()
        {
            return identityChanged() || speedChanged();
        }

        /** Grays what the rule refuses. */
        void setBlocked(boolean m)
        {
            trash.setEnabled(!m);
            wrench.setEnabled(!m);
            crop.setEnabled(!m);
            revert.setEnabled(!m);
        }

        /** Shows the locomotive's picture and the marks that fit it. */
        void refreshPicture()
        {
            String url = loc.getImageURL();

            // Loaded again only when the locomotive's picture has changed: this is told of every repaint
            if (!java.util.Objects.equals(url, shownPicture))
            {
                shownPicture = url;

                if (url == null || url.isEmpty())
                {
                    // The application's stand-in, as the locomotive buttons and the database show it (FR-054)
                    picture.setIcon(new javax.swing.ImageIcon(
                        org.traincontrol.gui.locomotive.LocomotivePlaceholder.image(PICTURE_WIDTH - 2)));
                }
                else
                {
                    // A fetch may be over the network: loaded off the event thread, and a result for a locomotive the
                    // window has since left is ignored
                    final Locomotive target = loc;

                    main.getImageLoader().execute(() ->
                    {
                        javax.swing.ImageIcon loaded = null;

                        try
                        {
                            loaded = new javax.swing.ImageIcon(main.getLocImageMaxHeight(url, PICTURE_WIDTH - 2, PICTURE_HEIGHT - 2));
                        }
                        catch (Exception e)
                        {
                            // Shown as no picture, and said in the log
                            main.getModel().logf("loc.ui.errorImageLoad", url);
                        }

                        final javax.swing.ImageIcon shown = loaded;

                        SwingUtilities.invokeLater(() ->
                        {
                            if (loc == target && url.equals(shownPicture)) picture.setIcon(shown);
                        });
                    });
                }
            }

            crop.setVisible(TrainControlUI.offersACrop(loc));
            revert.setVisible(loc.getLocalImageURL() != null);
        }
    }

    /** The third tab: years, railway and notes, and Find Similar Locomotives. */
    private final class NotesTab extends JPanel
    {
        final LocomotiveNotesPanel panel = new LocomotiveNotesPanel(loc,
            LocomotiveNotesPanel.railwayNames(main.getModel().getLocomotives()), 8, 50, LocomotiveSettingsFrame.this::changed);

        NotesTab()
        {
            super(new BorderLayout(0, 6));

            JButton find = new JButton(I18n.t("loc.ui.menuFindSimilarLocomotives") + "...");

            find.setName("settings.findSimilar");
            find.setFont(BUTTON_FONT);
            find.setFocusable(false);
            find.setToolTipText(I18n.t("loc.ui.tooltip.findSimilarHint"));
            find.addActionListener(e -> main.locomotiveActions.findSimilarLocs(loc, LocomotiveSettingsFrame.this));

            panel.styleCaptions();

            // The gap is the notes panel's cell margin, so the button starts where the captions do
            JPanel foot = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            foot.add(find);
            foot.add(InfoMark.label(I18n.t("loc.ui.settingsFindSimilarHint")));

            add(panel, BorderLayout.CENTER);
            add(foot, BorderLayout.SOUTH);
        }
    }

    /** The fourth tab: which locomotives run with this one as a train. */
    private final class MultiUnitTab extends JPanel
    {
        private MultiUnitPanel members;

        MultiUnitTab()
        {
            super(new BorderLayout(0, 6));

            Locomotive parent = main.getModel().isLocLinkedToOthers(loc);

            if (parent != null)
            {
                add(new JLabel(I18n.f("loc.ui.settingsPartOfMultiUnit", loc.getName(), parent.getName())), BorderLayout.NORTH);
            }
            else if (main.getModel().getLocomotives().size() < 2)
            {
                add(new JLabel(I18n.t("loc.ui.errorNeedTwoLocomotivesForMultiUnit")), BorderLayout.NORTH);
            }
            else
            {
                members = new MultiUnitPanel(main, loc, LocomotiveSettingsFrame.this::changed);

                members.setPreferredSize(new Dimension(560, 330));

                members.setNote(members.isReadOnly() ? I18n.t("loc.ui.settingsMultiUnitReadOnly")
                    : I18n.f("loc.ui.settingsMultiUnitHint", loc.getName()));

                add(members, BorderLayout.CENTER);

            }
        }

        boolean isDirty()
        {
            return members != null && members.isDirty();
        }

        java.util.Map<String, Double> chosen()
        {
            return members == null ? new java.util.HashMap<>() : members.chosen();
        }
    }

    /**
     * The fifth tab: what autonomy needs to know of the train.  A setup that keeps its own copy of a standing train has
     * that copy changed too, or the next load would undo the Save.
     */
    private final class AutonomyTab extends JPanel
    {
        private final int loadedLength = loc.getTrainLength() == null ? 0 : loc.getTrainLength();
        private final boolean loadedReversible = loc.isReversible();
        private final Integer loadedDeparture = loc.getDepartureFunc();
        private final Integer loadedArrival = loc.getArrivalFunc();
        private final boolean loadedActive = !loc.isAutonomyPaused();

        final JSpinner length = typedAtOnce(new JSpinner(new SpinnerNumberModel(loadedLength, 0,
            Math.max(TrainControlUI.ROUTE_TRAIN_LENGTH_MAX, loadedLength), 1)));
        final javax.swing.JCheckBox reversible = new javax.swing.JCheckBox(I18n.t("loc.ui.settingsReversibleBox"));
        final javax.swing.JCheckBox active = new javax.swing.JCheckBox(I18n.t("autosetup.ui.trainMayBeMoved"));
        final JComboBox<String> departure = functionChoices(loadedDeparture);
        final JComboBox<String> arrival = functionChoices(loadedArrival);

        AutonomyTab()
        {
            super(new GridBagLayout());

            length.setName("autonomy.length");
            reversible.setName("autonomy.reversible");
            active.setName("autonomy.active");
            departure.setName("autonomy.departure");
            arrival.setName("autonomy.arrival");

            reversible.setSelected(loadedReversible);
            active.setSelected(loadedActive);

            Font regular = new Font("Segoe UI", Font.PLAIN, 14);

            reversible.setFont(regular);
            active.setFont(regular);

            length.addChangeListener(e -> changed());
            reversible.addItemListener(e -> changed());
            active.addItemListener(e -> changed());
            departure.addActionListener(e -> changed());
            arrival.addActionListener(e -> changed());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.WEST;

            int y = 0;

            y = heading(gbc, y, "loc.ui.settingsAutonomyHeading");
            y = row(gbc, y, "autolayout.ui.trainLength", length, "loc.ui.settingsTrainLengthHint");
            y = row(gbc, y, "autolayout.ui.reversible", reversible, "loc.ui.settingsReversibleHint");
            y = row(gbc, y, "autosetup.ui.trainActive", active, "autolayout.ui.hintLocomotiveActive");
            y = heading(gbc, y, "loc.ui.settingsAutonomyFunctionsHeading");
            y = row(gbc, y, "autolayout.ui.departureFunction", departure, "loc.ui.settingsDepartureHint");
            y = row(gbc, y, "autolayout.ui.arrivalFunction", arrival, "loc.ui.settingsArrivalHint");

            // The rest of the tab, right and below
            gbc.gridx = 3;
            gbc.gridy = y;
            gbc.weightx = 1;
            gbc.weighty = 1;
            add(new JLabel(), gbc);
        }

        private int heading(GridBagConstraints gbc, int y, String key)
        {
            gbc.gridx = 0;
            gbc.gridy = y;
            gbc.gridwidth = 3;
            gbc.insets = new Insets(y == 0 ? 4 : 14, 4, 4, 6);
            add(UiStyle.heading(I18n.t(key)), gbc);
            gbc.gridwidth = 1;

            return y + 1;
        }

        private int row(GridBagConstraints gbc, int y, String label, JComponent field, String hint)
        {
            gbc.gridy = y;
            gbc.gridx = 0;
            gbc.insets = new Insets(4, 4, 4, 12);
            add(label(I18n.t(label)), gbc);
            gbc.gridx = 1;
            gbc.insets = new Insets(4, 4, 4, 6);
            add(field, gbc);
            gbc.gridx = 2;
            add(InfoMark.label(I18n.t(hint)), gbc);

            return y + 1;
        }

        /** None, then F0 up to the last function, and to the one set if it lies beyond. */
        private JComboBox<String> functionChoices(Integer set)
        {
            int count = Math.max(loc.getNumF(), set == null ? 0 : set + 1);

            String[] items = new String[count + 1];

            items[0] = I18n.t("autosetup.ui.labelNone");

            for (int i = 0; i < count; i++) items[i + 1] = "F" + i;

            JComboBox<String> out = new JComboBox<>(items);

            out.setSelectedIndex(set == null ? 0 : set + 1);

            return out;
        }

        private Integer function(JComboBox<String> box)
        {
            return box.getSelectedIndex() <= 0 ? null : box.getSelectedIndex() - 1;
        }

        private int lengthValue()
        {
            return ((Number) length.getValue()).intValue();
        }

        boolean isDirty()
        {
            return lengthValue() != loadedLength || reversible.isSelected() != loadedReversible
                || !java.util.Objects.equals(function(departure), loadedDeparture)
                || !java.util.Objects.equals(function(arrival), loadedArrival) || active.isSelected() != loadedActive;
        }

        /** Writes what changed onto the train, and into the setup's copies of it under `Point.toJSON`'s keys. */
        void apply()
        {
            java.util.Map<String, Object> stored = new java.util.LinkedHashMap<>();

            if (lengthValue() != loadedLength)
            {
                main.applyTrainLength(loc, lengthValue());

                stored.put("trainLength", lengthValue() > 0 ? (Object) lengthValue() : null);
            }

            if (reversible.isSelected() != loadedReversible)
            {
                loc.setReversible(reversible.isSelected());

                stored.put("reversible", reversible.isSelected());
            }

            if (!java.util.Objects.equals(function(departure), loadedDeparture))
            {
                loc.setDepartureFunc(function(departure));

                stored.put("departureFunc", function(departure));
            }

            if (!java.util.Objects.equals(function(arrival), loadedArrival))
            {
                loc.setArrivalFunc(function(arrival));

                stored.put("arrivalFunc", function(arrival));
            }

            org.traincontrol.automation.Layout railway = main.getModel().getAutoLayoutIfLoaded();

            if (railway != null) railway.applyDefaultLocCallbacks(loc);

            main.recordInTheSetup(loc, stored);

            if (active.isSelected() != loadedActive)
            {
                main.setLocomotiveActive(main.getAutonomySession(), loc, active.isSelected());
            }

            main.repaintAutoLocList(false);
        }
    }

    /** For tests: where each part of the window is. */
    public javax.swing.JTabbedPane tabsForTest()
    {
        return tabs;
    }

    /** Runs {@code r} on the event thread, for the hooks that are told from other threads. */
    static void later(Runnable r)
    {
        SwingUtilities.invokeLater(r);
    }
}
