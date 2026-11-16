package ui;

import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JLabel;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import support.Display;
import support.LanguageFit;

/**
 * Every language fits in the window it was translated for: `testMessageBundles` checks that strings are CORRECT,
 * nothing else checks their LENGTH (German and Polish run half again as long as English).  A Swing component narrower
 * than its text draws it clipped, so one comparison per component, on a window laid out at the size the form declares.
 *
 * ONE sandbox around the whole class, fingerprinting `cs2_sample_layout` and failing on a single moved byte: a sandbox
 * per window let a disposed window's scheduled work write to Adam's real railway.
 *
 * Cannot see: panels filled from a railway (its siblings `testEveryLanguageFitsThe*` open those), containers that overflow (scroll panes legitimately hold wider content),
 * and the tab titles (IMAGES).  `LOCALISED_FLOOR` keeps the count of locale-changed components from becoming none.
 *
 * @author Adam
 */
public class testEveryLanguageFits
{
    private static final String[] LANGUAGES = LanguageFit.LANGUAGES;

    private static final File OUT = LanguageFit.OUT;

    private static final File LIVE = LanguageFit.LIVE;

    /** Every component the main window showed, for the comparison with English (`window-ratio.txt`). */
    private static final LanguageFit.Ledger LEDGER = new LanguageFit.Ledger();

    /**
     * How many text-bearing components a non-English run is expected to change: a FLOOR, to catch the locale failing to
     * reach the window, which would make every clean run a statement about English.
     */
    private static final int LOCALISED_FLOOR = 30;

    /** What each language's window packs to. */
    private static final Map<String, Integer> packedWidth = new LinkedHashMap<>();

    /** 3.1.0's main window in English, as packed here: no language may make it wider (D162). */
    private static final int WIDEST = 1129;

    /**
     * The rule can fail: the eight-language walk cannot carry its own control, so the RULE is controlled on a component
     * this method owns: a short string in 80 pixels fits, the same label with a long one does not.
     *
     * MUTATION: comparing `getPreferredSize().width` against `getWidth()` again fails the second assertion
     * (`setPreferredSize` is how the fixture fixes the width, which that comparison cannot see).
     */
    @Test
    public void testTheClippingRuleCanActuallyFail()
    {
        Display.needed("a font has to be measured");

        JLabel label = new JLabel("OK");

        // The width fixed the way the form fixes it, which is the case that defeated the old rule.
        label.setPreferredSize(new Dimension(80, 20));
        label.setSize(80, 20);

        assertFalse(LanguageFit.clips(label),
            "\"OK\" was called too wide for 80 pixels, so the rule refuses text that fits and every "
            + "finding below is suspect");

        label.setText("Eine Konfigurationseinstellungsmoeglichkeit die viel zu lang ist");

        assertTrue(LanguageFit.clips(label),
            "a 64-character string was called a fit in 80 pixels, so this test cannot see a clipped "
            + "label at all - which is what it was doing until 2026-09-11. The rule has to measure the "
            + "TEXT, because `setPreferredSize` has already decided what the component will ask for");
    }

