package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RenameProposals;
import org.traincontrol.base.RenameProposals.Row;
import org.traincontrol.marklin.MarklinSimpleComponent;
import org.traincontrol.util.I18n;
import support.StandInStation;
import support.StationRig;

/**
 * What the Central Station differs on (FR-249), worked out by one model method from one read of its locomotives: one
 * row per locomotive here, with what Apply would do and why it may not, and an automatic sync that adds no copy of a
 * locomotive that looks renamed there and says so in the log.  Against a stand-in CS3 on loopback whose locomotive list
 * each claim writes, with no window.
 */
public class testSyncDifferences
{
    private static final String FUNCTION = "[{\"nr\":0,\"typ\":1,\"fs\":0,\"typ2\":1,\"dauer\":0,\"state\":0,\"isMoment\":false}]";

    /**
     * Each automatic sync leaves a locomotive renamed at the station alone: the station's name is not added beside it,
     * and one line per sync names both; the station's locomotives are read once a sync.  A pending rename onto a name
     * held at another decoder is the duplicate line alone.
     *
     * MUTATION: in `MarklinControlStation.syncThroughTheNewParser`, leave out `setHeldBackStationName`, or the log line.
     */
    @Test(timeOut = 120000)
    public void testAnAutomaticSyncAddsNoCopyOfAPendingRenameAndLogsIt() throws Exception
    {
        try (StationRig rig = rig(station("SD Y", 10, "SD Q", 11)))
        {
            here(rig, "SD X", 10, "SD P", 11, "SD Q", -20);
            String line = pending(rig, "SD Y", "SD X");

            for (int sync = 1; sync <= 2; sync++)
            {
                int reads = rig.station.requests("/app/api/locos");
                rig.takeLog();
                sync(rig);
                List<String> log = rig.takeLog();

                assertNull(rig.model.getLocByName("SD Y"), "sync " + sync + " added a copy of SD X under the station's name");
                assertEquals(rig.model.getLocByName("SD X").getAddress(), 10);
                assertEquals(Collections.frequency(log, line), 1, "sync " + sync + " did not log the hold-back once: " + log);
                assertFalse(log.contains(pending(rig, "SD Q", "SD P")), "a name held here was logged as held back");
                assertTrue(log.contains(I18n.f("loc.importSkippedDuplicateName", "SD Q")), "no duplicate line: " + log);
                assertEquals(rig.station.requests("/app/api/locos") - reads, 1, "the locomotives were not read once");
            }
        }
    }

    /**
     * Only a one-to-one rename is held back: two at the station's address, two here, or another decoder at the same
     * number are added as before, with no hold-back line.
     *
     * MUTATION: in `MarklinControlStation.differences`, leave out the `parsedCountByAddress` check.
     */
    @Test(timeOut = 120000)
    public void testOnlyAOneToOneRenameIsHeldBack() throws Exception
    {
        try (StationRig rig = rig(station("SD Y", 10, "SD Z", 10, "SD C", 12, "SD E", 13,
            loco("SD G", "mfx", 0x4000 + 14))))
        {
            synced(rig, "SD X", 10, "SD A", 12, "SD B", 12, "SD D", -13, "SD F", 14);
            List<String> log = rig.takeLog();

            for (String added : new String[] {"SD Y", "SD Z", "SD C", "SD E", "SD G"})
            {
                assertNotNull(rig.model.getLocByName(added), added + " was held back");
            }

            assertFalse(log.stream().anyMatch(l -> l.contains("SD X") || l.contains("SD A") || l.contains("SD D")
                || l.contains("SD F")), "a hold-back was logged: " + log);
        }
    }

