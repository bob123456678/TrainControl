package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.AbstractCellEditor;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.widgets.PopupListener;
import org.traincontrol.gui.widgets.RowIcons;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.gui.widgets.QuickFileChooser;
import org.traincontrol.util.I18n;

/**
 * The Functions tab of the Locomotive Settings window: one table of a locomotive's functions (icon, behavior, whether
 * on now, whether in the preset) held in a buffer that is written only by {@link #apply}.
 */
final class LocomotiveFunctionsTab extends JPanel
{
    /** Toggle, Momentary, then 1s to 30s. */
    private static final int BEHAVIORS = 32;

    /** One function as the table holds it, and as it was when the tab was built. */
    static final class Row
    {
        final int function;
        int icon;
        int behavior;
        String custom;
        boolean preset;

        final int icon0;
        final int behavior0;
        final String custom0;
        final boolean preset0;

        Row(int function, int icon, int behavior, String custom, boolean preset)
        {
            this.function = function;
            this.icon = this.icon0 = icon;
            this.behavior = this.behavior0 = behavior;
            this.custom = this.custom0 = custom;
            this.preset = this.preset0 = preset;
        }

        boolean differs()
        {
            return icon != icon0 || behavior != behavior0 || !Objects.equals(custom, custom0) || preset != preset0;
        }
    }

    private final TrainControlUI ui;
    private final Locomotive loc;
    private final Runnable onChange;
    private final List<Row> rows = new ArrayList<>();
    private final String[] behaviorNames = new String[BEHAVIORS];

    /** The standard icons, null until loaded. */
    private final ImageIcon[] standardIcons;

    private final JTable table;
    private final TableModel model = new TableModel();
    private final IconCell iconRenderer;
    private final IconCell iconEditor;
    private final JButton copyIcons = button("");
    private final JButton resetAll = revertButton("functions.resetAll", "loc.ui.resetAllCustomizations");
    private final JButton clearPreset = revertButton("functions.clearPreset", "loc.ui.settingsClearPreset");
    private final javax.swing.Timer spinner = new javax.swing.Timer(100, e -> turn());
    private int phase;
    private boolean reset;
    private boolean copied;
    private boolean blocked;

