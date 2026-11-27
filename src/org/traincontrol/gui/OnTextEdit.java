package org.traincontrol.gui;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A document listener that tells one callback about any edit. */
final class OnTextEdit
{
    private OnTextEdit()
    {
    }

    static DocumentListener tell(Runnable onEdit)
    {
        return new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent e) { onEdit.run(); }
            @Override public void removeUpdate(DocumentEvent e) { onEdit.run(); }
            @Override public void changedUpdate(DocumentEvent e) { onEdit.run(); }
        };
    }
}
