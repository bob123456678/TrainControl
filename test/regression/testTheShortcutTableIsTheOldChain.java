package regression;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI.KeyShortcuts;
import support.SourceOf;

/**
 * The shortcut table is the if/else chain it replaced, press for press (OB-331).  The old method stands frozen
 * in `testTheShortcutTableIsTheOldChain.chain.txt`, its text verbatim; {@link OldChain} evaluates that text as the pure
 * function (key code, modifiers, button mapping) to arm.  It is text, not Java, because compiled here its arms would
 * count as copies against `testNoNewCopies`.
 *
 * Arm and row are paired by what they run (`testEveryRowRunsItsArmsBody`), so a row bound to another arm's keys fails
 * the exhaustive claim.  No window: `ui.testEveryKindOfShortcutOnTheWindow` presses the real keys.
 */
public class testTheShortcutTableIsTheOldChain
{
    private static final File CHAIN = new File("test/regression/testTheShortcutTableIsTheOldChain.chain.txt");

    private static final String TABLE = "public static final class KeyShortcuts";

    private static final String HANDLER = "private void LocControlPanelKeyPressed(";

    /** Alt, Control and Shift in both their spellings: every combination is one modifiers value handed to both. */
    private static final int[] MODIFIER_BITS = {InputEvent.ALT_MASK, InputEvent.ALT_DOWN_MASK, InputEvent.CTRL_MASK,
        InputEvent.CTRL_DOWN_MASK, InputEvent.SHIFT_MASK, InputEvent.SHIFT_DOWN_MASK};

    /** The chain as text: its arms in order, each with its condition. */
    private static final class OldChain
    {
        final List<Condition> conditions = new ArrayList<>();

        final List<String> bodies = new ArrayList<>();

        OldChain() throws Exception
        {
            String method = SourceOf.withoutComments(new String(Files.readAllBytes(CHAIN.toPath()),
                StandardCharsets.UTF_8).replace("\r\n", "\n"));

            for (String reading : new String[] {"boolean altPressed = (evt.getModifiers() & KeyEvent.ALT_MASK) != 0;",
                "boolean controlPressed = (evt.getModifiers() & KeyEvent.CTRL_MASK) != 0"
                    + " || (evt.getModifiers() & KeyEvent.CTRL_DOWN_MASK) != 0;"})
            {
                assertTrue(method.contains(reading), "the frozen chain does not read the modifiers as this does");
            }

            int at = method.indexOf("if (altPressed && keyCode == KeyEvent.VK_G)");

            assertTrue(at > 0, "the first arm is not in the frozen chain");

            while (true)
            {
                int open = method.indexOf('(', at);
                int close = closing(method, open);
                int brace = method.indexOf('{', close);
                int end = closing(method, brace);

                conditions.add(new ConditionReader(method.substring(open + 1, close)).read());
                bodies.add(normal(method.substring(brace + 1, end)));

                at = method.indexOf("else if", end);

                if (at < 0) break;

                at += "else ".length();
            }
        }

        /** The arm this press takes, or -1 for none. */
        int arm(int keyCode, int modifiers, Map<Integer, ?> buttonMapping)
        {
            boolean altPressed = (modifiers & KeyEvent.ALT_MASK) != 0;
            boolean controlPressed = (modifiers & KeyEvent.CTRL_MASK) != 0 || (modifiers & KeyEvent.CTRL_DOWN_MASK) != 0;

            for (int arm = 0; arm < conditions.size(); arm++)
            {
                if (conditions.get(arm).holds(keyCode, altPressed, controlPressed, buttonMapping)) return arm;
            }

            return -1;
        }
    }

    /** One arm's condition. */
    private interface Condition
    {
        boolean holds(int keyCode, boolean altPressed, boolean controlPressed, Map<Integer, ?> buttonMapping);
    }

    /** Reads the only grammar the chain's conditions use: `||`, `&&`, `!`, parentheses and four atoms. */
    private static final class ConditionReader
    {
        private final String text;

        private int at;

        ConditionReader(String text)
        {
            this.text = text.replaceAll("\\s+", "");
        }

        Condition read()
        {
            Condition all = either();

            assertEquals(at, text.length(), "unread condition text: " + text.substring(at));

            return all;
        }

        private boolean next(String token)
        {
            if (!text.startsWith(token, at)) return false;

            at += token.length();

            return true;
        }

        private Condition either()
        {
            Condition left = both();

            while (next("||"))
            {
                Condition first = left;
                Condition second = both();

                left = (k, a, c, m) -> first.holds(k, a, c, m) || second.holds(k, a, c, m);
            }

            return left;
        }