    /**
     * A rename answered Keep, or a locomotive told to Ignore, is held back too, with no line: it was answered.
     *
     * MUTATION: in `MarklinControlStation.syncThroughTheNewParser`, leave out `!here.isSyncIgnored()`.
     */
    @Test(timeOut = 120000)
    public void testASettledRenameIsHeldBackWithoutALine() throws Exception
    {
        try (StationRig rig = rig(station("SD Y", 10, "SD V", 11)))
        {
            here(rig, "SD X", 10, "SD W", 11);
            rig.model.getLocByName("SD X").setSyncIgnored(true);
            rig.model.getLocByName("SD W").setDeclinedStationName("SD V");
            sync(rig);
            List<String> log = rig.takeLog();

            assertNull(rig.model.getLocByName("SD Y"), "the ignored locomotive's station name was added");
            assertNull(rig.model.getLocByName("SD V"), "the kept locomotive's station name was added");
            assertFalse(log.contains(pending(rig, "SD Y", "SD X")), "the ignored locomotive was logged: " + log);
            assertFalse(log.contains(pending(rig, "SD V", "SD W")), "the kept locomotive was logged: " + log);
        }
    }

    /**
     * Ignore is saved and survives a restart, and the restarted sync still holds the station's name back, worked out
     * again from its read; a file saved before the field reads as not ignored.
     *
     * MUTATION: in `MarklinControlStation.newLocomotive(MarklinSimpleComponent)`, leave out `setSyncIgnored`.
     */
    @Test(timeOut = 180000)
    public void testAnIgnoreSurvivesARestart() throws Exception
    {
        File root = Files.createTempDirectory("tc-sync-differences").toFile();
        AtomicReference<String> list = station("SD Y", 10);

        try (StationRig rig = rig(root, list))
        {
            here(rig, "SD X", 10, "SD N", 11);
            rig.model.getLocByName("SD X").setSyncIgnored(true);
            sync(rig);
            rig.model.saveState(false);
        }

        try (StationRig rig = rig(root, list))
        {
            assertNotNull(rig.model.getLocByName("SD X"), "precondition: the restart did not keep the database");
            assertTrue(rig.model.getLocByName("SD X").isSyncIgnored(), "Ignore did not survive the restart");
            assertFalse(rig.model.getLocByName("SD N").isSyncIgnored(), "a locomotive not ignored came back ignored");

            rig.takeLog();
            sync(rig);

            assertNull(rig.model.getLocByName("SD Y"), "the restarted sync added the ignored locomotive's station name");
            assertFalse(rig.takeLog().contains(pending(rig, "SD Y", "SD X")), "the ignored locomotive was logged");

            for (MarklinSimpleComponent c : rig.model.restoreState("test/LocDB3_0_0.data"))
            {
                assertFalse(c.isSyncIgnored(), "an older file's " + c.getName() + " read as ignored");
            }
        }
    }

    /**
     * Renamed and re-addressed at the station: one by-hand row with both, asking nothing and left to answer, which an
     * automatic sync leaves alone, holding the station's name back and logging the other address.
     *
     * MUTATION: in `MarklinControlStation.differences`, build a row for the rename alone.
     */
    @Test(timeOut = 120000)
    public void testARenamedAndReAddressedLocomotiveIsOneRowLeftAloneAutomatically() throws Exception
    {
        try (StationRig rig = rig(station("SD Y", 5, "SD X", 7)))
        {
            RenameProposals d = synced(rig, "SD X", 5);
            List<String> log = rig.takeLog();
            Locomotive x = rig.model.getLocByName("SD X");

            assertNull(rig.model.getLocByName("SD Y"), "the station's name was added beside it");
            assertEquals(x.getAddress(), 5, "an automatic sync took the station's address");
            assertTrue(log.contains(pending(rig, "SD Y", "SD X")), "the hold-back was not logged: " + log);
            assertTrue(log.contains(I18n.f("loc.addressDiffersAtStation", "SD X", 7, 5)), "no address line: " + log);

            Row row = row(rig, d, "SD X");

            assertEquals(d.getRows(), Collections.singletonList(row), "not one row");
            assertEquals(Arrays.asList(row.stationName, row.stationAddress, row.asksName(), row.asksAddress()),
                Arrays.asList("SD Y", 7, false, false));
        }
    }

