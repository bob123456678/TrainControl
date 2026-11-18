package ui;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuBar;
import javax.swing.UIManager;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import support.Display;
import support.LanguageFit;
import support.LayoutSandbox;
import support.Reflect;

/**
 * The menus are never smaller than the window's main text (14), whatever FlatLaf's own size is: at a scale of 1 its menu font
 * is 12 (MT-1053).  FlatLaf keeps one scale per JVM, so this class installs the look and feel at a scale of 1 itself, as
 * `installLookAndFeel` does, and asks for the menu step.
 *
 * @author Adam
 */
public class testMenusAreNoSmallerThanTheWindowsText
{
    private static final String[] KEYS =
    {
        "Menu.font", "MenuBar.font", "MenuItem.font", "CheckBoxMenuItem.font", "RadioButtonMenuItem.font", "PopupMenu.font"
    };

    private static void enlarge() throws Exception
    {
        Reflect.method(TrainControlUI.class, "enlargeTheMenus").invoke(null);
    }

    /** Every menu font at the size FlatLaf gives at a scale of 1, as a claim before may have left them. */
    private static void flatLafsOwn()
    {
        for (String key : KEYS)
        {
            UIManager.put(key, new javax.swing.plaf.FontUIResource(UIManager.getFont(key).deriveFont(12f)));
        }
    }

    /** The look and feel at a scale of 1, as the application installs it, but for the menu step, which the claims run. */
    private static void installAtScaleOne()
    {
        if (UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatLightLaf) return;

        System.setProperty("flatlaf.uiScale", "1");

        com.formdev.flatlaf.FlatLightLaf.setup();

        UIManager.put("TitlePane.menuBarEmbedded", TrainControlUI.MENUS_IN_THE_TITLE_BAR);
        UIManager.put("TabbedPane.tabWidthMode", "compact");
        UIManager.put("TabbedPane.tabInsets", new java.awt.Insets(8, 8, 8, 8));
    }

    /**
     * At a scale of 1 every menu font is 14, not FlatLaf's 12; a larger one stays as it is.
     *
     * MUTATION: no step in `enlargeTheMenus` leaves 12 and fails the first assertion; a step that sets 14 whatever the
     * size shrinks the 15 and fails the last.
     */
    @Test
    public void testAMenuIsNeverSmallerThanTheWindowsText() throws Exception
    {
        Display.needed("a look and feel needs a display");

        installAtScaleOne();

        assertEquals(com.formdev.flatlaf.util.UIScale.getUserScaleFactor(), 1f, 0.001f, "precondition: a scale of 1");

        flatLafsOwn();

        enlarge();

        for (String key : KEYS)
        {
            assertEquals(UIManager.getFont(key).getSize(), TrainControlUI.MENU_FONT_MIN,
                key + " is smaller than the window's text at a scale of 1 (12 is FlatLaf's own)");
        }

        // What 1.25 and 1.5 give FlatLaf (15 and 18) is kept.
        for (int size : new int[] {15, 18})
        {
            for (String key : KEYS)
            {
                UIManager.put(key, new javax.swing.plaf.FontUIResource(UIManager.getFont(key).deriveFont((float) size)));
            }

            enlarge();

            for (String key : KEYS)
            {
                assertEquals(UIManager.getFont(key).getSize(), size, key + " was shrunk from " + size);
            }
        }
    }

    /**
     * With 14-point menus at a scale of 1, the menu bar and every menu fit in every language.  Measured as
     * `testEveryLanguageFits` does, on the window laid out at its minimum, and the menu bar's own widths are listed.
     */
    @Test
    public void testTheMenuBarFitsInEveryLanguageAtScaleOne() throws Exception
    {
        Display.needed("a window has to be laid out to be measured");

        installAtScaleOne();

        flatLafsOwn();

        enlarge();

        LayoutSandbox sandbox = LayoutSandbox.open();

        List<String> found = new ArrayList<>();

        try
        {
            for (String language : LanguageFit.LANGUAGES)
            {
                LanguageFit.speak(language);

                final TrainControlUI[] ui = new TrainControlUI[1];

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    ui[0] = new TrainControlUI();
                    ui[0].pack();
                    ui[0].setMinimumSize(ui[0].getSize());
                    ui[0].setSize(ui[0].getMinimumSize());
                    ui[0].validate();

                    JMenuBar bar = ui[0].getRootPane().getJMenuBar();

                    Font font = bar.getMenu(0).getFont();

                    System.out.println("FIT " + language + ": window " + ui[0].getWidth() + ", menu bar wants "
                        + bar.getPreferredSize().width + " has " + bar.getWidth() + ", font " + font.getSize());

                    for (String one : LanguageFit.measure(new LanguageFit.Ledger(), "window", language, ui[0], new int[1]))
                    {
                        if (one.contains("menu")) found.add(language + ": " + one);
                    }
                });

                javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].dispose());
            }
        }
        finally
        {
            LanguageFit.speak("en");

            sandbox.close();
        }

        assertTrue(found.isEmpty(), "a menu does not fit with 14-point menus at a scale of 1: " + found);
    }
}
