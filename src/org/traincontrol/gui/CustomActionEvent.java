package org.traincontrol.gui;

import java.awt.event.ActionEvent;

/**
 * Used to passed custom data to manually triggered event listeners
 */
public class CustomActionEvent extends ActionEvent
{
    private final Object customData;

    public CustomActionEvent(Object source, int id, String command, Object customData)
    {
        super(source, id, command);
        this.customData = customData;
    }

    public Object getCustomData()
    {
        return customData;
    }
}