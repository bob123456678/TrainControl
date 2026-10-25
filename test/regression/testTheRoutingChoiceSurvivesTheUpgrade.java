package regression;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A routing rule chosen before 3.0.0 has to reach the configuration, or wait until it can.  The rule was a
 * java Preference pushed into a static on Layout and is now a per-configuration setting, so an upgrading user's
 * choice has to be carried across; if there is no configuration to carry it into, the old key must survive so that
 * it can be carried tomorrow, or RANDOM takes its place silently, for ever.
 *
 * The success path deletes the key only once persistPathPreference answers true.  The branch above it asked whether
 * the migration was needed by looking at the LIVE layout, which carries what this same method wrote into it on the
 * previous call, so the second call concluded the configuration had answered and deleted the key.  The second call
 * is not exotic: the migration runs from loadAutoLayoutSettings, which has fourteen callers.
 */
public class testTheRoutingChoiceSurvivesTheUpgrade
{
    /** What TrainControlUI's preference node was before this class swapped it out. */
    private static java.util.prefs.Preferences realPrefs;

    /** The throwaway node the tests read and write instead. */
    private static java.util.prefs.Preferences ourPrefs;

    /**
     * Gives this class its own preference node, so nothing it writes can reach the operator.  Writing to
     * TrainControlUI's node and restoring afterwards looks safe and is not: TrainControl started DURING a battery run
     * read the key, migrated it, and stored it into the operator's live configuration over the rule he had chosen.
     *
     * So the field is swapped rather than the value.  `prefs` is private static final, which JDK 8 lets us reassign by
     * clearing the final bit; from JDK 12 that is refused, and this asserts rather than falling back, because a guard
     * that silently stops guarding is worse than none.
     */
    @BeforeClass
    public void useOurOwnPreferences() throws Exception
    {
        java.lang.reflect.Field field =
            org.traincontrol.gui.TrainControlUI.class.getDeclaredField("prefs");
        field.setAccessible(true);

        java.lang.reflect.Field modifiers = java.lang.reflect.Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

        realPrefs = (java.util.prefs.Preferences) field.get(null);

        ourPrefs = java.util.prefs.Preferences.userRoot()
            .node("org/traincontrol/test/routingChoice");

        field.set(null, ourPrefs);

        assertSame(org.traincontrol.gui.TrainControlUI.getPrefs(), ourPrefs,
            "the preference node could not be swapped, so this class would write the operator's own "
            + "settings - which is how a battery once changed his routing rule underneath him "
            + "(RC-A12).  On JDK 12 and later the Field.modifiers trick is refused and this needs "
            + "another way in");
    }

    /** Puts the real node back, and takes the throwaway one away with it. */
    @AfterClass(alwaysRun = true)
    public void giveThePreferencesBack() throws Exception
    {
        java.lang.reflect.Field field =
            org.traincontrol.gui.TrainControlUI.class.getDeclaredField("prefs");
        field.setAccessible(true);

        if (realPrefs != null) field.set(null, realPrefs);

        if (ourPrefs != null) ourPrefs.removeNode();
    }

    /** The key as it was spelled before the setting moved - written out, as the migration writes it. */
    private static final String LEGACY = "AutonomyPathPreference";

