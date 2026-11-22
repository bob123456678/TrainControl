package org.traincontrol.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one read of the Central Station's locomotives differs on here (FR-249): the renames that can be applied, whether
 * anything was declined, and one row per locomotive here that differs, with what every door that answers them needs.
 *
 * Both of the first two are needed because an empty proposal list means either that the Central Station and the local
 * database agree or that every rename it wanted was refused, and the user has to be told which.
 *
 * @author Adam
 */
public class RenameProposals
{
    private final List<String[]> proposals;
    private final int refused;
    private final Map<String, Integer> addresses;
    private final List<Row> rows;
    private final Map<Integer, Integer> held;

    /** One locomotive here the station differs from: by name at its decoder address, by address under its name, or both. */
    public static final class Row
    {
        public final Locomotive loco;
        /** The station's name for it and the station's address of its decoder type, each null where they agree. */
        public final String stationName;
        public final Integer stationAddress;
        private final String whyNoRename;
        private final String whyNoAddress;
        /** The other locomotive here under stationName, which the rename would delete; else null. */
        public final Locomotive nameHolder;
        /** The station's locomotive under stationName, which Keep Both adds; else null. */
        public final Locomotive station;
        // Decoder type and address, as MarklinLocomotive.getIntUID, now and at stationAddress
        final int uid;
        final int stationUid;

        public Row(Locomotive loco, String stationName, Integer stationAddress, String whyNoRename, String whyNoAddress,
            Locomotive nameHolder, Locomotive station, int uid, int stationUid)
        {
            this.loco = loco;
            this.stationName = stationName;
            this.stationAddress = stationAddress;
            this.whyNoRename = whyNoRename;
            this.whyNoAddress = whyNoAddress;
            this.nameHolder = nameHolder;
            this.station = station;
            this.uid = uid;
            this.stationUid = stationUid;
        }

        /** Why Apply may not rename it (`rename`), or change its address, now; null where it may. */
        public String whyNot(boolean rename)
        {
            return rename ? this.whyNoRename : this.whyNoAddress;
        }

        public boolean isNameKept()
        {
            return this.stationName != null && this.stationName.equals(this.loco.getDeclinedStationName());
        }

        public boolean isAddressKept()
        {
            return this.stationAddress != null && this.stationAddress.equals(this.loco.getDeclinedStationAddress());
        }

        /** Whether nothing is left to answer: ignored, or each difference kept. */
        public boolean isSettled()
        {
            return this.loco.isSyncIgnored() || !asksName() && !asksAddress();
        }

        /** Whether the table asks about the name: it differs and is not kept, or the locomotive is ignored. */
        public boolean asksName()
        {
            return this.stationName != null && (this.loco.isSyncIgnored() || !isNameKept());
        }

        public boolean asksAddress()
        {
            return this.stationAddress != null && (this.loco.isSyncIgnored() || !isAddressKept());
        }
    }

    /** @param held how many locomotives here are at each decoder address (as getIntUID) */
    public RenameProposals(List<String[]> proposals, int refused, Map<String, Integer> addresses, List<Row> rows,
        Map<Integer, Integer> held)
    {
        this.proposals = proposals;
        this.refused = refused;
        this.addresses = new java.util.LinkedHashMap<>(addresses);
        this.rows = new ArrayList<>(rows);
        this.held = new HashMap<>(held);
        this.rows.sort(Comparator.comparing((Row r) -> r.loco.isSyncIgnored()).thenComparing(r -> r.loco.getName(),
            String.CASE_INSENSITIVE_ORDER));
    }

    /** By name, each locomotive the station has at another address, with that address (FR-246). */
    public Map<String, Integer> getAddresses()
    {
        return Collections.unmodifiableMap(this.addresses);
    }

    /**
     * The renames that can be applied, in the order they must be applied in
     */
    public List<String[]> getProposals()
    {
        return Collections.unmodifiableList(this.proposals);
    }

    /**
     * How many candidate renames were declined.
     *
     * Counts declined candidates rather than certain renames: the duplicate-address refusals fire
     * before the names are compared, so some of what they skip would have produced no proposal anyway.
     * It is a "was anything held back" signal, not a total - the log carries the detail.
     */
    public int getRefusedCount()
    {
        return this.refused;
    }

    /**
     * Whether anything was declined, and the log therefore has something worth reading
     */
    public boolean hasRefusals()
    {
        return this.refused > 0;
    }

    /** Every locomotive here that differs, by name, ignored ones last. */
    public List<Row> getRows()
    {
        return Collections.unmodifiableList(this.rows);
    }

    /** How many locomotives differ with something left to answer. */
    public int getUnsettledCount()
    {
        return (int) this.rows.stream().filter(r -> !r.isSettled()).count();
    }

    /**
     * Of the rows told to take the station's address, those that would share a decoder address with another locomotive
     * here once every one of them has: judged on the end result, so swaps and chains are no collision in any order.
     */
    public Set<Row> collisions(Collection<Row> addressed)
    {
        Map<Integer, Integer> end = new HashMap<>(this.held);
        for (Row r : addressed)
        {
            end.merge(r.uid, -1, Integer::sum);
            end.merge(r.stationUid, 1, Integer::sum);
        }
        Set<Row> out = new HashSet<>();
        for (Row r : addressed) if (end.get(r.stationUid) > 1) out.add(r);
        return out;
    }

    /**
     * Of these rows, those Set all may Apply: not ignored, one difference asked, Apply allowed, and neither colliding
     * nor deleting once all of them are applied together.
     */
    public Set<Row> applicable(Collection<Row> rows)
    {
        List<Row> names = new ArrayList<>(), moves = new ArrayList<>();
        for (Row r : rows)
        {
            if (!r.loco.isSyncIgnored() && r.asksName() != r.asksAddress() && r.whyNot(r.asksName()) == null)
                (r.asksName() ? names : moves).add(r);
        }
        // Each one left out can change what the rest collide with or delete
        while (moves.removeAll(collisions(moves)) | names.removeIf(r -> deletes(r, names) != null))
        {
        }
        Set<Row> out = new HashSet<>(names);
        out.addAll(moves);
        return out;
    }

    /** The locomotive r's rename would delete, when the rows renamed with it leave its holder under that name; else null. */
    public Locomotive deletes(Row r, Collection<Row> renamed)
    {
        for (Row other : renamed) if (other.loco == r.nameHolder && other.stationName != null) return null;
        return r.nameHolder;
    }
}
