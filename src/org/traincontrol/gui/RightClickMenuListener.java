package org.traincontrol.gui;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu with various utility functions displayed when any locomotive button is right-clicked
 * @author Adam
 */
public class RightClickMenuListener extends MouseAdapter
{    
    protected TrainControlUI ui;
    protected JButton source;
    
    public RightClickMenuListener(TrainControlUI u, JButton source)
    {
        this.ui = u;
        this.source = source;
    }
    
    @Override
    public void mousePressed(MouseEvent e)
    {
        if (e.isPopupTrigger()) showPopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        if (e.isPopupTrigger()) showPopup(e);
    }

    private void showPopup(MouseEvent e)
    {
        RightClickMenu menu = new RightClickMenu(ui);
        menu.show(e.getComponent(), e.getX(), e.getY());
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
            menuItem.setToolTipText("Control+A");
            add(menuItem);

            addSeparator();

            // Option to copy
            if (ui.buttonHasLocomotive(source))
            {
                menuItem = new JMenuItem(
                    I18n.f("loc.ui.menuCopyLocomotiveNamed", ui.getButtonLocomotive(source).getName())
                );
                menuItem.addActionListener(event -> ui.setCopyTarget(source, false));
                menuItem.setToolTipText("Control+C");
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
                menuItem.setToolTipText("Control+V");
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.f("loc.ui.menuMoveLocomotive", ui.getCopyTarget().getName())
                );
                menuItem.addActionListener(event -> ui.doPaste(source, false, true));
                menuItem.setToolTipText("Control+B");
                add(menuItem);

                if (ui.getSwapTarget() != null
                    && ui.getButtonLocomotive(source) != null
                    && !ui.getButtonLocomotive(source).getName().equals(ui.getCopyTarget().getName()))
                {
                    menuItem = new JMenuItem(
                        I18n.f("loc.ui.menuSwapLocomotive", ui.getCopyTarget().getName())
                    );
                    menuItem.addActionListener(event -> ui.doPaste(source, true, false));
                    menuItem.setToolTipText("Control+S");
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
                // We no longer need these since users can just drag or copy entire pages
                /*addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuCopyToNextPage")
                );
                menuItem.addActionListener(event -> ui.copyToNextPage(source));
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuCopyToPreviousPage")
                );
                menuItem.addActionListener(event -> ui.copyToPrevPage(source));
                add(menuItem);*/

                addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuApplySavedFunctionPreset")
                );
                menuItem.addActionListener(event -> ui.applyPreferredFunctions(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-P");
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.f("loc.ui.menuApplySavedSpeedPreset", ui.getButtonLocomotive(source).getPreferredSpeed())
                );
                menuItem.addActionListener(event -> ui.applyPreferredSpeed(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-V");
                add(menuItem);

                addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuSaveFunctionsAsPreset")
                );
                menuItem.addActionListener(event -> ui.savePreferredFunctions(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-S");
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuSaveSpeedAsPreset")
                );
                menuItem.addActionListener(event -> ui.savePreferredSpeed(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-U");
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuTurnOffFunctions")
                );
                menuItem.addActionListener(event -> ui.locFunctionsOff(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-O");
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuSyncCentralStation")
                );
                menuItem.addActionListener(event -> ui.syncLocomotive(ui.getButtonLocomotive(source)));
                add(menuItem);

                addSeparator();

                menuItem = new JMenuItem(
                    !(ui.getButtonLocomotive(source)).hasLinkedLocomotives()
                        ? I18n.t("loc.ui.menuSetAsMultiUnit")
                        : I18n.t("loc.ui.menuEditMultiUnitLocomotives")
                );
                menuItem.addActionListener(event -> ui.changeLinkedLocomotives((Locomotive) ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Control+L");

                if ((ui.getButtonLocomotive(source)).getDecoderType() == Locomotive.decoderType.MULTI_UNIT)
                {
                    menuItem.setText(
                        I18n.t("loc.ui.menuViewMultiUnitLocomotives")
                    );
                }
                add(menuItem);

                addSeparator();

                JMenu submenu = new JMenu(
                    I18n.t("loc.ui.submenuManageLocomotive")
                );

                menuItem = new JMenuItem(ui.getButtonLocomotive(source).getName());
                menuItem.setEnabled(false);
                submenu.add(menuItem);
                submenu.addSeparator();

                // SHARED WITH THE LOCOMOTIVE DATABASE MENU (FR-047).
                //
                // These seven items were written out twice, here and in RightClickSelectorMenu, and
                // had already drifted. They live in LocomotiveMenuItems now; the ORDER and the
                // keyboard tooltips stay here, because those belong to this menu rather than to the
                // locomotive.
                //
                // One deliberate difference: the locomotive is resolved ONCE, as the menu is built,
                // where this used to ask the button again inside every listener. That is the same
                // answer in every case that can happen with a popup open, and it is the better one -
                // the menu should act on what it was opened for.
                final org.traincontrol.base.Locomotive subject = ui.getButtonLocomotive(source);

                submenu.add(LocomotiveMenuItems.setLocalIcon(ui, subject, null));

                // Through `subject`, like every other line here.  This asked the button twice more,
                // three lines under the paragraph above saying the locomotive is resolved ONCE - the
                // comment had the intent right and this one line did not follow it (RC-C12).
                if (subject != null && subject.getLocalImageURL() != null)
                {
                    submenu.add(LocomotiveMenuItems.clearLocalIcon(ui, subject));
                }

                submenu.add(LocomotiveMenuItems.customiseFunctionIcons(ui, subject, source, null));
                submenu.addSeparator();

                menuItem = LocomotiveMenuItems.editNameAddressDecoder(ui, subject, null);
                menuItem.setToolTipText("Control+R");
                submenu.add(menuItem);

                menuItem = LocomotiveMenuItems.editNotes(ui, subject, null);
                menuItem.setToolTipText("Control+N");
                submenu.add(menuItem);

                // How long this train is, when there is autonomy to care (FR-047).  Null when there
                // is not, so the item simply is not there rather than being greyed.
                JMenuItem howLong = LocomotiveMenuItems.trainLength(ui, subject, null);

                if (howLong != null) submenu.add(howLong);

                submenu.addSeparator();

                submenu.add(LocomotiveMenuItems.findSimilar(ui, subject, null));

                submenu.addSeparator();

                menuItem = LocomotiveMenuItems.deleteFromDatabase(ui, subject, null);
                menuItem.setToolTipText("Control+Delete");
                submenu.add(menuItem);

                add(submenu);

                addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuClearButtonCut")
                );
                menuItem.addActionListener(event -> ui.setCopyTarget(source, true));
                menuItem.setToolTipText("Control+X");
                add(menuItem);
                
                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuClearButton")
                );
                menuItem.setToolTipText("Delete");
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
   