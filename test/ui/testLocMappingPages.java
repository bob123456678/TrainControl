package ui;

import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.RightClickPageMenu;
import org.traincontrol.gui.TrainControlUI;
import support.SourceOf;

/**
 * A ceiling of fifty locomotive mapping pages.
 *
 * Adam, 2026-08-27: "add a cap of 50 pages for now (not 99) and associated error message if exceeded."
 * There was no ceiling before this - `addLocMappingPage` incremented and nothing looked.
 *
 * Two halves, and the second is the one worth having. A guard that only the interface consults is a
 * guard the next caller does not have, and a guard nothing consults is a rule with a passing test and
 * no effect - which is the fault that has cost this project four defects, three of them last week. So
 * the predicate is tested, AND the menu that offers the action is asked whether it actually greys.
 *
 * The refusal inside `addLocMappingPage` cannot be driven here: it is a modal dialog, and a modal
 * dialog in a test is a hang rather than a failure. That one is read rather than run.
 *
 * @author Adam
 */
public class testLocMappingPages
{
    /**
     * Fifty is the last page, and a state that already holds more is not talked out of it.
     *
     * The third case is the point of the design and the reason the cap is on ADDING only. Any
     * installation set up before today can hold any number of pages, and clamping on load would
     * silently throw away the mappings on everything past the fiftieth - the same data loss
     * `canDeleteCurrentPage` refuses to allow one page at a time, done wholesale and without asking.
     *
     * MUTATION: `<=` in place of `<` fails the second case (fifty-one pages allowed); reading
     * MIN_LOC_MAPPINGS instead of MAX fails the first.
     */
    @Test
    public void testTheFiftiethPageIsTheLast() throws Exception
    {
        TrainControlUI ui = build();

        assertEquals(TrainControlUI.MAX_LOC_MAPPINGS, 50,
            "Adam asked for fifty, not ninety-nine - the badge can draw more digits than that, but "
            + "what the badge can draw was never the constraint that mattered");

        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS - 1);

