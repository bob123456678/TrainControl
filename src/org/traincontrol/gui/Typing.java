package org.traincontrol.gui;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A document listener that tells one callback about any edit. */
final class Typing
{
    private Typing()
    {
    }

    /** Runs this when the component is shown, to give a field the keyboard. */
    static void onShown(javax.swing.JComponent component, Runnable then)
    {
        component.addAncestorListener(new javax.swing.event.AncestorListener()
        {
            @Override public void ancestorRemoved(javax.swing.event.AncestorEvent event) { }
            @Override public void ancestorMoved(javax.swing.event.AncestorEvent event) { }
            @Override public void ancestorAdded(javax.swing.event.AncestorEvent event) { then.run(); }
        });
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