        private Condition both()
        {
            Condition left = atom();

            while (next("&&"))
            {
                Condition first = left;
                Condition second = atom();

                left = (k, a, c, m) -> first.holds(k, a, c, m) && second.holds(k, a, c, m);
            }

            return left;
        }

        private Condition atom()
        {
            if (next("!"))
            {
                Condition inner = atom();

                return (k, a, c, m) -> !inner.holds(k, a, c, m);
            }

            if (next("("))
            {
                Condition inner = either();

                assertTrue(next(")"), "an unclosed parenthesis in " + text);

                return inner;
            }

            if (next("altPressed")) return (k, a, c, m) -> a;

            if (next("controlPressed")) return (k, a, c, m) -> c;

            if (next("this.buttonMapping.containsKey(keyCode)")) return (k, a, c, m) -> m.containsKey(k);

            assertTrue(next("keyCode==KeyEvent."), "not a condition this reads: " + text.substring(at));

            int from = at;

            while (at < text.length() && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_')) at++;

            int code = constant(text.substring(from, at));

            return (k, a, c, m) -> k == code;
        }
    }

    /**
     * Every `KeyEvent.VK_*`, times every combination of the modifier spellings, on each button mapping, chooses the
     * same arm of the chain and row of the table.  The layouts only swap which button a letter gets, so the three
     * have one set of keys, and an empty and an all-keys mapping put the mapped arm's place in the order to the test.
     *
     * MUTATION: put the row "swap with the button" before "save preferred functions" (both take Control+Alt+S); drop the
     * row "speed zero"; make "next function tab" `Held.NEITHER`; drop `CTRL_DOWN_MASK` from `controlPressed`; swap two
     * names in `SPEED_BUTTONS`.  Each fails here, naming the press.
     */
    @Test
    public void testEveryPressChoosesTheSameArm() throws Exception
    {
        OldChain chain = new OldChain();

        List<String> rowOfArm = rowOfEachArm(chain);

        List<Integer> keys = everyKeyCode();

        Map<String, Map<Integer, Object>> mappings = mappings(keys);

        long compared = 0;

        List<String> different = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, Object>> mapping : mappings.entrySet())
        {
            for (int key : keys)
            {
                for (int held = 0; held < 1 << MODIFIER_BITS.length; held++)
                {
                    int modifiers = modifiers(held);

                    int arm = chain.arm(key, modifiers, mapping.getValue());

                    KeyShortcuts.Row row = KeyShortcuts.choose(key, modifiers, mapping.getValue());

                    String want = arm < 0 ? null : rowOfArm.get(arm);

                    String got = row == null ? null : row.name();

                    compared++;

                    if (want == null ? got != null : !want.equals(got))
                    {
                        different.add(mapping.getKey() + " key " + KeyEvent.getKeyText(key) + " (" + key + ") modifiers "
                            + modifiers + ": the chain took " + want + ", the table " + got);
                    }
                }
            }
        }

        System.out.println("### " + compared + " presses compared: " + keys.size() + " key codes, "
            + (1 << MODIFIER_BITS.length) + " modifier combinations, " + mappings.size() + " button mappings");

        assertEquals(compared, (long) keys.size() * (1 << MODIFIER_BITS.length) * mappings.size(),
            "not every press was compared");

        assertTrue(keys.size() > 150, "only " + keys.size() + " key codes were found by reflection");

        assertTrue(different.isEmpty(), different.size() + " presses choose differently, the first: "
            + different.subList(0, Math.min(10, different.size())));
    }

    /**
     * Each row runs its arm's body, text for text once comments, spacing and the receivers (`this.` for `key.ui.` and
     * `key.`) are set aside.  The two families pair by index: `SPEED_BUTTONS` by digit, `switchF` by function.
     *
     * MUTATION: `doPaste(key.ui.currentButton, false, true)` in the row "paste onto the button"; `switchF(fn + 1)` in the
     * function rows.  Each fails here.
     */
    @Test
    public void testEveryRowRunsItsArmsBody() throws Exception
    {
        OldChain chain = new OldChain();

        List<String> rowOfArm = rowOfEachArm(chain);

        assertEquals(new HashSet<>(rowOfArm).size(), rowOfArm.size(), "two arms pair with one row: " + rowOfArm);

        assertEquals(rowOfArm.size(), KeyShortcuts.rows().size(), "the chain has " + rowOfArm.size() + " arms and the"
            + " table " + KeyShortcuts.rows().size() + " rows");

        Set<String> names = new HashSet<>();

        for (KeyShortcuts.Row row : KeyShortcuts.rows()) names.add(row.name());

        assertEquals(new TreeSet<>(rowOfArm), new TreeSet<>(names), "the rows the source pairs are not the rows the table"
            + " holds");
    }

