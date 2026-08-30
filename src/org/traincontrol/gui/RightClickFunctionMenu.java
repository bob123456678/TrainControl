package org.traincontrol.gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
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

    // UXR-C21: mousePressed/mouseReleased overrides used to live here, extending MouseAdapter as
    // though this were registered as a mouse listener on the function button. It never is - the only
    // caller, TrainControlUI.EditFunction, constructs this and calls showPopup(evt) directly - so
    // those two overrides never ran. Removed rather than left behind, since dead listener methods on
    // a class that keeps a live isPopupTrigger() branch look like the real wiring at a glance.

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

            // WHICH FUNCTION AUTONOMY USES, said on the function itself (FR-045).
            //
            // Adam: "add a checkbox right click entry to designate the function as the autonomy
            // departure function, and another as the autonomy arrival function... Only show these
            // controls if autonomy is loaded."
            //
            // The two slots already existed on the Locomotive and could only be filled from a pair of
            // dropdowns in the autonomy editor's Edit Locomotive view. That is a long way to go to say
            // "this horn is the one that sounds when it leaves", and it is a statement about this
            // function, which is where the tick belongs.
            //
            // ONE PER SLOT is the model's rule rather than one enforced here: each slot is a single
            // Integer, so ticking F3 does not add it, it replaces whatever held the slot. That makes
            // these read as a radio choice spread across the functions - ticking one unticks another,
            // which is why the item is disabled with a note when a DIFFERENT function holds the slot,
            // rather than silently taking it.
            //
            // Both may be the same function, which Adam asked for explicitly and which the model
            // allows: two Integers, nothing stopping them being equal.
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
         * A tick for one of the two autonomy slots (FR-045).
         *
         * Ticked when THIS function holds the slot. Unticking clears it, which is the one thing the
         * dropdowns in the autonomy editor cannot say without a blank entry.
         *
         * When ANOTHER function holds the slot the item still shows, ticked off, and says which one -
         * so the answer to "why can I not tick this" is on the item rather than discovered by trying.
         * Choosing it moves the slot here, because that is what the user just asked for.
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

            // Which function has it, when it is not this one.  A slot pointing somewhere else is the
            // commonest reason a tick is not where somebody expects it.
            if (held != null && held != fNumber) label = label + "  (F" + held + ")";

            javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(label, mine);

            item.addActionListener(event ->
            {
                Integer now = item.isSelected() ? Integer.valueOf(fNumber) : null;

                if (departure) activeLoc.setDepartureFunc(now);
                else activeLoc.setArrivalFunc(now);

                // Not logged, deliberately. Every other slot change in this application says so in the
                // log, and this one would need two more strings in eight bundles to say something the
                // menu already shows: reopen it and the tick is where it now belongs, with the other
                // slot naming whichever function holds it. Worth adding if the silence is ever
                // confusing; not worth sixteen translations before anybody has been confused.
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

            // Focus the icon selector when the dialog actually shows.  This used to be a bare
            // focusImages() after showOptionDialog returned - the modal was already disposed, so
            // it never did anything.  The ancestor listener is the same pattern GraphLocAssign
            // uses for its dropdown.
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

            // THE SAME TWO TICKS, ON THE DIALOG (FR-045).
            //
            // Adam: "Add checkboxes to the full edit function popup, remembering that no 2 functions
            // should ever be selectable."  They are the same two slots the menu offers, and the same
            // rule: a slot is one Integer, so ticking here takes it from wherever it was.
            //
            // On the dialog rather than in the panel because the panel is a form - anything added
            // inside it has to go through the designer, and a component declared by hand in a
            // GEN-BEGIN block is deleted the next time somebody opens the form. Wrapping is the same
            // answer OB-148 reached for the autonomy strip.
            //
            // They follow the panel's own function dropdown, because switching function inside it
            // changes what a tick would be about.
            javax.swing.JComponent shown = edit;

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
        }
    }
}
