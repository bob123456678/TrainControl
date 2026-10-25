package regression;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import support.SourceOf;

/**
 * Every collection the store keeps is in the registry, and the registry is what the sites walk.
 *
 * OB-025. `AutonomyCompanionStore` keeps eleven collections and repeats the same per-collection shape at fourteen sites
 * (saving, loading, clearing, renaming a page, moving and forgetting squares, snapshotting and restoring a page,
 * reconciling against the diagram, applying to another store).  Adding one collection used to mean finding all
 * fourteen, and the omissions were each found afterwards as a bug (a rename turned every switched-off link back on;
 * `clear()` missed `stationSignals`; `captions` missing from `KNOWN_SHARED` reverted every caption edit).
 *
 * The sites now walk `kept()`, so adding a collection is one entry, and what is left to check is smaller:
 *
 *   - Nothing the store holds is unclassified: a collection nobody classified is how the eleventh took five commits.
 *   - Every kept collection is IN the registry.  The compiler cannot check this: a collection that is never registered
 *     builds perfectly and does nothing.
 *   - Every site that lists them walks the registry, which guards against a collection being re-inlined at one
 * site.
 *   - `reconcile` and `applyTo` still name every collection, because those two are written by hand on purpose: each
 * asks a
 *     question the registry cannot express.
 *
 * The exemptions below are the comment "this is deliberate" for a decision, in a place that fails if it stops being
 * true.
 *
 * @author Adam
 */
public class testStoreCollectionsAreHandledEverywhere
{
    /** The collections that are part of the SETUP - the things a square or a page carries. */
    private static final Set<String> KEPT = new LinkedHashSet<>(java.util.Arrays.asList(
        "pointNames", "stations", "tileLengths", "tileDirections", "barredArrivals", "portals",
        "blockedPoints",
        "stationSignals", "captions", "linkNames", "excludedPages", "keptDespiteRepeats",
        "disabledPortals",
        // The entry guard (FR-096): the same shape as stationSignals, and setup for the same reason.
        "entrySignals"));

    /** Everything else the store holds, and why it is not setup: a new field has to be classified. */
    private static final Map<String, String> NOT_KEPT = new LinkedHashMap<>();

    static
    {
        NOT_KEPT.put("configurations", "the configurations themselves, not a per-square setting");
        NOT_KEPT.put("unknownSharedFields", "what a NEWER build wrote and this one must not destroy");
        NOT_KEPT.put("pageNameToId", "page identity bookkeeping, rebuilt on load");
        NOT_KEPT.put("pageIdToName", "page identity bookkeeping, rebuilt on load");
        NOT_KEPT.put("pageNamesWhenWritten", "page identity bookkeeping, rebuilt on load");
        NOT_KEPT.put("pageIdConflicts", "diagnostics about a load, not setup");
        // The one collection no bookkeeping site may touch: it holds the file's own JSON for pages that are not loaded,
        // keyed by FIELD name and written back exactly as it came in, so a rename, move or delete acting on it would
        // lose what it exists to keep (OB-067).
        NOT_KEPT.put("heldForAbsentPages", "entries for pages that are not loaded, kept verbatim");
    }

    /**
     * The two bookkeeping sites still written out by hand, and why: each asks a question a registry cannot express.
     * `reconcile` decides what a square's absence from the diagram MEANS, which differs per collection (a caption goes
     * when either its square or its sensor goes); `applyTo` populates the tile GRAPH, which models track and has
     * nowhere to put a name.  A registry entry answering "not applicable" would pretend they are uniform, so they are
     * checked the old way.
     */
    private static final String[] SITES = { "reconcile", "applyTo",
        // The thirteenth site, which the registry does not cover: heldFields says how each collection's entries are
        // picked out of the file when their page is not loaded.  It is about the SHAPE the entries have in the file,
        // and it is static, so kept() is not available to it.  The cost of an omission is in that constant's own
        // javadoc: one save while a page's file is missing deletes that page's entries for the collection nobody
        // listed.
        "heldFields",
    };

