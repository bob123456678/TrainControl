package org.traincontrol.gui;

import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import org.traincontrol.base.Locomotive.decoderType;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.LocomotiveNotes;
import org.traincontrol.base.Route;
import org.traincontrol.gui.locomotive.LocomotiveAddressChange;
import org.traincontrol.util.I18n;

/**
 * The locomotive operations behind the locomotive buttons' menus and the keyboard's shortcuts: jumping to a
 * locomotive by name, renaming or re-addressing one, its notes, finding similar locomotives and mapping them to a
 * page, and deleting one from the database.
 *
 * Every field stays on `TrainControlUI`; the dialogs are owned by the clicked component or the window, as before.
 */
final class LocomotiveActions
{
    private final TrainControlUI ui;

    LocomotiveActions(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Prompts for a locomotive name, and activates that locomotive if found
     */
    void quickLocSearch()
    {
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            String input = JOptionPane.showInputDialog(
                this.ui,
                I18n.t("loc.ui.promptEnterLocomotiveNameToJumpTo")
            );

            if (input != null)
            {
                this.ui.jumpToLocomotive(input);
            }
        });
    }

    void changeLocAddress(Locomotive l, MouseEvent evt)
    {
        Component source = evt != null ? (Component) evt.getSource() : this.ui;

        // THE WINDOW, read now (the popups sweep, on Adam's MT-703 note): the database's refresh below takes the tile
        // out of it, and an error after that was owned by Swing's hidden frame
        final java.awt.Window around = javax.swing.SwingUtilities.getWindowAncestor(source);

        final Component sourceWindow = source instanceof java.awt.Window ? source : around != null ? around : this.ui;
        
        if (this.ui.getModel().isAutonomyRunning())
        {
            JOptionPane.showMessageDialog(
                source,
                I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning")
            );
            return;
        }

        // AND NOT WHILE A ROUTE THAT DRIVES IT IS RUNNING.  This door renames as well as re-addresses, and
        // both rewrite what a running route is reading.
        if (this.ui.refuseWhileARouteDrivesIt(source, l == null ? null : l.getName())) return;

        try
        {
            if (l != null)
            {
                LocomotiveAddressChange edit = new LocomotiveAddressChange(l);

                int result = JOptionPane.showOptionDialog(
                    source,
                    edit,
                    I18n.f("loc.ui.dialogChangeNameOrAddress", l.getName()),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    TrainControlUI.OK_CANCEL_OPTS,
                    TrainControlUI.OK_CANCEL_OPTS[0] // default selection
                );

                // The name asked first (OB-301): a name refused returns before anything has changed, not after the new address had gone
                // through and before the sweep that takes a clash off the railway, so a standing train given another's address and a taken name
                // stayed on the railway beside it, the two answering to one decoder address.
                String newName = edit.getLocName();

                boolean renaming = result == JOptionPane.OK_OPTION && newName != null && !l.getName().equals(newName);

                if (renaming)
                {
                    if (newName.trim().length() == 0)
                    {
                        JOptionPane.showMessageDialog(
                            source,
                            I18n.t("loc.ui.errorEmptyLocomotiveName")
                        );
                        return;
                    }

                    if (newName.length() > TrainControlUI.MAX_LOC_NAME_DATABASE)
                    {
                        JOptionPane.showMessageDialog(
                            source,
                            I18n.f("loc.ui.errorLocomotiveNameTooLong", TrainControlUI.MAX_LOC_NAME_DATABASE)
                        );
                        return;
                    }

                    if (this.ui.getModel().getLocByName(newName) != null)
                    {
                        JOptionPane.showMessageDialog(
                            source,
                            I18n.f("loc.ui.errorLocomotiveAlreadyExists", newName)
                        );
                        return;
                    }

                    // Routes store locomotives by name, in a comma-separated text format, so renaming into a name with a comma rewrites every existing
                    // command and condition into something that re-parses as a different locomotive, or not at all.  (Brackets are allowed: Adam's
                    // ruling.)  The route editor refuses these names at both of its own doors; this is the third way in.
                    if (!org.traincontrol.base.RouteCommand.isNameUsable(newName))
                    {
                        JOptionPane.showMessageDialog(
                            source,
                            I18n.t("loc.ui.errorLocomotiveNameUnusable")
                        );
                        return;
                    }
                }

                // ASKED AGAIN AFTER THE DIALOG, BEFORE ANYTHING IS WRITTEN.  The question above was put before
                // the dialog opened, an s88 trigger can start a route that drives this locomotive while it is open, and
                // both writes below rewrite what that route is reading.  The multi-unit dialog and the Central Station's
                // name proposal already ask after theirs.
                if (result == JOptionPane.OK_OPTION && this.ui.refuseWhileARouteDrivesIt(source, l.getName())) return;

                String newAddress = edit.getAddress();
                decoderType newDecoderType = edit.getDecoderType();
                Integer proposedAddress;

                if (result == JOptionPane.OK_OPTION && newAddress != null && !"".equals(newAddress.trim()))
                {
                    if (newDecoderType == decoderType.MFX && newAddress.contains("0x"))
                    {
                        proposedAddress = Integer.valueOf(newAddress.replace("0x", ""), 16);
                    }
                    else
                    {
                        proposedAddress = Integer.valueOf(newAddress);
                    }

                    if (proposedAddress != l.getAddress() || newDecoderType != l.getDecoderType())
                    {
                        this.ui.getModel().changeLocAddress(l.getName(), proposedAddress, newDecoderType);
                    }
                }

                if (renaming) this.ui.getModel().renameLoc(l.getName(), newName);

                // Refresh everything
                if (result == JOptionPane.OK_OPTION)
                {
                    this.ui.clearCopyTarget();
                    this.ui.repaintLoc(true, null);
                    this.ui.repaintMappings(null, false);
                    this.ui.refreshRouteList();
                    this.ui.getLocSelector().refreshLocSelectorList();

                    // Update locomotive on graph
                    if (this.ui.getModel().hasAutoLayout())
                    {
                        this.ui.getModel().getAutoLayout().sanitizeMultiUnits(l);
                        this.ui.getModel().getAutoLayout().refreshUI();

                        // And the station labels on the track diagram (OB-081; Adam: "When autonomy is loaded: Renaming a locomotive does not immediately
                        // propagate to the labels in the track diagram viewer."): refreshUI fires the layout's callbacks, which redraw the autonomy
                        // surfaces, but the labels beside a station are written by updateStationLabels, which every other door that changes which locomotive
                        // stands where already calls ("The label still says the locomotive's name until something rewrites it.").
                        this.ui.updateVisiblePoints();
                        this.ui.repaintAutoLocList(false);

                        // And the timetable, which names locomotives too: the guard inside `repaintTimetable` is keyed on a hash a rename
                        // cannot move (MT-149), and neither rename door CALLED it.
                        this.ui.repaintTimetable();
                    }
                }
            }
        }
        catch (Exception e)
        {
            this.ui.getModel().log(e);

            JOptionPane.showMessageDialog(
                sourceWindow,
                I18n.f("loc.ui.errorLocomotiveEditException", e.getMessage())
            );
        }
    }

    /**
     * Shows the user locomotives similar to searchLoc, and maps them to a page if requested
     * @param searchLoc
     * @param evt 
     */ 
    void findSimilarLocs(Locomotive searchLoc, MouseEvent evt)
    {
        Component source = evt != null ? (Component) evt.getSource() : this.ui;

        LocomotiveNotes notes = searchLoc.getStructuredNotes();
        String railway = notes.getRailway();
        int startYear = notes.getStartYear();

        if ((railway == null || railway.trim().isEmpty()) && startYear == 0)
        {
            JOptionPane.showMessageDialog(
                source,
                I18n.t("search.ui.errorNoRailwayOrStartYear"),
                I18n.t("search.ui.dialogInsufficientData"),
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        JComboBox<Integer> countDropdown = new JComboBox<>();
        for (int i = 1; i <= 10; i++) countDropdown.addItem(i);
        for (int i = 15; i <= 50; i += 5) countDropdown.addItem(i);
        countDropdown.setSelectedItem(5);

        JTextField railroadsField = new JTextField("", 30);
        railroadsField.setText(railway);

        JCheckBox currentPageOnly = new JCheckBox(
            I18n.t("search.ui.checkboxOnlySearchCurrentPage")
        );
        currentPageOnly.setSelected(false);

        JRadioButton randomOrder = new JRadioButton(
            I18n.t("search.ui.radioRandom"),
            true
        );
        JRadioButton leastUsedOrder = new JRadioButton(
            I18n.t("search.ui.radioLeastUsed")
        );
        JRadioButton autonomyOrder = new JRadioButton(
            I18n.t("search.ui.radioFromAutonomy")
        );
        ButtonGroup orderGroup = new ButtonGroup();
        orderGroup.add(randomOrder);
        orderGroup.add(leastUsedOrder);
        orderGroup.add(autonomyOrder);

        JPanel inputPanel = new JPanel(new GridLayout(0, 1));
        inputPanel.add(new JLabel(I18n.t("search.ui.labelMaximumMatches")));
        inputPanel.add(countDropdown);
        inputPanel.add(new JLabel(I18n.t("search.ui.labelRailroadNamesCommaSeparated")));
        inputPanel.add(railroadsField);
        inputPanel.add(currentPageOnly);
        inputPanel.add(new JLabel(I18n.t("search.ui.labelOrdering")));
        inputPanel.add(randomOrder);
        inputPanel.add(leastUsedOrder);
        inputPanel.add(autonomyOrder);
        
        // Special option for selecting only autonomy locomotives if autonomy is active
        // Asked, not built
        if (this.ui.getModel().getAutoLayoutIfLoaded() == null
            || this.ui.getModel().getAutoLayoutIfLoaded().getLocomotivesToRun().isEmpty())
        {
            autonomyOrder.setVisible(false);
        }

        int result = JOptionPane.showOptionDialog(
            source,
            inputPanel,
            I18n.f("search.ui.dialogFindSimilarLocomotives", searchLoc.getName()),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            TrainControlUI.OK_CANCEL_OPTS,
            TrainControlUI.OK_CANCEL_OPTS[0] // default selection
        );
        if (result != JOptionPane.OK_OPTION) return;

        try
        {
            int count = (Integer) countDropdown.getSelectedItem();
            String rawRailroads = railroadsField.getText().trim();
            List<String> railroads = rawRailroads.isEmpty()
                ? Collections.emptyList()
                : Arrays.stream(rawRailroads.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

            List<Locomotive> sourceLocs = currentPageOnly.isSelected()
                ? this.ui.currentLocMapping().values().stream().map(ll -> (Locomotive) ll).collect(Collectors.toList())
                : this.ui.getModel().getLocomotives().stream().map(ll -> (Locomotive) ll).collect(Collectors.toList());

            // Narrow to what's on the graph
            if (this.ui.getModel().hasAutoLayout() && autonomyOrder.isSelected() 
                    && !this.ui.getModel().getAutoLayout().getLocomotivesToRun().isEmpty())
            {
                sourceLocs.retainAll(this.ui.getModel().getAutoLayout().getLocomotivesToRun());
            }
            
            boolean randomize = randomOrder.isSelected();

            List<Locomotive> matches = Locomotive.findSimilarLocomotives(
                searchLoc, count, railroads, sourceLocs, randomize
            );

            if (matches.isEmpty())
            {
                JOptionPane.showMessageDialog(
                    source,
                    I18n.t("search.ui.infoNoSimilarLocomotivesFound"),
                    I18n.t("search.ui.dialogResults"),
                    JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(I18n.t("search.ui.labelTargetLocomotive")).append("\n- ").append(searchLoc.getNotesSummaryLine());
            sb.append("\n\n").append(I18n.t("search.ui.labelSimilarLocomotives")).append("\n");
            for (Locomotive match : matches)
            {
                sb.append("- ").append(match.getNotesSummaryLine()).append("\n");
                this.ui.getModel().logf(
                    "loc.ui.infoSimilarLocomotive",
                    searchLoc.getName(),
                    match.getNotesSummaryLine()
                );
            }

            // Custom dialog with OK and Map to Page
            Object[] options = {
                I18n.t("ui.ok"),
                I18n.t("page.ui.optionMapToPage")
            };
            int choice = JOptionPane.showOptionDialog(
                source,
                sb.toString(),
                I18n.t("search.ui.dialogResults"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
            );

            if (choice == 1) // Map to Page...
            {
                JComboBox<String> pageDropdown = new JComboBox<>();
                Map<String, Integer> pageNameToIndex = new LinkedHashMap<>();
                for (int i = 0; i < this.ui.numLocMappings; i++)
                {
                    int oneBasedIndex = i + 1;
                    if (oneBasedIndex != this.ui.getLocMappingNumber())
                    {
                        String name = this.ui.pageNames.getOrDefault(
                            oneBasedIndex,
                            I18n.f("page.ui.labelDefaultPageName", oneBasedIndex)
                        );
                        pageDropdown.addItem(name);
                        pageNameToIndex.put(name, oneBasedIndex);
                    }
                }

                JPanel mapPanel = new JPanel(new GridLayout(0, 1));
                mapPanel.add(new JLabel(I18n.t("page.ui.labelSelectPageToMapLocomotives")));
                mapPanel.add(pageDropdown);

                int mapResult = JOptionPane.showOptionDialog(
                    source,
                    mapPanel,
                    I18n.t("page.ui.dialogMapLocomotives"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    TrainControlUI.OK_CANCEL_OPTS,
                    TrainControlUI.OK_CANCEL_OPTS[0] // default selection
                );
                if (mapResult == JOptionPane.OK_OPTION)
                {
                    String selectedPageName = (String) pageDropdown.getSelectedItem();
                    Integer pageNumber = pageNameToIndex.get(selectedPageName);
                    if (pageNumber != null)
                    {
                        matches.add(searchLoc);
                        this.ui.mapLocomotivesToPage(pageNumber, matches);
                    }
                    else
                    {
                        this.ui.getModel().logf(
                            "page.ui.errorInvalidPageSelection",
                            selectedPageName
                        );
                    }
                }
            }
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(
                source,
                I18n.t("search.ui.errorInvalidNumberOfMatches"),
                I18n.t("ui.dialogInputError"),
                JOptionPane.ERROR_MESSAGE
            );
            this.ui.getModel().log(ex);
        }
    }

    /**
    * Allows the user to specify notes and metadata for the given locomotive
    * @param l
    * @param evt
    */
    void changeLocNotes(Locomotive l, MouseEvent evt)
    {
        Component source = evt != null ? (Component) evt.getSource() : this.ui;

        if (l != null)
        {
            LocomotiveNotes existing = l.getStructuredNotes();

            String startYearText = existing.getStartYear() == 0 ? "" : String.valueOf(existing.getStartYear());
            String endYearText = existing.getEndYear() == 0 ? "" : String.valueOf(existing.getEndYear());

            JTextField startYearField = new JTextField(startYearText, 10);
            startYearField.setToolTipText(I18n.t("loc.ui.tooltip.StartYear"));
            JTextField endYearField = new JTextField(endYearText, 10);
            endYearField.setToolTipText(I18n.t("loc.ui.tooltip.EndYear"));
            JTextField railwayField = new JTextField(existing.getRailway(), 30);

            startYearField.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyReleased(KeyEvent e)
                {
                    TrainControlUI.validateInt(e, false);
                    TrainControlUI.limitLength(e, 4);
                }
            });

            endYearField.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyReleased(KeyEvent e)
                {
                    TrainControlUI.validateInt(e, false);
                    TrainControlUI.limitLength(e, 4);
                }
            });

            railwayField.addKeyListener(new KeyAdapter()
            {
                @Override
                public void keyReleased(KeyEvent e)
                {
                    TrainControlUI.limitLength(e, TrainControlUI.MAX_LOC_RAILWAY_NAME);
                }
            });

            JTextArea textArea = new JTextArea(existing.getNotes());
            textArea.setColumns(80);
            textArea.setRows(15);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            textArea.setSize(textArea.getPreferredSize().width, textArea.getPreferredSize().height);

            textArea.addAncestorListener(new AncestorListener()
            {
                @Override public void ancestorRemoved(AncestorEvent event) {}
                @Override public void ancestorMoved(AncestorEvent event) {}
                @Override public void ancestorAdded(AncestorEvent event)
                {
                    event.getComponent().requestFocusInWindow();
                }
            });

            JPanel panel = new JPanel(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0;
            gbc.gridy = 0;
            panel.add(new JLabel(I18n.t("loc.ui.labelStartYear")), gbc);
            gbc.gridx = 1;
            panel.add(startYearField, gbc);

            gbc.gridx = 0;
            gbc.gridy++;
            panel.add(new JLabel(I18n.t("loc.ui.labelEndYear")), gbc);
            gbc.gridx = 1;
            panel.add(endYearField, gbc);

            gbc.gridx = 0;
            gbc.gridy++;
            panel.add(new JLabel(I18n.t("loc.ui.labelRailwayName")), gbc);
            gbc.gridx = 1;
            panel.add(railwayField, gbc);

            gbc.gridx = 0;
            gbc.gridy++;
            gbc.gridwidth = 2;
            panel.add(new JLabel(I18n.t("loc.ui.labelNotes")), gbc);

            gbc.gridy++;
            panel.add(new JScrollPane(textArea), gbc);

            int confirm = JOptionPane.showOptionDialog(
                source,
                panel,
                I18n.f("loc.ui.dialogEditLocomotiveInfo", l.getName()),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.OK_CANCEL_OPTS,
                TrainControlUI.OK_CANCEL_OPTS[0] // default selection
            );

            if (confirm == JOptionPane.YES_OPTION)
            {
                try
                {
                    String startText = startYearField.getText().trim();
                    String endText = endYearField.getText().trim();

                    int startYear = startText.isEmpty() ? 0 : Math.abs(Integer.parseInt(startText));
                    int endYear = endText.isEmpty() ? 0 : Math.abs(Integer.parseInt(endText));

                    String railway = railwayField.getText().trim().substring(0, Math.min(railwayField.getText().length(), TrainControlUI.MAX_LOC_RAILWAY_NAME));
                    String notes = textArea.getText().substring(0, Math.min(textArea.getText().length(), TrainControlUI.MAX_LOC_NOTES_LENGTH));

                    l.setStructuredNotes(startYear, endYear, railway, notes);
                }
                catch (NumberFormatException ex)
                {
                    JOptionPane.showMessageDialog(
                        source,
                        I18n.t("loc.ui.errorStartEndYearMustBeIntegers"),
                        I18n.t("ui.dialogInputError"),
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }
    }

    void deleteLoc(String value, MouseEvent evt)
    {
        Component source = evt != null ? (Component) evt.getSource() : this.ui;
        
        if (this.ui.getModel().isAutonomyRunning())
        {
            JOptionPane.showMessageDialog(source, I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning"));
            return;
        }

        // AND NOT WHILE A ROUTE THAT DRIVES IT IS RUNNING.
        if (this.ui.refuseWhileARouteDrivesIt(source, value)) return;
        
        // The routes that drive it, counted before it goes: deleting a locomotive removes every command that drives it from every route
        // (the reasoning is in Route.locomotiveDeleted: a command naming a locomotive that does not exist does nothing when the route fires
        // and makes the route look complete while it is not), and an operator who deletes and re-adds it under the same name (how a decoder
        // type is changed) loses those commands, which is why the question counts them.  Counted here rather than reported afterwards, because by then there is
        // nothing left to count.  Commands only: a CONDITION naming the locomotive is left in place and reported on its own line by the
        // deletion.
        int drivenBy = 0;

        for (String routeName : this.ui.getModel().getRouteList())
        {
            Route r = this.ui.getModel().getRoute(routeName);

            if (r != null && r.commandsDrive(value)) drivenBy++;
        }

        String question = drivenBy > 0
            ? I18n.f("ui.confirmDeleteFromDatabaseWithRoutes", value, drivenBy)
            : I18n.f("ui.confirmDeleteFromDatabase", value);

        if (JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(
                source,
                question,
                I18n.t("layout.ui.dialogPleaseConfirm"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                // Default to No - deleting a locomotive from the database is not recoverable
                // from this dialog, and should not be one Enter keypress away.
                TrainControlUI.YES_NO_OPTS[1]
            ))
        {
            // ASKED AGAIN AT YES, BEFORE ANYTHING IS WRITTEN: the question at the top was put before this
            // confirmation opened, and an s88 trigger can start a route that drives the locomotive while it is open.
            if (this.ui.refuseWhileARouteDrivesIt(source, value)) return;

            Locomotive l = this.ui.getModel().getLocByName(value);

            if (l != null)
            {
                // Also delete locomotive from active loc list
                if (l.equals(this.ui.activeLoc))
                {
                    this.ui.activeLoc = null;
                }

                // What Control+X is holding, if it is this locomotive: cut a train off a square, delete the locomotive, press Control+V, and the paste
                // resolves a locomotive no longer in the database.  Harmless in itself (it fails and logs), but the same shape as the five that were
                // not, and the holder is in the interface rather than in the layout, which the source guard cannot see.
                if (l.equals(this.ui.cutLocomotive))
                {
                    this.ui.cutLocomotive = null;

                    // The heading goes with it, or a later cut of a DIFFERENT train would inherit it
                    // where that train has none of its own - and the square it was cut from.
                    this.ui.cutFacing = null;

                    this.ui.cutFrom = null;
                }

                // Every PAGE of mappings, not only the one on screen: a locomotive mapped on any other page kept its button, holding a reference to
                // something no longer in the database on a button that still drives real hardware, its decoder address being unchanged.
                for (java.util.HashMap<javax.swing.JButton, Locomotive> page : this.ui.locMapping)
                {
                    for (JButton key : new LinkedList<>(page.keySet()))
                    {
                        if (page.get(key) == l) page.put(key, null);
                    }
                }

                this.ui.getModel().deleteLoc(value);
                this.ui.clearCopyTarget();
                this.ui.repaintLoc();
                this.ui.repaintMappings();
                this.ui.getLocSelector().refreshLocSelectorList();

                // And the two the rename doors have, which this one never got: `updateVisiblePoints()` is the only writer of the station
                // captions on the track diagram (OB-081; `refreshUI()` below redraws the overlay, not the captions), so deleting a locomotive
                // standing at a station left its caption naming a locomotive the database no longer has; and `repaintTimetable()` is MT-149 at the
                // door its fix never reached.  A locomotive leaving the database changes exactly what a rename changes (which name is right), so
                // the door that removes one needs what the doors that rename one need.
                this.ui.updateVisiblePoints();
                this.ui.repaintTimetable();
                
                // Remove locomotive from graph
                if (this.ui.getModel().hasAutoLayout())
                {
                    this.ui.getModel().getAutoLayout().locDeleted(l);
                    this.ui.repaintAutoLocListFull();
                    this.ui.getModel().getAutoLayout().refreshUI();
                }
            }
        }
    }
}
