package regression;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * Two windows, and no Control key doing two things in either of them.
 *
 * FR-066 asked for Control+D, which is taken twice over (the layout editor toggles addresses with it, the main window
 * opens the locomotive adder), so the answer to "which key" was a list measured off both key handlers by reading them;
 * Adam chose **E**.  This class does that reading on every run.  A collision is invisible: both are `else if` chains on
 * one keycode, so the second branch is never reached, and the shortcut that stops working is the one added last.
 *
 * **What this is, and what it is not.**  It is a source-shape guard and knows only the spellings the two files use
 * (`evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X`, `e.` in the editor, `controlPressed && keyCode ==
 * KeyEvent.VK_X` in the main window), so `testItFoundTheHandlersItThinksItIsReading` asserts a floor on how many it
 * found: a regex that silently stopped matching would otherwise report a clean bill of health.
 *
 * It does NOT require the two windows to agree (Control+S means Rename in one and Swap in the other); only that neither
 * window has two answers to one key.
 *
 * **THE MAIN WINDOW HAD NO FREE LETTERS AT ALL UNTIL OB-197.**  Its chain ended `else if
 * (this.buttonMapping.containsKey(keyCode))` with no `!controlPressed`, so Control plus any letter not caught above
 * selected a locomotive button.  Adam: "it should stop doing that.  that was not intended."
 * `testTheFallThroughStillFiltersControl` keeps it filtering, because everything this class prints about free keys
 * rests on it.
 *
 * @author Adam
 */
public class testNoTwoShortcutsShareAKey
{
    /**
     * The layout and autonomy editor, where FR-066's Control+E lives: named by its window class, the key of the
     * allowances below.  Its shortcuts are read from its own key handlers, found by declaration wherever they are kept.
     */
    private static final String EDITOR = "LayoutEditor.java";

    /** The main window, named the same way. */
    private static final String MAIN = "TrainControlUI.java";

    /**
     * Each window's key handlers, so "the editor" means the editor's handlers and not every file that spells a shortcut
     * the editor's way (FR-136).  `formKeyPressed` is a name more than one window gives its handler, so the editor's is
     * asked of its class.
     */
    private static final Map<String, String[][]> HANDLERS = new LinkedHashMap<>();

    static
    {
        HANDLERS.put(EDITOR, new String[][] {
            {"public class LayoutEditor extends PositionAwareJFrame",
                "private void formKeyPressed(java.awt.event.KeyEvent evt)"},
            {null, "public void receiveKeyEvent(KeyEvent e, LayoutLabel label)"}});

        HANDLERS.put(MAIN, new String[][] {
            {null, "private void LocControlPanelKeyPressed("},
            {null, "private boolean locomotiveGestureOnDiagram("}});
    }

    /**
     * `evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X`, and the same with `e.`: the Control half first, the
     * only order either file writes (the floor catches a spelling going unmatched).  Both names, because
     * `formKeyPressed` calls its argument `evt` and `receiveKeyEvent` calls it `e`.
     */
    private static final Pattern IN_THE_EDITOR = Pattern.compile(
        "\\be(?:vt)?\\.isControlDown\\(\\)\\s*&&\\s*e(?:vt)?\\.getKeyCode\\(\\)"
        + "\\s*==\\s*KeyEvent\\.VK_([A-Z0-9_]+)");

    /** `controlPressed && keyCode == KeyEvent.VK_X`. */
    private static final Pattern IN_THE_MAIN_WINDOW = Pattern.compile(
        "controlPressed\\s*&&\\s*keyCode\\s*==\\s*KeyEvent\\.VK_([A-Z0-9_]+)");

    /**
     * The keys one file binds twice on purpose, as `File.java#KEY`; each is a claim somebody has to defend.
     *
     * **A second binding is not automatically a collision.**  `TrainControlUI.locomotiveGestureOnDiagram` is asked
     * FIRST and returns true when it has handled the key, so Control+X and Control+V mean the square under the pointer
     * while it rests on the track diagram, and the locomotive button elsewhere.  Delete is here because the diagram
     * layer takes it WITHOUT Control and the else-if chain WITH, so they never contend, and the regex sees only the key
     * name.
     */
    private static final Set<String> BOUND_TWICE_ON_PURPOSE = new LinkedHashSet<>(java.util.Arrays.asList(
        "TrainControlUI.java#X", "TrainControlUI.java#V", "TrainControlUI.java#DELETE",

        // `LayoutEditor.receiveKeyEvent` is a SECOND entry point, not a second branch: it has no caller today and
        // returns at once in autonomy mode.
        "LayoutEditor.java#V"));