    /**
     * The sites that DO walk the registry, by the name of the method that performs them: a collection re-inlined at one
     * would put the divergence back.
     */
    private static final String[] REGISTRY_SITES =
    {
        "sharedFields", "readShared", "clear", "clearShared", "renamePage", "moveTiles",
        "forgetSquares", "snapshotPage", "restorePage",
        // deletePage gathers the page's squares for forgetSquares, so the GATHERING has to know every collection.
        "deletePage",
        // Not a bookkeeping site but the same failure: a collection missing from the known-field list is read into its
        // own collection AND kept as an unknown field, so the next save writes both and the stale copy wins.
        "knownShared",
    };

    /**
     * Where a collection is deliberately absent from a site, and why, keyed "site:collection"; a line that stops being
     * needed fails this test.
     */
    private static final Map<String, String> EXEMPT = new LinkedHashMap<>();

    static
    {
        // excludedPages is keyed by PAGE, not by square; only the hand-written pair still need an exemption.
        EXEMPT.put("reconcile:excludedPages", "reconcile compares SQUARES against the diagram");

        // Its twin, keyed by page for the same reason: the PAGES the operator turned back on after the repeated-sensor
        // rule shut them.
        EXEMPT.put("reconcile:keptDespiteRepeats", "reconcile compares SQUARES against the diagram");

        // HELD_FIELDS is keyed by the name in the FILE, like everything else that reads or writes one.
        EXEMPT.put("heldFields:disabledPortals", "listed by its JSON name, disabledLinks");

        // Handled by a helper of its own, called from here
        EXEMPT.put("reconcile:captions", "done by reconcileCaptions - a caption goes when either the "
            + "square it is drawn on or the sensor it is about does, which is a rule of its own");

        // applyTo puts the setup onto the TILE GRAPH, a model of TRACK: a name, a length, a caption or a barred arrival
        // is a fact about the railway that the graph has nowhere to put, so "handled everywhere" is the wrong rule for
        // this one site.
        for (String notTrack : new String[] {"pointNames", "stations", "tileLengths", "barredArrivals",
            "stationSignals", "captions", "linkNames", "excludedPages", "keptDespiteRepeats",
            "blockedPoints", "entrySignals"})
        {
            EXEMPT.put("applyTo:" + notTrack, "applyTo populates the tile GRAPH, which models track - "
                + "pairings, switched-off links and directions. This is not a property of track");
        }
    }

    /** Nothing the store holds is unclassified. */
    @Test
    public void testEveryCollectionIsEitherKeptOrExplainedAway()
    {
        List<String> unclassified = new ArrayList<>();

        for (Field field : AutonomyCompanionStore.class.getDeclaredFields())
        {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;

            if (!Map.class.isAssignableFrom(field.getType())
                && !Set.class.isAssignableFrom(field.getType())) continue;

            if (KEPT.contains(field.getName()) || NOT_KEPT.containsKey(field.getName())) continue;

            unclassified.add(field.getName());
        }

        assertTrue(unclassified.isEmpty(),
            "the store holds collections this test has never been told about: " + unclassified
            + ". Add each to KEPT - and then to the store's registry, which is what the rest of this "
            + "test will insist on - or to NOT_KEPT with the reason it is not part of the setup. A "
            + "collection nobody classified is how the eleventh one took five commits to finish "
            + "adding");
    }

    /**
     * Every kept collection is in the registry: the one thing the compiler cannot check, since a collection never
     * registered builds and does nothing.
     */
    @Test
    public void testEveryKeptCollectionIsRegistered() throws Exception
    {
        String body = withoutComments(bodyOf(source(), "kept"));

        assertNotNull(body, "no kept() in the store - if the registry was renamed, rename it here "
            + "too, because this test is the only thing checking every collection is in it");

        List<String> missing = new ArrayList<>();

        for (String kept : KEPT)
        {
            if (!body.contains(kept)) missing.add(kept);
        }

        assertEquals(missing, new ArrayList<String>(),
            missing + ": a collection the store keeps is not in the registry, so nothing clears it, "
            + "saves it, loads it, renames it, moves it, forgets it, snapshots it or deletes it with "
            + "its page. That is every bookkeeping site at once");
    }

