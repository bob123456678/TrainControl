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

        report.append(System.lineSeparator()).append("components with text inspected:")
            .append(System.lineSeparator());

        for (Map.Entry<String, Integer> e : inspected.entrySet())
        {
            report.append("   ").append(e.getKey()).append("  ").append(e.getValue())
                .append(System.lineSeparator());
        }

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

            report.append(System.lineSeparator()).append("of those, how many the locale changes:")
                .append(System.lineSeparator());

            for (Map.Entry<String, Integer> e : moved.entrySet())
            {
                report.append("   ").append(e.getKey()).append("  ").append(e.getValue())
                    .append(System.lineSeparator());
            }
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

        assertEquals(total, 0,
            "text is clipped - the full list is in " + new File(OUT, "clipped.txt")
            + System.lineSeparator() + report);
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

                // Then the size the form declares, the window an operator gets on first run (its minimum size); pack()
                // would give whatever the longest string in THIS language needs, which is the question rather than the
                // answer.
                ui[0].setSize(ui[0].getMinimumSize());
                ui[0].validate();
            });

            final List<String> found = new ArrayList<>();

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                // No container-level checks: the per-component rule catches a component whose width is FIXED, since a
                // free layout grants the wider preferred size and the overflow lands elsewhere.
                found.addAll(LanguageFit.measure(LEDGER, "window", language, ui[0], inspected));

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
