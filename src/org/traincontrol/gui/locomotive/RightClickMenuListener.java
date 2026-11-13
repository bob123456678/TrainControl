package org.traincontrol.gui.locomotive;

import java.awt.Color;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.PopupListener;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu with various utility functions displayed when any locomotive button is right-clicked
 * @author Adam
 */
public class RightClickMenuListener extends PopupListener
{    
    protected TrainControlUI ui;
    protected JButton source;
    
    public RightClickMenuListener(TrainControlUI u, JButton source)
    {
        this.ui = u;
        this.source = source;
    }
    
    @Override
    protected void showPopup(MouseEvent e)
    {
        RightClickMenu menu = new RightClickMenu(ui);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }
    
    /** Shows on the item the key the window's shortcut table gives this row, display only. */
    private static void keyed(JMenuItem item, String row)
    {
        TrainControlUI.showShortcut(item, TrainControlUI.KeyShortcuts.named(row).stroke());
    }

    final class RightClickMenu extends JPopupMenu
    {
        JMenuItem menuItem;

        public RightClickMenu(TrainControlUI ui)
        {
            // Select the active locomotive
            menuItem = new JMenuItem(
                I18n.t("loc.ui.menuAssignLocomotive")
            );
            menuItem.addActionListener(event -> ui.selectLocomotiveActivated(source));
            keyed(menuItem, "choose a locomotive");
            add(menuItem);

            if (ui.buttonHasLocomotive(source))
            {
                menuItem = LocomotiveMenuItems.settings(ui, ui.getButtonLocomotive(source));

                keyed(menuItem, "locomotive settings");

                add(menuItem);
            }

            addSeparator();

            // Option to copy
            if (ui.buttonHasLocomotive(source))
            {
                menuItem = new JMenuItem(
                    I18n.f("loc.ui.menuCopyLocomotiveNamed", ui.getButtonLocomotive(source).getName())
                );
                menuItem.addActionListener(event -> ui.setCopyTarget(source, false));
                keyed(menuItem, "copy the button");
            }
            else
            {
                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuCopyLocomotive")
                );
                menuItem.setEnabled(false);
            }
            add(menuItem);

            // Option to paste
            if (ui.hasCopyTarget())
            {
                menuItem = new JMenuItem(
                    I18n.f("loc.ui.menuPasteLocomotiveNamed", ui.getCopyTarget().getName())
                );
                menuItem.addActionListener(event -> ui.doPaste(source, false, false));
                keyed(menuItem, "paste onto the button");
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.f("loc.ui.menuMoveLocomotive", ui.getCopyTarget().getName())
                );
                menuItem.addActionListener(event -> ui.doPaste(source, false, true));
                keyed(menuItem, "move onto the button");
                add(menuItem);

                if (ui.getSwapTarget() != null
                    && ui.getButtonLocomotive(source) != null
                    && !ui.getButtonLocomotive(source).getName().equals(ui.getCopyTarget().getName()))
                {
                    menuItem = new JMenuItem(
                        I18n.f("loc.ui.menuSwapLocomotive", ui.getCopyTarget().getName())
                    );
                    menuItem.addActionListener(event -> ui.doPaste(source, true, false));
                    keyed(menuItem, "swap with the button");
                    add(menuItem);
                }
            }
            else
            {
                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuPasteLocomotive")
                );
                menuItem.setEnabled(false);
                add(menuItem);
            }

            if (ui.buttonHasLocomotive(source))
            {

                addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuApplySavedFunctionPreset")
                );
                menuItem.addActionListener(event -> ui.applyPreferredFunctions(ui.getButtonLocomotive(source)));
                keyed(menuItem, "apply preferred functions");
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.f("loc.ui.menuApplySavedSpeedPreset", ui.getButtonLocomotive(source).getPreferredSpeed())
                );
                menuItem.addActionListener(event -> ui.applyPreferredSpeed(ui.getButtonLocomotive(source)));
                keyed(menuItem, "apply preferred speed");

                if (!TrainControlUI.hasSpeedPreset(ui.getButtonLocomotive(source)))
                {
                    menuItem.setEnabled(false);
                    menuItem.setToolTipText(I18n.t("loc.ui.tooltip.noSpeedPreset"));
                }

                add(menuItem);

                addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuSaveFunctionsAsPreset")
                );
                menuItem.addActionListener(event -> ui.savePreferredFunctions(ui.getButtonLocomotive(source)));
                keyed(menuItem, "save preferred functions");
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuSaveSpeedAsPreset")
                );
                menuItem.addActionListener(event -> ui.savePreferredSpeed(ui.getButtonLocomotive(source)));
                keyed(menuItem, "save preferred speed");
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuTurnOffFunctions")
                );
                menuItem.addActionListener(event -> ui.locFunctionsOff(ui.getButtonLocomotive(source)));
                keyed(menuItem, "functions off");
                add(menuItem);

                addSeparator();

                // Shared with the locomotive database's menu; the key belongs to this menu
                menuItem = LocomotiveMenuItems.multiUnit(ui, ui.getButtonLocomotive(source));

                keyed(menuItem, "linked locomotives");

                add(menuItem);

                addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuClearButtonCut")
                );
                menuItem.addActionListener(event -> ui.setCopyTarget(source, true));
                keyed(menuItem, "cut the button");
                add(menuItem);
                
                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuClearButton")
                );
                keyed(menuItem, "clear the button");
                // Doing this twice effectively clears the clipboard
                menuItem.addActionListener(event -> { 
                    ui.setCopyTarget(source, true); 
                    ui.setCopyTarget(source, true); 
                });
                add(menuItem);
            }
        }
    }
}
   