    /**
     * A Central Station multi-unit naming a member whose rename is pending lists the locomotive here, also after a sync
     * whose locomotive list did not read: a partial read can set the mark but never clear it.
     *
     * MUTATION: in `Locomotive.keepsItsNameOver`, leave out `heldBackStationName`; or in
     * `MarklinControlStation.syncThroughTheNewParser`, clear the marks after a partial read too.
     */
    @Test(timeOut = 120000)
    public void testAStationMultiUnitFindsAMemberWhoseRenameIsPending() throws Exception
    {
        String unit = "{\"name\":\"SD MU\",\"internname\":\"SD MU\",\"uid\":\"0x2c01\",\"dectyp\":\"mm2_prg\","
            + "\"traktion\":{\"0\":{\"lokname\":\"SD Y\"}},\"funktionen\":" + FUNCTION + "}";
        AtomicReference<String> list = station("SD Y", 10, unit);

        try (StationRig rig = rig(list))
        {
            synced(rig, "SD X", 10);

            assertNull(rig.model.getLocByName("SD Y"), "the member's station name was added");
            assertNotNull(rig.model.getLocByName("SD MU"), "precondition: the multi-unit was not added");
            assertEquals(rig.model.getLocByName("SD MU").getModelMultiUnitLocomotives(), Collections.singletonList(
                rig.model.getLocByName("SD X")), "the multi-unit does not list the member whose rename is pending");

            list.set("{}");
            sync(rig);

            assertTrue(rig.model.wasLastSyncPartial(), "precondition: the locomotive list read");
            assertEquals(rig.model.getLocByName("SD MU").getModelMultiUnitLocomotives(), Collections.singletonList(
                rig.model.getLocByName("SD X")), "a sync whose list did not read dropped the member whose rename is pending");
        }
    }

    /**
     * The rows: one per locomotive here that differs, by name with ignored ones last; a swap's rows say to rename one by
     * hand; two here at one address are no row.  An empty station list has none.
     *
     * MUTATION: in `RenameProposals`, sort without `isSyncIgnored`.
     */
    @Test(timeOut = 120000)
    public void testTheRowsAndWhatTheyCount() throws Exception
    {
        AtomicReference<String> list = station("SD B2", 21, "SD A2", 22, "SD D", 23, "SD C", 24,
            "SD E3", 25);

        try (StationRig rig = rig(list))
        {
            here(rig, "SD B", 21, "SD A", 22, "SD C", 23, "SD D", 24, "SD E1", 25, "SD E2", 25);
            rig.model.getLocByName("SD A").setSyncIgnored(true);
            sync(rig);
            RenameProposals d = rig.model.getRenameProposals(true);

            assertEquals(d.getRows().stream().map(r -> r.loco.getName()).collect(Collectors.toList()),
                Arrays.asList("SD B", "SD C", "SD D", "SD A"), "the rows or their order");
            assertEquals(row(rig, d, "SD C").whyNot(true), I18n.f("sync.ui.whyCycle", "SD C", "SD D"),
                "a swap's row does not say to rename one by hand");
            assertNull(row(rig, d, "SD B").whyNot(true));

            list.set("[]");
            d = rig.model.getRenameProposals(true);

            assertTrue(d.getRows().isEmpty(), "an empty station list has rows");
        }
    }