    /** And every site that lists the collections walks the registry. */
    @Test
    public void testEverySiteWalksTheRegistry() throws Exception
    {
        List<String> notWalking = new ArrayList<>();

        for (String site : REGISTRY_SITES)
        {
            String body = withoutComments(bodyOf(source(), site));

            assertNotNull(body, "no site called " + site + " in the store - if it was renamed, rename "
                + "it in REGISTRY_SITES too, because this test is the only thing checking it still "
                + "covers everything");

            if (!body.contains("kept()")) notWalking.add(site);
        }

        assertEquals(notWalking, new ArrayList<String>(),
            notWalking + ": a bookkeeping site has stopped walking the registry. Whatever it does "
            + "instead is a list of collections that can now fall out of step with the other nine - "
            + "which is the state OB-025 was raised about, arriving one site at a time");
    }

    /** And every kept collection is named at the sites still written by hand. */
    @Test
    public void testEveryKeptCollectionIsHandledAtEverySite() throws Exception
    {
        String source = source();

        List<String> missing = new ArrayList<>();

        for (String site : SITES)
        {
            String body = withoutComments(bodyOf(source, site));

            assertNotNull(body, "no site called " + site + " in the store - if it was renamed, rename "
                + "it in SITES too, because this test is the only thing checking it covers everything");

            for (String kept : KEPT)
            {
                if (body.contains(kept)) continue;

                if (EXEMPT.containsKey(site + ":" + kept)) continue;

                missing.add(site + " says nothing about " + kept);
            }
        }

        assertEquals(missing, new ArrayList<String>(),
            missing + ": a collection the store keeps is not handled at one of its bookkeeping sites. Each of "
            + "these is the shape that produced the disabledPortals defects - a setting that survives "
            + "a rename, a move, a delete or a discard because one site of " + SITES.length + " never "
            + "heard of it. Handle it, or add a line to EXEMPT saying why it does not apply");
    }

    /** An exemption that is no longer needed is removed rather than left to rot. */
    @Test
    public void testNoExemptionIsStale() throws Exception
    {
        String source = source();

        List<String> stale = new ArrayList<>();

        for (String key : EXEMPT.keySet())
        {
            String site = key.substring(0, key.indexOf(':'));
            String kept = key.substring(key.indexOf(':') + 1);

            String body = withoutComments(bodyOf(source, site));

            if (body != null && body.contains(kept)) stale.add(key);
        }

        assertEquals(stale, new ArrayList<String>(),
            "these collections ARE handled at these sites now, so the exemptions claiming they do not "
            + "apply are wrong. An exemption is a statement about the code, and a stale one is worse "
            + "than none - it is a comment that reads as a decision. Stale: " + stale);
    }

    /**
     * The store's source, or a failure that says so: a test that reads the source cannot pass by not finding it, and
     * the store is looked for by its declaration, wherever the file is.
     */
    private String source() throws Exception
    {
        return SourceOf.fileDeclaring("public class AutonomyCompanionStore\n");
    }

    private String withoutComments(String body)
    {
        if (body == null) return null;

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
            else if (c == '/' && next == '/') inLine = true;
            else if (c == '/' && next == '*') inBlock = true;
            else out.append(c);
        }

        return out.toString();
    }

    private String bodyOf(String source, String name)
    {
        String longest = null;

        // Anchored on a DECLARATION (start of line, a modifier, then the name), so a call elsewhere is not mistaken for
        // the method.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "^[ \t]*(?:private|public|protected)[^\n=;]*\\b" + name + "\\s*\\(",
            java.util.regex.Pattern.MULTILINE).matcher(source);

        // The LONGEST of them: several are overloaded and the short one is a two-line delegate, which would report
        // every collection missing.
        while (m.find())
        {
            String body = braced(source, m.end() - 1);

            if (body != null && (longest == null || body.length() > longest.length())) longest = body;
        }

        if (longest != null) return longest;

        // Not a method - a constant, whose "body" is its initializer up to the semicolon
        java.util.regex.Matcher constant = java.util.regex.Pattern.compile(
            "^[ \t]*(?:private|public|protected)[^\n]*\\b" + name + "\\s*=",
            java.util.regex.Pattern.MULTILINE).matcher(source);

        if (!constant.find()) return null;

        int end = source.indexOf(';', constant.end());

        return end < 0 ? null : source.substring(constant.start(), end + 1);
    }

    /**
     * The braced block starting at or after {@code from}.
     */
    private String braced(String source, int from)
    {
        int open = source.indexOf('{', from);

        if (open < 0) return null;

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }

        return null;
    }

}
