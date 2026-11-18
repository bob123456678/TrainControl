package ui;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuBar;
import javax.swing.UIManager;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import support.Display;
import support.LanguageFit;
import support.LayoutSandbox;
import support.Reflect;

/**
 * The menus are never smaller than the window's main text (14), whatever FlatLaf's own size is: at a scale of 1 its menu font
 * is 12 (MT-1053).  FlatLaf keeps one scale per JVM, so this class runs the application's own `installLookAndFeel` for a
 * display of 96 dpi (`tc.dpi`).
 *
 * @author Adam
 */
public class testMenusAreNoSmallerThanTheWindowsText
{
    private static final String[] KEYS =
    {
        "Menu.font", "MenuBar.font", "MenuItem.font", "CheckBoxMenuItem.font", "RadioButtonMenuItem.font", "PopupMenu.font",
        "Menu.acceleratorFont", "MenuItem.acceleratorFont", "CheckBoxMenuItem.acceleratorFont",
        "RadioButtonMenuItem.acceleratorFont"
    };

    private String dpiWas;

    private String scaleWas;

    /** The application's own install, on a display of 96 dpi (a scale of 1), which `tc.dpi` stands in for. */
    @BeforeClass
    public void installAtScaleOne()
    {
        Display.needed("a look and feel needs a display");

        dpiWas = System.getProperty("tc.dpi");
        scaleWas = System.getProperty("flatlaf.uiScale");

        System.setProperty("tc.dpi", "96");

        TrainControlUI.installLookAndFeel();
    }

    @AfterClass(alwaysRun = true)
    public void putThePropertiesBack()
    {
        restore("tc.dpi", dpiWas);
        restore("flatlaf.uiScale", scaleWas);
    }

    private static void restore(String name, String was)
    {
        if (was == null) System.clearProperty(name);
        else System.setProperty(name, was);
    }

    private static void assertScaleOne()
    {
        assertTrue(UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatLightLaf, "precondition: installed");
        assertEquals(com.formdev.flatlaf.util.UIScale.getUserScaleFactor(), 1f, 0.001f, "precondition: a scale of 1");
    }

    private static void assertMenusAtLeastTheWindowsText()
    {
        for (String key : KEYS)
        {
            assertTrue(UIManager.getFont(key).getSize() >= TrainControlUI.MENU_FONT_MIN,
                key + " is " + UIManager.getFont(key).getSize() + ", smaller than the window's text at a scale of 1");
        }
    }

    /**
     * At a scale of 1 every menu font is 14, not FlatLaf's 12; a larger one stays as it is.
     *
     * MUTATION: removing the `enlargeTheMenus();` call from `installLookAndFeel` leaves 12 and fails; a step that sets 14
     * whatever the size shrinks the 15 and fails the last assertion.
     */
    @Test
    public void testAMenuIsNeverSmallerThanTheWindowsText() throws Exception
    {
        assertScaleOne();

        assertMenusAtLeastTheWindowsText();

        // What 1.25 and 1.5 give FlatLaf (15 and 18) is kept; the fonts are put back after.
        java.util.Map<String, Font> was = new java.util.LinkedHashMap<>();

        for (String key : KEYS) was.put(key, UIManager.getFont(key));

        try
        {
            for (int size : new int[] {15, 18})
            {
                for (String key : KEYS)
                {
                    UIManager.put(key, new javax.swing.plaf.FontUIResource(was.get(key).deriveFont((float) size)));
                }

                Reflect.method(TrainControlUI.class, "enlargeTheMenus").invoke(null);

                for (String key : KEYS)
                {
                    assertEquals(UIManager.getFont(key).getSize(), size, key + " was shrunk from " + size);
                }
            }
        }
        finally
        {
            for (String key : KEYS) UIManager.put(key, new javax.swing.plaf.FontUIResource(was.get(key)));
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

        assertScaleOne();

        assertMenusAtLeastTheWindowsText();

        LayoutSandbox sandbox = null;

        List<String> found = new ArrayList<>();

        try
        {
            sandbox = LayoutSandbox.open();

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

            if (sandbox != null) sandbox.close();
        }

        assertTrue(found.isEmpty(), "a menu does not fit with 14-point menus at a scale of 1: " + found);
    }
}
