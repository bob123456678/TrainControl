package regression;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A routing rule chosen before 3.0.0 has to reach the configuration, or wait until it can (RC-A2).
 *
 * Until 3.0.0 the routing rule was a java Preference, read at startup and pushed into a static on
 * Layout.  It is now a per-configuration setting, so an upgrading user's choice has to be carried
 * across - and if it cannot be carried today, because there is no configuration to carry it into, the
 * old key must survive so that it can be carried tomorrow.  Deleting it early means the user's choice
 * is gone and RANDOM takes its place, silently, for ever.
 *
 * LE-B5 got the SUCCESS path right: the key goes only once persistPathPreference answers true.  What
 * it left was the branch above, which asked whether the migration was needed by looking at the LIVE
 * layout - and the live layout carries what this same method wrote into it in memory on the previous
 * call.  So the first call stored nothing and kept the key, and the second call read its own writing,
 * concluded the configuration had answered, and deleted it.
 *
 * The second call is not exotic: the migration runs from loadAutoLayoutSettings, which has fourteen
 * callers, every autonomy settings checkbox among them.
 *
 * ON PREFERENCES: this reads and writes the real preference node, because that IS the thing under
 * test - a stand-in node would not be the store the migration deletes from.  Whatever was there is put
 * back in a finally, and the key involved is defunct: nothing but this migration ever reads it.
 */
public class testTheRoutingChoiceSurvivesTheUpgrade
{
    /** What TrainControlUI's preference node was before this class swapped it out. */
    private static java.util.prefs.Preferences realPrefs;

    /** The throwaway node the tests read and write instead. */
    private static java.util.prefs.Preferences ourPrefs;