    /**
     * Whether Apply puts two locomotives here on one decoder address is judged on the end result of every address
     * applied: a swap or a chain is no collision in either order, one half of it is, and another decoder type at the
     * same number is none.
     *
     * MUTATION: in `RenameProposals.collisions`, leave out the `-1` for the address each row leaves.
     */
    @Test(timeOut = 120000)
    public void testACollisionIsJudgedOnTheFinalState() throws Exception
    {
        try (StationRig rig = rig(station("SD A", 32, "SD B", 31, "SD C", 34, "SD D", 35,
            "SD G", 38)))
        {
            RenameProposals d = synced(rig, "SD A", 31, "SD B", 32, "SD C", 33, "SD D", 34, "SD G", 37, "SD H", -38);
            Row a = row(rig, d, "SD A"), b = row(rig, d, "SD B"), c = row(rig, d, "SD C"), dd = row(rig, d, "SD D");

            assertTrue(d.collisions(Arrays.asList(a, b)).isEmpty(), "a swap collides");
            assertEquals(d.collisions(Collections.singletonList(a)), Collections.singleton(a), "half a swap does not");
            assertTrue(d.collisions(Arrays.asList(c, dd)).isEmpty(), "a chain collides");
            assertTrue(d.collisions(Arrays.asList(dd, c)).isEmpty(), "a chain collides in the other order");
            assertEquals(d.collisions(Collections.singletonList(c)), Collections.singleton(c), "half a chain does not");
            assertTrue(d.collisions(Collections.singletonList(row(rig, d, "SD G"))).isEmpty(),
                "MM2 38 collides with DCC 38");
        }
    }

    /**
     * A rename onto a name held here says which locomotive it deletes, unless the rows renamed with it move that one
     * away first; a pair whose other name the station dropped deletes the holder.
     *
     * MUTATION: in `RenameProposals.deletes`, leave out the loop over the rows renamed with it.
     */
    @Test(timeOut = 120000)
    public void testADeletingRenameNamesWhatItDeletes() throws Exception
    {
        try (StationRig rig = rig(station("SD C", 41, "SD D", 42, "SD Q", 43)))
        {
            RenameProposals d = synced(rig, "SD X", 41, "SD C", 42, "SD P", 43, "SD Q", 43);
            Row x = row(rig, d, "SD X"), cd = row(rig, d, "SD C");

            assertEquals(d.deletes(x, Collections.<Row>emptyList()), rig.model.getLocByName("SD C"),
                "the chain's rename alone does not delete SD C");
            assertNull(d.deletes(x, Collections.singletonList(cd)), "renamed with SD C's rename, it still deletes");
            assertNull(d.deletes(cd, Collections.<Row>emptyList()));
            assertEquals(d.deletes(row(rig, d, "SD P"), Collections.<Row>emptyList()), rig.model.getLocByName("SD Q"),
                "the pair's rename does not delete the holder");
        }
    }

    /**
     * Two locomotives here that the station renamed to one name it lists twice are neither offered: the second rename
     * would delete the first.  Both rows say why, and the sync adds the name at neither address.
     *
     * MUTATION: in `MarklinControlStation.differences`, leave out `loc.renameNameListedTwice`.
     */
    @Test(timeOut = 120000)
    public void testTwoRowsOntoOneStationNameCannotBothApply() throws Exception
    {
        try (StationRig rig = rig(station("SD Y", 51, "SD Y", 52)))
        {
            RenameProposals d = synced(rig, "SD X", 51, "SD W", 52);

            assertEquals(row(rig, d, "SD X").whyNot(true), I18n.f("sync.ui.whyListedTwice", "SD X", "SD Y"));
            assertEquals(row(rig, d, "SD W").whyNot(true), I18n.f("sync.ui.whyListedTwice", "SD W", "SD Y"));
            assertNull(rig.model.getLocByName("SD Y"), "the sync added the name");
            assertTrue(rig.model.getLocomotivesToRenameFromImport().isEmpty(), "a rename onto the name listed twice was"
                + " offered");
        }
    }

