package org.traincontrol.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RenameProposals;
import org.traincontrol.base.RenameProposals.Row;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import support.Components;
import static support.Components.onTheEventThread;

/**
 * `SyncDifferencesWindow` on its own (FR-249): the rows, the choices each offers and grays, the headings, the Deletes
 * note and the answers, built by `over` and never shown.  `ui.testTheSyncDifferencesWindow` and
 * `ui.testTheSyncAsksAboutRenamedLocomotives` are its integration tests, through the real Sync.
 */
public class testSyncDifferencesWindow
{
    /**
     * A row for each locomotive with something to answer: a name, an address, an ignored one, and one that differs
     * both ways, shown by hand with no choices; none for a name already kept, only `only`'s when given, and no table
     * with nothing to ask.
     *
     * MUTATION: in `SyncDifferencesWindow.over`, leave out `|| r.byHand`, and Twice has no row.
     */
    @Test
    public void testARowForEachLocomotiveWithSomethingToAnswer()
    {
        List<String> said = onTheTable(null, w ->
        {
            JLabel byHand = Components.named(w.dialog.getContentPane(), "Twice:byHand", JLabel.class);
            return Arrays.asList(rows(w).toString(), byHand == null ? "no by-hand row" : byHand.getToolTipText());
        });

        assertEquals(said, Arrays.asList("[D, Ign, Locked, Shunter, Twice, Uri]", I18n.t("sync.ui.byHandTip")));
        assertEquals(onTheTable("Uri", testSyncDifferencesWindow::rows), Arrays.asList("Uri"), "only Uri's row");
        assertNull(onTheEventThread(() -> SyncDifferencesWindow.over(null, proposals("Kept"), null)),
            "a table for a kept name alone");
    }

    /**
     * A name row offers Keep As Is, Rename, Keep Both, Ignore and Ask Again; an address row Change Address in place of
     * Rename; each starts on Ask Again, an ignored locomotive's on Ignore.
     *
     * MUTATION: in `SyncDifferencesWindow.addRow`, start every row on Ask Again, and Ign starts there too.
     */
    @Test
    public void testEachRowOffersItsChoicesAndStartsOnAskAgain()
    {
        List<String> said = onTheTable(null, w ->
        {
            List<String> offered = new ArrayList<>(), picked = new ArrayList<>();
            for (JRadioButton b : Components.all(w.dialog.getContentPane(), JRadioButton.class, null))
            {
                offered.add(b.getName());
                if (b.isSelected()) picked.add(b.getName());
            }
            return Arrays.asList(offered.toString(), picked.toString());
        });

        assertEquals(said.get(0), Arrays.asList("D:KEEP", "D:RENAME", "D:BOTH", "D:IGNORE", "D:ASK", "Locked:KEEP",
            "Locked:RENAME", "Locked:BOTH", "Locked:IGNORE", "Locked:ASK", "Shunter:KEEP", "Shunter:RENAME",
            "Shunter:BOTH", "Shunter:IGNORE", "Shunter:ASK", "Uri:KEEP", "Uri:ADDRESS", "Uri:BOTH", "Uri:IGNORE",
            "Uri:ASK", "Ign:KEEP", "Ign:RENAME", "Ign:BOTH", "Ign:IGNORE", "Ign:ASK").toString(), "the choices");
        assertEquals(said.get(1), "[D:ASK, Locked:ASK, Shunter:ASK, Uri:ASK, Ign:IGNORE]", "where each row starts");
    }

    /**
     * Keep Both is lit only for a locomotive the station has under another name that no locomotive here holds; grayed,
     * its hover says why.
     *
     * MUTATION: in `SyncDifferencesWindow.addRow`, give Keep Both no reason (null), and it is lit on every row.
     */
    @Test
    public void testKeepBothIsOnlyForANameThatIsFreeHere()
    {
        List<String> said = onTheTable(null, w ->
        {
            List<String> out = new ArrayList<>();
            for (String row : new String[] {"Shunter", "Uri", "D"})
            {
                JRadioButton both = radio(w, row + ":BOTH");
                out.add(both.isEnabled() + " " + both.getToolTipText());
            }
            return out;
        });

        assertEquals(said, Arrays.asList("true " + I18n.f("sync.ui.keepBothSaid", "Shunter", "V 60"), "false "
            + I18n.t("sync.ui.bothOnlyRenamed"), "false " + I18n.f("sync.ui.bothNameTaken", "Held")),
            "a free name, an address, a name held here");
    }

