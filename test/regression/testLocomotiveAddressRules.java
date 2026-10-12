package regression;

import java.io.File;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.base.Locomotive.decoderType;

/**
 * What counts as a valid locomotive address, asked once and answered in one place.
 *
 * `MarklinLocomotive.validateNewAddress` is the rule. It was also written out again, differently, in
 * the Add Locomotive dialog: three separate `if` blocks testing the UPPER bound only, with no branch
 * for MULTI_UNIT, and an `abs()` above them. So **address 0 passed the dialog** - zero is not greater
 * than any maximum - and made a locomotive the model itself would have refused.
 *
 * These tests pin the rule rather than the dialog, because the dialog now asks the rule. The pair that
 * matters is the lower bound: the drifted copy would pass every upper-bound test in this file.
 */
public class testLocomotiveAddressRules
{
    /**
     * Zero is not an address, for any decoder.
     *
     * This is the clause the dialog's copy left out, and the only one that was reachable: the maximum
     * is hard to type by accident and zero is not.
     */
    @Test
    public void testZeroIsRefusedForEveryDecoder()
    {
        for (decoderType type : decoderType.values())
        {
            assertFalse(MarklinLocomotive.validateNewAddress(type, 0),
                type + " accepted address 0, which addresses no decoder at all");
        }
    }

    /**
     * And neither is a negative one.
     *
     * The dialog takes an absolute value before it looks, so this is about the rule rather than about
     * that screen - but a rule that accepts -1 accepts it from every other caller too.
     */
    @Test
    public void testNegativeIsRefusedForEveryDecoder()
    {
        for (decoderType type : decoderType.values())
        {
            assertFalse(MarklinLocomotive.validateNewAddress(type, -1),
                type + " accepted a negative address");
        }
    }

    /**
     * Each decoder's ceiling is its own, and one past it is refused.
     */
    @Test
    public void testEachDecoderKeepsItsOwnCeiling()
    {
        assertTrue(MarklinLocomotive.validateNewAddress(decoderType.MM2, Locomotive.MM2_MAX_ADDR),
            "MM2 refused its own maximum");

        assertFalse(MarklinLocomotive.validateNewAddress(decoderType.MM2, Locomotive.MM2_MAX_ADDR + 1),
            "MM2 accepted one past its maximum");

        assertTrue(MarklinLocomotive.validateNewAddress(decoderType.MFX, Locomotive.MFX_MAX_ADDR),
            "MFX refused its own maximum");

        assertFalse(MarklinLocomotive.validateNewAddress(decoderType.MFX, Locomotive.MFX_MAX_ADDR + 1),
            "MFX accepted one past its maximum");

        assertTrue(MarklinLocomotive.validateNewAddress(decoderType.DCC, Locomotive.DCC_MAX_ADDR),
            "DCC refused its own maximum");

        assertFalse(MarklinLocomotive.validateNewAddress(decoderType.DCC, Locomotive.DCC_MAX_ADDR + 1),
            "DCC accepted one past its maximum");
    }

    /**
     * Every decoder type has an answer, including any added later.
     *
     * `validateNewAddress` returns false in its default branch, so a new decoder type is refused
     * everywhere rather than let through - which is the safe way round, but means a type added and not
     * wired here would silently refuse every address. One is as much a defect as the other, and this
     * says which one is happening.
     */
    @Test
    public void testEveryDecoderTypeHasAWorkingRange()
    {
        for (decoderType type : decoderType.values())
        {
            assertTrue(MarklinLocomotive.validateNewAddress(type, 1),
                type + " refuses address 1, so either it has no branch in validateNewAddress or its "
                + "maximum is below one - a locomotive of this type cannot be created at all");
        }
    }

    /**
     * The dialog asks the rule above rather than restating it.
     *
     * Every test in this file pins `MarklinLocomotive.validateNewAddress` itself, and this class's own
     * javadoc says why that is not enough: the original defect was not in the rule, it was in
     * `AddLocomotive`'s own copy of it, three separate upper-bound-only `if` blocks with an `abs()`
     * above them and no branch for MULTI_UNIT - and address 0 passed every one of them, since zero is
     * not greater than any maximum. Nothing above this test would notice that copy coming back, because
     * `grep -rn AddLocomotive test/` before this method found nothing at all: the dialog had no test of
     * its own, only the rule it used to ignore.
     *
     * MUTATION this catches: replace the call this checks for with the three-`if` copy the class
     * javadoc describes. Every test above still passes - they never call `AddLocomotive` - and address
     * 0 is accepted again for every decoder type this dialog offers.
     */
    @Test
    public void testTheDialogAsksTheRuleRatherThanRestatingIt() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(
            new File("src/org/traincontrol/gui/AddLocomotive.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8);

        String body = withoutComments(bodyOf(source,
            "private void AddLocButtonActionPerformed(java.awt.event.ActionEvent evt)"));

        assertFalse(body.isEmpty(),
            "AddLocButtonActionPerformed has moved or been renamed - this scan is reading nothing");

        int validated = body.indexOf("validateNewAddress(");

        assertTrue(validated >= 0,
            "AddLocomotive no longer calls validateNewAddress at all - either the shared rule has gone "
            + "and each decoder's range is being restated here again, or it has been renamed and this "
            + "scan needs updating either way");

        int mfx = body.indexOf("newMFXLocomotive(");
        int dcc = body.indexOf("newDCCLocomotive(");
        int mm2 = body.indexOf("newMM2Locomotive(");

        assertTrue(mfx >= 0 && dcc >= 0 && mm2 >= 0,
            "one of the three locomotive-creation calls has moved or been renamed, so the ordering "
            + "below cannot be checked");

        // All three, not just the branch this run happened to take - address validation has to gate
        // creation regardless of which decoder type was selected.
        assertTrue(validated < mfx && validated < dcc && validated < mm2,
            "validateNewAddress is called AFTER a locomotive can already be created, which is no "
            + "refusal at all - a rejected address would be asked about only after the locomotive it "
            + "was supposed to stop already exists");
    }

    /**
     * A line with any comment - `//` or block - removed, so a check does not pass on the strength of
     * prose describing code that has gone. Copied rather than shared with the other tests that do
     * this: a test helper reaching into another test class is a dependency between things that are
     * supposed to fail independently.
     */
    private static String withoutComments(String body)
    {
        StringBuilder out = new StringBuilder();

        boolean inLine = false, inBlock = false;

        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            char next = i + 1 < body.length() ? body.charAt(i + 1) : ' ';

            if (inLine)
            {
                if (c == '\n') { inLine = false; out.append(c); }
            }
            else if (inBlock)
            {
                if (c == '*' && next == '/') { inBlock = false; i++; }
            }
            else if (c == '/' && next == '/') inLine = true;
            else if (c == '/' && next == '*') inBlock = true;
            else out.append(c);
        }

        return out.toString();
    }

    /**
     * The body of one method, braces included, or empty when the declaration cannot be found.
     */
    private static String bodyOf(String source, String declaration)
    {
        int at = source.indexOf(declaration);

        if (at < 0) return "";

        int open = source.indexOf('{', at + declaration.length());

        if (open < 0) return "";

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(at, i + 1);
        }

        return "";
    }
}