    /**
     * How many Control shortcuts each file is known to bind: a floor rather than a count, so adding one is ordinary
     * while a regex that stopped matching drops towards nothing and fails.  Measured by running the regexes (20 in the
     * editor, 16 in the main window) and set a little below; floors of 8 and 12 had let half the editor's handlers go
     * invisible.
     */
    private static final int EDITOR_FLOOR = 17;

    private static final int MAIN_FLOOR = 14;

    /** No key does two things in the layout editor. */
    @Test
    public void testTheEditorHasOneAnswerPerKey() throws IOException
    {
        assertEquals(duplicatesIn(EDITOR, IN_THE_EDITOR), new TreeSet<String>(),
            "these Control shortcuts are bound twice in " + EDITOR + ": "
            + duplicatesIn(EDITOR, IN_THE_EDITOR) + ". Both branches test the same keycode in one"
            + " else-if chain, so the second is unreachable and the shortcut that stops working is"
            + " whichever was added last - silently");
    }

    /** And none does in the main window. */
    @Test
    public void testTheMainWindowHasOneAnswerPerKey() throws IOException
    {
        assertEquals(duplicatesIn(MAIN, IN_THE_MAIN_WINDOW), new TreeSet<String>(),
            "these Control shortcuts are bound twice in " + MAIN + ": "
            + duplicatesIn(MAIN, IN_THE_MAIN_WINDOW) + ". See the sibling test for why that is silent");
    }

    /**
     * The regexes still match the handlers they were written against: without it the two claims above pass by finding
     * nothing.
     */
    @Test
    public void testItFoundTheHandlersItThinksItIsReading() throws IOException
    {
        List<String> editor = keysIn(EDITOR, IN_THE_EDITOR);
        List<String> main = keysIn(MAIN, IN_THE_MAIN_WINDOW);

        assertTrue(editor.size() >= EDITOR_FLOOR,
            "only " + editor.size() + " Control shortcuts were found in " + EDITOR + " (" + editor
            + "), against the " + EDITOR_FLOOR + " this was written against. The spelling has changed"
            + " and the claims above are about almost nothing");

        assertTrue(main.size() >= MAIN_FLOOR,
            "only " + main.size() + " Control shortcuts were found in " + MAIN + " (" + main
            + "), against the " + MAIN_FLOOR + " this was written against");

        assertTrue(editor.contains("E"),
            "Control+E is not bound in " + EDITOR + " any more. It is FR-066, the shortcut Adam picked"
            + " himself for setting a square's length, and it found: " + editor);

        assertTrue(editor.contains("S"),
            "Control+S is not bound in " + EDITOR + " any more - MT-257 item 4, naming the square"
            + " under the pointer. Found: " + editor);
    }

    /**
     * What is still free in BOTH windows, printed rather than asserted, so the next "which key" is answered off a run
     * instead of a reading.
     */
    @Test
    public void testTheFreeKeysArePrinted() throws IOException
    {
        Set<String> inTheEditor = new LinkedHashSet<>(keysIn(EDITOR, IN_THE_EDITOR));

        Set<String> inTheMainWindow = new LinkedHashSet<>(keysIn(MAIN, IN_THE_MAIN_WINDOW));

        StringBuilder freeInTheEditor = new StringBuilder();

        StringBuilder unclaimed = new StringBuilder();

        for (char c = 'A'; c <= 'Z'; c++)
        {
            String key = String.valueOf(c);

            if (!inTheEditor.contains(key)) freeInTheEditor.append(" ").append(key);

            if (!inTheEditor.contains(key) && !inTheMainWindow.contains(key))
            {
                unclaimed.append(" ").append(key);
            }
        }

        System.out.println("### Control shortcuts");
        System.out.println("  bound in the layout/autonomy editor :" + new TreeSet<>(inTheEditor));
        System.out.println("  bound in the main window            :" + new TreeSet<>(inTheMainWindow));
        System.out.println("  free in the editor                  :" + freeInTheEditor);
        System.out.println("  free in BOTH                        :" + unclaimed);

        assertTrue(freeInTheEditor.length() > 0,
            "every letter of the alphabet is bound to a Control shortcut in the layout editor, which"
            + " is either a remarkable application or a regex matching things it should not: "
            + new TreeSet<>(inTheEditor));

        assertTrue(inTheEditor.size() < 26,
            "the editor binds " + inTheEditor.size() + " letters, which cannot be fewer than the 26"
            + " it would take to make the claim above impossible - so that claim is unfalsifiable"
            + " and this one says so");
    }

