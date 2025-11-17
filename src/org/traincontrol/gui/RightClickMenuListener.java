package org.traincontrol.gui;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.marklin.MarklinLocomotive;
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
                    I18n.f("loc.ui.menuCopyLocomotive", ui.getButtonLocomotive(source).getName())
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
                    I18n.f("loc.ui.menuPasteLocomotive", ui.getCopyTarget().getName())
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
                addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuCopyToNextPage")
                );
                menuItem.addActionListener(event -> ui.copyToNextPage(source));
                add(menuItem);

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuCopyToPreviousPage")
                );
                menuItem.addActionListener(event -> ui.copyToPrevPage(source));
                add(menuItem);

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
                    !((MarklinLocomotive) ui.getButtonLocomotive(source)).hasLinkedLocomotives()
                        ? I18n.t("loc.ui.menuSetAsMultiUnit")
                        : I18n.t("loc.ui.menuEditMultiUnitLocomotives")
                );
                menuItem.addActionListener(event -> ui.changeLinkedLocomotives((MarklinLocomotive) ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Control+L");

                if (((MarklinLocomotive) ui.getButtonLocomotive(source)).getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT)
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

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuSetLocalLocomotiveIcon")
                );
                menuItem.addActionListener(event -> ui.setLocIcon(ui.getButtonLocomotive(source)));
                submenu.add(menuItem);

                if (ui.getButtonLocomotive(source) != null && ui.getButtonLocomotive(source).getLocalImageURL() != null)
                {
                    menuItem = new JMenuItem(
                        I18n.t("loc.ui.menuClearLocalLocomotiveIcon")
                    );
                    menuItem.addActionListener(event -> ui.clearLocIcon(ui.getButtonLocomotive(source)));
                    submenu.add(menuItem);
                }

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuCustomizeFunctionIcons")
                );
                menuItem.addActionListener(event -> ui.setFunctionIcon(ui.getButtonLocomotive(source), source, null));
                submenu.add(menuItem);
                submenu.addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuEditNameAddressDecoder")
                );
                menuItem.addActionListener(event -> ui.changeLocAddress((MarklinLocomotive) ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Control+R");
                submenu.add(menuItem);

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuEditNotes")
                );
                menuItem.addActionListener(event -> ui.changeLocNotes(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Control+N");
                submenu.add(menuItem);

                submenu.addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuFindSimilarLocomotives")
                );
                menuItem.addActionListener(event -> ui.findSimilarLocs(ui.getButtonLocomotive(source), null));
                menuItem.setToolTipText(I18n.t("loc.ui.tooltip.findSimilarHint"));
                submenu.add(menuItem);

                submenu.addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuDeleteFromDatabase")
                );
                menuItem.setForeground(Color.RED);
                menuItem.setToolTipText("Control+Delete");
                menuItem.addActionListener(event -> ui.deleteLoc(ui.getButtonLocomotive(source).getName()));
                submenu.add(menuItem);

                add(submenu);

                addSeparator();

                menuItem = new JMenuItem(
                    I18n.t("loc.ui.menuClearButtonCut")
                );
                menuItem.addActionListener(event -> ui.setCopyTarget(source, true));
                menuItem.setToolTipText("Control+X");
                add(menuItem);
            }
        }
    }
}
   