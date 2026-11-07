package support;

import java.awt.Color;
import java.awt.Container;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.traincontrol.gui.widgets.UiStyle;

/** The standard's fonts and colors (docs/UI-standards.md), asserted on a window's parts.  Not a test. */
public final class Looks
{
    private Looks()
    {
    }

    /** Every push or toggle button with words on it is Bold 12; a one-glyph button (plus, minus, close) is exempt. */
    public static void assertButtonsAreBold12(Container root, String where)
    {
        java.util.List<AbstractButton> all = Components.all(root, AbstractButton.class, b ->
            (b instanceof javax.swing.JButton || b instanceof javax.swing.JToggleButton)
            && !(b instanceof javax.swing.JCheckBox) && !(b instanceof javax.swing.JRadioButton)
            && b.getText() != null && b.getText().length() > 1);

        assertTrue(!all.isEmpty(), where + " has no buttons to look at");

        for (AbstractButton b : all)
        {
            assertEquals(b.getFont(), UiStyle.BUTTON_FONT, where + ": '" + b.getText() + "' is not Bold 12");
        }
    }

    /** The label with this text (colon ignored) is Semibold 13 in this color. */
    public static void assertLabel(Container root, String text, Color color, String where)
    {
        JLabel l = Components.find(root, JLabel.class, x -> x.getText() != null && text.equals(UiStyle.withoutColon(x.getText())));

        assertNotNull(l, where + " has no label '" + text + "'");
        assertEquals(l.getFont(), UiStyle.LABEL_FONT, where + ": '" + text + "' is not Semibold 13");
        assertEquals(l.getForeground(), color, where + ": '" + text + "' is the wrong color");
    }
}
