package core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;

/**
 * Importing a 2.7.4c graph does not switch your routes off (MT-297).
 *
 * Adam, 2026-09-12: *"write a test case for this, should be trivial to see if the treads are live."*
 *
 * **Twice, because the entry says twice.** *"Run the setup, and look again - the disabling used to
 * happen on load rather than on import, so it has to be checked after autonomy has actually parsed the
 * configuration."* An import that looks harmless and a `parseAuto` that switches everything off a minute
 * later is the defect, so asking only after the import would miss exactly the case this is about.
 *
 * **The mechanism it is guarding against is real and it is asserted here.** `parseAuto` ends in
 * `applyAutonomyRouteActivations`, which walks the LIVE Central Station route database: every route whose
 * id is not in `activateRouteIDs` is disabled. Adam's own legacy file says `activateRoutes: true` with an
 * EMPTY list, so an import that carried those two keys would switch off every route he has - on import,
 * and again on every diagram edit. The last test below turns that mechanism on by hand and checks it
 * fires, so the two claims above cannot pass because nothing was ever capable of disabling anything.
 *
 * MUTATION: let `importLegacy` carry `activateRoutes`/`activateRouteIDs` and the second claim fails.
 *
 * @author Adam
 */
public class testAnImportDoesNotSwitchYourRoutesOff
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;

    /** A route of this test's own, so the claim does not depend on what the fixture happens to hold */
    private static final String PROBE = "MT297 import probe";
    private static final int PROBE_SENSOR = 8873;
    private static final int PROBE_TURNOUT = 297;

    /** Which routes were enabled before anything happened, so they can be put back */
    private static Map<Integer, Boolean> enabledBefore;

    /** Which were enabled after the import, and after the setup was parsed */
    private static Map<Integer, Boolean> afterImport;
    private static Map<Integer, Boolean> afterParse;

    /** And after the mechanism was deliberately turned on */
    private static Map<Integer, Boolean> afterActivations;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        java.io.File file = new java.io.File(new java.io.File(sandbox.getFolder(),
            "config" + java.io.File.separator + "autonomy_legacy"), "autonomy.json");

        if (!file.exists()) throw new SkipException("this fixture carries no legacy autonomy.json");

        org.json.JSONObject legacy = new org.json.JSONObject(new String(
            java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));

        model = MarklinControlStation.init(null, true, false, false, false);

        // A ROUTE OF OUR OWN, enabled and watching a sensor.  The fixture's own routes are asserted
        // over too, but a class whose subject exists only if somebody else's file happens to contain
        // one is a class that quietly stops testing anything.
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(PROBE_TURNOUT,
            Accessory.accessoryDecoderType.MM2, true));

        model.newFeedback(PROBE_SENSOR, null);

        model.newRoute(PROBE, commands, PROBE_SENSOR, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
            true, null);

        enabledBefore = enabledStates();

        if (enabledCount(enabledBefore) == 0)
        {
            throw new SkipException("no route is enabled at all, so nothing here could be switched off");
        }

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        session.getStore().createConfiguration("Imported", null);
        session.getStore().setActiveConfiguration("Imported");

        session.rebuild();

        session.importLegacy(legacy);

        afterImport = enabledStates();

        // AND THEN THE SETUP IS RUN, which is the half the entry insists on.
        model.parseAuto(session.buildConfiguration());

        afterParse = enabledStates();

        // AND THE MECHANISM, TURNED ON BY HAND, so the two claims above are not about a railway where
        // nothing could ever have been switched off.  This is what carrying the two excluded keys
        // would have produced.
        if (model.getAutoLayout() != null)
        {
            model.getAutoLayout().setActivateRouteIDs(new ArrayList<Integer>());
            model.getAutoLayout().setActivateRoutes(true);

            model.applyAutonomyRouteActivations();
        }

        afterActivations = enabledStates();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            // PUT THE RAILWAY BACK.  The route database is the real one - `init` restores it from the
            // operator's own state - and the control above deliberately switches every route off.
            if (model != null && enabledBefore != null)
            {
                for (MarklinRoute route : model.getRoutes())
                {
                    Boolean was = enabledBefore.get(route.getId());

                    if (was == null) continue;

                    if (was && !route.isEnabled()) route.enable();

                    if (!was && route.isEnabled()) route.disable();
                }

                if (model.getRoute(PROBE) != null) model.deleteRoute(PROBE);
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The claim: the import itself switches nothing off.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheImportLeavesEveryRouteAsItFoundIt() throws Exception
    {
        assertTrue(sameAsBefore(afterImport).isEmpty(),
            "importing a 2.7.4c graph switched these routes off: " + sameAsBefore(afterImport)
            + ". Nothing about importing a graph should touch a route");
    }

    /**
     * And the claim the entry really turns on: running the setup afterwards switches nothing off
     * either.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testRunningTheImportedSetupLeavesThemOnToo() throws Exception
    {
        assertTrue(sameAsBefore(afterParse).isEmpty(),
            "the import left the routes alone and then parsing the imported setup switched these off: "
            + sameAsBefore(afterParse) + ". The disabling used to happen on LOAD rather than on import,"
            + " which is why this entry asks the question twice - parseAuto ends in"
            + " applyAutonomyRouteActivations, and the legacy file says activateRoutes with an EMPTY"
            + " list");
    }

    /**
     * The control: the thing that would have done it really can do it.
     *
     * Without this, both claims above are satisfied by a railway on which no route could be switched
     * off by anything - which is how a guard against a mechanism outlives the mechanism and reports
     * clean for ever.
     *
     * @throws Exception from the railway
     */
    @Test(dependsOnMethods = "testRunningTheImportedSetupLeavesThemOnToo")
    public void testTheMechanismThatWouldHaveDoneItIsLive() throws Exception
    {
        if (model.getAutoLayout() == null)
        {
            throw new SkipException("the imported setup did not build a running layout, so the"
                + " mechanism could not be turned on");
        }

        assertFalse(sameAsBefore(afterActivations).isEmpty(),
            "turning route activation on with an EMPTY id list switched nothing off, so"
            + " applyAutonomyRouteActivations is not doing what the two claims above are guarding"
            + " against and they would pass with every exclusion removed");
    }

    /** Every route's enabled flag, by id */
    private static Map<Integer, Boolean> enabledStates()
    {
        Map<Integer, Boolean> out = new LinkedHashMap<>();

        for (MarklinRoute route : model.getRoutes()) out.put(route.getId(), route.isEnabled());

        return out;
    }

    private static int enabledCount(Map<Integer, Boolean> states)
    {
        int on = 0;

        for (Boolean value : states.values())
        {
            if (Boolean.TRUE.equals(value)) on++;
        }

        return on;
    }

    /** The routes that were enabled before and are not now, named */
    private static List<String> sameAsBefore(Map<Integer, Boolean> now)
    {
        List<String> lost = new ArrayList<>();

        for (Map.Entry<Integer, Boolean> was : enabledBefore.entrySet())
        {
            if (!Boolean.TRUE.equals(was.getValue())) continue;

            if (!Boolean.TRUE.equals(now.get(was.getKey())))
            {
                MarklinRoute route = model.getRoute(was.getKey());

                lost.add(route == null ? String.valueOf(was.getKey()) : route.getName());
            }
        }

        return lost;
    }
}
