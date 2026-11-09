package org.traincontrol.gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Mouse adapters that hear one event and hand it to a lambda, for listeners that override a single method.  Three, no
 * more: a fourth of the same shape is a copy testNoNewCopies counts.
 */
public final class Listen
{
    private Listen()
    {
    }

    public static MouseAdapter clicked(Consumer<MouseEvent> then)
    {
        return new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { then.accept(e); } };
    }

    public static MouseAdapter pressed(Consumer<MouseEvent> then)
    {
        return new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { then.accept(e); } };
    }

    public static MouseAdapter released(Consumer<MouseEvent> then)
    {
        return new MouseAdapter() { @Override public void mouseReleased(MouseEvent e) { then.accept(e); } };
    }
}
