package org.traincontrol.gui.widgets;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A mouse listener that shows a popup menu on the platform's popup trigger, which fires on press on some systems and on
 * release on others.
 */
public abstract class PopupListener extends MouseAdapter
{
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

    /**
     * Shows this listener's menu for a popup trigger.
     * @param e the trigger
     */
    protected abstract void showPopup(MouseEvent e);
}
