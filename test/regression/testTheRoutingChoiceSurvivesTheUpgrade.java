package regression;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import support.Diagrams;
import support.Display;
import support.Reflect;

/**
 * A routing rule chosen before 3.0.0 has to reach the configuration, or wait until it can.  It was a java Preference
 * pushed into a static on Layout and is now a per-configuration setting; with no configuration to carry it into the old
 * key must survive, or RANDOM takes its place silently, for ever.
 *
 * The success path deletes the key only once persistPathPreference answers true.  The branch above it once looked at
 * the LIVE layout, which carries what this method wrote on the previous call, so the second call deleted the key (the
 * migration runs from loadAutoLayoutSettings, which has fourteen callers).
 */
public class testTheRoutingChoiceSurvivesTheUpgrade
{
    /** What TrainControlUI's preference node was before this class swapped it out. */
    private static java.util.prefs.Preferences realPrefs;

    /** The throwaway node the tests read and write instead. */
    private static java.util.prefs.Preferences ourPrefs;

    /**
     * Gives this class its own preference node, so nothing it writes can reach the operator: a TrainControl started
     * DURING a battery run read the key from TrainControlUI's node and stored it into the operator's live
     * configuration.  So the field is swapped, not the value: `prefs` is private static final, reassignable on JDK 8 by
     * clearing the final bit; from JDK 12 this asserts rather than falling back (a guard that silently stops guarding
     * is worse).
     */
    @BeforeClass
    public void useOurOwnPreferences() throws Exception
    {
        java.lang.reflect.Field field = Reflect.field(org.traincontrol.gui.TrainControlUI.class, "prefs");

        java.lang.reflect.Field modifiers = Reflect.field(java.lang.reflect.Field.class, "modifiers");
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
        if (realPrefs != null) Reflect.set(org.traincontrol.gui.TrainControlUI.class, null, "prefs", realPrefs);

        if (ourPrefs != null) ourPrefs.removeNode();
    }

    /** The key as it was spelled before the setting moved - written out, as the migration writes it. */
    private static final String LEGACY = "AutonomyPathPreference";

