package core;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinSimpleComponent;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test TrainControl save file loading - against a written-down statement of what each file holds.
 *
 * These seven files are the only genuine old-build fixtures in the repository: each was written by the
 * TrainControl version it is named after, one of them taken straight from a working install.  They are
 * the only evidence that a database somebody has been adding to since 2.3.3 still opens, and they
 * cannot be regenerated - a file written by today's build only proves that this build's writer and
 * this build's reader agree with each other.
 *
 * For a long time each was asserted to load NON-EMPTY and nothing more (TA-B3 of the 2026-08-24 test
 * suite audit).  That is the wrong shape of assertion for a migration test: what a migration does when
 * it goes wrong is not to fail, it is to quietly bring back less than was there.  The audit's receipt:
 * a `restoreState` truncated to `subList(0, 1)` - one component out of two and a half thousand, every
 * locomotive, route, switch and sensor gone - passed all seven tests.
 *
 * So each fixture now carries a MANIFEST: how many components it holds, how many of each kind, and a
 * handful of named locomotives, routes, switches and sensors with their addresses and function counts.
 * The figures were read off the files once and written down here; they are ground truth about the
 * files, not about the reader, and a migration that drops a kind or truncates a list now says which
 * fixture and by how much.
 *
 * Mutation these must fail: in `MarklinControlStation.restoreState`, truncate the restored list -
 * `instance = ((List&lt;MarklinSimpleComponent&gt;) obj).subList(0, 1);`.  Before the manifests that
 * passed all seven tests; run 2026-08-25 against a mutant compiled outside the repository, it fails
 * all 8 of them.
 *
 * A figure here changing is not automatically a bug - but it is always a question, and the answer has
 * to be a deliberate one about the reader, because the files themselves never change.
 */
public class testLoadData
{
    /**
     * What one of the fixture files holds.
     *
     * Read off the file once and written down, rather than computed from the file at test time: an
     * expectation the subject produces is not an expectation.
     */
    private static final class Manifest
    {
        final String resource;

        final int total;

        final Map<MarklinSimpleComponent.Type, Integer> byType;

        /** name, type, address, and how many function slots - -1 where the kind has none */
        final List<Object[]> named;

        Manifest(String resource, int total, Map<MarklinSimpleComponent.Type, Integer> byType,
            List<Object[]> named)
        {
            this.resource = resource;
            this.total = total;
            this.byType = byType;
            this.named = named;
        }

        @Override
        public String toString()
        {
            return resource;
        }
    }

    // Shorthand for the eight component kinds, in the order the enum declares them
    private static final MarklinSimpleComponent.Type MFX = MarklinSimpleComponent.Type.LOC_MFX;
    private static final MarklinSimpleComponent.Type MM2 = MarklinSimpleComponent.Type.LOC_MM2;
    private static final MarklinSimpleComponent.Type DCC = MarklinSimpleComponent.Type.LOC_DCC;
    private static final MarklinSimpleComponent.Type MULTI = MarklinSimpleComponent.Type.LOC_MULTI_UNIT;
    private static final MarklinSimpleComponent.Type SWITCH = MarklinSimpleComponent.Type.SWITCH;
    private static final MarklinSimpleComponent.Type SIGNAL = MarklinSimpleComponent.Type.SIGNAL;
    private static final MarklinSimpleComponent.Type ROUTE = MarklinSimpleComponent.Type.ROUTE;
    private static final MarklinSimpleComponent.Type FEEDBACK = MarklinSimpleComponent.Type.FEEDBACK;

    // Locomotives, a route, a switch and a sensor that are in every one of these files.  The same
    // engines under the same addresses across seven builds is itself the property being asserted:
    // these are one operator's database, saved again and again as the software changed underneath it.
    private static final Object[] EA_3005 = {"EA 3005 DSB", MFX, 62, 32};
    private static final Object[] BR_628 = {"BR 628 2", MM2, 28, 5};
    private static final Object[] SNCF_26199 = {"26199 SNCF", DCC, 126, 29};
    private static final Object[] SU45 = {"SU45-070", MFX, 80, 32};
    private static final Object[] SWITCH_1 = {"Switch 1", SWITCH, 0, -1};
    private static final Object[] SENSOR_1024 = {"1024", FEEDBACK, 1024, -1};

