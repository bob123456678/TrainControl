package regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

/**
 * Two windows, and no Control key doing two things in either of them.
 *
 * **Adam picked a shortcut by hand on 2026-09-09 and had to be told what was free.**  FR-066 asked for
 * Control+D and Control+D is taken twice over - the layout editor toggles addresses with it, the main
 * window opens the locomotive adder - so the answer to *"which key"* was a list measured off both key
 * handlers by reading them.  He chose **E** from it.
 *
 * That reading is what this class does on every run.  A collision is invisible: the second branch is
 * simply never reached, because both are `else if` chains on one keycode, and the shortcut that stops
 * working is the one somebody added last.  Nothing else in the suite would notice.
 *
 * **What this is, and what it is not.**  It is a source-shape guard, and it knows only the spellings
 * the two files actually use - `evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X` or the same
 * with `e.` in the editor, `controlPressed && keyCode == KeyEvent.VK_X` in the main window.  A shortcut
 * bound some third way is one it cannot see, so `testItFoundTheHandlersItThinksItIsReading` asserts a
 * floor on how many it found: a regex that silently stopped matching would otherwise report a clean
 * bill of health about every case it never heard of.
 *
 * It deliberately does NOT require the two windows to agree with each other.  They are different
 * windows with different jobs, and Control+S means Rename in one and Swap in the other quite happily.
 * What it requires is that neither window has two answers to one key.
 *
 * **AND THE MAIN WINDOW HAS NO FREE LETTERS AT ALL**, which is the thing this class got wrong on the
 * day it was written.  `TrainControlUI`'s chain ends
 * `else if (this.buttonMapping.containsKey(keyCode))` with no `!controlPressed`, and `buttonMapping`
 * holds every letter - so Control plus any letter not caught above it selects a locomotive button.
 * The seven letters this printed as "free in both" were seven letters that already did something.
 * `testTheMainWindowSwallowsEveryOtherLetter` pins that arm, so the print below can say what is true;
 * whether the arm itself should filter Control is `OB-197`.
 *
 * @author Adam
 */
public class testNoTwoShortcutsShareAKey
{
    /**
     * The layout and autonomy editor, where FR-066's Control+E lives.
     */
    private static final String EDITOR = "src/org/traincontrol/gui/LayoutEditor.java";

    /**
     * The main window.
     */
    private static final String MAIN = "src/org/traincontrol/gui/TrainControlUI.java";

    /**
     * `evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X`, and the same with `e.`.
     *
     * The Control half first and the keycode second, which is the only order either file writes.  A
     * branch written the other way round is one this cannot see - it said it read both until a review
     * checked - and the floor above is what catches a spelling going silently unmatched.
     *
     * Both event names, because `LayoutEditor` has two key entry points and they do not agree on one:
     * `formKeyPressed` calls its argument `evt` and `receiveKeyEvent` calls it `e`.
     */
    private static final Pattern IN_THE_EDITOR = Pattern.compile(
        "\\be(?:vt)?\\.isControlDown\\(\\)\\s*&&\\s*e(?:vt)?\\.getKeyCode\\(\\)"
        + "\\s*==\\s*KeyEvent\\.VK_([A-Z0-9_]+)");

    /**
     * `controlPressed && keyCode == KeyEvent.VK_X`.
     */
    private static final Pattern IN_THE_MAIN_WINDOW = Pattern.compile(
        "controlPressed\\s*&&\\s*keyCode\\s*==\\s*KeyEvent\\.VK_([A-Z0-9_]+)");

    /**
     * The keys one file binds twice on purpose, and why.
     *
     * **A second binding is not automatically a collision.**  `TrainControlUI.locomotiveGestureOnDiagram`
     * is asked FIRST, from the top of the key handler, and returns true when it has handled the key -
     * so Control+X and Control+V mean the square under the pointer while the pointer is resting on the
     * track diagram, and the locomotive button everywhere else.  Adam's own reasoning is in that
     * method: *"A user pointing at a station and pressing Control+X means the station - there is
     * nothing else it could mean."*
     *
     * Delete is on this list for a related reason: the diagram layer takes it WITHOUT Control and the
     * else-if chain takes it WITH, so the two never contend at all - the regex here sees only the key
     * name and cannot tell them apart.
     *
     * Each entry is `File.java#KEY`, and each is a claim somebody has to defend: an entry added
     * without a first-refusal layer to point at is a shortcut quietly turned off.
     */
    private static final Set<String> BOUND_TWICE_ON_PURPOSE = new LinkedHashSet<>(java.util.Arrays.asList(
        "TrainControlUI.java#X", "TrainControlUI.java#V", "TrainControlUI.java#DELETE",

        // `LayoutEditor.receiveKeyEvent` is a SECOND entry point, not a second branch of the first:
        // it takes the tile it was pressed over, has no caller today, and returns immediately in
        // autonomy mode.  Its Control+V cannot contend with `formKeyPressed`'s.
        "LayoutEditor.java#V"));

    /**
     * How many Control shortcuts each file is known to bind.
     *
     * A floor rather than a count: adding one is ordinary and must not fail this, while a regex that
     * has stopped matching drops towards nothing and must.
     *
     * **Measured by running the regexes**, which is how the first version of this got them wrong: it
     * said 10 and 15 from a reading, the scan finds **16 and 16**, and floors of 8 and 12 would have
     * let half the editor's handlers go invisible before anything reddened.  Two below the real count,
     * so that removing a shortcut is ordinary and losing a spelling is not.
     */
    private static final int EDITOR_FLOOR = 14;

    private static final int MAIN_FLOOR = 14;