    /**
     * A refused routing change puts the dropdown back, without asking twice: choosing a rule while trains run is
     * refused, and the dropdown kept the rule picked (its siblings timetableCapture and toggleSpecifiedRoutes restore
     * themselves).  Asserted: the control ends up on the rule the layout uses, and does not re-enter the listener,
     * measured by a DEADLINE (`setSelectedIndex` fires an ActionEvent, so an unguarded restore opens a modal dialog
     * that never returns).
     */
    @Test
    public void testARefusedRoutingChangePutsTheDropdownBack() throws Exception
    {
        Display.needed("the dropdown lives on a window");

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedInDebug();

            ui[0] = newWindow(model);

            // The rule the railway is actually following; getAutoLayout builds one if there is none.
            model.getAutoLayout().setPathPreference(
                org.traincontrol.automation.Layout.PathPreference.LONGEST_LENGTH);

            // Busy, without a train moving: isAutonomyBusy() answers true on this flag alone.
            Reflect.set(org.traincontrol.gui.TrainControlUI.class, ui[0], "stagingFlowActive", true);

            java.lang.reflect.Field combo = Reflect.field(org.traincontrol.gui.TrainControlUI.class, "algorithmType");

            final javax.swing.JComboBox<String> dropdown =
                (javax.swing.JComboBox<String>) combo.get(ui[0]);

            assertNotNull(dropdown, "the dropdown was never mounted, so this test proves nothing");

            java.lang.reflect.Method restore =
                Reflect.method(org.traincontrol.gui.TrainControlUI.class, "restoreRoutingLogicSelection");

            // What a user's refused choice leaves behind: a control showing something else, set with the guard held.
            java.lang.reflect.Field guard =
                Reflect.field(org.traincontrol.gui.TrainControlUI.class, "restoringRoutingLogic");

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
        java.lang.reflect.Field order = Reflect.field(org.traincontrol.gui.TrainControlUI.class, "ROUTING_ORDER");

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
        Display.needed("the migration lives on a window");

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

            ui[0] = newWindow(model);

        // No session, so there is nowhere to put the answer.
            giveTheWindowThisSession(ui[0], null);

            org.traincontrol.automation.Layout live = new org.traincontrol.automation.Layout(model);

            migrateTheStoredChoice(ui[0], live);

            assertEquals(prefs.get(LEGACY, null), "SHORTEST_LENGTH",
                "the first migration deleted the only durable copy of the choice even though it had "
                + "nothing to store it in - LE-B5's own fix, which is to keep the key until "
                + "persistPathPreference says it landed");

            assertEquals(live.getPathPreference(),
                org.traincontrol.automation.Layout.PathPreference.SHORTEST_LENGTH,
                "the choice did not reach the running layout, so this session routes at random even "
                + "though the user's answer is sitting on disk");

        // The tick of a checkbox: nothing about the world has changed, so nothing about the answer should.
            migrateTheStoredChoice(ui[0], live);

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
     * MUTATION THIS CATCHES: in `AutonomySettingsTab`'s migration, take any stored configuration as having answered (drop
     * `stored.getGlobal("pathPreference") != null`): the legacy key is deleted and nothing is ever stored.
     */
    @Test
    public void testAConfigurationWithNoAnswerIsGivenTheOldOne() throws Exception
    {
        Display.needed("the migration lives on a window");

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

            ui[0] = newWindow(model);

            java.io.File folder = java.nio.file.Files.createTempDirectory("tc-routing-blank").toFile();

            org.traincontrol.automationui.AutonomySession session =
                new org.traincontrol.automationui.AutonomySession(folder);

            org.traincontrol.base.LayoutDiagram page =
                new org.traincontrol.base.LayoutDiagram("Routing Page", 8, 4, null, null);

            Diagrams.feedback(page, 1, 1, 5, 11);

            page.setEdit(true);
            page.checkBounds();

            session.open(java.util.Arrays.asList(page));
            session.initialize("Default");

            assertNull(session.getGlobal("pathPreference"),
                "the fixture already carries an answer, so this test would prove nothing");

            giveTheWindowThisSession(ui[0], session);

            org.traincontrol.automation.Layout live = new org.traincontrol.automation.Layout(model);

            migrateTheStoredChoice(ui[0], live);

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
        Display.needed("the migration lives on a window");

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

            ui[0] = newWindow(model);

            // No session at all, which is the legacy user exactly.
            giveTheWindowThisSession(ui[0], null);

            org.traincontrol.automation.Layout live = new org.traincontrol.automation.Layout(model);

            // Their own answer, as Layout.fromJSON would have left it.
            live.setPathPreference(
                org.traincontrol.automation.Layout.PathPreference.LEAST_RECENTLY_VISITED);

            migrateTheStoredChoice(ui[0], live);

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
        Display.needed("the migration lives on a window");

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

            ui[0] = newWindow(model);

            java.io.File folder = java.nio.file.Files.createTempDirectory("tc-routing-choice").toFile();

            org.traincontrol.automationui.AutonomySession session =
                new org.traincontrol.automationui.AutonomySession(folder);

            org.traincontrol.base.LayoutDiagram page =
                new org.traincontrol.base.LayoutDiagram("Routing Page", 8, 4, null, null);

            Diagrams.feedback(page, 1, 1, 5, 11);

            page.setEdit(true);
            page.checkBounds();

            session.open(java.util.Arrays.asList(page));
            session.initialize("Default");

            // The user's answer, already in the configuration, whether by an earlier migration or by the dropdown.
            assertTrue(session.setGlobal("pathPreference", "LONGEST_LENGTH"),
                "the fixture could not store a global, so this test would pass for the wrong reason");

            giveTheWindowThisSession(ui[0], session);

            org.traincontrol.automation.Layout live = new org.traincontrol.automation.Layout(model);

            migrateTheStoredChoice(ui[0], live);

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
     * The Auto tab on a layout whose autonomy is an `autonomy.json` stays SHUT (MT-244): nothing at start-up parses or
     * activates `autonomy.json` any more (OB-254), so the tab would stand in front of an empty model.
     */
    @Test
    public void testTheAutoTabStaysShutWithALegacyAutonomyJson() throws Exception
    {
        Display.needed("the tab is on a window");

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

            java.lang.reflect.Field active =
                Reflect.field(org.traincontrol.gui.TrainControlUI.class, "activeDiagramConfiguration");

            assertNull(active.get(ui[0]),
                "precondition: this test is about a layout with NO diagram configuration active");

            javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].refreshAutonomyTabState());

            java.lang.reflect.Field tabs = Reflect.field(org.traincontrol.gui.TrainControlUI.class, "KeyboardTab");

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

