package ui;

import java.util.Arrays;
import javax.swing.JComboBox;
import javax.swing.text.JTextComponent;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import support.Components;
import support.SettingsWindow;

/** The Railway Name box on the Notes tab of the Locomotive Settings window (FR-180). */
public class testTheLocomotiveSettingsRailwayBox extends support.SettingsClass
{
    private static final String NAME = "RB train";
    private static final String[] MADE = { NAME, "RB one", "RB two", "RB three", "RB four", "RB five" };

    @Override
    protected String[] made()
    {
        return MADE;
    }

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        SettingsWindow.closeIfOpen();
        SettingsWindow.power(ui, fixture.model, false);
        fixture.quiet();
    }

    /** Lays the other locomotives with these railway names, then opens the Notes tab of a train with this railway. */
    private Locomotive open(String railway, String... others) throws Exception
    {
        fixture.quiet();
        fixture.forget(MADE);

        for (int i = 0; i < others.length; i++)
        {
            fixture.model.newMFXLocomotive(MADE[i + 1], 3900 + i).setStructuredNotes(0, 0, others[i], "");
        }

        Locomotive loc = fixture.layDcc(NAME, 3899, LocomotiveSettingsFrame.Tab.LOCOMOTIVE);

        loc.setStructuredNotes(0, 0, railway, "");

        SettingsWindow.closeIfOpen();
        fixture.show(loc, LocomotiveSettingsFrame.Tab.NOTES);

        return loc;
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<String> box()
    {
        JComboBox<String> box = SettingsWindow.part("notes.railway", JComboBox.class);

        assertNotNull(box, "the Notes tab has no Railway Name box");

        return box;
    }

    private static java.util.List<String> listed()
    {
        return Components.onTheEventThread(() ->
        {
            java.util.List<String> out = new java.util.ArrayList<>();
            JComboBox<String> box = box();

            for (int i = 0; i < box.getItemCount(); i++) out.add(box.getItemAt(i));

            return out;
        });
    }

    private static String shown()
    {
        return Components.onTheEventThread(() -> ((JTextComponent) box().getEditor().getEditorComponent()).getText());
    }

    private static boolean saveLit()
    {
        return Components.onTheEventThread(SettingsWindow.save()::isEnabled);
    }

    private static void save()
    {
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, 8000), "Save did not close the window");
    }

    /**
     * The list holds each railway name in use once, sorted without regard to case, names differing only in case folded
     * to the first spelling found, blanks left out; the box shows this train's own name.
     *
     * MUTATION: drop the case folding or the sort in `LocomotiveNotesPanel.railwayNames`, or leave the box a plain
     * text field, and this fails.
     */
    @Test
    public void testTheListHoldsEachNameOnceSorted() throws Exception
    {
        open("RB Zeta", "RB beta", "RB Alpha", "  ", "RB Beta", "RB Zeta ");

        java.util.List<String> all = listed();
        java.util.List<String> mine = new java.util.ArrayList<>();

        for (String name : all) if (name.startsWith("RB ")) mine.add(name);

        assertEquals(mine, Arrays.asList("RB Alpha", "RB beta", "RB Zeta"));
        assertFalse(all.contains(""), "a blank name is listed");

        java.util.List<String> sorted = new java.util.ArrayList<>(all);
        java.util.Set<String> folded = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        folded.addAll(all);

        assertEquals(all, sorted, "the list is not sorted");
        assertEquals(folded.size(), all.size(), "a name is listed twice");
        assertEquals(shown(), "RB Zeta", "the box does not show the train's name");
    }

    /** A train with no railway name shows a blank box, and Save stays gray. */
    @Test
    public void testNoNameShowsBlank() throws Exception
    {
        open("", "Alpha");

        assertEquals(shown(), "", "the box is not blank");
        assertFalse(saveLit(), "Save is lit with nothing changed");
    }

    /**
     * Picking a name from the list, then Save, stores it.
     *
     * MUTATION: have `LocomotiveNotesPanel.apply` ignore the box, and this fails.
     */
    @Test
    public void testPickingANameThenSaveStoresIt() throws Exception
    {
        Locomotive loc = open("", "Alpha", "Beta");

        Components.select(box(), "Beta");

        assertTrue(saveLit(), "picking did not light Save");

        save();
        assertEquals(loc.getStructuredNotes().getRailway(), "Beta");
    }

    /**
     * Typing a new name, then Save, stores it trimmed; typing marks the window dirty, and typing the value back does
     * not.
     *
     * MUTATION: leave the box's editor out of the dirty check in `LocomotiveNotesPanel.isDirty`, or save the untrimmed
     * text, and this fails.
     */
    @Test
    public void testTypingANewNameThenSaveStoresIt() throws Exception
    {
        Locomotive loc = open("Alpha", "Alpha");

        box();

        assertFalse(saveLit(), "Save is lit with nothing changed");

        SettingsWindow.type("notes.railway", "Brand New");

        assertTrue(saveLit(), "typing did not light Save");

        SettingsWindow.type("notes.railway", "Alpha");

        assertFalse(saveLit(), "an unchanged value lit Save");

        SettingsWindow.type("notes.railway", "  Brand New ");
        save();
        assertEquals(loc.getStructuredNotes().getRailway(), "Brand New");
    }

    /**
     * Locked while the train moves, the box keeps what is picked and Save stays gray, as for the other Notes fields.
     *
     * MUTATION: drop `!blocked` from `LocomotiveSettingsFrame.changed`, and this fails.
     */
    @Test
    public void testLockedLikeTheOtherFields() throws Exception
    {
        Locomotive loc = open("Alpha", "Alpha", "Beta");

        SettingsWindow.power(ui, fixture.model, true);
        loc.setSpeed(40);

        Components.onTheEventThread(() ->
        {
            ui.repaintLoc(true, null);

            return null;
        });

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(
            () -> SettingsWindow.part("settings.strip", javax.swing.JLabel.class).isVisible()), 8000), "the window did not lock");

        Components.select(box(), "Beta");

        assertEquals(shown(), "Beta", "the picking was lost while locked");
        assertFalse(saveLit(), "Save is live while locked");

        loc.setSpeed(0);
    }
}