    /**
     * A name the station lists twice is offered no address: one entry at this address, two elsewhere, or the same
     * number under another decoder type.
     *
     * MUTATION: in `MarklinControlStation.differences`, leave out `stationNames.get(l.getName()) == 1`.
     */
    @Test(timeOut = 120000)
    public void testANameListedTwiceIsNotOfferedTheOtherAddress() throws Exception
    {
        try (StationRig rig = rig(station("SD X", 61, "SD X", 62, "SD Z", 64, "SD Z", 65,
            "SD V", 66, loco("SD V", "dcc", 0xc000 + 66))))
        {
            RenameProposals d = synced(rig, "SD X", 61, "SD Z", 63, "SD V", 66);

            assertTrue(d.getAddresses().isEmpty(), "an address was offered: " + d.getAddresses());
            assertTrue(d.getRows().isEmpty(), "a row was made");
        }
    }

    /**
     * After a partial read, a rename that would delete is not offered, as the name held may sit in a record that would
     * not read; its row says why.  One that deletes nothing is offered, and the sync holds back what it read.
     *
     * MUTATION: in `MarklinControlStation.differences`, leave out `loc.renameAfterPartialRead`.
     */
    @Test(timeOut = 120000)
    public void testAPartialListOffersNoDeletingRename() throws Exception
    {
        String broken = "{\"name\":\"SD broken\",\"internname\":\"SD broken\",\"uid\":\"0x49\",\"dectyp\":\"mm2_prg\","
            + "\"funktionen\":[1]}";

        try (StationRig rig = rig(station("SD Q", 71, "SD S", 72, broken)))
        {
            RenameProposals d = synced(rig, "SD P", 71, "SD Q", 71, "SD R", 72);

            assertTrue(rig.model.wasLastSyncPartial(), "precondition: the read was not partial");
            assertNull(rig.model.getLocByName("SD S"), "the partial sync added a pending rename's copy");
            assertTrue(rig.takeLog().contains(pending(rig, "SD S", "SD R")), "the partial sync did not log the hold-back");
            assertEquals(row(rig, d, "SD P").whyNot(true), I18n.f("sync.ui.whyPartialRead", "SD P", "SD Q"));
            assertEquals(d.getProposals().stream().map(p -> p[0] + ">" + p[1]).collect(Collectors.toList()),
                Collections.singletonList("SD R>SD S"), "the renames offered");
        }
    }

    /**
     * Apply is unavailable, with the reason, for a locomotive with a speed (its rename and its address) and for a
     * station name no route condition could hold.
     *
     * MUTATION: in `MarklinControlStation.whyLeftAsItIs`, leave out the `hasSpeed` clause.
     */
    @Test(timeOut = 120000)
    public void testAMovingLocomotiveGraysApplyWithItsReason() throws Exception
    {
        try (StationRig rig = rig(station("SD Y", 81, "SD U,2", 82, "SD M", 84)))
        {
            synced(rig, "SD X", 81, "SD U", 82, "SD M", 83);
            Locomotive x = rig.model.getLocByName("SD X"), m = rig.model.getLocByName("SD M");

            try
            {
                x.setSpeed(40);
                m.setSpeed(40);
                RenameProposals d = rig.model.getRenameProposals(true);

                assertEquals(row(rig, d, "SD X").whyNot(true), I18n.f("loc.syncLeftMoving", "SD X"));
                assertEquals(row(rig, d, "SD M").whyNot(false), I18n.f("loc.syncLeftMoving", "SD M"));
                assertEquals(row(rig, d, "SD U").whyNot(true), I18n.t("sync.ui.whyNameUnusable"));
            }
            finally
            {
                x.setSpeed(0);
                m.setSpeed(0);
            }
        }
    }

