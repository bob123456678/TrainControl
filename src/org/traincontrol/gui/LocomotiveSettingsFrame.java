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
 * its functions, its notes and its multi-unit.  Everything waits for Save except the picture's overlay icons, which
 * apply at once.  A locomotive that cannot be edited now cannot be saved (see {@link LocomotiveSettings}).
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
        LOCOMOTIVE, FUNCTIONS, NOTES, MULTI_UNIT
    }

    private static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 12);

    /** The margin round every tab's content and the footer: the route editor's. */
    private static final int PAD = 10;

    /** The first tab's picture box: wide like the application's locomotive pictures, taller to balance the fields. */
    private static final int PICTURE_WIDTH = 278;
    private static final int PICTURE_HEIGHT = 150;

    /** What a test answers the unsaved-changes question with: 0 Save, 1 Discard, 2 Cancel, or null to ask. */
    public static Integer unsavedAnswerForTest;

    private final TrainControlUI main;
    private Locomotive loc;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel strip = new JLabel();
    private final JButton save = footerButton(I18n.t("loc.ui.settingsSave"), "settings.save");
    private final JButton cancel = footerButton(I18n.t("ui.cancel"), "settings.cancel");

    private LocomotiveTab locomotiveTab;
    private LocomotiveFunctionsTab functionsTab;
    private NotesTab notesTab;
    private MultiUnitTab multiUnitTab;
    private boolean blocked;

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
        strip.setOpaque(true);
        strip.setBackground(new Color(255, 243, 205));
        strip.setForeground(new Color(102, 77, 3));
        strip.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        strip.setVisible(false);

        tabs.setName("settings.tabs");

        // The application's compact tabs show the title of the chosen tab only; these keep every title beside its icon
        tabs.putClientProperty("JTabbedPane.tabWidthMode", "preferred");

        // One width, so the two buttons share an edge.
        int width = Math.max(save.getPreferredSize().width, cancel.getPreferredSize().width);

        save.setPreferredSize(new Dimension(width, save.getPreferredSize().height));
        cancel.setPreferredSize(new Dimension(width, cancel.getPreferredSize().height));

        save.addActionListener(e -> save());
        cancel.addActionListener(e -> close());

        // Save, then Cancel, at the bottom right, spaced as the route editor's are
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

        // What Save does for every tab, as a mark beside it
        buttons.add(InfoMark.label(I18n.t("loc.ui.settingsKeptHere")));
        buttons.add(save);
        buttons.add(cancel);

        JPanel footer = new JPanel(new BorderLayout());

        footer.setBorder(BorderFactory.createEmptyBorder(6, PAD, PAD, PAD));
        footer.add(buttons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());

        content.add(strip, BorderLayout.NORTH);
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
        if (loc != null && loc != l && isDirty() && !settleUnsaved()) return false;

        if (loc != l)
        {
            loc = l;

            build();
        }

        tabs.setSelectedIndex(tab.ordinal());

        watch.start();

        if (function != null && tab == Tab.FUNCTIONS) functionsTab.select(function);

        refresh();

        return true;
    }

    /** @return the locomotive the window is showing, or null */
    public Locomotive getLocomotive()
    {
        return loc;
    }

    /** Builds the four tabs afresh from the locomotive. */
    private void build()
    {
        tabs.removeAll();

        locomotiveTab = new LocomotiveTab();
        functionsTab = new LocomotiveFunctionsTab(main, loc, this::changed);
        notesTab = new NotesTab();
        multiUnitTab = new MultiUnitTab();

        for (JPanel tab : new JPanel[] { locomotiveTab, functionsTab, notesTab, multiUnitTab })
        {
            tab.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
        }

        tabs.addTab(I18n.t("loc.ui.settingsTabLocomotive"), TabIcons.locomotive(), locomotiveTab);
        tabs.addTab(I18n.t("loc.ui.settingsTabFunctions"), TabIcons.functions(), functionsTab);
        tabs.addTab(I18n.t("loc.ui.settingsTabNotes"), TabIcons.notes(), notesTab);
        tabs.addTab(I18n.t("loc.ui.settingsTabMultiUnit"), TabIcons.multiUnit(), multiUnitTab);

        setTitle(I18n.f("loc.ui.settingsTitle", loc.getName()));

        // The strip is up before the window is sized, so a locked window opens with its tabs whole
        String reason = LocomotiveSettings.stripFor(main, loc);

        if (reason != null) strip.setText(reason);

        strip.setVisible(reason != null);

        boolean first = !isVisible();

        pack();

        if (first) setLocationRelativeTo(main);
    }

    /** Re-reads the moving rule and the locomotive: the window is told whenever either may have changed. */
    void refresh()
    {
        if (loc == null) return;

        String reason = LocomotiveSettings.stripFor(main, loc);

        blocked = reason != null;

        if (blocked) strip.setText(reason);

        if (strip.isVisible() != blocked)
        {
            strip.setVisible(blocked);

            // The strip takes its room from the tabs: the window is not resized under the operator
            getContentPane().revalidate();
        }

        locomotiveTab.setBlocked(blocked);
        locomotiveTab.refreshPicture();
        functionsTab.setBlocked(blocked);
        functionsTab.refresh();
        changed();
    }

    /** Whether the window is showing anything the operator has typed and not saved. */
    boolean isDirty()
    {
        return locomotiveTab != null && (locomotiveTab.isDirty() || functionsTab.isDirty() || notesTab.panel.isDirty()
            || multiUnitTab.isDirty());
    }

    private void changed()
    {
        save.setEnabled(isDirty() && !blocked);
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
        dispose();
    }

    @Override
    public void dispose()
    {
        watch.stop();

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

            answer = JOptionPane.showOptionDialog(this, I18n.f("loc.ui.settingsUnsaved", loc.getName()),
                I18n.t("route.ui.titleDiscardChanges"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[2]);
        }

        if (answer == 0) return write();

        return answer == 1;
    }

    /** Save: writes, and closes when it went through. */
    void save()
    {
        if (write()) close();
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
        if (loc == null) return false;

        stopEditing();

        if (LocomotiveSettings.refuseIfNotEditable(main, loc, this))
        {
            refresh();
            return false;
        }

        boolean identity = locomotiveTab.identityChanged();
        boolean members = multiUnitTab.isDirty();

        if (identity && main.refuseWhileARouteDrivesIt(this, loc.getName())) return false;

        if (members && main.locomotiveActions.refuseMultiUnitEdit(this, loc, multiUnitTab.chosen().keySet())) return false;

        if (notesTab.panel.isDirty())
        {
            try
            {
                notesTab.panel.checkYears();
            }
            catch (NumberFormatException ex)
            {
                tabs.setSelectedIndex(Tab.NOTES.ordinal());

                JOptionPane.showMessageDialog(this, I18n.t("loc.ui.errorStartEndYearMustBeIntegers"),
                    I18n.t("ui.dialogInputError"), JOptionPane.ERROR_MESSAGE);

                return false;
            }
        }

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

        if (members && !main.locomotiveActions.applyLinkedLocomotives(this, loc, multiUnitTab.chosen())) return false;

        return true;
    }

    private void stopEditing()
    {
        functionsTab.commitEdit();
    }

    // ------------------------------------------------------------------------------------------------------------

    /** The first tab's labels are blue; the other tabs' item labels are black. */
    private static JLabel label(String text)
    {
        return UiStyle.heading(text);
    }

    /** The first tab: picture, name, decoder, address and preset speed, with the trash can. */
    private final class LocomotiveTab extends JPanel
    {
        final JTextField name = new JTextField(16);
        final JComboBox<decoderType> decoder = new JComboBox<>(decoderType.values());
        final JTextField address = new JTextField(16);
        final JSpinner speed = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        final JLabel sameAddress = new JLabel();
        final JLabel picture = new JLabel("", JLabel.CENTER);
        final JButton trash = new JButton(RowIcons.trash(18));
        final JLabel wrench = new JLabel(new LocIconActions.WrenchMark());
        final JLabel crop = new JLabel(new LocIconActions.CropMark());
        final JLabel revert = new JLabel(new LocIconActions.RevertMark());

        private final String loadedName = loc.getName();
        private final String loadedAddress = Integer.toString(loc.getAddress());
        private final decoderType loadedDecoder = loc.getDecoderType();
        private final int loadedSpeed = loc.getPreferredSpeed();
        private String shownPicture = "";

        LocomotiveTab()
        {
            super(new GridBagLayout());

            name.setText(loadedName);
            address.setText(loadedAddress);
            decoder.setSelectedItem(loadedDecoder);
            speed.setValue(loadedSpeed);

            name.setName("settings.name");
            decoder.setName("settings.decoder");
            address.setName("settings.address");
            speed.setName("settings.speed");
            trash.setName("settings.trash");
            wrench.setName("settings.wrench");
            crop.setName("settings.crop");
            revert.setName("settings.revert");
            picture.setName("settings.picture");

            sameAddress.setForeground(new Color(176, 96, 0));
            sameAddress.setText(" ");

            // The spinner is as tall as the fields above it
            speed.setPreferredSize(new Dimension(speed.getPreferredSize().width, name.getPreferredSize().height));

            DocumentListener tell = Typing.tell(this::typed);

            name.getDocument().addDocumentListener(tell);
            address.getDocument().addDocumentListener(tell);
            decoder.addActionListener(e -> typed());
            speed.addChangeListener(e -> typed());

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
            trash.setBorderPainted(false);
            trash.setContentAreaFilled(false);
            trash.addActionListener(e -> delete());

            // The fields are at the tab's left edge and the picture at its right, level with Name under its label; the
            // trash can is under Address, and the divider is midway between it and Preset Speed
            final Insets edge = new Insets(4, 4, 4, 6);
            final Insets inner = new Insets(4, 4, 4, 6);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = inner;
            gbc.anchor = GridBagConstraints.WEST;

            gbc.gridx = 3;
            gbc.gridy = 0;
            gbc.insets = new Insets(4, 6, 4, 4);
            gbc.anchor = GridBagConstraints.NORTHEAST;
            add(UiStyle.heading(I18n.t("loc.ui.settingsLocomotiveIcon")), gbc);

            gbc.gridy = 1;
            gbc.gridheight = 8;
            add(picture, gbc);

            gbc.gridheight = 1;
            gbc.anchor = GridBagConstraints.WEST;

            JComponent[] fields = { name, decoder, address };
            String[] labels = { "ui.name", "loc.ui.decoderType", "layout.ui.address" };

            for (int i = 0; i < fields.length; i++)
            {
                gbc.gridy = i + 1;
                gbc.gridx = 0;
                gbc.weightx = 0;
                gbc.fill = GridBagConstraints.NONE;
                gbc.insets = edge;
                add(label(I18n.t(labels[i])), gbc);
                gbc.gridx = 1;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = inner;
                add(fields[i], gbc);
            }

            gbc.fill = GridBagConstraints.NONE;
            gbc.gridx = 1;
            gbc.gridy = 4;
            add(sameAddress, gbc);

            gbc.gridx = 0;
            gbc.gridy = 5;
            gbc.insets = edge;
            add(trash, gbc);

            gbc.gridy = 6;
            gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(6, 4, 6, 6);
            add(new javax.swing.JSeparator(), gbc);
            gbc.gridwidth = 1;
            gbc.fill = GridBagConstraints.NONE;

            gbc.gridy = 7;
            gbc.insets = edge;
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
            gbc.gridy = 8;
            gbc.weightx = 0;
            gbc.weighty = 1;
            add(new JLabel(), gbc);

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
            String also = null;

            try
            {
                decoderType type = (decoderType) decoder.getSelectedItem();
                int typed = org.traincontrol.util.Conversion.parseTypedAddress(address.getText().trim(),
                    type == decoderType.MFX);

                for (Locomotive other : main.getModel().getLocomotives())
                {
                    if (other != loc && other.getAddress() == typed && other.getDecoderType() == type)
                    {
                        also = other.getName();
                        break;
                    }
                }
            }
            catch (RuntimeException notANumberYet)
            {
                // Said by Save, if it is still not one then.
            }

            sameAddress.setText(also == null ? " " : I18n.f("loc.ui.settingsAlsoUsedBy", also));
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

                    main.getImageLoader().submit(() ->
                    {
                        javax.swing.ImageIcon loaded = null;

                        try
                        {
                            loaded = new javax.swing.ImageIcon(main.getLocImageMaxHeight(url, PICTURE_WIDTH - 2, PICTURE_HEIGHT - 2));
                        }
                        catch (Exception e)
                        {
                            // Shown as no picture
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
        final LocomotiveNotesPanel panel = new LocomotiveNotesPanel(loc, 8, 50, LocomotiveSettingsFrame.this::changed);

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
