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
 * **What this is, and what it is not.**  It is a source-shape guard, and it knows only the two spellings
 * the two files actually use - `evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X` in the editor,
 * `controlPressed && keyCode == KeyEvent.VK_X` in the main window.  A shortcut bound some third way is
 * one it cannot see, so `testItFoundTheHandlersItThinksItIsReading` asserts a floor on how many it
 * found: a regex that silently stopped matching would otherwise report a clean railway of nothing.
 *
 * It deliberately does NOT require the two windows to agree with each other.  They are different
 * windows with different jobs, and Control+S means Rename in one and Swap in the other quite happily.
 * What it requires is that neither window has two answers to one key.
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
     * `evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_X`, in either order of the two halves.
     */
    private static final Pattern IN_THE_EDITOR = Pattern.compile(
        "evt\\.isControlDown\\(\\)\\s*&&\\s*evt\\.getKeyCode\\(\\)\\s*==\\s*KeyEvent\\.VK_([A-Z0-9_]+)");

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
        "TrainControlUI.java#X", "TrainControlUI.java#V", "TrainControlUI.java#DELETE"));

    /**
     * How many Control shortcuts each file is known to bind.
     *
     * A floor rather than a count: adding one is ordinary and must not fail this, while a regex that
     * has stopped matching drops to nothing and must.  Measured on 2026-09-09 at 10 in the editor and
     * 15 in the main window.
     */
    private static final int EDITOR_FLOOR = 8;

    private static final int MAIN_FLOOR = 12;

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
        Set<String> taken = new LinkedHashSet<>(keysIn(EDITOR, IN_THE_EDITOR));

        taken.addAll(keysIn(MAIN, IN_THE_MAIN_WINDOW));

        StringBuilder free = new StringBuilder();

        for (char c = 'A'; c <= 'Z'; c++)
        {
            if (!taken.contains(String.valueOf(c))) free.append(" ").append(c);
        }

        System.out.println("### Control shortcuts");
        System.out.println("  taken in one window or the other:" + new TreeSet<>(taken));
        System.out.println("  free in both                    :" + free);

        assertTrue(free.length() > 0,
            "every letter of the alphabet is bound to a Control shortcut in one window or the other,"
            + " which is either a remarkable application or a regex matching things it should not:"
            + " " + taken);
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
        File file = new File(path);

        assertTrue(file.isFile(), path + " is not there, so this test is reading nothing. Tests run"
            + " from the project root");

        String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

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