    /**
     * No key does two things in the layout editor.
     *
     * @throws IOException when the source cannot be read
     */
    @Test
    public void testTheEditorHasOneAnswerPerKey() throws IOException
    {
        assertEquals(duplicatesIn(EDITOR, IN_THE_EDITOR), new TreeSet<String>(),
            "these Control shortcuts are bound twice in " + EDITOR + ": "
            + duplicatesIn(EDITOR, IN_THE_EDITOR) + ". Both branches test the same keycode in one"
            + " else-if chain, so the second is unreachable and the shortcut that stops working is"
            + " whichever was added last - silently");
    }

    /**
     * And none does in the main window.
     *
     * @throws IOException when the source cannot be read
     */
    @Test
    public void testTheMainWindowHasOneAnswerPerKey() throws IOException
    {
        assertEquals(duplicatesIn(MAIN, IN_THE_MAIN_WINDOW), new TreeSet<String>(),
            "these Control shortcuts are bound twice in " + MAIN + ": "
            + duplicatesIn(MAIN, IN_THE_MAIN_WINDOW) + ". See the sibling test for why that is silent");
    }

    /**
     * The regexes still match the handlers they were written against.
     *
     * Without this the two claims above pass by finding nothing, which is the failure mode of every
     * guard that reads source: it reports a clean bill of health about the cases it never heard of.
     *
     * @throws IOException when the source cannot be read
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
     * What is still free in BOTH windows, printed rather than asserted.
     *
     * The next person to be asked "which key" gets the answer off a run instead of off a reading, and
     * the reading is what took an hour on 2026-09-09.
     *
     * @throws IOException when the source cannot be read
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
        System.out.println("  claimed by neither chain            :" + unclaimed);
        System.out.println("  ...but the main window's last arm takes every letter it reaches, so a"
            + " letter on that line still selects a locomotive button there (OB-197).");

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
     * The main window's chain ends in an arm that takes every letter, Control held or not.
     *
     * `else if (this.buttonMapping.containsKey(keyCode))` with no `!controlPressed`, and
     * `buttonMapping` is filled with all 26 letters in `setupKeyboardShortcuts` - so Control plus any
     * letter the arms above do not catch selects a locomotive button.
     *
     * **This is why the sibling test prints "free in the editor" rather than "free in both".**  The
     * first version of this class printed seven letters as free in both windows and FR-066's key was
     * chosen off that list; the seven were not free, they were unclaimed by any named shortcut and
     * swallowed by this arm.  Control+E in the main window selects the E button today.
     *
     * Pinned rather than fixed: whether that arm should filter Control is a change to what the
     * application does, and it is filed as `OB-197`.  What this asserts is that the arm is still
     * there, so the sibling's wording stays true - and if somebody adds the filter, this goes red and
     * the wording can go back to "free in both".
     *
     * @throws IOException when the source cannot be read
     */
    @Test
    public void testTheMainWindowSwallowsEveryOtherLetter() throws IOException
    {
        String source = sourceOf(MAIN);

        assertTrue(source.contains("else if (this.buttonMapping.containsKey(keyCode))"),
            "the main window's fall-through arm is not spelled the way this class reads it, so"
            + " nothing here says whether Control plus a letter still selects a locomotive button -"
            + " and the sibling test's wording about free keys rests on that");

        assertFalse(source.contains("!controlPressed && this.buttonMapping.containsKey(keyCode)"),
            "the fall-through arm now filters Control, so letters bound to no named shortcut really"
            + " ARE free in the main window. That is OB-197 fixed: say so in the sibling test's"
            + " printout and in this class's javadoc, and delete this test");
    }

    /**
     * One source file, read whole.
     *
     * @param path the file
     * @return its text
     * @throws IOException when it cannot be read
     */
    private String sourceOf(String path) throws IOException
    {
        File file = new File(path);

        assertTrue(file.isFile(), path + " is not there, so this test is reading nothing. Tests run"
            + " from the project root");

        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Every Control key bound in one file, in the order they appear.
     *
     * @param path the source file
     * @param how the spelling that file uses
     * @return the key names, with repeats
     * @throws IOException when the source cannot be read
     */
    private List<String> keysIn(String path, Pattern how) throws IOException
    {
        String source = sourceOf(path);

        List<String> out = new ArrayList<>();

        Matcher found = how.matcher(source);

        while (found.find()) out.add(found.group(1));

        return out;
    }

    /**
     * Every allowance names a key that really is bound twice.
     *
     * An allowlist entry whose case has gone is an allowance nobody can see expiring, and the next
     * genuine collision on that key is admitted without a word.
     *
     * @throws IOException when the source cannot be read
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

        // Removed once per APPEARANCE above, so a key bound once is still removed - what is asserted
        // here is only that the key is bound at all.  A key bound once has no second binding to
        // allow, and that is caught by the count below.
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

    /**
     * The keys that appear more than once.
     *
     * @param path the source file
     * @param how the spelling that file uses
     * @return the duplicated key names, sorted
     * @throws IOException when the source cannot be read
     */
    private Set<String> duplicatesIn(String path, Pattern how) throws IOException
    {
        Map<String, Integer> counted = new LinkedHashMap<>();

        for (String key : keysIn(path, how))
        {
            counted.put(key, counted.containsKey(key) ? counted.get(key) + 1 : 1);
        }

        Set<String> twice = new TreeSet<>();

        String file = new File(path).getName();

        for (Map.Entry<String, Integer> entry : counted.entrySet())
        {
            if (entry.getValue() <= 1) continue;

            if (BOUND_TWICE_ON_PURPOSE.contains(file + "#" + entry.getKey())) continue;

            twice.add(entry.getKey());
        }

        return twice;
    }
}