    /**
     * A rename Apply may not take is grayed, its hover saying why, and its line says "(can't apply)" with the same
     * reason; one Apply may take is lit.
     *
     * MUTATION: in `SyncDifferencesWindow.addRow`, pass the rename choice no reason (null), and Locked's is lit.
     */
    @Test
    public void testARenameThatCannotApplyIsGrayedWithItsReason()
    {
        List<String> said = onTheTable(null, w ->
        {
            JRadioButton locked = radio(w, "Locked:RENAME"), shunter = radio(w, "Shunter:RENAME");
            List<String> cant = new ArrayList<>();
            for (JLabel l : Components.all(w.dialog.getContentPane(), JLabel.class, l -> I18n.t("sync.ui.cantApply")
                .equals(l.getText()))) cant.add(l.getToolTipText());
            return Arrays.asList(locked.isEnabled() + " " + locked.getToolTipText(), cant.toString(),
                String.valueOf(shunter.isEnabled()));
        });

        assertEquals(said, Arrays.asList("false " + LOCKED, "[" + LOCKED + "]", "true"));
    }

    /**
     * A heading sets every row it can to its answer: Central Station only the rows Apply may take, none ignored, and
     * Keep Both only where it is lit.
     *
     * MUTATION: in `SyncDifferencesWindow.setAllTo`, leave out `c.isEnabled()`, and Keep Both's heading picks a grayed
     * Keep Both.
     */
    @Test
    public void testAHeadingSetsEveryRowItCan()
    {
        List<String> said = onTheTable(null, w ->
        {
            List<String> out = new ArrayList<>();
            for (String key : new String[] {"sync.ui.centralStation", "sync.ui.thisComputer", "sync.ui.keepBoth"})
            {
                Components.withText(w.dialog.getContentPane(), I18n.t(key)).doClick();
                out.add(picked(w));
            }
            return out;
        });

        assertEquals(said, Arrays.asList("[D:ASK, Locked:ASK, Shunter:RENAME, Uri:ADDRESS, Ign:IGNORE]",
            "[D:KEEP, Locked:KEEP, Shunter:KEEP, Uri:KEEP, Ign:IGNORE]",
            "[D:KEEP, Locked:BOTH, Shunter:BOTH, Uri:KEEP, Ign:IGNORE]"), "Central Station, This Computer, Keep Both");
    }

    /**
     * A rename onto a name held here says "Deletes" that locomotive only while its Apply is picked.
     *
     * MUTATION: in `SyncDifferencesWindow.refresh`, leave out `&& renamed.contains(d.getKey())`, and the note shows
     * from the start.
     */
    @Test
    public void testTheDeletesNoteShowsOnlyWhileApplyIsPicked()
    {
        List<String> said = onTheTable(null, w ->
        {
            JLabel note = Components.named(w.dialog.getContentPane(), "D:deletes", JLabel.class);
            List<String> out = new ArrayList<>(Collections.singletonList(String.valueOf(note.isVisible())));
            for (String pick : new String[] {"D:RENAME", "D:KEEP"})
            {
                radio(w, pick).doClick();
                out.add(note.isVisible() + (note.isVisible() ? " " + note.getText() : ""));
            }
            return out;
        });

        assertEquals(said, Arrays.asList("false", "true " + I18n.f("sync.ui.deletes", "Held", "DCC 37"), "false"),
            "at the start, on Apply, back on Keep As Is");
    }

    /**
     * The answers are each answerable row's pick, top to bottom, once Continue is pressed; none before, or on Cancel.
     *
     * MUTATION: in `SyncDifferencesWindow.answers`, return `picks()` whether or not Continue was pressed, and a
     * canceled table answers.
     */
    @Test
    public void testTheAnswersAreEachRowsPickOnContinue()
    {
        List<String> said = onTheTable(null, w ->
        {
            radio(w, "Shunter:RENAME").doClick();
            radio(w, "Uri:ADDRESS").doClick();
            String before = String.valueOf(w.answers());
            Components.withText(w.dialog.getContentPane(), I18n.t("sync.ui.continue")).doClick();
            return Arrays.asList(before, answers(w.answers()));
        });
        String canceled = onTheTable(null, w ->
        {
            Components.withText(w.dialog.getContentPane(), I18n.t("ui.cancel")).doClick();
            return String.valueOf(w.answers());
        });

        assertEquals(said, Arrays.asList("null", "[D=ASK, Locked=ASK, Shunter=RENAME, Uri=ADDRESS, Ign=IGNORE]"));
        assertEquals(canceled, "null", "canceled");
    }

