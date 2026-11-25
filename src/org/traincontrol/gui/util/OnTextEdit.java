package org.traincontrol.gui.util;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A document listener that tells one callback about any edit. */
public final class OnTextEdit
{
    private OnTextEdit()
    {
    }

    public static DocumentListener tell(Runnable onEdit)
    {
        return new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent e) { onEdit.run(); }
            @Override public void removeUpdate(DocumentEvent e) { onEdit.run(); }
            @Override public void changedUpdate(DocumentEvent e) { onEdit.run(); }
        };
    }
}