    /**
     * Gives this class its own preference node, so nothing it writes can reach the operator (RC-A12).
     *
     * These tests have to put a pre-3.0.0 preference somewhere the migration will find it, and the
     * migration reads TrainControlUI's own node.  Writing there and restoring afterwards looks safe and
     * is not: TrainControl started DURING a battery run read the key this class had just written,
     * migrated it exactly as designed, and stored it into the operator's live configuration over the
     * rule he had chosen.  A finally cannot close a window that another process is looking through.
     *
     * So the field is swapped rather than the value.  `prefs` is private static final, which on JDK 8
     * can be reassigned by clearing the final bit on its Field; from JDK 12 that is refused, and this
     * asserts rather than falling back - a guard that silently stops guarding is worse than none, and
     * what it is guarding is a railway that took years to set up.
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
     * A refused routing change puts the dropdown back, without asking twice (RC-B11).
     *
     * Choosing a rule while trains are running is refused, and the dropdown used to keep showing the
     * rule the user picked - so the one control that reports which rule is in force reported the one
     * that is not.  Its two siblings, timetableCapture and toggleSpecifiedRoutes, have always restored
     * themselves after the same refusal; the dropdown had a TODO instead.
     *
     * TWO THINGS ARE ASSERTED, and the second is the one the TODO called "safely":
     *
     *   the control ends up on the rule the layout is actually using;
     *   and putting it there does not re-enter the listener that asked for it.
     *
     * The second is measured by a DEADLINE rather than a value.  setSelectedIndex on a JComboBox fires
     * an ActionEvent, so an unguarded restore re-enters the listener, finds the railway still busy,
     * and opens a modal dialog - which on the event thread never returns.  Guarded, this takes a
     * millisecond.  A checkbox needs none of this, which is why the siblings could restore in one line.
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

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

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

            // The rule the railway is actually following.  getAutoLayout builds one if there is none,
            // which is what makes hasAutoLayout true for the helper.
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

            // What a user's refused choice leaves behind: a control showing something else.  Set with
            // the guard held, so that arranging the fixture does not itself go through the listener.
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
     * With nowhere to store it, the choice stays on disk however many times the migration runs.
     *
     * The defect, in one gesture the user cannot see: open the autonomy settings and tick anything.
     * Each tick calls loadAutoLayoutSettings, which calls the migration.
     *
     * The session is left null, which is the state of every user whose autonomy comes from the JSON
     * file rather than from a diagram configuration - setGlobal cannot succeed for them at all - and
     * of every diagram user in the moment after a diagram edit has reset the session.
     */
    @Test
    public void testTheOldChoiceIsKeptUntilSomethingCanStoreIt() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the migration lives on a window");
        }

        // The window's node, whichever one it is - which is the throwaway this class installed.
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

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

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

            // No session, so there is nowhere to put the answer - which is the whole point.
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

            // The tick of a checkbox.  Nothing about the world has changed, so nothing about the
            // answer should either.
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
     * A configuration that exists and has NOT answered gets the old choice written into it.
     *
     * The state neither other test builds, and the one that matters: a session with an active
     * configuration carrying no pathPreference.  This is the migration actually working, end to end.
     *
     * MUTATION THIS CATCHES: AutonomySession.getGlobal returning "" instead of null for an absent key.
     * The guard then fires on the FIRST call, the legacy key is deleted, and nothing is ever stored -
     * RC-A2’s data loss restored, with both of the other tests still green.
     */
    @Test
    public void testAConfigurationWithNoAnswerIsGivenTheOldOne() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the migration lives on a window");
        }

        // The window's node, whichever one it is - which is the throwaway this class installed.
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        String was = prefs.get(LEGACY, null);

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            prefs.put(LEGACY, "SHORTEST_LENGTH");

            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

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
     * A rule already in the loaded layout is not overruled by the pre-upgrade one (RC-A8).
     *
     * The legacy population: autonomy from autonomy.json, so there is no active configuration and
     * getGlobal can never answer.  Layout.fromJSON DOES read pathPreference, so their rule is in the
     * running layout - and RC-A2, asking only the store, pushed the pre-upgrade key over it on every
     * settings refresh, for ever.
     */
    @Test
    public void testARuleAlreadyInTheLayoutIsNotOverruled() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the migration lives on a window");
        }

        // The window's node, whichever one it is - which is the throwaway this class installed.
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        String was = prefs.get(LEGACY, null);

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            prefs.put(LEGACY, "SHORTEST_LENGTH");

            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

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

            // No session at all, which is the legacy user exactly: nothing to ask, nothing to store in.
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
     * Once the configuration holds an answer, the old key goes and does not come back.
     *
     * The other direction, and the reason the guard above cannot simply be "never delete it".  A key
     * kept for ever would overrule a choice the user makes from the dropdown after upgrading: they pick
     * RANDOM, and the next start finds the pre-upgrade key and puts the old rule back.
     *
     * Both directions in one class because satisfying either one alone is trivial and wrong.
     */
    @Test
    public void testTheOldChoiceGoesOnceTheConfigurationHasOne() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the migration lives on a window");
        }

        // The window's node, whichever one it is - which is the throwaway this class installed.
        java.util.prefs.Preferences prefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        String was = prefs.get(LEGACY, null);

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            prefs.put(LEGACY, "SHORTEST_LENGTH");

            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

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

            // The user's answer, already in the configuration - whether it got there by an earlier
            // migration or by the dropdown does not matter, and must not.
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
     * The Auto tab on a layout whose autonomy is an `autonomy.json` (RGN-A2, MT-244).
     *
     * Adam asked for exactly this: *"make a test case for this.  in my testing, it loaded OK."*
     *
     * **What the finding says.**  `refreshAutonomyTabState` computes
     * `loaded = getAutonomySession() == null || activeDiagramConfiguration != null`, and disables the
     * tab when that is false.  A user upgrading from 2.7.4c has a local layout, a session (because the
     * layout is local), and no diagram configuration - so the reasoning goes that their Auto tab is
     * greyed and the thing they have been using for a year is unreachable.
     *
     * **What this asserts is the state, not the theory.**  It puts a window on a local layout that
     * carries a legacy `autonomy.json` and no diagram configuration at all, parses that JSON the way
     * the JSON path does, and asks the tab.
     */
    @Test
    public void testTheAutoTabIsReachableWithALegacyAutonomyJson() throws Exception
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
            // Inside the try, so nothing can leave the preference behind (TSX-B8).
            sandbox = support.LayoutSandbox.open();

            // THE LEGACY STATE, which the fixture is not: it ships a diagram configuration called
            // Main, and asking for a session makes that one active - which is exactly the state this
            // finding says is safe.  What an upgrading user has is a layout with NO diagram
            // configuration at all and an autonomy.json beside it.
            java.io.File diagramSetups = new java.io.File(sandbox.getFolder(), "config/autonomy");

            if (diagramSetups.isDirectory())
            {
                for (java.io.File f : diagramSetups.listFiles()) f.delete();

                diagramSetups.delete();
            }

            assertFalse(diagramSetups.exists(),
                "the sandbox still has a diagram setup folder, so this is not the legacy state");

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);
            model.stop();

            javax.swing.SwingUtilities.invokeAndWait(() ->
                ui[0] = new org.traincontrol.gui.TrainControlUI());

            ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

            // The legacy path: a graph parsed straight from an autonomy.json, with no diagram
            // configuration behind it.
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

            assertTrue(pane.isEnabledAt(2),
                "the Auto tab is greyed on a local layout whose autonomy comes from an autonomy.json "
                + "- which is every user upgrading from 2.7.4c, and the thing they have been using "
                + "for a year (RGN-A2)");

            // AND WITH A SESSION IN EXISTENCE, which is the half the finding turns on.
            //
            // `loaded` is `session == null || activeDiagramConfiguration != null`, so the tab is only
            // at risk once something has built a session - opening the editor, or anything else that
            // asks for one.  Asking for it here is what makes this test discriminate rather than pass
            // because nothing had been built yet.
            assertNotNull(ui[0].getAutonomySession(),
                "asking for a session on a local layout did not produce one, so the state this "
                + "finding is about cannot be reached from here and the assertion below proves "
                + "nothing");

            assertNull(active.get(ui[0]),
                "building a session set an active diagram configuration, so this is no longer the "
                + "legacy state");

            javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].refreshAutonomyTabState());

            assertTrue(pane.isEnabledAt(2),
                "the Auto tab is greyed once a session exists, on a layout whose autonomy is an "
                + "autonomy.json and which has no diagram configuration - so an upgrading user loses "
                + "the tab as soon as anything touches the editor.  This is RGN-A2, and it is the "
                + "state Adam could not reproduce by hand");
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