    private static final String LOCKED = "Locked's new name is taken on the Central Station";

    /**
     * The station renames Shunter, D onto Held (a name held here), Locked (refused) and ignored Ign, moves Uri, and
     * differs from Twice both ways; Kept's station name was kept.  Rows only for the names given, when any.
     */
    private static RenameProposals proposals(String... only)
    {
        Map<String, MarklinLocomotive> here = new HashMap<>();
        for (Object[] l : new Object[][] {{"Shunter", 3}, {"Uri", 21}, {"D", 36}, {"Held", 37}, {"Locked", 40},
            {"Twice", 50}, {"Ign", 60}, {"Kept", 70}})
            here.put((String) l[0], new MarklinLocomotive(null, (Integer) l[1], Locomotive.decoderType.DCC,
                (String) l[0]));
        here.get("Ign").setSyncIgnored(true);
        here.get("Kept").setDeclinedStationName("K2");
        Map<Integer, List<MarklinLocomotive>> held = new HashMap<>();
        for (MarklinLocomotive l : here.values()) held.put(l.getIntUID(), new ArrayList<>(Arrays.asList(l)));
        int at22 = new MarklinLocomotive(null, 22, Locomotive.decoderType.DCC, "x").getIntUID();
        List<Row> rows = new ArrayList<>();
        for (Row r : Arrays.asList(row(here.get("Shunter"), "V 60", null, null, null, 0),
            row(here.get("Uri"), null, 22, null, null, at22),
            row(here.get("D"), "Held", null, null, here.get("Held"), 0),
            row(here.get("Locked"), "L2", null, LOCKED, null, 0), row(here.get("Twice"), "T2", 51, null, null, 0),
            row(here.get("Ign"), "I2", null, null, null, 0), row(here.get("Kept"), "K2", null, null, null, 0)))
            if (only.length == 0 || Arrays.asList(only).contains(r.loco.getName())) rows.add(r);
        return new RenameProposals(new ArrayList<>(), 0, rows, held, false);
    }

    /** A row for l: the station's name and address (null where they agree), why no rename, who holds the name. */
    private static Row row(MarklinLocomotive l, String name, Integer address, String whyNoRename, Locomotive holder,
        int stationUid)
    {
        return new Row(l, name, address, whyNoRename, null, holder, null, l.getIntUID(), address == null
            ? l.getIntUID() : stationUid);
    }

    /** What `ask` would show for these proposals (only the locomotive so named, unless null), read and then closed. */
    private static <T> T onTheTable(String only, Function<SyncDifferencesWindow, T> read)
    {
        return onTheEventThread(() ->
        {
            RenameProposals p = proposals();
            Locomotive mine = only == null ? null : p.getRows().stream().filter(r -> r.loco.getName().equals(only))
                .findFirst().get().loco;
            SyncDifferencesWindow w = SyncDifferencesWindow.over(null, p, mine);
            try
            {
                return read.apply(w);
            }
            finally
            {
                w.dialog.dispose();
            }
        });
    }

    /** The rows that have choices or a by-hand line, top to bottom. */
    private static List<String> rows(SyncDifferencesWindow w)
    {
        List<String> out = new ArrayList<>();
        for (JComponent c : Components.all(w.dialog.getContentPane(), JComponent.class, c -> c.getName() != null
            && (c.getName().endsWith(":KEEP") || c.getName().endsWith(":byHand"))))
            out.add(c.getName().replaceAll(":.*", ""));
        Collections.sort(out);
        return out;
    }

    private static JRadioButton radio(SyncDifferencesWindow w, String name)
    {
        return Components.named(w.dialog.getContentPane(), name, JRadioButton.class);
    }

    /** The radios selected now, top to bottom. */
    private static String picked(SyncDifferencesWindow w)
    {
        List<String> out = new ArrayList<>();
        for (JRadioButton b : Components.all(w.dialog.getContentPane(), JRadioButton.class, b -> b.isSelected()))
            out.add(b.getName());
        return out.toString();
    }

    private static String answers(Map<Row, SyncDifferencesWindow.Answer> answers)
    {
        List<String> out = new ArrayList<>();
        answers.forEach((r, a) -> out.add(r.loco.getName() + "=" + a));
        return out.toString();
    }
}
