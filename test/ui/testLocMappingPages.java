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
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String add = withoutComments(bodyOf(source, "public void addLocMappingPage()"));

        assertFalse(add.isEmpty(), "cannot find addLocMappingPage - has it been renamed?");

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
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String load = withoutComments(bodyOf(source, "public void setViewListener("));

        assertFalse(load.isEmpty(), "cannot find setViewListener - has it been renamed?");

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

        /** A window over a model, as the application builds it; the page it is on is read from the window. */
        static Panel open() throws Exception
        {
            Panel p = new Panel();

            try
            {
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
                    if (model != null) model.stop();

                    if (sandbox != null) sandbox.close();
                }
            }
        }
    }

    private interface Body
    {
        void run(Panel p) throws Exception;
    }

    private static void withPanel(Body body) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the locomotive panel is on a window");
        }

        Panel p = Panel.open();

        try
        {
            body.run(p);
        }
        finally
        {
            p.close();
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

    private static Object pub(Object owner, String name) throws Exception
    {
        return owner.getClass().getField(name).get(owner);
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

    /** What the three pieces say, joined, for a check that a word is not in any of them. */
    private static String drawn(Object geometry) throws Exception
    {
        return pub(geometry, "key") + "|" + pub(geometry, "number") + "|" + pub(geometry, "name") + "|"
            + pub(geometry, "decoderText");
    }

    /**
     * The line over the locomotive's name is a keycap, a page pill and a decoder pill, for every kind of decoder (FR-128).
     *
     * Adam: *"the active locomotive info is currently text consisting of [letter] . Page [num] ([page name]) .
     * [decodertype] [address]. the page number could be condensed into a nice pill, along with the other info.  make it
     * look more modern."*  The design he chose: the key's letter on a keycap, then a page pill with the page's NUMBER and
     * its name, then a solid decoder pill saying what the line said before - "MFX 54".  *"I wouldn't like to see the word
     * 'page' in it."*
     *
     * One locomotive of each decoder type the program has, on the page of the window's current key: MM2, MFX, DCC, a
     * multi-unit and a consist of an MFX head with a member.  Driven through `repaintLoc`, which is what the window calls
     * to show a locomotive, and read from the row's own layout - what is drawn is what is asked, so there is no second
     * account of it to drift.
     *
     * MUTATION: put the word Page back in the page pill (draw `getTooltip`'s page words there), swap the order of the
     * shapes, or draw the key and the decoder as one text again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheLineIsAKeycapAndTwoPillsForEveryDecoder() throws Exception
    {
        withPanel(p ->
        {
            javax.swing.JLabel slot = p.slot();
            javax.swing.JComponent row = p.row();

            assertNotNull(row, "the window has no row over the locomotive's name: the line is still the old text in"
                + " CurrentKeyLabel, which cannot draw a keycap or a pill (FR-128)");

            assertEquals(slot.getText(), "", "CurrentKeyLabel still carries the old text as well as the row, so the line"
                + " would be said twice");

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
            Object[][] kinds = {
                {p.mm2("FR-128 MM2", 79), "MM2 79"},
                {p.mfx("FR-128 MFX", 3954), "MFX 3954"},
                {p.dcc("FR-128 DCC", 9871), "DCC 9871"},
                {multiUnit, org.traincontrol.util.I18n.t("loc.multiUnit") + " 4999"},
                {head, org.traincontrol.util.I18n.f("loc.multiUnitLinked", "MFX") + " 3912"},
            };

            // the word the program uses for a page, out of its own bundle - "Page" in English
            String pageWord = org.traincontrol.util.I18n.f("page.ui.pageNo", 0).replace("0", "").trim().toLowerCase();

            assertFalse(pageWord.isEmpty(), "precondition: cannot find the bundle's word for a page");

            java.awt.Dimension box = slot.getSize();

            for (Object[] kind : kinds)
            {
                p.show((org.traincontrol.base.Locomotive) kind[0]);

                String said = "for " + kind[1];

                Object at = geometry(row, box.width, box.height);

                assertNotNull(at, "the row draws nothing " + said);

                assertEquals(pub(at, "key"), key, "the keycap does not carry the key's letter " + said);

                assertEquals(pub(at, "number"), String.valueOf(p.page),
                    "the page pill does not carry the page's number " + said);

                assertEquals(pub(at, "name"), "Example", "the page pill does not carry the page's name " + said);

                assertEquals(pub(at, "decoderText"), kind[1], "the decoder pill does not say the decoder and address " + said);

                String all = drawn(at).toLowerCase();

                assertFalse(all.contains("page") || all.contains(pageWord),
                    "the word for a page is in the line " + said + ": " + drawn(at)
                    + ".  Adam: \"I wouldn't like to see the word 'page' in it.\"");

                // keycap, then page pill, then decoder pill, a gap apart, left to right
                java.awt.Rectangle keycap = rect(at, "keycap"), page = rect(at, "page"), decoder = rect(at, "decoder");

                assertNotNull(keycap, "no keycap " + said);
                assertNotNull(decoder, "no decoder pill " + said);

                assertEquals(page.x, keycap.x + keycap.width + 5, "the page pill is not a 5 px gap after the keycap " + said);
                assertEquals(decoder.x, page.x + page.width + 5, "the decoder pill is not a 5 px gap after the page pill " + said);

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
                    "Example")) && tip.contains((String) kind[1]), "the tooltip " + said + " is not the whole line: " + tip);
            }

            // A KEY WITH NO LOCOMOTIVE ON IT has the key and the page and no decoder pill
            p.show(null);

            Object bare = geometry(row, box.width, box.height);

            assertNotNull(bare, "nothing is drawn for a key with no locomotive: the key and its page went with it");
            assertNull(rect(bare, "decoder"), "a decoder pill is drawn for a key with no locomotive on it");
            assertEquals(pub(bare, "name"), "Example");
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
     * Measured with the widest decoder text, the multi-unit's, because that is the one that leaves the least room.
     *
     * MUTATION: cut the name by letters instead of by width, leave the ellipsis off, let a pill grow past 18 px, or draw
     * the name's pill wider than the room it was given, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAWidePageNameGivesWayAndTheRestDoesNot() throws Exception
    {
        withPanel(p ->
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

                String decoderText = loc.getDecoderTypeLabel() + " " + p.model.getLocAddress(loc.getName());

                assertEquals(pub(at, "decoderText"), decoderText, "the decoder text was cut " + said);

                assertNotNull(rect(at, "keycap"), "the keycap went " + said);

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

            // A PAGE WITH NO NAME shows the number alone
            p.nameThePage(null);
            p.show(plain);

            Object unnamed = geometry(row, box.width, box.height);

            assertEquals(pub(unnamed, "name"), "", "a page with no name still has a name in its pill");
            assertEquals(pub(unnamed, "number"), String.valueOf(p.page), "a page with no name has lost its number");

            // AND A RENAME REACHES THE LINE THE WAY IT ALWAYS DID: through a repaint that is not forced.  The old line
            // compared its own text to decide whether anything had changed; the row compares what it says.
            p.nameThePage("Renamed");
            p.repaintWithoutForce();

            assertEquals(pub(geometry(row, box.width, box.height), "name"), "Renamed",
                "a changed page name did not reach the line: the check for whether it changed has stopped seeing the name");
        });
    }

    /**
     * What is painted is the design Adam chose: a keycap with a heavier bottom edge, a page pill that is an outline and
     * nothing else, and a decoder pill that is solid navy (FR-128).
     *
     * Read from the pixels the row paints, over a background that is nothing else, so a fill that is not meant to be there
     * shows as a change from it.  Each reading is a point well inside one shape and clear of its words, so it does not
     * depend on how the letters are drawn - only on whether the shape is filled, outlined or neither.
     *
     * MUTATION: fill the page pill, draw the keycap's edge one pixel all round, or take the decoder's navy from anywhere
     * but rgb(0,0,115), and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheShapesAreThoseAdamChose() throws Exception
    {
        withPanel(p ->
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

            java.awt.Rectangle keycap = rect(at, "keycap"), page = rect(at, "page"), decoder = rect(at, "decoder");

            // THE DECODER PILL: solid, in the navy the old line's text was, which is CurrentKeyLabel's own foreground
            assertEquals(image.getRGB(decoder.x + 4, decoder.y + decoder.height / 2) & 0xFFFFFF, 0x000073,
                "the decoder pill is not solid navy rgb(0,0,115)");

            // THE PAGE PILL: an outline, and nothing inside it
            assertEquals(image.getRGB(page.x + 4, page.y + page.height / 2) & 0xFFFFFF, none,
                "the page pill is filled - Adam chose an outline with no fill");

            int top = image.getRGB(page.x + page.width / 2, page.y) & 0xFFFFFF;
            int bottomEdge = image.getRGB(page.x + page.width / 2, page.y + page.height - 1) & 0xFFFFFF;

            assertTrue(top != none && bottomEdge != none, "the page pill has no outline along its top and bottom");

            assertEquals(image.getRGB(page.x + page.width / 2, page.y + 1) & 0xFFFFFF, none,
                "the page pill's outline is thicker than a thin line");

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
     * Java source with its comments stripped, so a check reads code and not the prose about it.
     */
    private String withoutComments(String body)
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
            else if (c == '/' && next == '/')
            {
                inLine = true;
            }
            else if (c == '/' && next == '*')
            {
                inBlock = true;
                i++;
            }
            else
            {
                out.append(c);
            }
        }

        return out.toString();
    }

    /**
     * A method, from its declaration to its closing brace.
     */
    private String bodyOf(String source, String declaration)
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