    /**
     * The table lists every shortcut: names unique and nonempty, every row chosen by some press, and each press's key
     * named by its row's listing.  The listing is printed, so "which keys are taken" is answered off a run.
     *
     * MUTATION: name the row "stop every train" "speed zero" too; add a row after "next function tab" that repeats its
     * keys.  Each fails here.
     */
    @Test
    public void testTheTableListsEveryShortcutOnce() throws Exception
    {
        Set<String> names = new TreeSet<>();

        for (KeyShortcuts.Row row : KeyShortcuts.rows())
        {
            assertFalse(row.name().trim().isEmpty(), "a row has no name: " + row);
            assertTrue(names.add(row.name()), "two rows are called " + row.name());

            System.out.println("  " + row);
        }

        Map<Integer, Object> letters = letters();

        Set<String> reached = new HashSet<>();

        for (int key : everyKeyCode())
        {
            for (int held = 0; held < 1 << MODIFIER_BITS.length; held++)
            {
                KeyShortcuts.Row row = KeyShortcuts.choose(key, modifiers(held), letters);

                if (row == null) continue;

                reached.add(row.name());

                assertTrue(row.toString().contains(KeyEvent.getKeyText(key)) || letters.containsKey(key),
                    row + " took " + KeyEvent.getKeyText(key) + " and its listing does not say so");
            }
        }

        Set<String> never = new TreeSet<>(names);

        never.removeAll(reached);

        assertEquals(never, new TreeSet<String>(), "no press reaches these rows");
    }

    /**
     * The window's handler is the lookup and what ran before the chain, and nothing else.
     *
     * MUTATION: add `if (evt.getKeyCode() == KeyEvent.VK_PAUSE) stop();` to `LocControlPanelKeyPressed`, and this fails.
     */
    @Test
    public void testNoKeyHandlingRemainsInTheHandler()
    {
        String body = SourceOf.withoutComments(SourceOf.body(HANDLER)).replaceAll("\\s+", "");

        assertEquals(body, "this.theKeyTheMapAlreadySaw=evt;"
            + "if(locomotiveGestureOnDiagram(evt.getKeyCode(),KeyShortcuts.controlPressed(evt.getModifiers())))return;"
            + "KeyShortcuts.Rowrow=KeyShortcuts.choose(evt.getKeyCode(),evt.getModifiers(),this.buttonMapping);"
            + "if(row!=null)row.run(this,evt);");
    }

    /**
     * Pairs each arm with the row whose source runs the same body, failing on an arm with no such row, or on a row whose
     * source is not read.
     */
    private static List<String> rowOfEachArm(OldChain chain)
    {
        Map<String, String> rowOfBody = new HashMap<>();

        String table = SourceOf.withoutComments(SourceOf.declaration(TABLE));

        for (int at = table.indexOf("row(\""); at >= 0; at = table.indexOf("row(\"", at + 1))
        {
            List<String> args = arguments(table, at + "row".length());

            assertEquals(args.size(), 3, "a row is not (name, keys, action): " + args);

            String action = args.get(2).substring(args.get(2).indexOf("->") + 2).trim();

            String body = normal(action.startsWith("{") ? action.substring(1, action.lastIndexOf('}')) : action + ";");

            if (args.get(0).endsWith("\"") && !args.get(0).contains("+"))
            {
                rowOfBody.put(body, args.get(0).substring(1, args.get(0).length() - 1));
            }
            else if (args.get(0).startsWith("\"function \""))
            {
                assertEquals(body, "switchF(fn);", "the function rows do not run switchF of their function");

                for (int fn = 0; fn < 30; fn++) rowOfBody.put("switchF(" + fn + ");", "function " + fn);
            }
            else
            {
                assertEquals(args.get(0), "\"speed key \" + digit", "a row with a built name this does not know");
                assertEquals(body, "button.accept(ui,null);", "the speed rows do not press their button");

                String buttons = table.substring(table.indexOf("SPEED_BUTTONS ="), table.indexOf("static\n"));

                int digit = 0;

                for (String each : buttons.split("TrainControlUI::"))
                {
                    if (each.startsWith("SPEED_BUTTONS")) continue;

                    rowOfBody.put(each.replaceAll("[^A-Za-z]", "") + "(null);", "speed key " + digit++);
                }

                assertEquals(digit, 10, "SPEED_BUTTONS does not hold ten buttons");
            }
        }

        List<String> rows = new ArrayList<>();

        for (String body : chain.bodies)
        {
            String row = rowOfBody.get(body);

            if (row == null) fail("no row runs this arm's body: " + body);

            rows.add(row);
        }

        return rows;
    }

