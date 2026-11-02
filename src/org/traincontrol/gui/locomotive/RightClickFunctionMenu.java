package org.traincontrol.gui.locomotive;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import org.traincontrol.base.Locomotive;

/**
 * Right‑click menu for locomotive function buttons.
 * Provides: apply preset, save preset, edit function.
 * @author Adam
 */
public class RightClickFunctionMenu extends MouseAdapter
{
    private final TrainControlUI tcui;
    private final Locomotive activeLoc;
    private final int fNumber;

    public RightClickFunctionMenu(TrainControlUI tcui, Locomotive loc, int fNumber)
    {
        this.tcui = tcui;
        this.activeLoc = loc;
        this.fNumber = fNumber;
    }

    // No mousePressed/mouseReleased overrides: the only caller (TrainControlUI.EditFunction) constructs this and calls
    // showPopup(evt) directly, so they never ran.

    public void showPopup(MouseEvent e)
    {
        JToggleButton button = (JToggleButton) e.getSource();
        FunctionPopupMenu menu = new FunctionPopupMenu(button);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * Inner popup menu class
     */
    final class FunctionPopupMenu extends JPopupMenu
    {
        public FunctionPopupMenu(JToggleButton button)
        {
            JMenuItem menuItem = new JMenuItem(I18n.f("loc.ui.editFunction", fNumber));
            menuItem.addActionListener(ev -> openEditDialog(button));
            add(menuItem);
            
            addSeparator();
            
            menuItem = new JMenuItem(
                I18n.t("loc.ui.menuApplySavedFunctionPreset")
            );
            menuItem.addActionListener(event -> tcui.applyPreferredFunctions(activeLoc));
            menuItem.setToolTipText("Alt-P");
            add(menuItem);
            
            menuItem = new JMenuItem(
                I18n.t("loc.ui.menuSaveFunctionsAsPreset")
            );
            menuItem.addActionListener(event -> tcui.savePreferredFunctions(activeLoc));
            menuItem.setToolTipText("Alt-S");
            add(menuItem);
            
            menuItem = new JMenuItem(
                I18n.t("loc.ui.menuTurnOffFunctions")
            );
            menuItem.addActionListener(event -> tcui.locFunctionsOff(activeLoc));
            menuItem.setToolTipText("Alt-O");
            add(menuItem);

            // Which function autonomy uses, said on the function itself (FR-045): "add a checkbox right click entry to
            // designate the function as the autonomy departure function, and another as the autonomy arrival
            // function...  Only show these controls if autonomy is loaded."  One per slot is the model's rule (each
            // slot is one Integer, so ticking replaces the holder).  When a different function holds the slot the item
            // stays clickable, unticked, and names the holder (see autonomySlot).  Both may be the same function.
            if (tcui.isAutonomyLoaded())
            {
                addSeparator();

                add(autonomySlot(true));
                add(autonomySlot(false));
            }
                                    
            addSeparator();

            menuItem = new JMenuItem(
                I18n.f("loc.ui.menuApplySavedSpeedPreset", activeLoc.getPreferredSpeed())
            );
            menuItem.addActionListener(event -> tcui.applyPreferredSpeed(activeLoc));
            menuItem.setToolTipText("Alt-V");
            add(menuItem);
            
            menuItem = new JMenuItem(
                I18n.t("loc.ui.menuSaveSpeedAsPreset")
            );
            menuItem.addActionListener(event -> tcui.savePreferredSpeed(activeLoc));
            menuItem.setToolTipText("Alt-U");
            add(menuItem);
        }


        /**
         * A tick for one of the two autonomy slots (FR-045): ticked when this function holds the slot, and unticking
         * clears it.  When another function holds it the item shows unticked and names which; choosing it moves the
         * slot here.
         *
         * @param departure true for the departure slot, false for arrival
         * @return the item
         */
        private javax.swing.JCheckBoxMenuItem autonomySlot(boolean departure)
        {
            Integer held = departure ? activeLoc.getDepartureFunc() : activeLoc.getArrivalFunc();

            boolean mine = held != null && held == fNumber;

            String label = I18n.t(departure
                ? "loc.ui.menuAutonomyDepartureFunction" : "loc.ui.menuAutonomyArrivalFunction");

            // Which function has it, when it is not this one: the commonest reason a tick is not where somebody
            // expects.
            if (held != null && held != fNumber) label = label + "  (F" + held + ")";

            javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(label, mine);

            item.addActionListener(event ->
            {
                Integer now = item.isSelected() ? Integer.valueOf(fNumber) : null;

                if (departure) activeLoc.setDepartureFunc(now);
                else activeLoc.setArrivalFunc(now);

                // Not logged, deliberately: it would need two more strings in eight bundles to say what the reopened
                // menu already shows.
            });

            return item;
        }
        /**
         * Calls the same logic you currently have in EditFunction(...)
         */
        private void openEditDialog(JToggleButton b)
        {
            if (!b.isEnabled()) return;

            LocomotiveFunctionAssign edit =
                new LocomotiveFunctionAssign(activeLoc, tcui, fNumber, true);

            // Focus the icon selector when the dialog actually shows: after showOptionDialog returns the modal is
            // already disposed, so a bare focusImages() there does nothing.
            edit.addAncestorListener(new javax.swing.event.AncestorListener()
            {
                @Override
                public void ancestorRemoved(javax.swing.event.AncestorEvent event) {}

                @Override
                public void ancestorMoved(javax.swing.event.AncestorEvent event) {}

                @Override
                public void ancestorAdded(javax.swing.event.AncestorEvent event)
                {
                    edit.focusImages();
                }
            });

            // The same two ticks, on the dialog (FR-045): "Add checkboxes to the full edit function popup, remembering
            // that no 2 functions should ever be selectable."  On the dialog, not in the panel: the panel is a form,
            // and a component declared by hand in a GEN-BEGIN block is deleted the next time the form is opened (as
            // OB-148's autonomy strip).  They follow the panel's own function dropdown.
            javax.swing.JComponent shown = edit;

            // What Cancel puts back (see the else branch below).  Taken before the autonomy block: with no autonomy
            // loaded the ticks are never built, so restoring is a no-op.
            final Integer departureWas = activeLoc.getDepartureFunc();
            final Integer arrivalWas = activeLoc.getArrivalFunc();

            if (tcui.isAutonomyLoaded())
            {
                final javax.swing.JCheckBox departure = new javax.swing.JCheckBox();
                final javax.swing.JCheckBox arrival = new javax.swing.JCheckBox();

                final int[] editing = { fNumber };

                Runnable follow = () ->
                {
                    Integer d = activeLoc.getDepartureFunc();
                    Integer a = activeLoc.getArrivalFunc();

                    departure.setText(I18n.t("loc.ui.menuAutonomyDepartureFunction")
                        + (d != null && d != editing[0] ? "  (F" + d + ")" : ""));

                    arrival.setText(I18n.t("loc.ui.menuAutonomyArrivalFunction")
                        + (a != null && a != editing[0] ? "  (F" + a + ")" : ""));

                    departure.setSelected(d != null && d == editing[0]);
                    arrival.setSelected(a != null && a == editing[0]);
                };

                follow.run();

                departure.addActionListener(ev ->
                {
                    activeLoc.setDepartureFunc(departure.isSelected() ? editing[0] : null);
                    follow.run();
                });

                arrival.addActionListener(ev ->
                {
                    activeLoc.setArrivalFunc(arrival.isSelected() ? editing[0] : null);
                    follow.run();
                });

                edit.setOnFunctionChanged(now ->
                {
                    editing[0] = now;
                    follow.run();
                });

                javax.swing.JPanel slots = new javax.swing.JPanel();
                slots.setLayout(new javax.swing.BoxLayout(slots, javax.swing.BoxLayout.Y_AXIS));
                slots.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 4, 0, 4));
                slots.setOpaque(false);
                slots.add(departure);
                slots.add(arrival);

                javax.swing.JPanel stacked = new javax.swing.JPanel(new java.awt.BorderLayout());
                stacked.setOpaque(false);
                stacked.add(edit, java.awt.BorderLayout.CENTER);
                stacked.add(slots, java.awt.BorderLayout.SOUTH);

                shown = stacked;
            }

            int result = JOptionPane.showOptionDialog(
                tcui,
                shown,
                I18n.f("loc.ui.dialogEditLocomotiveFunctions", activeLoc.getName()),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.OK_CANCEL_OPTS,
                TrainControlUI.OK_CANCEL_OPTS[0]
            );


            if (result == JOptionPane.OK_OPTION)
            {
                edit.doApply();
            }
            else
            {
                // Cancel puts the two slots back: their tick boxes write straight to the locomotive (the label names
                // the holder and must follow), so Cancel would otherwise keep the slot move.  Restored rather than
                // deferred, so the preview stays honest; GraphLocAssign.commitChanges applies at commit time instead.
                activeLoc.setDepartureFunc(departureWas);
                activeLoc.setArrivalFunc(arrivalWas);

                // And the copied functions: Copy Customizations also writes straight through.
                edit.undoCopiedCustomizations();

                // And the window is told (MT-466: "they are correctly undone by cancel in the single function editor,
                // but you must trigger a repaint on the target locomotive."): the undo restores the data, but nothing
                // reads a locomotive again until the model sends a message.  Forced, because the ordinary repaint is a
                // no-op when nothing the window watches has changed.
                tcui.repaintLoc(true, java.util.Arrays.asList(activeLoc));
            }
        }
    }
}
