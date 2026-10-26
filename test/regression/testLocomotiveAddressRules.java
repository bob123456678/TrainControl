package regression;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.base.Locomotive.decoderType;
import support.SourceOf;

/**
 * What counts as a valid locomotive address, asked once and answered in one place.
 * `MarklinLocomotive.validateNewAddress` is the rule; the Add Locomotive dialog once wrote it out again (three
 * upper-bound-only `if` blocks, no MULTI_UNIT branch), so address 0 passed the dialog.  These tests pin the rule, which
 * the dialog now asks; the lower bound is the pair that matters, since the drifted copy would pass every upper-bound
 * test.
 */
public class testLocomotiveAddressRules
{
    /**
     * Zero is not an address, for any decoder: the clause the dialog's copy left out, and the only one reachable by
     * accident.
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
     * And neither is a negative one: the dialog takes an absolute value, but a rule that accepts -1 accepts it from
     * every other caller too.
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

    /** Each decoder's ceiling is its own, and one past it is refused. */
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
     * Every decoder type has an answer, including any added later: the default branch refuses, so a type added and not
     * wired here would silently refuse every address.
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
     * The dialog asks the rule above rather than restating it: the original defect was in `AddLocomotive`'s own copy,
     * which no other test here calls.
     *
     * MUTATION: replace the call this checks for with the three-`if` copy the class javadoc describes; address 0 is
     * accepted again for every decoder type this dialog offers.
     */
    @Test
    public void testTheDialogAsksTheRuleRatherThanRestatingIt() throws Exception
    {
        String body = SourceOf.withoutComments(SourceOf.declaration(
            "private void AddLocButtonActionPerformed(java.awt.event.ActionEvent evt)"));

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

        // All three, not just the branch this run happened to take: validation has to gate creation whichever decoder
        // was selected.
        assertTrue(validated < mfx && validated < dcc && validated < mm2,
            "validateNewAddress is called AFTER a locomotive can already be created, which is no "
            + "refusal at all - a rejected address would be asked about only after the locomotive it "
            + "was supposed to stop already exists");
    }
}