    /** The top-level arguments of the call whose `(` is at `open`. */
    private static List<String> arguments(String text, int open)
    {
        List<String> args = new ArrayList<>();

        int close = closing(text, open);

        int depth = 0;

        int from = open + 1;

        boolean quoted = false;

        for (int i = open + 1; i < close; i++)
        {
            char c = text.charAt(i);

            if (c == '"' && text.charAt(i - 1) != '\\') quoted = !quoted;

            if (quoted) continue;

            if (c == '(' || c == '{') depth++;
            else if (c == ')' || c == '}') depth--;
            else if (c == ',' && depth == 0)
            {
                args.add(text.substring(from, i).trim());

                from = i + 1;
            }
        }

        args.add(text.substring(from, close).trim());

        return args;
    }

    /** The bracket closing the one at `open`; strings are skipped, comments already gone. */
    private static int closing(String text, int open)
    {
        char opening = text.charAt(open);

        char closes = opening == '(' ? ')' : '}';

        int depth = 0;

        boolean quoted = false;

        for (int i = open; i < text.length(); i++)
        {
            char c = text.charAt(i);

            if (c == '"' && text.charAt(i - 1) != '\\') quoted = !quoted;

            if (quoted) continue;

            if (c == opening) depth++;
            else if (c == closes && --depth == 0) return i;
        }

        throw new AssertionError("nothing closes the bracket at " + open);
    }

    /** A body with its receivers and spacing set aside. */
    private static String normal(String body)
    {
        return body.replaceAll("\\bkey\\.ui\\.|\\bkey\\.|\\bthis\\.", "").replaceAll("\\s+", "");
    }

    private static int constant(String name)
    {
        try
        {
            return KeyEvent.class.getField(name).getInt(null);
        }
        catch (ReflectiveOperationException unknown)
        {
            throw new AssertionError(name + " is not a key code", unknown);
        }
    }

    /** Every `VK_*` constant of `KeyEvent`, by reflection. */
    private static List<Integer> everyKeyCode() throws Exception
    {
        Set<Integer> codes = new TreeSet<>();

        for (Field field : KeyEvent.class.getFields())
        {
            if (field.getName().startsWith("VK_") && Modifier.isStatic(field.getModifiers())
                && field.getType() == int.class)
            {
                codes.add(field.getInt(null));
            }
        }

        return new ArrayList<>(codes);
    }

    /** The modifiers value of this combination of `MODIFIER_BITS`. */
    private static int modifiers(int held)
    {
        int modifiers = 0;

        for (int bit = 0; bit < MODIFIER_BITS.length; bit++)
        {
            if ((held & 1 << bit) != 0) modifiers |= MODIFIER_BITS[bit];
        }

        return modifiers;
    }

    /** A to Z, each on its own button, as `initButtonMapping` leaves it. */
    private static Map<Integer, Object> letters()
    {
        Map<Integer, Object> letters = new HashMap<>();

        for (int key = KeyEvent.VK_A; key <= KeyEvent.VK_Z; key++) letters.put(key, (char) key);

        return letters;
    }

    /** The mapping with these letters' buttons swapped, as `applyKeyboardType` does. */
    private static Map<Integer, Object> swapped(int... pairs)
    {
        Map<Integer, Object> letters = letters();

        for (int at = 0; at < pairs.length; at += 2)
        {
            Object first = letters.get(pairs[at]);

            letters.put(pairs[at], letters.get(pairs[at + 1]));
            letters.put(pairs[at + 1], first);
        }

        return letters;
    }

    private static Map<String, Map<Integer, Object>> mappings(List<Integer> keys)
    {
        Map<String, Map<Integer, Object>> mappings = new LinkedHashMap<>();

        mappings.put("QWERTY", letters());
        mappings.put("QWERTZ", swapped(KeyEvent.VK_Z, KeyEvent.VK_Y));
        mappings.put("AZERTY", swapped(KeyEvent.VK_A, KeyEvent.VK_Q, KeyEvent.VK_Z, KeyEvent.VK_W));
        mappings.put("no letters", Collections.<Integer, Object>emptyMap());

        Map<Integer, Object> everything = new HashMap<>();

        for (int key : keys) everything.put(key, key);

        mappings.put("every key", everything);

        return mappings;
    }
}