    private static final Manifest DB2_3_3 = new Manifest("/LocDB2_3_3.data", 521,
        counts(91, 22, 17, 3, 205, 52, 73, 58),
        // 2.3.3 predates "MF+ER"; its multi-unit is this one, and its routes are named differently
        named(EA_3005, BR_628, SNCF_26199, SU45, SWITCH_1, SENSOR_1024,
            new Object[]{"SBB420  Red/Cargo", MULTI, 11266, 32},
            new Object[]{"Top Track 1", ROUTE, 1, -1}));

    private static final Manifest DB2_4_12 = new Manifest("/LocDB2_4_12.data", 4378,
        counts(101, 21, 18, 4, 4031, 64, 81, 58),
        // The one file whose routes are the "FS n" set rather than the "Top Track n" set
        named(EA_3005, BR_628, SNCF_26199, SU45, SWITCH_1, SENSOR_1024,
            new Object[]{"MF+ER", MULTI, 4, 32},
            new Object[]{"FS 1", ROUTE, 1, -1}));

    private static final Manifest DB2_5_16 = new Manifest("/LocDB2_5_16.data", 2664,
        counts(109, 22, 18, 4, 2315, 53, 85, 58),
        named(EA_3005, BR_628, SNCF_26199, SU45, SWITCH_1, SENSOR_1024,
            new Object[]{"MF+ER", MULTI, 4, 32},
            new Object[]{"Top Track 1", ROUTE, 1, -1}));

    private static final Manifest DB2_6_5 = new Manifest("/LocDB2_6_5.data", 2663,
        counts(112, 21, 17, 4, 2318, 50, 83, 58),
        named(EA_3005, BR_628, SNCF_26199, SU45, SWITCH_1, SENSOR_1024,
            new Object[]{"MF+ER", MULTI, 4, 32},
            new Object[]{"Top Track 1", ROUTE, 1, -1}));

    private static final Manifest DB2_7_0 = new Manifest("/LocDB2_7_0.data", 2668,
        counts(113, 22, 18, 4, 2316, 52, 85, 58),
        named(EA_3005, BR_628, SNCF_26199, SU45, SWITCH_1, SENSOR_1024,
            new Object[]{"MF+ER", MULTI, 4, 32},
            new Object[]{"Top Track 1", ROUTE, 1, -1}));

    private static final Manifest DB2_8_0 = new Manifest("/LocDB2_8_0.data", 2673,
        counts(115, 23, 20, 4, 2317, 51, 85, 58),
        named(EA_3005, BR_628, SNCF_26199, SU45, SWITCH_1, SENSOR_1024,
            new Object[]{"MF+ER", MULTI, 4, 32},
            new Object[]{"Top Track 1", ROUTE, 1, -1}));

    /**
     * The database the 3.0.0 beta is actually running on.
     *
     * Taken from the working install rather than written by hand, because the point of these fixtures
     * is that a file some earlier version really produced still opens.  A hand-made one only proves
     * that the writer and the reader in this build agree with each other.
     */
    private static final Manifest DB3_0_0 = new Manifest("/LocDB3_0_0.data", 2681,
        counts(115, 23, 28, 4, 2304, 64, 85, 58),
        named(EA_3005, BR_628, SNCF_26199, SU45, SWITCH_1, SENSOR_1024,
            new Object[]{"MF+ER", MULTI, 4, 32},
            new Object[]{"Top Track 1", ROUTE, 1, -1}));

    public static MarklinControlStation model;

    public testLoadData() throws Exception
    {
    }

    @Test
    public void testLoad2_4_12() throws Exception
    {
        assertRestores(DB2_4_12);
    }

    @Test
    public void testLoad2_6_5() throws Exception
    {
        assertRestores(DB2_6_5);
    }

    @Test
    public void testLoad2_7_0() throws Exception
    {
        assertRestores(DB2_7_0);
    }

    @Test
    public void testLoad2_5_16() throws Exception
    {
        assertRestores(DB2_5_16);
    }

    @Test
    public void testLoad2_3_3() throws Exception
    {
        assertRestores(DB2_3_3);
    }

    @Test
    public void testLoad2_8_0() throws Exception
    {
        assertRestores(DB2_8_0);
    }

    @Test
    public void testLoad3_0_0() throws Exception
    {
        assertRestores(DB3_0_0);
    }