    /**
     * The main window's last arm takes a bare letter and not a Control chord (OB-197): `buttonMapping` holds all 26
     * letters, so without `!controlPressed` Control plus every letter the arms above do not catch selected a locomotive
     * button.  Everything this class says about free keys rests on this arm, so it is asserted rather than assumed.
     */
    @Test
    public void testTheFallThroughStillFiltersControl() throws IOException
    {
        assertTrue(SourceOf.allCode().contains("!controlPressed && this.buttonMapping.containsKey(keyCode)"),
            "the main window's last arm no longer filters Control, so Control plus every letter no"
            + " named shortcut catches selects a locomotive button again - and the free-key list this"
            + " class prints is wrong about all of them. That is OB-197 coming back");
    }

    /** Every Control key bound in one window's key handlers, handler by handler, with repeats. */
    private List<String> keysIn(String window, Pattern how) throws IOException
    {
        List<String> out = new ArrayList<>();

        for (String[] handler : HANDLERS.get(window))
        {
            String text = handler[0] == null ? SourceOf.declaration(handler[1])
                : SourceOf.declarationIn(SourceOf.declaration(handler[0]), handler[1]);

            Matcher found = how.matcher(SourceOf.withoutComments(text));

            while (found.find()) out.add(found.group(1));
        }

        return out;
    }

    /**
     * Every allowance names a key that really is bound twice: an allowance whose case has gone is one nobody sees
     * expiring.
     */
    @Test
    public void testNoAllowanceHasOutlivedItsCase() throws IOException
    {
        Set<String> stale = new TreeSet<>(BOUND_TWICE_ON_PURPOSE);

        for (String key : keysIn(MAIN, IN_THE_MAIN_WINDOW))
        {
            stale.remove("TrainControlUI.java#" + key);
        }

        for (String key : keysIn(EDITOR, IN_THE_EDITOR))
        {
            stale.remove("LayoutEditor.java#" + key);
        }

        // Removed once per APPEARANCE, so a key bound once is still removed; one bound once has no second binding to
        // allow, which the count below catches.
        assertEquals(stale, new TreeSet<String>(),
            "these allowances name keys nothing binds any more: " + stale + ". An allowance that has"
            + " outlived its case admits the next real collision on that key without a word");

        for (String allowed : BOUND_TWICE_ON_PURPOSE)
        {
            String key = allowed.substring(allowed.indexOf('#') + 1);

            List<String> found = allowed.startsWith("TrainControlUI")
                ? keysIn(MAIN, IN_THE_MAIN_WINDOW) : keysIn(EDITOR, IN_THE_EDITOR);

            int times = 0;

            for (String each : found)
            {
                if (each.equals(key)) times++;
            }

            assertTrue(times > 1,
                allowed + " is allowed to be bound twice and is bound " + times + " time(s), so the"
                + " allowance is doing nothing and should go");
        }
    }

    /** The keys that appear more than once, sorted. */
    private Set<String> duplicatesIn(String window, Pattern how) throws IOException
    {
        Map<String, Integer> counted = new LinkedHashMap<>();

        for (String key : keysIn(window, how))
        {
            counted.put(key, counted.containsKey(key) ? counted.get(key) + 1 : 1);
        }

        Set<String> twice = new TreeSet<>();

        String file = window;

        for (Map.Entry<String, Integer> entry : counted.entrySet())
        {
            if (entry.getValue() <= 1) continue;

            if (BOUND_TWICE_ON_PURPOSE.contains(file + "#" + entry.getKey())) continue;

            twice.add(entry.getKey());
        }

        return twice;
    }
}
