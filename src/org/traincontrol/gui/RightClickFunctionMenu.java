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

            int result = JOptionPane.showOptionDialog(
                tcui,
                edit,
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