    /**
     * Every route in every fixture came back with the commands that make it a route.
     *
     * Split out from the per-file manifests because it is a different kind of statement: not "this
     * file holds these things" but "no route in any of them is empty".  A route with no commands is
     * the shape a half-finished migration leaves behind - the name and the trigger survive, the thing
     * it actually does does not, and the operator finds out when the route fires and no point moves.
     */
    @Test
    public void testEveryRouteInEveryFixtureStillHasItsCommands() throws Exception
    {
        for (Manifest manifest : Arrays.asList(DB2_3_3, DB2_4_12, DB2_5_16, DB2_6_5, DB2_7_0,
            DB2_8_0, DB3_0_0))
        {
            List<MarklinSimpleComponent> restored = model.restoreState(pathOf(manifest));

            int routes = 0;

            for (MarklinSimpleComponent component : restored)
            {
                if (component.getType() != ROUTE) continue;

                routes++;

                assertNotNull(component.getRoute(),
                    manifest + ": route \"" + component.getName() + "\" came back with no command "
                    + "list at all");

                assertTrue(component.getRoute().size() > 0,
                    manifest + ": route \"" + component.getName() + "\" came back with an empty "
                    + "command list, so it would fire and move nothing");
            }

            // Assert the variable, not the control: a loop over no routes passes every line above
            assertEquals(routes, manifest.byType.get(ROUTE).intValue(),
                manifest + ": the loop above did not see the routes this file holds, so it proved "
                + "nothing about them");
        }
    }

    /**
     * Checks one fixture against its manifest.
     */
    private void assertRestores(Manifest manifest)
    {
        List<MarklinSimpleComponent> restored = model.restoreState(pathOf(manifest));

        assertEquals(restored.size(), manifest.total,
            manifest + " did not come back whole.  These files never change, so a different number "
            + "of components means the reader changed - and what a broken migration does is not to "
            + "fail, it is to bring back less than was saved");

        Map<MarklinSimpleComponent.Type, Integer> found =
            new EnumMap<>(MarklinSimpleComponent.Type.class);

        for (MarklinSimpleComponent.Type type : MarklinSimpleComponent.Type.values())
        {
            found.put(type, 0);
        }

        for (MarklinSimpleComponent component : restored)
        {
            found.put(component.getType(), found.get(component.getType()) + 1);
        }

        for (MarklinSimpleComponent.Type type : MarklinSimpleComponent.Type.values())
        {
            assertEquals(found.get(type), manifest.byType.get(type),
                manifest + " came back with the wrong number of " + type + ".  A whole kind of "
                + "component going missing keeps the total plausible and loses every one of them");
        }

        for (Object[] expected : manifest.named)
        {
            String name = (String) expected[0];

            MarklinSimpleComponent component = byName(restored, name);

            assertNotNull(component, manifest + " no longer contains \"" + name + "\"");

            assertEquals(component.getType(), expected[1],
                manifest + ": \"" + name + "\" came back as the wrong kind of component");

            assertEquals(component.getAddress(), ((Integer) expected[2]).intValue(),
                manifest + ": \"" + name + "\" came back on the wrong address, which is the one "
                + "field that decides whether a command reaches the right engine");

            int functions = ((Integer) expected[3]);

            if (functions >= 0)
            {
                assertNotNull(component.getFunctions(),
                    manifest + ": \"" + name + "\" came back with no function map");

                assertEquals(component.getFunctions().length, functions,
                    manifest + ": \"" + name + "\" came back with a different number of function "
                    + "slots, so its buttons no longer line up with the decoder");
            }
        }
    }

    private static MarklinSimpleComponent byName(List<MarklinSimpleComponent> components, String name)
    {
        for (MarklinSimpleComponent component : components)
        {
            if (name.equals(component.getName())) return component;
        }

        return null;
    }

    private String pathOf(Manifest manifest)
    {
        try
        {
            return Paths.get(getClass().getResource(manifest.resource).toURI()).toString();
        }
        catch (Exception cannotHappen)
        {
            throw new IllegalStateException("the fixture " + manifest + " is not on the classpath",
                cannotHappen);
        }
    }

    /**
     * The eight per-kind counts, in the order the Type enum declares them.
     */
    private static Map<MarklinSimpleComponent.Type, Integer> counts(int mfx, int mm2, int dcc,
        int multi, int switches, int signals, int routes, int feedback)
    {
        Map<MarklinSimpleComponent.Type, Integer> map =
            new EnumMap<>(MarklinSimpleComponent.Type.class);

        map.put(MFX, mfx);
        map.put(MM2, mm2);
        map.put(DCC, dcc);
        map.put(MULTI, multi);
        map.put(SWITCH, switches);
        map.put(SIGNAL, signals);
        map.put(ROUTE, routes);
        map.put(FEEDBACK, feedback);

        return map;
    }

    private static List<Object[]> named(Object[]... entries)
    {
        return new ArrayList<>(Arrays.asList(entries));
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception
    {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception
    {
    }
}