        assertTrue(ui.canAddLocMappingPage(),
            "the last page a person is entitled to is refused, so the ceiling bites one page early");

        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS);

        assertFalse(ui.canAddLocMappingPage(),
            "a fifty-first page is allowed, so there is no ceiling");

        // A state from before the cap existed. Nothing capped it on the way in, and nothing should.
        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS + 12);

        assertFalse(ui.canAddLocMappingPage(),
            "an installation that already has more pages than the ceiling is offered another, which "
            + "is the one case where the number can only get worse");
    }

    /**
     * The menu that offers the page stops offering it at the ceiling.
     *
     * Built for real rather than read, because this is where a person meets the rule and because an
     * affordance that offers what the guard will refuse is its own defect - OB-057 and OB-090 were
     * both exactly that, and a third turned up in this morning's review. The button that offers an
     * action asks that action's own question.
     *
     * Greyed rather than removed, which is the shape Delete settled on for the reason written beside
     * it: an item that appears and disappears reads as an interface that cannot make up its mind.
     *
     * MUTATION: dropping the `canAddLocMappingPage` test from the menu leaves the item enabled and
     * fails the second assertion.
     */
    @Test
    public void testTheMenuWillNotOfferAFiftyFirstPage() throws Exception
    {
        TrainControlUI ui = build();

        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS - 1);

        JMenuItem below = addItemOf(ui);

        assertNotNull(below, "no Add Page item in the page menu at all");

        assertTrue(below.isEnabled(),
            "Add Page is greyed with room to spare, so the ceiling is being read as one page lower "
            + "than it is - or as no room at any count");

        setPages(ui, TrainControlUI.MAX_LOC_MAPPINGS);

        JMenuItem at = addItemOf(ui);

        assertNotNull(at, "no Add Page item in the page menu at all");

        assertFalse(at.isEnabled(),
            "Add Page is still offered at the ceiling, so a person is invited to do something that "
            + "will be refused with a dialog when they try it");

        // And the greying SAYS SO. A dead item with no explanation is the state the delete side was
        // in when Adam found it, and it reads as a bug rather than a rule.
        assertNotNull(at.getToolTipText(),
            "Add Page greys itself at the ceiling and gives no reason, which looks like a fault");

        assertTrue(at.getToolTipText().contains(String.valueOf(TrainControlUI.MAX_LOC_MAPPINGS)),
            "the tooltip on the greyed Add Page does not say what the limit is, got: "
            + at.getToolTipText());
    }

    /**
     * Adding refuses on its own, without the menu's help.
     *
     * `addLocMappingPage` is public, and the greying above is one caller's manners rather than the
     * rule. Read rather than run because the refusal is a modal dialog.
     *
     * Comments are stripped first. The guard in that method has a comment above it that names
     * `canAddLocMappingPage`, and a check that did not strip comments would pass on the strength of
     * the prose describing the code after the code had gone.
     *
     * MUTATION: deleting the guard from the method fails this while both tests above stay green.
     */
    @Test
    public void testAddingRefusesWithoutBeingAsked() throws Exception
    {
        String add = SourceOf.withoutComments(SourceOf.declaration("public void addLocMappingPage()"));

        assertTrue(add.contains("canAddLocMappingPage()"),
            "addLocMappingPage no longer asks whether it may, so anything that calls it other than "
            + "the menu - a keyboard shortcut, a script, the next feature - walks past the ceiling");

        assertTrue(add.contains("errorTooManyPages"),
            "adding is refused at the ceiling without saying why, which is a menu item that does "
            + "nothing when clicked");

        assertTrue(add.indexOf("canAddLocMappingPage()") < add.indexOf("numLocMappings++"),
            "the guard sits after the increment, so the page is added and then complained about");
    }

    /**
     * Loading a state that already has too many pages does NOT throw the extra ones away.
     *
     * The dangerous half of this cap. `setViewListener` grows the page count to fit whatever the saved
     * state holds, past the ceiling and without asking, and it has to: the only way to honour a limit
     * while loading is to drop the mappings above it. Every installation configured before 2026-08-27
     * could be over the line, and the pages above it are somebody's railway.
     *
     * It exists because the two rules look inconsistent. Somebody will notice that adding stops at
     * fifty and loading does not, and they will be right that it is odd; this is what tells them what
     * happens if they make it consistent. Two rules that must never agree need a test saying so, or
     * the next tidy-up is a data loss.
     *
     * Read rather than run: standing this up needs a control station, a saved state and a window, and
     * what can silently break is a ceiling being introduced into that loop - which is visible.
     *
     * MUTATION: adding `&& this.numLocMappings < MAX_LOC_MAPPINGS` to the growth loop fails this.
     */
    @Test
    public void testLoadingIsNotCapped() throws Exception
    {
        String load = SourceOf.withoutComments(SourceOf.declaration("public void setViewListener("));

        assertTrue(load.contains("saveStates.size() - 1 > this.numLocMappings"),
            "the loop that grows the page count to fit a saved state is gone, so a state holding more "
            + "pages than the preference knows about loses the ones above it");

        assertFalse(load.contains("MAX_LOC_MAPPINGS"),
            "the ceiling has been applied while LOADING. Adding a page stops at fifty on purpose; "
            + "loading must not, because the only way to honour a limit here is to throw away pages "
            + "that already have locomotives on them - every installation set up before the cap "
            + "existed may be over it");
    }

    /**
     * A window, on the event thread, as everything in this package builds one.
     *
     * A Swing window put together off the event thread is a race that usually wins, which is the
     * worst kind. No layout and no model: the constructor alone gives `numLocMappings` its default,
     * and nothing here needs a railway.
     */
    /**
     * A page may be called "3", and the two sentinels in the name map do not make it a duplicate.
     *
     * `pageNames` is not only page names: `saveState` writes the active page number and the active
     * button's key code into it under keys -1 and -2, as strings, and they survive a load.  The rename
     * dialog asked `containsValue`, which saw them - so after any start from a saved state, naming a
     * page the digits of the last-saved active page, or the key code of the active button ("81" for Q),
     * was refused as a duplicate of a page that does not exist (SVN-C13).
     *
     * Asked of the predicate rather than through the dialog, which is modal and would hang a battery.
     *
     * MUTATION this catches: `namesOfPages()` dropping the `< 1` test, which is `containsValue` again.
     */
    @Test
    public void testASentinelIsNotAPageName() throws Exception
    {
        TrainControlUI ui = build();

        java.lang.reflect.Field names = TrainControlUI.class.getDeclaredField("pageNames");
        names.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Map<Integer, String> pageNames = (java.util.Map<Integer, String>) names.get(ui);

        pageNames.clear();

        pageNames.put(1, "Yard");

        // The two sentinels, exactly as saveState writes them.
        pageNames.put(-1, "3");
        pageNames.put(-2, "81");

        java.lang.reflect.Method ofPages = TrainControlUI.class.getDeclaredMethod("namesOfPages");
        ofPages.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Set<String> asked = (java.util.Set<String>) ofPages.invoke(ui);

        assertTrue(asked.contains("Yard"), "a real page name was not offered as one: " + asked);

        assertFalse(asked.contains("3"),
            "the active page number is treated as a page name, so a page cannot be called 3 - which "
            + "is what a person naming pages by number would try first.  Names: " + asked);

        assertFalse(asked.contains("81"),
            "the active button's key code is treated as a page name.  Names: " + asked);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui.dispose());
    }

    /**
     * The autonomy menu greys itself, and says which of the two reasons it is (TSX-B4).
     *
     * `AutonomyMenu.refreshEnabled()` decides two things a person meets: whether the menu can be
     * opened at all, and what the tooltip tells them instead.  Both were tested nowhere - and this is
     * the pattern this project has paid for repeatedly, most recently as OB-057 and OB-090: the
     * control that OFFERS an action has to ask the guard's own question, and nothing was checking that
     * this one still did.
     *
     * Two reasons, and they are not interchangeable: no layout at all, and a layout that came straight
     * from the Central Station, which has nowhere to keep a setup.  A menu that greyed for the second
     * and blamed the first would send somebody looking for a layout they already have.
     *
     * Driven through the menu rather than read from the source, so what is asserted is what the
     * operator would see.
     *
     * MUTATION this catches: dropping either half of `setEnabled(hasLayout && local)`, or swapping the
     * two tooltip arms.
     */
    @Test
    public void testTheAutonomyMenuSaysWhyItIsGrey() throws Exception
    {
        TrainControlUI ui = build();

        try
        {
            org.traincontrol.gui.AutonomyMenu menu = new org.traincontrol.gui.AutonomyMenu(ui);

            // NO MODEL AT ALL, which is the first of the two reasons.
            menu.refreshEnabled();

            assertFalse(menu.isEnabled(),
                "the autonomy menu offers itself with no layout loaded, so every gesture in it "
                + "comes back having done nothing");

            String tip = menu.getToolTipText();

            assertNotNull(tip, "the menu is grey and says nothing about why");

            assertTrue(tip.contains(strip(org.traincontrol.util.I18n.t("autosetup.ui.tooltipNoLayout"))),
                "the tooltip does not give the no-layout reason.  Got: " + tip);

            assertFalse(tip.contains(strip(org.traincontrol.util.I18n.t("autosetup.ui.tooltipNeedsLocalLayout"))),
                "the menu blames the layout being remote when there is no layout at all - which "
                + "sends somebody looking for a setting instead of opening a layout.  Got: " + tip);
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> ui.dispose());
        }
    }

    /**
     * The message, without the HTML `wrapped` puts round it.
     */
    private static String strip(String message)
    {
        return message.replace("<html>", "").replace("</html>", "");
    }
    // ------------------------------------------------------------------------------------------------------------------
    // FR-128: the line over the locomotive's name is a keycap and two pills
    // ------------------------------------------------------------------------------------------------------------------

    /**
     * Adam's page name that is wider than any pill can hold: 22 capital Ms, as in his own test data (FR-128: "make sure
     * we test with wide page names (like MMMMMMMMMMM) as in the current test data").
     */
    private static final String WIDE_PAGE_NAME = "MMMMMMMMMMMMMMMMMMMMMM";

    /** What a test of the locomotive panel needs, and what puts all of it back. */
    private static final class Panel
    {
        support.LayoutSandbox sandbox;
        org.traincontrol.marklin.MarklinControlStation model;
        TrainControlUI ui;

        final java.util.List<String> made = new java.util.ArrayList<>();

        boolean pageWasNamed;
        String pageNameWas;
        int page;

        /**
         * What each preference this run set held before it was set, by key - null for none at all - so that every one can
         * be put back as it was, unset included.
         */
        final java.util.Map<String, String> prefsWas = new java.util.LinkedHashMap<>();

        /**
         * Sets a preference in the run's own node, remembering what it held.  Set, never toggled: the node starts as a copy
         * of the operator's, so a toggle would be a coin toss.
         *
         * @param key the preference
         * @param value what to set it to; null removes it, which is its default
         */
        void setPref(String key, Boolean value)
        {
            java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

            if (!prefsWas.containsKey(key)) prefsWas.put(key, prefs.get(key, null));

            if (value == null) prefs.remove(key);
            else prefs.putBoolean(key, value);
        }

        /** The same, with the pages left as tabs or as arrows the way the run's copy of the operator's preferences has them. */
        static Panel open(Boolean numbers) throws Exception
        {
            return open(numbers, null);
        }

        /**
         * A window over a model, as the application builds it; the page it is on is read from the window.
         *
         * @param numbers what the "Show Page Numbers" preference is SET to before the window is built, so that the window
         *        reads it at start-up as it does the operator's; null removes it, which is the default.  It is set and
         *        never toggled, and put back in `close`.  Only in a run with a preference node of its own: the
         *        operator's is not this test's to write.
         * @param tabs what "Show Locomotive Pages as Tabs" is SET to, in the same way - the page tabs when true, the page
         *        label between the arrows when false - or null to leave it as the run's copy of the operator's has it
         */
        static Panel open(Boolean numbers, Boolean tabs) throws Exception
        {
            Panel p = new Panel();

            try
            {
                String numbersKey = pageNumbersKey();

                if (numbersKey != null || tabs != null)
                {
                    if (System.getProperty(org.traincontrol.util.Util.PREFERENCES_PROPERTY, "").trim().isEmpty())
                    {
                        throw new org.testng.SkipException("the page preferences are the operator's, and this run has no"
                            + " preference node of its own to set them in");
                    }

                    if (numbersKey != null) p.setPref(numbersKey, numbers);

                    if (tabs != null) p.setPref(TrainControlUI.TABS_SETTING_PREF, tabs);
                }

                p.sandbox = support.LayoutSandbox.open();

                p.model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        p.ui = new TrainControlUI();
                        p.ui.setViewListener(p.model, new java.util.concurrent.CountDownLatch(1));
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                });

                // The window chooses its first key from a task it queues on the event thread, and draws it from a
                // worker; wait for both, so the page and the key read below are the ones it settled on
                p.waitForTheRender();

                p.page = (Integer) privateField(p.ui, "currentButtonlocMappingNumber");

                java.util.Map<Integer, String> names = p.names();

                p.pageWasNamed = names.containsKey(p.page);
                p.pageNameWas = names.get(p.page);

                // laid out, so the slot has the size it has on screen
                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    p.ui.pack();
                    p.ui.validate();
                });

                return p;
            }
            catch (Throwable t)
            {
                p.close();

                throw t;
            }
        }

        @SuppressWarnings("unchecked")
        java.util.Map<Integer, String> names() throws Exception
        {
            return (java.util.Map<Integer, String>) privateField(ui, "pageNames");
        }

        /** Names the page the window is on, on the event thread, as the rename does; empty for none. */
        void nameThePage(String name) throws Exception
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    if (name == null) names().remove(page);
                    else names().put(page, name);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });
        }

        org.traincontrol.marklin.MarklinLocomotive mm2(String name, int address)
        {
            made.add(name);

            return model.newMM2Locomotive(name, address);
        }

        org.traincontrol.marklin.MarklinLocomotive mfx(String name, int address)
        {
            made.add(name);

            return model.newMFXLocomotive(name, address);
        }

        org.traincontrol.marklin.MarklinLocomotive dcc(String name, int address)
        {
            made.add(name);

            return model.newDCCLocomotive(name, address);
        }

        /** Makes this the active locomotive and lets the window draw its panel, as `repaintLoc` does. */
        void show(org.traincontrol.base.Locomotive loc) throws Exception
        {
            java.lang.reflect.Field active = TrainControlUI.class.getDeclaredField("activeLoc");

            active.setAccessible(true);

            // `repaintLoc` returns without doing anything while an earlier render is still running, and the render
            // goes to a worker and back through the event thread - so wait for the last one before and for this one after
            waitForTheRender();

            active.set(ui, loc);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.repaintLoc(true, null));

            waitForTheRender();

            // Showing a locomotive makes buttons visible, which invalidates the panel and lays nothing out until somebody
            // asks - so ask, or "nothing moved" below would be true of a panel that was never laid out again
            javax.swing.SwingUtilities.invokeAndWait(() -> ui.validate());
        }

        /** The same, without forcing - the way a change to something other than the locomotive reaches the window. */
        void repaintWithoutForce() throws Exception
        {
            waitForTheRender();

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.repaintLoc(false, null));

            waitForTheRender();
        }

        @SuppressWarnings("unchecked")
        void waitForTheRender() throws Exception
        {
            java.lang.reflect.Field futures = TrainControlUI.class.getDeclaredField("locFutures");

            futures.setAccessible(true);

            long deadline = System.currentTimeMillis() + 10000;

            while (System.currentTimeMillis() < deadline)
            {
                boolean done = true;

                synchronized (ui)
                {
                    for (java.util.concurrent.Future<?> f : new java.util.ArrayList<>(
                        (java.util.List<java.util.concurrent.Future<?>>) futures.get(ui)))
                    {
                        done &= f.isDone();
                    }
                }

                if (done)
                {
                    // what the render posted to the event thread has run, too
                    javax.swing.SwingUtilities.invokeAndWait(() -> { });
                    javax.swing.SwingUtilities.invokeAndWait(() -> { });

                    return;
                }

                Thread.sleep(20);
            }

            throw new AssertionError("the window's render did not finish within ten seconds, so what is read below would"
                + " be whatever it last drew");
        }

        javax.swing.JLabel slot() throws Exception
        {
            return (javax.swing.JLabel) privateField(ui, "CurrentKeyLabel");
        }

        /** The row that draws the line, or null when the window has none. */
        javax.swing.JComponent row() throws Exception
        {
            try
            {
                return (javax.swing.JComponent) privateField(ui, "currentKeyRow");
            }
            catch (NoSuchFieldException none)
            {
                return null;
            }
        }

        void close() throws Exception
        {
            try
            {
                if (ui != null)
                {
                    waitForTheRender();

                    if (page != 0) nameThePage(pageWasNamed ? pageNameWas : null);
                }
            }
            finally
            {
                try
                {
                    if (model != null)
                    {
                        for (String name : made)
                        {
                            if (model.getLocByName(name) != null) model.deleteLoc(name);
                        }
                    }

                    if (ui != null) javax.swing.SwingUtilities.invokeAndWait(() -> ui.dispose());
                }
                finally
                {
                    try
                    {
                        if (model != null) model.stop();

                        if (sandbox != null) sandbox.close();
                    }
                    finally
                    {
                        // AS THEY WERE, including not there at all
                        java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

                        for (java.util.Map.Entry<String, String> was : prefsWas.entrySet())
                        {
                            if (was.getValue() == null) prefs.remove(was.getKey());
                            else prefs.put(was.getKey(), was.getValue());
                        }
                    }
                }
            }
        }

        /** The "Show Page Numbers" item, or null when the window has none. */
        javax.swing.JCheckBoxMenuItem pageNumbersItem() throws Exception
        {
            try
            {
                return (javax.swing.JCheckBoxMenuItem) privateField(ui, "showPageNumbersMenuItem");
            }
            catch (NoSuchFieldException none)
            {
                return null;
            }
        }

        /** The words the row has for the page now, once the render is done. */
        String pageWords() throws Exception
        {
            waitForTheRender();

            return (String) pub(geometry(row(), slot().getWidth(), slot().getHeight()), "pageText");
        }

        /** The page the window is showing, whose words are on the page label between the arrows. */
        int shown()
        {
            return ui.getLocMappingNumber();
        }

        /**
         * Names the page the window is showing the way the operator's Rename does once its dialog is answered - the
         * window's own `setPageName`, which words the label and asks for a render - on the event thread; null for none.
         */
        void renameTheShownPage(String name) throws Exception
        {
            java.lang.reflect.Method rename = TrainControlUI.class.getDeclaredMethod("setPageName", String.class);

            rename.setAccessible(true);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    rename.invoke(ui, name);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            waitForTheRender();
        }

        /**
         * Everything the window has queued for the tabs done: the renders, the mapping renderer's thread - which words
         * every tab, and posts that to the event thread - and what that posted.
         */
        void settle() throws Exception
        {
            waitForTheRender();

            java.util.concurrent.ExecutorService renderer =
                (java.util.concurrent.ExecutorService) privateField(ui, "MappingRenderer");

            renderer.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);

            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }

        /** The caption on the tab of this page, once the window has done wording the tabs. */
        String tab(int page) throws Exception
        {
            settle();

            javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) privateField(ui, "locKeyTabs");
            final String[] title = new String[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> title[0] = tabs.getTitleAt(page - 1));

            return title[0];
        }

        /** The tooltip on the tab of this page. */
        String tabTip(int page) throws Exception
        {
            settle();

            javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) privateField(ui, "locKeyTabs");
            final String[] tip = new String[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> tip[0] = tabs.getToolTipTextAt(page - 1));

            return tip[0];
        }

        /** The words on the page label between the arrows. */
        String label() throws Exception
        {
            javax.swing.JLabel label = (javax.swing.JLabel) privateField(ui, "LocMappingNumberLabel");
            final String[] text = new String[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> text[0] = label.getText());

            return text[0];
        }
    }

    private interface Body
    {
        void run(Panel p) throws Exception;
    }

    private static void withPanel(Boolean numbers, Body body) throws Exception
    {
        withPanel(numbers, null, body);
    }

    /**
     * A panel with both page preferences set before the window is built.
     *
     * @param numbers "Show Page Numbers", or null for its default
     * @param tabs "Show Locomotive Pages as Tabs", or null to leave it as the run's copy of the operator's has it
     * @param body what to do with it
     */
    private static void withPanel(Boolean numbers, Boolean tabs, Body body) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the locomotive panel is on a window");
        }

        Panel p = Panel.open(numbers, tabs);

        try
        {
            body.run(p);
        }
        finally
        {
            p.close();
        }
    }

    /** The key of the "Show Page Numbers" preference, or null when the window has none. */
    private static String pageNumbersKey()
    {
        try
        {
            return (String) TrainControlUI.class.getField("SHOW_PAGE_NUMBERS").get(null);
        }
        catch (ReflectiveOperationException none)
        {
            return null;
        }
    }

    private static Object privateField(Object owner, String name) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(owner);
    }

    /**
     * The row's own layout for a box this size.  Asked by name, so this class still compiles - and fails, in words - on a
     * window that has no such row.
     */
    private static Object geometry(javax.swing.JComponent row, int width, int height) throws Exception
    {
        return row.getClass().getMethod("geometry", int.class, int.class).invoke(row, width, height);
    }

    /**
     * A public field of the row's layout, or null when it has none - so a layout without the field is an assertion that
     * says what is missing, and not an exception that says nothing.
     */
    private static Object pub(Object owner, String name) throws Exception
    {
        try
        {
            return owner.getClass().getField(name).get(owner);
        }
        catch (NoSuchFieldException none)
        {
            return null;
        }
    }

    private static int right(Object geometry) throws Exception
    {
        return (Integer) geometry.getClass().getMethod("right").invoke(geometry);
    }

    private static int bottom(Object geometry) throws Exception
    {
        return (Integer) geometry.getClass().getMethod("bottom").invoke(geometry);
    }

    private static java.awt.Rectangle rect(Object geometry, String name) throws Exception
    {
        return (java.awt.Rectangle) pub(geometry, name);
    }

    /** What the pieces say, joined, for a check that a word is not in any of them. */
    private static String drawn(Object geometry) throws Exception
    {
        return pub(geometry, "key") + "|" + pub(geometry, "number") + "|" + pub(geometry, "name") + "|"
            + pub(geometry, "decoderType") + "|" + pub(geometry, "decoderAddress");
    }

    /** The colour of a pixel, without its alpha. */
    private static int rgb(java.awt.image.BufferedImage image, int x, int y)
    {
        return image.getRGB(x, y) & 0xFFFFFF;
    }

    /**
     * The first and the last row, within these rows, that has a pixel in these columns which is not the background - dark
     * text on a light ground, or light on dark when `light`.  Null when there is none.
     */
    private static int[] inkRows(java.awt.image.BufferedImage image, int x0, int x1, int y0, int y1, boolean light)
    {
        int first = -1, last = -1;

        for (int y = y0; y <= y1; y++)
        {
            for (int x = Math.max(0, x0); x <= Math.min(image.getWidth() - 1, x1); x++)
            {
                int c = rgb(image, x, y);

                int luminance = (int) Math.round(0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255));

                if (light ? luminance >= 120 : luminance <= 170)
                {
                    if (first < 0) first = y;

                    last = y;

                    break;
                }
            }
        }

        return first < 0 ? null : new int[] {first, last};
    }

    /**
     * Whether every pixel in this box is the page's words' colour on the background or a blend of the two - which, over a
     * magenta background and navy words, is every pixel whose green is nothing.  A grey, a white or a neutral edge has
     * some green, so a shape round the words, or a fill behind them, is a pixel that fails this.
     */
    private static boolean onlyWordsOnTheBackground(java.awt.image.BufferedImage image, int x0, int x1, int y0, int y1)
    {
        for (int x = Math.max(0, x0); x <= Math.min(image.getWidth() - 1, x1); x++)
        {
            for (int y = Math.max(0, y0); y <= Math.min(image.getHeight() - 1, y1); y++)
            {
                if (((image.getRGB(x, y) >> 8) & 255) != 0) return false;
            }
        }

        return true;
    }

    /** Whether any pixel in this box is the page's words' navy: nothing red, a blue of about 115. */
    private static boolean navyIn(java.awt.image.BufferedImage image, int x0, int x1, int y0, int y1)
    {
        for (int x = Math.max(0, x0); x <= Math.min(image.getWidth() - 1, x1); x++)
        {
            for (int y = Math.max(0, y0); y <= Math.min(image.getHeight() - 1, y1); y++)
            {
                int c = rgb(image, x, y);

                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;

                if (r <= 40 && g <= 40 && b >= 100 && b <= 130) return true;
            }
        }

        return false;
    }

    /** Whether every pixel along this row, in these columns, is the one colour - nothing is drawn into it. */
    private static boolean rowIsOneColour(java.awt.image.BufferedImage image, int x0, int x1, int y)
    {
        int ref = rgb(image, x0, y);

        for (int x = x0 + 1; x <= x1; x++)
        {
            if (rgb(image, x, y) != ref) return false;
        }

        return true;
    }

    /** Whether any pixel in these columns and rows is white or all but - the colour of the words and the line. */
    private static boolean whiteIn(java.awt.image.BufferedImage image, int x0, int x1, int y0, int y1)
    {
        for (int x = x0; x <= x1; x++)
        {
            for (int y = y0; y <= y1; y++)
            {
                int c = rgb(image, x, y);

                if (Math.min((c >> 16) & 255, Math.min((c >> 8) & 255, c & 255)) >= 235) return true;
            }
        }

        return false;
    }

    /**
     * The line over the locomotive's name is a keycap, a page pill and a decoder pill, for every kind of decoder (FR-128).
     *
     * Adam: *"the active locomotive info is currently text consisting of [letter] . Page [num] ([page name]) .
     * [decodertype] [address]. the page number could be condensed into a nice pill, along with the other info.  make it
     * look more modern."*  The design he chose: the key's letter on a keycap, then a page pill with the page's NUMBER and
     * its name, then a solid decoder pill with the type and the address.  *"I wouldn't like to see the word 'page' in
     * it."*  And on seeing it (round 2): the decoder pill's type and address are parted by a white vertical line, and the
     * pill floats to the right: *"Can we also float the address to the right?"*  Round 3: *"move the page number to after
     * the page name and separate it with a line"*.  Round 4: *"Change the page title to just the name with page number in
     * parens after, no pill around that, in the standard blue label font"*.  Round 5: *"Change (5) to 5 as a prefix.
     * Example: 5. MMMMMM"* - so the page is plain words, "5. Example", in `CurrentKeyLabel`'s own font and colour, and
     * only the keycap and the decoder pill are shapes.
     *
     * One locomotive of each decoder type the program has, on the page of the window's current key: MM2, MFX, DCC, a
     * multi-unit and a consist of an MFX head with a member.  Driven through `repaintLoc`, which is what the window calls
     * to show a locomotive, and read from the row's own layout - what is drawn is what is asked, so there is no second
     * account of it to drift.
     *
     * The keycap and the page pill stay at the left, a gap apart; the decoder pill is at the slot's right edge, with more
     * than the least gap between it and a short page name's pill, which is the proof that it floats and does not follow.
     *
     * MUTATION: put the word Page back in the page's words (draw `getTooltip`'s page words there), put the number after
     * the name or back in parentheses, leave the full stop off, put a line between them, put the decoder pill back beside
     * the page's words, join the type and the address into one text, draw the divider outside its pill, or set the
     * page's words in any font or colour but the label's, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheLineIsAKeycapAndTwoPillsForEveryDecoder() throws Exception
    {
        withPanel(Boolean.TRUE, p ->
        {
            javax.swing.JLabel slot = p.slot();
            javax.swing.JComponent row = p.row();

            assertNotNull(row, "the window has no row over the locomotive's name: the line is still the old text in"
                + " CurrentKeyLabel, which cannot draw a keycap or a pill (FR-128)");

            assertEquals(slot.getText(), "", "CurrentKeyLabel still carries the old text as well as the row, so the line"
                + " would be said twice");

            // THE PAGE'S WORDS ARE SET IN THE LABEL'S OWN FONT AND COLOUR, read from it: the "standard blue label font"
            java.lang.reflect.Method pageFont = null, pageColour = null;

            try
            {
                pageFont = row.getClass().getMethod("pageFont");
                pageColour = row.getClass().getMethod("pageColour");
            }
            catch (NoSuchMethodException none)
            {
                // said below
            }

            assertNotNull(pageFont, "the row does not say what font the page's words are in");
            assertNotNull(pageColour, "the row does not say what colour the page's words are in");

            assertEquals(pageFont.invoke(row), slot.getFont(), "the page's words are not in CurrentKeyLabel's font.  Adam,"
                + " round 4: \"in the standard blue label font\"");

            assertEquals(pageColour.invoke(row), slot.getForeground(), "the page's words are not in CurrentKeyLabel's colour."
                + "  Adam, round 4: \"in the standard blue label font\"");

            p.nameThePage("Example");

            String key = ((javax.swing.JButton) privateField(p.ui, "currentButton")).getText();

            org.traincontrol.marklin.MarklinLocomotive head = p.mfx("FR-128 consist head", 3912);
            org.traincontrol.marklin.MarklinLocomotive member = p.mm2("FR-128 consist member", 78);

            java.util.Map<String, Double> list = new java.util.HashMap<>();
            list.put(member.getName(), 1.0);
            head.preSetLinkedLocomotives(list);
            head.setLinkedLocomotives();

            assertTrue(head.hasLinkedLocomotives(), "precondition: the consist is not linked, so there is no MU label to see");

            org.traincontrol.marklin.MarklinLocomotive multiUnit = p.dcc("FR-128 multi-unit", 9872);
            p.model.changeLocAddress(multiUnit.getName(), 4999, org.traincontrol.marklin.MarklinLocomotive.decoderType.MULTI_UNIT);

            // the decoder texts: the enum's own names for the three protocols; the bundle's words for the other two
            // and each decoder is two things, the type and the address, as the pill draws them
            Object[][] kinds = {
                {p.mm2("FR-128 MM2", 79), "MM2", "79"},
                {p.mfx("FR-128 MFX", 3954), "MFX", "3954"},
                {p.dcc("FR-128 DCC", 9871), "DCC", "9871"},
                {multiUnit, org.traincontrol.util.I18n.t("loc.multiUnit"), "4999"},
                {head, org.traincontrol.util.I18n.f("loc.multiUnitLinked", "MFX"), "3912"},
            };

            // the word the program uses for a page, out of its own bundle - "Page" in English
            String pageWord = org.traincontrol.util.I18n.f("page.ui.pageNo", 0).replace("0", "").trim().toLowerCase();

            assertFalse(pageWord.isEmpty(), "precondition: cannot find the bundle's word for a page");

            java.awt.Dimension box = slot.getSize();

            for (Object[] kind : kinds)
            {
                p.show((org.traincontrol.base.Locomotive) kind[0]);

                String said = "for " + kind[1] + " " + kind[2];

                Object at = geometry(row, box.width, box.height);

                assertNotNull(at, "the row draws nothing " + said);

                assertEquals(pub(at, "key"), key, "the keycap does not carry the key's letter " + said);

                assertEquals(pub(at, "number"), String.valueOf(p.page),
                    "the page pill does not carry the page's number " + said);

                assertEquals(pub(at, "name"), "Example", "the page pill does not carry the page's name " + said);

                assertEquals(pub(at, "decoderType"), kind[1], "the decoder pill does not say the decoder's type " + said);

                assertEquals(pub(at, "decoderAddress"), kind[2], "the decoder pill does not say the address " + said);

                String all = drawn(at).toLowerCase();

                assertFalse(all.contains("page") || all.contains(pageWord),
                    "the word for a page is in the line " + said + ": " + drawn(at)
                    + ".  Adam: \"I wouldn't like to see the word 'page' in it.\"");

                // THE KEYCAP AND THE PAGE PILL AT THE LEFT, a gap apart; THE DECODER PILL FLOATED TO THE RIGHT EDGE
                java.awt.Rectangle keycap = rect(at, "keycap"), page = rect(at, "page"), decoder = rect(at, "decoder"),
                    divider = rect(at, "divider");

                assertNotNull(keycap, "no keycap " + said);
                assertNotNull(decoder, "no decoder pill " + said);

                assertEquals(page.x, keycap.x + keycap.width + rowGap(), "the page pill is not a " + rowGap()
                    + " px gap after the keycap " + said);

                assertEquals(decoder.x + decoder.width, box.width, "the decoder pill is not at the slot's right edge " + said
                    + ": it is " + decoder + " in " + box + ".  Adam: \"float the address to the right\"");

                assertTrue(decoder.x - (page.x + page.width) > rowGap(), "the decoder pill is a least gap from a short page name's"
                    + " pill, so it follows the page pill and does not float " + said + ": " + page + " and " + decoder);

                // THE PAGE IS PLAIN WORDS - the number and a full stop, then the name - with no pill, no fill and no line
                assertNull(pub(at, "separator"), "the page still has a line between its name and its number " + said
                    + ".  Adam, round 4: \"no pill around that\"");

                assertEquals(pub(at, "pageText"), p.page + ". Example", "the page's words are not the number, a full stop"
                    + " and then the name " + said + ".  Adam, round 5: \"Change (5) to 5 as a prefix.  Example: 5. MMMMMM\"");

                // A WHITE LINE BETWEEN THE TYPE AND THE ADDRESS, inside the pill's rim and not through it
                assertNotNull(divider, "the decoder pill has no line between its type and its address " + said);

                assertTrue(divider.x > decoder.x && divider.x < decoder.x + decoder.width, "the divider is not inside the"
                    + " decoder pill " + said + ": " + divider + " in " + decoder);

                assertEquals(divider.y, decoder.y + 1, "the divider does not start one pixel below the pill's top rim " + said);

                assertEquals(divider.y + divider.height, decoder.y + decoder.height - 1,
                    "the divider does not stop one pixel above the pill's bottom rim " + said);

                double typeX = (Double) pub(at, "typeX"), addressX = (Double) pub(at, "addressX");

                assertTrue(typeX < divider.x && addressX >= divider.x + divider.width, "the type is not left of the divider and"
                    + " the address right of it " + said + ": type at " + typeX + ", divider at " + divider.x + ", address at "
                    + addressX);

                // all three fit the slot, in height and in width
                for (java.awt.Rectangle r : new java.awt.Rectangle[] {keycap, page, decoder})
                {
                    assertTrue(r.y >= 0 && r.y + r.height <= box.height, "a shape is taller than the slot " + said + ": " + r
                        + " in " + box);

                    assertTrue(r.x >= 0 && r.x + r.width <= box.width, "a shape runs past the slot " + said + ": " + r
                        + " in " + box);
                }

                // and the words in the tooltip are the key, the page as the program words it, and the decoder
                String tip = ((javax.swing.JComponent) row).getToolTipText();

                assertNotNull(tip, "the row has no tooltip " + said);

                assertTrue(tip.contains(key) && tip.contains(org.traincontrol.util.I18n.f("page.ui.plainPageName", p.page,
                    "Example")) && tip.contains(kind[1] + " " + kind[2]), "the tooltip " + said + " is not the whole line: " + tip);
            }

            // A KEY WITH NO LOCOMOTIVE ON IT has the key and the page and no decoder pill
            p.show(null);

            Object bare = geometry(row, box.width, box.height);

            assertNotNull(bare, "nothing is drawn for a key with no locomotive: the key and its page went with it");
            assertNull(rect(bare, "decoder"), "a decoder pill is drawn for a key with no locomotive on it");
            assertEquals(pub(bare, "name"), "Example");
            assertEquals(pub(bare, "pageText"), p.page + ". Example",
                "with no decoder pill the page's words are not the number, a full stop and the name");

            assertEquals(rect(bare, "page").x, rect(bare, "keycap").x + rect(bare, "keycap").width + rowGap(),
                "with no decoder pill the page pill is not at the left, a gap after the keycap");
        });
    }

    /**
     * A wide page name is cut with an ellipsis and nothing else is, and the line never leaves its slot (FR-128).
     *
     * Adam: *"make sure we test with wide page names (like MMMMMMMMMMM) as in the current test data"*, and *"manage the
     * height so it fits in the current space"*.  His data has a page named with 22 capital Ms.  The page NAME takes what
     * room is left and gives way; the keycap, the page number and the decoder pill are never cut.  The slot is the one the
     * old text had - 296 by 18 - and nothing below it may move.
     *
     * Measured with the widest decoder text, the multi-unit's, because that is the one that leaves the least room.  The
     * decoder pill floats to the right edge, so the name's room is what lies between the number's segment and it, less a
     * gap: the name is cut to that, and the page pill never runs under the decoder pill.
     *
     * Round 3, Adam: *"Increase font sizes by 1 notch to improve visibility - make sure everything still fits vertically"*:
     * every font in the pills is 12 point, and the slot is still 296 by 18.  With the 22-M name the page's words are
     * "5. MMMM..." (round 5: *"Example: 5. MMMMMM"*): the name is cut with an ellipsis and the "5. " in front of it is
     * whole and clear of the decoder pill, which the name's room is cut to leave.
     *
     * MUTATION: cut the name by letters instead of by width, leave the ellipsis off, let a pill grow past 18 px, draw
     * the page's words wider than the room they were given, take the decoder pill's width out of that room, cut the
     * "5. ", or set any of the pills' fonts back to 11 point, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAWidePageNameGivesWayAndTheRestDoesNot() throws Exception
    {
        withPanel(Boolean.TRUE, p ->
        {
            javax.swing.JLabel slot = p.slot();
            javax.swing.JComponent row = p.row();

            assertNotNull(row, "the window has no row over the locomotive's name (FR-128)");

            org.traincontrol.marklin.MarklinLocomotive multiUnit = p.dcc("FR-128 wide multi-unit", 9873);
            p.model.changeLocAddress(multiUnit.getName(), 5120, org.traincontrol.marklin.MarklinLocomotive.decoderType.MULTI_UNIT);

            org.traincontrol.marklin.MarklinLocomotive plain = p.mfx("FR-128 wide MFX", 3955);

            // THE SLOT: what the form gives the label, which is what the old text had to fit
            assertEquals(slot.getSize(), new java.awt.Dimension(296, 18),
                "the label's slot is not the 296 x 18 the old line had, so the panel's layout has moved");

            java.awt.Dimension box = slot.getSize();

            assertEquals(row.getSize(), box, "the row does not fill the slot it sits in");

            // EVERY FONT IN THE ROW IS ONE NOTCH UP: 12 point, from 11
            java.lang.reflect.Method fonts = null;

            try
            {
                fonts = row.getClass().getMethod("fonts");
            }
            catch (NoSuchMethodException none)
            {
                // said below
            }

            assertNotNull(fonts, "the row does not say what fonts it draws in");

            java.awt.Font[] drawn = (java.awt.Font[]) fonts.invoke(null);

            assertEquals(drawn[0].getSize2D(), 12f, 0.001f, "the keycap's letter is " + drawn[0].getSize2D()
                + " point, not 12: " + drawn[0] + ".  Adam, round 3: \"Increase font sizes by 1 notch\"");

            assertEquals(drawn[1].getSize2D(), 13f, 0.001f, "the decoder pill's words are " + drawn[1].getSize2D()
                + " point, not 13: " + drawn[1] + ".  Adam: \"increase the font size inside the blue mfx+address pill"
                + " slightly\"");

            assertTrue(row.getPreferredSize().height <= box.height, "the row's own height, " + row.getPreferredSize().height
                + ", is taller than the slot's " + box.height + " - Adam: \"manage the height so it fits in the current space\"");

            for (org.traincontrol.base.Locomotive loc : new org.traincontrol.base.Locomotive[] {plain, multiUnit})
            {
                // what is below the line with a short name, for THIS locomotive, and then with the wide one
                p.nameThePage("Example");
                p.show(loc);

                String before = whereTheOthersAre(p);

                p.nameThePage(WIDE_PAGE_NAME);
                p.show(loc);

                String said = "for " + loc.getDecoderTypeLabel() + " with a page called " + WIDE_PAGE_NAME;

                Object at = geometry(row, box.width, box.height);

                String name = (String) pub(at, "name");

                assertTrue(name.endsWith(String.valueOf((char) 0x2026)), "the page name is not cut with an ellipsis "
                    + said + ": [" + name + "]");

                assertTrue(name.length() > 2 && name.length() < WIDE_PAGE_NAME.length() + 1
                    && name.substring(0, name.length() - 1).matches("M+"), "what is left of the page name is not a run of its"
                    + " own letters " + said + ": [" + name + "]");

                // THE PARTS THAT NEVER GIVE WAY
                assertEquals(pub(at, "number"), String.valueOf(p.page), "the page number was cut " + said);

                // ... and the "5. " in front of it, whole: the page's words are the number, a full stop, and the cut name
                assertEquals(pub(at, "pageText"), p.page + ". " + name, "the page's number was cut, or is not the prefix of the"
                    + " cut name, with its full stop " + said + ": [" + pub(at, "pageText") + "]");

                assertNull(pub(at, "separator"), "the page still has a line before its number " + said);

                String decoderType = loc.getDecoderTypeLabel(), address = p.model.getLocAddress(loc.getName());
                String decoderText = decoderType + " " + address;

                assertEquals(pub(at, "decoderType"), decoderType, "the decoder's type was cut " + said);
                assertEquals(pub(at, "decoderAddress"), address, "the address was cut " + said);

                java.awt.Rectangle keycap = rect(at, "keycap"), page = rect(at, "page"), decoder = rect(at, "decoder");

                assertNotNull(keycap, "the keycap went " + said);
                assertNotNull(decoder, "the decoder pill went " + said);

                // NOTHING OVERLAPS, and the decoder pill is still at the right edge
                assertEquals(decoder.x + decoder.width, box.width, "the decoder pill is not at the slot's right edge " + said);

                assertTrue(keycap.x + keycap.width + rowGap() <= page.x, "the page pill is closer than the least gap to the keycap "
                    + said + ": " + keycap + " and " + page);

                assertTrue(page.x + page.width + rowGap() <= decoder.x, "the page pill runs under the decoder pill, or is closer"
                    + " than the least gap to it " + said + ": " + page + " and " + decoder);

                // THE WHOLE LINE IN ITS SLOT
                assertTrue(right(at) <= box.width, "the line runs past the slot's width " + said + ": " + right(at) + " > "
                    + box.width);

                assertTrue(bottom(at) <= box.height, "the line is taller than the slot " + said + ": " + bottom(at) + " > "
                    + box.height);

                // and the tooltip has all of it: the name uncut, in the program's own words for a page
                String tip = row.getToolTipText();

                assertTrue(tip != null && tip.contains(org.traincontrol.util.I18n.f("page.ui.plainPageName", p.page,
                    WIDE_PAGE_NAME)) && tip.contains(decoderText), "the tooltip does not have the whole line " + said + ": " + tip);

                // NOTHING BELOW IT MOVED, and the slot is as it was
                assertEquals(slot.getSize(), box, "the slot changed size " + said);

                assertEquals(whereTheOthersAre(p), before, "something in the panel moved when the page name got wide " + said);
            }

            // A PAGE WITH NO NAME reads "Page 5", drawn whole (round 7)
            p.nameThePage(null);
            p.show(plain);

            Object unnamed = geometry(row, box.width, box.height);

            assertEquals(pub(unnamed, "name"), "", "a page with no name still has a name in its pill");
            assertEquals(pub(unnamed, "number"), String.valueOf(p.page), "a page with no name has lost its number");
            assertEquals(pub(unnamed, "pageText"), unnamedWords(p.page), "a page with no name is not 'Page N'.  Adam, round 7:"
                + " \"the text next to the locomotive key should be 'Page 5', not '5' if the page is unnamed\"");
            assertTrue(rect(unnamed, "page").width < 60, "a page with no name is not short: " + rect(unnamed, "page"));

            // AND A RENAME REACHES THE LINE THE WAY IT ALWAYS DID: through a repaint that is not forced.  The old line
            // compared its own text to decide whether anything had changed; the row compares what it says.
            p.nameThePage("Renamed");
            p.repaintWithoutForce();

            assertEquals(pub(geometry(row, box.width, box.height), "name"), "Renamed",
                "a changed page name did not reach the line: the check for whether it changed has stopped seeing the name");
        });
    }

    /**
     * What is painted is the design Adam chose: a keycap with a heavier bottom edge, the page as plain words in the label's
     * navy with nothing round them, and a decoder pill that is solid navy at the slot's right edge, with a white line
     * between its type and its address (FR-128).
     *
     * Read from the pixels the row paints, over a background that is nothing else, so a fill that is not meant to be there
     * shows as a change from it.  Each reading is a point well inside one shape and clear of its words, so it does not
     * depend on how the letters are drawn - only on whether the shape is filled, outlined or neither.
     *
     * Round 2, Adam: *"In the decoder address pill, add a vertical line from top to bottom (white) separating the type
     * from the number (MFX | 12) while maintaining a blue border around it ... float the address to the right"*.  Round 4:
     * *"Change the page title to just the name with page number in parens after, no pill around that, in the standard
     * blue label font ... Keep both the key and address pills as is"*, and round 5, *"Change (5) to 5 as a prefix"*.  So,
     * between the keycap and the decoder pill, every
     * pixel is the background or the label's navy words and a blend of the two - no outline, no fill, no line, no grey -
     * and the words are navy; the decoder's divider is white with navy above and below it - the rim, unbroken - and the
     * pill's last painted column is the slot's last.
     *
     * MUTATION: draw any outline, fill or line round the page's words, draw them in anything but the label's navy, put the
     * decoder pill beside them, draw the divider to the pill's edge, draw the keycap's edge one pixel all round, or take
     * the decoder's navy from anywhere but rgb(0,0,115), and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheShapesAreThoseAdamChose() throws Exception
    {
        withPanel(Boolean.TRUE, p ->
        {
            javax.swing.JLabel slot = p.slot();
            javax.swing.JComponent row = p.row();

            assertNotNull(row, "the window has no row over the locomotive's name (FR-128)");

            p.nameThePage("Example");
            p.show(p.mfx("FR-128 painted", 3956));

            java.awt.Dimension box = slot.getSize();

            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(box.width, box.height,
                java.awt.image.BufferedImage.TYPE_INT_RGB);

            final int none = java.awt.Color.MAGENTA.getRGB() & 0xFFFFFF;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                java.awt.Graphics2D g = image.createGraphics();

                g.setColor(java.awt.Color.MAGENTA);
                g.fillRect(0, 0, box.width, box.height);

                row.paint(g);
                g.dispose();
            });

            Object at = geometry(row, box.width, box.height);

            java.awt.Rectangle keycap = rect(at, "keycap"), page = rect(at, "page"), decoder = rect(at, "decoder"),
                divider = rect(at, "divider");

            assertNull(pub(at, "separator"), "the page still has a line between its name and its number");
            assertNotNull(divider, "the decoder pill has no line between its type and its address");

            final int navy = 0x000073, white = 0xFFFFFF;

            int pillMid = decoder.y + decoder.height / 2;

            // THE DECODER PILL: solid, in the navy the old line's text was, which is CurrentKeyLabel's own foreground
            assertEquals(rgb(image, decoder.x + 4, pillMid), navy, "the decoder pill is not solid navy rgb(0,0,115)");

            // ... and AT THE SLOT'S RIGHT EDGE: the last column anything is painted in is the slot's last, or the one before
            int last = box.width - 1;

            while (last > 0 && rgb(image, last, pillMid) == none) last--;

            assertTrue(last >= box.width - 2, "the decoder pill does not reach the slot's right edge: the last column painted"
                + " is " + last + " of " + box.width + ".  Adam: \"float the address to the right\"");

            // THE DIVIDER: white, one pixel wide, from just inside the top rim to just inside the bottom one
            assertEquals(rgb(image, divider.x, pillMid), white, "the divider is not white");
            assertEquals(rgb(image, divider.x, decoder.y + 1), white, "the divider does not reach the top of the pill's inside");
            assertEquals(rgb(image, divider.x, decoder.y + decoder.height - 2), white,
                "the divider does not reach the bottom of the pill's inside");

            assertEquals(rgb(image, divider.x, decoder.y), navy, "the divider cuts through the navy rim at the top: Adam"
                + " asked for a blue border maintained around it");
            assertEquals(rgb(image, divider.x, decoder.y + decoder.height - 1), navy,
                "the divider cuts through the navy rim at the bottom");

            assertEquals(rgb(image, divider.x - 1, decoder.y + 2), navy, "the divider is more than one pixel wide (left)");
            assertEquals(rgb(image, divider.x + 1, decoder.y + 2), navy, "the divider is more than one pixel wide (right)");

            // THE WORDS, white, on both sides of it: the type left, the address right
            assertTrue(whiteIn(image, decoder.x + 2, divider.x - 1, decoder.y + 2, decoder.y + decoder.height - 3),
                "no white type text left of the divider");
            assertTrue(whiteIn(image, divider.x + 2, decoder.x + decoder.width - 4, decoder.y + 2, decoder.y + decoder.height - 3),
                "no white address text right of the divider");

            // THE PAGE'S WORDS HAVE NO SHAPE: in all the room between the keycap and the decoder pill, every pixel is the
            // background, the label's navy, or a blend of the two - so no outline, fill, line or grey of any kind
            int from = keycap.x + keycap.width, to = decoder.x - 1;

            assertTrue(onlyWordsOnTheBackground(image, from, to, 0, box.height - 1), "something other than the page's words is"
                + " drawn between the keycap and the decoder pill: a pill, a fill or a line.  Adam, round 4: \"no pill around"
                + " that\"");

            // ... and the words ARE THE LABEL'S NAVY, not black or a grey
            assertTrue(navyIn(image, page.x, page.x + page.width, page.y, page.y + page.height - 1), "the page's words are not"
                + " drawn in the label's navy rgb(0,0,115)");

            // ... with the top and bottom rows of the slot empty, where a pill's outline would be
            for (int x = from; x <= to; x++)
            {
                assertEquals(rgb(image, x, 0), none, "something is drawn along the top of the slot at column " + x);
                assertEquals(rgb(image, x, box.height - 1), none, "something is drawn along the bottom of the slot at column " + x);
            }

            // THE KEYCAP: a thin edge all round and a heavier one along the bottom, and a face that is the panel's own
            int midX = keycap.x + keycap.width / 2, midY = keycap.y + keycap.height / 2;

            assertEquals(image.getRGB(keycap.x + 3, midY) & 0xFFFFFF, none, "the keycap's face is filled");

            assertTrue((image.getRGB(keycap.x, midY) & 0xFFFFFF) != none, "the keycap has no edge on its left");
            assertEquals(image.getRGB(keycap.x + 1, midY) & 0xFFFFFF, none, "the keycap's side edge is more than one pixel");

            assertTrue((image.getRGB(midX, keycap.y) & 0xFFFFFF) != none, "the keycap has no edge on its top");
            assertEquals(image.getRGB(midX, keycap.y + 1) & 0xFFFFFF, none, "the keycap's top edge is more than one pixel");

            int h = keycap.y + keycap.height;

            assertTrue((image.getRGB(midX, h - 1) & 0xFFFFFF) != none && (image.getRGB(midX, h - 2) & 0xFFFFFF) != none,
                "the keycap's bottom edge is not two pixels");

            assertEquals(image.getRGB(midX, h - 3) & 0xFFFFFF, none,
                "the keycap's bottom edge is more than two pixels, or its face is not the panel's own");

            // and NOTHING IS PAINTED OUTSIDE THE THREE SHAPES: the gaps between them are the background's
            assertEquals(image.getRGB(keycap.x + keycap.width + 2, midY) & 0xFFFFFF, none, "something is drawn in the gap"
                + " between the keycap and the page pill");

            assertEquals(image.getRGB(page.x + page.width + 2, midY) & 0xFFFFFF, none, "something is drawn in the gap"
                + " between the page pill and the decoder pill");
        });
    }

    /**
     * Twelve point text fits the 18 pixel pills cleanly, and the page's 13 point words the slot: nothing clipped, nothing
     * touching a rim (FR-128, rounds 3 and 4).
     *
     * Adam: *"Increase font sizes by 1 notch to improve visibility - make sure everything still fits vertically."*  The
     * slot is 296 by 18 and does not grow, so the words have to fit in the pills as they are.  A page name with letters
     * that go above the line and below it - "Egypt jgy Alt" - a key with a tail ("Q") and a decoder in both its parts are
     * painted, and each piece's text is looked for in the rows it may use: below the top rim and above the bottom one,
     * with the descenders really there (a clipped descender is a text whose last row is the rim's) and the rim rows
     * themselves untouched, one colour along the whole run of the text.
     *
     * Round 4 put the page back to plain words in the label's 13 point Semibold, which fitted this slot before there
     * were any pills; "12. Egypt jgy Alt" has its digits, capitals and descenders and must show all of them within the 18
     * rows, with the top and the bottom row empty.
     *
     * MUTATION: set the pills' fonts to 14 point, set the page's words to a size the slot cannot hold, or move the baseline
     * down a pixel or two, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTwelvePointTextFitsTheEighteenPixelsCleanly() throws Exception
    {
        withPanel(Boolean.TRUE, p ->
        {
            javax.swing.JLabel slot = p.slot();
            javax.swing.JComponent row = p.row();

            assertNotNull(row, "the window has no row over the locomotive's name (FR-128)");

            p.nameThePage("Egypt jgy Alt");
            p.show(p.mfx("FR-128 fit", 3957));

            // a key with a tail, so the keycap's letter reaches below its baseline
            java.lang.reflect.Method set = null;

            for (java.lang.reflect.Method m : row.getClass().getMethods())
            {
                if (m.getName().equals("setContent")) set = m;
            }

            assertNotNull(set, "the row has no setContent");

            Class<?> contentClass = Class.forName("org.traincontrol.gui.CurrentKeyRow$Content");

            Object content = contentClass.getConstructor(String.class, int.class, String.class, String.class, String.class)
                .newInstance("Q", p.page, "Egypt jgy Alt", "Multi-Unit", "5120");

            final java.lang.reflect.Method setter = set;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    setter.invoke(row, content);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            java.awt.Dimension box = slot.getSize();

            assertEquals(box, new java.awt.Dimension(296, 18), "the slot is not 296 x 18: it has grown, which moves everything below it");

            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(box.width, box.height,
                java.awt.image.BufferedImage.TYPE_INT_RGB);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                java.awt.Graphics2D g = image.createGraphics();

                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, box.width, box.height);

                row.paint(g);
                g.dispose();
            });

            Object at = geometry(row, box.width, box.height);

            java.awt.Rectangle keycap = rect(at, "keycap"), page = rect(at, "page"), decoder = rect(at, "decoder"),
                divider = rect(at, "divider");

            for (java.awt.Rectangle r : new java.awt.Rectangle[] {keycap, page, decoder})
            {
                assertTrue(r.height <= box.height && r.y >= 0 && r.y + r.height <= box.height, "a piece does not fit the slot's"
                    + " height: " + r + " in " + box);
            }

            int bottomRim = box.height - 1;

            assertNotNull(divider, "the decoder pill has no line between its type and its address");

            // THE PAGE'S WORDS, the "12. ", the capitals and the descenders: in the slot, the top row and the bottom row empty
            int[] words = inkRows(image, page.x, page.x + page.width, 0, bottomRim, false);

            assertNotNull(words, "the page's words are not drawn");
            assertTrue(words[0] >= 2, "the page's ascenders reach the top of the slot: ink from row " + words[0]);
            assertTrue(words[1] >= box.height - 4, "the page's descenders are not drawn: its last row is " + words[1]
                + ", so they are clipped");
            assertTrue(words[1] <= bottomRim - 1, "the page's descenders reach the bottom row of the slot: ink to row " + words[1]);

            assertTrue(rowIsOneColour(image, page.x - 2, page.x + page.width + 2, 0), "something is drawn along the top row");
            assertTrue(rowIsOneColour(image, page.x - 2, page.x + page.width + 2, bottomRim),
                "something is drawn along the bottom row");

            // THE KEYCAP'S LETTER, tail and all, in the face: above the heavier bottom edge, below the top one
            int[] key = inkRows(image, keycap.x + 4, keycap.x + keycap.width - 5, 1, bottomRim - 2, false);

            assertNotNull(key, "the key's letter is not drawn");
            assertTrue(key[0] >= 2 && key[1] <= bottomRim - 3, "the key's letter does not fit its face: rows " + key[0] + " to "
                + key[1] + " with the face's rows 1 to " + (bottomRim - 3));

            // THE DECODER, both parts: white on navy, clear of the navy rim above and below
            int decoderTo = decoder.x + decoder.width - 9;

            for (int[] span : new int[][] {{(int) (double) (Double) pub(at, "typeX"), divider.x - 3},
                {(int) (double) (Double) pub(at, "addressX"), decoderTo}})
            {
                int[] ink = inkRows(image, span[0], span[1], 1, bottomRim - 1, true);

                assertNotNull(ink, "part of the decoder is not drawn");
                assertTrue(ink[0] >= 2 && ink[1] <= bottomRim - 2, "the decoder's text is too tall for the pill: rows "
                    + ink[0] + " to " + ink[1]);

                // the straight part of the rim: past the rounded ends, which are not one colour along their length
                int from = Math.max(span[0], decoder.x + 10), to = Math.min(span[1], decoder.x + decoder.width - 11);

                assertTrue(rowIsOneColour(image, from, to, decoder.y), "the decoder's text touches the pill's top rim");
                assertTrue(rowIsOneColour(image, from, to, decoder.y + decoder.height - 1),
                    "the decoder's text touches the pill's bottom rim");
            }
        });
    }

    /**
     * "Show Page Numbers" is a checkbox in the Preferences menu's Locomotive submenu, off until it is asked for, and with
     * it off the line over the locomotive's name has the page's name and no number (FR-128, round 6).
     *
     * Adam, 2026-10-13: *"Make an checkbox option called show page numbers within the preferences jmenu, under the
     * locomotive submenu.  Default off.  When off, 5. MMMM... becomes just MMMM... .  Use the same design patterns as now
     * with prefs."*  The preference is removed before the window is built, so what is read is the default.  The words are
     * the name alone; a name too wide for the room is cut, and cut later than with the number, which no longer takes
     * room; a page with no name reads "Page 5", so the line is never a key with nothing after it (round 7, Adam: *"the
     * text next to the locomotive key should be 'Page 5', not '5' if the page is unnamed"*); and the tooltip has the page's
     * number whatever the setting.
     *
     * MUTATION: default the preference on, leave the item out of the submenu or out of the form's menu, take the number
     * out of the tooltip, let an unnamed page show nothing, or let it read "5.", and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testPageNumbersAreOffUntilAskedFor() throws Exception
    {
        withPanel(null, p ->
        {
            javax.swing.JCheckBoxMenuItem item = p.pageNumbersItem();

            assertNotNull(item, "the window has no Show Page Numbers item (FR-128, round 6)");

            // IN THE LOCOMOTIVE SUBMENU OF THE PREFERENCES MENU, with the bundle's label and a tooltip
            javax.swing.JMenu preferences = (javax.swing.JMenu) privateField(p.ui, "interfaceMenu");
            javax.swing.JMenu locomotive = (javax.swing.JMenu) privateField(p.ui, "locomotiveControlMenu");

            assertTrue(menuHas(preferences, locomotive), "precondition: the Locomotive submenu is not in the Preferences menu");
            assertTrue(menuHas(locomotive, item), "Show Page Numbers is not in the Preferences menu's Locomotive submenu");

            assertEquals(item.getText(), org.traincontrol.util.I18n.t("ui.main.toolbar.showPageNumbers"),
                "the item does not carry the bundle's label");

            assertNotNull(item.getToolTipText(), "the item has no tooltip");
            assertTrue(item.getToolTipText().contains(org.traincontrol.util.I18n.t("ui.main.toolbar.tooltip.showPageNumbers")),
                "the item's tooltip is not the bundle's: " + item.getToolTipText());

            // DEFAULT OFF
            assertFalse(item.isSelected(), "Show Page Numbers is on by default.  Adam, round 6: \"Default off\"");

            // THE LINE: the name alone
            org.traincontrol.marklin.MarklinLocomotive loc = p.mfx("FR-128 numbers off", 3958);

            p.nameThePage("Example");
            p.show(loc);

            assertEquals(p.pageWords(), "Example", "with the numbers off the page's words are not just the name.  Adam,"
                + " round 6: \"When off, 5. MMMM... becomes just MMMM...\"");

            // ... and the tooltip has the page's number whatever the setting
            String tip = p.row().getToolTipText();

            assertTrue(tip != null && tip.contains(org.traincontrol.util.I18n.f("page.ui.plainPageName", p.page, "Example")),
                "the tooltip does not have the page's number with the numbers off: " + tip);

            // THE 22-M NAME, cut with an ellipsis, and with more of it than the number would have left
            p.nameThePage(WIDE_PAGE_NAME);
            p.show(loc);

            String wide = p.pageWords();

            assertTrue(wide.endsWith(String.valueOf((char) 0x2026)) && wide.substring(0, wide.length() - 1).matches("M+"),
                "with the numbers off the wide name is not a run of its Ms and an ellipsis: [" + wide + "]");

            // A PAGE WITH NO NAME reads "Page 5", so the line is not a key with nothing after it (round 7)
            p.nameThePage(null);
            p.show(loc);

            assertEquals(p.pageWords(), unnamedWords(p.page), "a page with no name, with the numbers off, does not read 'Page N'."
                + "  Adam, round 7: \"the text next to the locomotive key should be 'Page 5', not '5' if the page is unnamed\"");
        });
    }

    /**
     * Ticking the item shows the number and keeps the choice, and unticking takes it off (FR-128, round 6).
     *
     * The preference is SET before the window is built, not toggled, so the first click is known to be the one from off to
     * on; the run's preferences are a copy of the operator's, and `Panel.close` puts back what was there.  Driven by
     * clicking the item, which is what the operator does and what runs the item's own handler: the handler stores the
     * choice and the line is redrawn at once, which it can only be if what the row says includes the setting, so the
     * window's check for whether the line has changed finds that it has.
     *
     * Round 7: a page with no name reads "Page 5" ticked or not, which is read at both ends of the click.
     *
     * MUTATION: leave the choice out of the handler, leave it out of what the row says, or have the handler store the
     * opposite, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheItemSwitchesTheNumbersAndKeepsTheChoice() throws Exception
    {
        withPanel(Boolean.FALSE, p ->
        {
            javax.swing.JCheckBoxMenuItem item = p.pageNumbersItem();

            assertNotNull(item, "the window has no Show Page Numbers item (FR-128, round 6)");

            String key = pageNumbersKey();

            java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

            assertFalse(item.isSelected(), "precondition: the item is on with the preference set off");

            org.traincontrol.marklin.MarklinLocomotive loc = p.mfx("FR-128 numbers toggled", 3959);

            p.nameThePage("Example");
            p.show(loc);

            assertEquals(p.pageWords(), "Example", "precondition: the numbers are not off");

            String offWide;

            p.nameThePage(WIDE_PAGE_NAME);
            p.show(loc);

            offWide = p.pageWords();

            p.nameThePage("Example");
            p.show(loc);

            // ON: the number comes in front of the name, at once, and the choice is stored
            javax.swing.SwingUtilities.invokeAndWait(item::doClick);

            assertTrue(item.isSelected(), "clicking the item did not tick it");
            assertTrue(prefs.getBoolean(key, false), "ticking the item did not store the choice in the preference");

            assertEquals(p.pageWords(), p.page + ". Example", "ticking the item did not put the number in front of the name at once");

            // ... and the wide name is cut sooner for it
            p.nameThePage(WIDE_PAGE_NAME);
            p.show(loc);

            String onWide = p.pageWords();

            assertEquals(onWide.substring(0, (p.page + ". ").length()), p.page + ". ", "the wide name has lost its number: [" + onWide + "]");

            assertTrue(onWide.length() - (p.page + ". ").length() < offWide.length(), "the wide name is not cut sooner with the number"
                + " in front of it: [" + onWide + "] against [" + offWide + "]");

            p.nameThePage("Example");
            p.show(loc);

            // OFF: the number goes, at once, and the choice is stored
            javax.swing.SwingUtilities.invokeAndWait(item::doClick);

            assertFalse(item.isSelected(), "clicking the ticked item did not untick it");
            assertFalse(prefs.getBoolean(key, true), "unticking the item did not store the choice in the preference");

            assertEquals(p.pageWords(), "Example", "unticking the item did not take the number off at once");

            // an unnamed page, off, reads "Page 5" (round 7) ...
            p.nameThePage(null);
            p.show(loc);

            assertEquals(p.pageWords(), unnamedWords(p.page), "an unnamed page with the numbers off does not read 'Page N'");

            // ... and ticked again, the same: the number setting is for a page that has a name
            javax.swing.SwingUtilities.invokeAndWait(item::doClick);

            assertTrue(item.isSelected(), "clicking the item again did not tick it");

            assertEquals(p.pageWords(), unnamedWords(p.page), "an unnamed page with the numbers on does not read 'Page N'.  Adam,"
                + " round 7: \"If no name, use Page 5\"");
        });
    }

    /**
     * The preference is read at start-up, as the other ticks in the Locomotive submenu are (FR-128, round 6).
     *
     * A window built with the preference set on has the item ticked and the number in front of the name, and one built with
     * it set off has neither - so the state the line is built from is read from the preference when the window starts,
     * not left at the item's own default.  Set, not toggled, and put back, as above.
     *
     * Round 7: the same reading reaches the page tabs and the page label between the arrows, in all three cases of the one rule
     * - a name with the numbers on, a name with them off, and a page with no name, which reads "Page 5" either way.
     *
     * MUTATION: leave the item's start-up state out of the start-up block, or word the tabs or the label without asking
     * it, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testThePreferenceIsReadAtStartUp() throws Exception
    {
        for (boolean on : new boolean[] {true, false})
        {
            withPanel(on, p ->
            {
                javax.swing.JCheckBoxMenuItem item = p.pageNumbersItem();

                assertNotNull(item, "the window has no Show Page Numbers item (FR-128, round 6)");

                assertEquals(item.isSelected(), on, "a window started with the preference " + (on ? "on" : "off")
                    + " has the item " + (item.isSelected() ? "ticked" : "not ticked"));

                int page = p.shown();

                assertEquals(page, p.page, "precondition: the page the label shows is not the page of the active key");

                org.traincontrol.marklin.MarklinLocomotive loc = p.mfx("FR-128 numbers at start", 3960);

                for (String name : new String[] {"Example", WIDE_PAGE_NAME, null})
                {
                    p.renameTheShownPage(name);
                    p.show(loc);

                    String said = " for " + (name == null ? "a page with no name" : "a page called " + name)
                        + " in a window started with the preference " + (on ? "on" : "off");

                    // THE LINE OVER THE NAME
                    String row = p.pageWords();

                    if (WIDE_PAGE_NAME.equals(name))
                    {
                        assertTrue(row.startsWith(on ? page + ". M" : "M") && row.endsWith(String.valueOf((char) 0x2026)),
                            "the line over the name is not a cut run of Ms" + said + ": [" + row + "]");
                    }
                    else
                    {
                        assertEquals(row, wording(page, name, on, Integer.MAX_VALUE), "the line over the name is worded as the"
                            + " other way" + said);
                    }

                    // THE PAGE TAB, and THE PAGE LABEL
                    assertEquals(p.tab(page), wording(page, name, on, TrainControlUI.MAX_PAGE_NAME_LENGTH_TAB),
                        "the page tab is not worded by the preference read at start-up" + said);

                    assertEquals(p.label(), wording(page, name, on, TrainControlUI.MAX_PAGE_NAME_LENGTH),
                        "the page label is not worded by the preference read at start-up" + said);
                }
            });
        }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // FR-128, round 7: the page tabs and the page label follow the one rule the line over the name does
    // ------------------------------------------------------------------------------------------------------------------

    /**
     * Forty-four Ms: wider than the page label's forty-letter cut, which the 22-M page is not.
     */
    private static final String LONG_PAGE_NAME = WIDE_PAGE_NAME + WIDE_PAGE_NAME;

    /** The bundle's words for a page with no name, "Page 5" in English. */
    private static String unnamedWords(int page)
    {
        return org.traincontrol.util.I18n.f("page.ui.pageNo", page);
    }

    /**
     * Adam's rule, written out again for the claims to hold the program to: for page 5 with the name N, "Page 5" with no
     * name, "N" with the numbers off, "5. N" with them on - and only the NAME cut, to the room the place has for it.
     *
     * @param page the page
     * @param name its name, or null for none
     * @param on whether the numbers are shown
     * @param cutTo how many letters of the name the place has room for
     * @return the words
     */
    private static String wording(int page, String name, boolean on, int cutTo)
    {
        if (name == null || name.trim().isEmpty()) return unnamedWords(page);

        String cut = name.length() > cutTo ? name.substring(0, cutTo) : name;

        return on ? page + ". " + cut : cut;
    }

    /**
     * Clicks the item and reads the tab and the label in the SAME event-thread task, so that nothing but the click's own
     * handler can have worded them in between: the window's renders land on the event thread later, and would hide a
     * handler that left the tabs and the label to them.
     */
    private static String[] clickAndRead(Panel p, javax.swing.JCheckBoxMenuItem item, int page) throws Exception
    {
        javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) privateField(p.ui, "locKeyTabs");
        javax.swing.JLabel label = (javax.swing.JLabel) privateField(p.ui, "LocMappingNumberLabel");

        final String[] read = new String[2];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            item.doClick();

            read[0] = tabs.getTitleAt(page - 1);
            read[1] = label.getText();
        });

        return read;
    }

    /** Where everything that shares the page label's panel is, except the label itself, as one text. */
    private static String whereTheLabelsNeighboursAre(Panel p) throws Exception
    {
        javax.swing.JLabel label = (javax.swing.JLabel) privateField(p.ui, "LocMappingNumberLabel");
        java.awt.Container parent = label.getParent();

        StringBuilder where = new StringBuilder("panel " + parent.getSize() + " window " + p.ui.getSize());

        for (Field f : TrainControlUI.class.getDeclaredFields())
        {
            if (!java.awt.Component.class.isAssignableFrom(f.getType())) continue;

            f.setAccessible(true);

            java.awt.Component c = (java.awt.Component) f.get(p.ui);

            if (c == null || c == label || c.getParent() != parent) continue;

            where.append("\n  ").append(f.getName()).append(" at ").append(c.getBounds());
        }

        return where.toString();
    }

    /**
     * The rule itself, with no window: for page 5 with the name N, "Page 5" with no name, "N" with the numbers off, "5. N"
     * with them on - and only the NAME is the caller's to cut (FR-128, round 7).
     *
     * Adam: *"when the show page numbers is turned off, the keyboard tabs themselves should also not show the page number
     * unless the page has no name.  If no name, use Page 5.  If yes name, MMM if show is off, and 5. MMM if show is on."*
     * and *"That way, all are consistent."*  So there is one rule, asked by every place that follows the setting, and this
     * is it asked directly: every case, the cuts that are the caller's, and the one thing the rule keeps for itself - a
     * name that had no room at all leaves the number and its full stop.
     *
     * Asked by name, so this class still compiles - and fails, in words - on a window without the rule.
     *
     * MUTATION: show the number whatever the setting, never show it, word an unnamed page "5." or leave it out, cut the
     * number or the "Page 5" with the name, ask the caller to cut an unnamed page, or lose a name's trim, and this fails.
     *
     * @throws Exception from the rule
     */
    @Test
    public void testTheRuleWordsAPageThreeWays() throws Exception
    {
        java.lang.reflect.Method rule = null;

        try
        {
            rule = Class.forName("org.traincontrol.gui.CurrentKeyRow").getMethod("pageWords", int.class, String.class,
                boolean.class, java.util.function.UnaryOperator.class);
        }
        catch (NoSuchMethodException none)
        {
            // said below
        }

        assertNotNull(rule, "there is no one rule the line, the tabs and the label ask for the page's words (FR-128, round 7)");

        final java.lang.reflect.Method pageWords = rule;

        java.util.function.UnaryOperator<String> whole = name -> name;
        java.util.function.UnaryOperator<String> fourteen = name -> name.length() > 14 ? name.substring(0, 14) : name;
        java.util.function.UnaryOperator<String> noRoom = name -> "";
        java.util.function.UnaryOperator<String> notAsked = name ->
        {
            throw new AssertionError("a page with no name was handed to the caller to cut: [" + name + "]");
        };

        // A PAGE WITH NO NAME: "Page 5", whatever the setting, and nothing for the caller to cut
        for (boolean on : new boolean[] {false, true})
        {
            for (String none : new String[] {null, "", "   "})
            {
                assertEquals(pageWords.invoke(null, 5, none, on, notAsked), unnamedWords(5), "a page with no name ["
                    + none + "] does not read 'Page 5' with the numbers " + (on ? "on" : "off") + ".  Adam, round 7: \"If no"
                    + " name, use Page 5\"");
            }
        }

        // A NAME, with the numbers off: the name; on: "5. " and the name
        assertEquals(pageWords.invoke(null, 5, "Example", false, whole), "Example",
            "with the numbers off a named page is not just its name");

        assertEquals(pageWords.invoke(null, 5, "Example", true, whole), "5. Example",
            "with the numbers on a named page is not its number, a full stop and its name");

        assertEquals(pageWords.invoke(null, 12, "Example", true, whole), "12. Example",
            "a two-digit page is not its number, a full stop and its name");

        // THE NAME IS TRIMMED, as it is typed with spaces round it
        assertEquals(pageWords.invoke(null, 5, "  Example ", true, whole), "5. Example", "a name with spaces round it is not trimmed");

        // ONLY THE NAME IS CUT: fourteen letters of the 22 Ms, and the "5. " in front of them whole
        String cut = WIDE_PAGE_NAME.substring(0, 14);

        assertEquals(pageWords.invoke(null, 5, WIDE_PAGE_NAME, false, fourteen), cut, "the wide name is not cut by the caller");

        assertEquals(pageWords.invoke(null, 5, WIDE_PAGE_NAME, true, fourteen), "5. " + cut,
            "the number went with the cut, or is inside it: it is the name that is cut, never the '5. '");

        assertEquals(pageWords.invoke(null, 12, WIDE_PAGE_NAME, true, fourteen), "12. " + cut, "the two-digit number was cut");

        // A NAME WITH NO ROOM AT ALL leaves the number and its full stop, with the numbers on or off: never nothing
        for (boolean on : new boolean[] {false, true})
        {
            assertEquals(pageWords.invoke(null, 5, "Example", on, noRoom), "5.", "a name with no room at all is not left as the"
                + " number and its full stop with the numbers " + (on ? "on" : "off"));
        }
    }

    /**
     * The page tabs are worded by the one rule (FR-128, round 7).
     *
     * Adam: *"when the show page numbers is turned off, the keyboard tabs themselves should also not show the page number
     * unless the page has no name.  If no name, use Page 5.  If yes name, MMM if show is off, and 5. MMM if show is on."*
     * The page tabs are the ones shown with "Show Locomotive Pages as Tabs" ticked, in a window built with the numbers off
     * and again with them on - set before the window, not toggled - and a page is a page with no name, a page called
     * Example and the 22-M page.  A tab has room for fourteen letters of the NAME: the 22 Ms are cut to 14, and with the
     * numbers on the "5. " is in front of those fourteen and is not one of them.  The tab's tooltip, "Page 5", is the same
     * in every case.
     *
     * MUTATION: show the number whatever the setting, never show it, word an unnamed page "5." or by its name's cut, let the
     * tabs ignore the setting, cut the "5. " with the name, or change the tab's tooltip, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testThePageTabsFollowTheRule() throws Exception
    {
        for (boolean on : new boolean[] {false, true})
        {
            withPanel(on, Boolean.TRUE, p ->
            {
                int page = p.page;

                for (String name : new String[] {null, "Example", WIDE_PAGE_NAME})
                {
                    p.nameThePage(name);
                    p.show(null);

                    String said = " for " + (name == null ? "a page with no name" : "a page called " + name) + " with the numbers "
                        + (on ? "on" : "off");

                    assertEquals(p.tab(page), wording(page, name, on, TrainControlUI.MAX_PAGE_NAME_LENGTH_TAB),
                        "the page tab is not worded by the rule" + said + ".  Adam, round 7: \"If no name, use Page 5.  If yes"
                        + " name, MMM if show is off, and 5. MMM if show is on.\"");

                    assertEquals(p.tabTip(page), unnamedWords(page), "the tab's tooltip is not 'Page N'" + said);
                }
            });
        }
    }

    /**
     * The page label between the arrows is worded by the one rule, in one line, and moves nothing (FR-128, round 7).
     *
     * Adam: *"That way, all are consistent."*  The label is the one shown with the tabs off, and it was the name on one line
     * and "Page 5" under it; it is now the rule's words on one line - "Page 5" for a page with no name, "N" with the numbers
     * off, "5. N" with them on - with its own cut of forty letters of the NAME: the 22-M page is whole, a 44-M page is cut
     * to 40.
     *
     * One line is shorter than the two the label had for a named page.  It is a generated component in a fixed layout, and
     * this reads that nothing around it moved or was resized, whatever the page is called: the panel, the window and every
     * other component are where they are for a page with no name, and the label is the height and the place it is for a
     * page with no name - which was always one line.
     *
     * MUTATION: show the number whatever the setting, never show it, word an unnamed page "5.", put the name back on a line
     * of its own over "Page 5", cut the forty letters short or not at all, or cut the "5. ", and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testThePageLabelFollowsTheRuleAndMovesNothing() throws Exception
    {
        for (boolean on : new boolean[] {false, true})
        {
            withPanel(on, Boolean.FALSE, p ->
            {
                javax.swing.JLabel label = (javax.swing.JLabel) privateField(p.ui, "LocMappingNumberLabel");

                assertTrue(label.isVisible(), "precondition: with the tabs off the page label is not the one shown");

                int page = p.shown();

                assertEquals(page, p.page, "precondition: the page the label shows is not the page of the active key");

                // A PAGE WITH NO NAME is the reference for where everything is: it has always been one line
                p.renameTheShownPage(null);
                javax.swing.SwingUtilities.invokeAndWait(() -> p.ui.validate());

                java.awt.Rectangle labelAt = label.getBounds();
                String around = whereTheLabelsNeighboursAre(p);

                assertEquals(p.label(), unnamedWords(page), "the page label of a page with no name is not 'Page N' with the numbers "
                    + (on ? "on" : "off") + ".  Adam, round 7: \"If no name, use Page 5\"");

                assertTrue(labelAt.height > 0 && labelAt.width > 0, "precondition: the label has no size: " + labelAt);

                for (String name : new String[] {"Example", WIDE_PAGE_NAME, LONG_PAGE_NAME})
                {
                    p.renameTheShownPage(name);
                    javax.swing.SwingUtilities.invokeAndWait(() -> p.ui.validate());

                    String said = " for a page called " + name + " with the numbers " + (on ? "on" : "off");

                    String text = p.label();

                    assertEquals(text, wording(page, name, on, TrainControlUI.MAX_PAGE_NAME_LENGTH),
                        "the page label is not worded by the rule" + said + ".  Adam, round 7: \"If yes name, MMM if show is off,"
                        + " and 5. MMM if show is on.\"");

                    assertFalse(text.toLowerCase().contains("<html") || text.toLowerCase().contains("<br"),
                        "the page label is more than one line" + said + ": [" + text + "]");

                    assertEquals(label.getBounds(), labelAt, "the page label moved or changed size" + said);

                    assertEquals(whereTheLabelsNeighboursAre(p), around, "something round the page label moved or changed size"
                        + said);
                }

                // the cut is the name's alone: 40 Ms of 44, and the 22 whole
                assertEquals(TrainControlUI.MAX_PAGE_NAME_LENGTH.intValue(), 40, "the label's cut is not the forty letters it had");

                // BACK TO NO NAME: where it was
                p.renameTheShownPage(null);
                javax.swing.SwingUtilities.invokeAndWait(() -> p.ui.validate());

                assertEquals(label.getBounds(), labelAt, "the page label did not come back to where it was");
            });
        }
    }

    /**
     * Ticking or unticking the item words the page tabs and the page label again at once (FR-128, round 7).
     *
     * The tabs and the label are set when a page is named or chosen, so nothing about a change to the setting reaches them
     * unless the item's own handler sends it.  Driven by clicking the item, as the operator does, with the tab and the label
     * read in the SAME event-thread task as the click: the renders the click also asks for land on the event thread later and
     * would word the tabs on their own, which would hide a handler that left it to them.  The line over the name is read
     * once its render is done.  A page with no name, a page called Example and the 22-M page, each ticked and unticked.
     *
     * MUTATION: leave the tabs or the label out of the handler, or word them from the setting as it was before the click,
     * and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheItemRetitlesTheTabsAndTheLabelAtOnce() throws Exception
    {
        withPanel(Boolean.FALSE, Boolean.FALSE, p ->
        {
            javax.swing.JCheckBoxMenuItem item = p.pageNumbersItem();

            assertNotNull(item, "the window has no Show Page Numbers item (FR-128, round 6)");
            assertFalse(item.isSelected(), "precondition: the item is on with the preference set off");

            int page = p.shown();

            assertEquals(page, p.page, "precondition: the page the label shows is not the page of the active key");

            org.traincontrol.marklin.MarklinLocomotive loc = p.mfx("FR-128 numbers retitled", 3961);

            for (String name : new String[] {"Example", WIDE_PAGE_NAME, null})
            {
                p.renameTheShownPage(name);
                p.show(loc);

                String said = " for " + (name == null ? "a page with no name" : "a page called " + name);

                // OFF to begin with, as the preference was set
                assertEquals(p.tab(page), wording(page, name, false, TrainControlUI.MAX_PAGE_NAME_LENGTH_TAB),
                    "precondition: the tab is not worded for the numbers off" + said);

                assertEquals(p.label(), wording(page, name, false, TrainControlUI.MAX_PAGE_NAME_LENGTH),
                    "precondition: the label is not worded for the numbers off" + said);

                // TICK: the tab and the label at once
                String[] ticked = clickAndRead(p, item, page);

                assertTrue(item.isSelected(), "clicking the item did not tick it" + said);

                assertEquals(ticked[0], wording(page, name, true, TrainControlUI.MAX_PAGE_NAME_LENGTH_TAB),
                    "ticking the item did not word the page tab again at once" + said);

                assertEquals(ticked[1], wording(page, name, true, TrainControlUI.MAX_PAGE_NAME_LENGTH),
                    "ticking the item did not word the page label again at once" + said);

                // ... and the line over the name with them
                String row = p.pageWords();

                if (WIDE_PAGE_NAME.equals(name))
                {
                    assertTrue(row.startsWith(page + ". M") && row.endsWith(String.valueOf((char) 0x2026)),
                        "ticking the item did not word the line over the name" + said + ": [" + row + "]");
                }
                else
                {
                    assertEquals(row, wording(page, name, true, Integer.MAX_VALUE),
                        "ticking the item did not word the line over the name" + said);
                }

                // UNTICK: back
                String[] unticked = clickAndRead(p, item, page);

                assertFalse(item.isSelected(), "clicking the ticked item did not untick it" + said);

                assertEquals(unticked[0], wording(page, name, false, TrainControlUI.MAX_PAGE_NAME_LENGTH_TAB),
                    "unticking the item did not word the page tab again at once" + said);

                assertEquals(unticked[1], wording(page, name, false, TrainControlUI.MAX_PAGE_NAME_LENGTH),
                    "unticking the item did not word the page label again at once" + said);
            }
        });
    }

    /**
     * A page name with no room for a letter leaves the number alone, and a page with no name is drawn whole (FR-128, round 7).
     *
     * Round 6 left a named page with no room for even one letter and the ellipsis at the number and its full stop, "5.", and
     * round 7 keeps that, with the numbers on or off; a page with no name reads "Page 5" at every width, and is not cut to
     * fit.  Asked of the row in boxes from wide to narrower than the keycap and the decoder pill together, so that the cut
     * takes the name to nothing somewhere along the way - and there the words are the number alone, and everywhere else the
     * name, whole or cut, with its "5. " when the numbers are on.
     *
     * MUTATION: show nothing, or "Page 5", for a name with no room, drop the full stop, cut "Page 5" with the room, or leave
     * the "5. " off the cut name, and this fails.
     *
     * @throws Exception from the row
     */
    @Test
    public void testANameWithNoRoomLeavesTheNumberAloneAndAnUnnamedPageIsWhole() throws Exception
    {
        Class<?> rowClass = Class.forName("org.traincontrol.gui.CurrentKeyRow");
        Class<?> contentClass = Class.forName("org.traincontrol.gui.CurrentKeyRow$Content");

        java.lang.reflect.Constructor<?> make = contentClass.getConstructor(String.class, int.class, String.class, String.class,
            String.class, boolean.class);

        java.lang.reflect.Method set = rowClass.getMethod("setContent", contentClass);

        for (boolean on : new boolean[] {false, true})
        {
            for (String name : new String[] {"Example", WIDE_PAGE_NAME, null})
            {
                javax.swing.JComponent row = (javax.swing.JComponent) rowClass.getConstructor().newInstance();

                set.invoke(row, make.newInstance("A", 5, name, "Multi-Unit", "5120", on));

                String said = " for " + (name == null ? "a page with no name" : "a page called " + name) + " with the numbers "
                    + (on ? "on" : "off");

                boolean sawNoRoom = false;

                for (int width = 400; width >= 60; width--)
                {
                    Object at = geometry(row, width, 18);

                    String text = (String) pub(at, "pageText"), cut = (String) pub(at, "name");

                    String where = said + " in a box " + width + " wide: [" + text + "]";

                    if (name == null)
                    {
                        assertEquals(text, unnamedWords(5), "a page with no name is not 'Page 5', whole" + where);
                        assertEquals(cut, "", "a page with no name has a name drawn" + where);
                    }
                    else if (cut.isEmpty())
                    {
                        sawNoRoom = true;

                        assertEquals(text, "5.", "a name with no room is not left as the number and its full stop" + where);
                    }
                    else
                    {
                        assertEquals(text, on ? "5. " + cut : cut, "a name that has room is not worded by the rule" + where);
                    }
                }

                if (name != null)
                {
                    assertTrue(sawNoRoom, "the boxes never left a name without room, so the corner was not asked" + said);
                }
            }
        }
    }

    /**
     * The item's tooltip says where the number goes: on the page tabs, beside the page arrows and over the locomotive's
     * name (FR-128, round 7), in every language the program has.
     *
     * The English text is read from its bundle and has to name the three places; each of the other seven has to be there and
     * not be the English text left in.  Read from the files, not through the bundle, because the bundle chooses by the
     * machine's language.
     *
     * MUTATION: leave the tabs, the arrows or the locomotive out of the English text, or leave a language's untranslated,
     * and this fails.
     *
     * @throws Exception from reading the bundles
     */
    @Test
    public void testTheTooltipSaysWhereTheNumbersGo() throws Exception
    {
        String key = "ui.main.toolbar.tooltip.showPageNumbers";

        String english = bundleValue("messages.properties", key);

        assertNotNull(english, "the English bundle has no tooltip for Show Page Numbers");

        String lower = english.toLowerCase();

        assertTrue(lower.contains("tabs"), "the tooltip does not say the number goes on the page tabs: " + english);
        assertTrue(lower.contains("arrows"), "the tooltip does not say the number goes beside the page arrows: " + english);
        assertTrue(lower.contains("name of the locomotive"), "the tooltip no longer says the number goes above the name of the"
            + " locomotive: " + english);

        for (String language : new String[] {"da", "de", "es", "fr", "it", "nl", "pl"})
        {
            String there = bundleValue("messages_" + language + ".properties", key);

            assertNotNull(there, "the " + language + " bundle has no tooltip for Show Page Numbers");
            assertFalse(there.trim().isEmpty(), "the " + language + " tooltip is empty");
            assertFalse(there.equals(english), "the " + language + " tooltip is the English text, untranslated");
        }
    }

    /** One value from one of the message bundles, read from the file as the program reads it. */
    private static String bundleValue(String file, String key) throws Exception
    {
        java.util.Properties values = new java.util.Properties();

        try (java.io.Reader in = new java.io.InputStreamReader(new java.io.FileInputStream(
            "src/org/traincontrol/resources/" + file), java.nio.charset.StandardCharsets.UTF_8))
        {
            values.load(in);
        }

        return values.getProperty(key);
    }

    /** Whether this menu has this item or submenu directly in it. */
    private static boolean menuHas(javax.swing.JMenu menu, java.awt.Component item)
    {
        for (java.awt.Component c : menu.getMenuComponents())
        {
            if (c == item) return true;
        }

        return false;
    }

    /** Where every other part of the locomotive panel is, as one text, for a check that none of it moved. */
    private static String whereTheOthersAre(Panel p) throws Exception
    {
        javax.swing.JComponent panel = (javax.swing.JComponent) privateField(p.ui, "LocFunctionsPanel");

        StringBuilder where = new StringBuilder("panel " + panel.getSize() + " max " + panel.getMaximumSize());

        for (String name : new String[] {"PowerOff", "OnButton", "CurrentKeyLabel", "ActiveLocLabel", "Backward", "Forward",
            "SpeedSlider", "FunctionTabs", "locIcon"})
        {
            java.awt.Component c = (java.awt.Component) privateField(p.ui, name);

            java.awt.Point at = javax.swing.SwingUtilities.convertPoint(c.getParent(), c.getLocation(), panel);

            where.append("\n  ").append(name).append(" at ").append(at.x).append(",").append(at.y).append(" size ")
                .append(c.getWidth()).append("x").append(c.getHeight());
        }

        return where.toString();
    }

    private TrainControlUI build() throws Exception
    {
        // The window reads the layout preference in its constructor, so it opens Adam’s railway
        // unless something points it elsewhere first (OB-111). This was the last class building one
        // without a sandbox.
        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] built = new TrainControlUI[1];

        try
        {
            // Inside the try, so nothing between the open and the close can leave the
            // preference behind (TSX-B8).
            sandbox = support.LayoutSandbox.open();

            javax.swing.SwingUtilities.invokeAndWait(() -> built[0] = new TrainControlUI());
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }

        return built[0];
    }

    /**
     * Puts the window on a given number of pages.
     *
     * By reflection because the count is private and has no setter - the only ways to move it are to
     * add or delete pages, and adding is the thing under test.
     */
    private void setPages(TrainControlUI ui, int pages) throws Exception
    {
        Field count = TrainControlUI.class.getDeclaredField("numLocMappings");

        count.setAccessible(true);
        count.setInt(ui, pages);
    }

    /**
     * The Add Page item out of a freshly built page menu.
     *
     * The menu is an inner class built inside `showPopup`, which needs a shown component to pop over,
     * so it is constructed directly. Found by ACTION rather than by label: the label is translated
     * and the wording is Adam's to change, while what the item does is the thing being asked about.
     */
    private JMenuItem addItemOf(TrainControlUI ui) throws Exception
    {
        RightClickPageMenu outer = new RightClickPageMenu(ui);

        Class<?> menuClass = Class.forName("org.traincontrol.gui.RightClickPageMenu$RightClickMenu");

        Constructor<?> make = menuClass.getDeclaredConstructors()[0];

        make.setAccessible(true);

        MouseEvent event = new MouseEvent(new javax.swing.JPanel(), MouseEvent.MOUSE_PRESSED,
            0L, 0, 1, 1, 1, true);

        final JPopupMenu[] menu = new JPopupMenu[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                menu[0] = (JPopupMenu) make.newInstance(outer, ui, event);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        JMenuItem found = null;

        for (java.awt.Component c : menu[0].getComponents())
        {
            if (!(c instanceof JMenuItem)) continue;

            JMenuItem item = (JMenuItem) c;

            // The label of the one that adds a page, asked of the same bundle the menu asked.
            if (item.getText().equals(org.traincontrol.util.I18n.t("page.ui.menuAddPage")))
            {
                assertNull(found, "two Add Page items in one menu");

                found = item;
            }
        }

        return found;
    }

    /**
     * The gap between a key row's pieces, asked of the row (`CurrentKeyRow.GAP`) rather than restated.
     *
     * @return the gap in pixels
     */
    private static int rowGap()
    {
        try
        {
            java.lang.reflect.Field gap = Class.forName("org.traincontrol.gui.CurrentKeyRow").getField("GAP");

            gap.setAccessible(true);

            return gap.getInt(null);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("CurrentKeyRow has no GAP to ask", e);
        }
    }
}
