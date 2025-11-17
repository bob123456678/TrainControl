package org.traincontrol.gui;

import java.util.Objects;
import javax.swing.JButton;
import org.traincontrol.util.I18n;

/**
 * Helper class for main UI
 */
public class LocomotiveKeyboardMapping
{
    private final int page;
    private final JButton button;
    
    public LocomotiveKeyboardMapping(int p, JButton b)
    {
        this.button = b;
        this.page = p;
    }

    public int getPage()
    {
        return page;
    }

    public JButton getButton()
    {
        return button;
    }

    @Override
    public String toString()
    {
        return I18n.f(
            "loc.ui.buttonPageLabel",
            button.getText(),
            page
        );
    }
    
    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 47 * hash + this.page;
        hash = 47 * hash + Objects.hashCode(this.button);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        
        if (obj == null)
        {
            return false;
        }
        
        if (getClass() != obj.getClass())
        {
            return false;
        }
        
        final LocomotiveKeyboardMapping other = (LocomotiveKeyboardMapping) obj;
        if (this.page != other.page)
        {
            return false;
        }
        
        return Objects.equals(this.button, other.button);
    }
}