    LocomotiveFunctionsTab(TrainControlUI ui, Locomotive loc, Runnable onChange)
    {
        super(new BorderLayout(0, 6));

        this.ui = ui;
        this.loc = loc;
        this.onChange = onChange;

        behaviorNames[0] = I18n.t("function.ui.toggle");
        behaviorNames[1] = I18n.t("function.ui.momentary");

        for (int i = 2; i < BEHAVIORS; i++) behaviorNames[i] = (i - 1) + "s";

        this.standardIcons = new ImageIcon[loc.getNumFnIcons() + 1];

        boolean[] preset = loc.getPreferredFunctions();

        for (int f = 0; f < loc.getNumF(); f++)
        {
            rows.add(new Row(f, loc.sanitizeFIconIndex(loc.getFunctionType(f)), behaviorShownFor(loc, f),
                loc.getLocalFunctionImageURL(f), preset != null && f < preset.length && preset[f]));
        }

        this.iconRenderer = new IconCell(false);
        this.iconEditor = new IconCell(true);

        table = new JTable(model);
        table.setName("functions.table");
        table.setRowHeight(TrainControlUI.BUTTON_ICON_WIDTH + 8);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(org.traincontrol.gui.widgets.UiStyle.LABEL_FONT);
        table.getTableHeader().setForeground(org.traincontrol.gui.widgets.UiStyle.TEXT);
        table.getColumnModel().getColumn(1).setCellRenderer(iconRenderer);
        table.getColumnModel().getColumn(1).setCellEditor(iconEditor);
        table.getColumnModel().getColumn(TEST).setCellRenderer(new TestCell());

        JComboBox<String> behaviorBox = new JComboBox<>(behaviorNames);
        behaviorBox.setName("functions.behaviorCombo");

        DefaultCellEditor behaviorEditor = new DefaultCellEditor(behaviorBox);
        behaviorEditor.setClickCountToStart(1);
        table.getColumnModel().getColumn(2).setCellEditor(behaviorEditor);

        fixTheFnColumn();
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.getColumnModel().getColumn(TEST).setPreferredWidth(70);

        // The Test column's cell is the target: a press anywhere in it switches the function
        table.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseReleased(MouseEvent e)
            {
                int row = playRowAt(e);

                if (row >= 0 && SwingUtilities.isLeftMouseButton(e)) press(row);
            }
        });

        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                boolean over = playRowAt(e) >= 0;

                table.setCursor(over ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR) : null);
                table.setToolTipText(over ? I18n.t("loc.ui.settingsSwitchFunction") : null);
            }
        });

        table.addMouseListener(new PopupListener()
        {
            @Override
            protected void showPopup(MouseEvent e)
            {
                int row = table.rowAtPoint(e.getPoint());

                if (row < 0) return;

                table.setRowSelectionInterval(row, row);

                rowMenu(rows.get(row)).show(table, e.getX(), e.getY());
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(560, 280));

        add(scroll, BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);

        loadStandardIcons();
    }

    private static JButton button(String text)
    {
        JButton b = new JButton(text);

        b.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        b.setFocusable(false);

        return b;
    }

    /** Where the behavior list shows a function's stored trigger: its timing, momentary, or toggle. */
    private static int behaviorShownFor(Locomotive l, int function)
    {
        if (l.isFunctionTimed(function) > 0) return Math.min(l.isFunctionTimed(function) + 1, BEHAVIORS - 1);

        return l.isFunctionPulse(function) ? 1 : 0;
    }

    /** The stored trigger a place in the behavior list means. */
    private static int triggerFor(int behavior)
    {
        if (behavior == 0) return Locomotive.FUNCTION_TOGGLE;
        if (behavior == 1) return Locomotive.FUNCTION_PULSE;

        return behavior - 1;
    }

    /** The place in the behavior list a stored trigger shows at. */
    private static int behaviorOfTrigger(int trigger)
    {
        if (trigger > 0) return Math.min(trigger + 1, BEHAVIORS - 1);

        return trigger == Locomotive.FUNCTION_PULSE ? 1 : 0;
    }

    private JPanel buttons()
    {
        JButton useCurrent = button(I18n.t("loc.ui.menuSaveFunctionsAsPreset"));

        useCurrent.setName("functions.useCurrent");
        copyIcons.setName("functions.copyIcons");

        useCurrent.addActionListener(e ->
        {
            for (Row r : rows) r.preset = r.function < loc.getNumF() && loc.getF(r.function);

            changed();
        });

        clearPreset.addActionListener(e ->
        {
            for (Row r : rows) r.preset = false;

            changed();
        });

        fitCopyIcons();
        copyIcons.setToolTipText(I18n.t("loc.ui.toolip.copyIconHelp"));
        copyIcons.addActionListener(e -> copyFrom(ui.getCopyTarget()));

        refreshReverts();
        resetAll.addActionListener(e -> resetEverything());

        // One row: the copy on the left, the preset on the right
        JPanel copyGroup = group(copyIcons, resetAll);
        JPanel presetGroup = group(useCurrent, clearPreset, org.traincontrol.gui.InfoMark.label(I18n.t("loc.ui.settingsFunctionsHint")));

        copyGroup.setName("functions.copyGroup");
        presetGroup.setName("functions.presetGroup");

        JPanel row = new JPanel(new BorderLayout(12, 0));

        row.add(copyGroup, BorderLayout.WEST);
        row.add(presetGroup, BorderLayout.EAST);

        return row;
    }

    /** Components side by side with a 4 pixel gap and none at the ends, so the row's edges line up with the table's. */
    private static JPanel group(java.awt.Component... parts)
    {
        JPanel panel = new JPanel();

        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.X_AXIS));

        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0) panel.add(javax.swing.Box.createHorizontalStrut(4));

            panel.add(parts[i]);
        }

        return panel;
    }

    /** The Copy Icons button names, and is lit by, the locomotive on the clipboard now. */
    private void fitCopyIcons()
    {
        Locomotive from = ui.getCopyTarget();

        copyIcons.setEnabled(from != null);
        copyIcons.setText(from == null ? I18n.t("loc.ui.copyIconsFromClipboardLocomotive")
            : I18n.f("loc.ui.copyIconsFromLocomotive", from.getName()));
    }

    /** The standard icons are fetched on a thread of their own, so they do not hold up the main window's pictures. */
    private static final java.util.concurrent.ExecutorService ICON_LOADER =
        java.util.concurrent.Executors.newSingleThreadExecutor(r ->
        {
            Thread t = new Thread(r, "function-icons");

            t.setDaemon(true);

            return t;
        });

    /** Shows each standard icon as it arrives. */
    private void loadStandardIcons()
    {
        boolean active = ui.getModel().isCS3() || !ui.getModel().getNetworkCommState();

        for (int i = 0; i < standardIcons.length; i++)
        {
            final int which = i;

            ICON_LOADER.submit(() ->
            {
                ImageIcon loaded = null;

                try
                {
                    loaded = new ImageIcon(ui.getLocImage(loc.getFunctionIconUrl(which, active, true),
                        TrainControlUI.BUTTON_ICON_WIDTH));
                }
                catch (Exception e)
                {
                    ui.getModel().logf("loc.ui.errorLoadingFunctionIcon", which);
                }

                final ImageIcon shown = loaded;

                SwingUtilities.invokeLater(() ->
                {
                    standardIcons[which] = shown;
                    table.repaint();
                    iconEditor.combo.repaint();
                });
            });
        }
    }

    private void changed()
    {
        model.fireTableRowsUpdated(0, Math.max(0, rows.size() - 1));
        refreshReverts();

        if (onChange != null) onChange.run();
    }

    /** Puts the functions of another locomotive into the table; written at Save. */
    private void copyFrom(Locomotive from)
    {
        if (from == null) return;

        Locomotive.Customizations c = from.captureCustomizations();

        int[] types = c.getTypes();
        int[] triggers = c.getTriggers();
        Map<Integer, String> icons = c.getIcons();

        for (Row r : rows)
        {
            if (r.function < types.length) r.icon = loc.sanitizeFIconIndex(types[r.function]);

            if (r.function < triggers.length) r.behavior = behaviorOfTrigger(triggers[r.function]);

            r.custom = icons.get(r.function);
        }

        copied = true;

        changed();
    }

    /** Asks, then marks every customization to go at Save and shows the table without them. */
    private void resetEverything()
    {
        // NOT [0]: Yes here throws away every custom function and icon on this locomotive, so it is not the answer a
        // reflexive Enter should give.
        int answer = JOptionPane.showOptionDialog(
            this,
            I18n.t("loc.ui.confirmResetFunctionsToCentralStation"),
            I18n.t("loc.ui.dialogConfirmReset"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            TrainControlUI.YES_NO_OPTS,
            TrainControlUI.YES_NO_OPTS[1]
        );

        if (answer != JOptionPane.YES_OPTION) return;

        reset = true;
        copied = false;

        for (Row r : rows)
        {
            r.icon = r.icon0;
            r.behavior = r.behavior0;
            r.custom = null;
        }

        changed();
    }

    /** Whether anything in the table differs from what the locomotive held when the tab was built. */
    boolean isDirty()
    {
        if (reset || copied) return true;

        for (Row r : rows)
        {
            if (r.differs()) return true;
        }

        return false;
    }

    /** Shows which function is the one being looked at. */
    void select(int function)
    {
        if (function < 0 || function >= rows.size()) return;

        table.setRowSelectionInterval(function, function);
        table.scrollRectToVisible(table.getCellRect(function, 0, true));
    }

    /** Ends a cell edit in progress, as Enter would, so what it shows is in the buffer. */
    void commitEdit()
    {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
    }

    /** Redraws the table and the revert icons from the locomotive. */
    void refresh()
    {
        table.repaint();
        refreshReverts();
        fitCopyIcons();
        keepTurning();
    }

    /** An icon is lit when there is something to put back: a preset, or a customization. */
    private void refreshReverts()
    {
        boolean preset = false;

        for (Row r : rows) preset |= r.preset;

        clearPreset.setEnabled(preset);
        resetAll.setEnabled(loc.isCustomFunctions() || reset);
    }

    private static JButton revertButton(String name, String tipKey)
    {
        int size = com.formdev.flatlaf.util.UIScale.scale(18);
        JButton b = new JButton(RowIcons.revert(size, UiStyle.BLUE));

        b.setDisabledIcon(RowIcons.revert(size, new java.awt.Color(175, 175, 175)));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setFocusable(false);
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        b.setToolTipText(I18n.t(tipKey));
        b.setName(name);

        return b;
    }

    /** The row whose play icon the pointer is over, or -1. */
    private int playRowAt(MouseEvent e)
    {
        int row = table.rowAtPoint(e.getPoint());

        return row >= 0 && table.columnAtPoint(e.getPoint()) == TEST ? row : -1;
    }

    /**
     * The Fn column is as wide as its heading and the highest function number, and stays so: minimum, maximum and
     * preferred together, so a resized window gives its room to the other columns.
     */
    private void fixTheFnColumn()
    {
        java.awt.Font heading = table.getTableHeader().getFont();
        int widest = Math.max(table.getFontMetrics(table.getFont()).stringWidth("F" + Math.max(0, loc.getNumF() - 1)),
            table.getFontMetrics(heading).stringWidth(model.getColumnName(0)));
        int width = widest + com.formdev.flatlaf.util.UIScale.scale(20);
        javax.swing.table.TableColumn column = table.getColumnModel().getColumn(0);

        column.setMinWidth(width);
        column.setMaxWidth(width);
        column.setPreferredWidth(width);
        column.setResizable(false);
    }

    /** The Test column's place in the table. */
    private static final int TEST = 4;

    /** Whether a momentary or timed function is running its course, which the play icon shows as a turning arc. */
    private boolean firing(Row row)
    {
        return row.function < loc.getNumF() && loc.getF(row.function)
            && (loc.isFunctionPulse(row.function) || loc.isFunctionTimed(row.function) > 0);
    }

    /** A press on a play icon: a command to the railway now, not part of Save. */
    private void press(int row)
    {
        Row r = rows.get(row);

        if (blocked || r.function >= loc.getNumF()) return;

        // The cached flag is up to a second old: a train that has just started must not fire from Test
        if (LocomotiveSettings.whyNotEditable(ui, loc) != null) return;

        ui.fireFunction(loc, r.function, !loc.getF(r.function));

        // The railway answers on its own thread: shown again once it has, and turning while a pulse runs
        javax.swing.Timer later = new javax.swing.Timer(300, e ->
        {
            table.repaint();
            keepTurning();
        });

        later.setRepeats(false);
        later.start();
    }

    /** Runs the spinner only while some function is firing. */
    private void keepTurning()
    {
        boolean any = false;

        for (Row r : rows) any |= firing(r);

        if (any && !spinner.isRunning()) spinner.start();
        else if (!any && spinner.isRunning()) spinner.stop();
    }

    private void turn()
    {
        phase = (phase + 40) % 360;
        table.repaint();
        keepTurning();
    }

    @Override
    public void removeNotify()
    {
        spinner.stop();
        super.removeNotify();
    }

    /** Whether the moving rule locks the window: the Now boxes then do nothing, as they send at once. */
    void setBlocked(boolean b)
    {
        blocked = b;
        table.repaint();
    }

    /** What the tab means to write, for the window to know whether the Central Station is to be asked again. */
    boolean resets()
    {
        return reset;
    }

    /** Writes the table onto the locomotive. */
    void apply()
    {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();

        if (reset)
        {
            loc.setCustomFunctions(false);
            loc.unsetLocalFunctionImageURLs();
        }

        boolean[] preset = new boolean[loc.getNumF()];

        for (Row r : rows)
        {
            int f = r.function;

            // A decoder change in the same Save can leave the locomotive with fewer functions than the table has
            if (f >= preset.length) continue;

            if (copied || r.icon != loc.sanitizeFIconIndex(loc.getFunctionType(f)) || r.behavior != behaviorShownFor(loc, f))
            {
                loc.setFunctionType(f, r.icon, triggerFor(r.behavior));
            }

            String stored = loc.getLocalFunctionImageURL(f);

            if (r.custom == null)
            {
                if (stored != null) loc.unsetLocalFunctionImageURL(f);
            }
            else if (!r.custom.equals(stored))
            {
                loc.setLocalFunctionImageURL(f, r.custom);

                ui.getModel().logf("loc.ui.logSetCustomFunctionIcon", loc.getName(), f, loc.getLocalFunctionImageURL(f));
            }

            preset[f] = r.preset;
        }

        // A copy leaves this locomotive customized even when its source was not: it now carries somebody else's functions.
        if (copied) loc.setCustomFunctions(true);

        loc.setPreferredFunctions(preset);
    }

    /** Each custom image by path: loaded off the event thread, and a file that would not load is held as null, tried once. */
    private final Map<String, ImageIcon> customPictures = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.Set<String> customRequested = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The picture for a row: its own image once it has loaded, else its standard icon. */
    private ImageIcon pictureOf(Row r)
    {
        if (r.custom != null)
        {
            final String path = r.custom;

            if (customRequested.add(path))
            {
                ui.getImageLoader().submit(() ->
                {
                    ImageIcon loaded = null;

                    try
                    {
                        loaded = new ImageIcon(ui.getLocImage(path, TrainControlUI.BUTTON_ICON_WIDTH));
                    }
                    catch (Exception ex)
                    {
                        ui.getModel().logf("loc.ui.errorInvalidIconPath", path);
                    }

                    if (loaded != null) customPictures.put(path, loaded);

                    SwingUtilities.invokeLater(() ->
                    {
                        table.repaint();
                        iconEditor.combo.repaint();
                    });
                });
            }

            return customPictures.get(path);
        }

        return r.icon >= 0 && r.icon < standardIcons.length ? standardIcons[r.icon] : null;
    }

    /** Asks for one of the user's own images for a row; the table shows it at once and Save writes it. */
    void chooseOwnImage(Row r)
    {
        String currentPath = null;

        if (r.custom != null)
        {
            File currentIcon = TrainControlUI.fileOf(r.custom);

            if (currentIcon != null && currentIcon.exists()) currentPath = currentIcon.getParent();
        }

        JFileChooser fc = new QuickFileChooser(
            currentPath != null ? currentPath
                : TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_ICON_FOLDER, new File(".").getAbsolutePath())
        );

        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setFileFilter(QuickFileChooser.imageFilter());
        fc.setAcceptAllFileFilterUsed(false);

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            File f = fc.getSelectedFile();

            TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_ICON_FOLDER, f.getParent());

            useOwnImage(r.function, Paths.get(f.getAbsolutePath()).toUri().toString());
        }
    }

    /** Makes an image the icon of a function, in the table. */
    void useOwnImage(int function, String uri)
    {
        rows.get(function).custom = uri;

        changed();
    }

    /** Makes one of the standard icons the icon of a function, in the table, replacing an image of the user's own. */
    void useStandardIcon(int function, int icon)
    {
        Row r = rows.get(function);

        r.icon = icon;
        r.custom = null;

        changed();
    }

    /** Sets what a function does when pressed, in the table. */
    void useBehavior(int function, int behavior)
    {
        rows.get(function).behavior = behavior;

        changed();
    }

    /** The right-click menu of a row: its icon, its own image, whether it is in the preset, and what it does. */
    private JPopupMenu rowMenu(Row r)
    {
        JPopupMenu menu = new JPopupMenu();

        JMenu icon = new JMenu(I18n.t("loc.ui.settingsMenuIcon"));
        icon.setName("functions.menuIcon");

        JList<Integer> grid = new JList<>(iconNumbers());
        grid.setCellRenderer((list, value, index, selected, focus) ->
        {
            JLabel label = new JLabel(standardIcons[value] == null ? String.valueOf(value) : null, standardIcons[value],
                JLabel.CENTER);

            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());

            return label;
        });
        gridLayout(grid);
        grid.setName("functions.menuIconGrid");
        grid.setSelectedIndex(r.custom == null ? r.icon : -1);
        grid.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseReleased(MouseEvent e)
            {
                int i = grid.locationToIndex(e.getPoint());

                if (i < 0 || !grid.getCellBounds(i, i).contains(e.getPoint())) return;

                MenuSelectionManager.defaultManager().clearSelectedPath();
                menu.setVisible(false);

                useStandardIcon(r.function, i);
            }
        });

        JScrollPane gridScroll = new JScrollPane(grid);
        gridScroll.setPreferredSize(new Dimension(520, 300));
        icon.add(gridScroll);
        menu.add(icon);

        JMenuItem own = new JMenuItem(I18n.t("loc.ui.settingsMenuCustomIcon"));
        own.setName("functions.menuCustom");
        own.addActionListener(e -> chooseOwnImage(r));
        menu.add(own);

        menu.addSeparator();

        JCheckBoxMenuItem inPreset = new JCheckBoxMenuItem(I18n.t("loc.ui.settingsMenuInPreset"), r.preset);
        inPreset.setName("functions.menuPreset");
        inPreset.addActionListener(e ->
        {
            r.preset = inPreset.isSelected();

            changed();
        });
        menu.add(inPreset);

        JMenu behavior = new JMenu(I18n.t("loc.ui.settingsColumnBehavior"));
        behavior.setName("functions.menuBehavior");
        ButtonGroup group = new ButtonGroup();

        for (int i = 0; i < BEHAVIORS; i++)
        {
            final int which = i;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(behaviorNames[i], r.behavior == i);

            item.setName("functions.menuBehavior." + i);
            item.addActionListener(e -> useBehavior(r.function, which));
            group.add(item);
            behavior.add(item);
        }

        menu.add(behavior);

        return menu;
    }

    private Integer[] iconNumbers()
    {
        Integer[] out = new Integer[standardIcons.length];

        for (int i = 0; i < out.length; i++) out[i] = i;

        return out;
    }

    /** Lays icons out across the width, as the old function editor's dropdown did. */
    private static void gridLayout(JList<?> list)
    {
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(TrainControlUI.BUTTON_ICON_WIDTH + 12);
        list.setFixedCellHeight(TrainControlUI.BUTTON_ICON_WIDTH + 12);
    }

    /** The Test column: the play icon, centered, filled when the function is on. */
    private final class TestCell extends JLabel implements TableCellRenderer
    {
        TestCell()
        {
            super("", JLabel.CENTER);

            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int r, int c)
        {
            Row row = (Row) value;

            setBackground(selected ? t.getSelectionBackground() : t.getBackground());

            RowIcons.PlayState state = blocked ? RowIcons.PlayState.DISABLED
                : firing(row) ? RowIcons.PlayState.FIRING
                : row.function < loc.getNumF() && loc.getF(row.function) ? RowIcons.PlayState.ON
                : RowIcons.PlayState.OFF;

            setIcon(RowIcons.play(com.formdev.flatlaf.util.UIScale.scale(16), state, phase));
            putClientProperty("playState", state);

            return this;
        }
    }

    /** The table's rows over the buffer. */
    private final class TableModel extends AbstractTableModel
    {
        private final String[] names = {
            I18n.t("loc.ui.settingsColumnFn"), I18n.t("loc.ui.settingsColumnIcon"),
            I18n.t("loc.ui.settingsColumnBehavior"), I18n.t("loc.ui.settingsColumnPreset"),
            I18n.t("loc.ui.settingsColumnTest") };

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return names.length; }
        @Override public String getColumnName(int c) { return names[c]; }

        @Override
        public Class<?> getColumnClass(int c)
        {
            return c == 1 || c == TEST ? Row.class : c == 3 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int r, int c)
        {
            return c == 1 || c == 2 || c == 3;
        }

        @Override
        public Object getValueAt(int r, int c)
        {
            Row row = rows.get(r);

            switch (c)
            {
                case 0: return "F" + row.function;
                case 1: return row;
                case 2: return behaviorNames[row.behavior];
                case 3: return row.preset;
                default: return row;
            }
        }

        @Override
        public void setValueAt(Object value, int r, int c)
        {
            Row row = rows.get(r);

            if (c == 2)
            {
                for (int i = 0; i < behaviorNames.length; i++)
                {
                    if (behaviorNames[i].equals(value)) row.behavior = i;
                }
            }
            else if (c == 3)
            {
                row.preset = Boolean.TRUE.equals(value);
                refreshReverts();
            }

            if (onChange != null) onChange.run();
        }
    }

    /**
     * The Icon column: a dropdown of the standard icons with the wrench beside it for the user's own image.  One class
     * draws it and edits it, as two instances.
     */
    private final class IconCell extends AbstractCellEditor implements TableCellRenderer, TableCellEditor
    {
        final JComboBox<Integer> combo = new JComboBox<>(new DefaultComboBoxModel<>(iconNumbers()));
        final JButton wrench = new JButton(new LocIconActions.WrenchMark());
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        private Row row;
        private boolean filling;

        IconCell(boolean editing)
        {
            combo.setPreferredSize(new Dimension(TrainControlUI.BUTTON_ICON_WIDTH + 34, TrainControlUI.BUTTON_ICON_WIDTH + 6));
            combo.setFocusable(false);

            // The row's own picture whenever the combo shows its value, which is its image when it has one.
            combo.setRenderer((list, value, index, selected, focus) ->
            {
                ImageIcon shown = index < 0 ? (row == null ? null : pictureOf(row)) : value == null ? null : standardIcons[value];

                String number = index < 0 ? (row == null || row.custom != null ? "" : String.valueOf(row.icon))
                    : String.valueOf(value);

                JLabel label = new JLabel(shown == null ? number : null, shown, JLabel.CENTER);

                label.setOpaque(true);
                label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());

                return label;
            });

            wrench.setFocusable(false);
            wrench.putClientProperty("JButton.buttonType", "toolBarButton");
            wrench.setMargin(new Insets(1, 1, 1, 1));
            wrench.setToolTipText(I18n.t("loc.ui.settingsMenuCustomIcon"));
            wrench.setPreferredSize(new Dimension(24, 24));
            wrench.setName("functions.wrench");
            combo.setName("functions.iconCombo");

            panel.setOpaque(true);
            panel.add(combo);
            panel.add(wrench);

            if (editing)
            {
                // The grid the old function editor's dropdown used.
                BasicComboPopup popup = (BasicComboPopup) combo.getAccessibleContext().getAccessibleChild(0);
                JScrollPane scrollPane = (JScrollPane) popup.getComponent(0);
                JList<?> list = (JList<?>) scrollPane.getViewport().getView();

                gridLayout(list);
                popup.setMinimumSize(new Dimension(300, 400));

                combo.addActionListener(e ->
                {
                    if (filling || row == null || combo.getSelectedItem() == null) return;

                    useStandardIcon(row.function, (Integer) combo.getSelectedItem());

                    fireEditingStopped();
                });

                wrench.addActionListener(e ->
                {
                    Row editedRow = row;

                    fireEditingStopped();

                    if (editedRow != null) chooseOwnImage(editedRow);
                });
            }
        }

        private void show(Row r, boolean selected, JTable t)
        {
            filling = true;
            row = r;
            combo.setSelectedIndex(r.custom != null ? -1 : r.icon);
            filling = false;

            panel.setBackground(selected ? t.getSelectionBackground() : t.getBackground());
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int r, int c)
        {
            show((Row) value, selected, t);

            return panel;
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object value, boolean selected, int r, int c)
        {
            show((Row) value, true, t);

            return panel;
        }

        @Override
        public Object getCellEditorValue()
        {
            return row;
        }
    }

    /** For a test: the combo of the icon editor. */
    JComboBox<Integer> iconCombo()
    {
        return iconEditor.combo;
    }
}