    /**
     * A refused routing change puts the dropdown back, without asking twice.  Choosing a rule while trains are running
     * is refused, and the dropdown used to keep showing the rule the user picked; its two siblings, timetableCapture
     * and toggleSpecifiedRoutes, restore themselves after the same refusal.
     *
     * Asserted: the control ends up on the rule the layout is actually using, and putting it there does not re-enter
     * the listener that asked for it.  The second is measured by a DEADLINE rather than a value: `setSelectedIndex` on
     * a JComboBox fires an ActionEvent, so an unguarded restore finds the railway still busy and opens a modal dialog
     * that never returns on the event thread.
     */
    @Test
    public void testARefusedRoutingChangePutsTheDropdownBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the dropdown lives on a window");
        }

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedInDebug();

            final org.traincontrol.marklin.MarklinControlStation finalModel = model;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui[0] = new org.traincontrol.gui.TrainControlUI();
                    ui[0].setViewListener(finalModel, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            // The rule the railway is actually following; getAutoLayout builds one if there is none.
            model.getAutoLayout().setPathPreference(
                org.traincontrol.automation.Layout.PathPreference.LONGEST_LENGTH);

            // Busy, without a train moving: isAutonomyBusy() answers true on this flag alone.
            java.lang.reflect.Field staging =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("stagingFlowActive");
            staging.setAccessible(true);
            staging.set(ui[0], true);

            java.lang.reflect.Field combo =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("algorithmType");
            combo.setAccessible(true);

            final javax.swing.JComboBox<String> dropdown =
                (javax.swing.JComboBox<String>) combo.get(ui[0]);

            assertNotNull(dropdown, "the dropdown was never mounted, so this test proves nothing");

            java.lang.reflect.Method restore =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod(
                    "restoreRoutingLogicSelection");
            restore.setAccessible(true);

            // What a user's refused choice leaves behind: a control showing something else, set with the guard held.
            java.lang.reflect.Field guard =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("restoringRoutingLogic");
            guard.setAccessible(true);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    guard.set(ui[0], true);
                    dropdown.setSelectedIndex(0);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
                finally
                {
                    try { guard.set(ui[0], false); } catch (ReflectiveOperationException ignored) { }
                }
            });

            assertEquals(dropdown.getSelectedIndex(), 0, "precondition: the control is on the wrong rule");

            final java.util.concurrent.CountDownLatch done =
                new java.util.concurrent.CountDownLatch(1);

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                try
                {
                    restore.invoke(ui[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
                finally
                {
                    done.countDown();
                }
            });

            assertTrue(done.await(20, java.util.concurrent.TimeUnit.SECONDS),
                "restoring the dropdown never returned.  setSelectedIndex fires an ActionEvent, so an "
                + "unguarded restore re-enters the listener, finds the railway still busy and opens a "
                + "modal dialog on the event thread - which is the second dialog a user would have had "
                + "to dismiss, and here is a hang.  That is what the TODO meant by safely (RC-B11)");

            assertEquals(dropdown.getSelectedIndex(),
                java.util.Arrays.asList(routingOrder()).indexOf(
                    org.traincontrol.automation.Layout.PathPreference.LONGEST_LENGTH),
                "the dropdown does not show the rule the layout is actually using, so the one control "
                + "that reports the routing rule is reporting one that is not in force (RC-B11)");

            assertEquals(model.getAutoLayout().getPathPreference(),
                org.traincontrol.automation.Layout.PathPreference.LONGEST_LENGTH,
                "restoring the control changed the railway's rule, so the restore is writing through "
                + "the listener instead of being ignored by it");
        }
        finally
        {
            if (ui[0] != null)
            {
                javax.swing.SwingUtilities.invokeLater(() -> ui[0].dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /** The order the dropdown speaks, read from the window rather than copied. */
    private static org.traincontrol.automation.Layout.PathPreference[] routingOrder() throws Exception
    {
        java.lang.reflect.Field order =
            org.traincontrol.gui.TrainControlUI.class.getDeclaredField("ROUTING_ORDER");
        order.setAccessible(true);

        return (org.traincontrol.automation.Layout.PathPreference[]) order.get(null);
    }

    /**
     * With nowhere to store it, the choice stays on disk however many times the migration runs.  Each tick of an
     * autonomy settings checkbox calls loadAutoLayoutSettings, which calls the migration.  The session is left null,
     * the state of every user whose autonomy comes from the JSON file, and of every diagram user just after a diagram
     * edit has reset the session.
     */
    @Test
    public void testTheOldChoiceIsKeptUntilSomethingCanStoreIt() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the migration lives on a window");
        }

        // The window's node, which is the throwaway this class installed.
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        String was = prefs.get(LEGACY, null);

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            prefs.put(LEGACY, "SHORTEST_LENGTH");

            // Before the model, not just before the window (OB-111).
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedInDebug();

            final org.traincontrol.marklin.MarklinControlStation finalModel = model;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui[0] = new org.traincontrol.gui.TrainControlUI();
                    ui[0].setViewListener(finalModel, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

        // No session, so there is nowhere to put the answer.
            java.lang.reflect.Field sessionField =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomySession");
            sessionField.setAccessible(true);
            sessionField.set(ui[0], null);

            org.traincontrol.automation.Layout live = new org.traincontrol.automation.Layout(model);

            java.lang.reflect.Method migrate =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod(
                    "migrateStoredPathPreference", org.traincontrol.automation.Layout.class);
            migrate.setAccessible(true);

            migrate.invoke(ui[0], live);

            assertEquals(prefs.get(LEGACY, null), "SHORTEST_LENGTH",
                "the first migration deleted the only durable copy of the choice even though it had "
                + "nothing to store it in - LE-B5's own fix, which is to keep the key until "
                + "persistPathPreference says it landed");

            assertEquals(live.getPathPreference(),
                org.traincontrol.automation.Layout.PathPreference.SHORTEST_LENGTH,
                "the choice did not reach the running layout, so this session routes at random even "
                + "though the user's answer is sitting on disk");

        // The tick of a checkbox: nothing about the world has changed, so nothing about the answer should.
            migrate.invoke(ui[0], live);

            assertEquals(prefs.get(LEGACY, null), "SHORTEST_LENGTH",
                "the SECOND migration deleted the key, because it asked the live layout whether the "
                + "configuration had answered - and the live layout was carrying what the FIRST call "
                + "had just written into it in memory.  Nothing was ever stored, so the user's choice "
                + "is gone and every future session routes at random (RC-A2)");
        }
        finally
        {
            if (was == null) prefs.remove(LEGACY); else prefs.put(LEGACY, was);

            if (ui[0] != null)
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A configuration that exists and has NOT answered gets the old choice written into it: a session with an active
     * configuration carrying no pathPreference, the migration working end to end.
     *
     * MUTATION THIS CATCHES: AutonomySession.getGlobal returning "" instead of null for an absent key.  The guard
     * then fires on the FIRST call, the legacy key is deleted, and nothing is ever stored.
     */
    @Test
    public void testAConfigurationWithNoAnswerIsGivenTheOldOne() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the migration lives on a window");
        }

        // The window's node, which is the throwaway this class installed.
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        String was = prefs.get(LEGACY, null);

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            prefs.put(LEGACY, "SHORTEST_LENGTH");

            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedInDebug();

            final org.traincontrol.marklin.MarklinControlStation finalModel = model;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui[0] = new org.traincontrol.gui.TrainControlUI();
                    ui[0].setViewListener(finalModel, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            java.io.File folder = java.nio.file.Files.createTempDirectory("tc-routing-blank").toFile();

            org.traincontrol.automationui.AutonomySession session =
                new org.traincontrol.automationui.AutonomySession(folder);

            org.traincontrol.base.LayoutDiagram page =
                new org.traincontrol.base.LayoutDiagram("Routing Page", 8, 4, null, null);

            page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK,
                1, 1, 0, 0, 5, 11,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);

            page.setEdit(true);
            page.checkBounds();

            session.open(java.util.Arrays.asList(page));
            session.initialize("Default");

            assertNull(session.getGlobal("pathPreference"),
                "the fixture already carries an answer, so this test would prove nothing");

            java.lang.reflect.Field sessionField =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomySession");
            sessionField.setAccessible(true);
            sessionField.set(ui[0], session);

            org.traincontrol.automation.Layout live = new org.traincontrol.automation.Layout(model);

            java.lang.reflect.Method migrate =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod(
                    "migrateStoredPathPreference", org.traincontrol.automation.Layout.class);
            migrate.setAccessible(true);

            migrate.invoke(ui[0], live);

            assertEquals(session.getGlobal("pathPreference"), "SHORTEST_LENGTH",
                "the choice was not written into the configuration that had room for it - which is "
                + "the migration doing its job, and the only test that watches it happen");

            assertNull(prefs.get(LEGACY, null),
                "the old key survived a migration that succeeded, so it will be offered again next "
                + "start and can overrule a choice made since");
        }
        finally
        {
            if (was == null) prefs.remove(LEGACY); else prefs.put(LEGACY, was);

            if (ui[0] != null)
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A rule already in the loaded layout is not overruled by the pre-upgrade one.  For autonomy from autonomy.json
     * there is no active configuration, so getGlobal can never answer, but Layout.fromJSON DOES read pathPreference;
     * a check asking only the store pushed the pre-upgrade key over it on every settings refresh.
     */
    @Test
    public void testARuleAlreadyInTheLayoutIsNotOverruled() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the migration lives on a window");
        }

        // The window's node, which is the throwaway this class installed.
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        String was = prefs.get(LEGACY, null);

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            prefs.put(LEGACY, "SHORTEST_LENGTH");

            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedInDebug();

            final org.traincontrol.marklin.MarklinControlStation finalModel = model;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui[0] = new org.traincontrol.gui.TrainControlUI();
                    ui[0].setViewListener(finalModel, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            // No session at all, which is the legacy user exactly.
            java.lang.reflect.Field sessionField =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomySession");
            sessionField.setAccessible(true);
            sessionField.set(ui[0], null);

            org.traincontrol.automation.Layout live = new org.traincontrol.automation.Layout(model);

            // Their own answer, as Layout.fromJSON would have left it.
            live.setPathPreference(
                org.traincontrol.automation.Layout.PathPreference.LEAST_RECENTLY_VISITED);

            java.lang.reflect.Method migrate =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod(
                    "migrateStoredPathPreference", org.traincontrol.automation.Layout.class);
            migrate.setAccessible(true);

            migrate.invoke(ui[0], live);

            assertEquals(live.getPathPreference(),
                org.traincontrol.automation.Layout.PathPreference.LEAST_RECENTLY_VISITED,
                "the pre-upgrade preference was pushed over the rule the loaded configuration already "
                + "carried.  For a user whose autonomy comes from autonomy.json there is no store to "
                + "ask, so this happens on every settings refresh and their own file never wins "
                + "(RC-A8)");

            assertNull(prefs.get(LEGACY, null),
                "the loaded layout has its own answer, so the pre-upgrade key is stale and must go - "
                + "kept, it comes back and overrules them at the next start");
        }
        finally
        {
            if (was == null) prefs.remove(LEGACY); else prefs.put(LEGACY, was);

            if (ui[0] != null)
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Once the configuration holds an answer, the old key goes and does not come back: a key kept for ever would
     * overrule a choice made from the dropdown after upgrading.  Both directions in one class because satisfying
     * either alone is trivial and wrong.
     */
    @Test
    public void testTheOldChoiceGoesOnceTheConfigurationHasOne() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the migration lives on a window");
        }

        // The window's node, which is the throwaway this class installed.
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        String was = prefs.get(LEGACY, null);

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            prefs.put(LEGACY, "SHORTEST_LENGTH");

            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedInDebug();

            final org.traincontrol.marklin.MarklinControlStation finalModel = model;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui[0] = new org.traincontrol.gui.TrainControlUI();
                    ui[0].setViewListener(finalModel, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            java.io.File folder = java.nio.file.Files.createTempDirectory("tc-routing-choice").toFile();

            org.traincontrol.automationui.AutonomySession session =
                new org.traincontrol.automationui.AutonomySession(folder);

            org.traincontrol.base.LayoutDiagram page =
                new org.traincontrol.base.LayoutDiagram("Routing Page", 8, 4, null, null);

            page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK,
                1, 1, 0, 0, 5, 11,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);

            page.setEdit(true);
            page.checkBounds();

            session.open(java.util.Arrays.asList(page));
            session.initialize("Default");

            // The user's answer, already in the configuration, whether by an earlier migration or by the dropdown.
            assertTrue(session.setGlobal("pathPreference", "LONGEST_LENGTH"),
                "the fixture could not store a global, so this test would pass for the wrong reason");

            java.lang.reflect.Field sessionField =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomySession");
            sessionField.setAccessible(true);
            sessionField.set(ui[0], session);

            org.traincontrol.automation.Layout live = new org.traincontrol.automation.Layout(model);

            java.lang.reflect.Method migrate =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod(
                    "migrateStoredPathPreference", org.traincontrol.automation.Layout.class);
            migrate.setAccessible(true);

            migrate.invoke(ui[0], live);

            assertNull(prefs.get(LEGACY, null),
                "the pre-upgrade key survived even though the configuration has its own answer - so a "
                + "choice the user makes from the dropdown is overruled by the old one at every start "
                + "(RC-A2)");

            assertEquals(session.getGlobal("pathPreference"), "LONGEST_LENGTH",
                "the migration overwrote the configuration's own answer with the pre-upgrade one");
        }
        finally
        {
            if (was == null) prefs.remove(LEGACY); else prefs.put(LEGACY, was);

            if (ui[0] != null)
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The Auto tab on a layout whose autonomy is an `autonomy.json` stays SHUT (MT-244; Adam: "The tab should stay
     * shut until they import.").  This claim was inverted: nothing at start-up parses or activates `autonomy.json`
     * any more (the only `parseAuto` caller in `src` is the diagram path, OB-254), so the tab would stand in front of
     * an empty model.  Inverting into a guard on the new invariant beats deleting, because the scenario is still the
     * one that matters.
     */
    @Test
    public void testTheAutoTabStaysShutWithALegacyAutonomyJson() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the tab is on a window");
        }

        support.LayoutSandbox sandbox = null;

        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        org.traincontrol.marklin.MarklinControlStation model = null;

        try
        {
            sandbox = support.LayoutSandbox.open();

            // THE LEGACY STATE, which the fixture is not: it ships a diagram configuration called Main, which asking
            // for a session makes active.  An upgrading user has NO diagram configuration and an autonomy.json beside
            // it.
            java.io.File diagramSetups = new java.io.File(sandbox.getFolder(), "config/autonomy");

            if (diagramSetups.isDirectory())
            {
                for (java.io.File f : diagramSetups.listFiles()) f.delete();

                diagramSetups.delete();
            }

            assertFalse(diagramSetups.exists(),
                "the sandbox still has a diagram setup folder, so this is not the legacy state");

            model = support.TestStation.simulated();
            model.stop();

            javax.swing.SwingUtilities.invokeAndWait(() ->
                ui[0] = new org.traincontrol.gui.TrainControlUI());

            ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

            // The legacy path: a graph parsed straight from an autonomy.json, with no diagram configuration behind it.
            String legacy = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "test/operator_layout/config/autonomy_legacy/autonomy.json")),
                java.nio.charset.StandardCharsets.UTF_8);

            model.parseAuto(legacy);

            assertTrue(model.hasAutoLayout() && model.getAutoLayout().isValid(),
                "precondition: the legacy file must parse, or the tab is off for a reason that has "
                + "nothing to do with this finding.  " + org.traincontrol.automation.Layout.getLastError());

            java.lang.reflect.Field active = org.traincontrol.gui.TrainControlUI.class
                .getDeclaredField("activeDiagramConfiguration");

            active.setAccessible(true);

            assertNull(active.get(ui[0]),
                "precondition: this test is about a layout with NO diagram configuration active");

            javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].refreshAutonomyTabState());

            java.lang.reflect.Field tabs = org.traincontrol.gui.TrainControlUI.class
                .getDeclaredField("KeyboardTab");

            tabs.setAccessible(true);

            javax.swing.JTabbedPane pane = (javax.swing.JTabbedPane) tabs.get(ui[0]);

            assertTrue(pane.getTabCount() > 2, "the window has no Auto tab to ask about");

            // Before a session exists the tab's state is whatever start-up left; the claim is about the state AFTER
            // one.
            boolean beforeASession = pane.isEnabledAt(2);

            // AND WITH A SESSION IN EXISTENCE: `loaded` is `session == null || activeDiagramConfiguration != null`, so
            // the tab is only at risk once something has built a session; asking for it makes this test discriminate.
            assertNotNull(ui[0].getAutonomySession(),
                "asking for a session on a local layout did not produce one, so the state this "
                + "finding is about cannot be reached from here and the assertion below proves "
                + "nothing");

            assertNull(active.get(ui[0]),
                "building a session set an active diagram configuration, so this is no longer the "
                + "legacy state");

            javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].refreshAutonomyTabState());

            assertFalse(pane.isEnabledAt(2),
                "the Auto tab is OPEN on a layout whose autonomy is an autonomy.json and which has no"
                + " diagram configuration. Adam, 2026-09-13: \"The tab should stay shut until they"
                + " import.\" Nothing at start-up activates that file any more, so the tab is not"
                + " holding a running railway for this user - it is offering a way into the old model"
                + " instead of the import. (It was "
                + (beforeASession ? "open" : "shut") + " before a session existed.)");
        }
        finally
        {
            if (ui[0] != null)
            {
                final org.traincontrol.gui.TrainControlUI closing = ui[0];

                javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }
}
