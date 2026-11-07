package org.traincontrol.gui;

import java.awt.Component;
import java.awt.GridLayout;
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
import javax.swing.JTextField;
import org.traincontrol.base.Locomotive.decoderType;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.LocomotiveNotes;
import org.traincontrol.base.Route;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.I18n;

/**
 * The locomotive operations behind the locomotive buttons' menus and the keyboard's shortcuts: jumping to a
 * locomotive by name, writing a locomotive's name and address, finding similar locomotives and mapping them to a
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
        final java.awt.Window asked = this.ui.dialogParent();

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            String input = JOptionPane.showInputDialog(
                this.ui.stillShowing(asked),
                I18n.t("loc.ui.promptEnterLocomotiveNameToJumpTo")
            );

            if (input != null)
            {
                this.ui.jumpToLocomotive(input);
            }
        });
    }

    /**
     * Renames and re-addresses a locomotive: the one door the Locomotive Settings window's Save goes through.  The name is
     * asked first (OB-301), so a refusal returns before anything has changed.  A route that drives the locomotive is asked
     * again here, before anything is written, because it can start while a window is open.
     *
     * @param source what a refusal hangs off
     * @param newName the name wanted, or null to keep it
     * @param newAddress the address as typed, or empty to keep it
     * @param newDecoderType the decoder wanted
     * @return true when the write went through, false when something was refused or failed (and said so)
     */
    boolean writeNameAndAddress(Component source, Locomotive l, String newName, String newAddress,
        decoderType newDecoderType)
    {
        try
        {
            boolean renaming = newName != null && !l.getName().equals(newName);

            if (renaming)
            {
                if (newName.trim().length() == 0)
                {
                    JOptionPane.showMessageDialog(source, I18n.t("loc.ui.errorEmptyLocomotiveName"));
                    return false;
                }

                if (newName.length() > TrainControlUI.MAX_LOC_NAME_DATABASE)
                {
                    JOptionPane.showMessageDialog(
                        source,
                        I18n.f("loc.ui.errorLocomotiveNameTooLong", TrainControlUI.MAX_LOC_NAME_DATABASE)
                    );
                    return false;
                }

                if (this.ui.getModel().getLocByName(newName) != null)
                {
                    JOptionPane.showMessageDialog(source, I18n.f("loc.ui.errorLocomotiveAlreadyExists", newName));
                    return false;
                }

                // Routes store locomotives by name in a comma-separated text format, so renaming into a name with a
                // comma rewrites every command and condition into something that re-parses as a different
                // locomotive.  (Brackets are allowed: Adam's ruling.)  The route editor refuses these names at both
                // its doors; this is the third way in.
                if (!org.traincontrol.base.RouteCommand.isNameUsable(newName))
                {
                    JOptionPane.showMessageDialog(source, I18n.t("loc.ui.errorLocomotiveNameUnusable"));
                    return false;
                }
            }

            if (RunGates.refuseWhileAutonomyRunning(this.ui, source)) return false;

            if (this.ui.refuseWhileARouteDrivesIt(source, l.getName())) return false;

            if (newAddress != null && !"".equals(newAddress.trim()))
            {
                Integer proposedAddress;

                try
                {
                    proposedAddress = Conversion.parseTypedAddress(newAddress, newDecoderType == decoderType.MFX);
                }
                catch (NumberFormatException notANumber)
                {
                    JOptionPane.showMessageDialog(source, I18n.t("loc.errorAddressNotNumeric"));
                    return false;
                }

                if (proposedAddress != l.getAddress() || newDecoderType != l.getDecoderType())
                {
                    // A new address or decoder is not written under a train with a stored speed, power on or off
                    if (LocomotiveSettings.hasSpeed(this.ui, l))
                    {
                        JOptionPane.showMessageDialog(source, I18n.f("loc.ui.stopToEdit", l.getName()));
                        return false;
                    }

                    this.ui.getModel().changeLocAddress(l.getName(), proposedAddress, newDecoderType);
                }
            }

            if (renaming) this.ui.getModel().renameLoc(l.getName(), newName);

            // Refresh everything
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

                // And the station labels on the track diagram (OB-081): refreshUI redraws the autonomy
                // surfaces, but the labels beside a station are written by updateStationLabels, which every
                // other door that changes which locomotive stands where calls.
                this.ui.updateVisiblePoints();
                this.ui.repaintAutoLocList(false);

                // And the timetable, which names locomotives too: the guard inside `repaintTimetable` is keyed
                // on a hash a rename cannot move (MT-149).
                this.ui.repaintTimetable();
            }

            return true;
        }
        catch (Exception e)
        {
            this.ui.getModel().log(e);

            JOptionPane.showMessageDialog(source, I18n.f("loc.ui.errorLocomotiveEditException", e.getMessage()));

            return false;
        }
    }

    /**
     * The refusals a multi-unit edit meets, in today's order: not while autonomy runs, nor while a route drives the
     * multi-unit or a member (OB-287), since a multi-unit's commands drive its members.
     *
     * @param newMembers the members wanted as well, or null before they are chosen
     * @return true when it refused (and said so)
     */
    boolean refuseMultiUnitEdit(Component source, Locomotive l, java.util.Collection<String> newMembers)
    {
        if (this.ui.getModel().isAutonomyRunning())
        {
            JOptionPane.showMessageDialog(source, I18n.t("autolayout.ui.errorCannotEditMultiUnitsWhileRunning"));
            return true;
        }

        java.util.Set<String> involved = new java.util.LinkedHashSet<>();

        involved.add(l.getName());
        involved.addAll(l.getLinkedLocomotiveNames().keySet());

        // Asked again at OK, of every member before and after: a route can start from its s88 while this is open.
        if (newMembers != null) involved.addAll(newMembers);

        return RunGates.refuseWhileARouteDrivesAny(this.ui, source, involved);
    }

    /**
     * Makes {@code newLinkedLocos} the members of a multi-unit and tells everything that shows it: the one write the
     * Locomotive Settings window's Multi-Unit tab goes through on Save.
     *
     * @return true when it was written, false when a refusal stopped it
     */
    boolean applyLinkedLocomotives(Component source, Locomotive l, Map<String, Double> newLinkedLocos)
    {
        if (refuseMultiUnitEdit(source, l, newLinkedLocos.keySet())) return false;

        // One call: syncWithCS2 rebuilds consists off this thread; a two-call form would stage on a shared field.
        l.setLinkedLocomotives(newLinkedLocos);
        this.ui.repaintLoc(true, null);

        // Ensure there are no conflicts on the graph
        if (this.ui.getModel().hasAutoLayout())
        {
            this.ui.getModel().getAutoLayout().sanitizeMultiUnits(l);
            this.ui.repaintAutoLocListFull();
            this.ui.getModel().getAutoLayout().refreshUI();
        }

        return true;
    }

    /**
     * Add Locomotive (FR-179): makes a locomotive with the refusals Add Locomotive always asked, in its order (the name,
     * then the address), says it was added and puts it on the selected key if that key is empty.
     *
     * @param source what the messages hang off
     * @return the new locomotive, or null when something was refused (and said so)
     */
    Locomotive addLocomotive(Component source, String locName, String typedAddress, decoderType type)
    {
        if (locName.trim().length() == 0)
        {
            JOptionPane.showMessageDialog(source, I18n.t("loc.errorNameMissing"));
            return null;
        }

        if (locName.length() >= TrainControlUI.MAX_LOC_NAME_DATABASE)
        {
            JOptionPane.showMessageDialog(source, I18n.f("loc.errorNameTooLong", TrainControlUI.MAX_LOC_NAME_DATABASE));
            return null;
        }

        if (this.ui.getModel().getLocByName(locName) != null)
        {
            JOptionPane.showMessageDialog(source, I18n.t("loc.errorNameExists"));
            return null;
        }

        // A name is born usable too (MT-270): routes store locomotives by name, comma-separated
        if (!org.traincontrol.base.RouteCommand.isNameUsable(locName))
        {
            JOptionPane.showMessageDialog(source, I18n.t("loc.ui.errorLocomotiveNameUnusable"));
            return null;
        }

        int locAddress;

        try
        {
            locAddress = Math.abs(Conversion.parseTypedAddress(typedAddress, type == decoderType.MFX));
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(source, I18n.t("loc.errorAddressNotNumeric"));
            return null;
        }

        // The model's own rule, asked rather than re-stated; the message names the type's range
        if (!org.traincontrol.marklin.MarklinLocomotive.validateNewAddress(type, locAddress))
        {
            JOptionPane.showMessageDialog(source, I18n.t(type == decoderType.MM2 ? "loc.errorMM2AddressOutOfRange"
                : type == decoderType.DCC ? "loc.errorDCCAddressOutOfRange"
                : type == decoderType.MFX ? "loc.errorMFXAddressOutOfRange" : "loc.errorAddressOutOfRange"));
            return null;
        }

        Locomotive made = type == decoderType.MFX ? this.ui.getModel().newMFXLocomotive(locName, locAddress)
            : type == decoderType.DCC ? this.ui.getModel().newDCCLocomotive(locName, locAddress)
            : this.ui.getModel().newMM2Locomotive(locName, locAddress);

        this.ui.getLocSelector().refreshLocSelectorList();

        JOptionPane.showMessageDialog(source, I18n.t("loc.infoAddedSuccessfully"));

        this.ui.mapLocToCurrentButton(locName, true);

        return made;
    }

    /**
     * Check Duplicates: writes every address more than one locomotive shares to the log, MFX and the rest apart, and
     * says whether the typed address is used by a locomotive other than {@code self}.
     *
     * @param self the locomotive being edited, or null when adding one
     */
    void checkDuplicates(Component source, String typedAddress, decoderType type, Locomotive self)
    {
        int locAddress;

        try
        {
            locAddress = Conversion.parseTypedAddress(typedAddress, type == decoderType.MFX);
        }
        catch (NumberFormatException e)
        {
            JOptionPane.showMessageDialog(source, I18n.t("loc.errorAddressNotNumeric"));
            return;
        }

        Map<Integer, java.util.Set<Locomotive>> locs = this.ui.getModel().getDuplicateLocAddresses();

        if (!locs.isEmpty())
        {
            List<Integer> sortedLocs = new java.util.ArrayList<>(locs.keySet());
            Collections.sort(sortedLocs, Collections.reverseOrder());

            this.ui.getModel().logf("loc.reportMfxHeader");
            logShared(locs, sortedLocs, true);

            this.ui.getModel().logf("loc.reportNonMfxHeader");
            logShared(locs, sortedLocs, false);

            this.ui.getModel().logf("loc.reportDuplicateSummary");
        }
        else
        {
            this.ui.getModel().logf("loc.reportNoDuplicates");
        }

        if (locAddress <= 0) return;

        // Every address in use, not the duplicate list, which leaves out an address with one locomotive
        java.util.Set<Locomotive> using = this.ui.getModel().getLocAddresses().get(locAddress);

        boolean inUse = using != null && using.stream().anyMatch(other -> other != self);

        JOptionPane.showMessageDialog(source, I18n.t(inUse ? "loc.errorAddressInUse" : "loc.infoAddressFree"));
    }

    /** The report's lines for each shared address, of MFX locomotives or of the rest. */
    private void logShared(Map<Integer, java.util.Set<Locomotive>> locs, List<Integer> addresses, boolean mfx)
    {
        for (Integer addr : addresses)
        {
            List<Locomotive> shared = locs.get(addr).stream()
                .filter(l -> (l.getDecoderType() == decoderType.MFX) == mfx)
                .collect(Collectors.toList());

            if (shared.size() < 2) continue;

            for (Locomotive l : shared)
            {
                this.ui.getModel().log("\t" + l.getName() + " [" + l.getDecoderTypeLabel() + "]");
            }

            this.ui.getModel().logf("loc.reportAddressSeparator", mfx ? Integer.toString(addr) : addr);
        }
    }

    /**
     * Shows the user locomotives similar to searchLoc, and maps them to a page if requested
     * @param searchLoc
     * @param evt
     */
    void findSimilarLocs(Locomotive searchLoc, MouseEvent evt)
    {
        findSimilarLocs(searchLoc, evt != null ? (Component) evt.getSource() : this.ui);
    }

    /** As above, with the dialogs hanging off {@code source}. */
    void findSimilarLocs(Locomotive searchLoc, Component source)
    {
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

    void deleteLoc(String value, MouseEvent evt)
    {
        deleteLoc(value, evt != null ? (Component) evt.getSource() : this.ui.dialogParent());
    }

    /**
     * Deletes a locomotive from the database after the question, from any door.
     *
     * @param source what the refusals and the question hang off
     * @return true when the locomotive was deleted
     */
    boolean deleteLoc(String value, Component source)
    {
        // The moving rule, at every door (FR-048), and asked again at Yes.
        if (LocomotiveSettings.refuseIfNotEditable(this.ui, this.ui.getModel().getLocByName(value), source)) return false;

        if (this.ui.getModel().isAutonomyRunning())
        {
            JOptionPane.showMessageDialog(source, I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning"));
            return false;
        }

        // AND NOT WHILE A ROUTE THAT DRIVES IT IS RUNNING.
        if (this.ui.refuseWhileARouteDrivesIt(source, value)) return false;
        
        // The routes that drive it, counted before it goes: deleting a locomotive removes every command that drives it
        // from every route (see Route.locomotiveDeleted), and an operator who deletes and re-adds it under the same
        // name loses those commands, which is why the question counts them.  Counted here, not afterwards, when there
        // is nothing left to count.  Commands only: a CONDITION naming it is left in place and reported on its own line
        // by the deletion.
        int drivenBy = 0;

        for (String routeName : this.ui.getModel().getRouteList())
        {
            Route r = this.ui.getModel().getRoute(routeName);

            if (r != null && r.commandsDrive(value)) drivenBy++;
        }

        String question = drivenBy > 0
            ? I18n.f("ui.confirmDeleteFromDatabaseWithRoutes" + I18n.countForm(drivenBy), value, drivenBy)
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
            if (this.ui.refuseWhileARouteDrivesIt(source, value)) return false;

            Locomotive l = this.ui.getModel().getLocByName(value);

            if (LocomotiveSettings.refuseIfNotEditable(this.ui, l, source)) return false;

            if (l != null)
            {
                // Also delete locomotive from active loc list
                if (l.equals(this.ui.activeLoc))
                {
                    this.ui.activeLoc = null;
                }

                // What Control+X is holding, if it is this locomotive: cut a train off a square, delete the locomotive,
                // press Control+V, and the paste resolves a locomotive no longer in the database.  The holder is in the
                // interface rather than the layout, which the source guard cannot see.
                if (l.equals(this.ui.cutLocomotive))
                {
                    this.ui.cutLocomotive = null;

                    // The heading goes with it, or a later cut of a DIFFERENT train would inherit it.
                    this.ui.cutFacing = null;

                    this.ui.cutFrom = null;
                }

                // Every PAGE of mappings, not only the one on screen: a button mapped on another page would keep a
                // reference to a locomotive no longer in the database, on a button that still drives real hardware.
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

                // And the two the rename doors have: `updateVisiblePoints()` is the only writer of the station captions
                // on the track diagram (OB-081), and `repaintTimetable()` is MT-149.  A locomotive leaving the database
                // changes which name is right, as a rename does.
                this.ui.updateVisiblePoints();
                this.ui.repaintTimetable();
                
                // Remove locomotive from graph
                if (this.ui.getModel().hasAutoLayout())
                {
                    this.ui.getModel().getAutoLayout().locDeleted(l);
                    this.ui.repaintAutoLocListFull();
                    this.ui.getModel().getAutoLayout().refreshUI();
                }

                this.ui.locomotiveSettingsClosedFor(l);

                return true;
            }
        }

        return false;
    }
}