    /** Sets the window's autonomy session field, which is null for a user whose autonomy comes from autonomy.json. */
    private static void giveTheWindowThisSession(org.traincontrol.gui.TrainControlUI ui, Object session)
        throws Exception
    {
        Reflect.set(org.traincontrol.gui.TrainControlUI.class, ui, "autonomySession", session);
    }

    /** Runs the window's start-up migration of the stored path preference onto `live`. */
    private static void migrateTheStoredChoice(org.traincontrol.gui.TrainControlUI ui,
        org.traincontrol.automation.Layout live) throws Exception
    {
        java.lang.reflect.Method migrate = Reflect.method(org.traincontrol.gui.TrainControlUI.class,
            "migrateStoredPathPreference", org.traincontrol.automation.Layout.class);

        migrate.invoke(ui, live);
    }

    /** A window on `model`, built on the event thread. */
    private static org.traincontrol.gui.TrainControlUI newWindow(final org.traincontrol.marklin.MarklinControlStation model)
        throws Exception
    {
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ui[0] = new org.traincontrol.gui.TrainControlUI();
                ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });

        return ui[0];
    }

    /** What a claim does with a window, a simulated station and a sandboxed layout. */
    private interface Body
    {
        void run(org.traincontrol.gui.TrainControlUI ui, org.traincontrol.marklin.MarklinControlStation model)
            throws Exception;
    }

    /** Runs `body` on a fresh window; the legacy key is put back and everything is closed after. */
    private static void inAWindow(Body body) throws Exception
    {
        Display.needed("the routing dropdown lives on a window");

        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        String was = prefs.get(LEGACY, null);

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            prefs.remove(LEGACY);

            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedInDebug();

            ui[0] = newWindow(model);

            // The station was started with no view; the log reaches the window only through this.
            Reflect.set(org.traincontrol.marklin.MarklinControlStation.class, model, "view", ui[0]);

            // The window loads its settings after setViewListener; wait for that load (bounded) so it cannot undo a claim.
            long until = System.currentTimeMillis() + 10000;

            while (!model.hasAutoLayout() && System.currentTimeMillis() < until) settle();

            model.getAutoLayout();

            settle();
            settle();

            body.run(ui[0], model);
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

    /** A setup with one configuration, so it has room for the answer. */
    private static org.traincontrol.automationui.AutonomySession aSessionWithRoom() throws Exception
    {
        return aSessionWithRoom(java.nio.file.Files.createTempDirectory("tc-routing-room").toFile());
    }

    private static org.traincontrol.automationui.AutonomySession aSessionWithRoom(java.io.File folder) throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = new org.traincontrol.automationui.AutonomySession(folder);

        org.traincontrol.base.LayoutDiagram page = new org.traincontrol.base.LayoutDiagram("Routing Page", 8, 4, null, null);

        Diagrams.feedback(page, 1, 1, 5, 11);

        page.setEdit(true);
        page.checkBounds();

        session.open(java.util.Arrays.asList(page));
        session.initialize("Default");

        return session;
    }

    private static javax.swing.JComboBox<?> dropdownOf(org.traincontrol.gui.TrainControlUI ui) throws Exception
    {
        return (javax.swing.JComboBox<?>) Reflect.get(org.traincontrol.gui.TrainControlUI.class, ui, "algorithmType");
    }

    /** What the window's log shows, after what is queued has run. */
    private static String logOf(org.traincontrol.gui.TrainControlUI ui) throws Exception
    {
        settle();

        return ((javax.swing.JTextArea) Reflect.get(org.traincontrol.gui.TrainControlUI.class, ui, "debugArea")).getText();
    }

    /** Lets the event thread finish what is queued, twice: the dropdown's listener queues its work. */
    private static void settle() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /** Picks `rule` in the dropdown as a person would. */
    private static void pick(org.traincontrol.gui.TrainControlUI ui, org.traincontrol.automation.Layout.PathPreference rule)
        throws Exception
    {
        final int at = java.util.Arrays.asList(routingOrder()).indexOf(rule);

        final javax.swing.JComboBox<?> dropdown = dropdownOf(ui);

        javax.swing.SwingUtilities.invokeAndWait(() -> dropdown.setSelectedIndex(at));

        settle();
    }

    /** A chosen rule is written to the layout and the setup, explained on the control, and not said to have failed. */
    @Test
    public void testAChosenRuleIsKeptShownAndExplained() throws Exception
    {
        inAWindow((ui, model) ->
        {
            org.traincontrol.automation.Layout.PathPreference rule = org.traincontrol.automation.Layout.PathPreference.FEWEST_POINTS;

            org.traincontrol.automationui.AutonomySession session = aSessionWithRoom();

            giveTheWindowThisSession(ui, session);

            model.getAutoLayout().setPathPreference(org.traincontrol.automation.Layout.PathPreference.RANDOM);

            pick(ui, rule);

            assertEquals(model.getAutoLayout().getPathPreference(), rule, "the chosen rule is not the layout's");
            assertEquals(session.getGlobal("pathPreference"), rule.name(), "the chosen rule did not reach the setup");

            assertTrue(dropdownOf(ui).getToolTipText().contains(
                org.traincontrol.util.I18n.t("autolayout.ui.tooltip.pathPreference" + rule.name())),
                "the control does not explain the rule just chosen");

            assertFalse(logOf(ui).contains(org.traincontrol.util.I18n.f("autolayout.warnRoutingRuleNotStored",
                org.traincontrol.util.I18n.t("autolayout.ui.pathPreference" + rule.name()))),
                "a rule that was stored was reported as not stored");

            // A pick equal to the layout's rule writes nothing, and no pick at all changes nothing.
            org.traincontrol.automation.Layout.PathPreference inForce =
                org.traincontrol.automation.Layout.PathPreference.LONGEST_LENGTH;

            model.getAutoLayout().setPathPreference(inForce);

            session.setGlobal("pathPreference", "MOST_POINTS");

            pick(ui, inForce);

            assertEquals(session.getGlobal("pathPreference"), "MOST_POINTS", "choosing the rule in force wrote the setup");

            final javax.swing.JComboBox<?> none = dropdownOf(ui);

            javax.swing.SwingUtilities.invokeAndWait(() -> none.setSelectedIndex(-1));

            settle();

            assertEquals(model.getAutoLayout().getPathPreference(), inForce, "choosing nothing changed the rule");
        });
    }

    /** A rule the setup cannot keep is said aloud, and still used. */
    @Test
    public void testARuleThatCannotBeStoredIsSaidAloud() throws Exception
    {
        inAWindow((ui, model) ->
        {
            org.traincontrol.automation.Layout.PathPreference rule = org.traincontrol.automation.Layout.PathPreference.MOST_STATIONS;

            // Opened with no configuration: setGlobal has nowhere to write and says so.
            org.traincontrol.automationui.AutonomySession bare = new org.traincontrol.automationui.AutonomySession(
                java.nio.file.Files.createTempDirectory("tc-routing-bare").toFile());

            giveTheWindowThisSession(ui, bare);

            model.getAutoLayout().setPathPreference(org.traincontrol.automation.Layout.PathPreference.RANDOM);

            pick(ui, rule);

            assertEquals(model.getAutoLayout().getPathPreference(), rule, "the rule was not used");

            assertTrue(logOf(ui).contains(org.traincontrol.util.I18n.f("autolayout.warnRoutingRuleNotStored",
                org.traincontrol.util.I18n.t("autolayout.ui.pathPreference" + rule.name()))),
                "a rule that could not be kept was not said to be");
        });
    }

    /** A pick with no setup yet builds it for the layout (a person clicked); a refresh never does. */
    @Test
    public void testAPickBuildsTheSetupButARefreshDoesNot() throws Exception
    {
        inAWindow((ui, model) ->
        {
            java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

            prefs.put(LEGACY, "SHORTEST_LENGTH");

            giveTheWindowThisSession(ui, null);

            migrateTheStoredChoice(ui, new org.traincontrol.automation.Layout(model));

            assertNull(Reflect.get(org.traincontrol.gui.TrainControlUI.class, ui, "autonomySession"),
                "a settings refresh built the setup");

            model.getAutoLayout().setPathPreference(org.traincontrol.automation.Layout.PathPreference.RANDOM);

            pick(ui, org.traincontrol.automation.Layout.PathPreference.MOST_POINTS);

            assertNotNull(Reflect.get(org.traincontrol.gui.TrainControlUI.class, ui, "autonomySession"),
                "a person's pick did not build the setup that keeps the rule");
        });
    }

    /** Putting the dropdown back holds the flag while the selection event is delivered, shows the rule, and explains it. */
    @Test
    public void testRestoringTheDropdownHoldsTheFlagAndExplainsTheRule() throws Exception
    {
        inAWindow((ui, model) ->
        {
            org.traincontrol.automation.Layout.PathPreference rule = org.traincontrol.automation.Layout.PathPreference.LONGEST_LENGTH;

            model.getAutoLayout().setPathPreference(rule);

            final javax.swing.JComboBox<?> dropdown = dropdownOf(ui);

            final java.util.List<Object> flagSeen = new java.util.ArrayList<>();

            final java.lang.reflect.Field flag =
                Reflect.field(org.traincontrol.gui.TrainControlUI.class, "restoringRoutingLogic");

            flag.setAccessible(true);

            dropdown.addActionListener(e ->
            {
                try
                {
                    flagSeen.add(flag.get(ui));
                }
                catch (ReflectiveOperationException x)
                {
                    throw new IllegalStateException(x);
                }
            });

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    flag.set(ui, true);
                    dropdown.setSelectedIndex(0);
                    flag.set(ui, false);
                    flagSeen.clear();

                    Reflect.method(org.traincontrol.gui.TrainControlUI.class, "restoreRoutingLogicSelection").invoke(ui);
                }
                catch (ReflectiveOperationException x)
                {
                    throw new IllegalStateException(x);
                }
            });

            assertEquals(flagSeen, java.util.Arrays.asList(true), "the selection event was not delivered under the flag");
            assertEquals(flag.get(ui), false, "the flag was left held");
            assertEquals(dropdown.getSelectedIndex(), java.util.Arrays.asList(routingOrder()).indexOf(rule));

            assertTrue(dropdown.getToolTipText().contains(
                org.traincontrol.util.I18n.t("autolayout.ui.tooltip.pathPreference" + rule.name())),
                "the restored control does not explain the rule shown");
        });
    }

    /** Nothing to carry: no layout, a stored choice this build does not have, or no stored choice. */
    @Test
    public void testNothingToCarryLeavesEverythingAlone() throws Exception
    {
        inAWindow((ui, model) ->
        {
            java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

            giveTheWindowThisSession(ui, aSessionWithRoom());

            org.traincontrol.automation.Layout live = new org.traincontrol.automation.Layout(model);

            // No loaded layout: nothing to carry it onto, and the key is kept.
            prefs.put(LEGACY, "SHORTEST_LENGTH");

            migrateTheStoredChoice(ui, null);

            assertEquals(prefs.get(LEGACY, null), "SHORTEST_LENGTH", "a migration with no layout deleted the key");

            // A choice this build does not have: kept so it can be retried, the layout unchanged.
            prefs.put(LEGACY, "NO_SUCH_RULE");

            migrateTheStoredChoice(ui, live);

            assertEquals(prefs.get(LEGACY, null), "NO_SUCH_RULE", "an unknown stored choice was deleted");
            assertEquals(live.getPathPreference(), org.traincontrol.automation.Layout.PathPreference.RANDOM);

            // Nothing stored: nothing changes and nothing is reported.
            prefs.remove(LEGACY);

            String before = logOf(ui);

            migrateTheStoredChoice(ui, live);

            assertEquals(logOf(ui), before, "a migration with nothing stored reported something");
            assertEquals(live.getPathPreference(), org.traincontrol.automation.Layout.PathPreference.RANDOM);
        });
    }

    /** A pick refused because a run holds the setup is put back before the refusal is said, and changes nothing. */
    @Test
    public void testARefusedPickIsPutBackBeforeItIsSaid() throws Exception
    {
        inAWindow((ui, model) ->
        {
            org.traincontrol.automation.Layout.PathPreference inForce = org.traincontrol.automation.Layout.PathPreference.LONGEST_LENGTH;

            model.getAutoLayout().setPathPreference(inForce);

            giveTheWindowThisSession(ui, aSessionWithRoom());

            final javax.swing.JComboBox<?> dropdown = dropdownOf(ui);

            final int inForceAt = java.util.Arrays.asList(routingOrder()).indexOf(inForce);

            javax.swing.SwingUtilities.invokeAndWait(() -> dropdown.setSelectedIndex(inForceAt));

            settle();

            Reflect.set(org.traincontrol.gui.TrainControlUI.class, ui, "stagingFlowActive", true);

            // Not invokeAndWait: the refusal is a modal question that holds the event thread until it is closed.
            javax.swing.SwingUtilities.invokeLater(() -> dropdown.setSelectedIndex(inForceAt == 0 ? 1 : 0));

            boolean said = false;
            long until = System.currentTimeMillis() + 10000;

            while (!said && System.currentTimeMillis() < until)
            {
                settle();

                for (java.awt.Window w : java.awt.Window.getWindows())
                {
                    if (w instanceof javax.swing.JDialog && w.isShowing())
                    {
                        // The question is up: the dropdown has already been put back behind it.
                        final java.awt.Window question = w;

                        said = true;

                        javax.swing.SwingUtilities.invokeLater(question::dispose);
                    }
                }
            }

            assertTrue(said, "the refused pick was not said");

            settle();

            assertEquals(dropdown.getSelectedIndex(), inForceAt, "a refused pick was left showing on the control");
            assertEquals(model.getAutoLayout().getPathPreference(), inForce, "a refused pick changed the rule");
        });
    }

    /** A pick whose layout is gone by the time it is handled does nothing, and does not build another. */
    @Test
    public void testAPickWhoseLayoutIsGoneDoesNothing() throws Exception
    {
        inAWindow((ui, model) ->
        {
            final javax.swing.JComboBox<?> dropdown = dropdownOf(ui);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                dropdown.setSelectedIndex(4);
                model.clearAutoLayout();
            });

            settle();

            assertFalse(model.hasAutoLayout(), "a pick handled after the layout went built a layout");
        });
    }

    /** A rule whose setup cannot be written to is said aloud, whatever the cause. */
    @Test
    public void testARuleTheSetupFailsToWriteIsSaidAloud() throws Exception
    {
        inAWindow((ui, model) ->
        {
            org.traincontrol.automation.Layout.PathPreference rule = org.traincontrol.automation.Layout.PathPreference.MOST_POINTS;

            java.io.File folder = java.nio.file.Files.createTempDirectory("tc-routing-gone").toFile();

            giveTheWindowThisSession(ui, aSessionWithRoom(folder));

            model.getAutoLayout().setPathPreference(org.traincontrol.automation.Layout.PathPreference.RANDOM);

            // The folder the setup saves into goes away: the save throws.

            java.nio.file.Files.walk(folder.toPath()).sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());

            folder.createNewFile();

            pick(ui, rule);

            assertTrue(logOf(ui).contains(org.traincontrol.util.I18n.f("autolayout.warnRoutingRuleNotStored",
                org.traincontrol.util.I18n.t("autolayout.ui.pathPreference" + rule.name()))),
                "a rule whose save threw was not said to be unkept");
        });
    }

    /** The dropdown and its label are built from the designer's controls: one item per rule, in the language, capped. */
    @Test
    public void testTheDropdownIsMountedFromTheDesigner() throws Exception
    {
        inAWindow((ui, model) ->
        {
            javax.swing.JComboBox<?> dropdown = dropdownOf(ui);

            org.traincontrol.automation.Layout.PathPreference[] order = routingOrder();

            assertEquals(dropdown.getItemCount(), order.length, "the designer's own items were kept, or a rule is missing");

            for (int at = 0; at < order.length; at++)
            {
                assertEquals(dropdown.getItemAt(at),
                    org.traincontrol.util.I18n.t("autolayout.ui.pathPreference" + order[at].name()), "item " + at);
            }

            javax.swing.JLabel label = (javax.swing.JLabel) Reflect.get(org.traincontrol.gui.TrainControlUI.class, ui, "jLabel2");

            assertEquals(label.getText(), org.traincontrol.util.I18n.t("ui.main.routingLogic"), "the label is not in the language");
            assertEquals(label.getToolTipText(), org.traincontrol.util.I18n.t("ui.main.tooltip.routingLogic"));

            assertEquals(dropdown.getPreferredSize().width, 230, "the dropdown's preferred width is not the capped one");
            assertEquals(dropdown.getMaximumSize().width, 230, "the dropdown's maximum width is not the capped one");

            assertTrue(dropdown.getToolTipText().contains(
                org.traincontrol.util.I18n.t("autolayout.ui.tooltip.pathPreference" + order[dropdown.getSelectedIndex()].name())),
                "the dropdown starts with no explanation of the rule it shows");
        });
    }

    /**
     * A window explains the rule it starts on as soon as it is built, before any layout is loaded.
     *
     * The model is null at that point and the restore returns early until an Auto layout exists, so the mount's own
     * refresh is the only thing that gives the dropdown its rule text then.
     *
     * MUTATION this catches: removing the `refreshRoutingLogicTooltip()` call from `AutonomySettingsTab.mountRoutingLogic`.
     */
    @Test
    public void testTheDropdownExplainsItsRuleOnceBuilt() throws Exception
    {
        Display.needed("the routing dropdown lives on a window");

        support.LayoutSandbox sandbox = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            sandbox = support.LayoutSandbox.open();

            javax.swing.SwingUtilities.invokeAndWait(() -> ui[0] = new org.traincontrol.gui.TrainControlUI());

            javax.swing.JComboBox<?> dropdown = dropdownOf(ui[0]);

            assertEquals(dropdown.getSelectedIndex(), 0, "the window does not start on the first rule");

            assertTrue(dropdown.getToolTipText().contains(org.traincontrol.util.I18n.t(
                "autolayout.ui.tooltip.pathPreference" + routingOrder()[0].name())),
                "a window just built does not explain the rule it shows");
        }
        finally
        {
            if (ui[0] != null) javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].dispose());

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A refused pick puts the Auto route list back to the routes the layout activates, not only the dropdown.
     *
     * The list's selection can drift from the layout by keyboard (arrow keys, Ctrl-A fire no mouse release), and the
     * refusal is the one place that is put right again.
     *
     * MUTATION this catches: dropping `this.ui.applyAutoRouteListSelections()` from the putBack in `AutonomySettingsTab.mountRoutingLogic`.
     */
    @Test
    public void testARefusedPickPutsTheRouteListBack() throws Exception
    {
        inAWindow((ui, model) ->
        {
            for (int id : new int[] { 9831, 9832 })
            {
                model.newRoute("RL route " + id, id,
                    java.util.Collections.singletonList(org.traincontrol.base.RouteCommand.RouteCommandStop()),
                    8800 + id % 10, org.traincontrol.marklin.MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);
            }

            final int a = model.getRoute("RL route 9831").getId();
            final int b = model.getRoute("RL route 9832").getId();

            model.getAutoLayout().setActivateRouteIDs(java.util.Collections.singletonList(a));

            support.Components.onTheEventThread(() -> Reflect.method(org.traincontrol.gui.TrainControlUI.class,
                "updateAutoRouteList").invoke(ui));

            final javax.swing.JList<?> list = (javax.swing.JList<?>) Reflect.get(org.traincontrol.gui.TrainControlUI.class,
                ui, "autoRouteList");

            final int atA = indexOfRoute(list, a);
            final int atB = indexOfRoute(list, b);

            assertTrue(atA >= 0 && atB >= 0 && atA != atB, "the route list does not hold both routes");

            giveTheWindowThisSession(ui, aSessionWithRoom());

            final javax.swing.JComboBox<?> dropdown = dropdownOf(ui);

            final int inForceAt = dropdown.getSelectedIndex();

            Reflect.set(org.traincontrol.gui.TrainControlUI.class, ui, "stagingFlowActive", true);

            // The selection drifts to B with no event the layout hears.
            javax.swing.SwingUtilities.invokeAndWait(() -> list.setSelectedIndex(atB));

            assertEquals(list.getSelectedIndices(), new int[] { atB }, "the drift was not set up");

            // Not invokeAndWait: the refusal is a modal question that holds the event thread until it is closed.
            javax.swing.SwingUtilities.invokeLater(() -> dropdown.setSelectedIndex(inForceAt == 0 ? 1 : 0));

            boolean said = false;
            long until = System.currentTimeMillis() + 10000;

            while (!said && System.currentTimeMillis() < until)
            {
                settle();

                for (java.awt.Window w : java.awt.Window.getWindows())
                {
                    if (w instanceof javax.swing.JDialog && w.isShowing())
                    {
                        final java.awt.Window question = w;

                        said = true;

                        javax.swing.SwingUtilities.invokeLater(question::dispose);
                    }
                }
            }

            assertTrue(said, "the refused pick was not said");

            settle();

            assertEquals(list.getSelectedIndices(), new int[] { atA }, "a refused pick left the route list off the layout's routes");
            assertEquals(model.getAutoLayout().getActivateRouteIDs(), java.util.Collections.singletonList(a));
        });
    }

    private static int indexOfRoute(javax.swing.JList<?> list, int id)
    {
        for (int i = 0; i < list.getModel().getSize(); i++)
        {
            if (((org.traincontrol.marklin.MarklinRoute) list.getModel().getElementAt(i)).getId() == id) return i;
        }

        return -1;
    }
}