    @Test
    public void testNoLabelIsClippedInAnyLanguage() throws Exception
    {
        Display.needed("a window has to be laid out to be measured");

        OUT.mkdirs();

        String railwayBefore = LanguageFit.fingerprint(LIVE);

        Locale was = Locale.getDefault();

        Map<String, List<String>> clipped = new LinkedHashMap<>();
        Map<String, Integer> inspected = new LinkedHashMap<>();

        // What each language puts on the window, so the reach of this test is a number rather than a hope.
        Map<String, Map<String, String>> localised = new LinkedHashMap<>();

        // ONE sandbox for the whole class, opened before any window and closed after the last: a window schedules work
        // that outlives dispose().
        support.LayoutSandbox sandbox = null;

        try
        {
            // Inside the try, so nothing can leave the preference behind.
            sandbox = support.LayoutSandbox.open();

            for (String language : LANGUAGES)
            {
                int[] seen = {0};

                clipped.put(language, measure(language, seen, localised));

                inspected.put(language, seen[0]);
            }
        }
        finally
        {
            Locale.setDefault(was);
            I18n.setLocale(was);

            // Let what the windows posted run first: work scheduled by a disposed window lands some milliseconds after
            // the last dispose, and fingerprinting before draining the queue samples the folder at the one moment that
            // cannot have seen it.
            try
            {
                for (int pass = 0; pass < 5; pass++)
                {
                    javax.swing.SwingUtilities.invokeAndWait(() -> { });
                }
            }
            catch (Exception draining)
            {
                // A queue that will not drain is not a reason to skip the check below.
            }

            if (sandbox != null) sandbox.close();

            // And in the finally, because the run most likely to have written is the one that threw.
            assertEquals(LanguageFit.fingerprint(LIVE), railwayBefore,
                "this test wrote to " + LIVE + ", which is the operator's real railway and is not "
                + "recoverable.  Whatever it found is beside the point until that is understood");
        }

        StringBuilder report = LanguageFit.report(clipped);

        int total = LanguageFit.total(clipped);

        counts(report, "components with text inspected:", inspected);

        // How many components the locale actually moves, the reach of this test: most of this form's text is baked into
        // the generated layout rather than passed through `I18n`, so a German run leaves it in English and it says
        // nothing about translation length.
        Map<String, String> english = localised.get("en");

        Map<String, Integer> moved = new LinkedHashMap<>();

        if (english != null)
        {
            for (Map.Entry<String, Map<String, String>> e : localised.entrySet())
            {
                if ("en".equals(e.getKey())) continue;

                int differs = 0;

                for (Map.Entry<String, String> one : e.getValue().entrySet())
                {
                    String inEnglish = english.get(one.getKey());

                    if (inEnglish != null && !inEnglish.equals(one.getValue())) differs++;
                }

                moved.put(e.getKey(), differs);
            }

            counts(report, "of those, how many the locale changes:", moved);
        }

        java.nio.file.Files.write(new File(OUT, "clipped.txt").toPath(),
            report.toString().getBytes("UTF-8"));

        LEDGER.write("window");

        // What was looked at, before what was found: a window that is not displayable lays nothing out, so every
        // component has a width of zero and every one is skipped. Both guards below fail on that.
        for (Map.Entry<String, Integer> e : inspected.entrySet())
        {
            assertTrue(e.getValue() >= 40,
                "only " + e.getValue() + " components with text were measured in " + e.getKey()
                + ", which is not a window - the layout did not happen and nothing was asked");
        }

        // The two pictures have to exist first: `sameBytes` answers false when either file is missing and `shoot`
        // swallows a write failure by design, so an unwritable OUT would satisfy the control below by there being
        // nothing to compare.
        assertTrue(new File(OUT, "window-en.png").isFile() && new File(OUT, "window-de.png").isFile(),
            "the screenshots this control compares were never written, so it would pass having "
            + "compared nothing.  Looked in " + OUT);

        assertFalse(LanguageFit.sameBytes(new File(OUT, "window-en.png"), new File(OUT, "window-de.png")),
            "the English and German windows are byte-identical, so the locale is not reaching the "
            + "text and all eight measurements are of the same language");

        // And a floor under the reach, which the screenshot control cannot give: two differing pictures prove the
        // locale reached SOMETHING, this says how many of the measured components are in the language being measured. A
        // floor, because translating one more label should not fail a test.
        for (Map.Entry<String, Integer> e : moved.entrySet())
        {
            assertTrue(e.getValue() >= LOCALISED_FLOOR,
                "only " + e.getValue() + " of the components this measures change when the language is "
                + e.getKey() + ", against " + LOCALISED_FLOOR + " when this was measured. Either the "
                + "locale has stopped reaching the window - in which case every clean run below is "
                + "clean about English - or text has moved out of the bundles and into the form");
        }

        // A component the window's edge or a neighbor covers is laid out wide enough, so the clipping rule below cannot
        // see it: OB-488, the Spanish power ON button 10 pixels under the window's edge.
        List<String> pushed = new ArrayList<>();

        for (LanguageFit.Ledger.Flag f : LEDGER.flags())
        {
            if (f.pushed)
            {
                pushed.add(f.language + " \"" + LanguageFit.oneLine(f.here.text) + "\" wants " + f.here.wants
                    + " and shows " + f.here.visible + " [" + f.path + "]");
            }
        }

        assertTrue(pushed.isEmpty(), "components are pushed partly out of sight at the window's minimum size: " + pushed);

        // The window is never wider than 3.1.0's English default (1129 px here), in any language (D162).
        for (String language : LANGUAGES)
        {
            assertTrue(packedWidth.get(language) <= WIDEST, language + " packs to " + packedWidth.get(language)
                + " px, wider than 3.1.0's English default of " + WIDEST);
        }

        // The layout above copies `display()`'s minimum; this keeps the two from drifting apart.
        String display = new String(java.nio.file.Files.readAllBytes(
            new File("src/org/traincontrol/gui/TrainControlUI.java").toPath()), "UTF-8");
        display = display.replace("\r\n", "\n");
        int packed = display.indexOf("        pack();\n        displayMenuBar();");
        assertTrue(packed >= 0 && display.indexOf(
            "setMinimumSize(new Dimension(this.getSize().width, this.getSize().height));", packed) > packed,
            "display() no longer packs and then takes the packed size as the minimum; update this test's layout to match");

        assertEquals(total, 0,
            "text is clipped - the full list is in " + new File(OUT, "clipped.txt")
            + System.lineSeparator() + report);
    }

