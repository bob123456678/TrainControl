package regression;

import static org.testng.Assert.*;
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
    /** The key as it was spelled before the setting moved - written out, as the migration writes it. */
    private static final String LEGACY = "AutonomyPathPreference";

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

        java.util.prefs.Preferences prefs =
            java.util.prefs.Preferences.userNodeForPackage(org.traincontrol.gui.TrainControlUI.class);

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

        java.util.prefs.Preferences prefs =
            java.util.prefs.Preferences.userNodeForPackage(org.traincontrol.gui.TrainControlUI.class);

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

        java.util.prefs.Preferences prefs =
            java.util.prefs.Preferences.userNodeForPackage(org.traincontrol.gui.TrainControlUI.class);

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

        java.util.prefs.Preferences prefs =
            java.util.prefs.Preferences.userNodeForPackage(org.traincontrol.gui.TrainControlUI.class);

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
}
