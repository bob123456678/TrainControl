package core;

import java.io.File;
import java.nio.file.Files;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.StationIndex;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * A square cannot be given a name that ends the way the builder names one direction of a square.
 *
 * Adam, TDR-C11, 2026-09-14: *"refuse and close, but make sure the words are uncommon."*
 *
 * **Why it is refused.**  Messages name squares by stripping the builder's heading from a copy's name -
 * "BottomMainB (eastbound, reverse)" is shown as "BottomMainB" - so a square somebody NAMED "Main (eastbound)"
 * read as "Main", the same as the real Main's copies, and two different places became one in every refusal
 * and every Return Home reason.
 *
 * **Why the words are uncommon.**  Only the builder's form is refused: a lowercase heading in brackets at the
 * very end, after a space - with anything after a comma inside the bracket, of which ", reverse" is the
 * builder's own (FTN-C1).  Each control below is a name a real railway might use and
 * that must still be allowed.
 *
 * @author Adam
 */
public class testANameCannotEndInAHeading
{
    /**
     * The builder's forms are refused.
     */
    @Test
    public void testTheBuildersFormsAreRefused()
    {
        // "Depot (eastbound, old)" as well: the stripping reads the heading up to the comma and ignores the
        // rest, so that name reads as "Depot" in every message - refusing it is what keeps the two agreeing (FTN-C1).
        for (String name : new String[] {"Main (eastbound)", "Main (westbound)", "Yard (northbound)",
            "Yard (southbound)", "Main (eastbound, reverse)", "  Main (westbound)  ", "Depot (eastbound, old)"})
        {
            assertTrue(StationIndex.endsWithAnArrivalHeading(name),
                "\"" + name + "\" ends the way the builder names one direction of a square and was not"
                + " refused - it would read as another square wherever a message names squares (TDR-C11)");

            assertNotNull(AutonomySession.whyNotAPointName(name),
                "the session would take \"" + name + "\" although the rule refuses it");
        }
    }

    /**
     * Names a railway might really use are not refused.
     */
    @Test
    public void testOrdinaryNamesWithTheseWordsAreAllowed()
    {
        for (String name : new String[] {"Eastbound Platform", "Main Eastbound", "Main Line (Northbound)",
            "Yard (old)", "Westbound", "Main (eastbound track)", "Main (east)", "(eastbound) Main",
            "Main (eastbound) sidings"})
        {
            assertFalse(StationIndex.endsWithAnArrivalHeading(name),
                "\"" + name + "\" was refused, but it is not the builder's form - only a lowercase heading in"
                + " brackets at the end is. Adam: \"make sure the words are uncommon\"");

            assertNull(AutonomySession.whyNotAPointName(name),
                "the session refuses \"" + name + "\"");
        }
    }

    /**
     * The session door refuses it and stores nothing, saying which name.
     *
     * @throws Exception from the temporary folder
     */
    @Test
    public void testTheSessionRefusesAndStoresNothing() throws Exception
    {
        File folder = Files.createTempDirectory("tc-name-heading").toFile();

        try
        {
            AutonomySession session = new AutonomySession(folder);

            TileKey square = new TileKey("Page", 3, 3);

            try
            {
                session.setPointName(square, "Main (eastbound)");

                fail("the session stored \"Main (eastbound)\" - a door that forgets to ask would let it through (TDR-C11)");
            }
            catch (IllegalArgumentException refused)
            {
                assertTrue(String.valueOf(refused.getMessage()).contains("Main (eastbound)"),
                    "the refusal does not name what was typed: " + refused.getMessage());
            }

            assertEquals(session.getStore().getPointName(square), null,
                "the refused name was stored anyway");
        }
        finally
        {
            File[] children = folder.listFiles();

            if (children != null) for (File child : children) child.delete();

            folder.delete();
        }
    }
}