    /**
     * Set all to Apply picks only what needs no human pick, judged on all its picks together: a free address, a free
     * rename; not a collision (with a locomotive here or between two picks), a deleting rename, a grayed Apply, a
     * row differing in both, or an ignored row (E80, E81).
     *
     * MUTATION: in `RenameProposals.applicable`, leave out the `collisions` or the `deletes` pass, or `asksName() !=
     * asksAddress()`.
     */
    @Test(timeOut = 120000)
    public void testSetAllApplySkipsWhatNeedsAHumanPick() throws Exception
    {
        try (StationRig rig = rig(station("SD A", 62, "SD T", 65, "SD C2", 66, "SD Held", 67, "SD E, 1", 68,
            "SD O", 69, "SD Both", 70, "SD I2", 71, "SD P", 74, "SD Q", 74)))
        {
            here(rig, "SD I", 71);
            rig.model.getLocByName("SD I").setSyncIgnored(true);
            RenameProposals d = synced(rig, "SD A", 61, "SD T", 64, "SD H", 65, "SD C", 66, "SD D", 67,
                "SD Held", 67, "SD E", 68, "SD Both", 69, "SD P", 72, "SD Q", 73);

            assertEquals(d.applicable(d.getRows(), Collections.emptyList()).stream().map(r -> r.loco.getName()).sorted()
                .collect(Collectors.toList()), Arrays.asList("SD A", "SD C"), "what Set all applies");
        }
    }

    /** The row of the locomotive here by this name, which must differ. */
    private static Row row(StationRig rig, RenameProposals d, String name)
    {
        Locomotive l = rig.model.getLocByName(name);
        Row r = d.getRows().stream().filter(each -> each.loco == l).findFirst().orElse(null);
        assertNotNull(r, "no row for " + name);
        return r;
    }

    /** The automatic sync's line for the station's `station`, held back as `here`'s pending rename. */
    private static String pending(StationRig rig, String station, String here)
    {
        Locomotive l = rig.model.getLocByName(here);
        return I18n.f("loc.renamePendingNotAdded", station, here, l.getDecoderTypeLabel(), l.getAddress());
    }

    private static void sync(StationRig rig)
    {
        assertTrue(rig.model.syncWithCS2() >= 0, "the sync failed: " + rig.model.getLastSyncError());
    }

    /** Adds locomotives here, by name and address, MM2 or, for a negative address, DCC. */
    private static void here(StationRig rig, Object... namesAndAddresses)
    {
        for (int i = 0; i < namesAndAddresses.length; i += 2)
        {
            String name = (String) namesAndAddresses[i];
            int address = (Integer) namesAndAddresses[i + 1];

            if (address < 0) rig.model.newDCCLocomotive(name, -address);
            else rig.model.newMM2Locomotive(name, address);
        }
    }

    /** Adds these locomotives here, syncs, and reads what the station differs on as a pressed Sync does. */
    private static RenameProposals synced(StationRig rig, Object... namesAndAddresses) throws Exception
    {
        here(rig, namesAndAddresses);
        sync(rig);
        return rig.model.getRenameProposals(true);
    }

    /** The station's list: names each followed by an MM2 address, or CS3 records as written. */
    private static AtomicReference<String> station(Object... entries)
    {
        List<String> records = new ArrayList<>();

        for (int i = 0; i < entries.length; i++)
        {
            String s = (String) entries[i];
            records.add(s.startsWith("{") ? s : loco(s, "mm2_prg", (Integer) entries[++i]));
        }

        return new AtomicReference<>("[" + String.join(",", records) + "]");
    }

    /** One CS3 locomotive record; `uid` is the decoder type's base plus the address, as the station writes it. */
    private static String loco(String name, String type, int uid)
    {
        return "{\"name\":\"" + name + "\",\"internname\":\"" + name + "\",\"uid\":\"0x" + Integer.toHexString(uid)
            + "\",\"dectyp\":\"" + type + "\",\"funktionen\":" + FUNCTION + "}";
    }

    private static StationRig rig(AtomicReference<String> list) throws Exception
    {
        return rig(Files.createTempDirectory("tc-sync-differences").toFile(), list);
    }

    /** A model over this folder's data syncing with a CS3 whose locomotive list is `list` as it is when asked. */
    private static StationRig rig(File root, AtomicReference<String> list) throws Exception
    {
        BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", null);

        return new StationRig(root, (path, n) -> "/app/api/locos".equals(path)
            ? list.get().getBytes(StandardCharsets.UTF_8) : cs3.apply(path, n));
    }
}