    /**
     * What the locomotive panel (power buttons, speed, function buttons) cuts or pushes at the window's minimum width,
     * in ANY language, English included: the ledger's `pushed` flag compares with English, which hid a panel cut 20 px
     * in every language (OB-488).  A component is cut where its visible part is narrower than it is; pushed where it
     * overlaps another button or label of the panel.
     *
     * MUTATION: laying the window out at the form's declared 1110 (the test's `setMinimumSize(getSize())` removed) cuts the ON button, the ">>>>>>>" button and the F3/F7/F11 column, and this fails.
     */
    private static List<String> locomotivePanelCut(TrainControlUI ui, String language)
    {
        List<String> out = new ArrayList<>();

        try
        {
            java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("LocFunctionsPanel");

            field.setAccessible(true);

            java.awt.Container panel = (java.awt.Container) field.get(ui);
            List<java.awt.Component> leaves = new ArrayList<>();

            cutIn(panel, panel, language, leaves, out);

            for (int a = 0; a < leaves.size(); a++)
            {
                for (int b = a + 1; b < leaves.size(); b++)
                {
                    java.awt.Rectangle one = javax.swing.SwingUtilities.convertRectangle(leaves.get(a).getParent(),
                        leaves.get(a).getBounds(), panel);
                    java.awt.Rectangle two = javax.swing.SwingUtilities.convertRectangle(leaves.get(b).getParent(),
                        leaves.get(b).getBounds(), panel);
                    java.awt.Rectangle both = one.intersection(two);

                    if (both.width > LanguageFit.SLACK && both.height > LanguageFit.SLACK)
                    {
                        out.add(language + " locomotive panel: " + named(leaves.get(a)) + " is pushed under "
                            + named(leaves.get(b)));
                    }
                }
            }

            assertTrue(leaves.size() >= 20, "the locomotive panel held only " + leaves.size() + " buttons and "
                + "labels, so it was not laid out and nothing was measured");
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("the locomotive panel field is gone: " + e);
        }

        return out;
    }

    private static void cutIn(java.awt.Container panel, java.awt.Container in, String language,
        List<java.awt.Component> leaves, List<String> out)
    {
        for (java.awt.Component c : in.getComponents())
        {
            if (!c.isVisible() || !(c instanceof javax.swing.JComponent)) continue;

            javax.swing.JComponent each = (javax.swing.JComponent) c;

            if (each.getVisibleRect().width < each.getWidth() - LanguageFit.SLACK)
            {
                out.add(language + " locomotive panel: " + named(each) + " is cut, " + each.getVisibleRect().width
                    + " of " + each.getWidth() + " px show");
            }

            boolean leaf = each instanceof javax.swing.AbstractButton
                || each instanceof JLabel && !((JLabel) each).getText().trim().isEmpty();

            if (leaf) leaves.add(each);

            cutIn(panel, each, language, leaves, out);
        }
    }

    private static void counts(StringBuilder report, String title, Map<String, Integer> counts)
    {
        report.append(System.lineSeparator()).append(title).append(System.lineSeparator());

        for (Map.Entry<String, Integer> e : counts.entrySet())
        {
            report.append("   ").append(e.getKey()).append("  ").append(e.getValue()).append(System.lineSeparator());
        }
    }

    private static String named(java.awt.Component c)
    {
        String text = c instanceof javax.swing.AbstractButton ? ((javax.swing.AbstractButton) c).getText()
            : c instanceof JLabel ? ((JLabel) c).getText() : "";

        return c.getClass().getSimpleName() + " \"" + text + "\"";
    }

    /**
     * Builds the window in one language, writes its picture, and returns everything that does not fit; `inspected`
     * counts the components looked at so the caller can insist there were some.
     */
    private List<String> measure(String language, int[] inspected,
        Map<String, Map<String, String>> localised) throws Exception
    {
        LanguageFit.speak(language);

        final TrainControlUI[] ui = new TrainControlUI[1];

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                ui[0] = new TrainControlUI();

                // PACKED, which makes the window displayable; without it nothing is laid out and eight identical blank
                // pictures come out.
                ui[0].pack();

                packedWidth.put(language, ui[0].getWidth());

                // Then the window as the app leaves it: `display()` sets the minimum to the packed size, which is the
                // narrowest an operator can make it.  The form's declared 1110 is narrower than the locomotive panel needs
                // and is never what a user gets.
                ui[0].setMinimumSize(ui[0].getSize());
                ui[0].setSize(ui[0].getMinimumSize());
                ui[0].validate();
            });

            final List<String> found = new ArrayList<>();

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                // No container-level checks: the per-component rule catches a component whose width is FIXED, since a
                // free layout grants the wider preferred size and the overflow lands elsewhere.
                found.addAll(LanguageFit.measure(LEDGER, "window", language, ui[0], inspected));

                found.addAll(locomotivePanelCut(ui[0], language));

                localised.put(language, LanguageFit.textByPath(ui[0]));
            });

            return found;
        }
        finally
        {
            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
    }
}
